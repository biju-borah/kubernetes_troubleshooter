# Kubernetes Pod Troubleshooter

An AI-assisted tool that diagnoses unhealthy Kubernetes pods by analyzing their status, events, and logs using the Groq API. It provides on-demand CLI diagnosis (Mode A) and continuous monitoring through a Kafka-based event pipeline (Mode B). Diagnoses contain a likely root cause, failure category, and suggested fix; the application does not apply changes to the cluster.

---

## Modes

| Mode | Status | Description |
|------|--------|-------------|
| Mode A — On-demand CLI | Implemented | Interactive namespace input; finds unhealthy pods, diagnoses them, and prints results |
| Mode B — Continuous Watch | Implemented with known issues | `watch-service` publishes unhealthy pod snapshots to Kafka; `worker-service` consumes them, diagnoses failures, and persists results |

Both pipelines are present in source. See [Known Limitations](#known-limitations) for issues affecting diagnosis categories, watcher configuration, and caching.

---

## Architecture

This is a Java 17, Spring Boot, multi-module Maven project:

| Module | Responsibility |
|---|---|
| `common` | Shared `Diagnosis` entity, `FailureCategory` enum, and watcher/worker `PodSnapshot` record |
| `cli-service` | Interactive Kubernetes scan and diagnosis |
| `watch-service` | Kubernetes informer, evidence collection, and Kafka producer |
| `worker-service` | Kafka consumer, LLM diagnosis, Redis caching, and PostgreSQL persistence |

### Mode A

```
CLI input (namespace)
        │
        ▼
DiagnosisService
        │
        ├──► K8sClient          — list pods, fetch events, fetch logs
        ├──► PodHealthChecker   — filter unhealthy pods
        ├──► LogTrimmer         — keyword-based error extraction
        ├──► PromptBuilder      — structured prompt construction
        ├──► Redis              — return cached diagnosis on a hit
        ├──► LLMClient          — call Groq on a cache miss; parse JSON response
        └──► PostgreSQL         — persist new diagnoses
```

### Mode B

```
Kubernetes Cluster (SharedIndexInformer)
        │
        ▼
watch-service
        │  checks pod health on informer update callbacks
        │  publishes full PodSnapshot to Kafka topic: pod-snapshot
        ▼
Kafka
        │
        ▼
worker-service (consumer group: llm-workers)
        │  consumes PodSnapshot from Kafka
        │  skips snapshots with a cached diagnosis
        │  calls Groq for uncached failures
        └──► PostgreSQL + Redis + console output
```

The CLI and worker implement similar diagnosis logic separately. The worker uses the captured snapshot and does not make Kubernetes API calls.

Health checks flag crash/image backoff, more than five container restarts, unschedulable or prolonged pending pods, and running pods without a true `Ready` condition. Completed pods with phase `Succeeded` count as healthy.

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose
- A running Kubernetes cluster (Minikube, KillerCoda, or any cloud cluster)
- Groq API key from [the Groq console](https://console.groq.com)

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/Hrishi-learn/kubernetes_troubleshooter
cd kubernetes_troubleshooter
```

### 2. Set environment variables

```bash
export LLM_API_KEY="your-groq-api-key-here"
```

Set this variable in each terminal that starts `cli-service` or `worker-service`. Both read `llm.api.key=${LLM_API_KEY}` and use `openai/gpt-oss-20b` by default. The watcher does not call the LLM.

### 3. Start infrastructure

```bash
docker compose up -d
```

Starts:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Kafka on `localhost:9092`

Compose starts the infrastructure only; run the Java services separately. The application properties use database `troubleshooter` with username/password `postgres`/`postgres`, matching Compose. Hibernate manages the diagnosis table with `ddl-auto=update`.

### 4. Build the project

```bash
mvn clean install
```

Run this from the repository root to build and install all modules, including the shared `common` dependency. The POMs do not bind Spring Boot's `repackage` goal, so this build does not create executable Boot jars by default. The commands below launch services through the explicitly versioned Spring Boot Maven plugin instead of `java -jar`.

### 5. Point kubectl at your cluster

```bash
kubectl config current-context   # should show minikube or your cluster name
```

If using Minikube:

```bash
minikube start --driver=docker --cpus=2 --memory=2200mb
```

The CLI and watcher initialize the Kubernetes Java client with `ClientBuilder.standard()`. For local use, configure your kubeconfig and ensure it permits listing/watching pods and reading pod events and logs in the target namespace.

---

## Running Mode A (On-demand CLI)

From the repository root, after the build/install step:

```bash
mvn -pl cli-service org.springframework.boot:spring-boot-maven-plugin:3.5.16:run
```

Enter a namespace at the prompt:

```
Please enter the namespace: troubleshoot-test
```

The CLI prints `PodName`, `Failure_Category`, `Root Cause`, and `Suggested Fix` for diagnosed pods, then prompts again. Enter another namespace to scan again, or `exit` to leave the input loop. Use Ctrl+C to stop the process if it remains running. The runner does not read a `--namespace` command-line argument.

New diagnoses are persisted to PostgreSQL and cached in Redis. A subsequent scan with the same cache key returns the cached result without another LLM call or database insert. The current response-field mismatch can leave the printed category as `null`; see [Known Limitations](#known-limitations).

---

## Running Mode B (Continuous Watch)

**Current issue:** `SnapshotBuilder` has a malformed namespace placeholder, `@Value("${kubernetes.namespace:default")`, missing the closing `}`. It is used for event/log lookups and the emitted snapshot's namespace. This needs correction to `@Value("${kubernetes.namespace:default}")` before relying on Mode B's evidence collection. The commands below describe the configured startup flow; they do not fix this source issue.

Start the worker in a terminal with `LLM_API_KEY` set:

```bash
mvn -pl worker-service org.springframework.boot:spring-boot-maven-plugin:3.5.16:run
```

Start the watcher in another terminal with Kubernetes access:

```bash
mvn -pl watch-service org.springframework.boot:spring-boot-maven-plugin:3.5.16:run \
  -Dspring-boot.run.arguments="--kubernetes.namespace=troubleshoot-test"
```

Replace `troubleshoot-test` with your namespace. The checked-in watcher configuration also defaults to `troubleshoot-test`; the property name is `kubernetes.namespace`.

The watcher starts a `SharedIndexInformer` for that namespace. On each pod update, it checks the new pod's health and publishes a full `PodSnapshot` to `pod-snapshot` if unhealthy. It does not compare old and new health, so repeated unhealthy updates can produce multiple messages. Add/delete callbacks only print metadata; existing unhealthy pods are not diagnosed during the initial add callback.

The worker consumes snapshots as part of group `llm-workers`. It extracts relevant log lines and builds a prompt before checking Redis. On a cache miss, it calls Groq, saves the diagnosis to PostgreSQL, caches the result, and prints the LLM response. On a cache hit, it skips the LLM call and persistence. The worker needs Kafka, Redis, PostgreSQL, and Groq access, but no Kubernetes credentials.

To inspect published snapshots using the Kafka container:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic pod-snapshot --from-beginning
```

---

## Manual Testing

There are currently no automated test sources in the repository. The following pods provide a manual integration exercise and require a running cluster and the infrastructure above. For Mode B, address the watcher issue above and start the services before creating the test pods so updates can be observed.

Create unhealthy test pods:

```bash
kubectl create namespace troubleshoot-test

# CrashLoopBackOff — simulates a Spring Boot app failing to connect to DB
kubectl run crash-pod \
  --image=busybox \
  --restart=Always \
  -n troubleshoot-test \
  -- sh -c "
    echo 'INFO: Starting application...';
    echo 'ERROR: Connection refused to db-primary.internal:5432';
    echo 'EXCEPTION: Failed to create bean dataSource';
    echo 'FATAL: Application failed to start';
    exit 1
  "

# ImagePullBackOff — nonexistent image
kubectl run imagepull-pod \
  --image=nonexistent-image-xyz123:latest \
  --restart=Always \
  -n troubleshoot-test
```

Check the pod states until the failures are visible:

```bash
kubectl get pods -n troubleshoot-test
```

Run the Mode A command above and enter `troubleshoot-test` at the prompt. For Mode B, inspect the worker's console output and the `diagnosis` table. Diagnosis text varies with the LLM response; failure-category handling currently has the limitation described below.

---

## Key Design Decisions

**`common` module free of client-java dependency**

The shared `PodSnapshot` uses plain strings, an integer, and a list of event messages rather than raw K8s types (`V1Pod`, `CoreV1Event`). The watcher and worker use this contract; the CLI currently has a separate record with the same fields. `common` also contains the JPA entity and failure enum, and does not depend on the Kubernetes client.

**Cache key design**

Keys directly concatenate `namespace:podName:waitingReason:filtered event messages`; they are not hashed. Event messages containing any of `Pulling`, `Pulled`, `Created`, `Started`, `Scheduled`, or `Successfully` are removed from the key. Entries have no TTL. Changes to the retained event messages can cause cache misses, while changes only to logs, phase, or restart count do not change the key.

**Isolated K8s and LLM clients**

`K8sClient` and `LLMClient` wrap their respective APIs. The CLI and worker currently duplicate the LLM client, prompt, log trimming, and caching implementations, so changes to those behaviors need to be kept aligned across modules.

**Error handling**

Kubernetes event/log API errors are caught so evidence collection can continue with missing data. LLM JSON parsing errors are logged and the affected diagnosis is skipped. Other failures, such as HTTP, Redis, or database errors, are not covered by those catches, so per-pod error isolation is incomplete.

**Mode B decoupling**

`watch-service` publishes the captured `PodSnapshot` to Kafka, including events and logs when available. `worker-service` can diagnose that captured state without fetching evidence from Kubernetes again.

---

## Known Limitations

- **Watcher namespace placeholder** — `SnapshotBuilder` is missing `}` in its `@Value` expression, as described in the Mode B startup section.
- **LLM category mismatch** — both prompts ask for JSON field `category`, but both `LLMResponse` records expect `failureCategory`. Responses following the prompt leave that field null; persistence substitutes `Unknown`.
- **Init and multiple containers** — health checks examine regular container statuses, but snapshot waiting reason/restart count come only from the first regular container. Init container statuses are not inspected. Log requests do not explicitly select a container, so multi-container log coverage is incomplete.
- **Namespace scope** — each CLI scan targets one namespace; the interactive session can scan another afterward. Each watcher instance monitors one configured namespace.
- **Update-driven detection** — initial add callbacks do not publish snapshots. Pending-age checks run when health is evaluated on an update, rather than on a separate timer.
- **Cache freshness and compatibility** — entries never expire and do not include log content in the key. The CLI and worker also use the same Redis key format with different Java result classes and type-aware JSON serialization, which can cause cross-service deserialization failures when sharing keys.
- **Incomplete failure recovery** — the code does not define a custom Kafka retry/dead-letter strategy; JSON parsing failures are caught inside diagnosis instead of propagated to the listener.
- **No history UI or alerts** — results are stored in PostgreSQL and printed to the console; there is no implemented browsing API, web UI, or Slack/webhook integration.

---

## Roadmap

- [x] `worker-service` — Kafka consumer with LLM diagnosis, PostgreSQL persistence, and Redis caching
- [ ] Correct watcher namespace injection and align the LLM JSON response fields
- [ ] Add automated tests for health checks, diagnosis, caching, and the Kafka pipeline
- [ ] Share diagnosis components and make cache serialization compatible across services
- [ ] Add cache expiry and improve failure deduplication
- [ ] Init container failure detection
- [ ] Multi-namespace support
- [ ] Slack/webhook alerting from `worker-service`
- [ ] Web UI for browsing diagnosis history
- [ ] Semantic log similarity for smarter cache dedup

---

## Dependencies

| Dependency | Purpose |
|---|---|
| Spring Boot 3.5.16 | Framework |
| io.kubernetes:client-java 26.0.1 | Kubernetes API + SharedIndexInformer |
| Groq API (openai/gpt-oss-20b) | LLM diagnosis |
| Spring Data JPA + PostgreSQL | Diagnosis persistence |
| Spring Data Redis | Diagnosis caching |
| Apache Kafka | Mode B event pipeline |
| Jackson | JSON parsing (LLM response + Kafka messages) |
| Lombok | Boilerplate reduction |

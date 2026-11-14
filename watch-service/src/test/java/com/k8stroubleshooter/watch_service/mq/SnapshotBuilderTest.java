package com.k8stroubleshooter.watch_service.mq;

import com.k8s_troubleshooter.common.entity.PodSnapshot;
import com.k8stroubleshooter.watch_service.k8s.K8sClient;
import com.k8stroubleshooter.watch_service.k8s.PodHealthChecker;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotBuilderTest {

    @Test
    void usesConfiguredNamespaceForEvidenceAndSnapshot() throws ApiException {
        assertNamespaceUsed(
                new ApplicationContextRunner().withPropertyValues("kubernetes.namespace=payments"),
                "payments");
    }

    @Test
    void usesDefaultNamespaceWhenPropertyIsAbsent() throws ApiException {
        assertNamespaceUsed(new ApplicationContextRunner(), "default");
    }

    private void assertNamespaceUsed(ApplicationContextRunner runner, String expectedNamespace) throws ApiException {
        K8sClient k8sClient = mock(K8sClient.class);
        when(k8sClient.getEvents(expectedNamespace, "api-pod"))
                .thenReturn(List.of(new CoreV1Event().message("Readiness probe failed")));
        when(k8sClient.getLogs(expectedNamespace, "api-pod", false))
                .thenReturn("ERROR: database connection refused");

        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta().name("api-pod"))
                .status(new V1PodStatus().phase("Running"));

        runner.withBean(K8sClient.class, () -> k8sClient)
                .withBean(PodHealthChecker.class)
                .withBean(SnapshotBuilder.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    PodSnapshot snapshot = context.getBean(SnapshotBuilder.class).buildSnapshot(pod);

                    assertThat(snapshot.namespace()).isEqualTo(expectedNamespace);
                    assertThat(snapshot.podName()).isEqualTo("api-pod");
                    assertThat(snapshot.eventMessages()).containsExactly("Readiness probe failed");
                    assertThat(snapshot.rawLogs()).isEqualTo("ERROR: database connection refused");
                    verify(k8sClient).getEvents(expectedNamespace, "api-pod");
                    verify(k8sClient).getLogs(expectedNamespace, "api-pod", false);
                });
    }
}

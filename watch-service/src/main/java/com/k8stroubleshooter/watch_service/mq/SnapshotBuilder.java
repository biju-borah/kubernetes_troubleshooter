package com.k8stroubleshooter.watch_service.mq;

import com.k8stroubleshooter.watch_service.k8s.K8sClient;
import com.k8stroubleshooter.watch_service.k8s.PodHealthChecker;
import com.k8s_troubleshooter.common.entity.PodSnapshot;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SnapshotBuilder {
    private final PodHealthChecker podHealthChecker;
    private final K8sClient k8sClient;

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    public PodSnapshot buildSnapshot(V1Pod pod) {
        List<CoreV1Event> events = Collections.emptyList();
        String podName = "";
        String phase = "";
        String logs = "";
        String waitingReason = "";
        int restartCount = 0;

        if (pod.getMetadata() != null) {
            podName = pod.getMetadata().getName();
        }
        boolean isCrashOrImageLoop = podHealthChecker.isCrashOrImageLooping(pod);

        if (pod.getStatus() != null) {
            phase = pod.getStatus().getPhase();
        }
        try {
            events = k8sClient.getEvents(namespace, podName);
        } catch (ApiException e) {
            System.out.println("No events found for pod " + pod);
        }
        try {
            logs = k8sClient.getLogs(namespace, podName, isCrashOrImageLoop);
        } catch (ApiException e) {
            System.out.println("No Logs found for pod " + pod);
        }

        List<String> eventMessages = new ArrayList<>();
        for (CoreV1Event event : events) {
            eventMessages.add(event.getMessage());
        }
        waitingReason = getWaitingReason(pod);
        restartCount = getRestartCount(pod);

        return new PodSnapshot(podName, namespace, phase, waitingReason, restartCount, eventMessages, logs);
    }

    private String getWaitingReason(V1Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return "";
        }
        V1PodStatus status = pod.getStatus();
        if (status == null) {
            return "";
        }
        List<V1ContainerStatus> containerStatuses = status.getContainerStatuses();
        if (containerStatuses == null || containerStatuses.isEmpty()) {
            return "";
        }
        V1ContainerStateWaiting waiting =
                containerStatuses.get(0).getState() != null
                        ? containerStatuses.get(0).getState().getWaiting()
                        : null;

        return waiting != null ? waiting.getReason() : "";
    }

    private int getRestartCount(V1Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        V1PodStatus status = pod.getStatus();
        if (status == null) {
            return 0;
        }
        List<V1ContainerStatus> containerStatuses = status.getContainerStatuses();
        if (containerStatuses == null || containerStatuses.isEmpty()) {
            return 0;
        }
        return containerStatuses.get(0).getRestartCount();
    }
}

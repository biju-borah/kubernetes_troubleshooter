package com.k8stroubleshooter.watch_service.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Pod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.util.List;

@Component
@RequiredArgsConstructor
public class K8sClient {

    private final ApiClient apiClient;
    private final CoreV1Api api;

    public List<V1Pod> getPods(String namespace) throws ApiException {

        return api.listNamespacedPod(namespace).execute().getItems();
    }

    public List<CoreV1Event> getEvents(String namespace, String podname) throws ApiException {

        List<CoreV1Event> events = api.listNamespacedEvent(namespace).execute().getItems();

        return events.stream().filter(event -> event.getInvolvedObject() != null && podname.equals(event.getInvolvedObject().getName()))
                .toList();
    }

    public String getLogs(String namespace, String pod, boolean previous) throws ApiException {

        return api.readNamespacedPodLog(pod, namespace).previous(previous).execute();
    }

}

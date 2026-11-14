package com.k8stroubleshooter.watch_service.service;

import com.k8stroubleshooter.watch_service.k8s.PodHealthChecker;
import com.k8stroubleshooter.watch_service.mq.PodSnapshotProducer;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.CallGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PodWatcherService {

    private final CoreV1Api api;
    private final SharedInformerFactory factory;
    private final PodHealthChecker podHealthChecker;
    private final PodSnapshotProducer kafkaProducer;

    @Value("${kubernetes.namespace:default}")
    private String namespace;


    public void monitorPod() {
        CallGenerator podCallGenerator = params ->
                api
                        .listNamespacedPod(namespace)
                        .resourceVersion(params.resourceVersion)
                        .watch(params.watch)
                        .timeoutSeconds(params.timeoutSeconds)
                        .buildCall(null);

        SharedIndexInformer<V1Pod> informer = factory.sharedIndexInformerFor(
                podCallGenerator,
                V1Pod.class,
                V1PodList.class
        );

        ResourceEventHandler<V1Pod> podResourceEventHandler = new ResourceEventHandler<V1Pod>() {
            @Override
            public void onAdd(V1Pod pod) {
                System.out.println(pod.getMetadata());
            }

            @Override
            public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                System.out.println(newPod.getMetadata());
                if (!podHealthChecker.isHealthy(newPod)) {
                    kafkaProducer.publish(newPod);
                }
            }

            @Override
            public void onDelete(V1Pod pod, boolean b) {
                System.out.println(pod.getMetadata());
            }
        };

        informer.addEventHandler(podResourceEventHandler);
        factory.startAllRegisteredInformers();
    }
}

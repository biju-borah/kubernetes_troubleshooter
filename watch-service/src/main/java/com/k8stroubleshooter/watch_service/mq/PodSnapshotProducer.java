package com.k8stroubleshooter.watch_service.mq;

import com.k8s_troubleshooter.common.entity.PodSnapshot;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class PodSnapshotProducer {
    private final SnapshotBuilder snapshotBuilder;
    private final KafkaTemplate<String, PodSnapshot> kafkaTemplate;

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    private final String topic = "pod-snapshot";

    public void publish(V1Pod pod) {
        PodSnapshot snapshot = snapshotBuilder.buildSnapshot(pod);
        String key = namespace;
        if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
            key = namespace + pod.getMetadata().getName();
        }
        kafkaTemplate.send(topic, key, snapshot);


    }


}

package com.k8stroubleshooter.worker_service.mq;

import com.k8s_troubleshooter.common.entity.PodSnapshot;
import com.k8stroubleshooter.worker_service.diagnosis.DiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PodSnapshotConsumer {

    private final DiagnosisService diagnosisService;

    @KafkaListener(topics = "pod-snapshot")
    public void consume(PodSnapshot snapshot){
        System.out.println(snapshot);
        diagnosisService.podDiagnosis(snapshot);
    }

}

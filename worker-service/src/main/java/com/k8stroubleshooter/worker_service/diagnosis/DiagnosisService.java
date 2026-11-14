package com.k8stroubleshooter.worker_service.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.k8s_troubleshooter.common.FailureCategory;
import com.k8s_troubleshooter.common.entity.Diagnosis;
import com.k8s_troubleshooter.common.entity.PodSnapshot;
import com.k8stroubleshooter.worker_service.cache.CachingService;
import com.k8stroubleshooter.worker_service.cache.dto.CacheKey;
import com.k8stroubleshooter.worker_service.diagnosis.dto.DiagnosisResult;
import com.k8stroubleshooter.worker_service.llm.LLMClient;
import com.k8stroubleshooter.worker_service.llm.PromptBuilder;
import com.k8stroubleshooter.worker_service.llm.dto.LLMResponse;
import com.k8stroubleshooter.worker_service.logs.LogTrimmer;
import com.k8stroubleshooter.worker_service.repository.DiagnosisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final LogTrimmer logTrimmer;
    private final DiagnosisRepository diagnosisRepository;
    private final CachingService cachingService;

    public void podDiagnosis(PodSnapshot podSnapshot){
        String trimmedLogs = logTrimmer.trim(podSnapshot.rawLogs());
        String prompt = promptBuilder.buildPrompt(podSnapshot,trimmedLogs);

        CacheKey cacheKey = new CacheKey(podSnapshot.podName(), podSnapshot.namespace(), podSnapshot.waitingReason(), podSnapshot.eventMessages());
        Optional<DiagnosisResult> cachedResult = cachingService.get(cacheKey);

        if (cachedResult.isPresent()) {
            return;
        }

        try{
            LLMResponse response = llmClient.llmCall(prompt);
            saveToDb(podSnapshot,response);
            DiagnosisResult result = new DiagnosisResult(podSnapshot.podName(), response.rootCause(), response.suggestedFix(), response.failureCategory());
            cachingService.put(cacheKey, result);
            System.out.println(response);
        } catch (JsonProcessingException e) {
            System.out.println("Failed to parse LLM response for pod: " + podSnapshot.podName());
        }
    }
    public void saveToDb(PodSnapshot snapshot,LLMResponse response){
        FailureCategory failureCategory = (response.failureCategory() == null) ? FailureCategory.Unknown : response.failureCategory();
        Diagnosis diagnosis = new Diagnosis(snapshot.podName(), snapshot.namespace(), "", failureCategory, response.rootCause(), response.suggestedFix(), snapshot.rawLogs());
        diagnosisRepository.save(diagnosis);
    }

}

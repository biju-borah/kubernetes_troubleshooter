package com.k8s_troubleshooter.cli_service.cache;

import com.k8s_troubleshooter.cli_service.cache.dto.CacheKey;
import com.k8s_troubleshooter.cli_service.diagnosis.dto.DiagnosisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CachingService {

    private final RedisTemplate<String, DiagnosisResult> redisTemplate;
    private static final Set<String> NOISE_REASONS = Set.of("Pulling", "Pulled", "Created", "Started", "Scheduled","Successfully");


    public Optional<DiagnosisResult> get(CacheKey cacheKey) {
        String key = getKey(cacheKey);

        DiagnosisResult result = (DiagnosisResult) redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(result);
    }

    public void put(CacheKey cacheKey, DiagnosisResult result) {
        String key = getKey(cacheKey);
        /*
         * current set without TTL.
         * */
        redisTemplate.opsForValue().set(key, result);
    }

    private String getKey(CacheKey cacheKey) {
        List<String> filterMessages = cacheKey.eventMessages().stream()
                .filter(msg -> NOISE_REASONS.stream().noneMatch(reason -> msg.contains(reason))).toList();

        return cacheKey.namespace() + ":" + cacheKey.podName()
                + ":" + cacheKey.waitingReason() + ":" + String.join(",", filterMessages);
    }
}

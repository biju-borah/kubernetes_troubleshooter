package com.k8stroubleshooter.worker_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.api")
public record LLMConfigProperties(
        String baseUrl,
        String key,
        String model
) {
}

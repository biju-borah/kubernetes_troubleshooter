package com.k8s_troubleshooter.cli_service.llm.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.k8s_troubleshooter.common.FailureCategory;

public record LLMResponse(
        @JsonProperty("category") @JsonAlias("failureCategory") FailureCategory failureCategory,
        String rootCause,
        String suggestedFix
) {
}

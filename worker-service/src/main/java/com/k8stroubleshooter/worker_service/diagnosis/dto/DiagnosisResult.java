package com.k8stroubleshooter.worker_service.diagnosis.dto;

import com.k8s_troubleshooter.common.FailureCategory;

public record DiagnosisResult(String podName, String rootCause, String suggestedFix, FailureCategory failureCategory) {
}

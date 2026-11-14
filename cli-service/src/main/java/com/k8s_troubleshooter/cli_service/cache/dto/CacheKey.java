package com.k8s_troubleshooter.cli_service.cache.dto;

import java.util.List;

public record CacheKey(String podName, String namespace, String waitingReason, List<String> eventMessages){
}

package com.k8s_troubleshooter.common.entity;

import java.util.List;

public record PodSnapshot(
        String podName,
        String namespace,
        String phase,
        String waitingReason,   // nullable - e.g. "CrashLoopBackOff", null if none
        int restartCount,
        List<String> eventMessages,
        String rawLogs
) {
}
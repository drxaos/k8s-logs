package com.github.drxaos.k8s.logs;

public record LogRecord(
        String podTimestamp,
        String appTimestamp,
        String podName,
        String appLevel,
        String appThread,
        String appLogger,
        String message,
        String appCaller,
        String podUid,
        String podNamespace,
        String podContainer,
        String partitionPeriod
        ) {
}

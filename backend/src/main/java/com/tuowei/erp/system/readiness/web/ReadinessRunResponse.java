package com.tuowei.erp.system.readiness.web;

import java.time.LocalDateTime;

public record ReadinessRunResponse(
        Long id,
        String runNo,
        String releaseCommit,
        String releaseVersion,
        String environment,
        String databaseInstance,
        String redisInstance,
        String dockerProfile,
        String status,
        String decision,
        String decisionComment,
        String remark,
        Long startedBy,
        LocalDateTime startedTime,
        Long decidedBy,
        LocalDateTime decidedTime,
        LocalDateTime createdTime
) {
}

package com.tuowei.erp.system.readiness.web;

public record ReadinessRunCreateRequest(
        String releaseCommit,
        String releaseVersion,
        String environment,
        String databaseInstance,
        String redisInstance,
        String dockerProfile,
        Boolean generateDefaultItems,
        Boolean recordPreflightEvidence,
        String remark
) {
}

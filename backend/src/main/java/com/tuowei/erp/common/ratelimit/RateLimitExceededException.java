package com.tuowei.erp.common.ratelimit;

/**
 * 限流异常
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

package com.tuowei.erp.system.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class NoOpLoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(NoOpLoginRateLimiter.class);

    public NoOpLoginRateLimiter() {
        log.warn("登录限流功能已禁用（Redis不可用）");
    }

    public void assertAllowed(String username, String clientIp) {
        // 无操作 - Redis不可用时不进行限流
    }

    public void recordFailure(String username, String clientIp) {
        // 无操作 - Redis不可用时不记录失败
    }

    public void recordSuccess(String username, String clientIp) {
        // 无操作 - Redis不可用时不记录成功
    }
}

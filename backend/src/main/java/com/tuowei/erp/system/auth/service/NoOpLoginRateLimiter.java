package com.tuowei.erp.system.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "test"})
public class NoOpLoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(NoOpLoginRateLimiter.class);

    public NoOpLoginRateLimiter() {
        log.info("当前 profile 未启用 Redis 登录限流");
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

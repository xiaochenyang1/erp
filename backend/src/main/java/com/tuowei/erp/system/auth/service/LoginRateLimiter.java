package com.tuowei.erp.system.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String LOCK_MESSAGE = "登录失败次数过多，请15分钟后重试";

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void assertAllowed(String username, String clientIp) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(username, clientIp)))) {
                throw new LockedException(LOCK_MESSAGE);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，跳过登录限流检查: {}", e.getMessage());
            // Redis不可用时不阻止登录
        }
    }

    public void recordFailure(String username, String clientIp) {
        try {
            String failureKey = failureKey(username, clientIp);
            Long failures = redisTemplate.opsForValue().increment(failureKey);
            if (failures != null && failures == 1L) {
                redisTemplate.expire(failureKey, WINDOW);
            }
            if (failures != null && failures >= MAX_FAILURES) {
                redisTemplate.opsForValue().set(lockKey(username, clientIp), "1", WINDOW);
                throw new LockedException(LOCK_MESSAGE);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，跳过登录失败记录: {}", e.getMessage());
            // Redis不可用时不阻止登录
        }
    }

    public void recordSuccess(String username, String clientIp) {
        try {
            redisTemplate.delete(List.of(failureKey(username, clientIp), lockKey(username, clientIp)));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis不可用，跳过登录成功记录: {}", e.getMessage());
            // Redis不可用时不影响登录
        }
    }

    private String failureKey(String username, String clientIp) {
        return "erp:login:fail:" + normalize(username) + ":" + normalizeClientIp(clientIp);
    }

    private String lockKey(String username, String clientIp) {
        return "erp:login:lock:" + normalize(username) + ":" + normalizeClientIp(clientIp);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "anonymous";
    }

    private String normalizeClientIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
    }
}

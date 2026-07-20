package com.tuowei.erp.system.auth;

import com.tuowei.erp.system.auth.service.LoginRateLimiter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.LockedException;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.TimeUnit;

@EnabledIfSystemProperty(named = "erp.testcontainers.enabled", matches = "true")
@Tag("testcontainers")
class LoginRateLimiterRedisIntegrationTest {

    private static final String USERNAME = " Redis_User ";
    private static final String CLIENT_IP = "10.0.0.5";
    private static final String FAILURE_KEY = "erp:login:fail:redis_user:" + CLIENT_IP;
    private static final String LOCK_KEY = "erp:login:lock:redis_user:" + CLIENT_IP;
    private static final String LOCK_MESSAGE = "登录失败次数过多，请15分钟后重试";

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private LoginRateLimiter loginRateLimiter;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        loginRateLimiter = new LoginRateLimiter(redisTemplate);
    }

    @Test
    void failedAttemptsUseRedisCounterTtlAndLockKey() {
        for (int i = 0; i < 4; i++) {
            loginRateLimiter.assertAllowed(USERNAME, CLIENT_IP);
            loginRateLimiter.recordFailure(USERNAME, CLIENT_IP);
        }

        Assertions.assertThatThrownBy(() -> loginRateLimiter.recordFailure(USERNAME, CLIENT_IP))
                .isInstanceOf(LockedException.class)
                .hasMessage(LOCK_MESSAGE);
        Assertions.assertThatThrownBy(() -> loginRateLimiter.assertAllowed(USERNAME, CLIENT_IP))
                .isInstanceOf(LockedException.class)
                .hasMessage(LOCK_MESSAGE);

        Assertions.assertThat(redisTemplate.opsForValue().get(FAILURE_KEY)).isEqualTo("5");
        assertPositiveTtl(FAILURE_KEY);
        assertPositiveTtl(LOCK_KEY);
    }

    @Test
    void successfulLoginClearsRedisFailureAndLockKeys() {
        for (int i = 0; i < 4; i++) {
            loginRateLimiter.recordFailure(USERNAME, CLIENT_IP);
        }
        Assertions.assertThatThrownBy(() -> loginRateLimiter.recordFailure(USERNAME, CLIENT_IP))
                .isInstanceOf(LockedException.class);

        loginRateLimiter.recordSuccess(USERNAME, CLIENT_IP);

        Assertions.assertThat(redisTemplate.hasKey(FAILURE_KEY)).isFalse();
        Assertions.assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
    }

    private void assertPositiveTtl(String key) {
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        Assertions.assertThat(ttlSeconds).isNotNull();
        Assertions.assertThat(ttlSeconds).isBetween(1L, TimeUnit.MINUTES.toSeconds(15));
    }
}

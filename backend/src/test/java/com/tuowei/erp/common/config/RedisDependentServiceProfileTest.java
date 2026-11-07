package com.tuowei.erp.common.config;

import com.tuowei.erp.common.cache.LocalCacheService;
import com.tuowei.erp.common.cache.RedisCacheService;
import com.tuowei.erp.system.auth.service.LoginRateLimiter;
import com.tuowei.erp.system.auth.service.NoOpLoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisDependentServiceProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void prodProfileUsesRedisBackedServices() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCacheService.class);
                    assertThat(context).doesNotHaveBean(LocalCacheService.class);
                    assertThat(context).hasSingleBean(LoginRateLimiter.class);
                    assertThat(context).doesNotHaveBean(NoOpLoginRateLimiter.class);
                });
    }

    @Test
    void testProfileUsesLocalFallbackServices() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalCacheService.class);
                    assertThat(context).doesNotHaveBean(RedisCacheService.class);
                    assertThat(context).hasSingleBean(NoOpLoginRateLimiter.class);
                    assertThat(context).doesNotHaveBean(LoginRateLimiter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            LocalCacheService.class,
            RedisCacheService.class,
            LoginRateLimiter.class,
            NoOpLoginRateLimiter.class
    })
    static class TestConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}

package com.tuowei.erp.common.ratelimit;

import com.tuowei.erp.common.web.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    private MutableClock clock;
    private ClientIpResolver clientIpResolver;
    private RateLimitKeyResolver keyResolver;
    private DefaultListableBeanFactory beanFactory;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-04T00:00:00Z"));
        clientIpResolver = new ClientIpResolver("10.0.0.0/8");
        keyResolver = new RateLimitKeyResolver(clientIpResolver);
        beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("rateLimitKeyResolver", keyResolver);
        aspect = new RateLimitAspect(true, clientIpResolver, keyResolver, beanFactory, clock, 2, 1_000L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void defaultTemplateExpressionUsesTrustedClientIpAndSeparatesEndpoints() throws Throwable {
        RateLimit firstLimit = methodAnnotation("first");
        ProceedingJoinPoint first = joinPoint("first");

        // The proxy is trusted, so two valid forwarded client addresses must
        // receive independent counters even though the remote proxy is equal.
        bindRequest("10.1.2.3", "198.51.100.10");
        assertThat(aspect.rateLimit(first, firstLimit)).isEqualTo("ok");
        bindRequest("10.1.2.3", "198.51.100.11");
        assertThat(aspect.rateLimit(first, firstLimit)).isEqualTo("ok");

        // A different endpoint must not consume the first endpoint's quota.
        RateLimit secondLimit = methodAnnotation("second");
        ProceedingJoinPoint second = joinPoint("second");
        bindRequest("10.1.2.3", "198.51.100.10");
        assertThat(aspect.rateLimit(second, secondLimit)).isEqualTo("ok");
    }

    @Test
    void forgedForwardedForDoesNotBypassTheSameClientCounter() throws Throwable {
        RateLimit limit = methodAnnotation("first");
        ProceedingJoinPoint first = joinPoint("first");

        bindRequest("203.0.113.10", "198.51.100.10");
        assertThat(aspect.rateLimit(first, limit)).isEqualTo("ok");
        bindRequest("203.0.113.10", "198.51.100.11");

        assertThatThrownBy(() -> aspect.rateLimit(first, limit))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void expressionFailureFallsBackToTrustedIpInsteadOfGlobalKey() throws Throwable {
        RateLimit limit = mock(RateLimit.class);
        when(limit.key()).thenReturn("#{@missingRateLimitResolver.resolve()}");
        when(limit.limit()).thenReturn(1);
        when(limit.window()).thenReturn(60);
        ProceedingJoinPoint first = joinPoint("first");

        bindRequest("203.0.113.10", null);
        assertThat(aspect.rateLimit(first, limit)).isEqualTo("ok");
        bindRequest("203.0.113.11", null);
        assertThat(aspect.rateLimit(first, limit)).isEqualTo("ok");
    }

    @Test
    void localCounterMapIsBoundedAndExpiredEntriesCanBeCleaned() throws Throwable {
        RateLimit limit = methodAnnotation("third");
        ProceedingJoinPoint third = joinPoint("third");

        bindRequest("203.0.113.20", null);
        aspect.rateLimit(third, limit);
        bindRequest("203.0.113.21", null);
        aspect.rateLimit(third, limit);
        bindRequest("203.0.113.22", null);
        aspect.rateLimit(third, limit);

        assertThat(aspect.counterCount()).isLessThanOrEqualTo(2);

        clock.advance(Duration.ofSeconds(61));
        aspect.cleanupExpiredCounters();
        assertThat(aspect.counterCount()).isZero();
    }

    @Test
    void rejectsNonPositiveAnnotationParameters() throws Throwable {
        RateLimit invalidLimit = mock(RateLimit.class);
        when(invalidLimit.key()).thenReturn("'fixed'");
        when(invalidLimit.limit()).thenReturn(0);
        when(invalidLimit.window()).thenReturn(60);

        assertThatThrownBy(() -> aspect.rateLimit(joinPoint("first"), invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    private void bindRequest(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RateLimit methodAnnotation(String methodName) throws NoSuchMethodException {
        return TestEndpoints.class.getDeclaredMethod(methodName).getAnnotation(RateLimit.class);
    }

    private ProceedingJoinPoint joinPoint(String methodName) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringTypeName()).thenReturn(TestEndpoints.class.getName());
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        try {
            when(joinPoint.proceed()).thenReturn("ok");
        } catch (Throwable throwable) {
            throw new AssertionError(throwable);
        }
        return joinPoint;
    }

    private static final class TestEndpoints {
        @RateLimit(limit = 1, window = 60)
        private void first() {
        }

        @RateLimit(limit = 1, window = 60)
        private void second() {
        }

        @RateLimit(limit = 10, window = 1)
        private void third() {
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}

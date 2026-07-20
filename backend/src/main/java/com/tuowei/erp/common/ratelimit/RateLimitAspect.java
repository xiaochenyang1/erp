package com.tuowei.erp.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API限流切面 - 基于滑动窗口算法
 */
@Aspect
@Component
public class RateLimitAspect {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final ExpressionParser parser = new SpelExpressionParser();
    private final boolean enabled;

    public RateLimitAspect(@Value("${erp.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        if (!enabled) {
            return pjp.proceed();
        }

        String key = resolveKey(rateLimit.key());

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(rateLimit.limit(), rateLimit.window()));

        if (!counter.tryAcquire()) {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
        }

        return pjp.proceed();
    }

    private String resolveKey(String keyExpression) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("rateLimitKeyResolver", new RateLimitKeyResolver());
            return parser.parseExpression(keyExpression).getValue(context, String.class);
        } catch (Exception e) {
            return "rate_limit:default";
        }
    }

    private static class WindowCounter {
        private final int limit;
        private final long windowMillis;
        private final AtomicLong counter = new AtomicLong(0);
        private volatile long windowStart;

        WindowCounter(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowMillis = windowSeconds * 1000L;
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                counter.set(0);
            }

            long current = counter.get();
            if (current >= limit) {
                return false;
            }

            counter.incrementAndGet();
            return true;
        }
    }

    public static class RateLimitKeyResolver {
        public String resolveIp() {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return "rate_limit:ip:" + ip;
        }
    }
}

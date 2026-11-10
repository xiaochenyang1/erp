package com.tuowei.erp.common.ratelimit;

import com.tuowei.erp.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API限流切面 - 基于固定窗口算法。
 *
 * <p>This component is intentionally an in-process guard for generic APIs.
 * Login requests use the dedicated Redis-backed {@code LoginRateLimiter};
 * keeping the two mechanisms separate avoids counting a login twice. For a
 * generic endpoint that must be shared across instances, use a distributed
 * gateway or a dedicated distributed limiter.</p>
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);
    private static final int DEFAULT_MAX_COUNTERS = 10_000;
    private static final long DEFAULT_CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_KEY_PART_LENGTH = 256;
    private static final ParserContext TEMPLATE_CONTEXT = new TemplateParserContext();

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Object counterLock = new Object();
    private final ExpressionParser parser = new SpelExpressionParser();
    private final boolean enabled;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitKeyResolver rateLimitKeyResolver;
    private final BeanFactory beanFactory;
    private final Clock clock;
    private final int maxCounters;
    private final long cleanupIntervalMillis;
    private final AtomicLong nextCleanupAt = new AtomicLong(0L);

    @Autowired
    public RateLimitAspect(
            @Value("${erp.rate-limit.enabled:true}") boolean enabled,
            ClientIpResolver clientIpResolver,
            RateLimitKeyResolver rateLimitKeyResolver,
            BeanFactory beanFactory,
            Clock clock,
            @Value("${erp.rate-limit.max-counters:10000}") int maxCounters,
            @Value("${erp.rate-limit.cleanup-interval-ms:60000}") long cleanupIntervalMillis
    ) {
        this(enabled, clientIpResolver, rateLimitKeyResolver, beanFactory, clock, maxCounters, cleanupIntervalMillis, true);
    }

    /**
     * Retains the original one-argument constructor for small standalone
     * callers. Spring uses the fully wired constructor above.
     */
    public RateLimitAspect(boolean enabled) {
        this(enabled, standaloneDependencies());
    }

    private RateLimitAspect(boolean enabled, StandaloneDependencies dependencies) {
        this(enabled,
                dependencies.clientIpResolver(),
                dependencies.rateLimitKeyResolver(),
                dependencies.beanFactory(),
                Clock.systemUTC(),
                DEFAULT_MAX_COUNTERS,
                DEFAULT_CLEANUP_INTERVAL_MILLIS,
                false);
    }

    private static StandaloneDependencies standaloneDependencies() {
        ClientIpResolver resolver = new ClientIpResolver("");
        RateLimitKeyResolver keyResolver = new RateLimitKeyResolver(resolver);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("rateLimitKeyResolver", keyResolver);
        return new StandaloneDependencies(resolver, keyResolver, beanFactory);
    }

    private RateLimitAspect(
            boolean enabled,
            ClientIpResolver clientIpResolver,
            RateLimitKeyResolver rateLimitKeyResolver,
            BeanFactory beanFactory,
            Clock clock,
            int maxCounters,
            long cleanupIntervalMillis,
            boolean springWiring
    ) {
        this.enabled = enabled;
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
        this.rateLimitKeyResolver = Objects.requireNonNull(rateLimitKeyResolver, "rateLimitKeyResolver must not be null");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxCounters = maxCounters > 0 ? maxCounters : DEFAULT_MAX_COUNTERS;
        this.cleanupIntervalMillis = cleanupIntervalMillis > 0
                ? cleanupIntervalMillis
                : DEFAULT_CLEANUP_INTERVAL_MILLIS;
        if (springWiring) {
            log.info("通用API限流已启用，最大本地计数器数={}，清理间隔={}ms", this.maxCounters, this.cleanupIntervalMillis);
        }
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        if (!enabled) {
            return pjp.proceed();
        }
        validate(rateLimit);

        String key = resolveKey(pjp, rateLimit.key());
        if (!tryAcquire(key, rateLimit.limit(), rateLimit.window())) {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
        }

        return pjp.proceed();
    }

    /**
     * Scheduler-based cleanup handles keys that receive no subsequent traffic.
     * Requests also perform throttled cleanup so standalone use does not leak
     * counters when scheduling is disabled.
     */
    @Scheduled(fixedDelayString = "${erp.rate-limit.cleanup-interval-ms:60000}")
    public void cleanupExpiredCounters() {
        cleanupExpiredCounters(clock.millis());
    }

    int counterCount() {
        return counters.size();
    }

    /**
     * Counter lookup, eviction and the first increment share one lock.  If
     * lookup returned a counter before eviction, a concurrent request could
     * otherwise keep using the detached counter while a replacement started
     * for the same key.
     */
    private boolean tryAcquire(String key, int limit, int windowSeconds) {
        long now = clock.millis();
        cleanupIfDue(now);

        synchronized (counterLock) {
            WindowCounter current = counters.get(key);
            if (current != null && current.matches(limit, windowSeconds) && !current.isExpired(now)) {
                return current.tryAcquire(now);
            }

            if (current != null) {
                counters.remove(key, current);
            }
            removeExpiredLocked(now);
            while (counters.size() >= maxCounters) {
                if (!evictLeastRecentlyUsedLocked()) {
                    break;
                }
            }

            WindowCounter replacement = new WindowCounter(limit, windowSeconds, now);
            counters.put(key, replacement);
            return replacement.tryAcquire(now);
        }
    }

    private void cleanupIfDue(long now) {
        long due = nextCleanupAt.get();
        if (now < due || !nextCleanupAt.compareAndSet(due, now + cleanupIntervalMillis)) {
            return;
        }
        cleanupExpiredCounters(now);
    }

    private void cleanupExpiredCounters(long now) {
        synchronized (counterLock) {
            removeExpiredLocked(now);
        }
    }

    private void removeExpiredLocked(long now) {
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private boolean evictLeastRecentlyUsedLocked() {
        Map.Entry<String, WindowCounter> oldest = null;
        for (Map.Entry<String, WindowCounter> entry : counters.entrySet()) {
            if (oldest == null || entry.getValue().lastAccess() < oldest.getValue().lastAccess()) {
                oldest = entry;
            }
        }
        return oldest != null && counters.remove(oldest.getKey(), oldest.getValue());
    }

    private String resolveKey(ProceedingJoinPoint pjp, String keyExpression) {
        String dimension = null;
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            // @bean references require a BeanResolver; setting a variable alone
            // silently fails and used to collapse every request to one fallback key.
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
            context.setVariable("rateLimitKeyResolver", rateLimitKeyResolver);
            context.setVariable("request", currentRequest());

            String expression = keyExpression == null ? "" : keyExpression.trim();
            Object value = (expression.startsWith("#{") && expression.endsWith("}"))
                    ? parser.parseExpression(expression, TEMPLATE_CONTEXT).getValue(context)
                    : parser.parseExpression(expression).getValue(context);
            if (value != null) {
                dimension = String.valueOf(value);
            }
        } catch (Exception ex) {
            log.debug("解析限流键表达式失败，将回退到可信客户端IP: {}", keyExpression, ex);
        }

        if (!StringUtils.hasText(dimension)) {
            dimension = resolveTrustedClientIp();
        }
        if (!StringUtils.hasText(dimension)) {
            dimension = "unknown";
        }

        return "rate_limit:" + sanitize(endpointKey(pjp)) + ":" + sanitize(dimension);
    }

    private String resolveTrustedClientIp() {
        String ip = clientIpResolver.resolve(currentRequest());
        return StringUtils.hasText(ip) ? ip : rateLimitKeyResolver.resolveIp();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String endpointKey(ProceedingJoinPoint pjp) {
        Signature signature = pjp.getSignature();
        if (signature instanceof MethodSignature methodSignature) {
            return methodSignature.getDeclaringTypeName() + "#" + methodSignature.getName();
        }
        return signature.toShortString();
    }

    private String sanitize(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "unknown";
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), MAX_KEY_PART_LENGTH));
        for (int index = 0; index < normalized.length() && result.length() < MAX_KEY_PART_LENGTH; index++) {
            char character = normalized.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '.' || character == ':'
                    || character == '_' || character == '-' || character == '/' || character == '$') {
                result.append(character);
            } else {
                result.append('_');
            }
        }
        return result.length() == 0 ? "unknown" : result.toString();
    }

    private void validate(RateLimit rateLimit) {
        if (rateLimit == null) {
            throw new IllegalArgumentException("rateLimit must not be null");
        }
        if (rateLimit.limit() < 1) {
            throw new IllegalArgumentException("rateLimit.limit must be positive");
        }
        if (rateLimit.window() < 1) {
            throw new IllegalArgumentException("rateLimit.window must be positive");
        }
    }

    private static final class WindowCounter {
        private final int limit;
        private final long windowMillis;
        private long count;
        private long windowStart;
        private volatile long lastAccess;

        private WindowCounter(int limit, int windowSeconds, long now) {
            this.limit = limit;
            this.windowMillis = windowSeconds * 1000L;
            this.windowStart = now;
            this.lastAccess = now;
        }

        private synchronized boolean tryAcquire(long now) {
            if (now < windowStart || now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0L;
            }
            lastAccess = now;
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }

        private synchronized boolean isExpired(long now) {
            return now >= windowStart && now - windowStart >= windowMillis;
        }

        private synchronized boolean matches(int expectedLimit, int expectedWindowSeconds) {
            return limit == expectedLimit && windowMillis == expectedWindowSeconds * 1000L;
        }

        private long lastAccess() {
            return lastAccess;
        }
    }

    private record StandaloneDependencies(
            ClientIpResolver clientIpResolver,
            RateLimitKeyResolver rateLimitKeyResolver,
            BeanFactory beanFactory
    ) {
    }
}

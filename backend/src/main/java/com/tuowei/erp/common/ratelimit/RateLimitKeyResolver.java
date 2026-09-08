package com.tuowei.erp.common.ratelimit;

import com.tuowei.erp.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves dimensions used by {@link RateLimit} expressions.
 *
 * <p>The bean is deliberately backed by {@link ClientIpResolver}; rate-limit
 * keys must use the same trusted-proxy rules as audit and login records.</p>
 */
@Component("rateLimitKeyResolver")
public class RateLimitKeyResolver {

    private final ClientIpResolver clientIpResolver;

    public RateLimitKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * Returns the canonical client IP, or {@code unknown} when no request is
     * bound to the current thread.
     */
    public String resolveIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = clientIpResolver.resolve(request);
        return StringUtils.hasText(ip) ? ip : "unknown";
    }
}

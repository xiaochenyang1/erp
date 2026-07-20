package com.tuowei.erp.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流键（支持SpEL表达式）
     * 默认使用IP地址
     */
    String key() default "#{@rateLimitKeyResolver.resolveIp()}";

    /**
     * 时间窗口内允许的请求数
     */
    int limit() default 100;

    /**
     * 时间窗口（秒）
     */
    int window() default 60;
}

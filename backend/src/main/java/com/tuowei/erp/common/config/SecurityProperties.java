package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "erp.security")
public record SecurityProperties(Jwt jwt) {

    public record Jwt(String secret, long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("ERP_JWT_SECRET长度不能小于32字节");
            }
            if (accessTokenTtlSeconds < 60) {
                throw new IllegalArgumentException("access-token-ttl-seconds不能小于60");
            }
            if (refreshTokenTtlSeconds < accessTokenTtlSeconds) {
                throw new IllegalArgumentException("refresh-token-ttl-seconds不能小于access-token-ttl-seconds");
            }
        }
    }
}

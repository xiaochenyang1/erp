package com.tuowei.erp.common.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProductionStartupValidator implements ApplicationRunner {

    private static final int PROD_JWT_SECRET_MIN_BYTES = 48;
    private static final List<String> FORBIDDEN_PROD_COMPANION_PROFILES = List.of("dev", "test", "local");
    private static final Map<String, String> ENV_NAMES_BY_PROPERTY = Map.of(
            "spring.datasource.url", "ERP_DATASOURCE_URL",
            "spring.datasource.username", "ERP_DATASOURCE_USERNAME",
            "spring.datasource.password", "ERP_DATASOURCE_PASSWORD",
            "spring.data.redis.host", "ERP_REDIS_HOST",
            "spring.data.redis.password", "ERP_REDIS_PASSWORD",
            "erp.security.cors.allowed-origins", "ERP_CORS_ALLOWED_ORIGINS",
            "erp.security.jwt.secret", "ERP_JWT_SECRET",
            "erp.attachment.storage-root", "ERP_ATTACHMENT_STORAGE_ROOT"
    );

    private final Environment environment;

    public ProductionStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfile()) {
            return;
        }
        rejectForbiddenCompanionProfiles();

        List<String> requiredProperties = List.of(
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.data.redis.host",
                "spring.data.redis.password",
                "erp.security.cors.allowed-origins",
                "erp.security.jwt.secret",
                "erp.attachment.storage-root"
        );
        for (String property : requiredProperties) {
            requireText(property);
            requireNoPlaceholder(property, ENV_NAMES_BY_PROPERTY.getOrDefault(property, property));
        }

        String jwtSecret = propertyValue("erp.security.jwt.secret", "ERP_JWT_SECRET");
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < PROD_JWT_SECRET_MIN_BYTES) {
            throw new IllegalStateException("生产环境 ERP_JWT_SECRET 长度不能小于48字节");
        }
        String corsAllowedOrigins = propertyValue("erp.security.cors.allowed-origins", "ERP_CORS_ALLOWED_ORIGINS");
        validateCorsAllowedOrigins(corsAllowedOrigins);
        validateAttachmentStorageRoot(propertyValue("erp.attachment.storage-root", "ERP_ATTACHMENT_STORAGE_ROOT"));
        if (environment.getProperty("erp.error.expose-unexpected-message", Boolean.class, false)) {
            throw new IllegalStateException("生产环境禁止暴露未知异常明文");
        }
        if (environment.getProperty("erp.security.public-api-docs-enabled", Boolean.class, false)) {
            throw new IllegalStateException("生产环境禁止开放 Swagger / OpenAPI 接口");
        }
    }

    private void requireText(String property) {
        if (!StringUtils.hasText(propertyValue(property, ENV_NAMES_BY_PROPERTY.getOrDefault(property, property)))) {
            throw new IllegalStateException("生产环境缺少必要配置: " + property);
        }
    }

    private void requireNoPlaceholder(String property, String envName) {
        if (containsPlaceholder(propertyValue(property, envName))) {
            throw new IllegalStateException("生产环境 " + envName + " 不能使用占位符");
        }
    }

    private String propertyValue(String property, String envName) {
        try {
            return environment.getProperty(property, "");
        } catch (RuntimeException ex) {
            if ("org.springframework.util.PlaceholderResolutionException".equals(ex.getClass().getName())) {
                throw new IllegalStateException("生产环境 " + envName + " 不能使用占位符", ex);
            }
            throw ex;
        }
    }

    private void validateCorsAllowedOrigins(String value) {
        Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .forEach(this::validateCorsAllowedOrigin);
    }

    private void validateCorsAllowedOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 包含空 Origin");
        }
        if ("*".equals(origin)) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 禁止使用 *");
        }

        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 包含非法 Origin: " + origin, ex);
        }

        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 只允许 http/https Origin");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 必须是完整 Origin");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 禁止包含用户信息");
        }
        if (StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 禁止包含 query 或 fragment");
        }
        String path = uri.getPath();
        if (StringUtils.hasText(path) && !"/".equals(path)) {
            throw new IllegalStateException("生产环境 ERP_CORS_ALLOWED_ORIGINS 只能配置 Origin，不能包含路径");
        }
    }

    private void validateAttachmentStorageRoot(String value) {
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IllegalStateException("生产环境附件存储目录不是有效目录");
        }
        try {
            Files.createDirectories(root);
            Path probeFile = Files.createTempFile(root, ".erp-write-check-", ".tmp");
            Files.deleteIfExists(probeFile);
        } catch (IOException | SecurityException ex) {
            throw new IllegalStateException("生产环境附件存储目录不可写", ex);
        }
    }

    private boolean containsPlaceholder(String value) {
        return value.contains("${") || value.toUpperCase(Locale.ROOT).contains("CHANGE_ME");
    }

    private void rejectForbiddenCompanionProfiles() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        FORBIDDEN_PROD_COMPANION_PROFILES.stream()
                .filter(activeProfiles::contains)
                .findFirst()
                .ifPresent(profile -> {
                    throw new IllegalStateException("生产环境禁止同时启用 " + profile + " profile");
                });
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}

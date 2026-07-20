package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

@Service
public class NotificationWebhookPublisher {

    public static final String CONFIG_CODE = "notification.webhook.url";

    private static final Logger log = LoggerFactory.getLogger(NotificationWebhookPublisher.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;
    private final BiConsumer<String, String> httpPoster;

    @Autowired
    public NotificationWebhookPublisher(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this(systemConfigMapper, objectMapper, command -> CompletableFuture.runAsync(command), null);
    }

    // test-only overload: package-private, not a Spring bean constructor
    NotificationWebhookPublisher(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            Executor asyncExecutor,
            BiConsumer<String, String> httpPoster
    ) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
        this.asyncExecutor = asyncExecutor == null ? Runnable::run : asyncExecutor;
        this.httpPoster = httpPoster == null ? this::postJson : httpPoster;
    }

    public void publishWorkflowPending(NotificationEntity notification, List<Long> recipientUserIds) {
        try {
            String webhookUrl = resolveWebhookUrl();
            if (!StringUtils.hasText(webhookUrl)) {
                return;
            }
            if (notification == null) {
                return;
            }
            List<Long> recipients = normalizeRecipientIds(recipientUserIds);
            String body = objectMapper.writeValueAsString(new WebhookPayload(
                    notification.getNotificationType(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getBusinessType(),
                    notification.getBusinessId(),
                    notification.getBusinessNo(),
                    notification.getTargetUrl(),
                    recipients
            ));
            String targetUrl = webhookUrl.trim();
            asyncExecutor.execute(() -> {
                try {
                    httpPoster.accept(targetUrl, body);
                } catch (Exception ex) {
                    log.warn("Notification webhook delivery failed: {}", ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("Notification webhook publish skipped: {}", ex.getMessage());
        }
    }

    private String resolveWebhookUrl() {
        SystemConfigEntity config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getConfigCode, CONFIG_CODE)
                .eq(SystemConfigEntity::getDeletedFlag, 0)
                .eq(SystemConfigEntity::getStatus, "ACTIVE")
                .last("limit 1"));
        if (config == null) {
            return null;
        }
        return config.getConfigValue();
    }

    private List<Long> normalizeRecipientIds(List<Long> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(recipientUserIds).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private void postJson(String url, String body) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Notification webhook returned status {}", response.statusCode());
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    record WebhookPayload(
            String type,
            String title,
            String content,
            String businessType,
            Long businessId,
            String businessNo,
            String targetUrl,
            List<Long> recipientUserIds
    ) {
    }
}

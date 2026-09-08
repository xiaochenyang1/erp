package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.NotificationWebhookProperties;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
public class NotificationWebhookPublisher {

    public static final String CONFIG_CODE = "notification.webhook.url";

    private static final Logger log = LoggerFactory.getLogger(NotificationWebhookPublisher.class);

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;
    private final ExecutorService ownedAsyncExecutor;
    private final BiConsumer<String, String> httpPoster;
    private final NotificationWebhookProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public NotificationWebhookPublisher(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            NotificationWebhookProperties properties
    ) {
        this(
                systemConfigMapper,
                objectMapper,
                createExecutor(properties),
                null,
                properties,
                true
        );
    }

    /** Keeps direct construction in older integrations and focused tests compatible. */
    public NotificationWebhookPublisher(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this(systemConfigMapper, objectMapper, NotificationWebhookProperties.defaults());
    }

    // test-only overload: package-private, not a Spring bean constructor
    NotificationWebhookPublisher(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            Executor asyncExecutor,
            BiConsumer<String, String> httpPoster
    ) {
        this(systemConfigMapper, objectMapper, asyncExecutor, httpPoster,
                NotificationWebhookProperties.defaults(), false);
    }

    // test-only overload: allows network policy to be exercised without a Spring context
    NotificationWebhookPublisher(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            Executor asyncExecutor,
            BiConsumer<String, String> httpPoster,
            NotificationWebhookProperties properties
    ) {
        this(systemConfigMapper, objectMapper, asyncExecutor, httpPoster, properties, false);
    }

    private NotificationWebhookPublisher(
            SystemConfigMapper systemConfigMapper,
            ObjectMapper objectMapper,
            Executor asyncExecutor,
            BiConsumer<String, String> httpPoster,
            NotificationWebhookProperties properties,
            boolean ownsExecutor
    ) {
        this.systemConfigMapper = Objects.requireNonNull(systemConfigMapper, "systemConfigMapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = properties == null ? NotificationWebhookProperties.defaults() : properties;
        this.asyncExecutor = asyncExecutor == null ? Runnable::run : asyncExecutor;
        this.ownedAsyncExecutor = ownsExecutor && this.asyncExecutor instanceof ExecutorService executor
                ? executor
                : null;
        this.httpPoster = httpPoster == null ? this::postJson : httpPoster;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void publishWorkflowPending(NotificationEntity notification, List<Long> recipientUserIds) {
        try {
            if (!properties.enabled()) {
                return;
            }
            String webhookUrl = resolveWebhookUrl();
            if (!StringUtils.hasText(webhookUrl)) {
                return;
            }
            if (notification == null) {
                return;
            }
            List<Long> recipients = normalizeRecipientIds(recipientUserIds);
            String targetUrl = validateTargetUrl(webhookUrl);
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
            Runnable dispatch = () -> enqueue(targetUrl, body);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch.run();
                    }
                });
            } else {
                dispatch.run();
            }
        } catch (Exception ex) {
            log.warn("Notification webhook publish skipped: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (ownedAsyncExecutor != null) {
            ownedAsyncExecutor.shutdown();
            try {
                if (!ownedAsyncExecutor.awaitTermination(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    ownedAsyncExecutor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                ownedAsyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void enqueue(String targetUrl, String body) {
        try {
            asyncExecutor.execute(() -> {
                try {
                    httpPoster.accept(targetUrl, body);
                } catch (Exception ex) {
                    log.warn("Notification webhook delivery failed: {}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("Notification webhook queue is full; delivery dropped");
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
            URI target = URI.create(validateTargetUrl(url));
            rejectPrivateAddress(target.getHost());
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Notification webhook returned status {}", response.statusCode());
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private String validateTargetUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("webhook URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("webhook URL 格式无效", ex);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("webhook URL 仅允许 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("webhook URL 缺少主机名");
        }
        if (uri.getPort() < -1 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("webhook URL 端口无效");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("webhook URL 不允许凭据或 fragment");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowedHosts = configuredAllowedHosts();
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(host)) {
            throw new IllegalArgumentException("webhook 主机不在允许列表中");
        }
        // Reject deterministic private targets before queueing. DNS names are
        // checked again immediately before the HTTP request to reduce rebinding
        // risk, while IP literals and localhost need no network lookup here.
        if (isLoopbackHostname(host) || isIpLiteral(host)) {
            try {
                rejectPrivateAddress(host);
            } catch (Exception ex) {
                if (ex instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                throw new IllegalArgumentException("webhook 主机地址无法解析", ex);
            }
        }
        return uri.toString();
    }

    private boolean isLoopbackHostname(String host) {
        return "localhost".equals(host)
                || "localhost.localdomain".equals(host)
                || host.endsWith(".localhost");
    }

    private boolean isIpLiteral(String host) {
        String normalized = host;
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return true;
        }
        return normalized.indexOf(':') >= 0 || normalized.matches("[0-9.]+");
    }

    private Set<String> configuredAllowedHosts() {
        if (!StringUtils.hasText(properties.allowedHosts())) {
            return Set.of();
        }
        return java.util.Arrays.stream(properties.allowedHosts().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void rejectPrivateAddress(String host) throws Exception {
        if (properties.allowPrivateAddresses()) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (isForbiddenAddress(address)) {
                throw new IllegalArgumentException("webhook 主机解析到禁止访问的内网地址");
            }
        }
    }

    private boolean isForbiddenAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isIpv4MetadataAddress(address)
                || isIpv6UniqueLocalAddress(address)) {
            return true;
        }
        return isIpv4MappedAddress(address) && isForbiddenIpv4Bytes(address.getAddress(), 12);
    }

    private boolean isIpv6UniqueLocalAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xff) >= 0xfc && (bytes[0] & 0xff) <= 0xfd;
    }

    private boolean isIpv4MappedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private boolean isForbiddenIpv4Bytes(byte[] bytes, int offset) {
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || first >= 224;
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && (bytes[0] & 0xff) == 100
                && (bytes[1] & 0xff) >= 64
                && (bytes[1] & 0xff) <= 127;
    }

    private boolean isIpv4MetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && (bytes[0] & 0xff) == 169
                && (bytes[1] & 0xff) == 254
                && (bytes[2] & 0xff) == 169
                && (bytes[3] & 0xff) == 254;
    }

    private static ExecutorService createExecutor(NotificationWebhookProperties properties) {
        NotificationWebhookProperties effective = properties == null
                ? NotificationWebhookProperties.defaults()
                : properties;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "erp-notification-webhook");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                effective.poolSize(),
                effective.poolSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(effective.queueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
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

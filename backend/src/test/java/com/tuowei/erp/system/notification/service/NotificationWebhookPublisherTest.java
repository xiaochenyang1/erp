package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.NotificationWebhookProperties;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationWebhookPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SystemConfigEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                SystemConfigEntity.class.getName()
        );
        assistant.setCurrentNamespace(SystemConfigEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SystemConfigEntity.class);
    }

    @Test
    void emptyConfigIsNoOp() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(null);
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        assertThatCode(() -> publisher.publishWorkflowPending(notification(), List.of(1L, 2L)))
                .doesNotThrowAnyException();
        assertThat(posts.get()).isZero();
    }

    @Test
    void blankConfigValueIsNoOp() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("   "));
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        assertThatCode(() -> publisher.publishWorkflowPending(notification(), List.of(9L)))
                .doesNotThrowAnyException();
        assertThat(posts.get()).isZero();
    }

    @Test
    void configuredUrlPostsJsonPayload() throws Exception {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("https://hooks.example.com/erp"));
        List<String> urls = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> {
                    urls.add(url);
                    bodies.add(body);
                }
        );

        NotificationEntity notification = notification();
        assertThatCode(() -> publisher.publishWorkflowPending(notification, List.of(11L, 11L, 22L)))
                .doesNotThrowAnyException();

        assertThat(urls).containsExactly("https://hooks.example.com/erp");
        JsonNode payload = objectMapper.readTree(bodies.get(0));
        assertThat(payload.get("type").asText()).isEqualTo("WORKFLOW_APPROVAL_PENDING");
        assertThat(payload.get("title").asText()).isEqualTo("待审批：采购订单");
        assertThat(payload.get("content").asText()).isEqualTo("单据 PO-001 已提交审批，请及时处理");
        assertThat(payload.get("businessType").asText()).isEqualTo("PURCHASE_ORDER");
        assertThat(payload.get("businessId").asLong()).isEqualTo(1001L);
        assertThat(payload.get("businessNo").asText()).isEqualTo("PO-001");
        assertThat(payload.get("targetUrl").asText()).isEqualTo("/workflow/tasks?businessType=PURCHASE_ORDER&businessId=1001");
        assertThat(payload.get("recipientUserIds").toString()).isEqualTo("[11,22]");
    }

    @Test
    void httpFailureDoesNotThrow() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("https://hooks.example.com/erp"));
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> {
                    throw new IllegalStateException("connection refused");
                }
        );

        assertThatCode(() -> publisher.publishWorkflowPending(notification(), List.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledWebhookDoesNotReadOrPost() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet(),
                new NotificationWebhookProperties(false, 1, 1, java.time.Duration.ofSeconds(1), "", false)
        );

        publisher.publishWorkflowPending(notification(), List.of(1L));

        assertThat(posts.get()).isZero();
        org.mockito.Mockito.verifyNoInteractions(configMapper);
    }

    @Test
    void allowlistRejectsUnexpectedWebhookHost() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("https://hooks.example.com/erp"));
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet(),
                new NotificationWebhookProperties(true, 1, 1, java.time.Duration.ofSeconds(1), "allowed.example.com", false)
        );

        publisher.publishWorkflowPending(notification(), List.of(1L));

        assertThat(posts.get()).isZero();
    }

    @Test
    void webhookIsQueuedOnlyAfterCurrentTransactionCommits() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("https://hooks.example.com/erp"));
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishWorkflowPending(notification(), List.of(1L));
            assertThat(posts.get()).isZero();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCommit()
            );
            assertThat(posts.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void malformedAndCredentialUrlsAreRejectedBeforeDispatch() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        when(configMapper.selectOne(any())).thenReturn(config("file:///tmp/hook"));
        publisher.publishWorkflowPending(notification(), List.of(1L));
        when(configMapper.selectOne(any())).thenReturn(config("https://user:pass@hooks.example.com/erp"));
        publisher.publishWorkflowPending(notification(), List.of(1L));

        assertThat(posts.get()).isZero();
    }

    @Test
    void loopbackAndPrivateAddressLiteralsAreRejectedBeforeDispatch() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(
                config("http://localhost/hook"),
                config("http://127.0.0.1/hook"),
                config("http://10.0.0.1/hook"),
                config("http://[::1]/hook"),
                config("http://[fd00::1]/hook")
        );
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> publisher.publishWorkflowPending(notification(), List.of(1L)))
                    .doesNotThrowAnyException();
        }

        assertThat(posts.get()).isZero();
    }

    @Test
    void explicitPrivateAddressOptInIsHonored() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("http://127.0.0.1/hook"));
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet(),
                new NotificationWebhookProperties(true, 1, 1, java.time.Duration.ofSeconds(1), "", true)
        );

        publisher.publishWorkflowPending(notification(), List.of(1L));

        assertThat(posts.get()).isEqualTo(1);
    }

    @Test
    void invalidPortIsRejectedBeforeDispatch() {
        SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
        when(configMapper.selectOne(any())).thenReturn(config("https://hooks.example.com:65536/erp"));
        AtomicInteger posts = new AtomicInteger();
        NotificationWebhookPublisher publisher = new NotificationWebhookPublisher(
                configMapper,
                objectMapper,
                Runnable::run,
                (url, body) -> posts.incrementAndGet()
        );

        publisher.publishWorkflowPending(notification(), List.of(1L));

        assertThat(posts.get()).isZero();
    }

    private static SystemConfigEntity config(String value) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigCode(NotificationWebhookPublisher.CONFIG_CODE);
        entity.setConfigValue(value);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static NotificationEntity notification() {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(501L);
        entity.setNotificationType("WORKFLOW_APPROVAL_PENDING");
        entity.setTitle("待审批：采购订单");
        entity.setContent("单据 PO-001 已提交审批，请及时处理");
        entity.setBusinessType("PURCHASE_ORDER");
        entity.setBusinessId(1001L);
        entity.setBusinessNo("PO-001");
        entity.setTargetUrl("/workflow/tasks?businessType=PURCHASE_ORDER&businessId=1001");
        return entity;
    }
}

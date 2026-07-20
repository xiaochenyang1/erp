package com.tuowei.erp.system.notification.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

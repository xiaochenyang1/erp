package com.tuowei.erp.system.notification;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.mapper.NotificationRecipientMapper;
import com.tuowei.erp.system.notification.model.NotificationEntity;
import com.tuowei.erp.system.notification.model.NotificationRecipientEntity;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.system.notification.service.NotificationWebhookPublisher;
import com.tuowei.erp.system.notification.web.NotificationResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class NotificationServiceQueryDefaultsTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(NotificationRecipientEntity.class);
        initTableInfo(NotificationEntity.class);
    }

    @Test
    void listMineTreatsNullQueryAsDefaultPagination() {
        NotificationRecipientMapper recipientMapper = mock(NotificationRecipientMapper.class);
        NotificationMapper notificationMapper = mock(NotificationMapper.class);
        when(recipientMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<NotificationRecipientEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(recipient()));
            return page;
        });
        when(notificationMapper.selectList(any())).thenReturn(List.of(notification()));
        NotificationService service = new NotificationService(
                notificationMapper,
                recipientMapper,
                auditFactory(),
                mock(NotificationWebhookPublisher.class)
        );

        PageResponse<NotificationResponse> response = service.listMine(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(NotificationResponse::title).containsExactly("待审批");

        ArgumentCaptor<Page<NotificationRecipientEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<NotificationRecipientEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recipientMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id")
                .contains("recipient_user_id")
                .contains("status");
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private static NotificationRecipientEntity recipient() {
        NotificationRecipientEntity entity = new NotificationRecipientEntity();
        entity.setId(11L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setNotificationId(21L);
        entity.setRecipientUserId(AUDIT.userId());
        entity.setReadFlag(0);
        entity.setStatus("ACTIVE");
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private static NotificationEntity notification() {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(21L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCategory("TODO");
        entity.setNotificationType("WORKFLOW_APPROVAL_PENDING");
        entity.setTitle("待审批");
        entity.setContent("有单据待审批");
        entity.setBusinessType("WORKFLOW");
        entity.setBusinessId(31L);
        entity.setBusinessNo("WF-001");
        entity.setTargetUrl("/workflow/tasks");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

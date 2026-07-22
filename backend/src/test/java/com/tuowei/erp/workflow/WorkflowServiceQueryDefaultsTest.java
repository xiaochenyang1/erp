package com.tuowei.erp.workflow;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.notification.service.NotificationService;
import com.tuowei.erp.workflow.mapper.WorkflowInstanceMapper;
import com.tuowei.erp.workflow.mapper.WorkflowRecordMapper;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowRecordEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import com.tuowei.erp.workflow.service.WorkflowApprovalConfigService;
import com.tuowei.erp.workflow.service.WorkflowService;
import com.tuowei.erp.workflow.web.WorkflowRecordResponse;
import com.tuowei.erp.workflow.web.WorkflowTaskResponse;
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
class WorkflowServiceQueryDefaultsTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(WorkflowTaskEntity.class);
        initTableInfo(WorkflowRecordEntity.class);
    }

    @Test
    void listTasksTreatsNullQueryAsDefaultPagination() {
        WorkflowTaskMapper taskMapper = mock(WorkflowTaskMapper.class);
        when(taskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<WorkflowTaskEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(task()));
            return page;
        });
        WorkflowService service = service(taskMapper, mock(WorkflowRecordMapper.class));

        PageResponse<WorkflowTaskResponse> response = service.listTasks(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(WorkflowTaskResponse::businessNo).containsExactly("SO-001");

        ArgumentCaptor<Page<WorkflowTaskEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<WorkflowTaskEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertDefaultPage(pageCaptor.getValue());
        assertScopedInstanceSql(wrapperCaptor.getValue());
    }

    @Test
    void listRecordsTreatsNullQueryAsDefaultPagination() {
        WorkflowRecordMapper recordMapper = mock(WorkflowRecordMapper.class);
        when(recordMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<WorkflowRecordEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(record()));
            return page;
        });
        WorkflowService service = service(mock(WorkflowTaskMapper.class), recordMapper);

        PageResponse<WorkflowRecordResponse> response = service.listRecords(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(WorkflowRecordResponse::businessNo).containsExactly("SO-001");

        ArgumentCaptor<Page<WorkflowRecordEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<WorkflowRecordEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertDefaultPage(pageCaptor.getValue());
        assertScopedInstanceSql(wrapperCaptor.getValue());
    }

    private static WorkflowService service(WorkflowTaskMapper taskMapper, WorkflowRecordMapper recordMapper) {
        return new WorkflowService(
                mock(WorkflowInstanceMapper.class),
                taskMapper,
                recordMapper,
                auditFactory(),
                mock(SystemLogService.class),
                mock(CurrentUserContext.class),
                mock(NotificationService.class),
                mock(WorkflowApprovalConfigService.class),
                mock(com.tuowei.erp.system.user.mapper.UserMapper.class)
        );
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private static void assertDefaultPage(Page<?> page) {
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private static void assertScopedInstanceSql(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase())
                .contains("wf_approval_instance")
                .contains("deleted_flag")
                .contains("company_id = " + AUDIT.companyId())
                .contains("account_book_id = " + AUDIT.accountBookId())
                .contains("order by");
    }

    private static WorkflowTaskEntity task() {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(1L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setInstanceId(11L);
        entity.setBusinessType("SALES_ORDER");
        entity.setBusinessId(21L);
        entity.setBusinessNo("SO-001");
        entity.setTitle("销售订单审批");
        entity.setStatus("PENDING");
        entity.setCreatedTime(AUDIT.now());
        entity.setUpdatedTime(AUDIT.now());
        return entity;
    }

    private static WorkflowRecordEntity record() {
        WorkflowRecordEntity entity = new WorkflowRecordEntity();
        entity.setId(2L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setInstanceId(11L);
        entity.setBusinessType("SALES_ORDER");
        entity.setBusinessId(21L);
        entity.setBusinessNo("SO-001");
        entity.setAction("SUBMIT");
        entity.setOperatorUserId(AUDIT.userId());
        entity.setActionTime(AUDIT.now());
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

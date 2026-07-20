package com.tuowei.erp.system.log;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.log.mapper.AuditLogMapper;
import com.tuowei.erp.system.log.mapper.LoginLogMapper;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemOperationLogExportServiceTest {

    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            9701L,
            101L,
            202L,
            11L,
            12L,
            "admin",
            "管理员",
            "N/A",
            Set.of("system:log:view"),
            DataScopeSnapshot.all()
    );

    private final OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(OperationLogEntity.class);
    }

    @Test
    void getOperationLogReturnsOnlyCurrentTenantRecord() {
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(operationLogMapper.selectById(9101L)).thenReturn(operationLog(PRINCIPAL.companyId(), PRINCIPAL.accountBookId()));

        OperationLogResponse response = service().getOperationLog(9101L);

        assertThat(response.id()).isEqualTo(9101L);
        assertThat(response.module()).isEqualTo("purchase");
        assertThat(response.operation()).isEqualTo("post");
    }

    @Test
    void getOperationLogRejectsDifferentAccountBookRecord() {
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(operationLogMapper.selectById(9101L)).thenReturn(operationLog(PRINCIPAL.companyId(), 999L));

        assertThatThrownBy(() -> service().getOperationLog(9101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("操作日志不存在");
    }

    @Test
    void exportOperationLogsWritesTenantScopedCsvRows() throws Exception {
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(operationLogMapper.selectList(any())).thenReturn(List.of(operationLog(PRINCIPAL.companyId(), PRINCIPAL.accountBookId())));

        OperationLogPageQuery query = new OperationLogPageQuery();
        query.setModule("purchase");
        query.setOperation("post");
        query.setBizNo("GR-001");
        query.setResult("success");
        query.setOperationTimeFrom(LocalDateTime.of(2026, 6, 1, 0, 0));
        query.setOperationTimeTo(LocalDateTime.of(2026, 6, 30, 23, 59, 59));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service().exportOperationLogs(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,userId,username,module,operation,bizNo,result,message,requestMethod,requestUri,operationTime\r\n");
        assertThat(csv).contains("9101,9701,admin,purchase,post,GR-001,SUCCESS,posted,POST,/api/purchase/receipts/7001/post,2026-06-18T10:30\r\n");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<OperationLogEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(operationLogMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("module")
                .contains("operation")
                .contains("biz_no")
                .contains("result")
                .contains("operation_time");
    }

    private SystemLogService service() {
        return new SystemLogService(
                mock(LoginLogMapper.class),
                operationLogMapper,
                mock(AuditLogMapper.class),
                currentUserContext,
                mock(UserMapper.class),
                mock(ClientIpResolver.class),
                Clock.systemUTC()
        );
    }

    private static OperationLogEntity operationLog(Long companyId, Long accountBookId) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(9101L);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setUserId(9701L);
        entity.setUsername("admin");
        entity.setModule("purchase");
        entity.setOperation("post");
        entity.setBizNo("GR-001");
        entity.setResult("SUCCESS");
        entity.setMessage("posted");
        entity.setRequestMethod("POST");
        entity.setRequestUri("/api/purchase/receipts/7001/post");
        entity.setOperationTime(LocalDateTime.of(2026, 6, 18, 10, 30));
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

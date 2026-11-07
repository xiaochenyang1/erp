package com.tuowei.erp.system.log;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.log.mapper.AuditLogMapper;
import com.tuowei.erp.system.log.mapper.LoginLogMapper;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.AuditLogEntity;
import com.tuowei.erp.system.log.model.LoginLogEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.system.log.service.SystemLogQueryService;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.AuditLogResponse;
import com.tuowei.erp.system.log.web.LoginLogResponse;
import com.tuowei.erp.system.log.web.OperationLogResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SystemLogServiceQueryDefaultsTest {

    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            9L,
            101L,
            202L,
            "admin",
            "管理员",
            "secret",
            Set.of("system:log:list")
    );
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-01-02T03:04:05");

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(LoginLogEntity.class);
        initTableInfo(OperationLogEntity.class);
        initTableInfo(AuditLogEntity.class);
    }

    @Test
    void listLoginLogsTreatsNullQueryAsDefaultPagination() {
        LoginLogMapper loginLogMapper = mock(LoginLogMapper.class);
        when(loginLogMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<LoginLogEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(loginLog()));
            return page;
        });
        SystemLogQueryService service = queryService(
                loginLogMapper,
                mock(OperationLogMapper.class),
                mock(AuditLogMapper.class)
        );

        PageResponse<LoginLogResponse> response = service.listLoginLogs(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(LoginLogResponse::username).containsExactly("admin");

        ArgumentCaptor<Page<LoginLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<LoginLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertDefaultPage(pageCaptor.getValue());
        assertTenantSql(wrapperCaptor.getValue());
    }

    @Test
    void listOperationLogsTreatsNullQueryAsDefaultPagination() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        when(operationLogMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<OperationLogEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(operationLog()));
            return page;
        });
        SystemLogQueryService service = queryService(
                mock(LoginLogMapper.class),
                operationLogMapper,
                mock(AuditLogMapper.class)
        );

        PageResponse<OperationLogResponse> response = service.listOperationLogs(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(OperationLogResponse::operation).containsExactly("CREATE");

        ArgumentCaptor<Page<OperationLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<OperationLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(operationLogMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertDefaultPage(pageCaptor.getValue());
        assertTenantSql(wrapperCaptor.getValue());
    }

    @Test
    void listAuditLogsTreatsNullQueryAsDefaultPagination() {
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        when(auditLogMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AuditLogEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(auditLog()));
            return page;
        });
        SystemLogQueryService service = queryService(
                mock(LoginLogMapper.class),
                mock(OperationLogMapper.class),
                auditLogMapper
        );

        PageResponse<AuditLogResponse> response = service.listAuditLogs(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(AuditLogResponse::businessNo).containsExactly("SO-001");

        ArgumentCaptor<Page<AuditLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<AuditLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(auditLogMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertDefaultPage(pageCaptor.getValue());
        assertTenantSql(wrapperCaptor.getValue());
    }

    @Test
    void recordLoginSanitizesUserAgentBeforePersistingLoginLog() {
        LoginLogMapper loginLogMapper = mock(LoginLogMapper.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "ERP\r\nClient\t/1.0");
        SystemLogService service = writeService(
                loginLogMapper,
                mock(OperationLogMapper.class),
                mock(AuditLogMapper.class)
        );

        service.recordLoginSuccess(PRINCIPAL.userId(), PRINCIPAL.username(), "登录成功", request);

        ArgumentCaptor<LoginLogEntity> entityCaptor = ArgumentCaptor.forClass(LoginLogEntity.class);
        verify(loginLogMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserAgent()).isEqualTo("ERP Client /1.0");
    }

    private static SystemLogService writeService(
            LoginLogMapper loginLogMapper,
            OperationLogMapper operationLogMapper,
            AuditLogMapper auditLogMapper
    ) {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        return new SystemLogService(
                loginLogMapper,
                operationLogMapper,
                auditLogMapper,
                currentUserContext,
                mock(UserMapper.class),
                mock(ClientIpResolver.class),
                Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC),
                new SystemLogQueryService(loginLogMapper, operationLogMapper, auditLogMapper, currentUserContext)
        );
    }

    private static SystemLogQueryService queryService(
            LoginLogMapper loginLogMapper,
            OperationLogMapper operationLogMapper,
            AuditLogMapper auditLogMapper
    ) {
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        return new SystemLogQueryService(
                loginLogMapper,
                operationLogMapper,
                auditLogMapper,
                currentUserContext
        );
    }

    private static void assertDefaultPage(Page<?> page) {
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private static void assertTenantSql(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase())
                .contains("company_id")
                .contains("account_book_id")
                .contains("order by");
    }

    private static LoginLogEntity loginLog() {
        LoginLogEntity entity = new LoginLogEntity();
        entity.setId(1L);
        entity.setCompanyId(PRINCIPAL.companyId());
        entity.setAccountBookId(PRINCIPAL.accountBookId());
        entity.setUserId(PRINCIPAL.userId());
        entity.setUsername("admin");
        entity.setResult("SUCCESS");
        entity.setMessage("登录成功");
        entity.setLoginTime(NOW);
        return entity;
    }

    private static OperationLogEntity operationLog() {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(2L);
        entity.setCompanyId(PRINCIPAL.companyId());
        entity.setAccountBookId(PRINCIPAL.accountBookId());
        entity.setUserId(PRINCIPAL.userId());
        entity.setUsername("admin");
        entity.setModule("SALE");
        entity.setOperation("CREATE");
        entity.setBizNo("SO-001");
        entity.setResult("SUCCESS");
        entity.setOperationTime(NOW);
        return entity;
    }

    private static AuditLogEntity auditLog() {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(3L);
        entity.setCompanyId(PRINCIPAL.companyId());
        entity.setAccountBookId(PRINCIPAL.accountBookId());
        entity.setAuditType("BUSINESS");
        entity.setBusinessType("SALE_ORDER");
        entity.setBusinessId(11L);
        entity.setBusinessNo("SO-001");
        entity.setAction("APPROVE");
        entity.setOperatorId(PRINCIPAL.userId());
        entity.setOperatorName("admin");
        entity.setAuditTime(NOW);
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

package com.tuowei.erp.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.log.mapper.AuditLogMapper;
import com.tuowei.erp.system.log.mapper.LoginLogMapper;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.AuditLogEntity;
import com.tuowei.erp.system.log.model.LoginLogEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.AuditLogResponse;
import com.tuowei.erp.system.log.web.LoginLogPageQuery;
import com.tuowei.erp.system.log.web.LoginLogResponse;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tenant-scoped system log querying, response mapping and operation-log export.
 */
@Service
public class SystemLogQueryService {

    private static final List<String> OPERATION_LOG_EXPORT_HEADERS = List.of(
            "id",
            "userId",
            "username",
            "module",
            "operation",
            "bizNo",
            "result",
            "message",
            "requestMethod",
            "requestUri",
            "operationTime"
    );

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final CurrentUserContext currentUserContext;

    public SystemLogQueryService(
            LoginLogMapper loginLogMapper,
            OperationLogMapper operationLogMapper,
            AuditLogMapper auditLogMapper,
            CurrentUserContext currentUserContext
    ) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.currentUserContext = currentUserContext;
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginLogResponse> listLoginLogs(LoginLogPageQuery query) {
        LoginLogPageQuery safeQuery = safeQuery(query);
        Page<LoginLogEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<LoginLogEntity> result = loginLogMapper.selectPage(page, buildLoginQuery(safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toLoginResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationLogResponse> listOperationLogs(OperationLogPageQuery query) {
        OperationLogPageQuery safeQuery = safeQuery(query);
        Page<OperationLogEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<OperationLogEntity> result = operationLogMapper.selectPage(page, buildOperationQuery(safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toOperationResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public OperationLogResponse getOperationLog(Long id) {
        OperationLogEntity entity = operationLogMapper.selectById(id);
        TenantScope tenantScope = requireCurrentTenantScope();
        if (entity == null
                || !tenantScope.companyId().equals(entity.getCompanyId())
                || !tenantScope.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("操作日志不存在");
        }
        return toOperationResponse(entity);
    }

    public StreamingResponseBody exportOperationLogs(OperationLogPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OperationLogPageQuery safeQuery = safeQuery(query);
        return outputStream -> withAuthentication(
                authentication,
                () -> CsvExport.write(outputStream, OPERATION_LOG_EXPORT_HEADERS, rowWriter -> {
                    for (OperationLogEntity entity : operationLogMapper.selectList(buildOperationQuery(safeQuery))) {
                        rowWriter.write(operationLogExportRow(entity));
                    }
                })
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAuditLogs(AuditLogPageQuery query) {
        AuditLogPageQuery safeQuery = safeQuery(query);
        Page<AuditLogEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<AuditLogEntity> result = auditLogMapper.selectPage(page, buildAuditQuery(safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toAuditResponse).toList()
        );
    }

    private LambdaQueryWrapper<LoginLogEntity> buildLoginQuery(LoginLogPageQuery query) {
        TenantScope tenantScope = requireCurrentTenantScope();
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .eq(LoginLogEntity::getCompanyId, tenantScope.companyId())
                .eq(LoginLogEntity::getAccountBookId, tenantScope.accountBookId());
        if (query.getUserId() != null) {
            wrapper.eq(LoginLogEntity::getUserId, query.getUserId());
        }
        String username = normalizeNullable(query.getUsername());
        if (StringUtils.hasText(username)) {
            wrapper.eq(LoginLogEntity::getUsername, username);
        }
        String result = normalizeNullable(query.getResult());
        if (StringUtils.hasText(result)) {
            wrapper.eq(LoginLogEntity::getResult, result.toUpperCase(Locale.ROOT));
        }
        if (query.getLoginTimeFrom() != null) {
            wrapper.ge(LoginLogEntity::getLoginTime, query.getLoginTimeFrom());
        }
        if (query.getLoginTimeTo() != null) {
            wrapper.le(LoginLogEntity::getLoginTime, query.getLoginTimeTo());
        }
        return wrapper.orderByDesc(LoginLogEntity::getLoginTime).orderByDesc(LoginLogEntity::getId);
    }

    private LambdaQueryWrapper<OperationLogEntity> buildOperationQuery(OperationLogPageQuery query) {
        TenantScope tenantScope = requireCurrentTenantScope();
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getCompanyId, tenantScope.companyId())
                .eq(OperationLogEntity::getAccountBookId, tenantScope.accountBookId());
        if (query.getUserId() != null) {
            wrapper.eq(OperationLogEntity::getUserId, query.getUserId());
        }
        String username = normalizeNullable(query.getUsername());
        if (StringUtils.hasText(username)) {
            wrapper.eq(OperationLogEntity::getUsername, username);
        }
        String module = normalizeNullable(query.getModule());
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLogEntity::getModule, module);
        }
        String operation = normalizeNullable(query.getOperation());
        if (StringUtils.hasText(operation)) {
            wrapper.eq(OperationLogEntity::getOperation, operation);
        }
        String bizNo = normalizeNullable(query.getBizNo());
        if (StringUtils.hasText(bizNo)) {
            wrapper.eq(OperationLogEntity::getBizNo, bizNo);
        }
        String result = normalizeNullable(query.getResult());
        if (StringUtils.hasText(result)) {
            wrapper.eq(OperationLogEntity::getResult, result.toUpperCase(Locale.ROOT));
        }
        if (query.getOperationTimeFrom() != null) {
            wrapper.ge(OperationLogEntity::getOperationTime, query.getOperationTimeFrom());
        }
        if (query.getOperationTimeTo() != null) {
            wrapper.le(OperationLogEntity::getOperationTime, query.getOperationTimeTo());
        }
        return wrapper.orderByDesc(OperationLogEntity::getOperationTime).orderByDesc(OperationLogEntity::getId);
    }

    private LambdaQueryWrapper<AuditLogEntity> buildAuditQuery(AuditLogPageQuery query) {
        TenantScope tenantScope = requireCurrentTenantScope();
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getCompanyId, tenantScope.companyId())
                .eq(AuditLogEntity::getAccountBookId, tenantScope.accountBookId());
        String auditType = normalizeNullable(query.getAuditType());
        if (StringUtils.hasText(auditType)) {
            wrapper.eq(AuditLogEntity::getAuditType, auditType.toUpperCase(Locale.ROOT));
        }
        String businessType = normalizeNullable(query.getBusinessType());
        if (StringUtils.hasText(businessType)) {
            wrapper.eq(AuditLogEntity::getBusinessType, businessType.toUpperCase(Locale.ROOT));
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(AuditLogEntity::getBusinessId, query.getBusinessId());
        }
        String businessNo = normalizeNullable(query.getBusinessNo());
        if (StringUtils.hasText(businessNo)) {
            wrapper.eq(AuditLogEntity::getBusinessNo, businessNo);
        }
        String action = normalizeNullable(query.getAction());
        if (StringUtils.hasText(action)) {
            wrapper.eq(AuditLogEntity::getAction, action.toUpperCase(Locale.ROOT));
        }
        if (query.getOperatorId() != null) {
            wrapper.eq(AuditLogEntity::getOperatorId, query.getOperatorId());
        }
        String operatorName = normalizeNullable(query.getOperatorName());
        if (StringUtils.hasText(operatorName)) {
            wrapper.eq(AuditLogEntity::getOperatorName, operatorName);
        }
        if (query.getAuditTimeFrom() != null) {
            wrapper.ge(AuditLogEntity::getAuditTime, query.getAuditTimeFrom());
        }
        if (query.getAuditTimeTo() != null) {
            wrapper.le(AuditLogEntity::getAuditTime, query.getAuditTimeTo());
        }
        return wrapper.orderByDesc(AuditLogEntity::getAuditTime).orderByDesc(AuditLogEntity::getId);
    }

    private LoginLogPageQuery safeQuery(LoginLogPageQuery query) {
        return query == null ? new LoginLogPageQuery() : query;
    }

    private OperationLogPageQuery safeQuery(OperationLogPageQuery query) {
        return query == null ? new OperationLogPageQuery() : query;
    }

    private AuditLogPageQuery safeQuery(AuditLogPageQuery query) {
        return query == null ? new AuditLogPageQuery() : query;
    }

    private TenantScope requireCurrentTenantScope() {
        TenantScope tenantScope = currentTenantScopeOrNull();
        if (tenantScope == null) {
            throw new IllegalStateException("查询系统日志缺少当前登录用户");
        }
        return tenantScope;
    }

    private TenantScope currentTenantScopeOrNull() {
        try {
            ErpPrincipal principal = currentUserContext.requirePrincipal();
            return tenantScope(principal.companyId(), principal.accountBookId());
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private TenantScope tenantScope(Long companyId, Long accountBookId) {
        if (companyId == null || accountBookId == null) {
            return null;
        }
        return new TenantScope(companyId, accountBookId);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private LoginLogResponse toLoginResponse(LoginLogEntity entity) {
        return new LoginLogResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getResult(),
                entity.getMessage(),
                entity.getLoginIp(),
                entity.getUserAgent(),
                entity.getLoginTime()
        );
    }

    private OperationLogResponse toOperationResponse(OperationLogEntity entity) {
        return new OperationLogResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getModule(),
                entity.getOperation(),
                entity.getBizNo(),
                entity.getResult(),
                entity.getMessage(),
                entity.getRequestMethod(),
                entity.getRequestUri(),
                entity.getOperationTime()
        );
    }

    private List<?> operationLogExportRow(OperationLogEntity entity) {
        return Arrays.asList(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getModule(),
                entity.getOperation(),
                entity.getBizNo(),
                entity.getResult(),
                entity.getMessage(),
                entity.getRequestMethod(),
                entity.getRequestUri(),
                entity.getOperationTime()
        );
    }

    private AuditLogResponse toAuditResponse(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getAuditType(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getAction(),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getSnapshotJson(),
                entity.getMessage(),
                entity.getAuditTime()
        );
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    private record TenantScope(Long companyId, Long accountBookId) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}

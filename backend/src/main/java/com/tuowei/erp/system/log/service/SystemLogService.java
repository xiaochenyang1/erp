package com.tuowei.erp.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.common.web.HeaderValueSanitizer;
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
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SystemLogService {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final CurrentUserContext currentUserContext;
    private final UserMapper userMapper;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    private final SystemLogQueryService queryService;

    public SystemLogService(
            LoginLogMapper loginLogMapper,
            OperationLogMapper operationLogMapper,
            AuditLogMapper auditLogMapper,
            CurrentUserContext currentUserContext,
            UserMapper userMapper,
            ClientIpResolver clientIpResolver,
            Clock clock,
            SystemLogQueryService queryService
    ) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.currentUserContext = currentUserContext;
        this.userMapper = userMapper;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
        this.queryService = queryService;
    }

    @Transactional
    public void recordLoginSuccess(Long userId, String username, String message, HttpServletRequest request) {
        recordLogin(userId, username, "SUCCESS", message, request);
    }

    @Transactional
    public void recordLoginFailure(String username, String message, HttpServletRequest request) {
        recordLogin(null, username, "FAILURE", message, request);
    }

    @Transactional
    public void recordOperation(
            ErpPrincipal principal,
            String module,
            String operation,
            String bizNo,
            String result,
            String message,
            HttpServletRequest request
    ) {
        OperationLogEntity entity = new OperationLogEntity();
        TenantScope tenantScope = resolveTenantScope(principal);
        applyTenant(entity, tenantScope);
        if (principal != null) {
            entity.setUserId(principal.userId());
            entity.setUsername(principal.username());
        }
        entity.setModule(module);
        entity.setOperation(operation);
        entity.setBizNo(bizNo);
        entity.setResult(result);
        entity.setMessage(message);
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUri(request.getRequestURI());
        }
        entity.setOperationTime(LocalDateTime.now(clock));
        operationLogMapper.insert(entity);
    }

    @Transactional
    public void recordAudit(
            String auditType,
            String businessType,
            Long businessId,
            String businessNo,
            String action,
            Long operatorId,
            String operatorName,
            String snapshotJson,
            String message,
            LocalDateTime auditTime
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        TenantScope tenantScope = resolveTenantScope(operatorId, operatorName);
        applyTenant(entity, tenantScope);
        entity.setAuditType(normalizeRequired(auditType).toUpperCase(Locale.ROOT));
        entity.setBusinessType(normalizeRequired(businessType).toUpperCase(Locale.ROOT));
        entity.setBusinessId(businessId);
        entity.setBusinessNo(normalizeNullable(businessNo));
        entity.setAction(normalizeRequired(action).toUpperCase(Locale.ROOT));
        entity.setOperatorId(operatorId);
        entity.setOperatorName(normalizeNullable(operatorName));
        entity.setSnapshotJson(snapshotJson);
        entity.setMessage(message);
        entity.setAuditTime(auditTime == null ? LocalDateTime.now(clock) : auditTime);
        auditLogMapper.insert(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginLogResponse> listLoginLogs(LoginLogPageQuery query) {
        return queryService.listLoginLogs(query);
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationLogResponse> listOperationLogs(OperationLogPageQuery query) {
        return queryService.listOperationLogs(query);
    }

    @Transactional(readOnly = true)
    public OperationLogResponse getOperationLog(Long id) {
        return queryService.getOperationLog(id);
    }

    public StreamingResponseBody exportOperationLogs(OperationLogPageQuery query) {
        return queryService.exportOperationLogs(query);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAuditLogs(AuditLogPageQuery query) {
        return queryService.listAuditLogs(query);
    }

    private void recordLogin(Long userId, String username, String result, String message, HttpServletRequest request) {
        LoginLogEntity entity = new LoginLogEntity();
        TenantScope tenantScope = resolveTenantScope(userId, username);
        applyTenant(entity, tenantScope);
        entity.setUserId(userId);
        entity.setUsername(StringUtils.hasText(username) ? username.trim() : "");
        entity.setResult(result);
        entity.setMessage(message);
        entity.setLoginIp(clientIpResolver.resolve(request));
        entity.setUserAgent(resolveUserAgent(request));
        entity.setLoginTime(LocalDateTime.now(clock));
        loginLogMapper.insert(entity);
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return HeaderValueSanitizer.sanitize(request.getHeader("User-Agent"), 512);
    }

    private void applyTenant(LoginLogEntity entity, TenantScope tenantScope) {
        if (tenantScope == null) {
            return;
        }
        entity.setCompanyId(tenantScope.companyId());
        entity.setAccountBookId(tenantScope.accountBookId());
    }

    private void applyTenant(OperationLogEntity entity, TenantScope tenantScope) {
        if (tenantScope == null) {
            return;
        }
        entity.setCompanyId(tenantScope.companyId());
        entity.setAccountBookId(tenantScope.accountBookId());
    }

    private void applyTenant(AuditLogEntity entity, TenantScope tenantScope) {
        if (tenantScope == null) {
            return;
        }
        entity.setCompanyId(tenantScope.companyId());
        entity.setAccountBookId(tenantScope.accountBookId());
    }

    private TenantScope resolveTenantScope(ErpPrincipal principal) {
        TenantScope principalScope = principal == null ? null : tenantScope(principal.companyId(), principal.accountBookId());
        return principalScope == null ? currentTenantScopeOrNull() : principalScope;
    }

    private TenantScope resolveTenantScope(Long userId, String username) {
        TenantScope userScope = resolveTenantScopeFromUser(userId, username);
        return userScope == null ? currentTenantScopeOrNull() : userScope;
    }

    private TenantScope resolveTenantScopeFromUser(Long userId, String username) {
        UserEntity user = null;
        if (userId != null) {
            user = userMapper.selectById(userId);
        }
        String normalizedUsername = normalizeNullable(username);
        if (user == null && StringUtils.hasText(normalizedUsername)) {
            user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, normalizedUsername)
                    .eq(UserEntity::getDeletedFlag, 0)
                    .last("limit 1"));
        }
        if (user == null) {
            return null;
        }
        return tenantScope(user.getCompanyId(), user.getAccountBookId());
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

    private String normalizeRequired(String value) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("日志关键字段不能为空");
        }
        return normalized;
    }

    private record TenantScope(Long companyId, Long accountBookId) {
    }
}

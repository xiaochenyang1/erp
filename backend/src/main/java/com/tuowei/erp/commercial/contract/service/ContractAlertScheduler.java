package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "erp.contract-alert.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContractAlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(ContractAlertScheduler.class);

    private final ContractMapper contractMapper;
    private final ContractAlertService alertService;
    private final Clock clock;
    private final int expirationWarningDays;
    private final BigDecimal lowExecutionRate;
    private final long systemUserId;

    public ContractAlertScheduler(ContractMapper contractMapper, ContractAlertService alertService, Clock clock,
                                  @Value("${erp.contract-alert.scheduler.expiration-warning-days:30}") int expirationWarningDays,
                                  @Value("${erp.contract-alert.scheduler.low-execution-rate:0.5}") BigDecimal lowExecutionRate,
                                  @Value("${erp.contract-alert.scheduler.system-user-id:0}") long systemUserId) {
        this.contractMapper = contractMapper;
        this.alertService = alertService;
        this.clock = clock;
        this.expirationWarningDays = Math.max(0, expirationWarningDays);
        this.lowExecutionRate = lowExecutionRate.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        this.systemUserId = systemUserId;
    }

    @Scheduled(fixedDelayString = "${erp.contract-alert.scheduler.fixed-delay-ms:3600000}", initialDelayString = "${erp.contract-alert.scheduler.initial-delay-ms:120000}")
    public void runAutomationCycle() {
        LocalDate today = LocalDate.now(clock);
        for (ContractEntity scope : contractMapper.selectActiveTenantScopesForScheduler()) {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(systemSecurityContext(scope.getCompanyId(), scope.getAccountBookId()));
            try {
                alertService.scan(new AuditMetadata(systemUserId, scope.getCompanyId(), scope.getAccountBookId(), java.time.LocalDateTime.now(clock)),
                        today, expirationWarningDays, lowExecutionRate);
            } catch (RuntimeException ex) {
                log.warn("Contract alert automation failed for companyId={}, accountBookId={}",
                        scope.getCompanyId(), scope.getAccountBookId(), ex);
            } finally {
                restoreSecurityContext(previousContext);
            }
        }
    }

    private SecurityContext systemSecurityContext(Long companyId, Long accountBookId) {
        ErpPrincipal principal = new ErpPrincipal(systemUserId, companyId, accountBookId, null, null,
                "contract-alert-scheduler", "Contract Alert Scheduler", "N/A", Set.of(), DataScopeSnapshot.all());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, "N/A", principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    private void restoreSecurityContext(SecurityContext previousContext) {
        if (previousContext == null || previousContext.getAuthentication() == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(previousContext);
        }
    }
}

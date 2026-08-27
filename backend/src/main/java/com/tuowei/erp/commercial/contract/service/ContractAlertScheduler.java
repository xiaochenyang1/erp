package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.common.security.AuditMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "erp.contract-alert.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContractAlertScheduler {
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
        Set<String> scopes = new LinkedHashSet<>();
        contractMapper.selectList(new LambdaQueryWrapper<ContractEntity>()
                .eq(ContractEntity::getStatus, "ACTIVE")
                .eq(ContractEntity::getDeletedFlag, 0))
                .forEach(contract -> scopes.add(contract.getCompanyId() + ":" + contract.getAccountBookId()));
        for (String scope : scopes) {
            String[] parts = scope.split(":", 2);
            alertService.scan(new AuditMetadata(systemUserId, Long.valueOf(parts[0]), Long.valueOf(parts[1]), java.time.LocalDateTime.now(clock)),
                    today, expirationWarningDays, lowExecutionRate);
        }
    }
}

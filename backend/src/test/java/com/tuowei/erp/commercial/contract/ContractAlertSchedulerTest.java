package com.tuowei.erp.commercial.contract;

import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.service.ContractAlertScheduler;
import com.tuowei.erp.commercial.contract.service.ContractAlertService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractAlertSchedulerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void automationCycleRunsEachActiveScopeWithMatchingSystemPrincipal() {
        ContractMapper contractMapper = mock(ContractMapper.class);
        ContractAlertService alertService = mock(ContractAlertService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ContractEntity first = scope(1L, 11L);
        ContractEntity second = scope(2L, 22L);
        when(contractMapper.selectActiveTenantScopesForScheduler()).thenReturn(List.of(first, second));
        when(alertService.scan(any(AuditMetadata.class), eq(LocalDate.of(2026, 8, 28)), eq(30), eq(new BigDecimal("0.5"))))
                .thenAnswer(invocation -> {
                    AuditMetadata audit = invocation.getArgument(0);
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    assertThat(authentication).isNotNull();
                    assertThat(authentication.getPrincipal()).isInstanceOf(ErpPrincipal.class);
                    ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
                    assertThat(principal.userId()).isEqualTo(99L);
                    assertThat(principal.companyId()).isEqualTo(audit.companyId());
                    assertThat(principal.accountBookId()).isEqualTo(audit.accountBookId());
                    assertThat(principal.dataScopeSnapshot().hasAllScope()).isTrue();
                    return 0;
                });

        ContractAlertScheduler scheduler = new ContractAlertScheduler(
                contractMapper, alertService, clock, 30, new BigDecimal("0.5"), 99L);

        scheduler.runAutomationCycle();

        verify(alertService, times(2)).scan(any(AuditMetadata.class), eq(LocalDate.of(2026, 8, 28)), eq(30), eq(new BigDecimal("0.5")));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private ContractEntity scope(Long companyId, Long accountBookId) {
        ContractEntity entity = new ContractEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }
}

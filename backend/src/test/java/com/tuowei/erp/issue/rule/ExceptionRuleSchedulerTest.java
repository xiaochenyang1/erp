package com.tuowei.erp.issue.rule;

import com.tuowei.erp.issue.rule.service.ExceptionRuleScheduler;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExceptionRuleSchedulerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void automationCycleScansDueRulesAndEscalatesOverdueTickets() {
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(ruleService.scanDueRules()).thenReturn(List.of());

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(ruleService, ticketService, clock);

        scheduler.runAutomationCycle();

        verify(ruleService).scanDueRules();
        verify(ticketService).escalateOverdueTickets(LocalDateTime.of(2026, 6, 30, 10, 0));
    }

    @Test
    void automationCycleRunsWithSystemTenantPrincipalAndClearsItAfterwards() {
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(ruleService.scanDueRules()).thenAnswer(invocation -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isInstanceOf(ErpPrincipal.class);
            ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
            assertThat(principal.userId()).isZero();
            assertThat(principal.companyId()).isEqualTo(1L);
            assertThat(principal.accountBookId()).isEqualTo(1L);
            assertThat(principal.username()).isEqualTo("system-scheduler");
            return List.of();
        });

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(ruleService, ticketService, clock);

        scheduler.runAutomationCycle();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

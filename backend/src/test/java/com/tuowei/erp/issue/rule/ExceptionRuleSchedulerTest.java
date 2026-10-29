package com.tuowei.erp.issue.rule;

import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.service.ExceptionRuleScheduler;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.scheduler.SchedulerLeaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void automationCycleRunsEachDiscoveredTenantScopeWithMatchingPrincipal() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(ruleMapper.selectTenantScopesForScheduler()).thenReturn(List.of(scope(11L, 101L), scope(22L, 202L)));
        when(ruleService.scanDueRulesForScope(any(), any(), eq(LocalDateTime.of(2026, 6, 30, 10, 0))))
                .thenAnswer(invocation -> {
                    Long companyId = invocation.getArgument(0);
                    Long accountBookId = invocation.getArgument(1);
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    assertThat(authentication).isNotNull();
                    ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
                    assertThat(principal.companyId()).isEqualTo(companyId);
                    assertThat(principal.accountBookId()).isEqualTo(accountBookId);
                    assertThat(principal.dataScopeSnapshot().hasAllScope()).isTrue();
                    return List.of();
                });

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L);

        scheduler.runAutomationCycle();

        verify(ruleService).scanDueRulesForScope(11L, 101L, LocalDateTime.of(2026, 6, 30, 10, 0));
        verify(ruleService).scanDueRulesForScope(22L, 202L, LocalDateTime.of(2026, 6, 30, 10, 0));
        verify(ticketService).escalateOverdueTickets(LocalDateTime.of(2026, 6, 30, 10, 0), 11L, 101L);
        verify(ticketService).escalateOverdueTickets(LocalDateTime.of(2026, 6, 30, 10, 0), 22L, 202L);
        verify(ticketService, times(2)).escalateOverdueTickets(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void leasedAutomationAcquiresBeforeScanningAndReleasesWithSameOwnerToken() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        SchedulerLeaseService leaseService = mock(SchedulerLeaseService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        when(leaseService.tryAcquire(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(120)))).thenReturn(true);
        when(ruleMapper.selectTenantScopesForScheduler()).thenReturn(List.of(scope(11L, 101L)));
        when(ruleService.scanDueRulesForScope(11L, 101L, now)).thenReturn(List.of());

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L,
                leaseService, true, 120L);

        scheduler.runAutomationCycle();

        var ownerToken = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(leaseService).tryAcquire(eq(ExceptionRuleScheduler.LEASE_KEY), ownerToken.capture(), eq(now),
                eq(Duration.ofSeconds(120)));
        assertThat(ownerToken.getValue()).isNotBlank();
        verify(leaseService).release(ExceptionRuleScheduler.LEASE_KEY, ownerToken.getValue());
        verify(ruleService).scanDueRulesForScope(11L, 101L, now);
    }

    @Test
    void leasedAutomationSkipsBusinessWorkWhenAnotherInstanceOwnsLease() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        SchedulerLeaseService leaseService = mock(SchedulerLeaseService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(leaseService.tryAcquire(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), any(LocalDateTime.class),
                eq(Duration.ofSeconds(600)))).thenReturn(false);

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L,
                leaseService, true, 600L);

        scheduler.runAutomationCycle();

        verify(ruleMapper, never()).selectTenantScopesForScheduler();
        verify(ruleService, never()).scanDueRulesForScope(any(), any(), any());
        verify(ticketService, never()).escalateOverdueTickets(any(), any(), any());
        verify(leaseService, never()).release(any(), any());
    }

    @Test
    void leasedAutomationReleasesLeaseWhenScopeDiscoveryFails() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        SchedulerLeaseService leaseService = mock(SchedulerLeaseService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(leaseService.tryAcquire(any(), any(), any(), any())).thenReturn(true);
        when(ruleMapper.selectTenantScopesForScheduler()).thenThrow(new IllegalStateException("db unavailable"));

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L,
                leaseService, true, 600L);

        scheduler.runAutomationCycle();

        verify(leaseService).release(eq(ExceptionRuleScheduler.LEASE_KEY),
                org.mockito.ArgumentMatchers.argThat(token -> token != null && !token.isBlank()));
    }

    @Test
    void leasedAutomationRenewsLeaseDuringLongCycleAndContinuesScopes() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        SchedulerLeaseService leaseService = mock(SchedulerLeaseService.class);
        ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        when(leaseService.tryAcquire(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(6)))).thenReturn(true);
        when(leaseService.renew(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(6)))).thenReturn(true);
        AtomicReference<Runnable> renewalTask = new AtomicReference<>();
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            renewalTask.set(invocation.getArgument(0));
            return heartbeatFuture;
        });
        when(ruleMapper.selectTenantScopesForScheduler()).thenReturn(List.of(scope(11L, 101L), scope(22L, 202L)));
        when(ruleService.scanDueRulesForScope(11L, 101L, now)).thenAnswer(invocation -> {
            renewalTask.get().run();
            return List.of();
        });
        when(ruleService.scanDueRulesForScope(22L, 202L, now)).thenReturn(List.of());

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L,
                leaseService, true, 6L, heartbeatExecutor);

        scheduler.runAutomationCycle();

        verify(heartbeatExecutor).scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L),
                eq(TimeUnit.MILLISECONDS));
        verify(leaseService).renew(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(6)));
        verify(ruleService).scanDueRulesForScope(11L, 101L, now);
        verify(ruleService).scanDueRulesForScope(22L, 202L, now);
        verify(heartbeatFuture).cancel(false);
    }

    @Test
    void leasedAutomationStopsCurrentAndRemainingScopesWhenHeartbeatLosesLease() {
        ExceptionRuleMapper ruleMapper = mock(ExceptionRuleMapper.class);
        ExceptionRuleService ruleService = mock(ExceptionRuleService.class);
        ExceptionTicketService ticketService = mock(ExceptionTicketService.class);
        SchedulerLeaseService leaseService = mock(SchedulerLeaseService.class);
        ScheduledExecutorService heartbeatExecutor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        when(leaseService.tryAcquire(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(6)))).thenReturn(true);
        when(leaseService.renew(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class), eq(now),
                eq(Duration.ofSeconds(6)))).thenReturn(false);
        AtomicReference<Runnable> renewalTask = new AtomicReference<>();
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), eq(2000L), eq(2000L),
                eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            renewalTask.set(invocation.getArgument(0));
            return heartbeatFuture;
        });
        when(ruleMapper.selectTenantScopesForScheduler()).thenReturn(List.of(scope(11L, 101L), scope(22L, 202L)));
        when(ruleService.scanDueRulesForScope(11L, 101L, now)).thenAnswer(invocation -> {
            renewalTask.get().run();
            return List.of();
        });

        ExceptionRuleScheduler scheduler = new ExceptionRuleScheduler(
                ruleMapper, ruleService, ticketService, clock, 99L, 1L, 1L,
                leaseService, true, 6L, heartbeatExecutor);

        scheduler.runAutomationCycle();

        verify(ruleService).scanDueRulesForScope(11L, 101L, now);
        verify(ruleService, never()).scanDueRulesForScope(22L, 202L, now);
        verify(ticketService, never()).escalateOverdueTickets(any(), any(), any());
        verify(heartbeatFuture).cancel(false);
        verify(leaseService).release(eq(ExceptionRuleScheduler.LEASE_KEY), any(String.class));
    }

    private ExceptionRuleEntity scope(Long companyId, Long accountBookId) {
        ExceptionRuleEntity entity = new ExceptionRuleEntity();
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        return entity;
    }
}

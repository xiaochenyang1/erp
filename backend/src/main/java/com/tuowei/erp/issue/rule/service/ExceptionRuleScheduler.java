package com.tuowei.erp.issue.rule.service;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.scheduler.SchedulerLeaseService;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "erp.exception-rule.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionRuleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionRuleScheduler.class);
    public static final String LEASE_KEY = "EXCEPTION_RULE_AUTOMATION";
    static final long DEFAULT_LEASE_TTL_SECONDS = 600L;

    private final ExceptionRuleMapper ruleMapper;
    private final ExceptionRuleService ruleService;
    private final ExceptionTicketService ticketService;
    private final SchedulerLeaseService leaseService;
    private final Clock clock;
    private final long systemUserId;
    private final long systemCompanyId;
    private final long systemAccountBookId;
    private final boolean leaseEnabled;
    private final Duration leaseTtl;
    private final Supplier<ScheduledExecutorService> leaseHeartbeatExecutorFactory;
    private final boolean ownsLeaseHeartbeatExecutor;

    @Autowired
    public ExceptionRuleScheduler(
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            @Value("${erp.exception-rule.scheduler.system-user-id:0}") long systemUserId,
            @Value("${erp.exception-rule.scheduler.system-company-id:1}") long systemCompanyId,
            @Value("${erp.exception-rule.scheduler.system-account-book-id:1}") long systemAccountBookId,
            SchedulerLeaseService leaseService,
            @Value("${erp.exception-rule.scheduler.lease-enabled:true}") boolean leaseEnabled,
            @Value("${erp.exception-rule.scheduler.lease-ttl-seconds:600}") long leaseTtlSeconds
    ) {
        this(ruleMapper, ruleService, ticketService, clock, systemUserId, systemCompanyId, systemAccountBookId,
                leaseService, leaseEnabled, leaseTtlSeconds,
                ExceptionRuleScheduler::createLeaseHeartbeatExecutor, true);
    }

    /** Keeps direct construction in existing tests and integrations compatible. */
    public ExceptionRuleScheduler(
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            long systemUserId,
            long systemCompanyId,
            long systemAccountBookId
    ) {
        this(ruleMapper, ruleService, ticketService, clock, systemUserId, systemCompanyId, systemAccountBookId,
                SchedulerLeaseService.NOOP, false, DEFAULT_LEASE_TTL_SECONDS,
                ExceptionRuleScheduler::createLeaseHeartbeatExecutor, true);
    }

    /** Keeps legacy direct construction without tenant discovery compatible. */
    public ExceptionRuleScheduler(
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            long systemUserId,
            long systemCompanyId,
            long systemAccountBookId
    ) {
        this(null, ruleService, ticketService, clock, systemUserId, systemCompanyId, systemAccountBookId,
                SchedulerLeaseService.NOOP, false, DEFAULT_LEASE_TTL_SECONDS,
                ExceptionRuleScheduler::createLeaseHeartbeatExecutor, true);
    }

    public ExceptionRuleScheduler(ExceptionRuleService ruleService, ExceptionTicketService ticketService, Clock clock) {
        this(ruleService, ticketService, clock, 0L, 1L, 1L);
    }

    /** Supplies the executor used by one cycle's lease heartbeat; the caller owns it. */
    public ExceptionRuleScheduler(
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            long systemUserId,
            long systemCompanyId,
            long systemAccountBookId,
            SchedulerLeaseService leaseService,
            boolean leaseEnabled,
            long leaseTtlSeconds,
            ScheduledExecutorService leaseHeartbeatExecutor
    ) {
        this(ruleMapper, ruleService, ticketService, clock, systemUserId, systemCompanyId, systemAccountBookId,
                leaseService, leaseEnabled, leaseTtlSeconds,
                () -> Objects.requireNonNull(leaseHeartbeatExecutor, "leaseHeartbeatExecutor"), false);
    }

    private ExceptionRuleScheduler(
            ExceptionRuleMapper ruleMapper,
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            long systemUserId,
            long systemCompanyId,
            long systemAccountBookId,
            SchedulerLeaseService leaseService,
            boolean leaseEnabled,
            long leaseTtlSeconds,
            Supplier<ScheduledExecutorService> leaseHeartbeatExecutorFactory,
            boolean ownsLeaseHeartbeatExecutor
    ) {
        this.ruleMapper = ruleMapper;
        this.ruleService = ruleService;
        this.ticketService = ticketService;
        this.leaseService = leaseService == null ? SchedulerLeaseService.NOOP : leaseService;
        this.clock = clock;
        this.systemUserId = systemUserId;
        this.systemCompanyId = systemCompanyId;
        this.systemAccountBookId = systemAccountBookId;
        this.leaseEnabled = leaseEnabled;
        this.leaseTtl = validateLeaseTtl(leaseTtlSeconds);
        this.leaseHeartbeatExecutorFactory = Objects.requireNonNull(
                leaseHeartbeatExecutorFactory, "leaseHeartbeatExecutorFactory");
        this.ownsLeaseHeartbeatExecutor = ownsLeaseHeartbeatExecutor;
    }

    @Scheduled(
            fixedDelayString = "${erp.exception-rule.scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${erp.exception-rule.scheduler.initial-delay-ms:60000}"
    )
    public void runAutomationCycle() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (ruleMapper == null) {
            runLegacyAutomationCycle(now);
            return;
        }
        if (!leaseEnabled) {
            runLeasedAutomationCycle(now);
            return;
        }

        String ownerToken = UUID.randomUUID().toString();
        try {
            if (!leaseService.tryAcquire(LEASE_KEY, ownerToken, now, leaseTtl)) {
                log.debug("Exception rule automation cycle skipped because another instance owns the lease");
                return;
            }
        } catch (RuntimeException ex) {
            // A coordination failure must not cause two instances to run the
            // same cycle.  The next scheduled invocation can retry acquisition.
            log.warn("Exception rule automation lease acquisition failed", ex);
            return;
        }

        LeaseHeartbeat heartbeat = null;
        try {
            heartbeat = startLeaseHeartbeat(ownerToken);
            runLeasedAutomationCycle(now, heartbeat);
        } catch (RuntimeException ex) {
            // Keep the scheduled executor alive if an unexpected failure
            // escapes one of the per-scope guards.
            log.warn("Exception rule automation cycle failed", ex);
        } finally {
            if (heartbeat != null) {
                heartbeat.close();
            }
            try {
                leaseService.release(LEASE_KEY, ownerToken);
            } catch (RuntimeException ex) {
                // Losing a lease release is recoverable: the row will expire
                // and the next cycle can acquire it again.
                log.warn("Exception rule automation lease release failed", ex);
            }
        }
    }

    private void runLeasedAutomationCycle(LocalDateTime now) {
        runLeasedAutomationCycle(now, null);
    }

    private void runLeasedAutomationCycle(LocalDateTime now, LeaseHeartbeat heartbeat) {
        int scannedRules = 0;
        int escalatedTickets = 0;
        List<ExceptionRuleEntity> scopes;
        try {
            scopes = ruleMapper.selectTenantScopesForScheduler();
        } catch (RuntimeException ex) {
            log.warn("Exception rule automation scope discovery failed", ex);
            return;
        }
        if (scopes == null) {
            scopes = List.of();
        }
        for (ExceptionRuleEntity scope : scopes) {
            if (leaseLost(heartbeat)) {
                log.warn("Exception rule automation stopped after losing its scheduler lease");
                return;
            }
            if (scope == null || scope.getCompanyId() == null || scope.getAccountBookId() == null) {
                log.warn("Skipping exception automation scope with missing company/account book: {}", scope);
                continue;
            }
            Long companyId = scope.getCompanyId();
            Long accountBookId = scope.getAccountBookId();
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(systemSecurityContext(companyId, accountBookId));
            try {
                if (leaseLost(heartbeat)) {
                    log.warn("Exception rule automation stopped before companyId={}, accountBookId={} because its scheduler lease was lost",
                            companyId, accountBookId);
                    return;
                }
                int scopeScannedRules = ruleService
                        .scanDueRulesForScope(companyId, accountBookId, now)
                        .size();
                if (leaseLost(heartbeat)) {
                    log.warn("Exception rule automation stopped before escalating companyId={}, accountBookId={} because its scheduler lease was lost",
                            companyId, accountBookId);
                    return;
                }
                int scopeEscalatedTickets = ticketService
                        .escalateOverdueTickets(now, companyId, accountBookId);
                if (leaseLost(heartbeat)) {
                    log.warn("Exception rule automation stopped after companyId={}, accountBookId={} because its scheduler lease was lost",
                            companyId, accountBookId);
                    return;
                }
                scannedRules += scopeScannedRules;
                escalatedTickets += scopeEscalatedTickets;
                log.debug("Exception rule automation scope completed: companyId={}, accountBookId={}, scannedRules={}, escalatedTickets={}",
                        companyId, accountBookId, scopeScannedRules, scopeEscalatedTickets);
            } catch (RuntimeException ex) {
                log.warn("Exception rule automation failed for companyId={}, accountBookId={}",
                        companyId, accountBookId, ex);
            } finally {
                restoreSecurityContext(previousContext);
            }
        }
        log.debug("Exception rule automation cycle completed: scopes={}, scannedRules={}, escalatedTickets={}",
                scopes.size(), scannedRules, escalatedTickets);
    }

    private LeaseHeartbeat startLeaseHeartbeat(String ownerToken) {
        ScheduledExecutorService executor = Objects.requireNonNull(
                leaseHeartbeatExecutorFactory.get(), "lease heartbeat executor");
        LeaseHeartbeat heartbeat = new LeaseHeartbeat(executor, ownerToken, ownsLeaseHeartbeatExecutor);
        try {
            heartbeat.start(heartbeatIntervalMillis(leaseTtl));
            return heartbeat;
        } catch (RuntimeException ex) {
            heartbeat.close();
            throw ex;
        }
    }

    private boolean leaseLost(LeaseHeartbeat heartbeat) {
        return heartbeat != null && !heartbeat.isHeld();
    }

    private long heartbeatIntervalMillis(Duration ttl) {
        long ttlMillis;
        try {
            ttlMillis = ttl.toMillis();
        } catch (ArithmeticException ex) {
            ttlMillis = Long.MAX_VALUE;
        }
        return Math.max(1L, ttlMillis / 3L);
    }

    private static ScheduledExecutorService createLeaseHeartbeatExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "erp-exception-rule-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    private final class LeaseHeartbeat implements AutoCloseable {
        private final ScheduledExecutorService executor;
        private final String ownerToken;
        private final boolean ownsExecutor;
        private final AtomicBoolean held = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private ScheduledFuture<?> future;

        private LeaseHeartbeat(ScheduledExecutorService executor, String ownerToken, boolean ownsExecutor) {
            this.executor = executor;
            this.ownerToken = ownerToken;
            this.ownsExecutor = ownsExecutor;
        }

        private void start(long intervalMillis) {
            future = executor.scheduleAtFixedRate(this::renew, intervalMillis, intervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        private void renew() {
            if (closed.get() || !held.get()) {
                return;
            }
            try {
                if (!leaseService.renew(LEASE_KEY, ownerToken, LocalDateTime.now(clock), leaseTtl)) {
                    markLost(null);
                }
            } catch (RuntimeException ex) {
                markLost(ex);
            }
        }

        private void markLost(RuntimeException cause) {
            if (!held.compareAndSet(true, false)) {
                return;
            }
            if (cause == null) {
                log.warn("Exception rule automation scheduler lease renewal was rejected; stopping this cycle");
            } else {
                log.warn("Exception rule automation scheduler lease renewal failed; stopping this cycle", cause);
            }
        }

        private boolean isHeld() {
            return held.get();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (future != null) {
                future.cancel(false);
            }
            if (ownsExecutor) {
                executor.shutdown();
            }
        }
    }

    private void runLegacyAutomationCycle(LocalDateTime now) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(systemSecurityContext(systemCompanyId, systemAccountBookId));
        try {
            int scannedRules = ruleService.scanDueRules().size();
            int escalatedTickets = ticketService.escalateOverdueTickets(now);
            log.debug("Exception rule automation cycle completed: scannedRules={}, escalatedTickets={}",
                    scannedRules, escalatedTickets);
        } catch (RuntimeException ex) {
            log.warn("Exception rule automation cycle failed", ex);
        } finally {
            restoreSecurityContext(previousContext);
        }
    }

    private SecurityContext systemSecurityContext(Long companyId, Long accountBookId) {
        ErpPrincipal principal = new ErpPrincipal(
                systemUserId,
                companyId,
                accountBookId,
                null,
                null,
                "system-scheduler",
                "System Scheduler",
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                "N/A",
                principal.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

    private void restoreSecurityContext(SecurityContext previousContext) {
        if (previousContext == null || previousContext.getAuthentication() == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        SecurityContextHolder.setContext(previousContext);
    }

    private Duration validateLeaseTtl(long leaseTtlSeconds) {
        if (leaseTtlSeconds <= 0L) {
            throw new IllegalArgumentException("异常规则调度租约时长必须大于 0 秒");
        }
        return Duration.ofSeconds(leaseTtlSeconds);
    }
}

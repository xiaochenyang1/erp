package com.tuowei.erp.issue.rule.service;

import com.tuowei.erp.common.security.ErpPrincipal;
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
import java.time.LocalDateTime;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "erp.exception-rule.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionRuleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionRuleScheduler.class);

    private final ExceptionRuleService ruleService;
    private final ExceptionTicketService ticketService;
    private final Clock clock;
    private final long systemUserId;
    private final long systemCompanyId;
    private final long systemAccountBookId;

    @Autowired
    public ExceptionRuleScheduler(
            ExceptionRuleService ruleService,
            ExceptionTicketService ticketService,
            Clock clock,
            @Value("${erp.exception-rule.scheduler.system-user-id:0}") long systemUserId,
            @Value("${erp.exception-rule.scheduler.system-company-id:1}") long systemCompanyId,
            @Value("${erp.exception-rule.scheduler.system-account-book-id:1}") long systemAccountBookId
    ) {
        this.ruleService = ruleService;
        this.ticketService = ticketService;
        this.clock = clock;
        this.systemUserId = systemUserId;
        this.systemCompanyId = systemCompanyId;
        this.systemAccountBookId = systemAccountBookId;
    }

    public ExceptionRuleScheduler(ExceptionRuleService ruleService, ExceptionTicketService ticketService, Clock clock) {
        this(ruleService, ticketService, clock, 0L, 1L, 1L);
    }

    @Scheduled(
            fixedDelayString = "${erp.exception-rule.scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${erp.exception-rule.scheduler.initial-delay-ms:60000}"
    )
    public void runAutomationCycle() {
        LocalDateTime now = LocalDateTime.now(clock);
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(systemSecurityContext());
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

    private SecurityContext systemSecurityContext() {
        ErpPrincipal principal = new ErpPrincipal(
                systemUserId,
                systemCompanyId,
                systemAccountBookId,
                "system-scheduler",
                "System Scheduler",
                "N/A",
                Set.of()
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
}

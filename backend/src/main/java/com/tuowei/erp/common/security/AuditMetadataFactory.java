package com.tuowei.erp.common.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AuditMetadataFactory {

    private final CurrentUserContext currentUserContext;
    private final Clock clock;

    public AuditMetadataFactory(CurrentUserContext currentUserContext, Clock clock) {
        this.currentUserContext = currentUserContext;
        this.clock = clock;
    }

    public AuditMetadata current() {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        return new AuditMetadata(
                currentUser.userId(),
                currentUser.companyId(),
                currentUser.accountBookId(),
                LocalDateTime.now(clock)
        );
    }
}

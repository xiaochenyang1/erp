package com.tuowei.erp.issue.web;

import java.time.LocalDateTime;

public record ExceptionTicketEventResponse(
        Long id,
        Long ticketId,
        String action,
        String fromStatus,
        String toStatus,
        String comment,
        Long operatorUserId,
        LocalDateTime createdTime
) {
}

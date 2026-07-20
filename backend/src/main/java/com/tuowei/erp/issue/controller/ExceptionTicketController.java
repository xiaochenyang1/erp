package com.tuowei.erp.issue.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exception-tickets")
public class ExceptionTicketController {

    private final ExceptionTicketService exceptionTicketService;

    public ExceptionTicketController(ExceptionTicketService exceptionTicketService) {
        this.exceptionTicketService = exceptionTicketService;
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ExceptionTicketResponse>> list(ExceptionTicketPageQuery query) {
        return ApiResponse.success(exceptionTicketService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_MANAGE)
    @PostMapping
    public ApiResponse<ExceptionTicketResponse> create(@Valid @RequestBody ExceptionTicketCreateRequest request) {
        return ApiResponse.success(exceptionTicketService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_MANAGE)
    @PostMapping("/{id}/assign")
    public ApiResponse<ExceptionTicketResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionTicketAssignRequest request
    ) {
        return ApiResponse.success(exceptionTicketService.assign(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_MANAGE)
    @PostMapping("/{id}/start")
    public ApiResponse<ExceptionTicketResponse> start(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionTicketActionRequest request
    ) {
        return ApiResponse.success(exceptionTicketService.start(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_MANAGE)
    @PostMapping("/{id}/resolve")
    public ApiResponse<ExceptionTicketResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionTicketActionRequest request
    ) {
        return ApiResponse.success(exceptionTicketService.resolve(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_EXCEPTION_TICKET_MANAGE)
    @PostMapping("/{id}/close")
    public ApiResponse<ExceptionTicketResponse> close(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionTicketActionRequest request
    ) {
        return ApiResponse.success(exceptionTicketService.close(id, request));
    }
}

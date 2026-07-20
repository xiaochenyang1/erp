package com.tuowei.erp.system.timeline.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import com.tuowei.erp.system.timeline.web.BusinessTimelineCommentRequest;
import com.tuowei.erp.system.timeline.web.BusinessTimelineQuery;
import com.tuowei.erp.system.timeline.web.BusinessTimelineResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-timeline")
public class BusinessTimelineController {

    private final BusinessTimelineService timelineService;

    public BusinessTimelineController(BusinessTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<BusinessTimelineResponse>> list(BusinessTimelineQuery query) {
        return ApiResponse.success(timelineService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_MANAGE)
    @PostMapping("/comments")
    public ApiResponse<BusinessTimelineResponse> createComment(@Valid @RequestBody BusinessTimelineCommentRequest request) {
        return ApiResponse.success(timelineService.createComment(request));
    }
}

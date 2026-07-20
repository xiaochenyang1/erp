package com.tuowei.erp.system.post.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.post.service.PostService;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.post.web.PostResponse;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_CREATE)
    @PostMapping
    public ApiResponse<PostResponse> create(@Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.success(postService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<PostResponse>> list(PostPageQuery query) {
        return ApiResponse.success(postService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<PostResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(postService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<PostResponse> update(@PathVariable Long id, @Valid @RequestBody PostUpdateRequest request) {
        return ApiResponse.success(postService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<PostResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(postService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_POST_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<PostResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(postService.disable(id));
    }
}

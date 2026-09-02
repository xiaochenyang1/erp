package com.tuowei.erp.system.post.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.post.web.PostResponse;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for post queries and commands. */
@Service
public class PostService {

    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;

    public PostService(PostQueryService postQueryService, PostCommandService postCommandService) {
        this.postQueryService = postQueryService;
        this.postCommandService = postCommandService;
    }

    @Transactional
    public PostResponse create(PostCreateRequest request) {
        return postCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(PostPageQuery query) {
        return postQueryService.list(query == null ? new PostPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id) {
        return postQueryService.getById(id);
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        return postCommandService.update(id, request);
    }

    @Transactional
    public PostResponse enable(Long id) {
        return postCommandService.enable(id);
    }

    @Transactional
    public PostResponse disable(Long id) {
        return postCommandService.disable(id);
    }
}

package com.tuowei.erp.system.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.post.web.PostResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/** Read-side post queries and tenant-scoped entity lookup. */
@Service
public class PostQueryService {

    private final PostMapper postMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public PostQueryService(PostMapper postMapper, AuditMetadataFactory auditMetadataFactory) {
        this.postMapper = postMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(PostPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        PostPageQuery safeQuery = query == null ? new PostPageQuery() : query;
        Page<PostEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<PostEntity> result = postMapper.selectPage(page, buildListQuery(
                audit.companyId(), audit.accountBookId(), normalizeNullableText(safeQuery.getKeyword()),
                normalizeStatus(safeQuery.getStatus()), safeQuery.getDeptId()
        ));
        return new PageResponse<>(
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id) {
        return toResponse(requirePost(id));
    }

    PostEntity requirePost(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PostEntity entity = postMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("岗位不存在");
        }
        return entity;
    }

    PostResponse toResponse(PostEntity entity) {
        return new PostResponse(
                entity.getId(), entity.getDeptId(), entity.getPostCode(), entity.getPostName(),
                entity.getStatus(), entity.getRemark()
        );
    }

    private LambdaQueryWrapper<PostEntity> buildListQuery(
            Long companyId, Long accountBookId, String keyword, String status, Long deptId
    ) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getCompanyId, companyId)
                .eq(PostEntity::getAccountBookId, accountBookId)
                .eq(PostEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(PostEntity::getPostCode, keyword)
                    .or().like(PostEntity::getPostName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PostEntity::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(PostEntity::getDeptId, deptId);
        }
        return wrapper.orderByAsc(PostEntity::getPostCode);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }
}

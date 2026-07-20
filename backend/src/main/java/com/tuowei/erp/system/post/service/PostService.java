package com.tuowei.erp.system.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostPageQuery;
import com.tuowei.erp.system.post.web.PostResponse;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final DeptMapper deptMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public PostService(PostMapper postMapper, DeptMapper deptMapper, AuditMetadataFactory auditMetadataFactory) {
        this.postMapper = postMapper;
        this.deptMapper = deptMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public PostResponse create(PostCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        requireDept(request.deptId(), audit.companyId(), audit.accountBookId());
        LocalDateTime now = audit.now();

        PostEntity entity = new PostEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setDeptId(request.deptId());
        entity.setPostCode(request.postCode());
        entity.setPostName(request.postName());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        postMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(PostPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        PostPageQuery safeQuery = query == null ? new PostPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<PostEntity> page = new Page<>(pageNo, pageSize);
        Page<PostEntity> result = postMapper.selectPage(page, buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, safeQuery.getDeptId()));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id) {
        return toResponse(requirePost(id));
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        PostEntity entity = requirePost(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setPostName(request.postName());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(postMapper.updateById(entity), "岗位已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    @Transactional
    public PostResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public PostResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private PostResponse toResponse(PostEntity entity) {
        return new PostResponse(
                entity.getId(),
                entity.getDeptId(),
                entity.getPostCode(),
                entity.getPostName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private void requireDept(Long deptId, Long companyId, Long accountBookId) {
        DeptEntity entity = deptMapper.selectById(deptId);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !companyId.equals(entity.getCompanyId())
                || !accountBookId.equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("部门不存在");
        }
    }

    private PostResponse updateStatus(Long id, String status) {
        PostEntity entity = requirePost(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(postMapper.updateById(entity), "岗位已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private PostEntity requirePost(Long id) {
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

    private LambdaQueryWrapper<PostEntity> buildListQuery(Long companyId, Long accountBookId, String keyword, String status, Long deptId) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getCompanyId, companyId)
                .eq(PostEntity::getAccountBookId, accountBookId)
                .eq(PostEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(PostEntity::getPostCode, keyword)
                    .or()
                    .like(PostEntity::getPostName, keyword));
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

}

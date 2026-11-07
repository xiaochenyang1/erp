package com.tuowei.erp.system.post.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.post.web.PostCreateRequest;
import com.tuowei.erp.system.post.web.PostResponse;
import com.tuowei.erp.system.post.web.PostUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Write-side post lifecycle commands. */
@Service
public class PostCommandService {

    private final PostMapper postMapper;
    private final DeptMapper deptMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PostQueryService postQueryService;

    public PostCommandService(
            PostMapper postMapper,
            DeptMapper deptMapper,
            AuditMetadataFactory auditMetadataFactory,
            PostQueryService postQueryService
    ) {
        this.postMapper = postMapper;
        this.deptMapper = deptMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.postQueryService = postQueryService;
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
        return postQueryService.toResponse(entity);
    }

    @Transactional
    public PostResponse update(Long id, PostUpdateRequest request) {
        PostEntity entity = postQueryService.requirePost(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setPostName(request.postName());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                postMapper.updateById(entity), "岗位已被其他操作修改，请刷新后重试"
        );
        return postQueryService.toResponse(entity);
    }

    @Transactional
    public PostResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public PostResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private PostResponse updateStatus(Long id, String status) {
        PostEntity entity = postQueryService.requirePost(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                postMapper.updateById(entity), "岗位已被其他操作修改，请刷新后重试"
        );
        return postQueryService.toResponse(entity);
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
}

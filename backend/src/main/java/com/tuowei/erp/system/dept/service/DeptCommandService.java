package com.tuowei.erp.system.dept.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptResponse;
import com.tuowei.erp.system.dept.web.DeptUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Write-side department lifecycle commands. */
@Service
public class DeptCommandService {

    private final DeptMapper deptMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final DeptQueryService deptQueryService;

    public DeptCommandService(
            DeptMapper deptMapper,
            AuditMetadataFactory auditMetadataFactory,
            DeptQueryService deptQueryService
    ) {
        this.deptMapper = deptMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.deptQueryService = deptQueryService;
    }

    @Transactional
    public DeptResponse create(DeptCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        deptQueryService.requireParentDept(parentId, audit.companyId(), audit.accountBookId());

        DeptEntity entity = new DeptEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setParentId(parentId);
        entity.setDeptCode(request.deptCode());
        entity.setDeptName(request.deptName());
        entity.setLeaderUserId(request.leaderUserId());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        deptMapper.insert(entity);
        return deptQueryService.toResponse(entity);
    }

    @Transactional
    public DeptResponse update(Long id, DeptUpdateRequest request) {
        DeptEntity entity = deptQueryService.requireDept(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setDeptName(request.deptName());
        entity.setLeaderUserId(request.leaderUserId());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                deptMapper.updateById(entity), "部门已被其他操作修改，请刷新后重试"
        );
        return deptQueryService.toResponse(entity);
    }

    @Transactional
    public DeptResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public DeptResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private DeptResponse updateStatus(Long id, String status) {
        DeptEntity entity = deptQueryService.requireDept(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                deptMapper.updateById(entity), "部门已被其他操作修改，请刷新后重试"
        );
        return deptQueryService.toResponse(entity);
    }
}

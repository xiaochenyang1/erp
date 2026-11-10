package com.tuowei.erp.system.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dept.web.DeptResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-side department queries and tenant-scoped entity lookup. */
@Service
public class DeptQueryService {

    private final DeptMapper deptMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public DeptQueryService(DeptMapper deptMapper, AuditMetadataFactory auditMetadataFactory) {
        this.deptMapper = deptMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeptResponse> list(DeptPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        DeptPageQuery safeQuery = query == null ? new DeptPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<DeptEntity> page = new Page<>(pageNo, pageSize);
        Page<DeptEntity> result = deptMapper.selectPage(page, buildListQuery(
                audit.companyId(), audit.accountBookId(), keyword, status, safeQuery.getParentId()
        ));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<DeptResponse> tree() {
        AuditMetadata audit = auditMetadataFactory.current();
        List<DeptEntity> entities = deptMapper.selectList(new LambdaQueryWrapper<DeptEntity>()
                .eq(DeptEntity::getCompanyId, audit.companyId())
                .eq(DeptEntity::getAccountBookId, audit.accountBookId())
                .eq(DeptEntity::getDeletedFlag, 0)
                .orderByAsc(DeptEntity::getParentId)
                .orderByAsc(DeptEntity::getSortNo)
                .orderByAsc(DeptEntity::getId));

        Map<Long, DeptResponse> nodeMap = new LinkedHashMap<>();
        List<DeptResponse> roots = new ArrayList<>();
        for (DeptEntity entity : entities) {
            nodeMap.put(entity.getId(), toResponse(entity));
        }
        for (DeptEntity entity : entities) {
            DeptResponse current = nodeMap.get(entity.getId());
            if (entity.getParentId() == null || entity.getParentId() == 0L) {
                roots.add(current);
                continue;
            }
            DeptResponse parent = nodeMap.get(entity.getParentId());
            if (parent == null) {
                roots.add(current);
            } else {
                parent.children().add(current);
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public DeptResponse getById(Long id) {
        return toResponse(requireDept(id));
    }

    DeptEntity requireDept(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        DeptEntity entity = deptMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("部门不存在");
        }
        return entity;
    }

    DeptEntity requireParentDept(Long parentId, Long companyId, Long accountBookId) {
        if (parentId == null || parentId == 0L) {
            return null;
        }
        DeptEntity parent = deptMapper.selectById(parentId);
        if (parent == null
                || parent.getDeletedFlag() == null
                || parent.getDeletedFlag() != 0
                || !companyId.equals(parent.getCompanyId())
                || !accountBookId.equals(parent.getAccountBookId())) {
            throw new IllegalArgumentException("上级部门不存在");
        }
        return parent;
    }

    DeptResponse toResponse(DeptEntity entity) {
        return new DeptResponse(
                entity.getId(), entity.getParentId(), entity.getDeptCode(), entity.getDeptName(),
                entity.getLeaderUserId(), entity.getSortNo(), entity.getStatus(), entity.getRemark(),
                new ArrayList<>()
        );
    }

    private LambdaQueryWrapper<DeptEntity> buildListQuery(
            Long companyId, Long accountBookId, String keyword, String status, Long parentId
    ) {
        LambdaQueryWrapper<DeptEntity> wrapper = new LambdaQueryWrapper<DeptEntity>()
                .eq(DeptEntity::getCompanyId, companyId)
                .eq(DeptEntity::getAccountBookId, accountBookId)
                .eq(DeptEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(DeptEntity::getDeptCode, keyword)
                    .or().like(DeptEntity::getDeptName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DeptEntity::getStatus, status);
        }
        if (parentId != null) {
            wrapper.eq(DeptEntity::getParentId, parentId);
        }
        return wrapper.orderByAsc(DeptEntity::getParentId)
                .orderByAsc(DeptEntity::getSortNo)
                .orderByAsc(DeptEntity::getId);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

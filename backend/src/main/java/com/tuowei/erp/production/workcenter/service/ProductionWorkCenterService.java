package com.tuowei.erp.production.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductionWorkCenterService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProductionWorkCenterMapper workCenterMapper;
    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionWorkCenterService(
            ProductionWorkCenterMapper workCenterMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.workCenterMapper = workCenterMapper;
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ProductionWorkCenterResponse create(ProductionWorkCenterCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireUniqueCode(request.workCenterCode(), audit, null);

        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setWorkCenterCode(requireText(request.workCenterCode(), "工作中心编码不能为空"));
        entity.setWorkCenterName(requireText(request.workCenterName(), "工作中心名称不能为空"));
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(normalizeNullable(request.remark()));
        fillCreateAudit(entity, audit, now);

        workCenterMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public ProductionWorkCenterResponse update(Long id, ProductionWorkCenterUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        entity.setWorkCenterName(requireText(request.workCenterName(), "工作中心名称不能为空"));
        entity.setRemark(normalizeNullable(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(workCenterMapper.updateById(entity), "工作中心已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public ProductionWorkCenterResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireWorkCenter(id, audit.companyId(), audit.accountBookId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionWorkCenterResponse> list(ProductionWorkCenterPageQuery query) {
        ProductionWorkCenterPageQuery safeQuery = query == null ? new ProductionWorkCenterPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionWorkCenterEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ProductionWorkCenterEntity> wrapper = new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionWorkCenterEntity::getDeletedFlag, 0);
        String keyword = normalizeNullable(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(queryWrapper -> queryWrapper.like(ProductionWorkCenterEntity::getWorkCenterCode, keyword)
                    .or()
                    .like(ProductionWorkCenterEntity::getWorkCenterName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionWorkCenterEntity::getStatus, status);
        }
        wrapper.orderByAsc(ProductionWorkCenterEntity::getWorkCenterCode);
        Page<ProductionWorkCenterEntity> result = workCenterMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional
    public ProductionWorkCenterResponse enable(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        return updateStatus(entity, STATUS_ACTIVE, audit);
    }

    @Transactional
    public ProductionWorkCenterResponse disable(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        assertNotReferencedByActiveRouting(entity.getId(), audit.companyId(), audit.accountBookId());
        return updateStatus(entity, STATUS_DISABLED, audit);
    }

    private ProductionWorkCenterEntity requireWorkCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = workCenterMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工作中心不存在");
        }
        return entity;
    }

    private ProductionWorkCenterResponse updateStatus(
            ProductionWorkCenterEntity entity,
            String status,
            AuditMetadata audit
    ) {
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(workCenterMapper.updateById(entity), "工作中心已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private void assertNotReferencedByActiveRouting(Long workCenterId, Long companyId, Long accountBookId) {
        List<Long> routingIds = routingOperationMapper.selectList(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                        .eq(ProductionRoutingOperationEntity::getCompanyId, companyId)
                        .eq(ProductionRoutingOperationEntity::getAccountBookId, accountBookId)
                        .eq(ProductionRoutingOperationEntity::getWorkCenterId, workCenterId))
                .stream()
                .map(ProductionRoutingOperationEntity::getRoutingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (routingIds.isEmpty()) {
            return;
        }
        Long activeRoutingCount = routingMapper.selectCount(new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, companyId)
                .eq(ProductionRoutingEntity::getAccountBookId, accountBookId)
                .eq(ProductionRoutingEntity::getDeletedFlag, 0)
                .eq(ProductionRoutingEntity::getStatus, STATUS_ACTIVE)
                .in(ProductionRoutingEntity::getId, routingIds));
        if (activeRoutingCount != null && activeRoutingCount > 0) {
            throw new IllegalArgumentException("工作中心已被启用工艺路线引用，不能停用");
        }
    }

    private void requireUniqueCode(String workCenterCode, AuditMetadata audit, Long excludedId) {
        LambdaQueryWrapper<ProductionWorkCenterEntity> wrapper = new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionWorkCenterEntity::getWorkCenterCode, requireText(workCenterCode, "工作中心编码不能为空"))
                .eq(ProductionWorkCenterEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionWorkCenterEntity::getId, excludedId);
        }
        if (workCenterMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("工作中心编码已存在");
        }
    }

    private void fillCreateAudit(ProductionWorkCenterEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private ProductionWorkCenterResponse toResponse(ProductionWorkCenterEntity entity) {
        return new ProductionWorkCenterResponse(
                entity.getId(),
                entity.getWorkCenterCode(),
                entity.getWorkCenterName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

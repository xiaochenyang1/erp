package com.tuowei.erp.production.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterCreateRequest;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Write-side work-center creation, updates and enable/disable (with active-routing guard), reusing the query service for tenant guards and mapping. */
@Service
public class ProductionWorkCenterCommandService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProductionWorkCenterMapper workCenterMapper;
    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionWorkCenterQueryService queryService;

    public ProductionWorkCenterCommandService(
            ProductionWorkCenterMapper workCenterMapper,
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            AuditMetadataFactory auditMetadataFactory,
            ProductionWorkCenterQueryService queryService
    ) {
        this.workCenterMapper = workCenterMapper;
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
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
        entity.setRemark(queryService.normalizeNullable(request.remark()));
        fillCreateAudit(entity, audit, now);

        workCenterMapper.insert(entity);
        return queryService.toResponse(entity);
    }

    @Transactional
    public ProductionWorkCenterResponse update(Long id, ProductionWorkCenterUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = queryService.requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        entity.setWorkCenterName(requireText(request.workCenterName(), "工作中心名称不能为空"));
        entity.setRemark(queryService.normalizeNullable(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(workCenterMapper.updateById(entity), "工作中心已被其他操作修改，请刷新后重试");
        return queryService.toResponse(entity);
    }

    @Transactional
    public ProductionWorkCenterResponse enable(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = queryService.requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        return updateStatus(entity, STATUS_ACTIVE, audit);
    }

    @Transactional
    public ProductionWorkCenterResponse disable(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionWorkCenterEntity entity = queryService.requireWorkCenter(id, audit.companyId(), audit.accountBookId());
        assertNotReferencedByActiveRouting(entity.getId(), audit.companyId(), audit.accountBookId());
        return updateStatus(entity, STATUS_DISABLED, audit);
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
        return queryService.toResponse(entity);
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

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

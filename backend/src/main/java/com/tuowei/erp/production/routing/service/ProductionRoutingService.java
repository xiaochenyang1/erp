package com.tuowei.erp.production.routing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingUpdateRequest;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductionRoutingService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final ProductionBomMapper bomMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionRoutingService(
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            ProductionBomMapper bomMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.bomMapper = bomMapper;
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ProductionRoutingResponse create(ProductionRoutingCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionBomEntity bom = requireActiveBom(request.bomId(), audit.companyId(), audit.accountBookId());
        requireUniqueRoutingCode(request.routingCode(), audit.companyId(), audit.accountBookId(), null);
        requireNoRoutingForBom(bom.getId(), audit.companyId(), audit.accountBookId(), null);
        List<NormalizedOperation> operations = normalizeOperations(
                request.operations(),
                audit.companyId(),
                audit.accountBookId()
        );
        LocalDateTime now = audit.now();

        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setRoutingCode(requireText(request.routingCode(), "工艺路线编码不能为空"));
        entity.setRoutingName(requireText(request.routingName(), "工艺路线名称不能为空"));
        entity.setBomId(bom.getId());
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(normalizeNullableText(request.remark()));
        fillCreateAudit(entity, audit, now);
        routingMapper.insert(entity);

        insertOperations(entity.getId(), operations, audit, now);
        return toResponse(entity, audit.companyId(), audit.accountBookId());
    }

    @Transactional(readOnly = true)
    public ProductionRoutingResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireRouting(id, audit.companyId(), audit.accountBookId()), audit.companyId(), audit.accountBookId());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionRoutingResponse> list(ProductionRoutingPageQuery query) {
        ProductionRoutingPageQuery safeQuery = query == null ? new ProductionRoutingPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionRoutingEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(queryWrapper -> queryWrapper.like(ProductionRoutingEntity::getRoutingCode, keyword)
                    .or()
                    .like(ProductionRoutingEntity::getRoutingName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionRoutingEntity::getStatus, status);
        }
        if (safeQuery.getBomId() != null) {
            wrapper.eq(ProductionRoutingEntity::getBomId, safeQuery.getBomId());
        }
        wrapper.orderByAsc(ProductionRoutingEntity::getRoutingCode);
        Page<ProductionRoutingEntity> result = routingMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(entity, audit.companyId(), audit.accountBookId()))
                        .toList()
        );
    }

    @Transactional
    public ProductionRoutingResponse update(Long id, ProductionRoutingUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionRoutingEntity entity = requireRouting(id, audit.companyId(), audit.accountBookId());
        requireActiveBom(entity.getBomId(), audit.companyId(), audit.accountBookId());
        List<NormalizedOperation> operations = normalizeOperations(
                request.operations(),
                audit.companyId(),
                audit.accountBookId()
        );
        LocalDateTime now = audit.now();

        entity.setRoutingName(requireText(request.routingName(), "工艺路线名称不能为空"));
        entity.setRemark(normalizeNullableText(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(routingMapper.updateById(entity), "工艺路线已被其他操作修改，请刷新后重试");

        routingOperationMapper.delete(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                .eq(ProductionRoutingOperationEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingOperationEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingOperationEntity::getRoutingId, id));
        insertOperations(id, operations, audit, now);
        return toResponse(requireRouting(id, audit.companyId(), audit.accountBookId()), audit.companyId(), audit.accountBookId());
    }

    @Transactional
    public ProductionRoutingResponse enable(Long id) {
        return updateStatus(id, STATUS_ACTIVE);
    }

    @Transactional
    public ProductionRoutingResponse disable(Long id) {
        return updateStatus(id, STATUS_DISABLED);
    }

    private ProductionRoutingResponse updateStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionRoutingEntity entity = requireRouting(id, audit.companyId(), audit.accountBookId());
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(routingMapper.updateById(entity), "工艺路线已被其他操作修改，请刷新后重试");
        return toResponse(entity, audit.companyId(), audit.accountBookId());
    }

    private List<NormalizedOperation> normalizeOperations(
            List<ProductionRoutingOperationRequest> operations,
            Long companyId,
            Long accountBookId
    ) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("工艺路线至少需要一道工序");
        }
        Set<String> operationCodes = new HashSet<>();
        return operations.stream().map(operation -> normalizeOperation(operation, operationCodes, companyId, accountBookId)).toList();
    }

    private NormalizedOperation normalizeOperation(
            ProductionRoutingOperationRequest operation,
            Set<String> operationCodes,
            Long companyId,
            Long accountBookId
    ) {
        if (operation == null) {
            throw new IllegalArgumentException("工序不能为空");
        }
        String operationCode = requireText(operation.operationCode(), "工序编码不能为空");
        if (!operationCodes.add(operationCode)) {
            throw new IllegalArgumentException("工序编码不能重复");
        }
        String operationName = requireText(operation.operationName(), "工序名称不能为空");
        BigDecimal standardMinutes = normalizeStandardMinutes(operation.standardMinutes());
        requireActiveWorkCenter(operation.workCenterId(), companyId, accountBookId);
        return new NormalizedOperation(
                operationCode,
                operationName,
                operation.workCenterId(),
                standardMinutes,
                normalizeNullableText(operation.remark())
        );
    }

    private void insertOperations(
            Long routingId,
            List<NormalizedOperation> operations,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        int lineNo = 1;
        for (NormalizedOperation operation : operations) {
            ProductionRoutingOperationEntity entity = new ProductionRoutingOperationEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setRoutingId(routingId);
            entity.setLineNo(lineNo++);
            entity.setOperationCode(operation.operationCode());
            entity.setOperationName(operation.operationName());
            entity.setWorkCenterId(operation.workCenterId());
            entity.setStandardMinutes(operation.standardMinutes());
            entity.setRemark(operation.remark());
            fillCreateAudit(entity, audit, now);
            routingOperationMapper.insert(entity);
        }
    }

    private ProductionRoutingResponse toResponse(ProductionRoutingEntity entity, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = findBom(entity.getBomId(), companyId, accountBookId);
        List<ProductionRoutingOperationEntity> operations = routingOperationMapper.selectList(new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                .eq(ProductionRoutingOperationEntity::getCompanyId, companyId)
                .eq(ProductionRoutingOperationEntity::getAccountBookId, accountBookId)
                .eq(ProductionRoutingOperationEntity::getRoutingId, entity.getId())
                .orderByAsc(ProductionRoutingOperationEntity::getLineNo));
        Map<Long, ProductionWorkCenterEntity> workCenters = loadWorkCenters(operations, companyId, accountBookId);
        return new ProductionRoutingResponse(
                entity.getId(),
                entity.getRoutingCode(),
                entity.getRoutingName(),
                entity.getBomId(),
                bom == null ? null : bom.getBomNo(),
                bom == null ? null : bom.getProductId(),
                entity.getStatus(),
                entity.getRemark(),
                operations.stream().map(operation -> {
                    ProductionWorkCenterEntity workCenter = workCenters.get(operation.getWorkCenterId());
                    return new ProductionRoutingOperationResponse(
                            operation.getId(),
                            operation.getLineNo(),
                            operation.getOperationCode(),
                            operation.getOperationName(),
                            operation.getWorkCenterId(),
                            workCenter == null ? null : workCenter.getWorkCenterCode(),
                            workCenter == null ? null : workCenter.getWorkCenterName(),
                            operation.getStandardMinutes(),
                            operation.getRemark()
                    );
                }).toList()
        );
    }

    private Map<Long, ProductionWorkCenterEntity> loadWorkCenters(
            List<ProductionRoutingOperationEntity> operations,
            Long companyId,
            Long accountBookId
    ) {
        if (operations.isEmpty()) {
            return Map.of();
        }
        List<Long> workCenterIds = operations.stream()
                .map(ProductionRoutingOperationEntity::getWorkCenterId)
                .distinct()
                .toList();
        Map<Long, ProductionWorkCenterEntity> workCenters = new HashMap<>();
        workCenterMapper.selectList(new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                        .eq(ProductionWorkCenterEntity::getCompanyId, companyId)
                        .eq(ProductionWorkCenterEntity::getAccountBookId, accountBookId)
                        .eq(ProductionWorkCenterEntity::getDeletedFlag, 0)
                        .in(ProductionWorkCenterEntity::getId, workCenterIds))
                .forEach(entity -> workCenters.put(entity.getId(), entity));
        return workCenters;
    }

    private ProductionRoutingEntity requireRouting(Long id, Long companyId, Long accountBookId) {
        ProductionRoutingEntity entity = routingMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工艺路线不存在");
        }
        return entity;
    }

    private ProductionBomEntity requireActiveBom(Long bomId, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = bomId == null ? null : bomMapper.selectById(bomId);
        if (bom == null
                || !Objects.equals(bom.getCompanyId(), companyId)
                || !Objects.equals(bom.getAccountBookId(), accountBookId)
                || bom.getDeletedFlag() == null
                || bom.getDeletedFlag() != 0
                || !STATUS_ACTIVE.equalsIgnoreCase(bom.getStatus())) {
            throw new IllegalArgumentException("BOM不存在或已停用");
        }
        return bom;
    }

    private ProductionBomEntity findBom(Long bomId, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = bomId == null ? null : bomMapper.selectById(bomId);
        if (bom == null
                || !Objects.equals(bom.getCompanyId(), companyId)
                || !Objects.equals(bom.getAccountBookId(), accountBookId)
                || bom.getDeletedFlag() == null
                || bom.getDeletedFlag() != 0) {
            return null;
        }
        return bom;
    }

    private ProductionWorkCenterEntity requireActiveWorkCenter(Long workCenterId, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity workCenter = workCenterId == null ? null : workCenterMapper.selectById(workCenterId);
        if (workCenter == null
                || !Objects.equals(workCenter.getCompanyId(), companyId)
                || !Objects.equals(workCenter.getAccountBookId(), accountBookId)
                || workCenter.getDeletedFlag() == null
                || workCenter.getDeletedFlag() != 0
                || !STATUS_ACTIVE.equalsIgnoreCase(workCenter.getStatus())) {
            throw new IllegalArgumentException("工作中心不存在或已停用");
        }
        return workCenter;
    }

    private void requireUniqueRoutingCode(String routingCode, Long companyId, Long accountBookId, Long excludedId) {
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, companyId)
                .eq(ProductionRoutingEntity::getAccountBookId, accountBookId)
                .eq(ProductionRoutingEntity::getRoutingCode, requireText(routingCode, "工艺路线编码不能为空"))
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionRoutingEntity::getId, excludedId);
        }
        if (routingMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("工艺路线编码已存在");
        }
    }

    private void requireNoRoutingForBom(Long bomId, Long companyId, Long accountBookId, Long excludedId) {
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, companyId)
                .eq(ProductionRoutingEntity::getAccountBookId, accountBookId)
                .eq(ProductionRoutingEntity::getBomId, bomId)
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        if (excludedId != null) {
            wrapper.ne(ProductionRoutingEntity::getId, excludedId);
        }
        if (routingMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("当前BOM已存在工艺路线");
        }
    }

    private void fillCreateAudit(ProductionRoutingEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ProductionRoutingOperationEntity entity, AuditMetadata audit, LocalDateTime now) {
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

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeStandardMinutes(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("标准工时必须大于0");
        }
        return normalized;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private record NormalizedOperation(
            String operationCode,
            String operationName,
            Long workCenterId,
            BigDecimal standardMinutes,
            String remark
    ) {
    }
}

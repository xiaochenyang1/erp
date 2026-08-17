package com.tuowei.erp.production.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderMaterialResponse;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductionOrderService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_MATERIAL_ISSUED = "MATERIAL_ISSUED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String SOURCE_TYPE = "PRODUCTION_ORDER";

    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionOrderNumberService numberService;
    private final ProductionBomService bomService;
    private final InventoryPostingService inventoryPostingService;
    private final ProductValidator productValidator;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final ProductionOperationService productionOperationService;
    private final AttachmentService attachmentService;

    public ProductionOrderService(
            ProductionOrderMapper orderMapper,
            ProductionOrderMaterialMapper materialMapper,
            ProductionOrderNumberService numberService,
            ProductionBomService bomService,
            InventoryPostingService inventoryPostingService,
            ProductValidator productValidator,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            ProductionOperationService productionOperationService,
            AttachmentService attachmentService
    ) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.numberService = numberService;
        this.bomService = bomService;
        this.inventoryPostingService = inventoryPostingService;
        this.productValidator = productValidator;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.productionOperationService = productionOperationService;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ProductionOrderResponse create(ProductionOrderCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionBomEntity bom = bomService.requireBom(request.bomId(), audit.companyId(), audit.accountBookId());
        productValidator.requireProduct(bom.getProductId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.materialWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.finishedWarehouseId(), audit.companyId(), audit.accountBookId());
        BigDecimal plannedQty = requirePositiveQty(request.plannedQty(), "生产计划数量必须大于0");
        if (request.plannedFinishDate().isBefore(request.plannedStartDate())) {
            throw new IllegalArgumentException("计划完工日期不能早于计划开工日期");
        }

        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setCompanyId(audit.companyId());
        order.setAccountBookId(audit.accountBookId());
        order.setOrderNo(numberService.nextOrderNo(request.plannedStartDate()));
        order.setBomId(bom.getId());
        order.setProductId(bom.getProductId());
        order.setMaterialWarehouseId(request.materialWarehouseId());
        order.setFinishedWarehouseId(request.finishedWarehouseId());
        order.setPlannedQty(plannedQty);
        order.setCompletedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate());
        order.setStatus(STATUS_DRAFT);
        order.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        order.setFinishedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        order.setDeletedFlag(0);
        order.setRemark(request.remark());
        fillCreateAudit(order, audit, now);
        assertCanView(order);
        orderMapper.insert(order);
        insertMaterials(order, bom, plannedQty, audit, now);
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionOrderEntity order = requireOrder(id);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的生产工单可以修改");
        }
        requireWarehouse(request.materialWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.finishedWarehouseId(), audit.companyId(), audit.accountBookId());
        BigDecimal plannedQty = requirePositiveQty(request.plannedQty(), "生产计划数量必须大于0");
        if (request.plannedFinishDate().isBefore(request.plannedStartDate())) {
            throw new IllegalArgumentException("计划完工日期不能早于计划开工日期");
        }
        ProductionBomEntity bom = bomService.requireBom(order.getBomId(), audit.companyId(), audit.accountBookId());
        order.setMaterialWarehouseId(request.materialWarehouseId());
        order.setFinishedWarehouseId(request.finishedWarehouseId());
        order.setPlannedQty(plannedQty);
        order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate());
        order.setRemark(request.remark());
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        materialMapper.delete(new LambdaQueryWrapper<ProductionOrderMaterialEntity>()
                .eq(ProductionOrderMaterialEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderMaterialEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderMaterialEntity::getOrderId, id));
        insertMaterials(order, bom, plannedQty, audit, now);
        return toResponse(orderMapper.selectById(id));
    }

    @Transactional(readOnly = true)
    public ProductionOrderResponse getById(Long id) {
        return toResponse(requireOrder(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionOrderResponse> list(ProductionOrderPageQuery query) {
        ProductionOrderPageQuery safeQuery = query == null ? new ProductionOrderPageQuery() : query;
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        Page<ProductionOrderEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = buildListQuery(safeQuery, currentUser.companyId(), currentUser.accountBookId());
        wrapper = applyProductionOrderScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<ProductionOrderEntity> result = orderMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional
    public ProductionOrderResponse release(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireOrder(id);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的生产工单可以释放");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, order.getId());
        List<ProductionOrderMaterialEntity> materials = selectMaterials(order);
        for (ProductionOrderMaterialEntity material : materials) {
            inventoryPostingService.reserve(
                    new InventoryReservationCommand(
                            order.getMaterialWarehouseId(),
                            material.getMaterialProductId(),
                            SOURCE_TYPE,
                            order.getId(),
                            order.getOrderNo(),
                            material.getId(),
                            material.getRequiredQty(),
                            material.getRemark()
                    ),
                    audit,
                    "材料可用量不足，不能释放生产工单"
            );
        }
        order.setStatus(STATUS_RELEASED);
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        productionOperationService.generateForReleasedOrder(order, audit);
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireOrder(id);
        if (STATUS_COMPLETED.equals(order.getStatus()) || STATUS_MATERIAL_ISSUED.equals(order.getStatus())) {
            throw new IllegalArgumentException("已领料或已完工的生产工单不能取消");
        }
        if (STATUS_RELEASED.equals(order.getStatus())) {
            inventoryPostingService.releaseAllReservations(SOURCE_TYPE, order.getId(), audit);
        }
        order.setStatus(STATUS_CANCELLED);
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        return toResponse(order);
    }

    public ProductionOrderEntity requireOrder(Long id) {
        ProductionOrderEntity order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeletedFlag())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        assertCanView(order);
        return order;
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(Long orderId) {
        return selectMaterials(requireOrder(orderId));
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(ProductionOrderEntity order) {
        return materialMapper.selectList(new LambdaQueryWrapper<ProductionOrderMaterialEntity>()
                .eq(ProductionOrderMaterialEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderMaterialEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderMaterialEntity::getOrderId, order.getId())
                .orderByAsc(ProductionOrderMaterialEntity::getLineNo));
    }

    public ProductionOrderResponse toResponse(ProductionOrderEntity order) {
        return new ProductionOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getBomId(),
                order.getProductId(),
                order.getFinishedWarehouseId(),
                order.getMaterialWarehouseId(),
                order.getPlannedQty(),
                order.getCompletedQty(),
                order.getPlannedStartDate(),
                order.getPlannedFinishDate(),
                order.getStatus(),
                order.getIssuedAmount(),
                order.getFinishedAmount(),
                order.getRemark(),
                selectMaterials(order).stream()
                        .map(material -> new ProductionOrderMaterialResponse(
                                material.getId(),
                                material.getLineNo(),
                                material.getMaterialProductId(),
                                material.getRequiredQty(),
                                material.getIssuedQty(),
                                material.getIssuedAmount(),
                                material.getRemark()
                        ))
                        .toList()
        );
    }

    private void insertMaterials(
            ProductionOrderEntity order,
            ProductionBomEntity bom,
            BigDecimal plannedQty,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<ProductionBomLineEntity> bomLines = bomService.selectLines(bom.getId());
        for (ProductionBomLineEntity bomLine : bomLines) {
            ProductionOrderMaterialEntity material = new ProductionOrderMaterialEntity();
            material.setCompanyId(audit.companyId());
            material.setAccountBookId(audit.accountBookId());
            material.setOrderId(order.getId());
            material.setLineNo(bomLine.getLineNo());
            material.setMaterialProductId(bomLine.getMaterialProductId());
            material.setRequiredQty(expandRequiredQty(bom, bomLine, plannedQty));
            material.setIssuedQty(ScalePrecision.quantity(BigDecimal.ZERO));
            material.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO));
            material.setRemark(bomLine.getRemark());
            fillCreateAudit(material, audit, now);
            materialMapper.insert(material);
        }
    }

    private BigDecimal expandRequiredQty(ProductionBomEntity bom, ProductionBomLineEntity line, BigDecimal plannedQty) {
        BigDecimal baseQty = ScalePrecision.quantity(bom.getBaseQty());
        BigDecimal lossRate = ScalePrecision.zeroDefault(line.getLossRate());
        BigDecimal factor = BigDecimal.ONE.add(lossRate);
        return ScalePrecision.quantity(line.getQtyPer()
                .multiply(plannedQty)
                .divide(baseQty, 8, RoundingMode.HALF_UP)
                .multiply(factor));
    }

    private BigDecimal requirePositiveQty(BigDecimal qty, String message) {
        BigDecimal scaled = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return scaled;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private void assertCanView(ProductionOrderEntity order) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = order.getCreatedBy() == null ? null : userMapper.selectById(order.getCreatedBy());
        dataScopeService.assertCanViewProductionOrder(
                order,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private LambdaQueryWrapper<ProductionOrderEntity> buildListQuery(ProductionOrderPageQuery query, Long companyId, Long accountBookId) {
        LambdaQueryWrapper<ProductionOrderEntity> wrapper = new LambdaQueryWrapper<ProductionOrderEntity>()
                .eq(ProductionOrderEntity::getCompanyId, companyId)
                .eq(ProductionOrderEntity::getAccountBookId, accountBookId)
                .eq(ProductionOrderEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ProductionOrderEntity::getOrderNo, keyword);
        }
        String status = normalizeStatus(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionOrderEntity::getStatus, status);
        }
        if (query.getBomId() != null) {
            wrapper.eq(ProductionOrderEntity::getBomId, query.getBomId());
        }
        if (query.getProductId() != null) {
            wrapper.eq(ProductionOrderEntity::getProductId, query.getProductId());
        }
        if (query.getMaterialWarehouseId() != null) {
            wrapper.eq(ProductionOrderEntity::getMaterialWarehouseId, query.getMaterialWarehouseId());
        }
        if (query.getFinishedWarehouseId() != null) {
            wrapper.eq(ProductionOrderEntity::getFinishedWarehouseId, query.getFinishedWarehouseId());
        }
        return wrapper.orderByDesc(ProductionOrderEntity::getId);
    }

    private LambdaQueryWrapper<ProductionOrderEntity> applyProductionOrderScope(
            LambdaQueryWrapper<ProductionOrderEntity> wrapper,
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        if (snapshot.hasAllScope()) {
            return wrapper;
        }
        Set<Long> visibleCreatorIds = visibleCreatorIds(currentUser, snapshot, deptUserIds, postUserIds);
        Set<Long> warehouseIds = snapshot.warehouseIds();
        if (visibleCreatorIds.isEmpty() && warehouseIds.isEmpty()) {
            return wrapper.apply("1 = 0");
        }
        if (visibleCreatorIds.isEmpty()) {
            return wrapper.in(ProductionOrderEntity::getMaterialWarehouseId, warehouseIds)
                    .in(ProductionOrderEntity::getFinishedWarehouseId, warehouseIds);
        }
        if (warehouseIds.isEmpty()) {
            return wrapper.in(ProductionOrderEntity::getCreatedBy, visibleCreatorIds);
        }
        return wrapper.and(query -> query
                .in(ProductionOrderEntity::getCreatedBy, visibleCreatorIds)
                .or(scope -> scope
                        .in(ProductionOrderEntity::getMaterialWarehouseId, warehouseIds)
                        .in(ProductionOrderEntity::getFinishedWarehouseId, warehouseIds)));
    }

    private Set<Long> visibleCreatorIds(
            CurrentUser currentUser,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        Set<Long> visibleCreatorIds = new LinkedHashSet<>();
        if (snapshot.selfScoped()) {
            visibleCreatorIds.add(currentUser.userId());
        }
        if (snapshot.deptScoped()) {
            visibleCreatorIds.addAll(deptUserIds);
        }
        if (snapshot.postScoped()) {
            visibleCreatorIds.addAll(postUserIds);
        }
        return visibleCreatorIds;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private void fillCreateAudit(ProductionOrderEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ProductionOrderMaterialEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

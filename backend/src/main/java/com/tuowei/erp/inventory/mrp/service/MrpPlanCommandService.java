package com.tuowei.erp.inventory.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.model.MrpRunEntity;
import com.tuowei.erp.inventory.mrp.model.MrpRunLineEntity;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Write-side persistence and order conversion for executable MRP runs. */
@Service
public class MrpPlanCommandService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String TYPE_PURCHASE = "PURCHASE";
    private static final String TYPE_PRODUCTION = "PRODUCTION";

    private final AuditMetadataFactory auditMetadataFactory;
    private final MrpRunMapper mrpRunMapper;
    private final MrpRunLineMapper mrpRunLineMapper;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final PurchaseOrderService purchaseOrderService;
    private final ProductionOrderService productionOrderService;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final MrpPlanQueryService mrpPlanQueryService;
    private final MrpPlanCalculationService mrpPlanCalculationService;
    private final MrpSnapshotCodec snapshotCodec;
    private final MrpAccessPolicy accessPolicy;

    public MrpPlanCommandService(
            AuditMetadataFactory auditMetadataFactory,
            MrpRunMapper mrpRunMapper,
            MrpRunLineMapper mrpRunLineMapper,
            SequenceNumberGenerator sequenceNumberGenerator,
            PurchaseOrderService purchaseOrderService,
            ProductionOrderService productionOrderService,
            ProductMapper productMapper,
            SupplierMapper supplierMapper,
            WarehouseMapper warehouseMapper,
            MrpPlanQueryService mrpPlanQueryService,
            MrpPlanCalculationService mrpPlanCalculationService,
            MrpSnapshotCodec snapshotCodec,
            MrpAccessPolicy accessPolicy
    ) {
        this.auditMetadataFactory = auditMetadataFactory;
        this.mrpRunMapper = mrpRunMapper;
        this.mrpRunLineMapper = mrpRunLineMapper;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.purchaseOrderService = purchaseOrderService;
        this.productionOrderService = productionOrderService;
        this.productMapper = productMapper;
        this.supplierMapper = supplierMapper;
        this.warehouseMapper = warehouseMapper;
        this.mrpPlanQueryService = mrpPlanQueryService;
        this.mrpPlanCalculationService = mrpPlanCalculationService;
        this.snapshotCodec = snapshotCodec;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MrpRunResponse run() {
        accessPolicy.requireFullScope();
        AuditMetadata audit = auditMetadataFactory.current();
        mrpPlanCalculationService.lockPlanning(audit);
        Long companyId = audit.companyId();
        Long bookId = audit.accountBookId();
        LocalDateTime now = audit.now();
        LocalDate asOf = now.toLocalDate();
        MrpPlanCalculationService.CalculationResult calculation = mrpPlanCalculationService.calculate(audit);
        List<MrpPlanCalculationService.ComputedLine> productionLines = calculation.productionLines();
        List<MrpPlanCalculationService.ComputedLine> purchaseLines = calculation.purchaseLines();

        MrpRunEntity run = new MrpRunEntity();
        run.setCompanyId(companyId);
        run.setAccountBookId(bookId);
        run.setRunNo(sequenceNumberGenerator.nextNumber("MRP_RUN", "MRP计划", asOf));
        run.setAsOfDate(asOf);
        run.setStatus(purchaseLines.isEmpty() && productionLines.isEmpty() ? STATUS_CLOSED : STATUS_OPEN);
        run.setAlgorithmVersion(MrpCalculationSnapshot.CURRENT_VERSION);
        run.setCalculationSnapshot(snapshotCodec.write(calculation.snapshot()));
        run.setPurchaseCount(purchaseLines.size());
        run.setProductionCount(productionLines.size());
        run.setDeletedFlag(0);
        run.setCreatedBy(audit.userId());
        run.setCreatedTime(now);
        run.setUpdatedBy(audit.userId());
        run.setUpdatedTime(now);
        run.setVersion(0);
        mrpRunMapper.insert(run);

        int lineNo = 1;
        for (MrpPlanCalculationService.ComputedLine line : productionLines) {
            insertLine(run, line, lineNo++, audit, now);
        }
        for (MrpPlanCalculationService.ComputedLine line : purchaseLines) {
            insertLine(run, line, lineNo++, audit, now);
        }
        return mrpPlanQueryService.getById(run.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MrpSuggestionLineResponse convertLine(Long runId, Long lineId, MrpConvertLineRequest request) {
        accessPolicy.requireFullScope();
        AuditMetadata audit = auditMetadataFactory.current();
        mrpPlanCalculationService.lockPlanning(audit);
        LocalDateTime now = audit.now();
        MrpRunEntity run = mrpPlanQueryService.requireRun(runId, audit);
        if (!STATUS_OPEN.equals(run.getStatus())) {
            throw new IllegalArgumentException("当前MRP计划状态不允许转单");
        }
        MrpRunLineEntity line = mrpPlanQueryService.requireLine(runId, lineId, audit);
        if (!STATUS_OPEN.equals(line.getStatus())) {
            throw new IllegalArgumentException("当前建议行状态不允许转单");
        }
        accessPolicy.requireConversion(line.getSuggestionType());
        requireCurrentCalculation(run, line, audit);
        if (TYPE_PURCHASE.equals(line.getSuggestionType())) {
            convertPurchase(line, request, audit, now);
        } else if (TYPE_PRODUCTION.equals(line.getSuggestionType())) {
            convertProduction(line, request, audit, now);
        } else {
            throw new IllegalArgumentException("未知建议类型: " + line.getSuggestionType());
        }
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                mrpRunLineMapper.updateById(line),
                "MRP建议行已被其他操作修改，请刷新后重试"
        );
        refreshRunStatus(run, audit, now);
        return mrpPlanQueryService.toLineResponse(line, audit);
    }

    private void convertPurchase(
            MrpRunLineEntity line,
            MrpConvertLineRequest request,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        Long supplierId = request == null ? null : request.supplierId();
        if (supplierId == null) {
            throw new IllegalArgumentException("请选择供应商后再转采购订单");
        }
        requireActiveWarehouse(line.getWarehouseId(), audit.companyId(), audit.accountBookId());
        requireActiveSupplier(supplierId, audit.companyId(), audit.accountBookId());
        ProductEntity product = requireProduct(line.getProductId(), audit.companyId(), audit.accountBookId());
        BigDecimal price = ScalePrecision.amount(
                product.getPurchasePrice() == null ? BigDecimal.ZERO : product.getPurchasePrice()
        );
        BigDecimal taxRate = product.getTaxRate() == null ? BigDecimal.ZERO : product.getTaxRate();
        if (taxRate.compareTo(BigDecimal.ONE) > 0) {
            taxRate = taxRate.divide(new BigDecimal("100"));
        }
        PurchaseOrderResponse order = purchaseOrderService.create(new PurchaseOrderCreateRequest(
                null,
                supplierId,
                now.toLocalDate(),
                now.toLocalDate().plusDays(7),
                null,
                null,
                "由MRP计划行生成",
                List.of(new PurchaseOrderLineRequest(
                        line.getProductId(),
                        line.getNetQty(),
                        price,
                        taxRate,
                        "MRP " + line.getRunId() + " / line " + line.getLineNo()
                )),
                line.getWarehouseId()
        ));
        line.setStatus(STATUS_CONVERTED);
        line.setConvertedBizType("PURCHASE_ORDER");
        line.setConvertedBizId(order.id());
        line.setConvertedBizNo(order.orderNo());
        line.setConvertedTime(now);
    }

    private void convertProduction(
            MrpRunLineEntity line,
            MrpConvertLineRequest request,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        if (line.getBomId() == null) {
            throw new IllegalArgumentException("生产建议缺少BOM，无法转生产订单");
        }
        Long finishedWarehouseId = line.getWarehouseId();
        Long materialWarehouseId = line.getMaterialWarehouseId();
        if (request != null && ((request.finishedWarehouseId() != null && !Objects.equals(request.finishedWarehouseId(), finishedWarehouseId))
                || (request.materialWarehouseId() != null && !Objects.equals(request.materialWarehouseId(), materialWarehouseId)))) {
            throw new IllegalArgumentException("转单仓库必须与MRP计算仓库一致，请修改商品投料仓配置后重新运行计划");
        }
        requireActiveWarehouse(materialWarehouseId, audit.companyId(), audit.accountBookId());
        requireActiveWarehouse(finishedWarehouseId, audit.companyId(), audit.accountBookId());
        LocalDate start = now.toLocalDate();
        ProductionOrderResponse order = productionOrderService.create(new ProductionOrderCreateRequest(
                line.getBomId(),
                finishedWarehouseId,
                materialWarehouseId,
                line.getNetQty(),
                start,
                start.plusDays(7),
                "由MRP计划行生成"
        ));
        line.setStatus(STATUS_CONVERTED);
        line.setConvertedBizType("PRODUCTION_ORDER");
        line.setConvertedBizId(order.id());
        line.setConvertedBizNo(order.orderNo());
        line.setConvertedTime(now);
    }

    private void refreshRunStatus(MrpRunEntity run, AuditMetadata audit, LocalDateTime now) {
        Long openCount = mrpRunLineMapper.selectCount(new LambdaQueryWrapper<MrpRunLineEntity>()
                .eq(MrpRunLineEntity::getCompanyId, audit.companyId())
                .eq(MrpRunLineEntity::getAccountBookId, audit.accountBookId())
                .eq(MrpRunLineEntity::getRunId, run.getId())
                .eq(MrpRunLineEntity::getDeletedFlag, 0)
                .eq(MrpRunLineEntity::getStatus, STATUS_OPEN));
        if (openCount != null && openCount == 0L && STATUS_OPEN.equals(run.getStatus())) {
            run.setStatus(STATUS_CLOSED);
            run.setUpdatedBy(audit.userId());
            run.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    mrpRunMapper.updateById(run),
                    "MRP计划已被其他操作修改，请刷新后重试"
            );
        }
    }

    private void insertLine(
            MrpRunEntity run,
            MrpPlanCalculationService.ComputedLine computed,
            int lineNo,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        MrpRunLineEntity entity = new MrpRunLineEntity();
        entity.setCompanyId(run.getCompanyId());
        entity.setAccountBookId(run.getAccountBookId());
        entity.setRunId(run.getId());
        entity.setLineNo(lineNo);
        entity.setProductId(computed.productId());
        entity.setSuggestionType(computed.suggestionType());
        entity.setDemandQty(ScalePrecision.quantity(computed.demandQty()));
        entity.setOnHandQty(ScalePrecision.quantity(computed.onHandQty()));
        entity.setOpenSupplyQty(ScalePrecision.quantity(computed.openSupplyQty()));
        entity.setNetQty(ScalePrecision.quantity(computed.netQty()));
        entity.setBomId(computed.bomId());
        entity.setReason(computed.reason());
        if (computed.trace() != null) {
            entity.setWarehouseId(computed.trace().warehouseId());
            entity.setMaterialWarehouseId(computed.trace().materialWarehouseId());
            entity.setBomVersion(computed.trace().bomVersion());
            entity.setBomLevel(computed.trace().level());
            entity.setCalculationTrace(snapshotCodec.write(computed.trace()));
        }
        entity.setStatus(STATUS_OPEN);
        entity.setDeletedFlag(0);
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        mrpRunLineMapper.insert(entity);
    }

    private ProductEntity requireProduct(Long productId, Long companyId, Long bookId) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null
                || product.getDeletedFlag() == null
                || product.getDeletedFlag() != 0
                || !Objects.equals(product.getCompanyId(), companyId)
                || !Objects.equals(product.getAccountBookId(), bookId)) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    private void requireActiveSupplier(Long supplierId, Long companyId, Long bookId) {
        SupplierEntity supplier = supplierMapper.selectById(supplierId);
        if (supplier == null
                || supplier.getDeletedFlag() == null
                || supplier.getDeletedFlag() != 0
                || !Objects.equals(supplier.getCompanyId(), companyId)
                || !Objects.equals(supplier.getAccountBookId(), bookId)
                || !"ACTIVE".equalsIgnoreCase(String.valueOf(supplier.getStatus()))) {
            throw new IllegalArgumentException("供应商不存在或未启用");
        }
    }

    private void requireActiveWarehouse(Long warehouseId, Long companyId, Long bookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null
                || warehouse.getDeletedFlag() == null
                || warehouse.getDeletedFlag() != 0
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), bookId)
                || !"ACTIVE".equalsIgnoreCase(String.valueOf(warehouse.getStatus()))) {
            throw new IllegalArgumentException("仓库不存在或未启用");
        }
    }

    private void requireCurrentCalculation(MrpRunEntity run, MrpRunLineEntity line, AuditMetadata audit) {
        MrpCalculationSnapshot saved = snapshotCodec.snapshot(run.getCalculationSnapshot());
        MrpCalculationSnapshot.Node previous = snapshotCodec.node(line.getCalculationTrace());
        if (!Objects.equals(run.getAlgorithmVersion(), MrpCalculationSnapshot.CURRENT_VERSION)
                || saved == null || previous == null || line.getWarehouseId() == null) {
            throw new BusinessConflictException("历史MRP计划缺少分仓计算依据，请重新运行计划后转单");
        }
        if (!saved.warnings().isEmpty()) {
            throw new BusinessConflictException("请先补充在途采购订单的预计收货仓库，再重新运行MRP");
        }
        MrpCalculationSnapshot current = mrpPlanCalculationService.calculate(audit).snapshot();
        if (!current.warnings().isEmpty()) {
            throw new BusinessConflictException("在途采购订单缺少预计收货仓库，请补充后重新运行MRP");
        }
        MrpCalculationSnapshot.Node fresh = current.nodes().stream()
                .filter(n -> Objects.equals(n.productId(), line.getProductId()) && Objects.equals(n.warehouseId(), line.getWarehouseId()))
                .findFirst().orElse(null);
        if (fresh == null || fresh.netQty().signum() <= 0
                || !Objects.equals(fresh.supplyType(), line.getSuggestionType())
                || fresh.netQty().compareTo(line.getNetQty()) != 0
                || fresh.demandQty().compareTo(line.getDemandQty()) != 0
                || fresh.onHandQty().compareTo(line.getOnHandQty()) != 0
                || fresh.openSupplyQty().compareTo(line.getOpenSupplyQty()) != 0
                || !Objects.equals(fresh.bomId(), line.getBomId())
                || !Objects.equals(fresh.bomVersion(), line.getBomVersion())
                || !Objects.equals(fresh.materialWarehouseId(), line.getMaterialWarehouseId())
                || !Objects.equals(fresh.materials(), previous.materials())) {
            throw new BusinessConflictException("需求、库存、在途或BOM已变化，请重新运行MRP后转单");
        }
    }
}

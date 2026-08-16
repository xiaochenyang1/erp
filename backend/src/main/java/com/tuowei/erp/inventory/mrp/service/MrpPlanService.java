package com.tuowei.erp.inventory.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.model.MrpRunEntity;
import com.tuowei.erp.inventory.mrp.model.MrpRunLineEntity;
import com.tuowei.erp.inventory.mrp.web.MrpConvertLineRequest;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpRunSummaryResponse;
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

/**
 * 轻量 MRP：
 * 独立需求 = 已审批未发完销售订单缺口 + 安全库存（预警规则 min_qty 若有）
 * 供应 = 现存量 + 在途采购(已审批未完成) + 在制生产(已下达/领料未完工)
 * 有启用 BOM 的成品建议生产，否则建议采购；BOM 材料展开叠加到材料需求。
 * 运行结果持久化为 inv_mrp_run / inv_mrp_run_line，支持转采购订单或生产订单草稿。
 */
@Service
@NativeSqlTenantScoped("MRP run scoped by current tenant")
public class MrpPlanService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
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

    public MrpPlanService(
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
            MrpPlanCalculationService mrpPlanCalculationService
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
    }

    @Transactional
    public MrpRunResponse run() {
        AuditMetadata audit = auditMetadataFactory.current();
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
        run.setStatus(STATUS_OPEN);
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

    @Transactional(readOnly = true)
    public PageResponse<MrpRunSummaryResponse> listRuns(MrpRunPageQuery query) {
        MrpRunPageQuery safeQuery = query == null ? new MrpRunPageQuery() : query;
        return mrpPlanQueryService.listRuns(safeQuery);
    }

    @Transactional(readOnly = true)
    public MrpRunResponse getById(Long id) {
        return mrpPlanQueryService.getById(id);
    }

    @Transactional
    public MrpSuggestionLineResponse convertLine(Long runId, Long lineId, MrpConvertLineRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        MrpRunEntity run = mrpPlanQueryService.requireRun(runId, audit);
        if (!STATUS_OPEN.equals(run.getStatus())) {
            throw new IllegalArgumentException("当前MRP计划状态不允许转单");
        }
        MrpRunLineEntity line = mrpPlanQueryService.requireLine(runId, lineId, audit);
        if (!STATUS_OPEN.equals(line.getStatus())) {
            throw new IllegalArgumentException("当前建议行状态不允许转单");
        }
        if (TYPE_PURCHASE.equals(line.getSuggestionType())) {
            convertPurchase(line, request, audit, now);
        } else if (TYPE_PRODUCTION.equals(line.getSuggestionType())) {
            convertProduction(line, request, audit, now);
        } else {
            throw new IllegalArgumentException("未知建议类型: " + line.getSuggestionType());
        }
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(mrpRunLineMapper.updateById(line), "MRP建议行已被其他操作修改，请刷新后重试");
        refreshRunStatus(run, audit, now);
        return mrpPlanQueryService.toLineResponse(line, audit);
    }

    private void convertPurchase(MrpRunLineEntity line, MrpConvertLineRequest request, AuditMetadata audit, LocalDateTime now) {
        Long supplierId = request == null ? null : request.supplierId();
        if (supplierId == null) {
            supplierId = findDefaultSupplierId(audit.companyId(), audit.accountBookId());
        }
        if (supplierId == null) {
            throw new IllegalArgumentException("请选择供应商后再转采购订单");
        }
        requireActiveSupplier(supplierId, audit.companyId(), audit.accountBookId());
        ProductEntity product = requireProduct(line.getProductId(), audit.companyId(), audit.accountBookId());
        BigDecimal price = ScalePrecision.amount(product.getPurchasePrice() == null ? BigDecimal.ZERO : product.getPurchasePrice());
        BigDecimal taxRate = product.getTaxRate() == null ? BigDecimal.ZERO : product.getTaxRate();
        if (taxRate.compareTo(BigDecimal.ONE) > 0) {
            taxRate = taxRate.divide(new BigDecimal("100"));
        }
        PurchaseOrderResponse order = purchaseOrderService.create(new PurchaseOrderCreateRequest(
                supplierId,
                now.toLocalDate(),
                now.toLocalDate().plusDays(7),
                "由MRP计划行生成",
                List.of(new PurchaseOrderLineRequest(
                        line.getProductId(),
                        line.getNetQty(),
                        price,
                        taxRate,
                        "MRP " + line.getRunId() + " / line " + line.getLineNo()
                ))
        ));
        line.setStatus(STATUS_CONVERTED);
        line.setConvertedBizType("PURCHASE_ORDER");
        line.setConvertedBizId(order.id());
        line.setConvertedBizNo(order.orderNo());
        line.setConvertedTime(now);
    }

    private void convertProduction(MrpRunLineEntity line, MrpConvertLineRequest request, AuditMetadata audit, LocalDateTime now) {
        if (line.getBomId() == null) {
            throw new IllegalArgumentException("生产建议缺少BOM，无法转生产订单");
        }
        Long finishedWarehouseId = request == null ? null : request.finishedWarehouseId();
        Long materialWarehouseId = request == null ? null : request.materialWarehouseId();
        if (finishedWarehouseId == null || materialWarehouseId == null) {
            Long defaultWarehouseId = findDefaultWarehouseId(audit.companyId(), audit.accountBookId());
            if (finishedWarehouseId == null) {
                finishedWarehouseId = defaultWarehouseId;
            }
            if (materialWarehouseId == null) {
                materialWarehouseId = defaultWarehouseId;
            }
        }
        if (finishedWarehouseId == null || materialWarehouseId == null) {
            throw new IllegalArgumentException("请选择材料仓和成品仓后再转生产订单");
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
            run.setStatus("CLOSED");
            run.setUpdatedBy(audit.userId());
            run.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(mrpRunMapper.updateById(run), "MRP计划已被其他操作修改，请刷新后重试");
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

    private Long findDefaultSupplierId(Long companyId, Long bookId) {
        SupplierEntity supplier = supplierMapper.selectOne(new LambdaQueryWrapper<SupplierEntity>()
                .eq(SupplierEntity::getCompanyId, companyId)
                .eq(SupplierEntity::getAccountBookId, bookId)
                .eq(SupplierEntity::getDeletedFlag, 0)
                .eq(SupplierEntity::getStatus, "ACTIVE")
                .orderByAsc(SupplierEntity::getId)
                .last("limit 1"));
        return supplier == null ? null : supplier.getId();
    }

    private Long findDefaultWarehouseId(Long companyId, Long bookId) {
        WarehouseEntity warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getCompanyId, companyId)
                .eq(WarehouseEntity::getAccountBookId, bookId)
                .eq(WarehouseEntity::getDeletedFlag, 0)
                .eq(WarehouseEntity::getStatus, "ACTIVE")
                .orderByAsc(WarehouseEntity::getId)
                .last("limit 1"));
        return warehouse == null ? null : warehouse.getId();
    }

}

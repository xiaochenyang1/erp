package com.tuowei.erp.inventory.mrp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final JdbcTemplate jdbcTemplate;
    private final AuditMetadataFactory auditMetadataFactory;
    private final MrpRunMapper mrpRunMapper;
    private final MrpRunLineMapper mrpRunLineMapper;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final PurchaseOrderService purchaseOrderService;
    private final ProductionOrderService productionOrderService;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;

    public MrpPlanService(
            JdbcTemplate jdbcTemplate,
            AuditMetadataFactory auditMetadataFactory,
            MrpRunMapper mrpRunMapper,
            MrpRunLineMapper mrpRunLineMapper,
            SequenceNumberGenerator sequenceNumberGenerator,
            PurchaseOrderService purchaseOrderService,
            ProductionOrderService productionOrderService,
            ProductMapper productMapper,
            SupplierMapper supplierMapper,
            WarehouseMapper warehouseMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditMetadataFactory = auditMetadataFactory;
        this.mrpRunMapper = mrpRunMapper;
        this.mrpRunLineMapper = mrpRunLineMapper;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.purchaseOrderService = purchaseOrderService;
        this.productionOrderService = productionOrderService;
        this.productMapper = productMapper;
        this.supplierMapper = supplierMapper;
        this.warehouseMapper = warehouseMapper;
    }

    @Transactional
    public MrpRunResponse run() {
        AuditMetadata audit = auditMetadataFactory.current();
        Long companyId = audit.companyId();
        Long bookId = audit.accountBookId();
        LocalDateTime now = audit.now();
        LocalDate asOf = now.toLocalDate();

        Map<Long, BigDecimal> demand = new HashMap<>();
        Map<Long, String> reasons = new HashMap<>();

        List<Map<String, Object>> salesDemand = jdbcTemplate.queryForList("""
                select l.product_id as productId,
                       coalesce(sum(greatest(l.qty - coalesce(l.delivered_qty, 0), 0)), 0) as qty
                from sal_order o
                join sal_order_line l
                  on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                where o.company_id = ? and o.account_book_id = ?
                  and o.deleted_flag = 0
                  and o.status = 'APPROVED'
                  and o.delivery_status in ('NOT_DELIVERED', 'PARTIAL')
                group by l.product_id
                having qty > 0
                """, companyId, bookId);
        for (Map<String, Object> row : salesDemand) {
            Long pid = toLong(row.get("productId"));
            BigDecimal qty = toBd(row.get("qty"));
            add(demand, pid, qty);
            reasons.merge(pid, "销售未发货", (a, b) -> a.contains(b) ? a : a + ";" + b);
        }

        try {
            List<Map<String, Object>> safety = jdbcTemplate.queryForList("""
                    select r.product_id as productId,
                           coalesce(max(r.min_qty), 0) as minQty,
                           coalesce((
                             select sum(b.qty_on_hand) from inv_balance b
                             where b.company_id = r.company_id and b.account_book_id = r.account_book_id
                               and b.product_id = r.product_id
                           ), 0) as onHand
                    from inv_alert_rule r
                    where r.company_id = ? and r.account_book_id = ?
                      and r.deleted_flag = 0 and r.status = 'ACTIVE'
                    group by r.product_id
                    """, companyId, bookId);
            for (Map<String, Object> row : safety) {
                Long pid = toLong(row.get("productId"));
                BigDecimal gap = toBd(row.get("minQty")).subtract(toBd(row.get("onHand")));
                if (gap.compareTo(BigDecimal.ZERO) > 0) {
                    add(demand, pid, gap);
                    reasons.merge(pid, "安全库存", (a, b) -> a.contains(b) ? a : a + ";" + b);
                }
            }
        } catch (Exception ignored) {
        }

        Map<Long, BigDecimal> onHand = loadOnHand(companyId, bookId);
        Map<Long, BigDecimal> openPo = loadOpenPurchase(companyId, bookId);
        Map<Long, BigDecimal> openMo = loadOpenProduction(companyId, bookId);
        Map<Long, Long> productBom = loadActiveBom(companyId, bookId);
        Map<Long, List<BomComponent>> bomComponents = loadBomComponents(companyId, bookId, productBom);

        Map<Long, BigDecimal> materialDemand = new HashMap<>();
        List<ComputedLine> productionLines = new ArrayList<>();
        List<ComputedLine> purchaseLines = new ArrayList<>();

        for (Long productId : new HashSet<>(demand.keySet())) {
            BigDecimal d = demand.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal oh = onHand.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal supply = openPo.getOrDefault(productId, BigDecimal.ZERO)
                    .add(openMo.getOrDefault(productId, BigDecimal.ZERO));
            BigDecimal net = ScalePrecision.quantity(d.subtract(oh).subtract(supply));
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Long bomId = productBom.get(productId);
            String reason = reasons.getOrDefault(productId, "需求");
            if (bomId != null) {
                productionLines.add(new ComputedLine(productId, TYPE_PRODUCTION, d, oh, supply, net, bomId, reason + ";有BOM建议生产"));
                List<BomComponent> comps = bomComponents.getOrDefault(bomId, List.of());
                for (BomComponent c : comps) {
                    BigDecimal need = ScalePrecision.quantity(net.multiply(c.qtyPer()));
                    add(materialDemand, c.materialProductId(), need);
                    reasons.merge(c.materialProductId(), "BOM展开:" + productId, (a, b) -> a.contains(b) ? a : a + ";" + b);
                }
            } else {
                purchaseLines.add(new ComputedLine(productId, TYPE_PURCHASE, d, oh, supply, net, null, reason + ";无BOM建议采购"));
            }
        }

        for (Long materialId : materialDemand.keySet()) {
            BigDecimal d = materialDemand.get(materialId);
            BigDecimal oh = onHand.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal supply = openPo.getOrDefault(materialId, BigDecimal.ZERO)
                    .add(openMo.getOrDefault(materialId, BigDecimal.ZERO));
            BigDecimal net = ScalePrecision.quantity(d.subtract(oh).subtract(supply));
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            purchaseLines.add(new ComputedLine(
                    materialId, TYPE_PURCHASE, d, oh, supply, net, null,
                    reasons.getOrDefault(materialId, "材料需求")
            ));
        }

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
        for (ComputedLine line : productionLines) {
            insertLine(run, line, lineNo++, audit, now);
        }
        for (ComputedLine line : purchaseLines) {
            insertLine(run, line, lineNo++, audit, now);
        }
        return getById(run.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<MrpRunSummaryResponse> listRuns(MrpRunPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        MrpRunPageQuery safe = query == null ? new MrpRunPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());
        LambdaQueryWrapper<MrpRunEntity> wrapper = new LambdaQueryWrapper<MrpRunEntity>()
                .eq(MrpRunEntity::getCompanyId, audit.companyId())
                .eq(MrpRunEntity::getAccountBookId, audit.accountBookId())
                .eq(MrpRunEntity::getDeletedFlag, 0)
                .orderByDesc(MrpRunEntity::getCreatedTime)
                .orderByDesc(MrpRunEntity::getId);
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(MrpRunEntity::getStatus, safe.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        Page<MrpRunEntity> page = mrpRunMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public MrpRunResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        MrpRunEntity run = requireRun(id, audit);
        List<MrpRunLineEntity> lines = mrpRunLineMapper.selectList(new LambdaQueryWrapper<MrpRunLineEntity>()
                .eq(MrpRunLineEntity::getCompanyId, audit.companyId())
                .eq(MrpRunLineEntity::getAccountBookId, audit.accountBookId())
                .eq(MrpRunLineEntity::getRunId, run.getId())
                .eq(MrpRunLineEntity::getDeletedFlag, 0)
                .orderByAsc(MrpRunLineEntity::getLineNo)
                .orderByAsc(MrpRunLineEntity::getId));
        Map<Long, String[]> names = loadProductNames(audit.companyId(), audit.accountBookId());
        List<MrpSuggestionLineResponse> purchase = new ArrayList<>();
        List<MrpSuggestionLineResponse> production = new ArrayList<>();
        for (MrpRunLineEntity line : lines) {
            MrpSuggestionLineResponse response = toLineResponse(line, names);
            if (TYPE_PRODUCTION.equals(line.getSuggestionType())) {
                production.add(response);
            } else {
                purchase.add(response);
            }
        }
        return new MrpRunResponse(
                run.getId(),
                run.getRunNo(),
                run.getAsOfDate() == null ? null : run.getAsOfDate().toString(),
                run.getStatus(),
                run.getPurchaseCount() == null ? purchase.size() : run.getPurchaseCount(),
                run.getProductionCount() == null ? production.size() : run.getProductionCount(),
                run.getCreatedTime(),
                purchase,
                production
        );
    }

    @Transactional
    public MrpSuggestionLineResponse convertLine(Long runId, Long lineId, MrpConvertLineRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        MrpRunEntity run = requireRun(runId, audit);
        if (!STATUS_OPEN.equals(run.getStatus())) {
            throw new IllegalArgumentException("当前MRP计划状态不允许转单");
        }
        MrpRunLineEntity line = requireLine(runId, lineId, audit);
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
        Map<Long, String[]> names = loadProductNames(audit.companyId(), audit.accountBookId());
        return toLineResponse(line, names);
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

    private void insertLine(MrpRunEntity run, ComputedLine computed, int lineNo, AuditMetadata audit, LocalDateTime now) {
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

    private MrpRunEntity requireRun(Long id, AuditMetadata audit) {
        MrpRunEntity run = mrpRunMapper.selectById(id);
        if (run == null
                || run.getDeletedFlag() == null
                || run.getDeletedFlag() != 0
                || !Objects.equals(run.getCompanyId(), audit.companyId())
                || !Objects.equals(run.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("MRP计划不存在");
        }
        return run;
    }

    private MrpRunLineEntity requireLine(Long runId, Long lineId, AuditMetadata audit) {
        MrpRunLineEntity line = mrpRunLineMapper.selectById(lineId);
        if (line == null
                || line.getDeletedFlag() == null
                || line.getDeletedFlag() != 0
                || !Objects.equals(line.getCompanyId(), audit.companyId())
                || !Objects.equals(line.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(line.getRunId(), runId)) {
            throw new IllegalArgumentException("MRP建议行不存在");
        }
        return line;
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

    private MrpRunSummaryResponse toSummary(MrpRunEntity run) {
        return new MrpRunSummaryResponse(
                run.getId(),
                run.getRunNo(),
                run.getAsOfDate() == null ? null : run.getAsOfDate().toString(),
                run.getStatus(),
                run.getPurchaseCount() == null ? 0 : run.getPurchaseCount(),
                run.getProductionCount() == null ? 0 : run.getProductionCount(),
                run.getCreatedTime()
        );
    }

    private MrpSuggestionLineResponse toLineResponse(MrpRunLineEntity line, Map<Long, String[]> names) {
        String[] n = names.get(line.getProductId());
        return new MrpSuggestionLineResponse(
                line.getId(),
                line.getRunId(),
                line.getLineNo(),
                line.getProductId(),
                n == null ? null : n[0],
                n == null ? null : n[1],
                line.getSuggestionType(),
                ScalePrecision.quantity(line.getDemandQty()),
                ScalePrecision.quantity(line.getOnHandQty()),
                ScalePrecision.quantity(line.getOpenSupplyQty()),
                ScalePrecision.quantity(line.getNetQty()),
                line.getBomId(),
                line.getReason(),
                line.getStatus(),
                line.getConvertedBizType(),
                line.getConvertedBizId(),
                line.getConvertedBizNo(),
                line.getConvertedTime()
        );
    }

    private Map<Long, BigDecimal> loadOnHand(Long companyId, Long bookId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select product_id as productId, coalesce(sum(qty_on_hand),0) as qty
                from inv_balance
                where company_id = ? and account_book_id = ?
                group by product_id
                """, companyId, bookId)) {
            map.put(toLong(row.get("productId")), toBd(row.get("qty")));
        }
        return map;
    }

    private Map<Long, BigDecimal> loadOpenPurchase(Long companyId, Long bookId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        try {
            for (Map<String, Object> row : jdbcTemplate.queryForList("""
                    select l.product_id as productId,
                           coalesce(sum(greatest(l.qty - coalesce(l.received_qty, 0), 0)), 0) as qty
                    from pur_order o
                    join pur_order_line l
                      on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                    where o.company_id = ? and o.account_book_id = ?
                      and o.deleted_flag = 0
                      and o.status in ('APPROVED', 'PARTIAL', 'SUBMITTED')
                    group by l.product_id
                    """, companyId, bookId)) {
                map.put(toLong(row.get("productId")), toBd(row.get("qty")));
            }
        } catch (Exception ex) {
            // received_qty 可能不存在：退化为未收完 = 全量 qty for open orders
            for (Map<String, Object> row : jdbcTemplate.queryForList("""
                    select l.product_id as productId, coalesce(sum(l.qty), 0) as qty
                    from pur_order o
                    join pur_order_line l
                      on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                    where o.company_id = ? and o.account_book_id = ?
                      and o.deleted_flag = 0
                      and o.status in ('APPROVED', 'SUBMITTED')
                    group by l.product_id
                    """, companyId, bookId)) {
                map.put(toLong(row.get("productId")), toBd(row.get("qty")));
            }
        }
        return map;
    }

    private Map<Long, BigDecimal> loadOpenProduction(Long companyId, Long bookId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select product_id as productId,
                       coalesce(sum(greatest(planned_qty - coalesce(completed_qty, 0), 0)), 0) as qty
                from prd_order
                where company_id = ? and account_book_id = ?
                  and deleted_flag = 0
                  and status in ('RELEASED', 'MATERIAL_ISSUED')
                group by product_id
                """, companyId, bookId)) {
            map.put(toLong(row.get("productId")), toBd(row.get("qty")));
        }
        return map;
    }

    private Map<Long, Long> loadActiveBom(Long companyId, Long bookId) {
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select product_id as productId, id as bomId
                from prd_bom
                where company_id = ? and account_book_id = ?
                  and deleted_flag = 0 and status = 'ACTIVE'
                """, companyId, bookId)) {
            map.put(toLong(row.get("productId")), toLong(row.get("bomId")));
        }
        return map;
    }

    private Map<Long, List<BomComponent>> loadBomComponents(Long companyId, Long bookId, Map<Long, Long> productBom) {
        Map<Long, List<BomComponent>> map = new HashMap<>();
        if (productBom.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select bom_id as bomId, material_product_id as materialId, qty_per as qtyPer
                from prd_bom_line
                where company_id = ? and account_book_id = ?
                """, companyId, bookId)) {
            Long bomId = toLong(row.get("bomId"));
            map.computeIfAbsent(bomId, k -> new ArrayList<>())
                    .add(new BomComponent(toLong(row.get("materialId")), toBd(row.get("qtyPer"))));
        }
        return map;
    }

    private Map<Long, String[]> loadProductNames(Long companyId, Long bookId) {
        Map<Long, String[]> map = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                select id, product_code, product_name from md_product
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                """, companyId, bookId)) {
            map.put(toLong(row.get("id")), new String[]{
                    row.get("product_code") == null ? null : row.get("product_code").toString(),
                    row.get("product_name") == null ? null : row.get("product_name").toString()
            });
        }
        return map;
    }

    private void add(Map<Long, BigDecimal> map, Long key, BigDecimal qty) {
        if (key == null || qty == null) return;
        map.merge(key, qty, BigDecimal::add);
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return ((Number) v).longValue();
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private record BomComponent(Long materialProductId, BigDecimal qtyPer) {
    }

    private record ComputedLine(
            Long productId,
            String suggestionType,
            BigDecimal demandQty,
            BigDecimal onHandQty,
            BigDecimal openSupplyQty,
            BigDecimal netQty,
            Long bomId,
            String reason
    ) {
    }
}

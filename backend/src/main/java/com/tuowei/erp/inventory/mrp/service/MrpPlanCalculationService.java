package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot.*;
import com.tuowei.erp.inventory.mrp.service.MrpPlanEngine.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Loads tenant-scoped facts; all netting and BOM traversal live in the pure calculation engine. */
@Service
@NativeSqlTenantScoped("MRP inputs and planning lock scoped by company and account book")
public class MrpPlanCalculationService {
    private final JdbcTemplate jdbcTemplate;

    public MrpPlanCalculationService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    /** The existing per-book MRP numbering rule serializes planning/conversion across application instances. */
    @Transactional
    public void lockPlanning(AuditMetadata audit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id from sys_sequence_rule
                where company_id = ? and account_book_id = ? and biz_type = 'MRP_RUN'
                for update
                """, audit.companyId(), audit.accountBookId());
        if (rows.isEmpty()) throw new IllegalArgumentException("MRP计划编号规则不存在");
    }

    @Transactional(readOnly = true)
    public CalculationResult calculate(AuditMetadata audit) {
        Map<Key, List<Source>> demands = new TreeMap<>();
        Map<Key, List<Supply>> supplies = new TreeMap<>();
        List<Warning> warnings = new ArrayList<>();
        for (Map<String, Object> row : query("""
                select o.id as documentId, o.order_no as documentNo, l.id as lineId,
                       o.warehouse_id as warehouseId, l.product_id as productId,
                       greatest(l.qty - coalesce(l.delivered_qty, 0), 0) as qty
                from sal_order o join sal_order_line l
                  on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                where o.company_id = ? and o.account_book_id = ? and o.deleted_flag = 0
                  and o.status = 'APPROVED' and o.delivery_status in ('NOT_DELIVERED', 'PARTIAL')
                order by o.id, l.id
                """, audit)) {
            demand(demands, row, "SALES_ORDER");
        }
        for (Map<String, Object> row : query("""
                select id as documentId, warehouse_id as warehouseId, product_id as productId, min_qty as qty
                from inv_alert_rule
                where company_id = ? and account_book_id = ? and deleted_flag = 0 and enabled = 1
                order by warehouse_id, product_id, id
                """, audit)) {
            // Desired closing balance per warehouse/product; legacy duplicate rules use the largest target.
            Key key = key(row);
            BigDecimal qty = bd(row, "qty");
            if (qty.signum() <= 0) continue;
            List<Source> list = demands.computeIfAbsent(key, ignored -> new ArrayList<>());
            Source previous = list.stream().filter(s -> "SAFETY_STOCK".equals(s.type())).findFirst().orElse(null);
            if (previous == null || qty.compareTo(previous.qty()) > 0) {
                list.remove(previous);
                list.add(Source.direct("SAFETY_STOCK", id(row, "documentId"), null, null, qty));
            }
        }
        for (Map<String, Object> row : query("""
                select warehouse_id as warehouseId, product_id as productId, sum(qty_on_hand) as qty
                from inv_balance where company_id = ? and account_book_id = ?
                group by warehouse_id, product_id order by warehouse_id, product_id
                """, audit)) {
            // Open sales/material requirements already include reservations: do not subtract them twice.
            supplies.computeIfAbsent(key(row), ignored -> new ArrayList<>())
                    .add(new Supply("ON_HAND", null, null, null, bd(row, "qty")));
        }
        for (Map<String, Object> row : query("""
                select o.id as documentId, o.order_no as documentNo, l.id as lineId,
                       o.planned_warehouse_id as warehouseId, l.product_id as productId, o.status,
                       greatest(l.qty - coalesce(l.received_qty, 0), 0) as qty
                from pur_order o join pur_order_line l
                  on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                where o.company_id = ? and o.account_book_id = ? and o.deleted_flag = 0
                  and (o.status in ('APPROVED', 'PARTIAL', 'SUBMITTED')
                    or (o.status = 'DRAFT' and exists (
                      select 1 from inv_mrp_run_line m where m.company_id = o.company_id
                        and m.account_book_id = o.account_book_id and m.deleted_flag = 0
                        and m.status = 'CONVERTED' and m.converted_biz_type = 'PURCHASE_ORDER' and m.converted_biz_id = o.id)))
                order by o.id, l.id
                """, audit)) {
            BigDecimal qty = bd(row, "qty");
            if (qty.signum() <= 0) continue;
            if (id(row, "warehouseId") == null) {
                warnings.add(new Warning("PURCHASE_WAREHOUSE_REQUIRED", id(row, "documentId"), str(row, "documentNo"),
                        id(row, "productId"), qty, "采购订单未设置预计收货仓库，请补充后重新运行MRP"));
            } else {
                supply(supplies, row, "DRAFT".equals(str(row, "status")) ? "PLANNED_PURCHASE" : "PURCHASE_ORDER");
            }
        }
        // EXISTS counts a document once even when multiple historical MRP links reference it.
        for (Map<String, Object> row : query("""
                select o.id as documentId, o.order_no as documentNo, o.finished_warehouse_id as warehouseId,
                       o.product_id as productId, o.status,
                       greatest(o.planned_qty - coalesce(o.completed_qty, 0), 0) as qty
                from prd_order o
                where o.company_id = ? and o.account_book_id = ? and o.deleted_flag = 0
                  and (o.status in ('RELEASED', 'MATERIAL_ISSUED')
                    or (o.status = 'DRAFT' and exists (
                      select 1 from inv_mrp_run_line m where m.company_id = o.company_id
                        and m.account_book_id = o.account_book_id and m.deleted_flag = 0
                        and m.status = 'CONVERTED' and m.converted_biz_type = 'PRODUCTION_ORDER' and m.converted_biz_id = o.id)))
                order by o.id
                """, audit)) {
            supply(supplies, row, "DRAFT".equals(str(row, "status")) ? "PLANNED_PRODUCTION" : "PRODUCTION_ORDER");
        }
        for (Map<String, Object> row : query("""
                select o.id as documentId, o.order_no as documentNo, l.id as lineId,
                       o.material_warehouse_id as warehouseId, l.material_product_id as productId,
                       greatest(l.required_qty - coalesce(l.issued_qty, 0), 0) as qty
                from prd_order o join prd_order_material l
                  on l.company_id = o.company_id and l.account_book_id = o.account_book_id and l.order_id = o.id
                where o.company_id = ? and o.account_book_id = ? and o.deleted_flag = 0
                  and (o.status in ('RELEASED', 'MATERIAL_ISSUED')
                    or (o.status = 'DRAFT' and exists (
                      select 1 from inv_mrp_run_line m where m.company_id = o.company_id
                        and m.account_book_id = o.account_book_id and m.deleted_flag = 0
                        and m.status = 'CONVERTED' and m.converted_biz_type = 'PRODUCTION_ORDER' and m.converted_biz_id = o.id)))
                order by o.id, l.id
                """, audit)) {
            demand(demands, row, "PRODUCTION_MATERIAL");
        }
        Map<Long, Product> products = new HashMap<>();
        for (Map<String, Object> row : query("""
                select id, product_code as code, product_name as name, mrp_supply_type as supplyType,
                       mrp_material_warehouse_id as materialWarehouseId
                from md_product where company_id = ? and account_book_id = ? and deleted_flag = 0 and status = 'ACTIVE'
                """, audit)) {
            products.put(id(row, "id"), new Product(id(row, "id"), str(row, "code"), str(row, "name"),
                    str(row, "supplyType"), id(row, "materialWarehouseId")));
        }
        Map<Long, String> warehouses = new HashMap<>();
        for (Map<String, Object> row : query("""
                select id, warehouse_name as name from md_warehouse
                where company_id = ? and account_book_id = ? and deleted_flag = 0 and status = 'ACTIVE'
                """, audit)) {
            warehouses.put(id(row, "id"), str(row, "name"));
        }
        Map<Long, List<Material>> materials = new HashMap<>();
        for (Map<String, Object> row : query("""
                select b.id as bomId, l.material_product_id as productId, l.qty_per as qtyPer,
                       b.base_qty as baseQty, l.loss_rate as lossRate
                from prd_bom b join prd_bom_line l on b.id = l.bom_id
                  and b.company_id = l.company_id and b.account_book_id = l.account_book_id
                where b.company_id = ? and b.account_book_id = ? and b.deleted_flag = 0 and b.status = 'ACTIVE'
                order by b.id, l.line_no, l.id
                """, audit)) {
            materials.computeIfAbsent(id(row, "bomId"), ignored -> new ArrayList<>())
                    .add(new Material(id(row, "productId"), bd(row, "qtyPer"), bd(row, "baseQty"), bd(row, "lossRate")));
        }
        Map<Long, List<Bom>> boms = new HashMap<>();
        for (Map<String, Object> row : query("""
                select id, bom_no as bomNo, product_id as productId, version from prd_bom
                where company_id = ? and account_book_id = ? and deleted_flag = 0 and status = 'ACTIVE'
                order by id
                """, audit)) {
            boms.computeIfAbsent(id(row, "productId"), ignored -> new ArrayList<>()).add(new Bom(id(row, "id"),
                    str(row, "bomNo"), id(row, "productId"), bd(row, "version").intValue(),
                    materials.getOrDefault(id(row, "id"), List.of())));
        }
        MrpCalculationSnapshot snapshot = new MrpPlanEngine().calculate(new Input(products, warehouses, boms, demands, supplies, warnings));
        List<ComputedLine> production = new ArrayList<>();
        List<ComputedLine> purchase = new ArrayList<>();
        for (Node node : snapshot.nodes()) {
            if (node.netQty().signum() <= 0) continue;
            ComputedLine line = new ComputedLine(node.productId(), node.supplyType(), node.demandQty(), node.onHandQty(),
                    node.openSupplyQty(), node.netQty(), node.bomId(), "按仓汇总需求－现存量－在途/在制－已转计划供给", node);
            ("PRODUCTION".equals(node.supplyType()) ? production : purchase).add(line);
        }
        return new CalculationResult(List.copyOf(production), List.copyOf(purchase), snapshot);
    }

    private List<Map<String, Object>> query(String sql, AuditMetadata audit) {
        return jdbcTemplate.queryForList(sql, audit.companyId(), audit.accountBookId());
    }
    private Key key(Map<String, Object> row) { return new Key(id(row, "productId"), id(row, "warehouseId")); }
    private void demand(Map<Key, List<Source>> demands, Map<String, Object> row, String type) {
        BigDecimal qty = bd(row, "qty");
        if (qty.signum() > 0) demands.computeIfAbsent(key(row), ignored -> new ArrayList<>()).add(
                Source.direct(type, id(row, "documentId"), str(row, "documentNo"), id(row, "lineId"), qty));
    }
    private void supply(Map<Key, List<Supply>> supplies, Map<String, Object> row, String type) {
        BigDecimal qty = bd(row, "qty");
        if (qty.signum() > 0) supplies.computeIfAbsent(key(row), ignored -> new ArrayList<>()).add(
                new Supply(type, id(row, "documentId"), str(row, "documentNo"), id(row, "lineId"), qty));
    }
    private Long id(Map<String, Object> row, String key) { return row.get(key) == null ? null : ((Number) row.get(key)).longValue(); }
    private String str(Map<String, Object> row, String key) { return row.get(key) == null ? null : row.get(key).toString(); }
    private BigDecimal bd(Map<String, Object> row, String key) {
        return row.get(key) == null ? BigDecimal.ZERO : new BigDecimal(row.get(key).toString());
    }

    public record CalculationResult(List<ComputedLine> productionLines, List<ComputedLine> purchaseLines, MrpCalculationSnapshot snapshot) { }
    public record ComputedLine(Long productId, String suggestionType, BigDecimal demandQty, BigDecimal onHandQty,
                               BigDecimal openSupplyQty, BigDecimal netQty, Long bomId, String reason, Node trace) { }
}

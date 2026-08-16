package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * MRP 运行的只读计算协作者。
 *
 * <p>这里负责收集需求、抵扣现有供应并展开 BOM；它不生成单号、不持久化运行结果，
 * 也不参与采购订单或生产订单转换。这样运行门面可以继续保持单一写事务，计算规则则能
 * 在没有数据库写入的情况下独立验证。</p>
 */
@Service
@NativeSqlTenantScoped("MRP calculation scoped by current tenant")
public class MrpPlanCalculationService {

    private static final String TYPE_PURCHASE = "PURCHASE";
    private static final String TYPE_PRODUCTION = "PRODUCTION";

    private final JdbcTemplate jdbcTemplate;

    public MrpPlanCalculationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public CalculationResult calculate(AuditMetadata audit) {
        Long companyId = audit.companyId();
        Long bookId = audit.accountBookId();

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
            // 预警表在旧租户中可能尚未升级，MRP 仍可按销售需求继续计算。
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
                productionLines.add(new ComputedLine(
                        productId,
                        TYPE_PRODUCTION,
                        d,
                        oh,
                        supply,
                        net,
                        bomId,
                        reason + ";有BOM建议生产"
                ));
                for (BomComponent component : bomComponents.getOrDefault(bomId, List.of())) {
                    BigDecimal need = ScalePrecision.quantity(net.multiply(component.qtyPer()));
                    add(materialDemand, component.materialProductId(), need);
                    reasons.merge(
                            component.materialProductId(),
                            "BOM展开:" + productId,
                            (a, b) -> a.contains(b) ? a : a + ";" + b
                    );
                }
            } else {
                purchaseLines.add(new ComputedLine(
                        productId,
                        TYPE_PURCHASE,
                        d,
                        oh,
                        supply,
                        net,
                        null,
                        reason + ";无BOM建议采购"
                ));
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
                    materialId,
                    TYPE_PURCHASE,
                    d,
                    oh,
                    supply,
                    net,
                    null,
                    reasons.getOrDefault(materialId, "材料需求")
            ));
        }

        return new CalculationResult(productionLines, purchaseLines);
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

    private Map<Long, List<BomComponent>> loadBomComponents(
            Long companyId,
            Long bookId,
            Map<Long, Long> productBom
    ) {
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
            map.computeIfAbsent(bomId, key -> new ArrayList<>())
                    .add(new BomComponent(toLong(row.get("materialId")), toBd(row.get("qtyPer"))));
        }
        return map;
    }

    private void add(Map<Long, BigDecimal> map, Long key, BigDecimal qty) {
        if (key == null || qty == null) {
            return;
        }
        map.merge(key, qty, BigDecimal::add);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private BigDecimal toBd(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    public record CalculationResult(
            List<ComputedLine> productionLines,
            List<ComputedLine> purchaseLines
    ) {
    }

    public record ComputedLine(
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

    private record BomComponent(Long materialProductId, BigDecimal qtyPer) {
    }
}

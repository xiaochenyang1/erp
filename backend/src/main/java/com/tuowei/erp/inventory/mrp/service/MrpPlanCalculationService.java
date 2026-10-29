package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
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

        List<Map<String, Object>> safety = jdbcTemplate.queryForList("""
                select r.product_id as productId,
                       coalesce(max(r.min_qty), 0) as minQty
                from inv_alert_rule r
                where r.company_id = ? and r.account_book_id = ?
                  and r.deleted_flag = 0 and r.enabled = 1
                group by r.product_id
                """, companyId, bookId);
        for (Map<String, Object> row : safety) {
            Long pid = toLong(row.get("productId"));
            // Safety stock is the desired closing balance, not an already-netted shortage.
            BigDecimal target = toBd(row.get("minQty"));
            if (target.compareTo(BigDecimal.ZERO) > 0) {
                add(demand, pid, target);
                reasons.merge(pid, "安全库存", (a, b) -> a.contains(b) ? a : a + ";" + b);
            }
        }

        Map<Long, BigDecimal> onHand = loadOnHand(companyId, bookId);
        Map<Long, BigDecimal> openPo = loadOpenPurchase(companyId, bookId);
        Map<Long, BigDecimal> openMo = loadOpenProduction(companyId, bookId);
        Map<Long, Long> productBom = loadActiveBom(companyId, bookId);
        Map<Long, List<BomComponent>> bomComponents = loadBomComponents(companyId, bookId, productBom);

        Map<Long, BigDecimal> remainingOnHand = new HashMap<>(onHand);
        Map<Long, BigDecimal> remainingSupply = new HashMap<>(openPo);
        openMo.forEach((productId, qty) -> add(remainingSupply, productId, qty));
        Map<Long, BigDecimal> purchaseDemand = new HashMap<>();
        List<ComputedLine> productionLines = new ArrayList<>();
        List<ComputedLine> purchaseLines = new ArrayList<>();

        // Only direct demand for products with a BOM is manufactured in MRP-lite.
        // Aggregate purchased goods and all expanded component demand before netting them.
        for (Long productId : new TreeSet<>(demand.keySet())) {
            BigDecimal d = demand.get(productId);
            Long bomId = productBom.get(productId);
            if (bomId == null) {
                add(purchaseDemand, productId, d);
                reasons.merge(productId, "无BOM建议采购", (a, b) -> a + ";" + b);
                continue;
            }
            BigDecimal oh = remainingOnHand.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal supply = remainingSupply.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal net = ScalePrecision.quantity(d.subtract(oh).subtract(supply));

            // A manufactured product may also be another product's component. Reserve the
            // supply consumed by direct demand even when no production suggestion is needed.
            BigDecimal usedStock = d.min(oh);
            remainingOnHand.put(productId, oh.subtract(usedStock));
            BigDecimal usedSupply = d.subtract(usedStock).min(supply);
            remainingSupply.put(productId, supply.subtract(usedSupply));
            if (net.signum() <= 0) {
                continue;
            }
            productionLines.add(new ComputedLine(productId, TYPE_PRODUCTION, d, oh, supply, net,
                    bomId, reasons.getOrDefault(productId, "需求") + ";有BOM建议生产"));
            for (BomComponent component : bomComponents.getOrDefault(bomId, List.of())) {
                BigDecimal need = ScalePrecision.quantity(component.qtyPer().multiply(net)
                        .divide(ScalePrecision.quantity(component.baseQty()), 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.ONE.add(component.lossRate())));
                add(purchaseDemand, component.materialProductId(), need);
                String reason = "BOM展开:" + productId;
                reasons.merge(component.materialProductId(), reason, (a, b) -> a + ";" + b);
            }
        }

        for (Long productId : new TreeSet<>(purchaseDemand.keySet())) {
            BigDecimal d = purchaseDemand.get(productId);
            BigDecimal oh = remainingOnHand.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal supply = remainingSupply.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal net = ScalePrecision.quantity(d.subtract(oh).subtract(supply));
            if (net.signum() > 0) {
                purchaseLines.add(new ComputedLine(productId, TYPE_PURCHASE, d, oh, supply, net,
                        null, reasons.getOrDefault(productId, "材料需求")));
            }
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
                select l.bom_id as bomId, l.material_product_id as materialId, l.qty_per as qtyPer,
                       b.base_qty as baseQty, l.loss_rate as lossRate
                from prd_bom_line l
                join prd_bom b on b.id = l.bom_id
                  and b.company_id = l.company_id and b.account_book_id = l.account_book_id
                where l.company_id = ? and l.account_book_id = ?
                """, companyId, bookId)) {
            Long bomId = toLong(row.get("bomId"));
            map.computeIfAbsent(bomId, key -> new ArrayList<>())
                    .add(new BomComponent(toLong(row.get("materialId")), toBd(row.get("qtyPer")),
                            toBd(row.get("baseQty")), toBd(row.get("lossRate"))));
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

    private record BomComponent(Long materialProductId, BigDecimal qtyPer, BigDecimal baseQty, BigDecimal lossRate) {
    }
}

package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.mrp.web.MrpRunResponse;
import com.tuowei.erp.inventory.mrp.web.MrpSuggestionLineResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量 MRP：
 * 独立需求 = 已审批未发完销售订单缺口 + 安全库存（预警规则 min_qty 若有）
 * 供应 = 现存量 + 在途采购(已审批未完成) + 在制生产(已下达/领料未完工)
 * 有启用 BOM 的成品建议生产，否则建议采购；BOM 材料展开叠加到材料需求。
 */
@Service
@NativeSqlTenantScoped("MRP run scoped by current tenant")
public class MrpPlanService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditMetadataFactory auditMetadataFactory;

    public MrpPlanService(JdbcTemplate jdbcTemplate, AuditMetadataFactory auditMetadataFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public MrpRunResponse run() {
        AuditMetadata audit = auditMetadataFactory.current();
        Long companyId = audit.companyId();
        Long bookId = audit.accountBookId();

        Map<Long, BigDecimal> demand = new HashMap<>();
        Map<Long, String> reasons = new HashMap<>();

        // 销售独立需求：审批通过且未完全发货的订单行剩余数量
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

        // 安全库存：active 预警规则 min_qty - onhand 的正缺口（若规则表存在）
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
            // 无预警表/字段时跳过安全库存
        }

        Map<Long, BigDecimal> onHand = loadOnHand(companyId, bookId);
        Map<Long, BigDecimal> openPo = loadOpenPurchase(companyId, bookId);
        Map<Long, BigDecimal> openMo = loadOpenProduction(companyId, bookId);
        Map<Long, Long> productBom = loadActiveBom(companyId, bookId);
        Map<Long, List<BomComponent>> bomComponents = loadBomComponents(companyId, bookId, productBom);

        // 成品净需求 -> 生产建议；并展开材料需求
        Map<Long, BigDecimal> materialDemand = new HashMap<>();
        List<MrpSuggestionLineResponse> productionLines = new ArrayList<>();
        List<MrpSuggestionLineResponse> purchaseLines = new ArrayList<>();

        Set<Long> productIds = new HashSet<>(demand.keySet());
        productIds.addAll(onHand.keySet());

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
                productionLines.add(line(productId, "PRODUCTION", d, oh, supply, net, bomId, reason + ";有BOM建议生产"));
                List<BomComponent> comps = bomComponents.getOrDefault(bomId, List.of());
                for (BomComponent c : comps) {
                    BigDecimal need = ScalePrecision.quantity(net.multiply(c.qtyPer()));
                    add(materialDemand, c.materialProductId(), need);
                    reasons.merge(c.materialProductId(), "BOM展开:" + productId, (a, b) -> a.contains(b) ? a : a + ";" + b);
                }
            } else {
                purchaseLines.add(line(productId, "PURCHASE", d, oh, supply, net, null, reason + ";无BOM建议采购"));
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
            // 材料若也有BOM，仍建议采购（避免无限递归），除非再一轮 - 最小口径直接采购
            purchaseLines.add(line(
                    materialId, "PURCHASE", d, oh, supply, net, null,
                    reasons.getOrDefault(materialId, "材料需求")
            ));
        }

        Map<Long, String[]> names = loadProductNames(companyId, bookId);
        productionLines = enrich(productionLines, names);
        purchaseLines = enrich(purchaseLines, names);

        return new MrpRunResponse(
                LocalDate.now().toString(),
                purchaseLines.size(),
                productionLines.size(),
                purchaseLines,
                productionLines
        );
    }

    private List<MrpSuggestionLineResponse> enrich(List<MrpSuggestionLineResponse> lines, Map<Long, String[]> names) {
        List<MrpSuggestionLineResponse> out = new ArrayList<>();
        for (MrpSuggestionLineResponse l : lines) {
            String[] n = names.get(l.productId());
            out.add(new MrpSuggestionLineResponse(
                    l.productId(),
                    n == null ? null : n[0],
                    n == null ? null : n[1],
                    l.suggestionType(),
                    l.demandQty(),
                    l.onHandQty(),
                    l.openSupplyQty(),
                    l.netQty(),
                    l.bomId(),
                    l.reason()
            ));
        }
        return out;
    }

    private MrpSuggestionLineResponse line(
            Long productId, String type, BigDecimal demand, BigDecimal onHand,
            BigDecimal supply, BigDecimal net, Long bomId, String reason
    ) {
        return new MrpSuggestionLineResponse(
                productId, null, null, type,
                ScalePrecision.quantity(demand),
                ScalePrecision.quantity(onHand),
                ScalePrecision.quantity(supply),
                ScalePrecision.quantity(net),
                bomId,
                reason
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
}

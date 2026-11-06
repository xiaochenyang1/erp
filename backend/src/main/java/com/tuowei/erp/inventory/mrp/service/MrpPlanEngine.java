package com.tuowei.erp.inventory.mrp.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure, deterministic, low-level-code MRP: aggregate all parents before netting a warehouse/product once. */
public final class MrpPlanEngine {
    private static final int MAX_NODES = 20_000;
    private static final int MAX_DEPTH = 100;
    private static final BigDecimal ZERO = ScalePrecision.quantity(BigDecimal.ZERO);

    public record Key(Long productId, Long warehouseId) implements Comparable<Key> {
        public Key {
            Objects.requireNonNull(productId, "MRP商品不能为空");
            Objects.requireNonNull(warehouseId, "MRP需求仓库不能为空");
        }
        @Override public int compareTo(Key other) {
            int warehouse = warehouseId.compareTo(other.warehouseId);
            return warehouse != 0 ? warehouse : productId.compareTo(other.productId);
        }
    }
    public record Product(Long id, String code, String name, String supplyType, Long materialWarehouseId) { }
    public record Bom(Long id, String no, Long productId, int version, List<Material> materials) { }
    public record Input(Map<Long, Product> products, Map<Long, String> warehouses, Map<Long, List<Bom>> boms,
                        Map<Key, List<Source>> demands, Map<Key, List<Supply>> supplies, List<Warning> warnings) { }

    public MrpCalculationSnapshot calculate(Input input) {
        Map<Key, List<Key>> edges = new TreeMap<>();
        Map<Key, Bom> selectedBoms = new HashMap<>();
        Map<Key, Integer> marks = new HashMap<>();
        for (Key key : new TreeSet<>(input.demands().keySet())) {
            discover(key, input, edges, selectedBoms, marks, new ArrayList<>());
        }
        Map<Key, Integer> indegree = new HashMap<>();
        Map<Key, Integer> levels = new HashMap<>();
        edges.keySet().forEach(key -> indegree.put(key, 0));
        edges.values().forEach(children -> children.forEach(child -> indegree.merge(child, 1, Integer::sum)));
        PriorityQueue<Key> ready = new PriorityQueue<>();
        indegree.forEach((key, degree) -> { if (degree == 0) ready.add(key); });
        Map<Key, List<Source>> sources = new HashMap<>();
        input.demands().forEach((key, list) -> sources.put(key, new ArrayList<>(list)));
        List<Node> nodes = new ArrayList<>();
        while (!ready.isEmpty()) {
            Key key = ready.remove();
            Product product = input.products().get(key.productId());
            Bom bom = selectedBoms.get(key);
            Long materialWarehouse = bom == null ? null : materialWarehouse(product, key);
            List<Source> nodeSources = sources.getOrDefault(key, List.of());
            BigDecimal demand = quantity(nodeSources.stream().map(Source::qty).reduce(ZERO, BigDecimal::add));
            List<Supply> supply = input.supplies().getOrDefault(key, List.of());
            BigDecimal onHand = totalSupply(supply, "ON_HAND");
            BigDecimal purchase = totalSupply(supply, "PURCHASE_ORDER");
            BigDecimal production = totalSupply(supply, "PRODUCTION_ORDER");
            BigDecimal planned = totalSupply(supply, "PLANNED_PURCHASE").add(totalSupply(supply, "PLANNED_PRODUCTION"));
            BigDecimal net = quantity(demand.subtract(onHand).subtract(purchase).subtract(production).subtract(planned).max(ZERO));
            int level = levels.getOrDefault(key, 0);
            if (level >= MAX_DEPTH) throw new IllegalArgumentException("BOM超过MRP计算层级限制（100层）");
            nodes.add(new Node(key.productId(), product.code(), product.name(), key.warehouseId(),
                    input.warehouses().get(key.warehouseId()), bom == null ? "PURCHASE" : "PRODUCTION",
                    materialWarehouse, materialWarehouse == null ? null : input.warehouses().get(materialWarehouse),
                    bom == null ? null : bom.id(), bom == null ? null : bom.no(), bom == null ? null : bom.version(),
                    level, demand, onHand, purchase, production, planned, net,
                    List.copyOf(nodeSources), List.copyOf(supply), bom == null ? List.of() : List.copyOf(bom.materials())));
            if (bom != null && net.signum() > 0) {
                for (Material material : bom.materials()) {
                    BigDecimal need = quantity(material.qtyPer().multiply(net)
                            .divide(material.baseQty(), 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.ONE.add(material.lossRate())));
                    if (need.signum() > 0) {
                        Key child = new Key(material.productId(), materialWarehouse);
                        sources.computeIfAbsent(child, ignored -> new ArrayList<>()).add(new Source(
                                "BOM", bom.id(), bom.no(), null, need, key.productId(), key.warehouseId(),
                                bom.id(), net, material.qtyPer(), material.baseQty(), material.lossRate()));
                    }
                }
            }
            for (Key child : edges.get(key)) {
                levels.merge(child, level + 1, Math::max);
                if (indegree.merge(child, -1, Integer::sum) == 0) ready.add(child);
            }
        }
        Set<Long> relevantProducts = new HashSet<>();
        nodes.forEach(node -> relevantProducts.add(node.productId()));
        return new MrpCalculationSnapshot(MrpCalculationSnapshot.CURRENT_VERSION, List.copyOf(nodes),
                input.warnings().stream().filter(w -> relevantProducts.contains(w.productId())).toList());
    }

    private void discover(Key key, Input input, Map<Key, List<Key>> edges, Map<Key, Bom> selected,
                          Map<Key, Integer> marks, List<Key> path) {
        if (marks.getOrDefault(key, 0) == 1) {
            List<Key> cycle = new ArrayList<>(path.subList(path.indexOf(key), path.size()));
            cycle.add(key);
            throw new IllegalArgumentException("检测到循环BOM: " + String.join(" → ", cycle.stream()
                    .map(k -> input.products().get(k.productId()).code() + "@" + input.warehouses().get(k.warehouseId())).toList()));
        }
        if (marks.getOrDefault(key, 0) == 2) return;
        if (path.size() >= MAX_DEPTH || marks.size() >= MAX_NODES) {
            throw new IllegalArgumentException("BOM超过MRP计算规模限制（100层 / 20000个商品仓库组合）");
        }
        Product product = input.products().get(key.productId());
        if (product == null) throw new IllegalArgumentException("MRP商品不存在或未启用: " + key.productId());
        if (!input.warehouses().containsKey(key.warehouseId())) {
            throw new IllegalArgumentException("MRP仓库不存在或未启用: " + key.warehouseId());
        }
        marks.put(key, 1);
        path.add(key);
        List<Bom> boms = input.boms().getOrDefault(key.productId(), List.of());
        String policy = product.supplyType() == null ? "AUTO" : product.supplyType();
        if (!Set.of("AUTO", "PURCHASE", "PRODUCTION").contains(policy)) {
            throw new IllegalArgumentException("MRP供给策略无效: " + product.code());
        }
        List<Key> children = new ArrayList<>();
        if (!"PURCHASE".equals(policy) && (!boms.isEmpty() || "PRODUCTION".equals(policy))) {
            if (boms.size() != 1) {
                throw new IllegalArgumentException(product.code() + (boms.isEmpty()
                        ? " 设置为自制但没有启用BOM" : " 存在多个启用BOM，请保留一个后重算"));
            }
            Bom bom = boms.get(0);
            if (bom.materials().isEmpty()) throw new IllegalArgumentException("BOM没有材料: " + bom.no());
            selected.put(key, bom);
            for (Material material : bom.materials()) {
                if (material.qtyPer() == null || material.qtyPer().signum() <= 0 || material.baseQty() == null
                        || material.baseQty().signum() <= 0 || material.lossRate() == null || material.lossRate().signum() < 0) {
                    throw new IllegalArgumentException("BOM用量、基准数量或损耗率无效: " + bom.no());
                }
                Key child = new Key(material.productId(), materialWarehouse(product, key));
                if (!children.contains(child)) children.add(child);
                discover(child, input, edges, selected, marks, path);
            }
        }
        edges.put(key, List.copyOf(children));
        path.remove(path.size() - 1);
        marks.put(key, 2);
    }

    private Long materialWarehouse(Product product, Key key) {
        return product.materialWarehouseId() == null ? key.warehouseId() : product.materialWarehouseId();
    }

    private BigDecimal totalSupply(List<Supply> supplies, String type) {
        return quantity(supplies.stream().filter(s -> type.equals(s.type())).map(Supply::qty).reduce(ZERO, BigDecimal::add));
    }

    private BigDecimal quantity(BigDecimal value) {
        BigDecimal qty = ScalePrecision.quantity(value);
        if (qty.precision() - qty.scale() > 14) throw new IllegalArgumentException("MRP数量超过支持范围");
        return qty;
    }
}

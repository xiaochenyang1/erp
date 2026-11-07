package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryLotGenealogyQueryService {

    static final int DEFAULT_MAX_DEPTH = 5;
    static final int MIN_MAX_DEPTH = 1;
    static final int HARD_MAX_DEPTH = 10;
    static final int PER_LEVEL_NODE_LIMIT = 200;
    static final int TOTAL_NODE_LIMIT = 500;

    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final InventoryLotGenealogyAssemblyService assemblyService;

    public InventoryLotGenealogyQueryService(
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            InventoryLotGenealogyAssemblyService assemblyService
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.assemblyService = assemblyService;
    }

    @Transactional(readOnly = true)
    public InventoryLotGenealogyResponse genealogy(InventoryLotGenealogyQuery query) {
        InventoryLotGenealogyQuery safeQuery = query == null ? new InventoryLotGenealogyQuery() : query;
        if (safeQuery.getProductId() == null) {
            throw new IllegalArgumentException("批次谱系必须指定商品");
        }
        String lotNo = safeQuery.getLotNo() == null ? null : safeQuery.getLotNo().trim();
        if (!StringUtils.hasText(lotNo)) {
            throw new IllegalArgumentException("批次谱系必须指定批次号");
        }

        Direction direction = Direction.parse(safeQuery.getDirection());
        int maxDepth = clampMaxDepth(safeQuery.getMaxDepth());
        CurrentUser user = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        LotGenealogyKey rootKey = new LotGenealogyKey(safeQuery.getProductId(), lotNo);
        LotGenealogyAccumulator accumulator = new LotGenealogyAccumulator();
        accumulator.productIds.add(rootKey.productId());

        LotGenealogyNodeBuilder upstream = direction.includesUpstream()
                ? traverse(rootKey, Direction.UPSTREAM, maxDepth, user, snapshot, accumulator)
                : null;
        LotGenealogyNodeBuilder downstream = direction.includesDownstream()
                ? traverse(rootKey, Direction.DOWNSTREAM, maxDepth, user, snapshot, accumulator)
                : null;

        return assemblyService.assemble(new LotGenealogyTraversalResult(
                rootKey,
                upstream,
                downstream,
                accumulator,
                maxDepth,
                !snapshot.hasAllScope(),
                user.companyId(),
                user.accountBookId()
        ));
    }

    private LotGenealogyNodeBuilder traverse(
            LotGenealogyKey rootKey,
            Direction walk,
            int maxDepth,
            CurrentUser user,
            DataScopeSnapshot snapshot,
            LotGenealogyAccumulator accumulator
    ) {
        String txnDirection = walk == Direction.UPSTREAM ? DIRECTION_IN : DIRECTION_OUT;
        String expandBizType = walk == Direction.UPSTREAM ? "PRODUCTION_COMPLETION" : "PRODUCTION_ISSUE";
        String counterBizType = walk == Direction.UPSTREAM ? "PRODUCTION_ISSUE" : "PRODUCTION_COMPLETION";
        String counterDirection = walk == Direction.UPSTREAM ? DIRECTION_OUT : DIRECTION_IN;
        LotGenealogyNodeBuilder root = new LotGenealogyNodeBuilder(rootKey, 0);
        List<LotGenealogyNodeBuilder> frontier = new ArrayList<>(List.of(root));
        Set<LotGenealogyKey> visited = new LinkedHashSet<>(Set.of(rootKey));
        int depth = 0;
        int totalNodes = 0;

        while (!frontier.isEmpty()) {
            if (depth >= maxDepth) {
                accumulator.truncationReasons.add("MAX_DEPTH");
                break;
            }
            List<LotGenealogyKey> keys = frontier.stream().map(node -> node.key).toList();
            List<InventoryTransactionEntity> rows = loadLevelTransactions(keys, txnDirection, user, snapshot);
            rows.forEach(accumulator::observe);
            Map<LotGenealogyKey, List<InventoryTransactionEntity>> byLot = rows.stream()
                    .filter(row -> row.getProductId() != null && StringUtils.hasText(row.getLotNo()))
                    .collect(Collectors.groupingBy(
                            row -> new LotGenealogyKey(row.getProductId(), row.getLotNo().trim()),
                            LinkedHashMap::new,
                            Collectors.toList()));
            List<LotGenealogyExpansion> expansions = new ArrayList<>();
            Set<String> orderNos = new LinkedHashSet<>();

            for (LotGenealogyNodeBuilder node : frontier) {
                for (InventoryTransactionEntity row : byLot.getOrDefault(node.key, List.of())) {
                    LotGenealogyLinkBuilder link = new LotGenealogyLinkBuilder(row);
                    node.links.add(link);
                    if (expandBizType.equalsIgnoreCase(text(row.getBizType()))
                            && StringUtils.hasText(row.getBizNo())) {
                        expansions.add(new LotGenealogyExpansion(node, link));
                        orderNos.add(row.getBizNo().trim());
                    } else {
                        link.terminalReason = terminalReason(walk, row.getBizType());
                    }
                }
            }

            List<InventoryTransactionEntity> counterpartRows = orderNos.isEmpty()
                    ? List.of()
                    : loadProductionCounterparts(orderNos, counterBizType, counterDirection, user, snapshot);
            counterpartRows.forEach(accumulator::observe);
            Map<String, List<InventoryTransactionEntity>> counterparts = counterpartRows.stream()
                    .filter(row -> StringUtils.hasText(row.getBizNo()))
                    .collect(Collectors.groupingBy(row -> row.getBizNo().trim(), LinkedHashMap::new, Collectors.toList()));

            List<LotGenealogyNodeBuilder> next = new ArrayList<>();
            Set<LotGenealogyKey> levelNodeKeys = new LinkedHashSet<>();
            boolean levelLimitReached = false;
            boolean totalLimitReached = false;
            for (LotGenealogyExpansion expansion : expansions) {
                LotGenealogyLinkBuilder link = expansion.link();
                if (levelLimitReached) {
                    link.terminalReason = "NODE_LIMIT_PER_LEVEL";
                    continue;
                }
                if (totalLimitReached) {
                    link.terminalReason = "NODE_LIMIT_TOTAL";
                    continue;
                }
                List<InventoryTransactionEntity> children = counterparts.getOrDefault(
                        text(link.txn.getBizNo()).trim(), List.of());
                if (children.isEmpty()) {
                    link.terminalReason = walk == Direction.UPSTREAM ? "NO_MATERIAL_ISSUED" : "IN_PRODUCTION";
                    continue;
                }
                LotGenealogyNodeBuilder orderNode = new LotGenealogyNodeBuilder(
                        expansion.parent().key,
                        expansion.parent().depth
                );
                Set<LotGenealogyKey> expansionKeys = new LinkedHashSet<>();
                for (InventoryTransactionEntity child : children) {
                    String childLotNo = StringUtils.hasText(child.getLotNo()) ? child.getLotNo().trim() : null;
                    LotGenealogyKey childKey = new LotGenealogyKey(child.getProductId(), childLotNo);
                    if (!expansionKeys.add(childKey)) {
                        continue;
                    }
                    boolean lotControlled = StringUtils.hasText(childLotNo);
                    boolean alreadyVisited = lotControlled && visited.contains(childKey);
                    boolean createsNode = !lotControlled || !alreadyVisited;
                    boolean newLevelNode = !levelNodeKeys.contains(childKey);
                    if (createsNode && newLevelNode && levelNodeKeys.size() >= PER_LEVEL_NODE_LIMIT) {
                        accumulator.truncationReasons.add("NODE_LIMIT_PER_LEVEL");
                        levelLimitReached = true;
                        break;
                    }
                    if (createsNode && totalNodes >= TOTAL_NODE_LIMIT) {
                        accumulator.truncationReasons.add("NODE_LIMIT_TOTAL");
                        totalLimitReached = true;
                        break;
                    }
                    if (createsNode) {
                        levelNodeKeys.add(childKey);
                    }
                    LotGenealogyLinkBuilder childLink = new LotGenealogyLinkBuilder(child);
                    if (!lotControlled) {
                        childLink.terminalReason = walk == Direction.UPSTREAM
                                ? "MATERIAL_NOT_LOT_CONTROLLED" : "OUTPUT_NOT_LOT_CONTROLLED";
                        childLink.child = new LotGenealogyNodeBuilder(childKey, expansion.parent().depth + 1);
                        orderNode.links.add(childLink);
                        totalNodes++;
                        continue;
                    }
                    if (alreadyVisited) {
                        childLink.terminalReason = "ALREADY_VISITED";
                    } else {
                        visited.add(childKey);
                        childLink.child = new LotGenealogyNodeBuilder(childKey, expansion.parent().depth + 1);
                        if (depth + 1 >= maxDepth) {
                            childLink.child.depthTruncated = true;
                            accumulator.truncationReasons.add("MAX_DEPTH");
                        } else {
                            next.add(childLink.child);
                        }
                        totalNodes++;
                    }
                    orderNode.links.add(childLink);
                }
                if (orderNode.links.isEmpty()) {
                    if (link.terminalReason == null) {
                        link.terminalReason = levelLimitReached
                                ? "NODE_LIMIT_PER_LEVEL"
                                : totalLimitReached
                                ? "NODE_LIMIT_TOTAL"
                                : walk == Direction.UPSTREAM
                                ? "MATERIAL_NOT_LOT_CONTROLLED" : "OUTPUT_NOT_LOT_CONTROLLED";
                    }
                } else {
                    link.child = orderNode;
                    link.terminalReason = null;
                }
            }
            frontier = next;
            depth++;
        }
        return root;
    }

    private List<InventoryTransactionEntity> loadLevelTransactions(
            List<LotGenealogyKey> keys,
            String direction,
            CurrentUser user,
            DataScopeSnapshot snapshot
    ) {
        if (keys.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, user.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, user.accountBookId())
                .eq(InventoryTransactionEntity::getDirection, direction)
                .and(group -> {
                    boolean first = true;
                    for (LotGenealogyKey key : keys) {
                        if (first) {
                            group.eq(InventoryTransactionEntity::getProductId, key.productId())
                                    .eq(InventoryTransactionEntity::getLotNo, key.lotNo());
                            first = false;
                        } else {
                            group.or(inner -> inner
                                    .eq(InventoryTransactionEntity::getProductId, key.productId())
                                    .eq(InventoryTransactionEntity::getLotNo, key.lotNo()));
                        }
                    }
                })
                .orderByAsc(InventoryTransactionEntity::getOccurredTime)
                .orderByAsc(InventoryTransactionEntity::getId);
        return inventoryTransactionMapper.selectList(dataScopeService.applyInventoryTransactionScope(wrapper, snapshot));
    }

    private List<InventoryTransactionEntity> loadProductionCounterparts(
            Collection<String> orderNos,
            String bizType,
            String direction,
            CurrentUser user,
            DataScopeSnapshot snapshot
    ) {
        if (orderNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<InventoryTransactionEntity> wrapper = new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, user.companyId())
                .eq(InventoryTransactionEntity::getAccountBookId, user.accountBookId())
                .eq(InventoryTransactionEntity::getBizType, bizType)
                .eq(InventoryTransactionEntity::getDirection, direction)
                .in(InventoryTransactionEntity::getBizNo, orderNos)
                .orderByAsc(InventoryTransactionEntity::getOccurredTime)
                .orderByAsc(InventoryTransactionEntity::getId);
        return inventoryTransactionMapper.selectList(dataScopeService.applyInventoryTransactionScope(wrapper, snapshot));
    }

    private String terminalReason(Direction walk, String rawBizType) {
        String bizType = text(rawBizType).toUpperCase(Locale.ROOT);
        if (walk == Direction.UPSTREAM) {
            return switch (bizType) {
                case "PURCHASE_RECEIPT" -> "PURCHASED";
                case "SALES_RETURN" -> "RETURNED_BY_CUSTOMER";
                case "INVENTORY_TRANSFER" -> "MOVED_INTERNALLY";
                case "INVENTORY_ADJUSTMENT", "INVENTORY_CHECK" -> "ADJUSTED";
                case "OPENING_INVENTORY", "OPENING_BALANCE" -> "OPENING_BALANCE";
                case "PRODUCTION_COMPLETION" -> null;
                case "PRODUCTION_COMPLETION_REVERSAL", "PRODUCTION_RETURN" -> "REVERSED";
                default -> "UNKNOWN_SOURCE";
            };
        }
        return switch (bizType) {
            case "SALES_DELIVERY" -> "SOLD";
            case "PURCHASE_RETURN" -> "RETURNED_TO_SUPPLIER";
            case "INVENTORY_TRANSFER" -> "MOVED_INTERNALLY";
            case "INVENTORY_ADJUSTMENT", "INVENTORY_CHECK" -> "ADJUSTED";
            case "PRODUCTION_ISSUE" -> null;
            case "PRODUCTION_COMPLETION_REVERSAL", "PRODUCTION_RETURN" -> "REVERSED";
            default -> "UNKNOWN_DESTINATION";
        };
    }

    private static int clampMaxDepth(Integer value) {
        return value == null
                ? DEFAULT_MAX_DEPTH
                : Math.max(MIN_MAX_DEPTH, Math.min(HARD_MAX_DEPTH, value));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    record LotGenealogyTraversalResult(
            LotGenealogyKey rootKey,
            LotGenealogyNodeBuilder upstream,
            LotGenealogyNodeBuilder downstream,
            LotGenealogyAccumulator accumulator,
            int maxDepth,
            boolean scopeLimited,
            Long companyId,
            Long accountBookId
    ) {
    }

    record LotGenealogyKey(Long productId, String lotNo) {
    }

    private record LotGenealogyExpansion(
            LotGenealogyNodeBuilder parent,
            LotGenealogyLinkBuilder link
    ) {
    }

    static final class LotGenealogyNodeBuilder {
        final LotGenealogyKey key;
        final int depth;
        final List<LotGenealogyLinkBuilder> links = new ArrayList<>();
        boolean depthTruncated;

        LotGenealogyNodeBuilder(LotGenealogyKey key, int depth) {
            this.key = key;
            this.depth = depth;
        }

        boolean depthTruncated() {
            return depthTruncated;
        }
    }

    static final class LotGenealogyLinkBuilder {
        final InventoryTransactionEntity txn;
        String terminalReason;
        LotGenealogyNodeBuilder child;

        private LotGenealogyLinkBuilder(InventoryTransactionEntity txn) {
            this.txn = txn;
        }
    }

    static final class LotGenealogyAccumulator {
        final Map<LotGenealogyKey, InventoryTransactionEntity> lotDates = new HashMap<>();
        final Set<Long> productIds = new LinkedHashSet<>();
        final Set<Long> warehouseIds = new LinkedHashSet<>();
        final Set<String> receiptNos = new LinkedHashSet<>();
        final Set<String> deliveryNos = new LinkedHashSet<>();
        final Set<String> truncationReasons = new LinkedHashSet<>();

        private void observe(InventoryTransactionEntity txn) {
            if (txn.getProductId() != null) {
                productIds.add(txn.getProductId());
                if (StringUtils.hasText(txn.getLotNo())) {
                    lotDates.putIfAbsent(
                            new LotGenealogyKey(txn.getProductId(), txn.getLotNo().trim()),
                            txn
                    );
                }
            }
            if (txn.getWarehouseId() != null) {
                warehouseIds.add(txn.getWarehouseId());
            }
            if (("PURCHASE_RECEIPT".equalsIgnoreCase(text(txn.getBizType()))
                    || "PURCHASE_RETURN".equalsIgnoreCase(text(txn.getBizType())))
                    && StringUtils.hasText(txn.getBizNo())) {
                receiptNos.add(txn.getBizNo().trim());
            }
            if (("SALES_DELIVERY".equalsIgnoreCase(text(txn.getBizType()))
                    || "SALES_RETURN".equalsIgnoreCase(text(txn.getBizType())))
                    && StringUtils.hasText(txn.getBizNo())) {
                deliveryNos.add(txn.getBizNo().trim());
            }
        }
    }

    enum Direction {
        UPSTREAM,
        DOWNSTREAM,
        BOTH;

        static Direction parse(String value) {
            if (!StringUtils.hasText(value)) {
                return BOTH;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("批次谱系方向只支持 UPSTREAM、DOWNSTREAM 或 BOTH");
            }
        }

        boolean includesUpstream() {
            return this != DOWNSTREAM;
        }

        boolean includesDownstream() {
            return this != UPSTREAM;
        }
    }
}

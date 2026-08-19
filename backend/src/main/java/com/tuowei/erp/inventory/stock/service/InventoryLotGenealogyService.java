package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.web.CounterpartyRef;
import com.tuowei.erp.inventory.stock.web.GenealogyLimits;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import com.tuowei.erp.inventory.stock.web.LotGenealogyLink;
import com.tuowei.erp.inventory.stock.web.LotGenealogyNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryLotGenealogyService {

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
    private final InventoryDocumentLinkResolver documentLinkResolver;
    private final LotGenealogyCounterpartyResolver counterpartyResolver;
    private final LotGenealogyDisplayResolver displayResolver;

    public InventoryLotGenealogyService(
            InventoryTransactionMapper inventoryTransactionMapper,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            InventoryDocumentLinkResolver documentLinkResolver,
            LotGenealogyCounterpartyResolver counterpartyResolver,
            LotGenealogyDisplayResolver displayResolver
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.documentLinkResolver = documentLinkResolver;
        this.counterpartyResolver = counterpartyResolver;
        this.displayResolver = displayResolver;
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
        LotKey rootKey = new LotKey(safeQuery.getProductId(), lotNo);
        Accumulator accumulator = new Accumulator();
        accumulator.productIds.add(rootKey.productId());

        NodeBuilder upstream = direction.includesUpstream()
                ? traverse(rootKey, Direction.UPSTREAM, maxDepth, user, snapshot, accumulator)
                : null;
        NodeBuilder downstream = direction.includesDownstream()
                ? traverse(rootKey, Direction.DOWNSTREAM, maxDepth, user, snapshot, accumulator)
                : null;

        Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products = displayResolver.products(
                accumulator.productIds, user.companyId(), user.accountBookId());
        Map<Long, String> warehouseNames = displayResolver.warehouseNames(
                accumulator.warehouseIds, user.companyId(), user.accountBookId());
        LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties = counterpartyResolver.resolve(
                accumulator.receiptNos, accumulator.deliveryNos, user.companyId(), user.accountBookId());

        LotGenealogyNode root = toNode(new NodeBuilder(rootKey, 0), accumulator, products, warehouseNames, counterparties);
        return new InventoryLotGenealogyResponse(
                root,
                upstream == null ? null : toNode(upstream, accumulator, products, warehouseNames, counterparties),
                downstream == null ? null : toNode(downstream, accumulator, products, warehouseNames, counterparties),
                new GenealogyLimits(
                        maxDepth,
                        PER_LEVEL_NODE_LIMIT,
                        TOTAL_NODE_LIMIT,
                        !accumulator.truncationReasons.isEmpty(),
                        List.copyOf(accumulator.truncationReasons),
                        !snapshot.hasAllScope()
                )
        );
    }

    private NodeBuilder traverse(
            LotKey rootKey,
            Direction walk,
            int maxDepth,
            CurrentUser user,
            DataScopeSnapshot snapshot,
            Accumulator accumulator
    ) {
        String txnDirection = walk == Direction.UPSTREAM ? DIRECTION_IN : DIRECTION_OUT;
        String expandBizType = walk == Direction.UPSTREAM ? "PRODUCTION_COMPLETION" : "PRODUCTION_ISSUE";
        String counterBizType = walk == Direction.UPSTREAM ? "PRODUCTION_ISSUE" : "PRODUCTION_COMPLETION";
        String counterDirection = walk == Direction.UPSTREAM ? DIRECTION_OUT : DIRECTION_IN;
        NodeBuilder root = new NodeBuilder(rootKey, 0);
        List<NodeBuilder> frontier = new ArrayList<>(List.of(root));
        Set<LotKey> visited = new LinkedHashSet<>(Set.of(rootKey));
        int depth = 0;
        int totalNodes = 0;

        while (!frontier.isEmpty()) {
            if (depth >= maxDepth) {
                accumulator.truncationReasons.add("MAX_DEPTH");
                break;
            }
            List<LotKey> keys = frontier.stream().map(node -> node.key).toList();
            List<InventoryTransactionEntity> rows = loadLevelTransactions(keys, txnDirection, user, snapshot);
            rows.forEach(accumulator::observe);
            Map<LotKey, List<InventoryTransactionEntity>> byLot = rows.stream()
                    .filter(row -> row.getProductId() != null && StringUtils.hasText(row.getLotNo()))
                    .collect(Collectors.groupingBy(
                            row -> new LotKey(row.getProductId(), row.getLotNo().trim()),
                            LinkedHashMap::new,
                            Collectors.toList()));
            List<LinkExpansion> expansions = new ArrayList<>();
            Set<String> orderNos = new LinkedHashSet<>();

            for (NodeBuilder node : frontier) {
                for (InventoryTransactionEntity row : byLot.getOrDefault(node.key, List.of())) {
                    LinkBuilder link = new LinkBuilder(row, node.key);
                    node.links.add(link);
                    if (expandBizType.equalsIgnoreCase(text(row.getBizType()))
                            && StringUtils.hasText(row.getBizNo())) {
                        expansions.add(new LinkExpansion(node, link));
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

            List<NodeBuilder> next = new ArrayList<>();
            Set<LotKey> levelNodeKeys = new LinkedHashSet<>();
            boolean levelLimitReached = false;
            boolean totalLimitReached = false;
            for (LinkExpansion expansion : expansions) {
                LinkBuilder link = expansion.link;
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
                NodeBuilder orderNode = new NodeBuilder(expansion.parent.key, expansion.parent.depth);
                Set<LotKey> expansionKeys = new LinkedHashSet<>();
                for (InventoryTransactionEntity child : children) {
                    String childLotNo = StringUtils.hasText(child.getLotNo()) ? child.getLotNo().trim() : null;
                    LotKey childKey = new LotKey(child.getProductId(), childLotNo);
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
                    LinkBuilder childLink = new LinkBuilder(child, expansion.parent.key);
                    if (!lotControlled) {
                        childLink.terminalReason = walk == Direction.UPSTREAM
                                ? "MATERIAL_NOT_LOT_CONTROLLED" : "OUTPUT_NOT_LOT_CONTROLLED";
                        childLink.child = new NodeBuilder(childKey, expansion.parent.depth + 1);
                        orderNode.links.add(childLink);
                        totalNodes++;
                        continue;
                    }
                    if (alreadyVisited) {
                        childLink.terminalReason = "ALREADY_VISITED";
                    } else {
                        visited.add(childKey);
                        childLink.child = new NodeBuilder(childKey, expansion.parent.depth + 1);
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
            List<LotKey> keys,
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
                    for (LotKey key : keys) {
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

    private LotGenealogyNode toNode(
            NodeBuilder builder,
            Accumulator accumulator,
            Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products,
            Map<Long, String> warehouseNames,
            LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties
    ) {
        LotGenealogyDisplayResolver.ProductDisplay display = products.get(builder.key.productId());
        InventoryTransactionEntity dates = accumulator.lotDates.get(builder.key);
        return new LotGenealogyNode(
                builder.key.productId(),
                display == null ? null : display.code(),
                display == null ? null : display.name(),
                builder.key.lotNo(),
                dates == null ? null : dates.getProductionDate(),
                dates == null ? null : dates.getExpiryDate(),
                builder.depth,
                builder.links.stream()
                        .map(link -> toLink(link, accumulator, products, warehouseNames, counterparties))
                        .toList()
        );
    }

    private LotGenealogyLink toLink(
            LinkBuilder builder,
            Accumulator accumulator,
            Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products,
            Map<Long, String> warehouseNames,
            LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties
    ) {
        InventoryTransactionEntity txn = builder.txn;
        String bizType = text(txn.getBizType()).toUpperCase(Locale.ROOT);
        CounterpartyRef counterparty = switch (bizType) {
            case "PURCHASE_RECEIPT", "PURCHASE_RETURN" -> counterparties.supplierFor(txn.getBizNo());
            case "SALES_DELIVERY", "SALES_RETURN" -> counterparties.customerFor(txn.getBizNo());
            default -> null;
        };
        String terminalReason = builder.terminalReason;
        if (terminalReason == null && builder.child != null && builder.child.depthTruncated) {
            terminalReason = "MAX_DEPTH";
        }
        return new LotGenealogyLink(
                txn.getBizType(),
                txn.getBizNo(),
                documentLinkResolver.resolveLabel(txn.getBizType()),
                documentLinkResolver.resolveRoute(txn.getBizType(), txn.getBizNo()),
                txn.getOccurredTime(),
                ScalePrecision.quantity(ScalePrecision.zeroDefault(txn.getQty())),
                txn.getWarehouseId(),
                txn.getWarehouseId() == null ? null : warehouseNames.get(txn.getWarehouseId()),
                counterparty,
                terminalReason,
                builder.child == null ? null : toNode(builder.child, accumulator, products, warehouseNames, counterparties)
        );
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

    private record LotKey(Long productId, String lotNo) {
    }

    private record LinkExpansion(NodeBuilder parent, LinkBuilder link) {
    }

    private static final class NodeBuilder {
        private final LotKey key;
        private final int depth;
        private final List<LinkBuilder> links = new ArrayList<>();
        private boolean depthTruncated;

        private NodeBuilder(LotKey key, int depth) {
            this.key = key;
            this.depth = depth;
        }
    }

    private static final class LinkBuilder {
        private final InventoryTransactionEntity txn;
        private final LotKey parentKey;
        private String terminalReason;
        private NodeBuilder child;

        private LinkBuilder(InventoryTransactionEntity txn, LotKey parentKey) {
            this.txn = txn;
            this.parentKey = parentKey;
        }
    }

    private static final class Accumulator {
        private final Map<LotKey, InventoryTransactionEntity> lotDates = new HashMap<>();
        private final Set<Long> productIds = new LinkedHashSet<>();
        private final Set<Long> warehouseIds = new LinkedHashSet<>();
        private final Set<String> receiptNos = new LinkedHashSet<>();
        private final Set<String> deliveryNos = new LinkedHashSet<>();
        private final Set<String> truncationReasons = new LinkedHashSet<>();

        private void observe(InventoryTransactionEntity txn) {
            if (txn.getProductId() != null) {
                productIds.add(txn.getProductId());
                if (StringUtils.hasText(txn.getLotNo())) {
                    lotDates.putIfAbsent(new LotKey(txn.getProductId(), txn.getLotNo().trim()), txn);
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

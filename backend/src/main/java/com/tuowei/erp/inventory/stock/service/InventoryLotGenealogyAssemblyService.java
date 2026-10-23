package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.web.CounterpartyRef;
import com.tuowei.erp.inventory.stock.web.GenealogyLimits;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import com.tuowei.erp.inventory.stock.web.LotGenealogyLink;
import com.tuowei.erp.inventory.stock.web.LotGenealogyNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Hydrates display data and assembles the public lot genealogy DTO graph. */
@Service
public class InventoryLotGenealogyAssemblyService {

    private final InventoryDocumentLinkResolver documentLinkResolver;
    private final LotGenealogyCounterpartyResolver counterpartyResolver;
    private final LotGenealogyDisplayResolver displayResolver;

    public InventoryLotGenealogyAssemblyService(
            InventoryDocumentLinkResolver documentLinkResolver,
            LotGenealogyCounterpartyResolver counterpartyResolver,
            LotGenealogyDisplayResolver displayResolver
    ) {
        this.documentLinkResolver = documentLinkResolver;
        this.counterpartyResolver = counterpartyResolver;
        this.displayResolver = displayResolver;
    }

    public InventoryLotGenealogyResponse assemble(
            InventoryLotGenealogyQueryService.LotGenealogyTraversalResult result
    ) {
        InventoryLotGenealogyQueryService.LotGenealogyAccumulator accumulator = result.accumulator();
        Map<Long, LotGenealogyDisplayResolver.ProductDisplay> products = displayResolver.products(
                accumulator.productIds, result.companyId(), result.accountBookId());
        Map<Long, String> warehouseNames = displayResolver.warehouseNames(
                accumulator.warehouseIds, result.companyId(), result.accountBookId());
        LotGenealogyCounterpartyResolver.CounterpartyIndex counterparties = counterpartyResolver.resolve(
                accumulator.receiptNos,
                accumulator.deliveryNos,
                result.companyId(),
                result.accountBookId()
        );
        LotGenealogyNode root = toNode(
                newRoot(result.rootKey()), accumulator, products, warehouseNames, counterparties);
        return new InventoryLotGenealogyResponse(
                root,
                result.upstream() == null ? null : toNode(
                        result.upstream(), accumulator, products, warehouseNames, counterparties),
                result.downstream() == null ? null : toNode(
                        result.downstream(), accumulator, products, warehouseNames, counterparties),
                new GenealogyLimits(
                        result.maxDepth(),
                        InventoryLotGenealogyQueryService.PER_LEVEL_NODE_LIMIT,
                        InventoryLotGenealogyQueryService.TOTAL_NODE_LIMIT,
                        !accumulator.truncationReasons.isEmpty(),
                        List.copyOf(accumulator.truncationReasons),
                        result.scopeLimited()
                )
        );
    }

    private InventoryLotGenealogyQueryService.LotGenealogyNodeBuilder newRoot(
            InventoryLotGenealogyQueryService.LotGenealogyKey key
    ) {
        return new InventoryLotGenealogyQueryService.LotGenealogyNodeBuilder(key, 0);
    }

    private LotGenealogyNode toNode(
            InventoryLotGenealogyQueryService.LotGenealogyNodeBuilder builder,
            InventoryLotGenealogyQueryService.LotGenealogyAccumulator accumulator,
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
            InventoryLotGenealogyQueryService.LotGenealogyLinkBuilder builder,
            InventoryLotGenealogyQueryService.LotGenealogyAccumulator accumulator,
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
        if (terminalReason == null && builder.child != null && builder.child.depthTruncated()) {
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

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}

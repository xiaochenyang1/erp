package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotGenealogyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for lot genealogy queries. */
@Service
public class InventoryLotGenealogyService {

    private final InventoryLotGenealogyQueryService queryService;

    @Autowired
    public InventoryLotGenealogyService(InventoryLotGenealogyQueryService queryService) {
        this.queryService = queryService;
    }

    /** Keeps direct construction in existing tests compatible. */
    public InventoryLotGenealogyService(
            com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper inventoryTransactionMapper,
            com.tuowei.erp.common.security.CurrentUserContext currentUserContext,
            com.tuowei.erp.common.security.DataScopeService dataScopeService,
            InventoryDocumentLinkResolver documentLinkResolver,
            LotGenealogyCounterpartyResolver counterpartyResolver,
            LotGenealogyDisplayResolver displayResolver
    ) {
        this.queryService = new InventoryLotGenealogyQueryService(
                inventoryTransactionMapper,
                currentUserContext,
                dataScopeService,
                new InventoryLotGenealogyAssemblyService(
                        documentLinkResolver,
                        counterpartyResolver,
                        displayResolver
                )
        );
    }

    @Transactional(readOnly = true)
    public InventoryLotGenealogyResponse genealogy(InventoryLotGenealogyQuery query) {
        return queryService.genealogy(query);
    }
}

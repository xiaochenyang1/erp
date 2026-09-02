package com.tuowei.erp.inventory.stock.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.web.InventoryBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class InventoryStockQueryService {

    private final InventoryBalanceQueryService inventoryBalanceQueryService;
    private final InventoryTransactionQueryService inventoryTransactionQueryService;
    private final InventoryLotQueryService inventoryLotQueryService;

    public InventoryStockQueryService(
            InventoryBalanceQueryService inventoryBalanceQueryService,
            InventoryTransactionQueryService inventoryTransactionQueryService,
            InventoryLotQueryService inventoryLotQueryService
    ) {
        this.inventoryBalanceQueryService = inventoryBalanceQueryService;
        this.inventoryTransactionQueryService = inventoryTransactionQueryService;
        this.inventoryLotQueryService = inventoryLotQueryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBalanceResponse> listBalances(InventoryBalancePageQuery query) {
        return inventoryBalanceQueryService.listBalances(query);
    }

    @Transactional(readOnly = true)
    public StreamingResponseBody exportBalances(InventoryBalancePageQuery query) {
        return inventoryBalanceQueryService.exportBalances(query);
    }

    @Transactional(readOnly = true)
    public InventoryBalanceResponse getBalanceById(Long id) {
        return inventoryBalanceQueryService.getBalanceById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotBalanceResponse> listLotBalances(InventoryLotBalancePageQuery query) {
        return inventoryLotQueryService.listLotBalances(query);
    }

    @Transactional(readOnly = true)
    public InventoryLotBalanceResponse getLotBalanceById(Long id) {
        return inventoryLotQueryService.getLotBalanceById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> listTransactions(InventoryTransactionPageQuery query) {
        return inventoryTransactionQueryService.listTransactions(query);
    }

    @Transactional(readOnly = true)
    public InventoryTransactionResponse getTransactionById(Long id) {
        return inventoryTransactionQueryService.getTransactionById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotTraceResponse> traceLot(InventoryLotTraceQuery query) {
        return inventoryLotQueryService.traceLot(query);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryLotExpiryAlertResponse> listLotExpiryAlerts(InventoryLotExpiryAlertQuery query) {
        return inventoryLotQueryService.listLotExpiryAlerts(query);
    }
}

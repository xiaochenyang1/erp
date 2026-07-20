package com.tuowei.erp.inventory.stock.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.inventory.stock.service.InventoryStockQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/inventory")
public class InventoryStockQueryController {

    private final InventoryStockQueryService inventoryStockQueryService;

    public InventoryStockQueryController(InventoryStockQueryService inventoryStockQueryService) {
        this.inventoryStockQueryService = inventoryStockQueryService;
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/balances")
    public ApiResponse<PageResponse<InventoryBalanceResponse>> listBalances(InventoryBalancePageQuery query) {
        return ApiResponse.success(inventoryStockQueryService.listBalances(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/balances/export")
    public ResponseEntity<StreamingResponseBody> exportBalances(InventoryBalancePageQuery query) {
        return csv("inventory-balances.csv", inventoryStockQueryService.exportBalances(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/balances/{id}")
    public ApiResponse<InventoryBalanceResponse> balanceDetail(@PathVariable Long id) {
        return ApiResponse.success(inventoryStockQueryService.getBalanceById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/lot-balances")
    public ApiResponse<PageResponse<InventoryLotBalanceResponse>> listLotBalances(InventoryLotBalancePageQuery query) {
        return ApiResponse.success(inventoryStockQueryService.listLotBalances(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/lot-balances/{id}")
    public ApiResponse<InventoryLotBalanceResponse> lotBalanceDetail(@PathVariable Long id) {
        return ApiResponse.success(inventoryStockQueryService.getLotBalanceById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/lots/trace")
    public ApiResponse<PageResponse<InventoryLotTraceResponse>> traceLot(InventoryLotTraceQuery query) {
        return ApiResponse.success(inventoryStockQueryService.traceLot(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/lots/alerts")
    public ApiResponse<PageResponse<InventoryLotExpiryAlertResponse>> lotExpiryAlerts(InventoryLotExpiryAlertQuery query) {
        return ApiResponse.success(inventoryStockQueryService.listLotExpiryAlerts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/transactions")
    public ApiResponse<PageResponse<InventoryTransactionResponse>> listTransactions(InventoryTransactionPageQuery query) {
        return ApiResponse.success(inventoryStockQueryService.listTransactions(query));
    }

    @PreAuthorize(PermissionCodes.HAS_INVENTORY_STOCK_VIEW)
    @GetMapping("/transactions/{id}")
    public ApiResponse<InventoryTransactionResponse> transactionDetail(@PathVariable Long id) {
        return ApiResponse.success(inventoryStockQueryService.getTransactionById(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "inventory-export.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

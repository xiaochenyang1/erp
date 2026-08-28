package com.tuowei.erp.inventory.alert.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleCreateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleResponse;
import com.tuowei.erp.inventory.alert.web.InventoryAlertRuleUpdateRequest;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;

/** Compatibility facade for inventory alert queries and commands. */
@Service
public class InventoryAlertService {

    private final InventoryAlertQueryService alertQueryService;
    private final InventoryAlertCommandService alertCommandService;

    public InventoryAlertService(
            InventoryAlertQueryService alertQueryService,
            InventoryAlertCommandService alertCommandService
    ) {
        this.alertQueryService = alertQueryService;
        this.alertCommandService = alertCommandService;
    }

    @Transactional
    public InventoryAlertRuleResponse createRule(InventoryAlertRuleCreateRequest request) {
        return alertCommandService.createRule(request);
    }

    @Transactional(readOnly = true)
    public List<InventoryAlertRuleResponse> listRules(Long warehouseId, Long productId, Boolean enabled) {
        return alertQueryService.listRules(warehouseId, productId, enabled);
    }

    @Transactional
    public InventoryAlertRuleResponse updateRule(Long id, InventoryAlertRuleUpdateRequest request) {
        return alertCommandService.updateRule(id, request);
    }

    @Transactional
    public InventoryAlertRuleResponse enableRule(Long id) {
        return alertCommandService.enableRule(id);
    }

    @Transactional
    public InventoryAlertRuleResponse disableRule(Long id) {
        return alertCommandService.disableRule(id);
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(Long warehouseId, Long productId) {
        return alertQueryService.listLowStock(warehouseId, productId);
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(Long warehouseId, Long productId, AuditMetadata audit) {
        return alertQueryService.listLowStock(warehouseId, productId, audit);
    }

    @Transactional(readOnly = true)
    public List<InventoryLowStockResponse> listLowStock(
            Long warehouseId,
            Long productId,
            AuditMetadata audit,
            Collection<Long> scopedWarehouseIds
    ) {
        return alertQueryService.listLowStock(warehouseId, productId, audit, scopedWarehouseIds);
    }

    @Transactional
    public void handle(Long warehouseId, Long productId, String status, String remark) {
        alertCommandService.handle(warehouseId, productId, status, remark);
    }

    @Transactional
    public void reactivate(Long warehouseId, Long productId) {
        alertCommandService.reactivate(warehouseId, productId);
    }
}

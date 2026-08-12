package com.tuowei.erp.inventory.replenishment.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 补货建议门面：将读路径委托给 {@link InventoryReplenishmentSuggestionQueryService}，
 * 将写路径委托给 {@link InventoryReplenishmentSuggestionPostingService}，控制器调用方签名保持不变。
 *
 * 读方法标注只读事务，写方法与协作者均保持 REQUIRED 写事务。
 */
@Service
public class InventoryReplenishmentSuggestionService {

    private final InventoryReplenishmentSuggestionQueryService replenishmentSuggestionQueryService;
    private final InventoryReplenishmentSuggestionPostingService replenishmentSuggestionPostingService;

    public InventoryReplenishmentSuggestionService(
            InventoryReplenishmentSuggestionQueryService replenishmentSuggestionQueryService,
            InventoryReplenishmentSuggestionPostingService replenishmentSuggestionPostingService
    ) {
        this.replenishmentSuggestionQueryService = replenishmentSuggestionQueryService;
        this.replenishmentSuggestionPostingService = replenishmentSuggestionPostingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReplenishmentSuggestionResponse> list(InventoryReplenishmentSuggestionPageQuery query) {
        return replenishmentSuggestionQueryService.list(query);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse create(InventoryReplenishmentSuggestionCreateRequest request) {
        return replenishmentSuggestionPostingService.create(request);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse update(
            Long id,
            InventoryReplenishmentSuggestionUpdateRequest request
    ) {
        return replenishmentSuggestionPostingService.update(id, request);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse cancel(
            Long id,
            InventoryReplenishmentSuggestionCancelRequest request
    ) {
        return replenishmentSuggestionPostingService.cancel(id, request);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse convertToPurchaseOrder(Long id) {
        return replenishmentSuggestionPostingService.convertToPurchaseOrder(id);
    }
}

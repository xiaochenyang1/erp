package com.tuowei.erp.inventory.replenishment.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCancelRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionCreateRequest;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionPageQuery;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionResponse;
import com.tuowei.erp.inventory.replenishment.web.InventoryReplenishmentSuggestionUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for replenishment suggestion commands and queries. */
@Service
public class InventoryReplenishmentSuggestionService {

    private final InventoryReplenishmentSuggestionQueryService suggestionQueryService;
    private final InventoryReplenishmentSuggestionCommandService suggestionCommandService;

    public InventoryReplenishmentSuggestionService(
            InventoryReplenishmentSuggestionQueryService suggestionQueryService,
            InventoryReplenishmentSuggestionCommandService suggestionCommandService
    ) {
        this.suggestionQueryService = suggestionQueryService;
        this.suggestionCommandService = suggestionCommandService;
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse create(InventoryReplenishmentSuggestionCreateRequest request) {
        return suggestionCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryReplenishmentSuggestionResponse> list(InventoryReplenishmentSuggestionPageQuery query) {
        InventoryReplenishmentSuggestionPageQuery safeQuery =
                query == null ? new InventoryReplenishmentSuggestionPageQuery() : query;
        return suggestionQueryService.list(safeQuery);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse update(
            Long id,
            InventoryReplenishmentSuggestionUpdateRequest request
    ) {
        return suggestionCommandService.update(id, request);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse cancel(
            Long id,
            InventoryReplenishmentSuggestionCancelRequest request
    ) {
        return suggestionCommandService.cancel(id, request);
    }

    @Transactional
    public InventoryReplenishmentSuggestionResponse convertToPurchaseOrder(Long id) {
        return suggestionCommandService.convertToPurchaseOrder(id);
    }
}

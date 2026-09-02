package com.tuowei.erp.finance.voucher.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for manual voucher commands, queries and ledger posting. */
@Service
public class ManualVoucherService {

    private final ManualVoucherQueryService manualVoucherQueryService;
    private final ManualVoucherCommandService manualVoucherCommandService;
    private final ManualVoucherPostingService manualVoucherPostingService;

    public ManualVoucherService(
            ManualVoucherQueryService manualVoucherQueryService,
            ManualVoucherCommandService manualVoucherCommandService,
            ManualVoucherPostingService manualVoucherPostingService
    ) {
        this.manualVoucherQueryService = manualVoucherQueryService;
        this.manualVoucherCommandService = manualVoucherCommandService;
        this.manualVoucherPostingService = manualVoucherPostingService;
    }

    @Transactional
    public ManualVoucherResponse create(ManualVoucherSaveRequest request) {
        return manualVoucherCommandService.create(request);
    }

    @Transactional
    public ManualVoucherResponse update(Long id, ManualVoucherSaveRequest request) {
        return manualVoucherCommandService.update(id, request);
    }

    @Transactional
    public void submit(Long id) {
        manualVoucherCommandService.submit(id);
    }

    @Transactional
    public void approve(Long id) {
        manualVoucherCommandService.approve(id);
    }

    @Transactional
    public void reject(Long id, String reason) {
        manualVoucherCommandService.reject(id, reason);
    }

    @Transactional
    public void post(Long id) {
        manualVoucherPostingService.post(id);
    }

    @Transactional
    public void cancel(Long id, String reason) {
        manualVoucherPostingService.cancel(id, reason);
    }

    @Transactional
    public void delete(Long id) {
        manualVoucherCommandService.delete(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ManualVoucherResponse> list(ManualVoucherPageQuery query) {
        ManualVoucherPageQuery safeQuery = query == null ? new ManualVoucherPageQuery() : query;
        return manualVoucherQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public ManualVoucherResponse detail(Long id) {
        return manualVoucherQueryService.detail(id);
    }
}

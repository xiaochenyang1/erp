package com.tuowei.erp.finance.voucher.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherPageQuery;
import com.tuowei.erp.finance.voucher.web.ManualVoucherResponse;
import com.tuowei.erp.finance.voucher.web.ManualVoucherSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 手工凭证门面：读侧委托 {@link ManualVoucherQueryService}，写侧委托
 * {@link ManualVoucherPostingService}，仅为控制器保留薄入口。
 *
 * 草稿/审批阶段的分录只存在 fin_manual_voucher_line，不进 fin_voucher_entry，因此不影响
 * 总账/月结/对账；过账时才把分录灌入共享的 fin_voucher + fin_voucher_entry，与自动凭证同源。
 */
@Service
public class ManualVoucherService {

    private final ManualVoucherQueryService manualVoucherQueryService;
    private final ManualVoucherPostingService manualVoucherPostingService;

    public ManualVoucherService(
            ManualVoucherQueryService manualVoucherQueryService,
            ManualVoucherPostingService manualVoucherPostingService
    ) {
        this.manualVoucherQueryService = manualVoucherQueryService;
        this.manualVoucherPostingService = manualVoucherPostingService;
    }

    @Transactional
    public ManualVoucherResponse create(ManualVoucherSaveRequest request) {
        return manualVoucherPostingService.create(request);
    }

    @Transactional
    public ManualVoucherResponse update(Long id, ManualVoucherSaveRequest request) {
        return manualVoucherPostingService.update(id, request);
    }

    @Transactional
    public void submit(Long id) {
        manualVoucherPostingService.submit(id);
    }

    @Transactional
    public void approve(Long id) {
        manualVoucherPostingService.approve(id);
    }

    @Transactional
    public void reject(Long id, String reason) {
        manualVoucherPostingService.reject(id, reason);
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
        manualVoucherPostingService.delete(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ManualVoucherResponse> list(ManualVoucherPageQuery query) {
        return manualVoucherQueryService.list(query);
    }

    @Transactional(readOnly = true)
    public ManualVoucherResponse detail(Long id) {
        return manualVoucherQueryService.detail(id);
    }
}

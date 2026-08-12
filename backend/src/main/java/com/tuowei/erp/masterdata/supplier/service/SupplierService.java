package com.tuowei.erp.masterdata.supplier.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Supplier facade - delegates reads to {@link SupplierQueryService} and writes to
 * {@link SupplierPostingService}, keeping a thin entry point for controllers.
 */
@Service
public class SupplierService {

    private final SupplierQueryService supplierQueryService;
    private final SupplierPostingService supplierPostingService;

    public SupplierService(SupplierQueryService supplierQueryService, SupplierPostingService supplierPostingService) {
        this.supplierQueryService = supplierQueryService;
        this.supplierPostingService = supplierPostingService;
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        return supplierPostingService.create(request);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        return supplierQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(SupplierPageQuery query) {
        return supplierQueryService.list(query);
    }

    public StreamingResponseBody exportSuppliers(SupplierPageQuery query) {
        return supplierQueryService.exportSuppliers(query);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        return supplierPostingService.update(id, request);
    }

    @Transactional
    public SupplierResponse enable(Long id) {
        return supplierPostingService.enable(id);
    }

    @Transactional
    public SupplierResponse disable(Long id) {
        return supplierPostingService.disable(id);
    }
}

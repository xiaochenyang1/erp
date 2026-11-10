package com.tuowei.erp.masterdata.supplier.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Compatibility facade for supplier queries, export and commands. */
@Service
public class SupplierService {

    private final SupplierQueryService supplierQueryService;
    private final SupplierCommandService supplierCommandService;

    public SupplierService(SupplierQueryService supplierQueryService, SupplierCommandService supplierCommandService) {
        this.supplierQueryService = supplierQueryService;
        this.supplierCommandService = supplierCommandService;
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        return supplierCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        return supplierQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(SupplierPageQuery query) {
        SupplierPageQuery safeQuery = query == null ? new SupplierPageQuery() : query;
        return supplierQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportSuppliers(SupplierPageQuery query) {
        return supplierQueryService.exportSuppliers(query);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        return supplierCommandService.update(id, request);
    }

    @Transactional
    public SupplierResponse enable(Long id) {
        return supplierCommandService.enable(id);
    }

    @Transactional
    public SupplierResponse disable(Long id) {
        return supplierCommandService.disable(id);
    }
}

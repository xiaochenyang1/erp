package com.tuowei.erp.masterdata.customer.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Compatibility facade for customer queries, export and commands. */
@Service
public class CustomerService {

    private final CustomerQueryService customerQueryService;
    private final CustomerCommandService customerCommandService;

    public CustomerService(CustomerQueryService customerQueryService, CustomerCommandService customerCommandService) {
        this.customerQueryService = customerQueryService;
        this.customerCommandService = customerCommandService;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        return customerCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return customerQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(CustomerPageQuery query) {
        CustomerPageQuery safeQuery = query == null ? new CustomerPageQuery() : query;
        return customerQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportCustomers(CustomerPageQuery query) {
        return customerQueryService.exportCustomers(query);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        return customerCommandService.update(id, request);
    }

    @Transactional
    public CustomerResponse enable(Long id) {
        return customerCommandService.enable(id);
    }

    @Transactional
    public CustomerResponse disable(Long id) {
        return customerCommandService.disable(id);
    }
}

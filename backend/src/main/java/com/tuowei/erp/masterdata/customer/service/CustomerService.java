package com.tuowei.erp.masterdata.customer.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Customer facade - delegates reads to {@link CustomerQueryService} and writes to
 * {@link CustomerPostingService}, keeping a thin entry point for controllers.
 */
@Service
public class CustomerService {

    private final CustomerQueryService customerQueryService;
    private final CustomerPostingService customerPostingService;

    public CustomerService(CustomerQueryService customerQueryService, CustomerPostingService customerPostingService) {
        this.customerQueryService = customerQueryService;
        this.customerPostingService = customerPostingService;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        return customerPostingService.create(request);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return customerQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(CustomerPageQuery query) {
        return customerQueryService.list(query);
    }

    public StreamingResponseBody exportCustomers(CustomerPageQuery query) {
        return customerQueryService.exportCustomers(query);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        return customerPostingService.update(id, request);
    }

    @Transactional
    public CustomerResponse enable(Long id) {
        return customerPostingService.enable(id);
    }

    @Transactional
    public CustomerResponse disable(Long id) {
        return customerPostingService.disable(id);
    }
}

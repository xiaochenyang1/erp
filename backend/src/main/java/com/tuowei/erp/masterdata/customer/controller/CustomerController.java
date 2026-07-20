package com.tuowei.erp.masterdata.customer.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.masterdata.customer.service.CustomerService;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/masterdata/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_CREATE)
    @PostMapping
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ApiResponse.success(customerService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> list(CustomerPageQuery query) {
        return ApiResponse.success(customerService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(CustomerPageQuery query) {
        return csv("customers.csv", customerService.exportCustomers(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(customerService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return ApiResponse.success(customerService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<CustomerResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(customerService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_CUSTOMER_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<CustomerResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(customerService.disable(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "customers.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

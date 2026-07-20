package com.tuowei.erp.masterdata.supplier.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.masterdata.supplier.service.SupplierService;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
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
@RequestMapping("/api/masterdata/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_CREATE)
    @PostMapping
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest request) {
        return ApiResponse.success(supplierService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SupplierResponse>> list(SupplierPageQuery query) {
        return ApiResponse.success(supplierService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(SupplierPageQuery query) {
        return csv("suppliers.csv", supplierService.exportSuppliers(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SupplierResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(supplierService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.success(supplierService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<SupplierResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(supplierService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_SUPPLIER_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<SupplierResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(supplierService.disable(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "suppliers.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

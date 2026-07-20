package com.tuowei.erp.masterdata.product.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
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
@RequestMapping("/api/masterdata/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_CREATE)
    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.success(productService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(ProductPageQuery query) {
        return ApiResponse.success(productService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(ProductPageQuery query) {
        return csv("products.csv", productService.exportProducts(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(productService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<ProductResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(productService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_PRODUCT_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<ProductResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(productService.disable(id));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "products.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

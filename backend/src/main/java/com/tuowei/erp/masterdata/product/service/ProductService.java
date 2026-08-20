package com.tuowei.erp.masterdata.product.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Compatibility facade for product queries, export and commands. */
@Service
public class ProductService {

    private final ProductQueryService productQueryService;
    private final ProductCommandService productCommandService;

    public ProductService(ProductQueryService productQueryService, ProductCommandService productCommandService) {
        this.productQueryService = productQueryService;
        this.productCommandService = productCommandService;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        return productCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode) {
        return productQueryService.getByBarcode(barcode);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductPageQuery query) {
        ProductPageQuery safeQuery = query == null ? new ProductPageQuery() : query;
        return productQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportProducts(ProductPageQuery query) {
        return productQueryService.exportProducts(query);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        return productCommandService.update(id, request);
    }

    @Transactional
    public ProductResponse enable(Long id) {
        return productCommandService.enable(id);
    }

    @Transactional
    public ProductResponse disable(Long id) {
        return productCommandService.disable(id);
    }
}

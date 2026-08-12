package com.tuowei.erp.masterdata.product.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.web.ProductCreateRequest;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.masterdata.product.web.ProductUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Product facade - delegates reads to {@link ProductQueryService} and writes to
 * {@link ProductPostingService}, keeping a thin entry point for controllers.
 */
@Service
public class ProductService {

    private final ProductQueryService productQueryService;
    private final ProductPostingService productPostingService;

    public ProductService(ProductQueryService productQueryService, ProductPostingService productPostingService) {
        this.productQueryService = productQueryService;
        this.productPostingService = productPostingService;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        return productPostingService.create(request);
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
        return productQueryService.list(query);
    }

    public StreamingResponseBody exportProducts(ProductPageQuery query) {
        return productQueryService.exportProducts(query);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        return productPostingService.update(id, request);
    }

    @Transactional
    public ProductResponse enable(Long id) {
        return productPostingService.enable(id);
    }

    @Transactional
    public ProductResponse disable(Long id) {
        return productPostingService.disable(id);
    }
}

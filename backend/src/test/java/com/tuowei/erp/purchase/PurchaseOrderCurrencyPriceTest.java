package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.commercial.contract.service.ContractOrderBindingService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplierproduct.service.SupplierProductRelationService;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderCommandService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderNumberService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchasePriceEvaluator;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderCurrencyPriceTest {
    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long SUPPLIER_ID = 301L;
    private static final Long PRODUCT_ID = 501L;
    private static final Long ORDER_ID = 601L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 22);
    private static final AuditMetadata AUDIT = new AuditMetadata(
            9501L, COMPANY_ID, BOOK_ID, LocalDateTime.of(2026, 8, 22, 15, 0));

    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private PurchaseOrderLineMapper lineMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private ProductValidator productValidator;
    @Mock private PurchaseOrderNumberService numberService;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private PurchaseOrderQueryService queryService;
    @Mock private PurchasePriceEvaluator priceEvaluator;
    @Mock private SupplierProductRelationService supplierProductRelationService;
    @Mock private ContractOrderBindingService contractOrderBindingService;
    @Mock private SettlementCurrencyService currencyService;

    @ParameterizedTest(name = "{0} {1} order uses resolved rate {2} for the price gate")
    @CsvSource({"create, USD, 7", "update, USD, 7", "create, EUR, 8", "update, EUR, 8"})
    void writesResolveCurrencyBeforeCheckingBaseCurrencyMaximum(
            String operation, String currencyCode, BigDecimal rate
    ) {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(SUPPLIER_ID);
        supplier.setCompanyId(COMPANY_ID);
        supplier.setAccountBookId(BOOK_ID);
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier);
        when(currencyService.resolve(currencyCode, null, ORDER_DATE, AUDIT))
                .thenReturn(new SettlementCurrencyService.Resolution(currencyCode, rate));
        List<PurchaseOrderLineRequest> lines = List.of(new PurchaseOrderLineRequest(
                PRODUCT_ID, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, null));
        doThrow(new IllegalArgumentException("高于生效最高价"))
                .when(priceEvaluator).assertLinesWithinMaxPrice(
                        COMPANY_ID, BOOK_ID, SUPPLIER_ID, ORDER_DATE, lines, rate);
        if ("update".equals(operation)) {
            PurchaseOrderEntity order = new PurchaseOrderEntity();
            order.setId(ORDER_ID);
            order.setStatus("DRAFT");
            when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        }
        PurchaseOrderCommandService service = new PurchaseOrderCommandService(
                orderMapper, lineMapper, supplierMapper, productValidator, numberService, auditMetadataFactory,
                queryService, priceEvaluator, supplierProductRelationService, contractOrderBindingService, currencyService);

        assertThatThrownBy(() -> {
            if ("create".equals(operation)) {
                service.create(new PurchaseOrderCreateRequest(null, SUPPLIER_ID, ORDER_DATE, null,
                        currencyCode, null, null, lines));
            } else {
                service.update(ORDER_ID, new PurchaseOrderUpdateRequest(null, SUPPLIER_ID, ORDER_DATE, null,
                        currencyCode, null, null, lines));
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessage("高于生效最高价");

        InOrder checks = inOrder(currencyService, priceEvaluator);
        checks.verify(currencyService).resolve(currencyCode, null, ORDER_DATE, AUDIT);
        checks.verify(priceEvaluator).assertLinesWithinMaxPrice(
                COMPANY_ID, BOOK_ID, SUPPLIER_ID, ORDER_DATE, lines, rate);
        verifyNoInteractions(orderMapper, lineMapper);
    }
}

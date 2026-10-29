package com.tuowei.erp.sales.quote.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.order.service.SalesOrderService;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteLineMapper;
import com.tuowei.erp.sales.quote.mapper.SalesQuoteMapper;
import com.tuowei.erp.sales.quote.model.SalesQuoteEntity;
import com.tuowei.erp.sales.quote.model.SalesQuoteLineEntity;
import com.tuowei.erp.sales.quote.web.SalesQuoteLineRequest;
import com.tuowei.erp.sales.quote.web.SalesQuoteResponse;
import com.tuowei.erp.sales.quote.web.SalesQuoteSaveRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesQuoteCommandServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long CUSTOMER_ID = 301L;
    private static final Long PRODUCT_ID = 401L;
    private static final Long QUOTE_ID = 501L;
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 8, 22);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 14, 30);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, BOOK_ID, NOW);

    @Mock private SalesQuoteMapper salesQuoteMapper;
    @Mock private SalesQuoteLineMapper salesQuoteLineMapper;
    @Mock private SalesQuoteNumberService salesQuoteNumberService;
    @Mock private CustomerMapper customerMapper;
    @Mock private ProductValidator productValidator;
    @Mock private SalesOrderService salesOrderService;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SalesQuoteQueryService queryService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesQuoteEntity.class);
        initTableInfo(SalesQuoteLineEntity.class);
    }

    @Test
    void createMapsTenantAuditAmountsTaxAndLines() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(activeCustomer());
        when(salesQuoteNumberService.nextQuoteNo(QUOTE_DATE, AUDIT)).thenReturn("SQ-20260822-001");
        doAnswer(invocation -> {
            SalesQuoteEntity quote = invocation.getArgument(0);
            quote.setId(QUOTE_ID);
            return 1;
        }).when(salesQuoteMapper).insert(any(SalesQuoteEntity.class));
        SalesQuoteResponse expected = new SalesQuoteResponse(QUOTE_ID, "SQ-20260822-001", CUSTOMER_ID, "Acme",
                QUOTE_DATE, null, "DRAFT", new BigDecimal("20.00"), new BigDecimal("2.60"), null, "remark", List.of());
        when(queryService.detail(QUOTE_ID)).thenReturn(expected);

        SalesQuoteResponse result = service().create(new SalesQuoteSaveRequest(
                CUSTOMER_ID, QUOTE_DATE, null, "  remark ",
                List.of(new SalesQuoteLineRequest(PRODUCT_ID, new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("0.13"), " line "))));

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<SalesQuoteEntity> quoteCaptor = ArgumentCaptor.forClass(SalesQuoteEntity.class);
        verify(salesQuoteMapper).insert(quoteCaptor.capture());
        SalesQuoteEntity quote = quoteCaptor.getValue();
        assertThat(quote.getId()).isEqualTo(QUOTE_ID);
        assertThat(quote.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(quote.getAccountBookId()).isEqualTo(BOOK_ID);
        assertThat(quote.getQuoteNo()).isEqualTo("SQ-20260822-001");
        assertThat(quote.getStatus()).isEqualTo("DRAFT");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(quote.getTotalTaxAmount()).isEqualByComparingTo("2.60");
        assertThat(quote.getRemark()).isEqualTo("remark");
        assertThat(quote.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(quote.getVersion()).isZero();

        ArgumentCaptor<SalesQuoteLineEntity> lineCaptor = ArgumentCaptor.forClass(SalesQuoteLineEntity.class);
        verify(salesQuoteLineMapper).insert(lineCaptor.capture());
        SalesQuoteLineEntity line = lineCaptor.getValue();
        assertThat(line.getQuoteId()).isEqualTo(QUOTE_ID);
        assertThat(line.getLineNo()).isEqualTo(1);
        assertThat(line.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(line.getAccountBookId()).isEqualTo(BOOK_ID);
        assertThat(line.getQty()).isEqualByComparingTo("2.0000");
        assertThat(line.getPrice()).isEqualByComparingTo("10.00");
        assertThat(line.getTaxRate()).isEqualByComparingTo("0.1300");
        assertThat(line.getAmount()).isEqualByComparingTo("20.00");
        assertThat(line.getTaxAmount()).isEqualByComparingTo("2.60");
        assertThat(line.getRemark()).isEqualTo("line");
        verify(productValidator).requireProduct(PRODUCT_ID, COMPANY_ID, BOOK_ID);
    }

    @Test
    void updateRejectsNonDraftBeforeValidatingNewData() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        SalesQuoteEntity quote = quote("CONFIRMED");
        when(queryService.requireQuote(QUOTE_ID, AUDIT)).thenReturn(quote);

        assertThatThrownBy(() -> service().update(QUOTE_ID, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅草稿报价可编辑");

        verifyNoInteractions(customerMapper, productValidator, salesQuoteMapper, salesQuoteLineMapper);
    }

    @Test
    void confirmMovesDraftToConfirmedWithOptimisticUpdate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        SalesQuoteEntity quote = quote("DRAFT");
        when(queryService.requireQuote(QUOTE_ID, AUDIT)).thenReturn(quote);
        when(salesQuoteMapper.updateById(quote)).thenReturn(1);
        SalesQuoteResponse expected = new SalesQuoteResponse(QUOTE_ID, "SQ-1", CUSTOMER_ID, null,
                QUOTE_DATE, null, "CONFIRMED", null, null, null, null, List.of());
        when(queryService.detail(QUOTE_ID)).thenReturn(expected);

        assertThat(service().confirm(QUOTE_ID)).isSameAs(expected);
        assertThat(quote.getStatus()).isEqualTo("CONFIRMED");
        assertThat(quote.getUpdatedBy()).isEqualTo(USER_ID);
        verify(salesQuoteMapper).updateById(quote);
    }

    @Test
    void cancelRejectsConvertedAndCancelledQuotes() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        for (String status : List.of("CONVERTED", "CANCELLED")) {
            SalesQuoteEntity quote = quote(status);
            when(queryService.requireQuote(QUOTE_ID, AUDIT)).thenReturn(quote);
            assertThatThrownBy(() -> service().cancel(QUOTE_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("当前状态不可作废");
        }
        verify(salesQuoteMapper, never()).updateById(any(SalesQuoteEntity.class));
    }

    @Test
    void convertRequiresConfirmedQuote() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(queryService.requireQuote(QUOTE_ID, AUDIT)).thenReturn(quote("DRAFT"));

        assertThatThrownBy(() -> service().convertToOrder(QUOTE_ID, 601L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅已确认报价可转销售订单");
        verifyNoInteractions(salesOrderService, salesQuoteLineMapper);
    }

    @Test
    void convertCreatesOrderAndMarksQuoteConverted() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        SalesQuoteEntity quote = quote("CONFIRMED");
        quote.setQuoteNo("SQ-9");
        quote.setRemark("客户要求加急");
        when(queryService.requireQuote(QUOTE_ID, AUDIT)).thenReturn(quote);
        SalesQuoteLineEntity line = new SalesQuoteLineEntity();
        line.setProductId(PRODUCT_ID);
        line.setQty(new BigDecimal("2.0000"));
        line.setPrice(new BigDecimal("10.00"));
        line.setTaxRate(new BigDecimal("0.1300"));
        line.setRemark("line");
        when(queryService.loadLines(quote)).thenReturn(List.of(line));
        SalesOrderResponse order = new SalesOrderResponse(701L, "SO-1", CUSTOMER_ID, 601L, null,
                QUOTE_DATE, null, "DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED", null, null, null, "", List.of());
        when(salesOrderService.create(any(SalesOrderCreateRequest.class))).thenReturn(order);
        when(salesQuoteMapper.updateById(quote)).thenReturn(1);

        assertThat(service().convertToOrder(QUOTE_ID, 601L)).isSameAs(order);
        assertThat(quote.getStatus()).isEqualTo("CONVERTED");
        assertThat(quote.getConvertedOrderId()).isEqualTo(701L);
        ArgumentCaptor<SalesOrderCreateRequest> requestCaptor = ArgumentCaptor.forClass(SalesOrderCreateRequest.class);
        verify(salesOrderService).create(requestCaptor.capture());
        SalesOrderCreateRequest request = requestCaptor.getValue();
        assertThat(request.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(request.warehouseId()).isEqualTo(601L);
        assertThat(request.remark()).isEqualTo("来源报价 SQ-9；客户要求加急");
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().get(0).taxRate()).isEqualByComparingTo("0.1300");
        verify(salesQuoteMapper).updateById(quote);
    }

    private SalesQuoteCommandService service() {
        return new SalesQuoteCommandService(salesQuoteMapper, salesQuoteLineMapper, salesQuoteNumberService,
                customerMapper, productValidator, salesOrderService, auditMetadataFactory, queryService);
    }

    private CustomerEntity activeCustomer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCompanyId(COMPANY_ID);
        customer.setAccountBookId(BOOK_ID);
        customer.setCustomerName("Acme");
        customer.setStatus("ACTIVE");
        customer.setDeletedFlag(0);
        return customer;
    }

    private SalesQuoteEntity quote(String status) {
        SalesQuoteEntity quote = new SalesQuoteEntity();
        quote.setId(QUOTE_ID);
        quote.setCompanyId(COMPANY_ID);
        quote.setAccountBookId(BOOK_ID);
        quote.setCustomerId(CUSTOMER_ID);
        quote.setQuoteDate(QUOTE_DATE);
        quote.setStatus(status);
        quote.setDeletedFlag(0);
        quote.setVersion(0);
        return quote;
    }

    private SalesQuoteSaveRequest request() {
        return new SalesQuoteSaveRequest(CUSTOMER_ID, QUOTE_DATE, null, null,
                List.of(new SalesQuoteLineRequest(PRODUCT_ID, BigDecimal.ONE, BigDecimal.TEN, null, null)));
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

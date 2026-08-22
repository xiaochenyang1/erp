package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderCommandServiceTest {
    private static final Long COMPANY_ID = 101L;
    private static final Long BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long CUSTOMER_ID = 301L;
    private static final Long WAREHOUSE_ID = 401L;
    private static final Long PRODUCT_ID = 501L;
    private static final Long ORDER_ID = 601L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 8, 22);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, BOOK_ID, NOW);

    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private SalesOrderLineMapper salesOrderLineMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private ProductValidator productValidator;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private SalesOrderNumberService salesOrderNumberService;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SalesOrderQueryService queryService;
    @Mock private SalesCreditEvaluator salesCreditEvaluator;
    @Mock private SalesPriceEvaluator salesPriceEvaluator;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void createMapsTenantAmountsAuditAndAuxiliaryLineFields() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(salesOrderNumberService.nextOrderNo(ORDER_DATE)).thenReturn("SO-20260822-001");
        doAnswer(invocation -> {
            SalesOrderEntity entity = invocation.getArgument(0);
            entity.setId(ORDER_ID);
            return 1;
        }).when(salesOrderMapper).insert(any(SalesOrderEntity.class));
        SalesOrderResponse expected = new SalesOrderResponse(ORDER_ID, "SO-20260822-001", CUSTOMER_ID,
                WAREHOUSE_ID, "Acme", ORDER_DATE, null, "DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED",
                new BigDecimal("2.0000"), new BigDecimal("20.00"), new BigDecimal("2.60"), "remark", List.of());
        when(queryService.toResponse(any(SalesOrderEntity.class), any(String.class), any())).thenReturn(expected);

        SalesOrderResponse result = service().create(new SalesOrderCreateRequest(
                CUSTOMER_ID, WAREHOUSE_ID, ORDER_DATE, null, "remark",
                List.of(new SalesOrderLineRequest(PRODUCT_ID, new BigDecimal("2"), new BigDecimal("1"),
                        "箱", new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("13"), "line"))));

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<SalesOrderEntity> orderCaptor = ArgumentCaptor.forClass(SalesOrderEntity.class);
        verify(salesOrderMapper).insert(orderCaptor.capture());
        SalesOrderEntity order = orderCaptor.getValue();
        assertThat(order.getId()).isEqualTo(ORDER_ID);
        assertThat(order.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(order.getAccountBookId()).isEqualTo(BOOK_ID);
        assertThat(order.getStatus()).isEqualTo("DRAFT");
        assertThat(order.getTotalQuantity()).isEqualByComparingTo("2.0000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getTotalTaxAmount()).isEqualByComparingTo("2.60");

        ArgumentCaptor<SalesOrderLineEntity> lineCaptor = ArgumentCaptor.forClass(SalesOrderLineEntity.class);
        verify(salesOrderLineMapper).insert(lineCaptor.capture());
        SalesOrderLineEntity line = lineCaptor.getValue();
        assertThat(line.getQty()).isEqualByComparingTo("2.0000");
        assertThat(line.getAuxQty()).isEqualByComparingTo("1.0000");
        assertThat(line.getAuxUnitName()).isEqualTo("箱");
        assertThat(line.getConversionFactor()).isEqualByComparingTo("2.0000");
        assertThat(line.getTaxRate()).isEqualByComparingTo("13.0000");
        assertThat(line.getAmount()).isEqualByComparingTo("20.00");
        assertThat(line.getTaxAmount()).isEqualByComparingTo("2.60");
        verify(productValidator).requireProducts(List.of(PRODUCT_ID), COMPANY_ID, BOOK_ID);
    }

    @Test
    void updateRejectsApprovedOrderBeforeAnyWrite() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order("APPROVED"));

        assertThatThrownBy(() -> service().update(ORDER_ID, updateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前销售订单状态不允许编辑");
        verifyNoInteractions(customerMapper, warehouseMapper, salesOrderMapper, salesOrderLineMapper);
    }

    @Test
    void updateReplacesLinesAndReturnsFreshDetail() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        SalesOrderEntity order = order("REJECTED");
        when(queryService.requireOrder(ORDER_ID)).thenReturn(order);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(salesOrderMapper.updateById(order)).thenReturn(1);
        SalesOrderResponse expected = new SalesOrderResponse(ORDER_ID, "SO-1", CUSTOMER_ID, WAREHOUSE_ID,
                "Acme", ORDER_DATE, null, "DRAFT", "NOT_SUBMITTED", "NOT_DELIVERED", null, null, null, null, List.of());
        when(queryService.getById(ORDER_ID)).thenReturn(expected);

        assertThat(service().update(ORDER_ID, updateRequest())).isSameAs(expected);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("10.00");
        verify(salesOrderLineMapper).delete(any());
        verify(salesOrderLineMapper).insert(any(SalesOrderLineEntity.class));
    }

    @Test
    void previewCreditNormalizesTotalsAndMapsEvaluatorResult() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer());
        CustomerEntity customer = customer();
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer);
        when(salesCreditEvaluator.preview(customer, new BigDecimal("22.60")))
                .thenReturn(new SalesCreditPreview(new BigDecimal("100.00"), new BigDecimal("10.00"),
                        new BigDecimal("20.00"), new BigDecimal("30.00"), new BigDecimal("22.60"),
                        new BigDecimal("52.60"), new BigDecimal("70.00"), new BigDecimal("47.40"), false, false));

        SalesOrderCreditPreviewResponse result = service().previewCredit(new SalesOrderCreditPreviewRequest(
                CUSTOMER_ID, List.of(new SalesOrderLineRequest(PRODUCT_ID, BigDecimal.ONE, new BigDecimal("20"),
                        new BigDecimal("13"), null))));

        assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.orderAmount()).isEqualByComparingTo("22.60");
        assertThat(result.projectedExposure()).isEqualByComparingTo("52.60");
        verify(salesCreditEvaluator).preview(customer, new BigDecimal("22.60"));
    }

    private SalesOrderCommandService service() {
        return new SalesOrderCommandService(salesOrderMapper, salesOrderLineMapper, customerMapper, productValidator,
                warehouseMapper, salesOrderNumberService, auditMetadataFactory, queryService,
                salesCreditEvaluator, salesPriceEvaluator);
    }

    private CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(CUSTOMER_ID);
        customer.setCompanyId(COMPANY_ID);
        customer.setAccountBookId(BOOK_ID);
        customer.setCustomerName("Acme");
        customer.setStatus("ACTIVE");
        customer.setDeletedFlag(0);
        return customer;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCompanyId(COMPANY_ID);
        warehouse.setAccountBookId(BOOK_ID);
        warehouse.setWarehouseName("Main");
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private SalesOrderEntity order(String status) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(ORDER_ID);
        order.setCompanyId(COMPANY_ID);
        order.setAccountBookId(BOOK_ID);
        order.setCustomerId(CUSTOMER_ID);
        order.setWarehouseId(WAREHOUSE_ID);
        order.setOrderDate(ORDER_DATE);
        order.setStatus(status);
        order.setDeletedFlag(0);
        order.setVersion(0);
        return order;
    }

    private SalesOrderUpdateRequest updateRequest() {
        return new SalesOrderUpdateRequest(CUSTOMER_ID, WAREHOUSE_ID, ORDER_DATE, null, "updated",
                List.of(new SalesOrderLineRequest(PRODUCT_ID, BigDecimal.ONE, BigDecimal.TEN,
                        BigDecimal.ZERO, "line")));
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

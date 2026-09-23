package com.tuowei.erp.masterdata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.service.SupplierPayableExposureService;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierPayableExposureServiceTest {

    @BeforeAll
    static void initTableInfo() {
        init(SupplierEntity.class);
        init(PayableEntity.class);
        init(PurchaseOrderEntity.class);
        init(PurchaseOrderLineEntity.class);
    }

    @Test
    void aggregatesOutstandingPayablesAndUnreceivedPurchaseOrders() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        PayableMapper payableMapper = mock(PayableMapper.class);
        PurchaseOrderMapper orderMapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderLineMapper lineMapper = mock(PurchaseOrderLineMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        when(auditFactory.current()).thenReturn(new AuditMetadata(9L, 1L, 2L, LocalDateTime.parse("2026-07-22T10:00:00")));
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(801L);
        when(supplierMapper.selectOne(any())).thenReturn(supplier);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(payableMapper.selectList(payableQuery.capture())).thenReturn(List.of(payable("1000", "400")));
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(901L);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(lineMapper.selectList(any())).thenReturn(List.of(line("10", "4", "1000", "100")));

        var response = new SupplierPayableExposureService(supplierMapper, payableMapper, orderMapper,
                lineMapper, auditFactory).exposure(801L);

        assertThat(response.outstandingPayable()).isEqualByComparingTo("600.00");
        assertThat(response.openPurchaseOrderAmount()).isEqualByComparingTo("660.00");
        assertThat(response.totalExposure()).isEqualByComparingTo("1260.00");
        LambdaQueryWrapper<PayableEntity> query = payableQuery.getValue();
        String payableSql = query.getSqlSegment() + query.getParamNameValuePairs().values();
        assertThat(payableSql)
                .contains("UNSETTLED")
                .contains("PARTIALLY_SETTLED")
                .doesNotContain("INCREASE");
    }

    @Test
    void convertsForeignBalancesAndLetsReturnsReduceExposure() {
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        PayableMapper payableMapper = mock(PayableMapper.class);
        PurchaseOrderMapper orderMapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderLineMapper lineMapper = mock(PurchaseOrderLineMapper.class);
        AuditMetadataFactory auditFactory = mock(AuditMetadataFactory.class);
        when(auditFactory.current()).thenReturn(new AuditMetadata(9L, 1L, 2L, LocalDateTime.parse("2026-07-22T10:00:00")));
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(801L);
        when(supplierMapper.selectOne(any())).thenReturn(supplier);

        PayableEntity partial = payable("100", "40");
        partial.setDirection("INCREASE");
        partial.setCurrencyCode("USD");
        partial.setExchangeRate(new BigDecimal("7"));
        partial.setBaseOriginalAmount(new BigDecimal("700"));
        partial.setBaseSettledAmount(new BigDecimal("280"));
        PayableEntity returned = payable("50", "0");
        returned.setDirection("DECREASE");
        returned.setCurrencyCode("USD");
        returned.setExchangeRate(new BigDecimal("7"));
        returned.setBaseOriginalAmount(new BigDecimal("350"));
        when(payableMapper.selectList(any())).thenReturn(List.of(partial, returned));

        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(901L);
        order.setExchangeRate(new BigDecimal("7"));
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        PurchaseOrderLineEntity openLine = line("10", "4", "100", "10");
        openLine.setOrderId(901L);
        when(lineMapper.selectList(any())).thenReturn(List.of(openLine));

        var response = new SupplierPayableExposureService(supplierMapper, payableMapper, orderMapper,
                lineMapper, auditFactory).exposure(801L);

        assertThat(response.outstandingPayable()).isEqualByComparingTo("70.00");
        assertThat(response.openPurchaseOrderAmount()).isEqualByComparingTo("462.00");
        assertThat(response.totalExposure()).isEqualByComparingTo("532.00");
    }

    private static PayableEntity payable(String original, String settled) {
        PayableEntity entity = new PayableEntity();
        entity.setOriginalAmount(new BigDecimal(original));
        entity.setSettledAmount(new BigDecimal(settled));
        return entity;
    }

    private static PurchaseOrderLineEntity line(String qty, String received, String amount, String tax) {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setQty(new BigDecimal(qty));
        entity.setReceivedQty(new BigDecimal(received));
        entity.setAmount(new BigDecimal(amount));
        entity.setTaxAmount(new BigDecimal(tax));
        return entity;
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
    }
}

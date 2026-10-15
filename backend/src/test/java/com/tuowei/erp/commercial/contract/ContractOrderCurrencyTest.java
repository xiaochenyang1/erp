package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ContractOrderCurrencyTest {
    @BeforeAll
    static void tables() {
        for (Class<?> type : List.of(ContractEntity.class, SalesOrderLineEntity.class, PurchaseOrderLineEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    @ParameterizedTest
    @CsvSource({"SALES,USD,CNY,false", "PURCHASE,USD,CNY,false", "SALES,,USD,false", "PURCHASE,,USD,false",
            "SALES,USD,USD,true", "PURCHASE,USD,USD,true", "SALES,,CNY,true", "PURCHASE,,CNY,true"})
    void agreedPricesCanOnlyBeUsedInTheContractCurrency(String type, String contractCurrency, String orderCurrency, boolean valid) {
        ContractMapper contracts = mock(ContractMapper.class);
        ContractQueryService query = mock(ContractQueryService.class);
        var service = new ContractOrderBindingService(contracts, query, mock(SalesOrderMapper.class),
                mock(SalesOrderLineMapper.class), mock(PurchaseOrderMapper.class), mock(PurchaseOrderLineMapper.class));
        ContractEntity contract = new ContractEntity();
        contract.setId(1L); contract.setContractType(type); contract.setCustomerId(2L); contract.setSupplierId(2L);
        contract.setCurrencyCode(contractCurrency); contract.setStatus("ACTIVE"); contract.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(query.requireContract(1L)).thenReturn(contract);
        when(query.baseCurrency(contract)).thenReturn("CNY");
        when(contracts.updateById(contract)).thenReturn(1);
        ContractLineEntity line = new ContractLineEntity();
        line.setId(3L); line.setProductId(4L); line.setQuantity(BigDecimal.TEN); line.setUnitPrice(BigDecimal.TEN);
        when(query.loadLines(contract)).thenReturn(List.of(line));
        var audit = new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 1, 2, 12, 0));
        org.assertj.core.api.ThrowableAssert.ThrowingCallable action = () -> {
            if ("SALES".equals(type)) {
                service.validateSales(1L, 2L, LocalDate.of(2026, 1, 2), List.of(new SalesOrderLineRequest(
                        3L, 4L, BigDecimal.ONE, null, null, null, BigDecimal.TEN, BigDecimal.ZERO, null)), null, audit, orderCurrency);
            } else {
                service.validatePurchase(1L, 2L, LocalDate.of(2026, 1, 2), List.of(new PurchaseOrderLineRequest(
                        3L, 4L, BigDecimal.ONE, null, null, null, BigDecimal.TEN, BigDecimal.ZERO, null)), null, audit, orderCurrency);
            }
        };
        if (valid) assertThatCode(action).doesNotThrowAnyException();
        else assertThatThrownBy(action).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("币种必须与合同");
    }
}

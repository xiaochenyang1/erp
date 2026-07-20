package com.tuowei.erp.finance.payable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayableQueryServiceExportTest {

    @Mock
    private PayableMapper payableMapper;

    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    private PayableQueryService service;

    @BeforeEach
    void setUp() {
        service = new PayableQueryService(payableMapper, financeSettlementScopeSupport);
    }

    @Test
    void exportPayablesWritesScopedCsvRows() throws Exception {
        when(financeSettlementScopeSupport.applyPayableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, LambdaQueryWrapper.class));
        when(payableMapper.selectList(any())).thenReturn(List.of(payable()));

        PayablePageQuery query = new PayablePageQuery();
        query.setSupplierId(6001L);
        query.setStatus("unsettled");
        query.setSourceType("purchase_order");
        query.setBizDateFrom(LocalDate.of(2026, 6, 1));
        query.setBizDateTo(LocalDate.of(2026, 6, 30));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportPayables(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFpayableNo,supplierId,bizDate,sourceType,sourceNo,direction,originalAmount,settledAmount,remainingAmount,status,remark\r\n");
        assertThat(csv).contains("AP-2026-001,6001,2026-06-18,PURCHASE_ORDER,PO-2026-001,INCREASE,200.00,80.00,120.00,UNSETTLED,export test\r\n");
        verify(financeSettlementScopeSupport).applyPayableScope(any());
        verify(payableMapper).selectList(any());
    }

    private static PayableEntity payable() {
        PayableEntity entity = new PayableEntity();
        entity.setId(1001L);
        entity.setPayableNo("AP-2026-001");
        entity.setSupplierId(6001L);
        entity.setBizDate(LocalDate.of(2026, 6, 18));
        entity.setSourceType("PURCHASE_ORDER");
        entity.setSourceId(9001L);
        entity.setSourceNo("PO-2026-001");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(new BigDecimal("200.00"));
        entity.setSettledAmount(new BigDecimal("80.00"));
        entity.setStatus("UNSETTLED");
        entity.setRemark("export test");
        return entity;
    }
}

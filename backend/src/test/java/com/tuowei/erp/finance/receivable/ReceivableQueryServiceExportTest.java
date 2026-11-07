package com.tuowei.erp.finance.receivable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
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
class ReceivableQueryServiceExportTest {

    @Mock
    private ReceivableMapper receivableMapper;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    private ReceivableQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReceivableQueryService(
                receivableMapper,
                customerMapper,
                financeSettlementScopeSupport,
                auditMetadataFactory
        );
    }

    @Test
    void exportReceivablesWritesScopedCsvRows() throws Exception {
        when(financeSettlementScopeSupport.applyReceivableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, LambdaQueryWrapper.class));
        when(receivableMapper.selectList(any())).thenReturn(List.of(receivable()));

        ReceivablePageQuery query = new ReceivablePageQuery();
        query.setCustomerId(5001L);
        query.setStatus("unsettled");
        query.setSourceType("sales_order");
        query.setBizDateFrom(LocalDate.of(2026, 5, 1));
        query.setBizDateTo(LocalDate.of(2026, 5, 31));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.exportReceivables(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFreceivableNo,customerId,bizDate,dueDate,sourceType,sourceNo,direction,originalAmount,settledAmount,remainingAmount,status,remark\r\n");
        assertThat(csv).contains("AR-2026-001,5001,2026-05-18,2026-05-18,SALES_ORDER,SO-2026-001,INCREASE,100.00,20.00,80.00,UNSETTLED,export test\r\n");
        verify(financeSettlementScopeSupport).applyReceivableScope(any());
        verify(receivableMapper).selectList(any());
    }

    private static ReceivableEntity receivable() {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(1001L);
        entity.setReceivableNo("AR-2026-001");
        entity.setCustomerId(5001L);
        entity.setBizDate(LocalDate.of(2026, 5, 18));
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(9001L);
        entity.setSourceNo("SO-2026-001");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(new BigDecimal("100.00"));
        entity.setSettledAmount(new BigDecimal("20.00"));
        entity.setStatus("UNSETTLED");
        entity.setRemark("export test");
        return entity;
    }
}

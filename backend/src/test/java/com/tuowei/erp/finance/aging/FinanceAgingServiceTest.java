package com.tuowei.erp.finance.aging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.aging.service.FinanceAgingService;
import com.tuowei.erp.finance.aging.web.FinanceAgingSummaryResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceAgingServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long BOOK_ID = 1L;

    @Mock
    private ReceivableMapper receivableMapper;
    @Mock
    private PayableMapper payableMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTable() {
        init(ReceivableEntity.class);
        init(PayableEntity.class);
        init(CustomerEntity.class);
        init(SupplierEntity.class);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), type.getName());
        assistant.setCurrentNamespace(type.getName());
        TableInfoHelper.initTableInfo(assistant, type);
    }

    @Test
    void bucketsReceivableByAgingDays() {
        LocalDate asOf = LocalDate.of(2026, 7, 17);
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(COMPANY_ID, BOOK_ID, 9L, LocalDateTime.now()));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                ar(1L, 11L, asOf.minusDays(10), "100.00", "0.00"),
                ar(2L, 11L, asOf.minusDays(45), "200.00", "50.00"),
                ar(3L, 12L, asOf.minusDays(100), "300.00", "0.00"),
                ar(4L, 12L, asOf.minusDays(5), "80.00", "80.00") // remaining 0 skip
        ));
        when(payableMapper.selectList(any())).thenReturn(List.of(
                ap(5L, 21L, asOf.minusDays(70), "400.00", "100.00")
        ));
        CustomerEntity c1 = new CustomerEntity();
        c1.setId(11L);
        c1.setCompanyId(COMPANY_ID);
        c1.setCustomerName("客户A");
        CustomerEntity c2 = new CustomerEntity();
        c2.setId(12L);
        c2.setCompanyId(COMPANY_ID);
        c2.setCustomerName("客户B");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c1, c2));
        SupplierEntity s1 = new SupplierEntity();
        s1.setId(21L);
        s1.setCompanyId(COMPANY_ID);
        s1.setSupplierName("供应商X");
        when(supplierMapper.selectBatchIds(any())).thenReturn(List.of(s1));

        FinanceAgingSummaryResponse summary = service().summary(asOf);

        assertThat(summary.receivableTotal()).isEqualByComparingTo("550.00"); // 100+150+300
        assertThat(summary.payableTotal()).isEqualByComparingTo("300.00");
        assertThat(summary.receivableBuckets()).extracting("code")
                .containsExactly("D0_30", "D31_60", "D61_90", "D90_PLUS");
        assertThat(summary.receivableBuckets().get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(summary.receivableBuckets().get(1).amount()).isEqualByComparingTo("150.00");
        assertThat(summary.receivableBuckets().get(3).amount()).isEqualByComparingTo("300.00");
        assertThat(summary.payableBuckets().get(2).amount()).isEqualByComparingTo("300.00");
        assertThat(summary.overdueReceivables()).isNotEmpty();
        assertThat(summary.overdueReceivables().get(0).bucketCode()).isEqualTo("D90_PLUS");
    }

    @Test
    void doesNotHydrateCounterpartyNameFromAnotherAccountBook() {
        LocalDate asOf = LocalDate.of(2026, 7, 17);
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                COMPANY_ID, BOOK_ID, 9L, LocalDateTime.now()));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                ar(10L, 11L, asOf.minusDays(5), "100.00", "0.00")
        ));
        when(payableMapper.selectList(any())).thenReturn(List.of());
        CustomerEntity foreign = new CustomerEntity();
        foreign.setId(11L);
        foreign.setCompanyId(COMPANY_ID);
        foreign.setAccountBookId(999L);
        foreign.setCustomerName("其他账套客户");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(foreign));

        FinanceAgingSummaryResponse summary = service().summary(asOf);

        assertThat(summary.overdueReceivables()).isNotEmpty();
        assertThat(summary.overdueReceivables().get(0).partnerName()).isNull();
    }

    private ReceivableEntity ar(Long id, Long customerId, LocalDate bizDate, String original, String settled) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setReceivableNo("AR" + id);
        entity.setCustomerId(customerId);
        entity.setBizDate(bizDate);
        entity.setOriginalAmount(new BigDecimal(original));
        entity.setSettledAmount(new BigDecimal(settled));
        entity.setStatus("UNSETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PayableEntity ap(Long id, Long supplierId, LocalDate bizDate, String original, String settled) {
        PayableEntity entity = new PayableEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(BOOK_ID);
        entity.setPayableNo("AP" + id);
        entity.setSupplierId(supplierId);
        entity.setBizDate(bizDate);
        entity.setOriginalAmount(new BigDecimal(original));
        entity.setSettledAmount(new BigDecimal(settled));
        entity.setStatus("UNSETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private FinanceAgingService service() {
        return new FinanceAgingService(
                receivableMapper,
                payableMapper,
                customerMapper,
                supplierMapper,
                auditMetadataFactory
        );
    }
}

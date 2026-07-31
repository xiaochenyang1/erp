package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class FinanceSubledgerPostingServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            901L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 31, 15, 0)
    );

    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final FinanceSubledgerPostingService service = new FinanceSubledgerPostingService(
            payableMapper,
            receivableMapper,
            customerMapper,
            supplierMapper
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayableEntity.class);
        initTableInfo(ReceivableEntity.class);
    }

    @Test
    void recordsReceivableWithTenantScopedDeduplicationAndCustomerCreditPeriod() {
        when(receivableMapper.selectCount(any())).thenReturn(0L);
        CustomerEntity customer = new CustomerEntity();
        customer.setId(301L);
        customer.setCompanyId(AUDIT.companyId());
        customer.setAccountBookId(AUDIT.accountBookId());
        customer.setCreditPeriod(30);
        when(customerMapper.selectById(301L)).thenReturn(customer);

        service.recordReceivableIfAbsent(
                "SALES_DELIVERY",
                401L,
                "SD-401",
                "INCREASE",
                301L,
                LocalDate.of(2026, 7, 31),
                new BigDecimal("113.00"),
                "sales delivery",
                AUDIT
        );

        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectCount(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());

        ArgumentCaptor<ReceivableEntity> entityCaptor = ArgumentCaptor.forClass(ReceivableEntity.class);
        verify(receivableMapper).insert(entityCaptor.capture());
        ReceivableEntity entity = entityCaptor.getValue();
        assertThat(entity.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(entity.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(entity.getReceivableNo()).isEqualTo("AR-SALES_DELIVERY-401");
        assertThat(entity.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(entity.getStatus()).isEqualTo("UNSETTLED");
        assertThat(entity.getSettledAmount()).isEqualByComparingTo("0.00");
        assertThat(entity.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entity.getCreatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void ignoresCrossTenantSupplierCreditPeriodWhenRecordingPayableOffset() {
        when(payableMapper.selectCount(any())).thenReturn(0L);
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(302L);
        supplier.setCompanyId(999L);
        supplier.setAccountBookId(AUDIT.accountBookId());
        supplier.setCreditPeriod(90);
        when(supplierMapper.selectById(302L)).thenReturn(supplier);

        service.recordPayableIfAbsent(
                "PURCHASE_RETURN",
                402L,
                "PR-402",
                "DECREASE",
                302L,
                LocalDate.of(2026, 7, 31),
                new BigDecimal("22.60"),
                "purchase return",
                AUDIT
        );

        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectCount(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());

        ArgumentCaptor<PayableEntity> entityCaptor = ArgumentCaptor.forClass(PayableEntity.class);
        verify(payableMapper).insert(entityCaptor.capture());
        PayableEntity entity = entityCaptor.getValue();
        assertThat(entity.getPayableNo()).isEqualTo("AP-PURCHASE_RETURN-402");
        assertThat(entity.getDueDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(entity.getStatus()).isEqualTo("OFFSET");
        assertThat(entity.getOriginalAmount()).isEqualByComparingTo("22.60");
    }

    @Test
    void skipsMasterdataReadsAndWritesWhenSourceAlreadyPosted() {
        when(payableMapper.selectCount(any())).thenReturn(1L);
        when(receivableMapper.selectCount(any())).thenReturn(1L);

        service.recordPayableIfAbsent(
                "PURCHASE_RECEIPT", 403L, "PR-403", "INCREASE", 303L,
                LocalDate.of(2026, 7, 31), BigDecimal.TEN, "purchase receipt", AUDIT
        );
        service.recordReceivableIfAbsent(
                "SALES_DELIVERY", 404L, "SD-404", "INCREASE", 304L,
                LocalDate.of(2026, 7, 31), BigDecimal.TEN, "sales delivery", AUDIT
        );

        verify(payableMapper, never()).insert(any(PayableEntity.class));
        verify(receivableMapper, never()).insert(any(ReceivableEntity.class));
        verifyNoInteractions(customerMapper, supplierMapper);
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("source_type")
                .contains("source_id");
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

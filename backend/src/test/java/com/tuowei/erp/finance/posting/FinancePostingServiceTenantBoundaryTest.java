package com.tuowei.erp.finance.posting;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class FinancePostingServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9942L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 22, 30)
    );

    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final VoucherEntryMapper voucherEntryMapper = mock(VoucherEntryMapper.class);
    private final AccountSubjectMapper accountSubjectMapper = mock(AccountSubjectMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayableEntity.class);
        initTableInfo(ReceivableEntity.class);
        initTableInfo(VoucherEntity.class);
        initTableInfo(VoucherEntryEntity.class);
        initTableInfo(AccountSubjectEntity.class);
    }

    @Test
    void salesDeliveryPostingScopesDeduplicationAndSubjectLookupsByCompanyAndAccountBook() {
        when(receivableMapper.selectCount(any())).thenReturn(0L);
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(invocation -> {
            VoucherEntity voucher = invocation.getArgument(0);
            voucher.setId(7101L);
            return 1;
        });
        when(voucherEntryMapper.selectCount(any())).thenReturn(0L);
        when(voucherEntryMapper.selectList(any())).thenReturn(List.of());
        when(accountSubjectMapper.selectOne(any())).thenReturn(activeSubject());

        service().recordSalesDelivery(salesDelivery(), salesOrder(), new BigDecimal("45.00"), AUDIT);

        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> receivableWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectCount(receivableWrapper.capture());
        assertTenantScoped(receivableWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<VoucherEntity>> voucherWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherMapper).selectOne(voucherWrapper.capture());
        assertTenantScoped(voucherWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> voucherEntryCountWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper, atLeastOnce()).selectCount(voucherEntryCountWrapper.capture());
        assertThat(voucherEntryCountWrapper.getAllValues()).allSatisfy(this::assertTenantScoped);

        ArgumentCaptor<LambdaQueryWrapper<VoucherEntryEntity>> voucherEntryListWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(voucherEntryMapper).selectList(voucherEntryListWrapper.capture());
        assertTenantScoped(voucherEntryListWrapper.getValue());

        ArgumentCaptor<LambdaQueryWrapper<AccountSubjectEntity>> subjectWrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(accountSubjectMapper, atLeastOnce()).selectOne(subjectWrapper.capture());
        assertThat(subjectWrapper.getAllValues()).allSatisfy(this::assertTenantScoped);
    }

    private FinancePostingService service() {
        return new FinancePostingService(
                new FinanceSubledgerPostingService(
                        payableMapper,
                        receivableMapper,
                        customerMapper,
                        supplierMapper
                ),
                voucherMapper,
                voucherEntryMapper,
                accountSubjectMapper
        );
    }

    private SalesDeliveryEntity salesDelivery() {
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(7201L);
        delivery.setDeliveryNo("SD-7201");
        delivery.setDeliveryDate(LocalDate.of(2026, 6, 8));
        delivery.setTotalAmount(new BigDecimal("100.00"));
        delivery.setTotalTaxAmount(BigDecimal.ZERO);
        return delivery;
    }

    private SalesOrderEntity salesOrder() {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(7301L);
        order.setCustomerId(7401L);
        return order;
    }

    private AccountSubjectEntity activeSubject() {
        AccountSubjectEntity subject = new AccountSubjectEntity();
        subject.setId(7501L);
        subject.setCompanyId(AUDIT.companyId());
        subject.setAccountBookId(AUDIT.accountBookId());
        subject.setSubjectCode("1001");
        subject.setSubjectName("tenant subject");
        subject.setStatus("ACTIVE");
        subject.setDeletedFlag(0);
        return subject;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id");
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

package com.tuowei.erp.finance.statement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.statement.service.PartnerStatementService;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerStatementServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1L, 101L, 202L, LocalDateTime.of(2026, 9, 3, 10, 0)
    );

    private final ReceivableMapper receivableMapper = mock(ReceivableMapper.class);
    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    private PartnerStatementService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ReceiptEntity.class);
        initTableInfo(PaymentEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        service = new PartnerStatementService(
                receivableMapper,
                payableMapper,
                receiptMapper,
                paymentMapper,
                customerMapper,
                supplierMapper,
                auditMetadataFactory
        );
    }

    @Test
    void customerFromAnotherAccountBookIsNotVisible() {
        when(customerMapper.selectById(11L)).thenReturn(customer(11L, AUDIT.companyId(), 999L, 0));

        assertThatThrownBy(() -> service.statement("CUSTOMER", 11L, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");

        verify(receivableMapper, never()).selectList(any());
        verify(receiptMapper, never()).selectList(any());
    }

    @Test
    void deletedCustomerIsNotVisible() {
        when(customerMapper.selectById(12L)).thenReturn(customer(12L, AUDIT.companyId(), AUDIT.accountBookId(), 1));

        assertThatThrownBy(() -> service.statement("CUSTOMER", 12L, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户不存在");

        verify(receivableMapper, never()).selectList(any());
        verify(receiptMapper, never()).selectList(any());
    }

    @Test
    void supplierFromAnotherAccountBookIsNotVisible() {
        when(supplierMapper.selectById(21L)).thenReturn(supplier(21L, AUDIT.companyId(), 999L, 0));

        assertThatThrownBy(() -> service.statement("SUPPLIER", 21L, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");

        verify(payableMapper, never()).selectList(any());
        verify(paymentMapper, never()).selectList(any());
    }

    @Test
    void deletedSupplierIsNotVisible() {
        when(supplierMapper.selectById(22L)).thenReturn(supplier(22L, AUDIT.companyId(), AUDIT.accountBookId(), 1));

        assertThatThrownBy(() -> service.statement("SUPPLIER", 22L, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在");

        verify(payableMapper, never()).selectList(any());
        verify(paymentMapper, never()).selectList(any());
    }

    @Test
    void postedSettlementsAreScopedToActiveRowsInCurrentAccountBook() {
        when(customerMapper.selectById(31L)).thenReturn(customer(31L, AUDIT.companyId(), AUDIT.accountBookId(), 0));
        when(supplierMapper.selectById(41L)).thenReturn(supplier(41L, AUDIT.companyId(), AUDIT.accountBookId(), 0));
        when(receivableMapper.selectList(any())).thenReturn(List.of());
        when(receiptMapper.selectList(any())).thenReturn(List.of());
        when(payableMapper.selectList(any())).thenReturn(List.of());
        when(paymentMapper.selectList(any())).thenReturn(List.of());

        service.statement("CUSTOMER", 31L, FROM, TO);
        service.statement("SUPPLIER", 41L, FROM, TO);

        ArgumentCaptor<LambdaQueryWrapper<ReceiptEntity>> receiptCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receiptMapper).selectList(receiptCaptor.capture());
        assertThat(receiptCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "status");
        assertThat(receiptCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0, "POSTED");

        ArgumentCaptor<LambdaQueryWrapper<PaymentEntity>> paymentCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paymentMapper).selectList(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "status");
        assertThat(paymentCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0, "POSTED");
    }

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private CustomerEntity customer(Long id, Long companyId, Long accountBookId, int deletedFlag) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setDeletedFlag(deletedFlag);
        return entity;
    }

    private SupplierEntity supplier(Long id, Long companyId, Long accountBookId, int deletedFlag) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setDeletedFlag(deletedFlag);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

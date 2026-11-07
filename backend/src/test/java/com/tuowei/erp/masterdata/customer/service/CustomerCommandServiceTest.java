package com.tuowei.erp.masterdata.customer.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.customer.web.CustomerCreateRequest;
import com.tuowei.erp.masterdata.customer.web.CustomerResponse;
import com.tuowei.erp.masterdata.customer.web.CustomerUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7201L,
            8201L,
            9201L,
            LocalDateTime.of(2026, 8, 20, 16, 30)
    );
    private static final Long CUSTOMER_ID = 201L;

    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private CustomerQueryService customerQueryService;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantCustomerDefaultsStatusAndSetsAuditFields() {
        when(customerMapper.insert(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity entity = invocation.getArgument(0);
            entity.setId(CUSTOMER_ID);
            return 1;
        });
        CustomerResponse expected = response("ACTIVE", null);
        when(customerQueryService.toResponse(any(CustomerEntity.class))).thenReturn(expected);

        CustomerResponse actual = service().create(createRequest(null, null));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<CustomerEntity> entityCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerMapper).insert(entityCaptor.capture());
        CustomerEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getCustomerCode()).isEqualTo("C-001");
        assertThat(inserted.getCustomerName()).isEqualTo("华东客户");
        assertThat(inserted.getCustomerType()).isEqualTo("COMPANY");
        assertThat(inserted.getSettlementMethod()).isEqualTo("MONTHLY");
        assertThat(inserted.getCreditLimit()).isEqualByComparingTo("10000.00");
        assertThat(inserted.getCreditPeriod()).isNull();
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void createRejectsNegativeCreditPeriodBeforeWriting() {
        assertThatThrownBy(() -> service().create(createRequest(-1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("creditPeriod不能小于0");

        verify(customerMapper, never()).insert(any(CustomerEntity.class));
        verify(customerQueryService, never()).toResponse(any(CustomerEntity.class));
    }

    @Test
    void updateChangesMutableFieldsPreservesCodeAndBlankStatus() {
        CustomerEntity existing = customer("ACTIVE");
        when(customerQueryService.requireCustomer(CUSTOMER_ID)).thenReturn(existing);
        when(customerMapper.updateById(existing)).thenReturn(1);
        CustomerResponse expected = response("ACTIVE", 45);
        when(customerQueryService.toResponse(existing)).thenReturn(expected);

        CustomerResponse actual = service().update(CUSTOMER_ID, updateRequest("   ", 45));

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getCustomerCode()).isEqualTo("C-001");
        assertThat(existing.getCustomerName()).isEqualTo("华东客户更新");
        assertThat(existing.getCustomerType()).isEqualTo("INDIVIDUAL");
        assertThat(existing.getContactName()).isEqualTo("李经理");
        assertThat(existing.getContactPhone()).isEqualTo("13900000002");
        assertThat(existing.getEmail()).isEqualTo("updated@example.com");
        assertThat(existing.getSettlementMethod()).isEqualTo("PREPAID");
        assertThat(existing.getCreditLimit()).isEqualByComparingTo("20000.00");
        assertThat(existing.getCreditPeriod()).isEqualTo(45);
        assertThat(existing.getAddress()).isEqualTo("杭州");
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getRemark()).isEqualTo("updated");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(customerMapper).updateById(existing);
    }

    @Test
    void updateSurfacesOptimisticLockConflict() {
        CustomerEntity existing = customer("ACTIVE");
        when(customerQueryService.requireCustomer(CUSTOMER_ID)).thenReturn(existing);
        when(customerMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().update(CUSTOMER_ID, updateRequest("ACTIVE", 30)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("客户已被其他操作修改，请刷新后重试");

        verify(customerQueryService, never()).toResponse(any(CustomerEntity.class));
    }

    @Test
    void enableUpdatesStatusAndAuditFields() {
        CustomerEntity existing = customer("INACTIVE");
        when(customerQueryService.requireCustomer(CUSTOMER_ID)).thenReturn(existing);
        when(customerMapper.updateById(existing)).thenReturn(1);
        CustomerResponse expected = response("ACTIVE", 30);
        when(customerQueryService.toResponse(existing)).thenReturn(expected);

        CustomerResponse actual = service().enable(CUSTOMER_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void disableSetsInactiveBeforeSurfacingOptimisticLockConflict() {
        CustomerEntity existing = customer("ACTIVE");
        when(customerQueryService.requireCustomer(CUSTOMER_ID)).thenReturn(existing);
        when(customerMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(CUSTOMER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("客户已被其他操作修改，请刷新后重试");

        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        verify(customerQueryService, never()).toResponse(any(CustomerEntity.class));
    }

    private CustomerCommandService service() {
        return new CustomerCommandService(customerMapper, auditMetadataFactory, customerQueryService);
    }

    private CustomerCreateRequest createRequest(Integer creditPeriod, String status) {
        return new CustomerCreateRequest(
                "C-001",
                "华东客户",
                "COMPANY",
                "王经理",
                "13800000001",
                "customer@example.com",
                "MONTHLY",
                new BigDecimal("10000.00"),
                creditPeriod,
                "上海",
                status,
                "command test"
        );
    }

    private CustomerUpdateRequest updateRequest(String status, Integer creditPeriod) {
        return new CustomerUpdateRequest(
                "华东客户更新",
                "INDIVIDUAL",
                "李经理",
                "13900000002",
                "updated@example.com",
                "PREPAID",
                new BigDecimal("20000.00"),
                creditPeriod,
                "杭州",
                status,
                "updated"
        );
    }

    private CustomerEntity customer(String status) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(CUSTOMER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCustomerCode("C-001");
        entity.setCustomerName("华东客户");
        entity.setCustomerType("COMPANY");
        entity.setContactName("王经理");
        entity.setContactPhone("13800000001");
        entity.setEmail("customer@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditLimit(new BigDecimal("10000.00"));
        entity.setCreditPeriod(30);
        entity.setAddress("上海");
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setRemark("command test");
        entity.setVersion(0);
        return entity;
    }

    private CustomerResponse response(String status, Integer creditPeriod) {
        return new CustomerResponse(
                CUSTOMER_ID,
                "C-001",
                "华东客户",
                "COMPANY",
                "王经理",
                "13800000001",
                "customer@example.com",
                "MONTHLY",
                new BigDecimal("10000.00"),
                creditPeriod,
                "上海",
                status,
                "command test"
        );
    }
}

package com.tuowei.erp.masterdata.supplier.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.masterdata.supplier.web.SupplierCreateRequest;
import com.tuowei.erp.masterdata.supplier.web.SupplierResponse;
import com.tuowei.erp.masterdata.supplier.web.SupplierUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7401L,
            8401L,
            9401L,
            LocalDateTime.of(2026, 8, 20, 17, 30)
    );
    private static final Long SUPPLIER_ID = 401L;

    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private SupplierQueryService supplierQueryService;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantSupplierDefaultsStatusAndPreservesNegativeCreditPeriod() {
        when(supplierMapper.insert(any(SupplierEntity.class))).thenAnswer(invocation -> {
            SupplierEntity entity = invocation.getArgument(0);
            entity.setId(SUPPLIER_ID);
            return 1;
        });
        SupplierResponse expected = response("ACTIVE", -15);
        when(supplierQueryService.toResponse(any(SupplierEntity.class))).thenReturn(expected);

        SupplierResponse actual = service().create(createRequest(-15, null));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<SupplierEntity> entityCaptor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(supplierMapper).insert(entityCaptor.capture());
        SupplierEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getSupplierCode()).isEqualTo("S-001");
        assertThat(inserted.getSupplierName()).isEqualTo("华东供应商");
        assertThat(inserted.getEmail()).isEqualTo("supplier@example.com");
        assertThat(inserted.getSettlementMethod()).isEqualTo("MONTHLY");
        assertThat(inserted.getCreditPeriod()).isEqualTo(-15);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void updateChangesMutableFieldsPreservesCodeAndAllowsNegativeCreditPeriod() {
        SupplierEntity existing = supplier("ACTIVE", 30);
        when(supplierQueryService.requireSupplier(SUPPLIER_ID)).thenReturn(existing);
        when(supplierMapper.updateById(existing)).thenReturn(1);
        SupplierResponse expected = response("ACTIVE", -3);
        when(supplierQueryService.toResponse(existing)).thenReturn(expected);

        SupplierResponse actual = service().update(SUPPLIER_ID, updateRequest("   ", -3));

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getSupplierCode()).isEqualTo("S-001");
        assertThat(existing.getSupplierName()).isEqualTo("华东供应商更新");
        assertThat(existing.getContactName()).isEqualTo("李经理");
        assertThat(existing.getContactPhone()).isEqualTo("13900000002");
        assertThat(existing.getEmail()).isEqualTo("updated@example.com");
        assertThat(existing.getSettlementMethod()).isEqualTo("PREPAID");
        assertThat(existing.getCreditPeriod()).isEqualTo(-3);
        assertThat(existing.getAddress()).isEqualTo("杭州");
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getRemark()).isEqualTo("updated");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(supplierMapper).updateById(existing);
    }

    @Test
    void updateSurfacesOptimisticLockConflict() {
        SupplierEntity existing = supplier("ACTIVE", 30);
        when(supplierQueryService.requireSupplier(SUPPLIER_ID)).thenReturn(existing);
        when(supplierMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().update(SUPPLIER_ID, updateRequest("ACTIVE", 30)))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("供应商已被其他操作修改，请刷新后重试");

        verify(supplierQueryService, never()).toResponse(any(SupplierEntity.class));
    }

    @Test
    void enableUpdatesStatusAndAuditFields() {
        SupplierEntity existing = supplier("INACTIVE", 30);
        when(supplierQueryService.requireSupplier(SUPPLIER_ID)).thenReturn(existing);
        when(supplierMapper.updateById(existing)).thenReturn(1);
        SupplierResponse expected = response("ACTIVE", 30);
        when(supplierQueryService.toResponse(existing)).thenReturn(expected);

        SupplierResponse actual = service().enable(SUPPLIER_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void disableSetsInactiveBeforeSurfacingOptimisticLockConflict() {
        SupplierEntity existing = supplier("ACTIVE", 30);
        when(supplierQueryService.requireSupplier(SUPPLIER_ID)).thenReturn(existing);
        when(supplierMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().disable(SUPPLIER_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("供应商已被其他操作修改，请刷新后重试");

        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        verify(supplierQueryService, never()).toResponse(any(SupplierEntity.class));
    }

    private SupplierCommandService service() {
        return new SupplierCommandService(supplierMapper, auditMetadataFactory, supplierQueryService);
    }

    private SupplierCreateRequest createRequest(Integer creditPeriod, String status) {
        return new SupplierCreateRequest(
                "S-001",
                "华东供应商",
                "赵经理",
                "13900000001",
                "supplier@example.com",
                "MONTHLY",
                creditPeriod,
                "苏州",
                status,
                "command test"
        );
    }

    private SupplierUpdateRequest updateRequest(String status, Integer creditPeriod) {
        return new SupplierUpdateRequest(
                "华东供应商更新",
                "李经理",
                "13900000002",
                "updated@example.com",
                "PREPAID",
                creditPeriod,
                "杭州",
                status,
                "updated"
        );
    }

    private SupplierEntity supplier(String status, Integer creditPeriod) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(SUPPLIER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setSupplierCode("S-001");
        entity.setSupplierName("华东供应商");
        entity.setContactName("赵经理");
        entity.setContactPhone("13900000001");
        entity.setEmail("supplier@example.com");
        entity.setSettlementMethod("MONTHLY");
        entity.setCreditPeriod(creditPeriod);
        entity.setAddress("苏州");
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setRemark("command test");
        entity.setVersion(0);
        return entity;
    }

    private SupplierResponse response(String status, Integer creditPeriod) {
        return new SupplierResponse(
                SUPPLIER_ID,
                "S-001",
                "华东供应商",
                "赵经理",
                "13900000001",
                "supplier@example.com",
                "MONTHLY",
                creditPeriod,
                "苏州",
                status,
                "command test"
        );
    }
}

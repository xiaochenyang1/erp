package com.tuowei.erp.commercial.contract.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.web.ContractLineRequest;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractSaveRequest;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

class ContractCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(9L, 1L, 2L,
            LocalDateTime.of(2026, 8, 26, 10, 0));

    private ContractMapper contractMapper;
    private ContractLineMapper contractLineMapper;
    private ContractNumberService contractNumberService;
    private ContractQueryService queryService;
    private CustomerMapper customerMapper;
    private SupplierMapper supplierMapper;
    private ProductValidator productValidator;
    private AuditMetadataFactory auditMetadataFactory;
    private ContractCommandService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ContractEntity.class);
        initTableInfo(ContractLineEntity.class);
    }

    @BeforeEach
    void setUp() {
        contractMapper = mock(ContractMapper.class);
        contractLineMapper = mock(ContractLineMapper.class);
        contractNumberService = mock(ContractNumberService.class);
        queryService = mock(ContractQueryService.class);
        customerMapper = mock(CustomerMapper.class);
        supplierMapper = mock(SupplierMapper.class);
        productValidator = mock(ProductValidator.class);
        auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(contractNumberService.nextContractNo(any())).thenReturn("CT20260826-0001");
        when(customerMapper.selectById(101L)).thenReturn(customer(101L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE"));
        when(supplierMapper.selectById(201L)).thenReturn(supplier(201L, AUDIT.companyId(), AUDIT.accountBookId(), "ACTIVE"));
        service = new ContractCommandService(contractMapper, contractLineMapper, contractNumberService,
                queryService, customerMapper, supplierMapper, productValidator, auditMetadataFactory);
    }

    @Test
    void createsSalesContractWithTenantFieldsTotalsAndLineDefaults() {
        when(contractMapper.insert(any(ContractEntity.class))).thenAnswer(invocation -> {
            ((ContractEntity) invocation.getArgument(0)).setId(1001L);
            return 1;
        });
        when(queryService.detail(1001L)).thenReturn(response("DRAFT", "200.00"));

        ContractResponse result = service.create(request("SALES", 101L, null,
                List.of(line(7001L, "2", "100"))));

        ArgumentCaptor<ContractEntity> header = ArgumentCaptor.forClass(ContractEntity.class);
        verify(contractMapper).insert(header.capture());
        assertThat(header.getValue().getCompanyId()).isEqualTo(1L);
        assertThat(header.getValue().getAccountBookId()).isEqualTo(2L);
        assertThat(header.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(header.getValue().getTotalAmount()).isEqualByComparingTo("200.00");

        ArgumentCaptor<ContractLineEntity> line = ArgumentCaptor.forClass(ContractLineEntity.class);
        verify(contractLineMapper).insert(line.capture());
        assertThat(line.getValue().getContractId()).isEqualTo(1001L);
        assertThat(line.getValue().getFulfilledQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getValue().getAmount()).isEqualByComparingTo("200.00");
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsPurchaseContractWhenSupplierIsCrossTenantOrInactive() {
        when(supplierMapper.selectById(201L)).thenReturn(supplier(201L, 999L, AUDIT.accountBookId(), "ACTIVE"));

        assertThatThrownBy(() -> service.create(request("PURCHASE", null, 201L,
                List.of(line(7001L, "1", "5")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或已停用");
        verify(contractMapper, never()).insert(any(ContractEntity.class));
        verify(contractLineMapper, never()).insert(any(ContractLineEntity.class));
    }

    @Test
    void transitionsSubmittedContractToActiveAndRejectsInvalidState() {
        ContractEntity entity = contract("SUBMITTED");
        when(queryService.requireContract(1001L)).thenReturn(entity);
        when(contractMapper.updateById(any(ContractEntity.class))).thenReturn(1);
        when(queryService.detail(1001L)).thenReturn(response("ACTIVE", "0"));

        assertThat(service.approve(1001L).status()).isEqualTo("ACTIVE");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        entity.setStatus("DRAFT");
        assertThatThrownBy(() -> service.approve(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅已提交合同可审批生效");
    }

    @Test
    void updateDeletesExistingLinesBeforeWritingReplacementLines() {
        ContractEntity entity = contract("REJECTED");
        when(queryService.requireContract(1001L)).thenReturn(entity);
        when(contractMapper.updateById(any(ContractEntity.class))).thenReturn(1);
        when(queryService.detail(1001L)).thenReturn(response("REJECTED", "30.00"));

        service.update(1001L, request("SALES", 101L, null,
                List.of(line(7002L, "3", "10"))));

        verify(contractLineMapper).delete(any());
        verify(contractLineMapper).insert(any(ContractLineEntity.class));
        InOrder order = inOrder(contractLineMapper);
        order.verify(contractLineMapper).delete(any());
        order.verify(contractLineMapper).insert(any(ContractLineEntity.class));
    }

    @Test
    void optimisticLockConflictPreventsTransition() {
        when(queryService.requireContract(1001L)).thenReturn(contract("SUBMITTED"));
        when(contractMapper.updateById(any(ContractEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service.approve(1001L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("已被修改");
    }

    private ContractSaveRequest request(String type, Long customerId, Long supplierId, List<ContractLineRequest> lines) {
        return new ContractSaveRequest(type, customerId, supplierId, "测试合同",
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 12, 31), "备注", lines);
    }

    private ContractLineRequest line(Long productId, String quantity, String price) {
        return new ContractLineRequest(productId, new BigDecimal(quantity), new BigDecimal(price), null);
    }

    private ContractEntity contract(String status) {
        ContractEntity entity = new ContractEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setContractType("SALES");
        entity.setCustomerId(101L);
        entity.setContractName("测试合同");
        entity.setSignedDate(LocalDate.of(2026, 8, 26));
        entity.setEffectiveFrom(LocalDate.of(2026, 8, 26));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private CustomerEntity customer(Long id, Long companyId, Long bookId, String status) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id); entity.setCompanyId(companyId); entity.setAccountBookId(bookId);
        entity.setStatus(status); entity.setDeletedFlag(0); return entity;
    }

    private SupplierEntity supplier(Long id, Long companyId, Long bookId, String status) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(id); entity.setCompanyId(companyId); entity.setAccountBookId(bookId);
        entity.setStatus(status); entity.setDeletedFlag(0); return entity;
    }

    private ContractResponse response(String status, String total) {
        return new ContractResponse(1001L, "CT20260826-0001", "SALES", 101L, "客户", null, null,
                "测试合同", LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 12, 31), status, new BigDecimal(total), "备注", List.of());
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

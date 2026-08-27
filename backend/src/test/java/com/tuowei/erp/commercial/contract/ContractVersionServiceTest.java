package com.tuowei.erp.commercial.contract;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractVersionMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.model.ContractVersionEntity;
import com.tuowei.erp.commercial.contract.service.ContractNumberService;
import com.tuowei.erp.commercial.contract.service.ContractQueryService;
import com.tuowei.erp.commercial.contract.service.ContractVersionService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractVersionServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(9L, 1L, 2L, LocalDateTime.of(2026, 8, 26, 10, 0));
    private ContractVersionMapper versionMapper;
    private ContractMapper contractMapper;
    private ContractLineMapper lineMapper;
    private ContractQueryService queryService;
    private ContractNumberService numberService;
    private AuditMetadataFactory auditMetadataFactory;
    private ContractVersionService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ContractVersionEntity.class);
        initTableInfo(ContractEntity.class);
        initTableInfo(ContractLineEntity.class);
    }

    @BeforeEach
    void setUp() {
        versionMapper = mock(ContractVersionMapper.class);
        contractMapper = mock(ContractMapper.class);
        lineMapper = mock(ContractLineMapper.class);
        queryService = mock(ContractQueryService.class);
        numberService = mock(ContractNumberService.class);
        auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        service = new ContractVersionService(versionMapper, contractMapper, lineMapper, queryService, numberService,
                auditMetadataFactory, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void recordsImmutableHeaderAndLineSnapshotsWithIncrementingVersion() {
        ContractEntity contract = contract("DRAFT");
        ContractLineEntity line = line(7001L, "2", "10");
        when(versionMapper.selectOne(any())).thenReturn(null);
        service.record(contract, List.of(line), "CREATED");
        var captor = org.mockito.ArgumentCaptor.forClass(ContractVersionEntity.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(captor.getValue().getEventType()).isEqualTo("CREATED");
        assertThat(captor.getValue().getContractSnapshotJson()).contains("测试合同");
        assertThat(captor.getValue().getLineSnapshotJson()).contains("7001");
    }

    private ContractEntity contract(String status) {
        ContractEntity entity = new ContractEntity();
        entity.setId(1001L); entity.setCompanyId(1L); entity.setAccountBookId(2L); entity.setContractNo("CT-1");
        entity.setContractType("SALES"); entity.setCustomerId(101L); entity.setContractName("测试合同");
        entity.setSignedDate(LocalDate.of(2026, 8, 26)); entity.setEffectiveFrom(LocalDate.of(2026, 8, 26));
        entity.setStatus(status); entity.setTotalAmount(new BigDecimal("20.00")); entity.setRemark("备注");
        return entity;
    }

    private ContractLineEntity line(Long productId, String qty, String price) {
        ContractLineEntity line = new ContractLineEntity(); line.setLineNo(1); line.setProductId(productId);
        line.setQuantity(new BigDecimal(qty)); line.setFulfilledQuantity(BigDecimal.ZERO); line.setUnitPrice(new BigDecimal(price));
        line.setAmount(new BigDecimal(qty).multiply(new BigDecimal(price))); return line;
    }

    private static void initTableInfo(Class<?> type) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "contract-version-test"), type);
    }
}

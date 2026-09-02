package com.tuowei.erp.commercial.contract;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.service.ContractAlertService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.system.notification.mapper.NotificationMapper;
import com.tuowei.erp.system.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractAlertServiceTest {
    private final ContractMapper contractMapper = mock(ContractMapper.class);
    private final ContractLineMapper lineMapper = mock(ContractLineMapper.class);
    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    @Test
    void createsExpiringAndLowExecutionAlertsOnce() {
        ContractEntity contract = contract("1001", "2026-09-10");
        ContractLineEntity line = new ContractLineEntity();
        line.setCompanyId(1L); line.setAccountBookId(2L); line.setContractId(1001L);
        line.setQuantity(new BigDecimal("100")); line.setFulfilledQuantity(new BigDecimal("20")); line.setDeletedFlag(0);
        when(contractMapper.selectList(any())).thenReturn(List.of(contract));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(notificationMapper.selectCount(any())).thenReturn(0L);

        ContractAlertService service = new ContractAlertService(contractMapper, lineMapper, notificationMapper, notificationService);
        int created = service.scan(new AuditMetadata(9L, 1L, 2L, LocalDateTime.of(2026, 8, 26, 10, 0)),
                LocalDate.of(2026, 8, 26), 30, new BigDecimal("0.5"));

        org.assertj.core.api.Assertions.assertThat(created).isEqualTo(2);
        verify(notificationService, times(2)).createBusinessNotification(anyString(), anyString(), anyString(), anyString(),
                eq("COMMERCIAL_CONTRACT"), eq(1001L), eq("CT1001"), eq("/contracts"), eq(List.of(7L)), any(), any());
    }

    @Test
    void ignoresExpiredContractsAndExistingAlerts() {
        ContractEntity contract = contract("1002", "2026-08-20");
        when(contractMapper.selectList(any())).thenReturn(List.of(contract));
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        ContractAlertService service = new ContractAlertService(contractMapper, lineMapper, notificationMapper, notificationService);
        int created = service.scan(new AuditMetadata(9L, 1L, 2L, LocalDateTime.now()),
                LocalDate.of(2026, 8, 26), 30, new BigDecimal("0.5"));

        org.assertj.core.api.Assertions.assertThat(created).isZero();
        verifyNoInteractions(lineMapper, notificationService);
    }

    private ContractEntity contract(String id, String effectiveTo) {
        ContractEntity contract = new ContractEntity();
        contract.setId(Long.valueOf(id)); contract.setCompanyId(1L); contract.setAccountBookId(2L);
        contract.setContractNo("CT" + id); contract.setContractName("合同 " + id); contract.setContractType("SALES");
        contract.setEffectiveTo(LocalDate.parse(effectiveTo)); contract.setStatus("ACTIVE"); contract.setDeletedFlag(0); contract.setCreatedBy(7L);
        return contract;
    }
}

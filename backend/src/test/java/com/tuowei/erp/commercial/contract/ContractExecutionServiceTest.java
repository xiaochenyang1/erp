package com.tuowei.erp.commercial.contract;

import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.service.ContractExecutionService;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractExecutionServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(9L, 1L, 2L, LocalDateTime.of(2026, 8, 26, 10, 0));
    private final ContractLineMapper mapper = mock(ContractLineMapper.class);

    @Test
    void increasesAndDecreasesFulfilledQuantityWithinTenant() {
        ContractLineEntity line = line("10.0000", "2.0000");
        when(mapper.selectById(7001L)).thenReturn(line);
        when(mapper.updateById(any(ContractLineEntity.class))).thenReturn(1);
        ContractExecutionService service = new ContractExecutionService(mapper);

        service.increase(7001L, new BigDecimal("3"), AUDIT);
        assertThat(line.getFulfilledQuantity()).isEqualByComparingTo("5.0000");
        service.decrease(7001L, new BigDecimal("1"), AUDIT);
        assertThat(line.getFulfilledQuantity()).isEqualByComparingTo("4.0000");
        verify(mapper, times(2)).updateById(any(ContractLineEntity.class));
    }

    @Test
    void rejectsCrossTenantAndOverFulfillmentWithoutWrite() {
        ContractLineEntity line = line("2.0000", "1.0000");
        line.setCompanyId(99L);
        when(mapper.selectById(7001L)).thenReturn(line);
        ContractExecutionService service = new ContractExecutionService(mapper);
        assertThatThrownBy(() -> service.increase(7001L, new BigDecimal("1"), AUDIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("合同明细不存在");
        verify(mapper, never()).updateById(any(ContractLineEntity.class));

        line.setCompanyId(1L);
        when(mapper.selectById(7001L)).thenReturn(line);
        assertThatThrownBy(() -> service.increase(7001L, new BigDecimal("2"), AUDIT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("合同履约数量不能超过合同数量");
        verify(mapper, never()).updateById(any(ContractLineEntity.class));
    }

    @Test
    void optimisticLockConflictIsRaised() {
        when(mapper.selectById(7001L)).thenReturn(line("10.0000", "1.0000"));
        when(mapper.updateById(any(ContractLineEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> new ContractExecutionService(mapper).increase(7001L, new BigDecimal("1"), AUDIT))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("已被其他操作修改");
    }

    private ContractLineEntity line(String qty, String fulfilled) {
        ContractLineEntity line = new ContractLineEntity();
        line.setId(7001L); line.setCompanyId(1L); line.setAccountBookId(2L); line.setContractId(1001L);
        line.setQuantity(new BigDecimal(qty)); line.setFulfilledQuantity(new BigDecimal(fulfilled));
        line.setDeletedFlag(0); line.setVersion(0); return line;
    }
}

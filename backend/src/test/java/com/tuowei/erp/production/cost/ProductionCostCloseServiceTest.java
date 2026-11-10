package com.tuowei.erp.production.cost;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionMapper;
import com.tuowei.erp.production.completion.model.ProductionCompletionEntity;
import com.tuowei.erp.production.cost.mapper.ProductionCostCloseMapper;
import com.tuowei.erp.production.cost.mapper.ProductionCostEntryMapper;
import com.tuowei.erp.production.cost.model.ProductionCostCloseEntity;
import com.tuowei.erp.production.cost.service.ProductionCostCloseService;
import com.tuowei.erp.production.cost.web.ProductionCostCloseRequest;
import com.tuowei.erp.production.cost.web.ProductionCostReopenRequest;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionCostCloseServiceTest {
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 11, 8);
    private static final AuditMetadata AUDIT = new AuditMetadata(
            7L, 1L, 1L, LocalDateTime.of(2026, 11, 8, 10, 0)
    );

    private final ProductionCostCloseMapper closeMapper = mock(ProductionCostCloseMapper.class);
    private final ProductionCostEntryMapper costEntryMapper = mock(ProductionCostEntryMapper.class);
    private final ProductionCompletionMapper completionMapper = mock(ProductionCompletionMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderService orderService = mock(ProductionOrderService.class);
    private final AccountPeriodGuard accountPeriodGuard = mock(AccountPeriodGuard.class);
    private final FinancePostingService financePostingService = mock(FinancePostingService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final SequenceNumberGenerator sequenceNumberGenerator = mock(SequenceNumberGenerator.class);
    private final ProductionCostCloseService service = new ProductionCostCloseService(
            closeMapper, costEntryMapper, completionMapper, orderMapper, orderService,
            accountPeriodGuard, financePostingService, auditMetadataFactory, sequenceNumberGenerator
    );

    @BeforeEach
    void setup() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(sequenceNumberGenerator.nextNumber("PRODUCTION_COST_CLOSE", "生产成本关单", BIZ_DATE))
                .thenReturn("PCC202611080001", "PCC202611080002");
        when(orderMapper.updateById(any(ProductionOrderEntity.class))).thenReturn(1);
        when(costEntryMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of());
        when(completionMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(completion(BIZ_DATE.minusDays(1))));
        AtomicLong id = new AtomicLong(9000L);
        doAnswer(invocation -> {
            ProductionCostCloseEntity entry = invocation.getArgument(0);
            entry.setId(id.incrementAndGet());
            return 1;
        }).when(closeMapper).insert(any(ProductionCostCloseEntity.class));
        when(closeMapper.updateById(any(ProductionCostCloseEntity.class))).thenReturn(1);
    }

    @Test
    void closesPositiveVarianceAndLocksTheOrder() {
        ProductionOrderEntity order = order("COMPLETED", "OPEN");
        order.setIssuedAmount(new BigDecimal("50.00"));
        order.setLaborAmount(new BigDecimal("30.00"));
        order.setFinishedMaterialAmount(new BigDecimal("50.00"));
        order.setFinishedLaborAmount(new BigDecimal("25.00"));
        mockLockedOrder(order);

        var response = service.close(1001L, new ProductionCostCloseRequest(BIZ_DATE, " month close "));

        assertThat(response.amount()).isEqualByComparingTo("5.00");
        assertThat(response.remark()).isEqualTo("month close");
        assertThat(order.getCostCloseStatus()).isEqualTo("CLOSED");
        assertThat(order.getSettledVarianceAmount()).isEqualByComparingTo("5.00");
        assertThat(order.getCostCloseEntryId()).isEqualTo(9001L);
        verify(accountPeriodGuard).requireOpen(BIZ_DATE, "生产成本关单");
        verify(financePostingService).recordProductionCostClose(
                order, 9001L, "PCC202611080001", new BigDecimal("5.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void closesNegativeVarianceWithTheSignedSettlement() {
        ProductionOrderEntity order = order("COMPLETED", "OPEN");
        order.setIssuedAmount(new BigDecimal("40.00"));
        order.setFinishedMaterialAmount(new BigDecimal("50.00"));
        mockLockedOrder(order);

        var response = service.close(1001L, new ProductionCostCloseRequest(BIZ_DATE, null));

        assertThat(response.amount()).isEqualByComparingTo("-10.00");
        assertThat(order.getSettledVarianceAmount()).isEqualByComparingTo("-10.00");
        verify(financePostingService).recordProductionCostClose(
                order, 9001L, "PCC202611080001", new BigDecimal("-10.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void zeroVarianceStillCreatesAnAuditableClose() {
        ProductionOrderEntity order = order("COMPLETED", "OPEN");
        order.setIssuedAmount(new BigDecimal("50.00"));
        order.setFinishedMaterialAmount(new BigDecimal("50.00"));
        mockLockedOrder(order);

        var response = service.close(1001L, new ProductionCostCloseRequest(BIZ_DATE, null));

        assertThat(response.amount()).isEqualByComparingTo("0.00");
        assertThat(order.getCostCloseStatus()).isEqualTo("CLOSED");
        verify(financePostingService).recordProductionCostClose(
                order, 9001L, "PCC202611080001", new BigDecimal("0.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void reopeningCreatesANegativeAuditEntryAndUnlocksTheOrder() {
        ProductionOrderEntity order = order("COMPLETED", "CLOSED");
        order.setSettledVarianceAmount(new BigDecimal("5.00"));
        order.setCostCloseEntryId(8101L);
        mockLockedOrder(order);
        ProductionCostCloseEntity original = closeEntry(8101L, new BigDecimal("5.00"));
        when(closeMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(original);
        when(closeMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

        var response = service.reopen(1001L, new ProductionCostReopenRequest(BIZ_DATE, "late payroll"));

        assertThat(original.getStatus()).isEqualTo("REVERSED");
        assertThat(response.entryKind()).isEqualTo("REVERSAL");
        assertThat(response.amount()).isEqualByComparingTo("-5.00");
        assertThat(order.getCostCloseStatus()).isEqualTo("OPEN");
        assertThat(order.getSettledVarianceAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getCostCloseEntryId()).isNull();
        verify(financePostingService).recordProductionCostCloseReversal(
                order, 9001L, "PCC202611080001", new BigDecimal("-5.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void rejectsOrdersThatAreNotCompleted() {
        ProductionOrderEntity order = order("MATERIAL_ISSUED", "OPEN");
        mockLockedOrder(order);

        assertThatThrownBy(() -> service.close(
                1001L, new ProductionCostCloseRequest(BIZ_DATE, null)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("只有已完工");

        verifyNoInteractions(accountPeriodGuard, financePostingService);
    }

    @Test
    void closeDateCannotPrecedeTheLatestCostActivity() {
        ProductionOrderEntity order = order("COMPLETED", "OPEN");
        mockLockedOrder(order);
        var latest = new com.tuowei.erp.production.cost.model.ProductionCostEntryEntity();
        latest.setBusinessDate(BIZ_DATE);
        when(costEntryMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(latest));

        assertThatThrownBy(() -> service.close(
                1001L, new ProductionCostCloseRequest(BIZ_DATE.minusDays(1), null)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能早于最近");

        verifyNoInteractions(accountPeriodGuard, financePostingService);
    }

    private void mockLockedOrder(ProductionOrderEntity order) {
        when(orderService.requireOrder(1001L)).thenReturn(order);
        when(orderMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(order);
    }

    private ProductionOrderEntity order(String status, String closeStatus) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(1001L);
        order.setCompanyId(1L);
        order.setAccountBookId(1L);
        order.setStatus(status);
        order.setIssuedAmount(BigDecimal.ZERO);
        order.setLaborAmount(BigDecimal.ZERO);
        order.setOverheadAmount(BigDecimal.ZERO);
        order.setFinishedMaterialAmount(BigDecimal.ZERO);
        order.setFinishedLaborAmount(BigDecimal.ZERO);
        order.setFinishedOverheadAmount(BigDecimal.ZERO);
        order.setSettledVarianceAmount(BigDecimal.ZERO);
        order.setCostCloseStatus(closeStatus);
        order.setVersion(0);
        return order;
    }

    private ProductionCompletionEntity completion(LocalDate date) {
        ProductionCompletionEntity completion = new ProductionCompletionEntity();
        completion.setCompletionDate(date);
        return completion;
    }

    private ProductionCostCloseEntity closeEntry(Long id, BigDecimal amount) {
        ProductionCostCloseEntity entry = new ProductionCostCloseEntity();
        entry.setId(id);
        entry.setCompanyId(1L);
        entry.setAccountBookId(1L);
        entry.setOrderId(1001L);
        entry.setCloseNo("PCC-OLD");
        entry.setBusinessDate(BIZ_DATE.minusDays(1));
        entry.setAmount(amount);
        entry.setStatus("POSTED");
        entry.setDeletedFlag(0);
        entry.setVersion(0);
        return entry;
    }
}

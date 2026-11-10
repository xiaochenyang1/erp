package com.tuowei.erp.production.cost;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.production.cost.mapper.ProductionCostEntryMapper;
import com.tuowei.erp.production.cost.model.ProductionCostEntryEntity;
import com.tuowei.erp.production.cost.service.ProductionCostEntryService;
import com.tuowei.erp.production.cost.web.ProductionCostEntryCorrectionRequest;
import com.tuowei.erp.production.cost.web.ProductionCostEntryRequest;
import com.tuowei.erp.production.cost.web.ProductionCostEntryReversalRequest;
import com.tuowei.erp.production.operation.mapper.ProductionOrderOperationMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionCostEntryServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(
            7L, 1L, 1L, LocalDateTime.of(2026, 11, 7, 10, 0)
    );
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 11, 7);

    private final ProductionCostEntryMapper costEntryMapper = mock(ProductionCostEntryMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderOperationMapper operationMapper = mock(ProductionOrderOperationMapper.class);
    private final ProductionOrderService orderService = mock(ProductionOrderService.class);
    private final AccountPeriodGuard accountPeriodGuard = mock(AccountPeriodGuard.class);
    private final FinancePostingService financePostingService = mock(FinancePostingService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final SequenceNumberGenerator sequenceNumberGenerator = mock(SequenceNumberGenerator.class);
    private final ProductionCostEntryService service = new ProductionCostEntryService(
            costEntryMapper, orderMapper, operationMapper, orderService, accountPeriodGuard,
            financePostingService, auditMetadataFactory, sequenceNumberGenerator
    );

    @BeforeEach
    void setup() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(sequenceNumberGenerator.nextNumber("PRODUCTION_COST_ENTRY", "生产成本归集单", BIZ_DATE))
                .thenReturn("PCE202611070001", "PCE202611070002", "PCE202611070003");
        when(orderMapper.updateById(any(ProductionOrderEntity.class))).thenReturn(1);
        when(costEntryMapper.updateById(any(ProductionCostEntryEntity.class))).thenReturn(1);
        AtomicLong entryId = new AtomicLong(9000L);
        doAnswer(invocation -> {
            ProductionCostEntryEntity entry = invocation.getArgument(0);
            entry.setId(entryId.incrementAndGet());
            return 1;
        }).when(costEntryMapper).insert(any(ProductionCostEntryEntity.class));
    }

    @Test
    void postsLaborToTheOrderAndFinanceInOneCommand() {
        ProductionOrderEntity order = order("MATERIAL_ISSUED");
        order.setLaborAmount(new BigDecimal("25.00"));
        when(orderService.requireOrder(1001L)).thenReturn(order);

        var response = service.post(1001L, new ProductionCostEntryRequest(
                "LABOR", null, BIZ_DATE, new BigDecimal("75.126"), " payroll "
        ));

        assertThat(response.id()).isEqualTo(9001L);
        assertThat(response.amount()).isEqualByComparingTo("75.13");
        assertThat(response.remark()).isEqualTo("payroll");
        assertThat(order.getLaborAmount()).isEqualByComparingTo("100.13");
        verify(accountPeriodGuard).requireOpen(BIZ_DATE, "生产成本归集");
        verify(financePostingService).recordProductionCostEntry(
                order, 9001L, "PCE202611070001", "LABOR", new BigDecimal("75.13"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void completedOrderAcceptsLateOverheadForVarianceReporting() {
        ProductionOrderEntity order = order("COMPLETED");
        when(orderService.requireOrder(1002L)).thenReturn(order);

        service.post(1002L, new ProductionCostEntryRequest(
                "OVERHEAD", null, BIZ_DATE, new BigDecimal("20.00"), null
        ));

        assertThat(order.getOverheadAmount()).isEqualByComparingTo("20.00");
        verify(financePostingService).recordProductionCostEntry(
                order, 9001L, "PCE202611070001", "OVERHEAD", new BigDecimal("20.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void draftOrderCannotReceiveCosts() {
        when(orderService.requireOrder(1003L)).thenReturn(order("DRAFT"));

        assertThatThrownBy(() -> service.post(1003L, new ProductionCostEntryRequest(
                "LABOR", null, BIZ_DATE, new BigDecimal("10.00"), null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能归集成本");

        verifyNoInteractions(accountPeriodGuard, financePostingService);
    }

    @Test
    void reversesPostedLaborWithANegativeAuditEntryAndReverseVoucher() {
        ProductionOrderEntity order = order("MATERIAL_ISSUED");
        order.setLaborAmount(new BigDecimal("100.00"));
        ProductionCostEntryEntity original = entry(8101L, "PCE-OLD", "LABOR", new BigDecimal("75.00"));
        when(orderService.requireOrder(1001L)).thenReturn(order);
        when(costEntryMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(original);
        when(costEntryMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

        var response = service.reverse(1001L, 8101L, new ProductionCostEntryReversalRequest(
                BIZ_DATE, "payroll imported twice"
        ));

        assertThat(original.getStatus()).isEqualTo("REVERSED");
        assertThat(order.getLaborAmount()).isEqualByComparingTo("25.00");
        assertThat(response.entryKind()).isEqualTo("REVERSAL");
        assertThat(response.amount()).isEqualByComparingTo("-75.00");
        assertThat(response.reversalOfEntryId()).isEqualTo(8101L);
        assertThat(response.reversalOfCostNo()).isEqualTo("PCE-OLD");
        verify(accountPeriodGuard).requireOpen(BIZ_DATE, "生产成本归集冲销");
        verify(financePostingService).recordProductionCostEntryReversal(
                order, 9001L, "PCE202611070001", "LABOR", new BigDecimal("75.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void correctionAtomicallyReversesTheOriginalAndPostsTheReplacement() {
        ProductionOrderEntity order = order("COMPLETED");
        order.setLaborAmount(new BigDecimal("75.00"));
        order.setOverheadAmount(new BigDecimal("10.00"));
        ProductionCostEntryEntity original = entry(8201L, "PCE-OLD", "LABOR", new BigDecimal("75.00"));
        when(orderService.requireOrder(1001L)).thenReturn(order);
        when(costEntryMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(original);
        when(costEntryMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

        var response = service.correct(1001L, 8201L, new ProductionCostEntryCorrectionRequest(
                "OVERHEAD", null, BIZ_DATE, new BigDecimal("80.00"),
                "wrong cost category", "corrected allocation"
        ));

        assertThat(original.getStatus()).isEqualTo("REVERSED");
        assertThat(order.getLaborAmount()).isEqualByComparingTo("0.00");
        assertThat(order.getOverheadAmount()).isEqualByComparingTo("90.00");
        assertThat(response.entryKind()).isEqualTo("CORRECTION");
        assertThat(response.correctionOfEntryId()).isEqualTo(8201L);
        assertThat(response.amount()).isEqualByComparingTo("80.00");
        verify(accountPeriodGuard).requireOpen(BIZ_DATE, "生产成本归集更正");
        verify(financePostingService).recordProductionCostEntryReversal(
                order, 9001L, "PCE202611070001", "LABOR", new BigDecimal("75.00"), BIZ_DATE, AUDIT
        );
        verify(financePostingService).recordProductionCostEntry(
                order, 9002L, "PCE202611070002", "OVERHEAD", new BigDecimal("80.00"), BIZ_DATE, AUDIT
        );
    }

    @Test
    void alreadyReversedEntryCannotBeReversedAgain() {
        ProductionOrderEntity order = order("MATERIAL_ISSUED");
        ProductionCostEntryEntity original = entry(8301L, "PCE-OLD", "LABOR", new BigDecimal("10.00"));
        original.setStatus("REVERSED");
        when(orderService.requireOrder(1001L)).thenReturn(order);
        when(costEntryMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(original);

        assertThatThrownBy(() -> service.reverse(1001L, 8301L, new ProductionCostEntryReversalRequest(
                BIZ_DATE, "duplicate reverse"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("仅未冲销");

        verifyNoInteractions(accountPeriodGuard, financePostingService);
    }

    @Test
    void reversalDateCannotPrecedeTheOriginalBusinessDate() {
        ProductionOrderEntity order = order("MATERIAL_ISSUED");
        ProductionCostEntryEntity original = entry(8401L, "PCE-OLD", "LABOR", new BigDecimal("10.00"));
        original.setBusinessDate(BIZ_DATE);
        when(orderService.requireOrder(1001L)).thenReturn(order);
        when(costEntryMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(original);
        when(costEntryMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.reverse(1001L, 8401L, new ProductionCostEntryReversalRequest(
                BIZ_DATE.minusDays(1), "wrong date"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能早于原归集日期");

        verifyNoInteractions(accountPeriodGuard, financePostingService);
    }

    private ProductionOrderEntity order(String status) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(1001L);
        order.setCompanyId(1L);
        order.setAccountBookId(1L);
        order.setStatus(status);
        order.setLaborAmount(BigDecimal.ZERO);
        order.setOverheadAmount(BigDecimal.ZERO);
        order.setVersion(0);
        return order;
    }

    private ProductionCostEntryEntity entry(Long id, String costNo, String costType, BigDecimal amount) {
        ProductionCostEntryEntity entry = new ProductionCostEntryEntity();
        entry.setId(id);
        entry.setCompanyId(1L);
        entry.setAccountBookId(1L);
        entry.setOrderId(1001L);
        entry.setCostNo(costNo);
        entry.setCostType(costType);
        entry.setBusinessDate(BIZ_DATE.minusDays(1));
        entry.setAmount(amount);
        entry.setStatus("POSTED");
        entry.setDeletedFlag(0);
        entry.setVersion(0);
        return entry;
    }
}

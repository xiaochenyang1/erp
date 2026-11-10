package com.tuowei.erp.production.cost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.production.cost.web.ProductionCostCloseResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchRequest;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.system.log.service.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionCostPeriodCloseServiceTest {
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 30);
    private static final AuditMetadata AUDIT = new AuditMetadata(
            7L, 1L, 1L, LocalDateTime.of(2026, 11, 30, 18, 0)
    );

    private final AccountPeriodMapper periodMapper = mock(AccountPeriodMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionOrderQueryService orderQueryService = mock(ProductionOrderQueryService.class);
    private final ProductionCostCloseService closeService = mock(ProductionCostCloseService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final SystemLogService systemLogService = mock(SystemLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ProductionCostPeriodCloseService service = new ProductionCostPeriodCloseService(
            periodMapper,
            orderMapper,
            orderQueryService,
            closeService,
            auditMetadataFactory,
            currentUserContext,
            systemLogService,
            objectMapper
    );

    private AccountPeriodEntity period;
    private ProductionOrderEntity first;
    private ProductionOrderEntity second;

    @BeforeEach
    void setup() {
        period = new AccountPeriodEntity();
        period.setId(6001L);
        period.setCompanyId(1L);
        period.setAccountBookId(1L);
        period.setPeriodMonth("2026-11");
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(END_DATE);
        period.setStatus("OPEN");
        first = order(1001L, "MO001", "5.00");
        second = order(1002L, "MO002", "-2.00");

        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(currentUserContext.requireCurrentUser()).thenReturn(new CurrentUser(
                7L, 1L, 1L, 10L, 20L, "finance", "Finance User"
        ));
        when(periodMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(period);
        when(orderMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(first, second));
        doNothing().when(orderQueryService).assertCanView(any(ProductionOrderEntity.class));
        when(closeService.latestCompletionDate(first)).thenReturn(LocalDate.of(2026, 11, 20));
        when(closeService.latestActivityDate(first)).thenReturn(LocalDate.of(2026, 11, 25));
        when(closeService.unsettledVariance(first)).thenReturn(new BigDecimal("5.00"));
        when(closeService.latestCompletionDate(second)).thenReturn(LocalDate.of(2026, 11, 22));
        when(closeService.latestActivityDate(second)).thenReturn(LocalDate.of(2026, 11, 26));
        when(closeService.unsettledVariance(second)).thenReturn(new BigDecimal("-2.00"));
    }

    @Test
    void listsTenantPeriodsInMonthOrder() {
        AccountPeriodEntity later = new AccountPeriodEntity();
        later.setId(6002L);
        later.setPeriodYear(2026);
        later.setPeriodMonth("2026-12");
        later.setStartDate(LocalDate.of(2026, 12, 1));
        later.setEndDate(LocalDate.of(2026, 12, 31));
        later.setStatus("OPEN");
        when(periodMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(period, later));

        var periods = service.listPeriods();

        assertThat(periods).extracting("periodMonth").containsExactly("2026-11", "2026-12");
    }

    @Test
    void previewsAllUnclosedCompletedOrdersThroughThePeriodEnd() {
        var preview = service.preview(period.getId());

        assertThat(preview.candidateCount()).isEqualTo(2);
        assertThat(preview.positiveVarianceAmount()).isEqualByComparingTo("5.00");
        assertThat(preview.negativeVarianceAmount()).isEqualByComparingTo("-2.00");
        assertThat(preview.netVarianceAmount()).isEqualByComparingTo("3.00");
        assertThat(preview.items()).extracting("orderNo").containsExactly("MO001", "MO002");
    }

    @Test
    void excludesOrdersWithActivityAfterThePeriodCutoff() {
        when(closeService.latestActivityDate(second)).thenReturn(END_DATE.plusDays(1));

        var preview = service.preview(period.getId());

        assertThat(preview.items()).extracting("orderNo").containsExactly("MO001");
    }

    @Test
    void excludesOrdersOutsideTheCurrentUsersDataScope() {
        doThrow(new AccessDeniedException("forbidden")).when(orderQueryService).assertCanView(second);

        var preview = service.preview(period.getId());

        assertThat(preview.items()).extracting("orderNo").containsExactly("MO001");
    }

    @Test
    void preservesSuccessfulClosesAndReturnsPerOrderFailuresWithAuditEvidence() {
        when(closeService.close(eq(1001L), any())).thenReturn(new ProductionCostCloseResponse(
                9001L, "PCC001", 1001L, END_DATE, new BigDecimal("5.00"), "POSTED", "CLOSE",
                null, null, null, null, null, "period close", AUDIT.now()
        ));
        when(closeService.close(eq(1002L), any())).thenThrow(new BusinessConflictException("工单已被关单"));

        var response = service.closeBatch(
                period.getId(),
                new ProductionCostPeriodCloseBatchRequest(List.of(1001L, 1002L, 9999L), " period close ")
        );

        assertThat(response.requestedCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(2);
        assertThat(response.closedVarianceAmount()).isEqualByComparingTo("5.00");
        assertThat(response.results()).extracting("errorCode")
                .containsExactly(null, "CONFLICT", "NOT_IN_PERIOD");

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(systemLogService).recordAudit(
                eq("BUSINESS"),
                eq("PRODUCTION_COST_PERIOD_CLOSE"),
                eq(6001L),
                eq("2026-11"),
                eq("BATCH_CLOSE"),
                eq(7L),
                eq("finance"),
                snapshot.capture(),
                eq("期间批量关单：成功 1 张，失败 2 张，结转差异 5.00"),
                eq(AUDIT.now())
        );
        assertThat(snapshot.getValue()).contains("PCC001").contains("NOT_IN_PERIOD");
    }

    private ProductionOrderEntity order(Long id, String orderNo, String variance) {
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setId(id);
        order.setCompanyId(1L);
        order.setAccountBookId(1L);
        order.setOrderNo(orderNo);
        order.setProductId(id + 1000L);
        order.setIssuedAmount(new BigDecimal("50.00"));
        order.setLaborAmount(new BigDecimal(variance).max(BigDecimal.ZERO));
        order.setOverheadAmount(BigDecimal.ZERO);
        order.setFinishedMaterialAmount(new BigDecimal("50.00"));
        order.setFinishedLaborAmount(new BigDecimal(variance).negate().max(BigDecimal.ZERO));
        order.setFinishedOverheadAmount(BigDecimal.ZERO);
        order.setSettledVarianceAmount(BigDecimal.ZERO);
        return order;
    }
}

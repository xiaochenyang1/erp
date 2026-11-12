package com.tuowei.erp.finance.ledger;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.ledger.mapper.TrialBalanceMapper;
import com.tuowei.erp.finance.ledger.model.TrialBalanceAggregate;
import com.tuowei.erp.finance.ledger.service.TrialBalanceService;
import com.tuowei.erp.finance.ledger.web.TrialBalanceQuery;
import com.tuowei.erp.finance.ledger.web.TrialBalanceResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrialBalanceServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L, 101L, 202L, LocalDateTime.of(2026, 11, 10, 10, 0)
    );

    private final TrialBalanceMapper mapper = mock(TrialBalanceMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @Test
    void buildsOpeningPeriodAndClosingBalancesAndChecksReconciliation() {
        LocalDate dateFrom = LocalDate.of(2026, 10, 1);
        LocalDate dateTo = LocalDate.of(2026, 10, 31);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.aggregate(101L, 202L, dateFrom, dateTo)).thenReturn(List.of(
                aggregate("1002", "银行存款", "ASSET", "DEBIT", "100.00", "0", "50", "20"),
                aggregate("2202", "应付账款", "LIABILITY", "CREDIT", "0", "100.00", "20", "50")
        ));

        TrialBalanceQuery query = query(dateFrom, dateTo);
        TrialBalanceResponse response = service().trialBalance(query);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows().get(0).openingDebit()).isEqualByComparingTo("100.00");
        assertThat(response.rows().get(0).closingDebit()).isEqualByComparingTo("130.00");
        assertThat(response.rows().get(1).openingCredit()).isEqualByComparingTo("100.00");
        assertThat(response.rows().get(1).closingCredit()).isEqualByComparingTo("130.00");
        assertThat(response.openingBalanced()).isTrue();
        assertThat(response.periodBalanced()).isTrue();
        assertThat(response.closingBalanced()).isTrue();
        verify(mapper).aggregate(101L, 202L, dateFrom, dateTo);
    }

    @Test
    void preservesAbnormalReverseBalanceOnTheActualSide() {
        LocalDate dateFrom = LocalDate.of(2026, 10, 1);
        LocalDate dateTo = LocalDate.of(2026, 10, 31);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.aggregate(101L, 202L, dateFrom, dateTo)).thenReturn(List.of(
                aggregate("1002", "银行存款", "ASSET", "DEBIT", "0", "20", "0", "5")
        ));

        TrialBalanceResponse response = service().trialBalance(query(dateFrom, dateTo));

        assertThat(response.rows().get(0).closingDebit()).isEqualByComparingTo("0.00");
        assertThat(response.rows().get(0).closingCredit()).isEqualByComparingTo("25.00");
        assertThat(response.closingBalanced()).isFalse();
    }

    @Test
    void rejectsMissingOrReversedDateRange() {
        assertThatThrownBy(() -> service().trialBalance(new TrialBalanceQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("试算平衡日期范围不能为空");
        assertThatThrownBy(() -> service().trialBalance(query(
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 1)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("试算平衡开始日期不能晚于结束日期");
    }

    private TrialBalanceService service() {
        return new TrialBalanceService(mapper, auditMetadataFactory);
    }

    private TrialBalanceQuery query(LocalDate dateFrom, LocalDate dateTo) {
        TrialBalanceQuery query = new TrialBalanceQuery();
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        return query;
    }

    private TrialBalanceAggregate aggregate(
            String code,
            String name,
            String type,
            String direction,
            String openingDebit,
            String openingCredit,
            String periodDebit,
            String periodCredit
    ) {
        return new TrialBalanceAggregate(
                code,
                name,
                type,
                direction,
                new BigDecimal(openingDebit),
                new BigDecimal(openingCredit),
                new BigDecimal(periodDebit),
                new BigDecimal(periodCredit)
        );
    }
}

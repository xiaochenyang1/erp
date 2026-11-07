package com.tuowei.erp.finance.period;

import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationAssemblyService;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationQueryService;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryFinanceReconciliationAssemblyServiceTest {

    private final InventoryFinanceReconciliationAssemblyService service =
            new InventoryFinanceReconciliationAssemblyService();

    @Test
    void summaryRoundsDifferenceAndReportsBalance() {
        var response = service.assembleSummary(new InventoryFinanceReconciliationQueryService.SummaryData(
                period(), new BigDecimal("100.005"), new BigDecimal("100.004")));

        assertThat(response.differenceAmount()).isEqualByComparingTo("0.00");
        assertThat(response.balanced()).isTrue();
    }

    @Test
    void differencesClassifyAllMismatchTypesAndExcludeBalancedSources() {
        var data = new InventoryFinanceReconciliationQueryService.DifferenceData(
                period(),
                List.of(
                        inventory("RECEIPT", "MATCHED", "10.00"),
                        inventory("DELIVERY", "INVENTORY-ONLY", "-20.00"),
                        inventory("ADJUSTMENT", "MISMATCH", "8.00")
                ),
                List.of(
                        finance("RECEIPT", "MATCHED", "10.00"),
                        finance("PAYABLE", "FINANCE-ONLY", "15.00"),
                        finance("ADJUSTMENT", "MISMATCH", "5.00")
                )
        );

        var responses = service.assembleDifferences(data, null);

        assertThat(responses).hasSize(3);
        assertThat(responses).extracting("differenceType")
                .containsExactly("INVENTORY_ONLY", "AMOUNT_MISMATCH", "FINANCE_ONLY");
        assertThat(responses).extracting("sourceKey")
                .containsExactly(
                        "DELIVERY:INVENTORY-ONLY",
                        "ADJUSTMENT:MISMATCH",
                        "PAYABLE:FINANCE-ONLY"
                );
    }

    @Test
    void differenceTypeFilterIsTrimmedAndCaseInsensitive() {
        InventoryFinanceDifferenceQuery query = new InventoryFinanceDifferenceQuery();
        query.setDifferenceType(" inventory_only ");
        var data = new InventoryFinanceReconciliationQueryService.DifferenceData(
                period(),
                List.of(inventory("DELIVERY", "D-1", "-20.00")),
                List.of(finance("PAYABLE", "P-1", "15.00"))
        );

        var responses = service.assembleDifferences(data, query);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.sourceKey()).isEqualTo("DELIVERY:D-1");
            assertThat(response.differenceType()).isEqualTo("INVENTORY_ONLY");
        });
    }

    @Test
    void detailUsesInventoryDirectionAndVoucherDebitCreditSigns() {
        var inventoryTransactions = List.of(
                inventoryTransaction(1L, "IN", "20.005"),
                inventoryTransaction(2L, "OUT", "5.004")
        );
        var voucherEntries = List.of(
                voucherEntry(10L, "12.00", "0.00"),
                voucherEntry(11L, "0.00", "2.00")
        );

        var response = service.assembleDetail(
                new InventoryFinanceReconciliationQueryService.DifferenceDetailData(
                        period(), "ADJUSTMENT", "IA-1", inventoryTransactions, voucherEntries));

        assertThat(response.sourceKey()).isEqualTo("ADJUSTMENT:IA-1");
        assertThat(response.inventoryAmount()).isEqualByComparingTo("15.00");
        assertThat(response.financeAmount()).isEqualByComparingTo("10.00");
        assertThat(response.differenceAmount()).isEqualByComparingTo("5.00");
        assertThat(response.differenceType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(response.inventoryTransactions()).isSameAs(inventoryTransactions);
        assertThat(response.voucherEntries()).isSameAs(voucherEntries);
    }

    private AccountPeriodEntity period() {
        AccountPeriodEntity period = new AccountPeriodEntity();
        period.setId(865001L);
        period.setPeriodMonth("2036-05");
        return period;
    }

    private InventoryFinanceReconciliationQueryService.InventorySourceAmount inventory(
            String sourceType,
            String sourceNo,
            String amount
    ) {
        return new InventoryFinanceReconciliationQueryService.InventorySourceAmount(
                sourceType, sourceNo, new BigDecimal(amount));
    }

    private InventoryFinanceReconciliationQueryService.FinanceSourceAmount finance(
            String sourceType,
            String sourceNo,
            String amount
    ) {
        return new InventoryFinanceReconciliationQueryService.FinanceSourceAmount(
                sourceType, sourceNo, new BigDecimal(amount));
    }

    private InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse inventoryTransaction(
            Long id,
            String direction,
            String amount
    ) {
        return new InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse(
                id,
                "ADJUSTMENT",
                "IA-1",
                direction,
                BigDecimal.ONE,
                new BigDecimal(amount),
                LocalDateTime.of(2036, 5, 18, 10, 0),
                null
        );
    }

    private InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse voucherEntry(
            Long id,
            String debit,
            String credit
    ) {
        return new InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse(
                id,
                "VO-" + id,
                "ADJUSTMENT",
                "IA-1",
                LocalDate.of(2036, 5, 18),
                id.intValue(),
                "1001",
                "库存商品",
                new BigDecimal(debit),
                new BigDecimal(credit),
                null
        );
    }
}

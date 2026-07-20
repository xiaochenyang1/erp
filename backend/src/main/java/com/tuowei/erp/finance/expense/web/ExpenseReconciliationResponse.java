package com.tuowei.erp.finance.expense.web;

import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import com.tuowei.erp.finance.voucher.web.VoucherResponse;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseReconciliationResponse(
        ExpenseResponse expense,
        VoucherResponse voucher,
        List<VoucherEntryResponse> entries,
        VoucherResponse reversalVoucher,
        List<VoucherEntryResponse> reversalEntries,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal reversalDebitTotal,
        BigDecimal reversalCreditTotal,
        Boolean voucherMissing,
        Boolean entriesMissing,
        Boolean voucherBalanced,
        Boolean amountMatched,
        Boolean voucherLinkedToExpense,
        Boolean reversalVoucherBalanced,
        Boolean reversalAmountMatched,
        Boolean reversed
) {
}

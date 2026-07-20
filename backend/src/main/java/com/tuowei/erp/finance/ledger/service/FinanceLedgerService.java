package com.tuowei.erp.finance.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.ledger.web.DetailLedgerResponse;
import com.tuowei.erp.finance.ledger.web.GeneralLedgerResponse;
import com.tuowei.erp.finance.ledger.web.LedgerQuery;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FinanceLedgerService {

    private static final List<String> LEDGER_EXPORT_HEADERS = List.of(
            "bizDate",
            "voucherId",
            "lineNo",
            "subjectCode",
            "subjectName",
            "summary",
            "debitAmount",
            "creditAmount"
    );

    private final VoucherEntryMapper voucherEntryMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FinanceLedgerService(VoucherEntryMapper voucherEntryMapper, AuditMetadataFactory auditMetadataFactory) {
        this.voucherEntryMapper = voucherEntryMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerResponse> general(LedgerQuery query) {
        Map<String, LedgerAmounts> amounts = new TreeMap<>();
        for (VoucherEntryEntity entry : entries(query)) {
            LedgerAmounts ledgerAmounts = amounts.computeIfAbsent(
                    entry.getSubjectCode(),
                    code -> new LedgerAmounts(entry.getSubjectCode(), entry.getSubjectName())
            );
            ledgerAmounts.debitAmount = ScalePrecision.amount(ledgerAmounts.debitAmount.add(ScalePrecision.zeroDefault(entry.getDebitAmount())));
            ledgerAmounts.creditAmount = ScalePrecision.amount(ledgerAmounts.creditAmount.add(ScalePrecision.zeroDefault(entry.getCreditAmount())));
        }
        return amounts.values().stream()
                .map(item -> new GeneralLedgerResponse(item.subjectCode, item.subjectName, item.debitAmount, item.creditAmount))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DetailLedgerResponse> detail(LedgerQuery query) {
        return entries(query).stream()
                .map(entry -> new DetailLedgerResponse(
                        entry.getId(),
                        entry.getVoucherId(),
                        entry.getBizDate(),
                        entry.getLineNo(),
                        entry.getSubjectCode(),
                        entry.getSubjectName(),
                        entry.getDebitAmount(),
                        entry.getCreditAmount(),
                        entry.getSummary()
                ))
                .toList();
    }

    public StreamingResponseBody exportLedger(LedgerQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LedgerQuery safeQuery = query == null ? new LedgerQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, LEDGER_EXPORT_HEADERS, rowWriter -> {
            for (VoucherEntryEntity entry : entries(safeQuery)) {
                rowWriter.write(ledgerExportRow(entry));
            }
        }));
    }

    private List<VoucherEntryEntity> entries(LedgerQuery query) {
        LedgerQuery safeQuery = query == null ? new LedgerQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        LambdaQueryWrapper<VoucherEntryEntity> wrapper = new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId());
        if (StringUtils.hasText(safeQuery.getSubjectCode())) {
            wrapper.eq(VoucherEntryEntity::getSubjectCode, safeQuery.getSubjectCode().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(VoucherEntryEntity::getBizDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(VoucherEntryEntity::getBizDate, safeQuery.getDateTo());
        }
        wrapper.orderByAsc(VoucherEntryEntity::getSubjectCode)
                .orderByAsc(VoucherEntryEntity::getBizDate)
                .orderByAsc(VoucherEntryEntity::getVoucherId)
                .orderByAsc(VoucherEntryEntity::getLineNo);
        return voucherEntryMapper.selectList(wrapper);
    }

    private List<?> ledgerExportRow(VoucherEntryEntity entry) {
        return Arrays.asList(
                entry.getBizDate(),
                entry.getVoucherId(),
                entry.getLineNo(),
                entry.getSubjectCode(),
                entry.getSubjectName(),
                entry.getSummary(),
                entry.getDebitAmount(),
                entry.getCreditAmount()
        );
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    private static class LedgerAmounts {
        private final String subjectCode;
        private final String subjectName;
        private BigDecimal debitAmount = ScalePrecision.amount(BigDecimal.ZERO);
        private BigDecimal creditAmount = ScalePrecision.amount(BigDecimal.ZERO);

        private LedgerAmounts(String subjectCode, String subjectName) {
            this.subjectCode = subjectCode;
            this.subjectName = subjectName;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}

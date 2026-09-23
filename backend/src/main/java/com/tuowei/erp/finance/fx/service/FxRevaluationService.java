package com.tuowei.erp.finance.fx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.service.BaseCurrencyService;
import com.tuowei.erp.finance.currency.service.SettlementCurrencyService;
import com.tuowei.erp.finance.currency.support.CurrencyAmountSupport;
import com.tuowei.erp.finance.fx.mapper.FxRevaluationMapper;
import com.tuowei.erp.finance.fx.model.FxRevaluationEntity;
import com.tuowei.erp.finance.fx.web.FxRevaluationResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinanceVoucherPostingService;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Period-end unrealized FX revaluation.
 *
 * <p>Open foreign receivables and payables are revalued at the period-end rate.
 * The difference is posted to the general ledger only; subledger booking rates
 * stay unchanged so later settlement still realizes FX against the historical
 * rate. The adjustment is reversed on the first day of the next period.
 */
@Service
public class FxRevaluationService {

    private static final String AR = "FX_REVALUATION_AR";
    private static final String AP = "FX_REVALUATION_AP";
    private static final String AR_REVERSAL = "FX_REVALUATION_AR_REVERSAL";
    private static final String AP_REVERSAL = "FX_REVALUATION_AP_REVERSAL";
    private static final String AR_CANCEL = "FX_REVALUATION_AR_CANCEL";
    private static final String AP_CANCEL = "FX_REVALUATION_AP_CANCEL";
    private static final String AR_REVERSAL_CANCEL = "FX_REVALUATION_AR_REVERSAL_CANCEL";
    private static final String AP_REVERSAL_CANCEL = "FX_REVALUATION_AP_REVERSAL_CANCEL";

    private static final String AR_SUBJECT = "1122";
    private static final String AP_SUBJECT = "2202";
    private static final String FX_SUBJECT = "6061";
    private static final long POSTED_GENERATION = 0L;

    private final FxRevaluationMapper revaluationMapper;
    private final AccountPeriodMapper accountPeriodMapper;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final SettlementCurrencyService settlementCurrencyService;
    private final BaseCurrencyService baseCurrencyService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinanceVoucherPostingService voucherPostingService;
    private final AuditMetadataFactory auditMetadataFactory;

    public FxRevaluationService(
            FxRevaluationMapper revaluationMapper,
            AccountPeriodMapper accountPeriodMapper,
            ReceivableMapper receivableMapper,
            PayableMapper payableMapper,
            SettlementCurrencyService settlementCurrencyService,
            BaseCurrencyService baseCurrencyService,
            AccountPeriodGuard accountPeriodGuard,
            FinanceVoucherPostingService voucherPostingService,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.revaluationMapper = revaluationMapper;
        this.accountPeriodMapper = accountPeriodMapper;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.settlementCurrencyService = settlementCurrencyService;
        this.baseCurrencyService = baseCurrencyService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.voucherPostingService = voucherPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public FxRevaluationResponse revalue(Long periodId) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = requireOpenPeriod(periodId, audit, "执行期末调汇");
        accountPeriodGuard.requireOpen(period.getEndDate(), "期末调汇");

        Valuation valuation = valuate(period, audit);
        FxRevaluationEntity existing = findPosted(period);
        if (existing != null) {
            if (sameFingerprint(existing, valuation.fingerprint())) {
                return toResponse(existing, period, true);
            }
            throw new BusinessConflictException("外币未结清余额已变化，请先撤销本期调汇后再重新执行");
        }
        if (valuation.hasAdjustment()) {
            accountPeriodGuard.requireOpen(reversalDate(period), "期末调汇红冲");
        }

        FxRevaluationEntity run = newRun(period, valuation, audit);
        try {
            if (revaluationMapper.insert(run) != 1) {
                throw new IllegalStateException("保存期末调汇失败");
            }
        } catch (DataIntegrityViolationException ex) {
            return existingOrConflict(period, valuation.fingerprint());
        }
        postAdjustment(run, false, audit);
        return toResponse(run, period, false);
    }

    @Transactional
    public FxRevaluationResponse cancel(Long periodId) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = requireOpenPeriod(periodId, audit, "撤销期末调汇");
        accountPeriodGuard.requireOpen(period.getEndDate(), "撤销期末调汇");
        FxRevaluationEntity run = findPosted(period);
        if (run == null) {
            throw new BusinessConflictException("本期没有已过账的期末调汇");
        }
        if (hasAdjustment(run)) {
            accountPeriodGuard.requireOpen(run.getReversalDate(), "撤销期末调汇红冲");
        }
        postAdjustment(run, true, audit);
        run.setStatus("CANCELLED");
        run.setGeneration(run.getId());
        run.setUpdatedBy(audit.userId());
        run.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(revaluationMapper.updateById(run), "期末调汇已被其他操作修改，请刷新后重试");
        return toResponse(run, period, false);
    }

    private Valuation valuate(AccountPeriodEntity period, AuditMetadata audit) {
        String baseCurrency = CurrencyAmountSupport.currency(baseCurrencyService.current(audit));
        Exposure exposure = new Exposure(period.getEndDate(), audit, baseCurrency);
        for (ReceivableEntity receivable : openReceivables(audit)) {
            exposure.add(true, receivable.getDirection(), receivable.getCurrencyCode(),
                    receivable.getOriginalAmount(), receivable.getSettledAmount(),
                    receivable.getBaseOriginalAmount(), receivable.getBaseSettledAmount(),
                    receivable.getExchangeRate());
        }
        for (PayableEntity payable : openPayables(audit)) {
            exposure.add(false, payable.getDirection(), payable.getCurrencyCode(),
                    payable.getOriginalAmount(), payable.getSettledAmount(),
                    payable.getBaseOriginalAmount(), payable.getBaseSettledAmount(),
                    payable.getExchangeRate());
        }
        return exposure.toValuation();
    }

    private void postAdjustment(FxRevaluationEntity run, boolean cancel, AuditMetadata audit) {
        BigDecimal ar = ScalePrecision.amount(ScalePrecision.zeroDefault(run.getArAdjustment()));
        BigDecimal ap = ScalePrecision.amount(ScalePrecision.zeroDefault(run.getApAdjustment()));
        String sourceNo = run.getId() == null ? null : run.getId().toString();
        if (cancel) {
            post(AR_CANCEL, run.getId(), sourceNo, run.getRevaluationDate(), ar.negate(), true, "撤销期末调汇-应收", audit);
            post(AP_CANCEL, run.getId(), sourceNo, run.getRevaluationDate(), ap.negate(), false, "撤销期末调汇-应付", audit);
            post(AR_REVERSAL_CANCEL, run.getId(), sourceNo, run.getReversalDate(), ar, true, "撤销期末调汇红冲-应收", audit);
            post(AP_REVERSAL_CANCEL, run.getId(), sourceNo, run.getReversalDate(), ap, false, "撤销期末调汇红冲-应付", audit);
            return;
        }
        post(AR, run.getId(), sourceNo, run.getRevaluationDate(), ar, true, "期末调汇-应收", audit);
        post(AP, run.getId(), sourceNo, run.getRevaluationDate(), ap, false, "期末调汇-应付", audit);
        post(AR_REVERSAL, run.getId(), sourceNo, run.getReversalDate(), ar.negate(), true, "期末调汇红冲-应收", audit);
        post(AP_REVERSAL, run.getId(), sourceNo, run.getReversalDate(), ap.negate(), false, "期末调汇红冲-应付", audit);
    }

    private void post(
            String sourceType,
            Long sourceId,
            String sourceNo,
            LocalDate bizDate,
            BigDecimal signed,
            boolean receivable,
            String summary,
            AuditMetadata audit
    ) {
        BigDecimal amount = ScalePrecision.amount(ScalePrecision.zeroDefault(signed)).abs();
        if (amount.signum() == 0 || bizDate == null) {
            return;
        }
        String subject = receivable ? AR_SUBJECT : AP_SUBJECT;
        boolean increase = signed.signum() > 0;
        String debit = receivable
                ? (increase ? subject : FX_SUBJECT)
                : (increase ? FX_SUBJECT : subject);
        String credit = receivable
                ? (increase ? FX_SUBJECT : subject)
                : (increase ? subject : FX_SUBJECT);
        voucherPostingService.recordTwoSidedVoucher(
                sourceType, sourceId, sourceNo, bizDate, amount, summary, debit, credit, summary, audit);
    }

    private FxRevaluationEntity newRun(AccountPeriodEntity period, Valuation valuation, AuditMetadata audit) {
        FxRevaluationEntity run = new FxRevaluationEntity();
        run.setId(IdWorker.getId());
        run.setCompanyId(period.getCompanyId());
        run.setAccountBookId(period.getAccountBookId());
        run.setPeriodId(period.getId());
        run.setStatus("POSTED");
        run.setOpenOriginalTotal(valuation.fingerprint());
        run.setArAdjustment(valuation.arAdjustment());
        run.setApAdjustment(valuation.apAdjustment());
        run.setRevaluationDate(period.getEndDate());
        run.setReversalDate(reversalDate(period));
        run.setGeneration(POSTED_GENERATION);
        run.setDeletedFlag(0);
        run.setCreatedBy(audit.userId());
        run.setCreatedTime(audit.now());
        run.setUpdatedBy(audit.userId());
        run.setUpdatedTime(audit.now());
        run.setVersion(0);
        return run;
    }

    private FxRevaluationResponse existingOrConflict(AccountPeriodEntity period, BigDecimal fingerprint) {
        FxRevaluationEntity existing = findPosted(period);
        if (existing != null && sameFingerprint(existing, fingerprint)) {
            return toResponse(existing, period, true);
        }
        throw new BusinessConflictException("外币未结清余额已变化，请先撤销本期调汇后再重新执行");
    }

    private AccountPeriodEntity requireOpenPeriod(Long periodId, AuditMetadata audit, String action) {
        AccountPeriodEntity period = accountPeriodMapper.selectById(periodId);
        if (period == null
                || !Objects.equals(period.getCompanyId(), audit.companyId())
                || !Objects.equals(period.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessConflictException("只有打开的会计期间可以" + action);
        }
        if (period.getEndDate() == null) {
            throw new IllegalArgumentException("会计期间结束日期不能为空");
        }
        return period;
    }

    private FxRevaluationEntity findPosted(AccountPeriodEntity period) {
        return revaluationMapper.selectOne(new LambdaQueryWrapper<FxRevaluationEntity>()
                .eq(FxRevaluationEntity::getCompanyId, period.getCompanyId())
                .eq(FxRevaluationEntity::getAccountBookId, period.getAccountBookId())
                .eq(FxRevaluationEntity::getPeriodId, period.getId())
                .eq(FxRevaluationEntity::getStatus, "POSTED")
                .eq(FxRevaluationEntity::getDeletedFlag, 0)
                .last("limit 1"));
    }

    private List<ReceivableEntity> openReceivables(AuditMetadata audit) {
        return receivableMapper.selectList(new LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getCompanyId, audit.companyId())
                .eq(ReceivableEntity::getAccountBookId, audit.accountBookId())
                .eq(ReceivableEntity::getDeletedFlag, 0)
                .in(ReceivableEntity::getStatus, List.of("UNSETTLED", "PARTIALLY_SETTLED")));
    }

    private List<PayableEntity> openPayables(AuditMetadata audit) {
        return payableMapper.selectList(new LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getCompanyId, audit.companyId())
                .eq(PayableEntity::getAccountBookId, audit.accountBookId())
                .eq(PayableEntity::getDeletedFlag, 0)
                .in(PayableEntity::getStatus, List.of("UNSETTLED", "PARTIALLY_SETTLED")));
    }

    private static boolean sameFingerprint(FxRevaluationEntity existing, BigDecimal fingerprint) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(existing.getOpenOriginalTotal())).compareTo(fingerprint) == 0;
    }

    private static boolean hasAdjustment(FxRevaluationEntity run) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(run.getArAdjustment())).signum() != 0
                || ScalePrecision.amount(ScalePrecision.zeroDefault(run.getApAdjustment())).signum() != 0;
    }

    private static LocalDate reversalDate(AccountPeriodEntity period) {
        return period.getEndDate().plusDays(1);
    }

    private static FxRevaluationResponse toResponse(FxRevaluationEntity run, AccountPeriodEntity period, boolean alreadyPosted) {
        return new FxRevaluationResponse(
                run.getId(),
                period.getId(),
                period.getPeriodMonth(),
                run.getStatus(),
                ScalePrecision.amount(ScalePrecision.zeroDefault(run.getOpenOriginalTotal())),
                ScalePrecision.amount(ScalePrecision.zeroDefault(run.getArAdjustment())),
                ScalePrecision.amount(ScalePrecision.zeroDefault(run.getApAdjustment())),
                run.getRevaluationDate(),
                run.getReversalDate(),
                alreadyPosted
        );
    }

    private record Valuation(BigDecimal fingerprint, BigDecimal arAdjustment, BigDecimal apAdjustment) {
        private boolean hasAdjustment() {
            return arAdjustment.signum() != 0 || apAdjustment.signum() != 0;
        }
    }

    private final class Exposure {
        private final LocalDate periodEnd;
        private final AuditMetadata audit;
        private final String baseCurrency;
        private final Map<String, BigDecimal> rates = new HashMap<>();
        private BigDecimal fingerprint = BigDecimal.ZERO;
        private BigDecimal ar = BigDecimal.ZERO;
        private BigDecimal ap = BigDecimal.ZERO;

        private Exposure(LocalDate periodEnd, AuditMetadata audit, String baseCurrency) {
            this.periodEnd = periodEnd;
            this.audit = audit;
            this.baseCurrency = baseCurrency;
        }

        private void add(
                boolean receivable,
                String direction,
                String currencyCode,
                BigDecimal original,
                BigDecimal settled,
                BigDecimal baseOriginal,
                BigDecimal baseSettled,
                BigDecimal bookingRate
        ) {
            String currency = CurrencyAmountSupport.currency(currencyCode);
            if (currency.equals(baseCurrency)) {
                return;
            }
            BigDecimal signedOriginal = signed(direction, ScalePrecision.amount(
                    ScalePrecision.zeroDefault(original).subtract(ScalePrecision.zeroDefault(settled))));
            if (signedOriginal.signum() == 0) {
                return;
            }
            BigDecimal carrying = signed(direction, CurrencyAmountSupport.baseRemaining(
                    original, settled, baseOriginal, baseSettled, bookingRate));
            BigDecimal rate = rates.computeIfAbsent(currency, code -> settlementCurrencyService
                    .resolve(code, null, periodEnd, audit)
                    .exchangeRate());
            BigDecimal revalued = ScalePrecision.amount(signedOriginal.multiply(rate));
            BigDecimal difference = ScalePrecision.amount(revalued.subtract(carrying));
            fingerprint = fingerprint.add(signedOriginal);
            if (receivable) {
                ar = ar.add(difference);
            } else {
                ap = ap.add(difference);
            }
        }

        private Valuation toValuation() {
            return new Valuation(
                    ScalePrecision.amount(fingerprint),
                    ScalePrecision.amount(ar),
                    ScalePrecision.amount(ap)
            );
        }
    }

    private static BigDecimal signed(String direction, BigDecimal amount) {
        return "DECREASE".equalsIgnoreCase(direction) ? amount.negate() : amount;
    }
}

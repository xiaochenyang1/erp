package com.tuowei.erp.finance.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.fund.mapper.BankStatementMapper;
import com.tuowei.erp.finance.fund.mapper.FundAccountMapper;
import com.tuowei.erp.finance.fund.model.BankStatementEntity;
import com.tuowei.erp.finance.fund.model.FundAccountEntity;
import com.tuowei.erp.finance.fund.web.BankStatementCreateRequest;
import com.tuowei.erp.finance.fund.web.BankStatementMatchRequest;
import com.tuowei.erp.finance.fund.web.BankStatementResponse;
import com.tuowei.erp.finance.fund.web.BankStatementUnmatchRequest;
import com.tuowei.erp.finance.fund.web.FundAccountCreateRequest;
import com.tuowei.erp.finance.fund.web.FundAccountResponse;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.payment.model.PaymentEntity;
import com.tuowei.erp.finance.receipt.mapper.ReceiptMapper;
import com.tuowei.erp.finance.receipt.model.ReceiptEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/** Write-side account creation and bank statement reconciliation commands. */
@Service
public class FundCommandService {

    private static final String ACCOUNT_ENABLED = "ENABLED";
    private static final String ACCOUNT_BANK = "BANK";
    private static final String ACCOUNT_CASH = "CASH";
    private static final String STATEMENT_UNMATCHED = "UNMATCHED";
    private static final String STATEMENT_MATCHED = "MATCHED";
    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";
    private static final String BIZ_RECEIPT = "RECEIPT";
    private static final String BIZ_PAYMENT = "PAYMENT";

    private final FundAccountMapper fundAccountMapper;
    private final BankStatementMapper bankStatementMapper;
    private final ReceiptMapper receiptMapper;
    private final PaymentMapper paymentMapper;
    private final FundStatementNumberService statementNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final FundQueryService fundQueryService;

    public FundCommandService(
            FundAccountMapper fundAccountMapper,
            BankStatementMapper bankStatementMapper,
            ReceiptMapper receiptMapper,
            PaymentMapper paymentMapper,
            FundStatementNumberService statementNumberService,
            AuditMetadataFactory auditMetadataFactory,
            FundQueryService fundQueryService
    ) {
        this.fundAccountMapper = fundAccountMapper;
        this.bankStatementMapper = bankStatementMapper;
        this.receiptMapper = receiptMapper;
        this.paymentMapper = paymentMapper;
        this.statementNumberService = statementNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.fundQueryService = fundQueryService;
    }

    @Transactional
    public FundAccountResponse createAccount(FundAccountCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        String accountCode = normalizeRequired(request.accountCode(), "资金账户编码不能为空").toUpperCase(Locale.ROOT);
        String accountName = normalizeRequired(request.accountName(), "资金账户名称不能为空");
        String accountType = normalizeAccountType(request.accountType());
        String currencyCode = StringUtils.hasText(request.currencyCode())
                ? request.currencyCode().trim().toUpperCase(Locale.ROOT)
                : "CNY";

        FundAccountEntity entity = new FundAccountEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setAccountCode(accountCode);
        entity.setAccountName(accountName);
        entity.setAccountType(accountType);
        entity.setBankName(trimToNull(request.bankName()));
        entity.setBankAccountNo(trimToNull(request.bankAccountNo()));
        entity.setCurrencyCode(currencyCode);
        entity.setOpeningBalance(ScalePrecision.amount(
                request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance()
        ));
        entity.setStatus(ACCOUNT_ENABLED);
        entity.setDeletedFlag(0);
        entity.setRemark(trimToNull(request.remark()));
        setAudit(entity, audit, now);
        fundAccountMapper.insert(entity);
        return fundQueryService.toAccountResponse(entity);
    }

    @Transactional
    public BankStatementResponse createStatement(BankStatementCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        FundAccountEntity account = fundQueryService.requireAccount(request.fundAccountId(), audit);
        if (!ACCOUNT_ENABLED.equals(account.getStatus())) {
            throw new IllegalArgumentException("资金账户已停用，不能录入流水");
        }
        if (request.transactionDate() == null) {
            throw new IllegalArgumentException("交易日期不能为空");
        }
        String direction = normalizeDirection(request.direction());
        BigDecimal amount = ScalePrecision.amount(request.amount());
        ensurePositive(amount);

        BankStatementEntity entity = new BankStatementEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setFundAccountId(account.getId());
        entity.setStatementNo(statementNumberService.nextStatementNo(request.transactionDate()));
        entity.setExternalTxnNo(trimToNull(request.externalTxnNo()));
        entity.setTransactionDate(request.transactionDate());
        entity.setDirection(direction);
        entity.setAmount(amount);
        entity.setCounterpartyName(trimToNull(request.counterpartyName()));
        entity.setSummary(normalizeRequired(request.summary(), "流水摘要不能为空"));
        entity.setStatus(STATEMENT_UNMATCHED);
        entity.setDeletedFlag(0);
        entity.setRemark(trimToNull(request.remark()));
        setAudit(entity, audit, now);
        bankStatementMapper.insert(entity);
        return fundQueryService.toStatementResponse(entity);
    }

    @Transactional
    public BankStatementResponse matchStatement(Long id, BankStatementMatchRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        BankStatementEntity statement = fundQueryService.requireStatement(id, audit);
        if (!STATEMENT_UNMATCHED.equals(statement.getStatus())) {
            throw new IllegalArgumentException("银行流水已匹配，不能重复匹配");
        }
        String bizType = normalizeBizType(request.bizType());
        if (DIRECTION_IN.equals(statement.getDirection()) && !BIZ_RECEIPT.equals(bizType)) {
            throw new IllegalArgumentException("收入流水只能匹配收款单");
        }
        if (DIRECTION_OUT.equals(statement.getDirection()) && !BIZ_PAYMENT.equals(bizType)) {
            throw new IllegalArgumentException("支出流水只能匹配付款单");
        }

        MatchedBusiness business = BIZ_RECEIPT.equals(bizType)
                ? matchedReceipt(request.bizId(), audit)
                : matchedPayment(request.bizId(), audit);
        if (ScalePrecision.amount(business.amount()).compareTo(statement.getAmount()) != 0) {
            throw new IllegalArgumentException("银行流水金额与业务单据金额不一致");
        }
        ensureBusinessNotMatched(bizType, business.id(), audit);

        statement.setStatus(STATEMENT_MATCHED);
        statement.setMatchedBizType(bizType);
        statement.setMatchedBizId(business.id());
        statement.setMatchedBizNo(business.bizNo());
        statement.setMatchedBy(audit.userId());
        statement.setMatchedTime(audit.now());
        statement.setUnmatchReason(null);
        statement.setUpdatedBy(audit.userId());
        statement.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                bankStatementMapper.updateById(statement),
                "银行流水已被其他操作修改，请刷新后重试"
        );
        return fundQueryService.toStatementResponse(fundQueryService.requireStatement(id, audit));
    }

    @Transactional
    public BankStatementResponse unmatchStatement(Long id, BankStatementUnmatchRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        BankStatementEntity statement = fundQueryService.requireStatement(id, audit);
        if (!STATEMENT_MATCHED.equals(statement.getStatus())) {
            throw new IllegalArgumentException("只有已匹配银行流水可以取消匹配");
        }
        String reason = normalizeRequired(request.reason(), "取消匹配原因不能为空");
        OptimisticLockGuard.requireUpdated(bankStatementMapper.update(
                null,
                new LambdaUpdateWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getId, statement.getId())
                        .eq(BankStatementEntity::getCompanyId, audit.companyId())
                        .eq(BankStatementEntity::getAccountBookId, audit.accountBookId())
                        .eq(BankStatementEntity::getStatus, STATEMENT_MATCHED)
                        .eq(BankStatementEntity::getVersion, statement.getVersion())
                        .set(BankStatementEntity::getStatus, STATEMENT_UNMATCHED)
                        .set(BankStatementEntity::getMatchedBizType, null)
                        .set(BankStatementEntity::getMatchedBizId, null)
                        .set(BankStatementEntity::getMatchedBizNo, null)
                        .set(BankStatementEntity::getMatchedBy, null)
                        .set(BankStatementEntity::getMatchedTime, null)
                        .set(BankStatementEntity::getUnmatchReason, reason)
                        .set(BankStatementEntity::getUpdatedBy, audit.userId())
                        .set(BankStatementEntity::getUpdatedTime, audit.now())
                        .setSql("version = version + 1")
        ), "银行流水已被其他操作修改，请刷新后重试");
        return fundQueryService.toStatementResponse(fundQueryService.requireStatement(id, audit));
    }

    private MatchedBusiness matchedReceipt(Long id, AuditMetadata audit) {
        ReceiptEntity receipt = id == null ? null : receiptMapper.selectById(id);
        if (receipt == null
                || !Objects.equals(receipt.getCompanyId(), audit.companyId())
                || !Objects.equals(receipt.getAccountBookId(), audit.accountBookId())
                || receipt.getDeletedFlag() != 0
                || !"POSTED".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("收款单不存在或未过账");
        }
        return new MatchedBusiness(receipt.getId(), receipt.getReceiptNo(), receipt.getAmount());
    }

    private MatchedBusiness matchedPayment(Long id, AuditMetadata audit) {
        PaymentEntity payment = id == null ? null : paymentMapper.selectById(id);
        if (payment == null
                || !Objects.equals(payment.getCompanyId(), audit.companyId())
                || !Objects.equals(payment.getAccountBookId(), audit.accountBookId())
                || payment.getDeletedFlag() != 0
                || !"POSTED".equals(payment.getStatus())) {
            throw new IllegalArgumentException("付款单不存在或未过账");
        }
        return new MatchedBusiness(payment.getId(), payment.getPaymentNo(), payment.getAmount());
    }

    private void ensureBusinessNotMatched(String bizType, Long bizId, AuditMetadata audit) {
        long count = bankStatementMapper.selectCount(new LambdaQueryWrapper<BankStatementEntity>()
                .eq(BankStatementEntity::getCompanyId, audit.companyId())
                .eq(BankStatementEntity::getAccountBookId, audit.accountBookId())
                .eq(BankStatementEntity::getDeletedFlag, 0)
                .eq(BankStatementEntity::getStatus, STATEMENT_MATCHED)
                .eq(BankStatementEntity::getMatchedBizType, bizType)
                .eq(BankStatementEntity::getMatchedBizId, bizId));
        if (count > 0) {
            throw new IllegalArgumentException("业务单据已匹配银行流水");
        }
    }

    private String normalizeAccountType(String value) {
        String normalized = normalizeRequired(value, "资金账户类型不能为空").toUpperCase(Locale.ROOT);
        if (!ACCOUNT_BANK.equals(normalized) && !ACCOUNT_CASH.equals(normalized)) {
            throw new IllegalArgumentException("资金账户类型不合法");
        }
        return normalized;
    }

    private String normalizeDirection(String value) {
        String normalized = normalizeRequired(value, "流水方向不能为空").toUpperCase(Locale.ROOT);
        if (!DIRECTION_IN.equals(normalized) && !DIRECTION_OUT.equals(normalized)) {
            throw new IllegalArgumentException("流水方向不合法");
        }
        return normalized;
    }

    private String normalizeBizType(String value) {
        String normalized = normalizeRequired(value, "匹配业务类型不能为空").toUpperCase(Locale.ROOT);
        if (!BIZ_RECEIPT.equals(normalized) && !BIZ_PAYMENT.equals(normalized)) {
            throw new IllegalArgumentException("匹配业务类型不合法");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void ensurePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("流水金额必须大于0");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void setAudit(FundAccountEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void setAudit(BankStatementEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private record MatchedBusiness(Long id, String bizNo, BigDecimal amount) {
    }
}

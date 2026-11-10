package com.tuowei.erp.finance.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.fund.mapper.BankStatementMapper;
import com.tuowei.erp.finance.fund.mapper.FundAccountMapper;
import com.tuowei.erp.finance.fund.model.BankStatementEntity;
import com.tuowei.erp.finance.fund.model.FundAccountEntity;
import com.tuowei.erp.finance.fund.web.BankStatementPageQuery;
import com.tuowei.erp.finance.fund.web.BankStatementResponse;
import com.tuowei.erp.finance.fund.web.FundAccountPageQuery;
import com.tuowei.erp.finance.fund.web.FundAccountResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant guards and response mapping for fund reconciliation. */
@Service
public class FundQueryService {

    private static final String ACCOUNT_BANK = "BANK";
    private static final String ACCOUNT_CASH = "CASH";

    private final FundAccountMapper fundAccountMapper;
    private final BankStatementMapper bankStatementMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FundQueryService(
            FundAccountMapper fundAccountMapper,
            BankStatementMapper bankStatementMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.fundAccountMapper = fundAccountMapper;
        this.bankStatementMapper = bankStatementMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<FundAccountResponse> listAccounts(FundAccountPageQuery query) {
        FundAccountPageQuery safeQuery = query == null ? new FundAccountPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<FundAccountEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<FundAccountEntity> wrapper = accountBaseWrapper(audit);
        if (StringUtils.hasText(safeQuery.getAccountType())) {
            wrapper.eq(FundAccountEntity::getAccountType, normalizeAccountType(safeQuery.getAccountType()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(FundAccountEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(nested -> nested.like(FundAccountEntity::getAccountCode, keyword)
                    .or()
                    .like(FundAccountEntity::getAccountName, keyword));
        }
        wrapper.orderByDesc(FundAccountEntity::getId);
        Page<FundAccountEntity> result = fundAccountMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toAccountResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public FundAccountResponse accountDetail(Long id) {
        return toAccountResponse(requireAccount(id, auditMetadataFactory.current()));
    }

    @Transactional(readOnly = true)
    public PageResponse<BankStatementResponse> listStatements(BankStatementPageQuery query) {
        BankStatementPageQuery safeQuery = query == null ? new BankStatementPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<BankStatementEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<BankStatementEntity> wrapper = statementBaseWrapper(audit);
        if (safeQuery.getFundAccountId() != null) {
            wrapper.eq(BankStatementEntity::getFundAccountId, safeQuery.getFundAccountId());
        }
        if (StringUtils.hasText(safeQuery.getDirection())) {
            wrapper.eq(BankStatementEntity::getDirection, normalizeDirection(safeQuery.getDirection()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(BankStatementEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getTransactionDateFrom() != null) {
            wrapper.ge(BankStatementEntity::getTransactionDate, safeQuery.getTransactionDateFrom());
        }
        if (safeQuery.getTransactionDateTo() != null) {
            wrapper.le(BankStatementEntity::getTransactionDate, safeQuery.getTransactionDateTo());
        }
        if (StringUtils.hasText(safeQuery.getMatchedBizType())) {
            wrapper.eq(BankStatementEntity::getMatchedBizType, safeQuery.getMatchedBizType().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(safeQuery.getMatchedBizNo())) {
            wrapper.like(BankStatementEntity::getMatchedBizNo, safeQuery.getMatchedBizNo().trim());
        }
        wrapper.orderByDesc(BankStatementEntity::getTransactionDate)
                .orderByDesc(BankStatementEntity::getId);
        Page<BankStatementEntity> result = bankStatementMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toStatementResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public BankStatementResponse statementDetail(Long id) {
        return toStatementResponse(requireStatement(id, auditMetadataFactory.current()));
    }

    FundAccountEntity requireAccount(Long id, AuditMetadata audit) {
        FundAccountEntity account = id == null ? null : fundAccountMapper.selectById(id);
        if (account == null
                || !Objects.equals(account.getCompanyId(), audit.companyId())
                || !Objects.equals(account.getAccountBookId(), audit.accountBookId())
                || account.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("资金账户不存在");
        }
        return account;
    }

    BankStatementEntity requireStatement(Long id, AuditMetadata audit) {
        BankStatementEntity statement = id == null ? null : bankStatementMapper.selectById(id);
        if (statement == null
                || !Objects.equals(statement.getCompanyId(), audit.companyId())
                || !Objects.equals(statement.getAccountBookId(), audit.accountBookId())
                || statement.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("银行流水不存在");
        }
        return statement;
    }

    FundAccountResponse toAccountResponse(FundAccountEntity entity) {
        return new FundAccountResponse(
                entity.getId(),
                entity.getAccountCode(),
                entity.getAccountName(),
                entity.getAccountType(),
                entity.getBankName(),
                entity.getBankAccountNo(),
                entity.getCurrencyCode(),
                entity.getOpeningBalance(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreatedTime()
        );
    }

    BankStatementResponse toStatementResponse(BankStatementEntity entity) {
        return new BankStatementResponse(
                entity.getId(),
                entity.getFundAccountId(),
                entity.getStatementNo(),
                entity.getExternalTxnNo(),
                entity.getTransactionDate(),
                entity.getDirection(),
                entity.getAmount(),
                entity.getCounterpartyName(),
                entity.getSummary(),
                entity.getStatus(),
                entity.getMatchedBizType(),
                entity.getMatchedBizId(),
                entity.getMatchedBizNo(),
                entity.getMatchedTime(),
                entity.getMatchedBy(),
                entity.getUnmatchReason(),
                entity.getRemark(),
                entity.getCreatedTime()
        );
    }

    private LambdaQueryWrapper<FundAccountEntity> accountBaseWrapper(AuditMetadata audit) {
        return new LambdaQueryWrapper<FundAccountEntity>()
                .eq(FundAccountEntity::getCompanyId, audit.companyId())
                .eq(FundAccountEntity::getAccountBookId, audit.accountBookId())
                .eq(FundAccountEntity::getDeletedFlag, 0);
    }

    private LambdaQueryWrapper<BankStatementEntity> statementBaseWrapper(AuditMetadata audit) {
        return new LambdaQueryWrapper<BankStatementEntity>()
                .eq(BankStatementEntity::getCompanyId, audit.companyId())
                .eq(BankStatementEntity::getAccountBookId, audit.accountBookId())
                .eq(BankStatementEntity::getDeletedFlag, 0);
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
        if (!"IN".equals(normalized) && !"OUT".equals(normalized)) {
            throw new IllegalArgumentException("流水方向不合法");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

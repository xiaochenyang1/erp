package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ImportProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.mapper.ImportJobRowMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportJobResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Write-side lifecycle management for import previews and atomic commits. */
@Service
public class ImportJobCommandService {

    private static final int MAX_IMPORT_FILENAME_LENGTH = 255;

    private final ImportJobMapper importJobMapper;
    private final ImportJobRowMapper importJobRowMapper;
    private final ImportTemplateRegistry templateRegistry;
    private final CsvImportParser csvImportParser;
    private final AuditMetadataFactory auditMetadataFactory;
    private final Map<String, ImportTypeHandler> handlerMap;
    private final ImportValidationSupport validationSupport;
    private final TransactionTemplate commitTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final AccountPeriodGuard accountPeriodGuard;
    private final ImportProperties importProperties;
    private final ImportJobQueryService importJobQueryService;

    public ImportJobCommandService(
            ImportJobMapper importJobMapper,
            ImportJobRowMapper importJobRowMapper,
            ImportTemplateRegistry templateRegistry,
            CsvImportParser csvImportParser,
            AuditMetadataFactory auditMetadataFactory,
            PlatformTransactionManager transactionManager,
            List<ImportTypeHandler> handlers,
            ImportValidationSupport validationSupport,
            AccountPeriodGuard accountPeriodGuard,
            ImportProperties importProperties,
            ImportJobQueryService importJobQueryService
    ) {
        this.importJobMapper = importJobMapper;
        this.importJobRowMapper = importJobRowMapper;
        this.templateRegistry = templateRegistry;
        this.csvImportParser = csvImportParser;
        this.auditMetadataFactory = auditMetadataFactory;
        this.handlerMap = handlers.stream()
                .collect(java.util.stream.Collectors.toMap(ImportTypeHandler::importType, handler -> handler));
        this.validationSupport = validationSupport;
        this.commitTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        this.accountPeriodGuard = accountPeriodGuard;
        this.importProperties = importProperties;
        this.importJobQueryService = importJobQueryService;
    }

    @Transactional
    public ImportJobResponse preview(String importType, MultipartFile file) {
        String normalizedImportType = normalizeImportType(importType);
        ImportTypeHandler handler = requireHandler(normalizedImportType);
        AuditMetadata audit = auditMetadataFactory.current();
        ImportTypeHandler.ImportValidationContext context = new ImportTypeHandler.ImportValidationContext(
                audit.companyId(),
                audit.accountBookId(),
                audit.userId()
        );
        CsvImportParser.ParsedCsv parsedCsv = csvImportParser.parse(
                file,
                templateRegistry.headers(normalizedImportType)
        );
        LocalDateTime now = audit.now();
        ImportJobEntity job = new ImportJobEntity();
        job.setCompanyId(audit.companyId());
        job.setAccountBookId(audit.accountBookId());
        job.setImportType(normalizedImportType);
        job.setFileName(normalizeImportFilename(normalizedImportType, file.getOriginalFilename()));
        job.setStatus(ImportConstants.VALIDATED);
        job.setTotalRows(parsedCsv.rows().size());
        job.setValidRows(0);
        job.setErrorRows(0);
        job.setCommittedRows(0);
        job.setErrorMessage(null);
        job.setCreatedBy(audit.userId());
        job.setCreatedTime(now);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(now);
        job.setVersion(0);
        if (importJobMapper.insert(job) != 1) {
            throw new IllegalStateException("保存导入任务失败");
        }

        if (parsedCsv.rows().isEmpty()) {
            job.setStatus(ImportConstants.INVALID);
            job.setErrorMessage("CSV文件没有数据行");
            job.setUpdatedBy(audit.userId());
            job.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    importJobMapper.updateById(job),
                    "导入任务已被其他操作修改，请刷新后重试"
            );
            return importJobQueryService.toResponse(job, List.of());
        }

        List<ImportJobRowEntity> entities = new ArrayList<>();
        for (CsvImportParser.ParsedCsvRow parsedRow : parsedCsv.rows()) {
            ImportTypeHandler.ImportRowPlan plan = handler.validate(
                    parsedRow.rowNo(),
                    parsedRow.values(),
                    context
            );
            ImportJobRowEntity row = new ImportJobRowEntity();
            row.setCompanyId(audit.companyId());
            row.setAccountBookId(audit.accountBookId());
            row.setJobId(job.getId());
            row.setRowNo(parsedRow.rowNo());
            row.setRawJson(validationSupport.toJson(parsedRow.values()));
            row.setNormalizedJson(validationSupport.toJson(plan.normalized()));
            row.setValidFlag(plan.valid() ? 1 : 0);
            row.setErrorJson(validationSupport.toJson(plan.errors()));
            row.setCreatedTime(now);
            entities.add(row);
        }
        handler.afterValidate(entities);
        int validRows = 0;
        int errorRows = 0;
        for (ImportJobRowEntity row : entities) {
            if (row.getValidFlag() != null && row.getValidFlag() == 1) {
                validRows++;
            } else {
                errorRows++;
            }
        }
        for (ImportJobRowEntity row : entities) {
            if (importJobRowMapper.insert(row) != 1) {
                throw new IllegalStateException("保存导入明细失败");
            }
        }
        job.setValidRows(validRows);
        job.setErrorRows(errorRows);
        job.setStatus(errorRows > 0 ? ImportConstants.INVALID : ImportConstants.VALIDATED);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                importJobMapper.updateById(job),
                "导入任务已被其他操作修改，请刷新后重试"
        );
        return importJobQueryService.toResponse(job, entities);
    }

    /**
     * The commit transaction is intentionally managed explicitly. A failed commit is
     * recorded by {@link #markFailed(Long, String, AuditMetadata)} in a separate
     * REQUIRES_NEW transaction after the business transaction rolls back.
     */
    public ImportJobResponse commit(Long jobId) {
        ImportJobEntity job = importJobQueryService.requireCurrentJob(jobId);
        requireCommittableJob(job);
        ImportTypeHandler handler = requireHandler(job.getImportType());
        AuditMetadata audit = auditMetadataFactory.current();
        if (!Objects.equals(job.getCompanyId(), audit.companyId())
                || !Objects.equals(job.getAccountBookId(), audit.accountBookId())) {
            throw new BusinessConflictException("导入任务不属于当前公司或账套");
        }
        try {
            return commitTransactionTemplate.execute(status -> commitValidatedJob(jobId, handler, audit));
        } catch (RuntimeException ex) {
            String message = commitFailureMessage(ex);
            markFailed(jobId, message, audit);
            throw new BusinessConflictException(message);
        }
    }

    private ImportJobResponse commitValidatedJob(
            Long jobId,
            ImportTypeHandler handler,
            AuditMetadata audit
    ) {
        ImportJobEntity job = importJobQueryService.requireCurrentJob(jobId);
        requireCommittableJob(job);
        job.setStatus(ImportConstants.COMMITTING);
        job.setErrorMessage(null);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                importJobMapper.updateById(job),
                "导入任务已被其他操作修改，请刷新后重试"
        );
        int committedRows = commitValidRowsInBatches(job, handler, audit);
        job.setCommittedRows(committedRows);
        job.setStatus(ImportConstants.COMMITTED);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                importJobMapper.updateById(job),
                "导入任务已被其他操作修改，请刷新后重试"
        );
        return importJobQueryService.toResponse(job, List.of());
    }

    private void requireCommittableJob(ImportJobEntity job) {
        if (ImportConstants.VALIDATED.equals(job.getStatus())) {
            return;
        }
        boolean retryableFailedJob = ImportConstants.FAILED.equals(job.getStatus())
                && Objects.equals(job.getErrorRows(), 0)
                && job.getValidRows() != null
                && job.getValidRows() > 0
                && Objects.equals(job.getCommittedRows(), 0);
        if (!retryableFailedJob) {
            throw new BusinessConflictException("只有校验通过的导入任务才能提交");
        }
    }

    private int commitValidRowsInBatches(
            ImportJobEntity job,
            ImportTypeHandler handler,
            AuditMetadata audit
    ) {
        ImportTypeHandler.BatchCommitSession batchCommitSession = handler.beginBatchCommit(job, audit);
        if (batchCommitSession != null) {
            return commitValidRowsWithSession(job, batchCommitSession);
        }
        int[] committedRowsHolder = {0};
        forEachValidRowBatch(job, rows -> {
            guardImportBusinessDates(rows);
            committedRowsHolder[0] += handler.commit(job, rows, audit);
        });
        return committedRowsHolder[0];
    }

    private int commitValidRowsWithSession(
            ImportJobEntity job,
            ImportTypeHandler.BatchCommitSession session
    ) {
        forEachValidRowBatch(job, rows -> {
            guardImportBusinessDates(rows);
            session.inspect(rows);
        });
        session.beforeCommit();
        int[] committedRowsHolder = {0};
        forEachValidRowBatch(job, rows -> committedRowsHolder[0] += session.commit(rows));
        return committedRowsHolder[0];
    }

    private void forEachValidRowBatch(
            ImportJobEntity job,
            Consumer<List<ImportJobRowEntity>> consumer
    ) {
        long pageNo = 1;
        long batchSize = commitBatchSize();
        while (true) {
            List<ImportJobRowEntity> rows = selectValidRowsPage(job, pageNo, batchSize);
            if (rows.isEmpty()) {
                return;
            }
            consumer.accept(rows);
            if (rows.size() < batchSize) {
                return;
            }
            pageNo++;
        }
    }

    private List<ImportJobRowEntity> selectValidRowsPage(
            ImportJobEntity job,
            long pageNo,
            long batchSize
    ) {
        Page<ImportJobRowEntity> page = new Page<>(pageNo, batchSize, false);
        page.setMaxLimit(batchSize);
        return importJobRowMapper.selectPage(page, new LambdaQueryWrapper<ImportJobRowEntity>()
                .eq(ImportJobRowEntity::getCompanyId, job.getCompanyId())
                .eq(ImportJobRowEntity::getAccountBookId, job.getAccountBookId())
                .eq(ImportJobRowEntity::getJobId, job.getId())
                .eq(ImportJobRowEntity::getValidFlag, 1)
                .orderByAsc(ImportJobRowEntity::getRowNo))
                .getRecords();
    }

    private int commitBatchSize() {
        return Math.min(importProperties.commitBatchSize(), importProperties.maxRows());
    }

    private void guardImportBusinessDates(List<ImportJobRowEntity> rows) {
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = validationSupport.normalizedFromJson(row.getNormalizedJson());
            requireOpenIfDatePresent(normalized, "openingDate");
            requireOpenIfDatePresent(normalized, "bizDate");
        }
    }

    private void requireOpenIfDatePresent(Map<String, Object> normalized, String key) {
        Object value = normalized.get(key);
        if (value == null || !StringUtils.hasText(value.toString())) {
            return;
        }
        accountPeriodGuard.requireOpen(LocalDate.parse(value.toString()), "期初导入提交");
    }

    private void markFailed(Long jobId, String message, AuditMetadata audit) {
        ImportJobEntity failed = new ImportJobEntity();
        failed.setId(jobId);
        failed.setStatus(ImportConstants.FAILED);
        failed.setErrorMessage(StringUtils.hasText(message) ? message : "导入提交失败");
        failed.setUpdatedBy(audit.userId());
        failed.setUpdatedTime(audit.now());
        requiresNewTransactionTemplate.executeWithoutResult(status -> importJobMapper.updateById(failed));
    }

    private String commitFailureMessage(RuntimeException ex) {
        if (ex instanceof BusinessConflictException || ex instanceof IllegalArgumentException) {
            return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "导入提交失败";
        }
        return "导入提交失败";
    }

    private ImportTypeHandler requireHandler(String importType) {
        String normalizedImportType = normalizeImportType(importType);
        ImportTypeHandler handler = handlerMap.get(normalizedImportType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的导入类型: " + normalizedImportType);
        }
        return handler;
    }

    private String normalizeImportType(String importType) {
        if (!StringUtils.hasText(importType)) {
            throw new IllegalArgumentException("导入类型不能为空");
        }
        return importType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeImportFilename(String importType, String filename) {
        return com.tuowei.erp.common.web.SafeFilename.normalize(
                filename,
                importType + ".csv",
                MAX_IMPORT_FILENAME_LENGTH
        );
    }
}

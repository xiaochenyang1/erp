package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.ImportProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.mapper.ImportJobRowMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.imports.web.ImportJobResponse;
import com.tuowei.erp.imports.web.ImportRowResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class ImportJobService {

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

    public ImportJobService(
            ImportJobMapper importJobMapper,
            ImportJobRowMapper importJobRowMapper,
            ImportTemplateRegistry templateRegistry,
            CsvImportParser csvImportParser,
            ObjectMapper objectMapper,
            AuditMetadataFactory auditMetadataFactory,
            PlatformTransactionManager transactionManager,
            List<ImportTypeHandler> handlers,
            ImportValidationSupport validationSupport,
            AccountPeriodGuard accountPeriodGuard,
            ImportProperties importProperties
    ) {
        this.importJobMapper = importJobMapper;
        this.importJobRowMapper = importJobRowMapper;
        this.templateRegistry = templateRegistry;
        this.csvImportParser = csvImportParser;
        this.auditMetadataFactory = auditMetadataFactory;
        this.validationSupport = validationSupport;
        this.handlerMap = handlers.stream().collect(java.util.stream.Collectors.toMap(ImportTypeHandler::importType, handler -> handler));
        this.commitTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
        this.accountPeriodGuard = accountPeriodGuard;
        this.importProperties = importProperties;
    }

    public ResponseEntity<ByteArrayResource> template(String importType) {
        String normalizedImportType = normalizeImportType(importType);
        byte[] content = templateRegistry.csvTemplate(normalizedImportType).getBytes(StandardCharsets.UTF_8);
        String fileName = normalizedImportType.toLowerCase(java.util.Locale.ROOT) + "-template.csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> list(ImportJobPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ImportJobPageQuery safeQuery = query == null ? new ImportJobPageQuery() : query;
        Page<ImportJobEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        Page<ImportJobEntity> result = importJobMapper.selectPage(page, buildJobListQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(job -> toResponse(job, List.of())).toList()
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> exportErrorRows(Long jobId) {
        ImportJobEntity job = requireCurrentJob(jobId);
        List<ImportJobRowEntity> rows = importJobRowMapper.selectList(new LambdaQueryWrapper<ImportJobRowEntity>()
                .eq(ImportJobRowEntity::getCompanyId, job.getCompanyId())
                .eq(ImportJobRowEntity::getAccountBookId, job.getAccountBookId())
                .eq(ImportJobRowEntity::getJobId, jobId)
                .eq(ImportJobRowEntity::getValidFlag, 0)
                .orderByAsc(ImportJobRowEntity::getRowNo));
        String csv = CsvExport.write(
                List.of("rowNo", "rawData", "errors"),
                rows.stream().map(row -> List.of(
                        row.getRowNo(),
                        validationSupport.rawFromJson(row.getRawJson()).toString(),
                        validationSupport.errorsFromJson(row.getErrorJson()).stream()
                                .map(error -> error.column() + ":" + error.message())
                                .toList()
                                .toString()
                )).toList()
        );
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("import-job-" + jobId + "-errors.csv", StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));
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
        CsvImportParser.ParsedCsv parsedCsv = csvImportParser.parse(file, templateRegistry.headers(normalizedImportType));
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
            OptimisticLockGuard.requireUpdated(importJobMapper.updateById(job), "导入任务已被其他操作修改，请刷新后重试");
            return toResponse(job, List.of());
        }

        List<ImportJobRowEntity> entities = new ArrayList<>();
        for (CsvImportParser.ParsedCsvRow parsedRow : parsedCsv.rows()) {
            ImportTypeHandler.ImportRowPlan plan = handler.validate(parsedRow.rowNo(), parsedRow.values(), context);
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
        OptimisticLockGuard.requireUpdated(importJobMapper.updateById(job), "导入任务已被其他操作修改，请刷新后重试");
        return toResponse(job, entities);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse detail(Long jobId) {
        ImportJobEntity job = requireCurrentJob(jobId);
        List<ImportJobRowEntity> rows = importJobRowMapper.selectList(new LambdaQueryWrapper<ImportJobRowEntity>()
                .eq(ImportJobRowEntity::getCompanyId, job.getCompanyId())
                .eq(ImportJobRowEntity::getAccountBookId, job.getAccountBookId())
                .eq(ImportJobRowEntity::getJobId, jobId)
                .orderByAsc(ImportJobRowEntity::getRowNo));
        return toResponse(job, rows);
    }

    public ImportJobResponse commit(Long jobId) {
        ImportJobEntity job = requireCurrentJob(jobId);
        requireCommittableJob(job);
        ImportTypeHandler handler = requireHandler(job.getImportType());
        AuditMetadata audit = auditMetadataFactory.current();
        if (!Objects.equals(job.getCompanyId(), audit.companyId()) || !Objects.equals(job.getAccountBookId(), audit.accountBookId())) {
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

    private ImportJobResponse commitValidatedJob(Long jobId, ImportTypeHandler handler, AuditMetadata audit) {
        ImportJobEntity job = requireCurrentJob(jobId);
        requireCommittableJob(job);
        job.setStatus(ImportConstants.COMMITTING);
        job.setErrorMessage(null);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(importJobMapper.updateById(job), "导入任务已被其他操作修改，请刷新后重试");
        int committedRows = commitValidRowsInBatches(job, handler, audit);
        job.setCommittedRows(committedRows);
        job.setStatus(ImportConstants.COMMITTED);
        job.setUpdatedBy(audit.userId());
        job.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(importJobMapper.updateById(job), "导入任务已被其他操作修改，请刷新后重试");
        return toResponse(job, List.of());
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

    private int commitValidRowsInBatches(ImportJobEntity job, ImportTypeHandler handler, AuditMetadata audit) {
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

    private int commitValidRowsWithSession(ImportJobEntity job, ImportTypeHandler.BatchCommitSession session) {
        forEachValidRowBatch(job, rows -> {
            guardImportBusinessDates(rows);
            session.inspect(rows);
        });
        session.beforeCommit();
        int[] committedRowsHolder = {0};
        forEachValidRowBatch(job, rows -> committedRowsHolder[0] += session.commit(rows));
        return committedRowsHolder[0];
    }

    private void forEachValidRowBatch(ImportJobEntity job, Consumer<List<ImportJobRowEntity>> consumer) {
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

    private List<ImportJobRowEntity> selectValidRowsPage(ImportJobEntity job, long pageNo, long batchSize) {
        Page<ImportJobRowEntity> page = new Page<>(pageNo, batchSize, false);
        page.setMaxLimit(batchSize);
        return importJobRowMapper.selectPage(page, new LambdaQueryWrapper<ImportJobRowEntity>()
                .eq(ImportJobRowEntity::getCompanyId, job.getCompanyId())
                .eq(ImportJobRowEntity::getAccountBookId, job.getAccountBookId())
                .eq(ImportJobRowEntity::getJobId, job.getId())
                .eq(ImportJobRowEntity::getValidFlag, 1)
                .orderByAsc(ImportJobRowEntity::getRowNo)).getRecords();
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

    private ImportJobEntity requireCurrentJob(Long jobId) {
        AuditMetadata audit = auditMetadataFactory.current();
        ImportJobEntity job = importJobMapper.selectById(jobId);
        if (job == null || !Objects.equals(job.getCompanyId(), audit.companyId()) || !Objects.equals(job.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("导入任务不存在");
        }
        return job;
    }

    private ImportTypeHandler requireHandler(String importType) {
        String normalizedImportType = normalizeImportType(importType);
        ImportTypeHandler handler = handlerMap.get(normalizedImportType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的导入类型: " + normalizedImportType);
        }
        return handler;
    }

    private LambdaQueryWrapper<ImportJobEntity> buildJobListQuery(AuditMetadata audit, ImportJobPageQuery query) {
        LambdaQueryWrapper<ImportJobEntity> wrapper = new LambdaQueryWrapper<ImportJobEntity>()
                .eq(ImportJobEntity::getCompanyId, audit.companyId())
                .eq(ImportJobEntity::getAccountBookId, audit.accountBookId());
        String importType = normalizeNullable(query.getImportType());
        if (StringUtils.hasText(importType)) {
            wrapper.eq(ImportJobEntity::getImportType, importType.toUpperCase(Locale.ROOT));
        }
        String status = normalizeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ImportJobEntity::getStatus, status.toUpperCase(Locale.ROOT));
        }
        if (query.getCreatedBy() != null) {
            wrapper.eq(ImportJobEntity::getCreatedBy, query.getCreatedBy());
        }
        if (query.getCreatedTimeFrom() != null) {
            wrapper.ge(ImportJobEntity::getCreatedTime, query.getCreatedTimeFrom());
        }
        if (query.getCreatedTimeTo() != null) {
            wrapper.le(ImportJobEntity::getCreatedTime, query.getCreatedTimeTo());
        }
        return wrapper.orderByDesc(ImportJobEntity::getCreatedTime).orderByDesc(ImportJobEntity::getId);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeImportType(String importType) {
        if (!StringUtils.hasText(importType)) {
            throw new IllegalArgumentException("导入类型不能为空");
        }
        return importType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeImportFilename(String importType, String filename) {
        return SafeFilename.normalize(filename, importType + ".csv", MAX_IMPORT_FILENAME_LENGTH);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private ImportJobResponse toResponse(ImportJobEntity job, List<ImportJobRowEntity> rows) {
        List<ImportRowResponse> rowResponses = rows.stream().map(this::toRowResponse).toList();
        return new ImportJobResponse(
                job.getId(),
                job.getImportType(),
                job.getFileName(),
                job.getStatus(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getErrorRows(),
                job.getCommittedRows(),
                job.getErrorMessage(),
                rowResponses
        );
    }

    private ImportRowResponse toRowResponse(ImportJobRowEntity row) {
        return new ImportRowResponse(
                row.getRowNo(),
                row.getValidFlag() != null && row.getValidFlag() == 1,
                validationSupport.rawFromJson(row.getRawJson()),
                validationSupport.normalizedFromJson(row.getNormalizedJson()),
                validationSupport.errorsFromJson(row.getErrorJson())
        );
    }
}

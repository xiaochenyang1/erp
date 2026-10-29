package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side operations for import templates, history, details and error exports. */
@Service
public class ImportJobQueryService {

    private final ImportJobMapper importJobMapper;
    private final ImportJobRowMapper importJobRowMapper;
    private final ImportTemplateRegistry templateRegistry;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ImportValidationSupport validationSupport;

    public ImportJobQueryService(
            ImportJobMapper importJobMapper,
            ImportJobRowMapper importJobRowMapper,
            ImportTemplateRegistry templateRegistry,
            AuditMetadataFactory auditMetadataFactory,
            ImportValidationSupport validationSupport
    ) {
        this.importJobMapper = importJobMapper;
        this.importJobRowMapper = importJobRowMapper;
        this.templateRegistry = templateRegistry;
        this.auditMetadataFactory = auditMetadataFactory;
        this.validationSupport = validationSupport;
    }

    public ResponseEntity<ByteArrayResource> template(String importType) {
        String normalizedImportType = normalizeImportType(importType);
        byte[] content = templateRegistry.csvTemplate(normalizedImportType).getBytes(StandardCharsets.UTF_8);
        String fileName = normalizedImportType.toLowerCase(Locale.ROOT) + "-template.csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new ByteArrayResource(content));
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> list(ImportJobPageQuery query) {
        ImportJobPageQuery safeQuery = query == null ? new ImportJobPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ImportJobEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
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
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("import-job-" + jobId + "-errors.csv", StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));
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

    /** Shared tenant guard used by the command collaborator. */
    ImportJobEntity requireCurrentJob(Long jobId) {
        AuditMetadata audit = auditMetadataFactory.current();
        ImportJobEntity job = importJobMapper.selectById(jobId);
        if (job == null
                || !Objects.equals(job.getCompanyId(), audit.companyId())
                || !Objects.equals(job.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("导入任务不存在");
        }
        return job;
    }

    /** Shared response mapping keeps command responses identical to query responses. */
    ImportJobResponse toResponse(ImportJobEntity job, List<ImportJobRowEntity> rows) {
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

    private LambdaQueryWrapper<ImportJobEntity> buildJobListQuery(
            AuditMetadata audit,
            ImportJobPageQuery query
    ) {
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

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

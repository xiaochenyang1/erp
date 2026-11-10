package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.imports.web.ImportJobResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Compatibility facade for import job queries and commands. */
@Service
public class ImportJobService {

    private final ImportJobQueryService importJobQueryService;
    private final ImportJobCommandService importJobCommandService;

    public ImportJobService(
            ImportJobQueryService importJobQueryService,
            ImportJobCommandService importJobCommandService
    ) {
        this.importJobQueryService = importJobQueryService;
        this.importJobCommandService = importJobCommandService;
    }

    public ResponseEntity<ByteArrayResource> template(String importType) {
        return importJobQueryService.template(importType);
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> list(ImportJobPageQuery query) {
        ImportJobPageQuery safeQuery = query == null ? new ImportJobPageQuery() : query;
        return importJobQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ByteArrayResource> exportErrorRows(Long jobId) {
        return importJobQueryService.exportErrorRows(jobId);
    }

    @Transactional
    public ImportJobResponse preview(String importType, MultipartFile file) {
        return importJobCommandService.preview(importType, file);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse detail(Long jobId) {
        return importJobQueryService.detail(jobId);
    }

    /**
     * Commit owns its transaction explicitly so a failed business transaction can be
     * recorded in a separate REQUIRES_NEW transaction. Do not add @Transactional here.
     */
    public ImportJobResponse commit(Long jobId) {
        return importJobCommandService.commit(jobId);
    }
}

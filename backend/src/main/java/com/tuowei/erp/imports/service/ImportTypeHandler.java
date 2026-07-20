package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ImportTypeHandler {

    String importType();

    ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context);

    default void afterValidate(List<ImportJobRowEntity> rows) {
    }

    int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit);

    default BatchCommitSession beginBatchCommit(ImportJobEntity job, AuditMetadata audit) {
        return null;
    }

    interface BatchCommitSession {

        void inspect(List<ImportJobRowEntity> rows);

        void beforeCommit();

        int commit(List<ImportJobRowEntity> rows);
    }

    record ImportValidationContext(Long companyId, Long accountBookId, Long userId, Map<String, Object> attributes) {
        public ImportValidationContext(Long companyId, Long accountBookId, Long userId) {
            this(companyId, accountBookId, userId, new HashMap<>());
        }
    }

    record ImportRowPlan(Map<String, Object> normalized, List<ImportRowErrorResponse> errors) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }
}

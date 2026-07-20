package com.tuowei.erp.imports;

import com.tuowei.erp.imports.service.ImportJobService;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ImportJobHistoryServiceTest {

    private static final long USER_ID = 886001L;

    @Autowired
    private ImportJobService importJobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from sys_import_job_row where job_id in (886101, 886102, 886103)");
        jdbcTemplate.update("delete from sys_import_job where id in (886101, 886102, 886103)");
    }

    @Test
    @WithErpUser(userId = USER_ID, companyId = 1, accountBookId = 1, authorities = "import:init:manage")
    void listReturnsCurrentAccountBookJobsWithFilters() {
        seedJob(886101L, 1L, 1L, "PRODUCT", "VALIDATED", "product.csv", LocalDateTime.of(2026, 5, 20, 9, 0));
        seedJob(886102L, 1L, 1L, "CUSTOMER", "INVALID", "customer.csv", LocalDateTime.of(2026, 5, 21, 9, 0));
        seedJob(886103L, 2L, 1L, "PRODUCT", "VALIDATED", "other-company.csv", LocalDateTime.of(2026, 5, 22, 9, 0));

        ImportJobPageQuery query = new ImportJobPageQuery();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setImportType("PRODUCT");
        query.setStatus("VALIDATED");
        query.setCreatedTimeFrom(LocalDateTime.of(2026, 5, 1, 0, 0));
        query.setCreatedTimeTo(LocalDateTime.of(2026, 5, 31, 23, 59));

        var page = importJobService.list(query);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).jobId()).isEqualTo(886101L);
        assertThat(page.records().get(0).rows()).isEmpty();
    }

    @Test
    @WithErpUser(userId = USER_ID, companyId = 1, accountBookId = 1, authorities = "import:init:manage")
    void exportErrorRowsReturnsOnlyInvalidRowsWithRawAndErrorColumns() {
        seedJob(886101L, 1L, 1L, "PRODUCT", "INVALID", "product.csv", LocalDateTime.of(2026, 5, 20, 9, 0));
        seedRow(886101L, 1, 1, "{\"product_code\":\"P001\",\"product_name\":\"正常商品\"}", "[]");
        seedRow(886101L, 2, 0, "{\"product_code\":\"\",\"product_name\":\"坏商品\"}", "[{\"column\":\"product_code\",\"message\":\"商品编码不能为空\"}]");

        ResponseEntity<ByteArrayResource> response = importJobService.exportErrorRows(886101L);

        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("import-job-886101-errors.csv");
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(org.springframework.http.MediaType.valueOf("text/csv"))).isTrue();
        String csv = new String(response.getBody().getByteArray(), StandardCharsets.UTF_8);
        assertThat(csv)
                .contains("rowNo,rawData,errors")
                .contains("2")
                .contains("product_code")
                .contains("商品编码不能为空")
                .contains("坏商品")
                .doesNotContain("正常商品");
    }

    private void seedJob(Long id, Long companyId, Long accountBookId, String importType, String status, String fileName, LocalDateTime createdTime) {
        jdbcTemplate.update("""
                insert into sys_import_job
                (id, company_id, account_book_id, import_type, file_name, status, total_rows, valid_rows, error_rows,
                 committed_rows, error_message, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, ?, 1, 0, 1, 0, null, ?, ?, ?, ?, 0)
                """, id, companyId, accountBookId, importType, fileName, status, USER_ID, createdTime, USER_ID, createdTime);
    }

    private void seedRow(Long jobId, int rowNo, int validFlag, String rawJson, String errorJson) {
        jdbcTemplate.update("""
                insert into sys_import_job_row
                (id, company_id, account_book_id, job_id, row_no, raw_json, normalized_json, valid_flag, error_json, created_time)
                values (?, 1, 1, ?, ?, ?, '{}', ?, ?, ?)
                """, 886200L + rowNo, jobId, rowNo, rawJson, validFlag, errorJson, LocalDateTime.of(2026, 5, 20, 9, rowNo));
    }
}

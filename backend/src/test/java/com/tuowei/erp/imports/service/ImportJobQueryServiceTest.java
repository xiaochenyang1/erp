package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.mapper.ImportJobRowMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ImportJobQueryServiceTest {

    private final ImportJobMapper importJobMapper = mock(ImportJobMapper.class);
    private final ImportJobRowMapper importJobRowMapper = mock(ImportJobRowMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ImportJobEntity.class);
        initTableInfo(ImportJobRowEntity.class);
    }

    @Test
    void templateNormalizesImportTypeAndPreservesUtf8CsvResponse() {
        var response = service(new ImportTemplateRegistry()).template(" product ");

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("product-template.csv");
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(
                org.springframework.http.MediaType.valueOf("text/csv")
        )).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody().getByteArray(), StandardCharsets.UTF_8))
                .contains("product_code,product_name,product_type")
                .contains("P001,标准商品");
    }

    @Test
    void listScopesTenantNormalizesFiltersAndCapsPagination() {
        AuditMetadata audit = audit();
        ImportJobEntity job = job();
        ImportJobPageQuery query = new ImportJobPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setImportType(" product ");
        query.setStatus(" validated ");
        query.setCreatedBy(77L);
        query.setCreatedTimeFrom(LocalDateTime.of(2026, 5, 1, 0, 0));
        query.setCreatedTimeTo(LocalDateTime.of(2026, 5, 31, 23, 59));
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ImportJobEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(job));
            return page;
        });

        var response = service(mock(ImportTemplateRegistry.class)).list(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.jobId()).isEqualTo(88L);
            assertThat(record.rows()).isEmpty();
        });
        var pageCaptor = org.mockito.ArgumentCaptor.forClass(Page.class);
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(importJobMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        LambdaQueryWrapper<ImportJobEntity> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("import_type")
                .contains("status")
                .contains("created_by")
                .contains("created_time")
                .contains("order by created_time desc,id desc");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(audit.companyId(), audit.accountBookId(), "PRODUCT", "VALIDATED", 77L);
    }

    @Test
    void exportErrorRowsScopesTenantAndSelectsOnlyInvalidRows() {
        AuditMetadata audit = audit();
        ImportJobEntity job = job();
        ImportJobRowEntity invalidRow = new ImportJobRowEntity();
        invalidRow.setRowNo(3);
        invalidRow.setRawJson("{\"product_code\":\"\",\"product_name\":\"坏商品\"}");
        invalidRow.setNormalizedJson("{}");
        invalidRow.setValidFlag(0);
        invalidRow.setErrorJson("[{\"column\":\"product_code\",\"message\":\"商品编码不能为空\"}]");
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobRowMapper.selectList(any())).thenReturn(List.of(invalidRow));

        var response = service(mock(ImportTemplateRegistry.class)).exportErrorRows(88L);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("import-job-88-errors.csv");
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody().getByteArray(), StandardCharsets.UTF_8))
                .contains("rowNo,rawData,errors")
                .contains("坏商品")
                .contains("商品编码不能为空");
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(importJobRowMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<ImportJobRowEntity> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id")
                .contains("job_id")
                .contains("valid_flag")
                .contains("order by row_no asc");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(audit.companyId(), audit.accountBookId(), 88L, 0);
    }

    private ImportJobQueryService service(ImportTemplateRegistry templateRegistry) {
        return new ImportJobQueryService(
                importJobMapper,
                importJobRowMapper,
                templateRegistry,
                auditMetadataFactory,
                new ImportValidationSupport(new ObjectMapper())
        );
    }

    private AuditMetadata audit() {
        return new AuditMetadata(9L, 11L, 22L, LocalDateTime.of(2026, 6, 2, 10, 0));
    }

    private ImportJobEntity job() {
        ImportJobEntity job = new ImportJobEntity();
        job.setId(88L);
        job.setCompanyId(11L);
        job.setAccountBookId(22L);
        job.setImportType(ImportConstants.PRODUCT);
        job.setFileName("product.csv");
        job.setStatus(ImportConstants.VALIDATED);
        job.setTotalRows(1);
        job.setValidRows(1);
        job.setErrorRows(0);
        job.setCommittedRows(0);
        job.setVersion(0);
        return job;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityType.getName()
        );
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

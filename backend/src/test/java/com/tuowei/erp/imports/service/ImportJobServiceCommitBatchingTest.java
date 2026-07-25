package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.ImportProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.mapper.ImportJobRowMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportJobResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ImportJobServiceCommitBatchingTest {

    private static final int DEFAULT_COMMIT_BATCH_SIZE = 500;

    private final ImportJobMapper importJobMapper = mock(ImportJobMapper.class);
    private final ImportJobRowMapper importJobRowMapper = mock(ImportJobRowMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final ImportTypeHandler handler = mock(ImportTypeHandler.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ImportJobRowEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                ImportJobRowEntity.class.getName()
        );
        assistant.setCurrentNamespace(ImportJobRowEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, ImportJobRowEntity.class);
    }

    @Test
    void commitsValidatedRowsInBatchesWithoutLoadingAllRows() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        ImportJobEntity job = job();
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.PRODUCT);
        when(handler.commit(any(), any(), any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(1).size());
        when(importJobRowMapper.selectList(any())).thenThrow(new AssertionError("commit must not load all import rows"));
        stubPagedRows();

        ImportJobResponse response = service().commit(88L);

        assertThat(response.status()).isEqualTo(ImportConstants.COMMITTED);
        assertThat(response.committedRows()).isEqualTo(DEFAULT_COMMIT_BATCH_SIZE + 1);
        assertThat(response.rows()).isEmpty();
        verify(importJobRowMapper, never()).selectList(any());
        ArgumentCaptor<LambdaQueryWrapper<ImportJobRowEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(importJobRowMapper, org.mockito.Mockito.times(2)).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues()).allSatisfy(this::assertTenantScoped);
        ArgumentCaptor<List<ImportJobRowEntity>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, org.mockito.Mockito.times(2)).commit(any(), rowsCaptor.capture(), any());
        assertThat(rowsCaptor.getAllValues())
                .extracting(rows -> rows.stream().map(ImportJobRowEntity::getRowNo).toList())
                .containsExactly(
                        IntStream.rangeClosed(1, DEFAULT_COMMIT_BATCH_SIZE).boxed().toList(),
                        List.of(DEFAULT_COMMIT_BATCH_SIZE + 1)
                );
    }

    @Test
    void detailScopesRowsByCompanyAndAccountBook() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        ImportJobEntity job = job();
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobRowMapper.selectList(any())).thenReturn(List.of());

        service().detail(88L);

        ArgumentCaptor<LambdaQueryWrapper<ImportJobRowEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(importJobRowMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void previewFailsWhenJobInsertDoesNotPersistRecord() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.insert(any(ImportJobEntity.class))).thenReturn(0);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(importJobRowMapper.insert(any(ImportJobRowEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.PRODUCT);
        when(handler.validate(anyInt(), any(), any()))
                .thenReturn(new ImportTypeHandler.ImportRowPlan(Map.of("productCode", "P001"), List.of()));

        assertThatThrownBy(() -> serviceWithRealParser().preview(ImportConstants.PRODUCT, productCsvFile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存导入任务失败");

        verify(importJobRowMapper, never()).insert(any(ImportJobRowEntity.class));
    }

    @Test
    void previewFailsWhenRowInsertDoesNotPersistRecord() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.insert(any(ImportJobEntity.class))).thenAnswer(invocation -> {
            ImportJobEntity job = invocation.getArgument(0);
            job.setId(8801L);
            return 1;
        });
        when(importJobRowMapper.insert(any(ImportJobRowEntity.class))).thenReturn(0);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.PRODUCT);
        when(handler.validate(anyInt(), any(), any()))
                .thenReturn(new ImportTypeHandler.ImportRowPlan(Map.of("productCode", "P001"), List.of()));

        assertThatThrownBy(() -> serviceWithRealParser().preview(ImportConstants.PRODUCT, productCsvFile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存导入明细失败");
    }

    @Test
    void usesBatchCommitSessionToInspectAllBatchesBeforeWritingRows() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        ImportJobEntity job = job();
        job.setImportType(ImportConstants.OPENING_ACCOUNT_BALANCE);
        ImportTypeHandler.BatchCommitSession session = mock(ImportTypeHandler.BatchCommitSession.class);
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.OPENING_ACCOUNT_BALANCE);
        when(handler.beginBatchCommit(job, audit)).thenReturn(session);
        when(session.commit(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(importJobRowMapper.selectList(any())).thenThrow(new AssertionError("commit must not load all import rows"));
        stubPagedRows();

        ImportJobResponse response = service().commit(88L);

        assertThat(response.status()).isEqualTo(ImportConstants.COMMITTED);
        assertThat(response.committedRows()).isEqualTo(DEFAULT_COMMIT_BATCH_SIZE + 1);
        verify(handler, never()).commit(any(), any(), any());
        verify(importJobRowMapper, never()).selectList(any());
        verify(importJobRowMapper, org.mockito.Mockito.times(4)).selectPage(any(), any());
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(session);
        inOrder.verify(session, org.mockito.Mockito.times(2)).inspect(any());
        inOrder.verify(session).beforeCommit();
        inOrder.verify(session, org.mockito.Mockito.times(2)).commit(any());
    }

    @Test
    void commitHidesUnexpectedRuntimeFailureMessageFromJobAndClient() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        ImportJobEntity job = job();
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.PRODUCT);
        when(handler.commit(any(), any(), any())).thenThrow(new IllegalStateException("jdbc password=secret leaked"));
        stubPagedRows();

        assertThatThrownBy(() -> service().commit(88L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("导入提交失败");

        org.mockito.ArgumentCaptor<ImportJobEntity> jobCaptor = org.mockito.ArgumentCaptor.forClass(ImportJobEntity.class);
        verify(importJobMapper, org.mockito.Mockito.atLeastOnce()).updateById(jobCaptor.capture());
        assertThat(jobCaptor.getAllValues())
                .filteredOn(entity -> ImportConstants.FAILED.equals(entity.getStatus()))
                .singleElement()
                .extracting(ImportJobEntity::getErrorMessage)
                .isEqualTo("导入提交失败");
    }

    @Test
    void retriesFailedJobWhenNoRowsWereCommitted() {
        AuditMetadata audit = new AuditMetadata(9L, 1L, 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        ImportJobEntity job = job();
        job.setStatus(ImportConstants.FAILED);
        job.setCommittedRows(0);
        job.setErrorMessage("期间已关闭");
        when(auditMetadataFactory.current()).thenReturn(audit);
        when(importJobMapper.selectById(88L)).thenReturn(job);
        when(importJobMapper.updateById(any(ImportJobEntity.class))).thenReturn(1);
        when(handler.importType()).thenReturn(ImportConstants.PRODUCT);
        when(handler.commit(any(), any(), any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(1).size());
        stubPagedRows();

        ImportJobResponse response = service().commit(88L);

        assertThat(response.status()).isEqualTo(ImportConstants.COMMITTED);
        assertThat(response.committedRows()).isEqualTo(DEFAULT_COMMIT_BATCH_SIZE + 1);
        assertThat(response.errorMessage()).isNull();
        verify(importJobMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(ImportJobEntity.class));
    }

    private void stubPagedRows() {
        when(importJobRowMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ImportJobRowEntity> page = invocation.getArgument(0);
            if (page.getCurrent() == 1) {
                page.setRecords(IntStream.rangeClosed(1, DEFAULT_COMMIT_BATCH_SIZE)
                        .mapToObj(this::row)
                        .toList());
                return page;
            }
            if (page.getCurrent() == 2) {
                page.setRecords(List.of(row(DEFAULT_COMMIT_BATCH_SIZE + 1)));
                return page;
            }
            page.setRecords(List.of());
            return page;
        });
    }

    private ImportJobService service() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new ImportJobService(
                importJobMapper,
                importJobRowMapper,
                mock(ImportTemplateRegistry.class),
                mock(CsvImportParser.class),
                objectMapper,
                auditMetadataFactory,
                new NoopTransactionManager(),
                List.of(handler),
                new ImportValidationSupport(objectMapper),
                mock(AccountPeriodGuard.class),
                ImportProperties.defaults()
        );
    }

    private ImportJobService serviceWithRealParser() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new ImportJobService(
                importJobMapper,
                importJobRowMapper,
                new ImportTemplateRegistry(),
                new CsvImportParser(ImportProperties.defaults()),
                objectMapper,
                auditMetadataFactory,
                new NoopTransactionManager(),
                List.of(handler),
                new ImportValidationSupport(objectMapper),
                mock(AccountPeriodGuard.class),
                ImportProperties.defaults()
        );
    }

    private MockMultipartFile productCsvFile() {
        String content = String.join(",", new ImportTemplateRegistry().headers(ImportConstants.PRODUCT))
                + "\nP001,示例商品,STANDARD,默认分类,规格A,件,箱,12,6901234567890,10.00,15.00,13.00,ACTIVE,0,0,0,0,备注\n";
        return new MockMultipartFile(
                "file",
                "product.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ImportJobEntity job() {
        ImportJobEntity job = new ImportJobEntity();
        job.setId(88L);
        job.setCompanyId(1L);
        job.setAccountBookId(1L);
        job.setImportType(ImportConstants.PRODUCT);
        job.setFileName("product.csv");
        job.setStatus(ImportConstants.VALIDATED);
        job.setTotalRows(DEFAULT_COMMIT_BATCH_SIZE + 1);
        job.setValidRows(DEFAULT_COMMIT_BATCH_SIZE + 1);
        job.setErrorRows(0);
        job.setCommittedRows(0);
        job.setVersion(0);
        return job;
    }

    private ImportJobRowEntity row(int rowNo) {
        ImportJobRowEntity row = new ImportJobRowEntity();
        row.setId((long) rowNo);
        row.setCompanyId(1L);
        row.setAccountBookId(1L);
        row.setJobId(88L);
        row.setRowNo(rowNo);
        row.setValidFlag(1);
        row.setRawJson("{}");
        row.setNormalizedJson("{}");
        row.setErrorJson("[]");
        return row;
    }

    private void assertTenantScoped(LambdaQueryWrapper<ImportJobRowEntity> wrapper) {
        String sqlSegment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sqlSegment)
                .contains("company_id")
                .contains("account_book_id");
    }

    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}

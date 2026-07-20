package com.tuowei.erp.system.readiness;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.model.ReadinessRunEntity;
import com.tuowei.erp.system.readiness.service.ReadinessService;
import com.tuowei.erp.system.readiness.web.ReadinessRunResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReadinessServiceQueryDefaultsTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ReadinessRunEntity.class);
    }

    @Test
    void listRunsTreatsNullQueryAsDefaultPagination() {
        ReadinessRunMapper runMapper = mock(ReadinessRunMapper.class);
        when(runMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ReadinessRunEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(run()));
            return page;
        });
        ReadinessService service = new ReadinessService(
                runMapper,
                mock(ReadinessItemMapper.class),
                mock(ReadinessEvidenceMapper.class),
                auditFactory()
        );

        PageResponse<ReadinessRunResponse> response = service.listRuns(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(ReadinessRunResponse::runNo).containsExactly("RDY20260102030405000");

        ArgumentCaptor<Page<ReadinessRunEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<ReadinessRunEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(runMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private static ReadinessRunEntity run() {
        ReadinessRunEntity entity = new ReadinessRunEntity();
        entity.setId(1L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunNo("RDY20260102030405000");
        entity.setReleaseCommit("abc1234");
        entity.setEnvironment("PREPROD");
        entity.setStatus("DRAFT");
        entity.setDecision("PENDING");
        entity.setStartedBy(AUDIT.userId());
        entity.setStartedTime(AUDIT.now());
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

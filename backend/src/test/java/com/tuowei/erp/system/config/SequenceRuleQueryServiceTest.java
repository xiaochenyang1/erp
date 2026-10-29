package com.tuowei.erp.system.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SequenceRuleMapper;
import com.tuowei.erp.system.config.model.SequenceRuleEntity;
import com.tuowei.erp.system.config.service.SequenceRuleQueryService;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SequenceRuleQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1001L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 19, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SequenceRuleEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), SequenceRuleEntity.class.getName()
        );
        assistant.setCurrentNamespace(SequenceRuleEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SequenceRuleEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesCompanyAndAccountBook() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        Page<SequenceRuleEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(rule(7001L, AUDIT.companyId(), AUDIT.accountBookId())));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        SequenceRulePageQuery query = new SequenceRulePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  SALES ");
        query.setStatus(" active ");

        var response = service(mapper).list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.bizType()).isEqualTo("SALES_ORDER");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });

        ArgumentCaptor<Page<SequenceRuleEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SequenceRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("company_id", "account_book_id", "biz_type", "prefix", "status")
                .contains("order by biz_type asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%SALES%", "ACTIVE");
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service(mapper).list(null);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(20L);
    }

    @Test
    void getByIdUsesTenantScopedSelectAndMapsResponse() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(rule(7001L, AUDIT.companyId(), AUDIT.accountBookId()));

        var response = service(mapper).getById(7001L);

        assertThat(response.id()).isEqualTo(7001L);
        assertThat(response.companyId()).isEqualTo(AUDIT.companyId());
        assertThat(response.accountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(response.bizType()).isEqualTo("SALES_ORDER");
        assertThat(response.prefix()).isEqualTo("SO-");
        ArgumentCaptor<LambdaQueryWrapper<SequenceRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("id", "company_id", "account_book_id");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(7001L, AUDIT.companyId(), AUDIT.accountBookId());
    }

    @Test
    void getByIdRejectsMissingOrCrossTenantRule() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        SequenceRuleQueryService service = service(mapper);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("编号规则不存在");
    }

    private SequenceRuleQueryService service(SequenceRuleMapper mapper) {
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        return new SequenceRuleQueryService(mapper, auditMetadataFactory);
    }

    private SequenceRuleEntity rule(Long id, Long companyId, Long accountBookId) {
        SequenceRuleEntity entity = new SequenceRuleEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setBizType("SALES_ORDER");
        entity.setPrefix("SO-");
        entity.setDatePattern("yyyyMMdd");
        entity.setSeqLength(5);
        entity.setCurrentValue(12L);
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        return entity;
    }
}

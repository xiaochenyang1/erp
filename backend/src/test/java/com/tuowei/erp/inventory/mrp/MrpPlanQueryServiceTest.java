package com.tuowei.erp.inventory.mrp;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunLineMapper;
import com.tuowei.erp.inventory.mrp.mapper.MrpRunMapper;
import com.tuowei.erp.inventory.mrp.model.MrpRunEntity;
import com.tuowei.erp.inventory.mrp.model.MrpRunLineEntity;
import com.tuowei.erp.inventory.mrp.service.MrpPlanQueryService;
import com.tuowei.erp.inventory.mrp.web.MrpRunPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MrpPlanQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 13, 11, 30)
    );
    private static final Long RUN_ID = 8101L;
    private static final Long PURCHASE_PRODUCT_ID = 8201L;
    private static final Long PRODUCTION_PRODUCT_ID = 8202L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final MrpRunMapper mrpRunMapper = mock(MrpRunMapper.class);
    private final MrpRunLineMapper mrpRunLineMapper = mock(MrpRunLineMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(MrpRunEntity.class);
        initTableInfo(MrpRunLineEntity.class);
    }

    @Test
    void listRunsScopesTenantNormalizesStatusAndClampsPagination() {
        stubAudit();
        MrpRunEntity run = run();
        run.setPurchaseCount(null);
        run.setProductionCount(null);
        when(mrpRunMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<MrpRunEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(run));
            return page;
        });
        MrpRunPageQuery query = new MrpRunPageQuery();
        query.setPageNo(0L);
        query.setPageSize(999L);
        query.setStatus("  open  ");

        var result = service().listRuns(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(RUN_ID);
            assertThat(response.asOfDate()).isEqualTo("2026-08-13");
            assertThat(response.purchaseCount()).isZero();
            assertThat(response.productionCount()).isZero();
        });

        ArgumentCaptor<Page<MrpRunEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<MrpRunEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mrpRunMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "status", "order by");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0, "OPEN");
    }

    @Test
    void listRunsUsesDefaultPaginationForNullQuery() {
        stubAudit();
        when(mrpRunMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().listRuns(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
        assertThat(result.records()).isEmpty();
    }

    @Test
    void getByIdGroupsSuggestionsAndHydratesProductsWithTenantSql() {
        stubAudit();
        when(mrpRunMapper.selectById(RUN_ID)).thenReturn(run());
        when(mrpRunLineMapper.selectList(any())).thenReturn(List.of(
                line(8301L, 1, PURCHASE_PRODUCT_ID, "PURCHASE"),
                line(8302L, 2, PRODUCTION_PRODUCT_ID, "PRODUCTION")
        ));
        when(jdbcTemplate.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(List.of(
                        Map.of(
                                "id", PURCHASE_PRODUCT_ID,
                                "product_code", "MAT-001",
                                "product_name", "原料A"
                        ),
                        Map.of(
                                "id", PRODUCTION_PRODUCT_ID,
                                "product_code", "FG-001",
                                "product_name", "成品A"
                        )
                ));

        var result = service().getById(RUN_ID);

        assertThat(result.purchaseLines()).singleElement().satisfies(response -> {
            assertThat(response.productCode()).isEqualTo("MAT-001");
            assertThat(response.productName()).isEqualTo("原料A");
            assertThat(response.netQty()).isEqualByComparingTo("7.0000");
        });
        assertThat(result.productionLines()).singleElement().satisfies(response -> {
            assertThat(response.productCode()).isEqualTo("FG-001");
            assertThat(response.productName()).isEqualTo("成品A");
            assertThat(response.bomId()).isEqualTo(8401L);
        });

        ArgumentCaptor<LambdaQueryWrapper<MrpRunLineEntity>> lineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mrpRunLineMapper).selectList(lineQueryCaptor.capture());
        assertThat(lineQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "run_id", "deleted_flag", "order by");
        assertThat(lineQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), RUN_ID, 0);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(AUDIT.companyId()),
                eq(AUDIT.accountBookId())
        );
        assertThat(sqlCaptor.getValue().toLowerCase(Locale.ROOT))
                .contains("from md_product", "company_id = ?", "account_book_id = ?", "deleted_flag = 0");
    }

    @Test
    void getByIdLeavesProductDisplayFieldsEmptyWhenMasterDataIsMissing() {
        stubAudit();
        when(mrpRunMapper.selectById(RUN_ID)).thenReturn(run());
        when(mrpRunLineMapper.selectList(any())).thenReturn(List.of(
                line(8301L, 1, PURCHASE_PRODUCT_ID, "PURCHASE")
        ));
        when(jdbcTemplate.queryForList(anyString(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenReturn(List.of());

        var result = service().getById(RUN_ID);

        assertThat(result.purchaseLines()).singleElement().satisfies(response -> {
            assertThat(response.productCode()).isNull();
            assertThat(response.productName()).isNull();
        });
    }

    @Test
    void requireRunRejectsCrossAccountBookRecord() {
        MrpRunEntity run = run();
        run.setAccountBookId(999L);
        when(mrpRunMapper.selectById(RUN_ID)).thenReturn(run);

        assertThatThrownBy(() -> service().requireRun(RUN_ID, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MRP计划不存在");
    }

    @Test
    void requireLineRejectsCrossTenantAndWrongRunRecords() {
        MrpRunLineEntity line = line(8301L, 1, PURCHASE_PRODUCT_ID, "PURCHASE");
        line.setCompanyId(999L);
        when(mrpRunLineMapper.selectById(line.getId())).thenReturn(line);

        assertThatThrownBy(() -> service().requireLine(RUN_ID, line.getId(), AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MRP建议行不存在");

        line.setCompanyId(AUDIT.companyId());
        assertThatThrownBy(() -> service().requireLine(9999L, line.getId(), AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MRP建议行不存在");
    }

    private MrpPlanQueryService service() {
        return new MrpPlanQueryService(
                jdbcTemplate,
                auditMetadataFactory,
                mrpRunMapper,
                mrpRunLineMapper
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private MrpRunEntity run() {
        MrpRunEntity entity = new MrpRunEntity();
        entity.setId(RUN_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunNo("MRP202608130001");
        entity.setAsOfDate(LocalDate.of(2026, 8, 13));
        entity.setStatus("OPEN");
        entity.setPurchaseCount(1);
        entity.setProductionCount(1);
        entity.setDeletedFlag(0);
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private MrpRunLineEntity line(Long id, int lineNo, Long productId, String suggestionType) {
        MrpRunLineEntity entity = new MrpRunLineEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunId(RUN_ID);
        entity.setLineNo(lineNo);
        entity.setProductId(productId);
        entity.setSuggestionType(suggestionType);
        entity.setDemandQty(new BigDecimal("10"));
        entity.setOnHandQty(new BigDecimal("2"));
        entity.setOpenSupplyQty(new BigDecimal("1"));
        entity.setNetQty(new BigDecimal("7"));
        entity.setBomId("PRODUCTION".equals(suggestionType) ? 8401L : null);
        entity.setReason("测试建议");
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
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

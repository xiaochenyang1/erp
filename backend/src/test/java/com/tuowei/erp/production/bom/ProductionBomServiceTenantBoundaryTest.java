package com.tuowei.erp.production.bom;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.service.ProductionBomNumberService;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineRequest;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionBomServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9911L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 19, 0)
    );
    private static final Long FINISHED_PRODUCT_ID = 1001L;
    private static final Long MATERIAL_PRODUCT_ID = 1002L;

    private final ProductionBomMapper bomMapper = mock(ProductionBomMapper.class);
    private final ProductionBomLineMapper lineMapper = mock(ProductionBomLineMapper.class);
    private final ProductionBomNumberService numberService = mock(ProductionBomNumberService.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionBomEntity.class);
        initTableInfo(ProductionBomLineEntity.class);
    }

    @Test
    void listScopesBomQueryByCompanyAndAccountBook() {
        stubAudit();
        when(bomMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionBomEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionBomPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionBomEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bomMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void getByIdRejectsBomFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(bomMapper.selectById(2001L)).thenReturn(activeBom(2001L, AUDIT.companyId(), 999L));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().getById(2001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOM不存在");
    }

    @Test
    void createScopesDuplicateActiveBomCheckByCompanyAndAccountBook() {
        stubCreateDependencies();
        when(bomMapper.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ProductionBomEntity> wrapper = invocation.getArgument(0);
            String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
            return sql.contains("account_book_id") ? 0L : 1L;
        });

        assertThatCode(() -> service().create(createRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void createRejectsFinishedProductFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(productValidator.requireProduct(eq(FINISHED_PRODUCT_ID), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));
        when(bomMapper.selectCount(any())).thenReturn(0L);
        stubSuccessfulInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createRejectsMaterialProductFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(productValidator.requireProducts(any(), eq(AUDIT.companyId()), eq(AUDIT.accountBookId())))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));
        when(bomMapper.selectCount(any())).thenReturn(0L);
        stubSuccessfulInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void updateStopsBeforeReplacingLinesWhenBomUpdateConflicts() {
        stubAudit();
        when(bomMapper.selectById(2001L)).thenReturn(activeBom(2001L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(bomMapper.updateById(any(ProductionBomEntity.class))).thenReturn(0);
        when(lineMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service().update(2001L, new ProductionBomUpdateRequest(
                BigDecimal.ONE,
                "conflict",
                List.of(new ProductionBomLineRequest(MATERIAL_PRODUCT_ID, BigDecimal.ONE, BigDecimal.ZERO, "line"))
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("BOM已被其他操作修改，请刷新后重试");
        verify(lineMapper, never()).delete(any());
        verify(lineMapper, never()).insert(any(ProductionBomLineEntity.class));
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubCreateDependencies() {
        stubAudit();
        stubSuccessfulInsert();
    }

    private void stubSuccessfulInsert() {
        when(numberService.nextBomNo(AUDIT.now().toLocalDate())).thenReturn("BOM-9911");
        when(bomMapper.insert(any(ProductionBomEntity.class))).thenAnswer(invocation -> {
            ProductionBomEntity bom = invocation.getArgument(0);
            bom.setId(2002L);
            return 1;
        });
        when(lineMapper.insert(any(ProductionBomLineEntity.class))).thenReturn(1);
        when(lineMapper.selectList(any())).thenReturn(List.of());
    }

    private void assertTenantScoped(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private ProductionBomService service() {
        return new ProductionBomService(
                bomMapper,
                lineMapper,
                numberService,
                productMapper,
                productValidator,
                auditMetadataFactory
        );
    }

    private ProductionBomCreateRequest createRequest() {
        return new ProductionBomCreateRequest(
                FINISHED_PRODUCT_ID,
                BigDecimal.ONE,
                "tenant boundary",
                List.of(new ProductionBomLineRequest(MATERIAL_PRODUCT_ID, BigDecimal.ONE, BigDecimal.ZERO, "line"))
        );
    }

    private ProductionBomEntity activeBom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = new ProductionBomEntity();
        bom.setId(id);
        bom.setCompanyId(companyId);
        bom.setAccountBookId(accountBookId);
        bom.setBomNo("BOM-" + id);
        bom.setProductId(FINISHED_PRODUCT_ID);
        bom.setBaseQty(BigDecimal.ONE);
        bom.setStatus("ACTIVE");
        bom.setDeletedFlag(0);
        return bom;
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

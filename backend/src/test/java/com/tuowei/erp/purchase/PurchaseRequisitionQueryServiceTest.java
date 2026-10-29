package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionEntity;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionLineEntity;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionQueryService;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionPageQuery;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseRequisitionQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            701L, 801L, 901L, LocalDateTime.parse("2026-08-26T15:00:00")
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseRequisitionEntity.class);
        initTableInfo(PurchaseRequisitionLineEntity.class);
    }

    @Test
    void listBatchesLinesAndProductsAndScopesBothQueries() {
        PurchaseRequisitionMapper requisitionMapper = mock(PurchaseRequisitionMapper.class);
        PurchaseRequisitionLineMapper lineMapper = mock(PurchaseRequisitionLineMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        PurchaseRequisitionQueryService service = new PurchaseRequisitionQueryService(
                requisitionMapper, lineMapper, productMapper, auditMetadataFactory
        );
        PurchaseRequisitionPageQuery query = new PurchaseRequisitionPageQuery();
        query.setPageNo(2L);
        query.setPageSize(30L);
        query.setStatus(" approved ");
        query.setKeyword(" PR- ");
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(requisitionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<PurchaseRequisitionEntity> page = invocation.getArgument(0);
            page.setTotal(2);
            page.setRecords(List.of(requisition(11L), requisition(12L)));
            return page;
        });
        when(lineMapper.selectList(any())).thenReturn(List.of(line(101L, 11L, 501L), line(102L, 12L, 502L)));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product(501L, AUDIT.accountBookId())));

        PageResponse<PurchaseRequisitionResponse> result = service.list(query);

        assertThat(result.pageNo()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(30);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).lines().get(0).productCode()).isEqualTo("P-501");
        assertThat(result.records().get(1).lines().get(0).productCode()).isNull();
        verify(lineMapper).selectList(any());
        verify(productMapper).selectBatchIds(any());

        @SuppressWarnings({"unchecked", "rawtypes"})
        var headCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(requisitionMapper).selectPage(any(), headCaptor.capture());
        String headSql = headCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(headSql).contains("company_id", "account_book_id", "deleted_flag", "status", "requisition_no");

        @SuppressWarnings({"unchecked", "rawtypes"})
        var lineCaptor = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lineMapper).selectList(lineCaptor.capture());
        String lineSql = lineCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(lineSql).contains("company_id", "account_book_id", "requisition_id", "deleted_flag");
    }

    @Test
    void emptyPageDoesNotQueryLinesOrProducts() {
        PurchaseRequisitionMapper requisitionMapper = mock(PurchaseRequisitionMapper.class);
        PurchaseRequisitionLineMapper lineMapper = mock(PurchaseRequisitionLineMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        PurchaseRequisitionQueryService service = new PurchaseRequisitionQueryService(
                requisitionMapper, lineMapper, productMapper, auditMetadataFactory
        );
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(requisitionMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.list(null).records()).isEmpty();

        verify(lineMapper, never()).selectList(any());
        verify(productMapper, never()).selectBatchIds(any());
    }

    @Test
    void getByIdRejectsDifferentAccountBookWithinSameCompany() {
        PurchaseRequisitionMapper requisitionMapper = mock(PurchaseRequisitionMapper.class);
        PurchaseRequisitionLineMapper lineMapper = mock(PurchaseRequisitionLineMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        PurchaseRequisitionQueryService service = new PurchaseRequisitionQueryService(
                requisitionMapper, lineMapper, productMapper, auditMetadataFactory
        );
        PurchaseRequisitionEntity entity = requisition(11L);
        entity.setAccountBookId(9999L);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(requisitionMapper.selectById(11L)).thenReturn(entity);

        assertThatThrownBy(() -> service.getById(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请购单不存在");
    }

    private static PurchaseRequisitionEntity requisition(Long id) {
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRequisitionNo("PR-" + id);
        entity.setRequisitionDate(LocalDate.of(2026, 8, 26));
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static PurchaseRequisitionLineEntity line(Long id, Long requisitionId, Long productId) {
        PurchaseRequisitionLineEntity line = new PurchaseRequisitionLineEntity();
        line.setId(id);
        line.setCompanyId(AUDIT.companyId());
        line.setAccountBookId(AUDIT.accountBookId());
        line.setRequisitionId(requisitionId);
        line.setLineNo(1);
        line.setProductId(productId);
        line.setQty(new BigDecimal("2.0000"));
        line.setDeletedFlag(0);
        return line;
    }

    private static ProductEntity product(Long id, Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCompanyId(AUDIT.companyId());
        product.setAccountBookId(accountBookId);
        product.setProductCode("P-" + id);
        product.setProductName("商品 " + id);
        return product;
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

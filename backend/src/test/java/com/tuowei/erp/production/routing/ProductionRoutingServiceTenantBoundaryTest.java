package com.tuowei.erp.production.routing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.service.ProductionRoutingService;
import com.tuowei.erp.production.routing.web.ProductionRoutingCreateRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationRequest;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionRoutingServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT =
            new AuditMetadata(9913L, 101L, 202L, LocalDateTime.of(2026, 7, 8, 11, 30));

    private final ProductionRoutingMapper routingMapper = mock(ProductionRoutingMapper.class);
    private final ProductionRoutingOperationMapper routingOperationMapper = mock(ProductionRoutingOperationMapper.class);
    private final ProductionBomMapper bomMapper = mock(ProductionBomMapper.class);
    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionRoutingEntity.class);
    }

    @Test
    void listScopesRoutingQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionRoutingEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionRoutingPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionRoutingEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(routingMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void createRejectsBomFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(bomMapper.selectById(8101L)).thenReturn(activeBom(8101L, AUDIT.companyId(), 999L));
        when(workCenterMapper.selectById(8201L))
                .thenReturn(activeWorkCenter(8201L, AUDIT.companyId(), AUDIT.accountBookId()));

        assertThatThrownBy(() -> service().create(new ProductionRoutingCreateRequest(
                        "RT-8101",
                        "tenant",
                        8101L,
                        "routing",
                        List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 8201L, new BigDecimal("10.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("BOM不存在或已停用");
    }

    @Test
    void createRejectsWorkCenterFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(bomMapper.selectById(8102L)).thenReturn(activeBom(8102L, AUDIT.companyId(), AUDIT.accountBookId()));
        when(workCenterMapper.selectById(8202L)).thenReturn(activeWorkCenter(8202L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().create(new ProductionRoutingCreateRequest(
                        "RT-8102",
                        "tenant wc",
                        8102L,
                        "routing",
                        List.of(new ProductionRoutingOperationRequest("OP-10", "工序", 8202L, new BigDecimal("10.00"), "line"))
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在或已停用");
    }

    @Test
    void disableRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectById(8301L)).thenReturn(activeRouting(8301L, AUDIT.companyId(), 999L, 8102L));

        assertThatThrownBy(() -> service().disable(8301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线不存在");
    }

    private ProductionRoutingService service() {
        return new ProductionRoutingService(routingMapper, routingOperationMapper, bomMapper, workCenterMapper, auditMetadataFactory);
    }

    private ProductionBomEntity activeBom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity entity = new ProductionBomEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setBomNo("BOM-" + id);
        entity.setProductId(1L);
        entity.setBaseQty(BigDecimal.ONE);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionWorkCenterEntity activeWorkCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWorkCenterCode("WC-" + id);
        entity.setWorkCenterName("WC");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionRoutingEntity activeRouting(Long id, Long companyId, Long accountBookId, Long bomId) {
        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setRoutingCode("RT-" + id);
        entity.setRoutingName("RT");
        entity.setBomId(bomId);
        entity.setStatus("ACTIVE");
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

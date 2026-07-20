package com.tuowei.erp.production.workcenter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.service.ProductionWorkCenterService;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionWorkCenterServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT =
            new AuditMetadata(9912L, 101L, 202L, LocalDateTime.of(2026, 7, 8, 11, 0));

    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final ProductionRoutingMapper routingMapper = mock(ProductionRoutingMapper.class);
    private final ProductionRoutingOperationMapper routingOperationMapper = mock(ProductionRoutingOperationMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionWorkCenterEntity.class);
    }

    @Test
    void listScopesQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionWorkCenterEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ProductionWorkCenterPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ProductionWorkCenterEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(workCenterMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void getByIdRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectById(7001L)).thenReturn(activeWorkCenter(7001L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().getById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在");
    }

    @Test
    void disableRejectsDifferentAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workCenterMapper.selectById(7002L)).thenReturn(activeWorkCenter(7002L, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().disable(7002L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工作中心不存在");
    }

    private ProductionWorkCenterService service() {
        return new ProductionWorkCenterService(workCenterMapper, routingMapper, routingOperationMapper, auditMetadataFactory);
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

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

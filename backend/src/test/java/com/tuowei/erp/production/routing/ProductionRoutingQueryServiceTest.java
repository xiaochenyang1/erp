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
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.routing.service.ProductionRoutingQueryService;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
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

class ProductionRoutingQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 10, 30)
    );

    private final ProductionRoutingMapper routingMapper = mock(ProductionRoutingMapper.class);
    private final ProductionRoutingOperationMapper routingOperationMapper = mock(ProductionRoutingOperationMapper.class);
    private final ProductionBomMapper bomMapper = mock(ProductionBomMapper.class);
    private final ProductionWorkCenterMapper workCenterMapper = mock(ProductionWorkCenterMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProductionRoutingEntity.class);
        initTableInfo(ProductionRoutingOperationEntity.class);
        initTableInfo(ProductionBomEntity.class);
        initTableInfo(ProductionWorkCenterEntity.class);
    }

    @Test
    void listNormalizesPaginationFiltersAndTenantScope() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProductionRoutingEntity> page = invocation.getArgument(0);
            page.setTotal(0);
            page.setRecords(List.of());
            return page;
        });
        ProductionRoutingPageQuery query = new ProductionRoutingPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  rt-001  ");
        query.setStatus(" disabled ");
        query.setBomId(901L);

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.records()).isEmpty();
        ArgumentCaptor<Page<ProductionRoutingEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<ProductionRoutingEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(routingMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("routing_code")
                .contains("routing_name")
                .contains("status")
                .contains("bom_id")
                .contains("order by routing_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%rt-001%", "DISABLED", 901L);
    }

    @Test
    void listNullQueryUsesDefaultPage() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<ProductionRoutingEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(routingMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void getByIdHydratesBomOperationsAndWorkCenterWithTenantSafeMappings() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ProductionRoutingEntity routing = routing(701L, AUDIT.companyId(), AUDIT.accountBookId(), 901L);
        ProductionBomEntity bom = bom(901L, AUDIT.companyId(), AUDIT.accountBookId());
        ProductionRoutingOperationEntity operation = operation(801L, 701L, 1, 1001L);
        ProductionWorkCenterEntity workCenter = workCenter(1001L, AUDIT.companyId(), AUDIT.accountBookId());
        when(routingMapper.selectById(701L)).thenReturn(routing);
        when(bomMapper.selectById(901L)).thenReturn(bom);
        when(routingOperationMapper.selectList(any())).thenReturn(List.of(operation));
        when(workCenterMapper.selectList(any())).thenReturn(List.of(workCenter));

        ProductionRoutingResponse response = service().getById(701L);

        assertThat(response.id()).isEqualTo(701L);
        assertThat(response.bomNo()).isEqualTo("BOM-901");
        assertThat(response.productId()).isEqualTo(3001L);
        assertThat(response.operations()).singleElement().satisfies(line -> {
            assertThat(line.lineNo()).isEqualTo(1);
            assertThat(line.operationCode()).isEqualTo("OP-10");
            assertThat(line.workCenterCode()).isEqualTo("WC-1001");
            assertThat(line.workCenterName()).isEqualTo("装配中心");
            assertThat(line.standardMinutes()).isEqualByComparingTo("12.50");
        });
    }

    @Test
    void getByIdReturnsNullDisplayReferencesWhenAssociationsAreOutsideTenant() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ProductionRoutingEntity routing = routing(702L, AUDIT.companyId(), AUDIT.accountBookId(), 902L);
        ProductionBomEntity foreignBom = bom(902L, AUDIT.companyId(), 9999L);
        ProductionRoutingOperationEntity operation = operation(802L, 702L, 1, 1002L);
        when(routingMapper.selectById(702L)).thenReturn(routing);
        when(bomMapper.selectById(902L)).thenReturn(foreignBom);
        when(routingOperationMapper.selectList(any())).thenReturn(List.of(operation));
        when(workCenterMapper.selectList(any())).thenReturn(List.of());

        ProductionRoutingResponse response = service().getById(702L);

        assertThat(response.bomNo()).isNull();
        assertThat(response.productId()).isNull();
        assertThat(response.operations()).singleElement().satisfies(line -> {
            assertThat(line.workCenterCode()).isNull();
            assertThat(line.workCenterName()).isNull();
        });
    }

    @Test
    void getByIdRejectsForeignOrDeletedRouting() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(routingMapper.selectById(703L)).thenReturn(routing(703L, AUDIT.companyId(), 9999L, 903L));
        assertThatThrownBy(() -> service().getById(703L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线不存在");

        ProductionRoutingEntity deleted = routing(704L, AUDIT.companyId(), AUDIT.accountBookId(), 904L);
        deleted.setDeletedFlag(1);
        when(routingMapper.selectById(704L)).thenReturn(deleted);
        assertThatThrownBy(() -> service().getById(704L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工艺路线不存在");
    }

    private ProductionRoutingQueryService service() {
        return new ProductionRoutingQueryService(
                routingMapper,
                routingOperationMapper,
                bomMapper,
                workCenterMapper,
                auditMetadataFactory
        );
    }

    private ProductionRoutingEntity routing(Long id, Long companyId, Long accountBookId, Long bomId) {
        ProductionRoutingEntity entity = new ProductionRoutingEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setRoutingCode("RT-" + id);
        entity.setRoutingName("标准路线");
        entity.setBomId(bomId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("备注");
        return entity;
    }

    private ProductionBomEntity bom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity entity = new ProductionBomEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setBomNo("BOM-" + id);
        entity.setProductId(3001L);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductionRoutingOperationEntity operation(Long id, Long routingId, int lineNo, Long workCenterId) {
        ProductionRoutingOperationEntity entity = new ProductionRoutingOperationEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRoutingId(routingId);
        entity.setLineNo(lineNo);
        entity.setOperationCode("OP-10");
        entity.setOperationName("装配");
        entity.setWorkCenterId(workCenterId);
        entity.setStandardMinutes(new BigDecimal("12.50"));
        return entity;
    }

    private ProductionWorkCenterEntity workCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = new ProductionWorkCenterEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWorkCenterCode("WC-" + id);
        entity.setWorkCenterName("装配中心");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
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

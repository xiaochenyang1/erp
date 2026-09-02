package com.tuowei.erp.masterdata.location.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.location.web.LocationPageQuery;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
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

@SuppressWarnings({"unchecked", "rawtypes"})
class LocationQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7101L, 8101L, 9101L, LocalDateTime.of(2026, 8, 21, 9, 0)
    );
    private static final Long LOCATION_ID = 101L;
    private static final Long WAREHOUSE_ID = 201L;

    private final LocationMapper locationMapper = mock(LocationMapper.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(LocationEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                LocationEntity.class.getName()
        );
        assistant.setCurrentNamespace(LocationEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, LocationEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationScopesTenantAndHydratesWarehouseName() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(locationMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<LocationEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(location(AUDIT.accountBookId())));
            return page;
        });
        when(warehouseMapper.selectBatchIds(any())).thenReturn(List.of(warehouse(AUDIT.companyId(), AUDIT.accountBookId(), "成品仓")));

        LocationPageQuery query = new LocationPageQuery();
        query.setPageNo(0L);
        query.setPageSize(999L);
        query.setWarehouseId(WAREHOUSE_ID);
        query.setStatus(" inactive ");
        query.setKeyword("  A-01  ");

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(LOCATION_ID);
            assertThat(record.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(record.warehouseName()).isEqualTo("成品仓");
            assertThat(record.locationCode()).isEqualTo("A-01");
            assertThat(record.isDefault()).isTrue();
        });

        ArgumentCaptor<Page<LocationEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<LocationEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(locationMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("warehouse_id")
                .contains("status")
                .contains("location_code")
                .contains("location_name")
                .contains("order by is_default desc")
                .contains("location_code asc")
                .contains("id desc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), WAREHOUSE_ID, "INACTIVE", "%A-01%");
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(locationMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<LocationEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(locationMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void getByIdRejectsCrossAccountBookAndSoftDeletedLocations() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(locationMapper.selectById(LOCATION_ID)).thenReturn(location(9999L));

        assertThatThrownBy(() -> service().getById(LOCATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库位不存在");

        LocationEntity deleted = location(AUDIT.accountBookId());
        deleted.setDeletedFlag(1);
        when(locationMapper.selectById(LOCATION_ID)).thenReturn(deleted);
        assertThatThrownBy(() -> service().getById(LOCATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库位不存在");
    }

    @Test
    void getByIdMapsWarehouseName() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(locationMapper.selectById(LOCATION_ID)).thenReturn(location(AUDIT.accountBookId()));
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(AUDIT.companyId(), AUDIT.accountBookId(), "原料仓"));

        var response = service().getById(LOCATION_ID);

        assertThat(response.warehouseName()).isEqualTo("原料仓");
        assertThat(response.locationName()).isEqualTo("A区01");
    }

    @Test
    void listRejectsUnsupportedStatusBeforeQuery() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        LocationPageQuery query = new LocationPageQuery();
        query.setStatus("paused");

        assertThatThrownBy(() -> service().list(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态仅支持 ACTIVE/INACTIVE");
    }

    private LocationQueryService service() {
        return new LocationQueryService(locationMapper, warehouseMapper, auditMetadataFactory);
    }

    private LocationEntity location(Long accountBookId) {
        LocationEntity entity = new LocationEntity();
        entity.setId(LOCATION_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setLocationCode("A-01");
        entity.setLocationName("A区01");
        entity.setIsDefault(1);
        entity.setStatus("INACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        return entity;
    }

    private WarehouseEntity warehouse(Long companyId, Long accountBookId, String name) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseName(name);
        entity.setDeletedFlag(0);
        return entity;
    }
}

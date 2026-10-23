package com.tuowei.erp.masterdata.warehouse.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
class WarehouseQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7501L, 8501L, 9501L, LocalDateTime.of(2026, 8, 20, 18, 0)
    );
    private static final Long WAREHOUSE_ID = 501L;

    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(WarehouseEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), WarehouseEntity.class.getName()
        );
        assistant.setCurrentNamespace(WarehouseEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, WarehouseEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndScopesTenant() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<WarehouseEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(warehouse(AUDIT.accountBookId())));
            return page;
        });
        WarehousePageQuery query = new WarehousePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  W-001  ");
        query.setStatus(" active ");
        query.setDeptId(601L);
        query.setManagerUserId(701L);

        var response = service().list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(WAREHOUSE_ID);
            assertThat(record.warehouseCode()).isEqualTo("W-001");
        });

        ArgumentCaptor<Page<WarehouseEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<WarehouseEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(warehouseMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("warehouse_code")
                .contains("warehouse_name")
                .contains("status")
                .contains("dept_id")
                .contains("manager_user_id")
                .contains("order by warehouse_code asc");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "%W-001%", "ACTIVE", 601L, 701L);
    }

    @Test
    void listNullQueryUsesDefaultPagination() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().list(null);

        ArgumentCaptor<Page<WarehouseEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(warehouseMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
    }

    @Test
    void getByIdRejectsWarehouseFromAnotherAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(9999L));

        assertThatThrownBy(() -> service().getById(WAREHOUSE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在");
    }

    @Test
    void exportRestoresAuthenticationAndWritesTenantFilteredCsv() throws Exception {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingAuthentication = mock(Authentication.class);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(capturedAuthentication);
        SecurityContextHolder.setContext(requestContext);
        try {
            WarehousePageQuery query = new WarehousePageQuery();
            query.setKeyword(" W-001 ");
            query.setStatus(" active ");
            query.setDeptId(601L);
            query.setManagerUserId(701L);
            var export = service().exportWarehouses(query);

            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(warehouseMapper.selectList(any())).thenAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(capturedAuthentication);
                return List.of(warehouse(AUDIT.accountBookId()));
            });
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            export.writeTo(outputStream);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFFwarehouseCode,warehouseName,deptId,managerUserId,address,status,remark\r\n")
                    .contains("W-001,成品仓,601,701,苏州,ACTIVE,query test\r\n");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private WarehouseQueryService service() {
        return new WarehouseQueryService(warehouseMapper, auditMetadataFactory);
    }

    private WarehouseEntity warehouse(Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseCode("W-001");
        entity.setWarehouseName("成品仓");
        entity.setDeptId(601L);
        entity.setManagerUserId(701L);
        entity.setAddress("苏州");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("query test");
        return entity;
    }
}

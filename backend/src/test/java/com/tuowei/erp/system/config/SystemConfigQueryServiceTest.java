package com.tuowei.erp.system.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.config.service.SystemConfigQueryService;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SystemConfigQueryServiceTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SystemConfigEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), SystemConfigEntity.class.getName()
        );
        assistant.setCurrentNamespace(SystemConfigEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, SystemConfigEntity.class);
    }

    @Test
    void listNormalizesFiltersClampsPaginationAndKeepsGlobalSoftDeleteSemantics() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        Page<SystemConfigEntity> result = new Page<>(1, 200);
        result.setTotal(1L);
        result.setRecords(List.of(config(8001L, "ACTIVE", 0)));
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);

        SystemConfigPageQuery query = new SystemConfigPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  IMPORT ");
        query.setStatus(" active ");

        var response = service(mapper).list(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(8001L);
            assertThat(item.configCode()).isEqualTo("ERP_IMPORT_MAX_ROWS");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });

        ArgumentCaptor<Page<SystemConfigEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SystemConfigEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);

        LambdaQueryWrapper<SystemConfigEntity> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment().toLowerCase())
                .contains("deleted_flag", "config_code", "config_name", "status")
                .contains("order by config_code asc")
                .doesNotContain("company_id", "account_book_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(0, "%IMPORT%", "ACTIVE");
    }

    @Test
    void listNullQueryUsesDefaultPaginationAndOnlyFiltersUndeletedRows() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service(mapper).list(null);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(20L);
        ArgumentCaptor<Page<SystemConfigEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SystemConfigEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .doesNotContain("company_id", "account_book_id", "config_name", "status");
    }

    @Test
    void getByIdMapsGlobalConfigWithoutTenantFields() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectById(8001L)).thenReturn(config(8001L, "ACTIVE", 0));

        var response = service(mapper).getById(8001L);

        assertThat(response.id()).isEqualTo(8001L);
        assertThat(response.configCode()).isEqualTo("ERP_IMPORT_MAX_ROWS");
        assertThat(response.configName()).isEqualTo("导入最大行数");
        assertThat(response.configValue()).isEqualTo("5000");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.remark()).isEqualTo("global config");
    }

    @Test
    void getByIdRejectsMissingOrSoftDeletedConfig() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        SystemConfigQueryService service = service(mapper);

        when(mapper.selectById(8001L)).thenReturn(null);
        assertConfigMissing(() -> service.getById(8001L));

        when(mapper.selectById(8002L)).thenReturn(config(8002L, "DISABLED", 1));
        assertConfigMissing(() -> service.getById(8002L));

        SystemConfigEntity nullDeletedFlag = config(8003L, "ACTIVE", 0);
        nullDeletedFlag.setDeletedFlag(null);
        when(mapper.selectById(8003L)).thenReturn(nullDeletedFlag);
        assertConfigMissing(() -> service.getById(8003L));
    }

    private void assertConfigMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("系统参数不存在");
    }

    private SystemConfigQueryService service(SystemConfigMapper mapper) {
        return new SystemConfigQueryService(mapper);
    }

    private SystemConfigEntity config(Long id, String status, Integer deletedFlag) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(id);
        entity.setConfigCode("ERP_IMPORT_MAX_ROWS");
        entity.setConfigName("导入最大行数");
        entity.setConfigValue("5000");
        entity.setStatus(status);
        entity.setDeletedFlag(deletedFlag);
        entity.setRemark("global config");
        entity.setVersion(0);
        return entity;
    }
}

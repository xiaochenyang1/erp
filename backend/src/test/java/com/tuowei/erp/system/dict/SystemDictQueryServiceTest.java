package com.tuowei.erp.system.dict;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictItemEntity;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.service.SystemDictQueryService;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SystemDictQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1L, 101L, 202L, LocalDateTime.of(2026, 8, 21, 11, 0)
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(DictTypeEntity.class);
        initTableInfo(DictItemEntity.class);
    }

    @Test
    void listTypesNormalizesFiltersClampsPaginationAndKeepsGlobalTableUnscoped() {
        DictTypeMapper mapper = mock(DictTypeMapper.class);
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<DictTypeEntity> page = invocation.getArgument(0);
            page.setTotal(1L);
            page.setRecords(List.of(type("status", "状态", "ACTIVE")));
            return page;
        });
        DictTypePageQuery query = new DictTypePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setKeyword("  stat ");
        query.setStatus(" active ");

        var response = service(mapper, itemMapper, CacheService.NOOP).listTypes(query);

        assertThat(response.pageNo()).isEqualTo(1L);
        assertThat(response.pageSize()).isEqualTo(200L);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.dictType()).isEqualTo("status");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
        ArgumentCaptor<Page<DictTypeEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<DictTypeEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .contains("dict_type")
                .contains("dict_name")
                .contains("status")
                .doesNotContain("company_id")
                .doesNotContain("account_book_id");
    }

    @Test
    void requireEnabledItemRequiresActiveTypeAndItemAndNormalizesValues() {
        DictTypeMapper typeMapper = mock(DictTypeMapper.class);
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        when(typeMapper.selectOne(any())).thenReturn(type("product_type", "商品类型", "ACTIVE"));
        when(itemMapper.selectCount(any())).thenReturn(1L);

        String value = service(typeMapper, itemMapper, CacheService.NOOP)
                .requireEnabledItem(" product_type ", " STANDARD ", "商品类型不可用");

        assertThat(value).isEqualTo("STANDARD");
        ArgumentCaptor<LambdaQueryWrapper<DictItemEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(itemMapper).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .contains("status")
                .contains("dict_type")
                .contains("item_value");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("product_type", "STANDARD", "ACTIVE");
    }

    @Test
    void requireEnabledItemRejectsDisabledTypeAndMissingItemWithCallerMessage() {
        DictTypeMapper typeMapper = mock(DictTypeMapper.class);
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        when(typeMapper.selectOne(any())).thenReturn(type("status", "状态", "DISABLED"));
        assertThatThrownBy(() -> service(typeMapper, itemMapper, CacheService.NOOP)
                .requireEnabledItem("status", "ACTIVE", "状态不可用"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态不可用");

        when(typeMapper.selectOne(any())).thenReturn(type("status", "状态", "ACTIVE"));
        when(itemMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service(typeMapper, itemMapper, CacheService.NOOP)
                .requireEnabledItem("status", "ACTIVE", "状态不可用"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态不可用");
    }

    @Test
    void listItemsLoadsDeletedFilteredItemsFromGlobalCacheKey() {
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        RecordingCache cache = new RecordingCache();
        when(itemMapper.selectList(any())).thenReturn(List.of(item(11L, 1, "ACTIVE")));

        var response = service(mock(DictTypeMapper.class), itemMapper, cache)
                .listItems(" status ");

        assertThat(response).singleElement().satisfies(item -> assertThat(item.itemValue()).isEqualTo("ACTIVE"));
        assertThat(cache.key).isEqualTo("erp:global:dict:items:status");
        ArgumentCaptor<LambdaQueryWrapper<DictItemEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(itemMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .contains("dict_type")
                .contains("sort_no")
                .contains("id");
    }

    @Test
    void rejectsBlankDictionaryCode() {
        assertThatThrownBy(() -> service(mock(DictTypeMapper.class), mock(DictItemMapper.class), CacheService.NOOP)
                .listItems("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("字典编码不能为空");
    }

    private SystemDictQueryService service(DictTypeMapper typeMapper, DictItemMapper itemMapper, CacheService cache) {
        return new SystemDictQueryService(typeMapper, itemMapper, cache, new ObjectMapper());
    }

    private static DictTypeEntity type(String code, String name, String status) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setId(7L);
        entity.setDictType(code);
        entity.setDictName(name);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        return entity;
    }

    private static DictItemEntity item(Long id, int sortNo, String status) {
        DictItemEntity entity = new DictItemEntity();
        entity.setId(id);
        entity.setTypeId(7L);
        entity.setDictType("status");
        entity.setItemLabel("已启用");
        entity.setItemValue(status);
        entity.setSortNo(sortNo);
        entity.setStatus(status);
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

    private static final class RecordingCache implements CacheService {
        private String key;

        @Override
        public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
            this.key = key;
            return loader.get();
        }

        @Override
        public void evict(String key) {
        }

        @Override
        public void evictByPrefix(String keyPrefix) {
        }
    }
}

package com.tuowei.erp.system.dict;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class SystemDictServiceQueryDefaultsTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(DictTypeEntity.class);
    }

    @Test
    void listTypesTreatsNullQueryAsDefaultPagination() {
        DictTypeMapper typeMapper = mock(DictTypeMapper.class);
        when(typeMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<DictTypeEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(type()));
            return page;
        });
        SystemDictService service = new SystemDictService(
                typeMapper,
                mock(DictItemMapper.class),
                auditFactory(),
                new NoopCacheService(),
                new ObjectMapper()
        );

        PageResponse<DictTypeResponse> response = service.listTypes(null);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).extracting(DictTypeResponse::dictType).containsExactly("status");

        ArgumentCaptor<Page<DictTypeEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<DictTypeEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(typeMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase())
                .contains("deleted_flag")
                .contains("order by")
                .contains("dict_type");
    }

    private static AuditMetadataFactory auditFactory() {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(AUDIT);
        return factory;
    }

    private static DictTypeEntity type() {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setId(7L);
        entity.setDictType("status");
        entity.setDictName("状态");
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

    private static final class NoopCacheService implements CacheService {

        @Override
        public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
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

package com.tuowei.erp.system.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictItemEntity;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemDictServiceCacheTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-01-02T03:04:05")
    );

    @Test
    void listItemsUsesGlobalCacheKey() {
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        RecordingCacheService cacheService = new RecordingCacheService();
        when(itemMapper.selectList(any())).thenReturn(List.of(item(11L, "已启用", "ACTIVE")));
        SystemDictService service = service(itemMapper, cacheService);

        List<DictItemResponse> responses = service.listItems(" status ");

        assertThat(responses).extracting(DictItemResponse::itemValue).containsExactly("ACTIVE");
        assertThat(cacheService.loadedKey).isEqualTo("erp:global:dict:items:status");
        assertThat(cacheService.loadedValue).contains("\"itemValue\":\"ACTIVE\"");
    }

    @Test
    void listItemsUsesSameGlobalCacheKeyAcrossAccountBooks() {
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        RecordingCacheService firstCache = new RecordingCacheService();
        RecordingCacheService secondCache = new RecordingCacheService();
        when(itemMapper.selectList(any())).thenReturn(List.of(item(11L, "已启用", "ACTIVE")));

        service(itemMapper, firstCache, auditFactory(AUDIT)).listItems("status");
        service(itemMapper, secondCache, auditFactory(new AuditMetadata(
                9L,
                101L,
                303L,
                AUDIT.now()
        ))).listItems("status");

        assertThat(firstCache.loadedKey).isEqualTo("erp:global:dict:items:status");
        assertThat(secondCache.loadedKey).isEqualTo(firstCache.loadedKey);
    }

    @Test
    void listItemsRestoresItemsFromCacheWithoutQueryingMapper() {
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        RecordingCacheService cacheService = new RecordingCacheService();
        cacheService.cachedValue = """
                [{"id":11,"typeId":7,"dictType":"status","itemLabel":"已启用","itemValue":"ACTIVE","sortNo":1,"status":"ACTIVE","remark":"cached"}]
                """;
        SystemDictService service = service(itemMapper, cacheService);

        List<DictItemResponse> responses = service.listItems("status");

        assertThat(responses).extracting(DictItemResponse::remark).containsExactly("cached");
        verify(itemMapper, never()).selectList(anyDictItemWrapper());
    }

    @Test
    void createItemEvictsDictItemsCacheForDictType() {
        DictTypeMapper typeMapper = mock(DictTypeMapper.class);
        DictItemMapper itemMapper = mock(DictItemMapper.class);
        RecordingCacheService cacheService = new RecordingCacheService();
        when(typeMapper.selectOne(any())).thenReturn(type("status"));
        SystemDictService service = new SystemDictService(typeMapper, itemMapper, auditFactory(), cacheService, new ObjectMapper());

        service.createItem(new DictItemCreateRequest(" status ", "已启用", "ACTIVE", 1, null));

        assertThat(cacheService.evictedKey).isEqualTo("erp:global:dict:items:status");
    }

    private static SystemDictService service(DictItemMapper itemMapper, CacheService cacheService) {
        return service(itemMapper, cacheService, auditFactory(AUDIT));
    }

    private static SystemDictService service(DictItemMapper itemMapper, CacheService cacheService, AuditMetadataFactory auditFactory) {
        return new SystemDictService(mock(DictTypeMapper.class), itemMapper, auditFactory, cacheService, new ObjectMapper());
    }

    private static AuditMetadataFactory auditFactory() {
        return auditFactory(AUDIT);
    }

    private static AuditMetadataFactory auditFactory(AuditMetadata audit) {
        AuditMetadataFactory factory = mock(AuditMetadataFactory.class);
        when(factory.current()).thenReturn(audit);
        return factory;
    }

    private static DictTypeEntity type(String dictType) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.setId(7L);
        entity.setDictType(dictType);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static DictItemEntity item(Long id, String label, String value) {
        DictItemEntity entity = new DictItemEntity();
        entity.setId(id);
        entity.setTypeId(7L);
        entity.setDictType("status");
        entity.setItemLabel(label);
        entity.setItemValue(value);
        entity.setSortNo(1);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<DictItemEntity> anyDictItemWrapper() {
        return any(LambdaQueryWrapper.class);
    }

    private static final class RecordingCacheService implements CacheService {

        private String cachedValue;
        private String loadedKey;
        private String loadedValue;
        private String evictedKey;

        @Override
        public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
            loadedKey = key;
            if (cachedValue != null) {
                return cachedValue;
            }
            loadedValue = loader.get();
            return loadedValue;
        }

        @Override
        public void evict(String key) {
            evictedKey = key;
        }

        @Override
        public void evictByPrefix(String keyPrefix) {
        }
    }
}

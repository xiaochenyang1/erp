package com.tuowei.erp.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheKeyBuilder;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictItemEntity;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Read-side dictionary queries, enabled-item guards and cache-backed mappings. */
@Service
public class SystemDictQueryService {

    private static final Duration DICT_ITEMS_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<DictItemResponse>> DICT_ITEM_RESPONSE_LIST_TYPE = new TypeReference<>() {
    };

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public SystemDictQueryService(
            DictTypeMapper dictTypeMapper,
            DictItemMapper dictItemMapper,
            CacheService cacheService,
            ObjectMapper objectMapper
    ) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<DictTypeResponse> listTypes(DictTypePageQuery query) {
        DictTypePageQuery safeQuery = safeQuery(query);
        Page<DictTypeEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<DictTypeEntity> result = dictTypeMapper.selectPage(page, buildTypeQuery(safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toTypeResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public DictTypeResponse getTypeById(Long id) {
        return toTypeResponse(requireType(id));
    }

    @Transactional(readOnly = true)
    public List<DictItemResponse> listItems(String dictType) {
        String normalizedDictType = normalizeRequired(dictType);
        String cached = cacheService.getOrLoad(
                dictItemsCacheKey(normalizedDictType),
                DICT_ITEMS_CACHE_TTL,
                () -> serializeDictItems(loadItems(normalizedDictType))
        );
        return deserializeDictItems(cached);
    }

    @Transactional(readOnly = true)
    public String requireEnabledItem(String dictType, String itemValue, String message) {
        String normalizedDictType = normalizeRequired(dictType);
        String normalizedItemValue = normalizeRequired(itemValue);
        DictTypeEntity type = requireTypeByCode(normalizedDictType);
        if (!"ACTIVE".equals(type.getStatus())) {
            throw new IllegalArgumentException(message);
        }
        Long count = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItemEntity>()
                .eq(DictItemEntity::getDeletedFlag, 0)
                .eq(DictItemEntity::getStatus, "ACTIVE")
                .eq(DictItemEntity::getDictType, normalizedDictType)
                .eq(DictItemEntity::getItemValue, normalizedItemValue));
        if (count == null || count < 1) {
            throw new IllegalArgumentException(message);
        }
        return normalizedItemValue;
    }

    DictTypeEntity requireType(Long id) {
        DictTypeEntity entity = dictTypeMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("字典类型不存在");
        }
        return entity;
    }

    DictTypeEntity requireTypeByCode(String dictType) {
        DictTypeEntity entity = dictTypeMapper.selectOne(new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getDeletedFlag, 0)
                .eq(DictTypeEntity::getDictType, dictType));
        if (entity == null) {
            throw new IllegalArgumentException("字典类型不存在");
        }
        return entity;
    }

    DictItemEntity requireItem(Long id) {
        DictItemEntity entity = dictItemMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("字典项不存在");
        }
        return entity;
    }

    DictTypeResponse toTypeResponse(DictTypeEntity entity) {
        return new DictTypeResponse(
                entity.getId(),
                entity.getDictType(),
                entity.getDictName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    DictItemResponse toItemResponse(DictItemEntity entity) {
        return new DictItemResponse(
                entity.getId(),
                entity.getTypeId(),
                entity.getDictType(),
                entity.getItemLabel(),
                entity.getItemValue(),
                entity.getSortNo(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    void evictDictItemsCache(String dictType) {
        cacheService.evict(dictItemsCacheKey(dictType));
    }

    private List<DictItemResponse> loadItems(String dictType) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItemEntity>()
                        .eq(DictItemEntity::getDeletedFlag, 0)
                        .eq(DictItemEntity::getDictType, dictType)
                        .orderByAsc(DictItemEntity::getSortNo)
                        .orderByAsc(DictItemEntity::getId))
                .stream()
                .map(this::toItemResponse)
                .toList();
    }

    private LambdaQueryWrapper<DictTypeEntity> buildTypeQuery(DictTypePageQuery query) {
        LambdaQueryWrapper<DictTypeEntity> wrapper = new LambdaQueryWrapper<DictTypeEntity>()
                .eq(DictTypeEntity::getDeletedFlag, 0);
        String keyword = normalizeNullable(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(it -> it.like(DictTypeEntity::getDictType, keyword)
                    .or()
                    .like(DictTypeEntity::getDictName, keyword));
        }
        String status = normalizeNullable(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(DictTypeEntity::getStatus, status.toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByAsc(DictTypeEntity::getDictType);
    }

    private DictTypePageQuery safeQuery(DictTypePageQuery query) {
        return query == null ? new DictTypePageQuery() : query;
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("字典编码不能为空");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }

    private String dictItemsCacheKey(String dictType) {
        return CacheKeyBuilder.global("dict", "items", dictType);
    }

    private String serializeDictItems(List<DictItemResponse> responses) {
        try {
            return objectMapper.writeValueAsString(responses);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("字典项缓存序列化失败", ex);
        }
    }

    private List<DictItemResponse> deserializeDictItems(String cached) {
        try {
            return objectMapper.readValue(cached, DICT_ITEM_RESPONSE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("字典项缓存反序列化失败", ex);
        }
    }
}

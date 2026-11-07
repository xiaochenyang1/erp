package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Read-side global system configuration queries. */
@Service
public class SystemConfigQueryService {

    private final SystemConfigMapper systemConfigMapper;

    public SystemConfigQueryService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemConfigResponse> list(SystemConfigPageQuery query) {
        SystemConfigPageQuery safeQuery = query == null ? new SystemConfigPageQuery() : query;
        Page<SystemConfigEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<SystemConfigEntity> result = systemConfigMapper.selectPage(page, buildListQuery(
                normalizeNullableText(safeQuery.getKeyword()), normalizeStatus(safeQuery.getStatus())
        ));
        return new PageResponse<>(
                result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SystemConfigResponse getById(Long id) {
        return toResponse(requireConfig(id));
    }

    SystemConfigEntity requireConfig(Long id) {
        SystemConfigEntity entity = systemConfigMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("系统参数不存在");
        }
        return entity;
    }

    SystemConfigResponse toResponse(SystemConfigEntity entity) {
        return new SystemConfigResponse(
                entity.getId(), entity.getConfigCode(), entity.getConfigName(), entity.getConfigValue(),
                entity.getStatus(), entity.getRemark()
        );
    }

    private LambdaQueryWrapper<SystemConfigEntity> buildListQuery(String keyword, String status) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SystemConfigEntity::getConfigCode, keyword)
                    .or().like(SystemConfigEntity::getConfigName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SystemConfigEntity::getStatus, status);
        }
        return wrapper.orderByAsc(SystemConfigEntity::getConfigCode);
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }
}

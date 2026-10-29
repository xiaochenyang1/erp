package com.tuowei.erp.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.web.RoleMenuAssignmentResponse;
import com.tuowei.erp.system.role.web.RolePageQuery;
import com.tuowei.erp.system.role.web.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-side role queries, tenant guards and menu-binding cleanup. */
@Service
public class RoleQueryService {

    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public RoleQueryService(
            RoleMapper roleMapper,
            MenuMapper menuMapper,
            RoleMenuMapper roleMenuMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> list(RolePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        RolePageQuery safeQuery = query == null ? new RolePageQuery() : query;
        Page<RoleEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        Page<RoleEntity> result = roleMapper.selectPage(page, buildListQuery(
                audit.companyId(),
                audit.accountBookId(),
                normalizeNullableText(safeQuery.getKeyword()),
                normalizeStatus(safeQuery.getStatus())
        ));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return toResponse(requireRole(id));
    }

    @Transactional(readOnly = true)
    public RoleMenuAssignmentResponse getAssignedMenus(Long roleId) {
        requireRole(roleId);
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .eq(RoleMenuEntity::getRoleId, roleId)
                        .orderByAsc(RoleMenuEntity::getId))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .toList();
        return new RoleMenuAssignmentResponse(roleId, retainActiveMenuIds(menuIds));
    }

    RoleEntity requireRole(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("角色不存在");
        }
        return entity;
    }

    RoleResponse toResponse(RoleEntity entity) {
        return new RoleResponse(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    List<Long> retainActiveMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        List<Long> active = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            if (menuId == null) {
                continue;
            }
            MenuEntity menuEntity = menuMapper.selectById(menuId);
            if (menuEntity != null
                    && menuEntity.getDeletedFlag() != null
                    && menuEntity.getDeletedFlag() == 0) {
                active.add(menuId);
            }
        }
        return active;
    }

    private LambdaQueryWrapper<RoleEntity> buildListQuery(
            Long companyId,
            Long accountBookId,
            String keyword,
            String status
    ) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCompanyId, companyId)
                .eq(RoleEntity::getAccountBookId, accountBookId)
                .eq(RoleEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(RoleEntity::getRoleCode, keyword)
                    .or()
                    .like(RoleEntity::getRoleName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RoleEntity::getStatus, status);
        }
        return wrapper.orderByAsc(RoleEntity::getRoleCode);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

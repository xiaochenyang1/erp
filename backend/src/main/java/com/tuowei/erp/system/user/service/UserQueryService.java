package com.tuowei.erp.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserRoleAssignmentResponse;
import com.tuowei.erp.system.user.web.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/** Read-side user queries and tenant-scoped entity lookup. */
@Service
public class UserQueryService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public UserQueryService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(UserPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserPageQuery safeQuery = query == null ? new UserPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<UserEntity> page = new Page<>(pageNo, pageSize);
        Page<UserEntity> result = userMapper.selectPage(page, buildListQuery(
                audit.companyId(),
                audit.accountBookId(),
                keyword,
                status,
                safeQuery.getDeptId(),
                safeQuery.getPostId()
        ));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(requireUser(id));
    }

    @Transactional(readOnly = true)
    public UserRoleAssignmentResponse getAssignedRoles(Long userId) {
        requireUser(userId);
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId)
                        .orderByAsc(UserRoleEntity::getId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        return new UserRoleAssignmentResponse(userId, roleIds);
    }

    UserEntity requireUser(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserEntity entity = userMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("用户不存在");
        }
        return entity;
    }

    UserResponse toResponse(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getEmployeeNo(),
                entity.getRealName(),
                entity.getEmail(),
                entity.getMobile(),
                entity.getAvatar(),
                entity.getDeptId(),
                entity.getPostId(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

    private LambdaQueryWrapper<UserEntity> buildListQuery(
            Long companyId,
            Long accountBookId,
            String keyword,
            String status,
            Long deptId,
            Long postId
    ) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCompanyId, companyId)
                .eq(UserEntity::getAccountBookId, accountBookId)
                .eq(UserEntity::getDeletedFlag, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(UserEntity::getUsername, keyword)
                    .or()
                    .like(UserEntity::getRealName, keyword)
                    .or()
                    .like(UserEntity::getMobile, keyword)
                    .or()
                    .like(UserEntity::getEmployeeNo, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(UserEntity::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(UserEntity::getDeptId, deptId);
        }
        if (postId != null) {
            wrapper.eq(UserEntity::getPostId, postId);
        }

        return wrapper.orderByAsc(UserEntity::getUsername);
    }
}

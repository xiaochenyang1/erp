package com.tuowei.erp.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.common.validation.PasswordPolicy;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.post.mapper.PostMapper;
import com.tuowei.erp.system.post.model.PostEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserRoleAssignmentResponse;
import com.tuowei.erp.system.user.web.UserResponse;
import com.tuowei.erp.system.user.web.UserUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Write-side user lifecycle, role assignment and credential commands. */
@Service
public class UserCommandService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DeptMapper deptMapper;
    private final PostMapper postMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditMetadataFactory auditMetadataFactory;
    private final RefreshTokenService refreshTokenService;
    private final SecurityPrincipalCache principalCache;
    private final ScopedUserResolver scopedUserResolver;
    private final UserPermissionService userPermissionService;
    private final UserQueryService userQueryService;

    public UserCommandService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            DeptMapper deptMapper,
            PostMapper postMapper,
            PasswordEncoder passwordEncoder,
            AuditMetadataFactory auditMetadataFactory,
            RefreshTokenService refreshTokenService,
            SecurityPrincipalCache principalCache,
            ScopedUserResolver scopedUserResolver,
            UserPermissionService userPermissionService,
            UserQueryService userQueryService
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditMetadataFactory = auditMetadataFactory;
        this.refreshTokenService = refreshTokenService;
        this.principalCache = principalCache;
        this.scopedUserResolver = scopedUserResolver;
        this.userPermissionService = userPermissionService;
        this.userQueryService = userQueryService;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        PasswordPolicy.assertValid(request.password(), "password");
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        DeptEntity deptEntity = requireDept(request.deptId());
        PostEntity postEntity = requirePost(request.postId());
        validatePostBelongsToDept(request.deptId(), postEntity);
        String mobile = normalizeNullableText(request.mobile());
        String employeeNo = normalizeNullableText(request.employeeNo());
        String email = normalizeNullableText(request.email());
        String avatar = normalizeNullableText(request.avatar());
        validateUsernameUnique(request.username(), null);
        validateMobileUnique(mobile, null);
        validateEmployeeNoUnique(employeeNo, null);

        UserEntity entity = new UserEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setUsername(request.username());
        entity.setPassword(passwordEncoder.encode(request.password()));
        entity.setEmployeeNo(employeeNo);
        entity.setRealName(request.realName());
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setAvatar(avatar);
        entity.setDeptId(deptEntity == null ? null : deptEntity.getId());
        entity.setPostId(postEntity == null ? null : postEntity.getId());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        userMapper.insert(entity);
        scopedUserResolver.evictAll();
        return userQueryService.toResponse(entity);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        UserEntity entity = userQueryService.requireUser(id);
        AuditMetadata audit = auditMetadataFactory.current();
        DeptEntity deptEntity = requireDept(request.deptId());
        PostEntity postEntity = requirePost(request.postId());
        validatePostBelongsToDept(request.deptId(), postEntity);
        String mobile = normalizeNullableText(request.mobile());
        String employeeNo = normalizeNullableText(request.employeeNo());
        String email = normalizeNullableText(request.email());
        String avatar = normalizeNullableText(request.avatar());
        validateMobileUnique(mobile, id);
        validateEmployeeNoUnique(employeeNo, id);
        entity.setEmployeeNo(employeeNo);
        entity.setRealName(request.realName());
        entity.setEmail(email);
        entity.setMobile(mobile);
        entity.setAvatar(avatar);
        entity.setDeptId(deptEntity == null ? null : deptEntity.getId());
        entity.setPostId(postEntity == null ? null : postEntity.getId());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(userMapper.updateById(entity), "用户已被其他操作修改，请刷新后重试");
        principalCache.evictUser(id);
        scopedUserResolver.evictAll();
        return userQueryService.toResponse(entity);
    }

    @Transactional
    public UserResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public UserResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    @Transactional
    public UserRoleAssignmentResponse assignRoles(Long userId, UserRoleAssignRequest request) {
        userQueryService.requireUser(userId);
        AuditMetadata audit = auditMetadataFactory.current();
        List<Long> roleIds = normalizeRoleIds(request.roleIds());

        for (Long roleId : roleIds) {
            RoleEntity roleEntity = roleMapper.selectById(roleId);
            if (roleEntity == null
                    || roleEntity.getDeletedFlag() == null
                    || roleEntity.getDeletedFlag() != 0
                    || !audit.companyId().equals(roleEntity.getCompanyId())
                    || !audit.accountBookId().equals(roleEntity.getAccountBookId())) {
                throw new IllegalArgumentException("角色不存在");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId));

        LocalDateTime now = audit.now();
        for (Long roleId : roleIds) {
            UserRoleEntity userRoleEntity = new UserRoleEntity();
            userRoleEntity.setUserId(userId);
            userRoleEntity.setRoleId(roleId);
            userRoleEntity.setCreatedBy(audit.userId());
            userRoleEntity.setCreatedTime(now);
            userRoleMapper.insert(userRoleEntity);
        }

        principalCache.evictUser(userId);
        userPermissionService.evictUserPermissions(userId, audit.companyId(), audit.accountBookId());
        return new UserRoleAssignmentResponse(userId, roleIds);
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        PasswordPolicy.assertValid(request.newPassword(), "newPassword");
        UserEntity entity = userQueryService.requireUser(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setPassword(passwordEncoder.encode(request.newPassword()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(userMapper.updateById(entity), "用户已被其他操作修改，请刷新后重试");
        refreshTokenService.revokeAllForUser(id);
        principalCache.evictUser(id);
        return userQueryService.toResponse(entity);
    }

    private UserResponse updateStatus(Long id, String status) {
        UserEntity entity = userQueryService.requireUser(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(userMapper.updateById(entity), "用户已被其他操作修改，请刷新后重试");
        principalCache.evictUser(id);
        scopedUserResolver.evictAll();
        return userQueryService.toResponse(entity);
    }

    private List<Long> normalizeRoleIds(List<Long> roleIds) {
        return new ArrayList<>(new LinkedHashSet<>(roleIds));
    }

    private DeptEntity requireDept(Long deptId) {
        if (deptId == null) {
            return null;
        }

        DeptEntity entity = deptMapper.selectById(deptId);
        AuditMetadata audit = auditMetadataFactory.current();
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("部门不存在");
        }
        return entity;
    }

    private PostEntity requirePost(Long postId) {
        if (postId == null) {
            return null;
        }

        PostEntity entity = postMapper.selectById(postId);
        AuditMetadata audit = auditMetadataFactory.current();
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("岗位不存在");
        }
        return entity;
    }

    private void validatePostBelongsToDept(Long deptId, PostEntity postEntity) {
        if (deptId == null || postEntity == null) {
            return;
        }

        if (!deptId.equals(postEntity.getDeptId())) {
            throw new IllegalArgumentException("岗位不属于当前部门");
        }
    }

    private void validateUsernameUnique(String username, Long excludeId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeletedFlag, 0)
                .eq(UserEntity::getUsername, username)
                .ne(excludeId != null, UserEntity::getId, excludeId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private void validateMobileUnique(String mobile, Long excludeId) {
        if (!StringUtils.hasText(mobile)) {
            return;
        }

        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeletedFlag, 0)
                .eq(UserEntity::getMobile, mobile)
                .ne(excludeId != null, UserEntity::getId, excludeId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("手机号已存在");
        }
    }

    private void validateEmployeeNoUnique(String employeeNo, Long excludeId) {
        if (!StringUtils.hasText(employeeNo)) {
            return;
        }

        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeletedFlag, 0)
                .eq(UserEntity::getEmployeeNo, employeeNo)
                .ne(excludeId != null, UserEntity::getId, excludeId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("员工编号已存在");
        }
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

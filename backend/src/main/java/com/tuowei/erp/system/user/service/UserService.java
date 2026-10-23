package com.tuowei.erp.system.user.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.user.web.ResetPasswordRequest;
import com.tuowei.erp.system.user.web.UserCreateRequest;
import com.tuowei.erp.system.user.web.UserPageQuery;
import com.tuowei.erp.system.user.web.UserRoleAssignRequest;
import com.tuowei.erp.system.user.web.UserRoleAssignmentResponse;
import com.tuowei.erp.system.user.web.UserResponse;
import com.tuowei.erp.system.user.web.UserUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for user queries and commands. */
@Service
public class UserService {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    public UserService(UserQueryService userQueryService, UserCommandService userCommandService) {
        this.userQueryService = userQueryService;
        this.userCommandService = userCommandService;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        return userCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(UserPageQuery query) {
        return userQueryService.list(query == null ? new UserPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userQueryService.getById(id);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        return userCommandService.update(id, request);
    }

    @Transactional
    public UserResponse enable(Long id) {
        return userCommandService.enable(id);
    }

    @Transactional
    public UserResponse disable(Long id) {
        return userCommandService.disable(id);
    }

    @Transactional
    public UserRoleAssignmentResponse assignRoles(Long userId, UserRoleAssignRequest request) {
        return userCommandService.assignRoles(userId, request);
    }

    @Transactional(readOnly = true)
    public UserRoleAssignmentResponse getAssignedRoles(Long userId) {
        return userQueryService.getAssignedRoles(userId);
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        return userCommandService.resetPassword(id, request);
    }
}

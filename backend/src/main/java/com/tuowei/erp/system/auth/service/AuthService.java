package com.tuowei.erp.system.auth.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.validation.PasswordPolicy;
import com.tuowei.erp.system.auth.web.ChangePasswordRequest;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LoginUserDataScopeResponse;
import com.tuowei.erp.system.auth.web.LoginUserResponse;
import com.tuowei.erp.system.auth.web.LogoutRequest;
import com.tuowei.erp.system.auth.web.RefreshTokenRequest;
import com.tuowei.erp.system.auth.web.UpdateProfileRequest;
import com.tuowei.erp.system.auth.web.UserInfoResponse;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("zh-CN", "en-US");
    private static final Set<String> SUPPORTED_TIME_ZONES = Set.of(
            "Asia/Shanghai", "UTC", "America/New_York", "Europe/London"
    );

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final SystemLogService systemLogService;
    private final Object loginRateLimiter;
    private final CurrentUserContext currentUserContext;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ClientIpResolver clientIpResolver;
    private final SecurityPrincipalCache principalCache;
    private final MenuService menuService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            SystemLogService systemLogService,
            @Autowired(required = false) LoginRateLimiter redisRateLimiter,
            @Autowired(required = false) NoOpLoginRateLimiter noOpRateLimiter,
            CurrentUserContext currentUserContext,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            Clock clock,
            ClientIpResolver clientIpResolver,
            SecurityPrincipalCache principalCache,
            MenuService menuService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.systemLogService = systemLogService;
        this.loginRateLimiter = redisRateLimiter != null ? redisRateLimiter : noOpRateLimiter;
        this.currentUserContext = currentUserContext;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
        this.principalCache = principalCache;
        this.menuService = menuService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        if (loginRateLimiter instanceof LoginRateLimiter limiter) {
            limiter.assertAllowed(request.username(), clientIp);
        } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
            limiter.assertAllowed(request.username(), clientIp);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
            );
        } catch (AuthenticationException ex) {
            log.warn("Login failed username={} ip={} reason={}", request.username(), clientIp, ex.getClass().getSimpleName());
            systemLogService.recordLoginFailure(request.username(), "用户名或密码错误", httpRequest);

            if (loginRateLimiter instanceof LoginRateLimiter limiter) {
                limiter.recordFailure(request.username(), clientIp);
            } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
                limiter.recordFailure(request.username(), clientIp);
            }
            throw ex;
        }
        ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
        systemLogService.recordLoginSuccess(principal.userId(), principal.username(), "登录成功", httpRequest);

        if (loginRateLimiter instanceof LoginRateLimiter limiter) {
            limiter.recordSuccess(principal.username(), clientIp);
        } else if (loginRateLimiter instanceof NoOpLoginRateLimiter limiter) {
            limiter.recordSuccess(principal.username(), clientIp);
        }

        String accessToken = jwtTokenService.createAccessToken(principal);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(principal, httpRequest);
        LoginResponse response = buildLoginResponse(
                principal,
                accessToken,
                refreshToken.token(),
                jwtTokenService.accessTokenTtlSeconds(),
                refreshToken.expiresIn()
        );
        log.info("Login succeeded userId={} username={} ip={}", principal.userId(), principal.username(), clientIp);
        return response;
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.TokenRefreshResult result = refreshTokenService.rotate(request.refreshToken());
        return buildLoginResponse(
                result.principal(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.refreshExpiresIn()
        );
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        PasswordPolicy.assertValid(request.newPassword(), "newPassword");
        ErpPrincipal principal = currentUserContext.requirePrincipal();
        UserEntity user = userMapper.selectById(principal.userId());
        if (user == null
                || user.getDeletedFlag() == null
                || user.getDeletedFlag() != 0
                || !"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("用户不存在或已停用");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BadCredentialsException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedBy(principal.userId());
        user.setUpdatedTime(LocalDateTime.now(clock));
        OptimisticLockGuard.requireUpdated(userMapper.updateById(user), "用户已被其他操作修改，请刷新后重试");
        refreshTokenService.revokeAllForUser(principal.userId());
        principalCache.evictUser(principal.userId());
    }

    @Transactional
    public UserInfoResponse updateProfile(UpdateProfileRequest request) {
        ErpPrincipal principal = currentUserContext.requirePrincipal();
        UserEntity user = requireActiveUser(principal.userId());
        String mobile = normalizeNullableText(request.mobile());
        String email = normalizeNullableText(request.email());
        String avatar = normalizeNullableText(request.avatar());
        String locale = normalizePreference(request.locale(), SUPPORTED_LOCALES, "locale");
        String timeZone = normalizePreference(request.timeZone(), SUPPORTED_TIME_ZONES, "timeZone");
        String realName = request.realName() == null ? "" : request.realName().trim();
        if (!StringUtils.hasText(realName)) {
            throw new IllegalArgumentException("realName不能为空");
        }
        validateMobileUnique(mobile, principal.userId());

        user.setRealName(realName);
        user.setEmail(email);
        user.setMobile(mobile);
        user.setAvatar(avatar);
        if (locale != null) {
            user.setLocale(locale);
        }
        if (timeZone != null) {
            user.setTimeZone(timeZone);
        }
        user.setUpdatedBy(principal.userId());
        user.setUpdatedTime(LocalDateTime.now(clock));
        OptimisticLockGuard.requireUpdated(userMapper.updateById(user), "用户已被其他操作修改，请刷新后重试");
        principalCache.evictUser(principal.userId());
        return toUserInfoResponse(user, principal);
    }

    public UserInfoResponse getUserInfo() {
        ErpPrincipal principal = currentUserContext.requirePrincipal();
        UserEntity user = requireActiveUser(principal.userId());
        return toUserInfoResponse(user, principal);
    }

    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null
                || user.getDeletedFlag() == null
                || user.getDeletedFlag() != 0
                || !"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("用户不存在或已停用");
        }
        return user;
    }

    private UserInfoResponse toUserInfoResponse(UserEntity user, ErpPrincipal principal) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, principal.userId())
        );

        List<String> roleNames = userRoles.stream()
                .map(UserRoleEntity::getRoleId)
                .map(roleMapper::selectById)
                .filter(role -> role != null && "ACTIVE".equals(role.getStatus()))
                .map(RoleEntity::getRoleName)
                .sorted()
                .toList();

        List<String> permissions = principal.permissions().stream().sorted().toList();

        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getMobile(),
                user.getAvatar(),
                user.getLocale(),
                user.getTimeZone(),
                roleNames,
                permissions
        );
    }

    private String normalizePreference(String value, Set<String> supported, String field) {
        String normalized = normalizeNullableText(value);
        if (normalized != null && !supported.contains(normalized)) {
            throw new IllegalArgumentException(field + "不支持");
        }
        return normalized;
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

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public List<MenuResponse> getRuntimeMenuTree() {
        return menuService.runtimeTreeForCurrentUser();
    }

    private LoginResponse buildLoginResponse(
            ErpPrincipal principal,
            String accessToken,
            String refreshToken,
            long accessExpiresIn,
            long refreshExpiresIn
    ) {
        List<String> permissions = principal.permissions().stream().sorted().toList();
        LoginUserDataScopeResponse dataScope = new LoginUserDataScopeResponse(
                principal.dataScopeSnapshot().hasAllScope(),
                principal.dataScopeSnapshot().deptScoped(),
                principal.dataScopeSnapshot().postScoped(),
                principal.dataScopeSnapshot().selfScoped(),
                principal.dataScopeSnapshot().warehouseIds().stream().sorted().toList()
        );
        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessExpiresIn,
                refreshExpiresIn,
                new LoginUserResponse(principal.userId(), principal.username(), principal.realName(), dataScope),
                permissions
        );
    }

}

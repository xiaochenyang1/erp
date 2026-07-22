package com.tuowei.erp.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.service.LoginRateLimiter;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.auth.web.UpdateProfileRequest;
import com.tuowei.erp.system.auth.web.UserInfoResponse;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUpdateProfileTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SystemLogService systemLogService;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private SecurityPrincipalCache principalCache;
    @Mock private MenuService menuService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                systemLogService,
                loginRateLimiter,
                null,
                currentUserContext,
                userMapper,
                userRoleMapper,
                roleMapper,
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-07-17T02:00:00Z"), ZoneOffset.UTC),
                clientIpResolver,
                principalCache,
                menuService
        );
    }

    @Test
    void updateProfilePersistsSelfEditableFields() {
        ErpPrincipal principal = principal(4001L);
        when(currentUserContext.requirePrincipal()).thenReturn(principal);

        UserEntity user = activeUser(4001L, "admin", "管理员", null, null, null);
        when(userMapper.selectById(4001L)).thenReturn(user);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(roleLink(4001L, 3001L)));

        RoleEntity role = new RoleEntity();
        role.setId(3001L);
        role.setStatus("ACTIVE");
        role.setRoleName("超级管理员");
        when(roleMapper.selectById(3001L)).thenReturn(role);

        UserInfoResponse response = authService.updateProfile(new UpdateProfileRequest(
                "本地管理员",
                "admin@example.com",
                "13900001111",
                "https://cdn.example.com/admin.png",
                "en-US",
                "UTC"
        ));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getRealName()).isEqualTo("本地管理员");
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getMobile()).isEqualTo("13900001111");
        assertThat(saved.getAvatar()).isEqualTo("https://cdn.example.com/admin.png");
        assertThat(saved.getLocale()).isEqualTo("en-US");
        assertThat(saved.getTimeZone()).isEqualTo("UTC");

        assertThat(response.realName()).isEqualTo("本地管理员");
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.mobile()).isEqualTo("13900001111");
        assertThat(response.avatar()).isEqualTo("https://cdn.example.com/admin.png");
        assertThat(response.locale()).isEqualTo("en-US");
        assertThat(response.timeZone()).isEqualTo("UTC");
        assertThat(response.roles()).containsExactly("超级管理员");
        verify(principalCache).evictUser(4001L);
    }

    @Test
    void updateProfileRejectsDuplicateMobile() {
        ErpPrincipal principal = principal(4001L);
        when(currentUserContext.requirePrincipal()).thenReturn(principal);
        when(userMapper.selectById(4001L)).thenReturn(activeUser(4001L, "admin", "管理员", null, null, null));
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authService.updateProfile(new UpdateProfileRequest(
                "管理员",
                "admin@example.com",
                "13900001111",
                null,
                null,
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号已存在");
    }

    private static ErpPrincipal principal(Long userId) {
        return new ErpPrincipal(
                userId,
                1L,
                1L,
                "admin",
                "管理员",
                "encoded",
                Set.of("report:view")
        );
    }

    private static UserEntity activeUser(
            Long id,
            String username,
            String realName,
            String email,
            String mobile,
            String avatar
    ) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(realName);
        user.setEmail(email);
        user.setMobile(mobile);
        user.setAvatar(avatar);
        user.setStatus("ACTIVE");
        user.setDeletedFlag(0);
        user.setVersion(0);
        return user;
    }

    private static UserRoleEntity roleLink(Long userId, Long roleId) {
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(userId);
        link.setRoleId(roleId);
        return link;
    }
}

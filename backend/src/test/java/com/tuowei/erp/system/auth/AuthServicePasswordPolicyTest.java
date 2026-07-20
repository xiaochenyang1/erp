package com.tuowei.erp.system.auth;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.service.LoginRateLimiter;
import com.tuowei.erp.system.auth.service.RefreshTokenService;
import com.tuowei.erp.system.auth.web.ChangePasswordRequest;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServicePasswordPolicyTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private SecurityPrincipalCache principalCache;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                systemLogService,
                loginRateLimiter,
                null,  // noOpRateLimiter
                currentUserContext,
                userMapper,
                mock(com.tuowei.erp.system.user.mapper.UserRoleMapper.class),
                mock(com.tuowei.erp.system.role.mapper.RoleMapper.class),
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneOffset.UTC),
                clientIpResolver,
                principalCache,
                mock(MenuService.class)
        );
    }

    @Test
    void changePasswordRejectsWeakNewPasswordBeforeLoadingUser() {
        ErpPrincipal principal = new ErpPrincipal(
                9001L,
                1L,
                1L,
                "alice",
                "Alice",
                "encoded-old-password",
                Set.of("system:profile:change-password")
        );
        UserEntity user = new UserEntity();
        user.setId(principal.userId());
        user.setPassword("encoded-old-password");
        user.setStatus("ACTIVE");
        user.setDeletedFlag(0);
        when(currentUserContext.requirePrincipal()).thenReturn(principal);
        when(userMapper.selectById(principal.userId())).thenReturn(user);
        when(passwordEncoder.matches("OldPassword123", "encoded-old-password")).thenReturn(true);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("OldPassword123", "weak")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度必须在12到72位之间");

        verifyNoInteractions(currentUserContext, userMapper, passwordEncoder, refreshTokenService, principalCache);
    }
}

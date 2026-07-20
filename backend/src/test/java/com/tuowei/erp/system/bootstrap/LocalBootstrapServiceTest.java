package com.tuowei.erp.system.bootstrap;

import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalBootstrapServiceTest {

    @Mock
    private Environment environment;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SystemConfigMapper systemConfigMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private LocalBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new LocalBootstrapService(
                environment,
                userMapper,
                systemConfigMapper,
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void initializesLocalAdminPasswordWhenBootstrapMarkerIsOpen() {
        SystemConfigEntity marker = bootstrapMarker("false");
        UserEntity admin = activeAdmin();
        when(systemConfigMapper.selectOne(any())).thenReturn(marker);
        when(environment.getProperty("erp.bootstrap.local-admin-password", "LocalAdmin123"))
                .thenReturn("LocalAdmin123");
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.encode("LocalAdmin123")).thenReturn("encoded-local-password");
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);
        when(systemConfigMapper.updateById(any(SystemConfigEntity.class))).thenReturn(1);

        service.run(null);

        ArgumentCaptor<UserEntity> adminCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById(adminCaptor.capture());
        assertThat(adminCaptor.getValue().getPassword()).isEqualTo("encoded-local-password");
        assertThat(adminCaptor.getValue().getUpdatedBy()).isZero();
        assertThat(adminCaptor.getValue().getUpdatedTime())
                .isEqualTo(Instant.parse("2026-07-02T00:00:00Z").atOffset(ZoneOffset.UTC).toLocalDateTime());

        ArgumentCaptor<SystemConfigEntity> markerCaptor = ArgumentCaptor.forClass(SystemConfigEntity.class);
        verify(systemConfigMapper).updateById(markerCaptor.capture());
        assertThat(markerCaptor.getValue().getConfigValue()).isEqualTo("true");
    }

    @Test
    void skipsWhenLocalAdminPasswordAlreadyInitialized() {
        when(systemConfigMapper.selectOne(any())).thenReturn(bootstrapMarker("true"));

        service.run(null);

        verify(userMapper, never()).selectOne(any());
        verify(userMapper, never()).updateById(any(UserEntity.class));
        verify(systemConfigMapper, never()).updateById(any(SystemConfigEntity.class));
        verifyNoInteractions(passwordEncoder);
    }

    private static SystemConfigEntity bootstrapMarker(String value) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(1004L);
        entity.setConfigCode("erp.bootstrap.admin-password-initialized");
        entity.setConfigValue(value);
        entity.setDeletedFlag(0);
        return entity;
    }

    private static UserEntity activeAdmin() {
        UserEntity entity = new UserEntity();
        entity.setId(4001L);
        entity.setUsername("admin");
        entity.setDeletedFlag(0);
        entity.setStatus("ACTIVE");
        return entity;
    }
}

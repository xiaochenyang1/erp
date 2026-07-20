package com.tuowei.erp.system.bootstrap;

import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionBootstrapServicePasswordPolicyTest {

    @Mock
    private Environment environment;
    @Mock
    private UserMapper userMapper;
    @Mock
    private SystemConfigMapper systemConfigMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ProductionBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new ProductionBootstrapService(
                environment,
                userMapper,
                systemConfigMapper,
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsBootstrapPasswordWithoutDigitBeforeUpdatingAdmin() {
        when(systemConfigMapper.selectOne(any())).thenReturn(openBootstrapMarker());
        when(environment.getProperty("ERP_BOOTSTRAP_ADMIN_PASSWORD")).thenReturn("passwordonlyvalue");
        when(userMapper.selectOne(any())).thenReturn(activeAdmin());
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(1);
        when(systemConfigMapper.updateById(any(SystemConfigEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> service.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP_BOOTSTRAP_ADMIN_PASSWORD");

        verifyNoInteractions(userMapper, passwordEncoder);
    }

    private static SystemConfigEntity openBootstrapMarker() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setId(7001L);
        entity.setConfigCode("erp.bootstrap.admin-password-initialized");
        entity.setConfigValue("false");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static UserEntity activeAdmin() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setUsername("admin");
        entity.setDeletedFlag(0);
        entity.setStatus("ACTIVE");
        return entity;
    }
}

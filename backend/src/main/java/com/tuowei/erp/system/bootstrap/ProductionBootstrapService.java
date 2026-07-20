package com.tuowei.erp.system.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.validation.PasswordPolicy;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
@Profile("prod")
public class ProductionBootstrapService implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD_INITIALIZED_CONFIG = "erp.bootstrap.admin-password-initialized";

    private final Environment environment;
    private final UserMapper userMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ProductionBootstrapService(
            Environment environment,
            UserMapper userMapper,
            SystemConfigMapper systemConfigMapper,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.environment = environment;
        this.userMapper = userMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SystemConfigEntity marker = requireBootstrapMarker();
        if ("true".equalsIgnoreCase(marker.getConfigValue())) {
            return;
        }

        String adminPassword = environment.getProperty("ERP_BOOTSTRAP_ADMIN_PASSWORD");
        validateBootstrapPassword(adminPassword);

        UserEntity admin = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, ADMIN_USERNAME)
                .eq(UserEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (admin == null) {
            throw new IllegalStateException("生产初始化失败：缺少 admin 用户，请确认 Flyway bootstrap seed 已执行");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setUpdatedBy(0L);
        admin.setUpdatedTime(now);
        if (userMapper.updateById(admin) != 1) {
            throw new IllegalStateException("生产初始化失败：admin 密码更新失败");
        }

        marker.setConfigValue("true");
        marker.setUpdatedBy(0L);
        marker.setUpdatedTime(now);
        if (systemConfigMapper.updateById(marker) != 1) {
            throw new IllegalStateException("生产初始化失败：admin 密码初始化标记更新失败");
        }
    }

    private SystemConfigEntity requireBootstrapMarker() {
        SystemConfigEntity marker = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getConfigCode, ADMIN_PASSWORD_INITIALIZED_CONFIG)
                .eq(SystemConfigEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (marker == null) {
            throw new IllegalStateException("生产初始化失败：缺少 " + ADMIN_PASSWORD_INITIALIZED_CONFIG + " 配置");
        }
        return marker;
    }

    private void validateBootstrapPassword(String adminPassword) {
        if (!StringUtils.hasText(adminPassword) || containsPlaceholder(adminPassword)) {
            throw new IllegalStateException("首次生产启动必须设置符合密码策略且不能使用占位符的 ERP_BOOTSTRAP_ADMIN_PASSWORD");
        }
        try {
            PasswordPolicy.assertValid(adminPassword, "ERP_BOOTSTRAP_ADMIN_PASSWORD");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "首次生产启动必须设置长度12到72位、包含字母和数字且不含空白字符的 ERP_BOOTSTRAP_ADMIN_PASSWORD",
                    ex
            );
        }
    }

    private boolean containsPlaceholder(String value) {
        return value.toUpperCase(Locale.ROOT).contains("CHANGE_ME");
    }
}

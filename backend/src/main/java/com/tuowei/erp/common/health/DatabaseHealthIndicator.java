package com.tuowei.erp.common.health;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 数据库健康检查增强
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @NativeSqlTenantScoped("健康检查查询不涉及租户数据，仅验证数据库连接")
    public Health health() {
        try {
            // 检查数据库连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    // 执行简单查询测试
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class);

                    return Health.up()
                        .withDetail("database", "MySQL")
                        .withDetail("status", "Connected")
                        .build();
                }
            }
            return Health.down()
                .withDetail("error", "Database connection invalid")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

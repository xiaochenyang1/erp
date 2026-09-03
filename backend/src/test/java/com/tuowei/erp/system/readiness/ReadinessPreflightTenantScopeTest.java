package com.tuowei.erp.system.readiness;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.readiness.service.ReadinessPreflightService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessPreflightTenantScopeTest {

    @Test
    void duplicateMasterDataChecksCurrentAccountBook() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                1L, 101L, 202L, LocalDateTime.of(2026, 9, 3, 10, 0)
        ));

        new ReadinessPreflightService(auditMetadataFactory, jdbcTemplate).preflight();

        List<Invocation> duplicateQueries = jdbcTemplate.invocations().stream()
                .filter(invocation -> invocation.sql().contains("deleted_flag = 0"))
                .filter(invocation -> invocation.sql().contains("group by"))
                .filter(invocation -> invocation.sql().contains("md_product")
                        || invocation.sql().contains("md_customer")
                        || invocation.sql().contains("md_supplier")
                        || invocation.sql().contains("md_warehouse"))
                .toList();

        assertThat(duplicateQueries).hasSize(8);
        assertThat(duplicateQueries).allSatisfy(invocation -> {
            assertThat(invocation.sql()).contains("account_book_id = ?");
            assertThat(invocation.args()).containsExactly(101L, 202L);
        });
    }

    private record Invocation(String sql, List<Object> args) {
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final List<Invocation> invocations = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            record(sql, args);
            return requiredType.cast(0L);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            record(sql, args);
            return List.of();
        }

        private void record(String sql, Object[] args) {
            invocations.add(new Invocation(
                    sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(),
                    List.of(args)
            ));
        }

        private List<Invocation> invocations() {
            return invocations;
        }
    }
}

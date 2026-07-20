package com.tuowei.erp.common.idempotency;

import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTenantScopeTest {

    private static final long COMPANY_ID = 996001L;
    private static final long USER_ID = 996101L;
    private static final String IDEMPOTENCY_KEY = "IDEM-BOOK-SCOPE";
    private static final String REQUEST_METHOD = "POST";
    private static final String REQUEST_PATH = "/api/test/idempotency/book-scope";
    private static final String REQUEST_BODY_HASH = "BODY-HASH";

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from sys_idempotency_request
                where company_id = ?
                  and user_id = ?
                  and idempotency_key = ?
                """, COMPANY_ID, USER_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void beginScopesExistingRequestsByAccountBookWithinSameCompanyAndUser() {
        IdempotencyService.BeginResult firstBook = idempotencyService.begin(
                principal(1L),
                IDEMPOTENCY_KEY,
                REQUEST_METHOD,
                REQUEST_PATH,
                REQUEST_BODY_HASH
        );

        IdempotencyService.BeginResult secondBook = idempotencyService.begin(
                principal(2L),
                IDEMPOTENCY_KEY,
                REQUEST_METHOD,
                REQUEST_PATH,
                REQUEST_BODY_HASH
        );

        assertThat(firstBook.replay()).isFalse();
        assertThat(firstBook.requestId()).isNotNull();
        assertThat(secondBook.replay()).isFalse();
        assertThat(secondBook.requestId()).isNotNull();
        assertThat(secondBook.requestId()).isNotEqualTo(firstBook.requestId());
    }

    private ErpPrincipal principal(long accountBookId) {
        return new ErpPrincipal(
                USER_ID,
                COMPANY_ID,
                accountBookId,
                1L,
                1L,
                "idem_book_user",
                "幂等账套用户",
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
    }
}

package com.tuowei.erp.common.idempotency;

import com.tuowei.erp.common.config.IdempotencyProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.idempotency.mapper.IdempotencyRequestMapper;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-02T01:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Test
    void beginFailsWhenNewRequestInsertDoesNotPersistRow() {
        IdempotencyRequestMapper mapper = mock(IdempotencyRequestMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(IdempotencyRequestEntity.class))).thenReturn(0);
        IdempotencyService service = new IdempotencyService(
                mapper,
                new IdempotencyProperties(true, 86_400, 1024, 1024),
                CLOCK
        );

        assertThatThrownBy(() -> service.begin(
                principal(),
                "IDEM-INSERT-FAIL",
                "post",
                "/api/test/idempotency",
                "REQUEST-HASH"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存幂等请求失败");
    }

    @Test
    void beginTreatsExistingRequestWithMissingBodyHashAsConflict() {
        IdempotencyRequestMapper mapper = mock(IdempotencyRequestMapper.class);
        IdempotencyRequestEntity existing = new IdempotencyRequestEntity();
        existing.setId(9901L);
        existing.setRequestBodyHash(null);
        existing.setStatus("PROCESSING");
        existing.setExpiresAt(LocalDateTime.of(2026, 6, 3, 9, 0));
        when(mapper.selectOne(any())).thenReturn(existing);
        IdempotencyService service = new IdempotencyService(
                mapper,
                new IdempotencyProperties(true, 86_400, 1024, 1024),
                CLOCK
        );

        assertThatThrownBy(() -> service.begin(
                principal(),
                "IDEM-MISSING-HASH",
                "post",
                "/api/test/idempotency",
                "REQUEST-HASH"
        ))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Idempotency-Key 已用于不同请求，请重新生成后再提交");
    }

    private ErpPrincipal principal() {
        return new ErpPrincipal(
                996101L,
                996001L,
                1L,
                1L,
                1L,
                "idem_user",
                "幂等用户",
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
    }
}

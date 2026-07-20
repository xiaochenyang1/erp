package com.tuowei.erp.finance.receivable;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.service.ReceivableQueryService;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ReceivableControllerTest {

    private static final String RECEIVABLE_VIEW = "finance:receivable:view";

    @Autowired
    private ReceivableQueryService receivableQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receivable where id between 861000 and 861999");
    }

    @Test
    @WithErpUser(
            userId = 861001L,
            authorities = {RECEIVABLE_VIEW},
            allScope = false,
            selfScoped = true
    )
    void listsOwnOpeningReceivablesAndBlocksForeignDetail() {
        seedOpeningReceivable(861101L, "AR-OPEN-861101", 861001L);
        seedOpeningReceivable(861102L, "AR-OPEN-861102", 861002L);

        ReceivablePageQuery query = new ReceivablePageQuery();
        query.setPageNo(1);
        query.setPageSize(10);
        PageResponse<ReceivableResponse> response = receivableQueryService.list(query);

        Assertions.assertThat(response.total()).isEqualTo(1);
        Assertions.assertThat(response.records()).hasSize(1);
        Assertions.assertThat(response.records().get(0).receivableNo()).isEqualTo("AR-OPEN-861101");
        Assertions.assertThat(response.records().get(0).remainingAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThatThrownBy(() -> receivableQueryService.detail(861102L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void seedOpeningReceivable(long id, String receivableNo, long createdBy) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'OPENING_RECEIVABLE', ?, ?, 'INCREASE',
                        8601, '2026-05-18', ?, ?, 'UNSETTLED', 0, 'receivable entrypoint test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                receivableNo,
                id,
                receivableNo,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                createdBy,
                LocalDateTime.of(2026, 5, 18, 9, 0),
                createdBy,
                LocalDateTime.of(2026, 5, 18, 9, 0));
    }
}

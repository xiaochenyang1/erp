package com.tuowei.erp.finance.payable;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
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
class PayableControllerTest {

    private static final String PAYABLE_VIEW = "finance:payable:view";

    @Autowired
    private PayableQueryService payableQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_payable where id between 862000 and 862999");
    }

    @Test
    @WithErpUser(
            userId = 862001L,
            authorities = {PAYABLE_VIEW},
            allScope = false,
            selfScoped = true
    )
    void listsOwnOpeningPayablesAndBlocksForeignDetail() throws Exception {
        seedOpeningPayable(862101L, "AP-OPEN-862101", 862001L);
        seedOpeningPayable(862102L, "AP-OPEN-862102", 862002L);

        PayablePageQuery query = new PayablePageQuery();
        query.setPageNo(1);
        query.setPageSize(10);
        PageResponse<PayableResponse> response = payableQueryService.list(query);

        Assertions.assertThat(response.total()).isEqualTo(1);
        Assertions.assertThat(response.records()).hasSize(1);
        Assertions.assertThat(response.records().get(0).payableNo()).isEqualTo("AP-OPEN-862101");
        Assertions.assertThat(response.records().get(0).remainingAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThatThrownBy(() -> payableQueryService.detail(862102L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void seedOpeningPayable(long id, String payableNo, long createdBy) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'OPENING_PAYABLE', ?, ?, 'INCREASE',
                        8701, '2026-05-18', ?, ?, 'UNSETTLED', 0, 'payable entrypoint test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                payableNo,
                id,
                payableNo,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                createdBy,
                LocalDateTime.of(2026, 5, 18, 9, 0),
                createdBy,
                LocalDateTime.of(2026, 5, 18, 9, 0));
    }
}

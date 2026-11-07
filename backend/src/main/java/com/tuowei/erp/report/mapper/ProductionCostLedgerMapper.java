package com.tuowei.erp.report.mapper;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
@NativeSqlTenantScoped("The ledger aggregation binds company_id/account_book_id and joins every production fact, voucher, and entry within that same scope before summing subject 5001.")
public interface ProductionCostLedgerMapper {

    @Select({
            "<script>",
            "SELECT facts.order_id AS orderId,",
            "       COALESCE(SUM(entries.debit_amount - entries.credit_amount), 0) AS ledgerAmount",
            "FROM (",
            "    SELECT company_id, account_book_id, order_id, 'PRODUCTION_ISSUE' AS source_type, id AS source_id FROM prd_issue",
            "    UNION ALL SELECT company_id, account_book_id, order_id, 'PRODUCTION_RETURN', id FROM prd_return",
            "    UNION ALL SELECT company_id, account_book_id, order_id, 'PRODUCTION_COMPLETION', id FROM prd_completion",
            "    UNION ALL SELECT company_id, account_book_id, order_id, 'PRODUCTION_COMPLETION_REVERSAL', id FROM prd_completion_reversal",
            "    UNION ALL SELECT company_id, account_book_id, order_id,",
            "        CASE WHEN reversal_of_entry_id IS NULL THEN 'PRODUCTION_COST_ENTRY' ELSE 'PRODUCTION_COST_ENTRY_REVERSAL' END,",
            "        id FROM prd_cost_entry",
            "    UNION ALL SELECT company_id, account_book_id, order_id,",
            "        CASE WHEN reversal_of_close_id IS NULL THEN 'PRODUCTION_COST_CLOSE' ELSE 'PRODUCTION_COST_CLOSE_REVERSAL' END,",
            "        id FROM prd_cost_close",
            ") facts",
            "JOIN fin_voucher vouchers",
            "  ON vouchers.company_id = facts.company_id",
            " AND vouchers.account_book_id = facts.account_book_id",
            " AND vouchers.source_type = facts.source_type",
            " AND vouchers.source_id = facts.source_id",
            " AND vouchers.deleted_flag = 0",
            "JOIN fin_voucher_entry entries",
            "  ON entries.company_id = vouchers.company_id",
            " AND entries.account_book_id = vouchers.account_book_id",
            " AND entries.voucher_id = vouchers.id",
            " AND entries.subject_code = '5001'",
            "WHERE facts.company_id = #{companyId}",
            "  AND facts.account_book_id = #{accountBookId}",
            "  AND facts.order_id IN",
            "  <foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>#{orderId}</foreach>",
            "GROUP BY facts.order_id",
            "</script>"
    })
    List<ProductionCostLedgerRow> summarize(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("orderIds") List<Long> orderIds
    );
}

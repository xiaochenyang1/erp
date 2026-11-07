package com.tuowei.erp.finance.fx.mapper;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.fx.model.FxRevaluationBalance;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
@NativeSqlTenantScoped("Both subledger branches and allocation aggregates filter company_id/account_book_id; every allocation joins its settlement and source on the same company/account book and partner.")
public interface FxRevaluationBalanceMapper {

    @ConstructorArgs({
            @Arg(column = "side", javaType = String.class),
            @Arg(column = "item_id", javaType = Long.class),
            @Arg(column = "partner_id", javaType = Long.class),
            @Arg(column = "biz_date", javaType = LocalDate.class),
            @Arg(column = "direction", javaType = String.class),
            @Arg(column = "currency_code", javaType = String.class),
            @Arg(column = "original_amount", javaType = BigDecimal.class),
            @Arg(column = "settled_amount", javaType = BigDecimal.class),
            @Arg(column = "exchange_rate", javaType = BigDecimal.class),
            @Arg(column = "base_original_amount", javaType = BigDecimal.class),
            @Arg(column = "base_settled_amount", javaType = BigDecimal.class),
            @Arg(column = "posted_base_settled_amount", javaType = BigDecimal.class),
            @Arg(column = "future_settled_amount", javaType = BigDecimal.class),
            @Arg(column = "future_base_settled_amount", javaType = BigDecimal.class)
    })
    @Select("""
            SELECT 'AR' AS side, i.id AS item_id, i.customer_id AS partner_id, i.biz_date,
                   i.direction, i.currency_code, i.original_amount, i.settled_amount, i.exchange_rate,
                   i.base_original_amount, i.base_settled_amount, i.posted_base_settled_amount,
                   COALESCE(f.amount, 0) AS future_settled_amount,
                   COALESCE(f.base_amount, 0) AS future_base_settled_amount
            FROM fin_receivable i
            LEFT JOIN (
                SELECT a.receivable_id AS item_id, SUM(a.amount) AS amount,
                       SUM(CASE WHEN s.posted_base_settled_amount IS NOT NULL
                                THEN COALESCE(a.posted_base_settled_amount,
                                    ROUND(CASE WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount
                                          ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END, 2))
                                WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount
                                ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END) AS base_amount
                FROM fin_receipt_allocation a
                JOIN fin_receipt h ON h.id = a.receipt_id
                    AND h.company_id = a.company_id AND h.account_book_id = a.account_book_id
                JOIN fin_receivable s ON s.id = a.receivable_id
                    AND s.company_id = a.company_id AND s.account_book_id = a.account_book_id
                    AND s.customer_id = h.customer_id
                WHERE a.company_id = #{companyId} AND a.account_book_id = #{accountBookId}
                  AND h.status = 'POSTED' AND h.deleted_flag = 0 AND h.receipt_date > #{periodEnd}
                GROUP BY a.receivable_id
            ) f ON f.item_id = i.id
            WHERE i.company_id = #{companyId} AND i.account_book_id = #{accountBookId}
              AND i.deleted_flag = 0 AND i.biz_date <= #{periodEnd}
              AND i.status IN ('UNSETTLED', 'PARTIALLY_SETTLED', 'SETTLED', 'OFFSET')
              AND (COALESCE(i.original_amount, 0) <> COALESCE(i.settled_amount, 0) OR COALESCE(f.amount, 0) <> 0)
              AND UPPER(COALESCE(NULLIF(TRIM(i.currency_code), ''), 'CNY')) <> #{baseCurrency}
            UNION ALL
            SELECT 'AP' AS side, i.id AS item_id, i.supplier_id AS partner_id, i.biz_date,
                   i.direction, i.currency_code, i.original_amount, i.settled_amount, i.exchange_rate,
                   i.base_original_amount, i.base_settled_amount, i.posted_base_settled_amount,
                   COALESCE(f.amount, 0) AS future_settled_amount,
                   COALESCE(f.base_amount, 0) AS future_base_settled_amount
            FROM fin_payable i
            LEFT JOIN (
                SELECT a.payable_id AS item_id, SUM(a.amount) AS amount,
                       SUM(CASE WHEN s.posted_base_settled_amount IS NOT NULL
                                THEN COALESCE(a.posted_base_settled_amount,
                                    ROUND(CASE WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount
                                          ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END, 2))
                                WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount
                                ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END) AS base_amount
                FROM fin_payment_allocation a
                JOIN fin_payment h ON h.id = a.payment_id
                    AND h.company_id = a.company_id AND h.account_book_id = a.account_book_id
                JOIN fin_payable s ON s.id = a.payable_id
                    AND s.company_id = a.company_id AND s.account_book_id = a.account_book_id
                    AND s.supplier_id = h.supplier_id
                WHERE a.company_id = #{companyId} AND a.account_book_id = #{accountBookId}
                  AND h.status = 'POSTED' AND h.deleted_flag = 0 AND h.payment_date > #{periodEnd}
                GROUP BY a.payable_id
            ) f ON f.item_id = i.id
            WHERE i.company_id = #{companyId} AND i.account_book_id = #{accountBookId}
              AND i.deleted_flag = 0 AND i.biz_date <= #{periodEnd}
              AND i.status IN ('UNSETTLED', 'PARTIALLY_SETTLED', 'SETTLED', 'OFFSET')
              AND (COALESCE(i.original_amount, 0) <> COALESCE(i.settled_amount, 0) OR COALESCE(f.amount, 0) <> 0)
              AND UPPER(COALESCE(NULLIF(TRIM(i.currency_code), ''), 'CNY')) <> #{baseCurrency}
            """)
    List<FxRevaluationBalance> selectAtPeriodEnd(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("baseCurrency") String baseCurrency
    );
}

package com.tuowei.erp.finance.ledger.mapper;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.ledger.model.TrialBalanceAggregate;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
@NativeSqlTenantScoped("Trial balance aggregation binds current company_id/account_book_id on voucher entries and on the optional subject metadata join.")
public interface TrialBalanceMapper {

    @ConstructorArgs({
            @Arg(column = "subject_code", javaType = String.class),
            @Arg(column = "subject_name", javaType = String.class),
            @Arg(column = "subject_type", javaType = String.class),
            @Arg(column = "balance_direction", javaType = String.class),
            @Arg(column = "opening_debit", javaType = BigDecimal.class),
            @Arg(column = "opening_credit", javaType = BigDecimal.class),
            @Arg(column = "period_debit", javaType = BigDecimal.class),
            @Arg(column = "period_credit", javaType = BigDecimal.class)
    })
    @Select("""
            SELECT e.subject_code,
                   MAX(e.subject_name) AS subject_name,
                   COALESCE(MAX(s.subject_type), 'UNKNOWN') AS subject_type,
                   COALESCE(MAX(s.balance_direction), 'DEBIT') AS balance_direction,
                   SUM(CASE WHEN e.biz_date < #{dateFrom} THEN e.debit_amount ELSE 0 END) AS opening_debit,
                   SUM(CASE WHEN e.biz_date < #{dateFrom} THEN e.credit_amount ELSE 0 END) AS opening_credit,
                   SUM(CASE WHEN e.biz_date >= #{dateFrom} THEN e.debit_amount ELSE 0 END) AS period_debit,
                   SUM(CASE WHEN e.biz_date >= #{dateFrom} THEN e.credit_amount ELSE 0 END) AS period_credit
            FROM fin_voucher_entry e
            LEFT JOIN fin_account_subject s
              ON s.company_id = e.company_id
             AND s.account_book_id = e.account_book_id
             AND s.subject_code = e.subject_code
             AND s.deleted_flag = 0
            WHERE e.company_id = #{companyId}
              AND e.account_book_id = #{accountBookId}
              AND e.biz_date <= #{dateTo}
            GROUP BY e.subject_code
            ORDER BY e.subject_code
            """)
    List<TrialBalanceAggregate> aggregate(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );
}

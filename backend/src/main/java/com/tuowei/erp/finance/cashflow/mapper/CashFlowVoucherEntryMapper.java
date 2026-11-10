package com.tuowei.erp.finance.cashflow.mapper;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.cashflow.model.CashFlowVoucherEntry;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
@NativeSqlTenantScoped("Cash-flow voucher entry query binds company_id/account_book_id in both the cash-voucher subquery and outer entry query.")
public interface CashFlowVoucherEntryMapper {

    @ConstructorArgs({
            @Arg(column = "voucher_id", javaType = Long.class),
            @Arg(column = "biz_date", javaType = LocalDate.class),
            @Arg(column = "line_no", javaType = Integer.class),
            @Arg(column = "subject_code", javaType = String.class),
            @Arg(column = "subject_name", javaType = String.class),
            @Arg(column = "debit_amount", javaType = BigDecimal.class),
            @Arg(column = "credit_amount", javaType = BigDecimal.class)
    })
    @Select("""
            <script>
            SELECT e.voucher_id, e.biz_date, e.line_no, e.subject_code, e.subject_name,
                   e.debit_amount, e.credit_amount
            FROM fin_voucher_entry e
            JOIN (
                SELECT DISTINCT cash.voucher_id
                FROM fin_voucher_entry cash
                WHERE cash.company_id = #{companyId}
                  AND cash.account_book_id = #{accountBookId}
                  AND cash.biz_date BETWEEN #{dateFrom} AND #{dateTo}
                  AND cash.subject_code IN
                  <foreach collection="cashSubjectCodes" item="code" open="(" separator="," close=")">
                    #{code}
                  </foreach>
            ) cash_voucher ON cash_voucher.voucher_id = e.voucher_id
            WHERE e.company_id = #{companyId}
              AND e.account_book_id = #{accountBookId}
              AND e.biz_date BETWEEN #{dateFrom} AND #{dateTo}
            ORDER BY e.voucher_id, e.line_no
            </script>
            """)
    List<CashFlowVoucherEntry> selectCashVoucherEntries(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("cashSubjectCodes") List<String> cashSubjectCodes
    );
}

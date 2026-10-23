package com.tuowei.erp.finance.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.payment.model.PaymentAllocationEntity;
import com.tuowei.erp.finance.posting.SettlementAllocationTotals;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
@NativeSqlTenantScoped("Active allocation totals filter company/account book and join the header and source on the same tenant and partner.")
public interface PaymentAllocationMapper extends BaseMapper<PaymentAllocationEntity> {
    @ConstructorArgs({
            @Arg(column = "original_amount", javaType = BigDecimal.class),
            @Arg(column = "raw_relief", javaType = BigDecimal.class),
            @Arg(column = "posted_relief", javaType = BigDecimal.class)
    })
    @Select("""
            SELECT COALESCE(SUM(a.amount), 0) AS original_amount,
                   COALESCE(SUM(CASE WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END), 0) AS raw_relief,
                   COALESCE(SUM(COALESCE(a.posted_base_settled_amount, ROUND(CASE WHEN COALESCE(a.base_settled_amount, 0) <> 0 THEN a.base_settled_amount ELSE ROUND(a.amount * COALESCE(s.exchange_rate, 1), 6) END, 2))), 0) AS posted_relief
            FROM fin_payment_allocation a
            JOIN fin_payment h ON h.id = a.payment_id
                AND h.company_id = a.company_id AND h.account_book_id = a.account_book_id
            JOIN fin_payable s ON s.id = a.payable_id
                AND s.company_id = a.company_id AND s.account_book_id = a.account_book_id
                AND s.supplier_id = h.supplier_id
            WHERE a.company_id = #{companyId} AND a.account_book_id = #{accountBookId}
              AND a.payable_id = #{itemId} AND s.deleted_flag = 0
              AND h.status = 'POSTED' AND h.deleted_flag = 0
            """)
    SettlementAllocationTotals selectPostedTotals(@Param("companyId") Long companyId,
                                                @Param("accountBookId") Long accountBookId,
                                                @Param("itemId") Long itemId);
}

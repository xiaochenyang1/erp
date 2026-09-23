package com.tuowei.erp.report.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.report.web.FxGainLossReportResponse;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 已实现汇兑损益明细。
 *
 * <p>核销明细本身没有租户视角的来源单据信息，所以数据范围一律通过对子账表的
 * {@code IN (SELECT id FROM fin_payable|fin_receivable ${wrapper.customSqlSegment})} 子查询施加。
 * 把范围条件放在只含单张子账表的子查询里，既复用了 {@code FinanceSettlementScopeSupport}
 * 已验证的范围 SQL（其中的 {@code source_id} 引用需要无歧义），又避免与 join 进来的
 * 收付款单表出现同名列冲突。
 */
@Mapper
@NativeSqlTenantScoped("Data scope and tenant filters are injected by FinanceSettlementScopeSupport into the payable/receivable wrappers, which gate the allocation rows through the IN sub-queries below.")
public interface FxGainLossReportMapper {

    String FROM_CLAUSE = """
            FROM (
                SELECT
                    allocation.id AS allocation_id,
                    'PAYABLE' AS direction,
                    settlement.payment_no AS settlement_no,
                    settlement.payment_date AS settlement_date,
                    settlement.supplier_id AS partner_id,
                    subledger.payable_no AS subledger_no,
                    subledger.currency_code AS currency_code,
                    allocation.amount AS allocated_amount,
                    subledger.exchange_rate AS booking_rate,
                    settlement.exchange_rate AS settlement_rate,
                    allocation.base_amount AS base_allocated_amount,
                    allocation.base_settled_amount AS base_settled_amount,
                    allocation.fx_gain_loss_amount AS fx_gain_loss_amount,
                    0 AS source_order
                FROM fin_payment_allocation allocation
                JOIN fin_payment settlement ON settlement.id = allocation.payment_id
                JOIN fin_payable subledger ON subledger.id = allocation.payable_id
                WHERE allocation.fx_gain_loss_amount != 0
                  AND settlement.status = 'POSTED'
                  AND allocation.payable_id IN (SELECT id FROM fin_payable ${payableWrapper.customSqlSegment})
                  <if test="direction != null"> AND 'PAYABLE' = #{direction}</if>
                  <if test="settlementDateFrom != null"> AND settlement.payment_date >= #{settlementDateFrom}</if>
                  <if test="settlementDateTo != null"> AND settlement.payment_date &lt;= #{settlementDateTo}</if>
                UNION ALL
                SELECT
                    allocation.id AS allocation_id,
                    'RECEIVABLE' AS direction,
                    settlement.receipt_no AS settlement_no,
                    settlement.receipt_date AS settlement_date,
                    settlement.customer_id AS partner_id,
                    subledger.receivable_no AS subledger_no,
                    subledger.currency_code AS currency_code,
                    allocation.amount AS allocated_amount,
                    subledger.exchange_rate AS booking_rate,
                    settlement.exchange_rate AS settlement_rate,
                    allocation.base_amount AS base_allocated_amount,
                    allocation.base_settled_amount AS base_settled_amount,
                    allocation.fx_gain_loss_amount AS fx_gain_loss_amount,
                    1 AS source_order
                FROM fin_receipt_allocation allocation
                JOIN fin_receipt settlement ON settlement.id = allocation.receipt_id
                JOIN fin_receivable subledger ON subledger.id = allocation.receivable_id
                WHERE allocation.fx_gain_loss_amount != 0
                  AND settlement.status = 'POSTED'
                  AND allocation.receivable_id IN (SELECT id FROM fin_receivable ${receivableWrapper.customSqlSegment})
                  <if test="direction != null"> AND 'RECEIVABLE' = #{direction}</if>
                  <if test="settlementDateFrom != null"> AND settlement.receipt_date >= #{settlementDateFrom}</if>
                  <if test="settlementDateTo != null"> AND settlement.receipt_date &lt;= #{settlementDateTo}</if>
            ) fx_rows
            """;

    @ConstructorArgs({
            @Arg(column = "allocation_id", javaType = Long.class),
            @Arg(column = "direction", javaType = String.class),
            @Arg(column = "settlement_no", javaType = String.class),
            @Arg(column = "settlement_date", javaType = LocalDate.class),
            @Arg(column = "partner_id", javaType = Long.class),
            @Arg(column = "subledger_no", javaType = String.class),
            @Arg(column = "currency_code", javaType = String.class),
            @Arg(column = "allocated_amount", javaType = BigDecimal.class),
            @Arg(column = "booking_rate", javaType = BigDecimal.class),
            @Arg(column = "settlement_rate", javaType = BigDecimal.class),
            @Arg(column = "base_allocated_amount", javaType = BigDecimal.class),
            @Arg(column = "base_settled_amount", javaType = BigDecimal.class),
            @Arg(column = "fx_gain_loss_amount", javaType = BigDecimal.class)
    })
    @Select("""
            <script>
            SELECT
                allocation_id,
                direction,
                settlement_no,
                settlement_date,
                partner_id,
                subledger_no,
                currency_code,
                allocated_amount,
                booking_rate,
                settlement_rate,
                base_allocated_amount,
                base_settled_amount,
                fx_gain_loss_amount
            """ + FROM_CLAUSE + """
            ORDER BY settlement_date DESC, allocation_id DESC, source_order ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FxGainLossReportResponse> selectFxGainLossPage(
            @Param("payableWrapper") Wrapper<PayableEntity> payableWrapper,
            @Param("receivableWrapper") Wrapper<ReceivableEntity> receivableWrapper,
            @Param("direction") String direction,
            @Param("settlementDateFrom") LocalDate settlementDateFrom,
            @Param("settlementDateTo") LocalDate settlementDateTo,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            """ + FROM_CLAUSE + """
            </script>
            """)
    long countFxGainLoss(
            @Param("payableWrapper") Wrapper<PayableEntity> payableWrapper,
            @Param("receivableWrapper") Wrapper<ReceivableEntity> receivableWrapper,
            @Param("direction") String direction,
            @Param("settlementDateFrom") LocalDate settlementDateFrom,
            @Param("settlementDateTo") LocalDate settlementDateTo
    );
}

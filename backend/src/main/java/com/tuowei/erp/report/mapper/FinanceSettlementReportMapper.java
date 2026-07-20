package com.tuowei.erp.report.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
@NativeSqlTenantScoped("The UNION query receives MyBatis-Plus wrappers built by ReportQueryService and FinanceSettlementScopeSupport; those wrappers inject tenant and data-scope filters before customSqlSegment is expanded.")
public interface FinanceSettlementReportMapper {

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "direction", javaType = String.class),
            @Arg(column = "biz_no", javaType = String.class),
            @Arg(column = "partner_id", javaType = Long.class),
            @Arg(column = "biz_date", javaType = LocalDate.class),
            @Arg(column = "source_type", javaType = String.class),
            @Arg(column = "source_no", javaType = String.class),
            @Arg(column = "original_amount", javaType = BigDecimal.class),
            @Arg(column = "settled_amount", javaType = BigDecimal.class),
            @Arg(column = "remaining_amount", javaType = BigDecimal.class),
            @Arg(column = "status", javaType = String.class)
    })
    @Select("""
            <script>
            SELECT
                id,
                direction,
                biz_no,
                partner_id,
                biz_date,
                source_type,
                source_no,
                original_amount,
                settled_amount,
                remaining_amount,
                status
            FROM (
                SELECT
                    id,
                    'PAYABLE' AS direction,
                    payable_no AS biz_no,
                    supplier_id AS partner_id,
                    biz_date,
                    source_type,
                    source_no,
                    original_amount,
                    settled_amount,
                    original_amount - settled_amount AS remaining_amount,
                    status,
                    0 AS source_order
                FROM fin_payable
                ${payableWrapper.customSqlSegment}
                UNION ALL
                SELECT
                    id,
                    'RECEIVABLE' AS direction,
                    receivable_no AS biz_no,
                    customer_id AS partner_id,
                    biz_date,
                    source_type,
                    source_no,
                    original_amount,
                    settled_amount,
                    original_amount - settled_amount AS remaining_amount,
                    status,
                    1 AS source_order
                FROM fin_receivable
                ${receivableWrapper.customSqlSegment}
            ) settlement_rows
            ORDER BY biz_date DESC, id DESC, source_order ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FinanceSettlementReportResponse> selectAllSettlementPage(
            @Param("payableWrapper") Wrapper<PayableEntity> payableWrapper,
            @Param("receivableWrapper") Wrapper<ReceivableEntity> receivableWrapper,
            @Param("limit") long limit,
            @Param("offset") long offset
    );
}

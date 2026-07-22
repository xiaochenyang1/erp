package com.tuowei.erp.sales.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.tuowei.erp.dashboard.web.OperationsDashboardTopSkuResponse;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SalesDeliveryLineMapper extends BaseMapper<SalesDeliveryLineEntity> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT l.product_id AS productId,
                   p.product_code AS productCode,
                   p.product_name AS productName,
                   p.unit_name AS unitName,
                   SUM(l.qty) AS quantity,
                   SUM(l.amount) AS amount
            FROM sal_delivery_line l
            JOIN sal_delivery d ON d.id = l.delivery_id
            JOIN md_product p ON p.id = l.product_id
            WHERE d.company_id = #{companyId}
              AND d.account_book_id = #{accountBookId}
              AND l.company_id = #{companyId}
              AND l.account_book_id = #{accountBookId}
              AND p.company_id = #{companyId}
              AND p.account_book_id = #{accountBookId}
              AND d.deleted_flag = 0
              AND p.deleted_flag = 0
              AND d.status = 'POSTED'
              AND d.delivery_date BETWEEN #{startDate} AND #{endDate}
            GROUP BY l.product_id, p.product_code, p.product_name, p.unit_name
            ORDER BY SUM(l.qty) DESC, SUM(l.amount) DESC, l.product_id
            LIMIT #{limit}
            """)
    List<OperationsDashboardTopSkuResponse> selectTopSkus(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit
    );
}

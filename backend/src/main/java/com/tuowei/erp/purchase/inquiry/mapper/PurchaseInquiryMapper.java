package com.tuowei.erp.purchase.inquiry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.purchase.inquiry.model.PurchaseInquiryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
@NativeSqlTenantScoped("The locking read always constrains id, company_id, account_book_id and deleted_flag.")
public interface PurchaseInquiryMapper extends BaseMapper<PurchaseInquiryEntity> {

    @Select("""
            select id,
                   company_id as companyId,
                   account_book_id as accountBookId,
                   inquiry_no as inquiryNo,
                   inquiry_date as inquiryDate,
                   status,
                   selected_supplier_id as selectedSupplierId,
                   selected_quote_id as selectedQuoteId,
                   converted_order_id as convertedOrderId,
                   converted_order_no as convertedOrderNo,
                   converted_by as convertedBy,
                   converted_time as convertedTime,
                   title,
                   deleted_flag as deletedFlag,
                   remark,
                   created_by as createdBy,
                   created_time as createdTime,
                   updated_by as updatedBy,
                   updated_time as updatedTime,
                   version
            from pur_inquiry
            where id = #{id}
              and company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and deleted_flag = 0
            for update
            """)
    PurchaseInquiryEntity selectForUpdate(
            @Param("id") Long id,
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId
    );
}

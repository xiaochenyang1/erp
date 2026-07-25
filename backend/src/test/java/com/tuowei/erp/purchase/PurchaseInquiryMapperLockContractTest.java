package com.tuowei.erp.purchase;

import com.tuowei.erp.purchase.inquiry.mapper.PurchaseInquiryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInquiryMapperLockContractTest {

    @Test
    void atomicConversionLocksTheTenantScopedInquiryRow() throws Exception {
        Select select = PurchaseInquiryMapper.class
                .getMethod("selectForUpdate", Long.class, Long.class, Long.class)
                .getAnnotation(Select.class);

        assertThat(select).isNotNull();
        String sql = String.join(" ", select.value()).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id = #{companyid}")
                .contains("account_book_id = #{accountbookid}")
                .contains("deleted_flag = 0")
                .contains("for update");
    }
}

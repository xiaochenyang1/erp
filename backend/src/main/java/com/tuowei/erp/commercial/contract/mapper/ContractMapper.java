package com.tuowei.erp.commercial.contract.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContractMapper extends BaseMapper<ContractEntity> {

    @NativeSqlTenantScoped("scheduler discovers only active contract tenant scopes; each scope is restored as a system principal before tenant-owned rows are scanned")
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            select distinct company_id, account_book_id
            from biz_contract
            where status = 'ACTIVE'
              and deleted_flag = 0
            order by company_id, account_book_id
            """)
    List<ContractEntity> selectActiveTenantScopesForScheduler();
}

package com.tuowei.erp.system.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.system.config.model.SequenceCounterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
@NativeSqlTenantScoped("Sequence counters are company and account-book scoped; every native statement constrains company_id, account_book_id, biz_type and period_key.")
public interface SequenceCounterMapper extends BaseMapper<SequenceCounterEntity> {

    @Update("""
            update sys_sequence_counter
            set current_value = current_value + 1,
                updated_by = #{updatedBy},
                updated_time = #{updatedTime},
                version = version + 1
            where company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and biz_type = #{bizType}
              and period_key = #{periodKey}
            """)
    int incrementCurrentValue(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("bizType") String bizType,
            @Param("periodKey") String periodKey,
            @Param("updatedBy") Long updatedBy,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    @Select("""
            select current_value
            from sys_sequence_counter
            where company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and biz_type = #{bizType}
              and period_key = #{periodKey}
            """)
    Long selectCurrentValue(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("bizType") String bizType,
            @Param("periodKey") String periodKey
    );

    @Select("""
            select id,
                   company_id as companyId,
                   account_book_id as accountBookId,
                   biz_type as bizType,
                   period_key as periodKey,
                   current_value as currentValue,
                   created_by as createdBy,
                   created_time as createdTime,
                   updated_by as updatedBy,
                   updated_time as updatedTime,
                   version
            from sys_sequence_counter
            where company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and biz_type = #{bizType}
              and period_key = #{periodKey}
            for update
            """)
    SequenceCounterEntity selectForUpdate(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("bizType") String bizType,
            @Param("periodKey") String periodKey
    );

    @Select("""
            select max(current_value)
            from sys_sequence_counter
            where company_id = #{companyId}
              and account_book_id = #{accountBookId}
              and biz_type = #{bizType}
            """)
    Long selectMaxCurrentValue(
            @Param("companyId") Long companyId,
            @Param("accountBookId") Long accountBookId,
            @Param("bizType") String bizType
    );
}

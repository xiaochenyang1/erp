package com.tuowei.erp.common.scheduler.mapper;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
@NativeSqlTenantScoped("The scheduler lease is a process-wide coordination row without tenant data; acquisition and release constrain the lease key and owner token explicitly.")
public interface SchedulerLeaseMapper {

    @Update("""
            update sys_scheduler_lease
            set owner_token = #{ownerToken},
                expires_at = #{expiresAt},
                updated_time = #{now},
                version = version + 1
            where lease_key = #{leaseKey}
              and (expires_at is null or expires_at <= #{now} or owner_token = #{ownerToken})
            """)
    int tryAcquire(
            @Param("leaseKey") String leaseKey,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    @Update("""
            update sys_scheduler_lease
            set expires_at = #{expiresAt},
                updated_time = #{now},
                version = version + 1
            where lease_key = #{leaseKey}
              and owner_token = #{ownerToken}
              and expires_at > #{now}
            """)
    int renew(
            @Param("leaseKey") String leaseKey,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    @Update("""
            update sys_scheduler_lease
            set owner_token = '',
                expires_at = #{now},
                updated_time = #{now},
                version = version + 1
            where lease_key = #{leaseKey}
              and owner_token = #{ownerToken}
            """)
    int release(
            @Param("leaseKey") String leaseKey,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now
    );
}

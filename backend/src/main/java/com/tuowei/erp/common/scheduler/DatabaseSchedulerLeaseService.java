package com.tuowei.erp.common.scheduler;

import com.tuowei.erp.common.scheduler.mapper.SchedulerLeaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** MySQL-backed implementation of the process-wide scheduler lease. */
@Service
public class DatabaseSchedulerLeaseService implements SchedulerLeaseService {

    private final SchedulerLeaseMapper mapper;
    private final Clock clock;

    public DatabaseSchedulerLeaseService(SchedulerLeaseMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * The update commits before the caller starts its automation work.  In
     * particular, no row lock is held while tenant scans and ticket updates
     * run.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl) {
        validateLeaseRequest(leaseKey, ownerToken, now, ttl);
        LocalDateTime expiresAt = now.plus(ttl);
        return mapper.tryAcquire(leaseKey, ownerToken, now, expiresAt) == 1;
    }

    /** Renewal cannot revive an expired lease or overwrite a successor's token. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl) {
        validateLeaseRequest(leaseKey, ownerToken, now, ttl);
        LocalDateTime expiresAt = now.plus(ttl);
        return mapper.renew(leaseKey, ownerToken, now, expiresAt) == 1;
    }

    /** Release is conditional, so an expired owner cannot clear a successor's lease. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String leaseKey, String ownerToken) {
        requireText(leaseKey, "leaseKey");
        requireText(ownerToken, "ownerToken");
        mapper.release(leaseKey, ownerToken, LocalDateTime.now(clock));
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private void validateLeaseRequest(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl) {
        requireText(leaseKey, "leaseKey");
        requireText(ownerToken, "ownerToken");
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("租约时长必须大于 0");
        }
    }
}

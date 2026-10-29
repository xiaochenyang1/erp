package com.tuowei.erp.common.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Process-wide lease used to ensure that only one application instance runs a
 * scheduled automation cycle at a time.
 *
 * <p>The implementation is deliberately small: callers provide an owner
 * token, acquisition is conditional on expiry (or re-entrance by that token),
 * and release is conditional on the same token.  This prevents an old
 * instance from releasing a lease that a newer instance has already taken
 * after the old lease expired.</p>
 */
public interface SchedulerLeaseService {

    SchedulerLeaseService NOOP = new SchedulerLeaseService() {
        @Override
        public boolean tryAcquire(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl) {
            return true;
        }

        @Override
        public boolean renew(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl) {
            return true;
        }

        @Override
        public void release(String leaseKey, String ownerToken) {
            // Compatibility path for direct construction in focused tests.
        }
    };

    boolean tryAcquire(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl);

    /**
     * Extends a lease only while the same owner still holds it.  A false
     * result means the caller must stop work because another owner may have
     * taken the lease.
     */
    boolean renew(String leaseKey, String ownerToken, LocalDateTime now, Duration ttl);

    void release(String leaseKey, String ownerToken);
}

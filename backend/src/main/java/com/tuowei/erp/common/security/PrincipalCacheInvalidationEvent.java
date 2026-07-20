package com.tuowei.erp.common.security;

public record PrincipalCacheInvalidationEvent(Long userId, boolean allUsers) {

    public static PrincipalCacheInvalidationEvent user(Long userId) {
        return new PrincipalCacheInvalidationEvent(userId, false);
    }

    public static PrincipalCacheInvalidationEvent all() {
        return new PrincipalCacheInvalidationEvent(null, true);
    }
}

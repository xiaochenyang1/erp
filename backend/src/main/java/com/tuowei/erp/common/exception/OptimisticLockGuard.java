package com.tuowei.erp.common.exception;

public final class OptimisticLockGuard {

    private OptimisticLockGuard() {
    }

    public static void requireUpdated(int updatedRows, String message) {
        if (updatedRows != 1) {
            throw new BusinessConflictException(message);
        }
    }
}

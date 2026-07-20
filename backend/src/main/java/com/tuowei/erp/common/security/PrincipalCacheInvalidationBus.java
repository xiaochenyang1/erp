package com.tuowei.erp.common.security;

import java.util.function.Consumer;

public interface PrincipalCacheInvalidationBus {

    void publish(PrincipalCacheInvalidationEvent event);

    void subscribe(Consumer<PrincipalCacheInvalidationEvent> listener);
}

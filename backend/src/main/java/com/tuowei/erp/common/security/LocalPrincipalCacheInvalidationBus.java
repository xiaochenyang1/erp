package com.tuowei.erp.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "erp.security.principal-cache-invalidation", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalPrincipalCacheInvalidationBus implements PrincipalCacheInvalidationBus {

    private final List<Consumer<PrincipalCacheInvalidationEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(PrincipalCacheInvalidationEvent event) {
        listeners.forEach(listener -> listener.accept(event));
    }

    @Override
    public void subscribe(Consumer<PrincipalCacheInvalidationEvent> listener) {
        listeners.add(listener);
    }
}

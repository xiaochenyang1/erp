package com.tuowei.erp.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

public interface CacheService {

    CacheService NOOP = new CacheService() {
        @Override
        public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
            if (loader == null) {
                throw new IllegalArgumentException("cache loader must not be null");
            }
            return loader.get();
        }

        @Override
        public void evict(String key) {
        }

        @Override
        public void evictByPrefix(String keyPrefix) {
        }
    };

    String getOrLoad(String key, Duration ttl, Supplier<String> loader);

    void evict(String key);

    void evictByPrefix(String keyPrefix);
}

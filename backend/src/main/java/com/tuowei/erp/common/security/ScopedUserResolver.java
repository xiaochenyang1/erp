package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class ScopedUserResolver {

    private final UserMapper userMapper;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public ScopedUserResolver(
            UserMapper userMapper,
            Clock clock,
            @Value("${erp.security.scoped-user-cache-ttl-seconds:60}") long ttlSeconds
    ) {
        this(userMapper, clock, Duration.ofSeconds(ttlSeconds));
    }

    ScopedUserResolver(UserMapper userMapper, Clock clock, Duration ttl) {
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = validateTtl(ttl);
    }

    public ScopedUserIds resolve(CurrentUser currentUser, DataScopeSnapshot snapshot) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return new ScopedUserIds(
                loadScopedUserIds(
                        snapshot.deptScoped(),
                        currentUser.companyId(),
                        currentUser.accountBookId(),
                        ScopeKind.DEPT,
                        currentUser.deptId(),
                        UserEntity::getDeptId
                ),
                loadScopedUserIds(
                        snapshot.postScoped(),
                        currentUser.companyId(),
                        currentUser.accountBookId(),
                        ScopeKind.POST,
                        currentUser.postId(),
                        UserEntity::getPostId
                )
        );
    }

    public void evictAll() {
        cache.clear();
    }

    private Set<Long> loadScopedUserIds(
            boolean scoped,
            Long companyId,
            Long accountBookId,
            ScopeKind scopeKind,
            Long scopeValue,
            SFunction<UserEntity, Long> scopeColumn
    ) {
        if (!scoped || companyId == null || accountBookId == null || scopeValue == null) {
            return Set.of();
        }

        CacheKey key = new CacheKey(companyId, accountBookId, scopeKind, scopeValue);
        CacheEntry entry = cache.compute(key, (ignored, current) -> {
            Instant now = clock.instant();
            if (current != null && current.expiresAt().isAfter(now)) {
                return current;
            }
            return new CacheEntry(loadActiveUserIds(companyId, accountBookId, scopeColumn, scopeValue), now.plus(ttl));
        });
        return entry.userIds();
    }

    private Set<Long> loadActiveUserIds(
            Long companyId,
            Long accountBookId,
            SFunction<UserEntity, Long> scopeColumn,
            Long scopeValue
    ) {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .select(UserEntity::getId)
                        .eq(UserEntity::getCompanyId, companyId)
                        .eq(UserEntity::getAccountBookId, accountBookId)
                        .eq(UserEntity::getDeletedFlag, 0)
                        .eq(UserEntity::getStatus, "ACTIVE")
                        .eq(scopeColumn, scopeValue)
                        .orderByAsc(UserEntity::getId))
                .stream()
                .map(UserEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Duration validateTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("scoped user cache ttl must be positive");
        }
        return ttl;
    }

    public record ScopedUserIds(Set<Long> deptUserIds, Set<Long> postUserIds) {

        public ScopedUserIds {
            deptUserIds = Set.copyOf(deptUserIds);
            postUserIds = Set.copyOf(postUserIds);
        }
    }

    private enum ScopeKind {
        DEPT,
        POST
    }

    private record CacheKey(Long companyId, Long accountBookId, ScopeKind scopeKind, Long scopeValue) {
    }

    private record CacheEntry(Set<Long> userIds, Instant expiresAt) {

        private CacheEntry {
            userIds = Set.copyOf(userIds);
        }
    }
}

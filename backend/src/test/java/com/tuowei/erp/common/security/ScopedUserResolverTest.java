package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScopedUserResolverTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-29T00:00:00Z"));

    @BeforeEach
    void initMybatisTableInfo() {
        initTableInfo(UserEntity.class);
    }

    @Test
    void resolvesDeptAndPostUsersOnceWithinTtlAndReloadsAfterEviction() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(user(101L), user(102L)))
                .thenReturn(List.of(user(201L)))
                .thenReturn(List.of(user(103L)))
                .thenReturn(List.of(user(202L)));
        ScopedUserResolver resolver = new ScopedUserResolver(userMapper, clock, Duration.ofMinutes(5));

        ScopedUserResolver.ScopedUserIds first = resolver.resolve(currentUser(), scopedByDeptAndPost());
        ScopedUserResolver.ScopedUserIds second = resolver.resolve(currentUser(), scopedByDeptAndPost());

        assertThat(second.deptUserIds()).containsExactlyInAnyOrderElementsOf(first.deptUserIds());
        assertThat(second.postUserIds()).containsExactlyInAnyOrderElementsOf(first.postUserIds());
        assertThat(first.deptUserIds()).containsExactlyInAnyOrder(101L, 102L);
        assertThat(first.postUserIds()).containsExactly(201L);
        verify(userMapper, times(2)).selectList(any());

        resolver.evictAll();
        ScopedUserResolver.ScopedUserIds reloaded = resolver.resolve(currentUser(), scopedByDeptAndPost());

        assertThat(reloaded.deptUserIds()).containsExactly(103L);
        assertThat(reloaded.postUserIds()).containsExactly(202L);
        verify(userMapper, times(4)).selectList(any());
    }

    @Test
    void skipsDatabaseWhenSnapshotDoesNotNeedDeptOrPostScope() {
        UserMapper userMapper = mock(UserMapper.class);
        ScopedUserResolver resolver = new ScopedUserResolver(userMapper, clock, Duration.ofMinutes(5));

        ScopedUserResolver.ScopedUserIds scopedUsers = resolver.resolve(currentUser(), DataScopeSnapshot.none());

        assertThat(scopedUsers.deptUserIds()).isEmpty();
        assertThat(scopedUsers.postUserIds()).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    void scopedUserQueriesIncludeCurrentAccountBook() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectList(any())).thenReturn(List.of());
        ScopedUserResolver resolver = new ScopedUserResolver(userMapper, clock, Duration.ofMinutes(5));

        resolver.resolve(currentUser(), scopedByDeptOnly());

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> wrapper = lambdaQueryWrapperCaptor();
        verify(userMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("dept_id");
    }

    @Test
    void cacheSeparatesScopedUsersByAccountBook() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(user(101L)))
                .thenReturn(List.of(user(201L)));
        ScopedUserResolver resolver = new ScopedUserResolver(userMapper, clock, Duration.ofMinutes(5));

        ScopedUserResolver.ScopedUserIds first = resolver.resolve(currentUser(1L), scopedByDeptOnly());
        ScopedUserResolver.ScopedUserIds second = resolver.resolve(currentUser(2L), scopedByDeptOnly());

        assertThat(first.deptUserIds()).containsExactly(101L);
        assertThat(second.deptUserIds()).containsExactly(201L);
        verify(userMapper, times(2)).selectList(any());
    }

    private static CurrentUser currentUser() {
        return new CurrentUser(7L, 1L, 1L, 11L, 12L, "cache-user", "Cache User");
    }

    private static CurrentUser currentUser(Long accountBookId) {
        return new CurrentUser(7L, 1L, accountBookId, 11L, 12L, "cache-user", "Cache User");
    }

    private static DataScopeSnapshot scopedByDeptAndPost() {
        return new DataScopeSnapshot(false, true, true, false, Set.of());
    }

    private static DataScopeSnapshot scopedByDeptOnly() {
        return new DataScopeSnapshot(false, true, false, false, Set.of());
    }

    private static UserEntity user(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        return user;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<UserEntity>> lambdaQueryWrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private static final class MutableClock extends Clock {

        private final Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

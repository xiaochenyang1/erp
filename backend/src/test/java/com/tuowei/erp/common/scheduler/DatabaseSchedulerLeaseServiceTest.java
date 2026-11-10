package com.tuowei.erp.common.scheduler;

import com.tuowei.erp.common.scheduler.mapper.SchedulerLeaseMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseSchedulerLeaseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 10, 0);

    @Test
    void acquisitionUsesAtomicExpiryPredicateAndCommitsAsRequiresNew() throws Exception {
        SchedulerLeaseMapper mapper = mock(SchedulerLeaseMapper.class);
        when(mapper.tryAcquire("LEASE", "owner", NOW, NOW.plusSeconds(90))).thenReturn(1);
        DatabaseSchedulerLeaseService service = new DatabaseSchedulerLeaseService(
                mapper, Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.tryAcquire("LEASE", "owner", NOW, Duration.ofSeconds(90))).isTrue();
        verify(mapper).tryAcquire("LEASE", "owner", NOW, NOW.plusSeconds(90));

        Method method = DatabaseSchedulerLeaseService.class.getDeclaredMethod(
                "tryAcquire", String.class, String.class, LocalDateTime.class, Duration.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void releaseUsesDatabaseClockAndRequiresOwnerToken() throws Exception {
        SchedulerLeaseMapper mapper = mock(SchedulerLeaseMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        DatabaseSchedulerLeaseService service = new DatabaseSchedulerLeaseService(mapper, clock);

        service.release("LEASE", "owner");

        verify(mapper).release(eq("LEASE"), eq("owner"), eq(NOW));
        Method method = DatabaseSchedulerLeaseService.class.getDeclaredMethod(
                "release", String.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void renewalExtendsOnlyTheCurrentUnexpiredLeaseAndCommitsAsRequiresNew() throws Exception {
        SchedulerLeaseMapper mapper = mock(SchedulerLeaseMapper.class);
        when(mapper.renew("LEASE", "owner", NOW, NOW.plusSeconds(90))).thenReturn(1);
        DatabaseSchedulerLeaseService service = new DatabaseSchedulerLeaseService(
                mapper, Clock.fixed(Instant.parse("2026-09-04T02:00:00Z"), ZoneId.of("Asia/Shanghai")));

        assertThat(service.renew("LEASE", "owner", NOW, Duration.ofSeconds(90))).isTrue();
        verify(mapper).renew("LEASE", "owner", NOW, NOW.plusSeconds(90));

        Method method = DatabaseSchedulerLeaseService.class.getDeclaredMethod(
                "renew", String.class, String.class, LocalDateTime.class, Duration.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);

        Method mapperMethod = SchedulerLeaseMapper.class.getDeclaredMethod(
                "renew", String.class, String.class, LocalDateTime.class, LocalDateTime.class);
        String sql = String.join(" ", mapperMethod.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
        assertThat(sql)
                .contains("owner_token = #{ownerToken}")
                .contains("expires_at > #{now}");
    }

    @Test
    void rejectsNonPositiveTtlAndBlankOwner() {
        DatabaseSchedulerLeaseService service = new DatabaseSchedulerLeaseService(
                mock(SchedulerLeaseMapper.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.tryAcquire("LEASE", "owner", NOW, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.tryAcquire("LEASE", " ", NOW, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

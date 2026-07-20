package com.tuowei.erp.system.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.DatabaseUserDetailsService;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.system.auth.mapper.RefreshTokenMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final RefreshTokenMapper refreshTokenMapper = mock(RefreshTokenMapper.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-08T10:15:30Z"), ZoneOffset.UTC);

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(RefreshTokenEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), RefreshTokenEntity.class.getName());
        assistant.setCurrentNamespace(RefreshTokenEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, RefreshTokenEntity.class);
    }

    @Test
    void revokeAllForUserScopesRefreshTokensToPrincipalTenantAndAccountBook() {
        lenient().when(userDetailsService.loadPrincipalByUserId(1001L))
                .thenReturn(new ErpPrincipal(1001L, 2001L, 3001L, "tenant-user", "Tenant User", "pwd", Set.of()));

        service().revokeAllForUser(1001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper<RefreshTokenEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(refreshTokenMapper).update(org.mockito.ArgumentMatchers.isNull(), wrapperCaptor.capture());

        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("user_id")
                .contains("company_id")
                .contains("account_book_id")
                .contains("status");
    }

    @Test
    void issueSanitizesUserAgentBeforePersistingRefreshToken() {
        when(jwtTokenService.refreshTokenTtlSeconds()).thenReturn(7200L);
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "ERP\r\nClient\t/1.0");

        service().issue(new ErpPrincipal(1001L, 2001L, 3001L, "tenant-user", "Tenant User", "pwd", Set.of()), request);

        ArgumentCaptor<RefreshTokenEntity> entityCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserAgent()).isEqualTo("ERP Client /1.0");
    }

    private RefreshTokenService service() {
        return new RefreshTokenService(
                refreshTokenMapper,
                jwtTokenService,
                userDetailsService,
                clock,
                clientIpResolver
        );
    }
}

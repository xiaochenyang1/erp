package com.tuowei.erp.system.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.auth.mapper.RefreshTokenMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import com.tuowei.erp.system.auth.service.UserSessionService;
import com.tuowei.erp.system.auth.web.UserSessionPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9701L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 16, 0)
    );
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T08:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(RefreshTokenEntity.class);
        initTableInfo(UserEntity.class);
    }

    @Test
    void listScopesSessionsAndLoadedUsersByCompanyAndAccountBook() {
        stubAudit();
        when(refreshTokenMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<RefreshTokenEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(activeToken(AUDIT.accountBookId())));
            return page;
        });
        when(userMapper.selectList(any())).thenReturn(List.of());

        service().list(new UserSessionPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<RefreshTokenEntity>> sessionWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(refreshTokenMapper).selectPage(any(), sessionWrapperCaptor.capture());
        assertTenantScoped(sessionWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> userWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectList(userWrapperCaptor.capture());
        assertTenantScoped(userWrapperCaptor.getValue());
    }

    @Test
    void usernameFilterScopesUserLookupByCompanyAndAccountBook() {
        stubAudit();
        UserSessionPageQuery query = new UserSessionPageQuery();
        query.setUsername("demo");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(refreshTokenMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void revokeSessionRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(refreshTokenMapper.selectById(9001L)).thenReturn(activeToken(9999L));

        assertThatThrownBy(() -> service().revokeSession(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会话不存在");
    }

    @Test
    void revokeAllForUserRejectsDifferentAccountBookWithinSameCompany() {
        stubAudit();
        UserEntity user = activeUser(9999L);
        when(userMapper.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> service().revokeAllForUser(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void revokeAllForUserScopesUpdateByCompanyAndAccountBook() {
        stubAudit();
        when(userMapper.selectById(1001L)).thenReturn(activeUser(AUDIT.accountBookId()));
        when(refreshTokenMapper.update(eq(null), any())).thenReturn(1);

        service().revokeAllForUser(1001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaUpdateWrapper<RefreshTokenEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(refreshTokenMapper).update(eq(null), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void assertTenantScoped(com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private RefreshTokenEntity activeToken(Long accountBookId) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(9001L);
        entity.setUserId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setStatus("ACTIVE");
        entity.setIssuedAt(AUDIT.now());
        entity.setExpiresAt(AUDIT.now().plusDays(1));
        return entity;
    }

    private UserEntity activeUser(Long accountBookId) {
        UserEntity entity = new UserEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setUsername("demo");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserSessionService service() {
        return new UserSessionService(refreshTokenMapper, userMapper, auditMetadataFactory, CLOCK);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

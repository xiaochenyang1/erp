package com.tuowei.erp.system.auth.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.auth.mapper.RefreshTokenMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Write side of user sessions: revoke a single session or all active sessions of a user with tenant guards and optimistic locking. */
@Service
public class UserSessionCommandService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";

    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final Clock clock;

    public UserSessionCommandService(
            RefreshTokenMapper refreshTokenMapper,
            UserMapper userMapper,
            AuditMetadataFactory auditMetadataFactory,
            Clock clock
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.userMapper = userMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.clock = clock;
    }

    @Transactional
    public void revokeSession(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        RefreshTokenEntity entity = refreshTokenMapper.selectById(id);
        if (entity == null
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        entity.setStatus(STATUS_REVOKED);
        entity.setRevokedAt(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(refreshTokenMapper.updateById(entity), "会话已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        AuditMetadata audit = auditMetadataFactory.current();
        UserEntity user = userMapper.selectById(userId);
        if (user == null
                || user.getDeletedFlag() == null
                || user.getDeletedFlag() != 0
                || !audit.companyId().equals(user.getCompanyId())
                || !audit.accountBookId().equals(user.getAccountBookId())) {
            throw new IllegalArgumentException("用户不存在");
        }
        refreshTokenMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getCompanyId, audit.companyId())
                .eq(RefreshTokenEntity::getAccountBookId, audit.accountBookId())
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getStatus, STATUS_ACTIVE)
                .set(RefreshTokenEntity::getStatus, STATUS_REVOKED)
                .set(RefreshTokenEntity::getRevokedAt, audit.now())
                .set(RefreshTokenEntity::getUpdatedBy, audit.userId())
                .set(RefreshTokenEntity::getUpdatedTime, audit.now()));
    }
}

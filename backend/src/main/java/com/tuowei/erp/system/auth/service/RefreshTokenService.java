package com.tuowei.erp.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.DatabaseUserDetailsService;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.common.web.ClientIpResolver;
import com.tuowei.erp.common.web.HeaderValueSanitizer;
import com.tuowei.erp.system.auth.mapper.RefreshTokenMapper;
import com.tuowei.erp.system.auth.model.RefreshTokenEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtTokenService jwtTokenService;
    private final DatabaseUserDetailsService userDetailsService;
    private final Clock clock;
    private final ClientIpResolver clientIpResolver;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenMapper refreshTokenMapper,
            JwtTokenService jwtTokenService,
            DatabaseUserDetailsService userDetailsService,
            Clock clock,
            ClientIpResolver clientIpResolver
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
        this.clock = clock;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional
    public IssuedRefreshToken issue(ErpPrincipal principal, HttpServletRequest request) {
        String token = generateToken();
        String tokenHash = hashToken(token);
        LocalDateTime now = LocalDateTime.now(clock);

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(principal.userId());
        entity.setCompanyId(principal.companyId());
        entity.setAccountBookId(principal.accountBookId());
        entity.setTokenHash(tokenHash);
        entity.setStatus(STATUS_ACTIVE);
        entity.setIssuedAt(now);
        entity.setExpiresAt(now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds()));
        entity.setLoginIp(clientIpResolver.resolve(request));
        entity.setUserAgent(resolveUserAgent(request));
        entity.setLastUsedAt(now);
        entity.setCreatedBy(principal.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(principal.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        refreshTokenMapper.insert(entity);

        return new IssuedRefreshToken(token, jwtTokenService.refreshTokenTtlSeconds());
    }

    @Transactional
    public TokenRefreshResult rotate(String refreshToken) {
        RefreshTokenEntity existing = requireActiveToken(refreshToken);
        ErpPrincipal principal = userDetailsService.loadPrincipalByUserId(existing.getUserId());
        String newRefreshToken = generateToken();
        String newRefreshTokenHash = hashToken(newRefreshToken);
        LocalDateTime now = LocalDateTime.now(clock);

        existing.setStatus(STATUS_REVOKED);
        existing.setRevokedAt(now);
        existing.setReplacedByTokenHash(newRefreshTokenHash);
        existing.setUpdatedBy(principal.userId());
        existing.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(refreshTokenMapper.updateById(existing), "refresh token已失效");

        RefreshTokenEntity replacement = new RefreshTokenEntity();
        replacement.setUserId(principal.userId());
        replacement.setCompanyId(principal.companyId());
        replacement.setAccountBookId(principal.accountBookId());
        replacement.setTokenHash(newRefreshTokenHash);
        replacement.setStatus(STATUS_ACTIVE);
        replacement.setIssuedAt(now);
        replacement.setExpiresAt(now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds()));
        replacement.setLoginIp(existing.getLoginIp());
        replacement.setUserAgent(existing.getUserAgent());
        replacement.setLastUsedAt(now);
        replacement.setCreatedBy(principal.userId());
        replacement.setCreatedTime(now);
        replacement.setUpdatedBy(principal.userId());
        replacement.setUpdatedTime(now);
        replacement.setVersion(0);
        refreshTokenMapper.insert(replacement);

        return new TokenRefreshResult(
                principal,
                jwtTokenService.createAccessToken(principal),
                newRefreshToken,
                jwtTokenService.accessTokenTtlSeconds(),
                jwtTokenService.refreshTokenTtlSeconds()
        );
    }

    @Transactional
    public void revoke(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenMapper.update(null, new LambdaUpdateWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, tokenHash)
                .eq(RefreshTokenEntity::getStatus, STATUS_ACTIVE)
                .set(RefreshTokenEntity::getStatus, STATUS_REVOKED)
                .set(RefreshTokenEntity::getRevokedAt, now)
                .set(RefreshTokenEntity::getUpdatedTime, now));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        ErpPrincipal principal = userDetailsService.loadPrincipalByUserId(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenMapper.update(null, new LambdaUpdateWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getCompanyId, principal.companyId())
                .eq(RefreshTokenEntity::getAccountBookId, principal.accountBookId())
                .eq(RefreshTokenEntity::getStatus, STATUS_ACTIVE)
                .set(RefreshTokenEntity::getStatus, STATUS_REVOKED)
                .set(RefreshTokenEntity::getRevokedAt, now)
                .set(RefreshTokenEntity::getUpdatedTime, now));
    }

    private RefreshTokenEntity requireActiveToken(String refreshToken) {
        RefreshTokenEntity entity = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, hashToken(refreshToken)));
        LocalDateTime now = LocalDateTime.now(clock);
        if (entity == null
                || !STATUS_ACTIVE.equals(entity.getStatus())
                || entity.getExpiresAt() == null
                || !entity.getExpiresAt().isAfter(now)) {
            throw new BadCredentialsException("refresh token无效");
        }
        return entity;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return URL_ENCODER.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("refresh token哈希失败", ex);
        }
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return HeaderValueSanitizer.sanitize(request.getHeader("User-Agent"), 512);
    }

    public record IssuedRefreshToken(String token, long expiresIn) {
    }

    public record TokenRefreshResult(
            ErpPrincipal principal,
            String accessToken,
            String refreshToken,
            long expiresIn,
            long refreshExpiresIn
    ) {
    }
}

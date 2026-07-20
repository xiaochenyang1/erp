package com.tuowei.erp.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.SecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(ObjectMapper objectMapper, SecurityProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        validateJwtSecret();
    }

    private void validateJwtSecret() {
        byte[] secretBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret必须至少32字节(256位)，当前: " + secretBytes.length + "字节");
        }
    }

    public String createAccessToken(ErpPrincipal principal) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.jwt().accessTokenTtlSeconds());
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "sub", principal.username(),
                "uid", principal.userId(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        );
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return unsigned + "." + sign(unsigned);
    }

    public JwtClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT格式无效");
        }

        Map<String, Object> header = decodeJson(parts[0], "JWT头部无效");
        if (!"HS256".equals(header.get("alg"))) {
            throw new IllegalArgumentException("JWT算法无效");
        }

        String unsigned = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(
                sign(unsigned).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("JWT签名无效");
        }

        Map<String, Object> payload = decodeJson(parts[1], "JWT载荷无效");
        Instant expiresAt = Instant.ofEpochSecond(requiredNumber(payload, "exp").longValue());
        if (!expiresAt.isAfter(Instant.now(clock))) {
            throw new IllegalArgumentException("JWT已过期");
        }
        return new JwtClaims(
                requiredNumber(payload, "uid").longValue(),
                requiredString(payload, "sub"),
                expiresAt
        );
    }

    public long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtlSeconds();
    }

    public long refreshTokenTtlSeconds() {
        return properties.jwt().refreshTokenTtlSeconds();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT编码失败", ex);
        }
    }

    private Map<String, Object> decodeJson(String value, String message) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(value), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private Number requiredNumber(Map<String, Object> payload, String claim) {
        Object value = payload.get(claim);
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("JWT载荷无效");
            }
        }
        throw new IllegalArgumentException("JWT载荷无效");
    }

    private String requiredString(Map<String, Object> payload, String claim) {
        Object value = payload.get(claim);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("JWT载荷无效");
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT签名失败", ex);
        }
    }

    public record JwtClaims(Long userId, String username, Instant expiresAt) {
    }
}

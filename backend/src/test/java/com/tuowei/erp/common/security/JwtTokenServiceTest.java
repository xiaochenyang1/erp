package com.tuowei.erp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.tuowei.erp.common.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void parseRejectsTokenWithTrailingSegmentSeparator() {
        JwtTokenService service = service();
        String token = service.createAccessToken(principal());

        assertThatThrownBy(() -> service.parse(token + "."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT格式无效");
    }

    @Test
    void parseAcceptsStringNumberClaimsProducedByLongToStringMapper() {
        JwtTokenService service = service(longToStringObjectMapper());
        String token = service.createAccessToken(principal());

        assertThat(service.parse(token).userId()).isEqualTo(9001L);
    }

    private JwtTokenService service() {
        return service(new ObjectMapper());
    }

    private JwtTokenService service(ObjectMapper objectMapper) {
        return new JwtTokenService(
                objectMapper,
                new SecurityProperties(new SecurityProperties.Jwt(
                        "jwt-secret-jwt-secret-jwt-secret-jwt-secret",
                        7200,
                        1209600
                )),
                clock
        );
    }

    private ObjectMapper longToStringObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(Long.TYPE, ToStringSerializer.instance));
        return objectMapper;
    }

    private ErpPrincipal principal() {
        return new ErpPrincipal(
                9001L,
                1L,
                1L,
                "alice",
                "Alice",
                "{noop}password",
                Set.of("system:user:view")
        );
    }
}

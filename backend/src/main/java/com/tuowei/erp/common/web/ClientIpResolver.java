package com.tuowei.erp.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 64;
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";
    private static final Pattern IPV4_SEGMENT = Pattern.compile("\\d{1,3}");
    private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");

    private final List<TrustedProxy> trustedProxies;

    public ClientIpResolver(@Value("${erp.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = parseTrustedProxies(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remoteAddress = normalize(request.getRemoteAddr());
        if (isTrustedProxy(remoteAddress)) {
            String forwardedFor = firstForwardedFor(request.getHeader(FORWARDED_FOR));
            if (StringUtils.hasText(forwardedFor)) {
                return forwardedFor;
            }
            String realIp = normalizeHeaderAddress(request.getHeader(REAL_IP));
            if (StringUtils.hasText(realIp)) {
                return realIp;
            }
        }
        return truncate(remoteAddress);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (!StringUtils.hasText(remoteAddress) || trustedProxies.isEmpty()) {
            return false;
        }
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(remoteAddress));
    }

    private String firstForwardedFor(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (String segment : value.split(",")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            return normalizeHeaderAddress(segment);
        }
        return null;
    }

    private List<TrustedProxy> parseTrustedProxies(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .map(TrustedProxy::parse)
                .toList();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeHeaderAddress(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized) || hasControlCharacter(normalized) || !isIpLiteral(normalized)) {
            return null;
        }
        return truncate(normalized);
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private boolean isIpLiteral(String value) {
        return value.contains(":") ? isIpv6Literal(value) : isIpv4Literal(value);
    }

    private boolean isIpv4Literal(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        return Arrays.stream(segments).allMatch(this::isIpv4Segment);
    }

    private boolean isIpv4Segment(String segment) {
        if (!IPV4_SEGMENT.matcher(segment).matches()) {
            return false;
        }
        int value = Integer.parseInt(segment);
        return value >= 0 && value <= 255;
    }

    private boolean isIpv6Literal(String value) {
        if (!IPV6_LITERAL_CHARS.matcher(value).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address || address instanceof Inet4Address;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
    }

    private record TrustedProxy(InetAddress networkAddress, int prefixLength) {

        private static TrustedProxy parse(String value) {
            String[] parts = value.split("/", -1);
            try {
                if (!StringUtils.hasText(value) || parts.length > 2 || !StringUtils.hasText(parts[0])
                        || (parts.length == 2 && !StringUtils.hasText(parts[1]))) {
                    throw new IllegalArgumentException("trusted proxy配置不能为空");
                }
                InetAddress address = InetAddress.getByName(parts[0]);
                int maxPrefix = address.getAddress().length * 8;
                int prefix = parts.length == 1 ? maxPrefix : Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("trusted proxy配置无效: " + value);
                }
                return new TrustedProxy(address, prefix);
            } catch (Exception ex) {
                throw new IllegalArgumentException("trusted proxy配置无效: " + value, ex);
            }
        }

        private boolean matches(String candidateAddress) {
            try {
                byte[] network = networkAddress.getAddress();
                byte[] candidate = InetAddress.getByName(candidateAddress).getAddress();
                if (network.length != candidate.length) {
                    return false;
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;
                for (int index = 0; index < fullBytes; index++) {
                    if (network[index] != candidate[index]) {
                        return false;
                    }
                }
                if (remainingBits == 0) {
                    return true;
                }
                int mask = 0xFF << (8 - remainingBits);
                return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}

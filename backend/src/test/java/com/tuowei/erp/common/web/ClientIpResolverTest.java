package com.tuowei.erp.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenRemoteAddressIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = request("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.25");
        request.addHeader("X-Real-IP", "198.51.100.26");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void usesFirstForwardedForAddressWhenRemoteAddressIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = request("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.25, 10.1.2.3");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void usesRealIpWhenTrustedProxyDoesNotSendForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", " ");
        request.addHeader("X-Real-IP", "198.51.100.26");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.26");
    }

    @Test
    void ignoresInvalidForwardedForAndFallsBackToRealIp() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "bad\r\n198.51.100.25");
        request.addHeader("X-Real-IP", "198.51.100.26");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.26");
    }

    @Test
    void ignoresForwardedForHeaderWhenFirstAddressIsInvalid() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip, 198.51.100.25");
        request.addHeader("X-Real-IP", "198.51.100.26");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.26");
    }

    @Test
    void ignoresInvalidRealIpAndFallsBackToRemoteAddress() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", " ");
        request.addHeader("X-Real-IP", "not-an-ip");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void supportsIpv6CidrTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8::/32");
        MockHttpServletRequest request = request("2001:db8::10");
        request.addHeader("X-Forwarded-For", "2001:db8:abcd::20");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8:abcd::20");
    }

    @Test
    void truncatesResolvedAddressToDatabaseColumnLength() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = request("a".repeat(80));

        assertThat(resolver.resolve(request)).hasSize(64);
    }

    @Test
    void rejectsTrustedProxyConfigWithTrailingEmptyEntry() {
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/8,"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted proxy配置无效");
    }

    @Test
    void rejectsTrustedProxyConfigWithMissingNetworkAddress() {
        assertThatThrownBy(() -> new ClientIpResolver("/8"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted proxy配置无效");
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}

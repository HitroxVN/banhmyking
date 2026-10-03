package com.banhmyking.banhmyking.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void usesRightmostForwardedAddressWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", " 1.2.3.4 , 10.0.0.1");
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToRemoteAddrWhenTrustedHeaderIsBlankOrOnlySeparators() {
        for (String header : new String[] {"", "   ", ",", " , ,", ","}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.1.1.1");
            request.addHeader("X-Forwarded-For", header);
            assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("10.1.1.1");
        }
    }

    @Test
    void neverReturnsNullAndCapsLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("unknown");
        request.setRemoteAddr("x".repeat(60));
        assertThat(new ClientIpResolver(false).resolve(request)).hasSize(45);
    }
}

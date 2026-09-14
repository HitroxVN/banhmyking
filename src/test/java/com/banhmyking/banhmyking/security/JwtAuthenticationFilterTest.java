package com.banhmyking.banhmyking.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * token chữ ký hợp lệ nhưng subject không phải số → Long.valueOf nổ
 * NumberFormatException. Filter phải nuốt nó (clear context, đi tiếp chain
 * để EntryPoint trả 401), không được biến thành 500.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtUserDetailsService jwtUserDetailsService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, jwtUserDetailsService);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.token.value");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void numericSubjectButUnknownUser_clearsContextAndContinues() throws Exception {
        when(jwtTokenProvider.extractUserId(anyString())).thenReturn(999L);
        when(jwtUserDetailsService.loadById(999L))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("x"));

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void garbageSubject_numberFormatException_isSwallowedNotPropagated() throws Exception {
        // Chữ ký hợp lệ (parseClaims OK) nhưng subject = "not-a-number" → Long.valueOf nổ NFE
        when(jwtTokenProvider.extractUserId(anyString()))
                .thenThrow(new NumberFormatException("For input string: \"not-a-number\""));

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidSignature_jwtException_isSwallowed() throws Exception {
        when(jwtTokenProvider.extractUserId(anyString()))
                .thenThrow(new JwtException("bad signature"));

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

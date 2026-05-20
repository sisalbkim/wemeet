package com.kopo.wemeet.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpsRedirectFilterTest {

    @Test
    void redirectsHttpWwwRequestToCanonicalHttpsUrl() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter(true, "wemeet.ai.kr");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setServerName("www.wemeet.ai.kr");
        request.setServerPort(80);
        request.setQueryString("next=%2Fprofile");
        request.addHeader("Host", "www.wemeet.ai.kr");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(302, response.getStatus());
        assertEquals("https://wemeet.ai.kr/login?next=%2Fprofile", response.getRedirectedUrl());
    }

    @Test
    void redirectsForwardedHttpsRequestToCanonicalHost() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter(true, "wemeet.ai.kr");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "www.wemeet.ai.kr");
        request.addHeader("X-Forwarded-Port", "443");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(302, response.getStatus());
        assertEquals("https://wemeet.ai.kr/", response.getRedirectedUrl());
    }

    @Test
    void skipsRedirectForCanonicalHttpsRequest() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter(true, "wemeet.ai.kr");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");
        request.setSecure(true);
        request.setServerName("wemeet.ai.kr");
        request.setServerPort(443);
        request.addHeader("Host", "wemeet.ai.kr");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(response.getRedirectedUrl());
        assertEquals(request, filterChain.getRequest());
    }

    @Test
    void skipsRedirectForLocalhostRequest() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter(true, "wemeet.ai.kr");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setServerName("localhost");
        request.setServerPort(8082);
        request.addHeader("Host", "localhost:8082");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertNull(response.getRedirectedUrl());
        assertEquals(request, filterChain.getRequest());
    }
}

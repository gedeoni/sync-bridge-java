package com.syncbridge.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthFilter implements Filter {

    private final String configuredToken;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthFilter(String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String token = req.getHeader("x-auth-token");
        if (token == null || configuredToken == null || !configuredToken.equals(token)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> body = new HashMap<>();
            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
            body.put("message", "Access Denied");
            res.setContentType("application/json");
            mapper.writeValue(res.getOutputStream(), body);
            return;
        }
        chain.doFilter(request, response);
    }
}

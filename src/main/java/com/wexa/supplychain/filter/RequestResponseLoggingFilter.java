package com.wexa.supplychain.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wexa.supplychain.utils.LogJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private final ObjectMapper objectMapper;

    // Cap cache at 64KB for safety
    private static final int MAX_CACHE_LIMIT = 64 * 1024;

    public RequestResponseLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, MAX_CACHE_LIMIT);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            logRequest(requestWrapper);
            logResponse(responseWrapper, duration);

            // CRITICAL: Ensure response is passed back to client
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String contentType = request.getContentType();

        // 1. Build consolidated request metadata + headers Map
        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("method", request.getMethod());
        requestDetails.put("uri", request.getRequestURI() + queryString);
        requestDetails.put("headers", getRequestHeadersMap(request));

        // 2. Extract Body
        Object bodyToLog;
        if (isBinaryOrMultipart(contentType)) {
            bodyToLog = "[File Upload / Binary Content - " + (contentType != null ? contentType : "Unknown") + "]";
        } else {
            byte[] buf = request.getContentAsByteArray();
            if (buf.length > 0) {
                String bodyStr = new String(buf, StandardCharsets.UTF_8);
                try {
                    bodyToLog = objectMapper.readValue(bodyStr, Object.class);
                } catch (Exception e) {
                    bodyToLog = bodyStr;
                }
            } else {
                bodyToLog = "Empty";
            }
        }

        // 3. Log single combined JSON for metadata & headers
        log.info("===> [HTTP REQUEST START] {}", LogJson.of(requestDetails));
        log.info("Request Body    : {}", LogJson.of(bodyToLog));
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        String contentType = response.getContentType();

        // 1. Extract Body
        Object bodyToLog;
        if (isBinaryOrMultipart(contentType)) {
            bodyToLog = "[Binary Response Content - " + (contentType != null ? contentType : "Unknown") + "]";
        } else {
            byte[] buf = response.getContentAsByteArray();
            if (buf.length > 0) {
                String bodyStr = new String(buf, StandardCharsets.UTF_8);
                try {
                    bodyToLog = objectMapper.readValue(bodyStr, Object.class);
                } catch (Exception e) {
                    bodyToLog = bodyStr;
                }
            } else {
                bodyToLog = "Empty";
            }
        }

        // Truncate response body if it exceeds 2000 characters
        String bodyJsonStr = LogJson.of(bodyToLog);
        if (bodyJsonStr != null && bodyJsonStr.length() > 2000) {
            bodyToLog = bodyJsonStr.substring(0, 2000) + "... [TRUNCATED]";
        }

        // 2. Build consolidated response metadata + headers + body Map
        Map<String, Object> responseDetails = new HashMap<>();
        responseDetails.put("status", response.getStatus());
        responseDetails.put("durationMs", duration);
        responseDetails.put("headers", getResponseHeadersMap(response));
        responseDetails.put("body", bodyToLog);

        // 3. Log single combined JSON
        log.info("<=== [HTTP RESPONSE] {}", LogJson.of(responseDetails));
    }

    private Map<String, String> getRequestHeadersMap(HttpServletRequest request) {
        Map<String, String> headerMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headerMap.put(headerName, request.getHeader(headerName));
        }
        return headerMap;
    }

    private Map<String, String> getResponseHeadersMap(HttpServletResponse response) {
        Map<String, String> headerMap = new HashMap<>();
        for (String headerName : response.getHeaderNames()) {
            headerMap.put(headerName, response.getHeader(headerName));
        }
        return headerMap;
    }

    private boolean isBinaryOrMultipart(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerContent = contentType.toLowerCase();
        return lowerContent.contains(MediaType.MULTIPART_FORM_DATA_VALUE) ||
               lowerContent.contains("image/") ||
               lowerContent.contains("video/") ||
               lowerContent.contains("audio/") ||
               lowerContent.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE) ||
               lowerContent.contains("pdf");
    }
}
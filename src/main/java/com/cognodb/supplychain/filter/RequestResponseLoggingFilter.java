package com.cognodb.supplychain.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.cognodb.supplychain.utils.LogJson;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
	private final ObjectMapper objectMapper;

	private static final int MAX_CACHE_LIMIT = 64 * 1024;

	public RequestResponseLoggingFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		boolean isSse = isSseRequest(request);

		// Handle SSE streams without wrappers so the connection stays open
		if (isSse) {
			long startTime = System.currentTimeMillis();
			logRequestHeaderInfo(request);
			log.info("===> [SSE CONNECTION ESTABLISHED] URI: {}", request.getRequestURI());

			try {
				filterChain.doFilter(request, response);
			} finally {
				long duration = System.currentTimeMillis() - startTime;
				log.info("<=== [SSE CONNECTION CLOSED] URI: {} | Duration: {}ms | Status: {}", request.getRequestURI(),
						duration, response.getStatus());
			}
			return;
		}

		// Standard REST API Flow (with caching wrappers)
		ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, MAX_CACHE_LIMIT);
		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

		long startTime = System.currentTimeMillis();

		logRequestHeaderInfo(requestWrapper);

		try {
			filterChain.doFilter(requestWrapper, responseWrapper);
		} finally {
			long duration = System.currentTimeMillis() - startTime;

			logRequestBodyInfo(requestWrapper);
			logResponse(requestWrapper, responseWrapper, duration);

			responseWrapper.copyBodyToResponse();
		}
	}

	private boolean isSseRequest(HttpServletRequest request) {
		String acceptHeader = request.getHeader("Accept");
		return request.getRequestURI().contains("/subscribe/")
				|| (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE));
	}

	private void logRequestHeaderInfo(HttpServletRequest request) {
		String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";

		Map<String, Object> requestDetails = new HashMap<>();
		requestDetails.put("method", request.getMethod());
		requestDetails.put("uri", request.getRequestURI() + queryString);
		requestDetails.put("headers", getRequestHeadersMap(request));

		log.info("===> [HTTP REQUEST START] {}", LogJson.of(requestDetails));
	}

	private void logRequestBodyInfo(ContentCachingRequestWrapper request) {
		String contentType = request.getContentType();
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

		log.info("Request Body    : {}", LogJson.of(bodyToLog));
	}

	private void logResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
			long duration) {
		String contentType = response.getContentType();

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

		String bodyJsonStr = LogJson.of(bodyToLog);
		if (bodyJsonStr != null && bodyJsonStr.length() > 2000) {
			bodyToLog = bodyJsonStr.substring(0, 2000) + "... [TRUNCATED]";
		}

		String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";

		Map<String, Object> responseDetails = new HashMap<>();
		responseDetails.put("method", request.getMethod());
		responseDetails.put("uri", request.getRequestURI() + queryString);
		responseDetails.put("status", response.getStatus());
		responseDetails.put("durationMs", duration);
		responseDetails.put("headers", getResponseHeadersMap(response));
		responseDetails.put("body", bodyToLog);

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
		return lowerContent.contains(MediaType.MULTIPART_FORM_DATA_VALUE) || lowerContent.contains("image/")
				|| lowerContent.contains("video/") || lowerContent.contains("audio/")
				|| lowerContent.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE) || lowerContent.contains("pdf");
	}
}
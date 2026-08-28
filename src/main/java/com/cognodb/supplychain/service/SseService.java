package com.cognodb.supplychain.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cognodb.supplychain.dto.BatchUploadResponse;

@Service
public class SseService {

	private static final Logger log = LoggerFactory.getLogger(SseService.class);
	// Concurrent storage for active user SSE emitters
	private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter subscribe(String userId) {
		// Timeout set to 30 minutes
		SseEmitter emitter = new SseEmitter(1800000L);

		emitters.put(userId, emitter);

		emitter.onCompletion(() -> emitters.remove(userId));
		emitter.onTimeout(() -> emitters.remove(userId));
		emitter.onError((e) -> emitters.remove(userId));

		try {
			emitter.send(SseEmitter.event().name("INIT").data("Connected to notification channel for user: " + userId));
		} catch (IOException e) {
			emitters.remove(userId);
		}

		return emitter;
	}

	public void sendUploadResult(String userId, BatchUploadResponse result) {
		SseEmitter emitter = emitters.get(userId);
		if (emitter != null) {
			try {
				emitter.send(SseEmitter.event().name("UPLOAD_COMPLETE").data(result));
				// Complete emitter after result transmission so subscription closes
				emitter.complete();
			} catch (IOException e) {
				log.error("Failed to push SSE event to user {}", userId, e);
			} finally {
				emitters.remove(userId);
			}
		}
	}
}
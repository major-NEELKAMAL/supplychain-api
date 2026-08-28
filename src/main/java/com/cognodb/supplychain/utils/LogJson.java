package com.cognodb.supplychain.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

@Component
public class LogJson {

	private static ObjectMapper mapper;

	private final ObjectMapper objectMapper;

	// Keywords that should be masked (case-insensitive)
	private static final Set<String> SENSITIVE_KEYWORDS = new HashSet<>(Arrays.asList("password", "pwd", "pass", "pin",
			"otp", "secret", "token", "accessToken", "refreshToken", "authorization", "auth", "key", "credential"));

	public LogJson(@Qualifier("objectMapper") ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void init() {
		mapper = this.objectMapper;
	}

	/**
	 * Returns pretty-printed JSON starting on a new line. Sensitive fields are
	 * automatically masked.
	 */
	public static String of(Object obj) {
		if (obj == null) {
			return "null";
		}
		if (mapper == null) {
			return String.valueOf(obj);
		}

		try {
			JsonNode node;
			if (obj instanceof String) {
				String str = ((String) obj).trim();
				if ((str.startsWith("{") && str.endsWith("}")) || (str.startsWith("[") && str.endsWith("]"))) {
					node = mapper.readTree(str);
				} else {
					return (String) obj;
				}
			} else {
				node = mapper.valueToTree(obj);
			}

			maskSensitiveFields(node);
			return "\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (Exception e) {
			return String.valueOf(obj);
		}
	}

	private static void maskSensitiveFields(JsonNode node) {
		if (node == null || !node.isObject()) {
			return;
		}

		ObjectNode objectNode = (ObjectNode) node;

		objectNode.fieldNames().forEachRemaining(fieldName -> {
			JsonNode child = objectNode.get(fieldName);

			if (isSensitive(fieldName)) {
				objectNode.put(fieldName, "****");
			} else if (child.isObject()) {
				maskSensitiveFields(child);
			} else if (child.isArray()) {
				child.forEach(LogJson::maskSensitiveFields);
			}
		});
	}

	private static boolean isSensitive(String fieldName) {
		String lower = fieldName.toLowerCase();
		return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
	}
}

package com.cognodb.supplychain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
	private String message;
	private int code;
	private boolean success;
	private Object data;

	public ApiResponse(String message, int code, boolean success) {
		this.message = message;
		this.code = code;
		this.success = success;
	}
}
package com.cognodb.supplychain.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.cognodb.supplychain.dto.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse> handleBadRequest(IllegalArgumentException ex) {
		ApiResponse response = new ApiResponse();
		response.setMessage(ex.getMessage());
		response.setCode(HttpStatus.BAD_REQUEST.value());
		response.setSuccess(false);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse> handleGenericException(Exception ex) {
		ApiResponse response = new ApiResponse();
		response.setMessage("An unexpected error occurred: " + ex.getMessage());
		response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
		response.setSuccess(false);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(
				Map.of("error", "File upload failed", "message", "File size exceeds the maximum configured limit."));
	}
}
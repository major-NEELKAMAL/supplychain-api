package com.cognodb.supplychain.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchUploadResponse extends ApiResponse {

	private int totalRows;
	private int successCount;
	private int failureCount;

	@lombok.Builder.Default
	private List<RowError> errors = new ArrayList<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RowError {
		private int rowNumber;
		private String entityId;
		private String entityType;
		private String errorMessage;
	}
}
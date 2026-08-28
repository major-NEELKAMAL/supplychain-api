package com.cognodb.supplychain.dto;

import java.time.LocalDateTime;
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
public class NodeEntityResponse extends ApiResponse {
	private String id;
	private String name;
	private String category;
	private String entityType;
	private List<ParentNodeDto> parentNodeDto;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
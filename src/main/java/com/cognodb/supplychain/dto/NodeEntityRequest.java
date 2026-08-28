package com.cognodb.supplychain.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeEntityRequest {

	private String id;

	@NotBlank(message = "Category must not be empty or blank")
	@Size(max = 49, message = "Category must be less than 50 characters")
	private String category;

	@NotBlank(message = "EntityType must not be empty or blank")
	private String entityType;

	@NotBlank(message = "Name must not be empty or blank")
	@Size(max = 99, message = "Name must be less than 100 characters")
	private String name;

	private List<ParentNodeDto> parentNodeDto;
}
package com.cognodb.supplychain.model;

import java.time.LocalDateTime;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Node("Supplier")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierNode {

	@Id
	private String id;

	@Property("name")
	private String name;

	@Property("category")
	private String category;

	@Property("createdAt")
	private LocalDateTime createdAt;

	@Property("updatedAt")
	private LocalDateTime updatedAt;
}
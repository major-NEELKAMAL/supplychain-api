package com.cognodb.supplychain.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Node("RawMaterial")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawMaterialNode {

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

	@Builder.Default
	@Relationship(type = "SUPPLIES", direction = Relationship.Direction.INCOMING)
	private Set<SupplierNode> suppliers = new HashSet<>();
}
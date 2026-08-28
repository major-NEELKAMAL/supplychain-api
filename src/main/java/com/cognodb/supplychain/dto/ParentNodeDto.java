package com.cognodb.supplychain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentNodeDto {

	private String parentId;
	private String parentName;
	private String parentEdge;

}

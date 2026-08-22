package com.wexa.supplychain.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImpactedProductResponse extends ApiResponse {
	
	private List<ImpactedProductDto> impactedProducts;
	
}

package com.cognodb.supplychain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImpactedProductDto {
	private String supplierId;
	private String supplierName;
	private String rawMaterialId;
	private String rawMaterialName;
	private String componentId;
	private String componentName;
	private String subAssemblyId;
	private String subAssemblyName;
	private String productId;
	private String productName;
	private int depth;

	public ImpactedProductDto(String supplierId, String supplierName, String rawMaterialId, String rawMaterialName,
			String componentId, String componentName, String subAssemblyId, String subAssemblyName, String productId,
			String productName, int depth) {
		this.supplierId = supplierId;
		this.supplierName = supplierName;
		this.rawMaterialId = rawMaterialId;
		this.rawMaterialName = rawMaterialName;
		this.componentId = componentId;
		this.componentName = componentName;
		this.subAssemblyId = subAssemblyId;
		this.subAssemblyName = subAssemblyName;
		this.productId = productId;
		this.productName = productName;
		this.depth = depth;
	}

}
package com.cognodb.supplychain.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cognodb.supplychain.dto.ApiResponse;
import com.cognodb.supplychain.dto.ImpactedProductDto;
import com.cognodb.supplychain.dto.ImpactedProductResponse;
import com.cognodb.supplychain.service.ProductService;
import com.cognodb.supplychain.utils.AppEnums.EntityType;

@RestController
@RequestMapping("${api.version.one}/supply-chain")
@CrossOrigin(origins = "*")
public class SupplyChainController {

	private static final Logger log = LoggerFactory.getLogger(SupplyChainController.class);

	private final ProductService service;

	public SupplyChainController(ProductService service) {
		this.service = service;
	}

	@GetMapping("/healthcheck")
	public ResponseEntity<ApiResponse> healthcheck() {
		ApiResponse apiResponse = new ApiResponse();
		apiResponse.setMessage("Supply Chain Service is up and running.");
		apiResponse.setCode(HttpStatus.OK.value());
		apiResponse.setSuccess(true);

		return ResponseEntity.ok(apiResponse);
	}

	@GetMapping("/impact/{id}")
	public ResponseEntity<ApiResponse> getImpact(@PathVariable("id") String id,
			@RequestParam(value = "entityType", required = false, defaultValue = "Supplier") String entityType) {

		log.info("GET search request received: entity id='{}', entityType='{}'", id, entityType);

		EntityType targetType = null;
		if (entityType != null && !entityType.isBlank()) {
			try {
				targetType = EntityType.fromString(entityType);
			} catch (Exception e) {
				log.warn("Search parameter parsing failed for entityType: '{}'", entityType);
				return buildError("Invalid entityType parameter: " + entityType, HttpStatus.BAD_REQUEST);
			}
		}
		List<ImpactedProductDto> paths = service.getImpactedProducts(id, targetType);

		ImpactedProductResponse response = new ImpactedProductResponse();
		response.setImpactedProductDto(paths);
		response.setMessage("Impact analysis retrieved successfully!");
		response.setCode(HttpStatus.OK.value());
		response.setSuccess(true);

		return ResponseEntity.ok(response);
	}

	private ResponseEntity<ApiResponse> buildError(String message, HttpStatus status) {
		ApiResponse response = ApiResponse.builder().message(message).code(status.value()).success(false).build();
		return ResponseEntity.status(status).body(response);
	}
}
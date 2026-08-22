package com.wexa.supplychain.controllers;

import com.wexa.supplychain.dto.ApiResponse;
import com.wexa.supplychain.dto.ImpactedProductDto;
import com.wexa.supplychain.dto.ImpactedProductResponse;
import com.wexa.supplychain.services.SupplyChainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.version.one}/supply-chain")
@CrossOrigin(origins = "*")
public class SupplyChainController {

    private final SupplyChainService service;

    public SupplyChainController(SupplyChainService service) {
        this.service = service;
    }

    /**
     * Healthcheck endpoint to verify app availability.
     * GET /api/v1/supply-chain/healthcheck
     */
    @GetMapping("/healthcheck")
    public ResponseEntity<ApiResponse> healthcheck() {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Supply Chain Service is up and running.");
        apiResponse.setCode(HttpStatus.OK.value());
        apiResponse.setSuccess(true);

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * POST /api/v1/supply-chain/seed
     */
    @PostMapping("/seed")
    public ResponseEntity<ApiResponse> seed() {
        service.seedDatabase();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Graph data seeded successfully!");
        apiResponse.setCode(HttpStatus.OK.value());
        apiResponse.setSuccess(true);

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * GET /api/v1/supply-chain/impact/{supplierId}
     */
    @GetMapping("/impact/{supplierId}")
    public ResponseEntity<ImpactedProductResponse> getImpact(@PathVariable String supplierId) {
        List<ImpactedProductDto> dto = service.getImpactedProducts(supplierId);

        ImpactedProductResponse response = new ImpactedProductResponse();
        response.setImpactedProducts(dto);
        response.setMessage("Impact analysis retrieved successfully!");
        response.setCode(HttpStatus.OK.value());
        response.setSuccess(true);

        return ResponseEntity.ok(response);
    }
}
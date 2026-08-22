package com.wexa.supplychain.controllers;

import com.wexa.supplychain.dto.ImpactedProductDto;
import com.wexa.supplychain.services.SupplyChainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supply-chain")
@CrossOrigin(origins = "*")
public class SupplyChainController {

    private final SupplyChainService service;

    public SupplyChainController(SupplyChainService service) {
        this.service = service;
    }

    @PostMapping("/seed")
    public String seed() {
        service.seedDatabase();
        return "Graph data seeded successfully!";
    }

    @GetMapping("/impact/{supplierId}")
    public List<ImpactedProductDto> getImpact(@PathVariable String supplierId) {
        return service.getImpactedProducts(supplierId);
    }
}

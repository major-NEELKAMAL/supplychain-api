package com.wexa.supplychain.dto;

public class ImpactedProductDto {
    private String productId;
    private String productName;
    private int depth;

    // Default Constructor
    public ImpactedProductDto() {
    }

    // Parameterized Constructor
    public ImpactedProductDto(String productId, String productName, int depth) {
        this.productId = productId;
        this.productName = productName;
        this.depth = depth;
    }

    // Getters and Setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
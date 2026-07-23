package com.example.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private String description;
    private Long customerId;
    private List<Long> productIds;
}

package com.gpp.concurrency.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderResponse {
    private Long orderId;
    private Long productId;
    private Integer quantityOrdered;
    private Integer stockRemaining;
    private Integer newVersion; // Only populated for optimistic locking
}

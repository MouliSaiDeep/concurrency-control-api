package com.gpp.concurrency.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsResponse {
    private long totalOrders;
    private long successfulOrders;
    private long failedOutOfStock;
    private long failedConflict;
}

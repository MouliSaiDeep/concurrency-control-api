package com.gpp.concurrency.controller;

import com.gpp.concurrency.dto.OrderRequest;
import com.gpp.concurrency.dto.OrderResponse;
import com.gpp.concurrency.dto.OrderStatsResponse;
import com.gpp.concurrency.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final InventoryService inventoryService;

    @PostMapping("/pessimistic")
    public ResponseEntity<OrderResponse> placeOrderPessimistic(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = inventoryService.placeOrderPessimistic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/optimistic")
    public ResponseEntity<OrderResponse> placeOrderOptimistic(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = inventoryService.placeOrderOptimistic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<OrderStatsResponse> getOrderStats() {
        return ResponseEntity.ok(inventoryService.getOrderStats());
    }
}

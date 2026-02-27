package com.gpp.concurrency.controller;

import com.gpp.concurrency.entity.Product;
import com.gpp.concurrency.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductsController {

    private final InventoryService inventoryService;

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getProduct(id));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetProducts() {
        inventoryService.resetInventory();
        return ResponseEntity.ok(Map.of("message", "Product inventory reset successfully."));
    }
}

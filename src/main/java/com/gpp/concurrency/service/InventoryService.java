package com.gpp.concurrency.service;

import com.gpp.concurrency.dto.OrderRequest;
import com.gpp.concurrency.dto.OrderResponse;
import com.gpp.concurrency.dto.OrderStatsResponse;
import com.gpp.concurrency.entity.Order;
import com.gpp.concurrency.entity.Product;
import com.gpp.concurrency.exception.InsufficientStockException;
import com.gpp.concurrency.exception.ProductNotFoundException;
import com.gpp.concurrency.repository.OrderRepository;
import com.gpp.concurrency.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final OrderAuditService orderAuditService;

    @Transactional
    public OrderResponse placeOrderPessimistic(OrderRequest request) {
        log.info("Attempting pessimistic order for product {} by user {}", request.getProductId(), request.getUserId());

        // 1. Acquire pessimistic write lock
        Product product = productRepository.findByIdWithPessimisticLock(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        // 2. Check stock
        if (product.getStock() < request.getQuantity()) {
            orderAuditService.recordOrder(request, "FAILED_OUT_OF_STOCK");
            throw new InsufficientStockException("Insufficient stock");
        }

        // 3. Update stock
        product.setStock(product.getStock() - request.getQuantity());
        productRepository.save(product);

        // 4. Save order
        Order order = orderAuditService.recordOrder(request, "SUCCESS");

        log.info("Pessimistic order successful. Order ID: {}", order.getId());
        return OrderResponse.builder()
                .orderId(order.getId())
                .productId(product.getId())
                .quantityOrdered(request.getQuantity())
                .stockRemaining(product.getStock())
                .build();
    }

    public OrderResponse placeOrderOptimistic(OrderRequest request) {
        log.info("Attempting optimistic order for product {} by user {}", request.getProductId(), request.getUserId());
        int maxRetries = 3;
        int attempt = 0;

        while (true) {
            try {
                return transactionTemplate.execute(status -> {
                    // 1. Read product (no lock, just reads version)
                    Product product = productRepository.findById(request.getProductId())
                            .orElseThrow(() -> new ProductNotFoundException("Product not found"));

                    // 2. Check stock
                    if (product.getStock() < request.getQuantity()) {
                        orderAuditService.recordOrder(request, "FAILED_OUT_OF_STOCK");
                        throw new InsufficientStockException("Insufficient stock");
                    }

                    // 3. Update stock
                    product.setStock(product.getStock() - request.getQuantity());
                    productRepository.save(product);

                    // 4. Save order
                    Order order = orderAuditService.recordOrder(request, "SUCCESS");

                    return OrderResponse.builder()
                            .orderId(order.getId())
                            .productId(product.getId())
                            .quantityOrdered(request.getQuantity())
                            .stockRemaining(product.getStock())
                            .newVersion(product.getVersion() + 1)
                            .build();
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                log.warn("Optimistic locking conflict on attempt {}. Product: {}", attempt, request.getProductId());
                if (attempt >= maxRetries) {
                    orderAuditService.recordOrder(request, "FAILED_CONFLICT");
                    log.error("Failed to place order due to concurrent modification after {} attempts.", maxRetries);
                    throw new IllegalStateException(
                            "Failed to place order due to concurrent modification. Please try again.");
                }
                try {
                    // Exponential backoff
                    long sleepTime = 50L * (1L << (attempt - 1));
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted during backoff", ie);
                }
            }
        }
    }

    @Transactional
    public void resetInventory() {
        log.info("Resetting inventory back to seed values.");
        Product p1 = productRepository.findById(1L).orElse(new Product());
        p1.setId(1L);
        p1.setName("Super Widget");
        p1.setStock(100);
        p1.setVersion(1);

        Product p2 = productRepository.findById(2L).orElse(new Product());
        p2.setId(2L);
        p2.setName("Mega Gadget");
        p2.setStock(50);
        p2.setVersion(1);

        productRepository.saveAll(List.of(p1, p2));
        orderRepository.deleteAllInBatch();
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        List<Order> orders = orderRepository.findAll();
        long total = orders.size();
        long success = orders.stream().filter(o -> "SUCCESS".equals(o.getStatus())).count();
        long outOfStock = orders.stream().filter(o -> "FAILED_OUT_OF_STOCK".equals(o.getStatus())).count();
        long conflict = orders.stream().filter(o -> "FAILED_CONFLICT".equals(o.getStatus())).count();

        return new OrderStatsResponse(total, success, outOfStock, conflict);
    }
}

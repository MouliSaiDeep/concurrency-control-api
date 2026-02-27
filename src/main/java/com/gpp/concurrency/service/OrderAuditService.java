package com.gpp.concurrency.service;

import com.gpp.concurrency.dto.OrderRequest;
import com.gpp.concurrency.entity.Order;
import com.gpp.concurrency.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderAuditService {

    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order recordOrder(OrderRequest request, String status) {
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantityOrdered(request.getQuantity());
        order.setUserId(request.getUserId());
        order.setStatus(status);
        return orderRepository.save(order);
    }
}

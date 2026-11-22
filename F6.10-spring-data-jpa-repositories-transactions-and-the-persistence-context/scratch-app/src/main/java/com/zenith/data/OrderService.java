package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 20: the service for ticket orders. The rollback lessons add to it.
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 20: one write, inside one transaction. The database picks the id.
    @Transactional
    public long placeOrder(UUID eventId, String customerEmail, int quantity) {
        OrderEntity order = new OrderEntity(eventId, customerEmail, quantity);
        System.out.println(">>>    id before save: " + order.getId());
        orders.save(order);
        System.out.println(">>>    id after save: " + order.getId());
        return order.getId();
    }

    // Lesson 21: two writes in one transaction. With failHalfway, an unchecked exception
    // comes between them.
    @Transactional
    public void placeTwoOrders(UUID eventId, boolean failHalfway) {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new RuntimeException("payment failed after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }

    // Lesson 22: the same two writes, but the failure is a checked exception.
    @Transactional
    public void placeTwoOrdersChecked(UUID eventId, boolean failHalfway)
            throws PaymentDeclinedException {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new PaymentDeclinedException("card declined after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }

    // Lesson 22: the fix. rollbackFor adds this checked exception to the rollback rule.
    @Transactional(rollbackFor = PaymentDeclinedException.class)
    public void placeTwoOrdersRollbackFor(UUID eventId, boolean failHalfway)
            throws PaymentDeclinedException {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new PaymentDeclinedException("card declined after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }
}

package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 25: the separate bean. persistOrder moved here from CheckoutService, together with its
// @Transactional, so Spring wraps this bean in a proxy of its own.
@Service
public class OrderWriter {

    private final OrderRepository orders;

    public OrderWriter(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}

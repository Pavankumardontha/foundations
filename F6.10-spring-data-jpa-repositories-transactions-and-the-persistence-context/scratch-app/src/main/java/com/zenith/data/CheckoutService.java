package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 23: a small service for the proxy lessons. It has one @Transactional method.
@Service
public class CheckoutService {

    private final OrderRepository orders;

    public CheckoutService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 24: no annotation. It calls persistOrder on "this", which is the target, not the proxy.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        this.persistOrder(eventId, customerEmail, failAfterSave);
    }

    // Lesson 23: one write in a transaction. It also prints who "this" is, whether a
    // transaction is open, and the path the call took to get here.
    // Lesson 24: with failAfterSave, an unchecked exception comes right after the save.
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

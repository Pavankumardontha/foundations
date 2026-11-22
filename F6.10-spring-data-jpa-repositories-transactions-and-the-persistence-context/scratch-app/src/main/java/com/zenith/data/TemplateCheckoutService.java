package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

// Lesson 27: the third fix. There is no @Transactional in this class: createOrder begins and ends
// the transaction itself, with Spring's TransactionTemplate.
@Service
public class TemplateCheckoutService {

    private final OrderRepository orders;
    private final TransactionTemplate tx;

    public TemplateCheckoutService(OrderRepository orders, TransactionTemplate tx) {
        this.orders = orders;
        this.tx = tx;
    }

    // No annotation. The code between { and } runs inside one transaction.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    before the template, transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        tx.executeWithoutResult(status -> {
            this.persistOrder(eventId, customerEmail, failAfterSave);
        });
    }

    // No annotation either. It runs inside whatever transaction is open when it is called.
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

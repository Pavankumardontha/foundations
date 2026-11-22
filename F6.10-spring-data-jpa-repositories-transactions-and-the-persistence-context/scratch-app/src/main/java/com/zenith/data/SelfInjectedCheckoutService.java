package com.zenith.data;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 26: the second fix. persistOrder stays in this class, and createOrder calls it through
// "self", a reference to this same bean that Spring injects. @Lazy lets the constructor ask for it.
@Service
public class SelfInjectedCheckoutService {

    private final OrderRepository orders;
    private final SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(OrderRepository orders,
                                       @Lazy SelfInjectedCheckoutService self) {
        this.orders = orders;
        this.self = self;
    }

    // No annotation, as in the trap. The call goes through "self" instead of "this".
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    self is: " + self.getClass().getName());
        self.persistOrder(eventId, customerEmail, failAfterSave);
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

package com.zenith.data;

import org.springframework.stereotype.Service;

import java.util.UUID;

// Lesson 25: CheckoutService after the fix. persistOrder moved out to OrderWriter, a separate bean.
// CheckoutService stays as it was, so the trap can still run next to the fix.
@Service
public class FixedCheckoutService {

    private final OrderWriter writer;

    public FixedCheckoutService(OrderWriter writer) {
        this.writer = writer;
    }

    // No annotation, as before. The call now goes through "writer", which holds OrderWriter's proxy.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, writer is: " + writer.getClass().getName());
        writer.persistOrder(eventId, customerEmail, failAfterSave);
    }
}

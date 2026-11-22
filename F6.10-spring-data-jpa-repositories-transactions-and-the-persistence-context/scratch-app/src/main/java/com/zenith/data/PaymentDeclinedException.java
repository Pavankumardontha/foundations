package com.zenith.data;

// Lesson 22: a checked exception. Its family line has Exception, but not RuntimeException.
public class PaymentDeclinedException extends Exception {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}

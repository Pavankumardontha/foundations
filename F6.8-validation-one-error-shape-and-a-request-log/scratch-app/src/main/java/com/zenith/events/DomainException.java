package com.zenith.events;

// The root of our own exception family (the F2.10 idea, rebuilt small).
// A domain exception means: the request was understood, but a business rule said no.
// It carries a stable machine code; its message is authored by us, safe for the caller.
abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}

package com.zenith.lifecycle;

// Lesson 7: the stand-in. NOT a @Component — our post-processor makes it by hand.
// It holds the original, adds behaviour, and forwards the call. This shape is a proxy.
class L7ShoutingGreeter implements L7Greeter {

    private final L7Greeter original;

    L7ShoutingGreeter(L7Greeter original) {
        this.original = original;
    }

    @Override
    public String greet() {
        return original.greet().toUpperCase() + "!!!";   // extra behaviour + forward
    }
}

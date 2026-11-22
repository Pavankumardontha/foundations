package com.zenith.doubles;

import java.util.Optional;

/**
 * MOCK — double 2 of 4 for the task.
 * A mock is a stub that also remembers how it was called.
 * Same canned answer as the stub, plus a call count and the last id asked for.
 * Public fields are fine here: this is hand-written test tooling, not production code.
 */
public class MockHoldRepository implements HoldRepository {

    private final Hold cannedHold;

    public int findByIdCallCount = 0;
    public long lastAskedId = -1;

    public MockHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdCallCount = findByIdCallCount + 1; // the memory: how many times
        lastAskedId = id;                          // the memory: asked with what
        return Optional.of(cannedHold);            // still the stub's canned answer
    }

    @Override
    public void save(Hold hold) {
        // nothing: no test here touches save
    }

    @Override
    public void deleteById(long id) {
        // nothing
    }
}

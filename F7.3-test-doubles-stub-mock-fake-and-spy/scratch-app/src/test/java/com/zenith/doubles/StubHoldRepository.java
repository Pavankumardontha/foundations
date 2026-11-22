package com.zenith.doubles;

import java.util.Optional;

/**
 * STUB — double 1 of 4 for the task.
 * A stub returns canned answers and records nothing.
 * This one answers every findById with the one hold it was given.
 */
public class StubHoldRepository implements HoldRepository {

    private final Hold cannedHold;

    public StubHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.of(cannedHold); // same answer every time; the id is ignored
    }

    @Override
    public void save(Hold hold) {
        // a stub does nothing here: no test using this stub touches save
    }

    @Override
    public void deleteById(long id) {
        // a stub does nothing here either
    }
}

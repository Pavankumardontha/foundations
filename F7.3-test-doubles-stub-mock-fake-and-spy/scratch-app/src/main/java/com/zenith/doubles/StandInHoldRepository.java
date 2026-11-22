package com.zenith.doubles;

import java.util.Optional;

/**
 * A test double: a hand-written stand-in for the real repository.
 * You give it one hold when you build it.
 * It answers every findById with that hold, instantly.
 */
public class StandInHoldRepository implements HoldRepository {

    private final Hold theOnlyHold;

    public StandInHoldRepository(Hold theOnlyHold) {
        this.theOnlyHold = theOnlyHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.of(theOnlyHold);
    }

    @Override
    public void save(Hold hold) {
        // do nothing: no test in this lesson needs save
    }

    @Override
    public void deleteById(long id) {
        // do nothing: no test in this lesson needs delete
    }
}

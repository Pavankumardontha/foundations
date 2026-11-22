package com.zenith.doubles;

import java.util.Optional;

/**
 * STUB — a second stub, for a situation that is hard to stage with a real database:
 * the hold does not exist. Canned answer: "nothing found", every time.
 */
public class StubMissingHoldRepository implements HoldRepository {

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.empty(); // canned answer: no such hold
    }

    @Override
    public void save(Hold hold) {
        // nothing
    }

    @Override
    public void deleteById(long id) {
        // nothing
    }
}

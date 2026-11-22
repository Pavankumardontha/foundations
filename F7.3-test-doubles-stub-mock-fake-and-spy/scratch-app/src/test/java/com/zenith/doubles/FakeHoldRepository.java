package com.zenith.doubles;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * FAKE — double 3 of 4 for the task. This one is a keeper.
 * A fake is a real, working implementation that is lightweight.
 * It genuinely saves, finds, and deletes — in memory, in a HashMap.
 * No canned answers: what comes out depends on what was put in.
 */
public class FakeHoldRepository implements HoldRepository {

    private final Map<Long, Hold> holdsById = new HashMap<>();

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.ofNullable(holdsById.get(id)); // empty box if nothing stored
    }

    @Override
    public void save(Hold hold) {
        holdsById.put(hold.id(), hold); // genuinely stores; saving the same id overwrites
    }

    @Override
    public void deleteById(long id) {
        holdsById.remove(id); // genuinely gone afterwards
    }
}

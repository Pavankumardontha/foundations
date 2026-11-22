package com.zenith.holds;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The fake from F7.3, ported here: a real, working, lightweight implementation.
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
        holdsById.put(hold.id(), hold); // genuinely stores; same id overwrites
    }

    @Override
    public void deleteById(long id) {
        holdsById.remove(id); // genuinely gone afterwards
    }
}

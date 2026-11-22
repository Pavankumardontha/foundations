package com.zenith.doubles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SPY — double 4 of 4 for the task.
 * A spy wraps a real object: it forwards every call to the wrapped one,
 * and records the arguments as the calls pass through.
 * Real behaviour from underneath, memory on top.
 */
public class SpyHoldRepository implements HoldRepository {

    private final HoldRepository realRepository;

    public final List<Long> findByIdIds = new ArrayList<>();
    public final List<Hold> savedHolds = new ArrayList<>();
    public final List<Long> deletedIds = new ArrayList<>();

    public SpyHoldRepository(HoldRepository realRepository) {
        this.realRepository = realRepository;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdIds.add(id);                 // record the argument
        return realRepository.findById(id);  // forward to the real one
    }

    @Override
    public void save(Hold hold) {
        savedHolds.add(hold);                // record
        realRepository.save(hold);           // forward
    }

    @Override
    public void deleteById(long id) {
        deletedIds.add(id);                  // record
        realRepository.deleteById(id);       // forward
    }
}

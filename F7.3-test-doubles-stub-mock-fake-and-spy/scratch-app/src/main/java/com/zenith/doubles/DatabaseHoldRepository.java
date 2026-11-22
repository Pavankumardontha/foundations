package com.zenith.doubles;

import java.util.Optional;

/**
 * Pretends to be the real repository that talks to a database.
 * In a real app, every method here would send SQL over the network.
 * We fake the cost with a pause, so you can feel it without installing a database.
 * A real one would be worse: it would fail completely if no database is running.
 */
public class DatabaseHoldRepository implements HoldRepository {

    public DatabaseHoldRepository() {
        System.out.println("Opening connection to the ticket database...");
    }

    @Override
    public Optional<Hold> findById(long id) {
        pretendNetworkCall(); // in a real app: SELECT ... WHERE id = ?
        if (id == 7) {
            return Optional.of(new Hold(7, 4));
        }
        if (id == 8) {
            return Optional.of(new Hold(8, 10));
        }
        return Optional.empty();
    }

    @Override
    public void save(Hold hold) {
        pretendNetworkCall(); // in a real app: INSERT or UPDATE
    }

    @Override
    public void deleteById(long id) {
        pretendNetworkCall(); // in a real app: DELETE ... WHERE id = ?
    }

    private void pretendNetworkCall() {
        try {
            Thread.sleep(1500); // pretend the network round trip takes 1.5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

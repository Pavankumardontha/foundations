package com.zenith.holds;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryHoldRepository implements HoldRepository {
    private final List<String> held = new ArrayList<>();

    @Override
    public void save(String seatId) {
        held.add(seatId);
    }

    @Override
    public List<String> allHeldSeats() {
        return List.copyOf(held);
    }
}

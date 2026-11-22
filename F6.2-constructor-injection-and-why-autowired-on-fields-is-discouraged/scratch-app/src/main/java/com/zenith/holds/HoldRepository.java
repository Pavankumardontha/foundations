package com.zenith.holds;

import java.util.List;

public interface HoldRepository {
    void save(String seatId);

    List<String> allHeldSeats();
}

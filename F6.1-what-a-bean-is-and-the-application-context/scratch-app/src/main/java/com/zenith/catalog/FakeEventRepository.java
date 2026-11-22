package com.zenith.catalog;

import java.util.List;

/** Keeps the EventRepository promises with two hardcoded events. Never touches a database. */
public class FakeEventRepository implements EventRepository {

    @Override
    public List<String> findAllNames() {
        return List.of("Fake Gig", "Fake Festival");
    }
}

package com.zenith.data;

import org.springframework.data.domain.Sort;

import java.util.List;

// A stand-in with no database behind it. Two small methods are all it takes.
class FakeUpcomingEvents implements UpcomingEvents {

    @Override
    public List<EventEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public long count() {
        return 0;
    }
}

package com.zenith.catalog;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    public List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

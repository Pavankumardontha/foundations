package com.zenith.autoconfig;

import org.springframework.stereotype.Component;

// A component that DEMANDS the repository at construction time.
// Constructor injection (F6.2): Spring must find a NoteRepository bean
// to build this component, or startup fails.
@Component
public class NoteCounter {

    private final NoteRepository repository;

    NoteCounter(NoteRepository repository) {
        this.repository = repository;
    }

    long count() {
        return repository.count();
    }
}

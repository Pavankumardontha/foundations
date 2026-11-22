package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Lesson 13: three page requests, so you can see in the log which SQL statements each one sends.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=count
@Component
@Profile("count")
class CountDemo implements CommandLineRunner {

    private final EventRepository events;

    CountDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. Page, page 0, size 2");
        Page<EventEntity> first = events.findAll(PageRequest.of(0, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(first.getContent()) + "  total: " + first.getTotalElements());

        System.out.println(">>> 2. Page, page 3, size 2");
        Page<EventEntity> last = events.findAll(PageRequest.of(3, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(last.getContent()) + "  total: " + last.getTotalElements());

        System.out.println(">>> 3. Slice, page 0, size 2");
        Slice<EventEntity> slice = events.findAllBy(PageRequest.of(0, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(slice.getContent()) + "  hasNext: " + slice.hasNext());
    }

    private static List<String> names(List<EventEntity> found) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : found) {
            names.add(e.getName());
        }
        return names;
    }
}

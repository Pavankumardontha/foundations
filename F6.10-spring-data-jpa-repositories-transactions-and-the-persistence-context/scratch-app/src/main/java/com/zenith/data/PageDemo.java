package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Lesson 12: ask for one page of events, read the answer, then walk through the other pages.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=page
@Component
@Profile("page")
class PageDemo implements CommandLineRunner {

    private final EventRepository events;

    PageDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        // page number (counting starts at 0), rows per page, sort by the entity field startsAt
        Pageable firstPage = PageRequest.of(0, 2, Sort.by("startsAt"));
        System.out.println(">>> asked for: " + firstPage);

        Page<EventEntity> page = events.findAll(firstPage);
        System.out.println(">>> getContent():          " + names(page));
        System.out.println(">>> getNumber():           " + page.getNumber());
        System.out.println(">>> getSize():             " + page.getSize());
        System.out.println(">>> getNumberOfElements(): " + page.getNumberOfElements());
        System.out.println(">>> getTotalElements():    " + page.getTotalElements());
        System.out.println(">>> getTotalPages():       " + page.getTotalPages());
        System.out.println(">>> hasNext():             " + page.hasNext());

        while (page.hasNext()) {
            page = events.findAll(page.nextPageable());
            System.out.println(">>> page " + page.getNumber() + ": " + names(page) + "  hasNext() = " + page.hasNext());
        }
    }

    private static List<String> names(Page<EventEntity> page) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : page.getContent()) {
            names.add(e.getName());
        }
        return names;
    }
}

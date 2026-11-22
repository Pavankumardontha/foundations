package com.zenith.data;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;
    private final EntityManager entityManager;

    public EventService(EventRepository events, EntityManager entityManager) {
        this.events = events;
        this.entityManager = entityManager;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 15: the same event loaded twice, with no transaction around the two loads.
    public String loadTwiceWithoutTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 15: the same two loads inside one transaction. The second one is answered from the
    // persistence context, without a SELECT.
    @Transactional
    public String loadTwiceInOneTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 16: load the event, change its name, and return. There is no save() call anywhere.
    @Transactional
    public void renameWithoutSave(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 16: read the name from the database. findById runs in its own short transaction.
    public String currentName(UUID eventId) {
        return events.findById(eventId).orElseThrow().getName();
    }

    // Lesson 17: the same steps as renameWithoutSave, but without @Transactional.
    public void renameOutsideTransaction(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName()
                + ", managed: " + entityManager.contains(event));
    }

    // Lesson 18: what Hibernate and Postgres say about a normal transaction.
    @Transactional
    public String inspectNormalTransaction(UUID eventId) {
        return inspect(events.findById(eventId).orElseThrow());
    }

    // Lesson 18: the same, in a read-only transaction.
    @Transactional(readOnly = true)
    public String inspectReadOnlyTransaction(UUID eventId) {
        return inspect(events.findById(eventId).orElseThrow());
    }

    // Lesson 18: the same steps as renameWithoutSave, but in a read-only transaction.
    @Transactional(readOnly = true)
    public void renameInReadOnlyTransaction(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 18: runs inside the caller's transaction. Session is Hibernate's own version of the
    // EntityManager, with extra methods.
    private String inspect(EventEntity event) {
        Session session = entityManager.unwrap(Session.class);
        Object postgres = entityManager.createNativeQuery("show transaction_read_only").getSingleResult();
        return "flush mode: " + session.getHibernateFlushMode()
                + ", entity read-only: " + session.isReadOnly(event)
                + ", postgres transaction_read_only: " + postgres;
    }
}

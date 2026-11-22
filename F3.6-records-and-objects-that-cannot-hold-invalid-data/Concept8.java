import java.util.HashSet;
import java.util.Set;

public class Concept8 {

    // An event modelled as a RECORD: equality = all the data.
    record EventRecord(String id, String title) {}

    // An event modelled as an ENTITY class: equality = identity (id only), state can change.
    static class EventEntity {
        private final String id;
        private String title;                      // mutable: the event gets renamed

        EventEntity(String id, String title) {
            this.id = id;
            this.title = title;
        }

        void rename(String newTitle) { this.title = newTitle; }

        String title() { return title; }

        @Override public boolean equals(Object o) {
            return o instanceof EventEntity other && id.equals(other.id);
        }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "EventEntity[id=" + id + ", title=" + title + "]"; }
    }

    public static void main(String[] args) {
        // --- the record version: renaming breaks "sameness" ---
        EventRecord before = new EventRecord("EVT-1", "Java Meetup");
        Set<EventRecord> published = new HashSet<>();
        published.add(before);

        EventRecord after = new EventRecord("EVT-1", "Java Meetup 2026");  // "rename" = new object
        System.out.println("record: same event before/after rename? " + before.equals(after));
        System.out.println("record: set still recognises the event? " + published.contains(after));

        // --- the entity version: identity survives change ---
        EventEntity event = new EventEntity("EVT-1", "Java Meetup");
        Set<EventEntity> registry = new HashSet<>();
        registry.add(event);

        event.rename("Java Meetup 2026");
        EventEntity sameIdFreshLoad = new EventEntity("EVT-1", "Java Meetup 2026");
        System.out.println("entity: still found after rename?      " + registry.contains(event));
        System.out.println("entity: equal to a fresh load of EVT-1? " + event.equals(sameIdFreshLoad));
    }
}

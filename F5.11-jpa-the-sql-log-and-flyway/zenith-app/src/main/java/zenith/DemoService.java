package zenith;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// F5.11 Lesson 10: each @Transactional method runs inside ONE database transaction.
// The persistence context (Hibernate's notebook) opens when the method starts
// and is checked + thrown away when the method ends.
@Service
public class DemoService {

    private final EventRepository events;

    public DemoService(EventRepository events) {
        this.events = events;
    }

    // Load the same row twice inside ONE transaction - count the selects.
    @Transactional
    public void loadTwiceInOneTransaction(UUID id) {
        Event a = events.findById(id).orElseThrow();
        Event b = events.findById(id).orElseThrow();
        System.out.println(">>> inside one transaction - same object? " + (a == b));
    }

    // Change a field and call NO save(). Watch when the UPDATE appears.
    @Transactional
    public void rename(UUID id, String newName) {
        Event e = events.findById(id).orElseThrow();
        e.setName(newName);
        System.out.println(">>> rename: setName done, no save() called, method still running");
    }   // <- the transaction commits HERE: notebook checked, UPDATE issued only if something changed

    // F5.11 Lesson 11: touch the stand-in list while the notebook is open.
    @Transactional
    public void listTiersOf(UUID eventId) {
        Event e = events.findById(eventId).orElseThrow();
        System.out.println(">>> event loaded; now touching the list...");
        int n = e.getPriceTiers().size();   // <- the touch: the fetch happens HERE
        System.out.println(">>> " + e.getName() + " has " + n + " price tiers");
    }

    // F5.11 Lesson 12 (subtask F): the N+1, triggered on purpose.
    // Five events, one touch each. No line below looks wrong. Count the statements in the log.
    @Transactional
    public void triggerNPlusOne() {
        java.util.List<Event> five = events.findTop5ByOrderByNameAsc();   // statement 1: the list
        System.out.println(">>> loaded " + five.size() + " events with ONE query; now the loop:");
        for (Event e : five) {
            int n = e.getPriceTiers().size();          // statements 2..6: one fetch PER event
            System.out.println(">>>   " + e.getName() + " -> " + n + " tiers");
        }
    }
}

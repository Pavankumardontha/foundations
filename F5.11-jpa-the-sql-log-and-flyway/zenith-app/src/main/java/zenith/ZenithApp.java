package zenith;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class ZenithApp {

    // One row of the result, as a Java object. A record: just carries the four values.
    record EventRow(UUID id, String name, String venue, OffsetDateTime startsAt) {}

    public static void main(String[] args) {
        SpringApplication.run(ZenithApp.class, args);
    }

    // F5.11 Lesson 6: the first JPA query ever - run only now that the SQL log is on.
    @Bean
    CommandLineRunner firstLoggedQuery(EventRepository events) {
        return args -> {
            UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
            Event e = events.findById(id).orElseThrow();
            System.out.println(">>> loaded via JPA: " + e.getName() + " at " + e.getStartsAt());
        };
    }

    // F5.11 Lesson 12 (subtask F): produce the single most common performance bug in Java backends.
    @Bean
    CommandLineRunner nPlusOneDemo(DemoService demo) {
        return args -> {
            System.out.println(">>> --- F: trigger the N+1 on purpose ---");
            demo.triggerNPlusOne();
            System.out.println(">>> --- F end ---");
        };
    }

    // F5.11 Lesson 11: lazy loading - the touch inside a transaction, and the crash outside.
    @Bean
    CommandLineRunner lazyLoadingDemo(DemoService demo, EventRepository events) {
        return args -> {
            UUID monsoon = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

            System.out.println(">>> --- D: touch the list INSIDE a transaction ---");
            demo.listTiersOf(monsoon);

            System.out.println(">>> --- E: touch the list OUTSIDE a transaction ---");
            Event e = events.findById(monsoon).orElseThrow();
            try {
                e.getPriceTiers().size();   // the notebook is already closed
            } catch (Exception ex) {
                System.out.println(">>> CRASH: " + ex.getClass().getName());
                System.out.println(">>> message: " + ex.getMessage());
            }
        };
    }

    // F5.11 Lesson 10: the persistence context, seen from outside and inside a transaction.
    @Bean
    CommandLineRunner persistenceContextDemo(DemoService demo, EventRepository events) {
        return args -> {
            UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"); // Classical Evening

            System.out.println(">>> --- A: load twice WITHOUT a transaction ---");
            Event x = events.findById(id).orElseThrow();
            Event y = events.findById(id).orElseThrow();
            System.out.println(">>> outside any transaction - same object? " + (x == y));
            x.setName("Ghost Rename");   // change it - no transaction is open
            System.out.println(">>> set name to 'Ghost Rename' outside a transaction - watch for an UPDATE (there will be none)");

            System.out.println(">>> --- B: load twice inside ONE transaction ---");
            demo.loadTwiceInOneTransaction(id);

            System.out.println(">>> --- C: rename with no save(), inside a transaction ---");
            demo.rename(id, "Classical Evening (renamed)");
            demo.rename(id, "Classical Evening");          // put it back - a second UPDATE
            demo.rename(id, "Classical Evening");          // same value again - no UPDATE this time
        };
    }

    // F5.11 Lesson 9 (subtask E, second half): load a parent and look at the mirror list -
    // WITHOUT touching its contents.
    @Bean
    CommandLineRunner loadEventSeeList(EventRepository events) {
        return args -> {
            UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
            Event e = events.findById(id).orElseThrow();
            System.out.println(">>> loaded event: " + e.getName());
            System.out.println(">>> priceTiers field holds a: " + e.getPriceTiers().getClass().getName());
        };
    }

    // F5.11 Lesson 8 (subtask E, first half): load a child row and walk to its parent object.
    @Bean
    CommandLineRunner loadTierWithEvent(PriceTierRepository tiers) {
        return args -> {
            UUID vipId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
            PriceTier tier = tiers.findById(vipId).orElseThrow();
            System.out.println(">>> tier '" + tier.getName()
                    + "' belongs to event '" + tier.getEvent().getName() + "'");
        };
    }

    // F5.11 Lesson 7 (subtask D): call the derived method and read the SQL it generates.
    @Bean
    CommandLineRunner searchByNameJpa(EventRepository events) {
        return args -> {
            List<Event> hits = events.findByNameContainingIgnoreCase("night");
            System.out.println(">>> name search 'night' found " + hits.size() + ":");
            for (Event e : hits) {
                System.out.println(">>>   " + e.getName());
            }
        };
    }

    // Spring injects a ready-made JdbcClient. It borrows from the HikariCP pool
    // and returns the connection by itself - no Connection, no try-with-resources here.
    @Bean
    CommandLineRunner searchByCity(JdbcClient db) {
        return args -> {
            String city = args.length > 0 ? args[0] : "Hyderabad";

            List<EventRow> events = db.sql("""
                            SELECT e.id, e.name, v.name AS venue, e.starts_at
                            FROM event e
                            JOIN venue v ON v.id = e.venue_id
                            WHERE v.city = :city
                            ORDER BY e.starts_at
                            """)
                    .param("city", city)                       // bind the named parameter
                    .query((rs, rowNum) -> new EventRow(       // the row mapper: one row -> one object
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("venue"),
                            rs.getObject("starts_at", OffsetDateTime.class)))
                    .list();                                   // run, walk all rows, collect

            System.out.println(">>> events in " + city + ": " + events.size());
            for (EventRow e : events) {
                System.out.println(">>> " + e);
            }
        };
    }
}

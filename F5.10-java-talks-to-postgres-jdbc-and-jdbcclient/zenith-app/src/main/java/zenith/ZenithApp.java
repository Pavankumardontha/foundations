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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

public class LeakDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        partA_leakWithoutPool();
        System.out.println();
        partB_leakWithPool();
    }

    // Part A: no pool. Open connections in a loop of 200 and never close them.
    static void partA_leakWithoutPool() {
        System.out.println("=== Part A: no pool - open 200 connections, close none ===");
        List<Connection> held = new ArrayList<>();   // holds every connection so none is ever closed
        try {
            for (int i = 1; i <= 200; i++) {
                held.add(DriverManager.getConnection(URL, USER, PASS));   // no try-with-resources!
                if (i % 20 == 0) System.out.println("opened " + i + " connections, closed none...");
            }
            System.out.println("opened all 200 (unexpected!)");
        } catch (Exception e) {
            System.out.println();
            System.out.println("opened " + held.size() + " connections, then the server refused:");
            System.out.println("  error type:    " + e.getClass().getSimpleName());
            System.out.println("  error message: " + e.getMessage().trim());
        } finally {
            for (Connection c : held) {
                try { c.close(); } catch (Exception ignore) {}
            }
            System.out.println("(cleanup: closed all " + held.size() + " so the database is usable again)");
        }
    }

    // Part B: the same bug behind a HikariCP pool - borrow and never return.
    static void partB_leakWithPool() {
        System.out.println("=== Part B: HikariCP pool - borrow 200 connections, return none ===");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASS);
        config.setConnectionTimeout(2000);   // wait at most 2 s for a free connection (default is 30 s)

        List<Connection> held = new ArrayList<>();
        try (HikariDataSource pool = new HikariDataSource(config)) {
            try {
                for (int i = 1; i <= 200; i++) {
                    held.add(pool.getConnection());   // borrow, never return
                    System.out.println("borrowed " + i + ", returned none...");
                }
                System.out.println("borrowed all 200 (unexpected!)");
            } catch (Exception e) {
                System.out.println();
                System.out.println("borrow number " + (held.size() + 1) + " failed:");
                System.out.println("  error type:    " + e.getClass().getSimpleName());
                System.out.println("  error message: " + e.getMessage());
            } finally {
                for (Connection c : held) {
                    try { c.close(); } catch (Exception ignore) {}
                }
            }
        }
    }
}

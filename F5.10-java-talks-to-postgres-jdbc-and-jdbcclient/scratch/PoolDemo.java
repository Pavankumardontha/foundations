import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;

public class PoolDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Part A: a fresh connection every time (no pool) ===");
        for (int i = 1; i <= 5; i++) {
            long start = System.nanoTime();
            try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                System.out.println("open #" + i + ": took " + ms + " ms");
            }   // close() here really hangs up the connection
        }

        System.out.println();
        System.out.println("=== Part B: borrowing from a HikariCP pool ===");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASS);
        // note: we do NOT set a pool size, so HikariCP uses its default

        try (HikariDataSource pool = new HikariDataSource(config)) {
            System.out.println("default maximum pool size: " + pool.getMaximumPoolSize());

            for (int i = 1; i <= 5; i++) {
                long start = System.nanoTime();
                try (Connection c = pool.getConnection()) {   // borrow
                    long us = (System.nanoTime() - start) / 1_000;
                    System.out.println("borrow #" + i + ": got " + c.getClass().getSimpleName()
                            + " in " + us + " microseconds");
                }   // close() here does NOT hang up; it returns the connection to the pool
            }
        }   // closing the pool itself hangs up all its connections
    }
}

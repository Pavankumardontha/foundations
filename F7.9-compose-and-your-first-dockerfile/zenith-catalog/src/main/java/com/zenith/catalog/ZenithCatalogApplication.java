package com.zenith.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ZenithCatalogApplication {

    private final JdbcTemplate jdbc;

    public ZenithCatalogApplication(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(ZenithCatalogApplication.class, args);
    }

    /** Answers without touching the database. Proves the web server is up. */
    @GetMapping("/")
    public String home() {
        return "Zenith Catalog is running\n";
    }

    /** Asks the database for the time. Proves the app can reach Postgres. */
    @GetMapping("/db")
    public String db() {
        return "db says: " + jdbc.queryForObject("select now()", String.class) + "\n";
    }

    /** Tiny endpoint added in Lesson 12 to change the jar and bust the COPY cache. */
    @GetMapping("/ping")
    public String ping() {
        return "pong v3\n";
    }
}

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// Lesson 11 demo: the FIX. Same malicious inputs as the injection demo, but now the value is a
// bound ? parameter. The SQL is parsed BEFORE the value arrives, so the value can never be SQL.
public class SqlInjectionFixedDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {

            // Safe query: the value is a ? placeholder, never glued in.
            String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = ?";

            System.out.println("=== Parameterised SELECT: same malicious inputs, now harmless ===");
            safeSearch(c, sql, "Hyderabad");                   // honest input -> 4
            safeSearch(c, sql, "' OR '1'='1");                 // attack input -> 0
            safeSearch(c, sql, "'; DROP TABLE scratch; --");   // attack input -> 0

            // Prove the DROP attack does NOT destroy the table when bound.
            System.out.println("\n=== The DROP TABLE attack, bound as a parameter ===");
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS scratch");
                s.execute("CREATE TABLE scratch (note text)");
            }
            System.out.println("Before: scratch exists? " + tableExists(c, "scratch"));
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "'; DROP TABLE scratch; --");
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) count++;
                    System.out.println("rows returned: " + count);
                }
            }
            System.out.println("After:  scratch exists? " + tableExists(c, "scratch"));
        }
    }

    static void safeSearch(Connection c, String sql, String city) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) count++;
                System.out.println("city input = \"" + city + "\"  ->  rows returned: " + count);
            }
        }
    }

    static boolean tableExists(Connection c, String name) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }
}

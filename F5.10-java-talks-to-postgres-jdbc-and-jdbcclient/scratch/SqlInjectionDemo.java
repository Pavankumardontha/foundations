import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Lesson 10 demo: SQL injection. The city value is GLUED into the SQL text by concatenation,
// so a crafted value stops being data and becomes SQL. DANGEROUS ON PURPOSE.
public class SqlInjectionDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {

            // ---------- PART A: ' OR '1'='1  makes every row come back ----------
            System.out.println("=== PART A: concatenated SELECT, normal vs malicious input ===");
            vulnerableSearch(c, "Hyderabad");          // honest input
            vulnerableSearch(c, "' OR '1'='1");        // attack input

            // ---------- PART B: '; DROP TABLE scratch; --  destroys a throwaway table ----------
            System.out.println("\n=== PART B: concatenated statement drops a throwaway table ===");
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS scratch");
                s.execute("CREATE TABLE scratch (note text)");
                s.execute("INSERT INTO scratch VALUES ('I am important data')");
            }
            System.out.println("Before attack, scratch table exists? " + tableExists(c, "scratch"));

            String evilCity = "'; DROP TABLE scratch; --";
            String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = '" + evilCity + "'";
            System.out.println("SQL actually sent:\n  " + sql);
            try (Statement s = c.createStatement()) {
                s.execute(sql);          // runs the SELECT, THEN the DROP TABLE, THEN a comment
            }
            System.out.println("After attack, scratch table exists? " + tableExists(c, "scratch"));
        }
    }

    // VULNERABLE: the city value is pasted straight into the SQL string.
    static void vulnerableSearch(Connection c, String city) throws Exception {
        String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = '" + city + "'";
        System.out.println("\ncity input = \"" + city + "\"");
        System.out.println("SQL actually sent:\n  " + sql);
        int count = 0;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) count++;
        }
        System.out.println("rows returned: " + count);
    }

    static boolean tableExists(Connection c, String name) throws Exception {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }
}

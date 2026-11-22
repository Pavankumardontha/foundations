import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Lesson 6 demo: binding = filling each ? hole with a value, by position (1-based).
// The value is always treated as plain DATA, never as SQL.
public class BindParamsDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ? ORDER BY e.name";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);

        // 1) Run WITHOUT filling the hole -> error.
        try {
            ps.executeQuery();
        } catch (Exception e) {
            System.out.println("Ran without binding -> " + e.getMessage());
        }

        // 2) Fill hole number 1 with "Hyderabad".
        ps.setString(1, "Hyderabad");
        System.out.println("\nBound parameter 1 = \"Hyderabad\":");
        printNames(ps.executeQuery());

        // 3) Reuse the SAME statement; fill hole 1 with "Mumbai".
        ps.setString(1, "Mumbai");
        System.out.println("\nBound parameter 1 = \"Mumbai\":");
        printNames(ps.executeQuery());

        // 4) Even a value full of SQL punctuation is treated as plain data.
        ps.setString(1, "Hyderabad' OR '1'='1");
        System.out.println("\nBound parameter 1 = \"Hyderabad' OR '1'='1\"  (note the quotes):");
        printNames(ps.executeQuery());

        ps.close();
        c.close();
    }

    static void printNames(ResultSet rs) throws Exception {
        int count = 0;
        while (rs.next()) {
            System.out.println("  - " + rs.getString("name"));
            count++;
        }
        System.out.println("  (" + count + " rows)");
        rs.close();
    }
}

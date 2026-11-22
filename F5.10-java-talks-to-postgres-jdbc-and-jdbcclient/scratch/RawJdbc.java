import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Subtask 2: raw JDBC, written by hand. Verbose on purpose — that is the point.
// city lives on venue, so reaching it from event takes a join (v.id = e.venue_id).
public class RawJdbc {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String user = "postgres";
        String password = "dev";
        String city = args.length > 0 ? args[0] : "Hyderabad";

        String sql = "SELECT e.id, e.name FROM event e "
                   + "JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ?";

        // Each numbered step is something JDBC made YOU do by hand.
        try (Connection c = DriverManager.getConnection(url, user, password);  // 1. open connection
             PreparedStatement ps = c.prepareStatement(sql)) {                 // 2. prepare statement
            ps.setString(1, city);                                             // 3. bind by position
            try (ResultSet rs = ps.executeQuery()) {                          // 4. execute
                System.out.println("Events in " + city + ":");
                while (rs.next()) {                                           // 5. step row by row
                    String id = rs.getString("id");                          // 6. read by column name
                    String name = rs.getString("name");                      // 6. read by column name
                    System.out.println("  " + id + "  " + name);
                }
            }                                                                 // 7. close rs
        }                                                                     // 7. close ps, then c
    }
}

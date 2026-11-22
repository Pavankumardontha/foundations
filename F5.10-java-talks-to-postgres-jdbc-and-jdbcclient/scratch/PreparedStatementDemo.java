import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;

// Lesson 5 demo: a PreparedStatement is SQL sent WITH holes (?) left in it on purpose.
// The database prepares the structure now; the actual values are supplied later, separately.
public class PreparedStatementDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT id, name FROM event WHERE venue_id = ? AND name = ?";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);

        System.out.println("SQL text sent to the database:");
        System.out.println("  " + sql);

        ParameterMetaData pmd = ps.getParameterMetaData();
        System.out.println("Number of ? holes this statement expects: " + pmd.getParameterCount());
        System.out.println("No values are in that SQL text. They get supplied separately, later.");

        ps.close();
        c.close();
    }
}

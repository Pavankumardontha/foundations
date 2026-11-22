import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

// Lesson 7 demo: a ResultSet is a CURSOR over the returned rows.
// It starts BEFORE the first row; rs.next() steps to the next row and reports if one exists.
public class ResultSetDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        String sql = "SELECT e.id, e.name FROM event e JOIN venue v ON v.id = e.venue_id "
                   + "WHERE v.city = ? ORDER BY e.name";

        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, "Hyderabad");

        ResultSet rs = ps.executeQuery();
        System.out.println("A ResultSet starts positioned BEFORE the first row.");

        int rowNumber = 0;
        while (rs.next()) {                               // step to next row; false when none left
            rowNumber++;
            UUID id = rs.getObject("id", UUID.class);     // read a column by NAME, typed as UUID
            String nameByName = rs.getString("name");     // read a column by NAME
            String nameByIndex = rs.getString(2);         // read the SAME column by INDEX (1-based)
            System.out.println("Row " + rowNumber + ": id=" + id
                    + "  name(byName)=" + nameByName
                    + "  name(byIndex)=" + nameByIndex);
        }
        System.out.println("rs.next() returned false, so the loop ended after "
                + rowNumber + " rows.");

        rs.close();
        ps.close();
        c.close();
    }
}

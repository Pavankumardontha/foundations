import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Three ways injection survives even though "we use prepared statements".
public class StillInjectableDemo {
    static final String URL  = "jdbc:postgresql://localhost:5432/zenith";
    static final String USER = "postgres";
    static final String PASS = "dev";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            partA_identifierConcat(c);
            partB_gluedThenPrepared(c);
            partC_serverSideGlue(c);
        }
    }

    // Way 1: the ORDER BY column is glued, because ? cannot stand for a column name.
    static void partA_identifierConcat(Connection c) throws Exception {
        System.out.println("=== Way 1: concatenated ORDER BY (identifier) ===");
        for (String sortColumn : new String[]{"name", "capacity DESC"}) {
            String sql = "SELECT name, capacity FROM venue ORDER BY " + sortColumn;   // glued!
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                System.out.println("user input = \"" + sortColumn + "\" ->");
                while (rs.next()) {
                    System.out.println("   " + rs.getString("name")
                            + "  (capacity " + rs.getInt("capacity") + ")");
                }
            }
        }
        System.out.println("(\"capacity DESC\" was user input and it RAN AS SQL - the keyword steered the query)");
    }

    // Way 2: the string was glued BEFORE prepareStatement - preparing it changes nothing.
    static void partB_gluedThenPrepared(Connection c) throws Exception {
        System.out.println();
        System.out.println("=== Way 2: glued first, prepared after ===");
        for (String city : new String[]{"Hyderabad", "' OR '1'='1"}) {
            String sql = "SELECT count(*) FROM event e JOIN venue v ON v.id = e.venue_id "
                       + "WHERE v.city = '" + city + "'";            // glued BEFORE preparing
            try (PreparedStatement ps = c.prepareStatement(sql);     // "prepared" - but too late
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                System.out.println("city input = \"" + city + "\"  ->  rows matched: " + rs.getInt(1));
            }
        }
    }

    // Way 3: Java binds perfectly - but a function INSIDE the database glues the value into new SQL.
    static void partC_serverSideGlue(Connection c) throws Exception {
        System.out.println();
        System.out.println("=== Way 3: Java binds perfectly, the database function glues ===");
        String sql = "SELECT * FROM events_in(?)";                   // a real bind parameter!
        for (String city : new String[]{"Hyderabad", "' OR '1'='1"}) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, city);                               // bound as data - safe so far
                try (ResultSet rs = ps.executeQuery()) {
                    int n = 0;
                    while (rs.next()) n++;
                    System.out.println("city input = \"" + city + "\"  ->  rows returned: " + n);
                }
            }
        }
        System.out.println("(the function inside Postgres glued the value into new SQL - injection moved server-side)");
    }
}

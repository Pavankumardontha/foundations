import java.sql.Connection;
import java.sql.DriverManager;

// Lesson 3 demo: the JDBC URL + user + password is the full ADDRESS your program dials.
// Each attempt changes ONE part of that address, so you can see which failure each part causes.
public class JdbcUrlDemo {
    public static void main(String[] args) {
        attempt("Correct address + credentials",
                "jdbc:postgresql://localhost:5432/zenith", "postgres", "dev");

        attempt("Wrong database name",
                "jdbc:postgresql://localhost:5432/nosuchdb", "postgres", "dev");

        attempt("Wrong port (9999, nothing listening)",
                "jdbc:postgresql://localhost:9999/zenith", "postgres", "dev");

        attempt("Wrong password",
                "jdbc:postgresql://localhost:5432/zenith", "postgres", "wrongpass");
    }

    static void attempt(String label, String url, String user, String password) {
        try {
            Connection c = DriverManager.getConnection(url, user, password);
            System.out.println(label + " -> CONNECTED, current database = " + c.getCatalog());
            c.close();
        } catch (Exception e) {
            System.out.println(label + " -> FAILED: " + e.getMessage());
        }
    }
}

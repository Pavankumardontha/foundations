import java.sql.Connection;
import java.sql.DriverManager;

// Lesson 2 demo: without the driver, DriverManager cannot find anyone who speaks Postgres.
// With the driver jar on the classpath, the same code connects, and we can see the REAL
// class behind the Connection interface.
public class ConnectWithoutDriver {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/zenith";
        try {
            Connection c = DriverManager.getConnection(url, "postgres", "dev");
            System.out.println("Connected. Real object behind the Connection interface: "
                + c.getClass().getName());
            c.close();
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }
}

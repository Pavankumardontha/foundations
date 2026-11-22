import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;

// Lesson 4 demo: a Connection is ONE open, live conversation with the database.
// It costs real time to open, it can be inspected while open, and it is dead once closed.
public class ConnectionDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/zenith";

        long start = System.nanoTime();
        Connection c = DriverManager.getConnection(url, "postgres", "dev");
        long openMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Opening the connection took about " + openMillis + " ms");
        System.out.println("Real class behind the Connection: " + c.getClass().getName());
        System.out.println("Is it open right now? " + !c.isClosed());

        DatabaseMetaData meta = c.getMetaData();
        System.out.println("Talking to: " + meta.getDatabaseProductName()
                + " " + meta.getDatabaseProductVersion());
        System.out.println("Connected as user: " + meta.getUserName());
        System.out.println("Current database: " + c.getCatalog());

        c.close();
        System.out.println("After close(), is it closed? " + c.isClosed());

        // A closed connection is dead; using it throws.
        try {
            c.getCatalog();
        } catch (Exception e) {
            System.out.println("Using it after close -> "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}

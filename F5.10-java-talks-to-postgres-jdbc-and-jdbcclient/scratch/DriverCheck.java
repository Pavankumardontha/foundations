import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

// Lesson 2 demo: a JDBC driver on the classpath REGISTERS ITSELF with DriverManager.
// Run this with and without the Postgres driver jar on the classpath and compare.
public class DriverCheck {
    public static void main(String[] args) {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        if (!drivers.hasMoreElements()) {
            System.out.println("No JDBC drivers are registered.");
            return;
        }
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            System.out.println("Registered driver: " + d.getClass().getName()
                + "  (version " + d.getMajorVersion() + "." + d.getMinorVersion() + ")");
        }
    }
}

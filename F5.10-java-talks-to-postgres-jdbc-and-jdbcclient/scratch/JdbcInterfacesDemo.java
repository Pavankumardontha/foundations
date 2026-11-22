import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Lesson 1 demo: JDBC is a set of INTERFACES that ship inside Java itself.
// The real working objects are supplied later by a database driver.
public class JdbcInterfacesDemo {
    public static void main(String[] args) {
        Class<?>[] jdbcTypes = { Connection.class, PreparedStatement.class, ResultSet.class };
        for (Class<?> t : jdbcTypes) {
            System.out.println(
                t.getName()
                + "  | is it an interface? " + t.isInterface()
                + "  | ships in Java module: " + t.getModule().getName());
        }
    }
}

import java.lang.reflect.Method;
import java.util.Arrays;

// Asks, at runtime: which labels are visible on the method work()?
public class Inspect {

    @Timed
    static void work() {
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws Exception {
        Method m = Inspect.class.getDeclaredMethod("work");
        System.out.println("Labels visible at runtime: " + Arrays.toString(m.getAnnotations()));
    }
}

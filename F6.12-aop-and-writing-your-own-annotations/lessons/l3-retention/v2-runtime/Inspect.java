import java.lang.reflect.Method;
import java.util.Arrays;

// Identical to version 1. Only Timed.java differs.
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

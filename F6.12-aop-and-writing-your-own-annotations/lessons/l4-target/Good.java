import java.lang.reflect.Method;
import java.util.Arrays;

// Legal placement: @Timed on a method.
public class Good {

    @Timed
    static void work() {
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws Exception {
        work();
        Method m = Good.class.getDeclaredMethod("work");
        System.out.println("Labels visible at runtime: " + Arrays.toString(m.getAnnotations()));
    }
}

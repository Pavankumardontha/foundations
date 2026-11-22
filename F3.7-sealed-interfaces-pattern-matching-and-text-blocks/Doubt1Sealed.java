sealed interface Result permits Ok, Failed {}

final class Ok implements Result {}

// sealed again: Failed has its own guest list of exactly two subclasses.
sealed class Failed implements Result permits NetworkFailed, DiskFailed {}

final class NetworkFailed extends Failed {}
final class DiskFailed extends Failed {}

public class Doubt1Sealed {
    public static void main(String[] args) {
        Result r = new DiskFailed();
        System.out.println("compiles: whole tree is known, " + r.getClass().getName());
    }
}

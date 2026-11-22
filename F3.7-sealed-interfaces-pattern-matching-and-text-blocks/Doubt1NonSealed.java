sealed interface Result permits Ok, Failed {}

final class Ok implements Result {}

// non-sealed: "my branch is open again — anyone may extend me"
non-sealed class Failed implements Result {}

// This class is NOT on the permits list. Nobody asked the sealed interface
// for permission. Yet it compiles fine, because Failed opened its branch.
class NetworkFailed extends Failed {}

public class Doubt1NonSealed {
    public static void main(String[] args) {
        Result r = new NetworkFailed();
        System.out.println("is it a Result? " + (r instanceof Result));
        System.out.println("actual class:   " + r.getClass().getName());
    }
}

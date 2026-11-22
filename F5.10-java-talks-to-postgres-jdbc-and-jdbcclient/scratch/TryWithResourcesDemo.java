// Lesson 8 demo: try-with-resources closes every resource automatically, in REVERSE order,
// even when the body throws. We use a fake Resource that prints when it opens, is used, and closes.
public class TryWithResourcesDemo {

    // A resource is anything that implements AutoCloseable. Connection, PreparedStatement,
    // and ResultSet all do. Here we fake one so the open/use/close order is visible.
    static class Resource implements AutoCloseable {
        private final String name;
        Resource(String name) { this.name = name; System.out.println("opened " + name); }
        void use()  { System.out.println("using  " + name); }
        public void close() { System.out.println("closed " + name); }
    }

    public static void main(String[] args) {
        System.out.println("=== Normal case: the block finishes ===");
        try (Resource a = new Resource("A (like Connection)");
             Resource b = new Resource("B (like PreparedStatement)")) {
            a.use();
            b.use();
        }

        System.out.println("\n=== Failure case: the body throws halfway ===");
        try {
            try (Resource a = new Resource("A (like Connection)");
                 Resource b = new Resource("B (like PreparedStatement)")) {
                a.use();
                throw new RuntimeException("boom while using resources");
            }
        } catch (Exception e) {
            System.out.println("caught: " + e.getMessage());
        }
    }
}

public class Concept6Indent {

    // Prints every line wrapped in [ ] so leading spaces become visible.
    static void show(String label, String s) {
        System.out.println("--- " + label + " ---");
        for (String line : s.split("\n", -1)) {
            System.out.println("[" + line + "]");
        }
    }

    public static void main(String[] args) {
        // Closing delimiter aligned WITH the content:
        // the whole common indent is "incidental" and gets stripped.
        String a = """
                hello
                  world
                """;

        // Closing delimiter moved LEFT of the content (column 0):
        // now the delimiter defines the margin, and the content keeps its spaces.
        String b = """
                hello
                  world
""";

        show("a (closing quotes aligned with content)", a);
        show("b (closing quotes at column 0)", b);
    }
}

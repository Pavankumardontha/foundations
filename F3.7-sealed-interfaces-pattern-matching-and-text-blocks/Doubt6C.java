public class Doubt6C {

    // Prints the string but shows every space as a dot,
    // so we can SEE spaces that are normally invisible.
    static void showSpaces(String label, String s) {
        System.out.println("--- " + label + " ---");
        System.out.print(s.replace(" ", "."));
        System.out.println("(end)");
    }

    public static void main(String[] args) {
        // Block 1: both lines start at the same column in the code.
        String one = """
                hello
                world
                """;

        // Block 2: 'world' is pushed 2 spaces further right than 'hello'.
        String two = """
                hello
                  world
                """;

        // Block 3: same as block 1, but the closing \"\"\" is moved
        // all the way to the left edge of the file.
        String three = """
                hello
                world
""";

        showSpaces("one", one);
        showSpaces("two", two);
        showSpaces("three", three);
    }
}

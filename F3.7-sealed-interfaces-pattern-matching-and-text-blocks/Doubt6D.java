public class Doubt6D {

    public static void main(String[] args) {
        // Closing """ on its OWN line.
        String a = """
                hello
                world
                """;

        // Closing """ stuck to the END of the last line.
        String b = """
                hello
                world""";

        // Printing between [ and ] makes an ending line break visible:
        // if the string ends with a line break, the ] lands on the next line.
        System.out.println("[" + a + "]");
        System.out.println("[" + b + "]");
    }
}

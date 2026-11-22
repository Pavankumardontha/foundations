public class Concept6Newline {

    public static void main(String[] args) {
        // Closing """ on its OWN line -> the string ends with \n
        String withNewline = """
                line1
                line2
                """;

        // Closing """ on the SAME line as the last content -> no trailing \n
        String noNewline = """
                line1
                line2""";

        // Backslash at end of line JOINS it with the next line (no \n inserted)
        String joined = """
                line1 \
                line2""";

        System.out.println("withNewline ends with \\n? " + withNewline.endsWith("\n"));
        System.out.println("noNewline   ends with \\n? " + noNewline.endsWith("\n"));
        System.out.println("joined = [" + joined + "]");
    }
}

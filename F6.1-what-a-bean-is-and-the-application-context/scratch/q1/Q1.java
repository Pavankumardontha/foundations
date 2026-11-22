class NameFormatter {
    String shout(String name) {
        return name.toUpperCase() + "!";
    }
}

class Greeter {
    private final NameFormatter formatter;

    Greeter(NameFormatter formatter) {
        this.formatter = formatter;
    }

    String greet(String name) {
        return "Hello " + formatter.shout(name);
    }
}

public class Q1 {
    public static void main(String[] args) {
        NameFormatter formatter = new NameFormatter();
        Greeter greeter = new Greeter(formatter);
        System.out.println(greeter.greet("ava"));
    }
}

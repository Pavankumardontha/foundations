public class Concept7A {
    public static void main(String[] args) {
        String name = "pavan";

        name.toUpperCase();                    // result thrown away
        System.out.println("1: " + name);

        String loud = name.toUpperCase();      // result kept in a NEW variable
        System.out.println("2: " + loud);
        System.out.println("3: " + name);
    }
}

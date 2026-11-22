// WITHOUT @Override: a typo creates a silently broken program that compiles fine.

class Animal {
    String sound() {
        return "...generic animal sound...";
    }
}

class Dog extends Animal {
    // Programmer INTENDS to override sound(), but typo: capital S.
    // This silently becomes a brand-new, unrelated method. No error anywhere.
    String Sound() {
        return "Woof!";
    }
}

public class OverrideSilent {
    public static void main(String[] args) {
        Animal a = new Dog();
        System.out.println(a.sound());   // expected "Woof!" ... but what do we get?
    }
}

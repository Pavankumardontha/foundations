// Concept 7: the anti-pattern — isPresent() + get() is a null check in a costume.

import java.util.Optional;

public class Concept7 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    static void sendEmailAbout(Event e) {
        System.out.println("sending email about: " + e.name());
    }

    public static void main(String[] args) {
        // THE ANTI-PATTERN. It works. That is not the point.
        Optional<Event> opt = findById("E1");
        if (opt.isPresent()) {           // "is the box full?"  (yes/no question)
            sendEmailAbout(opt.get());   // "then open it"      (the forbidden method)
        }

        // Rewrite 1: ifPresent — same behaviour, one line, no get().
        findById("E1").ifPresent(e -> sendEmailAbout(e));

        // Rewrite 1b: same thing with a method reference.
        findById("E1").ifPresent(Concept7::sendEmailAbout);

        // Rewrite 2: when absence is an error — map + orElseThrow chain.
        String name = findById("E1")
                .map(e -> e.name())
                .orElseThrow(() -> new IllegalStateException("No event with id E1"));
        System.out.println("chain gave: " + name);
    }
}

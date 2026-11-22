import java.io.IOException;

// The two kinds of Java exceptions: checked and unchecked.
// Run with: java ExceptionKinds.java
public class ExceptionKinds {

    // Checked: its family line has Exception, but not RuntimeException.
    static class TicketsSoldOutException extends Exception {
        TicketsSoldOutException(String message) {
            super(message);
        }
    }

    // Unchecked: its family line has RuntimeException.
    static class VenueClosedException extends RuntimeException {
        VenueClosedException(String message) {
            super(message);
        }
    }

    // It can throw a checked exception, so the compiler demands "throws" here.
    static void buyTicket(int ticketsLeft) throws TicketsSoldOutException {
        if (ticketsLeft == 0) {
            throw new TicketsSoldOutException("no tickets left");
        }
        System.out.println("ticket bought");
    }

    // It can throw an unchecked exception. No "throws" is needed.
    static void openDoors(boolean venueOpen) {
        if (!venueOpen) {
            throw new VenueClosedException("the venue is closed");
        }
        System.out.println("doors open");
    }

    // The family line: the class, its parent, the parent's parent, and so on, up to Object.
    static String family(Throwable t) {
        StringBuilder chain = new StringBuilder(t.getClass().getSimpleName());
        for (Class<?> c = t.getClass().getSuperclass(); c != null; c = c.getSuperclass()) {
            chain.append(" -> ").append(c.getSimpleName());
        }
        return chain.toString();
    }

    public static void main(String[] args) {
        try {
            buyTicket(0);
        } catch (TicketsSoldOutException e) {
            System.out.println("caught a checked exception: " + e.getMessage());
        }

        try {
            openDoors(false);
        } catch (VenueClosedException e) {
            System.out.println("caught an unchecked exception: " + e.getMessage());
        }

        Throwable[] samples = {
                new TicketsSoldOutException("x"),
                new VenueClosedException("x"),
                new IOException("x"),
                new IllegalStateException("x"),
                new OutOfMemoryError("x"),
        };
        for (Throwable t : samples) {
            boolean unchecked = t instanceof RuntimeException || t instanceof Error;
            System.out.println((unchecked ? "unchecked: " : "checked:   ") + family(t));
        }
    }
}

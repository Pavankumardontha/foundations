interface Notifier {
    void notifyUser(String message);
}

class EmailNotifier implements Notifier {
    @Override
    public void notifyUser(String message) {
        System.out.println("emailing: " + message);
    }
}

class SilentNotifier implements Notifier {
    @Override
    public void notifyUser(String message) {
        // does nothing, on purpose (for tests)
    }
}

class SignupService {
    private final EmailNotifier notifier;   // look closely at this type

    SignupService(EmailNotifier notifier) {
        this.notifier = notifier;
    }

    void register(String user) {
        notifier.notifyUser("welcome " + user);
    }
}

public class Q2L3 {
    public static void main(String[] args) {
        SignupService service = new SignupService(new SilentNotifier());
        service.register("ava");
    }
}

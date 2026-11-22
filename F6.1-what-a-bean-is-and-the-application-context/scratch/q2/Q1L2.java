class SmtpClient {
    private final String host;
    private final int port;

    SmtpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void deliver(String text) {
        System.out.println("sending via " + host + ":" + port + " -> " + text);
    }
}

class MailSenderA {
    private final SmtpClient client;

    MailSenderA(SmtpClient client) {
        this.client = client;
    }

    void send(String text) {
        client.deliver(text);
    }
}

class MailSenderB {
    private final SmtpClient client = new SmtpClient("smtp.company.com", 587);

    void send(String text) {
        client.deliver(text);
    }
}

public class Q1L2 {
    public static void main(String[] args) {
        new MailSenderA(new SmtpClient("smtp.test.local", 2525)).send("hi");
        new MailSenderB().send("hi");
    }
}

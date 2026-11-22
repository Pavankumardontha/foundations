// Lesson 2 — attaching the label, and watching it do nothing.
public class Demo {

    @Timed
    static void work() throws InterruptedException {
        Thread.sleep(100);                   // pretend work
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws InterruptedException {
        work();
    }
}

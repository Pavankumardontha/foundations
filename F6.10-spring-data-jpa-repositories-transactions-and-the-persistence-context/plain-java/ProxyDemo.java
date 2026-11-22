import java.lang.reflect.Proxy;

// An object for an interface that no class implements - made while the program runs.
// Run with: java ProxyDemo.java
public class ProxyDemo {

    public interface Greeter {
        String hello(String name);
        String bye(String name);
    }

    public static void main(String[] args) {
        // No class anywhere implements Greeter. We ask Java to make one, right now.
        Greeter greeter = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] { Greeter.class },
                (proxy, method, methodArgs) -> {              // the handler: every call lands here
                    System.out.println("  handler got: " + method.getName() + "(" + methodArgs[0] + ")");
                    return method.getName() + ", " + methodArgs[0] + "!";
                });

        System.out.println(greeter.hello("Meera"));
        System.out.println(greeter.bye("Arjun"));
        System.out.println("class of greeter: " + greeter.getClass().getName());
    }
}

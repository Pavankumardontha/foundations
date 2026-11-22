package com.zenith.catalog;

import java.util.List;

/** The test that was impossible before the conversion. Now it is three lines. */
public class WiringAttempt {

    public static void main(String[] args) {
        List<String> wanted = List.of("Fake Gig", "Fake Festival");

        EventController controller =
                new EventController(new EventService(new FakeEventRepository()));
        List<String> got = controller.events();

        System.out.println("wanted: " + wanted);
        System.out.println("got:    " + got);
        System.out.println(got.equals(wanted) ? "TEST PASSED" : "TEST FAILED");
    }
}

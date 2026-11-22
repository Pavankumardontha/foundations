package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 16: the two faces of @Data's toString() on an entity.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l16-data-tostring
@Component
@Profile("l16-data-tostring")
class L16DataToStringDemo implements CommandLineRunner {

    private static final UUID MONSOON = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID RIVERSIDE_FLOOR = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private final BadEventRepository badEvents;
    private final BadSeatMapRepository badSeatMaps;

    L16DataToStringDemo(BadEventRepository badEvents, BadSeatMapRepository badSeatMaps) {
        this.badEvents = badEvents;
        this.badSeatMaps = badSeatMaps;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        // FACE 1: toString() touches a lazy field -> a surprise query, just from logging.
        System.out.println(">>> FACE 1 -----------------------------------------------");
        System.out.println(">>> load one BadEventEntity (watch: 1 select) ...");
        BadEventEntity event = badEvents.findById(MONSOON).orElseThrow();
        System.out.println(">>> now we only LOG it - watch a SECOND select appear ...");
        System.out.println(">>> " + event);
        System.out.println(">>> ^ that select fired because toString() read the lazy venue");

        // FACE 2: bidirectional toString() recurses -> StackOverflowError.
        System.out.println(">>> FACE 2 -----------------------------------------------");
        System.out.println(">>> load one BadSeatMapEntity and its seats ...");
        BadSeatMapEntity map = badSeatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        map.getSeats().size();   // force the seats to load, inside the transaction
        System.out.println(">>> now we only LOG the seat map - watch it blow the stack ...");
        try {
            System.out.println(map.toString());
        } catch (StackOverflowError e) {
            System.out.println(">>> StackOverflowError caught: toString went");
            System.out.println(">>>   seatMap -> seats -> seat -> seatMap -> seats -> ... forever");
        }
    }
}

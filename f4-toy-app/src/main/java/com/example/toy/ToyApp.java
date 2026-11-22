package com.example.toy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@RestController
public class ToyApp {

    public static void main(String[] args) {
        SpringApplication.run(ToyApp.class, args);
    }

    record HelloResponse(String message, int id) {}

    @GetMapping("/hello")
    HelloResponse hello() {
        return new HelloResponse("hello", 1);
    }

    // --- Task F4.2 toy endpoints (annotations explained properly in F4.7) ---

    record ToyEvent(String name) {}

    private final List<ToyEvent> events = new ArrayList<>();

    @PostMapping("/events")
    ResponseEntity<?> create(@RequestBody ToyEvent event) {
        if (event.name() == null || event.name().isBlank()) {
            return ResponseEntity.unprocessableEntity()              // 422: read it fine,
                .body(Map.of("error", "name must not be blank"));    // but invalid values
        }
        events.add(event);
        return ResponseEntity
            .created(URI.create("/events/" + event.name()))  // 201 + Location: a URL
            .body(event);                                    // you can actually GET
    }

    // --- Lesson 21 demo: 409 Conflict — holding a seat somebody already holds ---

    record Holder(String name) {}

    private final Map<String, String> seatHolds = new HashMap<>(); // seat -> holder

    @PostMapping("/seats/{seat}/hold")
    ResponseEntity<?> holdSeat(@PathVariable String seat, @RequestBody Holder holder) {
        if (seatHolds.containsKey(seat)) {
            return ResponseEntity.status(409)                        // conflict with
                .body(Map.of("error", "seat " + seat + " is already held"));  // current state
        }
        seatHolds.put(seat, holder.name());
        return ResponseEntity.status(201)
            .body(Map.of("seat", seat, "heldBy", holder.name()));
    }

    // --- Lesson 22 demo: 401 vs 403 vs 404-for-secrets ---
    // Toy identity: "Authorization: Bearer alice" just carries a name.
    // Real tokens are cryptographic and arrive in Phase 2.

    record OrganizerEvent(String name, String owner, boolean published) {}

    private final Map<Integer, OrganizerEvent> organizerEvents = new HashMap<>(Map.of(
        1, new OrganizerEvent("Coldplay Live", "alice", true),
        2, new OrganizerEvent("Secret Launch Party", "bob", false)));

    @GetMapping("/organizer-events/{id}")
    ResponseEntity<?> getOrganizerEvent(@PathVariable int id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null) {
            return ResponseEntity.status(401)                    // I do not know who you are
                .header("WWW-Authenticate", "Bearer")
                .body(Map.of("error", "no credentials"));
        }
        String user = auth.replace("Bearer ", "");
        OrganizerEvent event = organizerEvents.get(id);
        if (event == null || (!event.published() && !event.owner().equals(user))) {
            return ResponseEntity.status(404)                    // hide that it exists
                .body(Map.of("error", "no such event"));
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())                // personal: never store
            .body(event);
    }

    // --- Lesson 37 demo: an action that is not CRUD, scoped under its noun ---

    @PostMapping("/organizer-events/{id}/publish")
    ResponseEntity<?> publishOrganizerEvent(@PathVariable int id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null) {
            return ResponseEntity.status(401)
                .header("WWW-Authenticate", "Bearer")
                .body(Map.of("error", "no credentials"));
        }
        String user = auth.replace("Bearer ", "");
        OrganizerEvent event = organizerEvents.get(id);
        if (event == null || (!event.published() && !event.owner().equals(user))) {
            return ResponseEntity.status(404)                // hidden or missing
                .body(Map.of("error", "no such event"));
        }
        if (!event.owner().equals(user)) {
            return ResponseEntity.status(403)                // known, not allowed
                .body(Map.of("error", "only the owner may publish this event"));
        }
        if (event.published()) {
            return ResponseEntity.status(409)                // conflict with state
                .body(Map.of("error", "event is already published"));
        }
        OrganizerEvent published = new OrganizerEvent(event.name(), event.owner(), true);
        organizerEvents.put(id, published);
        return ResponseEntity.ok(published);
    }

    @DeleteMapping("/organizer-events/{id}")
    ResponseEntity<?> deleteOrganizerEvent(@PathVariable int id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null) {
            return ResponseEntity.status(401)
                .header("WWW-Authenticate", "Bearer")
                .body(Map.of("error", "no credentials"));
        }
        String user = auth.replace("Bearer ", "");
        OrganizerEvent event = organizerEvents.get(id);
        if (event == null || (!event.published() && !event.owner().equals(user))) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "no such event"));
        }
        if (!event.owner().equals(user)) {
            return ResponseEntity.status(403)                    // I know you; you may not
                .body(Map.of("error", "only the owner may delete this event"));
        }
        organizerEvents.remove(id);
        return ResponseEntity.noContent().build();
    }

    // --- Lesson 19 demo: 202 Accepted for work that finishes later ---

    record RefundStatus(String status) {}

    @PostMapping("/refunds")
    ResponseEntity<RefundStatus> startRefund() {
        return ResponseEntity
            .accepted()                        // 202: accepted, still working on it
            .header("Location", "/refunds/1") // where to check progress
            .body(new RefundStatus("processing"));
    }

    // The GET /events/{id}/cancel endpoint lived here for the Lesson 17 experiment.
    // Deleted on purpose, as Task F4.2 instructs: a GET must never change state.

    // --- Lesson 18 demo: a handler with a bug, to see a real 5xx response ---

    @GetMapping("/boom")
    String boom() {
        throw new RuntimeException("simulated bug in the handler");
    }

    // --- Lesson 23 demo: slow backend (for the gateway's 504) and 503 ---

    @GetMapping("/slow")
    String slow() throws InterruptedException {
        Thread.sleep(10_000);                    // pretend a query takes 10 seconds
        return "finally done";
    }

    @GetMapping("/busy")
    ResponseEntity<?> busy() {
        return ResponseEntity.status(503)        // temporarily cannot serve
            .header("Retry-After", "30")         // come back in 30 seconds
            .body(Map.of("error", "temporarily overloaded, try again shortly"));
    }

    // --- Lesson 20 demo: redirects ---

    @GetMapping("/ancient-hello")
    ResponseEntity<Void> ancientHello() {
        return ResponseEntity.status(301)                 // moved for good
            .header("Location", "/hello").build();
    }

    @GetMapping("/old-hello")
    ResponseEntity<Void> oldHello() {
        return ResponseEntity.status(302)                 // temporarily elsewhere
            .header("Location", "/hello").build();
    }

    // --- Lesson 32 demo: query parameters filter the set; the path identifies ---

    private static final int MAX_LIMIT = 3;      // server-side cap (absurdly small
                                                 // on purpose, so we can see it work)

    @GetMapping("/events")
    List<ToyEvent> listEvents(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        int effectiveLimit = Math.min(limit != null ? limit : 20, MAX_LIMIT);
        int effectiveOffset = offset != null ? offset : 0;
        return events.stream()
            .filter(e -> q == null || e.name().contains(q))  // 1. filter
            .skip(effectiveOffset)                           // 2. skip past pages
            .limit(effectiveLimit)                           // 3. take one page
            .toList();
    }

    // --- Lesson 35 demo: one level of nesting, and the flat alternative ---

    record Seat(String id, boolean held) {}

    private List<Seat> seatsFor(String eventName) {      // pretend seat map, fixed
        return List.of(new Seat("H1", false), new Seat("H2", true), new Seat("J1", false));
    }

    @GetMapping("/events/{name}/seats")
    ResponseEntity<?> seatsForEvent(@PathVariable String name) {
        if (events.stream().noneMatch(e -> e.name().equals(name))) {
            return ResponseEntity.status(404)            // resolve the parent FIRST
                .body(Map.of("error", "no event named " + name));
        }
        return ResponseEntity.ok(seatsFor(name));        // one level of nesting: fine
    }

    @GetMapping("/seats")
    ResponseEntity<?> findSeats(@RequestParam("event") String event,
            @RequestParam(value = "row", required = false) String row) {
        if (events.stream().noneMatch(e -> e.name().equals(event))) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "no event named " + event));
        }
        List<Seat> result = seatsFor(event).stream()
            .filter(s -> row == null || s.id().startsWith(row))
            .toList();
        return ResponseEntity.ok(result);                // deep access, flat address
    }

    // --- Lesson 24 demo: an honest lookup, soon to be broken on purpose ---

    @GetMapping("/events/{name}")
    ResponseEntity<?> getEvent(@PathVariable String name) {
        for (ToyEvent e : events) {
            if (e.name().equals(name)) {
                return ResponseEntity.ok(e);
            }
        }
        return ResponseEntity.status(404)                       // honest: error on the
            .body(Map.of("error", "no event named " + name));   // status line itself
    }

    // --- Lesson 44 demo: null vs absent, and why PATCH is hard ---

    static String profileName = "Pavan";
    static String profileNickname = "PK";

    @GetMapping("/profile")
    Map<String, String> profile() {
        Map<String, String> m = new HashMap<>();
        m.put("name", profileName);
        m.put("nickname", profileNickname);         // may be null after a clear
        return m;
    }

    record ProfilePatch(String name, String nickname) {}

    @PatchMapping("/profile")                       // the naive PATCH
    Map<String, String> patchProfile(@RequestBody ProfilePatch p) {
        if (p.name() != null) profileName = p.name();
        if (p.nickname() != null) profileNickname = p.nickname();
        return profile();
    }

    @PatchMapping("/profile-v2")                    // presence-aware PATCH
    Map<String, String> patchProfileV2(@RequestBody Map<String, Object> body) {
        if (body.containsKey("name")) profileName = (String) body.get("name");
        if (body.containsKey("nickname")) profileNickname = (String) body.get("nickname");
        return profile();
    }

    // --- Lesson 43 demo: wrong types in the body ---

    record TicketOrder(String event, int quantity, BigDecimal price) {}

    @PostMapping("/ticket-orders")
    TicketOrder ticketOrder(@RequestBody TicketOrder t) {
        return t;                                    // echo what Jackson built
    }

    // --- Lesson 42 demo: mass assignment, and the silent default ---

    record NaiveUser(String name, String role) {}    // role should NOT be client-settable

    @PostMapping("/naive-signup")
    NaiveUser naiveSignup(@RequestBody NaiveUser u) {
        return new NaiveUser(u.name(),               // binds whatever the client sent,
            u.role() != null ? u.role() : "USER");   // including a role
    }

    record SignUp(String name) {}                    // DTO: only what clients MAY set

    @PostMapping("/signup")
    NaiveUser signup(@RequestBody SignUp s) {
        return new NaiveUser(s.name(), "USER");      // role decided by the server
    }

    // --- Lesson 41 demo: ten 10p booking fees, totalled two ways ---

    record PriceDemo(double wrongTotal, BigDecimal rightTotal) {}

    @GetMapping("/price-demo")
    PriceDemo priceDemo() {
        double wrong = 0;
        for (int i = 0; i < 10; i++) {
            wrong += 0.10;                               // ten 10p fees, as double
        }
        BigDecimal right = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) {
            right = right.add(new BigDecimal("0.10"));   // the same, exactly
        }
        return new PriceDemo(wrong, right);
    }

    // --- Lesson 40 demo: three time types, three wire shapes ---

    record TimeDemo(Instant instant, LocalDateTime localDateTime, LocalDate localDate) {}

    @GetMapping("/time-demo")
    TimeDemo timeDemo() {
        return new TimeDemo(
            Instant.parse("2026-07-14T19:30:00Z"),           // a moment, pinned to UTC
            LocalDateTime.parse("2026-07-14T19:30:00"),      // "7:30pm" ... where?
            LocalDate.parse("2026-07-14"));                  // a date, no time at all
    }

    // --- Lesson 39 demo: controlling the wire shape ---

    record ShowTime(
            @JsonProperty("starts_at") String startsAt,  // wire name != Java name
            @JsonIgnore String internalNote,             // never leaves the server
            String venue) {}

    @GetMapping("/showtime")
    ShowTime showtime() {
        return new ShowTime("2026-07-14T19:30:00Z", "VIP list in drawer 3", "Grand Hall");
    }

    @PostMapping("/showtimes")
    ShowTime createShowtime(@RequestBody ShowTime st) {
        return st;                                       // echo what Jackson built
    }

    // --- Lesson 30 demo: identity travels in EVERY request, or not at all ---

    @GetMapping("/whoami")
    ResponseEntity<?> whoami(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null) {
            return ResponseEntity.status(401)
                .header("WWW-Authenticate", "Bearer")
                .body(Map.of("error", "no credentials"));
        }
        String user = auth.replace("Bearer ", "");   // toy token: just a name
        return ResponseEntity.ok(Map.of("you", user));
    }

    // --- Lesson 29 demo: Retry-After on a rate limit, and Idempotency-Key ---

    private final List<Instant> recentCalls = new ArrayList<>();

    @GetMapping("/limited")
    ResponseEntity<?> limited() {
        Instant now = Instant.now();
        recentCalls.removeIf(t -> t.isBefore(now.minusSeconds(10)));
        if (recentCalls.size() >= 3) {                       // allow 3 calls per 10s
            return ResponseEntity.status(429)
                .header("Retry-After", "10")                 // when to come back
                .body(Map.of("error", "too many requests, slow down"));
        }
        recentCalls.add(now);
        return ResponseEntity.ok(Map.of("message", "served"));
    }

    record Order(int id, String item) {}

    private final List<Order> orders = new ArrayList<>();
    private final Map<String, Order> ordersByKey = new HashMap<>(); // key -> first result

    @PostMapping("/orders")
    ResponseEntity<Order> createOrder(@RequestBody Map<String, String> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        if (key != null && ordersByKey.containsKey(key)) {
            Order existing = ordersByKey.get(key);           // seen this exact request:
            return ResponseEntity.status(201)                // replay the original answer,
                .location(URI.create("/orders/" + existing.id()))
                .body(existing);                             // do NOT create again
        }
        Order order = new Order(orders.size() + 1, body.get("item"));
        orders.add(order);
        if (key != null) {
            ordersByKey.put(key, order);                     // remember: key -> result
        }
        return ResponseEntity.status(201)
            .location(URI.create("/orders/" + order.id()))
            .body(order);
    }

    @GetMapping("/orders")
    List<Order> listOrders() {
        return orders;
    }

    // --- Lesson 15 demo: DELETE is idempotent even though its replies differ ---

    @DeleteMapping("/events/{name}")
    ResponseEntity<Void> deleteEvent(@PathVariable String name) {
        boolean removed = events.removeIf(e -> e.name().equals(name));
        if (removed) {
            return ResponseEntity.noContent().build();   // 204: done, nothing to show
        }
        return ResponseEntity.notFound().build();        // 404: nothing here by that name
    }

    // --- Lesson 16 demo: PUT replaces the whole resource, PATCH changes parts ---

    record Venue(String name, String city) {}

    private Venue venue = new Venue("Old Hall", "Leeds");
    private Instant venueLastModified = Instant.now();   // when the venue last changed

    @GetMapping("/venue")
    ResponseEntity<Venue> getVenue(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        String etag = "\"v-" + Integer.toHexString(venue.hashCode()) + "\"";  // label FROM content
        if (ifNoneMatch != null) {                           // ETag check wins when both sent
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).eTag(etag).build();  // same version; no body
            }
        } else if (ifModifiedSince != null) {
            Instant clientHas = Instant.from(
                DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince));
            if (!venueLastModified.truncatedTo(ChronoUnit.SECONDS).isAfter(clientHas)) {
                return ResponseEntity.status(304).build();   // nothing newer; no body
            }
        }
        return ResponseEntity.ok()
            .eTag(etag)                                      // version label, no clock in it
            .lastModified(venueLastModified)                 // timestamp label
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS)) // reuse for 60s
            .body(venue);
    }

    @PutMapping("/venue")
    Venue putVenue(@RequestBody Venue replacement) {
        venue = replacement;                 // the body IS the new venue, whole and entire
        venueLastModified = Instant.now();   // the label moves forward
        return venue;
    }

    @PatchMapping("/venue")
    Venue patchVenue(@RequestBody Venue changes) {
        venue = new Venue(                   // keep old values for fields not mentioned
            changes.name() != null ? changes.name() : venue.name(),
            changes.city() != null ? changes.city() : venue.city());
        venueLastModified = Instant.now();   // the label moves forward
        return venue;
    }
}

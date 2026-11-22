# F7.5 — Testing a Spring application with slices

**Learning outcome.** Choose between `@SpringBootTest`, `@WebMvcTest`, and `@DataJpaTest` for a
given scenario, defend the choice with a measured number, and assert on a JSON response with
`MockMvc`.

The app under test lives in `scratch-app/`: it is the F6.8 validation app plus a service layer
(`EventService`), a real database table (`EventEntity` + `EventRepository` on H2), and a 404
handler for `EventNotFoundException`. Run everything with `mvn test` from inside `scratch-app`.

> **Gap note.** Task F7.4 (Mockito in practice) was skipped — there are no F7.4 notes. Lesson 5
> of this task teaches the minimal Mockito that F7.5 needs: `mock()`,
> `when(...).thenReturn(...)`, `when(...).thenThrow(...)`, and `any()`. The full F7.4 task can
> still be done separately later.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Test slice** | An annotation that starts only part of the application context. Fewer beans, faster start. |
| **`@SpringBootTest`** | Starts the whole application context, including the database connection and every bean. |
| **`@WebMvcTest`** | Starts the web layer only: controllers, validation, your `@RestControllerAdvice`, and the JSON converters. No database. |
| **`@DataJpaTest`** | Starts the persistence layer only: entities, repositories, and a datasource. No controllers. |
| **`MockMvc`** | Sends fake HTTP requests into your controllers without opening a real port. |
| **`@MockitoBean`** | Replaces one bean inside a Spring test context with a Mockito mock. |
| **Context caching** | Spring reusing a started context between test classes that ask for an identical configuration. |
| **`jsonPath`** | A small expression language for picking values out of a JSON response, such as `$.name`. |

## The task's subtasks

- **S1** — Write the 404 assertion ("GET /events/{id} returns 404 for an unknown id") with
  `@SpringBootTest` and a real context. Note what it needed.
- **S2** — Write the same 404 assertion with `@WebMvcTest(EventController.class)`,
  `@MockitoBean EventService`, and `MockMvc`.
- **S3** — Measure both: the startup time each test class prints, and the total `mvn test`
  time. Write both numbers in `NOTES.md`.
- **S4** — Say the consequence out loud: multiply the gap by three hundred test classes.
- **S5** — Use `@WebMvcTest` for the F6.8 behaviour: validation failures give 400 with the
  right field names, domain exceptions give 409 or 422, and the error body has the designed shape.
- **S6** — Use `@DataJpaTest` for a repository test: save two events for one venue, query with
  a derived method, assert. Note the rollback after each test.
- **S7** — Note the limitation: `@DataJpaTest` uses an embedded database, which is not Postgres.
- **S8** — Keep `@SpringBootTest` for one end-to-end happy path only. Write one, stop there.
- **S9** — Understand context caching, and how careless setup defeats it.
- **S10** — Ask AI for five testing scenarios and pick the right annotation for each.
- **CP** — The five checkpoint questions.

Reading note: the task says "write the same assertion three ways", then lists only two ways for
the 404. The third kind is `@DataJpaTest`, which cannot see HTTP at all — so it proves a
repository fact instead. The "done when" line confirms this reading: one passing test of each kind.

## Roadmap for this task — 15 small lessons

| # | Lesson | What it gives you | Serves |
|---|---|---|---|
| 1 | Where a 404 is born | Why a plain JUnit test cannot see web behaviour. | S1, S2 |
| 2 | `@SpringBootTest` | A test that starts the whole application, and what that costs. | S1 |
| 3 | `MockMvc` | Sending a fake HTTP request from a test and checking the status. | S1, S2 |
| 4 | `jsonPath` | Checking fields inside the JSON body. Completes the `@SpringBootTest` 404 test. | S1 |
| 5 | Mockito in five lines | `mock()`, `when(...).thenReturn(...)`, `thenThrow(...)`, `any()`. | S2 |
| 6 | The test slice idea | Starting only part of the application; "narrowest test that proves it". | S2, S5–S8 |
| 7 | `@WebMvcTest` | The web slice — shown by the bean it refuses to create. | S2 |
| 8 | `@MockitoBean` | Filling the missing bean with a mock. Completes the `@WebMvcTest` 404 test. | S2 |
| 9 | Measure and record | The two numbers in `NOTES.md`, and the ×300 argument. | S3, S4 |
| 10 | `@WebMvcTest` for F6.8's error work | Validation 400 with field names, domain 409/422, one error shape. | S5 |
| 11 | `@DataJpaTest` | A repository test with a derived query, and the rollback. | S6 |
| 12 | The embedded-database trap | Where H2 and Postgres disagree; why Testcontainers comes later. | S7 |
| 13 | One end-to-end happy path | The only `@SpringBootTest` you keep, and why only one. | S8 |
| 14 | Context caching | Why some test classes start in milliseconds; how setup defeats it. | S9 |
| 15 | The decision drill | Five scenarios, pick and defend the annotation. Checkpoint answers. | S10, CP |

## Lesson 1 — Where a 404 is born, and why a plain test cannot see it (serves S1, S2)

### Definition

Tests so far live in the **method-call world**: the test calls a Java method and checks what
comes back. "GET /events/{id} returns 404" is a fact about the **HTTP world**: status codes,
JSON bodies, and error mapping. Those are made by Spring machinery that a plain test never starts.

### Why it is needed

F6.8 built one error shape for the whole API: a record named `ApiError` with five fields
(`errorId`, `status`, `code`, `message`, `fieldErrors`), filled in by a class marked
`@RestControllerAdvice`.

Re-explanation of that annotation: `@RestControllerAdvice` marks a class that watches all
controllers. When a controller throws an exception, Spring looks in that class for a method
marked `@ExceptionHandler(SomeException.class)`. Spring calls that method, and whatever it
returns becomes the HTTP response.

That machinery is real behaviour. Clients depend on it: a frontend checks `status == 404` to
show a "not found" page. If someone breaks the mapping, every plain JUnit test stays green
(proven in the walkthrough below — the answer changes from 404 to 422 and no test notices).
So we need tests that run the HTTP world. The three annotations in this task are three ways to
start Spring machinery inside a test. They differ in how much they start.

### Key points

- A plain JUnit test can prove: "this method returns X" or "this method throws Y". Nothing more.
- Status codes and JSON bodies do not exist in the method-call world. They are created by
  Spring's web machinery: routing, exception handlers, JSON converters.
- Protecting web behaviour needs a test that starts that machinery.
- The opposite extreme is also wrong: service and domain logic tests should stay plain and fast.
  Spring is only for behaviour that Spring itself produces.

### How it works — the journey of a 404

The app pieces involved (F7.5 scratch app):

The exception — thrown when an id matches nothing:

```java
class EventNotFoundException extends DomainException {

    EventNotFoundException() {
        super("event_not_found", "Event not found");
    }
}
```

The service — looks the event up in the database, throws if absent:

```java
EventResponse byId(UUID id) {
    EventEntity entity = repository.findById(id)
            .orElseThrow(EventNotFoundException::new);
    return toResponse(entity);
}
```

The controller — thin, just delegates:

```java
@GetMapping("/{id}")
EventResponse byId(@PathVariable("id") UUID eventId) {
    return service.byId(eventId);
}
```

The handler — the only place in the whole codebase where this 404 exists:

```java
// 404: the id is well-formed but names nothing. More specific than the
// DomainException handler below, so Spring picks this one for this type.
@ExceptionHandler(EventNotFoundException.class)
ResponseEntity<ApiError> onEventNotFound(EventNotFoundException ex) {
    ApiError body = new ApiError(
            UUID.randomUUID().toString(),
            404,
            ex.code(),
            ex.getMessage(),
            List.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}
```

When a real request arrives, six steps happen:

1. An HTTP request `GET /events/1111...` arrives at the server.
2. Spring routes it: reads the path, finds `EventController.byId`, converts the path text into
   a `UUID`.
3. The controller calls `service.byId(eventId)`.
4. The database has no such row, so the service throws `EventNotFoundException`.
5. The exception flies out of the controller. Spring catches it and finds `onEventNotFound` in
   the advice class. That method builds the `ApiError` and picks status 404.
6. Jackson (the JSON converter) turns the `ApiError` into JSON text, and the response goes out.

A plain test calls `controller.byId(id)` directly: only steps 3 and 4 happen. No request, no
routing, no catching, no status, no JSON. The exception lands in the test itself.

### The example — the most honest plain test possible

Note on the stub: in F7.3 the hand-written doubles implemented an interface. `EventService` is
a class, so here the stub is a tiny subclass that overrides one method. Same idea: a stand-in
with scripted behaviour.

```java
package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

// Lesson 1: a plain JUnit test, no Spring anywhere.
// It can prove "byId throws EventNotFoundException".
// It CANNOT prove "the API answers 404 with our error JSON" —
// no status code and no JSON exist in this world.
class L1PlainJUnitTest {

    @Test
    void byIdThrowsWhenEventDoesNotExist() {
        // A hand-written stub (the F7.3 idea): a stand-in EventService
        // whose byId always throws. The repository is never touched,
        // so passing null is safe here.
        EventService stub = new EventService(null) {
            @Override
            EventResponse byId(UUID id) {
                throw new EventNotFoundException();
            }
        };
        EventController controller = new EventController(stub);

        assertThrows(EventNotFoundException.class,
                () -> controller.byId(UUID.randomUUID()));
    }
}
```

### Walkthrough — three real runs

**Run 1: the plain test.** `mvn test`, real output trimmed:

```text
[INFO] Running com.zenith.events.L1PlainJUnitTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.032 s
[INFO] BUILD SUCCESS
```

Green, and fast. It proved one thing: `byId` lets the exception pass through. The test cannot
even mention `404` or JSON — those words have no meaning in the method-call world.

**Run 2: the real thing.** App started with `mvn spring-boot:run`, then:

```text
$ curl -si http://localhost:8080/events/11111111-2222-3333-4444-555555555555
HTTP/1.1 404
Content-Type: application/json

{"errorId":"f7f20bd7-...","status":404,"code":"event_not_found","message":"Event not found","fieldErrors":[]}
```

The 404 and the F6.8 error shape. This is the behaviour worth protecting.

**Run 3: break it, and watch the plain test stay green.** With the `onEventNotFound` handler
commented out and the app restarted, the same request gives:

```text
HTTP/1.1 422
Content-Type: application/json

{"errorId":"dce92c9a-...","status":422,"code":"event_not_found","message":"Event not found","fieldErrors":[]}
```

Why 422? `EventNotFoundException` extends `DomainException`. With its own handler gone, the
general `DomainException` handler caught it, and that one answers 422 ("the request breaks a
business rule"). That is a lie to the client: the id is not a broken rule, it just names
nothing. Every frontend checking for 404 now misbehaves. And `mvn test` stayed green, 1 of 1
passing — the plain test never touches the machinery that chose the status.

### Important terms

| Term | Meaning |
| --- | --- |
| Method-call world | What a plain test sees: arguments in, return value or exception out. |
| HTTP world | Status codes, headers, JSON bodies — produced by Spring's web machinery. |
| `@RestControllerAdvice` | A class watching all controllers; its `@ExceptionHandler` methods turn exceptions into responses. |
| `@ExceptionHandler` | Marks a method that handles one exception type and builds the response for it. |

### Where it is used

- Code review: "your test calls the controller method — what proves the API contract?" is a
  standard senior question.
- API contracts: web and mobile clients are built against status codes and the error body
  shape. Those are web-layer facts, so teams test them through the web layer.
- Regressions like run 3 are a real incident class: an error-mapping change ships, unit tests
  are green, clients break in production.

### Common mistakes

- **Believing a green method-call test proves API behaviour.** It proves the method's Java
  behaviour only. Status codes and JSON are made elsewhere.
- **Testing the exception type and calling the 404 "covered".** Run 3 is the proof: the
  exception still flew, the status still changed.
- **Swinging to the other extreme: "so everything needs Spring".** No. Service and domain
  logic tests stay plain and fast — everything from F7.1 to F7.3 still applies.

### Link to the subtask

This lesson is the "why" behind S1 and S2. Both exist because the 404 cannot be proven from
the method-call world. The three annotations are three sizes of "start Spring machinery in a
test"; the skill is picking the smallest size that can prove your statement.

### Your doubts

None raised for this lesson.

### Summary for revision

- A plain test proves method behaviour: return values and exceptions. Nothing else.
- The 404 is born in the advice class, chosen by Spring after it catches the exception.
- Breaking the handler changed the real answer from 404 to 422; the plain test stayed green.
- Spring tests exist to run the machinery; plain tests remain right for plain logic.

### Practice questions (with answers)

**Question 1.** Here is a complete controller, handler, and test:

```java
@RestController
class ItemController {
    private final CatalogService service;
    ItemController(CatalogService service) { this.service = service; }

    @GetMapping("/items/{id}")
    ItemResponse byId(@PathVariable("id") long id) {
        return service.byId(id);   // throws ItemNotFoundException when absent
    }
}

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(ItemNotFoundException.class)
    ResponseEntity<ApiError> onMissing(ItemNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ApiError("item_not_found", "Item not found"));
    }
}
```

```java
class ItemControllerTest {
    @Test
    void throwsWhenItemMissing() {
        CatalogService stub = new CatalogService(null) {
            @Override ItemResponse byId(long id) { throw new ItemNotFoundException(); }
        };
        ItemController controller = new ItemController(stub);

        assertThrows(ItemNotFoundException.class, () -> controller.byId(42));
    }
}
```

The test passes. Name two facts about the running API that this test does **not** prove.

**Answer.** Any two of these:

1. That the API answers status **404** for a missing item. The test contains no status code;
   the status is chosen in `ErrorHandler`, which never runs here.
2. That the response **body** is the designed JSON (`"item_not_found"`, `"Item not found"`).
   No JSON exists in this test.
3. That `ErrorHandler` is wired up at all. It could be deleted and this test would stay green.
4. That the routing works: that `GET /items/42` actually reaches `byId`, including converting
   the path text `"42"` into a `long`.

**Question 2.** A team's API maps `ProductNotFoundException` to status 404 using a method
marked `@ExceptionHandler(ProductNotFoundException.class)` inside a `@RestControllerAdvice`
class. The same class also has a catch-all method marked `@ExceptionHandler(Exception.class)`
that answers status 500. A teammate deletes the `ProductNotFoundException` handler method and
ships. All the project's tests are plain JUnit tests that call methods directly, and they all
stay green. A client now requests a product id that does not exist. What status code does the
client get, and why did no test fail?

**Answer.** The client gets **500**. The exception still flies out of the controller, Spring
looks for the most specific handler, finds only the catch-all `@ExceptionHandler(Exception.class)`
(every exception is an `Exception`), and that one answers 500. No test failed because plain
tests call methods directly: the choice of status code happens inside Spring's
exception-to-response machinery, and that machinery never runs in a plain test.

### Do it yourself — Lesson 1 (second laptop)

1. Check tools. If missing on macOS: `brew install openjdk@21 maven`.

   ```bash
   java -version    # expect: openjdk version "21..."
   mvn -version     # expect: Apache Maven 3.9.x or newer
   ```

2. Make the F7.5 folder and copy your F6.8 app into it:

   ```bash
   cd ~/Projects/foundations    # or wherever your tasks live
   mkdir -p F7.5-testing-a-spring-application-with-slices
   cp -R F6.8-validation-one-error-shape-and-a-request-log/scratch-app \
         F7.5-testing-a-spring-application-with-slices/scratch-app
   cd F7.5-testing-a-spring-application-with-slices/scratch-app
   rm -rf target
   ```

3. Replace the whole `pom.xml` with the following. New against F6.8: the artifactId, three
   dependencies (JPA, H2, the test kit), and two plugin blocks at the bottom. The plugin blocks
   load Mockito as a java agent at JVM start — on Java 21+ a library may no longer quietly
   attach itself to a running JVM, and this is the setup Spring Boot recommends. `-Xshare:off`
   turns off a JVM startup cache that cannot be used together with an agent.

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>

       <parent>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-parent</artifactId>
           <version>3.5.16</version>
           <relativePath/>
       </parent>

       <groupId>com.zenith</groupId>
       <artifactId>slices-scratch</artifactId>
       <version>0.0.1-SNAPSHOT</version>

       <properties>
           <java.version>21</java.version>
       </properties>

       <dependencies>
           <!-- The web starter: brings Tomcat (the server), Spring MVC (the pipeline),
                and Jackson (the JSON converter). One line, whole pipeline. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-web</artifactId>
           </dependency>
           <!-- Bean validation: the constraint annotations and the engine that checks them
                (Hibernate Validator). Without this, @NotBlank and friends do not even exist. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-validation</artifactId>
           </dependency>
           <!-- Spring Data JPA: repositories, Hibernate, and the connection pool.
                This task needs a persistence layer so the slice comparison is honest. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-data-jpa</artifactId>
           </dependency>
           <!-- H2: a small database that runs inside the JVM, no install needed.
                The app and the tests use it. F7.5 also discusses why this is a
                compromise (H2 is not Postgres). -->
           <dependency>
               <groupId>com.h2database</groupId>
               <artifactId>h2</artifactId>
               <scope>runtime</scope>
           </dependency>
           <!-- The test kit: JUnit 5, Mockito, AssertJ, MockMvc, and the Spring
                test annotations (@SpringBootTest, @WebMvcTest, @DataJpaTest). -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-test</artifactId>
               <scope>test</scope>
           </dependency>
       </dependencies>

       <build>
           <plugins>
               <!-- Makes `mvn package` produce a jar you can run with `java -jar`. -->
               <plugin>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-maven-plugin</artifactId>
               </plugin>
               <!-- Exposes each dependency's jar path as a property, such as
                    ${org.mockito:mockito-core:jar}. The surefire plugin below uses it. -->
               <plugin>
                   <groupId>org.apache.maven.plugins</groupId>
                   <artifactId>maven-dependency-plugin</artifactId>
                   <executions>
                       <execution>
                           <goals>
                               <goal>properties</goal>
                           </goals>
                       </execution>
                   </executions>
               </plugin>
               <!-- Mockito rewrites classes while the JVM runs. On Java 21+ a library
                    may no longer quietly attach itself to a running JVM, so we load
                    Mockito as an agent at JVM start. This is the setup Spring Boot
                    itself recommends. -->
               <plugin>
                   <groupId>org.apache.maven.plugins</groupId>
                   <artifactId>maven-surefire-plugin</artifactId>
                   <configuration>
                       <argLine>-javaagent:${org.mockito:mockito-core:jar} -Xshare:off</argLine>
                   </configuration>
               </plugin>
           </plugins>
       </build>
   </project>
   ```

4. Replace `src/main/resources/application.yml` with:

   ```yaml
   # Quiet logs: in F7.5 we read startup times, so the noise from F6.8's
   # debug logging would bury the lines we care about.
   logging:
     level:
       org.springframework.web: info
   ```

5. New file `src/main/java/com/zenith/events/EventEntity.java`:

   ```java
   package com.zenith.events;

   import java.time.Instant;
   import java.util.UUID;

   import jakarta.persistence.Column;
   import jakarta.persistence.Entity;
   import jakarta.persistence.GeneratedValue;
   import jakarta.persistence.Id;
   import jakarta.persistence.Table;

   // The event row in the database. Kept deliberately small for this task.
   @Entity
   @Table(name = "events")
   class EventEntity {

       @Id
       @GeneratedValue
       private UUID id;

       @Column(nullable = false)
       private String name;

       @Column(nullable = false)
       private UUID venueId;

       @Column(nullable = false)
       private Instant salesOpenAt;

       @Column(nullable = false)
       private Instant startsAt;

       @Column(nullable = false)
       private int capacity;

       protected EventEntity() {
           // JPA needs a no-argument constructor. Code should use of(...) instead.
       }

       static EventEntity of(String name, UUID venueId, Instant salesOpenAt,
                             Instant startsAt, int capacity) {
           EventEntity e = new EventEntity();
           e.name = name;
           e.venueId = venueId;
           e.salesOpenAt = salesOpenAt;
           e.startsAt = startsAt;
           e.capacity = capacity;
           return e;
       }

       UUID id() { return id; }
       String name() { return name; }
       UUID venueId() { return venueId; }
       Instant salesOpenAt() { return salesOpenAt; }
       Instant startsAt() { return startsAt; }
       int capacity() { return capacity; }
   }
   ```

6. New file `src/main/java/com/zenith/events/EventRepository.java`:

   ```java
   package com.zenith.events;

   import java.util.List;
   import java.util.UUID;

   import org.springframework.data.jpa.repository.JpaRepository;

   interface EventRepository extends JpaRepository<EventEntity, UUID> {

       // A derived query: Spring Data writes the SQL from the method name.
       List<EventEntity> findByVenueIdOrderByStartsAtAsc(UUID venueId);
   }
   ```

7. New file `src/main/java/com/zenith/events/EventNotFoundException.java`:

   ```java
   package com.zenith.events;

   // Thrown when an event id does not exist. The handler turns this into a 404.
   class EventNotFoundException extends DomainException {

       EventNotFoundException() {
           super("event_not_found", "Event not found");
       }
   }
   ```

8. New file `src/main/java/com/zenith/events/EventService.java`:

   ```java
   package com.zenith.events;

   import java.util.UUID;

   import org.springframework.stereotype.Service;

   // The service layer: looks events up and creates them. The controller stays thin.
   @Service
   class EventService {

       private final EventRepository repository;

       EventService(EventRepository repository) {
           this.repository = repository;
       }

       EventResponse byId(UUID id) {
           EventEntity entity = repository.findById(id)
                   .orElseThrow(EventNotFoundException::new);
           return toResponse(entity);
       }

       EventResponse create(CreateEventRequest request) {
           EventEntity saved = repository.save(EventEntity.of(
                   request.name(), request.venueId(), request.salesOpenAt(),
                   request.startsAt(), request.capacity()));
           return toResponse(saved);
       }

       private static EventResponse toResponse(EventEntity e) {
           return new EventResponse(e.id(), e.name(), e.venueId(),
                   e.salesOpenAt(), e.startsAt(), e.capacity());
       }
   }
   ```

9. In `EventController.java`, make two changes. First, below the `BLUE_NOTE_HALL` constant,
   replace the old `byId` method (the one returning a made-up `EventResponse`) with:

   ```java
   private final EventService service;

   EventController(EventService service) {
       this.service = service;
   }

   @GetMapping("/{id}")
   EventResponse byId(@PathVariable("id") UUID eventId) {
       return service.byId(eventId);
   }
   ```

   Second, replace the `create` method at the bottom so it saves through the service:

   ```java
   @PostMapping
   ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
       EventResponse created = service.create(request);
       return ResponseEntity
               .created(URI.create("/events/" + created.id()))
               .body(created);
   }
   ```

10. In `GlobalExceptionHandler.java`, add the 404 handler directly above the existing
    `@ExceptionHandler(DomainException.class)` method:

    ```java
    // 404: the id is well-formed but names nothing. More specific than the
    // DomainException handler below, so Spring picks this one for this type.
    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<ApiError> onEventNotFound(EventNotFoundException ex) {
        ApiError body = new ApiError(
                UUID.randomUUID().toString(),
                404,
                ex.code(),
                ex.getMessage(),
                List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
    ```

11. New test file `src/test/java/com/zenith/events/L1PlainJUnitTest.java` — full contents in
    "The example" section of this lesson, copy them exactly.

12. Run the test (first run downloads jars, give it a few minutes):

    ```bash
    mvn test
    ```

    Expected (trimmed):

    ```text
    [INFO] Running com.zenith.events.L1PlainJUnitTest
    [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
    [INFO] BUILD SUCCESS
    ```

13. Run the app and ask for an unknown id. In one terminal:

    ```bash
    mvn spring-boot:run
    ```

    In a second terminal:

    ```bash
    curl -si http://localhost:8080/events/11111111-2222-3333-4444-555555555555
    ```

    Expected: `HTTP/1.1 404` and a JSON body with `"code":"event_not_found"` and
    `"message":"Event not found"`.

14. See the blindness yourself. Stop the app (Ctrl+C). In `GlobalExceptionHandler.java`,
    comment out the whole `onEventNotFound` method (the annotation line too). Start the app
    again, repeat the same `curl`. Expected: `HTTP/1.1 422` — the behaviour broke. Now run
    `mvn test`: still green. Then **uncomment the handler**, restart, and confirm the `curl`
    says 404 again.

15. **Done when:** `mvn test` is green, the `curl` shows 404 with your error shape, you watched
    422 appear while the test stayed green, and the handler is restored.

## Lesson 2 — `@SpringBootTest`: a test that starts the whole application (serves S1)

### Definition

`@SpringBootTest` is an annotation on a test class. Before any test method runs, it starts the
whole application inside the test run: every bean, the database connection, everything.

Refresher: the **application context** is the container Spring builds when the app starts. It
creates every **bean** (an object Spring builds and manages: controller, service, repository,
error handler) and holds them all. `@SpringBootTest` builds that same container inside a test.

### Why it is needed

The 404 is made by Spring machinery — routing, the exception handler, the JSON converter. A
plain test starts none of it. `@SpringBootTest` is the bluntest answer: start all the
machinery. If the app can boot, every bean is there, so any behaviour can be tested. The catch
is the price: seconds of startup, paid over and over. Slices (later lessons) are the cheaper tools.

### Key points

- One annotation starts the real application context inside the test JVM.
- Spring finds the `@SpringBootApplication` class by itself (walks up from the test's package).
- The database connection is real. Hibernate creates the schema in the in-memory H2.
- By default **no network port opens** — nothing listens on 8080; `curl` would find nothing.
- `@Autowired` on fields is the normal style in test classes. JUnit builds the test object, so
  Spring cannot use its constructor. The F6.2 "no field injection" rule is for application code.
- The context starts once and is kept for reuse, not restarted per test method.

### How it works

1. JUnit sees the annotation and hands control to Spring's test support.
2. Spring finds `EventsApplication` by walking up packages from the test's package.
3. Spring starts the application context — the same work `mvn spring-boot:run` does: every
   bean, the connection pool, the schema.
4. No port opens. The web machinery exists in memory only.
5. Spring fills every `@Autowired` test field with the matching bean.
6. Test methods run. The context stays alive afterwards for reuse (context caching, Lesson 14).

### The example

```java
package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Lesson 2: start the WHOLE application inside the test JVM.
// Every bean exists, and the database connection is real.
@SpringBootTest
class L2FullContextStartsTest {

    @Autowired
    EventService service;

    @Autowired
    EventRepository repository;

    @Test
    void theWholeApplicationIsHere() {
        assertNotNull(service);
        assertNotNull(repository);
        // Real SQL against the in-memory database: proves the datasource is live.
        assertEquals(0, repository.count());
    }
}
```

`repository.count()` runs a real `select count(*)`. Without a live connection it would blow up.

### Walkthrough — the run, line by line

`mvn test -Dtest=L2FullContextStartsTest`, real output trimmed:

```text
[INFO] Running com.zenith.events.L2FullContextStartsTest
 :: Spring Boot ::               (v3.5.16)
INFO ... : Starting L2FullContextStartsTest using Java 21.0.11 with PID 48423
INFO ... com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Starting...
INFO ... com.zaxxer.hikari.pool.HikariPool  : HikariPool-1 - Added connection conn0:
         url=jdbc:h2:mem:8cd9c84b-e19d-4b07-88fa-40edcab9fa4c user=SA
INFO ... com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Start completed.
INFO ... : Started L2FullContextStartsTest in 2.22 seconds (process running for 2.897)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.805 s
[INFO] BUILD SUCCESS
```

- `Starting L2FullContextStartsTest` — Spring boots, named after the test class.
- `HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:8cd9...` — the connection pool opened
  a real connection to an in-memory H2 database. The random name means a fresh database for
  this context.
- `Started ... in 2.22 seconds` — the price tag, paid before one assertion ran.
- `Time elapsed: 2.805 s` — almost all of it is startup; the test method took milliseconds.
- Contrast in the same run: the Lesson 1 plain test printed `Time elapsed: 0.002 s`.

**Failure drill** — with the `@SpringBootTest` line deleted:

```text
[ERROR] L2FullContextStartsTest.theWholeApplicationIsHere:23 expected: not <null>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.035 s
[ERROR] BUILD FAILURE
```

`expected: not <null>` means `assertNotNull(service)` found `null`. Without the annotation this
is a plain JUnit class: nobody started Spring, nobody filled the `@Autowired` fields, so they
kept Java's default `null`. The annotation is the single thing that brings Spring in.

### Important terms

| Term | Meaning |
| --- | --- |
| Application context | The container of all beans, started by Spring at boot. |
| `@SpringBootTest` | Starts the whole application context inside a test. |
| `@Autowired` (in tests) | Asks Spring to fill a test field with a bean from the context. |
| Hikari | The connection pool: it opens and hands out database connections. |

### Where it is used

- The boot smoke test: one test proving "the app starts with this configuration".
- Full end-to-end paths through every real layer (S8 keeps exactly one).
- Debugging wiring problems: a full-context test reproduces missing or misconfigured beans.

### Common mistakes

- **`@SpringBootTest` on every test class.** Seconds of startup per class: the suite-death
  spiral. This is why slices exist.
- **Thinking it opens a real port.** It does not; nothing listens on 8080.
- **Thinking the context restarts per test method.** It starts once and is shared.
- **Expecting constructor injection in tests.** JUnit builds the test object; use `@Autowired`
  fields in tests.

### Link to the subtask

The engine for S1's `@SpringBootTest` 404 test. Still missing: sending a request into the
in-memory machinery (Lesson 3, `MockMvc`) and asserting on the JSON body (Lesson 4,
`jsonPath`). For S1's "note what it needed": a datasource, every bean, the full startup —
2.22 seconds of it on this machine.

### Your doubts

None raised for this lesson.

### Summary for revision

- `@SpringBootTest` = the whole application context inside a test: every bean, live database.
- Spring finds the app class itself; `@Autowired` fields get filled; no port opens by default.
- The price is startup seconds, paid before the first assertion. Plain test: 0.002 s. Full
  context: 2.8 s.
- Without the annotation, nothing starts and `@Autowired` fields stay `null`.

### Practice questions (with answers)

**Question 1.** Here is a complete test class:

```java
@SpringBootTest
class CatalogSmokeTest {

    @Autowired
    CatalogService catalog;

    @Test
    void beanExists() {
        assertNotNull(catalog);
    }
}
```

Name three things Spring does after JUnit picks this class but before the body of `beanExists`
runs.

**Answer.** Any three of:

1. Finds the `@SpringBootApplication` class by walking up, package by package, from the test's
   package.
2. Starts the application context: builds every bean — controllers, services, repositories,
   the advice class, filters, converters.
3. Connects the datasource: the connection pool opens a real database connection (and
   Hibernate creates the schema when JPA is present).
4. Injects the `@Autowired` field `catalog` with the `CatalogService` bean from the context.

**Question 2.** The same test class, but the `@SpringBootTest` line was deleted. The run fails
with `org.opentest4j.AssertionFailedError: expected: not <null>`. Explain why `catalog` is
`null`.

**Answer.** Without the annotation, JUnit runs the class as a plain test. No Spring context
starts, so no beans exist and nobody processes `@Autowired`. The field keeps Java's default
value for object fields, `null`, and `assertNotNull` fails.

### Do it yourself — Lesson 2 (second laptop)

1. Create `src/test/java/com/zenith/events/L2FullContextStartsTest.java` with the exact
   contents from "The example" above.

2. Run only this class:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L2FullContextStartsTest
   ```

   Expected: the Spring Boot banner, `HikariPool-1 - Start completed.`, a line like
   `Started L2FullContextStartsTest in ~2 seconds`, then `Tests run: 1 ... BUILD SUCCESS`.

3. Write down the number from your `Started ... in X seconds` line. It goes into `NOTES.md`
   in Lesson 9.

4. Failure drill: comment out `@SpringBootTest`, run again. Expected:

   ```text
   [ERROR] L2FullContextStartsTest.theWholeApplicationIsHere:23 expected: not <null>
   [ERROR] BUILD FAILURE
   ```

   Uncomment, rerun, confirm green.

5. **Done when:** the test is green, you noted your startup seconds, and you can point at the
   log line proving the database connection was real (`HikariPool-1 ... url=jdbc:h2:mem:...`).

## Lesson 3 — `MockMvc`: sending a fake HTTP request without a network (serves S1, S2)

### Definition

`MockMvc` is a test tool. It sends a fake HTTP request into the web machinery and gives back
the response to assert on. "Fake" means: no network, no port, no socket. Everything else —
routing, controller, handler, JSON — is real.

### Why it is needed

`@SpringBootTest` starts every bean, but no port opens, so nothing can connect from outside.
The behaviour to prove — "GET /events/{id} returns 404" — only happens when a request travels
the pipeline. `MockMvc` builds the same request object Tomcat would build from network bytes
and hands it straight to the pipeline. The pipeline cannot tell the difference. The same
`MockMvc` code works later inside the web slice, which keeps comparisons fair.

### Key points

- `@AutoConfigureMockMvc` under `@SpringBootTest` creates the `MockMvc` bean. Without it there
  is no such bean.
- `get("/events/{id}", someId)` builds a fake GET; the `{id}` placeholder takes the argument.
- `perform(...)` pushes the request through the real pipeline: filter, routing, controller,
  service, handler, JSON converter.
- `.andExpect(status().isNotFound())` asserts on the response. Every common code has a named
  matcher (`isOk()` = 200, `isNotFound()` = 404).
- Static imports: `get` from `MockMvcRequestBuilders`, `status` from `MockMvcResultMatchers`.
- `perform` declares a checked exception; the test method says `throws Exception`.

### The example

```java
package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 3: the whole application, plus MockMvc to push fake HTTP
// requests through the real web machinery. No port, no network.
@SpringBootTest
@AutoConfigureMockMvc
class L3MockMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns404WhenEventDoesNotExist() throws Exception {
        // The database is empty, so any random id is unknown.
        // Real service, real lookup, real exception, real handler: a real 404.
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound());
    }
}
```

No stub anywhere: the context started a fresh, empty H2 database. The real service queries it,
finds nothing, throws `EventNotFoundException`, and the real handler answers 404.

### Walkthrough — three real runs

**Run 1: success.** `mvn test -Dtest=L3MockMvcTest`:

```text
[INFO] Running com.zenith.events.L3MockMvcTest
INFO ... : Started L3MockMvcTest in 2.084 seconds (process running for 2.711)
INFO ... com.zenith.events.RequestLogFilter : GET /events/f23cb2b9-... -> 404 (100 ms)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.534 s
[INFO] BUILD SUCCESS
```

The middle line is the F6.8 `RequestLogFilter` logging the fake request: method, path, status
404. No network existed, yet the filter ran — proof that `MockMvc` exercises the pipeline, not
just a method. Startup price again: about 2 seconds.

**Run 2: forget `@AutoConfigureMockMvc`.** Real output, trimmed:

```text
org.springframework.beans.factory.UnsatisfiedDependencyException:
  Error creating bean with name 'com.zenith.events.L3MockMvcTest':
  Unsatisfied dependency expressed through field 'mockMvc':
  No qualifying bean of type 'org.springframework.test.web.servlet.MockMvc' available:
  expected at least 1 bean which qualifies as autowire candidate.
```

Decoded: Spring searched the context for a `MockMvc` bean and found none ("No qualifying bean
of type"), so it could not fill the `@Autowired` field ("Unsatisfied dependency through field
'mockMvc'"). `@SpringBootTest` starts the app's beans; the `MockMvc` tool is extra, and
`@AutoConfigureMockMvc` is what adds it.

**Run 3: wrong expectation** (`status().isOk()` instead of `isNotFound()`):

```text
java.lang.AssertionError: Status expected:<200> but was:<404>
```

"You expected 200, the pipeline answered 404." The failure speaks HTTP, because the assertion
is about HTTP — the plain test of Lesson 1 could not even spell 404.

### Important terms

| Term | Meaning |
| --- | --- |
| `MockMvc` | Sends fake HTTP requests through the real pipeline, no network. |
| `@AutoConfigureMockMvc` | Adds the `MockMvc` bean to a `@SpringBootTest` context. |
| `perform(...)` | Executes one fake request and returns the result to assert on. |
| `status()` | Result matcher for the HTTP status code. |

### Where it is used

- The standard way to test controllers in Spring projects, full context or web slice.
- CI-friendly: no ports, so no port conflicts on build machines.
- Anywhere the question is "what does my API answer?" rather than "what does my method return?".

### Common mistakes

- Forgetting `@AutoConfigureMockMvc` → `No qualifying bean of type MockMvc`.
- Thinking a server is running — there is no port; the hand-over happens in memory.
- Asserting the exception instead of the response. The exception is internal machinery.
- Dropping `throws Exception` — the compiler refuses until it is back.

### Link to the subtask

S1 nearly finished: this test already proves the 404 with a real context. Missing: the body
assertion (`jsonPath`, Lesson 4). Then S1 is done.

### Your doubts

None raised for this lesson.

### Summary for revision

- `MockMvc` = fake request in, real pipeline, response out. No network.
- `@AutoConfigureMockMvc` creates the tool; `@Autowired MockMvc` grabs it.
- `perform(get("/path/{x}", value)).andExpect(status().isNotFound())` is the core shape.
- The `RequestLogFilter` logging `-> 404` during the test proves the pipeline ran.

### Practice questions (with answers)

**Question 1.** A real HTTP request to this app normally makes this journey: (1) the request
arrives over the network, (2) Spring routes the path to a controller method and converts the
path text to the right types, (3) the controller calls the service, (4) the service throws
`EventNotFoundException` because the id is unknown, (5) the exception handler builds the error
body and picks status 404, (6) the JSON converter writes the body. Here is a complete test:

```java
@SpringBootTest
@AutoConfigureMockMvc
class EventApi404Test {

    @Autowired
    MockMvc mockMvc;

    @Test
    void unknownIdGives404() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound());
    }
}
```

When this test runs, which of the six steps happen for real, and which do not?

**Answer.** Steps 2 to 6 happen for real: routing and type conversion, the controller call, the
service throwing, the handler picking 404, the JSON being written. Step 1 does not happen:
there is no network. `MockMvc` builds the request object in memory and hands it directly to the
pipeline.

**Question 2.** A teammate writes this test:

```java
@SpringBootTest
class PingTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingAnswers() throws Exception {
        mockMvc.perform(get("/ping"))
               .andExpect(status().isOk());
    }
}
```

It fails before the test method runs, with: `No qualifying bean of type
'org.springframework.test.web.servlet.MockMvc' available: expected at least 1 bean which
qualifies as autowire candidate.` Explain what the message means, and name the one-line fix.

**Answer.** The context contains no `MockMvc` bean — `@SpringBootTest` alone does not create
the tool — so Spring cannot fill the `@Autowired mockMvc` field. The fix is one line: add
`@AutoConfigureMockMvc` under `@SpringBootTest`.

### Do it yourself — Lesson 3 (second laptop)

1. Create `src/test/java/com/zenith/events/L3MockMvcTest.java` with the contents from
   "The example" above.

2. Run it:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L3MockMvcTest
   ```

   Expected: `Started L3MockMvcTest in ~2 seconds`, a `RequestLogFilter : GET /events/... -> 404`
   line, then `Tests run: 1, Failures: 0 ... BUILD SUCCESS`.

3. Failure drill 1: comment out `@AutoConfigureMockMvc`, run. Expect
   `No qualifying bean of type 'org.springframework.test.web.servlet.MockMvc' available`.
   Uncomment it.

4. Failure drill 2: change `isNotFound()` to `isOk()`, run. Expect
   `Status expected:<200> but was:<404>`. Change it back, rerun, confirm green.

5. **Done when:** the test is green, both failure messages appeared as above, and you can
   explain why the `RequestLogFilter` line shows up in a test that never touched the network.

## Lesson 4 — `jsonPath`: asserting on the JSON body (serves S1 — completes it)

### Definition

`jsonPath` is a tiny expression language for picking one value out of a JSON document. `$` is
the whole document; `$.message` is the top-level field named `message`. In a `MockMvc` test,
`jsonPath("$.message").value("Event not found")` picks that field and asserts its value.

### Why it is needed

The status code is not the whole contract. The API's 404 body is:

```json
{
  "errorId": "f7f20bd7-...",
  "status": 404,
  "code": "event_not_found",
  "message": "Event not found",
  "fieldErrors": []
}
```

Clients parse this: branch on `code`, show `message`. If the body changed shape, a status-only
test would stay green. `jsonPath` reaches into the body.

### Key points

- Paths: `$` root · `$.field` top-level field · `$.array[0]` first element ·
  `$.array[0].field` field inside it.
- `jsonPath(...)` is a result matcher from `MockMvcResultMatchers`, like `status()`.
- `.value(expected)`: JSON strings compare with Java strings, JSON numbers with Java numbers —
  `.value(404)`, not `.value("404")`.
- Each `.andExpect(...)` is one assertion; chain as many as the contract needs.
- Random fields (like `errorId`) are not pinned; use `.exists()` to prove presence.

### The example — subtask S1, complete

```java
package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 4 — and subtask S1, complete:
// "GET /events/{id} returns 404 for an unknown id", proven with the
// whole application context, and the error body checked field by field.
@SpringBootTest
@AutoConfigureMockMvc
class L4FullContext404Test {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns404WithErrorBodyWhenEventDoesNotExist() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.code").value("event_not_found"))
               .andExpect(jsonPath("$.message").value("Event not found"));
    }
}
```

### Walkthrough — one green run, two failures

**Green:**

```text
INFO ... : Started L4FullContext404Test in 1.992 seconds (process running for 2.606)
INFO ... RequestLogFilter : GET /events/ae87c7b7-... -> 404 (99 ms)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.467 s
[INFO] BUILD SUCCESS
```

**Failure kind 1 — right path, wrong value** (expected text changed to "No such event"):

```text
java.lang.AssertionError: JSON path "$.message" expected:<No such event> but was:<Event not found>
```

**Failure kind 2 — wrong path** (`$.msg` instead of `$.message`):

```text
java.lang.AssertionError: No value at JSON path "$.msg"
```

Two different messages for two different diseases: "expected X but was Y" = field exists with
another value; "No value at JSON path" = the field is not there at all. The second one is how a
renamed field in the API announces itself.

### Important terms

| Term | Meaning |
| --- | --- |
| `jsonPath` | Expression language for picking values out of JSON. |
| `$` | The root of the JSON document. |
| `.value(x)` | Assert the picked value equals `x` (numbers as numbers, strings as strings). |
| `.exists()` | Assert the field is present, without pinning its value. |

### Where it is used

- Contract tests for every JSON API: error bodies, list sizes, nested fields.
- Guarding error shapes exactly like F6.8's (Lesson 10's whole job).
- Existence checks on varying fields with `.exists()`.

### Common mistakes

- Asserting only the status — the body is the other half of the contract.
- Path typos → `No value at JSON path`.
- Numbers as strings: `.value("404")` fails against the number 404.
- Pinning random fields like `errorId` — flaky on every run; use `.exists()`.

### Link to the subtask

**S1 done.** The 404 assertion is proven with `@SpringBootTest` and a real context, body
included. What it needed: a datasource, every bean, ~2 seconds of full startup. The numbers are
recorded in `NOTES.md` in Lesson 9.

### Your doubts

None raised for this lesson.

### Summary for revision

- `jsonPath("$.field").value(expected)` asserts one field of the JSON body.
- Numbers compare as numbers, strings as strings.
- Two failure kinds: wrong value ("expected X but was Y") and missing field ("No value at JSON
  path").
- Do not pin random fields; `.exists()` proves presence.

### Practice questions (with answers)

**Question 1.** An API answers this exact JSON body with status 409:

```json
{
  "errorId": "abc-123",
  "status": 409,
  "code": "seat_already_reserved",
  "message": "Seat A12 is already reserved.",
  "fieldErrors": []
}
```

A test contains these three assertions:

```java
.andExpect(jsonPath("$.code").value("seat_already_reserved"))
.andExpect(jsonPath("$.message").value("Seat A12 is reserved."))
.andExpect(jsonPath("$.detail").value("conflict"))
```

For each: pass or fail, and what will the failure message roughly say?

**Answer.**

1. **Passes** — the body's `code` is exactly `seat_already_reserved`.
2. **Fails, wrong value** — `JSON path "$.message" expected:<Seat A12 is reserved.> but
   was:<Seat A12 is already reserved.>`. The field exists; the text differs (missing
   "already").
3. **Fails, missing field** — `No value at JSON path "$.detail"`. The body has no `detail`
   field at all.

**Question 2.** An API answers this body for a created event:

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "name": "Jazz Night",
  "capacity": 300
}
```

Write the two `.andExpect(...)` lines that assert the name is `"Jazz Night"` and the capacity
is the number 300.

**Answer.**

```java
.andExpect(jsonPath("$.name").value("Jazz Night"))
.andExpect(jsonPath("$.capacity").value(300))
```

Capacity is a JSON number, so the expected value is the number `300`, without quotes.

### Do it yourself — Lesson 4 (second laptop)

1. Create `src/test/java/com/zenith/events/L4FullContext404Test.java` with the contents from
   "The example" above. This file is the S1 deliverable — it stays in the project.

2. Run it:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L4FullContext404Test
   ```

   Expected: `Started L4FullContext404Test in ~2 seconds`, the `RequestLogFilter ... -> 404`
   line, `Tests run: 1, Failures: 0`, `BUILD SUCCESS`.

3. Failure drill 1: change `"Event not found"` to `"No such event"`, run, expect
   `JSON path "$.message" expected:<No such event> but was:<Event not found>`. Change back.

4. Failure drill 2: change `"$.message"` to `"$.msg"`, run, expect
   `No value at JSON path "$.msg"`. Change back, rerun, confirm green.

5. **Done when:** the test is green, you saw both failure kinds and can say the difference out
   loud (wrong value versus missing field), and you noted your startup seconds again.

## Lesson 5 — Mockito in five lines (serves S2; closes the F7.4 gap just enough)

### Definition

Mockito is a library that writes test doubles for you. F7.3 wrote every double by hand as its
own class. Mockito builds the same kind of stand-in at runtime, from one line, for any class or
interface. More lines script what it returns or throws.

### Why it is needed

Practical reason: hand-written doubles cost a file or a subclass per behaviour. Lesson 1's stub
was six lines plus a `null`-to-the-constructor trick. A hand-written double for
`EventRepository` would mean ~20 inherited `JpaRepository` methods. Mockito: two lines.

Blocking reason: S2 uses `@MockitoBean`, which puts a *Mockito* mock into the Spring context,
scripted with `when(service.byId(any())).thenThrow(new EventNotFoundException())`. That line
must be readable before Lesson 8. F7.4 would have taught it; it was skipped, so this lesson
covers exactly what F7.5 needs.

### Key points

- `mock(EventService.class)` builds a stand-in at runtime: same methods, empty bodies,
  default answers (`null` / `0` / `false`). The real constructor never runs.
- `when(stub.byId(any())).thenThrow(...)` scripts a call. Read inside out: the call is made as
  a *recording*; `when(...)` says "script that recording"; `thenThrow`/`thenReturn` is the script.
- `any()` matches any argument. An exact value (`when(stub.byId(knownId))`) matches only itself.
- Calls matching no script fall back to the defaults.
- Imports: `mock`, `when` from `org.mockito.Mockito`; `any` from `org.mockito.ArgumentMatchers`.
- Vocabulary: Mockito names everything "mock", but scripting answers is, in F7.3's precise
  language, *stubbing*. Mockito also does interaction counting (`verify`) — not needed here.
- The runtime class-building is why the pom loads Mockito as a java agent.

### The example

```java
package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

// Lesson 5: Mockito writes the test double for you. No Spring here —
// the same plain-JUnit world as Lesson 1, minus the hand-written stub.
class L5MockitoBasicsTest {

    @Test
    void scriptedToThrow_lesson1TestRewritten() {
        EventService stub = mock(EventService.class);
        when(stub.byId(any())).thenThrow(new EventNotFoundException());

        EventController controller = new EventController(stub);

        assertThrows(EventNotFoundException.class,
                () -> controller.byId(UUID.randomUUID()));
    }

    @Test
    void scriptedToReturn_andDefaultsForEverythingElse() {
        UUID knownId = UUID.randomUUID();
        EventResponse canned = new EventResponse(knownId, "Jazz Night",
                UUID.randomUUID(),
                Instant.parse("2027-01-01T10:00:00Z"),
                Instant.parse("2027-02-01T19:00:00Z"), 300);

        EventService stub = mock(EventService.class);
        when(stub.byId(knownId)).thenReturn(canned);   // script ONE exact id

        assertEquals("Jazz Night", stub.byId(knownId).name()); // the scripted answer
        assertNull(stub.byId(UUID.randomUUID()));              // unscripted: default null
    }
}
```

Test 1 is Lesson 1's test rewritten: six stub lines became two, no constructor trick. Test 2
shows matching: the exact `knownId` gets the scripted answer; any other id falls back to `null`.

### Walkthrough — the runs

**Green run** (`mvn test -Dtest=L5MockitoBasicsTest`):

```text
[INFO] Running com.zenith.events.L5MockitoBasicsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.332 s
[INFO] BUILD SUCCESS
```

No Spring banner, no `Started` line, no HikariPool: Mockito lives in the plain-JUnit world.
Mockito is cheap; it is not what makes tests slow.

**Failure drill — `when()` on a real object:**

```java
@Test
void whenOnARealCallBlowsUp() {
    when("abc".length());   // "abc" is a real String, not a mock
}
```

```text
org.mockito.exceptions.misusing.MissingMethodInvocationException:

when() requires an argument which has to be 'a method call on a mock'.
For example:
    when(mock.getArticles()).thenReturn(articles);
```

`when()` only understands "the last call recorded on a Mockito-built object". A real `String`
records nothing, so `when()` finds no recording and refuses.

### Important terms

| Term | Meaning |
| --- | --- |
| `mock(X.class)` | Build a runtime stand-in for `X` with default answers. |
| `when(...)` | Start scripting the call just recorded on a mock. |
| `thenReturn` / `thenThrow` | The script: answer with a value, or throw. |
| `any()` | Argument matcher: match whatever argument comes. |
| Stubbing | Scripting answers (the precise F7.3 word for today's usage). |

### Where it is used

- The standard doubles tool in Java projects; `mockito-core` ships inside
  `spring-boot-starter-test`.
- `@MockitoBean` in Spring tests is a Mockito mock dropped into the context (Lessons 7–8).
- Interaction checks (`verify`) when the call itself is the behaviour.

### Common mistakes

- `when()` around a call on a real object → `MissingMethodInvocationException`.
- Scripting an exact argument, then calling with a different one → default `null` → confusing
  `NullPointerException` later. If the argument does not matter, say `any()`.
- Over-mocking: Mockito makes doubles cheap, including bad ones. F7.3's rules still govern —
  never mock value objects; prefer doubles at boundaries you own.

### Link to the subtask

S2's key line `when(service.byId(any())).thenThrow(new EventNotFoundException())` now reads as
plain English. Missing for S2: a context with only the web layer (Lessons 6–7) and the trick
that places this mock into it (`@MockitoBean`, Lesson 8).

### Your doubts

None raised for this lesson.

### Summary for revision

- `mock(X.class)` = instant stand-in, default answers, real constructor never runs.
- `when(call).thenReturn(v)` / `.thenThrow(e)` = script; `any()` = match any argument.
- Unscripted calls return defaults (`null`/`0`/`false`).
- `when()` needs a call on a mock — on a real object it throws `MissingMethodInvocationException`.

### Practice questions (with answers)

**Question 1.** Here is a complete class and test:

```java
class SeatLookup {
    String ownerOf(String seatLabel) {
        throw new IllegalStateException("real lookup not available in tests");
    }
}
```

```java
@Test
void whoOwnsTheSeats() {
    SeatLookup lookup = mock(SeatLookup.class);
    when(lookup.ownerOf("A12")).thenReturn("Pavan");

    String a = lookup.ownerOf("A12");
    String b = lookup.ownerOf("B07");
}
```

What are `a` and `b`, and why does the real `IllegalStateException` never appear?

**Answer.** `a` is `"Pavan"` — the call matches the exact scripted argument `"A12"`. `b` is
`null` — `"B07"` matches no script, so the mock answers the default for object returns. The
real exception never appears because `mock(SeatLookup.class)` builds a stand-in class whose
method bodies replace the real ones entirely; real code in `ownerOf` simply never runs.

**Question 2.** A teammate writes:

```java
class PriceCalculator {
    int totalFor(int seats) { return seats * 300; }
}

@Test
void discountApplied() {
    PriceCalculator calc = new PriceCalculator();
    when(calc.totalFor(3)).thenReturn(900);
}
```

It dies with `MissingMethodInvocationException: when() requires an argument which has to be 'a
method call on a mock'`. Why, and what one-line change fixes it?

**Answer.** `calc` was built with `new`, so it is a real object: calling `totalFor(3)` on it
records nothing for Mockito, and `when()` finds no recorded call to script. Fix the creation
line: `PriceCalculator calc = mock(PriceCalculator.class);`.

### Do it yourself — Lesson 5 (second laptop)

1. Create `src/test/java/com/zenith/events/L5MockitoBasicsTest.java` with the contents from
   "The example" above.

2. Run:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L5MockitoBasicsTest
   ```

   Expected: no Spring banner, then `Tests run: 2, Failures: 0 ... BUILD SUCCESS`.

3. Failure drill: create `src/test/java/com/zenith/events/L5WhenMisuseTest.java`:

   ```java
   package com.zenith.events;

   import static org.mockito.Mockito.when;

   import org.junit.jupiter.api.Test;

   class L5WhenMisuseTest {

       @Test
       void whenOnARealCallBlowsUp() {
           when("abc".length());   // "abc" is a real String, not a mock
       }
   }
   ```

   Run `mvn test -Dtest=L5WhenMisuseTest`, expect the `MissingMethodInvocationException`
   message above, then **delete the drill file**.

4. **Done when:** both Lesson 5 tests are green in well under a second of test time, you saw
   the misuse error once, and the drill file is deleted.

## Lesson 6 — The test slice idea: start only the part you are testing (serves S2, S5–S8)

### Definition

A test slice is an annotation that starts only one layer of the application context instead of
all of it. Fewer beans, faster start. This task uses two: `@WebMvcTest` (web layer only) and
`@DataJpaTest` (persistence layer only).

### Why it is needed

Two measured facts: every `@SpringBootTest` class pays ~2 seconds of startup, and the full
context holds **246 bean definitions** (measured below). The 404 test touches roughly a dozen
of them. The task's arithmetic: three hundred classes × 2 seconds = ten minutes of pure
startup. A suite that slow stops being run, and a suite not run protects nothing. The habit:
**reach for the narrowest context that can prove your statement.**

### Key points

- A slice runs only one layer's auto-configuration and scans only matching bean kinds.
- Everything else is **absent** — not mocked, absent.
- The beans a slice starts are the real ones: real advice, real Jackson, real filters.
- If the started layer depends on a bean from an absent layer, the slice refuses to start
  until a stand-in is provided (Lesson 7 shows the exact error).
- `@WebMvcTest(EventController.class)` names which controller to start; others stay out.

### The example

The measuring test:

```java
package com.zenith.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// Lesson 6: how big is "everything"? Count the beans in the full context.
@SpringBootTest
class L6BeanCountTest {

    @Autowired
    ApplicationContext context;

    @Test
    void countTheBeans() {
        System.out.println(">>> Beans in the FULL context: "
                + context.getBeanDefinitionCount());
    }
}
```

The map of this app across the three context sizes:

| Bean | `@SpringBootTest` | `@WebMvcTest(EventController.class)` | `@DataJpaTest` |
|---|---|---|---|
| `EventController` | yes | yes | no |
| `GlobalExceptionHandler` (advice) | yes | yes | no |
| `RequestLogFilter` | yes | yes | no |
| Jackson (JSON converter) | yes | yes | no |
| `PingController` | yes | **no** — only the named controller | no |
| `EventService` | yes | **no** | no |
| `EventRepository` | yes | no | yes |
| Datasource + Hikari + Hibernate | yes | no | yes |

### Walkthrough — the run and the reading

```text
INFO ... : Started L6BeanCountTest in 1.953 seconds (process running for 2.526)
>>> Beans in the FULL context: 246
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

246 bean definitions; maybe fifteen are ours. The rest is Spring machinery — useful in
production, dead weight for "does an unknown id give 404?". The 2 seconds pay for all 246.

### Important terms

| Term | Meaning |
| --- | --- |
| Test slice | An annotation starting only one layer of the context. |
| Absent bean | A bean a slice did not start. Asking for it is a layer violation, not a bug. |
| Narrowest-context rule | Pick the smallest context that can run the machinery your statement is about. |

### Where it is used

- Code review: "could this `@SpringBootTest` be a slice?" is a standard question in Spring teams.
- Other slices exist for other layers (`@JsonTest`, `@RestClientTest`) — same idea.

### Common mistakes

- `@SpringBootTest` by reflex (copy-paste) — how twenty-minute suites are born.
- Forcing genuinely end-to-end statements into a slice — full context is then correct
  (Lesson 13 keeps exactly one).
- Expecting absent beans inside a slice — that is the slice reporting a crossed layer.
- Thinking "slice" means "everything is mocked" — started beans are real; absence is not fakery.

### Link to the subtask

Grounds the task's Focus bullet with two of our own numbers: 246 beans, ~2 seconds. S2 starts
in Lesson 7; S5–S8 all lean on the narrowest-context rule.

### Your doubts

None raised for this lesson.

### Summary for revision

- Slice = one layer's context: real beans in, everything else absent.
- Full context: 246 beans, ~2 s. The 404 statement needs ~a dozen beans.
- Web statements → `@WebMvcTest`; query statements → `@DataJpaTest`; genuinely end-to-end →
  one `@SpringBootTest`.

### Practice questions (with answers)

**Question 1.** For each statement, name the right context size — `@WebMvcTest`,
`@DataJpaTest`, or `@SpringBootTest` — with a one-line reason:

1. "POST /events with a blank name must answer 400, and the error body must name the field
   `name`."
2. "`findByVenueIdOrderByStartsAtAsc` must return only that venue's events, sorted by start
   time."
3. "An event created through POST /events can then be fetched through GET /events/{id}."

**Answer.**

1. `@WebMvcTest` — validation and the error shape are web-layer behaviour; no database is
   involved.
2. `@DataJpaTest` — a derived query against a real schema; HTTP is not involved.
3. `@SpringBootTest` — the statement spans web, service, and persistence together; that is
   what the full context is for, used sparingly.

**Question 2.** A test class is annotated `@WebMvcTest(EventController.class)`. The
application also contains: `GlobalExceptionHandler` (marked `@RestControllerAdvice`),
`EventService` (marked `@Service`), `EventRepository` (a Spring Data repository interface),
and `RequestLogFilter` (a servlet filter marked `@Component`). Which of the four exist as
beans in this test's context?

**Answer.**

- `GlobalExceptionHandler`: **yes** — advice classes are part of the web layer the slice scans.
- `EventService`: **no** — `@Service` beans belong to an absent layer.
- `EventRepository`: **no** — persistence is absent: no repositories, no datasource.
- `RequestLogFilter`: **yes** — filters are included in the web slice.

### Do it yourself — Lesson 6 (second laptop)

1. Create `src/test/java/com/zenith/events/L6BeanCountTest.java` with the contents from
   "The example" above.

2. Run:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L6BeanCountTest
   ```

   Expected: `Started L6BeanCountTest in ~2 seconds`, then:

   ```text
   >>> Beans in the FULL context: 246
   ```

   (Your count may differ by a few.)

3. Write your count next to your startup seconds from Lesson 2 — the "cost of everything" side
   of the Lesson 9 comparison.

4. **Done when:** the test is green, the count is written down, and you can state the slice
   idea in one sentence: start only the layer the statement is about; everything else is absent.

## Lesson 7 — `@WebMvcTest`: the web slice, and the bean it refuses to make (serves S2)

### Definition

`@WebMvcTest` is the slice annotation for the web layer. It starts: the controller you name,
the `@RestControllerAdvice` class, filters, validation, the JSON converters — and `MockMvc`
itself, no extra annotation needed. It leaves out: services, repositories, the datasource.

### Why it is needed

The 404 statement is a web-layer statement, and Lesson 6's rule says: start only that layer.
`@WebMvcTest` is that rule as an annotation. Today the slice deliberately does not start — the
refusal teaches the most important thing about it.

### Key points

- `@WebMvcTest(EventController.class)` starts only the named controller. `PingController`
  stays out. Plain `@WebMvcTest` (no name) would start every controller.
- `MockMvc` is auto-configured by the slice — `@AutoConfigureMockMvc` is only for
  `@SpringBootTest`.
- `@Service` beans are absent. A controller whose constructor demands one cannot be built, so
  the context cancels startup.
- The refusal is the slice enforcing the layer boundary: "hand me a stand-in" — which is
  `@MockitoBean` (Lesson 8).
- Even the failed startup is fast (0.732 s total): no Hikari, no Hibernate, no schema.

### The example — built to fail

```java
package com.zenith.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 7, step one: the web slice WITHOUT a stand-in for EventService.
// Expected: the context refuses to start. The error is the lesson.
@WebMvcTest(EventController.class)
class L7WebSliceRefusalTest {

    @Autowired
    MockMvc mockMvc;   // no @AutoConfigureMockMvc needed: the slice brings MockMvc itself

    @Test
    void neverRuns() throws Exception {
        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound());
    }
}
```

(The file is deleted after the drill so the suite stays green; Lesson 8 rebuilds it properly.)

### Walkthrough — reading the refusal

Real output, trimmed:

```text
WARN  o.s.w.c.s.GenericWebApplicationContext : Exception encountered during context
      initialization - cancelling refresh attempt:
org.springframework.beans.factory.UnsatisfiedDependencyException:
  Error creating bean with name 'eventController':
  Unsatisfied dependency expressed through constructor parameter 0:
  No qualifying bean of type 'com.zenith.events.EventService' available:
  expected at least 1 bean which qualifies as autowire candidate.
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.732 s
```

- "No qualifying bean of type EventService available" — the slice never creates `@Service`
  beans, so the search finds nothing.
- "Unsatisfied dependency through constructor parameter 0" — the F6.2 constructor doing its
  honest job: no service, no controller.
- "cancelling refresh attempt" — "refresh" is Spring's word for starting the context; startup
  is abandoned, the test errors without running.

Comparison with Lesson 3's error: same family ("No qualifying bean"), different story. There a
*tool* (`MockMvc`) was missing because it was not configured; here an *application bean* is
missing because the slice deliberately excludes its layer. "You forgot something" versus "you
crossed a layer".

### Important terms

| Term | Meaning |
| --- | --- |
| `@WebMvcTest(X.class)` | Start the web slice with only controller `X`. |
| Refresh | Spring's word for starting the context. |
| `UnsatisfiedDependencyException` | A bean's constructor parameter could not be filled from the context. |

### Where it is used

- The default annotation for controller tests in Spring codebases: routing, status codes,
  bodies, validation, error shapes — the F6.7/F6.8 work (subtask S5, Lesson 10).

### Common mistakes

- Leaving out the controller name — every controller starts; name what you test.
- `@Autowired EventService` in a web slice — same refusal, now predictable.
- Adding `@AutoConfigureMockMvc` to a `@WebMvcTest` class — harmless but redundant.
- "Fixing" the refusal by switching to `@SpringBootTest` — pays for all 246 beans to silence a
  message; the right fix for a web statement is a stand-in (Lesson 8).

### Link to the subtask

S2's annotation, half-understood by design: what `@WebMvcTest(EventController.class)` starts,
what it refuses, and why the subtask's snippet cannot work without `@MockitoBean EventService`.

### Your doubts

None raised for this lesson.

### Summary for revision

- Web slice = named controller + advice + filters + converters + validation + MockMvc.
- Services/repositories/datasource are absent; a controller needing a service stops startup.
- The refusal error: `Unsatisfied dependency ... No qualifying bean of type 'EventService'`.
- Refusal is feedback ("crossed a layer"), not a bug. Stand-in next: `@MockitoBean`.

### Practice questions (with answers)

**Question 1.** An application has this controller:

```java
@RestController
class VenueController {
    private final VenueFinder finder;   // VenueFinder is a @Service class
    VenueController(VenueFinder finder) { this.finder = finder; }

    @GetMapping("/venues/{id}")
    VenueResponse byId(@PathVariable("id") UUID id) {
        return finder.byId(id);
    }
}
```

A test annotated `@WebMvcTest(VenueController.class)` fails at startup with: `Error creating
bean with name 'venueController': Unsatisfied dependency expressed through constructor
parameter 0: No qualifying bean of type 'VenueFinder' available`. Why? Name the two ways to
make it runnable, and pick the right one for the statement "GET /venues/{id} returns 404 for an
unknown id".

**Answer.** The web slice starts only web-layer beans, and `VenueFinder` is a `@Service`, so it
is absent; the controller's constructor cannot be filled and the context cancels startup. Way
one: provide a stand-in inside the context with `@MockitoBean VenueFinder finder;`. Way two:
widen to `@SpringBootTest` (plus `@AutoConfigureMockMvc`). For a web-layer statement like the
404 mapping, way one is right — it is the narrowest context that can prove the statement.

**Question 2.** Under `@SpringBootTest`, the field `@Autowired MockMvc mockMvc` stays empty
unless `@AutoConfigureMockMvc` is added. A class annotated only `@WebMvcTest(EventController.class)`
has the same `@Autowired MockMvc` field. Will it be filled?

**Answer.** Yes. `@WebMvcTest` auto-configures `MockMvc` as part of the slice — a web slice
without a way to send requests would be pointless. `@AutoConfigureMockMvc` exists to add the
tool to `@SpringBootTest`, which does not include it by default.

### Do it yourself — Lesson 7 (second laptop)

1. Create `src/test/java/com/zenith/events/L7WebSliceRefusalTest.java` with the contents from
   "The example" above.

2. Run and watch the refusal:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L7WebSliceRefusalTest
   ```

   Expected error contains:

   ```text
   Error creating bean with name 'eventController':
   Unsatisfied dependency expressed through constructor parameter 0:
   No qualifying bean of type 'com.zenith.events.EventService' available
   ```

   Note the speed: under a second, no Hikari lines.

3. **Delete the file** and run `mvn test` to confirm the suite is green again:

   ```bash
   rm src/test/java/com/zenith/events/L7WebSliceRefusalTest.java
   mvn test
   ```

4. **Done when:** you saw the exact refusal, can explain it in one sentence (the slice has no
   service layer; the controller's constructor demands one), and the suite is green.

## Lesson 8 — `@MockitoBean`: the missing bean, filled with a mock (serves S2 — completes it)

### Definition

`@MockitoBean` on a test field does two things at once: it creates a Mockito mock of the
field's type, and it registers that mock as a **bean inside the test's context**. Any bean that
needs that type receives the mock; the test field holds the same mock for scripting.

### Why it is needed

Lesson 7's standoff: the web slice has no `EventService`, and the controller's constructor
demands one. `mock(EventService.class)` from Lesson 5 lives only in test code — the controller
is built by Spring, inside the context, before any test method runs. `@MockitoBean` is the
bridge: Mockito builds the stand-in, Spring installs it where the controller will find it.

### Key points

- The mock is registered before the context starts, so the controller's constructor gets it.
- The test field holds the same instance — `when(service.byId(any())).thenThrow(...)` scripts
  the exact object the controller talks to.
- Mocks reset between test methods; script inside each test.
- Naming: old tutorials say `@MockBean` — deprecated since Boot 3.4. Use `@MockitoBean`, import
  `org.springframework.test.context.bean.override.mockito.MockitoBean` (Spring Framework, not Boot).
- `@Autowired` fetches an existing bean; `@MockitoBean` creates and registers a missing one.

### The example — subtask S2, complete

```java
package com.zenith.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Lesson 8 — and subtask S2, complete: the same 404 assertion as L4,
// now in the web slice. EventService is a Mockito mock placed into the context.
@WebMvcTest(EventController.class)
class L8WebSlice404Test {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    @Test
    void returns404WithErrorBodyWhenEventDoesNotExist() throws Exception {
        when(service.byId(any())).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.code").value("event_not_found"))
               .andExpect(jsonPath("$.message").value("Event not found"));
    }
}
```

The `perform` and the four assertions are identical to L4, character for character. Only the
context changed — plus one new line: the `when(...)`. In L4 the real service hit a real empty
database and really threw; here the script supplies the throw.

### Walkthrough — the numbers, and one trap

Green run (`mvn test -Dtest='L8*'`, including the bean-count twin):

```text
[INFO] Running com.zenith.events.L8SliceBeanCountTest
INFO ... : Started L8SliceBeanCountTest in 0.925 seconds (process running for 1.485)
>>> Beans in the WEB SLICE context: 129
[INFO] Running com.zenith.events.L8WebSlice404Test
INFO ... RequestLogFilter : GET /events/7b22a4c8-... -> 404 (46 ms)
[INFO] Tests run: 1, ... Time elapsed: 0.095 s
[INFO] BUILD SUCCESS
```

- Startup **0.925 s** (full context: 1.992 s). No Hikari line — no database started.
- **129 beans** (full context: 246).
- The real `RequestLogFilter` logged `-> 404`: real filter, real advice, real JSON converter.
- The second class ran in **0.095 s with no `Started` line**: identical configuration, so
  Spring reused the context — context caching, photographed early (Lesson 14).

| | `@SpringBootTest` (L4) | `@WebMvcTest` (L8) |
|---|---|---|
| Startup | 1.992 s | 0.925 s |
| Beans | 246 | 129 |
| Database | started | absent |
| Proves | mapping, with real service and DB | mapping, with scripted service |

**The trap — forgetting to script.** With the `when(...)` line commented out:

```text
INFO ... RequestLogFilter : GET /events/742195c8-... -> 200 (43 ms)
java.lang.AssertionError: Status expected:<404> but was:<200>
```

An unscripted mock answers defaults — `null`. The controller returned `null`, no exception
flew, the advice never ran, Spring answered 200 with an empty body. "Expected 404 but was 200"
in a slice test = the mock was never told to throw.

Honesty note: L8 does **not** prove the real `EventService` throws for a missing id — the
script replaced it. It proves the web layer's half of the contract: unknown-event exceptions
map to 404 with our body. The service's half lives in L4 and the single end-to-end test.
Knowing which half each test proves is the senior habit.

### Important terms

| Term | Meaning |
| --- | --- |
| `@MockitoBean` | Create a Mockito mock of the type and register it as a bean in the test context. |
| Bean override | Replacing or supplying a context bean just for a test. |
| Mock reset | Scripts are cleared between test methods. |

### Where it is used

- The standard partner of `@WebMvcTest` for every controller test that needs a service stand-in.
- Under `@SpringBootTest`, to replace one awkward bean (payment gateway, mail sender) while the
  rest stays real — with a caching cost, shown in Lesson 14.

### Common mistakes

- Forgetting to script the mock → `null` → status 200 → `Status expected:<404> but was:<200>`.
- Writing `@MockBean` from an old tutorial — deprecated; use `@MockitoBean`.
- Confusing `@Autowired` (fetch existing bean) with `@MockitoBean` (create and register one).
- Expecting one test method's script to apply in the next — mocks reset between methods.

### Link to the subtask

**S2 done.** The same assertion exists two ways: `L4FullContext404Test` (full context, 1.992 s,
246 beans) and `L8WebSlice404Test` (web slice, 0.925 s, 129 beans, no database). Lesson 9
records the numbers in `NOTES.md` (S3) and makes the ×300 argument (S4).

### Your doubts

None raised for this lesson.

### Summary for revision

- `@MockitoBean` = Mockito mock + bean registration, done before the context starts.
- Same assertions as the full-context test; one added `when(...)` line replaces the real
  service's behaviour.
- Numbers: 0.925 s vs 1.992 s startup; 129 vs 246 beans; no database in the slice.
- Unscripted mock → `null` → 200. Identical test configs share one context.

### Practice questions (with answers)

**Question 1.** Here is a complete test. The mock is scripted for one exact id, but the request
uses a freshly random id.

```java
@WebMvcTest(EventController.class)
class ExactIdTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    @Test
    void unknownIdGives404() throws Exception {
        UUID knownId = UUID.randomUUID();
        when(service.byId(knownId)).thenThrow(new EventNotFoundException());

        mockMvc.perform(get("/events/{id}", UUID.randomUUID()))
               .andExpect(status().isNotFound());
    }
}
```

Pass or fail? If it fails, what does the message say, and what chain of events produces that
status?

**Answer.** It fails with `Status expected:<404> but was:<200>`. The script matches only
`knownId`; the request carries a different random id, so the call matches no script and the
mock answers the default `null`. The controller returns `null`, no exception flies, the advice
never runs, and Spring answers 200. Fix: script with `any()`, or send `knownId` in the request.

**Question 2.** In that test class, what does `@Autowired` do for the `MockMvc` field, and what
does `@MockitoBean` do for the `EventService` field?

**Answer.** `@Autowired` fetches the `MockMvc` bean that the slice already created and placed
in the context. `@MockitoBean` creates a Mockito mock of `EventService`, registers it as a bean
the context otherwise lacks, and puts the same mock into the field so the test can script it.

### Do it yourself — Lesson 8 (second laptop)

1. Create `src/test/java/com/zenith/events/L8WebSlice404Test.java` with the contents from
   "The example" above.

2. Create `src/test/java/com/zenith/events/L8SliceBeanCountTest.java`:

   ```java
   package com.zenith.events;

   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
   import org.springframework.context.ApplicationContext;
   import org.springframework.test.context.bean.override.mockito.MockitoBean;

   // Lesson 8, side measurement: how big is the WEB SLICE context?
   // Compare with L6BeanCountTest's full-context number.
   @WebMvcTest(EventController.class)
   class L8SliceBeanCountTest {

       @Autowired
       ApplicationContext context;

       @MockitoBean
       EventService service;   // the stand-in that lets the slice start at all

       @Test
       void countTheBeans() {
           System.out.println(">>> Beans in the WEB SLICE context: "
                   + context.getBeanDefinitionCount());
       }
   }
   ```

3. Run both:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest='L8*'
   ```

   Expected: `Started L8SliceBeanCountTest in ~1 second`, `>>> Beans in the WEB SLICE context:
   129`, a `RequestLogFilter ... -> 404` line, both green — and only **one** `Started` line for
   the two classes.

4. Failure drill: comment out the `when(...)` line in `L8WebSlice404Test`, run
   `mvn test -Dtest=L8WebSlice404Test`, expect `Status expected:<404> but was:<200>`.
   Uncomment, rerun, confirm green.

5. **Done when:** both tests green, slice startup and bean count written next to the
   full-context numbers, and you can explain the drill's 200 in one sentence.

## Lesson 9 — Measure both, write it down, say the consequence (serves S3, S4 — completes both)

### Definition

Turn the observed costs into recorded evidence: per test kind, two numbers — the context
startup the class prints (`Started ... in X seconds`) and the total `mvn test` time — written
into `NOTES.md`, plus the one multiplication that makes the argument.

### Why it is needed

The learning outcome says "defend the choice with a measured number". Numbers survive code
review; taste does not. The task's "done when" requires the timing difference to be recorded.

### Key points — measuring honestly

- `Started ... in X s` = context startup only. `Total time` = the whole bill (JVM, compile
  check, context, test).
- One class per fresh `mvn test -Dtest=X` run — context caching inside a shared suite makes
  later classes look absurdly cheap.
- Never measure the first-ever run (dependency downloads). Expect ±0.1–0.2 s wobble.

### The example — the real runs

```text
=== RUN A: full context ===
INFO ... : Started L4FullContext404Test in 1.852 seconds (process running for 2.424)
[INFO] Total time:  3.793 s

=== RUN B: web slice ===
INFO ... : Started L8WebSlice404Test in 0.925 seconds (process running for 1.482)
[INFO] Total time:  2.761 s
```

Whole-suite run for perspective (9 tests, 6 Spring test classes):

```text
INFO ... : Started L4FullContext404Test in 1.801 seconds
INFO ... : Started L6BeanCountTest in 0.211 seconds
INFO ... : Started L8SliceBeanCountTest in 0.205 seconds
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Total time:  4.342 s
```

### Walkthrough — from numbers to argument

- Gap per class: ≈ **0.9 s** startup (1.852 vs 0.925), ≈ 1 s total (3.793 vs 2.761) — on a toy
  app with 246 beans and an in-memory database. This is the smallest the gap ever is.
- ×300 classes ≈ **4.6 minutes** of pure startup on the toy. Real apps: full context 10–30 s,
  slice ~1 s → **50–150 minutes against ~5** for the same 300 classes. A suite that slow stops
  being run; a suite not run protects nothing.
- The suite run shows the honesty rule: six Spring classes, only three `Started` lines
  (identical configurations shared contexts), and `L6BeanCountTest` "started" in 0.211 s warm
  versus 1.953 s fresh. Measuring inside a shared suite understates the true cost.

All recorded in `F7.5-testing-a-spring-application-with-slices/NOTES.md`.

### Important terms

| Term | Meaning |
| --- | --- |
| Context startup | Time to build the context, printed as `Started ... in X s`. |
| Total time | The whole `mvn test` bill, including JVM and compilation. |
| Fresh run | A new `mvn` invocation: own JVM, no cached context. |

### Where it is used

- CI time budgets ("suite under five minutes") are met with slices.
- Review arguments backed by checkable numbers.
- The same honesty rules apply to every performance claim.

### Common mistakes

- Trusting a single run; comparing a warm run against a cold one.
- Measuring inside the shared suite, where caching hides the cost.
- Letting the toy-app gap shrink the conclusion — the multiplication and real-app scaling are
  the argument.

### Link to the subtask

**S3 done** (both numbers for both kinds in `NOTES.md`). **S4 done** (the consequence computed
and written; say it out loud — the checkpoint asks you to quote your numbers).

### Your doubts

None raised for this lesson.

### Summary for revision

- Full context: started 1.852 s, total 3.793 s. Web slice: started 0.925 s, total 2.761 s.
- Gap ≈ 0.9 s/class → ×300 ≈ 4.6 min on a toy; real apps 50–150 min vs ~5.
- Measure per class in fresh runs; caching flatters suite-internal numbers.

### Practice questions (with answers)

**Question 1.** A team measures: a full-context test class starts in 12 seconds; a web-slice
class starts in 0.8 seconds. Their suite has 250 test classes, all full-context. If 200 could
become slices, roughly how much startup time would one suite run save?

**Answer.** Each converted class saves 12 − 0.8 = 11.2 s. 200 × 11.2 s = 2240 s ≈ **37
minutes per run**. Conclusion in one sentence: converting those 200 classes turns a
start-up-dominated suite into one people actually run, saving about 37 minutes every time.

**Question 2.** A teammate runs the two measurements on this scratch app, gets 1.9 s against
0.9 s, and says: "One second of difference. Slices are not worth it." Give two reasons the
conclusion is wrong, defensible with numbers from this task.

**Answer.** First, the gap scales with app size: this toy's full context (246 beans, in-memory
H2, no migrations) is the cheapest it will ever be; real contexts cost 10–30 s while slices
stay near 1 s. Second, the gap multiplies by class count: even the toy's 0.9 s × 300 classes is
~4.6 minutes of pure startup, and at real-app costs it becomes 50–150 minutes against ~5. (Also
valid: careless full-context use defeats caching, compounding the cost — Lesson 14.)

### Do it yourself — Lesson 9 (second laptop)

1. Measure, one command at a time:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L4FullContext404Test
   mvn test -Dtest=L8WebSlice404Test
   ```

   Note each run's `Started ... in X seconds` and `Total time`.

2. Create `~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/NOTES.md`
   (next to `scratch-app`) with your numbers:

   ```markdown
   # F7.5 — Measured numbers (S3, S4)

   | Test class | Context | Started ... in | Total mvn test |
   |---|---|---|---|
   | L4FullContext404Test | @SpringBootTest + @AutoConfigureMockMvc | ___ s | ___ s |
   | L8WebSlice404Test    | @WebMvcTest + @MockitoBean              | ___ s | ___ s |

   Gap per class: ~___ s. At 300 classes: ~___ minutes of pure startup, on a toy app.
   Real apps: full context 10–30 s vs slice ~1 s → 300 classes: ~50–150 min vs ~5 min.
   Conclusion: a suite that slow stops being run; narrowest context wins.
   ```

3. Optional: run `mvn test` and count the `Started` lines — fewer lines than Spring test
   classes = context caching in action.

4. **Done when:** `NOTES.md` has both rows from your own runs and you can say the S4 sentence
   from memory with your numbers in it.

## Lesson 10 — The web slice proves the F6.8 error contract (serves S5 — completes it)

### Definition

No new annotation. The known tools — `@WebMvcTest`, `@MockitoBean`, `MockMvc`, `jsonPath` —
applied to the three behaviours built in F6.8: validation failures return 400 **and name the
bad fields**, domain exceptions return 409 or 422, and every error wears the same shape
`{ errorId, status, code, message, fieldErrors }`.

### Why it is needed

The error rules are a **contract** clients rely on:

| Status | When | Example | `code` |
| --- | --- | --- | --- |
| **400** | Request defective **as a document** (unreadable, or fails validation) | blank `name` | `malformed_request` / `validation_failed` |
| **409** | Request fine, but **clashes with current state** | seat A12 already taken | `seat_already_reserved` |
| **422** | Request fine, but **breaks a business rule** | publishing with no price tier | `event_not_publishable` |

A frontend highlights the broken field using `fieldErrors[].field`; clients retry differently
for 409 than 422. No compiler guards any of this — only tests. And the whole contract is pure
web-layer behaviour (request in, JSON out, no database), so the slice is exactly the right size.

### Key points — two new MockMvc moves

```java
mockMvc.perform(post("/events")                     // POST instead of GET
        .contentType(MediaType.APPLICATION_JSON)     // "the body is JSON" — forget it: 415
        .content(body))                              // the body itself, a String
```

- `post(...)`, `put(...)`, `delete(...)` come from the same `MockMvcRequestBuilders` as `get(...)`.
- Java text blocks (`"""…"""`) keep JSON bodies readable.
- When validation fails, the request is rejected **before the controller method body runs** —
  the mocked service is never touched. These tests exercise only web machinery.

### The example

`src/test/java/com/zenith/events/L10ErrorShapeTest.java`:

```java
@WebMvcTest(EventController.class)
class L10ErrorShapeTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;   // needed so the slice can build the controller

    @Test
    void blankNameGives400AndNamesTheField() throws Exception {
        String body = """
                {
                  "name": "",
                  "venueId": "7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01",
                  "salesOpenAt": "2030-01-01T10:00:00Z",
                  "startsAt": "2030-02-01T19:00:00Z",
                  "capacity": 150
                }
                """;

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("validation_failed"))
               .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void unreadableJsonGives400WithMalformedCode() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void publishingWithoutPriceTierGives422() throws Exception {
        mockMvc.perform(post("/events/{id}/publish", UUID.randomUUID()))
               .andExpect(status().isUnprocessableEntity())
               .andExpect(jsonPath("$.code").value("event_not_publishable"));
    }

    @Test
    void reservingTakenSeatGives409() throws Exception {
        mockMvc.perform(post("/events/{id}/seats/{seat}", UUID.randomUUID(), "A12"))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.code").value("seat_already_reserved"))
               .andExpect(jsonPath("$.message").value("Seat A12 is already reserved."));
    }
}
```

Design choices: in the first test **only** `name` is invalid (2030 dates satisfy "must be in
the future"), so `fieldErrors` has exactly one entry and `[0]` is predictable. Publish and
seat endpoints throw inside the controller, so no `when(...)` stubbing is needed. The seat
test pins `message` too, because the seat number in it is data clients read.

### Walkthrough — real run

```text
Started L10ErrorShapeTest in 0.998 seconds
RequestLogFilter : POST /events -> 400 (116 ms)
RequestLogFilter : POST /events/af0f5550-.../seats/A12 -> 409 (3 ms)
RequestLogFilter : POST /events -> 400 (1 ms)
RequestLogFilter : POST /events/7fe4e6dc-.../publish -> 422 (0 ms)
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

One ≈1 s slice startup, four fake requests in milliseconds, and the F6.8 filter logging each
one — proof filters run in the slice.

**Failure drill.** Change the publish test to expect 409 (`isConflict()`):

```text
Tests run: 4, Failures: 1 <<< FAILURE!
java.lang.AssertionError: Status expected:<409> but was:<422>
  L10ErrorShapeTest.publishingWithoutPriceTierGives422:60
```

The app is right: "no price tier" breaks a business rule (422), it does not clash with another
reservation (409). The test now guards the 409-versus-422 distinction. (Restored; green.)

Bonus sighting: `L8WebSlice404Test` and `L10ErrorShapeTest` share the exact configuration
(`@WebMvcTest(EventController.class)` + `@MockitoBean EventService`), so in a full suite run
Spring started **one** context and both classes reused it — context caching working for us.

### Important terms

| Term | Meaning |
| --- | --- |
| `post(...)` / `content(...)` | Build a fake POST request and give it a body. |
| `contentType(...)` | Sets the `Content-Type` header; without it: 415. |
| Error contract | The documented statuses, codes, and shape clients depend on. |
| Text block | `"""…"""` multi-line string; keeps JSON bodies readable. |

### Where it is used

Public APIs document their error bodies; slice tests pin every documented case. When a web
form highlights exactly the field you got wrong, a `fieldErrors[].field` test keeps it working.

### Common mistakes

- Forgetting `contentType` → mysterious **415** instead of the expected status.
- Expecting 422 for broken JSON — unreadable means defective as a document → **400**.
- Pinning long human messages instead of stable `code` values and field names.
- Testing validation via `@SpringBootTest` out of habit: the full 246-bean bill for behaviour
  that never leaves the web layer.

### Link to the subtask

**S5 done:** validation 400 with field names, 409 and 422 domain failures, and the shared
error shape — all proven in a web slice that starts in about a second.

### Your doubts

None raised for this lesson.

### Summary for revision

- `post(...).contentType(APPLICATION_JSON).content(json)` sends a body; text blocks for JSON.
- Validation rejects before the controller body runs — the mock is never touched.
- 400 = defective document, 409 = state clash, 422 = rule break; tests guard the distinction.
- Assert stable things: statuses, `code`, field names.

### Practice questions (with answers)

**Question 1.** Rules: 400 = request defective as a document (unreadable or fails validation);
409 = clashes with current state; 422 = valid document but breaks a business rule. Give status
and likely `code` for: (a) body is `{ "name": ` cut off mid-JSON; (b) `capacity` is `-5`;
(c) reserving a seat someone holds; (d) publishing an event that has no price tier.

**Answer.** (a) **400 `malformed_request`** — unreadable as a document. (b) **400
`validation_failed`** with `fieldErrors` naming `capacity` — readable but fails validation.
(c) **409 `seat_already_reserved`** — fine request, clashes with current state. (d) **422
`event_not_publishable`** — fine document, breaks a business rule.

**Question 2.** An API has `DELETE /events/{id}` which, for an unknown id, returns 404 with
body `{"status":404,"code":"event_not_found","message":"Event not found"}`. `delete(...)`
works exactly like `get(...)`/`post(...)`. Write the complete test method.

**Answer.**

```java
@Test
void deletingUnknownEventGives404() throws Exception {
    mockMvc.perform(delete("/events/{id}", UUID.randomUUID()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.code").value("event_not_found"))
           .andExpect(jsonPath("$.message").value("Event not found"));
}
```

(`delete` is imported from `MockMvcRequestBuilders`, like `get` and `post`. No body is needed,
so no `contentType`/`content`.)

### Do it yourself — Lesson 10 (second laptop)

1. Create `src/test/java/com/zenith/events/L10ErrorShapeTest.java` with the class above and
   these imports:

   ```java
   package com.zenith.events;

   import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

   import java.util.UUID;

   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
   import org.springframework.http.MediaType;
   import org.springframework.test.context.bean.override.mockito.MockitoBean;
   import org.springframework.test.web.servlet.MockMvc;
   ```

2. Run:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L10ErrorShapeTest
   ```

   Expect `Tests run: 4, Failures: 0` and four `RequestLogFilter` lines: 400, 409, 400, 422.

3. **Drill:** change `isUnprocessableEntity()` to `isConflict()` in the publish test, rerun.
   Expect `Status expected:<409> but was:<422>`. Say why 422 is right, restore, rerun green.

4. **Done when:** four tests green and you can say which web machinery each test exercises
   (validation, JSON parsing, exception handler ×2).

## Lesson 11 — `@DataJpaTest`: the repository slice (serves S6 — completes it)

### Definition

`@DataJpaTest` starts only the **data layer**: `@Entity` classes, repository interfaces, an
**embedded in-memory database** (H2), and a transaction around each test that is **rolled back**
at the end. No controllers, no advice, no filters, no services.

| Started | Not started |
| --- | --- |
| Entities, repositories | Controllers, `@RestControllerAdvice`, filters |
| Embedded H2 database | Services (`EventService` does not exist here) |
| Per-test transaction with rollback | The real database |

### Why it is needed

Two data-layer things genuinely deserve tests:

- **Derived queries.** `findByVenueIdOrderByStartsAtAsc` holds real logic — filter by venue,
  sort by start — but Spring Data generated the SQL from the method name. Only a test shows
  the generated query doing what the name promises.
- **Mappings.** Does `EventEntity` round-trip to the `events` table, id generation included?

Embedded DB + automatic rollback make these tests self-contained and repeatable: nothing to
install, nothing to clean up, no test polluting the next.

### Key points — how it works

1. **Database swap:** at startup the slice replaces the datasource — the log literally says
   `Replacing 'dataSource' DataSource bean with embedded version`. Hibernate creates the
   `events` table from the entity. SQL printing is switched on by default.
2. **Derived-name parsing:** `findBy` + `VenueId` + `OrderBy` + `StartsAt` + `Asc` is parsed
   **at context startup** — so a typo in a method name stops the boot (see drill).
3. **Rollback cycle:** transaction opens before each test, everything the test writes is
   rolled back after it. Next test sees a clean table.

### The example

`src/test/java/com/zenith/events/L11RepositorySliceTest.java`:

```java
@DataJpaTest
class L11RepositorySliceTest {

    @Autowired
    EventRepository repository;

    @Test
    void findsOnlyOurVenueOrderedByStartTime() {
        UUID ourVenue = UUID.randomUUID();
        UUID otherVenue = UUID.randomUUID();

        // Saved out of order on purpose: the late show goes in first.
        repository.save(EventEntity.of("Late show", ourVenue,
                Instant.parse("2030-05-01T10:00:00Z"),
                Instant.parse("2030-06-01T20:00:00Z"), 300));
        repository.save(EventEntity.of("Early show", ourVenue,
                Instant.parse("2030-01-01T10:00:00Z"),
                Instant.parse("2030-02-01T19:00:00Z"), 150));
        repository.save(EventEntity.of("Someone else's show", otherVenue,
                Instant.parse("2030-03-01T10:00:00Z"),
                Instant.parse("2030-04-01T18:00:00Z"), 80));

        List<EventEntity> found = repository.findByVenueIdOrderByStartsAtAsc(ourVenue);

        assertEquals(2, found.size());                      // other venue filtered out
        assertEquals("Early show", found.get(0).name());    // ascending by startsAt
        assertEquals("Late show", found.get(1).name());
    }

    @Test
    void tableIsEmptyAgainBecauseEveryTestRollsBack() {
        assertEquals(0, repository.count());
    }
}
```

The data is designed to catch lies: the late show goes in first (a query without `ORDER BY`
would return it first) and a decoy event sits at another venue (a query without the `WHERE`
would return three). The second test is meaningful only because the first inserted rows —
rollback is why it still sees 0.

### Walkthrough — real run

```text
Replacing 'dataSource' DataSource bean with embedded version
Started L11RepositorySliceTest in 1.476 seconds
Hibernate: insert into events (capacity,name,sales_open_at,starts_at,venue_id,id) values (?,?,?,?,?,?)
Hibernate: insert into events (capacity,name,sales_open_at,starts_at,venue_id,id) values (?,?,?,?,?,?)
Hibernate: insert into events (capacity,name,sales_open_at,starts_at,venue_id,id) values (?,?,?,?,?,?)
Hibernate: select ... from events ee1_0 where ee1_0.venue_id=? order by ee1_0.starts_at
Hibernate: select count(*) from events ee1_0
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

The generated `select` carries exactly the `where venue_id=?` and `order by starts_at` the
method name promised. The final `count(*)` finds **0 rows** right after three inserts — that
green assertion is the rollback, visible.

**Failure drill.** Adding a repository method with a typo — `findByVenuOrderByStartsAtAsc` —
and rerunning:

```text
Error creating bean with name 'eventRepository' ...
Caused by: org.springframework.data.mapping.PropertyReferenceException:
    No property 'venu' found for type 'EventEntity'
```

The context refused to boot, before any test ran: derived names are parsed at startup, so a
misspelled query method fails loudly the moment any repository test runs. (Removed; green.)

### Important terms

| Term | Meaning |
| --- | --- |
| `@DataJpaTest` | Slice: entities + repositories + embedded DB + rollback per test. |
| Embedded database | In-memory H2 standing in for the real database. |
| Derived query | SQL generated by Spring Data from a method name. |
| Rollback | Undo of the test's transaction; writes vanish after each test. |

### Where it is used

Every repository with custom query methods: derived names, hand-written `@Query` methods,
pagination/sorting rules, mapping checks after schema changes.

### Common mistakes

- Expecting data to survive between tests — each method's writes roll back.
- Testing framework one-liners (`save`, `findById`) — test *your* decisions instead.
- Trusting the embedded database too much — it is H2, not Postgres (next lesson).
- Autowiring a service — the slice has none: `NoSuchBeanDefinitionException`.

### Link to the subtask

**S6 done:** two events for one venue plus a decoy, the derived method proven to filter and
order, and the rollback demonstrated by `count() == 0` in the following test.

### Your doubts

None raised for this lesson.

### Summary for revision

- `@DataJpaTest` = entities + repositories + embedded H2 + rollback per test.
- Design test data to catch lies: wrong insert order, decoy rows.
- Derived names are parsed at startup — typos stop the boot with `No property 'x' found`.
- SQL printing is on: read the generated query, see the `where` and `order by`.

### Practice questions (with answers)

**Question 1.** In a `@DataJpaTest` class, test A saves three rows and passes. Test B, in the
same class, only asserts `repository.count() == 0`. Does B pass, and what exactly made it pass?

**Answer.** B passes. Spring opened a transaction before test A, and when A finished it
**rolled that transaction back** — all three inserts were undone. B then got its own fresh
transaction over an empty table. (B would pass in any run order, which is what makes the
class safe.)

**Question 2.** The entity has properties `name`, `venueId`, `startsAt`, `capacity`. Write the
derived method for: "all events at a given venue with capacity greater than a given number,
biggest capacity first."

**Answer.**

```java
List<EventEntity> findByVenueIdAndCapacityGreaterThanOrderByCapacityDesc(
        UUID venueId, int capacity);
```

Reading: `findBy` `VenueId` (= first parameter) `And` `CapacityGreaterThan` (= second
parameter) `OrderBy` `Capacity` `Desc`.

### Do it yourself — Lesson 11 (second laptop)

1. Create `src/test/java/com/zenith/events/L11RepositorySliceTest.java` with the class above
   and these imports:

   ```java
   package com.zenith.events;

   import static org.junit.jupiter.api.Assertions.assertEquals;

   import java.time.Instant;
   import java.util.List;
   import java.util.UUID;

   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
   ```

2. Run:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L11RepositorySliceTest
   ```

   Expect `Tests run: 2, Failures: 0`, the `Replacing 'dataSource'` line, three inserts, and a
   `select` containing `where ... venue_id=? order by ... starts_at`.

3. **Drill:** add to `EventRepository` (note the typo `Venu`):

   ```java
   List<EventEntity> findByVenuOrderByStartsAtAsc(UUID venueId);
   ```

   Rerun: expect `No property 'venu' found for type 'EventEntity'` and no tests run. Delete
   the line, rerun green.

4. **Done when:** both tests green, you can point at the SQL line proving filter + order, and
   you can explain in one sentence why `count()` returned 0 in the second test.

## Lesson 12 — The embedded database is not your real database (serves S7 — completes it)

### Definition

A limitation to say out loud every time a `@DataJpaTest` passes: the tests ran against **H2**
(a small in-memory database written in Java), while production runs **Postgres**. The log
admits it: `Replacing 'dataSource' DataSource bean with embedded version`. A green
`@DataJpaTest` proves "my query works **on H2**" — nothing more.

### Why it matters

The swap is a fair trade (no install, 1-second start, vanishes after the run), but H2 and
Postgres are different engines. The danger has two flavors:

- **False alarm:** red on H2, fine on Postgres. Annoying but visible.
- **False confidence:** green on H2, different on Postgres in production. Invisible until a
  customer finds it. This is the dangerous one.

### Key points — where the two databases part ways

Safe on H2: **derived queries and JPQL** — Hibernate translates those into each database's
dialect itself.

Not safe:

| Area | Example | What H2 does |
| --- | --- | --- |
| Postgres-only SQL | `insert ... on conflict do nothing`, `returning` | Syntax error |
| Postgres-only types | `jsonb`, arrays | Missing or different |
| Locking tricks | `select ... for update skip locked` | Different or absent |
| Migration DDL | Flyway scripts with Postgres-specific DDL | Chokes — tests then generate the schema from entities, so the test schema is not the production schema |

### The example — the lie, run for real

A native query with Postgres's "insert, skip if the id exists" syntax (production Postgres
runs this happily) was added to the repository temporarily:

```java
@Modifying
@Query(value = "insert into events (id, name, venue_id, sales_open_at, starts_at, capacity) "
        + "values (?1, ?2, ?3, ?4, ?5, ?6) on conflict (id) do nothing", nativeQuery = true)
void insertIgnoringDuplicates(UUID id, String name, UUID venueId,
        Instant salesOpenAt, Instant startsAt, int capacity);
```

(`nativeQuery = true` = "run my SQL text as-is, do not translate" — exactly why the dialect
suddenly matters.) A `@DataJpaTest` called it once:

```text
Caused by: org.h2.jdbc.JdbcSQLSyntaxErrorException: Syntax error in SQL statement
  "insert into events (...) values (?, ?, ?, ?, ?, ?) [*]on conflict (id) do nothing"
Tests run: 1, Failures: 0, Errors: 1
```

### Walkthrough — reading the failure

- `org.h2.jdbc.` — the complaint comes from the stand-in, not from production.
- `[*]` is H2 pointing at the first word it cannot read: `on conflict`, the Postgres-only part.
- This is the **false alarm** flavor. Flip it for the worse one: SQL that H2 accepts but runs
  with slightly different rules (type coercion, collation, locking) — green test, different
  production behavior, and no error message will ever point at it.

Honest responses, in order: keep `@DataJpaTest` to portable JPA (derived queries, JPQL); when
Postgres behavior itself must be tested, use a **real Postgres** via **Testcontainers** (a
disposable Postgres in Docker per test run — a later task). H2's "PostgreSQL compatibility
mode" narrows the gap but does not close it.

### Important terms

| Term | Meaning |
| --- | --- |
| H2 | In-memory stand-in database used by `@DataJpaTest`. |
| Dialect | A database's own flavor of SQL beyond the shared core. |
| `nativeQuery = true` | Run the SQL text as written — no translation. |
| False confidence | Green on H2, different on Postgres. The dangerous flavor. |
| Testcontainers | Tool that starts a real database in Docker for tests (later task). |

### Where it is used

The judgment call "embedded database or real one?" happens on every team: a fast H2-backed
slice suite for portable queries, a smaller Testcontainers suite for native SQL, migrations,
and locking.

### Common mistakes

- Saying "the database tests pass" when you mean "the H2 tests pass".
- Slice-testing native Postgres SQL anyway — false alarms, or quietly testing nothing.
- Tests generating the schema while production runs migrations — silent drift.
- Overcorrecting: demanding real Postgres for every repository test. Portable derived queries
  on H2 are a fair trade; that is why the slice exists.

### Link to the subtask

**S7 done:** limitation understood, demonstrated with a real H2 refusal, recorded in
`NOTES.md` with Testcontainers named as the later fix.

### Your doubts

None raised for this lesson.

### Summary for revision

- Green `@DataJpaTest` = "works on H2". Portable JPA: trustworthy. Native SQL, Postgres
  types, locking, migration DDL: not.
- False alarm (red on H2, fine on PG) is annoying; false confidence (green on H2, different
  on PG) is dangerous.
- Real-dialect testing needs a real database: Testcontainers, later.

### Practice questions (with answers)

**Question 1.** (a) A repository test with native SQL `select * from events for update skip
locked` fails on embedded H2, though the SQL is valid Postgres. (b) A repository test passes
on H2, but production Postgres sorts the queried names differently because the two databases
compare text differently. Which is which, and which is more dangerous?

**Answer.** (a) is the **false alarm** — the code is right, the stand-in cannot read it; the
redness is visible and gets fixed. (b) is the **false confidence** — the suite stays green
while production behaves differently. (b) is more dangerous because nothing ever turns red;
the customer is the first to notice.

**Question 2.** A teammate says: "All our `@DataJpaTest`s are green, so our queries work in
production." Rewrite the claim so it is exactly true, and name one query kind where the
original could be wrong.

**Answer.** Exactly true version: "All our `@DataJpaTest`s are green, so our queries work
**on the embedded H2 database**." The original can be wrong for any native query — for
example an `insert ... on conflict` upsert, a `jsonb` filter, or `for update skip locked` —
because those depend on Postgres's dialect, which H2 does not share.

### Do it yourself — Lesson 12 (second laptop)

1. Add the drill method above to `EventRepository` (imports: `java.time.Instant`,
   `org.springframework.data.jpa.repository.Modifying`,
   `org.springframework.data.jpa.repository.Query`).

2. Create `src/test/java/com/zenith/events/L12PostgresSyntaxTest.java`:

   ```java
   package com.zenith.events;

   import java.time.Instant;
   import java.util.UUID;

   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

   @DataJpaTest
   class L12PostgresSyntaxTest {

       @Autowired
       EventRepository repository;

       @Test
       void postgresUpsertSyntaxRunsOnTheEmbeddedDatabase() {
           repository.insertIgnoringDuplicates(UUID.randomUUID(), "Upsert show",
                   UUID.randomUUID(), Instant.parse("2030-01-01T10:00:00Z"),
                   Instant.parse("2030-02-01T19:00:00Z"), 100);
       }
   }
   ```

3. Run and watch H2 refuse:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L12PostgresSyntaxTest
   ```

   Expect `JdbcSQLSyntaxErrorException: Syntax error ... [*]on conflict (id) do nothing`.

4. **Delete** the test file and the repository method; `mvn test` → `Tests run: 15,
   Failures: 0`.

5. Append the S7 note to `NOTES.md`: green `@DataJpaTest` = works on H2; portable JPA
   trustworthy; native SQL / types / locking / migration DDL not; fix later = Testcontainers.

6. **Done when:** you saw `[*]on conflict` with your own eyes, the suite is green again, and
   the note is in `NOTES.md`.

## Lesson 13 — Exactly one `@SpringBootTest`: the end-to-end happy path (serves S8 — completes it)

### Definition

The suite-design rule: **almost everything is a slice, plus exactly one full-context test**
that walks a real request through every floor — real controller, real service, real
repository, real (embedded) database, nothing mocked. Ours: create an event over HTTP, fetch
it back by the id the app generated.

### Why it is needed

The slices prove each floor separately — web layer against a **mocked** service, data layer
with **no** services. Nobody has proven the floors **connect**: that the real service bean
exists and reaches the controller, that JSON becomes a row and comes back out. Wiring is its
own bug class (a missing annotation, an unsatisfiable constructor), and slices are
structurally blind to it — they replace or omit exactly the parts being wired. One
full-context test buys that proof; a second re-buys the same proof at full price. Budget: one.

### Key points — two new tools

- **`andReturn()`** ends the `perform(...)` chain and returns an `MvcResult` — the whole
  exchange; `result.getResponse().getContentAsString()` is the raw response body.
- **`ObjectMapper`** is Jackson's JSON reader/writer; the context already has one, so
  `@Autowired` it. `objectMapper.readTree(text).get("id").asText()` walks parsed JSON.
- `@SpringBootTest` does **not** roll back database writes (unlike `@DataJpaTest`): the
  created row stays in the context's database. Fine here (fresh DB per context, random ids),
  but never assert "table is empty" in a full-context test without thinking.

### The example

`src/test/java/com/zenith/events/L13EndToEndTest.java`:

```java
@SpringBootTest
@AutoConfigureMockMvc
class L13EndToEndTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createdEventCanBeFetchedBack() throws Exception {
        String body = """
                {
                  "name": "Winter Jazz Night",
                  "venueId": "7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01",
                  "salesOpenAt": "2030-01-01T10:00:00Z",
                  "startsAt": "2030-02-01T19:00:00Z",
                  "capacity": 150
                }
                """;

        MvcResult created = mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Winter Jazz Night"))
                .andExpect(jsonPath("$.capacity").value(150));
    }
}
```

It asserts statuses, the id round-trip, a couple of fields — **not** every error case or field
(that is slice work). The e2e test checks the path, not the details.

### Walkthrough — real run

```text
Started L13EndToEndTest in 1.817 seconds
RequestLogFilter : POST /events -> 201 (113 ms)
RequestLogFilter : GET /events/f27fe71a-5f56-4a47-9619-7abc6351d433 -> 200 (25 ms)
Tests run: 1, Failures: 0, Errors: 0
```

The UUID in the GET line was invented by the database during the POST — create and fetch share
one real storage.

**Failure drill — the reason this test exists.** `@Service` removed from `EventService`, whole
suite run:

```text
Parameter 0 of constructor in com.zenith.events.EventController
    required a bean of type 'com.zenith.events.EventService' that could not be found.

Full-context classes (L2, L3, L4, L6, L13):  5 FAILURES
Web slices (L8, L8SliceBeanCount, L10):      all green (service is a mock)
Data slice (L11):                            green (no services exist here)
Plain JUnit (L1, L5):                        green (no Spring)
Tests run: 16, Errors: 5
```

Every slice slept through a genuinely broken app; only full-context tests caught it, naming
the exact constructor parameter. In a real project, where the teaching classes would not
exist, `L13EndToEndTest` would be the only guard before production. (Restored; 16/16 green.)

### Important terms

| Term | Meaning |
| --- | --- |
| End-to-end (e2e) test | One real request through all layers, nothing mocked. |
| Happy path | The expected success route, no error branches. |
| Smoke test / golden path | Team names for this one full-context test. |
| `MvcResult` / `andReturn()` | The captured exchange; ends the matcher chain. |
| `ObjectMapper` | Jackson's JSON reader/writer; `readTree` parses to a walkable tree. |

### Where it is used

One request-to-database round trip per application (sometimes per major feature) as a smoke
test; hundreds of slice tests beside it. Later the embedded database gets replaced by a real
Postgres in Docker and the same test becomes a production rehearsal.

### Common mistakes

- Letting e2e tests multiply — each with its own mocks/properties pays full startup again.
- Asserting details in the e2e test — details belong in slices; e2e proves the path.
- Forgetting `@AutoConfigureMockMvc` — `@SpringBootTest` alone has no `MockMvc`.
- Over-trusting it — the floors connect, but the database is still H2 (Lesson 12 caveat).

### Link to the subtask

**S8 done:** exactly one `@SpringBootTest` end-to-end happy path (create, then fetch by
generated id), plus the drill proving the bug class only this test catches.

### Your doubts

None raised for this lesson.

### Summary for revision

- One full-context e2e test per app: proves wiring; slices are structurally blind to it.
- `andReturn()` → `MvcResult` → response body; `ObjectMapper.readTree` to pull out the id.
- No rollback in `@SpringBootTest`; rows stay in the context's database.
- Drill evidence: `@Service` removed → slices all green, full contexts all red.

### Practice questions (with answers)

**Question 1.** A service class loses its `@Service` annotation, so Spring no longer creates
it as a bean and the controller's constructor cannot be satisfied. Three tests exist: (a) a
`@WebMvcTest` of that controller with the service replaced via `@MockitoBean`; (b) a
`@DataJpaTest` of a repository; (c) one `@SpringBootTest` end-to-end test. Which goes red, and
why do the others stay green?

**Answer.** Only **(c)** goes red — it builds the real bean graph, and the controller's
constructor parameter cannot be filled: `required a bean of type '...Service' that could not
be found`. (a) stays green because the web slice never creates real services anyway —
`@MockitoBean` plugs the hole with a mock, annotation or no annotation. (b) stays green
because the data slice contains no controllers or services at all; the broken wiring is
simply not part of what it starts.

**Question 2.** Why is the right number of `@SpringBootTest` end-to-end tests exactly one —
not zero, not five?

**Answer.** Not zero: then nothing proves the layers actually wire together, and a missing
annotation sails into production while every slice stays green. Not five: the wiring only
needs proving once — the other four would re-prove the same fact while each paying the most
expensive startup in the suite (≈2 s here, 10–30 s on real apps), and any configuration
difference between them multiplies full-price context starts.

### Do it yourself — Lesson 13 (second laptop)

1. Create `src/test/java/com/zenith/events/L13EndToEndTest.java` with the class above and
   these imports:

   ```java
   package com.zenith.events;

   import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
   import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

   import org.junit.jupiter.api.Test;
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
   import org.springframework.boot.test.context.SpringBootTest;
   import org.springframework.http.MediaType;
   import org.springframework.test.web.servlet.MockMvc;
   import org.springframework.test.web.servlet.MvcResult;

   import com.fasterxml.jackson.databind.ObjectMapper;
   ```

2. Run:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test -Dtest=L13EndToEndTest
   ```

   Expect `Tests run: 1, Failures: 0` and filter lines `POST /events -> 201` then
   `GET /events/<uuid> -> 200`.

3. **Drill:** comment out `@Service` in `EventService.java`, run the whole suite
   (`mvn test`). Expect `Errors: 5` — all full-context classes red with `required a bean of
   type ... EventService`, all slices green. Un-comment, rerun: `Tests run: 16, Failures: 0`.

4. **Done when:** the e2e test is green, you watched the slices sleep through a broken app,
   and you can name the bug class only the full-context test catches.

## Lesson 14 — Context caching: why 9 test classes started 4 contexts (serves S9 — completes it)

### Definition

Spring's test machinery keeps every started context in a **cache** for the whole test run.
Before starting a context it builds a **key** from the class's complete test configuration —
annotations and their arguments, the set of mocked beans, properties, profiles. Key already in
the cache → reuse, pay nothing. New key → full-price startup, cached for the next class. A
test class with no `Started` line of its own was riding in a cached context.

### Why it is needed

Context startup is the expensive part (Lesson 9: ≈1.9 s full, ≈0.9 s slice, fresh). Without
caching, 300 classes = 300 startups. With caching, the suite pays **per distinct
configuration, not per class** — so configuration consistency is worth real money.

### Key points — what goes into the key

- The slice annotation **and its arguments**: `@WebMvcTest(EventController.class)` ≠
  `@WebMvcTest(VenueController.class)`.
- The **set of `@MockitoBean` fields** — a different collection of mocked beans is a new
  context. (The mocks themselves reset between test methods; that is cheap and creates
  nothing new.)
- Test properties (`@TestPropertySource`, `properties = ...`) and active profiles.
- `@DirtiesContext` = "throw this context away after me" — the next class with that key pays
  a fresh start.
- Default cache size: 32 contexts, all **alive in memory** — several copies of the app at
  once. The quieter second cost of configuration sprawl.

### The example — a probe class, two runs

Probe with configuration identical to the existing web-slice classes:

```java
@WebMvcTest(EventController.class)
class L14CacheProbeTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventService service;

    @Test
    void probeRuns() throws Exception { /* one GET, expect 404 */ }
}
```

Run B differs by one line on the probe:

```java
@TestPropertySource(properties = "app.cache.demo=on")   // the ONLY change
```

### Walkthrough — the two real runs

**Run A** (probe identical) — 9 Spring test classes, 17 tests:

```text
Started L4FullContext404Test in 1.845 s
Started L6BeanCountTest in 0.223 s
Started L10ErrorShapeTest in 0.201 s
Started L11RepositorySliceTest in 0.145 s
Tests run: 17 ... Total time: 4.864 s
```

**Four** `Started` lines for nine classes. The families: `@SpringBootTest+@AutoConfigureMockMvc`
(L3, L4, L13), plain `@SpringBootTest` (L2, L6), web slice (L8, L8SliceBeanCount, L10, probe —
the probe ran in 0.020 s with **no** `Started` line), data slice (L11). The probe cost nothing.

**Run B** (one property added):

```text
Started L4FullContext404Test in 1.807 s
Started L6BeanCountTest in 0.215 s
Started L10ErrorShapeTest in 0.196 s
Started L14CacheProbeTest in 0.101 s    <-- new line
Started L11RepositorySliceTest in 0.125 s
Tests run: 17 ... Total time: 5.328 s
```

**Five** `Started` lines: same annotations, same mock, one unread property — different key,
own context, own startup.

Honesty notes: in-suite `Started` times look tiny (0.1–0.2 s) because the JVM is warm —
fresh runs tell the true price (Lesson 9 rule). And each extra context is a whole extra copy
of the slice held in memory until the run ends.

### Important terms

| Term | Meaning |
| --- | --- |
| Context cache | Started contexts kept for the whole run, reused on identical keys. |
| Cache key | The complete test configuration: annotation + args, mock set, properties, profiles. |
| `@DirtiesContext` | Marks a context as polluted; evicted after the class/method. |
| Cache size | Default 32 contexts, all alive in memory. |

### Where it is used

Teams design for cache hits: shared base classes or agreed configurations ("web-slice tests
mock exactly the service, nothing else"), review pushback on stray properties/mocks.
Configuration discipline is worth minutes per CI run.

### Common mistakes

- Believing caching makes `@SpringBootTest`-everywhere free. It only dedupes **identical**
  configurations; full-context classes accumulate differing mocks/properties, producing many
  distinct full contexts — the most expensive kind — all held in memory. That is why
  "`@SpringBootTest` on every class" is costly even with caching.
- Per-class properties or mocks "just in case" — each variation is a silent full-price start.
- `@DirtiesContext` as a habit — evicts the shared context; neighbors pay restarts.
- Measuring startup inside a shared suite — warm JVM and cache hits flatter the numbers.

### Link to the subtask

**S9 done:** caching observed (9 classes → 4 contexts), the key mechanism demonstrated
(one property: 4 → 5 `Started` lines), and the cost argument stated.

### Your doubts

None raised for this lesson.

### Summary for revision

- Cache key = complete test configuration; identical key = shared context.
- Different `@MockitoBean` set, property, profile, or annotation argument = new context.
- Mocks reset per test method (cheap); contexts persist per configuration (expensive).
- 9 classes → 4 contexts here; one unread property turned 4 into 5.

### Practice questions (with answers)

**Question 1.** Rule: identical complete test configuration = shared context; any difference
= new context. Six classes: A `@WebMvcTest(EventController.class)` + `@MockitoBean
EventService`; B exactly like A; C like A plus `@TestPropertySource(properties = "x=1")`;
D `@DataJpaTest`; E `@DataJpaTest`; F `@SpringBootTest`. How many contexts start, and which
classes share?

**Answer.** **Four.** A and B share one (identical keys); C gets its own (the property changes
the key); D and E share one (identical plain `@DataJpaTest`); F gets its own. 6 classes,
4 startups.

**Question 2.** A teammate adds a different `@TestPropertySource(properties =
"feature.<name>=true")` to each of 30 web-slice classes "to be safe"; the app reads none of
them. Before, all 30 shared one context. What did the habit cost per run, and what is the fix?

**Answer.** Each class now has a unique key, so the suite starts **30 contexts instead of 1**
— roughly 29 extra slice startups (≈29 s fresh, on the toy's ≈1 s price) plus 30 in-memory
copies of the web slice (and past 32 distinct contexts, the cache starts evicting, causing
even more restarts). Fix: delete the unread properties — or, if a property is genuinely
needed, agree on **one** shared set so every class carries the identical key again.

### Do it yourself — Lesson 14 (second laptop)

1. Count contexts:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test | grep -c ": Started L"
   ```

   Expect `4` — against 8 Spring test classes (L1, L5 are plain JUnit, no context).

2. Create the probe from the example as
   `src/test/java/com/zenith/events/L14CacheProbeTest.java` (imports as in
   `L8WebSlice404Test`). Rerun the count: expect **still 4**, probe shows no `Started` line.

3. Add to the probe (import `org.springframework.test.context.TestPropertySource`):

   ```java
   @TestPropertySource(properties = "app.cache.demo=on")
   ```

   Rerun the count: expect **5**, `Started L14CacheProbeTest` now present.

4. Delete the probe; `mvn test` → `Tests run: 16, Failures: 0`.

5. **Done when:** you saw 4 → 4 → 5 and can say in one sentence what the cache key is made of.

## Lesson 15 — The decision drill: the right annotation every time (serves S10 + checkpoint — completes the task)

### Definition

The skill the whole task built: given any testing scenario, pick between **plain JUnit**,
**`@WebMvcTest`**, **`@DataJpaTest`**, and **`@SpringBootTest`** quickly, and defend the pick
with measured numbers. No new tools — the previous fourteen lessons turned into a reflex.

### Why it is needed

The learning outcome: *choose the annotation for a scenario and defend the choice with a
measured number*. The numbers are recorded (fresh starts: slice ≈ 0.9 s, full ≈ 1.9 s on this
toy, 10–30 s on real apps). The choice happens several times a week on a real team — reviews,
planning, every new test.

### Key points — the drill, three questions in order

Ask about the **behaviour being proven**, not the class being tested:

1. **Does proving it need Spring at all?** Pure logic (maths, rules, no HTTP, no DB) →
   **plain JUnit**. The narrowest context is *none*.
2. **Which single layer owns the behaviour?** Statuses, JSON shapes, validation, exception
   mapping, filters → **`@WebMvcTest`** (+ `@MockitoBean` for the service). Queries,
   orderings, mappings → **`@DataJpaTest`** — portable JPA only; native Postgres SQL needs a
   real Postgres (Testcontainers, later task).
3. **Is the claim the wiring itself** — layers connect, request becomes row and back? The
   **one** `@SpringBootTest` end-to-end test. Budget stays at one.

Tie-breaker: if two could prove it, the cheaper one wins, by measurement.

### The example — five ticketing-backend scenarios (S10)

| # | Scenario | Right choice | Reason |
| --- | --- | --- | --- |
| 1 | Reserving seat 14B while another customer holds it must return **409** `seat_already_reserved` | `@WebMvcTest` + `@MockitoBean` | Status + error body are web-layer contract; stub the service to throw; no database |
| 2 | `findByVenueIdAndCapacityGreaterThanOrderByCapacityDesc` filters and orders correctly | `@DataJpaTest` | The generated SQL is the thing under test; embedded DB + rollback; portable JPA so H2 is fair |
| 3 | `POST /events` with capacity `0` returns **400 naming `capacity`** | `@WebMvcTest` | Validation + advice run before the service; pure web machinery |
| 4 | The app hangs together: **create an event, fetch it back** | the one `@SpringBootTest` | The claim is the wiring itself; only the full context builds the real bean graph |
| 5 | Refund maths: full refund before sales open, half after | **plain JUnit** | No HTTP, no database — any context is pure cost |

### Walkthrough — why the answers survive challenge

Scenario 1 through the drill: needs Spring (the 409 + body come from the exception handler);
web layer only (service stubbed to throw); so web slice at ≈1 s. Challenge "why not
`@SpringBootTest`, it also passes?" — same proof at 1.9 s (10–30 s real), and 300 such choices
decide minutes versus an hour. Scenario 5 is the reverse trap: a Spring annotation out of
habit where no context proves the maths in milliseconds. Scenario 2 carries the Lesson 12
asterisk: were the query native `ON CONFLICT` SQL, the honest answer is "none of the three —
real Postgres via Testcontainers, later."

Proof it all ran for real: final suite **16 tests green in ≈5 s** — two web-slice classes, one
data-slice class, one e2e, plain JUnit beside them, 4 shared contexts.

### Important terms

| Term | Meaning |
| --- | --- |
| Decision drill | Need Spring? → which layer? → is wiring the claim? Then pick cheapest. |
| Narrowest context | The smallest setup that can prove the statement — sometimes none. |
| Measured defence | Justifying the choice with recorded startup numbers, not taste. |

### Where it is used

Code review ("why is this a `@SpringBootTest`? A slice proves it"), CI budgets, interviews.
The senior move is reaching for the narrowest context *by default* and defending it with a
measurement.

### Common mistakes

- Choosing by what class is tested ("it's a controller, so…") instead of what behaviour is
  proven — refund maths lives in a service class yet needs no Spring.
- Defending with vibes instead of the two recorded numbers.
- Forgetting the standing caveats: H2 is not Postgres; the e2e budget is one.

### Link to the subtask

**S10 done** (five scenarios with reasons). With it, **S1–S10 all complete**.

### Your doubts

None raised for this lesson.

### Summary for revision

- Drill: Spring needed? → which layer? → wiring itself? Tie → cheaper.
- Web contract → `@WebMvcTest`; queries → `@DataJpaTest`; wiring → one `@SpringBootTest`;
  pure logic → plain JUnit.
- Defend with numbers: ≈0.9 s slice vs ≈1.9 s full (fresh), ×300 classes.

### Checkpoint questions (with full model answers)

**1. What does `@WebMvcTest` start, and what does it deliberately leave out?**

It starts the web layer only: the controller(s) named in the annotation, every
`@RestControllerAdvice`, registered filters, the validation machinery, the Jackson JSON
converters, and a ready-to-inject `MockMvc`. It deliberately leaves out services,
repositories, and the datasource/database — in this app that was 129 beans instead of 246.
Collaborators the controller needs are supplied as mocks via `@MockitoBean`.

**2. Quote your two measured numbers and the conclusion you drew from them.**

Fresh runs: `L4FullContext404Test` (full context) started in **1.852 s**, total `mvn test`
**3.793 s**; `L8WebSlice404Test` (web slice) started in **0.925 s**, total **2.761 s**.
Conclusion: ≈0.9–1 s saved per class on a toy app that will only ever get bigger; across 300
classes that is ≈4.6 minutes of pure startup here and 50–150 minutes on real apps — the
difference between a suite people run constantly and one they avoid.

**3. Why is `@DataJpaTest` against an embedded database not enough for this project?**

Because the embedded database is H2, not Postgres — a green test proves "works on H2".
Portable JPA (derived queries, JPQL) translates fine, but the project will need Postgres
behaviour: native SQL such as `insert ... on conflict` (H2 refused it with a syntax error —
`[*]on conflict`), Postgres types like `jsonb`, locking such as `for update skip locked`, and
real migration DDL. Those need a real Postgres; the later fix is Testcontainers running
actual Postgres in Docker for the tests.

**4. Describe three scenarios and name the correct annotation for each.**

Model set: (a) "Publishing an event with no price tier returns 422 `event_not_publishable`" →
`@WebMvcTest` + `@MockitoBean` — status and body are web contract, no data needed. (b)
"`findByVenueIdOrderByStartsAtAsc` returns only that venue's events, earliest first" →
`@DataJpaTest` — the generated SQL is under test, embedded DB + rollback suffice. (c) "A
created event can be fetched back by its generated id" → the one `@SpringBootTest` end-to-end
test — the claim is that the layers wire together. (Bonus trap: "refund percentage maths" →
plain JUnit, no Spring at all.)

**5. What is context caching, and how can careless test setup defeat it?**

Spring keeps every started test context in a cache for the whole run, keyed by the complete
test configuration (annotation + arguments, the set of `@MockitoBean` fields, properties,
profiles). Classes with identical keys share one startup — our 9 Spring test classes started
only 4 contexts. Careless setup defeats it by varying the key without need: a per-class
`@TestPropertySource` or an extra mock creates a full-price context each time (our probe: one
unread property turned 4 `Started` lines into 5), and `@DirtiesContext` evicts the shared
context so neighbours pay restarts. Inconsistent setup silently multiplies the most expensive
thing the suite does.

### Do it yourself — Lesson 15 (second laptop)

1. Verify the final state:

   ```bash
   cd ~/Projects/foundations/F7.5-testing-a-spring-application-with-slices/scratch-app
   mvn test
   ```

   Expect `Tests run: 16, Failures: 0, Errors: 0` and 4 `Started` lines.

2. Drill: cover the right-hand columns of the five-scenario table and answer from the
   three-question drill; check against the reasons given.

3. Answer the five checkpoint questions out loud without notes; check against the model
   answers above.

4. **Done when:** suite green, all five checkpoint answers come out without hesitation, and
   the two measured numbers are quoted from memory.

## Task F7.5 — final status

- **Subtasks:** S1–S10 all complete.
- **Done-when audit:** one passing test of each kind ✓ (`L8WebSlice404Test`/`L10ErrorShapeTest`
  web slice, `L11RepositorySliceTest` data slice, `L13EndToEndTest` full-context e2e); timing
  difference recorded ✓ (`NOTES.md`); annotation choice for new scenarios drilled ✓ (Lesson 15).
- **Final suite:** 16 tests, 0 failures, ≈5 s, 4 contexts, in
  `F7.5-testing-a-spring-application-with-slices/scratch-app`.
- **Artifacts:** this README (15 lessons), `NOTES.md` (measurements + S4 consequence + S7
  limitation), the scratch app with service/JPA layer and the 404 handler added for F7.5.

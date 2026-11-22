# F6.8 — Validation, one error shape, and a request log

**Goal of the task.** Reject invalid input with a clear, consistent JSON error for every
failure in the application. Write one validation rule that spans two fields. Log one
line per request with method, path, status, and duration.

**Done when:** every error from the API has one JSON shape, validation failures list the
offending fields, no stack trace reaches a client, and every request produces one log
line with its duration.

**You need first:** Task F6.7 (the request pipeline). Status codes from F4.3, especially
400 versus 422. Custom exception hierarchies from F2.10.

**The story in two sentences.** An API that returns three different error shapes is
painful to consume, and Spring's default error body can leak internals to an attacker.
This task puts a guard at the door (validation), one desk where every failure becomes
the same JSON shape (`@RestControllerAdvice`), and a diary of every request (a `Filter`).

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Bean validation** | The standard Java way of describing rules with annotations such as `@NotBlank` and `@Future`, checked by a validator. |
| **`@Valid`** | On a parameter, it means "check this object's rules before running my method". |
| **`MethodArgumentNotValidException`** | The exception thrown when a `@Valid` request body fails its rules. It carries the list of offending fields. |
| **`@RestControllerAdvice`** | A class that handles exceptions thrown by any controller, in one place. |
| **`@ExceptionHandler`** | A method inside that class, marked with the exception type it deals with. |
| **Class-level constraint** | A validation rule that looks at the whole object, needed when one field's validity depends on another. |
| **`Filter`** | Code that wraps every request, including ones that never reach a controller. Part of the servlet layer, below Spring MVC. |
| **`HandlerInterceptor`** | Similar, but inside Spring MVC, so it knows which controller method was chosen. |
| **Information leak** | Telling a caller something they should not know, such as an internal class name in a stack trace. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Put real validation rules on `CreateEventRequest` (`@NotBlank`, `@Size`, `@NotNull`, `@Future`, `@Positive`). |
| S2 | Add `@Valid` to the `@RequestBody` parameter. Send bad input. Read the default error body carefully. |
| S3 | Write one `@RestControllerAdvice` with exactly three handlers: validation → 400 with field list, domain exceptions → 409/422, catch-all → 500. |
| S4 | Make the catch-all correct: full stack trace in the server log at ERROR, only a message and an identifier in the response. |
| S5 | Prove the leak is closed: throw a deliberate `NullPointerException`, confirm no class names, paths, or stack frames reach the client. |
| S6 | Write a class-level constraint `@SalesBeforeStart`: `salesOpenAt` must be before `startsAt`. |
| S7 | Decide and write down the 400-versus-422 rule. Apply it consistently. |
| S8 | Add a request log with a `Filter`: method, path, status, duration in milliseconds. |
| S9 | Understand when to pick a `Filter` and when a `HandlerInterceptor`. |
| S10 | Break it on purpose: throw from inside an `@ExceptionHandler`. Watch the response. |
| S11 | Ask AI: `@RestControllerAdvice` returning RFC 9457 Problem Details, each field explained. |
| CP | Checkpoint: five questions, out loud. |

## Roadmap for this task — 16 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | The pain: checking input by hand | Why validation exists, and why if-checks in every method drown you. | S1 |
| 2 | Bean validation: rules as annotations | The five rules, written on the record itself. | S1 |
| 3 | `@Valid`: the check at the door | How the rules actually run, and the exception a failure throws. | S2 |
| 4 | Reading the default error body | What Spring sends when you do nothing, and why it serves nobody. | S2 |
| 5 | `@RestControllerAdvice`: one desk for all errors | The one place where every exception becomes a response. | S3 |
| 6 | Designing one error shape | The single JSON structure every failure will use. | S3 |
| 7 | Handler 1: validation failure → 400 with the field list | Turning the validation exception into a useful 400. | S3 |
| 8 | Handler 2: domain exceptions → 409 or 422 | Rebuilding a small `DomainException` family and mapping it to statuses. | S3 |
| 9 | Handler 3: the catch-all → 500 | Stack trace for you, error id for the caller, generic message for everyone. | S4 |
| 10 | Prove the leak is closed | A deliberate crash, and a response with nothing to steal. | S5 |
| 11 | A rule across two fields: class-level constraint | `@SalesBeforeStart` with a `ConstraintValidator`. | S6 |
| 12 | The 400-versus-422 rule, written down | One sentence you can apply to any future endpoint. | S7 |
| 13 | A `Filter` and the request log | One log line per request: method, path, status, duration. | S8 |
| 14 | `Filter` versus `HandlerInterceptor` | Which checkpoint sees what, and when to pick each. | S9 |
| 15 | Break it: the handler that throws | Why exception handlers must never fail. | S10 |
| 16 | RFC 9457 Problem Details, and the story retold | The standard error shape, and the whole pipeline from memory. | S11, CP |

---

## Lesson 1 — The pain: checking input by hand (subtask S1)

### Definition

**Input validation** is checking data from the outside world before your program uses
it. The check happens at the moment the data arrives, not later.

### Why it is needed

Without it, the API trusts every caller completely. The task's app (the F6.7 app with
the request record grown to five fields, no rules yet) was sent this request. Every
value in it is wrong on purpose:

```bash
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
```

Four things are broken in that body:

- `name` is three spaces. A blank name.
- `salesOpenAt` is in 2020. The past.
- `startsAt` is in 2019. Also the past — and *before* sales open. Tickets would go on
  sale for a show that already ended.
- `capacity` is -50. A hall that holds minus fifty people.

The real response:

```
HTTP/1.1 201 
Location: /events/79251ee3-2f4a-42dd-9bab-80fcedf6b0d2
Content-Type: application/json

{"id":"79251ee3-2f4a-42dd-9bab-80fcedf6b0d2","name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}
```

`201 Created`. Garbage went in, and the API celebrated. In a real system this garbage
would now sit in the database, break reports, and confuse every program that reads it
later.

### The by-hand way

The obvious fix: check each field at the top of the method with `if` statements. If a
value is bad, throw an exception to stop the method.

```java
@PostMapping
ResponseEntity<EventResponse> create(@RequestBody CreateEventRequest request) {
    if (request.name() == null || request.name().isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
    }
    if (request.name().length() > 200) {
        throw new IllegalArgumentException("name must be at most 200 characters");
    }
    if (request.venueId() == null) {
        throw new IllegalArgumentException("venueId must not be null");
    }
    if (request.salesOpenAt() == null || !request.salesOpenAt().isAfter(Instant.now())) {
        throw new IllegalArgumentException("salesOpenAt must be in the future");
    }
    if (request.startsAt() == null || !request.startsAt().isAfter(Instant.now())) {
        throw new IllegalArgumentException("startsAt must be in the future");
    }
    if (request.capacity() <= 0) {
        throw new IllegalArgumentException("capacity must be positive");
    }
    // ...only now does the real work start...
}
```

### Walkthrough — what the same garbage got from the by-hand version

The same garbage request was sent to this version. Real response:

```
HTTP/1.1 500 
Content-Type: application/json
Connection: close

{"timestamp":"2026-11-01T10:50:23.244+00:00","status":500,"error":"Internal Server Error","path":"/events"}
```

Step by step:

1. The body arrived and became a `CreateEventRequest` object (the message converter from
   F6.7 filled the record's fields).
2. The first `if` ran: `"   ".isBlank()` is `true`. The method threw
   `IllegalArgumentException("name must not be blank")`.
3. Nobody caught that exception. It flew up out of the method into Spring.
4. Spring has no idea what an uncaught exception means. It played safe and answered
   `500 Internal Server Error` — the code for "the *server* broke".

And in the server's own log:

```
ERROR 59058 --- [nio-8080-exec-2] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet
[dispatcherServlet] threw exception [Request processing failed:
java.lang.IllegalArgumentException: name must not be blank] with root cause

java.lang.IllegalArgumentException: name must not be blank
	at com.zenith.events.EventController.create(EventController.java:55)
	...
```

Count the wrongs:

| Wrong | Why it hurts |
| --- | --- |
| Wrong status: 500 | The client sent bad data, but the server confessed to a crime it did not commit. 500 means "my fault". It should say 400: "your fault" (F4.3's blame rule). |
| Useless body | The message "name must not be blank" exists — but only in the server log. The client gets no field name and no reason. It cannot fix its request. |
| One error at a time | The method stopped at the first bad field. Five bad fields need five round trips. |
| Twenty lines, one endpoint | 18 lines of checks before one line of real work. Every write endpoint needs its own copy. Copies drift; one forgotten `if` and garbage is back. |

For good input the manual version does work (a correct body got `201 Created`). The
by-hand way is not broken. It is expensive, repetitive, and rude to the client.

### Where it is used

Every serious API validates at the door. A payment API rejects a negative amount before
touching any account. A signup form rejects an email without `@` before creating a user.
The rule is always the same: check input where it enters, so everything behind the door
can trust the data.

### Common mistakes

- **Trusting the client because "our app always sends good data".** Anyone with `curl`
  can call the API. The browser form is not the only caller.
- **Validating deep inside the program instead of at the door.** The garbage travels
  through several layers before exploding, far from its cause.
- **Letting a validation failure become a 500.** A client mistake must produce a 4xx
  status. A 500 tells the client "retry later, the server is sick" — the worst possible
  advice for a request that will never be valid.

### Link to the task

This lesson is the "why" behind subtask S1 (put real rules on the request record). Both
bad worlds are now proven: no checks (garbage gets 201) and hand-written checks
(verbose, and the error turns into a wrong 500). Lesson 2 replaces the 18 lines with
five annotations. Lessons 3–7 fix the wrong status and the useless body.

### Do it yourself

1. Check your tools:

   ```bash
   java -version    # expect: openjdk version "21..."
   mvn -version     # expect: Apache Maven 3.9.x
   ```

   If missing on macOS: `brew install openjdk@21 maven`.

2. Make the F6.8 folder and copy your F6.7 app into it:

   ```bash
   cd ~/Projects/foundations    # or wherever your tasks live
   mkdir -p F6.8-validation-one-error-shape-and-a-request-log
   cp -R F6.7-the-request-pipeline-binding-input-and-shaping-the-response/scratch-app \
         F6.8-validation-one-error-shape-and-a-request-log/scratch-app
   cd F6.8-validation-one-error-shape-and-a-request-log/scratch-app
   rm -rf target
   ```

3. In `pom.xml`, change the artifactId line to `<artifactId>validation-scratch</artifactId>`.

4. Replace `src/main/java/com/zenith/events/CreateEventRequest.java` with:

   ```java
   package com.zenith.events;

   import java.time.Instant;
   import java.util.UUID;

   record CreateEventRequest(
           String name,
           UUID venueId,
           Instant salesOpenAt,
           Instant startsAt,
           int capacity) {
   }
   ```

5. Replace `src/main/java/com/zenith/events/EventResponse.java` with:

   ```java
   package com.zenith.events;

   import java.time.Instant;
   import java.util.UUID;

   record EventResponse(
           UUID id,
           String name,
           UUID venueId,
           Instant salesOpenAt,
           Instant startsAt,
           int capacity) {
   }
   ```

6. In `EventController.java`: add `import java.time.Instant;`, add the fixed venue id
   field, and update the three methods to build the six-field `EventResponse`:

   ```java
   private static final UUID BLUE_NOTE_HALL =
           UUID.fromString("7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01");

   // byId returns:
   return new EventResponse(eventId, "Indie Night", BLUE_NOTE_HALL,
           Instant.parse("2026-12-01T10:00:00Z"),
           Instant.parse("2026-12-20T19:30:00Z"), 300);

   // search's loop body becomes:
   results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, BLUE_NOTE_HALL,
           Instant.parse("2026-12-01T10:00:00Z"),
           Instant.parse("2026-12-20T19:30:00Z"), 300));

   // create becomes:
   EventResponse created = new EventResponse(UUID.randomUUID(), request.name(),
           request.venueId(), request.salesOpenAt(), request.startsAt(),
           request.capacity());
   ```

7. Build, start, and send the garbage:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   ```

   Expected: `HTTP/1.1 201` and the garbage echoed back with a fresh id.

8. Add the six `if` blocks from "The by-hand way" to the top of `create`. Stop the
   server with `Ctrl+C`, rebuild, restart, send the same garbage. Expected:
   `HTTP/1.1 500` with the body
   `{"timestamp":...,"status":500,"error":"Internal Server Error","path":"/events"}`,
   and the `name must not be blank` stack trace in the server window.

9. Send a good body. Expected: `HTTP/1.1 201`.

   ```bash
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2027-01-01T10:00:00Z","startsAt":"2027-02-01T19:30:00Z","capacity":300}'
   ```

10. Delete the six `if` blocks again and stop the server. Lesson 2 starts from the
    clean version.

**Done when:** you saw garbage get a 201, the same garbage get a 500 after the hand
checks, and you can say why both responses are wrong answers to a client mistake.

### Practice questions

**Question 1.** A Spring Boot app contains exactly this record and controller method:

```java
record RegisterRequest(String email, int age) {}

@PostMapping("/users")
ResponseEntity<String> register(@RequestBody RegisterRequest request) {
    return ResponseEntity.status(201).body("registered " + request.email());
}
```

A client sends `POST /users` with the body `{"email":"", "age":-3}`. What status code
does the client receive, and what two bad things are now true about the system?

*Answer:* The client receives **201 Created**. There is no check anywhere, so the empty
email and the negative age flow straight through. Two bad things are now true. First,
the system has accepted a user with an empty email and age -3 — garbage that every later
report, email job, and age check must now survive. Second, the client was told
"success", so nobody will ever correct the data; the API approved the mistake.

**Question 2.** A different app checks input by hand:

```java
@PostMapping("/transfers")
ResponseEntity<String> transfer(@RequestBody TransferRequest request) {
    if (request.amount() <= 0) {
        throw new IllegalArgumentException("amount must be positive");
    }
    return ResponseEntity.status(201).body("transferred");
}
```

A client sends `{"amount":-100}`. The server answers `500 Internal Server Error`. The
client's programmer reads that status and says: "the server is broken, I'll just retry
the same request every minute until it works." Why is the status code the real cause of
this bad plan, and what status *should* a request like this receive?

*Answer:* The status code is the API's only way to say whose fault a failure is. A 5xx
says "the server is sick; the same request might work later" — so retrying is a
perfectly logical reaction to a 500. But this request can *never* succeed: the data
itself breaks a rule. The truthful answer is a **400 Bad Request**: "your request is
wrong; fix it before sending it again". With a 400 (and a body naming `amount`), the
programmer would fix the number instead of hammering the server. (The finer choice
between 400 and 422 is settled in Lesson 12.)

### Summary for revision

- Validation = checking outside data at the moment it enters, before anything uses it.
- No checks → garbage gets `201 Created` and lives in your system forever.
- Hand-written `if` checks work, but: ~18 lines per endpoint, copies drift, first error
  only, and an uncaught `IllegalArgumentException` becomes a dishonest **500**.
- Client mistakes must produce **4xx**, never 500. 500 invites retries that can never
  succeed.
- The fix comes in three parts: rules as annotations (Lesson 2), a switch that runs them
  at the door (Lesson 3), and one desk that turns failures into honest JSON (Lessons
  5–9).

---

## Lesson 2 — Bean validation: rules as annotations (subtask S1)

### Definition

**Bean validation** is the standard Java way to write checking rules as annotations on
the data itself. A separate object called the **validator** reads those annotations and
checks any object handed to it. ("Bean" is an old Java word for a plain object that
carries data — the rules live on the data class, not in methods.)

### Why it is needed

Protecting one endpoint by hand cost eighteen lines of `if` statements, stopped at the
first bad field, and would need a fresh copy in every endpoint. Bean validation writes
each rule **once**, directly on the field it guards. The engine checks **all** rules in
one pass and reports **every** broken one, each with its field name and a ready-made
message. Rule and data cannot drift apart — they live on the same line.

The same rule language appeared in F6.6: rules on a configuration record made the app
refuse to start on bad config. Same words, same engine; now they guard request bodies.

### How it works

Three parts, with a clear division of labour:

1. **The rule annotations** (`@NotBlank`, `@Future`, …) are only *written marks* on the
   record. On their own they do nothing. A rule written on a door does not lock the door.
2. **The validator** is the engine. Hand it an object; it walks the fields, finds the
   marks, checks each value, and collects the failures. The standard engine is Hibernate
   Validator.
3. **A violation** is one broken rule: the field name plus a human-readable message that
   comes free with the annotation.

One Maven dependency brings the words and the engine:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

The five rules the record needs:

| Annotation | The rule it checks | When the value is `null` |
| --- | --- | --- |
| `@NotBlank` | Text is not `null` AND has at least one non-space character | `null` **fails** |
| `@Size(max = 200)` | Text length is at most 200 | `null` **passes** |
| `@NotNull` | Value is not `null` | `null` **fails** |
| `@Future` | Moment is after "now", measured at check time | `null` **passes** |
| `@Positive` | Number is greater than 0 (so 0 fails) | — (an `int` can never be `null`) |

**Most rules skip `null`.** Each annotation checks exactly one thing, and "must be
present" is its own rule (`@NotNull`). That is why `name` carries two marks: `@NotBlank`
catches missing or blank, `@Size` catches too long.

### The example — the real request record

```java
package com.zenith.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID venueId,
        @Future Instant salesOpenAt,
        @Future Instant startsAt,
        @Positive int capacity) {
}
```

Eighteen lines of `if` became five marks, each on the exact field it guards.

### Walkthrough — running the engine by hand

A temporary program ran the engine directly. No web server, no Spring — just the engine
and one garbage object:

```java
package com.zenith.events;

import java.time.Instant;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class ValidatorDemo {
    public static void main(String[] args) {
        CreateEventRequest garbage = new CreateEventRequest(
                "   ",                                   // blank name
                null,                                    // missing venue
                Instant.parse("2020-01-01T00:00:00Z"),   // sales open in the past
                Instant.parse("2019-06-15T19:30:00Z"),   // starts in the past
                -50);                                    // negative capacity

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var violations = validator.validate(garbage);
            System.out.println(violations.size() + " broken rules:");
            for (ConstraintViolation<CreateEventRequest> v : violations) {
                System.out.println("  " + v.getPropertyPath() + ": " + v.getMessage());
            }
        }
    }
}
```

Steps: `buildDefaultValidatorFactory()` finds the engine on the classpath;
`getValidator()` hands over the checking object; `validate(garbage)` walks all five
fields and returns a **set of violations** — it does not throw, and it does not stop at
the first failure.

Real output:

```
5 broken rules:
  name: must not be blank
  salesOpenAt: must be a future date
  capacity: must be greater than 0
  startsAt: must be a future date
  venueId: must not be null
```

All five failures in one pass, each named, each with a message nobody wrote. (The order
varies — it is a set, not a list.)

**The twist:** with the annotated record in place, the running web app was sent the same
garbage via `POST /events` — and still answered `201 Created` with the garbage echoed
back. In the demo program, *something called* `validator.validate(garbage)`. In the
request pipeline, **nothing calls the validator yet**. Marks without a runner are
decoration. The switch that tells Spring "run the validator on this parameter before my
method" is `@Valid` — Lesson 3.

### Where it is used

The same rule language guards every border of a Java system: request bodies in web APIs,
configuration records at startup (F6.6), and entities before they are written to a
database. Teams also run the validator directly in tests, exactly like the demo program.

### Common mistakes

- **Writing annotations and believing the API is protected.** Proven false with a real
  201. Marks without a runner are decoration.
- **Expecting `@Size` or `@Future` to reject `null`.** They pass `null` by design. Only
  `@NotNull`, `@NotBlank`, and `@NotEmpty` catch absence. Honest note about this task's
  own record: `@Future Instant salesOpenAt` alone lets a *missing* date through —
  tightened in Lesson 11 with the cross-field rule.
- **`@NotBlank` on something that is not text.** It is a String rule. For a `UUID` or a
  number, "must be present" is `@NotNull`.
- **Confusing `@Positive` with `@PositiveOrZero`.** `@Positive` rejects 0 — right for
  capacity; a hall for zero people is not an event venue.
- **Forgetting the dependency.** Without `spring-boot-starter-validation`, compilation
  fails at the import: `package jakarta.validation.constraints does not exist`.

### Link to the task

Subtask S1: the exact record from the task sheet is in the app, with real domain rules.
S1's spirit ("reject invalid input") completes when `@Valid` enforces the rules on every
request (Lesson 3).

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 1 left it (clean `create`, no `if` blocks).

1. In `pom.xml`, add next to the web starter:

   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-validation</artifactId>
   </dependency>
   ```

2. Replace `src/main/java/com/zenith/events/CreateEventRequest.java` with the annotated
   record from "The example" above.

3. Create `src/main/java/com/zenith/events/ValidatorDemo.java` with the exact code from
   the walkthrough above.

4. Compile, build the classpath list, and run the demo:

   ```bash
   mvn -q clean compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
   java -cp "target/classes:$(cat target/cp.txt)" com.zenith.events.ValidatorDemo
   ```

   Expected output (order of the five lines may differ):

   ```
   5 broken rules:
     name: must not be blank
     salesOpenAt: must be a future date
     capacity: must be greater than 0
     startsAt: must be a future date
     venueId: must not be null
   ```

5. Prove the marks alone protect nothing:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   ```

   Expected: `HTTP/1.1 201` — the garbage is still accepted.

6. Stop the server with `Ctrl+C` and delete `ValidatorDemo.java`. Keep the dependency
   and the annotated record — they are permanent.

**Done when:** the standalone engine printed all five violations in one run, the same
garbage still got a 201 from the API, and you can say in one sentence why both results
are true at the same time.

### Practice questions

**Question 1.** A record is defined with these rules, and a validator checks one object:

```java
record RoomBooking(
        @NotBlank String guestName,
        @Size(max = 5) String roomCode,
        @Positive int nights) {}

RoomBooking booking = new RoomBooking("   ", null, 0);
Set<ConstraintViolation<RoomBooking>> violations = validator.validate(booking);
```

For each of the three fields, say whether it produces a violation, and why.

*Answer:* Two violations. `guestName` **fails**: `"   "` is all spaces, and `@NotBlank`
demands at least one non-space character. `roomCode` **passes**: the value is `null`,
and `@Size` skips `null` — only `@NotNull`, `@NotBlank`, or `@NotEmpty` catch absence;
no such mark is present. `nights` **fails**: `@Positive` means strictly greater than
zero, and 0 is not greater than zero.

**Question 2.** A colleague adds rules to a record in a Spring Boot web app. The
controller is unchanged:

```java
record CancelRequest(@NotBlank String reason) {}

@PostMapping("/cancellations")
ResponseEntity<String> cancel(@RequestBody CancelRequest request) {
    return ResponseEntity.status(201).body("cancelled: " + request.reason());
}
```

He sends `POST /cancellations` with body `{"reason":""}` and receives `201 Created`. He
says: "bean validation is broken." What is the real reason for the 201, and which two
jobs in the validation story are done here versus not done?

*Answer:* Bean validation is not broken — it was never asked to run. Two jobs are done:
the **rule is written** (`@NotBlank` sits on `reason`) and the **engine is available**
(the code compiles and runs, so the validation dependency is on the classpath). The
third job is not done: **nothing triggers the check** during request handling. The
message converter builds the `CancelRequest` object and hands it straight to the method;
no one calls the validator in between. The missing trigger is `@Valid` on the
`@RequestBody` parameter.

### Summary for revision

- Bean validation = rules as annotations on the data + a validator engine that checks
  them + violations as the result.
- One dependency: `spring-boot-starter-validation` (words + engine).
- The five marks: `@NotBlank` (text present and not all spaces), `@Size(max)` (length
  cap), `@NotNull` (present), `@Future` (after now), `@Positive` (> 0, so 0 fails).
- Most marks **pass `null`**; only `@NotNull`/`@NotBlank`/`@NotEmpty` catch absence.
  Hence `@NotBlank @Size(max = 200)` together on `name`.
- `validator.validate(x)` returns **all** violations in one pass; it does not throw.
- Annotations alone are inert: the annotated record still let garbage get a 201 through
  the API, because nothing in the pipeline runs the validator without `@Valid`.

---

## Lesson 3 — `@Valid`: the check at the door (subtask S2, first half)

### Definition

**`@Valid`** is an annotation on a controller method parameter. It tells Spring: "before
you call my method, run the validator on this object. If any rule is broken, do not call
my method at all."

### Why it is needed

Lesson 2 ended with a proven problem: rules on the record, engine on the classpath, and
garbage still got `201 Created` — nothing *ran* the engine during a request. Calling
`validator.validate(...)` by hand in every method would be Lesson 1's copy-paste pain
again. `@Valid` wires the check into the pipeline, so it runs automatically, before your
code.

### How it works

The pipeline (from F6.7): the message converter turns the body's JSON into a Java
object, which becomes the `@RequestBody` parameter. `@Valid` adds one station between
those steps:

1. The converter builds the `CreateEventRequest` object. Binding must succeed first —
   unparseable JSON dies here, and the validator never sees it.
2. Because the parameter says `@Valid`, Spring hands the fresh object to the validator.
3. **No violations:** the method is called, exactly as before.
4. **Any violations:** Spring throws **`MethodArgumentNotValidException`**. The method
   is never called. The exception carries the complete list of violations — every field
   name, rejected value, and message.
5. The pipeline's exception machinery turns that exception into a `400 Bad Request`.

The check has a fixed address: after binding, before the method.

### The example — one word

```java
import jakarta.validation.Valid;

@PostMapping
ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
```

### Walkthrough — the garbage finally rejected

The same garbage as always (blank name, past dates, capacity -50, valid venueId) was
sent. Real response:

```
HTTP/1.1 400 
Content-Type: application/json
Connection: close

{"timestamp":"2026-11-01T11:37:52.060+00:00","status":400,"error":"Bad Request","path":"/events"}
```

- **400, not 201, not 500.** Rejected, with the blame on the right party. Compare: no
  checks gave a dishonest 201; hand checks gave a dishonest 500.
- **The method never ran.** No `Location` header, no created event.
- **The body is still almost useless** — no field names, no reasons (Lesson 4's topic).

A good body still returned `201 Created` with a `Location` header.

The server log tells the full story in one WARN line (shortened):

```
WARN ... .w.s.m.s.DefaultHandlerExceptionResolver :
Resolved [org.springframework.web.bind.MethodArgumentNotValidException:
Validation failed for argument [0] in ...EventController.create(...) with 4 errors:
[Field error ... on field 'capacity': rejected value [-50]; ... default message [must be greater than 0]]
[Field error ... on field 'name': rejected value [   ]; ... default message [must not be blank]]
[Field error ... on field 'salesOpenAt': ... default message [must be a future date]]
[Field error ... on field 'startsAt': ... default message [must be a future date]] ]
```

| Piece | Meaning |
| --- | --- |
| `WARN`, no stack trace | A routine rejection, not a crash — same pattern as F6.7's bad-UUID 400. |
| `Resolved [...]` | The pipeline's resolver caught the exception and translated it into a response. |
| `MethodArgumentNotValidException` | The exception `@Valid` throws: "a method argument was not valid". |
| `with 4 errors` | Four, not five — this garbage has a present, valid `venueId`. All fields checked, every failure reported in one go. |
| `Field error ... rejected value ... default message` | The exception carries everything a helpful response would need — and none of it reaches the client yet. |

That last row is the setup for Lesson 7: catch this exception ourselves and hand the
field list to the caller.

### Where it is used

Every endpoint that accepts a body — create, update, register, checkout — carries
`@Valid` on its `@RequestBody` parameter. Body parameter, `@Valid`, always. An endpoint
without it is an unlocked door.

### Common mistakes

- **Forgetting `@Valid`.** No error, no warning — the rules silently never run.
- **Believing the method receives the bad object and must answer.** The exception is
  thrown before the call; the pipeline's exception machinery builds the response.
- **Expecting the caller to see the rule messages.** The default 400 body names nothing;
  the messages live only in the exception and the server log — until a handler exists.
- **Thinking `@Valid` guards everything.** It checks the body object it is attached to.
  Bad path variables and query parameters are rejected by binding and conversion (F6.7),
  not by the validator.

### Link to the task

S1 complete: rules written *and* enforced. S2 half done: `@Valid` added, bad input sent.
The careful read of the default error body is Lesson 4.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 2 left it.

1. In `EventController.java`, add `import jakarta.validation.Valid;` and change the
   signature to `create(@Valid @RequestBody CreateEventRequest request)`.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

3. From a second terminal, garbage first, then good:

   ```bash
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'

   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2027-01-01T10:00:00Z","startsAt":"2027-02-01T19:30:00Z","capacity":300}'
   ```

   Expected: `400` (no `Location`) then `201` (with `Location`).

4. In the server window, find the WARN line
   `Resolved [MethodArgumentNotValidException: ... with 4 errors: ...]` and read all
   four `Field error` blocks.

5. Stop the server with `Ctrl+C`. Keep `@Valid` permanently.

**Done when:** garbage gets 400, good gets 201, you found all four field errors in the
log, and you can explain why the log says 4 errors while Lesson 2's standalone demo
printed 5.

### Practice questions

**Question 1.** A Spring Boot app (with the validation starter installed) contains
exactly this record and controller:

```java
record Workshop(@NotBlank String title) {}

@RestController
@RequestMapping("/workshops")
class WorkshopController {

    @PostMapping
    ResponseEntity<String> add(@Valid @RequestBody Workshop workshop) {
        return ResponseEntity.status(201).body("added " + workshop.title());
    }
}
```

Two clients call `POST /workshops` with `Content-Type: application/json`:

- Client A sends the body `{"title":""}` — valid JSON, empty title.
- Client B sends the body `{"title":}` — broken JSON that cannot be parsed.

For each client: does the `add` method run, and does the *validator* run? Name the
station that stops each request.

*Answer:* **Client A:** `add` does not run; the validator *does* run. The message
converter parses the valid JSON and builds `Workshop("")`. Because of `@Valid`, the
validator checks it, `@NotBlank` fails, and Spring throws
`MethodArgumentNotValidException` before the method call. The pipeline resolves it to
400. The stopping station is the validation step between binding and the method.
**Client B:** `add` does not run, and the validator does not run either. `{"title":}` is
not parseable JSON, so the message converter itself fails (a
`HttpMessageNotReadableException`), and the request is rejected with 400 at the binding
station — one station *earlier* than validation. Order: parse first, validate second.

**Question 2.** A teammate looks at the code from Question 1 and claims: "When client A
sends `{"title":""}`, the `add` method receives the bad `Workshop` object, notices the
broken rule, and returns the 400 response itself." Two parts of that claim are wrong.
Name both, and say what really builds the 400.

*Answer:* Wrong part one: `add` never receives the object. The exception is thrown
before the method is called; no line of `add` executes. Wrong part two: no code in the
controller builds the 400. The pipeline's exception resolver
(`DefaultHandlerExceptionResolver`) catches `MethodArgumentNotValidException`, sets
status 400, and Spring Boot's default error machinery writes the JSON body. The
controller contributed nothing to the error response — which is exactly why one central
handler (coming in Lesson 5) can change the error shape for the whole API at once.

### Summary for revision

- `@Valid` on a `@RequestBody` parameter = run the validator on the built object, before
  the method; any violation → `MethodArgumentNotValidException`, method skipped, 400.
- Order is fixed: **parse (binding) first, validate second**. Unparseable JSON never
  reaches the validator.
- The exception carries all violations (field, rejected value, message) — the server log
  shows them; the default client body does not.
- A validation failure is a WARN-level *rejection*, not an ERROR-level crash.
- Reflex: every body parameter gets `@Valid`.

---

## Lesson 4 — Reading the default error body (subtask S2, second half)

### Definition

The **default error body** is the JSON Spring Boot writes for any failed request when no
error handling has been written. It has been in every failing response since Lesson 1.

### Why it is needed (why study it)

It is what every client receives today. And it fails in two opposite directions — it
hides what honest clients need, and one config switch away it spills what attackers
want. Both failures together justify building our own error shape.

### How it works

When the pipeline rejects a request, the component that resolved the exception sets the
status code but writes **no body**. Spring Boot forwards the request internally to a
built-in address, `/error`, where a built-in controller (`BasicErrorController`) builds
a small map of facts and writes it as JSON. Three settings control which facts appear;
their defaults are the cautious ones:

| Setting | Default | What it would add |
| --- | --- | --- |
| `server.error.include-message` | `never` | The exception's message text |
| `server.error.include-binding-errors` | `never` | The list of field errors from validation |
| `server.error.include-stacktrace` | `never` | The full stack trace |

History note: older Spring Boot versions included the message by default, and teams
leaked internals without touching a setting. The defaults were locked down because of
such leaks.

### The example — the default body, dissected

Real body from the garbage request (four broken fields):

```json
{"timestamp":"2026-11-01T11:37:52.060+00:00","status":400,"error":"Bad Request","path":"/events"}
```

| Field | Value | What a client can do with it |
| --- | --- | --- |
| `timestamp` | when it failed | Nothing — they know when they sent it. |
| `status` | `400` | Repeats the status line they already have. |
| `error` | `"Bad Request"` | The dictionary name of 400. Nothing new. |
| `path` | `"/events"` | The path they themselves called. |

Zero information beyond the status line. Four broken fields; none named. A client's only
options: guess field by field, or file a ticket so somebody reads the server log.
Failure direction one: **it withholds everything useful.**

### Walkthrough — the same machinery, switched to spill

Three temporary lines in `application.yml`:

```yaml
server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: always
```

Same garbage request. The body now has keys
`timestamp, status, error, message, errors, trace, path`. Trimmed but faithful:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for object='createEventRequest'. Error count: 4",
  "errors": [
    {
      "objectName": "createEventRequest",
      "field": "capacity",
      "rejectedValue": -50,
      "defaultMessage": "must be greater than 0",
      "code": "Positive"
    }
  ],
  "trace": "org.springframework.web.bind.MethodArgumentNotValidException: Validation failed for argument [0] in org.springframework.http.ResponseEntity<com.zenith.events.EventResponse> com.zenith.events.EventController.create(com.zenith.events.CreateEventRequest) with 4 errors: [...]\n\tat org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.resolveArgument(RequestResponseBodyMethodProcessor.java:159)\n\t...",
  "path": "/events"
}
```

Real measurements from that response:

- `trace` was **6,533 characters**: 49 stack frames sent to a stranger.
- Its first line names three internal classes: `com.zenith.events.EventController`,
  `CreateEventRequest`, `EventResponse` — package naming, internal structure.
- Frames name Spring source files with line numbers
  (`RequestResponseBodyMethodProcessor.java:159`) and include 23 Tomcat frames
  (`org.apache.catalina...`). The exact serving stack is public: Spring MVC inside
  embedded Tomcat.

An attacker probing an API *wants* failed requests. With traces on, every failure is a
free reconnaissance report: framework, server, internal classes, file names, line
numbers. Known vulnerabilities are stack-specific; naming your stack tells an attacker
which keys to try. Failure direction two: **one switch away from spilling internals.**

Also worth noticing: the `errors` array *does* contain the field names and messages a
client needs — drowned in framework noise, bundled with a dangerous trace, in a shape
Boot does not promise to keep stable. The job ahead: serve exactly that useful core in a
small shape we own, with nothing else.

### Where it is used

Every Spring Boot app ships this body until someone designs errors. Real APIs replace
it: an error body is a *contract*, and Boot's default is explicitly not one (its shape
has changed between Boot versions).

### Common mistakes

- **Flipping `include-stacktrace: always` in production "to debug".** Every stranger
  gets the reconnaissance report. Debug with the server log — the trace is already there.
- **Believing the cautious default is "safe, done".** Useless to clients, and one yaml
  line away from talkative. Owning the shape is the only real fix.
- **Writing client code against the default shape.** Not a contract. Build clients only
  against error shapes the API explicitly promises.

### Link to the task

Completes S2: `@Valid` added, bad input sent, default body read carefully — it offers a
client nothing beyond the status line, and switched on, it exposed a 6,533-character
trace naming classes and stack. Sets up S4/S5: "no stack trace reaches a client" is now
a demonstrated danger.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 3 left it.

1. Build, start, send the garbage; read the default body:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -s -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   ```

   Expected: `{"timestamp":"...","status":400,"error":"Bad Request","path":"/events"}`.
   Say which of the four broken fields it names: none.

2. Stop the server. Add to `application.yml`:

   ```yaml
   server:
     error:
       include-message: always
       include-binding-errors: always
       include-stacktrace: always
   ```

3. Rebuild, restart, resend. Expected: a huge body with `message`, four `errors` blocks,
   and a `trace` thousands of characters long.

4. In `trace`, find: your class names (`com.zenith.events...`), a Spring file with line
   number (`...java:159`), an `org.apache.catalina` frame. Your structure, your
   framework, your server.

5. Delete the `server:` block. Rebuild, restart, resend, confirm the four-field default
   is back. Stop the server.

**Done when:** you saw both bodies, can name the four default fields from memory, and
can state the two failure directions: nothing a client can act on; everything an
attacker wants, one switch away.

### Practice questions

**Question 1.** A Spring Boot API (all error settings at their defaults) rejects a
request. The client developer sees exactly this:

```
HTTP/1.1 400 
Content-Type: application/json

{"timestamp":"2026-11-01T12:02:10.481+00:00","status":400,"error":"Bad Request","path":"/bookings"}
```

The request body they sent had nine fields. Can they work out from this response which
field broke which rule? If not, what are their only two ways forward — and why are both
bad for the API's team?

*Answer:* No. The body names no field and no rule; it only restates the status line,
adds the time, and echoes the path. Way forward one: trial and error — change one field
at a time and resend, up to nine rounds. Way forward two: ask the API team, who must dig
through server logs. Both are bad for the team: trial and error floods the API with
failing requests and produces frustrated integrators, and every support ticket costs an
engineer's time to answer a question the response body could have answered by itself.
The default body silently moves the cost of every client mistake onto the server team.

**Question 2.** During an incident, an engineer sets
`server.error.include-stacktrace: always` on a production API "so we can see errors
faster", and leaves it on. Name two concrete kinds of facts that any anonymous caller
can now extract just by sending one bad request, and explain in one sentence each why an
attacker values them.

*Answer:* Fact one: **internal code structure** — package and class names such as
`com.zenith.events.EventController` and `CreateEventRequest` appear in the trace; an
attacker learns how the system is organised and named, which guides targeted probing of
other endpoints and guessable resources. Fact two: **the exact serving stack** — frames
like `org.springframework.web...(...java:159)` and `org.apache.catalina...` reveal
Spring MVC inside embedded Tomcat, down to file names and line numbers; known
vulnerabilities are framework- and server-specific, so naming the stack tells the
attacker exactly which published exploits are worth trying. (Bonus wrongness: the
engineer gained nothing — the full trace was already in the server log, which is where
debugging belongs.)

### Summary for revision

- Default error machinery: resolver sets the status with no body → internal forward to
  `/error` → `BasicErrorController` writes the JSON map.
- Default fields: `timestamp`, `status`, `error`, `path` — nothing beyond the status
  line. Four broken fields, none named.
- Three switches (`include-message`, `include-binding-errors`, `include-stacktrace`),
  all `never` by default; `always` turns every failure into a reconnaissance report
  (6,533-character trace; our classes; Spring files with line numbers; 23 Tomcat frames).
- Two failure directions: withholds the useful; can spill the dangerous.
- The useful core (field + message per error) exists inside the machinery — the task is
  to serve it in a small, stable shape we own.

---

## Lesson 5 — `@RestControllerAdvice`: one desk for all errors (subtask S3)

### Definition

**`@RestControllerAdvice`** marks a class whose job is to catch exceptions thrown by
*any* controller. Inside it, each method marked **`@ExceptionHandler`** says which
exception type it deals with. Whatever such a method returns becomes the response body,
as JSON.

### Why it is needed

The alternative is try/catch inside every controller method — Lesson 1's drowning again,
for errors: shaping code copied everywhere, drifting with each copy. The task sheet
names this as exactly where people get stuck. And "one error shape for the whole API" is
only possible if one single place produces it. Controllers stay clean; every exception
lands on the same desk.

### How it works

A validation failure (or any exception) flies out of the controller layer into the
pipeline, where the exception resolver must answer: *what response does this exception
become?* With an advice class present:

1. Does any `@ExceptionHandler` match the exception's type? A handler matches its
   declared type **and every subclass**. Several matches → the **most specific type
   wins**; declaration order never matters.
2. **Match:** the method runs, receiving the exception object as argument. Its return
   value goes through the message converter and becomes the response.
3. **No match:** the default machinery takes over (status, forward to `/error`, Boot's
   four-field body).

Two details the next lessons lean on: the handler receives the exception *object* — for
validation failures it carries the full field-error list (mined in Lesson 7). And
"matches subclasses" lets one handler catch a whole `DomainException` family (Lesson 8)
and one `Exception` handler catch everything else (Lesson 9).

### The example — the desk, with a placeholder handler

```java
package com.zenith.events;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> onValidationFailure(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "validation failed"));
    }
}
```

The class is the desk. The method claims one exception type, receives it as `ex`, and
returns a `ResponseEntity` (body + status, chosen on purpose — F6.7).

### Walkthrough — the garbage request, with the desk in place

Real response to the usual garbage:

```
HTTP/1.1 400 
Content-Type: application/json

{"message":"validation failed"}
```

For the first time, the error body is **ours** — our map, our code. The log agrees,
twice. At startup (this same line said `0 @ExceptionHandler` before the class existed):

```
.m.m.a.ExceptionHandlerExceptionResolver : ControllerAdvice beans: 1 @ExceptionHandler, 1 ResponseBodyAdvice
```

And at request time:

```
.m.m.a.ExceptionHandlerExceptionResolver : Using @ExceptionHandler com.zenith.events.GlobalExceptionHandler#onValidationFailure(MethodArgumentNotValidException)
.m.m.a.ExceptionHandlerExceptionResolver : Resolved [org.springframework.web.bind.MethodArgumentNotValidException: ...]
```

Full journey: converter builds the object → validator finds four violations →
`MethodArgumentNotValidException`, `create` never called → resolver finds
`onValidationFailure` → calls it with the exception → the returned map becomes the 400
response. Same exception as before; new translator.

### Where it is used

Every real Spring API: one advice class (or a small handful) owning all error responses.
Platform teams ship a shared advice as a library so every service answers errors
identically — what this grows into in Phase 1.

### Common mistakes

- **Try/catch in every controller method.** The desk exists so controllers hold zero
  catch blocks.
- **Forgetting to set the status.** A handler returning a plain object without a status
  goes out as **200 OK** — an error body with a success code. Always state the status.
- **Several advice classes claiming the same exception.** Ambiguity. Keep one desk.
- **Expecting declaration order to matter.** The resolver picks the most specific
  *type*, wherever it is declared.

### Link to the task

S3 begun: the desk exists, handler one is a placeholder. Before upgrading it, Lesson 6
designs the one JSON shape all three handlers will share.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 4 left it (default error settings restored).

1. Create `src/main/java/com/zenith/events/GlobalExceptionHandler.java` with the exact
   code from "The example" above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

3. In the server window, find the startup line:
   `ExceptionHandlerExceptionResolver : ControllerAdvice beans: 1 @ExceptionHandler, ...`

4. Send the garbage:

   ```bash
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   ```

   Expected: `HTTP/1.1 400` with body `{"message":"validation failed"}`.

5. Find the two request-time lines: `Using @ExceptionHandler ...#onValidationFailure`
   and `Resolved [MethodArgumentNotValidException: ...]`.

6. Stop the server with `Ctrl+C`. Keep the class.

**Done when:** the garbage gets your placeholder body, you found both log lines, and you
can say how the response was produced without `create` or any try/catch running.

### Practice questions

**Question 1.** A Spring Boot app contains exactly these two classes:

```java
@RestController
class ReportController {

    @GetMapping("/reports")
    String reports() {
        throw new IllegalStateException("warehouse offline");
    }
}

@RestControllerAdvice
class ErrorDesk {

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> onIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(503).body(Map.of("message", "try again later"));
    }
}
```

A client calls `GET /reports`. (a) What status and body does the client receive, and
which method produced that body? (b) If the `ErrorDesk` class were deleted and the same
call made again, what status would the client receive, and what body shape?

*Answer:* (a) The client receives **503** with the body
`{"message":"try again later"}`. `reports()` threw and never completed; the resolver
found that `onIllegalState` claims `IllegalStateException`, called it with the
exception, and its return value became the response. (b) With no advice, no handler
matches, so the default machinery answers: **500 Internal Server Error** with Boot's
four-field body — `{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/reports"}`.

**Question 2.** One advice class contains these two handlers:

```java
@RestControllerAdvice
class ErrorDesk {

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<String> broad(RuntimeException ex) {
        return ResponseEntity.status(500).body("broad handler");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> narrow(IllegalStateException ex) {
        return ResponseEntity.status(503).body("narrow handler");
    }
}
```

`IllegalStateException` is a subclass of `RuntimeException`. `NullPointerException` is
also a subclass of `RuntimeException`, but not of `IllegalStateException`. A controller
throws an `IllegalStateException` on one request, and a `NullPointerException` on
another. Which handler answers each request, and what single rule decides it?

*Answer:* The `IllegalStateException` is answered by **`narrow`**: both handlers match
(the exception is also a `RuntimeException`), and the resolver picks the one whose
declared type is closest to the thrown class. The `NullPointerException` is answered by
**`broad`**: it is not an `IllegalStateException`, so only the `RuntimeException`
handler matches. The rule: **the most specific matching type wins, subclasses included;
declaration order plays no part.**

### Summary for revision

- `@RestControllerAdvice` = one class catching exceptions from all controllers;
  `@ExceptionHandler(X.class)` methods claim exception types; return values become JSON
  responses.
- Matching: declared type and all its subclasses; most specific type wins; order
  irrelevant; no match → Boot's default error machinery.
- The handler receives the exception object — with all its details (field errors for
  validation).
- Always set the status (`ResponseEntity`), or the error goes out as 200.
- Log evidence: startup `ControllerAdvice beans: 1 @ExceptionHandler`; per request
  `Using @ExceptionHandler ...` then `Resolved [...]`.

---

## Lesson 6 — Designing one error shape (subtask S3)

### Definition

The **error shape** is the single JSON structure that every error response from the API
uses — same field names, same meanings, for a validation failure, a business-rule
failure, or a crash. Ours is called `ApiError`.

### Why it is needed

Every error has three audiences. The client's **code** must decide mechanically: retry or
not, show which form error. The client's **human** needs one understandable sentence.
Your **own team** needs to count errors on dashboards and find the matching log line for
a ticket. A shape per endpoint means a parser per endpoint, ungroupable dashboards, and
guessing games in support — "painful to consume and impossible to monitor". One shape
serves all three audiences, and it is only achievable because one desk (Lesson 5)
produces every error.

### How it works — the design, field by field

| Field | For whom | Why it earns its place |
| --- | --- | --- |
| `errorId` | your team + support | Fresh unique id for *this occurrence*. Caller quotes it; you search the log for it. The bridge between the two worlds. |
| `status` | client code | HTTP status repeated in the body. Repetition alone was the default body's sin; alongside real content it is standard practice (bodies get logged apart from their status line; RFC 9457 includes it too). |
| `code` | client code | Short, stable, machine word for the failure kind: `validation_failed`, `seat_already_reserved`, `internal_error`. Clients switch on this, never on message text. Published codes never change. |
| `message` | client human | One plain sentence, **written by us**. Never raw exception text. |
| `fieldErrors` | client code + human | List of `{field, message}` pairs. Filled for validation; **empty list** otherwise — always present, never null. |

Deliberately **excluded**: stack traces, class names, server names, file paths — the
whole Lesson 4 leak stays on the server, findable via `errorId`.

The same shape serves all three failure kinds:

| Failure | `status` | `code` (examples) | `fieldErrors` |
| --- | --- | --- | --- |
| Validation | 400 | `validation_failed` | filled |
| Domain rule | 409 or 422 | `seat_already_reserved`, `event_has_no_price_tier` | empty |
| Unexpected crash | 500 | `internal_error` | empty; generic message; `errorId` is the star |

### The example

```java
package com.zenith.events;

import java.util.List;

// The one error shape. Every error from this API, whatever its cause, is one of these.
record ApiError(
        String errorId,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors) {

    record FieldError(String field, String message) {}
}
```

A record is the right tool (F3.6): pure data, immutable, and its shape *is* the contract.

### Walkthrough — the shape on the wire

The placeholder handler body became:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException ex) {
    ApiError body = new ApiError(
            UUID.randomUUID().toString(),
            400,
            "validation_failed",
            "Request validation failed.",
            List.of());
    return ResponseEntity.badRequest().body(body);
}
```

Real response to the usual garbage:

```
HTTP/1.1 400 
Content-Type: application/json

{"errorId":"743b57a5-5101-46bd-85e1-465f2d47eda5","status":400,"code":"validation_failed","message":"Request validation failed.","fieldErrors":[]}
```

Client code: 400 + `validation_failed` → no retry, form problem. Human: one clean
sentence. Support: quote `743b57a5...`. Honest gap: `fieldErrors` is empty — the list
sits inside `ex`, mined in Lesson 7.

### Where it is used

Stripe-style error objects (stable `code`, human message, request id), company-wide
error contracts shipped as shared libraries, and the standardised version of the same
idea: RFC 9457 Problem Details (Lesson 16). This `ApiError` is the seed of the Phase 1
platform contract.

### Common mistakes

- **A different shape per endpoint or failure kind.** One shape, filled differently.
- **Copying `ex.getMessage()` into `message`.** Exception text is for developers, can
  carry internals, and changes between library versions. Author every sentence.
- **Renaming a published `code`.** Clients switch on it; renames break them silently.
  Codes are append-only.
- **`fieldErrors: null` instead of `[]`.** Forces null-checks on every client. Always
  the list, possibly empty.

### Link to the task

The backbone of S3 and of the task's headline: one error shape for the whole API. Live
for validation failures; Lessons 7–9 fill it for all three failure kinds.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 5 left it.

1. Create `src/main/java/com/zenith/events/ApiError.java` with the record from "The
   example" above.

2. In `GlobalExceptionHandler.java`, replace the handler with the version from the
   walkthrough; replace the `java.util.Map` import with `java.util.List` and
   `java.util.UUID`.

3. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

4. Send the garbage:

   ```bash
   curl -i -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   ```

   Expected: `HTTP/1.1 400` with the five-field body: fresh `errorId` UUID,
   `"status":400`, `"code":"validation_failed"`,
   `"message":"Request validation failed."`, `"fieldErrors":[]`.

5. Send twice; the two `errorId` values must differ (one id per occurrence).

6. Stop the server. Keep both files.

**Done when:** the garbage returns the five-field shape, two runs give two different
`errorId`s, and you can name which audience each field serves.

### Practice questions

**Question 1.** An API returns errors in this shape:
`{"errorId":"...","status":400,"code":"validation_failed","message":"Request validation failed.","fieldErrors":[...]}`.
Two client teams write code against it:

```java
// Client team A
if (error.code().equals("validation_failed")) { showFormErrors(error.fieldErrors()); }

// Client team B
if (error.message().contains("validation")) { showFormErrors(error.fieldErrors()); }
```

Six months later the API team rewords the message to
`"Your request did not pass the input checks."` — the `code` is untouched. Which client
team's handling breaks, why, and what does this say about the different promises `code`
and `message` make?

*Answer:* **Team B breaks.** The new sentence no longer contains the word "validation",
so their condition is false and form errors stop appearing — no compile error, no crash,
just silently wrong behaviour. Team A keeps working: `code` was untouched. The lesson:
`message` is for humans and carries **no stability promise** — it may be reworded,
translated, or improved at any time. `code` is for machines and is a **contract** —
stable forever once published. Client logic must key on `code`; `message` is only for
display.

**Question 2.** A teammate proposes two extra fields for the shared `ApiError` shape,
"to make debugging easier": `"exceptionClass": ex.getClass().getName()` and
`"serverNode": <hostname of the machine that failed>`. Based on what the leak experiment
showed, what danger does each reintroduce — and where should those facts live instead,
so debugging still works?

*Answer:* `exceptionClass` reintroduces the **internal-structure leak**: values like
`com.zenith.events.PaymentService$CardDeclinedException` hand strangers the package
layout, naming conventions, and technology hints — exactly what the 6,533-character
trace leaked. `serverNode` leaks **infrastructure facts**: hostnames reveal naming
schemes, environments, and node counts — a map for targeting. Both facts belong in the
**server log**, written in the same log line as the `errorId`. Support flow stays
intact: caller quotes the `errorId`, an engineer searches the log, and finds the
exception class, the node, and the full trace — debugging works, strangers learn nothing.

### Summary for revision

- One shape, five fields: `errorId` (occurrence id, bridge to logs), `status` (HTTP code
  in the body), `code` (stable machine word), `message` (authored human sentence),
  `fieldErrors` (list, possibly empty, never null).
- Exclude by design: traces, class names, hosts, paths — server-log material, reachable
  via `errorId`.
- `code` is a contract (append-only); `message` is free to change — clients must switch
  on `code`.
- Same shape for validation / domain / crash; only the filling differs.

---

## Lesson 7 — Handler 1: validation failure → 400 with the field list (subtask S3)

### Definition

The real validation handler: it opens `MethodArgumentNotValidException`, reads the
validator's full report, and serves every broken field as a `{field, message}` pair in
`fieldErrors`.

### Why it is needed

The Lesson 6 response said "validation failed" but named no fields — the client still
guesses *where*. The by-hand version from Lesson 1 was crueller: one error per round
trip. Goal: **one response, every problem named**. The material already exists — the
exception carries every field, value, and message; the handler only has to serve it.

### How it works

`MethodArgumentNotValidException` wraps a **binding result** — the validator's report.
`ex.getBindingResult().getFieldErrors()` returns one entry per broken rule:

| Method | Gives | Serve it? |
| --- | --- | --- |
| `getField()` | field name, e.g. `"capacity"` | Yes — the address. |
| `getDefaultMessage()` | rule message, e.g. `"must be greater than 0"` | Yes — the reason. |
| `getRejectedValue()` | the value the client sent | **No** — the client knows what they sent; echoing bloats responses and can repeat sensitive or huge input. |

Messages ship with the annotations (`@NotBlank` carries "must not be blank"). Custom
wording goes **on the rule** (`@NotBlank(message = "name is required")`), never inside
the handler — text stays next to the rule it describes.

### The example — the upgraded handler

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException ex) {
    List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    ApiError body = new ApiError(
            UUID.randomUUID().toString(),
            400,
            "validation_failed",
            "Request validation failed.",
            fields);
    return ResponseEntity.badRequest().body(body);
}
```

### Walkthrough — three real calls

Full garbage (blank name, two past dates, capacity -50) now returns:

```json
{
    "errorId": "40232795-94c9-4f3e-a310-48c73d1eec0e",
    "status": 400,
    "code": "validation_failed",
    "message": "Request validation failed.",
    "fieldErrors": [
        { "field": "salesOpenAt", "message": "must be a future date" },
        { "field": "startsAt",   "message": "must be a future date" },
        { "field": "name",       "message": "must not be blank" },
        { "field": "capacity",   "message": "must be greater than 0" }
    ]
}
```

All four problems named and explained in one round trip — against the 201 of Lesson 1
and the bare `Bad Request` of Lesson 4. A body with only `capacity: 0` broken returns
exactly one entry (`"must be greater than 0"` — zero is not positive). A good body still
returns `201`; the handler exists only for failures. Entry order is not promised —
clients match by `field`, never by position.

### Where it is used

Form UIs walk `fieldErrors` and attach each message to its input box. Integration tests
assert on `code` plus `field` values. API docs list these pairs as the 400 contract.

### Common mistakes

- **Serving `ex.getMessage()`.** A giant one-line report naming controller class and
  method signature — unparseable for machines, leaky for strangers.
- **Echoing `getRejectedValue()`.** Can repeat a password or a huge attack payload.
  Serve addresses and reasons, not the input.
- **Relying on entry order.** It varies; match by field name.
- **Writing message text in the handler.** Custom wording lives on the annotation, next
  to its rule.

### Link to the task

S3 handler 1 of 3 complete: `MethodArgumentNotValidException` → 400 with field names and
messages. The task's "when it works you will see" for validation is delivered.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 6 left it.

1. Replace the handler method in `GlobalExceptionHandler.java` with "The example" above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

3. Three calls:

   ```bash
   # A: full garbage — expect 400 with four fieldErrors entries
   curl -s -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"   ","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'

   # B: only capacity broken (0) — expect 400 with exactly one entry
   curl -s -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2027-01-01T10:00:00Z","startsAt":"2027-02-01T19:30:00Z","capacity":0}'

   # C: good body — expect 201
   curl -i -s -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2027-01-01T10:00:00Z","startsAt":"2027-02-01T19:30:00Z","capacity":300}'
   ```

4. In A, find all four fields by name. In B, confirm capacity `0` gives
   `"must be greater than 0"`.

5. Stop the server. This handler is permanent.

**Done when:** A lists four named fields, B exactly one, C returns 201, and you can say
where each message text comes from (the annotation).

### Practice questions

**Question 1.** A Spring Boot app validates this record with `@Valid` on the request
body:

```java
record TicketOrder(
        @NotBlank String buyerEmail,
        @NotNull UUID eventId,
        @Size(max = 10) String promoCode,
        @Positive int quantity) {}
```

A client sends: `{"buyerEmail":"   ", "eventId":null, "promoCode":null, "quantity":3}`.
The handler returns `fieldErrors` as `{field, message}` pairs. Exactly which fields
appear, and why do the others not appear?

*Answer:* Two entries appear. `buyerEmail` — the value is three spaces, and `@NotBlank`
demands at least one non-space character: violation. `eventId` — the value is `null` and
the mark is `@NotNull`: violation. `promoCode` does **not** appear: it is `null`, and
`@Size` skips `null` — only `@NotNull`, `@NotBlank`, and `@NotEmpty` catch absence.
`quantity` does not appear: 3 is greater than 0, so `@Positive` passes.

**Question 2.** A colleague simplifies the validation handler to:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
ResponseEntity<String> onValidationFailure(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(ex.getMessage());
}
```

The response body becomes one giant line beginning:
`Validation failed for argument [0] in org.springframework.http.ResponseEntity<com.zenith.events.EventResponse> com.zenith.events.EventController.create(com.zenith.events.CreateEventRequest) with 4 errors: [Field error in object 'createEventRequest' on field 'capacity': rejected value [-50]; ...`.
Name the two problems this reintroduces, connecting each to something demonstrated
earlier in this task.

*Answer:* Problem one: **the information leak returns.** The text names internal classes
(`com.zenith.events.EventController`, `CreateEventRequest`, `EventResponse`) and the full
method signature — the same reconnaissance material the switched-on `trace` field leaked
in the Lesson 4 experiment, now shipped in every validation failure by default. It also
echoes rejected values (`[-50]`), which can repeat sensitive input. Problem two: **the
useless-contract problem returns.** One giant framework-formatted string: no stable
`code`, no parseable field list — a client must regex text that Spring may reword in any
upgrade, exactly the "not a contract" trap of Boot's default body. Machines cannot
consume it; humans must dig through it.

### Summary for revision

- The exception wraps the validator's report:
  `ex.getBindingResult().getFieldErrors()` — one entry per broken rule.
- Serve `getField()` + `getDefaultMessage()`; do not echo `getRejectedValue()`.
- Messages come from the annotations; custom wording belongs on the annotation.
- One response names every broken field — no more one-error-per-round-trip.
- Order of entries is not promised; clients match by field name.

---

## Lesson 8 — Handler 2: domain exceptions → 409 or 422 (subtask S3)

### Definition

The second handler on the desk. It catches our **own exception family** —
`DomainException` and everything extending it — and maps each failure to 409 or 422, in
the same `ApiError` shape as everything else.

### Why it is needed

Validation annotations check the request **by itself** (blank name, past date). Many
rules cannot be seen in the request alone: "this seat is already reserved" depends on
current state; "an event with no price tier cannot be published" is business logic. When
such a rule says no, the code throws its **own exception types** (the F2.10 idea), named
after business problems. The honest HTTP answers are neither 400 (the request was
well-formed) nor 500 (nothing broke — the system correctly said no). They are **409**
and **422**.

### How it works

A minimal rebuild of the F2.10 hierarchy:

```java
// A domain exception means: the request was understood, but a business rule said no.
abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() { return code; }
}

class SeatAlreadyReservedException extends DomainException {
    SeatAlreadyReservedException(String seatLabel) {
        super("seat_already_reserved", "Seat " + seatLabel + " is already reserved.");
    }
}

class EventNotPublishableException extends DomainException {
    EventNotPublishableException(String reason) {
        super("event_not_publishable", "Event cannot be published: " + reason);
    }
}
```

Each subclass fixes a stable machine `code` (feeds `ApiError.code`) and a message **we
authored for the caller**. This refines Lesson 6's "never serve exception text": that
rule is about *foreign* text (framework messages, written for developers). Domain
messages are ours, written for callers at the throw site — safe by construction.

One handler for the whole family (a handler matches its type **and every subclass**):

```java
@ExceptionHandler(DomainException.class)
ResponseEntity<ApiError> onDomainException(DomainException ex) {
    // 409: the request clashes with the current state (someone got there first).
    // 422: the request is understood but breaks a business rule.
    HttpStatus status = (ex instanceof SeatAlreadyReservedException)
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_ENTITY;
    ApiError body = new ApiError(
            UUID.randomUUID().toString(),
            status.value(),
            ex.code(),
            ex.getMessage(),
            List.of());
    return ResponseEntity.status(status).body(body);
}
```

The 409-versus-422 working rule:

- **409 Conflict** — the request clashes with **current state**; someone or something got
  there first; *timing is the villain*; the same request might succeed later or against a
  different resource. Textbook: reserving an already-reserved seat.
- **422 Unprocessable Entity** — well-formed, fully understood, but **breaks a business
  rule**; timing is irrelevant; resending the same request can never help. Textbook:
  publishing an event with no price tier.

Design note: the HTTP mapping lives in the **advice**, not the exceptions. The domain
family speaks business ("seat taken"); only the desk speaks HTTP ("409"). The exceptions
stay reusable where HTTP means nothing (queue consumers, scheduled jobs).

### The example — stand-in throw sites

Real throw sites arrive with a service layer in later tasks. Scratch rules: no price
tiers exist yet; seat A12 is always taken.

```java
@PostMapping("/{id}/publish")
ResponseEntity<Void> publish(@PathVariable("id") UUID eventId) {
    throw new EventNotPublishableException("it has no price tier");
}

@PostMapping("/{id}/seats/{seat}")
ResponseEntity<Void> reserveSeat(@PathVariable("id") UUID eventId,
                                 @PathVariable("seat") String seat) {
    if (seat.equals("A12")) {
        throw new SeatAlreadyReservedException(seat);
    }
    return ResponseEntity.noContent().build();
}
```

### Walkthrough — three real calls

Publish (rule breach):

```
HTTP/1.1 422 
{"errorId":"7aa23669-b36c-422b-9b12-a708402312f6","status":422,"code":"event_not_publishable","message":"Event cannot be published: it has no price tier","fieldErrors":[]}
```

Reserve A12 (state clash):

```
HTTP/1.1 409 
{"errorId":"8825b4f3-c150-4f66-b6c6-8430e464e161","status":409,"code":"seat_already_reserved","message":"Seat A12 is already reserved.","fieldErrors":[]}
```

Reserve B7 (free): `HTTP/1.1 204` — no exception, no desk.

The 409's journey: `reserveSeat` throws `SeatAlreadyReservedException` → no handler
names that exact class, but `onDomainException` claims the parent → `instanceof` picks
409 → `ApiError` built from the exception's own `code()` and message. Log evidence
(twice): `Using @ExceptionHandler ...#onDomainException(...)`.

Three failure kinds so far — validation, state clash, rule breach — three statuses, **one
identical shape**.

### Where it is used

Standard architecture: the domain layer throws business-named exceptions upward;
controllers never catch; one advice translates at the edge. Ticketing: seat clashes,
sold-out events, expired holds, refund windows — each a small exception class with a
stable code.

### Common mistakes

- **Catching domain exceptions in controllers.** The desk exists; controllers stay
  happy-path only.
- **Answering business failures with 500.** Blames the server for a correct refusal,
  invites doomed retries, and pages on-call for non-incidents.
- **Blurring 409 and 422.** One question: is *timing* the villain? Someone-got-there
  -first → 409. A rule no waiting fixes → 422.
- **Letting the `instanceof` chain rot.** Every new subclass silently falls to 422;
  revisit the mapping when adding types.

### Link to the task

S3 handler 2 of 3 done. The 409/422 half of F4.3's status knowledge is applied; Lesson
12 writes the 400-versus-422 rule down (S7).

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 7 left it.

1. Create `DomainException.java`, `SeatAlreadyReservedException.java`,
   `EventNotPublishableException.java` in `src/main/java/com/zenith/events/` — code from
   "How it works" above.

2. In `GlobalExceptionHandler.java`: add import `org.springframework.http.HttpStatus;`
   and the `onDomainException` method below the validation handler.

3. In `EventController.java`: add the two stand-in endpoints above `create`.

4. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

5. Three calls:

   ```bash
   ID="0f14d0ab-9605-4a62-a9e4-5ed26688389b"
   curl -i -X POST http://localhost:8080/events/$ID/publish      # expect 422, event_not_publishable
   curl -i -X POST http://localhost:8080/events/$ID/seats/A12   # expect 409, seat_already_reserved
   curl -i -X POST http://localhost:8080/events/$ID/seats/B7   # expect 204, empty body
   ```

6. Find `Using @ExceptionHandler ...#onDomainException(...)` twice in the server log.

7. Stop the server. Everything is permanent.

**Done when:** publish → 422, A12 → 409, B7 → 204, both error bodies share the five-field
shape, and the 409-versus-422 question ("is timing the villain?") is memorised.

### Practice questions

**Question 1.** A ticketing API uses 409 for state clashes and 422 for business-rule
breaches. Classify these two failures, each with one sentence of justification:

- (a) Two phones are logged into the same account. Both try to transfer the same ticket
  to a friend at nearly the same moment. The second transfer request fails, because the
  first one already moved the ticket.
- (b) A client sends a well-formed request to apply a student discount to a VIP ticket.
  The business rule says VIP tickets never get discounts. The request fails.

*Answer:* (a) **409 Conflict.** The second request was fine when written; it clashes
with the state the first request just created — timing is the villain, and the same
request could have succeeded a moment earlier (or later against a different ticket).
(b) **422 Unprocessable Entity.** The request is well-formed and fully understood, but a
standing business rule forbids it; no retry and no timing change can ever make it
succeed — the client must change what they are asking for.

**Question 2.** An app's advice contains exactly this handler, and
`RefundWindowClosedException extends DomainException` was just added:

```java
@ExceptionHandler(DomainException.class)
ResponseEntity<ApiError> onDomainException(DomainException ex) {
    HttpStatus status = (ex instanceof SeatAlreadyReservedException)
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_ENTITY;
    ApiError body = new ApiError(UUID.randomUUID().toString(), status.value(),
            ex.code(), ex.getMessage(), List.of());
    return ResponseEntity.status(status).body(body);
}
```

A controller throws `new RefundWindowClosedException(...)`. (a) Which handler catches
it, by what rule? (b) What status does the client receive, and which line decides it?
(c) What should the team double-check whenever they add a new `DomainException`
subclass?

*Answer:* (a) `onDomainException` catches it. No handler names
`RefundWindowClosedException`, but a handler matches its declared type **and all
subclasses** — `RefundWindowClosedException` is a `DomainException`, and this is the
most specific match available. (b) **422.** The `instanceof
SeatAlreadyReservedException` test is false, so the ternary's second arm,
`HttpStatus.UNPROCESSABLE_ENTITY`, is chosen. (c) The status mapping: any new subclass
silently falls into the 422 arm. That happens to be right for a closed refund window,
but each new type deserves a deliberate decision — extend the mapping if 422 is wrong
for it.

### Summary for revision

- Domain failures = request understood, business said no. Not 400 (well-formed), not 500
  (nothing broke): 409 or 422.
- 409 = state clash, timing is the villain. 422 = rule breach, no retry helps.
- `DomainException` root carries stable `code` + authored message (safe to serve — ours,
  unlike foreign exception text).
- One `@ExceptionHandler(DomainException.class)` catches the whole family via subclass
  matching; the mapping to HTTP lives in the advice, keeping the domain HTTP-free.
- Same `ApiError` shape, third filling: `status` 409/422, `code` from the exception,
  empty `fieldErrors`.

---

## Lesson 9 — Handler 3: the catch-all → 500 (subtask S4)

### Definition

The last handler on the desk: `@ExceptionHandler(Exception.class)`. It catches every
exception no other handler claims — bugs, surprises, unplanned failures — and turns each
into a 500 in our shape.

### Why it is needed

The first two handlers cover failures we *expect*. Real systems also fail in unplanned
ways (a null pointer, a dead connection). Without a catch-all, those fall to Boot's
default machinery: the one-shape promise breaks, there is no `errorId`, and body safety
depends on configuration staying cautious. The task marks this as **the part people get
wrong**, because the handler serves two audiences with opposite needs: **you** need
everything (the full trace, loud, in the log) and **the caller** must get almost nothing
(but one quotable thing). The design in one sentence: *the stack trace is for you; the
identifier is for the caller.*

### How it works

New tool: **SLF4J logging**. One logger per class:

```java
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
```

`log.error(...)` writes at ERROR level — the level monitoring alarms on (validation
rejections were WARN: routine). Crucial mechanic: when the **last argument** of a log
call is an exception, the logger prints its full stack trace — that is how the trace
reaches the log without touching the response.

The choreography: (1) make one fresh `errorId`; (2) `log.error` a line containing that
id, with the exception as last argument; (3) return the same `ApiError` shape — 500,
`internal_error`, a generic authored message, the **same** id, empty `fieldErrors`. The
same id in both places is the bridge: caller quotes it, you search the log for it.

Why a generic message, never `ex.getMessage()`? Evidence: the demo crash's message was
`Cannot invoke "String.trim()" because "venue" is null` — modern Java NPE messages name
source variables. Foreign exception text can carry variable names, SQL, file paths,
hostnames. None of it leaves the building.

### The example

```java
@ExceptionHandler(Exception.class)
ResponseEntity<ApiError> onUnexpected(Exception ex) {
    String errorId = UUID.randomUUID().toString();
    // The stack trace is for us: full detail, ERROR level, tagged with the errorId.
    log.error("Unhandled exception, errorId={}", errorId, ex);
    // The identifier is for the caller: nothing else leaves the building.
    ApiError body = new ApiError(
            errorId,
            500,
            "internal_error",
            "Something went wrong on our side. Quote the errorId when contacting support.",
            List.of());
    return ResponseEntity.internalServerError().body(body);
}
```

Temporary crash site (deleted after the leak check):

```java
// in PingController
@GetMapping("/boom")
String boom() {
    String venue = null;
    return venue.trim();   // NullPointerException, on purpose
}
```

### Walkthrough — the crash, both sides

Response:

```
HTTP/1.1 500 
{"errorId":"b68f55f4-384c-4c1d-8d9b-5c328c7bab8e","status":500,"code":"internal_error","message":"Something went wrong on our side. Quote the errorId when contacting support.","fieldErrors":[]}
```

Server log, same moment:

```
ERROR 60114 --- [nio-8080-exec-1] c.zenith.events.GlobalExceptionHandler   : Unhandled exception, errorId=b68f55f4-384c-4c1d-8d9b-5c328c7bab8e

java.lang.NullPointerException: Cannot invoke "String.trim()" because "venue" is null
	at com.zenith.events.PingController.boom(PingController.java:18) ~[!/:0.0.1-SNAPSHOT]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(...) ~[spring-web-6.2.19.jar!/:6.2.19]
	...
```

The response: a UUID, a code, one safe sentence. The log: the **same UUID**, the exact
file and line (`PingController.java:18`), the variable name, library versions. Support
flow: caller quotes the id → one log search → full story.

Specificity check (a common fear): the `Exception` handler does not steal from the
specific ones — most specific type wins. Verified live: validation still 400, seat clash
still 409, publish still 422.

### Where it is used

Every production service. The `errorId` is a first **correlation identifier** — the seed
of Phase 11 observability. On-call flows: alarm on ERROR count → ticket quotes id → log
search.

### Common mistakes

- **Serving `ex.getMessage()`.** Proven dangerous: the NPE message named a source
  variable.
- **Clean body but no logging.** 500s with no server-side evidence: the bug is
  unfindable. You protected the attacker and blinded yourself.
- **Logging at WARN or INFO.** Monitoring sleeps through crashes. ERROR, always.
- **Two different ids** for log and body. The bridge breaks. One variable, used twice.
- **Fearing the catch-all eats everything.** More specific handlers always win.

### Link to the task

S3 complete: one advice, exactly three handlers (400 field list / 409-422 domain / 500
catch-all). S4 complete: ERROR + full trace on the server; message + identifier only in
the body. Next: S5, the formal leak check.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 8 left it.

1. In `GlobalExceptionHandler.java`: add imports `org.slf4j.Logger` and
   `org.slf4j.LoggerFactory`, the `log` field (first line in the class), and the
   `onUnexpected` method below the other handlers.

2. In `PingController.java`: add the temporary `boom` method.

3. Rebuild, start, crash:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -i http://localhost:8080/boom
   ```

   Expected: `HTTP/1.1 500`, five-field shape, code `internal_error`, fresh `errorId`.

4. In the server window: find `Unhandled exception, errorId=<same id>`, the
   `NullPointerException ... because "venue" is null` message, and the frame
   `at com.zenith.events.PingController.boom(...)`. Ids must match exactly.

5. Confirm the specific handlers still win: validation garbage → 400; `/seats/A12` →
   409; `/publish` → 422.

6. Stop the server. Keep `boom` for the leak check.

**Done when:** the crash returns 500 in your shape, the log holds the trace tagged with
the matching id, the three specific statuses still work, and you can recite: the stack
trace is for you; the identifier is for the caller.

### Practice questions

**Question 1.** A support ticket arrives: "Your API gave me errorId `9f3c71aa-...`.
Please fix whatever broke." The server log contains these two ERROR entries, each
followed by its stack trace:

```
ERROR ... : Unhandled exception, errorId=c42d1189-...
java.lang.IllegalStateException: connection pool exhausted
	at com.zenith.events.VenueService.lookup(VenueService.java:41)
	...

ERROR ... : Unhandled exception, errorId=9f3c71aa-...
java.lang.NullPointerException: Cannot invoke "String.trim()" because "venue" is null
	at com.zenith.events.PingController.boom(PingController.java:18)
	...
```

(a) Which stack trace belongs to the ticket, and how do you know? (b) The customer also
pasted their full response body into the ticket. Why can support *not* diagnose the bug
from that body alone — and why is that a feature, not a flaw?

*Answer:* (a) The second one. The ticket quotes errorId `9f3c71aa-...`, and the log line
carrying that exact id is the one above the `NullPointerException` trace — the matching
id is the bridge; nothing else (time, endpoint, guesswork) is needed. The bug is at
`PingController.boom`, line 18. (b) By design, the body contains only the id, the stable
code, and one generic sentence — all diagnosis material (exception class, file, line,
variable) lives exclusively in the server log. That is a feature: the caller and any
attacker receive the same leak-free body, yet support loses nothing, because the id
unlocks the full story on the server side.

**Question 2.** A different team wrote their catch-all like this:

```java
@ExceptionHandler(Exception.class)
ResponseEntity<Map<String, String>> onUnexpected(Exception ex) {
    return ResponseEntity.internalServerError()
            .body(Map.of("error", ex.getMessage()));
}
```

There is no logging anywhere in the method. Using the rule "the stack trace is for you;
the identifier is for the caller", name the two separate ways this handler fails, and
describe what each failure costs in practice.

*Answer:* Failure one — **it gives the caller too much**: `ex.getMessage()` is foreign
text; a modern NPE message names source variables, a database exception can carry SQL or
hostnames. Cost: reconnaissance material in every crash response, plus a "contract" of
unstable text that clients start parsing. Failure two — **it keeps nothing for you**:
without `log.error(..., ex)` the stack trace is discarded forever, and without an
identifier there is nothing for a caller to quote. Cost: a production 500 with no trace,
no id, no ERROR entry — monitoring stays silent, support cannot correlate, and the bug
is effectively unfindable. The handler leaks what it should hide and hides what it
should keep.

### Summary for revision

- Catch-all = `@ExceptionHandler(Exception.class)`: everything unclaimed → 500 in the
  one shape. Specific handlers still win (most specific type).
- Choreography: one `errorId` → `log.error("... errorId={}", errorId, ex)` (exception
  last = trace printed) → same id in the `ApiError` body.
- ERROR level (alarms), not WARN (routine rejections).
- Generic authored message only; foreign exception text can name variables, SQL, hosts.
- The bridge: id in body = id in log. Caller quotes it; you search it.

---

## Lesson 10 — Prove the leak is closed (subtask S5)

### Definition

The **leak check**: a small, repeatable experiment proving a crash response carries no
stack frames, no class names, and no file paths. Not belief — a scan with evidence.

### Why it is needed

Lesson 9 built the protection; S5 demands proof. Claims rot: a handler edit, a config
flip, a Boot upgrade can quietly reopen the leak. A written check re-runs in thirty
seconds. Second payoff: a good check looks around — ours immediately caught an unrelated
bug (below).

### How it works

Define **leak markers** — strings that must never appear in a response body:

| Marker | What it would betray |
| --- | --- |
| `at ` (with the space) | a stack frame line |
| `.java` | a source file name |
| `Exception` | an exception class name |
| `com.zenith` | our internal packages |
| `org.springframework` | the framework |
| `tomcat` | the server |

Run the experiment **two-sided**: (1) the scan — crash on purpose, capture the body,
count each marker, want six zeros; (2) the **negative control** — count the same markers
in the server log, want plenty of hits. The control makes the zeros believable: it
proves the crash happened, the trace exists, and the scan detects these strings. A test
that cannot fail proves nothing.

### The example

```bash
curl -s http://localhost:8080/boom > crash-body.json

for m in "at " ".java" "Exception" "com.zenith" "org.springframework" "tomcat"; do
  printf '%-22s %s\n' "$m" "$(grep -c -- "$m" crash-body.json)"
done
# then the same loop against the server log
```

### Walkthrough — real results, and a caught bug

Crash body:
`{"errorId":"6eab43d8-...","status":500,"code":"internal_error","message":"Something went wrong on our side. Quote the errorId when contacting support.","fieldErrors":[]}`

| Marker | Response body | Server log |
| --- | --- | --- |
| `at ` | 0 | 51 |
| `.java` | 0 | 50 |
| `Exception` | 0 | 5 |
| `com.zenith` | 0 | 6 |
| `org.springframework` | 0 | 19 |
| `tomcat` | 0 | 30 |

Everything for us, nothing for the stranger. **S5 passed with evidence.**

**The caught bug:** probing `GET /definitely-not-a-path` returned **500**
`internal_error` and logged a full ERROR — a typo'd URL answered as a crash. Cause:
since Spring Boot 3.2, a request matching no endpoint **throws**
`NoResourceFoundException`. The resolver that consults `@ExceptionHandler` methods runs
before the built-in one, and our catch-all claims `Exception` — subclasses included. The
fix, one more mapping above the catch-all:

```java
// Boot 3.2+ turns "no such path" into an exception. Without this mapping, the
// catch-all would answer 404s with a fake 500 and page the on-call for typos.
@ExceptionHandler(NoResourceFoundException.class)
ResponseEntity<ApiError> onNoResource(NoResourceFoundException ex) {
    ApiError body = new ApiError(
            UUID.randomUUID().toString(),
            404,
            "not_found",
            "No resource at this path.",
            List.of());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}
```

Import: `org.springframework.web.servlet.resource.NoResourceFoundException`. Honest note
on "three handlers and nothing else": the three cover exceptions *our code* causes;
Boot's router nowadays speaks in exceptions too, so the desk needs this fourth mapping
or every typo becomes a fake 500. A justified, documented deviation — and a bonus: 404s
now wear the one `ApiError` shape as well.

After the fix, verified live: unknown path → `404` `not_found`, no ERROR logged; `boom`
deleted and `GET /boom` returns the same clean 404; regression sweep green (ping 200,
validation 400, seat 409, publish 422, create 201, zero `Unhandled exception` entries).

### Where it is used

Exactly what security reviews and penetration testers do: force failures, scan for
frames, versions, internal names. Mature teams encode it as a CI integration test —
Phase 1 turns this manual loop into that.

### Common mistakes

- **Skipping the negative control.** Six zeros with no proof of a crash is false comfort.
- **Checking one failure kind only.** The probe around the 500 is what caught the 404
  bug. Force several kinds.
- **Checking the wrong configuration.** Profiles (F6.5) can change error settings per
  environment; check what ships.
- **Treating the check as one-time.** Re-run whenever error handling changes — our own
  carefully built desk still held a surprise.

### Link to the task

S5 done: deliberate NPE, response verified free of class names, file paths, stack
frames. Done-when item "no stack trace reaches a client" has evidence. Bonus: unknown
paths now return the one shape too.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 9 left it (`boom` present).

1. Start, crash, scan the body:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -s http://localhost:8080/boom > crash-body.json
   for m in "at " ".java" "Exception" "com.zenith" "org.springframework" "tomcat"; do
     printf '%-22s %s\n' "$m" "$(grep -c -- "$m" crash-body.json)"
   done
   ```

   Expected: six zeros.

2. Negative control: run the same loop against the server output (start with
   `java -jar ... > server.log 2>&1` and scan `server.log`, or read the server window —
   the `at com.zenith.events.PingController.boom(...)` frame must be there). Expected:
   hits, not zeros.

3. Probe the bug: `curl -i http://localhost:8080/definitely-not-a-path`. Expected before
   the fix: `HTTP/1.1 500`, `"code":"internal_error"`, and a fresh ERROR in the log.

4. Fix: add the import and the `onNoResource` handler above `onUnexpected`.

5. Delete `boom` from `PingController.java`. Rebuild, restart.

6. Verify:

   ```bash
   curl -i http://localhost:8080/definitely-not-a-path   # 404, not_found, no ERROR logged
   curl -i http://localhost:8080/boom                    # same clean 404
   curl -i http://localhost:8080/ping                    # 200 pong
   ```

7. Stop the server.

**Done when:** six zeros in the body scan, hits in the log scan, unknown paths return
404 `not_found` in the one shape with no ERROR entry, `boom` is gone, and you can
explain why the negative control makes the zeros believable.

### Practice questions

**Question 1.** A developer runs a leak check: they crash their API on purpose, capture
the response body, and scan it for the markers `at `, `.java`, `Exception`, and their
package prefix. All counts come back zero, and they declare the leak closed — without
running any scan against the server log. Name two innocent situations in which those
zeros would be lies, and say what the log-side scan adds that closes both gaps.

*Answer:* Lie one: **the crash never happened.** A wrong URL, a server that silently
failed to start, or an endpoint that did not actually throw — the captured body is a
404, an empty file, or a success response, and scanning it yields zeros about nothing.
Lie two: **the scan itself is broken.** A misspelled marker, wrong case, a quoting
mistake in the loop, or scanning the wrong file produces zeros no matter what the body
contains. The log-side scan closes both: finding the markers there proves a real trace
was produced at that moment (the crash genuinely happened) and that this exact scan
detects those strings when they are present (the tool works). Only then do the six zeros
mean "the response is clean".

**Question 2.** An API's crash response passes the six-marker scan perfectly. The body
is:

```json
{"errorId":"77b0...","status":500,"code":"internal_error","message":"Database error: could not connect to postgres://zenith_app@10.0.3.7:5432/zenith_main"}
```

Is this response safe? If not, name exactly what it hands to an attacker, which rule
from the catch-all's design was broken, and where that information should have gone
instead.

*Answer:* Not safe. With zero stack frames it still hands an attacker a map: the
database technology (Postgres), a **credential username** (`zenith_app`), an **internal
IP and port** (`10.0.3.7:5432`), and the **database name** (`zenith_main`) — enough to
aim further attacks inside the network. The broken rule: the catch-all's `message` must
be a **generic sentence authored by us**; here someone copied foreign exception text (a
JDBC connection error) into it. The details belong in the **server log**, in the ERROR
entry tagged with `errorId 77b0...`, where support finds them after the caller quotes
the id. Mechanical marker scans help, but they do not replace the authored-message rule
— a leak can be fluent English.

### Summary for revision

- Leak check = marker scan on a forced-crash body (want six zeros) + negative control on
  the server log (want hits). The control proves the crash happened and the scan works.
- Markers: `at `, `.java`, `Exception`, your package prefix, `org.springframework`,
  `tomcat`.
- Boot 3.2+ reality: unknown paths throw `NoResourceFoundException`; a bare `Exception`
  catch-all turns every typo into a fake 500 — map it to 404 explicitly.
- Checks are cheap; re-run on every error-handling change. Scans don't replace rules:
  an authored-generic `message` matters even when no marker matches.

---

## Lesson 11 — A rule across two fields: the class-level constraint (subtask S6)

### Definition

A **class-level constraint** is a validation rule written on the whole record instead of
on one field — the tool for rules that must see two or more fields at once. Ours is
`@SalesBeforeStart`: sales must open before the event starts.

### Why it is needed

Field annotations judge one value in isolation. The rule `salesOpenAt < startsAt` is
invisible to them: `2027-06-01` is a fine future date, `2027-05-01` too; each passes
alone; only together they are nonsense. Proven on the live app: misordered future dates
returned `201 Created` — all five field rules passed, and tickets would go on sale a
month after the show. A field annotation receives one value; for a cross-field rule, the
validator must receive the **whole object**.

### How it works — three parts, all written by you

**Part one: the annotation.**

```java
@Target(ElementType.TYPE)                              // may sit on a class or record
@Retention(RetentionPolicy.RUNTIME)                    // visible to the engine while running
@Constraint(validatedBy = SalesBeforeStartValidator.class)  // the class that does the checking
public @interface SalesBeforeStart {

    // Every constraint annotation must offer these three members.
    String message() default "sales must open before the event starts";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

`@Target(TYPE)` = sits on a record/class, not a field. `@Retention(RUNTIME)` = readable
by the engine while running. `@Constraint(validatedBy = ...)` = names the checking
class. `message`/`groups`/`payload` are required members of every constraint annotation;
`message` is the violation text.

**Part two: the validator.** Implements `ConstraintValidator<A, T>` ("I check annotation
`A` on objects of type `T`") with one method `isValid` — which receives the **entire
request**, so it can read both dates.

**Part three: usage.** The record gets the mark, and the dates get `@NotNull` (closing
the Lesson 2 loophole — `@Future` passes null):

```java
@SalesBeforeStart
record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID venueId,
        @NotNull @Future Instant salesOpenAt,
        @NotNull @Future Instant startsAt,
        @Positive int capacity) {
}
```

### The naive version, and two traps (both demonstrated live)

```java
@Override
public boolean isValid(CreateEventRequest request, ConstraintValidatorContext context) {
    return request.salesOpenAt().isBefore(request.startsAt());
}
```

**Trap one — the invisible bucket.** Misordered dates → 400, but the real body showed
`"fieldErrors": []`. The binding result keeps **two lists**: field rules produce *field
errors*; class-level rules produce *global errors* — and the handler reads only
`getFieldErrors()`. A 400 that names nothing.

**Trap two — null crashes the rule into a 500.** With `startsAt` missing entirely, the
response was `HTTP/1.1 500`, and the log showed:

```
jakarta.validation.ValidationException: HV000028: Unexpected exception during isValid call.
Caused by: java.lang.NullPointerException: Cannot read field "seconds" because "otherInstant" is null
```

Why, despite `@NotNull` on the field? **No ordering promise between rules**: field rules
and class rules run in the same pass. `isValid` saw the null, `isBefore(null)` threw, the
engine wrapped it, the catch-all turned a client mistake into a fake server crash.

### The correct validator

```java
class SalesBeforeStartValidator
        implements ConstraintValidator<SalesBeforeStart, CreateEventRequest> {

    @Override
    public boolean isValid(CreateEventRequest request, ConstraintValidatorContext context) {
        // Absence is @NotNull's job. A cross-field rule only judges values that exist.
        if (request.salesOpenAt() == null || request.startsAt() == null) {
            return true;
        }
        if (request.salesOpenAt().isBefore(request.startsAt())) {
            return true;
        }
        // Report the violation on a field, so it lands in fieldErrors like every other rule.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("salesOpenAt")
                .addConstraintViolation();
        return false;
    }
}
```

The null guard returns `true` because absence is `@NotNull`'s job — each rule judges one
thing, and this prevents double-reporting. The `context` lines re-address the violation:
cancel the default object-level report, build a new one with the annotation's `message`,
pin it to `salesOpenAt`, file it. It then travels like any `@NotBlank` violation — the
handler needs no change.

Final build, real results: misordered →
`"fieldErrors":[{"field":"salesOpenAt","message":"sales must open before the event starts"}]`;
missing `startsAt` → clean 400 with `{"field":"startsAt","message":"must not be null"}`;
correct order → `201`.

### Where it is used

Start-before-end dates, min ≤ max, password equals confirmation, at-least-one-contact —
every cross-field rule is this same pattern with different arithmetic in `isValid`.

### Common mistakes

- **Checking the rule in the controller.** Works once, then drifts. Rules live with the
  data.
- **No null guard.** A crashing rule turns client mistakes into fake 500s (HV000028).
- **Trusting rule order.** There is none; never assume a sibling rule ran first.
- **Forgetting `addPropertyNode`.** The violation hides in `getGlobalErrors()`; a
  field-errors-only handler shows an empty list.
- **Missing `message`/`groups`/`payload`** — the engine rejects the constraint at
  startup.

### Link to the task

S6 done exactly as asked: `@SalesBeforeStart` + `ConstraintValidator` reading both
fields, on the record. Checkpoint question 4 answered: the rule needed a class-level
constraint because neither field alone can see the other. Lesson 2's loophole closed
(`@NotNull` on the dates).

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 10 left it.

1. Feel the hole: build, start, POST misordered future dates
   (`salesOpenAt 2027-06-01`, `startsAt 2027-05-01`, rest valid) → expect `201`. Stop.

2. Create `SalesBeforeStart.java` (annotation, from "How it works"; imports:
   `java.lang.annotation.*` pieces, `jakarta.validation.Constraint`,
   `jakarta.validation.Payload`).

3. Create `SalesBeforeStartValidator.java` — the **naive** body first (imports:
   `jakarta.validation.ConstraintValidator`, `jakarta.validation.ConstraintValidatorContext`).

4. In `CreateEventRequest.java`: `@SalesBeforeStart` on the record; `@NotNull` before
   `@Future` on both dates.

5. Rebuild, restart, walk into both traps: misordered dates → 400 with empty
   `fieldErrors`; missing `startsAt` → 500 and `HV000028` in the server window.

6. Replace the validator with the correct version. Rebuild, restart, verify: misordered
   → 400 naming `salesOpenAt`; missing `startsAt` → 400 "must not be null"; good order →
   201.

7. Stop the server. Everything is permanent.

**Done when:** you saw the hole, both traps, and the fixed behaviour, and can explain why
the null guard returns `true`.

### Practice questions

**Question 1.** A ticketing API has this record, validated with `@Valid`:

```java
record DiscountRequest(
        @NotBlank String tier,            // "REGULAR" or "VIP"
        @Positive int discountPercent) {}
```

A new business rule arrives: *VIP tickets may get at most 10 percent discount; regular
tickets up to 50.* A teammate proposes `@Max(50)` on `discountPercent`. Explain why no
annotation on `discountPercent` alone can ever implement this rule, and name the three
pieces you would build instead.

*Answer:* The allowed maximum **depends on the value of a different field** (`tier`). An
annotation on `discountPercent` receives only the number; it can never ask what `tier`
holds. `@Max(50)` wrongly accepts a VIP discount of 30 (the rule says at most 10) — it
encodes one limit for all tiers, which is not the rule. Build the class-level trio:
(1) a custom annotation, say `@DiscountWithinTierLimit`, with `@Target(TYPE)`,
`@Retention(RUNTIME)`, `@Constraint(validatedBy = ...)` and the three required members;
(2) a validator `implements ConstraintValidator<DiscountWithinTierLimit,
DiscountRequest>` whose `isValid` reads both fields (null-guard → `true`; VIP → limit
10, REGULAR → limit 50) and pins the violation to `discountPercent` via
`addPropertyNode`; (3) the annotation placed on the record.

**Question 2.** A validator for `@WindowValid` on `record Sale(Instant opensAt, Instant
closesAt)` is written as:

```java
@Override
public boolean isValid(Sale sale, ConstraintValidatorContext context) {
    return sale.opensAt().isBefore(sale.closesAt());
}
```

Both fields also carry `@NotNull`. A client sends a request with `closesAt` missing.
(a) Trace what happens, step by step, to the status the client receives — remembering
that the engine gives no ordering promise, and the app has a catch-all
`@ExceptionHandler(Exception.class)`. (b) Give the two-line fix and say which annotation
then reports the missing field.

*Answer:* (a) The converter builds `Sale(opensAt, null)`. The engine runs all rules in
one pass — `@NotNull` on `closesAt` records a violation, but the class rule still runs.
`isValid` calls `opensAt().isBefore(null)`, which throws `NullPointerException` *inside
the validator*. The engine wraps it: `ValidationException: HV000028: Unexpected
exception during isValid call`. That is not a `MethodArgumentNotValidException`, so the
validation handler does not match; it is an `Exception`, so the **catch-all** does: logs
ERROR with a trace, returns **500 `internal_error`**. A missing field — a pure client
mistake — produced a fake crash. (b) First two lines of `isValid`:
`if (sale.opensAt() == null || sale.closesAt() == null) return true;`. With the crash
gone, `@NotNull` reports `closesAt: must not be null` as an ordinary field error in a
clean 400.

### Summary for revision

- Field rules see one value; cross-field rules need the whole object: class-level
  constraint.
- Three pieces: annotation (`@Target(TYPE)`, `@Retention(RUNTIME)`,
  `@Constraint(validatedBy=...)`, three required members) + `ConstraintValidator<A, T>`
  + the mark on the record.
- Null guard returns `true`: absence is `@NotNull`'s job; a crashing rule becomes a fake
  500 (HV000028).
- No ordering promise between rules — `isValid` must survive any legal object shape.
- Default report is object-level ("global error"), invisible to field-errors-only
  handlers; `addPropertyNode("salesOpenAt")` pins it to a field.

---

## Lesson 12 — The 400-versus-422 rule, written down (subtask S7)

### Definition

A one-paragraph **written rule** deciding, for any failure, whether the API answers 400
or 422 (with 409 beside them) — plus the audit proving the whole app obeys it.

### Why it is needed

Knowledge (F4.3) does not keep an API consistent; every new endpoint re-opens the debate
unless one sentence can be quoted. "Apply consistently" means auditing **every** failure
path — and the audit caught the app answering 500 to four ordinary client mistakes.

### The rule (verbatim, for revision)

> **Ask one question: could a reader with no business knowledge reject this request?**
>
> - Defective **as a document** (unparseable JSON, wrong types, missing/blank/oversized
>   values, parameter text that cannot become its type) → **400**. No ticketing
>   knowledge needed to reject it.
> - Document perfect, but **what it asks breaks a business rule** (publish with no price
>   tier) → **422**. Only the domain can reject it; retrying the same request never
>   helps.
> - Document perfect, only **current state** says no (seat taken a second ago) →
>   **409**. Timing is the villain.

Decision ladder = pipeline order: parse → shape → business → state. Mechanics already in
place: unreadable body and type mismatches → 400; `@Valid` failures → 400;
`DomainException` family → 422 or 409.

### The audit — before

| Probe | Honest status | Actual status |
| --- | --- | --- |
| `POST /events`, body `{"name":}` (broken JSON) | 400 | **500** |
| `GET /events/not-a-uuid` | 400 | **500** |
| `POST /events`, no `Content-Type` header | 415 | **500** |
| `GET /events?limit=abc` | 400 | **500** |

Four client mistakes, four fake crashes, four false ERROR alarms. Swallowed exceptions
(named by the log): `HttpMessageNotReadableException`,
`MethodArgumentTypeMismatchException`, `HttpMediaTypeNotSupportedException`. Mechanism —
Lesson 10's discovery at full width: the resolver consulting `@ExceptionHandler` runs
**first**; a catch-all claiming `Exception` outranks the framework's built-in
rejections. **A bare catch-all steals from the framework**; each stolen kind must be
given back explicitly.

### The fix — three more mappings, same one-shape translation

```java
// The 400-versus-422 rule, applied: a request defective AS A DOCUMENT is 400.
@ExceptionHandler(HttpMessageNotReadableException.class)
ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException ex) {
    ApiError body = new ApiError(UUID.randomUUID().toString(), 400,
            "malformed_request", "Request body is not readable JSON.", List.of());
    return ResponseEntity.badRequest().body(body);
}

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ApiError body = new ApiError(UUID.randomUUID().toString(), 400,
            "invalid_parameter", "A parameter has an invalid value.",
            List.of(new ApiError.FieldError(ex.getName(), "invalid value")));
    return ResponseEntity.badRequest().body(body);
}

@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
ResponseEntity<ApiError> onUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
    ApiError body = new ApiError(UUID.randomUUID().toString(), 415,
            "unsupported_media_type", "Content-Type must be application/json.", List.of());
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
}
```

Imports: `org.springframework.http.converter.HttpMessageNotReadableException`,
`org.springframework.web.HttpMediaTypeNotSupportedException`,
`org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`.

### The audit — after (all real)

Probes: **400, 400, 415, 400.** The bad-UUID body names the parameter via
`ex.getName()`:

```json
{"errorId":"d439506f-...","status":400,"code":"invalid_parameter","message":"A parameter has an invalid value.","fieldErrors":[{"field":"id","message":"invalid value"}]}
```

Honesty sweep: validation 400 · seat clash 409 · publish 422 · unknown path 404 · good
create 201 · **zero** `Unhandled exception` entries. ERROR is reserved for genuine
crashes again.

Note on "three handlers and nothing else": the three remain the conceptual core; the
extra mappings exist because a bare `Exception` net demotes the framework's own correct
rejections. Spring ships `ResponseEntityExceptionHandler` handling this family in one go
(met in Lesson 16) — you now know by hand what it automates.

### Where it is used

API guidelines publish the rule; client SDKs branch on it (400 → fix shape, 422 → change
the ask, 409 → re-read and maybe retry); monitoring depends on it (4xx = client noise,
5xx = real fires — only works if no client mistake wears a 500).

### Common mistakes

- **Everything is 400.** The one signal that guides clients is wasted.
- **Validation as 422.** Defensible elsewhere, but pick one rule and never improvise per
  endpoint. Ours: 400 = document, 422 = rule.
- **A bare `Exception` catch-all left unaudited.** It silently relabels 400/404/415 as
  500.
- **A rule on paper without an audit.** A handful of curls makes it true; re-run on
  every error-handling change.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 11 left it.

1. Before-audit (expect four 500s and four ERROR entries):

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   curl -s -o /dev/null -w "broken JSON:  %{http_code}\n" -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" -d '{"name":}'
   curl -s -o /dev/null -w "bad UUID:     %{http_code}\n" http://localhost:8080/events/not-a-uuid
   curl -s -o /dev/null -w "wrong label:  %{http_code}\n" -X POST http://localhost:8080/events -d '{"name":"x"}'
   curl -s -o /dev/null -w "limit=abc:    %{http_code}\n" "http://localhost:8080/events?limit=abc"
   ```

2. Stop. Add the three imports and three handlers above the catch-all.

3. Rebuild, restart, re-run: expect `400, 400, 415, 400`, no new ERROR entries.

4. Honesty sweep: garbage 400, A12 409, publish 422, `/nope` 404, good create 201.

5. Write the rule into `NOTES.md`; say it from memory.

**Done when:** four 500s became 400/400/415/400, the sweep is honest, the log holds no
fake alarms, and the rule's one question comes without reading.

### Practice questions

**Question 1.** Using the rule — document defect → 400, business-rule breach → 422,
state clash → 409 — classify, one sentence each:

- (a) `POST /orders`, body `{"quantity": "many"}` — JSON parses; `"many"` cannot become
  the declared `int`.
- (b) A well-formed request to transfer a ticket to your own account; the business
  forbids self-transfers.
- (c) `POST /orders`, body `{"quantity": 2` — closing brace missing.
- (d) A well-formed request to cancel an order another device cancelled two seconds
  earlier.

*Answer:* (a) **400** — a document defect: the value cannot become its declared type;
no business knowledge needed to reject it. (b) **422** — perfectly formed and fully
understood; a standing business rule says no; retrying the identical request can never
succeed. (c) **400** — not parseable JSON at all; it dies at the parser, the emptiest
kind of document defect. (d) **409** — the document is perfect and the rule is fine;
only the current state (already cancelled) refuses it; timing is the villain.

**Question 2.** Before the fix, this app answered `500` to the broken-JSON body
`{"name":}`, although in F6.7 — before any advice existed — the same request correctly
got `400`. Explain the regression's mechanism (name the exception, the component that
used to produce the 400, and why `@ExceptionHandler(Exception.class)` changed the
outcome), then name two practical harms of a 500 for a client typo.

*Answer:* The unparseable body makes the message converter throw
`HttpMessageNotReadableException`. Without an advice, the pipeline's built-in resolver
(`DefaultHandlerExceptionResolver`) recognises that type and writes an honest 400. With
the advice present, the resolver that consults `@ExceptionHandler` methods
(`ExceptionHandlerExceptionResolver`) runs **first**, and a handler for `Exception`
matches every subclass — so the catch-all claims the exception before the built-in
resolver is ever asked, answers 500 `internal_error`, and logs a false ERROR. Harm one:
the client is told "the server is sick, retry later" for a request that can never
succeed — blame lands on the wrong side, retries hammer the API, support tickets follow.
Harm two: every typo raises an ERROR alarm, so 5xx monitoring fills with noise and real
fires get lost in it.

### Summary for revision

- The rule: *could a reader with no business knowledge reject it?* Document defect →
  **400**; rule breach → **422**; state clash → **409**. Ladder = parse → shape →
  business → state.
- A bare `Exception` catch-all steals the framework's honest 400/404/415 rejections and
  relabels them 500 — give each kind back explicitly (or use
  `ResponseEntityExceptionHandler`).
- Ticketing examples: `{"name":}` → 400; publish with no price tier → 422; seat taken a
  second ago → 409.
- Rules become true through audits — a handful of curls, re-run after every
  error-handling change.

---

## Lesson 13 — A `Filter` and the request log (subtask S8)

### Definition

A **`Filter`** wraps every request at the servlet layer — below Spring MVC, before the
`DispatcherServlet`. Ours is a **request log filter**: one line per request with method,
path, status, and duration in milliseconds. The seed of Phase 11 observability.

### Why it is needed

Nobody can run an API blind: which endpoints are called, how often they fail, with which
statuses, how slow. The humblest answer is the **access log** — one line per request, no
exceptions. "No exceptions" includes requests that never reach any controller (unknown
paths; later, requests rejected by authentication). Only something below Spring MVC sees
all of those, so the job belongs to a `Filter`.

### How it works

Pipeline: socket → Tomcat thread → **filters** → `DispatcherServlet` → handler → back
out **through the same filters in reverse**. Filter code is a sandwich:

1. Before `chain.doFilter(...)` = on the way **in** → note the start time.
2. `chain.doFilter(request, response)` hands the request onward — the entire rest of the
   pipeline happens inside this one call.
3. After it = on the way **out** → `response.getStatus()` is the real final status;
   now-minus-start is the real duration.

Four choices, each with a reason:

- **`OncePerRequestFilter`** (Spring helper to extend): guarantees exactly one run per
  request even when the container dispatches internally.
- **`try/finally`**: if anything explodes past us, the line still gets written — crashed
  requests are the ones you most want logged.
- **`System.nanoTime()`**: a stopwatch clock that only moves forward; the wall clock
  (`currentTimeMillis`) can jump mid-request and lie.
- **`@Component`**: Boot auto-registers any `Filter` bean into the chain.

### The filter (`RequestLogFilter.java`)

```java
@Component
class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);   // everything else happens inside this call
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs);
        }
    }
}
```

Imports: `java.io.IOException`, SLF4J `Logger`/`LoggerFactory`,
`org.springframework.stereotype.Component`,
`org.springframework.web.filter.OncePerRequestFilter`, and from `jakarta.servlet`:
`FilterChain`, `ServletException`, `http.HttpServletRequest`, `http.HttpServletResponse`.

### Real output (seven calls, every flavour)

```
GET /ping -> 200 (19 ms)
POST /events -> 201 (91 ms)
POST /events -> 400 (23 ms)
POST /events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/seats/A12 -> 409 (2 ms)
POST /events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/publish -> 422 (1 ms)
GET /definitely-not-a-path -> 404 (4 ms)
GET /events -> 200 (4 ms)
```

Three observations:

- **The 404 line exists** although no controller owns that path — the filter wraps the
  whole pipeline, not just successful routing.
- **Statuses are the final truth**: 400/409/422 are what the client received; by the
  time the response passes back out, the advice has already translated exceptions.
- **Durations tell a true story**: first requests 19/91 ms (warm-up: classes loading,
  converters initialising), then 1–4 ms. Duration columns are how slow endpoints get
  caught.

### Where it is used

Every serious deployment has an access log (nginx and Tomcat ship one built in). The
filter mechanism also carries much of the web's plumbing: authentication, request-id
tagging, CORS headers, response compression — all filters, all below Spring MVC.

### Common mistakes

- **Logging before `chain.doFilter` only** — no status or duration exists yet.
- **No `try/finally`** — crashed requests silently vanish from the record.
- **Heavy work in a filter** — it taxes every request.
- **Reading the request body in a filter** — the body is a one-shot stream; consume it
  and `@RequestBody` downstream gets nothing (wrapper classes exist; know the trap).
- **Measuring with the wall clock** — durations come from `nanoTime`.

### Do it yourself

Start from the F6.8 `scratch-app` as Lesson 12 left it.

1. Create `src/main/java/com/zenith/events/RequestLogFilter.java` with the code above.

2. Rebuild and start:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   ```

3. Second terminal — traffic of every flavour:

   ```bash
   ID="0f14d0ab-9605-4a62-a9e4-5ed26688389b"
   curl -s http://localhost:8080/ping > /dev/null
   curl -s -X POST http://localhost:8080/events -H "Content-Type: application/json" \
     -d '{"name":"Jazz Gala","venueId":"7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01","salesOpenAt":"2027-01-01T10:00:00Z","startsAt":"2027-02-01T19:30:00Z","capacity":300}' > /dev/null
   curl -s -X POST http://localhost:8080/events -H "Content-Type: application/json" \
     -d '{"name":"","venueId":null,"salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}' > /dev/null
   curl -s -X POST http://localhost:8080/events/$ID/seats/A12 > /dev/null
   curl -s http://localhost:8080/definitely-not-a-path > /dev/null
   ```

4. Find the five `RequestLogFilter` lines in the server window: 200, 201, 400, 409, 404
   — each with a millisecond figure.

5. Note the duration pattern: first request slowest (warm-up), then a few ms.

6. `Ctrl+C`. The filter stays — it is a deliverable.

**Done when:** every call produced exactly one line with all four facts, the 404 line is
present although no controller ran, and you can explain why the log statement sits in a
`finally` block after `chain.doFilter`.

### Practice questions

**Question 1.** With the filter above plus the usual advice, two requests arrive:
(i) `GET /no-such-path`, which no controller claims and the advice answers with 404;
(ii) `POST /events` with a blank name, which `@Valid` rejects and the advice answers
with 400. For each: does a log line appear, what status does it show, and did any
controller method run?

*Answer:* (i) A line appears — the filter wraps everything below it, and routing only
happens deeper inside `chain.doFilter`. It shows **404**, the final status after the
advice translated the unknown-path exception. No controller method ran; nothing owns
that path. (ii) A line appears, showing **400** — by the time the response passes back
out through the filter it is the finished product of the validation handler. The
controller method did **not** run: binding and validation happen before the method body,
and `@Valid` failed the request before entry. Both lines prove the filter logs the
finished truth no matter where inside the pipeline the request's fate was decided.

**Question 2.** A colleague rewrites the filter: the log statement comes *before*
`chain.doFilter`, and there is no `try/finally`. (a) For a normal request that ends as
201, which two of the four logged facts are wrong, and what appears instead? (b) For a
request whose processing throws past the filter, what happens to the log line — and why
is that the worst request to get wrong?

*Answer:* (a) **Status and duration.** On the way in, the response is still untouched,
so `getStatus()` reports the default **200** (not the real 201), and the duration is
**0 ms** because nothing has run between the two clock readings. Method and path are
fine — those are known on the way in. (b) As written, a line still appears — but it
*lies*: it claims 200 for a request that actually crashed, so the access log hides every
failure. And the naive fix — moving the log after `chain.doFilter` but still without
`try/finally` — loses the line entirely for crashing requests, because the exception
skips straight past the log statement. Crashed requests are exactly the ones an
investigation needs, which is why the log lives in a `finally` block after the chain
call: it then runs always, success or explosion.

### Summary for revision

- Access log = one line per request, **no exceptions** — including requests no
  controller ever saw. That is why it is a `Filter` (below Spring MVC), not controller
  code.
- Filter sandwich: before `chain.doFilter` = way in; the call = everything else; after =
  way out (final status, real duration).
- Four choices: `OncePerRequestFilter` (one run per request), `try/finally` (crashes
  still logged), `nanoTime` (stopwatch, not wall clock), `@Component` (Boot
  auto-registers).
- The filter logs the finished truth: statuses produced by the advice, durations
  including warm-up.
- Trap: never read the request body in a filter — one-shot stream.

---

## Lesson 14 — `Filter` versus `HandlerInterceptor` (subtask S9)

### Definition

A **`HandlerInterceptor`** is Spring MVC's own checkpoint: like a filter it runs code
around requests, but it lives *inside* the `DispatcherServlet`, after the router has
chosen which controller method will handle the request.

### Why it is needed

The two tools look interchangeable, which is why they get misused. An access log on an
interceptor silently misses every request that dies below Spring MVC (failed
authentication). Handler-aware logic on a filter is impossible — at a filter's depth,
routing has not happened yet.

### How it works

```
socket → Tomcat thread → FILTERS → DispatcherServlet:
                                      router picks the handler
                                      → interceptor preHandle
                                      → controller method
                                      → interceptor postHandle
                                      → interceptor afterCompletion
         ← back out through the same FILTERS, in reverse
```

A filter wraps the *whole box*; an interceptor wraps the *handler inside the box*.

Three interceptor callbacks:

- **`preHandle(request, response, handler)`** — before the controller; the third
  parameter is **the chosen handler object** (the prize no filter can have); returning
  `false` stops the request.
- **`postHandle(...)`** — after the controller returns **normally**; skipped if the
  handler throws.
- **`afterCompletion(..., ex)`** — always runs at the end; `ex` carries the exception —
  but if an advice resolved it, `ex` arrives as `null`.

Wiring differences: `Filter` beans are auto-registered by Boot; interceptors must be
added by hand (`@Configuration` implementing `WebMvcConfigurer`, override
`addInterceptors`). Filters form a chain ordered by `@Order`; the access-log filter
carries `@Order(1)` so it is outermost and sees even requests another filter kills.

**Choosing rule:** see *every* request / raw bytes / before MVC → **`Filter`**. Know
*which handler* / per-controller-method behaviour → **`HandlerInterceptor`**.

### The probes (temporary code)

```java
// TEMPORARY probe: logs the three interceptor callbacks so we can watch them fire.
@Component
class ProbeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ProbeInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        log.info("preHandle: {} {} -> handler is {}",
                request.getMethod(), request.getRequestURI(), handler);
        return true;   // true = let the request continue
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        log.info("postHandle: {} {} (handler finished normally)",
                request.getMethod(), request.getRequestURI());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        log.info("afterCompletion: {} {} ex={}",
                request.getMethod(), request.getRequestURI(),
                ex == null ? "none" : ex.getClass().getSimpleName());
    }
}
```

```java
// TEMPORARY: registers the probe — interceptors are not auto-registered.
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final ProbeInterceptor probe;

    WebConfig(ProbeInterceptor probe) { this.probe = probe; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(probe);
    }
}
```

```java
// TEMPORARY stand-in for an authentication filter: kills /blocked before Spring MVC.
@Component
@Order(2)   // runs after the request log filter (order 1)
class BlockFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().equals("/blocked")) {
            response.setStatus(403);
            return;   // chain stops here: Spring MVC never sees this request
        }
        chain.doFilter(request, response);
    }
}
```

### Real output — four probes, four stories

**`GET /ping` (normal success):**

```
[ProbeInterceptor] preHandle: GET /ping -> handler is com.zenith.events.PingController#ping()
[ProbeInterceptor] postHandle: GET /ping (handler finished normally)
[ProbeInterceptor] afterCompletion: GET /ping ex=none
[RequestLogFilter] GET /ping -> 200 (23 ms)
```

All three callbacks; `preHandle` *names the chosen method*; the filter line comes last —
visual proof of the nesting.

**`POST /events` garbage (validation 400):**

```
[ProbeInterceptor] preHandle: POST /events -> handler is com.zenith.events.EventController#create(CreateEventRequest)
[ProbeInterceptor] afterCompletion: POST /events ex=none
[RequestLogFilter] POST /events -> 400 (107 ms)
```

`postHandle` missing (handler did not finish normally); `ex=none` although an exception
flew — the advice resolved it, so MVC passes no exception to `afterCompletion`. Once an
advice exists, even `afterCompletion` cannot reliably report failures; the filter's
status line is the dependable record.

**`GET /blocked` (killed by a filter — the failed-authentication scenario):**

```
[RequestLogFilter] GET /blocked -> 403 (0 ms)
```

One line, zero interceptor output: Spring MVC never knew the request happened. The
access log still recorded it (outermost, `@Order(1)`).

**`GET /definitely-not-a-path` (unknown path):**

```
[ProbeInterceptor] preHandle: GET /definitely-not-a-path -> handler is ResourceHttpRequestHandler [classpath [META-INF/resources/], ...]
[ProbeInterceptor] afterCompletion: GET /definitely-not-a-path ex=none
[RequestLogFilter] GET /definitely-not-a-path -> 404 (6 ms)
```

Honest surprise: in modern Boot, unknown paths are still routed — to the framework's
static-file handler — so the interceptor runs, but its "prize" is a framework object,
not a controller method. No `postHandle` (handler threw), `ex=none` (advice resolved),
filter logs the final 404.

### Where it is used

Real apps use both. Interceptors: enforcing security annotations found on the controller
method, per-handler timing, tenant resolution. Filters: access logs, authentication
tokens, CORS, compression, request ids. Spring Security is built as a chain of filters —
security must run before anything else exists.

### Common mistakes

- **Access log as an interceptor** — misses filter-killed requests (probe 3).
- **Cleanup in `postHandle`** — skipped on exceptions; use `afterCompletion` or a
  filter's `finally`.
- **Trusting `afterCompletion`'s `ex`** — `null` once an advice resolved the failure.
- **Forgetting manual registration** — an unregistered interceptor is silent, no error.
- **Assuming unknown paths skip interceptors** — they are routed to a framework handler.

### Do it yourself

1. In `RequestLogFilter.java` add import `org.springframework.core.annotation.Order` and
   `@Order(1)` under `@Component` (permanent — the access log belongs outermost).

2. Create the three temporary files above in `src/main/java/com/zenith/events/`.
   Imports for the interceptor: SLF4J, `Component`,
   `org.springframework.web.servlet.HandlerInterceptor`,
   `org.springframework.web.servlet.ModelAndView`, `jakarta.servlet.http.*`. For the
   config: `Configuration`,
   `org.springframework.web.servlet.config.annotation.InterceptorRegistry` and
   `WebMvcConfigurer`. For the block filter: same as `RequestLogFilter` plus `@Order`.

3. `mvn clean package` and start the jar.

4. Four probes (expect `200`, `400`, `403`, `404`):

   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ping
   curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"name":"","venueId":null,"salesOpenAt":"2020-01-01T00:00:00Z","startsAt":"2019-06-15T19:30:00Z","capacity":-50}'
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/blocked
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/definitely-not-a-path
   ```

5. Check the four stories in the server log (as in "Real output" above).

6. Stop the server; **delete the three temporary files**; rebuild. Keep `@Order(1)`.

**Done when:** `/blocked` produced a filter line but zero interceptor lines, `preHandle`
printed a controller method's name, and the choosing rule comes from memory.

### Practice questions

**Question 1.** Choose the right checkpoint, one sentence of justification each:
(a) tag every request with a request id and include it in the access log — even for
requests that authentication rejects; (b) enforce a custom `@RequiresAdmin` annotation
that developers place on individual controller methods.

*Answer:* (a) **`Filter`**, ordered before the authentication filter — requests rejected
by authentication die below Spring MVC, so no interceptor ever runs for them; only an
outer filter sees every request without exception. (b) **`HandlerInterceptor`** — the
job is defined by *which controller method was chosen*: `preHandle` receives the handler
object, can read the `@RequiresAdmin` annotation off the method, and return `false`
(setting 403) to veto. A filter cannot do this, because routing has not happened at its
depth — it can never know the handler.

**Question 2.** In the real output, the garbage `POST /events` produced: `preHandle`
fired, `postHandle` never appeared, `afterCompletion` reported `ex=none` — while the
filter logged 400. Explain all three interceptor facts.

*Answer:* `preHandle` ran because routing had already chosen
`EventController#create(...)` before any validation — binding and validation happen at
method-invocation time, *after* `preHandle`. `postHandle` was skipped because it only
runs when the handler finishes normally, and the validation exception counts as an
abnormal end. `afterCompletion` said `ex=none` because the advice resolved
`MethodArgumentNotValidException` into the 400 response; once a resolver succeeds,
Spring MVC treats the failure as handled and passes `null` — the exception was
"consumed". The filter still logged 400 because the response flowing back out through it
was the finished product of the validation handler.

### Summary for revision

- Filter wraps the whole pipeline (servlet layer); interceptor wraps the chosen handler
  (inside Spring MVC).
- Interceptor callbacks: `preHandle` (knows the handler, can veto), `postHandle` (normal
  finish only), `afterCompletion` (always; `ex` is `null` once an advice resolved it).
- Filters auto-register; interceptors need `WebMvcConfigurer.addInterceptors`. Filter
  order via `@Order`; access log outermost at `@Order(1)`.
- Proofs: filter-killed `/blocked` = filter line only; `preHandle` printed
  `PingController#ping()`; unknown paths still route (to `ResourceHttpRequestHandler`).
- Choosing rule: see everything / raw request / pre-MVC → `Filter`; know the handler →
  `HandlerInterceptor`.

---

## Lesson 15 — Break it: the handler that throws (subtask S10)

### Definition

A deliberate crash **inside an `@ExceptionHandler` method** — tearing the safety net
while someone is falling, to watch what the client receives instead of the designed
error shape. Task words: *throw an exception from inside your `@ExceptionHandler`,
watch the response become something you did not design — handlers must not fail.*

### Why it is needed

Every error path ends in a handler; handler code is the **last designed line of
defense** — nothing of ours is behind it. Studying its failure mode once, on purpose,
in a scratch app is what turns "keep handlers boring" from a slogan into a reason.

### How it works — the mechanics when the domain handler is broken

1. The controller throws `EventNotPublishableException` (as designed).
2. The resolver that runs `@ExceptionHandler` methods calls `onDomainException`.
3. The handler crashes with its own `NullPointerException`.
4. The resolver catches the handler's crash, logs a **WARN** ("Failure in
   @ExceptionHandler …") and **gives up on the whole advice** — no other handler is
   tried; the catch-all never gets a chance.
5. The **original** exception travels to the remaining built-in resolvers; none knows
   `EventNotPublishableException`.
6. Unresolved, it escapes the `DispatcherServlet` into Tomcat: the container logs an
   **ERROR**, sets status **500** itself, and forwards to `/error` — Boot's default
   machinery writes the default body (the timestamp-status-error-path shape again).

Reassurance in step 4: Spring does not feed the handler's own exception back into the
advice — no infinite loop, but also no second chance.

### The sabotage (three lines)

```java
@ExceptionHandler(DomainException.class)
ResponseEntity<ApiError> onDomainException(DomainException ex) {
    // SABOTAGE (Lesson 15): a bug inside the safety net itself.
    String label = null;
    int ignored = label.length();   // NullPointerException, every time
    // ... rest unchanged, now unreachable
```

### Real output

Before (healthy):

```json
{"errorId":"907ae062-45b2-44d8-9a00-34eb6db18b11","status":422,"code":"event_not_publishable","message":"Event cannot be published: it has no price tier","fieldErrors":[]}
```

After the sabotage, the same `POST /events/{id}/publish` → status 500 with:

```json
{"timestamp":"2026-11-01T12:24:59.941+00:00","status":500,"error":"Internal Server Error","path":"/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/publish"}
```

The **default Spring Boot error body** — the shape replaced in Lessons 5–6 — is back: no
`errorId`, no `code`, no `fieldErrors`. The server log, in order:

```
WARN  .m.m.a.ExceptionHandlerExceptionResolver : Failure in @ExceptionHandler com.zenith.events.GlobalExceptionHandler#onDomainException(...)
ERROR o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] ... threw exception
DEBUG o.s.web.servlet.DispatcherServlet        : "ERROR" dispatch for POST "/error"
DEBUG o.s.web.servlet.DispatcherServlet        : Exiting from "ERROR" dispatch, status 500
```

Both stack traces appear: the handler's own
`NullPointerException: Cannot invoke "String.length()" because "label" is null` and the
original `EventNotPublishableException`.

Two savoured details:

- **The catch-all did not save us.** Had the resolver handed the NPE to
  `onUnexpected(Exception)`, the client would have received our 500 `ApiError` with an
  `errorId`. It received the default body instead — once any handler throws, the
  resolver abandons the whole advice for that request.
- **Even the access log lied.** The filter logged
  `POST /events/.../publish -> 200 (17 ms)` while the client got 500: the filter's
  `finally` ran while the exception was still flying past it — nothing written yet, so
  `getStatus()` showed the untouched default 200; Tomcat set the real 500 later, outside
  our filter. One line only — `OncePerRequestFilter` did not re-log the `/error`
  dispatch.

Restored (sabotage removed): 422 with the designed shape, zero WARN/ERROR, access log
truthful again (`-> 422 (50 ms)`).

### Where it is used

Classic real-world costume: an error handler doing *risky work* — fetching translated
messages from a database or i18n service, calling metrics, serializing complex objects.
The day that dependency fails, every error response degrades into an undesigned default
500 — at the worst possible moment. Code-review rule: **an exception handler is pure
translation** — exception in, response out, no I/O, no lookups, nothing that can
realistically throw.

### Common mistakes

- **Risky work inside handlers** — blast radius: every error of that type loses its
  designed shape, fleet-wide, during the dependency's worst hour.
- **Trusting the catch-all to rescue broken handlers** — proven false above.
- **Assuming the access log is always right** — false once an exception escapes the
  whole chain; the status is set after the filter already logged.
- **Fancy error bodies built on the fly** — serialization surprises can make the
  handler itself the thrower.

### Do it yourself

1. Confirm health: build, start, then
   `curl -s -w "\nstatus %{http_code}\n" -X POST http://localhost:8080/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/publish`
   → 422 with the `ApiError` shape.
2. Stop. Add the three sabotage lines at the top of `onDomainException`.
3. Rebuild, restart, repeat the request → **500** with the default Boot body.
4. Find all four artifacts in the server window: the WARN (`Failure in
   @ExceptionHandler`), Tomcat's ERROR (`Servlet.service()`), both stack traces, and the
   access line claiming `-> 200`.
5. Stop, **delete the sabotage lines**, rebuild, restart, repeat → 422, no WARN/ERROR,
   access line `-> 422`.

**Done when:** you saw the default body replace your designed shape, found the WARN that
marks the resolver giving up, can explain why the catch-all did not rescue the request,
and the app is restored to a clean 422.

### Practice questions

**Question 1.** In the sabotage run, the client received the default Spring Boot error
body with status 500. Explain why it received *neither* (a) the 422 `ApiError` that
`onDomainException` normally produces, *nor* (b) the 500 `ApiError` with an `errorId`
from the catch-all. Name what the resolver does after a handler throws, and where the
500 status actually came from.

*Answer:* (a) The 422 never existed: the handler crashed on its own
`NullPointerException` before reaching the code that builds the `ApiError`. (b) The
catch-all never ran because, after a handler throws, the resolver that runs
`@ExceptionHandler` methods logs `Failure in @ExceptionHandler` and **gives up on the
entire advice for that request** — it offers neither the new exception nor the original
one to the other handlers. The original `EventNotPublishableException` then passes to
the remaining built-in resolvers, none of which recognises it, so it escapes the
`DispatcherServlet` into Tomcat. The container logged the ERROR, **set the 500 status
itself**, and forwarded to `/error`, where Boot's default machinery wrote the default
body. The 500 came from Tomcat's error handling — not from any code of ours.

**Question 2.** Your team's error handler queries a `translations` database table on
every `DomainException` to build the message in the user's language. Explain the risk in
one sentence, describe what clients would see during a database outage, and propose the
safer design.

*Answer:* Risk: the handler does I/O that can fail, so the safety net tears exactly when
it is needed most. During a database outage, every `DomainException` makes the handler
itself throw; the resolver gives up on the advice, and clients receive the undesigned
default 500 body — business refusals that should be 422/409 appear as "Internal Server
Error" with no `code` and no `errorId`, support cannot correlate anything, and 5xx
monitoring floods with false alarms (the access log may even record wrong statuses).
Safer design: handlers translate only from values already in memory — the exception's
own fields. If localized text is required, attach it at throw time or read it from an
in-memory map or a resource file bundled with the app; any enrichment that can fail
stays outside the handler.

### Summary for revision

- Handler throws → resolver logs WARN and abandons the whole advice (no catch-all
  rescue, no loop, no second chance) → original exception escapes to Tomcat → container
  sets 500 and `/error` writes the **default body**. Your shape contract silently
  breaks.
- Even observability degrades: the access-log filter logged 200 for a request that
  ended as 500 — the status was set after the filter's `finally` ran.
- Therefore: an exception handler is pure translation — no I/O, no lookups, no business
  logic, nothing that can realistically throw.
- Our real handlers obey: each builds an `ApiError` from values it already holds.

---

## Lesson 16 — RFC 9457 Problem Details, and the story retold (subtasks S11, CP)

### Definition

**RFC 9457 "Problem Details for HTTP APIs"** is the internet's standard shape for HTTP
error bodies: a small JSON object with five well-known fields and its own registered
content type, **`application/problem+json`**. (It is the 2023 refresh of RFC 7807 —
same design, sharpened wording.) Spring supports it natively through the
`ProblemDetail` class.

### Why it is needed

`ApiError` works, but it is a *dialect*: every team that invents its own error shape
forces every client to learn a new one. A standard shape means client libraries, API
gateways, and monitoring tools recognise the media type on sight. Knowing the standard
also shows which of our design decisions were universal — the standard has a slot for
every one of them.

### Each field explained

```json
{
  "type": "https://api.zenith.example/problems/event_not_publishable",
  "title": "Business rule violation",
  "status": 422,
  "detail": "Event cannot be published: it has no price tier",
  "instance": "/events/0f14d0ab-.../publish"
}
```

- **`type`** — a URI naming the **kind** of problem. The machine contract: clients
  branch on it; it must never change casually — our `code`'s job, spelled as a URI that
  can point at documentation. Default when unset: `"about:blank"`.
- **`title`** — short human summary of the **kind**; identical for every occurrence of
  the same `type`. Describes the category, never the specific case.
- **`status`** — the HTTP status repeated inside the body (logs and queues lose
  headers; the body keeps the number).
- **`detail`** — human explanation of **this one occurrence** — our `message`.
  Specific, changeable, never for machine branching.
- **`instance`** — a URI identifying **this occurrence**, typically the request path.
- **Extension members** — any extra JSON fields; explicitly allowed. Our `errorId` and
  `fieldErrors` live on here.

Memory hook: `type`/`title` = the kind; `detail`/`instance` = the occurrence;
`status` repeats the number; everything else is an extension.

Spring mechanics: `ProblemDetail.forStatusAndDetail(status, text)` creates the object;
`setType(...)`, `setTitle(...)`, `setProperty("name", value)` fill it. Return a
`ProblemDetail` from an `@ExceptionHandler` and Spring sets the
`application/problem+json` content type **and** fills `instance` with the request path
by itself. Spring also ships `ResponseEntityExceptionHandler`, a ready-made advice base
class: extend it and the whole framework-exception family we mapped by hand in the
400-versus-422 audit (unreadable body, type mismatch, wrong media type, validation
failure, unknown path) comes back as Problem Details automatically.

### The example (temporary swap of the domain handler)

```java
// TEMPORARY (Lesson 16): the same handler, speaking RFC 9457 Problem Details.
@ExceptionHandler(DomainException.class)
ProblemDetail onDomainException(DomainException ex) {
    HttpStatus status = (ex instanceof SeatAlreadyReservedException)
            ? HttpStatus.CONFLICT
            : HttpStatus.UNPROCESSABLE_ENTITY;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    problem.setType(URI.create("https://api.zenith.example/problems/" + ex.code()));
    problem.setTitle(status == HttpStatus.CONFLICT
            ? "Conflict with current state"
            : "Business rule violation");
    problem.setProperty("errorId", UUID.randomUUID().toString());
    problem.setProperty("code", ex.code());
    return problem;
}
```

New imports: `java.net.URI`, `org.springframework.http.ProblemDetail`.

### Real output (headers included)

Business-rule refusal (`POST /events/{id}/publish`):

```
HTTP/1.1 422
Content-Type: application/problem+json

{"type":"https://api.zenith.example/problems/event_not_publishable","title":"Business rule violation","status":422,"detail":"Event cannot be published: it has no price tier","instance":"/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/publish","errorId":"75135a3c-6cbf-4675-ad22-95a792a2d8c3","code":"event_not_publishable"}
```

State clash (`POST /events/{id}/seats/A12`):

```
HTTP/1.1 409
Content-Type: application/problem+json

{"type":"https://api.zenith.example/problems/seat_already_reserved","title":"Conflict with current state","status":409,"detail":"Seat A12 is already reserved.","instance":"/events/0f14d0ab-9605-4a62-a9e4-5ed26688389b/seats/A12","errorId":"ff9eabdc-1fa5-4057-a733-7982990e2388","code":"seat_already_reserved"}
```

Two things we never wrote appeared by themselves: the `application/problem+json` header
and the `instance` field with each request's own path. After capturing these, the
original `ApiError` handler was restored and re-verified (422, our shape).

Our hand-built shape maps onto the standard almost field for field:

| Our `ApiError` | Problem Details home |
| --- | --- |
| `code` | `type` (as a URI) |
| `message` | `detail` |
| `status` | `status` |
| `errorId` | extension member |
| `fieldErrors` | extension member |

We re-invented the standard without knowing it — the reasoning (stable machine
contract, separate human text, correlation id) was the universal part.

### Where it is used

Public APIs increasingly return Problem Details; gateways and SDKs special-case the
media type; new Spring apps start with `ProblemDetail` + `ResponseEntityExceptionHandler`
— the standard shape and the framework family handled in one move. This task built
`ApiError` by hand on purpose: now you know exactly what the convenience classes
automate.

### Common mistakes

- **Treating `type` casually** — it is the machine contract; renaming breaks clients,
  exactly like renaming our `code` values.
- **Occurrence details in `title`** — "Seat A12 is already reserved" belongs in
  `detail`; `title` stays per-kind.
- **Thinking the standard shape makes content safe** — `detail` reaches clients like
  any field; the no-leak rule applies unchanged.
- **Adopting the shape, skipping the discipline** — statuses, the logging catch-all
  with a correlation id, and the audit habit are still your job.

### Do it yourself

1. In `GlobalExceptionHandler.java`, add imports `java.net.URI` and
   `org.springframework.http.ProblemDetail`; replace `onDomainException` with the
   version above.

2. Rebuild, start, and call both domain errors **with `-i`**:

   ```bash
   mvn clean package
   java -jar target/validation-scratch-0.0.1-SNAPSHOT.jar
   # second terminal:
   ID="0f14d0ab-9605-4a62-a9e4-5ed26688389b"
   curl -si -X POST http://localhost:8080/events/$ID/publish
   curl -si -X POST http://localhost:8080/events/$ID/seats/A12
   ```

3. Check: 422 then 409; `Content-Type: application/problem+json`; five standard fields;
   `instance` filled although you never set it; `errorId` and `code` as extensions.

4. Restore the original `onDomainException` (the `ResponseEntity<ApiError>` version),
   remove the two imports, rebuild, confirm publish → 422 with the `ApiError` shape.

**Done when:** you saw the standard media type and all five fields on a real response,
can say which pair describes the kind (`type`, `title`) and which the occurrence
(`detail`, `instance`), and the app is back to its `ApiError` deliverable.

### Checkpoint — the five questions, with full answers

**Question 1. Where does a validation failure go after it is thrown, and which class
catches it?**

*Answer:* When `@Valid` fails on the `@RequestBody`, Spring throws
`MethodArgumentNotValidException` *before* the controller method body runs (binding
first, then validation, then — only on success — the method). The exception leaves the
handler-invocation step and enters exception resolution, where the resolver that
consults `@ExceptionHandler` methods finds the match in our `@RestControllerAdvice`
class `GlobalExceptionHandler`: the `onValidationFailure(MethodArgumentNotValidException)`
handler. It reads the offending fields from `ex.getBindingResult().getFieldErrors()` and
returns the 400 `ApiError` with the per-field list.

**Question 2. Why must a stack trace never reach the client? Name two things it
reveals.**

*Answer:* A stack trace is written for the people who own the code; to anyone else it is
a map of our insides, and to an attacker it is reconnaissance. It reveals, among other
things: (1) internal class, package, and framework names with versions (`com.zenith…`,
Spring, Tomcat) — attackers match versions against known vulnerabilities; (2) file names
and line numbers, plus the exception message, which can carry live data such as SQL
fragments, file paths, or hostnames. Our catch-all therefore logs the full trace at
ERROR tagged with an `errorId`, and the client receives only that identifier.

**Question 3. Give one ticketing example each of a 400 and a 422.**

*Answer:* **400** — `POST /events` with the body `{"name":}` (unparseable JSON), or a
blank name and negative capacity: the request is defective *as a document*; a reader
with no business knowledge could reject it. **422** — `POST /events/{id}/publish` for an
event with no price tier: perfectly formed and fully understood, but a business rule
refuses it; retrying the identical request can never succeed.

**Question 4. Why did the sales-date rule need a class-level constraint instead of a
field one?**

*Answer:* The rule "sales must open before the event starts" compares **two** fields,
`salesOpenAt` and `startsAt`. A field-level annotation sees only its own field's value,
in isolation — it has no access to sibling fields. A class-level constraint is placed on
the record itself, so its validator receives the **whole** `CreateEventRequest` object
and can read both dates and compare them. Hence `@SalesBeforeStart` on the record, with
`SalesBeforeStartValidator implements ConstraintValidator<SalesBeforeStart,
CreateEventRequest>` (plus the null guard, since absent values are `@NotNull`'s job, and
`addPropertyNode("salesOpenAt")` to pin the report to a field).

**Question 5. What can a `Filter` see that a `HandlerInterceptor` cannot?**

*Answer:* Any request that ends **below Spring MVC** — above all, a request killed by an
earlier filter, such as one rejected by authentication. Our demo proved it: `/blocked`,
ended by a filter, produced an access-log line and **zero** interceptor output, because
Spring MVC never knew the request happened. The filter wraps the whole pipeline; the
interceptor only wraps the handler dispatch inside the `DispatcherServlet`. (The
interceptor's own prize is the reverse: it knows *which controller method* was chosen —
`preHandle` printed `PingController#ping()` — which no filter can know, because at the
filter's depth routing has not happened yet.)

### Summary for revision

- Problem Details = standard error body: `type`/`title` for the kind, `detail`/
  `instance` for the occurrence, `status` repeated, extensions for the rest; media type
  `application/problem+json`.
- Spring: return `ProblemDetail` from a handler → media type and `instance` filled
  automatically; `ResponseEntityExceptionHandler` automates the framework-exception
  family in this shape.
- `ApiError` ↔ Problem Details: `code`→`type`, `message`→`detail`, `errorId` and
  `fieldErrors`→extensions. The reasoning was universal; only the spelling was ours.
- The standard shape does not replace the discipline: right statuses, no leaks,
  correlation id, audits.

---

## Task complete — F6.8 done

**Done-when check, verified against the running app:**

- ✓ Every error from the API has one JSON shape — `ApiError` from every handler,
  audited end to end: validation 400, seat clash 409, publish 422, unknown path 404,
  broken JSON 400, bad UUID 400, wrong media type 415, bad query parameter 400,
  crash 500.
- ✓ Validation failures list the offending fields — `fieldErrors` with field name and
  message, all failures in one pass, cross-field rule pinned to `salesOpenAt`.
- ✓ No stack trace reaches a client — deliberate `NullPointerException` answered with
  `errorId` only; six-marker scan of the response body found nothing, while the same
  markers were present in the server log.
- ✓ Every request produces one log line with its duration — `RequestLogFilter`,
  outermost at `@Order(1)`, `finally` after `chain.doFilter`, `nanoTime` durations.

**Final state of `scratch-app`** (package `com.zenith.events`): `EventsApplication`,
`PingController`, `EventController`, `EventResponse`, `CreateEventRequest` (five field
rules + `@SalesBeforeStart`), `SalesBeforeStart` + `SalesBeforeStartValidator`,
`DomainException` + `SeatAlreadyReservedException` + `EventNotPublishableException`,
`ApiError`, `GlobalExceptionHandler` (validation, domain, three document-defect
mappings, 404, catch-all), `RequestLogFilter`.

All sixteen lessons, their doubts, answers, and do-it-yourself steps are in this file —
enough to rebuild everything on another machine without the chat.

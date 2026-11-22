# F6.3 — Bean scopes, duplicate beans, and beans you do not own

Notes for Task F6.3. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state:** Java 21 (`java -version` → `openjdk version "21...`) and
Maven 3.9.x — one copy sits unpacked at
`F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/tools/apache-maven-3.9.9`. No database
is needed for this task. The scratch project lives in this task's own folder at
`scratch-app/` (package `com.zenith.scopes`); Lesson 1 describes every file in it.

## The task's subtasks

| Id | Subtask |
|----|---------|
| S1 | Prove the default scope. Put a counter field on a `@Service`. Call an endpoint three times. The number climbs. |
| S2 | Say the consequence out loud: a singleton with a mutable field is shared mutable state across every concurrent request. Services hold dependencies, not data. |
| S3 | Add `@Scope("prototype")` to the same bean. See the counter stay at 1. Note the surprise: a prototype injected into a singleton is created once and shared anyway. |
| S4 | Break it on purpose. `PricingRule` interface, two `@Component` implementations. Inject `PricingRule`. Read the two-candidate failure. |
| S5 | Fix it twice. First `@Primary` on `TieredPricingRule`. Then `@Qualifier("flatPricingRule")` at the injection point. Learn which wins when both are present. |
| S6 | Inject `List<PricingRule>` and get all of them. Rule chains without `if` statements. |
| S7 | Write a `@Configuration` class with `@Bean` methods for classes you did not write: `ObjectMapper`, `RestClient`, `Clock`. |
| S8 | Understand why the injected `Clock` makes hold-expiry logic testable. |
| S9 | Ask AI: when to prefer a `@Bean` method over `@Component` on the class, with two examples. |
| CP | Checkpoint: four questions answered out loud. |

## Roadmap for this task — 9 small lessons

| # | Lesson | What it gives you |
|---|--------|-------------------|
| 1 | What "scope" means, and the default: singleton *(S1)* | The word scope, and live proof that one shared object serves every request. |
| 2 | The consequence: shared mutable state *(S2)* | Why a mutable field on that one shared object breaks under simultaneous requests. The stateless rule. |
| 3 | Prototype scope, and the injection surprise *(S3)* | The opt-out scope, why it is rarely wanted, and the prototype-into-singleton trap. |
| 4 | Break it: two beans match one slot *(S4)* | The real `NoUniqueBeanDefinitionException`, caused on purpose and decoded. |
| 5 | Fix one: `@Primary` *(S5 part)* | How to declare a default when two beans match. |
| 6 | Fix two: `@Qualifier`, and which wins *(S5 complete)* | How to pick one bean by name at the injection point; the rule when both marks are present. |
| 7 | Inject all of them: `List<PricingRule>` *(S6)* | The third option: take every matching bean at once. Rule chains without `if`. |
| 8 | Beans you do not own: `@Configuration` and `@Bean` *(S7, S9)* | Beans from classes you cannot annotate: `ObjectMapper`, `RestClient`, `Clock`. |
| 9 | The `Clock` habit, and the checkpoint *(S8, CP)* | Why injected time makes hold expiry testable; the four checkpoint answers. |

---

## Lesson 1 — What "scope" means, and the default: singleton (subtask S1)

### Definition

A **bean** is an object that Spring builds and keeps in its box, the application context.
An **instance** is one single object built from a class. A **scope** is the rule attached
to a bean that answers two questions: how many instances of this bean exist, and how long
does each one live? The default scope is **singleton**: Spring builds **one** instance,
and the whole application shares it for as long as the application runs.

### Why it is needed

Spring builds your objects now; you stopped writing `new` for them. That leaves a question
open: if ten classes ask for `CallCounterService`, does Spring build ten objects or one?
The scope is that decision. If you do not know the default, you will guess. Most beginners
guess "each web request gets a fresh object" — and that guess is wrong. On a wrong guess
you might store one customer's data in a field, believing the object is private to that
customer. It is shared with every customer. In a ticketing system, that is how one
person's seat hold leaks into another person's order.

### How it works

1. At startup, Spring scans the packages. For every marked class it writes one recipe
   card ("I know how to build this").
2. For a singleton bean, Spring cooks each recipe card **exactly once**. One card, one
   object. The object goes into the box.
3. Every place that needs that bean gets the **same one object** — every constructor
   parameter, every `getBean` call. Spring never cooks a second one.
4. The object lives until the application shuts down.

One recipe card, one object, many hands pointing at it.

### The whole app

A fresh scratch app for this task: `scratch-app/`, package `com.zenith.scopes`. One
difference from the F6.2 app: the pom uses `spring-boot-starter-web` instead of plain
`spring-boot-starter`. The web starter brings a built-in web server, so the app does not
run `main` and exit — it stays up and answers web requests. The proof needs that: an
**endpoint** (a URL path the app answers, here `/count`) gets called three times.

`scratch-app/pom.xml`:

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
    <artifactId>scopes-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Web this time: the scope proof needs an endpoint we can call three times. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- JUnit for the Clock lesson later. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

The main class — note it only starts the app; the web server keeps it alive:

```java
package com.zenith.scopes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ScopesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScopesApplication.class, args);
    }
}
```

The service with the counter. **Mutable** means: can be changed after the object is
created. `callCount` is a mutable field — `record()` changes it on every call:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class CallCounterService {
    private int callCount = 0;   // a mutable field on a bean — the subject of this whole task

    public int record() {
        callCount = callCount + 1;
        return callCount;
    }
}
```

The controller. `@RestController` marks a class whose methods answer web requests, and
makes it a bean. `@GetMapping("/count")` says: GET requests to `/count` run this method,
and the return value becomes the response body:

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterController {
    private final CallCounterService counter;

    public CounterController(CallCounterService counter) {
        this.counter = counter;
    }

    @GetMapping("/count")
    public String count() {
        return "call number " + counter.record();
    }
}
```

No scope annotation anywhere — both beans get the default.

Real run (`mvn spring-boot:run`), then three real calls with `curl` (a command-line tool
that sends one HTTP request and prints the response):

```text
Started ScopesApplication in 0.927 seconds (process running for 1.102)
```

```text
$ curl http://localhost:8080/count
call number 1
$ curl http://localhost:8080/count
call number 2
$ curl http://localhost:8080/count
call number 3
```

### Walkthrough

1. **Startup.** The scan finds two marked classes. Two recipe cards. Spring cooks each
   once: one `CallCounterService`, one `CounterController`. The controller's constructor
   asks for a `CallCounterService`, so Spring hands it the one it just built. Two objects
   sit in the box. No more are ever made.
2. **Call 1.** A GET request arrives at `/count`. It runs `count()` on the one controller
   object, which calls `record()` on the one service object. `callCount` goes 0 → 1.
3. **Call 2.** A completely separate request — same objects. The field still remembers 1.
   It goes 1 → 2.
4. **Call 3.** 2 → 3.

The reasoning: if each request got a fresh object, every request would meet a brand-new
`callCount = 0` and every response would say `call number 1`. The number climbed instead.
A climbing number means the field survived between requests; a field lives inside an
object; so all three requests hit the **same object**. One instance, shared. The climb
also shows the object belongs to the application, not to any user — the next caller,
from any machine, gets `call number 4`.

### Where it is used

Every `@Service`, `@Repository`, `@Component`, and `@RestController` written in F6.1 and
F6.2 was a singleton — none said so; they got the default. In the real ticketing system,
one `SeatHoldService` instance serves hundreds of simultaneous requests during an
on-sale. Singleton is the right default: build once, share everywhere, no waste.

### Common mistakes

- **Believing each request gets a fresh service object.** It does not. One object serves
  every request, forever. This is the most expensive wrong guess in this phase.
- **Believing each user gets their own object.** Also no. Alice and Bob share the same
  instance; any field on it is shared between them.
- **Confusing Spring's singleton scope with the hand-made "singleton pattern".** The
  hand-made pattern blocks `new` with a private constructor and hands out one static
  instance. A Spring singleton is a normal class — you could `new` it ten times. Spring
  simply chooses to build one and share it. The class stays ordinary and testable.

### Link to the task

**S1 exactly:** counter field on a `@Service`, endpoint called three times, number
climbing because it is the same object every time. One instance, shared.

### Do it yourself (second laptop)

**Setup:** Java 21 and Maven 3.9.x installed, same as for F6.1 and F6.2. No database,
no Docker.

1. Make the folders:
   `F6.3-bean-scopes-duplicate-beans-and-beans-you-do-not-own/scratch-app/src/main/java/com/zenith/scopes/`.
2. Create `pom.xml` and the three Java files exactly as above.
3. From `scratch-app/`: `mvn spring-boot:run`.
4. Wait for `Started ScopesApplication in ... seconds`. The command does not finish —
   the web server is running. Leave it.
5. In a **second** terminal: `curl http://localhost:8080/count` — three times. (Or open
   `http://localhost:8080/count` in a browser and refresh three times.)
6. Expect: `call number 1`, then `call number 2`, then `call number 3`.
7. Stop the app with Ctrl+C in the first terminal.

**If it fails:** a startup error naming the port — `Web server failed to start. Port 8080
was already in use.` — means another program owns 8080. Close that program, or create
`scratch-app/src/main/resources/application.properties` with the single line
`server.port=8081` and call `http://localhost:8081/count` instead.

**Done when:** you see 1, 2, 3, and you can say in one sentence why the number climbs —
same object every time.

### Summary for revision

- Scope = how many instances of a bean exist, and how long each lives.
- Default = **singleton**: one recipe card, cooked once, one object in the box, shared by
  every injection point, alive until shutdown.
- Proof: a counter field climbs across separate requests. Fresh objects would always
  answer 1.
- The object belongs to the application, not to a request and not to a user.
- Spring singleton ≠ hand-made singleton pattern: the class stays normal; Spring just
  builds one.

### Check questions, with answers

**Question 1.** Here is a complete service and controller from a Spring Boot app. There
are no scope annotations anywhere in the app:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class GreetCounterService {
    private int greetCount = 0;

    public int recordGreeting() {
        greetCount = greetCount + 1;
        return greetCount;
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetController {
    private final GreetCounterService counter;

    public GreetController(GreetCounterService counter) {
        this.counter = counter;
    }

    @GetMapping("/greet")
    public String greet() {
        return "greeting number " + counter.recordGreeting();
    }
}
```

The app starts fresh. Alice calls `GET /greet` twice from her laptop. Then Bob calls
`GET /greet` once from his phone. What exact text does Bob receive, and why?

**Answer.** Bob receives `greeting number 3`. `GreetCounterService` has no scope
annotation, so it is a singleton: one shared object for the whole application. Alice's
two calls moved the one `greetCount` field 0 → 1 → 2. Bob's call runs on the **same
object** and moves the same field 2 → 3. Who calls, and from which device, changes
nothing — the object belongs to the application. Checked for real (three calls on the
running app's beans):

```text
Alice call 1 -> greeting number 1
Alice call 2 -> greeting number 2
Bob   call 1 -> greeting number 3
```

**Question 2.** Here is one service and two controllers from a Spring Boot app. No scope
annotations anywhere:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class PriceService {
    public int basePriceFor(String seatId) {
        return 50;
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {
    private final PriceService prices;

    public EventController(PriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/events/price")
    public String eventPrice() {
        return "event seat price: " + prices.basePriceFor("A1");
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BasketController {
    private final PriceService prices;

    public BasketController(PriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/basket/price")
    public String basketPrice() {
        return "basket total: " + prices.basePriceFor("A1");
    }
}
```

When this app starts: (a) how many `PriceService` objects does Spring build — one, two,
or three? (b) `EventController` has a field named `prices`, and `BasketController` also
has a field named `prices`. Do those two fields point at the same object, or at two
different objects?

**Answer.** (a) **One.** The scan writes one recipe card for `PriceService`, and a
singleton recipe card is cooked exactly once, no matter how many classes ask for the
bean. (b) **The same object.** Cooking `EventController`, Spring hands in the one
`PriceService` from the box; cooking `BasketController`, it hands in that same one
again. Two fields, one object behind both. Checked for real — a small probe asked the
box and compared the references:

```text
PriceService beans in the box: 1
EventController sees:  com.zenith.scopes.PriceService@5b3f3ba0
BasketController sees: com.zenith.scopes.PriceService@5b3f3ba0
same object? true
```

The identical `@5b3f3ba0` name and `same object? true` (a `==` comparison) are the
proof: one instance, two hands pointing at it.

---

## Lesson 2 — The consequence: shared mutable state (subtask S2)

### Definition

A **thread** is one worker running code. A program can start several workers; then
several pieces of code run at the same moment. **Concurrent** means happening at the
same time. **Shared mutable state** is stored data ("state") that can be changed
("mutable") and that more than one worker can reach ("shared"). A **lost update** is a
write that another worker's write erases. **Stateless** means: the object keeps no data
between calls in its fields — inputs arrive as parameters, results leave as return
values. **Thread-safe** means: correct even when several workers run the methods at the
same time.

The lesson's one idea: the singleton default turns every mutable field on a service into
shared mutable state, and shared mutable state gives wrong answers under concurrent use.

### Why it is needed

The web server is not one worker. It keeps a pool of workers, and each incoming request
runs on one of them. During an on-sale, hundreds of requests run concurrently — hundreds
of workers running methods **on the same service object**, because the singleton default
means there is only one. A mutable field on that object is one whiteboard that hundreds
of hands write on at once. In quiet development nothing goes wrong. Under load, the
whiteboard lies — and in ticketing, the "whiteboard" will be somebody's seat hold.

### How it works

The innocent line `callCount = callCount + 1;` is secretly three steps: **read** the
current value, **add** one, **write** the result back. Two workers interleaving:

| Step | Worker A | Worker B | `callCount` is |
|------|----------|----------|----------------|
| 1 | reads 7 | | 7 |
| 2 | | reads 7 | 7 |
| 3 | adds → has 8 | | 7 |
| 4 | **writes 8** | | 8 |
| 5 | | adds → has 8 | 8 |
| 6 | | **writes 8** | 8 |

Two calls happened; the counter moved by one. Worker B never saw A's write, so B's write
erased it — a lost update. No crash, no error, no log line. The silence is what makes
this bug expensive.

### The demo, run for real

`CallCounterService` gains a read-only method. The full file:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class CallCounterService {
    private int callCount = 0;   // a mutable field on a bean — the subject of this whole task

    public int record() {
        callCount = callCount + 1;
        return callCount;
    }

    public int current() {
        return callCount;
    }
}
```

A new file `SharedCounterDemo.java` — a plain class with a `main`, not a bean. It starts
the box, takes the one shared service out, and sets two workers on it at the same time:

```java
package com.zenith.scopes;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Two workers hammer the one shared CallCounterService at the same time.
 * If the counter were safe, the final number would be exactly 2000000.
 */
public class SharedCounterDemo {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(
                ScopesApplication.class, "--spring.main.web-application-type=none");
        try {
            CallCounterService counter = context.getBean(CallCounterService.class);

            Runnable oneMillionCalls = () -> {
                for (int i = 0; i < 1000000; i++) {
                    counter.record();
                }
            };

            Thread workerA = new Thread(oneMillionCalls);
            Thread workerB = new Thread(oneMillionCalls);
            workerA.start();   // both workers now run at the same time...
            workerB.start();
            workerA.join();    // ...and main waits here until both are done
            workerB.join();

            System.out.println("calls made:    2000000");
            System.out.println("counter shows: " + counter.current());
            System.out.println("lost updates:  " + (2000000 - counter.current()));
        } finally {
            context.close();
        }
    }
}
```

Line glosses: `"--spring.main.web-application-type=none"` starts the box but skips the
web server, so the program ends when `main` ends. A `Runnable` is a piece of work with
no input and no result. `new Thread(work)` makes a worker; `start()` tells it to begin
and does **not** wait; `join()` makes `main` pause until that worker finishes.

Two real runs:

```text
calls made:    2000000
counter shows: 1192980
lost updates:  807020
```

```text
calls made:    2000000
counter shows: 1262534
lost updates:  737466
```

### Walkthrough

1. The box starts; one `CallCounterService` is cooked — the singleton.
2. `getBean` hands `main` that one object; both workers share the reference.
3. Each worker runs read-add-write a million times. The workers constantly interleave
   like the 7 → 8 table, so writes keep erasing other writes.
4. Final count ≈ 1.2 million instead of 2 million: roughly 800,000 calls left no trace.
   The number differs every run, because interleaving depends on timing.

The three sequential curls of the singleton proof (`call number 1, 2, 3`) never showed
this, because sequential calls cannot interleave. That is exactly why the bug hides in
development and explodes on sale day.

### Where it is used

The professional rule: **services hold dependencies, not data.** A stateless service has
fields only for dependencies — other beans, `final`, set once in the constructor:

```java
@Service
public class SeatHoldService {
    private final HoldRepository holds;   // a dependency: final, never changes — safe to share

    public SeatHoldService(HoldRepository holds) {
        this.holds = holds;
    }
}
```

A stateless object is thread-safe for free — there is nothing on it to corrupt. That is
why the singleton default is harmless in practice: shared is only dangerous when it is
also mutable.

### Common mistakes

- **Per-customer data in service fields** (`currentUser`, `selectedSeat`, a basket
  list). Every concurrent customer shares those fields. Request data belongs in
  parameters, local variables, and the database.
- **Trusting a passing test.** Tests and hand-made curl calls run one at a time;
  sequential calls cannot interleave, so the bug stays invisible until real load.
- **Believing `final` fixes it.** `final` locks which object the field points at; it
  does not freeze the object's insides. A `final List` still lets every worker `add` to
  the one list — still shared mutable state.
- **Reaching for locking tools first** (`synchronized`, `AtomicInteger` — the
  concurrency track teaches them properly). The first question is not "how do I guard
  this field?" but "why does a service have this field at all?" Removing state beats
  guarding it.

### Link to the task

**S2.** Complete when the consequence can be said out loud: *a singleton bean with a
mutable field is shared mutable state across every concurrent request; in a ticketing
system that is how seat holds leak between customers — services hold dependencies, not
data.* The demo is the task's promised sight: a counter on a singleton giving wrong
results under concurrent calls.

### Do it yourself (second laptop)

**Setup:** the F6.3 scratch app from Lesson 1 runs.

1. Add the `current()` method to `CallCounterService.java` (full file above).
2. Create `SharedCounterDemo.java` exactly as above.
3. From `scratch-app/`:
   `mvn spring-boot:run -Dspring-boot.run.main-class=com.zenith.scopes.SharedCounterDemo`
   (the switch says: run this main method instead of the usual one).
4. Expect three result lines: `calls made: 2000000`, a `counter shows:` number clearly
   **below** 2000000, and a large `lost updates:` number. The program exits by itself.
   A real run on this machine gave `counter shows: 1008047`, `lost updates: 991953`.
5. Run it again — expect a different wrong number.

**If it fails:** `Unknown lifecycle phase` or similar means the `-D` switch was
mistyped — it must be one unbroken piece. If the result is exactly `2000000`, the two
workers happened not to overlap; run again.

**Done when:** two different wrong totals seen, and you can recite the three hidden
steps inside `callCount = callCount + 1` and why two workers lose updates.

### Summary for revision

- Thread = one worker. The web server runs each request on a worker from a pool, so a
  singleton's methods run concurrently.
- `x = x + 1` is read-add-write; interleaved workers erase each other's writes (lost
  updates). No error is raised — the number is just wrong.
- Sequential calls cannot show the bug; concurrency reveals it. Demo: 2,000,000 calls,
  ~1.2 million counted.
- The rule: **services hold dependencies, not data.** Stateless objects are thread-safe
  for free.
- `final` on a collection field does not help: it locks the pointer, not the contents.

### Check questions, with answers

**Question 1.** Here is a service from a Spring Boot ticketing app. No scope annotations
anywhere, so the default applies:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class SeatHoldFormService {
    private String currentSeatId;   // the seat the current customer wants

    public String hold(String seatId) {
        this.currentSeatId = seatId;
        // ... imagine a short pause here: a price check, a fraud check ...
        return "holding seat " + currentSeatId;
    }
}
```

At the same moment, Alice calls `hold("A1")` and Bob calls `hold("B9")`, each from their
own request. Describe one possible wrong outcome for Alice, and list the step-by-step
order of events that produces it.

**Answer.** Alice can be told `holding seat B9` — another customer's seat. The service
is a singleton, so both workers run `hold` **on the same object**, and `currentSeatId`
is one shared field. One order that breaks: (1) Alice's worker writes `currentSeatId =
"A1"`. (2) Bob's worker writes `currentSeatId = "B9"` — same field, value overwritten.
(3) Alice's worker reaches the return line and **reads the field**: it now says `B9`.
Alice's reply names Bob's seat; her hold has leaked into his. The pause between write
and read makes the window wide, but even without it the window exists. The fix is the
stateless rule — the seat id must stay a parameter, never a field:

```java
public String hold(String seatId) {
    return "holding seat " + seatId;   // parameter: local to each call, nothing shared
}
```

**Question 2.** Here are two classes from a Spring Boot app. No scope annotations
anywhere:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class PriceService {
    public int basePriceFor(String seatId) {
        return 50;
    }
}
```

```java
package com.zenith.scopes;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private final PriceService prices;                             // field (a)
    private int searchesServed = 0;                                // field (b)
    private final List<String> recentQueries = new ArrayList<>();  // field (c)

    public SearchService(PriceService prices) {
        this.prices = prices;
    }

    public String search(String query) {
        searchesServed = searchesServed + 1;
        recentQueries.add(query);
        return "results for " + query + " (from " + prices.basePriceFor("A1") + ")";
    }
}
```

`SearchService` is a singleton serving many concurrent requests. For each field (a),
(b), (c): safe or a bug, and why? One is dangerous **despite** being `final` — explain
that one carefully.

**Answer.** **(a) Safe.** A dependency: `final`, set once during construction, never
changed afterwards. Many workers reading one unchanging reference cannot corrupt
anything. This is exactly what service fields are for. **(b) A bug.** A mutable `int` on
a shared object. `searchesServed = searchesServed + 1` is read-add-write; concurrent
workers interleave and erase each other's writes — lost updates, silently wrong counts.
**(c) A bug, despite `final`.** `final` only locks which list the field points at; it
does not freeze the list's contents. Every worker calls `add` on the **same**
`ArrayList`, and `ArrayList` is not built for concurrent writers: entries can vanish,
the structure can corrupt, calls can even throw. Fields (b) and (c) are *data*, and the
rule is: services hold dependencies, not data — counts and histories belong in storage,
not in service fields.

---

## Lesson 3 — Prototype scope, and the injection surprise (subtask S3)

### Definition

**Prototype** is the other scope. `@Scope("prototype")` on a bean changes the rule to:
build a **new instance every time somebody asks** the box for this bean. An **ask**
happens in exactly two places: at startup, when Spring fills a constructor slot that
needs this bean; and whenever code calls `getBean` for it. A web request is **not** an
ask. A method call is **not** an ask. Also: the box does not keep prototypes — it cooks
one, hands it over, and forgets it.

### Why it is needed

Three reasons. Seeing the opposite sharpens what singleton means. Self-defense: many
people add `@Scope("prototype")` believing it means "a fresh object per web request" —
it does not, and the surprise bites them. And honesty: prototype is rarely used;
knowing it is mostly so it cannot fool you.

### How it works

For a singleton, Spring cooks the recipe card **once at startup** and stores the dish;
every ask afterwards gets the stored dish. For a prototype, Spring stores only the
card; every ask cooks a **fresh dish**, which the box immediately forgets. So observed
behaviour depends entirely on **when asks happen**. Key fact: a controller is a
singleton, built once at startup — its constructor slot is filled **once**. A prototype
injected there is one ask: one fresh object, kept in the controller's field forever.

### Experiment 1 — add the annotation, change nothing else (run for real)

The service with the mark:

```java
package com.zenith.scopes;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
public class CallCounterService {
    private int callCount = 0;

    public int record() {
        callCount = callCount + 1;
        return callCount;
    }

    public int current() {
        return callCount;
    }
}
```

The controller keeps normal constructor injection. Only the response also prints the
service object itself, to show *which* object answered. Printing an object shows its
default name, like `com.zenith.scopes.CallCounterService@455889fc`; the part after `@`
is unique to that one living object — same name, same object:

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterController {
    private final CallCounterService counter;

    public CounterController(CallCounterService counter) {
        this.counter = counter;
    }

    @GetMapping("/count")
    public String count() {
        return "call number " + counter.record() + " served by " + counter;
    }
}
```

Real run, three real calls:

```text
$ curl http://localhost:8080/count
call number 1 served by com.zenith.scopes.CallCounterService@455889fc
$ curl http://localhost:8080/count
call number 2 served by com.zenith.scopes.CallCounterService@455889fc
$ curl http://localhost:8080/count
call number 3 served by com.zenith.scopes.CallCounterService@455889fc
```

**Still climbing, still the same object.** The annotation changed nothing observable —
the surprise the task warns about.

### Experiment 2 — make every request truly ask (run for real)

Same prototype service. The controller now receives the box itself (the box is a bean
too) and asks it on every request:

```java
package com.zenith.scopes;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterController {
    private final ApplicationContext box;

    public CounterController(ApplicationContext box) {
        this.box = box;
    }

    @GetMapping("/count")
    public String count() {
        CallCounterService counter = box.getBean(CallCounterService.class);   // a fresh ask, on every request
        return "call number " + counter.record() + " served by " + counter;
    }
}
```

Real run, three real calls:

```text
$ curl http://localhost:8080/count
call number 1 served by com.zenith.scopes.CallCounterService@6d4aa68a
$ curl http://localhost:8080/count
call number 1 served by com.zenith.scopes.CallCounterService@2831394
$ curl http://localhost:8080/count
call number 1 served by com.zenith.scopes.CallCounterService@362c8e9b
```

Counter stays at 1; three different names — a brand-new object answered each call.

### Walkthrough

**Experiment 1.** At startup the controller (a singleton) is cooked once; filling its
constructor slot is **one ask**, so one prototype service is cooked and stored in the
controller's `final` field. No ask ever happens again. Every request reuses that object:
1, 2, 3, and `@455889fc` never changes. *A prototype injected into a singleton is
created once and then shared anyway* — it behaves like a singleton with a misleading
label.

**Experiment 2.** Each request runs `box.getBean(...)` — a genuine ask — so each
request gets a fresh object whose `callCount` starts at 0. `record()` always returns 1;
the names differ. After the response, nothing holds the object and the box never kept
it: three requests, three short-lived objects, each forgotten.

One sentence holds both: **scope controls what an ask returns, and injection into a
singleton asks exactly once.**

### Where it is used

Rarely. Well-built backends are stateless singletons almost everywhere, and a stateless
bean has nothing that needs to be fresh. The legitimate niche: a genuinely stateful,
short-lived helper you still want Spring to assemble. True "one object per web request"
needs is served by a separate request scope in web apps — it exists; this project will
not need it. Per-request data belongs in parameters and local variables.

### Common mistakes

- **Believing prototype means "per web request".** It means per ask; requests are not
  asks.
- **Injecting a prototype into a singleton and expecting freshness.** One ask at
  startup, one object, shared forever after.
- **Using prototype to "fix" a shared mutable field.** Injected into a singleton it
  changes nothing; and even where it works it hides the real problem. Remove the state
  instead.
- **Expecting Spring to manage a prototype's life.** The box forgets it at birth;
  whoever asked owns it.

### Link to the task

**S3 complete, both halves:** the annotation added to the same bean and repeated — the
counter kept climbing (the surprise); the counter stayed at 1 only once each request
truly asked via `getBean`. Noted for good: *a prototype injected into a singleton is
created once and then shared anyway.* Prototype is not what this project wants — the
app is reverted to singleton afterwards.

### Do it yourself (second laptop)

**Setup:** the F6.3 scratch app with `current()` and `SharedCounterDemo` in place.

1. In `CallCounterService.java`: add `@Scope("prototype")` under `@Service`, plus the
   import `org.springframework.context.annotation.Scope`.
2. In `CounterController.java`: change the return line to
   `return "call number " + counter.record() + " served by " + counter;`
3. `mvn spring-boot:run`; in a second terminal `curl http://localhost:8080/count`
   three times. Expect climbing numbers and the **same** `@name` three times.
4. Replace `CounterController.java` with the Experiment 2 version (receives
   `ApplicationContext box`, calls `box.getBean` inside `count()`). Restart, curl three
   times. Expect `call number 1` three times with three **different** `@names`.
5. Revert: remove `@Scope("prototype")` and its import; restore the controller to
   constructor injection with the plain return `"call number " + counter.record()`.
6. Restart, curl twice — climbing again: the singleton is back.

**Done when:** climbing-same-name seen, ones-different-names seen, revert done, and the
rule said out loud: scope answers asks, and injection into a singleton asks once.

### Summary for revision

- Prototype = a new object **per ask**. An ask = a constructor slot filled at startup,
  or a `getBean` call. Requests and method calls are not asks.
- Prototype injected into a singleton: created once at startup, stored, shared — the
  classic trap.
- The counter stays at 1 only when every request asks the box itself (`getBean` per
  request), and then each fresh object is forgotten immediately.
- The box stores singletons; it never stores prototypes.
- You will almost never want prototype; stateless singletons remain the rule.

### Check questions, with answers

**Question 1.** A Spring Boot app contains exactly these two classes plus the main
class:

```java
package com.zenith.scopes;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
public class NotepadService {
    private final List<String> lines = new ArrayList<>();

    public int addLine(String line) {
        lines.add(line);
        return lines.size();
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoteController {
    private final NotepadService notepad;

    public NoteController(NotepadService notepad) {
        this.notepad = notepad;
    }

    @GetMapping("/note")
    public String note() {
        return "lines on this notepad: " + notepad.addLine("hello");
    }
}
```

The app starts, and one user calls `GET /note` three times. (a) How many
`NotepadService` objects are created in total, and at what moment? (b) What exactly
does the **third** response say — and why, despite `@Scope("prototype")`?

**Answer.** (a) **Exactly one**, created at startup — the moment Spring fills
`NoteController`'s constructor slot. That slot-filling is the only ask in the whole
story; the three web requests are not asks. (b) The third response says
`lines on this notepad: 3`. The controller keeps the one prototype instance in its
`final` field, so all three requests call `addLine` on the same object and its list
grows 1 → 2 → 3. The prototype mark never gets another chance to act. Checked for
real:

```text
lines on this notepad: 1
lines on this notepad: 2
lines on this notepad: 3
```

**Question 2.** A Spring Boot app contains this bean (an empty class is enough here):

```java
package com.zenith.scopes;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
public class StampService {
}
```

After the app has started, a `main` method runs these lines (`context` is the started
application context; `x == y` on objects prints `true` only when both sides are the
very same object):

```java
StampService a = context.getBean(StampService.class);
StampService b = context.getBean(StampService.class);
StampService c = a;
System.out.println(a == b);
System.out.println(a == c);
```

How many `StampService` objects exist after these lines run? What do the two prints
show? And what would both prints show if the `@Scope("prototype")` line were deleted?

**Answer.** **Two objects.** The two `getBean` calls are two asks, and each ask cooks a
fresh prototype. `c = a` is not an ask — it is just a second name for `a`'s object. So
`a == b` prints **`false`** (two different objects) and `a == c` prints **`true`**
(same object, two names). Checked for real:

```text
a is com.zenith.scopes.StampService@4a03bb68
b is com.zenith.scopes.StampService@4e868ef5
a == b ? false
a == c ? true
```

Without the annotation the bean is a singleton: both asks return the one stored object,
so both prints show `true`. Also checked for real:

```text
a is com.zenith.scopes.StampService@4bdcaf36
b is com.zenith.scopes.StampService@4bdcaf36
a == b ? true
a == c ? true
```

---

## Lesson 4 — Break it: two beans match one slot (subtask S4)

### Definition

The failure caused on purpose in this lesson: a constructor slot asks for an interface,
and the box holds **two** beans that implement it. Spring refuses to guess and stops the
whole application. The exception behind the refusal is
**`NoUniqueBeanDefinitionException`** — "no *unique* recipe card matched; more than one
was found."

### Why it is needed

A constructor slot can name an interface, and Spring fills it with a bean whose class
implements it — that works when exactly **one** matching bean exists. Real systems break
that condition on purpose: two ways to price tickets are two classes implementing one
`PricingRule` interface, and that is good design. The moment two implementations exist,
every slot that asks for "a `PricingRule`" has two possible answers. This failure is a
normal day in a growing codebase. It is also a *good* failure: loud, at startup,
before any customer sees a wrong price.

### How it works

Bean names first: every bean's name is its class name with the first letter made small —
`FlatPricingRule` → `flatPricingRule`, `TieredPricingRule` → `tieredPricingRule`.

1. Startup. The scan writes recipe cards for all marked classes — including **both**
   rule classes. Holding two implementations is fine.
2. Spring cooks `QuoteService`; its constructor wants **a** `PricingRule`.
3. Spring searches the box for beans of that type and finds two.
4. Spring never guesses — not the first, not alphabetical, not random. It aborts the
   whole startup and prints both candidates plus advice.

The two beans by themselves are innocent. Proven: with both rule classes present and
**nobody injecting a single** `PricingRule`, the app started cleanly
(`Started ScopesApplication in 0.394 seconds`). The error lives at the slot that wants
exactly one, not at the classes.

### The break, built for real

Four new files in the scratch app:

```java
package com.zenith.scopes;

public interface PricingRule {
    int priceFor(int tickets);
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class FlatPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        return tickets * 50;   // every ticket costs 50
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class TieredPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        if (tickets <= 2) {
            return tickets * 50;            // first two tickets: full price, 50 each
        }
        return 100 + (tickets - 2) * 40;    // from the third ticket on: 40 each
    }
}
```

(For 3 tickets: flat says 150, tiered says 140 — these numbers later reveal which rule
answered.)

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private final PricingRule pricing;

    public QuoteService(PricingRule pricing) {
        this.pricing = pricing;
    }

    public String quote(int tickets) {
        return tickets + " ticket(s) cost " + pricing.priceFor(tickets);
    }
}
```

Real run of `mvn spring-boot:run`, real failure:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.scopes.QuoteService required a single bean, but 2 were found:
	- flatPricingRule: defined in file [.../target/classes/com/zenith/scopes/FlatPricingRule.class]
	- tieredPricingRule: defined in file [.../target/classes/com/zenith/scopes/TieredPricingRule.class]

Action:

Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
```

### Decoding the failure

| Line | Plain English |
|------|---------------|
| `APPLICATION FAILED TO START` | Nothing runs. The whole box was abandoned — even `/count` is down. |
| `Parameter 0 of constructor in ...QuoteService` | The exact spot: the first constructor parameter (counting from 0). Spring points at the slot. |
| `required a single bean, but 2 were found` | The slot wants one; the box offers two. The whole problem in one clause. |
| the two `- name: defined in file [...]` lines | All candidates, by bean name, with their files. Nothing hidden. |
| `Action: ...@Primary... multiple beans... @Qualifier...` | Spring lists all three fixes — the next three lessons, in order. |

The friendly block appears at injection points. Asking the box directly shows the raw
exception name. A throwaway main ran `context.getBean(PricingRule.class)` while two
candidates existed; real output:

```text
NoUniqueBeanDefinitionException: No qualifying bean of type 'com.zenith.scopes.PricingRule' available: expected single matching bean but found 2: flatPricingRule,tieredPricingRule
```

Same refusal, rawer voice: *expected single matching bean but found 2*.

### Where you will meet it

- **Strategy sets** — several pricing rules, notification channels, payment providers.
  Good design produces multiple implementations, so this failure follows good design.
- **A fake leaked into main sources** — a fake marked `@Component` under `src/main/java`
  becomes a real bean; suddenly two candidates match.
- **Growing teams** — someone adds a second implementation on Tuesday; a class from
  January stops the app on Wednesday. The error names both candidates.

### Common mistakes

- **Panic-deleting one implementation.** Both usually exist for a reason. The fix is
  choosing, not deleting.
- **Assuming Spring picks one anyway.** Never. The printed list happens to be
  alphabetical; no order makes Spring guess.
- **Skipping the Description block.** It names the exact constructor and both bean
  names — half the fix is written there.
- **Skipping the Action line.** The three fixes are listed: `@Primary`, accept multiple
  beans, `@Qualifier`.
- **Believing two implementations are themselves illegal.** The box holds both happily;
  only a slot demanding a single one raises the error.

### Link to the task

**S4 complete:** `PricingRule` with two `@Component` implementations, injected into a
service, startup failure caused and read. The app stays broken on purpose — the next two
lessons fix it two ways.

### Do it yourself (second laptop)

**Setup:** the F6.3 scratch app, reverted to singleton after the prototype lesson.

1. Create `PricingRule.java`, `FlatPricingRule.java`, `TieredPricingRule.java` as above.
2. Optional: `mvn spring-boot:run` — it starts cleanly; two implementations alone are
   innocent. Ctrl+C.
3. Create `QuoteService.java` as above.
4. `mvn spring-boot:run` → `APPLICATION FAILED TO START` with the block above.
5. Find three things: the blamed slot, the two candidate bean names, the three advised
   fixes in the Action line.
6. **Leave everything broken** — the next lesson starts here.

**If it starts instead:** check both rule classes carry `@Component` and `QuoteService`
exists with the `PricingRule` constructor parameter.

**Done when:** the failure was caused on purpose, and you can point at the slot line and
the two candidate lines.

### Summary for revision

- Bean name = class name with a small first letter.
- Two implementations in the box: legal. A slot asking for a single one: that is where
  `NoUniqueBeanDefinitionException` strikes.
- Spring never guesses between candidates; it stops the whole app at startup.
- The Description names the slot and all candidates; the Action names the three fixes:
  `@Primary`, multiple beans, `@Qualifier`.
- Raw form (direct `getBean`): *expected single matching bean but found 2*.

### Check questions, with answers

**Question 1.** A Spring Boot app contains exactly these four classes plus the main
class:

```java
package com.zenith.scopes;

public interface Greeting {
    String say();
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class EnglishGreeting implements Greeting {
    @Override
    public String say() {
        return "hello";
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class FrenchGreeting implements Greeting {
    @Override
    public String say() {
        return "bonjour";
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class Welcomer {
    private final Greeting greeting;

    public Welcomer(Greeting greeting) {
        this.greeting = greeting;
    }

    public String welcome() {
        return greeting.say() + "!";
    }
}
```

Does `mvn spring-boot:run` start this app? If not: which class and which constructor
parameter does the Description line blame, and which two bean names appear in the
candidate list?

**Answer.** It does not start. `Welcomer`'s constructor asks for a single `Greeting`,
and the box holds two beans implementing it. The Description blames **`Parameter 0 of
constructor in com.zenith.scopes.Welcomer`**, and the candidates are the class names
with small first letters: **`englishGreeting`** and **`frenchGreeting`**. Checked for
real:

```text
Parameter 0 of constructor in com.zenith.scopes.Welcomer required a single bean, but 2 were found:
	- englishGreeting: defined in file [.../target/classes/com/zenith/scopes/EnglishGreeting.class]
	- frenchGreeting: defined in file [.../target/classes/com/zenith/scopes/FrenchGreeting.class]
```

**Question 2.** A Spring Boot app contains exactly these three classes plus the main
class:

```java
package com.zenith.scopes;

public interface PaymentGateway {
    String charge(int amountCents);
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class StripeGateway implements PaymentGateway {
    @Override
    public String charge(int amountCents) {
        return "charged " + amountCents + " cents via Stripe";
    }
}
```

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final PaymentGateway gateway;

    public PaymentService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String pay(int amountCents) {
        return gateway.charge(amountCents);
    }
}
```

(a) Does this app start? Say why in one sentence. (b) A teammate adds a
`FakePaymentGateway` class implementing `PaymentGateway`, marked `@Component`, under
`src/main/java` — what happens at the next startup, and which bean names are listed?
(c) Where should a fake like this live instead, and with or without `@Component`?

**Answer.** **(a) It starts** — the slot asks for a `PaymentGateway` and exactly one
bean implements it, so there is nothing to guess. Checked for real:
`Started ScopesApplication in 0.401 seconds`. **(b)** The next startup dies with the
two-candidate failure, blaming `Parameter 0 of constructor in
com.zenith.scopes.PaymentService` and listing **`fakePaymentGateway`** and
**`stripeGateway`** (alphabetical). Checked for real:

```text
Parameter 0 of constructor in com.zenith.scopes.PaymentService required a single bean, but 2 were found:
	- fakePaymentGateway: defined in file [.../target/classes/com/zenith/scopes/FakePaymentGateway.class]
	- stripeGateway: defined in file [.../target/classes/com/zenith/scopes/StripeGateway.class]
```

**(c)** Under `src/test/java`, with **no** annotation. Classes in test sources are not
scanned when the application starts, so they never become beans; a plain unannotated
fake is handed to the service by the test itself, through the constructor —
`new PaymentService(new FakePaymentGateway())`. That keeps the fake useful for tests and
invisible to the running app.

---

## Lesson 5 — Fix one: `@Primary` (subtask S5, first half)

### Definition

`@Primary` is one line placed on a bean class. It means: **"when several beans match a
single slot, prefer me."** It turns one candidate into the default, and the
two-candidate failure disappears.

### Why it is needed

The app was dead: `QuoteService` asks for one `PricingRule`, the box holds two, Spring
refuses to guess. Somebody must choose. `@Primary` is the first way, and it fits the
case where **one implementation is the normal one** and the rest are special-purpose.
Here: tiered pricing is the house standard, flat exists for special events — so the
house standard gets the mark.

### How it works

Cooking `QuoteService`, Spring runs the same search as before, plus one step:

1. The slot wants a `PricingRule`; the box holds two candidates.
2. **New step:** before giving up, Spring checks — is exactly **one** candidate marked
   `@Primary`? If yes, it wins; the slot is filled; startup continues.
3. If none is marked: the familiar `required a single bean, but 2 were found` stop.
4. If two are marked: a new refusal — `more than one 'primary' bean found`.

Two details: the mark goes on the **bean class**, not on the slot; and the losing bean
is **not** removed — it stays in the box, fully built, choosable by other means.

### The fix, run for real

`TieredPricingRule` with the mark (note the import):

```java
package com.zenith.scopes;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class TieredPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        if (tickets <= 2) {
            return tickets * 50;            // first two tickets: full price, 50 each
        }
        return 100 + (tickets - 2) * 40;    // from the third ticket on: 40 each
    }
}
```

A new controller, so the winner is visible — it prices 3 tickets, where the two rules
disagree (flat: 150, tiered: 140):

```java
package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {
    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping("/quote")
    public String quote() {
        return quotes.quote(3);
    }
}
```

Real run:

```text
Started ScopesApplication in 0.58 seconds (process running for 0.685)
```

```text
$ curl http://localhost:8080/quote
3 ticket(s) cost 140
$ curl http://localhost:8080/count
call number 1
```

140 = tiered (100 + 40), not flat (150): the primary bean is inside `QuoteService`. The
working `/count` shows the whole app returned — one ambiguity had kept the entire box
down.

### The two-primaries break, run for real

Both rules marked `@Primary`, then startup:

```text
NoUniqueBeanDefinitionException: No qualifying bean of type 'com.zenith.scopes.PricingRule' available: more than one 'primary' bean found among candidates: [flatPricingRule, tieredPricingRule]
```

Plainly: "you declared two different defaults — that is not a choice." Reverted
immediately; only the tiered rule keeps the mark.

### Where it is used

Wherever a team has a house standard plus alternatives: the real payment provider
primary, the sandbox one picked by name; the main database connection primary, the
reporting one named. The mark on a class tells reviewers "this is the normal one".

### Common mistakes

- **Marking two candidates `@Primary`** — `more than one 'primary' bean found`. Exactly
  one default per interface.
- **Believing the other bean is gone.** `@Primary` wins contests; it does not remove
  contestants. The flat rule stays in the box, injectable by name.
- **Putting the mark at the injection point.** `@Primary` marks the bean class; choosing
  at the slot is `@Qualifier`'s job.
- **Using `@Primary` where one specific slot needs one specific implementation.** It is
  a global default; per-slot choice needs `@Qualifier`.

### Link to the task

**S5 first half:** fixed with `@Primary` on `TieredPricingRule`, proof on screen (140,
not 150). Second half (`@Qualifier`, and which wins) is the next lesson.

### Do it yourself (second laptop)

**Setup:** the scratch app in its broken two-candidate state.

1. In `TieredPricingRule.java`: add `@Primary` under `@Component` plus the import
   `org.springframework.context.annotation.Primary`.
2. Create `QuoteController.java` as above.
3. `mvn spring-boot:run` → `Started ScopesApplication`, no failure.
4. `curl http://localhost:8080/quote` → `3 ticket(s) cost 140` (tiered, not flat).
5. `curl http://localhost:8080/count` → the counter works; the whole app is back.
6. Optional break: add `@Primary` to `FlatPricingRule` too, restart, read
   `more than one 'primary' bean found among candidates: [flatPricingRule,
   tieredPricingRule]`, then remove it and confirm health.

**Done when:** `/quote` says 140 and you can say why 140 proves `@Primary` worked.

### Summary for revision

- `@Primary` on a bean class = "prefer me when several beans match a single slot."
- It resolves ambiguity by declaring a default; none marked = the two-candidate stop;
  two marked = `more than one 'primary' bean found`.
- The non-primary bean still exists and can be chosen by name.
- Mark the house standard; keep exceptions for `@Qualifier`.

### Check questions, with answers

**Question 1.** An app has `interface Notifier { String send(String message); }`,
`EmailNotifier` (`@Component` **and** `@Primary`, returns `"email sent: " + message`),
`SmsNotifier` (`@Component` only, returns `"sms sent: " + message`), and this service:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Service;

@Service
public class AlertService {
    private final Notifier notifier;

    public AlertService(Notifier notifier) {
        this.notifier = notifier;
    }

    public String alert() {
        return notifier.send("event on sale");
    }
}
```

(a) Does the app start? (b) What exact text does `alertService.alert()` return? (c) Is
`smsNotifier` still a bean in the box, and what does that mean for another class that
specifically wants the SMS one?

**Answer.** **(a) Yes.** Two candidates match the slot, but exactly one is `@Primary`,
so Spring has its answer and startup continues. **(b)** `email sent: event on sale` —
the primary bean fills the plain slot. **(c) Yes** — `@Primary` does not remove the
loser. Checked for real:

```text
alert() -> email sent: event on sale
Notifier beans in the box: [emailNotifier, smsNotifier]
```

Both notifiers are in the box; a class that wants the SMS one can still get it by
asking for it **by name** at its own injection point — the `@Qualifier` tool of the
next lesson.

**Question 2.** Same shape, different marks: `interface Greeting { String say(); }`,
`EnglishGreeting` (`@Component` + `@Primary`, says `"hello"`), `FrenchGreeting`
(`@Component` + `@Primary`, says `"bonjour"`), and a `Welcomer` service whose
constructor takes a single `Greeting`. Does the app start? What does the refusal say
this time, and how does it differ from the version where neither class had `@Primary`?

**Answer.** It does not start. Checked for real:

```text
NoUniqueBeanDefinitionException: No qualifying bean of type 'com.zenith.scopes.Greeting' available: more than one 'primary' bean found among candidates: [englishGreeting, frenchGreeting]
```

Same exception class as the no-primary version, different reason clause. No primaries:
*expected single matching bean but found 2* — "I have no way to choose." Two primaries:
*more than one 'primary' bean found* — "your instructions name two defaults, which is
contradictory." Either way Spring refuses to guess; exactly one primary is the only
version of this setup that starts.

---

## Lesson 6 — Fix two: `@Qualifier`, and which wins (subtask S5, complete)

### Definition

`@Qualifier("beanName")` is a mark placed **at the injection point** — on the
constructor parameter. It means: **"fill this slot with the bean of this exact name."**
Bean-name reminder: class name with a small first letter, so `FlatPricingRule` is the
bean `flatPricingRule`.

### Why it is needed

`@Primary` answers "what is the default?" — but a default cannot express an exception.
If the whole system uses tiered pricing except one screen that must use flat, that is
one specific slot wanting one specific bean. `@Qualifier` is that tool. It also
completes the task's demand: fix the failure twice and know which fix wins.

### How it works

1. The slot wants a `PricingRule`; candidates are found by type: two.
2. A qualifier is present, so Spring keeps only candidates whose **name** matches the
   string — `"flatPricingRule"` matches one.
3. That one is injected. `@Primary` is **never consulted** — the slot's specific request
   beats the bean's general preference.
4. If the string matches nothing: startup fails — candidates found by type, none
   qualified by name.

Placement: on the parameter, inside the constructor parentheses. Import:
`org.springframework.beans.factory.annotation.Qualifier` (a different package than
`@Primary`).

### The fix, run for real

`@Primary` removed from `TieredPricingRule`; the choice moved to the slot:

```java
package com.zenith.scopes;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private final PricingRule pricing;

    public QuoteService(@Qualifier("flatPricingRule") PricingRule pricing) {
        this.pricing = pricing;
    }

    public String quote(int tickets) {
        return tickets + " ticket(s) cost " + pricing.priceFor(tickets);
    }
}
```

Real run:

```text
Started ScopesApplication in 0.58 seconds
```

```text
$ curl http://localhost:8080/quote
3 ticket(s) cost 150
```

150 = 3 × 50 — the **flat** rule answered (the `@Primary` lesson's run said 140 for the
same endpoint).

### The duel, run for real

Both marks at once: `@Primary` restored on `TieredPricingRule`, qualifier kept at the
slot:

```text
$ curl http://localhost:8080/quote
3 ticket(s) cost 150
```

Still 150. **`@Qualifier` wins. Specific beats default.** `@Primary` is a preference
that applies when nothing more precise is said; a qualifier is a precise request, so the
preference gets no vote at that slot.

### The typo break, run for real

`@Qualifier("flatRule")` — a name that matches nothing:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.scopes.QuoteService required a bean of type 'com.zenith.scopes.PricingRule' that could not be found.

The following candidates were found but could not be injected:
	- User-defined bean
	- User-defined bean
```

And the raw reason deeper in the log, naming the guilty string:

```text
expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {@org.springframework.beans.factory.annotation.Qualifier("flatRule")}
```

The flip to notice: the two-candidate failure said *"but 2 were found"* (too many); this
one says *"could not be found ... candidates were found but could not be injected"* —
both rules exist, but neither is named `flatRule`, so by name the shortlist is empty.
The compiler cannot catch this: quoted text is just data, never checked against bean
names, which exist only at runtime. The mistake sleeps until startup.

### Where it is used

One slot needing the non-default: the sandbox payment provider in one reconciliation
job while the real one stays primary; the reporting database in one analytics service.
In review, a qualifier at a slot signals a deliberate exception. Also possible: name a
bean yourself with `@Component("groupPricing")` and qualify by that name.

### Common mistakes

- **Typo or wrong case in the string** — the real failure above; read the line that
  names the annotation.
- **The class name instead of the bean name** — `@Qualifier("FlatPricingRule")`
  (capital F) matches nothing.
- **Expecting a qualifier to act globally.** It fixes exactly one slot; every other
  slot still follows ambiguity and `@Primary` rules.
- **Qualifiers everywhere.** That means no sensible default was declared. Pattern:
  `@Primary` on the house standard, `@Qualifier` only at exceptions.

### Link to the task

**S5 complete:** fixed twice and both kept. Fix one: `@Primary` on `TieredPricingRule`
(proof: 140). Fix two: `@Qualifier("flatPricingRule")` at the injection point (proof:
150). Both present: **the qualifier wins** (proof: still 150).

### Do it yourself (second laptop)

**Setup:** the scratch app as the `@Primary` lesson left it (`/quote` says 140).

1. Remove `@Primary` and its import from `TieredPricingRule.java`.
2. In `QuoteService.java`: constructor parameter becomes
   `@Qualifier("flatPricingRule") PricingRule pricing`; add the import
   `org.springframework.beans.factory.annotation.Qualifier`.
3. Run; `curl http://localhost:8080/quote` → `3 ticket(s) cost 150`.
4. The duel: restore `@Primary` (and import) on `TieredPricingRule`; restart; curl →
   **still** 150.
5. The break: change the string to `"flatRule"`; restart → `required a bean ... that
   could not be found`, and in the log `expected at least 1 bean which qualifies ...
   Qualifier("flatRule")`. Fix the string back.
6. Leave both marks in place (`/quote` = 150).

**Done when:** 150 seen without and with `@Primary`, the typo failure read, and the
rule recited: specific beats default.

### Summary for revision

- `@Qualifier("beanName")` sits at the injection point and picks by bean name.
- It beats `@Primary`: specific request > general preference.
- A wrong name = startup failure: *candidates were found but could not be injected* /
  *expected at least 1 bean which qualifies*; the compiler cannot check quoted strings.
- Pattern: `@Primary` for the house standard, `@Qualifier` for the exceptions.

### Check questions, with answers

**Question 1.** An app has `interface Notifier { String send(String message); }`;
`EmailNotifier` (`@Component` + `@Primary`, returns `"email sent: " + message`);
`SmsNotifier` (`@Component`, returns `"sms sent: " + message`); and:

```java
package com.zenith.scopes;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReminderService {
    private final Notifier notifier;

    public ReminderService(@Qualifier("smsNotifier") Notifier notifier) {
        this.notifier = notifier;
    }

    public String remind() {
        return notifier.send("show starts in one hour");
    }
}
```

(a) What exact text does `remind()` return, and which mark decided? (b) If the
`@Qualifier` were deleted, what would `remind()` return instead, and why?

**Answer.** **(a)** `sms sent: show starts in one hour`. The qualifier decided: at a
qualified slot, candidates are filtered by name first, and `@Primary` is never
consulted. Checked for real:

```text
remind() -> sms sent: show starts in one hour
```

**(b)** Without the qualifier the slot follows the default rules: two candidates, one
`@Primary` — so the email bean wins and `remind()` returns
`email sent: show starts in one hour`. Deleting one annotation silently flips which
channel a reminder uses — which is why a qualifier at a slot is a deliberate, visible
exception.

**Question 2.** Same four classes, one difference — the qualifier string is
`"smsnotifier"` (small `n`). Does the app start? What does the failure say, and why did
the compiler not catch the mistake?

**Answer.** It does not start. The slot's shortlist-by-name is empty: both notifier
beans exist, but neither is named `smsnotifier` — bean names are case-exact
(`smsNotifier`). The failure is the "could not be found" flavour, and the raw line
names the guilty annotation. Checked for real:

```text
expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {@org.springframework.beans.factory.annotation.Qualifier("smsnotifier")}
```

The compiler stayed silent because `"smsnotifier"` is just text inside quotes. The
compiler verifies types and identifiers, not the contents of strings; bean names only
come into existence at runtime, when the scan builds the box.

---

## Lesson 7 — Inject all of them: `List<PricingRule>` (subtask S6)

### Definition

The third option for a slot whose interface has many implementations: declare the slot
as a **list**. A constructor parameter of type `List<PricingRule>` receives **every**
bean in the box that implements `PricingRule`, in one list. No choosing happens, so no
ambiguity is possible.

### Why it is needed

`@Primary` and `@Qualifier` both answer "which **one**?" Some designs want all: show
the best price across every rule, run every validation, send to every channel. This is
the task's "rule chains and strategy sets without `if` statements", and the shape
returns in the project's first real phase.

### How it works

1. The slot's type is "a list of `PricingRule`".
2. Spring collects **all** beans of that type — more-than-one is welcome here.
3. The list is handed through the constructor.

Consequences: `@Primary` and `@Qualifier` play no part — the primary mark does **not**
shrink the list. The list's order is whatever the scan produced (alphabetical by class
name here); chains that need a fixed order control it with `@Order` (name only, not
needed in this task).

### The rewrite, run for real

`QuoteService` with a list slot (the qualifier disappeared with the rewrite;
`@Primary` stayed on `TieredPricingRule` to prove it gets ignored):

```java
package com.zenith.scopes;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private final List<PricingRule> rules;   // ALL beans implementing PricingRule, in one list

    public QuoteService(List<PricingRule> rules) {
        this.rules = rules;
    }

    public String quote(int tickets) {
        String answer = tickets + " ticket(s):";
        int best = Integer.MAX_VALUE;   // the largest possible int, so any real price is smaller
        for (PricingRule rule : rules) {
            int price = rule.priceFor(tickets);
            answer = answer + " " + rule.getClass().getSimpleName() + " says " + price + ".";
            if (price < best) {
                best = price;
            }
        }
        return answer + " Best price: " + best;
    }
}
```

(`Integer.MAX_VALUE` = the largest `int`, so the first real price beats it;
`getClass().getSimpleName()` = the object's class name as text.)

Real run, two rules in the box:

```text
$ curl http://localhost:8080/quote
3 ticket(s): FlatPricingRule says 150. TieredPricingRule says 140. Best price: 140
```

Both rules arrived — including the non-primary flat one.

### The payoff, run for real

One new file added; nothing else touched:

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class GroupPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        if (tickets >= 3) {
            return tickets * 45;   // groups of three or more: 45 each
        }
        return tickets * 50;       // too small for the group discount: full price
    }
}
```

```text
$ curl http://localhost:8080/quote
3 ticket(s): FlatPricingRule says 150. GroupPricingRule says 135. TieredPricingRule says 140. Best price: 135
```

The new rule simply appeared in the list. Without the list, `QuoteService` would carry
an `if`-chain edited on every new rule; with it, a new strategy is a new class — one
`@Component`, zero edits elsewhere.

### Where it is used

Behaviour in interchangeable pieces: pricing and discount chains, validation steps,
notification fan-out, export formats, fraud checks. Always the same shape: a small
interface, many `@Component` implementations, one service holding
`List<TheInterface>`.

### Common mistakes

- **Expecting `@Primary` to filter the list.** It breaks ties for single slots; a list
  takes everyone (proven above).
- **Relying on list order.** It is scan order; fixed orders need `@Order`.
- **Using a list to dodge choosing.** If the design truly wants one, choose with
  `@Primary`/`@Qualifier`; a loop is a design statement ("all apply").
- **Thinking one list slot fixes ambiguity everywhere.** Each slot is judged alone; a
  remaining single slot still faces the two-candidate rules.

### Link to the task

**S6 complete:** `List<PricingRule>` injected, all of them delivered (primary ignored),
and a new rule joined with zero edits to the consumer — rule chains without `if`.

### Do it yourself (second laptop)

**Setup:** the scratch app in the duel state (`/quote` says 150).

1. Replace `QuoteService.java` with the list version above (qualifier and import go;
   leave `@Primary` on `TieredPricingRule` to watch it be ignored).
2. Run; `curl http://localhost:8080/quote` →
   `3 ticket(s): FlatPricingRule says 150. TieredPricingRule says 140. Best price: 140`.
3. Create `GroupPricingRule.java`. Change nothing else.
4. Restart; curl →
   `3 ticket(s): FlatPricingRule says 150. GroupPricingRule says 135. TieredPricingRule says 140. Best price: 135`.

**Done when:** both outputs seen, and you can explain why the flat rule appears although
the tiered rule is `@Primary`.

### Summary for revision

- A `List<Interface>` slot receives every matching bean; no ambiguity possible.
- `@Primary`/`@Qualifier` do not filter lists; order is scan order unless `@Order` says
  otherwise.
- New behaviour = new `@Component` class; the consuming service never changes.
- Each slot is judged alone: a list slot elsewhere does not rescue an ambiguous single
  slot.

### Check questions, with answers

**Question 1.** An app has `interface Notifier { String send(String message); }`;
`EmailNotifier` (`@Component` + `@Primary`, returns `"email sent: " + message`);
`SmsNotifier` (`@Component`, returns `"sms sent: " + message`); and:

```java
package com.zenith.scopes;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BroadcastService {
    private final List<Notifier> notifiers;

    public BroadcastService(List<Notifier> notifiers) {
        this.notifiers = notifiers;
    }

    public String broadcast(String message) {
        String report = "";
        for (Notifier notifier : notifiers) {
            report = report + notifier.send(message) + " | ";
        }
        return report + notifiers.size() + " channel(s) used";
    }
}
```

(a) How many elements does the injected list hold? (b) What does
`broadcast("gates open")` return? (c) Did `@Primary` on `EmailNotifier` change the
list's contents?

**Answer.** **(a) Two** — a list slot takes every bean of the type. **(b)** Checked for
real:

```text
broadcast() -> email sent: gates open | sms sent: gates open | 2 channel(s) used
```

**(c) No.** `@Primary` only breaks ties when a slot wants a *single* bean. A list slot
has no tie to break; everyone is delivered, marked or not.

**Question 2.** An app has `Greeting` with `say()`, `EnglishGreeting` (`@Component`,
`"hello"`), `FrenchGreeting` (`@Component`, `"bonjour"`), neither primary, plus **both**
services: `WelcomeAll`, whose constructor takes `List<Greeting>` and joins all
greetings; and `Welcomer`, whose constructor takes a single `Greeting`. Does the app
start? Which class is blamed, why does `WelcomeAll` not save it, and what one change
makes everything start?

**Answer.** It does not start. Each slot is judged alone: `WelcomeAll`'s list slot is
satisfiable (both greetings would arrive), but `Welcomer`'s single slot faces two
candidates with no way to choose — and one unsatisfiable slot stops the whole box.
Checked for real:

```text
Parameter 0 of constructor in com.zenith.scopes.Welcomer required a single bean, but 2 were found:
	- englishGreeting: defined in file [.../EnglishGreeting.class]
	- frenchGreeting: defined in file [.../FrenchGreeting.class]
```

Any one of these makes it start: `@Primary` on one greeting, a
`@Qualifier("englishGreeting")` (or the French name) at `Welcomer`'s parameter, or
deleting `Welcomer`. The deletion variant was checked for real — the list service then
runs happily:

```text
welcomeEveryone() -> hello bonjour (2 greetings)
```

---

## Lesson 8 — Beans you do not own: `@Configuration` and `@Bean` (subtasks S7 + S9)

### Definition

- **`@Configuration`** — a mark for a class whose whole job is to define beans.
- **`@Bean`** — a mark for a **method** inside that class. The method is a recipe:
  Spring calls it **once at startup**, and whatever it **returns** goes into the box as
  a bean. The **method's name becomes the bean's name**.

### Why it is needed

The scan only works on classes you can edit — the `@Component` mark goes in the source
file. Three objects every real backend needs come from jars you cannot edit:

- **`ObjectMapper`** — Jackson's JSON converter (every JSON response goes through it,
  met in F4.6).
- **`RestClient`** — Spring's built-in HTTP client, for calling other services.
- **`Clock`** — the JDK's `java.time.Clock`, the "what time is it now?" object.

No way to annotate them → the scan can never find them → they could never be beans —
unless there is a second door into the box. `@Bean` methods are that door: you write
the creation code, Spring runs it and stores the result.

### How it works

1. The scan finds the `@Configuration` class (it must sit in a scanned package).
2. Spring calls each `@Bean` method once.
3. Each return value enters the box, named after its method.
4. From then on, no difference from a scanned bean: fills slots, singleton by default,
   `@Primary` / `@Qualifier` / list injection all apply.

Extra trick: a `@Bean` method can declare **parameters**, and Spring fills them from
the box like constructor slots — so a bean you own can feed a bean you do not own.

### The code

A small class we own (a later task replaces it with real configuration files):

```java
package com.zenith.scopes;

import org.springframework.stereotype.Component;

/** Our own class, so a normal annotation works. */
@Component
public class PaymentProperties {
    public String providerUrl() {
        return "https://payments.example.com";
    }
}
```

The configuration class:

```java
package com.zenith.scopes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PlatformConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);   // pretty-print all JSON responses
        return mapper;
    }

    @Bean
    RestClient paymentsClient(PaymentProperties properties) {
        return RestClient.builder().baseUrl(properties.providerUrl()).build();
    }
}
```

- `clock()` returns a clock reading the computer's time in UTC (the world reference
  time zone).
- `objectMapper()` shows why a method beats a mark: the body **configures** the object
  (`INDENT_OUTPUT` = JSON on multiple indented lines).
- `paymentsClient(PaymentProperties properties)` shows the parameter trick; a builder
  chain could never fit inside an annotation.

A controller using two foreign beans through ordinary slots:

```java
package com.zenith.scopes;

import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class PlatformController {
    private final Clock clock;
    private final RestClient paymentsClient;

    public PlatformController(Clock clock, RestClient paymentsClient) {
        this.clock = clock;
        this.paymentsClient = paymentsClient;
    }

    @GetMapping("/platform")
    public PlatformReport platform() {
        return new PlatformReport(
                clock.instant().toString(),
                paymentsClient.getClass().getSimpleName());
    }

    public record PlatformReport(String timeNow, String paymentsClientType) {
    }
}
```

### Walkthrough, run for real

The endpoint:

```text
$ curl http://localhost:8080/platform
{
  "timeNow" : "2026-10-30T19:35:33.456335Z",
  "paymentsClientType" : "DefaultRestClient"
}
```

One response, three proofs:

- `timeNow` came from the **`Clock` bean** (19:35 UTC = 01:05 IST — UTC is 5½ hours
  behind India).
- `DefaultRestClient` is the implementation class behind the **`RestClient` bean**
  that filled the controller's slot.
- The JSON arrived **indented** — Boot's web machinery is using **our `ObjectMapper`
  bean** for every response. (Boot normally auto-creates a mapper; define your own and
  Boot's steps aside.)

Peeking at the box with a check program:

```text
Clock bean names:        [clock]
RestClient bean names:   [paymentsClient]
ObjectMapper bean names: [objectMapper]
clock bean says now = 2026-10-30T19:36:07.615263Z
same Clock object twice? true
```

Bean names = method names, and the same `Clock` object comes back every time —
`@Bean` beans are singletons by default.

### Where it is used — and S9's answer

Every real backend has such classes: `DataSource` (database connection pool), HTTP
clients per outside service, configured mappers, a `Clock`, security pieces.

**When prefer a `@Bean` method over `@Component` on the class?**

1. **You do not own the class.** `@Component` requires editing the source;
   `ObjectMapper`, `RestClient`, `Clock` live in jars. `@Bean` is the only way.
   (Example: the `RestClient` pointing at the payments provider.)
2. **Building the object takes real code.** A mark cannot express "call the builder,
   set the base URL, switch on this feature"; a method body can. (Example: the
   `ObjectMapper` with `INDENT_OUTPUT` on.)

Flip side: for your own plain classes, `@Component`/`@Service` is shorter and keeps
the recipe next to the class. One line: **own it and it needs no setup → annotate it;
foreign or needs setup → `@Bean` method.**

### Common mistakes

- **Forgetting `@Configuration` on the class.** The scan skips it, no `@Bean` method
  runs, the beans do not exist. Checked for real (mark removed, app restarted):

  ```text
  ***************************
  APPLICATION FAILED TO START
  ***************************

  Description:

  Parameter 0 of constructor in com.zenith.scopes.PlatformController required a bean of type 'java.time.Clock' that could not be found.

  Action:

  Consider defining a bean of type 'java.time.Clock' in your configuration.
  ```

  The message blames the bean's *user* (the unfillable slot), not the recipe file —
  the box has no idea the recipe file exists.
- **Calling the method yourself.** `new PlatformConfig().clock()` is a plain method
  call; the result is a loose object, not a bean. Only Spring's startup call counts.
- **Expecting `@Bean` beans to behave differently.** Same box, same rules.
- **Replacing a Boot-provided bean without meaning to.** Your own `ObjectMapper` makes
  Boot's step aside entirely — fine when intended, surprising when not.

### Link to the task

**S7 complete:** a `@Configuration` class producing three beans from classes not
written by us — `Clock` (JDK), `ObjectMapper` (Jackson), `RestClient` (Spring) — one
fed by a bean we do own. This also satisfies the done-when item "at least two beans
from classes you did not write". **S9 answered** above (and run as an Ask-AI prompt in
the DIY).

### Do it yourself (second laptop)

**Setup:** the scratch app from Lesson 7 (`/quote` shows three rules).

1. Create `PaymentProperties.java`, `PlatformConfig.java`, `PlatformController.java`
   exactly as above.
2. `mvn spring-boot:run`
3. In a second terminal: `curl http://localhost:8080/platform` → multi-line indented
   JSON, UTC `timeNow`, `"paymentsClientType" : "DefaultRestClient"`.
4. Break it: delete the `@Configuration` line, restart → `APPLICATION FAILED TO START`
   with `required a bean of type 'java.time.Clock' that could not be found`. Put it
   back, restart, `/platform` works again.
5. S9 Ask-AI prompt: *"Explain when I should prefer a `@Bean` method in a
   `@Configuration` class over putting `@Component` on the class itself, with two
   examples from a typical backend."* Compare with the two situations above.

**Done when:** pretty JSON seen, break-and-fix done, the one-line rule said out loud.

### Summary for revision

- `@Configuration` class + `@Bean` methods = hand-written recipes for the box.
- Method runs once at startup; return value becomes the bean; method name becomes the
  bean name; singleton by default.
- `@Bean` method parameters are filled from the box, like constructor slots.
- Use `@Bean` for foreign classes or construction that needs code; annotate your own
  plain classes directly.
- No `@Configuration` → recipes never run → "required a bean ... could not be found"
  at the first slot that wanted one.

### Check questions, with answers

**Question 1.** Given:

```java
@Configuration
public class ToolsConfig {
    @Bean
    Random diceRoller() {
        return new Random();
    }
}

@Service
public class LuckService {
    private final Random dice;

    public LuckService(Random dice) {
        this.dice = dice;
    }

    public int roll() {
        return dice.nextInt(6) + 1;   // 1 to 6
    }
}
```

(a) When does `diceRoller()` run, and how many times? (b) What is the bean's name?
(c) If a second service also injects `Random`, same object or new one?

**Answer.** **(a)** Once, at startup, when Spring processes the `@Configuration`
class — never again afterwards. **(b)** `diceRoller` — the method name, exactly as a
scanned class's name is its class name with a small first letter. **(c)** The same
object — `@Bean` beans are singletons by default, like every other bean. Checked for
real:

```text
Random bean names: [diceRoller]
same Random twice? true
roll() -> 1
roll() -> 2
```

**Question 2.** `@Component`/`@Service` on the class, or `@Bean` method — and why?

1. `SeatHoldService` — a class you wrote, plain constructor.
2. `ObjectMapper` — Jackson's JSON converter.
3. `InMemoryHoldRepository` — a class you wrote, plain constructor.
4. `Clock` — the JDK's time source.

**Answer.**

| Class | Choice | Why |
|---|---|---|
| `SeatHoldService` | `@Service` on the class | You own the source; construction is plain |
| `ObjectMapper` | `@Bean` method | Jackson's class — you cannot edit its source; usually needs configuring too |
| `InMemoryHoldRepository` | `@Component` on the class | You own the source; construction is plain |
| `Clock` | `@Bean` method | JDK class — cannot be edited; you also choose which clock (`systemUTC()`) in the method body |

---

## Lesson 9 — The `Clock` habit: testable time (subtask S8) + the checkpoint

### Definition

The habit: **code that needs the current time asks an injected `Clock` — it never asks
the computer directly.** In the running app the `Clock` is the bean from
`PlatformConfig` (`Clock.systemUTC()`, real time in UTC). In a test, the service gets a
**frozen clock** — `Clock.fixed(moment, zone)`, whose answer never changes — and cannot
tell the difference, because both are `Clock`s.

### Why it is needed

The project's seat **holds expire after 10 minutes**. The naive way to check expiry
calls `Instant.now()` (a static call returning the current moment). Then time is a
**hidden input nobody controls**: testing "a hold older than 10 minutes is expired"
means actually waiting ten minutes. Nobody runs that test, so expiry logic ships
untested. With a `Clock` slot, time becomes an ordinary swappable dependency — the test
says "pretend it is 10:11" and checks instantly.

### How it works

- `clock.instant()` asks the clock for "now": real time from the production bean, a
  constant from `Clock.fixed(...)`.
- In production, the box fills the service's `Clock` slot. A test does not start
  Spring at all — it calls `new SeatHoldService(frozenClock)` by hand. **Constructor
  injection makes this possible**: everything enters through the front door, so a test
  can hand in whatever it likes.

### The code

The service:

```java
package com.zenith.scopes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SeatHoldService {
    private static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final Clock clock;   // asks the clock, never the computer directly

    public SeatHoldService(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(Instant heldAt) {
        Instant now = clock.instant();
        return now.isAfter(heldAt.plus(HOLD_TIME));
    }
}
```

The test, under `src/test/java` (new folder tree). Gloss: `@Test` marks a method the
test runner executes; `assertTrue`/`assertFalse` fail the test on a wrong boolean;
`mvn test` runs everything under `src/test/java`; JUnit came with
`spring-boot-starter-test`, already in the pom:

```java
package com.zenith.scopes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SeatHoldServiceTest {

    @Test
    void nineMinutesIn_holdStillGood() {
        Instant heldAt = Instant.parse("2026-10-31T10:00:00Z");
        Clock frozenAt10_09 = Clock.fixed(Instant.parse("2026-10-31T10:09:00Z"), ZoneOffset.UTC);

        SeatHoldService service = new SeatHoldService(frozenAt10_09);

        assertFalse(service.isExpired(heldAt));
    }

    @Test
    void elevenMinutesIn_holdExpired() {
        Instant heldAt = Instant.parse("2026-10-31T10:00:00Z");
        Clock frozenAt10_11 = Clock.fixed(Instant.parse("2026-10-31T10:11:00Z"), ZoneOffset.UTC);

        SeatHoldService service = new SeatHoldService(frozenAt10_11);

        assertTrue(service.isExpired(heldAt));
    }
}
```

Note: no Spring in the test — plain `new`.

### Walkthrough, run for real

**Run 1 — green, instantly:**

```text
$ mvn test
[INFO] Running com.zenith.scopes.SeatHoldServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.027 s
[INFO] BUILD SUCCESS
```

Nine minutes **and eleven minutes** of story, checked in 0.027 seconds.

**Run 2 — the test catches a planted bug.** `ofMinutes(10)` was deliberately changed
to `ofMinutes(12)`:

```text
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
	at com.zenith.scopes.SeatHoldServiceTest.elevenMinutesIn_holdExpired(SeatHoldServiceTest.java:30)
[INFO] BUILD FAILURE
```

Decoded: with a 12-minute window, an 11-minute-old hold is wrongly still alive — the
frozen clock exposed it in milliseconds. Bug reverted; green again.

**Run 3 — production wiring.** With Spring running, the box fills the slot with the
real UTC clock bean:

```text
fresh hold expired? false
hold from an hour ago expired? true
```

Same service, two different clocks, zero code changes.

### Where it is used

Anywhere time drives behaviour: hold/session expiry, "valid until" discounts, login
token lifetimes, rate limiting, nightly job logic, `createdAt` stamps. Inject a `Clock`
and all of it is testable with frozen clocks; call `Instant.now()` directly and none of
it is.

### Common mistakes

- **Calling `Instant.now()` / `LocalDateTime.now()` inside business logic.** The
  `now()` call belongs in the `@Bean` method or behind `clock.instant()`.
- **Testing time by waiting** (`Thread.sleep` in a test): slow, flaky, imprecise.
- **Using a running clock in a test** (`Clock.systemUTC()` there too): the test's
  "now" shifts between runs — freeze it with `Clock.fixed`.
- **Giving the service test-awareness.** It never knows which clock it got; no
  `if (testing)` flags — the constructor slot does all the work.

### Link to the task

**S8 complete** — the sentence to be able to say: *holds expire after ten minutes;
with `Instant.now()` inside the code you cannot test expiry without waiting, but with
an injected `Clock` the test hands in a fixed one — this single habit makes time-based
logic testable.*

### Do it yourself (second laptop)

**Setup:** the scratch app from Lesson 8 (`/platform` shows pretty JSON).

1. Create `src/main/java/com/zenith/scopes/SeatHoldService.java` as above.
2. Create `src/test/java/com/zenith/scopes/SeatHoldServiceTest.java` (the `test`
   tree, not `main`).
3. `mvn test` → expect `Tests run: 2, Failures: 0`, `BUILD SUCCESS`, well under a
   second.
4. Plant the bug: `ofMinutes(10)` → `ofMinutes(12)`, `mvn test` → expect
   `expected: <true> but was: <false>` in `elevenMinutesIn_holdExpired` and
   `BUILD FAILURE`. Revert; green again.

**Done when:** green, red, green seen, and the S8 sentence said without reading it.

### Summary for revision

- Never `Instant.now()` inside business logic; inject `Clock` and call
  `clock.instant()`.
- Production: the `systemUTC()` bean. Tests: `Clock.fixed(moment, ZoneOffset.UTC)` via
  plain `new` — no Spring needed in the test.
- Constructor injection is what lets tests swap in the frozen clock.
- Proven: 2 expiry tests in 0.027 s, and a planted 12-minute bug caught instantly.

### Checkpoint — the task's four questions, with answers

**CP1. What is the default scope, and what does that mean for fields inside a
service?**

Singleton: one instance of the bean for the whole application, created at startup and
shared by every injection point, every request, every thread. So any mutable field in
a service is shared by all users at once — simultaneous requests interleave their
read-and-write steps and updates get lost. Checked for real in Lesson 2: two threads
made 2,000,000 `record()` calls on one `@Service`, and the counter showed only
1,008,047 — 991,953 updates vanished. The rule: services hold **dependencies** (final
collaborator fields), never per-request **data**; data lives in parameters and local
variables.

**CP2. Two implementations of one interface are on the classpath. Describe the error
and two different fixes.**

The app fails at startup — `NoUniqueBeanDefinitionException` behind Boot's friendly
block (checked for real in Lesson 4):

```text
Parameter 0 of constructor in com.zenith.scopes.QuoteService required a single bean, but 2 were found:
	- flatPricingRule: defined in file [.../FlatPricingRule.class]
	- tieredPricingRule: defined in file [.../TieredPricingRule.class]
```

Fix 1: **`@Primary`** on one implementation — the app-wide default whenever a single
slot is ambiguous; pick it when one candidate is the normal choice nearly everywhere.
Fix 2: **`@Qualifier("beanName")`** at the injection point — chooses by bean name for
that slot only; pick it when different slots need different implementations. Qualifier
beats primary at any slot where both apply (specific beats default). And if the design
really wants *all* implementations, a `List<Interface>` slot takes everyone and
removes the ambiguity altogether.

**CP3. Why can you not put `@Component` on `ObjectMapper`, and what do you do
instead?**

`ObjectMapper` is Jackson's class; its source file lives inside a published jar, and
`@Component` must be typed into the source — which cannot be edited. So the scan can
never create it. Instead, write a `@Bean` method inside a `@Configuration` class:
construct and configure the object yourself and return it; Spring calls the method
once at startup and stores the result in the box under the method's name. Checked for
real in Lesson 8: the box reported `ObjectMapper bean names: [objectMapper]`, and
every JSON response came out indented because the whole web layer used that bean.

**CP4. Why does injecting a `Clock` make expiry logic testable?**

Expiry logic compares "now" with "held at + 10 minutes". If the code calls
`Instant.now()` directly, "now" is uncontrollable, so the only honest test waits ten
real minutes. With a `Clock` constructor slot, production receives the real UTC clock
bean, while a test builds the service by hand with `Clock.fixed(...)` and checks both
sides of the ten-minute line instantly. Checked for real in this lesson: both boundary
tests ran in 0.027 seconds, and a deliberately planted 12-minute bug was caught at
once with `expected: <true> but was: <false>`.

---

## Task F6.3 — complete

Done-when audit:

- **Singleton counter demonstrated** — `/count` returned `call number 1`, `2`, `3`
  across separate requests: one shared bean (Lesson 1).
- **Two-candidate failure caused and fixed both ways** — `required a single bean, but
  2 were found`, fixed with `@Primary` on the tiered rule (quote 140), then overridden
  with `@Qualifier("flatPricingRule")` (quote 150); specific beats default (Lessons
  4–6).
- **`@Configuration` class producing at least two beans from classes not written by
  us** — three: `Clock` (JDK), `ObjectMapper` (Jackson), `RestClient` (Spring), one
  of them fed by our own `PaymentProperties` bean (Lesson 8).

Final state of the scratch app (`scratch-app/`): 14 classes under `src/main/java` and
one test under `src/test/java`; `mvn spring-boot:run` serves `/count`, `/quote` and
`/platform`; `mvn test` is green.

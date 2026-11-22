# F7.4 — Mockito in practice, and the over-mocking trap

**Learning outcome.** You can set up a mock's return value, capture the argument it was
given, force it to throw so you can test an error path, and recognise a test suite that
passes while the application is broken.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **`mock()`** | Creates a fake implementation of an interface where every method returns a default until you say otherwise. |
| **`when(...).thenReturn(...)`** | "When this method is called like this, return that." Called stubbing. |
| **`verify(...)`** | Asserts that a call happened, optionally a specific number of times. |
| **Argument matcher** | `any()`, `eq()`, `argThat()`. Used instead of exact values when stubbing or verifying. |
| **`ArgumentCaptor`** | Catches the object that was passed to a mock so you can assert about it afterwards. |
| **Over-mocking** | Replacing so much of the system with doubles that the test no longer exercises anything real. |

## The task's subtasks

- **S1** — One unit test for `SeatHoldService` with a Mockito mock repository and a fixed `Clock`. Built with plain `new`, no Spring. Time `mvn test`.
- **S2** — Stub a return: `when(holds.findById(id)).thenReturn(Optional.of(hold))`, then assert the result.
- **S3** — Verify interactions: `verify(payments).refund(orderId)` and `verify(payments, never()).charge(any())`.
- **S4** — Use `ArgumentCaptor` to catch the `Hold` passed to `save`, and assert its expiry = fixed clock + 10 minutes.
- **S5** — Force an error with `thenThrow`, and assert the service converts it to the right domain exception.
- **S6** — Break it on purpose: an over-mocked test that stays green while the real code is broken.
- **S7** — The same test with the in-memory fake, which catches the break. Record the preference rule.
- **S8** — Read `UnnecessaryStubbingException` instead of switching it off.
- **S9** — Ask AI for a 90%-mocks test, what it proves, and a rewrite with a fake.
- **CP** — Four checkpoint questions, answered out loud.

## Roadmap for this task — 11 small lessons

| # | Lesson | What it gives you | Serves |
|---|---|---|---|
| 1 | What Mockito is, and putting it into the project | The tool installed, and why it beats handwritten doubles | S1 |
| 2 | `mock()` and the default answers | What a fresh mock says before you teach it anything | S1, S2 |
| 3 | Stubbing: `when(...).thenReturn(...)` | How to teach the mock one answer | S2 |
| 4 | The first real unit test, timed | Mock repository + fixed clock + plain `new`, and the speed proof | S1 |
| 5 | Argument matchers: `any()`, `eq()`, `argThat()` | How to stub and verify without exact values | S3–S5 prep |
| 6 | `verify(...)`, `times(...)`, `never()` | Asserting that a call happened, when the call is the behaviour | S3 |
| 7 | `ArgumentCaptor` | Catching the object your code built, to test a real rule | S4 |
| 8 | Error paths with `thenThrow(...)` | Forcing failures you cannot trigger any other way | S5 |
| 9 | `UnnecessaryStubbingException` | Reading Mockito's complaint about a stub nobody called | S8 |
| 10 | Over-mocking: watch a green test lie | You break the code; the over-mocked test stays green | S6 |
| 11 | Mock vs fake, side by side | The fake catches the break; the preference rule | S7, S9 |

---

## Lesson 1 — What Mockito is, and putting it into the project (serves S1)

### Definition

Mockito is a library. A library is code written by other people that you add to your
project. Mockito's one job: build test doubles for you, at run time, one line each.

### Why it is needed

In F7.3 every double was written by hand. Here is one of them, in full:

```java
// Handwritten in F7.3. One of SIX handwritten double classes.
public class MockHoldRepository implements HoldRepository {

    private final Hold cannedHold;

    public int findByIdCallCount = 0;
    public long lastAskedId = -1;

    public MockHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdCallCount = findByIdCallCount + 1;
        lastAskedId = id;
        return Optional.of(cannedHold);
    }

    @Override
    public void save(Hold hold) {
        // nothing
    }

    @Override
    public void deleteById(long id) {
        // nothing
    }
}
```

The pain of the handwritten way:

- F7.3 needed **six** such classes: two stubs, a mock, a fake, a spy, and a mock gateway.
- Every new canned answer needs a new class, or new fields in an old one.
- If the interface gains one method, all six classes stop compiling at once.

Mockito removes this pain. It builds the double in one line. When the interface grows,
the built double grows with it. You write nothing.

### Key points

- Mockito is a test-only library. It goes in `pom.xml` with `<scope>test</scope>`.
- `Mockito.mock(SomeInterface.class)` returns an object that implements the interface.
- The class of that object is written **in memory while the test runs**. It never exists
  as a `.java` file.
- The mock is connected to nothing real. No database, no network, no files.

### How it works, step by step

1. You hand Mockito an interface: `Mockito.mock(HoldRepository.class)`.
2. While the test runs, Mockito writes a brand-new class in memory. That class
   implements the interface.
3. Mockito creates one object of that new class and hands it back to you.
4. That object answers every method with a safe default, and it remembers every call
   made to it. The defaults are Lesson 2. The call memory is used by `verify` in Lesson 6.

### The example

The system under test for the whole task. It merges F7.3's seat rule with F6.3's clock
rule. Three small files:

`src/main/java/com/zenith/holds/Hold.java`:

```java
package com.zenith.holds;

import java.time.Instant;

/**
 * A seat hold: a customer is holding some seats for an event, but has not paid yet.
 * expiresAt is the moment the hold stops being valid.
 */
public record Hold(long id, int seats, Instant expiresAt) {
}
```

`src/main/java/com/zenith/holds/HoldRepository.java`:

```java
package com.zenith.holds;

import java.util.Optional;

/**
 * The service's named need: "I need something that can store and find holds."
 */
public interface HoldRepository {

    /** Find the hold with this id. The Optional is empty when there is none. */
    Optional<Hold> findById(long id);

    /** Store this hold. */
    void save(Hold hold);

    /** Remove the hold with this id. */
    void deleteById(long id);
}
```

`src/main/java/com/zenith/holds/SeatHoldService.java`:

```java
package com.zenith.holds;

import java.time.Clock;
import java.util.Optional;

/**
 * Rule: a hold may take one more seat only if the hold exists,
 * has not expired, and has fewer than 10 seats.
 */
public class SeatHoldService {

    private final HoldRepository repository;
    private final Clock clock; // asks the clock, never the computer directly

    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        Hold hold = found.get();
        boolean expired = clock.instant().isAfter(hold.expiresAt());
        return !expired && hold.seats() < 10;
    }
}
```

The Mockito dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.17.0</version>
    <scope>test</scope>
</dependency>
```

The smallest possible proof, `src/test/java/com/zenith/holds/L1MockitoIsHereTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class L1MockitoIsHereTest {

    @Test
    void mockitoCanManufactureARepository() {
        HoldRepository manufactured = Mockito.mock(HoldRepository.class);

        assertNotNull(manufactured); // an object exists; we wrote no class for it
        System.out.println("Mockito built an object of class: "
                + manufactured.getClass().getName());
    }
}
```

Real output from `mvn test`:

```text
[INFO] Running com.zenith.holds.L1MockitoIsHereTest
WARNING: A Java agent has been loaded dynamically (...byte-buddy-agent-1.15.11.jar)
Mockito built an object of class: com.zenith.holds.HoldRepository$MockitoMock$uO1xfitG
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough

- `Mockito.mock(HoldRepository.class)` — "Mockito, build me something that implements
  `HoldRepository`." The `.class` part passes the interface itself as a value, so Mockito
  can read what methods it must provide.
- The returned object goes into a variable of type `HoldRepository`. The service will
  accept it, because the service only asks for that interface.
- The printed class name is `HoldRepository$MockitoMock$uO1xfitG`. No file on disk has
  that name. Mockito invented the class while the test ran. That is the whole trick.
- The `WARNING: A Java agent has been loaded dynamically` lines: to invent classes at
  run time, Mockito plugs a small helper tool into the running Java program. Java
  announces that out loud. It is a notice, not an error.

### Important terms

| Term | Meaning |
| --- | --- |
| Library | Code written by other people that you add to your project. |
| Dependency | An entry in `pom.xml` that tells Maven to download and use a library. |
| `<scope>test</scope>` | "This library exists only while compiling and running tests." It is not packaged into the real application. |
| `Mockito.mock(X.class)` | Build and return an object that implements interface `X`. |

### Where Mockito is used

Almost every Java backend team uses Mockito for unit tests. Spring Boot's test starter
ships it by default — that is why version 5.17.0 was already in the local Maven cache
from the F6.x tasks. Typical uses: standing in for payment providers, email senders,
and repositories, so unit tests run in milliseconds.

### Common mistakes

- **Dropping `<scope>test</scope>`.** Without it, Mockito gets packaged into the real
  application and ships to production. Everything still compiles, so nobody notices.
- **Copying `mockito-all` from old tutorials.** That artifact is dead. The correct
  artifact is `mockito-core`.
- **Thinking the mock is connected to something real.** It is not. It is an empty shell
  that answers defaults until you teach it.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Mockito builds test doubles for you at run time, one line each.
- `Mockito.mock(HoldRepository.class)` returns an object of a class invented in memory.
- The dependency is `org.mockito:mockito-core` with `<scope>test</scope>`.
- The "Java agent has been loaded dynamically" warning is a notice, not an error.
- Handwritten doubles still exist (F7.3 wrote six); Mockito replaces the boilerplate ones.

### Practice questions (with answers)

**Question 1.** Here is an interface and one line of test code:

```java
public interface PaymentGateway {
    void refund(long orderId);
}
```

```java
PaymentGateway gateway = Mockito.mock(PaymentGateway.class);
```

When that line runs, an object comes back. Where does the class of that object come
from: (a) a `.java` file in your project, (b) the payment provider's servers, or
(c) somewhere else?

**Answer.** (c). Mockito writes a brand-new class in memory while the test runs. The
class implements `PaymentGateway`. It is not in your source code, and it talks to no
real payment system. Its `refund` method does nothing until you teach it otherwise.

**Question 2.** Two versions of a `pom.xml` entry:

```xml
<!-- Version A -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.17.0</version>
    <scope>test</scope>
</dependency>

<!-- Version B -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.17.0</version>
</dependency>
```

Both compile. All tests pass with both. What is the practical difference, and why would
a team reject Version B in code review?

**Answer.** Version A makes Mockito available only during test compilation and test
runs. Version B makes Mockito part of the application itself, so it gets packaged and
shipped to production. A test tool in production is dead weight and needless risk, so
reviewers reject Version B. Nothing warns you at build time — that is why the scope
line matters.

**Question 3.** You run `mvn test` and see this line before your tests pass:
`WARNING: A Java agent has been loaded dynamically (...byte-buddy-agent...)`.
Is the build broken?

**Answer.** No. To invent classes at run time, Mockito plugs a helper tool (an "agent")
into the running Java program, and Java announces that. It is a notice. The tests
passed; the build is fine.

### Do it yourself — Lesson 1 (second laptop, nothing installed)

**Goal:** install Mockito into a fresh project and watch it manufacture a class.

1. Install the tools (macOS). If `brew` is missing, first run the install command from
   https://brew.sh.

```bash
brew install openjdk@21 maven
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version   # expect first line like: openjdk version "21.0.x"
mvn -version    # expect first line like: Apache Maven 3.x
```

2. Create the project folders:

```bash
mkdir -p ~/zenith/mockito-practice/scratch-app/src/main/java/com/zenith/holds
mkdir -p ~/zenith/mockito-practice/scratch-app/src/test/java/com/zenith/holds
cd ~/zenith/mockito-practice/scratch-app
```

3. Create `pom.xml` in `scratch-app` with exactly this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.zenith</groupId>
    <artifactId>mockito-practice-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
        </dependency>

        <!-- Mockito: builds test doubles for us at run time. -->
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.17.0</version>
            <scope>test</scope>
        </dependency>

        <!-- Mockito's JUnit 5 add-on. Used in a later lesson; explained there. -->
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <version>5.17.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.6</version>
            </plugin>
        </plugins>
    </build>
</project>
```

4. Create `src/main/java/com/zenith/holds/Hold.java`:

```java
package com.zenith.holds;

import java.time.Instant;

/**
 * A seat hold: a customer is holding some seats for an event, but has not paid yet.
 * expiresAt is the moment the hold stops being valid.
 */
public record Hold(long id, int seats, Instant expiresAt) {
}
```

5. Create `src/main/java/com/zenith/holds/HoldRepository.java`:

```java
package com.zenith.holds;

import java.util.Optional;

/**
 * The service's named need: "I need something that can store and find holds."
 */
public interface HoldRepository {

    /** Find the hold with this id. The Optional is empty when there is none. */
    Optional<Hold> findById(long id);

    /** Store this hold. */
    void save(Hold hold);

    /** Remove the hold with this id. */
    void deleteById(long id);
}
```

6. Create `src/main/java/com/zenith/holds/SeatHoldService.java`:

```java
package com.zenith.holds;

import java.time.Clock;
import java.util.Optional;

/**
 * Rule: a hold may take one more seat only if the hold exists,
 * has not expired, and has fewer than 10 seats.
 */
public class SeatHoldService {

    private final HoldRepository repository;
    private final Clock clock; // asks the clock, never the computer directly

    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        Hold hold = found.get();
        boolean expired = clock.instant().isAfter(hold.expiresAt());
        return !expired && hold.seats() < 10;
    }
}
```

7. Create `src/test/java/com/zenith/holds/L1MockitoIsHereTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class L1MockitoIsHereTest {

    @Test
    void mockitoCanManufactureARepository() {
        HoldRepository manufactured = Mockito.mock(HoldRepository.class);

        assertNotNull(manufactured);
        System.out.println("Mockito built an object of class: "
                + manufactured.getClass().getName());
    }
}
```

8. Run it. The first run downloads Mockito, so it needs the internet and takes a minute:

```bash
mvn test
```

**Expect (success):** several `WARNING: A Java agent has been loaded dynamically` lines
(a notice, not an error), then:

```text
Mockito built an object of class: com.zenith.holds.HoldRepository$MockitoMock$<random letters>
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Expect (failure you might hit):** if the `pom.xml` version has a typo, Maven says
`Could not resolve dependencies ... org.mockito:mockito-core:jar:<bad version>`.
Fix the version to `5.17.0` and rerun.

**Done when:** you see a printed class name containing `$MockitoMock$` — a class you
never wrote.

---

## Lesson 2 — `mock()` and the default answers (serves S1, S2)

### Definition

A fresh mock is a mock you have not taught yet. A fresh mock answers every method with
the safest empty value for its return type. Methods that return nothing do nothing at all.

### Why it is needed

Imagine the opposite: every unscripted call crashes the test. Then every test would have
to script every method of every double, even methods the scenario never touches. Huge
noise, no value. So Mockito chooses silence: unscripted calls answer a harmless default.

The price: you must recognise those defaults. A mysterious `null`, `0`, `false`, or
empty result in a test almost always means "this came from a mock nobody taught". That
is the most common confusion in real test suites.

### Key points

When a method is called on a fresh mock, Mockito looks only at the return type and picks:

| Return type | Default answer |
|---|---|
| an object (`String`, `Hold`, ...) | `null` |
| a number (`int`, `long`, ...) | `0` |
| `boolean` | `false` |
| a collection (`List`, `Map`, ...) | empty collection |
| `Optional<...>` | empty `Optional` |
| `void` | nothing happens |

And one rule that matters a lot: **no call changes what the mock knows.** `save(hold)`
stores nothing. A mock is not a little database. (It does remember *that* it was called —
that memory feeds `verify` in Lesson 6 — but it has no working memory of data.)

### How it works

1. Your code calls a method on the mock.
2. The mock checks: was I taught an answer for this call? (Teaching is Lesson 3.)
3. If not, it looks at the return type and answers with the default from the table.
4. Nothing else happens. No storage, no side effects.

### The example

`src/test/java/com/zenith/holds/L2DefaultAnswersTest.java`, using the record
`Hold(long id, int seats, Instant expiresAt)`, the interface `HoldRepository`
(`findById`, `save`, `deleteById`), and `SeatHoldService` from Lesson 1:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class L2DefaultAnswersTest {

    /** A small demo interface, only to watch defaults across return types. */
    interface Gadget {
        String name();
        int count();
        boolean active();
        List<String> parts();
    }

    @Test
    void freshMockAnswersSafeDefaults() {
        Gadget gadget = Mockito.mock(Gadget.class);

        System.out.println("name()   -> " + gadget.name());
        System.out.println("count()  -> " + gadget.count());
        System.out.println("active() -> " + gadget.active());
        System.out.println("parts()  -> " + gadget.parts());
    }

    @Test
    void saveStoresNothing_aMockIsNotADatabase() {
        HoldRepository repository = Mockito.mock(HoldRepository.class);

        repository.save(new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z")));

        System.out.println("findById(7) after save -> " + repository.findById(7));
        assertTrue(repository.findById(7).isEmpty()); // nothing was stored
    }

    @Test
    void serviceWithFreshMock_alwaysSaysNo() {
        HoldRepository repository = Mockito.mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        // findById answers its default (empty Optional), so the service says no.
        assertFalse(service.canAddSeat(7));
    }
}
```

Real output from `mvn test`:

```text
[INFO] Running com.zenith.holds.L2DefaultAnswersTest
name()   -> null
count()  -> 0
active() -> false
parts()  -> []
findById(7) after save -> Optional.empty
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough

- **Test 1 — the defaults.** `Gadget` is mocked and taught nothing. `name()` returns an
  object type, so `null`. `count()` returns a number, so `0`. `active()` returns a
  boolean, so `false`. `parts()` returns a `List`, so an empty list `[]`.
- **Test 2 — the big one.** `save(...)` is a void method, so the mock does nothing with
  the hold. It does not store it. The next line asks `findById(7)` and gets the default:
  `Optional.empty`. A beginner expects the saved hold back. The mock has no such memory.
  **A mock is not a little database.**
- **Test 3 — the service meets a fresh mock.** `canAddSeat(7)` calls
  `repository.findById(7)`. The fresh mock answers the default, an empty `Optional`.
  `found.isEmpty()` is true, so the method returns `false` at the first `if`. The seat
  rule and the clock were never reached. The test passes because of a default, not
  because of logic.

### Important terms

| Term | Meaning |
| --- | --- |
| Fresh mock | A mock that has not been taught any answers yet. |
| Default answer | The safe empty value a fresh mock returns, picked by return type. |

### Where this is used

Diagnosing real test suites. The classic case: a test dies with `NullPointerException`
deep inside the code. The cause: a mock returned its default `null`, and the code called
a method on it. Knowing the defaults table, you read that crash instantly: "an unstubbed
mock method was hit." Also, reading a teammate's test: every method left unstubbed
quietly says "this call does not matter for this scenario."

### Common mistakes

- **Expecting `save` to store.** That is what a *fake* does — F7.3's
  `FakeHoldRepository` genuinely kept holds in a `HashMap`. A mock keeps nothing.
- **Using a `null` default by accident.** The mock returns `null`, your code calls
  something on it, and you get a `NullPointerException` far from the real cause.
- **Trusting a pass that came from a default.** `assertFalse(service.canAddSeat(7))`
  with a fresh mock would still pass if someone deleted the whole seat rule — `false`
  was the default path, not proof of logic. This crack becomes the over-mocking trap in
  Lesson 10.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Fresh mock defaults: object → `null`, number → `0`, boolean → `false`,
  collection → empty, `Optional` → empty, void → nothing.
- No call changes what the mock knows. `save` stores nothing.
- A surprising `null`/`0`/`false`/empty in a test usually means: unstubbed mock method.
- A test can pass because of a default, not because of your logic. Stay suspicious.

### Practice questions (with answers)

**Question 1.** Here is an interface and a fresh mock of it:

```java
public interface Inventory {
    int stock();
    String label();
    List<String> tags();
    void reindex();
}
```

```java
Inventory inventory = Mockito.mock(Inventory.class);
```

Nobody has taught this mock anything. What does each of these four calls return or do:
`inventory.stock()`, `inventory.label()`, `inventory.tags()`, `inventory.reindex()`?

**Answer.** `stock()` returns `0` (number default). `label()` returns `null` (object
default). `tags()` returns an empty list (collection default). `reindex()` does nothing
at all (void default).

**Question 2.** A teammate writes this, using the record
`Hold(long id, int seats, Instant expiresAt)` and the interface method
`Optional<Hold> findById(long id)`:

```java
HoldRepository repository = Mockito.mock(HoldRepository.class);
repository.save(new Hold(5, 2, Instant.parse("2026-11-17T12:00:00Z")));
Optional<Hold> result = repository.findById(5);
System.out.println(result);
```

The teammate expects it to print an `Optional` containing the hold. What does it
actually print, and why — in one sentence?

**Answer.** It prints `Optional.empty`, because a mock's `save` stores nothing and
`findById` just answers its default, the empty `Optional`.

**Question 3.** A test calls a service method. Deep inside the service, the code crashes
with `NullPointerException` on a line that reads `customer.name().length()`. The
`customer` object came from a mocked `CustomerRepository`. What is the most likely cause?

**Answer.** The mocked repository method was never stubbed, so it returned its default.
Either the repository returned `null` for `customer`, or `customer` is a mock whose
`name()` returned its default `null`, and calling `.length()` on `null` crashed. The fix
is to stub the call to return a real object.

### Do it yourself — Lesson 2 (second laptop)

**Goal:** watch the defaults with your own eyes, including the save-that-stores-nothing.

This continues in the project from the Lesson 1 "Do it yourself" (folder
`~/zenith/mockito-practice/scratch-app`, with `pom.xml`, `Hold.java`,
`HoldRepository.java`, `SeatHoldService.java` already created there).

1. Create `src/test/java/com/zenith/holds/L2DefaultAnswersTest.java` with exactly the
   test class shown in "The example" of this lesson.

2. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):**

```text
name()   -> null
count()  -> 0
active() -> false
parts()  -> []
findById(7) after save -> Optional.empty
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(4 tests total: 3 new ones plus the Lesson 1 test.)

3. Feel the trap once: inside the test `saveStoresNothing_aMockIsNotADatabase`, change
   `assertTrue` to `assertFalse` and rerun.

**Expect (failure):**

```text
[ERROR] L2DefaultAnswersTest.saveStoresNothing_aMockIsNotADatabase:45 expected: <false> but was: <true>
```

Decode: `findById(7).isEmpty()` really is `true` — the save stored nothing. Change it
back to `assertTrue` and rerun to green.

**Done when:** you can say, without looking, what a fresh mock returns for an object, a
number, a boolean, a collection, and an `Optional` — and why `findById` after `save` is
still empty.

---

## Lesson 3 — Stubbing: `when(...).thenReturn(...)` (serves S2)

### Definition

Stubbing is teaching a mock one answer. The line
`when(repository.findById(7)).thenReturn(Optional.of(hold))` reads as plain English:
"When asked `findById(7)`, answer with this hold."

### Why it is needed

A fresh mock only answers defaults. With defaults, `findById` always answers an empty
`Optional`, so `SeatHoldService.canAddSeat` always leaves at the first `if` and returns
`false`. The seat rule — the code we actually want to test — is never reached. To reach
it, the repository must answer with a real hold. In F7.3 that meant a whole handwritten
class per canned answer. With Mockito it is one line per canned answer, written inside
the test that needs it.

### Key points

- `when(call).thenReturn(answer)` attaches an answer to one exact call.
- **Exact-argument matching:** teach `7`, ask `8`, and you silently get the default.
- The taught answer repeats forever, as many times as asked.
- The `when...thenReturn` line is the *teaching phase*. The real call inside the service
  is the *using phase*.
- Static imports (`import static org.mockito.Mockito.mock;` and `...when;`) let you
  write `mock(...)` and `when(...)` without the `Mockito.` prefix. All real-world
  Mockito code reads this way.

### How it works

Take `when(repository.findById(7)).thenReturn(Optional.of(hold))` and slow it down:

1. Java runs the inside of parentheses first, so `repository.findById(7)` **really
   runs**, on the mock, right now.
2. The mock writes down: "last call: `findById` with argument `7`". Its default return
   value is thrown away.
3. `when(...)` picks up that written-down call.
4. `.thenReturn(Optional.of(hold))` attaches the answer to exactly that call.
5. From now on, every `findById(7)` gets the hold. `findById(8)` still gets the default.

### The example

`src/test/java/com/zenith/holds/L3StubbingTest.java`, using the record
`Hold(long id, int seats, Instant expiresAt)` and `SeatHoldService` (rule: seat allowed
only if the hold exists, is not expired, and has fewer than 10 seats):

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class L3StubbingTest {

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        HoldRepository repository = mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(hold)); // the teaching line

        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        assertTrue(service.canAddSeat(7)); // 4 < 10, and 10:00 is before 10:10
    }

    @Test
    void fullHold_refusesAnotherSeat() {
        HoldRepository repository = mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        Hold fullHold = new Hold(7, 10, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(fullHold));

        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        assertFalse(service.canAddSeat(7)); // 10 seats is the cap
    }

    @Test
    void stubAnswersOnlyTheExactArgumentItWasTaught() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(hold));

        System.out.println("findById(7) -> " + repository.findById(7));
        System.out.println("findById(8) -> " + repository.findById(8));
        assertTrue(repository.findById(8).isEmpty()); // 8 was never taught -> default
    }
}
```

Real output from `mvn test`:

```text
[INFO] Running com.zenith.holds.L3StubbingTest
findById(7) -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(8) -> Optional.empty
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s
[INFO] BUILD SUCCESS
```

### Walkthrough

Trace of the first test:

- **Setup.** A mock repository. A clock frozen at 10:00. A hold with id 7, 4 seats,
  expiring at 10:10.
- **Teach.** `when(repository.findById(7)).thenReturn(Optional.of(hold))`.
- **Use.** `service.canAddSeat(7)` runs. Inside: `repository.findById(7)` answers the
  taught hold. Not empty, so past the `if`. `clock.instant()` is always 10:00. Is 10:00
  after 10:10? No, so not expired. Then `4 < 10` is true, so the method returns `true`.
- **Assert.** Green — and this time the rule itself ran, not a default.

The second test changes one number (10 seats) and the same rule answers `false`. The
third test shows exact-argument matching in the printed output: `7` answers the hold,
`8` answers `Optional.empty`.

### Important terms

| Term | Meaning |
| --- | --- |
| Stubbing | Teaching a mock an answer for one call. |
| `when(call)` | Wraps the call being taught. The call really runs on the mock during teaching. |
| `thenReturn(x)` | The answer to attach to that call. |
| Exact-argument matching | The answer applies only to the exact argument taught. |
| Static import | `import static` lets you call `mock(...)`/`when(...)` without the `Mockito.` prefix. |

### Where stubbing is used

The first lines of almost every unit test in real codebases: repositories taught to
answer entities, HTTP clients taught to answer canned responses, config readers taught
to answer settings. Stubbing puts the system into exactly the state the scenario needs,
without a database or network.

### Common mistakes

- **Teaching one argument, calling with another.** You stub `findById(7)` but the code
  asks for `8`. No error — just the silent default and a confusing red test. When a stub
  "does not work", check the argument first.
- **Misreading the line.** `when` wraps the *call*, `thenReturn` carries the *answer*.
  The line only teaches; the real call happens later in the service.
- **Stubbing something nobody calls.** The test passes today but lies about the code.
  Mockito can flag this — `UnnecessaryStubbingException`, Lesson 9.
- **Trying to stub `save` this way.** `when(repository.save(hold))` does not compile:
  `save` returns `void`, so there is nothing to put inside `when(...)`.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- `when(call).thenReturn(answer)`: teach the mock one answer for one exact call.
- Wrong argument → silent default, not an error.
- The answer repeats forever.
- Teaching phase (in the test) vs using phase (in the service).
- Void methods cannot go inside `when(...)`.

### Practice questions (with answers)

**Question 1.** Here is an interface, a mock, and one taught answer:

```java
public interface PriceSource {
    int priceOf(String sku);
}
```

```java
PriceSource prices = mock(PriceSource.class);
when(prices.priceOf("apple")).thenReturn(30);

int a = prices.priceOf("apple");
int b = prices.priceOf("banana");
```

What are the values of `a` and `b`, and why?

**Answer.** `a` is `30`: that exact call was taught. `b` is `0`: `"banana"` was never
taught, so the mock answers the default for `int`, which is `0`. Exact-argument
matching, silently.

**Question 2.** Here is a service and a test setup. The record is
`Hold(long id, int seats, Instant expiresAt)`.

```java
public class SeatHoldService {
    private final HoldRepository repository;
    private final Clock clock;

    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false;
        }
        Hold hold = found.get();
        boolean expired = clock.instant().isAfter(hold.expiresAt());
        return !expired && hold.seats() < 10;
    }
}
```

```java
HoldRepository repository = mock(HoldRepository.class);
Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
Hold hold = new Hold(9, 3, Instant.parse("2026-11-17T09:55:00Z"));

when(repository.findById(9)).thenReturn(Optional.of(hold));

SeatHoldService service = new SeatHoldService(repository, fixedClock);
boolean answer = service.canAddSeat(9);
```

What is `answer`, and which exact part of the rule decided it?

**Answer.** `answer` is `false`. The taught stub returns the hold, so the `if` is
passed. The clock says 10:00. The hold expired at 09:55. `10:00 isAfter 09:55` is true,
so `expired` is `true`, so `!expired` is `false`. The `&&` stops there — the expiry
check decided it. The seat count (3 < 10, which would have been fine) never got a say.

### Do it yourself — Lesson 3 (second laptop)

**Goal:** teach a mock one answer, watch it steer the service, and watch exact-argument
matching bite.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/test/java/com/zenith/holds/L3StubbingTest.java` with exactly the test
   class shown in "The example" of this lesson.

2. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):**

```text
findById(7) -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(8) -> Optional.empty
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

3. Feel exact-argument matching once. In the test `holdWithRoom_allowsOneMoreSeat`,
   change the teaching line to use id `8`:

```java
when(repository.findById(8)).thenReturn(Optional.of(hold));
```

Rerun `mvn test`.

**Expect (failure):**

```text
[ERROR] L3StubbingTest.holdWithRoom_allowsOneMoreSeat:30 expected: <true> but was: <false>
```

Decode: the service asked for `7`, but the mock was only taught `8`. So `findById(7)`
answered the default empty `Optional`, and the service said no at the first `if`.
Change the line back to `findById(7)` and rerun to green.

**Done when:** you can explain, in your own words, why the failing version failed —
without rereading this lesson.

---

## Lesson 4 — The first real unit test: one mock, one fixed clock, plain `new`, timed (serves S1)

### Definition

A unit test checks one class alone, with its needs replaced by doubles. The complete
unit test for `SeatHoldService`: a mock repository, a frozen clock, the service built
with plain `new` — no Spring anywhere — and a stopwatch on it.

### Why it is needed

Testing this service *could* involve starting a Spring application (seconds every time)
and a real database. For checking one rule — "seat allowed only if the hold exists, is
not expired, and has fewer than 10 seats" — that is absurd overhead. Because the service
asks for its needs through its constructor, a test can hand in a taught mock and a
frozen clock with plain `new`, and the rule runs in milliseconds.

### Key points — the assembly recipe

1. **Freeze time.** `Clock.fixed(instant, zone)` always answers the same moment.
2. **Mock the boundary.** `mock(HoldRepository.class)`, then teach per scenario.
3. **Build with plain `new`.** `new SeatHoldService(repository, fixedClock)`.
4. **One test per branch of the rule.** Three ways to say no (missing, expired, full),
   one way to say yes. Four tests.
5. Mock and service live in instance fields. JUnit creates a new test-class instance
   per test method, so every test gets a fresh, untaught mock.

### The example

`src/test/java/com/zenith/holds/L4SeatHoldServiceUnitTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class L4SeatHoldServiceUnitTest {

    /** The clock is frozen at 10:00. Every test below reads time from here. */
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** JUnit builds a fresh test instance per test, so each test gets a fresh mock. */
    private final HoldRepository repository = mock(HoldRepository.class);
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(hold));

        assertTrue(service.canAddSeat(7)); // 4 < 10, not expired
    }

    @Test
    void fullHold_refusesAnotherSeat() {
        Hold fullHold = new Hold(7, 10, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(fullHold));

        assertFalse(service.canAddSeat(7)); // seat cap reached
    }

    @Test
    void expiredHold_refusesAnotherSeat() {
        Hold expiredHold = new Hold(7, 4, NOW.minus(Duration.ofMinutes(5)));
        when(repository.findById(7)).thenReturn(Optional.of(expiredHold));

        assertFalse(service.canAddSeat(7)); // expired five minutes ago
    }

    @Test
    void missingHold_refusesSeat() {
        // No stubbing on purpose: the fresh mock answers Optional.empty,
        // which is exactly the "no such hold" scenario.
        assertFalse(service.canAddSeat(99));
    }
}
```

Real, timed output (`time mvn test`):

```text
[INFO] Running com.zenith.holds.L4SeatHoldServiceUnitTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s
...
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  2.634 s
mvn test  ...  3.669 total
```

### Walkthrough

The rule's last line is `return !expired && hold.seats() < 10;` plus the
`if (found.isEmpty())` above it.

- `holdWithRoom...` — the only yes. Found, expires 10:10 (clock says 10:00), 4 seats.
- `fullHold...` — same timing, 10 seats. The seat cap says no.
- `expiredHold...` — 4 seats, but expired 09:55. The clock comparison says no.
- `missingHold...` — nothing taught; `findById(99)` answers the default empty
  `Optional`; the first `if` says no. The untaught default **is** the scenario here.

Each test pins exactly one branch. If someone later breaks one branch, exactly one test
name points at the broken rule.

**Reading the timing.** Three numbers appeared:

| Number | What it measures |
|---|---|
| `Time elapsed: 0.012 s` | The 4 tests themselves: **12 milliseconds** |
| `Total time: 2.634 s` | Maven: compiling, starting a Java process, wiring the runner |
| `3.669 total` | Wall clock for the whole command |

The task's "tens of milliseconds" is the first number — hit. The ~2.6 s is fixed
overhead and does not grow with your tests. A hundred more such tests add about a
second, not a hundred times 2.6 seconds.

### Important terms

| Term | Meaning |
| --- | --- |
| Unit test | Checks one class alone, needs replaced by doubles. |
| `Clock.fixed(instant, zone)` | A clock that always answers the same moment. |
| Fixed overhead | Build time that does not grow with the number of tests. |

### Where this is used

This exact shape — mock the repository, freeze the clock, build with `new`, one test per
branch — is the standard service-layer unit test in industry. Most service tests in
serious Spring codebases look like this file, precisely because they skip Spring.

### Common mistakes

- **Using the real clock in tests.** `Clock.systemUTC()` makes expiry tests depend on
  when they run — a flaky test. Freeze time; the test controls "now".
- **Booting Spring to test one class.** `@SpringBootTest` starts the whole application
  to check a rule that needs two constructor arguments. Reach for plain `new` first.
- **Sharing one mock through a `static` field.** `static` means one shared mock for all
  tests, so teachings leak between tests. Instance fields give a clean slate each time.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Recipe: freeze clock, mock boundary, plain `new`, one test per branch.
- Fresh test instance per test method = fresh mock per test.
- `Time elapsed` = your tests; `Total time` = Maven overhead. Only the first grows.
- 4 tests ran in 12 ms. That is what "unit test speed" means.

### Practice questions (with answers)

**Question 1.** A teammate runs a suite and sees this output:

```text
[INFO] Running com.example.PricingServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s
[INFO] BUILD SUCCESS
[INFO] Total time:  2.9 s
```

The teammate says: "The task said unit tests should take tens of milliseconds, but this
took 2.9 seconds. We failed." What is the right way to read these two numbers, and what
happens to each if the team adds 200 more tests of the same kind?

**Answer.** `Time elapsed: 0.018 s` is the tests: 6 tests in 18 milliseconds — that is
the number the task talks about. `Total time: 2.9 s` is Maven overhead: starting a Java
process, compiling, wiring the runner. With 200 more similar tests, the elapsed test
time grows to something like half a second, while the overhead stays roughly the same.
The suite total would still be about 3 seconds. The teammate is reading the wrong number.

**Question 2.** Here are two versions of a service. The repository interface is
`HoldRepository { Optional<Hold> findById(long id); void save(Hold hold); void deleteById(long id); }`,
and `DatabaseHoldRepository` is a class that connects to a real Postgres database.

```java
// Version A
public class SeatHoldService {
    private final HoldRepository repository;
    private final Clock clock;

    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }
    // ... canAddSeat uses repository and clock ...
}

// Version B
public class SeatHoldService {
    private final HoldRepository repository;
    private final Clock clock;

    public SeatHoldService() {
        this.repository = new DatabaseHoldRepository("jdbc:postgresql://prod-db:5432/tickets");
        this.clock = Clock.systemUTC();
    }
    // ... canAddSeat uses repository and clock ...
}
```

Which version can be unit tested with a mock and a fixed clock, and what exactly makes
the other one impossible to test that way?

**Answer.** Version A. Its needs come in through the constructor, so a test can hand in
`mock(HoldRepository.class)` and `Clock.fixed(...)` with plain `new`. Version B builds
its own database repository (pointing at production!) and reads the real system clock
inside its constructor. There is no way to substitute either from outside. Any test of
Version B talks to a real database and real time. Constructor injection is the seam that
makes unit testing possible.

### Do it yourself — Lesson 4 (second laptop)

**Goal:** assemble the complete S1 test and time it yourself.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/test/java/com/zenith/holds/L4SeatHoldServiceUnitTest.java` with exactly
   the test class shown in "The example" of this lesson.

2. Run it with a stopwatch:

```bash
cd ~/zenith/mockito-practice/scratch-app
time mvn test
```

**Expect (success):** a line like

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.0XX s -- in com.zenith.holds.L4SeatHoldServiceUnitTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Write down two numbers: the `Time elapsed` for `L4SeatHoldServiceUnitTest` (expect tens
of milliseconds) and the `Total time` (expect a few seconds — Maven overhead, not your
tests).

3. Break one branch on purpose. In `holdWithRoom_allowsOneMoreSeat`, delete the
   `when(...)` teaching line and rerun.

**Expect (failure):**

```text
[ERROR] L4SeatHoldServiceUnitTest.holdWithRoom_allowsOneMoreSeat:33 expected: <true> but was: <false>
```

Decode: with no teaching, the mock answered the default empty `Optional`, so the service
refused at the first `if`. Put the line back and rerun to green.

**Done when:** you can point at the output and say which number is "my tests" and which
is "Maven", and your 4 tests run in tens of milliseconds.

---

## Lesson 5 — Argument matchers: `any()`, `eq()`, `argThat()` (prepares S3, S4, S5)

### Definition

An argument matcher is a placeholder for an argument. Instead of teaching the mock "when
asked `findById(7)`", a matcher teaches "when asked `findById` with **any** id" or "with
any id **above 100**". The main ones: `anyLong()`, `anyString()`, `any()` (any object),
`eq(x)` (exactly `x`), and `argThat(condition)` (anything passing your check; for
primitive parameters use `longThat`/`intThat`).

### Why it is needed

Exact-argument matching breaks down in two situations:

- **The test cannot know the exact value.** When code under test builds an object
  internally and passes it on, the test never sees that object beforehand, so it cannot
  stub or check it by exact value.
- **The value does not matter for the scenario.** `verify(payments, never()).charge(any())`
  means "never charged, with any amount whatsoever". Pinning one exact amount would miss
  charges of other amounts.

### Key points

- `anyLong()` matches every `long`; `anyString()` every `String`; `any()` every object.
- `longThat(id -> id > 100)` matches by a condition you write.
- `eq(42L)` matches exactly `42` — used when mixing exact values with matchers.
- **The all-or-none rule:** within one taught or checked call, use all raw values or all
  matchers. Never a mix. Fix a mix by wrapping raw values in `eq(...)`.
- Why the rule exists: as the line runs, each matcher drops a note into Mockito's side
  notebook; a raw value drops no note. Two arguments but one note — Mockito cannot tell
  which argument the note belongs to, so it refuses loudly.

### The example

`src/test/java/com/zenith/holds/L5MatchersTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;

class L5MatchersTest {

    /** Demo interface with TWO parameters, to show the mixing rule. */
    interface SeatMap {
        boolean reserve(long eventId, String section);
    }

    @Test
    void anyLong_oneTeachingCoversEveryId() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(anyLong())).thenReturn(Optional.of(hold));

        System.out.println("findById(1)   -> " + repository.findById(1));
        System.out.println("findById(42)  -> " + repository.findById(42));
        System.out.println("findById(999) -> " + repository.findById(999));
        assertTrue(repository.findById(123456).isPresent());
    }

    @Test
    void longThat_teachesByCondition() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        // Only ids above 100 "exist".
        when(repository.findById(longThat(id -> id > 100))).thenReturn(Optional.of(hold));

        System.out.println("findById(150) -> " + repository.findById(150));
        System.out.println("findById(5)   -> " + repository.findById(5));
        assertTrue(repository.findById(150).isPresent());
        assertTrue(repository.findById(5).isEmpty());
    }

    @Test
    void mixingRawValueAndMatcher_blowsUp() {
        SeatMap seatMap = mock(SeatMap.class);

        InvalidUseOfMatchersException boom = assertThrows(
                InvalidUseOfMatchersException.class,
                () -> when(seatMap.reserve(42, anyString())).thenReturn(true));

        System.out.println("--- real Mockito error message ---");
        System.out.println(boom.getMessage());
    }

    @Test
    void eq_fixesTheMix() {
        SeatMap seatMap = mock(SeatMap.class);

        when(seatMap.reserve(eq(42L), anyString())).thenReturn(true);

        assertTrue(seatMap.reserve(42, "balcony"));   // taught: event 42, any section
        assertFalse(seatMap.reserve(43, "balcony"));  // 43 was never taught -> default
    }
}
```

Real output from `mvn test`:

```text
findById(1)   -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(42)  -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(999) -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(150) -> Optional[Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]]
findById(5)   -> Optional.empty
--- real Mockito error message ---

Invalid use of argument matchers!
2 matchers expected, 1 recorded:
-> at com.zenith.holds.L5MatchersTest.lambda$mixingRawValueAndMatcher_blowsUp$1(L5MatchersTest.java:63)

This exception may occur if matchers are combined with raw values:
    //incorrect:
    someMethod(any(), "raw String");
When using matchers, all arguments have to be provided by matchers.
For example:
    //correct:
    someMethod(any(), eq("String by matcher"));

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s
```

### Walkthrough

- **Test 1.** One teaching line with `anyLong()` covers ids 1, 42, 999, 123456 — all
  answer the same hold.
- **Test 2.** `longThat(id -> id > 100)`: 150 passes the check and answers the hold;
  5 fails the check, so that call was never taught — default empty `Optional`.
- **Test 3 — the crime, decoded:**

| Message line | Plain meaning |
|---|---|
| `Invalid use of argument matchers!` | You broke the all-or-none rule. |
| `2 matchers expected, 1 recorded` | The call has 2 arguments, but only 1 note (from `anyString()`) landed in the notebook. The raw `42` left no note. |
| `-> at ...L5MatchersTest.java:63` | The exact line of the crime. |
| `//incorrect / //correct` block | Mockito shows the mistake pattern and the fix: wrap the raw value in `eq()`. |

- **Test 4 — the fix.** `eq(42L)` plus `anyString()`: both arguments drop notes. Event
  42 with any section answers `true`; event 43 gets the default `false`.

### Important terms

| Term | Meaning |
| --- | --- |
| Argument matcher | A placeholder for an argument when teaching or checking a mock. |
| `any()` / `anyLong()` / `anyString()` | Match everything (of that type). |
| `eq(x)` | Match exactly `x`; makes a raw value legal among matchers. |
| `argThat` / `longThat` / `intThat` | Match by a condition you write. |
| All-or-none rule | In one call: all raw values or all matchers, never a mix. |
| `InvalidUseOfMatchersException` | Mockito's refusal when the rule is broken. |

### Where matchers are used

- Stubbing when the code generates values internally (ids, timestamps, built objects).
- Verifying claims about shape rather than value — "never charged **anything**".
- Wide stubs for irrelevant lookups, keeping the test focused on what matters.

### Common mistakes

- **Mixing raw values with matchers.** The exception above; fix with `eq()`.
- **Using `any()` when the exact value is the point.** If the service must look up id 7
  but a bug makes it ask for 8, a stub on `findById(anyLong())` happily answers the
  wrong call and the test stays green. The matcher hid the bug. Use exact values when
  the value *is* the behaviour.
- **Matcher type mismatch.** `findById` takes a `long`, so use `anyLong()`/`longThat`.
  The generic `any()` on a primitive parameter can crash confusingly. Match the matcher
  to the parameter type.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Matchers replace exact arguments: `anyLong()`, `any()`, `eq(x)`, `longThat(cond)`.
- All raw or all matchers in one call; `eq()` legalises an exact value among matchers.
- The error message names the line and shows the fix — read it.
- Loose matchers can hide wrong-argument bugs. Exact values when the value matters.

### Practice questions (with answers)

**Question 1.** Here is an interface and one teaching line:

```java
public interface Mailbox {
    boolean deliver(String address, String message);
}
```

```java
Mailbox box = mock(Mailbox.class);
when(box.deliver("a@b.com", anyString())).thenReturn(true);
```

What happens when the `when(...)` line runs, and what is the smallest change that fixes it?

**Answer.** The line throws `InvalidUseOfMatchersException`: the call has two arguments,
but only `anyString()` recorded a matcher note — the raw `"a@b.com"` recorded none, so
Mockito reports "2 matchers expected, 1 recorded". The fix is to wrap the raw value:
`when(box.deliver(eq("a@b.com"), anyString())).thenReturn(true);`.

**Question 2.** The repository interface has `Optional<Hold> findById(long id)`, and the
record is `Hold(long id, int seats, Instant expiresAt)`. A test contains:

```java
HoldRepository repository = mock(HoldRepository.class);
Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));
when(repository.findById(anyLong())).thenReturn(Optional.of(hold));

// ... service code under test calls repository.findById(8) due to a bug;
// it was supposed to call findById(7) ...

assertTrue(result.isPresent()); // passes
```

The test is green. What did `anyLong()` cost us here, and when should the stub use the
exact value instead?

**Answer.** `anyLong()` answers id 8 as happily as id 7, so the test cannot notice that
the service asked for the wrong id. The matcher hid the bug. When *which* value gets
passed is part of the behaviour (the service must look up the id it was given), stub the
exact value — `findById(7)` — so a wrong lookup falls to the default and the test goes
red.

### Do it yourself — Lesson 5 (second laptop)

**Goal:** use `anyLong`, `longThat`, and `eq`, and read the mixing error with your own
eyes.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/test/java/com/zenith/holds/L5MatchersTest.java` with exactly the test
   class shown in "The example" of this lesson.

2. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):** the five `findById(...) ->` lines, then the block starting
`Invalid use of argument matchers!` (printed by the test on purpose — the test itself
passes), then:

```text
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

3. Commit the crime without the safety net. In `eq_fixesTheMix`, change `eq(42L)` to a
   raw `42` and rerun.

**Expect (failure):**

```text
[ERROR] L5MatchersTest.eq_fixesTheMix:... » InvalidUseOfMatchers
Invalid use of argument matchers!
2 matchers expected, 1 recorded
```

Decode: two arguments, but only `anyString()` dropped a note — the raw `42` did not.
Restore `eq(42L)` and rerun to green.

**Done when:** you can state the all-or-none rule from memory and name the fix for
mixing (`eq`).

---

## Lesson 6 — `verify(...)`, `times(...)`, `never()` (serves S3)

### Definition

`verify` asserts that a call **happened** on a mock. `verify(payments).refund(42)`
reads: "I claim the code called `refund(42)` on this gateway, exactly once. Fail the
test if not." Variants: `verify(mock, times(2)).method(...)` for a count,
`verify(mock, never()).method(...)` for "this must not have happened".

### Why it is needed

Some behaviour returns nothing to check. Cancelling an order must refund the customer.
`refund` returns `void` — the effect is money moving at a bank, outside the program.
After `cancel(42)`, there is no value to read anywhere. For such cases the call itself
*is* the behaviour. In F7.3 the handwritten mock counted calls in a public field and the
test asserted on the count; `verify` is that same idea, built in. A mock records every
call made to it — `verify` reads that memory.

### Key points

- Run the code, then `verify` asks the mock's memory: method, arguments, count.
- Plain `verify(mock)` means `times(1)`. `never()` means `times(0)`.
- Matchers work here too, same all-or-none rule:
  `verify(payments, never()).charge(any())`.
- Verify only calls that are promises to the outside world (a refund). Do not verify
  internal plumbing (lookups) — that is over-verifying, and it breaks on refactors.

### The example

Three new main classes:

```java
/** An amount of money, in cents, to avoid decimal rounding trouble. */
public record Money(long cents) {
}
```

```java
/**
 * Outbound boundary: talks to the payment provider's web service.
 * The real implementation moves actual money in a bank, outside the program.
 */
public interface PaymentGateway {

    /** Take the money. Returns the provider's payment id. */
    String charge(Money amount);

    /** Give the money for this order back. The effect happens at the bank. */
    void refund(long orderId);
}
```

```java
/**
 * Cancelling an order must trigger exactly one refund, and must never charge.
 * The refund call IS the behaviour; there is no value to read back.
 */
public class OrderCancelService {

    private final PaymentGateway payments;

    public OrderCancelService(PaymentGateway payments) {
        this.payments = payments;
    }

    public void cancel(long orderId) {
        payments.refund(orderId);
    }
}
```

The test, `src/test/java/com/zenith/holds/L6VerifyTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class L6VerifyTest {

    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final OrderCancelService service = new OrderCancelService(payments);

    @Test
    void cancel_refundsTheOrder() {
        service.cancel(42);

        verify(payments).refund(42); // this exact call happened (once)
    }

    @Test
    void cancellingTwice_refundsTwice() {
        service.cancel(42);
        service.cancel(42);

        verify(payments, times(2)).refund(42);
    }

    @Test
    void cancel_neverCharges() {
        service.cancel(42);

        verify(payments, never()).charge(any()); // no charge, with any amount at all
    }

    @Test
    void verifyFailure_readTheMessage() {
        service.cancel(42);

        AssertionError boom = assertThrows(AssertionError.class,
                () -> verify(payments).refund(99)); // wrong id on purpose

        System.out.println("--- real verify failure message ---");
        System.out.println(boom.getMessage());
    }
}
```

Real output (all green, 29 ms):

```text
--- real verify failure message ---

Argument(s) are different! Wanted:
paymentGateway.refund(99L);
-> at com.zenith.holds.L6VerifyTest.lambda$verifyFailure_readTheMessage$0(L6VerifyTest.java:48)
Actual invocations have different arguments:
paymentGateway.refund(42L);
-> at com.zenith.holds.OrderCancelService.cancel(OrderCancelService.java:16)

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.029 s
```

And the other classic failure, produced by commenting out `payments.refund(orderId)` in
the service:

```text
Wanted but not invoked:
paymentGateway.refund(42L);
-> at com.zenith.holds.L6VerifyTest.cancel_refundsTheOrder(L6VerifyTest.java:25)
Actually, there were zero interactions with this mock.
```

### Walkthrough

- **Test 1.** `cancel(42)` runs; the mock records `refund(42)`; verify matches. No
  stubbing anywhere — nothing needed an answer; we only read the memory.
- **Test 2.** Two cancels, `times(2)` — green. Plain `verify` (= `times(1)`) would fail:
  "Wanted 1 time but was 2 times."
- **Test 3.** The claim that makes refunds safe: cancelling must never *take* money.
  `charge` takes a `Money` object, so the generic `any()` is type-correct (for the
  primitive `long` of `refund` it would be `anyLong()`).
- **Test 4 — failure messages decoded:**

| Message line | Plain meaning |
|---|---|
| `Argument(s) are different! Wanted: refund(99L)` | What the verify claimed. |
| `Actual invocations...: refund(42L)` | What really happened. |
| `-> at OrderCancelService.cancel(...:16)` | The line in the real service that made the call. |
| `Wanted but not invoked` | The claimed call never happened at all. |
| `Actually, there were zero interactions with this mock.` | The mock was never touched. |

"Wanted but not invoked" is verify doing its real job: the refund disappeared from the
code, and the test went red. This is the one test that catches a lost refund.

### Important terms

| Term | Meaning |
| --- | --- |
| `verify(mock).m(args)` | Assert `m(args)` was called exactly once. |
| `times(n)` | Assert the call happened exactly n times. |
| `never()` | Assert the call happened zero times. |
| Over-verifying | Asserting on every interaction, welding the test to the implementation. |

### Where verify is used

Boundaries with side effects and no readable state: refunds and charges, emails sent,
messages published to a queue, audit events emitted. Everywhere the question is "did we
do it, and how many times" rather than "what did we get back".

### Common mistakes

- **Over-verifying.** Asserting every interaction breaks the test on harmless refactors
  (reorder two reads, cache a lookup). Verify promises to the outside world, not
  plumbing.
- **Verifying instead of asserting.** If a method returns a value, assert on the value.
  `verify(repository).findById(7)` next to an assert on the result is noise.
- **Expecting `verify` to stub.** `verify` only reads the past; it teaches nothing.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- `verify` reads the mock's call memory: did this call happen, how many times.
- Plain = once; `times(n)`; `never()`.
- Use it when the interaction IS the behaviour (refund, email, event).
- Two failure shapes: "Argument(s) are different" and "Wanted but not invoked".
- Over-verifying welds tests to implementations.

### Practice questions (with answers)

**Question 1.** Here is an interface and a service:

```java
public interface Notifier {
    void emailReceipt(long orderId);
}

public class CheckoutService {
    private final Notifier notifier;

    public CheckoutService(Notifier notifier) {
        this.notifier = notifier;
    }

    public void completeOrder(long orderId) {
        notifier.emailReceipt(orderId);
    }
}
```

In a test, `Notifier notifier = mock(Notifier.class);` and
`CheckoutService service = new CheckoutService(notifier);`, then
`service.completeOrder(5);` runs. Write two verify lines: one asserting order 5 got
exactly one receipt email, and one asserting order 6 got none.

**Answer.**

```java
verify(notifier).emailReceipt(5);           // exactly once (times(1) is implied)
verify(notifier, never()).emailReceipt(6);  // not even once
```

**Question 2.** Both assertions below live in one test of the seat service. The service
returns `true` from `canAddSeat(7)` when the hold exists, is unexpired, and has room;
internally it calls `repository.findById(7)` once to decide:

```java
assertTrue(service.canAddSeat(7));          // assertion A
verify(repository, times(1)).findById(7);   // assertion B
```

One of these earns its place and one is brittle. Which is which, and what refactor would
break the brittle one while the behaviour stays correct?

**Answer.** A earns its place: it checks the outcome, which is the promise. B is
brittle: it pins an internal detail. A refactor that reads the hold twice (say, a
double-check), or caches it, changes the call count while `canAddSeat` still answers
correctly — B goes red with nothing broken. Verify interactions only when the
interaction is the promise (like a refund); a repository lookup is plumbing.

### Do it yourself — Lesson 6 (second laptop)

**Goal:** verify a refund, verify a never-charge, and watch verify catch a deleted
refund.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/main/java/com/zenith/holds/Money.java`,
   `src/main/java/com/zenith/holds/PaymentGateway.java`, and
   `src/main/java/com/zenith/holds/OrderCancelService.java` with exactly the three main
   classes shown in "The example" of this lesson.

2. Create `src/test/java/com/zenith/holds/L6VerifyTest.java` with exactly the test class
   shown in "The example".

3. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):** the `Argument(s) are different!` block printed by the last test on
purpose (that test passes), and:

```text
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

4. Delete the refund for real. In `OrderCancelService.cancel`, comment out the line
   `payments.refund(orderId);` and rerun `mvn test`.

**Expect (failure):**

```text
[ERROR]   L6VerifyTest.cancel_refundsTheOrder:25
Wanted but not invoked:
paymentGateway.refund(42L);
Actually, there were zero interactions with this mock.
```

Decode: the service no longer refunds, and the verify caught it. Uncomment the line and
rerun to green.

**Done when:** you can say when a `verify` earns its place (the call is the behaviour)
and when it is noise (the value already proves the outcome).

---

## Lesson 7 — `ArgumentCaptor`: catch what your code built, then test the rule (serves S4)

### Definition

An `ArgumentCaptor` catches the object that was passed to a mock, so the test can hold
it in a variable and assert things about it afterwards. `verify` says "the call
happened". The captor hands you *what the call carried*.

### Why it is needed

The service grew a second job — it *builds* a `Hold` and saves it:

```java
public class SeatHoldService {

    private static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final HoldRepository repository;
    private final Clock clock; // asks the clock, never the computer directly

    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Place a new hold. It expires HOLD_TIME from now — that is rule 1. */
    public Hold placeHold(long id, int seats) {
        Hold hold = new Hold(id, seats, clock.instant().plus(HOLD_TIME));
        repository.save(hold);
        return hold;
    }

    /** Rule 2: one more seat only if the hold exists, is not expired, below 10 seats. */
    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false;
        }
        Hold hold = found.get();
        boolean expired = clock.instant().isAfter(hold.expiresAt());
        return !expired && hold.seats() < 10;
    }
}
```

The rule to test: **the saved hold must expire ten minutes after "now"**. The `Hold` is
born inside `placeHold`; the test never touches it. The ladder of claims:

1. `verify(repository).save(any(Hold.class))` — proves only "something was saved".
2. Captor + assert on `expiresAt` — proves **the rule itself**. "This tests a real rule
   rather than the fact that a method was called."

### Key points

- `ArgumentCaptor.forClass(Hold.class)` creates the catcher.
- `captor.capture()` sits where the argument would go, inside a verify:
  `verify(repository).save(captor.capture())`. It matches any `Hold` and pockets it.
- `captor.getValue()` returns the caught object; `getAllValues()` the list, if the
  method was called several times.
- The grab happens during `verify`, so `getValue()` only works after the verify line.

### The example

`src/test/java/com/zenith/holds/L7ArgumentCaptorTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class L7ArgumentCaptorTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final HoldRepository repository = mock(HoldRepository.class);
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void savedHold_expiresTenMinutesFromNow() {
        service.placeHold(7, 4);

        // The catcher, typed to what save() takes.
        ArgumentCaptor<Hold> captor = ArgumentCaptor.forClass(Hold.class);

        // capture() sits where the argument would: "the call happened, and grab what it got".
        verify(repository).save(captor.capture());

        Hold saved = captor.getValue();
        System.out.println("captured: " + saved);

        // The real rule: expiry = the fixed clock's time plus ten minutes.
        assertEquals(NOW.plus(Duration.ofMinutes(10)), saved.expiresAt());
        assertEquals(7, saved.id());
        assertEquals(4, saved.seats());
    }
}
```

Real output:

```text
captured: Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s
[INFO] BUILD SUCCESS
```

And with the rule broken on purpose (service changed to five minutes):

```text
[ERROR] L7ArgumentCaptorTest.savedHold_expiresTenMinutesFromNow:40 expected: <2026-11-17T10:10:00Z> but was: <2026-11-17T10:05:00Z>
```

### Walkthrough

- `service.placeHold(7, 4)` asks the frozen clock (always 10:00), adds ten minutes,
  builds `Hold[id=7, seats=4, expiresAt=10:10]`, and calls `save` on the mock. The mock
  records the call, argument included.
- `verify(repository).save(captor.capture())` matches the recorded `save` and pockets
  the `Hold`.
- `captor.getValue()` returns the exact object the service built.
- `assertEquals(NOW.plus(Duration.ofMinutes(10)), saved.expiresAt())` states the
  ten-minute rule as an assertion.
- Under the five-minute bug, the failure names the rule: expected 10:10, got 10:05. A
  bare `verify(repository).save(any())` stays green under the same bug — a hold *was*
  saved, just a wrong one.

### Important terms

| Term | Meaning |
| --- | --- |
| `ArgumentCaptor.forClass(X.class)` | Creates a catcher for arguments of type X. |
| `captor.capture()` | Placed in the argument slot inside `verify`; matches and pockets the argument. |
| `captor.getValue()` | The caught object (after the verify line). |
| `captor.getAllValues()` | All caught objects, when the call happened several times. |

### Where captors are used

Anywhere code builds something internally and hands it to a boundary: the entity passed
to a repository, the email object passed to a mail sender, the event published to a
queue, the request body sent to another service. The captor turns "we sent *something*"
into "we sent *the right thing*".

### Common mistakes

- **Calling `getValue()` before any `verify(...capture())`.** Nothing is in the pocket
  yet; Mockito fails with "No argument value was captured".
- **Whole-object equality instead of the rule.** Verifying with a fully built expected
  object pins every field; adding one field breaks the test for reasons unrelated to the
  rule, with a blob-style dump. Captor + targeted asserts name the rule.
- **Captor where a plain assert works.** `placeHold` also returns the hold; rules about
  the return value need no captor. The captor earns its place for what crosses a
  boundary — proving what was *saved*.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Captor = catch the argument a mock received; then assert on its fields.
- Three steps: `forClass`, `capture()` inside verify, `getValue()` after.
- Use it to test rules about objects born inside the code under test.
- `save(any())` proves "something was saved"; the captor proves "the right thing".

### Practice questions (with answers)

**Question 1.** Here is an interface and a record:

```java
public record AuditEntry(String who, String action) {
}

public interface AuditLog {
    void record(AuditEntry entry);
}
```

A service method `deleteAccount(String adminName)` builds an `AuditEntry` internally and
calls `auditLog.record(...)` with it. The test holds
`AuditLog auditLog = mock(AuditLog.class)`. The line
`verify(auditLog).record(any(AuditEntry.class));` already passes. What extra claim can
an `ArgumentCaptor<AuditEntry>` prove here that this `verify` cannot — and what two
lines of code produce the captured entry?

**Answer.** The `verify` with `any()` proves only that *some* entry was recorded. The
captor proves what the entry *contains* — for example that `who()` equals the admin name
and `action()` equals `"DELETE_ACCOUNT"`. The two lines:

```java
ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
verify(auditLog).record(captor.capture());
// then: AuditEntry entry = captor.getValue(); and assertEquals on its fields
```

**Question 2.** Using this service (the clock is injected; `HOLD_TIME` is ten minutes):

```java
public Hold placeHold(long id, int seats) {
    Hold hold = new Hold(id, seats, clock.instant().plus(HOLD_TIME));
    repository.save(hold);
    return hold;
}
```

A test freezes the clock at `2026-11-17T09:30:00Z`, calls `placeHold(3, 2)`, captures
the `Hold` passed to `save`, and reads `captor.getValue().expiresAt()`. What exact
`Instant` does it see, and what would it see if someone changed `HOLD_TIME` to five
minutes?

**Answer.** It sees `2026-11-17T09:40:00Z` — the frozen 09:30 plus ten minutes. With
`HOLD_TIME` changed to five minutes it would see `2026-11-17T09:35:00Z`, and an
assertion pinned to 09:40 would fail with
`expected: <...09:40:00Z> but was: <...09:35:00Z>` — the failure names the broken rule.

### Do it yourself — Lesson 7 (second laptop)

**Goal:** capture the hold your code built and assert the ten-minute rule; watch the
test name the rule when it breaks.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Replace `src/main/java/com/zenith/holds/SeatHoldService.java` with exactly the full
   class shown in "Why it is needed" of this lesson (it adds `HOLD_TIME`, the `Duration`
   import, and `placeHold`; `canAddSeat` stays the same).

2. Create `src/test/java/com/zenith/holds/L7ArgumentCaptorTest.java` with exactly the
   test shown in "The example".

3. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):**

```text
captured: Hold[id=7, seats=4, expiresAt=2026-11-17T10:10:00Z]
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

4. Break the rule. In `SeatHoldService`, change `Duration.ofMinutes(10)` to
   `Duration.ofMinutes(5)` and rerun.

**Expect (failure):**

```text
[ERROR] L7ArgumentCaptorTest.savedHold_expiresTenMinutesFromNow:40 expected: <2026-11-17T10:10:00Z> but was: <2026-11-17T10:05:00Z>
```

Decode: the captured hold expired five minutes after the frozen clock, not ten — the
failure names the rule. Change it back to `10` and rerun to green.

**Done when:** you can explain why `verify(repository).save(any())` stays green under
the same bug, and your captor test goes red.

---

## Lesson 8 — Error paths with `thenThrow(...)` (serves S5)

### Definition

`thenThrow` is stubbing's dark twin.
`when(payments.charge(any())).thenThrow(new GatewayTimeoutException())` reads: "When any
charge is attempted, do not answer — blow up with this exception." It teaches the mock
to fail on command.

### Why it is needed

Error-handling paths (`catch` blocks, fallbacks, conversions) are code too — and
untested code in a `catch` block fails for the first time in production. You cannot
order a real payment provider to time out on cue. With a mock, the failure is one line,
instant, and identical on every run. **This is where mocks earn their place.**

### Key points

- Same mechanics as `thenReturn`; an exception is attached instead of an answer.
- The exception is thrown from inside the mock, exactly where the real failure would
  occur, so the service's `try/catch` runs for real.
- `assertThrows(X.class, () -> ...)` demands an `X` and returns it for inspection.
- Void methods cannot go inside `when(...)`; the void form is
  `doThrow(new X()).when(payments).refund(42);` — same idea, reversed word order.
- Convert provider exceptions into domain exceptions at the boundary, and keep the
  original as `getCause()` for debugging.

### The example

Three new main classes:

```java
/** Thrown by the payment provider's side when their service does not answer in time. */
public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException() {
        super("payment provider did not answer within 30 seconds");
    }
}
```

```java
/** Domain exception: OUR word for "payment failed", independent of any provider. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(long orderId, Throwable cause) {
        super("payment for order " + orderId + " failed", cause);
    }
}
```

```java
/**
 * Takes payment for an order. Converts the provider's failure into our own
 * domain exception, so callers never depend on the provider's error types.
 */
public class OrderPayService {

    private final PaymentGateway payments;

    public OrderPayService(PaymentGateway payments) {
        this.payments = payments;
    }

    /** Returns the provider's payment id. */
    public String pay(long orderId, Money amount) {
        try {
            return payments.charge(amount);
        } catch (GatewayTimeoutException e) {
            throw new PaymentFailedException(orderId, e);
        }
    }
}
```

The test, `src/test/java/com/zenith/holds/L8ThenThrowTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class L8ThenThrowTest {

    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final OrderPayService service = new OrderPayService(payments);

    @Test
    void happyPath_returnsProviderPaymentId() {
        when(payments.charge(new Money(4999))).thenReturn("pay_123");

        assertEquals("pay_123", service.pay(42, new Money(4999)));
    }

    @Test
    void gatewayTimeout_becomesOurDomainException() {
        // Force the failure: any charge now times out.
        when(payments.charge(any())).thenThrow(new GatewayTimeoutException());

        PaymentFailedException boom = assertThrows(PaymentFailedException.class,
                () -> service.pay(42, new Money(4999)));

        System.out.println("message: " + boom.getMessage());
        System.out.println("cause:   " + boom.getCause());

        assertEquals("payment for order 42 failed", boom.getMessage());
        assertInstanceOf(GatewayTimeoutException.class, boom.getCause());
    }
}
```

Real output:

```text
message: payment for order 42 failed
cause:   com.zenith.holds.GatewayTimeoutException: payment provider did not answer within 30 seconds
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s
[INFO] BUILD SUCCESS
```

And with the conversion removed on purpose (`throw e;` instead of the domain exception):

```text
[ERROR] L8ThenThrowTest.gatewayTimeout_becomesOurDomainException:33 Unexpected exception type thrown, expected: <com.zenith.holds.PaymentFailedException> but was: <com.zenith.holds.GatewayTimeoutException>
```

### Walkthrough

- **Happy path first.** `charge(new Money(4999))` answers `"pay_123"` (records compare
  by value, so exact-argument matching works on objects). The service passes the id
  through.
- **The forced failure.** Any charge throws → the service's `catch` catches →
  `throw new PaymentFailedException(42, e)` → `assertThrows` catches *our* exception.
- The asserts pin the conversion: our message names the order, and `getCause()` still
  carries the original provider error. Losing the cause makes production debugging
  miserable; the test pins it.
- The broken variant's failure reads: "you promised callers a `PaymentFailedException`,
  but the provider's exception leaked through."

### Important terms

| Term | Meaning |
| --- | --- |
| `thenThrow(e)` | Attach an exception instead of an answer to a taught call. |
| `doThrow(e).when(mock).m(args)` | The same, for void methods. |
| Domain exception | Our own word for a failure, independent of any provider. |
| Cause (`getCause()`) | The original exception carried inside the converted one. |
| Error path | The code that runs when things fail: catch blocks, fallbacks, conversions. |

### Where this is used

Every error path at every boundary: provider timeouts, HTTP 500s from other services,
full disks, broken connections, rate limits. Real failures cannot be summoned on demand;
`thenThrow`/`doThrow` can.

### Common mistakes

- **Testing only the happy path.** The `catch` block is code; untested, it first runs in
  production.
- **Asserting the throw but not the conversion.** `assertThrows(RuntimeException.class, ...)`
  passes for both our exception and the leaked provider one. Assert the exact domain
  type, and check `getCause()`.
- **`thenThrow` with a checked exception the method does not declare.** Mockito refuses:
  `Checked exception is invalid for this method!`.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- `thenThrow` forces failures that reality cannot produce on cue.
- Error paths are the strongest justification for mocks.
- Test the conversion: exact domain type + cause preserved.
- Void variant: `doThrow(...).when(mock).method(...)`.

### Practice questions (with answers)

**Question 1.** Here is a boundary interface, an exception pair, and a service:

```java
public class SdkUploadException extends RuntimeException { }

public class ReportFailedException extends RuntimeException {
    public ReportFailedException(Throwable cause) { super("report upload failed", cause); }
}

public interface ReportUploader {
    String upload(byte[] pdf);
}

public class ReportService {
    private final ReportUploader uploader;

    public ReportService(ReportUploader uploader) {
        this.uploader = uploader;
    }

    public String publish(byte[] pdf) {
        try {
            return uploader.upload(pdf);
        } catch (SdkUploadException e) {
            throw new ReportFailedException(e);
        }
    }
}
```

With `ReportUploader uploader = mock(ReportUploader.class)` and
`ReportService service = new ReportService(uploader)`: write the stubbing line that
forces every upload to fail with `SdkUploadException`, and the assertion line that
proves `publish` converts it to `ReportFailedException`.

**Answer.**

```java
when(uploader.upload(any())).thenThrow(new SdkUploadException());

assertThrows(ReportFailedException.class, () -> service.publish(new byte[]{1, 2, 3}));
```

Even better: catch the returned exception and also assert
`assertInstanceOf(SdkUploadException.class, boom.getCause())`.

**Question 2.** A teammate says: "We do not need `thenThrow`. We can test the timeout
path against the provider's real sandbox server." Give two concrete reasons the mock is
the right tool for this error path, and name the one extra thing (beyond the exception's
type) the conversion test should assert so production debugging stays possible.

**Answer.** First, a real sandbox cannot produce a timeout on command — you would wait
for real 30-second hangs or sabotage the network, making the test slow and flaky.
Second, the sandbox usually answers fine, so the error path would silently never run,
and the test would prove nothing on most days (and it needs network and credentials in
CI). The extra assertion: `getCause()` must carry the original provider exception, so
when this fires in production, the log still shows what actually went wrong underneath.

### Do it yourself — Lesson 8 (second laptop)

**Goal:** force a gateway timeout, prove the conversion to the domain exception, and
watch the test catch a leak.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create these three files in `src/main/java/com/zenith/holds/`, with exactly the
   content shown in "The example": `GatewayTimeoutException.java`,
   `PaymentFailedException.java`, `OrderPayService.java`.

2. Create `src/test/java/com/zenith/holds/L8ThenThrowTest.java` with exactly the test
   shown in "The example".

3. Run:

```bash
cd ~/zenith/mockito-practice/scratch-app
mvn test
```

**Expect (success):**

```text
message: payment for order 42 failed
cause:   com.zenith.holds.GatewayTimeoutException: payment provider did not answer within 30 seconds
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

4. Leak the provider exception on purpose. In `OrderPayService`, change the line
   `throw new PaymentFailedException(orderId, e);` to `throw e;` and rerun.

**Expect (failure):**

```text
[ERROR] L8ThenThrowTest.gatewayTimeout_becomesOurDomainException:33 Unexpected exception type thrown, expected: <com.zenith.holds.PaymentFailedException> but was: <com.zenith.holds.GatewayTimeoutException>
```

Decode: the conversion is gone; the provider's exception leaked to callers, and the test
names both types. Restore the original line and rerun to green.

**Done when:** you can explain why this error path cannot be tested well without a mock,
and what `getCause()` preserves.

---

## Lesson 9 — `UnnecessaryStubbingException`: strict Mockito calls out the lie (serves S8)

### Definition

`UnnecessaryStubbingException` is Mockito, in strict mode, failing a test because a
taught stub was **never used**. It usually means the test does not do what you think.

### Why it is needed

An unused stub means the scenario you set up is not the scenario that ran. Whatever the
assertions checked, they checked some other story — usually one powered by silent
defaults. Plain `mock(...)` never complains about this; strict mode makes the quiet lie
loud, at the moment you write it.

### Key points

- Strict checking comes with Mockito's JUnit plug-in, the `mockito-junit-jupiter`
  dependency (in the pom since Lesson 1).
- `@ExtendWith(MockitoExtension.class)` plugs Mockito into JUnit's lifecycle.
- `@Mock` fields get a fresh mock before each test.
- **After each test** the extension checks every stub was used; unused →
  `UnnecessaryStubbingException` (it fires in `afterEach`, visible in the stack trace).
- Fail-fast sibling: calling a stubbed method with *different arguments* during the test
  → `PotentialStubbingProblem`, immediately, naming both lines.
- Read the exception; do not switch it off with `lenient`.

### The example

The healthy version, `src/test/java/com/zenith/holds/L9StrictStubbingTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class L9StrictStubbingTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private HoldRepository repository; // the extension fills this before each test

    private SeatHoldService service;

    @BeforeEach
    void buildService() {
        service = new SeatHoldService(repository, FIXED_CLOCK);
    }

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(hold)); // taught...

        assertTrue(service.canAddSeat(7)); // ...and used. Strict Mockito is content.
    }
}
```

**Experiment 1 — stub that nobody calls.** Body replaced with:

```java
Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
when(repository.findById(7)).thenReturn(Optional.of(hold)); // taught: findById

service.placeHold(3, 2); // but placeHold only calls save, never findById
```

Real output — the body finished fine, then:

```text
org.mockito.exceptions.misusing.UnnecessaryStubbingException:

Unnecessary stubbings detected.
Clean & maintainable test code requires zero unnecessary code.
Following stubbings are unnecessary (click to navigate to relevant line of code):
  1. -> at com.zenith.holds.L9StrictStubbingTest.stubThatNobodyCalls(L9StrictStubbingTest.java:40)
Please remove unnecessary stubbings or use 'lenient' strictness. More info: javadoc for UnnecessaryStubbingException class.
	at org.mockito.junit.jupiter.MockitoExtension.lambda$afterEach$2(MockitoExtension.java:200)
```

**Experiment 2 — right method, wrong argument.** Stub `findById(7)`, call
`canAddSeat(99)`. Fails *during* the test:

```text
org.mockito.exceptions.misusing.PotentialStubbingProblem:

Strict stubbing argument mismatch. Please check:
 - this invocation of 'findById' method:
    repository.findById(99L);
    -> at com.zenith.holds.SeatHoldService.canAddSeat(SeatHoldService.java:34)
 - has following stubbing(s) with different arguments:
    1. repository.findById(7L);
      -> at com.zenith.holds.L9StrictStubbingTest.thisTestLiesAboutItsScenario(L9StrictStubbingTest.java:40)
Typically, stubbing argument mismatch indicates user mistake when writing tests.
Mockito fails early so that you can debug potential problem easily.
```

### Walkthrough

Decoding `UnnecessaryStubbingException`:

| Message line | Plain meaning |
|---|---|
| `Unnecessary stubbings detected.` | You taught at least one answer nobody asked for. |
| `1. -> at ...L9StrictStubbingTest.java:40` | The exact `when(...)` line that went unused. |
| `Please remove unnecessary stubbings or use 'lenient' strictness.` | Two exits: fix the test, or silence the checker. Read it; do not silence. |
| `at ...MockitoExtension.lambda$afterEach$2` | When it fired: after the test body had already "passed". |

Experiment 1 as a story: the stub says "this test is about a hold that exists under id
7"; the action says "this test places a new hold". Two stories in one test; the passing
assertion proved nothing about either. `PotentialStubbingProblem` is the same disease
caught faster — it names where the code asked (`findById(99L)`) and where you taught
(`findById(7L)`) and says: these disagree.

### Important terms

| Term | Meaning |
| --- | --- |
| `@ExtendWith(MockitoExtension.class)` | Plugs Mockito into JUnit; turns on strict stubbing. |
| `@Mock` | Field filled with a fresh mock before each test. |
| Strict stubbing | Every taught stub must be used by the test that taught it. |
| `UnnecessaryStubbingException` | A stub was never used (reported after the test). |
| `PotentialStubbingProblem` | A stubbed method was called with different arguments (reported during the test). |
| `lenient` | The off-switch for strictness. Resist it. |

### Where this is used

Aging test suites: setup blocks get copy-pasted, code gets refactored, stubs outlive the
calls they once fed. Strict mode keeps every test's setup honest, which is why the
extension is the default style in new projects.

### Common mistakes

- **Reaching for `lenient`.** The message itself offers it — resist. Silencing keeps the
  lying test green forever.
- **Deleting the stub without thinking.** First decide which story the test should tell.
  If the stubbed scenario was the intent, fix the action to use it; only otherwise
  delete the stub.
- **One giant `@BeforeEach` stubbing everything "just in case".** Most tests use only
  some stubs, strict mode complains, teams go lenient everywhere. Stub inside each test,
  only what that scenario needs.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Strict mode (via `MockitoExtension`) checks: every taught stub must be used.
- Unused stub → `UnnecessaryStubbingException`, after the test, naming the line.
- Wrong-argument call → `PotentialStubbingProblem`, during the test, naming both lines.
- Both mean: setup and action tell different stories. Align them; do not go lenient.

### Practice questions (with answers)

**Question 1.** Here is an interface, a service, and a strict-mode test:

```java
public interface PriceSource {
    int priceOf(String sku);
}

public class CartService {
    private final PriceSource prices;

    public CartService(PriceSource prices) {
        this.prices = prices;
    }

    /** Sum of the prices of the items. An empty cart looks nothing up. */
    public int total(List<String> skus) {
        int sum = 0;
        for (String sku : skus) {
            sum += prices.priceOf(sku);
        }
        return sum;
    }
}
```

```java
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private PriceSource prices;

    @Test
    void emptyCart_costsZero() {
        when(prices.priceOf("apple")).thenReturn(30);

        CartService service = new CartService(prices);

        assertEquals(0, service.total(List.of())); // this assertion passes
    }
}
```

What happens when this test runs, *when exactly* does it happen, and what is Mockito
telling you about this test's story?

**Answer.** The body runs and the assertion passes. Then, after the test (in the
extension's `afterEach`), Mockito throws `UnnecessaryStubbingException` pointing at the
`when(prices.priceOf("apple"))` line. It is saying: the setup tells an "apple costs 30"
story, but the action ran the "empty cart" story, which looks nothing up. The stub is a
leftover from a different scenario. The fix: delete the stub (an empty-cart test needs
no teaching) — and if pricing an apple cart was the intent, write that as its own test
that actually puts an apple in the cart.

**Question 2.** A teammate's strict-mode test just failed with
`UnnecessaryStubbingException` pointing at a `when(repository.findById(7))...` line. The
teammate says: "Easy fix — I'll delete that line," and the test goes green. What
question should have been answered before deleting, and in what situation would deleting
the line have been the wrong fix?

**Answer.** The question: *which scenario was this test meant to exercise?* Deleting is
wrong when the stubbed scenario was the intent and the action is what is broken — for
example, the test meant to check "hold 7 exists and has room" but accidentally calls
`canAddSeat(99)`. Deleting the stub leaves a test that only exercises the default
missing-hold path: it goes green while testing nothing it was meant to test. The right
fix there is to change the action to use the stub (`canAddSeat(7)`).

### Do it yourself — Lesson 9 (second laptop)

**Goal:** see both strict-mode exceptions with your own eyes, then end green.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`). The `mockito-junit-jupiter` dependency is
already in your `pom.xml` from the Lesson 1 steps.

1. Create `src/test/java/com/zenith/holds/L9StrictStubbingTest.java` with exactly the
   healthy test class shown in "The example". Run `mvn test` — expect all green
   (`Tests run: 23`).

2. Provoke `UnnecessaryStubbingException`. Replace the test body of
   `holdWithRoom_allowsOneMoreSeat` with:

```java
Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
when(repository.findById(7)).thenReturn(Optional.of(hold));

service.placeHold(3, 2); // never calls findById
```

Run `mvn test`.

**Expect (failure):** `UnnecessaryStubbingException ... Following stubbings are
unnecessary ... at org.mockito.junit.jupiter.MockitoExtension.lambda$afterEach$2` —
note it fired *after* the test body.

3. Provoke `PotentialStubbingProblem`. Change the body to:

```java
Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
when(repository.findById(7)).thenReturn(Optional.of(hold));

assertFalse(service.canAddSeat(99)); // stubbed 7, asking 99
```

(Add `import static org.junit.jupiter.api.Assertions.assertFalse;` if needed.) Run
`mvn test`.

**Expect (failure):** `Strict stubbing argument mismatch` naming both `findById(99L)`
(in the service) and `findById(7L)` (in your test).

4. Restore the healthy version from step 1 and rerun to green.

**Done when:** you can say, for each of the two exceptions, which mismatch it reports
and *when* it fires (after the test vs during it).

---

## Lesson 10 — Over-mocking: watch a green test lie (serves S6)

### Definition

Over-mocking is replacing so much of the system with doubles that the test no longer
exercises anything real. The test still runs, asserts, and shines green — but no line of
your actual business logic is on stage.

### Why it is needed

The failure mode is invisible from the outside: an over-mocked suite looks exactly like
a healthy one. The difference only shows when someone breaks real code and the suite
**stays green**. Subtask S6 makes you watch that happen once, so you never trust "all
green" blindly again.

### Key points — how the trap forms

1. A mock's answers are **your own assumptions, restated**.
2. The assertions check against those same assumptions — the test becomes circular.
3. Mockito can mock **classes**, not just interfaces. Mock your own `SeatHoldService`
   and its real method bodies are thrown away: the ten-minute rule, the save, the expiry
   check — all replaced by canned answers.
4. Now nothing real runs. Change the real logic however you like; the test cannot see it.

Judge a suite by one question: *if a line of business logic disappeared, which test
would notice?* If the answer is "none", the suite is decoration.

### The example

A new web-layer class, `src/main/java/com/zenith/holds/HoldController.java`:

```java
public class HoldController {

    private final SeatHoldService service;

    public HoldController(SeatHoldService service) {
        this.service = service;
    }

    /** POST /holds — returns a short confirmation string for the client. */
    public String placeHold(long id, int seats) {
        Hold hold = service.placeHold(id, seats);
        return "hold " + hold.id() + " for " + hold.seats()
                + " seats, expires " + hold.expiresAt();
    }
}
```

The over-mocked test, `src/test/java/com/zenith/holds/L10OverMockedControllerTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class L10OverMockedControllerTest {

    @Test
    void placeHold_returnsConfirmation() {
        // Mockito can mock classes too, not just interfaces.
        // That power gets misused exactly here: the real placeHold never runs.
        SeatHoldService service = mock(SeatHoldService.class);

        when(service.placeHold(7, 4))
                .thenReturn(new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z")));

        HoldController controller = new HoldController(service);

        String answer = controller.placeHold(7, 4);

        assertEquals("hold 7 for 4 seats, expires 2026-11-17T10:10:00Z", answer);
        verify(service).placeHold(7, 4);
    }
}
```

**The experiment.** Baseline: all green. Then the real service was broken — the save
deleted:

```java
public Hold placeHold(long id, int seats) {
    Hold hold = new Hold(id, seats, clock.instant().plus(HOLD_TIME));
    // repository.save(hold);   <- BROKEN ON PURPOSE: the hold is never stored
    return hold;
}
```

Real output of the full suite with the broken service:

```text
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.holds.L10OverMockedControllerTest
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.holds.L7ArgumentCaptorTest
Wanted but not invoked:
holdRepository.save(
[ERROR] BUILD FAILURE
```

The application is genuinely broken — every "placed" hold evaporates. The over-mocked
controller test: **still green**. The only test that screamed was the captor test, where
the *real* service runs against a mock at a genuine boundary. (Save restored afterwards;
all green again.)

### Walkthrough — what the over-mocked test actually proves

- `when(service.placeHold(7, 4)).thenReturn(hold)` — *you* invent the hold. The real
  `placeHold` never runs.
- `controller.placeHold(7, 4)` — one real thing runs: string gluing.
- `assertEquals(...)` — checks the glue against the hold you invented. Circular.
- `verify(service).placeHold(7, 4)` — checks the controller called the method you just
  stubbed. Also circular.

Total real logic exercised: one string concatenation. A test with five mocks usually
tests that your mocks were configured correctly, which proves nothing about the system.

The captor test caught the break because in it the **real** `placeHold` runs, and the
mock sits only at the true boundary. The claim "save was called with a correctly built
hold" is a claim about real code.

One honest nuance: a focused controller test with a mocked service is legitimate for
pinning routing and response shapes. The trap is a suite where **no test anywhere runs
the real rule**.

### Important terms

| Term | Meaning |
| --- | --- |
| Over-mocking | So much is doubled that no real logic runs in the test. |
| Circular test | Assertions that restate the stubbing — teaching X, then asserting X. |
| Mocking a class you own | Throwing away your own logic's method bodies in the test. |
| Boundary | The right place for a mock: repository, gateway, clock — things with real I/O behind them. |

### Where this bites

Thousand-test suites that "never fail"; refactors that deploy fully green and take
production down. The smells: five mocks per test, `verify` on every interaction, mocks
of your own service classes, tests that only fail on compile errors.

### Common mistakes

- **Mocking what you own.** Mock the boundaries. If a controller test mocks your
  service, some other test must run that service for real.
- **Measuring safety by test count or coverage.** The over-mocked test executes the
  controller lines, so coverage counts them. Executed is not verified.
- **Verifying the stub you just taught.** Proves Mockito works, nothing more.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Over-mocked test = your assumptions on both sides of the assert. Circular, blind.
- The experiment: delete `repository.save(hold)` → over-mocked test green, app broken.
- Only tests that run real logic can defend it (the captor test went red).
- Ask of every suite: "if this line vanished, which test would notice?"

### Practice questions (with answers)

**Question 1** (the subtask's own ask). You deleted the line `repository.save(hold);`
from the real `placeHold` method. The whole test suite ran. The over-mocked controller
test — where `SeatHoldService` is mocked and taught to return a ready-made `Hold` —
passed. The application, meanwhile, loses every hold a customer places. Write down, in
one or two of your own sentences, what that green test result tells you.

**Answer (model).** A green test proves only what it actually runs — this test ran none
of my real logic, so its green said "the mock answered as taught", not "the application
works". A suite is only trustworthy if, for every real rule, at least one test runs that
rule for real.

**Question 2.** The service under test is:

```java
public Hold placeHold(long id, int seats) {
    Hold hold = new Hold(id, seats, clock.instant().plus(Duration.ofMinutes(10)));
    repository.save(hold);
    return hold;
}
```

Three tests exist:

- **Test A:** mocks `SeatHoldService` itself, stubs `placeHold(7, 4)` to return a
  ready-made `Hold`, calls the controller, asserts on the confirmation string.
- **Test B:** builds the real service with a mock repository and a fixed clock, calls
  `placeHold(7, 4)`, captures the `Hold` passed to `save` with an `ArgumentCaptor`,
  asserts its expiry is the clock plus ten minutes.
- **Test C:** builds the real service with a mock repository and a fixed clock, calls
  `placeHold(7, 4)`, and asserts on the **returned** `Hold`'s fields only.

Someone deletes the `repository.save(hold);` line. Which of the three tests goes red?
And what do the other two actually prove?

**Answer.** Only **Test B** goes red — its `verify(repository).save(captor.capture())`
fails with "Wanted but not invoked". Test A proves the controller formats whatever the
service returns — glue only; the real service is not in the test. Test C proves the
built-and-returned hold obeys the expiry rule, which is real and useful — but it says
nothing about persistence, so the save can vanish and C stays green. Each test proves
exactly what it touches, nothing more.

### Do it yourself — Lesson 10 (second laptop)

**Goal:** run the over-mocking experiment with your own hands, and write down the
conclusion.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/main/java/com/zenith/holds/HoldController.java` and
   `src/test/java/com/zenith/holds/L10OverMockedControllerTest.java` with exactly the
   two classes shown in "The example".

2. Run `mvn test`. **Expect:** `Tests run: 24, Failures: 0` and `BUILD SUCCESS`.

3. Break the application. In `SeatHoldService.placeHold`, comment out
   `repository.save(hold);`. Run `mvn test` again.

**Expect (one failure — but not where a beginner expects):**

```text
[INFO] Tests run: 1, Failures: 0 ... -- in com.zenith.holds.L10OverMockedControllerTest   <- still green!
[ERROR] Tests run: 1, Failures: 1 ... <<< FAILURE! -- in com.zenith.holds.L7ArgumentCaptorTest
Wanted but not invoked:
holdRepository.save(
[ERROR] BUILD FAILURE
```

4. Write one sentence in your own notes answering: "the over-mocked test passed while
   the app was broken — what does that tell me?"

5. Restore the `repository.save(hold);` line and rerun to green.

**Done when:** you watched a green test coexist with a broken application, and your
written sentence explains why.

---

## Lesson 11 — Mock vs fake, side by side: the test that catches the broken save (serves S7, S9)

### Definition

A fake is a real, working, lightweight implementation of an interface — the
`FakeHoldRepository` from F7.3, a `HashMap` wearing a repository costume. It is not
taught answers; it *behaves*. This lesson puts the same scenario through a mock and
through the fake, breaks the code, and watches who notices.

### Why it is needed

The over-mocked test stayed green while the application lost every hold. The captor test
caught the break by pinning the interaction ("save must be called"). A stronger claim
exists: **"a hold you place can actually be used afterwards"** — the use case itself. No
mock can host that claim, because a mock has no memory of state. A fake is exactly that
little memory.

### Key points — the side-by-side

| | Mock | Fake |
|---|---|---|
| Where answers come from | You teach them (`when...thenReturn`) | Real behaviour (what was put in comes out) |
| What the test asserts | Interactions (`verify`, captor) | Outcomes (state after the action) |
| Setup per scenario | One stub per taught answer | Just put data in (or don't) |
| Catches a deleted `save`? | Only if you verified `save` specifically | Yes — the later read finds nothing |
| Right place | **Boundaries and error paths** | **Use-case tests** |

**The preference rule to carry forward:** fakes for use-case tests; mocks for boundaries
and error paths. Default to the fake when testing what a feature does. Reach for a mock
when the interaction is the promise (refund), when you must inspect what crossed a
boundary (captor), and when you must force failures reality will not produce on cue
(`thenThrow`).

### The example

The fake, in the **test** folder (it is test tooling, not production code):

```java
public class FakeHoldRepository implements HoldRepository {

    private final Map<Long, Hold> holdsById = new HashMap<>();

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.ofNullable(holdsById.get(id)); // empty box if nothing stored
    }

    @Override
    public void save(Hold hold) {
        holdsById.put(hold.id(), hold); // genuinely stores; same id overwrites
    }

    @Override
    public void deleteById(long id) {
        holdsById.remove(id); // genuinely gone afterwards
    }
}
```

The tests, `src/test/java/com/zenith/holds/L11FakeVsMockTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class L11FakeVsMockTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeHoldRepository repository = new FakeHoldRepository();
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void placedHold_canTakeAnotherSeat() {
        service.placeHold(7, 4);

        // The whole use case through real logic and real storage behaviour:
        // placeHold stored it, canAddSeat finds it, clock says not expired, 4 < 10.
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void controllerWithRealServiceAndFake_provesTheUseCase() {
        // The S9 rewrite: same controller as the over-mocked test,
        // but the real service and the fake sit underneath.
        HoldController controller = new HoldController(service);

        String answer = controller.placeHold(7, 4);

        assertEquals("hold 7 for 4 seats, expires 2026-11-17T10:10:00Z", answer);
        assertTrue(repository.findById(7).isPresent()); // genuinely stored
    }
}
```

Baseline: all 26 tests green. With `repository.save(hold);` deleted from the real
service, the full suite said:

```text
[INFO] ... Failures: 0 ... -- in com.zenith.holds.L10OverMockedControllerTest   <- still green
[ERROR] L11FakeVsMockTest.placedHold_canTakeAnotherSeat:30 expected: <true> but was: <false>
[ERROR] L11FakeVsMockTest.controllerWithRealServiceAndFake_provesTheUseCase:42 expected: <true> but was: <false>
[ERROR] ... Wanted but not invoked: holdRepository.save( ...   <- the captor test
[ERROR] Tests run: 26, Failures: 3, Errors: 0, Skipped: 0
```

Scoreboard under one real bug: over-mocked test green; captor test red; both fake tests
red. (Save restored; 26 green again.)

### Walkthrough

- **Test 1 — the use case.** `placeHold(7, 4)` runs the real rule: expiry = frozen 10:00
  plus ten minutes, saved into the `HashMap`. `canAddSeat(7)` reads it back: found, not
  expired, 4 < 10 → `true`. Zero stubbing, zero verifying. With the save deleted,
  `findById(7)` found an empty map and the test failed. **The fake has real behaviour,
  so removing the save makes it fail** — subtask S7, witnessed.
- **Test 2 — subtask S9, delivered.** The 90%-mocks test was Lesson 10's
  `L10OverMockedControllerTest`. What it actually proves: Mockito returns what you teach
  it, and the controller glues strings. The rewrite keeps the same controller but puts
  the real service and the fake underneath, asserting the confirmation string *and* that
  the hold is genuinely stored. Under the deleted save, the rewrite went red; the
  original stayed green.
- **Why the fake test beats even the captor test for use cases:** the captor pins *how*
  (save called, once, with these contents); the fake pins *what* (afterwards, the hold
  is usable). If the service someday saves differently (two steps, another method on the
  interface), the captor test breaks with behaviour intact; the fake test keeps passing.
  Outcome claims survive refactors; interaction claims do not.

### Important terms

| Term | Meaning |
| --- | --- |
| Fake | A real, working, lightweight implementation used in tests (HashMap repository). |
| Use-case test | A test of what the feature does, asserted on outcomes. |
| Outcome claim | "Afterwards, X is true of the system" — survives refactors. |
| Interaction claim | "This exact call happened" — breaks when the *how* changes. |

### Where this is used

Mature teams keep exactly this split: a small set of maintained fakes (in-memory
repositories, fake clocks, fake queues) powering fast use-case tests, and Mockito at the
edges for side effects and failures. H2 and Testcontainers (from F5/F6) are "fakes" of
increasing realism for the database boundary.

### Common mistakes

- **A fake that drifts from the real contract.** If the real repository overwrites on
  duplicate ids, the fake must too. A lying fake is an over-mock with extra steps.
- **Using a fake for error paths.** You cannot make a `HashMap` time out. Forcing
  failures is mock territory (`thenThrow`).
- **Sharing one fake across tests.** State leaks between tests. Fresh fake per test.

### Your doubts

No doubts raised for this lesson.

### Summary for revision

- Fake = behaves; mock = taught. Fake asserts outcomes; mock asserts interactions.
- Under the deleted save: fake tests red, captor red, over-mocked green.
- **Fakes for use-case tests. Mocks for boundaries and error paths.**
- The 90%-mocks test proves mock configuration; the fake rewrite proves the system.

### Practice questions (with answers)

**Question 1** (this is also checkpoint question 2). Give one concrete situation where a
**mock** is clearly the right tool, and one where a **fake** is. For each, one sentence
on why the other tool would be worse there.

**Answer.** Mock: testing that cancelling an order refunds exactly once and never
charges (`verify(payments).refund(42)`, `verify(payments, never()).charge(any())`) — the
interaction is the whole behaviour, and no readable state exists; a fake payment system
would be a big build and still could not express "never charged" as directly. Also any
error path: `when(payments.charge(any())).thenThrow(...)` — a fake cannot fail on cue.
Fake: the use-case test "place a hold, then a seat can be added" — with a mock you would
have to stub `findById` to return whatever `save` received, which restates the service's
own logic inside the test; the fake just behaves, and the test asserts the outcome.

**Question 2.** A team is building `WalletService` with two dependencies:
`WalletRepository` (`Optional<Wallet> findById(long id)`, `void save(Wallet w)`) and
`BankGateway` (`String transfer(Money amount)`, which really moves money). Four tests
are planned:

1. "Topping up a wallet increases the balance you can read back afterwards."
2. "When the bank gateway throws `BankDownException`, the service converts it to our
   `TopUpFailedException`."
3. "Cancelling a top-up calls `transfer` back to the customer exactly once, and never
   charges."
4. "The `Wallet` object passed to `save` carries the new balance, computed as old
   balance plus top-up."

For each: mock or fake (and which Mockito tool, where one applies)?

**Answer.**

1. **Fake** repository — a use-case outcome ("read back afterwards"); store, act, read.
2. **Mock** gateway with `when(gateway.transfer(any())).thenThrow(new BankDownException())`
   and `assertThrows(TopUpFailedException.class, ...)` — an error path; nothing real
   fails on cue.
3. **Mock** gateway with `verify(gateway).transfer(...)` and
   `verify(gateway, never())...` — the interaction is the behaviour; money movement has
   no readable state in our system.
4. **Mock** repository with an `ArgumentCaptor<Wallet>` on `save`, asserting the
   captured balance — inspecting what the code built. (A fake plus read-back also proves
   this and survives refactors; both are sound, the captor is the most direct tool.)

### Do it yourself — Lesson 11 (second laptop)

**Goal:** watch the fake catch the bug the over-mocked test missed, and record the
preference rule.

This continues in the project from the earlier "Do it yourself" steps (folder
`~/zenith/mockito-practice/scratch-app`).

1. Create `src/test/java/com/zenith/holds/FakeHoldRepository.java` and
   `src/test/java/com/zenith/holds/L11FakeVsMockTest.java` with exactly the two classes
   shown in "The example". (The fake lives in the **test** folder — it is test tooling.)

2. Run `mvn test`. **Expect:** `Tests run: 26, Failures: 0` and `BUILD SUCCESS`.

3. Break the save again: in `SeatHoldService.placeHold`, comment out
   `repository.save(hold);`. Run `mvn test`.

**Expect (three failures, one silence):**

```text
[INFO] ... Failures: 0 ... -- in com.zenith.holds.L10OverMockedControllerTest   <- the over-mocked test: green
[ERROR] L11FakeVsMockTest.placedHold_canTakeAnotherSeat:30 expected: <true> but was: <false>
[ERROR] L11FakeVsMockTest.controllerWithRealServiceAndFake_provesTheUseCase:42 expected: <true> but was: <false>
[ERROR] ... Wanted but not invoked: holdRepository.save( ...   <- the captor test
[ERROR] BUILD FAILURE
```

4. In your notes, record the preference rule in your own words: fakes for use-case
   tests, mocks for boundaries and error paths.

5. Restore `repository.save(hold);` and rerun to green (`Tests run: 26`).

**Done when:** you can explain why the fake test failed without any `verify` in it, and
your notes contain the preference rule.

---

## Checkpoint — the task's four questions (with full answers)

**Question 1. What does `ArgumentCaptor` let you assert that `verify` alone cannot?**

`verify` proves that a call happened: which method, how many times, and — if you already
know them — with which arguments. `ArgumentCaptor` hands you the **actual object the
mock received**, so you can assert on its contents. That matters when the object is
built *inside* the code under test, where the test cannot construct the expected value
up front and compare whole objects sensibly. In this task:
`verify(repository).save(any())` could only prove "something was saved". The captor
proved the rule itself — the saved hold's `expiresAt` equals the frozen clock plus ten
minutes — and when the rule broke, the failure named it precisely:
`expected: <2026-11-17T10:10:00Z> but was: <2026-11-17T10:05:00Z>`.

**Question 2. One situation where a mock is clearly the right tool, and one where a
fake is.**

Mock: when the interaction is the behaviour, or on an error path. Cancelling an order
must refund once and never charge — `verify(payments).refund(42)` and
`verify(payments, never()).charge(any())`; `refund` returns nothing, so there is no
state to read, and only a mock can express "never charged". Likewise
`when(payments.charge(any())).thenThrow(new GatewayTimeoutException())` — no real or
fake system fails on cue. Fake: the use-case test "place a hold, then a seat can be
added". The fake repository genuinely stores, so real logic runs end to end and the test
asserts the outcome. A mock there would force the test to restate the service's own
logic in stubs — and it stayed green when the save was deleted, while the fake test
went red.

**Question 3. You deleted a line of real logic and every test still passed. What does
that tell you?**

That no test in the suite actually runs that line. A green test proves only what it
exercises. The suite is over-mocked around that logic (every test that touches the flow
replaces the real class with a double) or the branch is simply untested — either way,
the green is decoration, not protection. The fix is at least one test where the real
rule runs: a fake-based use-case test, or a boundary test with a captor or verify. The
question to ask of any suite: "if this line vanished, which test would notice?"

**Question 4. What is `UnnecessaryStubbingException` telling you?**

Strict Mockito is saying: you taught a stub (`when(...).thenReturn(...)`) that the
test's code path never used. That usually means the test does not do what you think —
the setup tells one story and the action runs another, so whatever the assertions
checked, it was not the scenario you set up. Before touching anything, decide which
scenario the test was *meant* to exercise. If the stubbed scenario was the intent, fix
the action so it uses the stub (the id or argument is usually wrong). If the acted
scenario was the intent, delete the stub. Do not switch strictness to `lenient` — that
just keeps the lying test green.

---

## Task wrap-up — the "done when" review

> **Done when:** you have one fast mock-based unit test including an `ArgumentCaptor`
> and an error path, and you have personally watched an over-mocked test pass against
> broken code.

All met, with evidence:

- **Fast mock-based unit test:** `L4SeatHoldServiceUnitTest` — real service built with
  plain `new`, mock repository, fixed clock; 4 tests in **12 ms** (`Time elapsed: 0.012 s`).
- **`ArgumentCaptor`:** `L7ArgumentCaptorTest` captures the `Hold` passed to `save` and
  asserts expiry = fixed clock + ten minutes; breaking the rule produced
  `expected: <...10:10:00Z> but was: <...10:05:00Z>`.
- **Error path:** `L8ThenThrowTest` forces `GatewayTimeoutException` via `thenThrow` and
  asserts the conversion to `PaymentFailedException`, message and cause both pinned.
- **Over-mocked test passing against broken code — watched twice:** with
  `repository.save(hold)` deleted from the real service,
  `L10OverMockedControllerTest` stayed **green** while `L7ArgumentCaptorTest`
  (`Wanted but not invoked: holdRepository.save(`) and both `L11FakeVsMockTest` tests
  (`expected: <true> but was: <false>`) went red.

Final suite state: **26 tests, 0 failures**, total test execution under one second
(most of that is one-time mock-engine warm-up in the first test class; every later class
runs in single-digit milliseconds).

Subtask map: S1 (Lesson 4), S2 (Lesson 3), S3 (Lesson 6), S4 (Lesson 7), S5 (Lesson 8),
S6 (Lesson 10), S7 (Lesson 11), S8 (Lesson 9), S9 (Lesson 11, test 2). Checkpoint:
above.

**The rule this task exists to teach, once more:** fakes for use-case tests; mocks for
boundaries and error paths. A test with five mocks usually tests that your mocks were
configured correctly — judge every suite by the question "if a line of real logic
disappeared, which test would notice?"

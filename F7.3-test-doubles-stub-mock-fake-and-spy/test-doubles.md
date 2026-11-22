# F7.3 — Test doubles: stub, mock, fake, and spy

**Goal of the task.** Define all four kinds of test double in one sentence each. Pick the
right one for three given ticketing situations, with reasons. Explain why people say
"mock" for all four, and why the precise words still matter.

**Done when:** you have hand-written a stub, a mock, a fake, and a spy for
`HoldRepository`, kept the in-memory fake for later phases, and can justify your choice
of double for all three situations.

**You need first:** Task F7.1 (your first test, JUnit), interfaces (Module F2),
constructor injection (Task F6.2). Every lesson below re-explains what it needs, in place.

**The story in two sentences.** A test double is a stand-in object you plug into the
class under test, instead of a slow, unpredictable, or missing real dependency. There are
four kinds — stub, mock, fake, spy — and knowing which one you are holding tells you what
your test should assert.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Dependency** | Another object a class needs to do its job. |
| **Test double** | The umbrella word for anything standing in for a real dependency in a test. From film: a stunt double stands in for an actor. Nothing to do with the number type `double`. |
| **Canned answer** | An answer decided in advance, the same every time. |
| **Stub** | A double that returns canned answers. It records nothing. |
| **Mock** | A stub that also remembers how it was called, so the test can check the calls. |
| **Fake** | A real, working implementation that is lightweight. An in-memory `Map` behind a repository interface. It has behaviour. |
| **Spy** | A wrapper around a real object. It passes calls through, but records them. |
| **State testing** | Asserting on resulting values. Usually the better choice. |
| **Interaction testing** | Asserting on calls that were made, rather than on the result. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Hand-write four stand-ins for `interface HoldRepository`: a stub, a mock, a fake, and a spy. No library. |
| S2 | Keep the fake. The in-memory repository gets reused later for use-case tests. |
| S3 | Pick the right stand-in for three ticketing situations, and justify each pick. |
| S4 | Write the rule that prevents bad tests: mock at boundaries you own. |
| S5 | Note why people say "mock" for all four kinds, and why the distinction still matters. Includes the ask-AI exercise: all four doubles in one test class. |
| CP | Checkpoint: four questions, answered out loud. |

## Roadmap for this task — 9 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | What a test double is | The umbrella word, the pain it removes, and the swap trick that makes it possible. | groundwork for S1 |
| 2 | The stub | A stand-in that returns fixed, pre-decided answers and records nothing. | S1 |
| 3 | The mock | A stub that also remembers how it was called, so a test can check the calls. | S1 |
| 4 | State testing vs interaction testing | The two things a test can assert on, and which one to prefer. | S3, CP |
| 5 | The fake | A small but genuinely working implementation: a `HashMap` repository. The keeper. | S1, S2 |
| 6 | The spy | A wrapper around the real thing that forwards every call and records it. | S1 |
| 7 | Choosing a double | Apply the four kinds to the three ticketing situations. | S3 |
| 8 | The boundary rule | Where mocking is safe, and the three things you must never mock. | S4 |
| 9 | Why everyone says "mock" | The loose vocabulary, Mockito's role, and all four doubles in one test class. | S5 |

The scratch project for this task lives in
`F7.3-test-doubles-stub-mock-fake-and-spy/scratch-app`. It is plain Java 21 plus JUnit,
no Spring. Run everything with `mvn test` from inside `scratch-app`.

---

## Lesson 1 — What a test double is (serves S1 groundwork)

### Definition

A **dependency** of a class is another object that class needs to do its job. A **test
double** is an object you write yourself, which stands in for a real dependency while a
test runs. The name comes from film: a stunt double stands in for an actor in dangerous
scenes. It has nothing to do with the number type `double`.

### Why it is needed

Our ticketing app has a `SeatHoldService`. Its job: decide if a customer's hold can take
one more seat. To decide, it must look up the hold. So its dependency is a
`HoldRepository` — the thing that stores and finds holds. The real repository talks to a
database over the network. Testing against the real one hurts three ways:

1. **Slow.** One lookup costs around 1.5 seconds. A real project has hundreds of tests.
   Hundreds of tests times 1.5 seconds is many minutes per run. Slow tests stop getting
   run. Tests that do not run catch nothing.
2. **Heavy setup.** The test only passes if a database server is running, and only if it
   holds exactly the right rows. On a fresh laptop it fails before it starts.
3. **Sometimes the real thing is not available at all.** A payment gateway that charges
   real money. A service another team has not built yet.

Measured on this machine, the same two checks ran in **3.012 seconds** against the
(pretend) real repository and **0.024 seconds** against a hand-written stand-in. That is
about 125 times faster, with nothing installed.

### Key points

- A test double is any stand-in for a real dependency inside a test.
- Doubles exist because real dependencies are slow, unpredictable, costly, or missing.
- The swap needs a socket: an interface plus constructor injection.
- The class under test cannot tell the double from the real thing. It only calls the
  interface's methods.
- Stub, mock, fake, and spy are four kinds of test double. Four ways to fill the same
  socket.

### How the swap works, step by step

1. **The need is named as an interface.** An interface is a list of method promises
   without bodies. `HoldRepository` promises three methods: `findById`, `save`,
   `deleteById`. Any class that implements all three counts as a `HoldRepository`.
2. **The dependency arrives through the constructor.** This is constructor injection.
   The service stores what it was given in a field typed as the interface. The service
   never writes `new DatabaseHoldRepository()` inside itself. If it did, the choice would
   be locked inside the class, and no test could change it.
3. **The test passes in its own small class** that implements the same interface. The
   service cannot tell the difference.

Point 3 is the whole trick. Everything in this task is a variation of point 3.

### The example — the pain and the cure

Six small files. First the data. A record is a tiny class that only carries data.

`src/main/java/com/zenith/doubles/Hold.java`:

```java
package com.zenith.doubles;

/**
 * A seat hold: a customer is holding some seats for an event,
 * but has not paid yet. A record is a small class that only carries data.
 */
public record Hold(long id, int seats) {
}
```

The named need. `Optional<Hold>` is a box that either holds one `Hold` or is empty.

`src/main/java/com/zenith/doubles/HoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * The service's named need: "I need something that can store and find holds."
 * Any class that implements these three methods satisfies the need.
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

The class under test. The field type is the interface, and the constructor is the socket.

`src/main/java/com/zenith/doubles/SeatHoldService.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * The class under test. Rule: a hold may take one more seat
 * only while it has fewer than 10 seats.
 */
public class SeatHoldService {

    private final HoldRepository repository;

    /** Constructor injection: the repository comes in through the constructor. */
    public SeatHoldService(HoldRepository repository) {
        this.repository = repository;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        return found.get().seats() < 10;
    }
}
```

The painful dependency. A truly real one would run SQL against a running database server.
This class pretends: it pauses 1.5 seconds where the network call would be. A real one is
worse — with no server running it does not get slow, it dies.

`src/main/java/com/zenith/doubles/DatabaseHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * Pretends to be the real repository that talks to a database.
 * In a real app, every method here would send SQL over the network.
 * We fake the cost with a pause, so you can feel it without installing a database.
 * A real one would be worse: it would fail completely if no database is running.
 */
public class DatabaseHoldRepository implements HoldRepository {

    public DatabaseHoldRepository() {
        System.out.println("Opening connection to the ticket database...");
    }

    @Override
    public Optional<Hold> findById(long id) {
        pretendNetworkCall(); // in a real app: SELECT ... WHERE id = ?
        if (id == 7) {
            return Optional.of(new Hold(7, 4));
        }
        if (id == 8) {
            return Optional.of(new Hold(8, 10));
        }
        return Optional.empty();
    }

    @Override
    public void save(Hold hold) {
        pretendNetworkCall(); // in a real app: INSERT or UPDATE
    }

    @Override
    public void deleteById(long id) {
        pretendNetworkCall(); // in a real app: DELETE ... WHERE id = ?
    }

    private void pretendNetworkCall() {
        try {
            Thread.sleep(1500); // pretend the network round trip takes 1.5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

A test that uses the painful dependency. JUnit reminders: `@Test` marks a method that
JUnit runs as one test. `assertTrue(x)` passes the test only if `x` is true.
`assertFalse(x)` passes only if `x` is false.

`src/test/java/com/zenith/doubles/L1DatabaseSpeedTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 1, the painful way: the service talks to the (pretend) real database.
 * Every findById costs 1.5 seconds.
 */
class L1DatabaseSpeedTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        SeatHoldService service = new SeatHoldService(new DatabaseHoldRepository());
        assertTrue(service.canAddSeat(7)); // hold 7 has 4 seats in the database
    }

    @Test
    void holdWithTenSeatsIsFull() {
        SeatHoldService service = new SeatHoldService(new DatabaseHoldRepository());
        assertFalse(service.canAddSeat(8)); // hold 8 has 10 seats in the database
    }
}
```

The cure: a test double, written by hand.

`src/main/java/com/zenith/doubles/StandInHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * A test double: a hand-written stand-in for the real repository.
 * You give it one hold when you build it.
 * It answers every findById with that hold, instantly.
 */
public class StandInHoldRepository implements HoldRepository {

    private final Hold theOnlyHold;

    public StandInHoldRepository(Hold theOnlyHold) {
        this.theOnlyHold = theOnlyHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.of(theOnlyHold);
    }

    @Override
    public void save(Hold hold) {
        // do nothing: no test in this lesson needs save
    }

    @Override
    public void deleteById(long id) {
        // do nothing: no test in this lesson needs delete
    }
}
```

The same two checks, through the stand-in.

`src/test/java/com/zenith/doubles/L1StandInSpeedTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 1, the fast way: the service talks to a hand-written stand-in.
 * Same service, same rule, same checks. No database, no waiting.
 */
class L1StandInSpeedTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        HoldRepository standIn = new StandInHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(standIn);
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void holdWithTenSeatsIsFull() {
        HoldRepository standIn = new StandInHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(standIn);
        assertFalse(service.canAddSeat(8));
    }
}
```

Real output from `mvn test` (trimmed to the important lines):

```text
[INFO] Running com.zenith.doubles.L1StandInSpeedTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.024 s -- in com.zenith.doubles.L1StandInSpeedTest
[INFO] Running com.zenith.doubles.L1DatabaseSpeedTest
Opening connection to the ticket database...
Opening connection to the ticket database...
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.012 s -- in com.zenith.doubles.L1DatabaseSpeedTest
[INFO] Results:
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Same service, same rule, same four passing checks. 3.012 seconds against 0.024 seconds.

Why does "Opening connection to the ticket database..." print twice? JUnit builds a fresh
test-class object for every test method. Two test methods, two services, two pretend
connections. That doubles the pain of a slow dependency, which is exactly the point.

### Walkthrough — the fast test, step by step

Trace `holdWithFourSeatsCanTakeOneMore` in `L1StandInSpeedTest`:

1. `new Hold(7, 4)` builds a hold with id 7 and 4 seats. It lives only in memory.
2. `new StandInHoldRepository(new Hold(7, 4))` builds the stand-in. The stand-in stores
   that one hold in its `theOnlyHold` field.
3. `new SeatHoldService(standIn)` is the injection moment. The service's `repository`
   field now points at the stand-in. The field's type is `HoldRepository`, the stand-in
   implements `HoldRepository`, so the compiler is satisfied. The service has no way to
   know it did not get the database version.
4. `service.canAddSeat(7)` runs. Inside, `repository.findById(7)` executes the
   **stand-in's** `findById`. It returns `Optional.of(theOnlyHold)` instantly.
5. Back in `canAddSeat`: `found.isEmpty()` is false, so we reach
   `found.get().seats() < 10`. That is `4 < 10`, which is `true`.
6. `assertTrue(true)` passes.

The slow twin test runs the same six steps. Only step 4 differs: the database version's
`findById` pauses 1.5 seconds first. That one difference is the whole 125x.

### Important terms

| Term | Meaning |
| --- | --- |
| Dependency | Another object a class needs to do its job. |
| Test double | A hand-made stand-in for a real dependency, used in a test. |
| Interface | A list of method promises without bodies. Any class implementing all of them counts as that type. |
| Constructor injection | The dependency arrives as a constructor parameter and is stored in a field. |
| Record | A tiny class that only carries data, like `Hold(long id, int seats)`. |
| `Optional<T>` | A box that either holds one value or is empty. `isEmpty()` asks; `get()` opens it. |
| `@Test` | Marks a method JUnit runs as one test. |
| `assertTrue(x)` / `assertFalse(x)` | The test passes only if `x` is true / false. |

### Where doubles are used in practice

- Database repositories (this lesson).
- Payment gateways — you cannot charge a real card in a test.
- Email and SMS senders — you cannot spam real people.
- Clocks — a stand-in clock lets you test "what happens at midnight" at noon.
- Random pickers — a stand-in makes "random" predictable, so the test knows the expected answer.
- Web services owned by other teams — theirs may be down, slow, or not built yet.

Good to know:

- A real database repository without a running server does not get slow. It throws. A
  double removes that whole class of breakage from unit tests.
- The stand-in may ignore its arguments (ours ignores `id`). The test controls the whole
  world, so this is fine.

### Common mistakes

- **Doubling something you could just build.** `new Hold(7, 4)` is instant and real. It
  never needs a stand-in. Doubles are only for the slow, unpredictable, costly, or
  missing.
- **Making the stand-in complete.** It only needs to serve what the test touches. The
  empty `save` and `deleteById` here are correct, not lazy.
- **Creating the dependency inside the class.** If `SeatHoldService` contained
  `new DatabaseHoldRepository()`, no test could swap it. The constructor parameter is the
  socket; without it there is nowhere to plug in.

### Link to the subtask

Groundwork for S1: "hand-write a stub, a mock, a fake, and a spy for
`interface HoldRepository`". This lesson built that exact interface, a service to
exercise it, and proof that the swap works. The four names are four jobs a stand-in can
do.

### Your doubts

None raised for this lesson.

### Summary for revision

- Test double = hand-made stand-in for a real dependency in a test.
- Real dependencies hurt tests three ways: slow, heavy setup, sometimes unavailable.
- The swap socket = interface + constructor injection.
- The class under test cannot tell the double from the real thing.
- Never double cheap data objects like `Hold`; just build them.
- Measured here: 3.012 s with the (pretend) database vs 0.024 s with the stand-in.

### Practice questions (with answers)

**Question 1.** Here is a complete class. `GmailClient` is a class (not an interface)
that sends real email and needs the internet:

```java
public class ReminderService {

    private final GmailClient gmail = new GmailClient("smtp.gmail.com", 465);

    public void remind(String customerEmail) {
        gmail.send(customerEmail, "Your seats expire in 10 minutes!");
    }
}
```

(a) A teammate wrote a perfectly good stand-in class for sending email. Why can no test
plug it into `ReminderService` as the class stands? (b) Which two changes to
`ReminderService` would fix that?

**Answer.** (a) Two locks are in the way. First, the field is created inside the class
with `new`, so the choice of object is made internally and nothing outside can change
it. Second, the field's type is the concrete class `GmailClient`, so even a willing
caller could only supply a `GmailClient`, not a stand-in. (b) Fix one: define an
interface, for example `MessageSender` with a `send(String to, String text)` method, and
make the field that type. Fix two: accept the `MessageSender` as a constructor
parameter instead of building it inside. After both changes, a test passes in its own
`MessageSender`, and production code passes in the real Gmail one:

```java
public interface MessageSender {
    void send(String to, String text);
}

public class ReminderService {

    private final MessageSender sender;

    public ReminderService(MessageSender sender) {
        this.sender = sender;
    }

    public void remind(String customerEmail) {
        sender.send(customerEmail, "Your seats expire in 10 minutes!");
    }
}
```

**Question 2.** Here is a complete set of three types and one test line:

```java
public interface Clock {
    int currentHour(); // returns 0 to 23
}

public class BoxOffice {
    private final Clock clock;

    public BoxOffice(Clock clock) {
        this.clock = clock;
    }

    public boolean isOpen() {
        int hour = clock.currentHour();
        return hour >= 9 && hour < 17;
    }
}

public class FixedClock implements Clock {
    @Override
    public int currentHour() {
        return 21;
    }
}
```

The test runs: `boolean result = new BoxOffice(new FixedClock()).isOpen();` —
(a) What is `result`? Walk through why. (b) Suppose the test instead used a real clock
that reads the laptop's actual time. Name one concrete way that test goes wrong.

**Answer.** (a) `result` is `false`. The box office asks the clock for the hour. The
stand-in clock always answers 21. Then `21 >= 9` is true, but `21 < 17` is false, and
`true && false` is false. (b) With a real clock the answer depends on when you run the
test. A test asserting `isOpen()` is true passes at 2 in the afternoon and fails at 9 in
the evening. The same code gives different test results on different runs, so a red run
no longer means the code is wrong. A stand-in clock makes the test's world stand still.

### Do it yourself — Lesson 1 (second laptop, nothing installed)

**Goal:** run the slow test and the fast test yourself, and read one failure message.

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
mkdir -p ~/zenith/test-doubles/scratch-app/src/main/java/com/zenith/doubles
mkdir -p ~/zenith/test-doubles/scratch-app/src/test/java/com/zenith/doubles
cd ~/zenith/test-doubles/scratch-app
```

3. Create `pom.xml` in `scratch-app` with exactly this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.zenith</groupId>
    <artifactId>test-doubles-scratch</artifactId>
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

4. Create the seven Java files exactly as shown in this lesson's example section. Five go
   in `src/main/java/com/zenith/doubles/`: `Hold.java`, `HoldRepository.java`,
   `SeatHoldService.java`, `DatabaseHoldRepository.java`, `StandInHoldRepository.java`.
   Two go in `src/test/java/com/zenith/doubles/`: `L1DatabaseSpeedTest.java`,
   `L1StandInSpeedTest.java`.

5. Run the tests. The first run downloads libraries, so it needs internet and a minute of
   patience:

```bash
mvn test
```

Expected near the end (success):

```text
[INFO] Running com.zenith.doubles.L1StandInSpeedTest
[INFO] Tests run: 2, Failures: 0, ... Time elapsed: 0.024 s -- in com.zenith.doubles.L1StandInSpeedTest
[INFO] Running com.zenith.doubles.L1DatabaseSpeedTest
Opening connection to the ticket database...
Opening connection to the ticket database...
[INFO] Tests run: 2, Failures: 0, ... Time elapsed: 3.012 s -- in com.zenith.doubles.L1DatabaseSpeedTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

6. Break it on purpose, to learn the failure shape. In `L1StandInSpeedTest.java`, change
   the line `assertTrue(service.canAddSeat(7));` to
   `assertFalse(service.canAddSeat(7));`. Run `mvn test` again. Expected (failure):

```text
[ERROR] L1StandInSpeedTest.holdWithFourSeatsCanTakeOneMore:18 expected: <false> but was: <true>
[ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

Decoding that message:

| Piece | Meaning |
| --- | --- |
| `L1StandInSpeedTest.holdWithFourSeatsCanTakeOneMore:18` | Which test failed, and the line number of the failed check. |
| `expected: <false>` | What the check demanded. |
| `but was: <true>` | What the code actually returned. The service said true; our broken check demanded false. |
| `BUILD FAILURE` | One failing test fails the whole run. |

7. Change the line back to `assertTrue(service.canAddSeat(7));` and run `mvn test` once
   more.

**Done when:** `mvn test` prints `Tests run: 4, Failures: 0`, the stand-in test class
shows a time under 0.1 s, and the database test class shows about 3 s.

---

## Lesson 2 — The stub (serves S1)

### Definition

A **canned answer** is an answer decided in advance, the same every time. A **stub** is a
test double that returns canned answers and records nothing. It has no memory. Its only
job is to feed the class under test the data the test needs.

### Why it is needed

1. **The class under test needs data before its logic can start.** `SeatHoldService`
   cannot decide anything until `findById` hands it a hold. Getting that data for real
   means a running database with the right rows. A stub hands it over in zero steps.
2. **Some situations are hard to stage for real.** "The hold does not exist" needs a
   guaranteed-absent row. A declined card needs a bank. Midnight needs midnight. With a
   stub, the awkward situation is one line.

Without stubs, only the easy happy paths get tested. Bugs live in the other paths.

### Key points

- Stub = canned answers + no memory.
- The stand-in from Lesson 1 was already a stub; only the name was missing.
- Stubs may ignore their arguments. The test controls the whole world.
- Unused methods get empty bodies.
- Asserts point at the service's answer, never at the stub.
- Stubs are cheap. Write one tiny stub per situation.
- Doubles live under `src/test/java`. Test code sees main code; main code cannot see
  test code, so helpers there can never leak into production.

### How it works

1. The canned answer goes in through the stub's constructor, or is written directly in
   the method.
2. The stubbed method ignores its arguments and returns the can.
3. Methods the test never touches get empty bodies.
4. The test asserts on the **service's** result. It never asks the stub anything,
   because a stub has nothing to tell. (A stub cannot answer "were you called?" — that
   gap is what a mock fills, next lesson.)

### The example

The class under test (unchanged from Lesson 1). `Optional<Hold>` is a box that either
holds one `Hold` or is empty.

`src/main/java/com/zenith/doubles/SeatHoldService.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * The class under test. Rule: a hold may take one more seat
 * only while it has fewer than 10 seats.
 */
public class SeatHoldService {

    private final HoldRepository repository;

    /** Constructor injection: the repository comes in through the constructor. */
    public SeatHoldService(HoldRepository repository) {
        this.repository = repository;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        return found.get().seats() < 10;
    }
}
```

The stub the task asks for — "`findById` always returns the same hold".

`src/test/java/com/zenith/doubles/StubHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * STUB — double 1 of 4 for the task.
 * A stub returns canned answers and records nothing.
 * This one answers every findById with the one hold it was given.
 */
public class StubHoldRepository implements HoldRepository {

    private final Hold cannedHold;

    public StubHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.of(cannedHold); // same answer every time; the id is ignored
    }

    @Override
    public void save(Hold hold) {
        // a stub does nothing here: no test using this stub touches save
    }

    @Override
    public void deleteById(long id) {
        // a stub does nothing here either
    }
}
```

A second stub, for the situation that is hard to stage for real.

`src/test/java/com/zenith/doubles/StubMissingHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * STUB — a second stub, for a situation that is hard to stage with a real database:
 * the hold does not exist. Canned answer: "nothing found", every time.
 */
public class StubMissingHoldRepository implements HoldRepository {

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.empty(); // canned answer: no such hold
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

The tests. `@Test` marks a method JUnit runs as one test. `assertTrue(x)` passes only if
`x` is true; `assertFalse(x)` passes only if `x` is false.

`src/test/java/com/zenith/doubles/L2StubTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 2: the service under test is fed by stubs.
 * Every assert looks at the service's answer. No assert ever looks at the stub.
 */
class L2StubTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        HoldRepository stub = new StubHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(stub);
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void holdWithTenSeatsIsFull() {
        HoldRepository stub = new StubHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(stub);
        assertFalse(service.canAddSeat(8));
    }

    @Test
    void missingHoldCannotTakeASeat() {
        HoldRepository stub = new StubMissingHoldRepository();
        SeatHoldService service = new SeatHoldService(stub);
        assertFalse(service.canAddSeat(99)); // id 99 exists nowhere
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L2StubTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s -- in com.zenith.doubles.L2StubTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough — the missing-hold test, step by step

1. `new StubMissingHoldRepository()` builds the stub. Its one behaviour: answer empty.
2. `new SeatHoldService(stub)` plugs the stub into the service's constructor.
3. `service.canAddSeat(99)` runs. Inside, `repository.findById(99)` executes the stub's
   method. It ignores the 99 and returns `Optional.empty()` instantly.
4. Back in `canAddSeat`: `found.isEmpty()` is true, so the method returns `false`.
5. `assertFalse(false)` passes.

The test proved the **service** handles a missing hold safely. The stub only set the
stage. The stub's answers are facts we invented; checking our own inventions proves
nothing, so no assert ever points at the stub.

### Important terms

| Term | Meaning |
| --- | --- |
| Canned answer | An answer decided in advance, the same every time. |
| Stub | A test double that returns canned answers and records nothing. |
| `src/test/java` | Where test-only code lives. Test code sees main code; main code cannot see test code. |
| Failure vs error | A failure: a check compared values and lost. An error: the code threw an exception before any comparing happened. |

### Where stubs are used

- A price catalog stub that says every ticket costs 50, so a totals test can do clean math.
- A clock stub that says 23:59, to test closing-time rules in the middle of the day.
- A payment gateway stub that always declines, to test the "card declined" path without a bank.
- A config stub that says a feature is off.
- A repository stub that returns nothing, to test the not-found path.

### Common mistakes

- **Putting logic in the stub.** `if (id == 7) ... else ...` means you are now testing
  your stub. Keep stubs dumb; write a second tiny stub for a second situation.
- **Asserting on the stub.** `assertEquals(4, stub.findById(7).get().seats())` proves
  only that the stub works, and you wrote the stub.
- **Feeling guilty about ignoring the id.** Not cheating. One canned answer is all the
  test's world needs.
- **One giant configurable stub.** Three dumb stubs read better than one stub with ten
  settings.

### Link to the subtask

S1, first bullet, done: "a **stub** whose `findById` always returns the same hold" =
`StubHoldRepository`, hand-written, no library. The missing-hold variant is a bonus and
is still a stub: canned answers, no memory.

### Your doubts

None raised for this lesson.

### Summary for revision

- Stub = canned answers + no memory.
- Stubs feed the class under test, and stage awkward situations cheaply.
- Assert on the service's result, never on the stub.
- Keep stubs dumb; multiply them instead of making one clever.
- Doubles live in `src/test/java` so they cannot leak into production.
- The missing-hold stub caught a real crash: removing the `isEmpty` guard turned the
  test red with `NoSuchElementException`.

### Practice questions (with answers)

**Question 1.** Here is a complete interface and a complete class under test:

```java
public interface PriceCatalog {
    int priceInPaise(String eventCode); // price of one ticket for that event
}

public class TicketPricer {

    private final PriceCatalog catalog;

    public TicketPricer(PriceCatalog catalog) {
        this.catalog = catalog;
    }

    public int totalFor(String eventCode, int ticketCount) {
        return catalog.priceInPaise(eventCode) * ticketCount;
    }
}
```

(a) Write a stub class for `PriceCatalog` that always answers 50. (b) Using it, write one
JUnit test method proving that 3 tickets for event "ROCK" cost 150 in total. Reminder:
`assertEquals(expected, actual)` passes only if both values are equal.

**Answer.** (a) The stub implements the interface and returns the can:

```java
public class StubPriceCatalog implements PriceCatalog {
    @Override
    public int priceInPaise(String eventCode) {
        return 50; // canned answer, event code ignored
    }
}
```

(b) The test plugs the stub in and asserts on the pricer's answer:

```java
@Test
void threeTicketsCostOneFiftyPaise() {
    TicketPricer pricer = new TicketPricer(new StubPriceCatalog());
    assertEquals(150, pricer.totalFor("ROCK", 3));
}
```

The stub feeds the number 50; the service's own logic does 50 × 3; the assert checks the
service's answer, 150. (This code was compiled and run; the test passes.)

**Question 2.** A stub returns canned answers and records nothing. Here is a complete
class a teammate calls "my stub":

```java
public class ChattyHoldRepository implements HoldRepository {

    private final Hold cannedHold;
    public int findByIdCallCount = 0;

    public ChattyHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdCallCount = findByIdCallCount + 1;
        return Optional.of(cannedHold);
    }

    @Override
    public void save(Hold hold) { }

    @Override
    public void deleteById(long id) { }
}
```

Which exact part stops this being a plain stub, and which half of the stub definition
does it break?

**Answer.** The field `public int findByIdCallCount` plus the line
`findByIdCallCount = findByIdCallCount + 1;`. The answers are still canned, so the first
half of the definition holds. But the class now remembers how it was called, which
breaks the second half: "records nothing". A stub with memory is a different kind of
double — a mock, the subject of Lesson 3.

### Do it yourself — Lesson 2 (second laptop)

Continues the project from Lesson 1: folder `~/zenith/test-doubles/scratch-app`, Lesson 1
files passing `mvn test`.

1. Go to the project:

```bash
cd ~/zenith/test-doubles/scratch-app
```

2. Create the three files from this lesson's example section, all under
   `src/test/java/com/zenith/doubles/`: `StubHoldRepository.java`,
   `StubMissingHoldRepository.java`, `L2StubTest.java`.

3. Run:

```bash
mvn test
```

Expected (success):

```text
[INFO] Running com.zenith.doubles.L2StubTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s -- in com.zenith.doubles.L2StubTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

4. Watch the stub catch a real bug. Open
   `src/main/java/com/zenith/doubles/SeatHoldService.java` and delete these guard lines
   from `canAddSeat`:

```java
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
```

Run `mvn test` again. Expected (broken):

```text
[ERROR] com.zenith.doubles.L2StubTest.missingHoldCannotTakeASeat -- Time elapsed: 0.003 s <<< ERROR!
java.util.NoSuchElementException: No value present
	at java.base/java.util.Optional.get(Optional.java:143)
	at com.zenith.doubles.SeatHoldService.canAddSeat(SeatHoldService.java:20)
[ERROR] Tests run: 7, Failures: 0, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
```

Decoding it:

| Piece | Meaning |
| --- | --- |
| `Errors: 1`, not `Failures: 1` | A failure: a check compared values and lost. An error: the code blew up with an exception before any comparing happened. This is an error. |
| `NoSuchElementException: No value present` | Thrown by `Optional.get()` when the box is empty. The service opened the box without checking first. |
| `at ...SeatHoldService.canAddSeat(SeatHoldService.java:20)` | The exact file and line that blew up. |

Only a test that can stage "no such hold" could catch this bug. That is the
missing-hold stub earning its keep.

5. Put the guard lines back. Run `mvn test` once more.

**Done when:** `mvn test` prints `Tests run: 7, Failures: 0, Errors: 0`, and you can say
in one sentence why `missingHoldCannotTakeASeat` exists.

---

## Lesson 3 — The mock (serves S1)

### Definition

A **mock** is a stub that also remembers how it was called: how many times each method
ran, and with what arguments. A test can then assert on that memory. Checking a call
that was made, instead of a returned value, is called checking the **interaction**.

### Why it is needed

1. **Some methods return nothing.** `void refund(String orderId)` on a payment gateway:
   the money moves in a bank, far outside the program. Inside the test there is no value
   to check. The only evidence is the call itself. A stub cannot help — it has no memory.
2. **Wasteful or duplicate calls are invisible to state checks.** A service can call the
   repository twice and still return correct answers. Only call-memory sees the waste.

### Key points

- Mock = stub + memory (call counts, arguments received).
- The test asserts on the memory **after** letting the service act.
- Declare the test-side variable with the concrete type (`MockHoldRepository`), not the
  interface. The recording fields are not on the interface.
- The service still receives it as a plain `HoldRepository`.
- Public fields are fine in hand-written test tooling.
- Default to asserting results; assert calls when the call is the behaviour.

### How it works

1. Take a stub: canned answer in the constructor, empty unused methods.
2. Add public fields for the memory: a call counter, a holder for the last argument.
3. In the stubbed method, first update the memory, then return the canned answer.
4. In the test, let the service act.
5. After the action, assert on the memory fields.

The concrete-type trap, with the real compiler message. Writing
`HoldRepository mock = new MockHoldRepository(...)` and then `mock.findByIdCallCount`
gives:

```text
[ERROR] L3MockTest.java:[22,29] cannot find symbol
  symbol:   variable findByIdCallCount
  location: variable mock of type com.zenith.doubles.HoldRepository
```

"Cannot find symbol" means the type `HoldRepository` has no field with that name. Only
the concrete class `MockHoldRepository` has it.

### The example

The mock — the task's exact spec, plus one bonus memory field.

`src/test/java/com/zenith/doubles/MockHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.Optional;

/**
 * MOCK — double 2 of 4 for the task.
 * A mock is a stub that also remembers how it was called.
 * Same canned answer as the stub, plus a call count and the last id asked for.
 * Public fields are fine here: this is hand-written test tooling, not production code.
 */
public class MockHoldRepository implements HoldRepository {

    private final Hold cannedHold;

    public int findByIdCallCount = 0;
    public long lastAskedId = -1;

    public MockHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdCallCount = findByIdCallCount + 1; // the memory: how many times
        lastAskedId = id;                          // the memory: asked with what
        return Optional.of(cannedHold);            // still the stub's canned answer
    }

    @Override
    public void save(Hold hold) {
        // nothing: no test here touches save
    }

    @Override
    public void deleteById(long id) {
        // nothing
    }
}
```

The tests. `assertEquals(expected, actual)` passes only if both values are equal.

`src/test/java/com/zenith/doubles/L3MockTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 3: the mock remembers how it was called, and the test asserts on that memory.
 * Note the variable type: MockHoldRepository, not HoldRepository.
 * The recording fields are not part of the interface, so the test needs the concrete type.
 */
class L3MockTest {

    @Test
    void looksUpTheHoldExactlyOnce() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(7);

        assertEquals(1, mock.findByIdCallCount); // asserting on the calls, not the result
    }

    @Test
    void asksForTheIdItWasGiven() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(7);

        assertEquals(7, mock.lastAskedId);
    }

    @Test
    void stillAnswersLikeAStub() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        assertTrue(service.canAddSeat(7)); // a mock can still serve a plain state check
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L3MockTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zenith.doubles.L3MockTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough — the call-count test, step by step

1. `new MockHoldRepository(new Hold(7, 4))` builds the mock. Memory starts empty:
   `findByIdCallCount` is 0, `lastAskedId` is -1.
2. `new SeatHoldService(mock)` plugs the mock into the service.
3. `service.canAddSeat(7)` acts. Inside, `repository.findById(7)` runs the mock's
   method: the counter goes 0 to 1, `lastAskedId` becomes 7, the canned hold comes back.
4. The service returns `true`. This test ignores that result on purpose.
5. `assertEquals(1, mock.findByIdCallCount)` compares 1 with 1. Pass.

**What only a mock can see.** With a wasteful bug planted in the service — a second,
pointless lookup whose result is thrown away:

```java
public boolean canAddSeat(long holdId) {
    repository.findById(holdId); // wasteful extra lookup, result thrown away
    Optional<Hold> found = repository.findById(holdId);
    ...
}
```

the real run showed:

```text
[INFO] Running com.zenith.doubles.L2StubTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 ...
[ERROR] com.zenith.doubles.L3MockTest.looksUpTheHoldExactlyOnce  <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <1> but was: <2>
[INFO] Running com.zenith.doubles.L1DatabaseSpeedTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.022 s ...
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
```

Every state test stayed green: the answers were still correct. The database test even
passed, just twice as slow (6.0 s instead of 3.0 s), because every pretend network trip
now happened twice. Only the mock caught it: expected 1 call, saw 2.

### Important terms

| Term | Meaning |
| --- | --- |
| Mock | A stub that also remembers how it was called. |
| Interaction | A call made on a dependency. Interaction checking = asserting on calls. |
| Concrete type | The actual class (`MockHoldRepository`), as opposed to the interface it implements. The memory fields live only on the concrete type. |
| `assertEquals(expected, actual)` | Passes only if both values are equal. Expected comes first. |

### Where mocks are used

- A refund is requested **exactly once** when an order is cancelled. Zero cheats the
  customer; two loses money.
- A confirmation email goes to the right address, once.
- Something must **not** happen: a sold-out event must never reach the payment gateway —
  `assertEquals(0, mock.chargeCallCount)` is the whole test.
- Work is not done twice, like the wasteful double lookup above.

### Common mistakes

- **Counting calls when a result exists.** "The repository was called" proves nothing
  about whether seat holds work. If the behaviour shows up in a value, assert on the
  value.
- **Verifying everything.** Asserting every call, in order, with every argument glues
  the test to the current wiring; harmless cleanups then turn tests red. Record much,
  assert little.
- **The wrong variable type.** `HoldRepository mock = ...` hides the memory fields;
  the compiler says `cannot find symbol`.
- **Asserting before acting.** The memory fills during the action; read it after.

### Link to the subtask

S1, second bullet, done: "a **mock** that is the same but also increments
`findByIdCallCount`" = `MockHoldRepository`, hand-written, with `lastAskedId` as a bonus.
Two of four doubles are written.

### Your doubts

None raised for this lesson.

### Summary for revision

- Mock = stub + memory. Test asserts on the memory after the action.
- Mocks exist because void outbound actions and duplicate calls leave no readable state.
- Test-side variable uses the concrete mock type; the service sees only the interface.
- The wasteful-lookup experiment: state tests all green, only the mock went red.
- Default to state checks; use call checks when the call is the behaviour.

### Practice questions (with answers)

**Question 1.** Here is a complete interface and class under test. Note that `refund`
returns nothing:

```java
public interface RefundGateway {
    void refund(String orderId);
}

public class OrderCanceller {

    private final RefundGateway gateway;

    public OrderCanceller(RefundGateway gateway) {
        this.gateway = gateway;
    }

    public void cancel(String orderId) {
        gateway.refund(orderId);
    }
}
```

(a) Write a hand-made mock class `MockRefundGateway` that records how many times
`refund` ran and the last `orderId` it was given. (b) Write one JUnit test proving that
`cancel("A1")` refunds exactly once, with order id "A1".

**Answer.** (a) The mock implements the interface and records before doing nothing else —
there is no answer to return, because `refund` is void:

```java
public class MockRefundGateway implements RefundGateway {

    public int refundCallCount = 0;
    public String lastOrderId = null;

    @Override
    public void refund(String orderId) {
        refundCallCount = refundCallCount + 1;
        lastOrderId = orderId;
    }
}
```

(b) The test acts, then asserts on the memory:

```java
@Test
void cancellingRefundsExactlyOnce() {
    MockRefundGateway mock = new MockRefundGateway();
    OrderCanceller canceller = new OrderCanceller(mock);

    canceller.cancel("A1");

    assertEquals(1, mock.refundCallCount);
    assertEquals("A1", mock.lastOrderId);
}
```

(This code was compiled and run; the test passes.) Note there was nothing else to
assert: `cancel` returns nothing and `refund` returns nothing. Interaction checking was
the only option — this is the natural home of a mock.

**Question 2.** Here is a complete set of types and one test:

```java
public interface Sms {
    void send(String phone, String text);
}

public class MockSms implements Sms {
    public int sendCallCount = 0;

    @Override
    public void send(String phone, String text) {
        sendCallCount = sendCallCount + 1;
    }
}

public class ShowCancelledNotifier {

    private final Sms sms;

    public ShowCancelledNotifier(Sms sms) {
        this.sms = sms;
    }

    public void notifyAll(List<String> phones) {
        for (String phone : phones) {
            sms.send(phone, "Show cancelled, refund on the way");
        }
    }
}
```

The test runs:

```java
MockSms mock = new MockSms();
ShowCancelledNotifier notifier = new ShowCancelledNotifier(mock);
notifier.notifyAll(List.of("9800000001", "9800000002"));
```

(a) What is `mock.sendCallCount` now? Walk through why. (b) Suppose a bug made
`notifyAll` silently skip everyone. Why could no state test — one that only checks
returned values — ever catch that bug?

**Answer.** (a) It is 2. The list has two phone numbers. The loop runs once per number.
Each loop turn calls `sms.send(...)`, which runs the mock's `send`, which adds 1 to the
counter. Two numbers, two calls, counter 2. (This was compiled and run: 2 is correct,
and an empty list gives 0.) (b) `notifyAll` returns nothing, and a skipped SMS changes
no value anywhere inside the program — the missing effect would have happened in the
phone network, outside. With no returned value and no stored value to read, a state test
has nothing to look at. Only call-memory can notice that `send` ran zero times instead
of two.

### Do it yourself — Lesson 3 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, with Lessons 1 and 2
passing.

1. Go to the project:

```bash
cd ~/zenith/test-doubles/scratch-app
```

2. Create the two files from this lesson's example section, both under
   `src/test/java/com/zenith/doubles/`: `MockHoldRepository.java`, `L3MockTest.java`.

3. Run:

```bash
mvn test
```

Expected (success):

```text
[INFO] Running com.zenith.doubles.L3MockTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 ...
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

4. See what only the mock sees. Open
   `src/main/java/com/zenith/doubles/SeatHoldService.java` and add this line as the
   first line inside `canAddSeat`:

```java
        repository.findById(holdId); // wasteful extra lookup, result thrown away
```

Run `mvn test`. Expected (one red, everything else green):

```text
[ERROR] L3MockTest.looksUpTheHoldExactlyOnce:22 expected: <1> but was: <2>
[INFO] ... L1DatabaseSpeedTest ... Time elapsed: 6.0 s   (twice as slow, but still passing)
[ERROR] Tests run: 10, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

| Piece | Meaning |
| --- | --- |
| `expected: <1> but was: <2>` | The test demanded exactly one `findById` call. The mock counted two. |
| Every other test green | The answers were still correct. State checks cannot see wasted work. |
| Database test at ~6 s | The waste is real: every pretend network trip now happens twice. |

5. Remove the wasteful line. Run `mvn test` once more — back to 10 green.

**Done when:** all 10 tests pass, and you can answer in one sentence: what did the mock
see that no state test could?

---

## Lesson 4 — State testing vs interaction testing (serves S3, CP)

### Definition

**State testing** means asserting on resulting values: what a method returned, or what
can be read back afterwards. **Interaction testing** means asserting on calls that were
made to a dependency. `assertTrue(service.canAddSeat(7))` is a state check.
`assertEquals(1, mock.findByIdCallCount)` is an interaction check.

### Why it is needed

Asserting the wrong thing produces a test that lies. A test that checks "the repository
was called" proves nothing about whether a seat hold actually works — it passes while
the business rule is broken. The reverse mistake exists too: a wasteful double lookup is
invisible to every state check. Neither kind is "the good one". Each sees what the other
cannot.

### Key points

- State check = assert on values. Interaction check = assert on calls.
- Default to state. Reach for interaction when the call itself is the promise.
- State checks test the promise; interaction checks test the wiring.
- Interaction checks can turn red on harmless internal cleanups.
- Two live experiments prove the split: the rule bug was caught only by state checks;
  the wasteful-lookup bug was caught only by the interaction check.

### How it works — the decision procedure

1. Does the behaviour show up in a value I can read (a returned answer, or stored data I
   can fetch back)? If yes: state check on that value.
2. If not — the behaviour is an outgoing action whose effect lives outside the program
   (refund, email, SMS) — the call is the only evidence. Interaction check.
3. If both are possible, prefer state. State survives internal rewiring and asserts what
   users actually rely on.

### The example — one scenario, two checks

Scenario: a hold already has 10 seats, the legal maximum. One more must be refused.

`src/test/java/com/zenith/doubles/L4StateVsInteractionTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 4: the same scenario tested two ways.
 * Scenario: a hold already has 10 seats, the legal maximum. One more must be refused.
 * The state check asserts on the resulting value.
 * The interaction check asserts on the calls that were made.
 */
class L4StateVsInteractionTest {

    @Test
    void stateCheckFullHoldGetsNo() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(mock);

        boolean answer = service.canAddSeat(8);

        assertFalse(answer); // state: the resulting value says no
    }

    @Test
    void interactionCheckRepositoryAskedOnce() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(8);

        assertEquals(1, mock.findByIdCallCount); // interaction: the call was made once
    }
}
```

Both pass against the correct service (12 tests green in total).

### Walkthrough — the experiment

The rule lives in `SeatHoldService.canAddSeat`:

```java
public boolean canAddSeat(long holdId) {
    Optional<Hold> found = repository.findById(holdId);
    if (found.isEmpty()) {
        return false; // no such hold, nothing to add to
    }
    return found.get().seats() < 10;
}
```

Planting the off-by-one bug — `seats() <= 10` instead of `seats() < 10` — allows an
eleventh seat. Tickets oversell. The real run with the bug:

```text
[ERROR] Failures:
[ERROR]   L1DatabaseSpeedTest.holdWithTenSeatsIsFull:23 expected: <false> but was: <true>
[ERROR]   L1StandInSpeedTest.holdWithTenSeatsIsFull:25 expected: <false> but was: <true>
[ERROR]   L2StubTest.holdWithTenSeatsIsFull:25 expected: <false> but was: <true>
[ERROR]   L4StateVsInteractionTest.stateCheckFullHoldGetsNo:23 expected: <false> but was: <true>
[ERROR] Tests run: 12, Failures: 4, Errors: 0, Skipped: 0
```

Read it as two lists. Went red: four tests, every one a state check on the full-hold
scenario. Stayed green: every interaction check. The interaction check asked "did you
consult the repository exactly once?" — a service with a broken rule still consults
once. True answer, wrong question. The state check asked "did you refuse the eleventh
seat?" — the promise itself.

Paired with Lesson 3's experiment: the rule bug was visible only to state checks; the
wasteful-lookup bug was visible only to the interaction check. Two bugs, two kinds of
eyes.

### Important terms

| Term | Meaning |
| --- | --- |
| State testing | Asserting on resulting values. Usually the better choice. |
| Interaction testing | Asserting on calls that were made, rather than on the result. |
| The promise vs the wiring | State checks guard what users rely on; interaction checks guard how the work is currently done. |

### Where each is used

State checks (the default): calculations, decisions, storage once data can be read back.
Interaction checks (the specialist): void outbound effects (refund exactly once),
forbidden calls (`assertEquals(0, mock.chargeCallCount)` for a sold-out show),
efficiency promises (one network trip, not two).

### Common mistakes

- **The lazy substitute.** Asserting "the repository was called" instead of asserting
  the refusal. Passes while overselling ships.
- **Belt-and-braces everywhere.** Call-count asserts on top of every state test make
  harmless cleanups break dozens of tests.
- **Interaction-testing what you could read back.** Verifying `save` was called instead
  of saving and reading back.
- **Writing interaction testing off.** For refunds and must-not-happen rules it is the
  only honest test.

### Link to the subtask

Checkpoint question 4 is exactly this difference. S3's justifications use this
vocabulary: "the fake keeps the test about the rule" = state testing; "the refund call
is the behaviour under test" = interaction testing.

### Your doubts

None raised for this lesson.

### Summary for revision

- State = assert values. Interaction = assert calls.
- Rule bug → only state checks went red. Wasteful-lookup bug → only the interaction
  check went red.
- Default to state; use interaction when the call is the promise (void outbound effects,
  forbidden calls, exactly-once contracts).

### Practice questions (with answers)

**Question 1.** For each test described below, say whether it is a state check or an
interaction check:

1. A pricing test: `assertEquals(150, pricer.totalFor("ROCK", 3))` — the pricer
   multiplies a catalog price of 50 by 3.
2. A cancellation test: after `canceller.cancel("A1")`, it runs
   `assertEquals(1, mockGateway.refundCallCount)`.
3. A booking test for a cancelled show: after asking to book, it runs
   `assertEquals(0, mockGateway.chargeCallCount)`.
4. A seat-hold test: `assertFalse(service.canAddSeat(8))` for a hold that already has
   10 seats.

**Answer.**

1. **State.** It asserts on the returned value, 150.
2. **Interaction.** It asserts on how many times `refund` was called.
3. **Interaction.** It asserts that a call never happened. "Nothing was called" is still
   a statement about calls.
4. **State.** It asserts on the returned decision, false.

**Question 2.** `Hold` is `record Hold(long id, int seats) {}`. The repository
interface and a counting mock:

```java
public interface HoldRepository {
    Optional<Hold> findById(long id);
    void save(Hold hold);
    void deleteById(long id);
}

public class CountingHoldRepository implements HoldRepository {
    private final Hold cannedHold;
    public int findByIdCallCount = 0;

    public CountingHoldRepository(Hold cannedHold) {
        this.cannedHold = cannedHold;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdCallCount = findByIdCallCount + 1;
        return Optional.of(cannedHold);
    }

    @Override
    public void save(Hold hold) { }

    @Override
    public void deleteById(long id) { }
}
```

A service with a tiny cache (`orElseThrow()` opens the Optional box, or throws if
empty):

```java
public class CachingHoldService {
    private final HoldRepository repository;
    private Hold lastHold; // simple cache: remembers the last hold fetched

    public CachingHoldService(HoldRepository repository) {
        this.repository = repository;
    }

    public int seatsIn(long holdId) {
        if (lastHold != null && lastHold.id() == holdId) {
            return lastHold.seats(); // answered from the cache, no repository call
        }
        lastHold = repository.findById(holdId).orElseThrow();
        return lastHold.seats();
    }
}
```

Two tests:

```java
@Test
void answersFourBothTimes() {
    CountingHoldRepository mock = new CountingHoldRepository(new Hold(7, 4));
    CachingHoldService service = new CachingHoldService(mock);

    assertEquals(4, service.seatsIn(7));
    assertEquals(4, service.seatsIn(7));
}

@Test
void fetchesFromTheRepositoryOnlyOnce() {
    CountingHoldRepository mock = new CountingHoldRepository(new Hold(7, 4));
    CachingHoldService service = new CachingHoldService(mock);

    service.seatsIn(7);
    service.seatsIn(7);

    assertEquals(1, mock.findByIdCallCount);
}
```

A teammate later deletes the cache, so `seatsIn` always fetches. Users still get the
right answers. (a) Which test breaks, and what does its failure say? (b) What was each
test really checking? (c) When would keeping `fetchesFromTheRepositoryOnlyOnce` be right
anyway?

**Answer.** (a) `fetchesFromTheRepositoryOnlyOnce` breaks. Two calls to `seatsIn` now
mean two repository fetches, so the counter is 2 and the failure reads
`expected: <1> but was: <2>`. (This was compiled and run: with the cache the counter is
1; without it, 2.) `answersFourBothTimes` keeps passing — the answers are still 4.
(b) `answersFourBothTimes` checks the promise to users: asking twice gives the right
seats both times. `fetchesFromTheRepositoryOnlyOnce` checks how the work is done: that a
cache sits in front of the repository. Deleting the cache changed the how, not the
promise. (c) Keep it when the single fetch *is* a promise: the repository call is billed
per request, or the cache is the very feature being built, or a performance contract
says one trip per id. Then the interaction is the behaviour, and testing it is honest.

### Do it yourself — Lesson 4 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–3 passing (10
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. Create `src/test/java/com/zenith/doubles/L4StateVsInteractionTest.java` as shown in
   this lesson's example section.
3. Run `mvn test`. Expected: `Tests run: 12, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
4. The experiment: in `src/main/java/com/zenith/doubles/SeatHoldService.java` change
   `return found.get().seats() < 10;` to `return found.get().seats() <= 10;`. Run
   `mvn test`. Expected: 4 failures, all of them `expected: <false> but was: <true>`,
   all on full-hold state checks; every interaction check green; `Tests run: 12,
   Failures: 4`.
5. Change the line back to `< 10`. Run `mvn test` — 12 green.

**Done when:** all 12 pass and you can say which kind of check caught the rule bug, and
which kind caught the wasteful-lookup bug.

---

## Lesson 5 — The fake (serves S1 and S2)

### Definition

A **fake** is a real, working implementation of the interface — just a lightweight one.
It is not canned. What comes out depends on what was put in: what you save, you can
find; what you delete is gone. It cuts the heavy parts (no database server, no network,
no disk) by keeping everything in memory.

### Why it is needed

1. **Flows with several steps.** Save, look up, delete, confirm gone. With stubs you
   would script each step's answer in advance, and the test would only prove the script
   plays back. A fake computes answers, so the test proves real cause and effect.
2. **Many scenarios, one helper.** Lesson 2 needed two stub classes for "exists" and
   "missing". A fake covers both plus the full hold with plain data: save what should
   exist, skip what should not. This is why the task says keep the fake — Phase 1
   use-case tests reuse it.
3. **Storage becomes state-testable.** Instead of counting calls to `save`, save and
   read back. The promise, not the plumbing.

### Key points

- Fake = genuine lightweight implementation. Behaviour, not scripts.
- One `HashMap` is the whole trick.
- A fake has real behaviour, so it earns a few tests of its own.
- Build a fresh fake inside each test; shared fakes leak state between tests.
- The fake must honour the real contract (overwrite on repeated save, empty after
  delete), or tests pass while production breaks.
- No business rules inside the fake; it mirrors dumb storage only.

### How it works

Reminder: a `HashMap` is a container of key–value pairs — `put(key, value)` stores under
a key, `get(key)` fetches (or gives `null`), `remove(key)` deletes the pair.

1. The fake owns a private `Map<Long, Hold>` — id as key, hold as value.
2. `save` does `put(hold.id(), hold)`. Saving the same id overwrites, like a row update.
3. `findById` does `get(id)` wrapped by `Optional.ofNullable(...)`: a hold becomes a
   full box; `null` becomes the empty box.
4. `deleteById` does `remove(id)`.

### The example

`src/test/java/com/zenith/doubles/FakeHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * FAKE — double 3 of 4 for the task. This one is a keeper.
 * A fake is a real, working implementation that is lightweight.
 * It genuinely saves, finds, and deletes — in memory, in a HashMap.
 * No canned answers: what comes out depends on what was put in.
 */
public class FakeHoldRepository implements HoldRepository {

    private final Map<Long, Hold> holdsById = new HashMap<>();

    @Override
    public Optional<Hold> findById(long id) {
        return Optional.ofNullable(holdsById.get(id)); // empty box if nothing stored
    }

    @Override
    public void save(Hold hold) {
        holdsById.put(hold.id(), hold); // genuinely stores; saving the same id overwrites
    }

    @Override
    public void deleteById(long id) {
        holdsById.remove(id); // genuinely gone afterwards
    }
}
```

The tests. Tests 1–4 guard the fake's own behaviour; test 5 is the payoff. Records
compare by value, so two `Hold(7, 4)` are equal.

`src/test/java/com/zenith/doubles/L5FakeTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 5: the fake has real behaviour, so it gets a few tests of its own (1-4),
 * and then it carries a whole multi-scenario service test on plain data (5).
 */
class L5FakeTest {

    @Test
    void findingBeforeAnySaveGivesEmpty() {
        FakeHoldRepository fake = new FakeHoldRepository();

        assertTrue(fake.findById(7).isEmpty());
    }

    @Test
    void whatYouSaveIsWhatYouFind() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));

        // records compare by value, so two Hold(7, 4) are equal
        assertEquals(new Hold(7, 4), fake.findById(7).get());
    }

    @Test
    void savingTheSameIdOverwrites() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));
        fake.save(new Hold(7, 9));

        assertEquals(9, fake.findById(7).get().seats());
    }

    @Test
    void deleteReallyDeletes() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));
        fake.deleteById(7);

        assertTrue(fake.findById(7).isEmpty());
    }

    @Test
    void oneFakeCarriesThreeScenarios() {
        FakeHoldRepository fake = new FakeHoldRepository();
        SeatHoldService service = new SeatHoldService(fake);

        fake.save(new Hold(7, 4));   // a hold with room
        fake.save(new Hold(8, 10));  // a full hold

        assertTrue(service.canAddSeat(7));
        assertFalse(service.canAddSeat(8));
        assertFalse(service.canAddSeat(99)); // never saved, so genuinely missing
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L5FakeTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in com.zenith.doubles.L5FakeTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Note: these tests could declare the variable as plain `HoldRepository`, because `save`,
`findById`, and `deleteById` all live on the interface. The mock needed its concrete
type for the counter fields; the fake's effects show up through the interface itself.

### Walkthrough — one fake, three scenarios

1. `new FakeHoldRepository()` — the map is empty.
2. `new SeatHoldService(fake)` — the fake is plugged in.
3. `fake.save(new Hold(7, 4))` puts 7 → Hold(7, 4). `fake.save(new Hold(8, 10))` puts
   8 → Hold(8, 10). The test's world now has exactly two holds.
4. `canAddSeat(7)`: `findById(7)` → `get(7)` → Hold(7, 4) → `4 < 10` → true.
5. `canAddSeat(8)`: `get(8)` → Hold(8, 10) → `10 < 10` → false.
6. `canAddSeat(99)`: `get(99)` → `null` → empty box → guard → false.

Nothing was canned. Hold 99 is missing because it was never saved, not because a script
said so.

**The fake's safety net at work.** With `deleteById` emptied out (bug planted on
purpose), the real run gave:

```text
[ERROR] L5FakeTest.deleteReallyDeletes:49 expected: <true> but was: <false>
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

The fake's own test caught the fake's own bug before it could poison service tests.

### Important terms

| Term | Meaning |
| --- | --- |
| Fake | A real, working, lightweight implementation of the interface. |
| `HashMap` | Key–value container: `put` stores, `get` fetches or gives `null`, `remove` deletes. |
| `Optional.ofNullable(x)` | Wraps `x` in the box; if `x` is `null`, the box is empty. |
| Record equality | Records compare by field values, so `new Hold(7,4)` equals `new Hold(7,4)`. |

### Where fakes are used

- In-memory repositories (this one — reused in Phase 1 use-case tests).
- Fake gateways for local development: a payment gateway that always approves.
- In-memory file systems and message queues.
- An in-memory database (H2) standing in for PostgreSQL — the same idea, larger scale.

### Common mistakes

- **Drifting from the real contract.** Real repository overwrites on repeated id; if the
  fake throws instead, tests pass while production breaks.
- **Business rules inside the fake.** The 10-seat rule belongs to the service; the fake
  mirrors dumb storage.
- **A fake where a stub suffices.** One canned read → stub. Flows and reuse → fake.
- **Not testing the fake.** It has behaviour; behaviour can be wrong.
- **Sharing one fake across tests.** Leftover holds leak into the next test. Fresh fake
  per test.

### Link to the subtask

S1 bullet 3 done: "a **fake** backed by a `HashMap` that genuinely saves, finds, and
deletes". S2 done: the keeper lives at
`scratch-app/src/test/java/com/zenith/doubles/FakeHoldRepository.java` for Phase 1 to
reuse. Three of four doubles written.

### Your doubts

None raised for this lesson.

### Summary for revision

- Fake = working lightweight implementation; behaviour, not scripts.
- One HashMap: put, get-or-null wrapped in Optional, remove.
- Fakes carry multi-step flows and many scenarios with plain data.
- The fake gets its own small tests; a broken fake poisons everything above it.
- Keep this one — Phase 1 reuses it.

### Practice questions (with answers)

**Question 1.** The complete fake is above. `Hold` is `record Hold(long id, int seats) {}`.
A test runs this sequence on a fresh fake:

```java
fake.save(new Hold(5, 2));
fake.save(new Hold(5, 9));
fake.deleteById(5);
Optional<Hold> result = fake.findById(5);
```

(a) Is `result` empty, or does it hold a hold — and if so, which one? Walk through all
four lines. (b) What would `findById(5)` have returned at the very start, before any of
the four lines ran?

**Answer.** (a) `result` is empty. Line 1 stores 5 → Hold(5, 2). Line 2 stores under the
same key 5, so it overwrites: the map now holds 5 → Hold(5, 9), and Hold(5, 2) is gone.
Line 3 removes key 5 entirely; the map is empty. Line 4 does `get(5)`, which gives
`null`, and `Optional.ofNullable(null)` is the empty box. (b) Also empty: a fresh fake
has an empty map, so `get(5)` gives `null` straight away. Same answer, two different
true reasons — never stored vs stored-then-deleted. The fake computes both honestly.

**Question 2.** A teammate must test this complete service:

```java
public class CancelService {

    private final HoldRepository repository;

    public CancelService(HoldRepository repository) {
        this.repository = repository;
    }

    public void cancelHold(long holdId) {
        repository.deleteById(holdId);
    }
}
```

Test goal: after `cancelHold(7)`, a later `findById(7)` must be empty. Available: the
stub whose `findById` always answers one canned hold and whose `deleteById` does
nothing, and the fake that genuinely saves, finds, and deletes in a `HashMap`. Which
helper makes this test possible, and why can the other never express it?

**Answer.** Only the **fake**. The test writes itself: save Hold(7, 4) into the fake,
run `cancelHold(7)`, assert `fake.findById(7).isEmpty()`. The service's `deleteById`
lands in the fake's map and removes the entry, so the later `findById` honestly answers
empty — the deletion is observable. With the stub it can never work, no matter how the
test is written: the stub's `findById` returns the canned hold unconditionally and its
`deleteById` does nothing, so there is no connection between deleting and finding. After
`cancelHold(7)` the stub still answers with the hold. (Both claims were compiled and
run: the fake version passes; the stub still reports the hold present after the cancel.)
The general rule: when the behaviour under test is "an action changes what storage later
says", the double needs genuine linked behaviour — that is a fake.

### Do it yourself — Lesson 5 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–4 passing (12
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. Create `src/test/java/com/zenith/doubles/FakeHoldRepository.java` and
   `src/test/java/com/zenith/doubles/L5FakeTest.java` as shown in this lesson's example
   section.
3. Run `mvn test`. Expected: `L5FakeTest` shows `Tests run: 5`, total
   `Tests run: 17, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
4. Prove the fake is guarded: in `FakeHoldRepository.java`, delete the line
   `holdsById.remove(id);` so `deleteById` does nothing. Run `mvn test`. Expected:

```text
[ERROR] L5FakeTest.deleteReallyDeletes:49 expected: <true> but was: <false>
[ERROR] Tests run: 17, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

The fake's own test caught the fake's own bug, before it could poison the service tests.

5. Put the line back. Run `mvn test` — 17 green.

**Done when:** all 17 pass and you can say in one sentence what makes a fake different
from a stub.

---

## Lesson 6 — The spy (serves S1)

### Definition

A **spy** is a wrapper around a real object. It forwards every call to the wrapped
object, and records the calls — with their arguments — as they pass through. The answers
are real, from the thing underneath; the memory sits on top. Because a spy is just a
class, it can also answer one chosen method differently while the others still forward.

Mock vs spy, side by side:

| | Where do answers come from? | Memory? |
| --- | --- | --- |
| **Mock** | A can you scripted in advance. | Yes. |
| **Spy** | The real wrapped object. | Yes. |

### Why it is needed

- The mock remembers, but answers from scripts — useless for multi-step cause and effect.
- The fake behaves genuinely, but has no memory of how it was used.
- Some tests need both at once. Classic: prove a cache cuts repository traffic while the
  answers stay correct. Genuine answers plus a call log = spy.

### Key points

- Spy = wrap a real thing + forward every call + record arguments.
- "Real" means anything with genuine behaviour — the fake qualifies, so does the actual
  database repository.
- Record into public lists, one per method, so arguments are kept, not just counts.
- Every method must forward. A spy that records but does not forward is memory without
  reality.
- A spy supports state checks and interaction checks in the same test.
- Rarest of the four in daily work; stubs and fakes carry most tests.

### How it works

1. The spy implements the same interface as the real object.
2. The real object arrives through the spy's constructor and sits in a field.
3. Every method records first, then forwards. (An `ArrayList` is a list that grows as
   you add; `add` appends to the end.)
4. The test reads the lists after acting, like reading a mock's counters.

### The example

`src/test/java/com/zenith/doubles/SpyHoldRepository.java`:

```java
package com.zenith.doubles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SPY — double 4 of 4 for the task.
 * A spy wraps a real object: it forwards every call to the wrapped one,
 * and records the arguments as the calls pass through.
 * Real behaviour from underneath, memory on top.
 */
public class SpyHoldRepository implements HoldRepository {

    private final HoldRepository realRepository;

    public final List<Long> findByIdIds = new ArrayList<>();
    public final List<Hold> savedHolds = new ArrayList<>();
    public final List<Long> deletedIds = new ArrayList<>();

    public SpyHoldRepository(HoldRepository realRepository) {
        this.realRepository = realRepository;
    }

    @Override
    public Optional<Hold> findById(long id) {
        findByIdIds.add(id);                 // record the argument
        return realRepository.findById(id);  // forward to the real one
    }

    @Override
    public void save(Hold hold) {
        savedHolds.add(hold);                // record
        realRepository.save(hold);           // forward
    }

    @Override
    public void deleteById(long id) {
        deletedIds.add(id);                  // record
        realRepository.deleteById(id);       // forward
    }
}
```

The tests — the spy wraps the fake, which is real working code. Records compare by
value; lists compare by contents.

`src/test/java/com/zenith/doubles/L6SpyTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 6: the spy wraps a real repository (here: the fake, which is real working
 * code), forwards every call, and records the arguments on the way through.
 */
class L6SpyTest {

    @Test
    void forwardsToTheRealRepositoryUnderneath() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());

        spy.save(new Hold(7, 4));

        // The save really happened in the wrapped fake, so it can be found again.
        assertEquals(new Hold(7, 4), spy.findById(7).get());
    }

    @Test
    void recordsEveryCallAsItPassesThrough() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());
        SeatHoldService service = new SeatHoldService(spy);

        spy.save(new Hold(7, 4));
        service.canAddSeat(7);
        service.canAddSeat(7);

        // Lists compare by contents.
        assertEquals(List.of(7L, 7L), spy.findByIdIds); // two lookups, both for id 7
        assertEquals(List.of(new Hold(7, 4)), spy.savedHolds);
    }

    @Test
    void realAnswersAndMemoryTogether() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());
        SeatHoldService service = new SeatHoldService(spy);

        spy.save(new Hold(8, 10));

        assertFalse(service.canAddSeat(8));         // state check: real rule on real data
        assertEquals(List.of(8L), spy.findByIdIds); // interaction check: what was asked
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L6SpyTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in com.zenith.doubles.L6SpyTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`realAnswersAndMemoryTogether` is the signature trick: one test makes a state check and
an interaction check honestly. Neither the stub, the mock, nor the bare fake supports
both at once.

### Walkthrough — record, then forward

Tracing `recordsEveryCallAsItPassesThrough`:

1. The fake starts with an empty map; the spy wraps it with three empty lists.
2. `spy.save(new Hold(7, 4))`: append to `savedHolds`, forward — the fake stores
   7 → Hold(7, 4). Recorded and real.
3. First `canAddSeat(7)`: the service calls `spy.findById(7)`; the spy appends 7,
   forwards; the fake answers with the hold. `findByIdIds` is `[7]`.
4. Second `canAddSeat(7)`: same path. `[7, 7]`.
5. Both asserts compare lists by contents and pass.

**The forgotten forward.** With `realRepository.save(hold);` deleted (bug planted on
purpose), the real run gave:

```text
[ERROR] com.zenith.doubles.L6SpyTest.forwardsToTheRealRepositoryUnderneath <<< ERROR!
java.util.NoSuchElementException: No value present
	at com.zenith.doubles.L6SpyTest.forwardsToTheRealRepositoryUnderneath(L6SpyTest.java:23)
[ERROR] Tests run: 20, Failures: 0, Errors: 1, Skipped: 0
```

`savedHolds` proudly contained the hold — memory said "saved". The fake underneath never
got the call, so `findById` returned the empty box and `get()` threw. A spy that forgets
to forward is a mock wearing a spy's badge. Note: the recording test still passed with
this bug; only the forwarding test caught it. That is why a spy needs a forwarding test.

### Important terms

| Term | Meaning |
| --- | --- |
| Spy | A wrapper around a real object: forwards every call, records arguments. |
| Wrapper | An object that holds another object and passes calls to it, adding something on the way. |
| `ArrayList` | A list that grows as you add; `add` appends to the end. |
| List equality | Lists compare by contents, so `List.of(7L, 7L)` equals a grown list holding 7, 7. |

### Where spies are used

- Proving a cache works: wrap the real repository, read twice, assert right answers and
  only one recorded fetch.
- Learning what unfamiliar code really calls, by logging traffic to a real dependency.
- Auditing that a batch job touches exactly the right ids while really doing the work.
- Rarest of the four in daily unit tests; reach for it when you need truth and a log at
  once.

### Common mistakes

- **Forgetting to forward.** Memory without reality, and an error far from the cause.
- **Spying when scripting is fine.** If canned answers would satisfy the test, a mock is
  less machinery.
- **Wrapping what needs no wrapping.** `new Hold(7, 4)` is used directly, never doubled.
- **Asserting on the spy's memory when a read-back value is available.** Let values
  carry assertions when they can.

### Link to the subtask

S1 complete: all four doubles hand-written, no library, in
`scratch-app/src/test/java/com/zenith/doubles/` — `StubHoldRepository`,
`MockHoldRepository`, `FakeHoldRepository` (the S2 keeper), `SpyHoldRepository`. The
spy matches its spec: wraps a real repository, forwards every call, records the
arguments.

### Your doubts

None raised for this lesson.

### Summary for revision

- Spy = wrap real thing + forward everything + record arguments.
- Mock answers from a can; spy answers from the real wrapped object. Both remember.
- The spy alone supports state and interaction checks in one test.
- Every method must forward; the forwarding test exists because recording tests cannot
  catch a missing forward.
- Spy on fake = genuine answers, fast, logged.

### Practice questions (with answers)

**Question 1.** (The complete spy and fake code is in this lesson's example section;
`Hold` is `record Hold(long id, int seats) {}`.) A test runs:

```java
SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());
spy.save(new Hold(3, 2));
spy.findById(3);
spy.findById(9);
spy.deleteById(3);
Optional<Hold> last = spy.findById(3);
```

(a) What is in `spy.findByIdIds` at the end? (b) Is `last` empty or full, and why?
(c) What is in `spy.savedHolds`?

**Answer.** (a) `[3, 9, 3]`. Every `findById` is recorded, including the one that found
nothing (id 9) and the final one. Recording happens on the way in, regardless of the
answer. (b) Empty. The `deleteById(3)` was forwarded to the fake, which removed key 3
from its map. The final `findById(3)` forwarded to the fake, `get(3)` gave `null`, and
`ofNullable` made the empty box. The spy records calls; it does not change answers.
(c) `[Hold(3, 2)]` — exactly one save was recorded. (This whole sequence was compiled
and run; all three answers verified.)

**Question 2.** Two helpers both remember calls. The mock's `findById` answers from a
can given at construction, and counts calls; its `save` does nothing. The spy forwards
everything to a real repository it wraps and records arguments. The test must prove:
"after saving holds 1, 2, and 3, asking the service to total all seats gives the right
sum, and the repository was consulted exactly three times." Which helper, and what goes
wrong with the other?

**Answer.** The **spy**, wrapped around the fake. The test needs two things at once:
genuine linkage (the sum must come from the holds actually saved) and a call log (three
consultations). The spy-on-fake gives both: saves really land, lookups really answer,
and `findByIdIds` holds the three ids. With the mock, the counting half works but the
state half collapses: its `save` does nothing and its `findById` returns the same canned
hold for every id, so the "total" would be three copies of the can — not a sum of what
was saved. The test would either assert a meaningless number or quietly test the can.

### Do it yourself — Lesson 6 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–5 passing (17
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. Create `src/test/java/com/zenith/doubles/SpyHoldRepository.java` and
   `src/test/java/com/zenith/doubles/L6SpyTest.java` as shown in this lesson's example
   section.
3. Run `mvn test`. Expected: `L6SpyTest` shows `Tests run: 3`, total
   `Tests run: 20, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
4. Break the forwarding: in `SpyHoldRepository.java`, delete the line
   `realRepository.save(hold);`. Run `mvn test`. Expected:

```text
[ERROR] L6SpyTest.forwardsToTheRealRepositoryUnderneath <<< ERROR!
java.util.NoSuchElementException: No value present
[ERROR] Tests run: 20, Failures: 0, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
```

The memory recorded a save that never really happened; the wrapped fake stayed empty;
`get()` opened an empty box. Note that the recording test still passed — only the
forwarding test caught it.

5. Put the line back. Run `mvn test` — 20 green.

**Done when:** all 20 pass and you can finish from memory: "a mock answers from ___, a
spy answers from ___."

---

## Lesson 7 — Choosing a double (serves S3)

### Definition

A three-question decision procedure that picks the right double — fake, mock, stub, spy,
or none — for the behaviour under test. A **value object** is a small object defined
entirely by its values (two `Money(150)` are the same money), cheap to create, connected
to nothing outside.

### Why it is needed

Every double can be used in the wrong place with the test still green: a mock in a rule
test stays green while the rule is broken; a fake gateway with no interaction assert
stays green while refunds happen twice; a doubled `Money` tests the double instead of
the arithmetic. Green means nothing unless the double and the assertion match the
behaviour under test.

### Key points — the procedure

Ask in order, about "this test proves ___":

1. **Is the "dependency" a cheap value you could construct?** Build the real one. No
   double. (`Money`, `Hold`.)
2. **Is the dependency incidental — scenery the rule needs?** Fake (or stub), plain
   data, **state check** on the result.
3. **Is the call to the dependency itself the behaviour?** Mock, **interaction check**,
   usually "exactly once, with the right argument".

The spy sits outside the chain: real behaviour plus a call log at once (cache-proving
tests).

### The example — the task's three situations as running tests

New pieces: the outbound boundary, the service whose promise is one refund per cancel,
its mock, and the value object.

`src/main/java/com/zenith/doubles/PaymentGateway.java`:

```java
package com.zenith.doubles;

/**
 * Outbound boundary: asks the payment provider to return money.
 * The real implementation would call the provider's web service.
 * refund returns nothing — the effect happens in a bank, outside the program.
 */
public interface PaymentGateway {

    void refund(long orderId);
}
```

`src/main/java/com/zenith/doubles/OrderCancelService.java`:

```java
package com.zenith.doubles;

/**
 * Cancelling an order must trigger exactly one refund.
 * The refund call is the behaviour; there is no value to read back.
 */
public class OrderCancelService {

    private final PaymentGateway paymentGateway;

    public OrderCancelService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public void cancel(long orderId) {
        paymentGateway.refund(orderId);
    }
}
```

`src/test/java/com/zenith/doubles/MockPaymentGateway.java`:

```java
package com.zenith.doubles;

/**
 * MOCK for the payment gateway.
 * refund is void and its effect lives outside the program,
 * so call-memory is the only evidence a test can use.
 */
public class MockPaymentGateway implements PaymentGateway {

    public int refundCallCount = 0;
    public long lastRefundedOrderId = -1;

    @Override
    public void refund(long orderId) {
        refundCallCount = refundCallCount + 1;
        lastRefundedOrderId = orderId;
    }
}
```

`src/main/java/com/zenith/doubles/Money.java`:

```java
package com.zenith.doubles;

/**
 * A value object: defined entirely by its value, cheap to create,
 * connected to nothing outside. Amounts are in paise.
 * Value objects are used directly in tests — never doubled.
 */
public record Money(long paise) {

    public Money plus(Money other) {
        return new Money(paise + other.paise);
    }
}
```

`src/test/java/com/zenith/doubles/L7ChoosingTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 7: the three ticketing situations from the task, each with the right choice.
 * Situation 1: a business rule — fake, state check.
 * Situation 2: an outbound call — mock, interaction check.
 * Situation 3: a value object — no double at all.
 */
class L7ChoosingTest {

    @Test
    void rejectsTheEleventhSeatWithAFake() {
        FakeHoldRepository fake = new FakeHoldRepository();
        SeatHoldService service = new SeatHoldService(fake);
        fake.save(new Hold(8, 10)); // given: a hold already at the 10-seat maximum

        assertFalse(service.canAddSeat(8)); // state check: the rule says no

        // The repository is incidental here. The fake only makes the hold exist,
        // so the test stays about the rule.
    }

    @Test
    void cancellingRefundsExactlyOnceWithAMock() {
        MockPaymentGateway gateway = new MockPaymentGateway();
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        assertEquals(1, gateway.refundCallCount);      // the call IS the behaviour
        assertEquals(42, gateway.lastRefundedOrderId); // and it hit the right order
    }

    @Test
    void moneyAddsCorrectlyWithNoDouble() {
        Money oneHundred = new Money(100);
        Money fifty = new Money(50);

        assertEquals(new Money(150), oneHundred.plus(fifty));

        // No double anywhere. Money is a value object: cheap, real, deterministic.
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L7ChoosingTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in com.zenith.doubles.L7ChoosingTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough — the three decisions

| Situation | Choice | Justification |
| --- | --- | --- |
| Eleventh seat rejected | **Fake** | The repository is incidental; it only makes the full hold exist. State check on the answer. A stub would also work; the fake wins on reuse and readable data setup. A mock asserting `findByIdCallCount` would stay green with the rule broken. |
| Cancel refunds exactly once | **Mock** | The call is the behaviour. `refund` is void and acts on a bank outside the program — nothing to read back. Assert exactly 1 call with the right order id. |
| `Money.plus` adds | **No double** | Value object: cheap, deterministic, no dependencies. A double would assert against your own script. Never mock a value object. |

**Why "exactly once" and not "at least once":** with a second `refund` call planted in
`cancel`, the real run gave:

```text
[ERROR] L7ChoosingTest.cancellingRefundsExactlyOnceWithAMock:35 expected: <1> but was: <2>
[ERROR] Tests run: 23, Failures: 1, Errors: 0, Skipped: 0
```

A double refund — money paid out twice — caught by one integer. An at-least-once assert
would have waved it through.

### Where this is used

At the top of every test: rule and calculation tests → fake + state; outbound effects →
mock + interaction; pure domain (`Money`, `Hold`, labels) → real objects, no doubles.

### Common mistakes

- **Mocking the repository in a rule test.** Green test, broken rule.
- **Fake gateway with no interaction assert.** Proves refunds zero times and twice
  equally well.
- **"At least once" where "exactly once" is the promise.** Lets double refunds ship.
- **Doubling value objects.** If a real `Money` works, use a real `Money`.
- **Choosing by habit ("we always mock everything").** Choose per behaviour.

### Your doubts

None raised for this lesson.

### Summary for revision

- Three questions in order: constructible value? → no double. Incidental dependency? →
  fake + state. Call is the behaviour? → mock + interaction.
- The three situations: fake / mock / no double.
- Exactly-once asserts catch double refunds; at-least-once does not.

### Practice questions (with answers)

**Question 1.** For each situation, name the double (or "none") and the kind of check:

1. A `DiscountService` gives members 10% off. It looks members up through
   `interface MemberRepository { Optional<Member> findById(long id); }`. The test: a
   member pays 90 paise for a 100-paise ticket. The repository's only job is that the
   member exists.
2. A `BookingService` must send exactly one confirmation SMS after a successful booking,
   through `interface Sms { void send(String phone, String text); }` — `send` returns
   nothing.
3. `Seat.label()` must format row 3, number 12 as `"R3-12"`. `Seat` is
   `record Seat(int row, int number)` with no dependencies.

**Answer.**

1. **Fake (or stub) + state check.** The repository is incidental scenery — the member
   just has to exist. Save (or can) a member, then assert the returned price is 90.
2. **Mock + interaction check.** `send` is void and its effect lives in the phone
   network. Assert `sendCallCount` is exactly 1 and the phone number is right.
3. **No double + state check.** `Seat` is a value object with no dependencies. Build
   `new Seat(3, 12)` and assert `label()` equals `"R3-12"`.

**Question 2.** A teammate tests the eleventh-seat rule like this (the mock's `findById`
returns the canned hold and counts calls; `canAddSeat` returns false when the hold has
10 or more seats):

```java
@Test
void rejectsTheEleventhSeat() {
    MockHoldRepository mock = new MockHoldRepository(new Hold(8, 10));
    SeatHoldService service = new SeatHoldService(mock);

    service.canAddSeat(8);

    assertEquals(1, mock.findByIdCallCount);
}
```

The test is green. (a) Why can it stay green even if the rule wrongly allows an eleventh
seat? (b) Write the assert that would catch that bug. (c) Which double belongs here?

**Answer.** (a) Because the assert only counts lookups, and a service with a broken rule
still looks up exactly once — the call count does not depend on the rule at all. (The
`<= 10` experiment in Lesson 4 showed precisely this test shape staying green while four
state checks went red.) (b) `assertFalse(service.canAddSeat(8));` — assert the decision,
not the lookup. (c) The **fake**: save `new Hold(8, 10)` into it and assert the refusal.
The repository is incidental, so the fake keeps the test about the rule. (A stub with
the same canned hold is also acceptable; the interaction assert is what had to go.)

### Do it yourself — Lesson 7 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–6 passing (20
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. Create the five files from this lesson's example section: `Money.java`,
   `PaymentGateway.java`, `OrderCancelService.java` in
   `src/main/java/com/zenith/doubles/`; `MockPaymentGateway.java`,
   `L7ChoosingTest.java` in `src/test/java/com/zenith/doubles/`.
3. Run `mvn test`. Expected: `L7ChoosingTest` shows `Tests run: 3`, total
   `Tests run: 23, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
4. Cause a double refund: in `OrderCancelService.java`, duplicate the
   `paymentGateway.refund(orderId);` line. Run `mvn test`. Expected:
   `L7ChoosingTest.cancellingRefundsExactlyOnceWithAMock:35 expected: <1> but was: <2>`,
   `Tests run: 23, Failures: 1`. The exactly-once assert caught a double payout.
5. Remove the duplicate. Run `mvn test` — 23 green.

**Done when:** all 23 pass and you can recite the three decisions with one-line reasons.

---

## Lesson 8 — The boundary rule: mock at boundaries you own (serves S4)

### Definition

A **boundary** is an edge of your program — where it talks to something outside: a
database, a payment provider, an email server, a clock, another team's service. **You
own** a type when you wrote it and control it. A boundary interface you define yourself
is also called a **port**: named after what your app needs (`HoldRepository`,
`PaymentGateway`), not after the technology behind it.

**The rule (the S4 deliverable):**

> **Mock at boundaries you own.** Double only interfaces you defined — repository ports
> and outbound gateways. Do not mock the framework. Do not mock types you do not own.
> Do not mock value objects. When you need a third-party client, wrap it behind your own
> interface, then double the interface.

### Why it is needed

1. **Mocking types you do not own** (checkpoint question 3). A double of someone else's
   class encodes your *guess* about its contract: returns, exceptions, what counts as
   success. Tests pass against the guess; production runs against reality. Example: a
   vendor's email client returns 250 on success; your double guesses 200; the service
   treats 200 as success. All tests green, every real email mishandled. And when the
   vendor changes behaviour in an upgrade, your mocks keep mimicking the old version —
   the suite stays green exactly when it should fail.
2. **Mocking the framework.** Spring and JPA types are huge and not yours; mocks of them
   re-implement the framework badly and break on upgrades.
3. **Mocking value objects.** `new Money(100)` is faster than any double and behaves
   like production. A double can only be equal or worse.

### Key points

- Spot the edge → define your own small interface → one adapter wraps the vendor → tests
  double your interface.
- The SDK appears in exactly one production file and in no test.
- The thin adapter is not unit tested with doubles; it gets an integration test against
  the real system.
- Your port speaks only your types — leaking a vendor type through it hands the boundary
  back.
- The project already follows the rule: `HoldRepository` and `PaymentGateway` are owned
  ports; every double written in this task implements an owned interface.

### The example

The pretend vendor class — note `final`:

`src/main/java/com/zenith/doubles/thirdparty/ProviderSdkClient.java`:

```java
package com.zenith.doubles.thirdparty;

/**
 * PRETEND THIRD-PARTY CODE. Imagine this class arrived inside a payment provider's
 * SDK jar. You cannot edit it, you do not control when it changes, and it is final,
 * so you cannot even subclass it. You do not own this type.
 */
public final class ProviderSdkClient {

    /** Pretend: signs the payload and POSTs it to the provider over HTTPS. */
    public String createRefundRequest(String merchantId, String payloadJson) {
        return "{\"status\":\"QUEUED\"}";
    }
}
```

The forbidden move, attempted for real:

```java
class DoubledSdkClient extends ProviderSdkClient {
}
```

Real compiler output:

```text
[ERROR] DoubledSdkClient.java:[9,32] cannot inherit from final com.zenith.doubles.thirdparty.ProviderSdkClient
```

`final` on a class forbids subclassing. (Mockito has tricks to force doubles onto such
classes; even where possible, the rule says do not — the guessed-contract problem stays.)

The right move — the adapter:

`src/main/java/com/zenith/doubles/SdkPaymentGateway.java`:

```java
package com.zenith.doubles;

import com.zenith.doubles.thirdparty.ProviderSdkClient;

/**
 * The wrapper (adapter): the ONLY class allowed to touch the provider's SDK.
 * It implements the boundary interface we own, and translates our need
 * ("refund order 42") into the SDK's language (merchant ids, JSON strings).
 * Deliberately too thin to unit test; it gets an integration test later.
 */
public class SdkPaymentGateway implements PaymentGateway {

    private final ProviderSdkClient client;
    private final String merchantId;

    public SdkPaymentGateway(ProviderSdkClient client, String merchantId) {
        this.client = client;
        this.merchantId = merchantId;
    }

    @Override
    public void refund(long orderId) {
        client.createRefundRequest(merchantId, "{\"orderId\":" + orderId + "}");
    }
}
```

The tests. `assertDoesNotThrow(() -> ...)` passes if the code inside runs without
throwing; the arrow is a lambda, a no-name function passed as a value.

`src/test/java/com/zenith/doubles/L8BoundaryTest.java`:

```java
package com.zenith.doubles;

import com.zenith.doubles.thirdparty.ProviderSdkClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lesson 8: mock at boundaries you own.
 * The service test doubles PaymentGateway — the interface WE defined.
 * The provider's SDK class appears in exactly one production class (the adapter),
 * and never in a service test.
 */
class L8BoundaryTest {

    @Test
    void serviceTestsDoubleTheOwnedBoundaryOnly() {
        MockPaymentGateway gateway = new MockPaymentGateway(); // double of OUR interface
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        assertEquals(1, gateway.refundCallCount);
        // Note what is absent: no ProviderSdkClient, no JSON, no merchant id.
        // The service test knows nothing about the SDK. That is the rule working.
    }

    @Test
    void theAdapterSatisfiesTheOwnedInterface() {
        // Production wiring: the adapter wraps the real SDK client behind our interface.
        PaymentGateway gateway = new SdkPaymentGateway(new ProviderSdkClient(), "MERCHANT-77");

        // A smoke check only: it plugs in and runs. Its real test is an
        // integration test against the provider's sandbox, later.
        assertDoesNotThrow(() -> gateway.refund(42));
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L8BoundaryTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zenith.doubles.L8BoundaryTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Walkthrough — one refund, two worlds

1. **Production:** `cancel(42)` → `paymentGateway.refund(42)` → `SdkPaymentGateway`
   translates (merchant id, JSON) → SDK → HTTPS to the provider. All SDK knowledge in
   one file.
2. **Test:** the same `cancel(42)`, but the field holds `MockPaymentGateway`. Counted
   and remembered; no JSON, no network.
3. The service is identical in both worlds; it compiled against `PaymentGateway` only.
4. Payoffs: vendor ships SDK v2 → one file changes, tests stay green honestly. Company
   switches providers → new adapter, service and tests untouched.

### Important terms

| Term | Meaning |
| --- | --- |
| Boundary | An edge where your program talks to something outside. |
| Own | You wrote it and control it; reshaping it breaks nothing outside your codebase. |
| Port | A boundary interface you define, named after your need, not the technology. |
| Adapter | The one production class implementing your port by calling the vendor's client. |
| `final` (on a class) | Forbids subclassing — `cannot inherit from final`. |
| `assertDoesNotThrow(() -> ...)` | Passes if the lambda runs without throwing. |

### Where it is used

Every edge of a real codebase: your repository interface in front of JPA (Phase 1 does
this; the in-memory fake doubles it), gateway interfaces in front of payment/SMS/email
SDKs, a clock interface in front of the system clock, client interfaces in front of
other teams' HTTP services.

### Common mistakes

- **Mocking framework types** (`JdbcTemplate`, `EntityManager`): huge setup, breaks on
  upgrades, proves nothing. Port in front.
- **Mocking a vendor class because it is non-final.** Possible is not permitted — the
  guessed contract problem is identical. Wrap it.
- **Owning the interface in name only.** If your interface mirrors the SDK's shape (JSON
  strings, merchant ids), you copied their boundary. Shape it by your need:
  `refund(orderId)`.
- **Leaking foreign types through the port.** Returning `ProviderSdkResponse` hands the
  boundary back to the vendor.
- **Unit-testing the thin adapter with doubles.** Integration test it against the real
  sandbox instead.

### Your doubts

None raised for this lesson.

### Summary for revision

- The rule: mock at boundaries you own; never the framework, never foreign types, never
  value objects; wrap third-party clients behind your own interface and double that.
- Foreign mocks = guessed contracts = tests that stay green when reality changes.
- Port speaks your types; adapter is the only SDK-toucher; adapter gets integration
  tests.
- `cannot inherit from final` is the compiler refusing the forbidden double for you.

### Practice questions (with answers)

**Question 1.** For each candidate: "double it", "use the real thing", or "wrap it
first"?

1. `HoldRepository` — your own interface (`findById`, `save`, `deleteById`) in front of
   your database.
2. `JdbcTemplate` — a class from the Spring framework that runs SQL.
3. `Money` — your own `record Money(long paise)` with a `plus` method.
4. `WhatsAppSdkClient` — a final class from a vendor's jar, with
   `postMessage(String apiKey, String payloadJson)`.

**Answer.**

1. **Double it.** An owned port at a boundary — exactly what doubles are for.
2. **Wrap it first.** Framework type you do not own. Your repository port sits in front;
   the class that uses `JdbcTemplate` is an adapter, covered by integration tests.
3. **Use the real thing.** Value object: cheap, deterministic, dependency-free.
4. **Wrap it first.** Foreign and final — the compiler will not even allow a subclass.
   Define your own `MessageSender`-style interface shaped by your need, write one
   adapter around the SDK, and double your interface in tests.

**Question 2.** A teammate's service sends booking confirmations through a vendor's
(non-final) `EmailSdk` class, used directly with no wrapper. The real `send` returns
status `250` on success; the teammate's hand-written double returns `200`; the service
treats only `200` as success. All unit tests are green. (a) What happens in production,
and why did no test catch it? (b) Which part of the boundary rule was broken? (c) What
is the fix?

**Answer.** (a) In production every successful send returns 250, the service treats that
as failure, and correct emails get logged as errors or retried — possibly sending
customers duplicates. No test caught it because the double returned the guessed 200: the
tests verified the service against the teammate's guess about the SDK, not against the
SDK. A mock of a foreign type can only ever echo its author's beliefs. (b) "Do not mock
types you do not own." (c) Two steps. First, define an owned port shaped by the need,
for example `interface MessageSender { void send(String to, String subject, String body); }` —
note it exposes no status codes at all; translating 250-means-success is the adapter's
job, in one place. Second, write an `EmailSdkMessageSender` adapter — the only class
touching `EmailSdk` — and double `MessageSender` in unit tests. The adapter's
translation is checked by an integration test against the vendor's sandbox.

### Do it yourself — Lesson 8 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–7 passing (23
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. Make the vendor folder and create the three files from this lesson's example section:

```bash
mkdir -p src/main/java/com/zenith/doubles/thirdparty
```

`ProviderSdkClient.java` (in the `thirdparty` folder), `SdkPaymentGateway.java` (main),
`L8BoundaryTest.java` (test).

3. Run `mvn test`. Expected: `L8BoundaryTest` shows `Tests run: 2`, total
   `Tests run: 25, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
4. Try the forbidden move. Create `src/test/java/com/zenith/doubles/DoubledSdkClient.java`:

```java
package com.zenith.doubles;

import com.zenith.doubles.thirdparty.ProviderSdkClient;

class DoubledSdkClient extends ProviderSdkClient {
}
```

Run `mvn test`. Expected:
`cannot inherit from final com.zenith.doubles.thirdparty.ProviderSdkClient`,
`BUILD FAILURE`. The vendor closed the door; wrapping is the way, not tricks.

5. Delete `DoubledSdkClient.java`. Run `mvn test` — 25 green.

**Done when:** all 25 pass and you can say the rule from memory, with its three do-nots.

## Lesson 9 — Why everyone says "mock" (serves S5, the Ask-AI exercise, CP)

### Definition

In everyday speech, "mock" means **any test double** — when a teammate says "mock the
repository", they mean "put a stand-in there", not specifically a call-counting mock.
Mockito, the standard Java mocking library, reinforces this: its `mock()` method builds
a blank double that can act as a stub, a mock, or (via `spy()`) a spy. The **role** is
decided by what the test does with the object, not by the word. The four precise names
matter at exactly one moment: when you decide what to assert.

### Why it is needed

Two real failure modes for people who skip this lesson:

1. **Taking the word literally.** Someone hears "mock the repository" and concludes the
   test must verify interactions — producing the "repository was called" test that
   proves nothing. The word says "mock"; the meaning is "double"; you still run the
   choosing procedure before writing the assert.
2. **Reading Mockito blind.** Most tests in real codebases are Mockito tests. Without
   the decoder, `mock()` plus `when(...).thenReturn(...)` looks like "a mock" — but it
   is functionally a **stub**, and the right assertion for it is a state check. Misread
   that, and you misjudge what a whole suite actually proves.

### Key points

- Colloquially, "mock" = any double. Precisely, a mock is the double you verify calls on.
- Mockito's `mock(X.class)` makes a blank double: methods do nothing and return empty
  defaults (for `Optional` return types, the empty box).
- `when(...).thenReturn(...)` cans an answer → the object plays the **stub** role →
  assert state.
- `verify(x, times(1)).method(args)` checks the call log afterwards → the **mock** role
  → assert the interaction.
- `spy(realObject)` wraps a real object → the **spy** role → real behaviour plus a call
  record; both kinds of assert are honest.
- **Mockito cannot make a fake.** A fake is a genuine lightweight implementation; it
  stays hand-written. That is why this task says to keep `FakeHoldRepository`.

### How it works — the decoder

| You hand-wrote | Mockito equivalent | The role | Suits which assertion |
| --- | --- | --- | --- |
| `StubHoldRepository` | `mock(X.class)` + `when(...).thenReturn(...)` | Stub | State (`assertTrue`, `assertEquals` on results) |
| `MockHoldRepository` | `mock(X.class)` + `verify(...)` after acting | Mock | Interaction (`verify(x, times(1)).refund(42)`) |
| `FakeHoldRepository` | **None — Mockito cannot make fakes.** | Fake | State, via genuine read-back |
| `SpyHoldRepository` | `spy(realObject)` | Spy | Both, honestly |

One pleasant detail: with Mockito the variable can be typed as the plain interface
(`HoldRepository repository = mock(...)`), because the call memory lives inside
Mockito's machinery, not in public fields you must reach — unlike the hand-written mock,
which needed its concrete type visible.

### The example — all four doubles in one test class

The pom grows by one dependency, next to the JUnit one:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.17.0</version>
    <scope>test</scope>
</dependency>
```

This is the task's "Ask AI" exercise, asked and answered for real — one example each of
a stub, mock, fake, and spy in the same test class, with the assertion style that suits
each. `src/test/java/com/zenith/doubles/L9AllFourTest.java`:

```java
package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lesson 9: all four doubles in one test class, now with Mockito.
 * Mockito's mock() makes a blank double. What you DO with it decides its role:
 *   thenReturn(...)  -> it acts as a stub   -> assert state.
 *   verify(...)      -> it acts as a mock   -> assert the interaction.
 *   spy(realThing)   -> it acts as a spy    -> real behaviour, calls recorded.
 * Mockito cannot make a fake: a fake is a real implementation, written by hand.
 */
class L9AllFourTest {

    @Test
    void stubRoleCannedAnswerStateAssert() {
        HoldRepository repository = mock(HoldRepository.class);          // blank double
        when(repository.findById(7)).thenReturn(Optional.of(new Hold(7, 4))); // canned

        SeatHoldService service = new SeatHoldService(repository);

        assertTrue(service.canAddSeat(7)); // state assertion suits a stub
    }

    @Test
    void mockRoleCallMemoryInteractionAssert() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        verify(gateway, times(1)).refund(42); // interaction assertion suits a mock
    }

    @Test
    void fakeRoleStaysHandWrittenStateAssert() {
        FakeHoldRepository fake = new FakeHoldRepository(); // Mockito cannot make fakes
        SeatHoldService service = new SeatHoldService(fake);

        fake.save(new Hold(8, 10));

        assertFalse(service.canAddSeat(8)); // state assertion via genuine read-back
    }

    @Test
    void spyRoleRealBehaviourPlusCallRecord() {
        HoldRepository repository = spy(new FakeHoldRepository()); // wraps a real object

        SeatHoldService service = new SeatHoldService(repository);
        repository.save(new Hold(7, 4));   // forwarded: really stored underneath

        assertTrue(service.canAddSeat(7));         // state: real answer from underneath
        verify(repository, times(1)).findById(7);  // interaction: the call was recorded
    }
}
```

Real output from `mvn test` (trimmed):

```text
[INFO] Running com.zenith.doubles.L9AllFourTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.489 s -- in com.zenith.doubles.L9AllFourTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The run also prints warnings like `Mockito is currently self-attaching...` and
`WARNING: A Java agent has been loaded dynamically`. That is Mockito loading its
bytecode tooling into the running JVM — normal today; the tests pass. Configuring it
"properly" as a build agent is a later task's concern.

### Walkthrough — the four tests, translated with the decoder

1. **Stub role.** `mock(HoldRepository.class)` made a blank double; `thenReturn` canned
   one answer. The variable is *called* a mock; functionally this is
   `StubHoldRepository`. So the assert is a state check on the service's answer — and
   there is no `verify` anywhere in that test.
2. **Mock role.** No `thenReturn` at all (`refund` is void — nothing to can). The whole
   test is act, then `verify(gateway, times(1)).refund(42)`. This is
   `MockPaymentGateway`, library edition.
3. **Fake role.** Pure hand-written `FakeHoldRepository` — Mockito has nothing here,
   because a fake is a genuine implementation, not a recording shell. No library
   replaces it.
4. **Spy role.** `spy(new FakeHoldRepository())` wraps the real fake. `save` forwarded
   for real; the service got a real answer; `verify` read the call log.
   `SpyHoldRepository`, library edition.

**The experiment — same bug, richer story.** The double-refund bug was planted again
(`paymentGateway.refund(orderId);` duplicated inside `cancel`). The hand-written mock
said `expected: <1> but was: <2>`. Mockito said:

```text
org.mockito.exceptions.verification.TooManyActualInvocations:

paymentGateway.refund(42L);
Wanted 1 time:
-> at com.zenith.doubles.L9AllFourTest.mockRoleCallMemoryInteractionAssert(L9AllFourTest.java:42)
But was 2 times:
-> at com.zenith.doubles.OrderCancelService.cancel(OrderCancelService.java:16)
-> at com.zenith.doubles.OrderCancelService.cancel(OrderCancelService.java:17)
```

Same catch — but it names the call, the wanted count, and points at **both offending
lines** in the service. That message quality is a big part of why libraries exist, and
the hand-written week is what lets you read it fluently. (Bug reverted; 29 green.)

### Important terms

| Term | Meaning |
| --- | --- |
| Mockito | The standard Java library for making test doubles at runtime. |
| `mock(X.class)` | Builds a blank double of `X`: methods do nothing, return empty defaults. |
| `when(...).thenReturn(...)` | Cans an answer — the stubbing line. |
| `verify(x, times(1)).m(args)` | Asserts afterwards that `m(args)` was called exactly once. |
| `spy(realObject)` | Wraps a real object: forwards every call, records every call. |
| `TooManyActualInvocations` | Mockito's failure when a call happened more times than `verify` wanted. |
| Java agent warning | Mockito attaching its bytecode tooling to the JVM — normal, ignorable here. |

### Where it is used

- **Every standup and code review:** "mock the gateway" — translate to "double it",
  then choose properly.
- **Interviews:** defining all four precisely, then saying "colloquially we call them
  all mocks", is a cheap seniority signal.
- **Reading real codebases:** most tests you will ever read are Mockito tests; the
  decoder tells you what each one actually proves.
- **Later tasks:** Mockito in depth (annotations, argument matchers) comes later; the
  mental model is now in place.

### Common mistakes

- **Verifying because it is called a mock.** `mock()` does not oblige you to `verify()`.
  A Mockito stub test ends with a state assert, full stop.
- **Stubbing and verifying the same call.** `when(repository.findById(7))...` then
  `verify(repository).findById(7)` — the verify adds nothing the state assert did not
  already prove, and doubles the breakage surface.
- **Building a fake out of `when` chains.** Simulating storage with piles of
  `thenReturn` scripts is unreadable and wrong the moment order matters. The
  hand-written `HashMap` fake wins every time.
- **Letting the loose word blur the decision.** Say "mock" out loud all you like — but
  in your head, run the choosing procedure before writing the assert.

### Link to the subtask

This lesson completes **S5**: the vocabulary note is written — people say "mock" for
all four, Mockito's `mock()` can act as any of them, and the distinction lives on in
what you assert. It also completes the **Ask-AI exercise**: `L9AllFourTest` is one
example of each double in the same class, with the assertion style suited to each. With
S1–S4 done in Lessons 1–8, every subtask of F7.3 is complete.

### Your doubts

None raised for this lesson.

### Summary for revision

- Speech: "mock" = any double. Precision returns at assert time.
- Decoder: `thenReturn` → stub role → state assert; `verify` → mock role → interaction
  assert; `spy(real)` → spy role → both; fake → hand-written, no library equivalent.
- Mockito's blank mock returns empty defaults until stubbed.
- Mockito's failure messages name the call, the wanted count, and the offending lines.

### Practice questions — the task checkpoint (with answers)

These are the four checkpoint questions from the task sheet.

**Question 1.** Define stub, mock, fake, and spy — one sentence each.

**Answer.**

- A **stub** is a double that returns canned answers and remembers nothing.
- A **mock** is a double that also records the calls made on it, so the test can assert
  that an interaction happened (for example, exactly once).
- A **fake** is a genuine but lightweight working implementation — like an in-memory
  `HashMap` repository — whose behaviour is real, just cheap.
- A **spy** wraps a real object: it forwards every call to the real thing and records
  the calls as they pass through.

**Question 2.** You must test that a refund is requested exactly once when an order is
cancelled. Which double do you use, and why?

**Answer.** A **mock** of the payment gateway. The requirement is about an
interaction — that `refund` is *called*, with the right order id, exactly once — and
there is no state inside the service to read back afterwards. Only a double with call
memory can prove "exactly once": a stub has no memory, and a fake would need bolted-on
counters, which just turns it into a mock. The assert is
`verify(gateway, times(1)).refund(orderId)` (library) or a check on a recorded call
count (hand-written). The exactly-once part matters because a double refund costs real
money — `times(1)` fails both when the call is missing and when it happens twice.

**Question 3.** Why should you never mock a type you do not own?

**Answer.** Because the double can only echo your **guess** about how the foreign type
behaves, and nothing ever corrects the guess. If the vendor's client actually returns a
different status, throws where you assumed a return, or changes behaviour in an upgrade,
your double keeps replying with the old guess and the tests stay green while production
breaks. Foreign and framework types are also wide and awkward to double, and some are
`final` — the compiler refuses with `cannot inherit from final`. The fix is to wrap:
define a small interface you own, shaped by your need (`refund(orderId)`), write one
adapter class that touches the vendor's SDK, double your own interface in unit tests,
and cover the thin adapter with integration tests against the vendor's sandbox.

**Question 4.** What is the difference between state testing and interaction testing?

**Answer.** **State testing** checks the *outcome*: after acting, read a result or
stored value and compare it with what should be true — "the eleventh seat was refused".
**Interaction testing** checks the *conversation*: whether the object under test called
its dependency, with which arguments, how many times — "refund was called exactly once".
State testing is the default because it survives refactoring (any implementation
producing the right outcome passes). Interaction testing is reserved for when the call
itself is the requirement and no readable state exists — like a refund request leaving
for an external gateway. Wrongly used, each is worse than nothing: interaction tests of
internals break on every refactor, and state tests cannot see a missing or duplicated
outbound call.

### Do it yourself — Lesson 9 (second laptop)

Continues the project in `~/zenith/test-doubles/scratch-app`, Lessons 1–8 passing (25
tests).

1. `cd ~/zenith/test-doubles/scratch-app`
2. In `pom.xml`, add the Mockito dependency inside `<dependencies>`, next to the JUnit
   one:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.17.0</version>
    <scope>test</scope>
</dependency>
```

3. Create `src/test/java/com/zenith/doubles/L9AllFourTest.java` as shown in this
   lesson's example section.
4. Run (the first run downloads Mockito, so it needs internet):

```bash
mvn test
```

Expected: warnings about a Java agent / Mockito self-attaching (normal — ignore),
`L9AllFourTest` shows `Tests run: 4`, total `Tests run: 29, Failures: 0, Errors: 0`,
`BUILD SUCCESS`.

5. See Mockito catch the double refund. In `OrderCancelService.java`, duplicate the
   `paymentGateway.refund(orderId);` line. Run `mvn test`. Expected:

```text
org.mockito.exceptions.verification.TooManyActualInvocations:
paymentGateway.refund(42L);
Wanted 1 time:
-> at ...L9AllFourTest.mockRoleCallMemoryInteractionAssert(L9AllFourTest.java:42)
But was 2 times:
-> at ...OrderCancelService.cancel(OrderCancelService.java:16)
-> at ...OrderCancelService.cancel(OrderCancelService.java:17)
```

| Piece | Meaning |
| --- | --- |
| `TooManyActualInvocations` | A verify lost: more calls happened than wanted. |
| `Wanted 1 time` / `But was 2 times` | The promise and the reality — `expected: <1> but was: <2>` with a story. |
| The two `-> at OrderCancelService.cancel` lines | Mockito points at the exact lines that made the extra calls. |

6. Remove the duplicate line. Run `mvn test` — 29 green.

**Done when:** all 29 pass and you have answered the four checkpoint questions out loud.

## Task F7.3 — wrap-up

All nine lessons are documented above. Checking the task's "done when" list:

- **All four doubles hand-written for `HoldRepository` (S1):** `StubHoldRepository`
  (plus `StubMissingHoldRepository`), `MockHoldRepository`, `FakeHoldRepository`,
  `SpyHoldRepository` — written, tested, each caught a planted bug.
- **The in-memory fake kept (S2):** `FakeHoldRepository` (a `HashMap` behind the
  interface) carries multi-step scenarios and even the Lesson 9 spy wraps it.
- **Three situations justified (S3):** eleventh-seat rejection → fake (state);
  refund-exactly-once → mock (interaction); `Money.plus` → no double (real value
  object). All three written as tests in `L7ChoosingTest`.
- **The boundary rule written (S4):** mock at boundaries you own; never the framework,
  never foreign types, never value objects; wrap third-party clients behind an owned
  port and double that.
- **The vocabulary note (S5) and Ask-AI exercise:** Lesson 9, with `L9AllFourTest`.
- **Checkpoint:** the four questions, with full answers, are in Lesson 9's practice
  section.

Final state of the scratch project: 29 tests, all green, across nine test classes —
run with `mvn test` inside `~/zenith/test-doubles/scratch-app`.

# F6.2 — Constructor injection, and why `@Autowired` on fields is discouraged

Notes for Task F6.2. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state:** Java 21 (`java -version` → `openjdk version "21...`) and
Maven 3.9.x — one copy sits unpacked at
`F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/tools/apache-maven-3.9.9`. No database
is needed for this task. The scratch project lives in this task's own folder at
`scratch-app/` (package `com.zenith.holds`); Lesson 1 describes every file in it.

## The task's subtasks

| Id | Subtask |
|----|---------|
| S1 | Write `SeatHoldService` three ways in a scratch package: constructor, `@Autowired` field, setter. All three run. |
| S2 | Compare the three on honesty, immutability, testability. One own sentence for each in `NOTES.md`. |
| S3 | Write the discouraged/preferred pair side by side and keep it. |
| S4 | Learn the one-constructor rule: one constructor → `@Autowired` redundant; two or more → mark one. |
| S5 | Break it: the half-built object. `new SeatHoldService()` on the field version → `NullPointerException`; the constructor version will not even compile without the repository. |
| S6 | Break it: the circle. `SeatHoldService` ↔ `OrderService` by constructor → read the cycle error; then both by field → see current Boot still reject it. |
| S7 | Fix the circle properly: pull shared behaviour into a third class. A cycle is a design message. |
| S8 | Ask AI: why Spring can inject into a private field at all, what reflection does there, and the testing cost. |
| CP | Checkpoint: four questions answered out loud. |

## Roadmap for this task — 7 small lessons

| # | Lesson | What it gives you |
|---|--------|-------------------|
| 1 | The stage: `SeatHoldService`, constructor-injected *(S1 part)* | A fresh tiny scratch app (no database) and the precise meaning of constructor injection. |
| 2 | The same class two more ways *(S1 complete)* | Field injection and setter injection, both running. The framework does not stop you. |
| 3 | Honesty and immutability *(S2 part, S3)* | The side-by-side pair; why `final` is only possible with constructors; why ugly constructors are useful information. |
| 4 | Testability: the plain JUnit test *(S2 complete)* | `new SeatHoldService(fake)` in a test with no Spring at all, running in milliseconds. |
| 5 | The one-constructor rule *(S4)* | When `@Autowired` is redundant, when it is required, and the real error if you get it wrong. |
| 6 | Break it: the half-built object *(S5, S8)* | The `NullPointerException` the field version hides, the compile error the constructor version gives you instead, and what reflection is. |
| 7 | Break it: the circle — then fix it properly *(S6, S7, CP)* | The cycle error with constructors and with fields; the third-class fix; the four checkpoint answers; final audit. |

---

## Lesson 1 — The stage: `SeatHoldService`, constructor-injected (subtask S1, part one)

### Definition

**Constructor injection**: a class's dependencies arrive as **constructor parameters**,
so the object cannot be created without them. This lesson builds the task's class —
`SeatHoldService` from a ticket-sale system, whose job is holding seats — the
constructor way, inside a fresh tiny scratch app (package `com.zenith.holds`, no
database, no web server: it starts, prints, exits).

### Why it is needed

The task compares three ways of receiving dependencies. A comparison needs a reference
point, and the constructor version *is* the reference point — the form the task argues
for. Everything later (field version, setter version, the breakages) is measured
against this file.

### How it works

1. The class declares a `private final` field for the helper it needs.
2. The **constructor takes the helper as a parameter** and stores it in the field. The
   constructor is the only door in.
3. At startup, Spring reads the constructor's parameter list like a shopping list,
   finds a matching bean in the application context (the box of objects Spring built),
   and hands it in. If the list cannot be filled, startup fails loudly, before any user
   shows up.

### The whole app

`scratch-app/pom.xml` — Boot 3.5.16, core starter only, plus the test kit for later
lessons:

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
    <artifactId>holds-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

The interface:

```java
package com.zenith.holds;

import java.util.List;

public interface HoldRepository {
    void save(String seatId);

    List<String> allHeldSeats();
}
```

An in-memory implementation (marked `@Repository`, so the scan writes a recipe card):

```java
package com.zenith.holds;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryHoldRepository implements HoldRepository {
    private final List<String> held = new ArrayList<>();

    @Override
    public void save(String seatId) {
        held.add(seatId);
    }

    @Override
    public List<String> allHeldSeats() {
        return List.copyOf(held);
    }
}
```

The star — constructor injection, nothing else:

```java
package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class SeatHoldService {
    private final HoldRepository holds;

    public SeatHoldService(HoldRepository holds) {
        this.holds = holds;
    }

    public String hold(String seatId) {
        holds.save(seatId);
        return "Seat " + seatId + " held (" + holds.allHeldSeats().size() + " seat(s) now on hold)";
    }
}
```

The main class:

```java
package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        SeatHoldService service = context.getBean(SeatHoldService.class);
        System.out.println(service.hold("A1"));
        System.out.println(service.hold("A2"));
    }
}
```

Real run (`mvn spring-boot:run`), real output:

```text
Started HoldsApplication in 0.286 seconds (process running for 0.4)
Seat A1 held (1 seat(s) now on hold)
Seat A2 held (2 seat(s) now on hold)
```

The count going 1 → 2 proves both calls hit the **same** repository instance — the one
Spring built once and handed in.

### Walkthrough

1. The scan covers `com.zenith.holds` downward, finds the two marks, writes two recipe
   cards.
2. Cooking `SeatHoldService`: its constructor wants a `HoldRepository` — an interface —
   so Spring finds the bean whose class implements it (`inMemoryHoldRepository`),
   builds it first, and passes it through the constructor door.
3. `this.holds = holds;` stores it in a `final` field — set once, never replaceable.
4. `main` asks the box for the finished service and holds two seats.

The definition against the code: *dependencies arrive as constructor parameters* — the
`HoldRepository holds` parameter; *the object cannot be created without them* — there is
exactly one constructor and it demands a repository, so no code anywhere can make this
object empty-handed. Not Spring, not a test, not anyone.

### Where it is used

`private final` field + one constructor + no annotation on it is the standard shape of
nearly every service, repository, and controller in modern Spring codebases — the form
reviewers expect by default.

### Common mistakes

- **Expecting `@Autowired` on the constructor.** Older tutorials show it. With exactly
  one constructor it is pure noise — a later lesson proves the rule and its failure
  case.
- **Thinking injection needs "real" infrastructure.** No database, no web — injection
  is only about who fills the constructor slots.
- **Forgetting `final` on the field.** Works, but gives away a free guarantee.
  `final` + constructor is the pair.

### Link to the task

The first third of **S1** ("write `SeatHoldService` three ways") — the constructor
version, running. Also the yardstick for every comparison to come.

### Do it yourself (second laptop)

**Setup:** Java 21 and Maven installed (same as the F6.1 scratch app; no Docker, no
Postgres).

1. Make the folders: `scratch-app/src/main/java/com/zenith/holds/`.
2. Create `pom.xml` and the four Java files exactly as above.
3. From `scratch-app/`: `mvn spring-boot:run`.
4. Expect: `Started HoldsApplication...`, then
   `Seat A1 held (1 seat(s) now on hold)`, then
   `Seat A2 held (2 seat(s) now on hold)`.

**Done when:** both seat lines print with the count rising 1 → 2, and you can point at
the constructor and say "this is the only way into this object."

### Summary for revision

- Constructor injection = dependencies as constructor parameters; the object cannot
  exist without them.
- The pair: `private final` field + constructor assignment.
- Spring fills the slots from the box at startup; an unfillable list stops startup.
- The count rising across calls proves one shared bean behind every door.

### Check questions, with answers

**Question 1.** (The `SeatHoldService` code as above.) The definition of constructor
injection has two halves: *"dependencies arrive as constructor parameters"* and *"the
object cannot be created without them."* Point at the exact piece of the code that makes
each half true — and say **who** enforces the second half: Java or Spring?

**Answer.** First half: the parameter `HoldRepository holds` of
`public SeatHoldService(HoldRepository holds)` — the dependency arrives there, and
nowhere else. Second half: the fact that this is the **only** constructor.
`new SeatHoldService()` is rejected by the **compiler** — Java itself — with "required:
HoldRepository, found: no arguments". Spring is not the enforcer; it is just another
caller that must come through the same door with a repository in hand.

**Question 2.** A teammate wrote this class, which receives nothing — it builds its own
helper:

```java
package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class ReminderService {
    private final InMemoryHoldRepository holds = new InMemoryHoldRepository();

    public String remind() {
        return "Reminding about " + holds.allHeldSeats().size() + " held seat(s)";
    }
}
```

Name the exact code changes that convert it to constructor injection, and one concrete
thing that becomes possible after the change that is impossible now.

**Answer.** Three changes: (1) the field becomes the interface type with no value:
`private final HoldRepository holds;` (2) a constructor is added:
`public ReminderService(HoldRepository holds) { this.holds = holds; }` (3) the
`= new InMemoryHoldRepository()` is deleted. Newly possible: handing in a different
repository — a fake preloaded with seats in a plain test, for instance. And the change
fixes a real bug hiding in the original: its private `new` repository was a **second,
separate copy**, permanently empty — `remind()` would report 0 seats forever, no matter
how many seats the rest of the app held, because everyone else shares the single
repository bean while this class secretly owned its own. After the change, Spring hands
`ReminderService` the same shared bean as everyone else.

---

## Lesson 2 — The same class, two more ways: field and setter injection (subtask S1, complete)

### Definition

- **Field injection:** Spring creates the object first, then reaches into it and sets a
  private field directly — bypassing the constructor. Marked with `@Autowired` on the
  field.
- **Setter injection:** the dependency arrives through an ordinary setter method, which
  Spring calls after creating the object. Marked with `@Autowired` on the setter.

### Why it is needed

The task's own words: *"All three run. The framework is not stopping you."* If the other
forms crashed, nobody would need convincing. They work — so the case for constructors
rests on qualities (honesty, immutability, testability), and the comparison only means
something once the alternatives are seen genuinely working.

### How each works — the timeline is everything

**Constructor injection:** the repository exists **before** the service; the service is
born complete. No moment exists where the object lacks its repository.

**Field injection** flips the order:

1. Spring creates the object with the **no-argument constructor** — the invisible
   default Java gives every class that declares none.
2. The object now **exists with `holds` = `null`** — a half-built object.
3. Spring writes the private field directly, using **reflection**: Java's ability to
   inspect and modify a class at runtime, including private parts.

**Setter injection:** same flip, politer mechanism — created empty, then Spring calls
`setHolds(...)`.

### The two new files, run for real

`FieldSeatHoldService.java`:

```java
package com.zenith.holds;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FieldSeatHoldService {
    @Autowired
    private HoldRepository holds;   // Spring sets this AFTER the object exists

    public String hold(String seatId) {
        holds.save(seatId);
        return "Seat " + seatId + " held via field injection (" + holds.allHeldSeats().size() + " total)";
    }
}
```

`SetterSeatHoldService.java`:

```java
package com.zenith.holds;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SetterSeatHoldService {
    private HoldRepository holds;

    @Autowired
    public void setHolds(HoldRepository holds) {   // Spring calls this AFTER the object exists
        this.holds = holds;
    }

    public String hold(String seatId) {
        holds.save(seatId);
        return "Seat " + seatId + " held via setter injection (" + holds.allHeldSeats().size() + " total)";
    }
}
```

`HoldsApplication.main` exercising all three:

```java
ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
System.out.println(context.getBean(SeatHoldService.class).hold("A1"));
System.out.println(context.getBean(FieldSeatHoldService.class).hold("A2"));
System.out.println(context.getBean(SetterSeatHoldService.class).hold("A3"));
```

Real output:

```text
Started HoldsApplication in 0.289 seconds (process running for 0.4)
Seat A1 held (1 seat(s) now on hold)
Seat A2 held via field injection (2 total)
Seat A3 held via setter injection (3 total)
```

All three work — and the count climbing 1 → 2 → 3 across three different services proves
every door (constructor, field, setter) delivered the **same single repository bean**.

### The three timelines side by side

| Moment | Constructor version | Field version | Setter version |
| --- | --- | --- | --- |
| Before the object exists | repository already built | — | — |
| Object creation | born **with** repository | born **empty** (`holds` = `null`) | born **empty** |
| After creation | nothing left to do | Spring writes the private field (reflection) | Spring calls `setHolds(...)` |

Field and setter share a vulnerable moment: **the object exists but is not ready**.
Inside Spring's startup it passes quickly and safely; the danger appears when anything
other than Spring creates the object. `@Autowired` means "fill this in after birth" —
the constructor version never needed it, because its demands are in the constructor
signature.

### Where it is used

Field injection fills **older codebases and tutorials** — recognise it instantly. Setter
injection is rare, reserved for genuinely **optional** collaborators. New code in a
modern team: constructor, almost without exception.

### Common mistakes

- **Forgetting `@Autowired` on the field.** No startup error — Spring never fills the
  field; it stays `null` until the first call explodes.
- **Believing `private` protects the field.** Reflection walks past `private` — the
  keyword guards against ordinary code, not frameworks.
- **Concluding "all three are fine, pick by taste."** They all run; the judgement on
  honesty, immutability, and testability is not close.

### Link to the task

**S1 complete:** `SeatHoldService` three ways in the scratch package —
`SeatHoldService` (constructor), `FieldSeatHoldService` (field),
`SetterSeatHoldService` (setter) — all running, same repository behind each.

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app from Lesson 1 runs.

1. Create `FieldSeatHoldService.java` and `SetterSeatHoldService.java` exactly as above.
2. Replace the two print lines in `HoldsApplication.main` with the three
   `getBean(...).hold(...)` lines.
3. `mvn spring-boot:run` → the three seat lines, count rising 1 → 2 → 3.

**Done when:** all three print, and you can explain why the third line says `3 total`
though that service never saw seats A1 and A2 (one shared repository bean behind all
three doors).

### Summary for revision

- Field injection = object born empty, Spring writes the private field afterward,
  via reflection. Setter injection = object born empty, Spring calls the setter.
- Constructor injection is the only form with **no half-built moment**.
- `@Autowired` = "fill this in after birth"; constructors state demands in their
  signature instead.
- All three deliver the same shared bean — the route differs, not the object.

### Check questions, with answers

**Question 1.** (The `FieldSeatHoldService` code as above.) Put these four events in
the order they happen during a normal startup and first use: (a) the private field
`holds` receives its value, (b) the object is created by the no-argument constructor,
(c) the scan writes a recipe card for this class, (d) `hold("A2")` runs. At the exact
moment event (b) finishes, what value does `holds` hold?

**Answer.** Order: **(c) → (b) → (a) → (d)**. The scan reads the `@Service` mark and
writes the card; cooking creates the object through the invisible no-arg constructor;
Spring then writes the field by reflection; only later does `main` call `hold("A2")`.
At the end of (b), `holds` is **`null`** — the object exists but is half-built. Spring
closes that gap milliseconds later, which is exactly why the gap is easy to forget —
until something other than Spring creates the object.

**Question 2.** A teammate deletes the `@Autowired` line, leaving a plain
`private HoldRepository holds;` field in `FieldSeatHoldService`. What happens at
startup, and at the first `hold("A2")` call? Why can the constructor version not be put
into an equivalent "forgot to fill it" state?

**Answer.** Startup: perfectly clean — no error, no warning. Without the mark, Spring
has no instruction to fill the field, so it silently stays `null`. First call:
`NullPointerException` at `holds.save(seatId)` — the modern message even names the
culprit: cannot invoke `HoldRepository.save(String)` because `this.holds` is null. The
constructor version cannot reach such a state: the repository is demanded by the only
constructor, so the object cannot be created without one (the compiler rejects
`new SeatHoldService()`), and the `final` field is assigned exactly once there. Every
successfully created instance provably has its repository; "created but unfilled" is
not a state that exists.

---

## Lesson 3 — Honesty and immutability: the side-by-side pair (subtasks S2 part, S3)

### Definition

The first two of the task's three judging points:

- **Honesty** — does the class *tell you* what it needs, or hide it?
- **Immutability** — can the dependency be locked with `final`, so nothing can ever
  replace it?

Both are settled by the compiler below, with two real compile errors.

### Why it is needed

Every code review has an opinion about injection style; most people repeat it without
the reasons. These two reasons are concrete and checkable — one guards *readers* of the
class, the other guards *the object itself* for its whole life.

### How it works

**Honesty.** A constructor signature is the class's declaration form: every dependency
on one line, visible from outside, readable without opening the body. The field-injected
version shows the world a blank face. And the famous objection — "a constructor with
nine parameters is ugly!" — is the point: **that ugliness is useful information.** Nine
parameters means the class does too much and should be split. Field injection hides the
same nine as nine innocent one-liners, so the class keeps absorbing jobs — *silent
rot*. The ugly constructor is the smoke alarm; field injection unplugs the alarm, not
the fire.

**Immutability.** `final` is a one-word contract: *this field gets exactly one value,
during construction, never assigned again.* Constructor injection satisfies it
naturally. Field injection cannot — a law of the language, proven below.

### The pair, and two compiler verdicts

The side-by-side pair (kept in `NOTES.md`; living versions: `FieldSeatHoldService.java`
beside `SeatHoldService.java`):

```java
// Discouraged: field injection
@Service
class SeatHoldService {
    @Autowired private HoldRepository holds;   // cannot be final, invisible from outside
}

// Preferred: constructor injection
@Service
class SeatHoldService {
    private final HoldRepository holds;

    SeatHoldService(HoldRepository holds) {    // one constructor, so no @Autowired needed
        this.holds = holds;
    }
}
```

**Verdict 1 — "cannot be final" is not an opinion.** One word added to the real
field-injected class:

```java
@Autowired
private final HoldRepository holds;   // trying to add final
```

```text
[ERROR] .../FieldSeatHoldService.java:[9,34] variable holds not initialized in the default constructor
```

**Verdict 2 — what `final` buys.** A method added to the real constructor version,
trying to swap the repository later:

```java
public void swap(HoldRepository other) {
    this.holds = other;   // trying to replace the repository later
}
```

```text
[ERROR] .../SeatHoldService.java:[14,13] cannot assign a value to final variable holds
```

Both edits reverted; the app still runs.

### Decoding the two errors

| Error | Plain English |
| --- | --- |
| `variable holds not initialized in the default constructor` | "`final` means a value by the time construction ends. This class's only constructor (the invisible default) sets nothing. I refuse." |
| `cannot assign a value to final variable holds` | "`final` allows exactly one assignment, in the constructor. This is a second one. I refuse — even for the class's own code." |

The first is the timeline contradiction made official: `final` promises *a value by the
end of construction*; field injection promises *a value after construction*. Both cannot
hold, so Java bans the combination — which is why real-world field injection always
rides on non-final fields. It has no choice.

The second is the free guarantee in the constructor version: whatever the constructor
received is what every call uses, for the object's whole life. No method, no typo, no
late-night fix can swap it. That is *"nothing can replace the repository later,
including by accident."*

### Where it is used

Honesty pays when reading unfamiliar code: the constructor is a table of contents.
Immutability pays in long-lived shared objects: beans are shared by default, and a
shared object whose parts cannot be swapped mid-flight removes a whole category of bugs.

### Common mistakes

- **"Cleaning up" a fat constructor by hiding it.** Nine `@Autowired` fields remove the
  signal, not the problem. The honest fix is splitting the class.
- **Dropping `final` because "it works without it."** It does — and silently gives up
  the compiler's guard. The lock costs one word.
- **Trusting `private` as protection.** Reflection walks past `private`. `final` is the
  keyword with teeth.

### Link to the task

**S3 done** — the pair is written out and kept in `NOTES.md`, living versions side by
side in the scratch package. **S2 two-thirds due:** your own honesty and immutability
sentences go into the `NOTES.md` slots.

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app with all three versions running.

1. Add `final` to the `holds` field in `FieldSeatHoldService.java`; `mvn compile` →
   expect `variable holds not initialized in the default constructor`. Remove it.
2. Add the `swap` method to `SeatHoldService.java`; `mvn compile` → expect
   `cannot assign a value to final variable holds`. Delete it.
3. Copy the side-by-side pair into your `NOTES.md`; write your own honesty and
   immutability sentences under S2.

**Done when:** both compile errors seen on your own screen, both S2 sentences written
in your own words.

### Summary for revision

- Honesty: the constructor signature announces every dependency; field injection shows
  a blank face. Ugly constructors are smoke alarms — split the class, don't hide the
  list.
- Immutability: `final` + constructor assignment = locked for life;
  `final` + field injection = compile error (timeline contradiction).
- `private` guards against ordinary code; `final` guards against everything.

### Check questions, with answers

**Question 1.** This combination is forbidden — the error is real:

```java
@Service
public class FieldSeatHoldService {
    @Autowired
    private final HoldRepository holds;

    public String hold(String seatId) {
        holds.save(seatId);
        return "Seat " + seatId + " held (" + holds.allHeldSeats().size() + " total)";
    }
}
```

```text
[ERROR] variable holds not initialized in the default constructor
```

Name the two promises that collide — what does `final` promise about *when* the field
gets its value, and what does field injection promise? Then point to the exact line in
the constructor version that keeps `final`'s promise.

**Answer.** `final` promises: the field has its value **by the time construction
finishes** — every constructor must assign it exactly once. Field injection promises:
Spring writes the value **after construction**, by reflection. Both cannot be true; at
the end of the invisible default constructor the `final` field would still be empty, so
the compiler refuses the class outright. In the constructor version, the line that keeps
`final`'s promise is `this.holds = holds;` inside
`public SeatHoldService(HoldRepository holds)` — the one permitted assignment, made
during construction.

**Question 2.** A reviewer sees a service whose constructor takes **nine** parameters
and suggests: *"Convert it to field injection — nine `@Autowired` fields look much
cleaner."* If the team follows the advice: what information disappears from the class's
public face, what problem remains exactly as big as before, and what would the honest
fix have been?

**Answer.** What disappears: the constructor signature — the one line that announced
all nine dependencies to every reader and every creator of the class. After the
conversion, the class presents a no-argument face that *looks* self-sufficient while
secretly needing nine collaborators. What remains: the class still depends on nine
things and still does too much — the coupling is untouched; only the evidence moved out
of sight, and with the alarm unplugged the count quietly grows. The honest fix: treat
the nine-parameter constructor as the smoke alarm it is and **split the class** into
smaller ones with real single jobs — the ugliness was the design telling you where to
cut.

---

## Lesson 4 — Testability: the plain JUnit test (subtask S2, complete)

### Definition

A **plain unit test** builds the object itself — with `new`, through the constructor —
hands it a fake, and checks behaviour. No Spring, no application context, no
annotations. Constructor injection is what makes it possible.

### Why it is needed

Tests run hundreds of times a day. Full startup builds every bean before the first
check; the plain test skips all of it — ours ran in **0.026 seconds**. (Task F7.5 later
measures what the Spring-started alternative costs.)

### How it works

Tool words: `mvn test` compiles and runs everything under `src/test/java`; **JUnit**
(brought in by `spring-boot-starter-test`) runs each method marked `@Test`;
`assertEquals(expected, actual)` fails the test if the two differ. Three steps:

1. Write a fake implementing the same `HoldRepository` interface.
2. Build the service yourself: `new SeatHoldService(new FakeHoldRepository())` — the
   same constructor door Spring uses.
3. Assert what the method returns.

### The whole test file, run for real

`src/test/java/com/zenith/holds/SeatHoldServiceTest.java`:

```java
package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatHoldServiceTest {

    static class FakeHoldRepository implements HoldRepository {
        private final List<String> held = new ArrayList<>();

        @Override
        public void save(String seatId) {
            held.add(seatId);
        }

        @Override
        public List<String> allHeldSeats() {
            return List.copyOf(held);
        }
    }

    @Test
    void holdingSeatsReportsTheRisingCount() {
        SeatHoldService service = new SeatHoldService(new FakeHoldRepository());

        assertEquals("Seat A1 held (1 seat(s) now on hold)", service.hold("A1"));
        assertEquals("Seat A2 held (2 seat(s) now on hold)", service.hold("A2"));
    }
}
```

Real `mvn test` output:

```text
[INFO] Running com.zenith.holds.SeatHoldServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in com.zenith.holds.SeatHoldServiceTest
[INFO] BUILD SUCCESS
```

### Walkthrough — and what failure looks like

The imports are `java.util` and `org.junit` — **not one Spring import**. No context, no
scan, no beans; yet the class under test is the exact `@Service` class the real app
uses, because the test walks through the same constructor door Spring walks through.
Each test builds a fresh fake: an empty seat list per test, no leftovers.

Deliberately changing one expectation to `5 seat(s)` produced the real failure:

```text
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.026 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <Seat A2 held (5 seat(s) now on hold)> but was: <Seat A2 held (2 seat(s) now on hold)>
	at com.zenith.holds.SeatHoldServiceTest.holdingSeatsReportsTheRisingCount(SeatHoldServiceTest.java:30)
```

Reads like a sentence: expected this, got that, at line 30. (Reverted; green again.)

The contrast: the same test for `FieldSeatHoldService`. Creating it compiles —
`new FieldSeatHoldService()` uses the invisible no-arg constructor — but the fake has
no way in: the field is `private`, no constructor parameter, no setter. Only routes:
start Spring (not a plain test) or pry the field open with reflection. The constructor
version has a door; the field version has a wall.

### Where it is used

The daily-felt advantage: change a line, run the test, know in a blink. The
fake-behind-an-interface pattern is the same one the F6.1 catalog app used for its
wiring test; it works anywhere a dependency hides behind an interface and a constructor
accepts it.

### Common mistakes

- **Starting the whole application to test pure logic.** The plain test does the same
  job thousands of times faster.
- **Putting an annotated fake in main sources.** Fakes live under `src/test/java`,
  unannotated. Marked and in main sources, a fake becomes a bean — two beans then match
  `HoldRepository`, stopping startup with "required a single bean, but 2 were found".
- **Sharing one fake across tests.** A fake per test = a clean world per test.

### Link to the task

**S2 comparison complete** on all three points — honesty, immutability, testability —
each proven with a real compiler or runtime artifact. The third `NOTES.md` sentence
(testability) closes S2.

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app with all three service versions.

1. Create `src/test/java/com/zenith/holds/SeatHoldServiceTest.java` exactly as above
   (note: `src/test/java`, not `src/main/java`).
2. `mvn test` → expect `Tests run: 1, Failures: 0`, `Time elapsed` well under a second.
3. Change one expected string (`2` → `5`), rerun, read the failure
   (`expected: <...> but was: <...>` plus line number). Change it back; green.
4. Write the testability sentence in `NOTES.md` — S2 done.

**Done when:** green run seen, red run seen, all three S2 sentences written.

### Summary for revision

- Plain unit test = `new` through the constructor + a fake + `assertEquals`. No Spring
  imports at all.
- Green: `Tests run: 1, Failures: 0`, milliseconds. Red: `expected/but was` + line
  number.
- The test uses the same door as Spring; the field-injected version offers no door, so
  it cannot be unit-tested in plain Java.
- Fakes: `src/test/java`, no annotations, one per test.

### Check questions, with answers

**Question 1.** In the test file, one line does the job Spring's entire startup does
for the real app. Which line, and what is it doing in Spring's place? Name two things
present in every Spring run but absent from this file — and why their absence makes the
test finish in 0.026 seconds.

**Answer.** The line is
`SeatHoldService service = new SeatHoldService(new FakeHoldRepository());`. It performs
Spring's cooking step by hand: build the dependency first, then pass it through the
constructor door — dependency injection with a human doing the injecting. Absent:
(1) the application context — no `SpringApplication.run`, no box, no scan, no recipe
cards; (2) the annotation machinery — `@Service` sits on the class unread, `@Test`
belongs to JUnit, not Spring. Speed follows directly: a Spring run must scan packages
and cook every bean (two hundred in the F6.1 app) before anything can be checked; this
test builds exactly two small objects and calls one method.

**Question 2.** A teammate copies the test and aims it at the field-injected version:

```java
@Test
void fieldVersionTest() {
    FieldSeatHoldService service = new FieldSeatHoldService();
    // ... now what?
}
```

Does the first line compile, and why? List every route by which the fake repository
could get into `service` before calling `hold(...)` — and what that list means for
"this class can be unit-tested without Spring."

**Answer.** It compiles: `FieldSeatHoldService` declares no constructor, so Java gives
it the invisible no-argument one, and `new FieldSeatHoldService()` is legal — the object
is born with `holds` = `null`. Routes for the fake: (1) start the Spring context and
let it inject — but then it is no longer a plain unit test; (2) reflection in the test —
force the private field open and write it by hand, fragile and verbose; (3) nothing
else — the field is private, there is no setter, no constructor parameter. So the claim
fails: in plain Java the class is untestable, not because the logic is complex but
because the door is missing. That locked door is the testability cost of field
injection.

---

## Lesson 5 — The one-constructor rule (subtask S4)

### Definition

> **With exactly one constructor, Spring uses it automatically — `@Autowired` on it is
> redundant. With two or more constructors, you must mark the one Spring should use.**

### Why it is needed

Old tutorials and codebases sprinkle `@Autowired` over every constructor; the rule lets
you read that noise for what it is and write none of it. When the rule is violated —
several constructors, none marked — the failure comes in two shapes, one loud and one
sneaky, both staged below for real.

### How it works

Cooking a bean, Spring must pick a constructor:

- **One constructor:** no choice. Spring reads its parameter list and fills it. An
  annotation adds zero information.
- **Two or more:** Spring refuses to guess. It looks for a **no-argument fallback** —
  if one exists, it quietly uses it (filling nothing!); if none exists, startup stops.
  `@Autowired` on exactly one constructor ends the ambiguity.

### The rule, the loud break, the fix — run for real

**The rule working:** `SeatHoldService` ran since Lesson 1 with one constructor, no
`@Autowired`.

**The loud break.** A second constructor added, neither marked, no no-arg fallback:

```java
public SeatHoldService(HoldRepository holds) {
    this.holds = holds;
}

public SeatHoldService(HoldRepository holds, String label) {   // a second constructor
    this.holds = holds;
}
```

```text
Error creating bean with name 'seatHoldService' defined in file [.../SeatHoldService.class]:
Failed to instantiate [com.zenith.holds.SeatHoldService]: No default constructor found
```

**The fix** — mark the chosen door (both constructors still present):

```java
@Autowired   // two constructors exist, so point at the one Spring must use
public SeatHoldService(HoldRepository holds) {
    this.holds = holds;
}
```

Real result: app started, all three seat lines printed. (Then reverted to the canonical
one-constructor form — the best fix for ambiguity is having only one door.)

### The sneaky shape — fallback found, nothing filled

Same situation but a no-arg constructor exists. Built for real:

```java
package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class TwoCtorService {
    private HoldRepository holds;   // cannot be final: the no-arg constructor leaves it unset

    public TwoCtorService() {
    }

    public TwoCtorService(HoldRepository holds) {
        this.holds = holds;
    }

    public String report() {
        return "TwoCtorService sees " + holds.allHeldSeats().size() + " held seat(s)";
    }
}
```

`main` called `report()`. Real output:

```text
Started HoldsApplication in 0.289 seconds (process running for 0.39)
Seat A1 held (1 seat(s) now on hold)
Seat A2 held via field injection (2 total)
Seat A3 held via setter injection (3 total)
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "com.zenith.holds.HoldRepository.allHeldSeats()" because "this.holds" is null
```

**Startup succeeded.** Spring silently took the no-arg fallback, filled nothing, and the
half-built bean sat in the box until its first caller detonated it — the 3 a.m. failure
from the task's introduction, in miniature. (Demo class deleted afterwards.)

### Decoding

| Message | Plain English |
| --- | --- |
| `Failed to instantiate ... No default constructor found` | "Several constructors, none marked. I looked for the no-arg fallback; this class has none. I stop." Loud and immediate. |
| startup clean, then `NullPointerException ... because "this.holds" is null` | "Several constructors, none marked — the no-arg fallback existed, so I used it and filled nothing." Silent until first use. |

### Where it is used

Modern codebases: one constructor per bean, no `@Autowired`; redundant marks you find
can be deleted with zero behaviour change. The multi-constructor case is rare and
legitimate only when a class genuinely offers two construction recipes — then the mark
is mandatory.

### Common mistakes

- **Adding a "convenience" no-arg constructor to a service.** It arms the silent
  fallback: Spring may build the empty version without protest.
- **Assuming Spring picks the "fullest" constructor.** It never guesses — explicit mark
  or no-arg fallback, nothing else.
- **Marking two constructors `@Autowired`.** Rejected — only one may be the required
  door.

### Link to the task

**S4 complete:** rule stated; redundant case proven by four lessons of annotation-free
running; mandatory case proven by a real failure, its fix, and the silent trap seen end
to end.

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app with all three versions and the test.

1. Add the `(HoldRepository, String)` constructor to `SeatHoldService`;
   `mvn spring-boot:run` → `Failed to instantiate ... No default constructor found`.
2. Put `@Autowired` on the one-parameter constructor → the three seat lines return.
3. Create `TwoCtorService.java` as above; add
   `System.out.println(context.getBean(TwoCtorService.class).report());` to `main`;
   run → startup succeeds, three lines print, then the NPE naming `this.holds`.
4. Clean up: delete `TwoCtorService.java`, remove the print line, restore the single
   unannotated constructor. Run once more.

**Done when:** loud failure, fix, and silent trap all seen; `SeatHoldService` back to
one clean constructor.

### Summary for revision

- One constructor → Spring uses it, no annotation. Several → mark exactly one.
- Several + none marked + no no-arg → `No default constructor found`, startup stops.
- Several + none marked + a no-arg → Spring silently builds the empty object; NPE at
  first use.
- Never add a no-arg constructor to a bean with real dependencies.

### Check questions, with answers

**Question 1.** A colleague's class:

```java
@Service
public class PaymentService {
    private final PaymentGateway gateway;

    @Autowired
    public PaymentService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

What is the `@Autowired` contributing? Is deleting it safe, and why does Spring not need
it? Under what exact circumstance would it become required again?

**Answer.** It contributes nothing. The class has exactly one constructor, so Spring has
no choice to make — it reads that constructor's parameter list and fills it, annotation
or not. Deleting it is safe with zero behaviour change. It becomes required the moment a
**second constructor** appears: Spring then refuses to guess between them, and exactly
one must carry the mark (otherwise Spring hunts for a no-arg fallback — using it
silently if present, stopping startup with `No default constructor found` if not).

**Question 2.** Predict before running — this class is a bean, and `main` calls
`report()` on it:

```java
@Service
public class TwoCtorService {
    private HoldRepository holds;

    public TwoCtorService() {
    }

    public TwoCtorService(HoldRepository holds) {
        this.holds = holds;
    }

    public String report() {
        return "TwoCtorService sees " + holds.allHeldSeats().size() + " held seat(s)";
    }
}
```

Does startup succeed? Which constructor does Spring call, and what is `holds`
afterwards? When does the problem surface, and with what message? Give the one-line
change that makes Spring use the other constructor — and the better change, with the two
guarantees it restores.

**Answer.** Startup succeeds. Facing two unmarked constructors, Spring takes the
no-argument fallback `TwoCtorService()`, so `holds` is `null` — a half-built bean
sitting quietly in the box. The problem surfaces at the first call of `report()`:
`java.lang.NullPointerException: Cannot invoke "com.zenith.holds.HoldRepository.allHeldSeats()"
because "this.holds" is null` (the real captured message). One-line change: put
`@Autowired` on `TwoCtorService(HoldRepository holds)` — Spring then uses that door and
fills it. The better change: **delete the no-arg constructor** and keep only the
repository one. That restores two guarantees at once: the field can be `final` again
(locked for life), and a half-built instance becomes impossible anywhere — Spring and
plain tests alike must supply a repository, because the only constructor demands it. And
with one constructor left, no annotation is needed either.

---

## Lesson 6 — Break it: the half-built object, and the reflection trick behind `@Autowired` (subtasks S5, S8)

### Definition

The same mistake — create a service empty-handed and call it — made against both
versions of the class. The field-injected version allows it and explodes at runtime; the
constructor version refuses to compile. Plus the answer to the task's Ask-AI question:
how Spring writes a `private` field at all (**reflection**).

### Why it is needed

The task's warning: *"a half-built `SeatHoldService` that quietly holds a `null`
repository will fail at 3 a.m. during a sale, not at startup."* This lesson pins down
the mechanism and shows which design makes the 3 a.m. version impossible.

### How it works

The field version has a legal path to a broken object: `new FieldSeatHoldService()`
uses the invisible no-arg constructor; the object is born with `holds` = `null`; if
Spring is not the creator, nothing ever fills it. The constructor version has no such
path — its only constructor demands a repository, so the mistake is a compile error,
not a runtime risk.

### The break, run for real

The plain test against the field version:

```java
package com.zenith.holds;

import org.junit.jupiter.api.Test;

class FieldSeatHoldServiceTest {

    @Test
    void halfBuiltObjectExplodes() {
        FieldSeatHoldService service = new FieldSeatHoldService();   // compiles fine!
        service.hold("A1");   // nothing ever set the private field
    }
}
```

Real `mvn test` output:

```text
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.023 s <<< FAILURE!
java.lang.NullPointerException: Cannot invoke "com.zenith.holds.HoldRepository.save(String)" because "this.holds" is null
	at com.zenith.holds.FieldSeatHoldService.hold(FieldSeatHoldService.java:12)
	at com.zenith.holds.FieldSeatHoldServiceTest.halfBuiltObjectExplodes(FieldSeatHoldServiceTest.java:10)
```

The same mistake against the constructor version:

```java
SeatHoldService service = new SeatHoldService();   // same empty-handed attempt
service.hold("A1");
```

Real result — never ran, never compiled:

```text
[ERROR] .../FieldSeatHoldServiceTest.java:[9,35] constructor SeatHoldService in class com.zenith.holds.SeatHoldService cannot be applied to given types;
  required: com.zenith.holds.HoldRepository
  found:    no arguments
```

*"The compiler caught in one second what the other version leaves for runtime."*

### Decoding, then the reflection reveal

| Artifact | Plain English |
| --- | --- |
| `Errors: 1` (not `Failures: 1`) | The test blew up with an exception; JUnit counts explosions separately from wrong answers. |
| `Cannot invoke "HoldRepository.save(String)" because "this.holds" is null` | A method was called on a field nobody ever set; modern Java names the null thing. |
| the two `at ...` lines | Explosion in `FieldSeatHoldService.hold` line 12, called from test line 10. |
| `required: ... / found: no arguments` | The compiler: this constructor needs a repository; you brought nothing. Rejected. |

**Reflection** — Java's built-in ability for a program to inspect and modify classes at
runtime, including normally off-limits parts. Spring's exact trick, done by hand:

```java
FieldSeatHoldService service = new FieldSeatHoldService();

Field f = FieldSeatHoldService.class.getDeclaredField("holds");   // find the private field by NAME
f.setAccessible(true);                                            // switch off the private check
System.out.println("before: " + f.get(service));

f.set(service, new FakeHoldRepository());                         // write it from outside
System.out.println("after:  " + f.get(service));

System.out.println(service.hold("A1"));                           // now it works
```

Real output:

```text
before: null
after:  com.zenith.holds.ReflectionPeekTest$FakeHoldRepository@34a3d150
Seat A1 held via field injection (1 total)
```

That is the entire engine under `@Autowired`: find field by name, `setAccessible(true)`,
write it. The cost: the field is found by the **string** `"holds"` — rename the field
and this still compiles, then dies at runtime with `NoSuchFieldException`. The
constructor test line `new SeatHoldService(fake)` is a real constructor call the
compiler verifies on every build. That is why reflection makes the class harder to test
without Spring: the workaround exists, but it is verbose, fragile, and invisible to the
compiler. (Demo deleted; kept in the suite: the trap test converted to green,
documented form — `assertThrows(NullPointerException.class, () -> service.hold("A1"))`.)

### Where it is used

Reflection is the engine room of framework "magic": Spring writing `@Autowired` fields
and reading constructor parameter types, JUnit finding `@Test` methods, JSON tools
reading fields. You rarely write it — recognising it turns magic into machinery.

### Common mistakes

- **Patching the NPE with a null-check.** Converts a loud failure into silent wrong
  behaviour. Remove the doorless design, not the symptom.
- **Believing `private` means unreachable.** One `setAccessible(true)` and it is not.
- **Routine reflection in tests.** Works until a rename; breaks where the compiler
  cannot warn. The fix is the constructor, not better prying.

### Link to the task

**S5 complete** (NPE seen, compile refusal beside it). **S8 taught** (reflection
explained and performed by hand; the DIY includes the task's Ask-AI prompt).

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app with the three versions and the passing test.

1. Create `FieldSeatHoldServiceTest.java` with the naked `service.hold("A1")`;
   `mvn test` → `Errors: 1`, NPE naming `this.holds`.
2. Add a method doing `new SeatHoldService();` → `mvn test` → compile refusal
   `required: HoldRepository / found: no arguments`. Delete the method.
3. Convert the NPE test to
   `assertThrows(NullPointerException.class, () -> service.hold("A1"));`
   (static import `org.junit.jupiter.api.Assertions.assertThrows`) → suite green.
4. Optional: write the reflection demo as a test, watch `before: null / after: ...`,
   delete it.
5. Run the Ask-AI prompt: *"Explain why Spring can inject into a private field at all,
   what reflection is doing there, and why that makes the class harder to test without
   Spring"* — compare with what you did by hand.

**Done when:** NPE seen, compile refusal seen, suite green with the documented trap,
reflection no longer magic.

### Summary for revision

- Field version: empty creation compiles, explodes at first use (`because "this.holds"
  is null`). Constructor version: empty creation does not compile.
- JUnit `Errors` = exceptions; `Failures` = wrong answers.
- `@Autowired`'s engine = reflection: field by name-string, `setAccessible(true)`,
  write. Fragile because strings are invisible to the compiler.
- Never null-check around a missing dependency; give the class a constructor door.

### Check questions, with answers

**Question 1.** The task says: *"The compiler caught in one second what the other
version leaves for runtime."* Using the two real artifacts — the compile error
(`required: com.zenith.holds.HoldRepository / found: no arguments`) and the exception
(`NullPointerException ... because "this.holds" is null`) — explain for each: **when**
in the life of the code the failure appears, **who** reports it, and which of the two
can reach production. Why is the earlier one strictly better?

**Answer.** The compile error appears while the code is being **built**, before any
program exists; the reporter is the Java compiler; it can never reach production,
because code that does not compile cannot ship. The exception appears at **runtime**,
only when the broken object's method is actually called; the reporter is the running
JVM — possibly in production, at 3 a.m., during a sale, because startup itself was
clean and the explosion waits for the first real caller. Earlier is strictly better
because the distance between mistake and discovery is where cost lives: the compile
error is impossible to miss and impossible to ship, while the runtime error depends on
somebody exercising that exact path — tests can miss it, and then customers find it.

**Question 2.** The three reflection lines:

```java
Field f = FieldSeatHoldService.class.getDeclaredField("holds");
f.setAccessible(true);
f.set(service, new FakeHoldRepository());
```

What does `f.setAccessible(true)` switch off? If a teammate renames the field `holds`
to `repository`: what happens to this test at compile time, and at runtime? Why can
nothing similar happen to `new SeatHoldService(new FakeHoldRepository())`?

**Answer.** `setAccessible(true)` switches off the access check that normally makes the
JVM reject outside use of `private` members — the runtime enforcement of `private` for
reflective access. After the rename, this test **still compiles perfectly**: the field
name lives in the string `"holds"`, and strings are just data to the compiler. At
runtime, `getDeclaredField("holds")` throws `NoSuchFieldException` — the test errors,
discovering the rename the hard way. The constructor line cannot suffer this fate:
`new SeatHoldService(...)` is a language-level constructor call, checked by the
compiler against the real class on every single build — rename or change anything it
relies on and the build goes red immediately, before the code can run.

---

## Lesson 7 — Break it: the circle — then fix it properly (subtasks S6, S7 + checkpoint)

### Definition

The last break: `SeatHoldService` needs `OrderService` while `OrderService` needs
`SeatHoldService`. Run with constructors (caught at startup), with fields (rejected
today, hidden in older Spring unless you look), then fixed honestly — the shared
behaviour pulled into a third class. The verdict to keep: **a cycle is a design
message, not a configuration problem.**

### Why it is needed

Honesty, immutability, and testability protected single classes; this protects the
shape of the whole system. The field-injection escape hatch is exactly how real
codebases end up with entangled services nobody can untangle years later.

### How it works

A constructor cycle is a chicken-and-egg: finishing `SeatHoldService` needs a
**finished** `OrderService` and the other way round — no build order exists. Not a
Spring limitation: plain Java cannot hand-build the pair either; there is nothing to
pass to whichever constructor runs first. Spring merely *reports* the impossibility.
Field injection changes the physics — both objects can be created empty and filled
afterwards — but the entanglement is identical, just unreported, which is why current
Boot rejects it by default too.

### The break, run for real — constructors

The story: holds must not sell held seats, orders must not hold sold seats — so each
service *thinks* it needs the other.

```java
package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class SeatHoldService {
    private final HoldRepository holds;
    private final OrderService orders;   // ...and the order service needs this one

    public SeatHoldService(HoldRepository holds, OrderService orders) {
        this.holds = holds;
        this.orders = orders;
    }

    public String hold(String seatId) {
        if (orders.isSold(seatId)) {
            return "Seat " + seatId + " cannot be held (already sold)";
        }
        holds.save(seatId);
        return "Seat " + seatId + " held (" + holds.allHeldSeats().size() + " seat(s) now on hold)";
    }

    public boolean isHeld(String seatId) {
        return holds.allHeldSeats().contains(seatId);
    }
}
```

```java
package com.zenith.holds;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final SeatHoldService seatHolds;   // needs the hold service...
    private final Set<String> sold = new HashSet<>();

    public OrderService(SeatHoldService seatHolds) {
        this.seatHolds = seatHolds;
    }

    public String purchase(String seatId) {
        if (seatHolds.isHeld(seatId)) {
            return "Cannot sell seat " + seatId + " (currently held)";
        }
        sold.add(seatId);
        return "Order placed for seat " + seatId;
    }

    public boolean isSold(String seatId) {
        return sold.contains(seatId);
    }
}
```

Two real results. Before the app could start, the **compiler** flagged the existing
unit test — `required: HoldRepository, OrderService / found: FakeHoldRepository` —
constructor honesty announcing the new dependency to every caller instantly. Then,
startup:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌─────┐
|  orderService defined in file [.../target/classes/com/zenith/holds/OrderService.class]
↑     ↓
|  seatHoldService defined in file [.../target/classes/com/zenith/holds/SeatHoldService.class]
└─────┘
```

Spring draws the chain it found; a longer cycle (A → B → C → A) lists every link — the
full path of blame, top to bottom.

### The same circle on fields — and the opt-in

Both edges switched to `@Autowired` fields (constructors no longer demand the other
service). Real result — **still rejected**, with the diagram naming the exact fields:

```text
┌─────┐
|  orderService (field private com.zenith.holds.SeatHoldService com.zenith.holds.OrderService.seatHolds)
↑     ↓
|  seatHoldService (field private com.zenith.holds.OrderService com.zenith.holds.SeatHoldService.orders)
└─────┘
```

Then the escape hatch — one line in `src/main/resources/application.properties`:
`spring.main.allow-circular-references=true`. Real result:

```text
Started HoldsApplication in 0.314 seconds (process running for 0.421)
Seat A1 held (1 seat(s) now on hold)
...
```

**It started happily** — older Spring's behaviour resurrected: the cycle still exists,
the services are still inseparable; the alarm was told to stop ringing. (Property
deleted immediately.) The asymmetry worth remembering: the opt-in can rescue *field*
cycles (empty objects can exist first, be filled later); a pure *constructor* cycle
stays impossible even with the flag — neither object can be created at all. The
constructor form makes the design problem impossible to ignore.

### The proper fix — ask what each service really wanted

Neither wanted *the other service*. Each wanted shared **knowledge**: which seats are
held, which are sold. That is a third thing. Extract it:

```java
package com.zenith.holds;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SeatLedger {
    private final Set<String> sold = new HashSet<>();
    private final Set<String> held = new HashSet<>();

    public void markSold(String seatId) { sold.add(seatId); }

    public boolean isSold(String seatId) { return sold.contains(seatId); }

    public void markHeld(String seatId) { held.add(seatId); }

    public boolean isHeld(String seatId) { return held.contains(seatId); }
}
```

Both services now depend **downward** on the ledger — no edge between them, all fields
`final`:

```java
public SeatHoldService(HoldRepository holds, SeatLedger ledger) { ... }

public OrderService(SeatLedger ledger) { ... }
```

Shape change: `SeatHoldService ⇄ OrderService` became
`SeatHoldService → SeatLedger ← OrderService`. Real run, `main` exercising the
collaboration:

```text
Seat A1 held (1 seat(s) now on hold)
Order placed for seat B7
Seat B7 cannot be held (already sold)
Cannot sell seat A1 (currently held)
```

Holds block sales, sales block holds — the services cooperate **without knowing each
other exist**. The circle was the design begging for `SeatLedger` to be born.

### Final cleanup and audit, run for real

Per the done-when, the discouraged exhibits were deleted: `FieldSeatHoldService`,
`SetterSeatHoldService`, and their trap test (the written pair in `NOTES.md` is the
record). The unit test now builds
`new SeatHoldService(new FakeHoldRepository(), new SeatLedger())`.

| Done-when requirement | Evidence |
| --- | --- |
| Every own class uses constructor injection with `final` fields | Six files remain; every dependency field is `private final`, set in a constructor |
| No `@Autowired` on any field | `grep -rn "Autowired" src/` → **0 matches** (single constructors need none anywhere) |
| `NullPointerException` seen | `Cannot invoke "HoldRepository.save(String)" because "this.holds" is null` |
| Circular-dependency failure seen | Both diagrams above, constructor and field flavours |

Tests: `Tests run: 1, Failures: 0 — BUILD SUCCESS`. **Task F6.2 complete.**

### Common mistakes

- **Reaching for the property instead of the design.** Spring itself labels
  `allow-circular-references=true` a *last resort*; it silences the report and keeps
  the entanglement.
- **Breaking the cycle with `@Lazy` or one field edge.** Same category: the arrows
  still loop; only the moment of pain moves.
- **Extracting a third class named `Utils`.** The extraction works when the third
  thing has a real identity (`SeatLedger` owns seat-state knowledge). If it cannot be
  named honestly, the split is in the wrong place.

### Do it yourself (second laptop)

**Setup:** the F6.2 scratch app from the previous lessons.

1. Write the circular pair as above; `mvn -Dmaven.test.skip=true spring-boot:run` →
   the cycle diagram (the flag is needed because your unit test, rightly, no longer
   compiles against the 2-parameter constructor).
2. Switch both edges to `@Autowired` fields; run → the diagram with field names.
3. Create `src/main/resources/application.properties` with
   `spring.main.allow-circular-references=true`; run → `Started HoldsApplication`.
   **Delete the file.**
4. Create `SeatLedger.java`; rewire both services onto it; set `main` to the four
   print lines; update the test's constructor call to
   `new SeatHoldService(new FakeHoldRepository(), new SeatLedger())`. Run → the four
   lines.
5. Cleanup + audit: delete `FieldSeatHoldService.java`, `SetterSeatHoldService.java`,
   `FieldSeatHoldServiceTest.java`; `mvn test` green;
   `grep -rn "Autowired" src/` empty.
6. Run the task's two Ask-AI prompts and compare with what you saw.

**Done when:** both diagrams seen, the opt-in seen and removed, the ledger version runs
all four lines, audit greps clean.

### Summary for revision

- Constructor cycle: no build order exists — impossible even in plain Java; Spring
  reports it with the box diagram at startup.
- Field cycle: physically startable (empty objects filled later) but identical
  entanglement; current Boot rejects it by default; the opt-in resurrects the old
  silent behaviour and is a last resort.
- The honest fix is extraction: the mutual need names a missing third class both can
  depend on. Arrows go from ⇄ to → ← .
- A cycle is a design message, not a configuration problem.

### Checkpoint questions, with answers

**Question 1.** Name the three advantages of constructor injection, without reading
your notes.

**Answer.** (1) **Honesty** — the constructor lists every dependency in one visible
place; readers and creators see the full price of the class at a glance, and a bloated
list is loud, useful design feedback. (2) **Immutability** — constructor-filled fields
can be `final`: set once during construction, never replaceable afterwards, enforced by
the compiler. (3) **Testability** — the class can be built in a plain test with
`new SeatHoldService(fake, ...)`, no Spring at all, running in milliseconds.

**Question 2.** Why can a field-injected dependency never be `final`, and why does that
matter?

**Answer.** `final` demands a value **by the time construction finishes** — every
constructor must assign the field exactly once. Field injection delivers the value
**after construction**, by reflection, through the no-arg constructor's finished-but-empty
object. The two schedules contradict, so Java rejects the combination outright (the
real error: `variable holds not initialized in the default constructor`). It matters
because without `final` the collaborator is permanently swappable and nullable: the
object can exist half-built (the 3 a.m. `NullPointerException`), and nothing stops a
later assignment from replacing the repository mid-flight. With `final`, both failure
categories are compiler-impossible.

**Question 3.** A class has eleven constructor parameters. What is the code telling
you?

**Answer.** That the class has eleven dependencies — it does too much, and the design
is asking to be split. The ugly constructor is the smoke alarm: it makes the true cost
of the class impossible to miss. Converting it to eleven `@Autowired` fields would
silence the alarm while leaving all eleven dependencies (and the oversized job) exactly
in place. The correct response is extraction into smaller classes with single jobs, not
better hiding.

**Question 4.** Why is a circular dependency a design problem rather than a Spring
problem?

**Answer.** Because the impossibility exists without Spring: in plain Java there is no
order of `new` that builds two classes whose constructors each demand the other
finished — nothing exists to pass to whichever runs first. Spring only changes who
tells you: constructors make it report loudly at startup; field injection *could* paper
over it (create empty, fill later), which hides the entanglement without removing it.
And the cure is design work, not configuration: the mutual need is the shape of a
missing third class (`SeatLedger`) that both sides should depend on. Flipping
`allow-circular-references` changes the reporting, never the shape.

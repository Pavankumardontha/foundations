# F7.2 — Lifecycle, exceptions, and parameterised tests

**Goal of the task.** Set up shared test state safely. Assert that the right exception is
thrown with the right message. Cover many input cases with one test method. Build and then
remove an order-dependent test, so you recognise that bug for life.

**Done when:** `Money` has a test class covering every invariant, including a parameterised
case and an exception case. And you have built, watched fail, and then removed an
order-dependent test.

**You need first:** Task F7.1 (your first test). Lesson 1 below recaps the one thing from
it that every later lesson stands on. Exceptions from F2.10 are re-explained in place when
they appear.

**The story in two sentences.** Most of a test suite's value is in the failure cases: the
rules your objects enforce by throwing. This task teaches the tools that make those tests
short, isolated, and honest — fresh setup per test, exception assertions, and one method
that runs as many separate cases.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **`@BeforeEach`** | A method run before every single test in the class. Used for fresh setup. |
| **`@AfterEach`** | A method run after every test. Used for cleanup. |
| **`@BeforeAll`** | A method run once for the whole class. It is static. Anything it creates is shared by every test. |
| **Order-dependent test** | A test that passes or fails depending on which tests ran before it. Always a bug in the test. |
| **Test isolation** | Each test starts from a clean, known state, no matter what ran before. |
| **`@ParameterizedTest`** | One test method run several times with different inputs. |
| **`@ValueSource` / `@CsvSource`** | Two ways of supplying those inputs: a list of single values, or rows of comma-separated values. |
| **`assertThatThrownBy`** | AssertJ's way of asserting that a block throws, and then making further assertions about what it threw. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Use `@BeforeEach` to build a fresh object for each test. Confirm it runs every time by printing inside it. |
| S2 | Break it on purpose: a `static List<String>` filled in `@BeforeAll`; test A adds to it, test B asserts its size. Reorder or run B alone. It fails. |
| S3 | Fix it by moving setup into `@BeforeEach`. Write the rule: shared mutable state between tests is forbidden; `@BeforeAll` is for expensive, read-only things only. |
| S4 | Assert on failure properly: exception type plus something about the message. |
| S5 | Understand why `assertThatThrownBy` takes a lambda. |
| S6 | `@ParameterizedTest` + `@ValueSource` with five bad currency codes. One method, five separate results. |
| S7 | `@CsvSource` for cases with more than one input (quantity, expected price). Each row reported separately. |
| S8 | Complete the `Money` suite: rejects negatives, rejects null or malformed currency, adds correctly, refuses mismatched currencies, multiplies correctly. |
| S9 | Run `mvn test` and read the report files under `target/surefire-reports/`. |
| S10 | Study three tests that look fine but are order-dependent, and what makes each fragile. |
| CP | Checkpoint: four questions, out loud. |

## Roadmap for this task — 13 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | One test, green and red | The base everything else stands on. Recap of F7.1. | all |
| 2 | `@BeforeEach` | Fresh setup before every test, proven with a print. | S1 |
| 3 | `@AfterEach` | Cleanup after every test. Completes the per-test cycle. | S1 |
| 4 | `@BeforeAll` and `@AfterAll` | Runs once, is static, is shared. The tool misused next. | S2 |
| 5 | Build an order-dependent test | The worst test bug, seen with your own eyes. | S2 |
| 6 | The fix and the rule | Setup moves to `@BeforeEach`; when `@BeforeAll` is safe. | S3 |
| 7 | `assertThatThrownBy` | Prove code throws the right exception and message. | S4 |
| 8 | Why a lambda | Why the code under test is wrapped, not called. | S5 |
| 9 | `@ParameterizedTest` + `@ValueSource` | One method, five cases, five results. | S6 |
| 10 | `@CsvSource` | Rows with more than one input. | S7 |
| 11 | The full `Money` suite | Everything combined; the task's deliverable. | S8 |
| 12 | Surefire report files | What `mvn test` writes to disk; what CI reads. | S9 |
| 13 | Order-dependent lookalikes | Three fragile tests explained; checkpoint. | S10, CP |

The scratch project for this task lives in
`F7.2-lifecycle-exceptions-and-parameterised-tests/scratch-app`. It is a plain Maven
project: JUnit 5, AssertJ, and the Surefire plugin. No Spring. Every lesson's code goes
into `src/test/java/com/zenith/testing/` and runs with `mvn test`.

---

## Lesson 1 — One test, green and red (recap of F7.1; serves all subtasks)

### Definition

A **test** is a small Java method that runs your code and checks one fact about the
result. **JUnit** is the library that finds these methods, runs them, and reports pass or
fail. The checking line inside a test is called an **assertion**.

Two words matter from day one. **Expected** is the value the test demands. **Actual** is
the value your code really produced. `assertEquals` takes them in this order:
`assertEquals(expected, actual)`.

### Why it is needed

Before tests, you prove rules by hand. The `Money` record from F3.6 does it like this:

```java
System.out.println("valid:        " + Money.inr(450000));
System.out.println("plus:         " + Money.inr(450000).plus(Money.inr(25000)));
```

This prints text. A human must read the text and judge: is `475000` right? Every change
means reading it all again. Humans get bored and stop reading. Bugs slip through.

A test removes the human. The check itself says pass or fail. A machine can run thousands
of checks in seconds, on every change, forever.

### How it works

1. Test files live in `src/test/java`. Normal code lives in `src/main/java`. Maven keeps
   them apart so test code never ships to users.
2. You mark a method with `@Test`. That tells JUnit: run this method as a test.
3. Inside the method you write an assertion. `assertEquals(4, 2 + 2)` checks: the value
   `2 + 2` must equal `4`.
4. You run `mvn test`. Maven compiles everything, then a plugin named **Surefire** runs
   every test class and prints a report.
5. If every assertion holds, the build is **green**: `BUILD SUCCESS`. If any assertion
   fails, the build is **red**: `BUILD FAILURE`.

### The example — a project with one test

The project has exactly two files. First `pom.xml`, written once for the whole task:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.zenith</groupId>
    <artifactId>f72-testing</artifactId>
    <version>0.1.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- JUnit 5: the library that finds and runs tests -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.2</version>
            <scope>test</scope>
        </dependency>
        <!-- AssertJ: nicer assertions. First used in the exception lessons. -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.27.7</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Surefire: the Maven plugin that runs tests during `mvn test` -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.6</version>
            </plugin>
        </plugins>
    </build>
</project>
```

What each part is for:

| Part | Meaning |
| --- | --- |
| `maven.compiler.release` 21 | Compile for Java 21. |
| `junit-jupiter` | JUnit 5. Gives you `@Test` and `assertEquals`. |
| `assertj-core` | A second assertion library. First used in Lesson 7. It sits ready until then. |
| `maven-surefire-plugin` | The part of Maven that actually runs the tests. |
| `<scope>test</scope>` | This library exists only for tests. It never ships with your app. |

Then the test file, `src/test/java/com/zenith/testing/FirstTest.java`:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstTest {

    @Test
    void twoPlusTwoIsFour() {
        assertEquals(4, 2 + 2);
    }
}
```

Honest note: this test checks Java's own arithmetic, which never breaks. A real test
checks **your** rules — `Money` rejecting a negative amount, for example. `2 + 2` is used
only because it is the smallest thing that shows the machinery.

### Walkthrough — the real runs

`mvn test` with the passing test, trimmed to the important lines:

```text
[INFO] Running com.zenith.testing.FirstTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in com.zenith.testing.FirstTest
[INFO] Results:
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

What happened, step by step:

1. Maven compiled `FirstTest.java`.
2. Surefire looked for test classes. By default it picks classes whose name ends in
   `Test`. `FirstTest` matches.
3. JUnit created a fresh `FirstTest` object and called `twoPlusTwoIsFour()`.
4. `assertEquals(4, 2 + 2)` compared expected `4` with actual `4`. Equal, so the
   assertion held and said nothing.
5. The method ended without failure. The test passed. The build went green.

Then the test was broken on purpose. One change:

```java
assertEquals(5, 2 + 2);   // was: assertEquals(4, 2 + 2);
```

Real output of `mvn test` after the change:

```text
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.testing.FirstTest
org.opentest4j.AssertionFailedError: expected: <5> but was: <4>
	at com.zenith.testing.FirstTest.twoPlusTwoIsFour(FirstTest.java:11)
[ERROR] Failures:
[ERROR]   FirstTest.twoPlusTwoIsFour:11 expected: <5> but was: <4>
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
```

Decoding every piece:

| Output piece | Plain meaning |
| --- | --- |
| `expected: <5>` | The first argument of `assertEquals`. What the test demanded. |
| `but was: <4>` | What the code really produced. The actual value. |
| `FirstTest.twoPlusTwoIsFour:11` | The failing class, method, and line number 11. |
| `Failures: 1` | One assertion was false. |
| `Errors: 0` | No test blew up with an unexpected exception. A false assertion is a *failure*; an unexpected exception is an *error*. |
| `BUILD FAILURE` | One failing test turns the whole build red. |

Changing `5` back to `4` made the build green again.

### Where it is used

Every serious Java team runs `mvn test` on every change. A build server (called CI, short
for continuous integration) does the same run automatically when code is pushed. A red
build blocks the change from merging. The report above is exactly what CI shows when it
refuses your code.

### Common mistakes

1. **Swapping expected and actual.** `assertEquals(2 + 2, 5)` fails with
   `expected: <4> but was: <5>`. The message now points the wrong way, and you hunt the
   bug in the wrong place. Rule: expected first, actual second.
2. **Putting the file in `src/main/java`.** Surefire only looks in `src/test/java`. The
   build stays green with `Tests run: 0`. A green build that ran zero tests proves
   nothing. Always glance at the `Tests run:` number.
3. **Class name that does not end in `Test`.** A class named `MoneyChecks` is silently
   skipped by Surefire's default filter. Same trap: green, but nothing ran.

### Link to the task

Every subtask in F7.2 begins here. Each one is a test class, run by `mvn test`, read as
green or red. S9 reads the report files this exact run produced. The "Done when" line — a
`Money` test class covering every invariant — is many of these methods in one file.

### Do it yourself (second laptop)

**Setup check:** run `java -version` and `mvn -version`.

- If `java` is missing: `brew install openjdk@21`, then
  `sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`,
  then check again. Expect a line starting `openjdk version "21`.
- If `mvn` is missing: `brew install maven`. Expect `Apache Maven 3.` something.

**Step 1** — make the folders:

```bash
mkdir -p F7.2-lifecycle-exceptions-and-parameterised-tests/scratch-app/src/test/java/com/zenith/testing
cd F7.2-lifecycle-exceptions-and-parameterised-tests/scratch-app
```

**Step 2** — create `pom.xml` in `scratch-app/` with exactly the content shown in the
example section of this lesson.

**Step 3** — create `FirstTest.java` in `src/test/java/com/zenith/testing/` with exactly
the content shown in the example section.

**Step 4** — run:

```bash
mvn test
```

The first run downloads many files. That is normal; it happens once. Expect at the end:
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

**Step 5 (failure drill)** — change `assertEquals(4, 2 + 2)` to
`assertEquals(5, 2 + 2)`. Run `mvn test` again. Expect
`expected: <5> but was: <4>` and `BUILD FAILURE`.

**Step 6** — change it back to `4`. Run `mvn test`. Green again.

**Done when:** you saw both the green and the red output, and you can say which argument
of `assertEquals` is the expected one.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SumTest {

    @Test
    void threePlusSevenIsTen() {
        assertEquals(10, 3 + 7);
    }
}
```

You run `mvn test`. Does the build end in `BUILD SUCCESS` or `BUILD FAILURE`? What will
the line `Tests run: ..., Failures: ...` say?

**Answer.** `BUILD SUCCESS`. The actual value `3 + 7` is `10`, and the expected value is
`10`. They are equal, so the assertion holds. The line says
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

**Question 2.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SumTest {

    @Test
    void threePlusSevenIsNine() {
        assertEquals(9, 3 + 7);
    }
}
```

Running `mvn test` prints: `expected: <9> but was: <10>`. First: which of the two numbers
did the running code actually produce? Second: the `9` came from which argument position
of `assertEquals` — the first or the second?

**Answer.** The code produced `10` — the number after `but was:` is always the actual
value. The `9` came from the **first** argument. The first argument of `assertEquals` is
the expected value, and it is what the message prints after `expected:`.

### Summary for revision

- A test is a method that runs your code and checks one fact. The check is an assertion.
- `assertEquals(expected, actual)` — expected first, actual second.
- Tests live in `src/test/java`, in classes whose name ends with `Test`.
- `mvn test` compiles, then Surefire runs every test class and prints the report.
- Green = `BUILD SUCCESS`, every assertion held. Red = `BUILD FAILURE`, at least one
  assertion was false.
- `Failures` = a false assertion. `Errors` = a test threw an unexpected exception.
- A green build with `Tests run: 0` proves nothing. Always read the number.

---

## Lesson 2 — `@BeforeEach` (serves S1)

### Definition

`@BeforeEach` is an annotation you put on one method in a test class. JUnit then runs
that method before **every single test** in the class. Its job is fresh setup: building
the objects a test needs, from scratch, every time.

### Why it is needed

Without it, every test builds its own starting objects:

```java
@Test
void addingOneItemGivesSizeOne() {
    List<String> basket = new ArrayList<>();   // setup, copy 1
    basket.add("apple");
    assertEquals(1, basket.size());
}

@Test
void addingTwoItemsGivesSizeTwo() {
    List<String> basket = new ArrayList<>();   // setup, copy 2
    basket.add("bread");
    basket.add("milk");
    assertEquals(2, basket.size());
}
```

Real setup is often five or ten lines. Ten tests means ten copies. A change to the setup
must be made in ten places, and one will be missed. `@BeforeEach` holds the setup in one
place. It also guarantees every test starts from the same clean state.

### How it works

For **each** test method, JUnit does three steps, in order:

1. It builds a brand-new object of the test class. Not reused. New.
2. On that new object, it runs the `@BeforeEach` method.
3. Then it runs the test method itself.

Everything from that round is then thrown away. The next test starts at step 1 again.
Nothing normal survives from one test to the next. One thing can survive: a `static`
field. That is Lesson 4, and it is where the trouble starts.

### The example — a fresh basket for every test

File: `src/test/java/com/zenith/testing/FreshBasketTest.java`

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreshBasketTest {

    private List<String> basket;

    @BeforeEach
    void createFreshBasket() {
        basket = new ArrayList<>();
        System.out.println("setup: new empty basket created");
    }

    @Test
    void addingOneItemGivesSizeOne() {
        basket.add("apple");
        System.out.println("test A: basket size is " + basket.size());
        assertEquals(1, basket.size());
    }

    @Test
    void addingTwoItemsGivesSizeTwo() {
        basket.add("bread");
        basket.add("milk");
        System.out.println("test B: basket size is " + basket.size());
        assertEquals(2, basket.size());
    }
}
```

### Walkthrough — the real run

Real `mvn test` output, trimmed to this class:

```text
[INFO] Running com.zenith.testing.FreshBasketTest
setup: new empty basket created
test A: basket size is 1
setup: new empty basket created
test B: basket size is 2
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.FreshBasketTest
```

1. JUnit builds a new `FreshBasketTest` object for test A.
2. `createFreshBasket()` runs: `basket` becomes a new empty list. First `setup:` print.
3. Test A adds `"apple"`. Size 1. Assertion holds. Pass.
4. JUnit throws that object away and builds a new one for test B.
5. `createFreshBasket()` runs again: second `setup:` print. `basket` is empty again.
6. Test B adds two items. Size 2. Assertion holds. Pass.

Two proofs of freshness: the `setup:` line printed once per test, and test B saw size 2,
not 3. A shared basket would still hold the `"apple"` from test A, and test B would fail
with `expected: <2> but was: <3>`.

### Where it is used

Almost every real test class has one. Typical jobs: build the object under test, fill in
test data, reset a fake dependency, create a temporary folder.

### Common mistakes

1. **Thinking it runs once.** The name says it: before **each**. Two tests, two runs.
2. **Making it `static`.** Copied from `@BeforeAll`, which requires `static`. On
   `@BeforeEach` it breaks. Both failures, from real runs. If the method touches a
   normal field, it does not compile:

   ```text
   [ERROR] FreshBasketTest.java:[17,9] non-static variable basket cannot be referenced from a static context
   ```

   If the field is `static` too, it compiles, but JUnit refuses at run time:

   ```text
   org.junit.platform.commons.JUnitException: @BeforeEach method 'static void
   com.zenith.testing.FreshBasketTest.createFreshBasket()' must not be static.
   ```

   Note it counts as `Errors: 1`, not `Failures: 1`. No assertion was false; the test
   machinery itself blew up. Rule: `@BeforeEach` methods are plain methods — no `static`.
3. **Letting one test prepare data for another.** Works only in one exact run order.
   That is an order-dependent test, the most time-wasting bug a suite can contain.
   `@BeforeEach` exists to make it impossible. Lesson 5 builds this bug on purpose.

### Link to the task

This lesson is subtask S1: "Use `@BeforeEach` to build a fresh object for each test.
Confirm it runs every time by printing inside it." Done: `FreshBasketTest` builds a fresh
basket per test, and the double `setup:` print confirms it.

### Do it yourself (second laptop)

Needs the `scratch-app` project from Lesson 1, with its `pom.xml` in place.

**Step 1** — create `FreshBasketTest.java` in `src/test/java/com/zenith/testing/` with
exactly the content shown in the example section of this lesson.

**Step 2** — run `mvn test`. Expect `setup: new empty basket created` printed twice, and
`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` (two basket tests plus Lesson 1's test).

**Step 3 (failure drill 1)** — change `void createFreshBasket() {` to
`static void createFreshBasket() {`. Run `mvn test`. Expect the compile error:
`non-static variable basket cannot be referenced from a static context`.

**Step 4 (failure drill 2)** — also change `private List<String> basket;` to
`private static List<String> basket;`. Run `mvn test`. It compiles, but expect:
`JUnitException: @BeforeEach method ... must not be static.` with `Errors: 1`.

**Step 5** — remove both `static` keywords. Run `mvn test`. Green again.

**Done when:** the setup line printed once per test, and you saw both failure drills.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingTest {

    private String greeting;

    @BeforeEach
    void setUp() {
        greeting = "hello";
        System.out.println("fresh!");
    }

    @Test
    void greetingIsHello() {
        assertEquals("hello", greeting);
    }

    @Test
    void greetingHasFiveLetters() {
        assertEquals(5, greeting.length());
    }

    @Test
    void greetingStartsWithH() {
        assertEquals('h', greeting.charAt(0));
    }
}
```

When `mvn test` runs this class, how many times does `fresh!` appear in the output?

**Answer.** Three times. The class has three tests. `@BeforeEach` runs before every
single test, so `setUp()` runs three times, and each run prints `fresh!` once.

**Question 2.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotesTest {

    private List<String> notes;

    @BeforeEach
    void setUp() {
        notes = new ArrayList<>();
    }

    @Test
    void addingOneNoteGivesSizeOne() {
        notes.add("x");
        assertEquals(1, notes.size());
    }

    @Test
    void startsEmpty() {
        assertEquals(0, notes.size());
    }
}
```

The test `addingOneNoteGivesSizeOne` adds `"x"` to the list. Suppose it runs first. Does
the test `startsEmpty` still pass?

**Answer.** Yes, it passes. Between the two tests, JUnit builds a brand-new `NotesTest`
object and runs `setUp()` again. So `notes` points to a new empty list. The `"x"` went
into a different list object, which was thrown away with the first test's object. Run
order does not matter here — that is exactly the isolation `@BeforeEach` provides.

### Summary for revision

- `@BeforeEach` marks a method that JUnit runs before every single test in the class.
- Per test: new test-class object → `@BeforeEach` → the test. Then all of it is discarded.
- It centralises setup and guarantees a clean start per test.
- No `static` on it. `static` belongs to `@BeforeAll`.
- Never let one test prepare data for another — that creates order-dependent tests.

---

## Lesson 3 — `@AfterEach` (completes the per-test cycle; serves S1 and CP1)

### Definition

`@AfterEach` is an annotation you put on one method in a test class. JUnit runs that
method after **every single test** in the class. Its job is cleanup. Its strong
guarantee: it runs even when the test fails.

### Why it is needed

Some tests create things that live **outside** the test object: a file on disk, an open
database connection, a started server. JUnit throws the test object away after each test,
but it cannot delete a file or close a connection. You must do that yourself.

Cleanup at the end of the test method looks right and is a trap:

```java
@Test
void failingTest() {
    System.out.println("test runs");
    assertEquals(5, 2 + 2);                                   // fails here
    System.out.println("inline cleanup after the assertion"); // never reached
}
```

A failing assertion **throws**. The throw jumps out of the method at that line. Every
line after it is skipped. So exactly when things go wrong, the cleanup does not run, and
the leftover poisons later tests.

### How it works

The full cycle JUnit runs for **each** test method:

1. Build a brand-new object of the test class.
2. Run the `@BeforeEach` method on it.
3. Run the test method.
4. Run the `@AfterEach` method — **always**. Pass, fail, or unexpected exception.
5. Throw the object away.

Steps 2 and 4 are a matched pair around every test: fresh setup before, guaranteed
cleanup after.

### The example — the visible cycle

File: `src/test/java/com/zenith/testing/CleanupTest.java`

```java
package com.zenith.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupTest {

    @BeforeEach
    void setUp() {
        System.out.println("setup");
    }

    @Test
    void firstTest() {
        System.out.println("first test runs");
        assertEquals(4, 2 + 2);
    }

    @Test
    void secondTest() {
        System.out.println("second test runs");
        assertEquals(6, 2 * 3);
    }

    @AfterEach
    void cleanUp() {
        System.out.println("cleanup");
    }
}
```

The `cleanUp()` method sits at the bottom of the file, below the tests. File position
does not matter. The annotation decides when it runs.

Real `mvn test` output:

```text
[INFO] Running com.zenith.testing.CleanupTest
setup
first test runs
cleanup
setup
second test runs
cleanup
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.CleanupTest
```

### Walkthrough — proof that `@AfterEach` survives a failure

This drill class was run once and then deleted, to keep the project green:

```java
package com.zenith.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupDrillTest {

    @Test
    void failingTest() {
        System.out.println("test runs");
        assertEquals(5, 2 + 2);
        System.out.println("inline cleanup after the assertion");
    }

    @AfterEach
    void cleanUp() {
        System.out.println("@AfterEach cleanup");
    }
}
```

Real output:

```text
[INFO] Running com.zenith.testing.CleanupDrillTest
test runs
@AfterEach cleanup
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.testing.CleanupDrillTest
org.opentest4j.AssertionFailedError: expected: <5> but was: <4>
```

Two facts in one output:

| Fact | Meaning |
| --- | --- |
| `inline cleanup after the assertion` is **missing** | The failing assertion threw. The line after it never ran. |
| `@AfterEach cleanup` **printed** | JUnit ran the `@AfterEach` method anyway, after the failure. |

### Where it is used

Deleting temporary files. Closing database connections. Stopping a small server a test
started. Resetting anything global a test changed. Honest note: tests of pure objects
need no cleanup. `Money` tests create only `Money` objects; the garbage collector handles
those. Add `@AfterEach` only when a test leaves something behind in the outside world.

### Common mistakes

1. **Cleanup at the end of the test body.** Skipped the moment an assertion fails —
   proven above. Cleanup belongs in `@AfterEach`.
2. **Thinking a failed test skips `@AfterEach`.** It is the other way around. It runs
   after pass, fail, or unexpected exception. You can rely on it.
3. **Making it `static`.** Same rule as `@BeforeEach`: plain method, no `static`.

### Link to the task

The task is titled "Lifecycle, exceptions, and parameterised tests". This lesson
completes the per-test half of the lifecycle: fresh object → `@BeforeEach` → test →
`@AfterEach`, every time. Lesson 4 adds the once-per-class pair that subtask S2 then
misuses on purpose. Checkpoint question 1 compares exactly these.

### Do it yourself (second laptop)

Needs the `scratch-app` project with `FirstTest` and `FreshBasketTest` already in it.

**Step 1** — create `CleanupTest.java` in `src/test/java/com/zenith/testing/` with
exactly the content shown in the example section of this lesson.

**Step 2** — run `mvn test`. Expect `setup / first test runs / cleanup / setup / second
test runs / cleanup` and `Tests run: 5, Failures: 0`.

**Step 3 (failure drill)** — create `CleanupDrillTest.java` with exactly the content
shown in the walkthrough section. Run `mvn test`. Expect `test runs` then
`@AfterEach cleanup`, no `inline cleanup` line, and `BUILD FAILURE` with
`expected: <5> but was: <4>`.

**Step 4** — delete `CleanupDrillTest.java`. Run `mvn test`. Green again:
`Tests run: 5, Failures: 0`.

**Done when:** you saw that the line after a failing assertion never prints, while
`@AfterEach` still runs.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineTest {

    @BeforeEach
    void start() {
        System.out.println("open");
    }

    @Test
    void doesWork() {
        System.out.println("work");
        assertEquals(1, 2);
    }

    @AfterEach
    void stop() {
        System.out.println("close");
    }
}
```

The assertion `assertEquals(1, 2)` fails. Which words appear in the output, and in what
order? Does `close` appear even though the test fails?

**Answer.** The output is `open`, then `work`, then `close`, in that order. Yes, `close`
appears. `@BeforeEach` ran first, the test printed `work` and then failed at the
assertion, and JUnit still ran the `@AfterEach` method. The test is reported as a
failure: `expected: <1> but was: <2>`.

**Question 2.** Here is a complete test class. It writes a file to disk, and the delete
sits on the last line of the test method:

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTest {

    private Path file;

    @BeforeEach
    void createDraft() throws IOException {
        file = Path.of("/tmp/report.txt");
        Files.writeString(file, "draft");
    }

    @Test
    void reportSaysFinal() throws IOException {
        assertEquals("final", Files.readString(file));
        Files.delete(file);
    }
}
```

The file contains `"draft"`, so the assertion fails. Which line never runs, and what is
left on disk after the test? Where should the `Files.delete(file)` line move to?

**Answer.** `Files.delete(file)` never runs. The assertion compares `"final"` with
`"draft"`, fails, and throws out of the method before the delete line. The file
`/tmp/report.txt` is left on disk, still containing `draft`. The delete belongs in an
`@AfterEach` method:

```java
@AfterEach
void removeFile() throws IOException {
    Files.deleteIfExists(file);
}
```

`@AfterEach` runs even after a failure, so the file is always removed.

### Summary for revision

- `@AfterEach` marks a method that JUnit runs after every single test. Always — pass or
  fail.
- Full per-test cycle: new object → `@BeforeEach` → test → `@AfterEach` → discard.
- A failing assertion throws; lines after it in the test body are skipped. Cleanup in the
  test body is therefore unreliable.
- Use `@AfterEach` for things the garbage collector cannot clean: files, connections,
  servers, changed global state.
- Pure-object tests (like `Money`) usually need no `@AfterEach` at all.

---

## Lesson 4 — `@BeforeAll` and `@AfterAll` (serves S2)

### Definition

`@BeforeAll` is an annotation on a method that JUnit runs **once for the whole class**,
before the first test. `@AfterAll` is its partner: once, after the last test. Both
methods must be `static`.

### Why it is needed

Some setup is expensive. Starting a database container takes seconds. Put that in
`@BeforeEach`, and 200 tests pay the price 200 times — the suite takes minutes instead of
seconds. Nobody runs a slow suite, and a suite nobody runs catches nothing. `@BeforeAll`
pays the price once.

### How it works

The complete order for a class with two tests:

1. `@BeforeAll` runs. Once. No test object exists yet.
2. First test: new object → `@BeforeEach` → the test → `@AfterEach`.
3. Second test: new object → `@BeforeEach` → the test → `@AfterEach`.
4. `@AfterAll` runs. Once.

Why `static`, step by step:

1. A plain method can only run on an object.
2. `@BeforeAll` runs before the first test object is built. No object exists yet.
3. A `static` method belongs to the **class itself**, not to an object. It can run with
   no object. That is why JUnit demands `static`.
4. Anything `@BeforeAll` creates must be stored in a `static` field, because plain fields
   need an object too.
5. A `static` field belongs to the class. It is **not** thrown away between tests. Every
   test sees the same one. It is **shared**.

Shared is fine for things tests only read. Shared is a disaster for things tests change.

### The example — the visible full order

File: `src/test/java/com/zenith/testing/LifecycleOrderTest.java`

```java
package com.zenith.testing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleOrderTest {

    @BeforeAll
    static void onceBeforeEverything() {
        System.out.println("BEFORE ALL - once, before any test object exists");
    }

    @BeforeEach
    void setUp() {
        System.out.println("  setup");
    }

    @Test
    void firstTest() {
        System.out.println("    first test");
        assertEquals(4, 2 + 2);
    }

    @Test
    void secondTest() {
        System.out.println("    second test");
        assertEquals(6, 2 * 3);
    }

    @AfterEach
    void cleanUp() {
        System.out.println("  cleanup");
    }

    @AfterAll
    static void onceAfterEverything() {
        System.out.println("AFTER ALL - once, after the last test");
    }
}
```

### Walkthrough — the real run

```text
[INFO] Running com.zenith.testing.LifecycleOrderTest
BEFORE ALL - once, before any test object exists
  setup
    first test
  cleanup
  setup
    second test
  cleanup
AFTER ALL - once, after the last test
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.LifecycleOrderTest
```

Two tests gave two `setup` and two `cleanup` lines, but only one `BEFORE ALL` and one
`AFTER ALL`. Each-methods scale with the number of tests. All-methods do not.

### Where it is used

Starting a database container all tests in the class query (Phase 1 does this). Loading a
large read-only reference file. Expensive parsing that tests only look at. The pattern:
costly to build, and the tests **only read it**.

### Common mistakes

1. **Forgetting `static`.** Real output from this project:

   ```text
   [ERROR] com.zenith.testing.LifecycleOrderTest -- Time elapsed: 0.001 s <<< ERROR!
   org.junit.platform.commons.JUnitException: @BeforeAll method 'void
   com.zenith.testing.LifecycleOrderTest.onceBeforeEverything()' must be static
   unless the test class is annotated with @TestInstance(Lifecycle.PER_CLASS).
   ```

   Zero tests in the class ran. Mirror image of `@BeforeEach`, which must **not** be
   static. The mentioned `@TestInstance(Lifecycle.PER_CLASS)` is an advanced lifecycle
   switch we do not use; for us the rule is: all-methods are `static`.
2. **Mixing up the names.** *Each* = before/after **each test**. *All* = once, for **all
   tests together**.
3. **Using `@BeforeAll` to build the objects tests modify.** The object lands in a
   `static` field, every test shares it, and changes leak between tests. Lesson 5 builds
   this bug on purpose.

### Link to the task

Subtask S2 says: "Create a `static List<String>` populated in `@BeforeAll`, have test A
add to it and test B assert its size." This lesson supplies both halves: `static`
(survives between tests, shared) and `@BeforeAll` (runs once). Checkpoint question 1 —
"when is `@BeforeAll` safe?" — is answered in Lesson 6.

### Do it yourself (second laptop)

**Step 1** — create `LifecycleOrderTest.java` in `src/test/java/com/zenith/testing/`
with exactly the content shown in the example section of this lesson.

**Step 2** — run `mvn test`. Expect: `BEFORE ALL` once, then
`setup / first test / cleanup / setup / second test / cleanup`, then `AFTER ALL` once.
Total: `Tests run: 7, Failures: 0`.

**Step 3 (failure drill)** — delete the word `static` from the `onceBeforeEverything`
line. Run `mvn test`. Expect `JUnitException: @BeforeAll method ... must be static` with
`Errors: 1`, and zero tests of this class run.

**Step 4** — put `static` back. Run `mvn test`. Green: `Tests run: 7, Failures: 0`.

**Done when:** one `BEFORE ALL` line against two `setup` lines, and the must-be-static
error seen once.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderTest {

    @BeforeAll
    static void a() {
        System.out.println("A");
    }

    @BeforeEach
    void b() {
        System.out.println("B");
    }

    @Test
    void test1() {
        System.out.println("T1");
        assertEquals(1, 1);
    }

    @Test
    void test2() {
        System.out.println("T2");
        assertEquals(2, 2);
    }

    @AfterEach
    void c() {
        System.out.println("C");
    }

    @AfterAll
    static void d() {
        System.out.println("D");
    }
}
```

Assume `test1` runs before `test2`. Write the exact sequence of letters the class prints.

**Answer.** `A B T1 C B T2 C D`. `A` once at the start (`@BeforeAll`). Then per test:
`B` (setup), the test letter, `C` (cleanup). Then `D` once at the end (`@AfterAll`).

**Question 2.** JUnit builds a new object of the test class for every test. `@BeforeAll`
runs once, before the first of those objects is built. Why does this force the
`@BeforeAll` method to be `static`? And when `@BeforeAll` creates something — say a
started database — where must that thing be stored so tests can reach it, and what does
that storage place mean for sharing between tests?

**Answer.** A plain method needs an object to run on, and no test object exists yet when
`@BeforeAll` runs. A `static` method belongs to the class itself, so it can run without
any object — hence the requirement. Whatever it creates must be stored in a `static`
field, because plain fields also need an object. A `static` field belongs to the class
and survives across all tests, so every test sees the **same** stored thing. It is shared
state. Safe if tests only read it; dangerous the moment any test changes it.

### Summary for revision

- `@BeforeAll` / `@AfterAll`: once per class, before the first and after the last test.
  Both `static`.
- Full order: `@BeforeAll` → [new object → `@BeforeEach` → test → `@AfterEach`] per test
  → `@AfterAll`.
- `static` is required because no test object exists yet; `static` storage means shared
  by every test.
- Right use: expensive things that tests **only read**.
- Each = every test. All = once for the whole class.

---

## Lesson 5 — Build an order-dependent test on purpose (serves S2)

### Definition

An **order-dependent test** passes or fails depending on which tests ran before it.
**Test isolation** is the opposite: each test starts from a clean, known state, no matter
what ran before. An order-dependent test is always a bug in the test, never in the code
under test.

### Why it is needed

The suite is green for months. Then someone renames a method, runs one test alone, or
adds a test — and an **innocent** test turns red. The code under test did not change. The
red test is not the guilty one; the guilty one is still green. You can study the red test
forever and find nothing, because nothing is wrong with it. Building this bug once, on
purpose, teaches the symptoms for life.

### How it works

The trap needs three pieces:

1. A `static` field — belongs to the class, survives between tests, shared by all.
2. `@BeforeAll` filling it **once**, not before every test.
3. Two tests: test A **changes** the shared field; test B **asserts a value that is only
   true if A already ran**.

Test B's pass depends on a hidden sentence that appears nowhere in its code: "test A ran
before me."

Key fact discovered live: JUnit's default run order is **not** file order. It is
deterministic but intentionally non-obvious, and it depends on method **names**. Moving
code around changes nothing; renaming a method can flip the order.

### The example — the trap

File (while it existed): `src/test/java/com/zenith/testing/SharedStateTrapTest.java`

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.MethodName.class)   // run tests in name order: testA before testB
class SharedStateTrapTest {

    static List<String> guests;

    @BeforeAll
    static void createGuestList() {
        guests = new ArrayList<>();
        guests.add("Asha");
        System.out.println("BEFORE ALL: guest list starts with " + guests.size() + " guest");
    }

    @Test
    void testA_addsAGuest() {
        guests.add("Ravi");
        System.out.println("test A sees " + guests.size() + " guests");
        assertEquals(2, guests.size());
    }

    @Test
    void testB_countsTwoGuests() {
        System.out.println("test B sees " + guests.size() + " guests");
        assertEquals(2, guests.size());
    }
}
```

`@TestMethodOrder(MethodOrderer.MethodName.class)` tells JUnit: run this class's tests in
alphabetical name order. It is used here **only to stage the lucky order** in which the
trap hides. It is not a fix for anything.

### Walkthrough — four real runs

**Run 1 — without the order switch.** The class was first written *without*
`@TestMethodOrder`, trusting file order. Real output:

```text
[INFO] Running com.zenith.testing.SharedStateTrapTest
BEFORE ALL: guest list starts with 1 guest
test B sees 1 guests
test A sees 2 guests
[ERROR] ... testB_countsTwoGuests ... AssertionFailedError: expected: <2> but was: <1>
```

JUnit ran B first even though A is written first. File order means nothing.

**Run 2 — order pinned, everything green.** With the `@TestMethodOrder` line added:

```text
BEFORE ALL: guest list starts with 1 guest
test A sees 2 guests
test B sees 2 guests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Green, with the landmine armed. Many real suites live in this state.

**Run 3 — run test B alone.**

```bash
mvn test -Dtest="SharedStateTrapTest#testB_countsTwoGuests"
```

```text
BEFORE ALL: guest list starts with 1 guest
test B sees 1 guests
[ERROR] ... expected: <2> but was: <1>
```

`@BeforeAll` ran, test A did not. **Passes together, fails alone** — the signature of an
order-dependent test.

**Run 4 — rename one method.** `testA_addsAGuest` renamed to `testC_addsAGuest`, nothing
else. Alphabetical order now puts B first:

```text
test B sees 1 guests
[ERROR] ... expected: <2> but was: <1>
```

A rename turned the build red. In a 400-test suite, with the rename three weeks old and
the red test in a different file, this costs hours.

### Where it occurs in real life

A `static` cache one test fills and another reads. A shared database tests write to
without cleanup. A `@BeforeAll`-built object that tests modify. A changed global (system
time zone, locale) one test sets and forgets.

### Common mistakes

1. **Trusting file order.** Proven false by run 1. Default order depends on method names.
2. **"Fixing" by pinning order.** `@TestMethodOrder` made run 2 green while runs 3 and 4
   stayed broken. Pinning hides the dependency; it does not remove it.
3. **Believing a green suite proves isolation.** Green only proves this order, together,
   passed. Run 2 was green with the bug fully armed.

### Link to the task

Subtask S2, complete: a `static List<String>` filled in `@BeforeAll`, test A adds, test B
asserts the size; it failed when run alone and when the order flipped. Lesson 6 is S3:
the fix and the rule.

### Do it yourself (second laptop)

**Step 1** — create `SharedStateTrapTest.java` in `src/test/java/com/zenith/testing/`
with exactly the content shown in the example section, but **leave out** the
`@TestMethodOrder(...)` line and the `MethodOrderer` / `TestMethodOrder` imports.

**Step 2** — run `mvn test`. Expect: `test B sees 1 guests`,
`expected: <2> but was: <1>`, `BUILD FAILURE`. JUnit ran B first.

**Step 3** — add the `@TestMethodOrder(MethodOrderer.MethodName.class)` line and the two
imports. Run `mvn test`. Expect green overall: `Tests run: 9, Failures: 0`.

**Step 4 (break drill 1)** — run
`mvn test -Dtest="SharedStateTrapTest#testB_countsTwoGuests"`. Expect
`test B sees 1 guests`, `expected: <2> but was: <1>`, `BUILD FAILURE`.

**Step 5 (break drill 2)** — rename `testA_addsAGuest` to `testC_addsAGuest`. Run
`mvn test`. Expect the same failure: B runs first now. Rename back. Green.

**Done when:** the same test passed and failed with zero changes to the code under test —
only the order changed.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    static int hits = 0;

    @Test
    void recordsOneHit() {
        hits = hits + 1;
        assertEquals(1, hits);
    }

    @Test
    void startsAtZero() {
        assertEquals(0, hits);
    }
}
```

When `startsAtZero` runs first, both tests pass. When `recordsOneHit` runs first,
`startsAtZero` fails with `expected: <0> but was: <1>`. Point to the exact piece of code
that connects these two tests, and explain why it makes the result depend on order.

**Answer.** The connection is `static int hits`. A `static` field belongs to the class,
not to a test object, so it survives from one test to the next — both tests read and
write the **same** variable. `recordsOneHit` changes it to `1`. `startsAtZero` expects
`0`, which is only true if that change has not happened yet. So the outcome depends on
which test ran first: order-dependence through shared mutable state.

**Question 2.** A teammate says: "I renamed one test method this morning — nothing else.
Now a completely different test in the same class fails. The code under test has not
changed in weeks." JUnit's default run order is deterministic but depends on method
names, so a rename can change which test runs first. What kind of test bug does the class
almost certainly contain, and what is the first thing you would search the class for?

**Answer.** An order-dependent test. The rename changed JUnit's name-based run order, a
test that silently depended on another test running first now runs too early, and it
fails. First thing to search for: shared mutable state — `static` fields that any test
changes, and `@BeforeAll` methods that build objects tests modify.

### Summary for revision

- Order-dependent test: passes or fails depending on what ran before it. Always a test
  bug.
- Recipe for the trap: `static` field + `@BeforeAll` fills it once + one test mutates it
  + another test asserts the mutated value.
- JUnit's default order is name-based, not file-based. A rename can flip it.
- Signature symptom: **passes together, fails alone** (or after a rename/reorder).
- Pinning order with `@TestMethodOrder` hides the bug. It never fixes it.

---

## Lesson 6 — The fix and the rule (serves S3)

### Definition

The fix for an order-dependent test is to **remove the sharing**, not control the order.
Setup moves from `@BeforeAll` into `@BeforeEach`, and the `static` field becomes a plain
field. The rule, for good:

> **Shared mutable state between tests is forbidden. `@BeforeAll` is for expensive,
> read-only things only — such as starting a container in Phase 1.**

Exact meanings: **mutable** = can be changed after creation. **Read-only** = tests only
look at it, never change it. **Expensive** = costs real time to create, like seconds.

### Why it is needed

Lesson 5 left a live landmine: `SharedStateTrapTest`, green together in the lucky order,
red alone or after a rename. A green build with that inside proves nothing. The landmine
must be removed in the one way that works.

### How it works

Four changes turn the trap into an isolated class:

1. **`static List<String> guests` → `private List<String> guests`.** A plain field lives
   on the test object. JUnit builds a new object per test and discards it. The field
   *cannot* carry anything across tests. Isolation becomes structure, not discipline.
2. **`@BeforeAll` → `@BeforeEach`.** The list is built fresh before every test.
3. **Test B's assertion changes from `2` to `1`.** In the trap, B asserted a value only
   test A's add could produce — someone else's work. Now B asserts the size of the fresh
   list its own setup built. Every test's expectations follow from its own setup and its
   own actions only.
4. **`@TestMethodOrder` deleted.** Nothing depends on order, so there is nothing to pin.

### The example — the fixed class

File: `src/test/java/com/zenith/testing/GuestListTest.java` (replaced the deleted
`SharedStateTrapTest.java`)

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuestListTest {

    private List<String> guests;

    @BeforeEach
    void createGuestList() {
        guests = new ArrayList<>();
        guests.add("Asha");
        System.out.println("setup: fresh guest list with " + guests.size() + " guest");
    }

    @Test
    void testA_addsAGuest() {
        guests.add("Ravi");
        System.out.println("test A sees " + guests.size() + " guests");
        assertEquals(2, guests.size());
    }

    @Test
    void testB_countsOneGuestAtStart() {
        System.out.println("test B sees " + guests.size() + " guest");
        assertEquals(1, guests.size());
    }
}
```

### Walkthrough — the three attacks, repeated against the fix

**Attack 1 — run together.** Real output:

```text
[INFO] Running com.zenith.testing.GuestListTest
setup: fresh guest list with 1 guest
test A sees 2 guests
setup: fresh guest list with 1 guest
test B sees 1 guest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

`setup:` printed twice — one fresh list per test.

**Attack 2 — run B alone** (this killed the trap):

```bash
mvn test -Dtest="GuestListTest#testB_countsOneGuestAtStart"
```

```text
setup: fresh guest list with 1 guest
test B sees 1 guest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

**Attack 3 — rename to flip the order.** `testA_addsAGuest` → `testZ_addsAGuest`. The
order flipped (B ran first) and both still passed.

| Attack | Trap (Lesson 5) | Fix (Lesson 6) |
| --- | --- | --- |
| Run together, lucky order | green | green |
| Run B alone | **red**, `expected: <2> but was: <1>` | green |
| Rename a method | **red**, order flipped | green, order flipped, nobody cares |

### Where it is used

Plain fields built fresh in `@BeforeEach` is the default shape of every test class,
including the `Money` suite. `@BeforeAll` stays reserved for setup that is both expensive
and read-only: the Phase 1 database container, a large reference file tests only read.

### Common mistakes

1. **Pinning the order instead of removing the sharing.** Leaves the bug alive for
   run-alone and rename. Never the fix.
2. **Keeping the field `static` but assigning it in `@BeforeEach`.** Passes all three
   attacks today, because every test overwrites it first. Still wrong: one shared
   variable, safety depends on every future test overwriting it, and parallel test runs
   break it. A plain field makes the problem impossible.
3. **Moving cheap setup to `@BeforeAll` "for speed".** `new ArrayList<>()` costs
   microseconds. Only setup costing real seconds earns `@BeforeAll` — and it must be
   read-only.

### Link to the task

Subtask S3 done: setup in `@BeforeEach`, rule written. Also half the task's "Done when":
*built and then removed an order-dependent test* — built in Lesson 5, removed here.
Checkpoint questions 1 and 4 are now answerable.

### Do it yourself (second laptop)

Needs `scratch-app` with `SharedStateTrapTest.java` from Lesson 5.

**Step 1** — delete `SharedStateTrapTest.java`.

**Step 2** — create `GuestListTest.java` in `src/test/java/com/zenith/testing/` with
exactly the content shown in the example section.

**Step 3** — run `mvn test`. Expect the setup line twice and `Tests run: 9, Failures: 0`.

**Step 4** — run `mvn test -Dtest="GuestListTest#testB_countsOneGuestAtStart"`. Expect
`Tests run: 1, Failures: 0`.

**Step 5** — rename `testA_addsAGuest` to `testZ_addsAGuest`, run `mvn test`, confirm
both pass in the flipped order. Rename back. Green.

**Done when:** all three Lesson 5 attacks bounce off, and you can recite the rule.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreTest {

    static List<Integer> scores;

    @BeforeEach
    void setUp() {
        scores = new ArrayList<>();
    }

    @Test
    void addsScore() {
        scores.add(10);
        assertEquals(1, scores.size());
    }

    @Test
    void startsEmpty() {
        assertEquals(0, scores.size());
    }
}
```

Both tests pass together, alone, and in any order, because `setUp()` overwrites `scores`
before every test. Is this class safe to leave as it is? Which single word should still
change, and why?

**Answer.** It works today, but it is not safe to leave. The word `static` should go.
With `static`, all tests share one class-level variable; correctness depends on every
present and future test overwriting it in setup, and it breaks if tests ever run in
parallel, because two tests would write and read the same variable at the same time. As a
plain instance field, each test object carries its own list and sharing is impossible by
construction.

**Question 2.** Sort these four pieces of setup into `@BeforeEach` or `@BeforeAll`,
using the rule "`@BeforeAll` is for expensive, read-only things only":

1. Creating a `new ArrayList<>()` that the test will add items to.
2. Starting a database container that takes four seconds, which the tests will query.
3. Creating a `StringBuilder` that the test will append text to.
4. Reading a large file of all valid currency codes into a list that tests only look
   things up in.

**Answer.**
1. `@BeforeEach` — cheap, and the test changes it (mutable).
2. `@BeforeAll` — expensive (seconds), and the container itself is infrastructure the
   tests use, not an object they mutate in Java.
3. `@BeforeEach` — cheap, and appending changes it.
4. `@BeforeAll` — costly to load, and tests only read it. Exactly the pattern the rule
   allows.

### Summary for revision

- Fix order-dependence by removing sharing: plain field + `@BeforeEach`, never by
  pinning order.
- Each test's assertions must follow from its own setup and its own actions only.
- The rule: shared mutable state between tests is forbidden; `@BeforeAll` is for
  expensive, read-only things only.
- `static` field assigned in `@BeforeEach` still smells: safe only by convention, breaks
  in parallel runs.
- Task progress: the order-dependent test has been built (L5) and removed (L6).

---

## Lesson 7 — `assertThatThrownBy`: testing the error paths (serves S4)

### Definition

`assertThatThrownBy` is an assertion that says: *this code must throw*. It runs the code,
catches what flies out, and then checks facts about it — its type and its message. It
comes from **AssertJ**, the assertion library in the `pom.xml` since Lesson 1. Import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

### Why it is needed

Most of a suite's value is in the failure cases: `Money` rejecting a negative amount *is
the product*. The hand-rolled way to test a throw (used by F3.6's `expectRejection`
helper) looks like this:

```java
try {
    gbp.plus(eur);
    fail("expected an exception");
} catch (IllegalArgumentException e) {
    assertTrue(e.getMessage().contains("currency"));
}
```

Two traps hide in it. Forget the `fail(...)` line and the test silently passes when
nothing is thrown — a test that cannot fail. And a broad catch type means any
`IllegalArgumentException` satisfies it, even one thrown for a different reason.
`assertThatThrownBy` closes both traps in three readable lines.

### How it works

```java
assertThatThrownBy(() -> gbp.plus(eur))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency");
```

1. **`() -> gbp.plus(eur)`** — the code under test, wrapped in a lambda. The wrapping
   hands the code over *without running it yet*, so the assertion can run it inside its
   own try-catch. (The full why is Lesson 8.)
2. **`assertThatThrownBy(...)`** — runs the wrapped code and catches what it throws. If
   **nothing** is thrown, the assertion fails immediately.
3. **The chain** — each call checks one fact about the caught exception:
   `.isInstanceOf(...)` the type, `.hasMessageContaining(...)` a piece of the message.

### The example — Money and the task's own test

The class under test, `src/main/java/com/zenith/money/Money.java` — F3.6's `Money` grown
up, with `BigDecimal` (the exact-decimal type used for money; no `double` rounding):

```java
package com.zenith.money;

import java.math.BigDecimal;

/**
 * An amount of money in one currency.
 * A Money that breaks a rule cannot exist: the constructor throws.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "currency code must be three uppercase letters, was " + currency);
        }
    }

    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: cannot add " + other.currency + " to " + this.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money times(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }
}
```

Reading notes: a record with a compact constructor — the rules run on every
`new Money(...)`, so an invalid `Money` cannot exist. `signum()` returns `-1`, `0`, or
`1` for negative, zero, positive. `matches("[A-Z]{3}")` means exactly three uppercase
letters.

The test, `src/test/java/com/zenith/money/MoneyTest.java` — the task's S4 example:

```java
package com.zenith.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void refusesToAddDifferentCurrencies() {
        Money gbp = new Money(new BigDecimal("10.00"), "GBP");
        Money eur = new Money(new BigDecimal("10.00"), "EUR");

        assertThatThrownBy(() -> gbp.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }
}
```

### Walkthrough — one green run, two red drills

**Green.** `plus` hits the mismatch check and throws
`IllegalArgumentException("currency mismatch: cannot add EUR to GBP")`. Type check
passes, message contains `"currency"` — passes. Overall: `Tests run: 10, Failures: 0`.

**Red drill A — code does not throw.** Lambda changed to `() -> gbp.plus(gbp)` (legal
addition, no throw). Real output:

```text
[ERROR] com.zenith.money.MoneyTest.refusesToAddDifferentCurrencies -- <<< FAILURE!
java.lang.AssertionError: 

Expecting code to raise a throwable.
```

Nothing thrown = test fails. A rule that stops throwing cannot go unnoticed.

**Red drill B — right type, wrong message.** Expectation changed to
`.hasMessageContaining("colour")`. Real output:

```text
java.lang.AssertionError: 

Expecting throwable message:
  "currency mismatch: cannot add EUR to GBP"
to contain:
  "colour"
but did not.

Throwable that failed the check:

java.lang.IllegalArgumentException: currency mismatch: cannot add EUR to GBP
	at com.zenith.money.Money.plus(Money.java:26)
```

The failure shows the full actual message, the demanded text, and the exact line the real
exception came from.

### Where it is used

Every rule the system enforces: `Money` rejecting negatives, a seat hold refusing an
eleventh seat, publishing an event with no price tier. Later, the same chain names your
own exception types from F2.10: `.isInstanceOf(SeatLimitExceededException.class)`.

### Common mistakes

1. **Checking only the type.** All four `Money` rules throw `IllegalArgumentException`.
   A type-only assertion can pass because the *wrong* rule fired. The message check pins
   down which rule threw — that is why the task says: type **and** something about the
   message.
2. **Demanding the exact full message.** `.hasMessage("...")` breaks when the wording
   changes. `.hasMessageContaining("currency")` checks the stable word and survives
   rewording.
3. **Hand-rolled try-catch.** Forgotten `fail()` = a test that cannot fail. Use the tool.

### Link to the task

Subtask S4 done — the exact test from the task sheet runs green in the project. The
`Money` class that landed here is the one the full suite (Lesson 11) will cover
invariant by invariant.

### Do it yourself (second laptop)

**Step 1** — create folder `src/main/java/com/zenith/money/` and in it `Money.java` with
exactly the content shown in the example section.

**Step 2** — create `MoneyTest.java` in `src/test/java/com/zenith/money/` with exactly
the content shown in the example section.

**Step 3** — run `mvn test`. Expect `Running com.zenith.money.MoneyTest`, and overall
`Tests run: 10, Failures: 0`, `BUILD SUCCESS`.

**Step 4 (drill A)** — change the lambda to `() -> gbp.plus(gbp)`. Run `mvn test`.
Expect `Expecting code to raise a throwable.` and `BUILD FAILURE`. Change it back.

**Step 5 (drill B)** — change `"currency"` to `"colour"`. Run `mvn test`. Expect the
failure showing the real message and the demanded `"colour"`. Change it back.

**Step 6** — run `mvn test`. Green: `Tests run: 10, Failures: 0`.

**Done when:** both red drills seen, and each chained call explained.

### Practice questions

**Question 1.** Here is a complete helper class and a complete test:

```java
class Age {
    static void check(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age must be >= 0, was " + age);
        }
    }
}
```

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgeTest {

    @Test
    void rejectsNegativeAge() {
        assertThatThrownBy(() -> Age.check(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("age");
    }
}
```

Does this test pass as written? And if the lambda were changed to `() -> Age.check(5)`,
what would happen, and what would the failure message say?

**Answer.** Yes, it passes: `check(-5)` throws `IllegalArgumentException` with message
`"age must be >= 0, was -5"`, the type matches, and the message contains `"age"`. With
`check(5)`, nothing is thrown — 5 is not negative. The assertion then fails the test
with: `Expecting code to raise a throwable.`

**Question 2.** The `Money` constructor checks the amount **before** the currency
(full code in the example section above). Here is a test that claims to prove the
**currency** rule, but its amount is accidentally negative too:

```java
@Test
void rejectsBadCurrencyCode() {
    assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "EURO"))
            .isInstanceOf(IllegalArgumentException.class);
}
```

If a teammate deletes the currency check from `Money` entirely, does this test still
pass, and why? Which single chained call would expose the problem, and roughly what would
its failure output show?

**Answer.** Yes, it still passes. The amount check runs first, sees `-1.00`, and throws
`IllegalArgumentException("amount must be >= 0, was -1.00")`. The type-only assertion is
satisfied — by the wrong rule. The currency rule contributed nothing, so deleting it
changes nothing. Adding `.hasMessageContaining("currency")` exposes the problem: the
failure would show the actual message `"amount must be >= 0, was -1.00"` and the demanded
text `"currency"`, which it does not contain. The test itself also needs fixing: use a
valid amount, for example `new Money(new BigDecimal("10.00"), "EURO")`, so only the
currency rule can fire.

### Summary for revision

- `assertThatThrownBy(lambda)` runs the wrapped code, catches the throw, fails if
  nothing is thrown.
- Chain checks: `.isInstanceOf(type)` and `.hasMessageContaining(text)` — assert the
  type **and** something about the message.
- Message check pins down *which* rule threw; several rules often share one exception
  type.
- Prefer `hasMessageContaining` (stable word) over `hasMessage` (exact string, brittle).
- The wrapped code must be a lambda so the assertion can run it inside its own
  try-catch. Deep reason: Lesson 8.

---

## Lesson 8 — Why `assertThatThrownBy` takes a lambda (serves S5)

### Definition

The thing between the parentheses in `assertThatThrownBy(() -> gbp.plus(eur))` is a
lambda: the code itself, packaged as an object, handed over **unrun**. The question this
lesson answers: why must the code be wrapped, instead of written plainly as
`assertThatThrownBy(gbp.plus(eur))`?

### Why it is needed

One Java rule decides everything: **arguments are computed before the method runs.** In
`receive(boom())`, Java first computes `boom()` to know what to hand to `receive`. If
`boom()` throws, it throws *right there* — `receive` never starts.

Applied to the assertion: hand it the *result* `gbp.plus(eur)`, and the explosion happens
while computing the argument, **before** `assertThatThrownBy` begins. Nothing is left to
catch. The assertion needs the unlit firecracker — code that runs only when deliberately
run — so it can light it inside its own try-catch.

### How it works

**Without the lambda (broken timeline):**

1. Java computes the argument: `gbp.plus(eur)` runs now.
2. It throws.
3. `assertThatThrownBy` never starts.
4. The exception escapes the test method. JUnit reports an **Error** (unexpected
   exception), not a Failure (false assertion).

**With the lambda (real mechanism):**

1. The lambda object `() -> gbp.plus(eur)` is created. Creating it runs **nothing**.
2. `assertThatThrownBy` receives it and starts.
3. Inside: `try { run the lambda } catch (Throwable t) { keep t }`.
4. The throw happens inside that try-catch — caught and handed to the chain.

A gatekeeper stands in front: the **compiler**. The parameter type is "a runnable block
of code" (AssertJ's `ThrowingCallable`), so a plain value does not even compile.

### The example — watching when code runs

File: `src/test/java/com/zenith/testing/EvaluationOrderTest.java`

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationOrderTest {

    static String boom() {
        System.out.println("boom() is computing the argument");
        throw new IllegalStateException("exploded while computing the argument");
    }

    static void receive(String s) {
        System.out.println("receive() started");
    }

    @Test
    void aLambdaIsNotRunWhenItIsCreated() {
        Runnable wrapped = () -> boom();
        System.out.println("lambda created and passed around - boom() has not run");
    }

    @Test
    void theAssertionRunsTheLambdaInsideItsOwnTryCatch() {
        assertThatThrownBy(() -> boom())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exploded");
    }
}
```

A third test was run once and then deleted — the eager version:

```java
@Test
void argumentsAreComputedBeforeTheMethodRuns() {
    receive(boom());
}
```

### Walkthrough — three real outputs

**Output 1 — the compiler blocks the value version.** With
`assertThatThrownBy(gbp.plus(eur))` in `MoneyTest`:

```text
[ERROR] COMPILATION ERROR :
[ERROR] MoneyTest.java:[16,9] no suitable method found for assertThatThrownBy(com.zenith.money.Money)
      (argument mismatch; com.zenith.money.Money cannot be converted
       to org.assertj.core.api.ThrowableAssert.ThrowingCallable)
```

The method wants runnable code; it got a `Money` value.

**Output 2 — the eager explosion.** The deleted test `receive(boom())`:

```text
boom() is computing the argument
[ERROR] Tests run: 3, Failures: 0, Errors: 1, Skipped: 0 <<< FAILURE!
[ERROR] ...argumentsAreComputedBeforeTheMethodRuns -- <<< ERROR!
java.lang.IllegalStateException: exploded while computing the argument
	at com.zenith.testing.EvaluationOrderTest.boom(EvaluationOrderTest.java:11)
```

`receive() started` never printed — the argument exploded first. And it counts under
`Errors`, not `Failures`: no assertion was false, an exception escaped.

**Output 3 — the lambda is inert until run.**

```text
lambda created and passed around - boom() has not run
boom() is computing the argument
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Creating and passing the lambda ran nothing. `boom()` only printed when the assertion ran
the lambda — inside its try-catch. Green.

### Where it is used

F3.3 lambdas are exactly this idea: code as an object, run later. F3.4's
`Optional.orElseGet(() -> expensive())` computes the fallback only when needed, while
`orElse(expensive())` computes it every time — same evaluation-order rule. JUnit's own
`assertThrows` takes a lambda for the same reason.

### Common mistakes

1. **Running the throwing code before the assertion:**

   ```java
   Money sum = gbp.plus(eur);                 // throws HERE — test errors immediately
   assertThatThrownBy(() -> gbp.plus(eur))   // never reached
           .isInstanceOf(IllegalArgumentException.class);
   ```

   Report shows a raw `Error`, not an assertion failure. The throwing call lives only
   inside the lambda.
2. **Wrapping too much in the lambda.** If setup inside the lambda can throw the same
   exception type, the assertion catches the wrong throw and passes for the wrong
   reason. Build inputs outside; put exactly one call — the one under test — inside.
3. **Thinking the lambda runs where it is written.** It runs when someone runs it.

### Link to the task

Subtask S5 done. The task's sentence, now proven three ways: *the code must not run until
the assertion is ready to catch what it throws.* Checkpoint question 2 is this lesson in
one line.

### Do it yourself (second laptop)

**Step 1 (compiler drill)** — in `MoneyTest.java`, change
`assertThatThrownBy(() -> gbp.plus(eur))` to `assertThatThrownBy(gbp.plus(eur))`. Run
`mvn test`. Expect `COMPILATION ERROR` with `Money cannot be converted to ...
ThrowingCallable`. Change it back.

**Step 2** — create `EvaluationOrderTest.java` with exactly the content shown in the
example section, plus the third test method shown below it.

**Step 3** — run `mvn test`. Expect `boom() is computing the argument` but no
`receive() started`; `Errors: 1`; `BUILD FAILURE`.

**Step 4** — delete the `argumentsAreComputedBeforeTheMethodRuns` method. Run `mvn
test`. Green: `Tests run: 12, Failures: 0, Errors: 0`, including the print
`lambda created and passed around - boom() has not run`.

**Done when:** you can say from memory: the code must not run until the assertion is
ready to catch what it throws.

### Practice questions

**Question 1.** Here is a complete class and test:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

class ClockTest {

    static String stamp() {
        System.out.println("stamp() computing");
        throw new IllegalStateException("clock broken");
    }

    static void log(String s) {
        System.out.println("log started");
    }

    @Test
    void logsTheTime() {
        log(stamp());
    }
}
```

Which line prints? Which line never prints, and why? In the Maven report, does this test
count under `Failures` or under `Errors`?

**Answer.** `stamp() computing` prints. `log started` never prints: Java computes the
argument `stamp()` before `log` can start, and `stamp()` throws, so `log` is never
entered. It counts under `Errors` — no assertion was false; an unexpected exception
escaped the test.

**Question 2.** The writer wants to prove that `plus` refuses mismatched currencies. The
`Money` constructor throws `IllegalArgumentException("currency code must be three
uppercase letters, was GBPX")` for a bad code; `plus` throws
`IllegalArgumentException("currency mismatch: ...")` for mismatched currencies:

```java
@Test
void refusesToAddDifferentCurrencies() {
    assertThatThrownBy(() -> {
        Money gbp = new Money(new BigDecimal("10.00"), "GBPX");
        Money eur = new Money(new BigDecimal("10.00"), "EUR");
        gbp.plus(eur);
    })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
}
```

Note the typo: `"GBPX"` has four letters. Which line inside the lambda actually throws?
Does the test pass as written? Why is that bad, and how should it be rewritten?

**Answer.** The **first** line throws: the constructor rejects `"GBPX"` before `plus` is
ever reached. The test passes as written — the type matches and the constructor's message
contains `"currency"`. It is bad because the rule it claims to test (the mismatch check in
`plus`) never ran; that rule could be deleted and the test would stay green. Rewrite:
build both `Money` objects **outside** the lambda with valid codes (`"GBP"`, `"EUR"`),
and keep exactly one call inside: `assertThatThrownBy(() -> gbp.plus(eur))`.

### Summary for revision

- Java computes arguments before the method runs. A thrown exception at argument
  position escapes before the assertion starts.
- A lambda packages code without running it; creating it runs nothing.
- `assertThatThrownBy` runs the lambda inside its own try-catch and keeps what it caught.
- The compiler enforces the wrapping: a plain value is not a `ThrowingCallable`.
- Build inputs outside the lambda; exactly one call — the one under test — inside.
- Escaped exception = `Errors` column. False assertion = `Failures` column.

---

## Lesson 9 — `@ParameterizedTest` and `@ValueSource` (serves S6)

### Definition

`@ParameterizedTest` marks a test method that JUnit runs **several times, once per
input**. `@ValueSource` supplies the inputs: a flat list of single values. The method
declares a parameter; each run receives one value. Each run counts as its **own test**
in the report.

Imports:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
```

### Why it is needed

`Money` rejects five bad currency shapes: empty, blank, too short, too long, digits. The
alternatives are both bad. Five copy-pasted methods differ by two characters each and
must all be maintained. A loop inside one test is sneakier:

```java
@Test
void rejectsBadCodes() {
    for (String bad : List.of("", " ", "GB", "GBPX", "123")) {
        assertThatThrownBy(() -> new Money(TEN, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

The loop **stops at the first failing value**: later values are never checked, and the
report shows one blurred failure. `@ParameterizedTest`: one method, five cases, five
separate results, and a failing case stops nothing.

### How it works

1. JUnit reads the `@ValueSource` list.
2. For **each** value it runs one complete round: new test object → `@BeforeEach` → the
   method with the value → `@AfterEach`. The whole lifecycle applies per case.
3. Each round is reported as its own test, named `methodName[index]`, starting at `[1]`.
4. One red case does not stop the others.

### The example — five bad currency shapes, one method

File: `src/test/java/com/zenith/money/MoneyTest.java` (the task's exact list):

```java
package com.zenith.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void refusesToAddDifferentCurrencies() {
        Money gbp = new Money(new BigDecimal("10.00"), "GBP");
        Money eur = new Money(new BigDecimal("10.00"), "EUR");

        assertThatThrownBy(() -> gbp.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "GB", "GBPX", "123"})
    void rejectsMalformedCurrencyCodes(String badCode) {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), badCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }
}
```

The parameter `String badCode` is the slot; `@ValueSource` fills it five times.

### Walkthrough — the real runs

**Green:**

```text
[INFO] Running com.zenith.money.MoneyTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.money.MoneyTest
```

Six tests from two methods: one plain + five cases. The report file lists each case:
`rejectsMalformedCurrencyCodes(String)[1]` … `[5]`.

**Red drill — `"USD"` added to the list.** `"USD"` is valid, so nothing throws:

```text
[ERROR] Tests run: 7, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.money.MoneyTest
[ERROR] com.zenith.money.MoneyTest.rejectsMalformedCurrencyCodes(String)[6] -- <<< FAILURE!
java.lang.AssertionError: 

Expecting code to raise a throwable.
```

`Tests run: 7, Failures: 1` — the other six still ran and passed. The failing case is
`[6]`: the sixth list entry, `"USD"`. Maven names cases by index; an IDE shows the value
itself. Either way, the failure points at one input.

### Where it is used

Rules with many input shapes: currency codes, emails, usernames, postcodes. Boundary
lists: a discount rule at 0, 1, 99, 100. One-sentence rule + many inputs = one method
with many cases.

### Common mistakes

1. **The loop-in-a-test.** First failure hides the rest; one blurred result.
2. **Forgetting the parameter.** `@ValueSource(strings = ...)` needs exactly one
   `String` parameter; mismatch and JUnit refuses to run the method.
3. **`null` in `@ValueSource`.** Not allowed — literals only. Null currency gets its own
   plain `@Test` (Lesson 11). A dedicated `@NullSource` annotation exists.
4. **Misreading the index.** Numbering starts at `[1]`. `[6]` is the sixth value.

### Link to the task

Subtask S6 done, word for word: one method, five cases, five separate results, and a
failing sixth case that named itself.

### Do it yourself (second laptop)

**Step 1** — add the two imports and the `rejectsMalformedCurrencyCodes` method to
`MoneyTest.java`, exactly as in the example section.

**Step 2** — run `mvn test`. Expect `Tests run: 6 ... in com.zenith.money.MoneyTest`,
overall `Tests run: 17, Failures: 0`.

**Step 3** — open `target/surefire-reports/TEST-com.zenith.money.MoneyTest.xml`; find the
five `rejectsMalformedCurrencyCodes(String)[N]` entries.

**Step 4 (red drill)** — add `"USD"` to the list. Run `mvn test`. Expect
`Tests run: 7, Failures: 1`, case `[6]`, `Expecting code to raise a throwable.` Remove
`"USD"`.

**Step 5** — run `mvn test`. Green: `Tests run: 17, Failures: 0`.

**Done when:** five results from one method, and a failing case pointing at one input.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvenTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 5})
    void isEven(int n) {
        assertEquals(0, n % 2);
    }
}
```

How many test results does this method produce? Which case fails, and what does its
failure message say? Do the other cases still run and pass?

**Answer.** Three results — one per value. Case `[3]`, the value `5`, fails:
`5 % 2` is `1`, so the message is `expected: <0> but was: <1>`. Cases `[1]` and `[2]`
(values 2 and 4) run regardless and pass. A failing case never stops the others.

**Question 2.** Version A is a plain test with a loop; version B is parameterised:

```java
// Version A
@Test
void rejectsBadCodes() {
    for (String bad : List.of("", "GB", "123")) {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), bad))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

// Version B
@ParameterizedTest
@ValueSource(strings = {"", "GB", "123"})
void rejectsBadCode(String bad) {
    assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), bad))
            .isInstanceOf(IllegalArgumentException.class);
}
```

A bug makes `Money` accept **both** `"GB"` and `"123"` without throwing. What does each
version report on the next `mvn test`?

**Answer.** Version A: the loop checks `""` (still throws, fine), reaches `"GB"`, the
assertion fails with `Expecting code to raise a throwable.`, and the test method stops
there — `"123"` is **never checked**. The report shows **one** failure and does not name
which value broke. Version B: all three cases run. `[1]` passes, `[2]` fails, `[3]`
fails. The report shows **two** failures, each named by its case — both broken values are
visible immediately.

### Summary for revision

- `@ParameterizedTest` + `@ValueSource`: one method, one parameter, one run per value.
- Each case = full lifecycle round + its own line in the report, numbered from `[1]`.
- A failing case does not stop the others — unlike a loop inside a plain test.
- `@ValueSource` takes literals of one type; no `null`; one parameter only.
- Maven names cases by index; IDEs also show the value.

---

## Lesson 10 — `@CsvSource`: rows with more than one input (serves S7)

### Definition

`@CsvSource` supplies inputs where each case needs **several values at once**. Each
string is one row; commas split it into columns; columns fill the method's parameters in
order, with text converted to each parameter's type. Each row runs and reports as its own
test. (CSV = comma-separated values.)

Import: `org.junit.jupiter.params.provider.CsvSource`.

### Why it is needed

`@ValueSource` carries one value per case — enough for bad currency codes. A tiered
pricing test needs a **pair** per case: the quantity, and the price that quantity should
produce. One value cannot carry a question and its expected answer. A row can.

### How it works

Row `"5, 45.00"` feeding `void pricesEachQuantityTier(int quantity, BigDecimal expectedPrice)`:

1. Split at the comma: `"5"` and `"45.00"`. Surrounding spaces are trimmed.
2. Convert each piece to its parameter's type: `int` 5, `BigDecimal` 45.00. Built in for
   common types.
3. Run one full lifecycle round with both values filled in.
4. Each row gets its own report line: `[1]`, `[2]`, `[3]`.

### The example — tiered pricing

Production class `src/main/java/com/zenith/money/PricingRule.java`:

```java
package com.zenith.money;

import java.math.BigDecimal;

/**
 * Tiered ticket pricing: the more tickets you buy, the cheaper each one gets.
 *
 *   1 to 4 tickets   -> 50.00 GBP each
 *   5 to 19 tickets  -> 45.00 GBP each
 *   20 or more       -> 40.00 GBP each
 */
public class PricingRule {

    public Money pricePerTicket(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1, was " + quantity);
        }
        if (quantity >= 20) {
            return new Money(new BigDecimal("40.00"), "GBP");
        }
        if (quantity >= 5) {
            return new Money(new BigDecimal("45.00"), "GBP");
        }
        return new Money(new BigDecimal("50.00"), "GBP");
    }
}
```

Test `src/test/java/com/zenith/money/PricingRuleTest.java` — the task's exact rows:

```java
package com.zenith.money;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingRuleTest {

    @ParameterizedTest
    @CsvSource({
            "1,  50.00",
            "5,  45.00",
            "20, 40.00"
    })
    void pricesEachQuantityTier(int quantity, BigDecimal expectedPrice) {
        PricingRule rule = new PricingRule();

        Money price = rule.pricePerTicket(quantity);

        assertEquals(new Money(expectedPrice, "GBP"), price);
    }
}
```

### Walkthrough — the real runs

**Green:** `Tests run: 3 ... in com.zenith.money.PricingRuleTest`, and the report file
lists `pricesEachQuantityTier(int, BigDecimal)[1]`, `[2]`, `[3]` — each row reported
separately.

**Red drill A — the scale trap.** Row two changed to `"5,  45.0"`:

```text
[ERROR] ...pricesEachQuantityTier(int, BigDecimal)[2] -- <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <Money[amount=45.0, currency=GBP]> but was: <Money[amount=45.00, currency=GBP]>
```

`BigDecimal` remembers how many digits follow the point, and its `equals` demands an
exact match. `Money` is a record; record equality compares fields; the difference bubbles
up. Rule: write expected amounts with the same digits the code produces — `45.00`.

**Red drill B — swapped columns.** Row one changed to `"50.00, 1"`:

```text
[ERROR] ...pricesEachQuantityTier(int, BigDecimal)[1] -- <<< ERROR!
org.junit.jupiter.api.extension.ParameterResolutionException: Error converting parameter at index 0: Failed to convert String "50.00" to type int
```

Column one must fill `int quantity`; `"50.00"` is not an int. Counts as an **Error** —
the test never ran; its inputs could not be built.

### Where it is used

Table tests: pricing tiers, tax brackets, shipping cost by weight band, date formatting
in/out pairs. Input columns + expected-result column, one row per case.

### Common mistakes

1. **Swapped or misaligned columns** — demoed above. Keep column order = parameter order.
2. **Wrong scale in expected numbers** — demoed above. `45.0` ≠ `45.00` to `BigDecimal`.
3. **Column count ≠ parameter count** — same `ParameterResolutionException` family.
4. **A value containing a comma** — wrap in single quotes: `"1, 'Smith, John'"`.

### Link to the task

Subtask S7 done with the task's exact rows under a tiered `PricingRule`, each row
reported separately.

### Do it yourself (second laptop)

**Step 1** — create `PricingRule.java` in `src/main/java/com/zenith/money/` as shown.

**Step 2** — create `PricingRuleTest.java` in `src/test/java/com/zenith/money/` as shown.

**Step 3** — run `mvn test`. Expect `Tests run: 3` for the class, overall
`Tests run: 20, Failures: 0`.

**Step 4** — open `target/surefire-reports/TEST-com.zenith.money.PricingRuleTest.xml`;
confirm the three `[N]` entries.

**Step 5 (drill A)** — change `"5,  45.00"` to `"5,  45.0"`. Run. Expect case `[2]`:
`expected: <Money[amount=45.0, ...]> but was: <Money[amount=45.00, ...]>`. Revert.

**Step 6 (drill B)** — change `"1,  50.00"` to `"50.00, 1"`. Run. Expect case `[1]` as
Error: `Failed to convert String "50.00" to type int`. Revert.

**Step 7** — run `mvn test`. Green: `Tests run: 20, Failures: 0`.

**Done when:** three rows gave three results and both drill failures are explainable from
memory.

### Practice questions

**Question 1.** Here is a complete test class:

```java
package com.zenith.testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquareTest {

    @ParameterizedTest
    @CsvSource({
            "3, 9",
            "4, 16",
            "5, 25"
    })
    void squares(int n, int expected) {
        assertEquals(expected, n * n);
    }
}
```

How many test results appear in the report? Which values does case `[3]` receive? Do all
cases pass?

**Answer.** Three results, one per row. Case `[3]` is the third row: `n = 5`,
`expected = 25`. All pass: 9 = 3×3, 16 = 4×4, 25 = 5×5.

**Question 2.** `PricingRule` returns `new Money(new BigDecimal("40.00"), "GBP")` for
quantities of 20 or more. A teammate writes the row `"20, 40.0"` into the `@CsvSource` of
`void pricesEachQuantityTier(int quantity, BigDecimal expectedPrice)`, which asserts
`assertEquals(new Money(expectedPrice, "GBP"), price)`. Does the case pass or fail? What
does the failure line print? Why are `40.0` and `40.00` not equal here?

**Answer.** It fails. The line prints:
`expected: <Money[amount=40.0, currency=GBP]> but was: <Money[amount=40.00, currency=GBP]>`.
`BigDecimal` stores the number **and** its digit count after the point (its scale).
`40.0` has scale 1, `40.00` has scale 2, and `BigDecimal.equals` requires both value and
scale to match. `Money` is a record, so its equality compares the `amount` fields with
exactly that strict `equals`.

### Summary for revision

- `@CsvSource`: one string per row, commas split columns, columns fill parameters in
  order, text converted to parameter types.
- Each row = one full lifecycle round + one report line, numbered from `[1]`.
- Classic use: table tests with inputs and an expected-result column.
- Watch the two demons: column order and `BigDecimal` scale.
- Conversion failure = Error (test never ran); wrong value = Failure.

---

## Lesson 11 — The full `Money` suite (serves S8, the deliverable)

### Definition

An **invariant** is a rule that must hold for every `Money` that ever exists: never
negative, always a real currency code. The task's hard sentence: **a value object with
untested invariants is not a value object** — the point of `Money` is that it can be
trusted blindly everywhere, and untested rules are rules you only *hope* hold.

### How the lessons fit together

| Suite part | Tool | Lesson |
| --- | --- | --- |
| Fresh, isolated tests, no shared state | plain fields, no statics | 2–6 |
| "This must throw" with type + message | `assertThatThrownBy` | 7 |
| Code handed over unrun | the lambda | 8 |
| Many bad inputs, one method | `@ParameterizedTest` + `@ValueSource` | 9 |
| Input + expected answer pairs | `@CsvSource` | 10 |

### The approach in plain words

List `Money`'s promises: amount never negative and never null; currency exactly three
uppercase letters, never null; adding works within one currency and refuses across;
multiplying scales the amount. Then pick the cheapest tool per invariant: one-off cases
(nulls, the add happy path, the mismatch) → plain `@Test` (remember: `null` cannot ride
in `@ValueSource`); many single bad inputs → `@ValueSource`; input+expected pairs →
`@CsvSource`.

### The implementation

File: `src/test/java/com/zenith/money/MoneyTest.java` — 19 cases.

```java
package com.zenith.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The full Money suite: every invariant guarded.
 * A value object with untested invariants is not a value object.
 */
class MoneyTest {

    // ---- invariant 1: the amount must be >= 0 ----

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1.00", "-99999.99"})
    void rejectsNegativeAmounts(String negative) {
        assertThatThrownBy(() -> new Money(new BigDecimal(negative), "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void acceptsZeroBecauseZeroIsNotNegative() {
        Money zero = new Money(new BigDecimal("0.00"), "GBP");

        assertEquals(new BigDecimal("0.00"), zero.amount());
    }

    // ---- invariant 2: the currency must be three uppercase letters ----

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "GB", "GBPX", "123", "gbp", "GbP", "US$"})
    void rejectsMalformedCurrencyCodes(String badCode) {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), badCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    // ---- invariant 3: adding works, and only within one currency ----

    @Test
    void addsAmountsOfTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money twoFifty = new Money(new BigDecimal("2.50"), "GBP");

        assertEquals(new Money(new BigDecimal("12.50"), "GBP"), ten.plus(twoFifty));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        Money gbp = new Money(new BigDecimal("10.00"), "GBP");
        Money eur = new Money(new BigDecimal("10.00"), "EUR");

        assertThatThrownBy(() -> gbp.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    // ---- invariant 4: multiplying by a quantity works ----

    @ParameterizedTest
    @CsvSource({
            "12.50, 4,  50.00",
            "9.99,  3,  29.97",
            "0.00,  10, 0.00"
    })
    void multipliesByAQuantity(BigDecimal amount, int quantity, BigDecimal expected) {
        Money price = new Money(amount, "GBP");

        assertEquals(new Money(expected, "GBP"), price.times(quantity));
    }
}
```

### Design decisions

1. **Negatives as strings**, converted with `new BigDecimal(negative)` — the `doubles =`
   option rounds, which is the exact thing `Money` exists to avoid.
2. **The zero test** guards the boundary of "rejects negatives": zero must be
   **accepted**. Without it, `signum() <= 0` would slip through green.
3. **Eight malformed codes in one method** — the learning outcome's "eight input cases in
   one test method": empty, blank, short, long, digits, lowercase, mixed, symbol.
4. **Messages pin the rule.** All invariants throw `IllegalArgumentException`; the words
   `"amount"` and `"currency"` distinguish which rule fired (Lesson 7's wrong-reason
   trap).
5. **Whole-`Money` equality** on happy paths via record equality, expected amounts
   written at the scale the code produces (`50.00`, not `50.0`).
6. **No `@BeforeEach` — on purpose.** Each test builds different objects; `Money` is
   immutable; nothing is shared or reset. Lifecycle hooks are tools, not rituals.

### The proof — real runs

Green: `Tests run: 19, Failures: 0` in `MoneyTest`, 33 in the project.

Sabotage drill: the currency rule in `Money.java` was loosened from `[A-Z]{3}` to
`[A-Za-z]{3}` (quietly accepting lowercase). Real output:

```text
[ERROR] Tests run: 19, Failures: 2, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.money.MoneyTest
[ERROR] com.zenith.money.MoneyTest.rejectsMalformedCurrencyCodes(String)[6] -- <<< FAILURE!
[ERROR] com.zenith.money.MoneyTest.rejectsMalformedCurrencyCodes(String)[7] -- <<< FAILURE!
```

Cases `[6]` and `[7]` — `"gbp"` and `"GbP"` — went red and named the exact hole. That is
what covering an invariant means: the rule cannot disappear without a pointed failure.
Rule restored; green again.

### Link to the task

S8 done. The task checklist: rejects negatives ✓ (3), rejects null/malformed currency ✓
(1 + 8), adds correctly ✓, refuses mismatched currencies ✓, multiplies correctly ✓ (3).
The "Done when" line is satisfied: parameterised case ✓, exception case ✓,
order-dependent test built and removed ✓ (Lessons 5–6).

### Do it yourself (second laptop)

**Step 1** — replace `MoneyTest.java` entirely with the file shown above.

**Step 2** — run `mvn test`. Expect `Tests run: 19, Failures: 0` for the class, overall
`Tests run: 33, Failures: 0`.

**Step 3 (sabotage drill)** — in `Money.java`, change `matches("[A-Z]{3}")` to
`matches("[A-Za-z]{3}")`. Run `mvn test`. Expect exactly two failures:
`rejectsMalformedCurrencyCodes(String)[6]` and `[7]`, both
`Expecting code to raise a throwable.`

**Step 4** — restore `[A-Z]{3}`. Run `mvn test`. Green: `Tests run: 33, Failures: 0`.

**Done when:** the suite is green and you watched it catch a deleted invariant by name.

### Practice questions

**Question 1.** Here is a complete but shortened `Money` suite; it is green. `Money` also
has a `plus` rule refusing mismatched currencies and a `times(int quantity)` method:

```java
class MoneyTest {

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1.00"})
    void rejectsNegativeAmounts(String negative) {
        assertThatThrownBy(() -> new Money(new BigDecimal(negative), "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GB", "123"})
    void rejectsMalformedCurrencyCodes(String badCode) {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), badCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void addsAmountsOfTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money twoFifty = new Money(new BigDecimal("2.50"), "GBP");
        assertEquals(new Money(new BigDecimal("12.50"), "GBP"), ten.plus(twoFifty));
    }
}
```

Name the invariants this suite does not guard, and one silent regression per gap.

**Answer.** Unguarded: (1) the currency-mismatch rule in `plus` — delete that check and
cross-currency additions are silently allowed, build stays green; (2) multiplication —
`times` could return a wrong amount (even `return this;`) with a green build; (3) null
rejection — `amount == null` or `currency == null` checks could be removed unnoticed;
(4) zero acceptance — the negative check could be tightened to `signum() <= 0`, wrongly
rejecting zero, and nothing would fail.

**Question 2.** A new rule: `times(int quantity)` must throw `IllegalArgumentException`
with a message containing `"quantity"` for negative quantities. Fact: today, a negative
quantity makes `times` compute a negative amount, and the **constructor** rejects it with
the message `"amount must be >= 0, was ..."`. Which test shape fits, and why is
`hasMessageContaining("quantity")` essential here?

**Answer.** Shape: `@ValueSource(ints = {-1, -5, -100})` — many single bad inputs, one
rule:

```java
@ParameterizedTest
@ValueSource(ints = {-1, -5, -100})
void rejectsNegativeQuantities(int badQuantity) {
    Money price = new Money(new BigDecimal("10.00"), "GBP");

    assertThatThrownBy(() -> price.times(badQuantity))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity");
}
```

The message check is essential because a type-only test **already passes today**: the
constructor throws `IllegalArgumentException` (message about `"amount"`) before the new
rule exists. It would pass for the wrong reason and prove nothing.
`hasMessageContaining("quantity")` stays red until `times` itself enforces the new rule.

### Summary for revision

- List the invariants first; pick the cheapest tool per invariant: plain `@Test` for
  one-offs and nulls, `@ValueSource` for many bad inputs, `@CsvSource` for
  input+expected pairs.
- Test both sides of every boundary (negative rejected, zero accepted).
- Same exception type everywhere → the message word identifies the rule.
- A suite is proven by sabotage: delete a rule, expect a named failure.
- `@BeforeEach` only when there is shared setup to centralise; immutable value objects
  often need none.

---

## Lesson 12 — The report files under `target/surefire-reports/` (serves S9)

### Definition

Every `mvn test` run writes its results to disk, into `target/surefire-reports/`. For
each test class, Surefire writes two files: a short `.txt` summary for humans, and a
detailed `TEST-....xml` file for machines. The XML is the official record of the run —
it is what a CI server reads when it tells you your build failed.

### Why

Console output scrolls away and dies with the terminal. A CI server (the build machine
that runs `mvn test` on every push) has no human watching; it needs a file it can parse,
store, and show later. When a build goes red overnight, these files are the evidence:
which test, which input case, which message. This is what a CI build will show you in
Phase 1.

### How it works

1. `mvn test` runs the suite, printing to the console as usual.
2. At the same time, for each test class, Surefire writes two files into
   `target/surefire-reports/`, named after the full class name.
3. The `.txt` file holds the one familiar summary line.
4. The `TEST-....xml` file holds everything: a `<testsuite>` header with the counts, an
   environment snapshot (Java version, classpath), and one `<testcase>` element per test
   — including a separate one for every parameterised case `[N]`. A failed test carries a
   nested `<failure>` element with the message and full stack trace.
5. `target/` is generated output: delete it any time, the next build recreates it, and it
   never goes into version control.

### Real files from this project

```text
target/surefire-reports/
  TEST-com.zenith.money.MoneyTest.xml            com.zenith.money.MoneyTest.txt
  TEST-com.zenith.money.PricingRuleTest.xml      com.zenith.money.PricingRuleTest.txt
  ...two files per test class, eight classes
```

The `.txt` for `MoneyTest`:

```text
-------------------------------------------------------------------------------
Test set: com.zenith.money.MoneyTest
-------------------------------------------------------------------------------
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.051 s -- in com.zenith.money.MoneyTest
```

The XML header for the same class — the console numbers as machine-readable attributes:

```xml
<testsuite name="com.zenith.money.MoneyTest" time="0.051"
           tests="19" errors="0" skipped="0" failures="0" flakes="0">
```

### What a failure looks like on disk (real run)

One multiply row was broken on purpose — expected `29.96` where the code produces
`29.97`. The `<testcase>` element that landed in the XML:

```xml
<testcase name="multipliesByAQuantity(BigDecimal, int, BigDecimal)[2]"
          classname="com.zenith.money.MoneyTest" time="0.004">
  <failure message="expected: &lt;Money[amount=29.96, currency=GBP]&gt; but was: &lt;Money[amount=29.97, currency=GBP]&gt;"
           type="org.opentest4j.AssertionFailedError"><![CDATA[
org.opentest4j.AssertionFailedError: expected: <Money[amount=29.96, currency=GBP]> but was: <Money[amount=29.97, currency=GBP]>
	at org.junit.jupiter.api.AssertionFailureBuilder.build(...)
...]]></failure>
</testcase>
```

| Piece | Meaning |
| --- | --- |
| `name="...[2]"` | Which test, down to the parameterised case number. |
| `classname` | Which class. |
| `<failure ...>` present | This case failed; passing cases have no such element. |
| `message="expected: ... but was: ..."` | The same assertion text as the console (`&lt;`/`&gt;` are XML spellings of `<`/`>`). |
| `type="org.opentest4j.AssertionFailedError"` | A failure (false assertion). An escaped exception appears as an `<error>` element instead. |

A CI page saying "33 tests, 1 failed: `multipliesByAQuantity[2]` — expected 29.96 but
was 29.97" was produced by parsing exactly this element.

### Where it is used

GitHub Actions, Jenkins, GitLab: run the build, collect `target/surefire-reports/*.xml`,
render the red/green test page from them. Knowing the shape means the raw files can be
read straight off a build machine when the pretty page is not enough.

### Common mistakes

1. **Reading a stale report.** The files describe the last completed run only. Edit code,
   forget to re-run, and the reports still describe the old world. Check timestamps.
2. **Committing `target/` to version control.** Generated output; never belongs in git.
3. **Hunting for parameter values in the case name.** XML names cases `[2]`, index only.
   Count into the source list or read the failure message.
4. **Confusing the two files.** `.txt` = one-line summary. `TEST-....xml` = full record.
   For diagnosis, go straight to the XML.

### Do it yourself (second laptop)

Needs `scratch-app` with the full suite from Lesson 11.

**Step 1** — run `mvn test`, then `ls target/surefire-reports/`. Expect two files per
test class: `ClassName.txt` and `TEST-ClassName.xml`.

**Step 2** — open `com.zenith.money.MoneyTest.txt`. Expect the one summary line with
`Tests run: 19`.

**Step 3** — open `TEST-com.zenith.money.MoneyTest.xml`. Find the
`<testsuite ... tests="19" failures="0">` header and the nineteen `<testcase>` elements,
including `rejectsMalformedCurrencyCodes(String)[1]` through `[8]`.

**Step 4 (failure drill)** — in `MoneyTest.java`, change the row `"9.99,  3,  29.97"` to
`"9.99,  3,  29.96"`. Run `mvn test` (expect `BUILD FAILURE`). Reopen the XML and find
the `<failure>` element inside `multipliesByAQuantity(...)[2]`.

**Step 5** — change the row back. Run `mvn test`. Green, and the XML says `failures="0"`
again.

**Done when:** you can point at the exact XML element a CI page would have shown you.

### Practice questions

**Question 1.** A fragment of a real Surefire XML report:

```xml
<testsuite name="com.zenith.money.MoneyTest" tests="19" errors="0" skipped="0" failures="1">
  ...
  <testcase name="rejectsNegativeAmounts(String)[3]" classname="com.zenith.money.MoneyTest" time="0.002">
    <failure message="Expecting code to raise a throwable."
             type="java.lang.AssertionError">...</failure>
  </testcase>
  ...
</testsuite>
```

How many tests ran and how many failed? Which exact case failed? Did the code under test
throw, or not?

**Answer.** The header says `tests="19" failures="1"`: nineteen ran, one failed. The
failed case is `rejectsNegativeAmounts(String)[3]` — the third value in that test's
`@ValueSource` list. The message `Expecting code to raise a throwable.` is AssertJ's
text for "the lambda ran to the end and nothing was thrown" — so the code did **not**
throw. The rejection rule is missing or weakened for that input.

**Question 2.** A CI build failed at night; the console output is gone, but
`target/surefire-reports/` was kept. Which file holds the full failure detail for class
`com.zenith.money.MoneyTest`, and what is in it that the `.txt` does not have? After
fixing the bug, why is re-reading that same file the wrong way to check the fix?

**Answer.** Open `TEST-com.zenith.money.MoneyTest.xml`. Beyond the `.txt`'s single
summary line, it holds one `<testcase>` element per test case (including each
parameterised case `[N]`) and, inside any failed case, a `<failure>` element with the
assertion message and the full stack trace, plus an environment snapshot. Re-reading it
after a fix is wrong because the reports are a snapshot of the **last completed run** —
editing code does not touch them. Only a fresh `mvn test` rewrites the folder; verify
the fix by re-running and reading the new report (`failures="0"`, fresh timestamp).

### Summary for revision

- `mvn test` writes two files per test class into `target/surefire-reports/`:
  `ClassName.txt` (summary line) and `TEST-ClassName.xml` (full record).
- The XML has `<testsuite>` counts and one `<testcase>` per case; failures nest a
  `<failure>` element with message + stack trace; escaped exceptions nest `<error>`.
- CI systems parse exactly these XML files to render their test pages.
- Reports describe the last completed run only — re-run before re-reading.
- `target/` is generated: safe to delete, never committed.

---

## Lesson 13 — Three tests that look fine but are order-dependent (serves S10 + checkpoint)

### Definition

A gallery of three test classes. Each reads naturally, compiles, and runs green. Each is
secretly **order-dependent**: at least one test only passes because another test happens
to run before or after it. All three were built, run, exposed with real output, and then
deleted.

### Why

In real code review, nobody labels the trap. These bugs hide in code that *looks*
careful — even decorated with the right annotations. The skill is pattern recognition:
reading a green test file and spotting the time bomb anyway.

### How it works — the one question

For any test class ask: **what survives from one test to the next, and does any test
change it?** Anything that survives is a bridge between tests; anything that changes it
makes the bridge load-bearing. Three classic hiding places:

1. a `static` field in the **test class**,
2. an object created in `@BeforeAll` that a test **mutates**,
3. a `static` field hidden in the **class under test** — the sneakiest, because the test
   class itself looks spotless.

### Example 1 — the "cache" that is really a bridge

```java
class CustomerCacheTest {

    private static final Map<String, String> cache = new HashMap<>();

    @Test
    void storesACustomer() {
        cache.put("C1", "Asha");

        assertEquals(1, cache.size());
    }

    @Test
    void findsTheStoredCustomer() {
        assertEquals("Asha", cache.get("C1"));
    }
}
```

**Why it looks fine.** Reads like a story: store, then find. `final` makes it look extra
safe. Full run: `Tests run: 2, Failures: 0`.

**What makes it fragile.** `final` only stops the variable from pointing at a different
map; the map's **contents** can still change. `findsTheStoredCustomer` never puts
`"Asha"` in — it depends on the other test having run first. Real run alone:

```text
mvn test -Dtest="CustomerCacheTest#findsTheStoredCustomer"

[ERROR] CustomerCacheTest.findsTheStoredCustomer:23 expected: <Asha> but was: <null>
```

Passes together, fails alone. **Fix:** drop `static`, fill the map in `@BeforeEach`, and
let each test find what it stored itself.

### Example 2 — `@BeforeAll` used by the letter, broken in spirit

```java
class SharedBasketTest {

    static List<String> basket;

    @BeforeAll
    static void createBasket() {
        basket = new ArrayList<>();
    }

    @Test
    void startsEmpty() {
        assertEquals(0, basket.size());
    }

    @Test
    void holdsAnAddedItem() {
        basket.add("ticket");

        assertEquals(1, basket.size());
    }
}
```

**Why it looks fine.** `@BeforeAll` is used exactly as the books describe: create the
shared thing once. Each test alone reads sensibly. Full run: green.

**What makes it fragile.** The `@BeforeAll` rule is not just "expensive setup" — it is
"expensive **and read-only**". `holdsAnAddedItem` mutates the shared basket, and
`startsEmpty` is only true *before* that mutation. Forcing the other order (staging with
`@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` plus `@Order(1)`/`@Order(2)`,
an annotation that runs tests by the numbers you give — a flashlight, never a fix):

```text
[ERROR] SharedBasketTest.startsEmpty:27 expected: <0> but was: <1>
```

One reorder away from red the whole time. **Fix:** make it a `@BeforeEach`, drop
`static` — fresh basket per test, both orders pass.

### Example 3 — the spotless test class with a dirty secret

The class under test:

```java
/** Issues ticket numbers. The counter is static: shared by ALL instances. */
public class TicketCounter {

    private static int issued = 0;

    public int issueTicket() {
        issued = issued + 1;
        return issued;
    }
}
```

The tests:

```java
class TicketCounterTest {

    @Test
    void firstTicketIsNumberOne() {
        TicketCounter counter = new TicketCounter();

        assertEquals(1, counter.issueTicket());
    }

    @Test
    void ticketNumbersGrowByOne() {
        TicketCounter counter = new TicketCounter();

        int first = counter.issueTicket();

        assertEquals(first + 1, counter.issueTicket());
    }
}
```

**Why it looks fine.** The test class is textbook: no shared fields, every test creates
its own fresh `TicketCounter`. Full run: green.

**What makes it fragile.** The shared state is inside `TicketCounter`: `issued` is
`static`, so all the "fresh" objects share one counter — `new TicketCounter()` resets
nothing. `firstTicketIsNumberOne` asserts the absolute number `1`, only true if it runs
before any ticket is issued anywhere. With `ticketNumbersGrowByOne` forced first (it
issues tickets 1 and 2):

```text
[ERROR] TicketCounterTest.firstTicketIsNumberOne:18 expected: <1> but was: <3>
```

Note the survivor: `ticketNumbersGrowByOne` asserts a *relative* fact (`first + 1`), so
it passes anywhere. Absolute assertions on shared counters are the fragile ones.
**Fix:** a production-code bug — remove `static` from `issued`. No test-side trick truly
fixes it.

After capturing the outputs, all three classes were deleted and the suite re-ran green:
`Tests run: 33, Failures: 0, BUILD SUCCESS`.

### Where it is used

Code review and flaky-build triage. A green test file in a PR gets scanned for exactly
these three shapes. A build that fails only sometimes gets the same question: what
survives between tests, and who mutates it?

### Common mistakes

1. **"No shared field in the test class, so it's safe."** Example 3 disproves it.
2. **"It's `final`, so it can't change."** `final` locks the variable, not the object's
   contents.
3. **Fixing by pinning order** with `@TestMethodOrder`. That hides the dependency
   forever; here it was only a flashlight to stage the failure.
4. **Trusting the green.** All three classes were green when written. Green proves the
   assertions held *in this order, this run* — nothing more.

### Do it yourself (second laptop)

Needs `scratch-app` green at 33 tests.

**Step 1** — create the three test classes above (plus `TicketCounter.java`) in
`src/test/java/com/zenith/testing/`. Run `mvn test`. Expect all green:
`Tests run: 39, Failures: 0`.

**Step 2** — run `mvn test -Dtest="CustomerCacheTest#findsTheStoredCustomer"`.
Expect: `expected: <Asha> but was: <null>`.

**Step 3** — add `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` to
`SharedBasketTest` and `TicketCounterTest`, with `@Order(1)` on the mutating test and
`@Order(2)` on the asserting test. Run
`mvn test -Dtest="SharedBasketTest,TicketCounterTest"`.
Expect: `expected: <0> but was: <1>` and `expected: <1> but was: <3>`.

**Step 4** — delete all four files. Run `mvn test`.
Expect: `Tests run: 33, Failures: 0, BUILD SUCCESS`.

**Done when:** you exposed all three and the project is back to green 33.

### Checkpoint — the task's four questions, answered

**Question 1. What is the difference between `@BeforeEach` and `@BeforeAll`, and when is
the second one safe?**

**Answer.** `@BeforeEach` runs before **every single test**. JUnit creates a brand-new
test-class object for each test, then runs `@BeforeEach` on it — so every test starts
from the same fresh world. It must not be `static`. `@BeforeAll` runs **once**, before
all tests in the class, and must be `static` (unless the class uses
`@TestInstance(Lifecycle.PER_CLASS)`); whatever it creates is shared by every test.
`@BeforeAll` is safe only when the shared thing is **expensive and read-only** — loading
a file, starting a container. The moment any test mutates it, the tests become
order-dependent (Example 2 above is exactly that failure).

**Question 2. Why must `assertThatThrownBy` be given a lambda rather than a value?**

**Answer.** Java evaluates arguments **before** calling a method. If the throwing code
were written directly as an argument, the exception would fire while the argument is
being computed — before `assertThatThrownBy` ever runs — and would escape as an Error
instead of being judged. A lambda is code **packaged unrun**: the assertion receives the
package, runs it inside its own try/catch, catches whatever flies out, and checks the
type and message. The compiler enforces this: the parameter type is `ThrowingCallable`,
so passing a plain value does not compile
(`Money cannot be converted to ThrowableAssert.ThrowingCallable`).

**Question 3. When is `@ParameterizedTest` the right choice, and when would it make a
test harder to read?**

**Answer.** Right when **one rule** must be checked against many inputs: `@ValueSource`
for many single bad inputs (eight malformed currency codes in one method), `@CsvSource`
for input+expected pairs (quantity → tier price). Each case runs its own full lifecycle,
is reported separately (`[1]`…`[N]`), and one failing case does not stop the others.
Wrong when the cases are not really the same rule — forcing unrelated scenarios into one
parameter list, needing `if` branches per input, or piling up so many columns that a row
becomes a puzzle. Then separate plain `@Test` methods with good names are clearer. Also
remember: `@ValueSource` cannot carry `null`.

**Question 4. Describe an order-dependent test and why it wastes so much time.**

**Answer.** A test that only passes (or only fails) depending on which tests ran before
it, because state survives between tests: a `static` field in the test class, a mutated
`@BeforeAll` object, or `static` state inside the class under test. Its signature:
passes together, fails alone — or flips when tests are renamed, reordered, or run in
parallel. It wastes time because the failure points at the **wrong place**: the test
that goes red is innocent and the cause lives in another test entirely, so debugging
means reading everything, not the failing method. And because it fails only sometimes,
people re-run until green, stop trusting red builds, and real bugs start slipping
through the noise.

### Summary for revision

- One question finds them all: what survives between tests, and who mutates it?
- Three hiding places: static test-class field, mutated `@BeforeAll` object, static
  state inside the class under test.
- `final` on a collection locks the variable, not the contents.
- Relative assertions survive order changes; absolute assertions on shared state break.
- Exposure tools: run one test alone (`-Dtest`), or stage the other order with
  `@TestMethodOrder` — staging only, never the fix.
- Green today proves this order today, nothing more.

---

## Task F7.2 — closed

- S1–S10 all done; checkpoint answered above.
- **Done when** met: `MoneyTest` covers every invariant (19 cases) including
  parameterised (`@ValueSource`, `@CsvSource`) and exception (`assertThatThrownBy`)
  cases; an order-dependent test was built (Lesson 5) and removed (Lesson 6).
- Final state of `scratch-app`: `Tests run: 33, Failures: 0, BUILD SUCCESS` across
  `MoneyTest` (19), `PricingRuleTest` (3), and the lifecycle demo classes (11).

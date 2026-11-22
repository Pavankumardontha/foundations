# F7.1 — Your first test, and what makes a test worth having

**Goal of the task.** Write a test laid out as Arrange–Act–Assert, name it so that a
failure report is readable without opening the file, and read an AssertJ failure message
to find the problem in seconds.

**Done when:** `Money` has at least three named, well-structured tests, and you have read
a real AssertJ failure message next to the poor `assertTrue` alternative.

**You need first:** the `Money` record idea from Task F3.6 (an object that cannot hold
invalid data), and the Maven lifecycle from Task F1.5 (so `mvn test` is not magic).
Every lesson here re-explains what it needs, in place.

**The story in two sentences.** A test is code whose only job is to fail loudly when the
code it covers is wrong. This task teaches you to write tests whose failure message alone
tells you what broke, where, and by how much.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **JUnit 5** | The library that finds your test methods and runs them. |
| **`@Test`** | Marks a method as a test. JUnit runs every method with this annotation. |
| **AssertJ** | An assertion library. `assertThat(x).isEqualTo(y)` reads as a sentence and produces far better failure messages than JUnit's built-in assertions. |
| **Assertion** | A statement about what should be true. If it is not, the test fails with a message. |
| **Arrange–Act–Assert** | The three parts of every test: set up the situation, do the one thing, check the result. |
| **`@DisplayName`** | A readable sentence shown in test reports instead of the method name. |
| **Test report** | The output of `mvn test`, including which tests failed and why. |
| **Behaviour versus implementation** | Testing what a class does for its caller, rather than how it does it inside. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Confirm the setup: `spring-boot-starter-test` in `pom.xml` brings JUnit 5, AssertJ, and Mockito. Run `mvn test` on the empty project and read what it prints. |
| S2 | Write one test for `Money`, laid out in three visually separate blocks with blank lines between them. |
| S3 | Adopt Arrange–Act–Assert permanently. Know why the blank lines matter: the one action must be visible in under a second. |
| S4 | Name tests as sentences describing behaviour: `rejectsNegativeAmount`, `refusesToAddDifferentCurrencies`. Never `test1`. The name is the failure report. |
| S5 | Add `@DisplayName("refuses to add GBP to EUR")` to one test, run it, and decide your own convention. |
| S6 | Break a test on purpose. Read the AssertJ message. Rewrite the assertion with `assertTrue` and break it again. Compare the two messages. |
| S7 | Learn the five everyday AssertJ methods: `isEqualTo`, `isEqualByComparingTo`, `contains`, `hasSize`, `isNotNull`. |
| S8 | Learn why `BigDecimal` needs `isEqualByComparingTo`: `15.0` and `15.00` are not `equals`, because scale is part of equality. |
| S9 | Write the rule down and apply it from here on: test behaviour, not implementation. |
| S10 | See the same test written well and written badly, and what a reader learns from each failure output. |
| CP | Checkpoint: four questions, out loud. |

## Roadmap — 12 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | What a test is, and its one job | A test is code that checks code, and it must be able to fail. A test that cannot fail is worse than no test. | Focus, CP |
| 2 | The test project and `mvn test` | Where test code lives, what `spring-boot-starter-test` brings, and what `mvn test` prints when there are zero tests. | S1 |
| 3 | `@Test` — how JUnit finds and runs a test | A test method passes when it returns, and fails when it throws. One green run and one red run, for real. | S2 |
| 4 | `assertThat` — your first real assertion | What an assertion is, and the first passing `Money` test. | S2 |
| 5 | Arrange–Act–Assert | The three blocks, the blank lines, and why the middle block must stay one line. | S2, S3, CP1 |
| 6 | Test names are the failure report | Names as behaviour sentences. Never `test1`. | S4 |
| 7 | `@DisplayName` | Readable sentences in reports, and when a good method name already does the job. | S5 |
| 8 | Break it on purpose — reading failure messages | AssertJ's expected-versus-actual output beside `assertTrue`'s "expected true". The entire argument for AssertJ. | S6, CP2 |
| 9 | `BigDecimal` equality and `isEqualByComparingTo` | Why `15.0` and `15.00` are not `equals`, and the safe comparison for money. | S8, CP3 |
| 10 | The five everyday AssertJ methods | `isEqualTo`, `isEqualByComparingTo`, `contains`, `hasSize`, `isNotNull` — each one run once. | S7 |
| 11 | Testing that code refuses — `assertThatThrownBy` | Asserting exceptions. `rejectsNegativeAmount` and `refusesToAddDifferentCurrencies` complete the suite. | Done-when |
| 12 | Behaviour, not implementation | The rule, one test written well and badly side by side, and what each failure output teaches. | S9, S10, CP4 |

A note on `Money`. Your F3.6 `Money` stores the amount as `long amountInPaise`. This
task's example `Money` stores the amount as `BigDecimal`. Lessons 4 and 9 need the
`BigDecimal` form, so the scratch app for this task will hold a `BigDecimal` version of
your `Money`, with the same guarantees. Lesson 2 builds it.

Lesson demo files live in
`F7.1-your-first-test-and-what-makes-a-test-worth-having/lessons/`. The scratch Maven
project arrives in Lesson 2.

Lesson notes are appended below, one by one, after each lesson is confirmed in chat.

---

## Lesson 1 — What a test is, and its one job (serves the Focus bullet and the checkpoint)

### Definition

A test is a small program that runs your real code and checks the result. If the result
is wrong, the test must stop and complain loudly. That is its one job: **fail when the
code is wrong**.

### Why it is needed

Without tests you check code by running a `main` method and reading the printed lines
with your eyes. That works once, then breaks down:

- You must re-run and re-read after **every** change. Eyes get tired and skip lines.
- A real project has hundreds of behaviours. Nobody can re-read hundreds of print lines
  per change.
- Worst of all: the judgement ("looks right") lives in your head, not in the code. If a
  printed value is slightly wrong, you may not notice.

A test moves the judgement out of your head and into the program. The program itself
decides pass or fail. A thousand tests can then check themselves in seconds.

### How it works — five steps

Every test, in any language, does the same five steps:

1. It calls your code with inputs **it** chose.
2. It knows the correct answer ahead of time — the **expected value**.
3. It takes what the code really returned — the **actual value**.
4. If actual equals expected, the test **passes**. It stays quiet or prints OK.
5. If they differ, the test **fails**. It throws an error with a message.

All the value lives in step 5. A test earns its keep only on the day it fails. A test
that can never reach step 5 is worse than no test, because it makes you confident while
the code is broken.

### Terms used in this lesson

| Term | Plain meaning |
| --- | --- |
| **Expected value** | The answer the test knows is correct, written down before the code runs. |
| **Actual value** | The answer the code really produced. |
| **Pass (green)** | Actual matched expected. The test stays quiet. |
| **Fail (red)** | Actual did not match expected. The test throws an error with a message. |
| **False confidence** | Believing the code works because a test passed, when that test could never fail. |

### The example — one file, three runs

The file `lessons/L1ManualTest.java`. No libraries, just plain Java:

```java
// Lesson 1 — a test written by hand, with no libraries.
// Run with:  java L1ManualTest.java
class L1ManualTest {
    public static void main(String[] args) {
        Money ten = new Money(1000, "INR");
        Money five = new Money(500, "INR");

        Money total = ten.plus(five);

        if (total.amountInPaise() != 1500) {
            throw new AssertionError("plus is wrong: expected 1500 but got " + total.amountInPaise());
        }
        System.out.println("OK: plus adds two amounts in the same currency");
    }
}

// Your Money record from F3.6, trimmed to only what this lesson needs.
record Money(long amountInPaise, String currency) {
    Money plus(Money other) {
        return new Money(this.amountInPaise + other.amountInPaise, this.currency);
    }
}
```

**Run 1 — the code is correct.** The test creates 1000 paise and 500 paise, calls
`plus`, and expects 1500. Actual is 1500. Output:

```text
OK: plus adds two amounts in the same currency
```

**Run 2 — the code is broken on purpose.** One character changed inside `plus`: the `+`
became a `-`. The test did not change. Output:

```text
Exception in thread "main" java.lang.AssertionError: plus is wrong: expected 1500 but got 500
	at L1ManualTest.main(L1ManualTest.java:11)
```

The message carries everything: expected 1500, got 500, complaint raised at line 11.
**This failure is the test doing its job.** This run is the good news.

**Run 3 — the code stays broken, the check is weak.** The `-` stays. Only the `if` line
changed, to `if (total.amountInPaise() < 0)`. It looks like a real check, but 1000 minus
500 is 500, and 500 is not below zero. Output:

```text
OK: plus adds two amounts in the same currency
```

It prints OK **while the code is broken**. This is the dangerous case. With no test you
at least know you have not checked. With this test you believe a lie. A test that passes
no matter what you change is worse than no test.

### Where it is used

Everywhere real software is built. A team's build server runs every test on every
change. "The build is red" means some test failed, and the message should say why in
seconds. Teams ship when the build is green. That trust only works if every test can
actually go red.

### Common mistakes

- **Believing green means correct.** Green only means: the cases you checked, with the
  inputs you chose, passed. Nothing more.
- **Writing checks that cannot fail**, like the `< 0` check in Run 3. False confidence
  is the worst outcome a test can produce.
- **Checking by eye.** Printing values and reading them is not a test. The program must
  do the judging.
- **Weakening a failing test to make it green.** The red test was telling the truth.
  Fix the code, not the messenger.

### Link to the task

This lesson is the "Focus on this" bullet of F7.1: *a test exists to fail when the code
is wrong*. It is also the heart of subtask S6, where a real JUnit test gets broken on
purpose and its failure message read — exactly what Run 2 did by hand.

### Do it yourself (second laptop)

You need a JDK, version 21. Check:

```bash
java -version    # expect a line like: openjdk version "21..."
```

If missing, install one (from adoptium.net, or `brew install openjdk@21` on a Mac).

1. Make a folder. Create `L1ManualTest.java` with exactly the code shown above.
2. Run it: `java L1ManualTest.java` → expect `OK: plus adds two amounts in the same currency`.
3. Break the code: in `plus`, change `+` to `-`. Run again → expect
   `AssertionError: plus is wrong: expected 1500 but got 500`.
4. Keep the broken `-`. Weaken the check: change `!= 1500` to `< 0`. Run again → expect
   `OK`, even though the code is broken. Say out loud why this run is the dangerous one.
5. Restore both lines (`+` and `!= 1500`). Run once more → expect `OK`.

**Done when:** you have seen one honest green, one honest red, and one lying green, and
you can say which of the three is worse than having no test.

### Your doubts

None raised in this lesson.

### Summary for revision

- A test runs real code with chosen inputs and compares actual against expected.
- The whole value of a test is its ability to **fail** when the code is wrong.
- A failing test is good news: it names the problem before a user finds it.
- A test that cannot fail produces false confidence — worse than no test.
- The program must judge pass or fail. Eyes reading prints do not count.

### Practice questions (answers included)

**Q1.** A team has a test for a shipping-cost function. A teammate edits the function
and introduces a bug: every cost comes out doubled. The test still passes, so the team
ships. Was that test better or worse than having no test at all? Why?

**Answer.** Worse. The test's one job was to fail when the code went wrong, and it did
not, so it told a lie. The green result made the team confident enough to ship a bug.
With no test they would at least have known the code was unchecked.

**Q2.** Here is a complete program:

```java
class CheckDiscount {
    public static void main(String[] args) {
        int price = discountedPrice(200);

        if (price != 180) {
            throw new AssertionError("expected 180 but got " + price);
        }
        System.out.println("OK");
    }

    static int discountedPrice(int original) {
        return original - (original * 10 / 100);   // 10 percent off
    }
}
```

(a) What does it print as it is?
(b) A teammate changes the method to `return original - 10;`. What exactly happens on
the next run?

**Answer.** (a) `discountedPrice(200)` returns 200 − 20 = 180. The `if` is false, so it
prints `OK`. (b) The method now returns 200 − 10 = 190. The `if` is true, so the run
throws `java.lang.AssertionError: expected 180 but got 190` and the program stops with a
non-zero exit code. (Both runs verified on Java 21.)

---

## Lesson 2 — The test project and `mvn test` (serves S1)

### Definition

`src/test/java` is the folder where test code lives. `mvn test` is the command that
compiles the product code, compiles the test code, runs every test it finds, and prints
a report.

### Why it is needed

A hand-written test (a `main` method with an `if` and a thrown error) works, but does
not scale:

- Each hand-written test file must be run by hand, one at a time. With 300 tests, that
  is 300 commands.
- There is no summary. Nothing says "298 passed, 2 failed, here are the two".
- The test code sits next to product code. Nothing stops it from shipping to production.

Projects fix this with three agreements: tests live in one standard folder, one command
runs them all, and that folder is never shipped.

### How it works

**The two-folder rule.** `src/main/java` holds product code. It is compiled into the
**jar** — the zip file of compiled classes that you ship to the server. `src/test/java`
holds checking code. It is compiled and run during the build, then left behind. It never
enters the jar.

**The lifecycle ladder.** A Maven build is a fixed ladder of named steps, called
**phases**: validate, compile, test, package, install, and a few more. Asking for one
phase runs every phase below it first, then stops. `mvn test` means: check the pom,
compile `src/main/java`, compile `src/test/java`, run the tests, stop. `mvn package`
climbs **through** test on its way to building the jar — that is why broken tests block
a release.

**Who actually runs the tests.** A plugin named **Surefire** does the test phase. It
looks at the compiled test classes, picks the ones whose name ends in `Test`, runs their
test methods, and prints the report. Any failed test fails the build.

**`<scope>test</scope>`.** A dependency with this scope is visible to test code only.
Product code in `src/main/java` cannot even import it, and it never enters the jar.

**One dependency, the whole toolkit.** `spring-boot-starter-test` pulls in three
libraries at once: **JUnit 5** (finds and runs test methods), **AssertJ** (assertions
that read like sentences and fail with useful messages), and **Mockito** (stand-in
objects, used in a later task).

### Terms used in this lesson

| Term | Plain meaning |
| --- | --- |
| **Jar** | The zip file of compiled classes that you ship. `mvn package` builds it. |
| **Phase** | One named step on Maven's fixed build ladder (validate, compile, test, package, ...). |
| **Surefire** | The Maven plugin that finds and runs tests during the test phase. |
| **`<scope>test</scope>`** | Marks a dependency as visible to test code only, never shipped. |
| **`spring-boot-starter-test`** | One dependency that brings JUnit 5, AssertJ, and Mockito together. |

### The example — the scratch project for this task

Layout (in `F7.1-your-first-test-and-what-makes-a-test-worth-having/scratch-app`):

```text
scratch-app/
├── pom.xml
└── src/
    ├── main/java/com/zenith/testing/Money.java    ← product code
    └── test/java/                                 ← empty. Tests go here.
```

The full `pom.xml`:

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
    <artifactId>testing-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- The smallest Spring Boot there is. Every Boot app has this. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <!-- The testing toolkit, in one line: JUnit 5 (finds and runs tests),
             AssertJ (readable assertions), Mockito (stand-in objects, a later task).
             scope=test means: visible to test code only, never shipped in the jar. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

The class under test, `src/main/java/com/zenith/testing/Money.java`. It is the F3.6
`Money` with one change: the amount is a `BigDecimal` instead of `long` paise, because
the lessons on money equality need that form. Same guarantees: no negative amount, a
real 3-letter currency code, and `plus` refuses to mix currencies.

```java
package com.zenith.testing;

import java.math.BigDecimal;

/**
 * Your Money record from F3.6, now carrying its amount as BigDecimal.
 * Same guarantees as before: no negative amount, a real 3-letter currency
 * code, and plus() refuses to mix currencies.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {                                   // compact constructor
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
        }
        currency = currency.toUpperCase();           // normalise before storage
    }

    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: cannot add " + other.currency + " to " + this.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

No tests exist yet. `src/test/java` is empty on purpose.

### Walkthrough — the real run with zero tests

`mvn test` in `scratch-app`. Real output, trimmed to the lines that matter:

```text
[INFO] --- resources:3.3.1:resources (default-resources) @ testing-scratch ---
[INFO] skip non existing resourceDirectory .../scratch-app/src/main/resources
[INFO]
[INFO] --- compiler:3.14.1:compile (default-compile) @ testing-scratch ---
[INFO] Compiling 1 source file with javac [debug parameters release 21] to target/classes
[INFO]
[INFO] --- compiler:3.14.1:testCompile (default-testCompile) @ testing-scratch ---
[INFO]
[INFO] --- surefire:3.5.6:test (default-test) @ testing-scratch ---
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Reading it: each `---` line is one ladder step starting. `compile` compiled
`Money.java` ("Compiling 1 source file"). `testCompile` printed nothing — the test
folder is empty. `surefire:test` printed **nothing at all** — no tests exist, so it had
nothing to run. `BUILD SUCCESS` means only "every step attempted worked".

Lesson 1 mindset applied: this green build does **not** mean `Money` works. Zero checks
ran. A green build is only as meaningful as the tests behind it.

Proof of what `spring-boot-starter-test` brought, via `mvn dependency:list`, filtered:

```text
[INFO]    org.junit.jupiter:junit-jupiter:jar:5.12.2:test
[INFO]    org.assertj:assertj-core:jar:3.27.7:test
[INFO]    org.mockito:mockito-core:jar:5.17.0:test
```

All three libraries are present, each with scope `test`: tests only, never shipped.

### Where it is used

Every Java team. The build server runs `mvn test` (or `mvn verify`, a higher rung) on
every push. "The build is red" means this command failed. Because `mvn package` must
climb through the test phase, a failing test physically blocks the jar from being built.

### Common mistakes

- **Putting a test class in `src/main/java`.** The JUnit and AssertJ imports will not
  compile there (test scope). And if it did compile, checking code would ship to
  production.
- **Forgetting `<scope>test</scope>`.** Everything still works, silently — but the test
  libraries now travel inside the production jar.
- **Trusting a green build with zero tests.** `BUILD SUCCESS` means "nothing failed",
  not "the code is right".

### Link to the task

This lesson completes subtask S1: `spring-boot-starter-test` confirmed in `pom.xml`,
its contents proven with `mvn dependency:list` (JUnit 5.12.2, AssertJ 3.27.7, Mockito
5.17.0), and `mvn test` run and read on the empty project.

### Do it yourself (second laptop)

You need the JDK and Maven:

```bash
java -version    # expect: openjdk version "21..."  (if missing: brew install openjdk@21)
mvn -version     # expect: Apache Maven 3.9 or newer  (if missing: brew install maven)
```

1. Create the folders:

   ```bash
   mkdir -p testing-scratch/src/main/java/com/zenith/testing
   mkdir -p testing-scratch/src/test/java
   ```

2. Inside `testing-scratch`, create `pom.xml` with exactly the full pom shown above in
   this lesson.
3. Create `src/main/java/com/zenith/testing/Money.java` with exactly the `Money` record
   shown above, including the `package` and `import` lines.
4. Run `mvn test` inside `testing-scratch`. The first run prints many
   `Downloading from central` lines — Maven fetching libraries. Needs internet, happens
   once. Expect at the end: `Compiling 1 source file`, a silent `surefire:test` step,
   and `BUILD SUCCESS`.
5. Run `mvn dependency:list | grep -E "junit-jupiter:|assertj-core|mockito-core"`.
   Expect three lines, each ending in `:test`.

**Done when:** you saw `BUILD SUCCESS`, you can point at the surefire step and say why
it printed nothing, and you can name the three libraries `spring-boot-starter-test`
brought in.

### Your doubts

None raised in this lesson.

### Summary for revision

- Product code lives in `src/main/java` and ships in the jar. Test code lives in
  `src/test/java` and never ships.
- A Maven build is a ladder of phases. `mvn test` climbs up to and including the test
  phase. `mvn package` passes through test — failing tests block the jar.
- Surefire runs the test phase: it picks classes named `*Test` and runs their test
  methods.
- `<scope>test</scope>` keeps a dependency visible to tests only.
- `spring-boot-starter-test` brings JUnit 5, AssertJ, and Mockito in one line.
- `BUILD SUCCESS` with zero tests means nothing was checked.

### Practice questions (answers included)

**Q1.** A project's `pom.xml` has this dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

A teammate writes a class in `src/main/java` with the line
`import org.assertj.core.api.Assertions;`. The build fails: "package
org.assertj.core.api does not exist". The same import inside a class in `src/test/java`
compiles fine. Why?

**Answer.** The dependency has test scope, so AssertJ is on the classpath only while
test code is compiled and run. Code in `src/main/java` is compiled without it, so the
package does not exist there. Code in `src/test/java` sees it, so the import works.

**Q2.** You run `mvn test` and the output ends like this:

```text
[INFO] --- compiler:3.14.1:compile (default-compile) @ shop ---
[INFO] Compiling 12 source files with javac [debug parameters release 21] to target/classes
[INFO] --- compiler:3.14.1:testCompile (default-testCompile) @ shop ---
[INFO] --- surefire:3.5.6:test (default-test) @ shop ---
[INFO] BUILD SUCCESS
```

Your manager says: "Green build — so the code is correct, right?" What is the precise,
honest answer?

**Answer.** No. The silent `surefire:test` step shows zero tests ran — `testCompile`
also compiled nothing. Green here means only "the code compiled and no check failed",
and there were no checks. The build says nothing about whether the 12 classes behave
correctly.

---

## Lesson 3 — `@Test`: how JUnit finds and runs a test (prepares S2)

### Definition

An **annotation** is a label you attach to code. It starts with `@`. The label does
nothing by itself; a tool reads it and acts. `@Test` is an annotation for methods. It
tells JUnit: this method is a test — run it, and judge it.

### Why it is needed

A hand-written test (a `main` method with an `if` and a thrown error) makes **you** the
runner: you find the file, run it, read the result. With three hundred files that job is
impossible. The runner must be a program, and a program needs a way to know **which**
methods are tests. `@Test` is that marker. Label a method and JUnit finds it, runs it,
and counts the result — for the whole project, in one command.

### How it works — six steps on `mvn test`

1. Surefire (the Maven plugin that owns the test phase) looks at the compiled classes
   from `src/test/java` and picks the ones whose name ends in `Test`.
2. It hands each class to JUnit.
3. JUnit finds every method labelled `@Test`. The method must be `void`, take no
   parameters, and must not be `private` or `static`.
4. JUnit calls each one.
5. The judgement is one rule: **the method returns normally → pass. The method throws
   anything → fail.**
6. The report counts results in four columns: `Tests run` (methods ran), `Failures`
   (an `AssertionError` was thrown — a check said "wrong answer"), `Errors` (any other
   exception — the code blew up), `Skipped`.

The Lesson 1 hand-written test failed by throwing `AssertionError`. JUnit uses exactly
the same rule. Only the finding, running, and counting became automatic.

### Terms used in this lesson

| Term | Plain meaning |
| --- | --- |
| **Annotation** | A label starting with `@`, attached to code. A tool reads it and acts. |
| **`@Test`** | The label that makes JUnit run a method as a test. |
| **Failure (report column)** | The test threw `AssertionError`: a check said the answer was wrong. |
| **Error (report column)** | The test threw any other exception: the code blew up before or instead of a check. |

### The example — the smallest test

`src/test/java/com/zenith/testing/FirstTest.java`:

```java
package com.zenith.testing;

import org.junit.jupiter.api.Test;

class FirstTest {

    @Test
    void doesNothingYet() {
        System.out.println("JUnit ran this method");
    }
}
```

`@Test` comes from the `junit-jupiter` library, which `spring-boot-starter-test`
brought in. The method only prints, to prove JUnit called it.

### Walkthrough — one green run, one red run

**Green.** `mvn test`, real output, trimmed:

```text
[INFO] Running com.zenith.testing.FirstTest
JUnit ran this method
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.FirstTest
[INFO] Results:
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Nobody wrote a `main` method and nobody called `doesNothingYet()`, yet the print line
appeared: JUnit found the class by name, found the method by label, and called it. The
method returned normally → pass.

**Red.** Body replaced with `throw new AssertionError("failed on purpose");`:

```text
[INFO] Running com.zenith.testing.FirstTest
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.zenith.testing.FirstTest
java.lang.AssertionError: failed on purpose
	at com.zenith.testing.FirstTest.doesNothingYet(FirstTest.java:9)
[INFO] Results:
[ERROR] Failures:
[ERROR]   FirstTest.doesNothingYet:9 failed on purpose
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
```

The method threw → fail → red build. The report hands over class, method name, line
number, message. The thrown thing was an `AssertionError`, so the 1 landed in
`Failures`; any other exception lands in `Errors`.

### Where it is used

JUnit is the standard test runner of the Java world. Spring Boot ships it by default.
IDE play buttons next to test methods run the same `@Test` labels. Build servers parse
this same report to decide red or green.

### Common mistakes

- **Class name does not end in `Test`.** Surefire skips the whole class, silently.
  (Default patterns: `*Test`, `Test*`, `*Tests`, `*TestCase`.)
- **`private` or `static` on a test method.** Verified live: adding
  `@Test private void hidden()` left the report at `Tests run: 1` — skipped, no warning.
- **Forgetting `@Test`.** The method never runs. Also silent.

All three discovery failures are **silent**. After adding a test, glance at the
`Tests run:` count and confirm it went up by one.

### Link to the task

Subtask S2's `Money` test is exactly this shape: a class `MoneyTest` holding a `@Test`
method. This lesson built the shell; the assertion and the layout come next.

### Do it yourself (second laptop)

In the `testing-scratch` project from the Lesson 2 steps:

1. Create `src/test/java/com/zenith/testing/FirstTest.java` with the code above.
2. `mvn test` → expect `JUnit ran this method`, `Tests run: 1, Failures: 0`, `BUILD SUCCESS`.
3. Replace the print line with `throw new AssertionError("failed on purpose");` →
   `mvn test` → expect `<<< FAILURE!`, `FirstTest.doesNothingYet:9 failed on purpose`,
   `BUILD FAILURE`.
4. Put the print line back. `mvn test` → green again.

**Done when:** you can say "a test passes when the method returns, fails when it
throws", and explain `Failures` versus `Errors` in the report.

### Your doubts

None raised in this lesson.

### Summary for revision

- `@Test` marks a method as a test. JUnit finds it, runs it, judges it.
- Pass = the method returned normally. Fail = the method threw anything.
- `AssertionError` → `Failures` column. Other exceptions → `Errors` column.
- Discovery is silent: wrong class name, missing `@Test`, `private`, or `static` all
  skip the test with no warning. Watch the `Tests run:` count.

### Practice questions (answers included)

**Q1.** Here is a complete test class:

```java
package shop;

import org.junit.jupiter.api.Test;

class PricingTest {

    @Test
    void appliesTenPercentDiscount() {
    }

    void rejectsNegativePrice() {
    }
}
```

You run `mvn test`. What does `Tests run:` say, what happens to
`rejectsNegativePrice`, and does anything warn you?

**Answer.** `Tests run: 1`. Only `appliesTenPercentDiscount` runs.
`rejectsNegativePrice` has no `@Test` label, so JUnit never sees it. Nothing warns you —
discovery failures are silent.

**Q2.** Here is a complete test class:

```java
package shop;

import org.junit.jupiter.api.Test;

class StockTest {

    @Test
    void stockCanNeverBeNegative() {
        throw new IllegalStateException("boom");
    }
}
```

You run `mvn test`. (a) Build result? (b) In `Tests run: 1, Failures: ?, Errors: ?`,
which column gets the 1, and why?

**Answer.** (a) `BUILD FAILURE` — the method threw, so the test failed. (b) `Errors: 1`.
An `IllegalStateException` is not an `AssertionError`, so JUnit counts it as "the code
blew up" (error), not "a check said wrong answer" (failure).

---

## Lesson 4 — `assertThat`: your first real assertion (serves S2, first half)

### Definition

An **assertion** is a statement about what should be true, written as code. If the
statement turns out false, the assertion throws an `AssertionError` with a message.
`assertThat` is the word that starts every assertion in the AssertJ library.

### Why it is needed

Hand-written checks (`if` + `throw`) have two problems. First, **you** write the
comparison, and comparisons on objects are easy to get wrong. Second, **you** write the
failure message every time, and under deadline messages decay into `"wrong"` — which
tells a reader nothing. An assertion library ships correct ready-made comparisons and
builds rich failure messages automatically, with both values inside.

### How it works — three steps

1. `assertThat(actual)` takes the **actual value** — the one your code produced — and
   wraps it in a checker object.
2. You call a check method on that checker and give it the **expected value**:
   `isEqualTo(expected)`, `isEqualByComparingTo(expected)`, and many more. Placement
   rule: *actual goes inside `assertThat`, expected goes inside the check method.*
3. If the check holds, the method returns quietly. If not, it builds a message holding
   both values and throws `AssertionError`. JUnit's rule finishes the job: a thrown test
   is a failed test.

Three supporting facts:

- `import static org.assertj.core.api.Assertions.assertThat;` is a **static import**:
  it lets you write `assertThat(...)` instead of `Assertions.assertThat(...)`.
- `new BigDecimal("10.00")` uses a String on purpose. A String is read exactly; a double
  drags in binary-float noise. Never build money from a double.
- For `BigDecimal`, use `isEqualByComparingTo`, not `isEqualTo`: `BigDecimal.equals`
  also compares the number of decimal digits, so `15.0` and `15.00` count as different.
  `isEqualByComparingTo` compares only the numeric value. Full story in Lesson 9.

### Terms used in this lesson

| Term | Plain meaning |
| --- | --- |
| **Assertion** | A should-be-true statement in code. False → `AssertionError` with a message. |
| **Actual value** | What the code really produced. Goes inside `assertThat(...)`. |
| **Expected value** | What the test wants. Goes inside the check method. |
| **Static import** | An import of a method name, so `assertThat(...)` works without a class prefix. |

### The example — the first `Money` test

The class under test is the `Money` record from the Lesson 2 notes (BigDecimal amount,
3-letter currency, guarded constructor, `plus` refuses mixed currencies). The test,
`src/test/java/com/zenith/testing/MoneyTest.java`, as first written in this lesson:

```java
package com.zenith.testing;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void addsTwoAmountsInTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money five = new Money(new BigDecimal("5.00"), "GBP");
        Money total = ten.plus(five);
        assertThat(total.amount()).isEqualByComparingTo("15.00");
    }
}
```

Read the assertion aloud: "assert that total's amount is equal, by comparing, to
15.00." (`FirstTest.java` was deleted in this lesson: its method could never fail, and
Lesson 1 says what such a test is worth.)

### Walkthrough — the real run

```text
[INFO] Running com.zenith.testing.MoneyTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.MoneyTest
[INFO] Results:
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Green — and this green means something: a real behaviour of `Money` was checked against
a known answer.

### Where it is used

Every test from here on starts its checks with `assertThat`. It is the standard opener
in modern Java codebases; Spring's own test suite uses AssertJ.

### Common mistakes (each one verified live in this project)

- **Calling `assertThat` with no check method.** `assertThat(total.amount());` compiles
  and checks **nothing**. Verified: with `plus` secretly subtracting (3.00 and 2.00 gave
  1.00 instead of 5.00), a test with that bare line stayed green. Lesson 1's lying test,
  reborn.
- **Swapping actual and expected.** `assertThat("INR").isEqualTo(m.currency())` with
  broken normalisation failed with `expected: "inr" but was: "INR"` — backwards from the
  truth. The value the code produced goes inside `assertThat`; the wanted value goes in
  the check method.
- **Importing the wrong `assertThat`.** You want
  `org.assertj.core.api.Assertions.assertThat`. Decline `org.hamcrest...` suggestions.

### Link to the task

First half of subtask S2: the exact test from the task sheet,
`addsTwoAmountsInTheSameCurrency`, exists and passes. The required three-block layout is
Lesson 5, which finishes S2 and S3.

### Do it yourself (second laptop)

In the `testing-scratch` project from the Lesson 2 steps:

1. Delete `FirstTest.java`, saying out loud why it earns nothing.
2. Create `src/test/java/com/zenith/testing/MoneyTest.java` with exactly the code above.
3. `mvn test` → expect `Running com.zenith.testing.MoneyTest`,
   `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

**Done when:** green with `Tests run: 1`, and you can read the assertion aloud, naming
which part is actual and which is expected.

### Your doubts

None raised in this lesson.

### Summary for revision

- An assertion states what should be true. False → `AssertionError` with a message.
- Actual inside `assertThat(...)`; expected inside the check method. Swapping them makes
  failure messages lie.
- `assertThat(x);` with no check method checks nothing — a lying test.
- Money amounts: build `BigDecimal` from Strings, compare with `isEqualByComparingTo`.

### Practice questions (answers included)

**Q1.** `Money` is a record `Money(BigDecimal amount, String currency)` whose `plus`
method is supposed to **add** the two amounts. This complete test is green today:

```java
@Test
void totalIsCorrect() {
    Money a = new Money(new BigDecimal("3.00"), "INR");
    Money b = new Money(new BigDecimal("2.00"), "INR");
    Money total = a.plus(b);
    assertThat(total.amount());
}
```

A teammate changes `plus` to subtract, so `total.amount()` becomes 1.00 instead of 5.00.
Does the test turn red? Why or why not? What one change fixes the test?

**Answer.** It stays green (verified live). `assertThat(...)` without a check method
performs no comparison, so nothing can throw. The fix:
`assertThat(total.amount()).isEqualByComparingTo("5.00");`. Side note from the live
experiment: with amounts 2.00 and 3.00 the broken subtract gives −1.00, and the `Money`
constructor itself rejects negatives — the build goes red as an **Error**, not a test
failure. Good constructors catch some bugs even when tests are weak, but that is luck,
not testing.

**Q2.** `Money`'s constructor uppercases the currency, so
`new Money(new BigDecimal("1.00"), "inr").currency()` returns `"INR"`. The test:

```java
@Test
void currencyIsNormalised() {
    Money m = new Money(new BigDecimal("1.00"), "inr");
    assertThat("INR").isEqualTo(m.currency());
}
```

(a) Does it pass today? (b) Later the uppercasing breaks and `currency()` returns
`"inr"`. The test fails with `expected: "inr" but was: "INR"` — backwards. What caused
that, and what is the correct assertion line?

**Answer.** (a) Yes — both sides are `"INR"`, so the comparison holds. (b) The actual
and expected are swapped: the literal `"INR"` sits inside `assertThat`, so AssertJ
treats it as the actual value, and treats `m.currency()` as the expected one. Correct
line: `assertThat(m.currency()).isEqualTo("INR");`. Then the message reads
`expected: "INR" but was: "inr"` — the truth.

---

## Lesson 5 — Arrange–Act–Assert (serves S2, S3, CP1)

### Definition

**Arrange–Act–Assert** is the standard layout of a test method: three blocks, separated
by blank lines. *Arrange* sets up the situation. *Act* performs the one action being
tested. *Assert* checks the result.

### Why it is needed

A test is read most often at the worst moment: the build is red and a person who did not
write the test must understand it fast. The target: **a reader should see the one action
being tested in under a second.** The runner ignores layout — a mashed test passes just
like a tidy one (verified live). Layout exists purely for the human. Multiplied by
several hundred tests, reading speed decides whether a suite is useful or ignored.

### How it works

Three blocks, top to bottom, one blank line between each:

1. **Arrange** — create the objects and inputs the action needs. May be several lines.
2. **Act** — the one call this test exists to check. Almost always **one line**. Rule
   from the task sheet: *if the middle block has four lines, the test is testing too
   much* — split it.
3. **Assert** — the `assertThat` checks on the result. At the end, never in the middle.

The blank lines are the entire grammar; no `// arrange` comments needed. Eyes learn to
jump to the middle block, because the middle block is the test's subject. Some teams
call the same shape **Given–When–Then**.

(When the action under test is a constructor, arrange and act naturally merge into the
same line. That is fine — the blank line before the asserts still matters.)

### The example — before and after

Before (correct but flat):

```java
@Test
void addsTwoAmountsInTheSameCurrency() {
    Money ten = new Money(new BigDecimal("10.00"), "GBP");
    Money five = new Money(new BigDecimal("5.00"), "GBP");
    Money total = ten.plus(five);
    assertThat(total.amount()).isEqualByComparingTo("15.00");
}
```

After (the permanent form, matching the task sheet exactly):

```java
@Test
void addsTwoAmountsInTheSameCurrency() {
    Money ten = new Money(new BigDecimal("10.00"), "GBP");
    Money five = new Money(new BigDecimal("5.00"), "GBP");

    Money total = ten.plus(five);

    assertThat(total.amount()).isEqualByComparingTo("15.00");
}
```

Anatomy: Arrange = build ten and five pounds. Act = `ten.plus(five)` — cover the rest
with your hand; this line alone says what the test is about. Assert = result must be
15.00.

Real run after the reformat:

```text
[INFO] Running com.zenith.testing.MoneyTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.zenith.testing.MoneyTest
[INFO] BUILD SUCCESS
```

Identical result to the flat version. The computer saw no difference; every future
reader will.

### Where it is used

Every test in this project from here on (permanent adoption). Professional teams enforce
the shape in review. Spring's own tests follow it, sometimes labelled Given–When–Then.

### Common mistakes

- **Two behaviours in one test** (verified green live, which is the trap):

  ```java
  @Test
  void moneyWorks() {
      Money a = new Money(new BigDecimal("4.00"), "EUR");
      Money b = new Money(new BigDecimal("1.00"), "EUR");
      Money sum = a.plus(b);
      assertThat(sum.amount()).isEqualByComparingTo("5.00");
      Money c = new Money(new BigDecimal("2.00"), "eur");
      assertThat(c.currency()).isEqualTo("EUR");
  }
  ```

  It tests addition *and* normalisation. Red cannot tell you which broke. Worse, a
  failing assertion **throws**, so if the first `assertThat` fails the second never
  runs — the report hides half the story.
- **Assertions sprinkled between actions.** Act, assert, act, assert — same disease.
  Asserts belong at the end.
- **A bloated Arrange block.** Fifteen lines of setup is feedback about the design of
  the class under test, not just the test.

### Link to the task

Closes S2 (the `Money` test sits in three visually separate blocks, matching the task
sheet) and S3 (permanent adoption, the reason blank lines matter, the four-line-middle
rule). Checkpoint question 1 is now answerable.

### Do it yourself (second laptop)

In `testing-scratch`, in `MoneyTest.java` from the Lesson 4 steps:

1. Insert a blank line after the two `new Money(...)` lines, and another after the
   `ten.plus(five)` line.
2. `mvn test` → same green as before: `Tests run: 1, Failures: 0`, `BUILD SUCCESS`.
3. Cover the method with your hand, uncover only the middle block, say what the test is
   about.

**Done when:** green in three-block form, and you can state: arrange may be several
lines, act is one line, asserts come last.

### Your doubts

None raised in this lesson.

### Summary for revision

- Three blocks: Arrange (set the stage), Act (the one action, one line), Assert (checks
  at the end). Blank lines between them.
- The layout serves the human reading a red build, never the runner.
- Four lines in the middle block = the test is testing too much. Split it.
- One behaviour per test. A failing assertion throws, hiding any asserts after it.

### Practice questions (answers included)

**Q1.** `Money` is a record `Money(BigDecimal amount, String currency)`. `plus` adds two
amounts of the same currency. The constructor uppercases the currency, so `"eur"` is
stored as `"EUR"`. This complete test is green:

```java
@Test
void moneyWorks() {
    Money a = new Money(new BigDecimal("4.00"), "EUR");
    Money b = new Money(new BigDecimal("1.00"), "EUR");
    Money sum = a.plus(b);
    assertThat(sum.amount()).isEqualByComparingTo("5.00");
    Money c = new Money(new BigDecimal("2.00"), "eur");
    assertThat(c.currency()).isEqualTo("EUR");
}
```

(a) How many distinct behaviours does it check? (b) How would Arrange–Act–Assert
restructure it? (c) If the first `assertThat` fails, what happens to the second?

**Answer.** (a) Two: adding two same-currency amounts, and uppercasing the currency at
construction. (b) Two tests. Test one, `addsTwoAmountsInTheSameCurrency`: arrange `a`
and `b`, act `a.plus(b)`, assert 5.00. Test two, `normalisesCurrencyToUpperCase`:
construct with `"eur"` (arrange and act merge — the constructor is the action), assert
`currency()` is `"EUR"`. (c) It never runs. A failing assertion throws
`AssertionError`, the method exits at that line, and the second check's result stays
unknown until the first is fixed.

**Q2.** A teammate says: "I deleted the blank lines from our tests and the build stayed
green. They are cosmetic — they don't matter." What is the correct reply?

**Answer.** Green only proves the runner ignores layout — the blank lines were never for
the runner. They are for the human reading a red build: they make the one action visible
in a second. The layout matters exactly at the moment something breaks, which is the
moment the suite exists for.

---

## Lesson 6 — Test names are the failure report (serves S4)

### Definition

A test name is a short sentence about behaviour, squeezed into camelCase:
`addsTwoAmountsInTheSameCurrency`, `rejectsNegativeAmount`,
`refusesToAddDifferentCurrencies`. The name states what the class under test *does* in
one situation.

### Why it is needed

A red build prints one line per failed test: class name, method name, line number. For
most readers that line is **all they will read**. If it carries the behaviour, they can
act. If it says `test1`, they must open the file and reverse-engineer intent. Multiplied
by hundreds of tests, bad names make the report worthless. **The name is the failure
report.**

### How it works — a naming recipe

1. Start with a verb, third person: adds, rejects, refuses, returns, keeps, normalises.
2. State the behaviour and its condition: `refusesToAddDifferentCurrencies`,
   `publishFailsWhenNoPriceTierExists`. Shape: *doesWhat* + *underWhatCondition*.
3. Read it with the class name in front: `MoneyTest.rejectsNegativeAmount` → "Money
   rejects negative amount." If that sentence fully describes the behaviour, the name is
   right.
4. Never prefix with `test`. Every method in a test class is a test; the word carries
   zero information, and `testPlus` names a method instead of a behaviour.
5. Long is fine. Names are read, never typed.

### The example — one bug, two names (verified live)

Two test classes held the same check (10.00 GBP plus 5.00 GBP must be 15.00): one method
named `addsTwoAmountsInTheSameCurrency`, the other named `test1`. Then `plus` was broken
to subtract. Real report:

```text
[ERROR] Failures:
[ERROR]   BadlyNamedTest.test1:19
expected: 15.00
 but was: 5.00
[ERROR]   MoneyTest.addsTwoAmountsInTheSameCurrency:18
expected: 15.00
 but was: 5.00
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
```

Read as a stranger: the first line says something, somewhere, is wrong. The second line
says Money's same-currency addition is broken — before any file is opened. Same bug,
same assertion; the whole difference in usefulness is the name.

### Where it is used

Triage: CI shows 5 failures out of 800 tests, and the five names say which feature broke
and who should look, in seconds. Documentation: the method list of `MoneyTest` read top
to bottom is `Money`'s contract.

### Common mistakes

- **`test1`, `test2`, `testMoney`, `testPlus`** — say nothing, or name
  implementation instead of behaviour. When `testPlus` fails the reader still asks:
  plus should have done *what*?
- **Naming the setup, not the outcome.** `addingTwoMoneys` — and then what?
- **A name vague because the test does too much.** If the honest name is
  `moneyWorksCorrectly`, the test checks several behaviours. Split it; names then come
  naturally.

### Link to the task

Subtask S4: names as behaviour sentences, never `test1` or `testMoney`, because the name
is the failure report. The remaining named tests (`rejectsNegativeAmount`,
`refusesToAddDifferentCurrencies`) arrive with the exception-testing lesson.

### Do it yourself (second laptop)

In `testing-scratch` from the earlier steps:

1. Create `BadlyNamedTest.java`: copy the `MoneyTest` class body, rename the class to
   `BadlyNamedTest` and the method to `test1`.
2. In `Money.plus`, change `this.amount.add(other.amount)` to
   `this.amount.subtract(other.amount)`.
3. `mvn test` → expect `Tests run: 2, Failures: 2` with failure lines
   `BadlyNamedTest.test1:19` and `MoneyTest.addsTwoAmountsInTheSameCurrency:18`, both
   `expected: 15.00 but was: 5.00`.
4. Read both lines aloud as a stranger. Note which one is a usable report.
5. Restore `.add`, delete `BadlyNamedTest.java`, `mvn test` → `Tests run: 1`,
   `BUILD SUCCESS`.

**Done when:** you can say in one sentence why only one of the two failure lines is a
usable report.

### Your doubts

None raised in this lesson.

### Summary for revision

- The failure line (class.method:line) is all most readers see. The name must carry the
  behaviour.
- Recipe: verb first, behaviour plus condition, reads as a sentence after the class
  name, no `test` prefix, long is fine.
- Vague names signal tests that do too much.
- A test suite with good names doubles as the class's documentation.

### Practice questions (answers included)

**Q1.** A `Money` class adds amounts and refuses to mix currencies. CI is red with five
failures:

```text
MoneyTest.test1:12
MoneyTest.testPlus:20
MoneyTest.moneyWorks:31
MoneyTest.addsTwoAmountsInTheSameCurrency:40
MoneyTest.refusesToAddDifferentCurrencies:52
```

Which lines let you act without opening the file, and what is wrong with each of the
others?

**Answer.** Actionable: `addsTwoAmountsInTheSameCurrency` (same-currency addition is
broken) and `refusesToAddDifferentCurrencies` (the mixed-currency guard is broken).
Useless: `test1` says nothing at all; `testPlus` names a method, not a behaviour — plus
should have done what?; `moneyWorks` is so vague it probably covers several behaviours
at once.

**Q2.** You test this behaviour: creating `Money` with a four-letter currency code such
as `"EURO"` must be rejected (only 3-letter codes are valid). Propose a good name, and
say why `testMoney` and `test3` are worse.

**Answer.** `rejectsFourLetterCurrencyCode` (or
`rejectsCurrencyCodeThatIsNotThreeLetters`). Both state behaviour and condition, so the
failure line alone tells the reader what broke. `testMoney` and `test3` state neither —
the reader must open the file to learn what was being checked.

---

## Lesson 7 — `@DisplayName` (serves S5)

### Definition

`@DisplayName("refuses to add GBP to EUR")` is an annotation placed on a test method
next to `@Test`. It holds a human sentence — spaces and symbols allowed. Test tools that
honour display names show the sentence instead of the method name.

### Why it is needed

Method names are camelCase and limited: no spaces, and characters like `₹`, `→`, or
`83.50` are awkward or impossible. Some behaviours read better as a true sentence.
`@DisplayName` lets a report read like prose where prose is needed.

### How it works

Import `org.junit.jupiter.api.DisplayName`, annotate the method:

```java
@Test
@DisplayName("adds two amounts in the same currency")
void addsTwoAmountsInTheSameCurrency() { ... }
```

JUnit attaches the sentence to the test as metadata. Whether you see it depends on the
tool displaying results.

### The experiment — where the sentence actually shows (verified live)

With the annotation above on the one `Money` test:

1. **Green run, console:** output identical to before. Sentence nowhere.
2. **Report files** (`target/surefire-reports/`): both the `.txt` and the `.xml`
   recorded `testcase name="addsTwoAmountsInTheSameCurrency"` — the method name.
3. **Red run** (with `plus` broken): the failure line read
   `MoneyTest.addsTwoAmountsInTheSameCurrency:20` — method name again.

**In default Maven output, `@DisplayName` appears nowhere — green, red, or report
files.** It shows in IDE test panes (IntelliJ, VS Code, Cursor) and in reporters
configured to use phrased names.

### The conclusion

The failure report Maven prints uses the method name no matter what. So the method name
must carry the behaviour anyway — `@DisplayName` never excuses a bad method name. It is
a bonus layer for IDE readers.

**Project convention (decided):** no `@DisplayName` by default; good method names carry
the report. Allowed only where a true sentence genuinely helps an IDE reader. The task's
own example — `@DisplayName("refuses to add GBP to EUR")` — will sit on the
mixed-currency test as the one sanctioned use.

### Common mistakes

- **Duplicating the method name in the sentence.** Says the same thing twice, updated
  twice. Noise.
- **Relying on it where nobody sees it.** Teams reading Maven console output in CI never
  see display names.
- **Using it to excuse `test1`.** The Maven failure line still says `test1`.

### Link to the task

S5 done: annotation added to one test, run on green and red, reports inspected,
convention decided with evidence.

### Do it yourself (second laptop)

In `testing-scratch`, `MoneyTest.java`:

1. Add `import org.junit.jupiter.api.DisplayName;` and
   `@DisplayName("adds two amounts in the same currency")` under `@Test`.
2. `mvn test` → green; sentence nowhere in output.
3. Open `target/surefire-reports/com.zenith.testing.MoneyTest.txt` → method name there
   too.
4. Break `Money.plus` (`.add` → `.subtract`), run → failure line shows the method name,
   not the sentence. Restore `.add`.
5. Remove the annotation and import. Run → green.

**Done when:** you have seen that default Maven reports ignore display names, and can
state the convention this implies.

### Your doubts

None raised in this lesson.

### Summary for revision

- `@DisplayName` holds a sentence; only display-name-aware tools (IDEs, configured
  reporters) show it.
- Default `mvn test` output and surefire report files use method names, always.
- Therefore method names must be good regardless; `@DisplayName` is an optional bonus,
  used only where a real sentence helps.

### Practice questions (answers included)

**Q1.** A team runs tests only through `mvn test` in CI and reads console output. Fact:
default Maven output shows method names, never `@DisplayName` sentences. A teammate
proposes renaming all tests to `test1`, `test2`, ... with the real description in
`@DisplayName`. What would failure reports look like, and what is the objection?

**Answer.** Every failure line would read `SomethingTest.test1:12`,
`SomethingTest.test2:40` — behaviour invisible, report useless. Objection in one
sentence: the only report this team reads prints method names, so moving meaning into
`@DisplayName` deletes it from the report.

**Q2.** Two annotated tests:

```java
@Test
@DisplayName("refuses to add different currencies")
void refusesToAddDifferentCurrencies() { ... }

@Test
@DisplayName("converts using the 2024 average rate of ₹83.50 per $")
void convertsUsingThe2024AverageRate() { ... }
```

Which annotation earns its place, and why?

**Answer.** The second. Its sentence carries `₹83.50 per $` — symbols and precision a
method name cannot hold, so the sentence adds real information (for tools that show
it). The first duplicates its method name exactly: noise, maintained twice, drop it.

---

## Lesson 8 — Break it on purpose, and read the message (serves S6, CP2)

### Definition

A working habit: after writing a test, make it fail once on purpose, and read the
failure message as a stranger would. The failure message is the product a test delivers;
you inspect the product before trusting it.

### Why it is needed

First, a test earns its keep only on the day it fails; one deliberate red run proves
yours can fail at all. Second, not all failure messages are equal. A message carrying
expected, actual, and the difference lets you start fixing immediately. A message saying
only "expected true, got false" sends you hunting with a debugger. Same bug, minutes
versus an hour.

### How it works — the mechanical difference

**AssertJ:** `assertThat(total.amount()).isEqualByComparingTo("16.00")`. The library
receives **both values** and still holds them at failure time, so it prints both,
labelled.

**`assertTrue`:** `assertTrue(total.amount().compareTo(new BigDecimal("16.00")) == 0)`.
Java evaluates the inside first: the comparison collapses into a single boolean, and
`false` is all `assertTrue` ever receives. The numbers that produced the `false` are
gone. **The information was destroyed before the assertion ran** — no library could
print it. That is the entire argument for AssertJ: keep the values inside the assertion,
and the assertion can report them.

### The example — one wrong expectation, two styles (both run live)

The true total is 15.00; the expectation was deliberately set to 16.00.

```java
// Version A — AssertJ
assertThat(total.amount()).isEqualByComparingTo("16.00");

// Version B — JUnit's built-in (import static org.junit.jupiter.api.Assertions.assertTrue)
assertTrue(total.amount().compareTo(new BigDecimal("16.00")) == 0);
```

**Version A, real failure:**

```text
[ERROR] com.zenith.testing.MoneyTest.addsTwoAmountsInTheSameCurrency <<< FAILURE!
org.opentest4j.AssertionFailedError:
expected: 16.00
 but was: 15.00
```

You hold: the behaviour (method name), the wanted value, the arrived value, the gap. A
theory forms before any file opens.

**Version B, real failure:**

```text
[ERROR] com.zenith.testing.MoneyTest.addsTwoAmountsInTheSameCurrency <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
```

You hold: a boolean was false. Which values collided — unknown. Next step forced: open
the file, add prints or a debugger, re-run.

### Where it is used

"See it red once" applies to every new test — it catches lying tests on day one. The
message-quality argument applies in review: `assertTrue(a.equals(b))` and
`assertTrue(list.size() == 3)` get rewritten as `assertThat(a).isEqualTo(b)` and
`assertThat(list).hasSize(3)`.

### Common mistakes

- **Shipping a test that was never red.** It might be unable to fail.
- **Wrapping comparisons into booleans.** Every `assertTrue(x.compareTo(y) == 0)`-style
  check reports "expected true" and hides the values.
- **When red, fixing the wrong side.** The message says expected and actual disagree,
  not which one is wrong. Decide first: broken code, or stale expectation? (In our run,
  16.00 was the lie.)

### Link to the task

S6 complete, exactly as prescribed: 15.00 → 16.00, AssertJ message read, same assertion
rewritten with `assertTrue`, broken again, poor message read. CP2 in one sentence: *the
boolean form destroys the values before the assertion runs, so the failure message
cannot contain them.*

### Do it yourself (second laptop)

In `testing-scratch`, `MoneyTest.java`:

1. Change `"15.00"` to `"16.00"`. `mvn test` → `expected: 16.00  but was: 15.00`,
   `BUILD FAILURE`.
2. Add `import static org.junit.jupiter.api.Assertions.assertTrue;` and replace the
   assertion with `assertTrue(total.amount().compareTo(new BigDecimal("16.00")) == 0);`.
   Run → `expected: <true> but was: <false>`.
3. Say what the second message lets you do next (nothing except open the file).
4. Restore the AssertJ line with `"15.00"`, remove the import. Run → `BUILD SUCCESS`.

**Done when:** both messages are in your terminal history and you can give the
one-sentence mechanical reason the `assertTrue` message is empty.

### Your doubts

None raised in this lesson.

### Summary for revision

- Break every new test once. Red proves it can fail; the message shows what a reader
  will get.
- AssertJ holds both values at failure time and prints them. `assertTrue` receives only
  a boolean — the values were destroyed before the call.
- Any comparison wrapped into a boolean hides its evidence. There is an AssertJ method
  for each case; use it.

### Practice questions (answers included)

**Q1.** A cart's `total()` should return 250 after items of 100 and 150. The test:

```java
@Test
void totalsTwoItems() {
    Cart cart = new Cart();
    cart.add(new Item("book", 100));
    cart.add(new Item("lamp", 150));

    int total = cart.total();

    assertTrue(total == 250);
}
```

It fails with `expected: <true> but was: <false>`. (a) What do you know about `total`?
(b) Rewrite the assertion so the next failure shows the value, and give the message if
`total` were 230.

**Answer.** (a) Only that it is not 250. The number itself was destroyed when
`total == 250` collapsed to `false`. (b) `assertThat(total).isEqualTo(250);` — failing
with `expected: 250 but was: 230`.

**Q2.** Fact: a test that cannot fail is worse than no test. You just wrote a new test
and it passed first run. What single step do you take before trusting it, and what two
things do you check?

**Answer.** Break it once on purpose — flip the expected value or break the code — and
run. Check (1) it actually goes red, proving it can fail; (2) the failure line and
message name the right behaviour and carry useful values. Then restore and see green.

---

## Lesson 9 — `BigDecimal` equality and `isEqualByComparingTo` (serves S8, CP3)

### Definition

`new BigDecimal("15.0")` and `new BigDecimal("15.00")` are **not** `equals`: for
`BigDecimal`, the number of decimal digits (the scale) is part of equality. `compareTo`
compares only the numeric value. In tests: `isEqualTo` uses `equals`;
`isEqualByComparingTo` uses `compareTo` — the one money tests want.

### Why it is needed

Amounts reach a money system with different scales for the same value: a user types
`10.5`, the database column stores `10.50`, JSON says `10.5`. With `equals`-based
checks, a test can go red while the money is correct. A suite that cries wolf gets
ignored. Compare numbers, not formatting.

### How it works

A `BigDecimal` is two parts: an **unscaled value** (integer) and a **scale** (digits
after the decimal point).

- `"15.0"` = unscaled `150`, scale `1`. `"15.00"` = unscaled `1500`, scale `2`.
- **`equals`** compares both parts → `false` here. (Scale can carry meaning — precision
  — so `BigDecimal` refuses to discard it.)
- **`compareTo`** asks: same point on the number line? → `0` (equal).
- Arithmetic moves scales: addition keeps the larger operand scale, so `10.5 + 4.5`
  gives `15.0` (scale 1), not `15.00`.

| AssertJ method | Uses | For `BigDecimal` means |
| --- | --- | --- |
| `isEqualTo` | `equals` | value **and** scale must match |
| `isEqualByComparingTo` | `compareTo` | value only — what money tests want |

### The example — plain-Java proof (`lessons/L9ScaleDemo.java`)

```java
BigDecimal a = new BigDecimal("15.0");
BigDecimal b = new BigDecimal("15.00");

System.out.println("a.equals(b)    = " + a.equals(b));
System.out.println("a.compareTo(b) = " + a.compareTo(b));
System.out.println("a.scale()      = " + a.scale());
System.out.println("b.scale()      = " + b.scale());

BigDecimal sum = new BigDecimal("10.5").add(new BigDecimal("4.5"));
System.out.println("10.5 + 4.5     = " + sum + "   (scale " + sum.scale() + ")");
```

Real output:

```text
a.equals(b)    = false
a.compareTo(b) = 0
a.scale()      = 1
b.scale()      = 2
10.5 + 4.5     = 15.0   (scale 1)
```

### The trap inside a real test (verified live)

Two tests added `10.5` GBP and `4.5` GBP (true total: fifteen), differing only in the
last line:

```java
assertThat(total.amount()).isEqualTo(new BigDecimal("15.00"));      // version 1
assertThat(total.amount()).isEqualByComparingTo("15.00");           // version 2
```

Version 2 passed. Version 1 failed with:

```text
[ERROR]   ScaleTempTest.checkedWithIsEqualTo:19
expected: 15.00
 but was: 15.0
```

"Expected fifteen, got fifteen, test red" — absurd until you know the rule. The sum
arrived with scale 1, the expectation had scale 2, and `equals` counts scale. The money
was right; the comparison method was wrong.

### Where it is used

Every test touching a `BigDecimal` amount. Also in business logic (compare amounts with
`compareTo`/`signum`) and in hash collections: a `Set<BigDecimal>` holds `15.0` and
`15.00` as two different keys, because hashing follows `equals`.

### Common mistakes

- **`isEqualTo` on amounts** — works until the first scale drift, then fails with both
  sides looking identical.
- **`setScale` everywhere as a "fix"** — patches each test; the scale-ignoring
  comparison already exists.
- **Expecting value-equality in `HashSet`/`HashMap`** — they follow `equals`; normalise
  scales if you need set-of-values behaviour.
- **Switching to `double`** — worse: binary floats cannot represent `0.1` exactly. Money
  stays in `BigDecimal` built from Strings.

### Link to the task

S8 done; CP3 answerable: *`isEqualTo` uses `BigDecimal.equals`, which counts scale as
part of equality, so numerically equal amounts with different decimal digits fail.* Also
explains why the first test used `isEqualByComparingTo` from day one.

### Do it yourself (second laptop)

1. Create `L9ScaleDemo.java` with the code above. `java L9ScaleDemo.java` → expect
   `false`, `0`, `1`, `2`, `15.0 (scale 1)`.
2. In `MoneyTest.java`, temporarily add a test adding `10.5` and `4.5` GBP asserting
   `isEqualTo(new BigDecimal("15.00"))`. Run → expect `expected: 15.00  but was: 15.0`.
3. Change to `.isEqualByComparingTo("15.00")` → green.
4. Delete the temporary test. Run → green, `Tests run: 1`.

**Done when:** you can state from memory: `equals` compares value and scale; `compareTo`
compares value only; money tests use `isEqualByComparingTo`.

### Your doubts

None raised in this lesson.

### Summary for revision

- `BigDecimal` = unscaled value + scale. `equals` compares both; `compareTo` compares
  the number only.
- `15.0` ≠ `15.00` under `equals` (scales 1 and 2). Equal under `compareTo`.
- Arithmetic changes scales (addition keeps the larger operand scale).
- Tests on amounts: always `isEqualByComparingTo`. Hash collections follow `equals` —
  beware.

### Practice questions (answers included)

**Q1.** What does this print, line by line?

```java
BigDecimal x = new BigDecimal("7.5");
BigDecimal y = new BigDecimal("7.50");

System.out.println(x.equals(y));
System.out.println(x.compareTo(y));
System.out.println(x.scale() + " " + y.scale());
```

**Answer.** `false` (equals counts scale: 1 vs 2), then `0` (same numeric value), then
`1 2`.

**Q2.** Code under test adds `"49.95"` and `"49.95"`, producing `99.90`. The assertion:

```java
assertThat(invoice.total()).isEqualTo(new BigDecimal("99.9"));
```

(a) Pass or fail, and with what message? (b) Write the correct assertion.

**Answer.** (a) Fail: `expected: 99.9  but was: 99.90`. Numerically equal, but `equals`
sees scale 1 versus scale 2. (b)
`assertThat(invoice.total()).isEqualByComparingTo("99.9");` (or `"99.90"` — numerically
the same).

---

## Lesson 10 — The five everyday AssertJ methods (serves S7)

### Definition

Five check methods that cover most daily assertions:

| Method | Checks | Typical line |
| --- | --- | --- |
| `isEqualTo` | general equality (`equals`) | `assertThat(m.currency()).isEqualTo("GBP")` |
| `isEqualByComparingTo` | numeric value of a `BigDecimal` | `assertThat(total.amount()).isEqualByComparingTo("15.00")` |
| `contains` | a collection holds an element | `assertThat(currencies).contains("EUR")` |
| `hasSize` | a collection's element count | `assertThat(currencies).hasSize(3)` |
| `isNotNull` | the value exists at all | `assertThat(total).isNotNull()` |

### Why it is needed

Fluency, and message quality: **the method that matches the claim produces the best
failure message.** AssertJ reports what it is handed. Hand it the whole list and a
failure prints the whole list; reduce the list to a number first and the list vanishes
from the report.

### How it works

`isEqualTo`: the general equality check — Strings, numbers, records.
`isEqualByComparingTo`: the `BigDecimal` value comparison (scale ignored).

The three new ones:

- **`contains(x)`** — membership, any order, other elements allowed. Failure prints the
  collection, the wanted element, the missing element. (Stricter sibling:
  `containsExactly` for "exactly these, in this order".)
- **`hasSize(n)`** — exact element count. Failure prints both sizes **and the
  collection**.
- **`isNotNull()`** — the value exists. Right as an opener for lookups; weak as the only
  claim (a near-lying test). Usually starts a chain.

Rule underneath: **assert directly on the object that carries the claim.** No
pre-computed `list.size()`, no booleans — that strips evidence from the message.

### The example (all run live)

One test per method — `Tests run: 5, Failures: 0`. The new three:

```java
@Test
void containsChecksMembership() {
    List<String> currencies = List.of("GBP", "EUR", "INR");

    assertThat(currencies).contains("EUR");
}

@Test
void hasSizeChecksElementCount() {
    List<String> currencies = List.of("GBP", "EUR", "INR");

    assertThat(currencies).hasSize(3);
}

@Test
void isNotNullChecksExistence() {
    Money ten = new Money(new BigDecimal("10.00"), "GBP");
    Money five = new Money(new BigDecimal("5.00"), "GBP");

    Money total = ten.plus(five);

    assertThat(total).isNotNull();
}
```

(`List.of(...)` builds a fixed list; import `java.util.List`.)

### The failure messages (real output)

`contains("KWD")` against `["GBP", "EUR", "INR"]`:

```text
Expecting ListN:
  ["GBP", "EUR", "INR"]
to contain:
  ["KWD"]
but could not find the following element(s):
  ["KWD"]
```

`hasSize(4)` against the same list:

```text
Expected size: 4 but was: 3 in:
["GBP", "EUR", "INR"]
```

The same size claim written weakly, `assertThat(currencies.size()).isEqualTo(4)`:

```text
expected: 4
 but was: 3
```

Four versus three of *what*? Calling `.size()` yourself reduced the list to an `int`
before AssertJ saw it — Lesson 8's information destruction, milder form. `hasSize`
exists so the list survives into the report.

### Where it is used

Lists of bookings, events, price tiers, validation errors: `hasSize` and `contains`
carry those tests. Repository lookups: `isNotNull` opens, `isEqualTo` checks fields.
Money: `isEqualByComparingTo`, always.

### Common mistakes

- `assertThat(list.size()).isEqualTo(3)` instead of `assertThat(list).hasSize(3)` —
  the weak form's failure hides the list (verified above).
- `isEqualTo` on `BigDecimal` amounts — the Lesson 9 trap.
- `isNotNull()` as the only assertion on a value that cannot be null — near-lying test.
- Expecting `contains` to check order or completeness — it checks membership only.

### Link to the task

S7 done: five methods defined, each run green, and the collection ones also run red with
their messages read.

### Do it yourself (second laptop)

1. In `testing-scratch`, create a temporary `FiveMethodsTest.java` with five tests, one
   per method (use the examples above; `isEqualTo` on `currency()`,
   `isEqualByComparingTo` on a sum).
2. `mvn test` → `Tests run: 6, Failures: 0` (five new + `MoneyTest`).
3. Break them: `contains("KWD")`, `hasSize(4)`, plus a sixth test
   `assertThat(currencies.size()).isEqualTo(4)`. Run → compare the three messages; note
   which size failure shows the list.
4. Delete `FiveMethodsTest.java`. Run → green, `Tests run: 1`.

**Done when:** you can name the five from memory and say what `contains` and `hasSize`
print on failure that hand-rolled checks lose.

### Your doubts

None raised in this lesson.

### Summary for revision

- `isEqualTo` for general equality; `isEqualByComparingTo` for `BigDecimal` amounts;
  `contains` for membership; `hasSize` for counts; `isNotNull` for existence.
- Assert on the object that carries the claim; never reduce it to an int or boolean
  first.
- `hasSize` failures print the collection. `size()`-then-`isEqualTo` failures do not.

### Practice questions (answers included)

**Q1.** `order.items()` returns `List<String>`, `order.total()` returns `BigDecimal`,
`order.customerName()` returns `String`, `repository.find(42)` returns `Order` or
`null`. Write one assertion per claim:
(a) total is numerically 99.90; (b) exactly 3 items; (c) items include "keyboard";
(d) `find(42)` found something; (e) customer name is exactly "Ada".

**Answer.**
(a) `assertThat(order.total()).isEqualByComparingTo("99.90");`
(b) `assertThat(order.items()).hasSize(3);`
(c) `assertThat(order.items()).contains("keyboard");`
(d) `assertThat(repository.find(42)).isNotNull();`
(e) `assertThat(order.customerName()).isEqualTo("Ada");`

**Q2.** Both of these fail against a 3-element list:

```java
assertThat(currencies).hasSize(4);           // Style A
assertThat(currencies.size()).isEqualTo(4);  // Style B
```

One failure message includes `["GBP", "EUR", "INR"]`, one does not. Which is which, and
why?

**Answer.** Style A prints the list — `hasSize` received the whole list, so it can show
it. Style B prints only `expected: 4 but was: 3` — `.size()` collapsed the list into an
`int` before the assertion ran, destroying the evidence (Lesson 8's mechanism).

---

## Lesson 11 — Testing that code refuses: `assertThatThrownBy` (completes the "done when")

### Definition

`assertThatThrownBy` is AssertJ's way to assert that a piece of code **throws**. You
hand it the code wrapped in a lambda; it runs the code, catches the exception, and lets
you check its type and message. If the code does not throw, the assertion itself fails.

### Why it is needed

Half of `Money`'s contract is refusals (F3.6: an invalid `Money` cannot exist). But a
test fails when its method throws — so testing a refusal naively is backwards. Verified
live:

```java
@Test
void naiveRejectsNegativeAmount() {
    new Money(new BigDecimal("-1.00"), "GBP");
}
```

```text
[ERROR] ThrowTempTest.naiveRejectsNegativeAmount <<< ERROR!
java.lang.IllegalArgumentException: amount must be >= 0, was -1.00
```

The code behaved perfectly and the test went red (Errors column). We need the judgement
flipped for one call: throwing is the pass; returning normally is the failure.

### How it works

`() -> code` is a **lambda**: code packed in a box, not run yet, handed over so someone
else can run it later. Then:

1. `assertThatThrownBy(() -> pounds.plus(euros))` runs the boxed code inside its own
   try-catch.
2. Code **throws** → exception captured → chained checks:
   `.isInstanceOf(IllegalArgumentException.class)` (right *kind* of refusal),
   `.hasMessageContaining("currency mismatch")` (right *reason*; a fragment, so edge
   rewording survives).
3. Code **returns normally** → the assertion throws its own `AssertionError`:
   "Expecting code to raise a throwable."

Type and message both matter: the type is what callers catch; the message is what lands
in logs.

### The example — the suite's final shape

```java
@Test
void rejectsNegativeAmount() {
    assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "GBP"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount must be >= 0");
}

@Test
@DisplayName("refuses to add GBP to EUR")
void refusesToAddDifferentCurrencies() {
    Money pounds = new Money(new BigDecimal("10.00"), "GBP");
    Money euros = new Money(new BigDecimal("5.00"), "EUR");

    assertThatThrownBy(() -> pounds.plus(euros))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency mismatch");
}
```

Imports added: `org.junit.jupiter.api.DisplayName` and
`static org.assertj.core.api.Assertions.assertThatThrownBy`. The `@DisplayName` is the
one sanctioned use from the Lesson 7 convention — the task's own example.

AAA note: in refusal tests, Act and Assert **merge** — the action lives inside the
assertion so the throw can be caught. Arrange stays outside (crucial — see mistakes).

### Walkthrough — green, then the red-proof (real output)

Full suite: `Tests run: 3, Failures: 0, Errors: 0` → **the task's "done when" is met**.

Red-proof: with the negative-amount guard deleted from `Money`:

```text
[ERROR] com.zenith.testing.MoneyTest.rejectsNegativeAmount <<< FAILURE!
java.lang.AssertionError:

Expecting code to raise a throwable.
```

Read with Lesson 6 eyes: "Money was supposed to reject a negative amount and silently
accepted one." Guard restored; suite green again.

### Where it is used

Every guarded constructor, every validation rule, every service precondition
(`publishFailsWhenNoPriceTierExists`). In a money system the refusal tests guard against
corrupted balances — often more valuable than the happy paths.

### Common mistakes

- **Calling the code outside the lambda.** It then runs before the assertion's net
  exists; the exception escapes and errors the test.
- **Arrange code inside the lambda.** Verified live: a lambda containing
  `new Money(new BigDecimal("-10.00"), "GBP")` (typo: negative) before `plus` passed
  `isInstanceOf(IllegalArgumentException.class)` — green for the wrong reason. The
  constructor threw; `plus`'s guard was never exercised, and deleting it keeps the test
  green. **Only the one action belongs in the lambda.**
- **Too-loose types.** `.isInstanceOf(RuntimeException.class)` passes for almost any
  crash, including bugs. Assert the specific promised type.
- **The legacy pattern** `try { code; fail(); } catch (...)` — verbose, and silently
  useless if `fail()` is forgotten. Recognise it; write `assertThatThrownBy`.

### Link to the task

"Done when" satisfied: `Money` has three named, well-structured tests
(`addsTwoAmountsInTheSameCurrency`, `rejectsNegativeAmount`,
`refusesToAddDifferentCurrencies`), each proven able to fail. S4's proposed names are
realised; S5's `@DisplayName` example is placed.

### Do it yourself (second laptop)

In `testing-scratch`, `MoneyTest.java`:

1. Add imports `org.junit.jupiter.api.DisplayName` and
   `static org.assertj.core.api.Assertions.assertThatThrownBy`.
2. Add both tests from the example. `mvn test` → `Tests run: 3, Failures: 0`.
3. Red-proof one: comment out the negative-amount `if` in `Money`. Run →
   `rejectsNegativeAmount` fails with `Expecting code to raise a throwable.` Restore.
4. Red-proof two: comment out the currency-mismatch `if` in `plus`. Run → same failure
   shape on `refusesToAddDifferentCurrencies`. Restore.
5. Final run → `Tests run: 3`, green.

**Done when:** three green tests, and you can say what "Expecting code to raise a
throwable" means about the code.

### Your doubts

None raised in this lesson.

### Summary for revision

- A refusal test flips the judgement: throw = pass, normal return = fail.
- The code under test goes inside `() -> ...`; arrange stays outside the lambda.
- Check the specific exception type and a message fragment.
- "Expecting code to raise a throwable" on red = the guard is missing.

### Practice questions (answers included)

**Q1.** `Money`'s constructor throws `IllegalArgumentException` for negative amounts;
`plus` throws `IllegalArgumentException("currency mismatch: cannot add EUR to GBP")` for
mixed currencies. This test is green:

```java
@Test
void refusesToAddDifferentCurrencies() {
    assertThatThrownBy(() -> {
        Money pounds = new Money(new BigDecimal("-10.00"), "GBP");
        Money euros = new Money(new BigDecimal("5.00"), "EUR");
        pounds.plus(euros);
    }).isInstanceOf(IllegalArgumentException.class);
}
```

(a) Which guard does it actually exercise? (b) If the currency-mismatch check is
deleted, does it turn red? (c) Fix it.

**Answer.** (a) Only the constructor's negative-amount guard — the first lambda line
throws, `plus` never runs. (b) No, it stays green (verified live): the constructor still
throws first. The test protects nothing it claims to. (c) Move arrange out of the
lambda:

```java
Money pounds = new Money(new BigDecimal("10.00"), "GBP");
Money euros = new Money(new BigDecimal("5.00"), "EUR");

assertThatThrownBy(() -> pounds.plus(euros))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currency mismatch");
```

**Q2.** The code throws `IllegalArgumentException("currency mismatch: ...")` but the
test asserts `.isInstanceOf(IllegalStateException.class)`. Pass or fail, and why is the
outcome right?

**Answer.** Fail — the captured exception's type does not match. Right outcome: the
specific exception type is part of the public contract (callers catch by type), so a
type change is a behaviour change the test must catch.

---

## Lesson 12 — Test behaviour, not implementation (serves S9, S10, CP4)

### Definition

**Behaviour** is what a class promises its callers: which inputs produce which results
and which refusals, through its public methods. **Implementation** is how it keeps that
promise inside: private helpers, internal steps, representation. The rule: **tests may
touch only behaviour.**

### Why it is needed

Tests exist to make change safe: green after a change should mean "the promises still
hold — ship it." A test coupled to internals goes red when internals change even though
every promise holds. It fights every refactor, teaches the team that red does not really
mean broken, and protects nothing, because nothing a caller relies on was pinned. Litmus
test: **if renaming a private method breaks your test, the test is coupled to the wrong
thing.**

### How it works

One sorting question per assertion: **"could a caller observe this through the public
API?"** Yes → behaviour, testable. No → implementation, off limits.

Coupling smells: the test calls a private/package-private helper directly; visibility
was widened "for testing"; the test asserts representation (`toString`, field layout);
(later, with Mockito) the test verifies an internal method *was called* instead of what
came out.

### The example — a full refactor cycle, five acts, all run live

**Act 1 — internal refactor.** The constructor's uppercasing was extracted into a
package-private helper:

```java
currency = normalise(currency);
// ...
static String normalise(String code) {
    return code.toUpperCase();
}
```

Suite ran: `Tests run: 3, Failures: 0`. The behaviour tests did not notice. That is
refactor-safety.

**Act 2 — the same behaviour tested two ways.** The good test, pinned to the public API
(now a permanent member of `MoneyTest`):

```java
@Test
void normalisesCurrencyToUpperCase() {
    Money m = new Money(new BigDecimal("1.00"), "gbp");

    assertThat(m.currency()).isEqualTo("GBP");
}
```

The bad twin — coupled to the helper, boolean-wrapped, uselessly named:

```java
@Test
void test2() {
    assertTrue(Money.normalise("inr").equals("INR"));
}
```

Run: `Tests run: 5, Failures: 0`. **Both look equally fine today** — green hides the
difference, which is why the trap catches people.

**Act 3 — a real bug.** `normalise` was broken to return the code unchanged. Both tests
failed. The two reports side by side:

```text
[ERROR]   HelperTempTest.test2:13 expected: <true> but was: <false>
[ERROR]   MoneyTest.normalisesCurrencyToUpperCase:27
expected: "GBP"
 but was: "gbp"
```

The first line teaches nothing. The second teaches everything: Money fails to normalise
currency case, wanted `"GBP"`, got `"gbp"`. (Behaviour then restored.)

**Act 4 — the punchline.** The refactor cycle was finished by inlining the helper back
(deleting `normalise`). Behaviour identical for every caller. Result:

```text
[ERROR] COMPILATION ERROR :
[ERROR] HelperTempTest.java:[13,25] cannot find symbol
[ERROR]   symbol:   method normalise(java.lang.String)
[ERROR] BUILD FAILURE
```

The coupled test broke the **compilation of the test code — zero tests could run**. A
change that altered no behaviour was physically blocked by a test that protected none.
The good test needed no edit through either refactor and caught the real bug in between.

**Act 5 — cleanup.** Bad test deleted. Final: `Tests run: 4, Failures: 0, BUILD SUCCESS`.

### The scoreboard

| Event | Good test (public API) | Bad test (helper) |
| --- | --- | --- |
| Extract helper (refactor) | green, untouched | — (did not exist yet) |
| Real bug introduced | red, names behaviour and values | red, "expected true" |
| Inline helper (refactor) | green, untouched | compile error, blocks all tests |

The good test went red exactly once — for the real bug. The bad test's only unique
contribution was blocking a harmless refactor.

### Where it is used

Every refactor from here on. Also a code-review question for any test: *"what public
promise does this pin?"* If none, the test is a liability regardless of colour.

### Common mistakes

- **Testing a helper directly "because it's easier".** Every rename, merge, or inline of
  that helper then breaks the test with behaviour intact.
- **Widening visibility for tests.** A test that needs `private` loosened is aimed at
  the wrong target; test the public method that uses the helper.
- **Asserting `toString` or field layout.** Representation is implementation.
- **Counting coupled tests as coverage.** They have negative value: false alarms plus
  blocked refactors.

### Link to the task

S9: the rule is written and applied. S10: the same test seen written well and badly,
with the real failure output of each. CP4 answerable. With Lesson 11's "done when" met,
**Task F7.1 is complete.**

### Do it yourself (second laptop)

In `testing-scratch`:

1. In `Money`, extract `static String normalise(String code) { return
   code.toUpperCase(); }` and call it from the constructor. Run → `Tests run: 3`, green.
2. Add `normalisesCurrencyToUpperCase` to `MoneyTest`. Add temp `HelperTempTest` with
   the bad `test2` (import `static org.junit.jupiter.api.Assertions.assertTrue`). Run →
   `Tests run: 5`, green.
3. Break `normalise` to `return code;`. Run → compare the two failure messages. Restore.
4. Inline the helper back (delete `normalise`). Run → compilation error naming
   `HelperTempTest`; zero tests ran.
5. Delete `HelperTempTest.java`. Run → `Tests run: 4`, `BUILD SUCCESS`.

**Done when:** you watched one test survive two refactors and catch one real bug, and
the other do the opposite, and you can state the rule and its litmus from memory.

### Your doubts

None raised in this lesson.

### Summary for revision

- Behaviour = caller-observable promises through the public API. Implementation =
  everything else. Tests touch only behaviour.
- Litmus: if renaming a private method breaks a test, the test is wrong.
- A coupled test cries wolf on refactors — and can even block them at compile time —
  while a behaviour test goes red only for real broken promises.
- Review question for any test: what public promise does this pin?

### Practice questions (answers included)

**Q1.** `Wallet`'s public API: `balance()` returns `BigDecimal`; `deposit(Money m)` adds
to the balance; foreign-currency deposits throw `IllegalArgumentException`. Internally it
uses a private helper `applyExchangeChecks(...)` and keeps a private `List<Money>
history`. Classify each test as behaviour or implementation, and say which survive a
refactor that renames the helper and replaces the list:

- (a) asserts `balance()` is `150.00` by comparison after two deposits
- (b) asserts the private `applyExchangeChecks` was called once per deposit
- (c) asserts `deposit` throws `IllegalArgumentException` for a USD deposit into an INR
  wallet
- (d) asserts `wallet.toString()` equals `"Wallet[history=[...]]"`

**Answer.** (a) Behaviour — survives. (c) Behaviour — survives. (b) Implementation — a
caller cannot observe internal calls; breaks on the rename, protects nothing. (d)
Implementation — representation, and it leaks the internal list's name and shape; breaks
when the structure changes.

**Q2.** What does "test behaviour, not implementation" forbid, and why does a test
asserting a private method was called "protect nothing"?

**Answer.** It forbids asserting anything a caller cannot observe through the public
API: that internal helpers exist or were called, widened visibility for tests, and
representation details. Such a test protects nothing because no caller-visible promise
is pinned: it can go red — or block compilation — while behaviour is intact, and its
green never guaranteed any property a caller relies on.

---

## Task wrap-up — checkpoint and final state

### Checkpoint — the four questions, answered in full

**CP1. What are the three parts of Arrange–Act–Assert, and what belongs in each?**
Arrange: create the objects and inputs the action needs — the stage-setting; may be
several lines. Act: the one call the test exists to check — one line; if the middle
block has four lines, the test tests too much. Assert: the `assertThat` checks on the
result, at the end. Blank lines separate the blocks so a reader sees the one action in
under a second. Two sanctioned merges: constructor tests merge arrange and act (the
constructor *is* the action); refusal tests merge act and assert (the action lives
inside `assertThatThrownBy` so the throw can be caught).

**CP2. Why is AssertJ preferred over `assertTrue`? The concrete reason.**
`assertTrue` receives only a boolean: the comparison has already collapsed, and the
values that produced the `false` are destroyed before the assertion runs, so the best
possible message is `expected: <true> but was: <false>`. AssertJ receives actual and
expected separately and still holds both at failure time, so it prints both — measured
side by side on the same bug: `expected: 16.00  but was: 15.00` versus
`expected: <true> but was: <false>`.

**CP3. Why does comparing two `BigDecimal` values with `isEqualTo` sometimes fail
unexpectedly?**
`isEqualTo` uses `equals`, and `BigDecimal.equals` compares the numeric value **and the
scale** (digit count after the decimal point). `15.0` (scale 1) and `15.00` (scale 2)
are numerically equal but not `equals` — and arithmetic drifts scales (`10.5 + 4.5`
gives `15.0`, scale 1). Measured failure: `expected: 15.00  but was: 15.0`. Money tests
use `isEqualByComparingTo`, which uses `compareTo` — numeric value only.

**CP4. What does "test behaviour, not implementation" forbid you from doing?**
Asserting anything a caller cannot observe through the public API: that a private or
package-private helper exists or was called, widening visibility so a test can reach
internals, and pinning representation such as `toString`. A test that does these breaks
on harmless refactors (demonstrated: a compile error that blocked the whole suite) while
pinning no promise any caller relies on.

### The final artifact

`scratch-app/src/test/java/com/zenith/testing/MoneyTest.java` — four named,
well-structured, red-proofed tests:

```java
package com.zenith.testing;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addsTwoAmountsInTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money five = new Money(new BigDecimal("5.00"), "GBP");

        Money total = ten.plus(five);

        assertThat(total.amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void normalisesCurrencyToUpperCase() {
        Money m = new Money(new BigDecimal("1.00"), "gbp");

        assertThat(m.currency()).isEqualTo("GBP");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be >= 0");
    }

    @Test
    @DisplayName("refuses to add GBP to EUR")
    void refusesToAddDifferentCurrencies() {
        Money pounds = new Money(new BigDecimal("10.00"), "GBP");
        Money euros = new Money(new BigDecimal("5.00"), "EUR");

        assertThatThrownBy(() -> pounds.plus(euros))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }
}
```

Final run:

```text
[INFO] Running com.zenith.testing.MoneyTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Every test in this file has been seen red at least once, for the right reason — none of
them is a lying test.

### How the pieces fit together

A test is code that must be able to fail (L1). It lives in `src/test/java`, run by
Surefire via `mvn test`, with JUnit 5, AssertJ, and Mockito supplied by
`spring-boot-starter-test` (L2). JUnit finds methods by `@Test` and judges them by one
rule — return = pass, throw = fail (L3). AssertJ turns wrong answers into throws with
evidence inside (L4), laid out as Arrange–Act–Assert so the one action is visible in a
second (L5), under a name that *is* the failure report (L6), with `@DisplayName` as an
optional bonus layer for IDE readers (L7). Every new test is broken once on purpose,
because the boolean alternative destroys the evidence (L8); money amounts compare by
value, not scale (L9); five methods cover the daily claims (L10); refusals are asserted
with `assertThatThrownBy`, the action alone inside the lambda (L11); and everything
asserts only what a caller can observe, so refactors stay free (L12).

**Task F7.1 complete.** Subtasks S1–S10 done, checkpoint CP1–CP4 answered, "done when"
exceeded (four tests instead of three).

---

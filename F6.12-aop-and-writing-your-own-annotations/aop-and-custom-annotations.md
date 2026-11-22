# F6.12 — AOP and writing your own annotations

**Learning outcome.** You can write a custom annotation and a Spring aspect that runs
around every method carrying it, and you can explain the one limitation of Spring's
proxy-based AOP that catches absolutely everybody once: self-invocation.

**Why this exists.** Phase 2 of the project captures its audit trail with an aspect, and
Phase 5 builds `@Idempotent` — the most reused single piece of the entire project — as an
annotation plus an interceptor. Without this task, both are magic you copy. It also
explains `@Transactional`, `@Cacheable`, and `@Async` in one go, because they are all the
same mechanism.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Cross-cutting concern** | Work needed in many places that is not what those places are *about* — timing, logging, auditing, retries, transactions. |
| **AOP** | Aspect-Oriented Programming. Running code around methods without those methods knowing. |
| **Aspect** | The class holding that around-code. Marked `@Aspect @Component`. |
| **Advice** | The code that runs around a method. `@Around` is the useful kind — it wraps the call and decides whether the real method runs at all. |
| **Join point** | A place advice *could* run — in Spring, a method call on a bean. |
| **Pointcut** | The expression that selects which join points actually get the advice, e.g. `@annotation(com.zenith.common.Timed)`. |
| **`ProceedingJoinPoint`** | The handle to the wrapped call. `proceed()` runs the real method; skip it and the real method never runs. |
| **Proxy** | A stand-in object Spring hands your callers instead of your real bean. It does the extra work, then forwards. This is the Decorator pattern, applied for you. |
| **JDK proxy vs CGLIB** | Two ways Spring builds a proxy: a JDK proxy implements your interface; CGLIB generates a subclass of your class. CGLIB is why `final` and `private` methods cannot be advised. |
| **Self-invocation** | Calling another method of the same object directly. The call never leaves the object, never passes through the proxy, so no advice runs. The most common Spring bug there is. |
| **`@Retention`** | How long an annotation survives. The default is `CLASS` — invisible at runtime. An aspect can only see `RUNTIME`. |
| **`@Target`** | Where the annotation may legally be written — method, class, field. |

## The task's subtasks

- **S1.** Write three methods that each carry the same timing block. Name the problem: a cross-cutting concern.
- **S2.** Add `spring-boot-starter-aop`. Declare `@Timed` with `@Retention(RetentionPolicy.RUNTIME)` and `@Target(ElementType.METHOD)`. Try the default retention once to recognise the silent symptom.
- **S3.** Write the aspect: `@Aspect @Component`, `@Around` with a `ProceedingJoinPoint`. Use the annotation's fully qualified name in the pointcut. Prove it fired, then delete the three duplicated blocks.
- **S4.** Give the annotation a value — `@Timed("checkout")` — and read it inside the aspect via the `MethodSignature`.
- **S5.** Skip `proceed()` under a condition and return a canned value instead. The real method never runs.
- **S6.** Break it on purpose: call an annotated method from inside the same class. The aspect does not fire. Work out why.
- **S7.** Fix self-invocation both honest ways: own bean, or inject the bean into itself. Choose one and say why.
- **S8.** Print `service.getClass().getName()`, find `$$SpringCGLIB$$`. Make a method `final` and confirm it can no longer be advised.
- **S9.** Connect `@Transactional`, `@Cacheable`, `@Async`: all proxies, all the same mechanism as an `@Around` aspect.

## Roadmap — 14 small lessons

| # | Lesson | What it gives you |
|---|---|---|
| 1 | The cross-cutting concern | Name the problem AOP solves, and spot it in any code (S1) |
| 2 | Declaring your own annotation | Write `@interface Timed`; know it does nothing by itself (S2) |
| 3 | `@Retention` | Why the annotation must survive until runtime; the silent symptom when it does not (S2) |
| 4 | `@Target` | Control where the annotation may be written (S2) |
| 5 | What AOP is | The idea: run extra code around methods without editing them |
| 6 | The proxy | How Spring pulls it off: a stand-in object. The heart of the task |
| 7 | The four AOP words | Aspect, advice, join point, pointcut — read and write aspect code |
| 8 | Writing the aspect | Build `@Timed` for real, see it fire, break retention on purpose, delete the duplication (S2 + S3) |
| 9 | Annotation values | Pass `"checkout"` from the annotation into the aspect via `MethodSignature` (S4) |
| 10 | Skipping `proceed()` | The advice decides whether the real method runs — the idempotency trick (S5) |
| 11 | Self-invocation: the bug | Watch an internal call silently bypass the aspect, and why (S6) |
| 12 | Self-invocation: the fixes | Two honest fixes, trade-offs, and a choice (S7) |
| 13 | What the proxy really is | `$$SpringCGLIB$$`, JDK proxy vs CGLIB, why `final`/`private` escape advice (S8) |
| 14 | One mechanism everywhere | `@Transactional`, `@Cacheable`, `@Async` in one stroke; checkpoint (S9) |

## Where the code lives

Plain-Java lessons (1–4) each have a folder under
`F6.12-aop-and-writing-your-own-annotations/lessons/`, compiled with `javac` and run with
`java` (Java 21). From Lesson 8 on there is a Spring Boot scratch app (Java 21, Maven,
`spring-boot-starter-aop`); its setup is written down in that lesson.

---

## Lesson 1 — The cross-cutting concern (subtask S1)

### 1. What is it?

A cross-cutting concern is work your program needs in many places, but that is not what
any of those places is *about*. Timing is the classic one. Logging, security checks, and
transactions are others.

### 2. Why do we need this idea?

Imagine a booking service. The boss says: "Every method must log how long it took."

With no special tool, the only option is to copy a timing block into every method. Start a
clock at the top. Stop it at the bottom. Print the difference.

Now every method carries code that has nothing to do with its job. `reserveSeat` is about
reserving seats. It is not about clocks. But clock code now lives inside it — and inside
every other method too. The same off-topic block, copied everywhere. That is the pain.

### 3. How do you recognize one?

Two quick tests.

**Test 1 — the one-sentence test.** Say what the method is for, in one sentence.
"reserveSeat reserves a seat." Does the sentence mention timing? No. Then the timing code
inside it is a cross-cutting concern.

**Test 2 — the unrelated-methods test.** Does the exact same block appear in methods that
do *different* jobs? Reserving, cancelling, listing — three unrelated jobs, one identical
block. Ordinary duplication lives in methods doing similar jobs. Cross-cutting duplication
lives in methods doing unrelated jobs.

The name comes from a picture. Draw each method as a vertical column; each column is about
one job. The timing requirement is a horizontal stripe. It cuts *across* all the columns.
Hence: cross-cutting.

### 4. Simple example

Plain Java, no Spring. `Thread.sleep(120)` pauses for 120 milliseconds — it stands in for
real work. `System.nanoTime()` reads a very precise clock, in nanoseconds. The file lives
in `lessons/l1-cross-cutting-concern/BookingService.java`.

```java
public class BookingService {

    void reserveSeat() throws InterruptedException {
        long start = System.nanoTime();                              // timing code

        Thread.sleep(120);                                           // the real work (pretend)
        System.out.println("Seat reserved.");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code
        System.out.println("reserveSeat took " + elapsedMs + " ms"); // timing code
    }

    void cancelBooking() throws InterruptedException {
        long start = System.nanoTime();                              // timing code — copy 2

        Thread.sleep(80);                                            // the real work (pretend)
        System.out.println("Booking cancelled.");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code — copy 2
        System.out.println("cancelBooking took " + elapsedMs + " ms"); // timing code — copy 2
    }

    void listBookings() throws InterruptedException {
        long start = System.nanoTime();                              // timing code — copy 3

        Thread.sleep(40);                                            // the real work (pretend)
        System.out.println("Bookings: [A1, B4, C2]");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code — copy 3
        System.out.println("listBookings took " + elapsedMs + " ms"); // timing code — copy 3
    }

    public static void main(String[] args) throws InterruptedException {
        BookingService service = new BookingService();
        service.reserveSeat();
        service.cancelBooking();
        service.listBookings();
    }
}
```

Real output from a real run:

```text
Seat reserved.
reserveSeat took 125 ms
Booking cancelled.
cancelBooking took 81 ms
Bookings: [A1, B4, C2]
listBookings took 40 ms
```

The numbers are close to 120, 80, 40 but not exact. `Thread.sleep` promises "at least this
long", not "exactly this long".

### 5. Walkthrough

Just the first method:

```java
void reserveSeat() throws InterruptedException {
    long start = System.nanoTime();                              // line 1
    Thread.sleep(120);                                           // line 2
    System.out.println("Seat reserved.");                        // line 3
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // line 4
    System.out.println("reserveSeat took " + elapsedMs + " ms"); // line 5
}
```

- Line 1: read the clock, remember the number. Timing, not booking.
- Lines 2–3: the real work. The only part the method is *for*.
- Line 4: read the clock again, subtract, convert nanoseconds to milliseconds. Timing.
- Line 5: print the result. Timing.

Score: 2 lines of real work, 3 lines of clock-keeping — repeated in all three methods.
Nine copied lines total.

What the duplication costs:

- "Print seconds, not milliseconds" means editing 3 methods. Miss one, and the logs
  silently disagree.
- A teammate adds `changeSeat()` and forgets the timing block. No error. No warning. The
  method is simply invisible in the timings.

Hold the phrase: *no error, no warning*. This whole task is about problems that fail
silently.

### 6. Where is it used?

Real cross-cutting concerns, each one off-topic for the methods that need it:

| Concern | Plain meaning |
|---|---|
| Timing / metrics | How long did this take? |
| Logging | What happened, when? |
| Audit trail | Which user did what? Project Phase 2 captures this with an aspect. |
| Transactions | Make these database steps all-or-nothing. That is `@Transactional`. |
| Caching | Same question again? Return the saved answer. That is `@Cacheable`. |
| Retries | It failed once. Quietly try again. |
| Idempotency | Same request twice? Return the stored response, do not redo the work. Project Phase 5 builds `@Idempotent` for this. |

### 7. Common mistakes

**Mistake 1: "Just extract a helper method."** A helper shrinks the block but does not
remove the problem:

```java
void reserveSeat() throws InterruptedException {
    long start = Timing.begin();          // still here, in every method
    Thread.sleep(120);
    System.out.println("Seat reserved.");
    Timing.end(start, "reserveSeat");     // still here, in every method
}
```

Two calls still sit in every method. The method name is still typed by hand. New methods
can still forget both calls — silently. The duplication got smaller; it did not go away.

**Mistake 2: calling all duplication cross-cutting.** If two methods share real business
logic, extract a normal shared method. Cross-cutting means the repeated code is about a
*different topic* than the methods that carry it. Use the one-sentence test.

**Mistake 3: "We will just be careful."** Careful does not survive teams, deadlines, or
new joiners. A rule that breaks silently will be broken.

### 8. Link to the task

This is Subtask S1, word for word: write three service methods that each begin by starting
a timer and end by logging the elapsed time. The duplicated block has nothing to do with
what the methods are for. Every later subtask exists to delete those nine lines without
losing the timing.

### 9. Check my understanding

**Q1.** Here is a complete class:

```java
public class AccountService {

    void closeAccount(String user) {
        if (!user.equals("admin")) {                  // line A
            throw new RuntimeException("no access");  // line B
        }
        System.out.println("Account closed.");        // line C
    }

    void renameAccount(String user) {
        if (!user.equals("admin")) {                  // line D
            throw new RuntimeException("no access");  // line E
        }
        System.out.println("Account renamed.");       // line F
    }
}
```

Which lines are a cross-cutting concern? And which test tells you so?

**Answer.** Lines A, B, D, and E — the permission check. Both tests say so. The
one-sentence test: "closeAccount closes an account" — the sentence says nothing about
checking who the user is, so the check is off-topic for the method. The unrelated-methods
test: closing and renaming are different jobs, yet both start with the identical block.
Lines C and F are the real work. The concern here is security, not timing — the shape of
the problem is the same.

**Q2.** A teammate shows you this complete class and says: "I moved timing into a helper
class called `Timing`, so the duplication problem is solved."

```java
public class ReportService {

    void dailyReport() {
        long start = Timing.begin();
        System.out.println("Daily report built.");
        Timing.end(start, "dailyReport");
    }

    void weeklyReport() {
        long start = Timing.begin();
        System.out.println("Weekly report built.");
        Timing.end(start, "weeklyReport");
    }
}
```

(a) What is still copied into every method? (b) Next week someone adds `monthlyReport()`
and forgets the `Timing` calls. What error message appears?

**Answer.** (a) Three things are still copied: the `Timing.begin()` call at the top, the
`Timing.end(...)` call at the bottom, and the method's own name typed by hand as a string
(`"dailyReport"`, `"weeklyReport"`). (b) No error message appears at all. The code
compiles, runs, and prints the report. The timing for `monthlyReport` is simply missing,
and nothing anywhere reports that. This silent failure is the real danger — the helper
made the block shorter but kept the problem.

### Do it yourself (second laptop)

**Step 0 — only if Java is missing.** Check first:

```bash
java -version    # want: openjdk version "21..."
```

If that fails:

```bash
brew install openjdk@21
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version
```

**Step 1 — folder and file:**

```bash
mkdir -p ~/foundations/F6.12-aop/lesson1
cd ~/foundations/F6.12-aop/lesson1
```

Create `BookingService.java` with exactly the code from section 4 of this lesson.

**Step 2 — compile and run:**

```bash
javac BookingService.java   # success = prints nothing
java BookingService
```

Expected output (the millisecond numbers will differ slightly — that is normal):

```text
Seat reserved.
reserveSeat took 125 ms
Booking cancelled.
cancelBooking took 81 ms
Bookings: [A1, B4, C2]
listBookings took 40 ms
```

Expected failures and what they mean:

| You see | It means | Fix |
|---|---|---|
| `javac: command not found` | Java is not installed | Do Step 0 |
| `error: cannot find symbol` | A typo in the file | Compare against section 4 line by line |

**Done when:** you see three "took N ms" lines, and you can point at the nine lines that
are not about booking.

### Summary

- A cross-cutting concern is work needed in many places that is not what those places are
  about: timing, logging, security, transactions, caching, retries, idempotency.
- Spot it with two tests: the one-sentence test (describe the method; is that code in the
  sentence?) and the unrelated-methods test (same block, unrelated jobs).
- Copied concern code costs you: every change touches N methods, and forgotten copies fail
  silently — no error, no warning.
- A helper method shrinks the block but keeps the duplication: two calls and a hand-typed
  name in every method, still forgettable.
- The rest of this task deletes those copied lines without losing the behaviour.

---

## Lesson 2 — Declaring your own annotation (subtask S2, first half)

### 1. What is it?

An annotation is a label you attach to a piece of code. `public @interface Timed { }`
declares a brand-new label named `Timed`. One line, and the label exists. Ready-made
labels like `@SpringBootApplication` and `@Override` have been in use since F1.3; this
lesson makes one from scratch.

### 2. Why do we need it?

Lesson 1 ended with three methods carrying nine copied lines of clock code. The plan for
this task: outside machinery will do the timing *for* the methods. But the machinery must
know *which* methods to time. We need a way to mark a method.

The options, compared:

- A list of method names in a text file? Strings. Rename the method, and the list is
  silently wrong.
- A naming rule, like starting every timed method with `timed`? Ugly, and nothing
  enforces it.
- A comment like `// please time this`? Comments are for humans. Tools ignore them.
- An annotation: sits directly on the method, spelling checked by the compiler, readable
  by tools. Mark the methods now with `@Timed`, build the reader later.

### 3. How does it work?

Declaring takes one line in its own file, `Timed.java`:

```java
public @interface Timed { }
```

- `@interface` is the keyword meaning "I am declaring a new annotation". The `@` matters:
  plain `interface` declares a completely different thing.
- The name follows class rules: `Timed`, capital T, file `Timed.java`.
- `{ }` is the empty body — the label carries no extra information yet (Lesson 9 adds a
  value).

Attaching takes one line directly above a method:

```java
@Timed
void reserveSeat() { ... }
```

The most important sentence of the lesson: **an annotation does nothing by itself.** It is
a sticker, not a machine. A "FRAGILE" sticker does not make the box safe; someone must
read the sticker and act. Until some code reads `@Timed`, it changes nothing.

### 4. Simple example

Two files, in `lessons/l2-declaring-annotation/`. `Thread.sleep(100)` pauses for 100
milliseconds — pretend work.

`Timed.java`:

```java
public @interface Timed { }
```

`Demo.java`:

```java
public class Demo {

    @Timed
    static void work() throws InterruptedException {
        Thread.sleep(100);                   // pretend work
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws InterruptedException {
        work();
    }
}
```

Compile and run:

```bash
javac *.java
java Demo
```

Real output, in full:

```text
Work done.
```

The method is labelled `@Timed`. It slept 100 milliseconds. **No timing was printed.** The
sticker is on the box; nobody is reading stickers yet.

### 5. Walkthrough

1. `javac *.java` compiles both files. The compiler sees `@Timed` above `work()` and
   checks: does a declaration named `Timed` exist? Yes. So it compiles.
2. The compiler records the label into `Demo.class`, next to `work()`.
3. `java Demo` runs `main`, which calls `work()`.
4. `work()` sleeps 100 milliseconds, prints `Work done.`
5. No code ever asks "does this method carry `@Timed`?" Nobody asks, so the label has no
   effect. The program behaves exactly as if the label were not there.

A label only matters when a reader exists. The reader — the aspect — arrives in Lesson 8.

### 6. Where is it used?

Every annotation is the same pattern: a label plus a reader.

| Label | Who reads it | What the reader does |
|---|---|---|
| `@Override` | The compiler | Errors if the method does not really override anything |
| `@Deprecated` | The compiler | Warns anyone who calls the marked method |
| `@Test` | JUnit | Runs the marked method as a test |
| `@Component` | Spring, at startup | Creates a bean from the marked class |
| `@Entity` | Hibernate | Maps the marked class to a table |
| `@Timed` (ours) | Nobody, yet | Nothing, yet |

### 7. Common mistakes

**Mistake 1: expecting the label to act by itself.** Write `@Timed`, run, wait for
timings. Nothing appears — and no error appears either. If an annotation "does nothing",
the first question is always: *who is supposed to read it, and is that reader running?*

**Mistake 2: confusing `@interface` with `interface`.** Plain `interface` declares a
contract that classes implement. `@interface` declares a label. Nobody ever writes
`implements Timed`.

**Mistake 3: fearing typos.** Attaching a label is typo-safe. Misspelling it as `@Timde`
produced this real compile error:

```text
TypoDemo.java:4: error: cannot find symbol
    @Timde   // misspelled on purpose
     ^
  symbol:   class Timde
  location: class TypoDemo
1 error
```

Decoded: the compiler looked for a declaration named `Timde` and found none. Compare that
with a method name in a config file, where a typo silently stops matching.

### 8. Link to the task

Subtask S2 says: declare
`@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface Timed { }`.
This lesson built the core: `public @interface Timed { }`. The two decorations in front —
`@Retention(...)` and `@Target(...)` — are labels *on the label*: Lessons 3 and 4.

### 9. Check my understanding

**Q1.** Here is a complete program, two files.

File `Slow.java`:

```java
public @interface Slow { }
```

File `Worker.java`:

```java
public class Worker {

    @Slow
    static void crunch() throws InterruptedException {
        Thread.sleep(200);
        System.out.println("Crunching finished.");
    }

    public static void main(String[] args) throws InterruptedException {
        crunch();
    }
}
```

A teammate says: "Because of `@Slow`, running this will print how long `crunch` took."
What does the program actually print, and why?

**Answer.** It prints exactly one line, after a pause of about 200 milliseconds:

```text
Crunching finished.
```

No duration is printed. `@Slow` is a label, and a label does nothing by itself. No code in
this program reads the label — nothing asks "does `crunch` carry `@Slow`?" — so it has no
effect on how the program runs. For a duration to print, some reader code would have to
find the label and do the timing.

**Q2.** Two lines of Java, in two different files.

Line 1, in `Audited.java`: `public @interface Audited { }`

Line 2, written directly above a method `void pay() { ... }` in some service class:
`@Audited`

In one sentence each: what does line 1 do, and what does line 2 do? And after both lines
exist, does anything about how `pay()` runs change?

**Answer.** Line 1 *declares* a new annotation type named `Audited` — it creates the
label itself, so the name now exists and the compiler accepts it. Line 2 *attaches* that
label to the method `pay()` — it marks this one method. Nothing about how `pay()` runs
changes: the label sits on the method, but no code reads it, and an unread label has no
effect at all.

### Do it yourself (second laptop)

```bash
mkdir -p ~/foundations/F6.12-aop/lesson2
cd ~/foundations/F6.12-aop/lesson2
```

Create `Timed.java` and `Demo.java` with exactly the code from section 4 of this lesson.
Then:

```bash
javac *.java    # success = prints nothing
java Demo
```

Expected output:

```text
Work done.
```

No timing line — that is the correct result, not a failure.

Then the typo experiment: in `Demo.java`, change `@Timed` to `@Timde` and run
`javac *.java`. Expect `error: cannot find symbol`. Change it back and recompile.

**Done when:** you can answer out loud, in your own words: "the method is labelled, so why
did no timing print?"

### Summary

- `public @interface Timed { }` declares a new annotation — a label. `@Timed` above a
  method attaches it.
- An annotation does nothing by itself. It is a sticker, not a machine. Behaviour only
  appears when some reader code finds the label and acts.
- Every familiar annotation is label + reader: `@Override` read by the compiler, `@Test`
  by JUnit, `@Component` by Spring, `@Entity` by Hibernate.
- Attaching is typo-safe: a misspelled label is a compile error (`cannot find symbol`),
  not a silent miss.
- `@interface` declares labels; plain `interface` declares contracts. Different things.

---

## Lesson 3 — `@Retention`: how long your annotation survives (subtask S2)

### 1. What is it?

`@Retention` is a label you put on your annotation's *declaration*. It answers one
question: how long does this label survive? Three possible answers: `SOURCE`, `CLASS`,
and `RUNTIME`.

### 2. Why do we need it?

Code goes through three stages of life:

1. **Source code** — the `.java` text.
2. **Class file** — the `.class` file the compiler produces.
3. **Running program** — what the JVM loads into memory and executes.

An annotation can be dropped at any stage boundary. `@Retention` chooses where:

| Level | In the `.java` text? | In the `.class` file? | Visible to the running program? |
|---|---|---|---|
| `SOURCE` | yes | **no** — compiler drops it | no |
| `CLASS` **(the default)** | yes | yes | **no** — loaded class hides it |
| `RUNTIME` | yes | yes | **yes** |

Memory aid: each level names the *last* stage where the label exists.

Why it matters: the aspect must find `@Timed` **while the program runs**. If the label is
gone by then, the aspect finds nothing — and finding nothing is not an error. Nothing
crashes, nothing warns; the feature is simply absent. The default is `CLASS`, so a bare
`public @interface Timed { }` is invisible at runtime. The wrong default, for us, out of
the box.

### 3. How does it work?

One small tool is needed to *see* visibility: **reflection** — a running program examining
its own classes and methods. Two calls are enough:

- `Inspect.class.getDeclaredMethod("work")` — a handle to the method named `work`.
- `.getAnnotations()` — all labels **visible at runtime** on that method.

When Spring's aspect machinery later decides "should I time this method?", it does
essentially this same check. If `getAnnotations()` returns nothing here, an aspect is
blind there too.

### 4. Simple example

Two versions of the same two files, in `lessons/l3-retention/`. Only `Timed.java` differs.

**Version 1 — `v1-default/`, no `@Retention` line:**

```java
// Timed.java
public @interface Timed { }
```

```java
// Inspect.java
import java.lang.reflect.Method;
import java.util.Arrays;

public class Inspect {

    @Timed
    static void work() {
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws Exception {
        Method m = Inspect.class.getDeclaredMethod("work");
        System.out.println("Labels visible at runtime: " + Arrays.toString(m.getAnnotations()));
    }
}
```

Real output:

```text
Labels visible at runtime: []
```

Empty. The method *is* annotated in the source — and the running program cannot see it.

**Version 2 — `v2-runtime/`, one declaration change:**

```java
// Timed.java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Timed { }
```

`Inspect.java` is byte-for-byte identical to version 1. Real output:

```text
Labels visible at runtime: [@Timed()]
```

One line on the declaration, and the label survives into the running program.

### 5. Walkthrough

Version 1, step by step:

1. `javac *.java` compiles both files. No warning — the compiler is fine with the default.
2. The compiler writes the label into `Inspect.class`. It really is in the file.
3. `java Inspect` starts. The JVM loads the class. Because retention is `CLASS`, the JVM
   does **not** include the label in the runtime view of the method.
4. `getAnnotations()` returns an empty array. No error. Just empty.

Proof for step 2 — `javap -v Inspect` prints what is inside the class file. Version 1
contains:

```text
RuntimeInvisibleAnnotations:
  0: #50()
    Timed
```

**RuntimeInvisibleAnnotations** — the label is in the file, filed under "invisible at
runtime". That is the JVM's own word for the default. Version 2's file instead contains:

```text
RuntimeVisibleAnnotations:
  0: #50()
    Timed
```

Same label, different shelf. The shelf is what `@Retention` chooses.

### 6. Where is it used?

| Annotation | Retention | Why |
|---|---|---|
| `@Override` | `SOURCE` | Only the compiler needs it — a compile-time check |
| Lombok's `@Getter` | `SOURCE` | Lombok generates code during compilation; then the label's job is done |
| `@Test` (JUnit) | `RUNTIME` | JUnit finds test methods while running |
| `@Component`, `@Transactional` (Spring) | `RUNTIME` | Spring reads them while the app runs |
| `@Entity` (JPA) | `RUNTIME` | Hibernate reads mappings while the app runs |

The pattern: if a framework reads your annotation while the program runs, it must be
`RUNTIME`. Frameworks are exactly our case.

### 7. Common mistakes

**Mistake 1 — leaving the default.** Everything compiles, the app starts cleanly, no stack
trace, no warning. The feature is simply missing. Rule to burn in: **if an annotation
seems ignored at runtime, check `@Retention(RetentionPolicy.RUNTIME)` first.** Ten-second
check, most likely cause.

**Mistake 2 — assuming the useful setting is the default.** The default is `CLASS`. You
must write `RUNTIME` yourself, every time a framework will read your annotation.

**Mistake 3 — treating `SOURCE` and `CLASS` as the same.** Both are invisible at runtime.
The difference: `CLASS` keeps the label inside the `.class` file (file-reading tools can
see it); `SOURCE` means the compiler drops it completely.

### 8. Link to the task

Subtask S2: "**Pause on `RUNTIME`.** The default is `CLASS`: the annotation is compiled in
but thrown away before runtime, your aspect never fires, nothing errors, and you lose an
hour. Try it with the default once so you recognise the symptom." That exact experiment —
empty brackets, no error — has now been run in a tiny program where nothing else could be
wrong. When the same silence happens inside a full Spring app (Lesson 8), it is a
seconds-long diagnosis instead of an hour.

### 9. Check my understanding

**Q1.** Here is a complete program, two files.

File `Audit.java`:

```java
public @interface Audit { }
```

File `Main.java`:

```java
import java.lang.reflect.Method;
import java.util.Arrays;

public class Main {

    @Audit
    static void pay() {
        System.out.println("Paid.");
    }

    public static void main(String[] args) throws Exception {
        Method m = Main.class.getDeclaredMethod("pay");
        System.out.println(Arrays.toString(m.getAnnotations()));
    }
}
```

What exactly does this program print? And what is the smallest change that makes it print
`[@Audit()]`?

**Answer.** It prints exactly one line:

```text
[]
```

Two things to notice. First, `Paid.` does **not** print — `main` never calls `pay()`; it
only inspects it. Second, the brackets are empty because `Audit` has no `@Retention` line,
so the default `CLASS` applies: the label is in the class file but invisible to the
running program, and `getAnnotations()` returns nothing. The smallest change: declare
`Audit` as

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Audit { }
```

Then the program prints `[@Audit()]`.

**Q2.** A teammate built a Spring app with a custom annotation and an aspect that should
react to it. The app starts with zero errors in the log. The aspect never does anything.
The teammate starts rewriting the aspect class. What should they check first, before
touching the aspect? And why does this kind of failure produce no error message anywhere?

**Answer.** Check the annotation's declaration for `@Retention(RetentionPolicy.RUNTIME)`
first. With the default retention (`CLASS`), the label is thrown off the runtime view of
every method. The aspect machinery scans methods for the label, finds none, and matches
nothing. Matching nothing is a perfectly legal outcome — "no methods wanted this advice" —
so Spring has nothing to report: no exception, no warning, no log line. The aspect code
can be flawless and still do nothing. That is why the ten-second retention check comes
before any rewrite.

### Do it yourself (second laptop)

```bash
mkdir -p ~/foundations/F6.12-aop/lesson3/v1-default
mkdir -p ~/foundations/F6.12-aop/lesson3/v2-runtime
```

In `v1-default/`, create `Timed.java` and `Inspect.java` exactly as in section 4,
version 1. Then:

```bash
cd ~/foundations/F6.12-aop/lesson3/v1-default
javac *.java && java Inspect
```

Expected: `Labels visible at runtime: []`

Look inside the class file:

```bash
javap -v Inspect | grep -A 2 "leAnnotations:"
```

Expected:

```text
    RuntimeInvisibleAnnotations:
      0: #50()
        Timed
```

In `v2-runtime/`, create the version-2 `Timed.java` (with the `@Retention` line and its
two imports) and the identical `Inspect.java`. Then:

```bash
cd ~/foundations/F6.12-aop/lesson3/v2-runtime
javac *.java && java Inspect
javap -v Inspect | grep -A 2 "leAnnotations:"
```

Expected: `Labels visible at runtime: [@Timed()]`, and `RuntimeVisibleAnnotations` in the
`javap` output.

Expected failures and what they mean:

| You see | It means | Fix |
|---|---|---|
| `error: cannot find symbol ... RetentionPolicy` | Missing imports in `Timed.java` | Add both `java.lang.annotation` imports |
| `Error: Could not find or load main class Inspect` | Running from the wrong folder | `cd` into the version folder first |

**Done when:** you can answer out loud: in version 1, the label was written in the source
— where did it go? (It is still inside the `.class` file, on the "invisible" shelf; the
JVM never shows it to the running program.)

### Summary

- `@Retention` sets how long an annotation survives: `SOURCE` (compiler only), `CLASS`
  (in the file, invisible at runtime — **the default**), `RUNTIME` (visible to running
  code).
- Aspects, and frameworks generally, read annotations at runtime — so a framework-facing
  annotation must say `@Retention(RetentionPolicy.RUNTIME)` explicitly.
- The failure is silent: no error, no warning, `getAnnotations()` just returns empty and
  pointcuts match nothing.
- Diagnosis rule: an aspect that seems ignored → check for `RetentionPolicy.RUNTIME`
  first.
- `javap -v` shows the two shelves by name: `RuntimeInvisibleAnnotations` (default) vs
  `RuntimeVisibleAnnotations` (`RUNTIME`).

---

## Lesson 4 — `@Target`: where your annotation may be written (subtask S2)

### 1. What is it?

`@Target` is a label on your annotation's *declaration*. It lists the kinds of places
where the annotation may legally be attached: methods, classes, fields, and so on. Writing
it anywhere else becomes a compile error.

### 2. Why do we need it?

Without `@Target`, an annotation may be written almost anywhere: on a class, a field, a
parameter, a local variable. For `@Timed`, most of those places are nonsense — timing a
*field* means nothing; a field does not run. But if nonsense placement compiles, someone
will eventually write it, expect timings, and get nothing.

Same enemy as Lessons 1–3: the failure that makes no sound. `@Target` turns one whole
family of silent failures into loud ones — wrong placement stops compiling at the exact
line that is wrong.

### 3. How does it work?

One line on the declaration: `@Target(ElementType.METHOD)`. `ElementType` is a fixed list
of place-kinds:

| `ElementType` | Allows the annotation on |
|---|---|
| `METHOD` | methods |
| `TYPE` | classes, interfaces, enums, records |
| `FIELD` | fields |
| `PARAMETER` | method parameters |
| `CONSTRUCTOR` | constructors |

Several places at once take curly braces:
`@Target({ElementType.METHOD, ElementType.TYPE})`. No `@Target` at all means "allowed
almost everywhere" — not generous, just unguarded.

### 4. Simple example

Files in `lessons/l4-target/`. The finished declaration — character for character what
subtask S2 asks for:

```java
// Timed.java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed { }
```

A legal use, on a method:

```java
// Good.java
import java.lang.reflect.Method;
import java.util.Arrays;

public class Good {

    @Timed
    static void work() {
        System.out.println("Work done.");
    }

    public static void main(String[] args) throws Exception {
        work();
        Method m = Good.class.getDeclaredMethod("work");
        System.out.println("Labels visible at runtime: " + Arrays.toString(m.getAnnotations()));
    }
}
```

Real output:

```text
Work done.
Labels visible at runtime: [@Timed()]
```

An illegal use, on a field:

```java
// Bad.java
public class Bad {

    @Timed
    static int counter = 0;

    public static void main(String[] args) {
        System.out.println(counter);
    }
}
```

Real compiler output:

```text
Bad.java:5: error: annotation interface not applicable to this kind of declaration
    @Timed
    ^
1 error
```

### 5. Walkthrough

Compiling `Good.java`: the compiler meets `@Timed` above `work()`, reads the declaration's
`@Target` list (`METHOD`), sees that `work()` is a method, and continues. At runtime the
printout shows `[@Timed()]` — the `@Retention(RUNTIME)` from Lesson 3 still doing its job.
The two lines divide the work: `@Target` guards *where*, `@Retention` guards *how long*.

Compiling `Bad.java`: the compiler meets `@Timed` above the field `counter`, reads the
list (`METHOD`), finds fields not in it, and stops with
`annotation interface not applicable to this kind of declaration`. Decoded: "this label is
not allowed on this kind of thing." The error points at the exact line. Nothing runs;
nobody waits for timings that never come.

### 6. Where is it used?

| Annotation | `@Target` | Makes sense because |
|---|---|---|
| `@Override` | `METHOD` | Only methods override |
| `@Entity` (JPA) | `TYPE` | Only a class can map to a table |
| `@PathVariable` (Spring) | `PARAMETER` | It binds one method parameter to a piece of the URL |
| `@Autowired` (Spring) | constructor, method, field, parameter | Injection has several legal shapes |
| `@Timed` (ours) | `METHOD` | We time method calls |
| `@Idempotent` (project Phase 5) | `METHOD` | It guards one handler method per request |

### 7. Common mistakes

**Mistake 1 — omitting `@Target`.** Everything compiles everywhere. Someone puts the
annotation on a class while the reader machinery only inspects methods. Compiles fine,
does nothing — silent again. Habit: always declare the narrowest target that covers the
real use.

**Mistake 2 — misreading `TYPE`.** `ElementType.TYPE` does not mean "any type of thing".
It means type *declarations*: classes, interfaces, enums, records.

**Mistake 3 — forgetting braces for several targets.**
`@Target(ElementType.METHOD, ElementType.TYPE)` does not compile. The list form needs
braces: `@Target({ElementType.METHOD, ElementType.TYPE})`.

### 8. Link to the task

Subtask S2 asks for
`@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface Timed { }`.
Every character is now owned: `@interface` declares the label (Lesson 2), `RUNTIME` keeps
it visible to the running program (Lesson 3), `METHOD` makes wrong placement a loud
compile error (this lesson). The `spring-boot-starter-aop` half of S2 lands in Lesson 8,
when there is an app to add it to.

### 9. Check my understanding

**Q1.** Here is a complete program, two files.

File `Flag.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
public @interface Flag { }
```

File `App.java`:

```java
public class App {

    @Flag
    static void run() {
        System.out.println("Running.");
    }

    public static void main(String[] args) {
        run();
    }
}
```

You run `javac *.java`. What happens — and at which moment: while compiling, or while
running? Roughly what will the message say?

**Answer.** It fails while **compiling**. Nothing ever runs. `Flag` declares
`@Target(ElementType.FIELD)`, so the label may only sit on fields — and `App.java`
attaches it to the method `run()`. The compiler stops at that line with:

```text
App.java:3: error: annotation interface not applicable to this kind of declaration
    @Flag
    ^
1 error
```

Decoded: "this label is not allowed on this kind of thing." Because compilation fails, no
`App.class` is produced, so there is nothing to run.

**Q2.** You are designing `@Idempotent` for a project. The machinery that reads it only
ever inspects *methods*. A teammate says: "Leave `@Target` off, so the annotation stays
flexible — people can put it wherever they find useful." Give the argument against, using
the difference between a loud failure and a silent one.

**Answer.** Flexibility is only real where a reader exists. The reader inspects methods
and nothing else. With `@Target` left off, `@Idempotent` on a class or a field *compiles*
— and then silently does nothing, because no machinery ever looks there. The person who
wrote it believes their endpoint is protected against duplicate requests. It is not. That
is a silent failure in exactly the place where silence is most expensive. With
`@Target(ElementType.METHOD)`, every wrong placement fails loudly at compile time, at the
exact line, before the code can even run. Every placement the reader ignores should be
illegal to write.

### Do it yourself (second laptop)

```bash
mkdir -p ~/foundations/F6.12-aop/lesson4
cd ~/foundations/F6.12-aop/lesson4
```

Create `Timed.java` and `Good.java` exactly as in section 4 of this lesson. Then:

```bash
javac Timed.java Good.java && java Good
```

Expected:

```text
Work done.
Labels visible at runtime: [@Timed()]
```

Now create `Bad.java` exactly as in section 4 and compile it:

```bash
javac Bad.java
```

Expected — a compile error, not a run-time surprise:

```text
Bad.java:5: error: annotation interface not applicable to this kind of declaration
    @Timed
    ^
1 error
```

Delete `Bad.java` afterwards so `javac *.java` works again in that folder.

**Done when:** both results match, and you can say in one sentence what `@Target` buys you
(wrong placement fails loudly at compile time instead of doing nothing at runtime).

### Summary

- `@Target` lists the only places an annotation may be written; anywhere else is a compile
  error at the offending line.
- Common kinds: `METHOD`, `TYPE` (class/interface/enum/record declarations), `FIELD`,
  `PARAMETER`, `CONSTRUCTOR`; several at once need braces.
- No `@Target` means "almost anywhere" — which invites placements that no reader will ever
  look at: silent failures.
- Rule: declare the narrowest target the real use needs. For `@Timed` and `@Idempotent`,
  that is `METHOD`.
- The full declaration is now understood:
  `@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface Timed { }`.

---

## Lesson 5 — What AOP is

### 1. What is it?

AOP stands for Aspect-Oriented Programming. It is a way to run extra code **around**
chosen methods, without writing that code inside the methods. The methods stay clean. The
extra work happens anyway.

### 2. Why do we need it?

After four lessons, methods can be marked: `@Timed` is declared, survives until runtime,
and may only sit on methods. But a mark is just a sticker; nothing reads it yet. The
options for actually doing the timing:

- **Option A — inside every method.** That was Lesson 1: nine copied lines, silent gaps
  when someone forgets. Rejected.
- **Option B — at every call site.** Wrap every *call* with clock code. The duplication
  moves from three methods to every caller — usually far more callers than methods. Worse.
- **Option C — the dream.** State the rule once: "any method marked `@Timed` gets timing
  around it." It applies everywhere, automatically. New annotated method next year:
  covered, zero extra lines. Format change: one edit.

AOP is Option C made real.

### 3. How does it work?

Three parts must exist:

1. **The mark.** `@Timed` on chosen methods (Lessons 2–4; done).
2. **The around-code, written once.** One separate class holds the clock-keeping: note the
   time, let the real method run, note the time again, print. That class is called an
   **aspect** (written in Lesson 8).
3. **A matchmaker.** Something connects mark to around-code while the app runs: Spring. At
   startup it checks bean methods — with exactly the runtime-visibility check from the
   `@Retention` lesson — and arranges that calls to marked methods pass through the
   around-code.

One call then flows like this:

```text
caller ──► [ around-code: start clock ]
               │
               ▼
           real method runs
               │
               ▼
           [ around-code: stop clock, print ]
caller ◄── result comes back
```

Key sentence: **the method's file never changes.** The extra behaviour is added around it,
from outside.

### 4. Simple example

The *target state* — `BookingService` as it should look when the task is finished
(`lessons/l5-what-aop-is/`). Compared with Lesson 1, the nine timing lines are gone:

```java
public class BookingService {

    @Timed
    void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }

    @Timed
    void cancelBooking() throws InterruptedException {
        Thread.sleep(80);
        System.out.println("Booking cancelled.");
    }

    @Timed
    void listBookings() throws InterruptedException {
        Thread.sleep(40);
        System.out.println("Bookings: [A1, B4, C2]");
    }

    public static void main(String[] args) throws InterruptedException {
        BookingService service = new BookingService();
        service.reserveSeat();
        service.cancelBooking();
        service.listBookings();
    }
}
```

Real output:

```text
Seat reserved.
Booking cancelled.
Bookings: [A1, B4, C2]
```

Clean methods and — honestly — no timing. Of the three parts, only the mark exists in this
plain-Java program. No around-code, no matchmaker. Lesson 8 restores the timing without
changing these methods.

### 5. Walkthrough

What *will* happen to one call once all three parts exist:

1. `main` calls `service.reserveSeat()`.
2. The call does not land in the method body directly. It lands in the around-code first,
   which reads the clock: say, 1,000,000 nanoseconds.
3. The around-code lets the real method run. It sleeps, prints `Seat reserved.`, knows
   nothing about clocks.
4. The real method finishes; control returns to the around-code.
5. The around-code reads the clock again, subtracts, converts, prints
   `reserveSeat took 120 ms`.
6. The caller gets the result, exactly as if it had called the method directly.

Steps 2, 4, 5 live in one file, written once, serving every marked method in the
application.

### 6. Where is it used?

| You write | Around-code that runs |
|---|---|
| `@Transactional` | Open a transaction before; commit after; roll back on failure |
| `@Cacheable` | Before: asked already? Return the saved answer, skip the method. After: save the answer |
| `@Async` | Run the method on another thread; return control to the caller immediately |
| `@Timed` (ours, Lesson 8) | Clock before, clock after, print |
| `@Idempotent` (project Phase 5) | Before: seen this request key? Return the stored response, skip the method |
| Audit aspect (project Phase 2) | After: record who called what, and when |

One idea, many costumes — why this hour explains six Spring features at once.

### 7. Common mistakes

**Mistake 1 — believing the annotation does the work.** It never does. `@Transactional`
does not open transactions; Spring's around-code does, when it sees the mark. If the
around-code is missing or blind (`@Retention`), the mark is just ink.

**Mistake 2 — thinking AOP rewrites source files.** It does not touch them. The
interception is arranged at runtime, around the object (how — Lesson 6).

**Mistake 3 — stuffing business logic into aspects.** Aspects are for cross-cutting
concerns only. "Orders above 1000 get a discount" *is* what an order method is about; hide
it in an aspect and nobody will find it. One-sentence test: if the code belongs in the
method's one-sentence description, it belongs in the method.

### 8. Link to the task

The task title — "AOP and writing your own annotations" — is this lesson plus the four
before it. Subtask S3 ends: "**delete all three duplicated blocks** and annotate those
methods instead." Section 4 is a preview of exactly that deletion; the promise is Lesson
1's output with none of Lesson 1's duplication.

### 9. Check my understanding

**Q1.** Here is a complete method inside a Spring application:

```java
@Timed
public void checkout() {
    System.out.println("Checkout complete.");
}
```

When the app runs and `checkout()` is called, the console shows:

```text
Checkout complete.
checkout took 3 ms
```

The method body contains no clock code, yet a timing line appears. Name the three parts
that must exist somewhere in this application for that second line to print, and say in
one short sentence what each part does.

**Answer.**

1. **The mark** — the `@Timed` annotation on `checkout()`, declared with
   `@Retention(RetentionPolicy.RUNTIME)` so it is visible while the app runs. It says
   "this method wants timing."
2. **The around-code (the aspect)** — one class, written once, that reads the clock, lets
   the real method run, reads the clock again, and prints the difference.
3. **The matchmaker (Spring)** — at startup it finds methods carrying the mark and routes
   every call to them through the around-code.

Remove any one of the three and the second line disappears — silently.

**Q2.** A teammate says: "Our `cancelOrder` method must refuse to cancel orders that
already shipped. I will put that refusal check into an aspect, so `cancelOrder` stays
short." Is this a good use of AOP? Decide using this test: say in one sentence what the
method is for, then ask whether the check is part of that sentence.

**Answer.** Bad use. The one-sentence description is: "cancelOrder cancels an order —
unless it has already shipped." The refusal is part of the sentence. It is a business rule
of cancelling, not off-topic plumbing that repeats identically across unrelated methods.
Moving it into an aspect hides a core rule where no reader of `cancelOrder` will ever look;
the method would lie about its own behaviour. Business rules stay in the method. Aspects
are only for cross-cutting concerns — timing, logging, auditing, transactions — code that
fails the one-sentence test.

### Do it yourself (second laptop)

```bash
mkdir -p ~/foundations/F6.12-aop/lesson5
cd ~/foundations/F6.12-aop/lesson5
```

Create `Timed.java` (the finished declaration, same as lesson 4):

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed { }
```

Create `BookingService.java` exactly as in section 4 of this lesson. Then:

```bash
javac *.java && java BookingService
```

Expected output:

```text
Seat reserved.
Booking cancelled.
Bookings: [A1, B4, C2]
```

No timing lines — correct. **Done when:** you can name, out loud, which of the three AOP
parts this program has (the mark) and which two are missing (the around-code, and the
matchmaker that connects them at runtime).

### Summary

- AOP runs extra code around chosen methods without putting that code inside them.
- Three parts: the mark (`@Timed`), the around-code written once (the aspect), and the
  matchmaker that connects them at runtime (Spring).
- The method's source file never changes; behaviour is added from outside.
- `@Transactional`, `@Cacheable`, `@Async`, `@Idempotent`, audit trails: all the same
  pattern in different costumes.
- Aspects carry cross-cutting concerns only; business rules stay in their methods
  (one-sentence test).

---

## Lesson 6 — The proxy: how Spring runs code around your methods

### 1. What is it?

A proxy is a stand-in object. It has the same shape as your real object — the same methods
— so callers cannot tell the difference. When called, it does some extra work, then
forwards the call to the real object. Spring's trick in one sentence: **everyone who asks
for your bean secretly receives a proxy instead.**

### 2. Why do we need it?

Lesson 5 left a mystery: a call "lands in the around-code first". But in plain Java,
`service.reserveSeat()` jumps straight into the method body — there is no gap between
caller and method for anyone to step into.

Unless the object being called is not the real object. That is the whole trick. You cannot
step into the middle of a call — but you can hand the caller a *different object*: one
that looks identical, does the extra work, and passes the call along. The caller dials the
same number; a secretary picks up first.

### 3. How does it work?

At startup, for each bean that needs around-code, Spring:

1. Builds the real bean, as always.
2. Notices: some method of this bean is marked (for us: `@Timed`).
3. Builds a second object with the same shape — the proxy — holding the real bean inside.
4. Hands **the proxy** to everyone who asks for the bean (constructor injection, F6.2).

Every call then flows: caller → proxy → extra work → real method → extra work → caller.

The design has a classic name: the **Decorator pattern** — an object that wraps another
object of the same shape, adds behaviour around its calls, and forwards the real work.
Spring AOP is the Decorator pattern with Spring writing the decorator for you. (That is
checkpoint question 5's answer.)

### 4. Simple example

No Spring — the proxy built by hand, so no magic remains. Files in
`lessons/l6-the-proxy/`.

```java
// Booking.java — the shape both objects share
public interface Booking {
    void reserveSeat() throws InterruptedException;
}
```

```java
// RealBookingService.java — the real object, clean, no clock code
public class RealBookingService implements Booking {

    public void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }
}
```

```java
// TimingProxy.java — same shape, real object inside, extra work around the forward
public class TimingProxy implements Booking {

    private final Booking real;

    public TimingProxy(Booking real) {
        this.real = real;
    }

    public void reserveSeat() throws InterruptedException {
        long start = System.nanoTime();              // extra work, before

        real.reserveSeat();                          // forward to the real object

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("reserveSeat took " + elapsedMs + " ms");  // extra work, after
    }
}
```

```java
// Main.java — the caller receives the stand-in and cannot tell
public class Main {

    public static void main(String[] args) throws InterruptedException {
        Booking service = new TimingProxy(new RealBookingService());
        service.reserveSeat();   // the caller thinks it talks to a booking service
    }
}
```

Real output:

```text
Seat reserved.
reserveSeat took 125 ms
```

The real method contains **zero** clock code — and the timing printed anyway. AOP's
engine, hand-made, in thirty lines.

### 5. Walkthrough

1. `new RealBookingService()` — the real object exists. Call it R.
2. `new TimingProxy(R)` — the stand-in exists, holding R inside. Call it P. Two objects
   now live, not one.
3. `Booking service = P` — the caller's variable has the *interface* type and points at P.
   The caller has no idea two objects exist.
4. `service.reserveSeat()` lands on **P** — that is the object in the variable.
5. P reads the clock, then forwards: `real.reserveSeat()`.
6. R sleeps, prints `Seat reserved.` R knows nothing about any proxy.
7. Control returns to P. P reads the clock again, prints `reserveSeat took 125 ms`.
8. `main` continues, unaware of the whole arrangement.

The flip side — the leak that becomes Spring's most famous bug. Fifth file:

```java
// DirectCall.java — the proxy exists but the call bypasses it
public class DirectCall {

    public static void main(String[] args) throws InterruptedException {
        RealBookingService real = new RealBookingService();
        Booking service = new TimingProxy(real);   // proxy exists, but...

        real.reserveSeat();                        // ...the call goes straight to the real object
    }
}
```

Real output:

```text
Seat reserved.
```

No timing. No error either. The proxy sat there, fully built, never consulted. **The extra
work only happens on calls that pass through the stand-in.** Lesson 11 shows the same leak
inside a Spring bean, where it is much harder to see.

### 6. Where is it used?

| Place | The stand-in story |
|---|---|
| Spring AOP, `@Transactional`, `@Cacheable`, `@Async` | Callers of a bean receive a proxy that wraps the bean |
| Hibernate lazy loading (task F6.11) | A lazy relationship field holds a stand-in that fetches data on first touch — same pattern, different goal |
| `BufferedReader` wrapping a `FileReader` | Decorator by hand in the JDK: same shape, adds buffering around reads |

### 7. Common mistakes

**Mistake 1 — thinking the proxy *is* your object.** It is a second, different object.
`service == real` would be false. Lesson 13 prints the proxy's class name in a real Spring
app and finds `$$SpringCGLIB$$` in it.

**Mistake 2 — thinking Spring edits your class.** Nothing rewrites code. The class
compiles once and stays as written; Spring builds a wrapper *object* around an *instance*.

**Mistake 3 — expecting extra work on every call, through any path.** Only calls that
travel through the proxy get the extra work. `DirectCall` proved it: direct access to the
real object, and the timing silently vanished.

**Mistake 4 — "why not hand-write proxies everywhere?"** It does not scale: dozens of
methods per service, hundreds of beans, one forwarding method each — the same duplication
disease AOP exists to cure. Spring *generates* the proxies at startup instead.

### 8. Link to the task

The task says: "**What actually matters here: the proxy.** Understanding that Spring hands
callers a stand-in object explains AOP, `@Transactional`, `@Cacheable`, `@Async` and the
self-invocation bug all at once." The stand-in is now hand-built, and its bypass
(`DirectCall`) is Subtask S6 in miniature. Checkpoint question 5 is answered: the
Decorator pattern.

### 9. Check my understanding

**Q1.** Here is a complete program, four files:

```java
public interface Greeter {
    void greet();
}

public class RealGreeter implements Greeter {
    public void greet() {
        System.out.println("Hello!");
    }
}

public class LoggingProxy implements Greeter {

    private final Greeter real;

    public LoggingProxy(Greeter real) {
        this.real = real;
    }

    public void greet() {
        System.out.println("before");
        real.greet();
        System.out.println("after");
    }
}

public class Main {
    public static void main(String[] args) {
        Greeter g = new LoggingProxy(new RealGreeter());
        g.greet();
        g.greet();
    }
}
```

(a) Write out exactly what this prints, line by line. (b) How many `Greeter` objects exist
while this runs?

**Answer.** (a)

```text
before
Hello!
after
before
Hello!
after
```

Each `g.greet()` lands on the proxy: it prints `before`, forwards to the real object
(which prints `Hello!`), then prints `after`. Two calls, so the trio appears twice.
(b) Two objects: one `RealGreeter` and one `LoggingProxy` wrapping it. One variable, two
objects.

**Q2.** Same four files as in Q1, but `Main` is changed to this:

```java
public class Main {
    public static void main(String[] args) {
        RealGreeter real = new RealGreeter();
        Greeter g = new LoggingProxy(real);

        real.greet();
    }
}
```

What does this print now? Explain, in terms of which object the call lands on, why the
output changed even though the proxy still exists.

**Answer.**

```text
Hello!
```

The call `real.greet()` lands directly on the `RealGreeter` object. The `before` and
`after` lines live in the proxy's `greet` method, and that method never runs — the
variable `g` holding the proxy is never used. A proxy only adds its extra work to calls
that pass **through** it. Existing is not enough; the call path decides everything. This
is exactly the mechanism behind Spring's self-invocation bug (Lesson 11).

### Do it yourself (second laptop)

```bash
mkdir -p ~/foundations/F6.12-aop/lesson6
cd ~/foundations/F6.12-aop/lesson6
```

Create the five files exactly as in sections 4 and 5: `Booking.java`,
`RealBookingService.java`, `TimingProxy.java`, `Main.java`, `DirectCall.java`. Then:

```bash
javac *.java
java Main
```

Expected (milliseconds vary slightly):

```text
Seat reserved.
reserveSeat took 125 ms
```

```bash
java DirectCall
```

Expected:

```text
Seat reserved.
```

**Done when:** you can say, in your own words: where does the timing code live, and why
did `java DirectCall` print no timing? (The timing lives in the stand-in; only calls that
pass through the stand-in get timed; `DirectCall` called the real object directly.)

### Summary

- A proxy is a stand-in object with the same shape as the real one; it does extra work,
  then forwards the call. Spring hands callers the proxy instead of the bean.
- Hand-built in four small files: interface, real class, wrapping class, caller. Timing
  printed with zero clock code in the real method.
- Only calls that pass through the proxy get the extra work; a direct call to the real
  object silently gets nothing (`DirectCall`).
- Spring AOP = the Decorator pattern, generated for you at startup.
- Hibernate's lazy-loading stand-ins (F6.11) are the same pattern with a different goal.

---

## Lesson 7 — The four AOP words

### 1. What is it?

Four names for the four parts of AOP: **aspect**, **advice**, **join point**, **pointcut**.
Nothing new happens in this lesson — the machine is already understood. This lesson names
its parts.

### 2. Why do we need it?

The aspect code is literally assembled from these words: `@Aspect`, `@Around`,
`ProceedingJoinPoint`, a pointcut expression. Fuzzy words make that code runes; sharp words
make it read like a sentence. Spring's documentation and error messages speak these words
too.

### 3. How does it work?

**Aspect** — the *class that holds the extra code*, written once for the whole app. In
Spring: marked `@Aspect @Component`. The aspect is **not** the proxy: the aspect is your
class with the interesting code; the proxy is the generated stand-in that routes calls into
it. The hand-built `TimingProxy` of Lesson 6 played both roles at once; Spring splits them
— you write the aspect, Spring generates the proxy.

**Advice** — the *method inside the aspect* holding the extra code, plus *when* it wraps.
`@Around` wraps the whole call: code before, code after, control over the middle. Other
kinds exist (`@Before`, `@After`), but `@Around` can do everything they can.

**Join point** — *one place where advice could run*: in Spring, one method call on a bean,
arriving through its proxy.

**Pointcut** — *the rule selecting which join points actually get the advice*. Ours, in
words: "any method annotated with `@Timed`"; as an expression:
`@annotation(com.zenith.common.Timed)`.

Picture: a building full of doors. Every door is a **join point**. "Guard only doors with
the red sticker" is the **pointcut**. What the guard does at a guarded door is the
**advice**. The security office employing the guard is the **aspect**.

### 4. Simple example

The real aspect from the scratch app (it compiles on this machine; Lesson 8 runs it):

```java
package com.zenith.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {

    @Around("@annotation(com.zenith.common.Timed)")
    public Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();

        Object result = joinPoint.proceed();   // run the real method

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println(joinPoint.getSignature().getName() + " took " + elapsedMs + " ms");
        return result;
    }
}
```

The parts, labeled:

| Code | Which word | Meaning |
|---|---|---|
| `class TimingAspect` with `@Aspect @Component` | the **aspect** | The class holding the extra code; a bean |
| the `time(...)` method | the **advice** | The extra code itself |
| `@Around` | the advice's *kind* | Wraps the whole call |
| `"@annotation(com.zenith.common.Timed)"` | the **pointcut** | Selects: methods marked `@Timed` |
| `ProceedingJoinPoint joinPoint` | the **join point**, in hand | The wrapped call as an object, with a "proceed" button |

### 5. Walkthrough

Read as a sentence: "This class is an aspect. Around every method selected by *marked with
`com.zenith.common.Timed`*, run this advice: read the clock, let the call proceed, read the
clock again, print the method's name and the difference, hand back the result."

Mapped onto Lesson 6's hand-built proxy:

```java
public void reserveSeat() throws InterruptedException {
    long start = System.nanoTime();              // advice, first half
    real.reserveSeat();                          // = joinPoint.proceed()
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    System.out.println("reserveSeat took " + elapsedMs + " ms");  // advice, second half
}
```

- The clock lines are the **advice** — in Spring they move into the aspect.
- `real.reserveSeat()` is what `joinPoint.proceed()` does: forward to the real method.
- The **pointcut** was hardcoded in the hand version: this proxy wraps `reserveSeat` and
  nothing else. The expression replaces that hardcoding with a rule.
- The `TimingProxy` object itself is what Spring *generates* — never hand-written again.

One future call in the four words: the app calls `bookingService.reserveSeat()` — a **join
point**. The **pointcut** checks: marked `@Timed`? Yes. The **advice** runs around it. The
**aspect** is where the advice lives.

### 6. Where is it used?

| Feature | Its pointcut, in words | Its advice, in words |
|---|---|---|
| `@Transactional` | methods marked `@Transactional` | open transaction → proceed → commit, or roll back |
| `@Cacheable` | methods marked `@Cacheable` | check cache → maybe skip proceed and return saved answer → else proceed and save |
| `@Timed` (ours) | methods marked `@Timed` | clock → proceed → clock → print |

The words come from AspectJ, whose machinery Spring borrows — hence the `org.aspectj`
imports.

### 7. Common mistakes

**Mistake 1 — swapping join point and pointcut.** A join point is a *place*: one method
call. A pointcut is the *rule* that picks places. Many join points, one pointcut.

**Mistake 2 — thinking the aspect is the proxy.** Two different things at runtime: the
aspect is one bean you wrote holding the advice; proxies are generated stand-ins, one per
wrapped bean, that call into it.

**Mistake 3 — learning all five advice kinds at once.** `@Around` does everything the
others do and is the only one this task and the project need.

### 8. Link to the task

Subtask S3 says: "Write the aspect — `@Aspect @Component`, with `@Around` taking a
`ProceedingJoinPoint` and timing `joinPoint.proceed()`." Every term in that sentence now
has a face. The task's new-words table defines join point and pointcut exactly as here.

### 9. Check my understanding

**Q1.** Here is a complete aspect from a Spring app:

```java
@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(com.shop.audit.Audited)")
    public Object record(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("call started");
        Object result = joinPoint.proceed();
        System.out.println("call finished");
        return result;
    }
}
```

Elsewhere in the app there is a bean `OrderService` whose method `placeOrder()` is marked
`@Audited`, and at startup the app calls `placeOrder()` once. Point at: (a) the aspect,
(b) the advice, (c) the pointcut and what it selects, (d) one concrete join point during
this run.

**Answer.** (a) The class `AuditAspect` — marked `@Aspect @Component`, it holds the extra
code. (b) The method `record(...)` — the extra code that runs around selected calls.
(c) The expression `@annotation(com.shop.audit.Audited)` inside `@Around` — it selects
method calls whose method carries the `@Audited` annotation. (d) The one call to
`placeOrder()` at startup — a method call on a bean, and because `placeOrder()` is marked
`@Audited`, the pointcut selects it and the advice runs around it.

**Q2.** A Spring app contains exactly one aspect class, holding exactly one `@Around`
advice whose pointcut is "methods marked `@Timed`". The app has two beans.
`PaymentService` has methods `pay()` and `refund()`; `ReportService` has methods
`daily()`, `weekly()`, `monthly()`. Only `pay()` and `daily()` are marked `@Timed`. During
one run: `pay()` is called 3 times, `refund()` 2 times, `daily()` once, `weekly()` 4
times. (a) How many aspect classes exist? (b) How many advice methods? (c) Which calls
pass through the advice, and how many advised calls happen in total?

**Answer.** (a) One — the single aspect class serves the entire application. (b) One — the
single `@Around` method. (c) Only calls to methods the pointcut selects: the 3 calls to
`pay()` and the 1 call to `daily()`, so **4 advised calls** in total. The 2 `refund()`
calls and 4 `weekly()` calls are join points too, but the pointcut does not select them,
so they run plain. `monthly()` is never called, so no join point for it ever exists. One
aspect, one advice, one pointcut — many join points.

### Do it yourself (second laptop)

No new commands — a two-minute pencil exercise. Open `lesson6/TimingProxy.java` from the
previous lesson and add three comments: mark the clock lines with `// advice`, mark
`real.reserveSeat();` with `// = proceed()`, and at the top of the class write the
hand-made pointcut as a comment: `// pointcut, hardcoded: only Booking.reserveSeat`.
**Done when:** the file reads like the labeled table in section 4.

### Summary

- **Aspect**: the class holding the extra code (`@Aspect @Component`). **Advice**: the
  method with that code; `@Around` wraps the whole call. **Join point**: one method call
  on a bean — a place advice *could* run. **Pointcut**: the rule choosing which join
  points actually get the advice.
- The aspect is your code; the proxy is generated plumbing that routes calls into it.
- Hand-built mapping: clock lines = advice; `real.method()` = `proceed()`; the hardcoded
  "wraps only this method" = the pointcut; the wrapper class itself = what Spring
  generates.
- `@Transactional` and `@Cacheable` are describable in the same four words — same machine.

---

## Lesson 8 — Writing the aspect, and watching it fire (subtasks S2 + S3)

### 1. What is it?

The assembly lesson: the dependency (`spring-boot-starter-aop`), the `@Timed` mark, and
the aspect — the one class holding the around-code — put together and **run**, then broken
twice on purpose to learn the two silent failure modes.

### 2. Why do we need it?

Every part was ready: the mark (Lessons 2–4), the idea (Lesson 5), the mechanism (Lesson
6), the words (Lesson 7). What remained was assembly — and proof. Proof matters most: this
machinery fails *silently*, so the habit is to demand evidence that it fired.

### 3. How does it work?

**The dependency** — one block in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**The anatomy of the aspect file:**

- `@Aspect` — this class contains advice.
- `@Component` — makes it a bean. Both needed: `@Aspect` without `@Component` means the
  object is never created, and the advice silently does not exist.
- `@Around("...")` — the advice kind (wraps the whole call) plus the pointcut string.
- `public Object time(ProceedingJoinPoint joinPoint) throws Throwable` — returns `Object`
  to hand back whatever the real method returns; declares `throws Throwable` to let any
  failure pass through.
- `joinPoint.proceed()` — run the real method now; its return value is the method's
  return value.
- `return result` — give that value back. Forget this and wrapped methods that return
  something start returning `null`.

### 4. Simple example

The scratch app: `F6.12-aop-and-writing-your-own-annotations/scratch-app`, package
`com.zenith.aop`, Spring Boot 3.5.16, Java 21. The annotation deliberately lives in a
*different* package, `com.zenith.common`, like a real project with a shared common module
— and that placement is what makes break experiment B honest.

`pom.xml`:

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
    <artifactId>aop-scratch</artifactId>
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
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

```java
// src/main/java/com/zenith/common/Timed.java
package com.zenith.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed { }
```

```java
// src/main/java/com/zenith/aop/AopApplication.java
package com.zenith.aop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AopApplication {

    public static void main(String[] args) {
        SpringApplication.run(AopApplication.class, args);
    }
}
```

```java
// src/main/java/com/zenith/aop/BookingService.java  (subtask S3's deletion, done)
package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

@Component
public class BookingService {

    @Timed
    public void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }

    @Timed
    public void cancelBooking() throws InterruptedException {
        Thread.sleep(80);
        System.out.println("Booking cancelled.");
    }

    @Timed
    public void listBookings() throws InterruptedException {
        Thread.sleep(40);
        System.out.println("Bookings: [A1, B4, C2]");
    }
}
```

```java
// src/main/java/com/zenith/aop/TimingAspect.java
package com.zenith.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {

    // Pointcut: methods annotated with @Timed - ALWAYS the fully qualified name.
    @Around("@annotation(com.zenith.common.Timed)")
    public Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();

        Object result = joinPoint.proceed();   // run the real method

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println(joinPoint.getSignature().getName() + " took " + elapsedMs + " ms");
        return result;
    }
}
```

```java
// src/main/java/com/zenith/aop/L8TimedDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l8-timed")
public class L8TimedDemo implements CommandLineRunner {

    private final BookingService bookingService;

    public L8TimedDemo(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public void run(String... args) throws Exception {
        bookingService.reserveSeat();
        bookingService.cancelBooking();
        bookingService.listBookings();
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-timed`

Real output (startup lines trimmed):

```text
Started AopApplication in 0.343 seconds (process running for 0.449)
Seat reserved.
reserveSeat took 120 ms
Booking cancelled.
cancelBooking took 83 ms
Bookings: [A1, B4, C2]
listBookings took 43 ms
```

Lesson 1's output, from methods containing **zero** clock code. The timing lines are the
proof the aspect fired.

### 5. Walkthrough

One call through the machine: Spring starts, sees `BookingService` has methods matching
the pointcut, wraps it in a generated proxy. `L8TimedDemo` asked for `BookingService` in
its constructor — it received the proxy. `bookingService.reserveSeat()` lands on the
proxy → the aspect's `time(...)` → clock → `proceed()` runs the real method
(`Seat reserved.`) → clock again → `reserveSeat took 120 ms` → result flows back.

**Break A — remove `@Retention(RUNTIME)`** (the default, `CLASS`, applies). The task
predicted: aspect never fires. The machine said otherwise — timings still printed:

```text
Seat reserved.
reserveSeat took 125 ms
...
```

`javap` confirmed the `@Timed` marks really were on the invisible shelf
(`RuntimeInvisibleAnnotations`), unreadable by reflection. The aspect fired anyway because
Spring's matcher (AspectJ) does not use the reflection view for *matching*: it reads the
class **file**, where both shelves are visible. On Spring Boot 3.5.16, pointcut matching
survives wrong retention.

Is `RUNTIME` pointless then? No. Three reasons the rule stands: (1) reading the
annotation's *value* inside the aspect uses reflection, which sees only the visible shelf
— with `CLASS` retention that read returns nothing (proven in Lesson 9 with a real crash);
(2) every other reader — own code, tests, most libraries — uses reflection; (3) the
bytecode-reading behaviour is an internal detail that has varied across versions. Write
`RUNTIME` every time — but trust experiments over lore.

**Break B — the bare pointcut name:**

```java
@Around("@annotation(Timed)")   // bare name instead of com.zenith.common.Timed
```

Compiles fine — the pointcut is just a string. The run:

```text
Started AopApplication in 0.314 seconds (process running for 0.419)
Seat reserved.
Booking cancelled.
Bookings: [A1, B4, C2]
```

App clean, methods ran, **no timing lines, no error, no warning**. A bare name is resolved
only against `java.lang` and the aspect's *own package* (`com.zenith.aop`); `Timed` lives
in `com.zenith.common`, so the pointcut selects nothing — and selecting nothing is a legal
outcome Spring has no reason to report. Restoring the fully qualified name brought the
timings back.

The habit this teaches: **after writing an aspect, prove it fired before doing anything
else.** No proof line = no aspect, whatever the startup log says.

### 6. Where is it used?

This exact file shape — `@Aspect @Component`, one `@Around`, an `@annotation` pointcut —
is the project's Phase 2 audit aspect and Phase 5 `@Idempotent` interceptor, with
different advice bodies. A production-grade `@Timed` exists in the Micrometer metrics
library; this is the teaching version of the same idea.

### 7. Common mistakes

1. **The bare pointcut name** — seen live; always fully qualified.
2. **`@Aspect` without `@Component`** — no bean, no advice, identical silence.
3. **Forgetting `return result`** — invisible with `void` methods; the day a wrapped
   method returns a `String`, every caller silently receives `null`.
4. **Narrowing `throws Throwable` / swallowing exceptions in advice** — turns real
   failures into silence.
5. **Trusting lore over the machine** — the retention experiment predicted one thing and
   did another; run the experiment, believe the output, then follow the rule for the
   reasons that do hold.

### 8. Link to the task

**S2 complete:** dependency added, `@Timed` declared with `RUNTIME` + `METHOD`,
default-retention experiment run (with a more interesting result than advertised).
**S3 complete:** aspect written (`@Around`, `ProceedingJoinPoint`), fully qualified
pointcut, firing proven by the log line, bare-name silence witnessed, three duplicated
blocks deleted — `BookingService` contains only its real work.

### 9. Check my understanding

**Q1.** A Spring Boot app contains this annotation and this aspect, in the packages shown:

```java
// file: src/main/java/com/shop/annotations/Logged.java
package com.shop.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Logged { }
```

```java
// file: src/main/java/com/shop/web/LogAspect.java
package com.shop.web;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAspect {

    @Around("@annotation(Logged)")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("calling " + joinPoint.getSignature().getName());
        return joinPoint.proceed();
    }
}
```

A bean method marked `@Logged` runs, but nothing is ever printed. The app started with no
errors. (a) What is wrong? (b) Why is there no error anywhere? (c) What is the one-line
fix?

**Answer.** (a) The pointcut uses the bare name `Logged`. A bare name is resolved only
against `java.lang` and the aspect's own package — here `com.shop.web`. The annotation
lives in `com.shop.annotations`, so the name resolves to nothing and the pointcut selects
nothing. (b) Because "this pointcut selects no methods" is a perfectly legal situation —
maybe no method wanted logging. The pointcut string is checked for *syntax*, not for "did
you mean"; an empty match produces no exception, no warning, no log line. (c) Use the
fully qualified name:

```java
@Around("@annotation(com.shop.annotations.Logged)")
```

**Q2.** Here is a complete aspect and a complete bean in one Spring app. The annotation
`com.acme.Traced` is declared correctly with `RUNTIME` retention and `METHOD` target.

```java
@Aspect
@Component
public class TraceAspect {

    @Around("@annotation(com.acme.Traced)")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("IN");
        Object result = joinPoint.proceed();
        System.out.println("OUT");
        return result;
    }
}

@Component
public class GreetingService {

    @Traced
    public String greet() {
        System.out.println("building greeting");
        return "hello";
    }
}
```

Another bean receives `GreetingService` by constructor injection and calls `greet()` once,
printing its return value. Write the four console lines in their exact order. Then: if the
advice's last line were `return null;` instead of `return result;`, what would change?

**Answer.**

```text
IN
building greeting
OUT
hello
```

The call lands on the proxy, which runs the advice: `IN` prints, `proceed()` runs the real
method (`building greeting`), control returns to the advice (`OUT`), the advice returns
`"hello"`, and the caller prints it. With `return null;` the first three lines stay the
same, but the caller receives and prints `null` — the advice sits between the method and
the caller, so whatever the advice returns *is* the method's return value as far as the
caller can tell. Silent data corruption, which is why `return result` is sacred.

### Do it yourself (second laptop)

**Step 0 — things that must exist.** Java 21 and Maven:

```bash
brew install openjdk@21 maven
mvn -version    # want: Java version: 21...
```

**Step 1 — folders:**

```bash
mkdir -p ~/foundations/F6.12-aop/scratch-app/src/main/java/com/zenith/aop
mkdir -p ~/foundations/F6.12-aop/scratch-app/src/main/java/com/zenith/common
cd ~/foundations/F6.12-aop/scratch-app
```

**Step 2 — create the six files** exactly as in section 4: `pom.xml` in the project root,
`Timed.java` in `src/main/java/com/zenith/common/`, and `AopApplication.java`,
`BookingService.java`, `TimingAspect.java`, `L8TimedDemo.java` in
`src/main/java/com/zenith/aop/`.

**Step 3 — run:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-timed
```

(First run downloads dependencies.) Expected after the startup lines:

```text
Seat reserved.
reserveSeat took 120 ms
Booking cancelled.
cancelBooking took 83 ms
Bookings: [A1, B4, C2]
listBookings took 43 ms
```

**Step 4 — break experiment A (retention).** In `Timed.java`, delete the
`@Retention(RetentionPolicy.RUNTIME)` line. Run again. On Spring Boot 3.5.16 the timings
*still print* — the matcher reads the class file, not the reflection view. Put the line
back; the rule ("always `RUNTIME`") stands for the reasons in section 5.

**Step 5 — break experiment B (bare name).** In `TimingAspect.java`, change the pointcut
to `@Around("@annotation(Timed)")`. Run again. Expected: app starts cleanly, three work
lines, **no timing lines, no error**. Restore
`@Around("@annotation(com.zenith.common.Timed)")` and re-run; the timings return.

Expected failures and what they mean:

| You see | It means | Fix |
|---|---|---|
| `cannot find symbol ... ProceedingJoinPoint` | `spring-boot-starter-aop` missing | Add the dependency to `pom.xml` |
| `cannot find symbol ... Timed` in `BookingService` | Missing import | `import com.zenith.common.Timed;` |
| Work lines print, no timings, no errors | The silent no-match | Check the pointcut's fully qualified name, then `@Component` on the aspect |

**Done when:** you have seen the timing lines appear, disappear (bare name), and reappear
— and you can state the habit: *prove the aspect fired before trusting it.*

### Summary

- Assembly: `spring-boot-starter-aop` + `@Timed` + `@Aspect @Component` class with
  `@Around("@annotation(<fully.qualified.Name>)")` advice on a `ProceedingJoinPoint`.
- `proceed()` runs the real method; always `return` its result; keep `throws Throwable`.
- Proven on this machine: methods with zero clock code printed timings — S3's deletion
  achieved.
- Break B (bare pointcut name): starts clean, matches nothing, prints nothing, errors
  nothing. Fully qualified names, always.
- Break A (wrong retention): on this Spring version matching *still worked* — AspectJ
  reads the class file. `RUNTIME` remains the rule: reflection-based reads (the value, any
  other reader) still need it, and internals vary by version.
- The habit: prove the aspect fired (find its output line) before building on it.

---

## Lesson 9 — Annotation values: passing information from the mark to the aspect (subtask S4)

### 1. What is it?

An annotation can carry data. `@Timed("checkout")` carries the string `"checkout"`. The
data sits in a named slot declared inside the annotation, and the aspect reads it at
runtime through the `MethodSignature`.

### 2. Why do we need it?

A bare `@Timed` can only say "time me"; the printed label has to be the method name —
developer words, not business words. And Phase 5's `@Idempotent` is useless unless it
knows *which key* identifies a duplicate request. The mark needs a slot for extra
information; the aspect needs a way to read it. Phase 5 reads its key exactly the way this
lesson shows.

### 3. How does it work?

**Declaring the slot** — inside the annotation body, what looks like a short method:

```java
String value() default "";
```

- `String` — the slot's type (strings, numbers, booleans, enums, class references, arrays
  of those).
- `value` — this exact name unlocks the shorthand `@Timed("checkout")`. Any other name,
  say `label`, must be written out: `@Timed(label = "checkout")`.
- `default ""` — makes the slot optional. No default = mandatory at every use, enforced by
  the compiler.

**Reading the slot in the aspect** — four plain steps:

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();
Timed timed = signature.getMethod().getAnnotation(Timed.class);
String label = timed.value();
```

- `getSignature()` returns general call information — typed general, because in AspectJ's
  wider world a join point is not always a method.
- In Spring AOP it always is, so the cast to `MethodSignature` is safe.
- `getMethod()` hands over the `java.lang.reflect.Method` — the same handle as in the
  Lesson 3 reflection demo.
- `getAnnotation(Timed.class)` fetches the live annotation instance **via reflection** —
  so it sees only `RUNTIME`-retained annotations. `.value()` reads the slot.

### 4. Simple example

The annotation gains the slot:

```java
// src/main/java/com/zenith/common/Timed.java
package com.zenith.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timed {

    // The name "value" allows the shorthand @Timed("checkout").
    // The default makes it optional.
    String value() default "";
}
```

One method gets a business label; the other two stay bare:

```java
@Timed("checkout")
public void reserveSeat() throws InterruptedException {
    Thread.sleep(120);
    System.out.println("Seat reserved.");
}
```

The aspect reads the slot, falling back to the method name:

```java
// src/main/java/com/zenith/aop/TimingAspect.java
package com.zenith.aop;

import com.zenith.common.Timed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimingAspect {

    @Around("@annotation(com.zenith.common.Timed)")
    public Object time(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Timed timed = signature.getMethod().getAnnotation(Timed.class);
        String label = timed.value().isEmpty()
                ? signature.getMethod().getName()   // no value given: use the method name
                : timed.value();                    // value given: use it

        long start = System.nanoTime();

        Object result = joinPoint.proceed();   // run the real method

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println(label + " took " + elapsedMs + " ms");
        return result;
    }
}
```

The demo runner answers to both profiles: `@Profile({"l8-timed", "l9-value"})`.

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l9-value`

Real output:

```text
Seat reserved.
checkout took 125 ms
Booking cancelled.
cancelBooking took 83 ms
Bookings: [A1, B4, C2]
listBookings took 41 ms
```

The value travelled from the annotation into the aspect; unlabeled methods fall back to
method names.

### 5. Walkthrough

The `reserveSeat` call: proxy → advice → cast the signature → `getMethod()` →
`getAnnotation(Timed.class)` → instance exists (`RUNTIME`) → `value()` returns
`"checkout"` → not empty → label is `"checkout"` → clock, `proceed()`, clock → print
`checkout took 125 ms`.

**The retention debt from Lesson 8 comes due.** Wrong retention looked harmless for
matching. But `getAnnotation` is reflection. With `@Retention(RUNTIME)` removed, the run
on this machine produced:

```text
Started AopApplication in 0.359 seconds (process running for 0.453)
ERROR ... Application run failed
java.lang.NullPointerException: Cannot invoke "com.zenith.common.Timed.value()" because "timed" is null
	at com.zenith.aop.TimingAspect.time(TimingAspect.java:21)
	...
	at com.zenith.aop.BookingService$$SpringCGLIB$$0.reserveSeat(<generated>)
	at com.zenith.aop.L8TimedDemo.run(L8TimedDemo.java:20)
```

Decoded: the pointcut matched (class-file reading), the advice ran, reflection found
nothing on the visible shelf, returned `null`, and `.value()` on `null` exploded. On this
Spring version, wrong retention does not silence the aspect — it crashes it at the first
value read. Either way: `RUNTIME`, always.

Treasure in the stack trace: `BookingService$$SpringCGLIB$$0.reserveSeat(<generated>)` —
the caller of the advice is a *generated* class with `$$SpringCGLIB$$` in its name. The
proxy, caught on camera. Lesson 13 hunts it deliberately.

### 6. Where is it used?

| You write | The slot carries | Read by |
|---|---|---|
| `@Timed("checkout")` | the metric label | our aspect |
| `@Idempotent(key = "...")` (Phase 5) | what makes a request a duplicate | the idempotency interceptor |
| `@Cacheable("products")` | which cache to use | Spring's caching advice |
| `@Transactional(readOnly = true)` | transaction settings | Spring's transaction advice |
| `@Profile("l9-value")` | when the bean exists | the application context |

### 7. Common mistakes

1. **Expecting shorthand for a non-`value` slot.** `@Timed("checkout")` works only because
   the slot is named exactly `value`. A slot named `label` must be written
   `@Timed(label = "checkout")`.
2. **No `default` on a slot meant to be optional.** Every existing bare use stops
   compiling. Decide on purpose: mandatory (no default — right for an idempotency key) or
   optional (default — right for a cosmetic label).
3. **Reading from the wrong place.** `getAnnotation` must be called on the *method*
   carrying the mark, not on the class or the target object's class.
4. **Assuming the instance is always there.** Broken retention makes reflection return
   `null` and the advice crash. `NullPointerException ... because "timed" is null` in an
   aspect → check `@Retention` first.

### 8. Link to the task

Subtask S4, word for word: "Give the annotation a value — `@Timed("checkout")` — and read
it inside the aspect via the `MethodSignature`." Done and proven. Phase 5's `@Idempotent`
reads its key the same way: same cast, same `getMethod()`, same `getAnnotation(...)`,
different slot.

### 9. Check my understanding

**Q1.** Here is a complete annotation and one use of it:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Retry {
    int times() default 3;
}
```

```java
@Retry(times = 5)
public void fetchPrices() { ... }
```

(a) An aspect's advice has the `Method` handle `m` for `fetchPrices`. What does
`m.getAnnotation(Retry.class).times()` return? (b) A teammate writes `@Retry(5)` on
another method and it does not compile. Why not? (c) If the slot declaration lost its
`default 3`, what happens to a method annotated with just `@Retry`?

**Answer.** (a) `5` — the value written at the use site wins over the default. (b) The
positional shorthand `@Retry(5)` assigns to a slot named exactly `value`, and `Retry` has
no slot named `value` — its slot is `times`. The compiler rejects it; it must be written
`@Retry(times = 5)`. (c) Removing the default makes `times` mandatory. A bare `@Retry`
stops compiling, and every use must now write `times = ...` explicitly. That is a feature
when forgetting the value must be impossible — an idempotency key, for example.

**Q2.** Here is a complete annotation and a complete advice from one Spring app:

```java
// package com.acme.common
@Target(ElementType.METHOD)          // note: no @Retention line
public @interface Audit {
    String channel() default "main";
}
```

```java
@Around("@annotation(com.acme.common.Audit)")
public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Audit audit = signature.getMethod().getAnnotation(Audit.class);
    System.out.println("audit channel: " + audit.channel());
    return joinPoint.proceed();
}
```

On Spring Boot 3.5.16, a bean method marked `@Audit` is called. The pointcut matches (the
matcher reads class files). What happens next, line by line, and what is the fix?

**Answer.** The advice starts running. The cast to `MethodSignature` succeeds.
`getAnnotation(Audit.class)` runs — this is reflection, and with no `@Retention` line the
annotation defaults to `CLASS` retention: present in the class file, invisible to
reflection. So `audit` is `null`. The next line calls `audit.channel()` on `null` and
throws `NullPointerException: Cannot invoke ... because "audit" is null`. The exception
flies out of the advice, through the proxy, to the caller — the call fails loudly. The
real method never runs (`proceed()` was never reached). Fix: declare the annotation with
`@Retention(RetentionPolicy.RUNTIME)` (plus its two imports).

### Do it yourself (second laptop)

In the `scratch-app` from lesson 8:

**Step 1.** In `src/main/java/com/zenith/common/Timed.java`, add the slot inside the
braces:

```java
public @interface Timed {
    String value() default "";
}
```

**Step 2.** In `BookingService.java`, change `reserveSeat`'s mark to `@Timed("checkout")`.
Leave the other two methods bare.

**Step 3.** Replace `TimingAspect.java` with the version from section 4 (new imports:
`com.zenith.common.Timed`, `org.aspectj.lang.reflect.MethodSignature`).

**Step 4.** In `L8TimedDemo.java`, change the profile line to
`@Profile({"l8-timed", "l9-value"})`. Run:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l9-value
```

Expected:

```text
Seat reserved.
checkout took 125 ms
Booking cancelled.
cancelBooking took 83 ms
Bookings: [A1, B4, C2]
listBookings took 41 ms
```

**Step 5 — the crash experiment.** Delete `@Retention(RetentionPolicy.RUNTIME)` from
`Timed.java`, run again. Expected: startup succeeds, then

```text
java.lang.NullPointerException: Cannot invoke "com.zenith.common.Timed.value()" because "timed" is null
	at com.zenith.aop.TimingAspect.time(...)
```

Find the `$$SpringCGLIB$$` line in the stack trace — first sighting of the proxy. Restore
the retention line and re-run.

**Done when:** `checkout took ... ms` prints, and you can explain why wrong retention
crashed this version of the aspect but did not silence last lesson's (matching reads class
files; `getAnnotation` is reflection and sees only `RUNTIME`).

### Summary

- Annotation slots carry data: `String value() default "";` declares one; `value` is the
  only name that allows the `@Timed("checkout")` shorthand; `default` decides optional vs
  mandatory.
- Reading in the aspect: cast `getSignature()` to `MethodSignature` (safe — Spring join
  points are always methods) → `getMethod()` → `getAnnotation(Timed.class)` → `.value()`.
- `getAnnotation` is reflection: with wrong retention it returns `null` and the advice
  crashes with an NPE — proven live. `RUNTIME`, always.
- The crash's stack trace shows `$$SpringCGLIB$$` — the generated proxy, visible by name.
- `@Idempotent(key = ...)`, `@Cacheable("...")`, `@Transactional(readOnly = true)` are all
  slots read by advice — same mechanism everywhere.

---

## Lesson 10 — Skipping `proceed()`: the advice decides whether the method runs (subtask S5)

### 1. What is it?

`joinPoint.proceed()` is just a call *you* make — or do not make. If the advice skips it
and returns some other value, the real method never runs, and the caller receives that
other value as if the method had produced it.

### 2. Why do we need it?

The double-charge nightmare: a client sends "charge the card", the response is lost, the
client retries, the same charge arrives twice. If the method runs twice, the customer pays
twice. The wish: first request does the work, the answer is remembered; the repeat gets
the *same answer back* and the work is **not** redone. That property is idempotency —
Phase 5's `@Idempotent`, the most reused piece of the project. The gatekeeper must stand
between caller and method with the power to not let the call through — exactly where
`@Around` advice already stands.

### 3. How does it work?

Three shapes of advice, by what they do with `proceed()`:

1. **Always proceed** — timing, logging, auditing: the advice only observes.
2. **Proceed, then remember** — first time: run the method, store its answer.
3. **Check, then maybe skip** — answer already stored: return it; `proceed()` never runs.

Shapes 2 + 3 together are the idempotency pattern:

```text
call arrives at the advice
        │
   seen this key before?
        │
  yes ──┴── no
   │         │
return     proceed()  → run the real method
stored       │
answer     store the answer
   │         │
   └────► caller gets an answer either way
```

The heavy lifting is done by one Lesson 8 fact: *whatever the advice returns is what the
caller receives.*

### 4. Simple example

Three new files. The annotation — key has **no default**, so forgetting it is a compile
error:

```java
// src/main/java/com/zenith/common/Idempotent.java
package com.zenith.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    String key();
}
```

```java
// src/main/java/com/zenith/aop/PaymentService.java
package com.zenith.aop;

import com.zenith.common.Idempotent;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {

    private int charges = 0;

    // The demo key is fixed. In the real Phase 5, the key comes from the request.
    @Idempotent(key = "charge-order-42")
    public String charge() {
        charges++;
        System.out.println("Charging the card for real...");
        return "PAID-" + charges;
    }

    public int timesCharged() {
        return charges;
    }
}
```

```java
// src/main/java/com/zenith/aop/IdempotencyAspect.java
package com.zenith.aop;

import com.zenith.common.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class IdempotencyAspect {

    private final Map<String, Object> storedResponses = new ConcurrentHashMap<>();

    @Around("@annotation(com.zenith.common.Idempotent)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Idempotent idempotent = signature.getMethod().getAnnotation(Idempotent.class);
        String key = idempotent.key();

        if (storedResponses.containsKey(key)) {   // seen this key before?
            System.out.println("duplicate '" + key + "': returning stored response, skipping the method");
            return storedResponses.get(key);      // proceed() is NEVER called
        }

        Object result = joinPoint.proceed();      // first time: run the real method
        storedResponses.put(key, result);         // remember its answer
        return result;
    }
}
```

```java
// src/main/java/com/zenith/aop/L10SkipDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l10-skip")
public class L10SkipDemo implements CommandLineRunner {

    private final PaymentService paymentService;

    public L10SkipDemo(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void run(String... args) {
        System.out.println("first call returned:  " + paymentService.charge());
        System.out.println("second call returned: " + paymentService.charge());
        System.out.println("the card was charged " + paymentService.timesCharged() + " time(s)");
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-skip`

Real output:

```text
Charging the card for real...
first call returned:  PAID-1
duplicate 'charge-order-42': returning stored response, skipping the method
second call returned: PAID-1
the card was charged 1 time(s)
```

`Charging the card for real...` appears **once**. Both calls returned `PAID-1`. The second
call was answered entirely by the advice.

### 5. Walkthrough

**First call:** advice runs → key not in map → `proceed()` → method prints, counter → 1,
returns `"PAID-1"` → advice stores it under the key → returns it.

**Second call:** advice runs → key in map → prints the `duplicate` line → returns stored
`"PAID-1"`. **`proceed()` never reached.** No print, no counter change. The caller cannot
tell the difference — the point.

`timesCharged()` carries no annotation: through the proxy, no advice, runs plain.

Honest simplification: the demo key is a fixed string, so *every* call counts as the same
request. Phase 5's key comes from a request header, so different orders charge normally
and only true retries get the stored response.

### 6. Where is it used?

| Feature | The condition | Returned instead of proceeding |
|---|---|---|
| `@Idempotent` (Phase 5) | same request key seen before | the stored response |
| `@Cacheable` | same arguments asked before | the cached answer |
| Circuit breaker | downstream known dead | a fallback value, instantly |
| Kill switch | feature turned off | a "disabled" response |
| Rate limiter | quota used up | a rejection, without doing the work |

Same skeleton every time.

### 7. Common mistakes

1. **Forgetting that skipping skips everything** — database writes, emails, log lines in
   the method: none happen. Skip only when the whole method's work should not happen.
2. **Canned value of the wrong type.** The advice returns `Object`; the compiler cannot
   check it. `String` method + `Integer` canned value = `ClassCastException` at the caller,
   at runtime.
3. **A key that is too coarse.** A fixed key treats every call as the same request — in
   production, every customer would receive the first customer's response. The key must
   identify one logical request.
4. **A plain in-memory map in real systems.** It dies with the app and is not shared
   between servers. Phase 5 uses a shared store; the advice logic stays identical.

### 8. Link to the task

Subtask S5, word for word: "Skip `proceed()` entirely under some condition and return a
canned value instead. The real method never runs. That is precisely how an idempotency
interceptor returns the stored response for a duplicate request." Done — card charged
once, two identical responses, and a working miniature of Phase 5's most reused component.

### 9. Check my understanding

**Q1.** Here is a complete aspect and a complete bean. The annotation `com.acme.Guarded`
is correctly declared with `RUNTIME` retention and `METHOD` target.

```java
@Aspect
@Component
public class KillSwitchAspect {

    private final boolean featureEnabled = false;   // the switch is OFF

    @Around("@annotation(com.acme.Guarded)")
    public Object gate(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!featureEnabled) {
            return "feature disabled";
        }
        return joinPoint.proceed();
    }
}

@Component
public class ExportService {

    @Guarded
    public String exportReport() {
        System.out.println("building the export file...");
        return "export-2026-11-11.csv";
    }
}
```

Another bean calls `exportReport()` once and prints the returned value. (a) What appears
on the console, in order? (b) Does the export file get built? (c) What single change makes
the real method run again?

**Answer.** (a) One line only:

```text
feature disabled
```

The advice sees `featureEnabled` is false and returns the canned string without calling
`proceed()`. The method body never runs, so `building the export file...` never prints.
The caller receives `"feature disabled"` and prints it. (b) No — skipping `proceed()`
skips the entire body, including its side effects. (c) Set `featureEnabled = true`. Then
the console shows `building the export file...` followed by `export-2026-11-11.csv`.

**Q2.** In the idempotency aspect from this lesson, a teammate reorders the logic:

```java
Object result = joinPoint.proceed();          // run the method FIRST
if (storedResponses.containsKey(key)) {
    return storedResponses.get(key);          // then return the old answer for duplicates
}
storedResponses.put(key, result);
return result;
```

The duplicate call still returns the first answer, so the teammate says it works. For the
double-charge problem, why is this version worthless? Say exactly what happens on the
second call to a method annotated `@Idempotent(key = "charge-order-42")` whose body
charges a card.

**Answer.** Because `proceed()` now runs **before** the duplicate check, the real method
executes on *every* call. On the second call: the card is charged a second time
(`Charging the card for real...` prints again, the counter reaches 2) — and only then does
the advice notice the key and return the *old* response, `PAID-1`. The customer is charged
twice while the caller sees a response that pretends nothing happened — worse than no
protection, because it hides the damage. Idempotency exists to prevent re-*execution*, not
to repeat a response. The check must come before `proceed()`.

### Do it yourself (second laptop)

In the `scratch-app` from lessons 8–9, create the four new files exactly as in section 4:
`Idempotent.java` in `src/main/java/com/zenith/common/`, and `PaymentService.java`,
`IdempotencyAspect.java`, `L10SkipDemo.java` in `src/main/java/com/zenith/aop/`. Then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-skip
```

Expected:

```text
Charging the card for real...
first call returned:  PAID-1
duplicate 'charge-order-42': returning stored response, skipping the method
second call returned: PAID-1
the card was charged 1 time(s)
```

Two experiments:

1. In `PaymentService`, write just `@Idempotent` (no key). Compile: expect a loud error
   about the missing element `key` — the no-default design doing its job. Put it back.
2. Add a third `charge()` call in the demo: another `duplicate` line, `PAID-1` again,
   counter still 1.

**Done when:** the counter prints `1 time(s)` while all calls returned `PAID-1`, and you
can say where the second call's answer came from (the advice's map — the method never
ran).

### Summary

- `proceed()` is optional. Skip it and return a value: the method never runs, the caller
  receives the advice's value as if it were the method's.
- Idempotency shape: check a key before `proceed()`; hit = return stored response; miss =
  proceed, store, return. The check must come **before** `proceed()` — after is worthless.
- Skipping skips side effects too — that is the point, and the danger.
- The canned value must match the method's return type; the compiler cannot check it.
- `@Cacheable`, circuit breakers, kill switches, rate limiters: the same check-then-maybe-
  skip skeleton.

---

## Lesson 11 — Self-invocation: the call that silently skips your aspect (subtask S6)

### 1. What is it?

Self-invocation means a method of an object calling another method of the *same* object —
`this.calculateTotal()`, or just `calculateTotal()`, from a sibling method. In Spring,
such a call never passes through the proxy, so no advice runs. No timing, no transaction,
no cache, no error, no warning. The single most common Spring bug there is.

### 2. Why do we need it?

It bites everyone exactly once, invisibly. A `@Transactional` method that self-invokes
runs *without a transaction*. A `@Cacheable` method called internally *never consults the
cache*. Nothing fails, nothing logs; the feature you think you have, you do not have.
Better to lose five minutes on it deliberately than days in production.

### 3. How does it work?

From the two-objects picture: proxy P wraps real bean R; other beans hold P; advice lives
on P's path; inside R, `this` means **R** — R has no idea P exists.

Trace: someone calls `P.checkout()`. No advice matches `checkout` (not annotated); P
forwards to `R.checkout()`. Inside, `this.calculateTotal()` runs — `this` is R, so it is
`R.calculateTotal()`, object to itself. It never leaves R; P is never consulted; the
advice never runs:

```text
demo ──► P.calculateTotal() ──► advice ──► R.calculateTotal()        TIMED

demo ──► P.checkout() ──► (no advice matches) ──► R.checkout()
                                                     │  this.calculateTotal()
                                                     ▼
                                              R.calculateTotal()     NOT TIMED — P never saw it
```

Same leak as Lesson 6's `DirectCall`, but disguised: there the direct call was visible
(`real.reserveSeat()`); here the word `this` does it invisibly.

### 4. Simple example

```java
// src/main/java/com/zenith/aop/CheckoutService.java
package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

@Component
public class CheckoutService {

    // NOT annotated. Called from outside.
    public void checkout() throws InterruptedException {
        System.out.println("checkout: starting");
        this.calculateTotal();          // internal call - never leaves this object
        System.out.println("checkout: done");
    }

    @Timed("calculate-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated");
    }
}
```

```java
// src/main/java/com/zenith/aop/L11SelfInvocationDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l11-self")
public class L11SelfInvocationDemo implements CommandLineRunner {

    private final CheckoutService checkoutService;

    public L11SelfInvocationDemo(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- 1: calling calculateTotal() from OUTSIDE ---");
        checkoutService.calculateTotal();

        System.out.println("--- 2: calling checkout(), which calls this.calculateTotal() INSIDE ---");
        checkoutService.checkout();
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l11-self`

Real output:

```text
--- 1: calling calculateTotal() from OUTSIDE ---
total calculated
calculate-total took 65 ms
--- 2: calling checkout(), which calls this.calculateTotal() INSIDE ---
checkout: starting
total calculated
checkout: done
```

Block 2: the *same method* ran (`total calculated`), the same annotation sat on it — and
there is no timing line, and not one character of error output.

### 5. Walkthrough

**Block 1:** the demo's field holds P. `calculateTotal()` lands on P → pointcut matches →
advice: clock, forward to R (`total calculated`), clock, print `calculate-total took 65
ms`.

**Block 2:** `checkout()` lands on P → no annotation, no advice → forward to
`R.checkout()` → prints `checkout: starting` → `this.calculateTotal()` with `this` = R →
straight into R's own method → sleeps, prints, returns → `checkout: done`. Control never
touched P after the forward. The advice had no chance.

Bitter details: omitting `this.` changes nothing — the compiler inserts it. The annotation
is read on the proxy path only, and that path was never taken.

### 6. Where is it used?

| The code says | The internal call silently gets |
|---|---|
| `this.saveAll()` where `saveAll` is `@Transactional` | **no transaction** — partial writes stay on failure (checkpoint question 4) |
| `this.findPrices()` where `findPrices` is `@Cacheable` | no cache check — slow work runs every time |
| `this.sendEmails()` where `sendEmails` is `@Async` | no other thread — runs synchronously |
| `this.charge()` where `charge` is `@Idempotent` (Phase 5) | no duplicate protection |
| `this.calculateTotal()` where it is `@Timed` | no timing — this lesson's demo |

Sentence for life: **an annotation only works on calls that arrive through the proxy; a
call from inside the same object never does.**

### 7. Common mistakes

1. **Believing the annotation works because it is visibly there.** Necessary, not
   sufficient. Ask of every annotated method: who calls it, and does the call cross a bean
   boundary?
2. **Expecting any signal.** There is none. With `@Transactional` the code behaves
   correctly *until* the first mid-method failure, months later, in production.
3. **Blaming Spring.** Not a defect — the honest price of the proxy design. Spring cannot
   intercept a call it never receives.
4. **Clever fragile workarounds.** There are two honest fixes (Lesson 12).

### 8. Link to the task

Subtask S6 done and witnessed, including "work out why": Spring gave the caller a proxy;
an internal call never leaves the object; the proxy is never involved. Checkpoint question
3 answered (mechanical explanation); checkpoint question 4's "why" answered — the fixes
follow in Lesson 12.

### 9. Check my understanding

**Q1.** Here is a complete bean in a Spring app. Spring's transaction machinery works like
the timing aspect: advice around the method opens a transaction and rolls it back if the
method throws.

```java
@Component
public class OrderService {

    public void placeOrder() {
        System.out.println("validating order");
        this.saveEverything();
    }

    @Transactional
    public void saveEverything() {
        // writes the order row
        // writes the payment row   <-- suppose this line throws an exception
        // writes the audit row
    }
}
```

Another bean calls `placeOrder()`. Midway through `saveEverything`, writing the payment
row throws. The developer expects the order row to be rolled back. What actually happens
to the order row, and why — explained in terms of the two objects and the path of the
call?

**Answer.** The order row **stays in the database**. No rollback happens, because no
transaction was ever started. The path: the caller holds the proxy P. `placeOrder()` is
not annotated, so P forwards it plainly to the real object R. Inside R,
`this.saveEverything()` is a call from R to itself — it never reaches P, and P is where
the transaction advice lives. So `saveEverything` ran as a plain method: no transaction
opened, each write standing alone. When the payment write threw, there was nothing to roll
back the order write with. The annotation sat on the method the whole time, useless,
because the call path never crossed the proxy. No error mentions any of this — the
exception from the payment row looks like the only problem.

**Q2.** Here is a complete bean and its caller in a Spring app. `@Timed` and the timing
aspect work exactly as in this lesson.

```java
@Component
public class NotificationService {

    public void sendAll() throws InterruptedException {
        for (int i = 1; i <= 3; i++) {
            sendOne(i);                  // note: no "this." written - does that matter?
        }
    }

    @Timed("send-one")
    public void sendOne(int n) throws InterruptedException {
        Thread.sleep(30);
        System.out.println("sent notification " + n);
    }
}
```

A demo bean, which received `NotificationService` by constructor injection, calls
`sendAll()` once. (a) How many `send-one took ... ms` lines print? (b) Does the missing
`this.` change anything? (c) What single extra call from the *demo bean* would produce
exactly one timing line?

**Answer.** (a) **Zero.** `sendAll()` arrives through the proxy but carries no annotation,
so it is forwarded plainly to the real object. The three `sendOne(i)` calls inside are
self-invocations — real object to itself — so the advice never runs. Three
`sent notification ...` lines print, no timing lines. (b) No. `sendOne(i)` and
`this.sendOne(i)` compile to the same thing; the compiler inserts `this.`. (c)
`notificationService.sendOne(5);` from the demo bean — that call crosses the bean
boundary, lands on the proxy, and produces exactly one `send-one took ... ms` line.

### Do it yourself (second laptop)

In the `scratch-app`, create `CheckoutService.java` and `L11SelfInvocationDemo.java`
exactly as in section 4, both in `src/main/java/com/zenith/aop/`. Then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l11-self
```

Expected:

```text
--- 1: calling calculateTotal() from OUTSIDE ---
total calculated
calculate-total took 65 ms
--- 2: calling checkout(), which calls this.calculateTotal() INSIDE ---
checkout: starting
total calculated
checkout: done
```

Variation: in `checkout()`, delete the `this.` so the line reads `calculateTotal();`. Run
again. Expected: identical output — still no timing line.

**Done when:** you can give the mechanical explanation out loud, with the two objects
named: "My caller holds the proxy. Inside the real object, `this` is the real object. An
internal call goes object-to-itself, never reaches the proxy, so no advice runs."

### Summary

- Self-invocation: a method calling a sibling method of the same object (`this.other()` or
  bare `other()`).
- Mechanics: callers hold the proxy; inside the real object `this` is the real object; the
  internal call never reaches the proxy; advice lives only on the proxy path.
- Proven live: the same annotated method, timed from outside, silently untimed from
  inside. No error anywhere.
- The same mechanism silently disables `@Transactional`, `@Cacheable`, `@Async`,
  `@Idempotent` on internal calls — Spring's most famous failure mode.
- The annotation is necessary, not sufficient: the call must cross the proxy.

---

## Lesson 12 — Self-invocation: the two honest fixes (subtask S7)

### 1. What is it?

Two ways to repair a self-invocation so advice runs again. **Fix 1:** move the annotated
method into its own bean. **Fix 2:** inject the bean into itself and call through that
reference. Both work the same honest way: they give the call a proxy to cross.

### 2. Why do we need it?

Lesson 11 ended broken: `checkout()` calling `this.calculateTotal()`, timing silently
gone, because the call never crossed the proxy. The cure must be mechanical too: *make the
call cross a proxy.* There are exactly two clean arrangements, and the task asks for both,
plus a written choice.

### 3. How does it work?

**Fix 1 — own bean.** `calculateTotal()` moves to a new bean `TotalCalculator`.
`CheckoutService` asks for it in the constructor and receives — as always — the *proxy*.
`totalCalculator.calculateTotal()` is now a call to a different object, and that object is
a proxy. Advice runs. Self-invocation becomes impossible by construction: no sibling
method remains.

**Fix 2 — self-injection.** The bean takes a constructor parameter of its own type. Spring
injects the proxy. The internal call changes from `this.calculateTotal()` to
`self.calculateTotal()` — `this` is the real object, `self` is the proxy. One word changes
the path: out of the object, through the proxy and its advice, back in.

The wrinkle: a chicken-and-egg. To build the bean, Spring must inject the bean — which is
mid-construction. Plain self-injection kills startup with a circular-reference error.
`@Lazy` on the parameter escapes it: Spring hands a placeholder that resolves to the proxy
on first use, by which time the bean exists.

### 4. Simple example

Fix 1:

```java
// src/main/java/com/zenith/aop/TotalCalculator.java
package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

@Component
public class TotalCalculator {

    @Timed("extracted-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated (own-bean version)");
    }
}
```

```java
// src/main/java/com/zenith/aop/CheckoutServiceFixed.java
package com.zenith.aop;

import org.springframework.stereotype.Component;

@Component
public class CheckoutServiceFixed {

    private final TotalCalculator totalCalculator;

    public CheckoutServiceFixed(TotalCalculator totalCalculator) {
        this.totalCalculator = totalCalculator;
    }

    public void checkout() throws InterruptedException {
        System.out.println("fixed checkout: starting");
        totalCalculator.calculateTotal();   // crosses a bean boundary -> proxy -> advice
        System.out.println("fixed checkout: done");
    }
}
```

Fix 2:

```java
// src/main/java/com/zenith/aop/SelfInjectedCheckoutService.java
package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class SelfInjectedCheckoutService {

    private final SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(@Lazy SelfInjectedCheckoutService self) {
        this.self = self;
    }

    public void checkout() throws InterruptedException {
        System.out.println("self-injected checkout: starting");
        self.calculateTotal();              // through the proxy this time, NOT this.
        System.out.println("self-injected checkout: done");
    }

    @Timed("self-injected-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated (self-injected version)");
    }
}
```

The demo (`L12FixesDemo`, profile `l12-fixes`) injects both services and calls both
`checkout()` methods:

```java
// src/main/java/com/zenith/aop/L12FixesDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l12-fixes")
public class L12FixesDemo implements CommandLineRunner {

    private final CheckoutServiceFixed checkoutServiceFixed;
    private final SelfInjectedCheckoutService selfInjectedCheckoutService;

    public L12FixesDemo(CheckoutServiceFixed checkoutServiceFixed,
                        SelfInjectedCheckoutService selfInjectedCheckoutService) {
        this.checkoutServiceFixed = checkoutServiceFixed;
        this.selfInjectedCheckoutService = selfInjectedCheckoutService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- fix 1: annotated method moved to its own bean ---");
        checkoutServiceFixed.checkout();

        System.out.println("--- fix 2: bean injected into itself ---");
        selfInjectedCheckoutService.checkout();
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l12-fixes`

Real output:

```text
--- fix 1: annotated method moved to its own bean ---
fixed checkout: starting
total calculated (own-bean version)
extracted-total took 61 ms
fixed checkout: done
--- fix 2: bean injected into itself ---
self-injected checkout: starting
total calculated (self-injected version)
self-injected-total took 65 ms
self-injected checkout: done
```

Both timing lines are back.

### 5. Walkthrough

**Fix 1's path:** `checkout()` runs in the real `CheckoutServiceFixed` object; the
injected field holds *TotalCalculator's proxy*; the call leaves one object, lands on the
proxy, advice fires.

**Fix 2's path:** `checkout()` runs in the real object R; `self` holds R's own proxy P;
`self.calculateTotal()` leaves R, lands on P, advice fires, P forwards back into R. A
round trip out the front door and back in.

**`@Lazy` removed, on purpose** — real output:

```text
Error creating bean with name 'selfInjectedCheckoutService': Requested bean is currently
in creation: Is there an unresolvable circular reference or an asynchronous
initialization dependency?

APPLICATION FAILED TO START
Relying upon circular references is discouraged and they are prohibited by default.
```

Decoded: "to build this bean I need this bean, which I am mid-way through building."
Mercifully loud: the app refuses to start and names the bean.

### 6. Where is it used?

Fix 1 hides in plain sight in good codebases: a `TransactionalWriter` bean holding the
`@Transactional` methods, an `AsyncMailer` holding the `@Async` ones — praised as design,
and also proxy-correct. Phase 5 gets it for free: `@Idempotent` sits on service methods
called from controllers, and controller-to-service calls always cross a bean boundary.
Fix 2 appears in legacy code where extraction is too risky, and in small beans where a
one-method extraction would feel absurd.

### 7. Common mistakes

1. **Self-injection without `@Lazy`** — startup dies with the circular-reference error.
2. **The back door stays open in fix 2** — nothing stops a later `this.calculateTotal()`;
   it compiles, runs, silently skips advice. Fix 1 has no such weakness: no sibling method
   exists. The strongest argument between the two.
3. **Extracting everything into micro-beans** — fifty one-method beans is confetti, not
   design. Extract when the annotated method is a genuine separate responsibility.
4. **Believing the fixes change the rule** — they only arrange the crossing. The rule
   stays: advice runs only on calls that cross the proxy.

### 8. Link to the task

Subtask S7 done: both fixes built and proven. **Recorded choice: fix 1 (own bean) as the
default** — it removes the trap structurally (no sibling method can ever bypass the proxy
again), reads as ordinary good design, and needs no `@Lazy` trick. Fix 2 is kept for cases
where extraction would tear apart a cohesive class or is too risky right now. Checkpoint
question 4 is now fully answered: self-invoked `@Transactional` does nothing because the
call never crosses the proxy where the transaction advice lives; the fixes are (1) move
the method to its own bean, (2) inject the bean into itself and call through `self`.

### 9. Check my understanding

**Q1.** Here is a complete, broken bean — same disease as Lesson 11:

```java
@Component
public class ReportService {

    public void nightlyJob() {
        this.buildReport();
    }

    @Timed("build-report")
    public void buildReport() {
        System.out.println("report built");
    }
}
```

Apply **fix 1** in words: (a) name what moves and where it moves to, (b) name what
`ReportService` receives in its constructor afterwards, and (c) explain in one sentence —
using the word proxy — why the timing line appears after the change.

**Answer.** (a) The `buildReport()` method, with its `@Timed("build-report")` annotation,
moves into a new `@Component` class of its own — say `ReportBuilder`. (b) `ReportService`
now takes a `ReportBuilder` in its constructor — and what Spring actually hands it is the
`ReportBuilder` *proxy*. (c) `nightlyJob()` now calls `reportBuilder.buildReport()`, which
is a call from one object onto another object's proxy — and advice runs on the proxy path,
so `build-report took ... ms` prints.

**Q2.** A teammate applied fix 2, but only halfway. Here is their complete class:

```java
@Component
public class InvoiceService {

    private final InvoiceService self;

    public InvoiceService(@Lazy InvoiceService self) {
        this.self = self;
    }

    public void monthEnd() {
        self.generateInvoice();     // line A
        this.sendInvoice();         // line B
    }

    @Timed("generate")
    public void generateInvoice() {
        System.out.println("invoice generated");
    }

    @Timed("send")
    public void sendInvoice() {
        System.out.println("invoice sent");
    }
}
```

Another bean calls `monthEnd()` once. Which timing lines print — `generate took ... ms`,
`send took ... ms`, both, or neither? Explain line A and line B separately, naming which
object each call lands on.

**Answer.** Only `generate took ... ms` prints. Line A: `self` holds the **proxy**, so the
call leaves the real object, lands on the proxy, the advice fires, and the proxy forwards
back into the real `generateInvoice()` — timed. Line B: `this` is the **real object**, so
`this.sendInvoice()` goes object-to-itself, never touches the proxy, and no advice runs —
`invoice sent` prints with no timing line, and no error. This half-fixed class shows fix
2's weakness: every internal call must remember to use `self.`, and the compiler does not
help. Fix 1 removes the possibility entirely.

### Do it yourself (second laptop)

In the `scratch-app`, create the four files exactly as in section 4: `TotalCalculator.java`,
`CheckoutServiceFixed.java`, `SelfInjectedCheckoutService.java`, `L12FixesDemo.java`, all
in `src/main/java/com/zenith/aop/`. Then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l12-fixes
```

Expected:

```text
--- fix 1: annotated method moved to its own bean ---
fixed checkout: starting
total calculated (own-bean version)
extracted-total took 61 ms
fixed checkout: done
--- fix 2: bean injected into itself ---
self-injected checkout: starting
total calculated (self-injected version)
self-injected-total took 65 ms
self-injected checkout: done
```

Break experiment: remove `@Lazy` from the self-injection constructor and run. Expected:

```text
Error creating bean with name 'selfInjectedCheckoutService': Requested bean is currently in creation:
Is there an unresolvable circular reference or an asynchronous initialization dependency?
APPLICATION FAILED TO START
```

Put `@Lazy` back and re-run.

**Done when:** both timing lines print, and you have written one sentence: which fix is
your default, and why.

### Summary

- Both fixes reroute the call across a proxy; neither tricks the aspect.
- Fix 1 (own bean): the annotated method moves out; callers inject the new bean's proxy;
  self-invocation becomes structurally impossible. Default choice.
- Fix 2 (self-injection): the bean receives its own proxy via a `@Lazy` constructor
  parameter; internal calls must use `self.`, and nothing enforces it — the back door
  stays.
- Self-injection without `@Lazy` fails loudly at startup: circular reference.
- The rule never changed: advice runs only on calls that cross the proxy.

---

## Lesson 13 — What the proxy really is (subtask S8)

### 1. What is it?

The proxy is not magic — it is a real Java class that Spring *generates at startup*.
With CGLIB (Spring Boot's default), the generated class is a **subclass of your class**,
named like `BookingService$$SpringCGLIB$$0`. The other way to build one, a JDK proxy,
*implements your interface* instead. This lesson shows the generated class with your own
eyes and the one thing a subclass can never do — which is exactly why `final` and
`private` methods cannot be advised.

### 2. Why do we need it?

Three reasons. It turns the two-objects story from "trust me" into evidence you printed
yourself. It explains checkpoint question 6 (`final`/`private`). And `$$SpringCGLIB$$`
appears in stack traces for the rest of your career — it already did, in the Lesson 9
crash — so you should know exactly what it is.

### 3. How does it work?

At startup, for a bean that needs advice, Spring generates bytecode for a brand-new
class, roughly this shape:

```java
// generated at runtime - you never write this
public class BookingService$$SpringCGLIB$$0 extends BookingService {

    // holds the real BookingService inside

    @Override
    public void reserveSeat() {
        // run the advice that matches this method,
        // which calls proceed() -> forwards to the real object's reserveSeat()
    }

    // ...same for every method it can override...
}
```

The key word is `extends`. The proxy is a subclass, and interception is done by
**overriding**. Each override routes the call into the advice; the advice's `proceed()`
forwards to the real object held inside.

Everything follows from one Java rule — *what cannot be overridden cannot be
intercepted*:

- A **`final` method** cannot be overridden. The generated subclass must leave it alone.
  Calling it on the proxy runs the plain inherited code — no advice, ever.
- A **`private` method** is invisible to subclasses. No override is possible, so no
  interception is possible. (`@Transactional` on a private method is ink on paper.)

The JDK proxy branch: if a bean has an interface, Spring *can* instead generate a class
that `implements` the interface and routes every interface method to advice. No
subclassing, so `final` does not matter there — but the bean can only be called through
the interface type. Spring Boot's default is CGLIB subclassing for everything,
interfaces or not.

### 4. Simple example

Two new files. A bean whose only method is `final` and marked:

```java
// src/main/java/com/zenith/aop/FinalMethodService.java
package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

@Component
public class FinalMethodService {

    @Timed("final-export")
    public final void export() throws InterruptedException {
        Thread.sleep(50);
        System.out.println("export finished");
    }
}
```

And a demo that asks three beans what class they really are:

```java
// src/main/java/com/zenith/aop/L13ProxyRevealDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l13-proxy")
public class L13ProxyRevealDemo implements CommandLineRunner {

    private final BookingService bookingService;
    private final TimingAspect timingAspect;
    private final FinalMethodService finalMethodService;

    public L13ProxyRevealDemo(BookingService bookingService,
                              TimingAspect timingAspect,
                              FinalMethodService finalMethodService) {
        this.bookingService = bookingService;
        this.timingAspect = timingAspect;
        this.finalMethodService = finalMethodService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- what did Spring actually hand us? ---");
        System.out.println("bookingService class:      " + bookingService.getClass().getName());
        System.out.println("bookingService superclass: " + bookingService.getClass().getSuperclass().getName());
        System.out.println("timingAspect class:        " + timingAspect.getClass().getName());
        System.out.println("finalMethodService class:  " + finalMethodService.getClass().getName());

        System.out.println("--- calling the final @Timed method ---");
        finalMethodService.export();
        System.out.println("(look: no timing line above)");
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l13-proxy`

Real output:

```text
--- what did Spring actually hand us? ---
bookingService class:      com.zenith.aop.BookingService$$SpringCGLIB$$0
bookingService superclass: com.zenith.aop.BookingService
timingAspect class:        com.zenith.aop.TimingAspect
finalMethodService class:  com.zenith.aop.FinalMethodService$$SpringCGLIB$$0
--- calling the final @Timed method ---
export finished
(look: no timing line above)
```

### 5. Walkthrough

Each output line confirms a piece of the story:

- `BookingService$$SpringCGLIB$$0` — the object in the field **is not your class**. It
  is the generated class. That name is the proof the task sent you hunting for.
- `superclass: com.zenith.aop.BookingService` — and it *extends* your class. Subclass
  confirmed; the Decorator pattern, written in bytecode at startup.
- `timingAspect class: com.zenith.aop.TimingAspect` — plain name, no `$$SpringCGLIB$$`.
  Spring only wraps beans that need wrapping. The aspect holds advice; nothing advises
  *it*; no proxy.
- `finalMethodService class: ...$$SpringCGLIB$$0` — this bean *was* proxied (its method
  matched the pointcut, so Spring built the wrapper). But —
- `export finished` with **no timing line**. The one method is `final`. The generated
  subclass could not override it, so the call fell straight through to the inherited
  code. Proxy present, advice impossible.

The sharpest part: the entire startup log contains no complaint — even with Spring's
AOP logging turned up to TRACE, nothing mentions the skipped method. On this version
(Spring Boot 3.5.16), a `final` method on an advised bean is skipped in **total
silence**.

Connecting backwards: the Lesson 9 crash trace said
`at com.zenith.aop.BookingService$$SpringCGLIB$$0.reserveSeat(<generated>)`. Same name,
same object — the stack trace was showing the generated subclass forwarding into the
advice.

### 6. Where is it used?

The generated-subclass trick is one of the most reused ideas in the Java world:

| Where | The generated stand-in |
|---|---|
| Spring AOP (this task) | `YourService$$SpringCGLIB$$0 extends YourService` |
| Hibernate lazy loading (F6.11) | a generated subclass of your entity that fetches data on first touch |
| Mockito mocks in tests | a generated subclass of the class you mock, overriding methods to return canned values |
| Every stack trace through an advised bean | the `$$SpringCGLIB$$` frame from Lesson 9 |

### 7. Common mistakes

**Mistake 1 — `final` out of habit on bean methods (or the whole class).** Some styles
mark everything `final` by default. On a Spring bean, every `final` method silently
becomes un-advisable — no timing, no transaction, no warning. Keep bean methods
non-final.

**Mistake 2 — `@Transactional` or `@Timed` on a `private` method.** Compiles, does
nothing, forever. A subclass cannot override what it cannot see. If a method needs
advice, it must be visible and overridable — in practice: `public`, not `final`.

**Mistake 3 — assuming `getClass()` gives your class.** On an advised bean it gives the
generated class. Code that compares classes by name, or reflects on `getClass()`, can
break strangely. Spring offers `AopUtils.getTargetClass(bean)` when the real one is
truly needed.

**Mistake 4 — relying on constructor side effects.** Spring creates the proxy *instance*
without running your constructor (a library called Objenesis conjures it). Your
constructor runs once, for the real object only. One consequence worth knowing: when a
`final` method falls through, it executes *on the proxy instance*, whose own fields were
never initialized — a `final` method that reads a field can see `null` where a value was
expected. One more reason to never mix `final` with advised beans.

### 8. Link to the task

Subtask S8, word for word: "Print `service.getClass().getName()` and find
`$$SpringCGLIB$$` in it. That object is not your class. Now make a method `final` and
confirm it can no longer be advised, and explain why from what you just saw." All three
done: the name printed, the superclass printed, the `final` method ran untimed. The
explanation from what was seen: the proxy is a *subclass*, interception is *overriding*,
and `final` forbids overriding. Checkpoint question 6 is answered, and checkpoint
question 5 gets its proof: the Decorator pattern, generated for you.

### 9. Check my understanding

**Question 1.** A teammate debugs a Spring app and prints two lines about one injected
bean:

```text
paymentService class:      com.shop.PaymentService$$SpringCGLIB$$0
paymentService superclass: com.shop.PaymentService
```

The bean looks like this:

```java
@Component
public class PaymentService {

    @Cacheable("rates")
    public final BigDecimal exchangeRate(String currency) {
        System.out.println("fetching rate from the slow remote service...");
        return remoteCall(currency);
    }
}
```

The teammate complains: "Caching never works — the slow line prints on every call. But
the printout proves the proxy exists!" Explain: (a) why the proxy existing is not
enough, using the words subclass and override; (b) what single-keyword change fixes the
caching; (c) why no error or warning ever appeared.

**Answer.** (a) The proxy is a generated **subclass**, and it intercepts a method by
**overriding** it. `exchangeRate` is `final`, so the subclass is forbidden from
overriding it. The wrapper object exists, but calls to that method fall straight through
to the inherited, un-intercepted code — the caching advice never gets a chance to run.
(b) Delete the word `final` from the method. Then the subclass can override it, the
override routes through the caching advice, and the cache works. (c) Because Spring
skips un-overridable methods silently: the code compiles, the app starts, the method
runs correctly — it is just never advised. On this Spring Boot version, not even
TRACE-level AOP logging mentions it.

**Question 2.** In one Spring app, a demo prints the classes of two injected beans:

```text
orderService class:   com.shop.OrderService$$SpringCGLIB$$0
mailFormatter class:  com.shop.MailFormatter
```

`OrderService` has a method marked `@Timed`; `MailFormatter` is a plain `@Component`
with ordinary unannotated methods, and no aspect's pointcut matches anything in it. Why
did one bean get a generated class and the other keep its own? What general rule about
Spring and proxies does this pair of lines demonstrate?

**Answer.** `OrderService` has a method that matches a pointcut, so Spring had to put
advice between callers and that method — it generated the subclass wrapper and
registered *that* as the bean. `MailFormatter` matches nothing and needs nothing between
itself and its callers, so Spring registered the plain object as-is. The general rule:
proxying is **per bean and on demand**. Spring does not wrap everything; it wraps
exactly those beans where at least one piece of advice (or another proxy-needing
feature) applies, and leaves the rest untouched.

### Do it yourself (second laptop)

In the `scratch-app`, create the two files exactly as in section 4:
`FinalMethodService.java` and `L13ProxyRevealDemo.java`, both in
`src/main/java/com/zenith/aop/`. Then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l13-proxy
```

Expected (the `$$SpringCGLIB$$0` suffix number may differ):

```text
--- what did Spring actually hand us? ---
bookingService class:      com.zenith.aop.BookingService$$SpringCGLIB$$0
bookingService superclass: com.zenith.aop.BookingService
timingAspect class:        com.zenith.aop.TimingAspect
finalMethodService class:  com.zenith.aop.FinalMethodService$$SpringCGLIB$$0
--- calling the final @Timed method ---
export finished
(look: no timing line above)
```

Then one variation: remove the word `final` from `export()` and run again. Expected:
`final-export took 54 ms` appears (give or take a few ms) — the subclass can override
again, so the advice is back. Put `final` back or leave it off; the lesson is the
difference.

**Done when:** you can explain, pointing at the printed class name, why `final` killed
the timing: the proxy is a *subclass*; interception is *overriding*; `final` forbids
overriding.

### Summary

- The proxy is a real generated class: `YourClass$$SpringCGLIB$$0 extends YourClass`.
  Interception = overriding.
- `final` methods cannot be overridden → never advised. `private` methods cannot be seen
  by a subclass → never advised. Both fail in total silence.
- JDK proxies implement an interface instead of subclassing; Spring Boot defaults to
  CGLIB subclassing for all beans.
- Spring proxies per bean, on demand: beans with no matching advice keep their own class
  (the aspect itself printed plain `com.zenith.aop.TimingAspect`).
- `getClass()` on an advised bean returns the generated class — remember
  `AopUtils.getTargetClass(bean)` if the real one is ever needed.

---

## Lesson 14 — One mechanism everywhere (subtask S9)

### 1. What is it?

The payoff lesson. This task built a label (`@Timed`), a reader (`TimingAspect`), and
showed how Spring delivers the reader through a generated proxy. The reveal: **Spring's
own famous annotations are built the exact same way.** `@Transactional`, `@Cacheable`,
and `@Async` are each a RUNTIME-retained label plus a piece of advice that Spring ships
in the box, delivered through the same `$$SpringCGLIB$$` proxy.

### 2. Why do we need it?

It collapses three "mysterious Spring features" into one idea. Every rule learned the
hard way here — RUNTIME retention, calls must cross the proxy, self-invocation is
silent, `final` and `private` escape — applies to Spring's annotations identically.
This is why `@Transactional`'s most famous failure mode becomes explainable from
memory, and it answers the task's "Ask AI" question (subtask S9).

### 3. How does it work?

Each Spring annotation is an `@Around`-style advice with a different body. Compare them
to aspects already written in this task:

| Annotation | What its advice does around `proceed()` | The same shape was written in |
|---|---|---|
| `@Transactional` | open a transaction → `proceed()` → commit; if an exception flies, roll back | Lesson 8 — work before and after `proceed()` |
| `@Cacheable` | check the cache; **hit → return the stored value, never call `proceed()`**; miss → `proceed()`, store the result | Lesson 10 — the `IdempotencyAspect`, almost line for line |
| `@Async` | do not run `proceed()` on this thread at all; hand the work to a thread pool and return immediately | a new body, same wrapper |

Everything around the body is identical machinery:

- The annotation is declared with RUNTIME retention (open `Transactional.java` in an
  IDE — `@Retention(RetentionPolicy.RUNTIME)` is right there).
- Spring's built-in advice reads the mark and its values (propagation settings, cache
  names) the same way `TimingAspect` read `value()`.
- Delivery is the generated subclass proxy. So the advice runs **only when a call
  crosses the proxy** — with the same silent self-invocation hole, and the same
  `final`/`private` blindness.

### 4. Simple example

Proof with `@Async`, because it needs no database: three small files. First, switch on
Spring's built-in reader (this is what `@EnableAsync` does):

```java
// src/main/java/com/zenith/aop/L14AsyncConfig.java
package com.zenith.aop;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@Profile("l14-async")
public class L14AsyncConfig {
}
```

A bean using Spring's `@Async` exactly like `@Timed` was used — including the
self-invocation trap on purpose:

```java
// src/main/java/com/zenith/aop/AsyncMailer.java
package com.zenith.aop;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncMailer {

    @Async
    public void sendWelcomeMail() {
        System.out.println("sending welcome mail on thread: "
                + Thread.currentThread().getName());
    }

    public void register() {
        System.out.println("register() running on thread:   "
                + Thread.currentThread().getName());
        this.sendWelcomeMail(); // self-invocation
    }
}
```

And the demo:

```java
// src/main/java/com/zenith/aop/L14AsyncDemo.java
package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l14-async")
public class L14AsyncDemo implements CommandLineRunner {

    private final AsyncMailer mailer;

    public L14AsyncDemo(AsyncMailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("mailer class: " + mailer.getClass().getName());
        System.out.println("main thread:  " + Thread.currentThread().getName());

        System.out.println("--- calling @Async sendWelcomeMail() from OUTSIDE ---");
        mailer.sendWelcomeMail();
        Thread.sleep(300); // give the background thread time to print

        System.out.println("--- calling register(), which calls this.sendWelcomeMail() INSIDE ---");
        mailer.register();
        Thread.sleep(300);
    }
}
```

Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=l14-async`

Real output:

```text
mailer class: com.zenith.aop.AsyncMailer$$SpringCGLIB$$0
main thread:  main
--- calling @Async sendWelcomeMail() from OUTSIDE ---
sending welcome mail on thread: task-1
--- calling register(), which calls this.sendWelcomeMail() INSIDE ---
register() running on thread:   main
sending welcome mail on thread: main
```

### 5. Walkthrough

Four observations, each confirming "same mechanism":

- `AsyncMailer$$SpringCGLIB$$0` — no hand-written aspect exists for this bean, yet
  there is the generated subclass. Spring's *own* annotation triggered the *same*
  proxy generation as `@Timed` did.
- Called from **outside**: `sending welcome mail on thread: task-1`. The call crossed
  the proxy; Spring's advice intercepted it and handed the work to a pool thread named
  `task-1`. That is all `@Async` is — an advice that refuses to run `proceed()` on the
  caller's thread.
- Called from **inside** via `this.sendWelcomeMail()`: `sending welcome mail on
  thread: main`. The same object-to-itself call as Lesson 11 never touches the proxy,
  so the method ran synchronously on `main`. **Spring's own annotation has exactly the
  bug built by hand in Lesson 11.** No error, no warning.
- Swap the annotation name and this is the famous `@Transactional` failure: a `public`
  method, correctly annotated, called from a sibling method in the same class — and no
  transaction ever opens.

### 6. Where is it used?

Half of Spring's feature list reads as "label + shipped advice + proxy":

| Annotation | The shipped advice's job |
|---|---|
| `@Transactional` | begin / commit / roll back around the method |
| `@Cacheable`, `@CacheEvict` | return stored result instead of proceeding; or clear entries after |
| `@Async` | run the method on a thread pool |
| `@Retryable` (spring-retry) | catch the exception, call `proceed()` again, up to N times |
| `@PreAuthorize` (security) | check permissions before proceeding; throw if denied |

All of them: RUNTIME retention, proxy delivery, self-invocation hole, `final`/`private`
blindness.

### 7. Common mistakes

**Mistake 1 — believing annotations "do" things.** An annotation never does anything.
It is ink. Somewhere there is always a reader — an aspect, a built-in advice, a
framework scanner — and if the reader cannot see the mark (wrong retention, call never
crossed the proxy, `final` method), the ink just sits there.

**Mistake 2 — debugging only your own code.** When `@Transactional` "does nothing,"
the bug is almost never in the method body. Ask the three proxy questions: Is the call
crossing the proxy, or is it `this.…`? Is the method `public` and not `final`? Is the
bean actually a Spring bean (not created with `new`)? Those three catch nearly every
case.

**Mistake 3 — treating `@Async` + `this.` as "slightly less async."** It is not
partially broken; it is *fully synchronous*, silently. Same for a self-invoked
`@Transactional`: not a weaker transaction — no transaction.

### 8. Link to the task

Subtask S9's "Ask AI" question: *"How are `@Transactional`, `@Cacheable`, and `@Async`
implemented in Spring, and what do they have in common with an `@Around` aspect?"*

The answer: **they are all proxies.** Each is a RUNTIME-retained annotation read by a
piece of `@Around`-shaped advice that Spring ships, woven in by the same
generated-subclass proxy printed in Lesson 13. They differ only in the advice body:
manage a transaction, consult a cache before `proceed()`, or move `proceed()` to
another thread. And they inherit every rule proved on `@Timed`: calls must cross the
proxy, self-invocation is silently naked, `final` and `private` are invisible. That one
sentence — *they are all proxies* — explains a surprising amount of Spring at once.

### 9. Check my understanding — the task's checkpoint

The task's own six questions, with full answers.

**Question 1.** What is a cross-cutting concern? Name two from this project that are
not logging.

**Answer.** Work a program needs in many places that is not what any of those places is
*about* — it cuts across the codebase instead of living in one module. Two from this
project that are not logging: **timing/metrics** (the `@Timed` measurements around
booking methods) and **idempotency** (the `@Idempotent` stored-response check around
payment methods). Transactions and security checks are also correct answers.

**Question 2.** Why must a custom annotation be `RetentionPolicy.RUNTIME` for an aspect
to see it, and what is the symptom when it is not?

**Answer.** Only RUNTIME retention makes the compiler store the mark on the
`RuntimeVisibleAnnotations` shelf of the `.class` file — the one shelf reflection
(`getAnnotation(...)`) can read while the program runs. With the default (CLASS)
retention the mark goes to `RuntimeInvisibleAnnotations`, where reflection returns
`null`. The classic symptom is **total silence**: the aspect never fires, no error, no
warning. Measured on this exact setup (Spring Boot 3.5.16), pointcut *matching* still
worked because AspectJ reads the class-file bytes directly — but the moment the advice
read the annotation's values, `getAnnotation(...)` returned `null` and the call crashed
with `NullPointerException: Cannot invoke "Timed.value()" because "timed" is null`.
Either way the rule stands: custom annotations for aspects are always RUNTIME.

**Question 3.** Explain mechanically — in terms of objects and calls — why
self-invocation bypasses an aspect.

**Answer.** For an advised bean there are two objects: the generated proxy P and the
real object R. Spring injects P into every other bean, and the advice lives in P's
overridden methods. An outside call travels caller → P (advice runs) → R. But inside
R's own code, `this` is R itself — a plain `this.method()` call goes R → R directly.
P is never on the path, so the advice never runs. Nothing fails; the call simply never
crossed the proxy.

**Question 4.** `@Transactional` on a method called from within the same class does
nothing at all. Why, and what are the two fixes?

**Answer.** The transaction advice lives in the proxy, and a within-the-class call
(`this.method()`) goes straight from the real object to itself without touching the
proxy — so no transaction opens, silently. Fix 1 (default): move the annotated method
into its own bean and inject that bean; the call then must cross the new bean's proxy.
Fix 2: inject the bean into itself (a `@Lazy` constructor parameter), and make internal
calls through `self.method()` instead of `this.method()` — remembering that nothing
enforces the `self.`, and that without `@Lazy` the app fails at startup with a circular
reference.

**Question 5.** Which design pattern is Spring AOP an implementation of?

**Answer.** The **Decorator pattern** — an object that holds the real object inside,
adds behavior around the delegated call, and is handed to callers in the real object's
place. The hand-made `TimingProxy` from Lesson 6 was that pattern written by hand;
Spring generates the same thing as bytecode at startup. (The sibling name "Proxy
pattern" describes the same structure; decorator = add behavior, proxy = control
access.)

**Question 6.** Why can `final` and `private` methods not be advised?

**Answer.** The CGLIB proxy is a generated **subclass**, and interception works by
**overriding** each method so the override routes into the advice. A `final` method
cannot be overridden, so calls fall straight through to the inherited, un-intercepted
code. A `private` method is invisible to a subclass, so no override can exist at all.
Both are skipped in total silence — on this version not even TRACE-level AOP logging
mentions it.

### Do it yourself (second laptop)

In the `scratch-app`, create the three files from section 4: `L14AsyncConfig.java`,
`AsyncMailer.java`, `L14AsyncDemo.java`, all in `src/main/java/com/zenith/aop/`. Then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l14-async
```

Expected:

```text
mailer class: com.zenith.aop.AsyncMailer$$SpringCGLIB$$0
main thread:  main
--- calling @Async sendWelcomeMail() from OUTSIDE ---
sending welcome mail on thread: task-1
--- calling register(), which calls this.sendWelcomeMail() INSIDE ---
register() running on thread:   main
sending welcome mail on thread: main
```

**Done when:** you can point at `task-1` versus `main` and explain both lines with one
rule: advice runs only on calls that cross the proxy — and Spring's own annotations
obey it too.

### Summary

- `@Transactional`, `@Cacheable`, `@Async` = RUNTIME-retained label + advice Spring
  ships + the same generated proxy. They are all proxies.
- `@Cacheable` is the Lesson 10 skip-`proceed()` trick; `@Transactional` is Lesson 8's
  before/after shape; `@Async` moves `proceed()` to a thread pool.
- Spring's own annotations inherit every limit proved on `@Timed`: self-invocation is
  silently skipped (watched live: `main` instead of `task-1`), `final`/`private` are
  invisible, retention must be RUNTIME.
- When a Spring annotation "does nothing": check the call crosses the proxy, the method
  is `public` and not `final`, and the object is a real Spring bean.

---

## Task complete — the "done when" check

| "Done when" line from the task | Where it happened |
|---|---|
| `@Timed` works | Lesson 8: `checkout took 125 ms` printed by the aspect; duplication deleted |
| You have watched self-invocation silently skip the aspect | Lesson 11: inside call printed no timing line; Lesson 14: `@Async` fell back to `main` the same way |
| You can explain `@Transactional`'s most famous failure mode from memory | Checkpoint question 4 above — say it out loud without looking |

All nine subtasks S1–S9 are done, each proven by a program that ran. The one-sentence
takeaway of the whole task: **an annotation is ink; something must read it; in Spring
the reader is advice delivered by a generated decorator — and advice runs only on calls
that cross it.**

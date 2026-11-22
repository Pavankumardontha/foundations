# F6.13 — Who builds the beans: lifecycle and BeanPostProcessor

**Goal of the task.** Recite the stages a bean goes through between `new` and "ready to
use". Hook into them with `@PostConstruct` and `@PreDestroy`. Answer the question AOP
left open: *who* creates the `$$SpringCGLIB$$` proxy, and *when*.

**Done when:** your log shows constructor → `@PostConstruct` → `@PreDestroy` in order,
your own `BeanPostProcessor` has printed every bean in the application, and you can name
the exact point in the lifecycle where the `@Transactional` proxy is created.

**You need first:** Task F6.1 (what a bean is, what the application context does).
Task F6.12 (the proxy) helps, but every lesson here re-explains what it needs.

**The story in two sentences.** Spring builds every bean through one fixed sequence of
steps, and you can log your way through all of them. The famous "magic" — the proxy that
makes `@Transactional` work — is one specific object doing one specific thing at one
nameable step of that sequence.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Startup** | The time between calling `SpringApplication.run(...)` and the `Started ...` log line. Every bean is built inside this window. |
| **Bean lifecycle** | The fixed sequence every bean goes through on its way into the application context: constructor → dependencies filled → aware callbacks → post-process before init → `@PostConstruct` → post-process after init → ready. |
| **`@PostConstruct`** | A method Spring calls exactly once, after the constructor has run and every dependency is filled. The right home for setup code. |
| **`@PreDestroy`** | The matching exit hook: a method Spring calls once, as the application context shuts down. |
| **`BeanPostProcessor`** | An object Spring hands every bean to, twice — once before and once after initialization. Whatever object it returns is what the context keeps. |
| **Proxy** | A stand-in object that wraps the real one. Callers talk to the proxy; the proxy adds behaviour around the call, then forwards it to the real object. |
| **Self-invocation** | A method of a bean calling another method of the same bean, through `this`, instead of going through the context. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Log a line from a constructor, from `@PostConstruct`, and from `@PreDestroy`. Read the order out of the log. Say why setup belongs in `@PostConstruct`, not the constructor. |
| S2 | Write a trivial `BeanPostProcessor` that logs every bean name passing through. Watch Spring assemble the application. |
| S3 | The punchline experiment: print `bean.getClass()` from both hooks. Plain class before initialization; `$$SpringCGLIB$$` proxy after. |
| S4 | Write the sentence: "the proxy is created by a `BeanPostProcessor` during bean initialization, which is why self-invocation bypasses it: the raw object never knows the proxy exists." |
| CP | Checkpoint: five questions, out loud. |

## Roadmap for this task — 10 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | Spring calls your constructor | The exact moment your object is born, and who makes it. | S1 |
| 2 | The too-early problem | A dependency still missing while the constructor runs — the pain the next lesson fixes. | S1 |
| 3 | `@PostConstruct` — the setup hook | The method Spring calls when wiring is done. | S1 |
| 4 | `@PreDestroy` — the exit hook | The method Spring calls at shutdown; completes the S1 log. | S1 |
| 5 | The full lifecycle map | Every stage named, in order, including the two post-process slots. | CP1 |
| 6 | `BeanPostProcessor` — watching every bean | One class that sees every bean in the app go past. | S2 |
| 7 | `BeanPostProcessor` — swapping a bean | Return a different object and the context keeps it. The key to all Spring magic. | S3 |
| 8 | A `@Transactional` service joins the app | The database bits, and meeting `$$SpringCGLIB$$` yourself. | S3 |
| 9 | The punchline — who creates the proxy, and when | Plain class before init, proxy after. The answer to F6.12's open question. | S3 |
| 10 | Why self-invocation bypasses the proxy | Proof with a running transaction check, and the sentence for your notes. | S4 |

The scratch app for this task lives in
`F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`. It starts from
the smallest Spring Boot there is: `spring-boot-starter`, no web, no database. Each
lesson's demo is switched on by its own profile, so one run shows one lesson and nothing
else.

---

## Lesson 1 — Spring calls your constructor (serves S1)

### Definition

A **bean** is an object that Spring creates and manages for you. The **application
context** is the object that holds all beans. Lesson 1 is one fact: you never write
`new` for a bean. Spring calls the constructor itself, during startup.

### Why it is needed

This whole task asks: what happens between `new` and "ready to use"? To answer that, you
must first catch the `new` itself. It does not happen in your code. You can search the
project for `new BookingService()` and find nothing — yet the object exists. Someone
called its constructor. Until you know who and when, everything that follows (setup
hooks, proxies) stays magic. So lesson 1 pins down the who and the when, with a log line
you can see.

### How it works

1. Your `main` method calls `SpringApplication.run(...)`.
2. `run` creates the application context. It is empty at first.
3. Spring scans your packages. It looks for classes marked `@Component`. `@Component`
   means: "make one bean from this class."
4. For each class it found, Spring calls the constructor. Your object now exists.
5. More steps follow after the constructor (lessons 2 to 7).
6. When every bean is ready, `run` returns. The log prints `Started ...`.

**Startup** is the time between step 1 and step 6. The constructor runs *inside*
startup — not when the bean is first used.

### The example — one bean that shouts at birth

`pom.xml` — the smallest Spring Boot there is:

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
    <artifactId>lifecycle-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
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

`src/main/java/com/zenith/lifecycle/LifecycleApplication.java` — prints one line before
startup and one line after:

```java
package com.zenith.lifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LifecycleApplication {

    public static void main(String[] args) {
        System.out.println(">>> main: before SpringApplication.run — no beans exist yet");
        SpringApplication.run(LifecycleApplication.class, args);
        System.out.println(">>> main: run() returned — every bean is built and ready");
    }
}
```

`src/main/java/com/zenith/lifecycle/L1BookingCounter.java` — one bean whose only job is
to shout when its constructor runs:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l1-constructor")
class L1BookingCounter {

    L1BookingCounter() {
        System.out.println(">>> constructor: Spring is running `new L1BookingCounter()` right now");
    }
}
```

A **profile** is a named switch for the app. `@Profile("l1-constructor")` means: use
this class only when the switch `l1-constructor` is on. The run command turns it on.
Each lesson in this task gets its own switch, so one run shows one lesson.

### Walkthrough — the real run

Command:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-constructor
```

Real output (timestamps and paths trimmed):

```
>>> main: before SpringApplication.run — no beans exist yet
 :: Spring Boot ::               (v3.5.16)
... INFO ... : Starting LifecycleApplication using Java 21.0.11 ...
... INFO ... : The following 1 profile is active: "l1-constructor"
>>> constructor: Spring is running `new L1BookingCounter()` right now
... INFO ... : Started LifecycleApplication in 0.295 seconds
>>> main: run() returned — every bean is built and ready
```

Read it top to bottom:

1. `main: before ...` prints first. Our code is running. No beans exist.
2. `SpringApplication.run` starts. The banner and the first INFO lines appear.
3. The constructor line prints. **We never called it.** No line of our code says
   `new L1BookingCounter()`. Spring found the class through `@Component` and called the
   constructor itself.
4. Only after that, `Started LifecycleApplication` prints. Startup is done.
5. `main: run() returned` prints last. Our code gets control back only when every bean
   is built.

### Where it is used

Everywhere in Spring. Every class marked `@Component`, `@Service`, `@Repository`,
`@Controller`, or `@Configuration` is built exactly this way. Every service and
repository in the F6.10 and F6.11 apps was born inside `SpringApplication.run`, by a
constructor call that Spring made.

### Common mistakes

- **Calling `new` yourself and expecting the bean.** `new BookingCounter()` in your own
  code makes a plain object. Spring never sees it. It gets no dependencies and none of
  the later lifecycle steps. The bean in the application context and your hand-made
  object are two different objects.
- **Thinking the constructor runs at first use.** For a normal bean it runs at startup,
  even if nothing ever uses the bean. That is why one log line per constructor is enough
  to watch Spring work.

### Link to the task

Subtask S1 says: "log a line from the constructor too." That line now exists, and the
log shows when Spring calls it. Lessons 2–4 add the `@PostConstruct` and `@PreDestroy`
lines around it, which completes S1.

### Do it yourself

**Goal:** see the constructor line print inside startup, between the "profile is
active" line and the "Started" line.

**Step 0 — check the tools.**

```bash
java -version    # expect: openjdk version "21..."
mvn -version     # expect: Apache Maven 3.9...
```

If either is missing on a Mac: `brew install openjdk@21 maven`. Docker is **not** needed
for this lesson.

**Step 1 — make the folders.**

```bash
mkdir -p ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app/src/main/java/com/zenith/lifecycle
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
```

**Step 2 — create the three files** exactly as shown in "The example" section above:
`pom.xml` in the `scratch-app` folder, and the two Java files in
`src/main/java/com/zenith/lifecycle/`.

**Step 3 — run.**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-constructor
```

The first run downloads many libraries. It can take a few minutes. It needs internet.

**Expected output (success):** the constructor line between "profile is active" and
"Started", with the two `main:` lines first and last:

```
>>> main: before SpringApplication.run — no beans exist yet
... INFO ... : The following 1 profile is active: "l1-constructor"
>>> constructor: Spring is running `new L1BookingCounter()` right now
... INFO ... : Started LifecycleApplication in 0.295 seconds
>>> main: run() returned — every bean is built and ready
```

**Expected failures and what they mean:**

| Symptom | Meaning | Fix |
| --- | --- | --- |
| `command not found: mvn` | Maven is not installed. | `brew install maven` |
| `release version 21 not supported` | Maven is using an old Java. | `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Run works, constructor line missing | The profile switch is off, so Spring skipped the class. | Add `-Dspring-boot.run.profiles=l1-constructor` |

**Done when:** the constructor line prints *between* the "profile is active" line and
the "Started" line.

### Practice questions

**Question 1.** Here is one complete file:

```java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@Component
class Bell {
    Bell() {
        System.out.println("A");
    }
}

@SpringBootApplication
public class TinyApp {
    public static void main(String[] args) {
        System.out.println("B");
        SpringApplication.run(TinyApp.class, args);
        System.out.println("C");
    }
}
```

In what order do A, B, C print? And who calls `new Bell()` — your code, or Spring?

*Answer:* The order is **B, A, C**. `B` prints first: `main` runs before
`SpringApplication.run` is called, and no beans exist yet. `A` prints second, during
startup: Spring scans the package, finds `Bell` marked `@Component`, and calls
`new Bell()` itself. `C` prints last: `run` returns only after every bean is built.
Nobody in this file calls `new Bell()` — **Spring** calls it, inside
`SpringApplication.run`.

**Question 2.** Here is another complete file:

```java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@Component
class MailSender {
    MailSender() {
        System.out.println("MailSender constructor");
    }
}

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        MailSender mine = new MailSender();
    }
}
```

How many times does `MailSender constructor` print? And which of the two MailSender
objects does the application context hold — the first one, the second one, or both?

*Answer:* It prints **twice**. The first print happens during startup: Spring finds
`MailSender` through `@Component` and constructs the bean. The second print happens
after startup, when `main` runs `new MailSender()` by hand. The application context
holds **only the first object** — the one Spring made. The hand-made object `mine` is a
plain object. Spring never sees it, never fills its dependencies, and never applies any
later lifecycle step to it.

### Summary for revision

- A bean is born during **startup**, inside `SpringApplication.run`.
- **Spring** calls the constructor. Your code never writes `new` for a bean.
- The constructor runs at startup, not at first use.
- `new ByHand()` in your own code creates a second, unmanaged object — not the bean.
- Proof: one `System.out.println` in the constructor, printed between the "profile is
  active" line and the "Started" line.

---

## Lesson 2 — The too-early problem (serves S1)

### Definition

A **dependency** is another bean that your bean needs to do its job. **Injection** is
Spring filling in those dependencies for you. The too-early problem is one fact: inside
the constructor, injection is not finished yet. A dependency that arrives through a
field is still `null` while the constructor runs.

### Why it is needed

Setup code — warming a cache, checking a config value, counting rows — looks like it
belongs in the constructor. This lesson shows why that home is unsafe when the setup
touches dependencies. The dependency may simply not be there yet. The setup runs on
`null` and crashes the whole startup. The next lesson gives the safe home for setup
code. This lesson earns it by showing the pain first.

### How it works

Spring builds a bean in steps. This lesson needs the first two:

1. **Instantiate.** Spring calls the constructor. The object now exists, but it is raw.
2. **Populate dependencies.** Spring fills in what the bean asked for. Only now are
   `@Autowired` fields set.

The constructor belongs to step 1. Field filling belongs to step 2. So inside the
constructor, an `@Autowired` field is always still `null`.

One honest side note. With constructor injection (F6.2), dependencies arrive as
constructor parameters, and those parameters *are* usable inside the constructor —
Spring had to build them first to call the constructor at all. But that guarantee covers
only the parameters themselves. The official "everything is filled, I promise" moment
comes later (Lesson 3).

### The example — watch the field change

Two beans, both on profile `l2-too-early`.

`src/main/java/com/zenith/lifecycle/L2SeatMap.java` — the dependency:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l2-too-early")
class L2SeatMap {

    int seats() {
        return 40;
    }
}
```

`src/main/java/com/zenith/lifecycle/L2BookingCounter.java` — asks for the seat map
through a field. Field injection is discouraged in real code; it is used here on purpose
because it makes the too-early moment visible:

```java
package com.zenith.lifecycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l2-too-early")
class L2BookingCounter implements CommandLineRunner {

    @Autowired
    private L2SeatMap seatMap;

    L2BookingCounter() {
        System.out.println(">>> constructor: seatMap = " + seatMap);
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():       seatMap = " + seatMap + " — " + seatMap.seats() + " seats");
    }
}
```

A `CommandLineRunner` is a bean with a `run` method that Spring calls once, after
startup is finished — after every bean is built. It gives a safe "later" moment to look
at the same field again.

### Walkthrough — two real runs

**Run 1 — watch the field change.**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-too-early
```

Real output, trimmed to our lines:

```
>>> main: before SpringApplication.run — no beans exist yet
>>> constructor: seatMap = null
>>> run():       seatMap = com.zenith.lifecycle.L2SeatMap@3b65e559 — 40 seats
>>> main: run() returned — every bean is built and ready
```

The constructor sees `null` (step 1: the object is raw). `run()` sees a real object and
uses it (step 2 happened in between). No code of ours assigned the field. Spring did,
after the constructor returned.

**Run 2 — the pain, for real.** Change the constructor so it *uses* the dependency:

```java
L2BookingCounter() {
    System.out.println(">>> constructor: we have " + seatMap.seats() + " seats");
}
```

The app refuses to start. The real error, trimmed:

```
ERROR ... : Application run failed
org.springframework.beans.factory.BeanCreationException:
  Error creating bean with name 'l2BookingCounter' ...
  Failed to instantiate [com.zenith.lifecycle.L2BookingCounter]: Constructor threw exception
Caused by: java.lang.NullPointerException:
  Cannot invoke "com.zenith.lifecycle.L2SeatMap.seats()" because "this.seatMap" is null
```

| Line in the error | Plain English |
| --- | --- |
| `Error creating bean with name 'l2BookingCounter'` | Spring was mid-way through building this bean when things broke. |
| `Failed to instantiate ... Constructor threw exception` | The break happened in step 1, inside the constructor. |
| `Cannot invoke "L2SeatMap.seats()" because "this.seatMap" is null` | The field was still empty. Step 2 had not run yet. |
| `Application run failed` | One bean failing to build stops the entire startup. |

### Where it is used

This rule decides where setup code may live in every real Spring app. Any bean that
opens a connection, loads a file, or reads another bean's data "at birth" faces this
trap. It also explains bugs in older codebases, where `@Autowired` fields are common: a
constructor that touches such a field works never, not sometimes.

### Common mistakes

- **Doing real setup in the constructor.** Anything that touches an injected dependency
  can hit `null` there.
- **"Fixing" the null with `new`.** `this.seatMap = new L2SeatMap();` in the constructor
  makes the error go away — and silently wrong code appear. The hand-made object is not
  the bean. Two seat maps now exist in one app.
- **Over-trusting the constructor because of constructor injection.** The constructor
  parameters are safe. The bean as a whole is not guaranteed fully filled until later.
  Safe habit: constructors only store what they were handed; real setup waits for the
  Lesson 3 hook.

### Link to the task

Subtask S1 ends with: "say out loud why setup that uses injected dependencies belongs in
`@PostConstruct` rather than the constructor." This lesson is that "why", seen live. It
is also checkpoint question 5.

### Do it yourself

**Goal:** see `seatMap = null` in the constructor, a filled `seatMap` after startup,
then crash the startup on purpose, read the error, and repair it.

Continues in the same project: `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create the two files** in `src/main/java/com/zenith/lifecycle/`, exactly as
shown in "The example" section above: `L2SeatMap.java` and `L2BookingCounter.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-too-early
```

Expected: the four `>>>` lines, with `constructor: seatMap = null` and a filled object
in `run()`. The object id after the `@` differs per machine.

**Step 3 — break it on purpose.** Change the constructor body to
`System.out.println(">>> constructor: we have " + seatMap.seats() + " seats");` and run
again. Expected: `Application run failed`, `Error creating bean with name
'l2BookingCounter'`, and at the bottom `NullPointerException: Cannot invoke
"com.zenith.lifecycle.L2SeatMap.seats()" because "this.seatMap" is null`.

**Step 4 — repair.** Put the constructor body back to
`System.out.println(">>> constructor: seatMap = " + seatMap);` and run again. The
success output returns.

**Done when:** you saw `null` in the constructor, the filled object in `run()`, and the
real `NullPointerException` — and you can say which lifecycle step was missing when it
crashed (step 2, populate dependencies).

### Practice questions

**Question 1.** Here is a complete file:

```java
package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class Ink {
    String color() {
        return "black";
    }
}

@Component
class Printer {

    @Autowired
    private Ink ink;

    Printer() {
        System.out.println("constructor: ink = " + ink);
    }
}
```

The app starts without errors. What exactly does the constructor line print, and why?

*Answer:* It prints `constructor: ink = null`. The constructor runs in step 1
(instantiate). `@Autowired` fields are filled in step 2 (populate dependencies), which
happens only after the constructor returns. Whether or not the `Ink` bean itself already
exists somewhere in the context makes no difference — this `Printer` object's field has
not been filled yet.

**Question 2.** Here is another complete file. `OfficeDesk` gets one dependency through
a constructor parameter and one through a field:

```java
package demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class Scanner {
    String name() {
        return "scanner-1";
    }
}

@Component
class Copier {
    String name() {
        return "copier-1";
    }
}

@Component
class OfficeDesk {

    @Autowired
    private Copier copier;

    private final Scanner scanner;

    OfficeDesk(Scanner scanner) {
        this.scanner = scanner;
        // line X runs here
    }
}
```

At line X, which dependency can be used safely: `scanner`, `copier`, both, or neither?
Why?

*Answer:* Only `scanner`. It arrived as a constructor parameter, so Spring had to build
it first and hand it in — at line X it is a real, usable object. `copier` arrives
through a field. Fields are filled in step 2, after the constructor returns. At line X,
`copier` is still `null`.

### Summary for revision

- Step 1: constructor (the object is raw). Step 2: dependencies are filled. In between,
  `@Autowired` fields are `null`.
- Setup code that touches dependencies must not run in the constructor.
- Constructor injection makes the *parameters* safe inside the constructor — nothing
  more.
- The crash to recognise: `Failed to instantiate ... Constructor threw exception`,
  caused by `NullPointerException ... because "this.field" is null`.
- Never "fix" it with `new` — that creates a second, unmanaged object.

---

## Lesson 3 — `@PostConstruct`: the setup hook (serves S1)

### Definition

`@PostConstruct` is a mark you put on one method of a bean. Spring calls that method
exactly once, after the constructor has run **and** after every dependency is filled. It
is the official "everything is wired, go ahead" moment.

### Why it is needed

Setup code that touches dependencies cannot live in the constructor — the dependencies
may still be missing there, and the startup crashes on `null`. So we need a place that
is guaranteed to run *after* the filling step. Without such a hook, people invent
workarounds: "init" methods every caller must remember to call, or null-checks sprinkled
through the class. `@PostConstruct` removes all of that. You mark one method. Spring
promises: when this runs, injection is finished.

### How it works

The build steps for one bean, as far as this lesson needs them:

1. **Instantiate.** Spring calls the constructor. The object exists, but it is raw.
2. **Populate dependencies.** Spring fills every `@Autowired` field and any other
   injected value.
3. **Initialize.** Spring looks for a method marked `@PostConstruct` and calls it now.

Rules of the hook:

- It runs **once** per bean. Never again.
- The method must take **no parameters**.
- The method name is your choice: `prepare`, `init`, `warmUp` — anything.
- The import is `jakarta.annotation.PostConstruct`.

### The example — the fix for the too-early problem

Two beans on profile `l3-postconstruct`.

`src/main/java/com/zenith/lifecycle/L3SeatMap.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l3-postconstruct")
class L3SeatMap {

    int seats() {
        return 40;
    }
}
```

`src/main/java/com/zenith/lifecycle/L3BookingCounter.java` — the real setup lives in
`prepare()`:

```java
package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l3-postconstruct")
class L3BookingCounter implements CommandLineRunner {

    @Autowired
    private L3SeatMap seatMap;

    private int freeSeats;   // filled by the setup method

    L3BookingCounter() {
        System.out.println(">>> constructor:    seatMap = " + seatMap);
    }

    @PostConstruct
    void prepare() {
        this.freeSeats = seatMap.seats();   // safe: injection is finished here
        System.out.println(">>> @PostConstruct: seatMap = " + seatMap + " — setup done, " + freeSeats + " seats free");
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():          the app is working, freeSeats = " + freeSeats);
    }
}
```

### Walkthrough — the real run

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-postconstruct
```

Real output, trimmed to our lines:

```
>>> main: before SpringApplication.run — no beans exist yet
>>> constructor:    seatMap = null
>>> @PostConstruct: seatMap = com.zenith.lifecycle.L3SeatMap@7c18432b — setup done, 40 seats free
>>> run():          the app is working, freeSeats = 40
>>> main: run() returned — every bean is built and ready
```

The same field, at three moments:

1. **Constructor:** `seatMap = null`. Raw object, nothing filled.
2. **`@PostConstruct`:** the field is a real object, and `seatMap.seats()` works. This
   exact call crashed the app when it sat in the constructor (Lesson 2's run 2). Moved
   here, it is safe.
3. **`run()`:** the prepared value `freeSeats = 40` is ready before the app does any
   work.

### Where it is used

Warming a cache from the database. Checking that a required config value makes sense.
Registering the service with a monitoring system. Starting a background scheduler.
Anything that needs the bean's dependencies, exactly once, before the app starts
serving.

### Common mistakes

- **Giving the method a parameter.** Tried for real: `void prepare(String who)`. The app
  refused to start:

  ```
  Caused by: java.lang.IllegalStateException: Lifecycle annotation requires a no-arg method:
    void com.zenith.lifecycle.L3BookingCounter.prepare(java.lang.String)
  ```

  "No-arg" means no arguments. The hook method must take nothing.

- **Using the old import.** Old tutorials say `javax.annotation.PostConstruct`. In
  Spring Boot 3 that package is gone. Tried for real:

  ```
  [ERROR] ... L3BookingCounter.java:[3,24] package javax.annotation does not exist
  ```

  The correct import today is `jakarta.annotation.PostConstruct`.

- **Expecting the hook on a hand-made object.** `new L3BookingCounter()` creates a plain
  object. Spring never sees it, so `prepare()` never runs on it. The lifecycle only
  happens to beans.

- **Doing slow work there without thinking.** The whole startup waits for every
  `@PostConstruct` to finish.

### Link to the task

Subtask S1 asks for a `@PostConstruct` log line, and for this sentence out loud: *setup
that uses injected dependencies belongs in `@PostConstruct`, not the constructor — at
construction time, injection is not yet guaranteed to be finished; by `@PostConstruct`,
it is.* Both halves of that sentence are visible in the walkthrough log. This is also
checkpoint question 5.

### Do it yourself

**Goal:** see constructor → `@PostConstruct` → `run()` in order, then hit both classic
errors and repair them.

Continues in `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create the two files** in `src/main/java/com/zenith/lifecycle/`, exactly as
shown in "The example" section above: `L3SeatMap.java` and `L3BookingCounter.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-postconstruct
```

Expected: the five `>>>` lines in the walkthrough order. The object id after the `@`
differs per machine.

**Step 3 — break it, two ways.**

1. Change `void prepare()` to `void prepare(String who)`. Run. Expected:
   `Application run failed`, caused by `IllegalStateException: Lifecycle annotation
   requires a no-arg method`. Change it back.
2. Change the import to `javax.annotation.PostConstruct`. Run. Expected compile error:
   `package javax.annotation does not exist`. Change it back.

**Step 4 — run once more.** The success output returns.

**Done when:** the log shows constructor → `@PostConstruct` → `run()` in order, and you
have seen and repaired both errors.

### Practice questions

**Question 1.** Here is a complete file:

```java
package demo;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class Engine {
    String status() {
        return "ready";
    }
}

@Component
class Car {

    @Autowired
    private Engine engine;

    Car() {
        System.out.println("one: " + engine);
    }

    @PostConstruct
    void init() {
        System.out.println("two: " + engine.status());
    }
}
```

The app starts without errors. What do the two lines print, in order? And why is
`engine.status()` safe inside `init()`, when the same call inside the constructor would
crash?

*Answer:* First `one: null`, then `two: ready`. The constructor runs in step 1
(instantiate), before fields are filled, so `engine` is `null` there — printing `null`
is harmless, but *calling a method on it* would crash with a `NullPointerException`.
`init()` is marked `@PostConstruct`, so Spring calls it in step 3 (initialize), after
step 2 (populate dependencies) filled the field. By then `engine` is a real object, so
`engine.status()` returns `"ready"`.

**Question 2.** Here is another complete file:

```java
package demo;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@Component
class Cache {

    @PostConstruct
    void warmUp() {
        System.out.println("cache warmed");
    }
}

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        Cache byHand = new Cache();
    }
}
```

How many times does `cache warmed` print — zero, one, or two times? Why?

*Answer:* **Once.** During startup, Spring finds `Cache` through `@Component`, builds
the bean, and runs its lifecycle — including the `@PostConstruct` method. That is the
one print. The hand-made `new Cache()` creates a plain object outside Spring. No
lifecycle steps happen to it, so its `warmUp()` never runs. (Two would require Spring to
see the hand-made object; zero would require Spring to skip its own bean.)

### Summary for revision

- `@PostConstruct` marks one no-parameter method; Spring calls it exactly once, after
  the constructor and after all injection.
- Order so far: constructor → dependencies filled → `@PostConstruct` → app runs.
- Setup that uses dependencies goes here, never in the constructor.
- Import `jakarta.annotation.PostConstruct` (the `javax` one no longer exists).
- Errors to recognise: `Lifecycle annotation requires a no-arg method` (parameter on the
  hook), `package javax.annotation does not exist` (old import).
- The hook never runs on hand-made `new` objects — the lifecycle only happens to beans.

---

## Lesson 4 — `@PreDestroy`: the exit hook (serves S1, completes it)

### Definition

`@PreDestroy` is a mark you put on one method of a bean. Spring calls that method
exactly once, when the application is stopping. It is the mirror of `@PostConstruct`:
one hook when the bean becomes ready, one hook before it is thrown away.

### Why it is needed

Setup usually opens things: a database connection, a background scheduler, a file.
Whatever is opened must be closed. Without an exit hook there is no good place for the
closing code — the program just dies, connections hang until a timeout, half-written
files stay broken. `@PreDestroy` gives every bean one guaranteed moment to clean up.

### How it works

**Context close** is the moment Spring shuts the application context down. It happens in
two situations:

- The program finishes on its own (an app with no web server does this).
- Someone asks the program to stop: **Ctrl-C** in the terminal, or a `kill` command.
  This is a **clean stop** — the program gets a chance to react.

At context close, Spring calls every method marked `@PreDestroy`. Then the program ends.
Rules match `@PostConstruct`: no parameters, runs once, import
`jakarta.annotation.PreDestroy`.

Sharp edge: a **hard kill** (`kill -9`) ends the process instantly. No code runs. No
`@PreDestroy`. The hook is for clean stops only.

### The example — all three hooks in one class

`src/main/java/com/zenith/lifecycle/L4BookingCounter.java`:

```java
package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l4-predestroy")
class L4BookingCounter implements CommandLineRunner {

    L4BookingCounter() {
        System.out.println(">>> constructor:    the object exists");
    }

    @PostConstruct
    void prepare() {
        System.out.println(">>> @PostConstruct: setup done — pretend we opened a connection");
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():          the application is doing its work");
    }

    @PreDestroy
    void shutDown() {
        System.out.println(">>> @PreDestroy:    closing what we opened — goodbye");
    }
}
```

### Walkthrough — two real runs

**Run 1 — the app finishes on its own.**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l4-predestroy
```

Real output, trimmed to our lines:

```
>>> main: before SpringApplication.run — no beans exist yet
>>> constructor:    the object exists
>>> @PostConstruct: setup done — pretend we opened a connection
>>> run():          the application is doing its work
>>> main: run() returned — every bean is built and ready
>>> @PreDestroy:    closing what we opened — goodbye
```

Constructor → `@PostConstruct` → work → main finished → `@PreDestroy` last, as the
program ends and Spring closes the context.

**Run 2 — a clean stop in the middle of work.** With `Thread.sleep(60_000);` added to
`run()` and the app stopped mid-sleep by a polite `kill` (the same request Ctrl-C
sends):

```
>>> main: before SpringApplication.run — no beans exist yet
>>> constructor:    the object exists
>>> @PostConstruct: setup done — pretend we opened a connection
>>> run():          the application is doing its work
>>> @PreDestroy:    closing what we opened — goodbye
```

Two observations: `@PreDestroy` still ran, and `main: run() returned` is **missing** —
the work was interrupted, yet the cleanup hook fired anyway.

### Where it is used

Closing database connection pools. Stopping schedulers and thread pools. Flushing and
closing files. Telling a service registry "I am going away". Spring's own beans use the
same mechanism — a real app's connection pool prints its shutdown lines at this moment.

### Common mistakes

- **Trusting it with data you cannot lose.** Clean stops only. `kill -9`, a crash, or a
  power cut skips it. Critical data is saved when it changes, not at shutdown.
- **Expecting it for prototype beans.** A prototype bean (F6.3: a fresh object per
  request) is handed out and forgotten — its `@PreDestroy` never runs. Reliable for
  normal singleton beans.
- **Giving the method a parameter.** Same `Lifecycle annotation requires a no-arg
  method` error as `@PostConstruct`.
- **Thinking it runs after every use.** Once, at the end of the application's life.

### Link to the task

This completes **subtask S1**: the log shows constructor → `@PostConstruct` →
(application runs) → `@PreDestroy` at context close — including a stop in the middle of
work. And the sentence out loud: *setup that uses injected dependencies belongs in
`@PostConstruct`, not the constructor, because at construction time injection is not yet
guaranteed to be finished; by `@PostConstruct`, it is.*

### Do it yourself

**Goal:** one class, three hooks, the full order in your own log.

Continues in `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create** `src/main/java/com/zenith/lifecycle/L4BookingCounter.java` exactly
as in "The example" above.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l4-predestroy
```

Expected: six `>>>` lines ending with `@PreDestroy: closing what we opened — goodbye`.

**Step 3 — the Ctrl-C variant.** Change `run()` to:

```java
@Override
public void run(String... args) throws InterruptedException {
    System.out.println(">>> run():          the application is doing its work");
    Thread.sleep(60_000);
}
```

Run again. While the app sleeps, press **Ctrl-C** in that terminal. Expected:
`@PreDestroy` prints as the app stops; `main: run() returned` never appears.

**Step 4 — clean up.** Remove the sleep line and the `throws InterruptedException`. Run
once more; the six-line output returns.

**Done when:** constructor → `@PostConstruct` → `@PreDestroy` in order in your log, and
`@PreDestroy` fired on a Ctrl-C stop too. Subtask S1 is finished.

### Practice questions

**Question 1.** Here is a complete file. The app has no web server, so it stops on its
own right after `main` finishes:

```java
package demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@Component
class Session {

    Session() {
        System.out.println("A");
    }

    @PostConstruct
    void open() {
        System.out.println("B");
    }

    @PreDestroy
    void close() {
        System.out.println("C");
    }
}

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        System.out.println("D");
    }
}
```

In what order do A, B, C, D print?

*Answer:* **A, B, D, C.** During startup Spring builds the bean: constructor first
(`A`), then `@PostConstruct` (`B`). `SpringApplication.run` returns and `main` prints
`D`. Right after that the program begins to end — nothing keeps it running — so Spring
closes the application context and calls `@PreDestroy` (`C`) last.

**Question 2.** A bean's exit hook saves unsaved work to a file:

```java
package demo;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
class DraftKeeper {

    private String unsavedDraft = "half-written email";

    @PreDestroy
    void saveDrafts() {
        // writes unsavedDraft to a file on disk
        System.out.println("drafts saved");
    }
}
```

The app is running. An admin stops the process with `kill -9` — a hard kill the program
cannot react to. Does the draft get written to disk? Why or why not? And name one way of
stopping the app that *would* write it.

*Answer:* **No.** `kill -9` ends the process instantly; no further code runs, so
`saveDrafts()` never executes and the file is never written. `@PreDestroy` runs only at
context close, which needs a clean stop: Ctrl-C, a plain `kill` (without `-9`), or the
app finishing on its own — any of those would write the draft. Moral: never rely on
`@PreDestroy` for data you cannot afford to lose; save it when it changes.

### Summary for revision

- `@PreDestroy` marks one no-parameter method; Spring calls it once, at context close.
- Context close happens when the app finishes or gets a clean stop (Ctrl-C, `kill`).
- Hard kill (`kill -9`), crash, power cut: the hook never runs.
- Full arc observed: constructor → `@PostConstruct` → work → `@PreDestroy`.
- A clean stop mid-work still runs `@PreDestroy`; the work itself is abandoned.
- Prototype beans never get the call; hand-made `new` objects never get any hook.

---

## Lesson 5 — The full lifecycle map (serves checkpoint question 1)

### Definition

The **bean lifecycle** is the fixed sequence of stages Spring walks through to build
every single bean. Same stages, same order, for every bean in every Spring app.

### Why it is needed

Two reasons. Practical: reciting the map is checkpoint question 1 and a standard
interview question. Deeper: two stages on the map are *open slots* where an outsider may
step in and change the bean. All of Spring's magic — including the `@Transactional`
proxy — lives in those two slots. The map turns "Spring does something somewhere" into
"it happens at stage 6".

### How it works

The seven build stages, for one bean:

| Stage | Name | What happens | Your hook |
| --- | --- | --- | --- |
| 1 | **Instantiate** | Spring calls the constructor. The object exists, raw. | the constructor |
| 2 | **Populate dependencies** | Spring fills every injected field and value. | — |
| 3 | **Aware callbacks** | If the bean asked, Spring tells it things about itself (its name, the context). | e.g. `setBeanName` |
| 4 | **Post-process, before initialization** | Spring offers the bean to every registered post-processor. An open slot. | Lesson 6 |
| 5 | **Initialize** | Spring calls the `@PostConstruct` method. | `@PostConstruct` |
| 6 | **Post-process, after initialization** | Spring offers the bean to every post-processor again. The magic slot. | Lesson 6 |
| 7 | **Ready** | The finished object is stored in the application context. | — |

Destruction is the short mirror image: at context close, Spring calls `@PreDestroy`,
then the bean is discarded.

An **aware callback** (stage 3) is Spring answering a question the bean asked about
itself. The bean asks by implementing a small interface. A bean implementing
`BeanNameAware` says "tell me the name you registered me under"; Spring calls its
`setBeanName(String name)` at stage 3. Rare in app code — but a perfect probe, because
it prints *between* injection and `@PostConstruct`.

### The example — a probe bean that reports its own birth

`src/main/java/com/zenith/lifecycle/L5SeatMap.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l5-map")
class L5SeatMap {

    int seats() {
        return 40;
    }
}
```

`src/main/java/com/zenith/lifecycle/L5LifecycleProbe.java`:

```java
package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l5-map")
class L5LifecycleProbe implements BeanNameAware, CommandLineRunner {

    @Autowired
    private L5SeatMap seatMap;

    L5LifecycleProbe() {
        System.out.println(">>> stage 1  instantiate:  constructor runs, seatMap = " + seatMap);
    }

    // stage 2 (populate dependencies) has no hook — but its effect shows in stage 3:

    @Override
    public void setBeanName(String name) {
        System.out.println(">>> stage 3  aware:        my name is '" + name + "', seatMap = " + seatMap);
    }

    // stage 4 (post-process BEFORE initialization) belongs to BeanPostProcessor — Lesson 6.

    @PostConstruct
    void init() {
        System.out.println(">>> stage 5  initialize:   @PostConstruct runs, " + seatMap.seats() + " seats");
    }

    // stage 6 (post-process AFTER initialization) belongs to BeanPostProcessor — Lesson 6.

    @Override
    public void run(String... args) {
        System.out.println(">>> stage 7  ready:        the context holds me, the app uses me");
    }

    @PreDestroy
    void destroy() {
        System.out.println(">>> destruction:           @PreDestroy runs at context close");
    }
}
```

### Walkthrough — the real run

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-map
```

Real output, trimmed to our lines:

```
>>> main: before SpringApplication.run — no beans exist yet
>>> stage 1  instantiate:  constructor runs, seatMap = null
>>> stage 3  aware:        my name is 'l5LifecycleProbe', seatMap = com.zenith.lifecycle.L5SeatMap@726386ed
>>> stage 5  initialize:   @PostConstruct runs, 40 seats
>>> stage 7  ready:        the context holds me, the app uses me
>>> main: run() returned — every bean is built and ready
>>> destruction:           @PreDestroy runs at context close
```

- Stage 1: constructor, `seatMap = null`.
- Stage 2 is silent but left its fingerprint: by stage 3, `seatMap` is a real object.
  Injection happened between the two prints.
- Stage 3: the default bean name is the class name with a small first letter:
  `l5LifecycleProbe`.
- Stages 4 and 6 are silent — no post-processor of ours is registered yet.
- Stage 5: `@PostConstruct`, the dependency works.
- Stage 7: ready. Destruction at context close.

### Where it is used

- **Debugging startup failures:** Spring's errors name the stage
  (`Failed to instantiate ... Constructor threw exception` = stage 1; missing-bean
  errors point at stage 2).
- **Interviews:** "walk me through the bean lifecycle".
- **Understanding magic:** `@Transactional`, `@Async`, caching all enter at stages 4/6.

### Common mistakes

- **Treating constructor and `@PostConstruct` as the same moment.** Stages 1 and 5, with
  injection (stage 2) between them.
- **Thinking the order can vary.** It is fixed and identical for every bean.
- **Expecting a full mirror at shutdown.** Destruction is short: context close →
  `@PreDestroy` → gone.
- **Using aware callbacks for business logic.** They are probes and framework tools.

### Do it yourself

**Goal:** produce the stage log yourself, then recite the map without looking.

Continues in `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create the two files** in `src/main/java/com/zenith/lifecycle/`, exactly as
in "The example" above: `L5SeatMap.java` and `L5LifecycleProbe.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-map
```

Expected: stage lines 1, 3, 5, 7, then destruction — `seatMap = null` at stage 1, filled
at stage 3. If the stage lines are missing, the profile switch is off.

**Step 3 — recite.** Say the seven stages out loud, in order, plus where destruction
fits. Check yourself against the log.

**Done when:** you can recite instantiate → populate dependencies → aware callbacks →
post-process before initialization → initialize (`@PostConstruct`) → post-process after
initialization → ready, and "destruction: `@PreDestroy` at context close" — without
looking.

### Practice questions

**Question 1.** From memory: list the seven build stages of a bean in order. Then say at
which stage each of these happens: (a) the constructor runs, (b) `@Autowired` fields get
filled, (c) `@PostConstruct` runs. Finally: where does `@PreDestroy` fit — is it
stage 8?

*Answer:* Instantiate → populate dependencies → aware callbacks → post-process before
initialization → initialize → post-process after initialization → ready.
(a) stage 1 (instantiate). (b) stage 2 (populate dependencies). (c) stage 5
(initialize). `@PreDestroy` is **not** stage 8: the build sequence ends at "ready".
`@PreDestroy` belongs to destruction — the separate, short mirror that runs at context
close, when the application stops.

**Question 2.** Here is a complete file:

```java
package demo;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class Clock {
    String now() {
        return "12:00";
    }
}

@Component
class Watch implements BeanNameAware {

    @Autowired
    private Clock clock;

    Watch() {
        System.out.println("P: clock = " + clock);
    }

    @Override
    public void setBeanName(String name) {
        System.out.println("Q: clock = " + clock);
    }

    @PostConstruct
    void start() {
        System.out.println("R: " + clock.now());
    }
}
```

The app starts without errors. In what order do P, Q, R print? And at line Q — is
`clock` still `null`, or already a real object? Why?

*Answer:* **P, Q, R** — the stage order is fixed: constructor (stage 1), aware callback
(stage 3), `@PostConstruct` (stage 5). At line Q, `clock` is **already a real object**:
populate dependencies is stage 2, and it runs *before* the aware callbacks of stage 3.
Only at P (stage 1) is the field still `null`. That is also why `clock.now()` inside the
`@PostConstruct` method is safe.

### Summary for revision

- Build stages: **instantiate → populate dependencies → aware callbacks → post-process
  before init → initialize (`@PostConstruct`) → post-process after init → ready.**
- Destruction mirror: context close → `@PreDestroy` → discarded.
- Stage 2 is hookless but provable: a field is `null` at stage 1 and filled by stage 3.
- Default bean name = class name with a small first letter.
- Stages 4 and 6 are the open slots where all Spring magic enters; stage 6 is where the
  `@Transactional` proxy will appear.

---

## Lesson 6 — `BeanPostProcessor`: watching every bean (serves S2, completes it)

### Definition

A `BeanPostProcessor` is a bean that implements the interface `BeanPostProcessor`.
Spring hands it **every other bean**, twice: once at stage 4 (before initialization) and
once at stage 6 (after initialization). Whatever object it returns is what the
application context keeps. In watch-only mode, it logs and returns the bean unchanged.

### Why it is needed

The two open slots on the lifecycle map are the mechanism behind almost everything
called Spring magic. Standing in the slot and watching is the first step: the parade of
beans is Spring assembling the application in front of you.

### How it works

The interface has two methods, both with default do-nothing implementations:

- `Object postProcessBeforeInitialization(Object bean, String beanName)` — stage 4,
  before `@PostConstruct`.
- `Object postProcessAfterInitialization(Object bean, String beanName)` — stage 6,
  after `@PostConstruct`.

For each bean, Spring calls the method with the bean object and its registered name. The
method must return an object; returning the same bean changes nothing. No registration
step is needed: mark the class `@Component`. Spring notices the interface and builds the
post-processor *early*, before ordinary beans, so it is standing in the slot when they
come through.

**The secret now tellable:** Spring's own machinery lives in these slots. The
parameter-on-`@PostConstruct` error in Lesson 3 came from
`InitDestroyAnnotationBeanPostProcessor` — `@PostConstruct` itself is executed by a
built-in post-processor. `@Autowired` filling is done by another one
(`AutowiredAnnotationBeanPostProcessor`).

### The example — a watcher and two beans to watch

`src/main/java/com/zenith/lifecycle/L6BeanWatcher.java`:

```java
package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l6-watch")
class L6BeanWatcher implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println(">>> watched: " + beanName);
        return bean;   // hand back the same object — observe only, change nothing
    }
}
```

`src/main/java/com/zenith/lifecycle/L6SeatMap.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l6-watch")
class L6SeatMap {

    int seats() {
        return 40;
    }
}
```

`src/main/java/com/zenith/lifecycle/L6BookingCounter.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l6-watch")
class L6BookingCounter {

    private final L6SeatMap seatMap;

    L6BookingCounter(L6SeatMap seatMap) {
        this.seatMap = seatMap;
    }
}
```

### Walkthrough — the real parade

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-watch
```

**41 beans** passed through. Real output, numbered, middle trimmed:

```
 1  >>> watched: org.springframework.boot.autoconfigure.task.TaskExecutorConfigurations$TaskExecutorConfiguration
 ...
 5  >>> watched: threadPoolTaskExecutorBuilder
 6  >>> watched: applicationTaskExecutor
 7  >>> watched: lifecycleApplication
 8  >>> watched: l6SeatMap
 9  >>> watched: l6BookingCounter
10  >>> watched: org.springframework.boot.autoconfigure.AutoConfigurationPackages
    ... 30 more framework beans ...
41  >>> watched: org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
```

Observations:

1. Every line is one bean finishing stage 6 — Spring assembling the app, live.
2. Our classes (lines 7–9) are mixed in with the framework's, passing the same slot.
3. Dependency order is visible: `l6SeatMap` (8) before `l6BookingCounter` (9), because
   the counter needs the seat map in its constructor. Same for lines 5 → 6.
4. The watcher never watches itself: `l6BeanWatcher` is absent from its own list — it
   had to exist before the parade started.

### Where it is used

Inside Spring: injection, lifecycle hooks, `@Transactional`, `@Async`, caching — each a
post-processor in these slots. In app code: startup auditing — logging beans, enforcing
naming rules, failing fast on forbidden beans.

### Common mistakes

- **Forgetting to return the bean.** What the method returns is what the context keeps.
  Observe-only means `return bean;` — the exact object handed in.
- **Heavy work in the hook.** Runs once per bean; thousands of beans in real apps.
- **Expecting only your own beans.** Framework beans flood through with long names.
- **Expecting hand-made `new` objects.** Only beans walk the lifecycle.

### Link to the task

Subtask S2, word for word — done. Also half of checkpoint question 2; the other half
("it may return a *different* object") is Lesson 7.

### Do it yourself

**Goal:** your own watcher, and the full parade in your terminal.

Continues in `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create the three files** in `src/main/java/com/zenith/lifecycle/`, exactly as
in "The example" above: `L6BeanWatcher.java`, `L6SeatMap.java`, `L6BookingCounter.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-watch
```

Expected: ~40 `>>> watched:` lines (count varies with Boot version); among them
`lifecycleApplication`, `l6SeatMap`, `l6BookingCounter`, in that order. Just your beans:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-watch | grep "watched: l"
```

Failure: no `watched:` lines → the profile switch is off.

**Done when:** the parade scrolls past, you can point at your three beans, and you can
explain why `l6SeatMap` is listed before `l6BookingCounter`.

### Practice questions

**Question 1.** Here is a complete file:

```java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
class Alpha {
}

@Component
class Beta {

    private final Alpha alpha;

    Beta(Alpha alpha) {
        this.alpha = alpha;
    }
}

@Component
class Spy implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println("saw: " + beanName);
        return bean;
    }
}
```

(a) Which prints first — `saw: alpha` or `saw: beta` — and why? (b) Does `saw: spy` ever
print? Why or why not?

*Answer:* (a) `saw: alpha` first. `Beta` needs `Alpha` in its constructor, so Spring
must finish building `alpha` — all lifecycle stages, including the stage-6 hook — before
it can even instantiate `beta`. A bean always passes the watcher after the beans it
depends on. (b) No. A post-processor must exist before the other beans are built, so
Spring creates `spy` early, before it is itself watchable — it never passes through its
own hook.

**Question 2.** Here is a complete file. The post-processor returns something
*different* for one bean:

```java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

interface Greeter {
    String greet();
}

@Component
class PoliteGreeter implements Greeter {
    public String greet() {
        return "good day";
    }
}

@Component
class Swapper implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.equals("politeGreeter")) {
            return new Greeter() {
                public String greet() {
                    return "HEY!";
                }
            };
        }
        return bean;
    }
}
```

Another bean asks the application context for the `Greeter` and calls `greet()`. What
does it get back — `"good day"` or `"HEY!"`?

*Answer:* `"HEY!"`. The rule from the definition: **whatever object the post-processor
returns is what the application context keeps.** For the bean named `politeGreeter`, the
swapper returned a different `Greeter` object, so the context stored that replacement
under the name `politeGreeter`. Every consumer asking for the `Greeter` gets the
replacement. The original `PoliteGreeter` object is no longer what the context serves.

### Summary for revision

- `BeanPostProcessor`: two methods, stage 4 (`postProcessBeforeInitialization`) and
  stage 6 (`postProcessAfterInitialization`), called for **every bean**, with the bean
  and its name.
- Registration = `@Component`; Spring builds post-processors early.
- What the method returns is what the context keeps.
- The parade shows dependency order; a post-processor never sees its own birth.
- Spring's own `@PostConstruct` and `@Autowired` handling are built-in post-processors.

---

## Lesson 7 — `BeanPostProcessor`: swapping a bean (serves S3, checkpoint question 2)

### Definition

The swap is the second half of the `BeanPostProcessor` rule: **whatever object the
post-processor returns is what the application context keeps** — and nothing forces it
to return the object it was handed. Return a different one, and the context stores that
replacement under the bean's name. Consumers get the replacement and never learn a swap
happened.

A **proxy** is a stand-in object that wraps the real one: it holds the real object
inside, adds behaviour around the call, then forwards the call to the real object.

### Why it is needed

This one rule is the engine behind nearly all Spring magic. `@Transactional`, caching,
`@Async` — each is "the consumers got an upgraded stand-in instead of the original".
Doing the swap once by hand removes the sorcery: the `$$SpringCGLIB$$` class is just
someone else's stand-in, made by a machine instead of by hand.

### How it works — the swap, step by step

1. The target bean is built normally: stages 1–5.
2. At stage 6, Spring hands the finished bean to the post-processor.
3. The post-processor creates a stand-in with plain `new`, passing the original in.
4. It returns the stand-in.
5. The context stores the stand-in **under the original bean's name**. The name does not
   change; only the object does.
6. Every bean built afterwards that asks for this dependency receives the stand-in.
7. The original lives on in exactly one place: inside the stand-in's field. It **never
   learns the stand-in exists** — the seed of the self-invocation story.

### The example — five small files (profile `l7-swap`)

The contract:

```java
package com.zenith.lifecycle;

interface L7Greeter {
    String greet();
}
```

The honest bean:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l7-swap")
class L7PoliteGreeter implements L7Greeter {

    @Override
    public String greet() {
        return "good day";
    }
}
```

The stand-in — no `@Component`; the post-processor makes it by hand:

```java
package com.zenith.lifecycle;

class L7ShoutingGreeter implements L7Greeter {

    private final L7Greeter original;

    L7ShoutingGreeter(L7Greeter original) {
        this.original = original;
    }

    @Override
    public String greet() {
        return original.greet().toUpperCase() + "!!!";   // extra behaviour + forward
    }
}
```

The swapper (`bean instanceof L7Greeter original` checks the type and binds the variable
in one step):

```java
package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l7-swap")
class L7GreeterSwapper implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof L7Greeter original) {
            System.out.println(">>> swapper: replacing '" + beanName + "' with a shouting stand-in");
            return new L7ShoutingGreeter(original);   // the context keeps THIS object
        }
        return bean;
    }
}
```

The consumer:

```java
package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l7-swap")
class L7FrontDesk implements CommandLineRunner {

    private final L7Greeter greeter;

    L7FrontDesk(L7Greeter greeter) {
        this.greeter = greeter;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> front desk got : " + greeter.getClass().getName());
        System.out.println(">>> greeting       : " + greeter.greet());
    }
}
```

### Walkthrough — the real run

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l7-swap
```

```
>>> swapper: replacing 'l7PoliteGreeter' with a shouting stand-in
>>> front desk got : com.zenith.lifecycle.L7ShoutingGreeter
>>> greeting       : GOOD DAY!!!
```

1. At stage 6 of `l7PoliteGreeter`'s lifecycle, our hook swapped it.
2. The front desk prints a class it never imports or mentions: `L7ShoutingGreeter`. It
   compiled against the interface; the context chose the object. This is the exact
   experience of `getClass()` on a `@Transactional` service showing `$$SpringCGLIB$$`.
3. Behaviour changed without editing the bean: `good day` → `GOOD DAY!!!`.

The original polite greeter is alive inside the stand-in's `original` field, does the
real work on every forwarded call, and was never told about the swap.

### Where it is used

The shape — stand-in, added behaviour, forward — is the **proxy pattern**. Spring's
transaction post-processor returns a machine-generated proxy (begin → forward → commit
or roll back). Caching: check cache → forward. Metrics, security, retries: same shape.

### Common mistakes

- **Returning the wrong type.** Tried for real with `return new Object();`:

  ```
  Caused by: org.springframework.beans.factory.BeanNotOfRequiredTypeException:
    Bean named 'l7PoliteGreeter' is expected to be of type 'com.zenith.lifecycle.L7Greeter'
    but was actually of type 'java.lang.Object'
  ```

  The *name* stayed; the object behind it broke the contract. The stand-in must
  implement what consumers ask for.
- **Forgetting to forward.** A stand-in that never calls the original silently loses the
  real behaviour.
- **Forgetting `return bean;` for all other beans.** Only the target is swapped.
- **Thinking the original is destroyed.** It lives inside the stand-in; only the direct
  path to it is gone.

### Link to the task

Checkpoint question 2, answered from experience: *a post-processor sees every bean and
may hand the context a stand-in instead — so the framework can silently upgrade any
bean; transactions, caching, async are stand-ins substituted at stage 6.* The punchline
experiment (S3) is this lesson re-run with Spring's own swapper.

### Do it yourself

**Goal:** perform the swap, watch a consumer receive a class it never knew existed,
break the swap, read the error.

Continues in `~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app`.
Docker is not needed.

**Step 1 — create the five files** in `src/main/java/com/zenith/lifecycle/`, exactly as
in "The example" above: `L7Greeter.java`, `L7PoliteGreeter.java`,
`L7ShoutingGreeter.java`, `L7GreeterSwapper.java`, `L7FrontDesk.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l7-swap
```

Expected: the three `>>>` lines of the walkthrough.

**Step 3 — break it on purpose.** In `L7GreeterSwapper.java`, change the swap line to
`return new Object();`. Run. Expected: `BeanNotOfRequiredTypeException ... expected to
be of type 'com.zenith.lifecycle.L7Greeter' but was actually of type
'java.lang.Object'`.

**Step 4 — repair.** Restore `return new L7ShoutingGreeter(original);`. Run; success
returns.

**Done when:** the consumer printed a class name it never imported, and you can say
where the original lives after the swap.

### Practice questions

**Question 1.** Here is a complete file:

```java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

interface Alarm {
    String ring();
}

@Component
class QuietAlarm implements Alarm {
    public String ring() {
        return "beep";
    }
}

class LoudAlarm implements Alarm {

    private final Alarm original;

    LoudAlarm(Alarm original) {
        this.original = original;
    }

    public String ring() {
        return original.ring() + " BEEP BEEP";
    }
}

@Component
class AlarmUpgrader implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof Alarm original) {
            return new LoudAlarm(original);
        }
        return bean;
    }
}

@Component
class Bedroom implements CommandLineRunner {

    private final Alarm alarm;

    Bedroom(Alarm alarm) {
        this.alarm = alarm;
    }

    @Override
    public void run(String... args) {
        System.out.println(alarm.getClass().getSimpleName() + ": " + alarm.ring());
    }
}
```

(a) What exactly does the `Bedroom` line print? (b) `LoudAlarm` has no `@Component` — so
who created the `LoudAlarm` object, and under what bean name does the context store it?

*Answer:* (a) `LoudAlarm: beep BEEP BEEP`. The context holds the wrapper, so
`getClass().getSimpleName()` is `LoudAlarm`; its `ring()` forwards to the quiet alarm
(`beep`) and appends ` BEEP BEEP`. (b) The `AlarmUpgrader`'s own code created it, with
plain `new`, at stage 6 of `quietAlarm`'s lifecycle. The context stores it under the
original bean's name: `quietAlarm`. Spring's scanner never saw `LoudAlarm`; only the
returned object matters.

**Question 2.** Here is a swap, shown compactly. `Original` is a normal bean; `Wrapper`
is returned in its place by a post-processor:

```java
class Wrapper implements Work {

    private final Work original;

    Wrapper(Work original) {
        this.original = original;
    }

    public String doWork() {
        return "[logged] " + original.doWork();
    }
}
```

After startup: (a) Does the original object still exist, and if yes, where exactly?
(b) Does the original know it has been wrapped? (c) If a method *inside* the original
calls another method of the original — plain `this.otherMethod()` — does that call pass
through the `Wrapper`? Why or why not?

*Answer:* (a) Yes. It exists in exactly one place: the `Wrapper`'s private `original`
field. (b) No. It was built normally and never told; nothing inside it references the
wrapper. (c) No. Inside the original, `this` points to the original itself. The call
goes straight from the original to its own method, never leaving the raw object — the
wrapper only intercepts calls that *arrive from outside*, from consumers who were handed
the wrapper by the context. This is the mechanism behind the self-invocation problem.

### Summary for revision

- The context keeps **what the post-processor returns**, under the **unchanged** bean
  name.
- A proxy = stand-in: holds the original, adds behaviour, forwards the call.
- Consumers compile against the contract and receive whatever object the context holds —
  possibly a class they never heard of.
- The original lives inside the stand-in and never knows about it; `this`-calls inside
  the original bypass the stand-in.
- Error to recognise: `BeanNotOfRequiredTypeException ... expected to be of type X but
  was actually of type Y` — a swap returned the wrong type.

---

## Lesson 8 — A `@Transactional` service joins the app (serves S3)

### Definition

`@Transactional` marks a method so that everything it does in the database happens as
one unit — a **transaction**: opened before the code runs, committed on success, rolled
back on failure. This lesson's fact: for a `@Transactional` class, the bean in the
application context is **not your class** — it is a machine-made stand-in (proxy), and
`getClass()` shows it.

### Why it is needed

Lesson 7's swap was hand-made and visible. Spring's transaction machinery does the same
thing silently, with a generated class. Seeing the swapped object inside an app where we
wrote every file removes the last of the magic before the punchline experiment.

### How it works

**What had to be added.** `@Transactional` needs a **transaction manager** (opens,
commits, rolls back). The manager needs a **DataSource** (the pool of database
connections). `spring-boot-starter-jdbc` brings both; the Postgres driver talks to the
database; `application.properties` points at the `pg` container.

New blocks in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

New file `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/zenith
spring.datasource.username=postgres
spring.datasource.password=dev
```

**The stand-in.** At startup Spring generates a new class in memory: a **subclass** of
your class, named `YourClass$$SpringCGLIB$$0` (CGLIB is the class-generating library;
subclassing is why no interface is needed). The generated subclass overrides your public
methods: open transaction → forward to the real work → commit or roll back. Hold the
real thing, add behaviour, forward — the shouting greeter's shape, machine-made.

**The proof tool.** `TransactionSynchronizationManager.isActualTransactionActive()`
answers: is a transaction open on this thread right now?

### The example — three files (profile `l8-transactional`)

`src/main/java/com/zenith/lifecycle/L8PlainService.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l8-transactional")
class L8PlainService {

    public String ping() {
        return "pong";
    }
}
```

`src/main/java/com/zenith/lifecycle/L8BookingService.java` (`JdbcClient` is the small
database helper from F5.10):

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("l8-transactional")
class L8BookingService {

    private final JdbcClient db;

    L8BookingService(JdbcClient db) {
        this.db = db;
    }

    @Transactional
    public int reserveSeat() {
        boolean open = TransactionSynchronizationManager.isActualTransactionActive();
        System.out.println(">>> inside reserveSeat: transaction open? " + open);
        return db.sql("SELECT 1").query(Integer.class).single();
    }
}
```

`src/main/java/com/zenith/lifecycle/L8ProxyMeet.java`:

```java
package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l8-transactional")
class L8ProxyMeet implements CommandLineRunner {

    private final L8PlainService plain;
    private final L8BookingService booking;

    L8ProxyMeet(L8PlainService plain, L8BookingService booking) {
        this.plain = plain;
        this.booking = booking;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> plain service class  : " + plain.getClass().getName());
        System.out.println(">>> booking service class: " + booking.getClass().getName());
        System.out.println(">>> ping(): " + plain.ping());
        System.out.println(">>> reserveSeat(): " + booking.reserveSeat());
    }
}
```

### Walkthrough — two real runs

**Run 1 — Postgres running** (`docker start pg` first):

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional
```

```
>>> plain service class  : com.zenith.lifecycle.L8PlainService
>>> booking service class: com.zenith.lifecycle.L8BookingService$$SpringCGLIB$$0
>>> ping(): pong
... INFO ... HikariDataSource : HikariPool-1 - Starting...
... INFO ... HikariPool       : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
>>> inside reserveSeat: transaction open? true
>>> reserveSeat(): 1
>>> main: run() returned — every bean is built and ready
... INFO ... HikariDataSource : HikariPool-1 - Shutdown initiated...
```

1. Plain service = its own class; transactional service = `$$SpringCGLIB$$0`, a class in
   no `.java` file of ours. One annotation caused a swap.
2. The pool starts **lazily**, between `ping()` and the inside-line — at the first
   moment the stand-in needs a connection. Startup never touched the database.
3. `transaction open? true` — the stand-in opened it before our code ran.
4. At context close the pool closes its connections — Lesson 4's "close what you
   opened", performed by a framework bean.

**Run 2 — Postgres stopped.** Startup **succeeded** — `$$SpringCGLIB$$0` still printed,
`ping()` fine. Only the call failed:

```
org.springframework.transaction.CannotCreateTransactionException:
  Could not open JDBC Connection for transaction
Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
```

| Fact | What it teaches |
| --- | --- |
| Startup fine, proxy printed | The proxy is generated at startup, in memory — no database needed. |
| `ping()` fine | The plain service never touches the database. |
| `reserveSeat()` fails at `Could not open JDBC Connection` | The stand-in acts first — it failed opening the transaction before our code ran. |
| `Connection to localhost:5432 refused` | Nothing listening on the port: `docker start pg`. |

### Where it is used

Every `@Transactional` service and Spring Data repository is served to callers in this
swapped form. The F6.10/F6.11 apps were calling `$$SpringCGLIB$$` proxies all along.

### Common mistakes

- **Expecting `getClass()` to print your class.** It prints the stand-in. But
  `instanceof YourClass` is still `true` — the stand-in is a subclass (why entity rules
  say `instanceof`, never `getClass() ==`).
- **Thinking the proxy needs the database.** Disproven live: in-memory creation.
- **`@Transactional` on non-public methods.** Not wrapped — silently no transaction.
- **`final` class or method.** The machine must subclass and override; `final` forbids
  both.

### Do it yourself

**Step 0 — Postgres** (needs Docker, from F5.1):

```bash
docker start pg
docker ps        # expect: pg ... Up ...
```

If no such container:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
docker exec pg psql -U postgres -c 'CREATE DATABASE zenith;'
```

**Step 1 — pom.** Add the two dependency blocks shown in "How it works".

**Step 2 — config.** Create `src/main/resources/application.properties` as shown.

**Step 3 — code.** Create the three files from "The example".

**Step 4 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional
```

Expected: run 1's output, with `$$SpringCGLIB$$0` in the second class line.

**Step 5 — break and repair.** `docker stop pg`, run (expect
`CannotCreateTransactionException ... Connection refused` at the method call, startup
fine), `docker start pg`, run again.

**Done when:** you can point at `$$SpringCGLIB$$0` in your own log and explain why it
appears even with Postgres off.

### Practice questions

**Question 1.** Two beans:

```java
package demo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ReportService {

    @Transactional
    public String monthly() {
        return "report";
    }
}

@Component
class EmailService {

    public String send() {
        return "sent";
    }
}
```

A runner prints `reportService.getClass().getName()` and
`emailService.getClass().getName()`. One print ends with `$$SpringCGLIB$$0`. Which one,
and what caused the difference? Bonus: is `reportService instanceof ReportService` true?

*Answer:* `reportService` prints `demo.ReportService$$SpringCGLIB$$0`. Its
`@Transactional` method made the transaction machinery swap the bean for a
machine-generated subclass at startup. `emailService` has no such annotation, so no swap:
it prints `demo.EmailService`. Bonus: **true** — the stand-in is a *subclass* of
`ReportService`, so `instanceof` matches even though `getClass()` differs.

**Question 2.** An app has a `@Transactional` service connected to Postgres. The
database server is off. The app starts successfully, and `getClass()` on the service
prints a `$$SpringCGLIB$$0` name. Then a transactional method is called and the app
fails with `CannotCreateTransactionException: Could not open JDBC Connection for
transaction`. Explain both halves.

*Answer:* Startup succeeded because creating the proxy is in-memory class generation —
Spring writes a subclass of the service at startup and never needs the database for
that; the connection pool connects lazily. The call failed *before the method body ran*
because the proxy acts first: it asked the transaction manager to open a transaction,
the manager asked the pool for a connection, and the pool could not reach Postgres —
connection refused. The failure is the stand-in's added behaviour failing, not the
method's own code.

### Summary for revision

- `@Transactional` bean in the context = machine-made **subclass** stand-in,
  `YourClass$$SpringCGLIB$$0`: open transaction → forward → commit/roll back.
- `instanceof YourClass` still true; `getClass()` shows the proxy.
- Proxy creation is startup-time and in-memory; the database is touched only when a
  transactional method runs (pool starts lazily, visible in the log).
- `transaction open? true` inside the method = the proxy opened it first.
- Errors: `CannotCreateTransactionException ... Connection refused` = the proxy could
  not get a connection; check `docker start pg`.
- Keep `@Transactional` methods public and classes/methods non-final.

---

## Lesson 9 — The punchline: who creates the proxy, and when (serves S3, completes it)

### Definition

The punchline experiment: observe one bean at two moments of its own lifecycle — stage 4
(before initialization) and stage 6 (after initialization) — and print its class at
each. The two prints disagree. That disagreement is the complete answer to "who creates
the `@Transactional` proxy, and when".

### Why it is needed

Checkpoint question 3 asks exactly this, and the AOP task left it open. With the
lifecycle map, the watcher, and the hand-made swap already learned, one run turns the
answer from a memorised sentence into witnessed fact.

### How it works

**Order among post-processors.** Several post-processors stand in the same slot. Spring
sorts them: ranked (ordered) ones first, unranked ones last. The transaction machinery
registers its own post-processor — internal class
`InfrastructureAdvisorAutoProxyCreator`, "the infrastructure's automatic proxy maker" —
and it is ranked. A plain `@Component` post-processor with no rank runs after it.

Timeline for `l8BookingService`:

1. Stages 1–3: constructor, dependencies, aware callbacks — purely your class.
2. Stage 4 (before slot): the proxy maker does nothing here; the catcher prints the
   **plain class**.
3. Stage 5: `@PostConstruct` (if any) runs — **on the raw object**; the proxy does not
   exist yet.
4. Stage 6 (after slot): the ranked proxy maker generates the CGLIB subclass, wraps the
   raw object, and **returns the proxy** — machine-made version of
   `new L7ShoutingGreeter(original)`. Then the unranked catcher is handed the
   **already-swapped** object.
5. Stage 7: the context stores the proxy under the unchanged name; every consumer gets
   the proxy.

Substituted, not woven: your class's code is never rewritten; the raw object lives on
inside the proxy, unaware.

### The example — the catcher (profile `l9-punchline`)

`src/main/java/com/zenith/lifecycle/L9ProxyCatcher.java`:

```java
package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l9-punchline")
class L9ProxyCatcher implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.equals("l8BookingService")) {
            System.out.println(">>> BEFORE init: " + beanName + " is " + bean.getClass().getName());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.equals("l8BookingService")) {
            System.out.println(">>> AFTER  init: " + beanName + " is " + bean.getClass().getName());
        }
        return bean;
    }
}
```

### Walkthrough — the real run

Two profiles at once — the lesson 8 beans plus the catcher:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional,l9-punchline
```

Real output, complete:

```
>>> BEFORE init: l8BookingService is com.zenith.lifecycle.L8BookingService
>>> AFTER  init: l8BookingService is com.zenith.lifecycle.L8BookingService$$SpringCGLIB$$0
>>> plain service class  : com.zenith.lifecycle.L8PlainService
>>> booking service class: com.zenith.lifecycle.L8BookingService$$SpringCGLIB$$0
>>> ping(): pong
>>> inside reserveSeat: transaction open? true
>>> reserveSeat(): 1
```

1. BEFORE: the bean is **your class** — the proxy maker has not had its turn.
2. AFTER: the same bean name holds the **proxy** — the swap happened during
   initialization, in the after slot, before the unranked catcher's turn.
3. The consumer receives exactly what stage 6 produced.
4. `transaction open? true` — the swap is functional, not cosmetic.

The sentence, earned by evidence: **the proxy is created by a `BeanPostProcessor`,
during bean initialization, in the post-process-after-initialization slot — substituted
for your object, not woven into it.**

### Where it is used

A real debugging tool: when an annotation seems ignored or a stack trace shows an alien
class name, print `getClass()` at both hooks. The same experiment exposes caching,
`@Async`, and security proxies — all made the same way.

### Common mistakes

- **"Woven into the class."** Disproven: two different classes at two moments; your
  `.class` file is untouched; the proxy is a separate wrapping object.
- **Thinking `@PostConstruct` runs on the proxy.** Stage 5 is before the swap (stage 6);
  setup hooks run on the raw object.
- **Expecting to see the raw object in your own after-hook.** Ranked post-processors act
  first; by your turn the swap is history.
- **Filtering by class name in a post-processor.** The class changes mid-flight; filter
  by `beanName`, which never changes.

### Do it yourself

**Goal:** both hook lines in your own terminal — plain before, proxy after.

Needs the lesson 8 files and the `pg` container (`docker start pg`). Even with Postgres
off, the BEFORE/AFTER lines still print; only the final `reserveSeat()` call needs the
database.

**Step 1 — create** `src/main/java/com/zenith/lifecycle/L9ProxyCatcher.java` exactly as
in "The example" above.

**Step 2 — run with both profiles.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional,l9-punchline
```

Expected: the BEFORE line with the plain class, the AFTER line with
`$$SpringCGLIB$$0`, then the lesson 8 lines ending `reserveSeat(): 1`.

Failures: no catcher lines → `l9-punchline` missing from the command. No class lines at
all → `l8-transactional` missing. `Connection refused` at the end → `docker start pg`.

**Step 3 — say it out loud,** pointing at your own two lines: "the proxy is created by a
`BeanPostProcessor` during bean initialization — before initialization my bean is my
class, after initialization it is the proxy."

**Done when:** both lines are in your log and the sentence feels obvious.

### Practice questions

**Question 1.** Here is a complete scenario:

```java
package demo;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PayService {

    @Transactional
    public void pay() {
    }
}

@Component
class Catcher implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.equals("payService")) {
            System.out.println("before: " + bean.getClass().getSimpleName());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.equals("payService")) {
            System.out.println("after: " + bean.getClass().getSimpleName());
        }
        return bean;
    }
}
```

The app starts with a working transaction setup. What do the two lines print (roughly),
and what single fact about post-processor *order* explains why the "after" line does not
show the plain class?

*Answer:* `before: PayService` and `after: PayService$$SpringCGLIB$$0`. The ordering
fact: within each slot, **ranked post-processors run before unranked ones**. The
transaction proxy maker is ranked; `Catcher` declares no rank. So in the after slot the
proxy maker swaps the bean first, and by the time `Catcher` is handed the bean, it is
already the proxy.

**Question 2.** A colleague says: "Spring adds `@Transactional` support by rewriting
your class — it injects begin-and-commit code into your method's body at startup."
Using the two-moments evidence, correct the colleague. Where does the begin-and-commit
code actually live, and what happened to your original object?

*Answer:* The evidence says otherwise: before initialization the bean is plainly
`YourService` — unchanged, no injected code; after initialization the bean is a
*different class*, `YourService$$SpringCGLIB$$0`. Spring never edits your class. The
begin-and-commit code lives in the generated **subclass's overriding methods**: they
open the transaction, forward to your original object, then commit or roll back. Your
original object still exists, wrapped inside the proxy, executing the real method bodies
— substituted for, not rewritten.

### Summary for revision

- Catch: BEFORE init = your class; AFTER init = `$$SpringCGLIB$$0`. The swap happens
  during initialization, in the after slot.
- Who: a `BeanPostProcessor` — Spring's ranked transaction proxy maker
  (`InfrastructureAdvisorAutoProxyCreator`).
- Ranked post-processors run before unranked ones in the same slot.
- `@PostConstruct` runs on the raw object, before the proxy exists.
- Substituted, not woven: your class is untouched; the raw object lives inside the
  proxy.
- In post-processors, filter by `beanName` — the class name changes mid-flight.

---

## Lesson 10 — Why self-invocation bypasses the proxy (serves S4, completes it)

### Definition

**Self-invocation** is a bean's method calling another method of the same bean — a plain
`this.someMethod()` call. The fact: such a call never passes through the proxy, so
whatever the proxy adds (a transaction, a cache check) does not happen for that call.

### Why it is needed

The classic Spring production bug: a `@Transactional` method works for weeks, then
someone calls it from a sibling method of the same class — and it silently runs with no
transaction. No error, no warning, half-written data on failure. Also the standard
interview follow-up: "a proxy — so why does self-invocation bypass it?"

### How it works

After the swap, **two objects exist**: the raw object (your class, built normally) and
the proxy wrapped around it. The context holds the proxy; consumers get the proxy; the
raw object lives inside it and **was never told** — nothing inside the raw object points
at the proxy.

**Path 1 — from outside.** The caller's field holds the proxy. `service.saveNote()`
means: proxy's `saveNote()` → open transaction → forward to the raw object's
`saveNote()` → commit. Transaction: open.

**Path 2 — self-invocation.** `service.saveNoteTwice()` enters through the proxy, but
`saveNoteTwice` is not `@Transactional`, so the proxy just forwards. Execution is now
inside the raw object. There, `this.saveNote()` is a plain Java call: `this` is the raw
object, the call goes straight to its own method. The proxy stands outside, unaware.
Transaction: not open.

One line: the proxy intercepts only calls that arrive *from outside, through the
context's reference*. Calls that start inside the raw object never leave it.

### The example — two files (profile `l10-selfinvocation`)

`src/main/java/com/zenith/lifecycle/L10NotifyService.java`:

```java
package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("l10-selfinvocation")
class L10NotifyService {

    @Transactional
    public void saveNote() {
        System.out.println(">>> saveNote: this is " + this.getClass().getSimpleName()
                + ", transaction open? "
                + TransactionSynchronizationManager.isActualTransactionActive());
    }

    public void saveNoteTwice() {
        System.out.println(">>> saveNoteTwice: calling this.saveNote() — a self-invocation");
        this.saveNote();   // plain Java call on the raw object — no proxy on this path
    }
}
```

`src/main/java/com/zenith/lifecycle/L10SelfInvocationDemo.java`:

```java
package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("l10-selfinvocation")
class L10SelfInvocationDemo implements CommandLineRunner {

    private final L10NotifyService service;

    L10SelfInvocationDemo(L10NotifyService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> runner holds: " + service.getClass().getSimpleName());

        System.out.println(">>> --- call 1: service.saveNote(), from outside ---");
        service.saveNote();

        System.out.println(">>> --- call 2: service.saveNoteTwice(), which self-invokes saveNote() ---");
        service.saveNoteTwice();
    }
}
```

### Walkthrough — the real run

Postgres running (`docker start pg`), then:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-selfinvocation
```

Real output, complete:

```
>>> runner holds: L10NotifyService$$SpringCGLIB$$0
>>> --- call 1: service.saveNote(), from outside ---
>>> saveNote: this is L10NotifyService, transaction open? true
>>> --- call 2: service.saveNoteTwice(), which self-invokes saveNote() ---
>>> saveNoteTwice: calling this.saveNote() — a self-invocation
>>> saveNote: this is L10NotifyService, transaction open? false
```

1. The runner holds the **proxy** — that is what the context hands out.
2. Call 1, from outside: **true** — the proxy opened the transaction before forwarding.
3. Call 2, self-invocation: the *same method*, the *same print line* — **false**. Only
   the path changed; `this.saveNote()` never touched the proxy.
4. `this is L10NotifyService` in both calls: inside the method, `this` is always the
   **raw object**. Your code always runs on the raw object; the proxy is a separate
   object standing in front of it.

### Where it is used

- The silent transaction bug: a `@Transactional` method called from a sibling runs
  without a transaction — no rollback, half-written data. It is the call path, not the
  database.
- The same trap in other coats: `@Cacheable` self-invoked → cache never consulted;
  `@Async` self-invoked → same thread.
- The two standard fixes: (1) annotate the method callers actually enter through — the
  inner work then joins that transaction; (2) move the inner method into a separate bean
  and inject it — the call crosses a proxy boundary again.

### Common mistakes

- **Blaming the configuration.** If a method is transactional from the controller but
  not from a sibling method, it is self-invocation, every time.
- **"There is no proxy — `this.getClass()` prints my plain class!"** Inside the bean
  that is always true, proxy or not. Judge from outside: print the class of the injected
  reference.
- **Extracting to a `private` method and annotating it.** `@Transactional` on a private
  method does nothing in any call path: the generated subclass cannot override a private
  method.

### Link to the task

Subtask S4 — the sentence, written here as the notes demand, every word backed by a run:

> **"The proxy is created by a `BeanPostProcessor` during bean initialization, which is
> why self-invocation bypasses it: the raw object never knows the proxy exists."**

First half: Lesson 9's BEFORE/AFTER prints. Second half: Lesson 10's `true` then `false`
on the same method. This closes checkpoint question 4 and connects F6.10 (repositories
worked because callers held proxies) with F6.12 (the proxy that appeared by magic).

### Do it yourself

**Goal:** the same method printing `true` from outside and `false` via self-invocation.

Needs the Lesson 8 setup (dependencies, `application.properties`) and Postgres running:
`docker start pg`.

**Step 1 — create the two files** from "The example": `L10NotifyService.java`,
`L10SelfInvocationDemo.java`.

**Step 2 — run.**

```bash
cd ~/foundations/F6.13-who-builds-the-beans-lifecycle-and-beanpostprocessor/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-selfinvocation
```

Expected: the six `>>>` lines above — `true` for call 1, `false` for call 2.

Failures: no `>>>` lines → profile missing. `CannotCreateTransactionException ...
Connection to localhost:5432 refused` at call 1 → Postgres off, `docker start pg`.

**Step 3 — write the sentence** in your notes by hand and say it out loud twice,
pointing at the `true` and the `false` in your own log.

**Done when:** both values show for the same method, and the sentence comes out without
reading.

### Practice questions

**Question 1.** Same shape as the demo, with one change — the *outer* method is also
`@Transactional`:

```java
package demo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class OrderService {

    @Transactional
    public void place() {
        System.out.println("place: " + TransactionSynchronizationManager.isActualTransactionActive());
        this.audit();   // self-invocation
    }

    @Transactional
    public void audit() {
        System.out.println("audit: " + TransactionSynchronizationManager.isActualTransactionActive());
    }
}
```

A runner, holding the bean from the context, calls `orderService.place()`. What do the
two lines print — and why is the `audit` line *not* `false` this time, even though
`audit()` is reached by self-invocation?

*Answer:* Both print `true`. The runner holds the proxy, and `place()` is
`@Transactional`, so the proxy opened a transaction at the **entry method**. The
self-invoked `audit()` still bypasses the proxy — its own annotation adds nothing — but
the question it prints is "is a transaction open on this thread?", and the answer is
yes: the one `place()`'s proxy opened is still running. Lesson: the proxy acts at the
entry point; inner self-invoked work simply runs inside whatever transaction is already
open. (The bypass still matters: if `audit()` asked for special treatment of its own —
for example its own separate transaction — a self-invocation could never deliver it.)

**Question 2.** A teammate has this class, and complains that `record()` never runs in a
transaction when called via `submit()`:

```java
package demo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ClaimService {

    public void submit() {
        this.record();   // self-invocation — no transaction here
    }

    @Transactional
    public void record() {
    }
}
```

The teammate proposes: "move `record()` into a new `@Component class ClaimRecorder`,
inject `ClaimRecorder` into `ClaimService`, and call `recorder.record()` from
`submit()`." Explain, using the two-objects picture (raw object and proxy), why this fix
restores the transaction.

*Answer:* With `record()` in a separate bean, the context builds `ClaimRecorder`, swaps
it for a proxy at stage 6 (it has a `@Transactional` method), and injects **the proxy**
into `ClaimService`'s `recorder` field. Now `recorder.record()` is not a `this`-call any
more: it leaves `ClaimService`, arrives at the proxy from outside, the proxy opens the
transaction and forwards to the raw `ClaimRecorder`. Self-invocation has vanished
because the call now crosses a bean boundary — and every bean boundary crossing goes
through whatever object the context handed over, which is the proxy.

### Summary for revision

- Self-invocation = `this.method()` inside the same bean. It never reaches the proxy.
- Two objects exist: raw object (runs all your code; `this`) and proxy (held by
  consumers; adds behaviour at entry).
- Same method, two paths: through the proxy → `transaction open? true`; via
  self-invocation → `false`.
- Fixes: annotate the real entry method, or move the inner work to a separate injected
  bean.
- `@Transactional` on private methods never works; judge proxying from outside, not from
  `this.getClass()`.

---

## Checkpoint — the five questions, with full answers

**1. Recite the bean lifecycle stages in order, from instantiation to ready for use, and
say where destruction fits.**

Instantiate (Spring calls the constructor) → populate dependencies (injection fills
every `@Autowired` field and constructor-fed value) → aware callbacks (the bean is told
its name or handed the context, if it asked) → post-process **before** initialization
(every `BeanPostProcessor`'s before-hook) → initialize (`@PostConstruct` runs) →
post-process **after** initialization (every after-hook — the magic slot) → ready
(stored in the application context). Destruction is not a build stage: it is the short
mirror at context close — `@PreDestroy` runs, then the bean is discarded.

**2. What is a `BeanPostProcessor`, and why does "it may return a different object"
explain most of Spring's magic in one sentence?**

A bean implementing the `BeanPostProcessor` interface; Spring hands it **every bean**,
twice — before and after initialization — and **whatever object it returns is what the
context keeps**. The one sentence: because a post-processor may return a *different*
object, the framework can silently replace any bean with an upgraded stand-in — and
transactions, caching, and async are exactly such stand-ins, substituted at the
after-initialization slot.

**3. Who creates the `@Transactional` proxy, and at exactly which point in the
lifecycle?**

A `BeanPostProcessor` — Spring's ranked transaction proxy maker (internal class
`InfrastructureAdvisorAutoProxyCreator`) — creates it **during bean initialization, in
the post-process-after-initialization slot** (stage 6). Witnessed: the before-init hook
printed `com.zenith.lifecycle.L8BookingService`; the after-init hook printed
`com.zenith.lifecycle.L8BookingService$$SpringCGLIB$$0`.

**4. Using the lifecycle, explain why self-invocation bypasses the proxy — connect it to
F6.10 and F6.12.**

The raw object is fully built (stages 1–5) *before* the proxy exists (stage 6). The
proxy is a separate wrapping object; nothing is ever written into the raw object, so the
raw object never knows the proxy exists. Consumers get the proxy from the context, so
calls **from outside** are intercepted — that is why F6.10's repositories and services
were reliably transactional, and why F6.12's proxy could log around method calls. But
inside the raw object, `this` is the raw object: a self-invocation goes straight to its
own method and never touches the proxy — seen live as `transaction open? true` from
outside and `false` for the same method via self-invocation.

**5. Why does setup code that touches injected dependencies belong in `@PostConstruct`
rather than the constructor?**

The constructor is stage 1; injection is stage 2. At construction time, injection is not
yet guaranteed to be finished — an `@Autowired` field is still `null`, and using it
crashes startup (`NullPointerException ... because "this.seatMap" is null`, seen live).
`@PostConstruct` is stage 5, after population: when it runs, every dependency is
guaranteed filled. (Constructor injection makes the constructor's own parameters safe,
but the official "everything is wired" moment is still `@PostConstruct`.)

---

## Task F6.13 — done-when, verified

| Condition from the task | Evidence |
| --- | --- |
| Log shows constructor → `@PostConstruct` → `@PreDestroy` in order | Lesson 4's run: the six-line log, plus the Ctrl-C variant where `@PreDestroy` fired mid-work. |
| Your post-processor printed every bean in the application | Lesson 6's run: 41 beans through `L6BeanWatcher`, our classes mixed with the framework's, in dependency order. |
| You can name the exact point where the `@Transactional` proxy is created | Lesson 9's run: plain class at before-init, `$$SpringCGLIB$$0` at after-init — created by a ranked `BeanPostProcessor` in the post-process-after-initialization slot. |

The sentence to keep: **"the proxy is created by a `BeanPostProcessor` during bean
initialization, which is why self-invocation bypasses it: the raw object never knows the
proxy exists."**

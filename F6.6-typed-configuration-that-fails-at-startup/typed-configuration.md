# F6.6 — Typed configuration that fails at startup

**Goal of the task.** Bind the `zenith.hold` property group to a Java record, validate it
so that bad configuration stops the application at startup, and explain when `@Value` is
still reasonable.

**Done when:** an invalid or missing value under `zenith.hold` stops the application with
a message naming the property, and no `@Value` annotation remains in the service classes.

**You need first:** Task F6.4 (configuration files and the precedence order), Task F6.5
(profiles and secrets), Task F3.6 (records).

**The story in two sentences.** F6.4 proved that a mistyped property key is ignored in
silence: the app starts green, the value is simply gone. This task builds the opposite
behaviour — configuration that is wrong stops the application at startup, with a message
naming the exact property. That behaviour is called **failing fast**: detecting a problem
at the earliest possible moment.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **`@Value`** | Injects one property into one field or parameter, by writing its key as a string. |
| **`@ConfigurationProperties`** | Binds a whole group of properties onto the fields of one type, matched by name. |
| **Binding** | Matching property keys to fields and converting the text into the right Java type. |
| **`@Validated`** | Tells Spring to run validation rules on this object when it is created. |
| **Validation rule** | A statement about a value that must be true, written as an annotation. Example: `@Positive` = "must be greater than zero". |
| **Fail fast** | Detecting a problem at the earliest possible moment — here at startup, not on first use. |
| **Configuration metadata** | A small generated file that lets the IDE autocomplete your own property names. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Add `spring-boot-starter-validation` to `pom.xml` first, because its absence is invisible. |
| S2 | Inject `zenith.hold.ttl` and `zenith.hold.max-seats-per-order` with two `@Value` annotations. Note the three dislikes. |
| S3 | Bind the group to a `HoldProperties` record with `@Validated` and `@ConfigurationProperties`. Delete the `@Value` versions. |
| S4 | Register it with `@ConfigurationPropertiesScan`, inject it into `SeatHoldService`. |
| S5 | Notice the naming rule: `max-seats-per-order` in YAML ↔ `maxSeatsPerOrder` in Java. |
| S6 | Break it on purpose, three ways: `max-seats-per-order: 0`, delete `ttl`, and remove the validation dependency. |
| S7 | Add `spring-boot-configuration-processor`; your own keys autocomplete in IntelliJ. |
| S8 | Record the rule: when `@ConfigurationProperties`, when `@Value`. |
| S9 | Ask AI: nested properties and a `List` field. |
| S10 | Ask AI: why validation failures stop startup, and where in the lifecycle. |
| CP | Checkpoint: four questions, out loud. |

## Roadmap for this task — 12 small lessons

| # | Lesson | Serves |
| --- | --- | --- |
| 1 | The checker dependency that must come first | S1 |
| 2 | `@Value` — one property into one spot | S2 |
| 3 | Hands-on: `SeatHoldService` on `@Value`, and the three complaints | S2 |
| 4 | Binding — how text in a file becomes a typed Java value | S3, S5 |
| 5 | `@ConfigurationProperties` on a record | S3 |
| 6 | Registration — `@ConfigurationPropertiesScan` | S4 |
| 7 | Hands-on: bind, inject, delete every `@Value` | S3, S4, S5 |
| 8 | Validation rules on the record — `@Validated`, `@NotNull`, `@Positive`, `@Max` | S3 |
| 9 | Hands-on: break it on purpose, read the refusal | S6 |
| 10 | Hands-on: the nastiest trap — remove the checker, watch it pass | S6 |
| 11 | Configuration metadata — autocomplete for your own keys | S7 |
| 12 | The rule going forward, Ask-AI, checkpoint | S8, S9, S10, CP |

---

## Lesson 1 — The checker dependency that must come first (subtask S1)

### Definition

`spring-boot-starter-validation` is a dependency — a library your project declares in
`pom.xml` so Maven downloads it and puts it on the app's classpath (the set of jars the
app runs with). This dependency brings the **checker**: the library that actually
enforces validation rules.

### Why it is needed

A validation rule is a statement about a value that must be true. Example: "this number
must be greater than zero", written as the annotation `@Positive`. A validation rule has
**two halves**:

1. **The words.** The annotation itself, like `@Positive`. It is just a label. It does
   nothing by itself.
2. **The checker.** A separate library that reads those labels and enforces them.

Spring Boot's core does **not** include the checker. The web starter does not either —
it did before Spring Boot 2.3, and it does not now. If the checker is missing, every
validation annotation in the app is a label that nobody reads. No error, no warning.
Invalid values sail straight through.

That failure style is **silent-when-broken** — the same style as F6.4's mistyped key.
This task exists to make configuration fail loudly, so a silently sleeping checker would
defeat the whole task. That is why this dependency is subtask S1, before anything else.

### How it works

1. You add `spring-boot-starter-validation` to `pom.xml`. A **starter** is a bundle of
   related jars under one name.
2. Maven downloads the bundle. It contains three things:
   - `jakarta.validation-api` — the **words**: defines annotations like `@NotNull`, `@Positive`.
   - `hibernate-validator` — the **checker**: enforces the rules.
   - `tomcat-embed-el` — a small helper the checker uses to build error message texts.
3. At startup, Spring looks on the classpath for a checker. Found → every validation
   annotation is enforced. Not found → validation is skipped, silently.

Naming warning: `hibernate-validator` has **nothing to do with databases**. The
Hibernate team wrote two unrelated products — Hibernate ORM (the database library from
the JPA task) and Hibernate Validator (the rule checker). Same brand, different tools.

### The smallest example

Inside `<dependencies>` in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

No version number: the Spring Boot parent picks the matching version.

### Walkthrough — proof the checker is present

Absence of the checker prints nothing anywhere. No log line says "validation is off".
Presence, however, is checkable:

```bash
mvn dependency:tree
```

Real output, trimmed to the relevant lines:

```text
[INFO] \- org.springframework.boot:spring-boot-starter-validation:jar:3.5.16:compile
[INFO]    +- org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.55:compile
[INFO]    \- org.hibernate.validator:hibernate-validator:jar:8.0.3.Final:compile
[INFO]       +- jakarta.validation:jakarta.validation-api:jar:3.0.2:compile
```

Line by line:

- Line 1: the starter we declared.
- Line 2: the message-text helper it pulled in.
- Line 3: **`hibernate-validator` — the checker. This line is the proof.**
- Line 4: the words — the annotations the checker understands.

The only two ways to know the checker is active: look at this tree, or break a rule on
purpose and expect a refusal (Lessons 9 and 10 do exactly that).

### Where it is used

- Validating configuration at startup — this task.
- Validating incoming web requests: a controller marks a request body with `@Valid`, and
  bad input is rejected with a 400 error. Same checker, same trap.
- Any Spring service that declares rules on method parameters.

### Common mistakes

**Mistake 1 — "the web starter surely includes it."** It did, before Spring Boot 2.3.
Old tutorials carry the assumption forward. Today no starter except
`spring-boot-starter-validation` brings the checker.

**Mistake 2 — "it started without errors, so validation works."** Startup says nothing
about validation being on or off. Silence is not proof. Proof is the dependency tree, or
a deliberately broken value that gets refused.

**Mistake 3 — adding only the words.** Adding `jakarta.validation-api` alone compiles
fine: the annotations exist as words. But there is no checker, so nothing is enforced.
Always add the starter, which brings both halves.

### Link to the task

Subtask S1, word for word: "One dependency first, because its absence is invisible."
Load-bearing for S3 (the validated record) and S6 (the on-purpose breakages). Lesson 10
removes it deliberately, watches an invalid value pass, and puts it back.

### Do it yourself

1. Check your tools. You need Java 21 and Maven:

   ```bash
   java -version    # expect: openjdk version "21..."
   mvn -version     # expect: Apache Maven 3.9.x
   ```

   If missing on macOS: `brew install openjdk@21 maven`.

2. Create the folders:

   ```bash
   mkdir -p F6.6-typed-configuration-that-fails-at-startup/scratch-app/src/main/java/com/zenith/holds
   mkdir -p F6.6-typed-configuration-that-fails-at-startup/scratch-app/src/main/resources
   cd F6.6-typed-configuration-that-fails-at-startup/scratch-app
   ```

3. Create `pom.xml`:

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
       <artifactId>typed-config-scratch</artifactId>
       <version>0.0.1-SNAPSHOT</version>

       <properties>
           <java.version>21</java.version>
       </properties>

       <dependencies>
           <!-- Core Spring Boot: the container, no web server. The app starts, prints, exits. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter</artifactId>
           </dependency>

           <!-- The checker for validation rules (Hibernate Validator).
                Without this line, every @Validated and @Valid in the app is
                silently ignored. No error, no warning. Subtask S1. -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-validation</artifactId>
           </dependency>
       </dependencies>

       <build>
           <plugins>
               <!-- Makes `mvn package` produce a jar you can run with `java -jar`.
                    This task starts the app many times, healthy and broken. -->
               <plugin>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-maven-plugin</artifactId>
               </plugin>
           </plugins>
       </build>
   </project>
   ```

4. Create `src/main/java/com/zenith/holds/HoldsApplication.java`:

   ```java
   package com.zenith.holds;

   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;

   @SpringBootApplication
   public class HoldsApplication {
       public static void main(String[] args) {
           SpringApplication.run(HoldsApplication.class, args);
       }
   }
   ```

5. Create `src/main/resources/application.yml` — the same two hold values from F6.4:

   ```yaml
   zenith:
     hold:
       ttl: PT10M
       max-seats-per-order: 8
   ```

6. Build and run:

   ```bash
   mvn clean package
   java -jar target/typed-config-scratch-0.0.1-SNAPSHOT.jar
   echo $?
   ```

   Expected: the Spring banner, then a line like
   `Started HoldsApplication in 0.499 seconds`, then the app exits, and `echo $?`
   prints `0`.
   Failure case: without internet the build stops with
   `Could not transfer artifact ... spring-boot-starter-validation`. Connect and rerun.

7. See the proof that the checker is present:

   ```bash
   mvn dependency:tree | grep -E "validation|validator"
   ```

   Expected:

   ```text
   [INFO] \- org.springframework.boot:spring-boot-starter-validation:jar:3.5.16:compile
   [INFO]    \- org.hibernate.validator:hibernate-validator:jar:8.0.3.Final:compile
   [INFO]       +- jakarta.validation:jakarta.validation-api:jar:3.0.2:compile
   ```

**Done when:** the app runs and exits with code `0`, and the dependency tree shows
`hibernate-validator`.

### Check yourself

**Question 1.** A teammate's Spring Boot app contains this record. (`@NotNull` means
"this value must be present". `@Positive` means "this number must be greater than zero".
Both are validation rules, and `@Validated` asks Spring to enforce them when the object
is created.)

```java
@Validated
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(
        @NotNull Duration ttl,
        @Positive int maxSeatsPerOrder) {
}
```

In `application.yml` the teammate sets `max-seats-per-order: 0`. Zero is not greater
than zero, so the rule is broken. Yet the app starts cleanly, with no error at all. The
`pom.xml` lists exactly two dependencies: `spring-boot-starter` and
`spring-boot-starter-web`. What is the most likely cause, and what single line in
`pom.xml` fixes it?

*Model answer:* No checker is on the classpath. The annotations are only words; the
library that enforces them (Hibernate Validator) ships separately, and neither
`spring-boot-starter` nor `spring-boot-starter-web` includes it (the web starter stopped
including it in Spring Boot 2.3). So every rule is silently skipped and `0` sails
through. The fix is one dependency in `pom.xml`:
`<artifactId>spring-boot-starter-validation</artifactId>` (with groupId
`org.springframework.boot`).

**Question 2.** You suspect the problem from Question 1 in one of your own projects. You
do not want to guess — you want proof, from one command, of whether the rule checker is
on your app's classpath. Which command do you run, and which word do you look for in its
output?

*Model answer:* `mvn dependency:tree`. Look for `hibernate-validator` — that artifact is
the checker. If it appears, rules can be enforced; if it is absent, every validation
annotation in the app is silently inert.

### Summary

- A validation rule has two halves: the words (annotations, `jakarta.validation-api`)
  and the checker (`hibernate-validator`). Words without the checker do nothing.
- No starter except `spring-boot-starter-validation` brings the checker — the web
  starter stopped doing so in Spring Boot 2.3.
- A missing checker is **invisible**: no error, no warning, rules silently skipped.
  That is why this dependency is added first, before any rule exists.
- Presence is provable: `mvn dependency:tree` must show `hibernate-validator`.
- `hibernate-validator` is not a database tool. Hibernate ORM and Hibernate Validator
  are unrelated products from the same team.

---

## Lesson 2 — `@Value`: one property into one spot (subtask S2)

### Definition

`@Value` is an annotation you put on a field or on a constructor parameter. Inside it
you write a property key wrapped in `${...}`. When Spring creates the bean, it looks up
that key, converts the text to the parameter's Java type, and passes the result in.

### Why it is needed

In F6.4 configuration was read by injecting the whole `Environment` (Spring's merged
view of all property sources) and calling `environment.getProperty("zenith.hold.ttl")`.
Three pains:

1. **You fetch by hand.** Every value needs a method call.
2. **You convert by hand.** `getProperty` returns a `String`; you wrote
   `Duration.parse(...)` yourself.
3. **A missing key arrives as `null`.** The crash happens later, far from the cause.

`@Value` removes all three pains for a single value: Spring fetches, converts, and
injects at bean creation, and a missing key (without a default) stops startup. That is
one real step toward failing fast. But `@Value` checks only that a value is *present*,
not that it is *sensible* — `max-seats-per-order: 0` is injected happily.

### How it works

For `@Value("${zenith.hold.ttl}")` on a constructor parameter of type `Duration`:

1. Spring starts creating the bean, using constructor injection as usual.
2. It resolves the placeholder: it asks the `Environment` for `zenith.hold.ttl`.
   Sources answer in the F6.4 precedence order: command-line arguments beat environment
   variables, which beat profile-specific files, which beat `application.yml`.
3. The winning value is text, for example `PT10M`.
4. Spring converts the text to the parameter's type: `"PT10M"` → `Duration` of ten
   minutes; `"8"` → `int` 8.
5. The converted value goes into the constructor. The bean never knows the source.

Missing key: with `${key:fallback}` the fallback is used; without a default, startup
stops with an error naming the placeholder.

### The smallest example (compiled and run)

```java
package com.zenith.holds;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ValueDemo {

    private final Duration ttl;
    private final int maxSeats;

    public ValueDemo(
            @Value("${zenith.hold.ttl}") Duration ttl,
            @Value("${zenith.hold.max-seats-per-order}") int maxSeats) {
        this.ttl = ttl;
        this.maxSeats = maxSeats;
    }

    public String report() {
        return "ttl = " + ttl + " (" + ttl.toMinutes() + " minutes), maxSeats = " + maxSeats;
    }
}
```

With one print line in `main`:

```java
ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
System.out.println(context.getBean(ValueDemo.class).report());
```

Real output with `ttl: PT10M` and `max-seats-per-order: 8` in `application.yml`:

```text
ttl = PT10M (10 minutes), maxSeats = 8
```

Precedence still applies. The same jar run with an environment variable
(`zenith.hold.ttl` → `ZENITH_HOLD_TTL` by the F6.4 naming rule):

```bash
ZENITH_HOLD_TTL=PT2M java -jar target/typed-config-scratch-0.0.1-SNAPSHOT.jar
```

Real output: `ttl = PT2M (2 minutes), maxSeats = 8`. `@Value` reads through the same
`Environment`, so the environment variable beat the file.

### Where it is used

- A genuine one-off value: a single feature flag, one URL, one port.
- Very common in older codebases.
- Fine when one value is used in exactly one place and needs no rule checking.

### Common mistakes

**Mistake 1 — forgetting the `${}`.** `@Value("zenith.hold.ttl")` is not a lookup; the
string is the value itself. On a `Duration` parameter the app refused to start. Real
output, trimmed:

```text
Unsatisfied dependency expressed through constructor parameter 0:
Failed to convert value of type 'java.lang.String' to required type 'java.time.Duration'
...
Caused by: java.lang.IllegalArgumentException: 'zenith.hold.ttl' is not a valid duration
```

| Message piece | Plain meaning |
| --- | --- |
| `constructor parameter 0` | The first constructor parameter — `ttl`. |
| `Failed to convert ... String to ... Duration` | Spring took the annotation text as the value and tried to convert it. |
| `'zenith.hold.ttl' is not a valid duration` | The "value" being converted is the key name itself. |

On a `String` parameter the same mistake is **silent**: real output
`ttl = zenith.hold.ttl, maxSeats = 8`, exit 0. Text fits a `String`, so nothing fails.
Rule: `@Value` takes a template; only `${...}` triggers a lookup.

**Mistake 2 — actually good news: a typo'd key with no default is loud.** With
`${zenith.hold.tt1}` (digit one), the app refused to start, exit 1:

```text
Error creating bean with name 'valueDemo' ...
Caused by: org.springframework.util.PlaceholderResolutionException:
Could not resolve placeholder 'zenith.hold.tt1' in value "${zenith.hold.tt1}"
```

Compare F6.4: the same typo in `environment.getProperty(...)` returned `null` silently.
`@Value` without a default is stricter.

**Mistake 3 — a default swallows the typo.** `${zenith.hold.tt1:PT5M}` — still the
digit-one typo, now with a fallback. Real output: `ttl = PT5M (5 minutes), maxSeats = 8`,
clean start, exit 0. The file's `PT10M` sits unread under the correct key. A ticketing
app would hold seats for five minutes while everyone believes ten. Rule: give a default
only when the fallback is truly correct, not to make an error go away.

### Link to the task

Subtask S2: "Do it with `@Value` first so the comparison is real." Lesson 3 builds
`SeatHoldService` with these two injections and names the three dislikes: the key is a
string, the key gets repeated in other classes, and nothing checks the values.

### Check yourself

**Question 1.** Here is a complete bean, and the complete `application.yml` of its app:

```java
@Component
public class HoldReader {
    private final Duration ttl;

    public HoldReader(@Value("${zenith.hold.ttl}") Duration ttl) {
        this.ttl = ttl;
    }
}
```

```yaml
zenith:
  hold:
    ttl: PT10M
```

The app is started like this: `ZENITH_HOLD_TTL=PT2M java -jar app.jar`. Environment
variable names map to property keys by this rule: uppercase, dots become underscores,
hyphens removed — so `ZENITH_HOLD_TTL` supplies `zenith.hold.ttl`. Property sources
answer in this order: command-line arguments, then environment variables, then
profile-specific files, then `application.yml`. What value does `ttl` hold after
startup, and why?

*Model answer:* `PT2M` — two minutes. `@Value` resolves its placeholder through the
`Environment`, which searches sources in precedence order. The environment variable
`ZENITH_HOLD_TTL` supplies the key `zenith.hold.ttl` and sits higher than
`application.yml`, so `PT2M` wins over `PT10M`. (Proved by a real run: the app printed
`ttl = PT2M (2 minutes)`.)

**Question 2.** The only real key in a file is `zenith.hold.ttl` with value `PT10M`.
Two versions of one annotation both contain the same typo — digit one instead of the
letter L:

- Version A: `@Value("${zenith.hold.tt1}")`
- Version B: `@Value("${zenith.hold.tt1:PT5M}")`

For each version: does the app start, and what does the field hold? One of the two
outcomes is dangerous — which one, and why?

*Model answer:* Version A: the app refuses to start, exit code 1, with
`Could not resolve placeholder 'zenith.hold.tt1'`. The field never gets a value.
Version B: the app starts cleanly and the field holds `PT5M` — the default swallowed
the typo, and the file's `PT10M` sits unread under the correct key. The dangerous one
is B: it is silent. The app runs with a value nobody chose on purpose — holds expiring
at five minutes while the business believes ten. A is annoying but safe: it is loud,
immediate, and names the exact placeholder.

### Summary

- `@Value("${key}")` = look the key up in the `Environment` (full precedence order),
  convert the text to the parameter's type, inject at bean creation.
- Missing key without default → loud stop at startup, placeholder named. Missing key
  with default → the default is used, silently, even when the "missing" key is a typo.
- Forgetting `${}` makes the annotation text the value itself: loud on a `Duration`,
  fully silent on a `String`.
- `@Value` checks presence, not sense: `0` seats would be injected happily.
- Keep fields `final` and put `@Value` on constructor parameters, same house rule as
  F6.2.

---

## Lesson 3 — Hands-on: `SeatHoldService` on `@Value`, and the three complaints (subtask S2)

### What this lesson builds

The real service of this task. `SeatHoldService` places holds on seats; its two
settings arrive through two `@Value` annotations. A second bean needs one of the same
settings, so the same key string lives in two classes. Three drills then make the
task's three dislikes visible.

### Why it is needed

The task says: "Do it with `@Value` first so the comparison is real." Lesson 7 replaces
all of this with one typed record. Feeling the weaknesses first makes the record a fix
for watched problems, not fashion.

### The code

`src/main/java/com/zenith/holds/SeatHoldService.java`:

```java
package com.zenith.holds;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Places holds on seats. Subtask S2: configuration arrives through two
 * @Value annotations. It works — and Lesson 3 names what is wrong with it.
 */
@Service
public class SeatHoldService {

    private final Duration ttl;
    private final int maxSeatsPerOrder;

    public SeatHoldService(
            @Value("${zenith.hold.ttl}") Duration ttl,
            @Value("${zenith.hold.max-seats-per-order}") int maxSeatsPerOrder) {
        this.ttl = ttl;
        this.maxSeatsPerOrder = maxSeatsPerOrder;
    }

    public String describeHold(int requestedSeats) {
        if (requestedSeats > maxSeatsPerOrder) {
            return "REFUSED: " + requestedSeats + " seats requested, limit is " + maxSeatsPerOrder;
        }
        return "HELD: " + requestedSeats + " seats for " + ttl.toMinutes() + " minutes";
    }
}
```

(`@Service` = `@Component` with a name that says "business logic".)

`src/main/java/com/zenith/holds/HoldExpiryNotifier.java`:

```java
package com.zenith.holds;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A second bean that needs the same TTL — so the same string key now
 * lives in two places. Dislike number two, made real.
 */
@Component
public class HoldExpiryNotifier {

    private final Duration ttl;

    public HoldExpiryNotifier(@Value("${zenith.hold.ttl}") Duration ttl) {
        this.ttl = ttl;
    }

    public String reminderLine() {
        return "Reminder scheduled " + ttl.minusMinutes(1).toMinutes()
                + " minutes after hold, 1 minute before expiry";
    }
}
```

`main` in `HoldsApplication.java`:

```java
ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
SeatHoldService holds = context.getBean(SeatHoldService.class);
System.out.println(holds.describeHold(3));
System.out.println(holds.describeHold(12));
System.out.println(context.getBean(HoldExpiryNotifier.class).reminderLine());
```

Real output with `ttl: PT10M` and `max-seats-per-order: 8`:

```text
HELD: 3 seats for 10 minutes
REFUSED: 12 seats requested, limit is 8
Reminder scheduled 9 minutes after hold, 1 minute before expiry
```

### The three complaints, each with evidence

**Complaint 1 — the key is a string, invisible to the compiler.** Key changed to
`zenith.hold.tt1` (digit one), then rebuilt. Real result: `BUILD SUCCESS`. Only the run
fails, exit code 1:

```text
Error creating bean with name 'seatHoldService' ...
Caused by: ... Could not resolve placeholder 'zenith.hold.tt1' in value "${zenith.hold.tt1}"
```

| Message piece | Plain meaning |
| --- | --- |
| `Error creating bean with name 'seatHoldService'` | The failure happened while building this bean. |
| `Could not resolve placeholder 'zenith.hold.tt1'` | No source has this key. The typo is quoted back. |

Loud, but caught only at startup — a key is just text inside quotes to the compiler.

**Complaint 2 — the same key repeated across classes.** `"${zenith.hold.ttl}"` lives in
`SeatHoldService` and in `HoldExpiryNotifier`. Rename the key once in YAML, and every
copy must be hunted by hand. Here two classes; in real codebases dozens, with no tool
listing them.

**Complaint 3 — nothing checks the values.** With `max-seats-per-order: 0` the app
started perfectly, exit code 0. Real output:

```text
REFUSED: 3 seats requested, limit is 0
REFUSED: 12 seats requested, limit is 0
Reminder scheduled 9 minutes after hold, 1 minute before expiry
```

Green app, clean logs, and nobody can buy a ticket. Zero is present and is a valid
`int`, so `@Value` is satisfied. This exact value stops the app at startup in Lesson 9,
once a validation rule exists.

### Common mistakes

**Mistake 1 — editing `application.yml` and rerunning without rebuilding.** The jar
carries its own copy of the file. Until `mvn clean package` runs again, `java -jar`
uses the old values. If a drill "does nothing", rebuild first.

**Mistake 2 — expecting the `0` drill to fail.** There is no referee yet. Refusal
arrives in Lesson 8, when validation rules go onto a record.

**Mistake 3 — `@Value` on fields instead of constructor parameters.** Works, but the
field cannot be `final`, and the class cannot be created honestly in a plain unit test.
Same reasoning as the F6.2 rule against field `@Autowired`.

### Link to the task

Completes subtask S2: both `@Value` injections work, and the three dislikes come from
real runs. Lessons 4–7 build the "after": one record, one prefix, zero string keys in
services.

### Do it yourself

Start from the Lesson 1 state (`pom.xml`, `HoldsApplication.java`, `application.yml`
with `ttl: PT10M`, `max-seats-per-order: 8` — all under Lesson 1 above).

1. Create `SeatHoldService.java` exactly as shown above.
2. Create `HoldExpiryNotifier.java` exactly as shown above.
3. Replace `HoldsApplication.java`:

   ```java
   package com.zenith.holds;

   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.context.ApplicationContext;

   @SpringBootApplication
   public class HoldsApplication {
       public static void main(String[] args) {
           ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
           SeatHoldService holds = context.getBean(SeatHoldService.class);
           System.out.println(holds.describeHold(3));
           System.out.println(holds.describeHold(12));
           System.out.println(context.getBean(HoldExpiryNotifier.class).reminderLine());
       }
   }
   ```

4. `mvn clean package` then `java -jar target/typed-config-scratch-0.0.1-SNAPSHOT.jar`.
   Expected after the startup lines:

   ```text
   HELD: 3 seats for 10 minutes
   REFUSED: 12 seats requested, limit is 8
   Reminder scheduled 9 minutes after hold, 1 minute before expiry
   ```

5. Drill A — nothing checks the values. Set `max-seats-per-order: 0`, rebuild, run,
   `echo $?`. Expected: every order `REFUSED ... limit is 0`, exit code `0`. Say out
   loud: the app is green and no customer can buy a ticket.
6. Drill B — strings are invisible to the compiler. Put `8` back. In
   `SeatHoldService`, change the first key to `zenith.hold.tt1` (digit one). Then
   `mvn clean package` (expected: `BUILD SUCCESS`), run, `echo $?`. Expected: startup
   fails with `Could not resolve placeholder 'zenith.hold.tt1'`, exit code `1`.
7. Repair: key back to `zenith.hold.ttl`. Rebuild, run, confirm the three healthy
   lines and exit code `0`.

**Done when:** you saw the three healthy lines, the all-refusals run with exit code
`0`, and `BUILD SUCCESS` followed by the placeholder crash — and you can say the three
complaints in your own words.

### Check yourself

**Question 1.** A service contains this constructor parameter:
`@Value("${zenith.hold.max-seats-per-order}") int maxSeatsPerOrder`. The service
refuses any order asking for more than `maxSeatsPerOrder` seats. In `application.yml`
someone sets `max-seats-per-order: 0`. Two teammates predict the next startup:

- Teammate A: "The app will fail to start. A limit of zero is useless, and Spring will notice."
- Teammate B: "The app will start cleanly and refuse every single order."

Who is right, and why?

*Model answer:* Teammate B. `@Value` checks two things only: the key is present
somewhere, and the text converts to the declared type. `0` is present and is a valid
`int`, so injection succeeds and the app starts. The service then compares every
request against a limit of zero and refuses it. No rule anywhere says the limit must be
positive — that referee arrives only with validation on a record. (Proved by run: all
orders `REFUSED`, exit code 0.)

**Question 2.** A codebase injects the key `zenith.hold.ttl` with `@Value` in seven
different classes. The team renames the key in `application.yml` to
`zenith.hold.hold-ttl` and updates six of the seven annotations. The seventh still
reads `@Value("${zenith.hold.ttl:PT1M}")` — note it has a default. Three questions:
does `mvn clean package` fail? Does startup fail? What value does the seventh class run
with, and would anyone notice?

*Model answer:* The build passes — keys are strings, and the compiler does not read
them. Startup also passes — six classes resolve the new key, and the seventh finds its
key missing but falls back to its default. The seventh class runs with `PT1M`, one
minute, silently. Nobody notices from logs; it surfaces as behaviour — holds expiring
after one minute while everyone believes ten. Without the default, startup would have
failed loudly, naming the placeholder. Defaults added "just to be safe" hide exactly
this class of mistake.

### Summary

- Two `@Value` injections power `SeatHoldService`; the same key string also sits in
  `HoldExpiryNotifier`.
- Complaint 1: keys are strings — `BUILD SUCCESS`, then a startup crash, never a
  compile error.
- Complaint 2: every consumer repeats the key; renames become manual hunts.
- Complaint 3: values are unchecked — `0` seats = green app, zero sales.
- Edit the YAML → rebuild before rerunning; the jar carries its own copy.

---

## Lesson 4 — Binding: how text in a file becomes a typed Java value (subtasks S3, S5)

### Definition

**Binding** is the act of filling a whole Java object from configuration. Spring
matches property keys to the object's field names, converts each text value into the
declared Java type, and constructs the object. Two halves: **matching** (which key
feeds which field) and **converting** (how text becomes `Duration`, `int`, ...).

### Why it is needed

With `@Value` you write the key string yourself, in every class that needs the value.
Binding flips the direction: you declare a Java type, and the names inside it carry the
mapping. No string keys in your code. For that to be trustworthy the rules must be
exact: which spelling matches which name, and who converts the text. These rules make
`@ConfigurationProperties` predictable instead of magic.

### How it works

**Matching half.** Prefix `zenith.hold`, field `maxSeatsPerOrder`:

1. Java side: camelCase — words glued together, each new word capitalised:
   `maxSeatsPerOrder`.
2. File side: kebab-case — lowercase words joined by hyphens: `max-seats-per-order`.
3. Full key = prefix + dot + kebab name: `zenith.hold.max-seats-per-order`.
4. The matcher is forgiving: `max-seats-per-order`, `maxSeatsPerOrder`,
   `max_seats_per_order`, and the env var `ZENITH_HOLD_MAXSEATSPERORDER` all bind to
   the same field. This forgiveness is **relaxed binding** (same term as F6.4).
   Convention stays: kebab in files, camel in Java.

**Converting half.** The matched value is text. Spring converts to the declared type:
`"PT10M"` → ten-minute `Duration` (ISO-8601), `"8"` → `int`, `"true"` → `boolean`,
YAML lists → `List`.

**Sources.** Binding reads the merged `Environment` — all sources, F6.4 precedence:
arguments beat env vars beat profile files beat `application.yml`.

### The measuring instrument (compiled and run)

A preview of Lessons 5–7 machinery:

```java
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        Environment env = context.getEnvironment();
        System.out.println("getProperty exact : " + env.getProperty("zenith.hold.max-seats-per-order"));
        System.out.println("getProperty camel : " + env.getProperty("zenith.hold.maxSeatsPerOrder"));
        System.out.println("bound record      : " + context.getBean(HoldProperties.class));
    }
}
```

Real output with `ttl: PT10M` and `max-seats-per-order: 8` in the file:

```text
getProperty exact : 8
getProperty camel : null
bound record      : HoldProperties[ttl=PT10M, maxSeatsPerOrder=8]
```

Read it:

- **Line 3 — the naming rule, proven.** File says `max-seats-per-order`; field says
  `maxSeatsPerOrder`; no string key connects them. Trace: strip prefix → drop hyphens,
  capitalise following letters → match → convert `"8"` → `int`.
- **Lines 1 and 2 — the boundary of forgiveness.** Plain `getProperty` is an exact
  string lookup: the camel spelling exists nowhere in the file → `null`. Relaxed
  spelling belongs to binding only. `@Value` placeholders are exact in the same way.
- **Precedence run.** `ZENITH_HOLD_MAXSEATSPERORDER=5 java -jar ...` → real output:
  `bound record : HoldProperties[ttl=PT10M, maxSeatsPerOrder=5]`. File still says 8;
  the env var won. (The two `getProperty` lines also showed 5 — the env-var source
  translates its own names, F6.4's relaxed rule for env vars.)

### Where it is used

Every Boot property ever set — `spring.datasource.url` (F5.11), `server.port`,
`spring.profiles.active` — lands in Boot's own properties objects through this exact
machinery. The next lessons point the same machinery at your own keys.

### Common mistakes

**Mistake 1 — camelCase keys in YAML.** `maxSeatsPerOrder: 8` binds (relaxed), but
files should stay kebab-case: convention everywhere, and one spelling is greppable.

**Mistake 2 — expecting relaxed matching outside binding.** `getProperty` and `@Value`
are exact. Proof above: camel lookup → `null` while binding bound the same pair.

**Mistake 3 — thinking binding reads only `application.yml`.** It reads the whole
`Environment` with precedence. A stale exported env var silently beats a file edit.
When a value seems ignored, ask which higher source answers first (F6.4 rule).

### Link to the task

Subtask S5 word for word: `max-seats-per-order` in YAML binds to `maxSeatsPerOrder` in
Java; you never write the string key in Java again. Foundation for S3: the record
carries names and types, binding supplies the values.

### Check yourself

**Question 1.** A record is declared with
`@ConfigurationProperties(prefix = "zenith.hold")` and has one component:
`int maxSeatsPerOrder`. (a) Write the YAML snippet, nested form, file-side naming
convention, that sets this value to 6. (b) Name one other spelling of the key that
would still bind to the same component.

*Model answer:* (a)

```yaml
zenith:
  hold:
    max-seats-per-order: 6
```

(b) Any of: `maxSeatsPerOrder: 6` or `max_seats_per_order: 6` in the file, or the
environment variable `ZENITH_HOLD_MAXSEATSPERORDER=6`. All reach the same component via
relaxed binding.

**Question 2.** An app's complete `application.yml` is:

```yaml
zenith:
  hold:
    max-seats-per-order: 8
```

Read (a): `environment.getProperty("zenith.hold.maxSeatsPerOrder")` — a plain
`Environment` lookup with the camelCase spelling. Read (b): binding of prefix
`zenith.hold` onto a record with component `int maxSeatsPerOrder`. What does each read
produce, and why are the results different?

*Model answer:* (a) `null`. A plain lookup is an exact string comparison; the file
stores only the kebab-case key, and the camel spelling is a different string. (b) `8`.
The binder normalises spellings — `max-seats-per-order` and `maxSeatsPerOrder` are the
same name to it — then converts `"8"` to `int`. The difference: forgiveness lives in
the binder, not in the `Environment`. (Both proved by the run above: `null` and `8` in
one output.)

### Summary

- Binding = matching + converting, fed by the whole `Environment` in precedence order.
- Naming rule: kebab-case in files ↔ camelCase in Java; full key = prefix + dot + name.
- Relaxed binding accepts several spellings; write the canonical pair anyway.
- Plain `getProperty` and `@Value` are exact lookups — no forgiveness there.
- You have used this machinery all along for `spring.*`; now it serves your own keys.

---

## Lesson 5 — `@ConfigurationProperties` on a record (subtask S3)

### Definition

`@ConfigurationProperties(prefix = "zenith.hold")` is an annotation you put on a type.
It declares: "this type is a target for binding — fill it from the property group that
starts at this prefix." On a record, Spring fills it through the constructor, once, at
startup.

### Why it is needed

It answers Lesson 3's three complaints structurally:

- **One object for the whole group.** "The hold settings" becomes a real thing to
  inject, print, and pass around.
- **Names instead of keys.** Binding matches by name; no `"${...}"` strings anywhere.
- **One doorway.** Every value enters through one constructor — the place where
  validation will attach in Lesson 8.

Record reminder (F3.6): components declared in the header; each becomes a `final`
field with an accessor; one canonical constructor; `toString`/`equals` free. A record
cannot be changed after creation — and configuration should be read once and never
mutated at runtime.

### How it works

1. Spring learns `HoldProperties` is a binding target with prefix `zenith.hold`
   (how it learns = registration, Lesson 6).
2. The binder collects the group's keys from the merged `Environment` (all sources,
   F6.4 precedence).
3. Components match by name: `ttl` ↔ `zenith.hold.ttl`, `maxSeatsPerOrder` ↔
   `zenith.hold.max-seats-per-order`.
4. Text converts to types: `"PT10M"` → `Duration`, `"8"` → `int`.
5. Spring calls the record constructor once with the converted values —
   **constructor binding**. One constructor, no ambiguity, immutable result.
6. The finished object is a bean, injectable like any other.

### The smallest example (compiled and run)

```java
package com.zenith.holds;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

With `ttl: PT10M` and `max-seats-per-order: 8`, printing the bean gives:

```text
bound record: HoldProperties[ttl=PT10M, maxSeatsPerOrder=8]
```

String keys in the Java file: zero. The printed line is the record's free `toString`.

### The honest limit — typed is not validated (real runs)

Two experiments with this bare record (no validation anywhere, `@Value` services
temporarily removed so they could not interfere):

**Experiment 1 — delete `ttl` from the file.** App starts green, exit 0:

```text
bound record: HoldProperties[ttl=null, maxSeatsPerOrder=8]
```

A missing object-typed value binds as `null` and waits for the first
`ttl.toMinutes()` — a `NullPointerException` far from the cause.

**Experiment 2 — delete `max-seats-per-order`.** Green again, exit 0:

```text
bound record: HoldProperties[ttl=PT10M, maxSeatsPerOrder=0]
```

A missing primitive becomes `0` — absence silently manufactured the exact value that
refuses every order.

Contrast: the same absence under `@Value` without a default crashed loudly
(`Could not resolve placeholder 'zenith.hold.ttl'`). Score so far: `@Value` is loud on
missing keys but scatters strings and checks nothing else; the bare record gives
structure and types but is silent on absence. The referee is validation — Lesson 8.

### Where it is used

Boot itself: `server.*` fills `ServerProperties`, `spring.datasource.*` fills
`DataSourceProperties` (behind F5.11). Real services keep one properties type per
feature: holds, payments, mail.

### Common mistakes

**Mistake 1 — believing the record alone fails fast.** Absence becomes `null` or `0`,
silently (runs above). Binding fills; it does not judge.

**Mistake 2 — `new HoldProperties(...)` expecting configuration.** A hand-made record
holds whatever you pass. Only the container-built bean went through binding. (Handing a
test `new HoldProperties(Duration.ofMinutes(5), 4)` is fine — but those are your
values, not the file's.)

**Mistake 3 — a mutable class with getters and setters.** Works, but reopens mutation
of configuration at runtime. New code: record, constructor binding, immutable.

### Link to the task

The heart of subtask S3, deliberately half-done: the task's snippet also carries
`@Validated`, `@NotNull`, `@Positive`, `@Max` — Lesson 8. Registration is Lesson 6;
Lesson 7 assembles everything and deletes the `@Value` annotations.

### Check yourself

**Question 1.** Here is a complete record:

```java
@ConfigurationProperties(prefix = "zenith.payments")
record PaymentProperties(String providerUrl, int timeoutSeconds) {
}
```

Write the nested YAML snippet that sets the provider URL to
`https://pay.example.com` and the timeout to `30`, using the file-side naming
convention. Then say which record component each key feeds.

*Model answer:*

```yaml
zenith:
  payments:
    provider-url: https://pay.example.com
    timeout-seconds: 30
```

`provider-url` feeds `providerUrl` (kebab → camel), and `timeout-seconds` feeds
`timeoutSeconds`. Both sit under the prefix `zenith.payments`, so the full keys are
`zenith.payments.provider-url` and `zenith.payments.timeout-seconds`.

**Question 2.** An app contains this registered record, with no validation annotations
anywhere in the project:

```java
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

The complete `application.yml` is:

```yaml
zenith:
  hold:
    max-seats-per-order: 8
```

`ttl` is absent. Does the app start? What exact values does the record hold? When and
where does the trouble finally surface?

*Model answer:* The app starts cleanly. The record holds
`HoldProperties[ttl=null, maxSeatsPerOrder=8]` — a missing object-typed component binds
as `null` when no validation demands it. The trouble surfaces at the first *use* of
`ttl` — for example `ttl.toMinutes()` throws a `NullPointerException` — which can be
minutes or hours after startup, in a stack trace pointing at business code, far from
the real cause (one missing line of YAML). This is exactly the late failure that
`@NotNull` plus `@Validated` converts into a startup refusal naming the property.
(Proved by run: green start, `ttl=null` printed.)

### Summary

- `@ConfigurationProperties(prefix = ...)` declares a binding target; on a record,
  values arrive once, through the constructor (constructor binding).
- The record carries names and types only — zero string keys in Java.
- The bean is injectable like any other; `toString` prints what actually arrived.
- Typed ≠ validated: missing keys bind as `null` (objects) or `0` (primitives),
  silently. Validation closes that gap in Lesson 8.
- Boot's own `server.*` and `spring.datasource.*` work exactly this way.

---

## Lesson 6 — Registration: `@ConfigurationPropertiesScan` (subtask S4)

### Definition

`@ConfigurationPropertiesScan` is an annotation for the application class, next to
`@SpringBootApplication`. It tells Spring: search my packages for types marked
`@ConfigurationProperties`, and register each one as a bound bean.

### Why it is needed

The app has two different bean factories:

- **Factory 1 — dependency injection** (F6.1/F6.2). Component scanning finds
  `@Component`/`@Service` classes. Their constructor parameters are **other beans**.
- **Factory 2 — binding** (Lessons 4–5). It builds `@ConfigurationProperties` types.
  Their constructor parameters are **values** converted from configuration text.

The record's constructor wants a `Duration` and an `int` — values, not beans. Factory 1
cannot build it, so `@ConfigurationProperties` types are deliberately invisible to
component scanning. They need their own registration route into Factory 2. Without one,
the record is dead code: compiled, shipped, never read.

Proof run — record present and correctly annotated, application class with only
`@SpringBootApplication`:

```text
Started HoldsApplication in 0.464 seconds
...
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException:
No qualifying bean of type 'com.zenith.holds.HoldProperties' available
```

Exit code 1. Startup **succeeded** first; the crash came at the first fetch.

### How it works

1. One line on the application class:

   ```java
   @SpringBootApplication
   @ConfigurationPropertiesScan
   public class HoldsApplication { ... }
   ```

2. The scan walks the application class's package and below (same scope rule as
   component scanning).
3. Every `@ConfigurationProperties` type found is built once by the binder and placed
   in the context.
4. From then on it injects like any other bean.

Explicit alternative: `@EnableConfigurationProperties(HoldProperties.class)` — lists
types one by one; common in tests and libraries. For applications, the scan is the
default: new properties types register themselves by existing.

### The smallest example

Same app plus the one line. Real output:

```text
bound record: HoldProperties[ttl=PT10M, maxSeatsPerOrder=8]
```

One line was the difference between dead code and a bound bean.

### Decoding the failure

| Message piece | Plain meaning |
| --- | --- |
| `Started HoldsApplication ...` | Startup finished; nothing needed the record during startup. |
| `NoSuchBeanDefinitionException` | Someone asked the context for a bean that does not exist. |
| `No qualifying bean of type '...HoldProperties'` | Java knows the type (it compiled); Spring never registered it. |

Timing: in the demo, `main` asked after startup, so the app went green first. In a real
app a service constructor asks during startup, and startup itself fails with the same
"no qualifying bean" complaint.

### Common mistakes

**Mistake 1 — forgetting registration.** Symptom: `No qualifying bean of type
'HoldProperties'`. The reflex of sprinkling `@Component`/`@Autowired` does not help.
The fix is one scan line on the application class.

**Mistake 2 — `@Component` on the record.** Sends it to the wrong factory. Real output:

```text
APPLICATION FAILED TO START
***************************

Description:

HoldProperties is annotated with @ConstructorBinding but it is defined as a regular bean which caused dependency injection to fail.

Action:

Update your configuration so that HoldProperties is defined via @ConfigurationPropertiesScan or @EnableConfigurationProperties.
```

"Defined as a regular bean" = Factory 1 tried to autowire a `Duration` and an `int` as
if they were beans. The Action line prescribes both correct routes — the same
Description/Action analyzer format from F6.5.

**Mistake 3 — properties type outside the scanned packages.** The scan covers the
application class's package and below; a record in a sibling package is missed, giving
Mistake 1's symptom with the annotation plainly present. Keep properties types under
the root package (ours: `com.zenith.holds`, same package as the app class).

### Link to the task

First half of subtask S4: "Register it with `@ConfigurationPropertiesScan` on your
application class." The second half — inject `HoldProperties` into `SeatHoldService` —
is Lesson 7.

### Check yourself

**Question 1.** An app contains these two complete files (plus a healthy
`application.yml` with both keys present):

```java
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

```java
@SpringBootApplication
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        System.out.println(context.getBean(HoldProperties.class));
    }
}
```

`mvn clean package` succeeds. What exactly happens when the jar runs — including the
order of events — and what single line fixes it?

*Model answer:* The app starts normally and logs `Started HoldsApplication ...`,
because nothing needed the record during startup. Then `main` asks for the bean, and
the run crashes with `NoSuchBeanDefinitionException: No qualifying bean of type
'com.zenith.holds.HoldProperties' available`, exit code 1. The record compiled into the
jar, but component scanning ignores `@ConfigurationProperties` types, so it was never
registered. Fix: add `@ConfigurationPropertiesScan` under `@SpringBootApplication`
(alternative: `@EnableConfigurationProperties(HoldProperties.class)`).

**Question 2.** A teammate "fixes" Question 1 differently — they add `@Component` on
top of the record:

```java
@Component
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

The app now refuses to start. Why can the normal bean factory not build this record,
and what does Spring's error message tell the teammate to do instead?

*Model answer:* The normal factory builds beans by dependency injection: every
constructor parameter must be another bean. This record's constructor wants a
`Duration` and an `int` — configuration values, not beans — so injection fails. Spring's
failure analyzer says it directly: "HoldProperties is annotated with
@ConstructorBinding but it is defined as a regular bean which caused dependency
injection to fail", and the Action line prescribes the fix: define it via
`@ConfigurationPropertiesScan` or `@EnableConfigurationProperties` — that is, remove
`@Component` and use a registration route into the binder.

### Summary

- Two factories: dependency injection supplies beans; binding supplies converted
  values. Records of configuration belong to the binder.
- `@ConfigurationProperties` types are invisible to component scanning on purpose;
  registration hands them to the binder.
- `@ConfigurationPropertiesScan` next to `@SpringBootApplication` covers the root
  package and below; `@EnableConfigurationProperties(X.class)` registers explicitly.
- Missing registration = `No qualifying bean of type ...` at first use.
- `@Component` on the record = "annotated with @ConstructorBinding but defined as a
  regular bean" — a loud, self-explaining analyzer error.

---

## Lesson 7 — Hands-on: bind, inject, delete every `@Value` (subtasks S3, S4, S5)

### What this lesson builds

The assembly: the record (Lesson 5) + the scan line (Lesson 6) + ordinary injection
(F6.2), and the deletion of every `@Value`. Behaviour stays identical; the plumbing
changes completely.

### The code — four files

**New** `src/main/java/com/zenith/holds/HoldProperties.java`:

```java
package com.zenith.holds;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The whole zenith.hold group as one typed, immutable object.
 * Names carry the mapping: ttl <- zenith.hold.ttl,
 * maxSeatsPerOrder <- zenith.hold.max-seats-per-order.
 */
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

**Changed** `HoldsApplication.java` — one annotation added, prints unchanged:

```java
package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        SeatHoldService holds = context.getBean(SeatHoldService.class);
        System.out.println(holds.describeHold(3));
        System.out.println(holds.describeHold(12));
        System.out.println(context.getBean(HoldExpiryNotifier.class).reminderLine());
    }
}
```

**Changed** `SeatHoldService.java` — two `@Value` parameters become one typed bean:

```java
package com.zenith.holds;

import org.springframework.stereotype.Service;

/**
 * Places holds on seats. Subtasks S3+S4: configuration arrives as one
 * typed object, injected like any other bean. No string keys here.
 */
@Service
public class SeatHoldService {

    private final HoldProperties properties;

    public SeatHoldService(HoldProperties properties) {
        this.properties = properties;
    }

    public String describeHold(int requestedSeats) {
        if (requestedSeats > properties.maxSeatsPerOrder()) {
            return "REFUSED: " + requestedSeats + " seats requested, limit is "
                    + properties.maxSeatsPerOrder();
        }
        return "HELD: " + requestedSeats + " seats for "
                + properties.ttl().toMinutes() + " minutes";
    }
}
```

**Changed** `HoldExpiryNotifier.java` — the duplicated key is gone:

```java
package com.zenith.holds;

import org.springframework.stereotype.Component;

/**
 * Also needs the TTL. In Lesson 3 this class repeated the string key.
 * Now both consumers share one typed object; the key lives only in YAML.
 */
@Component
public class HoldExpiryNotifier {

    private final HoldProperties properties;

    public HoldExpiryNotifier(HoldProperties properties) {
        this.properties = properties;
    }

    public String reminderLine() {
        return "Reminder scheduled " + properties.ttl().minusMinutes(1).toMinutes()
                + " minutes after hold, 1 minute before expiry";
    }
}
```

Real output after rebuild — character for character the Lesson 3 output:

```text
HELD: 3 seats for 10 minutes
REFUSED: 12 seats requested, limit is 8
Reminder scheduled 9 minutes after hold, 1 minute before expiry
```

Subtask S5 proven: `max-seats-per-order` fed `maxSeatsPerOrder` with no string key in
Java. Precedence proven: `ZENITH_HOLD_MAXSEATSPERORDER=5 java -jar ...` → second line
became `REFUSED: 12 seats requested, limit is 5`.

### What changed, in words

- Services ask for `HoldProperties` like any dependency. The binder built it;
  dependency injection delivers it; the service cannot tell the difference.
- Values are read through typed accessor methods: `properties.ttl()`,
  `properties.maxSeatsPerOrder()` — autocompleted and compiler-checked.
- Audit (real runs): `grep -rn "@Value" src/` prints nothing. The only configuration
  string left in Java is the prefix `"zenith.hold"`, once, in one file.

Scoreboard vs Lesson 3's complaints: string keys — gone; repeated keys — gone (one
shared bean); unchecked values — **still open** (missing `ttl` → `null`, missing limit
→ `0`, silently). Lessons 8–9 close it.

### Common mistakes

**Mistake 1 — record access without parentheses.** `properties.ttl` instead of
`properties.ttl()`. Real compiler output:

```text
[ERROR] ... SeatHoldService.java:[24,29] ttl has private access in com.zenith.holds.HoldProperties
```

A record stores each component in a private field and exposes a **method** of the same
name. Write `properties.ttl()`.

**Mistake 2 — deleting the scan line in the assembled app.** Startup itself now fails
(unlike Lesson 6's demo, where `main` asked after startup). Real output:

```text
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.holds.HoldExpiryNotifier required a bean of type 'com.zenith.holds.HoldProperties' that could not be found.

Action:

Consider defining a bean of type 'com.zenith.holds.HoldProperties' in your configuration.
```

A consumer demands the bean during context refresh, so the failure moved earlier — and
louder is better. (Which consumer is named depends on bean creation order.)

**Mistake 3 — half-migration.** One class on the record, another still on `@Value`.
Runs, but the old risks live on. The audit is mechanical: the `@Value` grep must be
empty.

### Link to the task

Completes S3's binding half, all of S4, and S5. Part of the task's "done when" is now
true: no `@Value` remains in the service classes. Invalid values stopping startup —
Lessons 8 and 9.

### Do it yourself

Start from the Lesson 3 state (the `@Value` version).

1. Create `HoldProperties.java` as above.
2. Add `@ConfigurationPropertiesScan` (plus import) to `HoldsApplication.java`.
3. Replace `SeatHoldService.java` and `HoldExpiryNotifier.java` as above.
4. `mvn clean package && java -jar target/typed-config-scratch-0.0.1-SNAPSHOT.jar` —
   expect exactly the three Lesson 3 lines.
5. Audit: `grep -rn "@Value" src/` — expect no output.
6. Drill: delete the `@ConfigurationPropertiesScan` line, rebuild, run. Expect
   `APPLICATION FAILED TO START` with `required a bean of type
   'com.zenith.holds.HoldProperties' that could not be found`. Restore the line,
   rebuild, confirm green.
7. Optional: `ZENITH_HOLD_MAXSEATSPERORDER=5 java -jar ...` — expect
   `REFUSED: 12 seats requested, limit is 5`.

**Done when:** output matches Lesson 3 exactly, the `@Value` grep is empty, and you
watched the missing-scan failure and repaired it.

### Check yourself

**Question 1.** Given this complete record and service (registered with
`@ConfigurationPropertiesScan`, no validation involved):

```java
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(Duration ttl, int maxSeatsPerOrder) {
}
```

```java
@Service
public class SeatHoldService {
    private final HoldProperties properties;

    public SeatHoldService(HoldProperties properties) {
        this.properties = properties;
    }

    public String describeHold(int requestedSeats) {
        if (requestedSeats > properties.maxSeatsPerOrder()) {
            return "REFUSED: " + requestedSeats + " seats requested, limit is "
                    + properties.maxSeatsPerOrder();
        }
        return "HELD: " + requestedSeats + " seats for "
                + properties.ttl().toMinutes() + " minutes";
    }
}
```

```yaml
zenith:
  hold:
    ttl: PT15M
    max-seats-per-order: 4
```

What does `describeHold(5)` return? What does `describeHold(4)` return? Trace where
each number comes from.

*Model answer:* `describeHold(5)` → `REFUSED: 5 seats requested, limit is 4`. The 4
comes from `max-seats-per-order: 4`, bound by name into `maxSeatsPerOrder`, read via
the accessor. `describeHold(4)` → `HELD: 4 seats for 15 minutes` — 4 is not greater
than 4, and 15 comes from `PT15M` bound into the `Duration ttl`, then `toMinutes()`.
Chain for every number: YAML → binder → record component → accessor method.

**Question 2.** In the assembled app, `SeatHoldService` and `HoldExpiryNotifier` both
take `HoldProperties` in their constructors; `main` starts the app and calls them. A
teammate deletes the `@ConfigurationPropertiesScan` line and rebuilds. In Lesson 6's
demo (only `main` asked, after startup), the app logged `Started HoldsApplication`
before crashing. Will this app also start first and crash later — or something else?
What will the error block contain?

*Model answer:* Something else: startup itself fails, and no `Started` line appears.
The consumers demand `HoldProperties` while the context is being built, so the missing
bean surfaces during refresh. Boot's analyzer prints `APPLICATION FAILED TO START`
with: `Parameter 0 of constructor in com.zenith.holds.HoldExpiryNotifier required a
bean of type 'com.zenith.holds.HoldProperties' that could not be found`, and the
Action suggests defining such a bean. (Which consumer is named depends on creation
order — in the real run it was `HoldExpiryNotifier`.) Exit code 1. Same root cause as
Lesson 6, earlier and louder surface.

### Summary

- Assembly: record + `@ConfigurationPropertiesScan` + constructor injection; services
  read typed accessors.
- Output unchanged, plumbing replaced: the proof that binding worked.
- `grep -rn "@Value" src/` empty; the prefix is the only config string left in Java.
- Record components are read as methods: `ttl()`, not `ttl`.
- In an assembled app, missing registration fails at startup — the analyzer names the
  consumer and the missing type.

---

## Lesson 8 — Validation rules on the record: `@Validated`, `@NotNull`, `@Positive`, `@Max` (subtask S3)

### Definition

A validation rule is a statement about a value that must be true, written as an
annotation on a record component. `@NotNull` = must be present. `@Positive` = greater
than zero. `@Max(10)` = at most 10. `@Validated` on the type is the **switch**: it
tells Spring to hand the object to the checker at creation time.

### Why it is needed

Lesson 5 proved the gap: a bare record binds a deleted `ttl` as `null` and a deleted
limit as `0`, both silently. Ticketing stakes: a `null` TTL is a
`NullPointerException` mid-checkout at 2 a.m.; a limit of `0` is an on-sale that sells
nothing while dashboards stay green. Rules turn "present" and "sensible" into startup
requirements — fail fast, in front of you.

### How it works

1. **Words:** rules live in `jakarta.validation.constraints` — Lesson 1's "words" jar,
   finally used.
2. **Placement:** in front of the guarded component: `@NotNull Duration ttl`.
3. **Switch:** `@Validated` (`org.springframework.validation.annotation`) on the type.
   Without it, the words sit unread even with the checker installed (proven below).
4. **Timing:** binder builds the record → Spring immediately passes it to the checker →
   any violation aborts startup, naming property, value, and rule. A half-valid bean
   never circulates.
5. **Checker:** Hibernate Validator (Lesson 1). Absent checker = rules silently
   skipped (Lesson 10).

### The code

```java
package com.zenith.holds;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The whole zenith.hold group as one typed, immutable, validated object.
 * A broken rule stops the application at startup.
 */
@Validated
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(
        @NotNull Duration ttl,
        @Positive @Max(10) int maxSeatsPerOrder) {
}
```

| Rule | Guards against | Ticketing meaning |
| --- | --- | --- |
| `@NotNull` on `ttl` | Key deleted or never written (Lesson 5's silent `null`) | A hold time must exist. |
| `@Positive` on `maxSeatsPerOrder` | `0` and negatives — including the `0` a **missing** primitive becomes | Zero sells nothing; even absence of the key is fatal. |
| `@Max(10)` | Absurd values like `999` | No order hogs more than ten seats. |

No `@NotNull` on the `int`: a primitive can never be `null`; absence becomes `0`,
which is `@Positive`'s job.

### Three verified runs

**Run 1 — healthy (`PT10M`, `8`):** identical three output lines, exit 0. Valid
configuration pays zero cost.

**Run 2 — `max-seats-per-order: 0`:** startup refused, exit 1:

```text
APPLICATION FAILED TO START
***************************

Description:

Binding to target com.zenith.holds.HoldProperties failed:

    Property: zenith.hold.maxSeatsPerOrder
    Value: "0"
    Origin: class path resource [application.yml] from typed-config-scratch-0.0.1-SNAPSHOT.jar - 4:26
    Reason: must be greater than 0

Action:

Update your application's configuration
```

The `Origin` line names the file **and the line and column** (`4:26`). Property,
value, place, reason — a complete arrest report.

**Run 3 — rules kept, `@Validated` removed, value still `0`:** the app started green,
exit 0, `REFUSED: ... limit is 0`. Rules without the switch are decoration.

### Where it is used

Same vocabulary at every boundary: `@Valid` on web request bodies (bad JSON → 400),
rules on service method parameters, rules on entities. Learn once, use everywhere.

### Common mistakes

**Mistake 1 — forgetting `@Validated`.** Rules present, nothing enforced (run 3). The
symptom — invalid values passing silently — is identical to the missing-checker trap
(Lesson 10). One silence, two causes: check the switch on the type and the checker in
the dependency tree.

**Mistake 2 — the wrong import.** IDEs offer `org.jetbrains.annotations.NotNull` or
old `javax.validation` names. Compiles fine; the checker reads only
`jakarta.validation.constraints`. When validation "does not work", read the imports
first.

**Mistake 3 — `@NotNull` on a primitive.** Can never fire (`int` is never `null`) and
gives false comfort. The danger for primitives is absence-becoming-`0`; the guard is a
range rule like `@Positive`.

### Link to the task

Completes subtask S3's snippet exactly. The task's promise — "the application refusing
to start, with a clear message naming the missing or invalid property" — is run 2.
Subtask S6 (deliberate breakage) is Lesson 9; the removed-checker trap is Lesson 10.

### Check yourself

**Question 1.** Given this complete, registered record (checker installed):

```java
@Validated
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(
        @NotNull Duration ttl,
        @Positive @Max(10) int maxSeatsPerOrder) {
}
```

Four `application.yml` variants, one per startup. For each: does the app start, and if
not, which rule fired?

- (a) `ttl: PT10M` and `max-seats-per-order: 8`
- (b) `ttl: PT10M` and `max-seats-per-order: 12`
- (c) `ttl: PT10M` only — the limit key absent
- (d) `max-seats-per-order: 8` only — the ttl key absent

*Model answer:* (a) Starts — both values satisfy all rules. (b) Refuses — `@Max(10)`:
12 exceeds 10; reason reads "must be less than or equal to 10". (c) Refuses — the
missing primitive binds as `0`, and `@Positive` fires: "must be greater than 0". (d)
Refuses — `@NotNull` on `ttl` fires: "must not be null". Note (c): absence of the key
is caught by the *range* rule, because a missing primitive becomes `0`.

**Question 2.** A record carries `@NotNull`, `@Positive`, `@Max(10)` on its
components; the checker is in the dependency tree; `application.yml` says
`max-seats-per-order: 0`. The type-level annotations are only
`@ConfigurationProperties(prefix = "zenith.hold")` — no `@Validated`. The teammate
expects a startup failure. What actually happens, and why?

*Model answer:* The app starts cleanly and runs with the limit `0` (proven by run,
exit 0). Constraint annotations are only words; `@Validated` is the switch that
connects this particular bean to the checker. Installed checker + missing switch =
rules never consulted. Fix: add `@Validated` to the record.

### Summary

- Rules are words from `jakarta.validation.constraints`; the checker is Hibernate
  Validator; `@Validated` is the switch wiring this type to the checker.
- Violation at startup = `APPLICATION FAILED TO START` with Property, Value, Origin
  (file and line:column), Reason.
- Valid config pays zero cost; the rules only speak when broken.
- `@Positive` catches a missing primitive (absence → `0`); `@NotNull` catches a
  missing object type; `@Max` caps the absurd.
- Two causes for silently-ignored rules: missing `@Validated` (this lesson), missing
  checker (Lesson 10).

---

## Lesson 9 — Hands-on: break it on purpose, read the refusal (subtask S6)

### What this lesson does

Deliberately breaks the configuration three ways — a nonsense value, a deleted key, and
F6.4's one-character typo — and reads each refusal. Then repairs it. Trust in the
referee comes from watched catches, and the failure block has a fixed shape worth
learning to read in five seconds: `Property`, `Value`, `Origin`, `Reason`.

### The arming step

`HoldProperties.java` gains the rules (full file in Lesson 8 above; identical). With
healthy values (`ttl: PT10M`, `max-seats-per-order: 8`) the output is unchanged — the
same three lines, exit 0. Valid configuration pays nothing.

### Breakage 1 — the nonsense value (`max-seats-per-order: 0`)

Real output, exit 1:

```text
APPLICATION FAILED TO START
***************************

Description:

Binding to target com.zenith.holds.HoldProperties failed:

    Property: zenith.hold.maxSeatsPerOrder
    Value: "0"
    Origin: class path resource [application.yml] from typed-config-scratch-0.0.1-SNAPSHOT.jar - 4:26
    Reason: must be greater than 0

Action:

Update your application's configuration
```

| Line | How to read it |
| --- | --- |
| `Property` | Which setting is wrong, with its full key path. |
| `Value: "0"` | What the binder actually found. |
| `Origin: ... application.yml ... 4:26` | The file, line 4, column 26. Open exactly there. |
| `Reason: must be greater than 0` | The rule that fired — `@Positive`, in its own words. |

Lesson 3's same `0` produced a green app refusing every customer. Same mistake,
opposite outcome.

### Breakage 2 — the deleted key (no `ttl` line)

Real output, exit 1:

```text
    Property: zenith.hold.ttl
    Value: "null"
    Reason: must not be null
```

Two details: `Value: "null"` is what the bare record silently swallowed in Lesson 5.
And there is **no `Origin` line** — a missing key exists in no file, so there is
nothing to point at. Origin present = bad value, here. Origin absent = no value,
anywhere.

### Breakage 3 — the rematch with F6.4's silent typo (`tt1: PT10M`)

Letter-for-letter the F6.4 experiment. Then: green app, value silently gone. Now, real
output, exit 1 — the exact block from breakage 2, naming `zenith.hold.ttl`.

The message names the **expected** property, not the stray key you typed. The binder
ignores unknown keys like `tt1` (they might belong to someone else); validation demands
the keys it expects. The typo is caught from its other end: not "strange key written"
but "required key missing".

| | F6.4 — `Environment.getProperty` | F6.6 — validated record |
| --- | --- | --- |
| App start | Green, exit 0 | **Refused, exit 1** |
| The value | Silently `null` | Named: `zenith.hold.ttl`, "must not be null" |
| Discovery | Hours later, far from the cause | Immediately, at startup |

### Common mistakes

**Mistake 1 — hunting for the typo'd key in the message.** The block names what is
missing (`ttl`), not what is stray (`tt1`). On "must not be null" for a key you are
sure you wrote: diff the file against the record's component names.

**Mistake 2 — misreading a missing `Origin`.** Not an incomplete block — information.
No Origin = key exists in no source.

**Mistake 3 — forgetting to rebuild between edits.** The jar carries its own
`application.yml`. Edit → rebuild → run. If a breakage does not break, rebuild first.

### Link to the task

Subtask S6, bullets one and two, plus the direct comparison with F6.4's silent typo.
Subtask S3 fully complete. The third breakage — removing the checker — is Lesson 10.

### Do it yourself

Start from the Lesson 7 state (bare record, healthy values).

1. Arm: replace `HoldProperties.java` with the validated version (Lesson 8).
2. `mvn clean package && java -jar target/typed-config-scratch-0.0.1-SNAPSHOT.jar; echo $?`
   → three healthy lines, exit `0`.
3. Breakage 1: `max-seats-per-order: 0`, rebuild, run. Expect the block with
   `Property: zenith.hold.maxSeatsPerOrder`, `Value: "0"`, Origin with line:column,
   `Reason: must be greater than 0`, exit `1`. Read all four lines aloud.
4. Breakage 2: restore `8`; delete the `ttl` line. Rebuild, run. Expect
   `Property: zenith.hold.ttl`, `Value: "null"`, `Reason: must not be null`, no Origin,
   exit `1`.
5. Breakage 3: write `tt1: PT10M` (digit one). Rebuild, run. Expect the same block as
   step 4. Say aloud why it names `ttl`, not `tt1`.
6. Repair: `ttl: PT10M`. Rebuild, run → three lines, exit `0`.

**Done when:** all three refusals seen, breakage 3's naming explained in your own
words, app green again.

### Check yourself

**Question 1.** A colleague's app refuses to start with exactly this block:

```text
Description:

Binding to target com.zenith.holds.HoldProperties failed:

    Property: zenith.hold.maxSeatsPerOrder
    Value: "15"
    Origin: class path resource [application.yml] from app.jar - 4:26
    Reason: must be less than or equal to 10
```

The record is `@Validated` with `@NotNull Duration ttl` and
`@Positive @Max(10) int maxSeatsPerOrder`. Which file and line do they open, which rule
fired, and what range of values would let the app start?

*Model answer:* Open `application.yml`, line 4 (column 26) — the Origin says so.
`@Max(10)` fired: 15 exceeds 10, hence "must be less than or equal to 10". Legal
values: 1 through 10 — at most 10 for `@Max`, and at least 1 because `@Positive`
excludes 0 and negatives.

**Question 2.** The same validated record is in place. The complete `application.yml`:

```yaml
zenith:
  hold:
    tt1: PT10M
    max-seats-per-order: 8
```

Note the digit one. Does the app start? Which property does the block name, and why is
there no `Origin` line for it, even though the typo'd line plainly sits in the file?

*Model answer:* It refuses to start, exit 1. The block names `zenith.hold.ttl` with
`Value: "null"` and `Reason: must not be null`. No Origin appears because Origin points
at where a value *lives* — and the expected key `ttl` lives nowhere; it is absent from
every source. The typo'd `tt1` is an unknown key, silently ignored by the binder, so it
is neither bound nor mentioned. (In F6.4, this same file started the app in silence;
the whole F6.6 difference is this refusal.)

### Summary

- The block reads: Property = which setting; Value = what arrived; Origin = file and
  line:column (absent when the key exists nowhere); Reason = the rule, in words.
- `0` → `@Positive` refusal with Origin; deleted `ttl` → `@NotNull` refusal without
  Origin; typo'd `tt1` → the same `@NotNull` refusal, because validation checks
  expected keys, not stray ones.
- F6.4's silent typo is now a startup stop naming the property — the task's entire
  point, demonstrated.
- The jar carries its own YAML: rebuild between edits.

---

## Lesson 10 — The nastiest trap: rules written, nothing enforced (subtask S6)

### What this lesson does

Manufactures the situation the task warns about — validation annotations in the code
while no validation runs — and tests the task's own claim against Spring Boot 3.5.16.
Finding: the literal claim no longer reproduces; the silence survives through different
doors. All proven by runs.

### Experiment A — the task's literal script: remove the starter

`spring-boot-starter-validation` deleted from `pom.xml`, `max-seats-per-order: 0` set.
Real output:

```text
[ERROR] COMPILATION ERROR :
[ERROR] ... HoldProperties.java:[5,38] package jakarta.validation.constraints does not exist
```

The starter carries both halves (Lesson 1): the words and the checker. Our record
imports the words, so removing the starter removes the words → compile error. Loud. In
this app, silence by this route is impossible.

### Experiment B — words present, checker absent

Starter still removed; `jakarta.validation-api` added alone (the shape real projects
get when another library supplies the words). Build: `BUILD SUCCESS`. Run, exit 1:

```text
APPLICATION FAILED TO START
***************************

Description:

The Bean Validation API is on the classpath but no implementation could be found

Action:

Add an implementation, such as Hibernate Validator, to the classpath
```

Still not silent: Boot's failure analyzer recognises this exact trap and prescribes the
fix by name. This door has been welded shut on Boot 3.5.

### Experiment C — the trap that still works: the wrong dictionary

Before 2020 the annotations lived in `javax.validation.constraints`; old tutorials and
the old jar are still everywhere. Setup: full starter restored (checker present!),
legacy `javax.validation:validation-api:2.0.1.Final` added, and only the record's three
import lines changed from `jakarta...` to `javax...`. Value still `0`. Build: success.
Run, exit 0:

```text
REFUSED: 3 seats requested, limit is 0
REFUSED: 12 seats requested, limit is 0
Reminder scheduled 9 minutes after hold, 1 minute before expiry
```

**Green app. Broken rule. No warning anywhere.** The checker reads only the `jakarta`
dictionary; a `javax` `@NotNull` is a stranger wearing the right name. One careless IDE
auto-import or one old tutorial produces exactly this. The fourth door, proven in
Lesson 8: delete `@Validated` — same silence with perfect imports.

### The repair, proven

Imports back to `jakarta`, legacy jar removed, value still `0`: the refusal returned —
`Property: zenith.hold.maxSeatsPerOrder`, `Reason: must be greater than 0`, exit 1.
Then `8`: three healthy lines, exit 0.

### The three-point checklist

1. **Imports:** every rule from `jakarta.validation.constraints`. Anything else is
   silently inert (Experiment C).
2. **Switch:** `@Validated` on the type. Rules without it are decoration (Lesson 8).
3. **Checker:** `mvn dependency:tree` must show `hibernate-validator`. API without it →
   Boot refuses loudly (Experiment B); no API at all → rule-bearing code does not
   compile (Experiment A).

### Common mistakes

**Mistake 1 — trusting a green startup.** Green means "no failure detected", not
"rules enforced". After dependency cleanups, re-run a known-bad config and expect the
refusal.

**Mistake 2 — assuming one cause.** Wrong dictionary and missing switch produce the
identical symptom.

**Mistake 3 — "it compiles, so dependencies are fine."** Compilation needs only the
words; enforcement needs words + right dictionary + switch + checker. Experiment C
compiled perfectly and enforced nothing.

### Link to the task

Subtask S6's third bullet, honestly executed: the literal removal is loud on Boot 3.5;
the surviving silent trap is the wrong-dictionary variant — manufactured, seen, fixed.
S1's "load-bearing" claim proven from every side. The task's "done when" holds: invalid
or missing values under `zenith.hold` stop the application, naming the property.

### Do it yourself

Start from the Lesson 9 end state (validated record, healthy values). Internet needed
for step 4.

1. Tripwire: set `max-seats-per-order: 0`; rebuild, run → refusal block, exit `1`.
2. Task's script: delete the `spring-boot-starter-validation` block; `mvn clean
   package` → `COMPILATION ERROR`, `package jakarta.validation.constraints does not
   exist`.
3. Words without checker: in its place add

   ```xml
   <dependency>
       <groupId>jakarta.validation</groupId>
       <artifactId>jakarta.validation-api</artifactId>
   </dependency>
   ```

   Rebuild (succeeds), run → `APPLICATION FAILED TO START`, "The Bean Validation API
   is on the classpath but no implementation could be found", exit `1`.
4. The silent trap: restore the full starter block, add

   ```xml
   <dependency>
       <groupId>javax.validation</groupId>
       <artifactId>validation-api</artifactId>
       <version>2.0.1.Final</version>
   </dependency>
   ```

   and switch the record's three imports to `javax.validation.constraints.*`. Rebuild,
   run, `echo $?` → app starts, `limit is 0`, exit `0`. Silence, manufactured.
5. Repair: imports back to `jakarta`, delete the `javax` dependency. Rebuild, run →
   refusal returns, exit `1`.
6. Heal: `max-seats-per-order: 8`; rebuild, run → three healthy lines, exit `0`.

**Done when:** the same invalid `0` was refused, then silently accepted, then refused
again — controlled only by imports and dependencies — and you can recite the checklist.

### Check yourself

**Question 1.** A teammate's app starts happily despite `max-seats-per-order: 0`.
Their record (shown complete, imports not shown):

```java
@Validated
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(
        @NotNull Duration ttl,
        @Positive @Max(10) int maxSeatsPerOrder) {
}
```

The project builds cleanly; `mvn dependency:tree` shows `hibernate-validator`. Name the
two remaining suspects from the checklist, and the single file you open first — given
`@Validated` is plainly visible above.

*Model answer:* The remaining suspects are the import package (`jakarta` vs `javax`)
and the `@Validated` switch. The switch is visibly present in the snippet, so the top
suspect is the imports — which the snippet does not show. Open `HoldProperties.java`
and read its import lines. If they say `javax.validation.constraints`, that is the
silent cause (Experiment C): the checker reads only `jakarta` rules.

**Question 2.** Explain in two or three sentences why "the build succeeded" says
nothing about whether validation will run — what does compilation need versus what
does enforcement need? Name one proven example from this lesson.

*Model answer:* Compilation only needs the annotation classes to exist on the
classpath — the words. Enforcement needs more: the words from the right dictionary
(`jakarta`), the `@Validated` switch on the type, and the checker
(`hibernate-validator`) at runtime. Proven: Experiment C — `javax` imports with the
real checker and `@Validated` present — built cleanly and ran green while
`max-seats-per-order: 0` sat in the file.

### Summary

- On Boot 3.5, removing the starter is loud: compile error (words gone) or the
  analyzer block "Bean Validation API ... no implementation" (words without checker).
- The surviving silent trap: right rules, wrong dictionary (`javax` imports) — checker
  and switch present, nothing enforced, exit 0.
- Second silent door: missing `@Validated` (Lesson 8).
- Checklist: imports → switch → dependency tree.
- Green startup is not proof of enforcement; a deliberate bad value is.

---

## Lesson 11 — Configuration metadata: autocomplete for your own keys (subtask S7)

### Definition

`spring-boot-configuration-processor` is a compile-time helper. During the build it
reads `@ConfigurationProperties` types and writes
`META-INF/spring-configuration-metadata.json` — a machine-readable description of every
property: full key, type, documentation. IDEs read it to autocomplete keys in
`application.yml`, show descriptions, and flag unknown keys while typing.

### Why it is needed

The journey of one typo: F6.4 — discovered at runtime, or never. Lesson 9 — caught at
startup by validation. This lesson — flagged **while typing**, in the editor. Each
layer fails faster. The startup referee stays the enforcement; the editor warning is
advice that kills most typos before they are born.

### How it works

1. One dependency, marked `<optional>true</optional>` (build-time tool; consumers of
   the jar never need it).
2. At compilation the processor writes the JSON into `target/classes` (and the jar).
3. Record javadoc `@param` lines become property descriptions.
4. The IDE indexes the file: completion, docs, "cannot resolve" warnings.

### The code and the real generated file

`pom.xml`:

```xml
<!-- Compile-time only: generates META-INF/spring-configuration-metadata.json
     so the IDE can autocomplete zenith.* keys and flag typos. Subtask S7. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

Record javadoc:

```java
/**
 * The whole zenith.hold group as one typed, immutable, validated object.
 * A broken rule stops the application at startup.
 *
 * @param ttl              how long a seat hold lasts before it expires
 * @param maxSeatsPerOrder most seats one order may hold at once
 */
```

Generated `target/classes/META-INF/spring-configuration-metadata.json` (real output):

```json
{
  "groups": [
    {
      "name": "zenith.hold",
      "type": "com.zenith.holds.HoldProperties",
      "sourceType": "com.zenith.holds.HoldProperties"
    }
  ],
  "properties": [
    {
      "name": "zenith.hold.max-seats-per-order",
      "type": "java.lang.Integer",
      "description": "most seats one order may hold at once",
      "sourceType": "com.zenith.holds.HoldProperties",
      "defaultValue": 0
    },
    {
      "name": "zenith.hold.ttl",
      "type": "java.time.Duration",
      "description": "how long a seat hold lasts before it expires",
      "sourceType": "com.zenith.holds.HoldProperties"
    }
  ],
  "hints": [],
  "ignored": {
    "properties": []
  }
}
```

Reading it: the processor writes the **canonical kebab-case key** (the IDE will
complete the correct file-side spelling); `description` is the `@param` text;
`defaultValue: 0` records what the absent primitive would become (our `@Positive`
makes that absence fatal anyway). Zero runtime effect — the app runs identically.

### Where it is used

Every official Boot starter ships this file — that is why `spring.datasource.url`
autocompletes. Your keys are now first-class: same completion, docs, and typo warnings
as Boot's own.

### Common mistakes

**Mistake 1 — typing immediately after adding the dependency.** The file is born at
build time. Rebuild first.

**Mistake 2 — expecting runtime changes.** Metadata is developer comfort; the referee
from Lessons 8–9 remains the enforcement.

**Mistake 3 — judging by the IDE edition.** YAML completion from metadata is an
IntelliJ Ultimate feature; Community may show nothing while everything is correct. The
ground truth is the JSON file in `target/classes/META-INF/`. (VS Code with Spring Boot
Tools also reads it.)

### Link to the task

Subtask S7 word for word. Small, and it removes a whole class of typo — the class F6.4
made you fear.

### Do it yourself

Start from the Lesson 10 end state.

1. Add the processor dependency block to `pom.xml` after the validation starter.
2. Add the two `@param` lines to the `HoldProperties` javadoc.
3. `mvn clean package` then
   `cat target/classes/META-INF/spring-configuration-metadata.json` — expect the JSON
   above.
4. IntelliJ: in `application.yml` under `hold:` type `max` — expect completion of
   `max-seats-per-order` with your description. (Community edition: step 3 is the
   proof.)
5. Type `tt1: PT5M` — expect a "Cannot resolve configuration property
   'zenith.hold.tt1'" warning. Delete the line.
6. Run once — the usual three lines, exit `0`.

**Done when:** the JSON exists with both properties and descriptions, and the editor
flagged a typo'd key (or the JSON check passed on Community).

### Check yourself

**Question 1.** Three incidents. For each, name the layer that catches it and the
moment:

- (a) A developer types `zenith.hold.tt1: PT5M` into `application.yml` in the IDE
  (processor added, metadata built).
- (b) A deploy ships `max-seats-per-order: 0` (record `@Validated`, `@Positive`,
  checker installed).
- (c) A cleanup leaves the validation words on the classpath but removes the checker
  (record `@Validated`, Boot 3.5).

*Model answer:* (a) The editor, at edit time: metadata-driven warning "Cannot resolve
configuration property 'zenith.hold.tt1'" — advice, not enforcement. (b) Bean
validation, at startup: the refusal block names the property, value, origin
(file + line:column), reason "must be greater than 0"; exit 1. (c) Boot's failure
analyzer, at startup: "The Bean Validation API is on the classpath but no
implementation could be found" — the app refuses to start and prescribes Hibernate
Validator.

**Question 2.** A teammate adds the processor, immediately types `zenith.` in
`application.yml`, and gets no completion. Which step was skipped, and which exact
file verifies the metadata exists?

*Model answer:* The rebuild. The metadata file is generated during compilation, not by
the IDE on the fly. After `mvn clean package` (or an IDE build with annotation
processing), check `target/classes/META-INF/spring-configuration-metadata.json` — if
the properties are listed there, the metadata side is done; remaining gaps are IDE
indexing or edition limits.

### Summary

- One optional compile-time dependency → a JSON description of your properties inside
  the artifact.
- `@param` javadoc becomes IDE documentation; keys complete in canonical kebab-case.
- Three layers now guard configuration: editor (typo warnings, edit time), validation
  (refusal, startup), and the dependency tree discipline (Lesson 10's checklist).
- Zero runtime effect; the ground truth is the generated file, not the IDE's mood.

---

## Lesson 12 — The rule going forward, the two Ask-AI answers, and the checkpoint (subtasks S8, S9, S10, CP)

### Part 1 — The rule to record (S8)

> **Use `@ConfigurationProperties` on a `@Validated` record for any group of two or
> more related values, anything used in more than one class, and anything that must be
> valid. Use `@Value` only for a genuine one-off: a single value, used in exactly one
> place, with no rule beyond "it exists".**

Why each clause earned its place:

- *Two or more related values* — one object beats loose values: one injection, one
  `toString`, one place to look (Lesson 7).
- *Used in more than one class* — `@Value` repeats the string key in every consumer;
  the record is one shared bean (Lessons 3 and 7).
- *Must be valid* — only the record route has the startup referee with the
  Property / Value / Origin / Reason block (Lessons 8–9).
- *`@Value` for a true one-off* — it is honest about absence (a missing key without a
  default stops startup, Lesson 2), and a one-line flag does not deserve a record. The
  moment a second related value appears, or a rule beyond existence, migrate.

### Part 2 — Ask-AI answer one: nested properties and a `List` field (S9)

*Question asked: "Show me a `@ConfigurationProperties` record with nested properties
and a `List` field, and explain how Spring binds a YAML list to it."*

Built and run in the scratch-app. The record:

```java
package com.zenith.holds;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "zenith.payments")
record PaymentProperties(
        @NotNull String providerUrl,
        @NotNull @Valid Retry retry,
        @NotEmpty List<String> supportedCurrencies) {

    record Retry(@Positive int maxAttempts, @NotNull Duration backoff) {
    }
}
```

The YAML it binds:

```yaml
zenith:
  payments:
    provider-url: https://sandbox.payments.example.com
    retry:
      max-attempts: 3
      backoff: PT2S
    supported-currencies:
      - GBP
      - EUR
      - USD
```

Real output of the bound bean:

```text
payments: PaymentProperties[providerUrl=https://sandbox.payments.example.com, retry=Retry[maxAttempts=3, backoff=PT2S], supportedCurrencies=[GBP, EUR, USD]]
```

**How the nesting binds.** A component whose type is another record (`Retry retry`)
maps to a deeper YAML section: `zenith.payments.retry.*`. The binder recurses — it
builds the inner record through *its* constructor with the same matching and
converting rules, then passes the finished `Retry` into the outer constructor. Any
depth works; names compose with dots.

**How the list binds.** YAML dash items become indexed keys —
`supported-currencies[0]`, `[1]`, `[2]` (the same shape F6.4 showed). The binder
collects all indexes under the key, converts each element to the list's element type
(`String` here; a `List<Duration>` would convert each entry), and assembles a
`java.util.List` in order. Because elements are individually addressable keys, one
element can be overridden by a higher source, for example the command-line argument
`--zenith.payments.supported-currencies[0]=CHF`.

**Verified gotcha — nested rules need `@Valid`.** The first version had no `@Valid` on
the `retry` component. With `max-attempts: 0` the app started green — the output
showed `Retry[maxAttempts=0, ...]`, exit code 0. The `@Positive` inside the nested
record was silently skipped: `@Validated` checks the top-level object and only
cascades *into* a component marked `@Valid`. With `@Valid` added, the same `0` refused
startup (exit code 1), and the block names the full nested path:

```text
    Property: zenith.payments.retry.maxAttempts
    Value: "0"
    Reason: must be greater than 0
```

The list rule enforces too. With `supported-currencies: []` (real output):

```text
    Property: zenith.payments.supportedCurrencies
    Value: "[]"
    Reason: must not be empty
```

The Lesson 10 silence family, completed: wrong dictionary (`javax` imports), missing
switch (`@Validated`), and missing `@Valid` on a nested component.

### Part 3 — Ask-AI answer two: why validation failures stop startup, and where (S10)

*Question asked: "Explain why `@ConfigurationProperties` validation failures stop
application startup, and where in the Spring lifecycle that check happens."*

1. **When beans are born.** Starting a Spring application means refreshing the
   application context — the phase where the container builds every singleton bean.
   Every refusal in this task printed *before* any `Started HoldsApplication` line,
   because it happened inside this phase.
2. **Where the check sits.** `@ConfigurationPropertiesScan` registered
   `HoldProperties`. During refresh, the binder builds it: match keys, convert text,
   call the constructor. Because of `@Validated`, Spring hands the freshly built
   object to the checker immediately — still inside the bean's creation, before the
   bean is registered for anyone to use.
3. **What a violation does.** The checker reports the broken rule; the bean's creation
   throws; the context refresh aborts; no further beans are built. Boot's failure
   analyzer catches the exception and formats the human block — Property, Value,
   Origin, Reason. `SpringApplication.run` rethrows, and the JVM exits with code 1.
4. **Why this must stop the whole app.** Consumers like `SeatHoldService` are built in
   the same refresh and can only receive a bean that *finished* creation. A failed
   creation means there is nothing to inject, so the refresh cannot complete even in
   principle. The invariant this buys: **the properties bean either exists valid, or
   does not exist at all.** No code path in the application can ever observe a
   half-valid configuration — that is what "fail fast" means here: the earliest
   possible moment is the object's birth, and that is where the check stands.

### Part 4 — Checkpoint, with full answers (CP)

**Question 1. What does `@ConfigurationProperties` give you that `@Value` does not?**

*Answer:* One typed, immutable object for the whole group instead of loose values; one
injection point shared by every consumer instead of a string key repeated per class;
relaxed binding (`max-seats-per-order` in the file matches `maxSeatsPerOrder` in Java)
plus type conversion into `Duration`, `int`, lists, nested records; startup validation
with the Property / Value / Origin / Reason refusal block; and IDE metadata with
autocomplete via the configuration processor. `@Value` gives exact single-key lookup
only — no grouping, no sense-checking of values, no metadata.

**Question 2. Which YAML key binds to a field called `maxSeatsPerOrder`?**

*Answer:* `max-seats-per-order` — the canonical kebab-case form, under the prefix:
`zenith.hold.max-seats-per-order`. Relaxed binding also accepts `maxSeatsPerOrder` in
the file, and `ZENITH_HOLD_MAXSEATSPERORDER` as an environment variable, but
kebab-case is the file-side spelling the metadata and IDE complete.

**Question 3. Why is failing at startup better than failing on first use? Give a
ticketing example.**

*Answer:* A startup failure is seen by the person deploying, at deploy time, in the
logs, with the property named — cheap to notice, cheap to fix, and no customer is
affected. A first-use failure is seen by a customer, at the worst possible time, as a
symptom far from the cause. Ticketing example: `zenith.hold.ttl` is missing. With
validation, the deploy aborts immediately with
`Property: zenith.hold.ttl … must not be null` and the on-call fixes one line. Without
it, the app starts green and sells tickets fine — until the first seat hold during the
Saturday on-sale peak, when holds get a null expiry: either every hold crashes with a
`NullPointerException` or holds never expire and seats lock up exactly when thousands
are buying.

**Question 4. When is `@Value` still a reasonable choice?**

*Answer:* For a genuine one-off: a single value, consumed in exactly one class, with
no rule beyond "it exists" — a lone feature flag, a banner label. Without a default it
still fails loudly on absence, so it is not unsafe for that narrow case. The moment a
second related value appears, or the value needs a sense rule like "positive" or "at
most 10", move it into a `@ConfigurationProperties` record.

### Summary

- The rule: record + `@Validated` + `@ConfigurationProperties` for groups, shared
  values, and anything with rules; `@Value` only for a one-off with no rule beyond
  existence.
- Nested records bind from deeper YAML sections; lists bind from dash items as indexed
  keys, converted per element, in order.
- Nested validation needs `@Valid` on the component, or the inner rules are silently
  skipped — the last member of the silence family.
- The check lives inside the properties bean's creation during context refresh:
  bind, then validate, then (and only then) the bean exists. Fail there and the
  refresh aborts, the analyzer prints the block, the JVM exits 1.

---

## Task complete — final audit against "done when"

All checks run on the finished scratch-app, real output:

**Check 1 — no `@Value` remains in the service classes.**

```bash
grep -rn "@Value" src/
# (no matches)
```

**Check 2 — an invalid value under `zenith.hold` stops the application, naming the
property.** With `max-seats-per-order: 0`:

```text
    Property: zenith.hold.maxSeatsPerOrder
    Value: "0"
    Origin: class path resource [application.yml] from typed-config-scratch-0.0.1-SNAPSHOT.jar - 4:26
    Reason: must be greater than 0
```

Exit code `1`. (Lesson 9 also proved the missing-key case: deleting `ttl` refuses with
`Property: zenith.hold.ttl … must not be null`, with no Origin line because the key
exists nowhere.)

**Check 3 — the healthy configuration runs green.**

```text
HELD: 3 seats for 10 minutes
REFUSED: 12 seats requested, limit is 8
Reminder scheduled 9 minutes after hold, 1 minute before expiry
```

Exit code `0`.

**Subtasks:** S1 (Lesson 1) · S2 (Lessons 2–3) · S3 (Lessons 4–5, 7–8) · S4 (Lessons
6–7) · S5 (Lessons 4, 7) · S6 (Lessons 9–10) · S7 (Lesson 11) · S8, S9, S10, CP
(Lesson 12). All done.

**One honest note:** the task predicted that removing the validation dependency makes
validation "pass silently". On Spring Boot 3.5.16 that door is welded shut (Lesson 10:
compile error, or the Bean Validation analyzer refusal). The silent failure is real
but lives behind other doors: `javax` imports, a missing `@Validated`, or a missing
`@Valid` on a nested component.

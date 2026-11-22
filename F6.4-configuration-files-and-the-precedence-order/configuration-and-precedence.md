# F6.4 — Configuration files and the precedence order

Notes for Task F6.4. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state:** Java 21 (`java -version` → `openjdk version "21...`) and
Maven 3.9.x — one copy sits unpacked at
`F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/tools/apache-maven-3.9.9`. No database
is needed for this task. The scratch project lives in this task's own folder at
`scratch-app/` (package `com.zenith.holds`); Lesson 1 describes every file in it.

Two words used everywhere below:

- **Property** — one named setting. Always a key and a value. Example: key `server.port`, value `8080`.
- **Property source** — one place properties come from. A file is one source. The command line is another.

## The task's subtasks

| Id | Subtask |
|----|---------|
| S1 | Replace `application.properties` with `application.yml`. Give it real structure: nesting, a list, a duration, a placeholder with a default. |
| S2 | Read the file back and name each piece: full key, duration, list, placeholder. |
| S3 | Demonstrate precedence: set `zenith.hold.ttl` in the file, then override it with an environment variable, then with a command-line argument. Record all three results. |
| S4 | Write the five-level precedence order in `NOTES.md`, highest priority first. |
| S5 | Say why that order is sensible, in your own words. |
| S6 | Relaxed binding: confirm that the environment variable `ZENITH_PAYMENTS_PROVIDERURL` sets `zenith.payments.provider-url`. |
| S7 | Break it on purpose: change a key to `zenith.hold.tt1` (digit one). Watch the app start fine with your value silently gone. |
| S8 | Inject `Environment` into a bean and call `environment.getProperty("zenith.hold.ttl")`. |
| S9 | Ask AI: all property sources in precedence order, with one realistic scenario each. |
| CP | Checkpoint: answer four questions out loud. |

## Roadmap — 10 small lessons

| # | Lesson | Covers | What it gives you |
|---|--------|--------|-------------------|
| 1 | The stage: a tiny app that prints one property | S8 | The `Environment` object — your eyes. Every later lesson needs a trustworthy way to see which value the app really uses. |
| 2 | YAML, part 1 — nesting | S1 part | Dots become indentation. You replace `application.properties` with `application.yml` and learn the two-space rule. |
| 3 | YAML, part 2 — a list | S1 part | The `-` lines, and how they become a `List<String>` in Java. |
| 4 | The duration: `PT10M` | S1, S2 part | What ISO-8601 durations mean, so `PT10M`, `PT2M`, `PT30S` stop looking like noise. |
| 5 | The placeholder with a default: `${...}` | S1, S2 done | The full target `application.yml` assembled, and every piece of it named out loud. |
| 6 | Property sources and the precedence order | S4, S5 | The five-level order, written in `NOTES.md`, plus the reasoning that makes it stick. |
| 7 | Relaxed binding | S6 | How `ZENITH_HOLD_TTL` finds `zenith.hold.ttl`. Needed before the experiment, or the experiment looks like magic. |
| 8 | The experiment: three sources, three runs | S3 | You predict each winner, then run, then record. The heart of the task. |
| 9 | Break it: the silent typo | S7 | The app starts perfectly and your value is gone. You feel why this is dangerous. |
| 10 | Close-out: every source with a scenario, checkpoint | S9, CP | The full source list with realistic uses, the four checkpoint answers, final notes audit. |

---

## Lesson 1 — The stage: a tiny app that prints one property (subtask S8)

### Definition

The **`Environment`** is a built-in Spring object. It holds every property from every
property source, already merged into one place. You can inject it into your class and
ask it: "what is the value of this key?"

### Why it is needed

This whole task is about one question: when a property is set in several places, which
value wins? You cannot answer that by reading files. The files do not know about each
other. Only the running app knows the final answer.

So before anything else, we need a readout. The readout is a tiny app that starts,
prints one property's final value, and exits. Without it, every lesson after this one
would be guesswork.

### How it works

1. At startup, Spring visits every property source it knows: configuration files,
   operating-system environment variables, command-line arguments, and some others.
2. It merges them all into one lookup object: the `Environment`.
3. Spring keeps the `Environment` in its application context. The application context is
   the box of objects Spring builds and manages. An object in that box is called a bean.
4. Because the `Environment` is a bean, any of your classes can receive it through its
   constructor. Constructor injection means: the dependency arrives as a constructor
   parameter, and Spring fills it in at startup.
5. You call `environment.getProperty("some.key")`. You get the winning value back as a
   `String`. If no source set that key, you get `null`. Not an error — `null`.

### The whole app

Four files in `scratch-app/`.

`pom.xml` — Boot 3.5.16, core starter only. One new thing against earlier scratch apps:
the `spring-boot-maven-plugin`. It makes `mvn package` produce a jar you can run with
`java -jar`. This task runs that jar many times with different settings.

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
    <artifactId>config-scratch</artifactId>
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
    </dependencies>

    <build>
        <plugins>
            <!-- Makes `mvn package` produce a jar you can run with `java -jar`.
                 This task runs the jar many times with different settings. -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/resources/application.properties` — one property. The key is
`zenith.hold.ttl`. The value is the text `PT10M`. (What `PT10M` means is Lesson 4. For
now it is just a value.)

```properties
zenith.hold.ttl=PT10M
```

`src/main/java/com/zenith/holds/ConfigReporter.java` — the bean that reads
configuration. It asks for the `Environment` in its constructor. Spring hands it in at
startup.

```java
package com.zenith.holds;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * One job: read configuration values and turn them into printable lines.
 * The Environment arrives by constructor injection, like any other dependency.
 */
@Component
public class ConfigReporter {

    private final Environment environment;

    public ConfigReporter(Environment environment) {
        this.environment = environment;
    }

    public String ttlLine() {
        return "zenith.hold.ttl = " + environment.getProperty("zenith.hold.ttl");
    }
}
```

`src/main/java/com/zenith/holds/ConfigApplication.java` — the main class. It starts
Spring, fetches the `ConfigReporter` bean from the context, and prints its line.

```java
package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class ConfigApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(ConfigApplication.class, args);
        ConfigReporter reporter = context.getBean(ConfigReporter.class);
        System.out.println(reporter.ttlLine());
    }
}
```

One small detail to notice now, because it matters in Lesson 8:
`SpringApplication.run(ConfigApplication.class, args)` passes `args` in. `args` holds
the command-line arguments. That is the only reason command-line properties can reach
Spring at all.

### Walkthrough — real run

```bash
mvn clean package
java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
```

Real output (startup log trimmed to the last line):

```text
2026-10-31T00:19:58  INFO ... : Started ConfigApplication in 0.371 seconds
zenith.hold.ttl = PT10M
```

Step by step:

1. `java -jar` starts the jar. Java calls `main`.
2. `SpringApplication.run(...)` builds the application context. While doing that, Spring
   also builds the `Environment`. It finds `application.properties` inside the jar and
   loads `zenith.hold.ttl=PT10M` into the `Environment`.
3. Spring sees `ConfigReporter` is marked `@Component`, so it must become a bean. Its
   constructor asks for an `Environment`. Spring hands over the `Environment` it just
   built.
4. `main` fetches the `ConfigReporter` bean from the context with `context.getBean(...)`.
5. `main` calls `reporter.ttlLine()`. Inside,
   `environment.getProperty("zenith.hold.ttl")` looks up the key. Exactly one source set
   it: the file. So the answer is `PT10M`.
6. The line prints. The app exits.

### Where it is used

- Debugging in real projects: "the app is using the wrong database URL — what value does
  it actually see?" One `getProperty` call answers that.
- Spring itself reads the `Environment` everywhere internally. Every nicer configuration
  feature sits on top of it.
- In real application code you will usually use a nicer, typed mechanism (a later task
  covers it). `Environment` is the raw floor underneath. You reach for it when you need
  the truth.

### Common mistakes

**Mistake 1 — misspelling the file name.** Spring looks for exactly
`application.properties`. Name it `aplication.properties` and nothing complains. Tested
for real: the app started cleanly and printed:

```text
zenith.hold.ttl = null
```

No error. No warning. Just `null`.

**Mistake 2 — rebuilding without `clean`.** `mvn package` without `clean` keeps old
build output in the `target/` folder. A renamed or deleted resource file can survive in
there, and the jar keeps the old value. This happened for real while preparing this
lesson. `mvn clean package` removes `target/` first. In this task we rename and change
files constantly, so always build with `clean`.

**Mistake 3 — expecting an error for a missing key.** `getProperty` never throws for an
unknown key. It returns `null`. If your code prints `null`, the key you asked for and
the key that exists are different — check both spellings.

### Link to the task

This lesson is subtask S8 word for word: "Inject `Environment` into a bean and call
`environment.getProperty("zenith.hold.ttl")`." The task lists it late, as a debugging
tool. We did it first instead, because subtasks S3 (the three-run experiment), S6
(relaxed binding), and S7 (the silent typo) all need this printout to show their
results.

### Do it yourself

**Prerequisites.** Java 21 and Maven. Check both:

```bash
java -version        # expect: openjdk version "21..."
mvn -version         # expect: Apache Maven 3.9.x
```

If `mvn` is not found, use the unpacked copy from an earlier task:
`<repo>/F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/tools/apache-maven-3.9.9/bin/mvn`.

**Steps.**

1. Create the folder `F6.4-configuration-files-and-the-precedence-order/scratch-app/`.
2. Create the four files exactly as shown in "The whole app" above:
   - `pom.xml`
   - `src/main/resources/application.properties`
   - `src/main/java/com/zenith/holds/ConfigReporter.java`
   - `src/main/java/com/zenith/holds/ConfigApplication.java`
3. Build and run from inside `scratch-app/`:

   ```bash
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: startup log, then the last line

   ```text
   zenith.hold.ttl = PT10M
   ```

4. Failure drill — see the silence once, on purpose:

   ```bash
   mv src/main/resources/application.properties src/main/resources/aplication.properties
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: the app starts fine, and the last line is

   ```text
   zenith.hold.ttl = null
   ```

5. Repair it:

   ```bash
   mv src/main/resources/aplication.properties src/main/resources/application.properties
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected last line: `zenith.hold.ttl = PT10M` again.

**Done when:** you saw both `PT10M` and `null`, and you can say in one sentence why the
misnamed run printed `null` without any error.

### Understanding check — questions and answers

**Question 1.** An app has this `application.properties` file:

```properties
zenith.hold.ttl=PT10M
```

And this bean:

```java
@Component
public class ConfigReporter {
    private final Environment environment;

    public ConfigReporter(Environment environment) {
        this.environment = environment;
    }

    public String line() {
        return "value = " + environment.getProperty("zenith.hold.limit");
    }
}
```

`main` prints the result of `line()`. What exactly does it print, and why?

**Answer.** It prints `value = null`. The file sets the key `zenith.hold.ttl`. The code
asks for a different key, `zenith.hold.limit`. No source set that key, so
`getProperty` returns `null`. There is no error and no warning. String concatenation
turns the `null` into the text "null".

**Question 2.** The `Environment` is described as "merged". Merged from what? Name two
different places a value could come from.

**Answer.** Merged from all property sources. Each source is one place properties come
from. Examples (any two): the `application.properties` or `application.yml` file inside
the jar, operating-system environment variables, command-line arguments, a
profile-specific file such as `application-prod.yml`, or defaults written in code. The
`Environment` folds them all into one lookup, so one `getProperty` call gives the final,
winning value.

### Summary

- A property is a key plus a value. A property source is one place properties come from.
- The `Environment` is a built-in Spring bean. It holds every property from every
  source, already merged.
- Inject it by constructor, like any dependency. Ask with
  `environment.getProperty("key")`.
- A missing key gives `null`, never an error. Silence is the default failure mode of
  configuration — this task returns to that point again and again.
- Always build with `mvn clean package` when resource files change.

---

## Lesson 2 — YAML, part 1: nesting (subtask S1, part one)

### Definition

**YAML** is a file format that shows structure with indentation instead of brackets or
repeated prefixes. `application.yml` is the YAML version of Spring Boot's configuration
file. This lesson covers one idea only: **nesting** — keys that live inside other keys.

### Why it is needed

The old `.properties` format with two properties:

```properties
zenith.hold.ttl=PT10M
zenith.hold.max-seats-per-order=8
```

The prefix `zenith.hold.` is written twice. A real service has thirty or more
properties. Then every line repeats a long prefix, the structure drowns in noise, and
one mistyped prefix creates a wrong key that nobody notices. YAML writes each part of
the path exactly once. The shape of the file becomes the shape of the configuration.

### How it works — four rules

1. A key ends with a colon. If a value follows on the same line, that key is a
   **leaf**: `ttl: PT10M`. The space after the colon is required.
2. If indented lines follow underneath instead, the key is a **parent**: `hold:` with
   children under it.
3. Indent children with spaces. The convention is two spaces per level. Never use tabs —
   YAML rejects them.
4. The full key of a leaf is the path of parents joined with dots. `zenith` → `hold` →
   `ttl` makes `zenith.hold.ttl`.

The format changes nothing else. Spring reads `.properties` and `.yml` into the same
`Environment`, and `getProperty("zenith.hold.ttl")` works identically with both.

### The change

`application.properties` was deleted. In its place,
`src/main/resources/application.yml`:

```yaml
zenith:
  hold:
    ttl: PT10M
    max-seats-per-order: 8
```

`ConfigReporter` gained a second method, and `main` prints both lines:

```java
    public String ttlLine() {
        return "zenith.hold.ttl = " + environment.getProperty("zenith.hold.ttl");
    }

    public String maxSeatsLine() {
        return "zenith.hold.max-seats-per-order = "
                + environment.getProperty("zenith.hold.max-seats-per-order");
    }
```

```java
        System.out.println(reporter.ttlLine());
        System.out.println(reporter.maxSeatsLine());
```

### Walkthrough — real run

Read the YAML file line by line and build each full key:

| Line | Role | Full key |
|------|------|----------|
| `zenith:` | parent, no value | `zenith` |
| `  hold:` | parent inside `zenith` | `zenith.hold` |
| `    ttl: PT10M` | leaf inside `hold` | `zenith.hold.ttl` = `PT10M` |
| `    max-seats-per-order: 8` | leaf inside `hold` | `zenith.hold.max-seats-per-order` = `8` |

Real output of `mvn clean package` + `java -jar target/config-scratch-0.0.1-SNAPSHOT.jar`:

```text
zenith.hold.ttl = PT10M
zenith.hold.max-seats-per-order = 8
```

### Where it is used

Almost every Spring Boot service uses `application.yml`. YAML is bigger than Spring:
Docker Compose files, Kubernetes manifests, and CI pipeline files are all YAML. The same
indentation rules apply in all of them.

### Common mistakes — all run for real

**Mistake 1 — the mis-indent. Silent.** `ttl` and `max-seats-per-order` slipped left,
out of `hold`:

```yaml
zenith:
  hold:
  ttl: PT10M
  max-seats-per-order: 8
```

The app started cleanly and printed:

```text
zenith.hold.ttl = null
zenith.hold.max-seats-per-order = null
```

Indentation decides the parent. These keys now sit directly under `zenith`, so they
became `zenith.ttl` and `zenith.max-seats-per-order` — legal keys that nobody reads. A
mis-indented key is silently a *different* key. If a property seems ignored, check
indentation first.

**Mistake 2 — a tab. Loud.** The two spaces before `hold:` replaced with one tab. The
app refused to start:

```text
ERROR org.springframework.boot.SpringApplication -- Application run failed
found character '\t(TAB)' that cannot start any token. (Do not use \t(TAB) for indentation)
 in 'reader', line 2, column 1:
    	hold:
    ^
```

| Error piece | Plain English |
|---|---|
| `found character '\t(TAB)'` | There is a tab where YAML only allows spaces. |
| `Do not use \t(TAB) for indentation` | The fix, spelled out: replace the tab with spaces. |
| `line 2, column 1` | Exactly where to look. |

**Mistake 3 — no space after the colon. Loud, but it points at the wrong line.**
Line 3 says `ttl:PT10M`:

```yaml
zenith:
  hold:
    ttl:PT10M
    max-seats-per-order: 8
```

The app refused to start:

```text
ERROR org.springframework.boot.SpringApplication -- Application run failed
mapping values are not allowed here
 in 'reader', line 4, column 24:
        max-seats-per-order: 8
                           ^
```

Without the space, YAML reads `ttl:PT10M` as one meaningless word, not as a key and a
value. The confusion only becomes visible on the *next* line, so the error names line 4
while the real mistake is on line 3. When a YAML error makes no sense, look one line
**above** the place it names.

**Mistake 4 — keeping both files. Silent.** "Replace" means the old file must go. Both
files kept on purpose: `application.properties` saying `zenith.hold.ttl=PT99M`,
`application.yml` saying `PT10M`. Result:

```text
zenith.hold.ttl = PT99M
zenith.hold.max-seats-per-order = 8
```

Both files load, and the merge happens key by key. For `ttl`, both files compete and
`.properties` wins in Spring Boot. For `max-seats-per-order`, only the YAML file has it,
so it loads fine. YAML edits to `ttl` would look mysteriously ignored. Delete the old
file.

### Link to the task

Subtask S1, part one: "Replace `application.properties` with `application.yml`" — the
nesting piece. Mistake 1 is the task's "Where people get stuck" paragraph, word for
word: a YAML indentation error is silently a different key.

### Do it yourself

Work inside `F6.4-configuration-files-and-the-precedence-order/scratch-app/` from
Lesson 1.

1. Delete the old file:

   ```bash
   rm src/main/resources/application.properties
   ```

2. Create `src/main/resources/application.yml` as shown in "The change" above. Add the
   `maxSeatsLine()` method to `ConfigReporter` and the second `println` to `main`.
3. Build and run:

   ```bash
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected last two lines:

   ```text
   zenith.hold.ttl = PT10M
   zenith.hold.max-seats-per-order = 8
   ```

4. Failure drill 1 — the silent one. Remove two spaces of indentation from both the
   `ttl` line and the `max-seats-per-order` line, so they align with `hold:`. Rebuild
   and run. Expected: clean startup, both lines print `null`. Say out loud which keys
   now exist instead.
5. Failure drill 2 — the loud one. Fix drill 1. Replace the two spaces before `hold:`
   with a tab character. Rebuild and run. Expected: startup fails with
   `found character '\t(TAB)' that cannot start any token`, naming line 2.
6. Repair the file. Rebuild, run, confirm both values print again.

**Done when:** you saw both values print, then `null null` with a clean startup, then
the tab error — and you can say in one sentence why drill 1 was silent but drill 2 was
loud.

### Understanding check — questions and answers

**Question 1.** An app contains only this `application.yml`:

```yaml
zenith:
  payments:
  provider-url: https://pay.example.com
```

The code calls `environment.getProperty("zenith.payments.provider-url")` and prints the
result. Does the app start, and what exactly does it print? Name the full key that
actually exists in this file.

**Answer.** The app starts normally — this is legal YAML. It prints `null`. The
`provider-url` line is indented two spaces, the same as `payments:`. So `provider-url`
is a child of `zenith`, not of `payments`. The key that actually exists is
`zenith.provider-url`. The key the code asks for, `zenith.payments.provider-url`, was
never set, and `getProperty` returns `null` for it without any error.

**Question 2.** Convert this `.properties` content into YAML with nesting. Write the
whole file:

```properties
zenith.payments.retry-count=3
zenith.payments.timeout=PT5S
```

**Answer.**

```yaml
zenith:
  payments:
    retry-count: 3
    timeout: PT5S
```

Shared path parts are written once. `zenith` is the outer parent, `payments` the inner
parent, and the two leaves carry the values. Two spaces per level, spaces only, one
space after each colon.

### Summary

- YAML shows structure with indentation. Dots in a key become levels of nesting.
- A key with a value on its line is a leaf. A key with indented lines under it is a
  parent. Full key = parents joined with dots.
- Two spaces per level, never tabs, always a space after the colon.
- Wrong indentation is **silent**: the key still exists, just somewhere else. Tabs and
  missing colon-spaces are **loud**: startup fails with a position in the message.
- A YAML error can point one line after the real mistake.
- When switching formats, delete the old file. If both exist, `.properties` wins per
  key in Spring Boot.

---

## Lesson 3 — YAML, part 2: a list (subtask S1, part two)

### Definition

A **YAML list** is several values under one key. Each value gets its own line. Each line
starts with a dash and a space: `- GBP`.

### Why it is needed

Some settings are naturally plural. The ticket system accepts three currencies. Without
lists you would invent numbered keys by hand (`currency1`, `currency2`, `currency3`) or
cram everything into one comma-separated string. Both hide the fact that this is one
setting with several values. A list says it directly.

### How it works

Writing it — three rules:

1. The key ends with a colon and has no value on its line: `supported-currencies:`.
2. Each item goes on its own line below, as a dash, a space, then the value: `- GBP`.
3. All items share the same indentation, deeper than their key.

Reading it — Spring flattens a YAML list into **indexed keys**. One key per item, with a
position in square brackets, counting from zero:

| YAML line | Key Spring creates | Value |
|---|---|---|
| `- GBP` | `zenith.payments.supported-currencies[0]` | `GBP` |
| `- EUR` | `zenith.payments.supported-currencies[1]` | `EUR` |
| `- USD` | `zenith.payments.supported-currencies[2]` | `USD` |

The bare key `zenith.payments.supported-currencies` — without brackets — holds
**nothing**. "The list becomes a `List<String>`" is true, but it happens later, through
typed configuration (Task F6.6 builds it). Typed configuration collects the indexed keys
and assembles the `List<String>`. The `Environment` shows the raw flattened truth.

### The change

`application.yml` now:

```yaml
zenith:
  hold:
    ttl: PT10M
    max-seats-per-order: 8
  payments:
    supported-currencies:
      - GBP
      - EUR
      - USD
```

One new method in `ConfigReporter`, plus its `println` in `main`:

```java
    public String currenciesLine() {
        return "zenith.payments.supported-currencies = "
                + environment.getProperty("zenith.payments.supported-currencies[0]") + ", "
                + environment.getProperty("zenith.payments.supported-currencies[1]") + ", "
                + environment.getProperty("zenith.payments.supported-currencies[2]");
    }
```

### Walkthrough — real probes

A temporary method probed the list five ways. Real output:

```text
bare key      = null
as List.class = null
index [0]     = GBP
index [1]     = EUR
index [2]     = USD
```

1. The bare key returns `null`. The list did not land there.
2. Even asking the `Environment` to convert to a `List` returns `null`. There is nothing
   under the bare key to convert.
3. The three indexed keys hold the three values.

Final app output:

```text
zenith.hold.ttl = PT10M
zenith.hold.max-seats-per-order = 8
zenith.payments.supported-currencies = GBP, EUR, USD
```

### Where it is used

Allowed website origins for a web API, addresses of several servers, HTTP status codes
worth retrying, alert email addresses. Docker Compose and Kubernetes use the same `- `
syntax for containers, ports, and volumes.

### Common mistakes — all run for real

**Mistake 1 — forgetting the space after the dash. Silent, and it corrupts the value.**

```yaml
    supported-currencies:
      -GBP
      -EUR
      -USD
```

Clean startup. Real probes:

```text
bare key      = -GBP -EUR -USD
index [0]     = null
```

Without the space, `-GBP` is not a list item — it is plain text. YAML joins consecutive
plain-text lines into one string separated by spaces. The bare key now holds garbage,
and no indexed keys exist. No error anywhere.

**Mistake 2 — the comma one-liner. Silent, and it sometimes "works", which is worse.**

```yaml
    supported-currencies: GBP, EUR, USD
```

Real probes:

```text
bare key      = GBP, EUR, USD
as List.class = [GBP, EUR, USD]
index [0]     = null
```

To YAML this is one string. But when asked to convert, Spring splits at the commas and
produces a list. Code that converts will work; code that reads indexed keys finds
nothing. The file lies about its own shape. Write real lists with dashes.

### Link to the task

Subtask S1, part two: the list is in `application.yml` and proven to load. Half of S2 is
now precise: list items are stored as `key[0]`, `key[1]`, `key[2]`; typed configuration
later assembles them into a `List<String>`.

### Do it yourself

1. Update `application.yml`, add `currenciesLine()` to `ConfigReporter`, add its
   `println` to `main` — as shown in "The change" above.
2. Build and run:

   ```bash
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected last three lines:

   ```text
   zenith.hold.ttl = PT10M
   zenith.hold.max-seats-per-order = 8
   zenith.payments.supported-currencies = GBP, EUR, USD
   ```

3. Failure drill — delete the space after each dash (`- GBP` → `-GBP`). Rebuild, run.
   Expected: clean startup and

   ```text
   zenith.payments.supported-currencies = null, null, null
   ```

4. See where the value went. Temporarily add one probe inside `currenciesLine()`:

   ```java
   + " | bare = " + environment.getProperty("zenith.payments.supported-currencies")
   ```

   Rebuild, run. Expected: ` | bare = -GBP -EUR -USD` at the end of the line.
5. Repair: restore the spaces, remove the probe. Rebuild, run, confirm.

**Done when:** you saw `GBP, EUR, USD`, then `null, null, null`, then the collapsed
string under the bare key — and you can say where YAML list items live in the
`Environment`.

### Understanding check — questions and answers

**Question 1.** An app contains only this `application.yml`:

```yaml
zenith:
  payments:
    supported-currencies:
      - GBP
      - EUR
      - USD
```

The code calls `environment.getProperty("zenith.payments.supported-currencies")`. What
does it return? And which exact key would you ask for to get the value `EUR`?

**Answer.** It returns `null`. Spring flattens the list into indexed keys, so the bare
key holds nothing. `EUR` is the second item, and indexes count from zero, so the key is
`zenith.payments.supported-currencies[1]`.

**Question 2.** Write a complete `application.yml` so that these two keys exist with
these values:

- `zenith.alerts.emails[0]` = `ops@example.com`
- `zenith.alerts.emails[1]` = `dev@example.com`

**Answer.**

```yaml
zenith:
  alerts:
    emails:
      - ops@example.com
      - dev@example.com
```

`zenith` is the outer parent, `alerts` the inner parent, `emails` the list key. Each
item is a dash, a space, and the value. The first item becomes `[0]`, the second `[1]`.

### Summary

- A YAML list: key with a colon, then one `- value` line per item, all equally indented.
- Spring flattens lists into indexed keys: `key[0]`, `key[1]`, ... The bare key holds
  nothing and returns `null`.
- `-GBP` without a space is not a list item. Consecutive plain lines fold into one
  string. Silent.
- `key: a, b, c` is one string to YAML. Spring's converter can split it, so it sometimes
  works and hides the misunderstanding. Write dashes.
- The `List<String>` arrives with typed configuration (Task F6.6), assembled from the
  indexed keys.

---

## Lesson 4 — The duration: `PT10M` (subtask S1 part three, S2 part)

### Definition

`PT10M` is an **ISO-8601 duration**: a standard text format for a length of time.
`PT10M` means ten minutes. ISO-8601 is the international standard for writing dates and
times as text; the duration format is the piece of it we need.

### Why it is needed

A configuration file stores text. Text numbers carry no unit. If the file says
`ttl: 10`, is that ten seconds, ten minutes, or ten milliseconds? Every reader guesses
differently, and one wrong guess in a payment timeout is a production incident. An
ISO-8601 duration carries the unit inside the value: `PT10M` can only mean ten minutes.

Second win: Java's built-in `java.time.Duration` class parses exactly this format with
`Duration.parse(...)`. The text in the file becomes a real time object in code, with
methods like `toMinutes()` — no hand-written unit math.

### How it works

1. Every duration starts with `P` ("period").
2. Day amounts come right after the `P`: `P1D` is one day.
3. Clock units need a `T` first ("time"): hours `H`, minutes `M`, seconds `S` after it.
4. Units combine, largest first: `P1DT12H`, `PT1H30M`.

The one trap: **`M` means minutes only after the `T`.** Before the `T`, `M` would mean
months — months vary in length, so `Duration` rejects them outright.

Every claim verified with real Java. The script is saved as
`scratch-app/duration-check.jsh`; run it with `jshell -s duration-check.jsh`:

```java
import java.time.Duration;
System.out.println("PT30S   -> " + Duration.parse("PT30S").getSeconds() + " seconds");
System.out.println("PT2M    -> " + Duration.parse("PT2M").getSeconds() + " seconds");
System.out.println("PT10M   -> " + Duration.parse("PT10M").getSeconds() + " seconds");
System.out.println("PT90S   -> " + Duration.parse("PT90S").getSeconds() + " seconds");
System.out.println("PT1M30S -> " + Duration.parse("PT1M30S").getSeconds() + " seconds");
System.out.println("PT1H30M -> " + Duration.parse("PT1H30M").toMinutes() + " minutes");
System.out.println("P1D     -> " + Duration.parse("P1D").toHours() + " hours");
System.out.println("P1DT12H -> " + Duration.parse("P1DT12H").toHours() + " hours");
System.out.println("pt10m   -> " + Duration.parse("pt10m").toMinutes() + " minutes (lowercase)");
try { Duration.parse("10"); } catch (Exception e) { System.out.println("10      -> " + e); }
try { Duration.parse("10m"); } catch (Exception e) { System.out.println("10m     -> " + e); }
try { Duration.parse("PT10"); } catch (Exception e) { System.out.println("PT10    -> " + e); }
try { Duration.parse("P1M"); } catch (Exception e) { System.out.println("P1M     -> " + e); }
/exit
```

Real output:

```text
PT30S   -> 30 seconds
PT2M    -> 120 seconds
PT10M   -> 600 seconds
PT90S   -> 90 seconds
PT1M30S -> 90 seconds
PT1H30M -> 90 minutes
P1D     -> 24 hours
P1DT12H -> 36 hours
pt10m   -> 10 minutes (lowercase)
10      -> java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
10m     -> java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
PT10    -> java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
P1M     -> java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
```

`PT90S` and `PT1M30S` are both legal and both mean ninety seconds. Lowercase works.

### The change

One method added to `ConfigReporter` (plus `import java.time.Duration;` and a `println`
in `main`):

```java
    public String ttlDecodedLine() {
        Duration ttl = Duration.parse(environment.getProperty("zenith.hold.ttl"));
        return "decoded ttl = " + ttl.toMinutes() + " minutes = " + ttl.getSeconds() + " seconds";
    }
```

### Walkthrough — real run

```text
zenith.hold.ttl = PT10M
decoded ttl = 10 minutes = 600 seconds
zenith.hold.max-seats-per-order = 8
zenith.payments.supported-currencies = GBP, EUR, USD
```

1. `getProperty("zenith.hold.ttl")` returns the text `PT10M`. To the `Environment` it is
   just a string.
2. `Duration.parse("PT10M")` reads the letters: `P` — a duration starts; `T` — clock
   units follow; `10M` — ten minutes. It returns a `Duration` object.
3. The object answers questions: `toMinutes()` says `10`, `getSeconds()` says `600`.

### Where it is used

`zenith.hold.ttl` is the business rule "a held seat stays held for ten minutes". The
same format runs through real systems: HTTP timeouts (`PT5S`), cache lifetimes (`PT1H`),
retry waits (`PT0.5S` — fractions are legal), token expiry (`P7D`). Spring Boot's own
time settings accept ISO-8601 durations.

### Common mistakes — run for real

**Mistake 1 — a bare number.** `ttl: 10` in `application.yml`. Spring started fine —
then the app crashed:

```text
Caused by: java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
	at java.base/java.time.Duration.parse(Duration.java:419)
	at com.zenith.holds.ConfigReporter.ttlDecodedLine(ConfigReporter.java:26)
	at com.zenith.holds.ConfigApplication.main(ConfigApplication.java:13)
```

Exit code: 1.

| Error piece | Plain English |
|---|---|
| `Caused by:` | Skip the wrapper lines; this is the real reason. |
| `Text cannot be parsed to a Duration` | The value is not ISO-8601. `10` has no `P`, no unit letter. |
| `at ...ConfigReporter.ttlDecodedLine(ConfigReporter.java:26)` | Exact file and line that tried to parse. Go there, check the value it read. |

`getProperty` itself returned `"10"` happily. The crash came only when code **used** the
value — after startup. Task F6.6 moves this failure to startup, where it belongs.

**Mistake 2 — the casual shorthand `10m`.** `Duration.parse` rejects it (verified
above). In this project, write ISO-8601 only.

**Mistake 3 — the months trap.** `P1M` is one *month* (`M` before the `T`), and
`Duration` refuses it (verified above). Ten minutes is `PT10M`. If a duration
misbehaves, check which side of the `T` the `M` is on.

### Link to the task

Subtask S2's middle piece, verbatim: "`PT10M` is an ISO-8601 duration meaning ten
minutes." `PT2M` (two minutes) and `PT30S` (thirty seconds) are the exact values the
precedence experiment in S3 injects from an environment variable and the command line.

### Do it yourself

1. Create `scratch-app/duration-check.jsh` with the script shown above. Run:

   ```bash
   jshell -s duration-check.jsh
   ```

   Expected: the thirteen decode lines shown above.
2. Add `import java.time.Duration;` and `ttlDecodedLine()` to `ConfigReporter`, and its
   `println` to `main`.
3. Build and run:

   ```bash
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: four lines, including `decoded ttl = 10 minutes = 600 seconds`.
4. Failure drill. Change `ttl: PT10M` to `ttl: 10`. Rebuild, run. Expected: startup,
   then a crash with `DateTimeParseException: Text cannot be parsed to a Duration`.
   Check `echo $?` right after — expected `1`.
5. Repair to `ttl: PT10M`. Rebuild, run, confirm.

**Done when:** the jshell table ran, the app prints `10 minutes = 600 seconds`, you
caused and fixed the parse crash, and you can say from memory what `P`, `T`, and the
after-`T` `M` mean.

### Understanding check — questions and answers

**Question 1.** How many total minutes is `PT1H30M`? How many seconds is `PT90S`? Is
`P1M` a valid value for a `java.time.Duration` — and why or why not?

**Answer.** `PT1H30M` is 90 minutes (one hour = 60, plus 30). `PT90S` is 90 seconds —
amounts above the usual clock range are legal. `P1M` is **not** valid for a `Duration`:
before the `T`, `M` means months, and months vary in length (28–31 days), so `Duration`
throws `DateTimeParseException`. Minutes must sit after the `T`: `PT1M`.

**Question 2.** Write "ninety seconds" as an ISO-8601 duration in two different legal
ways. Then write "one day and twelve hours" as one duration.

**Answer.** Ninety seconds: `PT90S` or `PT1M30S` — both parse to the same `Duration`
(verified: both print 90 seconds). One day and twelve hours: `P1DT12H` — day amounts
before the `T`, clock amounts after (verified: 36 hours).

### Summary

- ISO-8601 duration: `P`, then days, then `T`, then hours/minutes/seconds. The unit
  travels inside the value.
- `PT10M` = ten minutes = 600 seconds. `Duration.parse` turns the text into an object.
- `M` after the `T` is minutes. Before the `T` it is months, and `Duration` rejects
  months.
- Bare numbers (`10`), shorthand (`10m`), and missing units (`PT10`) all throw
  `DateTimeParseException`.
- The parse crash happens at the moment of use, not at startup. Late failures are the
  theme this task keeps circling.

---

## Lesson 5 — The placeholder with a default: `${...}` (completes S1 and S2)

### Definition

A **placeholder** is `${...}` written inside a configuration value. It means: "look up
this name, and insert its value here." With a colon inside, it carries a **default**:
`${PAYMENTS_URL:https://sandbox.payments.example.com}` means "use `PAYMENTS_URL` if it
is set anywhere; otherwise use this sandbox address."

### Why it is needed

The jar never changes. Your laptop must call a fake sandbox payment provider. Production
must call the real one. Without placeholders you would edit the file and rebuild per
environment — or commit production addresses into the repository. The placeholder splits
the decision: the *file* says "this value may come from outside, here is a safe
fallback"; the *environment the app runs in* decides the actual value.

### How it works

1. The file stores the raw text, placeholder included.
2. When a property is read, Spring scans its value for `${...}`.
3. For `${PAYMENTS_URL}`, Spring looks up the name like any other property — across
   **all** property sources. Operating-system environment variables are one of those
   sources.
4. Found somewhere → its value replaces the placeholder.
5. Found nowhere, but a default follows the colon → the default is used.
6. Found nowhere and no default → reading that property fails loudly.

The name inside `${...}` can also be one of your own properties: `${zenith.hold.ttl}`
inserts that property's value. Same lookup, same rules.

### The change — the task's full target file, complete

`application.yml`:

```yaml
zenith:
  hold:
    ttl: PT10M
    max-seats-per-order: 8
  payments:
    provider-url: ${PAYMENTS_URL:https://sandbox.payments.example.com}
    supported-currencies:
      - GBP
      - EUR
      - USD
```

New method in `ConfigReporter` (plus its `println` in `main`):

```java
    public String providerUrlLine() {
        return "zenith.payments.provider-url = "
                + environment.getProperty("zenith.payments.provider-url");
    }
```

### Walkthrough — three real runs, no rebuilds between them

**Run 1 — nothing set.** `java -jar target/config-scratch-0.0.1-SNAPSHOT.jar`:

```text
zenith.payments.provider-url = https://sandbox.payments.example.com
```

`PAYMENTS_URL` exists in no source. The default after the colon steps in.

**Run 2 — environment variable set.**
`PAYMENTS_URL=https://pay.zenith-prod.example.com java -jar target/config-scratch-0.0.1-SNAPSHOT.jar`:

```text
zenith.payments.provider-url = https://pay.zenith-prod.example.com
```

Writing `NAME=value` before a command gives that command an environment variable for
just that run. Same jar, different behavior, zero rebuilds.

**Run 3 — the reference form.** Temporarily added:

```yaml
  display:
    hold-banner: Seats are held for ${zenith.hold.ttl}
```

Real output:

```text
banner = Seats are held for PT10M
```

One value flowed into another.

### Where it is used

Database passwords and URLs (`${DB_PASSWORD}` — the secret lives in the platform, never
in the repository), per-environment service addresses. The reference form builds derived
values, like a base URL reused across several settings.

### Common mistakes — run for real

**Mistake 1 — no default for something that must exist.** Value changed to
`${PAYMENTS_URL}`, run without the variable. Exit code 1:

```text
Caused by: org.springframework.util.PlaceholderResolutionException:
Could not resolve placeholder 'PAYMENTS_URL' in value "${PAYMENTS_URL}"
```

| Error piece | Plain English |
|---|---|
| `Could not resolve placeholder 'PAYMENTS_URL'` | Spring searched every source for `PAYMENTS_URL` and found nothing. |
| `in value "${PAYMENTS_URL}"` | The exact value containing the failing placeholder. |

With the variable set, the same jar ran fine (verified). The crash came when the
property was *read*, after startup. No default means "this value is mandatory" —
sometimes that loud crash is exactly right, e.g. for a database password.

**Mistake 2 — a typo when setting the variable.** The file reads `${PAYMENTS_URL:...}`;
the run said `PAYMENT_URL=...` — one missing `S`. Real output:

```text
zenith.payments.provider-url = https://sandbox.payments.example.com
```

The default, silently. You believe you pointed the app at production; it quietly talks
to the sandbox. When an override seems ignored: print the value and compare the two
names letter by letter.

### Link to the task

**S1 complete**: nesting, a list, a duration, a placeholder with a default — every piece
proven by a real run. **S2 complete** — reading the file back:

| Piece | Name it out loud |
|---|---|
| `zenith.hold.ttl` | The full key: path of parents joined with dots. |
| `PT10M` | An ISO-8601 duration: ten minutes. |
| `max-seats-per-order: 8` | A plain number property: `zenith.hold.max-seats-per-order`. |
| `${PAYMENTS_URL:https://...}` | "Use `PAYMENTS_URL` if set anywhere; otherwise this sandbox default." |
| `- GBP / - EUR / - USD` | A list, stored as `supported-currencies[0..2]`, assembled into a `List<String>` by typed configuration later. |

### Do it yourself

1. Update `application.yml` to the full ten-line version; add `providerUrlLine()` and
   its `println`.
2. Build once, run twice:

   ```bash
   mvn clean package
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   PAYMENTS_URL=https://pay.zenith-prod.example.com java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected provider-url line: sandbox first, then the prod address. No rebuild between.
3. Typo drill: `PAYMENT_URL=https://pay.zenith-prod.example.com java -jar ...` (missing
   `S`). Expected: sandbox default, silently.
4. Crash drill: change the value to `${PAYMENTS_URL}` (no default). Rebuild, run with
   nothing set. Expected: `Could not resolve placeholder 'PAYMENTS_URL'`, exit code 1.
   Confirm the same jar works with the variable set.
5. Repair the default. Rebuild, run, confirm all five lines print.

**Done when:** you saw default, override, silent typo, and the loud crash — and you can
explain when a placeholder *should* have no default.

### Understanding check — questions and answers

**Question 1.** A configuration file contains:

```yaml
zenith:
  reports:
    mode: ${REPORT_MODE:summary}
```

The code reads `zenith.reports.mode` and prints it. What prints with no environment
variables at all? What prints when run as `REPORT_MODE=detailed java -jar app.jar`? And
if the file said `${REPORT_MODE}` with no default and no variable set — what happens
when the property is read?

**Answer.** With nothing set: `summary` — the name is found nowhere, the default steps
in. With the variable: `detailed` — the environment variable replaces the placeholder.
With `${REPORT_MODE}` and nothing set: reading the property throws
`PlaceholderResolutionException: Could not resolve placeholder 'REPORT_MODE'`. In our
app that crash happens right after startup, with exit code 1.

**Question 2.** Write one YAML property `zenith.checkout.timeout` whose value normally
comes from the environment variable `CHECKOUT_TIMEOUT`, but falls back to five seconds
(ISO-8601) when the variable is absent.

**Answer.**

```yaml
zenith:
  checkout:
    timeout: ${CHECKOUT_TIMEOUT:PT5S}
```

`CHECKOUT_TIMEOUT` is looked up across all sources; if absent, the ISO-8601 default
`PT5S` (five seconds) is used.

### Summary

- `${NAME}` = "insert the value of NAME, searched across all property sources".
- `${NAME:default}` = same, with a fallback when NAME is nowhere.
- Environment variables are a property source, so `NAME=value java -jar app.jar` feeds
  the placeholder. Same jar, different environments.
- `${NAME}` without default + NAME absent = loud `PlaceholderResolutionException` at
  read time. Good for mandatory secrets.
- A typo in the variable name at run time silently gives you the default. Print the
  value when in doubt.
- The name inside `${...}` can be another of your own properties.

---

## Lesson 6 — Property sources and the precedence order (subtasks S4 and S5)

### Definition

**Precedence** is the fixed order that decides which property source wins when two or
more of them set the same key. The order never changes. It does not depend on your code,
your files, or luck.

### Why it is needed

In Phase 13 the same jar runs on a laptop, in Docker Compose, and in Kubernetes. The
property `zenith.hold.ttl` will be set in `application.yml` inside the jar, *and* by an
environment variable in the container, *and* sometimes by a container argument — all at
once, with different values. If you cannot predict the winner, you debug by
superstition. If you know the order, you predict first, then run, and the output only
confirms what you already knew.

### How it works

The `Environment` keeps its property sources in a **fixed, ordered list**. When you call
`getProperty("zenith.hold.ttl")`:

1. It asks the highest source: "do you have this key?"
2. If yes — that value is returned. **Search over.** Lower sources are never consulted.
3. If no — it asks the next source down, to the bottom.

First hit wins. That is the whole machine.

The order, highest priority first — five levels that cover everything for a long time
(Spring's full list has more, but they are rare):

| Priority | Source | Example |
|---|---|---|
| 1 (highest) | Command-line arguments | `java -jar app.jar --zenith.hold.ttl=PT30S` |
| 2 | OS environment variables | `ZENITH_HOLD_TTL=PT2M java -jar app.jar` |
| 3 | Profile-specific file | `application-prod.yml`, loaded when the `prod` profile is on |
| 4 | Plain `application.yml` inside the jar | our file |
| 5 (lowest) | Defaults written in code | the fallback in `${PAYMENTS_URL:https://sandbox...}` |

(A **profile** is a named mode, like `prod` or `dev`, switched on at startup. When
active, Spring loads its extra file on top of the plain one.)

**Why the order is sensible (S5):** *the closer a source is to the moment of running,
the more specific the intent — so it wins.* The `${...:default}` was written months ago
for all runs everywhere: the floor. `application.yml` was baked in at build time: still
general. A profile file narrows to "prod runs". An environment variable narrows to "this
machine, this container". A command-line argument is typed by a human for this one run:
the most specific intent possible, so nothing beats it. General loses to specific.

This was already visible in Task F1.4: `server.port` set in the properties file *and* on
the command line — the command line won. Level 1 beat level 4.

### Example — two sources fighting over one key (real run from Lesson 2)

Both files inside the jar at once. `application.properties`:

```properties
zenith.hold.ttl=PT99M
```

`application.yml`:

```yaml
zenith:
  hold:
    ttl: PT10M
    max-seats-per-order: 8
```

Real output:

```text
zenith.hold.ttl = PT99M
zenith.hold.max-seats-per-order = 8
```

For `ttl`, both sources answered; the higher one won and the loser's value was never
consulted. For `max-seats-per-order`, only YAML had an answer, so it supplied the value.
**Winning happens per key, not per file.**

### Walkthrough — tracing the list

**Scenario A — plain run** (`java -jar app.jar`, file says `PT10M`):
command line? no → environment? no → profile file? none → `application.yml`? **yes:
`PT10M`, stop.**

**Scenario B — container-style run** (environment variable `PT2M`, file `PT10M`):
command line? no → environment? **yes: `PT2M`, stop.** The file's value is never looked
at.

### Where it is used

- Levels 4/5: safe sandbox values a developer ships — app runs on any laptop, zero
  setup.
- Level 3: "in prod mode, stricter settings", written once, shipped with the app.
- Level 2: Docker Compose / Kubernetes inject real database and payment URLs per
  environment. The jar stays identical.
- Level 1: a human overrides one value for one run — `--server.port=8081` because 8080
  is busy.

### Common mistakes

**Mistake 1 — "my file should win, I wrote it in the file!"** The jar file is the most
*general* statement, so it sits near the bottom. Everything closer to the run can
override it. That is the feature that lets one jar serve three environments.

**Mistake 2 — the stale environment variable.** You edit `application.yml`, rebuild,
run — the old value keeps appearing. The cause: an `export ZENITH_HOLD_TTL=...` left in
your shell last week. Level 2 silently beats your level 4 edit. When an edit seems
ignored, ask which *higher* source answers first.

**Mistake 3 — reversing the profile rule.** `application-prod.yml` beats plain
`application.yml`, not the other way round. The profile file is more specific, and
specific wins. Keys it does not mention still come from the plain file — per key, as
always.

### Link to the task

S4: the order written into `NOTES.md` (Do it yourself below). S5: the justification —
closer to the run → more specific intent → wins. S3 (proving it with runs) is Lesson 8.

### Do it yourself

1. In `F6.4-configuration-files-and-the-precedence-order/`, create `NOTES.md`.
2. Write the precedence order from memory, highest first. Check against this lesson and
   fix slips: command-line arguments → OS environment variables → profile-specific file →
   plain `application.yml` → defaults in code.
3. Under the list, one sentence **in your own words** on why the order is sensible.
4. Recite drill: say the five levels out loud, top to bottom, then bottom to top.
   Repeat tomorrow.

**Done when:** `NOTES.md` has the five levels in order plus your own justification, and
you can recite the order without looking.

### Understanding check — questions and answers

**Question 1.** One app, one key. `application.yml` inside the jar says
`zenith.hold.ttl: PT10M`. The container sets the environment variable for that key to
`PT2M`. The start command is `java -jar app.jar --zenith.hold.ttl=PT30S`. (a) Which
value does `getProperty("zenith.hold.ttl")` return, and why? (b) The command-line
argument is removed and the app restarts — now which value, and why?

**Answer.** (a) `PT30S`. The command line is level 1, the highest source. The search
stops at the first source that has the key, so the environment variable and the file are
never consulted. (b) `PT2M`. With the command line gone, the search moves down and the
environment variable (level 2) is the first source with an answer. The file's `PT10M`
loses both times — it is only the floor.

**Question 2.** In your own words — the reasoning, not the rule: why is it sensible that
a command-line argument beats a file packaged inside the jar?

**Answer (model).** The file was written long before the run, by someone making a
general choice for every environment at once. The command-line argument was typed by a
person for this exact run, right now. The more recent, more specific intent should win —
otherwise there would be no way to override a general default without rebuilding the
jar.

### Summary

- Precedence = fixed, ordered list of sources. `getProperty` walks top down; first hit
  wins; losers are never consulted.
- The five levels, highest first: command line → environment variables → profile file →
  plain file → code defaults.
- The reason it is this way: closer to the moment of running = more specific intent =
  wins. A rule you can justify is a rule you remember.
- Overriding is per key, not per file.
- When an edit seems ignored, look for a higher source answering first.

---

## Lesson 7 — Relaxed binding: how `ZENITH_HOLD_TTL` finds `zenith.hold.ttl` (subtask S6)

### Definition

**Relaxed binding** is Spring accepting several spellings of the same property name. Its
main job: letting an operating-system environment variable, which cannot be named
`zenith.hold.ttl`, still set the property `zenith.hold.ttl`.

### Why it is needed

The shell refuses the obvious attempt. Real output:

```text
$ export zenith.hold.ttl=PT2M
export: not valid in this context: zenith.hold.ttl
```

Shell variable names allow letters, digits, and underscores — no dots, no hyphens. But
level 2 of the precedence order, the level Docker Compose and Kubernetes use for
everything, is exactly these variables. Relaxed binding is the translation rule that
makes level 2 able to set dotted keys at all.

### How it works

To build the environment variable name for a property key:

1. Uppercase everything.
2. Replace dots with underscores.
3. Remove hyphens.

| Property key | Environment variable |
|---|---|
| `zenith.hold.ttl` | `ZENITH_HOLD_TTL` |
| `zenith.payments.provider-url` | `ZENITH_PAYMENTS_PROVIDERURL` |
| `zenith.hold.max-seats-per-order` | `ZENITH_HOLD_MAXSEATSPERORDER` |

Reading direction: when Spring's search reaches the environment-variables source, it
translates the key it wants into this form and checks. A match is a level 2 answer — it
beats the file.

How forgiving is the layer really? Tested against the real jar — all of these overrode
the file:

| Variable set | Result |
|---|---|
| `ZENITH_PAYMENTS_PROVIDERURL` (canonical) | worked |
| `ZENITH_PAYMENTS_PROVIDER_URL` (hyphen became underscore) | worked |
| `ZENITH_HOLD_MAX_SEATS_PER_ORDER` | worked |
| `zenith_hold_ttl` (lowercase) | worked |

Slack in the rope, not an invitation: **always write the canonical form** — it is the
one spelling every Spring layer, document, and Kubernetes manifest agrees on.

### Example — the S6 confirm, real runs, no rebuild

```text
$ ZENITH_PAYMENTS_PROVIDERURL=https://pay.direct.example.com java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
zenith.payments.provider-url = https://pay.direct.example.com

$ ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
zenith.hold.ttl = PT2M
decoded ttl = 2 minutes = 120 seconds
```

The decoded line changed too: the parsed `Duration` the code works with is now two
minutes. Configuration from outside changed real behavior, jar untouched.

### Walkthrough

1. The app starts with `ZENITH_PAYMENTS_PROVIDERURL` in its environment.
2. `getProperty("zenith.payments.provider-url")` walks the sources, highest first.
3. Command line: nothing. Down.
4. Environment variables: translate the key → `ZENITH_PAYMENTS_PROVIDERURL`. Present?
   **Yes. Stop.**
5. The file — and the placeholder inside it — are never consulted.

### Where it is used

Docker Compose `environment:` blocks and Kubernetes `env:` blocks set exactly these
variables. Phase 13 writes these lines; they work only when spelled by the recipe.

### Common mistakes — run for real

**Mistake 1 — one letter off, total silence.** Run with `ZENITH_PAYMENT_PROVIDERURL`
(missing `S`):

```text
zenith.payments.provider-url = https://sandbox.payments.example.com
```

The default. The misspelled variable is just an unrelated variable. Print the value,
compare names letter by letter.

**Mistake 2 — confusing the two override routes.**

| | `PAYMENTS_URL` | `ZENITH_PAYMENTS_PROVIDERURL` |
|---|---|---|
| Why it works | The file's value contains `${PAYMENTS_URL:...}`, which looks this name up | Relaxed binding: the variable *is* the property, at level 2 |
| Depends on the file? | Yes — delete the placeholder and it does nothing | No — works even if the file never mentions the key |
| Level it acts at | Feeds a placeholder inside a level 4 value | Overrides the whole key at level 2 |

Both set at once, different values — real run:

```text
$ PAYMENTS_URL=https://pay.via-placeholder.example.com \
  ZENITH_PAYMENTS_PROVIDERURL=https://pay.direct.example.com \
  java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
zenith.payments.provider-url = https://pay.direct.example.com
```

The direct route wins: it answers at level 2, so the level 4 file value — the only place
the placeholder lives — is never read. The placeholder never runs.

**Mistake 3 — leaning on the forgiving spellings.** Lowercase and underscored variants
worked today, on this layer. Write the canonical form anyway; nonstandard spellings are
hidden landmines around stricter tooling and teammates' searches.

### Link to the task

S6 done and confirmed by real run: `ZENITH_PAYMENTS_PROVIDERURL` set
`zenith.payments.provider-url`. The experiment's middle run in S3
(`ZENITH_HOLD_TTL=PT2M`) is now mechanics, not magic.

### Do it yourself

No rebuild needed.

1. Feel the problem: `export zenith.hold.ttl=PT2M` → expect the shell to refuse.
2. The S6 confirm:

   ```bash
   ZENITH_PAYMENTS_PROVIDERURL=https://pay.direct.example.com java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: `zenith.payments.provider-url = https://pay.direct.example.com`.
3. The duration key:

   ```bash
   ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ```

   Expected: `PT2M` and `decoded ttl = 2 minutes = 120 seconds`.
4. Typo drill: step 2 with `ZENITH_PAYMENT_PROVIDERURL` (no `S`). Expected: sandbox
   default, silently.
5. Collision drill: set both `PAYMENTS_URL=https://a.example.com` and
   `ZENITH_PAYMENTS_PROVIDERURL=https://b.example.com`. Predict first. Expected:
   `https://b.example.com`.

**Done when:** all five runs matched your predictions, and you can write the canonical
variable name for any dotted key without hesitating.

### Understanding check — questions and answers

**Question 1.** Give the canonical environment variable name for
`zenith.payments.retry-count` and for `zenith.hold.ttl`.

**Answer.** `ZENITH_PAYMENTS_RETRYCOUNT` — uppercase, dots to underscores, the hyphen in
`retry-count` removed. And `ZENITH_HOLD_TTL` — uppercase, dots to underscores, no
hyphens to remove.

**Question 2.** An app's `application.yml` contains exactly:

```yaml
zenith:
  payments:
    provider-url: ${PAYMENTS_URL:https://sandbox.payments.example.com}
```

Started as:

```bash
PAYMENTS_URL=https://a.example.com ZENITH_PAYMENTS_PROVIDERURL=https://b.example.com java -jar app.jar
```

The code prints `getProperty("zenith.payments.provider-url")`. Which URL prints, and
why?

**Answer.** `https://b.example.com`. Walk the list: command line — nothing. Environment
variables — translate the key to `ZENITH_PAYMENTS_PROVIDERURL`; it is set; **stop**. The
search never reaches the file, so the file's value, which is where the placeholder
lives, is never read — `PAYMENTS_URL` and its `https://a.example.com` never come into
play. A level 2 answer beats everything below it.

### Summary

- Relaxed binding translates between environment-variable names and dotted keys.
- Recipe: uppercase, dots → underscores, hyphens removed. `zenith.payments.provider-url`
  → `ZENITH_PAYMENTS_PROVIDERURL`.
- The layer accepts looser spellings (tested), but write the canonical form always.
- A typo'd variable is silently ignored — it is just another unrelated variable.
- Two override routes exist for our URL: the placeholder name (`PAYMENTS_URL`, inside a
  level 4 value) and the relaxed-bound key (`ZENITH_PAYMENTS_PROVIDERURL`, level 2). When
  both are set, level 2 wins and the placeholder never runs.

---

## Lesson 8 — The experiment: three sources, three runs (subtask S3)

### Definition

The task's centerpiece: the same property, `zenith.hold.ttl`, set through three
different sources, the winner **predicted before each run**. New mechanism: a
**command-line argument** of the form `--key=value`, written after the jar name, sets a
property at level 1 — the top of the order.

### Why it is needed

*Demonstrate precedence rather than reading about it.* A rule you have only read is a
rule you will doubt at 2 a.m. Say the winner out loud, run, watch the output agree.
After three correct predictions the order stops being memorized text.

### How it works — where command-line properties come from

1. Everything after the jar path belongs to our program:
   `java -jar app.jar --zenith.hold.ttl=PT30S`.
2. Java hands those words to `main` as the `args` array.
3. `main` passes them on: `SpringApplication.run(ConfigApplication.class, args)`.
4. Spring scans `args` for `--key=value` pairs and builds them into the **highest**
   property source, level 1.

Spelling rules, enforced the hard way below: the pair needs the `=`, and it must come
*after* the jar path.

### The experiment — four real runs, no rebuilds

File says `ttl: PT10M` throughout.

**Run 1 — file only.** Prediction: no higher source has the key → `PT10M`.

```bash
java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
```

```text
zenith.hold.ttl = PT10M
decoded ttl = 10 minutes = 600 seconds
```

**Run 2 — environment variable.** `ZENITH_HOLD_TTL` is `zenith.hold.ttl` by relaxed
binding → level 2. Prediction: level 2 beats level 4 → `PT2M`.

```bash
ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
```

```text
zenith.hold.ttl = PT2M
decoded ttl = 2 minutes = 120 seconds
```

**Run 3 — command-line argument.** Level 1. Prediction: beats the file → `PT30S`.

```bash
java -jar target/config-scratch-0.0.1-SNAPSHOT.jar --zenith.hold.ttl=PT30S
```

```text
zenith.hold.ttl = PT30S
decoded ttl = 0 minutes = 30 seconds
```

(`0 minutes` is honest: `toMinutes()` counts whole minutes; thirty seconds is less than
one.)

**Run 4 — tiebreaker.** Environment variable *and* command line, different values.
Prediction: level 1 beats level 2 → `PT30S`.

```bash
ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar --zenith.hold.ttl=PT30S
```

```text
zenith.hold.ttl = PT30S
decoded ttl = 0 minutes = 30 seconds
```

Four predictions, four confirmations.

### The record (S3) — copy into NOTES.md

| Run | Sources setting `zenith.hold.ttl` | Winner | Why |
|---|---|---|---|
| 1 | file `PT10M` | `PT10M` | Only source with the key — level 4 answers. |
| 2 | file `PT10M`, env var `PT2M` | `PT2M` | Level 2 answers before level 4 is consulted. |
| 3 | file `PT10M`, command line `PT30S` | `PT30S` | Level 1 answers first. |
| 4 | file `PT10M`, env `PT2M`, command line `PT30S` | `PT30S` | Level 1 beats level 2 beats level 4. |

The search always stops at the highest source that has the key.

### Where it is used

Run 2 is Docker Compose / Kubernetes in miniature: the container injects a variable, the
jar never changes. Run 3 is the operator's emergency lever: restart one instance with an
override, delete it, normality returns. Run 4 is why the lever is safe: it beats even
the container's variables and vanishes when the run ends.

### Common mistakes — all run for real

**Mistake 1 — a space instead of `=`. The nastiest.**
`java -jar ... --zenith.hold.ttl PT30S` sets the property to the **empty string** at
level 1 — beating the file — then the app crashes at the point of use:

```text
zenith.hold.ttl = 
Caused by: java.time.format.DateTimeParseException: Text cannot be parsed to a Duration
	at com.zenith.holds.ConfigReporter.ttlDecodedLine(ConfigReporter.java:26)
```

The argument half-worked: the good `PT10M` was overridden by emptiness, and the failure
surfaced later, far from the cause. Without the parse, this would be fully silent.

**Mistake 2 — the argument before `-jar`.**
`java --zenith.hold.ttl=PT30S -jar app.jar` — arguments before the jar path belong to
the JVM, which does not know this option:

```text
Unrecognized option: --zenith.hold.ttl=PT30S
Error: Could not create the Java Virtual Machine.
```

Exit code 1, nothing started. Loud and immediate — the kindest failure here. Word
order: `java -jar <jarfile> --our.properties=here`.

**Mistake 3 — a single dash.** `java -jar app.jar -zenith.hold.ttl=PT30S` → output
`PT10M`. One dash does not make a property; silently ignored. Two dashes, always.

### Link to the task

S3 demonstrated end to end: file → environment variable → command line, each winner
predicted beforehand, each confirmed, results recorded. The task's "done when" asks
exactly this.

### Do it yourself

No rebuilds needed.

1. Predict out loud before each run:

   ```bash
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar
   java -jar target/config-scratch-0.0.1-SNAPSHOT.jar --zenith.hold.ttl=PT30S
   ZENITH_HOLD_TTL=PT2M java -jar target/config-scratch-0.0.1-SNAPSHOT.jar --zenith.hold.ttl=PT30S
   ```

   Expected ttl lines: `PT10M`, `PT2M`, `PT30S`, `PT30S`.
2. Copy the four-row record table above into `NOTES.md`, under your precedence list.
3. Failure drills:
   - `--zenith.hold.ttl PT30S` (space) → empty ttl line, then `DateTimeParseException`.
   - argument before `-jar` → `Unrecognized option`, no startup.
   - single dash → `PT10M`, silently ignored.

**Done when:** all four predictions were right before the runs, the record table is in
`NOTES.md`, and you can name which drill failure is silent, which is loud-late, and
which is loud-early.

### Understanding check — questions and answers

**Question 1.** `application.yml` inside the jar says `zenith.hold.ttl: PT10M`. The app
starts as:

```bash
ZENITH_HOLD_TTL=PT5M java -jar app.jar --zenith.hold.ttl=PT1M
```

(a) What does `getProperty("zenith.hold.ttl")` return? (b) The operator deletes
`--zenith.hold.ttl=PT1M` and restarts — now what? (c) The container is also rebuilt
without the environment variable — now what?

**Answer.** (a) `PT1M` — the command line is level 1, highest; search stops there.
(b) `PT5M` — with level 1 gone, the environment variable at level 2 is the highest
source holding the key. (c) `PT10M` — only the file (level 4) remains. Each layer peeled
away hands the answer to the next one down.

**Question 2.** A colleague runs `java -jar app.jar --zenith.hold.ttl PT30S` (a space
before `PT30S`) against the same file. The app prints the property and then crashes.
What value did the property have, which source supplied it, and why did the file's
`PT10M` not save the situation?

**Answer.** The property was the **empty string**. The command-line source supplied it:
`--zenith.hold.ttl` without `=` means "this key, empty value", and that is still a
level 1 answer. Because level 1 had the key, the search stopped there — the file's
`PT10M` was never consulted. The crash came when `Duration.parse("")` ran. A higher
source with a *bad* value still wins; precedence has no opinion about quality.

### Summary

- `--key=value` after the jar path → level 1, the top of the order. Needs the `=`,
  needs both dashes, needs to come after the jar.
- Four runs, four correct predictions: `PT10M` → `PT2M` → `PT30S` → `PT30S` (command
  line beat the environment variable).
- The search stops at the highest source holding the key — even when its value is empty
  or wrong.
- Failure styles: space instead of `=` is silent-then-late; argument before `-jar` is
  loud-early; single dash is fully silent.

---

## Lesson 9 — Break it on purpose: the silent typo (subtask S7)

### Definition

The failure mode of a mistyped property **key**. Write `zenith.hold.tt1` — digit one
instead of the letter L — and the application starts perfectly. No error, no warning.
The value is simply gone. Unknown keys are ignored without complaint.

### Why it is needed

Every other breakage in this task had a symptom: a parse error with a line number, a
crash, a refused startup. This one has nothing. Cause it deliberately once and sit with
how dangerous it is. The engineer who has felt this silence checks key spellings
*first*; the one who has not loses an afternoon reading code that was never wrong.

### How it works — why Spring *cannot* complain

1. The file declares keys. `zenith.hold.tt1` is a legal key name — some app might
   genuinely want it. There is no master list of "real" keys at this level.
2. The code asks for keys by name. A lookup that finds nothing returns `null` — that is
   the contract, not an error.
3. Nothing cross-checks the two sets. A key nobody reads harms nobody, as far as Spring
   can tell.

The file speaks, the code listens, no referee checks the names match. Building that
referee is Task F6.6 — typed configuration that fails at startup.

### The experiment — real runs

`application.yml` changed by one character:

```yaml
zenith:
  hold:
    tt1: PT10M
    max-seats-per-order: 8
```

**Pure form** (the `Duration.parse` line disabled, one probe added):

```text
INFO ... : Started ConfigApplication in 0.355 seconds
zenith.hold.ttl = null
zenith.hold.tt1 = PT10M
zenith.hold.max-seats-per-order = 8
zenith.payments.provider-url = https://sandbox.payments.example.com
zenith.payments.supported-currencies = GBP, EUR, USD
```

Exit code: **0**. Clean startup, every other property fine, the hold time gone. The
probe shows the value did not vanish — it sits under a name nobody reads. Same disease
as the Lesson 2 mis-indent: not a missing value, a *different key*.

**Loud costume** (our real app, parse line active):

```text
zenith.hold.ttl = null
Caused by: java.lang.NullPointerException: text
```

Exit code: 1. `Duration.parse(null)` threw (`text` is the parameter that was null). The
error points at `ConfigReporter` — the reading code — not at `application.yml`. Crash
site and cause live in different files. The loud version only exists because our code
parses the value immediately; a value just passed along (like a URL) crashes nothing.

### Walkthrough

1. YAML loads. `zenith.hold.tt1` is legal → enters the `Environment` with `PT10M`.
2. `getProperty("zenith.hold.ttl")` walks every source. No source has that key.
3. Returns `null` — the normal answer for an absent key.
4. Pure form: prints `null`, exits 0. Real app: `Duration.parse(null)` → crash.

### Where it bites

- **A database password**: key typo'd → app reads `null` → authentication fails, or a
  default connects to the wrong database.
- **The payment URL — literally in our file**: production override key typo'd → the real
  key falls back to `https://sandbox.payments.example.com` → app green, logs say
  payments succeed, the sandbox approves everything, **no money moves**. The finance
  team finds it, not the logs.

### Common mistakes

**Mistake 1 — trusting a clean startup.** Startup succeeded in 0.355 seconds with a
broken configuration. At this level startup proves only that the YAML parsed.

**Mistake 2 — hunting in the code first.** The stack trace names the reading code.
Check the key's spelling in the file against the code first — it costs ten seconds.

**Mistake 3 — assuming silent and loud are different bugs.** The exit-0 silence and the
`NullPointerException` were the *same one-character typo*. Quiet or noisy depends only
on what the code does with the value afterward. Treat any unexplained `null` as a
possible key mismatch.

### Link to the task

S7 word for word: key changed to `zenith.hold.tt1`, app started perfectly, value gone.
This is also checkpoint question 4. The fix is Task F6.6.

### Do it yourself

1. Change `ttl:` to `tt1:` (digit one). Rebuild, run, `echo $?`. Expected:
   `zenith.hold.ttl = null`, then `NullPointerException: text`, exit `1`.
2. Pure silence: comment out `System.out.println(reporter.ttlDecodedLine());` in
   `main`. Rebuild, run, `echo $?`. Expected: clean startup, `null`, everything else
   normal, exit `0`.
3. Find the value — add a probe in `main`:

   ```java
   System.out.println("zenith.hold.tt1 = "
           + context.getEnvironment().getProperty("zenith.hold.tt1"));
   ```

   Expected: `zenith.hold.tt1 = PT10M`.
4. In `NOTES.md`, write one sentence of your own: what happens in production if the
   payment URL key is the one typo'd.
5. Repair: `ttl:` back, uncomment, delete the probe. Rebuild, run, confirm all five
   lines and exit `0`.

**Done when:** the same typo produced both exit-0 silence and a misleading crash, the
production sentence is in `NOTES.md`, and the app is green again.

### Understanding check — questions and answers

**Question 1.** An `application.yml` contains:

```yaml
zenith:
  hold:
    tt1: PT10M
```

(digit one in the key). The app's code runs at startup:

```java
System.out.println(environment.getProperty("zenith.hold.ttl"));
Duration d = Duration.parse(environment.getProperty("zenith.hold.ttl"));
```

(a) Does Spring report any problem while starting? (b) What does the first line print?
(c) What happens at the second line? (d) What would
`environment.getProperty("zenith.hold.tt1")` return?

**Answer.** (a) No — `tt1` is a legal key, startup is clean. (b) `null` — no source has
`zenith.hold.ttl`. (c) `Duration.parse(null)` throws `NullPointerException` (message
`text`, the null parameter); the app crashes with exit code 1, and the stack trace
points at the reading code, not the file. (d) `PT10M` — the value sits faithfully under
the typo'd key.

**Question 2.** Why does Spring not warn about unknown keys at this level? What piece of
knowledge would it need that it does not have?

**Answer.** Because any key name is legal and keys are only ever *asked for*, never
*declared as the complete expected set*. Spring would need the list of keys this
application considers valid — which keys must exist, which are optional, which are
misspellings. At the `Environment` level that list does not exist. Typed configuration
(Task F6.6) supplies exactly that list, which is why it can fail at startup instead of
staying silent.

### Summary

- A mistyped key is not an error. It is a different, legal key that nobody reads.
- The intended key returns `null`; startup stays clean; exit code 0 unless the code
  happens to use the value immediately.
- When it does crash, the trace points at the reading code — far from the cause. Check
  file-vs-code key spellings first.
- The danger scales with the value's importance: passwords, payment URLs.
- The cure is a declared, checked configuration shape — Task F6.6.

---

## Lesson 10 — Close-out: the full source list, the checkpoint, the audit (subtasks S9 and CP)

### What this lesson is

Three finishing moves: the answer to subtask S9's ask-AI prompt, with experimental
evidence; the four checkpoint questions with model answers (say yours out loud first);
and the final audit against the task's "done when".

### The full source list, highest priority first (S9)

The five-level list is the daily-work skeleton. The full ladder has more rungs. Rows
marked **proved** were demonstrated with real runs on our jar; rows marked *docs* are
documented Spring Boot behavior not demoed in this task.

| # | Source | Right place for... | Evidence |
|---|---|---|---|
| 1 | Command-line arguments (`--key=value` after the jar) | A human overriding one value for one run: `--server.port=8081` while 8080 is busy. Gone when the run ends. | **proved** — beat everything below, including the JSON variable |
| 2 | `SPRING_APPLICATION_JSON` (several properties as JSON in one variable) | A platform that can only inject one variable but needs to pass a whole batch of settings. | **proved** — `{"zenith.hold.ttl":"PT4M"}` beat `-D` and the plain variable |
| 3 | Java system properties (`-Dkey=value`, written **before** `-jar`) | Launch scripts and service wrappers where you control JVM flags: `-Dspring.profiles.active=prod` in a start script. | **proved** — `PT7M` beat the environment variable's `PT2M`, lost to the command line |
| 4 | OS environment variables (relaxed binding names) | The container workhorse: Docker Compose and Kubernetes inject per-environment database and payment URLs here. | **proved** — Lessons 7 and 8 |
| 5 | External config file beside the jar (`./application.yml`, also `./config/`) | A VM deployment where operations edits a file next to the jar — no rebuild, no redeploy. | **proved** — a `PT8M` file beside the jar beat the packed `PT10M` |
| 6 | Profile-specific file (`application-prod.yml`) | Presets shipped with the app: "when running in prod mode, use stricter values." Beats the plain file at the same location. | *docs* — profiles get their own task |
| 7 | Packed `application.properties`, then packed `application.yml` | Developer defaults: sandbox URLs, safe TTLs, so the app runs on any laptop with zero setup. At the same location `.properties` beats `.yml`. | **proved** — Lesson 2's both-files run |
| 8 | `@PropertySource` on a configuration class | Loading an extra legacy `.properties` file that a library expects. | *docs* |
| 9 | Code defaults: `SpringApplication` default properties; the fallback in `${NAME:fallback}` | The floor. Per-value fallbacks at the point of use — our sandbox payment URL. | **proved** — Lesson 5 |

(Curiosity: a `random.*` source also exists — `${random.int}` and friends — mostly for
test ports. Rarely met.)

The reasoning survives the longer list unchanged: each row is one step farther from the
moment of running than the row above. Typed for this run → injected as this run's JSON →
set in this run's JVM flags → set on this machine → placed beside this deployment →
shipped for this mode → baked in for everyone → written in code. Specific beats general
at every rung.

The extended battery, all real runs on the unchanged jar (file value `PT10M`
throughout):

```text
-Dzenith.hold.ttl=PT7M                                   -> PT7M
ZENITH_HOLD_TTL=PT2M + -Dzenith.hold.ttl=PT7M            -> PT7M   (system property beats env var)
-Dzenith.hold.ttl=PT7M + --zenith.hold.ttl=PT30S         -> PT30S  (command line beats system property)
SPRING_APPLICATION_JSON='{"zenith.hold.ttl":"PT4M"}'     -> PT4M
JSON + -D + env var together                             -> PT4M   (JSON beats both)
JSON + --zenith.hold.ttl=PT30S                           -> PT30S  (command line beats JSON)
./application.yml beside the jar saying PT8M             -> PT8M   (external file beats packed file)
external file deleted                                    -> PT10M
```

### The checkpoint (CP) — answer out loud first, then compare

**CP1. Recite the precedence order from memory, highest priority first.**

*Model answer:* command-line arguments → OS environment variables → profile-specific
file (`application-prod.yml`) → plain `application.yml` → defaults written in code.

**CP2. Why does a command-line argument beat a file inside the jar? Reasoning, not
rule.**

*Model answer:* The file was written at build time — the most general statement
possible, one value for every run everywhere. The command-line argument was typed by a
person for this exact run, right now. The closer a source is to the moment of running,
the more specific the intent, so it wins. If general beat specific, nothing could be
overridden without rebuilding the jar.

**CP3. Which environment variable name sets `zenith.payments.provider-url`?**

*Model answer:* `ZENITH_PAYMENTS_PROVIDERURL` — uppercase, dots to underscores, hyphens
removed. Confirmed by a real run in Lesson 7.

**CP4. You typed a property key wrongly. What does the application do at startup, and
why is that a problem?**

*Model answer:* It starts perfectly. The mistyped key is a legal key, so the value loads
under the wrong name; the intended key exists in no source, so reads return `null` or
fall to a default. No error, no warning. The problem: the failure is silent and surfaces
far from the cause — a payment URL quietly falling back to the sandbox while logs show
success. Typed configuration (Task F6.6) declares the expected keys and fails at startup
instead.

### The final audit — the task's "done when"

| Requirement | Status |
|---|---|
| Same property set in three sources (S3) | Done — Lesson 8: file `PT10M`, env var `PT2M`, argument `PT30S`, plus the tiebreaker. |
| Winner predicted before each run | Done — four predictions, four confirmations. |
| Precedence order in your own notes (S4, S5) | Yours: `NOTES.md` with the five levels, your one-sentence why, the four-run table, the Lesson 9 production sentence. |
| YAML with nesting, list, duration, placeholder (S1, S2) | Done — Lessons 2–5, final file proven by runs. |
| Relaxed binding confirmed (S6) | Done — Lesson 7. |
| Silent typo caused and understood (S7) | Done — Lesson 9. |
| `Environment` injected and queried (S8) | Done — Lesson 1, used everywhere since. |
| Ask-AI on all sources (S9) | Answered above; optionally compare another AI's answer against the proved table. |
| Checkpoint out loud (CP) | Model answers above; say yours first. |

### Do it yourself

1. Finish `NOTES.md`: the five-level order (highest first), your own one-sentence why,
   the four-run results table from Lesson 8, the Lesson 9 production sentence.
2. Answer CP1–CP4 out loud, book closed.
3. Optional spaced repetition: rerun any one Lesson 8 experiment tomorrow, predicting
   first.

**Done when:** `NOTES.md` is complete, the four answers come out clean, and the audit
table has no open rows.

### Task summary — F6.4 in eight lines

- A property is a key and a value; the `Environment` holds every property from every
  source, merged; `getProperty` returns the winner or `null`, never an error.
- YAML: indentation is structure. Two spaces, never tabs. A mis-indented or mistyped key
  is a *different* key, silently.
- Lists become indexed keys (`[0]`, `[1]`, ...); durations are ISO-8601 (`PT10M` = ten
  minutes); `${NAME:default}` pulls from any source with a fallback.
- Precedence, highest first: **command line → environment variables → profile file →
  packed file → code defaults.**
- The reason: closer to the moment of running = more specific intent = wins.
- The search stops at the first source holding the key — even if its value is empty or
  wrong. Overriding is per key, not per file.
- Relaxed binding: uppercase, dots to underscores, hyphens removed —
  `ZENITH_PAYMENTS_PROVIDERURL`.
- Silence is configuration's default failure mode: unknown keys, typo'd variables, and
  mis-indents all pass without complaint. Print the value when in doubt; Task F6.6 adds
  the startup-time referee.

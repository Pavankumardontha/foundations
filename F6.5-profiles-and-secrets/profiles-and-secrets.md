# F6.5 — Profiles and secrets

**Goal of the task.** Run one jar against two different databases by changing one
environment variable. Switch a bean implementation by profile. Explain where a database
password must live, and where it must never live.

**Done when:** the same jar connects to two different databases based only on
`SPRING_PROFILES_ACTIVE`, a fake payment gateway is wired under `local` only, and no
password appears in any tracked file.

**You need first:** Task F6.4 (configuration files and the precedence order).

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Profile** | A named set of configuration, switched on by name. `local`, `test`, `prod`. |
| **Profile-specific file** | `application-local.yml` loads only when the `local` profile is active. Its values override `application.yml`. |
| **`SPRING_PROFILES_ACTIVE`** | The environment variable that chooses which profiles are on. |
| **`@Profile`** | An annotation on a bean meaning "only create this one when that profile is active". |
| **Secret** | A value that would cause harm if published: a database password, an API key, a signing key. |
| **`.gitignore`** | A file listing paths git must never track. Your defence against committing a secret. |

## The task's subtasks

- **S1 — Two profile files.** Create `application-local.yml` and `application-test.yml`, each with a different `spring.datasource.url`.
- **S2 — Run twice, no rebuild.** `SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`, then the same with `test`. Confirm a different database from the startup log.
- **S3 — Read the startup line.** `The following 1 profile is active: "local"`.
- **S4 — Gateway swap.** `FakePaymentGateway` with `@Profile("local")`, `HttpPaymentGateway` with `@Profile("!local")`. Confirm which one is wired under each profile.
- **S5 — Why the swap matters.** No real card charges from a laptop.
- **S6 — Password from the environment.** `password: ${DB_PASSWORD}`, no default. A missing password stops startup.
- **S7 — `.gitignore` first.** Add `.env`, `*.pem`, `application-local-secrets.yml` before such files exist.
- **S8 — Break it on purpose.** A profile with no datasource URL. Read the "Description / Action" block.
- **S9 — The test trap.** Tests also pick up profiles. A test under `local` would write to the dev database.
- **S10 — Ask AI.** Three ways a password gets committed by accident, and the first five minutes after.

## Roadmap for this task — 12 small lessons

| # | Lesson | Serves |
| --- | --- | --- |
| 1 | What a profile is, and the problem it solves | foundation for S2, S3 |
| 2 | Turning a profile on, and reading the startup line | S3 |
| 3 | Profile-specific files | S1 |
| 4 | Hands-on: one jar, two databases | S1, S2 |
| 5 | `@Profile` on a bean | S5 |
| 6 | Hands-on: the payment gateway swap | S4 |
| 7 | What a secret is, and why git never forgets | foundation for S6, S7 |
| 8 | Hands-on: password from an environment variable | S6 |
| 9 | Hands-on: `.gitignore` before the secret exists | S7 |
| 10 | Break it on purpose: the failure analyser | S8 |
| 11 | The test trap | S9 |
| 12 | Ask-AI exercise and checkpoint | S10 |

---

## Lesson 1 — What a profile is, and the problem it solves (foundation for S2, S3)

### Definition

A **profile** is a name for one way of running your app. Common names are `local`,
`test`, and `prod`. You choose the name when the app starts. You do not rebuild
anything.

### Why it is needed

Your app must run in several **environments**. An environment is a place where the app
runs. Your laptop is one environment. The test server is another. **Production** — the
real system with real users — is a third.

Each environment needs different settings. Your laptop talks to a Postgres database in
Docker. Tests need a throwaway database. Production talks to the real database.
Payments differ too: your laptop must use a fake payment gateway, and production must
use the real one.

Without profiles there are two bad options:

- **Option A: edit settings and rebuild per environment.** Slow and error-prone. Worse:
  the jar you tested is not the jar you ship.
- **Option B: three copies of the code, one per environment.** Every fix made three
  times. The copies drift apart.

Profiles give the third option. Build **one jar**. At start time, say one name. The app
reads the settings for that name. Same jar everywhere. This is the bold goal of the
task: **one artifact, two environments, zero code changes.** "Artifact" means the built
jar file.

### Key points

- A profile is only a **name**. A label.
- You pick the name at start time, from outside the jar.
- A profile **by itself changes nothing**. It is a wall switch. Something must be wired
  to it: a settings file, or a bean. Those reactions are Lessons 3 and 5.
- `default` is the built-in profile you get when you set nothing.

### How it works

1. When the app starts, Spring Boot checks which profile names are switched on.
2. It prints those names in the startup log, near the top. One line.
3. Settings files and beans can react to the active names. Nothing else happens on its
   own.

### The example, run for real

The smallest possible app. One class. No settings file at all.

`scratch-app/src/main/java/com/zenith/payments/ProfilesApplication.java`:

```java
package com.zenith.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProfilesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfilesApplication.class, args);
    }
}
```

Built once with `mvn package`. Then two runs of the same jar.

**Run 1 — plain start:**

```bash
java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar
```

Real output (banner removed):

```text
INFO 41952 --- [main] c.zenith.payments.ProfilesApplication : Starting ProfilesApplication v0.0.1-SNAPSHOT using Java 21.0.11 with PID 41952
INFO 41952 --- [main] c.zenith.payments.ProfilesApplication : No active profile set, falling back to 1 default profile: "default"
INFO 41952 --- [main] c.zenith.payments.ProfilesApplication : Started ProfilesApplication in 0.358 seconds (process running for 0.56)
```

**Run 2 — same jar, profile `local` switched on:**

```bash
SPRING_PROFILES_ACTIVE=local java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar
```

The first word sets an **environment variable**: a named value the operating system
hands to a program at start. Details of this mechanism are Lesson 2.

Real output (banner removed):

```text
INFO 41953 --- [main] c.zenith.payments.ProfilesApplication : Starting ProfilesApplication v0.0.1-SNAPSHOT using Java 21.0.11 with PID 41953
INFO 41953 --- [main] c.zenith.payments.ProfilesApplication : The following 1 profile is active: "local"
INFO 41953 --- [main] c.zenith.payments.ProfilesApplication : Started ProfilesApplication in 0.372 seconds (process running for 0.563)
```

### Walkthrough

- **Run 1, middle line:** `No active profile set, falling back to 1 default profile:
  "default"`. Nothing was switched on. Spring falls back to the built-in profile named
  `default`. This is the exact line from the first startup log you ever read (F1.4).
- **Run 2, middle line:** `The following 1 profile is active: "local"`. The name
  `local` is on. Spring confirms it.
- **Everything else is identical.** No setting changed. Nothing in this app reacts to
  the name `local` yet: no `application-local.yml`, no `@Profile` bean. The switch is
  on, but nothing is wired to it. Proof of the key idea: a profile alone is only a
  name.

### Terms in this lesson

| Term | Meaning |
| --- | --- |
| Profile | A name for one way of running the app, chosen at start time. |
| Environment | A place where the app runs: laptop, test server, production. |
| Production | The real system that serves real users. |
| Artifact | The built file you ship. Here: the jar. |
| Environment variable | A named value the operating system hands a program at start. |
| Default profile | The profile named `default`, active when nothing is set. |

### Where it is used

- Developer laptop: `local`.
- CI server running tests: `test`.
- Kubernetes deployment: sets `SPRING_PROFILES_ACTIVE=prod` on the container.
- Same jar in all three places. Phase 13 (Docker, Kubernetes, CI/CD) depends on this.

### Common mistakes

- **Expecting the profile alone to change settings.** It does not. Files and beans must
  react to the name.
- **Rebuilding per environment anyway.** Then you ship a jar nobody tested. The point
  is one build.
- **Thinking profile names are magic words.** `local`, `test`, `prod` are only popular
  choices. Any name works. Only `default` is special: it is what you get when you set
  nothing.

### Link to the task

Foundation for S2 (*one artifact, two environments, zero code changes*) and S3 (*read
the startup line*). Both startup-line forms have now been seen for real.

### Do it yourself (second laptop)

**Setup (one-time, if missing):** Java 21 and Maven.

```bash
brew install openjdk@21 maven
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk
java -version   # expect: openjdk version "21..."
mvn -version    # expect: Apache Maven 3.x
```

1. Make the folders: `F6.5-profiles-and-secrets/scratch-app/src/main/java/com/zenith/payments/`.
2. Create `scratch-app/pom.xml`:

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
    <artifactId>profiles-scratch</artifactId>
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

3. Create `ProfilesApplication.java` in the package folder, exactly as shown above.
4. From `scratch-app/`: `mvn package` (first run downloads a lot; needs internet).
5. Run 1: `java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` →
   expect `No active profile set, falling back to 1 default profile: "default"`.
6. Run 2: `SPRING_PROFILES_ACTIVE=local java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` →
   expect `The following 1 profile is active: "local"`.
7. Failure check: run `java -jar profiles-scratch-0.0.1-SNAPSHOT.jar` from the wrong
   folder → `Error: Unable to access jarfile profiles-scratch-0.0.1-SNAPSHOT.jar`.
   The path to the jar must be right; `target/` is where Maven puts it.

**Done when:** both startup lines seen on your own screen, and you can say in one
sentence why nothing else differed between the two runs.

### Summary for revision

- A profile is a **name** for one way of running the app. Chosen at start. No rebuild.
- Why: one jar must serve laptop, tests, and production, each with different settings.
- A profile alone changes **nothing**. Files and beans must react to it.
- No profile set → `default` is active. Log: `No active profile set, falling back to 1
  default profile: "default"`.
- Profile set → log: `The following 1 profile is active: "local"`.
- Goal of the task: one artifact, two environments, zero code changes.

### Check questions, with answers

**Q1.** A team builds `shop.jar`. It must talk to a throwaway database on the test
server, and to the real database in production. Plan A: keep two copies of the project,
different settings inside each, build two jars. Plan B: build one jar, pick a profile
name like `test` or `prod` at start. Which plan is better? Give two reasons.

**Answer.** Plan B. Reasons (any two): (1) The jar you tested is the jar you ship. With
two builds, production runs a jar that never went through testing. (2) One codebase.
With two copies, every fix must be made twice, and the copies drift apart. (3) No
rebuild per environment, so releasing is faster and there is less room for error.

**Q2.** An app starts and the log shows: `The following 2 profiles are active: "local",
"debug"`. (a) What does this line tell you? (b) The app has no settings files and no
`@Profile` beans at all — did switching on these two profiles change how the app
behaves?

**Answer.** (a) Two profile names, `local` and `debug`, were switched on at start, and
Spring confirms it read them. More than one profile can be active at once. (b) No.
A profile alone is only a name. Nothing in this app reacts to either name, so the run
behaves exactly like a run with no profiles. Only the log line differs.

### Your doubts

None raised for this lesson.

---

## Lesson 2 — Turning a profile on, and reading the startup line (S3)

### Definition

`SPRING_PROFILES_ACTIVE` is the environment variable that tells Spring Boot which
profiles to switch on. There is a second form: the command-line flag
`--spring.profiles.active=...`. Both forms set the same Spring setting, whose name is
`spring.profiles.active`.

### Why it is needed

The profile choice must live **outside** the jar. If the choice sat inside the jar,
changing it would mean rebuilding — the exact problem profiles exist to solve. So the
choice comes from outside, at start time. Whoever starts the app decides: you in a
terminal, the CI server, Docker, Kubernetes. The jar stays neutral.

### How it works

Recap of how Spring Boot reads settings (profiles ride on it):

1. A **setting** is a name and a value. Example: `spring.profiles.active` = `local`.
2. Spring Boot reads settings from several **sources**: settings files inside the jar,
   environment variables, command-line flags.
3. Same name from two sources → the stronger source wins. Command-line flag beats
   environment variable. Environment variable beats files. This order is called
   **precedence**.

The profile part:

4. `spring.profiles.active` is an **ordinary setting**. It can arrive from any source.
   Precedence decides conflicts, like for any other setting.
5. Environment variable names cannot contain dots. Naming rule: replace dots with
   underscores, make it uppercase. `spring.profiles.active` → `SPRING_PROFILES_ACTIVE`.
6. The value is one name, or several separated by commas: `local,debug`.
7. At startup Spring reads the setting, switches those names on, prints the line.

### The example, run for real

App: one class (`ProfilesApplication`, shown in Lesson 1), no settings file. Built
once. Six runs of the same jar; only the key log line shown.

| Run | Command | Key line |
| --- | --- | --- |
| A — flag | `java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar --spring.profiles.active=local` | `The following 1 profile is active: "local"` |
| B — env var | `SPRING_PROFILES_ACTIVE=test java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` | `The following 1 profile is active: "test"` |
| C — two at once | `SPRING_PROFILES_ACTIVE=local,debug java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` | `The following 2 profiles are active: "local", "debug"` |
| D — env var `test` vs flag `local` | `SPRING_PROFILES_ACTIVE=test java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar --spring.profiles.active=local` | `The following 1 profile is active: "local"` |
| E — misspelled `SPRING_PROFILE_ACTIVE` | `SPRING_PROFILE_ACTIVE=test java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` | `No active profile set, falling back to 1 default profile: "default"` |
| F — forgotten export | `export SPRING_PROFILES_ACTIVE=test` then plain `java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar` | `The following 1 profile is active: "test"` |

### Walkthrough

- **A.** The flag sets `spring.profiles.active=local`. Spring switches `local` on.
- **B.** The variable name follows the naming rule (dots → underscores, uppercase).
  Same setting, different source.
- **C.** Commas mean several names. The line turns plural: "2 profiles **are** active".
- **D.** Two sources, same setting. Command-line flag beats environment variable, so
  `local` wins. The log is the referee — trust the line, not your memory.
- **E.** The dangerous one. A misspelled variable name means nothing to Spring. Unknown
  variables are ignored. **No error.** The app quietly runs with `default`. The startup
  line is the only witness.
- **F.** `export` glues the variable to the terminal session. Every later run in that
  terminal inherits it, even with no prefix and no flag. Check with
  `echo $SPRING_PROFILES_ACTIVE`. Clean with `unset SPRING_PROFILES_ACTIVE`.

### Terms in this lesson

| Term | Meaning |
| --- | --- |
| Setting | A name and a value Spring reads, e.g. `spring.profiles.active=local`. |
| Source | A place settings come from: file, environment variable, command-line flag. |
| Precedence | The order that decides which source wins: flag > env var > files. |
| Command-line flag | `--name=value` after the jar name, e.g. `--spring.profiles.active=local`. |
| `export` | Shell command that makes a variable stick to the whole terminal session. |
| `unset` | Shell command that removes such a variable. |

### Where it is used

- Terminal one-off: `SPRING_PROFILES_ACTIVE=local java -jar ...`.
- IntelliJ: run configuration has fields for env vars and active profiles.
- Docker: `docker run -e SPRING_PROFILES_ACTIVE=prod myapp`.
- Kubernetes: an `env:` entry in the deployment file (Phase 13 does this).
- CI servers: export it before running tests.

### Common mistakes

- **Misspelling the variable.** Silent `default` run, no error. Read the startup line,
  every start.
- **A forgotten `export`.** Later runs mysteriously have a profile on. `echo`, then
  `unset`.
- **Baking the choice into the jar** (`spring.profiles.active` in the main settings
  file). Works, but the artifact now has a favourite environment. Habit: the
  environment chooses, not the artifact.
- **Expecting an error for an unknown profile name.** Not an error. A profile nothing
  reacts to gives the plain app.

### Link to the task

S3 done in understanding: all three shapes of the line (none / one / many) read for
real. Every word of S2's command `SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`
now has a meaning.

### Do it yourself (second laptop)

**Setup:** Lesson 1's "Do it yourself" done; the jar exists at
`scratch-app/target/profiles-scratch-0.0.1-SNAPSHOT.jar`.

1. `java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`
   → expect `...1 profile is active: "local"`.
2. `SPRING_PROFILES_ACTIVE=local,debug java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar`
   → expect `...2 profiles are active: "local", "debug"`.
3. `SPRING_PROFILES_ACTIVE=test java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`
   → expect `"local"` (flag beats variable).
4. Failure 1 (misspell): `SPRING_PROFILE_ACTIVE=test java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar`
   → expect the `No active profile set...` line and **no error**.
5. Failure 2 (sticky export): `export SPRING_PROFILES_ACTIVE=test`, plain run → expect
   `"test"`. Then `echo $SPRING_PROFILES_ACTIVE`, `unset SPRING_PROFILES_ACTIVE`,
   plain run again → back to the `default` line.

**Done when:** you predicted each line before running, and all five matched.

### Summary for revision

- Two forms, one setting: `SPRING_PROFILES_ACTIVE=x` (env var) and
  `--spring.profiles.active=x` (flag) both set `spring.profiles.active`.
- Naming rule for env vars: dots → underscores, uppercase.
- Several profiles: commas. Log goes plural.
- Precedence: flag > env var > files. The log line is the referee.
- Misspelled variable = silent `default` run. No error. Read the line.
- `export` sticks for the whole terminal session; `unset` removes it.
- The environment chooses the profile. The jar stays neutral.

### Check questions, with answers

**Q1.** A team runs this exact command:
`SPRING_PROFILES_ACTIVE=prod java -jar shop.jar --spring.profiles.active=local`.
Which profile ends up active, and why? Name the rule that decides.

**Answer.** `local`. Both sources set the same setting, `spring.profiles.active`. The
rule is precedence: a command-line flag is a stronger source than an environment
variable, so the flag's value wins. The startup line would read:
`The following 1 profile is active: "local"`.

**Q2.** A developer runs `SPRING_PROFILE_ACTIVE=test java -jar shop.jar` — the variable
name says `PROFILE`, not `PROFILES`. (a) What will the startup profile line say?
(b) Why does Spring print no error or warning?

**Answer.** (a) `No active profile set, falling back to 1 default profile: "default"`.
(b) Spring looks for specific variable names. A name it does not know is simply
ignored — Spring cannot tell a misspelling from any other unrelated variable the
operating system passes along. From Spring's point of view, nobody asked for a profile.
The startup line is the only place the mistake shows.

### Your doubts

None raised for Lesson 2.

---

## Lesson 3 — Profile-specific files (S1)

### Definition

A **profile-specific file** is a settings file named `application-<profile>.yml`,
e.g. `application-local.yml`. It loads only when that profile is active. Its values
then override the base file `application.yml`.

### Why it is needed

The switch from Lesson 2 needs something wired to it. Files are the first reactor.
Most settings are the same everywhere; a few differ per environment. So: one shared
base file, plus one small file of differences per environment. Without this you would
copy the whole configuration per environment, and the copies would drift apart.

### How it works

1. The base file `application.yml` sits in `src/main/resources/`. Maven packs it
   **inside the jar**.
2. A profile-specific file sits in the same folder. Naming rule: `application-` +
   profile name + `.yml`.
3. Profile `local` active → Spring loads **both**: the base, then
   `application-local.yml` on top.
4. Override is **per setting, not per file**. The profile file wins only for the names
   it declares. Everything else falls through to the base.
5. Active profile with no file → silently skipped. Not an error.
6. The files are inside the jar → editing them needs `mvn package` again.
7. Strength ladder so far: command-line flag → environment variable →
   `application-<profile>.yml` → `application.yml`. Left beats right.
8. `.properties` files follow the same rule (`application-local.properties`). This task
   uses `.yml`.

### The example, run for real

`src/main/resources/application.yml` (yml: indentation = nesting; this defines
`zenith.message` and `zenith.owner`):

```yaml
zenith:
  message: settings came from application.yml
  owner: team-zenith
```

`src/main/resources/application-local.yml` — only one setting:

```yaml
zenith:
  message: settings came from application-local.yml
```

`SettingsPrinter.java` — reads the two settings via `Environment` (Spring's phone book
of all settings), injected through the constructor:

```java
package com.zenith.payments;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SettingsPrinter {

    private final Environment environment;

    public SettingsPrinter(Environment environment) {
        this.environment = environment;
    }

    public String messageLine() {
        return "zenith.message = " + environment.getProperty("zenith.message");
    }

    public String ownerLine() {
        return "zenith.owner   = " + environment.getProperty("zenith.owner");
    }
}
```

`ProfilesApplication.java`:

```java
package com.zenith.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProfilesApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(ProfilesApplication.class, args);
        var printer = context.getBean(SettingsPrinter.class);
        System.out.println(printer.messageLine());
        System.out.println(printer.ownerLine());
    }
}
```

One `mvn package`, four runs. Three lines shown per run (profile line + the two
printed values).

**Run 1 — plain** (`java -jar target/profiles-scratch-0.0.1-SNAPSHOT.jar`):

```text
No active profile set, falling back to 1 default profile: "default"
zenith.message = settings came from application.yml
zenith.owner   = team-zenith
```

**Run 2 — `SPRING_PROFILES_ACTIVE=local`:**

```text
The following 1 profile is active: "local"
zenith.message = settings came from application-local.yml
zenith.owner   = team-zenith
```

**Run 3 — `SPRING_PROFILES_ACTIVE=test`, no `application-test.yml` exists:**

```text
The following 1 profile is active: "test"
zenith.message = settings came from application.yml
zenith.owner   = team-zenith
```

**Run 4 — `SPRING_PROFILES_ACTIVE=local` plus flag
`--zenith.message='settings came from the command line'`:**

```text
The following 1 profile is active: "local"
zenith.message = settings came from the command line
zenith.owner   = team-zenith
```

### Walkthrough

- **Run 1.** No profile → only the base loads.
- **Run 2.** The proof. `zenith.message` is declared in the profile file → it wins.
  `zenith.owner` is not → falls through to the base. Per-setting merge.
- **Run 3.** Missing profile file → silent. Looks exactly like Run 1 except the profile
  line. A misnamed file looks the same.
- **Run 4.** Three sources compete for `zenith.message`; the flag is strongest. The
  ladder in action: flag → profile file → base.

### Where it is used

Exactly subtask S1: `application-local.yml` and `application-test.yml` with different
database URLs. Real projects: shared base + one small file per environment. Warning for
later: these files are packed into the jar, so passwords never belong in any of them.

### Common mistakes

- **Thinking the profile file replaces the base file.** It merges, setting by setting.
- **Typo in the file name.** `application-locl.yml` is silently ignored. Looks like
  Run 3. No error.
- **Editing the yml, running the old jar.** The files live inside the jar. Edit →
  `mvn package` → run.
- **Wrong folder.** Must sit in `src/main/resources/`, next to `application.yml`,
  or Maven never packs it.

### Link to the task

S1 fully understood: naming rule, per-setting override, silent skip, rebuild after
edit. Lesson 4 performs S1 + S2 for real against the two Postgres databases `zenith`
and `zenith_lab`.

### Do it yourself (second laptop)

**Setup:** Lessons 1–2 done; the scratch app builds.

1. Create the two yml files and `SettingsPrinter.java`, and replace
   `ProfilesApplication.java`, exactly as above.
2. `mvn package`.
3. Run plain → `default` line, message from base, owner `team-zenith`.
4. Run with `SPRING_PROFILES_ACTIVE=local` → message from `application-local.yml`,
   owner still `team-zenith`.
5. Run with `SPRING_PROFILES_ACTIVE=test` → profile `test`, both values from base,
   **no error**.
6. Run with `SPRING_PROFILES_ACTIVE=local` and
   `--zenith.message='settings came from the command line'` → the flag's text wins.
7. Failure check: rename `application-local.yml` → `application-locl.yml`,
   `mvn package`, repeat step 4 → base values, silently. Rename back, rebuild.

**Done when:** before each run you can say which file each value comes from, and the
misnamed file stayed silent on your own screen.

### Summary for revision

- Naming rule: `application-<profile>.yml`, same folder as `application.yml`.
- Loads only when that profile is active. On top of the base.
- Merge is per setting. Profile file wins where it speaks; the rest falls through.
- Missing or misnamed file: silent. No error.
- Files are inside the jar: edit → rebuild → run.
- Ladder: flag → env var → profile file → base file.

### Check questions, with answers

**Q1.** Inside the jar: `application.yml` has `app.db-url: jdbc:h2:mem:dev` and
`app.pool-size: 5`; `application-prod.yml` has
`app.db-url: jdbc:postgresql://prod-server/shop`. Start with
`SPRING_PROFILES_ACTIVE=prod`. Final values of `app.db-url` and `app.pool-size`?

**Answer.** `app.db-url` = `jdbc:postgresql://prod-server/shop` — the profile file
declares it, and a profile file beats the base. `app.pool-size` = `5` — the profile
file says nothing about it, so it falls through to the base.

**Q2.** The log says `The following 1 profile is active: "local"`, yet every printed
value comes from `application.yml`. The developer swears `application-local.yml`
exists somewhere. Name two mistakes that produce exactly this.

**Answer.** Any two of: (1) the file name has a typo (`application-locl.yml`) —
ignored silently; (2) the file is not under `src/main/resources/`, so Maven never
packed it into the jar; (3) the file was added or edited, but the jar was not rebuilt —
the run used yesterday's jar.

### Your doubts

None raised for Lesson 3.

---

## Lesson 4 — Hands-on: one jar, two databases (S1 + S2)

### Goal

Give the scratch app a real database connection. One build, two runs: under `local` it
uses database `zenith`, under `test` it uses `zenith_lab`. Both live in the Postgres
container `pg` from Module F5.

### How the pieces fit

- The switch: `SPRING_PROFILES_ACTIVE` picks the profile name.
- The reactor: `application-<profile>.yml` loads only for that name.
- The reader: the startup line confirms which name is on.
- The varied setting: `spring.datasource.url` — the database address.

Recaps: a **DataSource** is the app's door to the database; Spring Boot builds it from
the `spring.datasource.*` settings once the JDBC library and driver are on board. The
door is a **connection pool** named Hikari — a few opened connections kept ready.
**JdbcClient** is Spring's small helper for running SQL.

### Design decisions

- Username and password go in the base `application.yml` — shared. (The password in a
  file is wrong on purpose; a later lesson moves it out.)
- The URL goes **only** in the profile files. The base has none. So the profile alone
  decides the database. Side effect: a plain run with no profile has no URL and fails
  at startup — that failure is its own later lesson. Until then: always start with a
  profile.
- A probe prints two lines: the configured URL (intent) and the answer to
  `select current_database()` (reality — the database naming itself).

### The files

`pom.xml`, add inside `<dependencies>`:

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

`src/main/resources/application.yml` — no URL here:

```yaml
zenith:
  message: settings came from application.yml
  owner: team-zenith

spring:
  datasource:
    username: postgres
    password: dev   # wrong place for a password - moved out in a later lesson
```

`src/main/resources/application-local.yml`:

```yaml
zenith:
  message: settings came from application-local.yml

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
```

`src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith_lab
```

`DatabaseProbe.java`:

```java
package com.zenith.payments;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DatabaseProbe {

    private final JdbcClient jdbc;
    private final Environment environment;

    public DatabaseProbe(JdbcClient jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    public String configuredUrlLine() {
        return "configured url     = " + environment.getProperty("spring.datasource.url");
    }

    public String actualDatabaseLine() {
        String name = jdbc.sql("select current_database()").query(String.class).single();
        return "database says I am = " + name;
    }
}
```

`ProfilesApplication.java`:

```java
package com.zenith.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProfilesApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(ProfilesApplication.class, args);

        var printer = context.getBean(SettingsPrinter.class);
        System.out.println(printer.messageLine());
        System.out.println(printer.ownerLine());

        var probe = context.getBean(DatabaseProbe.class);
        System.out.println(probe.configuredUrlLine());
        System.out.println(probe.actualDatabaseLine());
    }
}
```

### Build once, run twice — real output

Jar built once with `mvn package`. Then, with no rebuild:

**Run 1 — `SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`:**

```text
The following 1 profile is active: "local"
zenith.message = settings came from application-local.yml
zenith.owner   = team-zenith
configured url     = jdbc:postgresql://localhost:5432/zenith
HikariPool-1 - Starting...
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@6e33fcae
HikariPool-1 - Start completed.
database says I am = zenith
```

**Run 2 — `SPRING_PROFILES_ACTIVE=test java -jar target/*.jar`:**

```text
The following 1 profile is active: "test"
zenith.message = settings came from application.yml
zenith.owner   = team-zenith
configured url     = jdbc:postgresql://localhost:5432/zenith_lab
HikariPool-1 - Starting...
HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@1a87b51
HikariPool-1 - Start completed.
database says I am = zenith_lab
```

**One artifact, two environments, zero code changes — S2 achieved.**

### Walkthrough

- `local` active → base + local file; the URL exists only there → `zenith`. Username
  and password fall through from the base.
- Hikari's `Added connection org.postgresql.jdbc.PgConnection@...` = a real network
  connection was opened. The pool starts when the first query runs — hence its lines
  appear just before the probe's answer.
- `database says I am = ...` is the unfakeable proof: the database answered
  `select current_database()` itself.
- Under `test`, note `zenith.message` fell back to the base — `application-test.yml`
  does not declare it. Per-setting merge again.

### A real failure, read calmly

Wrong port on purpose (nothing listens at 5999):

```bash
SPRING_PROFILES_ACTIVE=local java -jar target/*.jar --spring.datasource.url=jdbc:postgresql://localhost:5999/zenith
```

```text
configured url     = jdbc:postgresql://localhost:5999/zenith
HikariPool-1 - Starting...
Caused by: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
Caused by: org.postgresql.util.PSQLException: Connection to localhost:5999 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

Readings: the flag beat the profile file (outside sources win); "Connection refused" =
nothing answered at that address → check Postgres is running, host right, port right.
"Postmaster" is Postgres's old name for its main process.

### Common mistakes

- **Running plain.** The base has no URL on purpose → plain start fails. Always start
  with a profile for now.
- **Editing yml, running the old jar.** Edit → `mvn package` → run.
- **Trusting the configured URL as proof.** It proves intent. The database's own answer
  proves reality.
- **Postgres not running.** Same "Connection refused". `docker start pg` first.

### Link to the task

S1 done (two files, two URLs). S2 done (same jar, two databases, switched by one
variable). Checkpoint question 2 is now answerable from experience.

### Do it yourself (second laptop)

**Setup — Postgres with two databases (one-time), Docker installed and running:**

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
docker exec pg psql -U postgres -c 'create database zenith;'
docker exec pg psql -U postgres -c 'create database zenith_lab;'
docker exec pg psql -U postgres -c '\l'
```

If the container exists but is stopped: `docker start pg`.

1. Add the two pom dependencies, exactly as above.
2. Replace `application.yml`, `application-local.yml`; create `application-test.yml`.
3. Create `DatabaseProbe.java`; replace `ProfilesApplication.java`.
4. Build **once**: `mvn package`.
5. `SPRING_PROFILES_ACTIVE=local java -jar target/*.jar` →
   `database says I am = zenith`.
6. `SPRING_PROFILES_ACTIVE=test java -jar target/*.jar` →
   `database says I am = zenith_lab`.
7. Failure check: add `--spring.datasource.url=jdbc:postgresql://localhost:5999/zenith`
   → `Connection to localhost:5999 refused...`.

**Done when:** two different database names from one build, and you can name the two
proofs of a real connection (Hikari's `Added connection`, the database's own answer).

### Summary for revision

- URLs live in profile files; shared settings in the base; the base holds no URL.
- One `mvn package`, then `SPRING_PROFILES_ACTIVE=local|test java -jar target/*.jar`.
- Proof of connection: Hikari `Added connection` + `select current_database()`.
- Flag still beats profile file. "Connection refused" = running? host? port?
- Password still in a file — flagged, to be removed in a later lesson.

### Check questions, with answers

**Q1.** Jar contains: `application.yml` (username `postgres`, password `dev`, no URL),
`application-local.yml` (URL `...5432/zenith`), `application-test.yml`
(URL `...5432/zenith_lab`). Run: `SPRING_PROFILES_ACTIVE=test java -jar app.jar`.
(a) Which files load? (b) Final URL, username, password? (c) Source of each?

**Answer.** (a) `application.yml` and `application-test.yml`. The local file stays
unloaded. (b+c) URL = `jdbc:postgresql://localhost:5432/zenith_lab` from
`application-test.yml` (only loaded file that declares it). Username = `postgres` and
password = `dev`, both from `application.yml` — the test file says nothing about them,
so they fall through to the base.

**Q2.** A teammate: "To point the app at a new database, edit the URL in the code,
rebuild, deploy the new jar." Give the better way, and one risk of the rebuild way.

**Answer.** Better: keep one URL per environment in profile-specific files (or pass it
from outside), and start the **same** jar with a different `SPRING_PROFILES_ACTIVE`.
No rebuild. Risk of rebuilding: the jar that reaches production is not the jar that was
tested — an untested artifact ships. (Also slower, and hand-edits in code invite
mistakes.)

### Your doubts

None raised for Lesson 4.

---

## Lesson 5 — `@Profile` on a bean: swapping behaviour, not values (S5, prepares S4)

### Definition

`@Profile` sits on a bean class next to `@Component`. `@Profile("local")`: create this
bean only when profile `local` is active. `@Profile("!local")`: create it only when
`local` is **not** active. The `!` reads "not".

### Why it is needed

Profile files change **values** (URL, timeout). Some environment differences are
**behaviour** — different code must run. Payments: production must move real money;
a laptop must never. Phase 5's checkout flow will run `charge` hundreds of times while
debugging — those must not be real charges. No value expresses that. You need two
objects behind one promise. The promise is an interface — a **port**: the list of
methods the app needs from the outside world. `@Profile` picks which implementation
exists per environment, automatically.

### The pain first

Every `@Component` class becomes a bean. Asking for a bean by interface requires
**exactly one** match. Both gateways as plain `@Component`, no `@Profile` — real run:

```text
Caused by: org.springframework.beans.factory.NoUniqueBeanDefinitionException: No qualifying bean of type 'com.zenith.payments.PaymentGateway' available: expected single matching bean but found 2: fakePaymentGateway,httpPaymentGateway
```

Decoded: one slot, two candidates, Spring refuses to guess. (In our scratch app the
error fires when `main` asks; with constructor injection it stops startup itself.)

### How `@Profile` fixes it

1. Startup: Spring reads the set of active profile names.
2. Per bean: check its `@Profile` condition against that set.
3. No match → the bean is **never created**. The class still ships in the jar; no
   object is made.
4. The pair `"local"` / `"!local"` covers every run with exactly one match.

### The example, run for real

`PaymentGateway.java`:

```java
package com.zenith.payments;

public interface PaymentGateway {

    String charge(int amountInRupees);
}
```

`FakePaymentGateway.java`:

```java
package com.zenith.payments;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public String charge(int amountInRupees) {
        return "FAKE charge of Rs." + amountInRupees + " approved. No real money moved.";
    }
}
```

`HttpPaymentGateway.java`:

```java
package com.zenith.payments;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class HttpPaymentGateway implements PaymentGateway {

    @Override
    public String charge(int amountInRupees) {
        return "HTTP gateway would now POST Rs." + amountInRupees + " to the real payment provider.";
    }
}
```

In `main`, after the database lines:

```java
var gateway = context.getBean(PaymentGateway.class);
System.out.println("wired gateway      = " + gateway.getClass().getSimpleName());
```

Three runs, real output (two key lines each):

| Run | Lines |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=local` | `The following 1 profile is active: "local"` → `wired gateway = FakePaymentGateway` |
| `SPRING_PROFILES_ACTIVE=test` | `The following 1 profile is active: "test"` → `wired gateway = HttpPaymentGateway` |
| `SPRING_PROFILES_ACTIVE=local,debug` | `The following 2 profiles are active: "local", "debug"` → `wired gateway = FakePaymentGateway` |

### Walkthrough

- Active {`local`}: `"local"` matches → fake created; `"!local"` false → no HTTP bean.
- Active {`test`}: `"local"` no → no fake; `"!local"` true → HTTP bean.
- Active {`local`,`debug`}: the condition looks at the whole set; `local` present →
  fake. Extra profiles do not confuse the pair.
- What changed across runs is **which object exists** — different code behind the same
  interface, not a different value.

### Where it is used

- Payment gateways: fake on laptops, real elsewhere (subtask S4).
- Email senders: log-only locally, real SMTP in production.
- Storage: local disk locally, cloud storage in production.

### Common mistakes

- **Annotating only one side.** Fake has `@Profile("local")`, real has nothing → under
  `local` both exist → "found 2". Annotate both.
- **Conditions that miss cases.** `"local"` vs `"prod"`, run under `test` → zero beans →
  the mirror error (no qualifying bean). `x` / `!x` always yields exactly one.
- **Thinking `@Profile` removes code from the jar.** The class ships; only the object is
  never created. Shipping is not hiding.
- **Forgetting the default case.** No profile set → active set is {`default`} →
  `"!local"` matches → the **real** gateway. Safe for production; your laptop must say
  `local` every time.

### Link to the task

S5 answered: the fake gateway exists on laptops automatically, so four hundred checkout
test runs move no money. S4's classes are written and verified. Checkpoint question 4:
a profile-specific value changes a setting; `@Profile` changes **which code runs**.

### Do it yourself (second laptop)

Nothing to type this lesson. Lesson 6's "Do it yourself" builds these files and the
checkout flow around them.

### Summary for revision

- `@Profile("x")` = create the bean only when `x` is active. `"!x"` = only when not.
- Non-matching bean: never created, though its class ships in the jar.
- The `x` / `!x` pair guarantees exactly one implementation per run.
- No profile → {`default`} → `"!local"` matches → real implementation. Laptops must say
  `local`.
- Values vs behaviour: profile files change settings; `@Profile` changes code.

### Check questions, with answers

**Q1.** Interface `SmsSender`; beans `FakeSms` (`@Profile("local")`) and `RealSms`
(`@Profile("!local")`). Which bean exists under: (a) `SPRING_PROFILES_ACTIVE=prod`,
(b) `SPRING_PROFILES_ACTIVE=local,debug`, (c) no profile at all?

**Answer.** (a) Active {prod}: `local` absent → `RealSms` only. (b) Active
{local,debug}: `local` present → `FakeSms` only. (c) Active {default}: `local` absent →
`RealSms` only. Note (c): a plain start wires the **real** sender — safe in production,
dangerous on a laptop. The laptop must say `local`.

**Q2.** Two beans `CashPriceCalculator` and `CardPriceCalculator`, both `@Component`,
both implement `PriceCalculator`, no `@Profile`. `BillingService` takes
`PriceCalculator` in its constructor. The app starts — what happens, and what is the
fix if cash should apply locally and card everywhere else?

**Answer.** Startup fails. Spring must inject exactly one `PriceCalculator` into the
constructor but finds two candidates; the error says it expected a single matching bean
but found 2, and names them (`cardPriceCalculator`, `cashPriceCalculator`). Fix from
this lesson: `@Profile("local")` on cash, `@Profile("!local")` on card — every run then
has exactly one. (Related tools from F6.3: `@Primary`/`@Qualifier` answer "choose this
one always"; `@Profile` answers "choose per environment".)

### Your doubts

None raised for Lesson 5.

---

## Lesson 6 — Hands-on: the payment gateway swap (S4)

### Goal

Put the gateway behind a real consumer (`CheckoutService`), watch one checkout call
behave differently under `local` and `test`, then break the wiring on purpose and read
the failure analyser block.

### The one idea

**The consumer stays ignorant.** `CheckoutService` asks for "a `PaymentGateway`"
through its constructor. Injection hands it whichever implementation exists in this
run; `@Profile` decided which one that is. The environment picks the behaviour; the
checkout code never mentions fake or real.

### The files

`CheckoutService.java`:

```java
package com.zenith.payments;

import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String checkout(int amountInRupees) {
        return "checkout: " + gateway.charge(amountInRupees);
    }
}
```

`ProfilesApplication.java`, final form:

```java
package com.zenith.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProfilesApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(ProfilesApplication.class, args);

        var printer = context.getBean(SettingsPrinter.class);
        System.out.println(printer.messageLine());
        System.out.println(printer.ownerLine());

        var probe = context.getBean(DatabaseProbe.class);
        System.out.println(probe.configuredUrlLine());
        System.out.println(probe.actualDatabaseLine());

        var gateway = context.getBean(PaymentGateway.class);
        System.out.println("wired gateway      = " + gateway.getClass().getSimpleName());

        var checkout = context.getBean(CheckoutService.class);
        System.out.println(checkout.checkout(499));
    }
}
```

### Build once, run twice — real output

Under `local`:

```text
The following 1 profile is active: "local"
wired gateway      = FakePaymentGateway
checkout: FAKE charge of Rs.499 approved. No real money moved.
```

Under `test`:

```text
The following 1 profile is active: "test"
wired gateway      = HttpPaymentGateway
checkout: HTTP gateway would now POST Rs.499 to the real payment provider.
```

`checkout.checkout(499)` is the same line of code, the jar the same bytes. Only the
object behind the interface changed. That is "swapped behaviour, not only settings".

### Break it on purpose — the failure analyser

`@Profile("!local")` removed from `HttpPaymentGateway`, rebuilt, started under `local`
(both beans now exist). Real output:

```text
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.payments.CheckoutService required a single bean, but 2 were found:
	- fakePaymentGateway: defined in URL [jar:nested:...!/com/zenith/payments/FakePaymentGateway.class]
	- httpPaymentGateway: defined in URL [jar:nested:...!/com/zenith/payments/HttpPaymentGateway.class]

Action:

Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed
```

This block comes from Spring Boot's **failure analyser**: Description = what went
wrong; Action = generic suggestions. Decode:

| Part | Tells you |
| --- | --- |
| `Parameter 0 of constructor in ...CheckoutService` | Where the need arose. |
| `required a single bean, but 2 were found` | One slot, two candidates. |
| The two bullet lines | The candidates, by bean name and class location. |
| Action | Suggestions — generic, not intent-aware. |

Judgment: `@Primary` pins one winner **everywhere** — wrong when the winner must depend
on the environment. The right fix was restoring `@Profile("!local")`. Restored, rebuilt,
healthy again.

### Common mistakes

- Reading only "2 were found" and not the bullet lines that name the beans.
- Obeying the Action blindly (`@Primary` would wire the real gateway on laptops too).
- Editing a class and running the old jar — `mvn package` first.

### Link to the task

**S4 complete**: two implementations, `@Profile("local")` / `@Profile("!local")`,
started under each profile, wiring confirmed. First taste of S8's skill: scroll to
Description / Action first.

### Do it yourself (second laptop)

**Setup:** Lessons 1–4 done; Postgres running (`docker start pg`).

1. Create `PaymentGateway.java`, `FakePaymentGateway.java`, `HttpPaymentGateway.java`
   (Lesson 5 notes) and `CheckoutService.java` above.
2. Replace `ProfilesApplication.java` with the final form above.
3. `mvn package`.
4. Run under `local` → `wired gateway = FakePaymentGateway`, `FAKE charge of Rs.499`.
5. Run under `test` → `wired gateway = HttpPaymentGateway`, `would now POST`.
6. Break: delete `@Profile("!local")` from `HttpPaymentGateway.java`, `mvn package`,
   run under `local` → `APPLICATION FAILED TO START`, `required a single bean, but 2
   were found`.
7. Restore the line, `mvn package`, run → healthy.

**Done when:** both gateways wired on your screen, the failure block read top to
bottom, and you can say why `@Primary` is the wrong fix here.

### Summary for revision

- Consumer injects the interface; `@Profile` picks the implementation; environment
  picks the behaviour.
- Same code line, different result per profile — behaviour swap, verified.
- Failure analyser block: Description (what + where + candidates), Action (generic
  ideas). Scroll to it first.
- `@Primary` = one winner always. `@Profile` pair = winner per environment.

### Check questions, with answers

**Q1.** A startup failure says: `Parameter 0 of constructor in com.shop.NotifierService
required a single bean, but 2 were found: emailNotifier..., smsNotifier...` and the
Action suggests `@Primary` or `@Qualifier`. (a) What two facts does the Description
give? (b) Email must be used in production only, SMS everywhere else — is `@Primary`
right?

**Answer.** (a) Where the need arose — the first constructor parameter of
`NotifierService` — and the exact candidates found: `emailNotifier` and `smsNotifier`,
with their class locations. (b) No. `@Primary` picks one winner in **every**
environment. The need is per-environment: `@Profile("prod")` on `EmailNotifier`,
`@Profile("!prod")` on `SmsNotifier`.

**Q2.** With `FakePaymentGateway` under `@Profile("local")` returning
`"FAKE charge of Rs." + amount + " approved. No real money moved."`,
`HttpPaymentGateway` under `@Profile("!local")` returning
`"HTTP gateway would now POST Rs." + amount + " to the real payment provider."`, and
`CheckoutService.checkout(amount)` returning `"checkout: " + gateway.charge(amount)`:
the app starts with `SPRING_PROFILES_ACTIVE=prod` and `checkout(250)` runs. Exact
returned string?

**Answer.** `checkout: HTTP gateway would now POST Rs.250 to the real payment
provider.` Under `prod`, `local` is not active, so only `HttpPaymentGateway` exists;
the constructor received it; `checkout` adds its prefix.

### Your doubts

None raised for Lesson 6.

---

## Lesson 7 — What a secret is, and why git never forgets (foundation for S6, S7)

### Definition

A **secret** is a value that causes harm if other people see it: a database password,
an API key, a signing key. A **tracked file** is a file git watches and stores;
everything ever committed is tracked history. To **rotate** a secret is to change it at
the source so the leaked value stops working.

### Why it is needed

The scratch app still has `password: dev` in `application.yml`. Before moving it, feel
why "in a file in the repo" is not an option. The task warns: credentials get committed
"just temporarily" — and git remembers forever. "Temporarily" does not exist in git.

### How git remembers

1. Every commit is a full **snapshot** of the files.
2. History is the chain of all snapshots; normal commits never edit old ones.
3. Deleting a line creates a **new** clean snapshot. The old one stays, one step back.
4. Every clone and pull carries the whole chain.
5. True removal = **rewriting history**: new ids for later commits, everyone re-clones —
   and it still does not help, because the value must be treated as stolen. The cure is
   rotation.

### The demo, run for real

Throwaway repo `git-never-forgets-demo`. Commit 1 — `application-prod.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod-db.zenith.internal:5432/zenith
    username: zenith_app
    password: SuperSecret123!
```

Commit 2 — password line deleted, message "remove password from config".

Today's file (`cat application-prod.yml`): clean, no password line.

```bash
git log --oneline
```

```text
362d7d4 remove password from config
65c9c6f add production config
```

Recovery, one command (`HEAD~1` = one commit before the latest):

```bash
git show HEAD~1:application-prod.yml
```

```text
spring:
  datasource:
    url: jdbc:postgresql://prod-db.zenith.internal:5432/zenith
    username: zenith_app
    password: SuperSecret123!
```

Diff view (`git log -p -- application-prod.yml`): the password appears twice —
`+ password: SuperSecret123!` in commit 1, `- password: SuperSecret123!` in commit 2.

Readings: the delete commit cleaned today's snapshot and simultaneously **advertised**
where the treasure sits. Anyone with a clone can recover it, forever, no special tools.

### Where a secret may live, and where never

| Place | Verdict |
| --- | --- |
| Tracked files (`application*.yml`, code, README) | **Never.** History keeps them; they also ship inside the jar. |
| A constant in source code | **Never.** Same, plus visible to every reader. |
| Environment variable, set outside the repo | Good — Lesson 8. |
| Untracked `.env` listed in `.gitignore` | Acceptable for local dev — Lesson 9 builds the fence. |
| Secret manager (Vault, K8s secrets, cloud) | The production answer — Phase 13. |

Hard truths: "private repo" is a setting, not a guarantee (forks, laptops, CI logs,
later publication). Bots scan public pushes for secrets within seconds. Hence the
secret scanner in Task 0.3's build.

### Common mistakes

- "Deleted in the next commit, we are safe." Two commands recover it.
- "Private repo, does not matter." Private is not protection.
- "Rewrite history, done." Without rotation the value still works — for the thief too.
- "Just temporarily." The fence must exist before the secret file does.

### Link to the task

Ground for S6 (password out of files, into an environment variable) and S7
(`.gitignore` entries **before** any secret file exists). The task's done-when ends:
*no password appears in any tracked file*.

### Do it yourself (second laptop)

**Setup:** git installed (`git --version`; accept the Xcode tools prompt if asked).

1. `mkdir git-never-forgets-demo && cd git-never-forgets-demo && git init -b main`,
   set `git config user.name` / `user.email`.
2. Create `application-prod.yml` with a fake `password:` line; commit
   "add production config".
3. Delete the password line; commit "remove password from config".
4. `cat application-prod.yml` → clean.
5. `git show HEAD~1:application-prod.yml` → password recovered.
6. `git log -p -- application-prod.yml` → the `+` and `-` password lines.
7. Optional cleanup: `cd .. && rm -rf git-never-forgets-demo`.

**Done when:** you recovered a deleted password yourself and can explain why the
removal commit made things worse.

### Summary for revision

- Secret = harm if seen. Tracked = git keeps it. Rotate = change it at the source.
- Commits are snapshots; deleting later never touches older snapshots.
- `git show HEAD~1:<file>` and `git log -p` recover "deleted" secrets.
- Rotation first; history rewrite second, if at all.
- Fences before secrets: `.gitignore` entries first, secret files after.

### Check questions, with answers

**Q1.** A teammate committed and pushed the production database password yesterday;
today they deleted the line and committed the removal: "we are fine." Two reasons this
is not fine, and the first action?

**Answer.** (1) History: the old snapshot still exists in every clone;
`git show HEAD~1:<file>` recovers it in one command — and the removal commit advertises
where to look. (2) The password was public the moment it was pushed; it must be treated
as stolen regardless of any cleanup. First action: **rotate** the database password at
the database, immediately. History cleanup is secondary and optional by comparison.

**Q2.** Homes for a real database password: (a) `application-local.yml`, committed;
(b) an environment variable on the running machine; (c) `.env`, listed in `.gitignore`,
never committed; (d) `private static final String DB_PASSWORD = "s3cret";` in code.
Verdicts?

**Answer.** (a) Never — tracked, kept in history forever, ships inside the jar.
(b) Good — lives outside repo and jar, handed to the process at start. (c) Acceptable
for local development — but only because it is ignored; the fence must exist and be
checked. (d) Never — code is tracked and shipped, and every reader sees the value.

### Your doubts

None raised for Lesson 7.

---

## Lesson 8 — Hands-on: the password comes from the environment (S6)

### Definition

`${DB_PASSWORD}` in a settings file is a **placeholder**: a hole, filled at startup
with the value of a setting named `DB_PASSWORD` — an environment variable with that
name counts. The file says **where** the value comes from, never **what** it is.
No default, on purpose.

### Why it is needed

A password may live in no tracked file (Lesson 7), yet the app needs it at run time.
The environment variable solves it: the operating system hands the value to the
process. Not in the repo, not in the jar. Why no default? `${DB_PASSWORD:something}`
would (1) put a working password back into a tracked file, and (2) let a missing
password pass silently instead of stopping the app.

### The change

In `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    username: postgres
    password: ${DB_PASSWORD}   # filled from outside at startup; no default, on purpose
```

Then `mvn package` (the yml lives inside the jar).

### Three runs, run for real

**A — set and correct:**
`DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`

```text
The following 1 profile is active: "local"
database says I am = zenith
checkout: FAKE charge of Rs.499 approved. No real money moved.
```

**B — missing:** `SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`

```text
Caused by: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed for user "postgres"
```

The app died — fail-fast worked. But note the message: **not** "variable missing". For
settings bound like the datasource ones, an unfillable hole stays as raw text, so the
literal characters `${DB_PASSWORD}` were sent to Postgres as the password. Rejected.

**C — set but wrong:** `DB_PASSWORD=wrong ... java -jar ...` → the **identical**
message as B.

Habit: "password authentication failed" = missing variable **or** wrong value. The log
cannot tell you which. First move, always:

```bash
echo $DB_PASSWORD
```

Empty → missing in this terminal. Non-empty → the value is wrong.

### Verify the subtask goal

```bash
grep -rn "password" src/
# → only: password: ${DB_PASSWORD}   (the name of the hole, no value)
grep -rn '\bdev\b' src/
# → no match - the value is gone from every file
```

### The blank default trap

`${DB_PASSWORD:}` sends an **empty** password. Against our Postgres: a vaguer failure.
Against a database that accepts password-less logins: a **silent success** — the app
runs unprotected and nobody notices. That is the "silently using a blank one" the task
warns about. Secrets get no defaults, ever.

### Where it is used

- Dev shell: the `DB_PASSWORD=dev java -jar ...` prefix (lands in shell history —
  acceptable for a dev password; `.env` in Lesson 9 is tidier).
- Docker: `-e DB_PASSWORD=...`. Kubernetes: a Secret mapped into the environment
  (Phase 13). CI: the server's secret store injects variables.

### Common mistakes

- **Adding a default "to be safe".** `${DB_PASSWORD:dev}` is a password in a tracked
  file again.
- **Expecting a "missing placeholder" error.** Reality: raw text → "authentication
  failed". Check `echo $DB_PASSWORD` first.
- **Variable set in one terminal, run in another.** Each terminal has its own
  environment.
- **The side door.** A committed `run.sh` with `DB_PASSWORD=real123` inside is a leak.
  Scripts count as tracked files.
- **Old jar.** Rebuild after editing the yml.

### Link to the task

**S6 complete:** password out of every file, read via `${DB_PASSWORD}` with no default,
missing password stops the app. Checkpoint 3 (mostly): production password lives in the
environment, fed by a secret manager; never in tracked files, never in the jar.

### Do it yourself (second laptop)

**Setup:** Lessons 1–6 done; Postgres running.

1. Replace the password line with `password: ${DB_PASSWORD}`. No default.
2. `mvn package`.
3. Run A (`DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`) →
   `database says I am = zenith`.
4. Run B (no variable) → dies with `FATAL: password authentication failed`. Say why:
   raw `${DB_PASSWORD}` was sent.
5. Run C (`DB_PASSWORD=nope ...`) → same message. `echo $DB_PASSWORD` separates B
   from C.
6. `grep -rn "password" src/` → only the placeholder. `grep -rn '\bdev\b' src/` →
   nothing.

**Done when:** A works, B and C stop the app, greps are clean.

### Summary for revision

- Placeholder `${NAME}` = hole filled from outside at startup. Env var `NAME` fills it.
- No default for secrets: fallback = secret back in a file + silent limping.
- Missing and wrong look identical: "password authentication failed". `echo` first.
- Blank default can silently connect. Never.
- Greps prove the done-when: no password value in any file.

### Check questions, with answers

**Q1.** `password: ${DB_PASSWORD}` versus `password: ${DB_PASSWORD:dev}` — behaviour
when the variable is missing, and which belongs in a real project (two reasons)?

**Answer.** Version one: the hole stays unfilled, the raw text goes through as the
password, the connection is rejected, the app stops — loudly, at once. Version two: the
app silently uses `dev` — it may connect somewhere unintended and nobody notices; and
`dev`, a working password, now sits in a tracked file. A real project uses version one:
fail fast and loud; keep every password value out of tracked files.

**Q2.** With `password: ${DB_PASSWORD}`, a teammate gets
`FATAL: password authentication failed for user "postgres"` and insists the password is
correct. The two possible causes, and the one command to separate them?

**Answer.** Causes: the variable is not set in that terminal (the raw placeholder text
was sent), or it is set to a wrong value. Separator: `echo $DB_PASSWORD` — empty means
missing; a value means compare it with the real password.

### Your doubts

None raised for Lesson 8.

---

## Lesson 9 — Hands-on: `.gitignore` before the secret exists (S7)

### Definition

`.gitignore` lists paths git must never track. Each line is a pattern: `.env` matches
that name, `*.pem` matches any file ending `.pem`. `.env` is the convention for a local
`KEY=value` file holding development secrets (`DB_PASSWORD=dev`).

### Why it is needed — and why *before*

1. Habit: `git add .` will eventually catch any unprotected secret file — and history
   keeps it forever.
2. Less known: **`.gitignore` cannot hide an already-tracked file.** Rules only stop
   git from *starting* to track. Fence first, cattle second — the task's exact order.

### The fence, built for real

`scratch-app/.gitignore`, written **before** any secret file existed:

```text
target/
.env
*.pem
application-local-secrets.yml
```

- `target/`: build output. `.env`: local secrets. `*.pem`: key files.
  `application-local-secrets.yml`: a local-only settings file name, ignored
  preemptively.
- `.gitignore` itself **is committed** — team law, contains only names.
- `application-local.yml` / `application-test.yml` are committed too: they hold
  addresses, not secrets.

Then: `git init -b main`, `git add . && git commit`, `git status --short` → empty.

### Secret files arrive — and stay invisible

Created `.env` (`DB_PASSWORD=dev`) and `local-dev.pem`. `git status --short` → still
empty. Proof of which rule caught them:

```bash
git check-ignore -v .env local-dev.pem
```

```text
.gitignore:2:.env	.env
.gitignore:3:*.pem	local-dev.pem
```

Read: ignored because of `.gitignore` line 2 / line 3. `git check-ignore -v` is the
fence-tester.

### Feeding the app from `.env`

Spring Boot does **not** read `.env` itself. The shell loads it:

```bash
set -a; source .env; set +a
SPRING_PROFILES_ACTIVE=local java -jar target/*.jar
```

Real result: `database says I am = zenith` — no password typed, none in any tracked
file. (`source` reads the file into the shell; `set -a` passes new variables to child
programs; `set +a` switches that off. Docker Compose reads `.env` natively.)

### The trap, demonstrated for real

`oops-secret.txt` (containing `pretend-password=abc123`) was committed **first**, then
added to `.gitignore`, then edited:

```bash
git status --short
```

```text
 M .gitignore
 M oops-secret.txt
```

Still tracked — the rule did not hide it. Fix tracking:

```bash
git rm --cached oops-secret.txt && git commit -m "stop tracking oops-secret.txt"
```

But the past remains:

```bash
git show HEAD~1:oops-secret.txt   # → pretend-password=abc123
```

Stopping tracking cleans the future, never the past. A real secret here must be
rotated.

### Common mistakes

- Rule added after committing → file stays tracked; history keeps old versions.
  `git rm --cached` + rotation.
- `.gitignore` not committed → only your machine is fenced.
- Believing ignore = protect. It only means "never start tracking".
- Expecting Spring to read `.env`. The shell or Docker Compose loads it.

### Link to the task

**S7 complete, in the demanded order:** entries added before any such file existed;
`.env` and a `.pem` then created and proven invisible (`git status`,
`git check-ignore -v`). With Lesson 8: no tracked file contains a password.

### Do it yourself (second laptop)

1. Create `.gitignore` with the four lines — first.
2. `git init -b main`, configure user, `git add . && git commit`. Status: empty.
3. Create `.env` (`DB_PASSWORD=dev`) and `local-dev.pem`. Status: still empty.
4. `git check-ignore -v .env local-dev.pem` → two rule lines.
5. `set -a; source .env; set +a`, then run with `SPRING_PROFILES_ACTIVE=local` →
   `database says I am = zenith`.
6. Trap: commit `oops-secret.txt`, then ignore it, edit it → status shows `M`.
   `git rm --cached`, commit; `git show HEAD~1:oops-secret.txt` → value still there.
7. `rm oops-secret.txt`.

**Done when:** the fence caught both files before git saw them; the app ran fed by
`.env`; you watched a rule fail to hide a tracked file.

### Summary for revision

- Fence before secrets: `.gitignore` entries first, files second.
- Rules stop tracking from starting; they never untrack. `git rm --cached` untracks;
  only rotation heals history.
- `git check-ignore -v <file>` names the matching rule.
- `.env` loads via `set -a; source .env; set +a` — Spring does not read it.
- Commit `.gitignore`; commit profile files with addresses; never commit values.

### Check questions, with answers

**Q1.** A teammate commits and pushes `real-keys.pem`, then adds `*.pem` to
`.gitignore` the next day: "Fixed." (a) Is the file still tracked? (b) What two
problems remain, with their actions?

**Answer.** (a) Yes. Ignore rules never untrack. (b) Problem 1: every future edit keeps
being committed — run `git rm --cached real-keys.pem` and commit, so the rule finally
applies. Problem 2: the key sits in pushed history and must be treated as stolen —
rotate/revoke the key at its source. (History rewriting is optional damage control
after rotation.)

**Q2.** (a) Should `.gitignore` itself be committed? (b) Our `.gitignore` names
`application-local-secrets.yml` — is naming it a leak?

**Answer.** (a) Yes. It is the team's shared fence and contains only names and
patterns, never values. Uncommitted, it protects one machine only. (b) Only the file
**name** is visible, not its contents. That a local secrets file may exist is harmless
information; the entry is precisely what keeps its **value** out of the repo. Good
trade.

### Your doubts

None raised for Lesson 9.

---

## Lesson 10 — Break it on purpose: reading the failure analyser (S8)

### Goal

Crash the app the classic way — a profile whose file does not exist, so no database URL
arrives — and learn to read the failure like a professional. Zero file edits needed:
the base `application.yml` has no URL by design.

### The skill

A crashing Spring Boot app prints a long stack trace. **Failure analysers** translate
common failures into a block at the very bottom: `APPLICATION FAILED TO START`, with a
**Description** (what went wrong) and an **Action** (what to consider). Always scroll
there first. (First met in the gateway lesson: the "2 beans found" block.)

### The break, run for real

```bash
DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=staging java -jar target/*.jar
```

```text
APPLICATION FAILED TO START
***************************

Description:

Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.

Reason: Failed to determine a suitable driver class


Action:

Consider the following:
	If you want an embedded database (H2, HSQL or Derby), please put it on the classpath.
	If you have database settings to be loaded from a particular profile you may need to activate it (the profiles staging are currently active).
```

### Decoded

| Line | Plain meaning |
| --- | --- |
| `Failed to configure a DataSource` | Spring could not build the door to the database. |
| `'url' attribute is not specified` | `spring.datasource.url` has no value — no address. |
| `no embedded datasource could be configured` | An **embedded database** runs inside the app (H2 etc.); Spring looked for one as fallback; none on board, on purpose. |
| `Reason: Failed to determine a suitable driver class` | The URL names the database kind (`jdbc:postgresql://`); no URL, no way to choose a driver. |
| Action bullet 2 | The gold: `(the profiles staging are currently active)` — the analyser names the active profiles. One glance: `staging` is on, `staging` has no file. |

Second flavour — plain start, no profile (`DB_PASSWORD=dev java -jar target/*.jar`):
same Description; the Action ends `(no profiles are currently active)`. This is the
promised plain-start failure: the URLs live only in profile files.

### The reading method

1. Do not read the stack trace top-down. Scroll to `APPLICATION FAILED TO START`.
2. Read the Description — what failed, which setting.
3. Read the Action — the parenthesis names the active profiles; compare with what you
   meant. Typos show up right there.
4. Cross-check the profile line at the top of the log.
5. Stack trace only if still unclear.

### Common mistakes

- Drowning in the stack trace; the answer was in the block.
- "Fixing" by adding a URL to base `application.yml` — starts, but marries the artifact
  to one database. Right fix: right profile or the missing file.
- Adding H2 because the Action mentioned it. Actions list possibilities, not intent.
- Confusing with "Connection refused": refused = address existed, nothing answered;
  this block = no address at all, no attempt made.

### Link to the task

**S8 complete.** Two analyser blocks read now: bean conflict (Lesson 6), missing
datasource (here). The habit — block first — is the deliverable.

### Do it yourself (second laptop)

1. `DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=staging java -jar target/*.jar` → scroll to
   the block. Read Description and Action aloud.
2. `DB_PASSWORD=dev java -jar target/*.jar` → same Description;
   `(no profiles are currently active)`.
3. Recover: `DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local java -jar target/*.jar` →
   `database says I am = zenith`. Nothing to undo.

**Done when:** both blocks found without reading stack traces; the active-profiles
parenthesis explained.

### Summary for revision

- Failure analyser = Description + Action at the bottom. Scroll there first.
- `'url' attribute is not specified` = no address anywhere → check active profiles and
  their files.
- The Action's parenthesis lists active profiles — typo detector.
- No address ≠ refused connection: not tried vs tried-and-failed.

### Check questions, with answers

**Q1.** Jar: `application.yml` (credentials, no URL), `application-local.yml` (URL).
Typed: `SPRING_PROFILES_ACTIVE=locl` (typo). The block shows
`'url' attribute is not specified...` and the Action ends
`(the profiles locl are currently active)`. Chain of events, where the typo shows, fix?

**Answer.** Chain: profile `locl` activated → no `application-locl.yml` → silently
skipped (missing profile files never error) → no URL from anywhere → DataSource cannot
be configured → analyser block. The typo is printed inside the Action's parenthesis:
`locl`. Fix: rerun with `SPRING_PROFILES_ACTIVE=local`.

**Q2.** Failure one: `Failed to configure a DataSource: 'url' attribute is not
specified...`. Failure two: `Connection to localhost:5999 refused...`. Meaning and
cause of each?

**Answer.** One: **no address configured** — the active profiles contributed no
`spring.datasource.url` (wrong/missing profile or file); Spring never attempted a
connection. Fix the profile or its file. Two: **address configured, nobody answered** —
the URL existed but nothing listens there (wrong port/host, Postgres down); Spring
tried and was refused. Start Postgres or fix host/port.

### Your doubts

None raised for Lesson 10.

---

## Lesson 11 — The test trap: profiles apply to tests too (S9)

### Definition

`@SpringBootTest` boots the real application context — real beans, real `DataSource`,
real configuration reading. So tests pick up `SPRING_PROFILES_ACTIVE` like the app
does. `@ActiveProfiles("test")` on a test class **pins** its profiles, ignoring the
shell.

### Why it is needed

The accident: morning — `export SPRING_PROFILES_ACTIVE=local` (exports stick to the
terminal). Afternoon — `mvn test` in the same terminal. Environment flows shell → Maven
→ test process. Tests boot under `local` → the development database. A test with
"delete all rows" cleanup empties dev tables. That is the trap, noted for Task F7.5.

### The demo, built and run for real

New pom dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

New file `src/test/java/com/zenith/payments/DatabaseSideTest.java` (first version,
no pin):

```java
package com.zenith.payments;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class DatabaseSideTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void whichDatabaseDoesThisTestTouch() {
        String name = jdbc.sql("select current_database()").query(String.class).single();
        System.out.println(">>> this test talked to database: " + name);
    }
}
```

Style note: `@Autowired` on fields is discouraged for beans — **test classes are the
accepted exception**; the framework fills the field.

**Run 1 — the trap** (`DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local mvn test`):

```text
The following 1 profile is active: "local"
>>> this test talked to database: zenith
```

A test touched the development database.

**Run 2 — no profile** (`DB_PASSWORD=dev mvn test`):

```text
Failed to configure a DataSource: 'url' attribute is not specified and no embedded datasource could be configured.
	If you have database settings to be loaded from a particular profile you may need to activate it (no profiles are currently active).
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
```

Same failure analyser block as Lesson 10, inside a test run. Tests are not special.

**Run 3 — the pin.** Added to the class:

```java
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseSideTest {
```

Same command as Run 1 — shell still says `local`:

```text
The following 1 profile is active: "test"
>>> this test talked to database: zenith_lab
```

The environment variable lost. The pin sets the test context's profiles directly.

### Common mistakes

- No pin → test behaviour depends on the runner's shell.
- `application-test.yml` pointing at the dev database — the pin is useless then. Pair:
  `test` profile ↔ throwaway database (`zenith_lab`).
- Believing `@SpringBootTest` is "not the real app". It is.
- This repo now runs the test during `mvn package` → build with
  `DB_PASSWORD=dev mvn package` or `mvn -DskipTests package`.

### Link to the task

**S9 complete** — trap demonstrated, defence installed. F7.5 adds test slices; the pin
habit carries over.

### Do it yourself (second laptop)

1. Add the test dependency; create `DatabaseSideTest.java` without the pin.
2. `DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local mvn test` →
   `>>> this test talked to database: zenith` (the trap).
3. `DB_PASSWORD=dev mvn test` → the DataSource failure block, `Errors: 1`.
4. Add `@ActiveProfiles("test")` + import.
5. `DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local mvn test` →
   `>>> this test talked to database: zenith_lab` (the pin wins).
6. `git add -A && git commit -m "database side test pinned to the test profile"`.

**Done when:** one annotation moved the same command between two databases, and you can
explain why the pin beats the shell.

### Summary for revision

- Tests boot the real context; profiles leak in from the shell.
- `@ActiveProfiles("test")` pins the profile per test class — shell loses.
- Pair the `test` profile with a throwaway database, always.
- Same failure analyser rules apply inside test runs.

### Check questions, with answers

**Q1.** Morning: `export SPRING_PROFILES_ACTIVE=local` (dev database in
`application-local.yml`). Afternoon, same terminal: `mvn test`; one test class has no
`@ActiveProfiles` and deletes all rows from `holds` as cleanup. (a) What happens, via
what chain? (b) Two defences?

**Answer.** (a) The export persists in the terminal; Maven inherits it; the forked test
process inherits it from Maven; `@SpringBootTest` boots under `local`;
`application-local.yml` supplies the dev database URL; the cleanup deletes every row of
`holds` **in the development database**. (b) Pin the class with
`@ActiveProfiles("test")` (paired with a throwaway test database), and check the shell
before test runs (`echo $SPRING_PROFILES_ACTIVE`, `unset` if set). Either alone would
have prevented the loss; use both.

**Q2.** Class has `@SpringBootTest` + `@ActiveProfiles("test")`; `local` → `zenith`,
`test` → `zenith_lab`. Run: `SPRING_PROFILES_ACTIVE=local mvn test`. (a) Profile and
database? (b) Why desirable?

**Answer.** (a) The context uses `test` and touches `zenith_lab`. (b) Tests must not
depend on the runner's shell: every machine and CI gets the same behaviour, and tests
may freely write and delete because they only ever touch the throwaway database.

### Your doubts

None raised for Lesson 11.

---

## Lesson 12 — Ask-AI, final audit, and the checkpoint (S10)

### The ask-AI exercise, answered in full

The task's prompt: *"Show me three ways a database password commonly gets committed to
git by accident in a Spring Boot project, and what to do in the first five minutes
after it happens."*

**Way 1 — written into a settings file "just temporarily".** While debugging, someone
replaces `password: ${DB_PASSWORD}` with the real value to rule out an environment
problem. It works, they move on, `git add .` ships it. Sneakiest form: the real value
as the placeholder default — `${DB_PASSWORD:Hunter2!}`. Looks like discipline; is a
password in a tracked file.

**Way 2 — the secrets file is created before the fence.** `.env` or
`application-local-secrets.yml` is made first; `.gitignore` is planned "for later";
`git add .` sweeps the file in. Same family: IDE run configurations committed into the
repo (`*.run.xml`, workspace files) carrying the environment values typed into the run
dialog. This is why the entries go into `.gitignore` **before** such files exist.

**Way 3 — the side doors: scripts and documentation.** `run.sh` with
`DB_PASSWORD=Hunter2! java -jar app.jar`, a `Makefile`, `docker-compose.yml` with the
value inline, or a README "how to run" snippet. Committed proudly as tooling or docs.
Scripts and docs are tracked files; they count fully.

**The first five minutes:**

1. **Minute 0–1: rotate.** Change the password at the source (`ALTER USER ... WITH
   PASSWORD ...`, or the cloud console) — **before any git work**. Once pushed, the
   value is stolen; bots scan public pushes within seconds. Rotation un-arms the leak.
2. **Minute 1–2: scope.** Pushed, or only in local commits? Never left the machine:
   amend, done. Pushed: continue.
3. **Minute 2–3: clean the tip.** Put the placeholder back, commit. New pulls are
   clean. (History still holds the old value — which is fine: it is dead.)
4. **Minute 3–4: tell the team, read the logs.** Announce it; check database access
   logs for logins with the old credential.
5. **Minute 4–5: decide on history, harden.** `git filter-repo` / BFG rewrite is
   optional hygiene after rotation (new commit ids, everyone re-clones). Verify the
   `.gitignore` fence; add the secret scanner to the build (Task 0.3) so the next
   attempt fails before it leaves the laptop.

Principle: **rotate first, clean second, rewrite maybe.** Git surgery never un-steals a
value.

### The final audit, run for real

The task's "done when" has three conditions:

| Condition | Evidence |
| --- | --- |
| Same jar connects to two databases via `SPRING_PROFILES_ACTIVE` | Lesson 4: `database says I am = zenith` under `local`, `zenith_lab` under `test`, one build. |
| Fake gateway wired under `local` only | Lessons 5–6: `wired gateway = FakePaymentGateway` under `local`, `HttpPaymentGateway` under `test`. |
| No password in any tracked file | Audit below. |

Audit commands, run in `scratch-app/`:

```bash
git log --all --oneline -- .env
# (no output = .env never entered history)

git grep -n "password" -- .
# src/main/resources/application.yml:8:    password: ${DB_PASSWORD}   # the hole's name, no value

git status --short
# (clean)
```

All three conditions hold. **Task F6.5 complete.**

### Do it yourself (second laptop)

1. In `scratch-app/`, run the three audit commands above; expect the same clean
   results.
2. Final sweep: `DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local java -jar target/*.jar`
   then the same with `test` — two database names, one jar.

**Done when:** all three audits are clean on your own machine.

### Summary for revision

- Three leak paths: settings file ("temporarily", or a real value as placeholder
  default); secrets file created before the fence (plus committed IDE run configs);
  side doors (scripts, compose files, READMEs).
- First five minutes: rotate → scope → clean the tip → tell the team and read logs →
  optional history rewrite → harden (fence + scanner).
- Rotate first, clean second, rewrite maybe.

## Checkpoint — answer out loud, answers for checking yourself

**1. What is a profile, and which file is loaded when `local` is active?**

A profile is a name for one way of running the app, chosen at start time, with no
rebuild. When `local` is active, Spring loads the base `application.yml` (always) and
`application-local.yml` on top. Values the profile file declares win; everything else
falls through to the base.

**2. Describe how one jar connects to two different databases with no rebuild.**

The jar carries `application-local.yml` with one database URL and `application-test.yml`
with another; the base file holds no URL. Starting with `SPRING_PROFILES_ACTIVE=local`
loads the first file → first database. The same jar with `SPRING_PROFILES_ACTIVE=test`
loads the second file → second database. The choice arrives from outside, through an
environment variable, so the artifact never changes. Seen for real: `database says I am
= zenith`, then `= zenith_lab`, from one build.

**3. Where should a production database password live, and name two places it must
not.**

It should live in the environment of the running process — fed by a secret manager
(Kubernetes Secret, Vault, a cloud secret store) into an environment variable that the
configuration reads as `${DB_PASSWORD}` with no default. It must not live in any
tracked file (settings files, code, scripts, READMEs — git history keeps them forever),
and it must not live inside the jar (settings files ship in it). Also never as a
placeholder default — that is a tracked file again.

**4. Why is `@Profile` on a bean more powerful than a profile-specific property
value?**

A property value changes a setting — a string or a number fed into the same code. A
`@Profile` bean changes **which object exists** — different code behind the same
interface. No value could make `charge()` pretend; the bean swap did exactly that:
`FakePaymentGateway` under `local`, `HttpPaymentGateway` everywhere else. Values tune
behaviour; `@Profile` replaces it.

## Task F6.5 — closed

**Final state of `scratch-app`:**

- `pom.xml` — starter, `spring-boot-starter-jdbc`, Postgres driver,
  `spring-boot-starter-test`.
- `application.yml` — `zenith.*` demo settings; datasource username;
  `password: ${DB_PASSWORD}` (no default); **no URL**.
- `application-local.yml` — URL → `zenith`. `application-test.yml` — URL →
  `zenith_lab`.
- `.gitignore` — `target/`, `.env`, `*.pem`, `application-local-secrets.yml`,
  `oops-secret.txt`. `.env` (ignored) — `DB_PASSWORD=dev`.
- Java: `ProfilesApplication`, `SettingsPrinter`, `DatabaseProbe`, `PaymentGateway`,
  `FakePaymentGateway` (`@Profile("local")`), `HttpPaymentGateway`
  (`@Profile("!local")`), `CheckoutService`; test `DatabaseSideTest`
  (`@ActiveProfiles("test")`).

**Command cheat-sheet:**

```bash
# build (tests run pinned to the test profile and need the password):
DB_PASSWORD=dev mvn package          # or: mvn -DskipTests package

# run against zenith / zenith_lab:
DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=local java -jar target/*.jar
DB_PASSWORD=dev SPRING_PROFILES_ACTIVE=test  java -jar target/*.jar

# or load the password from .env first:
set -a; source .env; set +a
```

### Your doubts

None raised for Lesson 12 — and none across the whole task.

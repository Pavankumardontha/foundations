# F6.9 — Auto-configuration: removing the magic

**Goal of the task.** Explain what a starter is. Run the conditions report. Use it to
answer "why does this bean exist?" and "why did my configuration not take effect?" with
evidence, not guesses.

**Done when:** you can open the conditions report and explain, for one bean you choose,
the precise reason it exists or does not.

**You need first:** Task F6.1 (what a bean is, what the application context does).
Task F6.3 (duplicate beans, beans you do not own). Reading a `pom.xml` from F1.3.

**The story in two sentences.** "It works and I do not know why" turns into "it is
broken and I do not know why" the first time something unusual happens. This task turns
Spring Boot from magic into a system with rules you can inspect: a shopping list
(starters), a rule book (auto-configuration classes), and a receipt (the conditions
report).

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Starter** | A dependency that contains no code, only a curated list of other dependencies. `spring-boot-starter-web` brings Spring MVC, Jackson, and Tomcat together. |
| **Transitive dependency** | A dependency pulled in by another dependency, not declared by you. |
| **Auto-configuration** | Configuration classes that ship inside Spring Boot and create beans for you **if** certain conditions hold. |
| **Condition** | The test that decides whether an auto-configuration applies, such as "is this class on the classpath?" |
| **`@ConditionalOnClass`** | "Apply this only if that class is present." Presence of a library is treated as intent to use it. |
| **`@ConditionalOnMissingBean`** | "Apply this only if the developer has not defined their own." This is what lets you override anything. |
| **Backing off** | An auto-configuration deciding not to act because you supplied your own bean. |
| **Conditions report** | The list printed by `--debug` showing which auto-configurations matched, which did not, and why. |
| **Actuator** | An add-on exposing endpoints that describe the running application: health, properties, beans, conditions. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Run `mvn dependency:tree`. Find `spring-boot-starter-web` and what hangs beneath it. A starter is a shopping list, not a library. |
| S2 | Run with `java -jar target/*.jar --debug`, send output to a file, find the `CONDITIONS EVALUATION REPORT`. |
| S3 | Read three entries properly: a positive match (`JacksonAutoConfiguration`), a negative match (Redis or Mongo), and the exclusions list. |
| S4 | Say the rule out loud: "library on the classpath → configure the obvious thing". Know what adding `spring-boot-starter-data-jpa` causes. |
| S5 | Prove `@ConditionalOnMissingBean`: define your own `ObjectMapper`, restart with `--debug`, watch Jackson's auto-configuration back off. |
| S6 | Break it on purpose, twice: exclude `DataSourceAutoConfiguration` in a JPA app; then remove the Postgres driver instead. Read both failures. |
| S7 | Open the `spring-boot-autoconfigure` jar, find `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. The magic is a text file. |
| S8 | Add `spring-boot-starter-actuator`, opt in to `health,env,beans,configprops,conditions`, visit each endpoint. |
| S9 | Write the security note: these endpoints are never public without authentication. |
| S10 | Ask AI: how Spring Boot decides to create a `DataSource` bean, condition by condition. |
| S11 | Ask AI: a step-by-step method to find which bean changed after adding a dependency. |
| CP | Checkpoint: five questions, out loud. |

## Roadmap for this task — 16 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | A starter is a shopping list | What a starter really is, proven with `mvn dependency:tree` and an empty jar. | S1 |
| 2 | Where beans you never wrote come from | What auto-configuration is, and why your context is full of strangers. | S2–S4 |
| 3 | `@ConditionalOnClass` | The test "is this library present?", and why presence means intent. | S3, S4 |
| 4 | `@ConditionalOnMissingBean` | The test that makes every default overridable. | S5 |
| 5 | The conditions report | Running with `--debug` and finding the report in the noise. | S2 |
| 6 | Reading a positive match | One `JacksonAutoConfiguration` entry, decoded word by word. | S3 |
| 7 | Negative matches and exclusions | Why absent things are absent, with evidence. | S3 |
| 8 | The one rule, and what JPA drags in | Why one `pom.xml` line creates a `DataSource` and friends. | S4 |
| 9 | Make Jackson back off | Your own `ObjectMapper`, and the report proving the framework yielded. | S5 |
| 10 | Break it on purpose, part 1 | Excluding `DataSourceAutoConfiguration`, and reading the failure analysis. | S6 |
| 11 | Break it on purpose, part 2 | Removing the driver instead, and how the message changes. | S6 |
| 12 | The master list file | The plain text file inside the jar that lists all auto-configurations. | S7 |
| 13 | Actuator, and why exposure is opt-in | What actuator is, and why most endpoints 404 out of the box. | S8 |
| 14 | The five endpoints | `health`, `env`, `beans`, `configprops`, `conditions` on a live app. | S8 |
| 15 | The security note | What an attacker reads in `/actuator/env`, and the rule for Phase 13. | S9 |
| 16 | Ask-AI drills and checkpoint | The two AI prompts, then the five checkpoint questions. | S10, S11, CP |

The scratch app for this task lives in
`F6.9-auto-configuration-removing-the-magic/scratch-app`. It starts with one declared
dependency (`spring-boot-starter-web`) and one class.

---

## Lesson 1 — A starter is a shopping list (subtask S1)

### Definition

A **starter** is a dependency whose jar contains **no code**. It is only a named list of
other dependencies. When you add a starter, Maven fetches everything on its list.

### Why it is needed

Imagine starters do not exist, and you want to build a web application.

You need a web server (Tomcat). You need the web framework (Spring MVC). You need a JSON
converter (Jackson). You need a logging setup. Each of those needs its own helpers. That
is roughly 30 libraries.

Now the painful part. You must pick a **version** for each one. The versions must agree
with each other. Jackson 2.21 may need a different Spring version than Jackson 2.15.
Pick wrong, and the app compiles fine but explodes at runtime with errors like
`NoSuchMethodError`. Those errors say nothing about versions. People lose days on them.

A starter removes this whole job. Someone at Spring already picked a set of libraries,
with versions tested together. You add one line. You get the whole tested set.

### How it works

Three steps happen behind one line in `pom.xml`:

1. You declare the starter in `pom.xml` — the file where a Maven project lists what it
   needs.
2. Maven downloads the starter. Inside it finds another `pom.xml` — the starter's own
   shopping list.
3. Maven downloads everything on that list. Each item may have its own list too. Maven
   keeps following lists until nothing is missing.

A dependency that arrives this way — pulled in by another dependency, not declared by
you — is called a **transitive dependency**. Almost everything in a Spring Boot app is
transitive. You declare one or two lines. Maven assembles the rest.

### The example — one declared dependency

The whole dependencies section of the scratch app:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

And the only Java file in the project:

```java
package com.zenith.autoconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoconfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoconfigApplication.class, args);
    }
}
```

### Walkthrough — the real tree

Command:

```bash
mvn dependency:tree
```

Real output (deep logging detail trimmed):

```
[INFO] com.zenith:autoconfig-scratch:jar:0.0.1-SNAPSHOT
[INFO] \- org.springframework.boot:spring-boot-starter-web:jar:3.5.16:compile
[INFO]    +- org.springframework.boot:spring-boot-starter:jar:3.5.16:compile
[INFO]    |  +- org.springframework.boot:spring-boot:jar:3.5.16:compile
[INFO]    |  +- org.springframework.boot:spring-boot-autoconfigure:jar:3.5.16:compile
[INFO]    |  +- org.springframework.boot:spring-boot-starter-logging:jar:3.5.16:compile
[INFO]    |  |  +- ch.qos.logback:logback-classic:jar:1.5.34:compile
[INFO]    |  |  ... (more logging libraries)
[INFO]    |  +- org.springframework:spring-core:jar:6.2.19:compile
[INFO]    |  \- org.yaml:snakeyaml:jar:2.4:compile
[INFO]    +- org.springframework.boot:spring-boot-starter-json:jar:3.5.16:compile
[INFO]    |  +- com.fasterxml.jackson.core:jackson-databind:jar:2.21.4:compile
[INFO]    |  |  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.21:compile
[INFO]    |  |  \- com.fasterxml.jackson.core:jackson-core:jar:2.21.4:compile
[INFO]    |  ... (more Jackson add-ons)
[INFO]    +- org.springframework.boot:spring-boot-starter-tomcat:jar:3.5.16:compile
[INFO]    |  +- org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.55:compile
[INFO]    |  ... (more Tomcat pieces)
[INFO]    +- org.springframework:spring-web:jar:6.2.19:compile
[INFO]    \- org.springframework:spring-webmvc:jar:6.2.19:compile
```

Reading it top to bottom:

- **Line 1** is the project. Nothing else is ours.
- **Line 2**: `spring-boot-starter-web`, the one declared line. Indented means "arrived
  because of the line above".
- **`spring-boot-starter`**: the base starter. Every starter includes it. It brings
  Spring Boot itself, the `spring-boot-autoconfigure` jar (the star of Lesson 2), and
  logging.
- **`spring-boot-starter-json`**: another shopping list, for JSON. It brings
  `jackson-databind` — the actual JSON converter code.
- **`spring-boot-starter-tomcat`**: a third shopping list. It brings
  `tomcat-embed-core` — the actual web server code.
- **`spring-webmvc`**: the web framework itself. A real library, listed directly.

So one line brought Spring MVC, Jackson, and Tomcat together. Versions included. We
chose none of them.

### The proof — the starter jar is empty

```bash
unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-starter-web/3.5.16/spring-boot-starter-web-3.5.16.jar
```

Real output:

```
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  06-25-2026 08:17   META-INF/
      335  06-25-2026 08:17   META-INF/MANIFEST.MF
    11359  06-25-2026 08:10   META-INF/LICENSE.txt
      211  06-25-2026 08:10   META-INF/NOTICE.txt
---------                     -------
    11905                     4 files
```

Four files. A manifest and two license texts. Not one class file. The starter ships no
code. It only shops.

### Where it is used

- In F6.8 we added `spring-boot-starter-validation`. That shopping list brought
  Hibernate Validator — the engine that checks `@NotBlank`.
- Later in this task we add `spring-boot-starter-data-jpa`. One line. It brings JPA,
  Hibernate, and connection pooling.
- `spring-boot-starter-test` brings JUnit, Mockito, and friends.

The pattern is always the same. Name the capability. Add
`spring-boot-starter-<capability>`. The tested set arrives.

### Common mistakes

- **Thinking the starter contains the code.** "Tomcat runs, so the starter jar must hold
  the server." No. The starter jar holds a list. Tomcat's code is in
  `tomcat-embed-core`, which the list points to.
- **Declaring libraries by hand next to a starter.** For example, adding
  `jackson-databind` with your own `<version>2.15.0</version>` while `starter-web`
  already brings 2.21.4. Now two versions compete. This recreates exactly the pain
  starters exist to remove. Rule: if a starter brings it, do not declare it yourself.
- **Reading "starter" as "something that starts the app".** It does not run anything. It
  is inert. The name only means "a starting point for your dependency list".

### Link to the task

This is subtask S1, word for word. It also lays the floor for S4: adding a starter is
the *cause*; new beans appearing is the *effect*. The machinery between cause and effect
is Lessons 2–4.

### Do it yourself

**Step 0 — tools.** Check what is installed:

```bash
java -version
mvn -version
```

Expected: `openjdk version "21..."` and `Apache Maven 3.9...`. If missing on a Mac:
`brew install openjdk@21` and `brew install maven`.

**Step 1 — make the project.**

```bash
mkdir -p ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app/src/main/java/com/zenith/autoconfig
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
```

Create `pom.xml` with exactly this content:

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
    <artifactId>autoconfig-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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

Create `src/main/java/com/zenith/autoconfig/AutoconfigApplication.java`:

```java
package com.zenith.autoconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutoconfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoconfigApplication.class, args);
    }
}
```

**Step 2 — build once.**

```bash
mvn -q package -DskipTests
```

First run downloads a lot; that is normal. Expected end: silence, or `BUILD SUCCESS`.
If you see `Could not find artifact` or `Could not transfer artifact`, the internet is
blocked or an id in `pom.xml` is misspelled — compare character by character.

**Step 3 — the tree.**

```bash
mvn dependency:tree
```

Find `spring-boot-starter-web` and, indented beneath it: `spring-boot-starter` (holding
`spring-boot-autoconfigure`), `spring-boot-starter-json` (holding `jackson-databind`),
`spring-boot-starter-tomcat` (holding `tomcat-embed-core`), and `spring-webmvc`.

**Step 4 — open the empty jar.**

```bash
unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-starter-web/3.5.16/spring-boot-starter-web-3.5.16.jar
```

Expected: 4 files, all under `META-INF/`, zero class files.

**Done when:** you can point at `spring-webmvc`, `jackson-databind`, and
`spring-boot-starter-tomcat` in your own tree output, and at the 4-file listing of the
starter jar, and say in your own words why the jar is nearly empty.

### Practice questions

**Question 1.** A teammate runs this command and gets this output:

```
unzip -l spring-boot-starter-web-3.5.16.jar

  Length      Name
---------    ----
        0    META-INF/
      335    META-INF/MANIFEST.MF
    11359    META-INF/LICENSE.txt
      211    META-INF/NOTICE.txt
---------    -------
    11905    4 files
```

He says: "This jar is broken. The download must have failed, because our web server code
has to be in here somewhere — the server does run." Is he right? Say what this jar is
supposed to contain, and how the web server code actually reaches the project.

*Answer:* He is wrong. The jar is complete. A starter jar is **supposed** to be nearly
empty: it ships no code, only metadata. The real content of a starter is its own
`pom.xml` — a shopping list of other dependencies. When Maven resolves
`spring-boot-starter-web`, it reads that list and downloads the entries, including
`spring-boot-starter-tomcat`, which in turn lists `tomcat-embed-core`. That last jar
holds the actual web server classes. The server code reaches the project as a
**transitive dependency** — pulled in through the list, never stored in the starter jar
itself.

**Question 2.** A project's `pom.xml` has exactly one dependency:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

Today, `mvn dependency:tree` shows about 30 libraries under the project, including
`tomcat-embed-core` and `jackson-databind`. You now delete that one dependency block and
run `mvn dependency:tree` again. What does the tree show beneath the project line, and
why?

*Answer:* Nothing. The tree shows only the project line itself:

```
[INFO] com.zenith:autoconfig-scratch:jar:0.0.1-SNAPSHOT
```

Every one of the ~30 libraries was a transitive dependency. Each was present only
because the starter's shopping list (directly or through further lists) asked for it.
Remove the one declared line and the whole chain of lists disappears with it. Nothing
else in the project asked for Tomcat or Jackson, so Maven fetches nothing. (The project
would also stop compiling: `SpringApplication` and `@SpringBootApplication` arrive
through the same starter.)

### Summary for revision

- A starter = a jar with **no code**, only a curated dependency list with tested
  versions.
- `spring-boot-starter-web` brings: `spring-boot-starter` (core + autoconfigure +
  logging), `spring-boot-starter-json` (Jackson), `spring-boot-starter-tomcat` (the
  server), `spring-webmvc` (the framework).
- Libraries pulled in by other libraries are **transitive dependencies**.
- `mvn dependency:tree` shows the full chain: who asked for what.
- Proof of emptiness: `unzip -l` on the starter jar shows 4 files, zero classes.
- Never hand-declare a library a starter already brings; two versions would compete.

---

## Lesson 2 — Where beans you never wrote come from (serves S2–S4)

### Definition

Two reminders first. A **bean** is an object that Spring creates, holds, and hands out.
The **application context** is the container that holds all the beans.

**Auto-configuration** is a set of configuration classes that ship **inside Spring Boot
itself**. A configuration class is a class whose job is to declare beans. These shipped
classes create beans for you — **if** certain conditions hold.

### Why it is needed

The scratch app has one class and declares zero beans of its own, apart from itself. But
a working web app needs many framework objects: something listening on port 8080
(Tomcat), something routing requests to methods (`dispatcherServlet`), something turning
objects into JSON (an `ObjectMapper` bean), converters, error handling.

Before Spring Boot, developers declared all of those beans by hand, in every project.
Hundreds of lines of setup, copied around, drifting apart. Auto-configuration is the
answer: the obvious setup is prewritten once, inside the framework.

### How it works

1. The dependency tree from Lesson 1 contains this line, arrived through the starter:
   `org.springframework.boot:spring-boot-autoconfigure:jar:3.5.16`.
2. That jar holds the prewritten configuration classes. Counted in the real jar:
   **156 classes**. One for Jackson, one for Tomcat, one for data sources, one for
   Redis, and so on.
3. At startup, Spring Boot walks that list. For each class it asks: do this class's
   conditions hold? A condition is a small test, such as "is a certain library
   present?".
4. Classes that pass run their bean-declaring methods. Those beans land in the **same
   context as yours**. Classes that fail are skipped completely.

The context ends up holding your beans plus the beans of every auto-configuration class
that passed.

### The example — a bean census

The scratch app's main method, grown to interrogate the context:

```java
package com.zenith.autoconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AutoconfigApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(AutoconfigApplication.class, args);

        // Bean census: how many beans exist, and who declared one we never wrote?
        System.out.println("--- bean census ---");
        System.out.println("total beans in the context : " + context.getBeanDefinitionCount());
        System.out.println("beans we wrote             : 1 (autoconfigApplication)");
        System.out.println("jacksonObjectMapper exists : " + context.containsBean("jacksonObjectMapper"));
        System.out.println("dispatcherServlet exists   : " + context.containsBean("dispatcherServlet"));
        System.out.println("declared by                : "
                + context.getBeanFactory().getBeanDefinition("jacksonObjectMapper").getFactoryBeanName());
    }
}
```

Two new methods. `containsBean("name")` asks: "is there a bean with this name?"
`getBeanDefinition("name")` fetches the bean's record card; `getFactoryBeanName()` reads
one field of it: **who declared this bean**.

### Walkthrough — the real output

```
--- bean census ---
total beans in the context : 158
beans we wrote             : 1 (autoconfigApplication)
jacksonObjectMapper exists : true
dispatcherServlet exists   : true
declared by                : org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration$JacksonObjectMapperConfiguration
```

- **158 beans; we wrote 1.** So 157 beans exist that nobody on the team wrote.
- **`jacksonObjectMapper` exists** — the JSON converter bean. Never declared by us.
- **The `declared by` line is the birth certificate.** `JacksonObjectMapperConfiguration`
  is a small class nested inside `JacksonAutoConfiguration` (the `$` means "nested
  inside"). The package `org.springframework.boot.autoconfigure.jackson` lives inside
  the `spring-boot-autoconfigure` jar.

"Why does this bean exist?" already has a non-magic answer: a specific, named class
inside the autoconfigure jar declared it. Spring records the creator.

### Where it is used

Every Spring Boot capability arrives this way: the web setup, the JSON setup, and later
the database setup (`DataSource`, a connection pool) when the JPA starter is added.

### Common mistakes

- **Confusing component scan with auto-configuration.** Two different bean sources.
  Component scan: Spring reads *your own packages* and registers classes *you* annotated
  (`@RestController`, `@Component`). Auto-configuration: Spring Boot applies prewritten
  classes from a fixed list inside the autoconfigure jar.
- **Thinking auto-configured beans are special or locked.** They are ordinary beans in
  the same context as yours. They can be replaced (Lessons 4 and 9).
- **Thinking the set of auto-configurations is unknowable.** It is 156 classes, listed
  in one plain text file, inside one jar you already have (Lesson 12 opens it).

### Link to the task

Groundwork for S2, S3, S4. The conditions report of S2 is exactly this startup walk,
printed out: all 156 candidates, which passed, which failed, why. S4's sentence —
"adding a starter causes beans to exist" — now has its middle: starters bring libraries,
and libraries make auto-configuration tests pass.

### Do it yourself

**Step 1.** In the Lesson 1 project, replace the whole content of
`src/main/java/com/zenith/autoconfig/AutoconfigApplication.java` with the census version
shown above.

**Step 2.** Build and run:

```bash
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
mvn -q package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar
```

Expected: startup logs, then the census block shown in the walkthrough. The app keeps
running (it is a web server). Stop it with `Ctrl+C`.

**Failure case worth seeing.** Bean names are exact strings. Misspell the name inside
`getBeanDefinition(...)` — say `jacksonObjectMaper`, one `p` — and the app starts, prints
the first lines, then crashes with:

```
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No bean named 'jacksonObjectMaper' available
```

Decoded: "you asked for a bean by name, and no bean has that exact name."
`containsBean` with a wrong name does not crash — it just returns `false`.

**Done when:** you see `declared by` naming `JacksonAutoConfiguration`, and you can say
in one sentence who created the 157 beans you did not write.

### Practice questions

**Question 1.** A Spring Boot project contains exactly one Java file:

```java
@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ShopApplication.class, args);
        System.out.println(context.getBeanDefinitionCount());
    }
}
```

Its `pom.xml` declares one dependency, `spring-boot-starter-web`. When run, it prints
`158`. The team wrote zero bean-declaring methods. Where did the other 157 beans come
from? Name the jar that carries their recipes, and say how that jar got into the
project.

*Answer:* They come from **auto-configuration**: prewritten configuration classes that
ship inside the `spring-boot-autoconfigure` jar. That jar was never declared by the
team. It is a transitive dependency: `spring-boot-starter-web` lists
`spring-boot-starter`, and `spring-boot-starter` lists `spring-boot-autoconfigure`. At
startup, Spring Boot walks the 156 classes in that jar, checks each one's conditions,
and runs the ones that pass. Their bean-declaring methods created the 157 extra beans —
the web server setup, the request router, the JSON converter, and the rest.

**Question 2.** A running application's context contains these two beans:

- `eventController` — from the class `com.zenith.events.EventController`, which a
  teammate wrote and marked with `@RestController`.
- `jacksonObjectMapper` — a JSON converter bean that nobody on the team wrote.

Two different mechanisms put these two beans into the context. Name the mechanism for
each bean, and say what each mechanism reads to find its beans.

*Answer:* `eventController` came from **component scan**. Component scan reads *your own
packages* — the packages under the class marked `@SpringBootApplication` — looking for
classes the team annotated (`@RestController`, `@Component`, and friends). It registers
what it finds. `jacksonObjectMapper` came from **auto-configuration**. Auto-configuration
reads a *fixed, shipped list* of 156 configuration classes inside the
`spring-boot-autoconfigure` jar, tests each class's conditions, and applies the ones
that pass; `JacksonAutoConfiguration` passed and declared this bean. In short: component
scan discovers *your* code; auto-configuration applies *Spring Boot's* prewritten
recipes.

### Summary for revision

- Your context = your beans + beans from every auto-configuration class that passed its
  conditions.
- The recipes live in `spring-boot-autoconfigure` (156 classes), which arrives
  transitively through any starter.
- Each bean records its creator; `getBeanDefinition(name).getFactoryBeanName()` reads
  it. For `jacksonObjectMapper` it names `JacksonAutoConfiguration`.
- Component scan finds your annotated classes; auto-configuration applies Boot's shipped
  classes. Different sources, same context.
- Real numbers from the scratch app: 158 beans total, 1 written by us.

---

## Lesson 3 — `@ConditionalOnClass`: "is this library present?" (serves S3, S4)

### Definition

The **classpath** is the list of places where the running JVM looks for classes: your
own compiled classes, plus every jar Maven fetched. "A library is on the classpath"
means its jar is in that list, so its classes can be found by name.

**`@ConditionalOnClass`** guards a bean recipe. It means: "only create this if a certain
class can be found on the classpath." Found → the recipe runs. Not found → the recipe is
skipped, silently.

### Why it is needed

Spring Boot holds 156 prewritten configuration classes — recipes for Jackson, Tomcat,
databases, Redis, Mongo, and more. Applying all 156 blindly would be a disaster twice
over. Most would crash: the Redis recipe builds objects from Redis classes, and an app
without Redis has none. And it would be nonsense: a Redis connection bean in an app that
never talks to Redis.

So every recipe needs an on/off switch. `@ConditionalOnClass` is that switch, and it is
a clever one: **the presence of a library is treated as intent to use it**. A class like
`ObjectMapper` can only be on your classpath because someone put `jackson-databind` into
the build — usually through a starter. Nobody adds a library for decoration.

### How it works

At startup, for each guarded recipe:

1. Spring reads the annotation and gets a class name, such as
   `com.fasterxml.jackson.databind.ObjectMapper`.
2. It asks the classpath: "can a class with this exact name be found?" A lookup by name
   only. The library is not started or configured.
3. Found → condition passes → the recipe's bean methods execute.
4. Not found → the recipe is skipped. No error, no warning. The conditions report
   (Lesson 5) records the reason; the log stays quiet.

### The example — the same annotation, in our own hands

`PresenceDemoConfig.java` in the scratch app:

```java
package com.zenith.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PresenceDemoConfig {

    // Jackson IS on the classpath (the web starter brought it).
    // This condition passes, so this bean is created.
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    String jacksonPresentNote() {
        return "Jackson is present, so this bean was created";
    }

    // The Postgres driver is NOT on the classpath.
    // A class literal would not even compile, so the name is written as a string.
    // This condition fails, so Spring silently skips this bean.
    @Bean
    @ConditionalOnClass(name = "org.postgresql.Driver")
    String postgresPresentNote() {
        return "Postgres driver is present, so this bean was created";
    }
}
```

Two spellings. For Jackson: `ObjectMapper.class`, a class literal, allowed because our
code compiles against Jackson. For Postgres: `name = "org.postgresql.Driver"`, a string,
because our code cannot mention a class it does not have.

Two census lines added to the main method:

```java
System.out.println("jacksonPresentNote exists  : " + context.containsBean("jacksonPresentNote"));
System.out.println("postgresPresentNote exists : " + context.containsBean("postgresPresentNote"));
```

### Walkthrough — the real run

```
--- bean census ---
total beans in the context : 160
beans we wrote             : 1 (autoconfigApplication)
jacksonObjectMapper exists : true
dispatcherServlet exists   : true
jacksonPresentNote exists  : true
postgresPresentNote exists : false
```

- Last lesson the context had 158 beans. Now **160**: the config class became a bean,
  and `jacksonPresentNote` became a bean.
- `jacksonPresentNote` exists: its guard asked for `ObjectMapper`, which the web starter
  provides.
- `postgresPresentNote` is false: its guard asked for `org.postgresql.Driver`, which is
  absent. The bean was never created. The logs said **nothing** — a failed condition is
  a silent skip.

The receipt from Boot itself, printed from the real `JacksonAutoConfiguration` class
file inside the autoconfigure jar (via `javap`, Java's class-file inspector):

```
RuntimeVisibleAnnotations:
  0: org.springframework.boot.autoconfigure.AutoConfiguration
  1: org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
      value=[class Lcom/fasterxml/jackson/databind/ObjectMapper;]
    )
```

The class that created `jacksonObjectMapper` is marked "I am an auto-configuration" and
"apply me only if `ObjectMapper` is present". The same annotation we just used.

### Where it is used

On nearly all 156 recipes. Jackson's recipe is guarded by `ObjectMapper`. The database
recipe by database classes. The Redis recipe by the Redis client class. This is why one
`pom.xml` line changes behavior: a new jar appears, and guards that used to fail now
pass.

### Common mistakes

- **Expecting an error when the class is missing.** There is none; the recipe is skipped
  silently. That silence is why the conditions report exists.
- **Writing a class literal for a class you do not have.** Tried for real:
  `@ConditionalOnClass(org.postgresql.Driver.class)` fails the build with

  ```
  [ERROR] PresenceDemoConfig.java:[23,39] package org.postgresql does not exist
  ```

  Your code can only name classes it compiles against. For absent classes use
  `name = "..."`. Boot's own recipes may use literals because Spring reads their
  annotations from the class file without running them.
- **Thinking the check starts the library.** It is a name lookup, nothing more.

### Link to the task

The heart of S4: "this library is on the classpath, therefore configure the obvious
thing." Prepares S3: positive report entries for `JacksonAutoConfiguration` and negative
ones for Redis/Mongo will cite `@ConditionalOnClass` with a found or not-found reason.

### Do it yourself

**Step 1.** Create `src/main/java/com/zenith/autoconfig/PresenceDemoConfig.java` with
the content shown above.

**Step 2.** In `AutoconfigApplication.java`, after the `dispatcherServlet` census line,
add:

```java
System.out.println("jacksonPresentNote exists  : " + context.containsBean("jacksonPresentNote"));
System.out.println("postgresPresentNote exists : " + context.containsBean("postgresPresentNote"));
```

**Step 3.** Build and run:

```bash
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
mvn -q package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar
```

Expected: `jacksonPresentNote exists : true`, `postgresPresentNote exists : false`,
total `160`. Stop with `Ctrl+C`.

**Failure case.** Change the Postgres guard to
`@ConditionalOnClass(org.postgresql.Driver.class)` and build. Expected:

```
[ERROR] PresenceDemoConfig.java:[23,39] package org.postgresql does not exist
```

Change it back to the string form and confirm the build is green.

**Done when:** both beans behave as predicted, and you can say why one guard could use
`ObjectMapper.class` while the other had to use a string.

### Practice questions

**Question 1.** An application's `pom.xml` declares only `spring-boot-starter-web`,
which puts Jackson (including `com.fasterxml.jackson.databind.ObjectMapper`) on the
classpath. Nothing Redis-related is in the build. The app contains this complete
configuration class:

```java
@Configuration
public class NotesConfig {

    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    String noteA() { return "A"; }

    @Bean
    @ConditionalOnClass(name = "io.lettuce.core.RedisClient")
    String noteB() { return "B"; }
}
```

After startup: does `noteA` exist? Does `noteB` exist? What does the log print about the
one that does not exist — an error, a warning, or nothing?

*Answer:* `noteA` exists: its guard asks for `ObjectMapper`, and the web starter put
Jackson on the classpath, so the condition passes. `noteB` does not exist: its guard
asks for `io.lettuce.core.RedisClient`, and no Redis library is in the build, so the
condition fails. The log prints **nothing** about `noteB`. A failed condition is a
silent skip — not an error, not a warning. The only place the skip is recorded with its
reason is the conditions report.

**Question 2.** You change one file only: you add `spring-boot-starter-data-jpa` to
`pom.xml`. You change no Java code. After a restart, a connection-pool bean exists that
did not exist before. Explain the chain from the `pom.xml` line to the new bean, using
the words **classpath** and **condition**.

*Answer:* The starter is a shopping list. Adding it makes Maven fetch the database
libraries it lists (JPA, Hibernate, a connection pool), so their jars join the
**classpath**. Spring Boot's recipes did not change — the same 156 classes are walked at
every startup. What changed is the outcome of their tests: a database recipe guarded by
`@ConditionalOnClass` on database classes used to fail its **condition**, because those
classes were absent. Now the classes are present, the condition passes, the recipe runs,
and its bean methods create the connection-pool bean. One `pom.xml` line changed the
classpath; the changed classpath changed which conditions pass; the changed conditions
changed which beans exist.

### Summary for revision

- Classpath = the JVM's list of places to find classes: your classes + every fetched
  jar.
- `@ConditionalOnClass` = "create this only if that class can be found." Found → recipe
  runs; not found → silent skip, no log line.
- Presence means intent: a library is only on the classpath because someone added it.
- In your own code, guard with `name = "some.Class"` for classes you do not compile
  against; a class literal for an absent class does not compile.
- Real evidence: `JacksonAutoConfiguration` carries
  `@ConditionalOnClass(ObjectMapper.class)` in its class file; our demo beans behaved
  exactly the same way (`true` / `false`, total 158 → 160).

---

## Lesson 4 — `@ConditionalOnMissingBean`: the default that yields (serves S5)

### Definition

**`@ConditionalOnMissingBean`** guards a bean recipe. It means: "create this bean **only
if no bean of this type exists yet**." With no arguments on a bean method, the checked
type is the method's return type.

When a recipe decides not to act because you already supplied your own bean, that is
called **backing off**.

### Why it is needed

Defaults are only good if you can take over. Imagine the guard did not exist. Boot would
always create its `ObjectMapper` bean. The day you declare your own, the context holds
two beans of the same type, and any class asking for "the" `ObjectMapper` by type gets
an ambiguity error (the duplicate-bean problem from F6.3). Your only escape would be
switching whole features off.

With the guard, the default only exists if you did not bother to provide one. You never
fight the framework; you declare your bean, and the framework's bean steps aside.

### How it works

Order is the trick:

1. Spring registers all **your** beans first — annotated classes and configuration
   classes.
2. Auto-configuration recipes are evaluated **after** that, deliberately.
3. A guarded recipe asks: "is there already a bean of this type?"
4. No → condition passes → the default is created.
5. Yes → condition fails → the recipe backs off. Silently.

Because your beans always register before step 2, you always win.

### The example — Boot's recipe versus our bean

Boot's real recipe, printed from the class file inside the autoconfigure jar:

```
com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper(...);
    RuntimeVisibleAnnotations:
      0: org.springframework.context.annotation.Bean
      1: org.springframework.context.annotation.Primary
      2: org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
```

Our challenger, `BackoffDemoConfig.java`:

```java
package com.zenith.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackoffDemoConfig {

    // Our own ObjectMapper bean. No conditions, no tricks.
    // Boot's recipe for jacksonObjectMapper is guarded by @ConditionalOnMissingBean,
    // so the moment this bean exists, Boot's recipe must back off.
    @Bean
    ObjectMapper myObjectMapper() {
        return new ObjectMapper();
    }
}
```

The census, made vanish-proof (a backed-off bean has no name to look up):

```java
System.out.println("myObjectMapper exists      : " + context.containsBean("myObjectMapper"));
if (context.containsBean("jacksonObjectMapper")) {
    System.out.println("jacksonObjectMapper        : present, declared by "
            + context.getBeanFactory().getBeanDefinition("jacksonObjectMapper").getFactoryBeanName());
} else {
    System.out.println("jacksonObjectMapper        : not present (Boot backed off; another ObjectMapper bean exists)");
}
```

### Walkthrough — two real runs

**Run A — without `BackoffDemoConfig`:**

```
total beans in the context : 160
myObjectMapper exists      : false
jacksonObjectMapper        : present, declared by org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration$JacksonObjectMapperConfiguration
```

No `ObjectMapper` bean existed when the recipe was evaluated, so the guard passed and
Boot's default was created.

**Run B — with `BackoffDemoConfig`:**

```
total beans in the context : 161
myObjectMapper exists      : true
jacksonObjectMapper        : not present (Boot backed off; another ObjectMapper bean exists)
```

- Our bean registered first (user beans always come first).
- Boot's recipe then found an existing `ObjectMapper` and backed off.
- Arithmetic: 160 → 161. Two definitions added (config class + our mapper), one of
  Boot's vanished. A **replacement**, not an addition.
- The log printed no warning. The framework yielded in silence.

### Where it is used

On almost every default bean among the 156 recipes. The two guards work together:
`@ConditionalOnClass` asks "should this area be configured at all?";
`@ConditionalOnMissingBean` asks "has the developer already done it?". Together they are
Spring Boot's personality: configure the obvious thing, unless the developer already
did.

### Common mistakes

- **Looking for a switch to disable an auto-configuration before customizing.** Not
  needed. Declare your bean; the default yields.
- **Expecting a merge.** Your bean replaces the default completely. None of Boot's
  default settings carry over to your `new ObjectMapper()`.
- **Assuming the old bean name still exists.** Fetching `jacksonObjectMapper` by name
  while a custom mapper exists crashes with

  ```
  Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No bean named 'jacksonObjectMapper' available
  ```

  Fetch by type (`context.getBean(ObjectMapper.class)`) and you get yours.
- **Using `@ConditionalOnMissingBean` between your own configuration classes.** Order
  among user classes is not guaranteed. The trick is reliable only because
  auto-configuration runs after all user beans.

### Link to the task

The mechanism of S5, shown on the exact bean the task names. Lesson 9 repeats it with a
properly configured ObjectMapper (ISO-8601 dates) plus the conditions report naming the
backed-off recipe. Answers checkpoint question 3.

### Do it yourself

**Step 1.** In `AutoconfigApplication.java`, replace the `jacksonObjectMapper exists`
and `declared by` census lines with the vanish-proof block above. Remove the
`beans we wrote` line.

**Step 2.** Build and run. Expect Run A's output.

```bash
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
mvn -q package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar
```

**Step 3.** Create `BackoffDemoConfig.java` as above. Build and run again. Expect Run
B's output, with the total up by exactly 1.

**Failure case.** Add this line after the census, build, run:

```java
context.getBeanFactory().getBeanDefinition("jacksonObjectMapper");
```

Expected crash: `NoSuchBeanDefinitionException: No bean named 'jacksonObjectMapper'
available` — the backed-off bean was never created, so its name does not exist. Remove
the line.

**Step 4 — clean up.** Delete `BackoffDemoConfig.java`, rebuild, confirm
`jacksonObjectMapper : present ...` again. Lesson 9 rebuilds the custom mapper properly.

**Done when:** you watched the same bean be present, absent, and present again, and can
explain each flip in one sentence about the guard.

### Practice questions

**Question 1.** Spring Boot ships this recipe method:

```java
@Bean
@Primary
@ConditionalOnMissingBean
ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) { ... }
```

Application A declares no `ObjectMapper` bean anywhere. Application B contains:

```java
@Configuration
public class JsonConfig {
    @Bean
    ObjectMapper reportMapper() {
        return new ObjectMapper();
    }
}
```

For each application, does the bean named `jacksonObjectMapper` exist after startup? For
application B, give the single word for what Boot's recipe did.

*Answer:* Application A: **yes**. When the recipe is evaluated, no `ObjectMapper` bean
exists, the guard passes, and `jacksonObjectMapper` is created. Application B: **no**.
`reportMapper` is a user bean, so it registers before auto-configuration runs. The guard
then finds an existing `ObjectMapper` and the recipe does not run — it **backed off**.
Application B still has exactly one mapper: `reportMapper`.

**Question 2.** A teammate added his own `ObjectMapper` bean to customize JSON. Since
then, this old line crashes at startup:

```java
ObjectMapper mapper = (ObjectMapper) context.getBean("jacksonObjectMapper");
```

The error: `NoSuchBeanDefinitionException: No bean named 'jacksonObjectMapper'
available`. Why does that name no longer exist, and why does this work instead?

```java
ObjectMapper mapper = context.getBean(ObjectMapper.class);
```

*Answer:* Boot's recipe for `jacksonObjectMapper` is guarded by
`@ConditionalOnMissingBean`. The teammate's mapper registered first, the guard found it,
and Boot's bean was **never created**. A bean that was never created has no name in the
context, so fetching by the name `jacksonObjectMapper` fails. Fetching by **type** asks
for "the one `ObjectMapper` bean, whatever its name". Exactly one exists — the
teammate's — so Spring returns it. Replacement removed the old name but not the type.

### Summary for revision

- `@ConditionalOnMissingBean` = "create this only if no bean of this type exists yet."
- User beans register first; auto-configuration evaluates after. That order is why your
  bean always wins.
- Backing off = the recipe skipping itself because your bean exists. Silent, like all
  conditions.
- Replacement, not merge: Boot's defaults do not carry over to your bean. And not
  addition: the context still holds exactly one bean of the type (160 → 161 total, with
  one of Boot's gone).
- Fetch replaced beans by type, not by Boot's old bean name.
- `@ConditionalOnClass` + `@ConditionalOnMissingBean` together: "configure the obvious
  thing, unless the developer already did."

---

## Lesson 5 — The conditions report: the receipt for every decision (subtask S2)

### Definition

The **conditions report** is a printout of every condition decision Spring Boot made at
startup: for each auto-configuration recipe, did its conditions pass or fail, and for
exactly what reason. You get it by starting the app in debug mode.

### Why it is needed

A failed `@ConditionalOnClass` is a silent skip. A backed-off
`@ConditionalOnMissingBean` is a silent skip. The normal log never mentions them. So
when a bean you expected is missing, or an unexpected one appears, the log has nothing.
The report ends the guessing: every decision is written down with its reason.

### How it works

Boot **always** collects these decisions during startup. The `--debug` flag tells it to
also **print** the collected report once startup finishes. Behavior does not change;
you just get the receipt. The output is long, so send it to a file and search the file.

Four sections, always in this order:

| Section | Meaning |
| --- | --- |
| **Positive matches** | Recipes whose conditions all passed. Their beans exist. |
| **Negative matches** | Recipes where a condition failed. Skipped. The longest section, and that is healthy — most of the 156 recipes should not apply to your app. |
| **Exclusions** | Recipes you explicitly told Boot to ignore. `None` unless you excluded something. |
| **Unconditional classes** | The few recipes with no conditions. Always applied. |

### The example — generating and mapping the report

```bash
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > debug-run.log 2>&1
# Ctrl+C after it settles

wc -l debug-run.log
grep -n "CONDITIONS EVALUATION REPORT\|Positive matches:\|Negative matches:\|Exclusions:\|Unconditional classes:" debug-run.log
```

Real result:

```
total lines: 1027

39:CONDITIONS EVALUATION REPORT
43:Positive matches:
307:Negative matches:
992:Exclusions:
998:Unconditional classes:
```

About 260 lines of positive matches and about 680 lines of negative matches. Two thirds
of the report is recipes that did not apply — correct for a small app.

### Walkthrough — a tour of the real file

The banner and the first positive entry:

```
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------

   AopAutoConfiguration matched:
      - @ConditionalOnBooleanProperty (spring.aop.auto=true) matched (OnPropertyCondition)
```

The first negative entry — Lesson 3's sentence, word for word:

```
Negative matches:
-----------------

   ActiveMQAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'jakarta.jms.ConnectionFactory' (OnClassCondition)
```

The tail:

```
Exclusions:
-----------

    None

Unconditional classes:
----------------------

    org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
```

And the payoff — our own Lesson 3 beans, explained in writing:

```
   PresenceDemoConfig#jacksonPresentNote matched:
      - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper' (OnClassCondition)

   PresenceDemoConfig#postgresPresentNote:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.postgresql.Driver' (OnClassCondition)
```

The decisions we predicted in the census (`true` / `false`) are here with the exact class
names the guards looked for.

### Where it is used

- Answering "why does this bean exist / not exist?" with evidence.
- Comparing two runs: report before a change, report after, diff the files.
- The same report on a running app: the actuator endpoint `/actuator/conditions`
  (Lesson 14).

### Common mistakes

- **Treating negative matches as errors.** Negative is the normal, healthy state for
  most recipes. Nothing failed.
- **Reading the report in the terminal.** It scrolls away. Redirect to a file.
- **Expecting `--debug` to flood everything with debug logs.** It switches a few core
  loggers and prints this report. Your own code's logging is unchanged.
- **Searching for `ERROR`.** The report contains reasons, not errors. Search for recipe
  names or section headers.

### Do it yourself

**Step 1.** Build, run with the flag, output to a file:

```bash
cd ~/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
mvn -q package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > debug-run.log 2>&1
```

`Ctrl+C` after it settles.

**Step 2.** Map the file:

```bash
wc -l debug-run.log
grep -n "CONDITIONS EVALUATION REPORT\|Positive matches:\|Negative matches:\|Exclusions:\|Unconditional classes:" debug-run.log
```

Expected: roughly 1000 lines; five hits in the same order as above.

**Step 3.** Open with `less debug-run.log`. Type `/CONDITIONS` + Enter to jump to the
report, `n` for next match, `q` to quit. Walk each section once.

**Step 4.** Find our beans: `grep -n "PresenceDemoConfig" debug-run.log` — one hit in
positive matches, one in negative matches.

**Failure case.** Run without `--debug` into `plain-run.log`, then
`grep -c "CONDITIONS EVALUATION REPORT" plain-run.log`. Expected: `0`. No flag, no
report — the decisions happened but were not printed.

**Done when:** you can name the four sections from memory and point at each one's line
number in your own `debug-run.log`.

### Practice questions

**Question 1.** A teammate's Spring Boot app is missing a bean he expected (a Redis
connection bean). He starts the app the normal way with `java -jar app.jar`, reads every
log line, finds no error, and concludes: "Spring is broken — the bean just silently
vanished." He is half right: it was silent. What exact command should he run instead, in
which of the four report sections should he look, and why was there nothing about it in
his normal logs?

*Answer:* Run `java -jar app.jar --debug > debug-run.log 2>&1`, then open
`debug-run.log` and find the `CONDITIONS EVALUATION REPORT`. Look in **Negative
matches** and search for the Redis recipe (for example with
`grep -n -i redis debug-run.log`). The entry will state the exact failed condition, such
as `@ConditionalOnClass did not find required class 'io.lettuce.core.RedisClient'`.
Nothing was in the normal logs because a failed condition is a **silent skip by
design** — not an error, so nothing is logged. The decisions are always recorded, but
only `--debug` prints them.

**Question 2.** Here is a complete excerpt from a conditions report:

```
Positive matches:
   JacksonAutoConfiguration matched:
      - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper' (OnClassCondition)

Negative matches:
   MongoAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.mongodb.client.MongoClient' (OnClassCondition)

Exclusions:
    None
```

Three claims. For each, say true or false, and point at the line that proves it:
(a) The app tried to connect to MongoDB and the connection failed, which is why Mongo
appears in negative matches.
(b) The Jackson library is on the app's classpath.
(c) A developer excluded the Mongo auto-configuration somewhere in the code.

*Answer:* (a) **False.** The reason line says `@ConditionalOnClass did not find required
class 'com.mongodb.client.MongoClient'`. That is a classpath lookup that failed, done
before any Mongo code could run. No connection was ever attempted; the recipe was
skipped because the library is absent. (b) **True.** The positive entry says
`@ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper'`
— "found" means the class is on the classpath. (c) **False.** The `Exclusions` section
says `None`. Mongo is in negative matches because of the classpath test, not because
anyone excluded it; an exclusion would be listed under `Exclusions`.

### Summary for revision

- The report = every condition decision, with reasons. Generated with
  `java -jar app.jar --debug`, always redirected to a file.
- Four sections: Positive matches (beans exist), Negative matches (skipped — normal,
  not errors), Exclusions (you switched off — usually `None`), Unconditional classes
  (always applied).
- Real shape for our app: 1027-line file; positive at line 43, negative at 307,
  exclusions `None`.
- The report also covers your own conditional beans (`PresenceDemoConfig` appeared in
  both sections).
- No `--debug`, no printout — but the decisions still happened.

---

## Lesson 6 — Reading a positive match, word by word (subtask S3, part 1)

### Definition

A **positive match entry** is one block in the report's Positive matches section. It
names a recipe that was applied, and lists every condition that passed, each with its
reason.

### Why it is needed

Skimming tells you *that* something matched. Reading word by word tells you *why* — and
"why" is what you need when behavior surprises you. Each line is a full sentence of
evidence squeezed into one row.

### How it works — the anatomy

**The name** comes in three shapes:

| Name shape | Points at | Example |
| --- | --- | --- |
| `Outer` | A whole recipe class | `JacksonAutoConfiguration` |
| `Outer.Nested` | A smaller recipe class nested inside | `JacksonAutoConfiguration.JacksonObjectMapperConfiguration` |
| `Outer.Nested#method` | **One single bean method** | `...JacksonObjectMapperConfiguration#jacksonObjectMapper` |

The `#method` entries are single beans.

**The condition lines** all follow one pattern:

```
- @GuardName   <reason sentence>   (CheckerName)
```

Several condition lines under one name = **all had to pass** (AND). One failure anywhere
would move the entry to Negative matches.

**The family logic:** the outer class is tested first. Only if it matches are nested
classes tested, then methods. A failed outer guard silently takes the whole family down.

### The example — the real Jackson family

```
   JacksonAutoConfiguration matched:
      - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper' (OnClassCondition)

   JacksonAutoConfiguration.JacksonObjectMapperConfiguration matched:
      - @ConditionalOnClass found required class 'org.springframework.http.converter.json.Jackson2ObjectMapperBuilder' (OnClassCondition)

   JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper matched:
      - @ConditionalOnMissingBean (types: com.fasterxml.jackson.databind.ObjectMapper; SearchStrategy: all) did not find any beans (OnBeanCondition)
```

### Walkthrough

- **Entry 1, the outer class.** The subtask's exact target: the condition that made
  Jackson's recipe apply. The guard needed `ObjectMapper`; the web starter provided it;
  found. Because this matched, the rest of the family was evaluated at all.
- **Entry 2, a nested class.** A different required class, `Jackson2ObjectMapperBuilder`
  (a Spring web class). Recipes can depend on several libraries.
- **Entry 3, the bean method.** `#jacksonObjectMapper` is the recipe for the exact bean
  the census watches. Guard: `@ConditionalOnMissingBean`. `types:` = what it searched
  for; `SearchStrategy: all` = searched the whole context. Verdict: `did not find any
  beans` — **the success sentence**. The guard wanted to find nothing; nothing found
  means no developer supplied a mapper, so the default was created.

A second real entry with **three** conditions:

```
   JacksonHttpMessageConvertersConfiguration.MappingJackson2HttpMessageConverterConfiguration matched:
      - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper' (OnClassCondition)
      - @ConditionalOnPreferredJsonMapper JACKSON no property was configured and Jackson is the default (OnPreferredJsonMapperCondition)
      - @ConditionalOnBean (types: com.fasterxml.jackson.databind.ObjectMapper; SearchStrategy: all) found bean 'jacksonObjectMapper' (OnBeanCondition)
```

This recipe creates the converter that turns controller return values into JSON
responses. All three passed. The third guard is `@ConditionalOnBean` — the mirror of
Lesson 4's guard: "apply only if that bean *does* exist." It found `jacksonObjectMapper`.

**Recipes chain:** the converter exists because the mapper bean exists; the mapper
exists because the Jackson class is on the classpath; the class is there because of one
starter line. Every link is written in the report.

### Where it is used

Tracing any bean: search Positive matches for the recipe or method name, read each
condition as a sentence, follow `@ConditionalOnBean` references to other entries.
Explains cascades: one dependency flips one guard, whose bean flips the next guard.

### Common mistakes

- **Reading "did not find any beans" as failure.** Under `matched:`, it is the passing
  outcome of `@ConditionalOnMissingBean`. Read whole sentences.
- **Misreading the "not found" direction.** `did not find required class` = failure;
  `did not find unwanted class` = success (that guard is `@ConditionalOnMissingClass`).
  The word before the quoted name — `required` or `unwanted` — gives the direction.
- **Stopping at the class-level entry.** The class can match while one `#method` backs
  off. Hunt the `#method` entry for a specific bean.

### Do it yourself

Needs `debug-run.log` from Lesson 5 (regenerate with `--debug` if deleted).

**Step 1.** Extract the Jackson family:

```bash
grep -n -A2 "JacksonAutoConfiguration matched\|JacksonObjectMapperConfiguration matched\|#jacksonObjectMapper matched" debug-run.log
```

**Step 2.** Read each entry aloud as: "Recipe X was applied because guard Y checked Z
and the answer was: …".

**Step 3.** Extract the converter entry and find its `@ConditionalOnBean` line:

```bash
grep -n -A4 "MappingJackson2HttpMessageConverterConfiguration matched" debug-run.log
```

**Failure case.** `grep -c "jacksonautoconfiguration" debug-run.log` → `0`. `grep` is
case-sensitive; use exact case or `-i`.

**Done when:** you can explain every piece of the `#jacksonObjectMapper` entry: the `#`,
the guard, `types:`, `SearchStrategy`, and why "did not find any beans" made the bean
exist.

### Practice questions

**Question 1.** One complete, real entry from Positive matches:

```
   AopAutoConfiguration.ClassProxyingConfiguration matched:
      - @ConditionalOnMissingClass did not find unwanted class 'org.aspectj.weaver.Advice' (OnClassCondition)
      - @ConditionalOnBooleanProperty (spring.aop.proxy-target-class=true) matched (OnPropertyCondition)
```

(a) How many separate tests guarded this recipe, and what relationship must hold between
them? (b) The first line says a class was *not found*, yet the entry is positive —
explain. (c) What does the name `AopAutoConfiguration.ClassProxyingConfiguration` point
at — a whole recipe class, a nested recipe class, or a single bean method?

*Answer:* (a) Two tests, and **both** had to pass — conditions on one entry combine with
AND. One failure would have moved the whole entry to Negative matches. (b) The guard is
`@ConditionalOnMissingClass`: it wants the class to be **absent** — the reason sentence
says "unwanted class". Not finding an unwanted class is exactly what passing looks like
for this guard. (Compare: "did not find **required** class" would be a failure.) (c) A
nested recipe class: the name has an `Outer.Nested` shape and no `#`, so it is not a
single bean method.

**Question 2.** Another complete, real entry:

```
   JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper matched:
      - @ConditionalOnMissingBean (types: com.fasterxml.jackson.databind.ObjectMapper; SearchStrategy: all) did not find any beans (OnBeanCondition)
```

(a) What exactly does `#jacksonObjectMapper` point at? (b) The condition reports "did
not find any beans" — is that a problem? What did it cause? (c) Describe one change that
would move this entry to Negative matches, and give the one word for what the recipe
would then have done.

*Answer:* (a) A single bean method: the method named `jacksonObjectMapper` inside the
nested configuration class — the recipe for one specific bean. (b) Not a problem — it is
the guard's passing outcome. `@ConditionalOnMissingBean` searched the whole context for
a bean of type `ObjectMapper`, found none, so the condition passed and **the default
bean was created**. That is why `jacksonObjectMapper` exists. (c) Declare your own
`ObjectMapper` bean anywhere in the app, for example `@Bean ObjectMapper myObjectMapper()
{ return new ObjectMapper(); }`. The guard would then find a bean, the condition would
fail, and the entry would appear under Negative matches. The word: **backed off**.

### Summary for revision

- Entry anatomy: name (`Outer`, `Outer.Nested`, or `Outer.Nested#method`) + condition
  lines (`- @Guard reason (Checker)`).
- `#method` entries are single beans. Always check them when hunting a bean.
- Multiple conditions on one entry = AND. All must pass.
- Family logic: outer first; a failed outer silently removes the whole family.
- Read whole sentences: "did not find any beans" under `matched:` is success for
  `@ConditionalOnMissingBean`; `required` vs `unwanted` gives the direction.
- `@ConditionalOnBean` is the mirror guard ("apply only if that bean exists") — it makes
  recipes chain, and the report documents every link.

---

## Lesson 7 — Negative matches and exclusions (subtask S3, part 2)

### Definition

A **negative match entry** names a recipe that was **not** applied. Under
`Did not match:` it lists the failed condition with its reason. Some entries also have a
`Matched:` subsection: conditions that passed before the failing one ended it.

The **Exclusions** section lists recipes **you ordered Boot to ignore**. Those were
never tested at all.

### Why it is needed

The most common real question is "why is this bean missing?" or "why did my change not
take effect?". The answer is always a failed condition, and failed conditions are silent
in the normal log. The negative section explains every skip — and shows how close a
recipe was to applying.

### How it works

A recipe lands in Negative matches when at least one condition fails. Two details:

- **`Matched:` subsection.** Conditions combine with AND. The entry can show passed
  conditions under `Matched:` and the failing one under `Did not match:`. Act on the
  failed line.
- **Evaluation stops early.** Conditions run cheapest first; the recipe is abandoned at
  the first failure. The entry may not list every problem — fix one, re-run, re-read.

Exclusions: made with `@SpringBootApplication(exclude = SomeAutoConfiguration.class)` or
the setting `spring.autoconfigure.exclude`. An excluded recipe is **never evaluated** —
no conditions run, so it appears in neither match section. Only the Exclusions list
records it.

### The example — four real entries

```
   RedisAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.springframework.data.redis.core.RedisOperations' (OnClassCondition)

   MongoAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.mongodb.client.MongoClient' (OnClassCondition)

   DataSourceAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)

   CacheAutoConfiguration:
      Did not match:
         - @ConditionalOnBean (types: org.springframework.cache.interceptor.CacheAspectSupport; SearchStrategy: all) did not find any beans of type org.springframework.cache.interceptor.CacheAspectSupport (OnBeanCondition)
      Matched:
         - @ConditionalOnClass found required class 'org.springframework.cache.CacheManager' (OnClassCondition)
```

And the tail: `Exclusions:` → `None`.

### Walkthrough

- **Redis.** The recipe asked the classpath for `RedisOperations`. Not found, skipped.
  No connection was attempted, nothing timed out — the recipe was abandoned before any
  Redis code could run. Absence of the library = absence of intent.
- **Mongo.** Same sentence, different class.
- **DataSource.** The recipe that would create a database connection pool, skipped
  because a JDBC class is missing. This exact entry flips to positive in Lesson 8 when
  one starter line is added.
- **Cache, the mixed one.** The class guard **passed** (`CacheManager` is a core Spring
  class, present). The bean guard **failed**: `@ConditionalOnBean` wanted to find a bean
  and found none — for that guard, "did not find any beans" is a failure. One failed
  condition sank the recipe despite the pass. Practical difference: a failed **class**
  guard is fixed by adding a library; a failed **bean** guard is fixed by something
  declaring that bean.
- **Exclusions: None.** Nothing ordered off. Lesson 10 changes that.

### Where it is used

- A bean is missing: find its recipe in Negative matches, read the failed guard, apply
  the matching fix.
- Prove a dependency removal worked: its recipes should now be negative.
- Remember old exclusions: the Exclusions list never forgets.

### Common mistakes

- **Alarm at the volume.** ~680 negative lines for our tiny app. Healthy: most of the
  156 recipes should not apply.
- **Misreading "did not find any beans of type X" as a missing library.** That is a
  **bean** guard failing. The library may be present — the Cache entry proves it. Check
  which guard the failing line names.
- **Hunting excluded recipes in Negative matches.** They are only in Exclusions; they
  were never evaluated.
- **Treating the entry as the full list of problems.** Evaluation stops at the first
  failure. Fix it, re-run `--debug`, read again.

### Link to the task

Completes S3: positive match read (Lesson 6), negative matches for absent libraries
(Redis, Mongo), Exclusions noted (`None`). Arms checkpoint question 4: missing bean →
`--debug` → Negative matches → failed guard → fix matching the guard's kind.

### Do it yourself

Needs `debug-run.log` from Lesson 5.

**Step 1.** The three library entries:

```bash
grep -n -A3 "^   RedisAutoConfiguration:\|^   MongoAutoConfiguration:\|^   DataSourceAutoConfiguration:" debug-run.log
```

Read each aloud: "Recipe X was skipped because guard Y looked for Z and did not find
it."

**Step 2.** The mixed entry:

```bash
grep -n -A5 "^   CacheAutoConfiguration:" debug-run.log
```

Point at the failed line and the passed line; say which fix the failure would need
(library or bean).

**Step 3.** The exclusions: `grep -n -A3 "Exclusions:" debug-run.log` → `None`.

**Failure case.** `grep -c "Redis" debug-run.log` → far more than 10 hits. Loose
searches drown you; anchor with three spaces + exact name + colon.

**Done when:** for Redis and DataSource you can say from memory: which guard failed,
what it looked for, and what one action would flip the entry to positive.

### Practice questions

**Question 1.** One complete, real entry from Negative matches:

```
   CacheAutoConfiguration:
      Did not match:
         - @ConditionalOnBean (types: org.springframework.cache.interceptor.CacheAspectSupport; SearchStrategy: all) did not find any beans of type org.springframework.cache.interceptor.CacheAspectSupport (OnBeanCondition)
      Matched:
         - @ConditionalOnClass found required class 'org.springframework.cache.CacheManager' (OnClassCondition)
```

(a) Which guard failed, and was it looking for a class on the classpath or a bean in the
context? (b) What does the `Matched:` subsection tell you about
`org.springframework.cache.CacheManager`? (c) The entry contains a passed condition, yet
the recipe was skipped — state the rule.

*Answer:* (a) `@ConditionalOnBean` failed. It searches the **context for a bean** — here
a bean of type `CacheAspectSupport` — not the classpath for a class. None existed.
(b) That the class guard **passed**: `CacheManager` was found, so that class is on the
classpath. The library side was fine; the bean side was not. (c) Conditions on one
recipe combine with **AND**. All must pass; one failure moves the whole recipe to
Negative matches regardless of how many others matched.

**Question 2.** A teammate's app is missing its database connection pool. He generates
the conditions report and searches Negative matches for `DataSourceAutoConfiguration` —
zero hits. Positive matches — zero hits. He concludes the report is broken. The end of
his report reads:

```
Exclusions:
-----------

    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

(a) Why is the recipe in neither match section? (b) What must someone have written, and
in which two possible places? (c) What single change brings the recipe back?

*Answer:* (a) Because it was **excluded**. Excluded recipes are never evaluated: no
conditions run, so there is nothing to report as matched or not matched. The Exclusions
list is the only trace. (b) Either
`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` on the main class,
or the setting `spring.autoconfigure.exclude:
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` in
`application.yml` (or properties). (c) Remove that exclusion. On the next startup the
recipe is evaluated normally again and will appear in Positive or Negative matches with
reasons.

### Summary for revision

- Negative entry = `Did not match:` (the failed guard, with reason) + optional
  `Matched:` (guards that passed anyway).
- One failed condition sinks the recipe (AND rule); evaluation stops at the first
  failure, so fix–rerun–reread.
- Failed class guard → add the library. Failed bean guard → something must declare that
  bean. The entry names which.
- Excluded recipes are never evaluated and appear only under Exclusions.
- A long negative section is healthy, not broken.

---

## Lesson 8 — The one rule, and what JPA drags in (subtask S4)

### Definition

The rule, out loud: **"Auto-configuration is almost entirely: this library is on the
classpath, therefore configure the obvious thing."** This lesson watches the rule act
end to end.

### Why it is needed

This is the mental model that replaces magic. When an app behaves differently after a
`pom.xml` edit, this rule is the explanation: the classpath changed → different
conditions passed → different beans exist.

### How it works — the five dominoes

1. You add a **starter** line to `pom.xml`.
2. Maven fetches its shopping list — jars join the **classpath**.
3. Guards that used to fail now pass (`@ConditionalOnClass` finds its classes).
4. The newly passing **recipes** run and declare **beans**.
5. New beans flip `@ConditionalOnBean` guards, so further recipes run (chaining).

### The example — adding a database in two blocks

New in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

New file `src/main/resources/application.yml` (same local Postgres as Stage F5):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev
```

New census lines:

```java
System.out.println("dataSource exists          : " + context.containsBean("dataSource"));
System.out.println("entityManagerFactory exists: " + context.containsBean("entityManagerFactory"));
System.out.println("transactionManager exists  : " + context.containsBean("transactionManager"));
```

### Walkthrough — the real before and after

Census after the change:

```
total beans in the context : 238
postgresPresentNote exists : true
dataSource exists          : true
entityManagerFactory exists: true
transactionManager exists  : true
```

- **160 beans became 238.** Two pom blocks, 78 new beans, zero new bean code.
- **The trio exists**: `dataSource`, `entityManagerFactory`, `transactionManager`.
- **Our own Lesson 3 bean flipped.** `postgresPresentNote` was false for five lessons:

  ```
     PresenceDemoConfig#postgresPresentNote matched:
        - @ConditionalOnClass found required class 'org.postgresql.Driver' (OnClassCondition)
  ```

  The guard did not change. The classpath did.
- **Lesson 7's negative entry is now positive:**

  ```
     DataSourceAutoConfiguration matched:
        - @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)
        - @ConditionalOnMissingBean (types: io.r2dbc.spi.ConnectionFactory; SearchStrategy: all) did not find any beans (OnBeanCondition)
  ```

  And the chain continued — JPA applied because the pool bean now exists:

  ```
     HibernateJpaConfiguration matched:
        - @ConditionalOnSingleCandidate (types: javax.sql.DataSource; SearchStrategy: all) found a single bean 'dataSource' (OnBeanCondition)
  ```

- Real connection in the log:

  ```
  com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Starting...
  com.zaxxer.hikari.pool.HikariPool  : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@3b24087d
  ```

### Where it is used

Every "I added a dependency and behavior changed" story — and the reverse: removing a
dependency makes recipes fail their guards, and bean families quietly disappear.

### Common mistakes

- **Crediting `application.yml` with creating the beans.** The classpath decides
  **existence**; settings decide **details** (which URL, which password). The yml only
  parameterized the already-created pool.
- **Looking for an "enable JPA" switch.** In Boot, presence is the switch.
- **Adding half the shopping** (starter without driver, or driver without starter).
  Each fails differently; Lesson 11 reads one such failure.

### Link to the task

S4 done: the rule said out loud, and watched in action. Stages Lessons 10 and 11, which
break this setup on purpose.

### Do it yourself

**Step 0 — database up.** `docker start pg`, or create it as in F5.1:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17-alpine
docker exec pg psql -U postgres -c "CREATE DATABASE zenith"
```

(`database "zenith" already exists` is fine.)

**Step 1.** Add the two dependency blocks, the `application.yml`, and the three census
lines shown above.

**Step 2.** Build, run with `--debug` into `jpa-debug.log`, check the census:

```bash
mvn -q package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > jpa-debug.log 2>&1
# Ctrl+C after it settles
grep -A10 "bean census" jpa-debug.log
```

Expected: total ≈ 238, the trio `true`, `postgresPresentNote : true`.

**Step 3.** Confirm the flips:

```bash
grep -n -A3 "^   DataSourceAutoConfiguration matched" jpa-debug.log
grep -n -A2 "PresenceDemoConfig#postgresPresentNote" jpa-debug.log
```

**Failure case.** `docker stop pg`, start the app. Expected deep in the trace:

```
Caused by: org.hibernate.HibernateException: Unable to determine Dialect without JDBC metadata (please set 'jakarta.persistence.jdbc.url' for common cases or 'hibernate.dialect' when a custom Dialect implementation must be provided)
```

Decoded: beans were configured fine, but at startup Hibernate asks the live database
what kind it is, and nobody answered — the database is down. A runtime failure, not a
conditions failure: the report would still show the recipes positive. `docker start pg`
and the same jar starts cleanly.

**Done when:** you can recite the five dominoes from pom line to `transactionManager`,
with a line of evidence for each.

### Practice questions

**Question 1.** A project adds the JPA starter and the Postgres driver to `pom.xml` and
sets `spring.datasource.url` (plus username and password) in `application.yml`. After
restart, a bean named `dataSource` exists that did not exist before. Describe the chain
from pom edit to bean, using each of these words exactly once: **starter, classpath,
condition, recipe, bean**.

*Answer:* The **starter** line makes Maven fetch the database shopping list, so the JDBC
and JPA jars join the **classpath**. At the next startup, the pool recipe's
**condition** — `@ConditionalOnClass` on the JDBC classes — now passes instead of
failing. The **recipe** (`DataSourceAutoConfiguration`) therefore runs its methods, and
one of them declares the **bean** named `dataSource`, configured with the URL, username,
and password from `application.yml`.

**Question 2.** A teammate says: "The `dataSource` bean exists because we set
`spring.datasource.url` in `application.yml`. Delete that line and the bean simply
disappears." The JPA starter and driver stay in `pom.xml` either way. Is he right?
(a) What actually controls whether the pool recipe runs? (b) What does the `url` setting
actually control, and roughly what happens at startup if it is deleted while the recipe
still runs?

*Answer:* He is wrong on both halves. (a) Whether the recipe runs is controlled by its
**conditions**, and those check the **classpath** and the existing beans — not that
setting. With the starter and driver still present, `DataSourceAutoConfiguration` still
matches and still tries to build the pool. (b) The `url` is a **detail** the chosen
recipe reads: which database to connect to. Delete it and the bean does not quietly
disappear — startup **fails loudly**, with Boot's failure analysis explaining that it
cannot configure a DataSource because no url is set and no embedded database is on the
classpath. Existence comes from the classpath; details come from settings; missing
details produce errors, not silent absence.

### Summary for revision

- The rule: library on the classpath → configure the obvious thing.
- Five dominoes: starter → classpath → conditions pass → recipes run → beans exist (and
  new beans flip further `@ConditionalOnBean` guards).
- Real numbers: two pom blocks took the context from 160 to 238 beans and created
  `dataSource`, `entityManagerFactory`, `transactionManager`.
- Our own `postgresPresentNote` guard flipped too — same rule, our code.
- Classpath decides existence; `application.yml` decides details.
- Database down ≠ conditions failure: "Unable to determine Dialect" is a runtime error
  with all recipes still positive.

---

## Lesson 9 — Make Jackson back off, and hold the evidence (subtask S5)

### Definition

Take over a real Boot default — the application's JSON converter — by declaring your own
properly configured `ObjectMapper` bean, and prove the takeover with the conditions
report.

### Why it is needed

Real projects customize JSON: date formats, null handling, unknown fields. The right way
is declaring your own `ObjectMapper` and letting Boot back off. The hidden price:
**replacement, not merge**. Boot's default mapper understands `java.time` dates and
writes them as ISO-8601 strings; a bare `new ObjectMapper()` knows none of that.

### How it works — three runs

1. **Boot's default**: an endpoint returning a date works.
2. **Naive takeover** (`return new ObjectMapper();`): Boot backs off, and the endpoint
   breaks — the bare mapper never learned dates.
3. **Proper takeover**: register `JavaTimeModule` (teaches `java.time`), disable
   `WRITE_DATES_AS_TIMESTAMPS` (ISO-8601 strings). Works again; the report records the
   back-off.

### The example

The endpoint:

```java
@RestController
public class ClockController {

    record Now(String name, Instant time) {}

    @GetMapping("/now")
    Now now() {
        return new Now("scratch-app", Instant.now());
    }
}
```

The proper configuration (permanent in the scratch app):

```java
@Configuration
public class JsonConfig {

    // - JavaTimeModule teaches it java.time types (Instant, LocalDate, ...)
    // - disabling WRITE_DATES_AS_TIMESTAMPS makes dates ISO-8601 strings, not numbers
    @Bean
    ObjectMapper myObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

### Walkthrough — the three real outputs

**Run 1, Boot default.** `curl http://localhost:8080/now`:

```
{"name":"scratch-app","time":"2026-11-01T14:28:19.544813Z"}
```

**Run 2, naive takeover.** Same request:

```
HTTP/1.1 500
{"timestamp":1793543330599,"status":500,"error":"Internal Server Error","path":"/now"}
```

Log:

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type `java.time.Instant` not supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling ... (through reference chain: com.zenith.autoconfig.ClockController$Now["time"])
```

Decoded: Jackson refuses `java.time` types until a helper module is registered. Boot's
mapper had it; ours did not. Even the error body's `"timestamp"` became a raw number —
our bare mapper now does **all** JSON in the app.

**Run 3, proper takeover.** The ISO string is back. Census:

```
total beans in the context : 240
myObjectMapper exists      : true
jacksonObjectMapper        : not present (Boot backed off; another ObjectMapper bean exists)
```

And the report entry the task sent us to find, in **Negative matches**:

```
   JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper:
      Did not match:
         - @ConditionalOnMissingBean (types: com.fasterxml.jackson.databind.ObjectMapper; SearchStrategy: all) found beans of type 'com.fasterxml.jackson.databind.ObjectMapper' myObjectMapper (OnBeanCondition)
```

The guard searched, **found** a bean — finding is failing for this guard — and named the
bean it found: `myObjectMapper`. Ours.

### Where it is used

The same pattern overrides any Boot default: data sources, security objects, task
executors, error handlers. (For small Jackson tweaks, `application.yml` settings adjust
the default mapper instead of replacing it; full takeover is for full control.)

### Common mistakes

- **The bare `new ObjectMapper()`.** Takes down endpoints and silently changes the error
  body format. If you take over, configure everything you need.
- **Expecting a warning.** Backing off is silent; the 500 pointed at Jackson, not at the
  config class. Only the report names the real cause.
- **Hunting the old bean name** `jacksonObjectMapper` — it was never created. Ask by
  type.
- **Also excluding `JacksonAutoConfiguration` "to be safe".** Unnecessary; it would kill
  the other Jackson recipes you still want.

### Link to the task

S5 done, in the task's own words. Checkpoint question 3: *Boot backed off; the proof is
the negative-match entry naming my bean, plus the census showing mine is the only
mapper.*

### Do it yourself

Postgres up (`docker start pg`), scratch app from Lesson 8.

**Step 1.** Create `ClockController.java`. Build, run, `curl -s
http://localhost:8080/now` → ISO string. Stop.

**Step 2 — break it knowingly.** Create `JsonConfig.java` with the naive bare mapper.
Rebuild, run, curl again. Expected: 500, `"timestamp"` as a number, and the
`InvalidDefinitionException` in the log naming `jackson-datatype-jsr310`. Stop.

**Step 3 — do it properly.** Replace with the proper `JsonConfig.java`. Rebuild, then:

```bash
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > takeover.log 2>&1
curl -s http://localhost:8080/now
# Ctrl+C afterwards
```

Expected: ISO string; census shows `myObjectMapper : true`, `jacksonObjectMapper : not
present`.

**Step 4 — hold the evidence.**

```bash
grep -n -A3 "JacksonObjectMapperConfiguration#jacksonObjectMapper" takeover.log
```

Expected: `Did not match:` with `found beans ... myObjectMapper`.

**Done when:** you can show the three outputs (ISO, 500, ISO) and the report line naming
your bean, with one sentence per transition.

### Practice questions

**Question 1.** One complete, real report entry:

```
   JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper:
      Did not match:
         - @ConditionalOnMissingBean (types: com.fasterxml.jackson.databind.ObjectMapper; SearchStrategy: all) found beans of type 'com.fasterxml.jackson.databind.ObjectMapper' myObjectMapper (OnBeanCondition)
```

(a) Which report section is this from, and which words tell you? (b) The guard **found**
beans — why is finding a failure here? (c) A teammate claims "Boot's mapper disappeared
because of some classpath change." Which exact word disproves him and names the real
cause?

*Answer:* (a) **Negative matches** — the heading under the name is `Did not match:`;
positive entries instead end their name line with `matched:`. (b) The guard is
`@ConditionalOnMissingBean`: it passes only when **no** bean of the type exists. It
exists to protect defaults from colliding with user beans, so discovering one means "the
developer already did this" — condition failed, recipe backed off. (c) The bean name
**`myObjectMapper`** at the end of the reason. The checker was `OnBeanCondition` (a bean
search), not `OnClassCondition` (a classpath search), and it names the user-declared
bean that triggered the back-off.

**Question 2.** A teammate writes:

```java
@Configuration
public class JsonConfig {
    @Bean
    ObjectMapper appMapper() {
        return new ObjectMapper();
    }
}
```

Before: an endpoint returning an object with a `LocalDate` worked. After: HTTP 500, log
says `InvalidDefinitionException: Java 8 date/time type java.time.LocalDate not
supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"`.
(a) Where did `LocalDate` support come from before, given nobody configured it? (b) Which
two lines inside `appMapper()` make dates work again and come out as ISO-8601 strings?

*Answer:* (a) From **Boot's default mapper**. The Jackson recipe builds it with the
helper modules found on the classpath — the web starter ships the `jsr310` module for
`java.time` — so `LocalDate` worked without anyone on the team configuring it. His bean
made Boot back off, and the bare replacement knows no `java.time`. (b)

```java
mapper.registerModule(new JavaTimeModule());
mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

The first line restores `java.time` support; the second makes dates serialize as
ISO-8601 strings instead of numbers.

### Summary for revision

- Takeover recipe: declare your own `ObjectMapper` bean; Boot's recipe backs off; the
  report's negative entry names your bean as the reason.
- Replacement, not merge: a bare mapper loses `java.time` support (500 +
  `InvalidDefinitionException`) and even changes the error body's date format.
- Proper mapper: `registerModule(new JavaTimeModule())` +
  `disable(WRITE_DATES_AS_TIMESTAMPS)`.
- Evidence trio: endpoint output, census (`backed off`), report entry
  (`found beans ... myObjectMapper`).
- Never exclude the auto-configuration just to override one bean.

---

## Lesson 10 — Break it on purpose, part 1: excluding the pool recipe (subtask S6, first half)

### Definition

An **exclusion** switches one auto-configuration recipe off by name:
`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`. The **failure
analysis** is the `APPLICATION FAILED TO START` block: when startup dies for a reason
Boot recognizes, it prints a plain `Description:` and a suggested `Action:` instead of
a bare stack trace.

### Why it is needed

A common real failure: someone excludes `DataSourceAutoConfiguration` to "turn off the
database" in a test, or copies it from an old answer, and something else still needs the
machinery. The failure's shape is misleading; knowing it saves hours.

### How it works — the cascade

1. The exclusion removes the pool recipe → no `dataSource` bean.
2. Recipes guarded by bean conditions on it back off **silently**: JPA, then
   repositories.
3. Silence ends at the first **unconditional demand** — a constructor that requires a
   never-created bean. Startup dies there.
4. The failure analysis names **the last link**, not the root cause. The report
   reconnects them.

### The example — making the app genuinely use JPA

Three new files. `Note.java`:

```java
@Entity
public class Note {
    @Id
    private Long id;
    private String text;
    protected Note() {}   // JPA requires a no-args constructor
}
```

`NoteRepository.java`:

```java
public interface NoteRepository extends JpaRepository<Note, Long> {}
```

`NoteCounter.java` — the unconditional demand:

```java
@Component
public class NoteCounter {
    private final NoteRepository repository;
    NoteCounter(NoteRepository repository) { this.repository = repository; }
    long count() { return repository.count(); }
}
```

Baseline run: `Started AutoconfigApplication`, 243–244 beans. The break, one line:

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
```

### Walkthrough — three real runs

**Run 1, the surprise (exclusion added before `NoteCounter` existed):**

```
Started AutoconfigApplication in 1.041 seconds
total beans in the context : 189
dataSource exists          : false
entityManagerFactory exists: false
transactionManager exists  : false
```

The app **started fine**. 243 → 189 beans, the trio gone, zero warnings. Everything
downstream of the pool is conditional, and back-offs are silent. An exclusion with no
consumer degrades the app **quietly**.

**Run 2, with the demand in place.** Exit code 1 and:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Parameter 0 of constructor in com.zenith.autoconfig.NoteCounter required a bean of type 'com.zenith.autoconfig.NoteRepository' that could not be found.

Action:

Consider defining a bean of type 'com.zenith.autoconfig.NoteRepository' in your configuration.
```

Every word true, and no mention of `DataSourceAutoConfiguration`. **The failure analysis
names the last link of the chain, not the root cause.** The same log hints at the next
move:

```
Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
```

**Run 3, with `--debug`.** Exclusions has its first entry:

```
Exclusions:
-----------

    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

And Negative matches shows both silent back-offs:

```
   HibernateJpaConfiguration:
      Did not match:
         - @ConditionalOnSingleCandidate (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans (OnBeanCondition)

   JpaRepositoriesAutoConfiguration:
      Did not match:
         - @ConditionalOnBean (types: javax.sql.DataSource; SearchStrategy: all) did not find any beans of type javax.sql.DataSource (OnBeanCondition)
```

Full chain in evidence: exclusion → no `dataSource` → JPA backed off → repositories
backed off → `noteRepository` never created → `NoteCounter` demand failed.

### Where it is used

Legitimate exclusions (replacing Boot's pool, silencing a database in tests) that
outlive their reason. The same pattern appears for any missing upstream bean.

### Common mistakes

- **Obeying the `Action:` literally** (hand-writing a repository implementation). The
  repository was supposed to be generated by the machinery you switched off. Trace, then
  fix the root.
- **Assuming an exclusion fails loudly.** Run 1 proved it can degrade in silence.
- **Hunting the excluded recipe in Negative matches.** It is only in Exclusions.

### Link to the task

First half of S6 done. Checkpoint question 4 method: failure analysis for the symptom,
`--debug` + Exclusions + Negative matches for the cause.

### Do it yourself

Scratch app from Lesson 9, Postgres up.

**Step 1.** Create `Note.java`, `NoteRepository.java`, `NoteCounter.java` as above.
Build, run: expect `Started`, census total 244. Stop.

**Step 2 — break.** Add the import and the exclusion to the main class:

```java
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
```

Rebuild, run in foreground. Expected: self-exit; the failure block naming `NoteCounter`
and `NoteRepository`; the 'debug' hint line above it.

**Step 3 — connect symptom to cause.**

```bash
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > broken.log 2>&1
grep -n -A4 "Exclusions:" broken.log
grep -n -A3 "^   HibernateJpaConfiguration:\|^   JpaRepositoriesAutoConfiguration:" broken.log
```

Say the six-link chain aloud, pointing at evidence for each link.

**Step 4 — heal.** Remove the exclusion and import, rebuild, run: `Started`, 244 beans.
(Optional: repeat Step 2 without `NoteCounter` to see the silent version: 189 beans,
trio `false`, no failure.)

**Done when:** you can explain why the message talks about `NoteRepository` when the
mistake was an exclusion three steps upstream, and show the report lines that prove it.

### Practice questions

**Question 1.** An application fails to start with exactly this block:

```
Description:

Parameter 0 of constructor in com.shop.OrderService required a bean of type 'com.shop.OrderRepository' that could not be found.

Action:

Consider defining a bean of type 'com.shop.OrderRepository' in your configuration.
```

`OrderRepository` extends `JpaRepository<Order, Long>`, and the main class carries
`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`. A teammate starts
writing `OrderRepositoryImpl` by hand. (a) Why is that wrong even though the Action
suggested it? (b) What is the root cause? (c) What two-step method proves the cause with
evidence?

*Answer:* (a) Spring Data **generates** repository beans from the interface at startup;
nobody is meant to implement them by hand. A hand-written class would paper over the
symptom while the app still has no `DataSource`, no JPA machinery, and no working data
layer — and it would lose everything `JpaRepository` provides. (b) The exclusion removed
the pool recipe; with no `dataSource` bean, the JPA recipe backed off, then the
repositories recipe backed off, so `orderRepository` was never generated;
`OrderService`'s constructor demand then failed. (c) Re-run with
`--debug > broken.log 2>&1`. First read **Exclusions** — `DataSourceAutoConfiguration`
is listed. Then read **Negative matches** — `HibernateJpaConfiguration` and
`JpaRepositoriesAutoConfiguration` both say they `did not find any beans` of type
`javax.sql.DataSource`. That is the full chain in writing.

**Question 2.** Six true facts about one failed startup, scrambled. Arrange them into
the causal chain, first cause to final symptom:

1. The failure analysis reports that `NoteCounter` required a `NoteRepository` bean that
   could not be found.
2. `JpaRepositoriesAutoConfiguration` backed off because `@ConditionalOnBean` found no
   bean of type `javax.sql.DataSource`.
3. The main class carries `@SpringBootApplication(exclude =
   DataSourceAutoConfiguration.class)`.
4. The bean `noteRepository` was never created.
5. No `dataSource` bean was ever created.
6. `NoteCounter`'s constructor demand could not back off, so startup died.

*Answer:* **3 → 5 → 2 → 4 → 6 → 1.** The exclusion (3) means the pool recipe never runs,
so no `dataSource` bean exists (5). The repositories recipe checks for that bean, finds
none, and backs off (2), so `noteRepository` is never generated (4). `NoteCounter`'s
constructor unconditionally demands it and cannot back off, so startup dies (6), and the
failure analysis reports that final unmet demand (1) — the last link, not the root.

### Summary for revision

- Exclusion = recipe switched off by name; never evaluated; listed only under
  Exclusions.
- Damage travels as silent back-offs and surfaces at the first unconditional demand —
  so the failure analysis can name a bean far from the root cause.
- With no consumer, the same exclusion degrades silently (243 → 189 beans, no warning).
- Method: failure analysis = symptom; `--debug` report (Exclusions + Negative matches) =
  cause.
- Real chain: exclusion → no `dataSource` → JPA back-off → repositories back-off → no
  `noteRepository` → constructor demand → `APPLICATION FAILED TO START`.

---

## Lesson 11 — Break it on purpose, part 2: remove the driver (subtask S6, second half)

### Definition

The second break: delete the **Postgres driver** dependency while the JPA starter and
the datasource `url` stay. This time the pool recipe **runs and crashes** instead of
never running — a different failure family with a different message.

### Why it is needed

Drivers go missing in real life: dependency cleanups, wrong scope, bad packaging. The
skill is **differential diagnosis**: seeing a startup failure and knowing which family
it is, because the two families are fixed in different places.

### How it works

1. The pool recipe's guard checks `javax.sql.DataSource` (ships with Java) and
   `EmbeddedDatabaseType` (ships with the JPA starter). **Neither is the driver.** The
   guard still passes; the recipe runs.
2. The recipe deduces the driver class from the url: `jdbc:postgresql://…` →
   `org.postgresql.Driver`.
3. Loading that class fails — the jar is gone. A `RuntimeException` is thrown
   mid-creation.
4. It wraps itself in `BeanCreationException` layers while climbing out through every
   waiting bean. No failure analyzer recognizes it → **no friendly block**, raw stack
   trace.

### The example

```xml
<!-- BREAK IT ON PURPOSE (Lesson 11): driver removed.
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
-->
```

Surprise: `mvn package` still **succeeds** — nothing compiles against a runtime-scoped
driver. Only startup can catch this.

### Walkthrough — the real failure

`grep -c "APPLICATION FAILED TO START" broken2.log` → **0**. No block. Instead:

```
ERROR ... o.s.boot.SpringApplication : Application run failed

org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory' ... : Error creating bean with name 'dataSource' defined in class path resource [org/springframework/boot/autoconfigure/jdbc/DataSourceConfiguration$Hikari.class]: Failed to instantiate [com.zaxxer.hikari.HikariDataSource]: Factory method 'dataSource' threw exception with message: Failed to load driver class org.postgresql.Driver in either of HikariConfig class loader or Thread context classloader
```

Deepest `Caused by:` at the bottom of the trace:

```
Caused by: java.lang.RuntimeException: Failed to load driver class org.postgresql.Driver in either of HikariConfig class loader or Thread context classloader
	at com.zaxxer.hikari.HikariConfig.setDriverClassName(HikariConfig.java:514)
```

**Reading rule:** the first bean named is where the collapse surfaced; the deepest
`Caused by:` is the root cause. Read bottom-up. The chain doubles as a map of bean
dependencies (`entityManagerFactory` → initializer → `dataSource`).

The conditions report of the broken app still says:

```
   DataSourceAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)
```

**Still positive, and correctly so.** The report explains **existence decisions**
(should this bean be attempted?), not **creation outcomes**. A positive match is a
promise to try, not to succeed. Our own probe told the classpath truth:

```
   PresenceDemoConfig#postgresPresentNote:
      Did not match:
         - @ConditionalOnClass did not find required class 'org.postgresql.Driver' (OnClassCondition)
```

### The two breaks, side by side

| | Break 1: exclusion | Break 2: driver removed |
| --- | --- | --- |
| Did the pool recipe run? | Never evaluated | Matched and ran |
| Failure style | Clean failure-analysis block | Raw `BeanCreationException` chain |
| Key sentence | "required a bean … could not be found" | "Failed to load driver class org.postgresql.Driver" |
| Report shows | Recipe under Exclusions; dependents negative | Recipe still in Positive matches |
| Fix lives in | Java — remove the `exclude` | `pom.xml` — restore the dependency |

### Where it is used

"Works on my machine, dies in staging" packaging differences; over-eager cleanups;
drivers scoped `test`. The bottom-up `Caused by:` reading applies to every
bean-creation failure.

### Common mistakes

- **Panicking without the friendly block.** Not every failure has an analyzer. No block
  → find the deepest `Caused by:` and read upward.
- **Fixing the first bean named.** `entityManagerFactory` led the message but was
  innocent.
- **Expecting the report to flag it.** The report's jurisdiction ends where bean
  creation begins.
- **Confusing with database-down.** `Unable to determine Dialect` = jar fine, nobody
  answered. `Failed to load driver class` = the jar itself is missing.

### Link to the task

S6 complete. Checkpoint 4 gains its final fork: *bean missing* → report (Exclusions,
Negative matches); *bean creation failed* → `Caused by:` chain bottom-up.

### Do it yourself

Scratch app from Lesson 10 (green), Postgres up.

**Step 1 — break.** Comment out the driver block in `pom.xml`. Then:

```bash
mvn -q package -DskipTests && echo BUILD_STILL_OK
```

**Step 2 — read the failure.**

```bash
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar > broken2.log 2>&1
grep -c "APPLICATION FAILED TO START" broken2.log   # expect 0
tail -30 broken2.log                                 # Caused by: ... Failed to load driver class
```

Read the chain bottom-up once, out loud.

**Step 3 — the report's verdict.**

```bash
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar --debug > broken2-debug.log 2>&1
grep -A3 "^   DataSourceAutoConfiguration matched" broken2-debug.log
grep -A3 "PresenceDemoConfig#postgresPresentNote" broken2-debug.log
```

Expected: recipe matched; probe negative.

**Step 4 — heal.** Un-comment the driver, rebuild, run: `Started`, 244 beans, probe
`true`.

**Done when:** you can reproduce the five-row table from memory and classify either
failure within ten seconds.

### Practice questions

**Question 1.** Two applications fail at startup. App A prints a clean block:

```
Description:

Parameter 0 of constructor in com.shop.ReportService required a bean of type 'com.shop.ReportRepository' that could not be found.
```

App B prints no block; its log ends with:

```
Caused by: java.lang.RuntimeException: Failed to load driver class org.postgresql.Driver in either of HikariConfig class loader or Thread context classloader
```

For each app: (a) did `DataSourceAutoConfiguration` run, (b) where does it appear in a
`--debug` report, and (c) is the fix a Java annotation change or a `pom.xml` change?

*Answer:* App A is the back-off family (our break 1): (a) the pool recipe **never ran**
— it was excluded, so no `dataSource` bean existed and everything downstream backed off
until an unconditional demand failed. (b) Under **Exclusions** (and its dependents in
Negative matches). (c) A **Java** change: remove
`@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`. App B is the
creation-failure family (break 2): (a) the recipe **matched and ran** — its guard checks
`spring-jdbc` classes, not the driver. (b) Still in **Positive matches**. (c) A
**`pom.xml`** change: restore the `org.postgresql:postgresql` dependency.

**Question 2.** After failure B, a teammate re-runs with `--debug`, sees
`DataSourceAutoConfiguration matched` in Positive matches, and says: "The report says
the data source is fine, so the report is wrong." Explain (a) what question the report
answers, (b) why `matched` is correct here, and (c) where the real evidence lives.

*Answer:* (a) The report answers one question per recipe: "did this recipe's conditions
pass — should Boot attempt its beans?" It documents **decisions about existence**, made
before any bean is built. (b) `matched` is correct because the recipe's guard looks for
`javax.sql.DataSource` and `EmbeddedDatabaseType`, both still on the classpath. The
decision to attempt the pool was right; what failed came later, **during creation**,
when Hikari tried to load the driver class named by the url. The report never promised
creation would succeed. (c) In the exception itself: the `BeanCreationException` chain,
read bottom-up — the deepest `Caused by:` names the root cause
(`Failed to load driver class org.postgresql.Driver`), and the chain above it shows
which beans were waiting.

### Summary for revision

- Driver removed, url kept: build succeeds, startup dies mid-creation.
- No failure analyzer for this one → no friendly block → read the deepest `Caused by:`
  first.
- The report still shows `matched` — correctly. Existence decisions ≠ creation outcomes.
- Our `postgresPresentNote` probe independently proved the driver's absence.
- Three database failures now known by sentence: missing bean ("could not be found"),
  missing jar ("Failed to load driver class"), database down ("Unable to determine
  Dialect").

---

## Lesson 12 — The master list: the magic is a text file (subtask S7)

### Definition

The **master list** is a plain text file inside the `spring-boot-autoconfigure` jar:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

It holds the full class name of every auto-configuration recipe, one per line.

### Why it is needed

The last open question: how does Boot know **which** classes to evaluate? Not scanning,
not magic — it reads this file. Any jar can carry its own file at the same path, and
Boot reads them all; that is how the actuator and third-party starters plug in.

### How it works — the complete pipeline

1. `@SpringBootApplication` includes `@EnableAutoConfiguration`.
2. Boot reads the imports file from **every jar on the classpath** and merges the lines
   into one candidate list.
3. Excluded entries are removed.
4. Every surviving candidate's conditions are evaluated.
5. Matches declare beans; everything lands in the report.

No other input exists.

### The example — opening the real jar

```bash
unzip -l spring-boot-autoconfigure-3.5.16.jar | grep "AutoConfiguration.imports"
#     13033  ...  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

unzip -p spring-boot-autoconfigure-3.5.16.jar META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | wc -l
# 156
```

First lines:

```
org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
...
```

### Walkthrough

- **156 lines** — the same number quoted since Lesson 2; it was always this file's line
  count.
- One full class name per line, alphabetical; the package names the area.
- Our three stars are lines in it:

  ```
  38:org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
  71:org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
  72:org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
  ```

  Every drama of this task started as one line of this text file.

### Where it is used

- Candidacy checks: a class in no imports file is invisible — never evaluated, in no
  report section.
- Writing your own starter later: your recipes + your own imports file in your jar.
- Predicting growth: `spring-boot-actuator-autoconfigure` ships its own imports file.

### Common mistakes

- **Believing in runtime annotation scanning.** The annotation marks the class; the file
  nominates it. Only listed classes are considered.
- **Trying to edit the file.** It is inside Boot's jar. Add recipes via your own jar's
  file; remove via `exclude`.
- **Old tutorials say `spring.factories`.** Pre-2.7 location, same idea. Mentally
  translate.

### Link to the task

S7 done: the file seen, the last mystery removed. Pipeline: file → candidates →
exclusions → conditions → report.

### Do it yourself

```bash
ls ~/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/3.5.16/
cd ~/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/3.5.16/
unzip -l spring-boot-autoconfigure-3.5.16.jar | grep "AutoConfiguration.imports"
unzip -p spring-boot-autoconfigure-3.5.16.jar META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | wc -l
unzip -p spring-boot-autoconfigure-3.5.16.jar META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | less
```

Expected: the file listed, `156`, then the alphabetical catalog (in `less`: `/Jackson`
jumps to our star, `q` quits). Then:

```bash
unzip -p spring-boot-autoconfigure-3.5.16.jar META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | grep -n "JacksonAutoConfiguration\|jdbc.DataSourceAutoConfiguration\|redis.RedisAutoConfiguration"
```

**Failure case.** Mistype the path inside the jar → `caution: filename not matched`.
Paths inside jars are exact; copy-paste them.

**Done when:** you can answer "how does Boot know which classes to evaluate?" with the
file's location, from memory.

### Practice questions

**Question 1.** A teammate says: "At startup, Spring Boot scans every jar for classes
annotated with `@AutoConfiguration` and evaluates each one it finds." (a) Correct the
mechanism. (b) A library jar contains a good recipe class, properly annotated and
guarded — but ships **no** imports file naming it. In which report section does it
appear?

*Answer:* (a) There is no scanning. Boot reads a specific text file —
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` —
from every jar on the classpath, and merges those lines into the candidate list. The
annotation marks a class as a recipe; **the file is what nominates it**. (b) **None of
them.** Not listed means not a candidate: its conditions are never evaluated, so it
cannot be a positive match, a negative match, or an exclusion. It is invisible. The fix
is shipping an imports file in that jar naming the class.

**Question 2.** Five pipeline stages, scrambled. Arrange in startup order:

1. Conditions on each surviving candidate are evaluated.
2. Outcomes recorded: matches create beans; the report lists every decision.
3. The imports files are read from every jar on the classpath.
4. Excluded entries are removed from the list.
5. Lines from all files are merged into one candidate list.

*Answer:* **3 → 5 → 4 → 1 → 2.** Read the files from all jars (3), merge into one
candidate list (5), strip exclusions (4) — excluded entries are never evaluated — then
evaluate each survivor's conditions (1), and finally create beans for matches while the
report records every decision with its reason (2).

### Summary for revision

- The candidate list is a text file: `META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports`, 156 lines in Boot
  3.5.16.
- Read from every jar on the classpath; merged; exclusions removed; conditions decide
  the rest.
- Not in any list = invisible to auto-configuration and to the report.
- `unzip -p <jar> <path>` prints a file from inside a jar.
- Old name, pre-Boot-2.7: `spring.factories`.

---

## Lesson 13 — The actuator, and why exposure is opt-in (subtask S8)

### Definition

The **actuator** is an add-on that gives a running app HTTP addresses — **endpoints** —
that describe it from the inside: health, beans, settings, matched recipes. One
dependency, no code. **Exposure is opt-in**: out of the box only `health` answers over
HTTP; everything else returns `404` with no hint why, until named in `application.yml`.

### Why it is needed

Startup logs are the past. Operations need to ask a **live** app questions: a load
balancer asks "alive?", an engineer asks "what config did this instance load?". The
actuator is the standard answer. And opt-in exists because the answers are secrets —
Boot refuses to network them just because a jar arrived.

### How it works

Step 1 — dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

The actuator jar carries **its own recipe list** at the standard path
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` —
Boot reads that file from every jar on the classpath:

```bash
unzip -p spring-boot-actuator-autoconfigure-3.5.16.jar \
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports | wc -l
# 123
```

123 new recipes join Boot's 156.

Step 2 — opt-in in `application.yml` (top level, NOT under `spring:`):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,env,beans,configprops,conditions
```

### The example — two real runs

**Run 1 (dependency only, no opt-in):**

```bash
curl -s http://localhost:8080/actuator
# {"_links":{"self":...,"health-path":...,"health":{"href":"http://localhost:8080/actuator/health"...}}}
curl -s -i http://localhost:8080/actuator/health | head -2
# HTTP/1.1 200
curl -s -i http://localhost:8080/actuator/beans | head -2
# HTTP/1.1 404        <- exists inside the app, not exposed, no hint
```

Census printed `total beans in the context : 374` — up from 244, with zero config.

**Run 2 (after the yml opt-in, rebuild):**

```bash
curl -s http://localhost:8080/actuator      # front door now lists all five
# .../actuator/beans  .../actuator/health  .../actuator/conditions
# .../actuator/configprops  .../actuator/env
curl -s -o /dev/null -w "%{http_code}, %{size_download} bytes\n" \
  http://localhost:8080/actuator/beans
# 200, 127807 bytes
```

### Walkthrough

- `/actuator` is the front door: it lists reachable endpoints. Before opt-in: health
  only. After: all five.
- The 404 on `/actuator/beans` is the task's warning verbatim: *"everything else
  returns 404 with no hint why."*
- 244 → 374 beans from one pom line: the actuator's 123 recipes were evaluated and
  ~130 matched — the classpath rule, live.
- Bean existence and HTTP exposure are **separate gates**: the endpoint beans existed
  in run 1; the yml line only opened the web door.

### Where it is used

Kubernetes probes `/actuator/health` to route or restart pods. On-call engineers read
`/actuator/env` (behind auth) to see what a misbehaving instance really loaded. The
exposure list is a deliberate, reviewed choice per environment.

### Common mistakes

- **Reading the 404 as "actuator broken".** It means *not exposed*. Check `/actuator` —
  the front door never lies.
- **"Fixing" with `include: "*"`.** Opens secret-printing endpoints too. Name only what
  you need.
- **Editing yml without rebuilding.** The yml is packaged into the jar: edit → rebuild
  → restart.
- **Indenting `management:` under `spring:`.** Silently ignored; `beans` keeps 404ing.

### Link to the task

Subtask S8 done: starter added, out-of-the-box 404 witnessed, the five endpoints
exposed by name.

### Do it yourself

Add the dependency to `scratch-app/pom.xml` (after postgresql):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Build and run (`docker start pg` first):

```bash
cd ~/Projects/foundations/F6.9-auto-configuration-removing-the-magic/scratch-app
mvn package -DskipTests
java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar
```

Second terminal:

```bash
curl -s http://localhost:8080/actuator          # links for health only
curl -i http://localhost:8080/actuator/health   # HTTP/1.1 200, {"status":"UP"}
curl -i http://localhost:8080/actuator/beans    # HTTP/1.1 404  <- the trap
```

Stop the app. Append to `application.yml` (top-level block):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,env,beans,configprops,conditions
```

Rebuild, rerun:

```bash
curl -s http://localhost:8080/actuator          # links for all five
curl -i http://localhost:8080/actuator/beans    # HTTP/1.1 200 + large JSON
```

**Failure case:** indent `management:` under `spring:` → opt-in silently ignored,
`beans` still 404.

**Done when:** you can flip `/actuator/beans` from 404 to 200 and explain the change in
one sentence.

### Practice questions

**Question 1.** A teammate adds `spring-boot-starter-actuator`, restarts, runs
`curl http://localhost:8080/actuator/env`, gets `404`. They conclude the actuator
didn't install. Give the correct diagnosis, the one `curl` that proves it, and the fix.

*Answer:* The actuator installed fine — **`/env` is simply not exposed**. Exposure is
opt-in; out of the box only `health` answers over HTTP, and unexposed endpoints return
a bare 404 by design. Proof: `curl -s http://localhost:8080/actuator` — the front door
lists only the `health` links, which shows the actuator is alive but `env` is dark.
Fix: add `management.endpoints.web.exposure.include: health,env,...` to
`application.yml`, rebuild, restart.

**Question 2.** Adding the actuator jar raised the census from 244 to 374 beans
*before* any yml change. Explain (a) why beans appeared with no configuration, and
(b) why they still didn't answer at `/actuator/beans` until the yml line.

*Answer:* (a) The actuator jar ships its own 123-line
`AutoConfiguration.imports` file at the standard path. Boot reads that file from every
jar on the classpath, merges the lines into the candidate list, and evaluates each
candidate's conditions. In a web app most matched, declaring ~130 beans — classpath
decides existence; no settings needed. (b) Bean existence and HTTP exposure are two
separate gates. The endpoint beans existed in the context, but the web layer only maps
endpoints named in `management.endpoints.web.exposure.include` onto URLs, and the
default list contains only `health`. The yml line opened the second gate; it created
nothing.

### Summary for revision

- Actuator = endpoints that describe the live app; one dependency, no code.
- Its jar carries its own 123-line imports file — recipes merge in via the standard
  mechanism.
- Exposure is opt-in: default is `health` only; the rest 404 with no hint.
- Opt-in line: `management.endpoints.web.exposure.include: health,env,beans,configprops,conditions`.
- `/actuator` front door lists what is reachable; check it before debugging 404s.
- Census 244 → 374 with zero config: classpath decides existence; yml decides exposure.

---

## Lesson 14 — The five endpoints: five questions a live app can answer (subtask S8)

### Definition

The five exposed endpoints each answer one operational question about the **running**
app, over HTTP, without restarts or log-reading:

| Endpoint | Question it answers |
|---|---|
| `/actuator/health` | Are you OK *right now*? |
| `/actuator/env` | What settings did you load, and where did each come from? |
| `/actuator/beans` | Which beans exist, and who declared each? |
| `/actuator/configprops` | What values do the settings-objects hold right now? |
| `/actuator/conditions` | The `--debug` conditions report, live, as JSON |

### Why it is needed

A production app cannot be restarted just to answer a question. These five addresses
interrogate the live process — together they make everything this task studied visible
on demand.

### How it works — the tour (all real output)

**`/actuator/health`** — `HTTP/1.1 200` + `{"status":"UP"}`. The UP is computed fresh:
Boot borrows a database connection and pings Postgres. Proven live:

```bash
docker stop pg                                   # kill the DB under the running app
curl -s -i --max-time 60 http://localhost:8080/actuator/health
# HTTP/1.1 503
# {"status":"DOWN"}        <- took ~30s: the pool waits out its connection timeout
docker start pg
curl -s http://localhost:8080/actuator/health    # {"status":"UP"} again
```

The **status code flips 200 ↔ 503** — that is what load balancers read, not the body.

**`/actuator/env`** — every property source in winning order, every key:

```json
"spring.datasource.url": {
  "value": "******",
  "origin": "class path resource [application.yml] from autoconfig-scratch-0.0.1-SNAPSHOT.jar - 5:10"
}
```

Values masked by default (Boot 3); origin is exact to file, jar, line 5, column 10.
Config-override arguments end here.

**`/actuator/beans`** — 361 entries, each with type, declaring class, dependencies:

```json
"myObjectMapper": { "type": "...ObjectMapper",
                    "resource": "class path resource [com/zenith/autoconfig/JsonConfig.class]" }
"dataSource":     { "type": "com.zaxxer.hikari.HikariDataSource",
                    "resource": "class path resource [org/.../jdbc/DataSourceConfiguration$Hikari.class]" }
```

`resource` = "who declared me": our class for our bean, Boot's recipe class for the
pool. Every bean carries its birth certificate.

**`/actuator/configprops`** — settings-objects by prefix with current values:

```json
"spring.datasource-...DataSourceProperties": {
  "prefix": "spring.datasource",
  "properties": { "password": "******", "url": "******", "username": "******" }
}
```

Masked, but the shape shows which keys landed — a typo'd yml key simply won't appear.

**`/actuator/conditions`** — the report, live: 209 positive / 302 negative /
12 unconditional. The famous entry, from the running app:

```json
"JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper": {
  "notMatched": [{ "condition": "OnBeanCondition",
    "message": "@ConditionalOnMissingBean (types: com.fasterxml.jackson.databind.ObjectMapper;
                SearchStrategy: all) found beans of type 'ObjectMapper' myObjectMapper" }]
}
```

Boot's recipe backing off, naming **our** bean as the reason — retrievable from
production at any moment. Our own `PresenceDemoConfig#jacksonPresentNote` sits in the
positive matches.

### Where it is used

`health` drives orchestrator traffic decisions (Kubernetes probes). `env` and
`configprops` settle "what config is this instance really running?" during incidents.
`beans` and `conditions` answer "is the bean there, and why/why not?" — standard
incident flow: health → env → conditions, all behind auth.

### Common mistakes

- **Automation matching the body text `UP`** instead of the status-code contract
  (200 vs 503).
- **Reading `******` as an error.** Deliberate masking, not data loss; don't disable it
  in production.
- **Using `/actuator/conditions` for a crashed start.** No running app, no endpoints.
  Crashed start → `--debug` log. Running app → this endpoint. Same report, two doors.

### Link to the task

Completes S8: five endpoints exposed and visited. The masking seen at `/env` and
`/configprops` leads into S9 — why these endpoints must never be public even masked.

### Do it yourself

App running (`docker start pg`, then `java -jar target/autoconfig-scratch-0.0.1-SNAPSHOT.jar`),
second terminal:

```bash
curl -s -i http://localhost:8080/actuator/health | head -1
curl -s http://localhost:8080/actuator/env | python3 -m json.tool | grep -A 2 "datasource.url"
curl -s http://localhost:8080/actuator/beans | python3 -m json.tool | grep -B 1 -A 4 '"myObjectMapper"'
curl -s http://localhost:8080/actuator/configprops | python3 -m json.tool | grep -B 2 -A 8 '"spring.datasource"'
curl -s http://localhost:8080/actuator/conditions | python3 -m json.tool | grep -A 6 "jacksonObjectMapper"
```

The health flip (app stays running):

```bash
docker stop pg
curl -s -i --max-time 60 http://localhost:8080/actuator/health | head -1   # HTTP/1.1 503 (~30s)
docker start pg && sleep 3
curl -s http://localhost:8080/actuator/health                               # {"status":"UP"}
```

**Failure case:** a short curl timeout (`--max-time 10`) on the flip returns empty —
the check is still waiting out the pool's 30-second timeout.

**Done when:** you can flip health 200→503→200 without restarting, and point at the
conditions line naming your own bean.

### Practice questions

**Question 1.** A monitoring system polls `/actuator/health` every 10 seconds and
alerts when the body is not exactly `{"status":"UP"}`. Name two things wrong with
matching the body, and state the exact response (code and body) while the database is
down.

*Answer:* (1) The contract for machines is the **HTTP status code** — 200 when UP, 503
when DOWN; the body is a human courtesy. Exact-text matching breaks the moment the body
legitimately changes shape (e.g. enabling detail view adds a `components` block, or key
order shifts — JSON order is not guaranteed). (2) Exact-match can't distinguish "app
answered DOWN" from "app never answered" (timeout, process dead) — both just fail the
text match, hiding which emergency you have. While the database is down the response is
`HTTP/1.1 503` with body `{"status":"DOWN"}` — and the first such answer can take ~30
seconds, because the connection pool waits out its connection timeout before admitting
defeat.

**Question 2.** During an incident a teammate claims: "our custom ObjectMapper bean
never worked — Boot is secretly using its own." The app may not be restarted. Which two
endpoints disprove the claim, and what will each literally show?

*Answer:* `/actuator/beans` shows the entry `"myObjectMapper"` with type
`com.fasterxml.jackson.databind.ObjectMapper` and resource `class path resource
[com/zenith/autoconfig/JsonConfig.class]` — our bean exists in the live context and was
declared by our own config class. `/actuator/conditions` shows
`JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper` under
**negative matches** with the message `@ConditionalOnMissingBean ... found beans of
type 'com.fasterxml.jackson.databind.ObjectMapper' myObjectMapper` — Boot's own recipe
on record saying it backed off *because of* our bean. Existence plus renunciation; the
claim is dead.

### Summary for revision

- Five endpoints, five questions: alive? (health) — settings + origins? (env) — beans +
  declarers? (beans) — current values by prefix? (configprops) — recipe decisions?
  (conditions).
- health flips 200/503 with a real database ping; machines read the code, not the body.
- `/env` origins are exact (file, jar, line:column); values masked by default in Boot 3.
- `/beans` `resource` field = who declared the bean (ours: JsonConfig; pool:
  DataSourceConfiguration$Hikari).
- `/actuator/conditions` = the `--debug` report live (209 positive / 302 negative / 12
  unconditional here); crashed starts still need the log version.

---

## Lesson 15 — The security note: why `/actuator/env` must never be public (subtask S9)

### Definition

A **security note** is a short written warning in the project docs: it names a risk you
are deliberately living with for now, and the future work that will close it. Ours:
this app serves five actuator endpoints to anyone who reaches port 8080 — no
authentication. Phase 13 (Spring Security) acts on it.

### Why it is needed

"Publicly reachable actuator" is one of the most common real-world Spring Boot security
failures. Automated scanners sweep the internet for `/actuator/env` around the clock.
Writing the risk down now is how the lock actually gets installed later.

### What leaks despite masking — measured on our own app

With masking fully on, a stranger reading our `/actuator/env` still gets:

1. **All 84 host environment-variable names** (`PATH = ******`, `HOME = ******`, ...).
   Names alone are reconnaissance: a key called `AWS_SECRET_ACCESS_KEY` says exactly
   what this app holds and what to phish for.
2. **Every configuration key we wrote, in the clear:** `spring.datasource.url`,
   `spring.datasource.username`, `spring.datasource.password`,
   `management.endpoints.web.exposure.include` — a SQL database with password auth,
   plus the list of management endpoints to try next.
3. **Exact origins:** `application.yml from autoconfig-scratch-0.0.1-SNAPSHOT.jar -
   5:10` — artifact name, version, internal file layout.

Compounding endpoints: `/beans` is a labeled diagram of the internal architecture;
`/conditions` is a tech inventory (libraries + versions to match against known
vulnerabilities).

**Masking is a seatbelt, not a lock.** One yml line turns it off:

```yaml
management:
  endpoint:
    env:
      show-values: ALWAYS   # every value, passwords included, in plain text
```

Dev conveniences get copied to prod. And `include: "*"` is worse: it opens
`/actuator/heapdump` — a full copy of the JVM's memory, where live secrets are
unmasked by definition.

### The note (the deliverable)

> **SECURITY NOTE — actuator endpoints are not public information.**
> This app exposes `health,env,beans,configprops,conditions` over HTTP with **no
> authentication**. Acceptable only while the app runs on localhost for study.
> `/actuator/env` must never be reachable from the public internet, even with value
> masking: it reveals every config key name, all 80+ host environment variable names,
> artifact names/versions, and file origins — a complete reconnaissance kit. Masking is
> one yml line from off (`show-values: ALWAYS`); never "fix" a 404 with `include: "*"`
> (opens `/actuator/heapdump` — raw memory, unmasked secrets).
> **Action, Phase 13 (Spring Security):** require authentication on `/actuator/**`;
> keep the exposure list minimal in prod (often `health` only); keep masking on.
> Until then: these endpoints stay on localhost.

### Where it is used

Layered production pattern: minimal exposure list (often just `health`); actuator on a
separate management port reachable only internally; authentication beyond `health`;
masking on even for authenticated users. Security reviews check the "Action" line
happened.

### Common mistakes

- **"Values are masked, so it's safe."** Names, sources, versions, origins are the
  reconnaissance; masking hides only the final step.
- **"Nobody knows our URL."** Scanners don't need to know; they try `/actuator/env` on
  every reachable address, constantly.
- **"We'll lock it down later," written nowhere.** Unwritten intentions don't survive
  deadlines — hence a *written* note.

### Link to the task

Subtask S9 done: risk named, measured on our own app, note drafted with its Phase-13
action.

### Do it yourself

```bash
curl -s http://localhost:8080/actuator/env | python3 -m json.tool | grep '"name"'
curl -s http://localhost:8080/actuator/env | python3 -c "
import json,sys
d=json.load(sys.stdin)
env=[ps for ps in d['propertySources'] if ps['name']=='systemEnvironment'][0]
print(len(env['properties']), 'environment variable names visible')
"
```

Expected: the property-source inventory, then a count in the dozens. Copy the security
note into your README copy.

**Failure case:** no `python3` on the second laptop → `brew install python3`, or
eyeball raw JSON: `curl -s http://localhost:8080/actuator/env | head -c 2000`.

**Done when:** you can state, without looking, three things `/actuator/env` reveals
despite masking.

### Practice questions

**Question 1.** A teammate says: "I checked `/actuator/env` on our public app — every
value shows `******`, so there's no urgency to lock it down." Give three concrete
pieces of intelligence an attacker still harvests, and the one-line config change that
would silently turn masking off.

*Answer:* (1) Every configuration **key name** in the clear — ours reveal a SQL
database with password auth (`spring.datasource.*`) and the exact list of exposed
management endpoints (`management.endpoints.web.exposure.include`). (2) All host
**environment-variable names** (84 on our machine) — names like `AWS_SECRET_ACCESS_KEY`
announce what secrets exist and what to phish for. (3) **Exact origins** — artifact
name and version (`autoconfig-scratch-0.0.1-SNAPSHOT.jar`), internal file layout, line
and column of each setting; version numbers can be matched against known
vulnerabilities. The one-liner: `management.endpoint.env.show-values: ALWAYS` — set in
dev for convenience, copied to prod, and every value including the database password is
suddenly plain text.

**Question 2.** Rank from safest to most dangerous, justifying each: (a)
`include: "*"`, internet-reachable, masking on; (b) `include: health`,
internet-reachable; (c) the five endpoints, internal network only, authentication
required.

*Answer:* **(c) safest** — rich endpoints, but two independent locks stand before them
(network isolation AND authentication), with masking as a third layer; an attacker
needs to breach the network *and* steal credentials. **(b) middle** — internet-facing,
but the surface is only `health`, which is designed for probes and leaks almost nothing
(just up/down). **(a) most dangerous** — `"*"` opens everything including
`/actuator/heapdump`, a raw dump of JVM memory where secrets sit unmasked by
definition, so the masking that justified the setup protects nothing; internet-reachable
with no auth means full reconnaissance plus memory theft for anyone who finds it, and
scanners always find it.

### Summary for revision

- A security note = written risk + written future action; ours targets Phase 13
  (Spring Security).
- Masked `/env` still leaks: config key names, 84 env-var names, artifact
  name/version, exact origins — a recon kit.
- Masking is one yml line from off (`show-values: ALWAYS`); `include: "*"` opens
  `/actuator/heapdump` (unmasked memory).
- Production layers: minimal include list, internal-only reachability, auth, masking.
- The note lives in this README, above; Phase 13 implements it.

---

## Lesson 16 — The two ask-AI drills, and the checkpoint (subtasks S10, S11, CP)

### Definition

Two prescribed prompts to paste into an AI assistant, then five closing questions
answered out loud. The skill trained by the drills is not *getting* an answer — it is
**judging** one. You now own two instruments the AI does not have: your app's
conditions report (`--debug` log, or `/actuator/conditions` live) and your bean census.
A good AI answer must survive being checked against them.

### Drill 1 — the DataSource decision (S10)

Prompt, verbatim:

> *"Walk me through how Spring Boot decides to create a DataSource bean, naming the
> auto-configuration class and each condition it checks."*

The model answer — every line checkable against our live `/actuator/conditions`, and
checked:

1. **Candidacy.** `DataSourceAutoConfiguration` must be listed in the
   `AutoConfiguration.imports` text file inside the autoconfigure jar. It is — line 72.
2. **Class-level conditions:**

   ```
   @ConditionalOnClass found required classes 'javax.sql.DataSource',
     'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType'
   @ConditionalOnMissingBean (types: io.r2dbc.spi.ConnectionFactory) did not find any beans
   ```

   JDBC classes must be present (ours came with `spring-boot-starter-data-jpa`), and no
   reactive-database bean may exist — with the reactive stack, this recipe steps aside.
3. **Inner group `PooledDataSourceConfiguration`** (outer passing unlocks inner groups):

   ```
   AnyNestedCondition 1 matched 1 did not;
     PooledDataSourceAvailable: found supported DataSource
     ExplicitType: @ConditionalOnProperty (spring.datasource.type) did not find property
   @ConditionalOnMissingBean (types: javax.sql.DataSource, javax.sql.XADataSource)
     did not find any beans
   ```

   `AnyNestedCondition` passes if **any** inner test passes: a pool library on the
   classpath (passed) *or* an explicit `spring.datasource.type` (we set none — not
   needed, one match suffices). And the guard: any user-defined `DataSource` bean would
   make everything below back off.
4. **Pool selection, `DataSourceConfiguration.Hikari`:**

   ```
   @ConditionalOnClass found required class 'com.zaxxer.hikari.HikariDataSource'
   @ConditionalOnProperty (spring.datasource.type=com.zaxxer.hikari.HikariDataSource) matched
   @ConditionalOnMissingBean (types: javax.sql.DataSource) did not find any beans
   ```

   The quiet trick: we never wrote `spring.datasource.type`, yet the property condition
   **matched** — it is declared "match if the property is missing". Absence means "no
   objection"; Hikari wins by default. Jar presence plus our silence selects it.
5. **The bean.** All gates passed → `HikariDataSource` built from
   `spring.datasource.url/username/password`. Downstream, JPA hooks on:
   `@ConditionalOnSingleCandidate ... found a single bean 'dataSource'`.

**Grading any AI answer:** it must name the class, walk real conditions (classpath,
missing-bean, pool selection), and agree with *your* report. If it invents conditions
your report does not show, the report wins. Always.

### Drill 2 — the method (S11)

Prompt, verbatim:

> *"I added a dependency and now my application behaves differently. Teach me a
> step-by-step method using the conditions report to find out exactly which bean
> changed."*

The model answer — a repeatable six-step method:

1. **Baseline.** Before the change: `java -jar app.jar --debug > before.txt 2>&1`
   (live app: `curl .../actuator/conditions > before.json`).
2. **Change.** Add the dependency, rebuild, run the same way into `after.txt`.
3. **Diff the decisions.** New *positive* entries = beans that now exist. Entries moved
   *positive → negative* = beans displaced or vanished.
4. **Read the reasons.** Each new positive entry names its trigger — usually
   `@ConditionalOnClass found required class ...` naming a class the new jar brought.
   Whole new families can appear: jars carry their own imports file (the actuator
   brought 123 recipes that way).
5. **Confirm the bean** in `/actuator/beans`; its `resource` field names the declaring
   recipe class.
6. **For a displaced bean**, find the old recipe in negative matches: its
   `@ConditionalOnMissingBean ... found beans ...` message names the bean that pushed
   it aside.

We lived this three times: JPA flipped `DataSourceAutoConfiguration` negative →
positive (census 161 → 238); the actuator jumped 244 → 374 with zero config;
`myObjectMapper` moved Boot's mapper recipe into negative matches with our bean named
in the message.

### Where it is used

This pairing — AI for the story, report for the truth — is the working pattern for
every future "why did my app change?" moment. AI answers date quickly and hallucinate
conditions; the report is generated from your exact classpath and config, every run.

### Common mistakes

- **Accepting an AI walkthrough without checking it** against `/actuator/conditions`.
- **No baseline.** Diagnosing after the fact is archaeology; `before.txt` is one
  command.
- **Reading only positive matches after a change** — displacements hide in the
  negatives.

### Link to the task

S10, S11, and CP done. With this, every subtask S1–S11 has been completed with
evidence.

### Do it yourself

1. Paste drill 1's prompt into an AI assistant. Grade it:

   ```bash
   curl -s http://localhost:8080/actuator/conditions | python3 -m json.tool | grep -A 8 '"DataSourceAutoConfiguration"'
   ```

2. Paste drill 2's prompt. Compare against the six steps — does it include a baseline?
   Does it use the report, or guess from docs?
3. Answer the five checkpoint questions out loud, no notes.

**Done when:** all five answered without looking anything up — #4 as a confident,
ordered procedure.

### Checkpoint — the five questions, with full answers

**1. What is in a starter, and what is not?**

*Answer:* In: only a `pom.xml` — a curated list of other dependencies with versions
known to work together. A shopping list. Not in: code. We opened
`spring-boot-starter-web`'s jar: 4 files, zero classes. Tomcat, Spring MVC, and Jackson
arrive as transitive dependencies the list names. Also not in a starter: the
configuration — recipes live in `spring-boot-autoconfigure`, itself one of the
dependencies pulled in.

**2. Explain `@ConditionalOnClass` and `@ConditionalOnMissingBean` in one sentence
each.**

*Answer:* `@ConditionalOnClass`: create these beans only if this class is on the
classpath — a library's presence is read as intent to use it.
`@ConditionalOnMissingBean`: create this bean only if the developer has not defined
their own of this type — and since user beans register before auto-configuration is
evaluated, the user always wins; this is what makes every Boot default replaceable.

**3. You defined your own `ObjectMapper`. What did Spring Boot do, and how did you
prove it?**

*Answer:* Boot backed off — its `jacksonObjectMapper` bean was never created, and our
`myObjectMapper` became the app's only `ObjectMapper`, used by the web layer. Proof,
three ways: (1) the conditions report lists
`JacksonAutoConfiguration.JacksonObjectMapperConfiguration#jacksonObjectMapper` under
**negative** matches with the message `@ConditionalOnMissingBean ... found beans of
type 'com.fasterxml.jackson.databind.ObjectMapper' myObjectMapper` — our bean named as
the reason; (2) the bean census: `jacksonObjectMapper` absent, `myObjectMapper`
present; (3) behavior: `/now` started returning ISO-8601 text instead of a decimal
timestamp, because our mapper had `JavaTimeModule` registered and
`WRITE_DATES_AS_TIMESTAMPS` disabled.

**4. A bean you expected does not exist. Describe the exact steps you would take to
find out why.**

*Answer:*
1. Confirm absence: `/actuator/beans` (or `context.containsBean`), searching by name
   and by type.
2. Get the report: app starts → `java -jar app.jar --debug > out.txt 2>&1`; app
   crashes → read the failure analysis first, add `--debug` for the report; app live →
   `curl .../actuator/conditions`.
3. Search the **negative matches** for the recipe or bean name.
4. Read the first failed condition — evaluation stops at the first failure:
   - `did not find required class X` → library missing from the classpath → add the
     dependency/starter.
   - `@ConditionalOnMissingBean ... found beans ... <name>` → someone already defined
     one; Boot backed off deliberately — the named bean is the answer.
   - `@ConditionalOnBean` / `@ConditionalOnSingleCandidate` `did not find ...` → an
     upstream bean is missing → investigate *that* bean the same way (chains: no
     `dataSource` → no `entityManagerFactory` → no `transactionManager`).
   - a property condition failed → a settings gate is off in `application.yml`.
5. Not in negatives either? Check **Exclusions** — excluded recipes are never
   evaluated (`@SpringBootApplication(exclude=...)` or `spring.autoconfigure.exclude`).
   And if the class is in no imports file at all, it is invisible to the whole
   machinery.
6. Fix per the stated reason, rerun, confirm the entry moved to positive matches and
   the bean exists.

**5. Why can `/actuator/env` not be public?**

*Answer:* Because even with Boot 3's default masking it hands an attacker a
reconnaissance kit: every configuration key name (`spring.datasource.password` says
"SQL database, password auth"; the exposure list says which endpoints to try next), all
host environment-variable names (84 on our machine — a name like `AWS_SECRET_ACCESS_KEY`
is a phishing map), and exact origins including artifact name and version — matchable
against known vulnerabilities. Masking is one yml line from off
(`show-values: ALWAYS` — set in dev, copied to prod), and the habit that over-exposes
(`include: "*"`) also opens `/actuator/heapdump`: raw JVM memory where secrets are
unmasked by definition. So: never public without authentication — enforced in Phase 13
(Spring Security); until then, localhost only.

### Summary for revision

- Drill pattern: AI for the story, your conditions report for the truth; disagreements
  resolve in the report's favor.
- DataSource chain: imports line 72 → `@ConditionalOnClass` (JDBC classes) +
  no-reactive-bean → pooled group (`AnyNestedCondition`: pool present OR explicit type)
  → Hikari (`@ConditionalOnClass` + property-matches-if-missing +
  `@ConditionalOnMissingBean`) → bean from `spring.datasource.*`.
- Change-diagnosis method: baseline report → change → rerun → diff → read reasons →
  confirm in `/beans` (displacements hide in negatives).
- Checkpoint answered: starter = list not code; presence = intent / user always wins;
  back-off proven three ways; absence diagnosed negatives-then-exclusions; `/env` =
  recon kit even masked.

---

## Task F6.9 — complete

**Done-when check, restated:** *"you can open the conditions report and explain, for
one bean you choose, the precise reason it exists or does not."* Done repeatedly, with
evidence: `dataSource` exists (full condition chain above), `jacksonObjectMapper` does
not (backed off to `myObjectMapper`, named in the report), `postgresPresentNote` exists
(`@ConditionalOnClass(name = "org.postgresql.Driver")` found it), and the trio
`dataSource`/`entityManagerFactory`/`transactionManager` vanished and returned across
two deliberate breaks.

Final state of the scratch-app: green — 374 beans, web + JPA + actuator, Postgres in
Docker, five endpoints exposed on localhost, security note on file for Phase 13.

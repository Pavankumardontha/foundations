# F5.11 — JPA, the SQL log, and Flyway

Notes for Task F5.11. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state (from F5.10):** a Docker container named `pg` running
`postgres:17-alpine` with a volume, holding a database named `zenith`. Inside `zenith`:
tables `venue` (3 rows), `event` (7 rows, including the `cancelled_at` column), `price_tier`
(6 rows), plus `app_user`, `role`, `user_role` from the modelling task. All F5.3 constraints
and F5.9 indexes exist. Next to the database sits the `zenith-app` Maven project from F5.10:
Spring Boot 3.5.16 on Java 21, with two dependencies (`spring-boot-starter-jdbc` and the
`postgresql` driver) and this `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev
```

The connection facts for every example in this task:

| Thing | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `zenith` |
| User | `postgres` |
| Password | `dev` |

## Roadmap for this task — 18 small lessons

| # | Lesson | What it gives you |
|---|--------|-------------------|
| 1 | What an ORM is | The problem all of F5.11 exists to solve. |
| 2 | JPA and Hibernate: the rulebook and the engine | Who actually runs in your app, and whose name is in every error. |
| 3 | Add the JPA starter *(subtask: add `spring-boot-starter-data-jpa`)* | One line in `pom.xml` that brings the ORM in. |
| 4 | The entity: `@Entity` and `@Id` *(subtask: entity for `event`)* | One Java class becomes the twin of the `event` table. |
| 5 | The repository *(subtask: `JpaRepository` with no implementation + NOTES.md)* | Where `findById` comes from; who builds the bean. |
| 6 | The SQL log *(subtask: `show-sql` + `format_sql` before any query)* | Your eyes on every statement Hibernate issues. |
| 7 | Derived query methods *(subtask: `findByNameContainingIgnoreCase`)* | A method name becomes a `WHERE` clause; compare with hand SQL. |
| 8 | `@ManyToOne` — the child side *(subtask: relationship, first half)* | The Java twin of `price_tier.event_id`. |
| 9 | `@OneToMany(mappedBy)` — the parent side *(subtask: relationship, second half)* | The mirror list on `Event`, and why it owns nothing. |
| 10 | The persistence context | Hibernate's notebook; explains SQL you never asked for. |
| 11 | Lazy loading | Why touching a list fires a query; the mechanism behind N+1. |
| 12 | Trigger the N+1 *(subtask: five events, 1+5 statements)* | Produce Java's most common performance bug and count it. |
| 13 | Why eager is not the fix *(subtask: one written sentence)* | What always-fetching does to a names-only list endpoint. |
| 14 | Migrations: the idea | Versioned SQL files that own the schema — concept before tool. |
| 15 | Flyway and `V1__baseline.sql` *(subtask: baseline + drop hand-made tables)* | Schema reborn from a file; `flyway_schema_history`. |
| 16 | `ddl-auto: validate` *(subtask: the absolute rule)* | Migrations own the schema; Hibernate never creates or alters. |
| 17 | Break it: schema drift *(subtask: field without migration)* | Read the exact startup failure that saves you at midnight. |
| 18 | AI exercise + checkpoint *(subtask: the N+1 prompt)* | Lock in the three N+1 fixes; answer the four checkpoint questions. |

---

## Lesson 1 — What an ORM is, and the problem it solves

### Definition

An **ORM** is a library that moves data between Java objects and database rows, in both
directions. The name stands for **Object-Relational Mapper**. "Object" means your Java
objects. "Relational" means tables made of rows and columns. "Mapper" means it matches one
shape onto the other.

### Why it is needed

This is real code from `zenith-app` (F5.10). It reads events in one city through
`JdbcClient`, a Spring tool that sends SQL text to the database and gives rows back:

```java
List<EventRow> events = db.sql("""
                SELECT e.id, e.name, v.name AS venue, e.starts_at
                FROM event e
                JOIN venue v ON v.id = e.venue_id
                WHERE v.city = :city
                ORDER BY e.starts_at
                """)
        .param("city", city)                       // bind the named parameter
        .query((rs, rowNum) -> new EventRow(       // the row mapper: one row -> one object
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("venue"),
                rs.getObject("starts_at", OffsetDateTime.class)))
        .list();                                   // run, walk all rows, collect
```

It works, but count the cost of **one** query:

- The SQL text is hand-written.
- The mapper names all four columns by hand and picks a Java type for each, by hand.
- The shape repeats for every query on every table. `zenith` already has six real tables
  (`venue`, `event`, `price_tier`, `app_user`, `role`, `user_role`). Each needs save,
  find-by-id, list, delete, plus searches. Dozens of mappers, all alike.
- Column names are strings. Type `rs.getString("nmae")` instead of `"name"` and the compiler
  says nothing — Java cannot check string contents against the database. The mistake shows up
  only at runtime, when that code path first runs.

Three-fold pain: repeated plumbing, no business meaning in it, and errors that hide until
runtime. An ORM exists to delete that plumbing.

### How it works, step by step

Analogy: the ORM is a clerk with one dictionary. The dictionary says which class matches
which table and which field matches which column. You write the dictionary **once**; the
clerk does all copying, both ways, forever.

1. **You describe the mapping once per table, on the class itself** — with annotations. An
   annotation is a label starting with `@` attached to a class or field; it does nothing by
   itself, tools read it and act (`@Bean` and `@SpringBootApplication` were annotations too).
2. **From then on you speak Java, not SQL** — a method call like "find the event with this id".
3. **The ORM writes the SQL text for you.**
4. **It sends that SQL through JDBC** — Java's standard doorway to a database: SQL text in,
   rows back. The ORM sits *on top of* JDBC and replaces nothing. The ladder:
   your code → ORM → JDBC → Postgres driver → Postgres.
5. **Rows come back; the ORM reads the dictionary again and builds Java objects.** No
   hand-written mapper runs.

### Example — what you say vs what the ORM does

| You say (Java) | The ORM writes and runs for you |
|---|---|
| `repository.findById(id)` | a `SELECT` of every mapped column `FROM event WHERE id = ?`, plus the object-building |
| `repository.save(event)` | an `INSERT` of a new row, or an `UPDATE` of the matching row |

The SQL is described in words here on purpose: in the SQL-log lesson the log gets turned on,
and the exact generated text appears in the console, readable with your own eyes.

### Walkthrough — the fate of each hand-written line under an ORM

```java
db.sql("""
        SELECT e.id, e.name, v.name AS venue, e.starts_at ...
        """)                                      // SQL text: ORM writes this for standard operations
    .param("city", city)                          // the VALUE stays yours; the plumbing goes
    .query((rs, rowNum) -> new EventRow(          // mapper: disappears entirely
            rs.getObject("id", UUID.class),       // disappears — the dictionary knows id is a UUID
            rs.getString("name"),                 // disappears — no typo can hide here any more
            rs.getString("venue"),                // disappears
            rs.getObject("starts_at", OffsetDateTime.class)))  // disappears
    .list();                                      // disappears — a List comes back by itself
```

What stays yours: the *decision* of what to fetch ("events in this city") and the values
("Hyderabad"). What leaves: the SQL text for everyday operations and every line of
column-naming, type-picking mapper code.

### Important terms

| Term | Meaning |
| --- | --- |
| **ORM** | Object-Relational Mapper: a library moving data between Java objects and table rows, both ways. |
| **Mapping** | The written match: this class = that table, this field = that column. |
| **Annotation** | A label starting with `@` on code; does nothing alone, tools read it and act. |
| **JDBC** | Java's standard doorway to a database: SQL text in, rows back. The ORM sits on top of it. |
| **Row mapper** | Hand-written code turning one result row into one object — the thing the ORM deletes. |

### Common mistakes

- **"With an ORM I never think about SQL again."** Backwards. The ORM *writes* SQL, so now
  you must *read* SQL you did not write. Engineers who skip the reading ship slow queries
  without knowing.
- **"The ORM replaced JDBC."** No. It sits on JDBC and calls it for you. Same doorway, same
  driver, same Postgres.
- **Trusting it blindly.** The ORM makes reasonable choices that can go badly at scale. The
  N+1 lesson in this task produces six queries from innocent-looking code; only the log tells.

### Where it is used

Nearly every Java backend runs Spring Boot with an ORM: banks, ticketing, food delivery,
airlines. The professional pattern is both tools in one app: the ORM for everyday reads and
writes, hand-written SQL (`JdbcClient`) for reports and hot paths. That is where `zenith-app`
is headed — this task's "done when" requires the app to talk to Postgres through *both*.

### Link to the task

The first subtask says: *"Add `spring-boot-starter-data-jpa`. Keep the datasource
configuration."* That one dependency brings the ORM in. Every later subtask — entity,
repository, SQL log, relationship, N+1, Flyway's ownership rule — is about living with what
the ORM does on your behalf. No laptop work for this lesson.

### Summary for revision

- ORM = library that maps Java objects ↔ table rows, both directions, from one mapping
  description.
- It kills three pains: repeated mapper code, meaningless plumbing, runtime-only typo errors.
- It writes SQL for you and runs it through JDBC — it replaces nothing underneath.
- The ladder: your code → ORM → JDBC → driver → Postgres.
- New duty: read the SQL it writes. The log is the only window.

### Check questions, with answers

**Question 1.** A teammate says: *"Good news — we added an ORM to our Java service, so the
service no longer uses JDBC, and nobody has to look at SQL any more."* There are two mistakes
in that sentence. Name both, one line each.

**Answer.** Mistake 1: "no longer uses JDBC" — the ORM sits *on top of* JDBC and calls it for
every statement; the doorway and the driver are still there. Mistake 2: "nobody has to look
at SQL" — the ORM *generates* SQL, so engineers must now read SQL they did not write (via the
SQL log), or they ship slow queries without knowing.

**Question 2.** Here is a complete piece of Java. It uses `JdbcClient`, a Spring tool that
sends SQL text to the database and gives rows back. `EventRow` is a record with four fields:
`id`, `name`, `venue`, `startsAt`.

```java
List<EventRow> events = db.sql("""
                SELECT e.id, e.name, v.name AS venue, e.starts_at
                FROM event e
                JOIN venue v ON v.id = e.venue_id
                WHERE v.city = :city
                ORDER BY e.starts_at
                """)
        .param("city", city)
        .query((rs, rowNum) -> new EventRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("venue"),
                rs.getObject("starts_at", OffsetDateTime.class)))
        .list();
```

**(a)** Once an ORM knows the mapping, which lines would it write for you?
**(b)** Suppose a teammate changes `rs.getString("name")` to `rs.getString("nmae")`. Does
the compiler complain? When would the team find out?

**Answer (a).** The whole mapper block — the four `rs.get...` lines, the `new EventRow(...)`
construction, and the `.list()` collection — disappears: the ORM builds objects from rows by
itself. For standard operations (find by id, save, delete, list all) the SQL text disappears
too. What stays yours: *naming* the search you want (this one filters by a joined table's
column, so you would still declare a small query method for it) and supplying the value
(`city`).

**Answer (b).** The compiler says nothing — `"nmae"` is legal string contents, and Java
cannot check strings against the database. The team finds out at runtime, the first time that
code path runs, when the result has no column called `nmae` and the mapper throws an error
naming the missing column. With an ORM the same typo cannot hide: field names are checked by
the compiler, and the mapping is checked once at startup.

---

## Lesson 2 — JPA and Hibernate: the rulebook and the engine

### Definition

**JPA** (Java Persistence API) is a written standard: an agreed list of annotation names,
interface names, and rules that every Java ORM promises to follow. It contains **no working
code** — it is paper. **Hibernate** is a real program, an ORM engine that *implements* that
standard. Spring Boot ships Hibernate as its default engine.

Naming note: JPA's official name today is **Jakarta Persistence** — the standard moved to the
Jakarta foundation. Practical effect: its annotations live in packages starting with
`jakarta.persistence`, so import lines read `jakarta.persistence.Entity`. Everyone still says
"JPA" out loud.

### Why it is needed

Without a standard, every ORM would invent its own labels (`@HibernateTable` here,
`@MyOrmObject` there), and entity classes would be glued to one vendor forever. With the
standard, `@Entity` is a rulebook name any compliant engine understands.

The daily reason to keep the names straight: paper cannot act. A standard cannot write SQL or
throw an error. Only the engine does things. When the app behaves surprisingly, the actor is
always Hibernate — *"when something surprising happens, Hibernate did it."* Knowing who is
who tells you what to search for and which docs apply.

### How it works, step by step

The same promise-vs-worker shape appeared one floor down in F5.10, with real output:

```text
Connected. Real object behind the Connection interface: org.postgresql.jdbc.PgConnection
```

`java.sql.Connection` is a promise shipped in Java; `org.postgresql.jdbc.PgConnection` is the
worker the driver supplies. One floor up:

| Floor | The promise (standard) | The worker (real code) |
|---|---|---|
| Talking to one database | `java.sql.*` — JDBC interfaces | `org.postgresql.*` — the driver |
| Mapping objects to rows | `jakarta.persistence.*` — JPA | `org.hibernate.*` — Hibernate |

Over the life of the app:

1. You label classes with standard names: `@Entity`, `@Id` (imports begin `jakarta.persistence`).
2. At startup an engine must be present — Spring Boot puts Hibernate on the classpath.
3. Hibernate reads the labels and builds its dictionary: class ↔ table, field ↔ column.
4. At runtime every real action — writing SQL, sending it through JDBC, building objects —
   is Hibernate's code running.
5. Swapping engines (rare) leaves `@Entity` classes untouched; only the engine jar changes.
   That is the point of a standard.

### Example — real names, sorted onto the right floor

| Name | Which floor | How you can tell |
|---|---|---|
| `@Entity`, `@Id`, `@ManyToOne`, `@OneToMany` | JPA, the standard | imports begin `jakarta.persistence` |
| error classes in startup failures | Hibernate, the engine | names begin `org.hibernate` |
| `spring.jpa.show-sql` | Spring's switchboard for JPA | key starts `spring.jpa` |
| `spring.jpa.properties.hibernate.format_sql` | a knob passed straight to Hibernate | key contains `hibernate` |

The last two rows are exactly the settings the SQL-log subtask asks for; the layer names are
written inside the keys.

### Walkthrough — one future moment, traced

1. A class `Event` gets the `@Entity` label — a `jakarta.persistence` name, the rulebook.
2. The app starts. Hibernate (`org.hibernate` code) finds the label and records:
   class `Event` ↔ table `event`.
3. A row is requested. Hibernate writes the `SELECT`, sends it through JDBC, builds an
   `Event` object.
4. Later in this task the schema gets broken on purpose. The startup failure names a class
   starting with `org.hibernate` — the engine checked the database and complained. Paper
   never checks anything, because paper cannot run.

### Important terms

| Term | Meaning |
| --- | --- |
| **JPA / Jakarta Persistence** | The written standard: names and rules, no working code. |
| **Hibernate** | The engine implementing JPA; Spring Boot's default; the thing that acts. |
| **Standard vs implementation** | A promise on paper vs the worker code that fulfils it. |
| **`jakarta.persistence`** | Package prefix of every standard annotation you will import. |
| **`org.hibernate`** | Package prefix of the engine — the name in runtime errors. |

### Common mistakes

- **"JPA is doing something weird."** Paper does nothing. If something happened, Hibernate
  did it. Blaming the right actor leads to the right docs.
- **Treating the names as interchangeable in config.** `format_sql` sits under
  `spring.jpa.properties.hibernate.` because it is an engine knob. A wrong layer path makes
  the setting silently do nothing.
- **Thinking Hibernate is the only engine.** EclipseLink and others exist; Hibernate is just
  Spring Boot's default — and the one whose habits matter here, because its code runs.

### Where it is used

Reading stack traces (first line names the thrower), choosing search words (*Hibernate* for
behaviour, *JPA* for mapping names), and the classic interview question — "difference between
JPA and Hibernate?": JPA is the rulebook with no working code; Hibernate is the engine that
implements it; Spring Boot defaults to Hibernate.

### Link to the task

Subtask B ends with *"write down in `NOTES.md` who created it"* — a precise answer needs
these names. Subtask J's planned startup failure is thrown by an `org.hibernate` class;
reading that prefix names the actor.

### Summary for revision

- JPA = rulebook (names + rules, no code). Hibernate = engine (the code that acts).
- Imports of mapping annotations: `jakarta.persistence.*`. Runtime errors: `org.hibernate.*`.
- Spring Boot defaults to Hibernate; engines are swappable in theory, classes stay.
- Config keys carry the layers: `spring.jpa.show-sql` vs
  `spring.jpa.properties.hibernate.format_sql`.
- When something surprising happens at runtime, Hibernate did it.

### Check questions, with answers

**Question 1.** An app fails at startup. The first line of the error names a class whose full
name starts with `org.hibernate`. A teammate asks: "Is the JPA standard throwing this, or our
ORM engine?" Give the answer, plus the one-sentence reason a standard can never be the thing
that throws at runtime.

**Answer.** The engine — Hibernate. A standard is paper: names and rules with no running
code, and something that never runs can never throw; only the engine's running code can.

**Question 2.** When a Java app saves an object through an ORM, four things take part: *the
Postgres driver*, *Hibernate*, *your code*, *JDBC*. (JDBC is Java's standard doorway to a
database: SQL text goes in, rows come back. A driver is the database-specific worker code
behind that doorway.) Put the four in calling order, top to bottom, starting from your code.

**Answer.** Your code → Hibernate → JDBC → the Postgres driver (→ Postgres itself). Your code
asks in Java terms; Hibernate writes the SQL and calls JDBC; JDBC hands the work to the
registered driver; the driver speaks Postgres's wire language to the server.

---

## Lesson 3 — Add the JPA starter (hands-on, subtask A)

### Definition

A **starter** is one Maven dependency that pulls in a bundle of libraries whose versions are
picked to work together. `spring-boot-starter-data-jpa` brings the ORM stack, three layers in
one line: **Spring Data JPA** (Spring's helper layer — its job is repositories, built in a
later lesson), **JPA the standard** (the jar with the `jakarta.persistence` annotations), and
**Hibernate the engine**.

### Why it is needed

1. Without the engine on the **classpath** (the list of places Java searches for compiled
   code at run time), an `@Entity` label is paper nobody reads. The starter is how the engine
   arrives.
2. Version safety. Hibernate, Spring Data JPA, and the standard jar must fit exactly. No
   dependency in the pom has a `<version>` tag: the parent `spring-boot-starter-parent 3.5.16`
   holds a tested version list and fills them in.

### How it works — the change and what it fans out into

Added to `pom.xml`, inside `<dependencies>`:

```xml
<!-- F5.11 subtask A: the JPA starter - brings Spring Data JPA (repositories),
     the JPA standard jar (jakarta.persistence), and Hibernate (the engine). -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

Real `mvn dependency:tree` output, trimmed:

```text
[INFO] zenith:zenith-app:jar:0.0.1-SNAPSHOT
[INFO] +- org.springframework.boot:spring-boot-starter-jdbc:jar:3.5.16:compile
[INFO] |  +- com.zaxxer:HikariCP:jar:6.3.3:compile
[INFO] +- org.postgresql:postgresql:jar:42.7.11:runtime
[INFO] \- org.springframework.boot:spring-boot-starter-data-jpa:jar:3.5.16:compile
[INFO]    +- org.hibernate.orm:hibernate-core:jar:6.6.53.Final:compile
[INFO]    |  +- jakarta.persistence:jakarta.persistence-api:jar:3.1.0:compile
[INFO]    +- org.springframework.data:spring-data-jpa:jar:3.5.13:compile
[INFO]    |  +- org.springframework:spring-orm:jar:6.2.19:compile
```

One starter line fanned out into the engine, the standard jar, and the helper layer; the
parent chose every version number.

### Example — real startup output after the change (`application.yml` untouched)

```text
.s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
.s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 4 ms. Found 0 JPA repository interfaces.
com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@46185a1b
org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.6.53.Final
j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
zenith.ZenithApp                         : Started ZenithApp in 0.828 seconds
>>> events in Hyderabad: 4
>>> EventRow[id=aaaaaaaa-0000-0000-0000-000000000001, name=Monsoon Rock Night, venue=Riverside Arena, startsAt=2026-11-20T13:30Z]
```

### Walkthrough — each new line decoded

1. `Bootstrapping Spring Data JPA repositories` — the helper layer woke and went looking for
   repository interfaces. The logger name (`RepositoryConfigurationDelegate`, a Spring Data
   class) names the speaker.
2. `Found 0 JPA repository interfaces` — none declared yet. The repository lesson makes this
   say 1 — that change is the proof the repository was seen.
3. `HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection` — the connection pool,
   unchanged (a pool opens connections at startup and lends them out, because fresh opens are
   slow).
4. `HHH000412: Hibernate ORM core version 6.6.53.Final` — the engine introducing itself.
   `HHH` is Hibernate's log-code prefix: see `HHH`, think "the engine is talking".
5. `Initialized JPA EntityManagerFactory for persistence unit 'default'` — Hibernate built
   its machine room: the one big object created at startup holding its dictionary and
   settings. "Persistence unit 'default'" is the name of that settings bundle.
6. `>>> events in Hyderabad: 4` — the old `JdbcClient` path, untouched. Both tools live in
   one app, as the task's "done when" requires.

Nothing else changed: no new config, no entities, no errors. A starter adds ability; nothing
forces its use.

### Important terms

| Term | Meaning |
| --- | --- |
| **Starter** | One dependency that pulls a tested bundle of libraries. |
| **Classpath** | The list of places Java searches for compiled code at run time. |
| **Spring Data JPA** | Spring's helper layer on top of JPA; provides repositories. |
| **`EntityManagerFactory`** | Hibernate's machine room, built once at startup. |
| **Persistence unit** | A named bundle of ORM settings; Spring Boot makes one called `default`. |
| **`HHH`** | Prefix of every Hibernate log code — marks the engine speaking. |

### Common mistakes

- **Adding `hibernate-core` directly with a hand-picked version** — invites clashes; the
  starter and parent exist to prevent that.
- **Mistyping the artifact name.** Done on purpose (`...data-jpaa`); the real error:

  ```text
  [ERROR] 'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter-data-jpaa:jar is missing. @ line 40, column 21
  ```

  Decoded: Maven demands a *version* because the parent's list only knows real names — a
  typo'd name has no managed version. Fix the spelling; never add a `<version>` tag.
- **Expecting new settings.** The starter reuses the existing datasource. "Keep the
  configuration" is satisfied by editing nothing.

### Where it is used

Every Spring Boot service is a pile of starters (`-web`, `-test`, `-data-jpa`). Reading a pom
and naming what each starter brings is how engineers size up a strange codebase quickly.

### Link to the task

Subtask A, word for word: *"Add `spring-boot-starter-data-jpa`. Keep the datasource
configuration."*

### Do it yourself (second laptop)

**Prerequisite state** — end of F5.10: container `pg` with database `zenith` and seed rows,
Java 21, Maven, project `zenith-app`. Check:

```bash
docker ps                 # expect: pg ... Up
java -version             # expect: openjdk version "21..."
mvn -v                    # expect: Apache Maven 3.9.x
```

If `pg` shows `Exited`: `docker start pg`. If it does not exist, recreate and reseed
(F5.10 setup, compressed):

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
docker exec pg psql -U postgres -c 'CREATE DATABASE zenith;'
docker exec -i pg psql -U postgres -d zenith < F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/db-setup.sql
```

**Step 1 — F5.11 copy of the project** (keeps F5.10 frozen):

```bash
cd ~/Projects/foundations
mkdir -p F5.11-jpa-the-sql-log-and-flyway
cp -R F5.10-java-talks-to-postgres-jdbc-and-jdbcclient/zenith-app F5.11-jpa-the-sql-log-and-flyway/
rm -rf F5.11-jpa-the-sql-log-and-flyway/zenith-app/target
cd F5.11-jpa-the-sql-log-and-flyway/zenith-app
```

**Step 2 — add the starter** to `pom.xml` inside `<dependencies>`, after `postgresql`
(exact XML in the "How it works" section of this lesson). No `<version>`. Do not touch
`application.yml`.

**Step 3 — look at what arrived:** `mvn dependency:tree` — expect the subtree shown in the
"How it works" section. First run prints many `Downloading from central:` lines; normal,
happens once.

**Step 4 — run:** `mvn spring-boot:run` — expect, in order: `Bootstrapping Spring Data JPA
repositories` → `Found 0 JPA repository interfaces` → `HHH000412: Hibernate ORM core version
6.6.53.Final` → `Initialized JPA EntityManagerFactory` → `>>> events in Hyderabad: 4`. The
app exits by itself (no web server).

**Expected failure, for recognition:** a typo in the artifact name stops the build at once
with `'dependencies.dependency.version' for ...:jar is missing`. Read: "name not on the
parent's list". Fix the spelling.

**Done when:** the log shows the Hibernate version line and `Found 0 JPA repository
interfaces`, and the old `>>>` lines still print.

### Summary for revision

- A starter = one line, a tested bundle. This one brings Spring Data JPA + the standard jar +
  Hibernate.
- The parent pom owns every version number; dependencies stay version-less.
- After adding it: engine boots (`HHH000412`), machine room built (`EntityManagerFactory`),
  repositories scanned (`Found 0`), old JdbcClient path untouched.
- A typo'd starter name fails as a *missing version* — fix the name, never add a version.

### Check questions, with answers

**Question 1.** A `pom.xml` has parent `spring-boot-starter-parent` version 3.5.16 and a new
dependency `spring-boot-starter-data-jpa` with no `<version>` tag. A teammate asks: "Nobody
typed a Hibernate version anywhere. The app runs Hibernate 6.6.53.Final. Where did that
number come from?" Answer in two sentences.

**Answer.** The parent pom carries a tested list of version numbers for every artifact Spring
Boot knows, and Maven fills them in during the build. Boot 3.5.16's list pins Hibernate at
6.6.53.Final, so that is what arrives — no human typed it.

**Question 2.** Two real startup lines; the left part names the class that printed each:

```text
.s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 4 ms. Found 0 JPA repository interfaces.
org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.6.53.Final
```

**(a)** Which layer printed each line — Spring Data JPA (helper), JPA (standard), or
Hibernate (engine) — and what in the text tells you? **(b)** The app has zero entities and
zero repositories, yet started cleanly. Why is that not a problem?

**Answer (a).** Line 1: Spring Data JPA — the printing class is a Spring Data one and the
message says "Spring Data repository scanning". Line 2: Hibernate — the logger is
`org.hibernate.Version` and the code starts with `HHH`, the engine's prefix. The standard
printed nothing, because paper has no code to print with.

**Answer (b).** The starter only adds ability. An engine with an empty dictionary is a valid
state: it boots, reports what it found (zero), and idles. Nothing in the stack requires
entities to exist before it agrees to start.

---

## Lesson 4 — The entity: `@Entity` and `@Id` (hands-on, subtask B first half)

### Definition

An **entity** is a Java class that is the twin of one database table, marked with `@Entity`.
Inside it, `@Id` marks the field holding the **primary key** — the column whose value
identifies exactly one row (`event.id uuid` here).

### Why it is needed

The ORM's dictionary starts empty; `@Entity` writes an entry into it. And the clerk must know
which field identifies a row — find-by-id, update, delete all hang off the key — so `@Id` is
mandatory, enforced by a real startup error (below).

### How it works — three rules

1. **Names map by convention.** Spring Boot defaults: lowercase the Java name, turn each
   camelCase hump into an underscore. `Event` → table `event`; field `startsAt` → column
   `starts_at`. No name strings anywhere, so no string typos possible.
2. **A plain class with a no-argument constructor.** Hibernate builds the object *empty*
   through a no-arg constructor (fine to make it `protected` — a sign only Hibernate uses
   that door), then fills fields from the row. **A record cannot be an entity**: records have
   no no-arg constructor and cannot be filled afterwards.
3. **Map only the columns you care about.** `event` has six columns; the entity maps three.
   Unmapped table columns are ignored. Nothing forces a field per column.

### Example — the real files

`src/main/java/zenith/Event.java`:

```java
package zenith;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.OffsetDateTime;
import java.util.UUID;

// F5.11: the entity - this class is the Java twin of the "event" table.
@Entity
public class Event {

    @Id
    private UUID id;                 // column: id   (the primary key)

    private String name;             // column: name
    private OffsetDateTime startsAt; // column: starts_at  (camelCase -> snake_case)

    protected Event() {}             // Hibernate's door: builds empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
}
```

Proof runner added to `ZenithApp.java` (import `jakarta.persistence.EntityManagerFactory`):

```java
// Ask Hibernate's machine room which entities its dictionary holds.
@Bean
CommandLineRunner showMappedEntities(EntityManagerFactory emf) {
    return args -> System.out.println(">>> entities Hibernate knows: "
            + emf.getMetamodel().getEntities().stream()
                  .map(e -> e.getName())
                  .toList());
}
```

Real output:

```text
>>> entities Hibernate knows: [Event]
>>> events in Hyderabad: 4
```

### Walkthrough

1. `@Entity` — at startup Hibernate scans, finds the label, writes the entry: class `Event` ↔
   table `event`.
2. `@Id private UUID id` — the key; Java `UUID` pairs with Postgres `uuid` out of the box.
3. `startsAt` → `starts_at`; `OffsetDateTime` (a moment with its UTC offset) pairs with
   `timestamptz`.
4. `protected Event() {}` — Hibernate's door; no code of ours calls it.
5. The runner reads the dictionary's table of contents: `getMetamodel().getEntities()`.
6. **No query ran against `event`.** Registering a mapping touches no data — the first JPA
   query waits until the SQL log is on (subtask C's ordering).

### Important terms

| Term | Meaning |
| --- | --- |
| **Entity** | A class mapped to one table, marked `@Entity`. |
| **`@Id`** | Marks the field holding the primary key. |
| **Primary key** | The column whose value identifies exactly one row. |
| **Naming convention** | Lowercase + camelCase→snake_case: `startsAt` ↔ `starts_at`. |
| **No-arg constructor** | Hibernate's door for building objects empty before filling them. |

### Common mistakes — with the real errors

**Forgetting `@Id`** (produced for real):

```text
Caused by: org.hibernate.AnnotationException: Entity 'zenith.Event' has no identifier
    (every '@Entity' class must declare or inherit at least one '@Id' or '@EmbeddedId' property)
```

Loud, instant, at startup, naming the class — a *good* failure. `org.hibernate.` = the engine
complaining.

**Using a record as an entity** (produced for real — `@Entity record Bad(@Id UUID id, String
name) {}`): the app **started anyway**. Only trace:

```text
o.h.m.i.EntityInstantiatorPojoStandard   : HHH000182: No default (no-argument) constructor for class: zenith.Bad (class must be instantiated by Interceptor)
>>> entities Hibernate knows: [Event, Bad]
```

The crash is deferred to the first time a row must become a `Bad` object — there is no door
to build it through. Lesson under the lesson: **a clean startup is not proof a mapping works;
the skimmed-past log line is the one that mattered.**

**Dutifully mapping every column.** Not required; map what the code uses.

### Where it is used

Each table a service owns gets exactly one entity class; those names (`Event`, `Order`,
`Customer`) become the vocabulary of the codebase.

### Link to the task

Subtask B, first half: *"Write an `@Entity` for `event` with `@Id`."* The repository half
comes next.

### Do it yourself (second laptop)

Prerequisite: Lesson 3 state (`zenith-app` with the JPA starter), `pg` running.

1. Create `src/main/java/zenith/Event.java` with the exact entity code above.
2. In `ZenithApp.java`: add import `jakarta.persistence.EntityManagerFactory;` and the
   `showMappedEntities` runner above, next to the existing `@Bean`.
3. `mvn spring-boot:run` → expect `>>> entities Hibernate knows: [Event]` plus the old
   `>>> events in Hyderabad: 4` (runner order may vary; both must appear).
4. Break it: comment out `@Id`, run, read the `AnnotationException` above, restore `@Id`,
   run again cleanly.

**Done when:** the log prints `>>> entities Hibernate knows: [Event]` and you have seen the
missing-`@Id` error once.

### Summary for revision

- `@Entity` = dictionary entry (class ↔ table); `@Id` = which field identifies a row.
- Names map by convention: lowercase + camel→snake. `startsAt` ↔ `starts_at`.
- Plain class + no-arg constructor required; records can never be entities.
- Map only needed columns; extra table columns are ignored.
- Missing `@Id` fails loud at startup; a record-entity fails *quietly, later* — read the log.

### Check questions, with answers

**Question 1.** A Spring Boot app with default settings has this entity:

```java
@Entity
public class PriceTier {
    @Id private UUID id;
    private String name;
    private OffsetDateTime createdAt;
    protected PriceTier() {}
}
```

Which *table* will Hibernate read for this class, and which *column* for the field
`createdAt`? State the naming rule.

**Answer.** Table `price_tier`; column `created_at`. Rule: lowercase the Java name and turn
each camelCase hump into an underscore (`PriceTier` → `price_tier`, `createdAt` →
`created_at`).

**Question 2.** A teammate starts an app; the log shows the line below, then the app starts
cleanly and everything looks normal:

```text
o.h.m.i.EntityInstantiatorPojoStandard : HHH000182: No default (no-argument) constructor for class: zenith.Coupon (class must be instantiated by Interceptor)
```

The teammate says: "It started, so the `Coupon` mapping is fine." Is it fine? What will
happen, and when?

**Answer.** Not fine. `Coupon` has no no-argument constructor (it is likely a record or has
only an all-args constructor), so Hibernate has no door to build it through. Nothing fails at
startup; the first time a query loads a `Coupon` row, object creation fails and that request
blows up. Fix: make it a plain class with a `protected Coupon() {}` constructor.

---

## Lesson 5 — The repository: the interface you never implement (hands-on, subtask B second half)

### Definition

A **repository** is an interface declared for one entity's data operations — never
implemented by hand. `JpaRepository<Event, UUID>` is Spring Data JPA's ready-made base
interface; extending it inherits ~20 working methods (`findById`, `findAll`, `save`,
`deleteById`, `count`, …). Type arguments: the entity class and the type of its `@Id` field.

### Why it is needed

The entity gave Hibernate a dictionary entry, but a dictionary has no verbs. Hand-writing a
class per entity with the same twenty methods would be the Lesson-1 plumbing story one floor
up. So: declare the promise, let Spring Data JPA build the worker at startup.

### How it works — the startup story

1. Spring Data scans for interfaces extending its repository marker (the scan that reported
   `Found 0` before).
2. For each, it generates a **proxy** — a stand-in object created during startup that
   implements the interface. No `.java` file for it exists.
3. Calls on the proxy are routed to the prewritten generic class `SimpleJpaRepository`,
   bound to the entity's mapping; it talks to Hibernate, which talks to JDBC.
4. The proxy is registered as a **bean** — an object Spring builds, manages, and injects.

### Example — the real file and the proof

The entire repository file:

```java
package zenith;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// The repository - an interface we declare and never implement.
// Spring Data JPA builds the working object at startup.
public interface EventRepository extends JpaRepository<Event, UUID> {
}
```

Proof runner (runs **no query** — the SQL log must be on before the first JPA query):

```java
// Prove the repository bean exists - without running any query yet.
@Bean
CommandLineRunner showRepositoryBean(EventRepository repo) {
    return args -> {
        System.out.println(">>> repository bean injected: " + (repo != null));
        System.out.println(">>> its runtime class: " + repo.getClass().getName());
        System.out.println(">>> the worker behind it: "
                + org.springframework.aop.framework.AopProxyUtils.ultimateTargetClass(repo).getName());
    };
}
```

Real output:

```text
Finished Spring Data repository scanning in 15 ms. Found 1 JPA repository interface.
>>> repository bean injected: true
>>> its runtime class: jdk.proxy2.$Proxy93
>>> the worker behind it: org.springframework.data.jpa.repository.support.SimpleJpaRepository
```

### Walkthrough

1. `Found 1 JPA repository interface.` — the scanner that said `Found 0` now reports 1; the
   one-word change proves the interface was seen.
2. `repository bean injected: true` — Spring had the object ready; nothing of ours implements
   the interface.
3. `jdk.proxy2.$Proxy93` — a class name no human wrote; generated during startup.
4. `SimpleJpaRepository` — the prewritten worker behind the stand-in, configured for `Event`
   and `UUID`.

**Who created it (the `NOTES.md` answer):** Spring Data JPA, at startup — a JDK dynamic proxy
over `SimpleJpaRepository`, registered as the bean `eventRepository`. Full note with evidence
in `F5.11-jpa-the-sql-log-and-flyway/NOTES.md`.

### Important terms

| Term | Meaning |
| --- | --- |
| **Repository** | An interface of data verbs for one entity; implemented by Spring Data at startup. |
| **`JpaRepository<T, ID>`** | Base interface bringing ~20 ready methods; `T` = entity, `ID` = key type. |
| **Proxy** | A stand-in object generated at runtime that implements your interface. |
| **Bean** | An object Spring builds, manages, and injects. |
| **`SimpleJpaRepository`** | Spring Data's prewritten class doing the real work behind the proxy. |
| **Managed type** | A type present in Hibernate's dictionary (i.e., a real `@Entity`). |

### Common mistakes — with the real error

- **Implementing the interface yourself.** Defeats the mechanism; the emptiness is the
  feature.
- **Repository for a non-entity** (produced for real — class `Plain` without `@Entity`):

  ```text
  Error creating bean with name 'plainRepository' ... : Not a managed type: class zenith.Plain
  ```

  "Managed type" = in Hibernate's dictionary. `Plain` lacks `@Entity`, so Spring Data cannot
  bind a worker. Fix: annotate the entity — never write an implementation.
- **Querying before the log is on.** A process error, not a code error: the first generated
  SQL should be one you can see.

### Where it is used

The standard data layer of Spring backends: services inject repositories and speak verbs;
tests replace the interface with a fake.

### Link to the task

Subtask B, second half: repository with no implementation; start the app; confirm the bean;
record in `NOTES.md` who created it. All verified.

### Do it yourself (second laptop)

Prerequisite: Lesson 4 state, `pg` running.

1. Create `src/main/java/zenith/EventRepository.java` with the exact code above.
2. Add the `showRepositoryBean` runner to `ZenithApp.java` (code above; no new imports —
   the one class is named in full inline).
3. `mvn spring-boot:run` → expect `Found 1 JPA repository interface.`, then the three `>>>`
   proof lines (`$Proxy` number may differ — it is generated).
4. Break it: add `Plain.java` (`public class Plain { private java.util.UUID id; }`) and
   `PlainRepository.java` (interface extending `JpaRepository<Plain, java.util.UUID>`); run;
   read `Not a managed type: class zenith.Plain`; delete both files; run cleanly.
5. Write `NOTES.md` in the F5.11 folder: who created the repository implementation, in your
   own words, with your own three evidence lines.

**Done when:** `Found 1 JPA repository interface.` appears, the proof lines print, and
`NOTES.md` answers "who created it".

### Summary for revision

- Declare the interface; Spring Data JPA builds the worker at startup.
- The bean is a generated proxy; the real work happens in `SimpleJpaRepository`.
- `Found N JPA repository interfaces` is the scanner's report — watch that number.
- `Not a managed type: X` = X is not an `@Entity`; fix the entity, never implement the
  interface.

### Check questions, with answers

**Question 1.** A codebase contains this complete file, and no class anywhere implements the
interface:

```java
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
}
```

The app starts, and a service successfully injects and uses `CouponRepository`. Explain what
happened at startup: who created the object, what kind of object it is, and which prewritten
class does the actual work when `findById` is called.

**Answer.** At startup, Spring Data JPA's scanner found the interface, generated a JDK
dynamic proxy that implements it, and registered the proxy as the bean `couponRepository`.
The injected object is that proxy — a class generated at runtime, written by no one. Every
call on it is carried out by Spring Data's prewritten `SimpleJpaRepository`, bound to
`Coupon`'s mapping, which asks Hibernate to write and run the SQL.

**Question 2.** An app fails at startup with this real error:

```text
Error creating bean with name 'plainRepository' ... : Not a managed type: class zenith.Plain
```

`PlainRepository.java` contains `public interface PlainRepository extends
JpaRepository<Plain, UUID> {}`. What is missing, and what is the correct fix — write an
implementation class, or something else?

**Answer.** `Plain` is missing `@Entity` (and an `@Id` field), so it is not in Hibernate's
dictionary — not a "managed type" — and Spring Data cannot bind a worker to it. The fix is to
make `Plain` a real entity (`@Entity` + `@Id`). Writing an implementation class is never the
fix.

---

## Lesson 6 — The SQL log: turn it on, leave it on (hands-on, subtask C)

### Definition

Two settings that make Hibernate show its work: `spring.jpa.show-sql: true` prints every SQL
statement Hibernate issues, prefixed `Hibernate:`; `spring.jpa.properties.hibernate.format_sql:
true` pretty-prints each statement across several lines.

### Why it is needed

The ORM writes SQL on your behalf; the log is the only window onto it. Without it, a
performance problem is unexplainable — you cannot reason about queries you have never seen.
The task's words: *not a debugging step, it is how you work.* Cost: console noise. Payoff:
nothing the engine does is invisible, and the N+1 lesson is impossible without it.

### How it works

The yml (layering from Lesson 2 made real — `spring.jpa.*` is Spring's switchboard;
everything under `spring.jpa.properties.*` passes through to Hibernate untouched):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev

  # F5.11 subtask C: the SQL log - on BEFORE the first JPA query, and it stays on
  # for the whole of Stage F. This is not a debugging step; it is how we work.
  jpa:
    show-sql: true          # Spring's switch: print every statement Hibernate issues
    properties:
      hibernate:
        format_sql: true    # Hibernate's knob: pretty-print those statements
```

Two honest limits of the window:

- **Hibernate's statements only.** The `JdbcClient` query ran in the same app and printed no
  `Hibernate:` line — hand-written SQL does not pass through the engine.
- **Values show as `?`.** Statements are prepared statements: shape with placeholders, values
  bound separately (the injection-safe mechanism). The log shows the shape.

### Example — the first JPA query this project ever ran

The Lessons 4–5 proof runners were deleted (scaffolding, job done) and replaced with:

```java
// The first JPA query ever - run only now that the SQL log is on.
@Bean
CommandLineRunner firstLoggedQuery(EventRepository events) {
    return args -> {
        UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        Event e = events.findById(id).orElseThrow();
        System.out.println(">>> loaded via JPA: " + e.getName() + " at " + e.getStartsAt());
    };
}
```

Real output:

```text
Hibernate: 
    select
        e1_0.id,
        e1_0.name,
        e1_0.starts_at 
    from
        event e1_0 
    where
        e1_0.id=?
>>> loaded via JPA: Monsoon Rock Night at 2026-11-20T13:30Z
```

### Walkthrough

1. `Hibernate:` — the prefix `show-sql` stamps on each statement; the engine speaking.
2. `select e1_0.id, e1_0.name, e1_0.starts_at` — exactly the three **mapped** fields. The
   table has six columns; the dictionary, not the table, decides what is fetched.
3. `from event e1_0` — `e1_0` is a generated nickname for the table; read past these.
4. `where e1_0.id=?` — the key lookup from `findById`; the UUID was bound to the `?`.
5. Multi-line layout = `format_sql`. Without it: one long line.
6. `>>> loaded via JPA: ...` — the clerk built the object; no mapper of ours ran.

### Important terms

| Term | Meaning |
| --- | --- |
| **`show-sql`** | Spring switch: print each Hibernate statement, prefixed `Hibernate:`. |
| **`format_sql`** | Hibernate knob (via `spring.jpa.properties.*`): pretty-print statements. |
| **`?` placeholder** | Prepared-statement slot; the value is bound separately, injection-safe. |
| **`e1_0`** | A table nickname Hibernate generates inside its SQL. |

### Common mistakes

- **Wrong nesting level — silent failure.** `format_sql` under `spring.jpa.hibernate` (instead
  of `spring.jpa.properties.hibernate`) raises no error; unknown keys are ignored. Symptom:
  SQL prints as one long line.
- **Turning it off when it gets noisy.** The noise is the information; muting it in week one
  ships the N+1 in month two.
- **Expecting all SQL.** `JdbcClient` statements never appear; this window shows the ORM only.

### Where it is used

On for all of Stage F, every run — reading generated SQL must become reflex. Real teams: on in
dev/staging; production swaps the console log for other telemetry (volume, data exposure).

### Link to the task

Subtask C, word for word, with the ordering honored: the settings went in *before* `findById`
— the first JPA query the project ever ran — and stay on.

### Do it yourself (second laptop)

Prerequisite: Lesson 5 state, `pg` running.

1. Replace `application.yml` with the exact yml above (watch indentation: `properties` under
   `jpa`, `hibernate` under `properties`, `format_sql` under `hibernate`).
2. In `ZenithApp.java`: delete `showMappedEntities` and `showRepositoryBean` (and the unused
   `jakarta.persistence.EntityManagerFactory` import); add the `firstLoggedQuery` runner above.
3. `mvn spring-boot:run` → expect the formatted `select ... where e1_0.id=?` block under
   `Hibernate:`, then `>>> loaded via JPA: Monsoon Rock Night at 2026-11-20T13:30Z`, then the
   old `>>> events in Hyderabad: 4` lines with **no** `Hibernate:` line above them.
4. Recognise the silent failure: SQL on one long line = `format_sql` at the wrong nesting
   level; fix indentation, run again.

**Done when:** every run shows generated SQL, and you can point at the proof that
`JdbcClient` statements do not appear in this log.

### Summary for revision

- `show-sql` prints Hibernate's statements; `format_sql` (via `properties`) pretty-prints.
- The log shows the ORM's SQL only, with `?` placeholders for bound values.
- The SELECT lists mapped fields — the dictionary decides, not the table.
- Wrong nesting fails silently; one-line SQL is the symptom.
- On before the first query; stays on for all of Stage F.

### Check questions, with answers

**Question 1.** The `event` table has six columns: `id`, `venue_id`, `name`, `starts_at`,
`created_at`, `cancelled_at`. An entity maps three fields: `id`, `name`, `startsAt`. The log
shows a SELECT naming only `e1_0.id, e1_0.name, e1_0.starts_at`. Why only three, and what
decides the list — the table or something else?

**Answer.** The entity's mapping decides — Hibernate's dictionary, not the table. The SELECT
names exactly the mapped fields; unmapped columns (`venue_id`, `created_at`, `cancelled_at`)
are invisible to the engine. Change the entity and the SELECT changes with it.

**Question 2.** A teammate configures:

```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      format_sql: true
```

SQL appears, but as one long line, with no error or warning anywhere. What went wrong, why no
complaint, and what is the corrected yml?

**Answer.** `format_sql` must live under `spring.jpa.properties.hibernate` — the passthrough
to the engine. Under `spring.jpa.hibernate` it is an unknown key, and unknown keys are
silently ignored, so the knob never reached Hibernate and nothing complained. Correct form:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

---

## Lesson 7 — Derived query methods (hands-on, subtask D)

### Definition

A **derived query method** is a repository method whose **name** defines the query — no SQL,
no body. Spring Data parses the name word by word and writes the SQL.
`findByNameContainingIgnoreCase(String q)` is one sentence in that language.

### Why it is needed

Everyday searches should not each need hand SQL plus a mapper. Bonus safety: the name is
checked against the entity **at startup** — the typo that compiled silently in mapper strings
(`rs.getString("nmae")`) now stops the app before it can run, with a suggested fix.

### How it works — the grammar

| Name piece | Meaning |
|---|---|
| `findBy` | a SELECT with a WHERE follows |
| `Name` | the entity **field** `name` (checked against `Event` at startup) |
| `Containing` | `LIKE` with `%` around the bound value |
| `IgnoreCase` | wrap both sides in `upper(...)` |
| `(String q)` | the value to bind |

Parsing happens at startup while the repository proxy is built; each call binds and runs.
Same grammar has more keywords (`StartingWith`, `After`, `OrderBy…`) for later.

### Example — the real method, call, and log

Repository addition:

```java
// A derived query method - the NAME is the query.
List<Event> findByNameContainingIgnoreCase(String q);
```

Runner calls it with `"night"`. Real output:

```text
Hibernate: 
    select
        e1_0.id,
        e1_0.name,
        e1_0.starts_at 
    from
        event e1_0 
    where
        upper(e1_0.name) like upper(?) escape '\'
>>> name search 'night' found 3:
>>>   Monsoon Rock Night
>>>   Open Mic Night
>>>   Sufi Night
```

### Walkthrough — generated vs hand-written

Hand version, run in psql, returned the same three rows:

```sql
SELECT id, name, starts_at FROM event WHERE name ILIKE '%night%' ORDER BY name;
```

| | Hibernate wrote | Hand version | Why |
|---|---|---|---|
| Case-insensitivity | `upper(e1_0.name) like upper(?)` | `ILIKE` | `ILIKE` is Postgres-only; Hibernate writes portable SQL |
| `%` wildcards | not in the SQL | in the SQL text | Spring Data binds the *value* as `%night%` |
| `escape '\'` | present | absent | user-typed `%`/`_` are escaped — searching `100%` means a literal `%` |
| Column list | mapped fields only | whatever you type | the dictionary decides |

Performance, one line: a leading `%` defeats ordinary indexes in both versions — fine at 7
rows, a thing to know at 7 million.

### Important terms

| Term | Meaning |
| --- | --- |
| **Derived query method** | Repository method whose name is parsed into SQL at startup. |
| **`Containing`** | LIKE with `%` around the bound value. |
| **`IgnoreCase`** | `upper(...)` on both sides — portable, unlike `ILIKE`. |
| **Property check** | Field names in the method name are validated against the entity at startup. |

### Common mistakes — with the real error

**Mistyped field name** (`findByNmaeContainingIgnoreCase`, produced for real):

```text
Could not create query for public abstract java.util.List zenith.EventRepository.findByNmaeContainingIgnoreCase(java.lang.String);
Reason: ... No property 'nmae' found for type 'Event'; Did you mean 'name'
```

Startup, not first call — and with a suggestion. Same typo as Lesson 1's mapper string, two
worlds.

**Typing `%` yourself** — `Containing` already means "% around the value"; passing `"%night%"`
double-wraps. **Assuming `ILIKE`** — derived queries stay portable; Postgres-specific SQL
belongs to `@Query` or `JdbcClient`.

### Where it is used

Everyday lookups everywhere. The grammar scales badly
(`findByNameContainingIgnoreCaseAndStartsAtAfterOrderBy...`); real teams keep names short and
switch to written-out queries when a name stops being a name.

### Link to the task

Subtask D complete: method added, called, SQL read, hand version compared, differences noted
(the table above).

### Do it yourself (second laptop)

Prerequisite: Lesson 6 state, `pg` running.

1. Add to `EventRepository.java` (plus `import java.util.List;`):
   `List<Event> findByNameContainingIgnoreCase(String q);`
2. Add runner:

```java
@Bean
CommandLineRunner searchByNameJpa(EventRepository events) {
    return args -> {
        List<Event> hits = events.findByNameContainingIgnoreCase("night");
        System.out.println(">>> name search 'night' found " + hits.size() + ":");
        for (Event e : hits) {
            System.out.println(">>>   " + e.getName());
        }
    };
}
```

3. `mvn spring-boot:run` → expect `where upper(e1_0.name) like upper(?) escape '\'` and three
   hits: Monsoon Rock Night, Open Mic Night, Sufi Night.
4. Compare by hand:
   `docker exec pg psql -U postgres -d zenith -c "SELECT id, name, starts_at FROM event WHERE name ILIKE '%night%' ORDER BY name;"`
   → same three rows. Note two differences.
5. Break: rename the method to `findByNmaeContainingIgnoreCase` in both files, run, read the
   startup error and its suggestion, rename back.

**Done when:** you can point at the generated WHERE clause, state two differences from your
hand SQL, and have seen the startup typo error once.

### Summary for revision

- The method name is the query; parsed at startup against entity fields.
- `Containing` = `%value%` bound as the value; `IgnoreCase` = `upper(...)` both sides.
- Generated SQL is portable — no `ILIKE`; escape clause protects literal `%`/`_`.
- Field typos fail at startup with a suggestion — far better than runtime string typos.

### Check questions, with answers

**Question 1.** Complete code:

```java
@Entity
public class PriceTier {
    @Id private UUID id;
    private String name;
    protected PriceTier() {}
    public String getName() { return name; }
}

public interface PriceTierRepository extends JpaRepository<PriceTier, UUID> {
    List<PriceTier> findByNameContaining(String q);   // note: no IgnoreCase
}
```

Someone calls `findByNameContaining("VIP")`. Will the WHERE clause contain `upper(...)`?
Where are the `%` wildcards? Will a row named `vip gold` match?

**Answer.** No `upper(...)` — without `IgnoreCase` the comparison is plain `like ? escape '\'`.
The `%` live in the bound value (`%VIP%`), not the SQL text. `vip gold` does **not** match:
LIKE is case-sensitive, and `VIP` ≠ `vip`.

**Question 2.** An app fails at startup with:

```text
Could not create query for ... findByNmaeContainingIgnoreCase(java.lang.String);
Reason: ... No property 'nmae' found for type 'Event'; Did you mean 'name'
```

**(a)** Who detected it, and when — compiler at compile time, Spring Data at startup, or
Postgres at query time? **(b)** Why is this timing safer than the same typo in
`rs.getString("nmae")`?

**Answer (a).** Spring Data, at startup, while parsing the method name to build the repository
proxy. The compiler cannot check it (the name is just a method name), and Postgres never saw
it (no SQL was ever generated).

**Answer (b).** The app refuses to start in every environment, instantly and loudly, before
serving a single request — and names the fix. The mapper-string typo waits silently until that
exact code path runs, which can be in production, at night, on the rarely-used endpoint.

---

## Lesson 8 — `@ManyToOne`: the child side (hands-on, subtask E first half)

### Definition

`@ManyToOne` marks a field holding the **one** side of a many-to-one relationship. Many price
tiers point at one event, so the field goes on `PriceTier` (the **child**), and its type is
the parent entity itself: `Event`, not `UUID`.

### Why it is needed

Database truth first: `price_tier` has `event_id uuid NOT NULL REFERENCES event(id)` — a
**foreign key**, a column holding the id of a row in another table, physically living in the
child's table. Unmapped, Java sees a bare `UUID` and every parent lookup is a hand-written
join plus mapper. Mapped, the clerk walks the link: `tier.getEvent().getName()`.

### How it works

- Field of the parent entity type on the child class, marked `@ManyToOne`.
- Column naming convention: field name + `_id` → field `event` ↔ column `event_id`.
- Direction rule: the annotation lives where the foreign key column lives — the many side.
- "The child owns the link" = the one column storing the relationship sits in the child's
  table. (Full ownership story: Task F6.11. Today the mapping just needs to exist.)

### Example — the real entity and run

`src/main/java/zenith/PriceTier.java`:

```java
package zenith;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.util.UUID;

// The child side of the relationship.
// Many price tiers point at one event - so the field lives HERE, on the many side.
@Entity
public class PriceTier {

    @Id
    private UUID id;        // column: id

    private String name;    // column: name

    @ManyToOne
    private Event event;    // column: event_id (field name + "_id") - the child owns the link

    protected PriceTier() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Event getEvent() { return event; }
}
```

Plus `PriceTierRepository extends JpaRepository<PriceTier, UUID>` and a runner loading the
VIP tier (`bbbbbbbb-...-0002`) and printing `tier.getEvent().getName()`. Real output:

```text
Hibernate: 
    select
        pt1_0.id,
        e1_0.id,
        e1_0.name,
        e1_0.starts_at,
        pt1_0.name 
    from
        price_tier pt1_0 
    left join
        event e1_0 
            on e1_0.id=pt1_0.event_id 
    where
        pt1_0.id=?
>>> tier 'VIP' belongs to event 'Monsoon Rock Night'
```

### Walkthrough

1. One statement, two tables.
2. `left join event e1_0 on e1_0.id=pt1_0.event_id` — the foreign key walked by a join nobody
   wrote; the `on` condition is the `REFERENCES` relationship.
3. Select list mixes both tables' mapped columns.
4. Result: a `PriceTier` holding a real, filled `Event`; `getEvent().getName()` needed no
   second query.
5. **Unasked-for fetching:** the code wanted a tier; Hibernate also fetched the whole event,
   immediately — `@ManyToOne`'s default (**eager**). Sometimes right, sometimes waste;
   Lessons 10–13 are about that choice. The log told what the Java never said.

### Important terms

| Term | Meaning |
| --- | --- |
| **Foreign key** | A column holding the id of a row in another table (`price_tier.event_id`). |
| **Child / many side** | The table holding the foreign key column; gets `@ManyToOne`. |
| **`@ManyToOne`** | Field on the child, typed as the parent entity, mapped to `<field>_id`. |
| **Eager fetching** | Fetching a relation immediately — `@ManyToOne`'s default. |

### Common mistakes

- **`UUID` field with `@ManyToOne`** — the annotation needs an entity type; the clerk
  converts object reference ↔ stored id.
- **`@ManyToOne` on the parent** — backwards; it lives where the FK column lives.
- **Expecting a column named `event`** — convention appends `_id`; a differently-named legacy
  column would need `@JoinColumn`, not needed here.

### Where it is used

Every foreign key's child side: `Order`→`Customer`, `Ticket`→`Order`, `Comment`→`Post`. The
child-side mapping is the bread and butter; parent-side lists are optional sugar.

### Link to the task

Subtask E first half, exact task code: `@ManyToOne private Event event;` on PriceTier — the
child owns the link.

### Do it yourself (second laptop)

Prerequisite: Lesson 7 state, `pg` running.

1. Create `PriceTier.java` exactly as above.
2. Create `PriceTierRepository.java`: interface extending `JpaRepository<PriceTier, UUID>`.
3. Add runner:

```java
@Bean
CommandLineRunner loadTierWithEvent(PriceTierRepository tiers) {
    return args -> {
        UUID vipId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        PriceTier tier = tiers.findById(vipId).orElseThrow();
        System.out.println(">>> tier '" + tier.getName()
                + "' belongs to event '" + tier.getEvent().getName() + "'");
    };
}
```

4. `mvn spring-boot:run` → expect the `left join event ... on e1_0.id=pt1_0.event_id` block,
   `>>> tier 'VIP' belongs to event 'Monsoon Rock Night'`, and startup now reporting
   `Found 2 JPA repository interfaces.`

**Done when:** the join appears in the log and the tier prints its event's name with no second
query.

### Summary for revision

- FK column lives in the child table; so does the `@ManyToOne` field.
- Field `event` (type `Event`) ↔ column `event_id` by convention.
- One `findById` produced one statement joining both tables — eager is the to-one default.
- The log shows what the Java never said; keep reading it.

### Check questions, with answers

**Question 1.** Schema: `ticket_order` (`id uuid PRIMARY KEY`, `buyer_email text`) and
`ticket` (`id uuid PRIMARY KEY`, `order_id uuid NOT NULL REFERENCES ticket_order(id)`,
`seat text`). Mapping classes `TicketOrder` and `Ticket`: where does `@ManyToOne` go, what is
the field declaration, and which column does it map by convention?

**Answer.** On `Ticket` — the child, whose table holds the foreign key. Declaration:
`@ManyToOne private TicketOrder order;`. Convention maps field `order` to column `order_id`,
which is exactly the schema's column.

**Question 2.** A real log block from an app whose `PriceTier` has `@ManyToOne private Event
event;` (block shown in the Example section above, ending `where pt1_0.id=?`): **(a)** how
many statements, how many tables; **(b)** which clause walks the foreign key; **(c)** the code
called only `tiers.findById(...)` — why do event columns appear?

**Answer (a).** One statement touching two tables (`price_tier`, `event`). **(b)** The join
condition `on e1_0.id=pt1_0.event_id`. **(c)** `@ManyToOne` defaults to eager fetching: the
parent is fetched immediately, in the same statement, whether or not the code asks for it —
the mapping, not the Java call, decided.

---

## Lesson 9 — `@OneToMany(mappedBy)`: the parent side (hands-on, subtask E second half)

### Definition

`@OneToMany(mappedBy = "event")` puts a **mirror list** on the parent: `Event` gets a
`List<PriceTier>`. `mappedBy` names the **field on the child that owns the link** —
`PriceTier.event`. The list adds **no column anywhere**; it is a Java-side view of the link
that already exists.

### Why it is needed

Holding an event and wanting its tiers (event page, pricing menu): with the mirror,
`event.getPriceTiers()`; without it, a hand query per use. Sugar for navigation — the
database is identical either way: the only physical link is `price_tier.event_id`.

### How it works

- The child's `@ManyToOne event` **is** the relationship (maps the real column).
- The parent's list says: "I am the reflection of that one," via `mappedBy = "event"` — a
  **Java field name**, not a table or column, checked at startup.
- One relationship, two views: `tier.getEvent()` and `event.getPriceTiers()`, riding one
  column.

### Example — the real change and run

Addition to `Event.java` (imports `jakarta.persistence.OneToMany`, `java.util.List`):

```java
// The parent's mirror list. It adds NO column anywhere.
// mappedBy = "event" names the FIELD on PriceTier that owns the link.
@OneToMany(mappedBy = "event")
private List<PriceTier> priceTiers;
```

plus getter. Runner loads one event and inspects the list **without touching contents**:

```java
@Bean
CommandLineRunner loadEventSeeList(EventRepository events) {
    return args -> {
        UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        Event e = events.findById(id).orElseThrow();
        System.out.println(">>> loaded event: " + e.getName());
        System.out.println(">>> priceTiers field holds a: " + e.getPriceTiers().getClass().getName());
    };
}
```

Real output:

```text
Hibernate: 
    select
        e1_0.id,
        e1_0.name,
        e1_0.starts_at 
    from
        event e1_0 
    where
        e1_0.id=?
>>> loaded event: Monsoon Rock Night
>>> priceTiers field holds a: org.hibernate.collection.spi.PersistentBag
```

### Walkthrough

1. The select touches **only `event`** — no join, no `price_tier`. Loading the parent did not
   load the list.
2. Contrast with Lesson 8: loading a tier eagerly joined its event. **Different defaults:**
   to-one fetches immediately (eager); to-many waits. Why, and at what cost — Lessons 10–13.
3. The field holds `org.hibernate.collection.spi.PersistentBag` — a **list-shaped stand-in**
   that knows how to fetch but has not. Printing its class is safe; asking for contents is
   the next lessons' story.
4. Startup now reports `Found 2 JPA repository interfaces.`; the dictionary holds
   `[Event, PriceTier]`.

### Important terms

| Term | Meaning |
| --- | --- |
| **Mirror list** | Parent-side `@OneToMany(mappedBy=...)` view of the child's link; no column. |
| **`mappedBy`** | Names the child's Java field that owns the link; checked at startup. |
| **`PersistentBag`** | Hibernate's stand-in list: knows how to fetch, has not yet. |
| **Lazy (preview)** | To-many default: wait until touched. Formal treatment next lessons. |

### Common mistakes — with the real error

**Wrong `mappedBy` value** (`"evnt"`, produced for real):

```text
org.hibernate.AnnotationException: Collection 'zenith.Event.priceTiers' is 'mappedBy' a property named 'evnt' which does not exist in the target entity 'zenith.PriceTier'
```

**Forgetting `mappedBy` entirely:** compiles and starts, but Hibernate assumes a *separate*
relationship stored in a third table (`event_price_tiers`); the first fetch fails looking for
a table that does not exist. A mirror of a child's `@ManyToOne` always carries `mappedBy`.

**Touching the list right away:** the stand-in has rules about when it may fetch;
`.size()` in the wrong place meets `LazyInitializationException` — triggered deliberately in
the coming lessons.

### Where it is used

Parent pages and object graphs. Professional habit: add the mirror only when navigation is
actually needed; the child side is essential, the parent side is opt-in.

### Link to the task

Subtask E second half, task-sheet code, then "load an event and read the log again": one
statement, `event` only, list unfetched.

### Do it yourself (second laptop)

Prerequisite: Lesson 8 state, `pg` running.

1. In `Event.java`: imports `jakarta.persistence.OneToMany`, `java.util.List`; add the
   `@OneToMany(mappedBy = "event") private List<PriceTier> priceTiers;` field and its getter.
2. Add the `loadEventSeeList` runner (code above).
3. `mvn spring-boot:run` → expect an event-only select, `>>> loaded event: Monsoon Rock
   Night`, and `>>> priceTiers field holds a: org.hibernate.collection.spi.PersistentBag`.
   Do **not** call `.size()` yet.
4. Break: `mappedBy = "evnt"` → startup `AnnotationException` naming the missing property →
   revert, run cleanly.

**Done when:** the event-only select and the `PersistentBag` line print, and the
wrong-`mappedBy` error has been seen once.

### Summary for revision

- Child `@ManyToOne` = the relationship; parent `@OneToMany(mappedBy)` = its mirror; one
  column total, in the child's table.
- `mappedBy` names the child's field; wrong name = startup error.
- Loading a parent does not fetch the list; the field holds a `PersistentBag` stand-in.
- To-one default: eager. To-many default: wait. The difference is the next lessons' subject.

### Check questions, with answers

**Question 1.** Two entities, complete:

```java
@Entity
public class Event {
    @Id private UUID id;
    @OneToMany(mappedBy = "event")
    private List<PriceTier> priceTiers;
    protected Event() {}
}

@Entity
public class PriceTier {
    @Id private UUID id;
    @ManyToOne private Event event;
    protected PriceTier() {}
}
```

**(a)** How many columns store this relationship, in which table — and does the list add one?
**(b)** What exactly does `"event"` in `mappedBy = "event"` name?

**Answer (a).** Exactly one column: `price_tier.event_id`, in the child's table. The list adds
none — it is a Java-side mirror. **(b)** The Java **field** named `event` on the child entity
`PriceTier` — not a table, not a column. Hibernate checks at startup that the field exists.

**Question 2.** With the two entities above, this runs:
`Event e = events.findById(someId).orElseThrow();`. Predict: how many statements, which
tables, and what sits in `priceTiers` afterwards?

**Answer.** One statement, `event` table only — the to-many side is not fetched. The field
holds Hibernate's stand-in list (`PersistentBag`), which knows how to fetch the tiers but has
not done so. An `ArrayList` of tiers would mean the rows were already fetched — they were not.

---

## Lesson 10 — The persistence context: Hibernate's notebook

### Definition

The **persistence context** is Hibernate's in-memory notebook for one transaction: every
entity loaded is recorded with a snapshot of its values; the same row is always handed out as
the same object; at commit, objects are compared with snapshots and each difference becomes an
`UPDATE` — with no `save()` call.

### Why it is needed

- **It computes writes:** change Java objects; the notebook works out the SQL.
- **One truth per row:** within a transaction the same row is the same object.
- **It is the open door lazy loading needs:** the stand-in list may only fetch while a
  notebook is open (bridge to Lessons 11–12).

### How it works

A **transaction** is an all-or-nothing group of statements (begin → commit, or roll back).
`@Transactional` on a public method of a Spring bean makes Spring wrap the call: begin
before, commit after, roll back on exception.

Notebook life: transaction begins → notebook opens → `findById` checks the notebook first
(hit = same object, **no SQL**; miss = SELECT + record with snapshot) → setters change only
objects → commit runs the **dirty check** (compare vs snapshots, UPDATE per changed entity) →
notebook discarded.

**Without a transaction**, each repository call opens and closes a *mini-notebook* around
itself; returned objects are **detached** — changes to them are watched by nobody and written
nowhere, silently.

### Example — the real service and runner

`DemoService.java` (each method = one transaction):

```java
@Service
public class DemoService {

    private final EventRepository events;

    public DemoService(EventRepository events) {
        this.events = events;
    }

    // Load the same row twice inside ONE transaction - count the selects.
    @Transactional
    public void loadTwiceInOneTransaction(UUID id) {
        Event a = events.findById(id).orElseThrow();
        Event b = events.findById(id).orElseThrow();
        System.out.println(">>> inside one transaction - same object? " + (a == b));
    }

    // Change a field and call NO save(). Watch when the UPDATE appears.
    @Transactional
    public void rename(UUID id, String newName) {
        Event e = events.findById(id).orElseThrow();
        e.setName(newName);
        System.out.println(">>> rename: setName done, no save() called, method still running");
    }   // <- commit HERE: notebook checked, UPDATE issued only if something changed
}
```

Runner (against Classical Evening, `aaaaaaaa-...-0002`): **A** two `findById` + a rename, all
outside any transaction; **B** two loads in one transaction; **C** three renames: change,
restore, and "change" to the same value. (`Event` got a plain `setName` setter.)

### Walkthrough — real output

**A, outside any transaction:** two identical SELECTs (two mini-notebooks), `same object?
false`, and the ghost rename (`setName` on a detached object) produced **no UPDATE and no
error** — psql confirms the row still reads `Classical Evening`. The quietest data-loss bug
in JPA.

**B, one transaction:** **one** SELECT for two `findById` calls — the second was a notebook
hit; `same object? true`.

**C, rename with no save():**

```text
Hibernate:  select ... where e1_0.id=?
>>> rename: setName done, no save() called, method still running
Hibernate:  update event set name=?, starts_at=? where id=?
```

The UPDATE printed **after** the "still running" line: writes happen at commit. Restore
rename: same shape. Same-value rename: select, println, **no UPDATE** — dirty check found
nothing. Eye-detail: `set name=?, starts_at=?` — Hibernate rewrites all mapped columns by
default, not only the changed one.

### Important terms

| Term | Meaning |
| --- | --- |
| **Persistence context** | Hibernate's per-transaction notebook: loaded entities + snapshots. |
| **Transaction** | All-or-nothing group of statements; `@Transactional` wraps a method in one. |
| **Dirty check** | At commit: compare objects to snapshots; UPDATE each difference. |
| **Flush at commit** | Writes are issued at transaction end, not at setter time. |
| **Detached** | An entity outside any notebook; changes to it are silently lost. |
| **First-level cache** | Notebook hit: same row asked again in one transaction = no SQL. |

### Common mistakes

- **Reflexive `save()` on loaded entities** — redundant inside a transaction; dirty check
  writes changes.
- **Expecting the UPDATE at setter time** — it comes at commit (proof: the log order in C).
- **Editing entities outside a transaction** — no notebook, no UPDATE, no error; data loss in
  silence (proof: the ghost rename in A).

### Where it is used

Every `@Transactional` service method. Explains three dailies: "UPDATE without save?" (dirty
check), "change didn't persist?" (detached), "second findById free?" (notebook hit).

### Link to the task

The task's word table: *"Hibernate's in-memory record of the entities it loaded in this
transaction, so it knows what changed."* Every clause now observed in a log. Bridge: the
stand-in list fetches only while a notebook is open — the mechanism of Lessons 11–12.

### Do it yourself (second laptop)

Prerequisite: Lesson 9 state, `pg` running.

1. Add to `Event.java`: `public void setName(String name) { this.name = name; }`
2. Create `DemoService.java` (code above, with package `zenith` and imports
   `org.springframework.stereotype.Service`,
   `org.springframework.transaction.annotation.Transactional`, `java.util.UUID`).
3. Add the `persistenceContextDemo` runner:

```java
@Bean
CommandLineRunner persistenceContextDemo(DemoService demo, EventRepository events) {
    return args -> {
        UUID id = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002"); // Classical Evening

        System.out.println(">>> --- A: load twice WITHOUT a transaction ---");
        Event x = events.findById(id).orElseThrow();
        Event y = events.findById(id).orElseThrow();
        System.out.println(">>> outside any transaction - same object? " + (x == y));
        x.setName("Ghost Rename");
        System.out.println(">>> set name to 'Ghost Rename' outside a transaction - watch for an UPDATE (there will be none)");

        System.out.println(">>> --- B: load twice inside ONE transaction ---");
        demo.loadTwiceInOneTransaction(id);

        System.out.println(">>> --- C: rename with no save(), inside a transaction ---");
        demo.rename(id, "Classical Evening (renamed)");
        demo.rename(id, "Classical Evening");
        demo.rename(id, "Classical Evening");   // same value - no UPDATE this time
    };
}
```

4. `mvn spring-boot:run` → expected: A two selects + `false` + no UPDATE; B one select +
   `true`; C select → "still running" → update; again; then select with no update.
5. Confirm no ghost damage:
   `docker exec pg psql -U postgres -d zenith -c "SELECT name FROM event WHERE id='aaaaaaaa-0000-0000-0000-000000000002';"`
   → `Classical Evening`.

**Done when:** you can point at the notebook hit, the commit-time update, the same-value
no-update, and the ghost rename that wrote nothing.

### Summary for revision

- Notebook = per-transaction record of loaded entities + snapshots.
- Inside a transaction: same row = same object; changes persist via dirty check at commit.
- Outside: mini-notebook per call; returned objects are detached; changes vanish silently.
- The UPDATE appears at commit (after your last line), and rewrites all mapped columns.

### Check questions, with answers

**Question 1.** This complete method runs in a Spring app (`tiers` is a
`JpaRepository<PriceTier, UUID>`; `PriceTier` has a working `setName`):

```java
@Transactional
public void discount(UUID id) {
    PriceTier t = tiers.findById(id).orElseThrow();
    t.setName("Early Bird");
    System.out.println("done changing");
}
```

No `save()` anywhere. List the SQL statements and when each runs relative to the `println`.

**Answer.** Two statements. A SELECT on `price_tier` at the `findById` line (before the
println). An UPDATE at the method's end — *after* the println — when the transaction commits
and the dirty check finds the name differs from the snapshot. `save()` is not needed; the
notebook computes the write.

**Question 2.** The same two lines run in a plain `CommandLineRunner`, no `@Transactional`:

```java
PriceTier t = tiers.findById(id).orElseThrow();
t.setName("Early Bird");
```

What SQL runs, what is the database state afterwards, and why no error?

**Answer.** Only the SELECT. `findById` ran in its own mini-notebook, which closed before
returning, so `t` is detached. `setName` changed an ordinary Java object; no notebook watches
it, no UPDATE is generated, the database keeps the old name. No error, because nothing illegal
happened — a plain object changed in memory. The silence is exactly the danger.

---

## Lesson 11 — Lazy loading: fetch only when touched

### Definition

**Lazy loading**: a related collection is not fetched when its owner loads — it is fetched at
the moment code first touches its contents. The stand-in list (`PersistentBag`) is the
mechanism: it sits in the field, knows how to fetch, and waits.

### Why it is needed

Parents are usually loaded without needing their children (an event list needs names, not
tiers). Always-fetching would drag unneeded data everywhere and snowball through nested
relations. So the to-many default is *wait until touched* — sensible, with one sharp edge
(the crash) and one famous trap (the N+1).

### How it works

The rule, built on Lesson 10:

> The stand-in may fetch **only while the notebook (persistence context) is open.**

- Inside a transaction: touch → one SQL fetch now (`where event_id=?`) → stand-in fills.
- Outside (detached owner): touch → no notebook to fetch through →
  `LazyInitializationException`.

"Touch" = asking for contents (`.size()`, `.get(0)`, iteration). Asking the class name is not
a touch. Vocabulary: **Session** is Hibernate's name for the notebook's carrier; "no Session"
= notebook closed.

### Example — the real method and the deliberate crash

Service method (open notebook):

```java
// Touch the stand-in list while the notebook is open.
@Transactional
public void listTiersOf(UUID eventId) {
    Event e = events.findById(eventId).orElseThrow();
    System.out.println(">>> event loaded; now touching the list...");
    int n = e.getPriceTiers().size();   // <- the touch: the fetch happens HERE
    System.out.println(">>> " + e.getName() + " has " + n + " price tiers");
}
```

Runner part E loads an event with a bare `findById` (mini-notebook closes on return) and
touches the list in a try/catch.

### Walkthrough — real output

**D, inside a transaction:**

```text
Hibernate:  select e1_0.id, e1_0.name, e1_0.starts_at from event e1_0 where e1_0.id=?
>>> event loaded; now touching the list...
Hibernate: 
    select
        pt1_0.event_id,
        pt1_0.id,
        pt1_0.name 
    from
        price_tier pt1_0 
    where
        pt1_0.event_id=?
>>> Monsoon Rock Night has 3 price tiers
```

The tier query ran **between the printlns** — at the `.size()` line, not at load time. Shape:
children by foreign key. Count 3: correct.

**E, outside:**

```text
>>> CRASH: org.hibernate.LazyInitializationException
>>> message: failed to lazily initialize a collection of role: zenith.Event.priceTiers: could not initialize proxy - no Session
```

Word by word: *a collection tried to fill itself* — *the field was `zenith.Event.priceTiers`*
(best debugging clue) — *no Session* = the notebook closed, namely when `findById` returned.

### Important terms

| Term | Meaning |
| --- | --- |
| **Lazy loading** | Fetch a relation at first touch, not at owner load. |
| **Touch** | Asking a collection for contents: size, get, iterate. |
| **Session** | Hibernate's carrier of the notebook; "no Session" = notebook closed. |
| **`LazyInitializationException`** | A stand-in tried to fetch with the notebook closed. |

### Common mistakes

- **Touching lazy collections outside the transaction** — demo E; fix by moving the touch
  inside the transactional method.
- **"Fixing" the crash with eager everywhere** — pays the fetch cost always; Lesson 13's
  subject.
- **Assuming the stand-in remembers across transactions** — each stand-in is bound to its own
  notebook; open or closed is all that matters.

### Where it is used

Every `@OneToMany` defaults lazy, so this is the water Java backends swim in. The working
pattern: **the service method is the transaction; all touching happens inside it**; return
simple values. E is exactly the classic web-app crash (touching collections after the service
returned).

### Link to the task

Word table: lazy loading *"saves work, and causes N+1 when you touch it in a loop."* The save
was Lesson 9 (no fetch when unneeded); the single touch was today (one extra query, deferred).
Next lesson: the touch inside a loop over five events — one plus five.

### Do it yourself (second laptop)

Prerequisite: Lesson 10 state, `pg` running.

1. Add `listTiersOf` (code above) to `DemoService.java`, below `rename`.
2. Add the runner:

```java
@Bean
CommandLineRunner lazyLoadingDemo(DemoService demo, EventRepository events) {
    return args -> {
        UUID monsoon = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        System.out.println(">>> --- D: touch the list INSIDE a transaction ---");
        demo.listTiersOf(monsoon);

        System.out.println(">>> --- E: touch the list OUTSIDE a transaction ---");
        Event e = events.findById(monsoon).orElseThrow();
        try {
            e.getPriceTiers().size();   // the notebook is already closed
        } catch (Exception ex) {
            System.out.println(">>> CRASH: " + ex.getClass().getName());
            System.out.println(">>> message: " + ex.getMessage());
        }
    };
}
```

3. `mvn spring-boot:run` → D: event select → touch line → tier select (`where
   pt1_0.event_id=?`) → `has 3 price tiers`. E: event select → `CRASH:
   org.hibernate.LazyInitializationException` → the `no Session` message.

**Done when:** the tier select sits between the two printlns in D, and E's message reads back
as a plain sentence.

### Summary for revision

- Lazy = fetch at first touch; the stand-in fetches only while the notebook is open.
- Inside the transaction: touch = one deferred query by foreign key.
- Outside: touch = `LazyInitializationException ... no Session`; the field name is in the
  message.
- Fix direction: touch inside the transaction — not eager-everything.

### Check questions, with answers

**Question 1.** This complete method runs (`Event` has `@OneToMany(mappedBy = "event")
List<PriceTier> priceTiers`):

```java
@Transactional
public void inspect(UUID id) {
    Event e = events.findById(id).orElseThrow();
    System.out.println("loaded");
    System.out.println("tiers: " + e.getPriceTiers().size());
}
```

List the SQL statements in order and the line causing each. Would the SQL change if the two
printlns swapped their contents?

**Answer.** Two statements. (1) `select ... from event where id=?` — caused by the `findById`
line. (2) `select ... from price_tier where event_id=?` — caused by the `.size()` touch. The
SQL follows the *touch*, not the printing: swap the printlns and the tier-select simply moves
with wherever `.size()` is evaluated. Printing text never causes SQL; touching collections
does.

**Question 2.** A teammate's code crashes with exactly:

```text
org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role: shop.Order.items: could not initialize proxy - no Session
```

**(a)** Which field in which class tried to load? **(b)** What does "no Session" mean in
notebook terms, and when did the notebook close? **(c)** The teammate proposes eager loading
to make the error go away — name the safer first fix.

**Answer (a).** The collection field `items` in class `shop.Order`. **(b)** "No Session"
means the persistence context — the notebook — was already closed when the touch happened; it
closed when the repository call that loaded the `Order` returned, because no transaction
surrounded the code. **(c)** Move the touch inside a `@Transactional` service method and
collect the needed values there before returning. Eager loading silences the error by paying
the fetch cost on every load everywhere — a trade, not a fix.

---

## Lesson 12 — Trigger the N+1 on purpose (hands-on, subtask F)

### Definition

The **N+1 problem**: one query loads a list of N parents; a loop touching each parent's lazy
collection then fires one more query per parent. N=5 → 1+5 = 6 statements. N=5000 → 5001.

### Why it is needed (why trigger a bug on purpose)

It is the most common performance bug in Java backends, and the code that causes it **looks
completely correct**. It cannot be spotted by reading Java — only by reading the log. Produce
it once in a controlled pen and its shape is yours forever.

### How it works — Lessons 9–11 chained

1. One query loads five events; five stand-in lists enter the notebook, each knowing only its
   own parent.
2. Each loop iteration touches one stand-in → one fetch `where event_id=?`.
3. Stand-ins do not cooperate; nobody fetches all tiers for all events at once.
4. Five iterations, five fetches, plus the list query: six.

### Example — the trigger and the full real log

In `DemoService` (list query: derived method `findTop5ByOrderByNameAsc()` on
`EventRepository`):

```java
// The N+1, triggered on purpose.
// Five events, one touch each. No line below looks wrong. Count the statements in the log.
@Transactional
public void triggerNPlusOne() {
    List<Event> five = events.findTop5ByOrderByNameAsc();   // statement 1: the list
    System.out.println(">>> loaded " + five.size() + " events with ONE query; now the loop:");
    for (Event e : five) {
        int n = e.getPriceTiers().size();          // statements 2..6: one fetch PER event
        System.out.println(">>>   " + e.getName() + " -> " + n + " tiers");
    }
}
```

Real log, complete (statement count verified mechanically: **6**):

```text
>>> --- F: trigger the N+1 on purpose ---
Hibernate:  select e1_0.id, e1_0.name, e1_0.starts_at from event e1_0 
            order by e1_0.name fetch first ? rows only
>>> loaded 5 events with ONE query; now the loop:
Hibernate:  select pt1_0.event_id, pt1_0.id, pt1_0.name from price_tier pt1_0 where pt1_0.event_id=?
>>>   Blues Brunch -> 0 tiers
Hibernate:  select pt1_0.event_id, pt1_0.id, pt1_0.name from price_tier pt1_0 where pt1_0.event_id=?
>>>   Classical Evening -> 1 tiers
Hibernate:  select pt1_0.event_id, pt1_0.id, pt1_0.name from price_tier pt1_0 where pt1_0.event_id=?
>>>   Indie Fest -> 0 tiers
Hibernate:  select pt1_0.event_id, pt1_0.id, pt1_0.name from price_tier pt1_0 where pt1_0.event_id=?
>>>   Jazz at the Note -> 2 tiers
Hibernate:  select pt1_0.event_id, pt1_0.id, pt1_0.name from price_tier pt1_0 where pt1_0.event_id=?
>>>   Monsoon Rock Night -> 3 tiers
>>> --- F end ---
```

### Walkthrough

1. Statement 1: the list (`Top5` became `fetch first ? rows only`).
2. Statements 2–6: **identical queries, different bound values** — the N+1 fingerprint in any
   log.
3. Blues Brunch and Indie Fest: **0 tiers, full query each** — an empty answer is not a free
   question.
4. Scale: each statement is a network round trip (~1ms warm, from F5.10's measurements). 5000
   events → 5001 round trips ≈ five seconds of back-and-forth for one page. Invisible at
   dev-database size — which is why it ships.
5. The task's sentence, earned: *produced without writing a line that looks wrong.*

### Important terms

| Term | Meaning |
| --- | --- |
| **N+1** | 1 list query + N per-item queries from touching lazy relations in a loop. |
| **Fingerprint** | Identical statement shapes repeating with different bound values. |
| **Round trip** | One network exchange with the database; the unit the N+1 multiplies. |

### Common mistakes

- **Log off** → the bug does not exist until production falls over. Subtask C paying rent.
- **Trusting small data** — read the statement-count formula (1+N), not dev elapsed time.
- **Miscounting** — it is one extra per item, not one extra in total.
- **Rushing to fix** — Task 3.3 fixes it; today is recognition only. The three standard fixes
  get *named* in the final lesson's AI exercise.

### Where it bites

List endpoints whose serializer touches a collection per item; report loops; dashboards
showing a per-row count (`.size()` is a touch). Pattern: load N, loop, touch → 1+N.

### Link to the task

Subtask F, word for word — done, counted (6), imagined at five thousand. Bonus proof: empty
collections still cost full queries.

### Do it yourself (second laptop)

Prerequisite: Lesson 11 state, `pg` running.

1. `EventRepository.java`: add `List<Event> findTop5ByOrderByNameAsc();`
2. `DemoService.java`: add the `triggerNPlusOne()` method above.
3. `ZenithApp.java`: add a runner printing `--- F` markers around `demo.triggerNPlusOne();`.
4. `mvn spring-boot:run` → between the markers, count `Hibernate:` blocks **by hand**.
   Expected: 6. Expected counts: Blues Brunch 0, Classical Evening 1, Indie Fest 0, Jazz at
   the Note 2, Monsoon Rock Night 3.

**Done when:** you counted 6 yourself and can say why a zero-tier event still cost a query.

### Summary for revision

- Load N, loop, touch = 1+N statements; fingerprint = identical shapes, different values.
- Empty results still cost full queries.
- The Java looks right; only the log tells. Formula over feel: 1+N.

### Check questions, with answers

**Question 1.** Twelve orders, item count each (complete code, inside `@Transactional`;
`Order` has lazy `@OneToMany(mappedBy = "order") List<OrderItem> items`):

```java
List<Order> orders = orderRepo.findTop12ByOrderByCreatedAtDesc();
for (Order o : orders) {
    System.out.println(o.getId() + ": " + o.getItems().size() + " items");
}
```

How many statements? Three orders have zero items — does that lower the count? And for 5,000
orders?

**Answer.** 13 statements: 1 list query + 12 item queries. Zero-item orders change nothing —
each still fires its query and gets an empty result. 5,000 orders → 5,001 statements.

**Question 2.** The bug is produced "without writing a line that looks wrong": **(a)** which
two lines of Question 1's code *together* cause it, and why does neither look wrong alone?
**(b)** where is the bug actually visible?

**Answer (a).** The `findTop12...` line and the `o.getItems().size()` line inside the loop.
Loading a list is normal; reading a size is normal. The harm is the combination: N lazy
stand-ins, each touched once, each fetching alone. **(b)** Only in the SQL log (or the
database's own statement statistics): 13 statements with one repeated fingerprint. The Java
source shows nothing.

---

## Lesson 13 — Why "make everything eager" is not the fix (hands-on, subtask G)

### Definition

**Eager loading**: a relation is fetched immediately, every time its owner loads, everywhere.
One word in the mapping turns it on:
`@OneToMany(mappedBy = "event", fetch = FetchType.EAGER)`.

### Why it is needed (why examine it)

Eager is the reflex "fix" for both the Lesson 11 crash and the Lesson 12 N+1. The task
demands the antidote in writing: one sentence on what eager does to a names-only list
endpoint. The sentence was earned by experiment.

### How it works — the experiment (flip one word, measure, revert)

**Observation 1 — single-event load became a join.** `findById` with nobody needing tiers:

```text
Hibernate: 
    select
        e1_0.id, e1_0.name, e1_0.starts_at,
        pt1_0.event_id, pt1_0.id, pt1_0.name 
    from
        event e1_0 
    left join
        price_tier pt1_0 on e1_0.id=pt1_0.event_id 
    where
        e1_0.id=?
```

One statement, tiers always aboard.

**Observation 2 — the names-only search started paying.** The Lesson 7 name search (code
never mentions tiers) went from 1 statement to **1 + 3**: three `price_tier` selects fired,
one per found event, before the results printed. The mapping asked; the code never did.

**Observation 3 — the N+1 became unconditional.** The Lesson 12 trigger still logged **6
statements**, but all six fired *before* the loop printed anything. Delete the loop and six
remain. Eager did not remove the N+1; it made it permanent and invisible as a "touch".

### The sentence (subtask G, also in NOTES.md)

> On a list endpoint that only needs names, eager loading fetches every event's full
> price-tier collection on every request — one extra query per row that the response never
> uses — so the endpoint pays the N+1 unconditionally, with no suspicious code line left to
> point at and no way to opt out for just one query.

### Walkthrough — why it behaves this way

1. **Eager is a property of the mapping, global to the program** — every loader of `Event`
   inherits the cost, forever.
2. **List queries still fetch eager collections per row** — the N+1 shape, baked in. Eager
   does not mean "one big smart query".
3. **The deciding asymmetry:** lazy can be upgraded to a fetch *for one query* (the real
   fixes, Task 3.3); eager cannot be downgraded for one query. Lazy keeps options; eager
   burns them.
4. Quieter costs ride along: wider results, network volume, memory, fatter responses — for
   unshown data.

### Important terms

| Term | Meaning |
| --- | --- |
| **Eager loading** | Fetch the relation on every owner load, program-wide. |
| **`FetchType.EAGER` / `LAZY`** | The mapping attribute choosing immediate vs on-touch. |
| **Unconditional N+1** | Per-row collection fetches on list queries, touch or no touch. |

### Common mistakes

- **Eager to silence `LazyInitializationException`** — fix the touch's location instead.
- **Eager to "fix" N+1** — statements don't drop; they become constant and codeless.
- **Believing eager = one join always** — true for single-row loads, false for lists; only
  the log tells.

### Where it is used

Legitimately: rare, tiny to-one relations needed by every path — and even then per-query
fetching is usually preferred. Eager collections: almost never. The to-many default is lazy
for a reason.

### Link to the task

Subtask G complete: understood by experiment, sentence written (above and in `NOTES.md`).

### Do it yourself (second laptop)

Prerequisite: Lesson 12 state, `pg` running.

1. In `Event.java` change the mirror list to
   `@OneToMany(mappedBy = "event", fetch = jakarta.persistence.FetchType.EAGER)`.
2. `mvn spring-boot:run`; find: the `findById` block containing `left join price_tier`; three
   `price_tier` selects before `>>> name search 'night' found 3:`; six `Hibernate:` blocks in
   the `--- F` section, all **above** `>>> loaded 5 events`.
3. **Revert** to `@OneToMany(mappedBy = "event")`; run; confirm the name search is one
   statement again and `PersistentBag` is back. Do not leave eager in.
4. Write your own one sentence in your `NOTES.md` under "Why eager is not the fix".

**Done when:** the mapping is lazy again and the sentence is written.

### Summary for revision

- Eager = mapping-wide, forever, no per-query opt-out.
- Single load: a wider join. List load: per-row selects — the N+1, unconditional.
- Lazy keeps the choice; the real fixes are per-query and come later (Task 3.3).

### Check questions, with answers

**Question 1.** `Restaurant` has `@OneToMany(mappedBy = "restaurant", fetch =
FetchType.EAGER) List<MenuItem> menu;`, restaurants average 60 menu items. The home screen
loads 20 restaurants to show **names and ratings only**. What SQL runs per request, and what
are the two kinds of waste?

**Answer.** 21 statements: 1 list query + 20 per-restaurant menu selects (eager collections on
list queries fetch per row). Waste one: 20 extra round trips on every request — an
unconditional N+1. Waste two: ~1,200 menu rows fetched, held in memory, and thrown away,
because the screen shows names and ratings only.

**Question 2.** A teammate: *"After we made `priceTiers` eager, the N+1 disappeared — no
`.size()` touch exists in the code anymore, so we're safe."* Name the two errors and the one
place that shows the truth.

**Answer.** Error one: the N+1 did not disappear — eager collections on list queries still
fetch one select per row; the statements remain, now firing always. Error two: "no touch in
code = safe" is backwards — eager moved the fetch out of the code and into the mapping, so
reading code can prove nothing. The truth lives in the SQL log: count the statements.

---

## Lesson 14 — Migrations: the idea

### Definition

A **migration** is a versioned SQL file describing **one change** to the database's shape.
Migrations live in the project next to the code, are applied in version order, each exactly
once per database — and once applied, a file is **never edited again**. New changes get new
files.

### Why it is needed

The zenith schema was built by hand: F5.2 `CREATE TABLE` in psql, F5.3 constraints by hand,
F5.4 a column by hand. Works with one database and one person. A real project has many
databases — every laptop, test, staging, production — and all must have the **same shape**.
Hand-typed DDL cannot deliver that: nobody remembers what ran where; a new teammate cannot
rebuild the schema; production gets hand-typed "quick fixes" and quietly disagrees with the
code — **schema drift**. Migrations fix it in one move: **the schema becomes files in git** —
the single truth any machine can replay, reviewed like code, because it is code.

### How it works — folder, ledger, tool

1. **Folder** of numbered files, `V<number>__<description>.sql` (double underscore):
   `db/migration/V1__baseline.sql`, `V2__add_doors_open.sql`, …
2. **Ledger**: a small table the tool creates inside the database, recording which versions
   ran there, when, and a **checksum** (fingerprint of the file's exact text). Flyway names it
   `flyway_schema_history`.
3. **Tool**, at app startup, before anything else: read the ledger; compare with the folder;
   run only the new files, in order; record each. For already-applied files, recompute the
   checksum — **an edited applied file means checksum mismatch, and the tool refuses to start
   the app.**

The laws: **append-only**; **never edit an applied migration**; **fix mistakes forward** (a
new file that repairs the old one's result).

### Example

```sql
-- V1__create_coupon.sql
CREATE TABLE coupon (
    id   uuid PRIMARY KEY,
    code text NOT NULL
);
```

```sql
-- V2__add_coupon_expiry.sql
ALTER TABLE coupon ADD COLUMN expires_at timestamptz;
```

Ledger essence after both run:

| version | description | checksum | applied on |
| --- | --- | --- | --- |
| 1 | create coupon | `-1142` | Oct 20 |
| 2 | add coupon expiry | `8821` | Oct 26 |

### Walkthrough — two machines, one truth

1. Laptop A, two weeks ago: only V1 existed; tool ran V1; ledger says 1.
2. Laptop B, new, empty database: tool sees empty ledger, runs V1 then V2.
3. Laptop A, after pulling latest code: ledger says 1; tool runs only V2.
4. Anyone edits applied V1: next startup, checksum mismatch, startup refused, error names the
   file. Restore the file; never force the start.

### Important terms

| Term | Meaning |
| --- | --- |
| **Migration** | Versioned SQL file; one schema change; applied once; never edited after. |
| **Ledger / history table** | In-database record of applied versions + checksums. |
| **Checksum** | Fingerprint of a file's text; catches edits to applied migrations. |
| **Schema drift** | Database shape and code's expectation disagreeing. |
| **Fix forward** | Repair mistakes with a new migration, never by editing old ones. |

### Common mistakes

- **Editing an applied migration** — the classic; checksum catches it; the refusal is correct.
- **Letting the ORM alter the schema** (`ddl-auto`) — Lesson 16: migrations own the schema,
  Hibernate only checks.
- **Many unrelated changes in one file** — keep one change per file for clean review and
  failure.
- **Hand-fixing one environment** — if it is not a migration, it did not happen.

### Where it is used

Every serious backend team, every language. Schema changes arrive as migration files in pull
requests; deployments apply pending migrations as a routine startup step.

### Link to the task

Defines the task's new words **Migration** and **Flyway**. Subtask H (next lesson): add
Flyway, write `V1__baseline.sql` from the real F5.2/F5.3/F5.4 DDL, drop the hand-made tables,
watch Flyway recreate them, inspect `flyway_schema_history`.

### Do it yourself

Nothing to run — concept lesson; the hands-on is Lesson 15.

### Summary for revision

- Migration = versioned SQL file, one change, applied once, never edited after.
- Folder + ledger + startup tool = every database converges on the same shape, with history.
- Append-only; fix forward; checksums police history.

### Check questions, with answers

**Question 1.** Files: `V1__create_event.sql`, `V2__add_venue.sql`, `V3__add_cancelled_at.sql`.
Teammate A's ledger shows 1 and 2 applied. Teammate B starts with an empty database. What runs
on each machine, and what must both ledgers show afterwards?

**Answer.** On A: only V3 (1 and 2 are in the ledger, so they are skipped). On B: V1, V2, V3
in order (empty ledger, everything is new). Afterwards both ledgers show versions 1, 2, 3
applied successfully — two databases, identical shape, identical history.

**Question 2.** `V4__create_coupon.sql` was applied everywhere months ago. A developer spots a
typo'd column name `discout_pct`, edits V4 to fix the spelling, restarts. **(a)** What happens
and why is it correct? **(b)** What should have been done?

**Answer (a).** Startup fails: the recomputed checksum of the edited V4 no longer matches the
checksum stored in the ledger when V4 was applied. Correct, because the edit is a lie — every
database that ran V4 ran the *old* text and still has `discout_pct`; the edited file would
pretend otherwise and new databases would diverge from old ones. **(b)** Restore V4 exactly,
and fix forward: `V5__rename_discout_pct.sql` containing
`ALTER TABLE coupon RENAME COLUMN discout_pct TO discount_pct;` — every database, old or new,
converges on the fixed name.

---

## Lesson 15 — Flyway and `V1__baseline.sql` (hands-on, subtask H)

### Definition

**Flyway** is the migration tool: it finds the numbered SQL files, keeps the ledger table
`flyway_schema_history` inside the database, and at every startup applies exactly the files
this database has not seen yet. In Spring Boot: two dependencies and one folder, no code.

### Why it is needed

The zenith schema was hand-typed across F5.2–F5.4. This lesson turns it into **files that own
the schema**: one baseline file snapshots the current shape; every future change is a new
numbered file. No hand-typed DDL ever again.

### How it works

Two dependencies (versions managed by the Boot parent; Flyway 11.7.2 arrived):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Two, because modern Flyway ships each database's support separately — `flyway-core` alone
cannot speak Postgres. Migrations live in `src/main/resources/db/migration/`. Boot wires the
startup order: **Flyway runs before Hibernate**.

The baseline, written from the database's real shape (checked first with
`docker exec pg pg_dump -U postgres -d zenith --schema-only`):

```sql
-- src/main/resources/db/migration/V1__baseline.sql
create table venue (
    id              uuid primary key,
    name            text not null,
    city            text not null,
    capacity        integer not null,
    seat_map_hints  jsonb,
    created_at      timestamptz not null default now(),
    constraint venue_capacity_positive check (capacity > 0)
);

create table event (
    id            uuid primary key,
    venue_id      uuid not null,
    name          text not null,
    starts_at     timestamptz not null,
    created_at    timestamptz not null default now(),
    cancelled_at  timestamptz,
    constraint event_venue_fk foreign key (venue_id) references venue (id)
);

create index idx_event_venue_id  on event (venue_id);
create index idx_event_starts_at on event (starts_at);

create table price_tier (
    id          uuid primary key,
    event_id    uuid not null,
    name        text not null,
    amount      numeric(19,4) not null,
    created_at  timestamptz not null default now(),
    constraint price_tier_event_fk foreign key (event_id) references event (id),
    constraint price_tier_name_unique_per_event unique (event_id, name),
    constraint price_tier_amount_non_negative check (amount >= 0)
);

create index idx_price_tier_event_id on price_tier (event_id);

create table app_user (
    id            uuid primary key,
    email         text not null,
    display_name  text not null,
    created_at    timestamptz not null default now(),
    constraint app_user_email_unique unique (email)
);

create table role (
    id    uuid primary key,
    name  text not null,
    constraint role_name_unique unique (name)
);

create table user_role (
    user_id  uuid not null,
    role_id  uuid not null,
    constraint user_role_pk primary key (user_id, role_id),
    constraint user_role_user_fk foreign key (user_id) references app_user (id),
    constraint user_role_role_fk foreign key (role_id) references role (id)
);
```

Scope decision: the baseline covers everything the database must keep — F5.2 tables, F5.3
constraints, F5.4 `cancelled_at`, and the F5.9 user/role tables. Only the empty `scratch`
playground table was left out and allowed to die.

### The real story, in five acts

**Act 1 — `flyway-core` alone (deliberate mistake):**

```text
Caused by: org.flywaydb.core.api.FlywayException: Unsupported Database: PostgreSQL 17.11
```

Flyway present, no Postgres dialect. The same stack trace also proved the startup order:
`Failed to initialize dependency 'flywayInitializer' of ... bean 'entityManagerFactory'` —
Hibernate waits for Flyway.

**Act 2 — both dependencies, old tables still in place:**

```text
org.flywaydb.core.api.FlywayException: Found non-empty schema(s) "public" but no
schema history table. Use baseline() or set baselineOnMigrate to true to initialize
the schema history table.
```

Flyway refuses to adopt tables it did not create and cannot account for. Answer: back up the
data, drop the hand-made tables, let V1 rebuild.

**Act 3 — backup, drop, migrate.** Backup
(`pg_dump --data-only --column-inserts | grep '^INSERT' > seed-dev-data.sql`, 24 rows), then
`DROP TABLE price_tier, event, venue, user_role, app_user, role, scratch;` and run:

```text
JdbcTableSchemaHistory : Schema history table "public"."flyway_schema_history" does not exist yet
DbValidate             : Successfully validated 1 migration (execution time 00:00.008s)
JdbcTableSchemaHistory : Creating Schema History table "public"."flyway_schema_history" ...
DbMigrate              : Current version of schema "public": << Empty Schema >>
DbMigrate              : Migrating schema "public" to version "1 - baseline"
DbMigrate              : Successfully applied 1 migration to schema "public", now at version v1
```

Then the app **crashed**: `java.util.NoSuchElementException: No value present` — the runners
found no Monsoon Rock Night. **Migrations restore shape, not data.**

**Act 4 — reseed, run again.** `psql < seed-dev-data.sql` (24 × `INSERT 0 1`), then:

```text
DbValidate : Successfully validated 1 migration (execution time 00:00.014s)
DbMigrate  : Schema "public" is up to date. No migration necessary.
```

Exit 0, all 21 logged statements, every demo green. That line is Flyway's steady state.

**Act 5 — the ledger:**

```text
 installed_rank | version | description | type |      script      |  checksum  | installed_by | success
----------------+---------+-------------+------+------------------+------------+--------------+---------
              1 | 1       | baseline    | SQL  | V1__baseline.sql | -999242384 | postgres     | t
```

### Walkthrough — every startup from now on

1. Boot builds the pool, hands Flyway a connection **before** Hibernate initializes.
2. No ledger + non-empty schema → refuse (Act 2). No ledger + empty schema → create ledger,
   apply all (Act 3).
3. Ledger present → recompute checksums of applied files (edit = refusal), apply only new
   files, else "No migration necessary" (Act 4).
4. Only then does Hibernate start, meeting a settled schema.

### Important terms

| Term | Meaning |
| --- | --- |
| **`flyway_schema_history`** | Flyway's ledger table: version, script, checksum, success. |
| **Baseline** | The first migration: a snapshot of the schema as it already stands. |
| **`flyway-database-postgresql`** | The Postgres dialect module; required beside `flyway-core`. |
| **"No migration necessary"** | Steady state: ledger matches folder, nothing to do. |

### Common mistakes

- **Forgetting `flyway-database-postgresql`** → `Unsupported Database: PostgreSQL 17.11`.
- **Expecting data back after a drop** → Act 3's crash; schema and data are separate concerns;
  back up first (one `pg_dump` line).
- **Editing the applied baseline** → its checksum is in the ledger; next startup dies.
- **Single underscore in the name** (`V1_baseline.sql`) → silently ignored; the pattern is
  `V<number>__<description>.sql`, double underscore.

### Where it is used

This exact setup is the default schema machinery of production Java backends: migrations run
against throwaway databases in CI, and deployments apply pending versions at startup.

### Link to the task

Subtask H complete: dependency added, `V1__baseline.sql` written with the F5.2/F5.3/F5.4 DDL,
hand-made tables dropped, Flyway watched recreating them, `flyway_schema_history` inspected.

### Do it yourself (second laptop)

Prerequisite: Lesson 13 state; `pg` running (`docker ps`; if `Exited`: `docker start pg`).

1. **Back up your data:**
   `docker exec pg pg_dump -U postgres -d zenith --data-only --column-inserts | grep '^INSERT' > seed-dev-data.sql`
   then `wc -l seed-dev-data.sql` (~24 lines) and read it.
2. Add **both** dependencies to `pom.xml` (block above). Optional: add only `flyway-core`
   first, run, read `Unsupported Database: PostgreSQL 17.x`, then add the second.
3. Create `src/main/resources/db/migration/V1__baseline.sql` — double underscore — with the
   exact contents above.
4. Run before dropping: `mvn spring-boot:run` → expect the Act 2 refusal
   (`Found non-empty schema(s) "public" but no schema history table`). Safety, not failure.
5. Drop and migrate:
   `docker exec pg psql -U postgres -d zenith -c "DROP TABLE price_tier, event, venue, user_role, app_user, role, scratch;"`
   then `mvn spring-boot:run` → expect `Creating Schema History table`,
   `Migrating schema "public" to version "1 - baseline"`, `Successfully applied 1 migration`,
   then the `NoSuchElementException` crash (shape back, rows gone).
6. Reseed and verify:
   `docker exec -i pg psql -U postgres -d zenith < seed-dev-data.sql` (24 × `INSERT 0 1`),
   `mvn spring-boot:run` → `Schema "public" is up to date. No migration necessary.`,
   `docker exec pg psql -U postgres -d zenith -c "SELECT version, script, checksum, success FROM flyway_schema_history;"`

**Done when:** the run exits clean with `No migration necessary`, all demo lines print, and
the ledger shows version 1, success `t`.

### Summary for revision

- Two dependencies + `db/migration/` + numbered files = Flyway in Boot; Flyway runs before
  Hibernate.
- Baseline = snapshot of the real current schema; after it is applied, never edit it.
- Flyway refuses non-empty schemas without a ledger; migrations restore shape, never data.
- Steady state: "Schema is up to date. No migration necessary."

### Check questions, with answers

**Question 1.** First Flyway run: `Successfully applied 1 migration`, then immediately
`java.util.NoSuchElementException: No value present` from a runner loading one event by id.
Why are both true at once? What did Flyway restore and not restore? Which backup step makes
this a non-event?

**Answer.** The migration succeeded as a *schema* operation: V1 contains only DDL, so the
tables, constraints, and indexes are back. V1 contains no rows, so the tables are empty;
`findById` returned an empty `Optional`, and `orElseThrow()` threw. Flyway restored shape, not
data — those are separate concerns. The backup step: before dropping, dump the rows with
`pg_dump --data-only --column-inserts > seed-dev-data.sql`, and replay the file with psql
after the migration.

**Question 2.** A developer deletes one ugly blank line from the applied `V1__baseline.sql`
and deploys; every ledger stores checksum `-999242384` from the original text. What happens on
every environment's next startup, and what rule is enforced? Separately: another developer
creates `V2_add_doors_open.sql` (single underscore) and startup says "up to date" — what
happened?

**Answer.** Flyway recomputes V1's checksum from the edited text, gets a different number than
`-999242384`, and refuses to start — on every environment, with a checksum-mismatch error
naming version 1. The rule: an applied migration is history; never edit it, fix forward with a
new file (restore the original text to recover). The single-underscore file does not match the
`V<number>__<description>.sql` naming pattern, so Flyway does not recognise it as a migration
at all — it is silently ignored and "up to date" is honest from Flyway's point of view. Rename
it to `V2__add_doors_open.sql`.

---

## Lesson 16 — `ddl-auto: validate`: migrations own the schema, Hibernate only checks (hands-on, subtask I)

### Definition

`spring.jpa.hibernate.ddl-auto` decides **what Hibernate may do to the schema**:

| Value | What Hibernate does to the schema |
| --- | --- |
| `none` | Nothing. Doesn't look, doesn't touch. |
| `validate` | **Looks, never touches.** Mismatch between entities and schema kills startup. |
| `update` | Alters the schema itself to match entities. Adds; never removes. |
| `create` | Drops everything mapped and recreates at startup. |
| `create-drop` | Like `create`, and drops again at shutdown. |

This lesson sets `validate`: Hibernate becomes a read-only inspector.

### Why it is needed

Two parties now have opinions about the schema: **Flyway owns it** (the files say the shape);
**Hibernate expects it** (every `@Entity` claims its table and columns exist). Unset,
Boot's default for a real database is `none` — blind: drift surfaces only when a query dies at
runtime. `validate` moves discovery to the cheapest moment: **startup refuses**. The rule:
**migrations own the schema; Hibernate only checks.**

### How it works

One line, nested under `spring.jpa.hibernate` (sibling of `show-sql`, *not* inside
`properties`):

```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate    # Hibernate may look at the schema, never touch it
    properties:
      hibernate:
        format_sql: true
```

Every startup is now a fixed pipeline: **1. Flyway migrates → 2. Hibernate validates
(table exists? mapped columns exist? types compatible?) → 3. the app runs.**

### The real run

```text
DbValidate : Successfully validated 1 migration (execution time 00:00.016s)
DbMigrate  : Schema "public" is up to date. No migration necessary.
```

Then all 36 demo lines, exit 0. Hibernate's validation printed **nothing — silence is the
pass** (the `Successfully validated` line is Flyway checking its own checksums, a different
validation). Subtlety: validate checks only what entities **map**. `event` also has
`venue_id`, `created_at`, `cancelled_at` — unmapped, invisible, fine. Extra columns never
fail validation; missing mapped columns do.

### Why the other values are wrong here

- **`none`** — no writer, but no inspector. Drift hides until runtime.
- **`update`** — three poisons. (1) **Unversioned changes**: no file, no review, no ledger;
  Flyway's history becomes a lie. (2) **Only adds — and loses data anyway**: rename a field
  `ticketPrice` → `price` and update does not rename the column; it adds an empty `price`
  and strands `ticket_price` with all the real data. No error; the app reads nulls.
  (3) **Divergence**: each environment's schema becomes "whatever code ever started here".
- **`create` / `create-drop`** — wipe data by design; throwaway test databases only.
- **`validate`** — read-only, loud, early. The complement to Flyway.

### Important terms

| Term | Meaning |
| --- | --- |
| **`ddl-auto`** | Hibernate's schema power level: none/validate/update/create/create-drop. |
| **`validate`** | Compare entities to schema at startup; refuse on mismatch; never touch. |
| **Inspector, not writer** | Migrations own the schema; Hibernate only checks. |

### Common mistakes

- **`update` in production** — see the three poisons above.
- **Believing validate checks everything** — it checks mapped tables/columns/types only;
  constraints, indexes, unmapped columns sail through. A floor, not a guarantee.
- **Wrong YAML nesting** — `spring.jpa.hibernate.ddl-auto`; mis-indented it is silently
  ignored. If a deliberate break does not fail startup, check indentation first.

### Where it is used

Migration tool owns schema + `ddl-auto: validate` is the standard production pairing for
Spring Boot. `create-drop` appears legitimately in integration tests; `update` in old
tutorials and toys — recognise it there and leave it there.

### Link to the task

Subtask I done: `ddl-auto: validate` set; rule internalized: **migrations own the schema;
Hibernate only checks.** Stays on for all future stages, like the SQL log.

### Do it yourself (second laptop)

Prerequisite: Lesson 15 state (Flyway green, data reseeded); `pg` running.

1. In `application.yml` add the `hibernate: ddl-auto: validate` block exactly as above.
2. `mvn spring-boot:run`.

**Expected:** identical green output — `No migration necessary`, all demo lines, clean exit.
Nothing visibly changes: schema and entities agree, so the inspector passes silently. You
armed a tripwire; the next lesson trips it.

**Done when:** the app starts and finishes green with the setting in place.

### Summary for revision

- `ddl-auto` = Hibernate's schema power. Production answer: `validate`.
- Pipeline per startup: Flyway migrates → Hibernate validates → app runs.
- `update` = unversioned changes, strands data on renames, divergent environments.
- Validation is silent when happy, fatal when not; it checks only mapped things.

### Check questions, with answers

**Question 1.** Production runs `ddl-auto: update`. Table `coupon` has `ticket_price` full of
real values. A developer renames the Java field `ticketPrice` to `price` and deploys. What
does Hibernate do, what does the table look like, what does the app read, why no error — and
would `validate` have allowed the deploy?

**Answer.** At startup Hibernate sees a mapped property `price` with no matching column and
issues `ALTER TABLE coupon ADD COLUMN price ...` (nullable, type guessed). It never drops or
renames, so `ticket_price` stays — stranded with all the real data. The table now has both
columns; the app reads and writes only `price`, so every existing coupon shows a null price.
No error, because adding a nullable column is perfectly legal SQL and nothing anywhere checks
the *intent* to rename. `validate` would have refused the deploy at startup — missing column
`price` in table `coupon` — forcing a proper rename migration before the app could run.

**Question 2.** With Flyway + `validate`: a change ships `V2__add_doors_open.sql` (adds a
`doors_open` column) and the `Event` entity now maps `doorsOpen`. Why does the deploy come up
green — and what if Hibernate validated *before* Flyway ran?

**Answer.** Green path: (1) Flyway compares the ledger with the folder, finds V2 new, runs the
`ALTER`, records it — the column now exists; (2) Hibernate validates: mapped `doorsOpen` ↔
column `doors_open` present, types compatible — pass; (3) the app runs. Reversed order:
validation would inspect the *old* schema, find `doors_open` missing, and refuse startup —
and the migration that would have fixed it never gets to run. Every deploy that changes both
entity and schema would deadlock. This is why Boot wires Flyway as a dependency of Hibernate's
`entityManagerFactory` — visible in Lesson 15's Act 1 stack trace.

---

## Lesson 17 — Break it: schema drift, caught at startup (hands-on, subtask J)

### Definition

**Schema drift**: the code expects a database shape the database does not have. This lesson
manufactures drift on purpose, reads the exact refusal, and repairs it the only correct way.

### Why it is needed

Lesson 16 armed a tripwire (`ddl-auto: validate`) that passed in silence. A tripwire you have
never seen fire is a tripwire you don't trust. One innocent line of Java with no migration
makes the application undeployable — loudly, at startup, before any user is hurt.

### The break

One field added to `Event`, nothing else:

```java
private OffsetDateTime doorsOpenAt;   // new field - and NO migration
```

Hibernate now expects column `doors_open_at` in table `event` (camelCase → snake_case). Run:

```text
DbMigrate : Schema "public" is up to date. No migration necessary.

ERROR ... Failed to initialize JPA EntityManagerFactory: [PersistenceUnit: default]
Unable to build Hibernate SessionFactory; nested exception is
org.hibernate.tool.schema.spi.SchemaManagementException:
Schema-validation: missing column [doors_open_at] in table [event]
```

Exit code 1. Two tools, two truths: **Flyway is happy** (files vs ledger — nothing new; Flyway
cannot see Java). **Hibernate refuses** and names the exact gap. The error arrives wrapped
(`BeanCreationException` → `PersistenceException` → `SchemaManagementException`) — read the
**last `Caused by:`**.

### The dead end

"Add the column to `V1__baseline.sql`" fails twice: the ledger lists V1 as applied, so the
edited file never re-runs — and its changed checksum kills the next startup anyway. Applied
migrations are history.

### The fix — forward

`src/main/resources/db/migration/V2__add_doors_open_at.sql`:

```sql
alter table event add column doors_open_at timestamptz;
```

Same run command:

```text
DbValidate : Successfully validated 2 migrations (execution time 00:00.014s)
DbMigrate  : Current version of schema "public": 1
DbMigrate  : Migrating schema "public" to version "2 - add doors open at"
DbMigrate  : Successfully applied 1 migration to schema "public", now at version v2
```

Hibernate validated in silence, all 36 demo lines, exit 0. The ledger:

```text
 installed_rank | version |    description    |          script           | success
----------------+---------+-------------------+---------------------------+---------
              1 | 1       | baseline          | V1__baseline.sql          | t
              2 | 2       | add doors open at | V2__add_doors_open_at.sql | t
```

`\d event` now shows `doors_open_at | timestamp with time zone`.

### The rule this teaches

A schema-touching change is a **pair that ships together**: the entity edit and its migration,
same commit. The startup pipeline (Flyway applies → Hibernate validates → app runs) makes the
pair safe in order. The Java half alone fails validation; the SQL half alone is a harmless
extra unmapped column.

### Walkthrough — the drift timeline

1. Entity gains `doorsOpenAt`; folder still ends at V1 — drift exists silently in the code.
2. Startup: Flyway finds nothing new; "up to date" is its honest truth.
3. Hibernate's validator walks the entity: `id` ✓, `name` ✓, `starts_at` ✓,
   `doors_open_at` ✗ → `SchemaManagementException`, startup refused. The deploy fails;
   production never comes up broken.
4. V2 written: six words of SQL, versioned, reviewable.
5. Next startup: ledger 1, folder 1+2 → V2 applied in 7 ms → validation passes → green.
6. Every other database — second laptop, teammates, CI, production — repeats step 5
   automatically. One file fixes the fleet.

### Important terms

| Term | Meaning |
| --- | --- |
| **Schema drift** | Code's expected shape ≠ database's real shape. |
| **`Schema-validation: missing column`** | The validator's refusal, naming column and table. |
| **Fix forward** | Repair with a new migration; never edit applied history. |
| **The pair** | Entity change + its migration, shipped together. |

### Common mistakes

- **Editing the applied baseline** — skipped by the ledger *and* checksum mismatch.
- **`V2_add_doors_open_at.sql` (single underscore)** — silently ignored; same validation
  error returns. Double underscore.
- **Hand-fixing with psql** — this one database goes green; every other database stays
  broken, with no record of why. If it isn't a migration, it didn't happen.
- **Wrong type in the migration** — `date` vs `OffsetDateTime` fails on type compatibility;
  the pair must match in type, not just name.

### Where it is used

Real drift sources: a forgotten migration in a feature branch (this exact scenario), hand-typed
hotfix `ALTER`s, leftovers from an old `ddl-auto: update` era.
`Schema-validation: missing column/table` is one of the most-searched Boot startup errors —
anatomy, cause, and fix now known.

### Link to the task

Subtask J complete: field added with no migration → real startup validation error read and
decoded → repaired properly with V2. The field and column stay, as versioned history.

### Do it yourself (second laptop)

Prerequisite: Lesson 16 state (`validate` on, Flyway green); `pg` running.

1. In `Event.java`, next to `startsAt`, add: `private OffsetDateTime doorsOpenAt;`
2. `mvn spring-boot:run` → expect failure with
   `Schema-validation: missing column [doors_open_at] in table [event]`; find Flyway's
   "up to date" line above it and say why both are true.
3. Create `src/main/resources/db/migration/V2__add_doors_open_at.sql` (double underscore)
   containing: `alter table event add column doors_open_at timestamptz;`
4. `mvn spring-boot:run` → expect `Migrating schema "public" to version "2 - add doors open
   at"`, then green. Verify:
   `docker exec pg psql -U postgres -d zenith -c "SELECT version, script, success FROM flyway_schema_history;"`

**Done when:** the ledger shows versions 1 and 2 with `success = t` and the app runs green
with the new field.

### Summary for revision

- Drift = entity expects what the schema lacks; `validate` turns it into a startup refusal.
- Flyway and Hibernate each tell their own truth; read the last `Caused by:`.
- Never edit applied history; fix forward with the next numbered file.
- Ship entity + migration as one pair.

### Check questions, with answers

**Question 1.** A deploy ships `Booking` gaining `private String refundPolicy;` but the
migration was forgotten. Pipeline: Flyway then `validate`. What does each tool log, what is
the exit state? Why can't editing the applied `V1__baseline.sql` fix any already-migrated
database? Write the one-line correct fix file.

**Answer.** Flyway: `Schema "public" is up to date. No migration necessary.` — its files match
its ledger. Hibernate: `Schema-validation: missing column [refund_policy] in table [booking]`,
wrapped in the EntityManagerFactory failure; startup refused, non-zero exit, the app never
comes up. Editing V1 cannot help: every migrated database's ledger lists version 1 as applied,
so the file is skipped — and its new checksum mismatches the stored one, failing startup on
its own. The fix, as the next number, e.g. `V2__add_refund_policy.sql` (or V3 if V2 exists):
`alter table booking add column refund_policy text;`

**Question 2.** A teammate "fixes" the drift by typing
`ALTER TABLE event ADD COLUMN doors_open_at timestamptz;` straight into psql on staging.
Staging starts green. What is still wrong — next fresh environment, the ledger, and what
should have been done?

**Answer.** The change exists only inside staging's database: no file, no commit, no ledger
row — `flyway_schema_history` is unchanged and knows nothing. The next fresh environment (new
laptop, CI, a rebuilt staging) replays only the files, gets no `doors_open_at`, and dies with
the same validation error. Worse, when the proper `V2__add_doors_open_at.sql` is finally
written, it fails *on staging* with "column already exists" — the hand-fix now blocks the real
fix. Correct move: put the same `ALTER` into the next numbered migration file so every
database converges; staging's hand-added column must first be dropped so V2 can apply cleanly.

---

## Lesson 18 — The AI exercise, and the three standard N+1 fixes (subtask K)

### The exercise

The task's exact prompt to ask an AI:

> *"Explain the N+1 problem using a ticketing example of events and price tiers, show me the
> SQL that gets generated, and then list the three standard fixes with the trade-off of each.
> Do not write the code for me, describe what each fix changes."*

No code in this lesson — the point is to lock in the map of fixes, each with its cost, so the
later task that implements them is a choice, not a reflex.

### The answer

**The problem, in ticketing terms.** A page lists events; each event has price tiers. The code
loads 5 events with one query, then loops and touches each event's lazy tier list. Each touch
fires its own query: 1 + N statements. With 5 events, 6; with 200, 201. The page slows in
direct proportion to its own success, and the code looks innocent.

**The SQL generated** (from the real log, run F):

```text
select e1_0.id, ... from event e1_0 order by e1_0.name fetch first ? rows only   <- 1: the list
select pt1_0.event_id, ... from price_tier pt1_0 where pt1_0.event_id=?         <- 2
select pt1_0.event_id, ... from price_tier pt1_0 where pt1_0.event_id=?         <- 3
select pt1_0.event_id, ... from price_tier pt1_0 where pt1_0.event_id=?         <- 4
select pt1_0.event_id, ... from price_tier pt1_0 where pt1_0.event_id=?         <- 5
select pt1_0.event_id, ... from price_tier pt1_0 where pt1_0.event_id=?         <- 6
```

Fingerprint: one list query, then the same statement repeated with different bound values.

**Fix 1 — the fetch join.** Tell JPA, *for this one query*, "bring the tiers along": the list
query becomes a single `SELECT` with a `LEFT JOIN` to `price_tier`; 6 statements become 1.
(Two syntaxes, same effect: a JPQL `join fetch` query, or `@EntityGraph` on the repository
method.) *Trade-offs:* the joined result repeats each event once per tier (more data on the
wire); **pagination breaks** — `LIMIT` counts joined rows, not events, so Hibernate loads
everything and paginates in memory, with a warning; joining two collections at once multiplies
rows. Best for one event's detail page or small unpaginated lists.

**Fix 2 — batch fetching.** Collections stay lazy, but a batch size is set (per collection or
app-wide). On the first touch, Hibernate loads tiers for up to `size` events in one
`WHERE event_id IN (?, ?, …)` query: 1+N becomes 1 + N/size rounded up (200 events, size 16 →
14 statements). *Trade-offs:* still more than one query; an app-wide setting silently changes
loading everywhere; the size needs choosing. Pagination-safe and nearly free to adopt — often
the pragmatic default.

**Fix 3 — the DTO projection.** Step back: a list page shows a few columns. Don't load
entities at all — one purpose-built query selects exactly those columns (joins and aggregates
in the database) into a small read-only carrier object. No entities, no lazy collections: one
query shaped like the page. *Trade-offs:* one extra query and type to maintain per screen;
results are plain data, not managed entities — no dirty checking, read-only by design (for a
list page, a feature). The truest fix for the names-only page.

**Choosing:** detail page of one event → fetch join. Paginated list genuinely showing tier
data → batch fetching. Names-and-summaries list → DTO projection — or first ask the quiet
question: does this page need tiers at all?

**What each fix changes:** fetch join changes *the query*; batch fetching changes *the loading
pattern*; DTO projection changes *the question*. The rejected fourth, eager loading, changes
*the mapping globally* — every loader everywhere pays, forever. That is why it lost.

### Common mistakes

- Reaching for eager loading as "fix 4" — measured in Lesson 13: statements don't drop, they
  fire unconditionally and invisibly.
- Fetch join + pagination — the in-memory-pagination trap.
- One hammer for every screen — the fixes are per-use-case; the SQL log verifies each choice.

### Do it yourself

No terminal work. Paste the prompt above into an AI of your choice and grade its answer
against this lesson. **Done when:** from memory, you can name the three fixes and one
trade-off for each in one sentence.

### The task checkpoint — four questions, with answers

**Checkpoint 1. You wrote an interface with no implementation and it worked. What happened at
startup?**

**Answer.** Spring Data JPA scanned the classpath for interfaces extending `JpaRepository`
(the log said `Found 2 JPA repository interfaces`) and, for each, built the implementation at
startup: a JDK dynamic proxy (a class like `jdk.proxy2.$Proxy93`) wrapping the ready-made
`SimpleJpaRepository`. Inherited methods like `findById` and `save` run `SimpleJpaRepository`'s
generic code; derived methods like `findByNameContainingIgnoreCase` are parsed from the method
name into a query — at startup, so a typo in the name kills boot with "No property … found".
The proxy was registered as a bean and injected wherever the interface is asked for. I declared
the promise; Spring Data built the worker.

**Checkpoint 2. Describe the N+1 problem using the log output you saw, and say why it worsens
with scale.**

**Answer.** `triggerNPlusOne()` loaded 5 events with one query (`select … from event … fetch
first ? rows only`), then a loop touched each event's lazy `priceTiers` list — and the log
showed five more *identical* `select … from price_tier … where pt1_0.event_id=?` statements,
one per event, different bound value each: 6 statements where one page load should need 1 or 2.
It worsens with scale because the statement count is 1+N, linear in the rows shown: 200 events
mean 201 queries, each a full network round trip to the database. Latency grows with data
volume while the code never changes — the page gets slower the more successful it becomes, and
the Java source shows nothing wrong; only the log does.

**Checkpoint 3. Why must migrations own the schema instead of Hibernate, and what does
`validate` do?**

**Answer.** Because the schema must be identical on every database — laptops, CI, staging,
production — and only versioned files can deliver that: migrations live in git, are reviewed
like code, replay identically on any machine, and leave a ledger (`flyway_schema_history`,
with checksums that forbid rewriting history). Hibernate's schema-writing (`ddl-auto: update`)
is the opposite: unversioned, unreviewed, add-only (a rename strands the old column with its
data), and divergent per environment. `validate` makes Hibernate a read-only inspector: at
every startup, after Flyway has run, it compares every mapped entity against the real schema —
table exists, mapped columns exist, types compatible — and refuses to start on any mismatch
(`Schema-validation: missing column [doors_open_at] in table [event]`). Migrations own;
Hibernate checks.

**Checkpoint 4. Why is keeping `show-sql` on for the whole of Stage F worth the noise in your
log?**

**Answer.** Because the ORM writes SQL invisibly, and the log is the only honest witness of
what actually runs. Every real thing learned in this task was *read from the log*, not from the
Java: the derived method's generated `upper(…) like upper(?)`; one select for two `findById`
calls (the persistence context at work); an `UPDATE` appearing at commit with no `save()`
anywhere; the lazy fetch firing between two print lines; the N+1's six statements; eager
loading's unasked-for tier queries on a names-only search. None of those are visible in the
source — some (the N+1) are invisible precisely because every line looks right. The cost is
scrolling; the payoff is never being lied to about the database. Stage F is where that habit
is built.

### Link to the task

Subtask K complete — and with it, A through K all done.

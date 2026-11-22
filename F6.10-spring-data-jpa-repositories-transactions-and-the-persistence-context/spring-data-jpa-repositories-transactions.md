# F6.10 — Spring Data JPA: repositories, transactions, and the persistence context

Notes for Task F6.10. One lesson per section. Each lesson ends with practice questions and
full answers. Every Java example was compiled and run on Java 21 against the real `zenith`
database. Every output and error shown is real.

**Goal of the task.** You can write a repository interface with no implementation and
explain where the implementation comes from. You can show that changing a loaded entity
saves without calling `save()`. You can reproduce the self-invocation trap that makes
`@Transactional` silently do nothing.

**Done when:** you can demonstrate dirty checking, show a rollback and a non-rollback, and
reproduce the self-invocation trap and fix it.

**You need first:** Task F5.8 (transactions, ACID, isolation — you ran `BEGIN` and
`ROLLBACK` by hand). Task F5.11 (JPA, the SQL log, Flyway). `show-sql` stays on for this
whole task.

**Why this task exists.** JPA is the most convenient and the most dangerous part of this
stack. Phase 3 spends fifteen hours undoing damage caused by the wrong mental model, so this
task builds the right one now. Order and payment correctness in Phase 5 depends entirely on
understanding transaction boundaries.

**What actually matters here.** The persistence context is a **cache with a lifetime**.
Changes to a managed entity are written at commit without you calling `save()`. Not knowing
that produces both phantom updates and updates that never happen.

**Where people get stuck.** `@Transactional` on a method called from inside the same class
does nothing at all — no error, no transaction. That is the self-invocation proxy problem.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Entity** | A Java class mapped to a database table, marked with `@Entity`. |
| **Repository interface** | An interface you declare and never implement. Spring Data generates the implementation at startup. |
| **Derived query** | A query built from the method name, such as `findByVenueIdAndStartsAtAfter`. |
| **JPQL** | A query language that looks like SQL but refers to your entity classes and fields rather than tables and columns. |
| **Persistence context** | A per-transaction workspace holding every entity you have loaded. Often called the first-level cache. |
| **Managed entity** | An object currently inside the persistence context. Changes to it are tracked. |
| **Dirty checking** | Hibernate comparing managed entities against their loaded state at commit and writing an `UPDATE` for anything that changed. |
| **`@Transactional`** | Marks a method as one database transaction: everything inside commits together or rolls back together. |
| **Proxy** | A wrapper object Spring puts around your bean. Calls from outside go through the wrapper, which starts the transaction, then forwards to your real method. |

## The task's subtasks

| # | Subtask |
| --- | --- |
| S1 | Write `EventRepository extends JpaRepository<EventEntity, UUID>` with an empty body. Call `findAll()`. Print `repository.getClass()`. |
| S2 | Look at what you inherited. Compare `CrudRepository` and `JpaRepository`. Pick one on purpose. |
| S3 | Write three derived queries: `findByVenueId`, `findByNameContainingIgnoreCase`, `existsBySlug`. Read their SQL. Then misspell a property and read the startup failure. |
| S4 | Write one `@Query` in JPQL and one with `nativeQuery = true`, both returning a venue's events. |
| S5 | Add paging with `Pageable` and `Page<EventEntity>`. Find the two statements in the log. |
| S6 | Inside `@Transactional`: load an event, rename it, don't call `save()`. See the `UPDATE` at commit. |
| S7 | Do the same with no transaction. Nothing is written. Say the rule out loud. |
| S8 | Put `@Transactional(readOnly = true)` on query methods, and learn why it really speeds things up. |
| S9 | Test the rollback rule: an unchecked exception rolls back, a checked exception commits. |
| S10 | Break it: `createOrder()` calls `this.persistOrder()`. The exception does not undo the write. |
| S11 | Explain this with the proxy. Fix it with a separate bean. |
| S12 | The AI prompt: the transaction proxy, why self-invocation skips it, and three fixes. |
| CP | The five checkpoint questions, CP1 to CP5, answered out loud. |

## Roadmap for this task — 26 small lessons

| # | Lesson | What it gives you | Serves |
| --- | --- | --- | --- |
| 1 | The entity `EventEntity` and `@Table` | The Java class that stands for the `event` table. A repository always manages one entity, so the entity comes first. | S1 |
| 2 | The repository interface | An interface you declare but never implement. You call `findAll()` and read its SQL. | S1 |
| 3 | Where the implementation comes from | You print `getClass()` and see the object Spring Data built at startup. | S1, CP1 |
| 4 | The repository family | What `Repository`, `CrudRepository`, `PagingAndSortingRepository` and `JpaRepository` each add. | S2 |
| 5 | Picking the contract on purpose | Why a smaller interface is easier to replace, and what breaks when you narrow it. | S2 |
| 6 | The method name is the query | `findByVenueId` becomes `WHERE venue_id = ?`. This is the basic grammar. | S3 |
| 7 | Keywords inside a method name | `Containing` becomes `LIKE`. `IgnoreCase` becomes `upper()`. | S3 |
| 8 | A yes/no query: `existsBySlug` | A query that answers `true` or `false`. It needs a new `slug` column, added by a migration. | S3 |
| 9 | Break it: a misspelled property | The app refuses to start and names the bad property. Why failing early is good. | S3 |
| 10 | `@Query` with JPQL | A hand-written query that names the entity class and its fields. | S4 |
| 11 | `@Query` with native SQL | The same query in real SQL, with table and column names. The two compared side by side. | S4 |
| 12 | Paging: `Pageable` and `Page` | Asking for "page 0, 2 rows, sorted by start time", and reading the answer. | S5 |
| 13 | The two statements behind a page | One query for the rows, one `count` for the total, and when you can skip the count. | S5 |
| 14 | `@Transactional`: one method, one transaction | How Spring begins and commits a transaction around your method. You turn on a log that shows it. | S6–S11 |
| 15 | The persistence context and managed entities | The per-transaction workspace that remembers loaded rows. You load one row twice and see one `SELECT`. | S6, CP2 |
| 16 | Dirty checking | You change a name, never call `save()`, and watch the `UPDATE` appear at commit. | S6, CP3 |
| 17 | Outside a transaction, nothing is written | The same change without a transaction writes nothing and shows no error. | S7 |
| 18 | `@Transactional(readOnly = true)` | Proof that Hibernate skips dirty checking, and that Postgres knows the transaction is read-only. | S8 |
| 19 | Checked and unchecked exceptions | The two kinds of Java exceptions. The rollback rule depends on the difference. | S9 |
| 20 | A table to write to: `ticket_order` | A small `OrderEntity` with a generated id, so there are real writes to undo. | S9, S10 |
| 21 | An unchecked exception rolls everything back | A two-write method fails halfway. The `INSERT` is in the log, but the row is gone. | S9, CP5 |
| 22 | A checked exception commits | The same method with a checked exception keeps the first write. The fix is `rollbackFor`. | S9, CP5 |
| 23 | The proxy around your bean | You print your service's class and see the wrapper Spring made. Calls from outside pass through it. | S10, S11 |
| 24 | Break it: the self-invocation trap | `createOrder()` calls `this.persistOrder()`. The exception does not undo the write. | S10 |
| 25 | Why it happens, and the fix | A call through `this` skips the wrapper. Moving `persistOrder` into its own bean fixes it. | S11, CP4 |
| 26 | The AI prompt and the checkpoint | Three ways to restructure the code, then the five checkpoint questions. | S12, CP |

## The scratch app and the database

The scratch app lives in
`F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app`,
package `com.zenith.data`. Spring Boot 3.5.16, Java 21, Hibernate 6.6.53.

It talks to the same `zenith` database as Stage F5. Flyway owns the schema. The migration
files `V1__baseline.sql` and `V2__add_doors_open_at.sql` are byte-for-byte copies of the
F5.11 files, so Flyway sees the existing database as up to date. The dev data file
`seed-dev-data.sql` sits next to the scratch app.

| Thing | Value |
| --- | --- |
| Docker container | `pg` (image `postgres:17-alpine`) |
| Host and port | `localhost:5432` |
| Database | `zenith` |
| User / password | `postgres` / `dev` |
| `event` rows in the seed data | 7 |

**One demo at a time.** From Lesson 2 on, each lesson's demo is a `CommandLineRunner` class
marked `@Profile("some-name")`. It runs only when that profile is switched on:
`mvn -q spring-boot:run -Dspring-boot.run.profiles=some-name`. With no profile, the app
starts, runs no demo, and stops.

---

## Lesson 1 — The entity: `EventEntity`, and why it needs `@Table` (prepares subtask S1)

### Definition

Three words first:

- **Hibernate** is the library that turns Java objects into SQL and back.
- **JPA** is the standard set of annotations Hibernate follows, such as `@Entity`, `@Id` and
  `@Table`. In Spring Boot 3 they live in the package `jakarta.persistence`.
- An **annotation** is a label that starts with `@`, placed on a class or a field.
  Libraries read it.

An **entity** is a Java class that stands for one database table. One object of that class
stands for one row of that table. `@Table(name = "event")` tells Hibernate the exact name of
that table.

### Why it is needed

Hibernate writes SQL for you. To do that, it must know two things:

- Which table does this class stand for?
- Which column does each field stand for?

The entity is where you answer both.

Without an entity, you do this matching by hand. You type the table name and every column
name inside each SQL string. You copy each column's value into a Java field yourself. If
someone renames a column, nothing warns you. The query breaks only when it runs.

Now the specific problem that `@Table` solves. Hibernate guesses the table name from the
class name. For a class called `EventEntity`, the guess is `event_entity`. Our table is
called `event`. A wrong guess stops the app at startup. `@Table(name = "event")` replaces
the guess with the real name.

### Key points

- One entity class stands for one table. One object stands for one row. One mapped field
  stands for one column.
- Without `@Table`, Hibernate guesses the table name from the class name.
- Hibernate guesses each column name from a field name, by the same rule.
- `@Id` marks the primary key field.
- Hibernate needs a constructor with no parameters. It may be `protected`.
- `ddl-auto: validate` checks the mapping against the real database at startup. It never
  changes the database.
- Columns that no field stands for are ignored.

### How it works, step by step

At startup, these steps happen in order.

1. Spring Boot looks for classes marked `@Entity`. It searches the main class's package
   (`com.zenith.data`) and every package inside it.
2. Hibernate works out the table name. If `@Table(name = "...")` is present, it uses that
   name. If not, it guesses from the class name (the exact rule is below). `EventEntity`
   becomes `event_entity`.
3. Hibernate works out each column name from a field name with the same rule. `venueId`
   becomes `venue_id`. `startsAt` becomes `starts_at`. A column is **mapped** when a field
   stands for it.
4. The field marked `@Id` is the **primary key**: the column whose value picks out exactly
   one row.
5. `application.yml` sets `ddl-auto: validate`. This makes Hibernate compare what it
   expects with the real database. Every expected table and column must exist, with a type
   that fits the field. If anything is missing, the app stops. `validate` never changes the
   database. It only checks.
6. Later, when Hibernate loads a row, it calls the constructor that takes no parameters.
   That gives an empty object. Then Hibernate fills each field from its column.

Two more details:

- Hibernate checks only the mapped columns. The `event` table has 7 columns, and
  `EventEntity` maps 4. The other 3 are ignored, and that is allowed.
- Hibernate reads and writes the fields directly, because `@Id` sits on a field. The getters
  are for your own code, not for Hibernate.

**The guessing rule, exactly.** Spring Boot's default rule is a class called
`CamelCaseToUnderscoresNamingStrategy`. It puts an underscore before a capital letter only
when there is a lowercase letter right before it **and** a lowercase letter right after it.
Then it makes the whole name lowercase. These are real results from running Hibernate's own
class on each name:

| Java name | Guessed database name | Why |
| --- | --- | --- |
| `EventEntity` | `event_entity` | `E` sits between `t` and `n`, so an underscore goes before it. |
| `venueId` | `venue_id` | `I` sits between `e` and `d`. |
| `startsAt` | `starts_at` | `A` sits between `s` and `t`. |
| `TicketOrder` | `ticket_order` | `O` sits between `t` and `r`. |
| `buyerEmail` | `buyer_email` | `E` sits between `r` and `m`. |
| `URLRecord` | `urlrecord` | Every capital has another capital next to it. No underscores. |
| `seatX` | `seatx` | `X` is the last letter, so nothing comes after it. No underscore. |
| `ticket2Go` | `ticket2go` | A digit, not a lowercase letter, comes before `G`. No underscore. |
| `Order` | `order` | One word. But `order` is a reserved word in SQL. |

### Example

The real table (`docker exec pg psql -U postgres -d zenith -c '\d event'`, with the empty
Collation column removed):

```text
    Column     |           Type           | Nullable | Default
---------------+--------------------------+----------+---------
 id            | uuid                     | not null |
 venue_id      | uuid                     | not null |
 name          | text                     | not null |
 starts_at     | timestamp with time zone | not null |
 created_at    | timestamp with time zone | not null | now()
 cancelled_at  | timestamp with time zone |          |
 doors_open_at | timestamp with time zone |          |
```

The class, `src/main/java/com/zenith/data/EventEntity.java`:

```java
package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")   // without this, Hibernate guesses the table name "event_entity"
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
}
```

The setting that turns on the startup check, in `src/main/resources/application.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # Hibernate checks the schema at startup, never changes it
```

### Walkthrough

**How each line of the class matches the table:**

| Java side | Database side | How Hibernate matched them |
| --- | --- | --- |
| `@Entity` on `class EventEntity` | none | "This class stands for a table." |
| `@Table(name = "event")` | table `event` | It uses the name in `@Table`, so there is no guessing. |
| `@Id private UUID id` | `id uuid` | Same name. `@Id` marks the primary key. |
| `private UUID venueId` | `venue_id uuid` | `venueId` becomes `venue_id`. |
| `private String name` | `name text` | Same name. |
| `private OffsetDateTime startsAt` | `starts_at timestamp with time zone` | `startsAt` becomes `starts_at`. `OffsetDateTime` is a date and time with a time-zone offset, like `+05:30`. |
| no field | `created_at`, `cancelled_at`, `doors_open_at` | Not mapped, so Hibernate ignores them. |
| `protected EventEntity() {}` | none | Hibernate uses this to build an empty object, then fills the fields. `protected` shows that this constructor is for Hibernate. Code outside the package cannot call it. |

**Run 1 — with `@Table`.** Command: `mvn -q spring-boot:run`. The important lines of the
real log, with timestamps removed:

```text
Finished Spring Data repository scanning in 3 ms. Found 0 JPA repository interfaces.
HikariPool-1 - Start completed.
Successfully validated 2 migrations (execution time 00:00.012s)
Current version of schema "public": 2
Schema "public" is up to date. No migration necessary.
HHH000412: Hibernate ORM core version 6.6.53.Final
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started DataApplication in 1.429 seconds (process running for 1.556)
```

Reading it, line by line:

1. `Found 0 JPA repository interfaces` — Spring Data is the part of Spring that builds
   repositories. It found none, because no repository had been written yet.
2. `HikariPool-1 - Start completed` — the app opened its connection pool: a small set of
   open database connections that the app reuses.
3. The three Flyway lines — Flyway is the tool that builds tables by running numbered SQL
   files, called **migrations**. It found V1 and V2. Both were already applied, so it did
   nothing.
4. `Hibernate ORM core version 6.6.53.Final` — Hibernate is starting.
5. `Initialized JPA EntityManagerFactory` — the EntityManagerFactory is Hibernate's main
   object, built once at startup. This line appears only if the check passed. So
   `EventEntity` matches the `event` table.
6. `Started DataApplication` — the app is up. It has no web server, so it stops right
   after. That is expected.

**Run 2 — the same app with the `@Table(name = "event")` line deleted.** Real output,
trimmed:

```text
ERROR ... Failed to initialize JPA EntityManagerFactory: [PersistenceUnit: default] Unable to build Hibernate SessionFactory; nested exception is org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [event_entity]
ERROR ... Application run failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory' ...
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [event_entity]
[ERROR] Failed to execute goal ... spring-boot-maven-plugin:3.5.16:run ... Process terminated with exit code: 1
```

| Part of the message | Plain meaning |
| --- | --- |
| `Schema-validation` | The `ddl-auto: validate` check ran. |
| `missing table [event_entity]` | Hibernate looked for a table called `event_entity` and found none. It guessed that name from `EventEntity`. |
| `Error creating bean with name 'entityManagerFactory'` | A bean is an object Spring creates and manages. Spring could not build Hibernate's main object. Without it, nothing can talk to the database. |
| `Application run failed`, `exit code: 1` | The app stopped. None of your code ran. |

Tip: in a long error, read the **last** `Caused by:` line first. It names the root cause.

With the line put back, the app starts again.

### Important terms

| Term | Meaning |
| --- | --- |
| **Hibernate** | The library that turns Java objects into SQL and back. |
| **JPA** | The standard set of annotations Hibernate follows (`@Entity`, `@Id`, `@Table`, `@Column`). |
| **Entity** | A Java class that stands for one table. One object stands for one row. |
| **`@Table(name = "...")`** | Gives the exact table name, so Hibernate does not guess. |
| **`@Column(name = "...")`** | Gives the exact column name for one field, so Hibernate does not guess. |
| **`@Id`** | Marks the field that holds the primary key. |
| **Primary key** | The column whose value picks out exactly one row. |
| **Mapped column** | A column that some field of the entity stands for. |
| **`ddl-auto: validate`** | At startup, Hibernate checks that every expected table and column exists with a fitting type. It never changes the database. |
| **Flyway** | The tool that builds and changes tables by running numbered SQL files at startup. |
| **Migration** | One of those numbered SQL files, such as `V1__baseline.sql`. |
| **EntityManagerFactory** | Hibernate's main object, built once at startup after reading and checking every entity. |
| **Bean** | An object Spring creates and manages. |
| **Connection pool** | A small set of open database connections that the app reuses (HikariCP here). |
| **`jakarta.persistence`** | The package of the JPA annotations in Spring Boot 3. Spring Boot 2 used `javax.persistence`. |

### Where it is used

- Every table you read or write through JPA gets one entity class.
- Many teams end entity names with `Entity`. This keeps the plain name `Event` free for other
  classes. One example is the record your API sends back as JSON. The entity is shaped like
  the table. The API class is shaped like what the client needs. Keeping them apart means a
  table change cannot silently change your API. That is why the task says `EventEntity`.
- `@Table` is also needed when the guessed name cannot be used. A class named `Order` would
  map to `order`, which is a reserved word in SQL. So the table gets another name, like
  `ticket_order`. Then `@Table(name = "ticket_order")` points the class at it.

### Edge cases

- **Capitals in a row, or at the end, get no underscore.** `URLRecord` maps to `urlrecord`
  and `seatX` maps to `seatx` (real results from Hibernate's own naming class). If the real
  table or column is `url_record` or `seat_x`, you need `@Table` or `@Column`.
- **A correct guess needs no `@Table`.** A class `TicketOrder` maps to `ticket_order` by
  itself. `@Table` is required only when the guess is wrong.
- **Unmapped columns are ignored by `validate`, but not by `INSERT`.** When Hibernate inserts
  a row, it lists only the mapped columns. The database fills the rest with their default,
  or with null. If an unmapped column is `NOT NULL` with no default, every insert fails. The
  three unmapped `event` columns are safe: `created_at` has a default, and the other two
  allow null.
- **`validate` checks types too, not only names.** A column must exist **and** its type must
  fit the field's type.

### Common mistakes — with the real errors

All of these were produced on this project, then fixed.

1. **Forgetting `@Table` when the class name and table name differ.** You get
   `Schema-validation: missing table [event_entity]`. Fix: add `@Table(name = "event")`.
2. **A field name that does not match its column.** With `startsAt` renamed to `startAt`:

   ```text
   Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing column [start_at] in table [event]
   ```

   Hibernate guessed `start_at` from `startAt`, but the real column is `starts_at`. Fix:
   rename the field back. If you really want a different field name, put
   `@Column(name = "starts_at")` on the field.
3. **Importing from the old package.** Old tutorials, written for Spring Boot 2, use
   `import javax.persistence.Entity;`. Spring Boot 3 uses `jakarta.persistence`. The real
   compile error:

   ```text
   EventEntity.java:[3,25] package javax.persistence does not exist
   EventEntity.java:[11,2] cannot find symbol
     symbol: class Entity
   ```

   Fix: change `javax` to `jakarta`.
4. **Expecting `validate` to fix the table.** It never creates or changes anything. It only
   checks. To change a table, add a new Flyway migration file.

### Link to the task

Subtask S1 asks for `interface EventRepository extends JpaRepository<EventEntity, UUID>`.
The first type, `EventEntity`, is this class. The second type, `UUID`, is the type of its
`@Id` field. The repository cannot exist without the entity.

The fields matter later too. `findByVenueId` (S3) needs the `venueId` field.
`findByNameContainingIgnoreCase` needs the `name` field. `existsBySlug` will need a new
`slug` field.

### Do it yourself (second laptop)

**Step 0 — tools (once per laptop).** Skip anything already installed. If `brew` itself is
missing, install Homebrew from [brew.sh](https://brew.sh) first. Docker Desktop works
instead of `colima`.

```bash
brew install openjdk@21 maven
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
brew install colima docker
colima start
```

Check them:

```bash
java -version    # expect: openjdk version "21..."
mvn -version     # expect: Apache Maven 3.9...
docker ps        # expect: a header line starting with CONTAINER ID
```

If `docker ps` says `Cannot connect to the Docker daemon`, Docker is not running. Run
`colima start`, or open Docker Desktop.

**Step 1 — Postgres (once per laptop).** Try `docker start pg`. If it prints
`No such container: pg`, create the container. Wait about 5 seconds, then create the
database:

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
docker exec pg psql -U postgres -c 'CREATE DATABASE zenith;'
```

Expected: `CREATE DATABASE`. `ERROR: database "zenith" already exists` is fine.

**Step 2 — folders.**

```bash
mkdir -p ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app/src/main/java/com/zenith/data
mkdir -p ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app/src/main/resources/db/migration
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
```

**Step 3 — four files, each with exactly this content.**

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
    <artifactId>data-scratch</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Data JPA (repositories), the JPA annotations, Hibernate,
             and the Hikari connection pool. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <!-- The Postgres JDBC driver: needed only when the app runs. -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- Flyway: migrations own the schema. -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
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

`src/main/resources/application.yml`:

```yaml
# Same local Postgres and the same zenith database as Stage F5.
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zenith
    username: postgres
    password: dev

  jpa:
    show-sql: true          # print every SQL statement Hibernate sends - on for the whole task
    hibernate:
      ddl-auto: validate    # Hibernate checks the schema at startup, never changes it
    properties:
      hibernate:
        format_sql: true    # pretty-print those statements
```

`src/main/java/com/zenith/data/DataApplication.java`:

```java
package com.zenith.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataApplication.class, args);
    }
}
```

`src/main/java/com/zenith/data/EventEntity.java`:

```java
package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")   // without this, Hibernate guesses the table name "event_entity"
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
}
```

**Step 4 — the two migration files.** First, ask the database what Flyway has already
applied:

```bash
docker exec pg psql -U postgres -d zenith -c 'SELECT version, description FROM flyway_schema_history;'
```

*Case A — it lists versions 1 and 2.* This laptop already has the tables from F5.11. Copy
your own two files from your F5.11 project into `src/main/resources/db/migration/`. Do not
retype them. Flyway stores a **checksum** for every applied file: a number computed from
the file's content, like a fingerprint. If one character changes, the number changes, and
Flyway refuses to start with a message containing
`checksum mismatch for migration version 1`.

*Case B — it says `relation "flyway_schema_history" does not exist`.* The database is
empty. Create both files with exactly this content.

`src/main/resources/db/migration/V1__baseline.sql`:

```sql
-- V1__baseline.sql
-- The complete zenith schema as it stood after the hand-made era:
--   F5.2 tables (venue, event, price_tier), F5.3 constraints and indexes,
--   F5.4 cancelled_at, F5.9 users and roles.
-- From this file onward, migrations own the schema. Never edit this file after it runs.

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

`src/main/resources/db/migration/V2__add_doors_open_at.sql` (two underscores after `V2`):

```sql
-- V2__add_doors_open_at.sql
-- The proper fix for the Lesson 17 drift: the entity gained doorsOpenAt,
-- so the schema gains doors_open_at - as a new, versioned, reviewed migration.
-- V1 stays untouched: it is history.

alter table event add column doors_open_at timestamptz;
```

**Step 5 — run it.**

```bash
mvn -q spring-boot:run
```

The first run downloads many libraries. That is normal.

Success in Case A includes these lines:

```text
Successfully validated 2 migrations
Current version of schema "public": 2
Schema "public" is up to date. No migration necessary.
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started DataApplication in ... seconds
```

In Case B you see `Migrating schema "public" to version "1 - baseline"` and then
`to version "2 - add doors open at"` instead of "up to date". The last two lines are the
same. Then the app stops by itself, because it has no web server.

Failures you might see:

- `Connection to localhost:5432 refused` — Postgres is not running. Run `docker start pg`.
- `FATAL: database "zenith" does not exist` — the `CREATE DATABASE` command in Step 1 was
  skipped.
- `package javax.persistence does not exist` — an import says `javax`. Change it to
  `jakarta`.

**Step 6 — break it and read it.** Delete the line `@Table(name = "event")`. Run
`mvn -q spring-boot:run`. Find this line:

```text
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [event_entity]
```

Put the line back and run again. The app starts.

**Done when:**

- your app prints `Initialized JPA EntityManagerFactory` and `Started DataApplication`;
- you have seen `missing table [event_entity]` on your own screen;
- you can say why Hibernate guessed that name.

### Summary for revision

- An entity is a class that stands for a table. One object stands for one row.
- Without `@Table`, the table name is guessed: an underscore before a capital that has
  lowercase letters on both sides, then all lowercase. `EventEntity` gives `event_entity`.
- Field names become column names by the same rule. `startsAt` gives `starts_at`.
- `@Id` marks the primary key. Hibernate needs an empty constructor.
- `ddl-auto: validate` checks names and types at startup, and never changes anything.
- `missing table [x]` or `missing column [x] in table [y]` means the guess is wrong. Fix it
  with the right name, `@Table` or `@Column`. Never fix it by editing the database by hand.
- Spring Boot 3 imports come from `jakarta.persistence`, not `javax.persistence`.

### Practice questions, with answers

**Question 1.** A Spring Boot 3 app has `spring.jpa.hibernate.ddl-auto: validate`. Its
database has this table:

```sql
create table ticket_order (
    id          uuid primary key,
    buyer_email text not null,
    quantity    integer not null
);
```

The app has this class, and no other entity:

```java
@Entity
public class TicketOrder {
    @Id
    private UUID id;
    private String buyerEmail;
    private int quantity;

    protected TicketOrder() {}
}
```

There is no `@Table` on the class. Does the app start, or does it fail? Write down the
table name and the three column names Hibernate expects. Explain how you worked out each
one.

**Answer.** The app starts. Hibernate guesses every name, and every guess is right:

- Table: `TicketOrder` becomes `ticket_order`. The capital `O` has lowercase letters on
  both sides (`t` before, `r` after), so an underscore goes before it. Then everything
  becomes lowercase.
- Column `id`: the field `id` has no capitals, so the name stays `id`.
- Column `buyer_email`: in `buyerEmail`, the capital `E` sits between `r` and `m`, so an
  underscore goes before it.
- Column `quantity`: no capitals, so the name stays `quantity`.

All three columns exist, and the types fit (`UUID` with `uuid`, `String` with `text`, `int`
with `integer`). So `validate` passes. `@Table` is needed only when the guess is wrong.

**Question 2.** Another Spring Boot 3 app, also with `ddl-auto: validate`, stops at startup
with this line:

```text
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [venue_entity]
```

The database has a table named `venue` with the columns `id`, `name` and `city`. Here is
the class:

```java
@Entity
public class VenueEntity {
    @Id
    private UUID id;
    private String name;
    private String city;

    protected VenueEntity() {}
}
```

Explain in your own words why Hibernate looked for `venue_entity`. Then write the one line
that fixes it, and say where it goes.

**Answer.** The class has no `@Table`, so Hibernate guessed the table name from the class
name. In `VenueEntity`, the capital `E` sits between `e` and `n`, so an underscore goes
before it. Then everything becomes lowercase: `venue_entity`. No table has that name, so
the startup check failed and the app stopped.

The fix is one line, placed directly above the class declaration, next to `@Entity`:

```java
@Table(name = "venue")
```

It needs the import `jakarta.persistence.Table`. The columns `id`, `name` and `city`
already match the field names, so nothing else changes.

**Question 3.** An app with `ddl-auto: validate` stops at startup with:

```text
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing column [start_time] in table [event]
```

The `event` table has a column named `starts_at`. The entity has the field
`private OffsetDateTime startTime;`. Explain the message, and give two different fixes.

**Answer.** Hibernate found the `event` table. Then it guessed a column name from the field
`startTime`: the capital `T` sits between `t` and `i`, so the guess is `start_time`. No
column has that name, so the check failed.

- Fix 1: rename the field to `startsAt`. The guess becomes `starts_at`, which exists.
- Fix 2: keep the field name and tell Hibernate the column directly:
  `@Column(name = "starts_at") private OffsetDateTime startTime;` (import
  `jakarta.persistence.Column`).

Wrong fix: renaming the column in the database by hand. `validate` would pass on this one
database, but every other database would still have `starts_at`, and the change would have
no migration recording it.

---

## Lesson 2 — The repository interface: you declare it, you never implement it (prepares subtask S1)

### Definition

Three words first:

- An **interface** is a list of methods with no bodies. Each method has a name, parameter
  types and a return type, and nothing more. A class that `implements` the interface must
  write a body for every method.
- A **type parameter** is a blank in a type, written in angle brackets. The writer of
  `JpaRepository<T, ID>` left two blanks, `T` and `ID`. You fill them in when you use it:
  `JpaRepository<EventEntity, UUID>`.
- **Spring Data** is the part of Spring that builds repositories.

A **repository** is the object your code asks for rows of one table. You say "give me all
events". It runs the SQL and hands back `EventEntity` objects.

A **repository interface** is an interface you write for one entity. It extends
`JpaRepository<EventEntity, UUID>`. You never write a class that implements it. Spring Data
supplies a working object for it when the app starts.

### Why it is needed

Every table needs the same basic actions: find all rows, find one row by id, count, save,
delete. Without Spring Data, you write each of these by hand, for every entity.

`JpaRepository` declares 30 such methods. Here is real proof of how much work that is. A
class that tries to implement `EventRepository` by hand does not compile. Java demands a
body for every one of the 30 methods:

```text
EventRepositoryByHand.java:[4,1] com.zenith.data.EventRepositoryByHand is not abstract and does not override abstract method <S>findAll(org.springframework.data.domain.Example<S>,org.springframework.data.domain.Sort) in org.springframework.data.jpa.repository.JpaRepository
```

With four entities, that would be 120 method bodies, and most of them would be copies of each
other. With a repository interface, each entity needs one short file and no method bodies
at all.

### Key points

- The whole repository is one declaration with an empty body:
  `public interface EventRepository extends JpaRepository<EventEntity, UUID> {}`.
- The first blank is the entity the repository stores. The second blank is the type of
  that entity's `@Id` field.
- The interface inherits 30 methods. The ones used most: `findAll()`, `findById(id)`,
  `count()`, `existsById(id)`, `save(entity)`, `deleteById(id)`.
- At startup, Spring Data finds repository interfaces in the main class's package and every
  package inside it. The log line `Found 1 JPA repository interface` confirms it.
- For each interface it finds, Spring creates an object whose methods really work, and keeps
  it as a bean. Your class receives it through its constructor.
- `findAll()` returns `List<EventEntity>`. Hibernate's SQL reads only the mapped columns.
- `findAll()` has no `ORDER BY`. The order of the rows is not guaranteed.

### How it works, step by step

1. You write the interface, and it extends `JpaRepository<EventEntity, UUID>`.
2. Java fills the blanks in every inherited method. `List<T> findAll()` becomes
   `List<EventEntity> findAll()`. `Optional<T> findById(ID id)` becomes
   `Optional<EventEntity> findById(UUID id)`. An `Optional` is a box that holds either one
   object or nothing, because a row with that id may not exist.
3. At startup, Spring Data searches `com.zenith.data` (the main class's package) and every
   package inside it. It looks for interfaces that extend a Spring Data repository interface.
   The log reports what it found: `Found 1 JPA repository interface.`
4. For each interface found, Spring Data creates an object whose methods really work. Spring
   keeps that object as a **bean**: an object Spring creates and manages.
5. A class that needs the repository lists it as a constructor parameter:
   `FindAllDemo(EventRepository events)`. Spring sees the parameter and passes the bean in.
   This is **constructor injection**.
6. Your code calls `events.findAll()`.
7. Hibernate builds the SQL from the entity: the table name from `@Table`, the column names
   from the fields. It sends `select ... from event`.
8. For each row, Hibernate makes an empty `EventEntity` with the constructor that takes no
   parameters, then fills its fields from the columns. You get back a `List` of those
   objects.

**The 30 inherited methods** (real list, read from the compiled interfaces by a small Java
program). Most are rarely needed. Do not learn them by heart.

| Kind | Methods |
| --- | --- |
| Find | `findAll()`, `findAll(Sort)`, `findAll(Pageable)`, `findAllById(ids)`, `findById(id)`, `getReferenceById(id)`, `getById(id)`, `getOne(id)` |
| Count and check | `count()`, `existsById(id)` |
| Save | `save(entity)`, `saveAll(entities)`, `saveAndFlush(entity)`, `saveAllAndFlush(entities)`, `flush()` |
| Delete | `delete(entity)`, `deleteById(id)`, `deleteAll()`, `deleteAll(entities)`, `deleteAllById(ids)`, `deleteAllInBatch()`, `deleteAllInBatch(entities)`, `deleteAllByIdInBatch(ids)` |
| Query by example | `findOne(Example)`, `findAll(Example)`, `findAll(Example, Sort)`, `findAll(Example, Pageable)`, `count(Example)`, `exists(Example)`, `findBy(Example, Function)` |

### Running one demo at a time: `CommandLineRunner` and `@Profile`

Two small tools let each lesson have its own demo:

- A **`CommandLineRunner`** is a bean with one method, `run`. Spring Boot calls it once,
  right after the line `Started DataApplication`.
- A **profile** is a name you switch on when you start the app. `@Profile("find-all")` on a
  class means: create this bean only when the profile `find-all` is on.

You switch the profile on in the run command:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all
```

The log confirms it: `The following 1 profile is active: "find-all"`. Without the flag,
the demo bean is never created, so no demo runs.

### Example

`src/main/java/com/zenith/data/EventRepository.java`, the whole file:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
}
```

`src/main/java/com/zenith/data/FindAllDemo.java`:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 2: call one inherited method and read the SQL it produced.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all
@Component
@Profile("find-all")
class FindAllDemo implements CommandLineRunner {

    private final EventRepository events;

    FindAllDemo(EventRepository events) {   // Spring passes the repository in
        this.events = events;
    }

    @Override
    public void run(String... args) {
        List<EventEntity> all = events.findAll();
        System.out.println(">>> findAll() returned " + all.size() + " events:");
        for (EventEntity e : all) {
            System.out.println(">>>   " + e.getName() + " | venue " + e.getVenueId() + " | " + e.getStartsAt());
        }
    }
}
```

`@Component` tells Spring: create one object of this class and keep it as a bean.

### Walkthrough

Command: `mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all`. The important lines
of the real log, with timestamps removed:

```text
The following 1 profile is active: "find-all"
Bootstrapping Spring Data JPA repositories in DEFAULT mode.
Finished Spring Data repository scanning in 12 ms. Found 1 JPA repository interface.
...
Initialized JPA EntityManagerFactory for persistence unit 'default'
Started DataApplication in 1.175 seconds (process running for 1.299)
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0
>>> findAll() returned 7 events:
>>>   Monsoon Rock Night | venue 11111111-1111-1111-1111-111111111111 | 2026-11-20T13:30Z
>>>   Jazz at the Note | venue 22222222-2222-2222-2222-222222222222 | 2026-11-28T15:30Z
>>>   Open Mic Night | venue 22222222-2222-2222-2222-222222222222 | 2026-12-12T14:30Z
>>>   Sufi Night | venue 11111111-1111-1111-1111-111111111111 | 2027-07-10T14:00Z
>>>   Indie Fest | venue 11111111-1111-1111-1111-111111111111 | 2027-07-24T11:30Z
>>>   Blues Brunch | venue 22222222-2222-2222-2222-222222222222 | 2027-07-18T06:30Z
>>>   Classical Evening | venue 11111111-1111-1111-1111-111111111111 | 2026-12-05T13:00Z
```

Reading it, line by line:

1. `profile is active: "find-all"` — the flag worked, so the `FindAllDemo` bean exists.
2. `Found 1 JPA repository interface` — Spring Data found `EventRepository`. Before that
   file existed, this line said `Found 0 JPA repository interfaces`.
3. `Started DataApplication` — startup is finished. Now Spring Boot calls
   `FindAllDemo.run`.
4. `Hibernate:` and the `select` — the one SQL statement that `findAll()` caused.
   `show-sql: true` prints it. `format_sql: true` spreads it over several lines.
5. `ee1_0` is an **alias**: a short nickname for the table, used only inside this one
   statement. Hibernate builds it from the capital letters of `EventEntity` (`E`, `E`,
   lowercased) plus a counter. `ee1_0.name` means "the `name` column of that table".
6. Only four columns are read: `id`, `name`, `starts_at`, `venue_id`. These are the mapped
   ones. `created_at`, `cancelled_at` and `doors_open_at` have no field, so they are not
   read.
7. No `WHERE`, so every row comes back. No `ORDER BY`, so the rows come in whatever order
   Postgres finds them. Look at the dates: they are not sorted.
8. `7 events` — one object per row. `2026-11-20T13:30Z` is how an `OffsetDateTime` prints:
   the date, a `T`, the time, then `Z`, which means offset zero (UTC).

| Your Java | What happened |
| --- | --- |
| `extends JpaRepository<EventEntity, UUID>` | `findAll()` exists and returns `List<EventEntity>`. |
| `FindAllDemo(EventRepository events)` | Spring passed in the repository bean. |
| `events.findAll()` | `select ee1_0.id, ee1_0.name, ee1_0.starts_at, ee1_0.venue_id from event ee1_0` |
| `all.size()` | `7`, one object per row. |

### Important terms

| Term | Meaning |
| --- | --- |
| **Repository** | The object your code asks for rows of one table. It hands back entity objects. |
| **Repository interface** | An interface you write for one entity, extending `JpaRepository<Entity, IdType>`. You never implement it. |
| **Interface** | A list of methods with no bodies. A class that implements it must write every body. |
| **Type parameter** | A blank in a type, in angle brackets. `JpaRepository<T, ID>` has two, and you fill them in. |
| **`JpaRepository<T, ID>`** | Spring Data's interface with 30 ready-made methods for entity type `T`, whose `@Id` has type `ID`. |
| **`List<EventEntity>`** | What `findAll()` returns: a list of entity objects, one per row. |
| **`Optional<EventEntity>`** | What `findById(id)` returns: a box holding one object, or nothing when no row has that id. |
| **Spring Data** | The part of Spring that finds repository interfaces and supplies a working object for each. |
| **Bean** | An object Spring creates and manages. |
| **`@Component`** | Tells Spring to create one object of the class and keep it as a bean. |
| **Constructor injection** | Listing what your class needs as constructor parameters, so Spring passes it in. |
| **`CommandLineRunner`** | A bean whose `run` method Spring Boot calls once, right after startup. |
| **Profile** | A name switched on at startup. A bean marked `@Profile("x")` exists only when profile `x` is on. |
| **Alias** | A short table nickname inside one SQL statement, such as `ee1_0`. |

### Where it is used

- Every Spring Boot app that uses JPA has one repository interface for each entity it reads
  or writes directly.
- Services, the classes that hold the business rules, receive repositories through their
  constructors and call them.
- Your code depends only on the interface. So a test can pass in a fake object with the same
  methods instead of a real database.

### Edge cases

- **The second blank is not checked at startup.** With `JpaRepository<EventEntity, Long>`,
  the app starts and `findAll()` works. The mistake shows only when an id is passed in, for
  example `findById(1L)`. The real error is in "Common mistakes" below.
- **No `ORDER BY` means no promised order.** The rows above came back unsorted. The order
  can change after rows are updated. When the order matters, ask for sorting on purpose.
  Sorting comes with paging, later in this task.
- **`findAll()` loads every row into memory.** For 7 rows that is fine. For a table with a
  million rows, one call creates a million objects. Paging, later in this task, is the fix.
- **Packages inside the main class's package are searched too.** A repository in
  `com.zenith.data.repo` is found. A repository in `com.zenith.repo` is not, because that
  package sits beside `com.zenith.data`, not inside it.

### Common mistakes — with the real errors

All three were produced on this project, then undone.

1. **Writing the implementation yourself.** This file:

   ```java
   package com.zenith.data;

   // Trying to write the implementation yourself.
   class EventRepositoryByHand implements EventRepository {
   }
   ```

   The real compile error from `mvn -q compile`:

   ```text
   EventRepositoryByHand.java:[4,1] com.zenith.data.EventRepositoryByHand is not abstract and does not override abstract method <S>findAll(org.springframework.data.domain.Example<S>,org.springframework.data.domain.Sort) in org.springframework.data.jpa.repository.JpaRepository
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `[4,1]` | Line 4, column 1 of the file: the `class` line. |
   | `is not abstract` | It is a normal class, so every method must have a body. |
   | `does not override abstract method <S>findAll(...Example<S>,...Sort)` | The first missing body the compiler reports. 29 more are missing too. |
   | `in ...JpaRepository` | That method was inherited from `JpaRepository`. |

   Fix: delete the class. Spring Data supplies the working object.

2. **The wrong id type.** `EventRepository` was changed to
   `extends JpaRepository<EventEntity, Long>`, but the `@Id` field is `UUID`. A demo called
   `findAll()` and then `findById(1L)`. The app started and `findAll()` returned 7 events.
   Then:

   ```text
   org.springframework.dao.InvalidDataAccessApiUsageException: Supplied id had wrong type: entity 'com.zenith.data.EventEntity' has id type 'class java.util.UUID' but supplied id was of type 'class java.lang.Long'
   ...
   Caused by: org.hibernate.TypeMismatchException: Supplied id had wrong type: entity 'com.zenith.data.EventEntity' has id type 'class java.util.UUID' but supplied id was of type 'class java.lang.Long'
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `InvalidDataAccessApiUsageException` | Spring's name for "the database code was used wrongly". |
   | `has id type 'class java.util.UUID'` | The `@Id` field of `EventEntity` is a `UUID`. |
   | `supplied id was of type 'class java.lang.Long'` | The code passed a `Long`, because the second blank said `Long`. |
   | `Caused by: org.hibernate.TypeMismatchException` | Hibernate found the mismatch when it tried to load the row. |

   Fix: the second blank must be the type of the `@Id` field, so `UUID`. Then the compiler
   itself refuses `findById(5L)`:
   `incompatible types: long cannot be converted to java.util.UUID`. The mistake is caught
   before the app ever runs.

3. **The repository in a package that is not searched.** The file was moved to
   `src/main/java/com/zenith/repo/`, and its first line became `package com.zenith.repo;`.
   The real output:

   ```text
   Finished Spring Data repository scanning in 4 ms. Found 0 JPA repository interfaces.
   ...
   ***************************
   APPLICATION FAILED TO START
   ***************************

   Description:

   Parameter 0 of constructor in com.zenith.data.FindAllDemo required a bean of type 'com.zenith.repo.EventRepository' that could not be found.


   Action:

   Consider defining a bean of type 'com.zenith.repo.EventRepository' in your configuration.
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Found 0 JPA repository interfaces` | Spring Data searched `com.zenith.data` and the packages inside it. `com.zenith.repo` is not inside it. |
   | `Parameter 0 of constructor in ...FindAllDemo` | The first constructor parameter of `FindAllDemo`, which is `EventRepository events`. |
   | `required a bean ... that could not be found` | Spring had no repository bean to pass in, so it could not create `FindAllDemo`. |
   | `Consider defining a bean ...` | A general hint from Spring Boot. Here it points the wrong way: do not write a bean by hand. |

   Fix: move the interface back into `com.zenith.data`, or into a package inside it, and make
   its `package` line match the folder.

### Link to the task

Subtask S1 asks for `interface EventRepository extends JpaRepository<EventEntity, UUID>`
with nothing else, and a call to `findAll()`. That is this lesson. The file has an empty
body, and `FindAllDemo` calls `findAll()`. The rest of S1 is printing `repository.getClass()`.
It answers the question this lesson leaves open: nobody wrote the working methods, so where
did they come from?

### Do it yourself (second laptop)

**Starting point.** The scratch app with `pom.xml`, `application.yml`,
`DataApplication.java`, `EventEntity.java` and the two migrations, where
`mvn -q spring-boot:run` ends with `Started DataApplication`. These are the setup steps in
the `EventEntity` lesson of these notes. Do them first if this laptop does not have the app
yet.

**Step 1 — make sure the `event` table has rows.**

```bash
docker exec pg psql -U postgres -d zenith -c 'SELECT count(*) FROM event;'
```

Expected:

```text
 count 
-------
     7
(1 row)
```

If it says `7`, go to Step 2. If it says `0`, the database has tables but no rows. Create
the file `seed-dev-data.sql` in the task folder, next to `scratch-app`
(`~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/seed-dev-data.sql`),
with exactly this content:

```sql
INSERT INTO public.app_user (id, email, display_name, created_at) VALUES ('cccccccc-0000-0000-0000-000000000001', 'meera@example.com', 'Meera', '2026-10-25 12:50:24.815716+00');
INSERT INTO public.app_user (id, email, display_name, created_at) VALUES ('cccccccc-0000-0000-0000-000000000002', 'arjun@example.com', 'Arjun', '2026-10-25 12:50:24.815716+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000, NULL, '2026-10-24 13:04:21.937358+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('22222222-2222-2222-2222-222222222222', 'The Blue Note', 'Mumbai', 350, NULL, '2026-10-24 13:04:21.937358+00');
INSERT INTO public.venue (id, name, city, capacity, seat_map_hints, created_at) VALUES ('33333333-3333-3333-3333-333333333333', 'Lakeside Hall', 'Chennai', 800, NULL, '2026-10-24 19:37:01.766814+00');
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Monsoon Rock Night', '2026-11-20 13:30:00+00', '2026-10-24 13:04:21.939416+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'Jazz at the Note', '2026-11-28 15:30:00+00', '2026-10-24 13:04:21.939416+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'Open Mic Night', '2026-12-12 14:30:00+00', '2026-10-24 20:34:40.288633+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Sufi Night', '2027-07-10 14:00:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Indie Fest', '2027-07-24 11:30:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000007', '22222222-2222-2222-2222-222222222222', 'Blues Brunch', '2027-07-18 06:30:00+00', '2026-10-25 11:57:14.860599+00', NULL);
INSERT INTO public.event (id, venue_id, name, starts_at, created_at, cancelled_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Classical Evening', '2026-12-05 13:00:00+00', '2026-10-24 13:04:21.939416+00', '2026-10-24 18:05:00.690845+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General', 1499.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP', 4999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General', 999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General', 1999.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499.0000, '2026-10-24 13:04:21.940448+00');
INSERT INTO public.price_tier (id, event_id, name, amount, created_at) VALUES ('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000001', 'Balcony', 2499.0000, '2026-10-24 21:30:49.481755+00');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000001', 'attendee');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000002', 'organiser');
INSERT INTO public.role (id, name) VALUES ('dddddddd-0000-0000-0000-000000000003', 'admin');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000002');
INSERT INTO public.user_role (user_id, role_id) VALUES ('cccccccc-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001');
```

Load it. The `-i` lets Docker pass the file's content into `psql`:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context
docker exec -i pg psql -U postgres -d zenith < seed-dev-data.sql
```

Expected: `INSERT 0 1`, printed 24 times. Run the count again. It now says `7`. If you
load the file a second time by mistake, each line fails with
`duplicate key value violates unique constraint`, and nothing changes.

**Step 2 — two new files** in `scratch-app/src/main/java/com/zenith/data/`. Create
`EventRepository.java` and `FindAllDemo.java` with exactly the content shown in "Example"
above.

**Step 3 — run it with the profile.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all
```

Success includes these lines:

```text
The following 1 profile is active: "find-all"
Finished Spring Data repository scanning in ... ms. Found 1 JPA repository interface.
Started DataApplication in ... seconds
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0
>>> findAll() returned 7 events:
```

Then come 7 lines that start with `>>>`. Their order may differ from the order shown in
"Walkthrough". That is expected, because there is no `ORDER BY`.

Failures you might see:

- `Found 0 JPA repository interfaces` and then `APPLICATION FAILED TO START ... required a
  bean of type ... that could not be found`. The `package` line of `EventRepository.java`
  is wrong, or the file is in a different folder.
- No `>>>` lines and no `select` at all. The profile flag is missing or misspelled. Check
  the line `The following 1 profile is active: ...`.
- `>>> findAll() returned 0 events:` — the seed data is not loaded. Do Step 1.

**Step 4 — break it and read it.** Create
`src/main/java/com/zenith/data/EventRepositoryByHand.java` with the content from
"Common mistakes" item 1. Run `mvn -q compile`. Find
`is not abstract and does not override abstract method`. Then delete the file and run
`mvn -q compile` again. It succeeds and prints nothing.

**Done when:**

- your log shows `Found 1 JPA repository interface.` and the `select ... from event ee1_0`
  statement;
- you see `>>> findAll() returned 7 events:` followed by 7 event lines;
- you have seen the `is not abstract` error on your own screen, and you can say why it
  appears.

### Summary for revision

- A repository interface is one declaration with an empty body:
  `interface EventRepository extends JpaRepository<EventEntity, UUID> {}`.
- The two blanks are the entity, and then the type of its `@Id` field.
- You inherit 30 methods. `findAll()` returns `List<EventEntity>`. `findById(id)` returns
  `Optional<EventEntity>`.
- Spring Data searches the main class's package and every package inside it.
  `Found N JPA repository interfaces` reports what it found.
- Your class gets the repository through its constructor. Spring passes in the bean.
- `findAll()` sends one `select` of the mapped columns, with no `WHERE` and no `ORDER BY`.
- Never implement the interface by hand: that means 30 method bodies. A wrong second blank
  fails only when an id is passed. A repository outside the searched packages gives
  `required a bean ... that could not be found`.

### Practice questions, with answers

**Question 1.** A Spring Boot 3 app has its main class in `com.zenith.data`. Its database
has this table, with 3 rows in it:

```sql
create table venue (
    id              uuid primary key,
    name            text not null,
    city            text not null,
    capacity        integer not null,
    seat_map_hints  jsonb,
    created_at      timestamptz not null default now()
);
```

The app has these two files in `com.zenith.data`:

```java
@Entity
@Table(name = "venue")
public class VenueEntity {
    @Id
    private UUID id;
    private String name;
    private String city;

    protected VenueEntity() {}
}
```

```java
public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {
}
```

A `CommandLineRunner` calls `venues.findAll()`, where `venues` is a `VenueRepository`.

- (a) What type does `findAll()` return here?
- (b) Which columns does the `select` read, and which columns does it not read? Why?
- (c) How many objects are in the result?

**Answer.** This exact code was run against the real `venue` table.

- (a) `List<VenueEntity>`. The first blank, `T`, was filled with `VenueEntity`, so
  `List<T> findAll()` became `List<VenueEntity> findAll()`.
- (b) The real SQL was:

  ```text
      select
          ve1_0.id,
          ve1_0.city,
          ve1_0.name 
      from
          venue ve1_0
  ```

  It reads `id`, `city` and `name`, because each has a field in `VenueEntity`. It does not
  read `capacity`, `seat_map_hints` or `created_at`, because no field stands for them.
  Unmapped columns are ignored. The alias `ve1_0` comes from the capitals of `VenueEntity`.
- (c) 3, one object per row. The real run printed `findAll() returned 3 venues`.

**Question 2.** A teammate writes this entity and repository:

```java
@Entity
@Table(name = "price_tier")
public class PriceTierEntity {
    @Id
    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal amount;

    protected PriceTierEntity() {}
}
```

```java
public interface PriceTierRepository extends JpaRepository<PriceTierEntity, Long> {
}
```

- (a) Does the app start? Does `findAll()` work? What happens when code calls
  `findById(5L)`?
- (b) Why did startup not catch the mistake?
- (c) What is the fix, and what does the compiler do with `findById(5L)` after the fix?

**Answer.**

- (a) The app starts, and `findAll()` works. `findById(5L)` throws an error. The real run
  with `EventEntity` gave this message, and `PriceTierEntity` gives the same one with its
  own name:
  `InvalidDataAccessApiUsageException: Supplied id had wrong type: entity '...PriceTierEntity' has id type 'class java.util.UUID' but supplied id was of type 'class java.lang.Long'`.
- (b) At startup, Spring Data does not compare the second blank with the type of the `@Id`
  field. `findAll()` never uses an id, so it works. The check happens only when an id is
  passed in: Hibernate compares that id's type with the `@Id` type, and they differ.
- (c) Change `Long` to `UUID`:
  `public interface PriceTierRepository extends JpaRepository<PriceTierEntity, UUID> {}`.
  After the fix, `findById(5L)` does not even compile. The real compiler error is
  `incompatible types: long cannot be converted to java.util.UUID`. The mistake is now
  caught before the app runs.

**Question 3.** An app's main class is `com.zenith.data.DataApplication`. A teammate puts
`EventRepository` in the package `com.zenith.persistence`. Startup fails:

```text
Finished Spring Data repository scanning in 4 ms. Found 0 JPA repository interfaces.
...
APPLICATION FAILED TO START
...
Parameter 0 of constructor in com.zenith.data.FindAllDemo required a bean of type 'com.zenith.persistence.EventRepository' that could not be found.
...
Consider defining a bean of type 'com.zenith.persistence.EventRepository' in your configuration.
```

The teammate follows the hint and starts writing
`class EventRepositoryImpl implements EventRepository`.

- (a) Why did Spring Data not find the interface?
- (b) Why is the teammate's plan wrong?
- (c) What is the right fix? Would the package `com.zenith.data.persistence` work?

**Answer.**

- (a) Spring Data searches only the main class's package, `com.zenith.data`, and the
  packages inside it. `com.zenith.persistence` sits beside `com.zenith.data`, not inside
  it. So the search found nothing: `Found 0 JPA repository interfaces`.
- (b) A class that implements `EventRepository` must write a body for all 30 inherited
  methods. The compiler refuses it until every body exists
  (`is not abstract and does not override abstract method ...`). That throws away the whole
  reason for using Spring Data. The "Consider defining a bean" line is a general hint. The
  real cause is that the interface was never found.
- (c) Move `EventRepository` into `com.zenith.data`, and change its `package` line to
  match. Then the log says `Found 1 JPA repository interface.` Yes,
  `com.zenith.data.persistence` also works, because that package is inside
  `com.zenith.data`.

---

## Lesson 3 — Where the implementation comes from: a proxy, made while the app starts (finishes subtask S1, answers CP1)

### Definition

Four words first:

- Every Java object knows the class it was made from. **`getClass()`** returns that class.
  `getClass().getName()` gives its full name as text.
- A **proxy** is a stand-in object. It has the same methods as the interface you asked for.
  When you call one of them, it can do some extra work, and then it passes the call on to
  another object. The task's word list calls it a "wrapper". It is the same thing.
- The **target** is that other object: the real object behind the proxy, which does the
  actual job.
- A **handler** is the one method inside a proxy that every call lands in.

`EventRepository` has an empty body, and no class implements it. Yet `findAll()` works.
When the app starts, Spring Data makes two objects for `EventRepository`:

1. **The target**: an object of `SimpleJpaRepository`. This is a normal class that the
   Spring Data team wrote once, with real code for all the inherited methods. It lives
   inside the Spring Data jar, not in your project.
2. **The proxy**: an object of a class that Java creates in memory while the app starts.
   That class implements `EventRepository`. Every call on it goes to the handler, then
   through a few extra steps, then to the target.

Spring passes the **proxy** into your constructor. That is why `events.getClass()` prints a
name you never wrote: `jdk.proxy2.$Proxy103`.

### Why it is needed

**Problem 1: Java cannot make an object from an interface.** The real compiler error for
`new EventRepository()`:

```text
NewInterfaceCheck.java:[5,16] com.zenith.data.EventRepository is abstract; cannot be instantiated
```

An object must come from a class, and no class in your project implements
`EventRepository`.

**Problem 2: the class with the real code does not know your interface.**
`SimpleJpaRepository` was written long before your `EventRepository` existed. Its class
line cannot say `implements EventRepository`. So Spring cannot pass a
`SimpleJpaRepository` where an `EventRepository` is needed.

Something must stand in between: an object whose class **does** implement
`EventRepository`, made for your interface when the app starts. That is the proxy.

**A bonus.** Every call goes through the proxy, so Spring can add extra steps to every
repository call without touching your code. One step opens a **transaction**: a group of
SQL statements that the database treats as one unit, where either all are kept or none
are. Later you will add methods such as `findByVenueId`, which `SimpleJpaRepository` has
never heard of. The proxy is also the part that runs those.

### Key points

- The object Spring passes in for `EventRepository` is a proxy. Its class,
  `jdk.proxy2.$Proxy103`, was made by Java in memory at startup.
- Behind the proxy sits the target, a `SimpleJpaRepository`. That is where the real code
  is.
- The proxy implements your interface plus four of Spring's own.
- Every call travels: your code, the proxy, the handler, the extra steps (error
  translation, transaction, query check), the target, then Hibernate.
- No `.java` or `.class` file exists for the proxy class. There is nothing to open in the
  IDE.
- `getClass()` tells the truth. `println(events)` does not, because the proxy passes
  `toString()` on to the target.

### How it works, step by step

**Part A — Java's built-in proxy feature.** Java has a class `java.lang.reflect.Proxy`.
Its method `Proxy.newProxyInstance(...)` takes three things:

1. a **class loader**: the part of Java that loads classes into memory. You pass the
   interface's own one;
2. the list of **interfaces** the new class must implement;
3. the **handler**. For every call, it receives the proxy itself, which method was called,
   and the arguments. Whatever it returns becomes the result of the call.

Java then writes a new class in memory, named like `$Proxy0`, that implements those
interfaces. Each of its methods does one thing: call the handler. This is a
**JDK dynamic proxy**. "JDK" because it is built into Java. "Dynamic" because the class is
made while the program runs.

**Part B — what Spring Data does at startup, for each repository interface:**

1. It finds the interface. The log says `Found 1 JPA repository interface`.
2. It reads the two blanks: `EventEntity` and `UUID`.
3. It creates the target: a `SimpleJpaRepository` set up for `EventEntity`.
4. It calls Java's `Proxy.newProxyInstance(...)`. The interfaces are `EventRepository`
   plus four of Spring's own. The handler is a Spring class called `JdkDynamicAopProxy`.
   Java creates the class `jdk.proxy2.$Proxy103` in memory.
5. It keeps the proxy as the bean. Every constructor that asks for an `EventRepository`
   gets the proxy.

**Part C — what happens on each call**, for example `events.findById(id)`:

1. Your code calls the proxy.
2. The proxy calls the handler.
3. The handler runs extra steps, in order:
   - **Error translation** turns Hibernate's errors into Spring's errors.
   - **Transaction** starts a transaction around the call.
   - **Query check** asks: "Is this a method declared in `EventRepository` itself?" If
     not, it passes the call on.
4. The last step calls the same method on the target: `SimpleJpaRepository.findById`.
5. The target asks Hibernate to run the SQL. The result travels back the same way.

### Example

**Example A — a proxy in plain Java, with no Spring at all.** File `plain-java/ProxyDemo.java`
in the task folder, next to `scratch-app`:

```java
import java.lang.reflect.Proxy;

// An object for an interface that no class implements - made while the program runs.
// Run with: java ProxyDemo.java
public class ProxyDemo {

    public interface Greeter {
        String hello(String name);
        String bye(String name);
    }

    public static void main(String[] args) {
        // No class anywhere implements Greeter. We ask Java to make one, right now.
        Greeter greeter = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[] { Greeter.class },
                (proxy, method, methodArgs) -> {              // the handler: every call lands here
                    System.out.println("  handler got: " + method.getName() + "(" + methodArgs[0] + ")");
                    return method.getName() + ", " + methodArgs[0] + "!";
                });

        System.out.println(greeter.hello("Meera"));
        System.out.println(greeter.bye("Arjun"));
        System.out.println("class of greeter: " + greeter.getClass().getName());
    }
}
```

- `(proxy, method, methodArgs) -> { ... }` is a **lambda**: a short way to write one method
  without naming a class. This lambda is the handler.
- `new Class<?>[] { Greeter.class }` is the list of interfaces. Here it has one.
- `newProxyInstance` returns a plain `Object`. `(Greeter)` tells Java to treat it as a
  `Greeter`.

Run it with `java ProxyDemo.java`. Java can run a single source file directly. Real
output:

```text
  handler got: hello(Meera)
hello, Meera!
  handler got: bye(Arjun)
bye, Arjun!
class of greeter: jdk.proxy1.$Proxy0
```

**Example B — look at the object Spring passed in.**
`scratch-app/src/main/java/com/zenith/data/GetClassDemo.java`:

```java
package com.zenith.data;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

// Lesson 3: what object did Spring pass in for the EventRepository interface?
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=get-class
@Component
@Profile("get-class")
class GetClassDemo implements CommandLineRunner {

    private final EventRepository events;

    GetClassDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        Class<?> type = events.getClass();
        System.out.println(">>> class of the object     : " + type.getName());
        System.out.println(">>> made by Java's Proxy?   : " + Proxy.isProxyClass(type));
        System.out.println(">>> is a SimpleJpaRepository: " + (events instanceof SimpleJpaRepository));
        System.out.println(">>> target behind it        : " + AopProxyUtils.ultimateTargetClass(events).getName());
        System.out.println(">>> interfaces it implements:");
        for (Class<?> each : type.getInterfaces()) {
            System.out.println(">>>     " + each.getName());
        }
        System.out.println(">>> count() through it      : " + events.count());
        System.out.println(">>> println(events) shows   : " + events);
    }
}
```

- `Proxy.isProxyClass(type)` asks Java: "Was this class made by `newProxyInstance`?"
- `AopProxyUtils.ultimateTargetClass(events)` is a Spring helper. It looks through a proxy
  and returns the class of its target. **AOP** is Spring's name for adding extra steps
  around method calls.
- `type.getInterfaces()` lists the interfaces the class implements.

Command: `mvn -q spring-boot:run -Dspring-boot.run.profiles=get-class`. Real output:

```text
>>> class of the object     : jdk.proxy2.$Proxy103
>>> made by Java's Proxy?   : true
>>> is a SimpleJpaRepository: false
>>> target behind it        : org.springframework.data.jpa.repository.support.SimpleJpaRepository
>>> interfaces it implements:
>>>     com.zenith.data.EventRepository
>>>     org.springframework.data.repository.Repository
>>>     org.springframework.transaction.interceptor.TransactionalProxy
>>>     org.springframework.aop.framework.Advised
>>>     org.springframework.core.DecoratingProxy
Hibernate: 
    select
        count(*) 
    from
        event ee1_0
>>> count() through it      : 7
>>> println(events) shows   : org.springframework.data.jpa.repository.support.SimpleJpaRepository@68e62b3b
```

**Example C — follow one call.** The target refuses a `null` id, so this demo crashes on
purpose. The stack trace it leaves shows the whole path of the call.
`scratch-app/src/main/java/com/zenith/data/CallPathDemo.java`:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 3: follow one call from your code, through the proxy, to the target.
// The target refuses a null id, so the app stops with a stack trace that shows the whole path.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=call-path
@Component
@Profile("call-path")
class CallPathDemo implements CommandLineRunner {

    private final EventRepository events;

    CallPathDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> calling findById(null) on purpose - read the stack trace");
        events.findById(null);
    }
}
```

Command: `mvn -q spring-boot:run -Dspring-boot.run.profiles=call-path`. Real output,
trimmed to the lines that matter, with the `~[...jar]` endings removed:

```text
>>> calling findById(null) on purpose - read the stack trace
org.springframework.dao.InvalidDataAccessApiUsageException: The given id must not be null
	...
	at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
	...
	at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
	at jdk.proxy2/jdk.proxy2.$Proxy103.findById(Unknown Source)
	at com.zenith.data.CallPathDemo.run(CallPathDemo.java:23)
	...
Caused by: java.lang.IllegalArgumentException: The given id must not be null
	at org.springframework.util.Assert.notNull(Assert.java:181)
	at org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById(SimpleJpaRepository.java:323)
	...
	at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:149)
	...
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
	...
	at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:138)
	... 26 common frames omitted
```

### Walkthrough

**Example A, step by step:**

- No class in the file says `implements Greeter`. Still, `greeter.hello("Meera")` works.
- `handler got: hello(Meera)` — the call landed in the handler. `method.getName()` was
  `"hello"`, and `methodArgs[0]` was `"Meera"`.
- `hello, Meera!` — the handler's return value became the result of `greeter.hello(...)`.
- `bye` lands in the same handler. One handler serves every method.
- `jdk.proxy1.$Proxy0` — the class Java wrote while the program ran. `$Proxy0` means it is
  the first proxy class made in that run. No file holds it. It exists only in memory.

**Example B, line by line:**

| Output | What it tells you |
| --- | --- |
| `jdk.proxy2.$Proxy103` | The same pattern as `jdk.proxy1.$Proxy0` in Example A: a class made in memory by Java's `Proxy`. The number is only a counter of proxy classes made in this run, so it may differ on another laptop. |
| `made by Java's Proxy? : true` | Java confirms that this class came from `newProxyInstance`. |
| `is a SimpleJpaRepository: false` | The object you got is **not** the target. It is the proxy. |
| `target behind it : ...SimpleJpaRepository` | Behind the proxy sits the real code. |
| `com.zenith.data.EventRepository` | Your interface. That is why Spring can pass this object to a constructor that asks for `EventRepository`. |
| The other four interfaces | Spring's own labels. `Repository` means "a Spring Data repository". `TransactionalProxy` means "this proxy handles transactions". `Advised` and `DecoratingProxy` let Spring look inside the proxy, which is how `AopProxyUtils` found the target. |
| `select count(*) ...` and `7` | A call through the proxy reached the target, and the target asked Hibernate to run the SQL. |
| `SimpleJpaRepository@68e62b3b` | A **trap**. `println` calls `toString()`. The proxy passes `toString()` on to the target, so the target answers with its own name. `@68e62b3b` is a number that marks that one object. `getClass()` cannot be passed on, because Java lets no class change it. So `getClass()` tells the truth. |

**Example C — how to read a stack trace.** A **stack trace** is the list of method calls
that were still running when an error was thrown. The newest call is at the top. Each line
below it is the method that called the line above. So read **from the bottom up** to see
the calls in the order they happened.

This trace has two parts:

- The target threw the first error, an `IllegalArgumentException`.
- On the way back, the error-translation step caught it. It swapped in Spring's own error,
  `InvalidDataAccessApiUsageException`, and kept the original inside as its "cause".
- That is why Spring's error is printed first, and the original is printed under
  `Caused by:`.
- `... 26 common frames omitted` means the rest of the second part is the same as the first
  part, so Java does not print it twice.

The path of the call, in the order it happened:

| # | Line in the trace | What it is |
| --- | --- | --- |
| 1 | `CallPathDemo.run(CallPathDemo.java:23)` | Your code. Line 23 is `events.findById(null)`. |
| 2 | `$Proxy103.findById(Unknown Source)` | The call lands on the proxy. `Unknown Source` means no source file exists, because Java wrote this class in memory. |
| 3 | `JdkDynamicAopProxy.invoke` | The proxy hands the call to Spring's handler. |
| 4 | `PersistenceExceptionTranslationInterceptor.invoke` | Extra step: error translation. |
| 5 | `TransactionInterceptor.invoke` | Extra step: a transaction around this one call. |
| 6 | `QueryExecutorMethodInterceptor.invoke` | Extra step: "Is `findById` declared in `EventRepository`?" It is not, so the call is passed on. |
| 7 | `SimpleJpaRepository.findById(SimpleJpaRepository.java:323)` | The target: real code, at line 323 of a real file. |
| 8 | `Assert.notNull(Assert.java:181)` | The target's first check is that the id must not be null. The error is thrown here. |

**Proof on disk.** `ls target/classes/com/zenith/data/` lists only your own classes:

```text
CallPathDemo.class
DataApplication.class
EventEntity.class
EventRepository.class
FindAllDemo.class
GetClassDemo.class
```

`EventRepository.class` is the compiled interface itself, still with no method bodies.
`find . -iname '*proxy*'` (run inside `scratch-app`) prints nothing. The target, though,
is a real file inside the Spring Data jar. `unzip -l` on that jar, filtered with `grep`,
shows:

```text
    51770  06-24-2026 07:50   org/springframework/data/jpa/repository/support/SimpleJpaRepository.class
```

### Important terms

| Term | Meaning |
| --- | --- |
| **`getClass()`** | Returns the class an object was made from. It cannot be passed on by a proxy, so it always tells the truth. |
| **Proxy** | A stand-in object with the same methods as an interface. It can do extra work, then passes each call on. The task also calls it a wrapper. |
| **Target** | The real object behind a proxy. For a repository, it is a `SimpleJpaRepository`. |
| **Handler** | The one method inside a proxy that every call lands in. |
| **JDK dynamic proxy** | A proxy whose class Java itself makes while the program runs, through `Proxy.newProxyInstance`. |
| **`Proxy.newProxyInstance`** | Java's method that makes the class and one object of it. It takes a class loader, a list of interfaces and a handler. |
| **Class loader** | The part of Java that loads classes into memory. |
| **Lambda** | A short way to write one method without naming a class, such as `(a, b) -> { ... }`. |
| **`SimpleJpaRepository`** | Spring Data's ready-made class with real code for the inherited repository methods. It lives in the Spring Data jar. |
| **`JdkDynamicAopProxy`** | Spring's handler for its JDK dynamic proxies. |
| **AOP** | Spring's name for adding extra steps around method calls. |
| **`AopProxyUtils.ultimateTargetClass`** | A Spring helper that looks through a proxy and returns the class of its target. |
| **Transaction** | A group of SQL statements the database treats as one unit: all are kept, or none are. |
| **Stack trace** | The list of method calls still running when an error was thrown. The newest call is at the top. |
| **`Caused by:`** | The part of a stack trace that shows the original error, kept inside a newer one. |
| **`Unknown Source`** | A line of a stack trace whose class has no source file, such as a class made in memory. |

### Where it is used

- **Every Spring Data repository** in every Spring Boot app works this way: one proxy per
  repository interface, with one `SimpleJpaRepository` target behind it.
- **`@Transactional` on your own classes**, later in this task. Spring puts a proxy around
  your service too. Calls from outside go through the proxy, which starts a transaction,
  then passes the call to your real object, the target. The `TransactionInterceptor` line
  in the trace is that same machinery. It already runs inside every repository call.
- **Other Spring annotations** use the same trick. Two examples are `@Cacheable` (remember
  results) and `@Async` (run in the background).

### Edge cases

- **The `$Proxy` number is only a counter.** It depends on how many proxy classes were made
  before it in that run. It can change when the app changes, and it can differ on another
  laptop. Never write code that checks it. Use `Proxy.isProxyClass(...)` or
  `AopProxyUtils.ultimateTargetClass(...)` instead.
- **`toString()` goes to the target, `getClass()` does not.** That is why
  `println(events)` and `events.getClass()` disagree.
- **`events instanceof EventRepository` is true, `events instanceof SimpleJpaRepository`
  is false.** The proxy implements interfaces only. It is not a `SimpleJpaRepository`.
- **Even a plain read runs inside a transaction.** When no transaction is already running,
  the proxy opens a short one around that single call and ends it when the call returns.
  That is the `TransactionInterceptor` line in the trace.
- **`java ProxyDemo.java` writes no `.class` file.** Java compiles the file in memory and
  runs it. The `$Proxy0` class is made in memory too.

### Common mistakes — with the real errors

1. **Looking for the implementation in your project.** People search the IDE for an
   `EventRepositoryImpl`, or step into `findAll()` hoping to see code they wrote. There is
   nothing to find in your project. The code is in `SimpleJpaRepository`, inside the Spring
   Data jar.
2. **Trusting `println(events)`.** It prints `SimpleJpaRepository@68e62b3b`, which makes
   the object look like a `SimpleJpaRepository`. It is not. Use `getClass()`.
3. **Casting the repository to `SimpleJpaRepository`.** A temporary demo ran
   `(SimpleJpaRepository<EventEntity, UUID>) events`. It was deleted afterwards. The real
   error:

   ```text
   java.lang.ClassCastException: class jdk.proxy2.$Proxy103 cannot be cast to class org.springframework.data.jpa.repository.support.SimpleJpaRepository (jdk.proxy2.$Proxy103 is in module jdk.proxy2 of loader 'app'; org.springframework.data.jpa.repository.support.SimpleJpaRepository is in unnamed module of loader 'app')
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `class jdk.proxy2.$Proxy103` | The class of the object you have, which is the proxy. |
   | `cannot be cast to class ...SimpleJpaRepository` | The proxy only implements interfaces. It is not a `SimpleJpaRepository`. |
   | `(... in module ... of loader 'app' ...)` | Where Java loaded each class from. You can ignore it here. |

   Fix: always use the repository through your own interface, `EventRepository`.

### Link to the task

- **S1, second half:** "print `repository.getClass()`. The name is not yours." It printed
  `jdk.proxy2.$Proxy103`. With this, all of S1 is done: an empty interface, a `findAll()`
  call, and `getClass()` printed.
- **CP1:** "You wrote an interface with no implementation and it worked. Where did the
  implementation come from?" A full answer:
  - At startup, Spring Data found `EventRepository` and built two objects.
  - The target is a `SimpleJpaRepository`, Spring Data's ready-made class with real code
    for the inherited methods.
  - The proxy belongs to a class that Java generated in memory, `jdk.proxy2.$Proxy103`,
    which implements `EventRepository`. Spring injected the proxy.
  - Each call goes through the proxy's handler and some extra steps, such as a transaction
    and error translation, and then to the target. The target uses Hibernate to run the
    SQL.
  - No file was ever generated, so there is nothing to open in the IDE.
- **Later:** subtasks S10 and S11, the self-invocation trap, depend on this same proxy idea.

### Do it yourself (second laptop)

**Starting point.** The scratch app with `EventRepository.java` and `FindAllDemo.java`,
where `mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all` prints
`>>> findAll() returned 7 events:`. The repository lesson of these notes sets this up.

**Step 1 — a proxy in plain Java.**

```bash
mkdir -p ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/plain-java
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/plain-java
```

Create `ProxyDemo.java` with the content from Example A. Run it:

```bash
java ProxyDemo.java
```

Expected: the five lines from Example A, ending with
`class of greeter: jdk.proxy1.$Proxy0`. If `java -version` does not say `21`, redo the
Java install in the setup steps of the `EventEntity` lesson.

**Step 2 — what Spring passed in.** Create
`scratch-app/src/main/java/com/zenith/data/GetClassDemo.java` with the content from
Example B. Run:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=get-class
```

Expected: the `>>>` lines from Example B. The `$Proxy` number may differ, and the
`@68e62b3b` part will differ.

**Step 3 — follow one call.** Create `CallPathDemo.java` next to it, with the content from
Example C. The full trace is long, so filter it:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=call-path | grep -E '>>>|Caused by|CallPathDemo\.run|\$Proxy|JdkDynamicAopProxy\.invoke|TransactionInterceptor\.invoke|SimpleJpaRepository\.findById'
```

Real filtered output:

```text
>>> calling findById(null) on purpose - read the stack trace
	at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223) ~[spring-aop-6.2.19.jar:6.2.19]
	at jdk.proxy2/jdk.proxy2.$Proxy103.findById(Unknown Source) ~[na:na]
	at com.zenith.data.CallPathDemo.run(CallPathDemo.java:23) ~[classes/:na]
Caused by: java.lang.IllegalArgumentException: The given id must not be null
	at org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById(SimpleJpaRepository.java:323) ~[spring-data-jpa-3.5.13.jar:3.5.13]
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119) ~[spring-tx-6.2.19.jar:6.2.19]
```

The app stops with a failure here. That is expected. If you see only the `>>>` line, the run
stopped earlier. Run the command again without `| grep ...` and read the first `ERROR`.

**Step 4 — look on disk.**

```bash
ls target/classes/com/zenith/data/
find . -iname '*proxy*'
unzip -l ~/.m2/repository/org/springframework/data/spring-data-jpa/3.5.13/spring-data-jpa-3.5.13.jar | grep 'support/SimpleJpaRepository.class'
```

Expected:

- `ls` lists only your own `.class` files.
- `find` prints nothing.
- `unzip` prints the `51770 ... SimpleJpaRepository.class` line.

If `unzip` says `cannot find or open`, Maven has not downloaded the jar yet. Run Step 2
first.

**Done when:**

- `ProxyDemo` printed a `jdk.proxy1.$Proxy...` name;
- `get-class` printed a `jdk.proxy2.$Proxy...` name, `true`, `false` and
  `SimpleJpaRepository`;
- you found `$Proxy...findById(Unknown Source)` and `SimpleJpaRepository.findById` in the
  trace;
- you can answer CP1 out loud, in your own words.

### Summary for revision

- For each repository interface, Spring Data builds a **target** (`SimpleJpaRepository`,
  with the real code) and a **proxy** (a class made in memory that implements your
  interface).
- Spring injects the proxy. `getClass()` shows `jdk.proxy2.$ProxyNNN`, a name you never
  wrote.
- Java makes the proxy class with `Proxy.newProxyInstance(loader, interfaces, handler)`.
  Every call lands in the handler.
- One call travels: your code, the proxy, the handler, the extra steps (error translation,
  transaction, query check), `SimpleJpaRepository`, then Hibernate.
- In a stack trace, read from the bottom up. `Unknown Source` marks the class made in
  memory. `Caused by:` is the original error.
- No file exists for the proxy, so there is nothing to open in the IDE. `println` lies, and
  `getClass()` tells the truth. Never cast to `SimpleJpaRepository`.

### Practice questions, with answers

**Question 1.** Here is a complete Java program. No class in it says `implements Pricing`.

```java
import java.lang.reflect.Proxy;

public class Quiz {

    public interface Pricing {
        int priceFor(String tier);
    }

    public static void main(String[] args) {
        Pricing pricing = (Pricing) Proxy.newProxyInstance(
                Pricing.class.getClassLoader(),
                new Class<?>[] { Pricing.class },
                (proxy, method, methodArgs) -> {
                    System.out.println("handler: " + method.getName() + "(" + methodArgs[0] + ")");
                    return methodArgs[0].equals("VIP") ? 4999 : 1499;
                });

        System.out.println(pricing.priceFor("VIP"));
        System.out.println(pricing.priceFor("General"));
        System.out.println(pricing.getClass().getName());
    }
}
```

- (a) Write down exactly what it prints, line by line.
- (b) What kind of object is `pricing`, and where did its class come from?
- (c) Could you find a `.java` or `.class` file for that class anywhere on disk? Why or why
  not?

**Answer.** This exact program was run with `java Quiz.java`.

- (a) The real output:

  ```text
  handler: priceFor(VIP)
  4999
  handler: priceFor(General)
  1499
  jdk.proxy1.$Proxy0
  ```

  Each call first lands in the handler, which prints the `handler:` line. Then the
  handler's return value becomes the result, which `main` prints. `"VIP"` gives `4999`,
  and anything else gives `1499`.
- (b) `pricing` is a proxy: a JDK dynamic proxy. Java made its class in memory, at the
  moment `Proxy.newProxyInstance(...)` ran. The class implements `Pricing`, and each of its
  methods hands the call to the handler, which is the lambda.
- (c) No. The class was written by Java into memory while the program ran. It never existed
  as a `.java` file, and it is never saved as a `.class` file. That is why a stack trace
  shows such a class as `Unknown Source`.

**Question 2.** In a Spring Boot app, `events` is an `EventRepository` that Spring passed
into a constructor. `EventRepository` is
`interface EventRepository extends JpaRepository<EventEntity, UUID> {}`, with an empty
body. A teammate runs:

```java
System.out.println(events);             // printed: org.springframework.data.jpa.repository.support.SimpleJpaRepository@68e62b3b
System.out.println(events.getClass());  // printed: class jdk.proxy2.$Proxy103
SimpleJpaRepository<EventEntity, UUID> direct = (SimpleJpaRepository<EventEntity, UUID>) events;
```

The teammate says: "The first line proves it is a `SimpleJpaRepository`, so the cast is
safe."

- (a) What happens on the third line?
- (b) Why do the first two lines show different class names?
- (c) In your own words: where did the code that runs `events.findAll()` come from?

**Answer.**

- (a) The cast fails while the app runs. The real error was:
  `java.lang.ClassCastException: class jdk.proxy2.$Proxy103 cannot be cast to class org.springframework.data.jpa.repository.support.SimpleJpaRepository (...)`.
  The object is the proxy, and the proxy only implements interfaces. It is not a
  `SimpleJpaRepository`.
- (b) `println(events)` calls `toString()`. The proxy passes `toString()` on to its
  target, the `SimpleJpaRepository`, so the target prints its own class name and number.
  `getClass()` cannot be passed on, because Java lets no class change it. So it returns the
  proxy's real class, `jdk.proxy2.$Proxy103`.
- (c) At startup, Spring Data built two objects for `EventRepository`. The target is a
  `SimpleJpaRepository`, whose real code for `findAll()` lives in the Spring Data jar. The
  proxy belongs to a class Java made in memory, and it implements `EventRepository`. Spring
  injected the proxy. A call to `findAll()` goes through the proxy's handler and some extra
  steps, such as a transaction, and then to the target's `findAll()`. That code asks
  Hibernate to run the `select`.

**Question 3.** A repository call stopped an app with this stack trace (real, trimmed, with
the `~[...jar]` endings removed):

```text
org.springframework.dao.InvalidDataAccessApiUsageException: The given id must not be null
	...
	at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:160)
	...
	at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
	at jdk.proxy2/jdk.proxy2.$Proxy103.findById(Unknown Source)
	at com.zenith.data.CallPathDemo.run(CallPathDemo.java:23)
	...
Caused by: java.lang.IllegalArgumentException: The given id must not be null
	at org.springframework.util.Assert.notNull(Assert.java:181)
	at org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById(SimpleJpaRepository.java:323)
	...
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
	...
```

- (a) Which line is your own code, and which line is the proxy? How can you tell that the
  proxy's class has no source file?
- (b) Which line is the target?
- (c) Two error types appear. Which one was thrown first, and why is the other one printed
  at the top?
- (d) Which line shows that a transaction was started around this one call?

**Answer.**

- (a) Your code is `com.zenith.data.CallPathDemo.run(CallPathDemo.java:23)`: a real file
  and a real line number. The proxy is `jdk.proxy2.$Proxy103.findById(Unknown Source)`.
  `Unknown Source` shows there is no source file, because Java made the class in memory
  at startup.
- (b) `SimpleJpaRepository.findById(SimpleJpaRepository.java:323)`. It is Spring Data's
  real code, the target behind the proxy.
- (c) `IllegalArgumentException` was thrown first, by `Assert.notNull` inside the target.
  On the way back, the error-translation step (`PersistenceExceptionTranslationInterceptor`)
  caught it. It wrapped it in Spring's own `InvalidDataAccessApiUsageException` and kept
  the original inside as the cause. So Spring's error is printed at the top, and the
  original is printed under `Caused by:`.
- (d) `TransactionInterceptor.invoke(TransactionInterceptor.java:119)`. It is one of the
  extra steps the proxy runs before it reaches the target.

---

## Lesson 4 — The repository family: what each interface adds (prepares subtask S2)

### Definition

Two ideas first:

- When an interface **extends** another interface, it gets all of that interface's methods
  and can add its own. An interface may extend **several** interfaces at once. A class can
  extend only one class, but interfaces have no such limit.
- **Spring Data** comes in parts. `spring-data-commons` is the shared part, used by every
  kind of database Spring Data supports: JPA, MongoDB, JDBC, Redis and more.
  `spring-data-jpa` is the part only for JPA.

The **repository family** is the group of interfaces that `JpaRepository` is built from.
Each member adds a few methods. `EventRepository` is
`interface EventRepository extends JpaRepository<EventEntity, UUID> {}`, and `JpaRepository`
sits at the bottom of the family. It extends three members, so it has everything:

```text
Repository<T, ID>                              adds 0   - only a label
├── CrudRepository<T, ID>                      adds 12  - save, findById, existsById, findAll, count, delete...
│   └── ListCrudRepository<T, ID>              adds 3   - findAll, findAllById, saveAll, now returning List
└── PagingAndSortingRepository<T, ID>          adds 2   - findAll(Sort), findAll(Pageable)
    └── ListPagingAndSortingRepository<T, ID>  adds 1   - findAll(Sort), now returning List
QueryByExampleExecutor<T>                      adds 7   - find rows that look like a sample object

JpaRepository<T, ID> extends ListCrudRepository + ListPagingAndSortingRepository + QueryByExampleExecutor
                     adds 11, plus 1 old one   - flush, saveAndFlush, batch deletes, getReferenceById...
```

All of them live in `spring-data-commons`, except `JpaRepository`, which lives in
`spring-data-jpa`.

### Why it is needed

Subtask S2 says: look at what you inherited, compare `CrudRepository` and `JpaRepository`,
and pick one on purpose. You cannot pick until you know what each one gives you. Three
real problems come from not knowing the family:

- **Old tutorials break.** Spring Boot 2 tutorials often write
  `extends PagingAndSortingRepository`, and then call `save()` and `findById()`. In Spring
  Boot 3, `PagingAndSortingRepository` no longer includes those. On this project, that one
  changed line gave five compile errors (see "Common mistakes").
- **`Iterable` is not `List`.** `CrudRepository.findAll()` gives an `Iterable`. You can
  loop over it, but you cannot ask it for `.size()` or `.get(0)`. That is the whole reason
  `ListCrudRepository` exists.
- **Some methods only exist for JPA.** `flush()` and `saveAndFlush()` are examples. If your
  code needs them, a smaller interface will not have them.

### Key points

- `Repository` is an empty label. `CrudRepository` has the 12 basic actions.
  `PagingAndSortingRepository` has `findAll(Sort)` and `findAll(Pageable)`.
  `QueryByExampleExecutor` has 7 "find by a sample" methods.
- The two `List...` members repeat some methods with `List` instead of `Iterable`.
- In Spring Data 3, `PagingAndSortingRepository` extends **only** `Repository`. It has no
  `save`, no `findById` and no `count`.
- `JpaRepository` extends `ListCrudRepository`, `ListPagingAndSortingRepository` and
  `QueryByExampleExecutor`, and adds JPA-only methods.
- In total, that is 30 different methods with no body, plus one old method with a body.
  `SimpleJpaRepository` supplies the code for all 30.
- `CrudRepository` is shared by every Spring Data database type. `JpaRepository` is for JPA
  only.

### How it works, step by step

1. **Interfaces inherit methods.** `ListCrudRepository<T, ID> extends CrudRepository<T, ID>`
   means that everything `CrudRepository` declares, `ListCrudRepository` has too.
2. **`JpaRepository` joins three branches.** It extends three interfaces, so it collects
   the methods of all three and of their parents. `EventRepository` extends
   `JpaRepository`, so it has all of them.
3. **A `List` version can replace an `Iterable` version.** An `Iterable` is anything you
   can loop over with `for (x : things)`. That is all it promises. A `List` is an
   `Iterable` that also has `size()`, `get(i)` and more. Every `List` is an `Iterable`, but
   not every `Iterable` is a `List`. So `ListCrudRepository` may declare `findAll()` again
   with the return type `List`. It keeps the old promise and adds more.
4. **The compiler adds hidden helpers.** When an interface declares a method again with a
   narrower return type, the compiler adds a "bridge method" by itself. `javap` prints it
   as `public default ...` with no `<...>` anywhere. `javap -v` marks it
   `ACC_BRIDGE, ACC_SYNTHETIC`. It is not a real method that someone wrote.
5. **`SimpleJpaRepository` has the code for every method.** Whatever member a method comes
   from, the proxy passes the call to the same target.

**Names in the method list.** Each gets its own lesson later:

| Name | One-line meaning |
| --- | --- |
| `Sort` | An order, for example "by `startsAt`, earliest first". |
| `Pageable` | Which page, and how many rows on it, for example "page 0, 2 rows". |
| `Page<T>` | One page of rows, plus the total count. |
| `Example<T>` | A sample object: "find rows that look like this one". |
| `<S extends T> S save(S)` | `S` means "`T`, or a subclass of `T`". In plain words: `save` takes an `EventEntity` and gives back an `EventEntity`. `? extends ID` is the same idea. |

**How to read `javap` output.** `javap` comes with Java. It prints what is inside a
compiled `.class` file:

- The `Compiled from "..."` line names the source file.
- The `public interface ... extends ...` line gives the full name, the blanks and the
  parents.
- A `public abstract ...;` line is one method with no body.
- A `public default ...` line **with** `<...>` in it is a method that already has a body.
- A `public default ...` line with **no** `<...>` anywhere is a bridge method. Ignore it.

### Example

**Example A — read the family from the real jars.** `-cp` means classpath: the list of
places where `javap` should look for classes.

```bash
D=~/.m2/repository/org/springframework/data
CP=$D/spring-data-commons/3.5.13/spring-data-commons-3.5.13.jar:$D/spring-data-jpa/3.5.13/spring-data-jpa-3.5.13.jar
javap -cp $CP org.springframework.data.repository.Repository org.springframework.data.repository.CrudRepository org.springframework.data.repository.ListCrudRepository org.springframework.data.repository.PagingAndSortingRepository org.springframework.data.repository.ListPagingAndSortingRepository org.springframework.data.repository.query.QueryByExampleExecutor org.springframework.data.jpa.repository.JpaRepository
```

Real output, all 64 lines:

```text
Compiled from "Repository.java"
public interface org.springframework.data.repository.Repository<T, ID> {
}
Compiled from "CrudRepository.java"
public interface org.springframework.data.repository.CrudRepository<T, ID> extends org.springframework.data.repository.Repository<T, ID> {
  public abstract <S extends T> S save(S);
  public abstract <S extends T> java.lang.Iterable<S> saveAll(java.lang.Iterable<S>);
  public abstract java.util.Optional<T> findById(ID);
  public abstract boolean existsById(ID);
  public abstract java.lang.Iterable<T> findAll();
  public abstract java.lang.Iterable<T> findAllById(java.lang.Iterable<ID>);
  public abstract long count();
  public abstract void deleteById(ID);
  public abstract void delete(T);
  public abstract void deleteAllById(java.lang.Iterable<? extends ID>);
  public abstract void deleteAll(java.lang.Iterable<? extends T>);
  public abstract void deleteAll();
}
Compiled from "ListCrudRepository.java"
public interface org.springframework.data.repository.ListCrudRepository<T, ID> extends org.springframework.data.repository.CrudRepository<T, ID> {
  public abstract <S extends T> java.util.List<S> saveAll(java.lang.Iterable<S>);
  public abstract java.util.List<T> findAll();
  public abstract java.util.List<T> findAllById(java.lang.Iterable<ID>);
  public default java.lang.Iterable findAllById(java.lang.Iterable);
  public default java.lang.Iterable findAll();
  public default java.lang.Iterable saveAll(java.lang.Iterable);
}
Compiled from "PagingAndSortingRepository.java"
public interface org.springframework.data.repository.PagingAndSortingRepository<T, ID> extends org.springframework.data.repository.Repository<T, ID> {
  public abstract java.lang.Iterable<T> findAll(org.springframework.data.domain.Sort);
  public abstract org.springframework.data.domain.Page<T> findAll(org.springframework.data.domain.Pageable);
}
Compiled from "ListPagingAndSortingRepository.java"
public interface org.springframework.data.repository.ListPagingAndSortingRepository<T, ID> extends org.springframework.data.repository.PagingAndSortingRepository<T, ID> {
  public abstract java.util.List<T> findAll(org.springframework.data.domain.Sort);
  public default java.lang.Iterable findAll(org.springframework.data.domain.Sort);
}
Compiled from "QueryByExampleExecutor.java"
public interface org.springframework.data.repository.query.QueryByExampleExecutor<T> {
  public abstract <S extends T> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S>);
  public abstract <S extends T> java.lang.Iterable<S> findAll(org.springframework.data.domain.Example<S>);
  public abstract <S extends T> java.lang.Iterable<S> findAll(org.springframework.data.domain.Example<S>, org.springframework.data.domain.Sort);
  public abstract <S extends T> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S>, org.springframework.data.domain.Pageable);
  public abstract <S extends T> long count(org.springframework.data.domain.Example<S>);
  public abstract <S extends T> boolean exists(org.springframework.data.domain.Example<S>);
  public abstract <S extends T, R> R findBy(org.springframework.data.domain.Example<S>, java.util.function.Function<org.springframework.data.repository.query.FluentQuery$FetchableFluentQuery<S>, R>);
}
Compiled from "JpaRepository.java"
public interface org.springframework.data.jpa.repository.JpaRepository<T, ID> extends org.springframework.data.repository.ListCrudRepository<T, ID>, org.springframework.data.repository.ListPagingAndSortingRepository<T, ID>, org.springframework.data.repository.query.QueryByExampleExecutor<T> {
  public abstract void flush();
  public abstract <S extends T> S saveAndFlush(S);
  public abstract <S extends T> java.util.List<S> saveAllAndFlush(java.lang.Iterable<S>);
  public default void deleteInBatch(java.lang.Iterable<T>);
  public abstract void deleteAllInBatch(java.lang.Iterable<T>);
  public abstract void deleteAllByIdInBatch(java.lang.Iterable<ID>);
  public abstract void deleteAllInBatch();
  public abstract T getOne(ID);
  public abstract T getById(ID);
  public abstract T getReferenceById(ID);
  public abstract <S extends T> java.util.List<S> findAll(org.springframework.data.domain.Example<S>);
  public abstract <S extends T> java.util.List<S> findAll(org.springframework.data.domain.Example<S>, org.springframework.data.domain.Sort);
  public default java.lang.Iterable findAll(org.springframework.data.domain.Example, org.springframework.data.domain.Sort);
  public default java.lang.Iterable findAll(org.springframework.data.domain.Example);
}
```

**Example B — methods from two different branches, on one repository.**
`scratch-app/src/main/java/com/zenith/data/FamilyDemo.java`:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 4: one method from each branch of the family, called on the same repository.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=family
@Component
@Profile("family")
class FamilyDemo implements CommandLineRunner {

    private final EventRepository events;

    FamilyDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        // declared in CrudRepository
        boolean found = events.existsById(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));
        System.out.println(">>> existsById(...0001) -> " + found);

        // declared in ListPagingAndSortingRepository
        List<EventEntity> byStart = events.findAll(Sort.by("startsAt"));
        System.out.println(">>> findAll(Sort.by(\"startsAt\")) -> " + byStart.size() + " events, earliest first:");
        for (EventEntity e : byStart) {
            System.out.println(">>>   " + e.getStartsAt() + " | " + e.getName());
        }
    }
}
```

Command: `mvn -q spring-boot:run -Dspring-boot.run.profiles=family`. Real output after
`Started DataApplication`:

```text
Hibernate: 
    select
        count(*) 
    from
        event ee1_0 
    where
        ee1_0.id=?
>>> existsById(...0001) -> true
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    order by
        ee1_0.starts_at
>>> findAll(Sort.by("startsAt")) -> 7 events, earliest first:
>>>   2026-11-20T13:30Z | Monsoon Rock Night
>>>   2026-11-28T15:30Z | Jazz at the Note
>>>   2026-12-05T13:00Z | Classical Evening
>>>   2026-12-12T14:30Z | Open Mic Night
>>>   2027-07-10T14:00Z | Sufi Night
>>>   2027-07-18T06:30Z | Blues Brunch
>>>   2027-07-24T11:30Z | Indie Fest
```

### Walkthrough

**What each member adds, read from the `javap` output:**

| Member | Extends | Adds | In plain words |
| --- | --- | --- | --- |
| `Repository<T, ID>` | nothing | 0 | A label: "this is a repository for `T`, whose id type is `ID`". Spring Data uses it to recognise repository interfaces at startup. |
| `CrudRepository<T, ID>` | `Repository` | 12 | The basic actions. Collections come back as `Iterable`. |
| `ListCrudRepository<T, ID>` | `CrudRepository` | 3 | The same `findAll()`, `findAllById` and `saveAll`, now returning `List`. The three `public default` lines with no `<...>` are bridge methods. |
| `PagingAndSortingRepository<T, ID>` | **`Repository` only** | 2 | `findAll(Sort)` returning `Iterable`, and `findAll(Pageable)` returning `Page`. No `save`, no `findById`, no `count`. |
| `ListPagingAndSortingRepository<T, ID>` | `PagingAndSortingRepository` | 1 | `findAll(Sort)`, now returning `List`. |
| `QueryByExampleExecutor<T>` | nothing | 7 | "Find rows that look like this sample." It has one blank, `T`, because it never needs an id. |
| `JpaRepository<T, ID>` | `ListCrudRepository`, `ListPagingAndSortingRepository`, `QueryByExampleExecutor` | 11, plus 1 old | The JPA-only methods in the next table. |

**What `JpaRepository` adds:**

| Method | Plain meaning |
| --- | --- |
| `flush()` | Hibernate holds your changes and sends them later. `flush()` sends them to the database **now**. |
| `saveAndFlush(e)`, `saveAllAndFlush(list)` | Save, then flush straight away. |
| `deleteAllInBatch()`, `deleteAllInBatch(list)`, `deleteAllByIdInBatch(ids)` | Delete with **one** bulk query. The compiled code of `SimpleJpaRepository` shows the difference from `deleteAll()`: `deleteAll()` first calls `findAll()`, then deletes the rows one at a time. |
| `getReferenceById(id)` | Gives a stand-in for the row without running a `SELECT` yet. Inside, it calls Hibernate's `EntityManager.getReference`. |
| `findAll(Example)`, `findAll(Example, Sort)` | The query-by-example versions, now returning `List`. |
| `getOne`, `getById`, `deleteInBatch` | Old names. `javap -v` marks all three `Deprecated: true`: they still work, but new code should not use them. |

**The total.** The seven members declare 36 methods with no body (12, 3, 2, 1, 7 and 11).
Six of them are the same method declared again with `List` instead of `Iterable`. That
leaves **30 different methods with no body**. `SimpleJpaRepository` supplies the code for
all 30. On top of those there is one old method with a body: `deleteInBatch`.

**`CrudRepository` and `JpaRepository` compared:**

| | `CrudRepository` | `JpaRepository` |
| --- | --- | --- |
| Methods | 12 | 30, plus 1 old |
| `findAll()` returns | `Iterable<T>` | `List<T>` |
| Sorting and paging | no | `findAll(Sort)`, `findAll(Pageable)` |
| Query by example | no | yes |
| `flush`, `saveAndFlush`, batch deletes, `getReferenceById` | no | yes |
| Lives in | `spring-data-commons`, shared by every database type | `spring-data-jpa`, for JPA only |

**Example B, step by step:**

1. `existsById(...)` comes from `CrudRepository`. Its SQL is
   `select count(*) from event ee1_0 where ee1_0.id=?`. The `?` is a placeholder: the id
   value travels next to the SQL rather than being pasted into it. The count was 1, so the
   answer is `true`.
2. `findAll(Sort.by("startsAt"))` comes from `ListPagingAndSortingRepository`. Its SQL is
   the same `select` as `findAll()`, plus `order by ee1_0.starts_at`. You named the **Java
   field** `startsAt`, and Hibernate wrote the **column** `starts_at`. You gave no
   direction, so the order is smallest first (ascending). The dates come back sorted,
   unlike plain `findAll()`, which has no `ORDER BY`.
3. Both calls worked on the same `events` object, even though their methods come from two
   different branches of the family. `JpaRepository` joins the branches.

### Important terms

| Term | Meaning |
| --- | --- |
| **Extends (between interfaces)** | The child interface gets every method of the parent and may add more. One interface may extend several. |
| **Repository family** | The interfaces `JpaRepository` is built from: `Repository`, `CrudRepository`, `ListCrudRepository`, `PagingAndSortingRepository`, `ListPagingAndSortingRepository` and `QueryByExampleExecutor`. |
| **`spring-data-commons`** | The shared part of Spring Data, used by every database type. Home of `CrudRepository`. |
| **`spring-data-jpa`** | The JPA-only part of Spring Data. Home of `JpaRepository` and `SimpleJpaRepository`. |
| **`Iterable<T>`** | Anything you can loop over with `for (x : things)`. No `size()`, no `get(i)`. |
| **`List<T>`** | An `Iterable` that also has `size()`, `get(i)` and more. |
| **`Sort`** | An order for the rows, such as `Sort.by("startsAt")`. Uses Java field names. |
| **`Pageable` / `Page<T>`** | Which page you want, and the page you get back with its total count. |
| **`Example<T>`** | A sample object for "find rows that look like this one". |
| **`flush()`** | Sends Hibernate's waiting changes to the database now. |
| **Deprecated** | Still works, but marked "do not use in new code". |
| **Bridge method** | A hidden helper the compiler adds by itself. `javap` prints it as `public default` with no `<...>`. |
| **`javap`** | A tool that comes with Java and prints what is inside a compiled `.class` file. |
| **Classpath (`-cp`)** | The list of places where Java tools look for classes. |

### Where it is used

- **Most Spring Boot + JPA projects** extend `JpaRepository`, because it has everything.
- **Some teams pick `CrudRepository` or `ListCrudRepository`** to keep the contract smaller
  and free of JPA-only methods.
- **List screens** ("page 1 of the events, sorted by date") use the paging-and-sorting
  branch.
- **`saveAndFlush`** is for when the SQL must reach the database right now. Later in this
  task, the rollback lessons use it so that the `INSERT` shows in the log before the
  rollback.

### Edge cases

- **`Sort.by(...)` takes the Java field name, not the column name.** `Sort.by("startsAt")`
  became `order by ee1_0.starts_at`.
- **No direction means ascending.** Without a direction, `Sort.by("startsAt")` puts the
  earliest date first.
- **`QueryByExampleExecutor` has only one blank.** It is `QueryByExampleExecutor<T>`,
  because its methods never take an id.
- **Some members appear twice in the family on purpose.** `findAll()` is declared in both
  `CrudRepository` (returning `Iterable`) and `ListCrudRepository` (returning `List`).
  `JpaRepository` users get the `List` one.

### Common mistakes — with the real errors

1. **Following a Spring Boot 2 tutorial: `extends PagingAndSortingRepository` alone.** Only
   the `extends` line of `EventRepository` was changed, to
   `extends PagingAndSortingRepository<EventEntity, UUID>`. The real errors from
   `mvn -q compile`:

   ```text
   CallPathDemo.java:[23,15] cannot find symbol
     symbol:   method findById(<nulltype>)
     location: variable events of type com.zenith.data.EventRepository
   FamilyDemo.java:[26,31] cannot find symbol
     symbol:   method existsById(java.util.UUID)
     location: variable events of type com.zenith.data.EventRepository
   FamilyDemo.java:[30,51] incompatible types: java.lang.Iterable<com.zenith.data.EventEntity> cannot be converted to java.util.List<com.zenith.data.EventEntity>
   FindAllDemo.java:[23,39] no suitable method found for findAll(no arguments)
       method org.springframework.data.repository.PagingAndSortingRepository.findAll(org.springframework.data.domain.Sort) is not applicable
         (actual and formal argument lists differ in length)
       method org.springframework.data.repository.PagingAndSortingRepository.findAll(org.springframework.data.domain.Pageable) is not applicable
         (actual and formal argument lists differ in length)
   GetClassDemo.java:[34,69] cannot find symbol
     symbol:   method count()
     location: variable events of type com.zenith.data.EventRepository
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `cannot find symbol ... method findById` / `existsById` / `count()` | `PagingAndSortingRepository` extends only `Repository`. These three methods live in `CrudRepository`, which is not in the picture. |
   | `no suitable method found for findAll(no arguments)` | This branch has only `findAll(Sort)` and `findAll(Pageable)`. Each needs one argument. |
   | `incompatible types: Iterable<EventEntity> cannot be converted to List<EventEntity>` | `findAll(Sort)` exists, but here it returns `Iterable`. The `List` version lives in `ListPagingAndSortingRepository`. |

   Fix: extend `JpaRepository`, or add `ListCrudRepository` next to the paging interface.
   `extends ListCrudRepository<EventEntity, UUID>, ListPagingAndSortingRepository<EventEntity, UUID>`
   was compile-tested: zero errors.
2. **Expecting a `List` where the method promises only an `Iterable`.** The third error
   above is this mistake. With `CrudRepository` or `PagingAndSortingRepository`,
   collections come back as `Iterable`, which has no `size()` and no `get(i)`.
3. **Copying `getOne(id)` or `getById(id)` from old code.** Both still work, but both are
   deprecated. New code uses `findById(id)`, which runs a `SELECT` and returns an
   `Optional`, or `getReferenceById(id)`.
4. **Counting the bridge lines in `javap` as real methods.** A line such as
   `public default java.lang.Iterable findAll();`, with no `<...>`, was made by the
   compiler.

### Link to the task

- **S2, first two parts:** "Look at what you inherited" is Example A. `javap` lists every
  method and the member that declares it. "Compare `CrudRepository` and `JpaRepository`" is
  the comparison table in the walkthrough.
- **S2, third part:** "Pick one on purpose" is the next lesson. It covers why a smaller
  interface is easier to replace, and what breaks when you narrow it.

### Do it yourself (second laptop)

**Starting point.** The scratch app, with `EventRepository` extending
`JpaRepository<EventEntity, UUID>` and the demo files from the earlier lessons. Maven has
run at least once, so the jars are in `~/.m2`.

**Step 1 — read the family.**

```bash
D=~/.m2/repository/org/springframework/data
CP=$D/spring-data-commons/3.5.13/spring-data-commons-3.5.13.jar:$D/spring-data-jpa/3.5.13/spring-data-jpa-3.5.13.jar
javap -cp $CP org.springframework.data.repository.Repository org.springframework.data.repository.CrudRepository org.springframework.data.repository.ListCrudRepository org.springframework.data.repository.PagingAndSortingRepository org.springframework.data.repository.ListPagingAndSortingRepository org.springframework.data.repository.query.QueryByExampleExecutor org.springframework.data.jpa.repository.JpaRepository
```

Expected: the 64 lines from Example A. Quick check: run the same `javap` command again
with `| grep -c 'public abstract'` at the end. It prints `36`.

If you see `Error: class not found: ...`, the jar path is wrong. Run
`ls $D/spring-data-commons/` to see which version folder you have.

**Step 2 — run the family demo.** Create
`scratch-app/src/main/java/com/zenith/data/FamilyDemo.java` with the content from
Example B.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=family
```

Expected:

- the `count(*) ... where ee1_0.id=?` statement, then `existsById(...0001) -> true`;
- then the `order by ee1_0.starts_at` statement;
- then 7 events in date order, starting with `2026-11-20T13:30Z | Monsoon Rock Night`.

If you see `existsById(...0001) -> false`, the seed data is not loaded.

**Step 3 — break it and read the errors.** In `EventRepository.java`, change the import to
`org.springframework.data.repository.PagingAndSortingRepository` and the `extends` line to
`PagingAndSortingRepository<EventEntity, UUID>`. Run `mvn -q compile`. Expected: the
errors from "Common mistakes" item 1, one for each demo file that uses a missing method.
With `FindAllDemo`, `GetClassDemo`, `CallPathDemo` and `FamilyDemo` present, there are
exactly five. Change both lines back to `JpaRepository`. Run `mvn -q compile` again. It
succeeds and prints nothing.

**Done when:**

- your `javap` check prints `36`;
- the family demo prints the `order by ee1_0.starts_at` statement and 7 sorted events;
- you have seen `cannot find symbol ... method count()` on your own screen, and you can say
  why `PagingAndSortingRepository` does not have it.

### Summary for revision

- `Repository` is a label. `CrudRepository` has the 12 basic actions, returning
  `Iterable`. `ListCrudRepository` returns `List`.
- `PagingAndSortingRepository` has `findAll(Sort)` and `findAll(Pageable)` only. In Spring
  Data 3, it does not include the CRUD methods.
- `QueryByExampleExecutor` has 7 "find by a sample" methods.
- `JpaRepository` extends `ListCrudRepository`, `ListPagingAndSortingRepository` and
  `QueryByExampleExecutor`, and adds `flush`, `saveAndFlush`, the batch deletes and
  `getReferenceById`. That makes 30 methods with no body, plus one old one.
- `getOne`, `getById` and `deleteInBatch` are deprecated.
- `CrudRepository` is shared by all Spring Data database types. `JpaRepository` is JPA
  only.
- Read the family with `javap`. Ignore `public default` lines with no `<...>`.

### Practice questions, with answers

**Question 1.** `EventRepository` is
`public interface EventRepository extends JpaRepository<EventEntity, UUID> {}`. For each
call below, name the member of the family that **declares** that method.
`PageRequest.of(0, 2)` makes a `Pageable` meaning "page 0, 2 rows".

- (a) `long n = events.count();`
- (b) `List<EventEntity> all = events.findAll();`
- (c) `List<EventEntity> sorted = events.findAll(Sort.by("startsAt"));`
- (d) `events.saveAndFlush(event);`
- (e) `Page<EventEntity> page = events.findAll(PageRequest.of(0, 2));`

**Answer.** These come from the real `javap` output.

- (a) `CrudRepository`: `public abstract long count();`. No other member declares it
  again.
- (b) `ListCrudRepository`: `public abstract java.util.List<T> findAll();`.
  `CrudRepository` also declares `findAll()`, but its version returns `Iterable`, so the
  `List` result comes from `ListCrudRepository`.
- (c) `ListPagingAndSortingRepository`:
  `public abstract java.util.List<T> findAll(org.springframework.data.domain.Sort);`.
  `PagingAndSortingRepository` has an `Iterable` version.
- (d) `JpaRepository`: `public abstract <S extends T> S saveAndFlush(S);`. It is a JPA-only
  method.
- (e) `PagingAndSortingRepository`:
  `public abstract org.springframework.data.domain.Page<T> findAll(org.springframework.data.domain.Pageable);`.
  No member declares it again.

**Question 2.** A teammate copies this from a Spring Boot 2 tutorial into a Spring Boot 3
project that uses Spring Data 3.5. `VenueEntity` is an entity whose `@Id` field is a
`UUID`.

```java
public interface VenueRepository extends PagingAndSortingRepository<VenueEntity, UUID> {
}
```

```java
Optional<VenueEntity> one = venues.findById(id);
List<VenueEntity> byName = venues.findAll(Sort.by("name"));
```

- (a) Does each of the two lines compile? Explain why or why not, for each one.
- (b) Write two different `extends` lines for `VenueRepository` that make both lines
  compile.

**Answer.** The same change was tried on `EventRepository`, and both errors appeared there.

- (a) Neither line compiles.
  - `findById` gives `cannot find symbol ... method findById`. In Spring Data 3,
    `PagingAndSortingRepository` extends only `Repository`, and `findById` lives in
    `CrudRepository`.
  - `findAll(Sort)` exists, but here it returns `Iterable<VenueEntity>`. So the
    assignment gives
    `incompatible types: java.lang.Iterable<...VenueEntity> cannot be converted to java.util.List<...VenueEntity>`.
- (b) Either of these:
  - `public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {}`.
  - `public interface VenueRepository extends ListCrudRepository<VenueEntity, UUID>, ListPagingAndSortingRepository<VenueEntity, UUID> {}`.
    `ListCrudRepository` brings `findById`, and `ListPagingAndSortingRepository` brings a
    `findAll(Sort)` that returns `List`. The same two-interface line was compile-tested on
    `EventRepository` with zero errors.

**Question 3.** Suppose `EventRepository` is changed to
`public interface EventRepository extends CrudRepository<EventEntity, UUID> {}`, and some
code does this:

```java
var all = events.findAll();
System.out.println(all.size());
```

What happens, and why? Give two fixes.

**Answer.** It does not compile. This exact code was compiled against that change:

```text
SizeCheck.java:[6,31] cannot find symbol
  symbol:   method size()
  location: variable all of type java.lang.Iterable<com.zenith.data.EventEntity>
```

`CrudRepository.findAll()` returns `Iterable<EventEntity>`, so `var all` is an `Iterable`.
An `Iterable` promises only that you can loop over it. It has no `size()`.

- Fix 1: extend `ListCrudRepository<EventEntity, UUID>` (or `JpaRepository`). Then
  `findAll()` returns `List`, and `size()` exists. The same code compiled with zero errors
  after that change.
- Fix 2: if only the number is needed, call `events.count()`. It sends one
  `select count(*)` instead of loading every row into memory.

---

## Lesson 5 — Picking the contract on purpose (finishes subtask S2)

### Definition

An interface is a list of methods. A class that implements the interface must have all of
them. Code that holds a variable of that interface type may call only those methods. The
compiler checks both sides. That list is the interface's **contract**.

`EventRepository` is
`public interface EventRepository extends JpaRepository<EventEntity, UUID> {}`. Its contract
is big: `JpaRepository` brings 30 methods with no body. `SimpleJpaRepository`, a class inside
Spring Data, has real code for all 30.

**Picking the contract on purpose** means choosing which interface your repository extends,
and being able to say why. There are three choices:

| Choice | What the contract holds |
| --- | --- |
| A. Extend `JpaRepository` | All 30 methods: save, find, delete, sorting, paging, `flush`, `saveAndFlush`, batch deletes. Lists come back as `List`. |
| B. Extend a smaller family member | `CrudRepository` has the 12 basic methods, and lists come back as `Iterable`. `ListCrudRepository` has the same 12, and lists come back as `List`. |
| C. Extend the empty `Repository` and declare the methods yourself | Exactly the methods you write, nothing more. |

Two more words:

- **Narrowing**: changing a repository to a smaller contract.
- **Fake**: a small class you write by hand. It implements the same interface, with no
  database behind it. Tests use a fake in place of the real repository, so they run fast and
  need no Postgres.

### Why it is needed

- Subtask S2 ends with "pick one on purpose". That needs a reason you can say out loud, not a
  habit.
- A bigger contract gives more ready-made tools.
- A smaller contract has three good sides:
  - **Less can go wrong.** Code that should only read gets a contract with only reading
    methods. A `deleteAll()` call then does not compile.
  - **It is easier to replace.** A fake must implement every method in the contract. Two
    methods take a minute. Thirty methods is a page of empty code.
  - **It is easier to understand.** The interface tells the reader what this code does with
    the data.
- Narrowing has a cost. Every line that calls a method the smaller contract lacks stops
  compiling. You want to know that before you narrow, not after.

### Key points

- The contract decides what callers can do. The compiler refuses anything outside it, even
  if the target behind the proxy has that method.
- Choice C works because Spring Data sends a declared method to `SimpleJpaRepository` when the
  name and parameter types match one of its methods.
- A fake of `UpcomingEvents` needs 2 methods. A fake of `EventRepository` needs 30.
- Narrowing `EventRepository` broke 2 demo lines with `CrudRepository` and 1 with
  `ListCrudRepository`.
- The decision for this task: `EventRepository` stays on `JpaRepository`, on purpose. Small
  contracts such as `UpcomingEvents` can sit next to it.

### How it works, step by step

Two words, re-explained:

- A **proxy** is a stand-in object that Java builds while the app runs. It implements your
  interface and passes each call on.
- The **target** is the real object behind the proxy. For every repository in this app, the
  target is a `SimpleJpaRepository`.

Here is choice C, step by step:

1. At startup, Spring Data finds every interface that extends `Repository`, directly or
   through a family member. The log line `Found 2 JPA repository interfaces.` gives the count.
2. For each interface, it builds one proxy, with a `SimpleJpaRepository` as the target.
3. For each method you declared, Spring Data asks: "Does `SimpleJpaRepository` have a method
   with the same name and the same parameter types?" If yes, calls go straight to that method
   of the target. `findAll(Sort)` and `count()` both match.
4. If no, Spring Data treats your method as a **query method**: one whose query it must build
   from the method's name. That is the topic of the next lessons. So the query step inside the
   proxy is not asking "did you declare this method?". It is asking "did you declare it, and is
   it missing from `SimpleJpaRepository`?"
5. The compiler lets callers use only methods in the contract. Anything else gives
   `cannot find symbol`.
6. A fake implements the same interface. Code written against the interface works with the
   proxy or the fake, and cannot tell which one it got.

### Example

**Part A — a small contract and a fake.** There are three new files in
`scratch-app/src/main/java/com/zenith/data/`.

`UpcomingEvents.java`, the small contract. `Repository` adds no methods, so this interface
has exactly two:

```java
package com.zenith.data;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Lesson 5: a small contract on purpose - only what an "upcoming events" screen needs.
// Repository itself adds no methods, so this interface has exactly the two below.
public interface UpcomingEvents extends Repository<EventEntity, UUID> {

    List<EventEntity> findAll(Sort sort);   // same name and parameters as a method of SimpleJpaRepository

    long count();                           // same here
}
```

`FakeUpcomingEvents.java`, the fake. `@Override` tells the compiler "this fills in a method
of the interface; check that it really does". `List.of()` is an empty list.

```java
package com.zenith.data;

import org.springframework.data.domain.Sort;

import java.util.List;

// A stand-in with no database behind it. Two small methods are all it takes.
class FakeUpcomingEvents implements UpcomingEvents {

    @Override
    public List<EventEntity> findAll(Sort sort) {
        return List.of();
    }

    @Override
    public long count() {
        return 0;
    }
}
```

`NarrowDemo.java`, the demo. It is a `CommandLineRunner`, which means Spring calls its `run`
method once, right after the app starts. `@Profile("narrow")` means Spring uses this class
only when the app starts with the profile `narrow`. `getSimpleName()` gives a class's name
without its package.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 5: the same code, run once with Spring's repository and once with a hand-written fake.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=narrow
@Component
@Profile("narrow")
class NarrowDemo implements CommandLineRunner {

    private final UpcomingEvents upcoming;

    NarrowDemo(UpcomingEvents upcoming) {   // Spring passes in its proxy for UpcomingEvents
        this.upcoming = upcoming;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> real: " + report(upcoming));
        System.out.println(">>> fake: " + report(new FakeUpcomingEvents()));
    }

    // Works with any object that implements UpcomingEvents.
    static String report(UpcomingEvents source) {
        List<EventEntity> sorted = source.findAll(Sort.by("startsAt"));
        String first = sorted.isEmpty() ? "none" : sorted.get(0).getName();
        return source.getClass().getSimpleName() + " -> " + source.count() + " events, first: " + first;
    }
}
```

Command: `mvn -q spring-boot:run -Dspring-boot.run.profiles=narrow`. Real output, with the log
lines shortened:

```text
... Finished Spring Data repository scanning in 20 ms. Found 2 JPA repository interfaces.
... Started DataApplication in 1.817 seconds (process running for 1.971)
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    order by
        ee1_0.starts_at
Hibernate: 
    select
        count(*) 
    from
        event ee1_0
>>> real: $Proxy103 -> 7 events, first: Monsoon Rock Night
>>> fake: FakeUpcomingEvents -> 0 events, first: none
```

**Part B — what breaks when you narrow `EventRepository`.** Only its import and `extends` line
were changed. Then `mvn -q compile` was run, the errors were read, and the file was changed
back. Two demo lines are affected:

- `FindAllDemo.java` line 23: `List<EventEntity> all = events.findAll();`
- `FamilyDemo.java` line 30:
  `List<EventEntity> byStart = events.findAll(Sort.by("startsAt"));`

With `extends CrudRepository<EventEntity, UUID>`, there are two real errors in the demo files
(paths shortened):

```text
FamilyDemo.java:[30,43] method findAll in interface org.springframework.data.repository.CrudRepository<T,ID> cannot be applied to given types;
  required: no arguments
  found:    org.springframework.data.domain.Sort
  reason: actual and formal argument lists differ in length
FindAllDemo.java:[23,47] incompatible types: java.lang.Iterable<com.zenith.data.EventEntity> cannot be converted to java.util.List<com.zenith.data.EventEntity>
```

With `extends ListCrudRepository<EventEntity, UUID>`, there is one real error:

```text
FamilyDemo.java:[30,43] method findAll in interface org.springframework.data.repository.ListCrudRepository<T,ID> cannot be applied to given types;
  required: no arguments
  found:    org.springframework.data.domain.Sort
  reason: actual and formal argument lists differ in length
```

### Walkthrough

**Part A, line by line:**

1. `Found 2 JPA repository interfaces.` These are `EventRepository` and `UpcomingEvents`. Two
   interfaces for the same entity are fine, because each one gets its own proxy.
2. Spring passes the `UpcomingEvents` proxy into the `NarrowDemo` constructor.
3. `report(upcoming)` calls `findAll(Sort.by("startsAt"))`. `Sort.by("startsAt")` means
   "order by the Java field `startsAt`, smallest first". The proxy passes the call to
   `SimpleJpaRepository.findAll(Sort)`, and Hibernate sends the first SQL:
   `... order by ee1_0.starts_at`.
4. `report` then calls `count()`, which sends the second SQL:
   `select count(*) from event ee1_0`.
5. Only then is `>>> real: $Proxy103 -> 7 events, first: Monsoon Rock Night` printed. Both SQL
   statements come first because `report` must finish before `println` can print its result.
   `$Proxy103` is the proxy's class name. The number is only a counter Java uses for proxy
   classes, so it may differ on another laptop.
6. `report(new FakeUpcomingEvents())` runs the same code. No SQL appears, because the fake
   never touches a database. It returns an empty list and 0, so the line says
   `0 events, first: none`.
7. The same `report` method worked with both objects. That is what "easy to replace" means.

**Trying to delete through the small contract.** `upcoming.deleteAll();` was added as the
first line inside `run`, and `mvn -q compile` was run. The real error:

```text
NarrowDemo.java:[24,17] cannot find symbol
  symbol:   method deleteAll()
  location: variable upcoming of type com.zenith.data.UpcomingEvents
```

The `UpcomingEvents` contract has no `deleteAll()`, so the compiler refuses the line. It does
not matter that `SimpleJpaRepository` behind the proxy has `deleteAll()`. Callers can only use
what the contract lists.

**A fake of the big contract, for comparison.** This temporary file was compiled and then
deleted:

```java
package com.zenith.data;

// A stand-in for the big contract.
class FakeEventRepository implements EventRepository {
}
```

The real error:

```text
FakeEventRepository.java:[4,1] com.zenith.data.FakeEventRepository is not abstract and does not override abstract method <S>findAll(org.springframework.data.domain.Example<S>,org.springframework.data.domain.Sort) in org.springframework.data.jpa.repository.JpaRepository
```

In plain words: "this class promises to implement the interface, but it has no code for this
method". The compiler names only the first missing method it finds. To make this fake
compile, you would have to write all 30.

| Contract | Methods a fake must write | Demo lines that broke when `EventRepository` was narrowed to it |
| --- | --- | --- |
| `JpaRepository` | 30 | 0 (it is the current choice) |
| `ListCrudRepository` | 12 | 1 |
| `CrudRepository` | 12 | 2 |
| `UpcomingEvents`, which extends `Repository` and declares 2 methods | 2 | does not apply (a separate interface) |

**Part B decoded:**

| Part of the message | Plain meaning |
| --- | --- |
| `method findAll in interface ...CrudRepository<T,ID> cannot be applied to given types` | The only `findAll` this contract has does not fit the way you called it. |
| `required: no arguments` / `found: ...Sort` | This contract's `findAll` takes nothing, but you passed a `Sort`. `findAll(Sort)` belongs to the paging-and-sorting branch of the family, which neither `CrudRepository` nor `ListCrudRepository` includes. |
| `incompatible types: Iterable<EventEntity> cannot be converted to List<EventEntity>` | `CrudRepository.findAll()` returns an `Iterable`, which you can loop over but which has no `size()` or `get(i)`. Java will not turn it into a `List` for you. `ListCrudRepository` fixes this one, so only one error is left there. |

### Important terms

| Term | Meaning |
| --- | --- |
| **Contract** | The list of methods an interface promises. Callers can use only those, and implementing classes must have all of them. |
| **Picking the contract on purpose** | Choosing which interface a repository extends, with a reason. |
| **Narrowing** | Changing a repository to a smaller contract. |
| **Fake** | A hand-written class that implements the same interface with no database behind it. Used in tests. |
| **Choice C** | Extending the empty `Repository` and declaring only the methods you want. Spring Data's documentation calls it "selectively exposing CRUD methods". |
| **Query method** | A declared repository method with no matching method in `SimpleJpaRepository`. Spring Data must build its query from the method's name. |
| **Bean** | An object that Spring creates and keeps for you. Each repository proxy is a bean. |
| **`@Override`** | Tells the compiler "this method fills in a method of the interface". The compiler checks that it really does. |

### Where it is used

- Most Spring Boot + JPA projects use choice A as their default.
- Spring Data's own documentation describes choice C under the name "selectively exposing CRUD
  methods". A common use is a repository that offers no delete methods at all.
- A smaller contract is the better pick in these cases:
  - **Code that only reads**, such as a report or a reminder job. Give it its own small
    interface, like `UpcomingEvents`. The compiler then refuses writes through it, and its
    fake stays tiny.
  - **Code shared across several kinds of database.** Use `CrudRepository` or
    `ListCrudRepository`. They live in `spring-data-commons`, the part of Spring Data shared by
    every database type (JPA, MongoDB, JDBC and more). `JpaRepository` lives in
    `spring-data-jpa` and works only with JPA.
  - **Code you often replace with a fake in tests.** Keep its contract small.

### Edge cases

- **Two repository interfaces for one entity are fine.** `EventRepository` and
  `UpcomingEvents` both manage `EventEntity`. The log says `Found 2 JPA repository interfaces.`,
  and each gets its own proxy.
- **The target's other methods stay hidden.** The proxy for `UpcomingEvents` has a
  `SimpleJpaRepository` behind it, which has `deleteAll()`. The compiler still refuses
  `upcoming.deleteAll()`, because callers see only the contract.
- **The number in `$Proxy103` is only a counter.** It can change when interfaces are added or
  removed. Never rely on it.

### Common mistakes — with the real errors

1. **Narrowing because "smaller is cleaner", without checking the callers.** With
   `CrudRepository`, both `List<EventEntity> all = events.findAll();` and
   `events.findAll(Sort.by("startsAt"))` stopped compiling (the real errors are in Part B of the
   example). Before you narrow, look at which methods the callers actually use.
2. **Thinking a small contract makes the data read-only.** It limits which methods can be
   called. It does not stop changes to the entities you get back. Inside a transaction,
   Hibernate can save such changes by itself. A later lesson in this task shows exactly that.
3. **A typo in a method you copy into a choice-C interface.** This temporary file compiled
   without any error:

   ```java
   package com.zenith.data;

   import org.springframework.data.repository.Repository;

   import java.util.UUID;

   interface TypoEvents extends Repository<EventEntity, UUID> {

       long cout();   // meant: count()
   }
   ```

   Then the app refused to start. The real lines, shortened:

   ```text
   ... Found 3 JPA repository interfaces.
   ... Application run failed
   org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'typoEvents' defined in com.zenith.data.TypoEvents defined in @EnableJpaRepositories declared on JpaRepositoriesRegistrar.EnableJpaRepositoriesConfiguration: Could not create query for public abstract long com.zenith.data.TypoEvents.cout(); Reason: Failed to create query for method public abstract long com.zenith.data.TypoEvents.cout(); No property 'cout' found for type 'EventEntity'
   ...
   Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'cout' found for type 'EventEntity'
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Found 3 JPA repository interfaces.` | Spring Data found `TypoEvents` too, so it must build a proxy for it. |
   | `Error creating bean with name 'typoEvents'` | A bean is an object Spring creates and keeps for you. Building the `TypoEvents` proxy failed. |
   | `Could not create query for ... TypoEvents.cout()` | `SimpleJpaRepository` has no `cout()`, so Spring Data tried to build a query from the name. |
   | `No property 'cout' found for type 'EventEntity'` | It read `cout` as the name of a field in `EventEntity`. No such field exists. |
   | `Application run failed` | The whole app stopped. No `>>>` line ran, even though no code calls `cout()`. |

   The compiler cannot catch this mistake. Only startup does.

### Link to the task

S2 says: "Look at what you inherited. Compare `CrudRepository` and `JpaRepository`. Pick one on
purpose." The first two parts were the `javap` list of all 30 methods and the comparison
table. This lesson covers the third part. Here is the pick for this task:

> `EventRepository` keeps `extends JpaRepository<EventEntity, UUID>`, on purpose.

There are three reasons:

1. This app talks to one database, through JPA. `CrudRepository`'s main advantage, being
   shared by every Spring Data database type, gives nothing here.
2. The current code already needs more than `CrudRepository` gives: `List` results and
   `findAll(Sort)`. Narrowing broke two lines with `CrudRepository` and one with
   `ListCrudRepository`.
3. The coming subtasks need two more things:
   - paging through events: `findAll(Pageable)` returns a `Page` with the total count;
   - seeing write SQL right away: `saveAndFlush` sends it at once.

   Of all the family members, only `JpaRepository` has both.

Where a small contract fits, choice C can sit next to it, like `UpcomingEvents`. The app runs
fine with two repository interfaces for the same entity.

With this lesson, S2 is complete.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` extends `JpaRepository<EventEntity, UUID>`;
- the demo files `FindAllDemo`, `GetClassDemo`, `CallPathDemo` and `FamilyDemo` exist;
- Postgres runs in the Docker container `pg`, with the 7 seed events loaded.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the three files.** In `scratch-app/src/main/java/com/zenith/data/`, create
`UpcomingEvents.java`, `FakeUpcomingEvents.java` and `NarrowDemo.java`. Use the exact content
from the example, Part A.

**Step 3 — run the demo.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=narrow
```

You should see:

- `Found 2 JPA repository interfaces.`;
- the `order by ee1_0.starts_at` statement, then the `count(*)` statement;
- `>>> real: $Proxy... -> 7 events, first: Monsoon Rock Night`;
- `>>> fake: FakeUpcomingEvents -> 0 events, first: none`.

If something goes wrong:

- `Connection to localhost:5432 refused` means Postgres is not running. Run `docker start pg`.
- `>>> real: $Proxy... -> 0 events, first: none` means the seed data is not loaded.

**Step 4 — try to delete through the small contract.** In `NarrowDemo.java`, add
`upcoming.deleteAll();` as the first line inside `run`, just above the `>>> real:` line. Run
`mvn -q compile`. You should see this error:

```text
[ERROR] .../NarrowDemo.java:[24,17] cannot find symbol
  symbol:   method deleteAll()
  location: variable upcoming of type com.zenith.data.UpcomingEvents
```

Remove the line, and run `mvn -q compile` again. It prints nothing.

**Step 5 — narrow `EventRepository` and count what breaks.** In `EventRepository.java`, make
two changes:

- change the import to `org.springframework.data.repository.ListCrudRepository`;
- change the `extends` line to `ListCrudRepository<EventEntity, UUID>`.

Run `mvn -q compile`. You should see exactly one error,
`FamilyDemo.java:[30,43] method findAll in interface org.springframework.data.repository.ListCrudRepository<T,ID> cannot be applied to given types;`,
with `found: org.springframework.data.domain.Sort`. Change both lines back to `JpaRepository`,
and run `mvn -q compile`. It prints nothing.

**Step 6 — write your decision.** In your notes, write one sentence, "EventRepository extends
JpaRepository on purpose, because ...", with at least two reasons.

**Done when:**

- the two `>>>` lines print `7 events, first: Monsoon Rock Night` and
  `FakeUpcomingEvents -> 0 events, first: none`;
- you have seen `cannot find symbol ... method deleteAll()` on your own screen;
- with `ListCrudRepository` you saw exactly one error, and after changing back,
  `mvn -q compile` prints nothing;
- your notes hold your S2 decision with at least two reasons.

### Summary for revision

- A contract is the list of methods an interface promises. Callers can use only those, and the
  compiler checks it.
- There are three choices: `JpaRepository` (30 methods), a smaller family member
  (`CrudRepository` or `ListCrudRepository`, 12), or the empty `Repository` plus only the
  methods you declare.
- Choice C works because Spring Data sends a declared method to `SimpleJpaRepository` when the
  name and parameter types match. With no match, Spring Data tries to build a query from the
  name. A typo there fails at startup, not at compile time.
- Smaller contracts: less can go wrong, the fake is small, and the reader sees the intent.
  The cost: callers that use missing methods break.
- A small contract limits calls. It does not make the data read-only.
- The S2 decision: `EventRepository` stays on `JpaRepository`, because the app is JPA-only, it
  already uses `List` and `findAll(Sort)`, and paging plus `saveAndFlush` are coming. Small
  interfaces such as `UpcomingEvents` can sit next to it.

### Practice questions, with answers

**Question 1.** Here is an interface. `Repository` comes from
`org.springframework.data.repository` and declares no methods. `EventEntity` is an entity with
a field called `name`.

```java
public interface UpcomingEvents extends Repository<EventEntity, UUID> {
    List<EventEntity> findAll(Sort sort);
    long count();
}
```

`upcoming` is a variable of type `UpcomingEvents`. For each line, say whether it compiles. If
it does not, say why.

- (a) `long n = upcoming.count();`
- (b) `List<EventEntity> all = upcoming.findAll();`
- (c) `upcoming.deleteAll();`
- (d) `List<EventEntity> sorted = upcoming.findAll(Sort.by("name"));`

**Answer.** All four lines were compiled together in one temporary class, inside a method
that takes `UpcomingEvents upcoming`.

- (a) It compiles. `count()` is in the contract.
- (b) It does not compile. The contract has only `findAll(Sort)`, which needs one argument.
  The real error:

  ```text
  Q1Check.java:[10,41] method findAll in interface com.zenith.data.UpcomingEvents cannot be applied to given types;
    required: org.springframework.data.domain.Sort
    found:    no arguments
    reason: actual and formal argument lists differ in length
  ```

- (c) It does not compile. `deleteAll()` is not in the contract, even though
  `SimpleJpaRepository` behind the proxy has it. The real error:

  ```text
  Q1Check.java:[11,17] cannot find symbol
    symbol:   method deleteAll()
    location: variable upcoming of type com.zenith.data.UpcomingEvents
  ```

- (d) It compiles. `findAll(Sort)` is in the contract. `Sort.by("name")` names the Java field
  `name`, so the SQL would end with `order by ee1_0.name`.

**Question 2.** An app uses JPA with PostgreSQL. It has the entity `EventEntity`, whose `@Id`
field is a `UUID`. Two pieces of code need event data:

- A nightly reminder job. It reads all events sorted by start time (`Sort.by("startsAt")`) and
  emails people. It must never change or delete an event.
- The admin screen. It creates, edits and deletes events, and shows them 20 per page.

Answer three parts:

- (a) Write a repository interface for each piece of code: the `interface ... extends ...`
  line, plus any methods you declare.
- (b) Give one reason for each choice.
- (c) A test needs a fake of each interface. How many methods must each fake implement?

**Answer.**

- (a) For the reminder job, use choice C with one method:

  ```java
  public interface ReminderEvents extends Repository<EventEntity, UUID> {

      List<EventEntity> findAll(Sort sort);
  }
  ```

  For the admin screen: `public interface EventRepository extends JpaRepository<EventEntity, UUID> {}`.

  `ReminderEvents` was checked in this project. With it present, the app logged
  `Found 3 JPA repository interfaces.` and started normally.
- (b)
  - Reminder job: the compiler refuses any `save` or `delete` call through `ReminderEvents`,
    so "never change or delete" is checked by the compiler, not by memory. It only limits
    calls. Changes made to returned entities inside a transaction can still be saved.
  - Admin screen: it needs save, delete and paging (`findAll(Pageable)` returning a `Page`).
    `JpaRepository` has all of them.
  - Another good answer for the admin screen is
    `extends ListCrudRepository<EventEntity, UUID>, ListPagingAndSortingRepository<EventEntity, UUID>`.
    It has save, delete and paging, without the JPA-only methods.
- (c)
  - A fake of `ReminderEvents` needs **1** method. This fake compiled:

    ```java
    class FakeReminderEvents implements ReminderEvents {

        @Override
        public List<EventEntity> findAll(Sort sort) {
            return List.of();
        }
    }
    ```

  - A fake of the `JpaRepository`-based `EventRepository` needs **30** methods. An empty
    `class FakeEventRepository implements EventRepository {}` fails with
    `is not abstract and does not override abstract method <S>findAll(...Example<S>,...Sort) in ...JpaRepository`.
    That is the first of the 30 missing methods.

---

## Lesson 6 — The method name is the query (starts subtask S3)

### Definition

A **derived query** is a repository method whose query Spring Data builds from the method's
name. "Derived" means "worked out from something else", and here that something is the name.
You write a single line: the return type, the name and the parameters. There is no SQL and no
method body.

```java
List<EventEntity> findByVenueId(UUID venueId);
```

That one line becomes this SQL:

```text
select ee1_0.id, ee1_0.name, ee1_0.starts_at, ee1_0.venue_id from event ee1_0 where ee1_0.venue_id=?
```

### Why it is needed

- `EventRepository extends JpaRepository<EventEntity, UUID>` gives 30 ready methods. They can
  find one row by its id (`findById`) or fetch all rows (`findAll`). None of them can say "the
  events at this venue".
- Without derived queries, you have two poor options:
  - Load every event with `findAll()` and filter in Java. The database sends every row, even
    when you need 3 out of a million.
  - Write the SQL by hand for every simple lookup.
- A derived query puts the condition into the SQL. The database does the filtering and sends
  only the matching rows.
- Spring Data checks every derived query when the app starts. A name, a parameter count or a
  parameter type that doesn't fit stops the app before anyone can call the method.

### Key points

- The name is the query: `find` (what to do), `By` (the conditions start), `VenueId` (a field of
  the entity), then one parameter per condition.
- The name uses the **Java field** name (`venueId`). The SQL uses the **column**
  (`venue_id`).
- The query is worked out once, at startup. Each call only binds a new value into the `?`.
- The return type decides how many rows you expect. `List` gives an empty list when nothing
  matches. A single `EventEntity` gives `null` for no row and an error for more than one.
- A missing parameter or a parameter of the wrong type stops the app at startup.
- Query methods are run by the proxy's query step, `QueryExecutorMethodInterceptor`. The call
  never reaches `SimpleJpaRepository`.

### How it works, step by step

First, three words:

- **Property**: a field of the entity, by its Java name (`venueId`), not by its column name
  (`venue_id`).
- **Placeholder**: the `?` in the SQL, a hole where a value goes. The value travels separately
  from the SQL text.
- **Bind**: to put a value into a placeholder.

The fields of `EventEntity`:

```java
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
```

**The grammar.** Here is how `List<EventEntity> findByVenueId(UUID venueId)` is read, piece by
piece:

| Piece | What Spring Data reads |
| --- | --- |
| `find` | The **subject**: what to do. `find` means "fetch rows". `read`, `get`, `query`, `search` and `stream` mean the same. Other subjects, such as `count` and `exists`, do other jobs. |
| *(nothing here)* | Any words between `find` and `By`, such as `All` in `findAllByVenueId`, are only a description and are ignored. A few special words such as `Distinct`, `First` and `Top` do change the query. |
| `By` | "The conditions start here." It is required. |
| `VenueId` | A property, with its first letter in capitals. Spring Data makes it `venueId` and looks for that field in `EventEntity`. With no extra keyword, the condition is "equals". |
| `(UUID venueId)` | The value for the condition. There is one parameter per condition, and its type must match the field's type (`UUID`). Spring Data goes by position: the first parameter goes into the first `?`. |
| `List<EventEntity>` | The return type, which decides how many rows you expect. `List` means "any number, maybe zero". |

**Step by step:**

1. At startup, Spring Data sees `findByVenueId` in `EventRepository`. `SimpleJpaRepository`,
   the class with real code for the 30 ready methods, has no method with that name. So
   `findByVenueId` is a **query method**: one whose query Spring Data must build from the
   name.
2. It cuts the name into pieces: the subject `find`, then `By`, then `VenueId`.
3. It changes `VenueId` to `venueId` and checks that `EventEntity` has that field.
4. It counts the conditions (1) and checks that the method has a parameter for each one.
5. It builds the query "events whose `venueId` equals parameter 1". Hibernate checks the
   types: the field is a `UUID` and the parameter is a `UUID`.
6. On every call, the proxy runs the prepared query with your value bound to the `?`. The proxy
   is the stand-in object Spring built for `EventRepository`, and its query step
   `QueryExecutorMethodInterceptor` runs query methods. The call never reaches
   `SimpleJpaRepository`.
7. The return type decides what to do with the rows that come back.

If step 3, 4 or 5 fails, the app does not start. "Common mistakes" items 2 and 3 show the real
errors.

### Example

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets its first derived query:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);
}
```

`scratch-app/src/main/java/com/zenith/data/DerivedQueryDemo.java` calls it for two venues
from the seed data:

- The Blue Note (`22222222-…`) has 3 events.
- Lakeside Hall (`33333333-…`) has none.

It is a `CommandLineRunner` (Spring calls its `run` method once, right after startup). It is
marked `@Profile("derived")`, so it runs only when the app starts with the profile `derived`.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 6: one derived query, called for two venues.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=derived
@Component
@Profile("derived")
class DerivedQueryDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE     = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LAKESIDE_HALL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final EventRepository events;

    DerivedQueryDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("The Blue Note", BLUE_NOTE);
        show("Lakeside Hall", LAKESIDE_HALL);
    }

    private void show(String venueName, UUID venueId) {
        List<EventEntity> atVenue = events.findByVenueId(venueId);
        System.out.println(">>> findByVenueId(" + venueName + ") -> " + atVenue.size() + " events");
        for (EventEntity e : atVenue) {
            System.out.println(">>>   " + e.getName());
        }
    }
}
```

This is the command. The last part, `-Dspring-boot.run.arguments=...`, passes one setting to
the app: it turns Hibernate's binding log up to `trace`, its most detailed level, so the log
shows every value put into a `?`.

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=derived -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output, with the log lines shortened:

```text
... Found 2 JPA repository interfaces.
... Started DataApplication in 1.885 seconds (process running for 2.044)
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.venue_id=?
... TRACE ... org.hibernate.orm.jdbc.bind : binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]
>>> findByVenueId(The Blue Note) -> 3 events
>>>   Jazz at the Note
>>>   Open Mic Night
>>>   Blues Brunch
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.venue_id=?
... TRACE ... org.hibernate.orm.jdbc.bind : binding parameter (1:UUID) <- [33333333-3333-3333-3333-333333333333]
>>> findByVenueId(Lakeside Hall) -> 0 events
```

### Walkthrough

1. `Found 2 JPA repository interfaces.` These are `EventRepository` and `UpcomingEvents`, a
   second, small repository interface in this project. The new method didn't add an interface.
   It added a query to one.
2. `where ee1_0.venue_id=?` comes from the name: `VenueId` became the field `venueId`, and
   Hibernate's naming rule turned that into the column `venue_id`. You named the Java field,
   and the SQL uses the column.
3. `binding parameter (1:UUID) <- [22222222-...]` reads as "put into `?` number 1 a value of
   type `UUID`: this one". The value travels next to the SQL, not pasted into it.
4. The Blue Note has 3 events. They come back in the order Postgres chose, because there is no
   `ORDER BY`. Don't rely on that order.
5. The second call sends the same SQL text with a new value. The query was worked out once, at
   startup, and each call only binds a value.
6. Lakeside Hall gives `0 events`. A `List` return type gives an **empty list** when nothing
   matches, never `null`. So `atVenue.size()` and the loop are safe.

Two more facts, from real runs with temporary interfaces:

- `List<EventEntity> findAllByVenueId(UUID venueId)` sent exactly the same SQL,
  `where ee1_0.venue_id=?`, and also returned 3 events for The Blue Note. `All` is only a
  description.
- With the return type `EventEntity` (one row, not a list), Lakeside Hall gave `null`. The
  Blue Note, with 3 rows, gave an error (see "Common mistakes", item 1).

### Important terms

| Term | Meaning |
| --- | --- |
| **Derived query** | A repository method whose query Spring Data builds from the method's name. |
| **Query method** | A declared repository method that `SimpleJpaRepository` does not have, so Spring Data must build its query. |
| **Subject** | The first word of the name, which says what to do: `find` (and `read`, `get`, `query`, `search`, `stream`), or `count`, `exists`, `delete`. |
| **Property** | A field of the entity, by its Java name, such as `venueId`. |
| **Placeholder (`?`)** | A hole in the SQL where a value goes. |
| **Bind** | To put a value into a placeholder. Hibernate's binding log shows `binding parameter (1:UUID) <- [...]`. |
| **`SIMPLE_PROPERTY`** | Spring Data's name for the plain "equals" condition, used when no keyword follows the property. |
| **`QueryExecutorMethodInterceptor`** | The query step inside the repository proxy. It runs query methods. |
| **Bean** | An object that Spring creates and keeps for you. Each repository proxy is a bean. |

### Where it is used

- Derived queries are the everyday way to write simple lookups in Spring projects:
  `findByEmail`, `findByStatus`, `findByCustomerId`, `findByVenueId`.
- They fit best for lookups by a linking column such as `venue_id`, which points from an event
  to its venue. The `event` table already has an index on that column, from the first
  migration, `V1__baseline.sql`:

  ```sql
  create index idx_event_venue_id  on event (venue_id);
  ```

  With that index, Postgres can find a venue's events without reading the whole table.
- When a condition gets long, the name gets long too. Then a hand-written `@Query` is the better
  tool, and a later lesson in this task covers it.

### Edge cases

- **No match gives an empty list, not `null`.** Lakeside Hall: `0 events`, and the loop ran zero
  times.
- **No `ORDER BY`.** The Blue Note's events came back as Jazz at the Note, Open Mic Night, Blues
  Brunch, which is the order Postgres chose. Don't rely on it.
- **Words between `find` and `By` are ignored**, except special words such as `Distinct`,
  `First` and `Top`. `findAllByVenueId` sent the same SQL as `findByVenueId`.
- **A one-row return type with no match gives `null`.** `EventEntity findByVenueId(...)` with
  Lakeside Hall printed `null`.
- **Type mistakes are caught at startup.** Hibernate checks the condition's types while Spring
  Data builds the query, so a `String` parameter for a `UUID` field never reaches the database.

### Common mistakes — with the real errors

1. **A one-row return type when many rows match.** This is a temporary interface:

   ```java
   interface OneEventPerVenue extends Repository<EventEntity, UUID> {

       List<EventEntity> findAllByVenueId(UUID venueId);   // "All" is only a word here

       EventEntity findByVenueId(UUID venueId);             // the return type says: exactly one row
   }
   ```

   Calling `findByVenueId` with The Blue Note's id sent the normal `where ee1_0.venue_id=?` SQL
   and got 3 rows back. The real error:

   ```text
   org.springframework.dao.IncorrectResultSizeDataAccessException: Query did not return a unique result: 3 results were returned
   	...
   	at com.zenith.data.$Proxy104.findByVenueId(Unknown Source) ~[na:na]
   	at com.zenith.data.OneEventDemo.run(OneEventDemo.java:26) ~[classes/:na]
   	...
   Caused by: org.hibernate.NonUniqueResultException: Query did not return a unique result: 3 results were returned
   	at org.hibernate.query.spi.AbstractSelectionQuery.getSingleResult(AbstractSelectionQuery.java:281)
   	...
   	at org.springframework.data.jpa.repository.query.JpaQueryExecution$SingleEntityExecution.doExecute(JpaQueryExecution.java:226)
   	...
   	at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:149)
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Query did not return a unique result: 3 results were returned` | You promised one row with the return type `EventEntity`, and the database sent 3. |
   | `SingleEntityExecution` / `getSingleResult` | The return type made Spring Data ask Hibernate for exactly one result. |
   | `QueryExecutorMethodInterceptor.invoke` | The query step inside the proxy ran this method. `SimpleJpaRepository` never appears. |
   | `Caused by: org.hibernate.NonUniqueResultException` | Hibernate's own error. Spring changed it into its own error type, `IncorrectResultSizeDataAccessException`. |

   The app started fine, because a one-row return type is legal. It failed only when the call
   returned 3 rows. Use `List` unless the column can hold each value only once.

2. **A condition with no parameter.** This is a temporary interface:

   ```java
   interface NoArgVenue extends Repository<EventEntity, UUID> {

       List<EventEntity> findByVenueId();   // the name has one condition, but no parameter
   }
   ```

   The app refused to start. This is the last `Caused by` line:

   ```text
   Caused by: java.lang.IllegalStateException: Method public abstract java.util.List com.zenith.data.NoArgVenue.findByVenueId() expects at least 1 arguments but only found 0; This leaves an operator of type SIMPLE_PROPERTY for property venueId unbound
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `expects at least 1 arguments but only found 0` | The name has one condition, so the method needs one parameter. |
   | `operator of type SIMPLE_PROPERTY` | The plain "equals" condition you get when no keyword follows the property. |
   | `for property venueId unbound` | No value would ever go into this `?`. |

3. **A parameter of the wrong type.** This is a temporary interface:

   ```java
   interface TextVenue extends Repository<EventEntity, UUID> {

       List<EventEntity> findByVenueId(String venueId);   // the field venueId is a UUID, not a String
   }
   ```

   The app refused to start. The real lines, shortened:

   ```text
   ... Found 3 JPA repository interfaces.
   ... Application run failed
   org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'textVenue' defined in com.zenith.data.TextVenue defined in @EnableJpaRepositories declared on JpaRepositoriesRegistrar.EnableJpaRepositoriesConfiguration: Could not create query for public abstract java.util.List com.zenith.data.TextVenue.findByVenueId(java.lang.String); ...
   ...
   Caused by: org.hibernate.query.SemanticException: Cannot compare left expression of type 'java.util.UUID' with right expression of type 'java.lang.String'
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Error creating bean with name 'textVenue'` | A bean is an object Spring creates and keeps for you. Building the proxy for `TextVenue` failed. |
   | `Could not create query for ... findByVenueId(java.lang.String)` | Spring Data could not build this method's query. |
   | `Cannot compare left expression of type 'java.util.UUID' with right expression of type 'java.lang.String'` | Hibernate checked the condition `venueId = parameter`. The left side (the field) is a `UUID`, and the right side (your parameter) is a `String`. |

Mistakes 2 and 3 are caught at startup, before anyone calls the method. Mistake 1 is caught
only when a call returns too many rows.

### Link to the task

S3 says: "Write three derived queries: `findByVenueId`, `findByNameContainingIgnoreCase`,
`existsBySlug`. Read their SQL. Then misspell a property and read the startup failure." This
lesson covers the first method and its SQL: `findByVenueId` became `where ee1_0.venue_id=?`,
with the venue id bound to the `?`. The next lessons add keywords (`Containing`,
`IgnoreCase`), then the yes/no query `existsBySlug`, then the misspelled property.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` extends `JpaRepository<EventEntity, UUID>`;
- Postgres runs in the Docker container `pg`, with the 7 seed events loaded.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the derived query.** Replace
`scratch-app/src/main/java/com/zenith/data/EventRepository.java` with the 14-line version from
the example.

**Step 3 — add the demo.** Create
`scratch-app/src/main/java/com/zenith/data/DerivedQueryDemo.java` with the content from the
example.

**Step 4 — run it with the binding log.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=derived -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see these lines, in this order:

- the `select` statement ending in `where ee1_0.venue_id=?`;
- `binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]`;
- `>>> findByVenueId(The Blue Note) -> 3 events`, followed by 3 names;
- the same `select` again;
- `binding parameter (1:UUID) <- [33333333-3333-3333-3333-333333333333]`;
- `>>> findByVenueId(Lakeside Hall) -> 0 events`.

If something goes wrong:

- `Connection to localhost:5432 refused` means Postgres is not running. Run `docker start pg`.
- `The Blue Note) -> 0 events` means the seed data is not loaded.
- If no `binding parameter` lines appear, the `-Dspring-boot.run.arguments=...` part is missing
  or mistyped. The SQL still prints without it.

**Step 5 — break it with a wrong parameter type.** Create a temporary file,
`scratch-app/src/main/java/com/zenith/data/TextVenue.java`:

```java
package com.zenith.data;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface TextVenue extends Repository<EventEntity, UUID> {

    List<EventEntity> findByVenueId(String venueId);   // the field venueId is a UUID, not a String
}
```

Run `mvn -q spring-boot:run -Dspring-boot.run.profiles=derived`. You should see:

- `Found 3 JPA repository interfaces.`;
- `Application run failed`;
- near the bottom,
  `Caused by: org.hibernate.query.SemanticException: Cannot compare left expression of type 'java.util.UUID' with right expression of type 'java.lang.String'`;
- no `>>>` lines.

Delete `TextVenue.java` and run the same command again. It starts and prints the two `>>>`
result lines. Maven removes the old compiled `TextVenue.class` by itself. This was tested:
after deleting only the `.java` file, the next build removed its `.class` file.

**Done when:**

- you have seen `where ee1_0.venue_id=?` and
  `binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]` on your own screen;
- The Blue Note shows 3 events and Lakeside Hall shows 0;
- you have seen the
  `Cannot compare left expression of type 'java.util.UUID' with right expression of type 'java.lang.String'`
  startup failure, deleted `TextVenue.java`, and the app runs again.

### Summary for revision

- A derived query is a method whose query comes from its name: `findByVenueId` becomes
  `where ee1_0.venue_id=?`.
- The grammar: a subject (`find`), optional ignored words, `By`, a property (the Java field
  name with a capital first letter), and one parameter per condition, in order.
- Spring Data checks the property, the parameter count and the parameter types at startup.
  Mistakes there stop the app.
- Each call only binds a value into the prepared query. The binding log
  (`org.hibernate.orm.jdbc.bind` at `trace`) shows it.
- The return type decides one or many. `List` gives an empty list for no match. A single
  entity gives `null` for no match and `IncorrectResultSizeDataAccessException` for several.
- Query methods are run by the proxy's query step, not by `SimpleJpaRepository`.

### Practice questions, with answers

**Question 1.** `EventEntity` has these fields: `UUID id`, `UUID venueId`, `String name`,
`OffsetDateTime startsAt`. The `event` table has exactly one row whose name is `Sufi Night`.
You add this method to `public interface EventRepository extends JpaRepository<EventEntity, UUID>`:

```java
List<EventEntity> findByName(String name);
```

You call `events.findByName("Sufi Night")` with the binding log turned on.

- (a) What does the `where` part of the SQL look like?
- (b) What value is bound, and into which `?` (by number)?
- (c) How many events come back?

**Answer.** This was run in a temporary interface with the binding log on.

- (a) `where ee1_0.name=?`. The property `Name` becomes the field `name`, whose column is also
  `name`. There is no keyword, so the condition is "equals".
- (b) The real line was `binding parameter (1:VARCHAR) <- [Sufi Night]`. The text `Sufi Night`
  goes into `?` number 1. `VARCHAR` is the SQL text type Hibernate uses for a Java `String`, in
  the same way it used `UUID` for a `UUID`.
- (c) One event: the real output was `>>> findByName(Sufi Night) -> 1 events`.

**Question 2.** `EventEntity` has the same four fields: `UUID id`, `UUID venueId`,
`String name`, `OffsetDateTime startsAt`. The venue Riverside Arena (id
`11111111-1111-1111-1111-111111111111`) has 4 events, and exactly one event is named
`Sufi Night`. Each line below is in its own interface that
`extends Repository<EventEntity, UUID>`. For each one, choose one of three outcomes and explain
briefly:

- the app fails at startup;
- it fails only when the method is called;
- it works.

The four lines:

- (a) `List<EventEntity> findByName();`
- (b) `List<EventEntity> findByStartsAt(String startsAt);`
- (c) `EventEntity findByVenueId(UUID venueId);`, called with Riverside Arena's id
- (d) `List<EventEntity> findAllByName(String name);`, called with `"Sufi Night"`

**Answer.** All four were run in temporary interfaces.

- (a) **It fails at startup.** The name has one condition (`Name`), but the method has no
  parameter. The real line:

  ```text
  Caused by: java.lang.IllegalStateException: Method public abstract java.util.List com.zenith.data.QuizNoName.findByName() expects at least 1 arguments but only found 0; This leaves an operator of type SIMPLE_PROPERTY for property name unbound
  ```

- (b) **It fails at startup.** The field `startsAt` is an `OffsetDateTime`, but the parameter is
  a `String`, and Hibernate refuses to compare them. The real line:

  ```text
  Caused by: org.hibernate.query.SemanticException: Cannot compare left expression of type 'java.time.OffsetDateTime' with right expression of type 'java.lang.String'
  ```

- (c) **It fails only when called.** The app starts, because a one-row return type is legal.
  The call sends `where ee1_0.venue_id=?` with
  `binding parameter (1:UUID) <- [11111111-1111-1111-1111-111111111111]` and gets 4 rows back.
  The real error:

  ```text
  org.springframework.dao.IncorrectResultSizeDataAccessException: Query did not return a unique result: 4 results were returned
  ```

- (d) **It works.** `All` is only a description. The real SQL was the same as for
  `findByName`, `where ee1_0.name=?`, with
  `binding parameter (1:VARCHAR) <- [Sufi Night]`, and the output was
  `>>> findAllByName(Sufi Night) -> 1 events`.

---

## Lesson 7 — Keywords inside a method name (continues subtask S3)

### Definition

A **keyword** is a word placed right after a property in a derived query's name. It changes
the condition for that property. (A derived query is a repository method whose query Spring
Data builds from its name. A property is a field of the entity, by its Java name.) With no
keyword, the condition is "equals".

This is S3's second method:

```java
List<EventEntity> findByNameContainingIgnoreCase(String part);
```

| Piece | Meaning |
| --- | --- |
| `Name` | The property: the field `name`. |
| `Containing` | The condition: "the name has this text somewhere in it". |
| `IgnoreCase` | "Capital and small letters count as the same." |

Three SQL words you need for the real SQL:

- **`LIKE`** is SQL's pattern match. In a pattern, `%` means "any text, of any length, even
  none". So `name like '%Night'` means "names ending in Night".
- **`upper()`** is an SQL function that turns text into capital letters: `upper('Sufi Night')`
  gives `'SUFI NIGHT'`.
- **`escape '\'`** tells Postgres that in this pattern, a backslash makes the next character
  ordinary. So `\%` means a real percent sign, not "any text".

### Why it is needed

- "Equals" can't power a search box. A user types `night` and expects to see "Monsoon Rock
  Night". Equals would need the full, exact name.
- Users don't type capital letters the way the data stores them.
- Without keywords you would write the `LIKE` SQL by hand. You would also have to add the `%`
  signs yourself and handle a user who types a `%`. Spring Data does both for you.

### Key points

- A keyword goes right after its property: `Name` + `Containing` + `IgnoreCase`.
- `Containing` becomes `like ?`, and Spring Data wraps your value in `%` on both sides.
- `IgnoreCase` becomes `upper()` on both sides, the column and the value.
- Spring Data always adds `escape '\'` and marks a `%` typed by the user as `\%`, so it means a
  real percent sign.
- `Like` is the exception: it sends your pattern exactly as you passed it, with no `%` added.
- Conditions are joined with `And` or `Or`. Each gets its own parameter, in the order of the
  name.
- `IgnoreCase` works only on `String` fields. On anything else, the app does not start.

### How it works, step by step

1. After `By`, Spring Data reads the property (`Name`). Then it looks for a keyword right after
   it (`Containing`), and then for `IgnoreCase`.
2. `Containing` turns the condition into `like ?`. On every call, Spring Data first puts a `\`
   in front of any `%` in your value, then wraps the value in `%` on both sides. So `night` is
   sent as `%night%`.
3. `IgnoreCase` wraps both sides in `upper()`: the column and the value.
   `'Monsoon Rock Night'` becomes `'MONSOON ROCK NIGHT'`, and `'%night%'` becomes `'%NIGHT%'`,
   so they match.
4. Several conditions are joined with `And` or `Or`. Each condition gets its own parameter, in
   the same order as in the name.
5. The startup checks still apply. A keyword that doesn't fit the field's type stops the app
   ("Common mistakes", item 3).

**Keywords, with real SQL from this project.** All of these were run against the `event` table
with the binding log on:

| Name piece | Condition | Real SQL condition | Real bound value |
| --- | --- | --- | --- |
| `Name` (no keyword) | equals | `ee1_0.name=?` | `Sufi Night` |
| `NameContaining` | contains | `ee1_0.name like ? escape '\'` | `%Night%` |
| `NameStartingWith` | starts with | `ee1_0.name like ? escape '\'` | `Open%` |
| `NameLike` | your own pattern | `ee1_0.name like ? escape '\'` | `%Night`, exactly as you passed it |
| `StartsAtAfter` | later than | `ee1_0.starts_at>?` | `2027-01-01T00:00Z` |
| `NameContainingIgnoreCase` | contains, any case | `upper(ee1_0.name) like upper(?) escape '\'` | `%night%` |
| `VenueIdAndNameContainingIgnoreCase` | both conditions | `ee1_0.venue_id=? and upper(ee1_0.name) like upper(?) escape '\'` | `11111111-…` into `?` 1, then `%night%` into `?` 2 |

The real results of those runs:

- `NameStartingWith("Open")` gave Open Mic Night.
- `NameLike("%Night")` gave Monsoon Rock Night, Open Mic Night and Sufi Night.
- `StartsAtAfter(2027-01-01T00:00Z)` gave Sufi Night, Indie Fest and Blues Brunch.
- `VenueIdAndNameContainingIgnoreCase(Riverside Arena, "night")` gave Monsoon Rock Night and
  Sufi Night.

There are many more keywords, such as `Before`, `GreaterThan`, `Between`, `In`, `IsNull`, `Not`
and `Or`. Spring Data JPA's reference documentation lists them all in its table of supported
keywords for query methods.

### Example

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets S3's second method:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);
}
```

`scratch-app/src/main/java/com/zenith/data/KeywordDemo.java` searches for the same word in
two spellings. It is a `CommandLineRunner` (Spring calls `run` once, right after startup),
and `@Profile("keywords")` makes it run only with the profile `keywords`.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 7: one query with two keywords, called with the same word in two spellings.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=keywords
@Component
@Profile("keywords")
class KeywordDemo implements CommandLineRunner {

    private final EventRepository events;

    KeywordDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("night");
        show("NIGHT");
    }

    private void show(String part) {
        List<EventEntity> found = events.findByNameContainingIgnoreCase(part);
        System.out.println(">>> findByNameContainingIgnoreCase(\"" + part + "\") -> " + found.size() + " events");
        for (EventEntity e : found) {
            System.out.println(">>>   " + e.getName());
        }
    }
}
```

The command's last part turns on Hibernate's binding log, which shows each value put into a
`?` (a placeholder in the SQL):

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=keywords -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output, with the log lines shortened:

```text
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        upper(ee1_0.name) like upper(?) escape '\'
... TRACE ... org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [%night%]
>>> findByNameContainingIgnoreCase("night") -> 3 events
>>>   Monsoon Rock Night
>>>   Open Mic Night
>>>   Sufi Night
Hibernate: 
    select
        ...
    where
        upper(ee1_0.name) like upper(?) escape '\'
... TRACE ... org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [%NIGHT%]
>>> findByNameContainingIgnoreCase("NIGHT") -> 3 events
>>>   Monsoon Rock Night
>>>   Open Mic Night
>>>   Sufi Night
```

### Walkthrough

1. `upper(ee1_0.name)` comes from `IgnoreCase`: the column `name`, turned into capital letters.
2. `like upper(?)` comes from `Containing`, plus `IgnoreCase` again on the value's side.
3. `escape '\'` is always added by Spring Data, so it can mark a `%` typed by a user as a real
   percent sign.
4. `binding parameter (1:VARCHAR) <- [%night%]`: you passed `night`, and Spring Data added the
   two `%` signs. `VARCHAR` is the SQL text type Hibernate uses for a Java `String`. The value
   travels as you typed it, and Postgres does the `upper()`.
5. Postgres compares row by row. `upper('Monsoon Rock Night')` is `'MONSOON ROCK NIGHT'`, which
   matches `'%NIGHT%'`. `'JAZZ AT THE NOTE'` does not. So 3 of the 7 events match.
6. `NIGHT` sends `%NIGHT%` and finds the same 3 events. That's `IgnoreCase` at work.
7. The order is Postgres's choice, because there is no `ORDER BY`.

The same search without `IgnoreCase` comes from a real run with a temporary interface, using
`List<EventEntity> findByNameContaining(String part)`:

```text
        ee1_0.name like ? escape '\'
    binding parameter (1:VARCHAR) <- [%night%]
>>> A findByNameContaining("night") -> []
        ee1_0.name like ? escape '\'
    binding parameter (1:VARCHAR) <- [%Night%]
>>> B findByNameContaining("Night") -> [Monsoon Rock Night, Open Mic Night, Sufi Night]
```

There is no `upper()`, so Postgres's `LIKE` compares letters exactly. `night` is not `Night`, so
A finds nothing.

### Important terms

| Term | Meaning |
| --- | --- |
| **Keyword** | A word right after a property in a derived query's name that changes the condition, such as `Containing`, `StartingWith`, `After` or `IgnoreCase`. |
| **`LIKE`** | SQL's pattern match. `%` in the pattern means "any text, even none". It is case-sensitive in Postgres. |
| **`upper()`** | An SQL function that turns text into capital letters. |
| **`escape '\'`** | Says that a backslash in the pattern makes the next character ordinary, so `\%` is a real percent sign. |
| **Escaping** | Marking a special character so it counts as an ordinary one. Spring Data turned the user's `%` into `\%`. |
| **`VARCHAR`** | The SQL text type Hibernate uses when it binds a Java `String`. |

### Where it is used

- Search boxes: "events whose name contains what the user typed" is
  `findByNameContainingIgnoreCase`.
- Filters: "events after a date" is `findByStartsAtAfter`.
- Combined filters: "this venue's events whose name contains X" is
  `findByVenueIdAndNameContainingIgnoreCase`.
- One limit to know: a pattern that starts with `%` can't use a normal index. Postgres reads
  every row and checks it. That's fine for small tables like this one. Big tables need a special
  kind of index or a search tool, which is outside this task.

### Edge cases

- **An empty search text finds everything.** `findByNameContainingIgnoreCase("")` sent `[%%]`
  and returned all 7 events.
- **A `%` typed by the user is a real percent sign.** `"%"` was sent as `[%\%%]`, and
  `"%night%"` as `[%\%night\%%]`. Both returned nothing.
- **`Like` adds nothing.** `findByNameLike("Night")` sent `[Night]`, which means "exactly
  Night", and returned nothing. `findByNameLike("%Night")` returned the 3 names ending in Night.
- **Two conditions, two parameters, in name order.**
  `findByVenueIdAndNameContainingIgnoreCase` bound the UUID into `?` 1 and `%night%` into `?` 2.

### Common mistakes — with the real errors

1. **Forgetting `IgnoreCase`.** `findByNameContaining("night")` sent `[%night%]` without
   `upper()` and returned nothing, while `"Night"` returned 3 events. If people will type into
   it, add `IgnoreCase`.
2. **Adding the `%` yourself with `Containing`.** A real run of
   `events.findByNameContainingIgnoreCase("%night%")`:

   ```text
           upper(ee1_0.name) like upper(?) escape '\'
       binding parameter (1:VARCHAR) <- [%\%night\%%]
   >>> H findByNameContainingIgnoreCase("%night%") -> []
   ```

   Spring Data marked your two `%` signs as real percent signs (`\%`), then added its own. So
   the search meant "names containing the text `%night%`", and there are none. With `Like` it
   is the other way round: Spring Data adds nothing, and `findByNameLike("Night")` sent
   `[Night]` and returned nothing.
3. **`IgnoreCase` on a field that isn't text.** This is a temporary interface:

   ```java
   interface UuidIgnoreCase extends Repository<EventEntity, UUID> {

       List<EventEntity> findByVenueIdIgnoreCase(UUID venueId);   // venueId is a UUID, not text
   }
   ```

   The app refused to start (shortened):

   ```text
   ... Found 3 JPA repository interfaces.
   ... Application run failed
   org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'uuidIgnoreCase' ... Could not create query for public abstract java.util.List com.zenith.data.UuidIgnoreCase.findByVenueIdIgnoreCase(java.util.UUID); ...
   ...
   Caused by: java.lang.IllegalStateException: Unable to ignore case of java.util.UUID types, the property 'venueId' must reference a String
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Error creating bean with name 'uuidIgnoreCase'` | A bean is an object Spring creates and keeps for you. Building the proxy for this interface failed. |
   | `Unable to ignore case of java.util.UUID types` | Capital and small letters only exist in text. A `UUID` is not text. |
   | `the property 'venueId' must reference a String` | `IgnoreCase` works only on `String` fields. |

4. **Expecting an empty search to find nothing.** `findByNameContainingIgnoreCase("")` sent
   `[%%]` and returned all 7 events, because "contains nothing" is true for every name. If an
   empty search box should show no results, check for empty text before calling the
   repository.

### Link to the task

S3 says: "Write three derived queries: `findByVenueId`, `findByNameContainingIgnoreCase`,
`existsBySlug`. Read their SQL. Then misspell a property and read the startup failure." This
lesson covers the second method:

- `findByNameContainingIgnoreCase` became `upper(ee1_0.name) like upper(?) escape '\'`;
- the value is bound as `%night%`.

Still to come in S3 are `existsBySlug`, which needs a new `slug` column added by a migration,
and the misspelled property.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` has `findByVenueId`;
- Postgres runs in the Docker container `pg`, with the 7 seed events loaded.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the method.** Replace
`scratch-app/src/main/java/com/zenith/data/EventRepository.java` with the 18-line version from
the example.

**Step 3 — add the demo.** Create
`scratch-app/src/main/java/com/zenith/data/KeywordDemo.java` with the content from the
example.

**Step 4 — run it with the binding log.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=keywords -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see:

- `upper(ee1_0.name) like upper(?) escape '\'`;
- `binding parameter (1:VARCHAR) <- [%night%]`, then `-> 3 events` with Monsoon Rock Night,
  Open Mic Night and Sufi Night;
- the same again with `[%NIGHT%]`.

If something goes wrong:

- `Connection to localhost:5432 refused` means Postgres is not running. Run `docker start pg`.
- `-> 0 events` for both searches means the seed data is not loaded.

**Step 5 — type the wildcards yourself.** In `KeywordDemo.java`, change `show("NIGHT");` to
`show("%night%");` and run the same command. The second search should show
`binding parameter (1:VARCHAR) <- [%\%night\%%]` and `-> 0 events`. Change it back to
`show("NIGHT");`.

**Step 6 — break it with `IgnoreCase` on a UUID.** Create a temporary file,
`scratch-app/src/main/java/com/zenith/data/UuidIgnoreCase.java`:

```java
package com.zenith.data;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface UuidIgnoreCase extends Repository<EventEntity, UUID> {

    List<EventEntity> findByVenueIdIgnoreCase(UUID venueId);   // venueId is a UUID, not text
}
```

Run `mvn -q spring-boot:run -Dspring-boot.run.profiles=keywords`. You should see:

- `Application run failed`;
- near the bottom,
  `Caused by: java.lang.IllegalStateException: Unable to ignore case of java.util.UUID types, the property 'venueId' must reference a String`;
- no `>>>` lines.

Delete `UuidIgnoreCase.java` and run again. The two searches print as before.

**Done when:**

- you have seen `upper(ee1_0.name) like upper(?) escape '\'` with `[%night%]` and `[%NIGHT%]`,
  both giving the same 3 events;
- you have seen `[%\%night\%%]` give 0 events, and changed the demo back;
- you have seen the `Unable to ignore case of java.util.UUID types` startup failure, deleted the
  file, and the app runs again.

### Summary for revision

- A keyword after a property changes its condition. With none, the condition is "equals".
- `Containing` becomes `like ?` with the value sent as `%value%`. `StartingWith` sends
  `value%`. `Like` sends your pattern exactly as given.
- `IgnoreCase` becomes `upper()` on the column and on the value. It works only on `String`
  fields.
- Spring Data adds `escape '\'` and turns a user's `%` into `\%`, so it can't act as "any
  text".
- `And` and `Or` join conditions. Parameters are bound in the order of the name.
- An empty search text finds every row.
- A leading `%` means Postgres reads every row. That's fine for small tables.

### Practice questions, with answers

**Question 1.** `EventRepository` has
`List<EventEntity> findByNameContainingIgnoreCase(String part);`, and `name` is a `String`
field. The 7 event names are: Monsoon Rock Night, Jazz at the Note, Open Mic Night, Sufi Night,
Indie Fest, Blues Brunch, Classical Evening. You call
`events.findByNameContainingIgnoreCase("jazz")` with the binding log on.

- (a) What does the `where` part of the SQL look like?
- (b) What value is bound to the `?`?
- (c) Which events come back?

**Answer.** This call was run with the binding log on.

- (a) `where upper(ee1_0.name) like upper(?) escape '\'`. `IgnoreCase` gives the two
  `upper()` calls, and `Containing` gives `like`.
- (b) The real line was `binding parameter (1:VARCHAR) <- [%jazz%]`. Spring Data added a `%` on
  each side of `jazz`.
- (c) One event: Jazz at the Note. The real output was
  `>>> J findByNameContainingIgnoreCase("jazz") -> [Jazz at the Note]`.
  `upper('Jazz at the Note')` is `'JAZZ AT THE NOTE'`, which matches `'%JAZZ%'`.

**Question 2.** Use the same 7 event names. Each method below is declared in an interface that
`extends Repository<EventEntity, UUID>`, and `name` is a `String` field. For each call, say
what value is bound to the `?` and how many events come back, and explain why.

- (a) `findByNameContaining("night")`
- (b) `findByNameStartingWith("Open")`
- (c) `findByNameContainingIgnoreCase("")`
- (d) `findByNameLike("Night")`

**Answer.** All four were run in a temporary interface with the binding log on.

- (a) It binds `[%night%]` and returns **0** events. There is no `IgnoreCase`, so the SQL is
  `ee1_0.name like ? escape '\'` with no `upper()`, and Postgres's `LIKE` is case-sensitive.
  The names say `Night`, with a capital N.
- (b) It binds `[Open%]` and returns **1** event, Open Mic Night. `StartingWith` adds `%` only at
  the end.
- (c) It binds `[%%]` and returns **all 7** events. "Contains the empty text" is true for every
  name.
- (d) It binds `[Night]` and returns **0** events. `Like` adds no `%`, so the pattern means "the
  name is exactly `Night`", and no event has that name.

---

## Lesson 8 — A yes/no query: `existsBySlug` (continues subtask S3)

### Definition

Some words first:

- A **slug** is an event's name written so it can go in a web address: small letters, with
  spaces turned into `-`. "Sufi Night" becomes `sufi-night`, as in `/events/sufi-night`.
- A **derived query** is a repository method whose query Spring Data builds from the method's
  name. You write no SQL and no method body.
- The **subject** is the first word of a derived query's name. It says what kind of answer you
  want back. `find` means "give me the rows".
- A **property** is a field of the entity, written with its Java name.

`existsBySlug` is a derived query whose subject is `exists`. It asks the database one yes/no
question: "Is there at least one event with this slug?" The answer is a Java `boolean`: `true`
or `false`.

```java
boolean existsBySlug(String slug);
```

| Piece | Meaning |
| --- | --- |
| `exists` | The subject: "answer yes or no". |
| `By` | "The condition starts here." |
| `Slug` | The property `slug`. No keyword follows it, so the condition is "equals". |
| `boolean` | The return type: `true` or `false`. |

### Why it is needed

- Many decisions in an app only need a yes or a no. "Is the web address `indie-fest` already
  used by an event?" "Is this email already registered?"
- You could load the event and check whether you got one. Or you could count the events and
  check for more than zero. Both work, but they make the database do more than the question
  needs, and the code hides what you really mean.
- `existsBySlug` says exactly what you mean. Spring Data turns it into SQL that reads only the
  id and stops at the first matching row.

The `event` table had no `slug` column, so it first needs a **migration**. A migration is a
numbered SQL file. **Flyway** is the tool that runs these files when the app starts: each file
once, in number order. It records every file it ran in its table `flyway_schema_history`.

### Key points

- The subject `exists` plus the return type `boolean` makes a yes/no query.
- The SQL reads only the id and asks for at most one row: `fetch first ? rows only`, with `1`
  bound into that `?`.
- A row came back, so the method returns `true`. No row came back, so it returns `false`.
- No keyword after the property means "equals", and equals is exact: `Sufi-Night` does not
  match `sufi-night`.
- A new field needs its column. Here the column comes from a new migration,
  `V3__add_event_slug.sql`.
- At startup, Flyway runs new migrations first. Then Hibernate checks the entity against the
  table. So the field and its migration can go in the same change.
- The unique constraint is the real guard against two events with the same slug. `exists` is
  for giving a friendly answer before saving.

### How it works, step by step

**Part 1: the migration adds the column.** The file `V3__add_event_slug.sql` does three things:

1. `alter table event add column slug text;` adds the column. Existing rows get `null` (no
   value) in it.
2. `update event set slug = lower(replace(name, ' ', '-'));` fills the column for every
   existing event. There is no `where`, so it changes all 7 rows. `replace(name, ' ', '-')`
   turns each space into `-`, and `lower(...)` makes all letters small.
3. `alter table event add constraint event_slug_unique unique (slug);` adds a **unique
   constraint**. That is a rule Postgres enforces: no two rows may have the same slug. To check
   the rule quickly, Postgres also builds an **index** on `slug`. An index is a sorted lookup
   list that lets Postgres find a slug without reading every row.

The column allows `null`, because `seed-dev-data.sql` inserts events without a slug. With a
`not null` rule, those inserts would fail if the seed file were ever loaded again.

**Part 2: the Java side.** The entity `EventEntity` gets a field `private String slug;`, which
maps to the column `slug`. The repository gets `boolean existsBySlug(String slug);`.

**At startup, three things happen in this order** (the log shows it):

1. Flyway compares the migration files with `flyway_schema_history` and runs any file it hasn't
   run yet. V3 runs.
2. Hibernate checks the entity against the table. The setting `ddl-auto: validate` in
   `application.yml` means "check that every field has its column, and stop the app if one is
   missing". The column exists now, so the check passes.
3. Spring Data reads the name `existsBySlug` and prepares its query.

**When you call it**, Spring Data runs this SQL:

```sql
select ee1_0.id from event ee1_0 where ee1_0.slug=? fetch first ? rows only
```

`fetch first ? rows only` is standard SQL for "give me at most this many rows". Spring Data
puts `1` there. If a row comes back, the method returns `true`. If none comes back, it returns
`false`.

### Example

The migration is a new file,
`scratch-app/src/main/resources/db/migration/V3__add_event_slug.sql`:

```sql
-- V3__add_event_slug.sql
-- A slug is the event's name written for a web address: small letters, spaces turned into "-".
-- 'Sufi Night' -> 'sufi-night', as in /events/sufi-night

alter table event add column slug text;

update event set slug = lower(replace(name, ' ', '-'));

alter table event add constraint event_slug_unique unique (slug);

-- No "not null" yet: seed-dev-data.sql inserts events without a slug, and those inserts would fail.
```

`scratch-app/src/main/java/com/zenith/data/EventEntity.java` gets one field and one getter:

```java
package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")   // without this, Hibernate guesses the table name "event_entity"
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getSlug() { return slug; }
}
```

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets the yes/no method:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);
}
```

`scratch-app/src/main/java/com/zenith/data/ExistsDemo.java` asks three questions. It is a
`CommandLineRunner`, which means Spring calls its `run` method once, right after startup.
`@Profile("exists")` makes it run only when you start the app with the profile `exists`.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 8: a yes/no question to the database - "is there an event with this slug?"
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=exists
@Component
@Profile("exists")
class ExistsDemo implements CommandLineRunner {

    private final EventRepository events;

    ExistsDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        check("sufi-night");
        check("rain-dance");
        check("Sufi-Night");
    }

    private void check(String slug) {
        boolean taken = events.existsBySlug(slug);
        System.out.println(">>> existsBySlug(\"" + slug + "\") -> " + taken);
    }
}
```

The last part of this command turns on Hibernate's **binding log**, which shows each value put
into a `?`:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output of the first run, shortened:

```text
... Successfully validated 3 migrations (execution time 00:00.024s)
... Current version of schema "public": 2
... Migrating schema "public" to version "3 - add event slug"
... Successfully applied 1 migration to schema "public", now at version v3 (execution time 00:00.012s)
...
Hibernate: 
    select
        ee1_0.id 
    from
        event ee1_0 
    where
        ee1_0.slug=? 
    fetch
        first ? rows only
... binding parameter (1:VARCHAR) <- [sufi-night]
... binding parameter (2:INTEGER) <- [1]
>>> existsBySlug("sufi-night") -> true
Hibernate: 
    (the same SQL again)
... binding parameter (1:VARCHAR) <- [rain-dance]
... binding parameter (2:INTEGER) <- [1]
>>> existsBySlug("rain-dance") -> false
Hibernate: 
    (the same SQL again)
... binding parameter (1:VARCHAR) <- [Sufi-Night]
... binding parameter (2:INTEGER) <- [1]
>>> existsBySlug("Sufi-Night") -> false
```

The new column in the database:

```bash
docker exec pg psql -U postgres -d zenith -c "select name, slug from event order by starts_at;"
```

```text
        name        |        slug        
--------------------+--------------------
 Monsoon Rock Night | monsoon-rock-night
 Jazz at the Note   | jazz-at-the-note
 Classical Evening  | classical-evening
 Open Mic Night     | open-mic-night
 Sufi Night         | sufi-night
 Blues Brunch       | blues-brunch
 Indie Fest         | indie-fest
(7 rows)
```

Flyway's table after the run:

```text
 installed_rank | version |    description    | success 
----------------+---------+-------------------+---------
              1 | 1       | baseline          | t
              2 | 2       | add doors open at | t
              3 | 3       | add event slug    | t
(3 rows)
```

And `\d event` in psql now lists the column and the index made by the unique constraint
(shortened):

```text
 slug          | text                     |           |          | 
Indexes:
    "event_pkey" PRIMARY KEY, btree (id)
    "event_slug_unique" UNIQUE CONSTRAINT, btree (slug)
```

### Walkthrough

1. `Current version of schema "public": 2` means Flyway's table said V1 and V2 had already
   run. `V3__add_event_slug.sql` was new, so Flyway ran it and wrote a third row in
   `flyway_schema_history`.
2. Flyway finished before Hibernate's check started. So the check found the `slug` column, and
   the app started.
3. `select ee1_0.id`: Spring Data asks only for the id. It doesn't need the name or the date to
   answer yes or no.
4. `where ee1_0.slug=?` with `(1:VARCHAR) <- [sufi-night]` is an exact "equals" on the slug.
   `VARCHAR` is the SQL text type Hibernate uses for a Java `String`.
5. `fetch first ? rows only` with `(2:INTEGER) <- [1]` means at most one row, so Postgres can
   stop as soon as it finds one. `INTEGER` means a whole number.
6. For `sufi-night`, one id came back, so the method returned `true`. For `rain-dance`, nothing
   came back, so it returned `false`.
7. For `Sufi-Night`, it returned `false`. Equals compares letters exactly, and `S` is not `s`.
8. On the next run, Flyway printed `Current version of schema "public": 3` and
   `Schema "public" is up to date. No migration necessary.` It never runs the same migration
   twice.

**Other ways to ask the same question.** These methods were run in a temporary interface, now
deleted, with the binding log on:

```java
interface SlugCompare extends Repository<EventEntity, UUID> {

    long countBySlug(String slug);

    Optional<EventEntity> findBySlug(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByVenueId(UUID venueId);
}
```

All of them used the slug `sufi-night`, except `existsById`, which takes an id:

| Method | Real SQL (shortened) | Real result |
| --- | --- | --- |
| `boolean existsBySlug(String slug)` | `select ee1_0.id from event ee1_0 where ee1_0.slug=? fetch first ? rows only` | `true` |
| `long countBySlug(String slug)` | `select count(ee1_0.id) from event ee1_0 where ee1_0.slug=?` | `1` |
| `Optional<EventEntity> findBySlug(String slug)` | `select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id from event ee1_0 where ee1_0.slug=?` | `Optional[Sufi Night]` |
| `existsById(UUID id)`, which every `JpaRepository` already has | `select count(*) from event ee1_0 where ee1_0.id=?` | `true` for id `aaaaaaaa-0000-0000-0000-000000000005` |

- `Optional` is a Java box that holds either one object or nothing. For `rain-dance`,
  `findBySlug` returned `Optional.empty`.
- `count` has to find every match before it can give the number.
- `find` reads every column and builds a Java object. Its list now includes `ee1_0.slug`: once
  the entity has the field, every query that loads whole events also reads that column.
- `exists` reads one id and stops.
- `existsById` is not built from a method name. Its code is written by hand inside Spring Data,
  so its SQL is different (`count(*)`). The answer is the same, and an id matches at most one
  row anyway.

### Important terms

| Term | Meaning |
| --- | --- |
| **Slug** | An event's name written for a web address: small letters, spaces turned into `-`. |
| **Subject** | The first word of a derived query's name. `find` gives rows, `count` gives a number, `exists` gives `true` or `false`. |
| **Migration** | A numbered SQL file, such as `V3__add_event_slug.sql`, that Flyway runs once and records. |
| **`flyway_schema_history`** | Flyway's table. One row per migration it has run. |
| **Unique constraint** | A rule Postgres enforces: no two rows may have the same value in this column. |
| **Index** | A sorted lookup list that lets Postgres find a value without reading every row. A unique constraint creates one. |
| **`fetch first ? rows only`** | Standard SQL for "give me at most this many rows". |
| **`Optional`** | A Java box that holds either one object or nothing. |
| **`null`** | "No value" in a column. |

### Where it is used

- **Before creating an event:** "Is `indie-fest` taken?" If yes, use another slug, such as
  `indie-fest-2`.
- **Sign-up forms:** `existsByEmail` lets you show "This email is already registered".
- **Checking input:** "Does this venue exist?" Call `existsById(venueId)` before saving an event
  for it.

One warning: a check alone does not protect the data. Two requests can ask at the same moment.
Both get `false`, and both try to save the same slug. The unique constraint is the real guard,
because Postgres refuses the second row. A real try in psql:

```bash
docker exec pg psql -U postgres -d zenith -c "update event set slug = 'sufi-night' where name = 'Indie Fest';"
```

```text
ERROR:  duplicate key value violates unique constraint "event_slug_unique"
DETAIL:  Key (slug)=(sufi-night) already exists.
```

Nothing changed. So use `exists` to give a friendly message, and the unique constraint to keep
the data correct.

### Edge cases

- **Equals is exact.** `existsBySlug("Sufi-Night")` returned `false`.
  `existsBySlugIgnoreCase("Sufi-Night")` used `where upper(ee1_0.slug)=upper(?)` and returned
  `true`.
- **A matching venue with many events still reads one row.** `existsByVenueId(Riverside Arena)`
  used `where ee1_0.venue_id=? fetch first ? rows only` and returned `true`, although that venue
  has 4 events. For Lakeside Hall, which has none, it returned `false`.
- **The built-in `existsById` uses different SQL**, `select count(*) ... where ee1_0.id=?`. The
  answer is the same.
- **A new field changes every query that loads whole events.** After V3, `findBySlug`'s select
  list included `ee1_0.slug`. So do `findAll()` and the other `find...` methods.
- **Rows inserted without a slug get `null`.** The column has no `not null` rule. If the
  database is ever rebuilt and `seed-dev-data.sql` is loaded after V3 has run, those events have
  no slug, and `existsBySlug` finds nothing for them. Fill them with the same expression V3
  used:
  `update event set slug = lower(replace(name, ' ', '-')) where slug is null;`.

### Common mistakes — with the real errors

1. **Adding the field without the migration.** With the field, the method and the demo in
   place but no V3 file, the app refused to start (real output, shortened):

   ```text
   ... Successfully validated 2 migrations (execution time 00:00.019s)
   ... Current version of schema "public": 2
   ... Schema "public" is up to date. No migration necessary.
   ...
   ... Application run failed
   ...
   Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing column [slug] in table [event]
   ```

   | Part of the message | Plain meaning |
   | --- | --- |
   | `Successfully validated 2 migrations` | Flyway found only V1 and V2. |
   | `No migration necessary` | It had nothing new to run. |
   | `Schema-validation` | Hibernate's startup check, turned on by `ddl-auto: validate`. |
   | `missing column [slug] in table [event]` | The entity has a field `slug`, but the table `event` has no column `slug`. |

   The fix is to add the migration. A field and its migration belong in the same change.
2. **A file name Flyway doesn't recognise.** Flyway reads only files named
   `V<number>__<description>.sql`, with **two** underscores. A temporary file named
   `V4_check_name.sql` (one underscore, containing `select 1;`) was put in the migration folder,
   and the app was started. Flyway printed `Successfully validated 3 migrations` and
   `Schema "public" is up to date. No migration necessary.`, with no warning. It skipped the
   file. So if the new file is named `V3_add_event_slug.sql`, Flyway skips it, and you get
   exactly the error from mistake 1.
3. **Expecting capital letters not to matter.** `existsBySlug("Sufi-Night")` returned `false`.
   `existsBySlugIgnoreCase` used `where upper(ee1_0.slug)=upper(?) fetch first ? rows only`
   (`upper()` turns text into capital letters) and returned `true`. There is a simpler fix:
   slugs are stored in small letters, so call `existsBySlug(slug.toLowerCase())`. That keeps the
   index on `slug` useful, because the index can't help with `upper(slug)`.
4. **Loading or counting to answer yes or no.** `findBySlug(slug).isPresent()` reads all 5
   columns and builds an object. `countBySlug(slug) > 0` counts every match. Both give the
   right answer, but `existsBySlug` does less work and says what you mean.

### Link to the task

S3 says: "Write three derived queries: `findByVenueId`, `findByNameContainingIgnoreCase`,
`existsBySlug`. Read their SQL. Then misspell a property and read the startup failure."

- This lesson does the third method, `existsBySlug`. Its SQL is
  `select ee1_0.id from event ee1_0 where ee1_0.slug=? fetch first ? rows only`.
- Its `slug` column comes from the new migration, `V3__add_event_slug.sql`.
- Still to do in S3: misspell a property and read the startup failure.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` has `findByVenueId` and `findByNameContainingIgnoreCase`;
- Postgres runs in the Docker container `pg`, with the 7 seed events loaded;
- `src/main/resources/db/migration` holds `V1__baseline.sql` and `V2__add_doors_open_at.sql`.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — break it first.** Do this before you create the migration file.

- Replace `scratch-app/src/main/java/com/zenith/data/EventEntity.java` with the 30-line version
  from the example.
- Replace `scratch-app/src/main/java/com/zenith/data/EventRepository.java` with the 21-line
  version from the example.
- Create `scratch-app/src/main/java/com/zenith/data/ExistsDemo.java` with the content from the
  example.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists
```

You should see `Successfully validated 2 migrations` and
`Schema "public" is up to date. No migration necessary.`, then `Application run failed` and
`Schema-validation: missing column [slug] in table [event]`. No `>>>` lines appear.

**Step 3 — add the migration.** Create
`scratch-app/src/main/resources/db/migration/V3__add_event_slug.sql` with the content from the
example. The name is a capital `V`, then `3`, then two underscores, then `add_event_slug.sql`.
Run with the binding log:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see:

- `Migrating schema "public" to version "3 - add event slug"`, then
  `Successfully applied 1 migration to schema "public", now at version v3`;
- the SQL ending in `fetch first ? rows only`, with
  `binding parameter (1:VARCHAR) <- [sufi-night]` and `binding parameter (2:INTEGER) <- [1]`;
- these three lines:

```text
>>> existsBySlug("sufi-night") -> true
>>> existsBySlug("rain-dance") -> false
>>> existsBySlug("Sufi-Night") -> false
```

If the log still says `Successfully validated 2 migrations` and `missing column [slug]`, Flyway
did not see your file. Check the two underscores, the `.sql` ending, and the folder.

**Step 4 — run it again.** Use the same command. You should see
`Current version of schema "public": 3` and
`Schema "public" is up to date. No migration necessary.`, then the same three `>>>` lines. This
shows Flyway ran V3 only once.

**Step 5 — look in the database.**

```bash
docker exec pg psql -U postgres -d zenith -c "select name, slug from event order by starts_at;"
docker exec pg psql -U postgres -d zenith -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

The first command shows the 7 events with their slugs, as in the example. The second shows 3
rows, and the last one is `3 | 3 | add event slug | t`.

**Step 6 — test the unique rule.**

```bash
docker exec pg psql -U postgres -d zenith -c "update event set slug = 'sufi-night' where name = 'Indie Fest';"
```

You should see
`ERROR:  duplicate key value violates unique constraint "event_slug_unique"` and
`DETAIL:  Key (slug)=(sufi-night) already exists.` Nothing changes.

**Done when:**

- you have seen `missing column [slug] in table [event]` before the migration existed;
- you have seen Flyway apply V3 once, and say "up to date" on the next run;
- you have seen the SQL with `fetch first ? rows only` and the three lines `true`, `false`,
  `false`;
- psql shows 7 slugs and 3 history rows, and refused the duplicate slug.

### Summary for revision

- The subject `exists` with a `boolean` return type makes a yes/no query.
- Its SQL reads only the id and fetches at most 1 row: `fetch first ? rows only`.
- Equals is exact. `IgnoreCase` adds `upper()`, but storing slugs in small letters and
  lower-casing the input is simpler.
- A new entity field needs its column. Add it with a new migration, named `V<n>__<text>.sql`
  with two underscores.
- At startup, Flyway runs new migrations before Hibernate's `validate` check.
- A migration runs once. Later starts say "up to date".
- The unique constraint keeps the data correct, and `exists` gives the friendly answer.

### Practice questions, with answers

**Question 1.** `EventRepository extends JpaRepository<EventEntity, UUID>`. `EventEntity` has a
field `private UUID venueId;`, mapped to the column `venue_id`. A teammate adds this method to
`EventRepository`:

```java
boolean existsByVenueId(UUID venueId);
```

The venue Lakeside Hall has id `33333333-3333-3333-3333-333333333333` and no events. The venue
Riverside Arena has id `11111111-1111-1111-1111-111111111111` and 4 events. The binding log is
on.

- (a) What do the `where` part and the last line of the SQL look like?
- (b) For the Lakeside Hall call, which two values are bound, and what is each one for?
- (c) What do the Lakeside Hall call and the Riverside Arena call return?

**Answer.** These calls were run in a temporary interface with the binding log on.

- (a) The real SQL was
  `select ee1_0.id from event ee1_0 where ee1_0.venue_id=? fetch first ? rows only`. So the
  `where` part is `where ee1_0.venue_id=?`, and the last line is `fetch first ? rows only`.
  It is the same shape as `existsBySlug`. Only the property changed.
- (b) `binding parameter (1:UUID) <- [33333333-3333-3333-3333-333333333333]` is the venue id
  for the condition. `binding parameter (2:INTEGER) <- [1]` is the row limit: at most one row.
- (c) Lakeside Hall returned `false`: no row came back. Riverside Arena returned `true`: one row
  came back. Postgres stopped after the first match, even though that venue has 4 events.

**Question 2.** A teammate's database is at Flyway version 2 and has no `slug` column. They add
`private String slug;` to `EventEntity` and `boolean existsBySlug(String slug);` to
`EventRepository`. They save the migration as
`src/main/resources/db/migration/V3_add_event_slug.sql`. The app has `ddl-auto: validate`.
When they start the app:

- (a) What do Flyway's log lines say?
- (b) What error stops the app?
- (c) What is the fix?

**Answer.**

- (a) `Successfully validated 2 migrations`, `Current version of schema "public": 2` and
  `Schema "public" is up to date. No migration necessary.` The file name has one underscore, so
  Flyway does not see it as a migration and skips it, with no warning. A real run with a
  temporary one-underscore file showed exactly this.
- (b) Hibernate's startup check stops the app: `Application run failed`, with
  `Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing column [slug] in table [event]`.
  This is the same output as a real run with no V3 file at all, because to Flyway the file
  does not exist.
- (c) Rename the file to `V3__add_event_slug.sql`, with two underscores, and start again.
  Flyway prints `Migrating schema "public" to version "3 - add event slug"`, then Hibernate's
  check finds the column, and the app starts.

---

## Lesson 9 — Break it: a misspelled property (finishes subtask S3)

### Definition

In this lesson you break the app on purpose. You misspell a property in a derived query's name,
then read what happens.

Some words first:

- A **derived query** is a repository method whose query Spring Data builds from the method's
  name.
- A **property** is a field of the entity, written with its Java name. `EventEntity` has five:
  `id`, `venueId`, `name`, `startsAt` and `slug`.
- The **subject** is the first word of a derived query's name. `count` means "how many rows
  match".
- **Failing early** means a mistake shows up at the first possible moment. Here, that moment is
  while the app starts, before any user can reach it.

This is the misspelled method:

```java
long countByVenuId(UUID venueId);   // the field is venueId, with an "e"
```

`VenuId` should name the property `venueId`, but the `e` is missing. The app refuses to start,
and the error says:

```text
No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'
```

### Why it is needed

- The Java compiler can't catch this. To the compiler, `countByVenuId` is just a method name,
  so the code compiles with no error. Only Spring Data knows the name must match a field.
- So Spring Data checks every derived query while the app starts, even if no code ever calls
  it. A wrong name stops the app right there.
- That's failing early, and it helps you:
  - you can't miss the mistake, because the app doesn't start;
  - you find it minutes after you made it, while you still remember the change;
  - the message names the method and the bad property, and often suggests the fix;
  - the mistake can't reach a user.
- The opposite is **failing late**. Put the same typo inside an SQL string you wrote yourself,
  and nothing checks it at startup. The app starts fine. The error waits until that exact line
  runs, which might be days later, in front of a user. Part B of the example shows this with a
  real run.

### Key points

- A misspelled property in a derived query's name compiles fine and stops the app at startup.
- The check runs for every derived query, even ones no code calls.
- The original error is in the last `Caused by:` line. Read from the bottom up.
- `Did you mean '...'` appears when a real field name is only one or two letters different.
  With no close field, there is no suggestion.
- The top line names the bean Spring was building when it hit the problem. That bean may be
  innocent.
- The same typo inside a hand-written SQL string is not checked at startup. It fails only when
  that line runs.
- Lazy startup (`spring.data.jpa.repositories.bootstrap-mode=lazy`) moves the check to the first
  call. The default mode checks before the app starts.

### How it works, step by step

At startup, Spring Data builds a **proxy** for each repository interface. A proxy is the object
that stands in for your interface and does the real work. Spring keeps it as a **bean**, which
is any object Spring creates and keeps for you. For each derived query method, Spring Data does
this:

1. It reads the subject (`count`) and finds `By`.
2. It takes the rest of the name, `VenuId`, makes the first letter small (`venuId`), and looks
   for that field in `EventEntity`.
3. There is no field `venuId`. So it looks for a real field whose name is only one or two
   letters different. It finds `venueId`, and adds `Did you mean 'venueId'`. If no field is
   that close, there is no suggestion. For example, `findByCity` gave only
   `No property 'city' found for type 'EventEntity'`.
4. It throws an error. The proxy can't be built, so the `eventRepository` bean fails.
5. Every bean that needs `EventRepository` fails too, and Spring stops the whole app. The line
   `Started DataApplication` never appears.

The error comes wrapped in layers, and each layer adds what it was doing. The original error
sits at the bottom, after the last `Caused by:` (the part of an error report that shows the
error it wraps). So **read the last `Caused by:` line first**.

### Example

**Part A: the typo.** For this run,
`scratch-app/src/main/java/com/zenith/data/EventRepository.java` had two extra lines at the
end:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);

    // Lesson 9: a typo on purpose. The field is venueId, but the name says VenuId.
    long countByVenuId(UUID venueId);
}
```

The app was started with the profile `exists`. Its demo, `ExistsDemo`, only calls
`existsBySlug`. Nothing calls `countByVenuId`.

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists
```

Real output, shortened (`at ...` stands for many lines that show where the code was running):

```text
... Found 2 JPA repository interfaces.
... Current version of schema "public": 3
... Schema "public" is up to date. No migration necessary.
...
... Application run failed

org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'existsDemo' defined in file [.../ExistsDemo.class]: Unsatisfied dependency expressed through constructor parameter 0: Error creating bean with name 'eventRepository' ...: Could not create query for public abstract long com.zenith.data.EventRepository.countByVenuId(java.util.UUID); Reason: ... No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'
    at ...
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'eventRepository' ...
    at ...
Caused by: org.springframework.data.repository.query.QueryCreationException: Could not create query for public abstract long com.zenith.data.EventRepository.countByVenuId(java.util.UUID); ...
    at ...
Caused by: java.lang.IllegalArgumentException: Failed to create query for method public abstract long com.zenith.data.EventRepository.countByVenuId(java.util.UUID); No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'
    at ...
Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'
    at ...
```

There is no `Started DataApplication` line and no `>>>` line.

**The fix:** change `countByVenuId` to `countByVenueId`. Real output after the fix:

```text
... Found 2 JPA repository interfaces.
... Started DataApplication in 1.888 seconds (process running for 2.05)
...
>>> existsBySlug("sufi-night") -> true
>>> existsBySlug("rain-dance") -> false
>>> existsBySlug("Sufi-Night") -> false
```

**Part B: the same typo in SQL you write yourself.** `JdbcClient` is Spring's helper for running
SQL that you write as a string. This temporary demo,
`scratch-app/src/main/java/com/zenith/data/LateFailureDemo.java`, first calls the fixed derived
query. Then it runs a hand-written SQL string with the same mistake, `venu_id` instead of
`venue_id`:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 9 (temporary): the same typo, but inside an SQL string. Nothing checks it until the line runs.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=late
@Component
@Profile("late")
class LateFailureDemo implements CommandLineRunner {

    private static final UUID RIVERSIDE_ARENA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final EventRepository events;
    private final JdbcClient jdbc;

    LateFailureDemo(EventRepository events, JdbcClient jdbc) {
        this.events = events;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> countByVenueId(Riverside Arena) -> " + events.countByVenueId(RIVERSIDE_ARENA));

        System.out.println(">>> now the hand-written SQL, with venu_id instead of venue_id");
        try {
            Long count = jdbc.sql("select count(*) from event where venu_id = ?")
                    .param(RIVERSIDE_ARENA)
                    .query(Long.class)
                    .single();
            System.out.println(">>> count: " + count);
        } catch (DataAccessException e) {
            System.out.println(">>> it failed only now, when the line ran: " + e.getClass().getSimpleName());
            System.out.println(">>> " + e.getMostSpecificCause().getMessage());
        }
        System.out.println(">>> the app is still running");
    }
}
```

Some new pieces in this code:

- `jdbc.sql("...")` holds the SQL text. `.param(...)` fills the `?`. `.query(Long.class)` says
  the answer is a number. `.single()` runs the SQL and returns that one value. Nothing reaches
  the database before `.single()`.
- `DataAccessException` is the parent class of Spring's database errors, so catching it catches
  this one. The `try`/`catch` lets the app keep going after the error, the way a real app keeps
  running when one screen fails.
- `e.getMostSpecificCause()` is the innermost error. Here, that is Postgres's own message.

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=late
```

Real output:

```text
... Started DataApplication in 1.882 seconds (process running for 2.041)
Hibernate: 
    select
        count(ee1_0.id) 
    from
        event ee1_0 
    where
        ee1_0.venue_id=?
>>> countByVenueId(Riverside Arena) -> 4
>>> now the hand-written SQL, with venu_id instead of venue_id
>>> it failed only now, when the line ran: BadSqlGrammarException
>>> ERROR: column "venu_id" does not exist
  Hint: Perhaps you meant to reference the column "event.venue_id".
  Position: 34
>>> the app is still running
```

### Walkthrough

**Part A, read from the bottom up:**

| Line | Plain meaning |
| --- | --- |
| `PropertyReferenceException: No property 'venuId' found for type 'EventEntity'` | The original error. Spring Data read `VenuId` as a field name, looked in `EventEntity`, and found no field `venuId`. |
| `Did you mean 'venueId'` | The closest real field. It is one letter away. |
| `Failed to create query for method ... countByVenuId(java.util.UUID)` | The method whose name has the typo. |
| `QueryCreationException: Could not create query for ...` | Spring Data could not build the query for that method. |
| `BeanCreationException: Error creating bean with name 'eventRepository'` | So it could not build the proxy for `EventRepository`. |
| `UnsatisfiedDependencyException: Error creating bean with name 'existsDemo' ... constructor parameter 0` | Spring was building `ExistsDemo`, whose constructor needs an `EventRepository` as its first parameter (parameter 0). The top line names where Spring was when it hit the problem, not where the problem is. |
| `Application run failed` | The app stopped before it finished starting. |

**Part B, line by line:**

1. `Started DataApplication`: the app started without a complaint. Nothing checked the SQL
   string, because to Spring it is just text.
2. `count(ee1_0.id) ... where ee1_0.venue_id=?` and `-> 4`: the fixed derived query worked.
   Riverside Arena has 4 events.
3. `BadSqlGrammarException` is Spring's error for "the database did not understand this SQL". It
   appeared only when `.single()` sent the SQL to Postgres.
4. `ERROR: column "venu_id" does not exist` is Postgres's own message. `Position: 34` is the
   character where `venu_id` starts in the SQL text.
5. `Hint: Perhaps you meant to reference the column "event.venue_id".` Postgres suggests the fix
   too, but only when the line runs.
6. `>>> the app is still running`: in a real app, this SQL would sit in code that runs when
   someone opens one screen. The app would keep running, and that screen would fail, perhaps
   days after release.

Same typo, two very different moments. The derived query failed before the app could start.
The SQL string failed only when its line ran.

### Important terms

| Term | Meaning |
| --- | --- |
| **Failing early** | A mistake shows up at the first possible moment. Here that is startup, before users can reach the code. |
| **Failing late** | A mistake shows up only when the broken line runs, maybe long after release. |
| **Bean** | Any object Spring creates and keeps for you. |
| **Proxy** | The object Spring Data builds at startup to stand in for a repository interface and do the work. |
| **`Caused by:`** | The part of an error report that shows the error it wraps. The last one is the original error. |
| **`PropertyReferenceException`** | Spring Data's error for "this name points to a field the entity doesn't have". |
| **`JdbcClient`** | Spring's helper for running SQL that you write as a string. |
| **`BadSqlGrammarException`** | Spring's error for "the database did not understand this SQL". |
| **`DataAccessException`** | The parent class of Spring's database errors. |
| **Bootstrap mode** | When Spring Data builds repositories. The default builds them at startup. `lazy` builds each one at its first use. |

### Where it is used

- Every Spring Data app gets this check for free, at every start: on your laptop, in automated
  tests, and on the server.
- Teams often keep one automated test that only starts the app. It catches every misspelled
  property before the code is merged.
- When you see `No property '...' found for type '...'`, compare the name in the method with
  the field names in the entity. Use the `Did you mean` suggestion if there is one.

### Edge cases

- **No close field, no suggestion.** A temporary interface with
  `List<EventEntity> findByCity(String city);` gave only
  `Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'city' found for type 'EventEntity'`.
- **A typo in a keyword looks like a property error.** It is explained in common mistake 2.
- **One bad method breaks the whole repository.** In lazy mode (common mistake 3), the failing
  call was to `existsBySlug`, a correct method. The repository could not be built at all.
- **Lazy startup moves the failure to the first call.** It is shown in common mistake 3.

### Common mistakes — with the real errors

1. **Blaming the bean named in the top line.** The top line said
   `Error creating bean with name 'existsDemo'`, but `ExistsDemo` was fine. It only needed the
   repository. The real cause was in the last `Caused by:`. Read from the bottom up.
2. **Being confused by a typo in a keyword.** A temporary interface had
   `List<EventEntity> findByNameContaning(String part);`, meaning `Containing`. The last
   `Caused by:` was:

   ```text
   Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'contaning' found for type 'String'; Traversed path: EventEntity.name
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `No property 'contaning'` | Spring Data did not recognise `Contaning` as a keyword, so it treated it as a property. |
   | `found for type 'String'` | It looked for that property inside `name`, which is a `String`. |
   | `Traversed path: EventEntity.name` | "I got as far as `EventEntity.name` before I got lost." |

   So when the "property" in the message looks like a keyword, check the keyword's spelling.
3. **Turning on lazy startup without knowing what it changes.** The setting
   `spring.data.jpa.repositories.bootstrap-mode=lazy` builds each repository only when it is
   first used. It can make startup faster. The typo was run with it:

   ```bash
   mvn -q spring-boot:run -Dspring-boot.run.profiles=exists -Dspring-boot.run.arguments=--spring.data.jpa.repositories.bootstrap-mode=lazy
   ```

   ```text
   ... Bootstrapping Spring Data JPA repositories in LAZY mode.
   ... Started DataApplication in 2.064 seconds (process running for 2.342)
   ... Application run failed
   org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'eventRepository': FactoryBean threw exception on object creation
       ...
       at com.zenith.data.ExistsDemo.check(ExistsDemo.java:27) ~[classes/:na]
       at com.zenith.data.ExistsDemo.run(ExistsDemo.java:21) ~[classes/:na]
       ...
   Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'
   ```

   This time the app started. It failed only at the first call: line 27 of `ExistsDemo` is
   `boolean taken = events.existsBySlug(slug);`. That call was to a correct method, but the
   whole repository could not be built. Lazy startup trades the early check for speed. The
   default mode, which this project uses, fails before the app starts.

### Link to the task

S3 says: "Write three derived queries: `findByVenueId`, `findByNameContainingIgnoreCase`,
`existsBySlug`. Read their SQL. Then misspell a property and read the startup failure."

- This lesson does the last part. `countByVenuId` gave
  `No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'`, and the app
  refused to start.
- With this, **S3 is complete**: three derived queries, their SQL read, and one startup failure
  read.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` has `findByVenueId`, `findByNameContainingIgnoreCase` and `existsBySlug`
  (21 lines);
- `ExistsDemo` exists, with the profile `exists`;
- Postgres runs in the Docker container `pg`, with the `slug` column from V3.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — make the typo.** Replace
`scratch-app/src/main/java/com/zenith/data/EventRepository.java` with the 24-line version from
Part A of the example. Then run:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists
```

The output is long. To see only the key lines, run it like this instead:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=exists | grep -E "Started DataApplication|Application run failed|Caused by|>>>"
```

You should see `Application run failed` and four `Caused by:` lines. The last one should be
`Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'`.
There should be no `Started DataApplication` line and no `>>>` lines.

If the app starts and prints the three `>>>` lines, the typo is not in the file. Check that the
method is spelled `countByVenuId`, with no `e`.

**Step 3 — fix the spelling.** Change `countByVenuId` to `countByVenueId`, and run the same
command. You should see `Started DataApplication`, then:

```text
>>> existsBySlug("sufi-night") -> true
>>> existsBySlug("rain-dance") -> false
>>> existsBySlug("Sufi-Night") -> false
```

**Step 4 — the same typo in hand-written SQL.** Create
`scratch-app/src/main/java/com/zenith/data/LateFailureDemo.java` with the content from Part B of
the example. Run:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=late
```

You should see `Started DataApplication` first. Then you should see:

- `>>> countByVenueId(Riverside Arena) -> 4`;
- `>>> it failed only now, when the line ran: BadSqlGrammarException`;
- `>>> ERROR: column "venu_id" does not exist`, with the hint about `event.venue_id`;
- `>>> the app is still running`.

If the build fails with `cannot find symbol` pointing at `countByVenueId`, step 3 is not done.
The demo needs the corrected method. Delete `LateFailureDemo.java` when you are done with this
step.

**Step 5 — clean up.** Remove the two Lesson 9 lines (the comment and `countByVenueId`) from
`EventRepository.java`, so it has 21 lines again. Run
`mvn -q spring-boot:run -Dspring-boot.run.profiles=exists`. You should see the same three `>>>`
lines.

**Done when:**

- you saw the app refuse to start, and found
  `No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'` in the last
  `Caused by:` line;
- after the spelling fix, the app started;
- you saw the hand-written SQL fail only when its line ran, after `Started DataApplication`,
  with the app still running;
- `LateFailureDemo.java` is deleted, and `EventRepository.java` has 21 lines again.

### Summary for revision

- A misspelled property in a derived query's name compiles fine, then stops the app at startup.
- Spring Data checks every derived query at startup, even ones no code calls.
- Read the last `Caused by:` first. It holds the original error, often with `Did you mean`.
- The top line may name an innocent bean that only needed the repository.
- A keyword typo shows up as an odd property error, sometimes with `Traversed path`.
- The same typo in a hand-written SQL string fails only when that line runs.
- Lazy startup moves the check to the first call. The default mode fails before the app starts.

### Practice questions, with answers

**Question 1.** `EventEntity` has the fields `id`, `venueId`, `name`, `startsAt` (an
`OffsetDateTime`) and `slug`. A teammate adds this method to
`EventRepository extends JpaRepository<EventEntity, UUID>`:

```java
List<EventEntity> findByStartAt(OffsetDateTime when);
```

Nothing calls it. They start the app with the profile `exists`, whose demo only calls
`existsBySlug`. The project uses the default startup mode.

- (a) Does the app start? Do any `>>>` lines print?
- (b) What does the last `Caused by:` line say?
- (c) What is the fix?

**Answer.** This was run with the method added to `EventRepository`.

- (a) **No.** The log shows `Application run failed`, with no `Started DataApplication` line and
  no `>>>` lines, even though nothing calls the method. Spring Data checks every derived query
  while the app starts.
- (b)
  `Caused by: org.springframework.data.mapping.PropertyReferenceException: No property 'startAt' found for type 'EventEntity'; Did you mean 'startsAt'`.
  The name is one letter away from the real field, so Spring Data suggests it.
- (c) Rename the method to `findByStartsAt(OffsetDateTime when)`. A real run with that name
  started normally and printed the `>>>` lines.

**Question 2.** Two teammates make the same typo on the same day. Asha adds
`long countByVenuId(UUID venueId);` to `EventRepository`, though the entity's field is
`venueId`. Ravi writes a `JdbcClient` call with the SQL
`select count(*) from event where venu_id = ?`, though the column is `venue_id`. His code runs
only when someone opens the "venue report" screen. Both push their code, the app is restarted
with the default startup mode, and nobody opens the venue report screen for a week.

- (a) When does each typo show itself, and what does each person see?
- (b) Which way of failing is better, and why?

**Answer.**

- (a) **Asha's typo shows itself at the restart.** The app refuses to start: `Application run failed`,
  and the last `Caused by:` is
  `No property 'venuId' found for type 'EventEntity'; Did you mean 'venueId'`. A real run showed
  exactly this. **Ravi's typo stays hidden.** The app starts (`Started DataApplication`) and
  works for a week. When someone finally opens the venue report screen, that call fails with
  `BadSqlGrammarException` and Postgres's `ERROR: column "venu_id" does not exist`, with the
  hint `Perhaps you meant to reference the column "event.venue_id".` A real run showed this
  happening only when the line ran, after startup.
- (b) **Failing early, Asha's case, is better.** The mistake is found within minutes, by a
  developer, while the change is fresh. The message names the method and suggests the fix. In a
  real team, the first start happens on a laptop or in an automated test, so the broken version
  never reaches users. Ravi's mistake waited a week and was found by a user, when nobody
  remembered the change.

---

## Lesson 10 — `@Query` with JPQL (starts subtask S4)

### Definition

Some words first:

- An **annotation** is a label that starts with `@`. You put it on code to give tools extra
  information.
- **`@Query`** is a Spring Data annotation for a repository method. It holds a query that you
  write yourself. When a method has `@Query`, Spring Data uses your query and does not build one
  from the method's name.
- **JPQL** (Jakarta Persistence Query Language) is the query language of JPA, the Java standard
  that Hibernate follows. It looks like SQL, but it talks about **entity classes and their
  fields**, not tables and columns.
- **Hibernate** translates the JPQL into real SQL for Postgres.

This is S4's JPQL method:

```java
@Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
List<EventEntity> eventsAtVenueJpql(UUID venueId);
```

| Piece | Meaning |
| --- | --- |
| `select e` | Give me whole `EventEntity` objects. |
| `from EventEntity e` | Look in the entity `EventEntity`. `e` is a short name for one event, called an **alias**. |
| `where e.venueId = :venueId` | Keep the events whose field `venueId` equals the value of `:venueId`. |
| `:venueId` | A **named parameter**: a placeholder, filled from the Java parameter with the same name. |
| `order by e.startsAt` | Sort by the field `startsAt`, earliest first. |

You can name the method anything you like, such as `eventsAtVenueJpql`. Spring Data doesn't
read the name when `@Query` is there.

### Why it is needed

- Method names get long. "A venue's events, sorted by start time" as a derived query is
  `findByVenueIdOrderByStartsAt`. Add two more conditions, and the name becomes hard to read.
- Some queries can't be written as a method name at all. Examples: choosing which fields to
  read (`select e.name`), counting per group, or grouping conditions with brackets.
- Why JPQL and not plain SQL? JPQL uses the Java names you already have in `EventEntity`.
  Hibernate also checks it against the entity at startup, so a wrong entity name or field name
  stops the app before anyone can use it. That's the same "failing early" you get with derived
  queries.

### Key points

- `@Query("...")` on a repository method replaces the query Spring Data would build from the
  name. The method name is then free.
- JPQL names the entity class (`EventEntity`) and its fields (`venueId`, `startsAt`). Hibernate
  turns them into the table (`event`) and columns (`venue_id`, `starts_at`).
- A named parameter `:venueId` is filled from the Java parameter with the same name, and becomes
  a plain `?` in the SQL.
- Parameter names are visible because Spring Boot's parent POM turns on the compiler option
  `-parameters`. `@Param("...")` names a parameter by hand.
- A wrong entity name or field name in JPQL stops the app at startup.
- A wrong parameter name is **not** caught at startup. It fails at the call.

### How it works, step by step

**At startup:**

1. Spring Data sees `@Query` on `eventsAtVenueJpql` and takes its text.
2. Hibernate reads the JPQL and checks it against your entities. Is there an entity named
   `EventEntity`? Does it have the fields `venueId` and `startsAt`? If either answer is no, the
   app stops.
3. Hibernate works out the SQL. The entity becomes its table (`event`), each field becomes its
   column (`venue_id`, `starts_at`), and the named parameter `:venueId` becomes a plain `?`.

**When you call the method:**

4. Spring Data looks for the Java parameter whose name matches `:venueId`. It can see the name
   `venueId` because the build keeps parameter names. Normally the Java compiler throws them
   away, but Spring Boot's parent POM (`spring-boot-starter-parent` in `pom.xml`) turns on the
   compiler option `-parameters`, which keeps them.
5. It puts the value into the `?`, runs the SQL, and turns each row into an `EventEntity`.

Step 4 happens only when the method is called, so a parameter name that doesn't match is not
caught at startup.

You can also name the parameter yourself with **`@Param`**: `@Param("id") UUID venueId` matches
`:id` in the query. You need it when the build doesn't keep parameter names, or when you want a
different name in the query. A real run with `... where e.venueId = :id ...` and
`@Param("id") UUID venueId` returned the same 3 events.

### Example

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets the JPQL method:

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);

    // Lesson 10: a hand-written query in JPQL. It names the entity class and its fields, not the table and columns.
    @Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
    List<EventEntity> eventsAtVenueJpql(UUID venueId);
}
```

`scratch-app/src/main/java/com/zenith/data/JpqlDemo.java` calls it for a venue with events and a
venue without. It is a `CommandLineRunner`, so Spring calls its `run` method once, right after
startup. `@Profile("jpql")` makes it run only with the profile `jpql`.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 10: the JPQL query, called for a venue with events and a venue without.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=jpql
@Component
@Profile("jpql")
class JpqlDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LAKESIDE_HALL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final EventRepository events;

    JpqlDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("The Blue Note", BLUE_NOTE);
        show("Lakeside Hall", LAKESIDE_HALL);
    }

    private void show(String venueName, UUID venueId) {
        List<EventEntity> atVenue = events.eventsAtVenueJpql(venueId);
        System.out.println(">>> eventsAtVenueJpql(" + venueName + ") -> " + atVenue.size() + " events");
        for (EventEntity e : atVenue) {
            System.out.println(">>>   " + e.getStartsAt() + "  " + e.getName());
        }
    }
}
```

The last part of the command turns on Hibernate's **binding log**, which shows each value put
into a `?`:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=jpql -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output, shortened:

```text
... Started DataApplication in 1.941 seconds (process running for 2.088)
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.venue_id=? 
    order by
        ee1_0.starts_at
... binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]
>>> eventsAtVenueJpql(The Blue Note) -> 3 events
>>>   2026-11-28T15:30Z  Jazz at the Note
>>>   2026-12-12T14:30Z  Open Mic Night
>>>   2027-07-18T06:30Z  Blues Brunch
Hibernate: 
    (the same SQL again)
... binding parameter (1:UUID) <- [33333333-3333-3333-3333-333333333333]
>>> eventsAtVenueJpql(Lakeside Hall) -> 0 events
```

### Walkthrough

Here is how each JPQL piece came out in the real SQL:

| You wrote (JPQL) | Hibernate sent (SQL) |
| --- | --- |
| `select e` | `select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id` (every field of the entity) |
| `from EventEntity e` | `from event ee1_0` (the entity became its table; Hibernate picked its own alias, `ee1_0`) |
| `where e.venueId = :venueId` | `where ee1_0.venue_id=?` (the field became its column; the named parameter became `?`) |
| `order by e.startsAt` | `order by ee1_0.starts_at` |

1. `binding parameter (1:UUID) <- [22222222-...]`: the value of the Java parameter `venueId`
   went into the first `?`.
2. The Blue Note's 3 events came back in start-time order: 28 Nov 2026, 12 Dec 2026,
   18 Jul 2027.
3. Lakeside Hall has no events, so the method returned an empty list (`0 events`), not `null`
   and not an error.

**The same query from a method name.** The derived query
`List<EventEntity> findByVenueIdOrderByStartsAt(UUID venueId);` was run in a temporary
interface. Its SQL was exactly the same:
`... from event ee1_0 where ee1_0.venue_id=? order by ee1_0.starts_at`. For a simple query,
both ways work. `@Query` wins when the name would get long, or when you need something a name
can't say.

**Something a method name can't say: reading only one field.** This was also run in the
temporary interface:

```java
@Query("select e.name from EventEntity e where e.venueId = :venueId order by e.startsAt")
List<String> eventNamesAtVenue(UUID venueId);
```

The real SQL was
`select ee1_0.name from event ee1_0 where ee1_0.venue_id=? order by ee1_0.starts_at`. The result
was `[Jazz at the Note, Open Mic Night, Blues Brunch]`. Only one column was read, and the method
returns plain `String`s instead of entities.

### Important terms

| Term | Meaning |
| --- | --- |
| **Annotation** | A label starting with `@` that gives tools extra information about code. |
| **`@Query`** | Spring Data's annotation that holds a hand-written query for a repository method. |
| **JPQL** | Jakarta Persistence Query Language. Like SQL, but it names entity classes and fields. |
| **Alias** | A short name for one row or object in a query, such as `e` in `from EventEntity e`. |
| **Named parameter** | A placeholder such as `:venueId`, filled from the Java parameter with the same name. |
| **`-parameters`** | A Java compiler option that keeps parameter names in the compiled code. Spring Boot's parent POM turns it on. |
| **`@Param`** | Names a method parameter by hand, so it matches a named parameter in the query. |
| **Root entity** | The entity after `from` in a JPQL query. |
| **Attribute** | Hibernate's word for an entity's field. |

### Where it is used

- Queries whose derived method names would be too long to read.
- Reading only some fields, counting, or grouping.
- Conditions that need brackets, such as "this venue, and (name has X or name has Y)".
- The next lesson writes this same query in plain SQL, so you can compare the two side by side.

### Edge cases

- **No matching rows gives an empty list.** Lakeside Hall returned `0 events`, not `null`.
- **`@Param` sets the name by hand.** `:id` with `@Param("id") UUID venueId` worked and returned
  the same 3 events.
- **A derived name can produce the same SQL.** `findByVenueIdOrderByStartsAt` gave exactly the
  SQL of the JPQL query.
- **JPQL can return single fields.** `select e.name ...` returned a `List<String>` and read only
  the `name` column.

### Common mistakes — with the real errors

1. **Using the table name instead of the entity name.** A temporary interface had
   `@Query("select e from event e where e.venueId = :venueId")`. The app refused to start
   (shortened):

   ```text
   ... Application run failed
   ...
   Caused by: org.springframework.data.repository.query.QueryCreationException: Could not create query for public abstract java.util.List com.zenith.data.JpqlCheck.wrongTable(java.util.UUID); Reason: Query 'select e from event e where e.venueId = :venueId' validation failed for method public abstract java.util.List com.zenith.data.JpqlCheck.wrongTable(java.util.UUID)
   ...
   Caused by: org.hibernate.query.sqm.UnknownEntityException: Could not resolve root entity 'event'
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Query '...' validation failed for method ...` | Hibernate checked the JPQL at startup and rejected it. |
   | `root entity` | The entity after `from`. |
   | `Could not resolve root entity 'event'` | There is no entity named `event`. The entity is the class `EventEntity`. `event` is only the table's name. |

2. **Using a column name instead of a field name.** With
   `@Query("select e from EventEntity e where e.venue_id = :venueId")`, the app refused to start.
   The last line was:

   ```text
   Caused by: org.hibernate.query.sqm.PathElementException: Could not resolve attribute 'venue_id' of 'com.zenith.data.EventEntity'
   ```

   "Attribute" is Hibernate's word for an entity's field. `EventEntity` has no field
   `venue_id`. Its field is `venueId`.
3. **A parameter name that doesn't match.** This mistake is not caught at startup. A temporary
   interface had this method, plus a small demo that calls it:

   ```java
   @Query("select e from EventEntity e where e.venueId = :venue")   // the Java parameter is called venueId
   List<EventEntity> wrongParam(UUID venueId);
   ```

   Real output, shortened:

   ```text
   ... Started DataApplication in 2.051 seconds (process running for 2.213)
   >>> the app started; calling wrongParam
   ... Application run failed

   org.springframework.dao.InvalidDataAccessResourceUsageException: No argument for named parameter ':venue'
   ...
   Caused by: org.hibernate.QueryParameterException: No argument for named parameter ':venue'
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Started DataApplication` first | Startup did not notice the problem. |
   | `No argument for named parameter ':venue'` | At the call, the query wanted a value for `:venue`, and no Java parameter has that name. Hibernate stopped before sending any SQL. |
   | `Application run failed` | The demo made the call right after startup, so Spring Boot reported it this way. In a real app, only the screen that calls this method would fail. |

   The fix is to use the same name in both places (`:venueId` with `UUID venueId`), or to name
   the parameter with `@Param("venue")`.

### Link to the task

S4 says: "Write one `@Query` in JPQL and one with `nativeQuery = true`, both returning a venue's
events."

- This lesson writes the JPQL one, `eventsAtVenueJpql`. It returned The Blue Note's 3 events in
  start-time order, using the SQL
  `... from event ee1_0 where ee1_0.venue_id=? order by ee1_0.starts_at`.
- Still to do in S4: the same query with `nativeQuery = true`.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` has `findByVenueId`, `findByNameContainingIgnoreCase` and `existsBySlug`
  (21 lines);
- Postgres runs in the Docker container `pg`, with the 7 seed events loaded.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the JPQL method and the demo.**

- Replace `scratch-app/src/main/java/com/zenith/data/EventRepository.java` with the 26-line
  version from the example. Note the new import line,
  `import org.springframework.data.jpa.repository.Query;`.
- Create `scratch-app/src/main/java/com/zenith/data/JpqlDemo.java` with the 38-line content
  from the example.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=jpql -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see:

- the SQL with `from event ee1_0`, `where ee1_0.venue_id=?` and `order by ee1_0.starts_at`;
- `binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]`;
- `>>> eventsAtVenueJpql(The Blue Note) -> 3 events`, then Jazz at the Note, Open Mic Night and
  Blues Brunch, in that order;
- `>>> eventsAtVenueJpql(Lakeside Hall) -> 0 events`.

If the build stops with `EventRepository.java:[23,6] cannot find symbol` and
`symbol: class Query`, the import line for `Query` is missing. A real compile without that line
showed exactly this.

**Step 3 — break it with the table name.** In the `@Query` text, change `from EventEntity e` to
`from event e`. Run `mvn -q spring-boot:run -Dspring-boot.run.profiles=jpql`. You should see
`Application run failed`, and the last `Caused by:` should be
`org.hibernate.query.sqm.UnknownEntityException: Could not resolve root entity 'event'`. No
`>>>` lines appear. Change it back to `from EventEntity e`.

**Step 4 — break it with a parameter name.** Change `:venueId` to `:venue` in the `@Query` text,
and run the same command. You should see `Started DataApplication` first. Then you should see
`Application run failed`, with
`InvalidDataAccessResourceUsageException: No argument for named parameter ':venue'`. No `>>>`
lines appear, because the very first call failed. Change it back to `:venueId`.

**Step 5 — run it once more.** Use the command from step 2. You should get the same output as in
step 2.

**Done when:**

- you have seen the JPQL become SQL with `event`, `venue_id` and `starts_at`, and the 3 Blue
  Note events in date order;
- you have seen `Could not resolve root entity 'event'` stop the app at startup;
- you have seen `No argument for named parameter ':venue'` appear only after
  `Started DataApplication`;
- both changes are undone, and step 5 prints the same output as step 2.

### Summary for revision

- `@Query("...")` holds a hand-written query. Spring Data no longer reads the method name.
- JPQL names the entity class and its fields. Hibernate turns them into the table and columns.
- `:name` is a named parameter. It matches the Java parameter with the same name, or the one
  marked `@Param("name")`.
- Entity and field mistakes in JPQL stop the app at startup.
- A parameter-name mismatch fails only at the call, before any SQL is sent.
- For simple queries, a derived name gives the same SQL. `@Query` is for long or special
  queries, such as reading only one field.

### Practice questions, with answers

**Question 1.** `EventEntity` has a field `private OffsetDateTime startsAt;`, mapped to the
column `starts_at`. A repository has this method:

```java
@Query("select e from EventEntity e where e.startsAt > :after order by e.startsAt desc")
List<EventEntity> startingAfter(OffsetDateTime after);
```

It is called with `OffsetDateTime.parse("2027-01-01T00:00Z")`, with the binding log on. Three
events start after that date: Sufi Night (2027-07-10T14:00Z), Blues Brunch (2027-07-18T06:30Z)
and Indie Fest (2027-07-24T11:30Z).

- (a) What do the `where` and `order by` parts of the SQL look like?
- (b) What does the binding line show?
- (c) In what order do the three events come back, and why?

**Answer.** This method was run in a temporary interface with the binding log on.

- (a) `where ee1_0.starts_at>? order by ee1_0.starts_at desc`. The field `startsAt` became the
  column `starts_at`, and `:after` became `?`.
- (b) `binding parameter (1:TIMESTAMP_UTC) <- [2027-01-01T00:00Z]`. `TIMESTAMP_UTC` means
  Hibernate sends the date-time as a timestamp in UTC.
- (c) Indie Fest, then Blues Brunch, then Sufi Night: latest first, because `desc` means
  "largest first". The real output was
  `[2027-07-24T11:30Z Indie Fest, 2027-07-18T06:30Z Blues Brunch, 2027-07-10T14:00Z Sufi Night]`.

**Question 2.** Each method below sits in its own interface that
`extends Repository<EventEntity, UUID>`. `EventEntity` is the entity class for the table
`event`. Its field `name` maps to the column `name`. For each one, say whether the app starts.
If something fails, say when (at startup or at the call), and what the message says.

- (a) `@Query("select e from EventEntity e where e.name = :name") List<EventEntity> named(String name);`
- (b) `@Query("select e from Event e where e.name = :name") List<EventEntity> named(String name);`
- (c) `@Query("select e from EventEntity e where e.name = :eventName") List<EventEntity> named(String name);`

**Answer.** All three were run for real.

- (a) **It starts and works.** The entity name, the field name and the parameter name are all
  right. The real SQL was `... from event ee1_0 where ee1_0.name=?`, with
  `binding parameter (1:VARCHAR) <- [Sufi Night]`, and `named("Sufi Night")` returned
  `[Sufi Night]`.
- (b) **It fails at startup.** The last line was
  `Caused by: org.hibernate.query.sqm.UnknownEntityException: Could not resolve root entity 'Event'`.
  There is no entity named `Event`. JPQL needs the entity's name, which is the class name
  `EventEntity`.
- (c) **It starts, and fails at the call.** `Started DataApplication` appeared first. Then the
  call failed with
  `InvalidDataAccessResourceUsageException: No argument for named parameter ':eventName'`.
  The query asks for `:eventName`, but the Java parameter is called `name`. Fix it with
  `:name`, or with `@Param("eventName") String name`.

---

## Lesson 11 — `@Query` with native SQL (finishes subtask S4)

### Definition

Some words first:

- An **entity** is a Java class that stands for one table. One object is one row. Ours is
  `EventEntity`, for the table `event`.
- **Hibernate** is the library that turns rows into entity objects, and objects back into rows.
  It also sends the SQL to Postgres.
- **`@Query`** is a Spring Data annotation (a label that starts with `@`) for a repository
  method. It holds a query that you write yourself.
- **JPQL** is the query language of JPA, the Java standard that Hibernate follows. It looks like
  SQL, but it names the entity class and its fields. Hibernate translates it into real SQL.
- **Native SQL** is plain SQL in the database's own language, with the real table and column
  names. "Native" means "the database's own".
- **`nativeQuery = true`** is a setting inside `@Query`. It says: "this text is native SQL, not
  JPQL".

Each Java name in `EventEntity` has a matching database name:

| Field in `EventEntity` (Java name) | Column in table `event` (database name) |
| --- | --- |
| `id` | `id` |
| `venueId` | `venue_id` |
| `name` | `name` |
| `startsAt` | `starts_at` |
| `slug` | `slug` |

S4's JPQL method uses the Java names:

```java
@Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
List<EventEntity> eventsAtVenueJpql(UUID venueId);
```

S4's native method uses the database names:

```java
@Query(value = "select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)
List<EventEntity> eventsAtVenueNative(UUID venueId);
```

| Piece | Meaning |
| --- | --- |
| `value = "..."` | The query text. |
| `select *` | Every column of each row. |
| `from event` | The table `event`. |
| `where venue_id = :venueId` | Keep the rows whose column `venue_id` equals `:venueId`. |
| `:venueId` | A **named parameter**: a placeholder, filled from the Java parameter with the same name. It is the only Java name in this SQL. |
| `order by starts_at` | Sort by the column `starts_at`, earliest first. |
| `nativeQuery = true` | Send this text as SQL. Don't read it as JPQL. |

**Why `value =` is needed here.** It's a Java rule. An annotation's settings are written
`name = value`. If there is only one setting and it is called `value`, you may leave out
`value =`. That's why the JPQL method can write `@Query("...")`. Once there are two settings,
every setting must have its name.

### Why it is needed

JPQL can only name things that have an entity. In this app, only the `event` table has one. The
`venue` table (columns `id`, `name`, `city`, `capacity`, `seat_map_hints`, `created_at`) has no
entity.

Take the question "all events in Hyderabad". The city is stored only in `venue.city`. So the
query must **join** the two tables: each event row is glued to the venue row whose `id` equals
the event's `venue_id`.

**JPQL can't do it.** A temporary interface had this method:

```java
@Query("select e from EventEntity e join venue v on v.id = e.venueId where v.city = :city order by e.startsAt")
List<EventEntity> eventsInCityJpql(String city);
```

The app refused to start (shortened):

```text
... Application run failed
...
Caused by: org.hibernate.query.PathException: Could not resolve join path 'venue'
```

Hibernate looked for something called `venue` among the entities and among `EventEntity`'s
fields. Nothing has that name.

**Native SQL can.** The same temporary interface had this method:

```java
@Query(value = "select e.* from event e join venue v on v.id = e.venue_id where v.city = :city order by e.starts_at",
       nativeQuery = true)
List<EventEntity> eventsInCity(String city);
```

`e` and `v` are **aliases**, short names for the two tables. `e.*` means "every column of
`event`, and none of `venue`". Real output:

```text
>>> eventsInCity("Hyderabad") -> [Monsoon Rock Night, Classical Evening, Sufi Night, Indie Fest]
>>> eventsInCity("Mumbai") -> [Jazz at the Note, Open Mic Night, Blues Brunch]
>>> eventsInCity("Chennai") -> []
```

There are two more reasons to use native SQL. You may need something only Postgres can do. Or
you may have tested the SQL in psql already and want to use it as it is.

Native SQL also has a price:

- Nothing checks it at startup. A wrong name shows up only when the method is called.
- Your SQL must return every column the entity needs.
- It is written for Postgres. Another database may not understand it.

### Key points

- `nativeQuery = true` makes `@Query` send its text to the database as SQL. The text uses table
  and column names, not entity and field names.
- With two settings, the query text needs its name: `value = "..."`.
- Named parameters work as in JPQL. `:venueId` is filled from the Java parameter with the same
  name (or the one marked `@Param("venueId")`), and becomes a plain `?`.
- Nothing checks the table and column names at startup. Mistakes show up at the call, reported
  by Postgres or by Hibernate.
- Hibernate fills each entity field from the column with the matching name. Extra columns are
  ignored. A missing column is an error.
- In a join, write `e.*` so that only the columns of the entity's table come back.

### How it works, step by step

**At startup:**

1. Spring Data sees `@Query` with `nativeQuery = true`, and keeps the text.
2. Nothing checks the table and column names in it. Hibernate doesn't compare native SQL with
   your entities, and Postgres hasn't seen it yet. So a wrong name stays hidden.

**When you call the method:**

3. Spring Data finds the Java parameter whose name matches `:venueId`. This is the same rule as
   in JPQL. In the SQL, `:venueId` becomes a plain `?`.
4. Hibernate sends your text to Postgres, with that one change, and puts the value into the `?`.
5. Postgres runs the SQL. If a table or column name is wrong, Postgres says so now.
6. Postgres sends back the rows. Hibernate turns each row into an `EventEntity`. For each field,
   it looks for the matching column **by name**: `id`, `venue_id`, `name`, `starts_at`, `slug`.

`select *` returns all 8 columns of `event`. This psql query lists them:

```bash
docker exec pg psql -U postgres -d zenith -c "select ordinal_position, column_name, data_type from information_schema.columns where table_name = 'event' order by ordinal_position;"
```

```text
 ordinal_position |  column_name  |        data_type         
------------------+---------------+--------------------------
                1 | id            | uuid
                2 | venue_id      | uuid
                3 | name          | text
                4 | starts_at     | timestamp with time zone
                5 | created_at    | timestamp with time zone
                6 | cancelled_at  | timestamp with time zone
                7 | doors_open_at | timestamp with time zone
                8 | slug          | text
(8 rows)
```

`EventEntity` uses 5 of them. Hibernate ignores `created_at`, `cancelled_at` and
`doors_open_at`. Extra columns are fine. A missing column is an error.

### Example

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets the native method. The
`Query` import was already there for the JPQL method.

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);

    // Lesson 10: a hand-written query in JPQL. It names the entity class and its fields, not the table and columns.
    @Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
    List<EventEntity> eventsAtVenueJpql(UUID venueId);

    // Lesson 11: the same query in native SQL. It names the table and its columns, exactly as Postgres knows them.
    @Query(value = "select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)
    List<EventEntity> eventsAtVenueNative(UUID venueId);
}
```

`scratch-app/src/main/java/com/zenith/data/NativeDemo.java` asks the same question both ways. It
is a `CommandLineRunner`, so Spring calls its `run` method once, right after startup.
`@Profile("native")` makes it run only with the profile `native`. The `names` helper keeps only
each event's name. Two lists are `equals` when they hold the same items in the same order.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Lesson 11: the same question asked twice - once in JPQL, once in native SQL.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=native
@Component
@Profile("native")
class NativeDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final EventRepository events;

    NativeDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        List<String> fromJpql = names(events.eventsAtVenueJpql(BLUE_NOTE));
        List<String> fromNative = names(events.eventsAtVenueNative(BLUE_NOTE));
        System.out.println(">>> JPQL:   " + fromJpql);
        System.out.println(">>> native: " + fromNative);
        System.out.println(">>> same result: " + fromJpql.equals(fromNative));
    }

    private static List<String> names(List<EventEntity> found) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : found) {
            names.add(e.getName());
        }
        return names;
    }
}
```

The last part of the command turns on Hibernate's **binding log**, which shows each value put
into a `?`:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=native -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output, shortened:

```text
... Started DataApplication in 1.799 seconds (process running for 1.943)
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.venue_id=? 
    order by
        ee1_0.starts_at
... binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]
Hibernate: 
    select
        * 
    from
        event 
    where
        venue_id = ? 
    order by
        starts_at
... binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]
>>> JPQL:   [Jazz at the Note, Open Mic Night, Blues Brunch]
>>> native: [Jazz at the Note, Open Mic Night, Blues Brunch]
>>> same result: true
```

### Walkthrough

1. The first `Hibernate:` block is the JPQL query. Hibernate wrote this SQL itself.
   `EventEntity` became `event`, each field became its column, and it listed exactly the 5
   columns the entity needs.
2. The second block is the native query, and it is your own text. Hibernate only spread it over
   several lines in the log and turned `:venueId` into `?`. `select *` stayed `select *`, and
   even your spaces in `venue_id = ?` stayed.
3. Both binding lines are the same. The value of the Java parameter `venueId` went into the
   first `?`.
4. Both queries returned The Blue Note's 3 events in start-time order, so the demo printed
   `same result: true`.

Side by side:

| | JPQL | Native SQL |
| --- | --- | --- |
| Names you write | Entity class and fields | Table and columns |
| SQL sent to Postgres | Written by Hibernate | Your text, with `?` for each parameter |
| Columns read in this run | The 5 the entity needs | All 8; Hibernate used 5 |
| Checked at startup | Yes | No |
| Tables with no entity | Can't reach them | Can reach them |
| Another database | Hibernate writes the SQL for it | Your SQL must work there too |

### Important terms

| Term | Meaning |
| --- | --- |
| **Native SQL** | Plain SQL in the database's own language, with real table and column names. |
| **`nativeQuery = true`** | The `@Query` setting that says "this text is native SQL, not JPQL". |
| **`value`** | The `@Query` setting that holds the query text. Its name may be left out only when it is the only setting. |
| **Join** | Gluing rows of two tables together by a matching value, such as `v.id = e.venue_id`. |
| **Alias** | A short name for a table in a query, such as `e` in `from event e`. |
| **`e.*`** | Every column of the table with the alias `e`, and no others. |
| **JDBC** | The basic Java library for talking to databases. Hibernate uses it underneath. |
| **ResultSet** | The JDBC object that holds the rows a query sent back. |
| **SQLState** | A standard error code from the database. `42703` means "no such column"; `42P01` means "no such table". |
| **Relation** | Postgres's word for a table, or anything that looks like one. |

### Where it is used

- Queries that need a table with no entity, like the `venue` join.
- Postgres-only features, such as its operators for JSON columns or for full-text search.
- Big report queries with many joins and groups, where you want to control the exact SQL.
- SQL you first tested in psql.

A common team rule: start with a derived query or JPQL, because startup checks them. Use native
SQL only when they can't do the job. And make sure something calls each native query before you
ship it, such as a test, because startup won't check it.

### Edge cases

- **Extra columns are fine.** `select *` returned 8 columns. Hibernate used the 5 the entity
  needs and ignored the rest.
- **No matching rows gives an empty list.** `eventsInCity("Chennai")` returned `[]`. Chennai's
  venue, Lakeside Hall, has no events.
- **`@Param` works in native SQL too.** A temporary method with
  `... where venue_id = :venue_id ...` and the parameter `@Param("venue_id") UUID venueId`
  returned `[Jazz at the Note, Open Mic Night, Blues Brunch]`.
- **The log spreads your SQL over lines, but keeps your words.** Only `:venueId` changed, to
  `?`.
- **Postgres changes names to small letters.** Names written without double quotes are turned
  into small letters: `EventEntity` became `evententity`, and `venueId` became `venueid`.

### Common mistakes — with the real errors

1. **Leaving out `value =`.** `EventRepository` had
   `@Query("select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)`.
   The build stopped:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../EventRepository.java:[28,12] annotation values must be of the form 'name=value'
   ```

   `[28,12]` means line 28, column 12, where the query text starts. With two settings, Java
   needs `name = value` for each one. Write `value = "..."`.

2. **A typo in a column name.** In `EventRepository`, `venue_id` was changed to `venu_id` in the
   native query only, and the demo was run. Real output, shortened:

   ```text
   ... Started DataApplication in 1.886 seconds (process running for 2.037)
   Hibernate: 
       (the JPQL query's SQL, which ran fine)
   Hibernate: 
       select
           * 
       from
           event 
       where
           venu_id = ? 
       order by
           starts_at
   ... SQL Error: 0, SQLState: 42703
   ... ERROR: column "venu_id" does not exist
     Hint: Perhaps you meant to reference the column "event.venue_id".
     Position: 27
   ... Application run failed

   org.springframework.dao.InvalidDataAccessResourceUsageException: JDBC exception executing SQL [select * from event where venu_id = ? order by starts_at] [ERROR: column "venu_id" does not exist
     Hint: Perhaps you meant to reference the column "event.venue_id".
     Position: 27] [n/a]; SQL [n/a]
   ...
   	at com.zenith.data.NativeDemo.run(NativeDemo.java:28) ~[classes/:na]
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Started DataApplication` first | Startup didn't notice the typo. |
   | The SQL with `venu_id = ?` was printed | Hibernate sent your text to Postgres without checking it. |
   | `SQLState: 42703` | A standard error code. `42703` means "no such column". |
   | `column "venu_id" does not exist` | Postgres found the typo when the SQL arrived. |
   | `Hint: Perhaps you meant ... "event.venue_id"` | Postgres guessed the name you meant. |
   | `Position: 27` | The problem starts at character 27 of the SQL, where `venu_id` begins. |
   | `NativeDemo.java:28` | The line that called the native method. |
   | `Application run failed` | The demo made the call right after startup, so Spring Boot reported it this way. In a real app, only the request that calls this method would fail. |

3. **JPQL names inside native SQL.** A temporary interface had a native query written with the
   Java names:

   ```java
   @Query(value = "select * from EventEntity where venueId = :venueId", nativeQuery = true)
   ```

   The app started, and the call failed (shortened):

   ```text
   ... Started DataApplication in 1.888 seconds (process running for 2.031)
   ... SQL Error: 0, SQLState: 42P01
   ... ERROR: relation "evententity" does not exist
     Position: 15
   ... Application run failed

   org.springframework.dao.InvalidDataAccessResourceUsageException: JDBC exception executing SQL [select * from EventEntity where venueId = ?] [ERROR: relation "evententity" does not exist
     Position: 15] [n/a]; SQL [n/a]
   ```

   "Relation" is Postgres's word for a table, or anything that looks like one, and `42P01`
   means "no such table". Postgres changes names written without double quotes to small
   letters, so `EventEntity` became `evententity`. Postgres also stops at the first problem, so
   it never reached `venueId`, which is wrong too (the column is `venue_id`). Position 15 is
   where `EventEntity` starts.

4. **Leaving out a column the entity needs.** In `EventRepository`, `select *` was changed to
   `select id, venue_id, name, starts_at`, and the demo was run. Real output, shortened:

   ```text
   ... Started DataApplication in 1.8 seconds (process running for 1.944)
   Hibernate: 
       (the JPQL query's SQL, which ran fine)
   Hibernate: 
       select
           id,
           venue_id,
           name,
           starts_at 
       from
           event 
       where
           venue_id = ? 
       order by
           starts_at
   ... SQL Error: 0, SQLState: 42703
   ... The column name slug was not found in this ResultSet.
   ... Application run failed

   org.springframework.dao.InvalidDataAccessResourceUsageException: Unable to find column position by name: slug [The column name slug was not found in this ResultSet.] [n/a]; SQL [n/a]
   ...
   	at com.zenith.data.NativeDemo.run(NativeDemo.java:28) ~[classes/:na]
   ```

   This time the SQL itself was fine. Postgres ran it and sent back rows with 4 columns. A
   **ResultSet** is the Java object that holds the rows a query sent back. It comes from JDBC,
   the basic Java library for talking to databases. Hibernate then tried to fill the field
   `slug`, looked for a column named `slug` in those rows, and found none. Fix it by adding
   `slug` to the list, or by using `select *`.

5. **`select *` in a join.** A temporary interface had the city query with `select *` instead of
   `select e.*`:

   ```java
   @Query(value = "select * from event e join venue v on v.id = e.venue_id where v.city = :city order by e.starts_at",
          nativeQuery = true)
   List<EventEntity> starFromEventFirst(String city);
   ```

   It started, the SQL ran, and then the call failed:

   ```text
   org.springframework.orm.jpa.JpaSystemException: Encountered a duplicated sql alias [id] during auto-discovery of a native-sql query
   ...
   Caused by: org.hibernate.loader.NonUniqueDiscoveredSqlAliasException: Encountered a duplicated sql alias [id] during auto-discovery of a native-sql query
   ```

   In a join, `select *` returns the columns of both tables. Both tables have an `id` (and a
   `name`, and a `created_at`), so each row had two columns called `id`. Hibernate can't tell
   which `id` belongs to the event, so it stops. Here "sql alias" just means a column name in
   the result. Writing the join the other way round (`from venue v join event e`) gave the same
   error. The fix is `select e.*`, which worked in the city example.

### Link to the task

S4 says: "Write one `@Query` in JPQL and one with `nativeQuery = true`, both returning a venue's
events."

- JPQL: `eventsAtVenueJpql`. Native: `eventsAtVenueNative`. Both are in `EventRepository`.
- For The Blue Note, both returned `[Jazz at the Note, Open Mic Night, Blues Brunch]`, and the
  demo printed `same result: true`.
- **S4 is done.**

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` has `findByVenueId`, `findByNameContainingIgnoreCase`, `existsBySlug` and
  the JPQL method `eventsAtVenueJpql` (26 lines). `JpqlDemo.java` exists.
- Postgres runs in the Docker container `pg`, with the 7 seed events and the `slug` column
  (migration V3).

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the native method and the demo.**

- In `scratch-app/src/main/java/com/zenith/data/EventRepository.java`, add the 3 lines for
  Lesson 11 (the comment, the `@Query`, the method) before the last `}`. The file then matches
  the 30-line version in the example. You don't need a new import.
- Create `scratch-app/src/main/java/com/zenith/data/NativeDemo.java` with the 41 lines from the
  example.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=native -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see:

- first, the JPQL SQL, with `from event ee1_0` and `where ee1_0.venue_id=?`;
- then the native SQL: `select *`, `from event`, `where venue_id = ?`, `order by starts_at`;
- after each one, `binding parameter (1:UUID) <- [22222222-2222-2222-2222-222222222222]`;
- at the end:

```text
>>> JPQL:   [Jazz at the Note, Open Mic Night, Blues Brunch]
>>> native: [Jazz at the Note, Open Mic Night, Blues Brunch]
>>> same result: true
```

If the build stops with
`EventRepository.java:[28,12] annotation values must be of the form 'name=value'`, you wrote
`@Query("...", nativeQuery = true)`. Put `value = ` before the query text.

**Step 3 — break it with a typo.** In the native query only, change `venue_id` to `venu_id`.
Run:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=native
```

You should see:

- `Started DataApplication` first;
- the JPQL SQL, then the native SQL with `venu_id = ?`;
- `ERROR: column "venu_id" does not exist` and
  `Hint: Perhaps you meant to reference the column "event.venue_id".`;
- `Application run failed`, with
  `InvalidDataAccessResourceUsageException: JDBC exception executing SQL [select * from event where venu_id = ? order by starts_at]`;
- no `>>>` lines, because the demo prints only after both calls, and the second call failed.

Change it back to `venue_id`.

**Step 4 — break it by leaving out a column.** Change `select *` to
`select id, venue_id, name, starts_at`. Run the same command as in step 3. You should see:

- `Started DataApplication` first;
- the native SQL with the 4 columns;
- `The column name slug was not found in this ResultSet.`;
- `Application run failed`, with
  `InvalidDataAccessResourceUsageException: Unable to find column position by name: slug`;
- no `>>>` lines.

Change it back to `select *`.

**Step 5 — run it once more.** Use the command from step 2. You should get the same output as
in step 2.

**Done when:**

- both queries printed the same 3 Blue Note events, and `same result: true`;
- you saw the native SQL in the log as you wrote it, with only `:venueId` turned into `?`;
- you saw the `venu_id` typo get past startup and fail only at the call, with Postgres's hint;
- you saw the missing `slug` column fail while the rows were being turned into objects;
- both changes are undone, and step 5 printed the same as step 2.

### Summary for revision

- `@Query(value = "...", nativeQuery = true)` sends real SQL. It names tables and columns.
- With two settings, the query text needs `value =`.
- Named parameters work as in JPQL: `:venueId` matches the Java parameter `venueId` (or
  `@Param("venueId")`), and becomes `?`.
- Nothing checks native SQL at startup. A wrong table or column is found by Postgres, at the
  call.
- Hibernate fills each field from the column with the same name. Extra columns are ignored. A
  missing one fails at the call.
- Native SQL can reach tables with no entity, such as `venue`. JPQL can't.
- In a join, write `e.*`, not `*`.
- Prefer derived queries and JPQL. Use native SQL when they can't do the job, and make sure
  something calls each native query, such as a test.

### Practice questions, with answers

**Question 1.** `EventEntity` has the field `venueId`, mapped to the column `venue_id`. One
interface, which `extends Repository<EventEntity, UUID>`, has these two methods. No code calls
either of them.

```java
@Query("select e from EventEntity e where e.venuId = :venueId order by e.startsAt")
List<EventEntity> typoJpql(UUID venueId);

@Query(value = "select * from event where venu_id = :venueId order by starts_at", nativeQuery = true)
List<EventEntity> typoNative(UUID venueId);
```

- (a) You start the app. Does it start? If not, which of the two typos does the error name?
- (b) You fix only the JPQL typo (`e.venuId` becomes `e.venueId`) and start again. What
  happens?
- (c) After (b), when will the other typo be found, and who will find it?

**Answer.** Both starts were run for real.

- (a) **It doesn't start, and only the JPQL typo is named.** The error names the method
  `typoJpql`, and the last line was
  `Caused by: org.hibernate.query.sqm.PathElementException: Could not resolve attribute 'venuId' of 'com.zenith.data.EventEntity'`.
  The native typo `venu_id` appears nowhere in the log. Hibernate checks JPQL against the
  entity at startup, but nothing checks native SQL.
- (b) **It starts cleanly.** The real run printed `Started DataApplication` and ended with exit
  code 0, without a single warning. The native typo is still there, hidden.
- (c) **Only when some code calls `typoNative`, and Postgres finds it.** A real call of this
  same SQL gave `ERROR: column "venu_id" does not exist` with
  `Hint: Perhaps you meant to reference the column "event.venue_id".` In a real app, that might
  be days later, on the first request that uses the method. That's why each native query needs
  something that calls it, such as a test.

**Question 2.** This native method works. `EventEntity`'s field `venueId` maps to the column
`venue_id`.

```java
@Query(value = "select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)
List<EventEntity> eventsAtVenueNative(UUID venueId);
```

- (a) `venue_id` and `:venueId` look alike. Which is a database name, and which is a Java name?
- (b) What happens if you write `where venueId = :venueId` instead?
- (c) What happens if you write `where venue_id = :venue_id` instead, with the Java parameter
  still `UUID venueId`?

**Answer.** (b) and (c) were run for real, each in its own temporary method.

- (a) `venue_id` is a **database name**: the column in the table `event`. Native SQL goes to
  Postgres, so it must use Postgres's names. `:venueId` is a **Java name**: a named parameter,
  filled from the Java parameter `UUID venueId`. It never reaches Postgres as a name, because it
  becomes `?`.
- (b) **It starts, and the call fails.** Postgres answered
  `ERROR: column "venueid" does not exist` with
  `Hint: Perhaps you meant to reference the column "event.venue_id".` `venueId` is the field's
  name, and Postgres knows nothing about fields. Postgres also turned it into small letters,
  because it does that to every name written without double quotes.
- (c) **It starts, and the call fails before any SQL is sent.** The error was
  `InvalidDataAccessResourceUsageException: No argument for named parameter ':venue_id'`, and
  its root cause was `org.hibernate.QueryParameterException`. No SQL was printed for this call.
  The query asks for a value called `venue_id`, and no Java parameter has that name. Fix it
  with `:venueId`, or keep `:venue_id` and write `@Param("venue_id") UUID venueId`. A real run
  of the `@Param` version returned `[Jazz at the Note, Open Mic Night, Blues Brunch]`.

---

## Lesson 12 — Paging: `Pageable` and `Page` (starts subtask S5)

### Definition

Some words first:

- **Paging** means showing a long list in small pieces, one piece at a time. Each piece is a
  **page**. A search site that shows "page 1 of 4" is paging.
- The **page size** is how many rows one page holds.
- The **page number** says which page you want. In Spring Data, counting starts at **0**, so
  page 0 is the first page.
- **`Sort`** describes the order of the rows. `Sort.by("startsAt")` means "by the entity field
  `startsAt`, smallest first". Smallest first is called **ascending** (`ASC`); for dates, that
  means earliest first. `.descending()` (`DESC`) flips it.
- **`Pageable`** is a Spring Data type that describes the page you want: page number, page size
  and sort.
- **`PageRequest`** is the ready-made `Pageable` you normally use. You make one with
  `PageRequest.of(pageNumber, pageSize, sort)`.
- **`Page<EventEntity>`** is the answer. It holds the rows on this page, plus facts about all
  the pages together.

`Pageable`, `PageRequest`, `Sort` and `Page` all live in the package
`org.springframework.data.domain`.

You don't add anything to the repository. `EventRepository extends JpaRepository<EventEntity, UUID>`,
and `JpaRepository` brings the method `findAll(Pageable pageable)` with it. That method comes
from `PagingAndSortingRepository`, one of the interfaces `JpaRepository` extends.

```java
Pageable firstPage = PageRequest.of(0, 2, Sort.by("startsAt"));
Page<EventEntity> page = events.findAll(firstPage);
```

| Piece | Meaning |
| --- | --- |
| `0` | Page number. 0 is the first page. |
| `2` | Page size: 2 rows per page. |
| `Sort.by("startsAt")` | Sort by the field `startsAt`, earliest first. |
| `events.findAll(firstPage)` | Fetch that one page. |

What the answer can tell you:

| Method on `Page` | What it tells you |
| --- | --- |
| `getContent()` | The rows on this page, as a `List<EventEntity>`. |
| `getNumber()` | This page's number, counting from 0. |
| `getSize()` | The page size you asked for. |
| `getNumberOfElements()` | How many rows are actually on this page. The last page can have fewer. |
| `getTotalElements()` | How many rows there are on all pages together. |
| `getTotalPages()` | How many pages there are. |
| `hasNext()` | Whether there is a page after this one. |
| `nextPageable()` | The request for the next page: same size, same sort, page number plus 1. |

### Why it is needed

- A real events table can hold thousands of rows. Plain `findAll()` loads every one of them into
  memory. That is slow, and no screen can show 10,000 rows anyway.
- With paging, the database sends only the rows for one page: 2 here, often 20 in real apps.
- The screen also needs to know "page 1 of 4", and whether to show a "Next" button. `Page`
  gives you those numbers too.
- Without `Pageable`, you would write the "skip some rows, take a few" SQL and a separate
  counting SQL by hand, for every list in the app.

### Key points

- `PageRequest.of(pageNumber, pageSize, sort)` describes one page. Page numbers start at 0.
- `findAll(Pageable)` comes with `JpaRepository`. Your own query methods can take a `Pageable`
  too.
- `Page` holds this page's rows (`getContent()`) and the totals (`getTotalElements()`,
  `getTotalPages()`).
- Spring Data skips (page number × page size) rows of the **sorted** list, then takes (page
  size) rows.
- `Sort` uses entity field names. A wrong name fails at the call, not at startup.
- Without a sort, the database promises no order, so pages can overlap or miss rows.

### How it works, step by step

1. `PageRequest.of(0, 2, Sort.by("startsAt"))` only builds a small object in memory. Nothing goes
   to the database yet. It does check the numbers at once: the size must be at least 1, and the
   page number at least 0.
2. `findAll(firstPage)` hands the request to Spring Data. Spring Data checks that `startsAt` is a
   field of `EventEntity`. A sort is just a value your code builds while it runs, so this check
   can only happen now, at the call, never at startup.
3. Spring Data works out how many rows to skip: page number × page size. Page 0 skips 0 rows,
   page 1 skips 2, page 3 skips 6.
4. It asks the database for the rows, sorted by `starts_at`: skip that many, then take 2.
5. It asks the database for the total number of rows (7), and works out the total pages:
   7 rows ÷ 2 per page = 3.5, rounded up to 4.
6. It puts the rows and the totals into a `Page` and returns it.
7. `page.nextPageable()` builds the request for the next page. You call `findAll` again with it.

The skip happens on the **sorted** list. That's why the sort matters: it decides which rows count
as "first". Steps 4 and 5 are two separate SQL statements in the log. The next lesson reads them
line by line.

### Example

`scratch-app/src/main/java/com/zenith/data/PageDemo.java` asks for page 0, prints every part of
the answer, then walks through the other pages. It is a `CommandLineRunner`, so Spring calls its
`run` method once, right after startup. `@Profile("page")` makes it run only with the profile
`page`. The `while` loop keeps asking for `nextPageable()` until `hasNext()` is false.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Lesson 12: ask for one page of events, read the answer, then walk through the other pages.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=page
@Component
@Profile("page")
class PageDemo implements CommandLineRunner {

    private final EventRepository events;

    PageDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        // page number (counting starts at 0), rows per page, sort by the entity field startsAt
        Pageable firstPage = PageRequest.of(0, 2, Sort.by("startsAt"));
        System.out.println(">>> asked for: " + firstPage);

        Page<EventEntity> page = events.findAll(firstPage);
        System.out.println(">>> getContent():          " + names(page));
        System.out.println(">>> getNumber():           " + page.getNumber());
        System.out.println(">>> getSize():             " + page.getSize());
        System.out.println(">>> getNumberOfElements(): " + page.getNumberOfElements());
        System.out.println(">>> getTotalElements():    " + page.getTotalElements());
        System.out.println(">>> getTotalPages():       " + page.getTotalPages());
        System.out.println(">>> hasNext():             " + page.hasNext());

        while (page.hasNext()) {
            page = events.findAll(page.nextPageable());
            System.out.println(">>> page " + page.getNumber() + ": " + names(page) + "  hasNext() = " + page.hasNext());
        }
    }

    private static List<String> names(Page<EventEntity> page) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : page.getContent()) {
            names.add(e.getName());
        }
        return names;
    }
}
```

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=page
```

Real output, with the SQL lines left out (the next lesson reads those):

```text
... Started DataApplication in 2.179 seconds (process running for 2.346)
>>> asked for: Page request [number: 0, size 2, sort: startsAt: ASC]
>>> getContent():          [Monsoon Rock Night, Jazz at the Note]
>>> getNumber():           0
>>> getSize():             2
>>> getNumberOfElements(): 2
>>> getTotalElements():    7
>>> getTotalPages():       4
>>> hasNext():             true
>>> page 1: [Classical Evening, Open Mic Night]  hasNext() = true
>>> page 2: [Sufi Night, Blues Brunch]  hasNext() = true
>>> page 3: [Indie Fest]  hasNext() = false
```

### Walkthrough

These are the 7 events in the database, earliest first (from psql), and the page each one landed
on:

| # | Event | `starts_at` (UTC) | Page (2 per page) |
| --- | --- | --- | --- |
| 1 | Monsoon Rock Night | 2026-11-20 13:30 | 0 |
| 2 | Jazz at the Note | 2026-11-28 15:30 | 0 |
| 3 | Classical Evening | 2026-12-05 13:00 | 1 |
| 4 | Open Mic Night | 2026-12-12 14:30 | 1 |
| 5 | Sufi Night | 2027-07-10 14:00 | 2 |
| 6 | Blues Brunch | 2027-07-18 06:30 | 2 |
| 7 | Indie Fest | 2027-07-24 11:30 | 3 |

1. `asked for: Page request [number: 0, size 2, sort: startsAt: ASC]` is the request, printed.
   `ASC` means ascending, so earliest first.
2. `getContent()` holds rows 1 and 2 of the sorted list.
3. `getNumber()` is 0, `getSize()` is 2, and `getNumberOfElements()` is 2. So this is page 0: we
   asked for 2 rows and got 2.
4. `getTotalElements()` is 7: it counts every event, not just this page. `getTotalPages()` is 4,
   because 7 ÷ 2 = 3.5, rounded up.
5. `hasNext()` was true, so the loop asked for `nextPageable()` and got page 1, then 2, then 3.
6. Page 3 holds only `[Indie Fest]`, 1 row, which is fewer than the size. `hasNext()` is false
   there, so the loop stopped.

### Important terms

| Term | Meaning |
| --- | --- |
| **Paging** | Showing a long list one small piece (a page) at a time. |
| **Page size** | How many rows one page holds. |
| **Page number** | Which page you want. Spring Data counts from 0. |
| **Ascending (`ASC`) / descending (`DESC`)** | Smallest first / largest first. For dates: earliest first / latest first. |
| **`Sort`** | The order of the rows, by entity field names, such as `Sort.by("startsAt")`. |
| **`Pageable`** | The type that describes one page: page number, page size and sort. |
| **`PageRequest`** | The ready-made `Pageable`, made with `PageRequest.of(...)`. |
| **`Page<T>`** | The answer: this page's rows plus the totals for all pages. |

### Where it is used

- Any list screen with page numbers or a "Next" button: event listings, search results, admin
  tables.
- Web APIs such as `GET /events?page=0&size=20`. Spring can build the `Pageable` from those
  web-address parameters for you, so the code that handles the request simply receives it.
- Your own query methods can take a `Pageable` too. Add a `Pageable` parameter (usually the last
  one) and return `Page<...>`. A temporary interface had this derived query:

  ```java
  Page<EventEntity> findByVenueId(UUID venueId, Pageable pageable);
  ```

  Called for Riverside Arena with `PageRequest.of(0, 3, Sort.by("startsAt"))`, it returned
  `[Monsoon Rock Night, Classical Evening, Sufi Night]`, with `getTotalElements()` 4 and
  `getTotalPages()` 2. The total counted only Riverside's events.

### Edge cases

- **A page past the end is empty, not an error.** `PageRequest.of(10, 2, Sort.by("startsAt"))`
  gave an empty list, with `getNumberOfElements()` 0, `getTotalElements()` 7,
  `getTotalPages()` 4 and `hasNext()` false.
- **The last page can be smaller.** Page 3 held only `[Indie Fest]`.
- **Newest first.** `PageRequest.of(0, 2, Sort.by("startsAt").descending())` gave
  `[Indie Fest, Blues Brunch]`.
- **A paged derived query counts only its own rows.** `findByVenueId` for Riverside Arena gave
  `getTotalElements()` 4, not 7.

### Common mistakes — with the real errors

1. **Counting pages from 1.** In `PageDemo`, `PageRequest.of(0, 2, ...)` was changed to
   `PageRequest.of(1, 2, ...)`. Real output, with the SQL lines left out:

   ```text
   >>> asked for: Page request [number: 1, size 2, sort: startsAt: ASC]
   >>> getContent():          [Classical Evening, Open Mic Night]
   >>> getNumber():           1
   >>> getSize():             2
   >>> getNumberOfElements(): 2
   >>> getTotalElements():    7
   >>> getTotalPages():       4
   >>> hasNext():             true
   >>> page 2: [Sufi Night, Blues Brunch]  hasNext() = true
   >>> page 3: [Indie Fest]  hasNext() = false
   ```

   There was no error, and the exit code was 0. But Monsoon Rock Night and Jazz at the Note never
   appear. This is the dangerous kind of mistake, because nothing tells you about it. Screens
   usually show "page 1" to people, so the code must subtract 1 before calling
   `PageRequest.of`.

2. **Sorting by a column name, or misspelling the field.** `Sort` uses the entity's field names,
   not column names. Both of these apps started fine, and the error came at the call:

   | You wrote | What happened at the call |
   | --- | --- |
   | `Sort.by("startAt")` | `PropertyReferenceException: No property 'startAt' found for type 'EventEntity'; Did you mean 'startsAt'` |
   | `Sort.by("starts_at")` | `PropertyReferenceException: No property 'starts' found for type 'EventEntity'; Did you mean 'startsAt'` |

   Why does the second one complain about `'starts'`? Spring Data reads `_` in a property name
   as "go inside". So `starts_at` means "the field `at` inside the field `starts`".
   `EventEntity` has no field `starts`, so that's the name it reports.

   Here is the real output of the first one, run through `PageDemo` (shortened):

   ```text
   ... Started DataApplication in 2.089 seconds (process running for 2.252)
   >>> asked for: Page request [number: 0, size 2, sort: startAt: ASC]
   ... Application run failed

   org.springframework.data.mapping.PropertyReferenceException: No property 'startAt' found for type 'EventEntity'; Did you mean 'startsAt'
   ...
   	at com.zenith.data.PageDemo.run(PageDemo.java:32) ~[classes/:na]
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Started DataApplication` first | Startup can't check a sort. It is only a value your code builds while it runs. |
   | `asked for: ... sort: startAt: ASC` | Building the request worked. `PageRequest` knows nothing about entities. |
   | `No property 'startAt' found for type 'EventEntity'` | At the call, Spring Data compared the sort field with `EventEntity`'s fields and found no match. |
   | `Did you mean 'startsAt'` | The closest real field name. |
   | `PageDemo.java:32` | The line `events.findAll(firstPage)`. |
   | No `Hibernate:` lines after `asked for` | The call stopped before any SQL was built. |

3. **Paging without a sort.** `PageRequest.of(0, 2)` printed `sort: UNSORTED`, and the SQL for
   the rows had no `order by` at all:

   ```text
   Hibernate: 
       select
           ee1_0.id,
           ee1_0.name,
           ee1_0.slug,
           ee1_0.starts_at,
           ee1_0.venue_id 
       from
           event ee1_0 
       offset
           ? rows 
       fetch
           first ? rows only
   ```

   Without `order by`, Postgres promises no order. Here the rows happened to come back as
   `[Monsoon Rock Night, Jazz at the Note]`, but that was luck. After rows are updated or
   deleted, the order can change between two requests, so an event can show up on two pages, or
   on none. Always sort. If two rows could have the same value in the sort field, add a second
   sort field that is unique, such as `id`, so ties are broken the same way every time.

4. **A page size of 0, or a negative page number.** A temporary check called
   `PageRequest.of(0, 0)` and then `PageRequest.of(-1, 2)`. The real messages:

   ```text
   java.lang.IllegalArgumentException: Page size must not be less than one
   java.lang.IllegalArgumentException: Page index must not be less than zero
   ```

   `PageRequest.of` refuses them right away, before anything reaches the database. This matters
   when page numbers come from users, such as `?page=-1` in a web address.

### Link to the task

S5 says: "Add paging with `Pageable` and `Page<EventEntity>`, then look at the SQL log. There
are two statements: one for the rows and one `count` for the total."

- This lesson does the first half. `PageDemo` asks for pages with a `Pageable`
  (`PageRequest.of(0, 2, Sort.by("startsAt"))`) and reads the `Page<EventEntity>` it gets back:
  2 rows per page, 7 rows in total, 4 pages.
- Still to do in S5: find the two statements in the log.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository extends JpaRepository<EventEntity, UUID>`. That is what brings
  `findAll(Pageable)`, so you don't change the repository.
- Postgres runs in the Docker container `pg`, with the 7 seed events.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/PageDemo.java`
with the 54 lines from the example.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=page
```

You should see these `>>>` lines, with `Hibernate:` SQL lines between them. Leave the SQL lines
for the next lesson.

```text
>>> asked for: Page request [number: 0, size 2, sort: startsAt: ASC]
>>> getContent():          [Monsoon Rock Night, Jazz at the Note]
>>> getNumber():           0
>>> getSize():             2
>>> getNumberOfElements(): 2
>>> getTotalElements():    7
>>> getTotalPages():       4
>>> hasNext():             true
>>> page 1: [Classical Evening, Open Mic Night]  hasNext() = true
>>> page 2: [Sufi Night, Blues Brunch]  hasNext() = true
>>> page 3: [Indie Fest]  hasNext() = false
```

If the build stops with `PageDemo.java:[28,30] cannot find symbol` and
`symbol: variable PageRequest`, the line `import org.springframework.data.domain.PageRequest;`
is missing. Java doesn't know the name `PageRequest`, and because a dot follows it, Java guesses
it is a variable.

**Step 3 — count from 1.** On line 29, change `PageRequest.of(0, 2, ...)` to
`PageRequest.of(1, 2, ...)`, and run the same command. You should see
`asked for: Page request [number: 1, size 2, sort: startsAt: ASC]` and
`getContent(): [Classical Evening, Open Mic Night]`, then pages 2 and 3. There is no error, but
Monsoon Rock Night and Jazz at the Note never appear. Change it back to `0`.

**Step 4 — misspell the sort field.** Change `Sort.by("startsAt")` to `Sort.by("startAt")`, and
run the same command. You should see:

- `Started DataApplication`;
- then `>>> asked for: Page request [number: 0, size 2, sort: startAt: ASC]`;
- then `Application run failed`, with
  `PropertyReferenceException: No property 'startAt' found for type 'EventEntity'; Did you mean 'startsAt'`;
- no `Hibernate:` lines after the `asked for` line.

Change it back to `startsAt`.

**Step 5 — run it once more.** Use the command from step 2. You should get the same output as
in step 2.

**Done when:**

- you have seen page 0 hold Monsoon Rock Night and Jazz at the Note, with 7 rows in total and
  4 pages;
- the loop walked through pages 1, 2 and 3, and stopped when `hasNext()` was false;
- you have seen page 1 skip the first two events without any error;
- you have seen the misspelled sort field fail only at the call, after `Started DataApplication`;
- both changes are undone, and step 5 printed the same as step 2.

### Summary for revision

- `PageRequest.of(pageNumber, pageSize, sort)` asks for one page. Page numbers start at 0.
- `findAll(Pageable)` comes with `JpaRepository`. Any query method can take a `Pageable` and
  return a `Page`.
- `Page` gives this page's rows (`getContent()`) and the totals (`getTotalElements()`,
  `getTotalPages()`, `hasNext()`).
- Rows skipped = page number × page size, counted on the sorted list.
- `Sort` uses entity field names. Mistakes fail at the call, with "Did you mean".
- Always sort when paging. Without `order by`, pages can overlap or miss rows.
- Counting from 1 by mistake gives no error. It silently hides the first page.

### Practice questions, with answers

**Question 1.** The `event` table has 7 events. Earliest first, they are: Monsoon Rock Night, Jazz
at the Note, Classical Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest.
`EventRepository extends JpaRepository<EventEntity, UUID>`, and the code runs:

```java
Page<EventEntity> page = events.findAll(PageRequest.of(2, 3, Sort.by("startsAt")));
```

What do `getContent()` (just the names), `getNumber()`, `getNumberOfElements()`,
`getTotalElements()`, `getTotalPages()` and `hasNext()` return? Explain how you got each one.

**Answer.** This exact call was run for real. It returned `[Indie Fest]`, page number 2, 1 row on
the page, 7 rows in total, 3 pages, and no next page.

- `getContent()` is `[Indie Fest]`. Page 2 with 3 rows per page skips 2 × 3 = 6 rows: Monsoon
  Rock Night, Jazz at the Note, Classical Evening, Open Mic Night, Sufi Night and Blues Brunch.
  Only Indie Fest is left.
- `getNumber()` is `2`, the page that was asked for.
- `getNumberOfElements()` is `1`, because only one row was left after the skip.
- `getTotalElements()` is `7`. It counts every event, not just this page.
- `getTotalPages()` is `3`: 7 ÷ 3 = 2.33..., rounded up. The pages are 0, 1 and 2.
- `hasNext()` is `false`, because page 2 is the last of the pages 0, 1 and 2.

**Question 2.** Same 7 events, earliest first: Monsoon Rock Night, Jazz at the Note, Classical
Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest. A "newest first" list shows 3
events per page:

```java
Page<EventEntity> page = events.findAll(PageRequest.of(1, 3, Sort.by("startsAt").descending()));
```

Which events are on this page, and in what order? How many pages are there, and is there a next
page?

**Answer.** This exact call was run for real.

- Newest first, the order is: Indie Fest, Blues Brunch, Sufi Night, Open Mic Night, Classical
  Evening, Jazz at the Note, Monsoon Rock Night.
- Page 1 with 3 per page skips 1 × 3 = 3 rows (Indie Fest, Blues Brunch, Sufi Night) and takes
  the next 3.
- So the page holds `[Open Mic Night, Classical Evening, Jazz at the Note]`, in that order,
  newest first.
- `getTotalPages()` is `3` (7 ÷ 3, rounded up), and `hasNext()` is `true`: page 2, which holds
  only Monsoon Rock Night, is still to come.

---

## Lesson 13 — The two statements behind a page (finishes subtask S5)

### Definition

Some words first:

- A **statement** is one SQL command sent to the database. In the log, each one starts with
  `Hibernate:`.
- The **rows query** is the statement that fetches the rows of one page.
- The **count query** is the statement that counts all the matching rows, so the totals can be
  filled in. It looks like `select count(...) from ...`.
- `offset ? rows` is SQL for "skip this many rows first". `fetch first ? rows only` is SQL for
  "then give me at most this many rows". (You may have seen `limit 2 offset 0`, which means the
  same thing.)
- The **binding log** shows the value put into each `?`.
- **`Slice<EventEntity>`** is a page without totals. It knows its own rows, and whether there is
  a next slice. It does not know how many rows or pages there are in all.

When you ask for a `Page` with `events.findAll(PageRequest.of(0, 2, Sort.by("startsAt")))`, it
looks like one call. In the log, it is usually **two** statements:

| Statement | Real SQL (shortened) | Its job |
| --- | --- | --- |
| Rows query | `select ... from event ee1_0 order by ee1_0.starts_at offset ? rows fetch first ? rows only` | Fetch this page's rows: skip (page number × page size), take (page size). |
| Count query | `select count(ee1_0.id) from event ee1_0` | Count every row, for `getTotalElements()` and `getTotalPages()`. |

`ee1_0` is the short name (alias) Hibernate gave the table `event`. `count(ee1_0.id)` counts the
rows by their `id`.

### Why it is needed

- If you don't know about the second statement, it's a surprise in the log. And when a list
  page gets slow, you might look at the wrong statement.
- The count can be the expensive one. Postgres keeps no ready-made row count, so to answer
  `count` it has to go through all the matching rows. On a table with millions of rows, counting
  can take longer than fetching 20 rows.
- Many screens never show a total. An endless list with a "Load more" button only needs to know
  "is there more?". For those, the count is wasted work, and a `Slice` skips it.

### Key points

- A `Page` usually costs two statements: the rows query and the count query.
- The rows query skips (page number × page size) rows and takes (page size) rows.
- The count query counts all matching rows, with the same condition as the rows query.
- Spring Data skips the count when a page comes back not full. Then it must be the last page,
  and the total is rows skipped + rows on this page. An empty page past the end still needs a
  count.
- A `Slice` never counts. It asks for one extra row to answer `hasNext()`.
- A `Slice` has no `getTotalElements()` and no `getTotalPages()`.
- To get a `Slice` of every row, declare `Slice<EventEntity> findAllBy(Pageable pageable)`.
  The name `findAll` would clash with the inherited `Page<EventEntity> findAll(Pageable)`.

### How it works, step by step

**A `Page`:**

1. Spring Data sends the rows query: skip (page number × page size) rows, take (page size) rows.
2. It looks at what came back. If the page is **full**, rows might follow, so it can't know the
   total. It sends the count query.
3. If the page is **not full** (fewer rows than the page size), this must be the last page.
   Spring Data works out the total itself: rows skipped + rows on this page. It sends no count
   query.
4. One exception: a page past the end comes back **empty**. An empty page can't show where the
   rows ended, so Spring Data counts.

**A `Slice`:**

1. Spring Data asks for one row more than the size: size + 1.
2. If all size + 1 rows come back, there is at least one more row, so `hasNext()` is true. The
   slice keeps `size` rows and throws the extra one away.
3. If fewer come back, this is the last slice, and `hasNext()` is false.
4. It never sends a count query. That's why a `Slice` has no `getTotalElements()` and no
   `getTotalPages()`.

**Why the method is called `findAllBy`, and not `findAll`.** `JpaRepository` already has
`Page<EventEntity> findAll(Pageable)`. Java doesn't allow a second method with the same name and
the same parameters but a different return type. `findAllBy(Pageable)` is a **derived query**:
Spring Data builds the query from the method name. Nothing after `By` means "no condition", so
it returns every event. `All` is just a readable word that Spring Data doesn't need. The return
type `Slice` tells Spring Data to skip the count.

### Example

`scratch-app/src/main/java/com/zenith/data/EventRepository.java` gets two imports and the
`Slice` method:

```java
package com.zenith.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);

    // Lesson 10: a hand-written query in JPQL. It names the entity class and its fields, not the table and columns.
    @Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
    List<EventEntity> eventsAtVenueJpql(UUID venueId);

    // Lesson 11: the same query in native SQL. It names the table and its columns, exactly as Postgres knows them.
    @Query(value = "select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)
    List<EventEntity> eventsAtVenueNative(UUID venueId);

    // Lesson 13: a Slice is a page without totals, so Spring Data never runs the count query.
    // Nothing after "By" means no condition: every event, one slice at a time.
    Slice<EventEntity> findAllBy(Pageable pageable);
}
```

`scratch-app/src/main/java/com/zenith/data/CountDemo.java` makes three requests and prints a
label before each one, so you can tell which SQL belongs to which request. It is a
`CommandLineRunner`, so Spring calls its `run` method once, right after startup.
`@Profile("count")` makes it run only with the profile `count`.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Lesson 13: three page requests, so you can see in the log which SQL statements each one sends.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=count
@Component
@Profile("count")
class CountDemo implements CommandLineRunner {

    private final EventRepository events;

    CountDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. Page, page 0, size 2");
        Page<EventEntity> first = events.findAll(PageRequest.of(0, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(first.getContent()) + "  total: " + first.getTotalElements());

        System.out.println(">>> 2. Page, page 3, size 2");
        Page<EventEntity> last = events.findAll(PageRequest.of(3, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(last.getContent()) + "  total: " + last.getTotalElements());

        System.out.println(">>> 3. Slice, page 0, size 2");
        Slice<EventEntity> slice = events.findAllBy(PageRequest.of(0, 2, Sort.by("startsAt")));
        System.out.println(">>>    " + names(slice.getContent()) + "  hasNext: " + slice.hasNext());
    }

    private static List<String> names(List<EventEntity> found) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : found) {
            names.add(e.getName());
        }
        return names;
    }
}
```

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=count -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

Real output after startup, in full. Only the time stamps on the binding lines are cut.

```text
... Started DataApplication in 1.932 seconds (process running for 2.078)
>>> 1. Page, page 0, size 2
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    order by
        ee1_0.starts_at 
    offset
        ? rows 
    fetch
        first ? rows only
... binding parameter (1:INTEGER) <- [0]
... binding parameter (2:INTEGER) <- [2]
Hibernate: 
    select
        count(ee1_0.id) 
    from
        event ee1_0
>>>    [Monsoon Rock Night, Jazz at the Note]  total: 7
>>> 2. Page, page 3, size 2
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    order by
        ee1_0.starts_at 
    offset
        ? rows 
    fetch
        first ? rows only
... binding parameter (1:INTEGER) <- [6]
... binding parameter (2:INTEGER) <- [2]
>>>    [Indie Fest]  total: 7
>>> 3. Slice, page 0, size 2
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    order by
        ee1_0.starts_at 
    fetch
        first ? rows only
... binding parameter (1:INTEGER) <- [3]
>>>    [Monsoon Rock Night, Jazz at the Note]  hasNext: true
```

### Walkthrough

The 7 events, earliest first, are: Monsoon Rock Night, Jazz at the Note, Classical Evening, Open
Mic Night, Sufi Night, Blues Brunch, Indie Fest.

| Request | Rows back | Statements | Values in the rows query | Count query? |
| --- | --- | --- | --- | --- |
| 1. Page, page 0, size 2 | 2 (full) | 2 | skip 0, take 2 | Yes |
| 2. Page, page 3, size 2 | 1 (not full) | 1 | skip 6, take 2 | No |
| 3. Slice, page 0, size 2 | 3 fetched, 2 kept | 1 | take 3 | Never |

1. **Request 1** shows the two statements S5 talks about. First comes the rows query, with `[0]`
   in the skip and `[2]` in the take. Then comes `select count(ee1_0.id) from event ee1_0`, with
   no `order by` and no skip: it counts every event. The page was full, so Spring Data had to
   count, and it printed `total: 7`.
2. **Request 2** skipped 6 rows (page 3 × size 2) and asked for 2, but only Indie Fest came back.
   The page was not full, so it had to be the last page. There was no count query, yet the total
   was still 7: Spring Data worked it out as 6 skipped + 1 here.
3. **Request 3** is the `Slice`. It asked for `[3]` rows, which is size 2 + 1. All 3 came back,
   so `hasNext: true`, and the slice kept 2. There was no count query. This SQL also has no
   `offset` part, because on page 0 there is nothing to skip. Request 1 sent "skip 0" instead;
   both mean the same.

More real results from a temporary check:

| Request | Statements | Result |
| --- | --- | --- |
| Page, page 0, size 10 | 1 | All 7 events. The page was not full, so the total (7) came without a count. |
| Page, page 5, size 3 | 2 | An empty page past the end, so Spring Data had to count: total 7. |
| Slice, page 1, size 2 | 1 (skip 2, take 3) | `[Classical Evening, Open Mic Night]`, `hasNext` true. |

**The count uses the same condition as the rows.** A temporary interface had the paged derived
query `Page<EventEntity> findByVenueId(UUID venueId, Pageable pageable)`. Called for Riverside
Arena with `PageRequest.of(0, 3, Sort.by("startsAt"))`, its two real statements were:

```text
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.venue_id=? 
    order by
        ee1_0.starts_at 
    fetch
        first ? rows only
... binding parameter (1:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (2:INTEGER) <- [3]
Hibernate: 
    select
        count(ee1_0.id) 
    from
        event ee1_0 
    where
        ee1_0.venue_id=?
... binding parameter (1:UUID) <- [11111111-1111-1111-1111-111111111111]
```

The count has the same `where ee1_0.venue_id=?`, so it counted only Riverside's events: the
total was 4.

### Important terms

| Term | Meaning |
| --- | --- |
| **Statement** | One SQL command sent to the database. Each one starts with `Hibernate:` in the log. |
| **Rows query** | The statement that fetches one page's rows. |
| **Count query** | The statement that counts all matching rows, for the totals. |
| **`offset ? rows`** | SQL for "skip this many rows first". |
| **`fetch first ? rows only`** | SQL for "then give me at most this many rows". |
| **Full page** | A page that came back with as many rows as the page size. |
| **`Slice<T>`** | A page without totals. It knows its rows and `hasNext()`, and never counts. |

### Where it is used

- **`Page`** fits screens that show totals or numbered pages: "Page 3 of 120", "1,204 results",
  admin tables with page buttons.
- **`Slice`** fits endless lists, "Load more" buttons and phone feeds. It also fits background
  jobs that walk through a big table piece by piece and never need a total.
- In later performance work, slow list pages are common, and the count query is often the slow
  part. Knowing it is there is the first step.

### Edge cases

- **A first page that isn't full needs no count.** Page 0 with size 10 sent one statement and
  still reported 7 rows in total.
- **An empty page past the end still counts.** Page 5 with size 3 sent two statements.
- **A slice on page 0 has no `offset` part.** On later pages it has one: page 1 with size 2
  skipped 2 and took 3.
- **The count of a derived query keeps its condition.** `findByVenueId` counted with
  `where ee1_0.venue_id=?`, and got 4.

### Common mistakes — with the real errors

1. **Expecting one statement per page.** This is not an error, but it surprises people. In
   request 1, one `findAll` call printed two `Hibernate:` blocks: the rows query, then
   `select count(ee1_0.id) from event ee1_0`. That's normal for a full `Page`.

2. **Paying for a count nobody shows.** A "Load more" list built on `Page` sends a count query
   for every full page, even though the screen never shows a total. A `Slice` answers "is there
   more?" with the one extra row instead, in a single statement.

3. **Asking a `Slice` for totals.** In `CountDemo`, `"  hasNext: " + slice.hasNext()` was
   changed to `"  total: " + slice.getTotalElements()`. The build stopped:

   ```text
   [ERROR] .../CountDemo.java:[38,87] cannot find symbol
   [ERROR]   symbol:   method getTotalElements()
   [ERROR]   location: variable slice of type org.springframework.data.domain.Slice<com.zenith.data.EventEntity>
   ```

   `cannot find symbol` with `symbol: method getTotalElements()` means the type `Slice` has no
   such method. A slice never counts, so it has no total to give.

4. **Naming the `Slice` method `findAll`.** In `EventRepository`, `findAllBy` was changed to
   `findAll`. The build stopped with several errors. The first one is the real cause:

   ```text
   [ERROR] .../EventRepository.java:[35,24] findAll(org.springframework.data.domain.Pageable) in com.zenith.data.EventRepository clashes with findAll(org.springframework.data.domain.Pageable) in org.springframework.data.repository.PagingAndSortingRepository
   [ERROR]   return type org.springframework.data.domain.Slice<com.zenith.data.EventEntity> is not compatible with org.springframework.data.domain.Page<com.zenith.data.EventEntity>
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `clashes with findAll(...) in ...PagingAndSortingRepository` | `EventRepository` already inherits a `findAll(Pageable)` (through `JpaRepository`), and yours has the same name and parameter. |
   | `return type ...Slice<...> is not compatible with ...Page<...>` | The two methods differ only in what they return. Java doesn't allow that. |
   | The errors after it | `CountDemo` and `PageDemo` now get a `Slice` where they expect a `Page`, and `findAllBy` no longer exists. They all go away once the first error is fixed. |

### Link to the task

S5 says: "Add paging with `Pageable` and `Page<EventEntity>`, then look at the SQL log. There
are two statements: one for the rows and one `count` for the total."

- The two statements are in the log. The rows query is
  `... order by ee1_0.starts_at offset ? rows fetch first ? rows only`, with `[0]` and `[2]`.
  The count query is `select count(ee1_0.id) from event ee1_0`.
- On top of that, this lesson shows when the count is skipped: on a page that isn't full, and
  always with a `Slice`.
- **S5 is done.**

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` ends with the native method `eventsAtVenueNative` (30 lines).
- `PageDemo.java` exists.
- Postgres runs in the Docker container `pg`, with the 7 seed events.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the `Slice` method and the demo.**

- In `scratch-app/src/main/java/com/zenith/data/EventRepository.java`, add the two imports
  (`org.springframework.data.domain.Pageable` and `org.springframework.data.domain.Slice`).
  Then add the 3 lines for Lesson 13 (two comment lines and the method) before the last `}`. The
  file then matches the 36-line version in the example.
- Create `scratch-app/src/main/java/com/zenith/data/CountDemo.java` with the 48 lines from the
  example.

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=count -Dspring-boot.run.arguments=--logging.level.org.hibernate.orm.jdbc.bind=trace
```

You should see the output from the example:

- after `>>> 1. Page, page 0, size 2`: two `Hibernate:` blocks. The rows query has
  `offset ? rows` and `fetch first ? rows only`, with bindings `[0]` and `[2]`. Then comes
  `select count(ee1_0.id) from event ee1_0`, and then
  `>>>    [Monsoon Rock Night, Jazz at the Note]  total: 7`;
- after `>>> 2. Page, page 3, size 2`: one `Hibernate:` block, with bindings `[6]` and `[2]`,
  then `>>>    [Indie Fest]  total: 7`;
- after `>>> 3. Slice, page 0, size 2`: one `Hibernate:` block with only
  `fetch first ? rows only` and binding `[3]`, then
  `>>>    [Monsoon Rock Night, Jazz at the Note]  hasNext: true`.

If the build stops with `EventRepository.java:[34,5] cannot find symbol` and
`symbol: class Slice`, the line `import org.springframework.data.domain.Slice;` is missing.

**Step 3 — ask the slice for a total.** On line 38 of `CountDemo.java`, change
`"  hasNext: " + slice.hasNext()` to `"  total: " + slice.getTotalElements()`, and run the same
command. The build should stop with `CountDemo.java:[38,87] cannot find symbol`,
`symbol: method getTotalElements()`. Change it back.

**Step 4 — name it `findAll`.** In `EventRepository.java`, change `findAllBy` to `findAll`, and
run the same command. The build should stop with several errors. The first should be
`EventRepository.java:[35,24] findAll(org.springframework.data.domain.Pageable) in com.zenith.data.EventRepository clashes with findAll(org.springframework.data.domain.Pageable) in org.springframework.data.repository.PagingAndSortingRepository`,
followed by `return type ...Slice<...> is not compatible with ...Page<...>`. Change it back to
`findAllBy`.

**Step 5 — run it once more.** Use the command from step 2. You should get the same output as
in step 2.

**Done when:**

- you have found both statements for request 1: the rows query and
  `select count(ee1_0.id) from event ee1_0`;
- you have seen request 2 send no count, yet print `total: 7`;
- you have seen the slice ask for 3 rows (size 2 + 1), send no count, and print
  `hasNext: true`;
- you have seen both compile errors, and undone both changes;
- step 5 printed the same as step 2.

### Summary for revision

- A `Page` usually means two statements: the rows query (skip, take) and the count query.
- The count keeps the query's condition, and it can be the slow one on big tables.
- Spring Data skips the count when a page isn't full. An empty page past the end still counts.
- A `Slice` never counts. It fetches size + 1 rows to answer `hasNext()`.
- A `Slice` has no totals: `getTotalElements()` doesn't exist on it.
- `Slice<EventEntity> findAllBy(Pageable)` gives every event as slices. The name `findAll`
  would clash with the inherited `Page<EventEntity> findAll(Pageable)`.
- Use `Page` when the screen shows totals, and `Slice` for "Load more" lists.

### Practice questions, with answers

**Question 1.** The `event` table has 7 events. Earliest first, they are: Monsoon Rock Night, Jazz
at the Note, Classical Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest.
`EventRepository` has `Page<EventEntity> findAll(Pageable)` (inherited from `JpaRepository`) and
`Slice<EventEntity> findAllBy(Pageable)`. For each call, how many SQL statements are sent, and
is one of them a count query? Say why, and what comes back.

- (a) `events.findAll(PageRequest.of(1, 3, Sort.by("startsAt")))`
- (b) `events.findAll(PageRequest.of(2, 3, Sort.by("startsAt")))`
- (c) `events.findAllBy(PageRequest.of(2, 3, Sort.by("startsAt")))`
- (d) `events.findAll(PageRequest.of(5, 3, Sort.by("startsAt")))`

**Answer.** All four calls were run for real, with the binding log on.

- (a) **2 statements, with a count.** The rows query skipped 3 and took 3 (bindings `[3]` and
  `[3]`). It got `[Open Mic Night, Sufi Night, Blues Brunch]`, a full page, so rows might follow
  and Spring Data had to count. Total 7, 3 pages, `hasNext()` true.
- (b) **1 statement, no count.** The rows query skipped 6 and took 3 (`[6]` and `[3]`). Only
  `[Indie Fest]` came back, so the page was not full and had to be the last one. Spring Data
  worked out the total as 6 + 1 = 7. 3 pages, `hasNext()` false.
- (c) **1 statement, no count, ever.** It is a `Slice`, so the rows query skipped 6 and took 4
  (`[6]` and `[4]`): size 3 + 1. Only `[Indie Fest]` came back, so `hasNext()` is false.
- (d) **2 statements, with a count.** The rows query skipped 15 and took 3 (`[15]` and `[3]`).
  Nothing came back. An empty page past the end can't show where the rows ended, so Spring Data
  counted: total 7, 3 pages, `hasNext()` false.

**Question 2.** Same 7 events, earliest first: Monsoon Rock Night, Jazz at the Note, Classical
Evening, Open Mic Night, Sufi Night, Blues Brunch, Indie Fest. `findAllBy(Pageable)` returns a
`Slice<EventEntity>`. The code runs, with the binding log on:

```java
Slice<EventEntity> slice = events.findAllBy(PageRequest.of(3, 2, Sort.by("startsAt")));
```

- (a) Which two values go into the rows query's `?`s (the skip and the take)?
- (b) What does `getContent()` hold (just the names)?
- (c) What does `hasNext()` return, and how does the slice know without counting?

**Answer.** This exact call was run for real.

- (a) **Skip 6, take 3.** The skip is page 3 × size 2 = 6. The take is size 2 + 1 = 3, the one
  extra row a slice always asks for. The binding lines were `(1:INTEGER) <- [6]` and
  `(2:INTEGER) <- [3]`.
- (b) **`[Indie Fest]`.** After skipping the first 6 events, only the 7th is left.
- (c) **`false`.** The slice asked for 3 rows and got only 1. If any row came after this slice,
  the query would have returned more than 2. So there is no next slice, and no count was needed
  to know that. The real run printed `hasNext false` and `isLast true`.

---

## Lesson 14 — `@Transactional`: one method, one transaction (starts subtask S6)

### Definition

Some words first:

- A **transaction** is a group of database statements that the database treats as one unit. It
  begins, the statements run, and then it ends in one of two ways:
  - **commit**: keep every change the statements made;
  - **rollback**: throw every change away.

  This lesson shows only commit. Rollback comes in a later lesson.
- A **Spring bean** is an object that Spring creates and keeps for you, such as a class marked
  `@Component`.
- A **service** is a Spring bean that holds a piece of the app's work. You mark it with
  `@Service`. It sits between the code that asks for the work and the repository.
- The **transaction manager** is the Spring object that begins, commits and rolls back
  transactions. In this app it is `JpaTransactionManager`. Spring Boot created it, because the
  project uses Spring Data JPA. You wrote no setup code for it.

`@Transactional` is a Spring annotation. Its full name is
`org.springframework.transaction.annotation.Transactional`. You put it on a public method of a
Spring bean, usually a service. It tells Spring: "begin a transaction before this method runs,
and commit it after the method returns." Every repository call inside the method then runs in
that one transaction.

### Why it is needed

- Without `@Transactional`, a method that calls the repository twice makes **two** separate
  transactions, one per call. The result looks the same, so you can't tell from the result. Only
  the log shows it.
- Separate transactions are a problem when the work must happen as one piece. Think of booking
  tickets: "save the order" and "lower the number of free seats". If the first one is committed
  and the second one fails, the database is left half done. One transaction around the whole
  method makes all its steps one unit.
- Hibernate can notice that you changed an entity and write the change for you, but only while
  a transaction is still open around your change. One transaction around the whole method gives
  you that. Subtask S6 depends on exactly this.

### Key points

- With `@Transactional` on a method, Spring begins a transaction before the method's first line.
  It commits after the method returns, before the caller gets the result.
- Inside that method, every repository call joins the one transaction. The log says
  `Participating in existing transaction`.
- Without it, each inherited repository method (`count()`, `findById()`, ...) runs in its own
  small transaction. That transaction comes from `SimpleJpaRepository`, and reading methods
  mark it `readOnly`.
- Query methods you declare yourself, like `existsBySlug`, have no transaction of their own.
  Outside a transaction they run with none. Inside yours, they run in yours.
- `PROPAGATION_REQUIRED` is the default: join the running transaction, or begin one if none is
  running.
- `ISOLATION_DEFAULT` means the database's own isolation level. For Postgres, that is
  `read committed`.
- Spring adds transactions only to beans that Spring creates. An object you make with `new`
  gets none, and nothing warns you.
- The transaction log is turned on with
  `--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG`.

### How it works, step by step

When some code calls a method marked `@Transactional`:

1. **Begin.** Before the method's first line, the transaction manager opens an
   **EntityManager** and begins a transaction. An EntityManager is Hibernate's working object
   for one piece of database work. It holds the database connection.
2. **Your lines run.** Each repository call looks for a running transaction. It finds the one
   from step 1 and joins it, instead of starting its own.
3. **Commit.** When the method returns, the transaction manager commits.
4. **Close.** It closes the EntityManager. Only then does the caller get the method's return
   value.

**Where the repository's own transactions come from.** The methods you inherit from
`JpaRepository`, such as `count()` and `findById()`, already have a transaction setting. It sits
inside `SimpleJpaRepository`, the Spring Data class that does the work behind your repository
interface. Called outside any transaction, each one begins and commits its own small
transaction. Called inside yours, each one joins yours.

**The settings in the log.** Each transaction shows its settings after its name:

- `PROPAGATION_REQUIRED`. **Propagation** is the rule for what to do when a transaction is
  already running. `REQUIRED` is the default. It means "if one is running, join it; if not, begin
  a new one". That is why the repository calls join yours.
- `ISOLATION_DEFAULT`. **Isolation** is how much one transaction can see of other transactions'
  changes that are not committed yet. `DEFAULT` means "use the database's own setting". For
  Postgres that is `read committed`: you only see changes that other transactions have already
  committed. In psql, `show transaction_isolation;` printed `read committed`.
- `readOnly`. This appears only on the repository's own reading transactions. It marks a
  transaction that only reads. A later lesson covers it.

**Turning on the log.** A **logger** is a named source of log lines. This one is named after the
class `org.springframework.orm.jpa.JpaTransactionManager`. **DEBUG** is a log level that shows
more detail than the normal `INFO` level. This command-line setting turns DEBUG on for that one
logger:

```text
--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

### Example

`scratch-app/src/main/java/com/zenith/data/EventService.java` is a new service. It has the same
two repository calls written twice: once without `@Transactional`, and once with it.

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;

    public EventService(EventRepository events) {
        this.events = events;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }
}
```

`scratch-app/src/main/java/com/zenith/data/TransactionDemo.java` is a runner that calls both
methods for Sufi Night, whose id is `aaaaaaaa-0000-0000-0000-000000000005`. It runs only with
the profile `tx`. Spring creates `EventService` and passes it into the constructor.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 14: the same work done twice - without and with @Transactional - so the log shows the difference.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=tx
@Component
@Profile("tx")
class TransactionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    TransactionDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. calling describeWithoutTransaction");
        String first = service.describeWithoutTransaction(SUFI_NIGHT);
        System.out.println(">>>    returned: " + first);

        System.out.println(">>> 2. calling describeInOneTransaction");
        String second = service.describeInOneTransaction(SUFI_NIGHT);
        System.out.println(">>>    returned: " + second);
    }
}
```

The command, in `scratch-app`:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=tx -Dspring-boot.run.arguments=--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

Each transaction line in the real log starts with the time, the level, a process number, the
thread and the logger name, like this:

```text
2026-11-02T14:12:09.268+05:30 DEBUG 78784 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [com.zenith.data.EventService.describeInOneTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
```

Below is the real output after the `Started DataApplication` line. The front part of each
transaction line (everything up to the `: `) is cut off so the lines fit:

```text
>>> 1. calling describeWithoutTransaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1972687241<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@508de524]
Hibernate: 
    select
        count(*) 
    from
        event ee1_0
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1972687241<open>)]
Closing JPA EntityManager [SessionImpl(1972687241<open>)] after transaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1635357010<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@5c39da9e]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1635357010<open>)]
Closing JPA EntityManager [SessionImpl(1635357010<open>)] after transaction
>>>    returned: Sufi Night (one of 7 events)
>>> 2. calling describeInOneTransaction
Creating new transaction with name [com.zenith.data.EventService.describeInOneTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(900432807<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@7a7698f4]
Found thread-bound EntityManager [SessionImpl(900432807<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        count(*) 
    from
        event ee1_0
Found thread-bound EntityManager [SessionImpl(900432807<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(900432807<open>)]
Closing JPA EntityManager [SessionImpl(900432807<open>)] after transaction
>>>    returned: Sufi Night (one of 7 events)
```

The numbers after `SessionImpl(` and after `@` are different on every run.

### Walkthrough

What each transaction line means:

| Log line | Meaning |
| --- | --- |
| `Creating new transaction with name [X]: ...` | A transaction begins. `X` is the method it was made for. |
| `Opened new EntityManager [SessionImpl(N<open>)] for JPA transaction` | Hibernate's working object for this transaction is opened. `SessionImpl` is Hibernate's class for it. `N` is a label for this one object: the same number means the same EntityManager. |
| `Exposing JPA transaction as JDBC [...]` | The same database connection is offered to plain JDBC code, like `JdbcClient`, so that code would join the same transaction. Nothing to do for you. |
| `Found thread-bound EntityManager [SessionImpl(N<open>)] for JPA transaction` | A repository call found the EntityManager of the running transaction. "Thread-bound" is explained below this table. |
| `Participating in existing transaction` | The repository call joined the running transaction instead of starting a new one. |
| `Initiating transaction commit`, then `Committing JPA transaction on EntityManager [...]` | The commit. |
| `Closing JPA EntityManager [...] after transaction` | Cleanup. This transaction is over. |

A **thread** is one line of work that the program runs, one step after another. Here it is the
`main` thread, shown as `[main]` in each log line. Spring ties the running transaction's
EntityManager to the thread. That way, code deeper down in the same call, like the repository,
can find it.

Part 1 compared with part 2:

| | Part 1: `describeWithoutTransaction` | Part 2: `describeInOneTransaction` |
| --- | --- | --- |
| `Creating new transaction` lines | 2: `SimpleJpaRepository.count`, then `SimpleJpaRepository.findById` | 1: `com.zenith.data.EventService.describeInOneTransaction` |
| `readOnly` in the settings | yes, on both | no |
| `Participating in existing transaction` | 0 | 2, one per repository call |
| Different `SessionImpl` numbers | 2 (`1972687241`, `1635357010`) | 1 (`900432807`) |
| Commits | 2 | 1, after both statements |
| SQL statements | the same two | the same two |
| Returned | `Sufi Night (one of 7 events)` | `Sufi Night (one of 7 events)` |

The SQL and the result are the same. The only difference is how the work was grouped:

- In part 1, `count()` was already committed before `findById()` even began.
- In part 2, both statements ran inside one transaction, and one commit at the end closed it.

### Important terms

| Term | Meaning |
| --- | --- |
| **Transaction** | A group of statements the database treats as one unit: begin, the statements, then commit or rollback. |
| **Commit** | End the transaction and keep every change. |
| **Rollback** | End the transaction and throw every change away. |
| **Spring bean** | An object that Spring creates and keeps for you. |
| **Service (`@Service`)** | A Spring bean that holds a piece of the app's work. The usual home of `@Transactional`. |
| **`@Transactional`** | "Begin a transaction before this method runs, commit it after the method returns." |
| **Transaction manager** | The Spring object that begins, commits and rolls back transactions. Here: `JpaTransactionManager`. |
| **EntityManager** | Hibernate's working object for one piece of database work. It holds the connection. In the log: `SessionImpl(N<open>)`. |
| **Thread** | One line of work the program runs, step by step. Here: `main`. |
| **Propagation, `REQUIRED`** | The rule for when a transaction is already running. `REQUIRED`: join it, or begin one if none is running. |
| **Isolation, `DEFAULT`** | How much one transaction sees of other transactions' uncommitted changes. `DEFAULT`: the database's own level, `read committed` on Postgres. |
| **Logger, DEBUG** | A logger is a named source of log lines. DEBUG is a level with more detail than INFO. |

### Where it is used

- In real apps, on service methods that do one whole job, like "book two tickets": check the
  seats, save the order, lower the free seat count. All those steps should succeed or fail
  together.
- In this task, in every remaining subtask:
  - S6, changing a name without `save()`;
  - S7, the same with no transaction;
  - S8, `readOnly`;
  - S9, the rollback tests;
  - S10 and S11, the self-invocation trap.

  The same transaction log is used in all of them.

### Edge cases

- **Query methods you declare yourself start no transaction.** `existsBySlug("sufi-night")`,
  called outside any transaction, printed only its SQL, without a single transaction line:

  ```text
  >>> CASE exists-no-tx
  Hibernate: 
      select
          ee1_0.id 
      from
          event ee1_0 
      where
          ee1_0.slug=? 
      fetch
          first ? rows only
  >>> END exists-no-tx -> true
  ```

  The inherited `count()` and `findById()` in the example do start one, because their
  transaction setting sits in `SimpleJpaRepository`. Methods you declare in `EventRepository`
  have no such setting.
- **Inside your transaction, such a method simply runs in it.** A `@Transactional` method that
  only called `existsBySlug` printed its own `Creating new transaction`, then the SQL, then one
  commit. There was no `Participating in existing transaction` line. `existsBySlug` has no
  transaction setting, so there was nothing to join; it just used the running transaction.
  The SQL is shortened here:

  ```text
  Creating new transaction with name [com.zenith.data.TxCheckService.existsInTx]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
  Opened new EntityManager [SessionImpl(1141720517<open>)] for JPA transaction
  Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
  Hibernate: 
      select ee1_0.id from event ee1_0 where ee1_0.slug=? fetch first ? rows only
  Initiating transaction commit
  Committing JPA transaction on EntityManager [SessionImpl(1141720517<open>)]
  Closing JPA EntityManager [SessionImpl(1141720517<open>)] after transaction
  ```

- **`jakarta.transaction.Transactional` also works here.** Your editor may offer this second
  annotation with the same name. A method marked with it printed
  `Creating new transaction with name [com.zenith.data.TxCheckService.jakartaAnnotated]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT`,
  then one `Participating in existing transaction` and one commit: the same as with Spring's
  annotation. Still, pick Spring's `org.springframework.transaction.annotation.Transactional`.
  It has settings the other one lacks, like `readOnly`.
- **The numbers change on every run.** `SessionImpl(1972687241<open>)` and
  `HibernateConnectionHandle@508de524` are labels for objects in memory. Compare them within
  one run, never across runs.

### Common mistakes — with the real errors

1. **Thinking a service method is one transaction anyway.** It isn't. Without `@Transactional`,
   each repository call is its own transaction. Part 1 of the output in the example shows two
   `Creating new transaction` lines and two commits.

2. **Making the service yourself with `new`.** Spring adds the begin and commit steps only to
   beans that Spring creates. In a test run, a runner did this:

   ```java
   String s = new EventService(events).describeInOneTransaction(SUFI_NIGHT);
   ```

   The method has `@Transactional`, but the log showed no `EventService` transaction at all,
   only the repository's own two:

   ```text
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   ...
   Committing JPA transaction on EntityManager [SessionImpl(834764756<open>)]
   ...
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   ...
   Committing JPA transaction on EntityManager [SessionImpl(1731883050<open>)]
   ```

   There was no error, so this is easy to miss. The fix: let Spring create the service, and
   take it in through a constructor, the way `TransactionDemo` does.

3. **Forgetting the import.** With the line
   `import org.springframework.transaction.annotation.Transactional;` removed, the build stopped:

   ```text
   [ERROR] .../EventService.java:[26,6] cannot find symbol
     symbol:   class Transactional
     location: class com.zenith.data.EventService
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `EventService.java:[26,6]` | Line 26, column 6: the `T` of `@Transactional`. It is line 26, not 27, because the removed import moved everything up one line. |
   | `cannot find symbol` | Java doesn't know this name. |
   | `symbol: class Transactional` | The unknown name is the annotation `Transactional`. |
   | `location: class com.zenith.data.EventService` | It was used inside `EventService`. |

4. **A typo in the logger name.** With `JpaTransactionManger` (missing an "a"), the app ran
   normally and printed both `>>>    returned:` lines, but **zero** transaction lines, and no
   warning. Spring accepts any logger name. If you see no transaction lines, check the
   spelling first.

### Link to the task

S6 says: "Inside a `@Transactional` service method, load an event, change its name, and
**do not call `save()`**. Commit. Reload it. The name changed."

- This lesson gives the first half: a service method with `@Transactional`, and a log that
  shows exactly where its transaction begins and where it commits.
- Still needed for S6: what Hibernate keeps track of inside the transaction (the persistence
  context), and how it writes the change at the commit (dirty checking).
- S7 to S11 use the same annotation and the same log.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventRepository` extends `JpaRepository<EventEntity, UUID>`. It has 36 lines and ends with
  `findAllBy`.
- Postgres runs in the Docker container `pg`, with the 7 seed events.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the service and the demo.** Create these two files in
`scratch-app/src/main/java/com/zenith/data/`, each with its 33 lines from the example:
`EventService.java` and `TransactionDemo.java`. Then run:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=tx -Dspring-boot.run.arguments=--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

You should see the output from the example:

- after `>>> 1. calling describeWithoutTransaction`: `Creating new transaction with name
  [...SimpleJpaRepository.count]`, and later one for `[...SimpleJpaRepository.findById]`. Both
  end with `readOnly`, and each is followed by its own `Committing JPA transaction`;
- after `>>> 2. calling describeInOneTransaction`: one
  `Creating new transaction with name [com.zenith.data.EventService.describeInOneTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT`,
  two `Participating in existing transaction` lines, and one `Committing JPA transaction` after
  both `Hibernate:` blocks;
- `>>>    returned: Sufi Night (one of 7 events)` after each part.

If it goes wrong:

- The build stops with `EventService.java:[26,6] cannot find symbol` and
  `symbol: class Transactional`: the line
  `import org.springframework.transaction.annotation.Transactional;` is missing.
- The app prints both `returned` lines but no transaction lines: the logger name in the
  command is misspelled. It must be exactly `org.springframework.orm.jpa.JpaTransactionManager`.

**Step 3 — break it: remove the annotation.** In `EventService.java`, delete line 27, the
`@Transactional` just above `describeInOneTransaction`. Keep the import: an unused import is not
an error. Run the same command. Part 2 now looks like part 1:

- `Creating new transaction` for `SimpleJpaRepository.count` and for
  `SimpleJpaRepository.findById`;
- no `EventService` line;
- no `Participating in existing transaction`.

The returned line is unchanged. Put `@Transactional` back.

**Step 4 — run it once more.** Use the command from step 2. You should get the same output as
in step 2.

**Done when:**

- you have seen part 1 make two transactions, one per repository call;
- you have seen part 2 make one transaction named after your method, with two
  `Participating in existing transaction` lines and one commit;
- removing `@Transactional` turned part 2 back into two transactions, and putting it back fixed
  it.

### Summary for revision

- A transaction: begin, the statements, then commit (keep) or rollback (throw away).
- `@Transactional` on a public method of a Spring bean: Spring begins a transaction before the
  method, and commits after it returns, before the caller continues.
- Inside it, repository calls join the one transaction: `Participating in existing transaction`.
- Without it, each inherited repository call is its own `readOnly` transaction, made by
  `SimpleJpaRepository`. Query methods you declare yourself get none.
- `PROPAGATION_REQUIRED` = join, or begin if none. `ISOLATION_DEFAULT` = the database's level,
  `read committed` on Postgres.
- `new EventService(...)` gets no transaction. Let Spring create the service and inject it.
- The log: `--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG`.

### Practice questions, with answers

**Question 1.** A temporary Spring bean, a service called `TxCheckService`, has an
`EventRepository events` field and these two methods. `MONSOON` and `INDIE` are the ids of
Monsoon Rock Night and Indie Fest.

```java
public void threeCallsNoTx() {
    events.count();
    events.findById(MONSOON);
    events.findById(INDIE);
}

@Transactional
public void threeCallsInTx() {
    events.count();
    events.findById(MONSOON);
    events.findById(INDIE);
}
```

A runner got this bean through its constructor. With the transaction log on, it called
`threeCallsNoTx()` and then `threeCallsInTx()`. For each method:

- (a) How many `Creating new transaction with name [...]` lines appear, and which names are in
  the brackets?
- (b) How many `Participating in existing transaction` lines appear?
- (c) How many `Committing JPA transaction ...` lines appear?
- (d) How many different `SessionImpl(...)` numbers appear?

**Answer.** Both methods were run for real.

| | `threeCallsNoTx()` | `threeCallsInTx()` |
| --- | --- | --- |
| (a) `Creating new transaction` | **3**: `SimpleJpaRepository.count`, `SimpleJpaRepository.findById`, `SimpleJpaRepository.findById`, each ending in `readOnly` | **1**: `com.zenith.data.TxCheckService.threeCallsInTx`, with `PROPAGATION_REQUIRED,ISOLATION_DEFAULT` |
| (b) `Participating in existing transaction` | **0** | **3**, one per repository call |
| (c) Commits | **3** | **1** |
| (d) Different `SessionImpl` numbers | **3** (`966443427`, `1770082598`, `458898362`) | **1** (`570616184`) |

Without the annotation, each of the three inherited methods makes its own transaction with its
own EntityManager. With the annotation, there is one transaction for the whole method, and all
three calls join it. The three SQL statements are the same in both cases.

**Question 2.** The same `TxCheckService` bean has this method:

```java
@Transactional
public void printsInside() {
    System.out.println(">>>    inside printsInside, before the query");
    events.count();
    System.out.println(">>>    inside printsInside, after the query");
}
```

A runner ran this, with the transaction log on:

```java
System.out.println(">>> CASE prints: before calling printsInside");
check.printsInside();
System.out.println(">>> END prints: after printsInside returned");
```

These 8 lines appeared, shown here in shuffled order. Put them in the order they really
appeared. (The lines `Opened new EntityManager`, `Exposing JPA transaction as JDBC`,
`Found thread-bound EntityManager`, `Initiating transaction commit` and
`Closing JPA EntityManager` also appeared, but are left out here.)

- A. `Committing JPA transaction on EntityManager [SessionImpl(...)]`
- B. `>>> CASE prints: before calling printsInside`
- C. `Participating in existing transaction`
- D. `>>>    inside printsInside, after the query`
- E. `Creating new transaction with name [com.zenith.data.TxCheckService.printsInside]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT`
- F. `>>> END prints: after printsInside returned`
- G. the `Hibernate:` block `select count(*) from event ee1_0`
- H. `>>>    inside printsInside, before the query`

**Answer.** **B, E, H, C, G, D, A, F.** The real run printed:

```text
>>> CASE prints: before calling printsInside
Creating new transaction with name [com.zenith.data.TxCheckService.printsInside]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1263831315<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@31048b3d]
>>>    inside printsInside, before the query
Found thread-bound EntityManager [SessionImpl(1263831315<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        count(*) 
    from
        event ee1_0
>>>    inside printsInside, after the query
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1263831315<open>)]
Closing JPA EntityManager [SessionImpl(1263831315<open>)] after transaction
>>> END prints: after printsInside returned
```

- The runner's first print (B) comes before anything, because the call hasn't happened yet.
- The transaction begins (E) **before the method's first line** (H).
- `count()` first joins the running transaction (C), and then sends its SQL (G).
- The method's last line (D) runs while the transaction is still open.
- The commit (A) comes **after the method's last line**, but **before the runner continues**
  (F). The caller only gets control back once the transaction is over.

---

## Lesson 15 — The persistence context and managed entities (continues subtask S6, answers CP2)

### Definition

Two words you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit.
  With `@Transactional` on a method, Spring begins a transaction before the method runs and
  commits it after the method returns.
- An **EntityManager** is Hibernate's working object for one piece of database work. Spring opens
  one when a transaction begins and closes it when the transaction ends. In the transaction log
  it shows up as `SessionImpl(N<open>)`, where `N` is a label for that one object.

The **persistence context** is the EntityManager's memory of the entities it has loaded. For
every row it has loaded, it keeps exactly one Java object, filed under the entity class and the
id. It is also called the **first-level cache**. (A **cache** is a store of things already
fetched, so they don't have to be fetched again.)

In this app, one transaction has one EntityManager, so one transaction has one persistence
context. It starts empty when the transaction begins, and it is thrown away when the
transaction ends.

- An entity object is **managed** when it sits in a persistence context that is still open.
  Hibernate keeps track of it.
- An entity object is **detached** when its persistence context is gone, because its transaction
  is over. It is still a normal Java object with its values, but Hibernate no longer keeps track
  of it.

`entityManager.contains(someEntity)` asks Hibernate directly. It returns `true` if the object is
managed in the current persistence context, and `false` if not.

### Why it is needed

- **It saves work.** Inside one transaction, asking for the same row by id a second time sends no
  SQL. Hibernate hands back the object it already has.
- **One row, one object.** Inside one transaction, every piece of code that loads Sufi Night gets
  the very same Java object. There can't be two different versions of the same row in one
  transaction.
- **Subtask S6 depends on it.** S6 changes a name without calling `save()`, and the database
  still changes. That only works because the entity is managed: Hibernate still holds it at
  commit time.
- **Checkpoint CP2** asks exactly this: "What is the persistence context, and what does
  'managed' mean?"

### Key points

- The persistence context is the EntityManager's memory of loaded entities: one Java object per
  row, filed under class and id.
- One transaction = one EntityManager = one persistence context. It starts empty and is thrown
  away at the end of the transaction.
- Managed = in an open persistence context, tracked by Hibernate. Detached = its persistence
  context is gone.
- Inside one transaction, `findById` for an id already loaded sends no SQL and returns the same
  object.
- Without a transaction, every `findById` has its own small transaction, so its own empty
  persistence context: a new `SELECT` and a new object each time.
- Queries (`findByVenueId`, `@Query`, ...) always send SQL. For rows already in memory, they
  still hand back the objects already held.
- Only rows that were found are remembered. A missing id is looked up again every time.
- `entityManager.contains(e)` tells you whether `e` is managed right now.

### How it works, step by step

When you call `events.findById(id)`:

1. Hibernate first looks in the persistence context: "Do I already hold an `EventEntity` with
   this id?"
2. **If yes**, it returns that same object. No SQL is sent.
3. **If no**, it sends the `SELECT`, builds a new `EventEntity` object from the row, puts it into
   the persistence context, and returns it. From now on, that object is managed.
4. When the transaction ends, Spring closes the EntityManager. The persistence context is thrown
   away, and every object in it becomes detached. Your variables still point to the objects, but
   Hibernate has forgotten them.

**Without `@Transactional`**, every `findById` call runs in its own small transaction (Spring
Data's own transaction for that one call). So each call gets a new, empty persistence context,
sends its own `SELECT`, and builds its own object. The object is detached as soon as the call
returns.

**About `==`.** In Java, `a == b` on two objects is `true` only if both variables point to the
very same object in memory. It does not compare the values inside. Two different objects holding
the same row give `false`.

**About getting the EntityManager.** You get it the same way as the repository: as a
constructor parameter. Its type is `jakarta.persistence.EntityManager`. What Spring passes in is
a stand-in. Each time you call it, it passes the call on to the EntityManager of the transaction
that is running right now. With no transaction running, there is no open persistence context
holding your object, so `contains` answers `false`.

### Example

`scratch-app/src/main/java/com/zenith/data/EventService.java` now receives the EntityManager and
has two new methods. The new parts are line 3 (the import), lines 14 to 19 (the field and the
constructor), and lines 37 to 51 (the two methods):

```java
package com.zenith.data;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;
    private final EntityManager entityManager;

    public EventService(EventRepository events, EntityManager entityManager) {
        this.events = events;
        this.entityManager = entityManager;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 15: the same event loaded twice, with no transaction around the two loads.
    public String loadTwiceWithoutTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 15: the same two loads inside one transaction. The second one is answered from the
    // persistence context, without a SELECT.
    @Transactional
    public String loadTwiceInOneTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }
}
```

`scratch-app/src/main/java/com/zenith/data/PersistenceContextDemo.java` is a new runner with the
profile `pc`. It calls both new methods for Sufi Night:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 15: load one event twice - without and with a transaction - and count the SELECTs.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=pc
@Component
@Profile("pc")
class PersistenceContextDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    PersistenceContextDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. loadTwiceWithoutTransaction");
        System.out.println(">>>    " + service.loadTwiceWithoutTransaction(SUFI_NIGHT));

        System.out.println(">>> 2. loadTwiceInOneTransaction");
        System.out.println(">>>    " + service.loadTwiceInOneTransaction(SUFI_NIGHT));
    }
}
```

The command, in `scratch-app`, with the transaction log on:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=pc -Dspring-boot.run.arguments=--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

The real output after the `Started DataApplication` line. Each transaction line really starts
with the time, the level, a process number, the thread and the logger name
`o.s.orm.jpa.JpaTransactionManager`; that front part is cut off here so the lines fit:

```text
>>> 1. loadTwiceWithoutTransaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1165547092<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@577b2c5f]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1165547092<open>)]
Closing JPA EntityManager [SessionImpl(1165547092<open>)] after transaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(563876034<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@5e352e3a]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(563876034<open>)]
Closing JPA EntityManager [SessionImpl(563876034<open>)] after transaction
>>>    same object: false, managed: false
>>> 2. loadTwiceInOneTransaction
Creating new transaction with name [com.zenith.data.EventService.loadTwiceInOneTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(742257784<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@7f88c53]
Found thread-bound EntityManager [SessionImpl(742257784<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
Found thread-bound EntityManager [SessionImpl(742257784<open>)] for JPA transaction
Participating in existing transaction
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(742257784<open>)]
Closing JPA EntityManager [SessionImpl(742257784<open>)] after transaction
>>>    same object: true, managed: true
```

The numbers after `SessionImpl(` and after `@` are different on every run.

### Walkthrough

| | Part 1: `loadTwiceWithoutTransaction` | Part 2: `loadTwiceInOneTransaction` |
| --- | --- | --- |
| Transactions | 2, one per `findById` | 1, named after the method |
| EntityManagers, so persistence contexts | 2 (`1165547092`, `563876034`) | 1 (`742257784`) |
| `SELECT`s | 2 | **1** |
| `first == second` | `false`: two objects for one row | `true`: one object |
| `entityManager.contains(first)` | `false`: detached | `true`: managed |

**Part 1, step by step:**

1. The first `findById` gets its own transaction and a new EntityManager, `1165547092`, with an
   empty persistence context. So it sends a `SELECT` and builds the object `first`. Its
   transaction commits and the EntityManager closes, and `first` is detached.
2. The second `findById` does the same with a new EntityManager, `563876034`: another `SELECT`,
   and another object, `second`.
3. `first == second` is `false`: two objects for the same row. `contains(first)` is `false`: no
   transaction is running, so no open persistence context holds `first`.

**Part 2, step by step:**

1. Spring begins the transaction for `loadTwiceInOneTransaction`. EntityManager `742257784`
   opens, with an empty persistence context.
2. The first `findById` joins (`Participating in existing transaction`). Sufi Night is not in
   memory, so it sends the `SELECT`, and the object `first` goes into the persistence context.
3. The second `findById` joins too. Hibernate finds Sufi Night in the persistence context and
   returns `first` again. In the log, the second `Participating in existing transaction` has
   **no** `Hibernate:` block after it; the next line is already the commit.
4. `first == second` is `true`. `contains(first)` is `true`, because the transaction is still
   running when this line runs.
5. Commit and close. The persistence context is thrown away, and `first` is now detached.

### Important terms

| Term | Meaning |
| --- | --- |
| **Persistence context** | The EntityManager's memory of the entities it has loaded: one Java object per row, filed under class and id. One per transaction here. |
| **First-level cache** | Another name for the persistence context. |
| **Cache** | A store of things already fetched, so they don't have to be fetched again. |
| **Managed entity** | An entity object in a persistence context that is still open. Hibernate keeps track of it. |
| **Detached entity** | An entity object whose persistence context is gone (its transaction is over). A plain Java object now. |
| **`entityManager.contains(e)`** | `true` if `e` is managed in the current persistence context. |
| **`==` on objects** | `true` only if both sides are the very same object in memory. |
| **Injected EntityManager** | A stand-in that passes each call on to the EntityManager of the running transaction. |

### Where it is used

- Any service method that loads the same entity more than once, for example in two helper
  methods: inside one transaction, the second load costs nothing and gives the same object.
- Subtask S6: Hibernate writes your change without `save()` only for managed entities.
- Subtask S7: changing an entity after its transaction is over writes nothing, because the
  entity is detached by then.
- Checkpoint CP2: the definition at the start of this lesson is the answer.

### Edge cases

- **Queries always go to the database, but hand back the objects already held.** Inside one
  transaction, a temporary method called `findById` for Sufi Night, then `findByVenueId` for
  Riverside Arena (which has 4 events, Sufi Night among them). Both sent SQL:

  ```text
  Creating new transaction with name [com.zenith.data.PcCheckService.loadThenQuery]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
  Opened new EntityManager [SessionImpl(2117395558<open>)] for JPA transaction
  Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
  Found thread-bound EntityManager [SessionImpl(2117395558<open>)] for JPA transaction
  Participating in existing transaction
  Hibernate: 
      select
          ee1_0.id,
          ee1_0.name,
          ee1_0.slug,
          ee1_0.starts_at,
          ee1_0.venue_id 
      from
          event ee1_0 
      where
          ee1_0.id=?
  ... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
  Hibernate: 
      select
          ee1_0.id,
          ee1_0.name,
          ee1_0.slug,
          ee1_0.starts_at,
          ee1_0.venue_id 
      from
          event ee1_0 
      where
          ee1_0.venue_id=?
  ... binding parameter (1:UUID) <- [11111111-1111-1111-1111-111111111111]
  Initiating transaction commit
  Committing JPA transaction on EntityManager [SessionImpl(2117395558<open>)]
  Closing JPA EntityManager [SessionImpl(2117395558<open>)] after transaction
  >>> END load-then-query -> list size 4, sufi==sufiFromList true
  ```

  The query must ask the database "which rows match?"; the persistence context can't answer
  that. But the Sufi Night inside the list of 4 was the very same object as the one from
  `findById`: for a row it already holds, Hibernate hands back the object it has. (The query
  shows no `Participating` line, because `findByVenueId` is a method you declared yourself and
  has no transaction setting of its own; it simply runs inside the transaction.)
- **Only rows that were found are remembered.** Inside one transaction, `findById` for the id
  `aaaaaaaa-0000-0000-0000-000000000099`, which doesn't exist, was called twice. Both calls sent
  a `SELECT` (each with binding `[aaaaaaaa-0000-0000-0000-000000000099]`), and both returned an
  empty `Optional`. Hibernate keeps no note of "not found".
- **The injected EntityManager works without a transaction, but holds nothing.** In part 1,
  `entityManager.contains(first)` ran with no transaction and simply answered `false`. It did
  not throw an error.

### Common mistakes — with the real errors

1. **Thinking every `findById` reads the database.** Inside one transaction, the second call
   for the same id sends no SQL (part 2 in the example). That also means it can't see anything
   that changed in the database in between.

2. **Comparing entities with `==` across transactions.** In part 1, `first == second` was
   `false`, though both hold Sufi Night. To ask "is it the same row?", compare the ids:
   `first.getId().equals(second.getId())`.

3. **Expecting an entity to stay managed after its transaction.** In part 1,
   `contains(first)` was `false`: `first` came from a transaction that had already ended.

4. **A missing or wrong import.** Without `import jakarta.persistence.EntityManager;`, the build
   stopped:

   ```text
   [ERROR] .../EventService.java:[13,19] cannot find symbol
     symbol:   class EntityManager
   [ERROR] .../EventService.java:[15,49] cannot find symbol
     symbol:   class EntityManager
     location: class com.zenith.data.EventService
   ```

   With the old `import javax.persistence.EntityManager;` (from older tutorials), it stopped
   like this:

   ```text
   [ERROR] .../EventService.java:[3,25] package javax.persistence does not exist
   [ERROR] .../EventService.java:[14,19] cannot find symbol
     symbol:   class EntityManager
   [ERROR] .../EventService.java:[16,49] cannot find symbol
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `[13,19]` and `[15,49]` (no import) | The field line and the constructor parameter. Without the import, everything moves up one line, so they are lines 13 and 15 instead of 14 and 16. |
   | `cannot find symbol`, `symbol: class EntityManager` | Java doesn't know the name `EntityManager`, because nothing imported it. |
   | `package javax.persistence does not exist` | The old package name. Spring Boot 3 uses `jakarta.persistence`; `javax.persistence` is not in the project at all. |
   | `[14,19]` and `[16,49]` (old import) | The same two uses, failing because of the bad import. Fixing line 3 fixes all three. |

### Link to the task

S6 says: "Inside a `@Transactional` service method, load an event, change its name, and
**do not call `save()`**. Commit. Reload it. The name changed. Explain why: the entity was
managed, Hibernate compared it at commit, and wrote the `UPDATE` itself."

- This lesson explains "the entity was managed": you load the event inside the transaction, so
  it sits in the persistence context, and Hibernate still holds it at the commit.
- Still needed for S6: "Hibernate compared it at commit, and wrote the `UPDATE` itself" (dirty
  checking).
- CP2 ("What is the persistence context, and what does 'managed' mean?") is answered by the
  definition at the start of this lesson.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventService.java` has 33 lines: `describeWithoutTransaction`, `describeInOneTransaction`,
  and a constructor that takes only `EventRepository`.
- `TransactionDemo.java` exists.
- `EventRepository` has 36 lines.
- Postgres runs in the Docker container `pg`, with the 7 seed events.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — update `EventService.java`.** Make it match the 52 lines in the example:

- add `import jakarta.persistence.EntityManager;` as line 3;
- add the field `private final EntityManager entityManager;` under the `events` field;
- give the constructor a second parameter, `EntityManager entityManager`, and add the line
  `this.entityManager = entityManager;`;
- add the two Lesson 15 methods, with their comments, before the last `}`.

**Step 3 — add the demo and run it.** Create
`scratch-app/src/main/java/com/zenith/data/PersistenceContextDemo.java` with the 31 lines from
the example. Then run:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=pc -Dspring-boot.run.arguments=--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

You should see the output from the example:

- part 1: two `Creating new transaction with name [...SimpleJpaRepository.findById]` lines, two
  `Hibernate:` blocks, then `>>>    same object: false, managed: false`;
- part 2: one `Creating new transaction with name [com.zenith.data.EventService.loadTwiceInOneTransaction]`,
  two `Participating in existing transaction` lines, but only **one** `Hibernate:` block, then
  `>>>    same object: true, managed: true`.

If the build fails:

- `EventService.java:[13,19] cannot find symbol` with `symbol: class EntityManager`: the import
  line is missing.
- `EventService.java:[3,25] package javax.persistence does not exist`: you typed `javax`
  instead of `jakarta`.

**Step 4 — break it: remove the annotation.** In `EventService.java`, delete line 46, the
`@Transactional` just above `loadTwiceInOneTransaction`. Run the same command. Part 2 now looks
like part 1:

- two `Creating new transaction ... SimpleJpaRepository.findById` lines;
- two `Hibernate:` blocks;
- `>>>    same object: false, managed: false`.

Put `@Transactional` back.

**Step 5 — run it once more, and check the older demo.** Run the command from step 3. You
should get the same output as in step 3. Then run the same command with
`-Dspring-boot.run.profiles=tx` instead of `pc`. It should still print
`>>>    returned: Sufi Night (one of 7 events)` twice: the new constructor didn't break it.

**Done when:**

- part 1 printed two `SELECT`s and `same object: false, managed: false`;
- part 2 printed one `SELECT` and `same object: true, managed: true`;
- removing `@Transactional` turned part 2 into two `SELECT`s and `false, false`, and putting it
  back fixed it;
- the `tx` demo still works.

### Summary for revision

- The persistence context is the EntityManager's memory of loaded entities: one object per row.
  Also called the first-level cache.
- Here, one transaction = one EntityManager = one persistence context. It starts empty and is
  thrown away when the transaction ends.
- Managed = in an open persistence context, tracked by Hibernate. Detached = its persistence
  context is gone.
- Inside one transaction, a second `findById` for the same id sends no SQL and returns the same
  object.
- Without a transaction, each `findById` gets its own transaction and persistence context: a new
  `SELECT` and a new object every time.
- Queries always send SQL, but give back objects already held for rows already loaded.
- `entityManager.contains(e)` answers "is `e` managed right now?".

### Practice questions, with answers

**Question 1.** A temporary Spring bean, a service called `PcCheckService`, has an
`EventRepository events` field and this method, called from a runner. `SUFI` is Sufi Night's id
(`aaaaaaaa-0000-0000-0000-000000000005`). `MONSOON` is Monsoon Rock Night's
(`aaaaaaaa-0000-0000-0000-000000000001`).

```java
@Transactional
public String threeLoads() {
    EventEntity a = events.findById(SUFI).orElseThrow();
    EventEntity b = events.findById(MONSOON).orElseThrow();
    EventEntity c = events.findById(SUFI).orElseThrow();
    return "a==b " + (a == b) + ", a==c " + (a == c);
}
```

- (a) How many `SELECT`s (`Hibernate:` blocks) does it send, and for which ids?
- (b) What does it return? Say why for each of the two values.

**Answer.** The method was run for real, with the transaction log and the binding log on. The
SQL is shortened here:

```text
Creating new transaction with name [com.zenith.data.PcCheckService.threeLoads]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1169387523<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
Found thread-bound EntityManager [SessionImpl(1169387523<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ... from event ee1_0 where ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Found thread-bound EntityManager [SessionImpl(1169387523<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ... from event ee1_0 where ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000001]
Found thread-bound EntityManager [SessionImpl(1169387523<open>)] for JPA transaction
Participating in existing transaction
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1169387523<open>)]
Closing JPA EntityManager [SessionImpl(1169387523<open>)] after transaction
>>> END three-loads -> a==b false, a==c true
```

- (a) **2 `SELECT`s**: one for `...0005` (Sufi Night), then one for `...0001` (Monsoon Rock
  Night). The third load, Sufi Night again, sent none: after its
  `Participating in existing transaction`, the next line is the commit.
- (b) **`a==b false, a==c true`.**
  - `a==b` is `false` because `a` and `b` are different rows, so they are different objects.
  - `a==c` is `true` because `c` is the same row as `a`, in the same transaction. The
    persistence context already held Sufi Night, so it handed back the object `a` itself.

**Question 2.** The same `PcCheckService` bean also has an `EntityManager entityManager` field
(taken in through its constructor) and these two methods:

```java
@Transactional
public EventEntity loadAndReturn() {
    return events.findById(SUFI).orElseThrow();
}

@Transactional
public String inALaterTransaction(EventEntity fromEarlier) {
    boolean managed = entityManager.contains(fromEarlier);
    EventEntity reloaded = events.findById(SUFI).orElseThrow();
    return "earlier one managed " + managed + ", reloaded==earlier " + (reloaded == fromEarlier)
            + ", reloaded managed " + entityManager.contains(reloaded);
}
```

The runner does:

```java
EventEntity earlier = check.loadAndReturn();
System.out.println(check.inALaterTransaction(earlier));
```

- (a) How many `SELECT`s does `inALaterTransaction` send?
- (b) What does it print? Give the three `true`/`false` values, and say why for each.

**Answer.** This was run for real, with the transaction log and the binding log on. The real
check also printed a marker line between the two calls. The SQL is shortened here:

```text
Creating new transaction with name [com.zenith.data.PcCheckService.loadAndReturn]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(295633618<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
Found thread-bound EntityManager [SessionImpl(295633618<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ... from event ee1_0 where ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(295633618<open>)]
Closing JPA EntityManager [SessionImpl(295633618<open>)] after transaction
>>>    between the two transactions
Creating new transaction with name [com.zenith.data.PcCheckService.inALaterTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(2136672550<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
Found thread-bound EntityManager [SessionImpl(2136672550<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ... from event ee1_0 where ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(2136672550<open>)]
Closing JPA EntityManager [SessionImpl(2136672550<open>)] after transaction
>>> END later-transaction -> earlier one managed false, reloaded==earlier false, reloaded managed true
```

- (a) **1 `SELECT`.** The later transaction has its own new EntityManager (`2136672550`), and its
  persistence context starts empty. So the reload of Sufi Night has to go to the database, even
  though Sufi Night was loaded a moment earlier, in the other transaction.
- (b) **`earlier one managed false, reloaded==earlier false, reloaded managed true`.**
  - `earlier one managed false`: `earlier` came from `loadAndReturn`'s transaction. Its
    EntityManager (`295633618`) was closed before `inALaterTransaction` began. So `earlier` is
    detached, and the new persistence context doesn't hold it.
  - `reloaded==earlier false`: the new persistence context was empty, so Hibernate built a
    brand-new object from the row. That gives two objects for the same row.
  - `reloaded managed true`: `reloaded` was loaded in the running transaction, so it sits in the
    current persistence context.

---

## Lesson 16 — Dirty checking (finishes subtask S6, answers CP3)

### Definition

Three ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit.
  With `@Transactional` on a method, Spring begins a transaction before the method runs and
  commits it after the method returns.
- The **persistence context** is Hibernate's memory of the entities loaded in the current
  transaction: one Java object per loaded row. It is thrown away when the transaction ends.
- An entity is **managed** while it sits in a persistence context that is still open.

New words:

- **Snapshot.** When Hibernate loads a row, it keeps two things in the persistence context: the
  entity object you get, and a private copy of the values exactly as they were loaded. That
  private copy is the snapshot.
- **Dirty.** An entity is dirty when its current values differ from its snapshot.
- **Dirty checking.** Just before the commit, Hibernate compares every managed entity with its
  snapshot. For each dirty entity it writes an `UPDATE` itself. You never call `save()`.
- **Flush.** Sending the changes Hibernate is holding to the database as SQL. It normally happens
  at the commit.
- **Setter.** A method that changes one field, here `setName`.

### Why it is needed

- It is exactly subtask S6, and checkpoint CP3 asks you to explain it: "You changed a field and
  never called `save()`, yet the database changed. Explain the mechanism."
- It means less code. An update is: load, change, return. There is no `save()` call after every
  change.
- It surprises people in both directions. Any change to a managed entity is written, even one
  meant only for printing. And a change to an entity that is **not** managed is never written.
  Knowing the rule protects you from both.

### Key points

- Loading an entity stores a snapshot of its values next to it.
- Changing a managed entity (`setName`) changes only the Java object. No SQL is sent at that
  moment.
- At the flush, normally at the commit, Hibernate compares each managed entity with its
  snapshot. Each dirty one gets an `UPDATE`.
- In the log, the `UPDATE` comes after `Committing JPA transaction on EntityManager [...]` and
  before `Closing JPA EntityManager`.
- By default, the `UPDATE` writes every mapped column, not only the changed one.
- `save()` is not needed for an entity loaded in the same transaction. Calling it adds no SQL.
- A query can make the flush happen early: Hibernate sends the pending `UPDATE` right before the
  query, so the query sees the change.

### How it works, step by step

A `@Transactional` method loads an event and renames it:

1. **Begin.** The transaction begins, and the persistence context is empty.
2. **Load.** `findById` sends the `SELECT`. Hibernate builds the object and also stores the
   snapshot: name = "Sufi Night".
3. **Change.** `event.setName("Sufi Night Live")` changes only the Java object. No SQL is sent.
   The snapshot still says "Sufi Night".
4. **Return.** The method returns. Spring starts the commit.
5. **Flush.** Before the database commits, Hibernate compares each managed entity with its
   snapshot. Sufi Night's name differs, so it is dirty, and Hibernate sends
   `update event set name=?, slug=?, starts_at=?, venue_id=? where id=?`.
6. **Commit.** The database commits. The change is now permanent.
7. **Close.** The EntityManager closes. The persistence context and its snapshots are thrown
   away.

### Example

`scratch-app/src/main/java/com/zenith/data/EventEntity.java` gets a setter, at lines 31 and 32:

```java
package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")   // without this, Hibernate guesses the table name "event_entity"
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getSlug() { return slug; }

    // Lesson 16: the one field a service may change.
    public void setName(String name) { this.name = name; }
}
```

`scratch-app/src/main/java/com/zenith/data/EventService.java` gets two new methods, at lines 53
to 64. `renameWithoutSave` loads, changes and returns, with no `save()` anywhere. `currentName`
reads the name back from the database:

```java
package com.zenith.data;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;
    private final EntityManager entityManager;

    public EventService(EventRepository events, EntityManager entityManager) {
        this.events = events;
        this.entityManager = entityManager;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 15: the same event loaded twice, with no transaction around the two loads.
    public String loadTwiceWithoutTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 15: the same two loads inside one transaction. The second one is answered from the
    // persistence context, without a SELECT.
    @Transactional
    public String loadTwiceInOneTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 16: load the event, change its name, and return. There is no save() call anywhere.
    @Transactional
    public void renameWithoutSave(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 16: read the name from the database. findById runs in its own short transaction.
    public String currentName(UUID eventId) {
        return events.findById(eventId).orElseThrow().getName();
    }
}
```

`scratch-app/src/main/java/com/zenith/data/DirtyCheckingDemo.java` is a new runner with the
profile `dirty`. It reads the name, renames the event, reads the name again, and then puts the
old name back:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 16: rename an event without calling save(), then read it back to see the database changed.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=dirty
@Component
@Profile("dirty")
class DirtyCheckingDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    DirtyCheckingDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. reading the name from the database");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 2. calling renameWithoutSave");
        service.renameWithoutSave(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameWithoutSave returned");

        System.out.println(">>> 3. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 4. putting the old name back");
        service.renameWithoutSave(SUFI_NIGHT, "Sufi Night");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
```

The command turns on two logs: the transaction log, and the **binding log**, which shows the
value put into each `?`. The two settings must be inside quotes, separated by a space:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=dirty -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

The real output after the `Started DataApplication` line. The front part of each log line (the
time, level, process number, thread and logger name) is cut off. Binding lines start with `...`:

```text
>>> 1. reading the name from the database
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(978128034<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2db07651]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(978128034<open>)]
Closing JPA EntityManager [SessionImpl(978128034<open>)] after transaction
>>>    Sufi Night
>>> 2. calling renameWithoutSave
Creating new transaction with name [com.zenith.data.EventService.renameWithoutSave]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(944780041<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@565fa7d2]
Found thread-bound EntityManager [SessionImpl(944780041<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    name changed in Java to: Sufi Night Live
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(944780041<open>)]
Hibernate: 
    update
        event 
    set
        name=?,
        slug=?,
        starts_at=?,
        venue_id=? 
    where
        id=?
... binding parameter (1:VARCHAR) <- [Sufi Night Live]
... binding parameter (2:VARCHAR) <- [sufi-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Closing JPA EntityManager [SessionImpl(944780041<open>)] after transaction
>>>    renameWithoutSave returned
>>> 3. reading the name from the database again
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(882402449<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2582e213]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(882402449<open>)]
Closing JPA EntityManager [SessionImpl(882402449<open>)] after transaction
>>>    Sufi Night Live
>>> 4. putting the old name back
Creating new transaction with name [com.zenith.data.EventService.renameWithoutSave]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(24919603<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2eb27091]
Found thread-bound EntityManager [SessionImpl(24919603<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    name changed in Java to: Sufi Night
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(24919603<open>)]
Hibernate: 
    update
        event 
    set
        name=?,
        slug=?,
        starts_at=?,
        venue_id=? 
    where
        id=?
... binding parameter (1:VARCHAR) <- [Sufi Night]
... binding parameter (2:VARCHAR) <- [sufi-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Closing JPA EntityManager [SessionImpl(24919603<open>)] after transaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1384193473<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@4f9191c1]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1384193473<open>)]
Closing JPA EntityManager [SessionImpl(1384193473<open>)] after transaction
>>>    Sufi Night
```

### Walkthrough

**Step 2 is subtask S6.** Read its lines in order:

1. **The `SELECT`** with Sufi Night's id. This is when Hibernate stores the snapshot
   (name = "Sufi Night").
2. **`>>>    name changed in Java to: Sufi Night Live`.** `setName` has run, and **no SQL** came
   between the `SELECT` and this line.
3. **`Initiating transaction commit`, then `Committing JPA transaction ...`.** The method has
   returned, and the commit starts.
4. **The `UPDATE`.** This is the flush. Hibernate compared Sufi Night with its snapshot, saw a
   different name, and wrote the row itself.
5. **`Closing JPA EntityManager`**, and only then `>>>    renameWithoutSave returned`.

What went into the `UPDATE`'s five `?`s:

| `?` | Column | Value | Changed? |
| --- | --- | --- | --- |
| 1 | `name` | `Sufi Night Live` | **yes** |
| 2 | `slug` | `sufi-night` | no |
| 3 | `starts_at` | `2027-07-10T14:00Z` | no |
| 4 | `venue_id` | `11111111-...` (Riverside Arena) | no |
| 5 | `id` in `where id=?` | `aaaaaaaa-...-000000000005` | (picks the row) |

**Step 3 proves the database changed.** `currentName` runs in a new transaction with a new, empty
persistence context, so it must send a fresh `SELECT`. The database answered "Sufi Night Live".

**Step 4** puts "Sufi Night" back with the same mechanism. A psql check afterwards showed
`Sufi Night`.

### Important terms

| Term | Meaning |
| --- | --- |
| **Snapshot** | Hibernate's private copy of an entity's values as they were loaded, kept in the persistence context. |
| **Dirty** | An entity whose current values differ from its snapshot. |
| **Dirty checking** | Comparing every managed entity with its snapshot at the flush, and writing an `UPDATE` for each dirty one. |
| **Flush** | Sending the changes Hibernate holds to the database as SQL. Normally at the commit; earlier if a query needs it. |
| **Setter** | A method that changes one field, like `setName`. |
| **Binding log** | `--logging.level.org.hibernate.orm.jdbc.bind=trace`: shows the value put into each `?`. |

### Where it is used

- Updates in real Spring apps usually look like `renameWithoutSave`: load the entity in a
  `@Transactional` method, change it, return. "Rename an event" and "change an order's status"
  are typical. No `save()` is needed for an entity loaded in the same transaction.
- Subtask S6 and checkpoint CP3.
- Later performance work: at every flush, Hibernate compares **every** managed entity with its
  snapshot. With thousands of loaded entities, that takes time. A read-only transaction skips it.

### Edge cases

- **The `UPDATE` writes every mapped column.** The binding log showed the unchanged `slug`,
  `starts_at` and `venue_id` values going in, together with the new name. That is Hibernate's
  default.
- **A query can make the flush happen early.** A temporary method changed the name to
  "Sufi Night v5", then ran `findByNameContainingIgnoreCase("v5")`:

  ```text
  Creating new transaction with name [com.zenith.data.DirtyCheckService.changeThenQuery]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
  Opened new EntityManager [SessionImpl(1993662895<open>)] for JPA transaction
  Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
  Found thread-bound EntityManager [SessionImpl(1993662895<open>)] for JPA transaction
  Participating in existing transaction
  Hibernate: 
      select
          ee1_0.id,
          ee1_0.name,
          ee1_0.slug,
          ee1_0.starts_at,
          ee1_0.venue_id 
      from
          event ee1_0 
      where
          ee1_0.id=?
  ... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
  >>>    name changed, now running a query
  Hibernate: 
      update
          event 
      set
          name=?,
          slug=?,
          starts_at=?,
          venue_id=? 
      where
          id=?
  ... binding parameter (1:VARCHAR) <- [Sufi Night v5]
  ... binding parameter (2:VARCHAR) <- [sufi-night]
  ... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
  ... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
  ... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
  Hibernate: 
      select
          ee1_0.id,
          ee1_0.name,
          ee1_0.slug,
          ee1_0.starts_at,
          ee1_0.venue_id 
      from
          event ee1_0 
      where
          upper(ee1_0.name) like upper(?) escape '\'
  ... binding parameter (1:VARCHAR) <- [%v5%]
  >>>    query returned 1 event(s)
  Initiating transaction commit
  Committing JPA transaction on EntityManager [SessionImpl(1993662895<open>)]
  Closing JPA EntityManager [SessionImpl(1993662895<open>)] after transaction
  ```

  The `UPDATE` came **before** the query's `SELECT`, not at the commit. The query asks the
  database for names containing "v5", and the new name existed only in Java. So Hibernate
  flushed first, and the query found the renamed event (1 result). The `UPDATE` was still part
  of the open transaction; it became permanent only at the commit. At the commit there was
  nothing left to flush, so no second `UPDATE` came.
- **Calling `save()` on a managed entity adds nothing.** With `events.save(e);` after
  `e.setName("Sufi Night v4")`, the only new line was one more
  `Participating in existing transaction`. `save()` sent no SQL, and returned the very same
  object (`returned == e` printed `true`). The single `UPDATE` still came after
  `Committing JPA transaction`.

### Common mistakes — with the real errors

1. **Thinking `save()` is what writes the change.** It isn't. Without any `save()`, the example
   wrote the `UPDATE` at the commit. With `save()` added, the output was the same apart from one
   more `Participating in existing transaction` line.

2. **Expecting the `UPDATE` right at `setName`.** No SQL comes after `setName`. The `UPDATE`
   comes at the flush, normally at the commit. Looking right after `setName`, you won't find it.

3. **Changing a managed entity "just for display".** Any change to a managed entity is written
   at the commit; Hibernate can't tell what you meant. In the example, the only thing that caused
   the `UPDATE` was `setName`. For a value that is only for printing, use a separate variable and
   leave the entity alone.

4. **No setter.** Without `setName` in `EventEntity`, the build stopped:

   ```text
   [ERROR] .../EventService.java:[57,14] cannot find symbol
     symbol:   method setName(java.lang.String)
     location: variable event of type com.zenith.data.EventEntity
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `EventService.java:[57,14]` | Line 57, column 14: the `setName` in `event.setName(newName)`. |
   | `symbol: method setName(java.lang.String)` | There is no method `setName` that takes a `String`. |
   | `location: variable event of type com.zenith.data.EventEntity` | Java looked in `EventEntity`, the type of `event`, and didn't find it. |

5. **No quotes around the two log settings.** Without the quotes, Maven stopped at once, and the
   app never started:

   ```text
   Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace
   ```

   The shell splits the line at the space. Maven then reads the second setting as one of its own
   options, which it doesn't know. The quotes keep both settings together as one value for
   `-Dspring-boot.run.arguments`.

### Link to the task

S6 says: "Inside a `@Transactional` service method, load an event, change its name, and
**do not call `save()`**. Commit. Reload it. The name changed. Explain why: the entity was
managed, Hibernate compared it at commit, and wrote the `UPDATE` itself."

- Load, change, no `save()`, commit: step 2 of the example, `renameWithoutSave`.
- Reload, and the name changed: step 3, `currentName`, printed `Sufi Night Live`.
- "The entity was managed": it was loaded inside the transaction, so it sat in the persistence
  context, with its snapshot.
- "Hibernate compared it at commit": the flush compares each managed entity with its snapshot.
- "And wrote the `UPDATE` itself": `update event set name=? ...` came after
  `Committing JPA transaction`, with `[Sufi Night Live]` in the first `?`.
- **S6 is done.** These three explanation points are also the answer to **CP3**.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventEntity.java` has 30 lines, with getters only.
- `EventService.java` has 52 lines, ending with `loadTwiceInOneTransaction`.
- `PersistenceContextDemo.java` exists.
- Postgres runs in the Docker container `pg`, with the 7 seed events.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the setter.** In `EventEntity.java`, before the last `}`, add a blank line, the
comment line, and `public void setName(String name) { this.name = name; }`. The file then
matches the 33 lines in the example.

**Step 3 — add the two service methods.** In `EventService.java`, before the last `}`, add
`renameWithoutSave` and `currentName` with their comments. The file then matches the 65 lines in
the example.

**Step 4 — add the demo and run it.** Create
`scratch-app/src/main/java/com/zenith/data/DirtyCheckingDemo.java` with the 39 lines from the
example. Then run:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=dirty -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

You should see the output from the example:

- after `>>> 2. calling renameWithoutSave`: one `SELECT`, then
  `>>>    name changed in Java to: Sufi Night Live` with no SQL before it, then
  `Committing JPA transaction`, and after it the `update event set name=?, ...` with
  `(1:VARCHAR) <- [Sufi Night Live]`;
- after `>>> 3.`: `>>>    Sufi Night Live`;
- at the end: `>>>    Sufi Night` (the old name back).

If it goes wrong:

- The build stops with `EventService.java:[57,14] cannot find symbol` and
  `symbol: method setName(java.lang.String)`: step 2 is missing.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace`:
  the quotes are missing.

**Step 5 — experiment: add `save()`.** In `EventService.java`, add `events.save(event);` on a new
line right after `event.setName(newName);`. Run the same command. Step 2 of the output still has
exactly one `UPDATE`, still after `Committing JPA transaction`. The only new thing is one more
`Participating in existing transaction` line, from `save()`. Delete the line again.

**Step 6 — run it once more, and check the database.** Run the command from step 4; you should
get the same output as in step 4. Then run:

```bash
docker exec pg psql -U postgres -d zenith -c "select name from event where slug = 'sufi-night';"
```

It prints:

```text
    name    
------------
 Sufi Night
(1 row)
```

**Done when:**

- you have seen the `UPDATE` come after `Committing JPA transaction`, with `[Sufi Night Live]`,
  and no `save()` in the code;
- step 3 of the output read `Sufi Night Live` back from the database;
- adding `save()` changed nothing but one log line;
- psql shows `Sufi Night` at the end.

### Summary for revision

- Loading an entity stores a snapshot of its values in the persistence context.
- Changing a managed entity changes only the Java object. No SQL at that moment.
- At the flush (normally at the commit), Hibernate compares each managed entity with its
  snapshot and writes an `UPDATE` for each dirty one: dirty checking.
- In the log: the `UPDATE` comes after `Committing JPA transaction`.
- The `UPDATE` writes every mapped column by default.
- `save()` is not needed for an entity loaded in the same transaction, and adds no SQL.
- A query can make the flush happen early, so the query sees the change.
- CP3: the entity was managed, Hibernate compared it with its snapshot at the commit, and wrote
  the `UPDATE` itself.

### Practice questions, with answers

**Question 1.** A temporary Spring bean, a service called `DirtyCheckService`, has an
`EventRepository events` field and the three methods below. `SUFI` is Sufi Night's id. Before
each method runs, the event's name in the database is "Sufi Night".

```java
@Transactional
public void sameValue() {
    EventEntity e = events.findById(SUFI).orElseThrow();
    e.setName("Sufi Night");
}

@Transactional
public void changeAndChangeBack() {
    EventEntity e = events.findById(SUFI).orElseThrow();
    e.setName("Sufi Night v2");
    e.setName("Sufi Night");
}

@Transactional
public void changeTwice() {
    EventEntity e = events.findById(SUFI).orElseThrow();
    e.setName("Sufi Night v2");
    e.setName("Sufi Night v3");
}
```

For each method, how many `UPDATE`s are sent at the commit? If there is one, which name does it
write? Say why.

**Answer.** All three were run for real, with the transaction log and the binding log on.

| Method | `SELECT`s | `UPDATE`s | Name written |
| --- | --- | --- | --- |
| `sameValue()` | 1 | **0** | — |
| `changeAndChangeBack()` | 1 | **0** | — |
| `changeTwice()` | 1 | **1** | `Sufi Night v3` |

- **`sameValue()`: no `UPDATE`.** At the flush, Hibernate compares the current name
  ("Sufi Night") with the snapshot ("Sufi Night"). They are equal, so the entity is not dirty.
  Calling a setter is not a change; a different value is.
- **`changeAndChangeBack()`: no `UPDATE`.** Hibernate does not record each `setName` call. It
  only compares the values at the flush. By then the name was back to "Sufi Night", the same as
  the snapshot.
- **`changeTwice()`: one `UPDATE`, with `Sufi Night v3`.** At the flush, the name is
  "Sufi Night v3", which differs from the snapshot. One dirty entity gets one `UPDATE`, carrying
  the value it has at the flush. "Sufi Night v2" never reached the database.

The real log of `sameValue()` and `changeAndChangeBack()` (the SQL shortened): a `SELECT`, then
the commit, with no `Hibernate:` block after `Committing JPA transaction`:

```text
Creating new transaction with name [com.zenith.data.DirtyCheckService.sameValue]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1254184015<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
Found thread-bound EntityManager [SessionImpl(1254184015<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1254184015<open>)]
Closing JPA EntityManager [SessionImpl(1254184015<open>)] after transaction
```

`changeAndChangeBack()` printed the same lines, with its own method name and EntityManager number
(`673430832`). `changeTwice()` printed an `UPDATE` after `Committing JPA transaction`, with
`(1:VARCHAR) <- [Sufi Night v3]`.

**Question 2.** The same bean has this method. `MONSOON` is Monsoon Rock Night's id. Before it
runs, the names in the database are "Sufi Night" and "Monsoon Rock Night".

```java
@Transactional
public void changeTwoEvents() {
    EventEntity a = events.findById(SUFI).orElseThrow();
    EventEntity b = events.findById(MONSOON).orElseThrow();
    a.setName("Sufi Night v6");
    b.setName("Monsoon Rock Night v6");
}
```

- (a) How many `SELECT`s and how many `UPDATE`s does it send?
- (b) Do the `UPDATE`s come before or after the line
  `Committing JPA transaction on EntityManager [...]`?
- (c) Which value goes into the first `?` (the name) of each `UPDATE`?

**Answer.** It was run for real (the SQL shortened):

```text
Creating new transaction with name [com.zenith.data.DirtyCheckService.changeTwoEvents]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(120398960<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@...]
Found thread-bound EntityManager [SessionImpl(120398960<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Found thread-bound EntityManager [SessionImpl(120398960<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000001]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(120398960<open>)]
Hibernate: 
    update ...
... binding parameter (1:VARCHAR) <- [Sufi Night v6]
... binding parameter (2:VARCHAR) <- [sufi-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Hibernate: 
    update ...
... binding parameter (1:VARCHAR) <- [Monsoon Rock Night v6]
... binding parameter (2:VARCHAR) <- [monsoon-rock-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2026-11-20T13:30Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000001]
Closing JPA EntityManager [SessionImpl(120398960<open>)] after transaction
```

- (a) **2 `SELECT`s and 2 `UPDATE`s.** Each event is loaded once. Both are dirty at the flush,
  and each dirty entity gets its own `UPDATE`.
- (b) **After.** Both `UPDATE`s come after `Committing JPA transaction on EntityManager [...]`
  and before `Closing JPA EntityManager`. The flush is part of the commit. Nothing was written
  at the two `setName` lines.
- (c) **`[Sufi Night v6]` in the first `UPDATE`, and `[Monsoon Rock Night v6]` in the second.**
  Each `UPDATE` also carries that event's own unchanged slug, start time and venue, and its own
  id in the `where`. (After the check, a restore method put both names back, and psql showed
  "Sufi Night" and "Monsoon Rock Night".)

---

## Lesson 17 — Outside a transaction, nothing is written (finishes subtask S7)

### Definition

Five ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit.
  With `@Transactional` on a method, Spring begins a transaction before the method runs and
  commits it after the method returns.
- The **EntityManager** is the Hibernate object that talks to the database for you. Each
  transaction gets its own EntityManager.
- The **persistence context** is the EntityManager's memory of the entities loaded in the current
  transaction: one Java object per loaded row. It is thrown away when the transaction ends.
- An entity is **managed** while it sits in a persistence context that is still open.
- **Dirty checking.** When Hibernate loads a row, it also keeps a private copy of the loaded
  values, called the **snapshot**. Just before the commit, Hibernate compares every managed
  entity with its snapshot, and writes an `UPDATE` for each one that changed. You never call
  `save()`.

New words:

- **Detached.** An entity whose persistence context has been thrown away. It is still a normal
  Java object: you can read it and change it. But Hibernate no longer holds it, so there is no
  snapshot and no comparison.
- **Outside a transaction.** Code that runs while no transaction is open. For example, a method
  with no `@Transactional`, called from code that has no `@Transactional` either.

The rule this lesson proves, in the words of the task:

> **Dirty checking only works for managed entities, and entities are only managed inside a
> transaction.**

So a change made to an entity outside a transaction is never written, and no error is shown.

### Why it is needed

- It is exactly subtask S7: "Now do the same outside any transaction. Nothing is written. Say
  the rule out loud."
- It is a silent bug. The code looks the same as working code, and the Java object shows the new
  value. There is no exception and no warning. The only signs are a missing `UPDATE` in the log
  and the old value when you read the row back.
- It is the other side of dirty checking. Inside a `@Transactional` method, a change is written
  without `save()`. That makes it easy to believe that every change is written. This lesson
  shows when it isn't.

### Key points

- Without a transaction around your method, each repository call (`findById`) opens and closes
  its own short transaction.
- When `findById` returns, its persistence context is already thrown away, so the entity is
  detached before your next line runs.
- Changing a detached entity changes only the Java object. No SQL is sent, then or later.
- There is no error and no warning. The only signs: no `update` in the log, and the old value
  when you read the row back.
- `entityManager.contains(entity)` tells you which case you are in: `false` means detached,
  `true` means managed.
- Adding `@Transactional` to the method makes the same code write: the entity stays managed until
  the commit, and dirty checking writes the `UPDATE`.
- `entityManager.flush()` outside a transaction throws `TransactionRequiredException`.
- `save()` on a detached entity does write, but by a merge: in its own transaction, with an extra
  `SELECT`, and it returns a different object.
- The rule: dirty checking only works for managed entities, and entities are only managed inside
  a transaction.

### How it works, step by step

A method with **no** `@Transactional` loads an event and renames it. The code that calls it has
no transaction either:

1. **No transaction.** The method starts. No transaction is open, so there is no persistence
   context.
2. **`findById` opens its own small transaction.** `findById` is a built-in method, and Spring
   Data marked it to run in a transaction: if none is open, it opens its own. The log shows it
   with the word `readOnly`; the next lesson explains that word. A new EntityManager opens, with
   an empty persistence context.
3. **Load.** `findById` sends the `SELECT`. Hibernate builds the object and stores its snapshot
   (name = "Sufi Night").
4. **`findById` returns, and its transaction ends at once.** It commits, and the EntityManager
   closes. The persistence context and the snapshot are thrown away. All this happens **before**
   your next line runs. The variable `event` still points to the Java object, but nothing in
   Hibernate holds it any more: it is **detached**.
5. **Change.** `event.setName("Sufi Night Live")` changes only the Java object. No SQL is sent.
6. **Return.** There is no transaction around the method, so at the end there is no commit, no
   comparison and no `UPDATE`. There is no error either: Hibernate isn't holding the object, so
   it doesn't know the object changed.
7. **The database still has "Sufi Night".** The next read runs in a new transaction with a new,
   empty persistence context. It sends a fresh `SELECT` and gets the old name.

The same method without and with `@Transactional`, from two real runs:

| | No `@Transactional` | With `@Transactional` added |
| --- | --- | --- |
| Transaction for this call | one, named `SimpleJpaRepository.findById` | one, named `EventService.renameOutsideTransaction` |
| What `findById` does | opens and closes its own transaction | joins yours (`Participating in existing transaction`) |
| When `setName` runs, the entity is | detached (`managed: false`) | managed (`managed: true`) |
| At the end of the method | nothing | the commit, then the `UPDATE` |
| Name in the database afterwards | "Sufi Night" | "Sufi Night Live" |
| Error shown | none | none |

`SimpleJpaRepository` is the class inside Spring Data that holds the code for built-in methods
like `findById`, `count` and `save`. That is why its name appears in the log.

### Example

`scratch-app/src/main/java/com/zenith/data/EventService.java` gets one new method,
`renameOutsideTransaction`, at lines 66 to 72. It does the same three things as
`renameWithoutSave` at lines 54 to 59: load, change, print. The one difference: it has no
`@Transactional`. It also prints `entityManager.contains(event)`. `entityManager` is a field the
constructor fills in, and `contains` answers one question: "Is this exact object in your
persistence context right now?" This is the whole file:

```java
package com.zenith.data;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;
    private final EntityManager entityManager;

    public EventService(EventRepository events, EntityManager entityManager) {
        this.events = events;
        this.entityManager = entityManager;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 15: the same event loaded twice, with no transaction around the two loads.
    public String loadTwiceWithoutTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 15: the same two loads inside one transaction. The second one is answered from the
    // persistence context, without a SELECT.
    @Transactional
    public String loadTwiceInOneTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 16: load the event, change its name, and return. There is no save() call anywhere.
    @Transactional
    public void renameWithoutSave(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 16: read the name from the database. findById runs in its own short transaction.
    public String currentName(UUID eventId) {
        return events.findById(eventId).orElseThrow().getName();
    }

    // Lesson 17: the same steps as renameWithoutSave, but without @Transactional.
    public void renameOutsideTransaction(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName()
                + ", managed: " + entityManager.contains(event));
    }
}
```

`scratch-app/src/main/java/com/zenith/data/OutsideTransactionDemo.java` is a new runner (a class
Spring runs once, right after the app starts) with the profile `outside`. It reads the name,
calls `renameOutsideTransaction`, and reads the name again:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 17: the rename from the dirty checking demo, but with no transaction around it.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=outside
@Component
@Profile("outside")
class OutsideTransactionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    OutsideTransactionDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. reading the name from the database");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 2. calling renameOutsideTransaction");
        service.renameOutsideTransaction(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameOutsideTransaction returned");

        System.out.println(">>> 3. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
```

The command turns on two logs: the transaction log, and the **binding log**, which shows the
value put into each `?`. The two settings must be inside quotes, separated by a space:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=outside -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

The real output after the `Started DataApplication` line. The front part of each log line (the
time, level, process number, thread and logger name) is cut off. Binding lines start with `...`:

```text
>>> 1. reading the name from the database
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1254184015<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@208ce928]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1254184015<open>)]
Closing JPA EntityManager [SessionImpl(1254184015<open>)] after transaction
>>>    Sufi Night
>>> 2. calling renameOutsideTransaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(950454841<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@797dd7ef]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(950454841<open>)]
Closing JPA EntityManager [SessionImpl(950454841<open>)] after transaction
>>>    name changed in Java to: Sufi Night Live, managed: false
>>>    renameOutsideTransaction returned
>>> 3. reading the name from the database again
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1187747732<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@44252e9b]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1187747732<open>)]
Closing JPA EntityManager [SessionImpl(1187747732<open>)] after transaction
>>>    Sufi Night
```

### Walkthrough

**Step 2 is subtask S7.** Read its lines in order:

1. **`Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]`.**
   The name in the brackets tells you whose transaction it is. It belongs to `findById`, not to
   your method. Your method has none.
2. **The `SELECT`** with Sufi Night's id. Hibernate stores the snapshot in this transaction's
   persistence context.
3. **`Initiating transaction commit`, `Committing JPA transaction ...`, then
   `Closing JPA EntityManager [SessionImpl(950454841<open>)] after transaction`.** `findById`'s
   transaction is over, and its persistence context is thrown away. All this happens **before**
   `setName` runs.
4. **`>>>    name changed in Java to: Sufi Night Live, managed: false`.** `setName` changed the
   Java object, but `contains` says Hibernate doesn't hold it. The object is detached.
5. **`>>>    renameOutsideTransaction returned`.** Nothing came between this line and the one
   before it: no commit, no `UPDATE`, no error.

**Step 3 proves that nothing was written.** `currentName` runs in a new transaction, so it sends
a fresh `SELECT`, and the database answered "Sufi Night". The whole run has zero `update`
statements.

**Proof that the transaction is what matters.** For one test run, `@Transactional` was added on
the line above `public void renameOutsideTransaction`, and nothing else changed. Step 2 then
looked like this:

```text
>>> 2. calling renameOutsideTransaction
Creating new transaction with name [com.zenith.data.EventService.renameOutsideTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(944780041<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@565fa7d2]
Found thread-bound EntityManager [SessionImpl(944780041<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    name changed in Java to: Sufi Night Live, managed: true
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(944780041<open>)]
Hibernate: 
    update
        event 
    set
        name=?,
        slug=?,
        starts_at=?,
        venue_id=? 
    where
        id=?
... binding parameter (1:VARCHAR) <- [Sufi Night Live]
... binding parameter (2:VARCHAR) <- [sufi-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Closing JPA EntityManager [SessionImpl(944780041<open>)] after transaction
>>>    renameOutsideTransaction returned
```

- The transaction now belongs to the method:
  `[com.zenith.data.EventService.renameOutsideTransaction]`.
- `findById` joins it (`Participating in existing transaction`), so no `Closing` line comes
  before `setName`.
- The same `contains` call now prints `managed: true`.
- The `UPDATE` comes after `Committing JPA transaction`, with `[Sufi Night Live]`. Step 3 then
  read "Sufi Night Live".

After that run, `@Transactional` was removed again, and psql put the old name back.

### Important terms

| Term | Meaning |
| --- | --- |
| **Detached** | An entity whose persistence context has been thrown away. Still a normal Java object, but Hibernate no longer holds it: no snapshot, no comparison, no `UPDATE`. |
| **Outside a transaction** | Code that runs while no transaction is open. Each repository call in it opens and closes its own short transaction. |
| **`entityManager.contains(entity)`** | Asks: "Is this exact object in your persistence context right now?" `true` = managed, `false` = detached. |
| **`SimpleJpaRepository`** | The Spring Data class that holds the code for built-in methods like `findById`, `count` and `save`. |
| **Merge** | What `save()` does with a detached object: load a fresh, managed copy of the row, copy the detached object's values onto it, and return that copy. |
| **`TransactionRequiredException`** | The error from `entityManager.flush()` when no transaction is open. |

### Where it is used

- Every change to an entity needs a transaction around it. In a real Spring app, the usual home
  is a `@Transactional` service method: load, change, return. If someone forgets the annotation,
  or deletes it later, the change is silently lost.
- Reading code: when you see `setSomething(...)` on an entity and no `save()` after it, ask "Is
  this running inside a transaction?" If not, it is a bug.
- Finding a change that doesn't stick: turn on the transaction log with
  `--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG`. If a
  `Closing JPA EntityManager` line comes before the change, and there is no `update` line, this
  is the reason.
- Subtask S7.

### Edge cases

- **`findById` opens a transaction only when none is open.** Without a transaction around the
  method, the log shows
  `Creating new transaction with name [...SimpleJpaRepository.findById]: ...,readOnly`. With one,
  `findById` shows `Participating in existing transaction` instead, and no new transaction.
- **Java and the database disagree, silently.** In the same run, `event.getName()` printed
  "Sufi Night Live", while the database kept "Sufi Night". Nothing ever brings the two back
  together.
- **Each repository call gets its own EntityManager.** In the example, the three `SessionImpl`
  numbers are all different: 1254184015 in step 1, 950454841 in step 2 and 1187747732 in step 3.
  Each one has its own persistence context, thrown away at its own `Closing` line.

### Common mistakes — with the real errors

1. **Forgetting `@Transactional` on a method that changes an entity.** That is the example.
   There is no error, so nothing tells you. The fix is `@Transactional` on the service method;
   the walkthrough shows the `UPDATE` coming back.

2. **Trying to force the write with `entityManager.flush()`.** **Flush** means sending the
   changes Hibernate is holding to the database as SQL. A temporary runner with no transaction
   did this:

   ```java
   EventEntity e4 = events.findById(SUFI).orElseThrow();
   e4.setName("Sufi Night Live");
   try {
       entityManager.flush();
       System.out.println(">>>    flush() returned normally");
   } catch (RuntimeException ex) {
       System.out.println(">>>    " + ex.getClass().getName() + ": " + ex.getMessage());
   }
   ```

   It printed:

   ```text
   >>>    jakarta.persistence.TransactionRequiredException: No EntityManager with actual transaction available for current thread - cannot reliably process 'flush' call
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `jakarta.persistence.TransactionRequiredException` | The kind of error: "this call needs a transaction". |
   | `No EntityManager with actual transaction available for current thread` | Spring looked for an open transaction for the code running right now, and found none. |
   | `cannot reliably process 'flush' call` | So it refused to run `flush()`. |

   Here you do get an error, because you asked for a write yourself and there is no transaction
   to write in. A plain `setName` never asks for anything, so it never fails. After the test,
   the name in the database was still "Sufi Night".

3. **Thinking `save()` on a detached object works like dirty checking.** Inside a transaction,
   `save()` on a managed entity sends no SQL and returns the same object. On a detached object it
   does write, but in a different way. A temporary runner with no transaction did this:

   ```java
   EventEntity e3 = events.findById(SUFI).orElseThrow();
   e3.setName("Sufi Night Live");
   EventEntity saved = events.save(e3);
   System.out.println(">>>    saved == e3 -> " + (saved == e3));
   ```

   The `Creating`, `Committing` and `Closing` lines from the log, and the printed line:

   ```text
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   Committing JPA transaction on EntityManager [SessionImpl(905879757<open>)]
   Closing JPA EntityManager [SessionImpl(905879757<open>)] after transaction
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
   Committing JPA transaction on EntityManager [SessionImpl(1842297286<open>)]
   Closing JPA EntityManager [SessionImpl(1842297286<open>)] after transaction
   >>>    saved == e3 -> false
   ```

   - `save()` opened its **own** transaction, `SimpleJpaRepository.save`.
   - In it, Hibernate sent a second `SELECT` for Sufi Night, which loaded a fresh, managed copy,
     and copied the detached object's values onto that copy. This is a **merge**.
   - At that transaction's commit, the `UPDATE` went out with `[Sufi Night Live]`.
   - `save()` returned the fresh copy, not `e3`: `saved == e3` is `false`. `e3` itself stayed
     detached.

   That took 2 transactions, 2 `SELECT`s and 1 `UPDATE`. The `@Transactional` version in the
   walkthrough did the job with 1 transaction, 1 `SELECT` and 1 `UPDATE`. (The old name was put
   back after the test.)

4. **Creating the demo before adding the method.** With `OutsideTransactionDemo.java` in place
   but no `renameOutsideTransaction` in `EventService`, the build stopped:

   ```text
   [ERROR] .../OutsideTransactionDemo.java:[29,16] cannot find symbol
     symbol:   method renameOutsideTransaction(java.util.UUID,java.lang.String)
     location: variable service of type com.zenith.data.EventService
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `OutsideTransactionDemo.java:[29,16]` | Line 29, column 16: the call `service.renameOutsideTransaction(...)`. |
   | `symbol: method renameOutsideTransaction(java.util.UUID,java.lang.String)` | There is no method with this name that takes a `UUID` and a `String`. |
   | `location: variable service of type com.zenith.data.EventService` | Java looked in `EventService`, the type of `service`, and didn't find it. |

5. **No quotes around the two log settings.** Without the quotes, Maven stops at once, and the
   app never starts:

   ```text
   Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace
   ```

   The shell splits the line at the space, so Maven reads the second setting as one of its own
   options, which it doesn't know. The quotes keep both settings together as one value for
   `-Dspring-boot.run.arguments`.

### Link to the task

S7 says: "Now do the same outside any transaction. Nothing is written. Say the rule out loud:
dirty checking only works for managed entities, and entities are only managed inside a
transaction."

- "Do the same": `renameOutsideTransaction` loads the event and changes its name without
  calling `save()`, exactly like `renameWithoutSave`.
- "Outside any transaction": the method has no `@Transactional`, and neither does the runner
  that calls it. The only transaction in step 2 is the one `findById` opens for itself, and it
  closes before `setName`.
- "Nothing is written": there is no `update` in the log, and step 3 read "Sufi Night".
- "Say the rule out loud": "Dirty checking only works for managed entities, and entities are
  only managed inside a transaction." In the example, `findById`'s transaction ended before
  `setName`, so the entity was detached (`managed: false`). Nothing compared it with a snapshot,
  so nothing wrote an `UPDATE`.
- **S7 is done.**

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventEntity.java` has 33 lines, including `setName`.
- `EventService.java` has 65 lines, ending with `currentName`.
- `DirtyCheckingDemo.java` exists.
- Postgres runs in the Docker container `pg` with the 7 seed events, and Sufi Night is named
  "Sufi Night".

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the method.** In `EventService.java`, before the last `}`, add a blank line, then
the comment line `// Lesson 17: ...` and the method `renameOutsideTransaction`, exactly as in the
example. The file then has 73 lines.

**Step 3 — add the demo.** Create
`scratch-app/src/main/java/com/zenith/data/OutsideTransactionDemo.java` with the 35 lines from
the example.

**Step 4 — run it.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=outside -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

You should see the output from the example:

- after `>>> 2. calling renameOutsideTransaction`, in this order:
  `Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]`
  ending in `readOnly`; one `SELECT`; `Closing JPA EntityManager [...] after transaction`; and
  only then `>>>    name changed in Java to: Sufi Night Live, managed: false`;
- no `update` anywhere in the output;
- after `>>> 3.`: `>>>    Sufi Night`.

The `SessionImpl(...)` and `@...` numbers will be different on your laptop. That is normal.

If it goes wrong:

- The build stops with `OutsideTransactionDemo.java:[29,16] cannot find symbol` and
  `symbol: method renameOutsideTransaction(java.util.UUID,java.lang.String)`: step 2 is missing,
  or the method name is spelled differently.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace`:
  the quotes are missing.

**Step 5 — say the rule out loud.** "Dirty checking only works for managed entities, and entities
are only managed inside a transaction." Then point to the two lines in the output that prove it:
the `Closing JPA EntityManager` line that comes before the change, and `managed: false`.

**Step 6 — experiment: add `@Transactional`.** In `EventService.java`, add `@Transactional` on
its own line, right above `public void renameOutsideTransaction(...)`. Run the command from
step 4. Step 2 of the output changes:

- the transaction is now `[com.zenith.data.EventService.renameOutsideTransaction]`, and
  `findById` shows `Participating in existing transaction`;
- the print says `managed: true`;
- after `Committing JPA transaction` comes the `update event set name=?, ...`, with
  `(1:VARCHAR) <- [Sufi Night Live]`;
- step 3 reads `Sufi Night Live`.

Then delete the `@Transactional` line again.

**Step 7 — put the old name back, and check.** The experiment really renamed the event, so run:

```bash
docker exec pg psql -U postgres -d zenith -c "update event set name = 'Sufi Night' where slug = 'sufi-night';"
```

It prints `UPDATE 1`. Then run the command from step 4 once more. You should get the same output
as in step 4, ending with `>>>    Sufi Night`.

**Done when:**

- you have seen `managed: false`, no `update` in the log, and step 3 reading `Sufi Night`;
- with `@Transactional` added, you have seen `managed: true` and the `UPDATE` after
  `Committing JPA transaction`;
- you can say the rule out loud;
- the last run ends with `>>>    Sufi Night`.

### Summary for revision

- Outside a transaction, each repository call opens and closes its own short transaction.
- The entity it returns is already detached: its persistence context is gone.
- Changing a detached entity changes only the Java object. No SQL, ever, and no error.
- `entityManager.contains(entity)`: `false` = detached, `true` = managed.
- With `@Transactional` on the method, the same code writes the `UPDATE` at the commit.
- `entityManager.flush()` with no transaction throws `TransactionRequiredException`.
- `save()` on a detached entity writes by a merge: its own transaction, an extra `SELECT`, and a
  different object returned.
- The rule (S7): dirty checking only works for managed entities, and entities are only managed
  inside a transaction.

### Practice questions, with answers

**Question 1.** A temporary runner has no `@Transactional`. It has two fields: `events`, the
`EventRepository`, and `service`, the `EventService` bean (a bean is an object that Spring makes
and keeps). `SUFI` is Sufi Night's id. The database has 7 events, and Sufi Night's name is
"Sufi Night". `EventService` has this method:

```java
@Transactional
public String describeInOneTransaction(UUID eventId) {
    long total = events.count();
    String name = events.findById(eventId).orElseThrow().getName();
    return name + " (one of " + total + " events)";
}
```

The runner does this:

```java
EventEntity e1 = events.findById(SUFI).orElseThrow();
e1.setName("Sufi Night Live");
System.out.println(">>>    describeInOneTransaction -> " + service.describeInOneTransaction(SUFI));
System.out.println(">>>    e1.getName() -> " + e1.getName());
```

- (a) What do the two `println` lines print?
- (b) Is there an `UPDATE` anywhere in the log? Say why, using the rule.

**Answer.** It was run for real. The log (the SQL shortened):

```text
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(328325471<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@51751265]
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(328325471<open>)]
Closing JPA EntityManager [SessionImpl(328325471<open>)] after transaction
Creating new transaction with name [com.zenith.data.EventService.describeInOneTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1865288005<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@3d268a2c]
Found thread-bound EntityManager [SessionImpl(1865288005<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select count(*) ...
Found thread-bound EntityManager [SessionImpl(1865288005<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1865288005<open>)]
Closing JPA EntityManager [SessionImpl(1865288005<open>)] after transaction
>>>    describeInOneTransaction -> Sufi Night (one of 7 events)
>>>    e1.getName() -> Sufi Night Live
```

- (a) **`>>>    describeInOneTransaction -> Sufi Night (one of 7 events)`**, then
  **`>>>    e1.getName() -> Sufi Night Live`**.
- (b) **No `UPDATE` anywhere** (2 transactions, 3 `SELECT`s, 0 `UPDATE`s). Why:
  - `e1` was loaded by `findById` in its own short transaction, which ended at once
    (`Closing JPA EntityManager [SessionImpl(328325471<open>)]`). So `e1` was detached when
    `setName` ran, and the change stayed in Java only.
  - `describeInOneTransaction` began a **new** transaction with a new, empty persistence context.
    It knew nothing about `e1`. Its `findById` sent a fresh `SELECT` and built a **new** object
    from the row, which still said "Sufi Night". That is why it returned "Sufi Night".
  - At its commit, dirty checking compared only that new object with its snapshot. Nothing had
    changed it, so there was nothing to write.
  - `e1` kept "Sufi Night Live" in Java. Reading the row afterwards gave "Sufi Night".

**Question 2.** The same runner also has a field `entityManager`, the EntityManager.
`EventService` has these two methods:

```java
@Transactional
public void renameWithoutSave(UUID eventId, String newName) {
    EventEntity event = events.findById(eventId).orElseThrow();
    event.setName(newName);
    System.out.println(">>>    name changed in Java to: " + event.getName());
}

public String currentName(UUID eventId) {
    return events.findById(eventId).orElseThrow().getName();
}
```

This time the runner makes its own `EventService` with `new`:

```java
new EventService(events, entityManager).renameWithoutSave(SUFI, "Sufi Night Live");
System.out.println(">>> NAME " + service.currentName(SUFI));
```

A reminder you need: Spring adds a transaction around a `@Transactional` method only when you
call it on the bean Spring made at startup. An object you make yourself with `new` is a plain
Java object, and Spring never sees its calls.

- (a) For the `renameWithoutSave` call, how many `Creating new transaction` lines does the log
  show, and what name is inside `[...]`?
- (b) Is there an `UPDATE`?
- (c) What does the last line print? Say why, using the rule.

**Answer.** It was run for real. The log of the `renameWithoutSave` call (the SQL shortened):

```text
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1129859294<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@48971262]
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1129859294<open>)]
Closing JPA EntityManager [SessionImpl(1129859294<open>)] after transaction
>>>    name changed in Java to: Sufi Night Live
```

- (a) **One**, with
  `[org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]` and
  `readOnly`. There is no `EventService.renameWithoutSave` transaction: the `@Transactional` on
  the method did nothing, because the object was made with `new`.
- (b) **No `UPDATE`.**
- (c) **`>>> NAME Sufi Night`.** With no transaction around `renameWithoutSave`, `findById`
  opened its own short transaction, which ended at once (the `Closing` line comes before the
  `>>>` line). So the entity was detached when `setName` ran. Dirty checking only works for
  managed entities, and entities are only managed inside a transaction, so nothing was written.
  (The name in the database stayed "Sufi Night"; nothing needed restoring.)

---

## Lesson 18 — `@Transactional(readOnly = true)` (finishes subtask S8)

### Definition

Ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit.
  With `@Transactional` on a method, Spring begins a transaction before the method runs and
  commits it after the method returns.
- The **EntityManager** is the Hibernate object that talks to the database for you. Each
  transaction gets its own EntityManager. Its **persistence context** is its memory of the
  entities loaded in the current transaction.
- **Snapshot and dirty checking.** When Hibernate loads a row, it keeps a private copy of the
  loaded values, the snapshot. Just before the commit, it compares every loaded entity with its
  snapshot, and writes an `UPDATE` for each one that changed.
- **Flush.** Sending the changes Hibernate is holding to the database as SQL. Normally Hibernate
  does it by itself at the commit.
- The **JDBC connection** is the open line between the app and Postgres. Every SQL statement
  travels on it.

New words:

- **`readOnly = true`.** A setting inside `@Transactional`. It is a promise: "this method only
  reads; it never writes." Spring passes the promise on to Hibernate and to the database.
- **Flush mode.** Hibernate's rule for when it flushes by itself. `AUTO`, the normal one:
  Hibernate flushes by itself at the commit, and before a query that needs it. `MANUAL`:
  Hibernate never flushes by itself; only a `flush()` call you write makes it flush.
- **Read-only entity.** An entity that Hibernate has agreed not to watch. It keeps no snapshot
  for it, and never writes an `UPDATE` for it.
- **`transaction_read_only`.** Postgres's own setting for the current transaction: `on` means
  read-only, `off` means normal. The SQL `show transaction_read_only` asks Postgres for it.
- **Session.** Hibernate's own version of the EntityManager, with extra methods.
  `entityManager.unwrap(Session.class)` gives it to you.

What `readOnly = true` changes, from a real run:

| | Normal `@Transactional` | `@Transactional(readOnly = true)` |
| --- | --- | --- |
| Hibernate flush mode | `AUTO` | `MANUAL` |
| Loaded entity | normal, with a snapshot | read-only, no snapshot |
| Dirty checking at the commit | yes | skipped |
| JDBC connection | normal | set to read-only |
| Postgres `transaction_read_only` | `off` | `on` |

### Why it is needed

- It is exactly subtask S8: "Use `@Transactional(readOnly = true)` on your query methods. It lets
  Hibernate skip dirty-checking work and tells the database the transaction will not write. It
  is a genuine optimisation, not decoration."
- Less work for Hibernate. In a normal transaction, Hibernate keeps a snapshot of every entity it
  loads (a second copy of all its values), and at the commit it compares every entity with its
  snapshot. A method that loads 10,000 events just to show a list would pay for 10,000 snapshots
  and 10,000 comparisons, only to find that nothing changed. With `readOnly = true`, there are no
  snapshots and no comparison.
- Safety from the database. Postgres knows the transaction is read-only, and refuses any write
  inside it.

### Key points

- `readOnly = true` is a promise that the method only reads. Spring passes it on to Hibernate and
  to the JDBC connection.
- Hibernate: the flush mode becomes `MANUAL`, so there is no flush at the commit. Every entity is
  loaded as read-only, so no snapshot is kept.
- The database: Spring sets the connection to read-only (`Setting JDBC Connection [...] read-only`),
  so inside Postgres the transaction is read-only (`transaction_read_only` is `on`). Postgres
  refuses writes in it.
- After the commit, Spring sets the connection back to normal
  (`Resetting read-only flag of JDBC Connection [...]`). The connection pool lends the same
  connection out again, so this matters.
- A change to an entity in a read-only transaction is silently dropped. No error.
- An SQL write in a read-only transaction fails: `ERROR: cannot execute UPDATE in a read-only
  transaction`.
- Spring Data's `SimpleJpaRepository` has `@Transactional(readOnly = true)` on the whole class.
  Only its writing methods, like `save` and `delete`, have a plain `@Transactional`.
- A setting on a method wins over the setting on the class.

### How it works, step by step

A `@Transactional(readOnly = true)` method loads an event and renames it:

1. **Begin.** Spring begins the transaction. The log line ends with `,readOnly`.
2. **Spring tells Hibernate.** It switches the flush mode to `MANUAL`, and tells Hibernate to load
   every entity as read-only.
3. **Spring tells the JDBC connection.** It sets the connection to read-only; with the right log
   on, you see `Setting JDBC Connection [...] read-only`. The Postgres driver passes this on, so
   inside Postgres the transaction is read-only: `transaction_read_only` is `on`.
4. **Load.** `findById` sends the `SELECT`. Hibernate builds the object but keeps no snapshot,
   because the entity is read-only.
5. **Change.** `setName` changes the Java object, and nothing else. Java doesn't stop you;
   Hibernate just isn't watching.
6. **Commit.** The flush mode is `MANUAL`, so Hibernate doesn't flush by itself. There is no
   comparison, no `UPDATE` and no error.
7. **Reset.** Spring sets the connection back to normal
   (`Resetting read-only flag of JDBC Connection [...]`), and the EntityManager closes.

### Example

`scratch-app/src/main/java/com/zenith/data/EventService.java` gets a new import at line 4,
`import org.hibernate.Session;`, and new code at lines 75 to 103:

- `inspectNormalTransaction` (plain `@Transactional`) and `inspectReadOnlyTransaction`
  (`readOnly = true`) do exactly the same thing: load Sufi Night and call `inspect`.
- `renameInReadOnlyTransaction` has the same steps as `renameWithoutSave` (load, change, print),
  in a read-only transaction.
- `inspect` is a private helper with no annotation, so it runs inside the transaction of the
  method that calls it. It reports three things: the flush mode
  (`session.getHibernateFlushMode()`), whether the entity is read-only
  (`session.isReadOnly(event)`), and Postgres's answer to `show transaction_read_only`
  (`createNativeQuery(...).getSingleResult()`).

This is the whole file:

```java
package com.zenith.data;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// A service: a Spring bean that holds the app's work. It is the usual home of @Transactional.
@Service
public class EventService {

    private final EventRepository events;
    private final EntityManager entityManager;

    public EventService(EventRepository events, EntityManager entityManager) {
        this.events = events;
        this.entityManager = entityManager;
    }

    // Lesson 14: no @Transactional here, so each repository call runs in its own short transaction.
    public String describeWithoutTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 14: the same two calls, inside one transaction that Spring begins before this method
    // runs and commits after it returns.
    @Transactional
    public String describeInOneTransaction(UUID eventId) {
        long total = events.count();
        String name = events.findById(eventId).orElseThrow().getName();
        return name + " (one of " + total + " events)";
    }

    // Lesson 15: the same event loaded twice, with no transaction around the two loads.
    public String loadTwiceWithoutTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 15: the same two loads inside one transaction. The second one is answered from the
    // persistence context, without a SELECT.
    @Transactional
    public String loadTwiceInOneTransaction(UUID eventId) {
        EventEntity first = events.findById(eventId).orElseThrow();
        EventEntity second = events.findById(eventId).orElseThrow();
        return "same object: " + (first == second) + ", managed: " + entityManager.contains(first);
    }

    // Lesson 16: load the event, change its name, and return. There is no save() call anywhere.
    @Transactional
    public void renameWithoutSave(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 16: read the name from the database. findById runs in its own short transaction.
    public String currentName(UUID eventId) {
        return events.findById(eventId).orElseThrow().getName();
    }

    // Lesson 17: the same steps as renameWithoutSave, but without @Transactional.
    public void renameOutsideTransaction(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName()
                + ", managed: " + entityManager.contains(event));
    }

    // Lesson 18: what Hibernate and Postgres say about a normal transaction.
    @Transactional
    public String inspectNormalTransaction(UUID eventId) {
        return inspect(events.findById(eventId).orElseThrow());
    }

    // Lesson 18: the same, in a read-only transaction.
    @Transactional(readOnly = true)
    public String inspectReadOnlyTransaction(UUID eventId) {
        return inspect(events.findById(eventId).orElseThrow());
    }

    // Lesson 18: the same steps as renameWithoutSave, but in a read-only transaction.
    @Transactional(readOnly = true)
    public void renameInReadOnlyTransaction(UUID eventId, String newName) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setName(newName);
        System.out.println(">>>    name changed in Java to: " + event.getName());
    }

    // Lesson 18: runs inside the caller's transaction. Session is Hibernate's own version of the
    // EntityManager, with extra methods.
    private String inspect(EventEntity event) {
        Session session = entityManager.unwrap(Session.class);
        Object postgres = entityManager.createNativeQuery("show transaction_read_only").getSingleResult();
        return "flush mode: " + session.getHibernateFlushMode()
                + ", entity read-only: " + session.isReadOnly(event)
                + ", postgres transaction_read_only: " + postgres;
    }
}
```

`scratch-app/src/main/java/com/zenith/data/ReadOnlyDemo.java` is a new runner with the profile
`readonly`. It runs both inspections, then the read-only rename, then reads the name back:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 18: a normal transaction next to a read-only one, then a rename in a read-only one.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=readonly
@Component
@Profile("readonly")
class ReadOnlyDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    ReadOnlyDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. a normal transaction");
        System.out.println(">>>    " + service.inspectNormalTransaction(SUFI_NIGHT));

        System.out.println(">>> 2. a read-only transaction");
        System.out.println(">>>    " + service.inspectReadOnlyTransaction(SUFI_NIGHT));

        System.out.println(">>> 3. calling renameInReadOnlyTransaction");
        service.renameInReadOnlyTransaction(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameInReadOnlyTransaction returned");

        System.out.println(">>> 4. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
```

The command turns on three logs, all inside one pair of quotes and separated by spaces: the
transaction log, the binding log (the value put into each `?`), and
`--logging.level.org.springframework.jdbc.datasource.DataSourceUtils=DEBUG`, which shows where
Spring sets the JDBC connection to read-only and back:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=readonly -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace --logging.level.org.springframework.jdbc.datasource.DataSourceUtils=DEBUG"
```

The real output after the `Started DataApplication` line. The front part of each log line (the
time, level, process number, thread and logger name) is cut off. Binding lines start with `...`:

```text
>>> 1. a normal transaction
Creating new transaction with name [com.zenith.data.EventService.inspectNormalTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(785090482<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@12e04531]
Found thread-bound EntityManager [SessionImpl(785090482<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Hibernate: 
    show transaction_read_only
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(785090482<open>)]
Closing JPA EntityManager [SessionImpl(785090482<open>)] after transaction
>>>    flush mode: AUTO, entity read-only: false, postgres transaction_read_only: off
>>> 2. a read-only transaction
Creating new transaction with name [com.zenith.data.EventService.inspectReadOnlyTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1334940097<open>)] for JPA transaction
Setting JDBC Connection [HikariProxyConnection@1477938025 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6] read-only
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@4a723adf]
Found thread-bound EntityManager [SessionImpl(1334940097<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Hibernate: 
    show transaction_read_only
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1334940097<open>)]
Resetting read-only flag of JDBC Connection [HikariProxyConnection@1477938025 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6]
Closing JPA EntityManager [SessionImpl(1334940097<open>)] after transaction
>>>    flush mode: MANUAL, entity read-only: true, postgres transaction_read_only: on
>>> 3. calling renameInReadOnlyTransaction
Creating new transaction with name [com.zenith.data.EventService.renameInReadOnlyTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1497818432<open>)] for JPA transaction
Setting JDBC Connection [HikariProxyConnection@106013593 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6] read-only
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@79ef8115]
Found thread-bound EntityManager [SessionImpl(1497818432<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    name changed in Java to: Sufi Night Live
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1497818432<open>)]
Resetting read-only flag of JDBC Connection [HikariProxyConnection@106013593 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6]
Closing JPA EntityManager [SessionImpl(1497818432<open>)] after transaction
>>>    renameInReadOnlyTransaction returned
>>> 4. reading the name from the database again
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1682899773<open>)] for JPA transaction
Setting JDBC Connection [HikariProxyConnection@1867673489 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6] read-only
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@26c349fc]
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1682899773<open>)]
Resetting read-only flag of JDBC Connection [HikariProxyConnection@1867673489 wrapping org.postgresql.jdbc.PgConnection@2f14b0f6]
Closing JPA EntityManager [SessionImpl(1682899773<open>)] after transaction
>>>    Sufi Night
```

### Walkthrough

**Steps 1 and 2 run the same code with a different annotation.** Where their logs differ:

| Line in the log | Step 1 (normal) | Step 2 (read-only) |
| --- | --- | --- |
| End of `Creating new transaction ...` | `ISOLATION_DEFAULT` | `ISOLATION_DEFAULT,readOnly` |
| `Setting JDBC Connection [...] read-only` | not there | right after `Opened new EntityManager` |
| `Resetting read-only flag of JDBC Connection [...]` | not there | right after `Committing JPA transaction` |

Both steps send the same two SQL statements, the `SELECT` and `show transaction_read_only`. Step 1
printed `flush mode: AUTO, entity read-only: false, postgres transaction_read_only: off`; step 2
printed `flush mode: MANUAL, entity read-only: true, postgres transaction_read_only: on`.

`HikariProxyConnection` is the wrapper the **connection pool** puts around the real Postgres
connection (`PgConnection`). A connection pool keeps a few connections open and lends one to each
transaction. Steps 2, 3 and 4 all show the same `PgConnection@2f14b0f6`: the pool lent out the same
real connection each time. That is why the `Resetting read-only flag` line matters: the next
transaction gets the connection back in normal mode.

**Step 3 is the dirty-checking half of S8.** Read its lines in order:

1. `Creating new transaction with name [com.zenith.data.EventService.renameInReadOnlyTransaction]: ...,readOnly`.
2. `Setting JDBC Connection [...] read-only`.
3. The `SELECT`. Hibernate keeps no snapshot this time.
4. `>>>    name changed in Java to: Sufi Night Live`.
5. `Initiating transaction commit` and `Committing JPA transaction ...`, then straight on to
   `Resetting read-only flag ...` and `Closing ...`. **There is no `UPDATE`**, and no error.

**Step 4 proves nothing was written:** it read `Sufi Night`. Its `findById` also shows
`Setting JDBC Connection [...] read-only`, because Spring Data's built-in `findById` is read-only
too.

**Proof that `readOnly = true` is what stops the write.** For one test run,
`@Transactional(readOnly = true)` above `renameInReadOnlyTransaction` was changed to plain
`@Transactional`, and nothing else. Step 3 then looked like this:

```text
>>> 3. calling renameInReadOnlyTransaction
Creating new transaction with name [com.zenith.data.EventService.renameInReadOnlyTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1677654076<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@5e752a2a]
Found thread-bound EntityManager [SessionImpl(1677654076<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        ee1_0.venue_id 
    from
        event ee1_0 
    where
        ee1_0.id=?
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    name changed in Java to: Sufi Night Live
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1677654076<open>)]
Hibernate: 
    update
        event 
    set
        name=?,
        slug=?,
        starts_at=?,
        venue_id=? 
    where
        id=?
... binding parameter (1:VARCHAR) <- [Sufi Night Live]
... binding parameter (2:VARCHAR) <- [sufi-night]
... binding parameter (3:TIMESTAMP_UTC) <- [2027-07-10T14:00Z]
... binding parameter (4:UUID) <- [11111111-1111-1111-1111-111111111111]
... binding parameter (5:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Closing JPA EntityManager [SessionImpl(1677654076<open>)] after transaction
>>>    renameInReadOnlyTransaction returned
```

- The transaction line no longer ends with `,readOnly`, and there is no `Setting JDBC Connection`
  line.
- The `UPDATE` is back, after `Committing JPA transaction`, with `[Sufi Night Live]`.
- Step 4 then read "Sufi Night Live".

After that run, `readOnly = true` was put back, and psql restored the name.

### Important terms

| Term | Meaning |
| --- | --- |
| **`readOnly = true`** | A setting inside `@Transactional`: the promise that the method only reads. Spring passes it on to Hibernate and to the JDBC connection. |
| **Flush mode** | Hibernate's rule for when it flushes by itself. `AUTO`: at the commit, and before queries that need it. `MANUAL`: never by itself. |
| **Read-only entity** | An entity Hibernate doesn't watch: no snapshot, never an `UPDATE`. |
| **`transaction_read_only`** | Postgres's setting for the current transaction: `on` = read-only, `off` = normal. |
| **Session** | Hibernate's own version of the EntityManager, with extra methods like `getHibernateFlushMode()` and `isReadOnly(entity)`. |
| **Connection pool** | Keeps a few JDBC connections open and lends one to each transaction. `HikariProxyConnection` is its wrapper. |
| **`DataSourceUtils` log** | `--logging.level.org.springframework.jdbc.datasource.DataSourceUtils=DEBUG`: shows `Setting JDBC Connection [...] read-only` and `Resetting read-only flag ...`. |
| **`javap`** | A JDK tool that prints what is inside a compiled class, including its annotations. |

### Where it is used

- Service methods that only read, like "show an event", "list a venue's events" or "count
  tickets", get `@Transactional(readOnly = true)`.
- Spring Data itself: `readOnly = true` on the whole `SimpleJpaRepository` class, and a plain
  `@Transactional` on the writing methods. Many service classes copy this pattern: `readOnly =
  true` on the class, and a plain `@Transactional` on the few methods that write. The setting on a
  method wins over the setting on the class; that is why `save` transactions in the log have no
  `readOnly`.
- Bigger setups: some apps use the read-only flag to send read-only work to a read-only copy of
  the database, called a replica. That is not set up here.
- Subtask S8.

### Edge cases

- **Spring Data's built-in read methods are already read-only.** `javap -v` on
  `org.springframework.data.jpa.repository.support.SimpleJpaRepository` (from
  `spring-data-jpa-3.5.13.jar`) shows this annotation on the class:

  ```text
  RuntimeVisibleAnnotations:
      org.springframework.transaction.annotation.Transactional(
        readOnly=true
  ```

  `save` and `delete` carry their own plain `org.springframework.transaction.annotation.Transactional`;
  `findById` and `count` carry none, so they get the class's `readOnly=true`. That is why every
  `findById` in the logs ends with `readOnly`.
- **No snapshot is kept for a read-only entity.** A temporary check looked inside Hibernate's
  persistence context (internal Hibernate classes, for learning only):

  ```java
  EntityEntry entry = entityManager.unwrap(SessionImplementor.class).getPersistenceContext().getEntry(event);
  // ... "snapshot kept: " + (entry.getLoadedState() != null)
  ```

  It printed, for a normal and then a read-only transaction:

  ```text
  >>>    flush mode: AUTO, entity read-only: false, snapshot kept: true, postgres transaction_read_only: off (String)
  >>>    flush mode: MANUAL, entity read-only: true, snapshot kept: false, postgres transaction_read_only: on (String)
  ```

  The `(String)` shows that Postgres's answer to `show transaction_read_only` arrives in Java as
  the text `"on"` or `"off"`.
- **The pool reuses the connection.** Steps 2, 3 and 4 of the example used the same
  `PgConnection@2f14b0f6`. Without the reset after each read-only transaction, the next
  transaction would get a read-only connection.

### Common mistakes — with the real errors

1. **Putting `readOnly = true` on a method that writes.** That is step 3 of the example. The
   change is silently dropped. There is no error, because Java lets you call `setName` and
   Hibernate simply never writes it. A method that changes data needs plain `@Transactional`.

2. **Writing with SQL inside a read-only transaction.** This time the database itself says no. A
   temporary service method did this:

   ```java
   @Transactional(readOnly = true)
   public int nativeUpdateReadOnly() {
       return entityManager.createNativeQuery(
               "update event set name = 'Sufi Night Live' where slug = 'sufi-night'").executeUpdate();
   }
   ```

   The runner called it inside a `try`, and printed the error and its cause:

   ```java
   try {
       System.out.println(">>>    rows: " + check.nativeUpdateReadOnly());
   } catch (RuntimeException ex) {
       System.out.println(">>>    " + ex.getClass().getName() + ": " + ex.getMessage());
       Throwable c = ex.getCause();
       while (c != null) {
           System.out.println(">>>    caused by " + c.getClass().getName() + ": " + c.getMessage());
           c = c.getCause();
       }
   }
   ```

   The real log:

   ```text
   Creating new transaction with name [com.zenith.data.RoCheckService.nativeUpdateReadOnly]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   Opened new EntityManager [SessionImpl(350761035<open>)] for JPA transaction
   Setting JDBC Connection [HikariProxyConnection@2064484576 wrapping org.postgresql.jdbc.PgConnection@76d72b5b] read-only
   Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@724e483f]
   Hibernate: 
       update
           event 
       set
           name = 'Sufi Night Live' 
       where
           slug = 'sufi-night'
   SQL Error: 0, SQLState: 25006
   ERROR: cannot execute UPDATE in a read-only transaction
   Initiating transaction rollback
   Rolling back JPA transaction on EntityManager [SessionImpl(350761035<open>)]
   Resetting read-only flag of JDBC Connection [HikariProxyConnection@2064484576 wrapping org.postgresql.jdbc.PgConnection@76d72b5b]
   Closing JPA EntityManager [SessionImpl(350761035<open>)] after transaction
   >>>    org.hibernate.exception.GenericJDBCException: JDBC exception executing SQL [update event set name = 'Sufi Night Live' where slug = 'sufi-night'] [ERROR: cannot execute UPDATE in a read-only transaction] [n/a]
   >>>    caused by org.postgresql.util.PSQLException: ERROR: cannot execute UPDATE in a read-only transaction
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `SQL Error: 0, SQLState: 25006` | A warning line from Hibernate. `25006` is Postgres's code for "a write inside a read-only transaction". |
   | `ERROR: cannot execute UPDATE in a read-only transaction` | Postgres's own words: it refused the `UPDATE`. |
   | `Initiating transaction rollback` | The method ended with an error, so Spring undid the transaction instead of committing it. A later lesson is about exactly this. |
   | `org.hibernate.exception.GenericJDBCException` | Hibernate's wrapper around the database error. |
   | `caused by org.postgresql.util.PSQLException` | The original error, from the Postgres driver. |

   Afterwards the name was still "Sufi Night". So "tells the database the transaction will not
   write" is not just a label: Postgres enforces it.

3. **Forgetting `import org.hibernate.Session;`.** The build stopped:

   ```text
   [ERROR] .../EventService.java:[97,9] cannot find symbol
     symbol:   class Session
     location: class com.zenith.data.EventService
   [ERROR] .../EventService.java:[97,48] cannot find symbol
     symbol:   class Session
     location: class com.zenith.data.EventService
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `EventService.java:[97,9]` | Line 97, column 9: the `Session` at the start of `Session session = ...`. It is line 97, not 98, because without the import line the file is one line shorter. |
   | `EventService.java:[97,48]` | The same line, column 48: the `Session.class` inside `unwrap(...)`. |
   | `symbol: class Session` | Java doesn't know any class called `Session` here. |
   | `location: class com.zenith.data.EventService` | It looked in `EventService` and its imports, and didn't find it. |

4. **No quotes around the log settings.** Maven stopped at once:

   ```text
   Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace
   ```

   The shell splits the line at each space, so Maven reads the second setting as one of its own
   options, which it doesn't know. One pair of quotes around all three settings fixes it.

### Link to the task

S8 says: "Use `@Transactional(readOnly = true)` on your query methods. It lets Hibernate skip
dirty-checking work and tells the database the transaction will not write. It is a genuine
optimisation, not decoration."

- "On your query methods": `inspectReadOnlyTransaction` only reads, and has `readOnly = true`.
  Spring Data's own query methods, like `findById` and `count`, already have it.
- "It lets Hibernate skip dirty-checking work": step 2 printed `flush mode: MANUAL` and
  `entity read-only: true`, and a check showed no snapshot kept. Step 3 changed the name, and
  nothing was written.
- "And tells the database the transaction will not write": `Setting JDBC Connection [...]
  read-only` in the log, then Postgres itself answered `transaction_read_only: on`. It even
  refused an SQL `UPDATE`.
- "A genuine optimisation, not decoration": the flag changes what Hibernate and Postgres actually
  do. Take it away, and the `UPDATE` comes back (the proof in the walkthrough).
- **S8 is done.**

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `EventService.java` has 73 lines, ending with `renameOutsideTransaction`.
- `OutsideTransactionDemo.java` exists.
- Postgres runs in the Docker container `pg` with the 7 seed events, and Sufi Night is named
  "Sufi Night".

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the import.** In `EventService.java`, add `import org.hibernate.Session;` as a new
line 4, between `import jakarta.persistence.EntityManager;` and
`import org.springframework.stereotype.Service;`.

**Step 3 — add the new code.** Before the last `}`, add a blank line, then lines 75 to 103 from the
example: the three `// Lesson 18` methods and the private `inspect` helper. The file then has 104
lines.

**Step 4 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/ReadOnlyDemo.java`
with the 38 lines from the example.

**Step 5 — run it.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=readonly -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace --logging.level.org.springframework.jdbc.datasource.DataSourceUtils=DEBUG"
```

You should see the output from the example:

- after step 1: `>>>    flush mode: AUTO, entity read-only: false, postgres transaction_read_only: off`;
- after step 2: `>>>    flush mode: MANUAL, entity read-only: true, postgres transaction_read_only: on`,
  with `Setting JDBC Connection [...] read-only` in that step's log;
- in step 3: `>>>    name changed in Java to: Sufi Night Live`, then the commit with no `update`
  after it;
- no `update` anywhere in the output;
- after `>>> 4.`: `>>>    Sufi Night`.

The `SessionImpl(...)`, `HikariProxyConnection@...` and `PgConnection@...` numbers will be
different on your laptop. That is normal.

If it goes wrong:

- The build stops with `EventService.java:[97,9] cannot find symbol` and `symbol: class Session`:
  step 2 is missing.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace`:
  the quotes are missing.

**Step 6 — experiment: remove `readOnly = true`.** In `EventService.java`, change the
`@Transactional(readOnly = true)` right above `public void renameInReadOnlyTransaction(...)` to
plain `@Transactional`. Run the command from step 5. Step 3 of the output changes:

- the transaction line no longer ends with `,readOnly`, and step 3 has no
  `Setting JDBC Connection` line;
- after `Committing JPA transaction` comes the `update event set name=?, ...`, with
  `(1:VARCHAR) <- [Sufi Night Live]`;
- step 4 reads `Sufi Night Live`.

Then change it back to `@Transactional(readOnly = true)`.

**Step 7 — put the old name back, and check.** The experiment really renamed the event, so run:

```bash
docker exec pg psql -U postgres -d zenith -c "update event set name = 'Sufi Night' where slug = 'sufi-night';"
```

It prints `UPDATE 1`. Then run the command from step 5 once more. It should end with
`>>>    Sufi Night`.

**Done when:**

- you have seen `AUTO`, `false` and `off` in step 1, and `MANUAL`, `true` and `on` in step 2;
- you have seen the rename in step 3 write nothing, and the `UPDATE` come back when
  `readOnly = true` was removed;
- you can say what `readOnly = true` tells Hibernate, and what it tells Postgres;
- the last run ends with `>>>    Sufi Night`.

### Summary for revision

- `readOnly = true` is a promise that the method only reads. Spring passes it on to Hibernate and
  to the JDBC connection.
- Hibernate: flush mode `MANUAL`, read-only entities, no snapshots, no dirty checking at the
  commit.
- Postgres: the transaction is read-only (`transaction_read_only` = `on`), and writes are refused
  (`SQLState: 25006`).
- In the log: `,readOnly` at the end of `Creating new transaction ...`; with the `DataSourceUtils`
  log on, `Setting JDBC Connection [...] read-only` and `Resetting read-only flag ...`.
- A change to an entity in a read-only transaction is silently dropped, with no error.
- Spring Data's built-in read methods are already read-only; `save` and `delete` are not.
- S8: it is a genuine optimisation (no snapshots, no comparison), plus a safety net from the
  database.

### Practice questions, with answers

**Question 1.** A temporary Spring bean, a service, has two fields: `events` (the
`EventRepository`) and `entityManager` (the EntityManager). `SUFI` is Sufi Night's id, and the name
in the database is "Sufi Night". `save()` is one of Spring Data's built-in methods, and it has its
own plain `@Transactional`, without `readOnly`. The bean has this method, and it is called with
`"Sufi Night Live"`:

```java
@Transactional(readOnly = true)
public void renameReadOnlyThenSave(String newName) {
    EventEntity event = events.findById(SUFI).orElseThrow();
    event.setName(newName);
    EventEntity returned = events.save(event);
    System.out.println(">>>    save returned the same object: " + (returned == event));
}
```

- (a) What does the `println` print?
- (b) For the `save` call, does the log show `Creating new transaction ...` or
  `Participating in existing transaction`?
- (c) Is there an `UPDATE`? What is the name in the database afterwards? Say why.

**Answer.** It was run for real. The log (the SQL shortened):

```text
Creating new transaction with name [com.zenith.data.RoCheckService.renameReadOnlyThenSave]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(812293336<open>)] for JPA transaction
Setting JDBC Connection [HikariProxyConnection@2085805920 wrapping org.postgresql.jdbc.PgConnection@76d72b5b] read-only
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2fe12ec3]
Found thread-bound EntityManager [SessionImpl(812293336<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
Found thread-bound EntityManager [SessionImpl(812293336<open>)] for JPA transaction
Participating in existing transaction
>>>    save returned the same object: true
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(812293336<open>)]
Resetting read-only flag of JDBC Connection [HikariProxyConnection@2085805920 wrapping org.postgresql.jdbc.PgConnection@76d72b5b]
Closing JPA EntityManager [SessionImpl(812293336<open>)] after transaction
```

- (a) **`>>>    save returned the same object: true`.**
- (b) **`Participating in existing transaction`**, the second pair of `Found thread-bound ...` and
  `Participating ...` lines. `save()`'s own plain `@Transactional` did not start a new
  transaction. It joined the read-only one that was already open, and joining doesn't change the
  settings of the transaction it joins.
- (c) **No `UPDATE`, and the name stayed "Sufi Night".** Why:
  - The entity was loaded in the read-only transaction, so it is a read-only entity, with no
    snapshot.
  - `save()` on an entity that is already in the persistence context sends no SQL and returns the
    same object.
  - At the commit, the flush mode is `MANUAL`, so Hibernate doesn't flush by itself, and there is
    nothing to compare anyway.

  After the call, reading the name back printed `>>> NAME Sufi Night`.

**Question 2.** The same bean has this method, also called with `"Sufi Night Live"`. The caller
wraps the call in a `try` and prints any error. (Outside any transaction, `entityManager.flush()`
throws `TransactionRequiredException`.)

```java
@Transactional(readOnly = true)
public void renameReadOnlyThenFlush(String newName) {
    EventEntity event = events.findById(SUFI).orElseThrow();
    event.setName(newName);
    entityManager.flush();
    System.out.println(">>>    flush() returned");
}
```

- (a) Does `flush()` throw an error here, or does `>>>    flush() returned` print?
- (b) Is there an `UPDATE`, either at the `flush()` or at the commit?
- (c) Say why, using what `readOnly = true` does to the entity.

**Answer.** It was run for real. The log (the SQL shortened):

```text
Creating new transaction with name [com.zenith.data.RoCheckService.renameReadOnlyThenFlush]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1680249794<open>)] for JPA transaction
Setting JDBC Connection [HikariProxyConnection@1674572833 wrapping org.postgresql.jdbc.PgConnection@76d72b5b] read-only
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@27e42fe8]
Found thread-bound EntityManager [SessionImpl(1680249794<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    select ...
... binding parameter (1:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
>>>    flush() returned
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1680249794<open>)]
Resetting read-only flag of JDBC Connection [HikariProxyConnection@1674572833 wrapping org.postgresql.jdbc.PgConnection@76d72b5b]
Closing JPA EntityManager [SessionImpl(1680249794<open>)] after transaction
```

- (a) **`>>>    flush() returned` printed, with no error.** Here a transaction is open (a
  read-only one), so `flush()` is allowed.
- (b) **No `UPDATE` at all:** nothing between the `SELECT` and `>>>    flush() returned`, and
  nothing after `Committing JPA transaction`.
- (c) The entity was loaded as read-only: Hibernate kept no snapshot for it, and skips it when it
  flushes. So the flush, even one you call yourself, had nothing to write. The `MANUAL` flush mode
  only stops the automatic flushes; it doesn't block your own `flush()` call, but that call found
  nothing to send. Reading the name back printed `>>> NAME Sufi Night`.

---

## Lesson 19 — Checked and unchecked exceptions (prepares subtask S9)

### Definition

Ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit.
  With `@Transactional` on a method, Spring begins a transaction before the method runs. It ends
  in one of two ways: a **commit** makes every change permanent; a **rollback** undoes every
  change, as if none of them had happened.
- **Parent class.** In `class B extends A`, `A` is the parent of `B`, and a `B` object is a kind
  of `A`. Every class has exactly one parent, except `Object`, which has none.
- A **stack trace** is the list of method calls that were still running when an error was thrown.
  The newest call is at the top.

New words:

- **Exception.** An object that says "something went wrong, and I can't finish this job". Its
  class name tells you the kind of problem. Its **message**, a short text, gives the details;
  `getMessage()` returns it.
- **Throw.** `throw new VenueClosedException("the venue is closed");` creates an exception and
  stops the method on that line. The lines after it don't run. The exception goes back to the
  caller, then to the caller's caller, and so on.
- **Catch.** `try { ... } catch (VenueClosedException e) { ... }`. If the code inside `try` throws
  that kind of exception, Java jumps to the `catch` block, and the program carries on after it. If
  no method on the way catches the exception, the program stops and prints a stack trace.
- **Compiler.** The part of Java that checks your code and translates it before it runs.
  `java ExceptionKinds.java` compiles the file first, then runs it. If the compiler finds an
  error, nothing runs at all.
- **`throws` clause.** The words `throws TicketsSoldOutException` after a method's parameters: a
  label that says "calling this method may give you this exception".
- **Family line.** A class, its parent, its parent's parent, and so on, up to `Object`. For
  example: `VenueClosedException -> RuntimeException -> Exception -> Throwable -> Object`.
  `Throwable` is the top class of everything that can be thrown.
- **Checked exception.** An exception whose family line has `Exception` but not
  `RuntimeException`. The compiler checks it: any method that can throw it must either catch it or
  name it in a `throws` clause. Otherwise the code doesn't compile.
- **Unchecked exception.** An exception whose family line has `RuntimeException` or `Error`. The
  compiler doesn't check it: no `throws` clause and no `catch` are needed.
- **`Error`.** The branch for serious problems inside Java itself, like running out of memory
  (`OutOfMemoryError`). It is unchecked too. Your own code almost never throws or catches one.
- **`instanceof`.** `x instanceof RuntimeException` is `true` when `RuntimeException` is in the
  family line of `x`'s class.

The two kinds side by side:

| | Checked exception | Unchecked exception |
| --- | --- | --- |
| Its family line has | `Exception`, but not `RuntimeException` | `RuntimeException` or `Error` |
| The compiler demands a `catch` or a `throws` clause | yes | no |
| Examples | `IOException`, `SQLException`, `TicketsSoldOutException` | `IllegalStateException`, `NullPointerException`, `VenueClosedException`, `OutOfMemoryError` |
| Spring's default, when one leaves a `@Transactional` method | commit | roll back |

### Why it is needed

- Subtask S9 depends on it. S9 says: "by default Spring rolls back on unchecked exceptions and
  **commits** on checked ones." You can't test that rule, or predict what your own code will do,
  until you can tell the two kinds apart.
- The kind decides what happens to your data. Picture a `@Transactional` method that writes one
  row and then fails. With an unchecked exception, Spring rolls back, and the row is gone. With a
  checked exception, Spring commits, and the row stays. Same failure, opposite result.
- The compiler's messages make sense once you know the rule. "unreported exception" and "is never
  thrown in body of corresponding try statement" are both about checked exceptions.

### Key points

- An exception is an object. `throw` stops the method and sends the exception up to the callers,
  one by one, until a `catch` for it. If there is none, the program stops with a stack trace.
- The family line decides the kind. The name doesn't: `IllegalStateException` ends in
  "Exception" and is unchecked.
- For a checked exception, the compiler demands a `catch` or a `throws` clause: at the `throw`,
  and at every call of a method whose `throws` clause names it.
- A `throws` clause doesn't solve anything by itself. It passes the duty on to every caller.
- A `catch` or `throws` for a class in the family line also counts: `catch (Exception e)`
  catches a `TicketsSoldOutException`.
- Catching a checked exception that the `try` block can't throw is a compile error. Catching an
  unchecked exception that can't happen is allowed.
- For an unchecked exception, the compiler asks for nothing, but the exception can still stop the
  program.
- Creating an exception object doesn't throw it. Only `throw` does.
- Hibernate and Spring hand your code unchecked exceptions, even when the cause underneath is a
  checked one, such as Postgres's `PSQLException`.
- Spring's default rollback rule is one test in its code:
  `ex instanceof RuntimeException || ex instanceof Error`.

### How it works, step by step

**How to tell the kind of any exception:**

1. Write out its family line.
2. If the line has `RuntimeException`, it is an unchecked exception.
3. If the line has `Error`, it is an unchecked exception too.
4. Otherwise the line has `Exception` and neither of those two, so it is a checked exception.

The top of the tree, with the examples of this lesson:

```text
Throwable
├── Error                    unchecked: OutOfMemoryError
└── Exception                checked:   IOException, TicketsSoldOutException
    └── RuntimeException     unchecked: IllegalStateException, VenueClosedException
```

**What the compiler does with a checked exception:**

1. It finds every line that can throw one: a `throw` statement, or a call to a method whose
   `throws` clause names one.
2. For each such line, it asks two questions. Is the line inside a `try` with a `catch` for this
   exception? Does the method around it have a `throws` clause for this exception? A `catch` or a
   `throws` for a class in its family line counts too.
3. If both answers are no, it stops with "unreported exception …; must be caught or declared to
   be thrown". Nothing runs.
4. It also checks the other way round: a `catch` for a checked exception that the `try` block
   can't throw is an error too.

For an unchecked exception, the compiler does none of these checks. The exception can still
happen while the program runs. If nobody catches it, the program stops with a stack trace.

### Example

File `plain-java/ExceptionKinds.java`, run with `java ExceptionKinds.java`. It needs no Spring and
no database.

```java
import java.io.IOException;

// The two kinds of Java exceptions: checked and unchecked.
// Run with: java ExceptionKinds.java
public class ExceptionKinds {

    // Checked: its family line has Exception, but not RuntimeException.
    static class TicketsSoldOutException extends Exception {
        TicketsSoldOutException(String message) {
            super(message);
        }
    }

    // Unchecked: its family line has RuntimeException.
    static class VenueClosedException extends RuntimeException {
        VenueClosedException(String message) {
            super(message);
        }
    }

    // It can throw a checked exception, so the compiler demands "throws" here.
    static void buyTicket(int ticketsLeft) throws TicketsSoldOutException {
        if (ticketsLeft == 0) {
            throw new TicketsSoldOutException("no tickets left");
        }
        System.out.println("ticket bought");
    }

    // It can throw an unchecked exception. No "throws" is needed.
    static void openDoors(boolean venueOpen) {
        if (!venueOpen) {
            throw new VenueClosedException("the venue is closed");
        }
        System.out.println("doors open");
    }

    // The family line: the class, its parent, the parent's parent, and so on, up to Object.
    static String family(Throwable t) {
        StringBuilder chain = new StringBuilder(t.getClass().getSimpleName());
        for (Class<?> c = t.getClass().getSuperclass(); c != null; c = c.getSuperclass()) {
            chain.append(" -> ").append(c.getSimpleName());
        }
        return chain.toString();
    }

    public static void main(String[] args) {
        try {
            buyTicket(0);
        } catch (TicketsSoldOutException e) {
            System.out.println("caught a checked exception: " + e.getMessage());
        }

        try {
            openDoors(false);
        } catch (VenueClosedException e) {
            System.out.println("caught an unchecked exception: " + e.getMessage());
        }

        Throwable[] samples = {
                new TicketsSoldOutException("x"),
                new VenueClosedException("x"),
                new IOException("x"),
                new IllegalStateException("x"),
                new OutOfMemoryError("x"),
        };
        for (Throwable t : samples) {
            boolean unchecked = t instanceof RuntimeException || t instanceof Error;
            System.out.println((unchecked ? "unchecked: " : "checked:   ") + family(t));
        }
    }
}
```

Real output:

```text
caught a checked exception: no tickets left
caught an unchecked exception: the venue is closed
checked:   TicketsSoldOutException -> Exception -> Throwable -> Object
unchecked: VenueClosedException -> RuntimeException -> Exception -> Throwable -> Object
checked:   IOException -> Exception -> Throwable -> Object
unchecked: IllegalStateException -> RuntimeException -> Exception -> Throwable -> Object
unchecked: OutOfMemoryError -> VirtualMachineError -> Error -> Throwable -> Object
```

### Walkthrough

- **Lines 8 to 12:** `TicketsSoldOutException extends Exception`. Its family line is
  `TicketsSoldOutException -> Exception -> Throwable -> Object`. There is no `RuntimeException` in
  it, so it is a checked exception. `super(message)` hands the message to the parent's
  constructor, and `getMessage()` gives it back later. Both exception classes sit inside
  `ExceptionKinds` as `static class`, so the whole example fits in one file.
- **Lines 15 to 19:** `VenueClosedException extends RuntimeException`. `RuntimeException` is in
  its family line, so it is an unchecked exception.
- **Line 22:** `buyTicket` has the `throws` clause. Line 24 throws a checked exception, and
  `buyTicket` doesn't catch it, so the compiler insists on the clause.
- **Lines 30 to 35:** `openDoors` throws an unchecked exception on line 32, and it has no
  `throws` clause. The compiler accepts that.
- **Lines 38 to 44:** `family` builds the family line. A `StringBuilder` is a text you can keep
  adding to, and `getSimpleName()` is the class name without its package. The loop keeps asking
  for the parent with `getSuperclass()`. `Object` has no parent, so after `Object` the answer is
  `null`, and the loop stops.
- **Lines 47 to 51:** `buyTicket(0)` reaches line 24, which throws. Line 26 never runs, so
  `ticket bought` is never printed. Java jumps to the `catch` on line 49, and line 50 prints
  `caught a checked exception: no tickets left`. This `try` is required: without it, `main`
  doesn't compile (the second common mistake shows that).
- **Lines 53 to 57:** the same with `openDoors(false)`, which prints
  `caught an unchecked exception: the venue is closed`. This `try` is our own choice; the
  compiler doesn't ask for it.
- **Lines 59 to 65:** five exception objects. Creating an exception doesn't throw it.
  `new OutOfMemoryError("x")` is just an object; nothing ran out of memory.
- **Line 67:** the rule "how to tell the kind" as code:
  `t instanceof RuntimeException || t instanceof Error`. `||` means "or".
- **Line 68:** `unchecked ? "unchecked: " : "checked:   "` picks the first text if `unchecked` is
  true, and the second one otherwise.
- **The last five output lines:** Java's own `IOException` (problems reading or writing files or
  the network) is checked, and `IllegalStateException` ("this was called at the wrong time") is
  unchecked, by the same rule as our two classes. `OutOfMemoryError` has a longer family line,
  through `VirtualMachineError` and `Error`, and it is unchecked.

### Important terms

| Term | Meaning |
| --- | --- |
| **Exception** | An object that says "something went wrong, and I can't finish this job". |
| **`throw`** | Creates the exception and stops the method on that line. The exception goes up to the callers. |
| **`try` / `catch`** | Catches an exception of the named class, or of a class that has it in its family line. The program carries on after the `catch` block. |
| **Compiler** | Checks and translates the code before it runs. If it finds an error, nothing runs. |
| **`throws` clause** | `throws X` after a method's parameters: "calling this method may give you `X`". |
| **Family line** | A class, its parent, its parent's parent, and so on, up to `Object`. |
| **Checked exception** | Its family line has `Exception` but not `RuntimeException`. The compiler demands a `catch` or a `throws` clause. |
| **Unchecked exception** | Its family line has `RuntimeException` or `Error`. The compiler demands nothing. |
| **`Error`** | The branch for serious problems inside Java itself, such as `OutOfMemoryError`. Unchecked. |
| **`Throwable`** | The top class of everything that can be thrown. |
| **`instanceof`** | `x instanceof C` is `true` when `C` is in the family line of `x`'s class. |
| **`getSuperclass()`** | Gives a class's parent; for `Object`, it gives `null`. |
| **`Outer$Inner`** | How Java writes the name of a class that sits inside another class, as in `ExceptionKinds$VenueClosedException`. |
| **`javap -c`** | A JDK tool that prints what is inside a compiled class, including the steps of each method. |

### Where it is used

- **The database libraries of the scratch app hand you unchecked exceptions.** These are the
  family lines of five real classes, printed with the scratch app's own jars
  (`postgresql-42.7.11`, `hibernate-core-6.6.53.Final`, `jakarta.persistence-api-3.1.0`,
  `spring-tx-6.2.19` and `spring-core-6.2.19`):

  ```text
  checked:   PSQLException -> SQLException -> Exception -> Throwable -> Object
  unchecked: GenericJDBCException -> JDBCException -> HibernateException -> PersistenceException -> RuntimeException -> Exception -> Throwable -> Object
  unchecked: TransactionRequiredException -> PersistenceException -> RuntimeException -> Exception -> Throwable -> Object
  unchecked: DataIntegrityViolationException -> NonTransientDataAccessException -> DataAccessException -> NestedRuntimeException -> RuntimeException -> Exception -> Throwable -> Object
  unchecked: NoSuchElementException -> RuntimeException -> Exception -> Throwable -> Object
  ```

  - `PSQLException` comes from the Postgres driver, and it is a checked exception. Hibernate
    wraps it inside `GenericJDBCException`, an unchecked one, before your code sees it. In a
    read-only transaction, a native `UPDATE` gave these four real log lines:

    ```text
    ERROR: cannot execute UPDATE in a read-only transaction
    Initiating transaction rollback
    >>>    org.hibernate.exception.GenericJDBCException: JDBC exception executing SQL [update event set name = 'Sufi Night Live' where slug = 'sufi-night'] [ERROR: cannot execute UPDATE in a read-only transaction] [n/a]
    >>>    caused by org.postgresql.util.PSQLException: ERROR: cannot execute UPDATE in a read-only transaction
    ```

    An unchecked exception left a `@Transactional` method, and Spring rolled back.
  - `TransactionRequiredException` is what `entityManager.flush()` throws when there is no
    transaction.
  - `DataIntegrityViolationException` is Spring's exception for a write that breaks a database
    rule, such as a duplicate value in a unique column.
  - `NoSuchElementException` is what `orElseThrow()` throws when `findById` found nothing. A quick
    test printed `java.util.NoSuchElementException: No value present`. Because it is unchecked,
    the `EventService` methods that call `events.findById(eventId).orElseThrow()` need no
    `throws` clause.

  The libraries do this on purpose: you don't need a `throws` clause on every method, and a
  database failure inside a `@Transactional` method rolls back under Spring's default rule.
- **Your own exceptions.** Many Spring teams make their own exceptions unchecked, by extending
  `RuntimeException`, so that they roll back by default too.
- **Plain Java** uses checked exceptions for problems outside the program that a caller can plan
  for: a missing file (`IOException`), or a database error over plain JDBC (`SQLException`).
- Subtask S9: the rollback rule depends on the kind.

### Edge cases

- **A class in the family line counts.** A temporary program declared `throws Exception`, threw a
  `TicketsSoldOutException`, and caught `Exception`:

  ```java
  // "throws Exception" names a class in the family line of TicketsSoldOutException.
  static void buyTicket(int ticketsLeft) throws Exception {
      if (ticketsLeft == 0) {
          throw new TicketsSoldOutException("no tickets left");
      }
      System.out.println("ticket bought");
  }

  public static void main(String[] args) {
      try {
          buyTicket(0);
      } catch (Exception e) {
          System.out.println("caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
  }
  ```

  It compiled and printed `caught TicketsSoldOutException: no tickets left`. The object keeps its
  own class; the `catch` only needs a class from its family line.
- **Catching the impossible: allowed for an unchecked exception, an error for a checked one.** A
  temporary file, `CatchNeverThrown.java`:

  ```java
  import java.io.IOException;

  public class CatchNeverThrown {

      public static void main(String[] args) {
          try {
              System.out.println("nothing risky here");
          } catch (IllegalStateException e) {
              System.out.println("never happens");
          }

          try {
              System.out.println("nothing risky here either");
          } catch (IOException e) {
              System.out.println("never happens");
          }
      }
  }
  ```

  The run stopped with one error, for line 14 only. The `catch (IllegalStateException e)` on line
  8 was fine:

  ```text
  CatchNeverThrown.java:14: error: exception IOException is never thrown in body of corresponding try statement
          } catch (IOException e) {
            ^
  1 error
  error: compilation failed
  ```

- **Creating is not throwing.** The `samples` array in the example holds five exception objects,
  and none of them is thrown. Only a `throw` statement throws.
- **`Error` is unchecked, and Spring rolls back on it too.** Its family line has no `Exception`
  at all: `OutOfMemoryError -> VirtualMachineError -> Error -> Throwable -> Object`. Spring's
  rollback test checks for `Error` as well as `RuntimeException`.
- **Single-file mode writes no `.class` files.** `java ExceptionKinds.java` compiles in memory;
  afterwards the folder still held only `ExceptionKinds.java` and `ProxyDemo.java`. In a test
  copy where `javac ExceptionKinds.java` had been run first (it wrote
  `ExceptionKinds.class`, `ExceptionKinds$TicketsSoldOutException.class` and
  `ExceptionKinds$VenueClosedException.class`), `java ExceptionKinds.java` still printed the
  same seven lines.

### Common mistakes — with the real errors

1. **Forgetting the `throws` clause.** On line 22 of the example,
   ` throws TicketsSoldOutException` was deleted. The run stopped:

   ```text
   ExceptionKinds.java:24: error: unreported exception TicketsSoldOutException; must be caught or declared to be thrown
               throw new TicketsSoldOutException("no tickets left");
               ^
   ExceptionKinds.java:49: error: exception TicketsSoldOutException is never thrown in body of corresponding try statement
           } catch (TicketsSoldOutException e) {
             ^
   2 errors
   error: compilation failed
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `ExceptionKinds.java:24` | The file and the line: the `throw` inside `buyTicket`. |
   | `unreported exception TicketsSoldOutException` | This line throws a checked exception. Nothing catches it, and no `throws` clause names it. |
   | `must be caught or declared to be thrown` | The two ways out: catch it with `try` / `catch`, or declare it with a `throws` clause. |
   | `ExceptionKinds.java:49` | The `catch` in `main`. |
   | `is never thrown in body of corresponding try statement` | `buyTicket` no longer says it throws this, so the compiler believes the `try` block can't produce it. Catching a checked exception that can't happen is an error too. |
   | `2 errors`, `error: compilation failed` | The compiler stopped. Not one line of the program ran. |

2. **Calling the method with no `try`.** With the `throws` clause back, one line was added after
   line 57, outside any `try`:

   ```java
           try {
               openDoors(false);
           } catch (VenueClosedException e) {
               System.out.println("caught an unchecked exception: " + e.getMessage());
           }
           buyTicket(0);
   ```

   ```text
   ExceptionKinds.java:58: error: unreported exception TicketsSoldOutException; must be caught or declared to be thrown
           buyTicket(0);
                    ^
   1 error
   error: compilation failed
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `ExceptionKinds.java:58` | The new line in `main`. |
   | `unreported exception TicketsSoldOutException` | `buyTicket`'s `throws` clause says it may throw this checked exception, and `main` neither catches it nor names it in a `throws` clause. |

   A `throws` clause doesn't make the problem go away. It passes the duty on to every caller.

3. **Thinking "unchecked" means "safe".** The same line 58 was changed to `openDoors(false);`.
   This time it compiled, and then:

   ```text
   caught a checked exception: no tickets left
   caught an unchecked exception: the venue is closed
   Exception in thread "main" ExceptionKinds$VenueClosedException: the venue is closed
   	at ExceptionKinds.openDoors(ExceptionKinds.java:32)
   	at ExceptionKinds.main(ExceptionKinds.java:58)
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Exception in thread "main"` | An exception reached the top, and nobody caught it, so the program stopped. |
   | `ExceptionKinds$VenueClosedException` | The class `VenueClosedException`, which sits inside `ExceptionKinds`. The `$` joins the two names. |
   | `the venue is closed` | The message given on line 32. |
   | `at ExceptionKinds.openDoors(ExceptionKinds.java:32)` | The newest call: where it was thrown. |
   | `at ExceptionKinds.main(ExceptionKinds.java:58)` | The call that led there: the new line. |

   The five family lines never printed, and the exit code was 1. "Unchecked" only means the
   compiler doesn't make you deal with the exception. It can still stop your program.

4. **Judging the kind by the name.** Every class in the example ends in "Exception" or "Error",
   checked or not: `IOException` and `TicketsSoldOutException` are checked, while
   `IllegalStateException` and `VenueClosedException` are unchecked. No message warns you. Only
   the family line decides.

### Link to the task

S9 says: "Learn the rollback rule that catches people out: by default Spring rolls back on
unchecked exceptions and **commits** on checked ones. Test it. Throw a `RuntimeException` halfway
through a two-write method and confirm both writes vanished. Then throw a checked exception and
watch the first write survive."

- "Unchecked exceptions": a family line with `RuntimeException` or `Error`, like
  `VenueClosedException`. The `RuntimeException` that S9 throws is one itself.
- "Checked ones": a family line with `Exception` but no `RuntimeException`, like
  `TicketsSoldOutException`.
- The rule is one test in Spring's own code. `javap -c` on the class
  `org.springframework.transaction.interceptor.DefaultTransactionAttribute`, from
  `spring-tx-6.2.19.jar`, shows its method `rollbackOn(Throwable)`:

  ```text
    public boolean rollbackOn(java.lang.Throwable);
      Code:
         0: aload_1
         1: instanceof    #35                 // class java/lang/RuntimeException
         4: ifne          14
         7: aload_1
         8: instanceof    #37                 // class java/lang/Error
        11: ifeq          18
        14: iconst_1
        15: goto          19
        18: iconst_0
        19: ireturn
  ```

  | Part | Plain meaning |
  | --- | --- |
  | The numbers on the left | Positions in the method's code. |
  | `aload_1` | Take the exception that was passed in. |
  | `instanceof ... RuntimeException`, then `ifne 14` | If it is a `RuntimeException`, jump to position 14. |
  | `instanceof ... Error`, then `ifeq 18` | If it isn't an `Error` either, jump to position 18. |
  | `14: iconst_1` | Answer `true`: roll back. |
  | `18: iconst_0` | Answer `false`: don't roll back, so commit. |

  In Java, that is `return ex instanceof RuntimeException || ex instanceof Error;`, the same test
  as line 67 of the example.
- **S9 is not done yet.** It still needs a table to write to, and two tests, one with each kind of
  exception.

### Do it yourself (second laptop)

**Starting point.** Java 21 is installed, and the folder
`~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/plain-java`
exists, with `ProxyDemo.java` in it from the proxy lesson. Steps 1 to 4 need no Docker, no
Postgres and no Maven. Step 5 reads a jar that Maven downloaded for the scratch app in the
earlier lessons.

**Step 1 — go to the folder and check Java.**

```bash
mkdir -p ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/plain-java
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/plain-java
java -version
```

The first line should start with `openjdk version "21`, for example
`openjdk version "21.0.11" 2026-04-21`.

**Step 2 — create the example and run it.** Create `ExceptionKinds.java` with the 71 lines from
the example. Then run:

```bash
java ExceptionKinds.java
```

Expected: the seven lines from the example, ending with
`unchecked: OutOfMemoryError -> VirtualMachineError -> Error -> Throwable -> Object`.

**Step 3 — break it: remove the `throws` clause.** On line 22, delete
` throws TicketsSoldOutException`, so the line reads `static void buyTicket(int ticketsLeft) {`.
Run `java ExceptionKinds.java` again. Expected: the two errors from the first common mistake, at
lines 24 and 49, ending with `error: compilation failed`. Put the clause back and run again: the
seven lines are back.

**Step 4 — the same line, with each kind of exception.**

1. After line 57, the `}` that closes the second `catch`, add a new line: `buyTicket(0);`.
2. Run it. Expected: the compile error from the second common mistake, at line 58.
3. Change that line to `openDoors(false);`, and run again. Expected: two lines, then the stack
   trace from the third common mistake.
4. Delete the line, and run once more. Expected: the seven lines.

**Step 5 — read Spring's rule yourself.**

```bash
javap -c -cp ~/.m2/repository/org/springframework/spring-tx/6.2.19/spring-tx-6.2.19.jar org.springframework.transaction.interceptor.DefaultTransactionAttribute | grep -A 12 'public boolean rollbackOn'
```

Expected: the 12 lines from the link to the task, starting with
`public boolean rollbackOn(java.lang.Throwable);`, with `class java/lang/RuntimeException` and
`class java/lang/Error` in them.

If it goes wrong:

- `java -version` doesn't start with `openjdk version "21"`, or says `command not found`: redo
  the Java install in the setup steps of the `EventEntity` lesson.
- Step 2 already shows the two errors from the first common mistake: the `throws` clause on line
  22 is missing.
- Your errors or stack trace show other line numbers: your file isn't the same as the example.
  Compare line 22 and line 57 first.
- Step 4 still ends with an error or a stack trace after you deleted the line: the extra line is
  still in the file.
- Step 5 prints
  `Error: class not found: org.springframework.transaction.interceptor.DefaultTransactionAttribute`:
  the jar isn't at that path. Run `ls ~/.m2/repository/org/springframework/spring-tx/`, and use
  the version folder you see there, in both places in the command.

**Done when:**

- step 2 printed the seven lines;
- removing the `throws` clause gave the two errors, and putting it back gave the seven lines
  again;
- the same new line failed to compile with `buyTicket(0);`, but compiled and then crashed with
  `openDoors(false);`;
- you have seen `RuntimeException` and `Error` inside Spring's `rollbackOn`;
- the file is back to 71 lines and prints the seven lines.

### Summary for revision

- An exception is an object. `throw` stops the method, and the exception goes up the callers
  until a `catch`. If there is none, the program stops with a stack trace.
- Checked exception: the family line has `Exception` but not `RuntimeException`. The compiler
  demands a `catch` or a `throws` clause.
- Unchecked exception: the family line has `RuntimeException` or `Error`. The compiler demands
  nothing, but it can still stop the program.
- The name doesn't tell you the kind. The family line does.
- The two compiler messages: "unreported exception X; must be caught or declared to be thrown",
  and "exception X is never thrown in body of corresponding try statement".
- A `throws` clause passes the duty on to every caller.
- Hibernate and Spring hand you unchecked exceptions.
- Spring's default: roll back on an unchecked exception, commit on a checked one. In its code:
  `ex instanceof RuntimeException || ex instanceof Error`.

### Practice questions, with answers

**Question 1.** Here are three exception classes of your own:

```java
static class PaymentFailedException extends Exception {
}

static class RefundFailedException extends PaymentFailedException {
}

static class SeatTakenException extends IllegalStateException {
}
```

Two more come with Java: `java.sql.SQLException`, whose parent is `Exception`, and
`NullPointerException`, whose parent is `RuntimeException`. The parent of `IllegalStateException`
is `RuntimeException`.

- (a) Write the family line of each of the five classes, and say whether it is a checked or an
  unchecked exception.
- (b) Each one is thrown from its own method, with no `throws` clause and no `try`. Here is the
  method for `PaymentFailedException`; the other four look the same:

  ```java
  static void p() {
      throw new PaymentFailedException();
  }
  ```

  Which of the five methods compile?

**Answer.** Both parts were run for real.

- (a) A test program printed the five family lines, with the same test as line 67 of the
  example:

  ```text
  checked:   PaymentFailedException -> Exception -> Throwable -> Object
  checked:   RefundFailedException -> PaymentFailedException -> Exception -> Throwable -> Object
  unchecked: SeatTakenException -> IllegalStateException -> RuntimeException -> Exception -> Throwable -> Object
  checked:   SQLException -> Exception -> Throwable -> Object
  unchecked: NullPointerException -> RuntimeException -> Exception -> Throwable -> Object
  ```

  The two traps are the classes that don't extend `Exception` or `RuntimeException` directly.
  `RefundFailedException` is checked: its family line passes through `PaymentFailedException` to
  `Exception`, with no `RuntimeException` anywhere. `SeatTakenException` is unchecked: its family
  line passes through `IllegalStateException` to `RuntimeException`.
- (b) **Only the methods for `SeatTakenException` and `NullPointerException` compile.** A test
  file had the five methods `p`, `r`, `s`, `q` and `n`, for `PaymentFailedException`,
  `RefundFailedException`, `SeatTakenException`, `SQLException` and `NullPointerException`, in
  that order. The compiler said:

  ```text
  Q1Compile.java:15: error: unreported exception PaymentFailedException; must be caught or declared to be thrown
          throw new PaymentFailedException();
          ^
  Q1Compile.java:19: error: unreported exception RefundFailedException; must be caught or declared to be thrown
          throw new RefundFailedException();
          ^
  Q1Compile.java:27: error: unreported exception SQLException; must be caught or declared to be thrown
          throw new SQLException("x");
          ^
  3 errors
  error: compilation failed
  ```

  The three errors point at the `throw` lines of `p`, `r` and `q`: the three checked exceptions.
  There is no error for `s` and `n`, because their exceptions are unchecked. The compiler agrees
  with the family lines.

**Question 2.** The four methods below are in one class, which starts with
`import java.io.IOException;`. The parent of `IOException` is `Exception`, and the parent of
`IllegalArgumentException` is `RuntimeException`.

```java
static void a() {
    throw new IllegalArgumentException("bad input");
}

static void b() {
    throw new IOException("disk full");
}

static void c() throws IOException {
    throw new IOException("disk full");
}

static void d() {
    try {
        throw new IOException("disk full");
    } catch (IOException e) {
        System.out.println("handled: " + e.getMessage());
    }
}
```

- (a) Which methods compile? For any that doesn't, what does the compiler say, and what are the
  two ways to fix it?
- (b) You delete `b`, and `main` calls only `d()`. What does the program print?

**Answer.** Both parts were run for real, with a `main` that calls only `d()`.

- (a) **`a`, `c` and `d` compile; `b` doesn't.** The compiler said:

  ```text
  Q2Check.java:10: error: unreported exception IOException; must be caught or declared to be thrown
          throw new IOException("disk full");
          ^
  1 error
  error: compilation failed
  ```

  Line 10 is the `throw` in `b`. `a` throws an unchecked exception, so it needs nothing. `c`
  declares its checked exception with a `throws` clause, and `d` catches it. Those are exactly the
  two fixes for `b`: add `throws IOException`, which makes it the same as `c`, or catch the
  exception inside it with `try` / `catch`, as `d` does. With the `throws` fix, every caller of `b`
  must then catch it or declare it in turn.
- (b) **`handled: disk full`.** With `b` deleted, the file compiled, and the run printed exactly
  that one line. `d` throws and catches its own exception, so `main` needs no `try`. `a` and `c`
  are never called.

---

## Lesson 20 — A table to write to: `ticket_order` (prepares subtasks S9 and S10)

### Definition

Ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** makes every change permanent; a **rollback** undoes every change. With
  `@Transactional` on a method, Spring begins a transaction before the method runs and commits it
  after the method returns.
- An **entity** is a Java class that stands for a table: one object is one row. `@Entity` marks
  it, `@Table(name = "...")` names the table, and `@Id` marks the primary key field.
- A **repository** is an interface you declare and never implement, such as
  `EventRepository extends JpaRepository<EventEntity, UUID>`. Spring Data builds the object behind
  it at startup. `save()` and `count()` come with it.
- A **migration** is a numbered SQL file in `src/main/resources/db/migration`, such as
  `V3__add_event_slug.sql`. At startup, Flyway runs every file it hasn't run yet, once, in number
  order, and records it in its table `flyway_schema_history`.
- **`ddl-auto: validate`** is set in `application.yml`. At startup, Hibernate checks that every
  table and column the entities need is really there. If one is missing, the app refuses to start.
- The **persistence context** is Hibernate's memory of the entities in the current transaction.
- An **unchecked exception** is one whose family line (its class, its parent, its parent's parent,
  and so on) has `RuntimeException`. The compiler doesn't make you catch it.

New words:

- **Generated id.** An id that the database picks, not your code.
- **Identity column.** A column that numbers new rows by itself: 1, 2, 3, and so on. In Postgres:
  `id bigint generated always as identity`. `always` means the database always picks the number,
  and refuses a number you try to give it.
- **`bigint` and `Long`.** `bigint` is Postgres's type for big whole numbers; `Long` is the
  matching Java type. `Long`, with a capital L, can be `null`; the small `long` can't. Before the
  row is inserted, the id is `null`: "no id yet".
- **`@GeneratedValue(strategy = GenerationType.IDENTITY)`.** Tells Hibernate "the database picks
  this id, with an identity column". Hibernate leaves `id` out of the `INSERT`, and reads the new
  id back afterwards.
- **Postgres driver.** The library (`postgresql-42.7.11.jar`) that carries SQL from Java to
  Postgres and brings the answers back.
- **`RETURNING *`.** An ending for an `INSERT` that asks Postgres to send the new row back. The
  Postgres driver adds it for Hibernate, which is how Hibernate learns the id.
- **Sequence.** A separate database object that hands out numbers when asked. This lesson doesn't
  use one; one of the common mistakes makes Hibernate look for one.
- **`truncate ... restart identity`.** SQL that deletes every row of a table at once, and sets its
  numbering back to 1.

What happens to a new order, from a real run:

| | Before `save()` | After `save()` | After the commit |
| --- | --- | --- | --- |
| `order.getId()` | `null` | `1` | `1` |
| The row in Postgres | not there | inserted, inside the open transaction | permanent |

### Why it is needed

- Subtask S9 needs real writes to undo. It says: "Throw a `RuntimeException` halfway through a
  two-write method and confirm both writes vanished." So far the only write was renaming an event.
  A new row is the clearest write there is: afterwards it is either in the table, or it isn't.
- Subtask S10 is about an order: "Put two methods in one service: a public `createOrder()` with no
  annotation that calls `this.persistOrder()`, where `persistOrder` is `@Transactional`. Throw
  inside `persistOrder` and watch the write **stay committed**." It needs this same table.
- With an identity column, the `INSERT` reaches the database at `save()`, while the transaction is
  still open. So the next lessons can show the `INSERT` in the log, and then show the rollback
  removing the row.

### Key points

- A new table comes from a new migration: `V4__create_ticket_order.sql`. Flyway runs it once, at
  startup, before Hibernate's `validate` check.
- `id bigint generated always as identity` makes Postgres number the rows itself, and refuse an id
  you try to give it.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` on a `Long id` tells Hibernate the same
  thing. The id is `null` until the insert.
- `save()` on a new entity (its id is `null`) asks Hibernate to insert it. With an identity id,
  Hibernate sends the `INSERT` at once, not at the commit, because it needs the id.
- The `INSERT` has no `id` column. The Postgres driver adds `RETURNING *`, Postgres sends the new
  row back, and Hibernate copies the id into the same Java object.
- The row is in the database before the commit, inside the open transaction. The commit makes it
  permanent.
- Plain `@GeneratedValue` (no strategy) makes Hibernate look for a sequence, `ticket_order_seq`.
  No `@GeneratedValue` at all makes `save()` fail, because the id was never set.
- A migration file needs the exact name shape `V4__name.sql`. With one underscore, Flyway skips it
  without a word.

### How it works, step by step

**At startup:**

1. Flyway compares the migration files with `flyway_schema_history`. It finds
   `V4__create_ticket_order.sql`, which it hasn't run, runs it once, and records it.
2. Then Hibernate's `validate` check runs. `ticket_order` and its columns now exist, so the app
   starts.

**When `placeOrder` saves a new order:**

1. **Begin.** Spring begins the transaction.
2. **A new object.** `new OrderEntity(...)` has no id yet: `getId()` returns `null`.
3. **`save()`.** Spring Data sees that the id is `null`, so the entity is new, and asks Hibernate
   to insert it.
4. **The `INSERT` goes now, not at the commit.** The persistence context keeps each entity under
   its id. A new entity has no id, and only the database can give it one. So Hibernate sends the
   `INSERT` at once, without an `id` column; Postgres fills that in.
5. **The id comes back.** The driver added `RETURNING *`, so Postgres sends the new row back.
   Hibernate copies its id into the same Java object, and `getId()` now returns `1`.
6. **Commit.** The row is already in the database, inside the open transaction. The commit makes
   it permanent.

Compare this with dirty checking: a change to a loaded entity waits for the flush at the commit.
An `INSERT` for an identity id doesn't wait.

### Example

Five new files. The migration,
`scratch-app/src/main/resources/db/migration/V4__create_ticket_order.sql`:

```sql
-- V4__create_ticket_order.sql
-- One row per ticket order, so the rollback lessons have real rows to write and to undo.
-- "generated always as identity": the database numbers the rows 1, 2, 3, ... by itself,
-- and refuses any id you try to give it.

create table ticket_order (
    id              bigint generated always as identity primary key,
    event_id        uuid not null,
    customer_email  text not null,
    quantity        integer not null,
    constraint ticket_order_event_fk foreign key (event_id) references event (id),
    constraint ticket_order_quantity_positive check (quantity > 0)
);

create index idx_ticket_order_event_id on ticket_order (event_id);
```

The entity, `scratch-app/src/main/java/com/zenith/data/OrderEntity.java` (36 lines):

```java
package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// Lesson 20: one ticket order = one row of the "ticket_order" table.
@Entity
@Table(name = "ticket_order")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // the database picks the id when the row is inserted
    private Long id;                  // column: id. Long, not long, so it can be null before the insert

    private UUID eventId;             // column: event_id
    private String customerEmail;     // column: customer_email
    private int quantity;             // column: quantity

    protected OrderEntity() {}        // Hibernate builds the object empty, then fills the fields

    public OrderEntity(UUID eventId, String customerEmail, int quantity) {
        this.eventId = eventId;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getCustomerEmail() { return customerEmail; }
    public int getQuantity() { return quantity; }
}
```

The repository, `OrderRepository.java` (8 lines):

```java
package com.zenith.data;

import org.springframework.data.jpa.repository.JpaRepository;

// Lesson 20: the repository for ticket orders.
// <OrderEntity, Long> = what it stores, and the type of that entity's @Id field.
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
```

The service, `OrderService.java` (27 lines). The next lessons add their methods here:

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 20: the service for ticket orders. The rollback lessons add to it.
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 20: one write, inside one transaction. The database picks the id.
    @Transactional
    public long placeOrder(UUID eventId, String customerEmail, int quantity) {
        OrderEntity order = new OrderEntity(eventId, customerEmail, quantity);
        System.out.println(">>>    id before save: " + order.getId());
        orders.save(order);
        System.out.println(">>>    id after save: " + order.getId());
        return order.getId();
    }
}
```

The demo, `OrderDemo.java` (35 lines):

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 20: save one ticket order, and see the id the database picks.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=order
@Component
@Profile("order")
class OrderDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    OrderDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. orders in the table: " + orders.count());

        System.out.println(">>> 2. calling placeOrder");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);

        System.out.println(">>> 3. orders in the table: " + orders.count());
    }
}
```

The command:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=order -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

Real output. The two short `count` transactions of steps 1 and 3 are left out; each is one
`select count(*) from ticket_order oe1_0` in its own read-only transaction:

```text
... Successfully validated 4 migrations (execution time 00:00.026s)
... Current version of schema "public": 3
... Migrating schema "public" to version "4 - create ticket order"
... Successfully applied 1 migration to schema "public", now at version v4 (execution time 00:00.016s)
...
>>> 1. orders in the table: 0
>>> 2. calling placeOrder
Creating new transaction with name [com.zenith.data.OrderService.placeOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(43472661<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@47ccbbd5]
>>>    id before save: null
Found thread-bound EntityManager [SessionImpl(43472661<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [meera@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [2]
>>>    id after save: 1
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(43472661<open>)]
Closing JPA EntityManager [SessionImpl(43472661<open>)] after transaction
>>>    placeOrder returned id 1
>>> 3. orders in the table: 1
```

Afterwards, `select * from ticket_order;` in psql:

```text
 id |               event_id               |  customer_email   | quantity 
----+--------------------------------------+-------------------+----------
  1 | aaaaaaaa-0000-0000-0000-000000000005 | meera@example.com |        2
(1 row)
```

### Walkthrough

**The migration.**

- Line 7: the identity column. Postgres numbers the rows itself.
- Lines 8 to 10: the event, the customer, and how many tickets. Each one is `not null`: every
  order needs all three.
- Line 11: a foreign key. `event_id` must be the id of a real row in `event`.
- Line 12: a check. `quantity` must be more than 0.
- Line 15: an index on `event_id`, like the older tables have on their foreign keys, so "all orders
  for this event" is fast.

psql's `\d ticket_order` shows the result (real output):

```text
                          Table "public.ticket_order"
     Column     |  Type   | Collation | Nullable |           Default            
----------------+---------+-----------+----------+------------------------------
 id             | bigint  |           | not null | generated always as identity
 event_id       | uuid    |           | not null | 
 customer_email | text    |           | not null | 
 quantity       | integer |           | not null | 
Indexes:
    "ticket_order_pkey" PRIMARY KEY, btree (id)
    "idx_ticket_order_event_id" btree (event_id)
Check constraints:
    "ticket_order_quantity_positive" CHECK (quantity > 0)
Foreign-key constraints:
    "ticket_order_event_fk" FOREIGN KEY (event_id) REFERENCES event(id)
```

**The entity.**

- Line 13: the table name. Without it, Hibernate would look for a table named after the class,
  `order_entity`.
- Line 17: the database picks the id, with an identity column.
- Line 18: a `Long`, which is `null` until the insert.
- Lines 20 to 22: camelCase becomes snake_case: `eventId` is `event_id`, and `customerEmail` is
  `customer_email`.
- Line 24: the empty constructor Hibernate needs.
- Lines 26 to 30: the constructor your code uses. It sets everything except the id.

**The repository.** `JpaRepository<OrderEntity, Long>`: `Long`, because the `@Id` field is a
`Long`.

**The service, `placeOrder` (lines 19 to 26).**

- `@Transactional`: one transaction around the whole method,
  `Creating new transaction with name [com.zenith.data.OrderService.placeOrder]`.
- Line 21 makes the new object, and line 22 prints `>>>    id before save: null`.
- Line 23 calls `orders.save(order)`. `save()` has its own `@Transactional`, so it joins the open
  transaction: `Participating in existing transaction`. The `insert` comes right away. Its column
  list has no `id`, and three values are bound.
- Line 24 prints `>>>    id after save: 1`. `save()` put the id into the same `order` object, so
  the code doesn't need what `save()` returns.
- Only then come `Initiating transaction commit` and `Committing JPA transaction`.

**The demo.** Step 1 counted 0, step 2 placed the order, and step 3 counted 1.

**Where the id came from.** One more run, with the Postgres driver's own log turned on
(`--logging.level.org.postgresql.core.v3.QueryExecutorImpl=TRACE`), showed between the `insert`
and `>>>    id after save: 1` what really went to Postgres and back (shortened):

```text
 FE=> SimpleQuery(query="BEGIN")
 FE=> Parse(stmt=null,query="insert into ticket_order (customer_email,event_id,quantity) values ($1,$2,$3)
RETURNING *",oids={1043,2950,23})
 FE=> Bind(stmt=null,portal=null,$1=<('meera@example.com')>,type=VARCHAR,$2=<('aaaaaaaa-0000-0000-0000-000000000005'::uuid)>,type=UUID,$3=<('2'::int4)>,type=INT4)
 <=BE RowDescription(4)
        Field(id,INT8,8,T)
        Field(event_id,UUID,16,T)
        Field(customer_email,TEXT,65535,T)
        Field(quantity,INT4,4,T)
 <=BE DataRow(len=55)
 <=BE CommandStatus(INSERT 0 1)
```

| Part | Plain meaning |
| --- | --- |
| `FE=>` | Sent from the app to Postgres. |
| `<=BE` | Sent from Postgres back to the app. |
| `SimpleQuery(query="BEGIN")` | The real start of the transaction inside Postgres. The driver sends it together with the first statement. |
| `$1,$2,$3` | Postgres's way of writing the three `?`. |
| `RETURNING *` | Added by the driver: "send me the new row". |
| `Bind(... $1=<('meera@example.com')> ...)` | The three values. |
| `RowDescription(4)` and the `Field` lines | Postgres describes the row it sends back: four columns. The first is `id`, of type `INT8`, which is `bigint`. |
| `DataRow(len=55)` | The row itself, with the new id inside. |
| `CommandStatus(INSERT 0 1)` | One row inserted. |

### Important terms

| Term | Meaning |
| --- | --- |
| **Generated id** | An id that the database picks, not your code. |
| **Identity column** | A column that numbers new rows by itself. `generated always` also refuses an id you give it. |
| **`bigint`** | Postgres's type for big whole numbers. It matches Java's `Long`. |
| **`@GeneratedValue(strategy = GenerationType.IDENTITY)`** | Tells Hibernate the database picks the id with an identity column. The `INSERT` leaves `id` out, and is sent at once. |
| **Postgres driver** | The library that carries SQL from Java to Postgres. Its own log: `--logging.level.org.postgresql.core.v3.QueryExecutorImpl=TRACE`. |
| **`RETURNING *`** | An ending for an `INSERT`: "send me the new row". The driver adds it. |
| **Sequence** | A separate database object that hands out numbers. Plain `@GeneratedValue` makes Hibernate expect one. |
| **`persist()`** | The insert call that `save()` makes for a new entity. |
| **`truncate ... restart identity`** | Deletes every row of a table at once, and sets its numbering back to 1. |
| **`\d table`** | psql's command to describe a table: its columns, types, defaults, indexes and constraints. |

### Where it is used

- Most tables in real apps get their ids this way: orders, payments, tickets, log rows. The
  database hands out the numbers, so two copies of the app running at the same time can never pick
  the same id.
- UUID ids, like the ones in `event`, are the other common choice. The app can make a UUID itself,
  before the insert. The seed data gave every event a fixed UUID.
- In this task: the rollback tests of S9 (a two-write method), and the `createOrder()` /
  `persistOrder()` trap of S10 and its fix in S11.

### Edge cases

- **`save()` fills in the object you passed.** For a new entity, `save()` calls `persist()` (the
  error in the fourth common mistake names it), and Hibernate puts the new id into that same
  object. That is why `order.getId()` on line 24 of `OrderService` works without using what
  `save()` returns.
- **`BEGIN` travels with the first statement.** In the driver log, `SimpleQuery(query="BEGIN")`
  comes right before the first `insert`, not when `Creating new transaction ...` is logged.
- **Flyway runs a migration once.** The second run printed
  `Current version of schema "public": 4` and
  `Schema "public" is up to date. No migration necessary.`, and V4 didn't run again.
- **`count()` has its own transaction.** Each `orders.count()` in the demo ran in
  `Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly`,
  outside `placeOrder`'s transaction.
- **`restart identity` resets the numbering.** After
  `truncate ticket_order restart identity;`, the next run of the demo printed
  `>>>    id after save: 1` again.

### Common mistakes — with the real errors

1. **The entity before the table.** The first run, before `V4` existed:

   ```text
   ... Successfully validated 3 migrations (execution time 00:00.018s)
   ... Current version of schema "public": 3
   ... Schema "public" is up to date. No migration necessary.
   ...
   ERROR ... Application run failed
   ...
   Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [ticket_order]
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Successfully validated 3 migrations` | Flyway found only V1 to V3. |
   | `Schema "public" is up to date. No migration necessary.` | Nothing new to run. |
   | `Schema-validation` | Hibernate's startup check, turned on by `ddl-auto: validate`. |
   | `missing table [ticket_order]` | `@Table(name = "ticket_order")` names a table that isn't in the database. |

2. **One underscore in the file name.** The file was named `V4_create_ticket_order.sql`, with a
   single `_`. It did reach `target/classes/db/migration/`, but Flyway skipped it without a word.
   The log was exactly the log of the first mistake: `Successfully validated 3 migrations`,
   `up to date`, `missing table [ticket_order]`. Flyway only reads names shaped like
   `V4__anything.sql`: a capital `V`, the number, two underscores.

3. **`@GeneratedValue` without the strategy.** Line 17 was changed to plain `@GeneratedValue`:

   ```text
   ... Schema "public" is up to date. No migration necessary.
   ...
   Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing sequence [ticket_order_seq]
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `missing sequence` | With no strategy, Hibernate chose a sequence for Postgres: a separate object it asks for the next number before each insert. It found none. |
   | `[ticket_order_seq]` | The name it looked for: the table name plus `_seq`. The table uses an identity column instead, so there is no such sequence. |

4. **No `@GeneratedValue` at all.** Line 17 was deleted. This time the app started (`validate`
   only checks tables and columns), and `save()` failed:

   ```text
   >>>    id before save: null
   Found thread-bound EntityManager [SessionImpl(537705315<open>)] for JPA transaction
   Participating in existing transaction
   Participating transaction failed - marking existing transaction as rollback-only
   Setting JPA transaction on EntityManager [SessionImpl(537705315<open>)] rollback-only
   Initiating transaction rollback
   Rolling back JPA transaction on EntityManager [SessionImpl(537705315<open>)]
   Closing JPA EntityManager [SessionImpl(537705315<open>)] after transaction
   ...
   ERROR ... Application run failed
   org.springframework.orm.jpa.JpaSystemException: Identifier of entity 'com.zenith.data.OrderEntity' must be manually assigned before calling 'persist()'
   Caused by: org.hibernate.id.IdentifierGenerationException: Identifier of entity 'com.zenith.data.OrderEntity' must be manually assigned before calling 'persist()'
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `must be manually assigned` | Without `@GeneratedValue`, Hibernate expects your code to set the id, and it was still `null`. |
   | `before calling 'persist()'` | `persist()` is the insert call that `save()` makes for a new entity. |
   | `JpaSystemException` | Spring's wrapper around Hibernate's error. Its family line is `JpaSystemException -> UncategorizedDataAccessException -> NonTransientDataAccessException -> DataAccessException -> NestedRuntimeException -> RuntimeException`: unchecked. |
   | `Initiating transaction rollback` | An unchecked exception left `placeOrder`, so Spring undid the transaction instead of committing it. |

   No `insert` was sent.

5. **No quotes around the log settings.** Maven stops at once with
   `Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace`.
   One pair of quotes around all the settings fixes it.

### Link to the task

- S9 now has a table and a first write. The `INSERT` is sent at `save()`, inside the open
  transaction, before the commit: `>>>    id after save: 1` printed before
  `Committing JPA transaction`. So when the next lesson makes the method fail after the `INSERT`,
  the database already has the row, and the rollback has to remove it.
- S10 and S11 will use the same table, through `OrderService`, with `createOrder()` and
  `persistOrder()`.
- S9 is still in progress.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `src/main/resources/db/migration` holds `V1__baseline.sql`, `V2__add_doors_open_at.sql` and
  `V3__add_event_slug.sql`;
- Postgres runs in the Docker container `pg` with the 7 seed events, and Flyway is at version 3.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the four Java files.** In `scratch-app/src/main/java/com/zenith/data/`, create
`OrderEntity.java` (36 lines), `OrderRepository.java` (8 lines), `OrderService.java` (27 lines)
and `OrderDemo.java` (35 lines), with the content from the example.

**Step 3 — run it before the migration exists.**

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=order
```

Expected: `Successfully validated 3 migrations` and
`Schema "public" is up to date. No migration necessary.`, then `Application run failed` and
`Schema-validation: missing table [ticket_order]`. No `>>>` lines appear.

**Step 4 — add the migration and run again.** Create
`scratch-app/src/main/resources/db/migration/V4__create_ticket_order.sql` with the 15 lines from
the example. The name is a capital `V`, `4`, two underscores, then `create_ticket_order.sql`. Run
with the two logs:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=order -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace"
```

Expected: the output from the example:

- `Migrating schema "public" to version "4 - create ticket order"`;
- `>>> 1. orders in the table: 0`, then `>>>    id before save: null`;
- the `insert` with no `id` and three bindings;
- `>>>    id after save: 1`, before `Committing JPA transaction`;
- `>>> 3. orders in the table: 1`.

**Step 5 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "\d ticket_order"
docker exec pg psql -U postgres -d zenith -c "select * from ticket_order;"
```

Expected: the `\d` output from the walkthrough, with `generated always as identity` on `id`, and
the one row for `meera@example.com` from the example.

**Step 6 — run it again, and watch the id come back.**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=order -Dspring-boot.run.arguments="--logging.level.org.postgresql.core.v3.QueryExecutorImpl=TRACE" | grep -E 'up to date|RETURNING|Field\(id|INSERT 0 1|>>>'
```

Real output. The start of three lines (time, process number, logger) will differ on your laptop:

```text
2026-11-03T02:27:54.682+05:30  INFO 85536 --- [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
>>> 1. orders in the table: 1
>>> 2. calling placeOrder
>>>    id before save: null
RETURNING *",oids={1043,2950,23})
2026-11-03T02:27:55.847+05:30 TRACE 85536 --- [           main] o.postgresql.core.v3.QueryExecutorImpl   :         Field(id,INT8,8,T)
2026-11-03T02:27:55.847+05:30 TRACE 85536 --- [           main] o.postgresql.core.v3.QueryExecutorImpl   :  <=BE CommandStatus(INSERT 0 1)
>>>    id after save: 2
>>>    placeOrder returned id 2
>>> 3. orders in the table: 2
```

Flyway didn't run V4 again, and the new row got the next number, 2.

**Step 7 — experiment with the id annotation.**

1. Change line 17 of `OrderEntity.java` to plain `@GeneratedValue`. Run
   `mvn -q spring-boot:run -Dspring-boot.run.profiles=order`. Expected:
   `Schema-validation: missing sequence [ticket_order_seq]`.
2. Delete line 17, and run again. Expected: the app starts and prints
   `>>>    id before save: null`, then stops with
   `JpaSystemException: Identifier of entity 'com.zenith.data.OrderEntity' must be manually assigned before calling 'persist()'`.
3. Put line 17 back exactly as in the example.

**Step 8 — empty the table for the next lessons.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`. The next order gets id 1 again, as
in these notes.

If it goes wrong:

- Step 4 still says `Successfully validated 3 migrations` and `missing table [ticket_order]`:
  Flyway didn't see the file. Check the two underscores, the `.sql` ending and the folder.
- `missing sequence [ticket_order_seq]`: line 17 lacks `strategy = GenerationType.IDENTITY`.
- `must be manually assigned before calling 'persist()'`: line 17 is missing.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.hibernate.orm.jdbc.bind=trace`:
  the quotes are missing.

**Done when:**

- you have seen `missing table [ticket_order]` before the migration, and
  `Migrating schema "public" to version "4 - create ticket order"` after it;
- you have seen `>>>    id before save: null`, the `insert` with no `id`, and
  `>>>    id after save: 1`, all before `Committing JPA transaction`;
- psql shows `generated always as identity` and the row;
- the second run gave id 2, with `RETURNING *` and `Field(id,INT8,8,T)` in the driver log;
- you have seen both id-annotation errors and put line 17 back;
- the table is empty again.

### Summary for revision

- A new table comes from a new migration. Flyway runs it once, at startup, before Hibernate's
  `validate` check.
- `id bigint generated always as identity`: Postgres numbers the rows, and refuses an id you give
  it.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`: the id is `null`
  until the insert.
- `save()` on a new entity sends the `INSERT` at once, without `id`. The driver adds
  `RETURNING *`, and Hibernate puts the new id into the same object.
- The row is in the database before the commit, inside the open transaction.
- Plain `@GeneratedValue` looks for a sequence (`missing sequence [ticket_order_seq]`); no
  `@GeneratedValue` fails at `save()` (`must be manually assigned`).
- Migration names need two underscores, or Flyway skips the file silently.
- `truncate ... restart identity` empties a table and restarts its numbering at 1.

### Practice questions, with answers

**Question 1.** `OrderService` gets this temporary method. `orders` is the `OrderRepository`, and
`OrderEntity`'s id is `@GeneratedValue(strategy = GenerationType.IDENTITY)` on a `Long`. Before the
call, `ticket_order` has one row, with id 1. The transaction log and the binding log are on.

```java
@Transactional
public long placeTwoOrders(UUID eventId) {
    OrderEntity first = orders.save(new OrderEntity(eventId, "first@example.com", 1));
    OrderEntity second = orders.save(new OrderEntity(eventId, "second@example.com", 3));
    System.out.println(">>>    ids: " + first.getId() + " and " + second.getId());
    return second.getId();
}
```

- (a) What does the `println` print?
- (b) How many `insert` statements appear in the log? Does each one come before or after the
  `>>>    ids:` line, and before or after `Committing JPA transaction`?
- (c) How many rows does the table have afterwards?

**Answer.** It was run for real, in a temporary service with exactly this method. The log (the
caller's `>>> CASE` and `>>> END` markers kept):

```text
>>> CASE two-orders
Creating new transaction with name [com.zenith.data.OrderCheckService.placeTwoOrders]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(451320510<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@1444e35f]
Found thread-bound EntityManager [SessionImpl(451320510<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [first@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Found thread-bound EntityManager [SessionImpl(451320510<open>)] for JPA transaction
Participating in existing transaction
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [second@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [3]
>>>    ids: 2 and 3
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(451320510<open>)]
Closing JPA EntityManager [SessionImpl(451320510<open>)] after transaction
>>> END
>>> COUNT 3
```

- (a) **`>>>    ids: 2 and 3`.** Row 1 already existed, so Postgres handed out the next two
  numbers.
- (b) **Two `insert`s, both before `>>>    ids:`, and both before `Committing JPA transaction`.**
  Each `save()` joined the open transaction (`Participating in existing transaction`) and sent its
  `INSERT` at once, because Hibernate needs each new id right away. Both ids were known by the time
  the `println` ran.
- (c) **Three rows** (`>>> COUNT 3`): the old row 1 and the two new ones. The commit made them
  permanent.

**Question 2.** `ticket_order` was created with `id bigint generated always as identity primary
key`, and it has three rows, with ids 1, 2 and 3. In psql you run these two statements, one after
the other. `returning id` asks Postgres to print the id of the new row.

```sql
insert into ticket_order (id, event_id, customer_email, quantity)
values (100, 'aaaaaaaa-0000-0000-0000-000000000005', 'arjun@example.com', 1);

insert into ticket_order (event_id, customer_email, quantity)
values ('aaaaaaaa-0000-0000-0000-000000000005', 'arjun@example.com', 1)
returning id;
```

- (a) What happens with the first statement?
- (b) What does the second one print?
- (c) After both, which ids are in the table?

**Answer.** Both were run for real, with `docker exec pg psql -U postgres -d zenith -c "..."`.

- (a) **Postgres refuses it, and inserts nothing:**

  ```text
  ERROR:  cannot insert a non-DEFAULT value into column "id"
  DETAIL:  Column "id" is an identity column defined as GENERATED ALWAYS.
  HINT:  Use OVERRIDING SYSTEM VALUE to override.
  ```

  `generated always` means only Postgres picks the id. The hint names a way to force it anyway,
  which normal code never needs.
- (b) **The new id, 4:**

  ```text
   id 
  ----
    4
  (1 row)

  INSERT 0 1
  ```

- (c) **1, 2, 3 and 4.** There is no row with id 100, and the refused insert didn't use up a
  number: the next one after 3 was still 4. The table afterwards:

  ```text
   id |               event_id               |   customer_email   | quantity 
  ----+--------------------------------------+--------------------+----------
    1 | aaaaaaaa-0000-0000-0000-000000000005 | meera@example.com  |        2
    2 | aaaaaaaa-0000-0000-0000-000000000005 | first@example.com  |        1
    3 | aaaaaaaa-0000-0000-0000-000000000005 | second@example.com |        3
    4 | aaaaaaaa-0000-0000-0000-000000000005 | arjun@example.com  |        1
  (4 rows)
  ```

  (After the checks, `truncate ticket_order restart identity;` emptied the table again.)

---

## Lesson 21 — An unchecked exception rolls everything back (starts subtask S9, answers half of CP5)

### Definition

Ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** makes every change permanent; a **rollback** undoes every change. With
  `@Transactional` on a method, Spring begins a transaction before the method runs and commits it
  after the method returns.
- An exception is **unchecked** when its family line (its class, its parent, its parent's parent,
  and so on) has `RuntimeException` or `Error`. The compiler doesn't make you catch it or write
  `throws`. Every other exception under `Exception` is **checked**: the compiler makes you do one
  of the two.
- In `ticket_order`, Postgres picks each row's id (1, 2, 3, ...) when the row is inserted. So
  `save()` on a new `OrderEntity` sends its `INSERT` at once. The row is in the database before
  the commit, inside the open transaction.
- `save()` and `count()` have their own `@Transactional` inside Spring Data. When a transaction is
  already open, they join it (`Participating in existing transaction`). When none is open, each
  one begins and commits its own short transaction.

New words:

- **Leaves the method.** An exception is thrown inside a method and not caught there, so it
  travels out to the caller.
- **Rollback rule.** How Spring decides, when an exception leaves a `@Transactional` method,
  whether to roll back or to commit. By default: roll back on an unchecked exception, commit on a
  checked one. This lesson shows the first half.
- **`TransactionInterceptor`.** The piece of Spring that runs just before and just after every
  `@Transactional` method. Its trace log
  (`--logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`)
  writes `Getting transaction for [method]` when the method starts, and
  `Completing transaction for [method]` when it ends. When an exception is leaving, the second
  line gets `after exception: ...` added.
- **Gap.** A missing number in the `id` column. Postgres hands out a number at each `INSERT` and
  never takes it back, even when the row is rolled back. So a gap proves that an `INSERT` reached
  the database and was then undone.

The same two-write method, from real runs:

| | No exception | `RuntimeException` halfway |
| --- | --- | --- |
| First `insert` | sent | sent |
| Second `insert` | sent | never runs |
| `TransactionInterceptor` logs | `Completing transaction for [...placeTwoOrders]` | the same, plus `after exception: java.lang.RuntimeException: ...` |
| Then | `Initiating transaction commit` | `Initiating transaction rollback` |
| Rows left from the call | 2 | 0 |

### Why it is needed

- It is the first half of subtask S9: "Throw a `RuntimeException` halfway through a two-write
  method and confirm both writes vanished."
- It answers half of checkpoint CP5: "Which kind of exception rolls back by default, and which
  does not?"
- "All or nothing" is the reason transactions exist. Picture an order that needs two writes, the
  order row and then a payment row. If the payment step fails after the order row is written, a
  half-finished order would stay in the database. The rollback leaves the database as it was
  before the call.

### Key points

- When an unchecked exception leaves a `@Transactional` method, Spring rolls the transaction
  back.
- Every write in that transaction is undone, including an `INSERT` that was already sent to
  Postgres.
- Spring then passes the very same exception on to the caller. It doesn't swallow it.
- With no settings on `@Transactional`, Spring's test is one line:
  `ex instanceof RuntimeException || ex instanceof Error`.
- Spring only sees an exception that leaves the method. One that is caught inside the method is
  invisible to it, and the transaction commits.
- Without `@Transactional` around the method, each `save()` commits on its own, and a later
  exception has nothing to roll back.
- Postgres never hands a number back, so a rolled-back `INSERT` leaves a gap in the ids.

### How it works, step by step

What happens when the demo calls `placeTwoOrders(SUFI_NIGHT, true)`:

1. **Begin.** Spring begins a new transaction before the method's first line.
2. **First write.** `save()` joins the open transaction and sends the `INSERT` at once. Postgres
   gives the row the number 3.
3. **Throw.** `throw new RuntimeException(...)` stops the method right there, so the second
   `save()` never runs.
4. **The exception leaves the method.** `TransactionInterceptor` sees it on its way out, and logs
   `Completing transaction for [...] after exception: ...`.
5. **The rule.** The exception is a `RuntimeException`, which is unchecked, so Spring rolls back.
   Postgres throws row 3 away.
6. **Pass it on.** The caller gets the very same `RuntimeException`.
7. **The number is gone for good.** Number 3 is never handed out again, so the next order gets 4.

### Example

`OrderService.java` gets `placeTwoOrders` at lines 28 to 39. When `failHalfway` is `true`, the
exception comes between the two writes. The whole file (40 lines):

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 20: the service for ticket orders. The rollback lessons add to it.
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 20: one write, inside one transaction. The database picks the id.
    @Transactional
    public long placeOrder(UUID eventId, String customerEmail, int quantity) {
        OrderEntity order = new OrderEntity(eventId, customerEmail, quantity);
        System.out.println(">>>    id before save: " + order.getId());
        orders.save(order);
        System.out.println(">>>    id after save: " + order.getId());
        return order.getId();
    }

    // Lesson 21: two writes in one transaction. With failHalfway, an unchecked exception
    // comes between them.
    @Transactional
    public void placeTwoOrders(UUID eventId, boolean failHalfway) {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new RuntimeException("payment failed after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }
}
```

The demo, `RollbackDemo.java` (43 lines). It calls the method once without the failure and once
with it, counts the rows after each call, and then places one normal order to see which id comes
next:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 21: the same two-write method, once without and once with an unchecked exception halfway.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=rollback
@Component
@Profile("rollback")
class RollbackDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    RollbackDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. placeTwoOrders without a failure");
        service.placeTwoOrders(SUFI_NIGHT, false);
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. placeTwoOrders with a RuntimeException halfway");
        try {
            service.placeTwoOrders(SUFI_NIGHT, true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 3. one more order, to see which id comes next");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);
    }
}
```

The command, with three logs: the transaction log, the binding log, and `TransactionInterceptor`'s
trace log. All three settings go inside one pair of quotes. The table was empty, with its
numbering at 1:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=rollback -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line (time, level,
process number, thread, logger) cut off. Binding lines start with `...`. Each `count()` runs in
its own short read-only transaction of 13 lines, and each of those is replaced by one `...` line.
In step 3 only the `>>>` lines are kept; its log has the same pattern as step 1, with one `insert`
for `meera@example.com` and a commit.

```text
>>> 1. placeTwoOrders without a failure
Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrders]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(761970631<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@509bf2d5]
Getting transaction for [com.zenith.data.OrderService.placeTwoOrders]
Found thread-bound EntityManager [SessionImpl(761970631<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [first@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Found thread-bound EntityManager [SessionImpl(761970631<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [second@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    second order saved
Completing transaction for [com.zenith.data.OrderService.placeTwoOrders]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(761970631<open>)]
Closing JPA EntityManager [SessionImpl(761970631<open>)] after transaction
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 2
>>> 2. placeTwoOrders with a RuntimeException halfway
Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrders]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1120120878<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@51039ec9]
Getting transaction for [com.zenith.data.OrderService.placeTwoOrders]
Found thread-bound EntityManager [SessionImpl(1120120878<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [first@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.OrderService.placeTwoOrders] after exception: java.lang.RuntimeException: payment failed after the first order
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(1120120878<open>)]
Closing JPA EntityManager [SessionImpl(1120120878<open>)] after transaction
>>>    caught RuntimeException: payment failed after the first order
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 2
>>> 3. one more order, to see which id comes next
>>>    id before save: null
>>>    id after save: 4
>>>    placeOrder returned id 4
```

Afterwards, `select * from ticket_order order by id;` in psql:

```text
 id |               event_id               |   customer_email   | quantity 
----+--------------------------------------+--------------------+----------
  1 | aaaaaaaa-0000-0000-0000-000000000005 | first@example.com  |        1
  2 | aaaaaaaa-0000-0000-0000-000000000005 | second@example.com |        1
  4 | aaaaaaaa-0000-0000-0000-000000000005 | meera@example.com  |        2
(3 rows)
```

### Walkthrough

**Step 2 is subtask S9.** Its lines, in order:

1. **`Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrders]`.** Spring
   began the transaction before the method's first line.
2. **`Getting transaction for [com.zenith.data.OrderService.placeTwoOrders]`.**
   `TransactionInterceptor` has the transaction ready, and now runs the method.
3. **`Participating in existing transaction`**, with
   `Getting transaction for [...SimpleJpaRepository.save]`. `save()` has its own `@Transactional`,
   so `TransactionInterceptor` runs around it too. A transaction is already open, so `save()`
   joins it instead of beginning a new one.
4. **The `insert`, with `[first@example.com]`.** The `INSERT` went to Postgres right then, and
   Postgres numbered the row 3.
5. **`>>>    first order saved`.** Then the `throw` ran, and the method stopped.
6. **`Completing transaction for [com.zenith.data.OrderService.placeTwoOrders] after exception: java.lang.RuntimeException: payment failed after the first order`.**
   The exception left the method, and Spring saw it.
7. **`Initiating transaction rollback`, then `Rolling back JPA transaction ...`.** The rule said:
   a `RuntimeException`, so roll back. Postgres threw row 3 away.
8. **`>>>    caught RuntimeException: payment failed after the first order`.** The same exception
   reached the runner's `catch`.
9. **`>>>    orders in the table: 2`.** Still 2: the call left nothing behind.

**Step 1, for comparison.** At the same spot it says
`Completing transaction for [com.zenith.data.OrderService.placeTwoOrders]` with no
`after exception`, and then `Initiating transaction commit`. It's the same method with the same
writes. The only difference is the exception, and it turns the commit into a rollback.

**The gap is the proof.** psql has no row 3. Postgres handed number 3 to the `INSERT` in step 2,
and the rollback removed the row, but numbers are never given back. That's why step 3 got id 4.
So the `INSERT` really reached the database, and was undone. It wasn't just "never sent".

**"Both writes vanished."** The first write ran and was undone. The second never ran, because the
`throw` comes before it. Neither of the two is in the table.

### Important terms

| Term | Meaning |
| --- | --- |
| **Leave the method** | An exception thrown inside a method and not caught there travels out to the caller. Only such an exception can make Spring roll back. |
| **Rollback rule** | Spring's default: roll back on an unchecked exception (`RuntimeException`, `Error`, or a child of one), commit on a checked one. |
| **`TransactionInterceptor`** | The piece of Spring that runs just before and just after each `@Transactional` method, and decides between commit and rollback. |
| **`Getting transaction for [...]`** | `TransactionInterceptor`: the transaction is ready, and the method starts. |
| **`Completing transaction for [...]`** | `TransactionInterceptor`: the method has ended. With `after exception: ...` added, an exception is leaving it. |
| **`Initiating transaction rollback`** | `JpaTransactionManager`: the transaction is being undone. |
| **Gap** | A missing id. A rolled-back `INSERT` still used up its number. |

### Where it is used

- **Any service method that writes more than one row.** Placing an order and then lowering the
  tickets left is one example. Taking money from one account and adding it to another is
  another. If something throws an unchecked exception in the middle, the rollback leaves the
  database as it was before the call.
- **Code that calls other code.** An uncaught unchecked exception from anything your
  `@Transactional` method calls also leaves your method, so it rolls back your writes too.
- In this task: subtask S9 and checkpoint CP5. The next lesson does the checked half.

### Edge cases

- **The caller still gets the exception.** Spring rolls back and then throws the same exception
  on. The runner printed `>>>    caught RuntimeException: payment failed after the first order`:
  the same class and the same message that `placeTwoOrders` threw.
- **`Error` rolls back too.** The rule's test is
  `ex instanceof RuntimeException || ex instanceof Error`. The demo only threw a
  `RuntimeException`.
- **Catching a repository's exception inside the method doesn't save the transaction.** A
  temporary service had this method. The database refuses the second `save()`, because
  `ticket_order` has the rule `check (quantity > 0)`:

  ```java
  @Transactional
  public void catchRepositoryException(UUID eventId) {
      orders.save(new OrderEntity(eventId, "first@example.com", 1));
      System.out.println(">>>    first order saved");
      try {
          orders.save(new OrderEntity(eventId, "second@example.com", 0));
      } catch (DataIntegrityViolationException e) {
          System.out.println(">>>    caught inside the method: " + e.getClass().getSimpleName());
      }
      System.out.println(">>>    method ends normally");
  }
  ```

  The real log. The front of each line is cut off, each `insert` is shortened to two lines, and
  the binding lines are left out. The table had 8 rows from earlier checks:

  ```text
  >>> CASE catch-repository-exception: orders before: 8
  Creating new transaction with name [com.zenith.data.RollbackCheckService.catchRepositoryException]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
  Getting transaction for [com.zenith.data.RollbackCheckService.catchRepositoryException]
  Participating in existing transaction
  Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
      insert 
          ticket_order
  Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
  >>>    first order saved
  Participating in existing transaction
  Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
      insert 
          ticket_order
  SQL Error: 0, SQLState: 23514
  ERROR: new row for relation "ticket_order" violates check constraint "ticket_order_quantity_positive"
    Detail: Failing row contains (13, aaaaaaaa-0000-0000-0000-000000000005, second@example.com, 0).
  Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save] after exception: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: new row for relation "ticket_order" violates check constraint "ticket_order_quantity_positive"
    Detail: Failing row contains (13, aaaaaaaa-0000-0000-0000-000000000005, second@example.com, 0).] [insert into ticket_order (customer_email,event_id,quantity) values (?,?,?)]
  Participating transaction failed - marking existing transaction as rollback-only
  Setting JPA transaction on EntityManager [SessionImpl(907681414<open>)] rollback-only
  >>>    caught inside the method: DataIntegrityViolationException
  >>>    method ends normally
  Completing transaction for [com.zenith.data.RollbackCheckService.catchRepositoryException]
  Initiating transaction commit
  Committing JPA transaction on EntityManager [SessionImpl(907681414<open>)]
  Closing JPA EntityManager [SessionImpl(907681414<open>)] after transaction
  >>>    caller caught org.springframework.transaction.UnexpectedRollbackException
  >>>    message: Transaction silently rolled back because it has been marked as rollback-only
  >>> END catch-repository-exception: orders after: 8
  ```

  What happened, step by step:

  1. The exception came out of `save()` first. `TransactionInterceptor` runs around `save()` too,
     and saw the exception leave it. But `save()` had only joined the transaction, so it
     couldn't roll back on its own. Instead, Spring put a mark on the shared transaction,
     **rollback-only**, which means "this transaction must not commit":
     `Participating transaction failed - marking existing transaction as rollback-only`.
  2. The `catch` stopped the exception, but not the mark. The method ended normally, so Spring
     went ahead with the commit: `Initiating transaction commit`.
  3. Because of the mark, that commit became a rollback. Spring then threw
     `UnexpectedRollbackException` to tell the caller. The count stayed at 8, so the first write
     was undone too, even though the log says `Committing JPA transaction`.

  | Part | Plain meaning |
  | --- | --- |
  | `UnexpectedRollbackException` | The caller expected a commit, because no exception left the method, but the transaction was rolled back. It is unchecked: the caller's `catch (RuntimeException e)` caught it. |
  | `Transaction silently rolled back` | The rollback happened during the commit step, with no exception leaving your method. |
  | `because it has been marked as rollback-only` | The mark that the failed `save()` put on the shared transaction. |

### Common mistakes — with the real errors

1. **Reading the `insert` in the log as "saved".** Step 2 of the demo shows a real `insert` for
   `first@example.com`, yet the row is not in the table. A row sent inside a transaction becomes
   permanent only at the commit. A rollback removes it, and the `insert` line stays in the log.

2. **A `throw` with no `if`.** "Throw halfway" makes people put the `throw` straight between the
   two saves, in place of lines 34 to 36. The build stopped:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../OrderService.java:[35,9] unreachable statement
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `OrderService.java:[35,9]` | Line 35, column 9: the start of the second `orders.save(...)`, which moved up to line 35. |
   | `unreachable statement` | Java can see that this line can never run: the `throw` just above it always ends the method. |

   The `if (failHalfway)` makes the `throw` happen only sometimes, so Java accepts the lines
   after it.

3. **Catching the exception inside the method.** A temporary service had this method:

   ```java
   @Transactional
   public void catchInside(UUID eventId) {
       orders.save(new OrderEntity(eventId, "first@example.com", 1));
       System.out.println(">>>    first order saved");
       try {
           throw new RuntimeException("payment failed after the first order");
       } catch (RuntimeException e) {
           System.out.println(">>>    caught inside the method: " + e.getMessage());
       }
       orders.save(new OrderEntity(eventId, "second@example.com", 1));
       System.out.println(">>>    second order saved");
   }
   ```

   The real log, cut the same way as in the edge case. The table had 3 rows before:

   ```text
   >>> CASE catch-inside: orders before: 3
   Creating new transaction with name [com.zenith.data.RollbackCheckService.catchInside]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
   Getting transaction for [com.zenith.data.RollbackCheckService.catchInside]
   Participating in existing transaction
   Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
       insert 
           ticket_order
   Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
   >>>    first order saved
   >>>    caught inside the method: payment failed after the first order
   Participating in existing transaction
   Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
       insert 
           ticket_order
   Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
   >>>    second order saved
   Completing transaction for [com.zenith.data.RollbackCheckService.catchInside]
   Initiating transaction commit
   Committing JPA transaction on EntityManager [SessionImpl(1332205096<open>)]
   Closing JPA EntityManager [SessionImpl(1332205096<open>)] after transaction
   >>>    no exception reached the caller
   >>> END catch-inside: orders after: 5
   ```

   There is no `after exception` and no rollback, and both rows were committed: 3 became 5.
   Spring only sees an exception that leaves the method.

4. **Forgetting `@Transactional` on the two-write method.** Line 30 was deleted, the table was
   emptied, and the demo ran again. Step 2 of the real output, shortened: the transaction lines
   and the `>>>` lines are kept, the `insert` is one line, and `count()`'s transaction is left
   out:

   ```text
   >>> 2. placeTwoOrders with a RuntimeException halfway
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
       insert 
   Initiating transaction commit
   Committing JPA transaction on EntityManager [SessionImpl(1212327555<open>)]
   Closing JPA EntityManager [SessionImpl(1212327555<open>)] after transaction
   >>>    first order saved
   >>>    caught RuntimeException: payment failed after the first order
   >>>    orders in the table: 3
   ```

   The transaction is now named after `SimpleJpaRepository.save`, not `placeTwoOrders`. With no
   transaction around the method, each `save()` began its own short transaction and committed at
   once, so the exception had nothing left to roll back. psql afterwards:

   ```text
    id |   customer_email   | quantity 
   ----+--------------------+----------
     1 | first@example.com  |        1
     2 | second@example.com |        1
     3 | first@example.com  |        1
     4 | meera@example.com  |        2
   (4 rows)
   ```

   Row 3 survived the exception. Subtask S10 ends with this same picture, even though
   `@Transactional` is written on the method there.

5. **No quotes around the log settings.** Maven stopped at once:

   ```text
   Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE
   ```

   The shell split the settings at the space, so Maven read the second one as one of its own
   options. One pair of quotes around all the settings fixes it.

### Link to the task

Subtask S9 begins: "Throw a `RuntimeException` halfway through a two-write method and confirm
both writes vanished."

- **A two-write method:** `placeTwoOrders`, with two `save()` calls.
- **A `RuntimeException` halfway:** `failHalfway = true` throws between the two writes.
- **Both writes vanished:** the first `INSERT` was sent and then rolled back
  (`Initiating transaction rollback`), and the second never ran. The count stayed at 2, and psql
  shows no row 3.
- **The other half of S9** is the next lesson: a checked exception, where "the first write
  survives".

**CP5, first half:** an unchecked exception rolls back by default. That means `RuntimeException`,
`Error`, and every class whose family line has one of them.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `OrderEntity.java`, `OrderRepository.java`, `OrderDemo.java`, and `OrderService.java` (27 lines,
  ending with `placeOrder`) are in `scratch-app/src/main/java/com/zenith/data/`;
- `V4__create_ticket_order.sql` has run, and `ticket_order` is empty, with its numbering
  restarted at 1;
- Postgres runs in the Docker container `pg`.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the two-write method.** In `OrderService.java`, before the last `}`, add a blank
line and then lines 28 to 39 from the example: the two comment lines, `@Transactional`, and
`placeTwoOrders`. The file then matches the 40 lines in the example.

**Step 3 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/RollbackDemo.java`
with the 43 lines from the example.

**Step 4 — run it.** `grep -E` keeps only the lines that contain one of the five patterns between
the quotes:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=rollback -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|insert|after exception|transaction rollback|transaction commit'
```

Real output. The start of the long lines (time, process number, logger) will differ on your
laptop:

```text
>>> 1. placeTwoOrders without a failure
    insert 
>>>    first order saved
    insert 
>>>    second order saved
2026-11-03T02:39:14.941+05:30 DEBUG 86334 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
2026-11-03T02:39:14.978+05:30 DEBUG 86334 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 2
>>> 2. placeTwoOrders with a RuntimeException halfway
    insert 
>>>    first order saved
2026-11-03T02:39:14.981+05:30 TRACE 86334 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.OrderService.placeTwoOrders] after exception: java.lang.RuntimeException: payment failed after the first order
2026-11-03T02:39:14.981+05:30 DEBUG 86334 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
>>>    caught RuntimeException: payment failed after the first order
2026-11-03T02:39:14.984+05:30 DEBUG 86334 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 2
>>> 3. one more order, to see which id comes next
>>>    id before save: null
    insert 
>>>    id after save: 4
2026-11-03T02:39:14.988+05:30 DEBUG 86334 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    placeOrder returned id 4
```

Step 1 of the output ends with two commits: the first belongs to `placeTwoOrders`, and the second
to `count()`'s own short transaction. Step 2 has one `insert`, then `after exception`, then
`Initiating transaction rollback`, and the count is still 2.

**Step 5 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select * from ticket_order order by id;"
```

Expected: the three rows from the example, with ids 1, 2 and 4, and no row 3.

**Step 6 — break it: a `throw` with no `if`.** In `OrderService.java`, replace lines 34 to 36
(the `if (failHalfway) {` line, the `throw` line and the `}`) with just this one line:

```java
        throw new RuntimeException("payment failed after the first order");
```

Run `mvn -q spring-boot:run -Dspring-boot.run.profiles=rollback`. The build stops with this, with
your full path in place of `...`:

```text
[ERROR] COMPILATION ERROR : 
[ERROR] .../OrderService.java:[35,9] unreachable statement
```

Put the three lines back exactly as in the example.

**Step 7 — break it: remove `@Transactional`.** First empty the table, so the ids are easy to
read:

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
```

It prints `TRUNCATE TABLE`. Then delete line 30 of `OrderService.java`: the `@Transactional` above
`placeTwoOrders`, not the one on line 19 above `placeOrder`. Run the command from step 4 again.
Real output:

```text
>>> 1. placeTwoOrders without a failure
    insert 
2026-11-03T02:39:31.477+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    first order saved
    insert 
2026-11-03T02:39:31.484+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    second order saved
2026-11-03T02:39:31.520+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 2
>>> 2. placeTwoOrders with a RuntimeException halfway
    insert 
2026-11-03T02:39:31.523+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    first order saved
>>>    caught RuntimeException: payment failed after the first order
2026-11-03T02:39:31.526+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 3
>>> 3. one more order, to see which id comes next
>>>    id before save: null
    insert 
>>>    id after save: 4
2026-11-03T02:39:31.529+05:30 DEBUG 86407 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    placeOrder returned id 4
```

Every `insert` now has its own commit right after it. There is no `after exception` and no
rollback, and the count after step 2 is 3. Run the psql command from step 5: it shows ids 1, 2, 3
and 4, and row 3 is `first@example.com`, the write that survived. Then put `    @Transactional`
back on its own line above `public void placeTwoOrders(`.

**Step 8 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- Step 4 shows `>>>    orders in the table: 3` after step 2 of the output, and no
  `Initiating transaction rollback`: the `@Transactional` above `placeTwoOrders` is missing.
- The build stops with `OrderService.java:[35,9] unreachable statement`: the
  `if (failHalfway) {` line or its `}` is missing.
- The ids are higher than 1, 2 and 4: the table wasn't empty with its numbering restarted. Run
  step 8, then step 4 again.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- step 2 of the output showed one `insert`, then
  `after exception: java.lang.RuntimeException: payment failed after the first order`, then
  `Initiating transaction rollback`, and the count stayed at 2;
- psql showed ids 1, 2 and 4, with no 3;
- you have seen `unreachable statement`, and put the `if` back;
- without `@Transactional`, the count became 3 and psql showed row 3 for `first@example.com`,
  and you put `@Transactional` back;
- the table is empty again.

### Summary for revision

- When an unchecked exception (`RuntimeException`, `Error`, or a child of one) leaves a
  `@Transactional` method, Spring rolls the whole transaction back.
- Writes already sent are undone too: the `insert` is in the log, but the row isn't in the table.
- The log shows the decision: `Completing transaction for [...] after exception: ...`, then
  `Initiating transaction rollback`.
- The caller still gets the same exception.
- A rolled-back `INSERT` leaves a gap in the identity numbers.
- An exception caught inside the method is invisible to Spring, and the transaction commits. But
  if it came out of a repository method, the transaction is already marked rollback-only, and the
  caller gets `UnexpectedRollbackException`.
- Without `@Transactional`, each `save()` commits on its own, and nothing can be rolled back.
- A `throw` right before another statement doesn't compile: `unreachable statement`.

### Practice questions, with answers

**Question 1.** The table `ticket_order` was created with the rule
`constraint ticket_order_quantity_positive check (quantity > 0)`, so Postgres refuses any row
whose `quantity` is 0 or less. A temporary Spring bean, a service called `LessonCheck21Service`,
has an `OrderRepository orders` field and the method below. `new OrderEntity(eventId, email,
quantity)` builds a new order, and `save()` sends its `INSERT` at once. The table is empty before
the call. The caller wraps the call in a `try` / `catch` that prints the class name of the
exception it catches, and then prints the number of rows.

```java
@Transactional
public void secondWriteBreaksARule(UUID eventId) {
    orders.save(new OrderEntity(eventId, "first@example.com", 1));
    System.out.println(">>>    first order saved");
    orders.save(new OrderEntity(eventId, "second@example.com", 0));
    System.out.println(">>>    second order saved");
}
```

- (a) Which of the two `>>>` lines print?
- (b) The exception that reaches the caller comes from Spring, not from your code. Is it checked
  or unchecked? Use one fact about the code above to explain.
- (c) How many rows does the table have afterwards, and why?

**Answer.** It was run for real, in a temporary service with exactly this method. The caller also
printed the exception's family line. The log, with the front of each line cut off, each `insert`
shortened to two lines, and the binding lines and `count()`'s transactions left out:

```text
>>> Q1: orders before: 0
Creating new transaction with name [com.zenith.data.LessonCheck21Service.secondWriteBreaksARule]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Getting transaction for [com.zenith.data.LessonCheck21Service.secondWriteBreaksARule]
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
SQL Error: 0, SQLState: 23514
ERROR: new row for relation "ticket_order" violates check constraint "ticket_order_quantity_positive"
  Detail: Failing row contains (2, aaaaaaaa-0000-0000-0000-000000000005, second@example.com, 0).
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save] after exception: org.hibernate.exception.ConstraintViolationException: could not execute statement [ERROR: new row for relation "ticket_order" violates check constraint "ticket_order_quantity_positive"
  Detail: Failing row contains (2, aaaaaaaa-0000-0000-0000-000000000005, second@example.com, 0).] [insert into ticket_order (customer_email,event_id,quantity) values (?,?,?)]
Participating transaction failed - marking existing transaction as rollback-only
Setting JPA transaction on EntityManager [SessionImpl(480909414<open>)] rollback-only
Completing transaction for [com.zenith.data.LessonCheck21Service.secondWriteBreaksARule] after exception: org.springframework.dao.DataIntegrityViolationException: could not execute statement [ERROR: new row for relation "ticket_order" violates check constraint "ticket_order_quantity_positive"
  Detail: Failing row contains (2, aaaaaaaa-0000-0000-0000-000000000005, second@example.com, 0).] [insert into ticket_order (customer_email,event_id,quantity) values (?,?,?)]; SQL [insert into ticket_order (customer_email,event_id,quantity) values (?,?,?)]; constraint [ticket_order_quantity_positive]
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(480909414<open>)]
Closing JPA EntityManager [SessionImpl(480909414<open>)] after transaction
>>>    caller caught org.springframework.dao.DataIntegrityViolationException
>>>    family: DataIntegrityViolationException -> NonTransientDataAccessException -> DataAccessException -> NestedRuntimeException -> RuntimeException -> Exception -> Throwable -> Object
>>> Q1: orders after: 0
```

- (a) **Only `>>>    first order saved`.** The second `save()` sent its `INSERT`, Postgres refused
  it (`violates check constraint "ticket_order_quantity_positive"`), and `save()` threw. The
  second `println` never ran.
- (b) **Unchecked.** The fact: `secondWriteBreaksARule` has no `throws` clause, and it still
  compiles. If a checked exception could come out of `save()`, the compiler would refuse the
  method with `unreported exception ...; must be caught or declared to be thrown`. The real
  class is `org.springframework.dao.DataIntegrityViolationException`, and its family line has
  `RuntimeException`. Under it, a check of the causes in the same situation found Hibernate's
  `org.hibernate.exception.ConstraintViolationException`, and at the bottom the driver's
  `org.postgresql.util.PSQLException`. That one is checked, but Hibernate and Spring wrap it in
  unchecked exceptions, so your code never has to catch it.
- (c) **0 rows.** The exception left the method (`after exception: org.springframework.dao.DataIntegrityViolationException`),
  so Spring rolled back, and the first order was undone too. The exception had first passed
  through `save()`'s own `@Transactional`, which marked the shared transaction rollback-only;
  either way, it ended in `Initiating transaction rollback`. Both numbers were used up: the first
  order had id 1, and the refused row had id 2 (`Failing row contains (2, ...)`). Postgres fills
  in the id before it checks the rules. The next order afterwards got id 3.

**Question 2.** `OrderService` is a Spring bean with an `OrderRepository orders` field and this
method:

```java
@Transactional
public void placeTwoOrders(UUID eventId, boolean failHalfway) {
    orders.save(new OrderEntity(eventId, "first@example.com", 1));
    System.out.println(">>>    first order saved");
    if (failHalfway) {
        throw new RuntimeException("payment failed after the first order");
    }
    orders.save(new OrderEntity(eventId, "second@example.com", 1));
    System.out.println(">>>    second order saved");
}
```

A runner with no `@Transactional` anywhere in it runs the code below. `service` is the
`OrderService`, and `orders` is the `OrderRepository`. The table is empty before.

```java
try {
    service.placeTwoOrders(SUFI_NIGHT, false);
    throw new RuntimeException("the caller fails after placeTwoOrders returned");
} catch (RuntimeException e) {
    System.out.println(">>>    caller caught RuntimeException: " + e.getMessage());
}
System.out.println(">>> Q2: orders after: " + orders.count());
```

- (a) What does the last line print?
- (b) A `RuntimeException` was thrown. Why doesn't it undo the two writes?
- (c) With the transaction log on, does the `Initiating transaction commit` for `placeTwoOrders`
  come before or after the `>>>    caller caught` line?

**Answer.** It was run for real, in the same temporary runner, right after Question 1. The log,
cut the same way:

```text
>>> Q2: orders before: 0
Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrders]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Getting transaction for [com.zenith.data.OrderService.placeTwoOrders]
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    second order saved
Completing transaction for [com.zenith.data.OrderService.placeTwoOrders]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(907191821<open>)]
Closing JPA EntityManager [SessionImpl(907191821<open>)] after transaction
>>>    caller caught RuntimeException: the caller fails after placeTwoOrders returned
>>> Q2: orders after: 2
```

- (a) **`>>> Q2: orders after: 2`.**
- (b) **The transaction was already committed.** The rollback rule only applies to an exception
  that leaves the `@Transactional` method while its transaction is still open. `placeTwoOrders`
  returned normally (`Completing transaction for [...]` with no `after exception`), so Spring
  committed. The runner's exception came later, in code with no transaction, and a commit can't be
  taken back.
- (c) **Before.** `Initiating transaction commit` and `Committing JPA transaction` come before
  `>>>    caller caught RuntimeException: ...`.

psql afterwards showed rows 3 and 4. Numbers 1 and 2 had been used up in Question 1: 1 by the
first order that was undone, and 2 by the row Postgres refused. (After the checks,
`truncate ticket_order restart identity;` emptied the table again.)

---

## Lesson 22 — A checked exception commits (finishes subtask S9, answers CP5)

### Definition

Ideas you need again, in short:

- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** makes every change permanent; a **rollback** undoes every change. With
  `@Transactional` on a method, Spring begins a transaction before the method runs and commits it
  after the method returns.
- An exception is **unchecked** when its family line (its class, its parent, its parent's parent,
  and so on) has `RuntimeException` or `Error`. Every other exception under `Exception` is
  **checked**.
- **The compiler's rule for checked exceptions:** a method that can throw one must say so on its
  first line, and every caller must either `catch` it or say so too. Otherwise the code doesn't
  compile.
- **What an unchecked exception does:** when one leaves a `@Transactional` method (it is thrown
  there and not caught there), Spring rolls the transaction back. `placeTwoOrders` saved one
  order and then threw a `RuntimeException`. Its `INSERT` had already been sent, but it was
  undone, and nothing was left in the table.
- **`TransactionInterceptor`** is the piece of Spring that runs just before and just after each
  `@Transactional` method. Its trace log says `Completing transaction for [method] after
  exception: ...` when an exception leaves.
- In `ticket_order`, Postgres numbers new rows 1, 2, 3, ... and never hands a number back. A
  missing number (a **gap**) means an `INSERT` was sent and then undone.

New words:

- **`throws` clause.** The part of a method's first line that lists the checked exceptions it may
  throw, for example `throws PaymentDeclinedException`.
- **`rollbackFor`.** A setting of `@Transactional` that lists exception classes that should roll
  back too, for example `@Transactional(rollbackFor = PaymentDeclinedException.class)`. It covers
  the listed class and all of its child classes.
- **`.class`.** `PaymentDeclinedException.class` is how Java refers to the class itself as a value,
  so it can be handed to a setting.
- **The rule's sign in the log.** With `rollbackFor`, the `Creating new transaction ...` line ends
  with `-com.zenith.data.PaymentDeclinedException`. The `-` means "roll back for this exception".

The same failure under two settings, from real runs:

| | `@Transactional` | `@Transactional(rollbackFor = PaymentDeclinedException.class)` |
| --- | --- | --- |
| End of the `Creating new transaction ...` line | `PROPAGATION_REQUIRED,ISOLATION_DEFAULT` | `PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-com.zenith.data.PaymentDeclinedException` |
| After `after exception: com.zenith.data.PaymentDeclinedException: ...` | `Initiating transaction commit` | `Initiating transaction rollback` |
| The write made before the `throw` | stays | undone |

### Why it is needed

- It is the second half of subtask S9: "Then throw a checked exception and watch the first write
  survive."
- It finishes checkpoint CP5: "Which kind of exception rolls back by default, and which does
  not?"
- S9 itself calls it "the rollback rule that catches people out". Business exceptions such as
  "payment declined" are often checked. Without knowing the rule, a failed order quietly leaves
  half of its rows behind, with no error anywhere.
- The thinking behind the default: the compiler forces callers to handle a checked exception, so
  Spring treats it as an expected outcome, not a crash, and keeps the work done so far.

### Key points

- By default, when a checked exception leaves a `@Transactional` method, Spring **commits**.
  Every write made before the `throw` stays.
- The log shows it plainly: `after exception: ...` followed by `Initiating transaction commit`.
- `@Transactional(rollbackFor = X.class)` makes `X` and all its child classes roll back.
- `rollbackFor` adds to the default rule; it doesn't replace it. Unchecked exceptions still roll
  back.
- The `throws` clause is for the compiler only. Both methods in the example have the same
  `throws PaymentDeclinedException`; only `rollbackFor` changes the outcome.
- The caller still gets the same checked exception, and the compiler makes it catch it.

### How it works, step by step

When the demo calls `placeTwoOrdersChecked(SUFI_NIGHT, true)`, which has plain `@Transactional`:

1. **Begin.** Spring begins a new transaction.
2. **First write.** `save()` joins the transaction and sends the `INSERT` at once. Postgres
   numbers the row 1.
3. **Throw.** `throw new PaymentDeclinedException(...)` stops the method. The second `save()`
   never runs.
4. **The exception leaves the method.** `TransactionInterceptor` logs
   `... after exception: com.zenith.data.PaymentDeclinedException: ...`.
5. **The rule.** Is it a `RuntimeException` or an `Error`? No, so Spring **commits**. Row 1 is
   permanent.
6. **Pass it on.** The caller gets the same `PaymentDeclinedException`.

When it calls `placeTwoOrdersRollbackFor(SUFI_NIGHT, true)`, which has
`@Transactional(rollbackFor = PaymentDeclinedException.class)`:

1. Steps 1 to 4 are the same; Postgres numbers this row 2.
2. **The rule** first checks the list: is the exception a `PaymentDeclinedException`, or a child
   of it? Yes, so Spring **rolls back**, and row 2 is thrown away.
3. **Pass it on**, the same as before.

### Example

A new file, `PaymentDeclinedException.java` (9 lines). Its parent is `Exception`, not
`RuntimeException`:

```java
package com.zenith.data;

// Lesson 22: a checked exception. Its family line has Exception, but not RuntimeException.
public class PaymentDeclinedException extends Exception {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
```

`OrderService.java` gets two new methods at lines 41 to 65. Both have the body of
`placeTwoOrders`, but they throw the checked exception. Only their annotation differs: plain
`@Transactional` on line 42, and `rollbackFor` on line 55. Both need
`throws PaymentDeclinedException` (lines 44 and 57). The whole file (66 lines):

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 20: the service for ticket orders. The rollback lessons add to it.
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 20: one write, inside one transaction. The database picks the id.
    @Transactional
    public long placeOrder(UUID eventId, String customerEmail, int quantity) {
        OrderEntity order = new OrderEntity(eventId, customerEmail, quantity);
        System.out.println(">>>    id before save: " + order.getId());
        orders.save(order);
        System.out.println(">>>    id after save: " + order.getId());
        return order.getId();
    }

    // Lesson 21: two writes in one transaction. With failHalfway, an unchecked exception
    // comes between them.
    @Transactional
    public void placeTwoOrders(UUID eventId, boolean failHalfway) {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new RuntimeException("payment failed after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }

    // Lesson 22: the same two writes, but the failure is a checked exception.
    @Transactional
    public void placeTwoOrdersChecked(UUID eventId, boolean failHalfway)
            throws PaymentDeclinedException {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new PaymentDeclinedException("card declined after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }

    // Lesson 22: the fix. rollbackFor adds this checked exception to the rollback rule.
    @Transactional(rollbackFor = PaymentDeclinedException.class)
    public void placeTwoOrdersRollbackFor(UUID eventId, boolean failHalfway)
            throws PaymentDeclinedException {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        System.out.println(">>>    first order saved");
        if (failHalfway) {
            throw new PaymentDeclinedException("card declined after the first order");
        }
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        System.out.println(">>>    second order saved");
    }
}
```

The demo, `CheckedDemo.java` (47 lines). It calls each new method with the failure, inside a
`try` / `catch` for the checked exception, counts the rows after each call, and then places one
normal order to see which id comes next:

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 22: a checked exception halfway, first without and then with rollbackFor.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=checked
@Component
@Profile("checked")
class CheckedDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    CheckedDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. placeTwoOrdersChecked: a checked exception halfway");
        try {
            service.placeTwoOrdersChecked(SUFI_NIGHT, true);
        } catch (PaymentDeclinedException e) {
            System.out.println(">>>    caught PaymentDeclinedException: " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. placeTwoOrdersRollbackFor: the same, with rollbackFor");
        try {
            service.placeTwoOrdersRollbackFor(SUFI_NIGHT, true);
        } catch (PaymentDeclinedException e) {
            System.out.println(">>>    caught PaymentDeclinedException: " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 3. one more order, to see which id comes next");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);
    }
}
```

The command, with the transaction log, the binding log, and `TransactionInterceptor`'s trace log,
all inside one pair of quotes. The table was empty, with its numbering at 1:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=checked -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=trace --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line (time, level,
process number, thread, logger) cut off. Binding lines start with `...`. Each `count()` call's
own short read-only transaction (13 lines) is replaced by one `...` line. In step 3 only the `>>>`
lines are kept; its log is one `insert` for `meera@example.com` and a commit.

```text
>>> 1. placeTwoOrdersChecked: a checked exception halfway
Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrdersChecked]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(327832021<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@4b4b02d]
Getting transaction for [com.zenith.data.OrderService.placeTwoOrdersChecked]
Found thread-bound EntityManager [SessionImpl(327832021<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [first@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.OrderService.placeTwoOrdersChecked] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(327832021<open>)]
Closing JPA EntityManager [SessionImpl(327832021<open>)] after transaction
>>>    caught PaymentDeclinedException: card declined after the first order
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
>>> 2. placeTwoOrdersRollbackFor: the same, with rollbackFor
Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrdersRollbackFor]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-com.zenith.data.PaymentDeclinedException
Opened new EntityManager [SessionImpl(374823934<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@60003305]
Getting transaction for [com.zenith.data.OrderService.placeTwoOrdersRollbackFor]
Found thread-bound EntityManager [SessionImpl(374823934<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
... binding parameter (1:VARCHAR) <- [first@example.com]
... binding parameter (2:UUID) <- [aaaaaaaa-0000-0000-0000-000000000005]
... binding parameter (3:INTEGER) <- [1]
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.OrderService.placeTwoOrdersRollbackFor] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(374823934<open>)]
Closing JPA EntityManager [SessionImpl(374823934<open>)] after transaction
>>>    caught PaymentDeclinedException: card declined after the first order
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
>>> 3. one more order, to see which id comes next
>>>    id before save: null
>>>    id after save: 3
>>>    placeOrder returned id 3
```

Afterwards, `select * from ticket_order order by id;` in psql:

```text
 id |               event_id               |  customer_email   | quantity 
----+--------------------------------------+-------------------+----------
  1 | aaaaaaaa-0000-0000-0000-000000000005 | first@example.com |        1
  3 | aaaaaaaa-0000-0000-0000-000000000005 | meera@example.com |        2
(2 rows)
```

### Walkthrough

**Step 1 is the second half of subtask S9.** Its lines, in order:

1. **`Creating new transaction with name [com.zenith.data.OrderService.placeTwoOrdersChecked]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT`.**
   Spring began the transaction. There is no rollback list at the end of the line.
2. **The `insert`, with `[first@example.com]`.** Sent at once; Postgres numbered the row 1.
3. **`>>>    first order saved`**, then the `throw`.
4. **`Completing transaction for [...placeTwoOrdersChecked] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order`.**
   Spring saw the exception leave, just as it saw the `RuntimeException` leave `placeTwoOrders`.
5. **`Initiating transaction commit`, then `Committing JPA transaction ...`.** The surprise: an
   exception left the method, and Spring still committed, because `PaymentDeclinedException` is
   checked.
6. **`>>>    caught PaymentDeclinedException: ...`, then `>>>    orders in the table: 1`.** The
   first write survived the failure.

**Step 2 is the same call with `rollbackFor`.** Its first line ends with
`-com.zenith.data.PaymentDeclinedException`: the rollback list. The same `after exception` line
comes, and this time `Initiating transaction rollback` follows. The count stays at 1.

**The table.** Row 1 is the write from step 1, which survived the checked exception. Number 2 is
missing: step 2's `INSERT` got it, and the rollback removed the row. That is why step 3 got id 3.
The same failure, with the same message, gave a commit in step 1 and a rollback in step 2. The
only difference is `rollbackFor`.

### Important terms

| Term | Meaning |
| --- | --- |
| **`throws` clause** | The part of a method's first line that lists the checked exceptions it may throw. The compiler checks it; Spring's rule doesn't look at it. |
| **Checked exception commits** | Spring's default: a checked exception leaving a `@Transactional` method still ends in `Initiating transaction commit`. |
| **`rollbackFor`** | `@Transactional(rollbackFor = X.class)`: `X` and its child classes roll back too. It adds to the default rule. |
| **`.class`** | Java's way to refer to a class as a value, such as `PaymentDeclinedException.class`. |
| **`-` in the transaction line** | A rollback rule: `-com.zenith.data.PaymentDeclinedException` means "roll back for this exception". Spring's jar calls the sign `PREFIX_ROLLBACK_RULE`. |

### Where it is used

- **Business exceptions.** "Payment declined" or "seat already taken". When creating one, decide
  on purpose: make it unchecked (`extends RuntimeException`), so it rolls back by default, or keep
  it checked and list it in `rollbackFor`.
- **Java's own checked exceptions.** An `IOException` while reading a file inside a
  `@Transactional` method commits by default.
- **Code reviews.** A `@Transactional` method whose `throws` clause names a checked exception is
  worth a second look: is the commit really what you want?
- In this task: the second half of S9, and the answer to CP5.

### Edge cases

- **`rollbackFor` adds to the default; it doesn't replace it.** A temporary service had this
  method:

  ```java
  @Transactional(rollbackFor = PaymentDeclinedException.class)
  public void rollbackForButRuntime(UUID eventId) {
      orders.save(new OrderEntity(eventId, "first@example.com", 1));
      System.out.println(">>>    first order saved");
      throw new IllegalStateException("something else went wrong");
  }
  ```

  The real log, with the front of each line cut off, the `insert` shortened to two lines and the
  binding lines left out. The table had 1 row before:

  ```text
  >>> CASE rollbackFor-but-runtime: orders before: 1
  Creating new transaction with name [com.zenith.data.LessonCheck22Service.rollbackForButRuntime]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-com.zenith.data.PaymentDeclinedException
  Getting transaction for [com.zenith.data.LessonCheck22Service.rollbackForButRuntime]
  Participating in existing transaction
  Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
      insert 
          ticket_order
  Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
  >>>    first order saved
  Completing transaction for [com.zenith.data.LessonCheck22Service.rollbackForButRuntime] after exception: java.lang.IllegalStateException: something else went wrong
  Initiating transaction rollback
  Rolling back JPA transaction on EntityManager [SessionImpl(182755121<open>)]
  Closing JPA EntityManager [SessionImpl(182755121<open>)] after transaction
  >>>    caller caught java.lang.IllegalStateException: something else went wrong
  >>> END rollbackFor-but-runtime: orders after: 1
  ```

  `IllegalStateException` isn't in the list, but it is unchecked, so the default rule still rolled
  it back.
- **The sign for the opposite rule.** `javap -constants` on `RuleBasedTransactionAttribute` in
  `spring-tx-6.2.19.jar` printed both signs:

  ```text
    public static final java.lang.String PREFIX_ROLLBACK_RULE = "-";
    public static final java.lang.String PREFIX_COMMIT_RULE = "+";
  ```

  A `+` in front of a class name would mean "commit for this exception". This task doesn't use
  that kind of rule.
- **`throws` changes nothing at run time.** `placeTwoOrdersChecked` and
  `placeTwoOrdersRollbackFor` have the same `throws PaymentDeclinedException`. One committed and
  the other rolled back; only `rollbackFor` made the difference.

### Common mistakes — with the real errors

1. **Expecting every exception to roll back.** Step 1 of the demo: an exception left the method,
   yet Spring committed, and `first@example.com` stayed in the table. No error message warns you;
   the only clue is `Initiating transaction commit` right after `after exception`.

2. **Forgetting `throws` on the method.** `throws PaymentDeclinedException` was deleted from
   `placeTwoOrdersChecked`, so its first line ended with `{` again. With the demo present, the
   build stopped with an error in the demo:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../CheckedDemo.java:[30,11] exception com.zenith.data.PaymentDeclinedException is never thrown in body of corresponding try statement
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `CheckedDemo.java:[30,11]` | Line 30, column 11: the `catch` in `} catch (PaymentDeclinedException e) {`. |
   | `exception ... is never thrown in body of corresponding try statement` | The compiler looked at the `try` block (line 29). The method no longer promises this checked exception, so catching it is pointless, and for a checked exception that is an error. |

   With the demo moved out (as when the method is written before any caller exists), the error
   was in the method itself:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../OrderService.java:[47,13] unreported exception com.zenith.data.PaymentDeclinedException; must be caught or declared to be thrown
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `OrderService.java:[47,13]` | Line 47, column 13: the `throw`. It moved up one line, because the `throws` line was gone. |
   | `unreported exception com.zenith.data.PaymentDeclinedException` | This line throws a checked exception that the method's first line doesn't report. |
   | `must be caught or declared to be thrown` | Catch it inside the method, or add `throws PaymentDeclinedException` to the method's first line. |

3. **Catching `RuntimeException` around the call.** Line 30 of the demo was changed to
   `} catch (RuntimeException e) {`:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../CheckedDemo.java:[29,42] unreported exception com.zenith.data.PaymentDeclinedException; must be caught or declared to be thrown
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `CheckedDemo.java:[29,42]` | Line 29, column 42: the `(` of `placeTwoOrdersChecked(SUFI_NIGHT, true)`. |
   | `unreported exception com.zenith.data.PaymentDeclinedException` | The call can throw the checked exception, and nothing here handles it. |
   | `must be caught or declared to be thrown` | `catch (RuntimeException e)` doesn't cover it: `PaymentDeclinedException`'s family line has no `RuntimeException`. |

4. **No quotes around the log settings.** Maven stops at once with
   `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`.
   One pair of quotes around all the settings fixes it.

### Link to the task

Subtask S9 says: "by default Spring rolls back on unchecked exceptions and **commits** on checked
ones. Test it. Throw a `RuntimeException` halfway through a two-write method and confirm both
writes vanished. Then throw a checked exception and watch the first write survive."

- **The `RuntimeException` half** was `placeTwoOrders`: its `INSERT` was rolled back, and the
  table had no row from that call.
- **"Throw a checked exception":** step 1, `placeTwoOrdersChecked`, threw
  `PaymentDeclinedException` halfway.
- **"Watch the first write survive":** `Initiating transaction commit` came right after the
  exception, the count became 1, and psql shows row 1, `first@example.com`.

S9 is done. It is also the "show a rollback and a non-rollback" part of the task's "Done when".

**The answer to CP5.** Unchecked exceptions roll back by default: `RuntimeException`, `Error`,
and every class whose family line has one of them. Checked exceptions commit by default: every
other child of `Exception`, such as `PaymentDeclinedException`, so writes made before the `throw`
stay. `rollbackFor` moves a checked exception to the rollback side, for the listed classes and
their children.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `OrderService.java` has 40 lines, ending with `placeTwoOrders`, and `RollbackDemo.java` exists;
- `ticket_order` is empty, with its numbering restarted at 1;
- Postgres runs in the Docker container `pg`.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the checked exception.** Create
`scratch-app/src/main/java/com/zenith/data/PaymentDeclinedException.java` with the 9 lines from
the example.

**Step 3 — add the two methods.** In `OrderService.java`, before the last `}`, add a blank line
and lines 41 to 65 from the example. The file then matches the 66 lines in the example.

**Step 4 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/CheckedDemo.java`
with the 47 lines from the example.

**Step 5 — run it.** `grep -E` keeps only the lines that contain one of the five patterns:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=checked -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|insert|after exception|transaction rollback|transaction commit'
```

Real output. The start of the long lines (time, process number, logger) will differ on your
laptop:

```text
>>> 1. placeTwoOrdersChecked: a checked exception halfway
    insert 
>>>    first order saved
2026-11-03T03:49:29.747+05:30 TRACE 87716 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.OrderService.placeTwoOrdersChecked] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
2026-11-03T03:49:29.747+05:30 DEBUG 87716 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    caught PaymentDeclinedException: card declined after the first order
2026-11-03T03:49:29.784+05:30 DEBUG 87716 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 1
>>> 2. placeTwoOrdersRollbackFor: the same, with rollbackFor
    insert 
>>>    first order saved
2026-11-03T03:49:29.788+05:30 TRACE 87716 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.OrderService.placeTwoOrdersRollbackFor] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
2026-11-03T03:49:29.788+05:30 DEBUG 87716 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
>>>    caught PaymentDeclinedException: card declined after the first order
2026-11-03T03:49:29.790+05:30 DEBUG 87716 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 1
>>> 3. one more order, to see which id comes next
>>>    id before save: null
    insert 
>>>    id after save: 3
2026-11-03T03:49:29.793+05:30 DEBUG 87716 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    placeOrder returned id 3
```

In step 1 of the output, `after exception` is followed by `Initiating transaction commit`; in
step 2, the same exception is followed by `Initiating transaction rollback`. The extra commit
after each `caught` line is `count()`'s own short transaction.

**Step 6 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select * from ticket_order order by id;"
```

Expected: the two rows from the example: id 1 for `first@example.com` and id 3 for
`meera@example.com`, ending with `(2 rows)`. There is no row 2.

**Step 7 — break it: remove `throws`.** In `OrderService.java`, delete line 44
(`            throws PaymentDeclinedException {`) and put ` {` at the end of line 43. Run
`mvn -q spring-boot:run -Dspring-boot.run.profiles=checked`. The build stops with this, with your
full path in place of `...`:

```text
[ERROR] COMPILATION ERROR : 
[ERROR] .../CheckedDemo.java:[30,11] exception com.zenith.data.PaymentDeclinedException is never thrown in body of corresponding try statement
```

Put lines 43 and 44 back exactly as in the example.

**Step 8 — break the caller.** In `CheckedDemo.java`, change line 30 to
`        } catch (RuntimeException e) {`. Run
`mvn -q spring-boot:run -Dspring-boot.run.profiles=checked`. The build stops with:

```text
[ERROR] COMPILATION ERROR : 
[ERROR] .../CheckedDemo.java:[29,42] unreported exception com.zenith.data.PaymentDeclinedException; must be caught or declared to be thrown
```

Change line 30 back to `        } catch (PaymentDeclinedException e) {`.

**Step 9 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- Step 1 of the output shows `Initiating transaction rollback` and a count of 0:
  `placeTwoOrdersChecked` must have plain `@Transactional`, with no `rollbackFor`, and
  `PaymentDeclinedException` must say `extends Exception`, not `extends RuntimeException`.
- Step 2 of the output shows `Initiating transaction commit` right after `after exception`, and
  the count becomes 2: the `rollbackFor = PaymentDeclinedException.class` on line 55 is missing.
- The ids are higher than 1 and 3: the table wasn't empty with its numbering restarted. Run
  step 9, then step 5 again.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- step 1 of the output showed
  `after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order`
  followed by `Initiating transaction commit`, and the count became 1;
- step 2 showed the same exception followed by `Initiating transaction rollback`, and the count
  stayed at 1;
- psql showed ids 1 and 3, with no 2;
- you have seen both compile errors and put the code back;
- the table is empty again.

### Summary for revision

- By default, a checked exception that leaves a `@Transactional` method ends in a **commit**:
  writes made before the `throw` stay.
- The log shows it: `after exception: ...` followed by `Initiating transaction commit`.
- `@Transactional(rollbackFor = X.class)` makes `X` and its child classes roll back. The
  transaction line then ends with `-X`.
- `rollbackFor` adds to the default; unchecked exceptions still roll back.
- `throws` is for the compiler; it doesn't change commit or rollback.
- The compiler errors for checked exceptions: `unreported exception X; must be caught or declared
  to be thrown`, and `exception X is never thrown in body of corresponding try statement`.
- CP5: unchecked rolls back, checked commits, unless listed in `rollbackFor`.

### Practice questions, with answers

**Question 1.** `PaymentDeclinedException` is a checked exception:
`public class PaymentDeclinedException extends Exception`. A temporary Spring bean, a service with
an `OrderRepository orders` field, has the two methods below. `new OrderEntity(eventId, email,
quantity)` builds a new order, and `save()` sends its `INSERT` at once. `java.io.IOException` is a
checked exception from Java itself, and `PaymentDeclinedException` is not a child of it. The table
is empty before each call, and the caller catches the exception.

```java
@Transactional(rollbackFor = Exception.class)
public void rollbackForException(UUID eventId) throws PaymentDeclinedException {
    orders.save(new OrderEntity(eventId, "first@example.com", 1));
    System.out.println(">>>    first order saved");
    throw new PaymentDeclinedException("card declined after the first order");
}

@Transactional(rollbackFor = java.io.IOException.class)
public void rollbackForIOException(UUID eventId) throws PaymentDeclinedException {
    orders.save(new OrderEntity(eventId, "first@example.com", 1));
    System.out.println(">>>    first order saved");
    throw new PaymentDeclinedException("card declined after the first order");
}
```

For each method: does Spring commit or roll back, and how many rows are in the table afterwards?
Say why.

**Answer.** Both were run for real, in a temporary service with exactly these methods. The log,
with the front of each line cut off, each `insert` shortened to two lines, and the binding lines
and `count()`'s transactions left out:

```text
>>> CASE rollbackFor-Exception: orders before: 0
Creating new transaction with name [com.zenith.data.LessonCheck22Service.rollbackForException]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-java.lang.Exception
Getting transaction for [com.zenith.data.LessonCheck22Service.rollbackForException]
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.LessonCheck22Service.rollbackForException] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(345758542<open>)]
Closing JPA EntityManager [SessionImpl(345758542<open>)] after transaction
>>>    caller caught com.zenith.data.PaymentDeclinedException: card declined after the first order
>>> END rollbackFor-Exception: orders after: 0
>>> CASE rollbackFor-IOException: orders before: 0
Creating new transaction with name [com.zenith.data.LessonCheck22Service.rollbackForIOException]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-java.io.IOException
Getting transaction for [com.zenith.data.LessonCheck22Service.rollbackForIOException]
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.LessonCheck22Service.rollbackForIOException] after exception: com.zenith.data.PaymentDeclinedException: card declined after the first order
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(361642617<open>)]
Closing JPA EntityManager [SessionImpl(361642617<open>)] after transaction
>>>    caller caught com.zenith.data.PaymentDeclinedException: card declined after the first order
>>> END rollbackFor-IOException: orders after: 1
```

- **`rollbackForException`: rollback, 0 rows.** `rollbackFor = Exception.class` covers
  `Exception` and every child of it. `PaymentDeclinedException`'s family line is
  `PaymentDeclinedException -> Exception -> Throwable -> Object`, so it matches. The transaction
  line ends with `-java.lang.Exception`. Many teams write `rollbackFor = Exception.class` so that
  every exception rolls back.
- **`rollbackForIOException`: commit, 1 row.** The list covers only `IOException` and its
  children, and `PaymentDeclinedException` isn't one of them. So the default rule decides: a
  checked exception, so commit. The first write stayed, even though the method has a
  `rollbackFor`.

**Question 2.** The same service has this method. `chargeCard()` is a private method in the same
class, and it always throws `PaymentDeclinedException`. The table has 1 row before the call. The
caller wraps the call in `try { ... } catch (Exception e) { ... }` and prints the exception's
class name, its message, and its cause.

```java
@Transactional
public void wrapAndRethrow(UUID eventId) {
    orders.save(new OrderEntity(eventId, "first@example.com", 1));
    System.out.println(">>>    first order saved");
    try {
        chargeCard();
    } catch (PaymentDeclinedException e) {
        throw new IllegalStateException("payment step failed", e);
    }
    orders.save(new OrderEntity(eventId, "second@example.com", 1));
    System.out.println(">>>    second order saved");
}

private void chargeCard() throws PaymentDeclinedException {
    throw new PaymentDeclinedException("card declined after the first order");
}
```

- (a) Does `>>>    second order saved` print?
- (b) Which exception reaches the caller, and what is its cause?
- (c) How many rows are in the table afterwards? Say why.

**Answer.** It was run for real, right after Question 1, so the table had the 1 row that
`rollbackForIOException` committed. The log, cut the same way:

```text
>>> CASE wrap-and-rethrow: orders before: 1
Creating new transaction with name [com.zenith.data.LessonCheck22Service.wrapAndRethrow]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Getting transaction for [com.zenith.data.LessonCheck22Service.wrapAndRethrow]
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
    insert 
        ticket_order
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    first order saved
Completing transaction for [com.zenith.data.LessonCheck22Service.wrapAndRethrow] after exception: java.lang.IllegalStateException: payment step failed
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(2138655503<open>)]
Closing JPA EntityManager [SessionImpl(2138655503<open>)] after transaction
>>>    caller caught java.lang.IllegalStateException: payment step failed
>>>    cause: com.zenith.data.PaymentDeclinedException: card declined after the first order
>>> END wrap-and-rethrow: orders after: 1
```

- (a) **No.** The `throw new IllegalStateException(...)` inside the `catch` block ends the method.
  The second `save()` and its `println` never run.
- (b) **`java.lang.IllegalStateException: payment step failed`**, with the cause
  **`com.zenith.data.PaymentDeclinedException: card declined after the first order`**. The second
  argument, `e`, keeps the original exception inside the new one, so the real reason isn't lost.
- (c) **1 row**, the one from before; this call's first order was undone. What counts is the class
  of the exception that *leaves* the method: here `IllegalStateException`, which is unchecked, so
  Spring rolled back (`after exception: java.lang.IllegalStateException`, then
  `Initiating transaction rollback`). Where the trouble started doesn't matter. Wrapping a checked
  exception in an unchecked one is a common way to get a rollback without `rollbackFor`.

(After the checks, `truncate ticket_order restart identity;` emptied the table again.)

---

## Lesson 23 — The proxy around your bean (prepares subtasks S10 and S11)

### Definition

Ideas you need again, in short:

- A **bean** is an object that Spring makes when the app starts and hands to every constructor
  that asks for its type. `@Service` or `@Component` on a class tells Spring to make one.
- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** keeps every change; a **rollback** undoes every change. With `@Transactional` on a
  method, Spring begins a transaction before the method runs and commits it after the method
  returns. If an unchecked exception (a `RuntimeException`, or a class that extends it) comes out,
  Spring rolls back instead.
- A **proxy** is a stand-in object. Your code calls the proxy; the proxy runs some extra steps,
  then calls the same method on the real object behind it, the **target**. The object Spring
  passes in for `EventRepository` is one: its `getClass()` printed `jdk.proxy2.$Proxy103`, a class
  made by Java's built-in `Proxy` feature. That feature only makes classes for interfaces.
- `getClass()` always tells the truth about an object's class. `println(object)` may not, because
  a proxy passes `toString()` on to its target.
- A **stack trace** is the list of method calls that are running at one moment. The newest is at
  the top, and each line was called by the line below it.

New words:

- **Subclass.** A class that extends another one, like `class B extends A`. A `B` object can go
  anywhere an `A` is needed. `B` can **override** a method of `A`: it gives its own version, which
  runs instead of `A`'s.
- **`final`.** On a class, it means no class may extend this one. On a method, it means no subclass
  may override this method.
- **CGLIB.** A tool that writes new Java classes while the program runs. Spring carries its own
  copy inside its jar, in the package `org.springframework.cglib`.
- **Class-based proxy.** A proxy whose class is a subclass of your class, written by CGLIB at
  startup. Its name looks like `CheckoutService$$SpringCGLIB$$0`. The task calls it "the wrapper".
- **Handler.** The Spring method that every method of the proxy hands the call to. For a
  class-based proxy, it is `intercept` in the class `CglibAopProxy$DynamicAdvisedInterceptor`.
- **Extra step.** Code that runs around your method. The transaction step is
  `TransactionInterceptor`: it begins the transaction before your method and commits or rolls back
  after it.
- **AOP.** Short for "aspect-oriented programming", Spring's name for this whole idea of adding
  extra steps around methods. It appears in class names such as `AopUtils` and `CglibAopProxy`.
- **`this`.** Inside a method, `this` means the object the method is running on.
- **Object number.** `System.identityHashCode(x)` gives a number for the object `x`. Two different
  objects almost always get different numbers, so it shows whether two printouts are about the
  same object.

The two objects behind one bean, from the real run:

| | The proxy | The target |
| --- | --- | --- |
| Class | `com.zenith.data.CheckoutService$$SpringCGLIB$$0` | `com.zenith.data.CheckoutService` |
| Made by | CGLIB writes the class; Spring makes the object without running your constructor | Spring, with `new CheckoutService(orders)` |
| Who holds it | Every constructor that asks for a `CheckoutService` | Only the proxy |
| Object number in the run | 1402302648 | 1920110923, the same as `this` inside `persistOrder` |

### Why it is needed

- **`@Transactional` is only a label.** Java does nothing with a label on its own. Some code must
  begin the transaction before the method and commit it after, and that code lives in the proxy.
  A call that doesn't go through the proxy gets nothing from the label.
- **A service is a class, not an interface.** Java's built-in `Proxy` can't make a stand-in for a
  class, so Spring has CGLIB write a subclass of it instead.
- **Subtasks S10 and S11 build on it.** S10 makes `@Transactional` silently do nothing. S11 asks
  for the reason "in terms of the proxy", which needs a clear picture of the proxy first.

### Key points

- Spring doesn't hand out your `CheckoutService` object. It hands out a proxy whose class,
  `CheckoutService$$SpringCGLIB$$0`, is a subclass of yours, written in memory at startup.
- The real object, the target, sits inside the proxy. One bean, two objects.
- A call from outside goes through your code, then the proxy's override, then the handler, then
  the transaction step, and only then reaches your method on the target.
- The transaction begins before the first line of your method and commits after it returns.
- Inside your own method, `this` is the target, not the proxy.
- Spring makes a proxy only for a bean that needs extra steps, such as one with a
  `@Transactional` method.
- A `final` class can't get a proxy, so the app doesn't start. A `final` method can't be
  overridden, so a call to it never reaches the handler.
- An object you make yourself with `new` has no proxy at all.

### How it works, step by step

**At startup**, for `CheckoutService`:

1. **Spring makes the target.** It runs `new CheckoutService(orders)`, so your constructor runs
   once.
2. **Spring checks whether the bean needs a proxy.** `persistOrder` has `@Transactional`, so it
   needs the transaction step. The Spring method that decides is called `wrapIfNecessary`.
3. **CGLIB writes the proxy class** in memory: `CheckoutService$$SpringCGLIB$$0`, which extends
   `CheckoutService` and overrides every public method. Each override does one thing: it hands
   the call to the handler.
4. **Spring makes one object of that class, the proxy, and stores the target inside it.** It
   doesn't run your constructor for the proxy, so the proxy's own `orders` field stays `null`. That
   is normally harmless, because the proxy passes every call on to the target.
5. **Spring hands out the proxy** to every constructor that asks for a `CheckoutService`. That
   works because the proxy's class is a subclass of `CheckoutService`.

**On each call**, such as `checkout.persistOrder(...)`:

1. Your code calls the method on the proxy, so the override in `CheckoutService$$SpringCGLIB$$0`
   runs.
2. The override hands the call to the handler.
3. The handler looks up the extra steps for this method. For `persistOrder` there is one: the
   transaction step. For a method with no extra steps, the handler calls the target directly.
4. The transaction step begins the transaction, then calls on.
5. Your `persistOrder` runs **on the target**, so `this` inside it is the target, not the proxy.
6. When your method returns, the transaction step commits. If an unchecked exception comes out,
   it rolls back.

### Example

Three new files, all in `scratch-app/src/main/java/com/zenith/data/`.

`CallPath.java` (20 lines) prints the calls that are running right now.
`Thread.currentThread().getStackTrace()` returns the same list a stack trace shows, but without
any error. The full list is about 30 lines, mostly Java's and Spring Boot's own code, so the `if`
keeps three kinds of lines: your own classes, the handler, and the transaction step.

```java
package com.zenith.data;

// Lesson 23: prints the calls that are running right now, newest first. It keeps only your own
// classes, the proxy's handler, and the transaction step. The full list is about 30 lines.
class CallPath {

    static void print() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        System.out.println(">>>    call path, newest first:");
        // frames[0] is getStackTrace itself and frames[1] is this method, so start at 2.
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.zenith")
                    || className.endsWith("DynamicAdvisedInterceptor")
                    || className.endsWith("TransactionInterceptor")) {
                System.out.println(">>>      " + frames[i]);
            }
        }
    }
}
```

`CheckoutService.java` (31 lines) is a small service with one `@Transactional` method.
`TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
is open for the code that's running right now.

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 23: a small service for the proxy lessons. It has one @Transactional method.
@Service
public class CheckoutService {

    private final OrderRepository orders;

    public CheckoutService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 23: one write in a transaction. It also prints who "this" is, whether a
    // transaction is open, and the path the call took to get here.
    @Transactional
    public void persistOrder(UUID eventId, String customerEmail) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
    }
}
```

`ProxyDemo.java` (39 lines) looks at the object Spring passed in, then calls `persistOrder` on it.
`AopUtils.isAopProxy(x)` returns `true` when `x` is a Spring proxy.
`AopProxyUtils.getSingletonTarget(x)` returns the target stored inside the proxy `x`.

```java
package com.zenith.data;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 23: what object did Spring pass in for CheckoutService, and which way does a call go?
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=proxy
@Component
@Profile("proxy")
class ProxyDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;

    ProxyDemo(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @Override
    public void run(String... args) {
        Object inside = AopProxyUtils.getSingletonTarget(checkout);
        System.out.println(">>> 1. the object Spring passed in for CheckoutService");
        System.out.println(">>>    its class:        " + checkout.getClass().getName());
        System.out.println(">>>    its parent class: " + checkout.getClass().getSuperclass().getName());
        System.out.println(">>>    is a proxy:       " + AopUtils.isAopProxy(checkout));
        System.out.println(">>>    its number:       " + System.identityHashCode(checkout));
        System.out.println(">>>    object inside it: " + inside.getClass().getName()
                + ", number " + System.identityHashCode(inside));

        System.out.println(">>> 2. calling checkout.persistOrder(...) from outside");
        checkout.persistOrder(SUFI_NIGHT, "meera@example.com");
    }
}
```

The command, with the transaction log and `TransactionInterceptor`'s trace log inside one pair of
quotes. The table was empty, with its numbering at 1:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=proxy -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line (time, level, process
number, thread, logger) cut off:

```text
>>> 1. the object Spring passed in for CheckoutService
>>>    its class:        com.zenith.data.CheckoutService$$SpringCGLIB$$0
>>>    its parent class: com.zenith.data.CheckoutService
>>>    is a proxy:       true
>>>    its number:       1402302648
>>>    object inside it: com.zenith.data.CheckoutService, number 1920110923
>>> 2. calling checkout.persistOrder(...) from outside
Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1737665086<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@7f680a9a]
Getting transaction for [com.zenith.data.CheckoutService.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 1920110923
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:27)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.ProxyDemo.run(ProxyDemo.java:37)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(1737665086<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.CheckoutService.persistOrder]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1737665086<open>)]
Closing JPA EntityManager [SessionImpl(1737665086<open>)] after transaction
```

The object numbers change from run to run. A second run printed 575233839 for the proxy, and
960704936 for both the object inside it and `this`. What matters is which numbers match.

Afterwards, `select id, customer_email, quantity from ticket_order order by id;` in psql:

```text
 id |  customer_email   | quantity 
----+-------------------+----------
  1 | meera@example.com |        1
(1 row)
```

### Walkthrough

**Part 1 — the object Spring passed in.**

| Output | What it tells you |
| --- | --- |
| `its class: com.zenith.data.CheckoutService$$SpringCGLIB$$0` | Not your class. CGLIB wrote it in memory at startup. `target/classes` holds only `CheckoutService.class`; no file holds the proxy class. |
| `its parent class: com.zenith.data.CheckoutService` | The proxy's class extends yours. That is why Spring can pass it to a constructor that asks for a `CheckoutService`. |
| `is a proxy: true` | Spring confirms it. |
| `its number: 1402302648` | The proxy's object number. |
| `object inside it: com.zenith.data.CheckoutService, number 1920110923` | The target: a plain `CheckoutService`, and a different object, because the number differs. |

So there are two objects: the proxy (1402302648), and the target inside it (1920110923).

**Part 2 — one call from outside**, in order:

1. **`Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]`** and
   **`Getting transaction for [com.zenith.data.CheckoutService.persistOrder]`** come before the
   first `>>>` line printed inside `persistOrder`. The transaction began before any line of the
   method ran. The method has no code that begins a transaction; the proxy did it.
2. **`this is: com.zenith.data.CheckoutService, number 1920110923`.** That is the target's number,
   not the proxy's 1402302648. The method runs on the target.
3. **`transaction open: true`.**
4. **The call path**, read from the bottom up:

   | # | Line | What it is |
   | --- | --- | --- |
   | 1 | `DataApplication.main(DataApplication.java:10)` | Where the app starts. |
   | 2 | `ProxyDemo.run(ProxyDemo.java:37)` | Your code. Line 37 is `checkout.persistOrder(SUFI_NIGHT, "meera@example.com");`. |
   | 3 | `CheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)` | The call lands on the proxy's override. `<generated>` stands where a file name and line number normally go: no source file exists, because CGLIB wrote this class. |
   | 4 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)` | The handler. Line 728 is its branch for "this method has extra steps, so run them". |
   | 5 | `TransactionInterceptor.invoke(TransactionInterceptor.java:119)` | The transaction step. It has already begun the transaction. |
   | 6 | `CheckoutService.persistOrder(CheckoutService.java:27)` | Your method, running on the target. Line 27 is `CallPath.print();`. |

5. **`Participating in existing transaction`.** The `save()` call joined the open transaction.
   Then come the `insert` and `>>>    order saved`.
6. **`Completing transaction for [com.zenith.data.CheckoutService.persistOrder]`**, then
   **`Initiating transaction commit`** and **`Committing JPA transaction`**. The method had
   returned, so the transaction step committed. psql shows the row.

### Important terms

| Term | Meaning |
| --- | --- |
| **Proxy** | A stand-in object. It runs extra steps, then calls the same method on the target. |
| **Target** | The real object behind a proxy. For `CheckoutService`, a plain `CheckoutService` made with your constructor. |
| **Class-based proxy** | A proxy whose class is a generated subclass of your class, such as `CheckoutService$$SpringCGLIB$$0`. Spring uses it for classes. |
| **JDK dynamic proxy** | The other kind: a class made by Java's built-in `Proxy`, such as `jdk.proxy2.$Proxy103`. It works only for interfaces; Spring Data uses it for repositories. |
| **CGLIB** | The tool, carried inside Spring's jar, that writes the subclass at startup. |
| **Handler** | `CglibAopProxy$DynamicAdvisedInterceptor.intercept`. Every method of the proxy hands the call to it. |
| **Extra step** | Code that runs around your method. The transaction step is `TransactionInterceptor`. |
| **`<generated>`** | What a stack trace shows, in place of a file name and line number, for a class that CGLIB wrote. |
| **`wrapIfNecessary`** | The Spring method that decides, at startup, whether a bean gets a proxy. |
| **AOP** | Aspect-oriented programming: Spring's name for adding extra steps around methods. |

### Where it is used

- **Every bean with `@Transactional`**, in every Spring app, gets a class-based proxy like this
  one.
- **Other Spring labels that add code around a method** work the same way, for example
  `@Cacheable`, which remembers a method's results, and `@Async`, which runs a method in the
  background.
- **Two kinds of proxy.** An interface such as `EventRepository` gets Java's built-in proxy. A
  class such as `CheckoutService` gets a CGLIB subclass.
- **Reading stack traces at work.** Lines with `$$SpringCGLIB$$`, `CglibAopProxy` and
  `TransactionInterceptor` are the proxy doing its job. Skip past them to find your own code.
- In this task: the picture that S10 and S11 rely on, and the "why" part of CP4.

### Edge cases

- **The handler has two branches.** `javap -c -l` on `CglibAopProxy$DynamicAdvisedInterceptor` in
  `spring-aop-6.2.19.jar` showed what its line numbers mean:
  - line 724: the list of extra steps is empty, so call the target directly
    (`AopUtils.invokeJoinpointUsingReflection`);
  - line 728: run the extra steps, which then call the target
    (`ReflectiveMethodInvocation.proceed()`).

  `persistOrder` went through line 728. A public method without `@Transactional` goes through
  line 724, as one of the practice questions below shows.
- **The proxy is made without your constructor.** In a test,
  `System.out.println(">>> CheckoutService constructor ran");` was put inside the constructor. At
  startup it printed once, before `Started DataApplication`: that was for the target. Nothing
  printed for the proxy. It printed a second time only when the test made an object with `new`
  itself.
- **No proxy without a reason.** With `@Transactional` removed from `persistOrder`, the same demo
  printed:

  ```text
  >>> 1. the object Spring passed in for CheckoutService
  >>>    its class:        com.zenith.data.CheckoutService
  >>>    its parent class: java.lang.Object
  >>>    is a proxy:       false
  >>>    its number:       755146854
  ```

  Then it stopped with
  `java.lang.NullPointerException: Cannot invoke "Object.getClass()" because "inside" is null` at
  `ProxyDemo.java:33`. There was no proxy, so `getSingletonTarget` had no target to return and
  gave back `null`.
- **No class file on disk.** `target/classes/com/zenith/data/` holds `CheckoutService.class`, but
  nothing for `CheckoutService$$SpringCGLIB$$0`. That class exists only in memory.

### Common mistakes — with the real errors

1. **A `final` method.** Line 22 was changed to
   `public final void persistOrder(UUID eventId, String customerEmail) {`, and the command from the
   example was run. The real output, trimmed:

   ```text
   >>> 1. the object Spring passed in for CheckoutService
   >>>    its class:        com.zenith.data.CheckoutService$$SpringCGLIB$$0
   >>>    its parent class: com.zenith.data.CheckoutService
   >>>    is a proxy:       true
   >>>    its number:       1402302648
   >>>    object inside it: com.zenith.data.CheckoutService, number 1680837503
   >>> 2. calling checkout.persistOrder(...) from outside
   >>>    inside persistOrder, this is: com.zenith.data.CheckoutService$$SpringCGLIB$$0, number 1402302648
   >>>    transaction open: false
   >>>    call path, newest first:
   >>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:27)
   >>>      com.zenith.data.ProxyDemo.run(ProxyDemo.java:37)
   >>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
   ...
   Application run failed

   java.lang.NullPointerException: Cannot invoke "com.zenith.data.OrderRepository.save(Object)" because "this.orders" is null
   	at com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:28) ~[classes/:na]
   	at com.zenith.data.ProxyDemo.run(ProxyDemo.java:37) ~[classes/:na]
   	...
   ```

   The proxy's class can't override a `final` method, so nothing hands the call to the handler.
   `checkout.persistOrder(...)` ran the original method directly, on the proxy object itself:
   `this` has the proxy's number, 1402302648. There was no handler, no transaction step, and no
   transaction. The proxy was made without your constructor, so its `orders` is `null`.

   | Part | Plain meaning |
   | --- | --- |
   | `java.lang.NullPointerException` | Java tried to use a variable that holds `null`. |
   | `Cannot invoke "com.zenith.data.OrderRepository.save(Object)"` | It was trying to call `save` ... |
   | `because "this.orders" is null` | ... on the `orders` field of `this`, and that field is empty. Here `this` is the proxy, whose fields were never filled. |
   | `CheckoutService.persistOrder(CheckoutService.java:28)` | Line 28: `orders.save(new OrderEntity(eventId, customerEmail, 1));`. |
   | `ProxyDemo.run(ProxyDemo.java:37)`, right below it | Your runner called your method directly. No proxy, handler or transaction lines sit between the two. |

   Spring does notice, but it only says so at DEBUG level. With
   `--logging.level.org.springframework.aop.framework.CglibAopProxy=DEBUG`, the startup log had:

   ```text
   Final method [public final void com.zenith.data.CheckoutService.persistOrder(java.util.UUID,java.lang.String)] cannot get proxied via CGLIB: Calls to this method will NOT be routed to the target instance and might lead to NPEs against uninitialized fields in the proxy instance.
   ```

   "NPE" is short for `NullPointerException`. "Routed to the target instance" means "passed on to
   the target".

2. **A `final` class.** Line 11 was changed to `public final class CheckoutService {`. The app
   stopped while starting, before `ProxyDemo` ran. Trimmed, with your path in place of `...`:

   ```text
   Application run failed

   org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'checkoutService' defined in file [.../CheckoutService.class]: Could not generate CGLIB subclass of class com.zenith.data.CheckoutService: Common causes of this problem include using a final class or a non-visible class
   	...
   Caused by: org.springframework.aop.framework.AopConfigException: Could not generate CGLIB subclass of class com.zenith.data.CheckoutService: Common causes of this problem include using a final class or a non-visible class
   	...
   	at org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator.wrapIfNecessary(AbstractAutoProxyCreator.java:370) ~[spring-aop-6.2.19.jar:6.2.19]
   	at org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator.postProcessAfterInitialization(AbstractAutoProxyCreator.java:319) ~[spring-aop-6.2.19.jar:6.2.19]
   	...
   Caused by: java.lang.IllegalArgumentException: Cannot subclass final class com.zenith.data.CheckoutService
   	at org.springframework.cglib.proxy.Enhancer.generateClass(Enhancer.java:653) ~[spring-core-6.2.19.jar:6.2.19]
   	...
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `Error creating bean with name 'checkoutService'` | Spring couldn't finish making the `CheckoutService` bean. A bean's name is its class name with a small first letter. |
   | `Could not generate CGLIB subclass of class com.zenith.data.CheckoutService` | Spring tried to write the proxy class, a subclass of yours, and failed. |
   | `Common causes ... a final class or a non-visible class` | Spring's own hint. "Non-visible" means a class that Spring isn't allowed to reach. |
   | `wrapIfNecessary` | The Spring method that decides whether a bean needs a proxy ("wrap if necessary"). It said yes, because of `@Transactional`. |
   | `postProcessAfterInitialization` | This runs after the target has been made and set up. So the target exists before the proxy. |
   | `Caused by: ... Cannot subclass final class com.zenith.data.CheckoutService` | The root cause. `final` means no class may extend `CheckoutService`, but the proxy's class must extend it. |

3. **Making the object yourself with `new`.** A temporary runner did this, with the
   `CheckoutService` from the example:

   ```java
   CheckoutService handMade = new CheckoutService(orders);
   handMade.persistOrder(SUFI_NIGHT, "ravi@example.com");
   ```

   The real output, with the front of each log line cut off:

   ```text
   >>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 392605705
   >>>    transaction open: false
   >>>    call path, newest first:
   >>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:27)
   >>>      com.zenith.data.LessonCheck23.run(LessonCheck23.java:38)
   >>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
   Opened new EntityManager [SessionImpl(2078888343<open>)] for JPA transaction
   Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@7719b257]
   Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
   Hibernate: 
       insert 
       into
           ticket_order
           (customer_email, event_id, quantity) 
       values
           (?, ?, ?)
   Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
   Initiating transaction commit
   Committing JPA transaction on EntityManager [SessionImpl(2078888343<open>)]
   Closing JPA EntityManager [SessionImpl(2078888343<open>)] after transaction
   >>>    order saved
   ```

   There is no error, so this mistake is easy to miss. `new` makes a plain `CheckoutService` with
   no proxy around it, so `@Transactional` did nothing: `transaction open: false`, and no proxy
   lines in the call path. The only transaction is the repository's own, around `save()` alone
   (`SimpleJpaRepository` is the class with the repository's real code). It committed before
   `persistOrder` had even finished; with two writes, each would commit on its own. Only beans
   that Spring made get a proxy.

4. **Trusting `println(checkout)`.** The same temporary runner printed `checkout` and its class:

   ```text
   >>>    com.zenith.data.CheckoutService@3321fb65
   >>>    getClass: com.zenith.data.CheckoutService$$SpringCGLIB$$0
   ```

   `println` calls `toString()`, and the proxy passes `toString()` on to the target, so it looks
   like a plain `CheckoutService`. Use `getClass()` to see the proxy.

### Link to the task

Subtask S10 says: "Put two methods in one service: a public `createOrder()` with no annotation
that calls `this.persistOrder()`, where `persistOrder` is `@Transactional`. Throw inside
`persistOrder` and watch the write **stay committed**."

Subtask S11 says: "Explain why, in terms of the proxy: Spring wrapped your bean. Calls from outside
hit the wrapper, which opens the transaction. A call through `this` goes straight to the real
method and never touches the wrapper."

This lesson gives the proxy part of S11, with proof:

- **"Spring wrapped your bean":** the injected object's class is `CheckoutService$$SpringCGLIB$$0`,
  and the real `CheckoutService` sits inside it.
- **"Calls from outside hit the wrapper, which opens the transaction":** the call path goes from
  `ProxyDemo.run` to the proxy, then the handler, then `TransactionInterceptor.invoke`, and only
  then reaches the method. `Creating new transaction ...` came before the method's first line.
- **"A call through `this` goes straight to the real method":** inside `persistOrder`, `this` is
  the target (the numbers matched), and a call on the target never passes through the proxy.
  The `final` method and the `new` object showed what that looks like: no proxy lines, and no
  transaction.

The service S10 needs is already here: `CheckoutService`, with a `@Transactional` `persistOrder`.
Adding `createOrder()`, which calls `this.persistOrder()`, and making `persistOrder` throw, is S10.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `OrderEntity.java` and `OrderRepository.java` exist, and so does the `ticket_order` table;
- `ticket_order` is empty, with its numbering restarted at 1;
- Postgres runs in the Docker container `pg`.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — add the call-path helper.** Create
`scratch-app/src/main/java/com/zenith/data/CallPath.java` with the 20 lines from the example.

**Step 3 — add the service.** Create `scratch-app/src/main/java/com/zenith/data/CheckoutService.java`
with the 31 lines from the example.

**Step 4 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/ProxyDemo.java` with
the 39 lines from the example.

**Step 5 — run it.** `grep -E` keeps only the lines that contain one of the four patterns:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=proxy -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|Creating new transaction|insert|Committing'
```

Real output. The start of the two long lines (time, process number, logger) and the object numbers
will differ on your laptop:

```text
>>> 1. the object Spring passed in for CheckoutService
>>>    its class:        com.zenith.data.CheckoutService$$SpringCGLIB$$0
>>>    its parent class: com.zenith.data.CheckoutService
>>>    is a proxy:       true
>>>    its number:       1402302648
>>>    object inside it: com.zenith.data.CheckoutService, number 1920110923
>>> 2. calling checkout.persistOrder(...) from outside
2026-11-03T04:00:12.737+05:30 DEBUG 88017 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 1920110923
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:27)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.ProxyDemo.run(ProxyDemo.java:37)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
    insert 
>>>    order saved
2026-11-03T04:00:12.766+05:30 DEBUG 88017 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Committing JPA transaction on EntityManager [SessionImpl(1737665086<open>)]
```

Check: the number after `this is:` equals the `object inside it` number, and differs from
`its number`.

**Step 6 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select id, customer_email, quantity from ticket_order order by id;"
```

Expected: one row (id 1, `meera@example.com`, quantity 1), ending with `(1 row)`.

**Step 7 — break it with a `final` method.** In `CheckoutService.java`, change line 22 to
`    public final void persistOrder(UUID eventId, String customerEmail) {`. Run the command from
step 5 again, but without the `| grep ...` part. Near the end you should see
`this is: com.zenith.data.CheckoutService$$SpringCGLIB$$0` with the same number as `its number`,
then `transaction open: false`, a call path of only three lines, and then:

```text
java.lang.NullPointerException: Cannot invoke "com.zenith.data.OrderRepository.save(Object)" because "this.orders" is null
```

Remove `final` from line 22 again.

**Step 8 — break it with a `final` class.** Change line 11 to `public final class CheckoutService {`.
Run the same command, again without the `| grep ...` part. The app stops before any `>>>` line.
Near the end:

```text
Caused by: java.lang.IllegalArgumentException: Cannot subclass final class com.zenith.data.CheckoutService
```

Remove `final` from line 11 again.

**Step 9 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- Step 5 shows `its class: com.zenith.data.CheckoutService`, `its parent class: java.lang.Object`
  and `is a proxy: false`, then
  `java.lang.NullPointerException: Cannot invoke "Object.getClass()" because "inside" is null` at
  `ProxyDemo.java:33`: `@Transactional` is missing from line 21 of `CheckoutService.java`. Without
  it, Spring makes no proxy, so there is no target inside to look up.
- Step 5 shows `NullPointerException ... "this.orders" is null`: `final` is still on line 22.
- Step 5 shows `Cannot subclass final class`: `final` is still on line 11.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- the class line showed `CheckoutService$$SpringCGLIB$$0`, with `CheckoutService` as its parent
  class;
- the number after `this is:` matched the `object inside it` number, not the proxy's;
- the call path showed the proxy, the handler and `TransactionInterceptor.invoke` between
  `ProxyDemo.run` and `CheckoutService.persistOrder`;
- `Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]` came before
  the `this is:` line;
- you have seen both break-it errors and put the code back;
- the table is empty again.

### Summary for revision

- Spring hands out a proxy, not your object: `CheckoutService$$SpringCGLIB$$0`, a subclass of
  `CheckoutService` that CGLIB writes in memory at startup.
- The target, a plain `CheckoutService` made with your constructor, sits inside the proxy.
- A call from outside goes through the proxy's override, the handler and the transaction step,
  and only then reaches your method.
- The transaction begins before your method's first line and commits after it returns.
- Inside your own method, `this` is the target, not the proxy.
- Only beans that need extra steps get a proxy. An object made with `new` never has one.
- A `final` class stops the app with `Cannot subclass final class`. A `final` method skips the
  handler: no transaction, and a `NullPointerException` on the proxy's empty fields.
- `getClass()` shows the proxy; `println` shows the target's name.

### Practice questions, with answers

**Question 1.** You add this class to the scratch app:

```java
package com.zenith.data;

import org.springframework.stereotype.Service;

@Service
public class PlainService {

    public String hello() {
        return "hello";
    }
}
```

A `CommandLineRunner` class gets it through its constructor, as `PlainService plain`. Its `run`
method prints:

```java
System.out.println(">>>    its class:  " + plain.getClass().getName());
System.out.println(">>>    is a proxy: " + AopUtils.isAopProxy(plain));
```

`AopUtils.isAopProxy(x)` returns `true` when `x` is a Spring proxy. What do the two lines print?
For `CheckoutService`, whose `persistOrder` method has `@Transactional`, the same two lines printed
`com.zenith.data.CheckoutService$$SpringCGLIB$$0` and `true`. Why is `PlainService` different?

**Answer.** It was run for real, in a temporary runner. The output:

```text
>>> Q1. PlainService
>>>    its class:  com.zenith.data.PlainService
>>>    is a proxy: false
```

`PlainService` gets no proxy. Spring wraps a bean only when at least one of its methods needs an
extra step, such as the transaction step for `@Transactional`. `PlainService` has nothing like
that, so Spring hands out the real object itself: one object, and `getClass()` shows your own
class. `CheckoutService` is different only because `persistOrder` has `@Transactional`.

**Question 2.** `CheckoutService` is a `@Service` with an `OrderRepository orders` field. Its
method `persistOrder` has `@Transactional`, so Spring makes a proxy for it. You add this method to
the class, right before its last `}`. The new method has no `@Transactional`:

```java
public long countOrders() {
    System.out.println(">>>    transaction open: "
            + TransactionSynchronizationManager.isActualTransactionActive());
    CallPath.print();
    return orders.count();
}
```

`TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
is open for the code that's running right now. `CallPath.print()` prints the calls that are
running right now, newest first. It keeps only lines from your own classes (`com.zenith...`), from
the handler (`CglibAopProxy$DynamicAdvisedInterceptor`), and from the transaction step
(`TransactionInterceptor`). A `CommandLineRunner` class gets the `CheckoutService` bean through its
constructor, as `checkout`, and calls `checkout.countOrders()`.

- (a) What does the `transaction open:` line print?
- (b) Which of these three lines appear in the call path:
  `CheckoutService$$SpringCGLIB$$0.countOrders(<generated>)`,
  `CglibAopProxy$DynamicAdvisedInterceptor.intercept(...)`, `TransactionInterceptor.invoke(...)`?
- (c) Explain (b) in one or two sentences.

**Answer.** It was run for real. The method sat at the end of `CheckoutService`, so
`CallPath.print()` was on line 35, and a temporary runner called it on line 33. The output, with
the front of each log line cut off:

```text
>>> Q2. checkout.countOrders()
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.countOrders(CheckoutService.java:35)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.countOrders(<generated>)
>>>      com.zenith.data.LessonCheck23.run(LessonCheck23.java:33)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
Opened new EntityManager [SessionImpl(1473829090<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@21143041]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]
Hibernate: 
    select
        count(*) 
    from
        ticket_order oe1_0
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1473829090<open>)]
Closing JPA EntityManager [SessionImpl(1473829090<open>)] after transaction
>>>    countOrders returned 0
```

- (a) **`false`.** No transaction was open while `countOrders` ran. The transaction lines after
  the call path belong to `count()`: the repository's own short read-only transaction, which began
  and ended inside that one call.
- (b) The first two appear. **`TransactionInterceptor.invoke` does not.**
- (c) The proxy's class overrides every public method, not only the `@Transactional` ones, so the
  call still landed on `CheckoutService$$SpringCGLIB$$0.countOrders` and went to the handler. The
  handler found no extra steps for `countOrders` and called the target directly. That is why the
  handler's line is 724 here, not 728 as for `persistOrder`.

(After the checks, `truncate ticket_order restart identity;` emptied the table again, and the
temporary method and files were removed.)

---

## Lesson 24 — Break it: self-invocation (finishes subtask S10)

### Definition

Ideas you need again, in short:

- A **bean** is an object that Spring makes when the app starts and hands to every constructor
  that asks for its type.
- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** keeps every change; a **rollback** undoes every change. With `@Transactional` on a
  method, Spring begins a transaction before the method runs and commits it after the method
  returns. If an unchecked exception (a `RuntimeException`, or a class that extends it) comes out,
  Spring rolls back instead.
- A **proxy** is a stand-in object. For a bean with a `@Transactional` method, Spring doesn't
  hand out your object. It hands out a proxy whose class, `CheckoutService$$SpringCGLIB$$0`, is a
  subclass of yours, written in memory at startup. Your real object, the **target**, sits inside
  it.
- A call from outside lands on the proxy. The proxy passes it to its **handler**
  (`CglibAopProxy$DynamicAdvisedInterceptor.intercept`). The handler runs the **transaction step**
  (`TransactionInterceptor`), which begins the transaction and then calls your method on the
  target.
- Inside your own method, **`this`** (the object the method is running on) is the target, not the
  proxy.
- A repository's `save()` has its own `@Transactional`. If a transaction is already open, `save()`
  joins it (`Participating in existing transaction`). If not, it begins its own short one and
  commits it before returning.

New words:

- **Self-invocation.** A method of an object calling another method of the same object, through
  `this`. "Self" means "the same object"; "invocation" means "a call".
- **The self-invocation trap.** When that second method has `@Transactional`, the call skips the
  proxy, so the label does nothing: no transaction and no rollback. There is no error and no
  warning.

The same `persistOrder`, reached by two routes, from the real run:

| | Step 1: `checkout.persistOrder(...)` from outside | Step 2: `checkout.createOrder(...)`, which calls `this.persistOrder(...)` |
| --- | --- | --- |
| Who calls `persistOrder` | `SelfInvocationDemo.run`, through the proxy | `createOrder`, on the target |
| Right below `persistOrder` in the call path | `TransactionInterceptor.invoke`, then the handler (line 728), then the proxy | `CheckoutService.createOrder` |
| `transaction open:` | `true` | `false` |
| Who begins a transaction | the transaction step, for `CheckoutService.persistOrder` | only `save()`, for itself |
| After the exception | rolled back | nothing to roll back: `save()` had already committed |
| Orders in the table after | 0 | 1 |

### Why it is needed

- **Subtask S10 asks for it.** The task calls it "a favourite interview question".
- **It fails silently.** The code looks right, and the label is right there. There is no error and
  no warning. Only the data shows the problem: half-finished work stays in the database.
- **It is common in real code.** A method in a service often calls a helper method in the same
  service. When the helper is the one with `@Transactional`, it gets no transaction.
- **Subtask S11 and checkpoint CP4 build on it.** They ask why it happens and how to fix it.

### Key points

- A call from one method of a bean to another method of the same bean goes through `this`, the
  target. It never reaches the proxy.
- So the `@Transactional` on the called method is never read: no transaction begins, and nothing
  can be rolled back.
- Repository calls inside it then run in their own short transactions, and each commits at once.
- An exception afterwards still reaches the caller, but the write stays committed.
- There is no error or warning. The only signs are missing lines in the transaction log, and the
  wrong data.
- Leaving out the word `this.` changes nothing: Java adds it for you.
- Changing the label's settings, such as `rollbackFor`, changes nothing either: on this route,
  nobody reads the label.

### How it works, step by step

**Step 1 — `checkout.persistOrder(...)` from outside.** `checkout` holds the proxy.

1. The call lands on the proxy's override of `persistOrder`.
2. The handler finds the transaction step for `persistOrder` and runs it (handler line 728).
3. The transaction step begins a transaction, then calls `persistOrder` on the target.
4. `save()` joins that transaction, and the `insert` runs.
5. `persistOrder` throws a `RuntimeException`. The transaction step sees it come out and rolls
   back, so the `insert` is undone.

**Step 2 — `checkout.createOrder(...)`.** `checkout` holds the same proxy.

1. The call lands on the proxy's override of `createOrder`. The proxy overrides every public
   method, not only the labelled ones.
2. The handler finds no extra steps for `createOrder`, because it has no label. So it calls
   `createOrder` on the target directly (handler line 724).
3. `createOrder` runs on the target. Its line `this.persistOrder(...)` is a plain Java call on
   the target. Java just runs the method; it never looks at labels. The proxy is a different
   object, so it never hears about this call.
4. `persistOrder` runs with no transaction open. Its `@Transactional` is never read.
5. `save()` finds no transaction, so it begins its own, runs the `insert`, and commits before
   returning. The row is now in the database for good.
6. `persistOrder` throws. The exception passes back through `createOrder` and the proxy, but no
   transaction step is on this route, and `save()`'s transaction is already over.

### Example

All files are in `scratch-app/src/main/java/com/zenith/data/`.

`CheckoutService.java` (42 lines) gets a second public method, `createOrder`, with no label. It
calls `this.persistOrder(...)`. `persistOrder` gets a third parameter, `failAfterSave`. When it is
`true`, an unchecked exception comes right after the save, like a payment step that fails after
the order was written.

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 23: a small service for the proxy lessons. It has one @Transactional method.
@Service
public class CheckoutService {

    private final OrderRepository orders;

    public CheckoutService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 24: no annotation. It calls persistOrder on "this", which is the target, not the proxy.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        this.persistOrder(eventId, customerEmail, failAfterSave);
    }

    // Lesson 23: one write in a transaction. It also prints who "this" is, whether a
    // transaction is open, and the path the call took to get here.
    // Lesson 24: with failAfterSave, an unchecked exception comes right after the save.
    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}
```

`ProxyDemo.java` called `persistOrder` with two values, so it no longer compiles. Its line 37 gets
`false` as the third value:

```java
        checkout.persistOrder(SUFI_NIGHT, "meera@example.com", false);
```

`CallPath.java` (20 lines) is unchanged. It prints the calls that are running right now, newest
first. `Thread.currentThread().getStackTrace()` returns that list, and the `if` keeps three kinds
of lines: your own classes, the handler, and the transaction step.

```java
package com.zenith.data;

// Lesson 23: prints the calls that are running right now, newest first. It keeps only your own
// classes, the proxy's handler, and the transaction step. The full list is about 30 lines.
class CallPath {

    static void print() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        System.out.println(">>>    call path, newest first:");
        // frames[0] is getStackTrace itself and frames[1] is this method, so start at 2.
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.zenith")
                    || className.endsWith("DynamicAdvisedInterceptor")
                    || className.endsWith("TransactionInterceptor")) {
                System.out.println(">>>      " + frames[i]);
            }
        }
    }
}
```

`SelfInvocationDemo.java` (44 lines) calls the failing `persistOrder` two ways: straight from
outside, then through `createOrder`. Each call sits in a `try`/`catch`, so the demo carries on and
prints the number of orders after each one.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 24: the same failing persistOrder, called two ways: straight from outside, and through
// createOrder, which calls this.persistOrder.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=self-invocation
@Component
@Profile("self-invocation")
class SelfInvocationDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;
    private final OrderRepository orders;

    SelfInvocationDemo(CheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. checkout.persistOrder(...) from outside, failing after the save");
        try {
            checkout.persistOrder(SUFI_NIGHT, "first@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. checkout.createOrder(...), which calls this.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "second@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
```

The command, with the transaction log and `TransactionInterceptor`'s trace log inside one pair of
quotes. The table was empty, with its numbering at 1:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=self-invocation -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line (time, level, process
number, thread, logger) cut off. Each `orders.count()` call has its own short read-only transaction
of 13 lines; each one is folded into a single line here:

```text
>>> 1. checkout.persistOrder(...) from outside, failing after the save
Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(2047373618<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@6f45a2fd]
Getting transaction for [com.zenith.data.CheckoutService.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.SelfInvocationDemo.run(SelfInvocationDemo.java:30)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(2047373618<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.CheckoutService.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(2047373618<open>)]
Closing JPA EntityManager [SessionImpl(2047373618<open>)] after transaction
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 0
>>> 2. checkout.createOrder(...), which calls this.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      com.zenith.data.CheckoutService.createOrder(CheckoutService.java:23)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)
>>>      com.zenith.data.SelfInvocationDemo.run(SelfInvocationDemo.java:38)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(630605810<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@54c9c09c]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(630605810<open>)]
Closing JPA EntityManager [SessionImpl(630605810<open>)] after transaction
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
```

Afterwards, `select id, customer_email, quantity from ticket_order order by id;` in psql:

```text
 id |   customer_email   | quantity 
----+--------------------+----------
  2 | second@example.com |        1
(1 row)
```

### Walkthrough

**Step 1 — from outside: rolled back.**

1. **`Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]`** comes
   before the first `>>>` line printed inside `persistOrder`. The transaction step began it.
2. **`transaction open: true`.** The call path has the proxy, the handler (line 728) and
   `TransactionInterceptor.invoke` between `SelfInvocationDemo.run` and `persistOrder`.
3. **`Participating in existing transaction`.** `save()` joined the open transaction. Then come the
   `insert` and `>>>    order saved`.
4. **`Completing transaction for [com.zenith.data.CheckoutService.persistOrder] after exception:
   java.lang.RuntimeException: payment failed after the order was saved`**, then
   **`Initiating transaction rollback`**. The transaction step saw the exception and undid the
   `insert`.
5. **`orders in the table: 0`.**

**Step 2 — through `this`: committed.**

1. **`inside createOrder, this is: com.zenith.data.CheckoutService, number 456824281`.**
   `createOrder` runs on the target. It is the same number that `persistOrder` printed in step 1.
2. **`inside persistOrder, this is: com.zenith.data.CheckoutService, number 456824281`.**
   `persistOrder` runs on the same target as in step 1. The object is not the problem; the route
   is.
3. **`transaction open: false`.** The label on `persistOrder` did nothing.
4. **The call path**, read from the bottom up:

   | # | Line | What it is |
   | --- | --- | --- |
   | 1 | `DataApplication.main(DataApplication.java:10)` | Where the app starts. |
   | 2 | `SelfInvocationDemo.run(SelfInvocationDemo.java:38)` | Line 38 is `checkout.createOrder(SUFI_NIGHT, "second@example.com", true);`. |
   | 3 | `CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)` | The call to `createOrder` lands on the proxy, like every call from outside. |
   | 4 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)` | The handler. Line 724 is its branch for "this method has no extra steps, so call the target directly". |
   | 5 | `CheckoutService.createOrder(CheckoutService.java:23)` | `createOrder`, running on the target. Line 23 is `this.persistOrder(eventId, customerEmail, failAfterSave);`. |
   | 6 | `CheckoutService.persistOrder(CheckoutService.java:35)` | `persistOrder`, called straight from row 5. Line 35 is `CallPath.print();`. |

   Nothing sits between rows 5 and 6: no proxy, no handler, no `TransactionInterceptor`. In
   step 1, those three lines sat right below `persistOrder`.
5. **`Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]`.**
   `save()` found no open transaction, so it began its own. Then come the `insert`,
   `Initiating transaction commit` and `Committing JPA transaction`. The row was committed before
   `persistOrder` even printed `order saved`.
6. **`caught RuntimeException: payment failed after the order was saved`.** It is the same
   exception as in step 1. But there is no `after exception` line and no `rollback` line: no
   transaction step ran on this route, and `save()`'s transaction was already over.
7. **`orders in the table: 1`.** The write stayed committed.

The row in psql has id 2, not 1. Step 1's `insert` took number 1. The rollback undid the row, but
not the number.

The two steps side by side:

| What you look for | Step 1: from outside | Step 2: through `this` |
| --- | --- | --- |
| A `Creating new transaction` line naming `CheckoutService.persistOrder` | yes, before the method's first line | none |
| Right below `persistOrder` in the call path | `TransactionInterceptor.invoke`, the handler (line 728), the proxy | `CheckoutService.createOrder` |
| `transaction open:` | `true` | `false` |
| How `save()` runs | `Participating in existing transaction` | its own transaction, committed right away |
| After the exception | `after exception`, then `Initiating transaction rollback` | nothing |
| Orders in the table after | 0 | 1 |

### Important terms

| Term | Meaning |
| --- | --- |
| **Self-invocation** | A method calling another method of the same object, through `this`. |
| **Self-invocation trap** | Self-invocation of a `@Transactional` method. The call skips the proxy, so the label does nothing, and there is no error. |
| **`failAfterSave`** | The new third parameter of `persistOrder`. With `true`, the method throws a `RuntimeException` right after the save. |
| **Handler line 724** | The handler's branch for a method with no extra steps: it calls the target directly. `createOrder` goes this way. |
| **Handler line 728** | The handler's branch for a method with extra steps: it runs them, and they call the target. `persistOrder` goes this way when it is called from outside. |
| **`aload_0`** | The compiled Java instruction that loads `this`. A call to another method of the same object starts with it, whether or not you write `this.`. |

### Where it is used

- **Interviews.** "Why does `@Transactional` do nothing when I call the method from the same
  class?" is one of the most asked Spring questions.
- **Real code.** A public method checks its input, then calls a `@Transactional` helper in the
  same class. It compiles, passes simple tests, and leaves half-finished work in the database
  when something fails.
- **Other labels that work through a proxy**, such as `@Cacheable` (remember a method's results)
  and `@Async` (run a method in the background), are skipped the same way when the call goes
  through `this`.
- **Code reviews.** Look for a method that calls a labelled method of its own class.
- In this task: subtask S10, and the "what happens" part of checkpoint CP4.

### Edge cases

- **`createOrder` itself goes through the proxy.** Its call path has
  `CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)` and the handler. The proxy is there.
  The trouble is that by the time `createOrder` calls `persistOrder`, the handler has already
  passed the call on to the target.
- **Same object in both methods.** `createOrder` and `persistOrder` printed the same number
  (456824281), and `persistOrder` printed that number in step 1 too. Both steps run `persistOrder`
  on the same object; only the route differs.
- **Spring's log never names either method in step 2.** In the full log of step 2, leaving out the
  `>>>` lines, no line contains `CheckoutService` at all. The only transaction names are
  `SimpleJpaRepository.save` and `SimpleJpaRepository.count`.
- **`ProxyDemo` still works** with `false` as its third value. A second run printed
  `transaction open: true` and committed, as before. Its call path now shows
  `persistOrder(CheckoutService.java:35)`, because `CallPath.print();` moved to line 35.

### Common mistakes — with the real errors

1. **Forgetting the other caller.** `persistOrder` got a third parameter, but `ProxyDemo.java`
   still called it with two values. Maven compiles every file, so the run stopped before the app
   started. Trimmed, with your path in place of `...`:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../ProxyDemo.java:[37,17] method persistOrder in class com.zenith.data.CheckoutService cannot be applied to given types;
     required: java.util.UUID,java.lang.String,boolean
     found:    java.util.UUID,java.lang.String
     reason: actual and formal argument lists differ in length
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `ProxyDemo.java:[37,17]` | The file, then line 37, column 17: where the bad call starts. |
   | `method persistOrder in class com.zenith.data.CheckoutService cannot be applied to given types` | The values in the call don't fit the method's parameters. |
   | `required: java.util.UUID,java.lang.String,boolean` | What the method asks for now: three values. |
   | `found: java.util.UUID,java.lang.String` | What line 37 passes: two values. |
   | `reason: actual and formal argument lists differ in length` | "Actual arguments" are the values in the call; "formal arguments" are the method's parameters. Their counts differ. |

   Fix: pass `false` as the third value on line 37.

2. **Thinking the word `this.` causes it.** Line 23 was changed to
   `persistOrder(eventId, customerEmail, failAfterSave);`, without `this.`. Step 2 printed the same
   as before: `transaction open: false`, `save()`'s own transaction with a commit, and
   `orders in the table: 1`. `javap -c` (a JDK tool that prints the compiled instructions of a
   class) showed the same instructions for `createOrder` both ways. Its last lines:

   ```text
         22: aload_0
         23: aload_1
         24: aload_2
         25: iload_3
         26: invokevirtual #43                 // Method persistOrder:(Ljava/util/UUID;Ljava/lang/String;Z)V
         29: return
   ```

   `aload_0` loads `this`. `aload_1`, `aload_2` and `iload_3` load the three parameters.
   `invokevirtual` calls `persistOrder` on the object loaded first, which is `this`. A call to a
   method of the same class, with nothing in front of it, is a call on `this`: Java adds it for
   you.

3. **Trying to fix it by changing the label.** `@Transactional` on `persistOrder` was changed to
   `@Transactional(rollbackFor = Exception.class)`, which asks for a rollback on every exception,
   checked or unchecked. Step 1's first transaction line changed to:

   ```text
   Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-java.lang.Exception
   ```

   The `-java.lang.Exception` at the end is the new setting: "roll back on `Exception`". Step 2
   didn't change at all: `transaction open: false`, `save()`'s own transaction, count 1. No
   setting on the label can help, because on this route nobody reads the label.

4. **Waiting for an error.** Spring gives none. The run with the transaction log at DEBUG and the
   transaction step at TRACE showed only what was missing: no `Creating new transaction` line
   naming `persistOrder`, and no rollback. Check the data, or print `transaction open:`, instead.

### Link to the task

Subtask S10 says: "Put two methods in one service: a public `createOrder()` with no annotation
that calls `this.persistOrder()`, where `persistOrder` is `@Transactional`. Throw inside
`persistOrder` and watch the write **stay committed**. The annotation did nothing."

Each part, with the proof:

- **"Put two methods in one service":** `createOrder` and `persistOrder`, both in
  `CheckoutService`.
- **"a public `createOrder()` with no annotation that calls `this.persistOrder()`":** line 20 has
  no label above it, and line 23 is the call.
- **"where `persistOrder` is `@Transactional`":** line 29.
- **"Throw inside `persistOrder`":** line 39, when `failAfterSave` is `true`.
- **"watch the write stay committed":** step 2 ends with `orders in the table: 1`, and psql shows
  the `second@example.com` row.
- **"The annotation did nothing":** `transaction open: false`, and no transaction named
  `persistOrder` in step 2. Step 1, with the same label and the same exception, rolled back to 0.

Subtask S10 is done. The task's "done when" line asks you to "reproduce the self-invocation trap";
step 2 is exactly that.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `OrderEntity.java` and `OrderRepository.java` exist, and so does the `ticket_order` table;
- `CallPath.java` (20 lines), `CheckoutService.java` (31 lines, with
  `persistOrder(UUID eventId, String customerEmail)`) and `ProxyDemo.java` (39 lines) exist;
- `ticket_order` is empty, with its numbering restarted at 1;
- Postgres runs in the Docker container `pg`.

If `CallPath.java` is missing, create it with the 20 lines from the example.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`.

**Step 2 — update the service.** Replace everything in
`scratch-app/src/main/java/com/zenith/data/CheckoutService.java` with the 42 lines from the
example.

**Step 3 — update the other caller.** In `ProxyDemo.java`, change line 37 to:

```java
        checkout.persistOrder(SUFI_NIGHT, "meera@example.com", false);
```

**Step 4 — add the demo.** Create
`scratch-app/src/main/java/com/zenith/data/SelfInvocationDemo.java` with the 44 lines from the
example.

**Step 5 — run it.** `grep -E` keeps only the lines that contain one of the six patterns:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=self-invocation -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|Creating new transaction|insert|after exception|transaction rollback|transaction commit'
```

Real output. The start of the long lines (time, process number, logger) and the object numbers
will differ on your laptop:

```text
>>> 1. checkout.persistOrder(...) from outside, failing after the save
2026-11-03T04:18:14.051+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [com.zenith.data.CheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.SelfInvocationDemo.run(SelfInvocationDemo.java:30)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
    insert 
>>>    order saved
2026-11-03T04:18:14.087+05:30 TRACE 89354 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.CheckoutService.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
2026-11-03T04:18:14.087+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
>>>    caught RuntimeException: payment failed after the order was saved
2026-11-03T04:18:14.090+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2026-11-03T04:18:14.121+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 0
>>> 2. checkout.createOrder(...), which calls this.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 456824281
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      com.zenith.data.CheckoutService.createOrder(CheckoutService.java:23)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)
>>>      com.zenith.data.SelfInvocationDemo.run(SelfInvocationDemo.java:38)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
2026-11-03T04:18:14.123+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
    insert 
2026-11-03T04:18:14.126+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
2026-11-03T04:18:14.130+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2026-11-03T04:18:14.132+05:30 DEBUG 89354 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 1
```

The `Creating new transaction ... count` lines and the commit after them belong to `count()`'s
own short transaction.

**Step 6 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select id, customer_email, quantity from ticket_order order by id;"
```

Expected: one row (id 2, `second@example.com`, quantity 1), ending with `(1 row)`. There is no
id 1.

**Step 7 — see the compile error.** In `ProxyDemo.java`, change line 37 back to
`        checkout.persistOrder(SUFI_NIGHT, "meera@example.com");`. Run the command from step 5
without the `| grep ...` part. The build stops with
`ProxyDemo.java:[37,17] method persistOrder in class com.zenith.data.CheckoutService cannot be applied to given types;`,
followed by the `required:`, `found:` and `reason:` lines from the first common mistake. Then put
`, false` back on line 37.

**Step 8 — experiment without `this.`.**

1. Empty the table with the first command from step 9.
2. In `CheckoutService.java`, change line 23 to
   `        persistOrder(eventId, customerEmail, failAfterSave);`.
3. Run the command from step 5. The output matches step 5, and step 2 still ends with
   `orders in the table: 1`.
4. Put `this.` back on line 23.

**Step 9 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- Step 2 of the output shows `Creating new transaction with name
  [com.zenith.data.CheckoutService.createOrder]`, and the count stays 0: `createOrder` has
  `@Transactional` on it. Line 20 must have no annotation above it. Only `persistOrder` has one,
  on line 29.
- The build stops with `cannot be applied to given types` at `ProxyDemo.java:[37,17]`: step 3 is
  missing.
- Step 1 of the output already ends with `orders in the table: 1`, and no `Creating new
  transaction` line names `CheckoutService.persistOrder`: `@Transactional` is missing from line 29
  of `CheckoutService.java`.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- step 1 of the output ended with `Initiating transaction rollback` and a count of 0;
- step 2 showed `transaction open: false`, a call path going straight from `createOrder` to
  `persistOrder`, the save's own `Initiating transaction commit`, and a count of 1;
- psql showed id 2, `second@example.com`;
- you have seen the compile error and the experiment without `this.`, and put the code back;
- the table is empty again.

### Summary for revision

- A call from one method of a bean to another, through `this`, goes straight to the target and
  skips the proxy.
- So the `@Transactional` on the called method does nothing: no transaction, no rollback.
- Repository calls inside it then commit on their own, one by one, right away.
- The exception still reaches the caller, but the write stays committed.
- No error, no warning: only missing log lines and wrong data show it.
- Writing `this.` or not makes no difference. Changing the label's settings makes no difference.
- The same method, called from outside through the proxy, gets its transaction and rolls back.

### Practice questions, with answers

**Question 1.** `CheckoutService` is a `@Service` with an `OrderRepository orders` field. Spring
hands out a proxy for it. You put `@Transactional` on `createOrder` as well, so both methods
carry it:

```java
@Transactional
public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
    System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
            + ", number " + System.identityHashCode(this));
    this.persistOrder(eventId, customerEmail, failAfterSave);
}

@Transactional
public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
    System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
            + ", number " + System.identityHashCode(this));
    System.out.println(">>>    transaction open: "
            + TransactionSynchronizationManager.isActualTransactionActive());
    CallPath.print();
    orders.save(new OrderEntity(eventId, customerEmail, 1));
    System.out.println(">>>    order saved");
    if (failAfterSave) {
        throw new RuntimeException("payment failed after the order was saved");
    }
}
```

`TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
is open for the code that's running right now. `CallPath.print()` prints the calls that are running
right now, newest first.

The table `ticket_order` is empty. A `CommandLineRunner` gets the `CheckoutService` bean through
its constructor, as `checkout`, and runs this, with the transaction log at DEBUG and
`TransactionInterceptor` at TRACE:

```java
try {
    checkout.createOrder(SUFI_NIGHT, "second@example.com", true);
} catch (RuntimeException e) {
    System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
System.out.println(">>>    orders in the table: " + orders.count());
```

- (a) Which `CheckoutService` methods are named in the `Creating new transaction with name [...]`
  lines?
- (b) What does `transaction open:` print inside `persistOrder`?
- (c) How many rows are in the table afterwards? Say why.

**Answer.** It was run for real, in a temporary runner. The extra `@Transactional` line pushed the
lines below it down by one, so the call path shows lines 24 and 36. The output, with the front of
each log line cut off:

```text
>>> Q1. checkout.createOrder(...) with @Transactional on createOrder too
Creating new transaction with name [com.zenith.data.CheckoutService.createOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1866834685<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2b26d289]
Getting transaction for [com.zenith.data.CheckoutService.createOrder]
>>>    inside createOrder, this is: com.zenith.data.CheckoutService, number 1680837503
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 1680837503
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:36)
>>>      com.zenith.data.CheckoutService.createOrder(CheckoutService.java:24)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)
>>>      com.zenith.data.LessonCheck24.run(LessonCheck24.java:29)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(1866834685<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.CheckoutService.createOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(1866834685<open>)]
Closing JPA EntityManager [SessionImpl(1866834685<open>)] after transaction
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 0
```

- (a) **Only `createOrder`, once:** `Creating new transaction with name
  [com.zenith.data.CheckoutService.createOrder]`. The call from the runner landed on the proxy,
  and `createOrder` now has the label, so the handler took line 728 and ran the transaction step
  before `createOrder` began. No line names `persistOrder`: `this.persistOrder(...)` still skipped
  the proxy.
- (b) **`true`.** `persistOrder` didn't begin a transaction, but it runs inside the one that
  began for `createOrder`. The call path shows it: `TransactionInterceptor.invoke` sits below
  `createOrder`, and nothing sits between `createOrder` and `persistOrder`.
- (c) **0.** `save()` joined the open transaction (`Participating in existing transaction`). The
  exception came out of `persistOrder`, passed through `createOrder`, and reached the transaction
  step of `createOrder`: `Completing transaction for [com.zenith.data.CheckoutService.createOrder]
  after exception: ...`, then `Initiating transaction rollback`. The `insert` was undone. It works
  because the label is now on the method that the outside call reaches. The label on
  `persistOrder` is still never read.

**Question 2.** You add this service to the scratch app:

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TwoWriteService {

    private final OrderRepository orders;

    public TwoWriteService(OrderRepository orders) {
        this.orders = orders;
    }

    public void outer(UUID eventId) {
        this.inner(eventId);
    }

    @Transactional
    public void inner(UUID eventId) {
        orders.save(new OrderEntity(eventId, "first@example.com", 1));
        orders.save(new OrderEntity(eventId, "second@example.com", 1));
        throw new RuntimeException("payment failed after both orders");
    }
}
```

`save()` sends each `INSERT` at once. The table `ticket_order` is empty. A `CommandLineRunner` gets
the bean through its constructor, as `twoWrites`, and runs these two parts one after the other,
without emptying the table in between. The transaction log is at DEBUG and `TransactionInterceptor`
at TRACE:

```java
try {
    twoWrites.outer(SUFI_NIGHT);
} catch (RuntimeException e) {
    System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
System.out.println(">>>    orders in the table: " + orders.count());

try {
    twoWrites.inner(SUFI_NIGHT);
} catch (RuntimeException e) {
    System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
}
System.out.println(">>>    orders in the table: " + orders.count());
```

- (a) How many rows are in the table after the first call?
- (b) How many after the second call?
- (c) For each call, which method names appear in the `Creating new transaction with name [...]`
  lines, and how many times? Leave out the ones for `count()`.

**Answer.** It was run for real, in a temporary runner. The output, with the front of each log
line cut off:

```text
>>> Q2a. twoWrites.outer(...)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(502852148<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@56511eda]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(502852148<open>)]
Closing JPA EntityManager [SessionImpl(502852148<open>)] after transaction
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1832487788<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@a8ef0c2]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1832487788<open>)]
Closing JPA EntityManager [SessionImpl(1832487788<open>)] after transaction
>>>    caught RuntimeException: payment failed after both orders
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 2
>>> Q2b. twoWrites.inner(...) called directly
Creating new transaction with name [com.zenith.data.TwoWriteService.inner]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(2055022026<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@6fb514db]
Getting transaction for [com.zenith.data.TwoWriteService.inner]
Found thread-bound EntityManager [SessionImpl(2055022026<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Found thread-bound EntityManager [SessionImpl(2055022026<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Completing transaction for [com.zenith.data.TwoWriteService.inner] after exception: java.lang.RuntimeException: payment failed after both orders
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(2055022026<open>)]
Closing JPA EntityManager [SessionImpl(2055022026<open>)] after transaction
>>>    caught RuntimeException: payment failed after both orders
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 2
```

Afterwards, in psql:

```text
 id |   customer_email   | quantity 
----+--------------------+----------
  1 | first@example.com  |        1
  2 | second@example.com |        1
(2 rows)
```

- (a) **2.** `outer` has no label, so the handler called it on the target directly. Its
  `this.inner(eventId)` skipped the proxy, so `inner` ran with no transaction. Each `save()` began
  its own transaction and committed it at once. The exception came after both were committed.
- (b) **Still 2.** Called from outside, `inner` went through the proxy: one transaction for
  `inner`, both saves joined it, and the exception rolled both back. The two rows from the first
  call stay. The second call's two inserts used up the numbers 3 and 4, which the rollback didn't
  give back.
- (c) **First call (`outer`):** `SimpleJpaRepository.save`, twice, each one followed by
  `Initiating transaction commit`. No line names `TwoWriteService`. **Second call (`inner`):**
  `com.zenith.data.TwoWriteService.inner`, once. Both saves show
  `Participating in existing transaction`, and it ends with `after exception` and
  `Initiating transaction rollback`.

  So with the trap, the writes don't just miss the transaction: each one commits on its own. When
  the work fails halfway, the finished half stays in the database.

(After the checks, `truncate ticket_order restart identity;` emptied the table again, and the
temporary files were removed.)

---

## Lesson 25 — Why it happens, and the fix (finishes subtask S11, answers CP4)

### Definition

Ideas you need again, in short:

- A **bean** is an object that Spring makes when the app starts and hands to every constructor
  that asks for its type. `@Service` on a class tells Spring to make one.
- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** keeps every change; a **rollback** undoes every change. With `@Transactional` on a
  method, Spring begins a transaction before the method runs and commits it after the method
  returns. If an unchecked exception (a `RuntimeException`, or a class that extends it) comes out,
  Spring rolls back instead.
- A **proxy** is a stand-in object; the real object behind it is the **target**. For a bean with a
  `@Transactional` method, Spring hands out a proxy whose class, such as
  `CheckoutService$$SpringCGLIB$$0`, is a subclass of your class, written in memory at startup.
- **The route of a call from outside**, such as `checkout.persistOrder(...)`: the call lands on the
  proxy's version of the method, which hands it to Spring's **handler**
  (`CglibAopProxy$DynamicAdvisedInterceptor.intercept`). For a method with `@Transactional`, the
  handler runs the **transaction step** (`TransactionInterceptor`), which begins the transaction
  and then calls your method on the target; the handler shows line 728. For a method without it,
  the handler calls the target directly; it shows line 724.
- Inside your own method, **`this`** is the target, not the proxy.
- **The self-invocation trap:** `CheckoutService.createOrder` has no annotation and calls
  `this.persistOrder(...)`, which has `@Transactional` and throws right after its save. The call
  through `this` skips the proxy, so no transaction begins. `save()` begins its own short
  transaction and commits at once, so the row stays after the exception.
- A repository's `save()` joins an open transaction (`Participating in existing transaction`), or
  begins and commits its own if none is open.
- `CallPath.print()` prints the calls that are running right now, newest first, keeping only your
  own classes, the handler and the transaction step.
  `TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
  is open for the code that's running right now.

New words:

- **Inject.** When a constructor asks for a bean and Spring passes one in, Spring *injects* it. If
  that bean has a proxy, the proxy is what gets injected.
- **Separate bean.** A second class with its own `@Service`. Spring makes it as a bean of its own,
  with its own proxy if it needs one.

The same failing input, sent down two routes in one real run:

| | The trap: `CheckoutService` | The fix: `FixedCheckoutService` and `OrderWriter` |
| --- | --- | --- |
| How `createOrder` calls `persistOrder` | `this.persistOrder(...)` | `writer.persistOrder(...)` |
| What that call lands on | the target, directly | `OrderWriter`'s proxy |
| `transaction open:` inside `persistOrder` | `false` | `true` |
| After the exception | nothing to roll back | rollback |
| Rows added | 1 | 0 |

### Why it is needed

- **Subtask S11 asks for it:** "Explain why, in terms of the proxy … Fix it by moving
  `persistOrder` into a separate bean and injecting it."
- **Checkpoint CP4 asks for it:** "Explain the self-invocation trap to someone who has not seen
  it, including why it happens." The section "Link to the task" has an answer you can give.
- **The "why" matters more than the fix.** If you only remember "move it to another class", you
  can fall into the same trap inside the new class. If you know why it happens, you can spot it
  anywhere.

### Key points

- A call through `this` is an ordinary Java call on the target. The target has no link back to
  its proxy, so Spring never gets a chance to step in.
- Spring can only add the transaction when a call passes through a proxy.
- The fix: move the labelled method, together with its label, into a separate bean. Inject that
  bean, and call the method through the injected field, which holds the bean's proxy.
- The label must move with the method, the new class needs `@Service`, and the object must come
  from Spring, not from `new`.
- Quick check: print the field's `getClass().getName()`. It must contain `$$SpringCGLIB$$`.
- The rule: the transaction step runs only when a call arrives through a proxy at a method with
  `@Transactional`. What matters is the route of the call.

### How it works, step by step

**Part 1: why the trap happens, in terms of the proxy.** Subtask S11 gives three sentences:

1. **"Spring wrapped your bean."** At startup, Spring made the target, a plain `CheckoutService`.
   Then it made a proxy around it, `CheckoutService$$SpringCGLIB$$0`. Every constructor that asks
   for a `CheckoutService` gets the proxy.
2. **"Calls from outside hit the wrapper, which opens the transaction."** A demo's field `checkout`
   holds the proxy. So `checkout.persistOrder(...)` lands on the proxy, and the handler runs the
   transaction step before `persistOrder` begins.
3. **"A call through `this` goes straight to the real method and never touches the wrapper."**
   - `checkout.createOrder(...)` does reach the proxy. But `createOrder` has no label, so the
     handler passes the call straight on to the target.
   - So `createOrder` runs on the target, and inside it `this` is the target.
   - The target doesn't know that the proxy exists. Its only field is `orders`; nothing in it
     points back to the proxy.
   - So `this.persistOrder(...)` is an ordinary Java call from the target to itself. Java jumps
     straight into the method. Spring never gets a chance to step in, so nobody reads the label.

**Part 2: the fix.** Spring can only step in when a call passes through a proxy. So make the call
to `persistOrder` pass through one:

1. Move `persistOrder`, together with its `@Transactional`, into a new class, `OrderWriter`, and
   mark that class `@Service`. Spring makes `OrderWriter` as a bean of its own and, because its
   method has the label, wraps it in its own proxy, `OrderWriter$$SpringCGLIB$$0`.
2. The service that creates orders asks for an `OrderWriter` in its constructor. Spring injects
   the `OrderWriter` proxy.
3. `createOrder` calls `writer.persistOrder(...)`. `writer` holds the proxy, so for `OrderWriter`
   this is a call from outside. It lands on the proxy, the handler runs the transaction step, and
   the transaction begins before `persistOrder` does.

The two routes, as pictures:

```text
THE TRAP: one bean

FixDemo.run
  | checkout.createOrder(...)         checkout holds CheckoutService's proxy
  v
[CheckoutService's proxy]             createOrder has no label: call the target directly
  v
CheckoutService.createOrder           runs on the target
  | this.persistOrder(...)            "this" is the target, so the proxy is skipped
  v
CheckoutService.persistOrder          no transaction; save() commits on its own

THE FIX: two beans

FixDemo.run
  | fixed.createOrder(...)
  v
FixedCheckoutService.createOrder
  | writer.persistOrder(...)          writer holds OrderWriter's proxy
  v
[OrderWriter's proxy]                 persistOrder has a label: run the transaction step
  v
[transaction step]                    begins the transaction
  v
OrderWriter.persistOrder              runs inside it; a failure rolls it back
```

### Example

`CheckoutService` stays exactly as it is, so the trap can still run. The fixed version is a new
class, `FixedCheckoutService`: it is `CheckoutService` after the fix. In real code you would change
the one service; here both live side by side, so a single run shows both.

All files are in `scratch-app/src/main/java/com/zenith/data/`.

`OrderWriter.java` (33 lines) is the separate bean. `persistOrder` moved here unchanged, with its
`@Transactional` on line 20. It still prints who `this` is, whether a transaction is open, and the
call path. Then it saves, and when `failAfterSave` is `true`, it throws (line 30).

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 25: the separate bean. persistOrder moved here from CheckoutService, together with its
// @Transactional, so Spring wraps this bean in a proxy of its own.
@Service
public class OrderWriter {

    private final OrderRepository orders;

    public OrderWriter(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}
```

`FixedCheckoutService.java` (23 lines). Its constructor asks for an `OrderWriter` (lines 14 to
16), so Spring injects one. Its `createOrder` has no annotation, just like the trap's. It prints
what `writer` holds (line 20), then calls `writer.persistOrder(...)` (line 21) instead of
`this.persistOrder(...)`.

```java
package com.zenith.data;

import org.springframework.stereotype.Service;

import java.util.UUID;

// Lesson 25: CheckoutService after the fix. persistOrder moved out to OrderWriter, a separate bean.
// CheckoutService stays as it was, so the trap can still run next to the fix.
@Service
public class FixedCheckoutService {

    private final OrderWriter writer;

    public FixedCheckoutService(OrderWriter writer) {
        this.writer = writer;
    }

    // No annotation, as before. The call now goes through "writer", which holds OrderWriter's proxy.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, writer is: " + writer.getClass().getName());
        writer.persistOrder(eventId, customerEmail, failAfterSave);
    }
}
```

`FixDemo.java` (46 lines) runs the trap (line 32) and then the fix (line 40), both with the same
failing input. Each call sits in a `try`/`catch`, so the demo carries on and prints the number of
orders after each one.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 25: the trap and the fix, side by side. Neither createOrder has an annotation, and both
// lead to a @Transactional persistOrder that fails after the save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=fix
@Component
@Profile("fix")
class FixDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;
    private final FixedCheckoutService fixed;
    private final OrderRepository orders;

    FixDemo(CheckoutService checkout, FixedCheckoutService fixed, OrderRepository orders) {
        this.checkout = checkout;
        this.fixed = fixed;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. the trap: checkout.createOrder(...), which calls this.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "trap@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. the fix: fixed.createOrder(...), which calls writer.persistOrder(...)");
        try {
            fixed.createOrder(SUFI_NIGHT, "fixed@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
```

Unchanged, but used by the demo: `CheckoutService.java` (42 lines), the trap. `createOrder`
(line 20) has no annotation and calls `this.persistOrder(...)` on line 23. `persistOrder` has
`@Transactional` on line 29.

```java
package com.zenith.data;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 23: a small service for the proxy lessons. It has one @Transactional method.
@Service
public class CheckoutService {

    private final OrderRepository orders;

    public CheckoutService(OrderRepository orders) {
        this.orders = orders;
    }

    // Lesson 24: no annotation. It calls persistOrder on "this", which is the target, not the proxy.
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        this.persistOrder(eventId, customerEmail, failAfterSave);
    }

    // Lesson 23: one write in a transaction. It also prints who "this" is, whether a
    // transaction is open, and the path the call took to get here.
    // Lesson 24: with failAfterSave, an unchecked exception comes right after the save.
    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}
```

`CallPath.java` (20 lines) is unchanged too:

```java
package com.zenith.data;

// Lesson 23: prints the calls that are running right now, newest first. It keeps only your own
// classes, the proxy's handler, and the transaction step. The full list is about 30 lines.
class CallPath {

    static void print() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        System.out.println(">>>    call path, newest first:");
        // frames[0] is getStackTrace itself and frames[1] is this method, so start at 2.
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.zenith")
                    || className.endsWith("DynamicAdvisedInterceptor")
                    || className.endsWith("TransactionInterceptor")) {
                System.out.println(">>>      " + frames[i]);
            }
        }
    }
}
```

The command, with the transaction log at DEBUG and `TransactionInterceptor` at TRACE, inside one
pair of quotes. The table was empty:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=fix -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line cut off. Each
`orders.count()` call has its own short read-only transaction of 13 lines; each one is folded into
a single line here:

```text
>>> 1. the trap: checkout.createOrder(...), which calls this.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.CheckoutService, number 1108674897
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 1108674897
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      com.zenith.data.CheckoutService.createOrder(CheckoutService.java:23)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)
>>>      com.zenith.data.FixDemo.run(FixDemo.java:32)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1382685521<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@2a4bee4e]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1382685521<open>)]
Closing JPA EntityManager [SessionImpl(1382685521<open>)] after transaction
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
>>> 2. the fix: fixed.createOrder(...), which calls writer.persistOrder(...)
>>>    inside createOrder, writer is: com.zenith.data.OrderWriter$$SpringCGLIB$$0
Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(707940781<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@457547a1]
Getting transaction for [com.zenith.data.OrderWriter.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 1268982338
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.FixedCheckoutService.createOrder(FixedCheckoutService.java:21)
>>>      com.zenith.data.FixDemo.run(FixDemo.java:40)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(707940781<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.OrderWriter.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(707940781<open>)]
Closing JPA EntityManager [SessionImpl(707940781<open>)] after transaction
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
```

Afterwards, `select id, customer_email, quantity from ticket_order order by id;` in psql:

```text
 id |  customer_email  | quantity 
----+------------------+----------
  1 | trap@example.com |        1
(1 row)
```

### Walkthrough

**Step 1, the trap**, behaves exactly as before:

- `transaction open: false`.
- In the call path, `CheckoutService.persistOrder` sits right on top of
  `CheckoutService.createOrder`, with nothing between them.
- `save()` began its own transaction and committed (`Initiating transaction commit`) before
  `order saved` printed.
- The count is 1: the `trap@example.com` order stayed.

**Step 2, the fix**, line by line:

1. **`inside createOrder, writer is: com.zenith.data.OrderWriter$$SpringCGLIB$$0`.** The field
   holds `OrderWriter`'s proxy, not a plain `OrderWriter`. That is what Spring injected.
2. **`Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]`** comes before
   the first line printed inside `persistOrder`. The proxy began the transaction.
3. **`inside persistOrder, this is: com.zenith.data.OrderWriter, number 1268982338`.**
   `persistOrder` runs on `OrderWriter`'s target. That's normal: the proxy always passes the call
   on to the target in the end.
4. **`transaction open: true`.**
5. **The call path**, read from the bottom up:

   | # | Line | What it is |
   | --- | --- | --- |
   | 1 | `DataApplication.main(DataApplication.java:10)` | Where the app starts. |
   | 2 | `FixDemo.run(FixDemo.java:40)` | Line 40 is `fixed.createOrder(SUFI_NIGHT, "fixed@example.com", true);`. |
   | 3 | `FixedCheckoutService.createOrder(FixedCheckoutService.java:21)` | Line 21 is `writer.persistOrder(eventId, customerEmail, failAfterSave);`. |
   | 4 | `OrderWriter$$SpringCGLIB$$0.persistOrder(<generated>)` | The call lands on `OrderWriter`'s proxy. `<generated>` means the class was written in memory, so it has no source file. |
   | 5 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)` | The handler, on its "run the extra steps" branch. |
   | 6 | `TransactionInterceptor.invoke(TransactionInterceptor.java:119)` | The transaction step. The transaction is already open at this point. |
   | 7 | `OrderWriter.persistOrder(OrderWriter.java:26)` | Your method, on the target. Line 26 is `CallPath.print();`. |

   Rows 4 to 6 are the wrapper. In step 1, nothing sat between `createOrder` and `persistOrder`.
   Here the wrapper does.
6. **`Participating in existing transaction`.** `save()` joined `persistOrder`'s transaction
   instead of starting its own. Then come the `insert` and `order saved`, with no
   `Initiating transaction commit` after this `insert`.
7. **`Completing transaction for [com.zenith.data.OrderWriter.persistOrder] after exception: ...`**,
   then **`Initiating transaction rollback`**. The transaction step saw the exception and undid
   the `insert`.
8. **`orders in the table: 1`.** Still 1, the trap's row. psql shows only `trap@example.com`;
   there is no `fixed@example.com`.

The two steps side by side:

| | Step 1: the trap | Step 2: the fix |
| --- | --- | --- |
| How `createOrder` calls `persistOrder` | `this.persistOrder(...)` | `writer.persistOrder(...)` |
| What that call lands on | the target, directly | `OrderWriter`'s proxy |
| Between `createOrder` and `persistOrder` in the call path | nothing | the proxy, the handler (line 728), `TransactionInterceptor.invoke` |
| Transaction for `persistOrder` | none | `Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]` |
| `transaction open:` | `false` | `true` |
| The save | its own transaction, committed at once | joins `persistOrder`'s transaction |
| After the exception | nothing | rollback |
| Rows added | 1 | 0 |

### Important terms

| Term | Meaning |
| --- | --- |
| **Inject** | Spring passes a bean into a constructor that asks for it. If the bean has a proxy, the proxy is passed. |
| **Separate bean** | A second class with its own `@Service`, made by Spring as a bean of its own, with its own proxy if it needs one. |
| **`OrderWriter`** | The separate bean. It holds the `@Transactional` `persistOrder` that moved out of the service. |
| **`FixedCheckoutService`** | `CheckoutService` after the fix: `createOrder` calls `writer.persistOrder(...)` through the injected field. |
| **`writer is:` line** | Prints the class of the object in the field. `OrderWriter$$SpringCGLIB$$0` means the call goes through a proxy; plain `OrderWriter` means it doesn't. |
| **Route of a call** | The objects a call passes through on its way to your method. The transaction step runs only on a route that passes through a proxy. |

### Where it is used

- **Real services are built this way.** One service coordinates the work (here
  `FixedCheckoutService`) and calls other beans that each do one job (here `OrderWriter`). Each
  bean's `@Transactional` methods are called through an injected proxy, so the labels work.
- **Interviews.** A full answer has two halves. The why: a call through `this` skips the proxy. A
  fix: make the call go through a proxy, for example by moving the method into a separate bean.
- **Code reviews.** For every `@Transactional` method, ask: "Is it called from another class,
  through an injected field?" If it's only ever called from inside its own class, the label does
  nothing.
- **Other Spring labels that use the same proxy**, such as `@Cacheable` and `@Async`, are fixed the
  same way.
- In this task: subtask S11 and checkpoint CP4.

### Edge cases

- **`FixedCheckoutService` has no proxy.** In step 2, `FixDemo.run` calls
  `FixedCheckoutService.createOrder` with nothing in between. `FixedCheckoutService` has no
  `@Transactional` anywhere, so Spring gave it no proxy. It doesn't need one, because the
  transaction is `OrderWriter`'s job.
- **`persistOrder` still runs on a target.** In step 2, `this is: com.zenith.data.OrderWriter` is
  `OrderWriter`'s target. The fix doesn't avoid the target; it makes the call pass through the
  proxy first.
- **A missing `@Service` breaks every run.** `FixedCheckoutService` has no profile, so Spring makes
  it in every run. Without an `OrderWriter` bean, even the `self-invocation` profile, which never
  uses the fix, stopped at startup with the same error as the first common mistake below.
- **`new` leaves Spring's bean unused.** With `new OrderWriter(orders)`, Spring still makes its own
  `OrderWriter` bean with a proxy, but nothing uses it.

### Common mistakes — with the real errors

1. **Forgetting `@Service` on the new class.** Line 11 of `OrderWriter.java` (`@Service`) was
   removed, and the demo was run. The app stopped before any `>>>` line:

   ```text
   ***************************
   APPLICATION FAILED TO START
   ***************************

   Description:

   Parameter 0 of constructor in com.zenith.data.FixedCheckoutService required a bean of type 'com.zenith.data.OrderWriter' that could not be found.


   Action:

   Consider defining a bean of type 'com.zenith.data.OrderWriter' in your configuration.
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `APPLICATION FAILED TO START` | Spring couldn't finish making the beans, so the app never ran. |
   | `Parameter 0 of constructor in com.zenith.data.FixedCheckoutService` | The first parameter of `FixedCheckoutService`'s constructor (Java counts from 0): `OrderWriter writer`. |
   | `required a bean of type 'com.zenith.data.OrderWriter'` | To call that constructor, Spring needs an `OrderWriter` bean to pass in. |
   | `that could not be found` | There isn't one. Without `@Service`, Spring never makes an `OrderWriter`. |
   | `Consider defining a bean of type 'com.zenith.data.OrderWriter'` | Spring's hint: make one, for example by putting `@Service` on the class. |

2. **Moving the method but leaving the label behind.** The `@Transactional` line was deleted from
   `OrderWriter.java`, and the demo was run on an empty table. Step 2, trimmed to the important
   lines. `CallPath.print()` moved up to line 25, because one line above it was deleted:

   ```text
   >>> 2. the fix: fixed.createOrder(...), which calls writer.persistOrder(...)
   >>>    inside createOrder, writer is: com.zenith.data.OrderWriter
   >>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 1387446869
   >>>    transaction open: false
   >>>    call path, newest first:
   >>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:25)
   >>>      com.zenith.data.FixedCheckoutService.createOrder(FixedCheckoutService.java:21)
   >>>      com.zenith.data.FixDemo.run(FixDemo.java:40)
   >>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
       insert 
   Initiating transaction commit
   >>>    order saved
   >>>    caught RuntimeException: payment failed after the order was saved
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   Initiating transaction commit
   >>>    orders in the table: 2
   ```

   - `writer is: com.zenith.data.OrderWriter` has no `$$SpringCGLIB$$`. With no label left in it,
     `OrderWriter` needs no extra steps, so Spring hands out the plain object.
   - No proxy means no transaction. The save committed on its own, and the count went from 1 to 2.
   - There's no error, just as with the trap itself. The label has to move together with the
     method.

3. **Making the new object yourself with `new`.** `FixedCheckoutService`'s constructor was changed
   to build its own writer:

   ```java
       public FixedCheckoutService(OrderRepository orders) {
           this.writer = new OrderWriter(orders);
       }
   ```

   Step 2 then printed `writer is: com.zenith.data.OrderWriter`, `transaction open: false`, a call
   path going straight from `FixedCheckoutService.createOrder` to `OrderWriter.persistOrder`, the
   save's own `Initiating transaction commit`, and `orders in the table: 2`. `new` makes a plain
   object that Spring never wrapped. Only beans that Spring injects come with the proxy.

**A quick check for all three:** print `writer.getClass().getName()`. If the name doesn't contain
`$$SpringCGLIB$$`, the call can't go through a proxy.

### Link to the task

Subtask S11 says: "Explain why, in terms of the proxy: Spring wrapped your bean. Calls from outside
hit the wrapper, which opens the transaction. A call through `this` goes straight to the real
method and never touches the wrapper. Fix it by moving `persistOrder` into a separate bean and
injecting it."

- **"Spring wrapped your bean":** there is `CheckoutService$$SpringCGLIB$$0` around
  `CheckoutService`, and `OrderWriter$$SpringCGLIB$$0` around `OrderWriter`.
- **"Calls from outside hit the wrapper, which opens the transaction":** in step 2, the call path
  shows the proxy, the handler and `TransactionInterceptor.invoke` between
  `FixedCheckoutService.createOrder` and `OrderWriter.persistOrder`.
  `Creating new transaction ... [com.zenith.data.OrderWriter.persistOrder]` comes before the
  method's first line.
- **"A call through `this` goes straight to the real method":** in step 1,
  `CheckoutService.persistOrder` sits right on top of `CheckoutService.createOrder` in the call
  path, with `transaction open: false`.
- **"Moving `persistOrder` into a separate bean":** that bean is `OrderWriter`, with `@Service` and
  the `@Transactional` `persistOrder`.
- **"And injecting it":** `FixedCheckoutService`'s constructor asks for an `OrderWriter`, and
  `writer is: com.zenith.data.OrderWriter$$SpringCGLIB$$0` shows that Spring passed in the proxy.
- **The fix works:** with the same failing input, step 1 added 1 row and step 2 added 0.

Subtask S11 is done. With it, every part of the task's "Done when" is met: dirty checking, a
rollback and a non-rollback, and the self-invocation trap reproduced and fixed.

**Checkpoint CP4: an answer you could give** to someone who has never seen the trap:

> In Spring, `@Transactional` is only a label. The work is done by a **proxy**: when the app
> starts, Spring wraps your service in a stand-in object, and that stand-in is what other classes
> receive. When another class calls your service, the call reaches the proxy first. The proxy
> sees the label, begins a transaction, calls your real method, and then commits. If an unchecked
> exception comes out, it rolls back instead.
>
> The trap is when one method of the service calls another method of the same service, like
> `createOrder` calling `persistOrder`. That call is made on `this`, and inside your own code
> `this` is the real object, not the proxy. The real object doesn't know the proxy exists. So the
> call goes straight into `persistOrder`, nobody reads the label, and no transaction begins. Each
> repository call then commits on its own right away. If something fails halfway, the finished
> half stays in the database. There is no error and no warning; you only notice it from wrong
> data, or from transaction lines missing in the log.
>
> The fix is to make the call go through a proxy. For example, move `persistOrder` into a
> separate bean, inject that bean, and call the method through the injected field. That field
> holds the other bean's proxy, so the transaction begins as it should.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `CallPath.java` (20 lines), `CheckoutService.java` (42 lines, with `createOrder` and
  `persistOrder`), `ProxyDemo.java` (39 lines) and `SelfInvocationDemo.java` (44 lines) exist;
- `ticket_order` is empty, with its numbering restarted at 1;
- Postgres runs in the Docker container `pg`.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`. If it prints
`Cannot connect to the Docker daemon`, Docker itself isn't running. Start Docker first
(`colima start` if you use Colima, or open Docker Desktop), then run `docker start pg` again.

**Step 2 — add the separate bean.** Create
`scratch-app/src/main/java/com/zenith/data/OrderWriter.java` with the 33 lines from the example.

**Step 3 — add the fixed service.** Create
`scratch-app/src/main/java/com/zenith/data/FixedCheckoutService.java` with the 23 lines from the
example.

**Step 4 — add the demo.** Create `scratch-app/src/main/java/com/zenith/data/FixDemo.java` with
the 46 lines from the example.

**Step 5 — run it.** `grep -E` keeps only the lines that contain one of the six patterns:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=fix -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|Creating new transaction|insert|after exception|transaction rollback|transaction commit'
```

Real output. The start of the long lines (time, process number, logger) and the object numbers
will differ on your laptop:

```text
>>> 1. the trap: checkout.createOrder(...), which calls this.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.CheckoutService, number 1108674897
>>>    inside persistOrder, this is: com.zenith.data.CheckoutService, number 1108674897
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.CheckoutService.persistOrder(CheckoutService.java:35)
>>>      com.zenith.data.CheckoutService.createOrder(CheckoutService.java:23)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.CheckoutService$$SpringCGLIB$$0.createOrder(<generated>)
>>>      com.zenith.data.FixDemo.run(FixDemo.java:32)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
2026-11-03T23:53:06.120+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
    insert 
2026-11-03T23:53:06.154+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
2026-11-03T23:53:06.160+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2026-11-03T23:53:06.190+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 1
>>> 2. the fix: fixed.createOrder(...), which calls writer.persistOrder(...)
>>>    inside createOrder, writer is: com.zenith.data.OrderWriter$$SpringCGLIB$$0
2026-11-03T23:53:06.191+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 1268982338
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.FixedCheckoutService.createOrder(FixedCheckoutService.java:21)
>>>      com.zenith.data.FixDemo.run(FixDemo.java:40)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
    insert 
>>>    order saved
2026-11-03T23:53:06.193+05:30 TRACE 3630 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.OrderWriter.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
2026-11-03T23:53:06.193+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
>>>    caught RuntimeException: payment failed after the order was saved
2026-11-03T23:53:06.194+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2026-11-03T23:53:06.197+05:30 DEBUG 3630 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 1
```

The `Creating new transaction ... count` lines and the commit after each one belong to `count()`'s
own short transaction.

**Step 6 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select id, customer_email, quantity from ticket_order order by id;"
```

Expected: one row (id 1, `trap@example.com`, quantity 1), ending with `(1 row)`. There is no
`fixed@example.com`.

**Step 7 — break it by leaving the label behind.**

1. Empty the table with the first command from step 9.
2. In `OrderWriter.java`, delete line 20, `    @Transactional`.
3. Run the command from step 5. In step 2 of the output, you should see
   `writer is: com.zenith.data.OrderWriter` (no `$$SpringCGLIB$$`), `transaction open: false`, and
   `orders in the table: 2`.
4. Put `    @Transactional` back as line 20, right above `public void persistOrder(`.

**Step 8 — break it by removing `@Service`.**

1. In `OrderWriter.java`, delete line 11, `@Service`.
2. Run the command from step 5 without the `| grep ...` part. The app stops before any `>>>` line,
   with `APPLICATION FAILED TO START` and
   `Parameter 0 of constructor in com.zenith.data.FixedCheckoutService required a bean of type 'com.zenith.data.OrderWriter' that could not be found.`
3. Put `@Service` back as line 11, right above `public class OrderWriter {`.

**Step 9 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- Step 2 of the output shows `writer is: com.zenith.data.OrderWriter` and ends with
  `orders in the table: 2`: either `@Transactional` is missing from line 20 of `OrderWriter.java`,
  or `FixedCheckoutService` makes its writer with `new`. Its constructor must be
  `public FixedCheckoutService(OrderWriter writer)`, with `this.writer = writer;` inside.
- `APPLICATION FAILED TO START` with `required a bean of type 'com.zenith.data.OrderWriter'`:
  `@Service` is missing from line 11 of `OrderWriter.java`.
- `Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.`:
  Postgres isn't running. Do step 1 again.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- step 1 of the output showed the save's own `Initiating transaction commit` and ended with
  `orders in the table: 1`;
- step 2 showed `writer is: com.zenith.data.OrderWriter$$SpringCGLIB$$0`,
  `Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]`,
  `transaction open: true`, and the proxy, handler and `TransactionInterceptor.invoke` between
  `FixedCheckoutService.createOrder` and `OrderWriter.persistOrder`;
- step 2 then showed a rollback, and the count stayed at 1;
- psql showed only `trap@example.com`;
- you have seen both break-its and put the code back;
- the table is empty again;
- you can give the CP4 answer in your own words.

### Summary for revision

- A call through `this` is a plain Java call on the target. The target doesn't know its proxy, so
  the label is never read.
- Spring can only add the transaction when a call passes through a proxy.
- The fix: move the `@Transactional` method, with its label, into a separate `@Service` bean.
  Inject that bean and call the method through the injected field.
- The injected field holds the other bean's proxy (`OrderWriter$$SpringCGLIB$$0`), so the call
  passes through the handler and the transaction step.
- With the same failing input, the trap kept its row and the fix rolled back.
- Three ways to break the fix, all silent or at startup: no `@Service` (startup error), label left
  behind (no proxy), and `new` (no proxy).
- Check with `getClass().getName()`: `$$SpringCGLIB$$` must appear.

### Practice questions, with answers

**Question 1.** `OrderWriter` is a `@Service`. Spring hands out a proxy for it, because
`persistOrder` has `@Transactional`. You add a second public method, `persistTwice`, with no
annotation, so the class is now:

```java
@Service
public class OrderWriter {

    private final OrderRepository orders;

    public OrderWriter(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }

    public void persistTwice(UUID eventId) {
        persistOrder(eventId, "first@example.com", false);
        persistOrder(eventId, "second@example.com", true);
    }
}
```

`TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
is open for the code that's running right now. `CallPath.print()` prints the calls that are running
right now, keeping only your own classes, the handler and the transaction step.

The table is empty. A `CommandLineRunner` gets the `OrderWriter` bean through its constructor, as
`writer`, and runs `writer.persistTwice(SUFI_NIGHT)` inside
`try { ... } catch (RuntimeException e) { ... }`.

- (a) What do the two `transaction open:` lines print?
- (b) How many rows are in the table afterwards?
- (c) `persistOrder` now lives in a separate bean, and the runner calls that bean through its
  proxy. Explain (a) and (b) in terms of the proxy.

**Answer.** It was run for real, in a temporary runner. The new method sat at the end of the class,
so its two calls were on lines 35 and 36, and the runner called it on line 31. The output, with the
front of each log line cut off:

```text
>>> Q1. writer.persistTwice(...)
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 735476985
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      com.zenith.data.OrderWriter.persistTwice(OrderWriter.java:35)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistTwice(<generated>)
>>>      com.zenith.data.LessonCheck25.run(LessonCheck25.java:31)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1474835976<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@1e470a51]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1474835976<open>)]
Closing JPA EntityManager [SessionImpl(1474835976<open>)] after transaction
>>>    order saved
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 735476985
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      com.zenith.data.OrderWriter.persistTwice(OrderWriter.java:36)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistTwice(<generated>)
>>>      com.zenith.data.LessonCheck25.run(LessonCheck25.java:31)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(2121437254<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@565bf1bb]
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(2121437254<open>)]
Closing JPA EntityManager [SessionImpl(2121437254<open>)] after transaction
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 2
```

Afterwards, in psql:

```text
 id |   customer_email   | quantity 
----+--------------------+----------
  1 | first@example.com  |        1
  2 | second@example.com |        1
(2 rows)
```

- (a) **`false`, both times.**
- (b) **2 rows**, `first@example.com` and `second@example.com`. With no transaction open, each
  `save()` began its own transaction and committed at once. The exception after the second save
  had nothing to roll back.
- (c) The runner's call did go through `OrderWriter`'s proxy: the call path shows
  `OrderWriter$$SpringCGLIB$$0.persistTwice(<generated>)` and the handler. But `persistTwice` has
  no label, so the handler took line 724 and called the target directly. Inside `persistTwice`,
  each `persistOrder(...)` is a call on `this`, the target: a call with nothing in front of it
  goes to `this`, so writing `this.persistOrder(...)` would change nothing. Nothing sits between
  `persistTwice` and `persistOrder` in the call path. It is the self-invocation trap again, now
  inside `OrderWriter`. A separate bean helps only for calls that arrive through its proxy
  straight at the labelled method.

**Question 2.** `OrderWriter` is a `@Service`. Its
`persistOrder(UUID eventId, String customerEmail, boolean failAfterSave)` has `@Transactional`. It
saves one order with `orders.save(...)`, and when `failAfterSave` is `true`, it then throws
`new RuntimeException("payment failed after the order was saved")`. Spring hands out a proxy for
`OrderWriter`.

`FixedCheckoutService` is another `@Service`. Spring injects the `OrderWriter` proxy into its
constructor, and it's stored in the field `writer`. You add this method to `FixedCheckoutService`,
with no annotation:

```java
public void createTwoOrders(UUID eventId) {
    writer.persistOrder(eventId, "first@example.com", false);
    writer.persistOrder(eventId, "second@example.com", true);
}
```

The table is empty. A `CommandLineRunner` gets the `FixedCheckoutService` bean through its
constructor, as `fixed`, and runs `fixed.createTwoOrders(SUFI_NIGHT)` inside
`try { ... } catch (RuntimeException e) { ... }`.

- (a) How many `Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]`
  lines appear?
- (b) How many rows are in the table afterwards, and which ones?
- (c) Explain (b) in terms of the proxy.

**Answer.** It was run for real, in a temporary runner. The new method sat at the end of
`FixedCheckoutService`, so its two calls were on lines 25 and 26, and the runner called it on
line 40. The output, with the front of each log line cut off:

```text
>>> Q2. fixed.createTwoOrders(...)
Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(1352397525<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@557f0a39]
Getting transaction for [com.zenith.data.OrderWriter.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 1673026967
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.FixedCheckoutService.createTwoOrders(FixedCheckoutService.java:25)
>>>      com.zenith.data.LessonCheck25.run(LessonCheck25.java:40)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(1352397525<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.OrderWriter.persistOrder]
Initiating transaction commit
Committing JPA transaction on EntityManager [SessionImpl(1352397525<open>)]
Closing JPA EntityManager [SessionImpl(1352397525<open>)] after transaction
Creating new transaction with name [com.zenith.data.OrderWriter.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(2064484576<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@724e483f]
Getting transaction for [com.zenith.data.OrderWriter.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.OrderWriter, number 1673026967
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.OrderWriter.persistOrder(OrderWriter.java:26)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.OrderWriter$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.FixedCheckoutService.createTwoOrders(FixedCheckoutService.java:26)
>>>      com.zenith.data.LessonCheck25.run(LessonCheck25.java:40)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(2064484576<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.OrderWriter.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(2064484576<open>)]
Closing JPA EntityManager [SessionImpl(2064484576<open>)] after transaction
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 1
```

Afterwards, in psql:

```text
 id |  customer_email   | quantity 
----+-------------------+----------
  1 | first@example.com |        1
(1 row)
```

- (a) **2.** Each `writer.persistOrder(...)` is a separate call through `OrderWriter`'s proxy, and
  each one got its own transaction.
- (b) **1 row, `first@example.com`.** The first call's transaction committed when `persistOrder`
  returned: `Completing transaction for [com.zenith.data.OrderWriter.persistOrder]`, then
  `Initiating transaction commit`. The second call's transaction rolled back after its exception.
  A rollback undoes only its own transaction, and the first one was already committed.
- (c) The proxy begins a transaction when a call arrives, and ends it when that same call
  returns. Nothing wraps `createTwoOrders` itself: `FixedCheckoutService` has no label, so it has
  no proxy. So the two orders are not one unit. To make both succeed or fail together, the
  transaction has to begin before the first call and end after the second. For example, put
  `@Transactional` on a method that makes both calls and is itself called through a proxy.

(After the checks, `truncate ticket_order restart identity;` emptied the table again, and the
temporary methods and runner were removed.)

---

## Lesson 26 — The second fix: self-injection (subtask S12, the second of three ways)

### Definition

Ideas you need again, in short:

- A **bean** is an object that Spring makes when the app starts and hands to every constructor
  that asks for its type. `@Service` on a class tells Spring to make one.
- A **transaction** is a group of database statements that the database treats as one unit. A
  **commit** keeps every change; a **rollback** undoes every change. With `@Transactional` on a
  method, Spring begins a transaction before the method runs and commits it after the method
  returns. If an unchecked exception (a `RuntimeException`, or a class that extends it) comes out,
  Spring rolls back instead.
- A **proxy** is a stand-in object; the real object behind it is the **target**. For a bean with a
  `@Transactional` method, Spring hands out a proxy whose class, such as
  `SelfInjectedCheckoutService$$SpringCGLIB$$1`, is a subclass of your class, written in memory at
  startup. The subclass **overrides** your methods (replaces them with its own versions), so it
  can step in before each call.
- **The route of a call from outside:** the call lands on the proxy's version of the method, which
  hands it to Spring's **handler** (`CglibAopProxy$DynamicAdvisedInterceptor.intercept`). For a
  method with `@Transactional`, the handler runs the **transaction step**
  (`TransactionInterceptor`), which begins the transaction and then calls your method on the
  target; the handler shows line 728. For a method without it, the handler calls the target
  directly; it shows line 724.
- Inside your own method, **`this`** is the target, not the proxy.
- **The self-invocation trap:** `createOrder` has no annotation and calls `this.persistOrder(...)`,
  which has `@Transactional` and throws right after its save. The call through `this` skips the
  proxy, so no transaction begins. `save()` begins its own short transaction and commits at once,
  so the row stays after the exception.
- **The first fix:** `persistOrder` moved into a separate `@Service` class, `OrderWriter`. The
  service with `createOrder` asked for an `OrderWriter` in its constructor, and Spring **injected**
  (passed in) `OrderWriter`'s proxy. `createOrder` called `writer.persistOrder(...)` through that
  field, so the call went through a proxy and the failure rolled back.
- A repository's `save()` joins an open transaction (`Participating in existing transaction`), or
  begins and commits its own if none is open.
- `CallPath.print()` prints the calls that are running right now, newest first, keeping only your
  own classes, the handler and the transaction step.
  `TransactionSynchronizationManager.isActualTransactionActive()` returns `true` when a transaction
  is open for the code that's running right now.

New words:

- **Self-injection.** A bean asks Spring for itself in its own constructor, and keeps what it gets
  in a field, here called `self`. Then it can call its own methods through `self` instead of
  `this`.
- **Cycle.** Beans that need each other before they can be made, so Spring can't make any of them
  first. Here the cycle is one bean that needs itself. Spring's messages also call this a
  **circular reference**.
- **`@Lazy`** on a constructor parameter means "don't look this bean up now". Spring passes in a
  stand-in right away, and the stand-in looks up the real bean only when you call a method on it.
- **Lazy stand-in.** The object that `@Lazy` passes in. It is one more proxy: one more subclass of
  your class, written in memory. It has no extra steps of its own; it finds the real bean and
  passes each call on.

The second fix in one sentence: `persistOrder` stays in its class, and `createOrder` calls it
through `self.persistOrder(...)` instead of `this.persistOrder(...)`.

### Why it is needed

- **Subtask S12 asks for three ways** to restructure the code to fix the trap. The separate bean is
  the first. Self-injection is the second.
- **Sometimes moving the method out is awkward**, because it belongs with the rest of its class.
  Self-injection lets it stay where it is.
- **It shows the rule from a new side.** The method stays in the same class, yet now it gets its
  transaction. What matters is the route of the call, not the class the method lives in.

### Key points

- A bean can't simply ask for itself in its constructor, because the bean exists only after that
  constructor has finished. Spring Boot stops at startup with "form a cycle".
- `@Lazy` on that parameter makes Spring pass in a lazy stand-in instead. The stand-in looks up the
  real bean only when a method is called on it. By then, the bean is finished and wrapped in its
  transaction proxy.
- A call to `self.persistOrder(...)` lands on the stand-in, which passes it to the transaction
  proxy. That proxy's handler runs the transaction step, which calls `persistOrder` on the same
  target.
- `self` holds the stand-in (`$$SpringCGLIB$$0` here). It is neither the target nor the transaction
  proxy (`$$SpringCGLIB$$1` here).
- The field only helps if the call goes through it. `persistOrder(...)` or `this.persistOrder(...)`
  is the trap again, with no error and no warning.
- The rule is the same as before: the transaction step runs only when a call arrives through a
  proxy at a method with `@Transactional`. Writing `self.` in front of the call gives it that
  route.

### How it works, step by step

**Part 1: why a bean can't simply ask for itself.**

1. To make a bean, Spring calls the class's constructor, passing in whatever the constructor asks
   for.
2. Suppose the constructor asks for a `SelfInjectedCheckoutService`. Spring must hand one in
   before the constructor can run.
3. But the only `SelfInjectedCheckoutService` bean is the one Spring is making right now. It
   doesn't exist until this constructor has finished.
4. So Spring would need the bean in order to make the bean. That's a cycle. Spring Boot stops at
   startup and draws the cycle for you; the first common mistake shows the real error.

**Part 2: what `@Lazy` changes.**

1. With `@Lazy` on the parameter, Spring doesn't look for the bean. It writes one more subclass of
   your class in memory, makes one object of it (the lazy stand-in), and passes that in. The
   constructor runs and stores the stand-in in `self`.
2. Spring finishes the bean. Because `persistOrder` has `@Transactional`, Spring wraps the bean in
   its transaction proxy, as for any such bean. That proxy is what other beans receive, such as
   the demo.
3. Later, `createOrder` runs on the target and calls `self.persistOrder(...)`. The call lands on
   the stand-in.
4. The stand-in has no extra steps of its own. It asks Spring for the bean
   `SelfInjectedCheckoutService`. The bean is finished by now, so Spring hands back the
   transaction proxy, and the stand-in passes the call on to it.
5. The transaction proxy sees `@Transactional` on `persistOrder`. Its handler runs the transaction
   step, which begins the transaction and then calls `persistOrder` on the target.

So the call leaves the target, goes through the stand-in and the transaction proxy, and comes back
to the same target, this time inside a transaction:

```text
SelfInjectionDemo.run
  | checkout.createOrder(...)              checkout holds the transaction proxy ($$1)
  v
[transaction proxy $$1]                    createOrder has no label: call the target directly
  v
SelfInjectedCheckoutService.createOrder    runs on the target
  | self.persistOrder(...)                 self holds the lazy stand-in ($$0)
  v
[lazy stand-in $$0]                        no steps of its own: find the bean, pass the call on
  v
[transaction proxy $$1]                    persistOrder has a label: run the transaction step
  v
[transaction step]                         begins the transaction
  v
SelfInjectedCheckoutService.persistOrder   the same target, now inside the transaction
```

**About the numbers in the class names.** After `$$SpringCGLIB$$` comes a counter. The first class
Spring writes for `SelfInjectedCheckoutService` gets `0`, and the next one gets `1`. Spring wrote
the stand-in first, because it needed it to call the constructor. It wrote the transaction proxy
afterwards, once the bean was made. So here `$$0` is the stand-in and `$$1` is the transaction
proxy.

### Example

All files are in `scratch-app/src/main/java/com/zenith/data/`.

`SelfInjectedCheckoutService.java` is a new file, 45 lines:

- Line 3 imports `@Lazy`.
- Line 16 is the new field, `self`.
- Lines 18 to 22 are the constructor. It asks for an `OrderRepository` and for a
  `SelfInjectedCheckoutService`, with `@Lazy` on line 19.
- `createOrder` (line 25) has no annotation, just like in the trap. It prints who `this` is
  (lines 26 and 27) and what `self` holds (line 28). Then it calls `self.persistOrder(...)` on
  line 29.
- `persistOrder` is the same as in the trap: `@Transactional` on line 32, `CallPath.print()` on
  line 38, the save on line 39 and the `throw` on line 42.

```java
package com.zenith.data;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// Lesson 26: the second fix. persistOrder stays in this class, and createOrder calls it through
// "self", a reference to this same bean that Spring injects. @Lazy lets the constructor ask for it.
@Service
public class SelfInjectedCheckoutService {

    private final OrderRepository orders;
    private final SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(OrderRepository orders,
                                       @Lazy SelfInjectedCheckoutService self) {
        this.orders = orders;
        this.self = self;
    }

    // No annotation, as in the trap. The call goes through "self" instead of "this".
    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    self is: " + self.getClass().getName());
        self.persistOrder(eventId, customerEmail, failAfterSave);
    }

    @Transactional
    public void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}
```

`SelfInjectionDemo.java` is new too, 37 lines. Line 28 prints what the demo's own field holds.
Line 31 calls `createOrder` with a failing input. Then the demo prints the number of orders.

```java
package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 26: the second fix. createOrder has no annotation and calls self.persistOrder(...),
// which fails after the save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=self-injection
@Component
@Profile("self-injection")
class SelfInjectionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final SelfInjectedCheckoutService checkout;
    private final OrderRepository orders;

    SelfInjectionDemo(SelfInjectedCheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> the demo's field holds: " + checkout.getClass().getName());
        System.out.println(">>> checkout.createOrder(...), which calls self.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "self@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
```

`CallPath.java` is unchanged, 20 lines:

```java
package com.zenith.data;

// Lesson 23: prints the calls that are running right now, newest first. It keeps only your own
// classes, the proxy's handler, and the transaction step. The full list is about 30 lines.
class CallPath {

    static void print() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        System.out.println(">>>    call path, newest first:");
        // frames[0] is getStackTrace itself and frames[1] is this method, so start at 2.
        for (int i = 2; i < frames.length; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.zenith")
                    || className.endsWith("DynamicAdvisedInterceptor")
                    || className.endsWith("TransactionInterceptor")) {
                System.out.println(">>>      " + frames[i]);
            }
        }
    }
}
```

The command, on an empty table. The two log settings go inside one pair of quotes:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=self-injection -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE"
```

Real output after `Started DataApplication`, with the front of each log line cut off.
`orders.count()` has its own short read-only transaction of 13 lines, folded here into one line:

```text
>>> the demo's field holds: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1
>>> checkout.createOrder(...), which calls self.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1564087423
>>>    self is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
Creating new transaction with name [com.zenith.data.SelfInjectedCheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
Opened new EntityManager [SessionImpl(976475733<open>)] for JPA transaction
Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect$HibernateConnectionHandle@6f78c132]
Getting transaction for [com.zenith.data.SelfInjectedCheckoutService.persistOrder]
>>>    inside persistOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1564087423
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.SelfInjectedCheckoutService.persistOrder(SelfInjectedCheckoutService.java:38)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.persistOrder(<generated>)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.SelfInjectedCheckoutService.createOrder(SelfInjectedCheckoutService.java:29)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.createOrder(<generated>)
>>>      com.zenith.data.SelfInjectionDemo.run(SelfInjectionDemo.java:31)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
Found thread-bound EntityManager [SessionImpl(976475733<open>)] for JPA transaction
Participating in existing transaction
Getting transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
Hibernate: 
    insert 
    into
        ticket_order
        (customer_email, event_id, quantity) 
    values
        (?, ?, ?)
Completing transaction for [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]
>>>    order saved
Completing transaction for [com.zenith.data.SelfInjectedCheckoutService.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
Initiating transaction rollback
Rolling back JPA transaction on EntityManager [SessionImpl(976475733<open>)]
Closing JPA EntityManager [SessionImpl(976475733<open>)] after transaction
>>>    caught RuntimeException: payment failed after the order was saved
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 0
```

Afterwards, `select id, customer_email, quantity from ticket_order order by id;` in psql:

```text
 id | customer_email | quantity 
----+----------------+----------
(0 rows)
```

### Walkthrough

1. **`the demo's field holds: ...$$SpringCGLIB$$1`.** That's the transaction proxy. Spring hands it
   out, as for any bean with a `@Transactional` method.
2. **`inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1564087423`.**
   `createOrder` runs on the target, as always.
3. **`self is: ...$$SpringCGLIB$$0`.** This is a third object, the lazy stand-in. It isn't the
   target, which has the plain name. It isn't the transaction proxy either, which is `$$1`.
4. **`Creating new transaction with name [com.zenith.data.SelfInjectedCheckoutService.persistOrder]`**
   comes before the first line inside `persistOrder`. The transaction began before the method did.
5. **`inside persistOrder, this is: ..., number 1564087423`.** This is the same number as in step
   2, so it's the same target. The call went out, through two stand-ins, and came back to the same
   object.
6. **`transaction open: true`.**
7. **The call path**, read from the bottom up:

   | # | Line | What it is |
   | --- | --- | --- |
   | 1 | `DataApplication.main(DataApplication.java:10)` | Where the app starts. |
   | 2 | `SelfInjectionDemo.run(SelfInjectionDemo.java:31)` | Line 31 is `checkout.createOrder(SUFI_NIGHT, "self@example.com", true);`. |
   | 3 | `SelfInjectedCheckoutService$$SpringCGLIB$$1.createOrder(<generated>)` | The call lands on the transaction proxy, which the demo's field holds. `<generated>` means the class was written in memory, so it has no source file. |
   | 4 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)` | Its handler. `createOrder` has no label, so the handler takes line 724: call the target directly. |
   | 5 | `SelfInjectedCheckoutService.createOrder(SelfInjectedCheckoutService.java:29)` | `createOrder`, on the target. Line 29 is `self.persistOrder(eventId, customerEmail, failAfterSave);`. |
   | 6 | `SelfInjectedCheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)` | The call lands on the lazy stand-in, which `self` holds. |
   | 7 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)` | The stand-in's handler. It has no extra steps, so it takes line 724 again and passes the call on to the bean it found. |
   | 8 | `SelfInjectedCheckoutService$$SpringCGLIB$$1.persistOrder(<generated>)` | The bean it found is the transaction proxy, the same one as in row 3. |
   | 9 | `CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)` | Its handler. `persistOrder` has a label, so the handler takes line 728: run the extra steps. |
   | 10 | `TransactionInterceptor.invoke(TransactionInterceptor.java:119)` | The transaction step. The transaction is already open at this point. |
   | 11 | `SelfInjectedCheckoutService.persistOrder(SelfInjectedCheckoutService.java:38)` | `persistOrder`, on the target. Line 38 is `CallPath.print();`. |

   Rows 6 to 10 are the route that `self.` adds. In the trap, nothing sat between `createOrder`
   and `persistOrder`.
8. **`Participating in existing transaction`.** `save()` joined `persistOrder`'s transaction.
   There's no `Initiating transaction commit` after this `insert`.
9. **`after exception: ...`, then `Initiating transaction rollback`.** The transaction step saw the
   exception and undid the `insert`.
10. **`orders in the table: 0`**, and psql shows `(0 rows)`.

The trap and the second fix, side by side. The trap's column comes from the second common mistake,
a real run of exactly that change in this class:

| | The trap | The second fix |
| --- | --- | --- |
| Line 29 | `persistOrder(...)` (Java adds `this.`) | `self.persistOrder(...)` |
| What that call lands on | the target, directly | the lazy stand-in, which passes it to the transaction proxy |
| Between `createOrder` and `persistOrder` in the call path | nothing | the stand-in, its handler (724), the transaction proxy, its handler (728), `TransactionInterceptor.invoke` |
| `transaction open:` | `false` | `true` |
| After the exception | nothing to roll back | rollback |
| Rows added | 1 | 0 |

And the two fixes, side by side:

| | First fix: separate bean | Second fix: self-injection |
| --- | --- | --- |
| Where `persistOrder` lives | in a new class, `OrderWriter` | it stays in the same class |
| What the field holds | `OrderWriter`'s proxy | a lazy stand-in that leads to this bean's own proxy |
| Needs `@Lazy` | no | yes, or the app won't start |
| Proxies the call passes through | one | two |

### Important terms

| Term | Meaning |
| --- | --- |
| **Self-injection** | A bean asks Spring for itself in its own constructor and keeps it in a field, so it can call its own methods through that field instead of `this`. |
| **Cycle** (circular reference) | Beans that need each other, or themselves, before they can be made, so Spring can't make any of them first. Spring Boot refuses cycles by default. |
| **`@Lazy`** (on a constructor parameter) | "Don't look this bean up now": Spring passes in a stand-in at once, which looks up the real bean only when a method is called on it. Import: `org.springframework.context.annotation.Lazy`. |
| **Lazy stand-in** | The object `@Lazy` passes in: one more subclass of your class, written in memory, with no extra steps of its own. It finds the real bean and passes each call on. |
| **`self`** | The field that holds the lazy stand-in. Calls through it reach the bean's own transaction proxy. |
| **`$$SpringCGLIB$$0`, `$$SpringCGLIB$$1`** | Classes Spring wrote in memory for `SelfInjectedCheckoutService`, numbered in the order they were written. Here `0` is the stand-in and `1` the transaction proxy. |

### Where it is used

- **When the method belongs with the rest of its class**, and moving it out would split code that
  belongs together.
- **Many teams still prefer the separate bean.** With a separate bean, a reader sees the route at a
  glance. With self-injection, the reader must know what `self` really holds, and that `self.` is
  the important part of the call.
- **Other Spring labels that work through the proxy**, such as `@Cacheable` and `@Async`, have the
  same trap. The same fix works for them.
- In this task: subtask S12, the second of its three ways.

### Edge cases

- **The class numbers have no fixed meaning.** They follow the order in which Spring wrote the
  classes. In a bean without a `self` field, the transaction proxy is the only class Spring
  writes, so it gets `0`, as with `CheckoutService$$SpringCGLIB$$0`. Here the stand-in was written
  first.
- **The lookup happens when a method is called, not at startup.** While the constructor ran, the
  bean didn't exist yet, and the constructor already held `self`. Only `self.persistOrder(...)`,
  long after startup, made the stand-in find the transaction proxy.
- **`self` doesn't help a `private` method.** A subclass can't override a private method, so
  neither the stand-in nor the transaction proxy can step in. The second practice question shows
  what happens. Spring printed no warning about it: the run's log had no `WARN` line at all.
- **A field marked `@Autowired`, with no `@Lazy`, doesn't get around the cycle by itself.**
  (`@Autowired` on a field tells Spring to fill in that field after the constructor has run.) An AI
  answer may show this form. In a real run of it on Spring Boot 3, the app stopped with
  `form a cycle` too. This time the box named the field instead of the class file:
  `selfInjectedCheckoutService (field private com.zenith.data.SelfInjectedCheckoutService com.zenith.data.SelfInjectedCheckoutService.self)`.

### Common mistakes — with the real errors

1. **Forgetting `@Lazy`.** `@Lazy ` was deleted from line 19, and the demo was run. The app stopped
   before any `>>>` line. The error, with the start of the path shortened to `...`:

   ```text
   ***************************
   APPLICATION FAILED TO START
   ***************************

   Description:

   The dependencies of some of the beans in the application context form a cycle:

   ┌──->──┐
   |  selfInjectedCheckoutService defined in file [.../target/classes/com/zenith/data/SelfInjectedCheckoutService.class]
   └──<-──┘


   Action:

   Relying upon circular references is discouraged and they are prohibited by default. Update your application to remove the dependency cycle between beans. As a last resort, it may be possible to break the cycle automatically by setting spring.main.allow-circular-references to true.
   ```

   Further up, Spring's warning line gives the reason in its own words:

   ```text
   org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'selfInjectedCheckoutService' defined in file [.../target/classes/com/zenith/data/SelfInjectedCheckoutService.class]: Unsatisfied dependency expressed through constructor parameter 1: Error creating bean with name 'selfInjectedCheckoutService': Requested bean is currently in creation: Is there an unresolvable circular reference or an asynchronous initialization dependency?
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `APPLICATION FAILED TO START` | Spring couldn't finish making the beans, so the app never ran. |
   | `The dependencies of some of the beans ... form a cycle:` | "Dependencies" are the things a bean asks for in its constructor. Some beans need each other, or themselves, before they can be made, so Spring can't make any of them first. |
   | The box `┌──->──┐` … `└──<-──┘` with one name inside | The cycle has just one bean. The arrow leaves `selfInjectedCheckoutService` and comes straight back to it: the bean needs itself. |
   | `selfInjectedCheckoutService` | The bean's name: the class name, starting with a small letter. |
   | `defined in file [.../SelfInjectedCheckoutService.class]` | Where Spring found the bean: your compiled class. |
   | `constructor parameter 1` | The constructor's second parameter (Java counts from 0): `SelfInjectedCheckoutService self`. |
   | `Requested bean is currently in creation` | Spring was asked for `selfInjectedCheckoutService` while it was still making it. |
   | `Relying upon circular references is discouraged and they are prohibited by default.` | Spring Boot refuses cycles unless you tell it otherwise. |
   | `Update your application to remove the dependency cycle between beans.` | Its main advice: change the code so that no bean needs itself, or needs a bean that needs it back. |
   | `As a last resort, ... setting spring.main.allow-circular-references to true.` | A setting that sometimes helps. Whether it helps here is the first practice question. |

   The fix: put `@Lazy ` back on line 19.

2. **Adding `self`, but not using it.** Line 29 was changed to
   `        persistOrder(eventId, customerEmail, failAfterSave);`, keeping the field and `@Lazy`,
   and the demo was run on an empty table. The output, filtered with `grep` and with the front of
   each log line cut off. The demo's label still says `self.persistOrder`, because only line 29
   changed:

   ```text
   >>> the demo's field holds: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1
   >>> checkout.createOrder(...), which calls self.persistOrder(...)
   >>>    inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1108121945
   >>>    self is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
   >>>    inside persistOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1108121945
   >>>    transaction open: false
   >>>    call path, newest first:
   >>>      com.zenith.data.SelfInjectedCheckoutService.persistOrder(SelfInjectedCheckoutService.java:38)
   >>>      com.zenith.data.SelfInjectedCheckoutService.createOrder(SelfInjectedCheckoutService.java:29)
   >>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
   >>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.createOrder(<generated>)
   >>>      com.zenith.data.SelfInjectionDemo.run(SelfInjectionDemo.java:31)
   >>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.save]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
       insert 
   Initiating transaction commit
   >>>    order saved
   >>>    caught RuntimeException: payment failed after the order was saved
   Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
   Initiating transaction commit
   >>>    orders in the table: 1
   ```

   - `self is: ...$$0` shows that the stand-in is there, but the call didn't use it.
   - A call with nothing in front of it goes to `this`, so this is the trap again: nothing sits
     between `createOrder` and `persistOrder`, the save commits on its own, and the count is 1.
   - There's no error and no warning. The field only helps if the call goes through it.

3. **Forgetting the import.** With line 3 (`import org.springframework.context.annotation.Lazy;`)
   deleted, the build stopped, with the start of the path shortened to `...`:

   ```text
   [ERROR] COMPILATION ERROR : 
   [ERROR] .../src/main/java/com/zenith/data/SelfInjectedCheckoutService.java:[18,41] cannot find symbol
     symbol:   class Lazy
     location: class com.zenith.data.SelfInjectedCheckoutService
   ```

   | Part | Plain meaning |
   | --- | --- |
   | `COMPILATION ERROR` | Java couldn't turn the file into a class, so nothing ran. |
   | `[18,41]` | Line 18, column 41. Without the import the file is one line shorter, so the `@Lazy` line, normally line 19, is now line 18. Column 41 is where the word `Lazy` starts, right after `@`. |
   | `cannot find symbol` | Java met a name it doesn't know. |
   | `symbol:   class Lazy` | The unknown name is the class `Lazy`: the annotation's class. |
   | `location: class com.zenith.data.SelfInjectedCheckoutService` | Where the name was used. |

   The fix: put the import back as line 3.

**A quick check for the first two:** the `self is:` line must show a `$$SpringCGLIB$$` name, and
every call that needs the transaction must start with `self.`.

### Link to the task

Subtask S12 says: "Ask AI: *"Explain Spring's transaction proxy in detail, why self-invocation
bypasses @Transactional, and show me three ways to restructure the code to fix it."*"

- **Way 1, a separate bean:** `persistOrder` moved into `OrderWriter`, and the call went through
  `OrderWriter`'s proxy.
- **Way 2, self-injection:** this lesson. `persistOrder` stayed in its class, and the call went
  through `self`. The run rolled back, and the table stayed at 0 rows.
- **Way 3, `TransactionTemplate`:** still to come.

When you send the prompt to an AI, check its answer against what you ran:

- It should say that the transaction comes from a proxy that Spring hands out in place of your
  object. You saw the `$$SpringCGLIB$$` class names.
- It should say that a call through `this` reaches the target directly, so the proxy never sees
  it. You saw nothing between `createOrder` and `persistOrder` in the trap's call path.
- If one of its ways is self-injection, its code should delay the lookup, for example with
  `@Lazy`. Without that, Spring Boot 3 stops at startup with the cycle error from the first common
  mistake.

S12 is in progress: two of its three ways have been run for real.

### Do it yourself (second laptop)

**Starting point.** You have the scratch app from the earlier lessons:

- `OrderEntity.java`, `OrderRepository.java` and `CallPath.java` (20 lines) exist, and so does the
  `ticket_order` table;
- `ticket_order` is empty, with its numbering restarted at 1;
- Postgres runs in the Docker container `pg`.

If `CallPath.java` is missing, create it with the 20 lines from the example.

**Step 1 — start Postgres.** Run `docker start pg`. It prints `pg`. If it prints
`Cannot connect to the Docker daemon`, Docker itself isn't running. Start Docker first
(`colima start` if you use Colima, or open Docker Desktop), then run `docker start pg` again.

**Step 2 — add the service.** Create
`scratch-app/src/main/java/com/zenith/data/SelfInjectedCheckoutService.java` with the 45 lines from
the example.

**Step 3 — add the demo.** Create
`scratch-app/src/main/java/com/zenith/data/SelfInjectionDemo.java` with the 37 lines from the
example.

**Step 4 — run it.** `grep -E` keeps only the lines that contain one of the six patterns:

```bash
cd ~/foundations/F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=self-injection -Dspring-boot.run.arguments="--logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE" | grep -E '>>>|Creating new transaction|insert|after exception|transaction rollback|transaction commit'
```

Real output. The start of the long lines (time, process number, logger) and the object numbers
will differ on your laptop:

```text
>>> the demo's field holds: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1
>>> checkout.createOrder(...), which calls self.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1564087423
>>>    self is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
2026-11-04T00:17:43.615+05:30 DEBUG 4933 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [com.zenith.data.SelfInjectedCheckoutService.persistOrder]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
>>>    inside persistOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1564087423
>>>    transaction open: true
>>>    call path, newest first:
>>>      com.zenith.data.SelfInjectedCheckoutService.persistOrder(SelfInjectedCheckoutService.java:38)
>>>      org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.persistOrder(<generated>)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0.persistOrder(<generated>)
>>>      com.zenith.data.SelfInjectedCheckoutService.createOrder(SelfInjectedCheckoutService.java:29)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.createOrder(<generated>)
>>>      com.zenith.data.SelfInjectionDemo.run(SelfInjectionDemo.java:31)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
    insert 
>>>    order saved
2026-11-04T00:17:43.647+05:30 TRACE 4933 --- [           main] o.s.t.i.TransactionInterceptor           : Completing transaction for [com.zenith.data.SelfInjectedCheckoutService.persistOrder] after exception: java.lang.RuntimeException: payment failed after the order was saved
2026-11-04T00:17:43.647+05:30 DEBUG 4933 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction rollback
>>>    caught RuntimeException: payment failed after the order was saved
2026-11-04T00:17:43.649+05:30 DEBUG 4933 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Creating new transaction with name [org.springframework.data.jpa.repository.support.SimpleJpaRepository.count]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
2026-11-04T00:17:43.676+05:30 DEBUG 4933 --- [           main] o.s.orm.jpa.JpaTransactionManager        : Initiating transaction commit
>>>    orders in the table: 0
```

The `Creating new transaction ... count` line and the commit after it belong to `count()`'s own
short transaction.

**Step 5 — look at the table.**

```bash
docker exec pg psql -U postgres -d zenith -c "select id, customer_email, quantity from ticket_order order by id;"
```

Expected: no rows. The last line is `(0 rows)`.

**Step 6 — break it by removing `@Lazy`.**

1. In `SelfInjectedCheckoutService.java`, delete `@Lazy ` (with its space) from line 19.
2. Run the command from step 4 without the `| grep ...` part. The app stops before any `>>>` line,
   with `APPLICATION FAILED TO START` and
   `The dependencies of some of the beans in the application context form a cycle:`.
3. Put `@Lazy ` back on line 19, right before `SelfInjectedCheckoutService self) {`.

**Step 7 — break it by not using `self`.**

1. Change line 29 to `        persistOrder(eventId, customerEmail, failAfterSave);`.
2. Run the command from step 4. You should see `transaction open: false`, the save's own
   `Initiating transaction commit`, and `orders in the table: 1`.
3. Put `self.` back at the start of the call on line 29.

**Step 8 — empty the table for the next lesson.**

```bash
docker exec pg psql -U postgres -d zenith -c "truncate ticket_order restart identity;"
docker exec pg psql -U postgres -d zenith -c "select count(*) from ticket_order;"
```

The first prints `TRUNCATE TABLE`, the second a count of `0`.

If it goes wrong:

- `APPLICATION FAILED TO START` with `form a cycle`: `@Lazy` is missing from line 19.
- The build stops with `cannot find symbol` and `symbol:   class Lazy`: the import on line 3 is
  missing. The error names line 18, because the file is one line shorter without the import:
  `SelfInjectedCheckoutService.java:[18,41] cannot find symbol`.
- `transaction open: false` and `orders in the table: 1`: line 29 calls `persistOrder(...)` or
  `this.persistOrder(...)` instead of `self.persistOrder(...)`.
- `Connection to localhost:5432 refused`: Postgres isn't running. Do step 1 again.
- `Unable to parse command line options: Unrecognized option: --logging.level.org.springframework.transaction.interceptor.TransactionInterceptor=TRACE`:
  the quotes are missing.

**Done when:**

- the output showed `the demo's field holds: ...$$SpringCGLIB$$1` and
  `self is: ...$$SpringCGLIB$$0`;
- it showed
  `Creating new transaction with name [com.zenith.data.SelfInjectedCheckoutService.persistOrder]`
  and `transaction open: true`;
- the call path showed the stand-in (`$$0`) and the transaction proxy (`$$1`) between `createOrder`
  and `persistOrder`;
- it ended with `Initiating transaction rollback` and `orders in the table: 0`, and psql showed
  `(0 rows)`;
- you have seen both break-its and put the code back;
- the table is empty again.

### Summary for revision

- Self-injection: the bean asks Spring for itself, keeps it in `self`, and calls
  `self.persistOrder(...)` instead of `this.persistOrder(...)`.
- Without `@Lazy`, the app can't start: the constructor needs the bean, and the bean exists only
  after the constructor. Spring Boot reports `form a cycle`.
- `@Lazy` passes in a lazy stand-in at once. The stand-in finds the real bean, the transaction
  proxy, only when a method is called on it.
- `self.persistOrder(...)` lands on the stand-in, then on the transaction proxy, whose handler runs
  the transaction step. A failure rolls back.
- In the run, `$$0` was the stand-in and `$$1` the transaction proxy; the numbers follow the order
  in which Spring wrote the classes.
- The field helps only if the call goes through it. Without `self.`, it is the trap again, and
  silent.
- Rule: the transaction step runs only when a call arrives through a proxy at a method with
  `@Transactional`.

### Practice questions, with answers

**Question 1.** `SelfInjectedCheckoutService` is a `@Service` with a `@Transactional` method,
`persistOrder`. You remove `@Lazy`, so its constructor is:

```java
    public SelfInjectedCheckoutService(OrderRepository orders,
                                       SelfInjectedCheckoutService self) {
        this.orders = orders;
        this.self = self;
    }
```

The app stops at startup with `APPLICATION FAILED TO START`. The error ends with: "Relying upon
circular references is discouraged and they are prohibited by default. Update your application to
remove the dependency cycle between beans. As a last resort, it may be possible to break the cycle
automatically by setting spring.main.allow-circular-references to true."

You follow that last hint. Still without `@Lazy`, you run:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=self-injection -Dspring-boot.run.arguments="--spring.main.allow-circular-references=true"
```

- (a) Does the app start this time?
- (b) Explain your answer: what must Spring have in hand before it can call this constructor?

**Answer.** It was run for real. The app stopped again before any `>>>` line, with the start of the
path shortened to `...`:

```text
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌──->──┐
|  selfInjectedCheckoutService defined in file [.../target/classes/com/zenith/data/SelfInjectedCheckoutService.class]
└──<-──┘


Action:

Despite circular references being allowed, the dependency cycle between beans could not be broken. Update your application to remove the dependency cycle.
```

The warning line further up was the same as without the setting:
`Unsatisfied dependency expressed through constructor parameter 1: Error creating bean with name 'selfInjectedCheckoutService': Requested bean is currently in creation`.

- (a) **No.** Only the `Action:` text changed: "Despite circular references being allowed, the
  dependency cycle between beans could not be broken."
- (b) To call this constructor, Spring must first have a `SelfInjectedCheckoutService` to pass in.
  The only one is the bean being made, and no object of it exists until this very constructor has
  finished. The setting lets Spring hand out a bean before it is completely set up, but only an
  object that already exists, one whose constructor has finished. Here there is no such object
  yet, so no setting can help. A stand-in can, because Spring makes it separately and passes it in
  at once. That is what `@Lazy` does.

A real run showed the difference. In a temporary version, `self` was a field marked `@Autowired`
(Spring fills it in after the constructor has run), with no `@Lazy`:

```java
    private final OrderRepository orders;
    @Autowired
    private SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(OrderRepository orders) {
        this.orders = orders;
    }
```

Without the setting, the app stopped with `form a cycle` and the "Relying upon circular references
is discouraged ..." advice; this time the box named the field `self` instead of the class file.
With the setting, the app started. Its `>>>` lines, without the call path:

```text
>>> the demo's field holds: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
>>> checkout.createOrder(...), which calls self.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1823595027
>>>    self is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
>>>    inside persistOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 1823595027
>>>    transaction open: true
>>>    order saved
>>>    caught RuntimeException: payment failed after the order was saved
>>>    orders in the table: 0
```

Here the object already existed when Spring filled in the field, so Spring could hand it out
early, already wrapped in its proxy. `self` held the transaction proxy itself (the same class as
the demo's field), and the failure rolled back. With no stand-in written, the transaction proxy was
the only class Spring wrote, so it got the number `0`.

**Question 2.** `@Lazy` is back. You make one change: `persistOrder` becomes `private`, and it keeps
its `@Transactional`. The class is now:

```java
@Service
public class SelfInjectedCheckoutService {

    private final OrderRepository orders;
    private final SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(OrderRepository orders,
                                       @Lazy SelfInjectedCheckoutService self) {
        this.orders = orders;
        this.self = self;
    }

    public void createOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside createOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    self is: " + self.getClass().getName());
        self.persistOrder(eventId, customerEmail, failAfterSave);
    }

    @Transactional
    private void persistOrder(UUID eventId, String customerEmail, boolean failAfterSave) {
        System.out.println(">>>    inside persistOrder, this is: " + this.getClass().getName()
                + ", number " + System.identityHashCode(this));
        System.out.println(">>>    transaction open: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        CallPath.print();
        orders.save(new OrderEntity(eventId, customerEmail, 1));
        System.out.println(">>>    order saved");
        if (failAfterSave) {
            throw new RuntimeException("payment failed after the order was saved");
        }
    }
}
```

Facts you need:

- The demo's field holds the transaction proxy, and `self` holds the lazy stand-in. Both are
  objects of subclasses of `SelfInjectedCheckoutService` that Spring wrote in memory.
- A subclass steps in by overriding your methods. Java doesn't let a subclass override a `private`
  method.
- Spring made the stand-in object without running your constructor. So the stand-in's own fields,
  `orders` and `self`, are `null`.
- In Java, code inside a class may call a private method on any object of that same class. So
  `self.persistOrder(...)` still compiles.

The table is empty. The demo runs `checkout.createOrder(SUFI_NIGHT, "self@example.com", true)`
inside `try { ... } catch (RuntimeException e) { ... }`. The catch prints
`">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage()`.

- (a) Which object does `persistOrder` run on? So does `inside persistOrder, this is:` print the
  plain class name, or a name with `$$SpringCGLIB$$`?
- (b) What does `transaction open:` print?
- (c) What does the catch print, roughly? How many rows are in the table afterwards?

**Answer.** It was run for real, with the same command and log settings as the example. The output
after `Started DataApplication`, with the front of each log line cut off:

```text
>>> the demo's field holds: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1
>>> checkout.createOrder(...), which calls self.persistOrder(...)
>>>    inside createOrder, this is: com.zenith.data.SelfInjectedCheckoutService, number 2042303704
>>>    self is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0
>>>    inside persistOrder, this is: com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0, number 121764047
>>>    transaction open: false
>>>    call path, newest first:
>>>      com.zenith.data.SelfInjectedCheckoutService.persistOrder(SelfInjectedCheckoutService.java:38)
>>>      com.zenith.data.SelfInjectedCheckoutService.createOrder(SelfInjectedCheckoutService.java:29)
>>>      org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724)
>>>      com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$1.createOrder(<generated>)
>>>      com.zenith.data.SelfInjectionDemo.run(SelfInjectionDemo.java:31)
>>>      com.zenith.data.DataApplication.main(DataApplication.java:10)
>>>    caught NullPointerException: Cannot invoke "com.zenith.data.OrderRepository.save(Object)" because "this.orders" is null
...  (count()'s own short read-only transaction: 13 lines)
>>>    orders in the table: 0
```

psql showed `(0 rows)`.

- (a) **On the stand-in itself.** `this is:` printed
  `com.zenith.data.SelfInjectedCheckoutService$$SpringCGLIB$$0, number 121764047`: the stand-in's
  class, and a different number from the target's `2042303704`.
- (b) **`false`.** No transaction step ran.
- (c) **`caught NullPointerException: Cannot invoke "com.zenith.data.OrderRepository.save(Object)" because "this.orders" is null`**,
  and **0 rows**. The `NullPointerException` came before any `insert`, so nothing was written.
  No transaction was open, so nothing was rolled back either.

Why, step by step:

1. `self.persistOrder(...)` is a call on the stand-in. The stand-in's class could not override a
   private method, so it has no version of `persistOrder` of its own.
2. So Java ran the code of `SelfInjectedCheckoutService.persistOrder` directly, on the stand-in
   object. No handler and no transaction step was involved. The call path shows `persistOrder`
   sitting right on top of `createOrder`, with no stand-in line between them.
3. Inside the method, `this` is the stand-in. Its field `orders` is `null`, because Spring made the
   stand-in without running your constructor.
4. `orders.save(...)` therefore threw the `NullPointerException`.

There was no error at startup and no warning in the log. `@Transactional` on a private method
never takes effect, because no proxy can step in on a private method.

(After the checks, `truncate ticket_order restart identity;` emptied the table again, and
`SelfInjectedCheckoutService.java` was put back as in the example.)

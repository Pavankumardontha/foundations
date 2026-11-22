# F6.11 — Relationships, lazy loading, and the N+1 problem

**Learning outcome.** You can map all three relationship kinds and say which side owns the
foreign key, trigger and explain `LazyInitializationException`, count the SQL statements for
a list endpoint and recognise an N+1 on sight, and explain why Lombok's `@Data` on an entity
is dangerous.

**Why this exists.** An events list endpoint that issues one query per event is fine with
five events and fatal with five thousand on-sale. This is the single most common performance
bug in Java backends. You need to be able to see it before you can fix it.

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **`@ManyToOne`** | Many rows of this table point at one row of that one. Many seats belong to one seat map. |
| **`@OneToMany(mappedBy = ...)`** | The other direction. `mappedBy` names the field on the other side that owns the foreign key. |
| **`@ManyToMany`** | Both sides have many of the other, so a separate join table holds the pairs. A user can have many roles and a role many users. |
| **Owning side** | The side holding the foreign key column. Changes to the owning side are what the database records. |
| **Fetch type** | Whether related data is loaded with the parent (`EAGER`) or on first touch (`LAZY`). |
| **Proxy (Hibernate)** | A stand-in object for a lazy relationship. It looks like the real entity until you call a method on it, at which point Hibernate fetches the data. |
| **`LazyInitializationException`** | "You touched a lazy relationship after the transaction ended, so there is no session left to load it with." |
| **Open Session In View** | A setting that keeps the database session open for the whole web request. Convenient, and it hides N+1 problems, so this project disables it. |
| **N+1 problem** | One query to fetch N parents, then one more query for each parent. Twenty-one statements for twenty events. |
| **`JOIN FETCH`** | Telling the query to bring back the parent and its children in one statement. |

## The task's subtasks

- **S1.** Map the real relationships: `SeatEntity` → `SeatMapEntity`, `SeatMapEntity` → its seats, `EventEntity` → `VenueEntity`. Find which table holds each foreign key.
- **S2.** Prove ownership matters: add a seat only to the seat map's list, save, and watch nothing get written. Then write a helper that sets both sides.
- **S3.** Map one `@ManyToMany` (`user` ↔ `role`). Inspect the join table. Write down the rule: a relationship that carries its own data becomes a real entity.
- **S4.** Set every relationship to `LAZY` explicitly, because `@ManyToOne` defaults to `EAGER`.
- **S5.** Break it on purpose: trigger `LazyInitializationException` and read the message.
- **S6.** Understand why this project sets `spring.jpa.open-in-view: false`.
- **S7.** Adopt the fix and project rule: map entities to response records inside the transactional service method.
- **S8.** See the N+1: twenty events, count twenty-one statements, write the number in `NOTES.md`.
- **S9.** Fix it once: `join fetch` or `@EntityGraph`, count again: one.
- **S10.** Learn the one-sentence diagnostic: statements grow with rows ⇒ N+1.
- **S11.** See the three separate ways `@Data` hurts an entity: `toString()`, `equals`/`hashCode`, setters.
- **S12.** Write down the entity rules: no `@Data`; `@Getter` plus explicit constructors; `equals`/`hashCode` on a stable business key; relationships out of `toString()`.
- **S13.** Explain why the database-generated id must not be used in `equals`/`hashCode`, with a before-and-after-save example.

## Roadmap — 19 small lessons

| # | Lesson | What it gives you |
|---|---|---|
| 1 | `@ManyToOne` — a foreign key becomes an object field | Map Event → Venue; the heart of S1 |
| 2 | New tables `seat_map` and `seat` by migration; map Seat → SeatMap | The rest of the S1 mappings |
| 3 | `@OneToMany(mappedBy = ...)` — the list on the other side | A seat map that holds its seats (S1) |
| 4 | The owning side — only the foreign-key side writes | Break it, then fix it with a helper (S2) |
| 5 | `@ManyToMany` — user and role, pairs live in a join table | First half of S3 |
| 6 | When the link itself has data, make it an entity | The project rule in S3 |
| 7 | Fetch types — `LAZY`, `EAGER`, and the `@ManyToOne` trap | Make every relationship `LAZY` (S4) |
| 8 | The Hibernate proxy — the stand-in object a lazy field gives you | The machinery behind S5 |
| 9 | Break it — `LazyInitializationException` in plain code | S5, and checkpoint question 2 |
| 10 | The same crash over HTTP — entity meets JSON writing | S5 done the way it happens in real apps |
| 11 | Open Session In View — why hiding the crash is worse | S6, and checkpoint question 3 |
| 12 | The project rule — records built inside the service | S7; the fix that removes the whole problem class |
| 13 | See the N+1 — twenty events, twenty-one statements | S8; count them, write `NOTES.md` |
| 14 | Fix one — `join fetch` in a `@Query` | S9; one statement again |
| 15 | Fix two — `@EntityGraph`, plus the one-sentence diagnostic | S9 and S10 |
| 16 | `@Data` danger one — `toString()` touches lazy fields, can recurse | S11 |
| 17 | `@Data` danger two — `equals`/`hashCode` on the generated id | S11 and S13 |
| 18 | `@Data` danger three — setters everywhere destroy the rules | S11 |
| 19 | The entity hygiene rule — what we write instead of `@Data` | S12; closes the task |

## The scratch app and the database

The scratch app lives in
`F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`,
package `com.zenith.rel`. Spring Boot 3.5.16, Java 21, Hibernate 6.6.53.

It talks to the same `zenith` database as the earlier stages. Flyway owns the schema. The
migration files `V1__baseline.sql` through `V4__create_ticket_order.sql` are byte-for-byte
copies of the F6.10 files, so Flyway sees the existing database as up to date. The dev data
file `seed-dev-data.sql` sits next to the scratch app.

| Thing | Value |
| --- | --- |
| Docker container | `pg` (image `postgres:17-alpine`) |
| Host and port | `localhost:5432` |
| Database | `zenith` |
| User / password | `postgres` / `dev` |
| SQL logging | `show-sql: true`, `format_sql: true` — on for the whole task |

**One demo at a time.** Each lesson's demo is a `CommandLineRunner` class marked
`@Profile("some-name")`. It runs only when that profile is switched on:
`mvn -q spring-boot:run -Dspring-boot.run.profiles=some-name`. With no profile, the app
starts, runs no demo, and stops.

---

## Lesson 1 — `@ManyToOne`: a foreign key becomes an object field (subtask S1)

### 1. What is it?

`@ManyToOne` is a JPA annotation you put on a field inside an entity. It tells Hibernate:
"this field holds the one row that this row's foreign key points at — give it to me as a
Java object."

Two reminders, so this lesson stands on its own. An **entity** is a Java class that stands
for one table; one object is one row. A **foreign key** is a column that holds the id of a
row in another table.

### 2. Why do we need it?

This is `EventEntity` as it stood at the end of F6.10:

```java
@Entity
@Table(name = "event")
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug
    // ...
}
```

Look at `venueId`. It is a bare `UUID`. It is just a number that names a venue. The object
knows *which* venue, but nothing *about* the venue.

So every question about the venue costs a second, hand-written query. "What is the venue's
name?" — write a query. "What city?" — write a query. Every screen that shows an event with
its venue name repeats this stitching work. That is the pain `@ManyToOne` removes.

### 3. How does it work?

Three steps:

1. Replace the bare id field with a field of the other entity's type:
   `private VenueEntity venue;`.
2. Put `@ManyToOne` on it. Read the name while standing on the class you are in:
   **many** events point **to one** venue. The annotation always goes on the "many" side —
   the class whose table holds the foreign key column.
3. Put `@JoinColumn(name = "venue_id")` under it. `@JoinColumn` names the column **in this
   entity's own table** that holds the key. For `EventEntity`, that is the `venue_id`
   column of the `event` table.

From then on, when Hibernate loads an event, it reads `venue_id`, loads that venue row too,
and hands you a filled `VenueEntity` object. You write zero queries. You just call
`event.getVenue().getName()`.

One honest note: today Hibernate loads the venue *immediately*, together with the event.
That choice is called `EAGER`. The other choice, load it only when first touched, is called
`LAZY`. Lesson 7 is entirely about this choice, because the default here is a trap. In this
lesson we only read data, so the default is fine.

### 4. Simple example

`VenueEntity` — a plain entity for the `venue` table:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "venue" table. One object = one row.
@Entity
@Table(name = "venue")
public class VenueEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name
    private String city;    // column: city

    protected VenueEntity() {}   // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
}
```

`EventEntity`, with the foreign key mapped as an object:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug

    // Lesson 1: the foreign key column becomes an object reference.
    // Many event rows point at one venue row, so: @ManyToOne.
    @ManyToOne
    @JoinColumn(name = "venue_id")    // the column in THIS table that holds the key
    private VenueEntity venue;

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getSlug() { return slug; }
    public VenueEntity getVenue() { return venue; }
}
```

The repository:

```java
package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Spring Data writes the implementation at startup; we only declare the interface.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
}
```

The demo. Part 1 does it the old way, with two hand-written queries. Part 2 does it the new
way:

```java
package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 1: the old way (two hand-written queries) next to the new way (@ManyToOne).
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-event-venue
@Component
@Profile("l1-event-venue")
class L1EventVenueDemo implements CommandLineRunner {

    private final JdbcClient jdbc;
    private final EventRepository events;

    L1EventVenueDemo(JdbcClient jdbc, EventRepository events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        UUID monsoonId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        // ---- Part 1: the old way. The row gives us only venue_id, a bare UUID.
        //      To learn the venue's name we must write a second query ourselves.
        System.out.println(">>> OLD WAY -------------------------------------------");
        UUID venueId = jdbc.sql("select venue_id from event where id = ?")
                .param(monsoonId)
                .query(UUID.class)
                .single();
        String venueName = jdbc.sql("select name from venue where id = ?")
                .param(venueId)
                .query(String.class)
                .single();
        System.out.println(">>> venue_id from event row: " + venueId);
        System.out.println(">>> venue name after 2nd query: " + venueName);

        // ---- Part 2: the new way. The entity now holds a VenueEntity object.
        //      We just walk from the event to its venue. No query written by us.
        System.out.println(">>> NEW WAY -------------------------------------------");
        EventEntity event = events.findById(monsoonId).orElseThrow();
        System.out.println(">>> event: " + event.getName());
        System.out.println(">>> venue: " + event.getVenue().getName()
                + " (" + event.getVenue().getCity() + ")");
    }
}
```

Real output from a real run:

```text
>>> OLD WAY -------------------------------------------
>>> venue_id from event row: 11111111-1111-1111-1111-111111111111
>>> venue name after 2nd query: Riverside Arena
>>> NEW WAY -------------------------------------------
Hibernate: 
    select
        ee1_0.id,
        ee1_0.name,
        ee1_0.slug,
        ee1_0.starts_at,
        v1_0.id,
        v1_0.city,
        v1_0.name 
    from
        event ee1_0 
    left join
        venue v1_0 
            on v1_0.id=ee1_0.venue_id 
    where
        ee1_0.id=?
>>> event: Monsoon Rock Night
>>> venue: Riverside Arena (Hyderabad)
```

### 5. Walkthrough

**Part 1, the old way.** We ask for `venue_id` from the event row. We get a bare UUID. Then
we write a second query against `venue` to turn that UUID into a name. Two queries, both
written by us, plus stitching code. These two do not show up as `Hibernate:` lines in the
log, because `show-sql` only prints statements that Hibernate itself sends. `JdbcClient` is
not Hibernate.

**Part 2, the new way.** We call `events.findById(monsoonId)`. Read the SQL Hibernate
printed:

- It selects the event columns (`ee1_0.id`, `ee1_0.name`, ...) **and** the venue columns
  (`v1_0.id`, `v1_0.city`, `v1_0.name`) in one statement.
- `ee1_0` and `v1_0` are nicknames Hibernate invents for the two tables.
- `left join venue v1_0 on v1_0.id = ee1_0.venue_id` means: for the event row, also bring
  the venue row whose `id` equals the event's `venue_id`. The word `left` means: if an
  event had no venue, the event would still come back, with empty venue columns. Our
  `venue_id` column is `not null`, so that can never happen here. Hibernate writes `left`
  anyway, because the Java mapping alone does not promise the column is never empty.
- Hibernate uses the venue columns to build a `VenueEntity` object and plugs it into the
  event's `venue` field.

Then `event.getVenue().getName()` is a plain Java call. No query. The object is already
there.

**Where does the foreign key live?** In the `event` table, and only there. Real proof:

```text
docker exec pg psql -U postgres -d zenith -c "\d event"
...
Foreign-key constraints:
    "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)
```

The `venue` table has no column that points back at events. This becomes the main idea of
Lessons 3 and 4.

### 6. Where is it used?

Everywhere two tables are linked, which is almost everywhere. An order points at its
customer. A ticket points at its event. A comment points at its post. In this project:
event → venue, seat → seat map, and later order lines → orders.

### 7. Common mistakes

1. **Putting `@ManyToOne` on the wrong side.** The annotation goes on the class whose own
   table holds the foreign key column. Standing on that class, read it out loud: "many of
   me point at one of them." A venue does not hold `event_id`, so `VenueEntity` gets no
   `@ManyToOne` here.
2. **Keeping both fields.** If you keep `private UUID venueId;` *and* add the mapped
   `venue` field, Hibernate refuses to start: the column `venue_id` would be mapped twice.
   Replace the bare id field; do not add alongside it.
3. **Skipping `@JoinColumn` and trusting the guess.** Without it, Hibernate guesses the
   column name from the field name: field `venue` becomes column `venue_id`. Here the guess
   happens to be right. The day the column is named `location_id`, startup fails with
   `Schema-validation: missing column`. Rule for this project: always write
   `@JoinColumn(name = ...)`, so the mapping says what it means.

### 8. Link to the task

Subtask S1 asks for three mappings and the location of each foreign key. This lesson
finished one of the three: `EventEntity` `@ManyToOne` `VenueEntity`, foreign key `venue_id`
in the `event` table.

### 9. Check my understanding

**Q1.** A library database has these two tables:

```sql
create table author ( id uuid primary key, name text not null );

create table book   ( id uuid primary key,
                      title text not null,
                      author_id uuid not null references author (id) );
```

Many book rows point at one author row. You are writing the two entity classes,
`BookEntity` and `AuthorEntity`.
(a) Which of the two classes gets the relationship field?
(b) Write that field, with its two annotations.
(c) In one sentence: why that class and not the other one?

**Answer.**
(a) `BookEntity`.
(b)

```java
@ManyToOne
@JoinColumn(name = "author_id")
private AuthorEntity author;
```

(c) The `book` table holds the foreign key column `author_id`, and `@ManyToOne` always
goes on the class whose own table holds the key — many books point at one author.

**Q2.** For the same library database (tables `author(id, name)` and
`book(id, title, author_id)`), a teammate wrote this:

```java
@Entity
@Table(name = "author")
public class AuthorEntity {

    @Id
    private UUID id;
    private String name;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private List<BookEntity> books;
}
```

Two things are wrong with the relationship part. Name both.

**Answer.**

1. **Wrong side.** The `author` table has no `author_id` column; the key lives in the
   `book` table. So the `@ManyToOne` field belongs in `BookEntity`, not here. As written,
   startup would fail schema validation, because `@JoinColumn(name = "author_id")` names a
   column that does not exist in the `author` table.
2. **Wrong shape.** A `@ManyToOne` field holds **one** object — the single row the foreign
   key points at. It can never be a `List`. A list of the other side does exist, but it
   uses a different annotation (`@OneToMany`), which is the subject of Lesson 3.

### Do it yourself (second laptop)

**Step 0 — things that must exist.** Java 21 and Maven: `brew install openjdk@21 maven`.
The Postgres container from earlier stages:

```bash
docker start pg   # starts it if it exists but is stopped
docker ps         # must show: pg ... postgres:17-alpine ... 0.0.0.0:5432->5432/tcp
```

If the container does not exist at all, create it and the database:

```bash
docker run --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -d postgres:17-alpine
docker exec pg psql -U postgres -c "create database zenith;"
```

**Step 1 — make the project folder and copy the unchanged files** from the F6.10 scratch
app (run from the `foundations` folder):

```bash
mkdir -p F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app/src/main/java/com/zenith/rel
cd F6.11-relationships-lazy-loading-and-the-n-plus-1-problem

cp -r ../F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app/src/main/resources scratch-app/src/main/
cp ../F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/scratch-app/pom.xml scratch-app/pom.xml
cp ../F6.10-spring-data-jpa-repositories-transactions-and-the-persistence-context/seed-dev-data.sql .
```

Open `scratch-app/pom.xml` and change the `<artifactId>` line to `relationships-scratch`.
Do not touch the copied migration files — Flyway checks them byte for byte.

**Step 2 — create the five Java files** in `scratch-app/src/main/java/com/zenith/rel/`:
`DataApplication.java` (same as F6.10, package `com.zenith.rel`), `VenueEntity.java`,
`EventEntity.java`, `EventRepository.java`, and `L1EventVenueDemo.java`. All five are shown
in full in this lesson. `DataApplication.java` is:

```java
package com.zenith.rel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataApplication.class, args);
    }
}
```

**Step 3 — if your `zenith` database is fresh and empty**, run the app once so Flyway
creates the tables, then load the dev data:

```bash
cd scratch-app && mvn -q spring-boot:run   # Ctrl+C after "Started DataApplication"
docker exec -i pg psql -U postgres -d zenith < ../seed-dev-data.sql
```

**Step 4 — run the lesson demo:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-event-venue
```

Expected success output: the `OLD WAY` block prints the venue id and then
`Riverside Arena`; the `NEW WAY` block prints one Hibernate `select` with
`left join venue`, then `Monsoon Rock Night` and `Riverside Arena (Hyderabad)`.

Expected failures and what they mean:

| You see | It means | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` | Postgres is not running | `docker start pg` |
| Flyway `Validate failed ... checksum mismatch` | A copied migration file was edited | Re-copy it unchanged |
| `Schema-validation: missing column [...] in table [event]` | `@JoinColumn` names a column that does not exist | Use `venue_id` |
| `NoSuchElementException` from `orElseThrow` | No seed rows in the database | Do Step 3 |

**Step 5 — find the foreign key yourself:**

```bash
docker exec pg psql -U postgres -d zenith -c "\d event"
```

**Done when:** both demo parts print, and you can point at the line
`"event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)` and say which table holds
the key.

### Summary

- `@ManyToOne` maps a foreign key column as an object field. Many rows of my table point at
  one row of the other table.
- The annotation lives on the "many" side — the class whose own table holds the key column.
- `@JoinColumn(name = ...)` names that key column. Always write it out.
- After mapping, walking to related data is a plain Java call:
  `event.getVenue().getName()`. Hibernate did the query work.
- Today the venue loads together with the event, in one joined statement. That default is
  called `EAGER`, and Lesson 7 shows why it must be changed.

---

## Lesson 2 — Two new tables by migration, and links that chain (subtask S1)

### 1. What is it?

This lesson adds the seating tables and applies `@ManyToOne` twice more. The one new idea:
**links chain**. A seat points at its seat map. The seat map points at its venue. Each link
is its own `@ManyToOne`, always on the side that holds the foreign key. Once mapped, you
walk the whole chain in plain Java: seat → seat map → venue.

One reminder, so this stands alone: a **migration** is a numbered SQL file (`V5__...sql`)
that Flyway runs exactly once, in order, at app start. The schema changes only through
these files. A file that has already run must never be edited.

### 2. Why do we need it?

The ticketing domain is a chain. A venue has seat maps (floor plan, balcony plan). A seat
map has seats. Every seat belongs to exactly one seat map.

Without the mappings, the old pain doubles: to show "Seat A1, Riverside Floor, Riverside
Arena" you would write a query for the seat, a second for the seat map, a third for the
venue, and stitch three UUIDs by hand. Every time.

### 3. How does it work?

Step 1: a new migration file creates the tables. The foreign key always sits in the table
of the "many" side:

```sql
-- V5__create_seat_map_and_seat.sql
-- Tables for the seating domain: a venue has seat maps, a seat map has seats.
-- The foreign key always lives on the "many" side:
--   seat_map points at venue  (column seat_map.venue_id),
--   seat     points at seat_map (column seat.seat_map_id).

create table seat_map (
    id        uuid primary key,
    venue_id  uuid not null,
    name      text not null,
    constraint seat_map_venue_fk foreign key (venue_id) references venue (id),
    constraint seat_map_name_unique_per_venue unique (venue_id, name)
);

create index idx_seat_map_venue_id on seat_map (venue_id);

create table seat (
    id           uuid primary key,
    seat_map_id  uuid not null,
    row_label    text not null,
    seat_number  integer not null,
    constraint seat_seat_map_fk foreign key (seat_map_id) references seat_map (id),
    constraint seat_unique_within_map unique (seat_map_id, row_label, seat_number),
    constraint seat_number_positive check (seat_number > 0)
);

create index idx_seat_seat_map_id on seat (seat_map_id);
```

Step 2: one entity per table, each with a `@ManyToOne` for its own foreign key column.

Step 3: walk the chain with getters: `seat.getSeatMap().getVenue().getName()`.

### 4. Simple example

`SeatMapEntity` — many seat maps belong to one venue:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "seat_map" table. One object = one row.
@Entity
@Table(name = "seat_map")
public class SeatMapEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name

    // Many seat maps belong to one venue, so: @ManyToOne.
    @ManyToOne
    @JoinColumn(name = "venue_id")   // the column in THIS table that holds the key
    private VenueEntity venue;

    protected SeatMapEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public VenueEntity getVenue() { return venue; }
}
```

`SeatEntity` — many seats belong to one seat map:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "seat" table. One object = one row.
@Entity
@Table(name = "seat")
public class SeatEntity {

    @Id
    private UUID id;            // column: id (the primary key)

    private String rowLabel;    // column: row_label
    private int seatNumber;     // column: seat_number

    // Many seats belong to one seat map, so: @ManyToOne.
    @ManyToOne
    @JoinColumn(name = "seat_map_id")   // the column in THIS table that holds the key
    private SeatMapEntity seatMap;

    protected SeatEntity() {}   // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatMapEntity getSeatMap() { return seatMap; }
}
```

The repository:

```java
package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatRepository extends JpaRepository<SeatEntity, UUID> {
}
```

The demo:

```java
package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 2: load ONE seat, then walk the chain: seat -> seat map -> venue.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-seat-chain
@Component
@Profile("l2-seat-chain")
class L2SeatChainDemo implements CommandLineRunner {

    private final SeatRepository seats;

    L2SeatChainDemo(SeatRepository seats) {
        this.seats = seats;
    }

    @Override
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        SeatEntity seat = seats.findById(seatA1).orElseThrow();

        System.out.println(">>> seat:     row " + seat.getRowLabel()
                + ", number " + seat.getSeatNumber());
        System.out.println(">>> seat map: " + seat.getSeatMap().getName());
        System.out.println(">>> venue:    " + seat.getSeatMap().getVenue().getName()
                + " (" + seat.getSeatMap().getVenue().getCity() + ")");
    }
}
```

Dev rows (also appended to `seed-dev-data.sql`): one seat map `Riverside Floor`
(`eeeeeeee-...0001`) at Riverside Arena, four seats A1, A2, B1, B2
(`ffffffff-...0001` to `...0004`).

First run applied the migration:

```text
Migrating schema "public" to version "5 - create seat map and seat"
Successfully applied 1 migration to schema "public", now at version v5
```

Then the demo. Real output:

```text
Hibernate: 
    select
        se1_0.id,
        se1_0.row_label,
        sm1_0.id,
        sm1_0.name,
        v1_0.id,
        v1_0.city,
        v1_0.name,
        se1_0.seat_number 
    from
        seat se1_0 
    left join
        seat_map sm1_0 
            on sm1_0.id=se1_0.seat_map_id 
    left join
        venue v1_0 
            on v1_0.id=sm1_0.venue_id 
    where
        se1_0.id=?
>>> seat:     row A, number 1
>>> seat map: Riverside Floor
>>> venue:    Riverside Arena (Hyderabad)
```

### 5. Walkthrough

- We called `seats.findById(seatA1)` — we asked for **one seat**, nothing else.
- Hibernate sent **one statement with two `left join`s**: find the `seat` row by id; join
  `seat_map` through `seat.seat_map_id`; join `venue` through `seat_map.venue_id`. Three
  tables answered one `findById`.
- `se1_0`, `sm1_0`, `v1_0` are nicknames Hibernate invents for the three tables.
- With all the columns in hand, Hibernate builds three objects — seat, seat map, venue —
  and plugs them into each other. The three `println` calls then run without any query.
- Nobody asked for the venue, yet Hibernate fetched it. That is the default for
  `@ManyToOne`, called `EAGER`: linked rows load together, immediately. Loading one seat
  quietly loaded a third of the schema. Lesson 7 shows why this default is a trap.

Where the foreign keys live — real proof from the database:

```text
\d seat      ->  "seat_seat_map_fk"  FOREIGN KEY (seat_map_id) REFERENCES seat_map(id)
\d seat_map  ->  "seat_map_venue_fk" FOREIGN KEY (venue_id)    REFERENCES venue(id)
```

The `venue` table points at nobody. The `seat_map` table does not store seat ids. Each
child points up at its parent; parents hold nothing.

### 6. Where is it used?

Chains like this are the shape of almost every business database. Order line → order →
customer. Comment → post → author. City → state → country. In this project the chain
seat → seat map → venue is the one the checkout flow will walk constantly.

### 7. Common mistakes

1. **Expecting the parent table to hold a list.** There is no "list of seat ids" column in
   `seat_map`, and SQL has no such column type. The child row points up at the parent. The
   Java-side list is Lesson 3, and it adds no column.
2. **Editing an old migration to add tables.** `V1__baseline.sql` already ran, so Flyway
   stored its checksum; any edit fails startup with `Validate failed ... checksum
   mismatch`. New tables always mean a new file.
3. **Chaining in the wrong direction.** `@ManyToOne` on `SeatMapEntity` pointing at a seat
   would claim the `seat_map` table holds a seat's id. It does not. Before writing the
   annotation, say: "many of me point at one of them" — if the sentence sounds wrong, the
   mapping is wrong.

### 8. Link to the task

Subtask S1 lists three mappings. After this lesson the `@ManyToOne` side of all of them
exists: `EventEntity → VenueEntity` (Lesson 1), `SeatEntity → SeatMapEntity` and
`SeatMapEntity → VenueEntity` (this lesson). The foreign-key question is answered for every
pair: `event.venue_id`, `seat.seat_map_id`, `seat_map.venue_id`. What remains of S1 is the
other direction — the seats list on the seat map — which is Lesson 3.

### 9. Check my understanding

**Q1.** A geography database has these three tables:

```sql
create table country ( id uuid primary key, name text not null );

create table state   ( id uuid primary key,
                       country_id uuid not null references country (id),
                       name text not null );

create table city    ( id uuid primary key,
                       state_id uuid not null references state (id),
                       name text not null );
```

You write three entities: `CountryEntity`, `StateEntity`, `CityEntity`.
(a) Which tables hold foreign keys, and which hold none?
(b) Write every `@ManyToOne` field that belongs in these three classes (with
`@JoinColumn`).
(c) You hold a `CityEntity` object named `city`. Write the one Java expression that gives
the name of its country.

**Answer.**
(a) `state` holds `country_id`; `city` holds `state_id`; `country` holds no foreign key.
(b) Two fields exist in total. In `StateEntity`:

```java
@ManyToOne
@JoinColumn(name = "country_id")
private CountryEntity country;
```

In `CityEntity`:

```java
@ManyToOne
@JoinColumn(name = "state_id")
private StateEntity state;
```

`CountryEntity` gets none — its table holds no foreign key.
(c) `city.getState().getCountry().getName()`

**Q2.** Same three entities as in Q1, mapped exactly as written, nothing else changed. You
run:

```java
CityEntity city = cities.findById(someId).orElseThrow();
```

Predict the SQL log: how many statements does Hibernate send, and which tables appear in
them? Explain why in one sentence.

**Answer.** One statement, and all three tables appear in it: `city`, with a `left join` to
`state` and a further `left join` to `country`. The reason: `@ManyToOne` defaults to
`EAGER`, so each linked row loads together with its owner, and the links chain from city
through state to country.

### Do it yourself (second laptop)

Continues inside `F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`.

**Step 1 — create the migration file**
`src/main/resources/db/migration/V5__create_seat_map_and_seat.sql` with the exact content
shown in this lesson. Do not touch V1–V4.

**Step 2 — create four Java files** in `src/main/java/com/zenith/rel/`:
`SeatMapEntity.java`, `SeatEntity.java`, `SeatRepository.java`, `L2SeatChainDemo.java` —
all shown in full above.

**Step 3 — run once so Flyway applies V5**, then stop with Ctrl+C:

```bash
cd scratch-app
mvn -q spring-boot:run
```

Expected in the log: `Migrating schema "public" to version "5 - create seat map and seat"`
then `Successfully applied 1 migration`.

**Step 4 — insert the dev rows** (parent first, children second — the order matters):

```bash
docker exec pg psql -U postgres -d zenith -c "
INSERT INTO public.seat_map (id, venue_id, name) VALUES
 ('eeeeeeee-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Riverside Floor');
INSERT INTO public.seat (id, seat_map_id, row_label, seat_number) VALUES
 ('ffffffff-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', 'A', 1),
 ('ffffffff-0000-0000-0000-000000000002', 'eeeeeeee-0000-0000-0000-000000000001', 'A', 2),
 ('ffffffff-0000-0000-0000-000000000003', 'eeeeeeee-0000-0000-0000-000000000001', 'B', 1),
 ('ffffffff-0000-0000-0000-000000000004', 'eeeeeeee-0000-0000-0000-000000000001', 'B', 2);"
```

**Step 5 — run the demo:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-seat-chain
```

Expected success: one `Hibernate: select` with two `left join` lines (`seat_map` and
`venue`), then the three `>>>` lines ending in `Riverside Arena (Hyderabad)`.

Expected failures and what they mean:

| You see | It means | Fix |
|---|---|---|
| `violates foreign key constraint "seat_seat_map_fk"` | Seats inserted before their seat map | Insert `seat_map` first |
| `Validate failed ... checksum mismatch` | An already-run migration was edited | Restore it; put new SQL in V5 only |
| `Schema-validation: missing table [seat]` | V5 never ran | Do Step 3 and watch for the `Migrating` line |
| `duplicate key value violates unique constraint "seat_unique_within_map"` | Same row+number twice in one map | The constraint works; change the seat number |

**Step 6 — find both foreign keys yourself:**

```bash
docker exec pg psql -U postgres -d zenith -c "\d seat"
docker exec pg psql -U postgres -d zenith -c "\d seat_map"
```

**Done when:** the chain prints, and you can say which table holds the key for all three
mappings: `event.venue_id`, `seat.seat_map_id`, `seat_map.venue_id`.

### Summary

- Links chain. Each table-to-table link is one `@ManyToOne`, on the side holding the key.
- New tables arrive only through a new migration file. Applied migrations are frozen.
- One `findById` on a seat produced one statement joining three tables, because
  `@ManyToOne` defaults to `EAGER`. Remember the feeling; Lesson 7 turns this off.
- Parents hold no list column. Children point up. The database knows only foreign keys.

---

## Lesson 3 — `@OneToMany(mappedBy = ...)`: the list on the other side (subtask S1)

### 1. What is it?

`@OneToMany` is a JPA annotation for the mirror direction of a link. One seat map has many
seats, so `SeatMapEntity` gets a field `List<SeatEntity> seats`. The setting
`mappedBy = "seatMap"` names the **field on the other entity** that holds the foreign key.
The list adds no column anywhere. Hibernate fills it by reading the foreign keys that
already exist in the `seat` table.

### 2. Why do we need it?

Real questions flow in both directions. "Which map does this seat belong to?" is one
direction. The seat picker screen asks the opposite: "give me **all seats** of this map."
With the list, the map object simply *has* its seats: `map.getSeats()`.

The deeper reason: the database stores this link in exactly one place — the `seat_map_id`
column in the `seat` table. Java now shows the same link in two places: the seat's
`seatMap` field and the map's `seats` list. Two Java views, one column. Somebody must be
the real one. `mappedBy` tells Hibernate: "the real one is over there, on the seat's
field; this list is only a mirror." The side that holds the foreign key column is called
the **owning side**.

### 3. How does it work?

1. Add the field to the "one" side: `private List<SeatEntity> seats = new ArrayList<>();`.
2. Put `@OneToMany(mappedBy = "seatMap")` on it. The string `"seatMap"` is the exact
   **Java field name** in `SeatEntity` — not a column name, not a table name.
3. When you first *use* the list, Hibernate runs
   `select ... from seat where seat_map_id = ?` and fills it. Not before. Loading on first
   touch is called `LAZY`, the default for `@OneToMany` lists — the opposite of the
   `@ManyToOne` default (`EAGER`, load immediately).

### 4. Simple example

The updated `SeatMapEntity` — only the list is new:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The Java class that stands for the "seat_map" table. One object = one row.
@Entity
@Table(name = "seat_map")
public class SeatMapEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name

    // Many seat maps belong to one venue, so: @ManyToOne.
    @ManyToOne
    @JoinColumn(name = "venue_id")   // the column in THIS table that holds the key
    private VenueEntity venue;

    // Lesson 3: the mirror direction. One seat map has many seats.
    // mappedBy = "seatMap" names the FIELD on SeatEntity that owns the foreign key.
    // No new column anywhere: Hibernate fills this list by reading seat.seat_map_id.
    @OneToMany(mappedBy = "seatMap")
    private List<SeatEntity> seats = new ArrayList<>();

    protected SeatMapEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public VenueEntity getVenue() { return venue; }
    public List<SeatEntity> getSeats() { return seats; }
}
```

The seat map repository:

```java
package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatMapRepository extends JpaRepository<SeatMapEntity, UUID> {
}
```

The demo:

```java
package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 3: load ONE seat map, then read its seats from the mapped list.
// @Transactional keeps one transaction open for the whole method, so Hibernate
// may still load the list when we first touch it.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-seats-list
@Component
@Profile("l3-seats-list")
class L3SeatsListDemo implements CommandLineRunner {

    private final SeatMapRepository seatMaps;

    L3SeatsListDemo(SeatMapRepository seatMaps) {
        this.seatMaps = seatMaps;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID riversideFloor = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
        SeatMapEntity map = seatMaps.findById(riversideFloor).orElseThrow();

        System.out.println(">>> seat map: " + map.getName());
        System.out.println(">>> now touching the seats list for the first time ...");
        for (SeatEntity seat : map.getSeats()) {
            System.out.println(">>>   seat " + seat.getRowLabel() + seat.getSeatNumber());
        }
        System.out.println(">>> seats in this map: " + map.getSeats().size());
    }
}
```

Real output:

```text
Hibernate: 
    select
        sme1_0.id,
        sme1_0.name,
        v1_0.id,
        v1_0.city,
        v1_0.name 
    from
        seat_map sme1_0 
    left join
        venue v1_0 
            on v1_0.id=sme1_0.venue_id 
    where
        sme1_0.id=?
>>> seat map: Riverside Floor
>>> now touching the seats list for the first time ...
Hibernate: 
    select
        s1_0.seat_map_id,
        s1_0.id,
        s1_0.row_label,
        s1_0.seat_number 
    from
        seat s1_0 
    where
        s1_0.seat_map_id=?
>>>   seat A1
>>>   seat A2
>>>   seat B1
>>>   seat B2
>>> seats in this map: 4
```

### 5. Walkthrough

1. **Statement 1** comes from `findById`. It selects the seat map and joins `venue` (the
   `@ManyToOne` default still loads immediately). **No seat columns anywhere.** The list
   was not loaded.
2. `>>> seat map: Riverside Floor` prints. Still no seats.
3. `>>> now touching the seats list ...` prints. **Then** statement 2 appears. The `for`
   loop was the first real use of the list; that first use pulled the trigger.
4. **Statement 2** is `select ... from seat where seat_map_id = ?` — exactly the query you
   would have written by hand. Hibernate wrote it from `mappedBy`.
5. The later `map.getSeats().size()` causes no third statement. The list is already
   filled.

The schema is untouched: no migration ran, validation passed, `\d seat_map` shows the same
columns and foreign keys as before. The list lives only in Java.

### 6. Where is it used?

Wherever a parent naturally carries its children: an order and its order lines, a post and
its comments, a seat map and its seats. The seat picker will render straight from
`map.getSeats()`.

### 7. Common mistakes

**Mistake 1 — forgetting `mappedBy`.** With plain `@OneToMany`, startup fails:

```text
Schema-validation: missing table [seat_map_seats]
```

Without `mappedBy`, Hibernate cannot know the seat's field already owns this link. So it
assumes the link lives in a separate table of map-and-seat pairs, invents the name
`seat_map_seats`, looks for it, and fails.

**Mistake 2 — writing the column name in `mappedBy`.** With
`@OneToMany(mappedBy = "seat_map_id")`, startup fails:

```text
Collection 'com.zenith.rel.SeatMapEntity.seats' is 'mappedBy' a property named
'seat_map_id' which does not exist in the target entity 'com.zenith.rel.SeatEntity'
```

`mappedBy` must name a **Java field** of `SeatEntity`. The field is called `seatMap`.
Column names belong in `@JoinColumn`, on the other side.

**Mistake 3 — trusting the list when writing.** Adding a seat to `map.getSeats()` and
saving writes nothing to the database. That mistake gets the whole next lesson (Lesson 4).

### 8. Link to the task

Subtask S1 is now fully mapped: `SeatEntity` `@ManyToOne` to `SeatMapEntity`,
`SeatMapEntity` `@OneToMany(mappedBy = "seatMap")` back to seats, and `EventEntity`
`@ManyToOne` to `VenueEntity`. Foreign keys located: `seat.seat_map_id`,
`seat_map.venue_id`, `event.venue_id` — the list side added no new one.

### 9. Check my understanding

**Q1.** A library database has tables `author(id, name)` and `book(id, title, author_id)`.
The book entity is already written:

```java
@Entity
@Table(name = "book")
public class BookEntity {

    @Id
    private UUID id;

    private String title;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private AuthorEntity author;

    protected BookEntity() {}

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public AuthorEntity getAuthor() { return author; }
}
```

Write the books list field that belongs in `AuthorEntity`, with its annotation. What exact
string goes inside `mappedBy`, and why that string and not `"author_id"`?

**Answer.**

```java
@OneToMany(mappedBy = "author")
private List<BookEntity> books = new ArrayList<>();
```

The string is `"author"` because `mappedBy` names the **Java field** on the owning entity
(`BookEntity`) that holds the foreign key — and that field is called `author`.
`"author_id"` is a column name; using it fails at startup with
`... is 'mappedBy' a property named 'author_id' which does not exist in the target
entity ...`.

**Q2.** Using the same two entities as in Q1 (the `BookEntity` shown above, and an
`AuthorEntity` that has `@OneToMany(mappedBy = "author") private List<BookEntity> books`),
this code runs inside one transaction:

```java
AuthorEntity author = authors.findById(someId).orElseThrow();   // line 1
System.out.println(author.getName());                           // line 2
System.out.println(author.getBooks().size());                   // line 3
```

How many SQL statements does Hibernate send in total, which line causes each one, and
roughly what does each statement look like?

**Answer.** Two statements.
Line 1 sends `select ... from author where id = ?` — `AuthorEntity` has no `@ManyToOne`,
so there is nothing to join.
Line 2 sends nothing: `name` arrived with statement 1.
Line 3 sends `select ... from book where author_id = ?` — the first real use of the lazy
list triggers its load.

### Do it yourself (second laptop)

Continues inside `F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`.

**Step 1 — replace `SeatMapEntity.java`** with the version shown in this lesson (new
imports: `jakarta.persistence.OneToMany`, `java.util.ArrayList`, `java.util.List`).

**Step 2 — create two files** in `src/main/java/com/zenith/rel/`:
`SeatMapRepository.java` and `L3SeatsListDemo.java` (both shown above).

**Step 3 — run the demo:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-seats-list
```

Expected: first select on `seat_map` with a `venue` join and no seat columns; then the
line `now touching the seats list for the first time ...`; only after it, the second
select on `seat`; then seats A1, A2, B1, B2 and the count 4.

**Step 4 — break it twice on purpose, read both messages, then restore:**

1. Change the annotation to plain `@OneToMany` (delete `mappedBy`). Run. Expect:
   `Schema-validation: missing table [seat_map_seats]`.
2. Change it to `@OneToMany(mappedBy = "seat_map_id")`. Run. Expect:
   `... is 'mappedBy' a property named 'seat_map_id' which does not exist ...`.
3. Restore `@OneToMany(mappedBy = "seatMap")` and rerun to confirm it works again.

| You see | It means | Fix |
|---|---|---|
| `NoSuchElementException` from `orElseThrow` | Seat map seed row missing | Re-run the Lesson 2 insert |
| `cannot find symbol: class List` | Missing imports after the edit | Add `java.util.List`, `java.util.ArrayList` |

**Step 5 — prove the schema did not change:**

```bash
docker exec pg psql -U postgres -d zenith -c "\d seat_map"
```

**Done when:** you can point at the log and say "the seats query ran *here*, because the
list was touched *here*", and you have seen and fixed both error messages.

### Summary

- `@OneToMany(mappedBy = "fieldName")` puts a read view of the children on the parent.
  No new column; Hibernate computes the list from the children's foreign keys.
- `mappedBy` names the **Java field** on the owning side, never a column.
- Lists load on first touch (`LAZY` default); `@ManyToOne` loads immediately (`EAGER`
  default). Watch the log order to see it.
- The side with the foreign key column is the **owning side**. The list is only a mirror —
  Lesson 4 proves it.

---

## Lesson 4 — The owning side: only the foreign-key side writes (subtask S2)

### 1. What is it?

In a two-way mapping, the two sides are not equal. The **owning side** is the entity whose
own table holds the foreign key column — here, the seat with its `seat_map_id`. When
Hibernate saves, it records **only what the owning side says**. The mirror list on the
other side (`@OneToMany(mappedBy = ...)`) is never written to the database. It is filled
on load, ignored on save.

### 2. Why do we need it?

Because the failure is **silent**. If you add a seat to `map.getSeats()` and commit, you
get no error, no SQL, no warning — and no row. A bug that throws is annoying; a bug that
quietly loses data is dangerous. This is also checkpoint question 1 of the task.

### 3. How does it work?

At commit, Hibernate writes:

1. A new entity handed over with `save(...)` becomes an `insert`. The value for the
   foreign key column comes from **that entity's own `@ManyToOne` field** (`seat.seatMap`).
   If that field is `null`, nothing else can fill it.
2. Changed columns of loaded entities become `update`s (dirty checking).
3. The mirror list is skipped entirely. `mappedBy` told Hibernate: "this link is owned
   elsewhere."

A fresh load later rebuilds the list from the foreign keys. In memory, inside a running
method, nobody syncs the two sides for you. That is your job, and the standard tool is a
**helper method** that sets both sides in one call.

### 4. Simple example

`SeatEntity` gets a constructor for new seats and a package-private setter:

```java
    protected SeatEntity() {}   // Hibernate builds the object empty, then fills the fields

    // Lesson 4: a constructor for NEW seats. The seat map is set separately,
    // by the helper on SeatMapEntity, so both sides stay in step.
    public SeatEntity(UUID id, String rowLabel, int seatNumber) {
        this.id = id;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
    }

    // Package-private on purpose: only the helper on SeatMapEntity may call it.
    void setSeatMap(SeatMapEntity seatMap) { this.seatMap = seatMap; }
```

`SeatMapEntity` gets the helper — the method subtask S2 asks for:

```java
    // Lesson 4: the helper that keeps both sides in step.
    // The owning side (seat.seatMap) is what the database records;
    // the mirror list gets the same truth for in-memory readers.
    public void addSeat(SeatEntity seat) {
        seats.add(seat);
        seat.setSeatMap(this);
    }
```

`SeatRepository` gets a derived query for cleanup between runs:

```java
    // Lesson 4: derived query, used to remove the demo seats (row C) between runs.
    List<SeatEntity> findByRowLabel(String rowLabel);
```

The experiments live in `L4OwnershipService`, one transaction per method; the runner
`L4OwnershipDemo` calls them from outside, so every call goes through the transaction
proxy (no self-invocation trap):

```java
    // Experiment 1 - BREAK IT: change only the mirror list. The owning side
    // (seat.seatMap) stays null. The transaction commits when this method returns.
    @Transactional
    public void addToListOnly() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c1 = new SeatEntity(UUID.randomUUID(), "C", 1);
        map.getSeats().add(c1);                 // mirror side only!
        System.out.println(">>> [list-only]   in-memory list size now: "
                + map.getSeats().size());
    }

    // Experiment 2 - set ONLY the owning side. The list is never touched.
    @Transactional
    public void setOwningSideOnly() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c2 = new SeatEntity(UUID.randomUUID(), "C", 2);
        c2.setSeatMap(map);                     // owning side: the foreign key
        seats.save(c2);                         // let Hibernate manage the new object
        System.out.println(">>> [owning-only] saved seat C2; the list was never touched");
    }

    // Experiment 3 - THE FIX: the helper sets both sides in one call.
    @Transactional
    public void addBothSides() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c3 = new SeatEntity(UUID.randomUUID(), "C", 3);
        map.addSeat(c3);                        // helper: list AND owning side
        seats.save(c3);
        System.out.println(">>> [helper]      in-memory list already correct: "
                + map.getSeats().size() + " seats");
    }

    // Fresh look: a new transaction, a fresh load, and the real row count.
    @Transactional(readOnly = true)
    public void report(String label) {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        System.out.println(">>> [" + label + "] rows in seat table: " + seats.count()
                + " | fresh list size: " + map.getSeats().size());
    }
```

The runner:

```java
    @Override
    public void run(String... args) {
        experiments.cleanUpRowC();              // make reruns start from the same state
        experiments.report("start           ");

        experiments.addToListOnly();            // commit happens as the method returns
        experiments.report("after list-only ");

        experiments.setOwningSideOnly();
        experiments.report("after owning    ");

        experiments.addBothSides();
        experiments.report("after helper    ");
    }
```

Real output, trimmed to the heart (report selects omitted):

```text
>>> [start           ] rows in seat table: 4 | fresh list size: 4

>>> [list-only]   in-memory list size now: 5
        (no insert statement appears anywhere here)
>>> [after list-only ] rows in seat table: 4 | fresh list size: 4

>>> [owning-only] saved seat C2; the list was never touched
Hibernate: 
    insert into seat (row_label, seat_map_id, seat_number, id) values (?, ?, ?, ?)
>>> [after owning    ] rows in seat table: 5 | fresh list size: 5

>>> [helper]      in-memory list already correct: 6 seats
Hibernate: 
    insert into seat (row_label, seat_map_id, seat_number, id) values (?, ?, ?, ?)
>>> [after helper    ] rows in seat table: 6 | fresh list size: 6
```

### 5. Walkthrough

**Experiment 1 — the silent loss.** Seat C1 went into the map's list and nowhere else. The
in-memory list says 5. The transaction commits — and the log contains **no insert**. The
next report, in a fresh transaction, says 4 rows, fresh list 4. C1 never existed for the
database, because the owning side (`seat.seatMap`) was never set. No error at any point.

**Experiment 2 — the owning side alone is enough.** We set `c2.setSeatMap(map)`, handed C2
over with `save(c2)`, never touched the list. At commit: `insert`, with `seat_map_id`
taken from the `seatMap` field. Next report: 5 rows — and the **fresh list has 5 too**,
including C2, though no code ever added C2 to any list. The list is computed from foreign
keys on load.

Two side notes from the full log. The `insert` prints *after* our `>>>` line: Hibernate
queues writes and sends them at commit. And `save(c2)` causes an extra `select` for that
id first: we chose the id ourselves, so `save` checks whether the row already exists.

**Experiment 3 — the fix.** `map.addSeat(c3)` sets both sides in one call. The database
gets its insert, and anyone holding `map` in memory sees a correct list immediately.

### 6. Where is it used?

Every serious codebase with two-way mappings has these helpers: `order.addLine(line)`,
`post.addComment(comment)`, `seatMap.addSeat(seat)`. Reviewers reject direct
`getSeats().add(...)` calls. The helper is also the natural home for rules like "a
cancelled order accepts no new lines".

### 7. Common mistakes

1. **Writing through the mirror.** `map.getSeats().add(seat)` alone saves nothing, ever.
   The database records the foreign key; the foreign key lives on the seat; so the seat's
   field must be set.
2. **Trusting an already-loaded list after changing the owning side.** A list loaded
   earlier in the transaction does not refresh itself. Measured in this app: a lazy list
   touched for the first time *after* a `save` in the same transaction still showed the
   old count (6, not 7) — the collection load reads the database, and the pending insert
   was not yet flushed. Fresh truth needs a fresh transaction and load.
3. **Setting both sides but forgetting `save(...)` for the brand-new object.** A new
   object is unknown to Hibernate until handed over. (JPA's cascade feature can automate
   this; this project does not use it yet.)

### 8. Link to the task

Subtask S2 is done: list-only change proven to write nothing; `addSeat` helper written.
Checkpoint question 1 answered: **the `@ManyToOne` side owns the foreign key, and it
matters because saving records only the owning side — mirror-only changes are silently
ignored.**

### 9. Check my understanding

**Q1.** A shop database has tables `customer_order(id)` and
`order_line(id, order_id, product text)`. The entities (relationship parts, same package):

```java
@Entity
@Table(name = "customer_order")
public class OrderEntity {
    @Id private UUID id;

    @OneToMany(mappedBy = "order")
    private List<OrderLineEntity> lines = new ArrayList<>();

    public List<OrderLineEntity> getLines() { return lines; }
}

@Entity
@Table(name = "order_line")
public class OrderLineEntity {
    @Id private UUID id;
    private String product;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    public OrderLineEntity(UUID id, String product) { this.id = id; this.product = product; }
    protected OrderLineEntity() {}
    void setOrder(OrderEntity order) { this.order = order; }
}
```

Inside one transactional method:

```java
OrderEntity order = orders.findById(orderId).orElseThrow();
OrderLineEntity line = new OrderLineEntity(UUID.randomUUID(), "T-shirt");
order.getLines().add(line);
// method ends, transaction commits
```

After the commit: is there a new row in `order_line`? Explain why, and write the corrected
version (you may add a helper to `OrderEntity`).

**Answer.** No new row. Only the mirror list changed; the owning side (`line.order`, which
feeds the `order_id` column) was never set, and the new object was never given to
Hibernate with `save`. Mirror-side changes are ignored at save time, silently. Corrected:

```java
// helper inside OrderEntity:
public void addLine(OrderLineEntity line) {
    lines.add(line);
    line.setOrder(this);
}

// in the service method:
OrderEntity order = orders.findById(orderId).orElseThrow();
OrderLineEntity line = new OrderLineEntity(UUID.randomUUID(), "T-shirt");
order.addLine(line);       // sets list AND owning side
orderLines.save(line);     // hand the new object to Hibernate
```

**Q2.** Same two entities as in Q1. A different method, one transaction:

```java
OrderEntity order = orders.findById(orderId).orElseThrow();
System.out.println(order.getLines().size());        // prints 3

OrderLineEntity line = new OrderLineEntity(UUID.randomUUID(), "Mug");
line.setOrder(order);
orderLines.save(line);
// method ends, transaction commits
```

(a) After the commit, how many rows for this order are in `order_line`?
(b) A colleague says: "the `println` would print 4 if we moved it to the end of the
method, because the list updates itself once the owning side is set." Is the colleague
right? Explain.

**Answer.**
(a) Four. The owning side was set and the object was saved, so commit writes the insert.
(b) No, and it prints 3 either way. A list that was already loaded stays exactly as
loaded — collections do not watch the owning side. And even if the list had never been
touched before and its first touch came at the end of the method, the load reads the
database, where the pending insert has not been flushed yet — still 3. (Measured in this
project's app: a lazy seats list first touched after a `save` in the same transaction
showed the old count.) The mirror list reflects the database as of its load, never the
in-flight changes of the session.

### Do it yourself (second laptop)

Continues inside `F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`.

**Step 1 — edit three existing files:** `SeatEntity.java` (public constructor +
package-private `setSeatMap`), `SeatMapEntity.java` (`addSeat` helper),
`SeatRepository.java` (`List<SeatEntity> findByRowLabel(String rowLabel);`, import
`java.util.List`). All shown above.

**Step 2 — create** `L4OwnershipService.java` and `L4OwnershipDemo.java` (shown above;
the service also has `cleanUpRowC` and `report`).

**Step 3 — run it:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l4-ownership
```

Expected report lines: `4 | 4`, then `4 | 4` (and **no insert** in between!), then
`5 | 5`, then `6 | 6`. Rerunning is safe: row "C" is deleted first, so every run starts at
4 and ends at 6.

| You see | It means | Fix |
|---|---|---|
| `duplicate key ... seat_unique_within_map` | Cleanup did not run | Keep `cleanUpRowC()` as the first call |
| `NoSuchElementException` from `orElseThrow` | Seat map seed row missing | Re-run the Lesson 2 insert |
| `setSeatMap(...) has private access` / `cannot find symbol` | Setter missing or wrong visibility | Package-private `void setSeatMap(...)`, same package |
| `null value in column "seat_map_id" violates not-null constraint` | Saved a seat whose `seatMap` was never set | Set the owning side before `save` |

**Done when:** you can explain why the count stayed at 4 after experiment 1 although the
in-memory list said 5 — and why experiment 2 reached the database although no list was
touched.

### Summary

- The owning side is the side with the foreign key column — always the `@ManyToOne` side.
- Saving records only the owning side. Mirror lists are ignored on save, rebuilt on load.
- Mirror-only changes are lost **silently**. No error, no SQL, no row.
- Keep both sides in step with a helper (`addSeat`) that sets the list and the owning
  field in one call; `save` the new child explicitly.

---

## Lesson 5 — `@ManyToMany`: the pairs live in a join table (subtask S3)

### 1. What is it?

`@ManyToMany` is the JPA annotation for links where **both sides have many of the other**.
A user can hold many roles, and a role belongs to many users. No single foreign key column
can store that, so the database keeps a separate table of pairs — here
`user_role(user_id, role_id)`. The annotation `@JoinTable` tells Hibernate which table
holds the pairs and which column belongs to which side.

### 2. Why do we need it?

A column holds exactly one value. Meera has two roles: you cannot put two role ids into
one column of her user row, and you cannot put all attendee ids into one column of the
role row. The only clean shape is one row per pair: "Meera–attendee", "Meera–organiser",
"Arjun–attendee". That is the `user_role` table from the modelling task. This lesson maps
that shape into objects, so code can ask `user.getRoles()` instead of hand-writing the
two-table join every time.

### 3. How does it work?

On the user entity, declare a set of roles and describe the join table:

- `@ManyToMany` — both sides are "many".
- `@JoinTable(name = "user_role", ...)` — the table of pairs.
- `joinColumns = @JoinColumn(name = "user_id")` — the column pointing at **me** (the user).
- `inverseJoinColumns = @JoinColumn(name = "role_id")` — the column pointing at the
  **other side** (the role).

Three details:

1. **The side that declares `@JoinTable` is the owning side.** What Hibernate writes into
   `user_role` is whatever this `roles` set says. The optional mirror
   (`@ManyToMany(mappedBy = "roles")` on `RoleEntity`) would be ignored for writes — the
   Lesson 4 rule, unchanged.
2. **The field is a `Set`, not a `List`.** A user holds a role once or not at all; the
   join table's primary key (the pair) forbids duplicates anyway.
3. **It is a collection, so it loads lazily** — on first touch, like the seats list.

### 4. Simple example

`RoleEntity`:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "role" table. One object = one row.
@Entity
@Table(name = "role")
public class RoleEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name

    protected RoleEntity() {}   // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
}
```

`UserEntity` with the mapping:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// The Java class that stands for the "app_user" table. One object = one row.
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id;              // column: id (the primary key)

    private String email;         // column: email
    private String displayName;   // column: display_name

    // Lesson 5: a user has many roles AND a role has many users.
    // Neither table can hold the other's key, so the pairs live in the
    // join table "user_role". This side names that table and its two
    // key columns, which makes this side the owning side.
    @ManyToMany
    @JoinTable(
            name = "user_role",                                  // the join table
            joinColumns = @JoinColumn(name = "user_id"),         // my key column in it
            inverseJoinColumns = @JoinColumn(name = "role_id"))  // the other key column
    private Set<RoleEntity> roles = new HashSet<>();

    protected UserEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public Set<RoleEntity> getRoles() { return roles; }
}
```

`UserRepository` is the usual one-liner. The demo:

```java
package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 5: load ONE user, then read the roles that live behind the join table.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-user-roles
@Component
@Profile("l5-user-roles")
class L5UserRolesDemo implements CommandLineRunner {

    private final UserRepository users;

    L5UserRolesDemo(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID meeraId = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
        UserEntity user = users.findById(meeraId).orElseThrow();

        System.out.println(">>> user: " + user.getDisplayName()
                + " (" + user.getEmail() + ")");
        System.out.println(">>> now touching the roles set for the first time ...");
        for (RoleEntity role : user.getRoles()) {
            System.out.println(">>>   role: " + role.getName());
        }
    }
}
```

Real output:

```text
Hibernate: 
    select
        ue1_0.id,
        ue1_0.display_name,
        ue1_0.email 
    from
        app_user ue1_0 
    where
        ue1_0.id=?
>>> user: Meera (meera@example.com)
>>> now touching the roles set for the first time ...
Hibernate: 
    select
        r1_0.user_id,
        r1_1.id,
        r1_1.name 
    from
        user_role r1_0 
    join
        role r1_1 
            on r1_1.id=r1_0.role_id 
    where
        r1_0.user_id=?
>>>   role: organiser
>>>   role: attendee
```

### 5. Walkthrough

1. **Statement 1** selects from `app_user` only — no join. Compare with Lesson 1, where
   loading an event joined `venue` immediately: `venue` was `@ManyToOne` (immediate
   default), `roles` is a collection (first-touch default).
2. Meera prints, the touch line prints, **then** statement 2 appears.
3. **Statement 2**: take `user_role` rows `where user_id = ?` — the pairs — and
   `join role` to turn each `role_id` into a full role row. The hand-written two-table
   walk, generated.
4. Roles print in no particular order — a `Set` promises none.

The join table itself:

```text
            Table "public.user_role"
 Column  | Type | Collation | Nullable | Default 
---------+------+-----------+----------+---------
 user_id | uuid |           | not null | 
 role_id | uuid |           | not null | 
Indexes:
    "user_role_pk" PRIMARY KEY, btree (user_id, role_id)
Foreign-key constraints:
    "user_role_role_fk" FOREIGN KEY (role_id) REFERENCES role(id)
    "user_role_user_fk" FOREIGN KEY (user_id) REFERENCES app_user(id)
```

**Neither `app_user` nor `role` holds a foreign key for this link — the join table holds
both.** Its primary key is the pair, so the same pair cannot exist twice.

### 6. Where is it used?

Students and courses. Posts and tags. Actors and films. Products and categories. Whenever
"an X has many Y, and a Y has many X" is true, a pair table exists in the database.

### 7. Common mistakes

1. **Leaving out `@JoinTable` and trusting the guess.** Tested: startup fails with
   `Schema-validation: missing table [app_user_roles]` — Hibernate guessed owner table
   plus field name. Our table is `user_role`. Always write `@JoinTable` in full.
2. **Using `List` where the meaning is a set.** The database forbids duplicate pairs; the
   Java type should not pretend otherwise.
3. **Forgetting which side writes.** The `roles` set declared with `@JoinTable` is the
   owning side: adds and removes there do write pair rows at commit. A `mappedBy` mirror
   on `RoleEntity` would be ignored for writes.

### 8. Link to the task

Subtask S3, first half, done: `@ManyToMany` mapped, join table inspected, key fact noted —
the join table holds both foreign keys, the entity tables hold none. The second half (the
rule for a relationship that carries its own data) is Lesson 6.

### 9. Check my understanding

**Q1.** A university database has these three tables:

```sql
create table student ( id uuid primary key, name text not null );

create table course  ( id uuid primary key, title text not null );

create table student_course (
    student_id uuid not null references student (id),
    course_id  uuid not null references course (id),
    primary key (student_id, course_id)
);
```

A student takes many courses; a course has many students. Write the field (with all
annotations) that belongs in `StudentEntity` so that `student.getCourses()` works. Which
of the three tables hold foreign keys for this link?

**Answer.**

```java
@ManyToMany
@JoinTable(
        name = "student_course",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id"))
private Set<CourseEntity> courses = new HashSet<>();
```

Only `student_course` holds foreign keys — both of them (`student_id` and `course_id`).
`student` and `course` hold none.

**Q2.** Using the mapping from Q1 (a `courses` set on `StudentEntity`, nothing extra on
`CourseEntity`), a teammate says: *"We must also add a `students` set to `CourseEntity`,
otherwise the database cannot know which students take the Databases course."* Is the
teammate right? What does the database actually store, and how could you list the students
of one course without that set?

**Answer.** The teammate is wrong. The database stores every student–course pair in
`student_course`, no matter which Java fields exist — Java mappings are views over the
tables, not the storage itself. To list the students of one course without a mirror set,
query for it, for example on the student repository:

```java
@Query("select s from StudentEntity s join s.courses c where c.title = :title")
List<StudentEntity> findByCourseTitle(String title);
```

(Or a native SQL join through `student_course`.) Mirror sets are a convenience for code
already holding the object, never a storage requirement.

### Do it yourself (second laptop)

Continues inside `F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`.
The tables `app_user`, `role`, `user_role` already exist (migration V1), and the seed data
has Meera with two roles.

**Step 1 — create four files** in `src/main/java/com/zenith/rel/`: `RoleEntity.java`,
`UserEntity.java`, `L5UserRolesDemo.java` (shown above) and `UserRepository.java`:

```java
package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
}
```

**Step 2 — run the demo:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-user-roles
```

Expected: statement 1 on `app_user` alone; the touch line; statement 2 on
`user_role join role`; roles `organiser` and `attendee` in either order.

**Step 3 — break it on purpose.** Delete the `@JoinTable(...)` block, leaving bare
`@ManyToMany`. Run. Expect `Schema-validation: missing table [app_user_roles]`. Restore
and rerun.

**Step 4 — look at the join table:**

```bash
docker exec pg psql -U postgres -d zenith -c "\d user_role"
```

| You see | It means | Fix |
|---|---|---|
| `missing table [app_user_roles]` | `@JoinTable` missing, name guessed | Write `@JoinTable` in full |
| `NoSuchElementException` | User seed rows missing | Load `seed-dev-data.sql` |
| Roles in a different order | A `Set` has no promised order | Not a problem |

**Done when:** you can point at `\d user_role` and say: the primary key is the pair, both
foreign keys live here, neither `app_user` nor `role` carries one.

### Summary

- `@ManyToMany` maps both-sides-many links. The pairs live in a join table; neither entity
  table holds a foreign key.
- `@JoinTable` names the pair table, `joinColumns` my key column, `inverseJoinColumns` the
  other side's. Write it in full, always.
- The `@JoinTable` side owns the link; a `mappedBy` mirror is read-only in effect.
- Use a `Set`; collections load on first touch.

---

## Lesson 6 — When the link itself has data, make it an entity (subtask S3)

### 1. What is it?

A design rule, not a new annotation: **the moment a relationship needs data of its own,
stop using `@ManyToMany`.** Turn the join table into a real table with its own primary
key, map it with a real entity, and connect both sides with two plain `@ManyToOne` links.

### 2. Why do we need it?

Take the user–role link and add one business question: *who granted this role, and when?*

Try to place those facts in the `@ManyToMany` world. On `RoleEntity`? Wrong — "organiser"
is one shared row; Meera's grant date would overwrite Arjun's. On `UserEntity`? Wrong —
Meera has two roles; which grant would the field mean? The truth: `granted_by` and
`granted_at` describe **the pair**. And under `@ManyToMany` the pair has no Java object —
the `user_role` rows are invisible, managed behind the scenes. There is nowhere to hang a
field. The mapping is a dead end the moment the link grows data.

### 3. How does it work?

1. **A new table by migration** — the join table plus the new columns, but with its own
   `id`, and the old "no duplicate pairs" rule kept by a unique constraint.
2. **An entity for it** — two `@ManyToOne` fields plus plain fields for the link's data.
3. **The `@ManyToMany` disappears** in a real project (copy the pairs over, drop
   `user_role`). The scratch app keeps Lesson 5's mapping for reference and builds the
   grown-up version next to it: `role_grant`.

Naming: the grown-up table earns a real name — a grant, an enrollment, an employment. The
row stopped being a bare pair and became a fact of its own.

### 4. Simple example

The migration:

```sql
-- V6__create_role_grant.sql
-- Lesson 6: the join table grows up. When the relationship itself carries
-- data (who granted the role, and when), the pair table becomes a real
-- table: its own primary key, its own columns, and the old pair rule
-- kept alive by a unique constraint.

create table role_grant (
    id          uuid primary key,
    user_id     uuid not null,
    role_id     uuid not null,
    granted_by  text not null,
    granted_at  timestamptz not null default now(),
    constraint role_grant_user_fk foreign key (user_id) references app_user (id),
    constraint role_grant_role_fk foreign key (role_id) references role (id),
    constraint role_grant_pair_unique unique (user_id, role_id)
);

create index idx_role_grant_user_id on role_grant (user_id);
create index idx_role_grant_role_id on role_grant (role_id);
```

The entity — Lesson 1's pattern twice, plus ordinary fields:

```java
package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// Lesson 6: the join table, grown up. One object = one granted role,
// with data about the granting itself.
@Entity
@Table(name = "role_grant")
public class RoleGrantEntity {

    @Id
    private UUID id;                   // its OWN primary key now

    // The two halves of the old pair, each a plain @ManyToOne (Lesson 1 pattern).
    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    // Data about the LINK itself - impossible to place in a @ManyToMany.
    private String grantedBy;          // column: granted_by
    private OffsetDateTime grantedAt;  // column: granted_at

    protected RoleGrantEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public RoleEntity getRole() { return role; }
    public String getGrantedBy() { return grantedBy; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
}
```

Seed rows: three grants matching the existing `user_role` pairs
(`99999999-...0001/2/3`), with granters `system-signup` and `admin@zenith.io`. The demo
(`L6RoleGrantsDemo`, profile `l6-role-grants`) lists all grants inside one transaction:

```java
    @Override
    @Transactional
    public void run(String... args) {
        for (RoleGrantEntity grant : grants.findAll()) {
            System.out.println(">>> " + grant.getRole().getName()
                    + " -> " + grant.getUser().getDisplayName()
                    + " | granted by " + grant.getGrantedBy()
                    + " on " + grant.getGrantedAt().toLocalDate());
        }
    }
```

Real output:

```text
Hibernate: 
    select rge1_0.id, rge1_0.granted_at, rge1_0.granted_by, rge1_0.role_id, rge1_0.user_id 
    from role_grant rge1_0
Hibernate: 
    select re1_0.id, re1_0.name from role re1_0 where re1_0.id=?
Hibernate: 
    select ue1_0.id, ue1_0.display_name, ue1_0.email from app_user ue1_0 where ue1_0.id=?
Hibernate: 
    select re1_0.id, re1_0.name from role re1_0 where re1_0.id=?
Hibernate: 
    select ue1_0.id, ue1_0.display_name, ue1_0.email from app_user ue1_0 where ue1_0.id=?
>>> attendee -> Meera | granted by system-signup on 2026-10-25
>>> organiser -> Meera | granted by admin@zenith.io on 2026-10-26
>>> attendee -> Arjun | granted by system-signup on 2026-10-27
```

### 5. Walkthrough

- Statement 1 fetches the three grant rows, each carrying `user_id`, `role_id`, and the
  link's own data.
- Each printed line reads like a sentence: *attendee was granted to Meera by system-signup
  on 2026-10-25.* The pair became a thing you can talk about, log, and query.
- Count the statements: **five, to print three rows.** After the grants loaded, Hibernate
  fetched each grant's role and user with separate selects (repeats came free from the
  persistence context, which remembers rows already loaded in the transaction). Nobody
  asked for five statements — that is the `@ManyToOne` immediate-loading default at work.
  Lesson 7 turns it off; Lesson 13 shows the fully grown disease.

### 6. Where is it used?

- **Order line**: product–order plus quantity and price.
- **Enrollment**: student–course plus grade and enrollment date.
- **Employment**: person–company plus start date, end date, salary.

Test: if the pair gains even one adjective people care about, it wants to be an entity.

### 7. Common mistakes

1. **Bolting columns onto `@ManyToMany`.** No annotation exists for "extra column on the
   join table". The clean answer is always: promote the table.
2. **Composite primary keys in JPA.** `@EmbeddedId` works but is verbose and error-prone.
   Project rule: plain `id` as primary key, `unique (user_id, role_id)` for the pair rule.
3. **Forgetting the unique constraint.** Without it, two identical grants are possible.
   The constraint is the old primary key living on.

### 8. Link to the task

Subtask S3, second half, done. The rule, also written in `NOTES.md`:

> The moment a relationship needs data of its own (who granted the role, and when), it is
> not a `@ManyToMany` any more. Make the join table a real table with its own primary key,
> keep the pair rule with a unique constraint, map it as an entity, and link both sides
> with `@ManyToOne`.

### 9. Check my understanding

**Q1.** A university database has `student(id, name)`, `course(id, title)`, and a join
table `student_course(student_id, course_id)` mapped with `@ManyToMany`. New requirement:
store the **grade** a student received and the **date they enrolled**. Write (a) the SQL
for the replacement table and (b) the entity class (relationship and data fields only).
Name both something better than "student_course".

**Answer.**
(a)

```sql
create table enrollment (
    id           uuid primary key,
    student_id   uuid not null,
    course_id    uuid not null,
    grade        text,
    enrolled_at  date not null,
    constraint enrollment_student_fk foreign key (student_id) references student (id),
    constraint enrollment_course_fk  foreign key (course_id)  references course (id),
    constraint enrollment_pair_unique unique (student_id, course_id)
);
```

(`grade` stays empty until the course ends, so it has no `not null`.)
(b)

```java
@Entity
@Table(name = "enrollment")
public class EnrollmentEntity {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private CourseEntity course;

    private String grade;          // column: grade
    private LocalDate enrolledAt;  // column: enrolled_at
}
```

**Q2.** For each relationship, plain `@ManyToMany` or promoted entity, and why?
1. Blog posts and tags, nothing stored about the tagging itself.
2. People and companies, with start date, end date, and salary per stint.
3. Users and roles, with "who granted it and when".

**Answer.**
1. Stays `@ManyToMany` — the link carries no data of its own.
2. Becomes an entity (call it `Employment`) — salary and dates are data about the link.
3. Becomes an entity (`RoleGrant`) — granter and time are data about the link; exactly
   this lesson's example.

### Do it yourself (second laptop)

Continues inside `F6.11-relationships-lazy-loading-and-the-n-plus-1-problem/scratch-app`.

**Step 1 —** create `src/main/resources/db/migration/V6__create_role_grant.sql` (shown
above; it must come after your existing V5).

**Step 2 —** create `RoleGrantEntity.java`, `L6RoleGrantsDemo.java` (shown above) and
`RoleGrantRepository.java`:

```java
package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoleGrantRepository extends JpaRepository<RoleGrantEntity, UUID> {
}
```

**Step 3 —** apply the migration (run once, Ctrl+C), then seed:

```bash
cd scratch-app
mvn -q spring-boot:run
docker exec pg psql -U postgres -d zenith -c "
INSERT INTO public.role_grant (id, user_id, role_id, granted_by, granted_at) VALUES
 ('99999999-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001', 'system-signup',   '2026-10-25 10:00:00+00'),
 ('99999999-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000002', 'admin@zenith.io', '2026-10-26 09:30:00+00'),
 ('99999999-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001', 'system-signup',   '2026-10-27 14:15:00+00');"
```

**Step 4 —** run the demo and count the statements:

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-role-grants
```

Expected: three grant lines, **five** selects.

**Step 5 —** write the rule into `NOTES.md` in the task folder.

| You see | It means | Fix |
|---|---|---|
| `missing table [role_grant]` | V6 never ran | Step 3; watch for `Migrating ... version "6"` |
| `duplicate key ... role_grant_pair_unique` | Same pair granted twice | The constraint works; change the pair |
| `violates foreign key constraint` | Grant points at missing user/role | Load `seed-dev-data.sql` first |

**Done when:** `NOTES.md` has the rule, the demo prints three sentences, and you can
explain why the table got its own `id` plus a unique constraint.

### Summary

- A relationship that carries data is an entity, not a `@ManyToMany`.
- Promote the join table: own `id`, unique constraint on the pair, two `@ManyToOne`
  links, real name (`role_grant`, `enrollment`, `order_line`).
- The demo's five statements for three rows were the eager default showing itself —
  Lesson 7 makes everything LAZY, Lesson 13 shows the disease at full size.

---

## Lesson 7 — Fetch types: `LAZY`, `EAGER`, and the `@ManyToOne` trap (subtask S4)

### 1. What is it?

Every relationship has a **fetch type** — when does the related data load?

- `EAGER`: together with the owner, immediately, needed or not.
- `LAZY`: on first touch, not before.

The defaults are inconsistent, and that is the trap: **`@ManyToOne` and `@OneToOne`
default to `EAGER`; collections (`@OneToMany`, `@ManyToMany`) default to `LAZY`.**

### 2. Why do we need it?

The evidence so far: loading **one seat** joined **three tables** (Lesson 2); listing
**three grants** cost **five statements** (Lesson 6). One seat quietly loaded a third of
the schema. At five thousand order lines, each dragging its product eagerly, this kills
endpoints.

The deeper reason `LAZY` is the only safe default is the asymmetry: a `LAZY` relationship
can be fetched eagerly *per query* when needed (`join fetch`, Lesson 14); an `EAGER` one
cannot be switched off per query. You can always ask for more; you can never ask for
less. Project rule: **every relationship is `LAZY`, written out explicitly**, even where
it is already the default, so the file says what happens.

### 3. How does it work?

The annotation takes a setting: `@ManyToOne(fetch = FetchType.LAZY)` (import
`jakarta.persistence.FetchType`). Applied to all five relationships: `EventEntity.venue`,
`SeatEntity.seatMap`, `SeatMapEntity.venue`, `RoleGrantEntity.user`,
`RoleGrantEntity.role` — and written redundantly on the collections
(`SeatMapEntity.seats`, `UserEntity.roles`).

Before the first touch, a lazy field holds a stand-in object that knows only the id and
fetches the rest when a real method is called on it. That stand-in is the **proxy** —
Lesson 8's subject.

### 4. Simple example

One edited entity (the others changed the same way):

```java
    // Many seats belong to one seat map, so: @ManyToOne.
    // Lesson 7: LAZY written out. @ManyToOne defaults to EAGER - the trap -
    // so this project writes the fetch type on every relationship.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_map_id")   // the column in THIS table that holds the key
    private SeatMapEntity seatMap;
```

The demo (`L7FetchLazyDemo`, profile `l7-fetch-lazy`) loads one seat and touches the
chain link by link:

```java
    @Override
    @Transactional
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

        SeatEntity seat = seats.findById(seatA1).orElseThrow();   // select 1: seat only
        System.out.println(">>> seat loaded: row " + seat.getRowLabel()
                + ", number " + seat.getSeatNumber());

        System.out.println(">>> touching the seat map now ...");
        System.out.println(">>> seat map: " + seat.getSeatMap().getName());        // select 2

        System.out.println(">>> touching the venue now ...");
        System.out.println(">>> venue: " + seat.getSeatMap().getVenue().getName()); // select 3
    }
```

Real output:

```text
Hibernate: 
    select se1_0.id, se1_0.row_label, se1_0.seat_map_id, se1_0.seat_number 
    from seat se1_0 where se1_0.id=?
>>> seat loaded: row A, number 1
>>> touching the seat map now ...
Hibernate: 
    select sme1_0.id, sme1_0.name, sme1_0.venue_id 
    from seat_map sme1_0 where sme1_0.id=?
>>> seat map: Riverside Floor
>>> touching the venue now ...
Hibernate: 
    select ve1_0.id, ve1_0.city, ve1_0.name 
    from venue ve1_0 where ve1_0.id=?
>>> venue: Riverside Arena
```

### 5. Walkthrough

**Before** (Lesson 2, same seat, same `findById`): one statement, three tables joined.
**After**: three statements, one table each, and each select appears only *after* its
touch line. Had the method stopped after printing the row label, total cost: one small
select. Code that needs little, pays little.

**The honest part.** Rerunning the Lesson 6 grants demo after the change: **still five
statements** — now interleaved with the prints instead of all up front:

```text
select ... from role_grant ...
select ... from role ...        <- touched in loop pass 1
select ... from app_user ...    <- touched in loop pass 1
>>> attendee -> Meera | ...
select ... from role ...        <- touched in loop pass 2
>>> organiser -> Meera | ...
select ... from app_user ...    <- touched in loop pass 3
>>> attendee -> Arjun | ...
```

**`LAZY` controls *when* data loads, not *how many* statements run.** If code touches the
related data of every row in a list, the little selects still happen. That pattern is the
N+1 problem (Lesson 13); its real fix is `join fetch` (Lesson 14). `LAZY` remains the
right default: it makes cost visible and leaves the choice per query.

Consequence left in place on purpose: the old Lesson 1 and Lesson 2 demos
(`l1-event-venue`, `l2-seat-chain`) have no `@Transactional` and touch relationships —
rerunning them now crashes with `LazyInitializationException`. They are exhibits for
Lessons 8–9.

### 6. Where is it used?

All-relationships-LAZY, written out, is standard in production Spring codebases and holds
for the whole ticketing project.

### 7. Common mistakes

1. **Forgetting which default is dangerous.** Collections are safe by default; the
   innocent-looking `@ManyToOne` is not — and it is the most common relationship there is.
2. **Believing `LAZY` fixes N+1.** Five statements stayed five. `LAZY` delays and exposes;
   `join fetch` removes.
3. **Making things `EAGER` "to be safe" after the first lazy crash.** The crash disappears
   and every load quietly drags whole table neighborhoods, everywhere, forever. The right
   fix is Lessons 9–12.

### 8. Link to the task

Subtask S4 done: every relationship explicitly `LAZY`.

### 9. Check my understanding

**Q1.** Two entities, no fetch settings written:

```java
@Entity
@Table(name = "book")
public class BookEntity {
    @Id private UUID id;
    private String title;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private AuthorEntity author;
}

@Entity
@Table(name = "author")
public class AuthorEntity {
    @Id private UUID id;
    private String name;

    @OneToMany(mappedBy = "author")
    private List<BookEntity> books = new ArrayList<>();
}
```

(a) When a book is loaded, does its author load immediately or on first touch? (b) When an
author is loaded, do the books load immediately or on first touch? (c) Rewrite both
relationship declarations the way this project requires.

**Answer.**
(a) Immediately — `@ManyToOne` defaults to `EAGER`.
(b) On first touch — collections default to `LAZY`.
(c)

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private AuthorEntity author;

@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
private List<BookEntity> books = new ArrayList<>();
```

**Q2.** `CityEntity` → `StateEntity` → `CountryEntity`, both links
`@ManyToOne(fetch = FetchType.LAZY)`. Inside one transaction:

```java
CityEntity city = cities.findById(someId).orElseThrow();   // line 1
System.out.println(city.getName());                        // line 2
```

(a) How many SQL statements? (b) After adding
`System.out.println(city.getState().getName());` — how many? (c) After further adding
`System.out.println(city.getState().getCountry().getName());` — how many?

**Answer.** (a) One — only the city row; nothing else was touched. (b) Two — the state
loads on its first touch. (c) Three — the country loads on its first touch. Each touch
pays exactly one select; untouched links cost nothing.

### Do it yourself (second laptop)

**Step 1 — edit five entities.** Add `import jakarta.persistence.FetchType;` to each,
then: `EventEntity.venue`, `SeatEntity.seatMap`, `SeatMapEntity.venue`,
`RoleGrantEntity.user`, `RoleGrantEntity.role` all get
`@ManyToOne(fetch = FetchType.LAZY)`; `SeatMapEntity.seats` gets
`@OneToMany(mappedBy = "seatMap", fetch = FetchType.LAZY)`; `UserEntity.roles` gets
`@ManyToMany(fetch = FetchType.LAZY)`.

**Step 2 — create `L7FetchLazyDemo.java`** and run:

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l7-fetch-lazy
```

Expected: three one-table selects, each only after its touch line.

**Step 3 — rerun the grants demo and count:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-role-grants
```

Expected: still five `Hibernate:` statements, now interleaved with the `>>>` lines.

**Step 4 — optional: see the coming crash.**
`mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-seat-chain` →
`LazyInitializationException ... no Session`. Do not fix it yet.

| You see | It means | Fix |
|---|---|---|
| `cannot find symbol: variable FetchType` | Missing import | `import jakarta.persistence.FetchType;` |
| `LazyInitializationException` in the l7 demo | `@Transactional` missing on `run` | Keep it; Lesson 9 explains |
| One big joined select in the l7 demo | Some `@ManyToOne` still eager | Re-check all five entities |

**Done when:** the three selects appear only after their touches, the grants demo still
counts five, and you can say from memory which annotations default to `EAGER`.

### Summary

- Fetch type = when related data loads. `EAGER` = with the owner; `LAZY` = on first touch.
- Defaults: `@ManyToOne`/`@OneToOne` are `EAGER` (the trap); collections are `LAZY`.
- Project rule: everything `LAZY`, written out.
- `LAZY` changes *when*, not *how many*. The statement count for a touch-everything list
  stays the same — that is the N+1, and `join fetch` is its fix.
- You can always fetch more per query; you can never fetch less than `EAGER` demands.

---

## Lesson 8 — The Hibernate proxy: the stand-in a lazy field holds (prepares S5)

### 1. What is it?

A **proxy** is the object Hibernate puts into a lazy field as a stand-in for the real
row: a generated **subclass** of the entity that starts out knowing only the id, and
loads everything else the moment a real method is called on it.

It is the third stand-in in this course: the repository implementation and the
`@Transactional` wrapper (previous task) were also objects pretending to be another while
adding behavior. This one's job: delay a database read until someone actually asks.

### 2. Why do we need it?

When Hibernate loads seat A1, the `seatMap` field must hold *something*:

- **`null`?** No — null already means "there is no related row". Code could never tell
  "absent" from "not loaded yet".
- **The real `SeatMapEntity`?** No — building it needs the exact query `LAZY` avoids.
- **A stand-in that type-checks, knows the id, and can load on touch?** Yes — the proxy.

**The id is free.** The loaded seat row contains `seat_map_id` — the foreign key *is* the
related row's id. That is why the proxy can exist without any extra query, and why
`getId()` on a proxy costs nothing.

### 3. How does it work?

1. Hibernate generates a subclass (`SeatMapEntity$HibernateProxy`) with intercepted
   methods.
2. On load of the owner, one instance is created with the id from the foreign key and a
   link to the current session, and plugged into the field.
3. `getId()` answers from memory. No query.
4. The first real method call (`getName()`) makes the proxy load through the session: one
   select, then the call is answered; later calls answer from the loaded data.
5. No session at touch time → `LazyInitializationException` (Lesson 9).

Consequences: entity classes and methods must not be `final` (subclassing), and the
no-argument constructor must exist.

### 4. Simple example

```java
    @Override
    @Transactional
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        SeatEntity seat = seats.findById(seatA1).orElseThrow();   // select 1: seat only

        SeatMapEntity map = seat.getSeatMap();    // taking the stand-in: no select

        System.out.println(">>> is it null?                  " + (map == null));
        System.out.println(">>> its class:                   " + map.getClass().getName());
        System.out.println(">>> exactly SeatMapEntity.class? " + (map.getClass() == SeatMapEntity.class));
        System.out.println(">>> instanceof SeatMapEntity?    " + (map instanceof SeatMapEntity));
        System.out.println(">>> its id (watch the log):      " + map.getId());
        System.out.println(">>> now its name (watch the log) ...");
        System.out.println(">>> name: " + map.getName());
        System.out.println(">>> class after loading:         " + map.getClass().getName());
    }
```

Real output:

```text
Hibernate: 
    select se1_0.id, se1_0.row_label, se1_0.seat_map_id, se1_0.seat_number 
    from seat se1_0 where se1_0.id=?
>>> is it null?                  false
>>> its class:                   com.zenith.rel.SeatMapEntity$HibernateProxy
>>> exactly SeatMapEntity.class? false
>>> instanceof SeatMapEntity?    true
>>> its id (watch the log):      eeeeeeee-0000-0000-0000-000000000001
>>> now its name (watch the log) ...
Hibernate: 
    select sme1_0.id, sme1_0.name, sme1_0.venue_id 
    from seat_map sme1_0 where sme1_0.id=?
>>> name: Riverside Floor
>>> class after loading:         com.zenith.rel.SeatMapEntity$HibernateProxy
```

### 5. Walkthrough

1. One select at the top — the seat only. `getSeatMap()` fires nothing: we picked up the
   stand-in.
2. Not null. A lazy field never is (when the foreign key has a value).
3. The class is `SeatMapEntity$HibernateProxy` — a generated subclass.
4. `getClass() == SeatMapEntity.class` is **false**, `instanceof` is **true**. A subclass
   is never the same class but is-a `SeatMapEntity`. This is why entity `equals` must use
   `instanceof`, never `getClass() ==` (Lesson 19).
5. The id prints with **no select** — lifted from the seat's `seat_map_id` column.
6. The `seat_map` select fires exactly between "now its name" and the name. First real
   call, first load.
7. After loading, the object is still the proxy. It filled with data; it did not swap.

### 6. Where is it used?

Every lazy `@ManyToOne`/`@OneToOne` field holds one until touched. Lazy collections use a
sibling trick — wrapper list/set classes that load on first use.

### 7. Common mistakes

1. **Null-checking to ask "is it loaded?"** — says nothing; the field is a proxy, not
   null, whenever the foreign key has a value.
2. **`getClass() ==` comparisons.** False against a live proxy. Use `instanceof`.
3. **`final` entities or methods.** Hibernate cannot subclass them; the lazy machinery
   degrades. Entities are never final.
4. **Trusting the debugger.** An uninitialized proxy shows null fields; the data lives
   behind the interception. Call getters.

### 8. Link to the task

The word **proxy** from the task's word list is now concrete. Next: touch one after the
session closes — `LazyInitializationException`, subtask S5.

### 9. Check my understanding

**Q1.** Two entities, lazily linked:

```java
@Entity
@Table(name = "book")
public class BookEntity {
    @Id private UUID id;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AuthorEntity author;

    public AuthorEntity getAuthor() { return author; }
}

@Entity
@Table(name = "author")
public class AuthorEntity {
    @Id private UUID id;
    private String name;

    public UUID getId() { return id; }
    public String getName() { return name; }
}
```

Inside one transaction (the book exists; its `author_id` is filled):

```java
BookEntity book = books.findById(bookId).orElseThrow();   // one select ran: book only
AuthorEntity a = book.getAuthor();

System.out.println(a == null);                            // print 1
System.out.println(a instanceof AuthorEntity);            // print 2
System.out.println(a.getClass() == AuthorEntity.class);   // print 3
System.out.println(a.getId());                            // print 4
System.out.println(a.getName());                          // print 5
```

For each print: what value, and does a select fire at that line?

**Answer.**
1. `false`, no query — the field holds a proxy, never null when the key is filled.
2. `true`, no query — the proxy is a subclass, so it is-an `AuthorEntity`.
3. `false`, no query — a subclass is not the same class object.
4. The author's UUID, **no query** — the proxy carries the id, taken from
   `book.author_id`.
5. The author's name, **and one select on `author` fires at exactly this line** — the
   first real method call initializes the proxy.

**Q2.** A teammate proposes: *"Skip the proxy. Hibernate should leave the lazy field
`null` and fill it in when someone asks."* Give two reasons this cannot work.

**Answer.**
1. `null` already has a meaning: "no related row" (foreign keys can be empty). If null
   also meant "not loaded yet", no code could tell absence from laziness.
2. Nothing runs when a getter returns a plain field. "Fill it when someone asks" needs
   code to execute at the moment of the ask — and that requires the ask to be a method
   call on an object Hibernate controls. The proxy *is* that object. (Doing it without a
   proxy would mean rewriting your class's bytecode, which standard setups do not do.)

### Do it yourself (second laptop)

**Step 1 —** create `L8ProxyDemo.java` (shown above) and run:

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-proxy
```

Expected: one seat select; `false / ...$HibernateProxy / false / true`; the id with no
select; the `seat_map` select only after "now its name"; proxy class again at the end.
(Your proxy class suffix may differ — Hibernate names it at runtime.)

**Step 2 —** temporarily delete `@Transactional` from the demo, rerun, and read:

```text
org.hibernate.LazyInitializationException: could not initialize proxy
[com.zenith.rel.SeatMapEntity#eeeeeeee-0000-0000-0000-000000000001] - no Session
```

Put `@Transactional` back.

| You see | It means | Fix |
|---|---|---|
| `LazyInitializationException ... no Session` | Proxy touched with no session | Restore `@Transactional`; Lesson 9 explains |
| `NoSuchElementException` | Seat seed row missing | Re-run the Lesson 2 insert |

**Done when:** you can answer from memory: where did the proxy get its id without a
query; why is `instanceof` true but `getClass() ==` false; at which line did the select
run.

### Summary

- A lazy field holds a **proxy**: a generated subclass knowing only the id.
- The id came free from the owner's foreign key column; `getId()` never queries.
- The first real method call loads through the session; no session → the famous
  exception.
- `instanceof` yes, `getClass() ==` no. Entities and their methods are never `final`.

---

## Lesson 9 — Break it: `LazyInitializationException` in plain code (S5 part 1, CP2)

### 1. What is it?

`LazyInitializationException` is Hibernate's way of saying: **"you touched a lazy
relationship after the transaction ended, so there is no session left to load it with."**
The most famous crash in Hibernate — this lesson produces it on purpose and reads it word
by word.

### 2. Why do we need it?

You will meet this exception for the rest of your Java career. People who do not
understand it reach for terrible fixes: `EAGER` everywhere, or a setting that hides the
problem (next lessons). Understanding the mechanism makes the right fix obvious.

### 3. How does it work?

Recap: the **session** is Hibernate's working object — it runs the SQL and holds the
persistence context (the notebook of loaded rows). In Spring's default setup **a session
is born when a transaction starts and dies when the transaction ends.**

The timeline of the crash:

1. The runner calls a service method marked `@Transactional`. Transaction starts,
   session opens.
2. `findById` loads the event. Its `venue` field gets a **proxy** — a stand-in holding
   only the venue's id, plus a link *to this session*.
3. The method returns the entity. Commit. **The session closes.** The entity lives on as
   a plain object — it is now **detached** — and its proxy points at a dead session.
4. The runner touches `event.getVenue().getName()`. The proxy must run a select. It asks
   its session. There is no session. **`LazyInitializationException`.**

Detachment erases nothing already loaded. Only *new loading* is impossible.

### 4. Simple example

The service — the anti-pattern, on purpose: it returns an entity across the transaction
border:

```java
@Service
public class L9EventService {

    private final EventRepository events;

    L9EventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventEntity loadEvent(UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        System.out.println(">>> inside the service: transaction open, session open");
        return event;   // <-- commit happens here; the session closes here
    }
}
```

The caller — note: NO `@Transactional`, that is the point:

```java
@Component
@Profile("l9-break")
class L9BreakDemo implements CommandLineRunner {

    private final L9EventService service;

    L9BreakDemo(L9EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        UUID monsoonId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        EventEntity event = service.loadEvent(monsoonId);
        System.out.println(">>> back in the runner: transaction over, session closed");

        System.out.println(">>> event name (already loaded): " + event.getName());
        System.out.println(">>> venue id (proxy, no query):  " + event.getVenue().getId());

        System.out.println(">>> venue name (needs a query) ...");
        try {
            System.out.println(">>> venue name: " + event.getVenue().getName());
        } catch (LazyInitializationException e) {
            System.out.println(">>> CRASH:   " + e.getClass().getName());
            System.out.println(">>> message: " + e.getMessage());
        }
    }
}
```

Real output:

```text
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id 
    from event ee1_0 where ee1_0.id=?
>>> inside the service: transaction open, session open
>>> back in the runner: transaction over, session closed
>>> event name (already loaded): Monsoon Rock Night
>>> venue id (proxy, no query):  11111111-1111-1111-1111-111111111111
>>> venue name (needs a query) ...
>>> CRASH:   org.hibernate.LazyInitializationException
>>> message: Could not initialize proxy [com.zenith.rel.VenueEntity#11111111-1111-1111-1111-111111111111] - no session
```

### 5. Walkthrough

1. **`event.getName()` worked** — that column arrived with the select inside the
   service. Detached entities keep their loaded data.
2. **`event.getVenue().getId()` worked** — the proxy was born holding the id (from the
   event row's `venue_id` column). Answering from memory needs no session.
3. **`event.getVenue().getName()` crashed** — the name was never loaded. Loading needs a
   select; a select needs a session; the session died when `loadEvent` returned.

The message, word by word:

| Message part | Meaning |
|---|---|
| `Could not initialize proxy` | A stand-in tried to load its real row, right now, and failed |
| `[com.zenith.rel.VenueEntity#11111111-...]` | Which row it wanted: the venue with exactly this id |
| `no session` | The loader it needed is gone — closed when the transaction committed |

The log always tells you *what* was touched too late. It cannot tell you *where the
transaction should have ended* — that is a design decision (Lesson 12).

### 6. Where is it met?

1. **Controllers serializing entities to JSON** — the serializer walks every getter,
   hits a lazy field, the exception becomes an HTTP 500 (next lesson).
2. **Logging** — `toString()` that includes a relationship (Lesson 16).
3. **Work after the service returns** — background threads, mappers, templates.

### 7. Common mistakes — the three wrong fixes

1. **Flip to `EAGER`.** Crash gone — and now every load everywhere drags the
   relationship with no opt-out. One visible crash traded for invisible bulk loading.
2. **Turn on Open Session In View.** Crash gone, worse reason — the session is held open
   through the whole web request and queries run from inside the JSON writer
   (Lesson 11).
3. **Catch the exception and ship partial data.** The demo's `try/catch` is for teaching
   only; swallowing it in real code silently drops fields.

The right fix has one shape: **finish all touching while the transaction is alive** —
load what the caller needs, copy it into a plain record, return the record (Lesson 12).

### 8. Link to the task

S5 half done (plain-code version; HTTP version next). Checkpoint question 2 answered:
**a session is born with the transaction and dies with it; proxies can only load through
a live session; touching an unloaded relationship after the transaction ends asks a dead
session to run a query, and Hibernate answers with `LazyInitializationException`.**

### 9. Check my understanding

**Q1.** A shop app:

```java
@Service
public class OrderService {

    private final OrderRepository orders;

    OrderService(OrderRepository orders) { this.orders = orders; }

    @Transactional(readOnly = true)
    public OrderEntity loadOrder(UUID id) {
        return orders.findById(id).orElseThrow();
    }
}

// elsewhere, in a class with no @Transactional anywhere:
OrderEntity order = orderService.loadOrder(orderId);
System.out.println(order.getId());                        // line A
System.out.println(order.getCustomer().getId());          // line B
System.out.println(order.getCustomer().getName());        // line C
```

`OrderEntity.customer` is `@ManyToOne(fetch = FetchType.LAZY)` and the order's
`customer_id` column is filled. Which lines print, which line throws, and why?

**Answer.**
- **Line A prints.** The order's own columns were loaded inside the service transaction;
  detached entities keep loaded data.
- **Line B prints.** `getCustomer()` returns the proxy; its id came free from the order
  row's `customer_id` column — answered from memory, no session needed.
- **Line C throws `LazyInitializationException`.** The customer's name was never loaded;
  loading needs a select through a live session, and the session closed when `loadOrder`
  returned.

**Q2.** In the demo, `event.getVenue().getId()` printed a value but
`event.getVenue().getName()` crashed, two lines apart, on the same proxy object. Explain
the difference in terms of what the proxy holds from birth and what it must do to answer
each call.

**Answer.** From birth the proxy holds exactly two things: the venue's id (copied from
the event row's `venue_id` foreign key) and a link to the session it was born in.
`getId()` is answered from what it already holds — no SQL, works even detached.
`getName()` holds nothing to answer with; it must run a select, a select needs a live
session, and that session died when the service method returned — so it throws.

### Do it yourself (second laptop)

**Step 1 —** create `L9EventService.java` and `L9BreakDemo.java` (shown above) in
`scratch-app/src/main/java/com/zenith/rel/`.

**Step 2 —** run:

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l9-break
```

Expected: one select on `event` only; event name and venue id print fine; then:

```text
>>> CRASH:   org.hibernate.LazyInitializationException
>>> message: Could not initialize proxy [com.zenith.rel.VenueEntity#11111111-...] - no session
```

**Step 3 — prove the diagnosis.** Add `@Transactional`
(`org.springframework.transaction.annotation.Transactional`) to the runner's `run`
method, rerun: the same code succeeds — a second select fires at the venue-name line and
`Riverside Arena` prints. The only change: the session now outlives the touch. **Remove
it again** — the demo stays broken on purpose, as the exhibit.

**Step 4 — optional:** rerun the Lesson 1 demo (`l1-event-venue`) to see the same
exception uncaught — a full stack trace ending the app, the form you meet in the wild.

| You see | It means | Fix |
|---|---|---|
| No crash in step 2 | `@Transactional` still on `run`, or venue still `EAGER` | Remove it / re-check Lesson 7 |
| Crash already at `getVenue().getId()` | Id getter not recognized | Keep `@Id` on the field, getter named `getId` |
| `NoSuchElementException` | Event seed row missing | Load `seed-dev-data.sql` |

**Done when:** you can say without notes what still works on a detached entity (loaded
data), what also works (the proxy's id), what fails (anything needing a new select) —
and decode all three parts of the exception message.

### Summary

- Session and transaction live and die together; proxies load only through a live
  session.
- After the transaction: loaded data readable, proxy id readable, new loading →
  `LazyInitializationException`.
- The message names the entity and id touched too late: `Could not initialize proxy
  [Entity#id] - no session`.
- Wrong fixes: `EAGER`, Open Session In View, catch-and-ignore. Right fix: finish
  touching inside the transaction, return records.

---

## Lesson 10 — Break it over HTTP: where everyone meets the exception (S5 complete)

### 1. What is it?

The Lesson 9 crash in its natural habitat: a REST controller. Core sentence: **the web
layer runs outside your transaction** — Tomcat's request thread, the controller code,
and the JSON writing all happen with no session open. An entity that crosses from the
service into that world carries dead proxies with it.

### 2. Why do we need it?

Nobody meets `LazyInitializationException` in a `CommandLineRunner`. They meet it as a
mysterious HTTP 500 that only happens on *some* endpoints, with a clean-looking
controller. This lesson teaches the costume it wears on the web: the status code, the
anonymous error body, and the one log line that tells the truth.

### 3. How does it work?

Two new pieces:

- **The web starter** (`spring-boot-starter-web`) brings an embedded web server (Tomcat)
  and **Jackson**, the library that turns Java objects into JSON. When a
  `@RestController` method returns an object, Spring hands it to Jackson, and Jackson
  **calls every public getter** to build the JSON.
- **One setting, explained next lesson.** Spring Boot's *Open Session In View* default
  keeps a session open through the whole web request — invented to paper over exactly
  this crash. The task's focus notes order it off, so the config now says
  `spring.jpa.open-in-view: false`: the session honestly dies with the transaction.

Timeline of one request, `GET /events/{id}`:

1. Tomcat routes the request to the controller method. **No transaction is open.**
2. The controller calls `events.findById(id)`. Recap: every built-in repository method
   runs in **its own small transaction** — opens it, runs the select, closes it before
   returning. The event loads fine.
3. That tiny transaction is over. The entity is detached; `venue` holds a proxy tied to
   a session that just closed.
4. The controller returns the entity. Jackson walks the getters: `getId()`, `getName()`,
   `getStartsAt()`, `getSlug()` — fine. `getVenue()` returns the proxy; the first real
   getter on it demands a select. No session. **Crash — while writing the response.**
5. Spring catches it and sends a generic 500. The real story appears only in the log.

### 4. Simple example

`pom.xml` gains the web starter:

```xml
        <!-- Lesson 10: web layer - embedded Tomcat, controllers, JSON (Jackson). -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
```

`application.yml` gains the project rule:

```yaml
  jpa:
    open-in-view: false     # session ends with the transaction, never held
                            # open for the whole web request (the project rule)
```

The controller — both endpoints wrong on purpose, in two different ways:

```java
@RestController
@Profile("l10-http")
class L10EventController {

    private final EventRepository events;

    L10EventController(EventRepository events) {
        this.events = events;
    }

    // Anti-pattern A: hand the entity itself to the JSON writer.
    @GetMapping("/events/{id}")
    EventEntity byId(@PathVariable UUID id) {
        return events.findById(id).orElseThrow();
    }

    // Anti-pattern B: touch a lazy relationship in the controller.
    @GetMapping("/events/{id}/venue-name")
    String venueName(@PathVariable UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        return event.getVenue().getName();
    }
}
```

Real responses:

```text
$ curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001
HTTP/1.1 500 
{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/events/aaaaaaaa-..."}

$ curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001/venue-name
HTTP/1.1 500 
{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/events/aaaaaaaa-.../venue-name"}
```

Real server log:

```text
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id 
    from event ee1_0 where ee1_0.id=?

WARN  ... Resolved [org.springframework.http.converter.HttpMessageNotWritableException:
   Could not write JSON: Could not initialize proxy
   [com.zenith.rel.VenueEntity#11111111-1111-1111-1111-111111111111] - no session]

ERROR ... Servlet.service() threw exception [Request processing failed:
   org.hibernate.LazyInitializationException: Could not initialize proxy
   [com.zenith.rel.VenueEntity#11111111-...] - no session] with root cause
org.hibernate.LazyInitializationException: Could not initialize proxy ...
	at org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor.intercept(...)
```

### 5. Walkthrough

1. **Endpoint A (entity to JSON).** The event select **ran and succeeded** — then the
   crash. The database work was fine; the failure happened while *writing the response*.
   Hence the wrapper `HttpMessageNotWritableException: Could not write JSON` with the
   familiar `no session` message inside. Jackson got four fields deep and died on
   `getVenue()`'s proxy.
2. **Endpoint B (lazy touch in controller).** The raw `LazyInitializationException` at
   ERROR, top stack frame `ByteBuddyInterceptor.intercept` — the proxy's interception
   code caught failing to load. The touch was our line, after `findById`'s own little
   transaction had closed.
3. **The client learns nothing** — status 500, timestamp, path. The evidence is only on
   the server, in the line naming the exact entity and id touched too late.

### 6. Where is it met?

Every REST API that returns entities. Also explains the classic *"test passes,
production 500s"*: tests often run inside a transaction, so the session is alive when
assertions touch relationships; the real request serializes after it ended.

### 7. Common mistakes

1. **Returning entities from controllers at all.** Even when it works, the JSON leaks
   every column and couples the public API to the table shape. Project shape: service
   maps entities to records, controller returns records (Lesson 12).
2. **"Fixing" serialization with `@Transactional` on the controller method.** Does not
   help: the method returns, the transaction commits, *then* Jackson writes.
   Serialization always runs outside the method.
3. **Flipping `open-in-view` to true.** The crash vanishes and something worse begins
   (Lesson 11).
4. **Reading only the client response.** 500 is a symptom. The diagnosis is the server
   log line naming `Entity#id`.

### 8. Link to the task

**S5 complete** — the exception triggered, read, and understood in plain code and over
HTTP. The config carries the task's required `spring.jpa.open-in-view: false`; the *why*
is S6/checkpoint 3, next lesson. The broken controller stays as an exhibit; Lesson 12
builds the correct replacement.

### 9. Check my understanding

**Q1.** A shop app with `open-in-view: false` has this controller
(`ProductEntity.category` is `@ManyToOne(fetch = FetchType.LAZY)`, the product's
`category_id` is filled):

```java
@RestController
public class ProductController {

    private final ProductRepository products;

    ProductController(ProductRepository products) { this.products = products; }

    @GetMapping("/products/{id}")
    ProductEntity byId(@PathVariable UUID id) {
        return products.findById(id).orElseThrow();   // line 1
    }                                                 // line 2 (method returns)
}
```

A client calls `GET /products/{id}` for an existing product. (a) What HTTP status does
the client get? (b) Does the crash happen at line 1, line 2, or after line 2? (c) Where
does the real error appear — response body or server log?

**Answer.**
- (a) **500.** Jackson hits the lazy `category` proxy with no session.
- (b) **After line 2.** Line 1 succeeds (repository method runs its own small
  transaction). The crash is during JSON writing, which happens after the controller
  method has returned.
- (c) **Server log only** (`Could not write JSON: Could not initialize proxy
  [CategoryEntity#...] - no session`). The client body is the generic
  `{"status":500,...}`.

**Q2.** A teammate says: *"Easy fix — put `@Transactional` on the `byId` controller
method."* The crash does **not** go away. Why not?

**Answer.** `@Transactional` wraps only the method: the transaction starts when `byId`
is entered and commits the moment it returns. Jackson writes the JSON *after* that
return, inside Spring's dispatcher — outside the wrapper. At writing time the session is
exactly as dead as before. The real fix is to finish all touching inside a service
transaction and return a plain record.

### Do it yourself (second laptop)

**Step 1 —** add the web starter to `pom.xml` and `open-in-view: false` under
`spring.jpa:` in `application.yml` (blocks above).

**Step 2 —** create `L10EventController.java` (shown above).

**Step 3 —** start the app and leave it running:

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-http
```

Expected startup now includes: `Tomcat started on port 8080 (http)`.

**Step 4 —** in a **second terminal**:

```bash
curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001
curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001/venue-name
```

Expected: both `HTTP/1.1 500` with `"error":"Internal Server Error"`. First terminal:
the event select, then `Could not write JSON: ... no session` (A) and the ERROR with
root cause `LazyInitializationException` (B).

**Step 5 —** stop the app with `Ctrl+C`.

| You see | It means | Fix |
|---|---|---|
| `404` on both endpoints | Profile missing — controller not active | `-Dspring-boot.run.profiles=l10-http` |
| `curl: (7) Failed to connect` | App not up yet / wrong port | Wait for `Tomcat started` |
| `/venue-name` answers `200 Riverside Arena` | `open-in-view` still on default `true` | Set `spring.jpa.open-in-view: false`, restart |
| `Port 8080 was already in use` | Another app on the port | Stop it, or `-Dspring-boot.run.arguments=--server.port=8081` |

**Done when:** both endpoints 500, and you can point at the server-log line naming the
entity and id — in both costumes (`Could not write JSON ...` and the raw exception).

### Summary

- The web layer runs outside the transaction: controller code and JSON writing have no
  session.
- Repository methods run their own tiny transaction; the entity detaches the moment the
  method returns.
- Two costumes, one disease: `Could not write JSON ... no session` (serializer) and the
  raw `LazyInitializationException` (controller touch). The client sees an anonymous 500.
- `@Transactional` on a controller method cannot save serialization — writing happens
  after the method returns.
- Config now carries the project rule: `spring.jpa.open-in-view: false`.

---

## Lesson 11 — Open Session In View: why hiding the crash is worse (S6, CP3)

### 1. What is it?

**Open Session In View** (OSIV) is a Spring Boot behavior, **on by default**, that opens
a Hibernate session at the start of every web request and keeps it open until the last
byte of the response is written — instead of letting the session live and die with the
transaction. "View" is the old name for the response-writing step.

One sentence: with OSIV the session belongs to the *request*; without it, to the
*transaction*.

### 2. Why do we need to understand it?

It is the single most tempting wrong fix: flip one setting and yesterday's 500 becomes a
friendly 200. Boot itself is uneasy about its own default — it prints a startup warning
urging you to decide. Checkpoint 3 asks exactly why this project refuses it.

### 3. How does it work?

A web interceptor opens the session before the controller runs and closes it after the
response is written. Transactions still begin and end where `@Transactional` says — but
**the session outlives them**, so a proxy touched in the controller or inside Jackson's
JSON writing finds a live session and quietly runs its select.

The four costs (the answer to checkpoint 3):

1. **SQL from anywhere.** Queries fire from controller code and the JSON writer. The
   service stops being the place where database work is complete.
2. **Performance problems go invisible.** An N+1 that would crash honestly now runs
   silently during serialization, where nobody profiles.
3. **The database stays involved until the last byte.** Lazy loads can happen while
   writing to a slow client; under load this stretches pressure on the connection pool.
4. **It silences the smoke alarm.** `LazyInitializationException` carries information:
   *"your service returned incomplete data."* OSIV removes the alarm, not the fire.

### 4. Simple example — the experiment

One print line added to the Lesson 10 controller, so the log can show *where* a select
fires from:

```java
    @GetMapping("/events/{id}/venue-name")
    String venueName(@PathVariable UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        System.out.println(">>> controller: repository call done, its transaction is OVER");
        return event.getVenue().getName();
    }
```

Then, temporarily, in `application.yml`:

```yaml
  jpa:
    # open-in-view: false  # <- experiment: line removed, Boot's default applies
```

Restart. Three real exhibits:

**Exhibit 1 — Boot warns at startup** (absent in all earlier runs, because we had set
the property):

```text
WARN ... JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled
by default. Therefore, database queries may be performed during view rendering.
Explicitly configure spring.jpa.open-in-view to disable this warning
```

**Exhibit 2 — yesterday's crash is "cured":**

```text
$ curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001/venue-name
HTTP/1.1 200 
Riverside Arena
```

The server log shows the smoking gun — read the order:

```text
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id 
    from event ee1_0 where ee1_0.id=?
>>> controller: repository call done, its transaction is OVER
Hibernate: 
    select ve1_0.id, ve1_0.city, ve1_0.name 
    from venue ve1_0 where ve1_0.id=?
```

**Exhibit 3 — the entity-to-JSON endpoint still 500s**, with a new error:

```text
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: No serializer found for
class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor and no properties
discovered to create BeanSerializer ... (through reference chain:
com.zenith.rel.EventEntity["venue"]
  ->com.zenith.rel.VenueEntity$HibernateProxy["hibernateLazyInitializer"])
```

Afterwards the config goes back for good: `open-in-view: false`.

### 5. Walkthrough

1. **Exhibit 2 is the core.** The venue select fired *after* the print line — after the
   repository's transaction committed, from plain controller code. Yesterday that touch
   threw. Nothing in the code changed; only *who owns the session* changed.
2. **The warning says the quiet part out loud:** "database queries may be performed
   during view rendering" — the framework telling you SQL may run while writing
   responses.
3. **Exhibit 3 kills the last excuse.** Even with a request-long session, the entity
   broke Jackson — not for lack of a session, but because the **proxy object itself
   leaked into the JSON writer**. The reference chain: while writing
   `EventEntity["venue"]`, Jackson found a `VenueEntity$HibernateProxy` (the generated
   subclass), looked at its properties, found the proxy's internal machinery
   (`hibernateLazyInitializer`) and could not serialize it. OSIV does not even deliver
   what it promises.

### 6. Where is it met?

OSIV has been Boot's default for years — most tutorials and many production apps rely on
it silently. The same controller code "works" in one project and crashes in another; the
difference is one YAML line. The decision must be made on day one: a mature app with
hundreds of endpoints secretly depending on request-wide sessions cannot easily turn it
off later.

### 7. Common mistakes

1. **Treating OSIV as the fix** — it moves database work into layers that should never
   do database work.
2. **Dismissing the startup warning as noise.** Set the property explicitly — to
   `false` — and the warning disappears because you decided.
3. **Believing OSIV makes "return the entity" safe.** Exhibit 3 disproves it.
4. **Turning it off late in a project.** Decide at the start, as this project does.

### 8. Link to the task

**S6 done.** Checkpoint question 3 answered: **OSIV removes the exception by letting the
session outlive the transaction — which moves SQL into the controller and the JSON
writer, hides incomplete services and N+1 problems where nobody profiles, keeps the
database involved until the last byte of a possibly slow response, and silences the one
error that honestly reports "this service returned incomplete data." The exception is
the smoke alarm; OSIV unplugs the alarm and leaves the fire.**

### 9. Check my understanding

**Q1.** Two teammates run the *same* code against the *same* database:

```java
@RestController
public class ProductController {

    private final ProductRepository products;

    ProductController(ProductRepository products) { this.products = products; }

    @GetMapping("/products/{id}/category-name")
    String categoryName(@PathVariable UUID id) {
        ProductEntity product = products.findById(id).orElseThrow();   // own tiny transaction
        return product.getCategory().getName();   // category is @ManyToOne(fetch = LAZY)
    }
}
```

Laptop A: `200` with the category name. Laptop B: `500` with
`LazyInitializationException ... no session`. No code or data differs. What single
configuration difference explains both results — and which laptop behaves the way this
project requires?

**Answer.** The difference is `spring.jpa.open-in-view`. Laptop A runs Boot's default
(`true`): the request-wide session is still open when the controller touches the lazy
`category`, so the select quietly fires and the endpoint answers 200. Laptop B sets
`open-in-view: false`: the session died with `findById`'s own small transaction, the
proxy has no session, and the touch throws. **Laptop B behaves the way this project
requires** — the honest failure that tells you the controller is doing the service's
job.

**Q2.** Boot's warning says *"database queries may be performed during view rendering."*
Describe the full chain that makes a query run in the middle of JSON writing when OSIV
is on, and give two separate reasons this project refuses that behavior even though it
prevents the crash.

**Answer.** The chain: the controller returns an entity → Spring hands it to Jackson →
Jackson walks every public getter to build the JSON → one getter returns a lazy proxy →
the proxy needs a select to answer → OSIV's request-wide session is still open → the
select fires in the middle of writing the response. Two reasons to refuse (any two):
(1) SQL now runs from the web layer, so the service stops being the boundary where
database work is complete and reviewed; (2) performance problems (like N+1) hide inside
serialization where nobody profiles; (3) the database stays involved until the last byte
reaches a possibly slow client, stretching connection-pool pressure; (4) it silences the
exception that honestly signals "this service returned incomplete data."

### Do it yourself (second laptop)

**Step 1 —** add the print line to `L10EventController.venueName` (shown above).

**Step 2 —** in `application.yml`, comment out `open-in-view: false`.

**Step 3 —** start the app (`mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-http`)
and find the startup warning `spring.jpa.open-in-view is enabled by default...`

**Step 4 —** second terminal:

```bash
curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001/venue-name
```

Expected: `200` and `Riverside Arena`; log order: event select → `transaction is OVER` →
venue select.

**Step 5 —** also:

```bash
curl -i http://localhost:8080/events/aaaaaaaa-0000-0000-0000-000000000001
```

Expected: still `500`; log: `No serializer found for class ...ByteBuddyInterceptor ...
through reference chain: ...EventEntity["venue"]->...VenueEntity$HibernateProxy["hibernateLazyInitializer"]`.

**Step 6 —** stop the app, **uncomment `open-in-view: false`**, restart, re-run step 4:
back to `500` with `no session`. Leave the config this way.

| You see | It means | Fix |
|---|---|---|
| No startup warning in step 3 | Property still set somewhere | Comment out the `open-in-view` line |
| Step 4 gives `500` | Property still `false`, or no restart | Comment out, full restart |
| Step 6 gives `200` | Uncomment didn't take | Check YAML indentation under `jpa:`, restart |

**Done when:** the same endpoint flipped 500 → 200 → 500 by changing only this setting,
and you can answer checkpoint 3 in two spoken sentences.

### Summary

- OSIV (Boot's default) ties the session to the request instead of the transaction;
  proxies then load from anywhere, including inside JSON writing.
- Costs: SQL from the web layer, hidden performance problems, database involvement until
  the last byte, and a silenced smoke alarm.
- It does not even make entity-returning endpoints safe — the proxy class itself breaks
  the serializer.
- Project rule, forever: `spring.jpa.open-in-view: false`. The honest fix is next:
  records built inside the transaction.

---

## Lesson 12 — The project rule: records built inside the transaction (S7)

### 1. What is it?

The honest fix, in one sentence — the project rule from subtask S7: **the
`@Transactional` service method loads entities, touches what the response needs, copies
the data into a record, and returns the record — entities never leave the service.**

Recap: a **record** is Java's compact immutable class —
`record EventSummary(UUID id, String name) {}` gives constructor, accessors, `equals`,
`toString` for free. Plain values only: no proxies, no session, nothing that can run SQL.

### 2. Why do we need it?

One rule kills four problems:

1. **`LazyInitializationException` — gone.** Every lazy touch happens inside the
   transaction, where the session is alive.
2. **The OSIV debate — irrelevant.** Nothing after the service can want a session.
3. **Jackson's proxy problems — gone.** The serializer receives plain values.
4. **API leakage — gone.** The JSON contains the fields you chose, not whatever columns
   the table happens to have.

### 3. How does it work?

Picture a border. **Inside** the `@Transactional` service method is entity-land:
sessions, proxies, SQL — all alive. **Outside** (controller, Jackson, logs) is
plain-data-land. The record is the suitcase packed *before* crossing.

The rule as four steps: **1. Load** the entities. **2. Touch** every relationship the
response needs — lazy loads fire here, legally. **3. Copy** the values into a record.
**4. Return** the record. The transaction commits; nothing lazy crosses the border.

(`readOnly = true` lets Hibernate skip change-tracking work, as in the previous task.)

### 4. Simple example

The suitcase — no `VenueEntity`, just the flattened `venueName`; no `slug`, because this
response doesn't need it:

```java
public record EventSummary(UUID id, String name, OffsetDateTime startsAt, String venueName) {
}
```

The service — all four steps visible:

```java
@Service
public class L12EventService {

    private final EventRepository events;

    L12EventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventSummary eventSummary(UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        String venueName = event.getVenue().getName();   // lazy touch - the session is alive HERE
        System.out.println(">>> service: record built, transaction still open");
        return new EventSummary(event.getId(), event.getName(), event.getStartsAt(), venueName);
    }
}
```

The controller — trivially safe, it never sees an entity:

```java
@RestController
@Profile("l12-records")
class L12EventController {

    private final L12EventService service;

    L12EventController(L12EventService service) {
        this.service = service;
    }

    @GetMapping("/v2/events/{id}")
    EventSummary byId(@PathVariable UUID id) {
        return service.eventSummary(id);
    }
}
```

Real run (with `open-in-view: false` — the same setting that made Lesson 10 crash):

```text
$ curl -i http://localhost:8080/v2/events/aaaaaaaa-0000-0000-0000-000000000001
HTTP/1.1 200 
{"id":"aaaaaaaa-0000-0000-0000-000000000001","name":"Monsoon Rock Night",
 "startsAt":"2026-11-20T13:30:00Z","venueName":"Riverside Arena"}
```

Server log order:

```text
Hibernate: 
    select ee1_0.id, ... from event ee1_0 where ee1_0.id=?
Hibernate: 
    select ve1_0.id, ve1_0.city, ve1_0.name from venue ve1_0 where ve1_0.id=?
>>> service: record built, transaction still open
```

### 5. Walkthrough

1. **Both selects fired before "record built"** — compare Lesson 11, where the venue
   select fired *after* "transaction is OVER". All SQL lives inside the service now.
2. **Jackson received a record** — nothing to initialize, nothing to crash on.
3. **No `slug` in the JSON, `venueName` flat.** The API shape is a decision, not a leak.
4. Two selects for one event is fine. The same pattern on a *list* is the N+1 problem —
   next lesson.

### 6. Where is it used?

The standard architecture of production Spring apps — the "DTO pattern" (data transfer
object), made nearly free by records. Real codebases carry `CustomerSummary`,
`OrderDetails`, `InvoiceLine` per use case: services speak entities inward, records
outward.

### 7. Common mistakes

1. **Mapping in the wrong place.** Service returns the entity, controller builds the
   record — crash, unchanged. The record's *construction* is where lazy touches happen;
   it must sit inside the transaction.
2. **The kitchen-sink record.** Copying every field and relationship "just in case"
   rebuilds the leak and loads data nobody asked for.
3. **One record per table.** Records are per *use case*: `EventSummary` for lists,
   `EventDetails` for the single page.
4. **Forgetting the touch step.** If construction happens inside the transaction, the
   touch rides along; keep steps 2 and 3 together.

### 8. Link to the task

**S7 done:** rule adopted, demonstrated end to end, written into `NOTES.md`. The
Lesson 10 endpoints stay as museum pieces. What remains is performance: a list endpoint
built this way still fires one venue select per event — the N+1 problem, S8, next.

### 9. Check my understanding

**Q1.** A teammate "follows" the rule like this (`open-in-view: false`;
`OrderEntity.customer` is `@ManyToOne(fetch = FetchType.LAZY)`):

```java
@Service
public class OrderService {

    private final OrderRepository orders;

    OrderService(OrderRepository orders) { this.orders = orders; }

    @Transactional(readOnly = true)
    public OrderEntity loadOrder(UUID id) {
        return orders.findById(id).orElseThrow();
    }
}

@RestController
public class OrderController {

    private final OrderService service;

    OrderController(OrderService service) { this.service = service; }

    record OrderView(UUID id, String customerName) {}

    @GetMapping("/orders/{id}")
    OrderView byId(@PathVariable UUID id) {
        OrderEntity order = service.loadOrder(id);
        return new OrderView(order.getId(), order.getCustomer().getName());   // <- here
    }
}
```

There *is* a record, yet the endpoint answers 500 with `LazyInitializationException`.
Why does the record not save him, and what is the minimal fix?

**Answer.** The record is built in the controller — outside any transaction. The lazy
touch is `order.getCustomer().getName()`; at that line the session (born and dead inside
`loadOrder`) is gone, so the proxy throws. A record only helps when its *construction*
— the moment of the touches — runs inside the transaction. Fix: move the mapping into
the service: `@Transactional(readOnly = true) OrderView orderView(UUID id)` that loads,
touches, builds, and returns the record; the controller calls that and never sees the
entity.

**Q2.** The rule has four steps: load, touch, copy, return. Which steps must stay inside
the `@Transactional` method, and what goes wrong for each if it slips outside?

**Answer.** Steps 1–3 must be inside. **Load** outside a transaction still works
(repository methods bring their own tiny one) but then the entity detaches immediately —
setting up the crash. **Touch** outside throws `LazyInitializationException`: lazy loads
need the live session. **Copy** outside crashes the same way whenever the copied value
comes from an untouched relationship — copying *is* touching. **Return** is the border
itself: what crosses must be the record; returning the entity instead is the
anti-pattern that started all of this.

### Do it yourself (second laptop)

Make sure `application.yml` has `open-in-view: false` (end state of Lesson 11).

**Step 1 —** create `EventSummary.java`, `L12EventService.java`,
`L12EventController.java` (shown above).

**Step 2 —** start the app:

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l12-records
```

**Step 3 —** second terminal:

```bash
curl -i http://localhost:8080/v2/events/aaaaaaaa-0000-0000-0000-000000000001
```

Expected: `200` and
`{"id":"aaaaaaaa-...","name":"Monsoon Rock Night","startsAt":"2026-11-20T13:30:00Z","venueName":"Riverside Arena"}`
— no `slug`, no nested venue object.

**Step 4 —** confirm the log order: event select, venue select, *then*
`>>> service: record built, transaction still open`.

**Step 5 —** prove the rule matters: move the venue touch and `new EventSummary(...)`
into the controller (as in Q1), restart, curl — `500`, `no session`. Move them back.

| You see | It means | Fix |
|---|---|---|
| `404` | Profile missing | `-Dspring-boot.run.profiles=l12-records` |
| `500` with `no session` | Venue touch outside the service | Touch and map inside `eventSummary(...)` |
| Nested `venue` object in JSON | Record holds the entity | Field must be `String venueName` |

**Done when:** step 3 answers 200 with the four-field JSON, step 5's broken variant
answers 500, and you can recite the four steps and the border they respect.

### Summary

- Project rule: load, touch, copy, return — all inside the `@Transactional` service
  method; only records cross the border.
- Where the record is **built** is everything; a record constructed in the controller
  saves nothing.
- Records flatten and select: the API shape is chosen, not leaked.
- Correctness is solved; performance is not — a list built this way fires one select per
  row: the N+1 problem, next.

---

## Lesson 13 — The N+1 problem: twenty events, twenty-one statements (S8, CP4 part 1)

### 1. What is it?

The **N+1 problem**: code that needs a list of rows *plus* one relationship per row fires
**1 query for the list and then N more — one per row**. Twenty events → 1 select for the
events + 20 selects for their venues = **21 statements** where one would do.

The name reads in query order: first the **1** (the list), then the **N** (one per row).
Nothing crashes. The results are correct. Only the *count* of queries is wrong — which is
why it survives code review.

### 2. Why do we need to understand it?

The most common database performance bug in real apps, with a cruel property: **invisible
while the data is small.** Five rows in dev = 6 statements = feels instant. Twenty
thousand rows in production = 20,001 statements = seconds per page. The code never
changed; the data grew. Checkpoint 4 asks exactly this.

### 3. How does it work?

1. The list query loads **event rows only**. Each `venue` field gets a **proxy** knowing
   just the venue's id (from the event row's `venue_id`), loading on first real touch.
2. The loop builds one record per event. `event.getVenue().getName()` is the first real
   call on *that event's* proxy → that proxy initializes → **one select**.
3. Twenty events with twenty *different* venues → twenty selects. Total 1 + 20 = 21.

Two sharpening details:

- **The persistence context can mask it.** If two events share a venue, the second
  `getVenue()` finds it already in the session's notebook — no second select. The
  original seven events share three venues (a puny 1 + 3). So the demo seeds twenty
  events with **their own venue each** — real catalogs are mostly distinct, and the demo
  must show the true scale.
- **`EAGER` would not fix it.** Eager changes *when* the relationship loads, not *how
  many* queries. With `EAGER` the same per-venue selects fire during the list load,
  before any loop, and you can never opt out. The fix changes the *query*, not the fetch
  type.

Why the database hates it: 21 statements = 21 network round trips, each costing latency
even when trivial. One query returning 20 joined rows does it in one trip.

### 4. Simple example

Twenty events each with their own venue (`generate_series(1, 20)` produces the numbers
1–20, so each insert runs twenty times) — now in `seed-dev-data.sql`:

```sql
INSERT INTO public.venue (id, name, city, capacity, created_at)
SELECT ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Venue ' || lpad(n::text, 2, '0'), 'Pune', 500, now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.event (id, venue_id, name, starts_at, slug, created_at)
SELECT ('a1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Event ' || lpad(n::text, 2, '0'),
       timestamptz '2027-09-01 18:00:00+00' + (n || ' days')::interval,
       'load-event-' || lpad(n::text, 2, '0'), now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;
```

A derived query to pick exactly those twenty:

```java
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 13: derived query - "where name like ? || '%' order by name".
    List<EventEntity> findByNameStartingWithOrderByNameAsc(String prefix);
}
```

The demo — follows the Lesson 12 rule perfectly (transaction, touches inside, records
out) and still fires 21:

```java
@Component
@Profile("l13-n-plus-1")
class L13NPlusOneDemo implements CommandLineRunner {

    private final EventRepository events;

    L13NPlusOneDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        System.out.println(">>> loading the demo events (watch: 1 select) ...");
        List<EventEntity> demo = events.findByNameStartingWithOrderByNameAsc("Load Event ");
        System.out.println(">>> got " + demo.size() + " events. now their venue names ...");

        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // lazy touch, once per event
        }

        System.out.println(">>> statements: 1 (event list) + " + summaries.size() + " (one per venue)");
    }
}
```

Real output (the venue select repeats twenty times):

```text
>>> loading the demo events (watch: 1 select) ...
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at, ee1_0.venue_id 
    from event ee1_0 where ee1_0.name like ? escape '\' order by ee1_0.name
>>> got 20 events. now their venue names ...
Hibernate: 
    select ve1_0.id, ve1_0.city, ve1_0.name from venue ve1_0 where ve1_0.id=?
Hibernate: 
    select ve1_0.id, ve1_0.city, ve1_0.name from venue ve1_0 where ve1_0.id=?
          ... the same select, eighteen more times ...
>>> statements: 1 (event list) + 20 (one per venue)
```

Counted by machine:

```text
$ grep -c "Hibernate:" /tmp/l13.log
21
```

### 5. Walkthrough

1. **The 1:** the list select — Spring Data turned the method name into
   `where name like ? order by name`. Event columns only; every `venue` is a proxy.
2. **The N:** twenty venue selects, *byte-for-byte identical* but for the bound id. That
   sameness is the fingerprint of an N+1 in any log.
3. Each fired at `event.getVenue().getName()` — once per loop pass, each proxy's first
   real method call.
4. **The code is correct** — transaction, mapping inside, records out, right data. N+1
   is a bug in cost, not in result.
5. `grep -c "Hibernate:"` → **21**, now written in `NOTES.md` (S8).

### 6. Where is it met?

Any list endpoint mapping entities with relationships: orders+customers, posts+authors,
tickets+events. Dashboards and admin grids are the classic victims. The war story is
always *"fine for months, then a big client onboarded and the page takes eight seconds"*
— rows grew, statements grew with them.

### 7. Common mistakes

1. **Never seeing it** — `show-sql` off and three dev rows hide it. The scratch app keeps
   `show-sql: true` all task and the demo uses twenty rows.
2. **"Fixing" with `EAGER`** — same N selects, now on every load everywhere, no opt-out.
3. **Reaching for a cache** — hides the symptom until it expires, then the stampede
   returns. Fix the query first.
4. **Hoping Hibernate batches automatically** — by default it does not.

### 8. Link to the task

**S8 done:** N+1 produced, statements counted (21), number written into `NOTES.md`.
Checkpoint 4 first half answered: **an N+1 — one list query plus one lazy-relationship
query per row.** The fix is the next two lessons: `join fetch` and `@EntityGraph`, each
collapsing 21 → 1.

### 9. Check my understanding

**Q1.** A library app lists books (all authors different):

```java
@Transactional(readOnly = true)
public List<BookView> allBooks() {
    List<BookEntity> books = bookRepository.findAll();           // 50 books
    List<BookView> views = new ArrayList<>();
    for (BookEntity book : books) {
        views.add(new BookView(book.getId(), book.getTitle(),
                book.getAuthor().getName()));    // author is @ManyToOne(fetch = LAZY)
    }
    return views;
}
```

(a) How many SQL statements fire for 50 books? (b) For 500? (c) The developer tested with
5 books and saw no problem — how many did they see, and why did it feel fine?

**Answer.**
- (a) **51** — 1 for the book list + 50 author selects (one per book, authors distinct).
- (b) **501** — 1 + 500. Statements grow one-for-one with rows.
- (c) **6** — 1 + 5. Six fast single-row selects on a tiny dev database feel instant;
  nothing fails, nothing is slow, so the bug hides until the row count grows in
  production.

**Q2.** Suppose Load Event 19 and Load Event 20 both pointed at **Load Venue 19**
(nineteen distinct venues for twenty events). How many statements, and which mechanism
saves the missing select?

**Answer.** **20** statements (1 list + 19 venue selects). When the loop reaches the
second event sharing Load Venue 19, `getVenue()` finds that venue already loaded in the
**persistence context** (the session's notebook of rows seen this transaction), so it
returns the cached instance instead of firing another select. This is also why sharing
hides the problem's true size — and why the demo uses distinct venues.

### Do it yourself (second laptop)

**Step 1 — seed** (also at the end of `seed-dev-data.sql`):

```bash
docker exec -i pg psql -U postgres -d zenith <<'SQL'
INSERT INTO public.venue (id, name, city, capacity, created_at)
SELECT ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Venue ' || lpad(n::text, 2, '0'), 'Pune', 500, now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;
INSERT INTO public.event (id, venue_id, name, starts_at, slug, created_at)
SELECT ('a1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       ('b1000000-0000-0000-0000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'Load Event ' || lpad(n::text, 2, '0'),
       timestamptz '2027-09-01 18:00:00+00' + (n || ' days')::interval,
       'load-event-' || lpad(n::text, 2, '0'), now()
FROM generate_series(1, 20) n
ON CONFLICT (id) DO NOTHING;
SQL
```

Expected: `INSERT 0 20` twice (`INSERT 0 0` on a re-run is fine).

**Step 2 —** add `findByNameStartingWithOrderByNameAsc` to `EventRepository` and create
`L13NPlusOneDemo.java` (shown above).

**Step 3 — run with a log file:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l13-n-plus-1 | tee /tmp/l13.log
```

The app no longer exits on its own (it carries a web server since Lesson 10) — press
`Ctrl+C` once the demo lines printed.

**Step 4 — count by machine:**

```bash
grep -c "Hibernate:" /tmp/l13.log
```

Expected: `21`.

**Step 5 —** write the number and one-line explanation into `NOTES.md`.

| You see | It means | Fix |
|---|---|---|
| `>>> got 0 events` | Seed not loaded | Run step 1, check `INSERT 0 20` |
| Count is small (`4`) | Querying old events (3 shared venues) | Prefix must be exactly `"Load Event "` |
| Count is `42`+ | Log has two runs appended | Re-run step 3 (fresh `tee` overwrites) |
| App hangs after the demo | Normal — Tomcat serving | `Ctrl+C` |

**Done when:** `grep -c` says 21, the number is in `NOTES.md`, and you can say what fires
the twenty extra selects: the first real touch of each row's lazy proxy, one per row.

### Summary

- N+1 = 1 list query + N per-row relationship queries. 20 events → 21 statements.
- Invisible on small data, brutal at scale; the code is correct, only the query count is
  wrong.
- The fingerprint in a log: the same single-row select repeated once per row.
- `EAGER` does not fix it (changes when, not how many); sharing a relationship hides its
  true size. The real fix changes the query — next.

---

## Lesson 14 — Fix one: `join fetch` in a `@Query` (S9 part 1, CP4 part 2)

### 1. What is it?

`join fetch` is a phrase added to a JPQL query that tells Hibernate: **"while you load
these events, load each event's venue in the same select."** The first of the task's two
N+1 cures — it turns yesterday's 21 statements into exactly **1**.

Two words, two jobs. Plain `join` links the two tables so you can filter or sort by the
joined one. The extra word **`fetch`** says *also put the joined rows into the objects* —
fill each event's `venue` field now, from this one query. Without `fetch` the join
happens but the venue field stays a lazy proxy; with `fetch` the field arrives loaded.

### 2. Why do we need it?

The N+1 is a query-shape problem, so only a query-shape change fixes it. Lesson 13 showed
the code was already correct and that `EAGER` cannot help (it changes *when*, not *how
many*). The lever that moves the statement count is the query itself: ask the database to
do the join once instead of stitching it together with N follow-up selects.

### 3. How does it work?

Recap: **JPQL** (Java Persistence Query Language) is the query language inside `@Query`.
It looks like SQL but speaks in *entities and fields*, not tables and columns: you write
`EventEntity e` and `e.venue`, and Hibernate translates to SQL against `event` and
`venue`.

Step by step:

1. You write `select e from EventEntity e join fetch e.venue`. `e.venue` is the Lesson 1
   relationship field — Hibernate knows it maps to `event.venue_id → venue.id`.
2. Hibernate emits **one** SQL select joining `event` to `venue`, with **both tables'
   columns** in the result: `ee1_0.id, …, v1_0.id, v1_0.city, v1_0.name`.
3. For each row it builds the `EventEntity` *and* the `VenueEntity` and sets the real
   object into the `venue` field — **no proxy**, because there is nothing to defer.
4. The loop calls `getVenue().getName()` twenty times, all answered from memory. Zero
   extra selects.

The field's fetch type (`LAZY`) is untouched and still correct everywhere else; this one
query overrides it for this one call site. **Lazy by default, eager on demand.**

### 4. Simple example

The query, next to yesterday's derived query:

```java
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    List<EventEntity> findByNameStartingWithOrderByNameAsc(String prefix);

    // Lesson 14: the N+1 fix - one query that also brings each event's venue.
    @Query("select e from EventEntity e join fetch e.venue "
            + "where e.name like concat(:prefix, '%') order by e.name")
    List<EventEntity> findWithVenueByNamePrefix(@Param("prefix") String prefix);
}
```

The demo is Lesson 13's with one line changed — the method it calls:

```java
@Component
@Profile("l14-join-fetch")
class L14JoinFetchDemo implements CommandLineRunner {

    private final EventRepository events;

    L14JoinFetchDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        List<EventEntity> demo = events.findWithVenueByNamePrefix("Load Event ");
        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // venue already loaded: free
        }
        System.out.println(">>> statements expected: 1 (list already carries the venues)");
    }
}
```

Real output — same twenty events, same records, one statement:

```text
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at,
           v1_0.id, v1_0.city, v1_0.name 
    from event ee1_0 
    join venue v1_0 on v1_0.id=ee1_0.venue_id 
    where ee1_0.name like (?||'%') escape '' 
    order by ee1_0.name
>>> got 20 events. now their venue names (watch: NO new selects) ...
>>> EventSummary[id=a1000000-..., name=Load Event 01, startsAt=2027-09-02T18:00Z, venueName=Load Venue 01]
```

Counted by machine:

```text
$ grep -c "Hibernate:" /tmp/l14.log
1
```

### 5. Walkthrough

Lesson 13 and Lesson 14 side by side — same twenty rows, same output records:

| | Lesson 13 (derived query) | Lesson 14 (`join fetch`) |
|---|---|---|
| SQL statements | **21** | **1** |
| The select | event columns only | event columns **+ venue columns** |
| `venue` field after load | proxy | real `VenueEntity` |
| `getVenue().getName()` in loop | fires a select each time | free, from memory |

1. **One select, two tables.** The log shows `join venue v1_0 on v1_0.id=ee1_0.venue_id`
   and both column sets in one result. The database matched them, once.
2. **No proxy was ever made** — the venue data arrived with the event, so the real object
   went straight into the field.
3. **Output identical to Lesson 13** — same first summary, same twenty records. Only the
   round-trip count changed. That is the signature of a proper N+1 fix: same data, fewer
   statements.
4. **`where` and `order by` still work** — `join fetch` just rides along, bringing venue
   columns with each matched event.

### 6. Where is it used?

Everywhere a list needs its relationships: `select o from Order o join fetch o.customer`,
`select p from Post p join fetch p.author`. The most direct, most explicit N+1 fix — you
read the join in the query and know exactly what one statement loads.

### 7. Common mistakes

1. **Forgetting the word `fetch`.** `join e.venue` joins for filtering but leaves the
   field a proxy — the N+1 returns. The fix *is* the word `fetch`.
2. **`join fetch` on a to-many with paging.** Fetching a *collection* with `Pageable`
   makes Hibernate pull every row and paginate in memory (a log warning, a real hazard).
   Single-valued like `event.venue` is safe; collections need care (often
   `@EntityGraph`, next).
3. **Fetching two separate collections at once** — multiplies their rows (a Cartesian
   product). One collection per query.
4. **Assuming it changes the field's fetch type.** It does not — `venue` stays `LAZY`
   elsewhere. `join fetch` is per-query eager, exactly what you want.

### 8. Link to the task

**S9 half done:** N+1 fixed once with `join fetch`, count verified down to **1**.
Checkpoint 4's second half gets its first answer: **load the relationship in the same
query with `join fetch`.** Next lesson fixes the same N+1 a second way — `@EntityGraph`
without JPQL — plus the one-sentence diagnostic (S10).

### 9. Check my understanding

**Q1.** A blog lists posts, each with one author (`Post.author` is
`@ManyToOne(fetch = FetchType.LAZY)`):

```java
// (A)
@Query("select p from PostEntity p join p.author where p.published = true")
List<PostEntity> findPublishedA();

// (B)
@Query("select p from PostEntity p join fetch p.author where p.published = true")
List<PostEntity> findPublishedB();
```

The service loops over the result and builds
`new PostView(p.getId(), p.getTitle(), p.getAuthor().getName())` for each of 30 published
posts (all authors different). For **each** method, how many SQL statements fire, and why
the difference when the queries look almost identical?

**Answer.**
- **(A) 31 statements.** Plain `join` links the tables for the `where`, but without
  `fetch` the `author` field stays a lazy proxy. The list is 1 select; then each of the
  30 `getAuthor().getName()` calls fires its own select. 1 + 30 = 31.
- **(B) 1 statement.** `join fetch` loads each author's columns in the same select, so
  the `author` field arrives as a real object. The 30 loop touches are free. 1 + 0 = 1.
- **The difference is the single word `fetch`:** `join` controls *what you can filter
  by*; `join fetch` additionally controls *what gets loaded into the objects*.

**Q2.** A teammate says: *"Great, now let's also change `venue` to
`@ManyToOne(fetch = FetchType.EAGER)` to be safe."* What would that cost across the app,
and why does `join fetch` on the one query already give the benefit without the cost?

**Answer.** `EAGER` on the field is global and permanent: *every* query that loads an
event anywhere — `findById`, every derived query, every other `@Query` — would now also
load the venue, whether that code path needs it or not, with no way to opt out for the
cases that don't. It trades a targeted fix for app-wide forced loading (and the venue's
own eager links would ride along too). `join fetch` keeps the field `LAZY` for everyone
else and loads the venue up front *only* for the one query that needs it — the benefit
exactly where wanted, nowhere else.

### Do it yourself (second laptop)

Needs the twenty `Load Event` rows from Lesson 13.

**Step 1 —** add `findWithVenueByNamePrefix` to `EventRepository` (import
`org.springframework.data.jpa.repository.Query` and
`org.springframework.data.repository.query.Param`) and create `L14JoinFetchDemo.java`
(both above).

**Step 2 — run with a log file:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l14-join-fetch | tee /tmp/l14.log
```

`Ctrl+C` after the `>>> statements expected: 1` line.

**Step 3 — count:**

```bash
grep -c "Hibernate:" /tmp/l14.log
```

Expected: `1`. The single statement must contain `join venue` and both `ee1_0.*` and
`v1_0.*` columns.

**Step 4 — the contrast:**

```bash
echo "L13 (N+1):        $(grep -c 'Hibernate:' /tmp/l13.log) statements"
echo "L14 (join fetch): $(grep -c 'Hibernate:' /tmp/l14.log) statement"
```

Expected: `21` then `1`.

| You see | It means | Fix |
|---|---|---|
| Count is `21` again | Query missing the word `fetch` | Must be `join fetch e.venue` |
| `got 0 events` | Seed missing / wrong prefix | Re-run Lesson 13 seed; prefix `"Load Event "` |
| Startup error mentioning `e.venue` | JPQL field name wrong | It is `venue` on `EventEntity` |
| `QuerySyntaxException` | Typo in the JPQL | Compare keyword by keyword |

**Done when:** `grep -c` says `1`, the single select shows a `join venue` with venue
columns, and you can say why the loop's twenty `getVenue()` calls fired zero new selects.

### Summary

- `join fetch` loads the relationship in the same select — the field arrives as a real
  object, no proxy, no per-row query. 21 → 1.
- `join` controls filtering/sorting; the extra word `fetch` controls what gets loaded.
- It is per-query eager: the field stays `LAZY` everywhere else. Lazy by default, eager
  on demand.
- Same data, fewer statements — the signature of a correct N+1 fix.

---

## Lesson 15 — Fix two: `@EntityGraph`, and the one-sentence diagnostic (S9 done, S10, CP4 done)

### 1. What is it?

`@EntityGraph` is an annotation above a repository method that says: *"when you run this
query, also load these relationships in the same trip."* The second N+1 cure — same
result as `join fetch`, 21 → **1** — **without writing any JPQL.** You keep a plain
derived query and just annotate it.

This lesson also hands over subtask S10: the single sentence that lets you *spot* an N+1
in any log, any project.

### 2. Why a second way?

Choice: sometimes you have a derived query you like and don't want to rewrite it as a
JPQL string — `@EntityGraph` bolts the fetch on and leaves the method name intact.
Understanding: `join fetch` and `@EntityGraph` are two doors to the same room, proving
the real point — **an N+1 is fixed by changing the query to load the relationship
eagerly, and there's more than one way to say that.**

### 3. How does it work?

Recap of the two query styles: a **derived query** is a method whose *name* is the query
(`findByNameStartingWith…` → `where name like ?`); a **`@Query`** carries a hand-written
JPQL string.

`@EntityGraph` works on the derived kind:

1. Write a normal derived-query method — the name does filtering and sorting.
2. Add `@EntityGraph(attributePaths = "venue")` above it. `attributePaths` lists the
   relationship fields to load eagerly — here, `venue`.
3. At query time Spring Data builds the derived query **and** tells Hibernate to fetch
   `venue` in the same select — Hibernate adds the join and the venue columns.
4. Each `venue` arrives as a real `VenueEntity`, not a proxy. The loop's touches are free.

Naming detail: the method is `findWithGraphByNameStartingWithOrderByNameAsc`. Spring Data
reads the name only **from the first `By` onward**; anything between `find` and `By` is a
free-text label it ignores. So `WithGraph` is a human note that keeps the name distinct
from Lesson 13's while parsing to the identical query.

### 4. Simple example

Repository method — a plain derived query with one annotation, no JPQL:

```java
    // Lesson 15: the SAME fix without writing JPQL.
    @EntityGraph(attributePaths = "venue")
    List<EventEntity> findWithGraphByNameStartingWithOrderByNameAsc(String prefix);
```

Demo — identical to Lesson 14 except the method it calls:

```java
@Component
@Profile("l15-entity-graph")
class L15EntityGraphDemo implements CommandLineRunner {

    private final EventRepository events;

    L15EntityGraphDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        List<EventEntity> demo = events.findWithGraphByNameStartingWithOrderByNameAsc("Load Event ");
        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // venue already loaded: free
        }
        System.out.println(">>> statements expected: 1 (the graph pulled the venues along)");
    }
}
```

Real output — one statement, and note the join kind:

```text
Hibernate: 
    select ee1_0.id, ee1_0.name, ee1_0.slug, ee1_0.starts_at,
           v1_0.id, v1_0.city, v1_0.name 
    from event ee1_0 
    left join venue v1_0 on v1_0.id=ee1_0.venue_id 
    where ee1_0.name like ? escape '\' 
    order by ee1_0.name
```

```text
$ grep -c "Hibernate:" /tmp/l15.log
1
```

### 5. Walkthrough

All three lessons, same twenty rows, same records out:

| | Lesson 13 | Lesson 14 (`join fetch`) | Lesson 15 (`@EntityGraph`) |
|---|---|---|---|
| Statements | 21 | 1 | 1 |
| How the fetch is written | — | JPQL string | annotation on a derived query |
| Join in the SQL | — | inner `join` | **`left join`** |

1. **One statement, same as `join fetch`** — venue columns ride in the single select,
   the loop touches cost nothing. No JPQL typed.
2. **The one real difference: `left join` vs `join`.** `join fetch` made an **inner**
   join (would silently *drop* events with a null `venue_id`). `@EntityGraph` made a
   **left (outer)** join (keeps every event, `null` where no match). For our data every
   event has a venue, so results match — but it's a genuine behavior difference.
3. **The field stayed `LAZY`** — per-query eager, like `join fetch`. Two tools, same
   promise: lazy by default, eager where asked.

The diagnostic (subtask S10), now in `NOTES.md`:

> **If the number of SQL statements grows with the number of rows returned, it is an
> N+1 — fix it by loading the relationship in the same query (`join fetch` or
> `@EntityGraph`).**

You don't need to recognize this exact code — watch the log as data grows. Statements
flat as rows climb = healthy. Statements climbing one-for-one with rows = N+1.

### 6. Where is it used?

`@EntityGraph` is the common choice because it leaves derived queries readable — annotate
`findByStatus`, `findAllByCustomerId` without converting them to JPQL. `join fetch` is
reached for when the query is already custom JPQL or needs join logic the annotation
can't express. Most projects use both, per method.

### 7. Common mistakes

1. **Expecting `@EntityGraph`'s `left join` to filter like an inner join.** To drop rows
   with a missing relationship, add `where … is not null`, or use `join fetch`. Usually
   the outer join (keep all primary rows) is what a fetch wants, which is the default.
2. **Misspelling the attribute path.** `attributePaths = "venues"` fails at startup — it
   must be the exact field name (`venue`). Caught early, mercifully.
3. **Graphing a collection with `Pageable`** — same in-memory pagination trap as
   `join fetch` on a to-many.
4. **Thinking one annotation fixes every N+1 in the class.** It fixes the method it sits
   on; each N+1 query needs its own fix. No global switch (that would be `EAGER`'s
   mistake).

### 8. Link to the task

**S9 complete** — N+1 fixed two ways, both verified to 1 statement. **S10 complete** —
the diagnostic sentence is in `NOTES.md` with a 21 → 1 → 1 table. **Checkpoint 4 fully
answered:** twenty-one statements for twenty rows is an N+1 (one list query + one
lazy-relationship query per row); the fix is to load the relationship in the same query
with `join fetch` or `@EntityGraph`. The performance arc (S8–S10) is closed. Next arc:
entity hygiene — why `@Data` is dangerous (S11, S13) and the rules that replace it (S12).

### 9. Check my understanding

**Q1.** Two methods both aim to fix the same N+1 (loading each order's customer):

```java
// (A)
@Query("select o from OrderEntity o join fetch o.customer where o.status = :status")
List<OrderEntity> findByStatusA(@Param("status") String status);

// (B)
@EntityGraph(attributePaths = "customer")
List<OrderEntity> findByStatus(String status);
```

Some orders are *guest* orders with `customer_id` NULL. With `status = "PAID"`: 40 paid
orders, 4 of them guest. (a) Which method silently returns only 36, which returns all 40?
(b) How many statements each? (c) For "show all paid orders, guest or not", which is
correct as written?

**Answer.**
- (a) **(A) returns 36; (B) returns 40.** `join fetch` is an inner join — it drops the 4
  orders whose `customer_id` is NULL. `@EntityGraph` uses a left outer join — it keeps
  them, with a null customer.
- (b) **Both fire 1 statement.** Both load the customer in the same select; neither has
  an N+1.
- (c) **(B)** is correct as written. To make (A) correct you'd write
  `left join fetch o.customer`.

**Q2.** A teammate profiles a page: *"With 10 items I see 11 queries; with 50 items I see
51 queries."* Name the problem, give the two-tool fix, and state the rule that let you
diagnose it from the numbers alone.

**Answer.** It's an **N+1**: 1 list query + 1 per-row relationship query (11 = 1 + 10,
51 = 1 + 50). Fix by loading the relationship in the same query — **`join fetch`** in a
`@Query`, or **`@EntityGraph(attributePaths = …)`** on a derived query. The rule:
**statements growing one-for-one with rows means an N+1** — which is exactly the shape
10→11, 50→51.

### Do it yourself (second laptop)

Needs the twenty `Load Event` rows from Lesson 13.

**Step 1 —** add the `@EntityGraph` method to `EventRepository` (import
`org.springframework.data.jpa.repository.EntityGraph`) and create
`L15EntityGraphDemo.java` (both above).

**Step 2 — run with a log file:**

```bash
cd scratch-app
mvn -q spring-boot:run -Dspring-boot.run.profiles=l15-entity-graph | tee /tmp/l15.log
```

`Ctrl+C` after the `>>> statements expected: 1` line.

**Step 3 — count and inspect the join:**

```bash
grep -c "Hibernate:" /tmp/l15.log
```

Expected: `1`. The statement says `left join venue` (outer) — versus Lesson 14's plain
`join venue` (inner).

**Step 4 — all three runs:**

```bash
for f in l13 l14 l15; do echo "$f: $(grep -c 'Hibernate:' /tmp/$f.log) statement(s)"; done
```

Expected: `l13: 21`, `l14: 1`, `l15: 1`.

**Step 5 —** confirm `NOTES.md` has the diagnostic sentence (S10).

| You see | It means | Fix |
|---|---|---|
| Startup error `Could not locate attribute 'venues'` | Wrong field name | Must be `"venue"` |
| Count is `21` | Annotation not on the method you call | `@EntityGraph` directly above `findWithGraphBy…` |
| `got 0 events` | Seed missing / wrong prefix | Re-run Lesson 13 seed; prefix `"Load Event "` |
| Two methods clash at startup | Reused Lesson 13's exact name | Keep the distinct `findWithGraphBy…` name |

**Done when:** `l15` shows `1`, you can point out the `left join` in its SQL, and you can
recite the diagnostic sentence.

### Summary

- `@EntityGraph(attributePaths = …)` loads the named relationship in the same query — the
  N+1 fix without JPQL, on a plain derived query. 21 → 1.
- It emits a `left` (outer) join; `join fetch` emits an inner join — a real difference
  when the relationship can be null.
- Per-query eager, like `join fetch`; the field stays `LAZY` elsewhere; each N+1 needs
  its own fix.
- The diagnostic: statements growing with rows = N+1. That is the portable skill.

---

## Lesson 16 — `@Data` danger one: `toString()` touches lazy fields, and can recurse (S11 part 1)

### 1. What is it?

Lombok's `@Data` is one annotation that auto-generates, at compile time, a getter and
setter for every field plus `toString()`, `equals()`, `hashCode()` — all from **every
field**. Wonderful on a small plain data holder; a landmine on a JPA entity. This lesson
is the first of three dangers (S11): its generated **`toString()`**.

Two disasters from that one method:
- **It reads your lazy relationships** — merely *logging* an entity secretly runs
  queries, or throws `LazyInitializationException`.
- **It recurses on a two-way relationship** — parent prints children, each child prints
  the parent, forever, until `StackOverflowError`.

### 2. Why do we need to understand it?

`@Data` on entities is everywhere in tutorials — tidy, saves typing. The damage is
invisible until runtime: a harmless `log.info("saving " + event)` fires surprise queries
in a loop, or brings the request down. You cannot avoid a trap you have not seen spring,
so we add Lombok and watch it spring.

### 3. How does it work?

1. **`@Data` builds `toString()` from all fields** — for `BadEventEntity` that includes
   the relationship `venue`.
2. **Reading a lazy relationship is a database touch.** `toString()` doing
   `"venue=" + venue` is that real read: inside a transaction it quietly fires an extra
   query; outside one it throws `LazyInitializationException`.
3. **Two-way relationships make `toString()` call itself.** A `BadSeatMapEntity` lists
   `seats`; each `BadSeatEntity` holds its `seatMap`. Printing the map prints the seats;
   printing a seat prints its map; forever — until `StackOverflowError`.

### 4. Simple example

The deliberately-wrong entity:

```java
@Data
@Entity
@Table(name = "event")
public class BadEventEntity {
    @Id private UUID id;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private VenueEntity venue;
}
```

The two-way pair (`BadSeatMapEntity.seats` ↔ `BadSeatEntity.seatMap`), both `@Data`. The
demo only *logs* them:

```java
// FACE 1: toString() touches a lazy field -> a surprise query, just from logging.
BadEventEntity event = badEvents.findById(MONSOON).orElseThrow();
System.out.println(">>> " + event);   // Lombok toString reads the lazy venue

// FACE 2: bidirectional toString() recurses -> StackOverflowError.
BadSeatMapEntity map = badSeatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
map.getSeats().size();   // force the seats to load
try {
    System.out.println(map.toString());
} catch (StackOverflowError e) {
    System.out.println(">>> StackOverflowError caught");
}
```

Real output:

```text
>>> load one BadEventEntity (watch: 1 select) ...
Hibernate:  select ... from event be1_0 where be1_0.id=?
>>> now we only LOG it - watch a SECOND select appear ...
Hibernate:  select ve1_0.id, ve1_0.city, ve1_0.name from venue ve1_0 where ve1_0.id=?
>>> BadEventEntity(id=aaaaaaaa-..., name=Monsoon Rock Night, venue=com.zenith.rel.VenueEntity@7e09bee)
...
>>> now we only LOG the seat map - watch it blow the stack ...
>>> StackOverflowError caught: toString went
>>>   seatMap -> seats -> seat -> seatMap -> seats -> ... forever
```

### 5. Walkthrough

**FACE 1 — logging ran a query.** `findById` fired one select on `event`. The only next
thing was `">>> " + event` — a log line — yet a **second select fired, on `venue`**. No
`getVenue()` anywhere; `+ event` invoked Lombok's `toString()`, which read the lazy
`venue`. The printed `venue=...VenueEntity@...` proves it. In a list of 100 events that
is 100 surprise selects from one logging statement. The same line outside a transaction
would throw `LazyInitializationException` instead.

**FACE 2 — logging killed the program.** `map.toString()` printed `id`, `name`, then the
`seats` list; printing seat A1 printed its `seatMap`, which restarted the map's
`toString()`… the stack filled and the JVM threw **`StackOverflowError`** (caught here
only to keep running; in a web app it crashes the request).

### 6. Where is it met?

Anywhere an entity is logged or debugged: `log.info("processing {}", order)`, a debug
`println`, an exception message including the entity, even the IDE debugger rendering
`toString()`. The recursion version is one of the most-reported Lombok+JPA bugs, because
two-way relationships are so common.

### 7. Common mistakes

1. **`@Data` on an entity "to save boilerplate"** — it silently generates the dangerous
   `toString()` (and `equals`/`hashCode`, next).
2. **Believing lazy fields make logging safe** — lazy only defers; `toString()` *is* a
   load trigger.
3. **Plain `@ToString` without excludes** — same two faults; you must exclude
   relationships.
4. **Testing with relationships unset** — a `new`-ed entity has null relationships, so
   `toString()` looks harmless until production.

### 8. Link to the task

Danger one of three (S11): **`@Data`'s `toString()` reads lazy relationships (hidden
query, or `LazyInitializationException`) and recurses on two-way links
(`StackOverflowError`).** Next: danger two, the `equals`/`hashCode`, and S13's
before-and-after-save id story.

### 9. Check my understanding

**Q1.** A real service ships this entity:

```java
@Data
@Entity
@Table(name = "invoice")
public class InvoiceEntity {
    @Id private UUID id;
    private String number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;
}
```

Inside a `@Transactional` method, right after loading 50 invoices:
`invoices.forEach(inv -> log.info("loaded {}", inv));`
(a) How many SQL statements does that one log line cause, and why? (b) The same line in a
**non**-`@Transactional` method with a detached invoice — what happens? (c) No
`getCustomer()` is called anywhere — what triggers the customer access?

**Answer.**
- (a) **50 selects** — one per invoice. `log.info("loaded {}", inv)` builds the message
  by calling `inv.toString()`, Lombok's version reads the lazy `customer`, and each read
  initializes that invoice's customer proxy with its own select (customers distinct).
- (b) **`LazyInitializationException`** on the first invoice logged: the entity is
  detached, its customer proxy has no session, and `toString()` tries to load it.
- (c) **Lombok's generated `toString()`**, which includes the `customer` field; building
  the log string calls `toString()`, and that is the hidden `customer` read — no explicit
  getter needed.

**Q2.** Two `@Data` entities pointing at each other (`AuthorEntity.books` ↔
`BookEntity.author`). `System.out.println(someAuthor)` dies with `StackOverflowError`.
Walk through why, and name the one `@ToString` change that stops the recursion while
still printing the author's own fields.

**Answer.** `println(author)` calls `AuthorEntity.toString()` (Lombok), which includes
`books`; printing the list calls each `BookEntity.toString()`, which includes `author`,
which calls `AuthorEntity.toString()` again — the two methods call each other with no
base case, so the call stack grows until it overflows. The fix: keep the relationship out
of `toString()` on at least one side — `@ToString(exclude = "books")` on `AuthorEntity`
(or `@ToString.Exclude` on the `books` field). Then the author still prints its scalar
fields (id) but not the collection, so the loop never starts.

### Do it yourself (second laptop)

**Step 1 — add Lombok** to `pom.xml`:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**Step 2 —** create `BadEventEntity`, `BadSeatMapEntity`, `BadSeatEntity`, the two
repositories, and `L16DataToStringDemo` (all shown above; seat map + 4 seats come from
the Lesson 2 seed).

**Step 3 — run:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l16-data-tostring | tee /tmp/l16.log
```

`Ctrl+C` after `>>> StackOverflowError caught`.

**Step 4 — confirm FACE 1 fired two selects, the second on `venue`:**

```bash
awk '/FACE 1/{f=1} /FACE 2/{f=0} f' /tmp/l16.log | grep -c "Hibernate:"   # expect 2
awk '/FACE 1/{f=1} /FACE 2/{f=0} f' /tmp/l16.log | grep "venue ve"        # the surprise select
```

**Step 5 — the cure:** replace `@Data` with `@Getter @Setter @ToString(exclude = "…")`
keeping relationships out of `toString`, e.g. `@ToString(exclude = "seats")` on the map,
`@ToString(exclude = "seatMap")` on the seat, `@ToString(exclude = "venue")` on the
event. Re-run: FACE 1 fires one select, FACE 2 prints with no crash.

| You see | It means | Fix |
|---|---|---|
| `cannot find symbol: method getId()` | Lombok not on classpath / processing off | Add dep; enable annotation processing in IDE |
| FACE 1 shows one select | `venue` excluded, or entity detached | Use `@Data` to see the trap |
| FACE 2 prints, no error | Recursion already broken | Both sides must include the other |
| `NoSuchElementException` | Seed missing | Re-run the Lesson 2 seat seed |

**Done when:** you have watched one log line fire a surprise `venue` select, another crash
the JVM with `StackOverflowError`, and fixed both by keeping relationships out of
`toString()`.

### Summary

- `@Data` generates `toString()` over every field, including relationships.
- Logging an entity then reads lazy relationships: a hidden query inside a transaction, a
  `LazyInitializationException` outside one.
- Two-way relationships make `toString()` recurse → `StackOverflowError`.
- Never `@Data` on an entity; if using Lombok, keep relationships out of `toString()`.

---

## Lesson 17 — `@Data` danger two: `equals`/`hashCode`, and the generated-id trap (S11 part 2, S13)

### 1. What is it?

`@Data` also generates `equals()` and `hashCode()` from **every field**. On an entity that
is danger two, and it hides the trap the task calls out as subtask S13: **a
database-generated id is `null` before save and a real number after — so any
`equals`/`hashCode` built from the id silently changes the moment you save.** This lesson
makes a `HashSet` lose an object still physically inside it.

Reminder: `equals()` answers "are these the same thing?"; `hashCode()` gives a "bucket
number" so `HashSet`/`HashMap` (and your entity `Set` fields) can find an object fast. The
two must agree, and the rule that keeps them usable is the one `@Data` breaks.

### 2. Why do we need to understand it?

Entities live in hash collections constantly — a `@OneToMany` is a `List`/`Set`,
de-duplication uses a `HashSet`, caches use a `HashMap` keyed by entity. If an entity's
`hashCode` can change after it is placed in one, the collection quietly corrupts:
`contains()` says no to an object it holds, `remove()` can't remove it, a `Set` grows
duplicates. Nothing throws — the data is just wrong. S13 asks for a before-and-after-save
example, so we build one.

### 3. How does it work?

Two facts collide.

**Fact one — a hash collection files an object by its hashCode at insertion time.**
`set.add(note)` computes `note.hashCode()`, picks a bucket, drops it there.
`set.contains(note)` computes the hashCode again and looks in that bucket. **If the
hashCode changed in between, it looks in the wrong bucket and finds nothing** — though the
object still sits in the old bucket.

**Fact two — a database-generated id changes exactly once, on save.** `@GeneratedValue`
means `null` on a new object; the database assigns it during `INSERT`. So `null → 1` on
first save.

`@Data` puts the id in `hashCode()`. Together: add a new entity to a `Set` (hashCode uses
`id = null`), save (id becomes `1`, hashCode changes), and the entity is lost in the set.
Any id-based hashCode on a generated id has this trap; `@Data` just applies it to every
entity automatically.

### 4. Simple example

A throwaway table whose id the **database** generates:

```sql
CREATE TABLE demo_note (
    id   bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    body text NOT NULL
);
```

The `@Data` entity:

```java
@Data
@Entity
@Table(name = "demo_note")
public class BadNoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // the DATABASE assigns it on insert
    private Long id;

    private String body;
}
```

The demo — make a note, put it in a `HashSet`, save it, ask if it is still there:

```java
BadNoteEntity note = new BadNoteEntity();
note.setBody("remember the milk");

System.out.println(">>> id:       " + note.getId());         // null
System.out.println(">>> hashCode: " + note.hashCode());

Set<BadNoteEntity> set = new HashSet<>();
set.add(note);
System.out.println(">>> set.contains(note)? " + set.contains(note));   // true

notes.saveAndFlush(note);   // the DATABASE assigns the id here

System.out.println(">>> id:       " + note.getId());         // 1
System.out.println(">>> hashCode: " + note.hashCode());      // CHANGED
System.out.println(">>> set.contains(note)? " + set.contains(note));   // false
```

Real output:

```text
>>> BEFORE save -----------------------------------------
>>> id:       null
>>> hashCode: 373690911
>>> added to a HashSet. set.contains(note)? true
>>> AFTER save ------------------------------------------
>>> id:       1
>>> hashCode: 373688433
>>> set.contains(note)?  false   <-- the object is lost
>>> but is it physically still inside? iterate the set:
>>>   iteration finds: id=1, body=remember the milk
```

### 5. Walkthrough

1. **Before save:** `id` null, `hashCode` `373690911`; the set files the note in that
   bucket; `contains` is `true`.
2. **The save:** `INSERT` runs, the database generates the id, Hibernate writes it back;
   `id` is now `1`.
3. **After save:** `hashCode` is `373688433` — different, because the id it is built from
   changed. The object did not move; its bucket address changed under it.
4. **The loss:** `contains(note)` computes the new hashCode, looks in that bucket, finds
   nothing, returns **`false`** — for an object the set holds. The loop proves it:
   iteration still finds `id=1, body=remember the milk`.
5. Silent corruption: no exception, a `Set` that stopped working — and we wrote zero
   equals/hashCode code; `@Data` wrote the broken version.

The rule this breaks: **an object's `hashCode` must not change while it lives in a hash
collection.** A database-generated id changes on save, so it must never be in
`equals`/`hashCode`.

### 6. Where is it met?

The classic report: *"I add a new entity to a `Set`, save it, then `set.remove(entity)`
doesn't remove it"*, or a `@OneToMany Set` that accumulates duplicates. Also `HashMap`
caches keyed by entity. Because many teams generate ids in the database, this is the most
common entity-equality mistake.

### 7. Common mistakes

1. **`@Data`/`@EqualsAndHashCode` on an entity** — equals/hashCode over all fields: the
   generated id flips on save, mutable fields flip on edit.
2. **"Fixing" by using only the id** — still breaks across save with a *generated* id
   (null → value).
3. **Using a mutable business field** (an editable email) — the day it changes, every set
   holding the entity corrupts.
4. **Java's default identity equals** — never changes, but two loads of the same row are
   "not equal", breaking de-duplication. The real answer is a stable business key
   (Lesson 19).

### 8. Link to the task

Danger two of three (S11), and it answers S13 in full: **the database-generated id must
not be used in `equals`/`hashCode` because it is `null` before save and a value after, so
an entity put in a `HashSet` before saving becomes unfindable the instant it is saved —
shown live, hashCode `373690911 → 373688433`, `contains` `true → false`.** Lesson 19's
rule: base equals/hashCode on a **stable business key** (our client-assigned UUID, set in
the constructor, never changed), fixed from birth so this cannot happen.

### 9. Check my understanding

**Q1.** An entity uses a generated id and bases equality only on it:

```java
@Entity
class ProductEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;

    @Override public boolean equals(Object o) {
        return o instanceof ProductEntity p && Objects.equals(id, p.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
```

```java
Set<ProductEntity> batch = new HashSet<>();
ProductEntity p = new ProductEntity();
p.setSku("SKU-1");
batch.add(p);
productRepository.saveAndFlush(p);
System.out.println(batch.contains(p));   // ?
```

(a) What does it print, and why? (b) The team moves `batch.add(p)` to *after*
`saveAndFlush` — does that remove the danger or hide it? (c) What property must the field
in `hashCode` have for a `HashSet` to work reliably?

**Answer.**
- (a) **`false`.** Before save `p.id` is null → hashCode files `p` in bucket X. `saveAndFlush`
  assigns the id → hashCode changes → `contains` looks in bucket Y and misses.
- (b) **Only hides it.** After save the id no longer changes, so that one set works — but
  the class is still fragile: any code that adds before saving breaks, and nothing warns
  you. The real fix is a key that is stable from construction.
- (c) It must be **stable for the object's whole life in the set** — its value must never
  change while the object is a member (ideally assigned at construction and never again).

**Q2.** Two rows of the same product load in two transactions → two objects `a`, `b`,
both `id = 42`. With the id-based `equals`, `a.equals(b)` is `true`. If the class kept
Java's default identity `equals`, what would `a.equals(b)` be, and name one operation that
would misbehave?

**Answer.** It would be **`false`** — identity equals compares object references, and `a`
and `b` are different objects despite being the same row. Something that breaks:
de-duplicating loads across transactions — putting both in a `HashSet` keeps two entries
for one real row (or `list.contains(b)` returns false after you loaded `a`). Any logic
that means "same row = same entity" fails, because two loads of row 42 are unequal.

### Do it yourself (second laptop)

**Step 1 —** create `V7__create_demo_note.sql`, `BadNoteEntity.java`,
`BadNoteRepository.java`, `L17EqualsHashcodeDemo.java` (all above).

**Step 2 — run:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l17-equals-hashcode | tee /tmp/l17.log
```

Flyway applies `V7` on startup. `Ctrl+C` after the iteration line.

**Step 3 — read the four numbers:** `id` `null → 1` (higher on re-runs), `hashCode`
**changes**, `contains` `true → false`, iteration still finds the note.

**Step 4 — the cure:** give the entity a **stable** id (client-assigned UUID set at
construction, drop `@GeneratedValue`). Re-run: hashCode identical before/after, `contains`
stays `true`. Previews Lesson 19.

**Step 5 — tidy (optional):** `docker exec -i pg psql -U postgres -d zenith -c "truncate demo_note;"`

| You see | It means | Fix |
|---|---|---|
| `hashCode` identical before/after | Entity used a stable id already | Must be `@GeneratedValue` to see the flip |
| `contains` stays `true` | Same, or not `@Data` | Use `@Data` + generated id |
| Flyway `validate failed` | `V7` edited after a run | Never edit an applied migration |
| `relation "demo_note" does not exist` | `V7` not on the classpath | File under `db/migration`, prefix `V7__` |

**Done when:** a `HashSet` reports `contains = false` for an object it still holds, and you
can state: *a field whose value changes (like a DB-generated id) must never be in
`equals`/`hashCode`.*

### Summary

- `@Data` builds `equals`/`hashCode` from every field, including the id.
- A hash collection files an object by its hashCode at add-time; if the hashCode later
  changes, the object becomes unfindable.
- A DB-generated id goes `null → value` on save, so id-based hashCode breaks exactly then
  — shown live.
- Rule: never put a changing field in `equals`/`hashCode`; use a stable business key
  (Lesson 19).

---

## Lesson 18 — `@Data` danger three: setters on everything destroy the rules (S11 complete)

### 1. What is it?

`@Data` generates a public setter for **every field** — including the `@Id` and the fields
that own relationships. Danger three: an entity should *protect* its rules (the id never
changes once set; a two-way relationship stays consistent; some fields are write-once),
and a wall of generated setters lets any code break every one. This lesson makes Hibernate
refuse the damage with its "identifier was altered" exception.

### 2. Why do we need to understand it?

Dangers one and two were about *reading* (`toString`) and *comparing* (`equals`). This is
about *changing*, the most dangerous, because a setter is an invitation: "this field is
yours to modify, any time." Most entity fields should say the opposite. A class that can't
say "no" to a change can't keep any invariant — and invariants are the whole reason to
have an entity class.

### 3. How does it work?

Three rules an entity should enforce, and the generated setter that breaks each:

1. **"The id is set once and never changes."** Hibernate identifies a managed entity by
   its id (its key in the persistence context). Change the id and you tell Hibernate two
   contradictory things about one object. `@Data` generates `setId(...)`, so this is one
   call away; Hibernate throws at flush.
2. **"A two-way relationship stays consistent on both sides."** Recall Lesson 4: a
   `SeatMap` holds `seats`, each `Seat` holds its `seatMap`, and only the `Seat` side (the
   FK `seat_map_id`) is written. The `addSeat(seat)` helper set *both* sides. `@Data`
   generates raw `setSeats`/`setSeatMap`, letting code set one side and forget the other
   — the object disagrees with itself, and the silent data loss from Lesson 4 returns.
3. **"Write-once fields stay written."** `createdAt`, a business key, the field
   `equals`/`hashCode` depends on — all should be fixed after construction. A generated
   setter makes each mutable, so Lesson 17's "stable key" is stable only until someone
   calls `setId`.

### 4. Simple example

Reusing the `@Data` `BadNoteEntity` — the point is its generated `setId` exists. The demo
saves a note (so it is **managed**), then changes its primary key and flushes:

```java
BadNoteEntity note = new BadNoteEntity();
note.setBody("original");
notes.saveAndFlush(note);                 // now saved and MANAGED
System.out.println(">>> saved. managed id = " + note.getId());

note.setId(999L);                         // @Data handed us setId()
System.out.println(">>> in-memory id is now " + note.getId());

try {
    notes.flush();                        // Hibernate compares id to its snapshot
} catch (Exception e) {
    Throwable root = e;
    while (root.getCause() != null) root = root.getCause();
    System.out.println(">>> CRASH:   " + root.getClass().getName());
    System.out.println(">>> message: " + root.getMessage());
}
```

Real output:

```text
>>> saved. managed id = 2
>>> calling the generated setId(999L) on a managed entity ...
>>> in-memory id is now 999
>>> flushing (Hibernate will compare id to its snapshot) ...
>>> CRASH:   org.hibernate.HibernateException
>>> message: Identifier of an instance of 'com.zenith.rel.BadNoteEntity' was altered from 2 to 999
```

### 5. Walkthrough

1. **The note saved** with id `2` (not `1` — Lesson 17's run already took `1`; the identity
   counter climbs). The entity is now *managed*, tracked under key `2`.
2. **`setId(999L)` succeeded silently** — Java let us rewrite the primary key of a tracked
   entity, because `@Data` generated the setter and nothing forbade the call.
3. **The flush caught it.** Hibernate compared the current id to the one it saved under,
   saw `2 → 999`, and threw `HibernateException: Identifier ... was altered from 2 to
   999`. It must forbid this: it no longer knows which row you meant.
4. The lesson is not "Hibernate protects me" — it is that **the setter should never have
   existed**. The guard fires late, at flush, far from the `setId` call. A real entity
   makes the mistake *impossible* by not offering the setter.

### 6. Where is it met?

The id-alteration crash appears when code "reuses" an entity by overwriting its id, or a
mapper copies fields including the id onto a managed instance. The quieter
relationship-setter damage is everywhere two-way relationships exist: `order.setLines(...)`
without fixing each line's `order` back-reference, and half the graph silently fails to
persist — Lesson 4, re-opened by a generated setter.

### 7. Common mistakes

1. **`@Data`/`@Setter` on an entity** — every field writable, including id and
   relationships. Mutability should be the deliberate exception.
2. **A public `setId`** — application code almost never legitimately sets an entity's id;
   offering it only enables the "altered identifier" crash.
3. **Raw setters for relationship fields** — they bypass the both-sides helper (`addSeat`)
   and reintroduce silent persistence loss.
4. **Setters for write-once fields** (`createdAt`, the business key) — quietly voids
   guarantees other code relies on.

### 8. Link to the task

**S11 complete** — all three ways `@Data` hurts an entity shown live: **`toString()`**
(hidden queries, `StackOverflowError`, L16), **`equals`/`hashCode`** (generated-id
`HashSet` corruption, L17), **setters** (uncontrolled mutation; "identifier was altered",
this lesson). Checkpoint 5 ("two reasons `@Data` is dangerous") is fully backed by real
runs. Lesson 19 writes the hygiene rule the project follows instead (S12), closing the
task.

### 9. Check my understanding

**Q1.** A mapping helper "updates" a managed entity from a request:

```java
@Transactional
public void applyUpdate(UUID requestedId, UpdateRequest req) {
    AccountEntity account = accountRepository.findById(requestedId).orElseThrow();
    account.setId(req.getId());         // @Data generated this setter
    account.setBalance(req.getBalance());
    // transaction commits here
}
```

`req.getId()` differs from `requestedId`. (a) Do the `set...` calls throw when called?
(b) When and with what does this fail? (c) The correct version — which setter should not
exist, and why was it never needed?

**Answer.**
- (a) **No.** Both setters just mutate fields in memory, silently; no exception at the
  call site.
- (b) **At commit/flush**, when Hibernate flushes the persistence context, with
  `org.hibernate.HibernateException: Identifier of an instance of ... was altered from
  <requestedId> to <req.getId()>`. The failure is far from the `setId` line.
- (c) Drop `account.setId(...)` entirely. The account was looked up *by* its id, so its
  identity is already fixed and must never change; only mutable business fields
  (`balance`) get updated. `setId` was never needed — the id is the lookup key, not
  something an update changes — and a correct entity wouldn't offer it.

**Q2.** A teammate replaces the `addSeat` helper with Lombok setters and writes
`map.setSeats(List.of(seatA, seatB));` without touching either seat's `seatMap`. After
`save`, the two seats are **not** linked to the map in the database. Explain why, using the
owning side from Lesson 4, and say what the generated setter failed to do that `addSeat`
did.

**Answer.** The owning side is the `Seat` — it holds the foreign key `seat_map_id` via
`@ManyToOne @JoinColumn`. The database persists only the owning side.
`map.setSeats(...)` sets only the mirror side (`@OneToMany(mappedBy = "seatMap")`), which
Hibernate never writes — it reads the link from each seat's `seat_map_id`. Since neither
seat's `seatMap` was set, each `seat_map_id` stays null and the seats aren't linked on
save (silent loss, exactly Lesson 4). What the setter failed to do that `addSeat` did:
set the **owning** side — `seat.setSeatMap(map)` on each seat — so the foreign key is
populated. `addSeat` kept both sides consistent; the raw setter touched only the side that
doesn't persist.

### Do it yourself (second laptop)

Needs `demo_note` and `BadNoteEntity` from Lesson 17.

**Step 1 —** create `L18SettersDemo.java` (shown above).

**Step 2 — run:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l18-setters | tee /tmp/l18.log
```

Finishes on its own (the exception is caught). Expected tail:

```text
>>> CRASH:   org.hibernate.HibernateException
>>> message: Identifier of an instance of 'com.zenith.rel.BadNoteEntity' was altered from N to 999
```

**Step 3 — prove it's the *managed* id that matters.** Move `note.setId(999L)` to
*before* `saveAndFlush` (a brand-new object) and rerun: no "altered" crash — setting the
id before the entity is managed is just ignored by the `IDENTITY` strategy. The crash is
specifically about mutating a *tracked* entity. Put it back.

| You see | It means | Fix |
|---|---|---|
| No crash in step 2 | `setId` ran before the entity was managed | Keep `setId` after `saveAndFlush` |
| `cannot find symbol: setId` | `@Data`/`@Setter` removed | Restore `@Data` to see the trap |
| Different id in the message | Earlier runs consumed ids | Expected — the counter climbs |

**Done when:** `setId` on a managed entity blows up at flush with "identifier was altered",
and you can list the three rules setters break: the id is write-once, relationships stay
two-sided, write-once fields stay written.

### Summary

- `@Data` generates a setter for every field, including the id and relationship fields.
- Mutating a managed entity's id throws at flush: `Identifier ... was altered from X to Y`.
- Raw relationship setters bypass the both-sides helper and re-open Lesson 4's silent loss.
- The fix is not Hibernate's guard but never offering the setter: entities expose getters,
  not blanket setters.

---

## Lesson 19 — The entity hygiene rule: what we write instead of `@Data` (S12, CP5 — closes the task)

### 1. What is it?

The payoff of the `@Data` arc: the small, fixed recipe this project uses for **every**
entity, chosen so none of the three disasters from Lessons 16-18 can happen. Five rules
(subtask S12, checkpoint 5):

1. **`@Getter` only** — never `@Data`, `@Setter`, or `@EqualsAndHashCode`.
2. **Explicit constructors** — a `protected` no-arg one for Hibernate, a public one that
   sets the key.
3. **A stable business key** — a client-assigned `UUID`, set in the constructor, never
   changed.
4. **`equals`/`hashCode` on that key**, using `instanceof` and the other object's *getter*.
5. **`toString()` on scalar fields only** — never a relationship.

### 2. Why do we need it?

"Don't use `@Data`" is not actionable unless you know what to write instead. Lombok is fine
— the problem was one annotation generating too much. This keeps the good part (no
boilerplate getters) and replaces the dangerous parts with four deliberate choices. The
result is safe to log, safe to put in a `Set`, and impossible to corrupt by mutating its id
— proven, not asserted.

### 3. How does it work?

Each rule maps to one danger you watched happen:

- **`@Getter` only (rule 1)** — Lesson 18's crash needed `setId`; with getters only, there
  is no setter to call. Mutate through narrow methods (Lesson 4's `addSeat`), not `setX`.
- **Explicit constructors (rule 2)** — Hibernate needs a no-arg constructor to build the
  empty object; `protected` nudges app code to the real constructor, which assigns the key.
- **Stable business key (rule 3)** — Lesson 17 broke because a *generated* id went
  `null → value` on save. We assign a `UUID` ourselves, in the constructor, so it is fixed
  from birth and a hashCode on it never moves.
- **`equals`/`hashCode` via `instanceof` + getter (rule 4)** — from Lesson 8 a lazy field
  holds a *proxy* (a subclass). `getClass() ==` is false for a proxy; `instanceof` is true,
  so a loaded entity and its proxy compare equal. Read the other key with `that.getId()`
  (not `that.id`): a proxy's field is null, its getter initializes and returns the key.
- **`toString()` scalars only (rule 5)** — Lesson 16's hidden queries and
  `StackOverflowError` came from `toString()` reaching into relationships. Ours prints
  `id` and `body` and stops.

### 4. Simple example

The whole recipe in one class:

```java
@Getter
@Entity
@Table(name = "demo_good_note")
public class GoodNoteEntity {

    @Id
    private UUID id;

    private String body;

    protected GoodNoteEntity() {
        // Hibernate builds the object empty, then fills the fields
    }

    public GoodNoteEntity(String body) {
        this.id = UUID.randomUUID();   // stable key, fixed from birth - not from the database
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoodNoteEntity that)) return false;   // instanceof: proxy-safe
        return id.equals(that.getId());                          // getter: proxy initializes
    }

    @Override
    public int hashCode() {
        return id.hashCode();   // the key never changes, so this never changes
    }

    @Override
    public String toString() {
        return "GoodNoteEntity{id=" + id + ", body=" + body + "}";   // no relationships
    }
}
```

The demo runs the **exact scenario that broke in Lesson 17** — add to a `HashSet`, save,
re-check:

```text
>>> BEFORE save -----------------------------------------
>>> id:       623dff1c-e14a-423f-914c-8d7e023c2c89
>>> hashCode: 268901588
>>> added to a HashSet. set.contains(note)? true
>>> AFTER save ------------------------------------------
>>> id:       623dff1c-e14a-423f-914c-8d7e023c2c89
>>> hashCode: 268901588
>>> set.contains(note)?  true   <-- still found
>>> toString (scalars only, safe to log): GoodNoteEntity{id=623dff1c-..., body=remember the milk}
```

### 5. Walkthrough

Beside Lesson 17's failure, the same five demo lines:

| | Lesson 17 (`@Data`, generated id) | Lesson 19 (hygiene rule, UUID key) |
|---|---|---|
| id before save | `null` | `623dff1c-…` (already set) |
| hashCode before → after | `373690911 → 373688433` | `268901588 → 268901588` |
| `contains` after save | **`false`** (lost) | **`true`** (held) |
| `toString()` | reads relationships | scalars only |
| `setId` exists? | yes | no |

1. The id was a real UUID **before** the save — no `null → value` jump for the database to
   cause.
2. The hashCode did not move, so the object stayed in its `HashSet` bucket and `contains`
   kept finding it. Rule 3 defused Lesson 17.
3. `toString()` printed `id` and `body` only — safe to log. Rule 5 defused Lesson 16.
4. There is no `setId` to call — Lesson 18's "identifier was altered" is unwritable. Rule 1
   defused Lesson 18.
5. `equals` via `instanceof` + `that.getId()` means a loaded entity and a lazy proxy of the
   same row compare equal (Lesson 8) — the bug an id-based `getClass() ==` equals would hit.

### 6. Where is it used?

The house style for entities in well-run JPA codebases. Mechanics vary (a `@NaturalId`, a
`@MappedSuperclass` base with `equals`/`hashCode` inherited) but the shape is constant:
getters not setters, a key stable from construction, `instanceof` equality, relationships
out of `toString()`. The project's `EventEntity`, `SeatEntity`, etc. already follow the
spirit (client UUIDs, protected no-arg constructors, no `@Data`); this states the rule and
adds the explicit `equals`/`hashCode`/`toString` discipline.

### 7. Common mistakes

1. **Deleting `@Data` but keeping a generated id in `equals`/`hashCode`** — half the fix;
   the key must also be stable.
2. **`equals` with `getClass() == other.getClass()`** — breaks against proxies (L8); use
   `instanceof`.
3. **Reading `that.id` instead of `that.getId()` in `equals`** — a proxy's field is null;
   the getter initializes it.
4. **Adding setters "just for tests"** — tests use the constructor; a public id/relationship
   setter is a production hazard.
5. **Forgetting the `protected` no-arg constructor** — Hibernate needs it to load the entity.

### 8. Link to the task

**S12 done**, written into `NOTES.md`: the five rules, a table mapping each to the danger it
prevents, and proof that `GoodNoteEntity` kept hashCode `268901588` across a save.
**Checkpoint 5** answered in `NOTES.md`: (1) `@Data`'s `toString`/`equals`/`hashCode`
include relationships → lazy loads/recursion; (2) its `equals`/`hashCode` include the
mutable/generated id and it generates blanket setters → entities lost in hash collections,
invariants mutated away. **This completes Task F6.11** — S1-S13 and checkpoints 1-5, each
backed by a real run.

### 9. Check my understanding

**Q1.** A teammate writes this, pleased they "avoided `@Data`":

```java
@Getter
@Setter
@Entity
class CouponEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;

    @Override public boolean equals(Object o) {
        return o != null && getClass() == o.getClass()
                && id != null && id.equals(((CouponEntity) o).id);
    }
    @Override public int hashCode() { return id == null ? 0 : id.hashCode(); }
}
```

Name the **three** rule violations and the concrete failure each causes.

**Answer.**
1. **`@Setter` on the entity** (violates rule 1). It generates `setId`/`setCode`; mutating a
   managed entity's id throws `Identifier ... was altered` at flush (Lesson 18), and the
   business field loses its write-once guarantee.
2. **A database-generated id used in `equals`/`hashCode`** (violates rule 3). The id is
   `null` before save and a value after, so an entity added to a `HashSet` before saving is
   lost the instant it is saved (Lesson 17) — `hashCode` returns `0` then the real hash.
3. **`equals` using `getClass() == o.getClass()`** (violates rule 4). A lazy proxy is a
   subclass, so `getClass()` differs and a loaded `CouponEntity` and its proxy of the same
   row compare **unequal** (Lesson 8). Use `instanceof`.

**Q2.** Our `equals` reads `that.getId()`, not `that.id`. With a lazy venue **proxy** being
checked against a `Set<VenueEntity>`, explain why the getter works and the raw field would
not, using how a proxy stores its id (Lesson 8).

**Answer.** A lazy proxy is a generated *subclass* of `VenueEntity`. The real id it was born
with (from the owner's foreign key) lives inside the proxy's interceptor, **not** in the
subclass's inherited `id` field, which is left null. Reading `that.id` (direct field access)
reads that null field → `equals`/`hashCode` would compare against null and never match.
Reading `that.getId()` calls the intercepted getter, which returns the stored id (Lesson 8
showed `getId()` answers without even a database hit). So the getter yields the real key and
the comparison works; the raw field yields null and breaks it.

### Do it yourself (second laptop)

**Step 1 —** create `V8__create_demo_good_note.sql`, `GoodNoteEntity.java`,
`GoodNoteRepository.java`, `L19GoodEntityDemo.java` (all above).

**Step 2 — run:**

```bash
mvn -q spring-boot:run -Dspring-boot.run.profiles=l19-good-entity | tee /tmp/l19.log
```

Flyway applies `V8` on startup. `Ctrl+C` after the `toString` line.

**Step 3 — read the contrast:** `id` is a UUID **before** the save, `hashCode` is
**identical** before and after, `set.contains(note)` stays **`true`**. Compare to
`/tmp/l17.log`, where the same lines gave `false`.

**Step 4 — confirm the setter is gone.** Add `note.setId(UUID.randomUUID());` to the demo:
it must **fail to compile** (`cannot find symbol: method setId`) — rule 1 making the
Lesson 18 crash unwritable. Remove the line.

**Step 5 — read `NOTES.md`** — the "Entity hygiene rules" and "Checkpoint 5" sections are
the S12 deliverable.

| You see | It means | Fix |
|---|---|---|
| `hashCode` differs before/after | Key not stable (generated id, or set outside ctor) | Assign `UUID.randomUUID()` in the constructor |
| `note.setId(...)` compiles | `@Setter`/`@Data` sneaked back | `@Getter` only |
| `contains` is `false` | `equals`/`hashCode` not on the stable key | Base both on `id` |
| Flyway `validate failed` on `V8` | File edited after a run | Never edit an applied migration |

**Done when:** the Lesson 17 scenario now reports `contains = true`, `note.setId(...)` won't
compile, and you can recite the five rules and the danger each prevents.

### Summary

- The rule that replaces `@Data`: `@Getter` only; explicit constructors; a stable
  client-assigned UUID key; `equals`/`hashCode` on that key via `instanceof` + getter;
  `toString()` on scalars only.
- Each rule closes one proven danger: logging (L16), hash collections (L17), mutation (L18),
  proxy equality (L8).
- Proven: `GoodNoteEntity` held its hashCode across a save, so the `HashSet` that failed in
  Lesson 17 now keeps the object.
- **Task F6.11 complete** — all subtasks and checkpoints done, every claim backed by a real
  run, captured here and in `NOTES.md`.

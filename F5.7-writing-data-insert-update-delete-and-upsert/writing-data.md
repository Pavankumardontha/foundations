# F5.7 — Writing data: INSERT, UPDATE, DELETE, and upsert

Notes for Task F5.7. One lesson per section. Each lesson ends with its understanding-check
questions and full answers, plus a **Do it yourself** block with exact commands. Every SQL
output shown here is real output captured from a running Postgres 17 container, not invented.

**Learning outcome.** Insert rows and get the generated id back in the same statement. Update
and delete safely. Write an upsert that makes a repeated insert harmless.

**Why this matters.** Writes are where data is lost. One missing `WHERE` changes every row in
the table, and outside a transaction there is no undo. Upserts are the tool Phase 5 uses to make
a replayed message or a retried request do no damage.

---

## Starting state of the machine

These notes assume the state left behind by tasks F5.1 to F5.5:

- A Docker container named `pg` running the `postgres:17-alpine` image, port 5432 published,
  data kept in the `pgdata` volume.
- A database named `zenith` inside it.
- Three tables: `venue` (3 rows), `event` (4 rows), `price_tier` (6 rows).

### The three tables

```text
                            Table "public.venue"
     Column     |           Type           | Collation | Nullable | Default
----------------+--------------------------+-----------+----------+---------
 id             | uuid                     |           | not null |
 name           | text                     |           | not null |
 city           | text                     |           | not null |
 capacity       | integer                  |           | not null |
 seat_map_hints | jsonb                    |           |          |
 created_at     | timestamp with time zone |           | not null | now()
Indexes:
    "venue_pkey" PRIMARY KEY, btree (id)
Check constraints:
    "venue_capacity_positive" CHECK (capacity > 0)
Referenced by:
    TABLE "event" CONSTRAINT "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)

                           Table "public.event"
    Column    |           Type           | Collation | Nullable | Default
--------------+--------------------------+-----------+----------+---------
 id           | uuid                     |           | not null |
 venue_id     | uuid                     |           | not null |
 name         | text                     |           | not null |
 starts_at    | timestamp with time zone |           | not null |
 created_at   | timestamp with time zone |           | not null | now()
 cancelled_at | timestamp with time zone |           |          |
Indexes:
    "event_pkey" PRIMARY KEY, btree (id)
Foreign-key constraints:
    "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)
Referenced by:
    TABLE "price_tier" CONSTRAINT "price_tier_event_fk" FOREIGN KEY (event_id) REFERENCES event(id)

                       Table "public.price_tier"
   Column   |           Type           | Collation | Nullable | Default
------------+--------------------------+-----------+----------+---------
 id         | uuid                     |           | not null |
 event_id   | uuid                     |           | not null |
 name       | text                     |           | not null |
 amount     | numeric(19,4)            |           | not null |
 created_at | timestamp with time zone |           | not null | now()
Indexes:
    "price_tier_pkey" PRIMARY KEY, btree (id)
    "price_tier_name_unique_per_event" UNIQUE CONSTRAINT, btree (event_id, name)
Check constraints:
    "price_tier_amount_non_negative" CHECK (amount >= 0::numeric)
Foreign-key constraints:
    "price_tier_event_fk" FOREIGN KEY (event_id) REFERENCES event(id)
```

### The data

```text
SELECT id, name, city, capacity FROM venue ORDER BY name;

                  id                  |      name       |   city    | capacity
--------------------------------------+-----------------+-----------+----------
 33333333-3333-3333-3333-333333333333 | Lakeside Hall   | Chennai   |      800
 11111111-1111-1111-1111-111111111111 | Riverside Arena | Hyderabad |    12000
 22222222-2222-2222-2222-222222222222 | The Blue Note   | Mumbai    |      350
(3 rows)

SELECT id, venue_id, name, starts_at, cancelled_at FROM event ORDER BY name;

                  id                  |               venue_id               |        name        |       starts_at        |         cancelled_at
--------------------------------------+--------------------------------------+--------------------+------------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000002 | 11111111-1111-1111-1111-111111111111 | Classical Evening  | 2026-12-05 13:00:00+00 | 2026-10-24 18:05:00.690845+00
 aaaaaaaa-0000-0000-0000-000000000003 | 22222222-2222-2222-2222-222222222222 | Jazz at the Note   | 2026-11-28 15:30:00+00 |
 aaaaaaaa-0000-0000-0000-000000000001 | 11111111-1111-1111-1111-111111111111 | Monsoon Rock Night | 2026-11-20 13:30:00+00 |
 aaaaaaaa-0000-0000-0000-000000000004 | 22222222-2222-2222-2222-222222222222 | Open Mic Night     | 2026-12-12 14:30:00+00 |
(4 rows)

SELECT id, event_id, name, amount FROM price_tier ORDER BY id;

                  id                  |               event_id               |    name     |  amount
--------------------------------------+--------------------------------------+-------------+-----------
 bbbbbbbb-0000-0000-0000-000000000001 | aaaaaaaa-0000-0000-0000-000000000001 | General     | 1499.0000
 bbbbbbbb-0000-0000-0000-000000000002 | aaaaaaaa-0000-0000-0000-000000000001 | VIP         | 4999.0000
 bbbbbbbb-0000-0000-0000-000000000003 | aaaaaaaa-0000-0000-0000-000000000002 | General     |  999.0000
 bbbbbbbb-0000-0000-0000-000000000004 | aaaaaaaa-0000-0000-0000-000000000003 | General     | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000005 | aaaaaaaa-0000-0000-0000-000000000003 | Front Table | 3499.0000
 bbbbbbbb-0000-0000-0000-000000000006 | aaaaaaaa-0000-0000-0000-000000000001 | Balcony     | 2499.0000
(6 rows)
```

In words:

| Venue | City | Capacity | Events it hosts |
| --- | --- | --- | --- |
| Riverside Arena | Hyderabad | 12000 | Monsoon Rock Night, Classical Evening (cancelled) |
| The Blue Note | Mumbai | 350 | Jazz at the Note, Open Mic Night |
| Lakeside Hall | Chennai | 800 | none |

| Event | Its price tiers |
| --- | --- |
| Monsoon Rock Night | General 1499, Balcony 2499, VIP 4999 |
| Classical Evening (cancelled) | General 999 |
| Jazz at the Note | General 1999, Front Table 3499 |
| Open Mic Night | none |

### Setting all of this up from nothing

Use this only on a machine that is missing Docker, the container, the database, or the tables.
It was tested end to end against Postgres 17: the result matched the state above column for
column, constraint for constraint, and row for row.

**Step A — install Docker.** On a Mac, install
[Docker Desktop](https://www.docker.com/products/docker-desktop/), open it once, and wait until
it says it is running. With Homebrew instead: `brew install colima docker`, then `colima start`.
Check it works:

```bash
docker version
```

Expected: a `Client:` section and a `Server:` section.

**Step B — start a Postgres container with a volume:**

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=dev -p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine
```

Expected: one long line of letters and digits (the new container's id). If it says
`The container name "/pg" is already in use`, the container already exists — run
`docker start pg` instead.

**Step C — create the database.** Wait about ten seconds for Postgres to finish starting, then:

```bash
docker exec pg psql -U postgres -c "CREATE DATABASE zenith;"
```

Expected: `CREATE DATABASE`. If it says `database "zenith" already exists`, that step is done.

**Step D — create and fill the tables.** Open psql:

```bash
docker exec -it pg psql -U postgres -d zenith
```

At the `zenith=#` prompt, paste this whole block:

```sql
CREATE TABLE venue (
    id             uuid PRIMARY KEY,
    name           text NOT NULL,
    city           text NOT NULL,
    capacity       integer NOT NULL,
    seat_map_hints jsonb,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT venue_capacity_positive CHECK (capacity > 0)
);

CREATE TABLE event (
    id           uuid PRIMARY KEY,
    venue_id     uuid NOT NULL,
    name         text NOT NULL,
    starts_at    timestamptz NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    cancelled_at timestamptz,
    CONSTRAINT event_venue_fk FOREIGN KEY (venue_id) REFERENCES venue(id)
);

CREATE TABLE price_tier (
    id         uuid PRIMARY KEY,
    event_id   uuid NOT NULL,
    name       text NOT NULL,
    amount     numeric(19,4) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT price_tier_event_fk FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT price_tier_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT price_tier_name_unique_per_event UNIQUE (event_id, name)
);

INSERT INTO venue (id, name, city, capacity) VALUES
('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000),
('22222222-2222-2222-2222-222222222222', 'The Blue Note',   'Mumbai',      350),
('33333333-3333-3333-3333-333333333333', 'Lakeside Hall',   'Chennai',     800);

INSERT INTO event (id, venue_id, name, starts_at, cancelled_at) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Monsoon Rock Night', '2026-11-20 19:00+05:30', NULL),
('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Classical Evening',  '2026-12-05 18:30+05:30', '2026-10-24 23:35+05:30'),
('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'Jazz at the Note',   '2026-11-28 21:00+05:30', NULL),
('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'Open Mic Night',     '2026-12-12 20:00+05:30', NULL);

INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General',     1499),
('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP',         4999),
('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General',      999),
('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General',     1999),
('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499),
('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000001', 'Balcony',     2499);
```

Expected output:

```text
CREATE TABLE
CREATE TABLE
CREATE TABLE
INSERT 0 3
INSERT 0 4
INSERT 0 6
```

**Step E — check the counts:**

```sql
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;
```

Expected:

```text
 venues | events | tiers
--------+--------+-------
      3 |      4 |     6
(1 row)
```

**Reset (optional).** If the tables exist but the counts are not 3, 4 and 6, and you want
exactly the state these notes assume, drop the three tables and repeat Step D. This deletes the
three tables and every row in them:

```sql
DROP TABLE price_tier, event, venue;
```

Expected: `DROP TABLE`. One statement may drop several tables at once; the foreign keys between
them do not block it, because all three go together.

**About constraint names.** If your tables were built by a shorter script with no
`CONSTRAINT name` parts, errors will show names Postgres generated instead — for example
`event_venue_id_fkey` instead of `event_venue_fk`, and `price_tier_event_id_name_key` instead of
`price_tier_name_unique_per_event`. The rules behave identically. `\d venue` shows which names
you have.

---

## The subtasks of F5.7

| # | Subtask |
| --- | --- |
| S1 | Insert a venue with `INSERT ... RETURNING id`. You get the new id back without a second query. That matters when you insert child rows next. |
| S2 | Insert several rows in one statement, using several `VALUES` lists. Compare the row count Postgres reports. |
| S3 | Rename one event with an `UPDATE` that has a `WHERE` on its id. Read the `UPDATE 1` that comes back. Make checking that number a habit. |
| S4 | Break it on purpose. In a throwaway copy of a table, run `UPDATE scratch SET name = 'oops';` with no `WHERE`. Read the row count. In production, that is every event renamed, with no error. |
| S5 | Adopt the habit that prevents S4. Write the `WHERE` first, or run it as a `SELECT` before turning it into an `UPDATE`. Write the habit down. |
| S6 | Delete a price tier. Then try to delete a venue that still has events. Watch the foreign key refuse. |
| S7 | Decide where soft deletes belong in this project. Write your rule down. |
| S8 | Write both upsert forms against `price_tier`, using its unique pair `(event_id, name)`: `DO NOTHING` and `DO UPDATE SET amount = EXCLUDED.amount`. |
| S9 | Run each upsert twice. Check the table after each run. Connect it to F4.2: this is how a `POST` is made safe to retry. |
| S10 | Ask an AI the task's question about `ON CONFLICT` and `EXCLUDED`, with ticketing examples. |

## The lesson roadmap

| Lesson | What it teaches | Subtask it serves |
| --- | --- | --- |
| 1 | `INSERT`, and the reply line `INSERT 0 1` | Foundation for S1, S2, S8 |
| 2 | `RETURNING`: getting back values the database filled in | S1 |
| 3 | Several rows in one `INSERT` | S2 |
| 4 | `UPDATE ... SET ... WHERE`: changing rows that already exist | S3 |
| 5 | The affected row count: reading `UPDATE 1`, `UPDATE 0`, `UPDATE 4000` | S3, checkpoint question 2 |
| 6 | The missing `WHERE`: what an `UPDATE` with no filter does | S4 |
| 7 | Autocommit: why psql has no undo button | S4, S5 |
| 8 | The safety habit: `WHERE` first, `SELECT` first | S5 |
| 9 | `DELETE ... WHERE`: removing rows | S6 |
| 10 | When a delete is refused: foreign keys protecting children | S6 |
| 11 | Soft delete: marking a row as gone instead of removing it | S7, checkpoint question 4 |
| 12 | Why repeating an `INSERT` is dangerous, and what an upsert is | Foundation for S8, S9 |
| 13 | `ON CONFLICT ... DO NOTHING` | S8, S9 |
| 14 | `ON CONFLICT ... DO UPDATE`, and `EXCLUDED` | S8, S9 |
| 15 | Running it twice: why both forms are safe to repeat | S9, checkpoint question 3 |
| — | Final run-through of every subtask, the AI question, the checkpoint | S10, "done when" |

---

## Lesson 1 — `INSERT`, and the reply line `INSERT 0 1`

### Definition

`INSERT` adds new rows to a table. You name the table and the columns you want to fill. Then
you give one value for each of those columns, in the same order.

### Why it is needed

A new table holds no rows. Every row in every table got there through an `INSERT`: starting
data, every new event, and later every ticket sold.

`INSERT` also prints a short reply after it runs, and that reply tells you whether the row went
in. If you do not read it, a failed insert can scroll past unnoticed — and you carry on
believing in a row the database never stored.

### Key points

- Values pair with the **column list** by position, not by name.
- Columns left out of the column list get their **default**, or `NULL` if they have none.
- Defaults are filled in first; then every constraint is checked against the finished row.
- One failed constraint refuses the **whole** row. Nothing is stored.
- Success prints a **command tag** such as `INSERT 0 1`. Its last number is the
  **affected row count**.
- Always write the column list. Without it, values follow the table's own column order.

### How it works, step by step

The shape:

```sql
INSERT INTO table_name (column_1, column_2, column_3)
VALUES (value_1, value_2, value_3);
```

1. Postgres pairs the value list with the column list by position. The first value goes into
   the first column named, the second value into the second column, and so on.
2. Every column not named gets its default — a value the table fills in by itself, such as
   `now()` (the current moment) for `created_at`. A column with no default gets `NULL`.
3. Postgres checks every constraint on the finished row (`NOT NULL`, `CHECK`, primary key,
   foreign key, `UNIQUE`).
4. If any constraint fails, Postgres refuses the whole row. psql prints `ERROR`. Nothing is
   stored.
5. If every constraint passes, Postgres stores the row and psql prints the command tag.

The command tag for one inserted row is `INSERT 0 1`:

| Part | Meaning |
| --- | --- |
| `INSERT` | The kind of statement that ran. |
| `0` | Left over from old versions of Postgres. Today it is always `0`. Ignore it. |
| `1` | The affected row count: how many rows the statement added, changed, or removed. |

Every `INSERT`, `UPDATE` and `DELETE` reports an affected row count. Reading it is the habit
this whole task builds.

### Example — real run (scratch table, dropped afterwards)

```sql
CREATE TABLE scratch_venue (
    id         uuid PRIMARY KEY,
    name       text NOT NULL,
    city       text NOT NULL,
    capacity   integer NOT NULL CHECK (capacity > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
```

```text
INSERT INTO scratch_venue (id, name, city, capacity)
VALUES ('44444444-4444-4444-4444-444444444444', 'Harbour Dome', 'Kochi', 5000);
INSERT 0 1

SELECT * FROM scratch_venue;
                  id                  |     name     | city  | capacity |          created_at
--------------------------------------+--------------+-------+----------+-------------------------------
 44444444-4444-4444-4444-444444444444 | Harbour Dome | Kochi |     5000 | 2026-10-25 11:10:21.538891+00
(1 row)

INSERT INTO scratch_venue (id, name, city, capacity)
VALUES ('55555555-5555-5555-5555-555555555555', 'Tiny Room', 'Pune', 0);
ERROR:  new row for relation "scratch_venue" violates check constraint "scratch_venue_capacity_check"
DETAIL:  Failing row contains (55555555-5555-5555-5555-555555555555, Tiny Room, Pune, 0, 2026-10-25 11:10:21.53958+00).

SELECT count(*) FROM scratch_venue;
 count
-------
     1
(1 row)
```

### Walkthrough

**The first insert** — `INSERT INTO scratch_venue (id, name, city, capacity) VALUES
('44444444-...', 'Harbour Dome', 'Kochi', 5000);`

1. `INSERT INTO scratch_venue` names the table that gets the new row.
2. The column list names four columns; the value list holds four values. Pairing by position:

   | Position | Column | Value |
   | --- | --- | --- |
   | 1 | `id` | `'44444444-4444-4444-4444-444444444444'` |
   | 2 | `name` | `'Harbour Dome'` |
   | 3 | `city` | `'Kochi'` |
   | 4 | `capacity` | `5000` |

3. `created_at` is not in the column list, so Postgres used its default, `now()`. The `+00`
   means the time is shown in UTC, the world's reference clock, which is 5 hours 30 minutes
   behind Indian time.
4. Every constraint passed. The row was stored, and psql printed `INSERT 0 1`.

**The second insert** — `INSERT INTO scratch_venue (id, name, city, capacity) VALUES
('55555555-...', 'Tiny Room', 'Pune', 0);`

1. The pairing worked the same way, so `capacity` got `0`.
2. `CHECK (capacity > 0)` failed, because 0 is not greater than 0.
3. Postgres refused the whole row. psql printed `ERROR` instead of a command tag.
4. `count(*)` still says 1. The refused row left no trace.

Decoding the error:

| Part | Plain English |
| --- | --- |
| `new row for relation "scratch_venue"` | The row you tried to add to `scratch_venue`. "Relation" is Postgres's formal word for a table. |
| `violates check constraint "scratch_venue_capacity_check"` | It broke this CHECK rule. Postgres built the name from the table, the column, and the word `check`. |
| `DETAIL: Failing row contains (...)` | The complete row that was refused. |

The `DETAIL` line shows the refused row already holding a `created_at` value. That proves the
order: defaults are filled in first, and then the rules are checked.

### Important terms

| Term | Meaning |
| --- | --- |
| **Statement** | One complete SQL command. It ends with a semicolon `;`. |
| **Column list** | The bracketed list of column names after the table name. |
| **Value list** | The bracketed list of values after the word `VALUES`. |
| **Default** | A value the table fills in by itself when an insert leaves the column out. |
| **Constraint** | A rule the table enforces on every row, refusing rows that break it. |
| **Command tag** | The one-line reply psql prints after a statement succeeds, such as `INSERT 0 1`. |
| **Affected row count** | The last number in the command tag: rows added, changed, or removed. |
| **Scratch table** | A temporary table made for one experiment and dropped afterwards. |
| **psql** | The terminal program you type SQL into. |

### Common mistakes

**Leaving out the column list.** Postgres allows `INSERT INTO scratch_venue VALUES (...)`. It
then pairs the values with the table's columns in the order the table was created, starting
from the left; columns left over at the end get their default. Remember that order wrong and
values land in the wrong columns. Two swapped text columns pass every rule, so nothing warns
you. Real output:

```text
INSERT INTO scratch_venue
VALUES ('66666666-6666-6666-6666-666666666666', 'Mumbai', 'Sea View Hall', 900);
INSERT 0 1

SELECT id, name, city, capacity FROM scratch_venue;
                  id                  |     name     |     city      | capacity
--------------------------------------+--------------+---------------+----------
 44444444-4444-4444-4444-444444444444 | Harbour Dome | Kochi         |     5000
 66666666-6666-6666-6666-666666666666 | Mumbai       | Sea View Hall |      900
(2 rows)
```

A venue named "Mumbai" in a city called "Sea View Hall", and psql said `INSERT 0 1`. The
command tag counts rows; it cannot see that values are in the wrong columns. The fix is a column
list written in the order you are thinking in:

```text
INSERT INTO scratch_venue (id, city, name, capacity)
VALUES ('77777777-7777-7777-7777-777777777777', 'Mumbai', 'Sea View Hall', 900);
INSERT 0 1

SELECT id, name, city, capacity FROM scratch_venue;
                  id                  |     name      |     city      | capacity
--------------------------------------+---------------+---------------+----------
 44444444-4444-4444-4444-444444444444 | Harbour Dome  | Kochi         |     5000
 66666666-6666-6666-6666-666666666666 | Mumbai        | Sea View Hall |      900
 77777777-7777-7777-7777-777777777777 | Sea View Hall | Mumbai        |      900
(3 rows)
```

**A different number of columns and values.** Refused before any row is built:

```text
INSERT INTO scratch_venue (id, name, city, capacity)
VALUES ('88888888-8888-8888-8888-888888888888', 'Hill Stage', 'Shimla');
ERROR:  INSERT has more target columns than expressions
LINE 1: INSERT INTO scratch_venue (id, name, city, capacity)
                                                   ^

INSERT INTO scratch_venue (id, name, city)
VALUES ('88888888-8888-8888-8888-888888888888', 'Hill Stage', 'Shimla', 400);
ERROR:  INSERT has more expressions than target columns
LINE 2: ...-8888-8888-8888-888888888888', 'Hill Stage', 'Shimla', 400);
                                                                  ^
```

| Error | Plain English |
| --- | --- |
| `more target columns than expressions` | 4 columns named, only 3 values given. "Target columns" = the column list; "expressions" = the values. The caret points at the column with no value. |
| `more expressions than target columns` | 4 values given, only 3 columns named. The caret points at the extra value. |

**Not reading the reply.** psql prints `ERROR` and moves on. In a long script that line scrolls
away, and the row you think you added does not exist. After every write, look for the command
tag and its number.

### Edge cases

**An explicit `NULL` skips the default.** Leaving a column out and writing `NULL` for it are
different things:

```text
INSERT INTO scratch_venue (id, name, city, capacity, created_at)
VALUES ('44444444-4444-4444-4444-444444444444', 'Harbour Dome', 'Kochi', 5000, NULL);
ERROR:  null value in column "created_at" of relation "scratch_venue" violates not-null constraint
DETAIL:  Failing row contains (44444444-4444-4444-4444-444444444444, Harbour Dome, Kochi, 5000, null).
```

**The word `DEFAULT` asks for the default on purpose.** It may stand in a value list wherever a
value would go:

```text
INSERT INTO scratch_venue (id, name, city, capacity, created_at)
VALUES ('44444444-4444-4444-4444-444444444444', 'Harbour Dome', 'Kochi', 5000, DEFAULT);
INSERT 0 1

SELECT * FROM scratch_venue;
                  id                  |     name     | city  | capacity |          created_at
--------------------------------------+--------------+-------+----------+-------------------------------
 44444444-4444-4444-4444-444444444444 | Harbour Dome | Kochi |     5000 | 2026-10-25 11:17:26.993644+00
(1 row)
```

**No column list and fewer values than columns.** The values fill the table's columns from the
left; every column after the last value gets its default. For example,
`INSERT INTO scratch_venue VALUES ('66666666-6666-6666-6666-666666666666', 'Mumbai', 'Sea View Hall', 900);`
gives four values to the five-column `scratch_venue`: they fill `id`, `name`, `city` and
`capacity`, and `created_at` gets `now()`.

### Where it is used

- A sign-up form becomes an `INSERT` into a customer table.
- Buying a ticket becomes an `INSERT` into a booking table.
- A seed script is a list of `INSERT` statements.
- Java code receives the same affected row count. JDBC is Java's standard library for talking
  to databases; its `executeUpdate(...)` returns this number as an `int`. Careful code checks
  that it is `1` after inserting one row.

### Link to the task

- **S1** inserts a venue; Lesson 2 adds `RETURNING` to the end of this same `INSERT` shape.
- **S2** inserts several rows in one statement; the affected row count then exceeds 1, and S2
  asks you to compare it.
- **S8** writes upserts; an upsert is an `INSERT` with one extra clause at the end.
- The habit of reading the affected row count starts here; **S3** makes it the main point.

### Do it yourself

Runs on any machine. If it has no Docker, no `pg` container, or no `zenith` database, first do
Steps A to C of **Setting all of this up from nothing** near the top of this file. This lesson
needs only an empty database; it uses a scratch table.

**Step 1 — check that Postgres is running.**

```bash
docker ps
```

Expected: a row whose `NAMES` column says `pg` and whose `STATUS` starts with `Up`. If that row
is missing, run `docker start pg` and check again.

**Step 2 — open psql inside the container.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`

**Step 3 — create the scratch table.**

```sql
CREATE TABLE scratch_venue (
    id         uuid PRIMARY KEY,
    name       text NOT NULL,
    city       text NOT NULL,
    capacity   integer NOT NULL CHECK (capacity > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
```

Expected: `CREATE TABLE`. If you see `relation "scratch_venue" already exists`, an old copy was
left behind: run `DROP TABLE scratch_venue;` and repeat this step.

**Step 4 — insert one row with a column list.**

```sql
INSERT INTO scratch_venue (id, name, city, capacity)
VALUES ('44444444-4444-4444-4444-444444444444', 'Harbour Dome', 'Kochi', 5000);
```

Expected: `INSERT 0 1`.

**Step 5 — look at the row.**

```sql
SELECT * FROM scratch_venue;
```

Expected: 1 row. `created_at` shows your current time in UTC. You never typed it; the default
filled it in.

**Step 6 — get an insert refused.**

```sql
INSERT INTO scratch_venue (id, name, city, capacity)
VALUES ('55555555-5555-5555-5555-555555555555', 'Tiny Room', 'Pune', 0);
```

Expected failure:

```text
ERROR:  new row for relation "scratch_venue" violates check constraint "scratch_venue_capacity_check"
DETAIL:  Failing row contains (55555555-5555-5555-5555-555555555555, Tiny Room, Pune, 0, <your current time>).
```

Then prove nothing was stored:

```sql
SELECT count(*) FROM scratch_venue;
```

Expected: `1`.

**Step 7 — fall into the no-column-list trap.**

```sql
INSERT INTO scratch_venue
VALUES ('66666666-6666-6666-6666-666666666666', 'Mumbai', 'Sea View Hall', 900);

SELECT id, name, city, capacity FROM scratch_venue;
```

Expected: `INSERT 0 1`, then 2 rows. The new row has `name = Mumbai` and
`city = Sea View Hall`. Wrong data, and no error anywhere.

**Step 8 — write the safe version.**

```sql
INSERT INTO scratch_venue (id, city, name, capacity)
VALUES ('77777777-7777-7777-7777-777777777777', 'Mumbai', 'Sea View Hall', 900);

SELECT id, name, city, capacity FROM scratch_venue;
```

Expected: `INSERT 0 1`, then 3 rows. The newest row has `name = Sea View Hall` and
`city = Mumbai`. Correct.

**Step 9 — clean up.**

```sql
DROP TABLE scratch_venue;
```

Expected: `DROP TABLE`. Type `\q` and press Enter to leave psql.

**Done when:** you can say what each part of `INSERT 0 1` means, and why step 7 stored wrong
data without any error.

### Summary for revision

- `INSERT INTO t (columns) VALUES (values);` — values pair with the column list by position.
- Left-out columns get their default (or `NULL`). An explicit `NULL` skips the default; the
  word `DEFAULT` asks for it.
- Defaults first, then every constraint. One failure refuses the whole row.
- Success prints `INSERT 0 1`: the kind of statement, a leftover `0`, the affected row count.
  Failure prints `ERROR` and stores nothing.
- Always write the column list; without it, swapped text columns go in silently.
- Read the command tag after every write.

### Check questions, with answers

**Question 1.** Here is a table. It is empty.

```sql
CREATE TABLE ticket_type (
    id         uuid PRIMARY KEY,
    name       text NOT NULL,
    price      numeric(19,4) NOT NULL CHECK (price >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
```

Someone runs this statement:

```sql
INSERT INTO ticket_type (id, name, price)
VALUES ('12121212-1212-1212-1212-121212121212', 'Student', 499);
```

(a) What does psql print right after the statement? Explain each of its three parts.
(b) What value ends up in `created_at`, and why?
(c) Suppose the statement had used a price of `-50` instead of `499`. What would psql print
instead? How many rows would the table hold afterwards?

**Answer.**

(a) `INSERT 0 1`. `INSERT` is the kind of statement that ran. `0` is a leftover from old
versions of Postgres and is always `0` today. `1` is the affected row count: one row was added.

(b) The exact moment the statement ran. `created_at` is not in the column list, so Postgres
used the column's default, `now()`. Real output (note also that `499` is stored as `499.0000`,
because the column is `numeric(19,4)`):

```text
                  id                  |  name   |  price   |          created_at
--------------------------------------+---------+----------+-------------------------------
 12121212-1212-1212-1212-121212121212 | Student | 499.0000 | 2026-10-25 11:10:34.553097+00
(1 row)
```

(c) An error, and no command tag:

```text
ERROR:  new row for relation "ticket_type" violates check constraint "ticket_type_price_check"
DETAIL:  Failing row contains (12121212-1212-1212-1212-121212121212, Student, -50.0000, 2026-10-25 11:13:37.421928+00).
```

The table would hold **0** rows. `CHECK (price >= 0)` failed, so the whole row was refused and
nothing was stored. The `DETAIL` line shows `created_at` already filled in — defaults are
applied before the rules are checked — but that half-built row was thrown away.

**Question 2.** Here is another table:

```sql
CREATE TABLE attendee (
    id         uuid PRIMARY KEY,
    email      text NOT NULL,
    full_name  text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

Someone runs this statement, with no column list:

```sql
INSERT INTO attendee
VALUES ('34343434-3434-3434-3434-343434343434', 'Asha Rao', 'asha@example.com');
```

Does it succeed? What ends up in `email`, and what ends up in `full_name`? Why does Postgres not
complain? Rewrite the statement so it stays correct whatever order the table's columns are in.

**Answer.** It succeeds with `INSERT 0 1` — and stores the values swapped:

```text
                  id                  |  email   |    full_name
--------------------------------------+----------+------------------
 34343434-3434-3434-3434-343434343434 | Asha Rao | asha@example.com
(1 row)
```

With no column list, Postgres pairs the values with the table's columns in the order the table
was created, from the left: `id`, then `email`, then `full_name`. `created_at` got no value, so
it took its default. Postgres does not complain because every rule passes: both values are
text, neither is `NULL`, and the id is new. Postgres checks types and rules; it cannot know what
a value *means*. The safe version names the columns, so the pairing is explicit:

```sql
INSERT INTO attendee (id, full_name, email)
VALUES ('34343434-3434-3434-3434-343434343434', 'Asha Rao', 'asha@example.com');
```

### Your doubts

None for this lesson.

---

## Lesson 2 — `RETURNING`: getting back values the database filled in

### Definition

`RETURNING` is a clause you add to the end of an `INSERT`. A **clause** is one named part of a
statement, like `VALUES (...)`. `RETURNING` makes the statement hand back columns from the row
it just stored. You get them from the same statement, so you need no second query.

### Why it is needed

Some values in a new row are chosen by the database, not by you:

- **An id made by `gen_random_uuid()`.** `gen_random_uuid()` is a Postgres function. Each call
  makes a brand-new random uuid. Put inside a value list, it runs during the insert, so nobody
  knows the value until then.
- **A `created_at` filled in by its default, `now()`.**

Your program often needs such a value straight away. The id matters most, because a child row
must store its parent's id: a new event must store its venue's id in `venue_id`.

Without `RETURNING`, the command tag says one row went in, but not which id that row got:

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000);
INSERT 0 1
```

So you need a second query to find "the row I just inserted". Both obvious ways fail.

**Search by name.** It works while there is one Harbour Dome:

```text
SELECT id, name, city FROM venue WHERE name = 'Harbour Dome';
                  id                  |     name     | city
--------------------------------------+--------------+-------
 a9888dcb-8e12-4ba2-a11c-41058183a8e5 | Harbour Dome | Kochi
(1 row)
```

Then another user adds a different venue with the same name:

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Harbour Dome', 'Goa', 3000);
INSERT 0 1

SELECT id, name, city FROM venue WHERE name = 'Harbour Dome';
                  id                  |     name     | city
--------------------------------------+--------------+-------
 a9888dcb-8e12-4ba2-a11c-41058183a8e5 | Harbour Dome | Kochi
 09114dfa-efbd-4473-9ae5-1878e618478b | Harbour Dome | Goa
(2 rows)
```

Venue names are not unique, so the search cannot tell you which venue is yours.

**Take the newest row.**

```text
SELECT id, name, city FROM venue ORDER BY created_at DESC LIMIT 1;
                  id                  |     name     | city
--------------------------------------+--------------+------
 09114dfa-efbd-4473-9ae5-1878e618478b | Harbour Dome | Goa
(1 row)
```

That is the other user's venue in Goa. On a busy system, someone can always insert a row
between your two queries.

Both tries also cost a second **round trip**: one message from your program to the database,
plus the answer coming back. Each one takes time. `RETURNING` removes both the guessing and the
second round trip.

### Key points

- `RETURNING column_a, column_b` at the end of an `INSERT` hands back those columns of the row
  that was just stored.
- psql prints the returned rows first, like a `SELECT` result, then the command tag.
- `RETURNING *` hands back every column.
- A refused insert hands back nothing. You get `ERROR`, as always.
- It works whether the id comes from `gen_random_uuid()` in the value list or from a column
  default such as `DEFAULT gen_random_uuid()`.
- It replaces "find my row" queries, which can return several rows or someone else's row.
- One round trip instead of two.

### How it works, step by step

The shape:

```sql
INSERT INTO table_name (column_1, column_2)
VALUES (value_1, value_2)
RETURNING column_a, column_b;
```

1. Postgres builds the row. It uses your values, runs any functions in the value list (like
   `gen_random_uuid()`), and fills in defaults.
2. It checks every constraint and stores the row.
3. It reads the columns named after `RETURNING`, taken from the row it just stored.
4. It sends them back as a small result table, just like the result of a `SELECT`.
5. psql prints that result table. Then it prints the command tag, `INSERT 0 1`, as usual.

### Example — real run

A venue whose id the database makes, with that id asked for back:

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
RETURNING id;
                  id
--------------------------------------
 7faad325-758d-445f-abba-7b93c29bf43d
(1 row)

INSERT 0 1
```

A child event for that venue. Its `venue_id` holds the id the venue insert returned,
`7faad325-758d-445f-abba-7b93c29bf43d`:

```text
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '7faad325-758d-445f-abba-7b93c29bf43d', 'Harbour Lights Festival', '2027-01-15 18:00+05:30')
RETURNING id, venue_id, name, created_at;
                  id                  |               venue_id               |          name           |          created_at
--------------------------------------+--------------------------------------+-------------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000005 | 7faad325-758d-445f-abba-7b93c29bf43d | Harbour Lights Festival | 2026-10-25 11:19:14.497104+00
(1 row)

INSERT 0 1
```

The venue's id is random on purpose: this subtask is about getting a database-made id back.
Your run will print a different uuid. The event's id is hand-picked, like the ids in the
starting data, so later lessons can refer to this event easily.

### Walkthrough

**The venue insert:** `INSERT INTO venue (id, name, city, capacity) VALUES (gen_random_uuid(),
'Harbour Dome', 'Kochi', 5000) RETURNING id;`

1. Postgres pairs values with columns by position. The first value is not a fixed value. It is
   a call to `gen_random_uuid()`.
2. Postgres runs that function and gets a new random uuid,
   `7faad325-758d-445f-abba-7b93c29bf43d`. That becomes the row's `id`.
3. Postgres fills in the other columns. `seat_map_hints` has no default, so it gets `NULL`.
   `created_at` gets `now()`.
4. Every constraint passes: the id is new, no required column is `NULL`, and 5000 is greater
   than 0. The row is stored.
5. `RETURNING id` reads `id` from the stored row and sends it back.
6. psql prints a one-row result holding that id. Then it prints `INSERT 0 1`.

**The event insert:** `INSERT INTO event (id, venue_id, name, starts_at) VALUES
('aaaaaaaa-0000-0000-0000-000000000005', '7faad325-758d-445f-abba-7b93c29bf43d', 'Harbour
Lights Festival', '2027-01-15 18:00+05:30') RETURNING id, venue_id, name, created_at;`

1. `venue_id` holds the id that the venue insert returned.
2. `venue_id` has a **foreign key**: a rule that its value must be the id of a venue that
   exists. Postgres looks in `venue` for `7faad325-...` and finds Harbour Dome, stored a moment
   earlier. The rule passes.
3. Postgres stores the row, and `RETURNING` hands back four columns. `created_at` is the one
   value nobody typed.

Two statements, two round trips, and no lookup query in between.

### Important terms

| Term | Meaning |
| --- | --- |
| **Clause** | One named part of a statement, like `VALUES (...)` or `RETURNING id`. |
| **`RETURNING`** | A clause at the end of an `INSERT` that hands back columns of the row just stored. |
| **`gen_random_uuid()`** | A Postgres function that makes a brand-new random uuid each time it runs. |
| **Round trip** | One message from your program to the database, plus the answer coming back. |
| **Result table** | The rows and columns a statement hands back, printed by psql like a `SELECT` result. |
| **Parent row, child row** | A child row stores the id of its parent row. An event stores its venue's id. |
| **Foreign key** | A rule that a column must hold the id of a row that exists in another table. |
| **`RETURNING *`** | Hands back every column of the row just stored. |

### Common mistakes

**Copying the id with a typo.** One wrong character, and the child row points at a venue that
does not exist. The foreign key refuses it:

```text
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000006', '7faad325-758d-445f-abba-7b93c29bf43e', 'Harbour Lights Afterparty', '2027-01-16 22:00+05:30')
RETURNING id, venue_id;
ERROR:  insert or update on table "event" violates foreign key constraint "event_venue_fk"
DETAIL:  Key (venue_id)=(7faad325-758d-445f-abba-7b93c29bf43e) is not present in table "venue".
```

| Part | Plain English |
| --- | --- |
| `insert or update on table "event"` | The row you tried to add to `event`. |
| `violates foreign key constraint "event_venue_fk"` | It broke the rule that `venue_id` must point at a real venue. `event_venue_fk` is that rule's name. |
| `Key (venue_id)=(...) is not present in table "venue"` | No venue has this id. The typed id ends in `e`; the real one ends in `d`. |

**Copying the wrong id.** Worse, because nothing stops it. Here Riverside Arena's id was pasted
by accident:

```text
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'Harbour Lights Afterparty', '2027-01-16 22:00+05:30')
RETURNING id, venue_id;
                  id                  |               venue_id
--------------------------------------+--------------------------------------
 aaaaaaaa-0000-0000-0000-000000000007 | 11111111-1111-1111-1111-111111111111
(1 row)

INSERT 0 1

SELECT e.name AS event_name, v.name AS venue_name, v.city
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.id = 'aaaaaaaa-0000-0000-0000-000000000007';
        event_name         |   venue_name    |   city
---------------------------+-----------------+-----------
 Harbour Lights Afterparty | Riverside Arena | Hyderabad
(1 row)
```

The Kochi afterparty now belongs to a venue in Hyderabad. The foreign key only checks that the
venue exists, not that it is the venue you meant. In real code this risk disappears, because
the program puts the returned id straight into a variable and nobody copies it by hand.

**Expecting a value back from a refused insert.** A failed insert hands back nothing:

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Pop-up Stage', 'Goa', 0)
RETURNING id;
ERROR:  new row for relation "venue" violates check constraint "venue_capacity_positive"
DETAIL:  Failing row contains (244464ee-0498-4438-b734-620da139ae82, Pop-up Stage, Goa, 0, null, 2026-10-25 11:19:14.502617+00).
```

The `DETAIL` line shows the database did make an id, `244464ee-...`. That id was thrown away
with the refused row. It was never stored and never returned.

**Finding the new row with a second query.** A search on a column that is not unique, like a
venue name, can return several rows. "The newest row" can be another user's row. Use
`RETURNING` instead.

### Edge cases

**`RETURNING *` hands back every column.** Real output, captured in a practice copy of the
database, so this venue is not in your tables:

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Fort Pavilion', 'Jaipur', 2500)
RETURNING *;
                  id                  |     name      |  city  | capacity | seat_map_hints |          created_at
--------------------------------------+---------------+--------+----------+----------------+-------------------------------
 66525dd9-ea0b-4926-961e-7ef4486ecc9f | Fort Pavilion | Jaipur |     2500 |                | 2026-10-25 11:28:21.000187+00
(1 row)

INSERT 0 1
```

The empty `seat_map_hints` cell is a `NULL`: the column has no default.

**An id made by a column default.** Some tables give the id column a default, so the insert
leaves `id` out entirely. `RETURNING` is still how you learn the value:

```text
CREATE TABLE playlist (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO playlist (title) VALUES ('Road Trip') RETURNING id;
                  id
--------------------------------------
 501a6316-060d-4b56-b736-a4c7d6d45c12
(1 row)

INSERT 0 1
```

**Your code may make the uuid itself.** Then it knows the id before inserting, but it still
cannot know `created_at` in advance. `RETURNING created_at` hands that back.

**`RETURNING` also works on `UPDATE` and `DELETE`.** Later lessons use it there.

### Where it is used

- **Creating a parent with its children.** An order and its order lines, a venue and its
  events, an event and its price tiers. The parent's id comes back from `RETURNING`, and every
  child insert uses it.
- **An API that creates things.** A `POST /venues` request creates a venue. The server replies
  with status `201 Created` and a `Location` header holding the new venue's address, like
  `/venues/7faad325-...`. That id comes from `RETURNING`.
- **Java.** The database library hands the returned columns to your code like a query result;
  your code reads the id into a variable and passes it to the next insert. On Postgres, Java's
  standard "give me the generated keys" feature works this way: the driver quietly adds
  `RETURNING` to your `INSERT`.

### Link to the task

This lesson is **S1**: *"Insert a venue with `INSERT ... RETURNING id`. Note that you now have
the generated id without a second round trip, which matters when you are about to insert child
rows."* The venue insert is S1 itself; the event insert is the "child rows" part. Event
`aaaaaaaa-0000-0000-0000-000000000005` is used again in later lessons.

### Do it yourself

These steps change your real `zenith` data. Keep the new venue and event: later lessons use
them.

**Step 0 — check the starting state.** If the machine has no Docker, no `pg` container, no
`zenith` database, or no tables, first do Steps A to E of **Setting all of this up from
nothing** near the top of this file. Then, in psql:

```sql
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;
```

Expected before this lesson:

```text
 venues | events | tiers
--------+--------+-------
      3 |      4 |     6
(1 row)
```

If it already says `4 | 5 | 6`, these steps were done before. Skip to Step 4 to check them.

**Step 1 — open psql** (unless it is already open).

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`

**Step 2 — insert the venue and get its id back.**

```sql
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
RETURNING id;
```

Expected:

```text
                  id
--------------------------------------
 <a random uuid>
(1 row)

INSERT 0 1
```

Your uuid will differ from the one in these notes. Select it and copy it.

**Step 3 — insert the child event, using that id.** Replace `<paste the venue id here>` with
your uuid. Keep the single quotes around it.

```sql
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '<paste the venue id here>', 'Harbour Lights Festival', '2027-01-15 18:00+05:30')
RETURNING id, venue_id, name, created_at;
```

Expected: one row whose `venue_id` equals your venue's id and whose `created_at` shows the
current time in UTC, then `INSERT 0 1`.

**Step 4 — check the link.**

```sql
SELECT e.name AS event_name, v.name AS venue_name, v.city
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.id = 'aaaaaaaa-0000-0000-0000-000000000005';
```

Expected:

```text
       event_name        |  venue_name  | city
-------------------------+--------------+-------
 Harbour Lights Festival | Harbour Dome | Kochi
(1 row)
```

**Step 5 — check the counts.** Run the count query from Step 0 again. Expected: `4 | 5 | 6`.

If something goes wrong:

| What you see | What it means | What to do |
| --- | --- | --- |
| `violates foreign key constraint "event_venue_fk"` in Step 3 | The pasted id matches no venue. | Copy the id from Step 2's result again, carefully, and rerun Step 3. |
| `duplicate key value violates unique constraint "event_pkey"` in Step 3 | An event with id `aaaaaaaa-0000-0000-0000-000000000005` already exists: Step 3 ran before. | Nothing to fix. Run Step 4 to check it. |
| You ran Step 2 twice | Two Harbour Dome venues exist, with different ids. Nothing stopped it, because venue names are not unique. | Remove the one whose id you did not use in Step 3: `DELETE FROM venue WHERE id = '<that id>';` (`DELETE` removes rows; a later lesson covers it.) |
| The constraint is called `event_venue_id_fkey`, not `event_venue_fk` | Your tables were built without hand-picked constraint names. | Nothing. The rule behaves the same. |

**Done when:** you got the new venue's id from `RETURNING` and inserted an event that points at
it, without running any query to look the id up.

### Summary for revision

- `INSERT ... RETURNING columns;` hands back columns of the stored row in the same statement.
- psql prints the returned rows, then the command tag. `RETURNING *` returns every column.
- Use it for values the database chooses: ids from `gen_random_uuid()` or an id default, and
  `created_at` from `now()`.
- It replaces the second "find my row" query. That query can return several rows or another
  user's row, and it costs an extra round trip.
- A refused insert returns nothing. An id made for a refused row is thrown away.
- The foreign key checks that the parent exists, not that it is the right parent. Pass the
  returned id along in code; do not retype it.

### Check questions, with answers

**Question 1.** Here are two tables:

```sql
CREATE TABLE playlist (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE playlist_song (
    playlist_id uuid NOT NULL REFERENCES playlist(id),
    song        text NOT NULL
);
```

An app creates a playlist called 'Road Trip', then adds two songs to it. The developer writes
these two statements:

```sql
INSERT INTO playlist (title) VALUES ('Road Trip');
SELECT id FROM playlist WHERE title = 'Road Trip';
```

The app takes the id from the `SELECT` and uses it in both `playlist_song` inserts.

(a) Describe a real situation where that `SELECT` returns the wrong id, or more than one id.
(b) Rewrite the `INSERT` so the `SELECT` is not needed. What does psql print after your
statement?

**Answer.**

(a) Any time a second playlist has the same title. Two users each make a 'Road Trip' playlist,
or one user makes two. Titles are not unique, so the `SELECT` returns both ids. Real output:

```text
INSERT INTO playlist (title) VALUES ('Road Trip');
INSERT 0 1

SELECT id FROM playlist WHERE title = 'Road Trip';
                  id
--------------------------------------
 dbdfc5b3-81a9-4e4e-9f03-df69c8dde26a
(1 row)

INSERT INTO playlist (title) VALUES ('Road Trip');
INSERT 0 1

SELECT id FROM playlist WHERE title = 'Road Trip';
                  id
--------------------------------------
 dbdfc5b3-81a9-4e4e-9f03-df69c8dde26a
 3ccad5c6-7402-4483-848e-b17fb826b9c9
(2 rows)
```

If the app takes the first id, the songs go into the older playlist — possibly another user's.
Taking "the newest" instead does not help: another user's insert can land between your
`INSERT` and your `SELECT`.

(b) Add `RETURNING id`:

```sql
INSERT INTO playlist (title) VALUES ('Road Trip') RETURNING id;
```

psql prints a one-row result holding the new id, then the command tag:

```text
                  id
--------------------------------------
 501a6316-060d-4b56-b736-a4c7d6d45c12
(1 row)

INSERT 0 1
```

`id` is not in the column list, so its default `gen_random_uuid()` made the value, and
`RETURNING` handed back exactly this row's id. The app uses it for both `playlist_song` inserts.
One round trip, no guessing.

**Question 2.** Here is a table. It is empty.

```sql
CREATE TABLE gig (
    id         uuid PRIMARY KEY,
    name       text NOT NULL,
    capacity   integer NOT NULL CHECK (capacity > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
```

Someone runs this statement:

```sql
INSERT INTO gig (id, name, capacity)
VALUES (gen_random_uuid(), 'Rooftop Session', 0)
RETURNING id, created_at;
```

What does psql print? Does any id come back to the caller? How many rows does `gig` hold
afterwards?

**Answer.** psql prints an error, and no result table and no command tag:

```text
ERROR:  new row for relation "gig" violates check constraint "gig_capacity_check"
DETAIL:  Failing row contains (4bd3c48c-b042-4b63-b832-ac3abbf32d32, Rooftop Session, 0, 2026-10-25 11:28:20.999284+00).
```

No id comes back. `RETURNING` only hands back rows that were stored, and this row broke
`CHECK (capacity > 0)`. The `DETAIL` line shows that `gen_random_uuid()` did make an id and
`now()` did fill `created_at`, but that row was thrown away. The table holds **0** rows:

```text
SELECT count(*) FROM gig;
 count
-------
     0
(1 row)
```

### Your doubts

None for this lesson.

---

## Lesson 3 — Several rows in one `INSERT`

### Definition

One `INSERT` statement can add several rows at once. After the word `VALUES`, you write several
bracketed lists of values, with a comma between each one. Each bracketed list is called a
**tuple**. A tuple holds the values for one row, in the same order as the column list. Three
tuples make three rows. Postgres treats the whole statement as one unit: either every row is
stored, or none is.

### Why it is needed

Several rows often belong together. A new event needs its whole price list — for example
General, Gold and Platinum. Sending three separate `INSERT` statements causes two problems.

**More round trips.** A round trip is one message from your program to the database, plus the
answer coming back. Each one takes time. Three statements cost three round trips.

**A half-finished price list.** Each separate statement stands alone. If one fails, the others
stay stored. A real run, in a scratch table (a temporary table made for one experiment and
dropped afterwards):

```sql
CREATE TABLE scratch_tier (
    id     uuid PRIMARY KEY,
    name   text NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount >= 0)
);
```

Three separate statements. The third has a typo: a minus sign before the amount.

```text
INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000001', 'General', 799);
INSERT 0 1
INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000002', 'Gold', 1999);
INSERT 0 1
INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000003', 'Platinum', -3999);
ERROR:  new row for relation "scratch_tier" violates check constraint "scratch_tier_amount_check"
DETAIL:  Failing row contains (cccccccc-0000-0000-0000-000000000003, Platinum, -3999.0000).

SELECT id, name, amount FROM scratch_tier ORDER BY amount;
                  id                  |  name   |  amount
--------------------------------------+---------+-----------
 cccccccc-0000-0000-0000-000000000001 | General |  799.0000
 cccccccc-0000-0000-0000-000000000002 | Gold    | 1999.0000
(2 rows)
```

General and Gold are stored; Platinum is not. Customers would see an incomplete price list until
someone notices, and someone must work out which statements worked. A multi-row `INSERT` fixes
both problems: one statement, one round trip, and all-or-nothing.

### Key points

- One `INSERT` can carry several tuples after `VALUES`, separated by commas.
- One column list serves every tuple. Every tuple needs exactly as many values as it has
  columns.
- No comma after the last tuple.
- **All-or-nothing:** if any row breaks a constraint, no row from the statement is stored.
- Success prints one command tag whose last number is the number of rows added, such as
  `INSERT 0 3`.
- Separate statements are separate: psql runs each one, a failure does not stop the next one,
  and the ones that succeeded stay stored.
- `RETURNING` hands back one row per tuple.

### How it works, step by step

The shape:

```sql
INSERT INTO table_name (column_1, column_2)
VALUES
    (value_1a, value_2a),
    (value_1b, value_2b),
    (value_1c, value_2c);
```

1. Postgres reads the whole statement first. If the shape is broken — a comma missing, for
   example — it refuses before building any row.
2. It builds one row from each tuple, in order. Values pair with the column list by position.
   Columns left out of the column list get their default.
3. It stores the rows and checks every **constraint** (a rule the table enforces, like
   `CHECK (amount >= 0)`) on them.
4. If any row breaks a constraint, Postgres throws away every row this statement stored. psql
   prints `ERROR` and no command tag. The table looks exactly as before the statement.
5. If every row passes, psql prints one command tag, such as `INSERT 0 3`. The last number is
   the **affected row count**: how many rows the statement added.

Step 4 is called **all-or-nothing**: either every row of the statement is stored, or none is.
Postgres guarantees it for every single statement. Database books call this being **atomic**.

### Example — real run

S2: the event `aaaaaaaa-0000-0000-0000-000000000005` (Harbour Lights Festival, created in S1)
has no price tiers yet. One statement gives it three:

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000005', 'General',  799),
('bbbbbbbb-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000005', 'Gold',     1999),
('bbbbbbbb-0000-0000-0000-000000000009', 'aaaaaaaa-0000-0000-0000-000000000005', 'Platinum', 3999);
INSERT 0 3

SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY amount;
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000007 | General  |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold     | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(3 rows)
```

Three separate statements print `INSERT 0 1` three times. This one statement printed
`INSERT 0 3`, once.

One bad tuple. This statement tries to add two more tiers; the second has a minus sign by
mistake:

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Student', 499),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000005', 'Family',  -1999);
ERROR:  new row for relation "price_tier" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (bbbbbbbb-0000-0000-0000-000000000011, aaaaaaaa-0000-0000-0000-000000000005, Family, -1999.0000, 2026-10-25 11:30:35.693592+00).

SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY amount;
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000007 | General  |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold     | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(3 rows)
```

Student was a valid tuple. It is still missing, because the whole statement was thrown away.

### Walkthrough

**The S2 insert** — three tuples for event `aaaaaaaa-0000-0000-0000-000000000005`: General 799,
Gold 1999, Platinum 3999.

1. One column list: `id`, `event_id`, `name`, `amount`. `created_at` is left out, so each row
   gets its default, `now()`.
2. Three tuples, a comma between each, none after the last.
3. Postgres builds three rows. Row 1: id `...07`, event `...05`, General, 799. Rows 2 and 3 the
   same way.
4. Every constraint passes for every row:
   - each id is new (the primary key rule);
   - event `...05` exists (the foreign key rule: `event_id` must be the id of an existing event);
   - every amount is 0 or more (`CHECK (amount >= 0)`);
   - each pair of event and name is new (one event cannot have two tiers with the same name).
5. All three rows are stored. psql prints `INSERT 0 3`. The `3` is the affected row count.

**The bad-tuple insert** — Student 499 and Family -1999 for the same event.

1. Tuple 1 (Student, 499) passes every constraint.
2. Tuple 2 (Family, -1999) breaks `CHECK (amount >= 0)`, named
   `price_tier_amount_non_negative`.
3. Postgres throws away the Student row too.
4. psql prints `ERROR` and no command tag. The `SELECT` proves it: still three tiers.

| Part | Plain English |
| --- | --- |
| `new row for relation "price_tier"` | A row this statement tried to add to `price_tier`. "Relation" is Postgres's formal word for a table. |
| `violates check constraint "price_tier_amount_non_negative"` | It broke the rule that the amount must be 0 or more. |
| `DETAIL: Failing row contains (..., Family, -1999.0000, ...)` | The broken row. Only this row is named, but every row of the statement was thrown away. |

### Important terms

| Term | Meaning |
| --- | --- |
| **Tuple** | One bracketed list of values after `VALUES`. It becomes one row. Postgres's error messages call tuples "VALUES lists". |
| **Multi-row `INSERT`** | One `INSERT` statement carrying several tuples. |
| **All-or-nothing** | Every row of a statement is stored, or none is. Postgres guarantees it for every statement. |
| **Atomic** | The formal word for all-or-nothing. |
| **Affected row count** | The last number in the command tag: how many rows the statement added, changed or removed. |
| **Round trip** | One message from your program to the database, plus the answer coming back. |
| **Constraint** | A rule the table enforces on every row, like `CHECK (amount >= 0)`. |
| **Batching** | A Java database library collecting many statements and sending them together. |
| **`COPY`** | Postgres's separate command for bulk-loading very many rows. |

### Common mistakes

**A comma after the last tuple.**

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', 299),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000004', 'Sofa',     599),
;
ERROR:  syntax error at or near ";"
LINE 4: ;
        ^
```

The last comma promised one more tuple; Postgres reached the `;` instead. Remove the comma after
the last tuple.

**A missing comma between tuples.**

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', 299)
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000004', 'Sofa',     599);
ERROR:  syntax error at or near "("
LINE 3: ('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000...
        ^
```

After the first tuple, Postgres expected a comma or the end of the statement, and found the `(`
of the next tuple. The caret points at that `(`; the missing comma belongs at the end of the
line before it.

**A tuple with a value missing.**

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', 299),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000004', 'Sofa');
ERROR:  VALUES lists must all be the same length
LINE 3: ('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000...
         ^
```

"VALUES lists" means tuples. The first tuple has four values; the second has three (its amount
is missing). The caret points at the short tuple.

**The same name twice in one statement.** Event `aaaaaaaa-0000-0000-0000-000000000004` is Open
Mic Night, which has no tiers:

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', 299),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', 349);
ERROR:  duplicate key value violates unique constraint "price_tier_name_unique_per_event"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000004, Standing) already exists.

SELECT count(*) FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000004';
 count
-------
     0
(1 row)
```

"Already exists", yet there was no Standing tier before the statement. The first tuple of this
same statement stored it a moment earlier; the second tuple collided with it; Postgres threw
away the whole statement, including the first Standing row.

**Re-running only the bad tuple.** After an error, nothing from that statement is stored. Fix
the bad tuple and re-run the whole statement, every tuple. Re-running only the fixed tuple
stores only that one row.

### Edge cases

**The error names only the first broken row Postgres found.** Both tuples below are broken,
but only Standing is named:

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000004', 'Standing', -299),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000004', 'Sofa',     -599);
ERROR:  new row for relation "price_tier" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (bbbbbbbb-0000-0000-0000-000000000010, aaaaaaaa-0000-0000-0000-000000000004, Standing, -299.0000, 2026-10-25 11:34:19.822061+00).
```

After fixing one broken tuple, run the statement again; a second broken tuple may show up then.

**`RETURNING` hands back one row per tuple.** Two tuples into the scratch table
`scratch_tier (id uuid PRIMARY KEY, name text NOT NULL, amount numeric(19,4) NOT NULL CHECK (amount >= 0))`:

```text
INSERT INTO scratch_tier (id, name, amount) VALUES
('cccccccc-0000-0000-0000-000000000004', 'Silver', 1299),
('cccccccc-0000-0000-0000-000000000005', 'Bronze', 599)
RETURNING id, name;
                  id                  |  name
--------------------------------------+--------
 cccccccc-0000-0000-0000-000000000004 | Silver
 cccccccc-0000-0000-0000-000000000005 | Bronze
(2 rows)

INSERT 0 2
```

The rows usually come back in tuple order, but Postgres does not promise that order. If code
must match each returned row to its tuple, return a column that tells them apart, like `name`.

**Repeating the whole statement.** If a multi-row insert succeeded and is run again, the first
tuple's id already exists, so the whole second run is refused — and, being all-or-nothing, it
stores nothing:

```text
ERROR:  duplicate key value violates unique constraint "price_tier_pkey"
DETAIL:  Key (id)=(bbbbbbbb-0000-0000-0000-000000000007) already exists.
```

### Where it is used

- **Seed scripts.** The setup script near the top of this file fills the three tables with
  three multi-row inserts. That is why it prints `INSERT 0 3`, `INSERT 0 4` and `INSERT 0 6`.
- **Forms that create several things at once.** An organiser saves an event's whole price list
  in one form. The tiers go in as one statement, so the event never has half a price list.
- **Imports.** A file of 10,000 attendees loaded 500 tuples per statement takes 20 round trips
  instead of 10,000.
- **Java.** JDBC, Java's standard library for talking to databases, can collect many inserts
  and send them together (batching). The Postgres driver for Java has a setting,
  `reWriteBatchedInserts=true`, that turns such a batch into multi-row inserts.
- **Very large loads.** For millions of rows, Postgres has the separate bulk-loading command
  `COPY`. Multi-row inserts suit tens to thousands of rows.

### Link to the task

This lesson is **S2**: *"Insert several rows in one statement with multiple `VALUES` tuples,
and compare the reported row count."* The S2 insert is the subtask. Comparing the counts: one
statement reported `INSERT 0 3`; three separate statements report `INSERT 0 1` three times.
Keep the three tiers: later lessons change and delete some of them.

### Do it yourself

These steps change your real `zenith` data.

**Step 0 — check the starting state.** If the machine has no Docker, no `pg` container, no
`zenith` database, or no tables, first do Steps A to E of **Setting all of this up from
nothing** near the top of this file. Then open psql
(`docker exec -it pg psql -U postgres -d zenith`) and run:

```sql
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;
```

Expected: `4 | 5 | 6`.

- **`9` tiers:** S2 is already done. Skip to Step 2 to check it.
- **`3 | 4 | 6`:** S1 is not done yet. Run these two statements first. In the second, replace
  `<paste the venue id here>` with the id the first one returns, and keep the quotes.

  ```sql
  INSERT INTO venue (id, name, city, capacity)
  VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
  RETURNING id;

  INSERT INTO event (id, venue_id, name, starts_at)
  VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '<paste the venue id here>', 'Harbour Lights Festival', '2027-01-15 18:00+05:30');
  ```

  Expected: a one-row result holding a random id, then `INSERT 0 1`; then `INSERT 0 1` for the
  event.

**Step 1 — insert three price tiers in one statement.**

```sql
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000005', 'General',  799),
('bbbbbbbb-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000005', 'Gold',     1999),
('bbbbbbbb-0000-0000-0000-000000000009', 'aaaaaaaa-0000-0000-0000-000000000005', 'Platinum', 3999);
```

Expected: `INSERT 0 3`

**Step 2 — look at them.**

```sql
SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY amount;
```

Expected:

```text
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000007 | General  |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold     | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(3 rows)
```

**Step 3 — see all-or-nothing for yourself.**

```sql
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Student', 499),
('bbbbbbbb-0000-0000-0000-000000000011', 'aaaaaaaa-0000-0000-0000-000000000005', 'Family',  -1999);
```

Expected failure:

```text
ERROR:  new row for relation "price_tier" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (bbbbbbbb-0000-0000-0000-000000000011, aaaaaaaa-0000-0000-0000-000000000005, Family, -1999.0000, <your current time>).
```

Run the `SELECT` from Step 2 again. Expected: the same three rows, no Student row.

**Step 4 — check the counts.** Run the count query from Step 0 again. Expected: `4 | 5 | 9`.

**Step 5 — compare with separate statements, in a scratch table.**

```sql
CREATE TABLE scratch_tier (
    id     uuid PRIMARY KEY,
    name   text NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount >= 0)
);

INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000001', 'General', 799);
INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000002', 'Gold', 1999);
INSERT INTO scratch_tier (id, name, amount) VALUES ('cccccccc-0000-0000-0000-000000000003', 'Platinum', -3999);

SELECT id, name, amount FROM scratch_tier ORDER BY amount;
```

Expected, in order: `CREATE TABLE`; `INSERT 0 1`; `INSERT 0 1`;
`ERROR:  new row for relation "scratch_tier" violates check constraint "scratch_tier_amount_check"`;
then a 2-row result — General and Gold, no Platinum. psql ran all three inserts one after
another; the failed one did not stop the others.

**Step 6 — clean up the scratch table.**

```sql
DROP TABLE scratch_tier;
```

Expected: `DROP TABLE`. Keep the three price tiers from Step 1: later lessons use them.

If something goes wrong:

| What you see | What it means | What to do |
| --- | --- | --- |
| `violates foreign key constraint "price_tier_event_fk"` in Step 1 | Event `aaaaaaaa-0000-0000-0000-000000000005` does not exist: S1 is not done. | Run Step 0's two S1 statements, then re-run Step 1. |
| `duplicate key value violates unique constraint "price_tier_pkey"` with `Key (id)=(bbbbbbbb-0000-0000-0000-000000000007) already exists` in Step 1 | Step 1 ran before. Because of all-or-nothing, this second run stored nothing. | Nothing to fix. Run Step 2 to check. |
| `syntax error at or near ";"` or `syntax error at or near "("` | A comma is extra or missing. | Compare your commas with Step 1. |
| `VALUES lists must all be the same length` | A tuple lost a value while copying. | Copy the statement again. |
| `relation "scratch_tier" already exists` in Step 5 | An old scratch table was left behind. | Run `DROP TABLE scratch_tier;` and repeat Step 5. |
| Names like `price_tier_amount_check` or `price_tier_event_id_fkey` | Your tables were built without hand-picked constraint names. | Nothing. The rules behave the same. |

**Done when:** one statement printed `INSERT 0 3`, the bad-tuple statement stored nothing, and
you can say why three separate statements left a half-finished price list.

### Summary for revision

- `INSERT INTO t (columns) VALUES (tuple), (tuple), (tuple);` adds several rows in one
  statement. One column list; commas between tuples; none after the last.
- The command tag counts the rows added: `INSERT 0 3`.
- All-or-nothing (atomic): one broken row means no row from the statement is stored. Fix it
  and re-run the whole statement.
- Separate statements are independent: the good ones stay stored, leaving a half-done result.
- Fewer statements means fewer round trips.
- Shape errors (`syntax error`, `VALUES lists must all be the same length`) are caught before
  any row is built. The error names only the first broken row found.
- `RETURNING` returns one row per tuple; do not rely on their order.

### Check questions, with answers

**Question 1.** Here is a table. It is empty.

```sql
CREATE TABLE seat (
    id        uuid PRIMARY KEY,
    row_label text NOT NULL,
    seat_no   integer NOT NULL CHECK (seat_no > 0)
);
```

(a) Someone runs this one statement:

```sql
INSERT INTO seat (id, row_label, seat_no) VALUES
('dddddddd-0000-0000-0000-000000000001', 'A', 1),
('dddddddd-0000-0000-0000-000000000002', 'A', 2),
('dddddddd-0000-0000-0000-000000000003', 'A', 0),
('dddddddd-0000-0000-0000-000000000004', 'A', 4);
```

What does psql print? How many rows does `seat` hold afterwards?

(b) On a fresh, empty `seat` table, someone pastes these four separate statements into psql,
all at once:

```sql
INSERT INTO seat (id, row_label, seat_no) VALUES ('dddddddd-0000-0000-0000-000000000001', 'A', 1);
INSERT INTO seat (id, row_label, seat_no) VALUES ('dddddddd-0000-0000-0000-000000000002', 'A', 2);
INSERT INTO seat (id, row_label, seat_no) VALUES ('dddddddd-0000-0000-0000-000000000003', 'A', 0);
INSERT INTO seat (id, row_label, seat_no) VALUES ('dddddddd-0000-0000-0000-000000000004', 'A', 4);
```

What does psql print for each statement? How many rows does `seat` hold afterwards, and which
seats are they?

**Answer.**

(a) An error, and no command tag:

```text
ERROR:  new row for relation "seat" violates check constraint "seat_seat_no_check"
DETAIL:  Failing row contains (dddddddd-0000-0000-0000-000000000003, A, 0).
```

The table holds **0** rows. The third tuple broke `CHECK (seat_no > 0)`, so Postgres threw away
the whole statement: seats A1 and A2, which were valid, and seat A4 too. All-or-nothing.

(b) Four replies, one per statement:

```text
INSERT 0 1
INSERT 0 1
ERROR:  new row for relation "seat" violates check constraint "seat_seat_no_check"
DETAIL:  Failing row contains (dddddddd-0000-0000-0000-000000000003, A, 0).
INSERT 0 1
```

The table holds **3** rows:

```text
SELECT row_label, seat_no FROM seat ORDER BY seat_no;
 row_label | seat_no
-----------+---------
 A         |       1
 A         |       2
 A         |       4
(3 rows)
```

Each statement is all-or-nothing on its own, but nothing ties the four together. psql ran them
one after another, and the failed third statement did not stop the fourth. The result is a row
with a gap: no seat 3.

**Question 2.** Here is a table. It is empty.

```sql
CREATE TABLE badge (
    id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    label text NOT NULL UNIQUE
);
```

(a) Someone runs:

```sql
INSERT INTO badge (label) VALUES ('Speaker'), ('Volunteer'), ('Speaker')
RETURNING id, label;
```

What does psql print? Do any ids come back? How many rows does `badge` hold afterwards?

(b) They delete the second `('Speaker')` tuple from the statement and run it again. What does
psql print now?

**Answer.**

(a) An error, with no result table and no command tag:

```text
ERROR:  duplicate key value violates unique constraint "badge_label_key"
DETAIL:  Key (label)=(Speaker) already exists.
```

No ids come back: `RETURNING` only hands back stored rows, and none were stored. The table
holds **0** rows. The first `('Speaker')` tuple stored a row a moment earlier; the third tuple
collided with it on the `UNIQUE` rule; Postgres threw away the whole statement, including the
first Speaker and Volunteer. The name `badge_label_key` was made by Postgres from the table,
the column, and the word `key`, because the `UNIQUE` rule was written without a name.

(b) Two returned rows, then the command tag:

```text
                  id                  |   label
--------------------------------------+-----------
 3341366b-6eef-4384-94fe-5b302a5fbb14 | Speaker
 47d245e3-2ec5-4634-af2d-b9f0eb6c864c | Volunteer
(2 rows)

INSERT 0 2
```

Each id was made by the column default `gen_random_uuid()`, so a fresh run prints different
uuids. `INSERT 0 2` reports two rows added.

### Your doubts

None for this lesson.

---

## Lesson 4 — `UPDATE ... SET ... WHERE`: changing rows that already exist

### Definition

`UPDATE` changes values in rows that already exist. It has three parts:

- `UPDATE table_name` names the table.
- `SET column = new_value` says which columns change, and what they change to.
- `WHERE condition` says which rows change. It works exactly like the `WHERE` in a `SELECT`.

Rows the `WHERE` does not pick stay exactly as they were. Columns not named in `SET` keep their
current values.

### Why it is needed

Data changes after it is stored. An event gets a new name. A price goes up. A show moves to
another date. An event gets cancelled. In the reading-data task, this statement cancelled
Classical Evening by filling in its `cancelled_at` column:

```sql
UPDATE event SET cancelled_at = now() WHERE name = 'Classical Evening';
```

Why not delete the old row and insert a corrected one? Because a row's id is how other rows find
it. Harbour Lights Festival has three price tiers, and each stores the festival's id in its
`event_id` column. `UPDATE` changes the row in place: the id stays the same, so the tiers still
point at the right event.

### Key points

- `UPDATE t SET column = value WHERE condition;` changes the rows the condition picks.
- Columns not named in `SET` keep their values. Rows not picked are untouched.
- Several columns change at once with commas between the assignments — never `AND`.
- The right side of `=` may use the row's current values: `SET amount = amount + 100`.
- Constraints are checked on every updated row; one failure refuses the whole statement
  (all-or-nothing).
- The command tag is `UPDATE n`: no leftover `0`; `n` is the affected row count.
- Pick a single row by its primary key: `WHERE id = '...'` can match at most one row.
- `RETURNING` works on `UPDATE` and shows the new values.

### How it works, step by step

The shape:

```sql
UPDATE table_name
SET column_1 = new_value_1, column_2 = new_value_2
WHERE condition;
```

1. Postgres finds every row for which the `WHERE` condition is true.
2. For each of those rows it works out the **updated row**: columns named in `SET` get their new
   values; every other column keeps its current value.
3. It checks every **constraint** (a rule the table enforces, like `CHECK (amount >= 0)`) on each
   updated row.
4. If any updated row breaks a constraint, Postgres throws away the whole statement. No row
   changes, and psql prints `ERROR`. This is the same all-or-nothing rule as for `INSERT`.
5. If every updated row passes, Postgres stores them. psql prints a command tag such as
   `UPDATE 1`.

The number in `UPDATE 1` is the **affected row count**: how many rows the `WHERE` picked and the
statement updated. There is no leftover `0` in this tag — only `INSERT`'s tag has that old extra
number. So in `UPDATE 0`, the `0` really means zero rows.

**Why pick the row by id.** The id is the primary key, so no two rows share an id, and
`WHERE id = '...'` can pick at most one row. A name gives no such promise. After S2, four
different events have a price tier called General:

```text
SELECT e.name AS event_name, p.name AS tier
FROM price_tier p JOIN event e ON e.id = p.event_id
WHERE p.name = 'General'
ORDER BY e.name;
          event_name          |  tier
------------------------------+---------
 Classical Evening            | General
 Harbour Lights Festival 2027 | General
 Jazz at the Note             | General
 Monsoon Rock Night           | General
(4 rows)
```

A `WHERE name = 'General'` would pick all four. (This query ran after the S3 rename, so the
festival's name already ends in 2027.)

### Example — real run

S3 renames Harbour Lights Festival to Harbour Lights Festival 2027. Look, change, look again:

```text
SELECT id, name, starts_at, created_at FROM event
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
                  id                  |          name           |       starts_at        |          created_at
--------------------------------------+-------------------------+------------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000005 | Harbour Lights Festival | 2027-01-15 12:30:00+00 | 2026-10-25 11:30:35.681619+00
(1 row)

UPDATE event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
UPDATE 1

SELECT id, name, starts_at, created_at FROM event
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
                  id                  |             name             |       starts_at        |          created_at
--------------------------------------+------------------------------+------------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000005 | Harbour Lights Festival 2027 | 2027-01-15 12:30:00+00 | 2026-10-25 11:30:35.681619+00
(1 row)
```

The festival's price tiers, each joined to its event:

```text
SELECT e.name AS event_name, p.name AS tier, p.amount
FROM price_tier p
JOIN event e ON e.id = p.event_id
WHERE e.id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY p.amount;
          event_name          |   tier   |  amount
------------------------------+----------+-----------
 Harbour Lights Festival 2027 | General  |  799.0000
 Harbour Lights Festival 2027 | Gold     | 1999.0000
 Harbour Lights Festival 2027 | Platinum | 3999.0000
(3 rows)
```

### Walkthrough

1. `UPDATE event` names the table.
2. `SET name = 'Harbour Lights Festival 2027'` is the only assignment, so `name` is the only
   column that changes. Text values go in single quotes.
3. `WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005'` picks the rows whose id equals that value.
   The id is the primary key, so at most one row could match. Exactly one did.
4. Postgres works out the updated row: the new name, with `id`, `venue_id`, `starts_at`,
   `created_at` and `cancelled_at` unchanged.
5. Every constraint passes: the name is not `NULL`, and `venue_id` still points at a real venue.
6. Postgres stores the updated row. psql prints `UPDATE 1`: one row picked and updated.
7. Read that number. You meant to change one event; the count says one. That match is the habit
   S3 asks for.
8. The two `SELECT`s prove only the name changed: `starts_at` and `created_at` are identical.
9. The join proves the tiers followed along. They store the event's id, not its name, and the id
   did not change. Nobody touched `price_tier`.

### Important terms

| Term | Meaning |
| --- | --- |
| **`UPDATE`** | The statement that changes values in rows that already exist. |
| **`SET`** | The clause that lists which columns change and their new values. |
| **Assignment** | One `column = new_value` part of `SET`. Assignments are separated by commas. |
| **`WHERE`** | The clause that picks which rows the statement changes. |
| **Updated row** | A picked row as it will look after the change: `SET` columns new, the rest unchanged. |
| **Affected row count** | The number in `UPDATE n`: how many rows the `WHERE` picked and the statement updated. |
| **Boolean** | A true-or-false value. `AND` joins two booleans. |
| **Scratch table** | A temporary table made for one experiment and dropped afterwards. |

### Common mistakes

These first three ran against a scratch table `scratch_tier` with columns `id`, `name` and
`amount`.

**Double quotes around text.**

```text
UPDATE scratch_tier
SET name = "Gold Max"
WHERE id = 'cccccccc-0000-0000-0000-000000000002';
ERROR:  column "Gold Max" does not exist
LINE 2: SET name = "Gold Max"
                   ^
```

Single quotes mark text; double quotes mark names, like column names. Postgres looked for a
column called `Gold Max`. Write `'Gold Max'`.

**`==` instead of `=`.** Java compares with `==`; SQL compares with a single `=`.

```text
UPDATE scratch_tier
SET name = 'Gold Max'
WHERE id == 'cccccccc-0000-0000-0000-000000000002';
ERROR:  operator does not exist: uuid == unknown
LINE 3: WHERE id == 'cccccccc-0000-0000-0000-000000000002';
                 ^
HINT:  No operator matches the given name and argument types. You might need to add explicit type casts.
```

| Part | Plain English |
| --- | --- |
| `operator does not exist: uuid == unknown` | Postgres knows no `==` that compares a uuid with a quoted value. "unknown" is what Postgres calls a quoted value whose type it has not decided yet. |
| `HINT: ... add explicit type casts` | Postgres guesses the types are the problem. The real problem is `==`. Use `=`. |

**`AND` between assignments instead of a comma.**

```text
UPDATE scratch_tier
SET name = 'Gold Max' AND amount = 2499
WHERE id = 'cccccccc-0000-0000-0000-000000000002';
ERROR:  invalid input syntax for type boolean: "Gold Max"
LINE 2: SET name = 'Gold Max' AND amount = 2499
                   ^
```

Postgres read one assignment: `name = ('Gold Max' AND amount = 2499)`. `AND` joins two booleans,
so Postgres tried to read `'Gold Max'` as true-or-false, and failed. With a number first, the
message differs but the cause is the same:

```text
UPDATE scratch_tier
SET amount = 2499 AND name = 'Gold Max'
WHERE id = 'cccccccc-0000-0000-0000-000000000002';
ERROR:  argument of AND must be type boolean, not type integer
LINE 2: SET amount = 2499 AND name = 'Gold Max'
                     ^
```

Fix: `SET name = 'Gold Max', amount = 2499`.

**A new value that breaks a rule.** The whole statement is refused; nothing changes. On the real
`price_tier` table, where Harbour Lights Festival's tiers are General (`...07`), Gold (`...08`)
and Platinum (`...09`):

```text
UPDATE price_tier
SET name = 'General'
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008';
ERROR:  duplicate key value violates unique constraint "price_tier_name_unique_per_event"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000005, General) already exists.

UPDATE price_tier
SET amount = -1
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000009';
ERROR:  new row for relation "price_tier" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (bbbbbbbb-0000-0000-0000-000000000009, aaaaaaaa-0000-0000-0000-000000000005, Platinum, -1.0000, 2026-10-25 11:30:35.691982+00).
```

The `DETAIL` line shows the refused updated row, with `-1.0000` in it. Afterwards the tiers are
unchanged:

```text
SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY amount;
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000007 | General  |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold     | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(3 rows)
```

**Leaving out `WHERE` altogether.** The `UPDATE` then picks every row in the table and changes
them all. A later lesson is about exactly this.

### Edge cases

Shown on a scratch table that starts with three rows:

```sql
CREATE TABLE scratch_tier (
    id     uuid PRIMARY KEY,
    name   text NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount >= 0)
);

INSERT INTO scratch_tier (id, name, amount) VALUES
('cccccccc-0000-0000-0000-000000000001', 'General',  799),
('cccccccc-0000-0000-0000-000000000002', 'Gold',     1999),
('cccccccc-0000-0000-0000-000000000003', 'Platinum', 3999);
```

**Several columns at once** — commas between the assignments:

```text
UPDATE scratch_tier
SET name = 'Gold Plus', amount = 2299
WHERE id = 'cccccccc-0000-0000-0000-000000000002';
UPDATE 1

SELECT id, name, amount FROM scratch_tier ORDER BY amount;
                  id                  |   name    |  amount
--------------------------------------+-----------+-----------
 cccccccc-0000-0000-0000-000000000001 | General   |  799.0000
 cccccccc-0000-0000-0000-000000000002 | Gold Plus | 2299.0000
 cccccccc-0000-0000-0000-000000000003 | Platinum  | 3999.0000
(3 rows)
```

**Using the current value.** On the right side of `=`, a column name means its value before the
update:

```text
UPDATE scratch_tier
SET amount = amount + 100
WHERE id = 'cccccccc-0000-0000-0000-000000000001';
UPDATE 1

SELECT id, name, amount FROM scratch_tier ORDER BY amount;
                  id                  |   name    |  amount
--------------------------------------+-----------+-----------
 cccccccc-0000-0000-0000-000000000001 | General   |  899.0000
 cccccccc-0000-0000-0000-000000000002 | Gold Plus | 2299.0000
 cccccccc-0000-0000-0000-000000000003 | Platinum  | 3999.0000
(3 rows)
```

Running it a second time would add another 100. A later lesson comes back to that shape.

**`RETURNING` on `UPDATE`** hands back the new values:

```text
UPDATE scratch_tier
SET amount = 3499
WHERE id = 'cccccccc-0000-0000-0000-000000000003'
RETURNING id, name, amount;
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 cccccccc-0000-0000-0000-000000000003 | Platinum | 3499.0000
(1 row)

UPDATE 1
```

**Setting a column to `NULL`** empties it. The grouping task cancelled Open Mic Night and undid
it with two statements, each printing `UPDATE 1`:

```sql
UPDATE event SET cancelled_at = now() WHERE name = 'Open Mic Night';
UPDATE event SET cancelled_at = NULL WHERE name = 'Open Mic Night';
```

**Running the same `UPDATE` twice** prints `UPDATE 1` again, even though the second run changes
nothing visible:

```text
UPDATE event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
UPDATE 1
```

The count is the number of rows the `WHERE` picked, not the number whose values differed.

### Where it is used

- **Edit forms.** "Save changes" on an event's edit page becomes
  `UPDATE event SET ... WHERE id = ...`.
- **APIs.** A `PATCH` request changes part of an existing resource — for example
  `PATCH /events/aaaaaaaa-0000-0000-0000-000000000005` with a new name. The server runs an
  `UPDATE` whose `WHERE` uses the id from the URL.
- **Status changes.** A booking moves from pending to paid; an event is cancelled by filling in
  `cancelled_at`.
- **Java.** JDBC, Java's standard library for talking to databases, has `executeUpdate(...)`. It
  runs an `UPDATE` and returns the affected row count as an `int` — the same number psql prints
  after the word `UPDATE`.

### Link to the task

This lesson is **S3**: *"Update one event's name with a `WHERE` on its id. Read the `UPDATE 1`
that comes back and make a habit of checking that number every time."* The rename is the
subtask. The habit: after every `UPDATE`, compare the number with what you expected — here one
event, and `UPDATE 1`.

### Do it yourself

These steps change your real `zenith` data.

**Step 0 — check the starting state.** If the machine has no Docker, no `pg` container, no
`zenith` database, or no tables, first do Steps A to E of **Setting all of this up from
nothing** near the top of this file. Then open psql
(`docker exec -it pg psql -U postgres -d zenith`) and run:

```sql
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;
```

Expected: `4 | 5 | 9`.

- **`4 | 5 | 6`:** S2 is not done. Run this first (expected `INSERT 0 3`):

  ```sql
  INSERT INTO price_tier (id, event_id, name, amount) VALUES
  ('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000005', 'General',  799),
  ('bbbbbbbb-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000005', 'Gold',     1999),
  ('bbbbbbbb-0000-0000-0000-000000000009', 'aaaaaaaa-0000-0000-0000-000000000005', 'Platinum', 3999);
  ```

- **`3 | 4 | 6`:** S1 and S2 are not done. Run these two statements (in the second, replace
  `<paste the venue id here>` with the id the first returns, keeping the quotes), then the S2
  statement in the bullet above.

  ```sql
  INSERT INTO venue (id, name, city, capacity)
  VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
  RETURNING id;

  INSERT INTO event (id, venue_id, name, starts_at)
  VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '<paste the venue id here>', 'Harbour Lights Festival', '2027-01-15 18:00+05:30');
  ```

**Step 1 — look at the event before changing it.**

```sql
SELECT id, name, starts_at, created_at FROM event
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
```

Expected: one row named `Harbour Lights Festival`, with `starts_at` = `2027-01-15 12:30:00+00`
(6:00 PM Indian time, shown in UTC). `created_at` shows when you ran S1. Note it down.

**Step 2 — rename it.**

```sql
UPDATE event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
```

Expected: `UPDATE 1` — one row picked and updated.

**Step 3 — look again.** Run the `SELECT` from Step 1. Expected: the name is
`Harbour Lights Festival 2027`; `starts_at` and `created_at` are exactly as in Step 1.

**Step 4 — check that the price tiers followed.**

```sql
SELECT e.name AS event_name, p.name AS tier, p.amount
FROM price_tier p
JOIN event e ON e.id = p.event_id
WHERE e.id = 'aaaaaaaa-0000-0000-0000-000000000005'
ORDER BY p.amount;
```

Expected:

```text
          event_name          |   tier   |  amount
------------------------------+----------+-----------
 Harbour Lights Festival 2027 | General  |  799.0000
 Harbour Lights Festival 2027 | Gold     | 1999.0000
 Harbour Lights Festival 2027 | Platinum | 3999.0000
(3 rows)
```

**Step 5 — watch an update get refused.**

```sql
UPDATE price_tier
SET name = 'General'
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008';
```

Expected failure:

```text
ERROR:  duplicate key value violates unique constraint "price_tier_name_unique_per_event"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000005, General) already exists.
```

Run the Step 4 query again. Expected: the same three rows; Gold is still Gold.

**Step 6 (optional) — practise in a scratch table.**

```sql
CREATE TABLE scratch_tier (
    id     uuid PRIMARY KEY,
    name   text NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount >= 0)
);

INSERT INTO scratch_tier (id, name, amount) VALUES
('cccccccc-0000-0000-0000-000000000001', 'General',  799),
('cccccccc-0000-0000-0000-000000000002', 'Gold',     1999),
('cccccccc-0000-0000-0000-000000000003', 'Platinum', 3999);

UPDATE scratch_tier
SET name = 'Gold Plus', amount = 2299
WHERE id = 'cccccccc-0000-0000-0000-000000000002';

UPDATE scratch_tier
SET amount = amount + 100
WHERE id = 'cccccccc-0000-0000-0000-000000000001';

SELECT id, name, amount FROM scratch_tier ORDER BY amount;
```

Expected, in order: `CREATE TABLE`, `INSERT 0 3`, `UPDATE 1`, `UPDATE 1`, then:

```text
                  id                  |   name    |  amount
--------------------------------------+-----------+-----------
 cccccccc-0000-0000-0000-000000000001 | General   |  899.0000
 cccccccc-0000-0000-0000-000000000002 | Gold Plus | 2299.0000
 cccccccc-0000-0000-0000-000000000003 | Platinum  | 3999.0000
(3 rows)
```

Clean up with `DROP TABLE scratch_tier;` (expected: `DROP TABLE`).

If something goes wrong:

| What you see | What it means | What to do |
| --- | --- | --- |
| `UPDATE 0` in Step 2 | No row matched the `WHERE`: the id is mistyped, or event `...05` does not exist yet. | Run Step 1. If it shows `(0 rows)`, do Step 0. If it shows the row, compare the id in Step 2 character by character. |
| `UPDATE 1` again after running Step 2 twice | The `WHERE` picked the row again and set the same name again. | Nothing to fix. The count is rows picked, not rows whose values differed. |
| `column "Harbour Lights Festival 2027" does not exist` | Double quotes around the new name. | Use single quotes. |
| `operator does not exist: uuid == unknown` | `==` instead of `=`. | Use a single `=`. |
| `invalid input syntax for type boolean` | `AND` between assignments in `SET`. | Use a comma. |
| `price_tier_event_id_name_key` instead of `price_tier_name_unique_per_event` | Tables built without hand-picked constraint names. | Nothing. The rule behaves the same. |

**Done when:** you renamed the event, saw `UPDATE 1`, and confirmed with a `SELECT` that only the
name changed and the tiers still belong to the event.

### Summary for revision

- `UPDATE t SET col = value, col2 = value2 WHERE condition;` changes only the picked rows and
  only the named columns.
- Commas between assignments, never `AND`. Single quotes for text. A single `=` to compare.
- The right side may use current values (`amount = amount + 100`).
- Every updated row must pass every constraint, or nothing changes.
- `UPDATE n` — no leftover `0`; `n` is the number of rows the `WHERE` picked.
- Pick one row by its primary key. The id stays the same, so child rows keep pointing at it.
- `RETURNING` shows the new values. No `WHERE` means every row changes.

### Check questions, with answers

**Question 1.** Here is a table with two rows:

```sql
CREATE TABLE room (
    id       uuid PRIMARY KEY,
    name     text NOT NULL,
    floor    integer NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0)
);

INSERT INTO room (id, name, floor, capacity) VALUES
('eeeeeeee-0000-0000-0000-000000000001', 'Green Room', 1, 20),
('eeeeeeee-0000-0000-0000-000000000002', 'Blue Room',  2, 40);
```

Someone runs:

```sql
UPDATE room
SET name = 'Gold Room', capacity = capacity + 10
WHERE id = 'eeeeeeee-0000-0000-0000-000000000002';
```

(a) What does psql print? (b) What do both rows look like afterwards? Give every column.
(c) Why is picking the row by its id safer than `WHERE name = 'Blue Room'`?

**Answer.**

(a) `UPDATE 1`: the `WHERE` picked one row, and it was updated.

(b) Real output:

```text
SELECT id, name, floor, capacity FROM room ORDER BY id;
                  id                  |    name    | floor | capacity
--------------------------------------+------------+-------+----------
 eeeeeeee-0000-0000-0000-000000000001 | Green Room |     1 |       20
 eeeeeeee-0000-0000-0000-000000000002 | Gold Room  |     2 |       50
(2 rows)
```

The Blue Room row got both new values: the name `Gold Room`, and capacity 40 + 10 = 50
(`capacity` on the right side means its value before the update). `floor` was not in `SET`, so
it stayed 2. Green Room was not picked by the `WHERE`, so it did not change at all.

(c) `id` is the primary key: no two rows can share it, so `WHERE id = '...'` picks at most one
row. Nothing stops a second room from also being called Blue Room; then `WHERE name = 'Blue Room'`
would rename and enlarge both.

**Question 2.** Here is the same `room` table, freshly created with the same two rows: Green
Room (floor 1, capacity 20) and Blue Room (floor 2, capacity 40). A developer wants to rename
Green Room to Red Room and move it to floor 3. They write:

```sql
UPDATE room
SET name = 'Red Room' AND floor = 3
WHERE id = 'eeeeeeee-0000-0000-0000-000000000001';
```

(a) What happens? Does the Green Room row change? (b) Write the correct statement. What does
psql print, and what does the row look like afterwards?

**Answer.**

(a) An error, and nothing changes:

```text
ERROR:  invalid input syntax for type boolean: "Red Room"
LINE 2: SET name = 'Red Room' AND floor = 3
                   ^

SELECT id, name, floor, capacity FROM room ORDER BY id;
                  id                  |    name    | floor | capacity
--------------------------------------+------------+-------+----------
 eeeeeeee-0000-0000-0000-000000000001 | Green Room |     1 |       20
 eeeeeeee-0000-0000-0000-000000000002 | Blue Room  |     2 |       40
(2 rows)
```

Postgres read a single assignment, `name = ('Red Room' AND floor = 3)`. `AND` joins two
true-or-false values, so Postgres tried to read `'Red Room'` as true-or-false and failed. The
statement was refused as a whole.

(b) Commas separate assignments:

```sql
UPDATE room
SET name = 'Red Room', floor = 3
WHERE id = 'eeeeeeee-0000-0000-0000-000000000001';
```

psql prints `UPDATE 1`. Afterwards:

```text
SELECT id, name, floor, capacity FROM room ORDER BY id;
                  id                  |   name    | floor | capacity
--------------------------------------+-----------+-------+----------
 eeeeeeee-0000-0000-0000-000000000001 | Red Room  |     3 |       20
 eeeeeeee-0000-0000-0000-000000000002 | Blue Room |     2 |       40
(2 rows)
```

### Your doubts

None for this lesson.

---

## Lesson 5 — The affected row count: what `UPDATE 1`, `UPDATE 0` and `UPDATE 4` tell you

### Definition

The **affected row count** is the number at the end of the command tag psql prints after an
`UPDATE`, like the `1` in `UPDATE 1`. It tells you how many rows the `WHERE` picked. Every
picked row gets updated, so it is also how many rows the statement updated. By default it is
the only feedback an `UPDATE` gives you, and Postgres never judges it: `UPDATE 0` is not an
error, and neither is `UPDATE 4000`. Only you know which number you expected.

### Why it is needed

A wrong `WHERE` does not cause an error. Postgres cannot know what you meant; it runs what you
wrote. So a mistake in the `WHERE` shows up in only one place — the number:

- a typo in an id gives `UPDATE 0`: nothing changed, quietly;
- a `WHERE` on a name four rows share gives `UPDATE 4`: you meant one row, and three others
  changed too.

Neither prints `ERROR`. If you do not read the number, you never find out.

### Key points

- The count is the number of rows the `WHERE` picked.
- A row counts even when its new values equal its old values. `UPDATE 1` does not prove a
  value changed.
- A refused statement prints `ERROR` and no count; nothing changed.
- Decide the number you expect **before** running, then compare.
- `UPDATE 0` has two kinds of reason: no row matches what you wrote, or a row exists but
  another part of the `WHERE` rules it out.
- More than expected is the dangerous case: the extra rows have already changed.
- Java's `executeUpdate` returns the same number as an `int`. Code should check it.

### How it works, step by step

For `UPDATE`, the count is the number of rows for which the `WHERE` condition was true. Three
facts:

1. **A row counts even if its new values equal its old values.** Postgres still picked it and
   wrote it again.
2. **A row the `WHERE` did not pick never counts.**
3. **A refused statement has no count.** psql prints `ERROR`; nothing changed.

Reading it:

| You see | It means |
| --- | --- |
| The number you expected | The `WHERE` picked as many rows as you meant. |
| `UPDATE 0` | The `WHERE` picked no rows. Nothing changed. |
| Fewer than expected, but not 0 | Some rows you meant were not picked. |
| More than expected | The `WHERE` picked extra rows, and they have already changed. |

The last case is the dangerous one: in psql, a finished `UPDATE` has no undo button.

`UPDATE 0` comes from one of two kinds of reason:

- **Reason 1 — no row matches what you wrote.** The id has a typo; the row was never created;
  it was deleted earlier. Text comparisons are exact: `'harbour lights festival 2027'` does not
  equal `'Harbour Lights Festival 2027'`.
- **Reason 2 — the row exists, but another part of the `WHERE` rules it out.** For example
  `AND cancelled_at IS NULL` rules out a cancelled event. A condition that can never be true,
  like `cancelled_at = NULL`, rules out every row.

### Example — real run

Every statement here ran on throwaway copies, so nothing real changed:

```text
CREATE TABLE scratch_event AS SELECT * FROM event;
SELECT 5
CREATE TABLE scratch_price AS SELECT * FROM price_tier;
SELECT 9
```

`CREATE TABLE new_name AS SELECT ...` makes a new table and fills it with the rows the query
returns. The copy has the same columns and rows, but not the rules (primary key, `CHECK`, and so
on). Its reply, `SELECT 5`, means five rows were copied. The event copy:

```text
SELECT id, name, cancelled_at FROM scratch_event ORDER BY id;
                  id                  |             name             |         cancelled_at
--------------------------------------+------------------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night           |
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening            | 2026-10-24 18:05:00.690845+00
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note             |
 aaaaaaaa-0000-0000-0000-000000000004 | Open Mic Night               |
 aaaaaaaa-0000-0000-0000-000000000005 | Harbour Lights Festival 2027 |
(5 rows)
```

**(a) Expected 1, got 1 — but nothing changed.** S3's rename, run again; the name is already
the new name:

```text
UPDATE scratch_event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
UPDATE 1
```

**(b) `UPDATE 0`, reason 1: a typo in the id** (`...050` instead of `...005`):

```text
UPDATE scratch_event
SET name = 'Harbour Lights Festival 2028'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000050';
UPDATE 0
```

**(c) `UPDATE 0`, reason 1: wrong capital letters.**

```text
UPDATE scratch_event
SET starts_at = '2027-01-16 18:00+05:30'
WHERE name = 'harbour lights festival 2027';
UPDATE 0
```

**(d) `UPDATE 0`, reason 2: a guard rules the row out.** "Move Classical Evening one day later,
but only if it is not cancelled." A **guard** is an extra condition in the `WHERE` that protects
a row in the wrong state:

```text
UPDATE scratch_event
SET starts_at = '2026-12-06 18:30+05:30'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002'
  AND cancelled_at IS NULL;
UPDATE 0

SELECT id, name, cancelled_at FROM scratch_event
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002';
                  id                  |       name        |         cancelled_at
--------------------------------------+-------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening | 2026-10-24 18:05:00.690845+00
(1 row)
```

The row exists but is cancelled. Here the `0` is useful news: the event was not moved.

**(e) `UPDATE 0`, reason 2: a condition that can never be true.** The goal: move every event
that is not cancelled one day later. `interval '1 day'` is a length of time; adding it to a
timestamp moves it one day later.

```text
UPDATE scratch_event
SET starts_at = starts_at + interval '1 day'
WHERE cancelled_at = NULL;
UPDATE 0
```

`NULL` means "no value". Comparing anything with `NULL` using `=` gives "unknown", never true,
and `WHERE` keeps only rows where the condition is true. The test that works is `IS NULL`:

```text
UPDATE scratch_event
SET starts_at = starts_at + interval '1 day'
WHERE cancelled_at IS NULL;
UPDATE 4

SELECT name, starts_at, cancelled_at FROM scratch_event ORDER BY name;
             name             |       starts_at        |         cancelled_at
------------------------------+------------------------+-------------------------------
 Classical Evening            | 2026-12-05 13:00:00+00 | 2026-10-24 18:05:00.690845+00
 Harbour Lights Festival 2027 | 2027-01-16 12:30:00+00 |
 Jazz at the Note             | 2026-11-29 15:30:00+00 |
 Monsoon Rock Night           | 2026-11-21 13:30:00+00 |
 Open Mic Night               | 2026-12-13 14:30:00+00 |
(5 rows)
```

**(f) More than expected.** The goal: change Harbour Lights Festival's General price from 799 to
899. Before this, the four General tiers cost 1499 (Monsoon Rock Night), 999 (Classical
Evening), 1999 (Jazz at the Note) and 799 (Harbour Lights Festival 2027).

```text
UPDATE scratch_price
SET amount = 899
WHERE name = 'General';
UPDATE 4

SELECT e.name AS event_name, s.name AS tier, s.amount
FROM scratch_price s JOIN event e ON e.id = s.event_id
WHERE s.name = 'General'
ORDER BY e.name;
          event_name          |  tier   |  amount
------------------------------+---------+----------
 Classical Evening            | General | 899.0000
 Harbour Lights Festival 2027 | General | 899.0000
 Jazz at the Note             | General | 899.0000
 Monsoon Rock Night           | General | 899.0000
(4 rows)
```

On a fresh copy, the right statement picks the tier by id:

```text
DROP TABLE scratch_price;
DROP TABLE
CREATE TABLE scratch_price AS SELECT * FROM price_tier;
SELECT 9

UPDATE scratch_price
SET amount = 899
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000007';
UPDATE 1

SELECT e.name AS event_name, s.name AS tier, s.amount
FROM scratch_price s JOIN event e ON e.id = s.event_id
WHERE s.name = 'General'
ORDER BY e.name;
          event_name          |  tier   |  amount
------------------------------+---------+-----------
 Classical Evening            | General |  999.0000
 Harbour Lights Festival 2027 | General |  899.0000
 Jazz at the Note             | General | 1999.0000
 Monsoon Rock Night           | General | 1499.0000
(4 rows)
```

### Walkthrough

Reading each number — what you expected against what you got:

- **(a) Expected 1, got `UPDATE 1`.** It matches, but the name was already the new name: the row
  was picked and written again with the same value. Only a `SELECT` (or `RETURNING`) shows
  values.
- **(b) Expected 1, got `UPDATE 0`.** No row has id `...050`. Reason 1. Next step: `SELECT` the id
  you meant and compare character by character.
- **(c) Expected 1, got `UPDATE 0`.** `=` on text compares every character exactly, including
  capitals. Reason 1. Picking rows by id avoids this.
- **(d) Expected 1, got `UPDATE 0`.** The id matched Classical Evening, but `cancelled_at IS NULL`
  was false. With `AND`, both parts must be true. Reason 2. A `SELECT` on the id alone shows why.
- **(e) Expected 4, got `UPDATE 0`.** Five events, one cancelled, so you expected 4. `= NULL` is
  never true. Reason 2. With `IS NULL`: `UPDATE 4`, which matches.
- **(f) Expected 1, got `UPDATE 4`.** The alarm. `WHERE name = 'General'` picked every General
  tier in every event. With `WHERE id = ...`: `UPDATE 1`, and only the festival's tier changed.

### Important terms

| Term | Meaning |
| --- | --- |
| **Affected row count** | The number in `UPDATE n`: how many rows the `WHERE` picked and the statement updated. |
| **Guard** | An extra condition in a `WHERE` that protects a row in the wrong state, like `AND cancelled_at IS NULL`. |
| **Throwaway copy** | A copy of a table made only to practise on, then dropped. |
| **`CREATE TABLE ... AS SELECT`** | Makes a new table filled with the rows a query returns. Same columns and rows; no rules. Replies `SELECT n`. |
| **`IS NULL`** | The test for "this column has no value". `= NULL` is never true. |
| **`interval '1 day'`** | A length of time. Adding it to a timestamp moves it one day later. |
| **`executeUpdate`** | The JDBC method that runs a write and returns the affected row count as an `int`. |

### Common mistakes

- **Treating "no `ERROR`" as success.** `UPDATE 0` is not an error. Read the number.
- **Thinking `UPDATE 1` proves the value changed.** It proves one row was picked. Use a `SELECT`
  or `RETURNING` to see values.
- **Comparing with `= NULL`.** Always 0 rows. Use `IS NULL` / `IS NOT NULL`.
- **Text that is almost right.** Capitals, an extra space, a missing "2027" — 0 rows. Prefer the
  id.
- **Checking the number in psql but not in code.** If code ignores what `executeUpdate` returns,
  a "rename" request can report success while nothing changed.
- **Not deciding the expected number first.** Then the number cannot warn you.

### Edge cases

- **A copy made with `CREATE TABLE ... AS SELECT` has no rules.** That is fine for practising
  counts. It also means a copy accepts values the real table would refuse.
- **A refused `UPDATE` prints no count**, not `UPDATE 0`. `UPDATE 0` means the statement ran and
  picked nothing; `ERROR` means it was refused.
- **With a `WHERE` on the primary key, the count can only be 0 or 1.** No two rows share an id.

### Where it is used

- **Java code checks the count.** For "rename the event with this id": 0 → reply
  `404 Not Found` (no such event); 1 → success; more than 1 → a bug, refuse. With a `WHERE` on
  the primary key, more than 1 cannot happen.
- **Guarded updates — who got the seat?** Two people click the same seat. A guard makes the count
  tell each one whether they won. Real run on a scratch table:

  ```text
  CREATE TABLE seat_hold (
      seat_label text PRIMARY KEY,
      held_by    text
  );
  INSERT INTO seat_hold (seat_label, held_by) VALUES ('A1', NULL), ('A2', NULL);
  INSERT 0 2

  UPDATE seat_hold SET held_by = 'asha'
  WHERE seat_label = 'A1' AND held_by IS NULL;
  UPDATE 1

  UPDATE seat_hold SET held_by = 'ravi'
  WHERE seat_label = 'A1' AND held_by IS NULL;
  UPDATE 0

  SELECT seat_label, held_by FROM seat_hold ORDER BY seat_label;
   seat_label | held_by
  ------------+---------
   A1         | asha
   A2         |
  (2 rows)
  ```

  Asha got `UPDATE 1`: she has the seat. Ravi got `UPDATE 0`: it was no longer free. Even if both
  clicks arrive at the same moment, Postgres lets only one of them match.
- **Data fixes.** Write down the expected number first. A fix meant for 12 rows that prints
  `UPDATE 4000` is an alarm.
- **Other statements.** `INSERT 0 3` and `DELETE 1` are read the same way.

### Link to the task

- **S3, second half:** *"Read the `UPDATE 1` that comes back and make a habit of checking that
  number every time."* This lesson is what that habit is for.
- **Checkpoint question 2 — "`UPDATE 0`: name two possible reasons":**
  1. No row matches what the `WHERE` asks for: the id is wrong or mistyped, the text is not
     exactly right, or the row does not exist (never created, or already deleted).
  2. The row exists, but another condition in the `WHERE` rules it out: for example
     `AND cancelled_at IS NULL` on a cancelled event, or a condition that is never true, like
     `= NULL`.

### Do it yourself

These steps work only on throwaway copies; your real data does not change.

**Step 0 — check the starting state.** If the machine has no Docker, no `pg` container, no
`zenith` database, or no tables, first do Steps A to E of **Setting all of this up from
nothing** near the top of this file. Then open psql
(`docker exec -it pg psql -U postgres -d zenith`) and run:

```sql
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;

SELECT name FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
```

Expected: `4 | 5 | 9`, then the name `Harbour Lights Festival 2027`.

- **The name is `Harbour Lights Festival`:** S3 is not done. Run this (expected `UPDATE 1`):

  ```sql
  UPDATE event
  SET name = 'Harbour Lights Festival 2027'
  WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
  ```

- **`4 | 5 | 6`:** S2 is not done. Run this (expected `INSERT 0 3`), then the S3 bullet:

  ```sql
  INSERT INTO price_tier (id, event_id, name, amount) VALUES
  ('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000005', 'General',  799),
  ('bbbbbbbb-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000005', 'Gold',     1999),
  ('bbbbbbbb-0000-0000-0000-000000000009', 'aaaaaaaa-0000-0000-0000-000000000005', 'Platinum', 3999);
  ```

- **`3 | 4 | 6`:** S1 is not done. Run these two (in the second, replace
  `<paste the venue id here>` with the id the first returns, keeping the quotes), then the S2
  bullet, then the S3 bullet:

  ```sql
  INSERT INTO venue (id, name, city, capacity)
  VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
  RETURNING id;

  INSERT INTO event (id, venue_id, name, starts_at)
  VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '<paste the venue id here>', 'Harbour Lights Festival', '2027-01-15 18:00+05:30');
  ```

**Step 1 — make the throwaway copies.**

```sql
CREATE TABLE scratch_event AS SELECT * FROM event;
CREATE TABLE scratch_price AS SELECT * FROM price_tier;
```

Expected: `SELECT 5`, then `SELECT 9`.

**Step 2 — the same value still counts.** Expected number first: 1.

```sql
UPDATE scratch_event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
```

Expected: `UPDATE 1`, though the name did not change.

**Step 3 — `UPDATE 0` from a typo.**

```sql
UPDATE scratch_event
SET name = 'Harbour Lights Festival 2028'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000050';
```

Expected: `UPDATE 0`

**Step 4 — `UPDATE 0` from capital letters.**

```sql
UPDATE scratch_event
SET starts_at = '2027-01-16 18:00+05:30'
WHERE name = 'harbour lights festival 2027';
```

Expected: `UPDATE 0`

**Step 5 — `UPDATE 0` from a guard.**

```sql
UPDATE scratch_event
SET starts_at = '2026-12-06 18:30+05:30'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002'
  AND cancelled_at IS NULL;

SELECT id, name, cancelled_at FROM scratch_event
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002';
```

Expected: `UPDATE 0`, then one row — Classical Evening, with a `cancelled_at` value.

**Step 6 — `= NULL` versus `IS NULL`.**

```sql
UPDATE scratch_event
SET starts_at = starts_at + interval '1 day'
WHERE cancelled_at = NULL;

UPDATE scratch_event
SET starts_at = starts_at + interval '1 day'
WHERE cancelled_at IS NULL;

SELECT name, starts_at, cancelled_at FROM scratch_event ORDER BY name;
```

Expected: `UPDATE 0`, then `UPDATE 4`, then:

```text
             name             |       starts_at        |         cancelled_at
------------------------------+------------------------+-------------------------------
 Classical Evening            | 2026-12-05 13:00:00+00 | 2026-10-24 18:05:00.690845+00
 Harbour Lights Festival 2027 | 2027-01-16 12:30:00+00 |
 Jazz at the Note             | 2026-11-29 15:30:00+00 |
 Monsoon Rock Night           | 2026-11-21 13:30:00+00 |
 Open Mic Night               | 2026-12-13 14:30:00+00 |
(5 rows)
```

Your `cancelled_at` for Classical Evening shows the time you cancelled it.

**Step 7 — more than expected.** Expected number first: 1.

```sql
UPDATE scratch_price
SET amount = 899
WHERE name = 'General';

SELECT e.name AS event_name, s.name AS tier, s.amount
FROM scratch_price s JOIN event e ON e.id = s.event_id
WHERE s.name = 'General'
ORDER BY e.name;
```

Expected: `UPDATE 4`, then four rows, all at `899.0000`.

**Step 8 — the right statement, on a fresh copy.**

```sql
DROP TABLE scratch_price;
CREATE TABLE scratch_price AS SELECT * FROM price_tier;

UPDATE scratch_price
SET amount = 899
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000007';
```

Expected: `DROP TABLE`, `SELECT 9`, `UPDATE 1`. Run the Step 7 `SELECT` again: only Harbour
Lights Festival 2027 shows `899.0000`; the others show 999, 1999 and 1499.

**Step 9 (optional) — who got the seat?**

```sql
CREATE TABLE seat_hold (
    seat_label text PRIMARY KEY,
    held_by    text
);
INSERT INTO seat_hold (seat_label, held_by) VALUES ('A1', NULL), ('A2', NULL);

UPDATE seat_hold SET held_by = 'asha'
WHERE seat_label = 'A1' AND held_by IS NULL;

UPDATE seat_hold SET held_by = 'ravi'
WHERE seat_label = 'A1' AND held_by IS NULL;
```

Expected: `CREATE TABLE`, `INSERT 0 2`, `UPDATE 1`, then `UPDATE 0`.

**Step 10 — clean up, and prove the real data is untouched.**

```sql
DROP TABLE scratch_event, scratch_price, seat_hold;

SELECT e.name AS event_name, p.name AS tier, p.amount
FROM price_tier p JOIN event e ON e.id = p.event_id
WHERE p.name = 'General'
ORDER BY e.name;
```

Expected: `DROP TABLE`, then the real General prices, unchanged: 999, 799, 1999, 1499. If you
skipped Step 9, drop only `scratch_event, scratch_price`.

If something goes wrong:

| What you see | What it means | What to do |
| --- | --- | --- |
| `relation "scratch_event" already exists` in Step 1 | An old copy was left behind. | `DROP TABLE scratch_event;` (or `scratch_price`) and repeat Step 1. |
| `relation "scratch_event" does not exist` | Step 1 has not run. | Run Step 1. |
| `UPDATE 3` in Step 7, or `SELECT 6` in Step 1 | S2 is not done: only three General tiers. | Do Step 0. |
| `UPDATE 1` in Step 3 | You typed the real id by accident; the copy's name changed. | Nothing serious — it is a copy. Drop the copies and restart from Step 1 if you like. |
| `table "seat_hold" does not exist` in Step 10 | You skipped Step 9. | Run `DROP TABLE scratch_event, scratch_price;` instead. |

**Done when:** you have seen `UPDATE 1` with no real change, `UPDATE 0` for both kinds of reason,
and `UPDATE 4` where you meant 1, and can explain each.

### Summary for revision

- The affected row count = rows the `WHERE` picked. Postgres never judges it.
- Same-value updates still count; `UPDATE 1` does not prove a change.
- `ERROR` means refused (no count); `UPDATE 0` means ran and picked nothing.
- `UPDATE 0` reasons: (1) no row matches what you wrote — typo, wrong text, row missing;
  (2) the row exists but the rest of the `WHERE` rules it out — a guard, or `= NULL`.
- More than expected = the extra rows already changed. Decide the expected number first.
- Guards turn the count into an answer: `UPDATE 1` you got the seat; `UPDATE 0` you did not.
- In Java, check what `executeUpdate` returns.

### Check questions, with answers

**Question 1.** Here is a table with three rows:

```sql
CREATE TABLE member (
    id      uuid PRIMARY KEY,
    email   text NOT NULL,
    city    text NOT NULL,
    left_at timestamptz
);

INSERT INTO member (id, email, city, left_at) VALUES
('ffffffff-0000-0000-0000-000000000001', 'asha@example.com',  'Pune',  NULL),
('ffffffff-0000-0000-0000-000000000002', 'ravi@example.com',  'Pune',  NULL),
('ffffffff-0000-0000-0000-000000000003', 'meera@example.com', 'Delhi', '2026-09-01 10:00+05:30');
```

These four statements run one after another. For each, what number does psql print after
`UPDATE`, and why?

```sql
-- (a)
UPDATE member SET city = 'Mumbai' WHERE email = 'Asha@example.com';
-- (b)
UPDATE member SET city = 'Pune' WHERE city = 'Pune';
-- (c)
UPDATE member SET city = 'Goa'
WHERE id = 'ffffffff-0000-0000-0000-000000000003' AND left_at IS NULL;
-- (d)
UPDATE member SET city = 'Goa' WHERE left_at = NULL;
```

**Answer.** Real output, in order:

```text
UPDATE 0
UPDATE 2
UPDATE 0
UPDATE 0
```

- (a) `UPDATE 0` — reason 1. Text comparison is exact: `'Asha@example.com'` has a capital A, the
  stored email does not. No row matches.
- (b) `UPDATE 2` — both Pune rows were picked and set to `'Pune'` again. Nothing visibly changed,
  but the count is rows picked, not rows whose values differed.
- (c) `UPDATE 0` — reason 2. Meera's id matches, but `left_at IS NULL` is false: she left on
  2026-09-01. With `AND`, both parts must be true. The guard ruled her out.
- (d) `UPDATE 0` — reason 2. `left_at = NULL` is never true for any row. `IS NULL` is the test
  that works; it would pick the two members whose `left_at` is empty.

The table afterwards is exactly as it started:

```text
SELECT id, email, city, left_at FROM member ORDER BY id;
                  id                  |       email       | city  |        left_at
--------------------------------------+-------------------+-------+------------------------
 ffffffff-0000-0000-0000-000000000001 | asha@example.com  | Pune  |
 ffffffff-0000-0000-0000-000000000002 | ravi@example.com  | Pune  |
 ffffffff-0000-0000-0000-000000000003 | meera@example.com | Delhi | 2026-09-01 04:30:00+00
(3 rows)
```

**Question 2.** A ticketing app lets an organiser rename an event. The server takes the event's
id from the URL and runs:

```sql
UPDATE event SET name = 'New Name'
WHERE id = '<id from the URL>' AND cancelled_at IS NULL;
```

(a) The command tag says `UPDATE 0`. Give two different reasons. (b) How can the server find
out which reason it was? (c) Can this statement ever print `UPDATE 2`? Why or why not?

**Answer.**

(a) Reason 1: no event has that id — the id in the URL is mistyped or wrong, or the event was
deleted, or it never existed. Reason 2: the event exists but is cancelled, so
`cancelled_at IS NULL` is false and the guard rules it out. Both give `UPDATE 0`. Real runs on
the `event` table — the first with an id that does not exist, the second with cancelled
Classical Evening's id:

```text
UPDATE event SET name = 'New Name'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000099' AND cancelled_at IS NULL;
UPDATE 0

UPDATE event SET name = 'New Name'
WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002' AND cancelled_at IS NULL;
UPDATE 0
```

(b) Look the row up by its id alone, without the guard:

```text
SELECT id, name, cancelled_at FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000099';
 id | name | cancelled_at
----+------+--------------
(0 rows)

SELECT id, name, cancelled_at FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002';
                  id                  |       name        |         cancelled_at
--------------------------------------+-------------------+-------------------------------
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening | 2026-10-24 18:05:00.690845+00
(1 row)
```

Zero rows means there is no such event: reply `404 Not Found`. One row with `cancelled_at` filled
in means the event exists but is cancelled: reply with an error that says so, such as
`409 Conflict` (the request clashes with the event's current state).

(c) No. `id` is the primary key, so no two rows share it and the `WHERE` can pick at most one
row. This statement can only print `UPDATE 0` or `UPDATE 1`.

### Your doubts

None for this lesson.

---

## Lesson 6 — The missing `WHERE`: what an `UPDATE` with no filter does

> **Format note.** From this lesson on, the notes are shorter: the idea, the key points, and
> 2–3 real examples per lesson. No check questions and no "Do it yourself" sections.

### The idea

`WHERE` is the only thing that limits an `UPDATE`. If you leave it out, Postgres changes
**every row in the table**. It does not ask "are you sure?" and does not warn you. It just runs.
This is subtask S4: do it on purpose, on a throwaway copy, so you have seen it happen once.

Key points:

- `CREATE TABLE scratch AS SELECT * FROM event;` makes a copy of the rows. The reply
  `SELECT 5` means 5 rows were copied. The copy gets **no** rules: no primary key, no foreign
  keys, no `CHECK`, no `UNIQUE`. That is fine for a throwaway table.
- psql runs a statement only when it sees `;`. Until then it keeps collecting lines, and the
  prompt changes from `=#` to `-#` ("still waiting for the rest").
- `--` starts a comment. Postgres ignores everything after `--` up to the end of that line.
- `\r` empties what you have typed so far without running it. psql replies
  `Query buffer reset (cleared).` This is your escape hatch for a half-typed statement.
- Once a statement finishes, it is saved for good (this is called autocommit; it is the next
  lesson). There is no undo.

### Example 1 — no `WHERE` on a copy changes all 5 rows

```text
zenith_lab=# CREATE TABLE scratch AS SELECT * FROM event;
SELECT 5
zenith_lab=# UPDATE scratch
zenith_lab-# SET name = 'oops'
zenith_lab-# WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
UPDATE 1
zenith_lab=# UPDATE scratch SET name = 'oops';
UPDATE 5
zenith_lab=# DROP TABLE scratch;
DROP TABLE
```

With the `WHERE`, the result is `UPDATE 1`. Without it, the result is `UPDATE 5`, and every event name in the copy
becomes `oops`. The real `event` table was never touched.

### Example 2 — two ways the `WHERE` "disappears" while you can still see it

```sql
UPDATE scratch
SET name = 'oops again'
-- WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005'
;
```

```text
UPDATE 5
```

The `WHERE` line is a comment, so Postgres never sees it. The statement is really
`UPDATE scratch SET name = 'oops again';`.

```sql
UPDATE scratch SET name = 'oops' WHERE id = id;
```

```text
UPDATE 5
```

`id = id` compares each row's id with itself. That is always true, so every row matches. It
looks like a filter but filters nothing.

### Example 3 — `\r` throws away a half-typed statement

```text
zenith_lab=# SELECT name
zenith_lab-# FROM event
zenith_lab-# \r
Query buffer reset (cleared).
zenith_lab=#
```

If you press Enter too early and the prompt shows `-#`, type `\r` instead of finishing the
statement. Nothing runs.

Bonus, seen in the lab: on a copy that **keeps** the rules
(`CREATE TABLE scratch_price (LIKE price_tier INCLUDING ALL);`), `UPDATE scratch_price SET name = 'oops';`
failed with
`ERROR: duplicate key value violates unique constraint "scratch_price_event_id_name_key"`,
because two tiers of the same event cannot both be named `oops`. `UPDATE scratch_price SET amount = 0;`
had no rule to break and printed `UPDATE 9`. Constraints sometimes catch a missing `WHERE`, but
only by luck. Do not count on them.

### Summary

- No `WHERE` means every row. Postgres does not warn you.
- A commented-out `WHERE` and `WHERE id = id` both behave like no `WHERE`.
- Watch the number in the reply: `UPDATE 5` when you expected `UPDATE 1` is the alarm.
- `\r` cancels a statement you have not finished typing.

### Your doubts

None for this lesson.

---

## Lesson 7 — Autocommit, and `BEGIN` / `COMMIT` / `ROLLBACK`

### The idea

In psql, every statement is saved for good the moment it finishes. This is called
**autocommit**. A **transaction** groups statements so they are saved together or not at all:

| Command | Meaning |
| --- | --- |
| `BEGIN;` | Open a transaction. From now on, changes are only tentative. |
| `COMMIT;` | Save everything since `BEGIN`. |
| `ROLLBACK;` | Throw away everything since `BEGIN`, as if it never happened. |

The psql prompt tells you where you are:

| Prompt | Meaning |
| --- | --- |
| `zenith_lab=#` | No transaction is open. Autocommit is on; each statement is saved as soon as it ends. |
| `zenith_lab=*#` | Inside a transaction. Nothing is saved yet. |
| `zenith_lab=!#` | Something failed inside the transaction. Every statement is ignored until `ROLLBACK;`. |

### Example 1 — under autocommit, `ROLLBACK` cannot help

```text
zenith_lab=# CREATE TABLE scratch AS SELECT * FROM event;
SELECT 5
zenith_lab=# UPDATE scratch SET name = 'oops';
UPDATE 5
zenith_lab=# ROLLBACK;
WARNING:  there is no transaction in progress
ROLLBACK
zenith_lab=# SELECT name FROM scratch ORDER BY id;
 name
------
 oops
 oops
 oops
 oops
 oops
(5 rows)
```

The `UPDATE` was already saved when it finished. The warning means "nothing is open, so there is
nothing to undo".

### Example 2 — the same mistake inside `BEGIN`, undone

```text
zenith_lab=# BEGIN;
BEGIN
zenith_lab=*# UPDATE scratch SET name = 'oops';
UPDATE 5
zenith_lab=*# SELECT name FROM scratch ORDER BY id;
 name
------
 oops
 oops
 oops
 oops
 oops
(5 rows)

zenith_lab=*# ROLLBACK;
ROLLBACK
zenith_lab=# SELECT name FROM scratch ORDER BY id;
             name
------------------------------
 Monsoon Rock Night
 Classical Evening
 Jazz at the Note
 Open Mic Night
 Harbour Lights Festival 2027
(5 rows)
```

Inside the transaction you can look at the result before deciding. `UPDATE 5` was the alarm, so
`ROLLBACK` brought every name back.

### Example 3 — `COMMIT` a good change; an error puts you in the failed state

```text
zenith_lab=# BEGIN;
BEGIN
zenith_lab=*# UPDATE scratch SET name = 'Harbour Lights (copy)' WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
UPDATE 1
zenith_lab=*# COMMIT;
COMMIT
zenith_lab=# BEGIN;
BEGIN
zenith_lab=*# UPDATE scratch SET nam = 'typo' WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
ERROR:  column "nam" of relation "scratch" does not exist
LINE 1: UPDATE scratch SET nam = 'typo' WHERE id = 'aaaaaaaa-0000-00...
                           ^
zenith_lab=!# SELECT count(*) FROM scratch;
ERROR:  current transaction is aborted, commands ignored until end of transaction block
zenith_lab=!# ROLLBACK;
ROLLBACK
```

After an error inside a transaction, even a harmless `SELECT` is refused. The only way out is
`ROLLBACK;`.

### Summary

- psql autocommits: a finished statement is permanent.
- `BEGIN;` → change → read the row count → `COMMIT;` if right, `ROLLBACK;` if wrong.
- `*` in the prompt = inside a transaction. `!` = failed transaction, type `ROLLBACK;`.

### Your doubts

None for this lesson.

---

## Lesson 8 — The safety habit: `WHERE` first, `SELECT` first (S5)

### The habit (S5 — written down)

Before any `UPDATE` or `DELETE`:

1. Write the `WHERE` clause **first**.
2. Run it as a `SELECT ... WHERE <same condition>`. Look at which rows come back and how many.
3. `BEGIN;`
4. Turn the `SELECT` into the `UPDATE ... SET` or `DELETE`, keeping the **exact same** `WHERE`.
5. Compare the reply count (`UPDATE n` / `DELETE n`) with the number of rows the `SELECT` returned.
6. Same number: `COMMIT;`. Different number: `ROLLBACK;`.

Prefer the primary key (`WHERE id = '...'`) when you mean one row.

### Example 1 — the `SELECT` shows that a vague `WHERE` catches too much

Goal: change the festival's General price.

```text
SELECT id, name, amount FROM price_tier WHERE name = 'General';
                  id                  |  name   |  amount
--------------------------------------+---------+-----------
 bbbbbbbb-0000-0000-0000-000000000001 | General | 1499.0000
 bbbbbbbb-0000-0000-0000-000000000003 | General |  999.0000
 bbbbbbbb-0000-0000-0000-000000000004 | General | 1999.0000
 bbbbbbbb-0000-0000-0000-000000000007 | General |  799.0000
(4 rows)

SELECT id, name, amount FROM price_tier WHERE id = 'bbbbbbbb-0000-0000-0000-000000000007';
                  id                  |  name   |  amount
--------------------------------------+---------+----------
 bbbbbbbb-0000-0000-0000-000000000007 | General | 799.0000
(1 row)
```

Every event has a General tier, so `name = 'General'` matches 4 rows. The id matches exactly one.

### Example 2 — the `SELECT` was skipped, but `BEGIN` saved the day

```text
BEGIN;
BEGIN
UPDATE price_tier SET amount = 899 WHERE name = 'General';
UPDATE 4
ROLLBACK;
ROLLBACK
SELECT e.name AS event_name, p.name AS tier, p.amount
FROM price_tier p JOIN event e ON e.id = p.event_id
WHERE p.name = 'General' ORDER BY e.name;
          event_name          |  tier   |  amount
------------------------------+---------+-----------
 Classical Evening            | General |  999.0000
 Harbour Lights Festival 2027 | General |  799.0000
 Jazz at the Note             | General | 1999.0000
 Monsoon Rock Night           | General | 1499.0000
(4 rows)
```

You expected 1 and got 4, so you rolled back. All four prices are unchanged.

### Your doubts

None for this lesson.

---

## Lesson 9 — `DELETE ... WHERE`: removing rows (S6, part 1)

### The idea

`DELETE FROM table WHERE ...;` removes the rows that match. The reply is `DELETE n` (rows
removed). With no `WHERE`, every row goes, just like `UPDATE`. `RETURNING` works too and shows
the rows that were removed.

### Example 1 — delete one tier, using the habit

```text
SELECT id, name, amount FROM price_tier WHERE id = 'bbbbbbbb-0000-0000-0000-000000000009';
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(1 row)

BEGIN;
BEGIN
DELETE FROM price_tier
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000009'
RETURNING id, name, amount;
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-000000000009 | Platinum | 3999.0000
(1 row)

DELETE 1
COMMIT;
COMMIT
SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005' ORDER BY amount;
                  id                  |  name   |  amount
--------------------------------------+---------+-----------
 bbbbbbbb-0000-0000-0000-000000000007 | General |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold    | 1999.0000
(2 rows)
```

### Example 2 — the same delete again

```text
DELETE FROM price_tier WHERE id = 'bbbbbbbb-0000-0000-0000-000000000009';
DELETE 0
```

There is no error. Nothing matched, so 0 rows were removed.

### Example 3 — no `WHERE`, on a throwaway copy

```text
CREATE TABLE scratch AS SELECT * FROM price_tier;
SELECT 8
DELETE FROM scratch;
DELETE 8
SELECT count(*) FROM scratch;
 count
-------
     0
(1 row)

SELECT count(*) FROM price_tier;
 count
-------
     8
(1 row)

DROP TABLE scratch;
DROP TABLE
```

### Your doubts

None for this lesson.

---

## Lesson 10 — When a delete is refused: foreign keys, and `ON DELETE CASCADE` (S6, part 2)

### The idea

A **foreign key** says "this row points at a row in another table" (`event.venue_id` points at a
`venue`). By default Postgres **refuses** to delete a parent row while child rows still point at
it; otherwise those children would point at nothing. `ON DELETE CASCADE` is the opposite choice:
deleting the parent silently deletes the children too.

### Example 1 — deleting a venue that still has events

```text
DELETE FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';
ERROR:  update or delete on table "venue" violates foreign key constraint "event_venue_fk" on table "event"
DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) is still referenced from table "event".
```

| Part | Plain English |
| --- | --- |
| `update or delete on table "venue"` | You tried to delete a venue. |
| `violates foreign key constraint "event_venue_fk" on table "event"` | The rule that events must point at a real venue would break. |
| `Key (id)=(1111...) is still referenced from table "event"` | Some events still point at this venue. Nothing was deleted. |

A venue with no events, such as Lakeside Hall (`33333333-...`), deletes fine: `DELETE 1`. (That
delete was tried inside `BEGIN` and rolled back.)

### Example 2 — the same protection one level down

```text
DELETE FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001';
ERROR:  update or delete on table "event" violates foreign key constraint "price_tier_event_fk" on table "price_tier"
DETAIL:  Key (id)=(aaaaaaaa-0000-0000-0000-000000000001) is still referenced from table "price_tier".
```

Monsoon Rock Night still has price tiers, so the event cannot go.

### Example 3 — `ON DELETE CASCADE` (scratch tables only)

```text
CREATE TABLE scratch_venue (
    id   integer PRIMARY KEY,
    name text NOT NULL
);
CREATE TABLE
CREATE TABLE scratch_event (
    id       integer PRIMARY KEY,
    venue_id integer NOT NULL REFERENCES scratch_venue(id) ON DELETE CASCADE,
    name     text NOT NULL
);
CREATE TABLE
INSERT INTO scratch_venue (id, name) VALUES (1, 'Riverside Arena');
INSERT 0 1
INSERT INTO scratch_event (id, venue_id, name) VALUES
(1, 1, 'Monsoon Rock Night'),
(2, 1, 'Classical Evening'),
(3, 1, 'Winter Gala');
INSERT 0 3
DELETE FROM scratch_venue WHERE id = 1;
DELETE 1
SELECT count(*) FROM scratch_event;
 count
-------
     0
(1 row)

DROP TABLE scratch_event, scratch_venue;
DROP TABLE
```

The reply says `DELETE 1`, but three events vanished with no message. That is why this project
keeps the default "refuse": the refusal protects you.

### Your doubts

None for this lesson.

---

## Lesson 11 — Soft delete: marking a row as gone instead of removing it (S7)

### The idea

A **hard delete** removes the row (`DELETE`). A **soft delete** keeps the row and fills in a
timestamp column such as `cancelled_at` or `deleted_at` (an `UPDATE`). Normal queries then add
`WHERE deleted_at IS NULL` to hide those rows. A soft delete can be undone; a hard delete cannot.

### The rule for this project (S7 — written down)

| Data | Rule | Why |
| --- | --- | --- |
| Order, payment, ticket, refund | Soft only. Never hard-delete. | Financial history: refunds, disputes, tax, audits. |
| Event | Soft (`cancelled_at`). | Tickets and orders point at it; buyers must see it was cancelled. |
| Venue, price tier that was ever sold | Soft. | Old orders still point at it. |
| Draft seat map never published, scratch tables, expired sessions | Hard delete is fine. | Nothing points at it and no money was involved. |

One-line version: **if anything (money, a customer, another row, an audit) could ever ask about
it, soft-delete it. If it was only ever a draft, hard-delete it.**

### Example 1 — the `event` table already soft-deletes

```text
SELECT name, cancelled_at FROM event WHERE cancelled_at IS NULL ORDER BY name;
             name             | cancelled_at
------------------------------+--------------
 Harbour Lights Festival 2027 |
 Jazz at the Note             |
 Monsoon Rock Night           |
 Open Mic Night               |
(4 rows)

SELECT e.name AS event_name, e.cancelled_at, p.name AS tier, p.amount
FROM price_tier p JOIN event e ON e.id = p.event_id
WHERE e.id = 'aaaaaaaa-0000-0000-0000-000000000002';
    event_name     |         cancelled_at          |  tier   |  amount
-------------------+-------------------------------+---------+----------
 Classical Evening | 2026-10-24 18:05:00.690845+00 | General | 999.0000
(1 row)
```

Classical Evening is hidden from the "live" list but still exists, with its price tier.

### Example 2 — soft delete, then restore (throwaway copy with a `deleted_at` column)

```text
UPDATE scratch_tier SET deleted_at = now()
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008' AND deleted_at IS NULL;
UPDATE 1
SELECT name, amount FROM scratch_tier WHERE deleted_at IS NULL ORDER BY amount;
  name   |  amount
---------+----------
 General | 799.0000
(1 row)

SELECT name, amount, deleted_at FROM scratch_tier ORDER BY amount;
  name   |  amount   |          deleted_at
---------+-----------+-------------------------------
 General |  799.0000 |
 Gold    | 1999.0000 | 2026-10-25 11:58:56.156704+00
(2 rows)

UPDATE scratch_tier SET deleted_at = NULL
WHERE id = 'bbbbbbbb-0000-0000-0000-000000000008';
UPDATE 1
SELECT name, amount FROM scratch_tier WHERE deleted_at IS NULL ORDER BY amount;
  name   |  amount
---------+-----------
 General |  799.0000
 Gold    | 1999.0000
(2 rows)
```

### Example 3 — unique rules still see hidden rows; a partial unique index fixes it

With Gold soft-deleted and a normal `UNIQUE (event_id, name)`, adding a new Gold fails:

```text
ERROR:  duplicate key value violates unique constraint "scratch_tier_name_unique"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000005, Gold) already exists.
```

A **partial unique index** checks uniqueness only for rows that match its `WHERE`:

```text
CREATE UNIQUE INDEX scratch_tier_live_name ON scratch_tier (event_id, name)
WHERE deleted_at IS NULL;
CREATE INDEX
INSERT INTO scratch_tier (id, event_id, name, amount, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Gold', 2199, now());
INSERT 0 1
SELECT name, amount, deleted_at FROM scratch_tier ORDER BY name, deleted_at NULLS FIRST;
  name   |  amount   |          deleted_at
---------+-----------+-------------------------------
 General |  799.0000 |
 Gold    | 2199.0000 |
 Gold    | 1999.0000 | 2026-10-25 11:58:56.160081+00
(3 rows)
```

Only live rows must be unique; the old Gold stays as history.

### Your doubts

None for this lesson.

---

## Lesson 12 — Why repeating an `INSERT` is dangerous, and what an upsert is

### The idea

Networks fail. A client sends "create Early Bird", the reply is lost, and the client sends it
again. A plain `INSERT` repeated either **errors** (if a unique rule exists) or **silently
duplicates** (if none exists). An **upsert** ("update or insert") is one statement that says
"insert this row; if it is already there, do something sensible instead". In Postgres it is
`INSERT ... ON CONFLICT ...`.

### Example 1 — repeat on the real table: the unique rule fires (rolled back)

```text
BEGIN;
BEGIN
INSERT INTO price_tier (id, event_id, name, amount)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00);
INSERT 0 1
INSERT INTO price_tier (id, event_id, name, amount)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00);
ERROR:  duplicate key value violates unique constraint "price_tier_name_unique_per_event"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000005, Early Bird) already exists.
ROLLBACK;
ROLLBACK
```

The data is safe, but the retry looks like a failure to the client.

### Example 2 — repeat on a copy with no unique rule: a silent duplicate

```text
INSERT INTO scratch_tier (id, event_id, name, amount, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00, now());
INSERT 0 1
INSERT INTO scratch_tier (id, event_id, name, amount, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00, now());
INSERT 0 1
SELECT id, name, amount FROM scratch_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005' AND name = 'Early Bird';
                  id                  |    name    | amount
--------------------------------------+------------+---------
 4ec2391d-1654-41b2-b09e-27c048c474f3 | Early Bird | 39.0000
 93f7a889-14e4-4107-bd14-80f442f62d46 | Early Bird | 39.0000
(2 rows)
```

Two Early Bird tiers, and no error to tell you.

### Your doubts

None for this lesson.

---

## Lesson 13 — `ON CONFLICT ... DO NOTHING`

### The idea

`INSERT ... ON CONFLICT (event_id, name) DO NOTHING;` means "try to insert; if a row with this
`(event_id, name)` already exists, skip quietly". The columns in brackets are the **conflict
target** and must match a unique rule on the table. The reply tells you what happened:
`INSERT 0 1` = inserted, `INSERT 0 0` = skipped. The existing row always wins ("first one wins").

### Example 1 — run twice

```text
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00)
ON CONFLICT (event_id, name) DO NOTHING;
INSERT 0 1
                  id                  |    name    |  amount
--------------------------------------+------------+-----------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird |   39.0000
 bbbbbbbb-0000-0000-0000-000000000007 | General    |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold       | 1999.0000
(3 rows)

-- same statement again:
INSERT 0 0
                  id                  |    name    |  amount
--------------------------------------+------------+-----------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird |   39.0000
 bbbbbbbb-0000-0000-0000-000000000007 | General    |  799.0000
 bbbbbbbb-0000-0000-0000-000000000008 | Gold       | 1999.0000
(3 rows)
```

### Example 2 — `DO NOTHING` ignores new values

```text
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 42.00)
ON CONFLICT (event_id, name) DO NOTHING;
INSERT 0 0
                  id                  |    name    | amount
--------------------------------------+------------+---------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird | 39.0000
(1 row)
```

### Example 3 — the conflict target matters

```text
-- no unique rule on (name) alone:
INSERT INTO price_tier (id, event_id, name, amount)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00)
ON CONFLICT (name) DO NOTHING;
ERROR:  there is no unique or exclusion constraint matching the ON CONFLICT specification

-- target is (event_id, name) but the clash is on the primary key:
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Super Early Bird', 29.00)
ON CONFLICT (event_id, name) DO NOTHING;
ERROR:  duplicate key value violates unique constraint "price_tier_pkey"
DETAIL:  Key (id)=(bbbbbbbb-0000-0000-0000-000000000010) already exists.

-- naming the rule directly also works:
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00)
ON CONFLICT ON CONSTRAINT price_tier_name_unique_per_event DO NOTHING;
INSERT 0 0
```

`ON CONFLICT` only covers the rule you named. A clash on any other rule is still an error.

### Your doubts

None for this lesson.

---

## Lesson 14 — `ON CONFLICT ... DO UPDATE`, and `EXCLUDED`

### The idea

`ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount` means "if the row already
exists, update it instead".

- **`EXCLUDED`** = the row you tried to insert (it was kept out by the conflict).
  `EXCLUDED.amount` is the new amount you just sent.
- The table name (`price_tier.amount`) = the row already in the table.
- Only the columns after `SET` change. Others (like `created_at`) stay.
- The update path still replies `INSERT 0 1`. The reply cannot tell you insert from update.

### Example 1 — run twice with 42.00

```text
-- before: Early Bird | 39.0000 | created_at 2026-10-25 11:58:56.173184+00
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 42.00)
ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount;
INSERT 0 1
                  id                  |    name    |  amount   |          created_at
--------------------------------------+------------+-----------+-------------------------------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird |   42.0000 | 2026-10-25 11:58:56.173184+00
 bbbbbbbb-0000-0000-0000-000000000007 | General    |  799.0000 | 2026-10-25 11:30:35.691982+00
 bbbbbbbb-0000-0000-0000-000000000008 | Gold       | 1999.0000 | 2026-10-25 11:30:35.691982+00
(3 rows)

-- same statement again:
INSERT 0 1
                  id                  |    name    |  amount   |          created_at
--------------------------------------+------------+-----------+-------------------------------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird |   42.0000 | 2026-10-25 11:58:56.173184+00
 bbbbbbbb-0000-0000-0000-000000000007 | General    |  799.0000 | 2026-10-25 11:30:35.691982+00
 bbbbbbbb-0000-0000-0000-000000000008 | Gold       | 1999.0000 | 2026-10-25 11:30:35.691982+00
(3 rows)
```

### Example 2 — the proposed `id` is thrown away

```text
INSERT INTO price_tier (id, event_id, name, amount)
VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 42.00)
ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount
RETURNING id, name, amount;
                  id                  |    name    | amount
--------------------------------------+------------+---------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird | 42.0000
(1 row)

INSERT 0 1
```

`RETURNING` shows the existing row's id, not the random one, so the client always learns the
real id.

### Example 3 — `DO UPDATE ... WHERE`: only update when something differs

```text
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 42.00)
ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount
WHERE price_tier.amount IS DISTINCT FROM EXCLUDED.amount;
INSERT 0 0
```

The amount was already 42, so nothing was touched. With no conflict at all, `DO UPDATE` is a
plain insert (a new Student tier gave `INSERT 0 1`; that run was rolled back).

### Your doubts

None for this lesson.

---

## Lesson 15 — Running it twice: idempotency (S9)

### The idea

An operation is **idempotent** if doing it once or many times leaves the **same end state**.
Judge by the table, not by the reply line.

| Form | Repeat behaviour | Idempotent? |
| --- | --- | --- |
| `DO NOTHING` | Repeats are skipped; first value stays. | Yes |
| `DO UPDATE SET amount = EXCLUDED.amount` (set-shaped: "make it 42") | Repeats set the same value; converges on the latest. | Yes |
| `DO UPDATE SET sold = t.sold + EXCLUDED.sold` (add-shaped: "add 2") | Each repeat adds again. | **No** |

Link to F4.2: `POST /price-tiers` is not idempotent by itself; each call creates a row. With an
upsert behind it, a retried `POST` lands on the same row instead of creating a duplicate. Phase 5
relies on this for replayed messages and retried requests.

### Example 1 — set-shaped `DO UPDATE`, three more runs

```text
INSERT 0 1
INSERT 0 1
INSERT 0 1
SELECT count(*) AS early_bird_rows, max(amount) AS amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000005' AND name = 'Early Bird';
 early_bird_rows | amount
-----------------+---------
               1 | 42.0000
(1 row)
```

### Example 2 — `DO NOTHING` with 39 after the update

```text
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000010', 'aaaaaaaa-0000-0000-0000-000000000005', 'Early Bird', 39.00)
ON CONFLICT (event_id, name) DO NOTHING;
INSERT 0 0
                  id                  |    name    | amount
--------------------------------------+------------+---------
 bbbbbbbb-0000-0000-0000-000000000010 | Early Bird | 42.0000
(1 row)
```

### Example 3 — add-shaped upsert is NOT safe to retry (scratch counter)

```text
CREATE TABLE scratch_sold (
    event_id uuid PRIMARY KEY,
    sold     integer NOT NULL
);
INSERT INTO scratch_sold (event_id, sold)
VALUES ('aaaaaaaa-0000-0000-0000-000000000005', 2)
ON CONFLICT (event_id) DO UPDATE SET sold = scratch_sold.sold + EXCLUDED.sold;
```

| Run | Reply | `sold` afterwards |
| --- | --- | --- |
| 1 | `INSERT 0 1` | 2 |
| 2 | `INSERT 0 1` | 4 |
| 3 | `INSERT 0 1` | 6 |

The reply was identical every time; only the table shows the damage.

### Your doubts

None for this lesson.

---

## Final run-through — every subtask, in order

This is the complete solution, run end to end on a fresh copy of `zenith` taken on
2026-10-25 at 5:45 PM. All output below is real.

### Before you start — the event id changed

Task F5.6 added three events to `zenith`: Sufi Night, Indie Fest and Blues Brunch, with ids
`aaaaaaaa-0000-0000-0000-000000000005` to `...0007`. The earlier lessons used `...0005` for the
festival, which now clashes:

```text
INSERT INTO event (id, venue_id, name, starts_at) VALUES ('aaaaaaaa-0000-0000-0000-000000000005', ...);
ERROR:  duplicate key value violates unique constraint "event_pkey"
DETAIL:  Key (id)=(aaaaaaaa-0000-0000-0000-000000000005) already exists.
```

If you see this, the festival was **not** created; `...0005` is Sufi Night. (The Lesson 2
troubleshooting table calls this error harmless. That is only true if `...0005` is your
festival.) So this run-through uses ids that cannot clash: **`f507`** stands for F5.7.

| Row | Id |
| --- | --- |
| Festival event | `aaaaaaaa-0000-0000-0000-00000000f507` |
| General / Gold / Platinum | `bbbbbbbb-0000-0000-0000-00000000f501` / `...f502` / `...f503` |
| Early Bird | `bbbbbbbb-0000-0000-0000-00000000f504` |

Open psql: `docker exec -it pg psql -U postgres -d zenith`. Starting state:

```text
SELECT (SELECT count(*) FROM venue)      AS venues,
       (SELECT count(*) FROM event)      AS events,
       (SELECT count(*) FROM price_tier) AS tiers;
 venues | events | tiers
--------+--------+-------
      3 |      7 |     6
(1 row)
```

(`3 | 4 | 6` is also fine; then F5.6's events are not there and nothing clashes either way.)

### S1 — insert a venue with `RETURNING id`, then its child event

```text
INSERT INTO venue (id, name, city, capacity)
VALUES (gen_random_uuid(), 'Harbour Dome', 'Kochi', 5000)
RETURNING id;
                  id
--------------------------------------
 9e2d5350-e88a-4943-ace1-4842109e91ad
(1 row)

INSERT 0 1
```

Copy your id (it will differ) into the event insert:

```text
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-00000000f507', '9e2d5350-e88a-4943-ace1-4842109e91ad', 'Harbour Lights Festival', '2027-01-15 18:00+05:30')
RETURNING id, venue_id, name;
                  id                  |               venue_id               |          name
--------------------------------------+--------------------------------------+-------------------------
 aaaaaaaa-0000-0000-0000-00000000f507 | 9e2d5350-e88a-4943-ace1-4842109e91ad | Harbour Lights Festival
(1 row)

INSERT 0 1
```

Why it matters: the child row needed the parent's new id, and `RETURNING` handed it over with no
second "look it up" query.

### S2 — several rows in one statement

```text
INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-00000000f501', 'aaaaaaaa-0000-0000-0000-00000000f507', 'General',  799),
('bbbbbbbb-0000-0000-0000-00000000f502', 'aaaaaaaa-0000-0000-0000-00000000f507', 'Gold',     1999),
('bbbbbbbb-0000-0000-0000-00000000f503', 'aaaaaaaa-0000-0000-0000-00000000f507', 'Platinum', 3999);
INSERT 0 3
```

Compared with S1's `INSERT 0 1`, the count is now 3: one per tuple, all stored or none.

### S3 — update one event by id, read `UPDATE 1`

```text
UPDATE event
SET name = 'Harbour Lights Festival 2027'
WHERE id = 'aaaaaaaa-0000-0000-0000-00000000f507';
UPDATE 1
```

### S4 — the missing `WHERE`, on a throwaway copy

```text
CREATE TABLE scratch AS SELECT * FROM event;
SELECT 8
UPDATE scratch SET name = 'oops';
UPDATE 8
SELECT name FROM scratch ORDER BY starts_at;
 name
------
 oops
 oops
 oops
 oops
 oops
 oops
 oops
 oops
(8 rows)

DROP TABLE scratch;
DROP TABLE
```

`UPDATE 8`: every event in the copy renamed, no error, no warning. The real `event` table still
has all 8 real names.

### S5 — the habit, used on the next delete

The habit is written down in Lesson 8: `WHERE` first, `SELECT` with that `WHERE`, `BEGIN;`, change,
compare counts, `COMMIT;` or `ROLLBACK;`.

### S6 — delete a tier; watch a venue delete get refused

```text
SELECT id, name, amount FROM price_tier
WHERE id = 'bbbbbbbb-0000-0000-0000-00000000f503';
                  id                  |   name   |  amount
--------------------------------------+----------+-----------
 bbbbbbbb-0000-0000-0000-00000000f503 | Platinum | 3999.0000
(1 row)

BEGIN;
BEGIN
DELETE FROM price_tier
WHERE id = 'bbbbbbbb-0000-0000-0000-00000000f503';
DELETE 1
COMMIT;
COMMIT
```

1 row in the `SELECT`, `DELETE 1`, so commit. Now the venue:

```text
DELETE FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';
ERROR:  update or delete on table "venue" violates foreign key constraint "event_venue_fk" on table "event"
DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) is still referenced from table "event".
```

Riverside Arena still has 4 events, so the foreign key refused. The venue count stayed at 4.

### S7 — where soft deletes belong

The rule is the table in Lesson 11: orders, payments, tickets and anything sold are soft-deleted
only; events use `cancelled_at`; unpublished drafts like a draft seat map may be hard-deleted.

### S8 + S9 — both upserts, each run twice, table checked after every run

```text
-- DO NOTHING, run 1
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-00000000f504', 'aaaaaaaa-0000-0000-0000-00000000f507', 'Early Bird', 39.00)
ON CONFLICT (event_id, name) DO NOTHING;
INSERT 0 1
                  id                  |    name    |  amount
--------------------------------------+------------+-----------
 bbbbbbbb-0000-0000-0000-00000000f504 | Early Bird |   39.0000
 bbbbbbbb-0000-0000-0000-00000000f501 | General    |  799.0000
 bbbbbbbb-0000-0000-0000-00000000f502 | Gold       | 1999.0000
(3 rows)

-- DO NOTHING, run 2 (same statement)
INSERT 0 0
 (same 3 rows: Early Bird 39.0000, General 799.0000, Gold 1999.0000)

-- DO UPDATE, run 1
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-00000000f504', 'aaaaaaaa-0000-0000-0000-00000000f507', 'Early Bird', 42.00)
ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount;
INSERT 0 1
                  id                  |    name    |  amount
--------------------------------------+------------+-----------
 bbbbbbbb-0000-0000-0000-00000000f504 | Early Bird |   42.0000
 bbbbbbbb-0000-0000-0000-00000000f501 | General    |  799.0000
 bbbbbbbb-0000-0000-0000-00000000f502 | Gold       | 1999.0000
(3 rows)

-- DO UPDATE, run 2 (same statement)
INSERT 0 1
 (same 3 rows: Early Bird 42.0000, General 799.0000, Gold 1999.0000)
```

The table check after each run:

```sql
SELECT id, name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-00000000f507' ORDER BY amount;
```

`DO NOTHING` made the repeat a no-op (`INSERT 0 0`, still 39). `DO UPDATE` converged on the
latest value (42 after both runs, still one Early Bird row). This is how a non-idempotent `POST`
is made safe to retry.

Final state:

```text
 venues | events | tiers
--------+--------+-------
      4 |      8 |     9
(1 row)
```

### S10 — the AI question, answered

> *"Explain INSERT ... ON CONFLICT in Postgres to a beginner, including what EXCLUDED refers to,
> and give me two examples from a ticketing system where DO NOTHING is correct and two where
> DO UPDATE is correct."*

**Explanation.** A normal `INSERT` fails if the new row breaks a unique rule.
`INSERT ... ON CONFLICT (columns)` tells Postgres what to do in that case instead of failing.
The columns must match a unique rule. `DO NOTHING` skips the new row and keeps the old one.
`DO UPDATE SET ...` changes the existing row. Inside `DO UPDATE`, **`EXCLUDED`** is the row you
tried to insert. `EXCLUDED.amount` is the amount you just sent. The table's own name refers to
the row already stored. The check and the action happen in one statement, so two requests
arriving at once cannot both insert.

**`DO NOTHING` is correct when the first write is the truth:**

1. **Payment webhook received twice.** Store each gateway event id in a table where it is the
   primary key: `INSERT INTO processed_webhook (gateway_event_id) VALUES (...) ON CONFLICT DO NOTHING`.
   `INSERT 0 0` means "already handled, do not charge or email again".
2. **Claiming a seat.** `ticket` has `UNIQUE (event_id, seat)`. Insert the ticket with
   `ON CONFLICT (event_id, seat) DO NOTHING`. `INSERT 0 1` means you got the seat. `INSERT 0 0`
   means someone else already has it. Overwriting would steal their seat.

**`DO UPDATE` is correct when the latest write is the truth:**

1. **Organiser sets a price.** `ON CONFLICT (event_id, name) DO UPDATE SET amount = EXCLUDED.amount`.
   "Early Bird costs 42" replayed any number of times still means 42.
2. **Nightly sync of venue data from a partner system.** Upsert each venue by its external id and
   `SET capacity = EXCLUDED.capacity, name = EXCLUDED.name`. The partner's latest data should
   replace yesterday's.

Warning: `DO UPDATE SET sold = t.sold + EXCLUDED.sold` is not safe to retry; each repeat adds again.

### Checkpoint answers

**1. What does `RETURNING` save you from doing, and why does that matter for parent and child
inserts?** It saves the second query that looks up the row you just inserted to learn its
generated id or `created_at`. Child rows need the parent's id. A lookup by name can return
several rows or another user's row, and it costs an extra trip to the database. `RETURNING`
gives the exact id in the same statement.

**2. `UPDATE 0` — two possible reasons.** (a) No row has that id: it is mistyped, deleted, or
never existed. (b) The row exists, but another condition rules it out. One example is a guard
like `AND cancelled_at IS NULL` on a cancelled event. Another is a comparison with `= NULL`,
which is never true. Look up the row by id alone to tell which.

**3. `DO NOTHING` vs `DO UPDATE`.** `DO NOTHING` keeps the existing row, so the first write wins.
A repeat replies `INSERT 0 0`. Pick it when the first write is the truth: a handled webhook, a
claimed seat. `DO UPDATE` overwrites the listed columns with `EXCLUDED` values, so the latest
write wins. It always replies `INSERT 0 1`. Pick it when the newest value is the truth: a price
set by the organiser, synced data. Both are safe to repeat if the update is set-shaped, and both
need a unique rule matching the conflict target.

**4. When should a delete be soft?** When anything could ask about the row later: money (orders,
payments, refunds), customers (tickets), other rows pointing at it, audits, or a need to restore
it. Hard-delete only throwaway data that nothing references and no money touched, such as a draft
seat map or a scratch table.

### Done when

- [x] Both upsert forms written (S8).
- [x] Each run twice with the expected result: `DO NOTHING` gave `INSERT 0 1`, then `INSERT 0 0`,
      staying at 39. `DO UPDATE` gave `INSERT 0 1` both times, staying at 42 (S9).
- [x] A missing-`WHERE` mass update caused and understood in a scratch table: `UPDATE 8` (S4).

---

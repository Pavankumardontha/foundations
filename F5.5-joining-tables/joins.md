# F5.5 — Joining tables

Notes for Task F5.5. One lesson per section. Each lesson ends with its understanding-check
questions and full answers, plus a **Do it yourself** block with exact commands. Every SQL
output shown here is real output captured from a running Postgres 17 container, not invented.

**Learning outcome.** Write inner, left, right and full joins. Say which rows each one keeps.
Spot the silent `LEFT JOIN` bug. Explain why joining multiplies rows.

---

## Starting state of the machine

These notes assume the state left behind by tasks F5.1 to F5.4:

- A Docker container named `pg` running the `postgres:17-alpine` image, port 5432 published.
- A database named `zenith` inside it.
- Three tables: `venue` (2 rows), `event` (3 rows), `price_tier` (5 rows).

### The three tables

```text
                            Table "public.venue"
     Column     |           Type           | Nullable | Default
----------------+--------------------------+----------+---------
 id             | uuid                     | not null |
 name           | text                     | not null |
 city           | text                     | not null |
 capacity       | integer                  | not null |
 seat_map_hints | jsonb                    |          |
 created_at     | timestamp with time zone | not null | now()
Indexes:
    "venue_pkey" PRIMARY KEY, btree (id)
Check constraints:
    "venue_capacity_positive" CHECK (capacity > 0)

                           Table "public.event"
    Column    |           Type           | Nullable | Default
--------------+--------------------------+----------+---------
 id           | uuid                     | not null |
 venue_id     | uuid                     | not null |
 name         | text                     | not null |
 starts_at    | timestamp with time zone | not null |
 created_at   | timestamp with time zone | not null | now()
 cancelled_at | timestamp with time zone |          |
Indexes:
    "event_pkey" PRIMARY KEY, btree (id)
Foreign-key constraints:
    "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)

                       Table "public.price_tier"
   Column   |           Type           | Nullable | Default
------------+--------------------------+----------+---------
 id         | uuid                     | not null |
 event_id   | uuid                     | not null |
 name       | text                     | not null |
 amount     | numeric(19,4)            | not null |
 created_at | timestamp with time zone | not null | now()
Indexes:
    "price_tier_pkey" PRIMARY KEY, btree (id)
    "price_tier_name_unique_per_event" UNIQUE CONSTRAINT, btree (event_id, name)
Check constraints:
    "price_tier_amount_non_negative" CHECK (amount >= 0::numeric)
Foreign-key constraints:
    "price_tier_event_fk" FOREIGN KEY (event_id) REFERENCES event(id)
```

### The seeded data

```text
SELECT id, name, city, capacity FROM venue ORDER BY name;

                  id                  |      name       |   city    | capacity
--------------------------------------+-----------------+-----------+----------
 11111111-1111-1111-1111-111111111111 | Riverside Arena | Hyderabad |    12000
 22222222-2222-2222-2222-222222222222 | The Blue Note   | Mumbai    |      350
(2 rows)

SELECT id, venue_id, name FROM event ORDER BY name;

                  id                  |               venue_id               |        name
--------------------------------------+--------------------------------------+--------------------
 aaaaaaaa-0000-0000-0000-000000000002 | 11111111-1111-1111-1111-111111111111 | Classical Evening
 aaaaaaaa-0000-0000-0000-000000000003 | 22222222-2222-2222-2222-222222222222 | Jazz at the Note
 aaaaaaaa-0000-0000-0000-000000000001 | 11111111-1111-1111-1111-111111111111 | Monsoon Rock Night
(3 rows)

SELECT event_id, name, amount FROM price_tier ORDER BY event_id, amount;

               event_id               |    name     |  amount
--------------------------------------+-------------+-----------
 aaaaaaaa-0000-0000-0000-000000000001 | General     | 1499.0000
 aaaaaaaa-0000-0000-0000-000000000002 | VIP         | 4999.0000
 aaaaaaaa-0000-0000-0000-000000000002 | General     |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | General     | 1999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Front Table | 3499.0000
(5 rows)
```

In words:

| Event | Venue | City | Its price tiers |
| --- | --- | --- | --- |
| Monsoon Rock Night | Riverside Arena | Hyderabad | General 1499, VIP 4999 |
| Classical Evening | Riverside Arena | Hyderabad | General 999 |
| Jazz at the Note | The Blue Note | Mumbai | General 1999, Front Table 3499 |

### Setting all of this up from nothing

Use this only if the machine has no container and no database yet. It was tested end to end
against Postgres 17 and runs without error.

**Step A — install Docker Desktop**, then check it works:

```bash
docker --version
```

**Step B — start a Postgres container:**

```bash
docker run -d --name pg \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data \
  postgres:17-alpine
```

**Step C — create the database:**

```bash
docker exec pg psql -U postgres -c "CREATE DATABASE zenith;"
```

**Step D — create and fill the tables.** Save this as `setup.sql`:

```sql
CREATE TABLE venue (
  id             uuid PRIMARY KEY,
  name           text NOT NULL,
  city           text NOT NULL,
  capacity       integer NOT NULL CHECK (capacity > 0),
  seat_map_hints jsonb,
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE event (
  id           uuid PRIMARY KEY,
  venue_id     uuid NOT NULL REFERENCES venue(id),
  name         text NOT NULL,
  starts_at    timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  cancelled_at timestamptz
);

CREATE TABLE price_tier (
  id         uuid PRIMARY KEY,
  event_id   uuid NOT NULL REFERENCES event(id),
  name       text NOT NULL,
  amount     numeric(19,4) NOT NULL CHECK (amount >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (event_id, name)
);

INSERT INTO venue (id, name, city, capacity) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000),
  ('22222222-2222-2222-2222-222222222222', 'The Blue Note',   'Mumbai',      350);

INSERT INTO event (id, venue_id, name, starts_at) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'Monsoon Rock Night', '2026-11-20 19:00+05:30'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'Classical Evening',  '2026-12-05 18:30+05:30'),
  ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
   'Jazz at the Note',   '2026-11-28 21:00+05:30');

INSERT INTO price_tier (id, event_id, name, amount) VALUES
  ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General',     1499),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP',         4999),
  ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General',      999),
  ('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General',     1999),
  ('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499);
```

Then load it:

```bash
docker exec -i pg psql -U postgres -d zenith -v ON_ERROR_STOP=1 < setup.sql
```

Expected tail of the output:

```text
CREATE TABLE
CREATE TABLE
CREATE TABLE
INSERT 0 2
INSERT 0 3
INSERT 0 5
```

**Step E — open a SQL prompt:**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. Type `\q` and press Enter to leave it.

---

## The subtasks of F5.5

| # | Subtask |
| --- | --- |
| S1 | Inner join `event` to `venue`. Return event name and city. Predict the row count, then check it. |
| S2 | Insert an event with no price tiers. Run the query as a `LEFT JOIN` to `price_tier`. Find that event with `NULL` in the tier columns. |
| S3 | Run the right join and the full join once each. Note that you will almost always write `LEFT` and swap the tables. |
| S4 | Break it on purpose: the silent inner join. Compare `WHERE pt.amount > 50` against `AND pt.amount > 50` inside `ON`. |
| S5 | Explain the fix in your own words. `WHERE` runs after the join. `ON` decides what counts as a match. |
| S6 | Break it on purpose: the Cartesian product. Join with no `ON`. Confirm the row count is the two table sizes multiplied. |
| S7 | Meet fan-out. Give one event three tiers. See the name three times. See `COUNT(*)` count tiers, not events. Fix it with `COUNT(DISTINCT e.id)`. |
| S8 | Write down why fan-out plus `LIMIT` hurts later. This is the `JOIN FETCH` pagination problem in Phase 3. |
| S9 | Write one three-table join: `event` to `venue` to `price_tier`, filtered by city, ordered by price. |
| S10 | Draw the four joins as diagrams in `NOTES.md`. Then run the AI question drill. |

## The lesson roadmap

| Lesson | What it teaches | Subtask it serves |
| --- | --- | --- |
| 1 | What a join is, and why data is split across tables | Foundation for all |
| 2 | Table aliases and qualified column names | All |
| 3 | The mental model: pair every row, then keep the pairs that pass `ON` | Foundation for all |
| 4 | `INNER JOIN`: keep only matched rows | S1 |
| 5 | The Cartesian product: what happens with no `ON` | S6 |
| 6 | `LEFT JOIN`: keep every left row, fill the right with `NULL` | S2 |
| 7 | `RIGHT JOIN` and `FULL JOIN` | S3 |
| 8 | `ON` versus `WHERE`: the silent inner join bug | S4, S5 |
| 9 | Fan-out: why one row can come back many times | S7 |
| 10 | Fan-out and counting: `COUNT(*)` versus `COUNT(DISTINCT e.id)` | S7 |
| 11 | Fan-out and `LIMIT`: the partial page trap | S8 |
| 12 | `USING`: the shorthand, and why this schema cannot use it | Vocabulary |
| 13 | Three-table joins with a filter and an order | S9 |
| — | Final run-through of every subtask, diagrams, AI drill | S10, checkpoint |

---

## Lesson 1 — What a join is, and why data is split across tables

### Definition

A **join** is one query that reads two tables together. It takes a row from one table, finds a
matching row in the other table, and returns them stuck side by side as a single wider row.

### Why it is needed

Facts are deliberately stored in separate tables. The `event` table knows *which venue*, but
not *which city*. Getting both in one answer is what a join is for.

Here is the whole of `event`:

```text
SELECT id, venue_id, name FROM event ORDER BY name;

                  id                  |               venue_id               |        name
--------------------------------------+--------------------------------------+--------------------
 aaaaaaaa-0000-0000-0000-000000000002 | 11111111-1111-1111-1111-111111111111 | Classical Evening
 aaaaaaaa-0000-0000-0000-000000000003 | 22222222-2222-2222-2222-222222222222 | Jazz at the Note
 aaaaaaaa-0000-0000-0000-000000000001 | 11111111-1111-1111-1111-111111111111 | Monsoon Rock Night
(3 rows)
```

A web page must show "Monsoon Rock Night — Hyderabad". There is no city here, so the obvious
attempt fails:

```text
SELECT city FROM event;

ERROR:  column "city" does not exist
LINE 1: SELECT city FROM event;
               ^
```

| Part of the error | Plain English |
| --- | --- |
| `column "city" does not exist` | The table named in `FROM` owns no column with that name. |
| `LINE 1: ... ^` | The caret points at the exact word Postgres could not resolve. |

The city is not missing. It is stored once, on `venue`:

```text
SELECT id, name, city, capacity FROM venue ORDER BY name;

                  id                  |      name       |   city    | capacity
--------------------------------------+-----------------+-----------+----------
 11111111-1111-1111-1111-111111111111 | Riverside Arena | Hyderabad |    12000
 22222222-2222-2222-2222-222222222222 | The Blue Note   | Mumbai    |      350
(2 rows)
```

**Why store it that way?** Riverside Arena sits in Hyderabad, and that stays true for every
event held there. If each event row carried its own copy of `'Hyderabad'`, two events would
hold two copies of one fact. One day somebody corrects one copy and forgets the other. Then
the database disagrees with itself. Storing each fact exactly once prevents that.

**The cost of not joining.** Without a join you run two queries:

```text
SELECT venue_id FROM event WHERE name = 'Monsoon Rock Night';

               venue_id
--------------------------------------
 11111111-1111-1111-1111-111111111111
(1 row)
```

Then you paste that value into a second query:

```text
SELECT city FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';

   city
-----------
 Hyderabad
(1 row)
```

Two queries for one line on a screen. A page listing all events costs one query for the list
plus one query per event. That is 1 + 3 = 4 queries here, and 1 + 500 = 501 queries with 500
events.

That pattern is the **N+1 problem**: one query to fetch N things, then N more queries to fill
in their details. It is the most common performance bug in database code. A join is the cure.

### How it works

The link already exists inside the data:

- `venue.id` holds `11111111-1111-1111-1111-111111111111`.
- `event.venue_id` holds `11111111-1111-1111-1111-111111111111` too.

The shared value *is* the link.

- A **primary key** is the column whose value names one row uniquely. In `venue` that is `id`.
  No two venues share an `id`.
- A **foreign key** is a column that stores another table's primary key value. In `event` that
  is `venue_id`. It holds the `id` of the venue where the event happens.

A join tells the database to follow that link for every row at once, inside one query. You
write the matching rule yourself: "the event's `venue_id` equals the venue's `id`".

### Example — real run

```text
SELECT e.name, v.city
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

        name        |   city
--------------------+-----------
 Classical Evening  | Hyderabad
 Jazz at the Note   | Mumbai
 Monsoon Rock Night | Hyderabad
(3 rows)
```

One query. Three events, each with its city.

### Walkthrough

| Clause | What it does |
| --- | --- |
| `FROM event e` | Start with the `event` table. Call it `e` for short. |
| `JOIN venue v` | Also bring in the `venue` table. Call it `v` for short. |
| `ON e.venue_id = v.id` | The matching rule. Pair an event row with a venue row only when the event's `venue_id` equals the venue's `id`. |
| `SELECT e.name, v.city` | From each paired row, return the name from the event side and the city from the venue side. |
| `ORDER BY e.name` | Sort the result by event name. |

Tracing one row by hand: the event `Monsoon Rock Night` has `venue_id` = `11111111-...`.
Postgres looks through `venue` for a row whose `id` equals `11111111-...`. It finds Riverside
Arena, whose city is Hyderabad. It builds one result row: `Monsoon Rock Night | Hyderabad`.
It repeats this for each of the 3 events.

Three things worth noticing:

1. The row count stayed at 3, matching the number of events. **That is not a rule of joins.**
   It happened because each event matched exactly one venue. Lesson 9 covers what happens when
   one row matches several rows.
2. `Hyderabad` appears twice in the output but is stored only once in `venue`. A join *copies*
   the value into the result. It does not duplicate the stored data.
3. A join never changes what is saved on disk. `event` and `venue` are untouched. The joined
   result exists only as the answer to that one query.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Looking for `city` on `event` because the event page shows a city | `ERROR: column "city" does not exist` | A web page is built for human eyes and mixes tables freely. A table stores each fact once. Run `\d event` to see which columns a table really owns. |
| Fetching the events, then looping in Java and running one query per event | 501 queries instead of 1. This is the N+1 problem. | Let the database do the matching. One join. |
| Thinking a join saves or changes data | Confusion about what the tables now hold | A join only builds a result for one query. The stored tables never change. |
| Copying `city` into the `event` table to avoid joining | Two copies of one fact. They drift apart, and the database contradicts itself. | Keep the fact in one place. Join to reach it. |

### Where it is used

- Any screen showing fields from more than one place: an order with the customer's name, a
  comment with the author's photo, an event with its city.
- Any API response built from several tables. A real `GET /events` returns event name, venue
  name, city and prices. That is three tables in one answer.
- Reports and exports, where every output row mixes columns from many tables.

### Link to the task

This is the foundation for **S1**: *"Write an inner join of `event` to `venue` returning the
event name and the city."* It also sets up **S8**, which asks you to connect joining to the
N+1 problem met again in Phase 3.

### Do it yourself

**Step 1 — start the database.**

```bash
docker ps
```

Expected: a line showing container `pg` with status `Up`. If nothing appears:

```bash
docker start pg
```

If `docker` itself is missing, do the "Setting all of this up from nothing" section above.

**Step 2 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt:

```text
zenith=#
```

**Step 3 — see that `event` has no city.**

```sql
SELECT id, venue_id, name FROM event ORDER BY name;
```

Expected: 3 rows, with a `venue_id` column and no `city` column.

**Step 4 — trigger the error on purpose.**

```sql
SELECT city FROM event;
```

Expected failure:

```text
ERROR:  column "city" does not exist
LINE 1: SELECT city FROM event;
               ^
```

**Step 5 — do it the slow way, by hand.**

```sql
SELECT venue_id FROM event WHERE name = 'Monsoon Rock Night';
```

Copy the value returned, then paste it inside the quotes below:

```sql
SELECT city FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';
```

Expected: `Hyderabad`.

**Done when:** you can say out loud why step 4 fails, and state how many queries a 500-event
page would need using the step 5 method. The answer is 501.

Leave the prompt with `\q`.

### Summary for revision

- A **join** reads two tables in one query and returns matched rows side by side.
- Tables are split on purpose so each fact is stored exactly once. That prevents two copies
  drifting apart.
- The link between tables is a shared value: a **foreign key** in one table holds the
  **primary key** value of a row in the other.
- `ERROR: column "..." does not exist` usually means "right database, wrong table".
- Avoiding joins by looping in application code gives the **N+1 problem**: 1 + N queries
  instead of 1.
- A join builds a result. It never changes the stored tables.

### Check questions, with answers

**Question 1.** A database has two tables.

```text
Table: author
 id | name
----+----------------
  1 | Ursula Le Guin
  2 | Terry Pratchett

Table: book
 id | author_id | title
----+-----------+----------------------
 10 |         1 | A Wizard of Earthsea
 11 |         2 | Mort
 12 |         2 | Guards! Guards!
```

Someone runs `SELECT name FROM book;` and gets `ERROR: column "name" does not exist`. They say
the author names are missing from the database. Are they right? Explain where the names are,
and which exact value in the `book` table is the road that leads to them.

**Answer.** They are wrong. The names are present, stored once each in the `author` table:
`Ursula Le Guin` in the row with `id` 1, and `Terry Pratchett` in the row with `id` 2. The
query failed only because `book` does not own a column called `name`; it owns `id`,
`author_id` and `title`. The road is the `author_id` value on each book row. For book 10 that
value is `1`, which points at the `author` row whose `id` is `1`, which holds
`Ursula Le Guin`. `author_id` is a foreign key: it stores the primary key value of a row in
`author`. Following that road inside one query is exactly what a join does.

**Question 2.** Using the same two tables shown in Question 1, a developer writes code that
does this:

1. Run `SELECT id, author_id, title FROM book;` to get all books.
2. For each book returned, run `SELECT name FROM author WHERE id = <that book's author_id>;`

How many queries does this send to the database in total, for the 3 books shown? Now imagine
the `book` table grows to 1000 rows while `author` stays at 2 rows. How many queries then?
What is this pattern called?

**Answer.** Four queries for the 3 books: one for the list, then one per book, so 1 + 3 = 4.
With 1000 books it becomes 1 + 1000 = 1001 queries. The pattern is the **N+1 problem**: one
query to fetch N things, then N more to fill in their details. Note that the size of `author`
is irrelevant — the query count is driven by the number of rows in the *outer* loop, not by
how much data is actually needed. Only two distinct author names exist, so 998 of those
queries re-fetch something already known. One join replaces all 1001 queries with 1.

### Your doubts

None for this lesson.

---

## Lesson 2 — Table aliases and qualified column names

### Definition

- A **qualified column name** is a column name with its table written in front, separated by a
  dot. `venue.city` means "the `city` column of the `venue` table".
- A **table alias** is a short nickname for a table inside one query. Writing `FROM event e`
  makes the letter `e` mean `event` for the rest of that query.

### Why it is needed

Two tables can own columns with the same name, and the database refuses to guess which one you
meant. In this schema, `venue`, `event` and `price_tier` **all** own a column called `name` and
a column called `id`.

```text
SELECT name
FROM event e
JOIN venue v ON e.venue_id = v.id;

ERROR:  column reference "name" is ambiguous
LINE 1: SELECT name FROM event e JOIN venue v ON e.venue_id = v.id;
               ^
```

| Part of the error | Plain English |
| --- | --- |
| `column reference "name" is ambiguous` | **Ambiguous** means "can be read in more than one way". Two tables in this query own a column called `name`. |
| `^` under `name` | The exact word Postgres could not resolve. |

This is a good error. A database that quietly picked one would hand you venue names where you
wanted event names, with no warning at all.

### How it works

**Part one — qualify the column.** Put the table name in front, with a dot:

```sql
SELECT event.name, venue.city
FROM event
JOIN venue ON event.venue_id = venue.id;
```

This works, but it is long. Real table names are longer than these.

**Part two — give the table a short nickname**, declared right after the table name:

```sql
FROM event e          -- or, identically:  FROM event AS e
```

Now `e` means `event`. Writing `AS` changes nothing. Most people leave it out for tables.

**The rule that surprises people:** once an alias is declared, it **replaces** the real table
name for that query. The original name stops working.

```text
SELECT event.name
FROM event e
JOIN venue v ON e.venue_id = v.id;

ERROR:  invalid reference to FROM-clause entry for table "event"
LINE 1: SELECT event.name FROM event e JOIN venue v ON e.venue_id = ...
               ^
HINT:  Perhaps you meant to reference the table alias "e".
```

Postgres prints the fix in the `HINT` line. An alias is not an extra name. Inside that query it
is *the* name.

### Example — real run, the same query three ways

All three return identical rows.

```text
-- Way one: no alias, fully qualified
SELECT event.name, venue.city
FROM event
JOIN venue ON event.venue_id = venue.id
ORDER BY event.name;

-- Way two: aliases with AS
SELECT e.name, v.city
FROM event AS e
JOIN venue AS v ON e.venue_id = v.id
ORDER BY e.name;

-- Way three: aliases without AS (the normal style)
SELECT e.name, v.city
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

        name        |   city
--------------------+-----------
 Classical Evening  | Hyderabad
 Jazz at the Note   | Mumbai
 Monsoon Rock Night | Hyderabad
(3 rows)
```

### Walkthrough

| Clause | Why the qualifier is needed |
| --- | --- |
| `FROM event e` | Declares `e` as the nickname for `event`. |
| `JOIN venue v` | Declares `v` as the nickname for `venue`. |
| `ON e.venue_id = v.id` | **Both** tables own an `id`, so a bare `id` here would fail. |
| `SELECT e.name, v.city` | Both tables own a `name`, so `name` alone would fail. |
| `ORDER BY e.name` | Same ambiguity applies here. Qualify in *every* clause. |

**A second problem aliases do not fix.** Asking for both names succeeds, because `e.name` and
`v.name` are each unambiguous — but look at the headers:

```text
SELECT e.name, v.name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

        name        |      name
--------------------+-----------------
 Classical Evening  | Riverside Arena
 Jazz at the Note   | The Blue Note
 Monsoon Rock Night | Riverside Arena
(3 rows)
```

**Two output columns are both labelled `name`.** A human can guess. Java code that reads a
result by column label cannot, and will take the wrong one or fail.

The fix is a **result column label**, written with `AS` in the `SELECT` list:

```text
SELECT e.name AS event_name, v.name AS venue_name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

     event_name     |   venue_name
--------------------+-----------------
 Classical Evening  | Riverside Arena
 Jazz at the Note   | The Blue Note
 Monsoon Rock Night | Riverside Arena
(3 rows)
```

### The two uses of `AS` — do not confuse them

| Where you write it | What it names | Example | Effect |
| --- | --- | --- | --- |
| After a table, in `FROM` or `JOIN` | A **table alias** | `FROM event AS e` | Lets you write `e.name` inside this query |
| After a column, in `SELECT` | A **result column label** | `SELECT e.name AS event_name` | Changes the header on the output column |

A table alias is for *writing* the query. A result column label is for *reading* the answer.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Bare column name that both tables own | `ERROR: column reference "name" is ambiguous` | Qualify it: `e.name` or `v.name`. |
| Declaring alias `e`, then writing `event.name` | `ERROR: invalid reference to FROM-clause entry for table "event"` | Use the alias everywhere once declared. |
| Forgetting to qualify inside `ON` or `ORDER BY` | Same ambiguity error, further down the query, easy to miss | Qualify in every clause. |
| Two output columns both labelled `name` | No error, but code reading by label gets the wrong value | Add result labels: `AS event_name`, `AS venue_name`. |
| Aliases like `a`, `b`, `t1`, `t2` | Unreadable six months later | Use the first letter or a short word: `e`, `v`, `pt`. |

### Where it is used

- Every join query in real code. Short aliases keep the `ON` condition readable.
- Any query needing the same table twice. Finding each employee and their manager means joining
  `employee` to `employee`. That is impossible without two different aliases, such as `emp` and
  `mgr`.
- Any query whose result feeds code. Clear result column labels are what the code reads by.

### Link to the task

Needed for **every** subtask, because every one joins tables that share column names. It matters
most for **S9**, the three-table join: with `event`, `venue` and `price_tier` all in one query,
three columns are called `name`, and the query cannot be written without aliases. The task
vocabulary lists it directly: *"Table alias — a short name such as `e` for `event`, so conditions
stay readable."*

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. If the container is not running, run `docker start pg` first.

**Step 2 — trigger the ambiguity error on purpose.**

```sql
SELECT name
FROM event e
JOIN venue v ON e.venue_id = v.id;
```

Expected failure:

```text
ERROR:  column reference "name" is ambiguous
LINE 1: SELECT name FROM event e JOIN venue v ON e.venue_id = v.id;
               ^
```

**Step 3 — fix it by qualifying the column.**

```sql
SELECT e.name, v.city
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected: 3 rows, event names with their cities.

**Step 4 — trigger the alias error on purpose.**

```sql
SELECT event.name
FROM event e
JOIN venue v ON e.venue_id = v.id;
```

Expected failure:

```text
ERROR:  invalid reference to FROM-clause entry for table "event"
HINT:  Perhaps you meant to reference the table alias "e".
```

**Step 5 — see the duplicate header, then fix it.**

```sql
SELECT e.name, v.name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected: 3 rows, **both** output columns headed `name`.

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected: the same 3 rows, now headed `event_name` and `venue_name`.

**Done when:** you can write the step 3 query from memory, and explain the difference between
the step 2 and step 4 errors without looking.

### Summary for revision

- `table.column` is a **qualified column name**. It removes doubt about which table is meant.
- `FROM event e` declares `e` as a **table alias**. `AS` is optional for tables.
- An alias **replaces** the table name inside that query. `event.name` then fails with
  `invalid reference to FROM-clause entry`.
- A bare column owned by two tables gives `column reference "..." is ambiguous`.
- Qualify in every clause: `SELECT`, `ON`, `WHERE`, `ORDER BY`.
- Duplicate output headers are legal but dangerous. Fix with `AS event_name` in the `SELECT`
  list.
- `AS` after a table names the table; `AS` after a column names the output header.

### Check questions, with answers

**Question 1.** A database has two tables.

```text
Table: team
 id | name      | city
----+-----------+--------
  1 | Red Lions | Pune
  2 | Blue Owls | Jaipur

Table: player
 id | team_id | name
----+---------+--------
 50 |       1 | Asha
 51 |       1 | Devi
 52 |       2 | Karan
```

Someone runs:

```sql
SELECT name, city
FROM player p
JOIN team t ON p.team_id = t.id;
```

It fails on `name` but would not have failed on `city`. Explain why those two columns behave
differently. Then rewrite the query so it returns each player's name next to their team's city,
with headers reading `player_name` and `team_city`.

**Answer.** `name` exists on **both** tables — `player.name` holds Asha, Devi, Karan, and
`team.name` holds Red Lions, Blue Owls. With both tables in one query, a bare `name` could mean
either, so Postgres stops with `column reference "name" is ambiguous` rather than guessing.
`city` exists on **only one** table, `team`, so there is nothing to guess and a bare `city`
resolves fine. Relying on that is still a bad habit: the day somebody adds a `city` column to
`player`, every query using bare `city` breaks. The rewrite:

```sql
SELECT p.name AS player_name, t.city AS team_city
FROM player p
JOIN team t ON p.team_id = t.id;
```

Expected result: `Asha | Pune`, `Devi | Pune`, `Karan | Jaipur` — 3 rows.

**Question 2.** Two queries over the same `team` and `player` tables shown in Question 1.

Query A:

```sql
SELECT p.name
FROM player p
JOIN team t ON p.team_id = t.id;
```

Query B:

```sql
SELECT player.name
FROM player p
JOIN team t ON p.team_id = t.id;
```

One succeeds and one fails with
`ERROR: invalid reference to FROM-clause entry for table "player"`. Say which is which, and
explain what rule about aliases causes the failure.

**Answer.** Query A succeeds and returns `Asha`, `Devi`, `Karan`. Query B fails. The rule is
that a table alias **replaces** the table name for the whole query rather than adding a second
usable name. Once `FROM player p` is written, the only way to refer to that table is `p`, so
`player.name` refers to nothing the query knows about. Postgres even prints
`HINT: Perhaps you meant to reference the table alias "p".` The fix is to use `p.name`, or to
drop the alias and write `FROM player` with `player.name` throughout.

### Your doubts

None for this lesson.

---

## Lesson 3 — The mental model: pair every row, then keep the pairs that pass `ON`

> **Data note.** Every output in this lesson was captured while `venue` held **2** rows.
> Lesson 4 adds a third venue, so pair counts grow from there on. The model itself does not
> change.

### Definition

A two-step picture of what any join does:

- **Step 1 — Pair.** Take every row of the first table. Pair it with every row of the second.
- **Step 2 — Test.** Look at each pair. Check the `ON` condition against it. Keep the pair if
  the condition is true. Throw it away if it is false.

That is the whole machine. Everything else in F5.5 is a variation on these two steps.

### Why it is needed

Without this picture, joins are a list of rules to memorise, and the rules look arbitrary. With
it, four things fall out for free:

- Why a missing `ON` explodes the row count — step 1 runs, step 2 never filters.
- Why one event can appear three times — one left row passes step 2 in three different pairs.
- Why `LEFT JOIN` is a small patch on step 2, not a different machine.
- Why a condition in `WHERE` behaves differently from the same condition in `ON` — one runs
  inside step 2, the other runs after both steps finish.

### How it works

With 3 events and 2 venues:

- **Step 1 — Pair.** Every event with every venue: 3 × 2 = 6 pairs. No condition yet. SQL has a
  name for stopping here: **`CROSS JOIN`** means "pair everything, test nothing".
- **Step 2 — Test.** Evaluate the `ON` condition on each of the 6 pairs. It is a question with a
  yes-or-no answer. Keep the yes pairs.

### Example — real run, step 2 made visible

This does step 1 only, but *prints* the answer the `ON` condition would give for each pair. In
psql, `t` means true and `f` means false.

```text
SELECT e.name AS event_name,
       v.name AS venue_name,
       e.venue_id = v.id AS on_condition
FROM event e
CROSS JOIN venue v
ORDER BY e.name, v.name;

     event_name     |   venue_name    | on_condition
--------------------+-----------------+--------------
 Classical Evening  | Riverside Arena | t
 Classical Evening  | The Blue Note   | f
 Jazz at the Note   | Riverside Arena | f
 Jazz at the Note   | The Blue Note   | t
 Monsoon Rock Night | Riverside Arena | t
 Monsoon Rock Night | The Blue Note   | f
(6 rows)
```

Now the real join, with step 2 applied for real:

```text
SELECT e.name AS event_name, v.name AS venue_name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

     event_name     |   venue_name
--------------------+-----------------
 Classical Evening  | Riverside Arena
 Jazz at the Note   | The Blue Note
 Monsoon Rock Night | Riverside Arena
(3 rows)
```

Three rows — exactly the three pairs marked `t`.

### Walkthrough

Classical Evening and Monsoon Rock Night both hold `venue_id` = `11111111-...`, which is
Riverside Arena's `id`. Jazz at the Note holds `venue_id` = `22222222-...`, which is The Blue
Note's `id`.

| Pair | Event's `venue_id` | Venue's `id` | Equal? | Step 2 decides |
| --- | --- | --- | --- | --- |
| Classical Evening + Riverside Arena | `11111111-...` | `11111111-...` | yes | **keep** |
| Classical Evening + The Blue Note | `11111111-...` | `22222222-...` | no | drop |
| Jazz at the Note + Riverside Arena | `22222222-...` | `11111111-...` | no | drop |
| Jazz at the Note + The Blue Note | `22222222-...` | `22222222-...` | yes | **keep** |
| Monsoon Rock Night + Riverside Arena | `11111111-...` | `11111111-...` | yes | **keep** |
| Monsoon Rock Night + The Blue Note | `11111111-...` | `22222222-...` | no | drop |

Six pairs in, three kept. That matches the real output exactly.

### Two consequences of the model

**1. The `ON` condition is just a true-or-false test. It need not compare keys.**

```text
SELECT e.name AS event_name, v.name AS venue_name, v.capacity
FROM event e
JOIN venue v ON v.capacity > 1000
ORDER BY e.name;

     event_name     |   venue_name    | capacity
--------------------+-----------------+----------
 Classical Evening  | Riverside Arena |    12000
 Jazz at the Note   | Riverside Arena |    12000
 Monsoon Rock Night | Riverside Arena |    12000
(3 rows)
```

Step 1 makes the same 6 pairs. Step 2 asks `v.capacity > 1000`. Riverside Arena has 12000 seats,
so every pair containing it passes. The Blue Note has 350, so every pair containing it fails.
Three pairs survive.

The result is nonsense as *data* — it claims Jazz at the Note is at Riverside Arena, which is
false. The query is still perfectly legal SQL. **The database tests the condition you wrote; it
does not know what you meant.** Matching foreign key to primary key is a discipline you apply,
not a rule SQL enforces.

**2. The output row count is not fixed by the input row counts.**

Here it is 3. With no condition it is 6. With a condition nothing passes it is 0. Lesson 9 shows
a case where the answer is *larger* than either table. Output rows = pairs that survive step 2.

### One honest caveat

Postgres does not really build all 6 pairs and then discard 3. On a million-row table that would
be absurd; it uses indexes and smarter algorithms. But the **answer is always identical** to what
this model predicts. Use the model to reason about *what* comes back. Leave *how fast* to the
database.

### Common mistakes

| Mistake | Why it is wrong | The right picture |
| --- | --- | --- |
| Thinking a join "looks up" exactly one partner per row | It makes no such promise. A row can pass with zero partners, one, or many. | It tests pairs. Count the pairs that pass. |
| Thinking `ON` must use `=` | Any true-or-false condition is allowed, including `>`, `<>`, and conditions touching one table only. | `ON` is a test, not a lookup. |
| Thinking `ON` must mention a foreign key | SQL does not check that. `ON v.capacity > 1000` runs happily and returns wrong-looking data. | Matching key to key is *your* discipline. |
| Believing the engine literally builds every pair | It does not; it uses indexes. | The model predicts the *answer*, not the speed. |
| Expecting output rows to equal the first table's row count | That only happens when each left row passes exactly once. | Output rows = pairs surviving step 2. |

### Where it is used

- **Predicting row counts before running a query.** F5.5 asks for this twice, on purpose.
- **Debugging a wrong result.** Too many rows or too few? Ask how many pairs step 1 made and
  which passed step 2. The bug is nearly always in step 2.
- **Reading an unfamiliar query.** Find the `ON` condition first; it defines what counts as a
  match.

### Link to the task

- **S6, the Cartesian product:** step 1 with step 2 skipped. The model predicts 3 × 2 = 6.
- **S7, fan-out:** one left row passing step 2 in several different pairs.
- **S4 and S5, the silent inner join:** a condition running *inside* step 2 versus *after* both
  steps.
- **S1:** predicting the row count before reading it.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. If the container is not running, run `docker start pg` first.

**Step 2 — count each table, so you know what step 1 will produce.**

```sql
SELECT count(*) AS events FROM event;
SELECT count(*) AS venues FROM venue;
```

Expected: `3` and `2`. (If you have already done Lesson 4, `venues` will be `3`; adjust the
arithmetic below accordingly.)

**Step 3 — before running anything else, write two numbers on paper.**

- How many pairs will step 1 make?
- How many of those pairs will pass the test `e.venue_id = v.id`?

**Step 4 — do step 1 only, and show the test result for every pair.**

```sql
SELECT e.name AS event_name,
       v.name AS venue_name,
       e.venue_id = v.id AS on_condition
FROM event e
CROSS JOIN venue v
ORDER BY e.name, v.name;
```

Expected: 6 rows, three `t` and three `f`.

**Step 5 — do step 2 for real and compare.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected: 3 rows, matching the three `t` rows from step 4.

**Step 6 — prove the `ON` condition need not involve keys.**

```sql
SELECT e.name AS event_name, v.name AS venue_name, v.capacity
FROM event e
JOIN venue v ON v.capacity > 1000
ORDER BY e.name;
```

Expected: 3 rows, every one naming Riverside Arena. Say out loud why the data is wrong but the
query is legal.

**Done when:** both numbers from step 3 were right, and you can explain why step 6 pairs Jazz at
the Note with the wrong venue.

### Summary for revision

- A join is **pair, then test**. Step 1 pairs every row with every row. Step 2 keeps pairs where
  `ON` is true.
- `CROSS JOIN` is step 1 alone — pair everything, test nothing.
- `ON` is any true-or-false condition. It does not have to compare keys, and SQL will not warn
  you if it does not.
- Output rows = pairs that survive step 2. That number can be smaller, equal to, or larger than
  either table.
- The engine does not literally build all pairs, but it always produces the answer this model
  predicts.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: colour
 id | shade
----+-------
  1 | red
  2 | blue
  3 | green

Table: garment
 id | colour_id | label
----+-----------+--------
 10 |         2 | scarf
 11 |         2 | hat
 12 |         9 | glove
 13 |         1 | sock
```

Query:

```sql
SELECT g.label, c.shade
FROM garment g
JOIN colour c ON g.colour_id = c.id;
```

1. How many pairs does step 1 produce?
2. How many pass step 2, and which garment labels appear in the result?
3. One garment is missing. Which one, and why did every pair containing it fail?

**Answer.**

1. **12 pairs.** `garment` has 4 rows, `colour` has 3, and step 1 pairs every row with every
   row: 4 × 3 = 12.
2. **3 pairs pass.** The result is `scarf | blue`, `hat | blue`, `sock | red`. Scarf and hat both
   hold `colour_id` 2, which equals the `id` of the blue row. Sock holds `colour_id` 1, which
   equals the `id` of the red row.
3. **Glove is missing.** It holds `colour_id` 9, and no row in `colour` has `id` 9 — the ids
   present are 1, 2 and 3. So all three pairs containing glove (glove+red, glove+blue,
   glove+green) tested false and were dropped. Note that nothing is wrong with the *query*; the
   row simply has no partner. Also note `green` never appears in the output either, because no
   garment points at `id` 3. A join that keeps only matching pairs silently drops both sides'
   leftovers.

**Question 2.** Two tables, shown in full:

```text
Table: room
 id | room_name | seats
----+-----------+-------
  1 | Hall A    |   100
  2 | Hall B    |    40

Table: class
 id | room_id | subject
----+---------+---------
  7 |       1 | Physics
  8 |       2 | Latin
```

Query, written to list each class with the room it is held in:

```sql
SELECT cl.subject, r.room_name
FROM class cl
JOIN room r ON r.seats > 30;
```

Say exactly which rows come back. Then explain why the database raises no error, even though the
result does not mean what the author intended.

**Answer.** Step 1 makes 2 × 2 = 4 pairs: Physics+Hall A, Physics+Hall B, Latin+Hall A,
Latin+Hall B. Step 2 asks `r.seats > 30` of each pair. Hall A has 100 seats and Hall B has 40, so
**both rooms pass, and therefore all four pairs pass**. The result is four rows:
`Physics | Hall A`, `Physics | Hall B`, `Latin | Hall A`, `Latin | Hall B`.

Three of those are false statements about the world — Physics is only in Hall A, and Latin is
only in Hall B. No error is raised because the query is valid SQL and the condition is a
well-formed true-or-false test. The database checks *syntax and types*, not *intent*. It never
inspects whether you used the foreign key `class.room_id`, and a foreign key constraint
constrains what may be **stored**, never what a query must **compare**. The author meant
`ON cl.room_id = r.id`, which would return the 2 correct rows. A good warning sign: the output
had more rows than the `class` table, which is the fan-out signature.

### Your doubts

None for this lesson.

---

## Lesson 4 — `INNER JOIN`: keep only the rows that matched

> **Data change made in this lesson.** One row was added to `venue`: `Lakeside Hall`, Chennai,
> 800 seats, id `33333333-3333-3333-3333-333333333333`. It hosts **no** events. From here on,
> `venue` has 3 rows and `event` has 3 rows. Lessons 6 and 7 need this row.

### Definition

An **inner join** keeps only the pairs where the `ON` condition was true. Anything with no
partner is dropped. The word `INNER` is optional — bare `JOIN` means exactly the same thing.

### Why it is needed

It is the join to use when every row of the answer must have real data on **both** sides.
"List every event with its city" is an inner join; an event with no venue would be a broken row
on a web page.

The other half of the sentence is the dangerous half: **an inner join silently drops rows that
had no partner.** No warning, no blank row, no footnote. Knowing what vanished is the skill.

### How it works

Exactly the two steps from the pair-then-test model, with nothing added:

- **Step 1 — Pair.** Every row of the first table with every row of the second.
- **Step 2 — Test.** Keep a pair only if `ON` is true for it.

Every other join type adds something back after step 2. Inner adds nothing.

### Example — real run

The data, after adding Lakeside Hall:

```text
SELECT id, name, city, capacity FROM venue ORDER BY name;

                  id                  |      name       |   city    | capacity
--------------------------------------+-----------------+-----------+----------
 33333333-3333-3333-3333-333333333333 | Lakeside Hall   | Chennai   |      800
 11111111-1111-1111-1111-111111111111 | Riverside Arena | Hyderabad |    12000
 22222222-2222-2222-2222-222222222222 | The Blue Note   | Mumbai    |      350
(3 rows)

SELECT id, venue_id, name FROM event ORDER BY name;

                  id                  |               venue_id               |        name
--------------------------------------+--------------------------------------+--------------------
 aaaaaaaa-0000-0000-0000-000000000002 | 11111111-1111-1111-1111-111111111111 | Classical Evening
 aaaaaaaa-0000-0000-0000-000000000003 | 22222222-2222-2222-2222-222222222222 | Jazz at the Note
 aaaaaaaa-0000-0000-0000-000000000001 | 11111111-1111-1111-1111-111111111111 | Monsoon Rock Night
(3 rows)
```

No event row holds `33333333-...`, so Lakeside Hall hosts nothing.

The inner join — **this is the S1 query**:

```text
SELECT e.name AS event_name, v.city
FROM event e
INNER JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

     event_name     |   city
--------------------+-----------
 Classical Evening  | Hyderabad
 Jazz at the Note   | Mumbai
 Monsoon Rock Night | Hyderabad
(3 rows)
```

Three rows. Chennai is nowhere in the answer.

### Walkthrough

**Step 1 makes 9 pairs** (3 events × 3 venues), and here is what step 2 decides about each:

```text
SELECT e.name AS event_name,
       v.name AS venue_name,
       e.venue_id = v.id AS on_condition
FROM event e
CROSS JOIN venue v
ORDER BY v.name, e.name;

     event_name     |   venue_name    | on_condition
--------------------+-----------------+--------------
 Classical Evening  | Lakeside Hall   | f
 Jazz at the Note   | Lakeside Hall   | f
 Monsoon Rock Night | Lakeside Hall   | f
 Classical Evening  | Riverside Arena | t
 Jazz at the Note   | Riverside Arena | f
 Monsoon Rock Night | Riverside Arena | t
 Classical Evening  | The Blue Note   | f
 Jazz at the Note   | The Blue Note   | t
 Monsoon Rock Night | The Blue Note   | f
(9 rows)
```

**Every pair containing Lakeside Hall is `f`.** No event points at it, so no pair with it can
ever be true.

**Step 2 keeps the three `t` rows:**

```text
SELECT count(*) AS row_count
FROM event e
INNER JOIN venue v ON e.venue_id = v.id;

 row_count
-----------
         3
(1 row)
```

Lakeside Hall was thrown away in total silence. That is the defining behaviour of `INNER`.

### Predicting the row count from the schema, not by guessing

S1 asks you to predict before reading. Here is how to be *certain* rather than lucky. Look at
what the schema promises about `event.venue_id`:

```text
    Column    |  Type  | Nullable
--------------+--------+----------
 venue_id     | uuid   | not null
Foreign-key constraints:
    "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)
```

Three facts combine:

1. `venue_id` is `not null`, so every event row holds some venue id. None is blank.
2. The **foreign key constraint** means the database refuses to store a value there unless it
   already exists in `venue.id`. So the value always points at a real venue.
3. `venue.id` is the **primary key**, so it is unique. Exactly one venue row carries any given
   id.

Therefore **every event matches exactly one venue — never zero, never two.** The inner join
returns exactly one row per event: 3 events, 3 rows, guaranteed before running anything.

**Direction matters.** The schema guarantees every event has a venue. It guarantees nothing
about every venue having an event, and Lakeside Hall has none. Inner join drops leftovers from
*both* sides; the schema only protects one side.

### `JOIN` and `INNER JOIN` are identical

```sql
SELECT e.name, v.city FROM event e INNER JOIN venue v ON e.venue_id = v.id;
SELECT e.name, v.city FROM event e       JOIN venue v ON e.venue_id = v.id;
```

Bare `JOIN` means `INNER JOIN`. Many teams still write `INNER` so a reader never wonders whether
a word was left out by mistake.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Forgetting that unmatched rows vanish without warning | A dashboard quietly under-reports; Lakeside Hall never appears in a "venues and their events" report | Ask first: do I need rows that have no partner? If yes, use `LEFT JOIN`. |
| Assuming output has one row per left-table row | Only true when each left row matches exactly once | Check the schema. `NOT NULL` + foreign key → primary key is what guarantees exactly one. |
| Guessing the row count instead of deriving it | You cannot tell a wrong result from a right one | Derive it from the constraints. |
| Thinking `JOIN` and `INNER JOIN` differ | Wasted worry | They are identical. |
| Using an inner join to "clean up" missing data | Hides a real problem instead of showing it | If rows vanish, find out why. A `LEFT JOIN` reveals which have no partner. |

### Where it is used

- Most queries in a normal application: orders with their customer, comments with their author,
  events with their venue.
- Any query whose output must be complete on both sides — where a `NULL` would break the screen
  or the arithmetic.
- Narrowing deliberately: "customers who placed at least one order" is an inner join from
  `customer` to `order`, and dropping order-less customers is the whole point.

### Link to the task

Completes **S1**: *"Write an inner join of `event` to `venue` returning the event name and the
city. Count the rows and check the number is what you expected before reading it."* The query is
above; the predicted count of 3 comes from the `NOT NULL` plus foreign key reasoning.

Also sets up **S3**: Lakeside Hall, the row this join just discarded, is what `RIGHT JOIN` and
`FULL JOIN` will bring back.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. If the container is not running, run `docker start pg` first.

**Step 2 — add the venue that hosts nothing.** Skip if you already have three venues.

```sql
INSERT INTO venue (id, name, city, capacity)
VALUES ('33333333-3333-3333-3333-333333333333', 'Lakeside Hall', 'Chennai', 800);
```

Expected output:

```text
INSERT 0 1
```

That reads as "0 object ids returned, 1 row inserted". The `1` is the part that matters.

**Step 3 — look at both tables.**

```sql
SELECT id, name, city FROM venue ORDER BY name;
SELECT id, venue_id, name FROM event ORDER BY name;
```

Expected: 3 venues, 3 events. Confirm by eye that no event holds `33333333-...`.

**Step 4 — predict before running.** Write two numbers on paper: how many pairs step 1 makes,
and how many rows the inner join returns, with the reason.

**Step 5 — run the inner join.**

```sql
SELECT e.name AS event_name, v.city
FROM event e
INNER JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected:

```text
     event_name     |   city
--------------------+-----------
 Classical Evening  | Hyderabad
 Jazz at the Note   | Mumbai
 Monsoon Rock Night | Hyderabad
(3 rows)
```

**Step 6 — confirm the count, then confirm what vanished.**

```sql
SELECT count(*) AS row_count
FROM event e
INNER JOIN venue v ON e.venue_id = v.id;
```

Expected: `3`. Then scan step 5's output for `Chennai`. It is not there.

**Step 7 — prove bare `JOIN` means the same.**

```sql
SELECT e.name AS event_name, v.city
FROM event e
JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;
```

Expected: the identical 3 rows.

**Done when:** your step 4 prediction of 3 was right, you justified it with `NOT NULL` and the
foreign key rather than by counting output, and you can name the row the join discarded.

To remove the extra venue later (Lessons 6 and 7 still need it, so not yet):

```sql
DELETE FROM venue WHERE id = '33333333-3333-3333-3333-333333333333';
```

### Summary for revision

- `INNER JOIN` keeps only pairs where `ON` was true. `JOIN` alone means the same.
- Unmatched rows on **either** side disappear, with no warning of any kind.
- Row count is derivable, not guessable: `NOT NULL` + foreign key → unique primary key means
  **exactly one** match per left row.
- A schema constraint protects one direction only. Every event has a venue; not every venue has
  an event.
- If you need the unmatched rows too, that is a `LEFT JOIN`.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: publisher
 id | house_name
----+-------------
  1 | Oak Press
  2 | Pine Books
  3 | Cedar House

Table: title
 id | publisher_id | book_name
----+--------------+------------
 20 |            1 | Low Tide
 21 |            1 | High Wind
 22 |            2 | Still Air
```

Query:

```sql
SELECT t.book_name, p.house_name
FROM title t
INNER JOIN publisher p ON t.publisher_id = p.id;
```

1. How many rows come back, and what are they?
2. Something present in the database appears nowhere in the output. Name it and say why.
3. Someone reads the output and says "good, that is every publisher we work with". Why is that
   conclusion wrong?

**Answer.**

1. **3 rows:** `Low Tide | Oak Press`, `High Wind | Oak Press`, `Still Air | Pine Books`. Step 1
   makes 3 × 3 = 9 pairs; 3 pass the test.
2. **Cedar House** (publisher id 3) is missing. No title row holds `publisher_id` 3, so every
   pair containing Cedar House tested false and was dropped. Nothing in the output hints that a
   third publisher exists.
3. Because an inner join lists publishers **that have at least one title**, not all publishers.
   The output answers "which publisher does each title belong to", and a publisher with no
   titles can never appear. To genuinely list every publisher, you would start from `publisher`
   and use a `LEFT JOIN` to `title`, which keeps Cedar House with `NULL` in the book column.

**Question 2.** Structure of two tables as Postgres prints it:

```text
                  Table "shipment"
   Column    |  Type   | Nullable
-------------+---------+----------
 id          | integer | not null
 courier_id  | integer | not null
 tracking_no | text    | not null
Indexes:
    "shipment_pkey" PRIMARY KEY, btree (id)
Foreign-key constraints:
    "shipment_courier_fk" FOREIGN KEY (courier_id) REFERENCES courier(id)

                 Table "courier"
  Column  |  Type   | Nullable
----------+---------+----------
 id       | integer | not null
 name     | text    | not null
Indexes:
    "courier_pkey" PRIMARY KEY, btree (id)
```

`shipment` holds 40 rows, `courier` holds 6. Without seeing the data, how many rows does this
return, and why?

```sql
SELECT s.tracking_no, c.name
FROM shipment s
INNER JOIN courier c ON s.courier_id = c.id;
```

Second part: could you predict as confidently the row count of a query listing every courier
alongside its shipments?

**Answer.** **Exactly 40 rows.** Three printed facts force it. First, `courier_id` is `not null`,
so every shipment holds some courier id. Second, the foreign key `shipment_courier_fk` means the
database would have rejected any value not already present in `courier.id`, so each one points
at a real courier. Third, `courier_pkey` makes `courier.id` unique, so each value matches exactly
one courier row — never two. One match per shipment, 40 shipments, 40 rows. Step 1 builds
40 × 6 = 240 pairs and step 2 keeps 40 of them.

**Second part: no.** That question runs the other way, and the schema says nothing about it. A
courier could have 20 shipments, 1, or none at all. Nothing forbids an unused courier. So the
row count for a courier-first query depends entirely on the data, and an inner join would also
silently omit any courier with zero shipments. The asymmetry is the lesson: a foreign key
constrains the table that holds it, never the table it points at.

### Your doubts

None for this lesson.

---

## Lesson 5 — The Cartesian product: what happens when nothing is tested

### Definition

A **Cartesian product** is the result of pairing every row of one table with every row of
another, with **no condition applied at all**. It is step 1 of the pair-then-test model running
on its own, with step 2 missing.

Its size is arithmetic: rows in the first table **multiplied by** rows in the second.

### Why it is needed

You almost never want one. You need to recognise it because it is the most expensive accident
in SQL.

Ten rows and ten rows becomes a hundred — harmless-sounding. But the multiplication does not
stay small. Twenty thousand rows and twenty thousand rows is four hundred million. Worse, it
does not *look* broken: rows come back, and they look plausible. The only signals are that there
are far too many of them, and that any `SUM` or `COUNT` over them is nonsense.

### How it works

Three ways to write the same thing:

| Form | Syntax | Notes |
| --- | --- | --- |
| Deliberate | `FROM event CROSS JOIN venue` | Says "I meant this" to the next reader |
| Comma form (older style) | `FROM event, venue` | A comma means "pair them". No condition → full product |
| Always-true condition | `FROM event e JOIN venue v ON true` | Legal but rare |

**A fourth thing you might expect does not work.** Writing `JOIN` and omitting `ON` is refused:

```text
SELECT e.name, v.name FROM event e JOIN venue v;

ERROR:  syntax error at or near ";"
LINE 1: SELECT e.name, v.name FROM event e JOIN venue v;
                                                       ^
```

This is good news. **The modern `JOIN ... ON` syntax forces you to state a condition.** Postgres
reached the semicolon, found no `ON`, and rejected the query. You cannot create a Cartesian
product by accident this way. The comma form has no such protection — which is the single best
reason to prefer `JOIN ... ON` over commas in everything you write.

### Example — real run

With 3 events and 3 venues, the product is 3 × 3 = 9.

```text
SELECT count(*) AS cross_join_rows FROM event CROSS JOIN venue;

 cross_join_rows
-----------------
               9
(1 row)

SELECT count(*) AS comma_rows FROM event, venue;

 comma_rows
------------
          9
(1 row)

SELECT e.name AS event_name, v.name AS venue_name
FROM event e, venue v
ORDER BY e.name, v.name;

     event_name     |   venue_name
--------------------+-----------------
 Classical Evening  | Lakeside Hall
 Classical Evening  | Riverside Arena
 Classical Evening  | The Blue Note
 Jazz at the Note   | Lakeside Hall
 Jazz at the Note   | Riverside Arena
 Jazz at the Note   | The Blue Note
 Monsoon Rock Night | Lakeside Hall
 Monsoon Rock Night | Riverside Arena
 Monsoon Rock Night | The Blue Note
(9 rows)
```

### Walkthrough

Each event appears three times, once beside each venue. Classical Evening is listed at Lakeside
Hall, at Riverside Arena and at The Blue Note. Only one of the three is true. The database is
not lying — you asked for every event beside every venue, and that is what came back.

### How it happens by accident

Here is the honest inner join written in the old comma style:

```text
SELECT e.name AS event_name, v.name AS venue_name
FROM event e, venue v
WHERE e.venue_id = v.id
ORDER BY e.name;

     event_name     |   venue_name
--------------------+-----------------
 Classical Evening  | Riverside Arena
 Jazz at the Note   | The Blue Note
 Monsoon Rock Night | Riverside Arena
(3 rows)
```

Correct. The matching condition simply lives in `WHERE` rather than `ON`.

Now delete only the `WHERE` line and you get the 9-row product. **One missing line, three times
the rows, no error.** In real code this happens two ways:

1. **A forgotten condition.** Someone writes the comma list intending to add `WHERE` next, and
   gets distracted.
2. **A third table added to the list.** A query joins two tables correctly. Someone needs a
   column from a third, appends `, price_tier pt`, takes the column, and forgets to add
   `AND pt.event_id = e.id`. The result is now multiplied by every row in `price_tier`. With
   three uncorrelated tables the size is first × second × third.

### What the cost actually looks like — real measurements

`generate_series(1,4000)` is a built-in that returns the numbers 1 to 4000 as rows. It stands in
for a table of that size.

```text
-- baseline: a small filtered query
SELECT count(*) FROM generate_series(1,4000) a WHERE a = 1;
 count
-------
     1
Time: 0.697 ms

-- 4,000 paired with 4,000
SELECT count(*) FROM generate_series(1,4000) a CROSS JOIN generate_series(1,4000) b;
   count
----------
 16000000
Time: 458.786 ms

-- 20,000 paired with 20,000
SELECT count(*) FROM generate_series(1,20000) a CROSS JOIN generate_series(1,20000) b;
   count
-----------
 400000000
Time: 9352.869 ms (00:09.353)
```

| Input size | Output rows | Time |
| --- | --- | --- |
| filtered baseline | 1 | 0.697 ms |
| 4,000 × 4,000 | 16,000,000 | 458.786 ms |
| 20,000 × 20,000 | 400,000,000 | 9,352.869 ms |

Multiplying the input by five multiplied the output by twenty-five and the time by twenty. The
last query is roughly **thirteen thousand times** slower than the baseline — and that is only to
*count* the rows. Sending four hundred million rows to an application would be far worse, and
would probably exhaust memory first.

The task phrases this as "a query that should take a millisecond takes four minutes". The table
above is that sentence in real numbers.

### A legitimate use

Cartesian products are not forbidden, just rarely wanted. The honest use is **generating
combinations that do not exist yet**. A booking calendar needs a row for every venue on every
date in a month, including the empty ones: 3 venues × 30 dates = 90 slots to draw. There you
want all combinations, so you write `CROSS JOIN` and mean it. Spelling out the words is itself
the value — it tells the next reader "yes, this was intentional".

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Comma form with a forgotten `WHERE` | Full product, no error, far too many rows | Use `JOIN ... ON`; Postgres then refuses a missing condition |
| Third table added to a comma list without its condition | Row count multiplies again — the common real-world version | One `JOIN ... ON` per table; each join carries its own condition |
| Seeing too many rows and reaching for `DISTINCT` | Hides the symptom, keeps the cost — the engine still builds every row first | Find the missing condition. `DISTINCT` does not fix a bad join. |
| Assuming a slow query needs an index | An accidental product is slow whatever indexes exist | Count rows first. A suspiciously large round number suggests a product. |
| "My tables are small, so it does not matter" | Tables grow, and cost grows as the product, not the sum | 10 and 10 is 100; 10,000 and 10,000 is 100,000,000 |

### Where it is used

- **As a bug, mostly.** One of the first things an experienced developer checks when a query is
  slow or a total is far too big.
- **Deliberately, for grids:** every venue × every date, every product × every size.
- **As a diagnostic:** running the product on purpose and comparing its count with your real
  join tells you how selective the `ON` condition is.

### Link to the task

This is **S6**: *"Join `event` to `venue` with no `ON` clause and count the rows. Multiply your
two table sizes together and confirm the number."* Sizes are 3 and 3, prediction 9, real run 9.

It also answers **checkpoint question 4**: *"What is a Cartesian product and what causes one by
accident?"* — every row paired with every row, no condition; caused accidentally by the comma
form of a join with a missing or incomplete `WHERE`, most often when a third table is added and
its matching condition is not.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. If the container is not running, run `docker start pg` first.

**Step 2 — turn on timing.**

```sql
\timing on
```

Expected: `Timing is on.`

**Step 3 — count both tables and predict.**

```sql
SELECT count(*) AS events FROM event;
SELECT count(*) AS venues FROM venue;
```

Expected: `3` and `3` at this point in the task. Multiply on paper before step 4.

**Step 4 — build the product and count it.**

```sql
SELECT count(*) AS product_rows FROM event, venue;
```

Expected: `9`.

**Step 5 — look at the rows.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e, venue v
ORDER BY e.name, v.name;
```

Expected: 9 rows. Find one that states something false, such as Classical Evening at The Blue
Note.

**Step 6 — add the condition and watch 9 become 3.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e, venue v
WHERE e.venue_id = v.id
ORDER BY e.name;
```

Expected: 3 rows, all true.

**Step 7 — prove the modern syntax protects you.**

```sql
SELECT e.name, v.name FROM event e JOIN venue v;
```

Expected failure:

```text
ERROR:  syntax error at or near ";"
```

**Step 8 — feel the cost at scale.**

```sql
SELECT count(*) FROM generate_series(1,4000) a WHERE a = 1;
SELECT count(*) FROM generate_series(1,4000) a CROSS JOIN generate_series(1,4000) b;
SELECT count(*) FROM generate_series(1,20000) a CROSS JOIN generate_series(1,20000) b;
```

Expected counts: `1`, `16000000`, `400000000`. Expected times: under a millisecond, around half
a second, around ten seconds. Exact numbers vary by machine; the ratio is the point.

**Done when:** you predicted 9 before running step 4, you can state the size rule for a product,
and you can explain why step 7 failing is a feature.

Turn timing off with `\timing off`, and leave with `\q`.

### Summary for revision

- A **Cartesian product** pairs every row with every row, with no condition. Size = left × right.
- Three ways to write it: `CROSS JOIN`, a comma between tables, or `ON true`.
- `JOIN` with no `ON` is a **syntax error** — the modern syntax will not let you do it by
  accident. The comma form will.
- The classic accident: a comma list whose `WHERE` condition is missing or incomplete, usually
  after a third table is added.
- Cost grows multiplicatively. Measured here: 16 million rows in 459 ms, 400 million in 9.4 s,
  against 0.7 ms for a filtered baseline.
- `DISTINCT` does not fix it. The rows are built before duplicates are removed.
- Deliberate use: generating grids of combinations, where you write `CROSS JOIN` and mean it.

### Check questions, with answers

**Question 1.** Three tables with these row counts:

```text
Table: customer    -- 500 rows
Table: product     -- 200 rows
Table: warehouse   --  12 rows
```

Query, written to list customers with the products they ordered:

```sql
SELECT c.name, p.title, w.location
FROM customer c, product p, warehouse w;
```

1. How many rows come back?
2. No error is raised and the rows look reasonable at a glance. What is the first clue something
   is wrong?
3. Rewrite the `FROM` clause with `JOIN ... ON`. Describe what each `ON` condition must say, and
   explain why the rewrite prevents this bug.

**Answer.**

1. **1,200,000 rows.** With no condition anywhere, the size is the product of all three:
   500 × 200 × 12 = 1,200,000.
2. **The row count itself.** There are only 500 customers, so an answer about customers can
   never honestly need 1.2 million rows. Any output far larger than your largest input table is
   the signature of a missing join condition. A second clue: each customer name repeats 2,400
   times, and every customer is paired with every product whether they ordered it or not — so
   any `SUM` or `COUNT` over this result is meaningless.
3. The rewrite:

   ```sql
   FROM customer c
   JOIN order_line ol ON ol.customer_id = c.id
   JOIN product p     ON p.id = ol.product_id
   JOIN warehouse w   ON w.id = p.warehouse_id
   ```

   Each `ON` states how the newly added table relates to one already present: an order line
   belongs to a customer, names a product, and the product sits in a warehouse. The rewrite
   prevents the bug because `JOIN` without `ON` is a **syntax error** — Postgres refuses to run
   it, so a forgotten condition becomes an immediate failure instead of a silent 1.2-million-row
   result. Note also that the original query is unfixable as written: with no table linking
   customers to products, there is no way to know who ordered what, which the comma form let the
   author overlook entirely.

**Question 2.** Table `a` has 10 rows. Table `b` has 10 rows.

```sql
-- query one
SELECT count(*) FROM a CROSS JOIN b;

-- query two
SELECT count(*) FROM a JOIN b ON true;
```

First: what does each return, and do they differ? Second: a reviewer says "nobody writes
`CROSS JOIN` on purpose". Give a realistic counter-example, and say why the spelled-out form
beats a comma.

**Answer.** **Both return 100, and they do not differ.** Query one asks for every pair with no
test. Query two makes the same pairs and applies a test that is true every time, so no pair is
removed. Same 10 × 10 = 100 rows by two routes.

The reviewer is wrong. A realistic case: a booking calendar must show a cell for every venue on
every date of a month, **including the empty ones**. Pairing 3 venues with 30 dates gives the 90
slots the grid needs, and the ones with no booking are exactly the ones a scheduler cares about.
Other examples are every product paired with every size to build a stock matrix, or every
student paired with every exam slot to build a timetable.

Spelling out `CROSS JOIN` beats a comma because the two are indistinguishable to the database
but not to a human. A comma looks exactly like a join whose condition was forgotten, so every
future reader must stop and work out whether it was deliberate. `CROSS JOIN` states the intent
in the code itself, and a reviewer scanning for accidental products can skip it immediately.

### Your doubts

None for this lesson.

---

## Lesson 6 — `LEFT JOIN`: keep every left row, fill the right side with `NULL`

> **Data change made in this lesson.** One row was added to `event`: `Open Mic Night` at The
> Blue Note, id `aaaaaaaa-0000-0000-0000-000000000004`, with **no** price tiers. This is the
> S2 setup. From here on, `event` has 4 rows and `price_tier` has 5.

### Definition

A **left join** keeps **every** row from the first table, whether or not it found a partner. For
a row that found no partner, the columns from the second table come back as `NULL`.

The "left" table is simply the one written first, before the words `LEFT JOIN`.

### Why it is needed

"Show me all of these, plus their details if they have any" is a question an inner join cannot
answer.

A page listing every event with its ticket prices: a brand-new event has no prices set yet. With
an inner join that event vanishes completely, and nobody notices, because a missing row looks
like nothing at all. A left join keeps it, showing blank prices — which is the truth: the event
exists, the prices do not.

The task states it directly: **a `LEFT JOIN` is how you ask "all of these, plus their details if
they have any".**

### How it works

The two familiar steps, plus a third:

- **Step 1 — Pair.** Every row of the left table with every row of the right table.
- **Step 2 — Test.** Keep a pair only if the `ON` condition is true.
- **Step 3 — Rescue.** *(This step is what makes it a left join.)* Look at the left table again.
  Find any left row that produced **no** surviving pair in step 2. Add that row back on its own,
  with `NULL` in every column that came from the right table.

Steps 1 and 2 **are** an inner join. **A left join is an inner join plus a rescue step.**

Two naming notes:

- `LEFT JOIN` and `LEFT OUTER JOIN` are identical. `OUTER` is optional and adds nothing.
- An **outer join** is the general name for any join that rescues unmatched rows. Left, right and
  full are all outer joins. Inner is not.

### Example — real run

```text
SELECT id, venue_id, name FROM event ORDER BY name;

                  id                  |               venue_id               |        name
--------------------------------------+--------------------------------------+--------------------
 aaaaaaaa-0000-0000-0000-000000000002 | 11111111-1111-1111-1111-111111111111 | Classical Evening
 aaaaaaaa-0000-0000-0000-000000000003 | 22222222-2222-2222-2222-222222222222 | Jazz at the Note
 aaaaaaaa-0000-0000-0000-000000000001 | 11111111-1111-1111-1111-111111111111 | Monsoon Rock Night
 aaaaaaaa-0000-0000-0000-000000000004 | 22222222-2222-2222-2222-222222222222 | Open Mic Night
(4 rows)
```

**The inner join** — `Open Mic Night` is absent, with no hint it was removed:

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
INNER JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(5 rows)
```

**The same query as a left join** — one word changed:

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | VIP         | 4999.0000
 Open Mic Night     | «NULL»      |    «NULL»
(6 rows)
```

psql was set to print `«NULL»` instead of a blank with `\pset null '«NULL»'`. By default psql
shows `NULL` as empty space, which looks identical to an empty piece of text. Turn the marker on
while learning.

### Walkthrough

**Step 1 — Pair.** 4 events × 5 price tiers = **20 pairs**.

**Step 2 — Test.** The condition `pt.event_id = e.id` is true for 5 of them:

| Left row | Passing pairs |
| --- | --- |
| Monsoon Rock Night | 2 — General and VIP |
| Classical Evening | 1 — General |
| Jazz at the Note | 2 — General and Front Table |
| Open Mic Night | **0** |

Those 5 are exactly the inner join result.

**Step 3 — Rescue.** Only `Open Mic Night` produced no surviving pair, so it is added back alone
with `NULL` in `tier_name` and `amount`. 5 + 1 = **6 rows**.

```text
SELECT count(*) AS inner_rows FROM event e INNER JOIN price_tier pt ON pt.event_id = e.id;
 inner_rows
------------
          5

SELECT count(*) AS left_rows FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id;
 left_rows
-----------
         6
```

### Three things to notice

**1. The `NULL`s are real `NULL`s, not empty text.** Test with `IS NULL`, never `= NULL`.

```text
SELECT e.name AS event_name, pt.id AS tier_id, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.id IS NULL;

   event_name   | tier_id | tier_name | amount
----------------+---------+-----------+--------
 Open Mic Night | «NULL»  | «NULL»    | «NULL»
(1 row)
```

**Every** right-hand column is `NULL` on a rescued row, including `pt.id` — even though
`price_tier.id` is a primary key that can never be `NULL` in the stored table. The rescued row is
not a stored row; it is a row the join invented to hold the left side.

**2. That query is a named idiom: the anti-join.** A left join followed by
`WHERE <right-hand key> IS NULL` returns exactly the left rows with **no** partner. It answers
"which events have no prices", "which customers never ordered", "which products are in no
category".

Note carefully: this *is* a condition on the right-hand table inside `WHERE`, and here it is
deliberate and correct. Lesson 8 shows the same shape with a different condition quietly breaks
the join. The `IS NULL` test is the one case where keeping **only** the rescued rows is the goal.

**3. A left join does not promise one row per left row.** `Monsoon Rock Night` appears twice and
`Jazz at the Note` appears twice, because each has two tiers: 4 events produced 6 rows. The
promise is weaker and precise — **every left row appears at least once**. The result can never
have fewer rows than the left table, and can easily have more. Lesson 9 covers why.

### Which table is "left"?

The left table is the one written first. These are **not** the same question:

```sql
-- every event, prices if any
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id

-- every price tier, event if any
FROM price_tier pt LEFT JOIN event e ON pt.event_id = e.id
```

The second keeps every price tier. In this database it happens to return the same 5 matched rows
with nothing rescued, because `price_tier.event_id` is `NOT NULL` with a foreign key, so every
tier is guaranteed an event. **Swapping the order changes the question even when the data hides
the difference.**

Before writing a left join, say the sentence aloud: **"I want all of the ____, plus their ____ if
they have any."** Whatever fills the first blank goes on the left.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| `INNER JOIN` where you meant "all of these" | Rows silently vanish; `Open Mic Night` disappears from the event page | Say the sentence "all of the X, plus their Y if any". If that is the question, use `LEFT JOIN`. |
| Testing for a missing match with `= NULL` | Returns nothing, with no error — `NULL` is never equal to anything, not even `NULL` | Use `IS NULL`. |
| Reading psql's blank as empty text | You test `= ''` and find nothing | `\pset null '«NULL»'` |
| Expecting one output row per left row | Fan-out breaks it; `Monsoon Rock Night` appears twice | A left join promises **at least** one row per left row, not exactly one. |
| Tables in the wrong order | You keep all of the wrong thing | The left table is written first. Fill in "all of the ____" and put that first. |
| A `WHERE` condition on the right-hand table | Quietly turns the left join back into an inner join | See Lesson 8 — the most common join bug in professional code. |

### Where it is used

- **Listing pages that must be complete:** all events with prices, all categories with product
  counts, all employees with their last review.
- **Finding gaps:** the anti-join idiom — which rows have nothing attached.
- **Optional relationships:** a user may or may not have a profile picture.
- **Reports where a zero must show:** "sales per region" needs regions with no sales to appear
  as zero rather than go missing, where the gap would look like good news.

### Link to the task

This is **S2**: *"Insert an event whose `price_tier` list is empty. Now run the same query as a
`LEFT JOIN` to `price_tier` and find that event in the output with `NULL` in the tier columns."*
The event is `Open Mic Night`; the left join returns 6 rows against the inner join's 5.

It answers **checkpoint question 1**: *"Which rows does a `LEFT JOIN` keep that an `INNER JOIN`
throws away?"* — the left rows that found no partner, here `Open Mic Night`, kept with `NULL` on
the right.

It is also the groundwork for **S4 and S5**: you cannot see the silent inner join bug until you
know what a working left join looks like.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. If the container is not running, run `docker start pg` first.

**Step 2 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".` This lasts for the current psql session only.

**Step 3 — insert an event with no price tiers.** Skip if you already have four events.

```sql
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000004',
        '22222222-2222-2222-2222-222222222222',
        'Open Mic Night',
        '2026-12-12 20:00+05:30');
```

Expected: `INSERT 0 1`

**Step 4 — confirm it has no tiers.**

```sql
SELECT count(*) AS tiers_for_open_mic
FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000004';
```

Expected: `0`.

**Step 5 — predict two numbers on paper:** rows from the inner join, rows from the left join.

**Step 6 — run the inner join.**

```sql
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
INNER JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;
```

Expected: 5 rows, no `Open Mic Night`.

**Step 7 — change one word and run again.**

```sql
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;
```

Expected: 6 rows, ending with `Open Mic Night | «NULL» | «NULL»`.

**Step 8 — find the gap with the anti-join idiom.**

```sql
SELECT e.name AS event_with_no_tiers
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.id IS NULL;
```

Expected: one row, `Open Mic Night`.

**Step 9 — see why `= NULL` is wrong.**

```sql
SELECT e.name
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.id = NULL;
```

Expected: `(0 rows)` and **no error**. Nothing is ever equal to `NULL`, so the test is never
true. That silence is why `IS NULL` exists.

**Done when:** both step 5 predictions were right, you can point at the rescued row and name the
step that created it, and you can explain why step 9 returns nothing rather than an error.

### Summary for revision

- `LEFT JOIN` = inner join **plus a rescue step** for left rows that matched nothing.
- Rescued rows carry `NULL` in **every** right-hand column, including the right table's primary
  key.
- `LEFT JOIN` and `LEFT OUTER JOIN` are identical. Left, right and full are **outer** joins;
  inner is not.
- The left table is the one written **first**. Say "all of the ____, plus their ____ if any" and
  put the first blank on the left.
- Promise: every left row appears **at least once** — not exactly once.
- Find rows with no partner: `LEFT JOIN ... WHERE right.key IS NULL` (the **anti-join**).
- `= NULL` never matches anything and raises no error. Always `IS NULL`.
- In psql, `\pset null '«NULL»'` makes `NULL` distinguishable from empty text.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: ward
 id | ward_name
----+------------
  1 | Oncology
  2 | Paediatrics
  3 | Cardiology

Table: nurse
 id | ward_id | nurse_name
----+---------+------------
 70 |       1 | Meera
 71 |       1 | Sunil
 72 |       2 | Farah
```

Query A:

```sql
SELECT w.ward_name, n.nurse_name
FROM ward w
INNER JOIN nurse n ON n.ward_id = w.id;
```

Query B:

```sql
SELECT w.ward_name, n.nurse_name
FROM ward w
LEFT JOIN nurse n ON n.ward_id = w.id;
```

1. How many rows does each return, and what are query B's exact rows?
2. Name the extra row in B and say which step created it.
3. Write the query listing wards with no nurse. Explain why `= NULL` would return nothing.

**Answer.**

1. **Query A returns 3 rows:** `Oncology | Meera`, `Oncology | Sunil`, `Paediatrics | Farah`.
   **Query B returns 4 rows:** those same three, plus `Cardiology | NULL`.
2. The extra row is **`Cardiology | NULL`**. Step 1 paired each of the 3 wards with each of the
   3 nurses, giving 9 pairs; step 2 kept 3. Cardiology produced no surviving pair, because no
   nurse holds `ward_id` 3. **Step 3, the rescue step**, added Cardiology back alone with `NULL`
   in `nurse_name`. Note Oncology appearing twice is not a bug — it genuinely has two nurses.
3. The query:

   ```sql
   SELECT w.ward_name
   FROM ward w
   LEFT JOIN nurse n ON n.ward_id = w.id
   WHERE n.id IS NULL;
   ```

   This returns one row, `Cardiology`. Writing `WHERE n.id = NULL` instead returns zero rows and
   raises no error, because `NULL` means "unknown" and a comparison with an unknown is itself
   unknown, never true. SQL keeps only rows where `WHERE` is *true*, so an unknown result is
   discarded exactly like a false one. `IS NULL` exists precisely because `=` cannot do this job.

**Question 2.** Two tables, shown in full:

```text
Table: podcast
 id | show_name
----+------------
  1 | Deep Dive
  2 | Night Shift

Table: episode
 id | podcast_id | episode_title
----+------------+---------------
 90 |          1 | Pilot
 91 |          1 | Follow Up
```

A developer expects exactly 2 rows from:

```sql
SELECT p.show_name, ep.episode_title
FROM podcast p
LEFT JOIN episode ep ON ep.podcast_id = p.id;
```

How many rows actually come back, and which? Why does "a left join keeps every left row" not
mean "one row per left row"? What is the smallest number of rows this query could ever return
for these two tables, and why?

**Answer.** **3 rows:** `Deep Dive | Pilot`, `Deep Dive | Follow Up`, and `Night Shift | NULL`.

The two statements differ because the rescue step only *adds* rows; it never removes or merges
any. Step 2 keeps **one output row per surviving pair**, so a left row with two partners produces
two rows — Deep Dive matched both episodes. The rescue step then runs only for left rows with
*zero* surviving pairs, which is Night Shift alone. So the guarantee is "every left row appears
at least once", and a left row with many partners appears many times. The developer's expectation
of 2 would only hold if every podcast had at most one episode.

**The smallest possible number is 2.** The rescue step guarantees at least one row per left row,
and there are 2 podcasts, so 2 is the floor. It would be reached if `episode` were empty: both
podcasts would be rescued with `NULL` titles, giving exactly 2 rows. No amount of data can push
the count below the left table's row count.

### Your doubts

None for this lesson.

---

## Lesson 7 — `RIGHT JOIN` and `FULL JOIN`

### Definition

- A **right join** keeps every row from the **second** table (written after the join keyword).
  Unmatched right rows come back with `NULL` in the left-hand columns.
- A **full join** keeps every row from **both** tables. Unmatched rows on either side come back
  with `NULL` in the other side's columns.

### Why it is needed

Mostly to *recognise*, not to write. The task says so: *"Run the right and full versions once
each so you have seen them, then note that you will almost always write `LEFT` and reorder the
tables instead."*

Two real reasons to learn them:

1. **You will read other people's code.** A `RIGHT JOIN` should not slow you down.
2. **`FULL JOIN` has one genuine use** nothing else does well: comparing two sets and finding
   what is missing from each.

### How it works — one machine, four settings

All four joins share steps 1 and 2 and differ **only** in step 3:

| Join type | Step 3 rescues |
| --- | --- |
| `INNER JOIN` | nothing |
| `LEFT JOIN` | left rows that matched nothing |
| `RIGHT JOIN` | right rows that matched nothing |
| `FULL JOIN` | unmatched rows on **both** sides |

`OUTER` is optional throughout: `RIGHT JOIN` = `RIGHT OUTER JOIN`, `FULL JOIN` = `FULL OUTER
JOIN`.

### Example — real run, all four on the same tiny data

Unmatched rows are needed on **both** sides, so this uses throwaway rows written inline with
`WITH ... AS (VALUES ...)`. Set `a` has keys 1 and 2; set `b` has keys 2 and 3. Only key 2 is in
both.

```text
WITH a(k, fruit) AS (VALUES (1,'apple'), (2,'banana')),
     b(k, colour) AS (VALUES (2,'yellow'), (3,'cherry'))
SELECT a.k AS a_k, a.fruit, b.k AS b_k, b.colour
FROM a INNER JOIN b ON a.k = b.k;

 a_k | fruit  | b_k | colour
-----+--------+-----+--------
   2 | banana |   2 | yellow
(1 row)


FROM a LEFT JOIN b ON a.k = b.k;

 a_k | fruit  |  b_k   | colour
-----+--------+--------+--------
   1 | apple  | «NULL» | «NULL»
   2 | banana |      2 | yellow
(2 rows)


FROM a RIGHT JOIN b ON a.k = b.k;

  a_k   | fruit  | b_k | colour
--------+--------+-----+--------
      2 | banana |   2 | yellow
 «NULL» | «NULL» |   3 | cherry
(2 rows)


FROM a FULL JOIN b ON a.k = b.k;

  a_k   | fruit  |  b_k   | colour
--------+--------+--------+--------
      1 | apple  | «NULL» | «NULL»
      2 | banana |      2 | yellow
 «NULL» | «NULL» |      3 | cherry
(3 rows)
```

### Walkthrough

Step 1 makes 2 × 2 = 4 pairs. Step 2 keeps 1 (both keys are 2). Everything else is step 3.

| Join | Kept by step 2 | Step 3 adds | Total |
| --- | --- | --- | --- |
| `INNER` | banana + yellow | nothing | **1** |
| `LEFT` | banana + yellow | apple, `NULL` colour | **2** |
| `RIGHT` | banana + yellow | cherry, `NULL` fruit | **2** |
| `FULL` | banana + yellow | apple **and** cherry | **3** |

The matched row is identical in all four. The joins disagree only about the leftovers.

### Diagram of the four joins

```text
         LEFT ONLY        MATCHED        RIGHT ONLY
         (apple)       (banana/yellow)    (cherry)

INNER                  [##############]
LEFT     [###########] [##############]
RIGHT                  [##############] [###########]
FULL     [###########] [##############] [###########]
```

### On the real tables

`venue` holds `Lakeside Hall`, which hosts nothing. `event` has no orphans and never can, because
`event.venue_id` is `NOT NULL` with a foreign key. So the leftovers are one-sided.

```text
-- LEFT from event: every event, venue if any. Nothing to rescue.
SELECT e.name AS event_name, v.name AS venue_name
FROM event e LEFT JOIN venue v ON e.venue_id = v.id
ORDER BY v.name, e.name;

     event_name     |   venue_name
--------------------+-----------------
 Classical Evening  | Riverside Arena
 Monsoon Rock Night | Riverside Arena
 Jazz at the Note   | The Blue Note
 Open Mic Night     | The Blue Note
(4 rows)


-- RIGHT: every venue, event if any. Lakeside Hall rescued.
FROM event e RIGHT JOIN venue v ON e.venue_id = v.id

     event_name     |   venue_name
--------------------+-----------------
 «NULL»             | Lakeside Hall
 Classical Evening  | Riverside Arena
 Monsoon Rock Night | Riverside Arena
 Jazz at the Note   | The Blue Note
 Open Mic Night     | The Blue Note
(5 rows)


-- FULL: everything from both. Identical to RIGHT.
FROM event e FULL JOIN venue v ON e.venue_id = v.id

     event_name     |   venue_name
--------------------+-----------------
 «NULL»             | Lakeside Hall
 Classical Evening  | Riverside Arena
 Monsoon Rock Night | Riverside Arena
 Jazz at the Note   | The Blue Note
 Open Mic Night     | The Blue Note
(5 rows)
```

### Why `FULL JOIN` gave nothing extra

A full join rescues leftovers from both sides — but on the event side there are none, and the
schema guarantees there never will be:

```text
    Column    |  Type  | Nullable
--------------+--------+----------
 venue_id     | uuid   | not null
Foreign-key constraints:
    "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)
```

`NOT NULL` forbids a blank venue; the foreign key forbids pointing at a venue that does not
exist. Between them **an orphan event is impossible**, so the full join collapses into the right
join.

This generalises, and it is why `FULL JOIN` is rare: **in a well-constrained schema one side
usually cannot have orphans, so a full join degenerates into a left or right join.** Full joins
earn their keep where the two sides are *not* linked by a foreign key — comparing expected
invoices against received payments, for instance, where either side can hold entries the other
lacks.

### The reordering trick, proved

**`A RIGHT JOIN B` returns the same rows as `B LEFT JOIN A`.** You never need `RIGHT JOIN`; put
the table you want kept in full on the left.

```text
SELECT v.name AS venue_name, e.name AS event_name
FROM venue v
LEFT JOIN event e ON e.venue_id = v.id
ORDER BY v.name, e.name;

   venue_name    |     event_name
-----------------+--------------------
 Lakeside Hall   | «NULL»
 Riverside Arena | Classical Evening
 Riverside Arena | Monsoon Rock Night
 The Blue Note   | Jazz at the Note
 The Blue Note   | Open Mic Night
(5 rows)
```

Same 5 rows, same information. Only the on-screen column order differs, and that is decided by
the `SELECT` list, not the join.

**Why prefer the left form?** You read a query top to bottom, so the first table named is the one
you assume it is "about". `FROM venue v LEFT JOIN event e` matches its meaning — *all venues,
their events if any*. `FROM event e RIGHT JOIN venue v` makes your eye land on `event` first and
your brain reverse itself. In a query with four joins, one pointing backwards is how bugs get
past review.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Mixing up which side `RIGHT` keeps | You keep the opposite table from the one you meant | `RIGHT` keeps the table written **after** the keyword. If unsure, reorder and use `LEFT`. |
| Mixing `LEFT` and `RIGHT` in one query | Very hard to reason about with three or more tables | Pick one direction: always `LEFT`, reorder as needed. |
| Expecting `FULL JOIN` to add rows when a foreign key forbids orphans | You write `FULL` "for safety", get a right join, and are confused | Check the constraints: `NOT NULL` + foreign key means that side cannot have orphans. |
| `FULL JOIN` "just in case" | Slower, and hides which side you care about | Choose the join that states your real question. |
| Thinking `FULL JOIN` equals `UNION` | `UNION` stacks rows vertically; `FULL JOIN` lines them up side by side and pads with `NULL` | Use `FULL JOIN` for matched pairs side by side **plus** leftovers. |

### Where it is used

- **`RIGHT JOIN`: rarely on purpose.** Usually appears when someone added a table to an existing
  query and took the lazy fix instead of reordering.
- **`FULL JOIN`: reconciliation.** Two systems should hold the same records — which rows does
  each have that the other lacks? `FULL JOIN` plus `WHERE a.id IS NULL OR b.id IS NULL` shows
  both gaps in one pass.
- **`FULL JOIN`: merging two partial lists** where each source holds part of the picture.

### Link to the task

This is **S3**: *"Run the right and full versions once each so you have seen them, then note that
you will almost always write `LEFT` and reorder the tables instead."* Both have now been run on
the real tables, and the note to keep is the reordering rule proved above.

It also feeds **S10**, which asks for diagrams of all four joins — the box picture and the
rescue table above are both reusable.

### Do it yourself

**Step 1 — open a SQL prompt and make `NULL` visible.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — see all four joins on tiny data.** Run one at a time, changing only the join word:

```sql
WITH a(k, fruit) AS (VALUES (1,'apple'), (2,'banana')),
     b(k, colour) AS (VALUES (2,'yellow'), (3,'cherry'))
SELECT a.k AS a_k, a.fruit, b.k AS b_k, b.colour FROM a INNER JOIN b ON a.k = b.k;
```

Repeat with `LEFT JOIN`, `RIGHT JOIN`, `FULL JOIN`. Expected row counts in order: **1, 2, 2, 3**.

**Step 3 — predict.** `event` has 4 rows, `venue` has 3, `Lakeside Hall` hosts nothing. Write
down the row count for each of the next three queries.

**Step 4 — left join.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e LEFT JOIN venue v ON e.venue_id = v.id
ORDER BY v.name, e.name;
```

Expected: 4 rows, no `«NULL»`, no Lakeside Hall.

**Step 5 — right join.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e RIGHT JOIN venue v ON e.venue_id = v.id
ORDER BY v.name, e.name;
```

Expected: 5 rows, first being `«NULL» | Lakeside Hall`.

**Step 6 — full join.**

```sql
SELECT e.name AS event_name, v.name AS venue_name
FROM event e FULL JOIN venue v ON e.venue_id = v.id
ORDER BY v.name, e.name;
```

Expected: 5 rows, identical to step 5. Work out why before reading on.

**Step 7 — prove the reordering trick.**

```sql
SELECT v.name AS venue_name, e.name AS event_name
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
ORDER BY v.name, e.name;
```

Expected: 5 rows carrying the same information as step 5, columns swapped.

**Done when:** your step 3 predictions were right, and you can state the reordering rule and
explain why step 6 added nothing beyond step 5.

### Summary for revision

- Four joins, one machine. Only **step 3, the rescue step**, differs:
  inner rescues nothing, left rescues the left leftovers, right rescues the right leftovers,
  full rescues both.
- `OUTER` is optional everywhere.
- **`A RIGHT JOIN B` = `B LEFT JOIN A`.** Always write `LEFT` and reorder the tables.
- `FULL JOIN` degenerates into a left or right join whenever a `NOT NULL` foreign key makes
  orphans impossible on one side.
- `FULL JOIN`'s real use is reconciliation between two sets not linked by a foreign key.
- `FULL JOIN` is not `UNION`: `UNION` stacks rows, `FULL JOIN` lines them up side by side.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: driver
 id | driver_name
----+-------------
  1 | Ravi
  2 | Lin

Table: vehicle
 id | driver_id | plate
----+-----------+--------
 30 |         1 | KA-01
 31 |      NULL | KA-02
```

Vehicle 31 has `NULL` in `driver_id` — no driver assigned.

```sql
-- Query 1
SELECT d.driver_name, v.plate FROM driver d INNER JOIN vehicle v ON v.driver_id = d.id;
-- Query 2
SELECT d.driver_name, v.plate FROM driver d LEFT  JOIN vehicle v ON v.driver_id = d.id;
-- Query 3
SELECT d.driver_name, v.plate FROM driver d RIGHT JOIN vehicle v ON v.driver_id = d.id;
-- Query 4
SELECT d.driver_name, v.plate FROM driver d FULL  JOIN vehicle v ON v.driver_id = d.id;
```

How many rows each, and which? Then: which single query shows both the driver with no vehicle
and the vehicle with no driver?

**Answer.** Step 1 makes 2 × 2 = 4 pairs. Step 2 keeps exactly **one**: Ravi + KA-01. Note why
only one — vehicle 31's `driver_id` is `NULL`, and `NULL = 1` and `NULL = 2` are both *unknown*,
never true. A `NULL` foreign key matches nothing at all, not even another `NULL`.

- **Query 1 (`INNER`): 1 row** — `Ravi | KA-01`.
- **Query 2 (`LEFT`): 2 rows** — `Ravi | KA-01`, `Lin | NULL`. Lin owns no vehicle, so she is
  rescued.
- **Query 3 (`RIGHT`): 2 rows** — `Ravi | KA-01`, `NULL | KA-02`. KA-02 matched nothing, so it is
  rescued.
- **Query 4 (`FULL`): 3 rows** — `Ravi | KA-01`, `Lin | NULL`, `NULL | KA-02`. Both leftovers are
  rescued.

**Query 4, the `FULL JOIN`,** is the one that shows both gaps at once. This is the rare case
where `FULL JOIN` genuinely earns its place, and the reason is visible in the data: `driver_id`
is **nullable**, so the vehicle side *can* have orphans. Had it been `NOT NULL` with a foreign
key, the full join would have collapsed into the left join.

**Question 2.** A colleague sends this for review:

```sql
SELECT c.category_name, p.product_name
FROM product p
RIGHT JOIN category c ON p.category_id = c.id;
```

1. What question does it answer, and which table is kept in full?
2. Rewrite it as a `LEFT JOIN` returning the same information, and say why yours reads better.
3. The colleague wants to change it to `FULL JOIN` "so nothing can ever be missed". Given:

```text
                  Table "product"
    Column     |  Type   | Nullable
---------------+---------+----------
 id            | integer | not null
 category_id   | integer | not null
 product_name  | text    | not null
Foreign-key constraints:
    "product_category_fk" FOREIGN KEY (category_id) REFERENCES category(id)
```

What would `FULL JOIN` actually change, and why?

**Answer.**

1. It answers **"every category, with its products if it has any"**. `category` is kept in full,
   because `RIGHT JOIN` keeps the table written *after* the keyword. A category with no products
   appears once with `NULL` in `product_name`.
2. The rewrite:

   ```sql
   SELECT c.category_name, p.product_name
   FROM category c
   LEFT JOIN product p ON p.category_id = c.id;
   ```

   Identical rows. It reads better because the first table named is the one the query is about,
   so the code's shape matches the sentence "all categories, plus their products if any". In the
   original you must read to the end before learning that `category`, not `product`, is the
   subject — and in a query with several joins, one pointing backwards is easy to misread.
3. **`FULL JOIN` would change nothing at all.** It differs from `RIGHT JOIN` only by also
   rescuing unmatched rows on the `product` side, and the printed schema makes those impossible:
   `category_id` is `not null`, so no product lacks a category, and `product_category_fk` means
   any value stored there must already exist in `category`. An orphan product cannot be stored,
   so there is nothing extra to rescue. The change would add no rows, cost a little more, and
   mislead the next reader into thinking orphan products are expected. The colleague's instinct —
   "be safe" — is better served by reading the constraints.

### Your doubts

None for this lesson.

---

## Lesson 8 — `ON` versus `WHERE`: the silent inner join

### Definition

Two places a condition can go, which look interchangeable and are not:

- A condition in **`ON`** decides **what counts as a match** while the join is being built.
- A condition in **`WHERE`** filters the **finished result** after the join is done.

On an inner join they give the same answer. On a left join they do not, and the `WHERE` version
quietly destroys the thing that made it a left join.

### Why it is needed

The task singles this out: *"a `LEFT JOIN` followed by a `WHERE` on the right-hand table. That
silently turns it back into an inner join, because `NULL` fails the `WHERE`."* And: **"This is
the most common join bug in professional code."**

It is common for a cruel reason. People meet `ON` and `WHERE` on **inner** joins first, where
either placement gives the same rows, and form the habit "conditions go in `WHERE`" — correct and
harmless for months. Then they write a left join, keep the habit, and the query starts quietly
lying. No error, no notice: just fewer rows than there should be, and the missing rows are
exactly the ones the left join existed to protect.

### How it works — the order clauses actually run

The run order is **not** the order you write them in:

| Order | Clause | What it does |
| --- | --- | --- |
| 1st | `FROM` and `JOIN` | Pair, test with `ON`, rescue unmatched rows → the joined result |
| 2nd | `WHERE` | Looks at that finished result and throws rows away |
| 3rd | `SELECT` | Picks the columns to show |
| 4th | `ORDER BY` | Sorts what is left |

**The rescue step has already happened by the time `WHERE` runs.** `WHERE` sees the rescued rows
sitting in the result and is free to delete them — and it will, because every right-hand column
on a rescued row is `NULL`:

```text
SELECT NULL > 50 AS null_gt_50, NULL = NULL AS null_eq_null, (NULL > 50) IS NULL AS is_it_null;

 null_gt_50 | null_eq_null | is_it_null
------------+--------------+------------
 «NULL»     | «NULL»       | t
```

`NULL > 50` is not false — it is **`NULL`**, meaning *unknown*. So is `NULL = NULL`. This is
**three-valued logic**: a condition can be true, false, or unknown, and `NULL` means "no value
here", so any comparison with it is unanswerable rather than wrong.

The rule that closes the trap: **`WHERE` keeps a row only when its condition is true.** Unknown
rows are discarded exactly like false ones.

The whole bug in one sentence: **the rescue step adds the row, then `WHERE` removes it again.**

### Example — real run, the two queries from the task

4 events, 6 price tiers, `Open Mic Night` with none. Every tier costs more than 50.

```text
-- BROKEN: condition in WHERE
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 50
ORDER BY e.name;

        name        |  amount
--------------------+-----------
 Classical Evening  |  999.0000
 Jazz at the Note   | 1999.0000
 Jazz at the Note   | 3499.0000
 Monsoon Rock Night | 1499.0000
 Monsoon Rock Night | 4999.0000
(5 rows)


-- FIXED: condition in ON
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 50
ORDER BY e.name;

        name        |  amount
--------------------+-----------
 Classical Evening  |  999.0000
 Jazz at the Note   | 1999.0000
 Jazz at the Note   | 3499.0000
 Monsoon Rock Night | 1499.0000
 Monsoon Rock Night | 4999.0000
 Open Mic Night     |    «NULL»
(6 rows)
```

(These outputs were captured when `price_tier` held 5 rows. Lesson 9 adds a sixth tier, so the
counts become 6 and 7 — the 1-row gap between the two versions is the point, not the totals.)

```text
 where_version        on_version
---------------       ------------
             5                  6
```

**One word moved, one row's difference, no error either time.**

### Walkthrough

**The broken version:**

1. **Pair.** 4 events × 5 tiers = 20 pairs.
2. **Test** `pt.event_id = e.id` — 5 pairs survive.
3. **Rescue.** `Open Mic Night` had no surviving pair, so it is added with `NULL` in `pt.amount`.
   The joined result now holds **6 rows**.
4. **`WHERE pt.amount > 50` runs on those 6 rows.** The five real amounts (999, 1499, 1999, 3499,
   4999) are all true and stay. For `Open Mic Night`, `pt.amount` is `NULL`, so the test is
   `NULL > 50` → unknown → **discarded**.
5. Five rows out.

**The fixed version:**

1. **Pair.** Same 20 pairs.
2. **Test** `pt.event_id = e.id AND pt.amount > 50`. Stricter, but all five real tiers still pass.
3. **Rescue.** `Open Mic Night` still added with `NULL`.
4. **No `WHERE`.** Nothing deletes the rescued row.
5. Six rows out.

The difference is step 4: in the broken version a filter runs *after* the rescue and undoes it.

### A sharper example — threshold 2000

A threshold of 50 hides half the story, because every tier passes it. Tiers cost 999, 1499, 1999,
3499 and 4999, so only two clear 2000.

```text
-- WHERE version
SELECT e.name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 2000
ORDER BY e.name;

        name        |  tier_name  |  amount
--------------------+-------------+-----------
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(2 rows)


-- ON version
SELECT e.name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 2000
ORDER BY e.name;

        name        |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | «NULL»      |    «NULL»
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | VIP         | 4999.0000
 Open Mic Night     | «NULL»      |    «NULL»
(4 rows)
```

Look at **`Classical Evening`**. It *does* own a price tier — General at 999. But 999 does not
clear 2000, so that pair failed the stricter test and did not survive. With no surviving pair it
qualified for the rescue and came back with `NULL`.

**That is the deeper point: moving the condition into `ON` protects not only rows with no
partners, but rows whose partners all failed the condition.** The two queries answer genuinely
different questions:

| Query | The question it answers |
| --- | --- |
| Condition in `WHERE` | "Show me the expensive tiers, and which event each belongs to." |
| Condition in `ON` | "Show me every event, and its expensive tiers if it has any." |

Both are reasonable. The bug is asking the second and writing the first.

### Why the trap is so well hidden — inner joins really are interchangeable

```text
SELECT e.name, pt.amount
FROM event e INNER JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 2000 ORDER BY e.name;

        name        |  amount
--------------------+-----------
 Jazz at the Note   | 3499.0000
 Monsoon Rock Night | 4999.0000
(2 rows)


SELECT e.name, pt.amount
FROM event e INNER JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 2000
ORDER BY e.name;

        name        |  amount
--------------------+-----------
 Jazz at the Note   | 3499.0000
 Monsoon Rock Night | 4999.0000
(2 rows)
```

Identical — and the reason follows from the model. **An inner join has no rescue step**, so there
are no `NULL`-padded rows for `WHERE` to kill. Filtering during the test and filtering afterwards
remove the same rows. The habit is genuinely correct everywhere it was first learned, and only
turns dangerous once a rescue step exists. That is why this bug survives code review: it looks
like every other query in the file.

### The one deliberate exception

```text
SELECT e.name AS event_with_no_tiers
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.id IS NULL;

 event_with_no_tiers
---------------------
 Open Mic Night
(1 row)
```

The **anti-join** works *because* of the behaviour above, not in spite of it. `WHERE pt.id IS
NULL` is true only for rescued rows, so it keeps them and deletes everything matched — which is
the entire point.

| `WHERE` condition on the right-hand table | Effect | Bug? |
| --- | --- | --- |
| `IS NULL` on a right-hand key | Keeps **only** rescued rows | No — the deliberate anti-join |
| Any ordinary comparison (`> 50`, `= 'x'`) | Deletes **all** rescued rows | Almost always yes |

### How to spot it in review — three checks

1. **Find every `LEFT JOIN`, then read the `WHERE` clause.** If `WHERE` names a table on the
   right of a left join, stop and think.
2. **Check whether that condition is `IS NULL`.** If yes, probably the deliberate anti-join. Any
   other comparison, probably the bug.
3. **Count both ways.** Run it with the condition in `WHERE`, then in `ON`. If the counts differ,
   the left join was doing something, and you must decide which answer you wanted.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Filtering the right-hand table in `WHERE` after a `LEFT JOIN` | The left join silently becomes an inner join | Move the condition into `ON` with `AND`. |
| Thinking `NULL > 50` is false | It is **unknown**, a third value. The row is still discarded, but the reason matters when reasoning about `NOT` and `OR`. | `NULL` compared with anything is unknown; `WHERE` keeps only true. |
| Carrying the "conditions go in `WHERE`" habit from inner joins | Correct for inner, wrong for outer | Ask whether the join has a rescue step. |
| Worrying about filtering the **left** table in `WHERE` | This is fine — `WHERE e.name = 'x'` removes left rows, usually as intended | The danger is specifically the **right-hand** table of an outer join. |
| "Fixing" it by switching to `INNER JOIN` because the counts then match | You deleted the requirement instead of meeting it | Decide the question first: do you need rows with no partner? |
| Reading a `NULL` as "no partner exists" | It can also mean the partner existed but failed the `ON` condition — see `Classical Evening` above | Check the `ON` clause before interpreting a `NULL`. |

### Link to the task

**S4** — run the two queries and compare counts: 5 against 6 at threshold 50; 2 against 4 at
threshold 2000.

**S5** — explain the fix: *"a condition in `WHERE` is applied after the join and throws away rows
whose right side is `NULL`, which turns the outer join back into an inner one. A condition in
`ON` decides what counts as a match in the first place."*

**Checkpoint question 2** — *"Why does putting a condition on the right-hand table in `WHERE`
turn a left join into an inner one?"* Because `WHERE` runs after the rescue step; rescued rows
carry `NULL` in every right-hand column; a comparison against `NULL` is unknown, not true; and
`WHERE` keeps only true rows.

### Do it yourself

**Step 1 — open a SQL prompt and make `NULL` visible.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

```sql
\pset null '«NULL»'
```

**Step 2 — see why `NULL` fails a test.**

```sql
SELECT NULL > 50 AS null_gt_50, NULL = NULL AS null_eq_null, (NULL > 50) IS NULL AS is_it_null;
```

Expected:

```text
 null_gt_50 | null_eq_null | is_it_null
------------+--------------+------------
 «NULL»     | «NULL»       | t
```

Say aloud: the comparison is not false, it is unknown, and `WHERE` keeps only true.

**Step 3 — run the broken version.**

```sql
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 50
ORDER BY e.name;
```

Check that `Open Mic Night` is absent.

**Step 4 — run the fixed version.**

```sql
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 50
ORDER BY e.name;
```

Expected: one more row, ending `Open Mic Night | «NULL»`.

**Step 5 — compare counts directly.**

```sql
SELECT count(*) AS where_version
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id WHERE pt.amount > 50;

SELECT count(*) AS on_version
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 50;
```

Expected: the `on_version` is exactly one larger.

**Step 6 — raise the threshold and predict first.** Tiers cost 999, 1499, 1999, 2499, 3499, 4999.

```sql
SELECT e.name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 2000
ORDER BY e.name;

SELECT e.name, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 2000
ORDER BY e.name;
```

In the second result, find `Classical Evening` and explain why it shows `NULL` even though it
owns a price tier.

**Step 7 — prove it does not happen on inner joins.**

```sql
SELECT e.name, pt.amount
FROM event e INNER JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 2000 ORDER BY e.name;

SELECT e.name, pt.amount
FROM event e INNER JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 2000
ORDER BY e.name;
```

Expected: both identical.

**Done when:** you can write both the broken and the fixed version from memory, and explain why
`Classical Evening` appears with `NULL` in step 6's second query. The task's "done when" names
exactly this.

### Summary for revision

- Run order: **`FROM`/`JOIN` → `WHERE` → `SELECT` → `ORDER BY`.** The rescue step finishes before
  `WHERE` starts.
- `ON` decides **what counts as a match**. `WHERE` filters the **finished** result.
- Rescued rows carry `NULL` in every right-hand column. `NULL > 50` is **unknown**, not false,
  and `WHERE` keeps only **true** rows — so rescued rows die.
- Net effect: a `WHERE` on the right-hand table turns a `LEFT JOIN` into an `INNER JOIN`, with
  no error.
- **Fix:** move the condition into `ON` with `AND`.
- Moving it to `ON` also rescues rows whose partners all *failed* the condition, not just rows
  with no partners.
- On **inner** joins, `ON` and `WHERE` are interchangeable — no rescue step, nothing to kill.
  That is why the bad habit forms.
- Deliberate exception: `WHERE right.key IS NULL` is the anti-join, and is correct.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: shop
 id | shop_name
----+-----------
  1 | Corner Co
  2 | Hill Mart
  3 | Dock Shed

Table: sale
 id | shop_id | amount
----+---------+--------
 60 |       1 |    500
 61 |       1 |     20
 62 |       2 |     30
```

`Dock Shed` has no sales.

```sql
-- Query A
SELECT s.shop_name, sa.amount
FROM shop s LEFT JOIN sale sa ON sa.shop_id = s.id
WHERE sa.amount > 100;

-- Query B
SELECT s.shop_name, sa.amount
FROM shop s LEFT JOIN sale sa ON sa.shop_id = s.id AND sa.amount > 100;
```

1. List the exact rows each returns.
2. Query B returns `Hill Mart` with `NULL`, yet Hill Mart *has* a sale. Explain exactly why.
3. A report must show all three shops with their big sales, so a manager can see which have none.
   Which query is correct, and what goes wrong with the other?

**Answer.**

1. **Query A returns 1 row:** `Corner Co | 500`.
   **Query B returns 3 rows:** `Corner Co | 500`, `Hill Mart | NULL`, `Dock Shed | NULL`.
2. Hill Mart's only sale is 30, and 30 is not greater than 100. In Query B the amount test is
   part of the **matching condition**, so the pair Hill Mart + sale 62 fails the test and does not
   survive. Hill Mart therefore ends step 2 with **zero** surviving pairs, which makes it eligible
   for the rescue step, and it comes back alone with `NULL` in `amount`. The `NULL` does not mean
   "Hill Mart has no sales" — it means "Hill Mart has no sales *that qualify*". Those are
   different statements, and only the `ON` form can express the second.
3. **Query B is correct.** It returns one row per shop, so the manager sees all three and can read
   off that Hill Mart and Dock Shed have no sale above 100. Query A returns a single row,
   `Corner Co | 500`. Hill Mart is dropped because `30 > 100` is false, and Dock Shed is dropped
   because its rescued row holds `NULL` and `NULL > 100` is unknown — and `WHERE` keeps only true
   rows. The manager would conclude only one shop exists, and the two shops that most need
   attention are precisely the two that vanished.

**Question 2.** A developer must list every customer along with any support ticket raised in 2026.
They write:

```sql
SELECT c.customer_name, t.ticket_ref
FROM customer c
LEFT JOIN ticket t ON t.customer_id = c.id
WHERE t.opened_year = 2026;
```

`customer` has 1000 rows. The query returns 340 rows.

1. What is wrong, and which customers are missing?
2. Write the corrected query.
3. After the fix it returns 1120 rows. Why can a correct result exceed 1000, and what further
   change would "exactly one row per customer" need?

**Answer.**

1. The condition `t.opened_year = 2026` sits in `WHERE`, so it runs **after** the rescue step and
   deletes every rescued row — `NULL = 2026` is unknown, never true. The left join has been
   silently turned into an inner join. **Missing are every customer with no 2026 ticket**: both
   those who raised no tickets at all and those whose only tickets were from other years. The 340
   rows are 2026 tickets, not customers, and the developer's test ("I can see tickets and customer
   names") could never have caught this, because the rows that *are* returned look perfectly
   correct. The bug is entirely in what is absent.
2. The fix:

   ```sql
   SELECT c.customer_name, t.ticket_ref
   FROM customer c
   LEFT JOIN ticket t ON t.customer_id = c.id AND t.opened_year = 2026;
   ```

3. **1120 is correct and expected.** A left join guarantees each left row appears *at least* once,
   not exactly once. A customer with three 2026 tickets produces three rows. So 1120 means roughly
   1000 customers, where those with multiple qualifying tickets contributed extra rows while
   customers with none contributed a single `NULL` row each. The row count exceeding the left
   table is the ordinary signature of a one-to-many join — this is **fan-out**.

   For strictly one row per customer, the extra rows must be collapsed by aggregating: group by
   the customer and reduce the ticket side to a single value per group, for example
   `count(t.id) AS tickets_2026` or `string_agg(t.ticket_ref, ', ')`, with
   `GROUP BY c.id, c.customer_name`. Reaching for `DISTINCT` instead would not work here, because
   the rows genuinely differ — they carry different ticket references.

### Your doubts

None for this lesson.

---

## Lesson 9 — Fan-out: why one row comes back many times

> **Data change made in this lesson.** A third price tier was added to `Monsoon Rock Night`:
> `Balcony` at 2499, id `bbbbbbbb-0000-0000-0000-000000000006`. This is the S7 setup. Totals are
> now **3 venues, 4 events, 6 price tiers**.

### Definition

**Fan-out** is one row from the left table appearing several times in the result, because it
matched several rows on the right. The task's vocabulary: *"One left row appearing several times
because it matched several right rows. Not a bug in itself, but the cause of many."*

### Why it is needed

Fan-out is not an error, and nothing reports it — it is the correct output of a correct query.
But it quietly breaks three things built on top of it:

- **Totals.** Any `SUM` over a fanned-out join adds the same number more than once.
- **Counts.** `COUNT(*)` counts output rows, no longer the thing you meant to count.
- **Paging.** `LIMIT 10` takes ten rows, not ten events, so page one shows a partial wrong thing.

Those are Lessons 9, 10 and 11. This lesson is the phenomenon and the damage to totals.

### How it works

Straight from the pair-then-test model, no new machinery:

- **Step 1 — Pair.** Every left row with every right row.
- **Step 2 — Test.** Keep each pair where `ON` is true.

The key sentence: **each surviving pair becomes one output row.** Not each left row — each
*pair*. A left row sitting in three surviving pairs produces three output rows, with its own
columns copied unchanged into all three.

**What causes it:** a **one-to-many relationship**. One event has many price tiers; one venue
holds many events; one customer places many orders.

**How to spot it before running anything:** look at which table holds the foreign key.
`price_tier` has `event_id`, so `price_tier` points at `event`. **The table holding the foreign
key is the "many" side.** Join `event` to `price_tier` and the event rows fan out.

### Example — real run

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | Balcony     | 2499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(6 rows)
```

**`Monsoon Rock Night` appears three times.** It exists once in `event` and occupies three rows
here.

### Walkthrough

| Event | Its price tiers | Output rows produced |
| --- | --- | --- |
| Monsoon Rock Night | General, Balcony, VIP | **3** |
| Classical Evening | General | 1 |
| Jazz at the Note | General, Front Table | 2 |
| Open Mic Night | none | 0 (inner join) |

3 + 1 + 2 + 0 = 6 rows. Only 4 events exist and only 3 appear, yet the result has 6 rows.

**The output row count equals the number of price tiers, not the number of events.** That
sentence is the whole of Lesson 10.

### Watch the left-hand data get copied

```text
SELECT e.name AS event_name, v.name AS venue_name, v.capacity, pt.name AS tier_name
FROM event e
JOIN venue v ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Monsoon Rock Night'
ORDER BY pt.amount;

     event_name     |   venue_name    | capacity | tier_name
--------------------+-----------------+----------+-----------
 Monsoon Rock Night | Riverside Arena |    12000 | General
 Monsoon Rock Night | Riverside Arena |    12000 | Balcony
 Monsoon Rock Night | Riverside Arena |    12000 | VIP
(3 rows)
```

**12000** appears three times. Riverside Arena has twelve thousand seats once, in reality. The
join wrote it three times because three tiers came along.

Nothing is wrong yet — each row is a true statement. The trouble starts when you add them up.

### The damage: a `SUM` that is quietly wrong

```text
SELECT sum(v.capacity) AS true_total_capacity FROM venue v;

 true_total_capacity
---------------------
               13150
(1 row)
```

That is 12000 + 350 + 800, each venue once. Now the same `SUM` over a join reaching down to
price tiers — easy to write by accident when you joined down to filter on price and the `SUM` was
already there:

```text
SELECT sum(v.capacity) AS inflated_capacity
FROM venue v
JOIN event e ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id;

 inflated_capacity
-------------------
             48700
(1 row)
```

**48,700 instead of 13,150** — nearly four times too big, with no error and a perfectly ordinary
looking number. Here is exactly where it came from:

```text
SELECT v.name, v.capacity, count(*) AS times_repeated
FROM venue v
JOIN event e ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
GROUP BY v.name, v.capacity
ORDER BY v.name;

      name       | capacity | times_repeated
-----------------+----------+----------------
 Riverside Arena |    12000 |              4
 The Blue Note   |      350 |              2
(2 rows)
```

Riverside Arena is counted **4 times** (Monsoon's 3 tiers + Classical's 1). The Blue Note
**2 times** (Jazz's 2 tiers). Lakeside Hall zero times — the inner join dropped it.

(12000 × 4) + (350 × 2) + (800 × 0) = 48000 + 700 + 0 = **48700**. Exactly the wrong answer.

**Fan-out does not announce itself as an error. It arrives as a number that is too big, in a
report nobody checks by hand.**

### Fan-out is not duplication

The beginner reflex is to call these rows duplicates and reach for `DISTINCT`. Look again: no two
of the six rows are identical — each carries a different tier name and amount. They are not
duplicates; they are different facts sharing a left-hand half.

So `DISTINCT` is the wrong tool. It removes rows identical in **every** selected column. Select
the tier details and nothing is identical, so `DISTINCT` removes nothing while still paying for
the sort. Select only the event name and `DISTINCT` happens to collapse the three rows into one —
but by accident, and it stops working the moment someone adds a column to the `SELECT` list.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Treating repeated rows as corrupt data | You hunt a data bug that does not exist | Fan-out is correct output. Count the children before suspecting the data. |
| `SUM` on a "one"-side column after joining to the "many" side | The total is multiplied by the number of children | Aggregate before joining, or sum only "many"-side columns. |
| Reaching for `DISTINCT` | Removes nothing when rows genuinely differ, and costs a sort | Decide what one output row should represent, then aggregate or restructure. |
| Assuming one output row per left row | Only true for one-to-one links | Check which table holds the foreign key — that is the "many" side. |
| Averaging across a fanned-out join | `AVG` is wrong for the same reason as `SUM`, and harder to notice | Same fixes as `SUM`. |

### Where it is used

Not a feature you reach for — a behaviour you recognise:

- **Whenever you join a parent to its children:** event to tiers, order to line items, post to
  comments, patient to visits.
- **The moment a report total looks too big:** first question is "did a join fan out before the
  `SUM`?"
- **When a page shows fewer distinct items than the page size.** (Lesson 11.)
- **In every object-relational mapping tool.** Hibernate's `JOIN FETCH` produces exactly these
  rows and quietly removes the repeats in memory — which works until you add paging.

### Link to the task

First half of **S7**: *"Meet fan-out deliberately. Join `event` to `price_tier` where one event
has three tiers. The event name appears three times."* Done above.

The second half — *"run `SELECT COUNT(*)` on that join and see that it counts tiers, not events.
Fix it with `COUNT(DISTINCT e.id)`"* — is Lesson 10.

Half of **checkpoint question 3**: *"An event with three price tiers appears three times. Why?"*
Because each surviving pair becomes one output row, and the event sits in three surviving pairs.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

**Step 2 — give one event a third price tier.** Skip if `price_tier` already has 6 rows.

```sql
INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000006',
        'aaaaaaaa-0000-0000-0000-000000000001',
        'Balcony', 2499);
```

Expected: `INSERT 0 1`

**Step 3 — confirm both counts.**

```sql
SELECT count(*) AS events FROM event;
SELECT count(*) AS tiers FROM price_tier;
```

Expected: `4` and `6`.

**Step 4 — predict.** How many rows does the next query return, and which event appears three
times?

**Step 5 — see the fan-out.**

```sql
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;
```

Expected: 6 rows, `Monsoon Rock Night` three times.

**Step 6 — watch left data get copied.**

```sql
SELECT e.name AS event_name, v.name AS venue_name, v.capacity, pt.name AS tier_name
FROM event e
JOIN venue v ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Monsoon Rock Night'
ORDER BY pt.amount;
```

Expected: 3 rows, `12000` printed three times.

**Step 7 — break a total on purpose.**

```sql
SELECT sum(v.capacity) AS true_total_capacity FROM venue v;

SELECT sum(v.capacity) AS inflated_capacity
FROM venue v
JOIN event e ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id;
```

Expected: `13150` then `48700`.

**Step 8 — find where the inflation came from.**

```sql
SELECT v.name, v.capacity, count(*) AS times_repeated
FROM venue v
JOIN event e ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
GROUP BY v.name, v.capacity
ORDER BY v.name;
```

Expected: `Riverside Arena | 12000 | 4`, `The Blue Note | 350 | 2`. Check by hand that
(12000 × 4) + (350 × 2) = 48700.

**Done when:** your step 4 prediction was right, and you can explain 48,700 from the repeat
counts without looking.

### Summary for revision

- **Fan-out:** one left row appearing many times because it matched many right rows.
- Cause: **each surviving pair becomes one output row** — not each left row.
- Trigger: a **one-to-many** link. The table holding the **foreign key** is the "many" side.
- Left-side column values are **copied** into every fanned row (12000 printed three times).
- `SUM` over a fanned join double-counts: true 13,150 became 48,700 here.
- Fan-out rows are **not duplicates** — they differ in the right-hand columns, so `DISTINCT` is
  the wrong tool.
- It is not an error and raises no warning. The symptom is a number that is too large.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: album
 id | album_title | release_year
----+-------------+--------------
  1 | Monsoon     |         2024
  2 | Dry Season  |         2025

Table: track
 id | album_id | track_title
----+----------+-------------
 40 |        1 | Opening
 41 |        1 | Downpour
 42 |        1 | Puddles
 43 |        2 | Dust
```

```sql
SELECT a.album_title, a.release_year, t.track_title
FROM album a JOIN track t ON t.album_id = a.id;
```

1. How many rows, and which album title repeats how often?
2. Someone says "those are duplicates, add `DISTINCT`". Why is that wrong, and what would
   `DISTINCT` actually do here?
3. Someone expects 2024 + 2025 = 4049 from
   `SELECT sum(a.release_year) FROM album a JOIN track t ON t.album_id = a.id;`. What do they get?

**Answer.**

1. **4 rows.** `Monsoon` appears **3 times** (Opening, Downpour, Puddles); `Dry Season` once
   (Dust). The row count equals the number of tracks, not albums.
2. They are not duplicates: the three Monsoon rows carry different track titles, so no two rows
   are identical. `DISTINCT` removes rows identical in **every** selected column, and since
   `track_title` differs on each, **`DISTINCT` would remove nothing at all** — the result would
   still be 4 rows, just after paying for a pointless sort. It would appear to "work" only if
   `track_title` were dropped from the `SELECT` list, and that fix breaks the moment anyone adds
   a column back.
3. **They get 10,121.** `Monsoon`'s 2024 is added once per track, so three times:
   2024 × 3 = 6072. `Dry Season`'s 2025 is added once: 2025. 6072 + 2025 = **10121**, against the
   expected 4049. The join fanned out before the `SUM`, so the figure counts tracks weighted by
   release year rather than summing years. (Adding release years is meaningless anyway, which is
   a useful hint: a total that makes no physical sense is often a fan-out symptom.)

**Question 2.** Three table structures, no data:

```text
Table: doctor        -- 10 rows
  id            integer not null  (primary key)
  doctor_name   text    not null

Table: appointment  -- 300 rows
  id            integer not null  (primary key)
  doctor_id     integer not null  (foreign key referencing doctor.id)
  patient_id    integer not null  (foreign key referencing patient.id)

Table: patient      -- 120 rows
  id            integer not null  (primary key)
  patient_name  text    not null
```

1. Which is the "many" side between `doctor` and `appointment`, and how can you tell?
2. How many rows does this return?

```sql
SELECT d.doctor_name, ap.id
FROM doctor d JOIN appointment ap ON ap.doctor_id = d.id;
```

3. A `years_experience` column is added to `doctor`, and a developer writes
   `SELECT sum(d.years_experience) FROM doctor d JOIN appointment ap ON ap.doctor_id = d.id;`
   to get total experience. Why is this wrong, and what does the number mean?

**Answer.**

1. **`appointment` is the "many" side.** It holds the foreign key `doctor_id`, and the table
   holding the foreign key is always the many side. The direction is readable from the structures
   alone, with no data: many appointment rows may carry the same `doctor_id`, while a doctor row
   has no column pointing back at appointments. In words, one doctor has many appointments.
2. **Exactly 300 rows.** `doctor_id` is `not null`, so every appointment holds a doctor id; the
   foreign key means that id must exist in `doctor`; and `doctor.id` is a primary key, so it
   matches exactly one doctor row. One match per appointment, 300 appointments, 300 rows. Step 1
   builds 10 × 300 = 3,000 pairs and step 2 keeps 300. The doctor rows fan out heavily — on
   average each doctor name is repeated 30 times.
3. It is wrong because the join fans out **before** the `SUM`, so each doctor's
   `years_experience` is added once **per appointment** rather than once. The number returned is
   the sum over all 300 result rows, which equals each doctor's experience multiplied by their
   number of appointments, then totalled — a figure with no meaning. It would be roughly thirty
   times too large, and it would change whenever appointments are booked or cancelled, even
   though nobody's experience changed. The correct query touches one table only:
   `SELECT sum(years_experience) FROM doctor;`. The general rule: never aggregate a "one"-side
   column across a join to the "many" side.

### Your doubts

None for this lesson.

---

## Lesson 10 — Counting across a fan-out: `COUNT(*)` versus `COUNT(DISTINCT ...)`

### Definition

Three forms of counting that look alike and answer different questions:

- **`COUNT(*)`** counts **output rows**. It never looks inside them.
- **`COUNT(some_column)`** counts rows where that column is **not `NULL`**.
- **`COUNT(DISTINCT some_column)`** counts how many **different non-`NULL` values** the column
  holds.

On a plain single table all three often agree. On a fanned-out join they disagree, and the wrong
one gives a confident wrong number.

### Why it is needed

`COUNT(*)` is the first thing everybody writes, and after a fan-out it silently answers the wrong
question. The task spells it out: *"run `SELECT COUNT(*)` on that join and see that it counts
tiers, not events. Fix it with `COUNT(DISTINCT e.id)`."*

This is worse than a wrong `SUM`, because a count looks too simple to re-check. "How many events
do we have?" gets the answer 6 when the truth is 3, and 6 is plausible enough to reach a report
and stay there.

### How it works

| Form | The question it asks |
| --- | --- |
| `COUNT(*)` | How many rows came out of everything before me? After a fan-out that is the number of **children**. |
| `COUNT(column)` | In how many rows is this column not `NULL`? Matters after a left join, where rescued rows hold `NULL` in every right-hand column. |
| `COUNT(DISTINCT column)` | How many different values appear, ignoring `NULL` and repeats? **This is what undoes a fan-out.** |

### Example — real run

Data: 4 events, 6 price tiers. Monsoon has 3 tiers, Jazz 2, Classical 1, Open Mic none.

```text
-- On the inner join
SELECT count(*) AS count_star, count(DISTINCT e.id) AS distinct_events
FROM event e
JOIN price_tier pt ON pt.event_id = e.id;

 count_star | distinct_events
------------+-----------------
          6 |               3
(1 row)
```

**`COUNT(*)` returned 6, the number of price tiers.** The number of events involved is 3.

```text
-- On the left join, four counts at once
SELECT count(*)              AS count_star,
       count(DISTINCT e.id)  AS distinct_events,
       count(pt.id)          AS count_tier_id,
       count(e.id)           AS count_event_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id;

 count_star | distinct_events | count_tier_id | count_event_id
------------+-----------------+---------------+----------------
          7 |               4 |             6 |              7
(1 row)
```

Four different numbers from one join. Every one is correct; each answers a different question.

### Walkthrough

The left join has 7 rows: 6 matched plus 1 rescued row for `Open Mic Night`.

| Written | Number | The question it answers |
| --- | --- | --- |
| `count(*)` | **7** | How many rows are in this result? |
| `count(DISTINCT e.id)` | **4** | How many different events appear? |
| `count(pt.id)` | **6** | How many rows have a real price tier? |
| `count(e.id)` | **7** | How many rows have a real event? |

- **`count(*)` = 7** — counted rows: 6 matched + 1 rescued.
- **`count(DISTINCT e.id)` = 4** — Monsoon's id appears in 3 rows and counts once; Open Mic's id
  is on the rescued row and is still a real event id, so it counts. Four events: the whole table.
- **`count(pt.id)` = 6** — the rescued row has `pt.id` as `NULL` and is skipped. Equals the size
  of `price_tier`.
- **`count(e.id)` = 7** — every row has a real event id, so nothing is skipped.

### The per-event version, where the trap bites hardest

```text
SELECT e.name AS event_name,
       count(*)     AS count_star,
       count(pt.id) AS count_tier_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name;

     event_name     | count_star | count_tier_id
--------------------+------------+---------------
 Classical Evening  |          1 |             1
 Jazz at the Note   |          2 |             2
 Monsoon Rock Night |          3 |             3
 Open Mic Night     |          1 |             0
(4 rows)
```

**`Open Mic Night` shows `count_star` 1 but `count_tier_id` 0.** It has no price tiers, so zero
is right. `COUNT(*)` reported 1 because the rescued row is still a row, and `COUNT(*)` counts
rows without looking inside them.

This is the most common counting bug after a left join. A page reading "Open Mic Night — 1 ticket
type available" when there are none is exactly this. **After a `LEFT JOIN`, count a column from
the right-hand table, never `*`.**

### Count the key, not the label

The task says `COUNT(DISTINCT e.id)` — the primary key, not `e.name`. Deliberately so:

```text
SELECT count(DISTINCT pt.name) AS distinct_tier_names,
       count(DISTINCT pt.id)   AS distinct_tier_ids
FROM price_tier pt;

 distinct_tier_names | distinct_tier_ids
---------------------+-------------------
                   4 |                 6
(1 row)
```

There are **6** price tiers but only **4** different names — three separate tiers are all called
`General`, one per event. Counting distinct names reports 4 and loses two real rows.

A primary key is unique by definition, so counting distinct keys can never merge two different
things. A name is a label, and two things may share one. **Always
`COUNT(DISTINCT <primary key>)`.**

### How to choose

| The question | What to write | Result here |
| --- | --- | --- |
| How many price tiers exist across all events? | `count(pt.id)` | 6 |
| How many events have at least one tier? | `count(DISTINCT e.id)` on the **inner** join | 3 |
| How many events exist at all? | `count(DISTINCT e.id)` on the **left** join | 4 |
| How many rows did this query produce? | `count(*)` | 7 |
| How many tiers does each event have? | `count(pt.id)` with `GROUP BY e.id` | 3, 1, 2, 0 |

Two of those use the same expression and give different answers depending on the join type. **The
join decides which rows are present; the count decides how they are tallied. Both must be right.**

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| `COUNT(*)` on a fanned-out join | Counts children, not parents — 6 instead of 3 | `COUNT(DISTINCT <parent primary key>)` |
| `COUNT(*)` after a `LEFT JOIN`, grouped per parent | Reports 1 for a parent with no children | `COUNT(<right-hand column>)`, which skips `NULL` |
| `COUNT(DISTINCT name)` instead of `COUNT(DISTINCT id)` | Different rows sharing a label are merged — 4 instead of 6 | Count the primary key |
| Adding `DISTINCT` to the whole `SELECT` to fix a count | Does not help when rows genuinely differ, and costs a sort | Fix the count, not the row list |
| Assuming `COUNT(column)` equals `COUNT(*)` | They agree only when the column is never `NULL` | `COUNT(column)` skips `NULL` — that is the whole difference |
| Right count, wrong join | `COUNT(DISTINCT e.id)` gives 3 on inner and 4 on left | Pick the join from the question first, then the count |

### Where it is used

- **Dashboard tiles:** "active customers", "orders this week". Each is a count over a join, wrong
  if the join fanned out.
- **Paging headers:** "Showing 1 to 10 of 247" — after a fan-out that 247 counts children, so the
  page numbers do not add up.
- **Any `GROUP BY` summary** counting children per parent: the 1-instead-of-0 case.
- **Pre-migration checks:** "both systems should have the same number of orders" sends you
  chasing a difference that does not exist.

### Link to the task

Completes **S7**: `COUNT(*)` returns 6 and `COUNT(DISTINCT e.id)` returns 3 on the inner join.

Completes **checkpoint question 3**: *"An event with three price tiers appears three times. Why,
and what does that do to `COUNT(*)`?"* — it appears three times because each surviving pair
becomes one output row and that event sits in three surviving pairs; `COUNT(*)` counts output
rows, so it reports tiers rather than events (6 rather than 3), and `COUNT(DISTINCT e.id)`
restores the 3.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

**Step 2 — remind yourself of the true table sizes.**

```sql
SELECT count(*) AS events FROM event;
SELECT count(*) AS tiers FROM price_tier;
```

Expected: `4` and `6`.

**Step 3 — predict** what `COUNT(*)` and `COUNT(DISTINCT e.id)` return on an inner join.

**Step 4 — see `COUNT(*)` count the wrong thing.**

```sql
SELECT count(*) AS count_star, count(DISTINCT e.id) AS distinct_events
FROM event e
JOIN price_tier pt ON pt.event_id = e.id;
```

Expected: `6 | 3`.

**Step 5 — all four counts on the left join.**

```sql
SELECT count(*)              AS count_star,
       count(DISTINCT e.id)  AS distinct_events,
       count(pt.id)          AS count_tier_id,
       count(e.id)           AS count_event_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id;
```

Expected: `7 | 4 | 6 | 7`. Say aloud which question each answers.

**Step 6 — the zero-versus-one trap.**

```sql
SELECT e.name AS event_name,
       count(*)     AS count_star,
       count(pt.id) AS count_tier_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name;
```

Expected: 4 rows; `Open Mic Night` shows `1` and `0`. Be sure you can say which is right.

**Step 7 — why you count the key, not the label.**

```sql
SELECT count(DISTINCT pt.name) AS distinct_tier_names,
       count(DISTINCT pt.id)   AS distinct_tier_ids
FROM price_tier pt;

SELECT name, count(*) FROM price_tier GROUP BY name ORDER BY name;
```

Expected: `4` and `6`, with `General` appearing 3 times.

**Done when:** you can state what each of the three count forms counts, and explain why
`Open Mic Night` shows 1 under one and 0 under the other.

### Summary for revision

- `COUNT(*)` counts **rows**. `COUNT(col)` counts **non-`NULL` values**. `COUNT(DISTINCT col)`
  counts **different non-`NULL` values**.
- After a fan-out, `COUNT(*)` counts **children**: 6 tiers, not 3 events.
- Fix with `COUNT(DISTINCT <parent primary key>)`.
- After a `LEFT JOIN`, `COUNT(*)` reports **1** for a parent with no children, because the
  rescued row is still a row. Use `COUNT(<right-hand column>)` to get **0**.
- Count the **key**, never the **label**: 6 tiers share only 4 distinct names.
- Pick the join first (it decides which rows exist), then the count (it decides the tally).

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: course
 id | course_name
----+-------------
  1 | Algebra
  2 | Geology
  3 | Pottery

Table: enrolment
 id | course_id | student_name
----+-----------+--------------
 80 |         1 | Asha
 81 |         1 | Bilal
 82 |         1 | Chen
 83 |         2 | Dara
```

`Pottery` has no enrolments.

```sql
-- Query 1
SELECT count(*) FROM course c JOIN enrolment e ON e.course_id = c.id;
-- Query 2
SELECT count(DISTINCT c.id) FROM course c JOIN enrolment e ON e.course_id = c.id;
-- Query 3
SELECT count(*) FROM course c LEFT JOIN enrolment e ON e.course_id = c.id;
-- Query 4
SELECT count(e.id) FROM course c LEFT JOIN enrolment e ON e.course_id = c.id;
```

1. What does each return?
2. A manager asks "how many courses do we run?" Which query is right, or write the one that is.
3. Write a report listing every course with its enrolment count, showing `0` for empty courses,
   and say what `COUNT(*)` would do instead.

**Answer.**

1. **Query 1 returns 4** — the inner join has one row per enrolment (Algebra fans out to 3,
   Geology 1, Pottery dropped). **Query 2 returns 2** — only Algebra and Geology appear in the
   inner join. **Query 3 returns 5** — the 4 matched rows plus 1 rescued row for Pottery.
   **Query 4 returns 4** — the rescued row's `e.id` is `NULL` and is skipped.
2. **None of them.** Query 2 comes closest but answers "how many courses have at least one
   enrolment", which is 2 — it silently omits Pottery. The right query touches one table:

   ```sql
   SELECT count(*) FROM course;
   ```

   which returns **3**. The lesson: if the question is only about one table, do not join at all.
   Every join introduces a way to get the number wrong.
3. The report:

   ```sql
   SELECT c.course_name, count(e.id) AS enrolled
   FROM course c
   LEFT JOIN enrolment e ON e.course_id = c.id
   GROUP BY c.id, c.course_name
   ORDER BY c.course_name;
   ```

   This gives `Algebra 3`, `Geology 1`, `Pottery 0`. Using `count(*)` instead would give
   `Pottery 1`, because Pottery's rescued row is still a row and `COUNT(*)` does not look inside
   it. The report would claim one student is enrolled in a course nobody has joined — and the
   error appears only on exactly the rows the report exists to highlight.

**Question 2.** One table, shown in full:

```text
Table: ticket
 id  | buyer_name | tier_label
-----+------------+------------
 100 | Rahul      | Standard
 101 | Priya      | Standard
 102 | Rahul      | Premium
 103 | Sana       | Standard
```

Someone needs to know how many different people bought tickets and writes
`SELECT count(DISTINCT t.tier_label) FROM ticket t;`.

1. What does it return, and what did they want?
2. Write the query for their real question. What is risky about it, and what column would make
   it safe?
3. State the general rule in one sentence.

**Answer.**

1. It returns **2** — the two distinct labels, `Standard` and `Premium`. They wanted the number
   of distinct **buyers**, which is **3**: Rahul, Priya and Sana. Rahul bought two tickets but is
   one person. They counted the wrong column entirely, and 2 is plausible enough to go unnoticed.
2. The query:

   ```sql
   SELECT count(DISTINCT t.buyer_name) FROM ticket t;
   ```

   It returns 3 for this data. **The risk is that `buyer_name` is a label, not a key.** Two
   different people called Rahul would be merged into one, under-counting; and the same person
   entered as `Rahul` and `rahul ` would be counted twice, over-counting. The answer is only
   right because this tiny sample happens to have unique names. A safe version needs a
   **`buyer_id`** column — a foreign key to a `buyer` table whose primary key identifies a person
   independently of spelling — and then `count(DISTINCT t.buyer_id)`.
3. **Put a primary key inside `COUNT(DISTINCT ...)`, never a human-readable label, because a key
   is guaranteed unique and a label is not.**

### Your doubts

None for this lesson.

---

## Lesson 11 — Fan-out and `LIMIT`: the partial page of the wrong thing

### Definition

`LIMIT n` stops the query after **n output rows** — not n events, n customers, or n of anything
else you had in mind. Without fan-out those are the same thing. With fan-out they are not, and
`LIMIT` cuts in the middle of a parent's children.

### Why it is needed

This is where fan-out stops being a curiosity and starts shipping broken pages. Every list screen
is paged, and if the query fans out, "the first ten rows" is not "the first ten events". Two bad
things happen at once: the page shows fewer parents than promised, **and** at least one parent is
shown with only some of its children.

The second is the dangerous one. A missing event is visibly missing. An event displayed with two
of its three prices looks completely normal.

The task: *"fan-out combined with `LIMIT` gives you a partial page of the wrong thing, which is
exactly the `JOIN FETCH` pagination problem you meet in Phase 3."*

### How it works — the full run order

| Order | Clause | What it does |
| --- | --- | --- |
| 1st | `FROM` / `JOIN` | Pair, test, rescue. **Fan-out happens here.** |
| 2nd | `WHERE` | Filters the joined result |
| 3rd | `GROUP BY` + aggregates | Collapses groups into single rows, if present |
| 4th | `SELECT` | Picks the columns |
| 5th | `ORDER BY` | Sorts |
| 6th | `LIMIT` / `OFFSET` | Takes a slice of what is left |

**`LIMIT` runs last.** By then the fan-out has happened and the rows are sorted; `LIMIT` just
counts off the first n. It has no idea three of those rows belong to one event.

### Example — real run

The six joined rows, sorted by event name then price:

| Row | Event | Tier | Amount |
| --- | --- | --- | --- |
| 1 | Classical Evening | General | 999 |
| 2 | Jazz at the Note | General | 1999 |
| 3 | Jazz at the Note | Front Table | 3499 |
| 4 | Monsoon Rock Night | General | 1499 |
| 5 | Monsoon Rock Night | Balcony | 2499 |
| 6 | Monsoon Rock Night | VIP | 4999 |

A page size of 3, meaning "show me three events":

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount
LIMIT 3;

    event_name     |  tier_name  |  amount
-------------------+-------------+-----------
 Classical Evening | General     |  999.0000
 Jazz at the Note  | General     | 1999.0000
 Jazz at the Note  | Front Table | 3499.0000
(3 rows)
```

Three rows, but only **two** events. The page promised three and delivered two.

### The worse case — `LIMIT 4`

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount
LIMIT 4;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
(4 rows)
```

**`Monsoon Rock Night` is on this page with one of its three tiers.** Balcony (2499) and VIP
(4999) were cut off.

A web page receiving this renders "Monsoon Rock Night — General, ₹1499". If it computes the
highest price from what it was given, it says ₹1499 when the truth is ₹4999. Nothing is marked
incomplete. **This is the "partial page of the wrong thing".** The object is partially loaded and
nothing says so.

### Page two is no better

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount
LIMIT 3 OFFSET 3;

     event_name     | tier_name |  amount
--------------------+-----------+-----------
 Monsoon Rock Night | General   | 1499.0000
 Monsoon Rock Night | Balcony   | 2499.0000
 Monsoon Rock Night | VIP       | 4999.0000
(3 rows)
```

Page two holds **one** event. With page size 3: page one shows 2 events, page two shows 1, and
`Open Mic Night` never appears at all because the inner join dropped it. A pager reading "page 1
of 2" no longer matches what the user sees — and if a row is inserted or deleted between
requests, the `OFFSET` shifts and a user sees repeats or gaps.

### Walkthrough: why `LIMIT` cannot help itself

1. **Join.** 4 events pair with 6 tiers; 6 pairs survive. Monsoon has fanned out into 3 rows.
2. No `WHERE`. 3. No `GROUP BY`. 4. `SELECT` picks three columns. 5. `ORDER BY` sorts the 6 rows.
6. **`LIMIT 4`** counts four rows from the top and discards the rest.

At step 6 the query holds a flat list of 6 rows. "Rows 4, 5 and 6 are the same event" is not
something `LIMIT` looks at. **The fix cannot live inside `LIMIT`; it must change what one row
means before `LIMIT` runs.**

### Fix one — page the parents first, then join

```text
WITH page AS (
  SELECT id, name FROM event ORDER BY name LIMIT 3
)
SELECT p.name AS event_name, pt.name AS tier_name, pt.amount
FROM page p
LEFT JOIN price_tier pt ON pt.event_id = p.id
ORDER BY p.name, pt.amount;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | Balcony     | 2499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(6 rows)
```

**Three events, all complete.**

A **common table expression** (CTE) is a named query defined at the top with `WITH` and used
below as if it were a table. Here it selects from `event` alone — no join, so no fan-out — and
`LIMIT 3` really does mean three events. The main query then joins only those three to their
tiers.

The final row count is **6, not 3, and that is correct**: the page holds three events, which own
six tiers between them. The `LIMIT` controls the page, and the fan-out happens safely inside it.

### Fix two — collapse each parent to one row, then limit

```text
SELECT e.name AS event_name,
       count(pt.id) AS tier_count,
       min(pt.amount) AS cheapest
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name
LIMIT 3;

     event_name     | tier_count | cheapest
--------------------+------------+-----------
 Classical Evening  |          1 |  999.0000
 Jazz at the Note   |          2 | 1999.0000
 Monsoon Rock Night |          3 | 1499.0000
(3 rows)
```

`GROUP BY` runs at step 3, before `LIMIT` at step 6, so by the time `LIMIT` counts rows, one row
already means one event. Note `count(pt.id)` rather than `count(*)` — the left join rescues
events with no tiers, and `count(*)` would report 1 for them instead of 0.

**Choosing:** fix two when the screen shows a summary ("3 ticket types, from ₹1499"); fix one
when the screen needs the children themselves.

### The Phase 3 connection — the S8 write-down

In Java, Hibernate's `JOIN FETCH` loads a parent together with a collection of its children,
using exactly this kind of join. Ask it for a page at the same time and it hits this precise
problem: it cannot apply the row limit in the database, because **n rows is not n parents**.

So it fetches **every** matching row into memory, assembles the parents, and takes your page from
the assembled list — announcing it in the log with
*"firstResult/maxResults specified with collection fetch; applying in memory"*. On five rows
nobody notices. On five million it loads all five million to give you ten.

**The note to keep:** that warning exists because a row limit cannot be pushed into a fanned-out
join without cutting a parent in half. Either the tool pages in memory, or you restructure the
query the way fix one does — pick the page of parents first, then fetch their children.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| `LIMIT 10` on a fanned-out join expecting 10 parents | Fewer parents, one of them incomplete | Page the parents in a CTE or subquery first, then join |
| Raising the `LIMIT` to compensate | You cannot know the right number — it depends on children per parent | Child counts are data, not something to hard-code |
| Trusting a partially loaded parent | No error, no gap, just wrong values like a maximum price that is not the maximum | Make one row mean one parent before `LIMIT` runs |
| `DISTINCT` + `LIMIT` to deduplicate a page | Still limits after the fact, still cuts mid-parent, now with a sort | Fix the shape of the query, not the symptom |
| `OFFSET` paging over fanned rows in a changing table | Rows shift between requests; users see repeats and gaps | Page the parents; prefer keyset paging over large offsets |
| `count(*)` for the pager total | The "of 247" counts children, so page counts are wrong too | `count(DISTINCT <parent key>)`, or count the parent table alone |

### Where it is used

- Every paged list over a one-to-many relationship.
- Infinite-scroll feeds, where a shifting `OFFSET` over fanned rows gives repeats and gaps.
- Exports with a row cap — the last record in the file is silently incomplete.
- Hibernate and other object-relational mapping tools (the Phase 3 connection above).

### Link to the task

This is **S8**. The Phase 3 section above is the write-down; the examples are the demonstration on
real data: `LIMIT 3` gives 2 events, `LIMIT 4` gives Monsoon Rock Night with one of three tiers,
and page two holds a single event.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

**Step 2 — see the full joined result before slicing it.**

```sql
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;
```

Expected: 6 rows, `Monsoon Rock Night` last, three times.

**Step 3 — predict.** With `LIMIT 3`: how many rows, and how many different events?

**Step 4 — take a page of three.**

```sql
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e
JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount
LIMIT 3;
```

Expected: 3 rows, only **2** events.

**Step 5 — see the partially loaded event.**

```sql
... LIMIT 4;
```

Expected: 4 rows, `Monsoon Rock Night` with only its General tier. Say aloud which two tiers are
missing and what price a screen would wrongly show as the highest.

**Step 6 — look at page two.**

```sql
... LIMIT 3 OFFSET 3;
```

Expected: 3 rows, all `Monsoon Rock Night`. One event on the whole page.

**Step 7 — fix one, paging the parents first.**

```sql
WITH page AS (
  SELECT id, name FROM event ORDER BY name LIMIT 3
)
SELECT p.name AS event_name, pt.name AS tier_name, pt.amount
FROM page p
LEFT JOIN price_tier pt ON pt.event_id = p.id
ORDER BY p.name, pt.amount;
```

Expected: 6 rows covering exactly 3 events, Monsoon complete. Confirm 6 rows for a page of 3 is
right, not a bug.

**Step 8 — fix two, aggregating first.**

```sql
SELECT e.name AS event_name,
       count(pt.id) AS tier_count,
       min(pt.amount) AS cheapest
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name
LIMIT 3;
```

Expected: 3 rows, tier counts 1, 2, 3.

**Done when:** you can explain why step 5 is more dangerous than step 4, and say in one sentence
why `LIMIT` cannot fix this by itself.

### Summary for revision

- `LIMIT` runs **last**, after the fan-out. It counts **rows**, not parents.
- `LIMIT 3` here gave 2 events; `LIMIT 4` gave an event with 1 of its 3 tiers; page two held 1
  event.
- The silent danger is the **partially loaded parent** — a maximum price that is not the maximum,
  with nothing marked incomplete.
- **Fix one:** page the parents in a CTE or subquery, then join to children. The final row count
  exceeding the page size is correct.
- **Fix two:** `GROUP BY` the parent and aggregate the children, then `LIMIT`. Works because
  `GROUP BY` runs before `LIMIT`.
- Pager totals need `count(DISTINCT <parent key>)`, not `count(*)`.
- Phase 3: Hibernate's *"firstResult/maxResults specified with collection fetch; applying in
  memory"* warning exists for exactly this reason.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: recipe
 id | recipe_name
----+-------------
  1 | Dal
  2 | Biryani
  3 | Kheer

Table: step
 id | recipe_id | instruction
----+-----------+-------------
 10 |         1 | Rinse
 11 |         2 | Soak
 12 |         2 | Fry
 13 |         2 | Layer
 14 |         3 | Boil
```

A "recipes" page shows three recipes per page:

```sql
SELECT r.recipe_name, s.instruction
FROM recipe r
JOIN step s ON s.recipe_id = r.id
ORDER BY r.recipe_name, s.id
LIMIT 3;
```

1. List the rows returned. How many different recipes appear?
2. One recipe is incomplete. Name it, say what is missing, and why a user would not notice.
3. Rewrite it so the page holds three complete recipes. How many rows does your version return,
   and why is that correct?

**Answer.**

1. Sorted by recipe name, the full join is: Biryani/Soak, Biryani/Fry, Biryani/Layer, Dal/Rinse,
   Kheer/Boil. `LIMIT 3` returns the first three: **`Biryani | Soak`, `Biryani | Fry`,
   `Biryani | Layer`**. **Only one recipe appears** — a page that promised three delivered one.
2. Strictly, no recipe is cut here: Biryani's three steps all fit. The incompleteness is the
   *page* — Dal and Kheer are absent entirely. Change the limit to `LIMIT 4` and the real danger
   appears: the page then holds Biryani complete plus **`Dal | Rinse`**, and with `LIMIT 2` it
   would hold **Biryani with only Soak and Fry**, its `Layer` step missing. A user would not
   notice because a recipe showing two steps looks exactly like a recipe that has two steps.
   Nothing on screen distinguishes "all the steps" from "the steps that fitted".
3. Page the parents first:

   ```sql
   WITH page AS (
     SELECT id, recipe_name FROM recipe ORDER BY recipe_name LIMIT 3
   )
   SELECT p.recipe_name, s.instruction
   FROM page p
   LEFT JOIN step s ON s.recipe_id = p.id
   ORDER BY p.recipe_name, s.id;
   ```

   It returns **5 rows**: Biryani 3, Dal 1, Kheer 1. That is correct rather than a bug, because
   the `LIMIT` now controls how many **recipes** are on the page, and 5 is simply how many steps
   those 3 recipes own between them. Row count and page size are different quantities once a
   one-to-many join is involved.

**Question 2.** An "orders" page shows ten orders per page with their line items:

```sql
SELECT o.order_ref, li.product_name, li.quantity
FROM orders o
JOIN line_item li ON li.order_id = o.id
ORDER BY o.placed_at DESC
LIMIT 10;
```

Orders average four line items. `orders` has 50,000 rows.

1. Roughly how many orders appear on page one, and why?
2. A developer suggests `LIMIT 40`. Why does that not fix it?
3. The pager says "Showing 1 to 10 of 200,000", from
   `SELECT count(*) FROM orders o JOIN line_item li ON li.order_id = o.id`. What does 200,000
   count, and what is the right query?
4. A Java developer reports the log warning *"firstResult/maxResults specified with collection
   fetch; applying in memory"* and asks if it can be ignored.

**Answer.**

1. **Roughly two or three orders.** `LIMIT 10` takes ten *rows*, and at about four line items per
   order, ten rows covers two full orders plus part of a third. The exact number varies per page
   because it depends on the item counts of whichever orders happen to be newest — so the page
   size is not merely wrong, it is **unpredictable**.
2. Because four is an *average*, not a guarantee. `LIMIT 40` returns forty rows, which might be
   twelve small orders or four large ones, and the last one on the page is still cut in half. The
   only way to get exactly ten orders is to limit the **orders**, and the number of line items is
   data that changes with every edit. No constant can encode it.
3. **200,000 counts line items**, not orders — it is roughly 50,000 orders × 4 items. The pager
   therefore reports item count where it promises order count, and every page number derived from
   it is wrong. The correct total counts the parent table alone:

   ```sql
   SELECT count(*) FROM orders;
   ```

   If a filter on line items is involved, use
   `SELECT count(DISTINCT o.id) FROM orders o JOIN line_item li ON li.order_id = o.id`.
4. **It should not be ignored.** The tool was asked to fetch each order together with its
   collection of line items *and* to return only one page. It cannot push the limit into the
   database, because ten rows is not ten orders — the limit would slice an order's items in half
   and hand back a half-loaded object. To stay correct it fetches **every** matching row into
   memory, assembles the complete orders, and then takes the requested page from that in-memory
   list. It is correct, and that is why it is only a warning. The risk is cost: on 50,000 orders
   with four items each it pulls about 200,000 rows into the application's memory to display ten
   orders, which is slow and can exhaust the heap as data grows. The fix is the same as fix one
   above — select the page of order ids first, then fetch the line items for just those ids.

### Your doubts

None for this lesson.

---

## Lesson 12 — `USING`: the shorthand, and why this schema cannot use it

### Definition

`USING (column_name)` is a shorter way to write an `ON` condition, available **only when both
tables have a column with that exact same name**:

```sql
JOIN dept ON staff.dept_id = dept.dept_id     -- these two are
JOIN dept USING (dept_id)                     -- the same join
```

Task vocabulary: *"`USING` — shorthand for `ON a.x = b.x` when both columns have the same name."*

### Why it is needed

The small reason: less typing, and no repeating `dept_id` twice.

The real reason: **you need to know when it does not apply, and recognise the trap when it
applies by accident.** This schema cannot use `USING` for either of its joins — and there is a
case where `USING` *will* compile on these tables and silently return nothing.

### How it works — two effects, not one

**Effect one: it writes the equality for you.**

**Effect two: it merges the two columns into one in the output.** Easy to miss, and the real
difference between the forms.

```text
WITH staff(dept_id, person) AS (VALUES (1,'Asha'), (2,'Bilal')),
     dept(dept_id, dept_name) AS (VALUES (1,'Sales'), (2,'Support'))
SELECT * FROM staff JOIN dept ON staff.dept_id = dept.dept_id;

 dept_id | person | dept_id | dept_name
---------+--------+---------+-----------
       1 | Asha   |       1 | Sales
       2 | Bilal  |       2 | Support
(2 rows)


SELECT * FROM staff JOIN dept USING (dept_id);

 dept_id | person | dept_name
---------+--------+-----------
       1 | Asha   | Sales
       2 | Bilal  | Support
(2 rows)
```

Four columns with `ON` (`dept_id` twice), three with `USING` (`dept_id` once). After
`USING (dept_id)` the merged column belongs to neither table, so you write it **unqualified**.

### Why this schema cannot use it

| Link | Left side | Right side | Same name? |
| --- | --- | --- | --- |
| event to venue | `event.venue_id` | `venue.id` | No |
| price_tier to event | `price_tier.event_id` | `event.id` | No |

```text
SELECT e.name FROM event e JOIN venue v USING (venue_id);

ERROR:  column "venue_id" specified in USING clause does not exist in right table
```

`venue` has `id`, `name`, `city`, `capacity`, `seat_map_hints`, `created_at` — no `venue_id`.

**This is not a flaw.** It follows the most common convention there is: name the primary key
`id`, and name a foreign key after the table it points at (`venue_id`). Under that convention the
two ends of a link are *deliberately* named differently, which is what makes them readable. So
`USING` almost never applies and `ON` is what you write.

### The trap: `USING` that compiles and is wrong

`USING` works on any shared column name and has no idea whether that column means anything as a
link. Both `event` and `venue` happen to have `id`:

```text
SELECT count(*) AS rows_from_using_id FROM event e JOIN venue v USING (id);

 rows_from_using_id
--------------------
                  0
(1 row)
```

**Zero rows, no error.** It asked "match events to venues where the event's own `id` equals the
venue's own `id`" — a meaningless question. Two unrelated primary keys never coincide here.

Zero is the kindest outcome, because it is obviously wrong. The frightening version is a schema
whose ids *do* overlap, such as small integers from 1. Then it returns rows, and they are
nonsense dressed as data.

### The bigger trap: `NATURAL JOIN`

`NATURAL JOIN` takes **every** column name the two tables share and matches on all of them at
once. You name nothing. It guesses.

`event` and `venue` share three names: `id`, `name`, `created_at`.

```text
SELECT count(*) AS rows_from_natural FROM event NATURAL JOIN venue;

 rows_from_natural
-------------------
                 0
(1 row)
```

Zero rows, no error, no warning. It silently demanded that an event and a venue share an id,
**and** share a name, **and** be created at the same instant.

Two things make this worse than `USING (id)`:

1. **Nothing in the query says what it joined on.** You must read both table definitions.
2. **It changes meaning when the schema changes.** Add a `notes` column to both tables next year
   and every `NATURAL JOIN` between them silently starts matching on `notes` too. No code edited,
   nothing failed, different rows.

**Never write `NATURAL JOIN`.**

### Where `USING` is genuinely useful

- **Junction tables.** A table linking students to courses with `student_id` and `course_id`,
  where the linked tables name their keys the same way.
- **Schemas that name primary keys after the table** (`event.event_id` rather than `event.id`).
  Both ends of every link then share a name. A legitimate style — it makes `USING (id)` accidents
  impossible but `NATURAL JOIN` accidents more likely.

Even where it fits, many teams prefer `ON`, because `ON` states both sides explicitly and a
reader never has to check a table definition to know what matched.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| `USING` on differently named columns | `ERROR: column "venue_id" specified in USING clause does not exist in right table` | `ON left.venue_id = right.id` |
| `USING (id)` because both tables have an `id` | Compiles, matches two unrelated primary keys, returns 0 rows here or nonsense elsewhere | Name the real link with `ON` |
| `NATURAL JOIN` | Joins on every shared column name, invisibly, and changes meaning when a column is added | Never use it |
| Qualifying the merged column after `USING` | It belongs to neither table | Write it unqualified: `dept_id` |
| Expecting `SELECT *` to match between the forms | `ON` shows the column twice, `USING` once | Expected — that is effect two |

### Link to the task

`USING` is in the task vocabulary, but you will not use it here. For **S9**, every link joins a
`<table>_id` to an `id`, so the names differ and `USING` cannot express any of them.

The useful takeaway is the trap: if a join on these tables returns zero rows for no apparent
reason, check whether someone wrote `USING (id)` or `NATURAL JOIN`.

### Do it yourself

**Step 1 — open a SQL prompt.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

**Step 2 — see both forms and the column merge.**

```sql
WITH staff(dept_id, person) AS (VALUES (1,'Asha'), (2,'Bilal')),
     dept(dept_id, dept_name) AS (VALUES (1,'Sales'), (2,'Support'))
SELECT * FROM staff JOIN dept ON staff.dept_id = dept.dept_id;
```

Expected: 2 rows, **four** columns, `dept_id` twice.

```sql
WITH staff(dept_id, person) AS (VALUES (1,'Asha'), (2,'Bilal')),
     dept(dept_id, dept_name) AS (VALUES (1,'Sales'), (2,'Support'))
SELECT * FROM staff JOIN dept USING (dept_id);
```

Expected: the same 2 rows, **three** columns, `dept_id` once.

**Step 3 — confirm `USING` cannot express the real join.**

```sql
SELECT e.name FROM event e JOIN venue v USING (venue_id);
```

Expected failure:

```text
ERROR:  column "venue_id" specified in USING clause does not exist in right table
```

**Step 4 — check why.**

```sql
\d venue
```

Expected: `id`, `name`, `city`, `capacity`, `seat_map_hints`, `created_at`. No `venue_id`.

**Step 5 — meet the trap that compiles.**

```sql
SELECT count(*) AS rows_from_using_id FROM event e JOIN venue v USING (id);
```

Expected: `0`, no error. Say aloud what the query actually asked.

**Step 6 — meet the worse trap.**

```sql
SELECT count(*) AS rows_from_natural FROM event NATURAL JOIN venue;
```

Expected: `0`, no error, no indication of what it joined on. Then work it out:

```sql
\d event
\d venue
```

Shared names: `id`, `name`, `created_at` — all three were used as the condition.

**Done when:** you can explain why step 3 fails but step 5 succeeds, and state in one sentence
why `NATURAL JOIN` is unsafe even when it happens to work today.

### Summary for revision

- `USING (col)` = `ON left.col = right.col`, **and** merges the two columns into one output
  column, written unqualified afterwards.
- It requires the **same column name on both sides**.
- This schema uses `id` for primary keys and `<table>_id` for foreign keys, so the two ends of
  every link differ by design and `USING` does not apply.
- **Trap:** `USING (id)` compiles because both tables have an `id`, and matches two unrelated
  primary keys — 0 rows here, nonsense elsewhere.
- **Bigger trap:** `NATURAL JOIN` matches on *every* shared column name invisibly, and its
  meaning changes when a column is added. Never use it.
- `USING` fits junction tables and schemas naming keys `<table>_id` everywhere. Many teams still
  prefer `ON` for explicitness.

### Check questions, with answers

**Question 1.** Two tables, shown in full:

```text
Table: branch
 branch_id | branch_city
-----------+-------------
         1 | Kochi
         2 | Surat

Table: atm
 atm_id | branch_id | model
--------+-----------+--------
     50 |         1 | NCR-22
     51 |         1 | NCR-30
     52 |         2 | DB-11
```

1. Write the join two ways — `ON` and `USING` — returning ATM model and branch city.
2. `SELECT * FROM atm JOIN branch USING (branch_id);` and
   `SELECT * FROM atm JOIN branch ON atm.branch_id = branch.branch_id;` both return 3 rows.
   What is the one difference?
3. A third table is added:

```text
Table: technician
 tech_id | branch_id | model
---------+-----------+--------
      90 |         1 | NCR-22
```

   Someone writes `SELECT * FROM atm NATURAL JOIN technician;`. Which columns does it match on,
   and why is the result almost certainly not what was wanted?

**Answer.**

1. Both forms:

   ```sql
   SELECT a.model, b.branch_city FROM atm a JOIN branch b ON a.branch_id = b.branch_id;
   SELECT a.model, b.branch_city FROM atm a JOIN branch b USING (branch_id);
   ```

   `USING` works here because both tables genuinely name the column `branch_id`. Both return 3
   rows: `NCR-22 | Kochi`, `NCR-30 | Kochi`, `DB-11 | Surat`.
2. **The column count.** The `ON` version returns five columns with `branch_id` appearing twice
   (once from each table, always holding the same value). The `USING` version returns four, with
   `branch_id` merged into one. The rows and values are otherwise identical.
3. `atm` and `technician` share **two** column names: `branch_id` **and** `model`. A natural join
   matches on both at once, so it demands that an ATM and a technician be at the same branch
   **and** have the same `model` value. That returns the single row where ATM 50 and technician
   90 share `branch_id` 1 and `model` `NCR-22`. The author almost certainly meant "technicians
   and the ATMs at their branch", which is `branch_id` alone — the `model` match is accidental,
   caused only by the two tables reusing a column name for different purposes (an ATM's model
   versus a technician's specialist model). Nothing in the query text reveals this, and adding
   another shared column name later would silently change the result again.

**Question 2.** Two table definitions:

```text
                 Table "invoice"                          Table "client"
   Column    |  Type   | Nullable          |     Column    |  Type   | Nullable
-------------+---------+----------         |   ------------+---------+----------
 id          | integer | not null          |    id         | integer | not null
 client_id   | integer | not null          |    name       | text    | not null
 total       | numeric | not null          |    created_at | date    | not null
 created_at  | date    | not null          |
```

1. `SELECT * FROM invoice JOIN client USING (client_id);` — what happens?
2. `SELECT * FROM invoice JOIN client USING (id);` — what does it ask, why does it run, and what
   would it produce if both tables held ids 1, 2 and 3?
3. Write the correct query. Could `USING` ever work for this link, and what would have to change?

**Answer.**

1. It **fails** with `ERROR: column "client_id" specified in USING clause does not exist in right
   table`. `USING` requires the named column on **both** sides, and `client` has only `id`,
   `name` and `created_at`. The foreign key lives on `invoice` alone.
2. It asks the database to match rows where **the invoice's own primary key equals the client's
   own primary key** — two unrelated identifiers that merely share a column name. It runs without
   error because `USING` checks only that the name exists on both sides; it has no knowledge of
   foreign keys or of what the column means. If both tables held ids 1, 2 and 3, it would return
   three rows pairing invoice 1 with client 1, invoice 2 with client 2 and invoice 3 with
   client 3 — **plausible-looking rows that are completely wrong**, since the pairing is a
   coincidence of numbering rather than a real relationship. This is more dangerous than the
   error in part 1, because an error stops you and a wrong answer does not.
3. The correct query:

   ```sql
   SELECT i.total, c.name
   FROM invoice i
   JOIN client c ON i.client_id = c.id;
   ```

   `USING` could work for this link only if **both ends were named the same**. That would mean
   renaming `client.id` to `client_id`, so the schema used `<table>_id` as the primary key name
   throughout, and then `USING (client_id)` would be valid. It is a real convention, but changing
   a schema to enable a shorthand is the wrong trade — and note it would also make the dangerous
   `NATURAL JOIN` more likely to find a match, since `created_at` is already shared by both
   tables.

### Your doubts

None for this lesson.

---

## Lesson 13 — Three-table joins with a filter and an order

### Definition

Joining a third table is not a new idea: write another `JOIN` and give it its own `ON`.

```sql
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
```

Two joins, two `ON` conditions. Each `JOIN` brings in exactly one table and says how it connects.

### Why it is needed

Almost no real question lives in two tables. The task calls this *"the shape of a real API
query"* — an event page needs the name from `event`, the city from `venue`, and prices from
`price_tier`. Three tables, one answer, one trip to the database. The alternative is the N+1
problem tripled: fetch events, loop and fetch each venue, loop again and fetch each event's
prices.

### How it works — three rules

1. **Joins are applied left to right.** The query above means `(event joined to venue)` first,
   then `(that result joined to price_tier)`. The pair-then-test model runs twice.
2. **Each `JOIN` needs exactly one `ON`.** Omitting one is a syntax error — the protection
   against accidental Cartesian products. A comma list has no such protection.
3. **A later `ON` may reference any table already in the query.** Above, the second `ON` reaches
   back to `event`, not `venue`. It cannot reference a table not yet joined.

### Example — real run, the S9 query

```text
SELECT e.name AS event_name,
       v.city,
       pt.name AS tier_name,
       pt.amount
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Hyderabad'
ORDER BY pt.amount;

     event_name     |   city    | tier_name |  amount
--------------------+-----------+-----------+-----------
 Classical Evening  | Hyderabad | General   |  999.0000
 Monsoon Rock Night | Hyderabad | General   | 1499.0000
 Monsoon Rock Night | Hyderabad | Balcony   | 2499.0000
 Monsoon Rock Night | Hyderabad | VIP       | 4999.0000
(4 rows)
```

A real answer to a real question: *what can I buy in Hyderabad, and what does it cost?*

### Walkthrough — build it one join at a time

Never write a three-table join in one go. Add one join, check the count, explain the change.

```text
SELECT count(*) AS step1_event_only FROM event e;
                4

SELECT count(*) AS step2_plus_venue
FROM event e JOIN venue v ON e.venue_id = v.id;
                4

SELECT count(*) AS step3_plus_tiers
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id;
                6

SELECT count(*) AS step4_plus_filter
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Hyderabad';
                4
```

- **Stage 2 stays at 4 — no fan-out.** Each event has exactly one venue, because
  `event.venue_id` is `NOT NULL` and points at the unique primary key `venue.id`.
- **Stage 3 grows to 6 — fan-out.** `price_tier` holds the foreign key `event_id`, so it is the
  "many" side. Monsoon fanned into 3, Jazz into 2, Classical into 1; Open Mic Night dropped out
  (no tiers, inner join). 3 + 2 + 1 = 6.
- **Stage 4 falls to 4.** Hyderabad is Riverside Arena, hosting Monsoon (3 tiers) and Classical
  (1 tier).

**4, 4, 6, 4 — every change explainable. A count you cannot explain is a bug you have not found
yet.**

### Which direction fans out

| Join | Which table holds the foreign key | Direction | Row count |
| --- | --- | --- | --- |
| `event` to `venue` | `event` holds `venue_id` | towards the "one" side | **no change** |
| `event` to `price_tier` | `price_tier` holds `event_id` | towards the "many" side | **multiplies** |

**Joining towards the table that holds the foreign key multiplies rows; joining towards the
table the foreign key points at does not.**

### Mixing an outer join into a three-table query

Mumbai means The Blue Note, hosting Jazz at the Note (2 tiers) and Open Mic Night (none).

```text
-- all inner joins
SELECT e.name AS event_name, v.city, pt.name AS tier_name, pt.amount
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Mumbai'
ORDER BY pt.amount;

    event_name    |  city  |  tier_name  |  amount
------------------+--------+-------------+-----------
 Jazz at the Note | Mumbai | General     | 1999.0000
 Jazz at the Note | Mumbai | Front Table | 3499.0000
(2 rows)


-- only the last join made outer
FROM event e
JOIN venue v            ON e.venue_id = v.id
LEFT JOIN price_tier pt ON pt.event_id = e.id

    event_name    |  city  |  tier_name  |  amount
------------------+--------+-------------+-----------
 Jazz at the Note | Mumbai | General     | 1999.0000
 Jazz at the Note | Mumbai | Front Table | 3499.0000
 Open Mic Night   | Mumbai | «NULL»      |    «NULL»
(3 rows)
```

The two joins have different types **on purpose**:

- `JOIN venue` is **inner** — an event with no venue cannot exist, so a left join would add only
  doubt.
- `LEFT JOIN price_tier` is **outer** — an event with no prices is normal and must still appear.

### A `WHERE` that is safe next to a `LEFT JOIN`

This query has `WHERE v.city = 'Mumbai'` below a `LEFT JOIN`, yet it is fine — because `v` is
**not** the right-hand table of that left join. `venue` is inner joined; the left join is to
`price_tier`. On every result row `v.city` holds a real value, so the test is answerable.

| Condition in `WHERE` on... | Safe? |
| --- | --- |
| A table joined with `INNER JOIN` | **Yes** — those columns are never `NULL` from the join |
| The left-hand table | **Yes** — removing left rows is usually the intent |
| The right-hand table of a `LEFT JOIN` | **No**, unless it is an `IS NULL` anti-join test |

Moving `v.city = 'Mumbai'` into the venue join's `ON` would change nothing. Moving a condition
about `pt.amount` into `WHERE` would break it.

### Where the `NULL` sorts

Postgres puts `NULL` **after** every real value in ascending order by default:

```text
... ORDER BY pt.amount NULLS FIRST;

    event_name    |  city  |  tier_name  |  amount
------------------+--------+-------------+-----------
 Open Mic Night   | Mumbai | «NULL»      |    «NULL»
 Jazz at the Note | Mumbai | General     | 1999.0000
 Jazz at the Note | Mumbai | Front Table | 3499.0000
(3 rows)
```

For "cheapest first" the default is usually right: events with no price belong at the bottom,
not advertised as the cheapest thing available.

### Common mistakes

| Mistake | What goes wrong | The correct way |
| --- | --- | --- |
| Writing all three joins at once, then debugging | You cannot tell which join caused the wrong count | Add one join at a time, check the count after each |
| Not expecting fan-out when the third table is the "many" side | Count jumps, totals wrong | Check which table holds the foreign key first |
| `INNER JOIN` everywhere out of habit | Rows with no children vanish, like `Open Mic Night` | Choose per join: inner where the schema guarantees a match, left where it does not |
| A condition about the left-joined table in `WHERE` | The left join silently becomes inner | Put it in that join's `ON` |
| Avoiding `WHERE` entirely after learning that rule | Over-correction; cluttered `ON` clauses | `WHERE` on inner-joined tables is perfectly safe |
| Switching to a comma list for the third table | No protection against a missing condition | One `JOIN ... ON` per table |
| Forgetting `NULL` sorts last | "Cheapest first" puts price-less rows somewhere surprising | Say `NULLS FIRST` / `NULLS LAST` when it matters |

### Where it is used

- Almost every real API endpoint — detail and list pages are nearly always three or more tables.
- Reports: sales by region by month.
- Permission checks: user to role to permission.
- Anywhere a junction table sits in the middle: student to enrolment to course.

### Link to the task

This is **S9**: *"Write one three-table join: `event` to `venue` to `price_tier`, filtered by
city and ordered by price."* The query in the example section is the deliverable; the Mumbai
left-join version is the better shape for a real endpoint.

### Do it yourself

**Step 1 — open a SQL prompt and make `NULL` visible.**

```bash
docker exec -it pg psql -U postgres -d zenith
```

```sql
\pset null '«NULL»'
```

**Step 2 — build up in stages, predicting each count first.**

```sql
SELECT count(*) AS step1 FROM event e;

SELECT count(*) AS step2
FROM event e JOIN venue v ON e.venue_id = v.id;

SELECT count(*) AS step3
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id;

SELECT count(*) AS step4
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Hyderabad';
```

Expected: `4`, `4`, `6`, `4`. Say why each one changes or does not.

**Step 3 — run the full S9 query.**

```sql
SELECT e.name AS event_name, v.city, pt.name AS tier_name, pt.amount
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Hyderabad'
ORDER BY pt.amount;
```

Expected: 4 rows — 999, 1499, 2499, 4999.

**Step 4 — switch city and lose an event.**

```sql
... WHERE v.city = 'Mumbai' ORDER BY pt.amount;
```

Expected: 2 rows, both `Jazz at the Note`. Name the missing Mumbai event.

**Step 5 — bring it back with one word.**

```sql
SELECT e.name AS event_name, v.city, pt.name AS tier_name, pt.amount
FROM event e
JOIN venue v            ON e.venue_id = v.id
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Mumbai'
ORDER BY pt.amount;
```

Expected: 3 rows, `Open Mic Night | Mumbai | «NULL» | «NULL»` last.

**Step 6 — say why this `WHERE` is safe.** Which table does the `WHERE` name, and which table is
on the right of the left join? They differ — that is the whole answer.

**Step 7 — move the `NULL` to the top.**

```sql
... ORDER BY pt.amount NULLS FIRST;
```

**Done when:** you can write the step 3 query from memory, you predicted all four counts in
step 2, and you can explain why one join in step 5 is inner and the other is left.

### Summary for revision

- One `JOIN` per table, each with its own `ON`. Joins apply **left to right**.
- A later `ON` may reference any table already joined, not one still to come.
- **Build up one join at a time and check the count after each.** Here: 4 → 4 → 6 → 4.
- Joining **towards the foreign-key holder** multiplies rows; joining **towards the table it
  points at** does not.
- Mix join types deliberately: inner where the schema guarantees a match, left where it does not.
- `WHERE` is safe on inner-joined tables and on the left table; it is dangerous only on the
  right-hand table of an outer join.
- `NULL` sorts **last** in ascending order by default; override with `NULLS FIRST`.

### Check questions, with answers

**Question 1.** Three tables, shown in full:

```text
Table: region              Table: store                      Table: review
 id | region_name           id | region_id | store_name       id | store_id | stars
----+-------------         ----+-----------+------------     ----+----------+-------
  1 | North                 10 |         1 | Oak St            20 |       10 |     5
  2 | South                 11 |         1 | Elm St            21 |       10 |     3
                            12 |         2 | Bay Rd            22 |       12 |     4
```

`Elm St` has no reviews.

```sql
SELECT r.region_name, s.store_name, rv.stars
FROM region r
JOIN store s   ON s.region_id = r.id
JOIN review rv ON rv.store_id = s.id
WHERE r.region_name = 'North'
ORDER BY rv.stars;
```

1. Row count at each stage, and which stage fans out.
2. The exact rows returned.
3. Rewrite so every North store is listed, including ones with no reviews. How many rows, and
   why did you change exactly one join?

**Answer.**

1. **`region` alone: 2.** **After joining `store`: 3** — this already fans out, because `store`
   holds the foreign key `region_id`, so North multiplies into 2 rows (Oak St, Elm St) and South
   into 1. **After joining `review`: 3** — Oak St fans into 2 (its two reviews), Bay Rd stays at
   1, and **Elm St disappears** because it has no reviews and this is an inner join. The total
   coincidentally stays 3, which is a good reminder that an unchanged count does not mean nothing
   happened. **After the `WHERE`: 2** — only North rows survive, and Bay Rd is in South.
2. **2 rows**, ordered by stars ascending: `North | Oak St | 3`, then `North | Oak St | 5`.
3. The rewrite changes only the review join:

   ```sql
   SELECT r.region_name, s.store_name, rv.stars
   FROM region r
   JOIN store s        ON s.region_id = r.id
   LEFT JOIN review rv ON rv.store_id = s.id
   WHERE r.region_name = 'North'
   ORDER BY rv.stars;
   ```

   It returns **3 rows**: `North | Oak St | 3`, `North | Oak St | 5`, and
   `North | Elm St | NULL` last (Postgres sorts `NULL` last by default).

   Only the review join changed because only that relationship is optional. A store without
   reviews is ordinary, so reviews must be outer joined. A store without a region is impossible —
   `store.region_id` is the foreign key that defines which region it belongs to — so an inner
   join there is correct and a left join would suggest a case that cannot occur. Note also that
   `WHERE r.region_name = 'North'` remains safe: `region` is inner joined, so `region_name` is
   never `NULL` and the filter cannot silently undo the left join.

**Question 2.** Three table definitions:

```text
Table: author   -- 50 rows      Table: article  -- 400 rows     Table: comment -- 9000 rows
  id          integer PK          id         integer PK           id         integer PK
  author_name text not null       author_id  integer not null     article_id integer not null
                                             FK -> author.id                 FK -> article.id
                                  title      text not null        body       text not null
```

```sql
SELECT a.author_name, ar.title, c.body
FROM author a
JOIN article ar ON ar.author_id = a.id
JOIN comment c  ON c.article_id = ar.id;
```

1. Exactly how many rows, justified from the structures alone?
2. They run `SELECT count(*)` on this join to count **authors**. What does that number count,
   and what is the right expression?
3. They add `LIMIT 20` for "the first twenty articles". What do they get, and what is the
   restructured query?

**Answer.**

1. **Exactly 9,000 rows.** Work outwards from the most-joined table. Every comment has a
   `not null` `article_id` backed by a foreign key to a unique primary key, so each comment
   matches exactly one article — never zero, never two. Every article likewise matches exactly
   one author. So each of the 9,000 comments contributes precisely one output row, and the row
   count equals the size of the **innermost "many" table**. Authors and article titles are
   heavily repeated: 400 titles spread over 9,000 rows, and 50 author names over the same 9,000.
   Note that articles with no comments and authors with no articles are silently absent, since
   both joins are inner.
2. `count(*)` counts **output rows, which here means comments** — it would return 9,000, not a
   number of authors. The correct expression is:

   ```sql
   SELECT count(DISTINCT a.id) FROM author a
   JOIN article ar ON ar.author_id = a.id
   JOIN comment c  ON c.article_id = ar.id;
   ```

   That counts authors who have at least one commented-on article, which is at most 50. If the
   real question is "how many authors do we have", the answer is `SELECT count(*) FROM author;`
   with no join at all — joining can only lose authors here, never add them. Note `a.id` rather
   than `a.author_name`: two authors may share a name, and counting the key cannot merge them.
3. `LIMIT 20` takes **twenty comments**, not twenty articles. Since a popular article can easily
   own more than twenty comments, the page might show **a single article**, and whichever article
   lands at the cut appears with only part of its comments — a partially loaded object that looks
   complete. The restructured query picks the page of articles first, then fetches their comments:

   ```sql
   WITH page AS (
     SELECT id, author_id, title FROM article ORDER BY id LIMIT 20
   )
   SELECT a.author_name, p.title, c.body
   FROM page p
   JOIN author a       ON p.author_id = a.id
   LEFT JOIN comment c ON c.article_id = p.id
   ORDER BY p.title;
   ```

   This returns twenty complete articles and however many comments they own between them — a row
   count far above 20, which is correct rather than a bug. The comment join is `LEFT` so that an
   article with no comments still appears on the page.

### Your doubts

None for this lesson.

---

# Final run-through — all ten subtasks, end to end

All thirteen concept lessons are complete. This section executes every subtask against the real
database, in order, with the final data. Everything below is real captured output.

## Final state of the data

| Venue | City | Capacity | Events it hosts |
| --- | --- | --- | --- |
| Riverside Arena | Hyderabad | 12000 | Monsoon Rock Night, Classical Evening |
| The Blue Note | Mumbai | 350 | Jazz at the Note, Open Mic Night |
| Lakeside Hall | Chennai | 800 | **none** |

| Event | Venue | Its price tiers |
| --- | --- | --- |
| Monsoon Rock Night | Riverside Arena | General 1499, Balcony 2499, VIP 4999 |
| Classical Evening | Riverside Arena | General 999 |
| Jazz at the Note | The Blue Note | General 1999, Front Table 3499 |
| Open Mic Night | The Blue Note | **none** |

**3 venues, 4 events, 6 price tiers.** The two deliberate gaps — a venue with no events and an
event with no tiers — are what make the outer joins show anything.

Before running anything, in psql:

```sql
\pset null '«NULL»'
```

---

## S1 — Inner join `event` to `venue`, returning event name and city

**Predict first.** `event.venue_id` is `NOT NULL` and carries a foreign key to `venue.id`, which
is a unique primary key. So every event matches **exactly one** venue — never zero, never two.
4 events → **4 rows**. Step 1 builds 4 × 3 = 12 pairs and step 2 keeps 4.

```text
SELECT e.name AS event_name, v.city
FROM event e
INNER JOIN venue v ON e.venue_id = v.id
ORDER BY e.name;

     event_name     |   city
--------------------+-----------
 Classical Evening  | Hyderabad
 Jazz at the Note   | Mumbai
 Monsoon Rock Night | Hyderabad
 Open Mic Night     | Mumbai
(4 rows)

SELECT count(*) AS s1_inner_rows FROM event e INNER JOIN venue v ON e.venue_id = v.id;
 s1_inner_rows
---------------
             4
```

**Prediction confirmed: 4.** Note what is *absent* — `Chennai` never appears, because Lakeside
Hall hosts nothing and an inner join discards it in silence.

---

## S2 — An event with no tiers, found through a `LEFT JOIN`

`Open Mic Night` was inserted with no price tiers:

```sql
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000004',
        '22222222-2222-2222-2222-222222222222',
        'Open Mic Night', '2026-12-12 20:00+05:30');
```

```text
SELECT count(*) AS s2_left_rows FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id;
 s2_left_rows
--------------
            7
```

The inner join gives 6 rows (one per tier). The left join gives **7** — the extra row is the
rescued one:

```text
SELECT e.name AS event_name, pt.id AS tier_id, pt.name AS tier_name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.id IS NULL;

   event_name   | tier_id | tier_name | amount
----------------+---------+-----------+--------
 Open Mic Night | «NULL»  | «NULL»    | «NULL»
(1 row)
```

**Every** right-hand column is `NULL`, including `pt.id` — a primary key that can never be
`NULL` in the stored table. The rescued row is not a stored row; the join invented it to hold
the left side.

**A `LEFT JOIN` is how you ask "all of these, plus their details if they have any".**

---

## S3 — The right and full versions, run once each

```text
-- RIGHT: every venue, events if any
SELECT e.name AS event_name, v.name AS venue_name
FROM event e RIGHT JOIN venue v ON e.venue_id = v.id
ORDER BY v.name, e.name;

     event_name     |   venue_name
--------------------+-----------------
 «NULL»             | Lakeside Hall
 Classical Evening  | Riverside Arena
 Monsoon Rock Night | Riverside Arena
 Jazz at the Note   | The Blue Note
 Open Mic Night     | The Blue Note
(5 rows)

-- FULL: everything from both — identical to RIGHT here
FROM event e FULL JOIN venue v ON e.venue_id = v.id
(5 rows, same content)
```

`FULL` added nothing because `event.venue_id` is `NOT NULL` with a foreign key, so an orphan
event is impossible. **In a well-constrained schema, a full join usually degenerates into a left
or right join.**

**The note to keep:** `A RIGHT JOIN B` equals `B LEFT JOIN A`. Proved on this data —
`FROM venue v LEFT JOIN event e ON e.venue_id = v.id` returns the same 5 rows. **Always write
`LEFT` and reorder the tables**, because the first table named is the one a reader assumes the
query is about.

---

## S4 — Break it on purpose: the silent inner join

```text
-- Loses events that have no tier, despite being a LEFT JOIN
SELECT count(*) AS s4_where_version
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 50;
 s4_where_version
------------------
                6

-- Keeps every event, filtering only which tiers come along
SELECT count(*) AS s4_on_version
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 50;
 s4_on_version
---------------
             7
```

**6 against 7.** One word moved, one row's difference, no error either time. The missing row is
`Open Mic Night`.

A sharper threshold shows more. All six tiers clear 50, but only three clear 2000:

| Threshold | `WHERE` version | `ON` version |
| --- | --- | --- |
| `> 50` | 6 rows | 7 rows |
| `> 2000` | 3 rows | 5 rows |

At 2000 the `ON` version also rescues **`Classical Evening`**, which *has* a tier (General 999)
whose amount fails the test. Moving a condition into `ON` protects not only rows with no
partners, but rows whose partners all failed the condition.

---

## S5 — The fix, explained

**A condition in `WHERE` is applied after the join and throws away rows whose right side is
`NULL`, which turns the outer join back into an inner one. A condition in `ON` decides what
counts as a match in the first place.**

The mechanism, step by step:

1. `FROM`/`JOIN` runs **first**: pair, test, and **rescue** unmatched left rows.
2. The rescued row for `Open Mic Night` carries `NULL` in every `price_tier` column.
3. `WHERE` runs **second**, on that finished result.
4. `pt.amount > 50` becomes `NULL > 50`, which is **unknown** — not false, unknown.
5. `WHERE` keeps a row only when its condition is **true**. Unknown is discarded just like false.
6. The rescued row dies, and the result is identical to an inner join.

```text
SELECT NULL > 50 AS null_gt_50, NULL = NULL AS null_eq_null;
 null_gt_50 | null_eq_null
------------+--------------
 «NULL»     | «NULL»
```

**Why it survives code review:** on an inner join there is no rescue step, so `ON` and `WHERE`
produce identical results. The habit is correct everywhere it was first learned.

**The one deliberate exception:** `WHERE <right-hand key> IS NULL` keeps *only* the rescued rows.
That is the anti-join, and it is how S2 found `Open Mic Night`.

---

## S6 — Break it on purpose: the Cartesian product

**Predict first:** 4 events × 3 venues = **12**.

```text
SELECT count(*) AS s6_cartesian FROM event, venue;
 s6_cartesian
--------------
           12
```

**Confirmed.** Every event is paired with every venue, so 8 of those 12 rows state something
false.

Note that the modern syntax refuses to do this:

```text
SELECT e.name, v.name FROM event e JOIN venue v;
ERROR:  syntax error at or near ";"
```

**`JOIN` demands an `ON`. The comma form does not.** That is the single best reason to prefer
`JOIN ... ON` everywhere.

**Cost at scale, measured on this container:**

| Input | Output rows | Time |
| --- | --- | --- |
| filtered baseline | 1 | 0.697 ms |
| 4,000 × 4,000 | 16,000,000 | 458.786 ms |
| 20,000 × 20,000 | 400,000,000 | 9,352.869 ms |

Thirteen thousand times slower than the baseline, just to *count* the rows.

---

## S7 — Fan-out, and counting it correctly

`Monsoon Rock Night` was given a third tier, so it appears three times:

```text
SELECT e.name AS event_name, pt.name AS tier_name, pt.amount
FROM event e JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.amount;

     event_name     |  tier_name  |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | Balcony     | 2499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(6 rows)
```

```text
SELECT count(*) AS s7_count_star, count(DISTINCT e.id) AS s7_distinct_events
FROM event e JOIN price_tier pt ON pt.event_id = e.id;

 s7_count_star | s7_distinct_events
---------------+--------------------
             6 |                  3
```

**`COUNT(*)` returned 6 — the number of tiers. `COUNT(DISTINCT e.id)` returned 3 — the number of
events.** Fixed exactly as the task asks.

Two refinements worth keeping:

- **Count the key, not the label.** `count(DISTINCT pt.name)` gives **4** while
  `count(DISTINCT pt.id)` gives **6**, because three separate tiers are all named `General`. A
  primary key is unique by definition; a name is not.
- **After a `LEFT JOIN`, count a right-hand column, never `*`.** Grouped per event, `count(*)`
  reports **1** for `Open Mic Night` while `count(pt.id)` correctly reports **0**, because the
  rescued row is still a row.

**The same damage hits `SUM`:** true total venue capacity is 13,150, but summed across a join
down to `price_tier` it becomes **48,700**, because Riverside Arena is counted 4 times and The
Blue Note twice. (12000 × 4) + (350 × 2) = 48,700.

---

## S8 — Why fan-out plus `LIMIT` matters later

**`LIMIT` runs last**, after the fan-out, and it counts **rows**, not parents.

```text
... ORDER BY e.name, pt.amount LIMIT 3;   →  3 rows, but only 2 events
... ORDER BY e.name, pt.amount LIMIT 4;   →  Monsoon Rock Night with 1 of its 3 tiers
... LIMIT 3 OFFSET 3;                     →  page two holds 1 event
```

The `LIMIT 4` case is the dangerous one. A screen receiving it shows Monsoon Rock Night with a
single ticket type and would report a highest price of ₹1499 when the truth is ₹4999. **Nothing
is marked incomplete.** That is the *partial page of the wrong thing*.

**The Phase 3 connection.** Hibernate's `JOIN FETCH` loads a parent with its collection of
children using exactly this kind of join. Ask for a page at the same time and it cannot push the
limit into the database, because n rows is not n parents — the limit would slice a parent in
half. So it fetches **every** matching row into memory, assembles the parents, and takes the page
from that list, logging *"firstResult/maxResults specified with collection fetch; applying in
memory"*. On five rows nobody notices; on five million it loads all five million to show ten.

**The fix, in SQL or in Java, is the same shape:** pick the page of parents first, then fetch
their children.

```text
WITH page AS (SELECT id, name FROM event ORDER BY name LIMIT 3)
SELECT p.name, pt.name, pt.amount
FROM page p LEFT JOIN price_tier pt ON pt.event_id = p.id
ORDER BY p.name, pt.amount;
→ 6 rows covering exactly 3 events, every one complete
```

6 rows for a page of 3 is **correct**, not a bug: three events own six tiers between them.

---

## S9 — The three-table join

Built one join at a time, explaining each row count:

| Stage | Query | Rows | Why |
| --- | --- | --- | --- |
| 1 | `event` alone | 4 | four events |
| 2 | `+ JOIN venue` | 4 | **no fan-out** — joining towards the "one" side |
| 3 | `+ JOIN price_tier` | 6 | **fan-out** — `price_tier` holds the foreign key |
| 4 | `+ WHERE city = 'Hyderabad'` | 4 | Riverside Arena only: 3 tiers + 1 tier |

```text
SELECT e.name AS event_name,
       v.city,
       pt.name AS tier_name,
       pt.amount
FROM event e
JOIN venue v       ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Hyderabad'
ORDER BY pt.amount;

     event_name     |   city    | tier_name |  amount
--------------------+-----------+-----------+-----------
 Classical Evening  | Hyderabad | General   |  999.0000
 Monsoon Rock Night | Hyderabad | General   | 1499.0000
 Monsoon Rock Night | Hyderabad | Balcony   | 2499.0000
 Monsoon Rock Night | Hyderabad | VIP       | 4999.0000
(4 rows)
```

**The better version for a real endpoint** keeps events that have no prices yet:

```text
SELECT e.name AS event_name, v.city, pt.name AS tier_name, pt.amount
FROM event e
JOIN venue v            ON e.venue_id = v.id
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE v.city = 'Mumbai'
ORDER BY pt.amount;

    event_name    |  city  |  tier_name  |  amount
------------------+--------+-------------+-----------
 Jazz at the Note | Mumbai | General     | 1999.0000
 Jazz at the Note | Mumbai | Front Table | 3499.0000
 Open Mic Night   | Mumbai | «NULL»      |    «NULL»
(3 rows)
```

The two joins differ **on purpose**: `venue` is inner joined because an event with no venue
cannot exist, and `price_tier` is left joined because an event with no prices is ordinary.

**The `WHERE` here is safe** even though a `LEFT JOIN` is present, because `v` is the
**inner-joined** table, not the right-hand side of the left join. `v.city` is never `NULL`, so
nothing is discarded by accident.

---

## S10 — Diagrams and the AI drill

The four joins are drawn in **`NOTES.md`** alongside the Cartesian product, fan-out, the
`ON`-versus-`WHERE` bug, and a pre-flight checklist.

**The prompt to use for the drill:**

> Give me five join questions over a ticketing schema of `venue`, `event` and `price_tier`,
> ranging from easy to difficult. Do not give me the answers until I have written mine, then mark
> them and explain what I misunderstood.

Schema to paste with it:

```text
venue       (id PK, name, city, capacity)
event       (id PK, venue_id FK -> venue.id NOT NULL, name, starts_at, cancelled_at NULL)
price_tier  (id PK, event_id FK -> event.id NOT NULL, name, amount, UNIQUE(event_id, name))
```

---

# Checkpoint — the four answers

**1. Which rows does a `LEFT JOIN` keep that an `INNER JOIN` throws away?**

The rows from the **left** table that found no partner. An inner join keeps only surviving pairs,
so a left row matching nothing disappears with no warning. A left join adds a third step — the
**rescue** — which puts those rows back on their own, with `NULL` in every right-hand column.
Here, `event LEFT JOIN price_tier` returns 7 rows against the inner join's 6, and the extra row
is `Open Mic Night`, the event with no price tiers.

Two refinements. First, a rescued row carries `NULL` in **every** right-hand column, including
the right table's primary key — which is what makes `WHERE pt.id IS NULL` a reliable way to find
them. Second, a left join promises each left row appears **at least once**, not exactly once:
`Monsoon Rock Night` appears three times because it has three tiers.

**2. Why does putting a condition on the right-hand table in `WHERE` turn a left join into an
inner one?**

Because of the order the clauses run in. `FROM`/`JOIN` runs **first** and completes the rescue
step, so by the time `WHERE` runs, the rescued rows are already sitting in the result — and
`WHERE` is free to delete them.

And it does. A rescued row holds `NULL` in every right-hand column, so a condition like
`pt.amount > 50` becomes `NULL > 50`, which evaluates to **unknown** rather than false. `WHERE`
keeps a row only when its condition is **true**, and discards unknown exactly as it discards
false. Every rescued row is removed, leaving precisely the inner join's rows. Measured here: 6
rows with the condition in `WHERE`, 7 with it in `ON`.

The fix is to move the condition into `ON` with `AND`, so it decides **what counts as a match**
rather than filtering a finished result. The reason this bug is so common is that on an inner
join the two placements are genuinely interchangeable — there is no rescue step, so there is
nothing for `WHERE` to kill.

**3. An event with three price tiers appears three times. Why, and what does that do to
`COUNT(*)`?**

Because **each surviving pair becomes one output row** — not each left row. Step 1 pairs every
event with every tier, step 2 keeps the pairs where `pt.event_id = e.id`, and `Monsoon Rock
Night` sits in three of those surviving pairs. Its own columns are copied unchanged into all
three rows. This is **fan-out**, and it happens whenever you join towards the table that holds
the foreign key — the "many" side.

`COUNT(*)` counts output rows without looking inside them, so it reports the number of **tiers**,
not events: **6** instead of 3. The fix is `COUNT(DISTINCT e.id)`, which returns **3**. Use the
primary key, not the name — `count(DISTINCT pt.name)` gives 4 for 6 tiers, because three are all
called `General`.

The same inflation hits `SUM`: total venue capacity reads 48,700 across this join instead of the
true 13,150. And after a `LEFT JOIN`, grouped per parent, `COUNT(*)` reports **1** for a parent
with no children, because the rescued row is still a row — use `COUNT(pt.id)` to get 0.

**4. What is a Cartesian product and what causes one by accident?**

Every row of one table paired with every row of another, with **no condition applied** — step 1
of a join with step 2 missing. Its size is the two table sizes multiplied: here 4 events × 3
venues = **12 rows**.

The accidental cause is the **comma form** of a join with a missing or incomplete `WHERE`. Most
often a query already joins two tables correctly, someone needs a column from a third, appends
`, price_tier pt` to the list, takes the column, and forgets the matching condition. The result
is multiplied by every row of the new table, with no error.

Writing `JOIN ... ON` prevents it, because `JOIN` with no `ON` is a **syntax error** — Postgres
refuses the query rather than silently producing the product. The cost is the reason to care:
measured on this container, 20,000 × 20,000 produced 400 million rows in 9.4 seconds, against
0.7 ms for a filtered baseline.

---

# Done when — self-check

- [x] **Write a three-table join with filtering and ordering.** S9 above: `event` to `venue` to
      `price_tier`, filtered by city, ordered by price.
- [x] **Show both the broken and the fixed version of the `LEFT JOIN` bug from memory:**

```sql
-- BROKEN: silently an inner join
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE pt.amount > 50;

-- FIXED: a real left join
SELECT e.name, pt.amount
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id AND pt.amount > 50;
```

---

# One-page revision card

| Idea | The one sentence to remember |
| --- | --- |
| What a join is | One query reading two tables, matched by a condition you write. |
| Why tables are split | Each fact stored once, so two copies cannot drift apart. |
| Aliases | `FROM event e` makes `e` **the** name for that query; `event.name` then fails. |
| The model | **Pair every row, then keep the pairs that pass `ON`.** |
| `ON` | Any true-or-false test. It need not compare keys, and SQL will not warn you. |
| `INNER JOIN` | Matched pairs only. Unmatched rows on **both** sides vanish silently. |
| Predicting counts | `NOT NULL` + foreign key → unique primary key means **exactly one** match. |
| Cartesian product | Size = left × right. `JOIN` without `ON` is a syntax error; a comma is not. |
| `LEFT JOIN` | Inner join **plus a rescue step**. Every left row appears **at least** once. |
| `RIGHT` / `FULL` | `A RIGHT JOIN B` = `B LEFT JOIN A`. Always write `LEFT` and reorder. |
| `ON` vs `WHERE` | `WHERE` runs **after** the rescue, and `NULL > 50` is unknown, so rescued rows die. |
| Anti-join | `LEFT JOIN ... WHERE right.key IS NULL` finds rows with no partner. |
| Fan-out | Each **surviving pair** is one output row. Joining towards the FK holder multiplies. |
| Counting | `COUNT(*)` counts rows; `COUNT(col)` skips `NULL`; `COUNT(DISTINCT key)` undoes fan-out. |
| `LIMIT` | Runs last, counts rows not parents. Page the parents in a CTE first. |
| `USING` / `NATURAL` | `USING` needs identical names; `NATURAL JOIN` guesses invisibly — never use it. |
| Three tables | One `JOIN ... ON` each. Build up one at a time and explain every row count. |

---

# Cleanup, if you want the original data back

These three rows were added during the task. Lessons 4, 6, 7, 9, 10 and 11 all depend on them,
so keep them unless you have a reason not to.

```sql
DELETE FROM price_tier WHERE id = 'bbbbbbbb-0000-0000-0000-000000000006';  -- Balcony tier
DELETE FROM event      WHERE id = 'aaaaaaaa-0000-0000-0000-000000000004';  -- Open Mic Night
DELETE FROM venue      WHERE id = '33333333-3333-3333-3333-333333333333';  -- Lakeside Hall
```

Delete in that order. The foreign keys forbid removing a parent while a child still points at it.

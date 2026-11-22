# F5.6 — Grouping, aggregates, and CTEs

Notes for Task F5.6. One lesson per section. Each lesson ends with its check questions and full
answers, practice questions, and a **Do it yourself** block with exact commands. Every SQL output
shown here is real output, captured from a running Postgres 17 container. None of it is invented.

**Learning outcome.** Summarise data with aggregate functions and `GROUP BY`. Filter groups with
`HAVING`. Write the same question as both a sub-query and a CTE.

**Why this exists.** Real people ask real questions: "how many tickets sold per event", "revenue
per price tier", "which venues have no upcoming events". Aggregation answers each one in a single
query. The other way is to load everything into Java and loop. That is slower by orders of
magnitude, and it gets worse as the data grows.

---

## Starting state of the machine

These notes start from the state left behind by tasks F5.1 to F5.5:

- A Docker container named `pg`, running the `postgres:17-alpine` image, with port 5432 published.
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

### The seeded data

```text
SELECT v.name, v.city, v.capacity FROM venue v ORDER BY v.name;

      name       |   city    | capacity
-----------------+-----------+----------
 Lakeside Hall   | Chennai   |      800
 Riverside Arena | Hyderabad |    12000
 The Blue Note   | Mumbai    |      350
(3 rows)

SELECT e.name, e.venue_id, e.starts_at, e.cancelled_at FROM event e ORDER BY e.starts_at;

        name        |               venue_id               |       starts_at        |         cancelled_at
--------------------+--------------------------------------+------------------------+-------------------------------
 Monsoon Rock Night | 11111111-1111-1111-1111-111111111111 | 2026-11-20 13:30:00+00 |
 Jazz at the Note   | 22222222-2222-2222-2222-222222222222 | 2026-11-28 15:30:00+00 |
 Classical Evening  | 11111111-1111-1111-1111-111111111111 | 2026-12-05 13:00:00+00 | 2026-10-24 18:05:00.690845+00
 Open Mic Night     | 22222222-2222-2222-2222-222222222222 | 2026-12-12 14:30:00+00 |
(4 rows)

SELECT e.name AS event, pt.name, pt.amount
FROM price_tier pt JOIN event e ON e.id = pt.event_id
ORDER BY e.name, pt.amount;

       event        |    name     |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | General     | 1999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | General     | 1499.0000
 Monsoon Rock Night | Balcony     | 2499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(6 rows)
```

In words:

| Venue | City | Capacity | Events it hosts |
| --- | --- | --- | --- |
| Riverside Arena | Hyderabad | 12000 | Monsoon Rock Night, Classical Evening |
| The Blue Note | Mumbai | 350 | Jazz at the Note, Open Mic Night |
| Lakeside Hall | Chennai | 800 | **none** |

| Event | Venue | Starts (UTC) | Cancelled? | Its price tiers |
| --- | --- | --- | --- | --- |
| Monsoon Rock Night | Riverside Arena | 2026-11-20 13:30 | no | General 1499, Balcony 2499, VIP 4999 |
| Jazz at the Note | The Blue Note | 2026-11-28 15:30 | no | General 1999, Front Table 3499 |
| Classical Evening | Riverside Arena | 2026-12-05 13:00 | **yes** | General 999 |
| Open Mic Night | The Blue Note | 2026-12-12 14:30 | no | **none** |

Three things to know about this data:

- **Two deliberate gaps.** Lakeside Hall hosts no events. Open Mic Night has no price tiers.
- **One cancelled event.** Classical Evening's `cancelled_at` holds a time. The other three events
  hold `NULL` there, which psql prints as a blank. `NULL` means "no value".
- **Times are shown in UTC.** The `+00` at the end of a time means UTC. `13:30:00+00` is 19:00 in
  India, because India is 5 hours 30 minutes ahead of UTC.

### Setting all of this up from nothing

Use this only if the machine has nothing installed yet. It was tested on a fresh database and runs
without error.

**Step A — install Docker Desktop** from
[docker.com](https://www.docker.com/products/docker-desktop/). Open it once, so it starts. Then
check it works:

```bash
docker --version
```

Expected: a line like `Docker version 27.x.x`.

**Step B — start a Postgres container:**

```bash
docker run -d --name pg \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data \
  postgres:17-alpine
```

Expected: one long ID printed. If you see `Conflict. The container name "/pg" is already in use`,
the container exists already. Run `docker start pg` instead.

**Step C — create the database:**

```bash
docker exec pg psql -U postgres -c "CREATE DATABASE zenith;"
```

Expected: `CREATE DATABASE`. If you see `database "zenith" already exists`, skip this step.

**Step D — create and fill the tables.** Save this as `setup.sql`:

```sql
CREATE TABLE venue (
  id             uuid PRIMARY KEY,
  name           text NOT NULL,
  city           text NOT NULL,
  capacity       integer NOT NULL CONSTRAINT venue_capacity_positive CHECK (capacity > 0),
  seat_map_hints jsonb,
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE event (
  id           uuid PRIMARY KEY,
  venue_id     uuid NOT NULL CONSTRAINT event_venue_fk REFERENCES venue(id),
  name         text NOT NULL,
  starts_at    timestamptz NOT NULL,
  created_at   timestamptz NOT NULL DEFAULT now(),
  cancelled_at timestamptz
);

CREATE TABLE price_tier (
  id         uuid PRIMARY KEY,
  event_id   uuid NOT NULL CONSTRAINT price_tier_event_fk REFERENCES event(id),
  name       text NOT NULL,
  amount     numeric(19,4) NOT NULL CONSTRAINT price_tier_amount_non_negative CHECK (amount >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT price_tier_name_unique_per_event UNIQUE (event_id, name)
);

INSERT INTO venue (id, name, city, capacity) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000),
  ('22222222-2222-2222-2222-222222222222', 'The Blue Note',   'Mumbai',      350),
  ('33333333-3333-3333-3333-333333333333', 'Lakeside Hall',   'Chennai',     800);

INSERT INTO event (id, venue_id, name, starts_at, cancelled_at) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   'Monsoon Rock Night', '2026-11-20 19:00+05:30', NULL),
  ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
   'Classical Evening',  '2026-12-05 18:30+05:30', '2026-10-24 23:35+05:30'),
  ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
   'Jazz at the Note',   '2026-11-28 21:00+05:30', NULL),
  ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222',
   'Open Mic Night',     '2026-12-12 20:00+05:30', NULL);

INSERT INTO price_tier (id, event_id, name, amount) VALUES
  ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General',     1499),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP',         4999),
  ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General',      999),
  ('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General',     1999),
  ('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499),
  ('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000001', 'Balcony',     2499);
```

**Step E — load it:**

```bash
docker exec -i pg psql -U postgres -d zenith -v ON_ERROR_STOP=1 < setup.sql
```

Expected:

```text
CREATE TABLE
CREATE TABLE
CREATE TABLE
INSERT 0 3
INSERT 0 4
INSERT 0 6
```

If you see `ERROR: relation "venue" already exists`, the tables are there already. Go to the
check below.

The one small difference you may see: the `cancelled_at` value for Classical Evening shows as
`2026-10-24 18:05:00+00`, without the `.690845` fraction of a second. That fraction came from the
exact moment the original row was cancelled. It changes nothing in this task.

### The check to run before every lesson

```bash
docker start pg
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`. Then:

```sql
SELECT COUNT(*) AS venues FROM venue;
SELECT COUNT(*) AS events FROM event;
SELECT COUNT(*) AS tiers FROM price_tier;
```

Expected: `3`, then `4`, then `6`. If the numbers differ, the machine holds different data, and
the outputs in these notes will not match. Some later lessons add rows on purpose. Each of those
lessons says what the new counts are.

---

## The subtasks of F5.6

| # | Subtask |
| --- | --- |
| S1 | Run each aggregate once with no grouping: total events, cheapest tier, average tier price, earliest start time. Each returns exactly one row. |
| S2 | Group: events per city (needs a join, because `city` lives on `venue`), and tiers per event. Then select a column you did not group by, and read the error. |
| S3 | "Cities with more than two events" with `HAVING COUNT(*) > 2`. "Events in July per city" with `WHERE` on the date. Then combine both, and say which clause runs first. |
| S4 | Count all events, then count `cancelled_at`. Write down when each is the right one. |
| S5 | Break it on purpose: `SUM(v.capacity)` across `venue` → `event` → `price_tier`. See the inflated total. |
| S6 | Write "events whose cheapest tier is above a price" twice: once as a sub-query, once as a CTE with `WITH cheapest AS (...)`. Decide which you would rather meet in a pull request. |
| S7 | Exit questions as single queries: total tier value per event, and venues with no events. |
| S8 | AI drill: the logical order of SQL clauses, and why a `SELECT` alias fails in `WHERE` but works in `ORDER BY`. |

Two notes on the data. Our tiers cost 999 to 4999, so the "above £40" threshold from the task is
adapted to our prices in S6. Also, no event is in July, and no city has more than two events yet.
Lesson 8 adds a few events so S3 has something to show.

## The lesson roadmap

| Lesson | What it teaches | Subtask it serves |
| --- | --- | --- |
| 1 | What an aggregate function is, using `COUNT(*)` | S1: total events |
| 2 | `MIN` and `MAX` | S1: cheapest tier, earliest start time |
| 3 | `SUM` and `AVG` | S1: average tier price. `SUM` is also needed for S5 and S7 |
| 4 | `COUNT(*)` versus `COUNT(column)` | S4 |
| 5 | `GROUP BY` on one table: tiers per event | S2 |
| 6 | The grouping-key rule, and its error | S2 |
| 7 | Grouping across a join: events per city | S2, S7 |
| 7b | Grouping per event across a join: group by the id, not the name | S7: total tier value per event |
| 8 | `HAVING`: filtering groups | S3 |
| 9 | `WHERE` before grouping: July events per city | S3 |
| 10 | `WHERE` and `HAVING` in one query | S3 |
| 11 | The full clause order, and where a `SELECT` alias works | S8 |
| 12 | What a sub-query is: a query in brackets used as one value | S6 |
| 13 | A sub-query used as a table, inside `FROM` | S6 |
| 14 | The correlated sub-query: runs once per outer row | S6 |
| 15 | The CTE: `WITH name AS (...)` | S6 |
| 16 | Aggregating over a fan-out: the plausible wrong `SUM` | S5 |
| — | Final run-through: both exit queries, plus the checkpoint answers | S7, checkpoint |

---

## Lesson 1 — What an aggregate function is, using `COUNT(*)`

### Definition

An **aggregate function** takes many rows and gives back one value. `COUNT(*)` is the simplest
aggregate function. It tells you how many rows there are.

### Why it is needed

Say an app must show "Total events: 1,000,000". Without an aggregate function, Java must ask for
every row. The database sends every row. Then Java counts them in a loop. All of that work
produces one number.

Both ways were measured on this machine, using 1,000,000 made-up rows:

```bash
# Way 1: the database counts, and sends back only the answer
docker exec pg psql -U postgres -d zenith -At \
  -c "SELECT COUNT(*) FROM generate_series(1, 1000000) AS g" | wc -c

# Way 2: the database sends every row, so the client can count them
docker exec pg psql -U postgres -d zenith -At \
  -c "SELECT g, 'Event ' || g FROM generate_series(1, 1000000) AS g" | wc -c
```

Way 1 printed `8`. Way 2 printed `19777792`.

`generate_series(1, 1000000)` only makes the fake rows. It is not part of this lesson. `wc -c`
counts the bytes that came back.

| Way | Bytes the database sent back | Total time, same machine |
| --- | --- | --- |
| The database counts (`COUNT(*)`) | **8** | 0.15 s |
| The database sends every row | **19,777,792** (about 20 MB) | 0.50 s |

That is the same answer for about 2.5 million times less data.

Most of the 0.15 s is the cost of starting `docker exec` and `psql`. Timed inside psql, on a table
of 1,000,000 rows, the count itself took 49 to 72 ms. Sending every row took 155 to 159 ms.

Here, the database and the client sit on one machine. Over a real network, the 20 MB must also
travel. Java must also hold it in memory. The gap grows as the table grows.

### Key points

- An aggregate function: many rows in, **one value** out.
- `COUNT(*)` counts **rows**. The `*` means "the whole row". It does not count columns.
- With no grouping, the result is **always exactly one row**. Even zero matching rows give one
  row, holding `0`.
- `WHERE` runs before `COUNT(*)`. So `COUNT(*)` counts only the rows that passed the test.
- `AS` names the result column. Without it, psql calls the column `count`.

### How it works

Think of a tally counter at a stadium gate. People walk through one by one. At the end, the
counter shows one number. Nobody hands you the list of people.

A query with `COUNT(*)` runs in these steps:

1. **`FROM`** collects the rows of the table.
2. **`WHERE`**, if there is one, keeps only the rows that pass a test. The rest are thrown away.
3. **`COUNT(*)`** looks at all the rows that are left. It squeezes them into one number.
4. The result has **exactly one row**. That row holds the number.

### Example — real run

Here is the whole `event` table:

```text
SELECT name, starts_at FROM event ORDER BY starts_at;

        name        |       starts_at
--------------------+------------------------
 Monsoon Rock Night | 2026-11-20 13:30:00+00
 Jazz at the Note   | 2026-11-28 15:30:00+00
 Classical Evening  | 2026-12-05 13:00:00+00
 Open Mic Night     | 2026-12-12 14:30:00+00
(4 rows)
```

Now count them:

```text
SELECT COUNT(*) FROM event;

 count
-------
     4
(1 row)
```

Four rows went in. One row came out.

The column is called `count` by default. `AS` gives it a better name:

```text
SELECT COUNT(*) AS total_events FROM event;

 total_events
--------------
            4
(1 row)
```

The same works on any table:

```text
SELECT COUNT(*) AS total_tiers FROM price_tier;

 total_tiers
-------------
           6
(1 row)

SELECT COUNT(*) AS total_venues FROM venue;

 total_venues
--------------
            3
(1 row)
```

Function names are not case-sensitive. `count(*)` is the same as `COUNT(*)`:

```text
SELECT count(*) AS lower_case_works FROM event;

 lower_case_works
------------------
                4
(1 row)
```

### Walkthrough

The query counts only some rows:

```sql
SELECT COUNT(*) AS tiers_over_2000 FROM price_tier WHERE amount > 2000;
```

**Step 1, `FROM price_tier`.** The database collects all 6 tiers:

| Tier | amount |
| --- | --- |
| Monsoon Rock Night, General | 1499 |
| Monsoon Rock Night, VIP | 4999 |
| Classical Evening, General | 999 |
| Jazz at the Note, General | 1999 |
| Jazz at the Note, Front Table | 3499 |
| Monsoon Rock Night, Balcony | 2499 |

**Step 2, `WHERE amount > 2000`.** Each row is tested:

| amount | `amount > 2000`? | Kept? |
| --- | --- | --- |
| 1499 | false | no |
| 4999 | true | **yes** |
| 999 | false | no |
| 1999 | false (1999 is less than 2000) | no |
| 3499 | true | **yes** |
| 2499 | true | **yes** |

Three rows are left.

**Step 3, `COUNT(*)`.** It counts those 3 rows and squeezes them into one number.

**Step 4, the result.** One row, holding 3:

```text
 tiers_over_2000
-----------------
               3
(1 row)
```

### Edge case — no row passes the test

No tier costs more than 9000:

```text
SELECT COUNT(*) AS tiers_over_9000 FROM price_tier WHERE amount > 9000;

 tiers_over_9000
-----------------
               0
(1 row)
```

You still get **one row**. It holds `0`. An aggregate function with no grouping always returns
exactly one row, even when zero rows went in. This is what S1 asks you to notice.

An empty result would mean "there is no answer". `0` is an answer: "there are none".

### Important terms

| Term | Meaning |
| --- | --- |
| Aggregate function | A function that takes many rows and gives back one value. |
| `COUNT(*)` | The aggregate function that counts rows. |
| `*` inside `COUNT(*)` | "The whole row". It does not mean "all the columns". |
| Row | One line of a table: one event, one tier, one venue. |
| Result | The rows a query sends back. |
| `AS` | Gives a column in the result a name. |
| `WHERE` | Keeps only the rows that pass a test. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Fetching every row and counting in Java | Millions of rows travel just to get one number. | Use `SELECT COUNT(*) ...` and read the single value. |
| Thinking `*` means "count the columns" | You expect the number of columns. | `COUNT(*)` counts **rows**. The `*` means "the whole row". |
| Leaving the brackets empty: `COUNT()` | An error, shown below. | Write `COUNT(*)`. |
| Putting a plain column next to `COUNT(*)` | An error, shown below. | Select only the count for now. Grouping makes the other case possible. |
| Expecting "no rows" when nothing matches | You get one row holding `0`. | Read the value `0`. Do not check for an empty result. |

The two errors, real:

```text
SELECT COUNT() FROM event;

ERROR:  count(*) must be used to call a parameterless aggregate function
LINE 1: SELECT COUNT() FROM event;
               ^
```

| Part of the error | Plain meaning |
| --- | --- |
| `parameterless` | "With nothing inside the brackets." |
| `count(*) must be used` | To count rows with nothing to look at, write `count(*)`. Empty brackets are not allowed. |
| `LINE 1: ... ^` | The caret points at the exact spot Postgres rejected. |

```text
SELECT name, COUNT(*) FROM event;

ERROR:  column "event.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT name, COUNT(*) FROM event;
               ^
```

| Part of the error | Plain meaning |
| --- | --- |
| `column "event.name"` | The problem is the plain column `name`. |
| `must appear in the GROUP BY clause` | One way out is grouping by it. Grouping is taught later in this task. |
| `or be used in an aggregate function` | The other way out is wrapping it in an aggregate function. |

In plain words: you asked for one count row, but there are 4 names. Which name should go next to
the count? The database cannot pick one, so it refuses.

### Where it is used

- Badges on screens: "12 unread messages", "3 items in your cart".
- Paging text: "Showing 1–20 of 347 results". The 347 comes from a `COUNT(*)`.
- Dashboards and reports: "orders today", "sign-ups this week".
- Quick checks while debugging: "how many rows does this table really have?"

### Link to the task

This is the first part of **S1**: "total events". The answer is
`SELECT COUNT(*) AS total_events FROM event;`. It returns one row, holding 4. The other parts of
S1 use `MIN` and `AVG`, taught in the next two lessons.

### Do it yourself

**Step 0 — only if the laptop has nothing installed.** Do "Setting all of this up from nothing"
at the top of this file first.

**Step 1 — start the database and open a SQL prompt.**

```bash
docker start pg
docker exec -it pg psql -U postgres -d zenith
```

Expected prompt: `zenith=#`

**Step 2 — look at the rows before counting them.**

```sql
SELECT name, starts_at FROM event ORDER BY starts_at;
```

Expected: 4 rows, from `Monsoon Rock Night` to `Open Mic Night`, ending with `(4 rows)`.

**Step 3 — count them.**

```sql
SELECT COUNT(*) AS total_events FROM event;
```

Expected: one row holding `4`, and `(1 row)`.

**Step 4 — count the other two tables.**

```sql
SELECT COUNT(*) AS total_tiers FROM price_tier;
SELECT COUNT(*) AS total_venues FROM venue;
```

Expected: `6`, then `3`. Each one is a single row.

**Step 5 — count some rows, then count none.**

```sql
SELECT COUNT(*) AS tiers_over_2000 FROM price_tier WHERE amount > 2000;
SELECT COUNT(*) AS tiers_over_9000 FROM price_tier WHERE amount > 9000;
```

Expected: `3`, then `0`. Both come back as `(1 row)`.

**Step 6 — trigger the empty-brackets error on purpose.**

```sql
SELECT COUNT() FROM event;
```

Expected failure:

```text
ERROR:  count(*) must be used to call a parameterless aggregate function
```

Leave the prompt with `\q`.

**Done when:** Step 3 shows `4` in exactly one row. Step 5 shows `0` in one row, not an empty
result. You can say why Step 6 fails.

### Summary for revision

- An **aggregate function** takes many rows and gives back one value.
- `COUNT(*)` counts rows. The `*` means "the whole row", not "the columns".
- With no grouping, an aggregate function always returns **exactly one row**. Zero matching rows
  give one row holding `0`.
- `WHERE` throws rows away first. `COUNT(*)` then counts what is left.
- `COUNT()` with empty brackets is an error. Write `COUNT(*)`.
- A plain column next to `COUNT(*)` is an error, because the database cannot pick one value from
  many to put beside the count.
- Counting in the database sends back one small number. Counting in Java sends back every row.
  Measured here: 8 bytes against about 20 MB for 1,000,000 rows.

### Check questions, with answers

**Question 1.** Here is a table called `book`:

```text
 id | title                | pages
----+----------------------+-------
  1 | A Wizard of Earthsea |   183
  2 | Mort                 |   272
  3 | Guards! Guards!      |   288
```

Someone runs this query:

```sql
SELECT COUNT(*) AS long_books FROM book WHERE pages > 1000;
```

(a) How many rows does the result have? (b) What value is in it? (c) A teammate says "it returns
nothing, because no book has more than 1000 pages." Are they right? Explain why or why not.

**Answer.** (a) One row. (b) The value `0`. (c) The teammate is wrong. Here is the order of
events. `FROM book` collects 3 rows. `WHERE pages > 1000` tests each one: 183, 272 and 288 all
fail, so all 3 are thrown away. `COUNT(*)` then counts what is left, which is nothing, and gives
`0`. An aggregate function with no grouping always returns exactly one row, even when zero rows
reach it. Real run, in a throwaway table with the same three rows:

```text
SELECT COUNT(*) AS long_books FROM book WHERE pages > 1000;

 long_books
------------
          0
(1 row)
```

"Nothing" would mean "there is no answer". `0` is an answer: "there are no long books".

**Question 2.** A teammate's Java code runs `SELECT * FROM event`. It then loops over the rows
and adds 1 for each one, to show "Total events: N" on a web page. The `event` table has 2 million
rows. Name two costs of this approach. Then write the one SQL query that replaces it.

**Answer.** Cost one: all 2 million rows travel from the database to Java. `SELECT *` sends every
column too, so each row is bigger than needed. In a measurement on this machine, 1,000,000 small
rows of only two columns were already about 20 MB. Cost two: Java must read every row and run the
loop. That takes time and memory on the app server. Both costs grow with the table. At 4 million
rows, the work doubles again. The replacement:

```sql
SELECT COUNT(*) AS total_events FROM event;
```

The database counts the rows itself. It sends back one row holding one number. In the same
measurement, that answer was 8 bytes.

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `seat`:

```text
 id | row_letter | sold
----+------------+-------
  1 | A          | true
  2 | A          | false
  3 | B          | true
  4 | B          | true
```

What does this query return?

```sql
SELECT COUNT(*) AS sold_seats FROM seat WHERE sold = true;
```

<details>
<summary>Answer</summary>

One row holding `3`. `WHERE sold = true` keeps seats 1, 3 and 4, and throws away seat 2.
`COUNT(*)` counts the 3 rows left. Real run, in a throwaway table with the same four rows:

```text
 sold_seats
------------
          3
(1 row)
```

</details>

**Practice 2.** Using the same `seat` table from Practice 1 (4 rows: ids 1 to 4), which of these
three queries work, and what does each working one return?

```sql
SELECT COUNT() FROM seat;
SELECT COUNT(*) FROM seat;
SELECT count(*) FROM seat;
```

<details>
<summary>Answer</summary>

The second and third work. Both return one row holding `4`. Function names are not
case-sensitive, so `COUNT(*)` and `count(*)` are the same. The first fails, because empty brackets
are not allowed:

```text
ERROR:  count(*) must be used to call a parameterless aggregate function
LINE 1: SELECT COUNT() FROM seat;
               ^
```

</details>

**Practice 3.** Using the same `seat` table from Practice 1 (row letters A, A, B, B), what
happens when this query runs, and why?

```sql
SELECT row_letter, COUNT(*) FROM seat;
```

<details>
<summary>Answer</summary>

It fails:

```text
ERROR:  column "seat.row_letter" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT row_letter, COUNT(*) FROM seat;
               ^
```

`COUNT(*)` with no grouping makes exactly one row. But there are 4 row letters: A, A, B, B. The
database has one slot beside the count and four candidates for it. It cannot pick one, so it
refuses. Grouping, taught later in this task, is the way to get "a count per row letter".

</details>

### Your doubts

None for this lesson.

---

## Lesson 2 — `MIN` and `MAX`

### Definition

An **aggregate function** takes many rows and gives back one value. `MIN` is an aggregate
function that gives back the **smallest** value in a column. `MAX` gives back the **largest**.

### Why it is needed

An event card on a website says "Tickets from ₹999". Somebody has to find that 999. It is the
smallest amount among the price tiers.

Without an aggregate function, Java asks for every tier. The database sends every tier. Java
loops over them and remembers the smallest one. Every row travels across, just to produce one
number. With a million tiers, a million rows travel.

`MIN` does the same search inside the database. Only the answer travels.

### Key points

- `MIN` gives the smallest value in a column. `MAX` gives the largest.
- Inside the brackets goes a **column name**. `MIN(*)` is an error. Only `COUNT` has the `(*)` form.
- "Smallest" depends on the type: lowest number, earliest timestamp, first in alphabetical order.
- Several aggregate functions can share one `SELECT`. The result is still one row.
- With no grouping, the result is always **exactly one row**.
- Zero rows in: `COUNT(*)` gives `0`, but `MIN` and `MAX` give `NULL`.
- `MIN` gives the **value**, not the row. Asking for another column beside it is an error.

### How it works

Think of a teacher looking for the shortest student in a line. She walks down the line. She
remembers the shortest student she has seen so far. When someone shorter appears, that person
becomes the new "shortest so far". At the end of the line, she has the answer.

A query with `MIN(amount)` runs in these steps:

1. **`FROM`** collects the rows of the table.
2. **`WHERE`**, if there is one, keeps only the rows that pass a test.
3. **`MIN(amount)`** walks through the rows that are left. It remembers the smallest `amount`
   seen so far. A smaller value replaces it.
4. After the last row, the "smallest so far" is the answer. The result has **exactly one row**.

`MAX` works the same way. It remembers the largest value instead.

**Inside the brackets, you write a column name.** It tells `MIN` which column to look at.
`COUNT(*)` can use `*` because it only counts rows. It never looks at a value. `MIN` must compare
values, so it needs a column.

**What "smallest" means depends on the column's type:**

| Column type | "Smallest" means | Example from our data |
| --- | --- | --- |
| Number | The lowest number | 999 is smaller than 1499 |
| Timestamp (a date and time) | The earliest moment | 20 Nov is smaller than 28 Nov |
| Text | First in alphabetical order | "Classical Evening" is smaller than "Jazz at the Note" |

### Example — real run

All 6 tiers:

```text
SELECT name, amount FROM price_tier ORDER BY id;

    name     |  amount
-------------+-----------
 General     | 1499.0000
 VIP         | 4999.0000
 General     |  999.0000
 General     | 1999.0000
 Front Table | 3499.0000
 Balcony     | 2499.0000
(6 rows)
```

The amounts end in `.0000` because the column type is `numeric(19,4)`. That type always keeps 4
digits after the decimal point.

The cheapest and the dearest:

```text
SELECT MIN(amount) AS cheapest_tier FROM price_tier;

 cheapest_tier
---------------
      999.0000
(1 row)

SELECT MAX(amount) AS dearest_tier FROM price_tier;

 dearest_tier
--------------
    4999.0000
(1 row)
```

Several aggregate functions in one `SELECT`, still one row:

```text
SELECT MIN(amount) AS cheapest, MAX(amount) AS dearest, COUNT(*) AS tiers FROM price_tier;

 cheapest |  dearest  | tiers
----------+-----------+-------
 999.0000 | 4999.0000 |     6
(1 row)
```

On timestamps:

```text
SELECT MIN(starts_at) AS earliest_start FROM event;

     earliest_start
------------------------
 2026-11-20 13:30:00+00
(1 row)

SELECT MIN(starts_at) AS earliest_start, MAX(starts_at) AS latest_start FROM event;

     earliest_start     |      latest_start
------------------------+------------------------
 2026-11-20 13:30:00+00 | 2026-12-12 14:30:00+00
(1 row)
```

The `+00` means the time is shown in UTC. 13:30 UTC is 19:00 in India, because India is 5 hours
30 minutes ahead. It is the same moment, written two ways. To see it in India time:

```text
SELECT MIN(starts_at) AT TIME ZONE 'Asia/Kolkata' AS earliest_start_india FROM event;

 earliest_start_india
----------------------
 2026-11-20 19:00:00
(1 row)
```

On text:

```text
SELECT MIN(name) AS first_alphabetically, MAX(name) AS last_alphabetically FROM event;

 first_alphabetically | last_alphabetically
----------------------+---------------------
 Classical Evening    | Open Mic Night
(1 row)
```

### Walkthrough

How `MIN(amount)` finds 999. It walks the 6 tiers in `id` order, which is the order in this
table:

| Row | amount | Smallest so far | Why |
| --- | --- | --- | --- |
| 1 | 1499 | **1499** | It is the first row, so it is the smallest seen so far. |
| 2 | 4999 | 1499 | 4999 is bigger. No change. |
| 3 | 999 | **999** | 999 is smaller than 1499. It replaces it. |
| 4 | 1999 | 999 | 1999 is bigger. No change. |
| 5 | 3499 | 999 | Bigger. No change. |
| 6 | 2499 | 999 | Bigger. No change. |

After row 6, the smallest so far is 999. The result is one row holding `999.0000`.

### Edge case — no row passes the test

`NULL` means "no value". It is not 0. It is not empty text. psql normally prints `NULL` as a
blank, which is easy to miss. This command makes psql print it as `«NULL»`. It changes only how
results are shown:

```text
\pset null '«NULL»'
Null display is "«NULL»".
```

No tier costs more than 9000. What each aggregate function does with zero rows:

```text
SELECT COUNT(*) AS count_over_9000 FROM price_tier WHERE amount > 9000;

 count_over_9000
-----------------
               0
(1 row)

SELECT MIN(amount) AS cheapest_over_9000 FROM price_tier WHERE amount > 9000;

 cheapest_over_9000
--------------------
             «NULL»
(1 row)
```

Both return exactly one row. The values differ:

- `COUNT(*)` gives `0`. Counting zero rows has a real answer: there are 0 of them.
- `MIN` gives `NULL`. The smallest of zero values does not exist, so there is nothing to report.

If `MIN` gave 0 here, it would look as if a free ticket existed. `NULL` is the honest answer.

### `MIN` versus `ORDER BY ... LIMIT 1`

These two are easy to confuse. `ORDER BY amount` sorts the rows from smallest to largest.
`LIMIT 1` keeps only the first row.

```text
SELECT name, amount FROM price_tier ORDER BY amount LIMIT 1;

  name   |  amount
---------+----------
 General | 999.0000
(1 row)
```

| | `MIN(amount)` | `ORDER BY amount LIMIT 1` |
| --- | --- | --- |
| What it gives back | The value only | A whole row, so the name comes too |
| Can sit beside other aggregate functions in one row | Yes: `MIN`, `MAX` and `COUNT(*)` together | No |
| Can give the smallest **for each event** in one query | Yes, with grouping (taught later in this task) | No |

### `MIN` versus `LEAST`

`LEAST` is the SQL version of Java's `Math.min(a, b)`. It is a normal function: it works on the
values inside **one row**, so it gives one answer **per row**. `MIN` works **down a column** and
squeezes all the rows into one answer.

```text
SELECT name, amount, LEAST(amount, 2000) AS capped_at_2000 FROM price_tier ORDER BY id;

    name     |  amount   | capped_at_2000
-------------+-----------+----------------
 General     | 1499.0000 |      1499.0000
 VIP         | 4999.0000 |           2000
 General     |  999.0000 |       999.0000
 General     | 1999.0000 |      1999.0000
 Front Table | 3499.0000 |           2000
 Balcony     | 2499.0000 |           2000
(6 rows)
```

Six rows in, six rows out. The `2000` values show no decimals because they come from the plain
number `2000` that was typed.

| | `MIN(amount)` | `LEAST(amount, 2000)` |
| --- | --- | --- |
| Compares | Values **down a column**, across rows | Values **across one row** |
| Rows out, for 6 rows in | 1 | 6 |
| Kind of function | Aggregate function | Normal function |

### Important terms

| Term | Meaning |
| --- | --- |
| `MIN(column)` | The aggregate function that gives the smallest value in the column. |
| `MAX(column)` | The aggregate function that gives the largest value in the column. |
| Timestamp | A date plus a time of day, such as `2026-11-20 13:30:00+00`. |
| UTC | The world's reference time zone. `+00` at the end of a time means UTC. |
| `NULL` | "No value". Not 0, and not empty text. |
| `\pset null '«NULL»'` | A psql display setting. It prints `NULL` as `«NULL»` instead of a blank. |
| `LEAST(a, b)` | A normal function. Gives the smaller of two values in the same row. |
| `VALUES (...), (...)` | A quick way to make a few throwaway rows for one query. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Asking for the name beside `MIN`: `SELECT name, MIN(amount)` | An error, shown below. | `MIN` gives the value, not the row. For the whole row, use `ORDER BY amount LIMIT 1`. |
| Writing `MIN(*)` | An error, shown below. | Put a column inside the brackets: `MIN(amount)`. |
| Writing `MIN(amount, 2000)`, like Java's `Math.min(a, b)` | An error, shown below. | For the smaller of two values in the same row, use `LEAST(amount, 2000)`. |
| Using `MAX` for "earliest" | You get the latest time. | Earliest means the smallest timestamp, so use `MIN`. Latest uses `MAX`. |
| Expecting `0` when nothing matches | You get `NULL`. | Check for `NULL`. Only `COUNT` gives `0`. |
| Storing numbers in a text column | `MIN` and `MAX` give strange answers, shown below. | Store numbers in number columns. |

**Error 1: a plain column beside `MIN`.**

```text
SELECT name, MIN(amount) FROM price_tier;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT name, MIN(amount) FROM price_tier;
               ^
```

In plain words: the result has one row, but there are 6 names. The cheapest tier is called
`General`, but so are two others. The database will not guess which name you meant, so it
refuses.

**Error 2: `MIN(*)`.**

```text
SELECT MIN(*) FROM price_tier;

ERROR:  function min() does not exist
LINE 1: SELECT MIN(*) FROM price_tier;
               ^
HINT:  No function matches the given name and argument types. You might need to add explicit type casts.
```

| Part of the error | Plain meaning |
| --- | --- |
| `function min() does not exist` | Postgres read `MIN(*)` as `MIN` with nothing to look at. No such version exists. Only `COUNT` has the special `(*)` form. |
| `HINT: No function matches the given name and argument types` | A function called `min` exists, but not one that takes what you gave it. |
| `You might need to add explicit type casts` | A general suggestion. It does not apply here. The real fix is to name a column. |

**Error 3: `MIN` with two values, Java style.**

```text
SELECT MIN(amount, 1000) FROM price_tier;

ERROR:  function min(numeric, integer) does not exist
LINE 1: SELECT MIN(amount, 1000) FROM price_tier;
               ^
HINT:  No function matches the given name and argument types. You might need to add explicit type casts.
```

| Part of the error | Plain meaning |
| --- | --- |
| `min(numeric, integer)` | You gave `MIN` two things: a number with decimals (`amount`) and a whole number (`1000`). |
| `does not exist` | `MIN` takes exactly one thing: the column to walk down. |

**The text trap.** This builds a tiny throwaway table named `t`, with one column `x` and three
rows: `'10'`, `'9'` and `'100'`. The quotes make them text, not numbers.

```text
SELECT MIN(x) AS min_text, MAX(x) AS max_text FROM (VALUES ('10'), ('9'), ('100')) AS t(x);

 min_text | max_text
----------+----------
 10       | 9
(1 row)
```

The same values as real numbers, without quotes:

```text
SELECT MIN(x) AS min_number, MAX(x) AS max_number FROM (VALUES (10), (9), (100)) AS t(x);

 min_number | max_number
------------+------------
          9 |        100
(1 row)
```

Why the text version says the largest is `9`. Text is compared one character at a time, like
words in a dictionary:

1. Compare the first characters: `'1'`, `'9'`, `'1'`.
2. The character `'1'` comes before `'9'`. So `'10'` and `'100'` both come before `'9'`.
3. Between `'10'` and `'100'`, the first two characters match. `'10'` runs out first, so it
   comes first. This is why "car" comes before "cart" in a dictionary.

So in text order, `'10'` is the smallest and `'9'` is the largest.

### Where it is used

- "Tickets from ₹999" on an event card: `MIN` of the tier amounts.
- "Next event: 20 Nov": `MIN` of the start times that are still in the future.
- "Last seen 5 minutes ago": `MAX` of a user's login times.
- A price filter slider: its two ends come from `MIN` and `MAX` in one query.
- Debugging: "what is the oldest row in this table?"

### Link to the task

This is two parts of **S1**:

- **Cheapest tier:** `SELECT MIN(amount) AS cheapest_tier FROM price_tier;` returns one row,
  holding `999.0000`.
- **Earliest start time:** `SELECT MIN(starts_at) AS earliest_start FROM event;` returns one row,
  holding `2026-11-20 13:30:00+00`.

`MIN` comes back in **S6**, which needs "the cheapest tier **of each event**". That is `MIN`
combined with grouping.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — look at the tiers.**

```sql
SELECT name, amount FROM price_tier ORDER BY id;
```

Expected: 6 rows, from `General | 1499.0000` to `Balcony | 2499.0000`.

**Step 3 — cheapest and dearest.**

```sql
SELECT MIN(amount) AS cheapest_tier FROM price_tier;
SELECT MAX(amount) AS dearest_tier FROM price_tier;
```

Expected: `999.0000`, then `4999.0000`. Each is `(1 row)`.

**Step 4 — earliest start time.**

```sql
SELECT MIN(starts_at) AS earliest_start FROM event;
```

Expected: `2026-11-20 13:30:00+00`, in `(1 row)`.

**Step 5 — three aggregate functions in one row.**

```sql
SELECT MIN(amount) AS cheapest, MAX(amount) AS dearest, COUNT(*) AS tiers FROM price_tier;
```

Expected: `999.0000 | 4999.0000 | 6`, in `(1 row)`.

**Step 6 — zero rows: `COUNT` against `MIN`.**

```sql
SELECT COUNT(*) AS count_over_9000 FROM price_tier WHERE amount > 9000;
SELECT MIN(amount) AS cheapest_over_9000 FROM price_tier WHERE amount > 9000;
```

Expected: `0`, then `«NULL»`. Both are `(1 row)`.

**Step 7 — trigger two errors on purpose.**

```sql
SELECT MIN(*) FROM price_tier;
SELECT name, MIN(amount) FROM price_tier;
```

Expected failures:

```text
ERROR:  function min() does not exist
ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
```

Leave the prompt with `\q`.

**Done when:** you have both S1 values, `999.0000` and `2026-11-20 13:30:00+00`, each from a
one-row result. You can also say why Step 6 gives `0` for `COUNT` but `«NULL»` for `MIN`.

### Summary for revision

- `MIN(column)` gives the smallest value. `MAX(column)` gives the largest.
- "Smallest" follows the type: lowest number, earliest timestamp, first alphabetically.
- A column name goes inside the brackets. `MIN(*)` is an error.
- Many aggregate functions can share one `SELECT`. Still one row.
- Zero rows: `COUNT` gives `0`; `MIN` and `MAX` give `NULL`, because the smallest of nothing
  does not exist.
- `MIN` gives the value, not the row. For the whole row, use `ORDER BY ... LIMIT 1`.
- `MIN` works down a column. `LEAST(a, b)` works across one row.
- Numbers stored as text compare like words: `'10'` is smaller than `'9'`.

### Check questions, with answers

**Question 1.** Here is a table called `flight`:

```text
 id |  city   |     departs_at      | price
----+---------+---------------------+-------
  1 | Delhi   | 2026-08-03 09:15:00 |  5200
  2 | Goa     | 2026-08-01 18:40:00 |  3100
  3 | Kolkata | 2026-08-02 06:05:00 |  4700
```

Someone runs this query:

```sql
SELECT MIN(departs_at) AS first_departure, MAX(price) AS dearest, COUNT(*) AS flights FROM flight;
```

(a) How many rows does the result have? (b) What value is in each of the three columns? Be
careful with `first_departure`: one flight leaves at 06:05 in the morning.

**Answer.** (a) One row. Three aggregate functions share one `SELECT`, and with no grouping the
result is always one row. (b) `first_departure` is `2026-08-01 18:40:00`, the Goa flight.
`dearest` is `5200`, the Delhi flight. `flights` is `3`.

The trap is the 06:05 flight. Its clock time is the earliest, but it leaves on 2 August. A
timestamp compares the date first, and only then the time. 1 August comes before 2 August, so
Goa's 18:40 on 1 August is the earliest moment of the three. Real run, in a throwaway table with
the same three rows:

```text
   first_departure   | dearest | flights
---------------------+---------+---------
 2026-08-01 18:40:00 |    5200 |       3
(1 row)
```

**Question 2.** Here is the same `flight` table again:

```text
 id |  city   |     departs_at      | price
----+---------+---------------------+-------
  1 | Delhi   | 2026-08-03 09:15:00 |  5200
  2 | Goa     | 2026-08-01 18:40:00 |  3100
  3 | Kolkata | 2026-08-02 06:05:00 |  4700
```

(a) What does each of these two queries return?

```sql
SELECT COUNT(*) AS n FROM flight WHERE price > 9000;
SELECT MIN(price) AS cheapest FROM flight WHERE price > 9000;
```

(b) Why do the two answers differ?

(c) A teammate wants the city of the cheapest flight, so they write
`SELECT city, MIN(price) FROM flight;`. What happens, and why?

**Answer.** (a) The first returns one row holding `0`. The second returns one row holding
`NULL`. In both, `WHERE price > 9000` throws away all three flights, so zero rows reach the
aggregate function.

```text
 n
---
 0
(1 row)

 cheapest
----------
   «NULL»
(1 row)
```

(b) Counting zero rows has a real answer: there are 0 of them. The smallest price among zero
flights does not exist. There is no price to report, so `MIN` reports "no value", which is
`NULL`. Giving `0` would wrongly suggest a free flight exists.

(c) It fails:

```text
ERROR:  column "flight.city" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT city, MIN(price) FROM flight;
               ^
```

`MIN(price)` with no grouping makes one row. There are three cities. The database has one slot
beside the minimum and three candidates for it, and it will not guess. `MIN` gives the value,
not the row it came from. To get the city, sort and keep the first row:

```text
SELECT city, price FROM flight ORDER BY price LIMIT 1;

 city | price
------+-------
 Goa  |  3100
(1 row)
```

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `score`:

```text
 id | player | points
----+--------+--------
  1 | Asha   |     40
  2 | Ravi   |     75
  3 | Meena  |     62
```

What does this query return?

```sql
SELECT MIN(points) AS lowest, MAX(player) AS last_alphabetically FROM score;
```

<details>
<summary>Answer</summary>

One row: `40 | Ravi`. `MIN(points)` is the lowest number, 40. `MAX(player)` works on text, so it
gives the name that comes last in alphabetical order: Asha, Meena, Ravi. The two values come
from different rows. That is fine: each aggregate function looks at its own column.

```text
 lowest | last_alphabetically
--------+---------------------
     40 | Ravi
(1 row)
```

</details>

**Practice 2.** Here is a table called `product`:

```text
 id | price
----+-------
  1 |   300
  2 |   800
  3 |   500
```

For every product, the page must show its price, but never more than 500. Which of these two
queries does that, and what does the other one do?

```sql
SELECT MIN(price, 500) FROM product;
SELECT id, price, LEAST(price, 500) AS capped FROM product ORDER BY id;
```

<details>
<summary>Answer</summary>

The second one. `LEAST(price, 500)` compares two values inside each row, so it gives one answer
per row:

```text
 id | price | capped
----+-------+--------
  1 |   300 |    300
  2 |   800 |    500
  3 |   500 |    500
(3 rows)
```

The first one fails. `MIN` takes exactly one column and works down it, across rows:

```text
ERROR:  function min(integer, integer) does not exist
LINE 1: SELECT MIN(price, 500) FROM product;
               ^
```

</details>

**Practice 3.** A table called `version_tag` has one text column, `tag`. Its three rows hold
`'2'`, `'11'` and `'3'`. What does this query return?

```sql
SELECT MIN(tag) AS min_tag, MAX(tag) AS max_tag FROM version_tag;
```

<details>
<summary>Answer</summary>

One row: `11 | 3`. The values are text, so they compare one character at a time, like words in a
dictionary. The first characters are `'2'`, `'1'` and `'3'`. `'1'` comes first, so `'11'` is the
smallest. `'3'` comes last, so `'3'` is the largest. As numbers, the answer would have been 2 and
11. Store numbers in number columns.

```text
 min_tag | max_tag
---------+---------
 11      | 3
(1 row)
```

</details>

### Your doubts

None for this lesson.

---

## Lesson 3 — `SUM` and `AVG`

### Definition

An **aggregate function** takes many rows and gives back one value. `SUM` is an aggregate
function that **adds up** all the values in a column. `AVG` is an aggregate function that gives
back the **average**. The average is the total divided by how many values there are.

### Why it is needed

A manager asks two questions. "What do all our price tiers add up to?" "What does a tier cost on
average?"

Without an aggregate function, Java asks for every tier. The database sends every tier. Java
loops and adds each amount to a running total. For the average, Java also counts the rows, then
divides. Every row travels across, just to produce two numbers.

`SUM` and `AVG` do this work inside the database. Only the answers travel.

### Key points

- `SUM(column)` adds up the values. `AVG(column)` gives total ÷ count.
- Both need a **number** column. Text and timestamps are errors.
- A column name goes inside the brackets. `SUM(*)` is an error.
- With no grouping, the result is always **exactly one row**.
- Zero rows in: `SUM` and `AVG` give `NULL`, not 0. `COALESCE(SUM(x), 0)` turns that into 0.
- `AVG` gives many decimal places. `ROUND(AVG(x), 2)` keeps 2.
- `SUM(x) / COUNT(*)` on a whole-number column chops off the decimals. Use `AVG`.

### How it works

Think of a shop till. The cashier scans items one by one. The till adds each price to a running
total. At the end, it shows one number: the bill. To get the average price per item, you also
count the items. Then you divide the bill by that count.

A query with `SUM(amount)` runs in these steps:

1. **`FROM`** collects the rows of the table.
2. **`WHERE`**, if there is one, keeps only the rows that pass a test.
3. **`SUM(amount)`** walks through the rows that are left. It keeps a running total. It adds each
   row's `amount` to it.
4. After the last row, the running total is the answer. The result has **exactly one row**.

`AVG(amount)` walks the rows the same way, but it keeps **two** things: a running total and a
running count. After the last row, it divides the total by the count.

Two rules about what goes inside the brackets:

- **A column name.** `SUM(*)` is an error. Only `COUNT` has the special `(*)` form.
- **A number column.** Adding up names or dates has no meaning, so Postgres refuses.

### Example — real run

All 6 tiers:

```text
SELECT name, amount FROM price_tier ORDER BY id;

    name     |  amount
-------------+-----------
 General     | 1499.0000
 VIP         | 4999.0000
 General     |  999.0000
 General     | 1999.0000
 Front Table | 3499.0000
 Balcony     | 2499.0000
(6 rows)
```

The total and the average:

```text
SELECT SUM(amount) AS total_tier_value FROM price_tier;

 total_tier_value
------------------
       15494.0000
(1 row)

SELECT AVG(amount) AS average_tier_price FROM price_tier;

  average_tier_price
-----------------------
 2582.3333333333333333
(1 row)
```

The 3 in the average repeats forever. Postgres stops after a fixed number of decimal places. That
is far too many decimals for a screen.

**`ROUND` fixes it.** `ROUND(value, 2)` rounds a number to 2 digits after the decimal point:

```text
SELECT ROUND(AVG(amount), 2) AS average_tier_price FROM price_tier;

 average_tier_price
--------------------
            2582.33
(1 row)
```

`ROUND` is a **normal function**, not an aggregate function. A normal function takes one value
and gives back one value. Here, `AVG` first squeezes the 6 rows into one value. Then `ROUND`
rounds that one value.

Several aggregate functions in one `SELECT`, still one row:

```text
SELECT SUM(amount) AS total, COUNT(*) AS tiers, AVG(amount) AS average FROM price_tier;

   total    | tiers |        average
------------+-------+-----------------------
 15494.0000 |     6 | 2582.3333333333333333
(1 row)
```

**`SUM` with `WHERE`.** The event Monsoon Rock Night has the id
`aaaaaaaa-0000-0000-0000-000000000001`. Its three tiers are General 1499, VIP 4999 and Balcony
2499. This adds up only its tiers:

```text
SELECT SUM(amount) AS monsoon_total
FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001';

 monsoon_total
---------------
     8997.0000
(1 row)
```

Check by hand: 1499 + 4999 + 2499 = 8997.

### Walkthrough

How `SUM(amount)` reaches 15494, walking the 6 tiers in `id` order:

| Row | amount | Running total | Why |
| --- | --- | --- | --- |
| 1 | 1499 | 1499 | Start at 0, add 1499. |
| 2 | 4999 | 6498 | 1499 + 4999 |
| 3 | 999 | 7497 | 6498 + 999 |
| 4 | 1999 | 9496 | 7497 + 1999 |
| 5 | 3499 | 12995 | 9496 + 3499 |
| 6 | 2499 | **15494** | 12995 + 2499 |

`AVG(amount)` builds the same running total, 15494. It also counts the rows: 6. At the end, it
divides: 15494 ÷ 6 = 2582.333...

### Edge case 1 — zero rows give `NULL`, not 0

`NULL` means "no value". It is not 0. psql normally prints `NULL` as a blank. This display
setting prints it as `«NULL»` instead:

```text
\pset null '«NULL»'
Null display is "«NULL»".
```

No tier costs more than 9000. `COUNT`, `SUM` and `AVG` with zero rows:

```text
SELECT COUNT(*) AS count_over_9000 FROM price_tier WHERE amount > 9000;

 count_over_9000
-----------------
               0
(1 row)

SELECT SUM(amount) AS total_over_9000, AVG(amount) AS avg_over_9000
FROM price_tier WHERE amount > 9000;

 total_over_9000 | avg_over_9000
-----------------+---------------
          «NULL» |        «NULL»
(1 row)
```

All three come back in exactly one row. Only `COUNT` gives `0`.

- `AVG` gives `NULL` because it would have to divide by a count of 0. That is impossible, so
  there is no value.
- `SUM` gives `NULL` because that is SQL's rule: no rows, no value. This surprises many people.
  In school maths, adding up nothing gives 0.

Why it matters: Java code asks for a number and gets `null`. If it does maths with that `null`,
it can crash.

**The fix is `COALESCE`.** `COALESCE(x, 0)` means: "give back `x`, but if `x` is `NULL`, give back
0 instead".

```text
SELECT COALESCE(SUM(amount), 0) AS total_over_9000 FROM price_tier WHERE amount > 9000;

 total_over_9000
-----------------
               0
(1 row)
```

### Edge case 2 — the whole-number division trap

`capacity` holds whole numbers:

```text
SELECT name, capacity FROM venue ORDER BY capacity;

      name       | capacity
-----------------+----------
 The Blue Note   |      350
 Lakeside Hall   |      800
 Riverside Arena |    12000
(3 rows)

SELECT SUM(capacity) AS total_capacity, AVG(capacity) AS avg_capacity FROM venue;

 total_capacity |     avg_capacity
----------------+-----------------------
          13150 | 4383.3333333333333333
(1 row)
```

`AVG` keeps the decimals, even though every capacity is a whole number. Now the average worked out
by hand, as total divided by count:

```text
SELECT SUM(capacity) / COUNT(*) AS by_hand_avg, AVG(capacity) AS real_avg FROM venue;

 by_hand_avg |       real_avg
-------------+-----------------------
        4383 | 4383.3333333333333333
(1 row)
```

The `.3333` is gone. Why, step by step:

1. `SUM` of a whole-number column gives a whole number: 13150.
2. `COUNT(*)` always gives a whole number: 3.
3. In Postgres, a whole number divided by a whole number gives a whole number. The decimals are
   chopped off.

Java does exactly the same with two `int` values. Real run, Java 21:

```java
public class IntDivision {
    public static void main(String[] args) {
        int totalCapacity = 13150;
        int venues = 3;
        System.out.println(totalCapacity / venues);
        System.out.println((double) totalCapacity / venues);
    }
}
```

```text
4383
4383.333333333333
```

The trap only bites on whole-number columns. `amount` already has decimals, so
`SUM(amount) / COUNT(*)` keeps them:

```text
SELECT SUM(amount) / COUNT(*) AS by_hand, AVG(amount) AS real_avg FROM price_tier;

        by_hand        |       real_avg
-----------------------+-----------------------
 2582.3333333333333333 | 2582.3333333333333333
(1 row)
```

Either way, the simple rule is: **for an average, use `AVG`.**

### Important terms

| Term | Meaning |
| --- | --- |
| `SUM(column)` | The aggregate function that adds up all the values in the column. |
| `AVG(column)` | The aggregate function that gives the average: total ÷ count. |
| Normal function | A function that takes one value and gives back one value, such as `ROUND`. |
| `ROUND(value, 2)` | Rounds a number to 2 digits after the decimal point. |
| `COALESCE(x, 0)` | Gives back `x`, or 0 if `x` is `NULL`. |
| Whole-number division | A whole number divided by a whole number. The decimals are chopped off: 13150 / 3 = 4383. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Adding up text: `SUM(name)` | An error, shown below. | `SUM` and `AVG` only work on number columns. |
| Averaging timestamps: `AVG(starts_at)` | An error, shown below. | For the earliest or latest time, use `MIN` or `MAX`. They do work on timestamps. |
| Writing `SUM(*)` | An error, shown below. | Put a column inside the brackets. |
| Expecting 0 when no rows match | You get `NULL`. | Write `COALESCE(SUM(amount), 0)`. |
| Showing `AVG` exactly as it comes | `2582.3333333333333333` on the screen. | Write `ROUND(AVG(amount), 2)`. |
| Working out an average as `SUM(...) / COUNT(*)` on whole numbers | Decimals chopped off: 4383 instead of 4383.33. | Use `AVG`. |

**Error 1: adding up text.**

```text
SELECT SUM(name) FROM event;

ERROR:  function sum(text) does not exist
LINE 1: SELECT SUM(name) FROM event;
               ^
HINT:  No function matches the given name and argument types. You might need to add explicit type casts.
```

| Part of the error | Plain meaning |
| --- | --- |
| `sum(text)` | You asked `SUM` to add up a text column. |
| `does not exist` | Postgres has versions of `SUM` for numbers only. None of them takes text. |
| `You might need to add explicit type casts` | A general suggestion. It does not help here, because names cannot be added. |

**Error 2: averaging timestamps.**

```text
SELECT AVG(starts_at) FROM event;

ERROR:  function avg(timestamp with time zone) does not exist
LINE 1: SELECT AVG(starts_at) FROM event;
               ^
HINT:  No function matches the given name and argument types. You might need to add explicit type casts.
```

| Part of the error | Plain meaning |
| --- | --- |
| `avg(timestamp with time zone)` | You asked `AVG` to average a column of timestamps. |
| `does not exist` | There is no version of `AVG` for timestamps. |

**Error 3: `SUM(*)`.**

```text
SELECT SUM(*) FROM price_tier;

ERROR:  function sum() does not exist
LINE 1: SELECT SUM(*) FROM price_tier;
               ^
HINT:  No function matches the given name and argument types. You might need to add explicit type casts.
```

Postgres read `SUM(*)` as `SUM` with nothing to add up. No such version exists.

### Where it is used

- A cart total: `SUM` of the item prices.
- A revenue report: `SUM` of order amounts. Real revenue needs an orders table, which arrives in a
  later phase.
- A star rating: "4.3 ★" is `ROUND(AVG(stars), 1)`.
- "Average order value" on a business dashboard: `AVG`.
- Total seats across all venues: `SUM(capacity)`.

### Link to the task

This is the last part of **S1**: "average tier price".

```sql
SELECT AVG(amount) AS average_tier_price FROM price_tier;
```

It returns one row, holding `2582.3333333333333333`. Rounded with `ROUND(..., 2)`, that is
`2582.33`.

`SUM` comes back twice later:

- **S5** adds up `capacity` across a three-table join and gets a wrong total.
- **S7** asks for "total tier value per event". The Monsoon Rock Night total, `8997.0000`, is that
  answer for one event. Grouping does it for every event at once.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — total and average.**

```sql
SELECT SUM(amount) AS total_tier_value FROM price_tier;
SELECT AVG(amount) AS average_tier_price FROM price_tier;
SELECT ROUND(AVG(amount), 2) AS average_tier_price FROM price_tier;
```

Expected: `15494.0000`, then `2582.3333333333333333`, then `2582.33`. Each is `(1 row)`.

**Step 3 — the total for one event.**

```sql
SELECT SUM(amount) AS monsoon_total
FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001';
```

Expected: `8997.0000`.

**Step 4 — zero rows, then the fix.**

```sql
SELECT SUM(amount) AS total_over_9000, AVG(amount) AS avg_over_9000
FROM price_tier WHERE amount > 9000;
SELECT COALESCE(SUM(amount), 0) AS total_over_9000 FROM price_tier WHERE amount > 9000;
```

Expected: `«NULL» | «NULL»`, then `0`.

**Step 5 — the whole-number division trap.**

```sql
SELECT SUM(capacity) / COUNT(*) AS by_hand_avg, AVG(capacity) AS real_avg FROM venue;
```

Expected: `4383 | 4383.3333333333333333`.

**Step 6 — trigger an error on purpose.**

```sql
SELECT SUM(name) FROM event;
```

Expected failure: `ERROR:  function sum(text) does not exist`

Leave the prompt with `\q`.

**Done when:** Step 2 gives the S1 value `2582.33` in one row. You can also say why Step 4 gives
`«NULL»` and not `0`, and why the two numbers in Step 5 differ.

### Summary for revision

- `SUM(column)` adds up. `AVG(column)` gives total ÷ count. Both need a number column.
- With no grouping, the result is always exactly one row.
- Zero rows: `COUNT` gives `0`; `SUM` and `AVG` give `NULL`. Wrap in `COALESCE(..., 0)` when the
  screen needs 0.
- `ROUND(AVG(x), 2)` for display. `ROUND` is a normal function: one value in, one value out.
- `SUM(x) / COUNT(*)` on whole numbers chops the decimals, just like Java's `int` division. Use
  `AVG`.
- `SUM(text)`, `AVG(timestamp)` and `SUM(*)` are all errors.

### Check questions, with answers

**Question 1.** Here is a table called `cart_item`. The `price` column holds whole numbers:

```text
 id |   item   | price
----+----------+-------
  1 | Pen      |    20
  2 | Notebook |    55
  3 | Bag      |   450
  4 | Pencil   |    10
```

Someone runs this query:

```sql
SELECT SUM(price) AS total, AVG(price) AS average, COUNT(*) AS items FROM cart_item;
```

(a) How many rows does the result have? (b) What value is in each column? (c) A teammate works
out the average another way, with `SELECT SUM(price) / COUNT(*) FROM cart_item;`. What does that
return, and why is it different from `AVG`?

**Answer.** (a) One row. Three aggregate functions share one `SELECT`, and with no grouping the
result is always one row. (b) `total` is 535, because 20 + 55 + 450 + 10 = 535. `average` is
133.75, because 535 ÷ 4 = 133.75. psql prints it as `133.7500000000000000`. `items` is 4. Real
run, in a throwaway table with the same four rows:

```text
 total |       average        | items
-------+----------------------+-------
   535 | 133.7500000000000000 |     4
(1 row)
```

(c) It returns `133`:

```text
 by_hand
---------
     133
(1 row)
```

`price` holds whole numbers, so `SUM(price)` is the whole number 535. `COUNT(*)` is always a whole
number, here 4. A whole number divided by a whole number gives a whole number, so the `.75` is
chopped off. Java does the same with `535 / 4` on two `int` values. `AVG` keeps the decimals. For
an average, use `AVG`.

**Question 2.** Here is the same `cart_item` table again:

```text
 id |   item   | price
----+----------+-------
  1 | Pen      |    20
  2 | Notebook |    55
  3 | Bag      |   450
  4 | Pencil   |    10
```

(a) What does this query return?

```sql
SELECT SUM(price) AS total FROM cart_item WHERE price > 1000;
```

(b) The web page must show "Total: 0" in this case. Rewrite the query so it gives `0`.

**Answer.** (a) One row, holding `NULL`. `WHERE price > 1000` throws away all four items, so zero
rows reach `SUM`. SQL's rule is: no rows, no value. So `SUM` gives `NULL`, not 0.

```text
 total
--------
 «NULL»
(1 row)
```

(b) Wrap the `SUM` in `COALESCE`, which gives back 0 when its first value is `NULL`:

```sql
SELECT COALESCE(SUM(price), 0) AS total FROM cart_item WHERE price > 1000;
```

```text
 total
-------
     0
(1 row)
```

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `review`. The `stars` column holds whole numbers:

```text
 id | stars
----+-------
  1 |     5
  2 |     4
  3 |     4
  4 |     2
```

The page shows a rating with one decimal place. What does this query return?

```sql
SELECT AVG(stars) AS raw_average, ROUND(AVG(stars), 1) AS rating FROM review;
```

<details>
<summary>Answer</summary>

One row: `3.7500000000000000 | 3.8`. The total is 5 + 4 + 4 + 2 = 15. There are 4 reviews, so the
average is 15 ÷ 4 = 3.75. `AVG` keeps the decimals, even on whole numbers. `ROUND(..., 1)` keeps
one decimal place, and 3.75 rounds up to 3.8.

```text
    raw_average     | rating
--------------------+--------
 3.7500000000000000 |    3.8
(1 row)
```

</details>

**Practice 2.** Here is a table called `book`:

```text
 id | title                | pages
----+----------------------+-------
  1 | A Wizard of Earthsea |   183
  2 | Mort                 |   272
  3 | Guards! Guards!      |   288
```

Which of these four queries work, and what does each working one return?

```sql
SELECT SUM(pages) FROM book;
SELECT AVG(pages) FROM book;
SELECT SUM(title) FROM book;
SELECT MIN(title) FROM book;
```

<details>
<summary>Answer</summary>

Three work. One fails.

- `SUM(pages)` gives `743`, because 183 + 272 + 288 = 743.
- `AVG(pages)` gives `247.6666666666666667`, because 743 ÷ 3 = 247.666...
- `SUM(title)` fails with `ERROR: function sum(text) does not exist`. Text cannot be added up.
- `MIN(title)` gives `A Wizard of Earthsea`. `MIN` does work on text: it gives the first title in
  alphabetical order.

The lesson: `SUM` and `AVG` need numbers. `MIN` and `MAX` work on numbers, text and timestamps.

</details>

**Practice 3.** Here is a table called `expense`:

```text
 id | amount
----+--------
  1 |    100
  2 |    250
  3 |     50
```

What does this query return?

```sql
SELECT ROUND(AVG(amount), 2) AS average_over_1000 FROM expense WHERE amount > 1000;
```

<details>
<summary>Answer</summary>

One row, holding `NULL`. `WHERE amount > 1000` throws away all three rows. `AVG` of zero rows is
`NULL`. Then `ROUND` gets `NULL` as its value, and rounding "no value" still gives "no value".

```text
 average_over_1000
-------------------
            «NULL»
(1 row)
```

</details>

### Your doubts

None for this lesson.

---

## S1 — end to end: four aggregates, no grouping

**The subtask.** "Run each aggregate once with no grouping: total events, cheapest tier, average
tier price, earliest start time. Note that each returns exactly one row."

**Concepts used.** `COUNT(*)`, `MIN`, `AVG`, `ROUND`, and the rule that an aggregate function
with no grouping always returns exactly one row.

### How the concepts fit together

Each question needs two choices: which **table** holds the data, and which **aggregate function**
answers it.

| Question | Table that holds the data | Aggregate function | Why that one |
| --- | --- | --- | --- |
| Total events | `event` | `COUNT(*)` | It counts rows. One row in `event` is one event. |
| Cheapest tier | `price_tier` | `MIN(amount)` | The smallest number. |
| Average tier price | `price_tier` | `AVG(amount)` | Total ÷ count. |
| Earliest start time | `event` | `MIN(starts_at)` | For timestamps, the smallest value is the earliest moment. |

### The approach in plain words

For each question:

1. Find the table that holds the column.
2. Pick the aggregate function.
3. Name the result column with `AS`, so it reads clearly.
4. Run it. Check that psql's footer says `(1 row)`.

### The solution — real run

One query per question:

```text
SELECT COUNT(*) AS total_events FROM event;

 total_events
--------------
            4
(1 row)

SELECT MIN(amount) AS cheapest_tier FROM price_tier;

 cheapest_tier
---------------
      999.0000
(1 row)

SELECT AVG(amount) AS average_tier_price FROM price_tier;

  average_tier_price
-----------------------
 2582.3333333333333333
(1 row)

SELECT MIN(starts_at) AS earliest_start FROM event;

     earliest_start
------------------------
 2026-11-20 13:30:00+00
(1 row)
```

**The tidier version.** Aggregate functions can share one `SELECT` when they read the same table.
So the four questions fit into two queries, one per table:

```text
SELECT COUNT(*) AS total_events, MIN(starts_at) AS earliest_start FROM event;

 total_events |     earliest_start
--------------+------------------------
            4 | 2026-11-20 13:30:00+00
(1 row)

SELECT MIN(amount) AS cheapest_tier, ROUND(AVG(amount), 2) AS average_tier_price FROM price_tier;

 cheapest_tier | average_tier_price
---------------+--------------------
      999.0000 |            2582.33
(1 row)
```

| S1 question | Answer |
| --- | --- |
| Total events | `4` |
| Cheapest tier | `999.0000` |
| Average tier price | `2582.3333333333333333`, shown as `2582.33` |
| Earliest start time | `2026-11-20 13:30:00+00` (19:00 in India) |

### Key decisions and trade-offs

**Decision 1: the database does the work, not Java.** Each query sends back one row. If Java
fetched every row and looped, every row would travel just to produce four numbers.

**Decision 2: the table in `FROM` decides what gets counted.** `COUNT(*)` counts the rows of the
table you name. `COUNT(*) FROM event` counts events, which gives 4. `COUNT(*) FROM price_tier`
would count tiers instead, which gives 6. Both run without error. Only one answers the question.

**Decision 3: two queries, not one.** Fewer queries means fewer trips to the database. But an
aggregate function can only read columns from the tables named in `FROM`:

```text
SELECT COUNT(*) AS total_events, MIN(amount) AS cheapest_tier FROM event;

ERROR:  column "amount" does not exist
LINE 1: SELECT COUNT(*) AS total_events, MIN(amount) AS cheapest_tie...
                                             ^
```

`amount` lives on `price_tier`. The query names only `event`, so Postgres cannot find `amount`.

Joining the two tables makes one query see both. A join pairs each event with each of its tiers.
It runs, and it looks right:

```text
SELECT COUNT(*) AS total_events,
       MIN(pt.amount) AS cheapest_tier,
       ROUND(AVG(pt.amount), 2) AS average_tier_price,
       MIN(e.starts_at) AS earliest_start
FROM event e
JOIN price_tier pt ON pt.event_id = e.id;

 total_events | cheapest_tier | average_tier_price |     earliest_start
--------------+---------------+--------------------+------------------------
            6 |      999.0000 |            2582.33 | 2026-11-20 13:30:00+00
(1 row)
```

But `total_events` says **6**. There are only 4 events. The joined rows show why:

```text
SELECT e.name AS event, pt.name AS tier
FROM event e JOIN price_tier pt ON pt.event_id = e.id
ORDER BY e.name, pt.name;

       event        |    tier
--------------------+-------------
 Classical Evening  | General
 Jazz at the Note   | Front Table
 Jazz at the Note   | General
 Monsoon Rock Night | Balcony
 Monsoon Rock Night | General
 Monsoon Rock Night | VIP
(6 rows)
```

`COUNT(*)` counted these 6 joined rows, not events. Two things went wrong:

1. Monsoon Rock Night has 3 tiers, so it sits in 3 rows. It was counted 3 times.
2. Open Mic Night has no tiers. The join found no partner for it, so it vanished. It was counted
   0 times.

There was no error. A plausible wrong number like this is the dangerous kind. The other three
columns happen to be right, because each tier still appears exactly once.

Getting all four answers correctly in one query needs sub-queries, taught later in this task. For
now, two queries is the honest answer.

**Decision 4: round only for display.** `AVG` gives `2582.3333333333333333`. A screen should show
`2582.33`. But if the number feeds more maths, keep the full value and round at the very end.
Rounding early adds small errors, and they pile up.

**Decision 5: "total events" includes the cancelled one.** `COUNT(*)` counts every row in
`event`. Classical Evening is cancelled, and it still counts:

```text
SELECT name, cancelled_at FROM event ORDER BY starts_at;

        name        |         cancelled_at
--------------------+-------------------------------
 Monsoon Rock Night | «NULL»
 Jazz at the Note   | «NULL»
 Classical Evening  | 2026-10-24 18:05:00.690845+00
 Open Mic Night     | «NULL»
(4 rows)
```

`«NULL»` is how psql shows `NULL` ("no value") after `\pset null '«NULL»'`. Here it means "not
cancelled". To count only live events, keep the rows where `cancelled_at` has no value. The test
for "no value" is `IS NULL`. Writing `= NULL` never matches anything.

```text
SELECT COUNT(*) AS live_events FROM event WHERE cancelled_at IS NULL;

 live_events
-------------
           3
(1 row)
```

The subtask says "total events", so the answer is 4. In a real product, ask the person who wants
the number which one they mean.

**Decision 6: keep times in UTC.** The database shows `2026-11-20 13:30:00+00`, and `+00` means
UTC. That is 19:00 in India. Store and compare in UTC. Convert to local time only when showing it
to a person.

### Tests

**Normal cases.** The four queries in "The solution — real run": `4`, `999.0000`,
`2582.3333333333333333` and `2026-11-20 13:30:00+00`, each in one row.

**Edge case: zero rows.** No event starts after 2030, and no tier costs more than 9000:

```text
SELECT COUNT(*) AS total_events, MIN(starts_at) AS earliest_start
FROM event WHERE starts_at > '2030-01-01';

 total_events | earliest_start
--------------+----------------
            0 | «NULL»
(1 row)

SELECT MIN(amount) AS cheapest_tier, ROUND(AVG(amount), 2) AS average_tier_price
FROM price_tier WHERE amount > 9000;

 cheapest_tier | average_tier_price
---------------+--------------------
        «NULL» |             «NULL»
(1 row)
```

Still one row each. `COUNT` gives `0`. `MIN` and `AVG` give `NULL`, because the smallest or the
average of nothing does not exist.

**Edge case: exactly one row.** Classical Evening (`aaaaaaaa-0000-0000-0000-000000000002`) has one
tier:

```text
SELECT MIN(amount) AS cheapest, MAX(amount) AS dearest, AVG(amount) AS average, COUNT(*) AS tiers
FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002';

 cheapest | dearest  |       average        | tiers
----------+----------+----------------------+-------
 999.0000 | 999.0000 | 999.0000000000000000 |     1
(1 row)
```

With one value, the smallest, the largest and the average are all the same value.

**Failure cases.**

| What was tried | What happened |
| --- | --- |
| A plain column beside an aggregate: `SELECT name, COUNT(*) FROM event;` | `ERROR: column "event.name" must appear in the GROUP BY clause or be used in an aggregate function` |
| A column from a table not in `FROM`: `MIN(amount) ... FROM event` | `ERROR: column "amount" does not exist` |
| Joining to get one query | No error. `total_events` silently became 6. |

### Why it works

With no grouping, all the rows that survive `WHERE` form **one pile**. Each aggregate function
squeezes that pile into one value. One pile gives one row.

That is why every query returns exactly one row, even when the pile is empty. Then `COUNT`
reports `0`, and `MIN`, `MAX`, `SUM` and `AVG` report `NULL`.

Grouping changes exactly this. It splits the rows into **several** piles, and the result has one
row per pile.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — the four S1 queries.**

```sql
SELECT COUNT(*) AS total_events FROM event;
SELECT MIN(amount) AS cheapest_tier FROM price_tier;
SELECT AVG(amount) AS average_tier_price FROM price_tier;
SELECT MIN(starts_at) AS earliest_start FROM event;
```

Expected: `4`, `999.0000`, `2582.3333333333333333`, `2026-11-20 13:30:00+00`. Each footer says
`(1 row)`.

**Step 3 — the tidier two-query version.**

```sql
SELECT COUNT(*) AS total_events, MIN(starts_at) AS earliest_start FROM event;
SELECT MIN(amount) AS cheapest_tier, ROUND(AVG(amount), 2) AS average_tier_price FROM price_tier;
```

Expected: `4 | 2026-11-20 13:30:00+00`, then `999.0000 | 2582.33`.

**Step 4 — the zero-rows edge case.**

```sql
SELECT COUNT(*) AS total_events, MIN(starts_at) AS earliest_start
FROM event WHERE starts_at > '2030-01-01';
```

Expected: `0 | «NULL»`, in `(1 row)`.

**Step 5 — the wrong-table failure.**

```sql
SELECT COUNT(*) AS total_events, MIN(amount) AS cheapest_tier FROM event;
```

Expected failure: `ERROR:  column "amount" does not exist`

**Step 6 — the plausible wrong number.**

```sql
SELECT COUNT(*) AS total_events, MIN(pt.amount) AS cheapest_tier
FROM event e JOIN price_tier pt ON pt.event_id = e.id;
```

Expected: `6 | 999.0000`. There is no error, but 6 is wrong, because there are only 4 events.

Leave the prompt with `\q`.

**Done when:** you have written down the four S1 answers (`4`, `999.0000`, `2582.33`,
`2026-11-20 13:30:00+00`), each from a `(1 row)` result. You can also say out loud why each
returns exactly one row, and why Step 6 says 6.

### Check question, with answer

**Question.** A table called `ticket` has three columns: `id`, `event_name` and `price`. Right now
it is empty: it has zero rows. Someone runs:

```sql
SELECT COUNT(*) AS sold, SUM(price) AS revenue, MAX(price) AS top_price FROM ticket;
```

(a) How many rows come back? (b) What is in each column? (c) In one or two sentences, why is the
number of rows what it is?

**Answer.** (a) One row. (b) `sold` is `0`. `revenue` is `NULL`. `top_price` is `NULL`. Real run,
on an empty throwaway table with those three columns:

```text
 sold | revenue | top_price
------+---------+-----------
    0 |  «NULL» |    «NULL»
(1 row)
```

(c) With no grouping, all the rows form one pile, and each aggregate function squeezes that pile
into one value. One pile gives one row, even when the pile is empty. Counting an empty pile gives
0. The total and the largest of an empty pile do not exist, so they are `NULL`.

### Your doubts

None for this subtask.

---

## Lesson 4 — `COUNT(*)` versus `COUNT(column)`

### Definition

`COUNT(*)` counts **rows**. `COUNT(column)` counts only the rows where that column is **not
`NULL`**. `NULL` means "no value".

### Why it is needed

**Reason 1: sometimes you want to count values, not rows.** The `event` table marks a cancelled
event by putting a time in `cancelled_at`. A live event holds `NULL` there. So
`COUNT(cancelled_at)` counts only the cancelled events. In the same query, `COUNT(*)` counts all
events. One query gives both numbers.

**Reason 2: the difference catches people out.** Someone wants "how many events do we have?".
They write `COUNT(cancelled_at)`, thinking any column will do. They get 1 instead of 4. There is
no error. It is a wrong number that looks fine.

### Key points

- `COUNT(*)` adds 1 for **every row**. It never looks inside a column.
- `COUNT(column)` adds 1 only when the column **holds a value**. It skips `NULL`.
- The two differ only on a column that can hold `NULL`.
- **Every** aggregate function that is given a column skips `NULL`: `SUM`, `AVG`, `MIN`, `MAX`,
  `COUNT(column)`. `AVG` of (10, `NULL`, 20) is 15, not 10.
- `COUNT(column)` of zero rows is `0`, never `NULL`. All forms of `COUNT` give 0 for nothing.
- `COUNT(1)` is the same as `COUNT(*)`, because `1` is never `NULL`.
- `COUNT` skips only `NULL`. It does not skip `false`, `0` or empty text `''`.

### How it works

Think of a class register with a "phone number" box for each student. `COUNT(*)` counts the
students on the list. `COUNT(phone)` counts only the students who filled in the box. A blank box
is skipped.

A query with `COUNT(cancelled_at)` runs in these steps:

1. **`FROM`** collects the rows of the table.
2. **`WHERE`**, if there is one, keeps only the rows that pass a test.
3. For each row that is left, `COUNT(cancelled_at)` looks inside `cancelled_at`:
   - If it holds a value, add 1.
   - If it holds `NULL`, add nothing.
4. The result has **exactly one row**, holding the count.

`COUNT(*)` never looks inside any column. It adds 1 for every row.

**The general rule:** every aggregate function that is given a column skips the `NULL`s in it.
`COUNT(*)` is given no column, so there is nothing for it to skip.

### Example — real run

psql normally prints `NULL` as a blank. This display setting prints it as `«NULL»`:

```text
\pset null '«NULL»'
Null display is "«NULL»".
```

The events:

```text
SELECT name, cancelled_at FROM event ORDER BY starts_at;

        name        |         cancelled_at
--------------------+-------------------------------
 Monsoon Rock Night | «NULL»
 Jazz at the Note   | «NULL»
 Classical Evening  | 2026-10-24 18:05:00.690845+00
 Open Mic Night     | «NULL»
(4 rows)
```

Counting both ways:

```text
SELECT COUNT(*) AS all_events FROM event;

 all_events
------------
          4
(1 row)

SELECT COUNT(cancelled_at) AS cancelled_events FROM event;

 cancelled_events
------------------
                1
(1 row)
```

Both in one query:

```text
SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;

 all_events | cancelled_events
------------+------------------
          4 |                1
(1 row)
```

The live events are the difference:

```text
SELECT COUNT(*) - COUNT(cancelled_at) AS live_events FROM event;

 live_events
-------------
           3
(1 row)
```

### Walkthrough

What each count adds, row by row:

| Event | `cancelled_at` | `COUNT(*)` adds | `COUNT(cancelled_at)` adds |
| --- | --- | --- | --- |
| Monsoon Rock Night | `NULL` | 1 | 0 (skipped, no value) |
| Jazz at the Note | `NULL` | 1 | 0 (skipped) |
| Classical Evening | `2026-10-24 18:05...` | 1 | **1** (it holds a value) |
| Open Mic Night | `NULL` | 1 | 0 (skipped) |
| **Total** | | **4** | **1** |

`COUNT(*)` never looked at `cancelled_at`. It just counted rows. `COUNT(cancelled_at)` looked
inside each row and counted only the filled boxes.

### Edge cases

**Edge case 1: a column that is never `NULL`.** `name` is declared `NOT NULL`, so every event has
one:

```text
SELECT COUNT(name) AS named_events FROM event;

 named_events
--------------
            4
(1 row)
```

`COUNT(name)` equals `COUNT(*)` here. The two counts differ only on a column that can hold `NULL`.

**Edge case 2: a column that is always `NULL`.** No venue has a seat map yet:

```text
SELECT name, seat_map_hints FROM venue ORDER BY name;

      name       | seat_map_hints
-----------------+----------------
 Lakeside Hall   | «NULL»
 Riverside Arena | «NULL»
 The Blue Note   | «NULL»
(3 rows)

SELECT COUNT(*) AS venues, COUNT(seat_map_hints) AS venues_with_seat_map FROM venue;

 venues | venues_with_seat_map
--------+----------------------
      3 |                    0
(1 row)
```

Three rows, zero values.

**Edge case 3: the other aggregate functions skip `NULL` too.** This builds three throwaway rows
holding `10`, `NULL` and `20`. `VALUES` is a quick way to make rows for one query. They form a
tiny table named `t`, with one column `x`.

```text
SELECT COUNT(*) AS count_rows, COUNT(x) AS count_x, SUM(x) AS sum_x, AVG(x) AS avg_x
FROM (VALUES (10), (NULL), (20)) AS t(x);

 count_rows | count_x | sum_x |        avg_x
------------+---------+-------+---------------------
          3 |       2 |    30 | 15.0000000000000000
(1 row)
```

`AVG` gave **15**, not 10. It skipped the `NULL` and averaged only the two real values:
(10 + 20) ÷ 2 = 15. It did not treat `NULL` as 0.

If a missing value really should count as 0, say so with `COALESCE`. `COALESCE(x, 0)` gives back
`x`, or 0 if `x` is `NULL`:

```text
SELECT AVG(COALESCE(x, 0)) AS avg_null_as_zero FROM (VALUES (10), (NULL), (20)) AS t(x);

  avg_null_as_zero
---------------------
 10.0000000000000000
(1 row)
```

Now it is (10 + 0 + 20) ÷ 3 = 10. Neither answer is "right". They answer different questions, so
choose on purpose.

`MIN` and `MAX` skip `NULL` the same way. Three of the four `cancelled_at` values are `NULL`:

```text
SELECT MIN(cancelled_at) AS first_cancellation, MAX(cancelled_at) AS last_cancellation FROM event;

      first_cancellation       |       last_cancellation
-------------------------------+-------------------------------
 2026-10-24 18:05:00.690845+00 | 2026-10-24 18:05:00.690845+00
(1 row)
```

**Edge case 4: `COUNT(1)` is the same as `COUNT(*)`.** It shows up in other people's code. The
value `1` is never `NULL`, so every row gets counted:

```text
SELECT COUNT(1) AS count_one FROM event;

 count_one
-----------
         4
(1 row)
```

### Important terms

| Term | Meaning |
| --- | --- |
| `COUNT(*)` | Counts rows. Never looks inside a column. |
| `COUNT(column)` | Counts the rows where the column holds a value. Skips `NULL`. |
| `COUNT(DISTINCT column)` | Throws away repeated values first, then counts what is left. Also skips `NULL`. |
| `COUNT(1)` | The same as `COUNT(*)`, because `1` is never `NULL`. |
| Nullable column | A column that is allowed to hold `NULL`. Declared without `NOT NULL`. |
| `IS NULL` | The test for "holds no value". `= NULL` never matches anything. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Using `COUNT(some_column)` to count rows | A smaller number, with no error, when the column has `NULL`s: `COUNT(cancelled_at)` gives 1, not 4. | To count rows, use `COUNT(*)`. |
| Thinking `COUNT(column)` counts **different** values | It counts every non-`NULL` value, repeats included. | For different values, use `COUNT(DISTINCT column)`. |
| Thinking empty text `''` is `NULL` | `''` is counted. | `''` is a value: text with nothing in it. Only `NULL` is skipped. |
| Writing `COUNT(cancelled_at IS NULL)` to count live events | It gives 4, not 3. | Use `COUNT(*) - COUNT(cancelled_at)`, or `COUNT(*)` with `WHERE cancelled_at IS NULL`. |
| Expecting `AVG` to treat `NULL` as 0 | (10, `NULL`, 20) averages to 15, not 10. | If missing should mean 0, write `AVG(COALESCE(x, 0))`. Decide on purpose. |

**Mistake 2, real run.** Three tiers are called `General`:

```text
SELECT COUNT(name) AS tier_names, COUNT(DISTINCT name) AS distinct_tier_names FROM price_tier;

 tier_names | distinct_tier_names
------------+---------------------
          6 |                   4
(1 row)
```

`COUNT(name)` counted all 6 names. `DISTINCT` throws away repeats first. The 4 different names
are General, VIP, Front Table and Balcony.

**Mistake 3, real run.** Three throwaway rows: empty text, `NULL`, and `'x'`:

```text
SELECT COUNT(*) AS count_rows, COUNT(x) AS count_x FROM (VALUES (''), (NULL), ('x')) AS t(x);

 count_rows | count_x
------------+---------
          3 |       2
(1 row)
```

Only the `NULL` was skipped. The empty text was counted.

**Mistake 4, real run.** This one fools experienced people:

```text
SELECT COUNT(cancelled_at IS NULL) AS looks_like_live_count FROM event;

 looks_like_live_count
-----------------------
                     4
(1 row)
```

Why it says 4. The test `cancelled_at IS NULL` is worked out for every row first. It gives true or
false:

```text
SELECT name, cancelled_at IS NULL AS not_cancelled FROM event ORDER BY starts_at;

        name        | not_cancelled
--------------------+---------------
 Monsoon Rock Night | t
 Jazz at the Note   | t
 Classical Evening  | f
 Open Mic Night     | t
(4 rows)
```

`t` means true and `f` means false. **Both are values.** Neither is `NULL`. So `COUNT` counts all
4. `COUNT` only skips `NULL`. It does not skip false.

### Where it is used

- "3 of 4 events are live": `COUNT(*)` and `COUNT(cancelled_at)` in one query.
- Data quality checks. "How many customers have no email?" is `COUNT(*) - COUNT(email)`.
- Profile completeness. "How many users added a photo?" is `COUNT(photo_url)`.
- Counting children after a `LEFT JOIN`. An event with no tiers must count as 0 tiers, not 1.

### Link to the task

This is exactly **S4**: "Count all events, then count `cancelled_at`, and watch the second number
only include cancelled ones. Write down when each is the one you want." `COUNT(*)` gives 4, all
events. `COUNT(cancelled_at)` gives 1, only the cancelled ones.

It comes back in the grouping lessons. "Tiers per event" with a `LEFT JOIN` must report 0 tiers
for Open Mic Night. `COUNT(*)` gets that wrong, and `COUNT(column)` gets it right.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — look at `cancelled_at`.**

```sql
SELECT name, cancelled_at FROM event ORDER BY starts_at;
```

Expected: 4 rows. Three show `«NULL»`. Classical Evening shows a time.

**Step 3 — count both ways in one query.**

```sql
SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;
```

Expected: `4 | 1`.

**Step 4 — the live events.**

```sql
SELECT COUNT(*) - COUNT(cancelled_at) AS live_events FROM event;
```

Expected: `3`.

**Step 5 — a column that is always `NULL`.**

```sql
SELECT COUNT(*) AS venues, COUNT(seat_map_hints) AS venues_with_seat_map FROM venue;
```

Expected: `3 | 0`.

**Step 6 — `AVG` skips `NULL`.**

```sql
SELECT COUNT(*) AS count_rows, COUNT(x) AS count_x, SUM(x) AS sum_x, AVG(x) AS avg_x
FROM (VALUES (10), (NULL), (20)) AS t(x);
```

Expected: `3 | 2 | 30 | 15.0000000000000000`.

**Step 7 — the trap.**

```sql
SELECT COUNT(cancelled_at IS NULL) AS looks_like_live_count FROM event;
```

Expected: `4`. It is not 3.

Leave the prompt with `\q`.

**Done when:** Step 3 shows `4 | 1`. You can say why the two numbers differ, why Step 6 gives 15
and not 10, and why Step 7 gives 4.

### Summary for revision

- `COUNT(*)` counts rows. `COUNT(column)` counts the values in that column, skipping `NULL`.
- They differ only on a nullable column. On a `NOT NULL` column they are equal.
- Every aggregate function given a column skips `NULL`. `AVG(10, NULL, 20)` is 15. Use
  `COALESCE(x, 0)` only if missing really means 0.
- `COUNT` skips only `NULL`. `false`, `0` and `''` are values and get counted. So
  `COUNT(condition)` counts every row.
- `COUNT(DISTINCT column)` counts different values. `COUNT(1)` equals `COUNT(*)`.
- Live events = `COUNT(*) - COUNT(cancelled_at)`.

### Check questions, with answers

**Question 1.** Here is a table called `customer`:

```text
 id | name  |    phone
----+-------+-------------
  1 | Asha  | 98450 11111
  2 | Ravi  | «NULL»
  3 | Meena | 98450 33333
  4 | Kiran | «NULL»
```

`«NULL»` means the phone box is empty: no value.

(a) What does this query return?

```sql
SELECT COUNT(*) AS customers, COUNT(phone) AS with_phone FROM customer;
```

(b) Write one query that returns the number of customers who have **no** phone.

**Answer.** (a) One row: `4 | 2`. `COUNT(*)` counts all 4 rows. `COUNT(phone)` skips Ravi and
Kiran, whose phone is `NULL`, and counts Asha and Meena. Real run, in a throwaway table with the
same four rows:

```text
 customers | with_phone
-----------+------------
         4 |          2
(1 row)
```

(b) Two correct ways, both giving `2`:

```sql
SELECT COUNT(*) - COUNT(phone) AS without_phone FROM customer;
SELECT COUNT(*) AS without_phone FROM customer WHERE phone IS NULL;
```

```text
 without_phone
---------------
             2
(1 row)
```

The first takes "all rows" minus "rows with a phone". The second throws away every row that has a
phone, then counts what is left. Note the test is `IS NULL`. `WHERE phone = NULL` would match
nothing and give 0.

**Question 2.** Here is a table called `sensor_reading`. The sensor failed once, so one reading is
missing:

```text
 id | temperature
----+-------------
  1 |          30
  2 |      «NULL»
  3 |          20
```

(a) What does this query return?

```sql
SELECT AVG(temperature) AS average, COUNT(temperature) AS readings, COUNT(*) AS all_rows
FROM sensor_reading;
```

(b) A teammate says "the average should be 16.67". Where does 16.67 come from? Which question
does each number answer?

**Answer.** (a) One row: `25.0000000000000000 | 2 | 3`. `AVG` skips the `NULL` and averages the
two real readings: (30 + 20) ÷ 2 = 25. `COUNT(temperature)` skips the `NULL` too, so it gives 2.
`COUNT(*)` counts all 3 rows. Real run:

```text
       average       | readings | all_rows
---------------------+----------+----------
 25.0000000000000000 |        2 |        3
(1 row)
```

(b) 16.67 treats the missing reading as 0 degrees: (30 + 0 + 20) ÷ 3 = 16.67. That is what
`AVG(COALESCE(temperature, 0))` computes:

```text
   missing_as_zero
---------------------
 16.6666666666666667
(1 row)
```

25 answers "what was the average of the readings we actually have?". 16.67 answers "what is the
average if a failed reading counts as 0 degrees?". For a temperature sensor, 25 is the sensible
one. A failed reading is not a reading of 0 degrees.

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `user_account`:

```text
 id |       email       | photo_url
----+-------------------+-----------
  1 | asha@example.com  | a.png
  2 | ravi@example.com  | «NULL»
  3 | «NULL»            | «NULL»
  4 | meena@example.com | m.png
  5 | kiran@example.com | «NULL»
```

What does this query return?

```sql
SELECT COUNT(*) AS users, COUNT(email) AS with_email, COUNT(photo_url) AS with_photo
FROM user_account;
```

<details>
<summary>Answer</summary>

One row: `5 | 4 | 2`. `COUNT(*)` counts all 5 users. `COUNT(email)` skips user 3. `COUNT(photo_url)`
counts only users 1 and 4.

```text
 users | with_email | with_photo
-------+------------+------------
     5 |          4 |          2
(1 row)
```

</details>

**Practice 2.** Here is a table called `venue_list`:

```text
 id |  city
----+--------
  1 | Mumbai
  2 | Mumbai
  3 | Pune
  4 | «NULL»
```

What does this query return?

```sql
SELECT COUNT(*) AS all_rows, COUNT(city) AS with_city, COUNT(DISTINCT city) AS different_cities
FROM venue_list;
```

<details>
<summary>Answer</summary>

One row: `4 | 3 | 2`. `COUNT(*)` counts all 4 rows. `COUNT(city)` skips the `NULL` and counts
Mumbai, Mumbai and Pune: 3. `COUNT(DISTINCT city)` also skips the `NULL`, throws away the
repeated Mumbai, and counts Mumbai and Pune: 2.

```text
 all_rows | with_city | different_cities
----------+-----------+------------------
        4 |         3 |                2
(1 row)
```

</details>

**Practice 3.** Here is a table called `invoice`. A paid invoice has a time in `paid_at`. An unpaid
one holds `NULL`:

```text
 id |       paid_at
----+---------------------
  1 | 2026-10-01 10:00:00
  2 | «NULL»
  3 | «NULL»
```

A teammate wants the number of paid invoices and writes:

```sql
SELECT COUNT(paid_at IS NOT NULL) AS looks_like_paid FROM invoice;
```

What does it return? Why? What should they write instead?

<details>
<summary>Answer</summary>

It returns `3`, not 1. The test `paid_at IS NOT NULL` is worked out for each row first, giving
true, false, false. True and false are both values, never `NULL`, so `COUNT` counts all 3 rows.
Write `COUNT(paid_at)` instead. It skips the two `NULL`s and gives `1`:

```text
 looks_like_paid
-----------------
               3
(1 row)

 paid_invoices
---------------
             1
(1 row)
```

</details>

### Your doubts

None for this lesson.

---

## S4 — end to end: the `COUNT` difference

**The subtask.** "Show yourself the `COUNT` difference. Count all events, then count
`cancelled_at`, and watch the second number only include cancelled ones. Write down when each is
the one you want."

**Concepts used.** `COUNT(*)`, `COUNT(column)`, `IS NULL` / `IS NOT NULL`, and the meaning of
`NULL` in the `cancelled_at` column.

### How the concepts fit together

Three facts combine:

1. `COUNT(*)` adds 1 for **every row**.
2. `COUNT(column)` adds 1 only when the column **holds a value**. It skips `NULL`, which means
   "no value".
3. In the `event` table, `cancelled_at` is `NULL` for a live event. It holds a time for a cancelled
   event.

Put together: `COUNT(cancelled_at)` skips exactly the live events. So it counts exactly the
cancelled ones.

### The approach in plain words

1. Look at the column first, so you know which rows hold `NULL`.
2. Count all rows.
3. Count the column.
4. Prove the second number is exactly the cancelled events. List them another way and compare.
5. Make the number move. Cancel one more event, count again, then undo it.
6. Write down the rule.

### The solution — real run

```text
\pset null '«NULL»'
Null display is "«NULL»".

SELECT name, cancelled_at FROM event ORDER BY starts_at;

        name        |         cancelled_at
--------------------+-------------------------------
 Monsoon Rock Night | «NULL»
 Jazz at the Note   | «NULL»
 Classical Evening  | 2026-10-24 18:05:00.690845+00
 Open Mic Night     | «NULL»
(4 rows)

SELECT COUNT(*) AS all_events FROM event;

 all_events
------------
          4
(1 row)

SELECT COUNT(cancelled_at) AS cancelled_events FROM event;

 cancelled_events
------------------
                1
(1 row)
```

**Cross-check by another method.** `IS NOT NULL` is the test for "holds a value":

```text
SELECT name AS cancelled_event FROM event WHERE cancelled_at IS NOT NULL;

  cancelled_event
-------------------
 Classical Evening
(1 row)
```

Two different methods give the same number, 1. The one name is the event known to be cancelled.

**All three numbers in one query:**

```text
SELECT COUNT(*) AS all_events,
       COUNT(cancelled_at) AS cancelled_events,
       COUNT(*) - COUNT(cancelled_at) AS live_events
FROM event;

 all_events | cancelled_events | live_events
------------+------------------+-------------
          4 |                1 |           3
(1 row)
```

### Watch the second number move

Cancel Open Mic Night too. `now()` gives the current time:

```text
UPDATE event SET cancelled_at = now() WHERE name = 'Open Mic Night';
UPDATE 1

SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;

 all_events | cancelled_events
------------+------------------
          4 |                2
(1 row)
```

`COUNT(*)` did not change, because no row was added or removed. `COUNT(cancelled_at)` went up by
exactly 1, because one more box got filled. Then undo it:

```text
UPDATE event SET cancelled_at = NULL WHERE name = 'Open Mic Night';
UPDATE 1

SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;

 all_events | cancelled_events
------------+------------------
          4 |                1
(1 row)
```

`SET cancelled_at = NULL` worked because inside `SET`, the `=` means "put this value here". It is
not a test. Inside `WHERE`, `=` **is** a test, and a test against `NULL` never matches:

```text
SELECT COUNT(*) AS equals_null_matches FROM event WHERE cancelled_at = NULL;

 equals_null_matches
---------------------
                   0
(1 row)
```

### The write-down: when each one is the one you want

| You want to know | Use | Example on our data | Result |
| --- | --- | --- | --- |
| How many things exist | `COUNT(*)` | All events | 4 |
| How many have a value in one column | `COUNT(column)` | Cancelled events: `COUNT(cancelled_at)` | 1 |
| How many are missing a value | `COUNT(*) - COUNT(column)` | Live events | 3 |
| How many **different** values exist | `COUNT(DISTINCT column)` | Different tier names | 4, from 6 tiers |
| How many children a parent has, after a `LEFT JOIN` | `COUNT(child primary key)`, such as `COUNT(pt.id)` | Tiers of Open Mic Night | 0. `COUNT(*)` says 1. |

**The rule in one sentence:** use `COUNT(*)` to count rows, and `COUNT(column)` to count values.
They differ only when the column can be `NULL`, and then they answer different questions.

The last row, demonstrated. A `LEFT JOIN` keeps every event, even one with no tiers. With no tier
to pair with, it fills the tier columns with `NULL`. So Open Mic Night still appears, as one row:

```text
SELECT e.name, pt.id AS tier_id, pt.name AS tier
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Open Mic Night';

      name      | tier_id |  tier
----------------+---------+--------
 Open Mic Night | «NULL»  | «NULL»
(1 row)

SELECT COUNT(*) AS count_star, COUNT(pt.id) AS count_tier_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Open Mic Night';

 count_star | count_tier_id
------------+---------------
          1 |             0
(1 row)
```

`COUNT(*)` says 1, because there is one row. Open Mic Night has **zero** tiers, and
`COUNT(pt.id)` says 0, because that row's tier id is `NULL`. For an event that does have tiers,
the two agree:

```text
SELECT COUNT(*) AS count_star, COUNT(pt.id) AS count_tier_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Monsoon Rock Night';

 count_star | count_tier_id
------------+---------------
          3 |             3
(1 row)
```

### Key decisions and trade-offs

**Decision 1: `COUNT(cancelled_at)` or `WHERE cancelled_at IS NOT NULL`?** Both give 1.
`COUNT(column)` lets one query return all three numbers in one row. `WHERE` gives one number per
query. But a newcomer reads `WHERE cancelled_at IS NOT NULL` more easily. The trade-off is fewer
queries against plainer code. With `COUNT(column)`, give the result a clear name such as
`cancelled_events`, so its meaning is not hidden.

**Decision 2: the trick depends on what `NULL` means in the table.** `COUNT(cancelled_at)` means
"cancelled events" only because this table uses `NULL` for "not cancelled". If someone stored a
fake date like `1970-01-01` for live events instead, every row would hold a value, and
`COUNT(cancelled_at)` would say 4. The design of the table is what makes the count meaningful.

**Decision 3: after a `LEFT JOIN`, count the child's primary key.** `pt.id` is the primary key of
`price_tier`, so it is never `NULL` in the real table. After the join, a `NULL` there can only mean
"this event had no tier". Counting a column that can be `NULL` in the real table would also skip
real tiers that happen to have an empty box, giving a count that is too small.

### Tests

| Test | Query or action | Result | What it proves |
| --- | --- | --- | --- |
| Normal | `COUNT(*)` and `COUNT(cancelled_at)` | `4` and `1` | The two counts answer different questions. |
| Cross-check | `WHERE cancelled_at IS NOT NULL` | 1 row: Classical Evening | The 1 is exactly the cancelled event. |
| Change | Cancel Open Mic Night, count, undo, count | `4 \| 2`, then `4 \| 1` | Only the second number tracks cancellations. |
| Edge: zero rows | `... WHERE starts_at > '2030-01-01'` | `0 \| 0` | `COUNT` never gives `NULL`, even for no rows. |
| Edge: parent with no children | `LEFT JOIN`, Open Mic Night | `COUNT(*)` 1, `COUNT(pt.id)` 0 | Only `COUNT(column)` gets zero right. |
| Silent failure | `COUNT(cancelled_at IS NULL)` | `4` | A true/false test is never `NULL`, so every row counts. |
| Silent failure | `WHERE cancelled_at = NULL` | `0` | A test against `NULL` never matches. |

The zero-rows test, real run:

```text
SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events
FROM event WHERE starts_at > '2030-01-01';

 all_events | cancelled_events
------------+------------------
          0 |                0
(1 row)
```

### Why it works

`COUNT(*)` never looks inside a row, so it counts all 4. `COUNT(cancelled_at)` looks inside each
row and skips `NULL`. The table stores `NULL` exactly for live events. So skipping the `NULL`s
means skipping the live events, and what is left is the cancelled ones.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — look at the column.**

```sql
SELECT name, cancelled_at FROM event ORDER BY starts_at;
```

Expected: 4 rows. Only Classical Evening shows a time.

**Step 3 — count all rows, then count the column.**

```sql
SELECT COUNT(*) AS all_events FROM event;
SELECT COUNT(cancelled_at) AS cancelled_events FROM event;
```

Expected: `4`, then `1`.

**Step 4 — cross-check.**

```sql
SELECT name AS cancelled_event FROM event WHERE cancelled_at IS NOT NULL;
```

Expected: one row, `Classical Evening`.

**Step 5 — all three numbers in one query.**

```sql
SELECT COUNT(*) AS all_events,
       COUNT(cancelled_at) AS cancelled_events,
       COUNT(*) - COUNT(cancelled_at) AS live_events
FROM event;
```

Expected: `4 | 1 | 3`.

**Step 6 — watch it move, then undo.**

```sql
UPDATE event SET cancelled_at = now() WHERE name = 'Open Mic Night';
SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;
UPDATE event SET cancelled_at = NULL WHERE name = 'Open Mic Night';
SELECT COUNT(*) AS all_events, COUNT(cancelled_at) AS cancelled_events FROM event;
```

Expected: `UPDATE 1`, then `4 | 2`, then `UPDATE 1`, then `4 | 1`.

**Important:** the last result must be `4 | 1`. If it is `4 | 2`, the undo did not run. Run the
second `UPDATE` again. Later lessons expect Open Mic Night to be live.

**Step 7 — counting children after a `LEFT JOIN`.**

```sql
SELECT COUNT(*) AS count_star, COUNT(pt.id) AS count_tier_id
FROM event e
LEFT JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Open Mic Night';
```

Expected: `1 | 0`.

Leave the prompt with `\q`.

**Step 8 — write it down.** In your own notes, write the rule from "The write-down: when each one
is the one you want", in your own words.

**Done when:** Step 3 gives `4` and `1`, and Step 6 ends at `4 | 1`. You have written down when to
use `COUNT(*)` and when to use `COUNT(column)`. You can explain why Step 7 gives two different
numbers.

### Check question, with answer

**Question.** Here is a table called `task`. A finished task has a time in `done_at`. An open task
holds `NULL` there:

```text
 id |     title     |       done_at
----+---------------+---------------------
  1 | Write tests   | 2026-10-20 09:00:00
  2 | Fix login bug | «NULL»
  3 | Update docs   | 2026-10-22 17:30:00
  4 | Review PR     | «NULL»
  5 | Deploy        | «NULL»
```

Write **one** query that returns three numbers in one row: all tasks, finished tasks, and open
tasks. What values does it return?

**Answer.**

```sql
SELECT COUNT(*) AS total_tasks,
       COUNT(done_at) AS done_tasks,
       COUNT(*) - COUNT(done_at) AS open_tasks
FROM task;
```

```text
 total_tasks | done_tasks | open_tasks
-------------+------------+------------
           5 |          2 |          3
(1 row)
```

`COUNT(*)` counts all 5 rows. `COUNT(done_at)` skips the three `NULL`s and counts the 2 finished
tasks. The open tasks are the difference: 5 − 2 = 3. Real run, in a throwaway table with the same
five rows.

### Your doubts

None for this subtask.

---

## Lesson 5 — `GROUP BY` on one table: tiers per event

### Definition

`GROUP BY` splits the rows into groups. A **group** is a set of rows that share the same value in
one column. Each aggregate function then runs **once per group**. The result has **one row per
group**.

The column you group by is called the **grouping key**.

### Why it is needed

The question: "How many price tiers does each event have?"

An aggregate function on its own answers for one event at a time, with a `WHERE` for each event:

```text
SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001';
 tiers
-------
     3

SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002';
 tiers
-------
     1

SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000003';
 tiers
-------
     2

SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000004';
 tiers
-------
     0
```

Four queries for four events. With 1,000 events, it would be 1,000 queries. Every new event means
one more query. `GROUP BY` answers for every event in **one** query.

### Key points

- `GROUP BY column` puts rows with the same value in that column into the same group.
- Each aggregate function runs once per group. One result row per group.
- Without `GROUP BY`, all rows form one single group, so the result is exactly one row.
- The grouping key decides "per what". The word after "per" in a question names it.
- A group is made only from rows that exist. A value with no rows gets no group, and no result
  row. It does not appear with 0.
- Zero rows in, with `GROUP BY`, gives **zero rows out**. Without `GROUP BY`, it gives one row.
- `GROUP BY` does not sort. Add `ORDER BY`.
- All `NULL`s in the grouping key share one group.

### How it works

Think of a teacher with a stack of exam papers from three classes. She sorts them into one stack
per class. Then she counts each stack. She writes one line per class, such as "Class A: 30
papers".

The query:

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id;
```

It runs in these steps:

1. **`FROM price_tier`** collects the rows.
2. **`WHERE`**, if there is one, keeps only the rows that pass a test.
3. **`GROUP BY event_id`** sorts the rows into groups. Rows with the same `event_id` go into the
   same group.
4. **Each aggregate function runs once per group.** `COUNT(*)` counts the rows of each group
   separately.
5. **One result row per group.** Each row holds the group's `event_id` and its count.

### Example — real run

```text
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000003 |     2
(3 rows)
```

Which id is which event:

```text
SELECT id, name FROM event ORDER BY id;

                  id                  |        name
--------------------------------------+--------------------
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note
 aaaaaaaa-0000-0000-0000-000000000004 | Open Mic Night
(4 rows)
```

The names live on the `event` table. Putting them into the grouped result needs a join.

Every aggregate function works per group, not just `COUNT`:

```text
SELECT event_id, COUNT(*) AS tiers, MIN(amount) AS cheapest, MAX(amount) AS dearest, SUM(amount) AS total
FROM price_tier
GROUP BY event_id
ORDER BY event_id;

               event_id               | tiers | cheapest  |  dearest  |   total
--------------------------------------+-------+-----------+-----------+-----------
 aaaaaaaa-0000-0000-0000-000000000001 |     3 | 1499.0000 | 4999.0000 | 8997.0000
 aaaaaaaa-0000-0000-0000-000000000002 |     1 |  999.0000 |  999.0000 |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 |     2 | 1999.0000 | 3499.0000 | 5498.0000
(3 rows)
```

### Walkthrough

**Step 1, `FROM price_tier`.** The 6 rows, sorted by `event_id` so the groups are easy to see:

```text
SELECT event_id, name, amount FROM price_tier ORDER BY event_id, amount;

               event_id               |    name     |  amount
--------------------------------------+-------------+-----------
 aaaaaaaa-0000-0000-0000-000000000001 | General     | 1499.0000
 aaaaaaaa-0000-0000-0000-000000000001 | Balcony     | 2499.0000
 aaaaaaaa-0000-0000-0000-000000000001 | VIP         | 4999.0000
 aaaaaaaa-0000-0000-0000-000000000002 | General     |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | General     | 1999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Front Table | 3499.0000
(6 rows)
```

**Step 3, `GROUP BY event_id`.** The rows are sorted into 3 groups:

| Group (`event_id`) | Event | Rows in the group | Amounts in the group |
| --- | --- | --- | --- |
| `...0001` | Monsoon Rock Night | 3 | 1499, 2499, 4999 |
| `...0002` | Classical Evening | 1 | 999 |
| `...0003` | Jazz at the Note | 2 | 1999, 3499 |

**Step 4, each aggregate function runs once per group.**

| Group | `COUNT(*)` | `MIN(amount)` | `MAX(amount)` | `SUM(amount)` |
| --- | --- | --- | --- | --- |
| `...0001` | 3 | 1499 | 4999 | 1499 + 2499 + 4999 = 8997 |
| `...0002` | 1 | 999 | 999 | 999 |
| `...0003` | 2 | 1999 | 3499 | 1999 + 3499 = 5498 |

**Step 5, one result row per group.** There are 3 groups, so there are 3 rows.

**What is missing: Open Mic Night (`...0004`).** It has no rows in `price_tier`. A group is made
only from rows that exist. No rows means no group, and no group means no result row. It does not
appear with a 0. It does not appear at all. Getting a 0 for it means starting from the `event`
table and using a `LEFT JOIN`.

### The grouping key decides "per what"

The same table, grouped by tier name instead:

```text
SELECT name AS tier_name, COUNT(*) AS events_offering_it
FROM price_tier
GROUP BY name
ORDER BY name;

  tier_name  | events_offering_it
-------------+--------------------
 Balcony     |                  1
 Front Table |                  1
 General     |                  3
 VIP         |                  1
(4 rows)
```

Same table, different key, different question: "how many events offer each tier name?".

| Question | Grouping key |
| --- | --- |
| Tiers **per event** | `GROUP BY event_id` |
| Events **per tier name** | `GROUP BY name` |

### Edge cases

**Edge case 1: `GROUP BY` does not sort.** The same query with no `ORDER BY`:

```text
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000003 |     2
(3 rows)
```

The order came out as 2, 1, 3. If the order matters, add `ORDER BY`.

**Edge case 2: zero rows in means zero rows out.** No tier costs more than 9000:

```text
SELECT event_id, COUNT(*) AS tiers FROM price_tier WHERE amount > 9000 GROUP BY event_id;

 event_id | tiers
----------+-------
(0 rows)

SELECT COUNT(*) AS tiers FROM price_tier WHERE amount > 9000;

 tiers
-------
     0
(1 row)
```

- **Without `GROUP BY`**, there is always exactly one group, even an empty one. One row, holding 0.
- **With `GROUP BY`**, groups are made from rows. No rows means no groups, so no result rows.

**Edge case 3: `NULL` in the grouping key.** `NULL` means "no value". After
`\pset null '«NULL»'`, psql prints it as `«NULL»`.

```text
SELECT cancelled_at, COUNT(*) AS events FROM event GROUP BY cancelled_at ORDER BY cancelled_at;

         cancelled_at          | events
-------------------------------+--------
 2026-10-24 18:05:00.690845+00 |      1
 «NULL»                        |      3
(2 rows)
```

The three live events all hold `NULL`, and they landed in **one** group. For grouping, SQL treats
all `NULL`s as the same, so they share a group. This rule is special to grouping. In `WHERE`, a
test against `NULL` is still never true:

```text
SELECT COUNT(*) AS matches FROM event WHERE cancelled_at = NULL;

 matches
---------
       0
(1 row)
```

### Important terms

| Term | Meaning |
| --- | --- |
| `GROUP BY column` | Splits the rows into groups by the value in that column. |
| Group | A set of rows that share the same value in the grouping key. |
| Grouping key | The column (or columns) named in `GROUP BY`. |
| "Per what" | The word after "per" in a question. It names the grouping key. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Forgetting `GROUP BY` | One row: the total for all events, 6. | Add `GROUP BY event_id`. |
| Leaving the grouping key out of `SELECT` | Numbers with no labels: 3, 1, 2. Which event is which? | Put the grouping key in `SELECT`. |
| Expecting the result to be sorted | Rows come back in any order. The real run gave 2, 1, 3. | Add `ORDER BY`. |
| Expecting a 0 row for an event with no tiers | Open Mic Night is simply missing. | Groups come only from rows that exist. Start from `event` with a `LEFT JOIN`. |
| Grouping by the wrong column | A wrong-shaped answer: one row per tier name instead of per event. | Find the word after "per" in the question. |
| Selecting a column that is neither the grouping key nor inside an aggregate function | An error, shown below. | Follow the grouping-key rule, taught in the next lesson. |

**Forgetting `GROUP BY`, real run:**

```text
SELECT COUNT(*) AS tiers FROM price_tier;

 tiers
-------
     6
(1 row)
```

**Leaving the grouping key out, real run:**

```text
SELECT COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;

 tiers
-------
     3
     1
     2
(3 rows)
```

The numbers are correct, but nobody reading them can tell which event each belongs to.

**A column that is not the grouping key, real run:**

```text
SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY event_id;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY eve...
                         ^
```

In plain words: the group for Monsoon Rock Night holds three names, General, Balcony and VIP. The
result has one row for that group, with one slot for `name`. The database will not pick one of
the three.

### Where it is used

Any question with "per" in it:

- Tickets sold per event.
- Revenue per price tier.
- Orders per day.
- Sign-ups per country.
- Errors per service, from a log table.

### Link to the task

This is the "tiers per event" part of **S2**:

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;
```

The other two parts of S2 are "select a column you did not group by and read the error", and
"events per city", which needs a join because `city` lives on `venue`.

It also sets up **S7**, "total tier value per event". That is `SUM(amount)` per group. The `total`
column in the walkthrough (8997, 999, 5498) is exactly that answer, minus the event names.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — the slow way: one query per event.**

```sql
SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001';
SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002';
SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000003';
SELECT COUNT(*) AS tiers FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000004';
```

Expected: `3`, `1`, `2`, `0`.

**Step 3 — the same answer in one query.**

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;
```

Expected: 3 rows, with counts `3`, `1`, `2`. There is no row for `...0004`.

**Step 4 — several aggregate functions per group.**

```sql
SELECT event_id, COUNT(*) AS tiers, MIN(amount) AS cheapest, MAX(amount) AS dearest, SUM(amount) AS total
FROM price_tier
GROUP BY event_id
ORDER BY event_id;
```

Expected: 3 rows. The `total` column shows `8997.0000`, `999.0000`, `5498.0000`.

**Step 5 — a different grouping key.**

```sql
SELECT name AS tier_name, COUNT(*) AS events_offering_it FROM price_tier GROUP BY name ORDER BY name;
```

Expected: 4 rows. `General` shows `3`; the other three names show `1`.

**Step 6 — no `ORDER BY`.**

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id;
```

Expected: the same 3 rows, possibly in a different order. Any order is allowed.

**Step 7 — zero rows in, zero rows out.**

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier WHERE amount > 9000 GROUP BY event_id;
SELECT COUNT(*) AS tiers FROM price_tier WHERE amount > 9000;
```

Expected: `(0 rows)`, then one row holding `0`.

**Step 8 — all `NULL`s share one group.**

```sql
SELECT cancelled_at, COUNT(*) AS events FROM event GROUP BY cancelled_at ORDER BY cancelled_at;
```

Expected: 2 rows. One shows a time with `1`. The other shows `«NULL»` with `3`.

Leave the prompt with `\q`.

**Done when:** Step 3 gives the "tiers per event" answer in one query. You can say why Open Mic
Night is missing from it, and why Step 7 gives `(0 rows)` in one query but `0` in the other.

### Summary for revision

- `GROUP BY key` sorts rows into groups by the key's value. Aggregate functions run once per group.
  One result row per group.
- No `GROUP BY` means one single group, so one row.
- The key answers "per what?". Change the key and you change the question.
- Groups come only from rows that exist. A missing value is missing from the result, not shown as
  0.
- With `GROUP BY`, zero rows in gives zero rows out.
- `GROUP BY` does not sort; add `ORDER BY`. All `NULL`s share one group.

### Check questions, with answers

**Question 1.** Here is a table called `sale`:

```text
 id | shop  | amount
----+-------+--------
  1 | Pune  |    200
  2 | Delhi |    500
  3 | Pune  |    300
  4 | Pune  |    100
  5 | Delhi |    400
```

Someone runs this query:

```sql
SELECT shop, COUNT(*) AS sales, SUM(amount) AS total FROM sale GROUP BY shop ORDER BY shop;
```

(a) How many rows does the result have? (b) What values are in each row?

**Answer.** (a) Two rows, one per shop. (b) `Delhi | 2 | 900` and `Pune | 3 | 600`.

`GROUP BY shop` makes two groups. Delhi holds rows 2 and 5 (500 and 400), so its count is 2 and its
total is 900. Pune holds rows 1, 3 and 4 (200, 300 and 100), so its count is 3 and its total is
600. `ORDER BY shop` puts Delhi before Pune, alphabetically. Real run, in a throwaway table with
the same five rows:

```text
 shop  | sales | total
-------+-------+-------
 Delhi |     2 |   900
 Pune  |     3 |   600
(2 rows)
```

**Question 2.** Here is the same `sale` table again:

```text
 id | shop  | amount
----+-------+--------
  1 | Pune  |    200
  2 | Delhi |    500
  3 | Pune  |    300
  4 | Pune  |    100
  5 | Delhi |    400
```

Here are two queries:

```sql
SELECT shop, COUNT(*) AS sales FROM sale WHERE amount > 1000 GROUP BY shop;
SELECT COUNT(*) AS sales FROM sale WHERE amount > 1000;
```

(a) What does each one return? (b) Why does the first return a different number of rows from the
second?

**Answer.** (a) The first returns no rows at all. The second returns one row holding 0.

```text
 shop | sales
------+-------
(0 rows)

 sales
-------
     0
(1 row)
```

(b) In both, `WHERE amount > 1000` throws away all five sales, because none is above 1000. After
that, the two queries differ. Without `GROUP BY`, there is always exactly one group, even an empty
one, so the second query returns one row, and counting an empty group gives 0. With `GROUP BY`,
groups are made from the rows that are left. No rows are left, so no groups exist, so the first
query has no rows to return.

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `visit`. Each row is one visit to a web page, with how long
it lasted:

```text
 id |  page   | seconds
----+---------+---------
  1 | home    |      30
  2 | about   |      10
  3 | home    |      50
  4 | pricing |      20
  5 | home    |      40
```

What does this query return?

```sql
SELECT page, COUNT(*) AS visits, AVG(seconds) AS avg_seconds FROM visit GROUP BY page ORDER BY page;
```

<details>
<summary>Answer</summary>

Three rows, one per page, sorted by page name. `home` has three visits (30, 50, 40), so its average
is 120 ÷ 3 = 40. The other two pages have one visit each, so their average is that one value.

```text
  page   | visits |     avg_seconds
---------+--------+---------------------
 about   |      1 | 10.0000000000000000
 home    |      3 | 40.0000000000000000
 pricing |      1 | 20.0000000000000000
(3 rows)
```

</details>

**Practice 2.** Here is a table called `purchase`:

```text
 id |  status
----+----------
  1 | paid
  2 | paid
  3 | refunded
  4 | pending
  5 | paid
```

Write a query that answers "how many purchases are there per status?", sorted by status.

<details>
<summary>Answer</summary>

The word after "per" is "status", so `status` is the grouping key:

```sql
SELECT status, COUNT(*) AS purchases FROM purchase GROUP BY status ORDER BY status;
```

```text
  status  | purchases
----------+-----------
 paid     |         3
 pending  |         1
 refunded |         1
(3 rows)
```

</details>

**Practice 3.** Here is a table called `ticket`. Some tickets have no seat type yet, shown as
`«NULL»`:

```text
 id | event_name | seat_type
----+------------+-----------
  1 | Jazz       | VIP
  2 | Jazz       | «NULL»
  3 | Rock       | «NULL»
  4 | Rock       | VIP
  5 | Rock       | General
```

What does this query return? How many rows, and is there a row for the tickets with no seat type?

```sql
SELECT seat_type, COUNT(*) AS tickets FROM ticket GROUP BY seat_type ORDER BY seat_type;
```

<details>
<summary>Answer</summary>

Three rows. Yes, there is a row for the missing seat types. The two `NULL`s share one group,
because for grouping, SQL treats all `NULL`s as the same. `ORDER BY` puts the `NULL` group last.

```text
 seat_type | tickets
-----------+---------
 General   |       1
 VIP       |       2
 «NULL»    |       2
(3 rows)
```

</details>

### Your doubts

None for this lesson.

---

## Lesson 6 — The grouping-key rule, and its error

### Definition

The **grouping-key rule** says: in a query that groups, every column in `SELECT` must be one of
two things.

- A **grouping key**, which is a column named in `GROUP BY`.
- Or something **inside an aggregate function**.

If a column is neither, Postgres refuses to run the query.

### Why it is needed

Each result row has one **slot** per column, and each slot holds exactly one value. But a group
can hold many rows, with many different values in a column. To fill the slot, the database would
have to pick one of those values. Any pick would be a guess. A guess gives a wrong answer with no
warning.

The rule turns that hidden guess into a loud error.

### Key points

- Every `SELECT` column must be a grouping key, or be inside an aggregate function.
- A grouping key has exactly one value per group. That is how the group was made.
- An aggregate function squeezes all the rows of a group into one value.
- Any other column may hold many values in a group, so the database refuses to guess.
- The error offers two ways out, and they answer **different questions**. Pick the one that
  matches your question.
- Wrapping two columns in `MIN` or `MAX` can pair values from **different rows**. There is no
  error, but the answer is wrong.
- The rule applies with no `GROUP BY` too. Then the whole table is one group.
- The rule is checked on the query's shape, before any data is read.
- The error reports one column at a time.

### How it works

Think of a teacher who sorted exam papers into one stack per class. Now she writes one line per
class on a summary sheet, such as "Class A: 30 papers, top mark 95".

- She can write the **class name**. Every paper in the stack has the same class. That is how the
  stack was made.
- She can write a **count** or the **top mark**. Each one squeezes the whole stack into one value.
- She **cannot** write "the student's name" on the class line. The stack holds 30 different names.
  Which one goes on the line?

For each column in `SELECT`, the database asks one question: "Is there exactly one value for
this, per group?"

1. **A grouping key: yes.** Every row in a group shares the same key value.
2. **An aggregate function: yes.** It squeezes all the rows of the group into one value.
3. **Anything else: not guaranteed.** The group may hold many different values, so Postgres
   refuses.

The question applied to the group for Monsoon Rock Night, in
`SELECT event_id, name, COUNT(*) ... GROUP BY event_id`:

| Column in `SELECT` | Values in the Monsoon Rock Night group | Exactly one value? |
| --- | --- | --- |
| `event_id` | `...0001`, `...0001`, `...0001` | **Yes.** It is the grouping key. |
| `COUNT(*)` | 3 rows, squeezed into `3` | **Yes.** It is an aggregate function. |
| `name` | General, Balcony, VIP | **No.** There are three different values. |

### Example — real run: the error

```text
SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY event_id;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY eve...
                         ^
```

Reading it piece by piece:

| Part of the error | Plain meaning |
| --- | --- |
| `column "price_tier.name"` | The column that broke the rule: `name`, from the `price_tier` table. |
| `must appear in the GROUP BY clause` | Way out 1: make it a grouping key. |
| `or be used in an aggregate function` | Way out 2: wrap it in an aggregate function. |
| `LINE 1: ... ^` | The caret points at `name` in the `SELECT`. |

The error is not about bad spelling. It is the database saying: "this group holds many names, and
you gave me one slot".

### Walkthrough — the two ways out, and what each one means

The two ways out answer different questions. Pick the one that matches the question you are
asking, not the one that silences the error fastest.

**Way out 1: make `name` a grouping key.**

```text
SELECT event_id, name, COUNT(*) AS tiers
FROM price_tier
GROUP BY event_id, name
ORDER BY event_id, name;

               event_id               |    name     | tiers
--------------------------------------+-------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 | Balcony     |     1
 aaaaaaaa-0000-0000-0000-000000000001 | General     |     1
 aaaaaaaa-0000-0000-0000-000000000001 | VIP         |     1
 aaaaaaaa-0000-0000-0000-000000000002 | General     |     1
 aaaaaaaa-0000-0000-0000-000000000003 | Front Table |     1
 aaaaaaaa-0000-0000-0000-000000000003 | General     |     1
(6 rows)
```

It runs. But the shape is 6 rows, and every count is 1. Step by step:

1. With two grouping keys, a group is now "one event **and** one tier name".
2. The table has a rule, `price_tier_name_unique_per_event`, that one event cannot have two tiers
   with the same name.
3. So every group holds exactly one row, and every count is 1.

The query no longer answers "tiers per event". It answers "tiers per event per name". The question
changed without anyone noticing.

**Way out 2: put `name` inside an aggregate function.**

`string_agg(name, ', ')` is an aggregate function. It joins the text values of a group into one
text, with `', '` between them. The `ORDER BY amount` inside the brackets sets the order of the
names in that text.

```text
SELECT event_id, string_agg(name, ', ' ORDER BY amount) AS tier_names, COUNT(*) AS tiers
FROM price_tier
GROUP BY event_id
ORDER BY event_id;

               event_id               |      tier_names       | tiers
--------------------------------------+-----------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 | General, Balcony, VIP |     3
 aaaaaaaa-0000-0000-0000-000000000002 | General               |     1
 aaaaaaaa-0000-0000-0000-000000000003 | General, Front Table  |     2
(3 rows)
```

Still 3 rows, one per event. The names were squeezed into one value, so the slot has exactly one
value again. This answers "tiers per event, and what are they called?".

| Way out | Groups | Rows | Question it answers |
| --- | --- | --- | --- |
| Add `name` to `GROUP BY` | One per event **and** name | 6 | Tiers per event per name |
| Wrap `name` in `string_agg` | One per event | 3 | Tiers per event, with their names listed |

### The trap: an aggregate function that silences the error but lies

The question: "the cheapest tier of each event, with its name". It is tempting to wrap both
columns in `MIN`:

```text
SELECT event_id, MIN(name) AS min_name, MIN(amount) AS min_amount
FROM price_tier
GROUP BY event_id
ORDER BY event_id;

               event_id               |  min_name   | min_amount
--------------------------------------+-------------+------------
 aaaaaaaa-0000-0000-0000-000000000001 | Balcony     |  1499.0000
 aaaaaaaa-0000-0000-0000-000000000002 | General     |   999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Front Table |  1999.0000
(3 rows)
```

The first row reads as "Balcony costs 1499". The real prices for that event:

```text
SELECT name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001'
ORDER BY amount;

  name   |  amount
---------+-----------
 General | 1499.0000
 Balcony | 2499.0000
 VIP     | 4999.0000
(3 rows)
```

Balcony costs **2499**. The 1499 belongs to **General**. The third row is wrong in the same way:
Front Table costs 3499, not 1999. The 1999 belongs to General.

Why it happened:

1. Each aggregate function looks at **its own column**, on its own.
2. `MIN(name)` picked the name that comes first alphabetically: Balcony.
3. `MIN(amount)` picked the smallest amount: 1499, which is General's price.
4. The two values came from **different rows**, but now sit side by side in one result row.

There is no error, and the answer looks reasonable. A plausible wrong answer like this is the
dangerous kind. "The name of the cheapest tier for each event" needs a sub-query, which comes
later in this task.

### Edge cases

**Edge case 1: the rule applies with no `GROUP BY` too.**

```text
SELECT name, COUNT(*) FROM event;

ERROR:  column "event.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT name, COUNT(*) FROM event;
               ^
```

With no `GROUP BY`, the whole table is one group. That one group holds 4 names, and there is one
slot.

**Edge case 2: the rule is checked before any data is read.** Classical Evening (`...0002`) has
exactly one tier:

```text
SELECT event_id, name FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002';

               event_id               |  name
--------------------------------------+---------
 aaaaaaaa-0000-0000-0000-000000000002 | General
(1 row)
```

Its group would hold only one name. Postgres still refuses:

```text
SELECT event_id, name, COUNT(*) FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002'
GROUP BY event_id;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT event_id, name, COUNT(*) FROM price_tier WHERE event_...
                         ^
```

The rule is about what **could** happen, not what happens today. Postgres checks the query's
shape before it looks at a single row. A query that worked only because of today's data would
break silently tomorrow.

**Edge case 3: the error names one column at a time.** This query breaks the rule twice, with
`name` and with `amount`:

```text
SELECT event_id, name, amount, COUNT(*) FROM price_tier GROUP BY event_id;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT event_id, name, amount, COUNT(*) FROM price_tier GROU...
                         ^
```

Fix `name`, and the error moves to the next column:

```text
SELECT event_id, string_agg(name, ', ' ORDER BY amount) AS tier_names, amount, COUNT(*)
FROM price_tier GROUP BY event_id;

ERROR:  column "price_tier.amount" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: ...ng_agg(name, ', ' ORDER BY amount) AS tier_names, amount, CO...
                                                             ^
```

The `amount` inside `string_agg(... ORDER BY amount)` was fine. It is inside an aggregate
function. Only the bare `amount` broke the rule.

### Important terms

| Term | Meaning |
| --- | --- |
| Grouping-key rule | Every `SELECT` column must be a grouping key or be inside an aggregate function. |
| Grouping key | A column named in `GROUP BY`. It has exactly one value per group. |
| Slot | The place for one column in one result row. It holds exactly one value. |
| `string_agg(column, ', ')` | An aggregate function. It joins the text values of a group into one text, with `', '` between them. |
| `string_agg(column, ', ' ORDER BY other)` | The same, with the values in the order of `other`. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Adding the column to `GROUP BY` just to silence the error | It runs, but the groups change: 6 rows of count 1 instead of 3 rows. | First ask "per what?". Add a grouping key only if the question is "per" that column too. |
| Wrapping the column in `MIN` or `MAX` just to silence the error | Values from different rows sit together: "Balcony, 1499". | Use an aggregate function only if it answers your question. For "the name of the cheapest tier", use a sub-query, taught later. |
| Thinking the rule applies only with `GROUP BY` | `SELECT name, COUNT(*) FROM event` fails too. | With no `GROUP BY`, the whole table is one group. |
| Thinking "my data has one value per group, so it is safe" | Postgres refuses anyway. | The rule is checked on the query's shape, before any data is read. |
| Fixing one column and expecting the error to be gone | The error moves to the next column. | The error reports one column at a time. |

### Where it is used

- Every report query that groups obeys this rule. This error comes up often.
- When it appears, ask one question. "Do I want one row per this column too? Then make it a
  grouping key. Or do I want its values squeezed into one? Then use an aggregate function."
- In code review, watch for `MIN` or `MAX` wrapped around a text column. Often it was added only to
  silence this error, and it pairs values from different rows.

### Link to the task

This is the error part of **S2**: "Then try to select a column you did not group by and read the
error. The error is telling you something real, which is that the database cannot pick one value
out of a bucket of many."

It also answers **checkpoint question 2**: "Why must every non-aggregated column in the `SELECT`
appear in the `GROUP BY`?". Each result row has one slot per column. A grouping key has one value
per group by definition, and an aggregate function squeezes a group into one value. Any other
column may hold many values in a group, and the database refuses to guess which one you meant.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — trigger the error and read it.**

```sql
SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY event_id;
```

Expected failure:

```text
ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
```

**Step 2 — the same error with no `GROUP BY`.**

```sql
SELECT name, COUNT(*) FROM event;
```

Expected failure:
`ERROR:  column "event.name" must appear in the GROUP BY clause or be used in an aggregate function`

**Step 3 — way out 1: make `name` a grouping key.**

```sql
SELECT event_id, name, COUNT(*) AS tiers
FROM price_tier
GROUP BY event_id, name
ORDER BY event_id, name;
```

Expected: 6 rows, and every `tiers` value is `1`.

**Step 4 — way out 2: squeeze the names into one value.**

```sql
SELECT event_id, string_agg(name, ', ' ORDER BY amount) AS tier_names, COUNT(*) AS tiers
FROM price_tier
GROUP BY event_id
ORDER BY event_id;
```

Expected: 3 rows. The first shows `General, Balcony, VIP | 3`.

**Step 5 — the trap.**

```sql
SELECT event_id, MIN(name) AS min_name, MIN(amount) AS min_amount
FROM price_tier
GROUP BY event_id
ORDER BY event_id;
```

Expected: the first row shows `Balcony | 1499.0000`. Then check the real prices:

```sql
SELECT name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001'
ORDER BY amount;
```

Expected: `General | 1499.0000`, `Balcony | 2499.0000`, `VIP | 4999.0000`. So Balcony does not
cost 1499.

**Step 6 — a one-row group is still refused.**

```sql
SELECT event_id, name, COUNT(*) FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000002'
GROUP BY event_id;
```

Expected failure: the same `must appear in the GROUP BY clause` error.

Leave the prompt with `\q`.

**Done when:** you have read the error in Step 1 and can say what each part means. You can explain
why Step 3 gives 6 rows of 1, and why the first row of Step 5 is a lie.

### Summary for revision

- Every `SELECT` column must be a grouping key or be inside an aggregate function.
- The reason: one slot per column per result row, and a group can hold many values.
- Way out 1, adding to `GROUP BY`, changes the groups, and so changes the question.
- Way out 2, an aggregate function, keeps the groups and squeezes the values into one.
- `MIN(a)` and `MIN(b)` in one row can come from different rows. No error, wrong pairing.
- No `GROUP BY` means one group, and the rule still applies.
- The rule is checked before any data is read, and the error names one column at a time.

### Check questions, with answers

**Question 1.** Here is a table called `employee`:

```text
 id | department | name  | salary
----+------------+-------+--------
  1 | Sales      | Asha  |  50000
  2 | Sales      | Ravi  |  40000
  3 | IT         | Meena |  70000
```

Someone runs this query:

```sql
SELECT department, name, SUM(salary) FROM employee GROUP BY department;
```

(a) What happens, and why? (b) Describe the two ways out of the error. For each one, say what the
result would look like and which question it answers.

**Answer.** (a) Postgres refuses to run it:

```text
ERROR:  column "employee.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT department, name, SUM(salary) FROM employee GROUP BY ...
                           ^
```

`GROUP BY department` makes two groups. The Sales group holds two rows, Asha and Ravi. The result
has one row for Sales, with one slot for `name`. There are two names and one slot, and the
database will not pick one. The IT group holds only Meena, but that does not help. The rule is
checked on the query's shape, before any data is read.

(b) **Way out 1: add `name` to `GROUP BY`.** Each group becomes one department and one name. Each
person is in their own group, so the "total" is just each person's own salary. 3 rows. It answers
"salary per department per person", which is not a total per department any more.

```text
SELECT department, name, SUM(salary) FROM employee GROUP BY department, name ORDER BY department, name;

 department | name  |  sum
------------+-------+-------
 IT         | Meena | 70000
 Sales      | Asha  | 50000
 Sales      | Ravi  | 40000
(3 rows)
```

**Way out 2: wrap `name` in `string_agg`.** The groups stay one per department, and the names are
squeezed into one text. 2 rows. It answers "total salary per department, and who works there".

```text
SELECT department, string_agg(name, ', ' ORDER BY name) AS names, SUM(salary)
FROM employee GROUP BY department ORDER BY department;

 department |   names    |  sum
------------+------------+-------
 IT         | Meena      | 70000
 Sales      | Asha, Ravi | 90000
(2 rows)
```

Real runs, in a throwaway table with the same three rows.

**Question 2.** Here is the same `employee` table again:

```text
 id | department | name  | salary
----+------------+-------+--------
  1 | Sales      | Asha  |  50000
  2 | Sales      | Ravi  |  40000
  3 | IT         | Meena |  70000
```

A teammate wants "the highest-paid person in each department" and writes:

```sql
SELECT department, MAX(name) AS top_earner, MAX(salary) AS top_salary
FROM employee
GROUP BY department
ORDER BY department;
```

What does it return for Sales? Is that answer true? Explain why or why not.

**Answer.** For Sales it returns `Ravi | 50000`. That is **not** true. Ravi earns 40000. The
person who earns 50000 is Asha.

```text
 department | top_earner | top_salary
------------+------------+------------
 IT         | Meena      |      70000
 Sales      | Ravi       |      50000
(2 rows)
```

Each aggregate function looks at its own column, on its own. `MAX(name)` picks the name that comes
last alphabetically. R comes after A, so it picks Ravi. `MAX(salary)` picks the biggest salary,
50000, which is Asha's. The two values come from different rows. The query runs with no error,
and the answer looks reasonable, which is what makes it dangerous. The IT row happens to be right
only because IT has one person. Getting the right person needs a sub-query, which comes later in
this task.

The real rows, to check:

```text
SELECT department, name, salary FROM employee ORDER BY department, salary DESC;

 department | name  | salary
------------+-------+--------
 IT         | Meena |  70000
 Sales      | Asha  |  50000
 Sales      | Ravi  |  40000
(3 rows)
```

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here is a table called `book`:

```text
 id | author | title   | price
----+--------+---------+-------
  1 | Kiran  | Rivers  |   500
  2 | Kiran  | Deserts |   300
  3 | Leela  | Islands |   700
```

Which of these five queries run, and which fail? For each one that fails, name the column the
error will complain about.

```sql
-- (a)
SELECT author, COUNT(*) FROM book GROUP BY author ORDER BY author;
-- (b)
SELECT author, title FROM book GROUP BY author;
-- (c)
SELECT author, MAX(price) FROM book GROUP BY author ORDER BY author;
-- (d)
SELECT COUNT(*) FROM book GROUP BY author ORDER BY author;
-- (e)
SELECT title, COUNT(*) FROM book;
```

<details>
<summary>Answer</summary>

- **(a) Runs.** `author` is the grouping key, and `COUNT(*)` is an aggregate function. Result:
  `Kiran | 2`, `Leela | 1`.
- **(b) Fails, on `book.title`.** There is no aggregate function in this query at all, but it
  groups, so the rule applies. Kiran's group holds two titles, and there is one slot.
- **(c) Runs.** `MAX(price)` is an aggregate function. Result: `Kiran | 500`, `Leela | 700`.
- **(d) Runs.** The rule is only about columns in `SELECT`. A grouping key may be left out of
  `SELECT`, and it can still be used in `ORDER BY`. Result: `2`, then `1`, with no labels.
- **(e) Fails, on `book.title`.** There is no `GROUP BY`, so the whole table is one group, holding
  three titles.

Real run of the two failures:

```text
ERROR:  column "book.title" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT author, title FROM book GROUP BY author;
                       ^

ERROR:  column "book.title" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT title, COUNT(*) FROM book;
               ^
```

</details>

**Practice 2.** Here is the same `book` table again:

```text
 id | author | title   | price
----+--------+---------+-------
  1 | Kiran  | Rivers  |   500
  2 | Kiran  | Deserts |   300
  3 | Leela  | Islands |   700
```

A teammate wants "how many books each author has, and what their titles are". They wrote this
query and got an error:

```sql
SELECT author, title, COUNT(*) FROM book GROUP BY author;
```

Which way out matches their question? Write the fixed query and give its output. Then say what the
other way out would return, and why it does not answer the question.

<details>
<summary>Answer</summary>

The question is "per author", so the groups must stay one per author. That means way out 2:
squeeze the titles into one value with `string_agg`.

```sql
SELECT author, string_agg(title, ', ' ORDER BY title) AS titles, COUNT(*) AS books
FROM book GROUP BY author ORDER BY author;
```

```text
 author |     titles      | books
--------+-----------------+-------
 Kiran  | Deserts, Rivers |     2
 Leela  | Islands         |     1
(2 rows)
```

Way out 1, `GROUP BY author, title`, makes one group per author **and** title. Every group holds
one book, so every count is 1:

```text
 author |  title  | books
--------+---------+-------
 Kiran  | Deserts |     1
 Kiran  | Rivers  |     1
 Leela  | Islands |     1
(3 rows)
```

It runs, but it no longer says that Kiran has 2 books.

</details>

**Practice 3.** Here is the same `book` table again:

```text
 id | author | title   | price
----+--------+---------+-------
  1 | Kiran  | Rivers  |   500
  2 | Kiran  | Deserts |   300
  3 | Leela  | Islands |   700
```

Leela has only one book. Will this query run? Why or why not?

```sql
SELECT author, title, COUNT(*) FROM book WHERE author = 'Leela' GROUP BY author;
```

<details>
<summary>Answer</summary>

No. It fails, even though Leela's group would hold just one title:

```text
ERROR:  column "book.title" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT author, title, COUNT(*) FROM book WHERE author = 'Lee...
                       ^
```

The rule is checked on the query's shape, before any data is read. `title` is not a grouping key
and is not inside an aggregate function, so the query is refused whatever the data holds.

</details>

### Your doubts

None for this lesson.

---

## Lesson 7 — Grouping across a join: events per city

### Definition

**Grouping across a join** means two steps, in this order:

1. Join the tables, so that each row carries all the columns you need.
2. Group those joined rows.

The grouping key can come from one table, while the rows you count come from another.

A quick reminder of what a join is. A **join** puts rows of two tables side by side. This one:

```sql
FROM event e JOIN venue v ON v.id = e.venue_id
```

pairs each event with the venue whose `id` matches the event's `venue_id`. `e` and `v` are
**table aliases**, which are short names for the tables. So `v.city` means "the `city` column of
`venue`". The result is one **joined row** per event, holding the event's columns and its venue's
columns together. A plain `JOIN` is also called an **inner join**. It keeps only the rows that
found a match.

### Why it is needed

The question "how many events are in each city?" needs two things:

- **The events**, to count. They live in `event`.
- **The city**, to group by. It lives in `venue`.

No single table has both. `event` has no `city` column, and `venue` has the city but no events.
The join brings them together, so the grouping can happen.

### Key points

- Join first, then group. `GROUP BY` only ever sees the joined rows.
- The grouping key can come from any table in the join, such as `v.city`.
- `COUNT(*)` counts whatever rows `FROM` and `JOIN` built. To count events, each row must be one
  event.
- An inner join drops a venue with no events. Its city never becomes a group, so it has no result
  row. It does not show 0.
- To show 0, start from the parent table with a `LEFT JOIN`, and count the child's primary key:
  `COUNT(e.id)`. `COUNT(*)` would say 1.
- Forgetting the join gives a quick error. Counting from the wrong table gives a wrong answer with
  no error.

### How it works

Think of a pile of exam papers that show only a student number. The class each student is in is
written on a separate class list. To count papers per class:

1. Look up each student on the class list, and write their class on their paper. This is the join.
2. Sort the papers into one stack per class. This is the grouping.
3. Count each stack. This is the aggregate function.

The query:

```sql
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city;
```

It runs in this order:

1. **`FROM ... JOIN`** builds the joined rows: one per event, each with its venue's city attached.
2. **`GROUP BY v.city`** sorts the joined rows into groups by city.
3. **`COUNT(*)`** counts the joined rows in each group. Each joined row is one event, so this
   counts events.
4. **One result row per city.**

### Example — real run

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY v.city;

   city    | events
-----------+--------
 Hyderabad |      2
 Mumbai    |      2
(2 rows)
```

### Walkthrough

**Step 1, the joined rows.** The same join, with no grouping, makes them visible:

```text
SELECT v.city, e.name AS event
FROM event e JOIN venue v ON v.id = e.venue_id
ORDER BY v.city, e.name;

   city    |       event
-----------+--------------------
 Hyderabad | Classical Evening
 Hyderabad | Monsoon Rock Night
 Mumbai    | Jazz at the Note
 Mumbai    | Open Mic Night
(4 rows)
```

There are 4 events and 4 joined rows. Each event now carries its venue's city.

**Step 2, `GROUP BY v.city`.**

| Group (`v.city`) | Joined rows in the group |
| --- | --- |
| Hyderabad | Classical Evening, Monsoon Rock Night |
| Mumbai | Jazz at the Note, Open Mic Night |

**Step 3, `COUNT(*)` per group.** Hyderabad gets 2 and Mumbai gets 2.

**Step 4, one result row per group.** There are 2 groups, so there are 2 rows.

### Where Chennai went, and how to get it back with 0

There are three venues. Lakeside Hall, in Chennai, hosts no events. An inner join keeps only rows
that found a match, and Lakeside Hall matched no event, so it made no joined row. With no rows,
Chennai never becomes a group, so it has no result row. It does not show 0. It is simply not
there.

Whether that is wrong depends on the question:

- "Which cities have events, and how many?" Chennai should not appear.
- "For every city with a venue, how many events?" Chennai should appear, with 0.

**Getting Chennai with 0: start from `venue`, and use `LEFT JOIN`.** A **`LEFT JOIN`** keeps every
row of the left table, which is the table written before `LEFT JOIN`, even when it finds no
match. When there is no match, the right table's columns are filled with `NULL`, which means "no
value". After `\pset null '«NULL»'`, psql prints it as `«NULL»`.

```text
SELECT v.city, e.id AS event_id, e.name AS event
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
ORDER BY v.city, e.name;

   city    |               event_id               |       event
-----------+--------------------------------------+--------------------
 Chennai   | «NULL»                               | «NULL»
 Hyderabad | aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening
 Hyderabad | aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night
 Mumbai    | aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note
 Mumbai    | aaaaaaaa-0000-0000-0000-000000000004 | Open Mic Night
(5 rows)
```

Chennai now has a joined row, with an empty event side. Group and count, two ways:

```text
SELECT v.city, COUNT(*) AS count_star, COUNT(e.id) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city
ORDER BY v.city;

   city    | count_star | events
-----------+------------+--------
 Chennai   |          1 |      0
 Hyderabad |          2 |      2
 Mumbai    |          2 |      2
(3 rows)
```

- **`COUNT(*)` counts rows.** Chennai's group holds one row: the venue with the empty event side.
  So it says 1, which is wrong. Chennai has no events.
- **`COUNT(e.id)` counts values, and skips `NULL`.** In Chennai's row, `e.id` is `NULL`, so it says
  0, which is right.

Why `e.id` and not some other event column: `e.id` is the primary key of `event`, so in the real
table it is never `NULL`. After the join, a `NULL` there can only mean "this venue had no event".

**Which table goes on the left matters.** The left table is the one whose rows must all survive.
With `event` on the left, every event survives, but nothing keeps Lakeside Hall, because it has no
events:

```text
SELECT v.city, COUNT(e.id) AS events
FROM event e LEFT JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY v.city;

   city    | events
-----------+--------
 Hyderabad |      2
 Mumbai    |      2
(2 rows)
```

To keep every city, `venue` must be on the left.

### Two ways to get it wrong

This is what the task means by "forgetting which table owns a column stops being a quick error
message and starts producing wrong-shaped results".

**Wrong 1: forgetting the join gives a quick error.**

```text
SELECT city, COUNT(*) FROM event GROUP BY city;

ERROR:  column "city" does not exist
LINE 1: SELECT city, COUNT(*) FROM event GROUP BY city;
               ^
```

The same mistake, written with an alias:

```text
SELECT v.city, COUNT(*) FROM event e GROUP BY v.city;

ERROR:  missing FROM-clause entry for table "v"
LINE 1: SELECT v.city, COUNT(*) FROM event e GROUP BY v.city;
               ^
```

| Error | Plain meaning |
| --- | --- |
| `column "city" does not exist` | The `event` table has no column called `city`. |
| `missing FROM-clause entry for table "v"` | The query uses `v.city`, but no table called `v` is in `FROM`. `venue` was never joined. |

Both errors are loud and quick to fix.

**Wrong 2: counting the wrong table gives no error and a wrong answer.** Someone "fixes" the error
the fast way. They reason that `city` lives on `venue`, so they query `venue`:

```text
SELECT v.city, COUNT(*) AS events FROM venue v GROUP BY v.city ORDER BY v.city;

   city    | events
-----------+--------
 Chennai   |      1
 Hyderabad |      1
 Mumbai    |      1
(3 rows)
```

It runs, and the column says `events`. But Hyderabad has 2 events, not 1. Here is why:

1. `COUNT(*)` counts the rows that `FROM` built.
2. Here, `FROM` built venue rows, one per venue.
3. So this counts **venues** per city.

The label `AS events` is only a name. It does not change what is counted.

**One more thing: `GROUP BY` does not sort, across a join either.** The events-per-city query with
no `ORDER BY`, real run:

```text
SELECT v.city, COUNT(*) AS events FROM event e JOIN venue v ON v.id = e.venue_id GROUP BY v.city;

   city    | events
-----------+--------
 Mumbai    |      2
 Hyderabad |      2
(2 rows)
```

### Important terms

| Term | Meaning |
| --- | --- |
| Grouping across a join | Join the tables first, then group the joined rows. |
| Joined row | One row of a join's result, holding columns from both tables. |
| Inner join (`JOIN`) | Keeps only the rows that found a match. |
| `LEFT JOIN` | Keeps every row of the left table. With no match, the right table's columns are `NULL`. |
| Left table | The table written before `LEFT JOIN`. All its rows survive. |
| Child table | The table holding the pointer, such as `event` with its `venue_id`. |
| Parent table | The table being pointed at, such as `venue`. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Grouping by `city` without joining `venue` | `ERROR: column "city" does not exist` | Join `venue`, then group by `v.city`. |
| Writing `v.city` with `venue` missing from `FROM` | `ERROR: missing FROM-clause entry for table "v"` | Add `JOIN venue v ON v.id = e.venue_id`. |
| Counting from `venue` alone | 1, 1, 1. It counts venues, not events, with no error. | Make each row one event: start from `event` and join `venue`. |
| Expecting Chennai with 0 from an inner join | Chennai is missing. | `venue v LEFT JOIN event e`, with `COUNT(e.id)`. |
| `LEFT JOIN` with `COUNT(*)` | Chennai shows 1. | Count the child's primary key: `COUNT(e.id)`. |
| `event` on the left of the `LEFT JOIN` | Chennai is still missing: only Hyderabad 2 and Mumbai 2. | The table whose rows must all survive goes on the left. Here that is `venue`. |
| Grouping by the venue (`v.name` or `v.id`) when the question says "per city" | One row per venue. Two venues in one city would become two rows. | Group by the column the question names: `v.city`. |

### Where it is used

The label you group by often lives in a different table from the rows you count:

- Orders per country, where the country lives on the customer table.
- Sales per product category, where the category lives on the product table.
- Events per city, where the city lives on the venue table.

The rows being counted usually live in the child table. The label usually lives in the parent
table.

### Link to the task

This is the "events per city" part of **S2**: "notice this one requires the join, because `city`
lives on `venue`; grouping is where forgetting which table owns a column stops being a quick error
message and starts producing wrong-shaped results". The two errors are the quick error messages.
The 1, 1, 1 result is the wrong-shaped result.

It also prepares **S7**, "venues with no events". The Chennai row with an empty event side is
exactly the row S7 looks for, using `WHERE e.id IS NULL`.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — the quick errors.**

```sql
SELECT city, COUNT(*) FROM event GROUP BY city;
SELECT v.city, COUNT(*) FROM event e GROUP BY v.city;
```

Expected failures: `ERROR:  column "city" does not exist`, then
`ERROR:  missing FROM-clause entry for table "v"`.

**Step 3 — see the joined rows.**

```sql
SELECT v.city, e.name AS event
FROM event e JOIN venue v ON v.id = e.venue_id
ORDER BY v.city, e.name;
```

Expected: 4 rows, two for Hyderabad and two for Mumbai.

**Step 4 — events per city.**

```sql
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY v.city;
```

Expected: `Hyderabad | 2` and `Mumbai | 2`. There is no Chennai.

**Step 5 — the wrong table.**

```sql
SELECT v.city, COUNT(*) AS events FROM venue v GROUP BY v.city ORDER BY v.city;
```

Expected: three rows, all showing `1`. This counts venues, not events.

**Step 6 — the `LEFT JOIN` rows.**

```sql
SELECT v.city, e.id AS event_id, e.name AS event
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
ORDER BY v.city, e.name;
```

Expected: 5 rows. The first is `Chennai | «NULL» | «NULL»`.

**Step 7 — `COUNT(*)` against `COUNT(e.id)`.**

```sql
SELECT v.city, COUNT(*) AS count_star, COUNT(e.id) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city
ORDER BY v.city;
```

Expected: `Chennai | 1 | 0`, `Hyderabad | 2 | 2`, `Mumbai | 2 | 2`.

Leave the prompt with `\q`.

**Done when:** Step 4 gives events per city. You can say why Step 5 prints 1 for every city, and
why Chennai shows 1 under `count_star` but 0 under `events` in Step 7.

### Summary for revision

- Join first, then group. The grouping key can come from the parent table, such as `v.city`.
- `COUNT(*)` counts the rows that `FROM` and `JOIN` built. Make each row one event.
- Forgetting the join gives a quick error. Counting from `venue` alone gives 1, 1, 1 with no
  error.
- An inner join drops Chennai. `venue LEFT JOIN event` keeps it, and `COUNT(e.id)` gives it 0.
- With a `LEFT JOIN`, count the child's primary key. `COUNT(*)` says 1 for an empty parent.
- The table whose rows must all survive goes on the left.

### Check questions, with answers

**Question 1.** Here are two tables. `team` is the parent and `player` is the child.

```text
team:
 id |  name  | city
----+--------+-------
  1 | Tigers | Pune
  2 | Lions  | Pune
  3 | Eagles | Delhi
  4 | Wolves | Goa

player:
 id | team_id | name
----+---------+-------
  1 |       1 | Asha
  2 |       1 | Ravi
  3 |       2 | Meena
  4 |       3 | Kiran
```

Here are two queries:

```sql
SELECT t.city, COUNT(*) AS players
FROM player p JOIN team t ON t.id = p.team_id
GROUP BY t.city ORDER BY t.city;

SELECT t.name, COUNT(*) AS players
FROM player p JOIN team t ON t.id = p.team_id
GROUP BY t.name ORDER BY t.name;
```

(a) What does each one return? (b) Which one answers "how many players are there per city?", and
why does the other one not?

**Answer.** (a) The joined rows first. Each player now carries their team's name and city:

```text
 city  |  team  | player
-------+--------+--------
 Delhi | Eagles | Kiran
 Pune  | Lions  | Meena
 Pune  | Tigers | Asha
 Pune  | Tigers | Ravi
(4 rows)
```

The first query groups them by city:

```text
 city  | players
-------+---------
 Delhi |       1
 Pune  |       3
(2 rows)
```

The second query groups them by team name:

```text
  name  | players
--------+---------
 Eagles |       1
 Lions  |       1
 Tigers |       2
(3 rows)
```

(b) The first one answers "per city". It groups by `t.city`, so the Tigers and the Lions, both in
Pune, share one group: 2 + 1 = 3. The second one answers "per team". Pune is split across two
rows, and the city is not shown at all. Goa is missing from both, because the Wolves have no
players and an inner join drops them. Real runs, in throwaway tables with the same rows.

**Question 2.** Here are the same two tables again:

```text
team:
 id |  name  | city
----+--------+-------
  1 | Tigers | Pune
  2 | Lions  | Pune
  3 | Eagles | Delhi
  4 | Wolves | Goa

player:
 id | team_id | name
----+---------+-------
  1 |       1 | Asha
  2 |       1 | Ravi
  3 |       2 | Meena
  4 |       3 | Kiran
```

The Wolves, in Goa, have no players. Write a query that shows players per city for **every** city,
with Goa showing 0. Then say what Goa would show if you used `COUNT(*)` instead, and why.

**Answer.** Start from `team`, so every team survives, and count the player's primary key:

```sql
SELECT t.city, COUNT(p.id) AS players
FROM team t LEFT JOIN player p ON p.team_id = t.id
GROUP BY t.city ORDER BY t.city;
```

```text
 city  | players
-------+---------
 Delhi |       1
 Goa   |       0
 Pune  |       3
(3 rows)
```

With `COUNT(*)`, Goa shows **1**:

```text
 city  | players
-------+---------
 Delhi |       1
 Goa   |       1
 Pune  |       3
(3 rows)
```

The joined rows explain why. The Wolves survive the `LEFT JOIN` as one row with an empty player
side:

```text
 city  |  team  | player_id | player
-------+--------+-----------+--------
 Delhi | Eagles |         4 | Kiran
 Goa   | Wolves |    «NULL» | «NULL»
 Pune  | Lions  |         3 | Meena
 Pune  | Tigers |         1 | Asha
 Pune  | Tigers |         2 | Ravi
(5 rows)
```

`COUNT(*)` counts rows, and Goa's group holds that one row, so it says 1. `COUNT(p.id)` counts
values and skips `NULL`. In Goa's row `p.id` is `NULL`, so it says 0.

### Practice questions

Each question below has everything needed to answer it. Try it before opening the answer.

**Practice 1.** Here are two tables, `team` (the parent) and `player` (the child):

```text
team:
 id |  name  | city
----+--------+-------
  1 | Tigers | Pune
  2 | Lions  | Pune
  3 | Eagles | Delhi
  4 | Wolves | Goa

player:
 id | team_id | name
----+---------+-------
  1 |       1 | Asha
  2 |       1 | Ravi
  3 |       2 | Meena
  4 |       3 | Kiran
```

Someone wants players per city, and writes:

```sql
SELECT t.city, COUNT(*) AS players FROM team t GROUP BY t.city ORDER BY t.city;
```

What does it return, and what does it actually count?

<details>
<summary>Answer</summary>

```text
 city  | players
-------+---------
 Delhi |       1
 Goa   |       1
 Pune  |       2
(3 rows)
```

It counts **teams** per city, not players. `FROM team` builds one row per team, and `COUNT(*)`
counts those rows. The label `AS players` does not change what is counted. Pune really has 3
players, and Goa has none. There is no error, which is what makes it dangerous.

</details>

**Practice 2.** Here are the same two tables again:

```text
team:
 id |  name  | city
----+--------+-------
  1 | Tigers | Pune
  2 | Lions  | Pune
  3 | Eagles | Delhi
  4 | Wolves | Goa

player:
 id | team_id | name
----+---------+-------
  1 |       1 | Asha
  2 |       1 | Ravi
  3 |       2 | Meena
  4 |       3 | Kiran
```

What happens when this query runs? Then write the fixed query and give its output.

```sql
SELECT city, COUNT(*) AS players FROM player GROUP BY city;
```

<details>
<summary>Answer</summary>

It fails:

```text
ERROR:  column "city" does not exist
LINE 1: SELECT city, COUNT(*) AS players FROM player GROUP BY city;
               ^
```

The `player` table has no `city` column. The city lives on `team`. The fix joins `team`, so that
each player row carries its team's city:

```sql
SELECT t.city, COUNT(*) AS players
FROM player p JOIN team t ON t.id = p.team_id
GROUP BY t.city ORDER BY t.city;
```

```text
 city  | players
-------+---------
 Delhi |       1
 Pune  |       3
(2 rows)
```

</details>

**Practice 3.** Here are the same two tables again:

```text
team:
 id |  name  | city
----+--------+-------
  1 | Tigers | Pune
  2 | Lions  | Pune
  3 | Eagles | Delhi
  4 | Wolves | Goa

player:
 id | team_id | name
----+---------+-------
  1 |       1 | Asha
  2 |       1 | Ravi
  3 |       2 | Meena
  4 |       3 | Kiran
```

Someone wants every city, with Goa showing 0. They used a `LEFT JOIN` and counted the player's
primary key, but Goa is still missing. Why?

```sql
SELECT t.city, COUNT(p.id) AS players
FROM player p LEFT JOIN team t ON t.id = p.team_id
GROUP BY t.city ORDER BY t.city;
```

<details>
<summary>Answer</summary>

```text
 city  | players
-------+---------
 Delhi |       1
 Pune  |       3
(2 rows)
```

`player` is on the left. A `LEFT JOIN` keeps every row of the left table, so every **player**
survives. The Wolves have no players, so no row carries Goa, and Goa never becomes a group. The
fix puts the table whose rows must all survive on the left:
`FROM team t LEFT JOIN player p ON p.team_id = t.id`. That gives `Delhi | 1`, `Goa | 0`,
`Pune | 3`.

</details>

### Your doubts

None for this lesson.

---

## S2 — end to end: grouping, across a join, and the error

**The subtask.** "Group it: events per city — and notice this one *requires* the join, because
`city` lives on `venue`; grouping is where forgetting which table owns a column stops being a
quick error message and starts producing wrong-shaped results — and tiers per event. Then try to
select a column you did not group by and read the error. The error is telling you something real,
which is that the database cannot pick one value out of a bucket of many."

**Concepts used.** `GROUP BY` on one table, the grouping-key rule, grouping across a join,
`LEFT JOIN`, and `COUNT(*)` versus `COUNT(column)`.

### How the concepts fit together

Three facts combine:

1. **`GROUP BY`** puts rows with the same value in the grouping key into one group. Each aggregate
   function runs once per group, and there is one result row per group.
2. **`GROUP BY` can only use columns of the rows it is given.** `event` has no `city` column, so
   `venue` must be joined first. Then each **joined row** (one row of the join's result) carries
   its venue's city.
3. **The grouping-key rule.** Every `SELECT` column must be a grouping key or be inside an
   aggregate function. Each result row has one slot per column, and a group can hold many values.

Put together:

- Tiers per event needs only `price_tier`, because `event_id` is already in that table.
- Events per city needs a join, because `city` is on `venue`.
- Any `SELECT` column that is neither a grouping key nor inside an aggregate function is refused.

### The approach in plain words

**Part A, tiers per event.**

1. Ask "per what?". The answer is per event, so the grouping key is the event id.
2. Find a table that has both the rows to count (tiers) and the key (`event_id`). `price_tier` has
   both, so no join is needed.
3. Group by `event_id`, and count.

**Part B, events per city.**

1. Ask "per what?". The answer is per city, so the grouping key is `city`.
2. The rows to count are events, in `event`. The key is in `venue`. Two tables means a join.
3. Decide whether cities with no events should appear. If not, use an inner join. If yes, use
   `venue LEFT JOIN event` and `COUNT(e.id)`.

**Part C, the error.**

1. Add a column that is neither a grouping key nor inside an aggregate function. Read the error.
2. Pick the way out that matches the question.

**Then cross-check every result.** The group counts must add up to the total count.

### The solution — real run

**Part A, tiers per event:**

```text
SELECT event_id, COUNT(*) AS tiers
FROM price_tier
GROUP BY event_id
ORDER BY event_id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000003 |     2
(3 rows)
```

**Part B, events per city, for cities that have events:**

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY v.city;

   city    | events
-----------+--------
 Hyderabad |      2
 Mumbai    |      2
(2 rows)
```

**Part B, events per city, for every city.** `venue` goes on the left, so every venue survives,
and `COUNT(e.id)` skips the empty event side:

```text
SELECT v.city, COUNT(e.id) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city
ORDER BY v.city;

   city    | events
-----------+--------
 Chennai   |      0
 Hyderabad |      2
 Mumbai    |      2
(3 rows)
```

**Part C, select a column you did not group by:**

```text
SELECT v.city, e.name, COUNT(*)
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city;

ERROR:  column "e.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT v.city, e.name, COUNT(*) FROM event e JOIN venue v ON...
                       ^
```

What the error is telling you:

1. The Hyderabad group holds two joined rows, with two event names: Classical Evening and Monsoon
   Rock Night.
2. The result row for Hyderabad has one slot for `e.name`.
3. Two names, one slot. The database will not pick one. That is the "bucket of many" in the task's
   words.

The error names the column the way the query names its table. When the table has an alias, it
uses the alias, as in `e.name`. When the table has no alias, it uses the table name:

```text
SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY event_id;

ERROR:  column "price_tier.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT event_id, name, COUNT(*) FROM price_tier GROUP BY eve...
                         ^

SELECT pt.event_id, pt.name, COUNT(*) FROM price_tier pt GROUP BY pt.event_id;

ERROR:  column "pt.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT pt.event_id, pt.name, COUNT(*) FROM price_tier pt GRO...
                            ^
```

**The two ways out, on the join query.** Wrapping `e.name` in `string_agg` keeps the question "per
city". `string_agg` is an aggregate function that joins a group's text values into one text:

```text
SELECT v.city, string_agg(e.name, ', ' ORDER BY e.name) AS event_names, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY v.city;

   city    |              event_names              | events
-----------+---------------------------------------+--------
 Hyderabad | Classical Evening, Monsoon Rock Night |      2
 Mumbai    | Jazz at the Note, Open Mic Night      |      2
(2 rows)
```

Adding `e.name` to `GROUP BY` changes the question to "per city per event", and every count
becomes 1:

```text
SELECT v.city, e.name, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city, e.name
ORDER BY v.city, e.name;

   city    |        name        | events
-----------+--------------------+--------
 Hyderabad | Classical Evening  |      1
 Hyderabad | Monsoon Rock Night |      1
 Mumbai    | Jazz at the Note   |      1
 Mumbai    | Open Mic Night     |      1
(4 rows)
```

### Key decisions and trade-offs

**Decision 1: inner join or `LEFT JOIN` for events per city?** It depends on the question.

- The inner join answers "which cities have events, and how many?". It is shorter, and has fewer
  traps.
- The `LEFT JOIN` answers "for every city with a venue, how many events?". It shows Chennai as 0,
  but it needs `venue` on the left and `COUNT(e.id)`, not `COUNT(*)`.

A missing row is easy to overlook, while a 0 is an answer the reader can see. So for a report that
people read, the `LEFT JOIN` version is usually the safer choice.

**Decision 2: group by `v.city`, not by the venue.** The question says "per city". Today, each
city has exactly one venue:

```text
SELECT v.city, COUNT(*) AS venues, string_agg(v.name, ', ') AS venue_names
FROM venue v GROUP BY v.city ORDER BY v.city;

   city    | venues |   venue_names
-----------+--------+-----------------
 Chennai   |      1 | Lakeside Hall
 Hyderabad |      1 | Riverside Arena
 Mumbai    |      1 | The Blue Note
(3 rows)
```

So grouping by the venue would give the same numbers today, by luck. When a second Hyderabad venue
opens, "per venue" would split Hyderabad into two rows. Match the query to the question, not to
today's data.

Postgres takes the same view. Even though each city has one venue today, it refuses `v.name`
beside `GROUP BY v.city`:

```text
SELECT v.city, v.name, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city;

ERROR:  column "v.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT v.city, v.name, COUNT(*) AS events FROM event e JOIN ...
                       ^
```

The rule is checked on the query's shape, before any data is read.

**Decision 3: tiers per event from `price_tier` alone, with no join.** `price_tier` already holds
both the rows to count and the key. A join would add work and another place to make mistakes. The
trade-off is that the result shows ids, not names, and Open Mic Night is missing, because it has
no tiers. If you need Open Mic Night with 0, start from `event` with a `LEFT JOIN`, group by
`e.id`, and count the tier's primary key:

```text
SELECT e.id AS event_id, COUNT(pt.id) AS tiers
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id
ORDER BY e.id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000003 |     2
 aaaaaaaa-0000-0000-0000-000000000004 |     0
(4 rows)
```

Showing event names beside the counts needs one more idea: grouping by the id, not the name. It is
the next lesson.

**Decision 4: which way out of the error.** The question is "per city", so `string_agg` is the
right way out. It keeps one row per city. Adding `e.name` to `GROUP BY` runs fine, but it answers a
different question.

### Tests

**The add-up check.** Each event belongs to exactly one city. So the per-city counts must add up
to the total number of events. If they do not, something was dropped or counted twice. It needs
no new SQL. Count the whole table, and add the group counts by hand.

```text
SELECT COUNT(*) AS all_events FROM event;

 all_events
------------
          4
(1 row)

SELECT COUNT(*) AS all_tiers FROM price_tier;

 all_tiers
-----------
         6
(1 row)
```

| Test | Query | Result | Adds up? | What it proves |
| --- | --- | --- | --- | --- |
| Normal | Tiers per event | 3, 1, 2 | 3 + 1 + 2 = 6. Yes. | No tier lost or counted twice. |
| Normal | Events per city, inner join | 2, 2 | 2 + 2 = 4. Yes. | The join gives each event its city. |
| Edge: city with no events | `venue LEFT JOIN event`, `COUNT(e.id)` | Chennai 0, 2, 2 | 0 + 2 + 2 = 4. Yes. | The zero is right, and the total still matches. |
| Edge: event with no tiers | `event LEFT JOIN price_tier`, `COUNT(pt.id)` | 3, 1, 2, 0 | 3 + 1 + 2 + 0 = 6. Yes. | The zero is right for Open Mic Night. |
| Loud failure | `SELECT v.city, e.name, COUNT(*) ... GROUP BY v.city` | Error on `e.name` | — | The grouping-key rule. |
| Loud failure | `SELECT city, COUNT(*) FROM event GROUP BY city` | `column "city" does not exist` | — | `city` lives on `venue`. |
| Silent failure | `venue LEFT JOIN event`, `COUNT(*)` | Chennai 1, 2, 2 | 1 + 2 + 2 = 5. **No.** | The add-up check catches it. |
| Silent failure | Counting from `venue` alone | 1, 1, 1 | 1 + 1 + 1 = 3. **No.** | The add-up check catches it. |
| Silent failure | `event LEFT JOIN price_tier`, `COUNT(*)` | 3, 1, 2, 1 | 3 + 1 + 2 + 1 = 7. **No.** | The add-up check catches it. |

The loud failure on `city`, real run:

```text
SELECT city, COUNT(*) FROM event GROUP BY city;

ERROR:  column "city" does not exist
LINE 1: SELECT city, COUNT(*) FROM event GROUP BY city;
               ^
```

The silent failures, real runs:

```text
SELECT v.city, COUNT(*) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city ORDER BY v.city;

   city    | events
-----------+--------
 Chennai   |      1
 Hyderabad |      2
 Mumbai    |      2
(3 rows)

SELECT v.city, COUNT(*) AS events FROM venue v GROUP BY v.city ORDER BY v.city;

   city    | events
-----------+--------
 Chennai   |      1
 Hyderabad |      1
 Mumbai    |      1
(3 rows)

SELECT e.id AS event_id, COUNT(*) AS tiers
FROM event e LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id ORDER BY e.id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000003 |     2
 aaaaaaaa-0000-0000-0000-000000000004 |     1
(4 rows)
```

None of these three gives an error. Each one looks reasonable. The add-up check is what exposes
them.

### Why it works

Three facts about the tables make the inner-join count right:

1. `event.venue_id` is `NOT NULL`, so every event points at a venue.
2. The foreign key `event_venue_fk` means that venue really exists.
3. `venue.id` is the primary key, so exactly one venue has that id.

So each event finds exactly one venue, and the join gives exactly one joined row per event. No
event is lost, and none is doubled. Each joined row then lands in exactly one city group. So
`COUNT(*)` per city counts each event exactly once, and the groups add up to 4.

Tiers work the same way. Each `price_tier` row is one tier with one `event_id`, so each tier lands
in exactly one event group, and the groups add up to 6.

The error works for you too. A city group can hold many events, so the database refuses to put one
event name in a city's row. Because the check is on the query's shape, a query that runs today
cannot start giving a made-up answer when the data changes.

### Do it yourself

**Before you start.** Do "The check to run before every lesson" at the top of this file. Expected
counts: `3`, `4`, `6`. If `docker` is missing, do "Setting all of this up from nothing" first.

**Step 1 — make `NULL` visible.**

```sql
\pset null '«NULL»'
```

Expected: `Null display is "«NULL»".`

**Step 2 — Part A, tiers per event.**

```sql
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;
```

Expected: 3 rows, with `3`, `1`, `2`. These add up to 6, which matches the tier count from the
start.

**Step 3 — Part B, events per city.**

```sql
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city ORDER BY v.city;
```

Expected: `Hyderabad | 2`, `Mumbai | 2`. These add up to 4, which matches the event count.

**Step 4 — Part B, every city.**

```sql
SELECT v.city, COUNT(e.id) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city ORDER BY v.city;
```

Expected: `Chennai | 0`, `Hyderabad | 2`, `Mumbai | 2`. These still add up to 4.

**Step 5 — Part C, the error.**

```sql
SELECT v.city, e.name, COUNT(*)
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city;
```

Expected failure:

```text
ERROR:  column "e.name" must appear in the GROUP BY clause or be used in an aggregate function
```

**Step 6 — the way out that keeps "per city".**

```sql
SELECT v.city, string_agg(e.name, ', ' ORDER BY e.name) AS event_names, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city ORDER BY v.city;
```

Expected: `Hyderabad | Classical Evening, Monsoon Rock Night | 2` and
`Mumbai | Jazz at the Note, Open Mic Night | 2`.

**Step 7 — the way out that changes the question.**

```sql
SELECT v.city, e.name, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city, e.name ORDER BY v.city, e.name;
```

Expected: 4 rows, and every `events` value is `1`.

**Step 8 — catch a silent failure with the add-up check.**

```sql
SELECT v.city, COUNT(*) AS events
FROM venue v LEFT JOIN event e ON e.venue_id = v.id
GROUP BY v.city ORDER BY v.city;
```

Expected: `Chennai | 1`, `Hyderabad | 2`, `Mumbai | 2`. Add them up: 1 + 2 + 2 = 5, but there are
only 4 events. That mismatch is the signal that this query is wrong.

Leave the prompt with `\q`.

**Done when:** Steps 2 and 3 give the two grouped answers, and both add up to the table counts.
You have read the error in Step 5 and can say which "bucket of many" it is about. You can explain
why Step 8 adds up to 5.

### Check question, with answer

**Question.** Here are two tables. `author` is the parent and `book` is the child.

```text
author:
 id | name  | country
----+-------+---------
  1 | Kiran | India
  2 | Leela | India
  3 | Sam   | UK
  4 | Mia   | France

book:
 id | author_id |  title
----+-----------+---------
  1 |         1 | Rivers
  2 |         1 | Deserts
  3 |         2 | Islands
  4 |         3 | Forests
```

(a) Write a query for "books per author id", using only the `book` table. What does it return?

(b) Write a query for "books per country", for **every** country, with France showing 0. What
does it return?

(c) Someone adds `b.title` to the `SELECT` of the query from (b), and changes nothing else. What
happens, and why?

**Answer.** (a) `book` already holds both the rows to count and the key, `author_id`, so no join
is needed:

```sql
SELECT author_id, COUNT(*) AS books FROM book GROUP BY author_id ORDER BY author_id;
```

```text
 author_id | books
-----------+-------
         1 |     2
         2 |     1
         3 |     1
(3 rows)
```

It adds up: 2 + 1 + 1 = 4 books. Mia, author 4, is missing. She has no books, and this query only
sees book rows.

(b) The country lives on `author`, so join. Every country must survive, so `author` goes on the
left, and the count uses the book's primary key:

```sql
SELECT a.country, COUNT(b.id) AS books
FROM author a LEFT JOIN book b ON b.author_id = a.id
GROUP BY a.country ORDER BY a.country;
```

```text
 country | books
---------+-------
 France  |     0
 India   |     3
 UK      |     1
(3 rows)
```

The joined rows show why:

```text
 country | author | book_id |  title
---------+--------+---------+---------
 France  | Mia    |  «NULL» | «NULL»
 India   | Kiran  |       2 | Deserts
 India   | Kiran  |       1 | Rivers
 India   | Leela  |       3 | Islands
 UK      | Sam    |       4 | Forests
(5 rows)
```

India's group holds the books of two authors, Kiran and Leela: 2 + 1 = 3. France's group holds
one row with an empty book side, and `COUNT(b.id)` skips that `NULL`, giving 0. It adds up:
0 + 3 + 1 = 4. With `COUNT(*)`, France would show 1, and the total would be 5, which is wrong.

(c) Postgres refuses it:

```text
ERROR:  column "b.title" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT a.country, b.title, COUNT(b.id) AS books FROM author ...
                          ^
```

India's group holds three titles: Rivers, Deserts and Islands. The result row for India has one
slot for `b.title`. Three titles, one slot. The database will not pick one. If the titles are
wanted, `string_agg` keeps the question "per country":

```text
SELECT a.country, string_agg(b.title, ', ' ORDER BY b.title) AS titles, COUNT(b.id) AS books
FROM author a LEFT JOIN book b ON b.author_id = a.id
GROUP BY a.country ORDER BY a.country;

 country |          titles          | books
---------+--------------------------+-------
 France  | «NULL»                   |     0
 India   | Deserts, Islands, Rivers |     3
 UK      | Forests                  |     1
(3 rows)
```

France shows `«NULL»` for its titles. Like `COUNT(column)`, `string_agg` skips `NULL`. With no
title left to join, it gives back `NULL`, the same way `SUM` over no values gives `NULL`. Real
runs, in throwaway tables with the same rows.

### Your doubts

None for this subtask.

---

## Lesson 7b — Grouping per event across a join: group by the id, not the name

From this lesson on, the notes hold the concept and its examples only. There are no check
questions, practice questions or do-it-yourself steps, as requested.

### Definition

To get one result row per event with the event's name in it, group by the event's **id**, and list
the name as a second grouping key: `GROUP BY e.id, e.name`. The id decides the groups. The name
only rides along, so people can read the result.

- A **key** is a column whose value picks out exactly one row. `event.id` is the **primary key**
  of `event`: no two events can have the same id.
- A **label** is a column meant for people to read, such as a name. Two rows can share it.

### Why it is needed

"Total tier value per event" needs the tier amounts (in `price_tier`) and the event's name (in
`event`). So it needs a join and a grouping key. A name is a label, not a key. A show that runs on
two nights is two events with the same name. Grouping by name pours both nights into one group and
adds their tiers together, with no error.

### Key points

- Group by the key (`e.id`). Show the label (`e.name`) by listing it in `GROUP BY` too.
- Adding `e.name` splits nothing, because one id has exactly one name. Adding a column to
  `GROUP BY` splits a group only if the column holds more than one value inside the group.
- **The primary-key shortcut (Postgres):** if `GROUP BY` holds a table's primary key, any other
  column of that same table may be selected without being listed.
- The shortcut works only outward from a primary key, does not look through a join, and covers
  only that table's columns.
- SQL Server and Oracle do not have the shortcut. `GROUP BY e.id, e.name` works everywhere.

### How it works

Think of a class with two students called Ravi Kumar, each with a different roll number. Sorting
answer sheets by name puts both Ravis in one stack. Sorting by roll number gives each his own
stack. The name is still written on each line, but the roll number decides the stacks.

```sql
SELECT e.id, e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name;
```

1. `FROM ... JOIN` builds one joined row per tier, each carrying its event's id and name.
2. `GROUP BY e.id, e.name` makes one group per pair of id and name, which is one group per event.
3. `SUM(pt.amount)` adds up the amounts in each group.
4. One result row per group.

`e.name` must be listed because of the grouping-key rule: every `SELECT` column must be a grouping
key or be inside an aggregate function. Compare with tier names: one event has many tier names
(Monsoon Rock Night has General, Balcony and VIP), so `GROUP BY event_id, name` on `price_tier`
splits Monsoon's one group into three.

### Example 1 — on the current data, name and id agree by luck

```text
SELECT e.id, e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name;

                  id                  |        name        |   total
--------------------------------------+--------------------+-----------
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening  |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note   | 5498.0000
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night | 8997.0000
(3 rows)

SELECT e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.name
ORDER BY e.name;

        name        |   total
--------------------+-----------
 Classical Evening  |  999.0000
 Jazz at the Note   | 5498.0000
 Monsoon Rock Night | 8997.0000
(3 rows)
```

The same totals, because today no two events share a name. Open Mic Night is missing from both,
because it has no tiers and the inner join drops it.

### Example 2 — a show that runs twice

A second night of Jazz at the Note, on 19 December, with one General tier at 1999. It was added
for this demo and removed afterwards:

```text
INSERT INTO event (id, venue_id, name, starts_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000005', '22222222-2222-2222-2222-222222222222',
        'Jazz at the Note', '2026-12-19 21:00+05:30');
INSERT 0 1

INSERT INTO price_tier (id, event_id, name, amount)
VALUES ('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000005', 'General', 1999);
INSERT 0 1

SELECT e.id, e.name, e.starts_at FROM event e WHERE e.name = 'Jazz at the Note' ORDER BY e.starts_at;

                  id                  |       name       |       starts_at
--------------------------------------+------------------+------------------------
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note | 2026-11-28 15:30:00+00
 aaaaaaaa-0000-0000-0000-000000000005 | Jazz at the Note | 2026-12-19 15:30:00+00
(2 rows)

SELECT e.id AS event_id, e.name, pt.name AS tier, pt.amount
FROM event e JOIN price_tier pt ON pt.event_id = e.id
WHERE e.name = 'Jazz at the Note'
ORDER BY e.id, pt.amount;

               event_id               |       name       |    tier     |  amount
--------------------------------------+------------------+-------------+-----------
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note | General     | 1999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note | Front Table | 3499.0000
 aaaaaaaa-0000-0000-0000-000000000005 | Jazz at the Note | General     | 1999.0000
(3 rows)
```

Grouping by the name: wrong, with no error.

```text
SELECT e.name, COUNT(*) AS tiers, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.name
ORDER BY e.name;

        name        | tiers |   total
--------------------+-------+-----------
 Classical Evening  |     1 |  999.0000
 Jazz at the Note   |     3 | 7497.0000
 Monsoon Rock Night |     3 | 8997.0000
(3 rows)
```

No single event has 3 tiers or a total of 7497. The two nights were poured into one group:
1999 + 3499 + 1999 = 7497. Four events have tiers, but there are only 3 rows.

Grouping by the id and the name: right.

```text
SELECT e.id, e.name, COUNT(*) AS tiers, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
ORDER BY e.name, e.id;

                  id                  |        name        | tiers |   total
--------------------------------------+--------------------+-------+-----------
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening  |     1 |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note   |     2 | 5498.0000
 aaaaaaaa-0000-0000-0000-000000000005 | Jazz at the Note   |     1 | 1999.0000
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night |     3 | 8997.0000
(4 rows)
```

### Example 3 — the primary-key shortcut, and its three limits

With the repeat show still in place, only `e.id` is in `GROUP BY`, yet `e.name` and `e.starts_at`
are allowed:

```text
SELECT e.id, e.name, e.starts_at, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id
ORDER BY e.name, e.starts_at;

                  id                  |        name        |       starts_at        |   total
--------------------------------------+--------------------+------------------------+-----------
 aaaaaaaa-0000-0000-0000-000000000002 | Classical Evening  | 2026-12-05 13:00:00+00 |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note   | 2026-11-28 15:30:00+00 | 5498.0000
 aaaaaaaa-0000-0000-0000-000000000005 | Jazz at the Note   | 2026-12-19 15:30:00+00 | 1999.0000
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night | 2026-11-20 13:30:00+00 | 8997.0000
(4 rows)
```

It is safe because a primary key picks out exactly one row, so inside the group for one `e.id`,
every column of `event` has exactly one value.

The three limits, each a real error:

```text
SELECT e.id, e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.name;

ERROR:  column "e.id" must appear in the GROUP BY clause or be used in an aggregate function

SELECT pt.event_id, e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY pt.event_id;

ERROR:  column "e.name" must appear in the GROUP BY clause or be used in an aggregate function

SELECT e.id, e.name, v.city, SUM(pt.amount) AS total
FROM event e JOIN venue v ON v.id = e.venue_id JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id;

ERROR:  column "v.city" must appear in the GROUP BY clause or be used in an aggregate function
```

1. **Only outward from the key.** One id has one name, but one name can have many ids. The "Jazz
   at the Note" group holds two ids.
2. **Not through the join.** `pt.event_id` equals `e.id` in every joined row, but it is not the
   primary key of `event`. Postgres trusts only a primary key that is itself in `GROUP BY`.
3. **Only that table's columns.** `e.id` is the key of `event`, not of `venue`.

The shortcut comes from the `PRIMARY KEY` constraint itself, not from the column being called
`id`. In a throwaway copy of a customers table with the same rows but **no** primary key,
`GROUP BY c.id` with `c.name` in the `SELECT` fails:

```text
ERROR:  column "c.name" must appear in the GROUP BY clause or be used in an aggregate function
LINE 1: SELECT c.id, c.name, SUM(p.amount) AS total
                     ^
```

### Cleaning up the demo rows

The tier goes first, then the event:

```text
DELETE FROM price_tier WHERE id = 'bbbbbbbb-0000-0000-0000-000000000007';
DELETE 1

DELETE FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';
DELETE 1
```

In the wrong order, the foreign key refuses:

```text
DELETE FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000005';

ERROR:  update or delete on table "event" violates foreign key constraint "price_tier_event_fk" on table "price_tier"
DETAIL:  Key (id)=(aaaaaaaa-0000-0000-0000-000000000005) is still referenced from table "price_tier".
```

After the cleanup, the counts are back to 3 venues, 4 events and 6 tiers.

### Important terms

| Term | Meaning |
| --- | --- |
| Key | A column whose value picks out exactly one row. |
| Primary key | The table's main key. The database forbids two rows with the same value. |
| Label | A column meant for people to read, such as a name. Two rows can share it. |
| Primary-key shortcut | In Postgres, grouping by a table's primary key allows any other column of that table in `SELECT`. |

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Grouping by the name alone | Two events with the same name merge: "Jazz at the Note, 3 tiers, 7497". No error. | `GROUP BY e.id, e.name`. |
| Grouping by the id, with no name in `SELECT` | Correct numbers, but only ids. | Add `e.name` to `SELECT` and `GROUP BY`. |
| Expecting the shortcut to work from the name | Error on `e.id`. | The shortcut works only from a primary key. |
| Grouping by `pt.event_id` and selecting `e.name` | Error on `e.name`. | Group by `e.id`, the primary key of the table whose columns you select. |
| Relying on the shortcut where the SQL may run on another database | SQL Server and Oracle refuse it. | Write `GROUP BY e.id, e.name`. |
| Deleting the event before its tier | Foreign key error. | Delete the child first. |

### Where it is used

- Revenue per customer: group by the customer id, show the name. Two customers can share a name.
- Sales per product: product names repeat across sizes or brands.
- The same idea in Java: a `Map` keyed by event name would merge the two Jazz nights. A `Map`
  keyed by event id keeps them apart.

### Link to the task

This explains **S7**'s first exit question, "total tier value per event
(`SELECT e.id, e.name, SUM(pt.amount) FROM event e JOIN price_tier pt ON pt.event_id = e.id GROUP BY e.id, e.name`)".
`e.id` makes one group per event even when names repeat. `e.name` is there because the `SELECT`
shows it, and it costs nothing, because one id has one name.

### Summary for revision

- Group by the key, show the label. `GROUP BY e.id, e.name`.
- Grouping by a name merges different things that share it, with no error.
- Adding a column splits a group only if it has more than one value inside the group.
- Postgres lets `GROUP BY e.id` cover all of `event`'s columns, because `e.id` is the primary key.
  It does not cover a name, a pointer from another table, or another table's columns.
- The portable form lists every selected column: `GROUP BY e.id, e.name`.

### Your doubts

None for this lesson.

---

## New data from Lesson 8 on: three July events

The filtering lessons need a city with more than two events, and some events in July. Three
events were added for good. They have no price tiers. Lakeside Hall in Chennai still has no
events.

```text
INSERT INTO event (id, venue_id, name, starts_at) VALUES
  ('aaaaaaaa-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'Sufi Night',   '2027-07-10 19:30+05:30'),
  ('aaaaaaaa-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'Indie Fest',   '2027-07-24 17:00+05:30'),
  ('aaaaaaaa-0000-0000-0000-000000000007', '22222222-2222-2222-2222-222222222222', 'Blues Brunch', '2027-07-18 12:00+05:30');
INSERT 0 3

SELECT v.city, e.name, e.starts_at, e.cancelled_at
FROM event e JOIN venue v ON v.id = e.venue_id
ORDER BY v.city, e.starts_at;

   city    |        name        |       starts_at        |         cancelled_at
-----------+--------------------+------------------------+-------------------------------
 Hyderabad | Monsoon Rock Night | 2026-11-20 13:30:00+00 | «NULL»
 Hyderabad | Classical Evening  | 2026-12-05 13:00:00+00 | 2026-10-24 18:05:00.690845+00
 Hyderabad | Sufi Night         | 2027-07-10 14:00:00+00 | «NULL»
 Hyderabad | Indie Fest         | 2027-07-24 11:30:00+00 | «NULL»
 Mumbai    | Jazz at the Note   | 2026-11-28 15:30:00+00 | «NULL»
 Mumbai    | Open Mic Night     | 2026-12-12 14:30:00+00 | «NULL»
 Mumbai    | Blues Brunch       | 2027-07-18 06:30:00+00 | «NULL»
(7 rows)
```

**New counts from here on:** 3 venues, **7** events, 6 tiers. Hyderabad has 4 events, Mumbai has
3, and Chennai has none. To rebuild this on a fresh machine, run "Setting all of this up from
nothing", then the `INSERT` above.

---

## Lesson 8 — `HAVING`: filtering groups

### Definition

`HAVING` keeps or drops **whole groups**. Its test usually uses an aggregate function, such as
`COUNT(*) > 2`. It runs after `GROUP BY`, when the groups and their counts exist.

### Why it is needed

`WHERE` runs before the groups are made. At that moment there is no count to test, so an aggregate
function in `WHERE` is refused:

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE COUNT(*) > 2
GROUP BY v.city;

ERROR:  aggregate functions are not allowed in WHERE
LINE 1: ...M event e JOIN venue v ON v.id = e.venue_id WHERE COUNT(*) >...
                                                             ^
```

### How it works

1. `FROM` and `JOIN` build the rows.
2. `GROUP BY` makes the groups.
3. The aggregate functions are worked out for each group.
4. `HAVING` tests each group, and drops the groups that fail.
5. `SELECT` and `ORDER BY` shape what is left.

### Example 1 — cities with more than two events

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING COUNT(*) > 2
ORDER BY v.city;

   city    | events
-----------+--------
 Hyderabad |      4
 Mumbai    |      3
(2 rows)

SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING COUNT(*) > 3
ORDER BY v.city;

   city    | events
-----------+--------
 Hyderabad |      4
(1 row)
```

### Example 2 — events whose total tier value is above 5000

```text
SELECT e.id, e.name, SUM(pt.amount) AS total
FROM event e JOIN price_tier pt ON pt.event_id = e.id
GROUP BY e.id, e.name
HAVING SUM(pt.amount) > 5000
ORDER BY e.name;

                  id                  |        name        |   total
--------------------------------------+--------------------+-----------
 aaaaaaaa-0000-0000-0000-000000000003 | Jazz at the Note   | 5498.0000
 aaaaaaaa-0000-0000-0000-000000000001 | Monsoon Rock Night | 8997.0000
(2 rows)
```

Classical Evening, with a total of 999, was dropped.

### Example 3 — the tested count does not have to be shown

```text
SELECT v.city
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING COUNT(*) > 2
ORDER BY v.city;

   city
-----------
 Hyderabad
 Mumbai
(2 rows)
```

If no group passes, the result is empty:

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING COUNT(*) > 10;

 city | events
------+--------
(0 rows)
```

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| An aggregate function in `WHERE` | `ERROR: aggregate functions are not allowed in WHERE` | Move the test to `HAVING`. |
| Expecting `HAVING` without `GROUP BY` to filter rows | The whole table is one group, so you get one row or none. | Use `WHERE` for row tests. |

### Link to the task

**S3**: "cities with more than two events" is `GROUP BY v.city HAVING COUNT(*) > 2`.

### Summary for revision

- `HAVING` filters groups. `WHERE` filters rows.
- `HAVING` runs after `GROUP BY`, so it can test `COUNT`, `SUM`, `MIN`, `MAX` and `AVG`.
- The tested aggregate function need not be in `SELECT`. If no group passes, you get zero rows.

### Your doubts

None for this lesson.

---

## Lesson 9 — `WHERE` before grouping

### Definition

`WHERE` removes **rows** before the groups are made. The groups are built only from the rows that
survive.

### Key points

- `WHERE` tests one row at a time, before any grouping.
- If every row of a would-be group is removed, that group never forms. It does not show 0. It is
  missing.
- For a month, write a range with an explicit time zone offset. The database stores times in UTC;
  `+05:30` makes the month start at midnight in India, where the venues are.

### Example 1 — events in July, per city

```text
SELECT v.city, COUNT(*) AS july_events
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.starts_at >= '2027-07-01 00:00+05:30'
  AND e.starts_at <  '2027-08-01 00:00+05:30'
GROUP BY v.city
ORDER BY v.city;

   city    | july_events
-----------+-------------
 Hyderabad |           2
 Mumbai    |           1
(2 rows)
```

`WHERE` keeps 3 of the 7 events (Sufi Night, Indie Fest, Blues Brunch). `GROUP BY` sorts those 3
into cities, and `COUNT(*)` counts each group. Without the `+05:30`, "July" would start at 05:30
in India, so an event just after midnight on 1 July would be counted in June.

### Example 2 — live events per city

```text
SELECT v.city, COUNT(*) AS live_events
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.cancelled_at IS NULL
GROUP BY v.city
ORDER BY v.city;

   city    | live_events
-----------+-------------
 Hyderabad |           3
 Mumbai    |           3
(2 rows)
```

Classical Evening was removed before counting, so Hyderabad counts 3, not 4.

### Example 3 — a group that vanishes

```text
SELECT pt.event_id, COUNT(*) AS tiers_over_2000
FROM price_tier pt
WHERE pt.amount > 2000
GROUP BY pt.event_id
ORDER BY pt.event_id;

               event_id               | tiers_over_2000
--------------------------------------+-----------------
 aaaaaaaa-0000-0000-0000-000000000001 |               2
 aaaaaaaa-0000-0000-0000-000000000003 |               1
(2 rows)
```

The rows that survived `WHERE`:

```text
SELECT pt.event_id, pt.name, pt.amount FROM price_tier pt WHERE pt.amount > 2000
ORDER BY pt.event_id, pt.amount;

               event_id               |    name     |  amount
--------------------------------------+-------------+-----------
 aaaaaaaa-0000-0000-0000-000000000001 | Balcony     | 2499.0000
 aaaaaaaa-0000-0000-0000-000000000001 | VIP         | 4999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | Front Table | 3499.0000
(3 rows)
```

Classical Evening (`...0002`) has one tier, at 999. `WHERE` removed it, so the event has no rows
left, no group, and no result row.

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Expecting a 0 for a group whose rows were all removed | The group is missing. | Use a `LEFT JOIN` from the parent if every parent must appear. |
| A month range with no time zone offset | The month starts at midnight UTC, which is 05:30 in India. | Write the bounds with `+05:30`. |

### Link to the task

**S3**: "events in July grouped by city" is the July `WHERE` plus `GROUP BY v.city`.

### Summary for revision

- `WHERE` removes rows first. The groups are made from what is left.
- A group with no surviving rows disappears.

### Your doubts

None for this lesson.

---

## Lesson 10 — `WHERE` and `HAVING` in one query

### Definition

A query can have both. **`WHERE` runs first**: it filters the rows. Then `GROUP BY` groups the
survivors. Then `HAVING` filters the groups.

### Example 1 — cities with more than one July event

```text
SELECT v.city, COUNT(*) AS july_events
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE e.starts_at >= '2027-07-01 00:00+05:30'
  AND e.starts_at <  '2027-08-01 00:00+05:30'
GROUP BY v.city
HAVING COUNT(*) > 1
ORDER BY v.city;

   city    | july_events
-----------+-------------
 Hyderabad |           2
(1 row)
```

1. There are 7 events.
2. `WHERE` keeps the 3 July ones.
3. `GROUP BY` makes two groups: Hyderabad with 2, Mumbai with 1.
4. `HAVING COUNT(*) > 1` keeps Hyderabad.

The task's exact combination, July with `HAVING COUNT(*) > 2`, returns nothing, because no city has
three July events:

```text
 city | july_events
------+-------------
(0 rows)
```

The same `HAVING COUNT(*) > 2` with no `WHERE` keeps Hyderabad 4 and Mumbai 3. `WHERE` shrank the
groups before `HAVING` judged them.

### Example 2 — events with at least two tiers over 2000

```text
SELECT pt.event_id, COUNT(*) AS tiers_over_2000
FROM price_tier pt
WHERE pt.amount > 2000
GROUP BY pt.event_id
HAVING COUNT(*) >= 2;

               event_id               | tiers_over_2000
--------------------------------------+-----------------
 aaaaaaaa-0000-0000-0000-000000000001 |               2
(1 row)
```

Only Monsoon Rock Night passes, with Balcony 2499 and VIP 4999.

### Example 3 — which clause for which test

A test on a grouping key works in both, with the same result:

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING v.city = 'Hyderabad';

   city    | events
-----------+--------
 Hyderabad |      4
(1 row)

SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE v.city = 'Hyderabad'
GROUP BY v.city;

   city    | events
-----------+--------
 Hyderabad |      4
(1 row)
```

`WHERE` is the better place, because it drops the Mumbai rows before any grouping work. A test on
a plain row column that is not a grouping key fails in `HAVING`, because each group has many values
for it:

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING e.cancelled_at IS NULL;

ERROR:  column "e.cancelled_at" must appear in the GROUP BY clause or be used in an aggregate function
```

| Clause | Runs | Tests | Use it for |
| --- | --- | --- | --- |
| `WHERE` | Before grouping | One row at a time | Tests on plain columns, such as dates and status |
| `HAVING` | After grouping | One group at a time | Tests on aggregate functions, such as counts and sums |

### Link to the task

**S3**: combining both and saying which runs first. `WHERE` runs first. **Checkpoint question 1**:
`WHERE` filters rows before grouping; `HAVING` filters groups after grouping.

### Summary for revision

- Order: `WHERE` (rows), then `GROUP BY`, then `HAVING` (groups).
- Row tests go in `WHERE`. Aggregate tests go in `HAVING`.
- A grouping-key test works in either; `WHERE` is cheaper.

### Your doubts

None for this lesson.

---

## Lesson 11 — The full clause order, and where a `SELECT` alias works

### Definition

SQL is written in one order but worked out in another, called the **logical order**.

- **Written order:** `SELECT`, `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT`.
- **Logical order:**
  1. `FROM` and `JOIN`
  2. `WHERE`
  3. `GROUP BY`
  4. `HAVING`
  5. `SELECT` (aliases are born here)
  6. `ORDER BY`
  7. `LIMIT`

The database may do the physical work differently for speed, but the result is always as if it
followed this order.

An **alias** is a name given with `AS` in the `SELECT`, such as `amount * 2 AS doubled`. It exists
only from step 5 onward. `WHERE`, `GROUP BY` and `HAVING` come before it and cannot see it.
`ORDER BY` comes after it and can.

### Example 1 — an alias in `WHERE` fails

```text
SELECT name, amount * 2 AS doubled FROM price_tier WHERE doubled > 5000;

ERROR:  column "doubled" does not exist
LINE 1: ...name, amount * 2 AS doubled FROM price_tier WHERE doubled > ...
                                                             ^
```

The fix is to repeat the expression:

```text
SELECT name, amount * 2 AS doubled FROM price_tier WHERE amount * 2 > 5000 ORDER BY doubled DESC;

    name     |  doubled
-------------+-----------
 VIP         | 9998.0000
 Front Table | 6998.0000
(2 rows)
```

### Example 2 — an alias in `ORDER BY` works, but only as a bare name

```text
SELECT name, amount * 2 AS doubled FROM price_tier ORDER BY doubled DESC;

    name     |  doubled
-------------+-----------
 VIP         | 9998.0000
 Front Table | 6998.0000
 Balcony     | 4998.0000
 General     | 3998.0000
 General     | 2998.0000
 General     | 1998.0000
(6 rows)

SELECT name, amount * 2 AS doubled FROM price_tier ORDER BY doubled + 1 DESC;

ERROR:  column "doubled" does not exist
LINE 1: ...e, amount * 2 AS doubled FROM price_tier ORDER BY doubled + ...
                                                             ^
```

### Example 3 — an alias in `HAVING` fails; `LIMIT` runs last

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
HAVING events > 2;

ERROR:  column "events" does not exist
LINE 1: ...nue v ON v.id = e.venue_id GROUP BY v.city HAVING events > 2...
                                                             ^
```

The fix is `HAVING COUNT(*) > 2`. The same alias works in `ORDER BY`, and `LIMIT` cuts the sorted
result:

```text
SELECT v.city, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY v.city
ORDER BY events DESC
LIMIT 1;

   city    | events
-----------+--------
 Hyderabad |      4
(1 row)
```

### One Postgres extra

Postgres also accepts an alias in `GROUP BY`:

```text
SELECT v.city AS town, COUNT(*) AS events
FROM event e JOIN venue v ON v.id = e.venue_id
GROUP BY town ORDER BY town;

   town    | events
-----------+--------
 Hyderabad |      4
 Mumbai    |      3
(2 rows)
```

This is a convenience Postgres adds. The SQL standard does not allow it, and it goes against the
logical order, so prefer the real column there.

### Common mistakes

| Mistake | What happens | The correct way |
| --- | --- | --- |
| Alias in `WHERE` | `column "doubled" does not exist` | Repeat the expression. |
| Alias in `HAVING` | `column "events" does not exist` | Repeat the aggregate function: `HAVING COUNT(*) > 2`. |
| Alias inside a calculation in `ORDER BY` | `column "doubled" does not exist` | Use the bare alias, or repeat the expression. |

### Link to the task

**S8**: the logical clause order, and why a `SELECT` alias fails in `WHERE` but works in
`ORDER BY`.

### Summary for revision

- Logical order: `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `SELECT`, `ORDER BY`, `LIMIT`.
- Aliases are born in `SELECT`, so only `ORDER BY` (and Postgres's `GROUP BY` extra) can use them.

### Your doubts

None for this lesson.

---

## Lesson 12 — A sub-query used as one value

### Definition

A **sub-query** is a query inside brackets, inside another query. The inner query runs first and
hands its answer to the outer query. When the answer is exactly **one value** (one row, one
column), it can go anywhere a single value can go: after `>` in a `WHERE`, or as a column in the
`SELECT`.

### Why it is needed

Some questions need a number that must be worked out first. "Which tiers cost more than the
average?" The average is not known until it is calculated. Without a sub-query, you would run two
queries and copy the number across by hand.

### How it works

1. The inner query runs and gives one value.
2. That value is dropped into the outer query.
3. The outer query runs.

### Example 1 — tiers priced above the average

```text
SELECT AVG(amount) FROM price_tier;

          avg
-----------------------
 2582.3333333333333333
(1 row)

SELECT name, amount
FROM price_tier
WHERE amount > (SELECT AVG(amount) FROM price_tier)
ORDER BY amount;

    name     |  amount
-------------+-----------
 Front Table | 3499.0000
 VIP         | 4999.0000
(2 rows)
```

### Example 2 — the cheapest tier, with its name and event

The name and the amount come from the **same row**, so they belong together:

```text
SELECT e.name AS event, pt.name AS tier, pt.amount
FROM price_tier pt JOIN event e ON e.id = pt.event_id
WHERE pt.amount = (SELECT MIN(amount) FROM price_tier);

       event       |  tier   |  amount
-------------------+---------+----------
 Classical Evening | General | 999.0000
(1 row)
```

### Example 3 — a sub-query inside `SELECT`

Each tier's share of the total of all tiers:

```text
SELECT name, amount,
       ROUND(100 * amount / (SELECT SUM(amount) FROM price_tier), 1) AS percent_of_all
FROM price_tier
ORDER BY amount DESC;

    name     |  amount   | percent_of_all
-------------+-----------+----------------
 VIP         | 4999.0000 |           32.3
 Front Table | 3499.0000 |           22.6
 Balcony     | 2499.0000 |           16.1
 General     | 1999.0000 |           12.9
 General     | 1499.0000 |            9.7
 General     |  999.0000 |            6.4
(6 rows)
```

### A close cousin — a sub-query that returns a list, used with `IN`

"Events that have a tier above 3000":

```text
SELECT event_id FROM price_tier WHERE amount > 3000;

               event_id
--------------------------------------
 aaaaaaaa-0000-0000-0000-000000000001
 aaaaaaaa-0000-0000-0000-000000000003
(2 rows)

SELECT name FROM event
WHERE id IN (SELECT event_id FROM price_tier WHERE amount > 3000)
ORDER BY name;

        name
--------------------
 Jazz at the Note
 Monsoon Rock Night
(2 rows)
```

### Things to watch

With `=`, a sub-query that returns more than one row is refused:

```text
SELECT name FROM price_tier
WHERE amount = (SELECT amount FROM price_tier WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001');

ERROR:  more than one row returned by a subquery used as an expression
```

Use `IN` when the inner query can return many values. A sub-query that returns no rows has the
value `NULL`, and a comparison with `NULL` is never true:

```text
SELECT (SELECT amount FROM price_tier WHERE amount > 9000) AS nothing;

 nothing
---------
  «NULL»
(1 row)
```

### Link to the task

Prepares **S6**. It also fixes the `MIN(name)` beside `MIN(amount)` trap ("Balcony 1499", a name
and a price from different rows): a sub-query finds the row first, so its name and amount match.

### Summary for revision

- A sub-query in brackets runs first; a one-value sub-query can stand wherever one value can.
- `=` needs exactly one row. `IN` takes a list. No rows means `NULL`.

### Your doubts

None for this lesson.

---

## Lesson 13 — A sub-query used as a table, inside `FROM`

### Definition

A sub-query can return a whole table, with many rows and many columns. Put in `FROM`, it is used
like a real table. This is often called a **derived table**. Give it a name with `AS`, so the outer
query can refer to its columns, such as `c.cheapest`.

### Why it is needed

Some questions need two steps. "Average number of tiers per event" means: count tiers per event,
then average those counts. One step cannot do both:

```text
SELECT AVG(COUNT(*)) FROM price_tier GROUP BY event_id;

ERROR:  aggregate function calls cannot be nested
LINE 1: SELECT AVG(COUNT(*)) FROM price_tier GROUP BY event_id;
                   ^
```

### How it works

1. The inner query builds its result table.
2. The outer query reads from that table as if it were a real one.

### Example 1 — the average number of tiers per event

```text
SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id ORDER BY event_id;

               event_id               | tiers
--------------------------------------+-------
 aaaaaaaa-0000-0000-0000-000000000001 |     3
 aaaaaaaa-0000-0000-0000-000000000002 |     1
 aaaaaaaa-0000-0000-0000-000000000003 |     2
(3 rows)

SELECT AVG(tiers) AS avg_tiers_per_event
FROM (SELECT event_id, COUNT(*) AS tiers FROM price_tier GROUP BY event_id) AS per_event;

 avg_tiers_per_event
---------------------
  2.0000000000000000
(1 row)
```

(3 + 1 + 2) ÷ 3 = 2.

### Example 2 — events whose cheapest tier is above 1000 (S6, sub-query form)

Our tiers cost 999 to 4999, so the task's "£40" becomes 1000. The inner table:

```text
SELECT event_id, MIN(amount) AS cheapest FROM price_tier GROUP BY event_id ORDER BY event_id;

               event_id               | cheapest
--------------------------------------+-----------
 aaaaaaaa-0000-0000-0000-000000000001 | 1499.0000
 aaaaaaaa-0000-0000-0000-000000000002 |  999.0000
 aaaaaaaa-0000-0000-0000-000000000003 | 1999.0000
(3 rows)
```

The full query:

```text
SELECT e.name, c.cheapest
FROM (SELECT event_id, MIN(amount) AS cheapest FROM price_tier GROUP BY event_id) AS c
JOIN event e ON e.id = c.event_id
WHERE c.cheapest > 1000
ORDER BY e.name;

        name        | cheapest
--------------------+-----------
 Jazz at the Note   | 1999.0000
 Monsoon Rock Night | 1499.0000
(2 rows)
```

### Example 3 — a way around the alias rule

A `SELECT` alias cannot be used in `WHERE`. Inside a derived table it becomes a real column, so the
outer `WHERE` can use it:

```text
SELECT name, doubled
FROM (SELECT name, amount * 2 AS doubled FROM price_tier) AS t
WHERE doubled > 5000
ORDER BY doubled DESC;

    name     |  doubled
-------------+-----------
 VIP         | 9998.0000
 Front Table | 6998.0000
(2 rows)
```

### Things to watch

Only the inner query's **output** columns exist outside it:

```text
SELECT c.amount
FROM (SELECT event_id, MIN(amount) AS cheapest FROM price_tier GROUP BY event_id) AS c;

ERROR:  column c.amount does not exist
LINE 1: SELECT c.amount FROM (SELECT event_id, MIN(amount) AS cheape...
               ^
```

Always name the derived table. Postgres 16 and later accept it without a name (a real run on
Postgres 17 of `SELECT COUNT(*) FROM (SELECT event_id FROM price_tier GROUP BY event_id);` returned
`3`), but older versions and many other databases require the name, and the name is needed to refer
to its columns anyway.

### Link to the task

**S6**, the sub-query version.

### Summary for revision

- A derived table is a sub-query in `FROM`, used like a table, with a name.
- It solves two-step questions, such as an aggregate of an aggregate.
- Outside it, only its output columns exist.

### Your doubts

None for this lesson.

---

## Lesson 14 — The correlated sub-query: runs once per outer row

### Definition

A **correlated sub-query** uses a column from the outer query's current row. "Correlated" means
"linked to the outer row". Because the inner query depends on that row, it is worked out again for
**each** outer row.

### Why it is needed

"For each thing, find something about it": for each event, how many tiers? Which tier is cheapest?
The inner answer is different for every event.

### How it works

For each row of the outer query:

1. Take that row's value, such as `e.id`.
2. Run the inner query with it.
3. Use the inner answer for that row only.

### Example 1 — the number of tiers beside each event

```text
SELECT e.name,
       (SELECT COUNT(*) FROM price_tier pt WHERE pt.event_id = e.id) AS tiers
FROM event e
ORDER BY e.name;

        name        | tiers
--------------------+-------
 Blues Brunch       |     0
 Classical Evening  |     1
 Indie Fest         |     0
 Jazz at the Note   |     2
 Monsoon Rock Night |     3
 Open Mic Night     |     0
 Sufi Night         |     0
(7 rows)
```

| Outer row | The inner query it ran | Answer |
| --- | --- | --- |
| Monsoon Rock Night | Count tiers where `event_id` = Monsoon's id | 3 |
| Classical Evening | Count tiers where `event_id` = Classical's id | 1 |
| Open Mic Night | Count tiers where `event_id` = Open Mic's id | 0 |

`COUNT` over no rows is 0, so every event appears, with no `LEFT JOIN` needed.

### Example 2 — the cheapest tier of each event, with its real name

The inner query uses its own alias, `pt2`, so it does not clash with the outer `pt`:

```text
SELECT e.name AS event, pt.name AS tier, pt.amount
FROM price_tier pt JOIN event e ON e.id = pt.event_id
WHERE pt.amount = (SELECT MIN(pt2.amount) FROM price_tier pt2 WHERE pt2.event_id = pt.event_id)
ORDER BY e.name;

       event        |  tier   |  amount
--------------------+---------+-----------
 Classical Evening  | General |  999.0000
 Jazz at the Note   | General | 1999.0000
 Monsoon Rock Night | General | 1499.0000
(3 rows)
```

Monsoon's cheapest tier is **General** at 1499, not "Balcony" as the `MIN(name)` trap said. The
same with `MAX` gives the dearest tier of each event:

```text
SELECT e.name AS event, pt.name AS tier, pt.amount
FROM price_tier pt JOIN event e ON e.id = pt.event_id
WHERE pt.amount = (SELECT MAX(pt2.amount) FROM price_tier pt2 WHERE pt2.event_id = pt.event_id)
ORDER BY e.name;

       event        |    tier     |  amount
--------------------+-------------+-----------
 Classical Evening  | General     |  999.0000
 Jazz at the Note   | Front Table | 3499.0000
 Monsoon Rock Night | VIP         | 4999.0000
(3 rows)
```

### Example 3 — events whose cheapest tier is above 1000, the correlated way

```text
SELECT e.name,
       (SELECT MIN(pt.amount) FROM price_tier pt WHERE pt.event_id = e.id) AS cheapest
FROM event e
WHERE (SELECT MIN(pt.amount) FROM price_tier pt WHERE pt.event_id = e.id) > 1000
ORDER BY e.name;

        name        | cheapest
--------------------+-----------
 Jazz at the Note   | 1999.0000
 Monsoon Rock Night | 1499.0000
(2 rows)
```

Events with no tiers get `NULL` from `MIN`, and `NULL > 1000` is never true, so they are dropped.
Without the `WHERE`:

```text
        name        | cheapest
--------------------+-----------
 Blues Brunch       |    «NULL»
 Classical Evening  |  999.0000
 Indie Fest         |    «NULL»
 Jazz at the Note   | 1999.0000
 Monsoon Rock Night | 1499.0000
 Open Mic Night     |    «NULL»
 Sufi Night         |    «NULL»
(7 rows)
```

### Example 4 — `EXISTS` and `NOT EXISTS`

`EXISTS (...)` asks one thing: does the inner query return at least one row?

```text
SELECT v.name, v.city
FROM venue v
WHERE NOT EXISTS (SELECT 1 FROM event e WHERE e.venue_id = v.id);

     name      |  city
---------------+---------
 Lakeside Hall | Chennai
(1 row)

SELECT v.name, v.city
FROM venue v
WHERE EXISTS (SELECT 1 FROM event e WHERE e.venue_id = v.id)
ORDER BY v.name;

      name       |   city
-----------------+-----------
 Riverside Arena | Hyderabad
 The Blue Note   | Mumbai
(2 rows)
```

The `SELECT 1` does not matter; only "is there a row?" matters. This is a second way to answer S7's
"venues with no events".

### The big mistake — forgetting the link

Without `WHERE pt.event_id = e.id`, the inner query counts **all** tiers every time:

```text
SELECT e.name, (SELECT COUNT(*) FROM price_tier pt) AS tiers FROM event e ORDER BY e.name;

        name        | tiers
--------------------+-------
 Blues Brunch       |     6
 Classical Evening  |     6
 Indie Fest         |     6
 Jazz at the Note   |     6
 Monsoon Rock Night |     6
 Open Mic Night     |     6
 Sufi Night         |     6
(7 rows)
```

No error, the same wrong number on every row.

### A note on speed

"Once per outer row" is the way to think about it. Postgres can often do the work in a smarter way,
but on big tables a correlated sub-query in `SELECT` can be slow, so check before using it on
millions of rows.

### Link to the task

**S6**, and a second way to write **S7**'s "venues with no events".

### Summary for revision

- A correlated sub-query refers to the outer row, and is worked out per outer row.
- It answers "for each X, find Y about X", including 0 for `COUNT`.
- `EXISTS` / `NOT EXISTS` test whether any matching row exists.
- Forgetting the link gives the same whole-table answer on every row.

### Your doubts

None for this lesson.

---

## Lesson 15 — The CTE: `WITH name AS (...)`

### Definition

A **CTE** (Common Table Expression) gives a **name** to a sub-query and writes it **before** the
main query. The main query uses the name like a table. The name exists only for that one statement.

```sql
WITH cheapest AS (
  ...a query...
)
SELECT ... FROM cheapest ...;
```

### Why it is needed

Readability. A derived table sits in the middle of the query, so it is read inside out. A CTE puts
each step first, with a name, so the query reads top to bottom like a recipe. A CTE can also be used
more than once in the same statement.

### How it works

1. `WITH` defines one or more named steps, separated by commas.
2. A later step may use an earlier one.
3. The main query, at the end, reads from them.

### Example 1 — S6 as a CTE

The same question and answer as the derived-table version:

```text
WITH cheapest AS (
  SELECT event_id, MIN(amount) AS cheapest
  FROM price_tier
  GROUP BY event_id
)
SELECT e.name, c.cheapest
FROM cheapest c
JOIN event e ON e.id = c.event_id
WHERE c.cheapest > 1000
ORDER BY e.name;

        name        | cheapest
--------------------+-----------
 Jazz at the Note   | 1999.0000
 Monsoon Rock Night | 1499.0000
(2 rows)
```

- **Sub-query shape:** `SELECT ... FROM ( ...cheapest per event... ) AS c JOIN event ...`. The first
  step is buried inside the second.
- **CTE shape:** `WITH cheapest AS ( ... )`, then `SELECT ... FROM cheapest`. Step one is written
  first, with a name that says what it holds.

### Example 2 — two steps, the second using the first

```text
WITH per_event AS (
  SELECT event_id, COUNT(*) AS tiers, SUM(amount) AS total
  FROM price_tier
  GROUP BY event_id
),
big AS (
  SELECT * FROM per_event WHERE total > 5000
)
SELECT e.name, b.tiers, b.total
FROM big b
JOIN event e ON e.id = b.event_id
ORDER BY e.name;

        name        | tiers |   total
--------------------+-------+-----------
 Jazz at the Note   |     2 | 5498.0000
 Monsoon Rock Night |     3 | 8997.0000
(2 rows)
```

### Example 3 — one CTE used twice

"Events whose total tier value is above the average event total":

```text
WITH per_event AS (
  SELECT event_id, SUM(amount) AS total
  FROM price_tier
  GROUP BY event_id
)
SELECT e.name, p.total
FROM per_event p
JOIN event e ON e.id = p.event_id
WHERE p.total > (SELECT AVG(total) FROM per_event)
ORDER BY e.name;

        name        |   total
--------------------+-----------
 Jazz at the Note   | 5498.0000
 Monsoon Rock Night | 8997.0000
(2 rows)

WITH per_event AS (SELECT event_id, SUM(amount) AS total FROM price_tier GROUP BY event_id)
SELECT ROUND(AVG(total), 2) AS avg_event_total FROM per_event;

 avg_event_total
-----------------
         5164.67
(1 row)
```

Classical Evening, at 999, is below 5164.67. Without a CTE, the per-event query would be written out
twice.

### Things to watch

The name disappears when the statement ends:

```text
SELECT * FROM cheapest;

ERROR:  relation "cheapest" does not exist
LINE 1: SELECT * FROM cheapest;
                      ^
```

- Several CTEs share **one** `WITH`, separated by commas.
- A CTE is not a stored table. It is a named part of one query.

### Link to the task

**S6**: the same question as a sub-query and as a CTE. For anything with more than one step, the CTE
is usually easier to read later: each step has a name, and it reads top to bottom.

### Summary for revision

- `WITH name AS (query)` names a step; the main query uses it like a table.
- Steps are separated by commas, and later ones may use earlier ones.
- A CTE can be used more than once. It lives for one statement only.

### Your doubts

None for this lesson.

---

## Lesson 16 — Aggregating over a fan-out: the plausible wrong `SUM`

### Definition

A join repeats a parent row once for each matching child. This is **fan-out**. A `SUM` of a column
that belongs to the **parent** then adds the same value once per child. There is no error, and the
number looks believable. It is just too big.

### Why it matters

**Checkpoint question 4**: "Why can a `SUM` over a join be wrong even though the query runs without
error?". The database did exactly what was asked: it added up every row the join produced, and some
of those rows were copies.

### How it happens

1. Riverside Arena has a capacity of 12000.
2. It hosts 4 events.
3. Joining `venue` to `event` makes 4 rows for Riverside Arena, each carrying 12000.
4. `SUM(v.capacity)` adds 12000 four times.

### Example 1 — the true total, then the joined total

```text
SELECT SUM(capacity) AS total_capacity FROM venue;

 total_capacity
----------------
          13150
(1 row)

SELECT SUM(v.capacity) AS total_capacity
FROM venue v JOIN event e ON e.venue_id = v.id;

 total_capacity
----------------
          49050
(1 row)

SELECT v.name AS venue, v.capacity, e.name AS event
FROM venue v JOIN event e ON e.venue_id = v.id
ORDER BY v.name, e.name;

      venue      | capacity |       event
-----------------+----------+--------------------
 Riverside Arena |    12000 | Classical Evening
 Riverside Arena |    12000 | Indie Fest
 Riverside Arena |    12000 | Monsoon Rock Night
 Riverside Arena |    12000 | Sufi Night
 The Blue Note   |      350 | Blues Brunch
 The Blue Note   |      350 | Jazz at the Note
 The Blue Note   |      350 | Open Mic Night
(7 rows)
```

12000 × 4 + 350 × 3 = 49050. Lakeside Hall's 800 is also missing, because the inner join dropped it.

### Example 2 — S5: through three tables

```text
SELECT SUM(v.capacity) AS total_capacity
FROM venue v
JOIN event e ON e.venue_id = v.id
JOIN price_tier pt ON pt.event_id = e.id;

 total_capacity
----------------
          48700
(1 row)

SELECT v.name AS venue, v.capacity, e.name AS event, pt.name AS tier
FROM venue v JOIN event e ON e.venue_id = v.id JOIN price_tier pt ON pt.event_id = e.id
ORDER BY v.name, e.name, pt.name;

      venue      | capacity |       event        |    tier
-----------------+----------+--------------------+-------------
 Riverside Arena |    12000 | Classical Evening  | General
 Riverside Arena |    12000 | Monsoon Rock Night | Balcony
 Riverside Arena |    12000 | Monsoon Rock Night | General
 Riverside Arena |    12000 | Monsoon Rock Night | VIP
 The Blue Note   |      350 | Jazz at the Note   | Front Table
 The Blue Note   |      350 | Jazz at the Note   | General
(6 rows)

SELECT v.name, v.capacity AS real_capacity, SUM(v.capacity) AS summed_capacity, COUNT(*) AS joined_rows
FROM venue v JOIN event e ON e.venue_id = v.id JOIN price_tier pt ON pt.event_id = e.id
GROUP BY v.id, v.name, v.capacity ORDER BY v.name;

      name       | real_capacity | summed_capacity | joined_rows
-----------------+---------------+-----------------+-------------
 Riverside Arena |         12000 |           48000 |           4
 The Blue Note   |           350 |             700 |           2
(2 rows)
```

There is one row per **tier** now. The number is wrong two ways at once: **too big** because of the
copies, and **missing pieces** because Lakeside Hall and the July events have no tiers, so the inner
joins dropped them.

### Example 3 — why `SUM(pt.amount)` over the same join is fine

```text
SELECT SUM(pt.amount) AS all_tiers
FROM venue v JOIN event e ON e.venue_id = v.id JOIN price_tier pt ON pt.event_id = e.id;

 all_tiers
------------
 15494.0000
(1 row)

SELECT SUM(amount) AS all_tiers FROM price_tier;

 all_tiers
------------
 15494.0000
(1 row)
```

They match. Each tier appears exactly once in this join, because a tier is the "many" end. **Summing
a column is safe only if the join does not repeat that column's rows.**

Counting suffers in the same way:

```text
SELECT v.name, COUNT(e.id) AS events, COUNT(pt.id) AS tiers
FROM venue v LEFT JOIN event e ON e.venue_id = v.id LEFT JOIN price_tier pt ON pt.event_id = e.id
GROUP BY v.id, v.name ORDER BY v.name;

      name       | events | tiers
-----------------+--------+-------
 Lakeside Hall   |      0 |     0
 Riverside Arena |      6 |     4
 The Blue Note   |      4 |     2
(3 rows)
```

Riverside Arena has 4 events, not 6. Monsoon Rock Night has 3 tiers, so its event id is counted
3 times.

### The fix — add up each child table first, then join

Build one row per venue for each child table in a CTE, then join those rows. Nothing repeats,
because each CTE has at most one row per venue:

```text
WITH ev AS (
  SELECT venue_id, COUNT(*) AS events FROM event GROUP BY venue_id
),
tr AS (
  SELECT e.venue_id, COUNT(*) AS tiers
  FROM event e JOIN price_tier pt ON pt.event_id = e.id
  GROUP BY e.venue_id
)
SELECT v.name, v.capacity, COALESCE(ev.events, 0) AS events, COALESCE(tr.tiers, 0) AS tiers
FROM venue v
LEFT JOIN ev ON ev.venue_id = v.id
LEFT JOIN tr ON tr.venue_id = v.id
ORDER BY v.name;

      name       | capacity | events | tiers
-----------------+----------+--------+-------
 Lakeside Hall   |      800 |      0 |     0
 Riverside Arena |    12000 |      4 |     4
 The Blue Note   |      350 |      3 |     2
(3 rows)

WITH ev AS (SELECT venue_id, COUNT(*) AS events FROM event GROUP BY venue_id)
SELECT SUM(v.capacity) AS total_capacity FROM venue v LEFT JOIN ev ON ev.venue_id = v.id;

 total_capacity
----------------
          13150
(1 row)
```

Every number is right. `COALESCE(x, 0)` turns Lakeside Hall's `NULL` into 0.

### A fake fix — `SUM(DISTINCT ...)`

It adds each **different value** once, which only works by luck when no two venues share a
capacity:

```text
SELECT SUM(DISTINCT c) AS sum_distinct, SUM(c) AS sum_all FROM (VALUES (800), (800), (350)) AS t(c);

 sum_distinct | sum_all
--------------+---------
         1150 |    1950
(1 row)
```

The two 800s were counted as one.

### How to catch it

Compare the result with the same sum taken from the table alone. 49050 against 13150 gives it away
at once.

### Link to the task

**S5** (break it on purpose with `SUM(v.capacity)` across `venue` → `event` → `price_tier`), and
**checkpoint question 4**.

### Summary for revision

- A join repeats parent rows once per child. Summing a parent column then counts it many times.
- Summing a column from the "many" end, such as `pt.amount`, is safe.
- Fix: aggregate each child table first, one row per parent, then join.
- `SUM(DISTINCT ...)` is not a fix. Compare against the single-table total to catch the problem.

### Your doubts

None for this lesson.

---

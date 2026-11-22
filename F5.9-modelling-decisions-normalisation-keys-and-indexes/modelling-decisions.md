# F5.9 — Modelling decisions: normalisation, keys, and indexes

Notes for Task F5.9. One lesson per section. Each lesson ends with a **Do it yourself** block
with exact commands. Every SQL output shown here is real output captured from a running
Postgres 17 container, not invented.

**Learning outcome.** You can normalise a flat table and state what you gave up, model
one-to-many and many-to-many relationships, choose between a surrogate and a natural key,
and explain in plain words what an index is and what it costs.

**Why this matters.** Phase 1 models this whole domain and Phase 6 splits it across services.
Both need a feel for where a piece of data belongs and why duplicating it is sometimes right
and usually wrong.

---

## New words for this task

| Word | Plain meaning |
| --- | --- |
| **Normalisation** | Organising data so each fact is stored in exactly one place. |
| **1NF** | No repeating groups. A column never holds a list, such as `seats` containing `'A1,A2,A3'`. |
| **2NF and 3NF** | Every column depends on the whole key and nothing but the key. The working version: **do not store the same fact twice.** |
| **Denormalisation** | Deliberately duplicating data to make reads faster, accepting the cost of keeping copies in step. |
| **Update anomaly** | The bug duplication causes: you change a fact in one place and the other copies are now wrong. |
| **One-to-many** | One event has many price tiers. Modelled with a foreign key on the many side. |
| **Many-to-many** | A user can have many roles and a role many users. Needs a third table. |
| **Join table** | That third table, holding one row per pairing, usually with both columns as a composite primary key. |
| **Surrogate key** | An identifier with no meaning, such as a `uuid`. It never needs to change because it never described anything. |
| **Natural key** | An identifier made of real data, such as an email address. Meaningful, and liable to change. |
| **UUID v4** | A random identifier. Values arrive scattered rather than in order. |
| **UUID v7** | A time-ordered identifier. New values sort after old ones. |
| **Index** | A separate sorted structure the database maintains so it can find rows without reading the whole table. Like the index of a book. |
| **B-tree** | The default index shape in Postgres. Kept sorted, which is why ordered inserts are cheaper than scattered ones. |
| **`EXPLAIN`** | The command that shows how the database plans to run a query. Named here; Phase 3 teaches it properly. |

---

## Starting state of the machine

These notes assume the state left behind by tasks F5.1 to F5.7:

- A Docker container named `pg` running the `postgres:17-alpine` image, port 5432 published,
  data kept in the `pgdata` volume.
- A database named `zenith` inside it.
- Three tables: `venue` (3 rows), `event` (7 rows), `price_tier` (6 rows). `event.venue_id`
  points at `venue.id` and `price_tier.event_id` points at `event.id`.

If your machine has none of this, the **Setting all of this up from nothing** section of the
F5.7 notes (`F5.7-writing-data-insert-update-delete-and-upsert/writing-data.md`) has the full
install-and-create block: install Docker, `docker run -d --name pg -e POSTGRES_PASSWORD=dev
-p 5432:5432 -v pgdata:/var/lib/postgresql/data postgres:17-alpine`, create the `zenith`
database, create and fill the three tables. Everything in this file works on top of that,
with one harmless difference: a fresh setup has 4 events instead of 7, so any output that
lists events will show fewer rows. The lessons do not change.

Open psql the same way as always:

```bash
docker exec -it pg psql -U postgres -d zenith
```

---

## Lesson 1 — Normalisation: each fact lives in exactly one place

### Definition

**Normalisation** means organising tables so that **each fact is stored exactly once**.
"Riverside Arena is in Hyderabad" is one fact. In a normalised design it appears in one row of
one table, and every other table that needs it points at that row instead of repeating it.

### Why it is needed

If the same fact is written in five places, nothing keeps the five copies equal. The database
cannot help you, because to the database they are five independent values that merely happen
to look alike. The day someone changes three of the five, you have two versions of the truth
and no error message. That bug has a name — the **update anomaly** — and Lesson 4 causes one
on purpose.

### The three rules, in working form

The textbook names are 1NF, 2NF and 3NF ("normal forms"). The working versions are enough:

| Rule | Working form | Breaking it looks like |
| --- | --- | --- |
| **1NF** | A column holds **one value**, never a list. | `seats = 'A1,A2,A3'` |
| **2NF** | Every column depends on the **whole** key, not part of it. | In a table keyed by `(event_id, seat)`, a column `event_name` — it depends only on `event_id`, half the key. |
| **3NF** | Every column depends on **the key and nothing but the key**. | In `event`, a column `venue_city` — it depends on the venue, not on the event. |

The one-line memory aid, said about a table keyed by `id`: **every column must describe the
key, the whole key, and nothing but the key.** If a column actually describes some *other*
thing (a venue, not this event), the fact belongs in that other thing's table.

The working summary of 2NF and 3NF together, and the version to actually carry around:
**do not store the same fact twice.**

### Example

`venue_city` inside `event` breaks the rule. The city does not describe the event; it
describes the venue. Four events at Riverside Arena would each carry `'Hyderabad'` — the same
fact four times. Move it to `venue`, give `event` a `venue_id` pointing there, and the fact
exists once. That is all normalisation is.

### Do it yourself

Nothing to run. Read the three rules again and check them against the real `event` table:

```bash
docker exec -it pg psql -U postgres -d zenith -c "\d event"
```

Every column (`venue_id`, `name`, `starts_at`, `created_at`, `cancelled_at`) describes *this
event* and nothing else. The venue's own facts (name, city, capacity) are absent — only the
pointer `venue_id` is here. **Done when** you can say which table `venue_city` belongs in
and why.

---

## Lesson 2 — The bad seats column: what breaking 1NF costs

### The deliberately bad table

A column holding a list. One row per event, all its seats crammed into one text value:

```sql
CREATE TABLE bad_event (
    id    uuid PRIMARY KEY,
    name  text NOT NULL,
    seats text
);

INSERT INTO bad_event VALUES
('eeeeeeee-0000-0000-0000-000000000001', 'Monsoon Rock Night', 'A1,A2,A3'),
('eeeeeeee-0000-0000-0000-000000000002', 'Jazz at the Note',   'A11,B2');
```

```text
CREATE TABLE
INSERT 0 2
                  id                  |        name        |  seats
--------------------------------------+--------------------+----------
 eeeeeeee-0000-0000-0000-000000000001 | Monsoon Rock Night | A1,A2,A3
 eeeeeeee-0000-0000-0000-000000000002 | Jazz at the Note   | A11,B2
(2 rows)
```

It looks compact and harmless. Now try to use it.

### Failure 1 — you cannot count seats

```text
SELECT name, count(seats) AS seat_count FROM bad_event GROUP BY name;

        name        | seat_count
--------------------+------------
 Jazz at the Note   |          1
 Monsoon Rock Night |          1
(2 rows)
```

Monsoon Rock Night has three seats. The database says 1, because to the database
`'A1,A2,A3'` is **one text value**. It cannot see inside it. Every question about seats —
how many, how many left, which are sold — is unanswerable without string surgery.

### Failure 2 — you cannot find one seat reliably

"Which events have seat A1?" The only tool is pattern matching on text:

```text
SELECT name, seats FROM bad_event WHERE seats LIKE '%A1%';

        name        |  seats
--------------------+----------
 Monsoon Rock Night | A1,A2,A3
 Jazz at the Note   | A11,B2
(2 rows)
```

Wrong. Jazz at the Note has **A11**, not A1 — but the text `A11` contains the text `A1`, so
`LIKE` matched it. The database is not wrong; the question was asked against a design that
cannot express "one seat". You also cannot update or lock exactly one seat for the same
reason, which is fatal for a ticketing system whose whole job is selling exactly one seat
to exactly one person.

### Failure 3 — you cannot constrain seats

```text
UPDATE bad_event SET seats = 'A1,A1,banana' WHERE name = 'Monsoon Rock Night';
UPDATE 1
```

No error. The list now contains the same seat twice and one piece of garbage:

```text
        name        |    seats
--------------------+--------------
 Jazz at the Note   | A11,B2
 Monsoon Rock Night | A1,A1,banana
(2 rows)
```

`UNIQUE`, `NOT NULL`, foreign keys — every constraint from F5.3 works on **column values**.
None of them can reach inside a text value to say "each seat appears once" or "a seat must
look like a letter plus a number". The database's entire policing power is switched off.

### The fix is always the same

A list in a column means a missing table. One row per seat:

```sql
CREATE TABLE seat (
    event_id uuid NOT NULL REFERENCES event (id),
    seat_no  text NOT NULL,
    PRIMARY KEY (event_id, seat_no)
);
```

Now `count(*)` counts seats, `WHERE seat_no = 'A1'` finds exactly seat A1, and the primary
key makes a duplicate seat for the same event impossible. (You do not need to create this
table today; the shape is what matters.)

### Do it yourself

```bash
docker exec -it pg psql -U postgres -d zenith
```

Paste the `CREATE TABLE bad_event` and `INSERT` from the top of this lesson, then run the
three failures and watch each one: `count` says 1, `LIKE '%A1%'` wrongly matches `A11`, and
`'A1,A1,banana'` is accepted with `UPDATE 1`. Leave the table in place; Lesson 12 drops all
scratch tables together. **Done when** you can name three things this design cannot do:
count seats, find or lock one seat, constrain seats.

---

## Lesson 3 — The flat venue table, and normalising it

### The second bad table

This one does not break 1NF — every column holds one value. It breaks the "nothing but the
key" rule instead, by carrying the venue's facts inside every event row:

```sql
CREATE TABLE event_flat (
    id             uuid PRIMARY KEY,
    name           text NOT NULL,
    starts_at      timestamptz NOT NULL,
    venue_name     text NOT NULL,
    venue_city     text NOT NULL,
    venue_capacity integer NOT NULL
);

INSERT INTO event_flat VALUES
('ffffffff-0000-0000-0000-000000000001', 'Monsoon Rock Night', '2026-11-20 19:00+05:30', 'Riverside Arena', 'Hyderabad', 12000),
('ffffffff-0000-0000-0000-000000000002', 'Classical Evening',  '2026-12-05 18:30+05:30', 'Riverside Arena', 'Hyderabad', 12000),
('ffffffff-0000-0000-0000-000000000003', 'Sufi Night',         '2027-07-10 19:30+05:30', 'Riverside Arena', 'Hyderabad', 12000),
('ffffffff-0000-0000-0000-000000000004', 'Indie Fest',         '2027-07-24 17:00+05:30', 'Riverside Arena', 'Hyderabad', 12000),
('ffffffff-0000-0000-0000-000000000005', 'New Year Gala',      '2026-12-31 20:00+05:30', 'Riverside Arena', 'Hyderabad', 12000);
```

```text
        name        |   venue_name    | venue_city | venue_capacity
--------------------+-----------------+------------+----------------
 Monsoon Rock Night | Riverside Arena | Hyderabad  |          12000
 Classical Evening  | Riverside Arena | Hyderabad  |          12000
 New Year Gala      | Riverside Arena | Hyderabad  |          12000
 Sufi Night         | Riverside Arena | Hyderabad  |          12000
 Indie Fest         | Riverside Arena | Hyderabad  |          12000
(5 rows)
```

The fact "Riverside Arena, Hyderabad, capacity 12000" is written **five times**. Five events,
five copies, zero things keeping them equal.

### The normalised version

Split by what each column describes. Columns that describe the venue go to a `venue` table;
`event` keeps a pointer. This is exactly the design the `zenith` database has had since F5.2:

```sql
CREATE TABLE venue (
    id       uuid PRIMARY KEY,
    name     text NOT NULL,
    city     text NOT NULL,
    capacity integer NOT NULL
);

CREATE TABLE event (
    id        uuid PRIMARY KEY,
    venue_id  uuid NOT NULL,
    name      text NOT NULL,
    starts_at timestamptz NOT NULL,
    CONSTRAINT event_venue_fk FOREIGN KEY (venue_id) REFERENCES venue (id)
);
```

A **foreign key** (from F5.3, restated briefly): a column that holds another table's primary
key, plus a rule that the value must actually exist there. `venue_id` is the pointer;
`event_venue_fk` is the rule that stops an event pointing at a venue that is not there.

Now the venue's facts exist in **one row**. Five events at Riverside Arena are five rows in
`event`, all carrying the same `venue_id` — five copies of a *pointer*, which is fine,
because a pointer is not a fact about the venue. If the venue's name changes, no pointer
changes.

### What was given up

Honesty matters here: the flat table could answer "list events with their venue name" by
reading one table. The normalised design needs a **join** (F5.5, restated briefly: a join
matches rows of two tables by a rule, here `event.venue_id = venue.id`, and glues them into
one answer). A join is extra work on every read. That is the price of storing each fact
once. Lesson 5 weighs this trade properly.

### Do it yourself

Paste the `event_flat` block above into psql and check you see the 5 rows with the venue
facts repeated. Do **not** drop it yet — Lesson 4 breaks it on purpose. **Done when** you can
point at the exact columns that repeat a fact (`venue_name`, `venue_city`, `venue_capacity`)
and say where each belongs (`venue`).

---

## Lesson 4 — The update anomaly, caused on purpose

### Definition

An **update anomaly** is the bug duplication causes: a fact is stored in several rows, an
update changes only some of them, and the copies now disagree. No constraint fires, no error
appears, because each copy is an independent value and every row is individually valid.

### Causing one

The venue rebrands: "Riverside Arena" becomes "Riverside Arena and Convention Centre".
Someone updates the flat table but their `WHERE` only catches three of the five rows —
mistyped filter, partial list, crashed script halfway, it happens constantly in real systems:

```sql
UPDATE event_flat
SET venue_name = 'Riverside Arena and Convention Centre'
WHERE name IN ('Monsoon Rock Night', 'Sufi Night', 'Indie Fest');
```

```text
UPDATE 3
```

Three rows changed, two missed. Now ask the database how many venues there are:

```text
SELECT DISTINCT venue_name FROM event_flat;

              venue_name
---------------------------------------
 Riverside Arena and Convention Centre
 Riverside Arena
(2 rows)

SELECT venue_name, count(*) AS events FROM event_flat GROUP BY venue_name;

              venue_name               | events
---------------------------------------+--------
 Riverside Arena and Convention Centre |      3
 Riverside Arena                       |      2
(2 rows)
```

**Two venues that are the same physical place.** Every page that groups, filters or counts by
venue is now wrong. And the key point: **there was no error at any point.** `UPDATE 3`
succeeded. Both names are valid text. The database enforced every rule it was given — the
design simply never told it that five copies of a venue name were supposed to be one fact.

### Why the normalised design makes this impossible

Same rename against the real tables, where the name exists once (wrapped in a transaction
and rolled back here, to leave the real data unchanged):

```text
BEGIN;
UPDATE venue SET name = 'Riverside Arena and Convention Centre'
WHERE id = '11111111-1111-1111-1111-111111111111';
UPDATE 1

SELECT e.name AS event, v.name AS venue
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE v.id = '11111111-1111-1111-1111-111111111111' ORDER BY e.name;

       event        |                 venue
--------------------+---------------------------------------
 Classical Evening  | Riverside Arena and Convention Centre
 Indie Fest         | Riverside Arena and Convention Centre
 Monsoon Rock Night | Riverside Arena and Convention Centre
 Sufi Night         | Riverside Arena and Convention Centre
(4 rows)

SELECT DISTINCT v.name
FROM event e JOIN venue v ON v.id = e.venue_id
WHERE v.id = '11111111-1111-1111-1111-111111111111';

                 name
---------------------------------------
 Riverside Arena and Convention Centre
(1 row)

ROLLBACK;
```

`UPDATE 1`. One row touched, and **every** event instantly shows the new name, because they
never stored the name — they store a pointer to the one place the name lives. A partial
rename cannot exist: there is nothing to partially rename. That is what normalisation buys.

(If you set the database up fresh from the F5.7 block, Riverside Arena has 2 events instead
of 4, so this join shows 2 rows. The point is identical: all of them, one distinct name.)

### Do it yourself

With `event_flat` still holding its 5 rows from Lesson 3:

1. Run the `UPDATE ... WHERE name IN (...)` above. Expect `UPDATE 3`.
2. Run `SELECT DISTINCT venue_name FROM event_flat;`. Expect **2 rows**.
3. Say out loud: *no error occurred*.
4. Run the `BEGIN; UPDATE venue ...; SELECT ...; ROLLBACK;` block against the real tables.
   Expect `UPDATE 1` and exactly **1** distinct name — then `ROLLBACK` puts the old name back.

**Done when** you have seen 2 distinct names in the flat table and 1 in the normalised one,
and can explain why no constraint could have saved the flat table.

---

## Lesson 5 — The other side: what denormalisation honestly buys

### The flat table's real advantage

Be fair to the design just demolished. Rendering a listing page from the flat table is:

```sql
SELECT name, venue_name, venue_city FROM event_flat ORDER BY starts_at;
```

One table, no join. The normalised version must join `event` to `venue` every single time
anyone looks at the list. On one laptop with seven events the difference is nothing; on a
site serving thousands of listing-page reads per second, a join the database runs thousands
of times per second is real work that costs real money. Fewer tables touched per read is a
genuine, measurable advantage.

### Definition

**Denormalisation** is doing this on purpose: deliberately duplicating a fact into the table
that readers hit, to make reads cheaper, while accepting the duty of keeping the copies in
step yourself — because Lesson 4 proved the database will not do it for you.

### When it is the right choice

> **Accept duplication when reads vastly outnumber writes and something reliable keeps the
> copies in step.**

That sentence is the whole decision. Unpack it:

- **Reads vastly outnumber writes.** A venue renames itself once in years; its listing page
  is read millions of times. Paying a tiny write-time cost to save a join on every read is a
  good trade *for that ratio*. If the data changed constantly, it would be a terrible one.
- **Something reliable keeps the copies in step.** Not "we will remember to update both
  places" — a *mechanism*. Examples: a database trigger; application code that updates both
  rows inside one transaction; or an event feed, where the venue service publishes
  "venue renamed" and a listener rewrites every affected copy. In Phase 6 this exact pattern
  appears as the **CQRS read model**: a separate, denormalised table (or service) built
  purely for fast reads, kept in step by replaying events from the tables that own the truth.

Without the second half, denormalisation is just Lesson 4's bug waiting to happen. The flat
table after the botched rename *is* a read model whose keeper fell asleep.

### The honest summary of the trade

| | Normalised | Denormalised |
| --- | --- | --- |
| Each fact stored | once | many times |
| Update anomaly | impossible | your job to prevent |
| Writes | simple, one place | must fan out to every copy |
| Reads | need joins | one table, no join |
| Right when | correctness first (the default) | reads vastly outnumber writes **and** a mechanism syncs copies |

### Do it yourself

Run the no-join read against your broken flat table:

```sql
SELECT name, venue_name, venue_city FROM event_flat ORDER BY starts_at;
```

Notice two things at once: no join was needed (the advantage), and the output happily shows
both spellings of the venue (the cost, already incurred). Then write your own one-sentence
rule for when you would accept duplication, out loud or on paper. **Done when** your sentence
contains both halves: the read/write ratio and the syncing mechanism.

---

## Lesson 6 — One-to-many: the foreign key lives on the many side

### Definition

A **one-to-many** relationship: one event has many price tiers, and each price tier belongs
to exactly one event. It is modelled with a foreign key column on the **many** side — the
child. The `zenith` database has had this shape since F5.2:

```text
event (one)  ◄─────  price_tier (many)
id                   event_id REFERENCES event(id)
```

Each `price_tier` row carries `event_id`, the id of the one event it belongs to.

### Why the key must be on the many side, and cannot be the other way round

Say it out loud, because the task asks you to: **the child holds the key because a column
holds exactly one value.**

- A `price_tier` row needs to point at its one event. One pointer, one column, one value.
  Fits perfectly.
- Flip it: `event` holds pointers to its price tiers. One event has *many* tiers, so the
  column would need to hold `'tier1,tier2,tier3'` — **a list in a column**. That is the
  seats mistake from Lesson 2, with every consequence it had: cannot count, cannot
  constrain, cannot address one tier, and no foreign key can police values buried inside a
  text value.

So the direction is forced. "One side, many side" always resolves to: **the many side
carries the foreign key.** The one side carries nothing extra at all — an event does not
store its tiers; the tiers know their event, and `WHERE event_id = ...` finds them.

### Seeing it on the real tables

```text
\d price_tier
...
Foreign-key constraints:
    "price_tier_event_fk" FOREIGN KEY (event_id) REFERENCES event(id)
```

```sql
SELECT name, amount FROM price_tier
WHERE event_id = 'aaaaaaaa-0000-0000-0000-000000000001' ORDER BY amount;
```

```text
  name   |  amount
---------+-----------
 General | 1499.0000
 Balcony | 2499.0000
 VIP     | 4999.0000
(3 rows)
```

One event, many tiers, found through the child's pointer.

### Do it yourself

Run `\d price_tier` and the `SELECT` above. **Done when** you have said the sentence: *the
foreign key is on `price_tier` because each tier has exactly one event, so one column holds
it; `event` cannot hold tier ids because that would be a list in a column.*

---

## Lesson 7 — Many-to-many: the join table

### Definition

A **many-to-many** relationship: a user can have many roles, and a role can belong to many
users. Neither side can hold the foreign key — a pointer column on either table would need
to hold a list. The solution is a **third table**, called a **join table**, holding **one row
per pairing**: "this user has this role."

```text
app_user  ◄─────  user_role  ─────►  role
id                user_id, role_id   id
```

### The three tables

```sql
CREATE TABLE app_user (
    id           uuid PRIMARY KEY,
    email        text NOT NULL,
    display_name text NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_email_unique UNIQUE (email)
);

CREATE TABLE role (
    id   uuid PRIMARY KEY,
    name text NOT NULL,
    CONSTRAINT role_name_unique UNIQUE (name)
);

CREATE TABLE user_role (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    CONSTRAINT user_role_pk      PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_role_user_fk FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT user_role_role_fk FOREIGN KEY (role_id) REFERENCES role (id)
);
```

Read `user_role` slowly, because every choice in it is doing work:

- **Two foreign keys**, one to each side. Each row must point at a real user *and* a real role.
- **`PRIMARY KEY (user_id, role_id)`** — a **composite primary key**: the key is the *pair*
  of columns together. Repeating `(user_id, role_id)` is impossible, single values repeating
  is fine. Meera can appear in many rows (many roles) and `attendee` can appear in many rows
  (many users) — only the exact pairing "Meera + attendee" is limited to once. The join
  table needs no `id` column of its own; the pair *is* the identity, because the row *means*
  the pair.

### Inserting a user with two roles

```sql
INSERT INTO role (id, name) VALUES
('dddddddd-0000-0000-0000-000000000001', 'attendee'),
('dddddddd-0000-0000-0000-000000000002', 'organiser'),
('dddddddd-0000-0000-0000-000000000003', 'admin');

INSERT INTO app_user (id, email, display_name) VALUES
('cccccccc-0000-0000-0000-000000000001', 'meera@example.com', 'Meera'),
('cccccccc-0000-0000-0000-000000000002', 'arjun@example.com', 'Arjun');

INSERT INTO user_role (user_id, role_id) VALUES
('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001'),  -- Meera: attendee
('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000002'),  -- Meera: organiser
('cccccccc-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001');  -- Arjun: attendee
```

```text
INSERT 0 3
INSERT 0 2
INSERT 0 3
```

Reading it back is a two-hop join — user to pairing, pairing to role:

```sql
SELECT u.display_name, r.name AS role
FROM app_user u
JOIN user_role ur ON ur.user_id = u.id
JOIN role r       ON r.id = ur.role_id
ORDER BY u.display_name, r.name;
```

```text
 display_name |   role
--------------+-----------
 Arjun        | attendee
 Meera        | attendee
 Meera        | organiser
(3 rows)
```

Both directions work from the same three tables. Meera has two roles; and per role:

```text
SELECT r.name AS role, count(*) AS users
FROM user_role ur JOIN role r ON r.id = ur.role_id
GROUP BY r.name ORDER BY r.name;

   role    | users
-----------+-------
 attendee  |     2
 organiser |     1
(2 rows)
```

### The composite key earning its keep

Give Meera the attendee role a second time:

```text
INSERT INTO user_role (user_id, role_id)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001');

ERROR:  duplicate key value violates unique constraint "user_role_pk"
DETAIL:  Key (user_id, role_id)=(cccccccc-0000-0000-0000-000000000001, dddddddd-0000-0000-0000-000000000001) already exists.
```

"Has this role" is a yes/no fact; the composite key makes storing it twice impossible.

### Do it yourself

Paste the three `CREATE TABLE` statements, the three `INSERT` blocks, then the two `SELECT`
joins, then the duplicate insert. Expected: `INSERT 0 3 / 0 2 / 0 3`, the 3-row and 2-row
results above, then the `user_role_pk` error. These tables are keepers — do not drop them;
the Phase 1 domain needs users and roles. **Done when** the duplicate pairing is refused and
you can read both joins aloud: "users and their roles", "roles and their user counts".

---

## Lesson 8 — Surrogate keys against natural keys

### Definitions

- A **natural key** is a primary key made of real data: an email address, a phone number,
  a passport number. It means something in the real world.
- A **surrogate key** is a primary key with no meaning at all, such as a random `uuid`. Its
  only job is to identify the row.

The trap: natural keys look attractive ("email already uniquely identifies a user — free
primary key!"). But a primary key gets **copied into every table that references the row** —
that is what a foreign key is, a copy of the parent's key. And real data changes. When it
does, every copy must change with it.

### The experiment: email as primary key

```sql
CREATE TABLE nk_user (
    email        text PRIMARY KEY,
    display_name text NOT NULL
);

CREATE TABLE nk_booking (
    id         uuid PRIMARY KEY,
    user_email text NOT NULL REFERENCES nk_user (email),
    event_name text NOT NULL
);

INSERT INTO nk_user VALUES ('meera@oldmail.com', 'Meera');
INSERT INTO nk_booking VALUES
('99999999-0000-0000-0000-000000000001', 'meera@oldmail.com', 'Monsoon Rock Night'),
('99999999-0000-0000-0000-000000000002', 'meera@oldmail.com', 'Sufi Night');
```

Meera changes her email address — a completely ordinary life event:

```text
UPDATE nk_user SET email = 'meera@newmail.com' WHERE email = 'meera@oldmail.com';

ERROR:  update or delete on table "nk_user" violates foreign key constraint "nk_booking_user_email_fkey" on table "nk_booking"
DETAIL:  Key (email)=(meera@oldmail.com) is still referenced from table "nk_booking".
```

Refused. Two bookings still point at the old email, and the foreign key will not let the
value they point at vanish. Changing one fact about one person now requires a coordinated
rewrite of **every table that references her**:

```text
BEGIN;
INSERT INTO nk_user VALUES ('meera@newmail.com', 'Meera');
INSERT 0 1
UPDATE nk_booking SET user_email = 'meera@newmail.com' WHERE user_email = 'meera@oldmail.com';
UPDATE 2
DELETE FROM nk_user WHERE email = 'meera@oldmail.com';
DELETE 1
COMMIT;
```

Three statements, two tables, and this toy schema has only *one* referencing table. A real
system has bookings, payments, tickets, reviews, audit logs — miss one and the FK blocks
you; and in a system without the FK rules, miss one and you have orphaned data instead.
The email was never a good *identity*; it is merely a *fact about* Meera, and facts change.

### The same change with a surrogate key

```sql
CREATE TABLE sk_user (
    id           uuid PRIMARY KEY,
    email        text NOT NULL UNIQUE,
    display_name text NOT NULL
);

CREATE TABLE sk_booking (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES sk_user (id),
    event_name text NOT NULL
);
```

The `uuid` is the identity; the email is demoted to an ordinary column with a `UNIQUE`
constraint — still no two users with the same email, but no table copies it:

```text
UPDATE sk_user SET email = 'meera@newmail.com' WHERE id = '88888888-0000-0000-0000-000000000001';
UPDATE 1

SELECT b.event_name, u.email FROM sk_booking b JOIN sk_user u ON u.id = b.user_id;

     event_name     |       email
--------------------+-------------------
 Monsoon Rock Night | meera@newmail.com
 Sufi Night         | meera@newmail.com
(2 rows)
```

`UPDATE 1`. Bookings untouched — they point at the id, and the id never changes **because it
never meant anything**. That is the whole argument for surrogate keys in one line: *a key
that describes nothing never has a reason to change.*

### When natural keys are still fine

A join table's composite key (`user_role`) is two surrogate ids — fine. A country code table
keyed by `'IN'`, `'US'` — fine, those codes are stable standards. The rule of thumb: natural
keys are acceptable when the value is genuinely permanent and never user-editable. An email,
a username, a phone number is none of those things.

### Do it yourself

Paste both experiments from this lesson into psql. Expected: the natural-key `UPDATE` fails
with the `nk_booking_user_email_fkey` error; the three-statement fix reports
`INSERT 0 1`, `UPDATE 2`, `DELETE 1`; the surrogate-key version is a single `UPDATE 1` and
the join shows the new email. Insert the `sk_user` row with id
`88888888-0000-0000-0000-000000000001` and bookings `77777777-...-0001/0002` pointing at it
before the update. **Done when** you have felt the difference: three statements and two
tables against one statement and one row.

---

## Lesson 9 — UUID v4 against UUID v7: the key-shape detail

### The fact to register (not to measure — Phase 3 measures it)

Both are 128-bit identifiers that look alike. They differ in **where new values land when
sorted**:

- **UUID v4** is entirely random. Five real values from this machine's Postgres:

```text
SELECT gen_random_uuid() AS v4 FROM generate_series(1, 5);

                  v4
--------------------------------------
 291e1dfa-7451-4c90-a5da-27dbeba3bca6
 53504af4-f380-4388-b486-524e1402e3ce
 5a4fa159-775d-4ffc-bbd9-d26df6a07b38
 2cd185cd-a312-430d-8dfa-58bf4b3f1b95
 0a8c9420-326b-4272-8da8-1d844c9af69f
(5 rows)
```

Look at the first characters: `29`, `53`, `5a`, `2c`, `0a`. Created one after another, yet
sorted they land all over the place.

- **UUID v7** starts with the current time in milliseconds, then random bits. The time
  prefix grows as the clock ticks, so a value created later always sorts after one created
  earlier. The prefix is real and you can compute it:

```text
SELECT lpad(to_hex((extract(epoch from now()) * 1000)::bigint), 12, '0')                        AS ms_hex_now,
       lpad(to_hex((extract(epoch from now() + interval '1 minute') * 1000)::bigint), 12, '0')  AS ms_hex_one_minute_later;

  ms_hex_now  | ms_hex_one_minute_later
--------------+-------------------------
 01a1731d73f6 | 01a1731e5e56
(1 row)
```

A v7 uuid made now starts `01a1731d...`; one made a minute later starts `01a1731e...` —
later in time, later in sort order, every time.

### Why anyone cares

The primary key's index (Lesson 10) is a **B-tree**: a structure kept **sorted** on disk, in
pages. An insert must place the new key where it sorts:

- **v4 keys** sort to a random spot each time, so consecutive inserts touch pages scattered
  across the whole index — more pages read and written, more cache churn.
- **v7 keys** always sort to the end, so consecutive inserts keep appending to the same last
  pages — cheap, like adding to the back of a notebook instead of squeezing lines into the
  middle.

That is the entire point to carry into Task 1.10, where the JPA entities pick their id
strategy. No measuring today; Phase 3 does that.

### One real version note

Postgres 17 (this machine) ships `gen_random_uuid()` for v4 but has no v7 function:

```text
SELECT uuidv7();
ERROR:  function uuidv7() does not exist
```

Postgres 18 added `uuidv7()`. On 17, v7 values are generated by the application (Java
libraries do this) and inserted like any other uuid.

### Do it yourself

Run the three queries above. Expected: five scattered v4 values (yours will differ — they
are random), the two growing hex prefixes, and the `uuidv7() does not exist` error on
Postgres 17. **Done when** you can say: *v4 lands anywhere, v7 lands at the end, and the end
of a sorted structure is the cheap place to insert.*

---

## Lesson 10 — Indexes: what they are and what they cost

### Definition

An **index** is a separate structure the database builds and maintains next to a table, kept
**sorted** by the indexed column, with each entry pointing at its row. Like the index at the
back of a book: a sorted list of terms, each with page numbers, so you find "normalisation"
by jumping straight to N instead of reading every page. Without one, the database answers
`WHERE starts_at > ...` by reading **every row** of the table (a *sequential scan*). With
one, it jumps to the right place in the sorted structure and follows pointers. The default
index shape in Postgres is the **B-tree** from Lesson 9 — the sorted, paged structure.

### Creating one

The event listing will constantly ask "events after date X", so:

```text
CREATE INDEX idx_event_starts_at ON event (starts_at);
CREATE INDEX
```

`\d event` before and after — the Indexes section gains a line:

```text
Indexes:
    "event_pkey" PRIMARY KEY, btree (id)
    "idx_event_starts_at" btree (starts_at)
```

### The three facts that matter now

1. **A primary key gets an index automatically.** `event_pkey` above — nobody created it by
   hand. The database needs it anyway: to enforce "no duplicate id" it must check every
   insert fast, and a sorted structure is how. Same for `venue_pkey`, `price_tier_pkey`,
   `user_role_pk`.
2. **A `UNIQUE` constraint is enforced by an index.** Look at `price_tier`:

   ```text
   SELECT indexname FROM pg_indexes WHERE tablename = 'price_tier';

               indexname
   ----------------------------------
    price_tier_pkey
    price_tier_name_unique_per_event
    idx_price_tier_event_id
   (3 rows)
   ```

   `price_tier_name_unique_per_event` is the `UNIQUE (event_id, name)` constraint from
   F5.3, living as an index — because "is this value already present?" needs the same fast
   sorted lookup as a search does.
3. **A foreign key column usually wants an index, and Postgres does NOT add it for you.**
   Before this lesson, `event.venue_id` had no index — check the `\d event` output above:
   only `event_pkey` existed. Yet every join `ON v.id = e.venue_id` and every
   "events at this venue" query searches by `venue_id`. So:

   ```sql
   CREATE INDEX idx_event_venue_id      ON event (venue_id);
   CREATE INDEX idx_price_tier_event_id ON price_tier (event_id);
   ```

### The price

An index is a second copy of the indexed data that must **never go stale**. So:

- **Every write pays.** Each `INSERT`, each `DELETE`, and each `UPDATE` that touches an
  indexed column must also update **every index** on the table, immediately, inside the same
  statement. A table with one index does each write twice, roughly; with six indexes, seven
  times.
- **Disk is consumed.** Real numbers from this machine, with `event` holding just 7 rows:

  ```text
   table_size | pk_index | starts_at_index | venue_id_index
  ------------+----------+-----------------+----------------
   8192 bytes | 16 kB    | 16 kB           | 16 kB
  (1 row)
  ```

  The table is 8 kB; its three indexes already take 48 kB — six times the table. (Tiny
  tables exaggerate the ratio because an empty B-tree starts at 16 kB, but the direction is
  honest: indexes are real structures occupying real disk, growing as the table grows.)
- **An unused index is pure cost.** It speeds up nothing and still taxes every write and
  sits on disk. **Indexing every column "to be safe" is a known beginner mistake** — it
  makes every write slower to speed up reads that never happen. Phase 3 will have you find
  such indexes and drop them.

The decision rule: index what your queries actually search, join and sort by; nothing else
until a measurement says otherwise. For this schema today that is: the primary keys (free),
the unique rules (free), the two foreign keys (added above), and `starts_at` (the listing
query).

### Do it yourself

```bash
docker exec -it pg psql -U postgres -d zenith
```

1. `\d event` — see only `event_pkey` (if you have not created the indexes yet).
2. `CREATE INDEX idx_event_starts_at ON event (starts_at);` then `\d event` again — new line.
3. `CREATE INDEX idx_event_venue_id ON event (venue_id);` and
   `CREATE INDEX idx_price_tier_event_id ON price_tier (event_id);`
4. `SELECT indexname FROM pg_indexes WHERE tablename = 'price_tier';` — expect 3 rows.
5. The size query:

   ```sql
   SELECT pg_size_pretty(pg_relation_size('event'))               AS table_size,
          pg_size_pretty(pg_relation_size('event_pkey'))          AS pk_index,
          pg_size_pretty(pg_relation_size('idx_event_starts_at')) AS starts_at_index,
          pg_size_pretty(pg_relation_size('idx_event_venue_id'))  AS venue_id_index;
   ```

**Done when** you can recite the three free-index facts (primary key: automatic; unique:
enforced by one; foreign key: not automatic, add it yourself) and the price (every write
updates every index; disk).

---

## Lesson 11 — `EXPLAIN` exists

### The one thing to do

Run this and look at the output **without trying to understand it**:

```text
EXPLAIN SELECT * FROM event WHERE starts_at > now();

                      QUERY PLAN
------------------------------------------------------
 Seq Scan on event  (cost=0.00..1.10 rows=2 width=88)
   Filter: (starts_at > now())
(2 rows)
```

What you are registering: the database does not just run a query — it first writes itself a
**plan** (which tables to read, how, in what order, using which indexes), and `EXPLAIN`
shows that plan instead of running the query. Here the plan says `Seq Scan` — read the whole
table — even though Lesson 10 built an index on `starts_at`. That is the planner being
smart, not broken: with 7 rows, reading the whole table is cheaper than using any index.
Plans change as tables grow.

That is all for today. Phase 3 teaches how to read the numbers, when a plan is bad, and how
indexes change plans on tables big enough to matter.

### Do it yourself

Run the `EXPLAIN` line. Expect a `Seq Scan` plan with small cost numbers. Resist the urge to
decode it. **Done when** you can say what `EXPLAIN` shows (the plan, not the result) and
which phase teaches it properly (Phase 3).

---

## Cleaning up the scratch tables

The deliberately bad tables and the key-experiment tables were scratch; the many-to-many
trio and the indexes are keepers:

```sql
DROP TABLE bad_event;
DROP TABLE event_flat;
DROP TABLE nk_booking;
DROP TABLE nk_user;
DROP TABLE sk_booking;
DROP TABLE sk_user;
```

State of the database after this task:

```text
\dt
           List of relations
 Schema |    Name    | Type  |  Owner
--------+------------+-------+----------
 public | app_user   | table | postgres
 public | event      | table | postgres
 public | price_tier | table | postgres
 public | role       | table | postgres
 public | user_role  | table | postgres
 public | venue      | table | postgres
(6 rows)
```

New since F5.7: `app_user`, `role`, `user_role`, and three indexes — `idx_event_starts_at`,
`idx_event_venue_id`, `idx_price_tier_event_id`.

---

## Solutions to the subtasks

### S1 — three things you cannot do with `seats = 'A1,A2,A3'`

Built in Lesson 2, demonstrated with real queries. The three failures:

1. **Cannot count.** `SELECT name, count(seats) ... GROUP BY name` returned `1` for an event
   with three seats — the list is one opaque text value to the database.
2. **Cannot find (or lock, or sell) one seat.** `WHERE seats LIKE '%A1%'` wrongly matched
   the event whose list contains `A11`. There is no reliable way to address a single seat,
   which is the one thing a ticketing system must do.
3. **Cannot constrain.** `UPDATE ... SET seats = 'A1,A1,banana'` succeeded with `UPDATE 1` —
   a duplicate seat and garbage, and no constraint (`UNIQUE`, check, foreign key) can reach
   inside a text value to object.

Fix: one row per seat in a `seat` table keyed by `(event_id, seat_no)`.

### S2 — normalise the flat venue table

Lesson 3. Split by what each column describes: `venue_name`, `venue_city`, `venue_capacity`
describe the venue, so they move to `venue`; `event` keeps a `venue_id` foreign key. The
result is exactly the `venue`/`event` pair the database has had since F5.2 — the venue's
facts in one row, events holding only a pointer.

### S3 — the update anomaly, shown

Lesson 4. Five events at Riverside Arena in `event_flat`; renamed the venue in only three
rows (`UPDATE 3`); then:

```text
SELECT DISTINCT venue_name FROM event_flat;
→ 2 rows: 'Riverside Arena and Convention Centre' and 'Riverside Arena'
```

Two venue names for one physical place, and **no error at any point** — every row was
individually valid. In the normalised tables the same rename was `UPDATE 1` on `venue`, and
every event immediately showed the new name; a partial rename is impossible.

### S4 — the honest other side

Lesson 5. The flat table rendered a listing page with no join:
`SELECT name, venue_name, venue_city FROM event_flat` — one table read, and at thousands of
reads per second that saving is real. The sentence to keep:

> **I accept duplication when reads vastly outnumber writes and something reliable keeps the
> copies in step** — which is exactly the trade behind the CQRS read model in Phase 6.

### S5 — one-to-many, said out loud

Lesson 6. `event` to `price_tier`, foreign key `event_id` on `price_tier` (the child, the
many side). It cannot be the other way round because a column holds exactly one value: each
tier has one event, so one pointer fits in one column; an event has many tiers, so a pointer
column on `event` would have to hold a list — the Lesson 2 mistake again.

### S6 — many-to-many, built and working

Lesson 7. The three `CREATE TABLE` statements (`app_user`, `role`, `user_role` with
`PRIMARY KEY (user_id, role_id)` and two foreign keys), roles `attendee`/`organiser`/`admin`
inserted, and Meera given two roles:

```text
 display_name |   role
--------------+-----------
 Arjun        | attendee
 Meera        | attendee
 Meera        | organiser
```

The composite key refused a duplicate pairing with
`ERROR: duplicate key value violates unique constraint "user_role_pk"`.

### S7 — surrogate against natural key, felt

Lesson 8. With `email` as the primary key of `nk_user` and bookings referencing it, changing
the email was first **refused** (`violates foreign key constraint "nk_booking_user_email_fkey"`),
and fixing it took a three-statement transaction across two tables (`INSERT 0 1`, `UPDATE 2`,
`DELETE 1`). With a `uuid` key and email as a mere `UNIQUE` column, the same change was one
`UPDATE 1` and no other table noticed. A key that describes nothing never has a reason to
change.

### S8 — the key-shape detail, registered

Lesson 9. Real v4 values arrived scattered (`29…`, `53…`, `5a…`, `2c…`, `0a…`); v7 values
start with the millisecond clock (`01a1731d…` now, `01a1731e…` a minute later), so new keys
sort after old ones. In a B-tree primary-key index, scattered v4 inserts touch pages all
over the structure; v7 inserts append at the end, the cheap place. Registered for Task 1.10;
measured in Phase 3. (Side fact: Postgres 17 has `gen_random_uuid()` but no `uuidv7()` —
that arrived in Postgres 18.)

### S9 — the index, created, with its three facts and its price

Lesson 10. `CREATE INDEX idx_event_starts_at ON event (starts_at);` ran and appeared in
`\d event`. The three facts: a primary key gets an index automatically (`event_pkey`); a
`UNIQUE` constraint is enforced by one (`price_tier_name_unique_per_event`); a foreign key
column gets **nothing** automatically and usually wants one because joins search it
(`idx_event_venue_id`, `idx_price_tier_event_id` added). The price: every insert, update and
delete must update every index, and they occupy disk — on this machine the three `event`
indexes take 48 kB against the table's 8 kB. Indexing every column is pure cost on writes
for reads that never happen.

### S10 — `EXPLAIN`, run and left alone

Lesson 11. `EXPLAIN SELECT * FROM event WHERE starts_at > now();` printed a plan —
`Seq Scan on event (cost=0.00..1.10 rows=2 width=88)` — not results. Registered that the
planner exists and chose a full-table read here because 7 rows is too small for an index to
help. Phase 3 teaches reading it.

### S11 — the AI question, answered

> *"Explain database indexes to someone who has never used one, using the index at the back
> of a book as the starting point. Then tell me, for a ticketing schema of venue, event,
> price_tier and seat, which four indexes you would create first and what each one speeds up."*

**The explanation.** The index at the back of a book is a short, **sorted** list of terms,
each followed by page numbers. To find "normalisation" you do not read the book; you flip to
N in the index, read one line, and jump to the page. A database index is the same object: a
sorted side-structure of one column's values, each entry pointing to its row. Without it the
database reads the whole table for `WHERE name = 'Riverside Arena'`; with it, it jumps. Two
consequences follow from the book picture. First, the index must be **maintained**: a book
reprinted with a new chapter needs its index redone, and a table must update every index on
every insert, update and delete — indexes make reads cheaper by making writes more
expensive. Second, nobody indexes every word of a book — an index of everything is as long
as the book and helps nobody. You index the terms people actually look up.

**The four indexes for venue / event / price_tier / seat** (beyond the automatic primary-key
and unique-constraint indexes, which come free):

1. **`event (venue_id)`** — foreign key. Speeds up "all events at this venue" and every
   `event`-to-`venue` join; the venue page runs this constantly.
2. **`event (starts_at)`** — the listing query. "Upcoming events, soonest first" filters and
   sorts by this column on the most-read page of the whole product.
3. **`price_tier (event_id)`** — foreign key. The event page shows its tiers every time it
   renders; this is the join column.
4. **`seat (event_id)`** (or `seat (venue_id)` if seats hang off the venue) — foreign key.
   Rendering a seat map pulls every seat of one event/venue; selling checks one seat's
   status over and over.

All four are either foreign keys that joins search or the one column the busiest page
filters on — and nothing else, until a measurement in Phase 3 argues for more.

---

## Checkpoint answers

**1. What is an update anomaly, and what did you see when you caused one?**
It is the bug duplication causes: a fact stored in several rows gets updated in only some of
them, and the copies disagree with no error raised. Concretely: five `event_flat` rows
carried the venue name; `UPDATE 3` renamed three; `SELECT DISTINCT venue_name` then returned
**two names for one physical venue**. Every step succeeded, because each row is an
independent value and no constraint relates them.

**2. When is denormalisation the right choice, and what do you take on by choosing it?**
When reads vastly outnumber writes — a listing page read millions of times for a venue
renamed once in years — so that saving a join on every read is worth a more expensive write.
What you take on: the database no longer protects the duplicated fact, so **you** must
supply the mechanism that keeps every copy in step (a trigger, both-rows-in-one-transaction
application code, or an event feed rebuilding the read model, as in Phase 6's CQRS). Without
that mechanism you have simply scheduled the update anomaly.

**3. Why is a `uuid` usually a better primary key than an email address?**
Because a primary key is copied into every referencing table, and should therefore never
change. An email is a real-world fact the user can change; when Meera changed hers, the
update was refused by the foreign key and fixing it took three statements across two tables
— in a real system, across every table that mentions her. The `uuid` describes nothing, so
nothing can ever force it to change; the email becomes an ordinary `UNIQUE` column, free to
change with `UPDATE 1`.

**4. What is an index, and what does each extra index cost you?**
A separate sorted structure (a B-tree in Postgres) over one or more columns, each entry
pointing at its row, so the database can find matching rows without reading the whole table
— the index at the back of a book. Each extra index costs: **every** insert, delete, and
update of an indexed column must also update that index, immediately; it occupies disk (16 kB
minimum each, growing with the table); and if no query uses it, it is pure cost with zero
benefit. Hence: index what queries search, join, and sort by — not every column.

---

## Done when

- [x] The flat table normalised into `event` + `venue` with a foreign key (Lesson 3 / S2).
- [x] A working many-to-many relationship: `app_user`–`user_role`–`role`, composite primary
      key, duplicate pairing refused (Lesson 7 / S6).
- [x] An update anomaly caused on purpose: `UPDATE 3` on five copies, two distinct names for
      one venue, no error (Lesson 4 / S3).
- [x] Said what was lost by normalising: the no-join listing read; joins now cost work on
      every read, which is the price of storing each fact once (Lesson 5 / S4).

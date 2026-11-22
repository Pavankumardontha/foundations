# F5.3 — Constraints: the rules the database enforces for you

Notes for Task F5.3. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state (from F5.1):** container `pg` running with the `pgdata` volume,
database `zenith` created. The three project tables get built in this task (Lesson 5).

---

## Lesson 1 — `UNIQUE`, and composite `UNIQUE`

### Definition

A **UNIQUE constraint** forbids two rows from sharing a value in a column. A **composite
UNIQUE** applies the same rule to a *combination* of columns: the pair may not repeat, though
each column on its own may.

### Why it is needed

Some facts are unique by business law: one account per email, one tier named "VIP" per event,
one seat sold per event. A Java `if` (check, then insert) cannot enforce this: **two servers
run the same code at the same moment**, both check, both see nothing, both insert — two VIPs.
The check was correct; the world changed between its look and its insert. The database is the
one place every write passes through in order, and a UNIQUE constraint is checked at the
moment of writing — so the second insert fails no matter how many servers raced. A constraint
is a bug that cannot happen. Phase 5's no-oversell guarantee is exactly this mechanism.

### How it works, step by step

1. Single column, inline: `name text NOT NULL UNIQUE`. Postgres builds an index and rejects
   a second row with the same value. Auto-name: `table_column_key`.
2. Composite, own line in the table definition: `UNIQUE (event_id, name)` — the *pair* must
   be unique. Two events may each have "VIP"; one event may not have it twice.
3. Choosing: is the thing unique *in the world* or *within its parent*? Emails: world. Tier
   names: within their event.
4. Versus PRIMARY KEY: the key is UNIQUE + NOT NULL + "this is the identifier". One key per
   table; any number of UNIQUE rules besides — they express business facts, not identity.

### Example — real session in `zenith` (scratch tables, dropped afterwards)

Too strict — `UNIQUE` on `name` alone:

```text
postgres@localhost:zenith> CREATE TABLE scratch_tier (event_id uuid NOT NULL, name text NOT NULL UNIQUE);
CREATE TABLE
postgres@localhost:zenith> INSERT INTO scratch_tier VALUES ('aaaaaaaa-...-0001', 'VIP');
INSERT 0 1
postgres@localhost:zenith> INSERT INTO scratch_tier VALUES ('aaaaaaaa-...-0002', 'VIP');
duplicate key value violates unique constraint "scratch_tier_name_key"
DETAIL:  Key (name)=(VIP) already exists.
```

Correct — composite on the pair:

```text
postgres@localhost:zenith> CREATE TABLE scratch_tier (event_id uuid NOT NULL, name text NOT NULL, UNIQUE (event_id, name));
CREATE TABLE
postgres@localhost:zenith> INSERT INTO scratch_tier VALUES ('aaaaaaaa-...-0001', 'VIP');
INSERT 0 1
postgres@localhost:zenith> INSERT INTO scratch_tier VALUES ('aaaaaaaa-...-0002', 'VIP');
INSERT 0 1
postgres@localhost:zenith> INSERT INTO scratch_tier VALUES ('aaaaaaaa-...-0001', 'VIP');
duplicate key value violates unique constraint "scratch_tier_event_id_name_key"
DETAIL:  Key (event_id, name)=(aaaaaaaa-...-0001, VIP) already exists.
```

Walkthrough:

1. Version one: event 2's own VIP is rejected — "one VIP in the world" is the wrong rule.
   Too strict is also a bug: legitimate inserts die.
2. Version two: both events take VIP; different pairs, both accepted.
3. Event 1 tries VIP again: rejected; `DETAIL` shows the whole offending pair.
4. Both errors share the shape `duplicate key value violates unique constraint "..."`,
   differing only in the constraint name — which is how you tell which rule fired.

| Error text | Plain English |
| --- | --- |
| `duplicate key value violates unique constraint "scratch_tier_name_key"` | A second row tried to reuse a value that a single-column UNIQUE protects. |
| `... "scratch_tier_event_id_name_key"` + `Key (event_id, name)=(...)` | The *pair* already exists; the composite rule fired. |

### Important terms

| Term | Meaning |
| --- | --- |
| **UNIQUE** | No two rows share this value. Gets an index. Auto-name `table_column_key`. |
| **Composite UNIQUE** | `UNIQUE (a, b)` — the combination may not repeat. |
| **Check-then-insert race** | Two servers both look, both see nothing, both write. Java loses; the constraint does not. |

### Common mistakes

- **Enforcing uniqueness in the service layer.** Fails to the race, the second service, the
  data migration, and the 3 a.m. manual fix. All of them hit the constraint instead.
- **Making the rule too global.** `UNIQUE (name)` when the business means per-event;
  legitimate inserts fail and someone "fixes" it by dropping the rule.
- **Confusing UNIQUE with PRIMARY KEY.** UNIQUE: business fact, several allowed, may be
  nullable. PRIMARY KEY: the one identifier, never null.

### Where it is used

One user per email, one booking reference per payment, and the course's destination: one
`(event_id, seat_id)` pair in bookings — double-selling made impossible at the database. In
Phase 5, Java catches this violation and returns HTTP 409 to the second buyer.

### Link to the task

Subtask 1 includes `UNIQUE (event_id, name)` on `price_tier`. Checkpoint question 2 is this
lesson verbatim. Checkpoint question 4's answer: constraints hold against races, other
services, migrations, and manual fixes — every write passes through the database.

### Summary for revision

- UNIQUE = no repeats in a column; composite UNIQUE = no repeats of the combination.
- Ask: unique in the world, or within the parent? Write the rule to match the business.
- Check-then-insert in Java loses races; the constraint never does.
- Error shape: `duplicate key value violates unique constraint "<name>"`.

### Check questions, with answers

**Question 1.** `CREATE TABLE booking (id uuid PRIMARY KEY, event_id uuid NOT NULL,
seat_label text NOT NULL);` — seat 'A-12' for Jazz Night was sold twice. A teammate proposes
`seat_label text NOT NULL UNIQUE`. What does that break, and what is the right line?

**Answer.** Their fix means one 'A-12' across the entire system: the moment Jazz Night sells
'A-12', every other event is forbidden from selling *its own* seat 'A-12' — legitimate sales
start failing. The business rule is per event:
`UNIQUE (event_id, seat_label)` — the same seat label may exist for many events, but only
once within one event.

**Question 2.** Two app servers run identical Java at the same instant: both `SELECT` to
check whether booking reference 'PAY-778' exists, both see nothing, both `INSERT`. The column
is UNIQUE. What does each server experience, and why does the same code without the
constraint produce two rows?

**Answer.** With the constraint: one server's insert lands first and succeeds; the other's
insert is rejected by the database with
`duplicate key value violates unique constraint "..."` — an error its Java can catch and turn
into a proper response. Without the constraint: both SELECTs ran before either INSERT, so
both saw "no such reference" — truthfully, at that moment — and then both INSERTs succeeded.
The check was logically correct but the world changed in the gap between look and write;
only a rule enforced *at the write itself* survives that race.

### Your doubts

None for this lesson.

---

## Lesson 2 — Naming your constraints

### Definition

Every constraint has a name, and the name appears in the error when the constraint fires.
Choose it yourself with the `CONSTRAINT` keyword:
`CONSTRAINT price_tier_amount_non_negative CHECK (amount >= 0)`.

### Why it is needed

Violations are usually read in a production log at midnight, where the SQL and the failing
row are often stripped away. What survives is one sentence: *violates check constraint
"..."*. `scratch_unnamed_amount_check` says "some amount check failed";
`price_tier_amount_non_negative` *is* the diagnosis. Second reason: in Phase 5, Java catches
violations and decides responses (409, 400) *by constraint name* — chosen names are a stable
contract between schema and application; auto-generated ones are not.

### How it works, step by step

1. Unnamed rules get generated names from table + column(s) + kind: `scratch_pk_pkey`,
   `scratch_tier_event_id_name_key`, `scratch_event_venue_id_fkey`,
   `scratch_unnamed_amount_check`.
2. To name: `CONSTRAINT <name>` before the rule, inside the table definition:

```sql
CREATE TABLE price_tier (
    amount numeric(19,4) NOT NULL,
    CONSTRAINT price_tier_amount_non_negative CHECK (amount >= 0)
);
```

3. Works for every kind: `CONSTRAINT price_tier_name_unique_per_event UNIQUE (event_id,
   name)`; `CONSTRAINT event_venue_fk FOREIGN KEY (venue_id) REFERENCES venue(id)`.
   `NOT NULL` is generally left unnamed — its error already names the column.
4. Convention: table, then what is protected, then the rule in business words:
   `booking_one_seat_per_event`.

### Example — real session in `zenith` (scratch tables, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_unnamed (amount numeric(19,4) CHECK (amount >= 0));
CREATE TABLE
postgres@localhost:zenith> INSERT INTO scratch_unnamed VALUES (-5);
new row for relation "scratch_unnamed" violates check constraint "scratch_unnamed_amount_check"
DETAIL:  Failing row contains (-5.0000).

postgres@localhost:zenith> CREATE TABLE scratch_named (amount numeric(19,4), CONSTRAINT price_tier_amount_non_negative CHECK (amount >= 0));
CREATE TABLE
postgres@localhost:zenith> INSERT INTO scratch_named VALUES (-5);
new row for relation "scratch_named" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (-5.0000).
```

Walkthrough:

1. Unnamed: the violation reports the generated `"scratch_unnamed_amount_check"` —
   serviceable, vague.
2. Named: identical violation, but the error states the business rule itself.
3. Everything else is identical — naming changes exactly the part a log reader and a Java
   `catch` block use.
4. `DETAIL: Failing row contains (-5.0000)` — the `numeric(19,4)` scale visible in the wild.

### Important terms

| Term | Meaning |
| --- | --- |
| **`CONSTRAINT <name> <rule>`** | Declares a rule with a chosen name. |
| **Generated name** | Postgres's fallback: table + column(s) + kind (`_pkey`, `_key`, `_fkey`, `_check`). |
| **Name as contract** | Code and alerts reference constraint names; changing one is a breaking change. |

### Common mistakes

- **Auto-generating everything.** Your application ends up depending on names you never
  chose or wrote down.
- **Names that restate syntax.** `price_tier_check_1` is barely better. Name the business
  rule: `_non_negative`, `_unique_per_event`.
- **Forgetting names are public API.** Once Java catches a name to return 409, renaming is a
  coordinated breaking change.

### Where it is used

Every mature schema. Names appear in migration files (Flyway soon), violation-catching code,
and monitoring alerts — three places that break silently if a generated name changes shape.

### Link to the task

Subtask 2 verbatim, example included. Lesson 5 builds the real tables with every constraint
named; subtask 7 has you fish a constraint name out of a duplicate-key error.

### Summary for revision

- The constraint name is the only part of the error a midnight reader gets. Spend it well.
- `CONSTRAINT table_thing_rule` before the rule; works for CHECK, UNIQUE, FK.
- Names are a schema↔code contract; Phase 5 Java catches violations by name.
- NOT NULL stays unnamed; its error already names the column.

### Check questions, with answers

**Question 1.** A 3 a.m. log shows one line:
`ERROR: new row for relation "price_tier" violates check constraint
"price_tier_amount_non_negative"`. What do you already know, and what would the incident look
like if the table had been created as
`CREATE TABLE price_tier (amount numeric(19,4) CHECK (amount >= 0));`?

**Answer.** You already know the whole story: something attempted to write a negative amount
into `price_tier`, and the database refused — no bad data reached a customer; the remaining
work is finding *which caller* sent a negative number. Unnamed, the error would say
`"price_tier_amount_check"` — you would know a check involving `amount` failed, but the rule
itself (non-negative? some range? a format?) requires opening the schema to find out, at
3 a.m., before the diagnosis can even start.

**Question 2.** Rewrite the UNIQUE line of

```sql
CREATE TABLE booking (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    seat_label text NOT NULL,
    UNIQUE (event_id, seat_label)
);
```

to carry the name `booking_one_seat_per_event`, and say why the name matters to Phase 5 Java.

**Answer.** `CONSTRAINT booking_one_seat_per_event UNIQUE (event_id, seat_label)`. The Java
that catches this violation to answer the second buyer with HTTP 409 identifies the rule *by
this name* — a name you chose is a stable, readable contract, while the generated
`booking_event_id_seat_label_key` merely restates columns and could change shape if the
schema is rebuilt differently.

### Your doubts

None for this lesson.

---

## Lesson 3 — `DEFAULT`: values that fill themselves

### Definition

A **DEFAULT** is a value or expression attached to a column, used automatically whenever an
insert does not supply that column. `created_at timestamptz NOT NULL DEFAULT now()`: if the
insert says nothing about `created_at`, write the current moment.

### Why it is needed

Some columns must be correct on every insert forever (`created_at`). Relying on every
application, script, and 3 a.m. manual fix to pass it is the "every caller must behave" trap.
A DEFAULT moves the duty into the table. Pairs well with `uuid`
(`DEFAULT gen_random_uuid()`) and with honest booleans
(`is_published boolean NOT NULL DEFAULT false`).

### How it works, step by step

1. In the column definition: `created_at timestamptz NOT NULL DEFAULT now()`. The expression
   is evaluated at insert time.
2. Fires only when the column is **omitted**. A supplied value wins.
3. **An explicit NULL is not omitting.** `VALUES (NULL)` bypasses the default and genuinely
   tries to insert NULL — hence the pattern `NOT NULL DEFAULT ...`: DEFAULT fills the
   forgetful, NOT NULL blocks the explicitly wrong.
4. Inserts only. `DEFAULT now()` stamps birth, not modification; `updated_at` needs other
   machinery.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_def (id uuid DEFAULT gen_random_uuid(), created_at timestamptz NOT NULL DEFAULT now(), note text);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_def (note) VALUES ('only the note supplied');
INSERT 0 1
postgres@localhost:zenith> SELECT * FROM scratch_def;
| 69a7e10d-05b0-4574-a775-68f4cfcc3f00 | 2026-10-24 17:46:57.942841+05:30 | only the note supplied |

postgres@localhost:zenith> INSERT INTO scratch_def (id, created_at, note) VALUES ('11111111-1111-1111-1111-111111111111', '2020-01-01 00:00:00+00', 'everything supplied');
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_def (created_at, note) VALUES (NULL, 'explicit NULL');
null value in column "created_at" of relation "scratch_def" violates not-null constraint
DETAIL:  Failing row contains (f3557698-2e28-428b-b5b7-ad7731e5339c, null, explicit NULL).
```

Walkthrough:

1. First insert: both omitted columns filled themselves — fresh uuid, exact insert moment
   (displayed in IST; stored as the UTC instant).
2. Second insert: both defaults stepped aside; the supplied values stored.
3. Third insert: `created_at` supplied *as NULL* — default did not fire; NOT NULL rejected
   the row. Inside the `DETAIL`, the failing row already had a minted id: the omitted `id`
   default fired even as the row died. Omitted → fills; explicit NULL → stands back.

### Important terms

| Term | Meaning |
| --- | --- |
| **DEFAULT** | Expression used when an insert omits the column. Evaluated per insert. |
| **`now()`** | The current moment, as a `timestamptz` instant. |
| **Omitted vs explicit NULL** | Omitted triggers the default; explicit NULL bypasses it. |

### Common mistakes

- **Believing DEFAULT validates.** It only fills. Validation is types, NOT NULL, CHECK.
- **Believing DEFAULT prevents NULL.** Explicit NULL sails past it; without NOT NULL it
  lands. `created_at` wants both halves.
- **Expecting `DEFAULT now()` to track updates.** Birth stamp only.

### Where it is used

`created_at timestamptz NOT NULL DEFAULT now()` on effectively every table everywhere;
`DEFAULT false` on flags; `DEFAULT gen_random_uuid()` where database-minted ids are wanted.

### Link to the task

Each of the three tables in subtask 1 carries `created_at timestamptz NOT NULL DEFAULT
now()`; ids stay application-minted (`id uuid PRIMARY KEY`, no default), matching the task's
starter SQL.

### Summary for revision

- DEFAULT fills omitted columns at insert time; supplied values win.
- Explicit NULL bypasses the default — pair with NOT NULL.
- Insert-only; never an update tracker.
- Standard line: `created_at timestamptz NOT NULL DEFAULT now()`.

### Check questions, with answers

**Question 1.** Given:

```sql
CREATE TABLE event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    is_published boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

(a) `INSERT INTO event (name) VALUES ('Jazz Night');` and
(b) `INSERT INTO event (name, is_published) VALUES ('Tech Meetup', NULL);` — what ends up
where?

**Answer.** (a) Accepted. `id`: a fresh random uuid (default fired); `name`: 'Jazz Night';
`is_published`: `false` (default); `created_at`: the insert moment (default). (b) Rejected.
`is_published` was supplied as explicit NULL, so its default stood back and NOT NULL refused
the row — nothing was stored. The error names `is_published` and the not-null constraint.

**Question 2.** A teammate: "`created_at` has `DEFAULT now()`, so NOT NULL is redundant."
What insert proves them wrong, and what lands if NOT NULL is dropped?

**Answer.** `INSERT INTO event (name, created_at) VALUES ('X', NULL);` — the column is
supplied, as NULL, so the default does not fire. With NOT NULL present, the row is rejected.
With NOT NULL dropped, the insert *succeeds* and NULL lands in `created_at`: the row's birth
time is missing forever, and every query that sorts or filters by creation time mishandles
it. DEFAULT and NOT NULL solve different problems; the column needs both.

### Your doubts

None for this lesson.

---

## Lesson 4 — `ON DELETE`: what happens to children when the parent dies

### Definition

`ON DELETE` is a setting on a foreign key answering: when a parent row is deleted, what
happens to children pointing at it? **Refuse** (the default, spelled `RESTRICT`), **delete
the children too** (`CASCADE`), or **keep children, blank the pointer** (`SET NULL`).

### Why it is needed

Refusal protects, but sometimes child deletion is wanted (a seat map's seats) and sometimes
keeping pointer-less children is honest (reviews by a deleted user). It is a per-relationship
business decision made once, in the schema — not improvised by each application. Wrong one
way blocks legitimate cleanups; wrong the other way silently destroys data. CASCADE is
powerful and easy to regret.

### How it works, step by step

1. Rides on the FK: `venue_id uuid NOT NULL REFERENCES venue(id) ON DELETE CASCADE`.
   Writing nothing = refuse.
2. **Refuse (default/`RESTRICT`):** parent DELETE fails — *still referenced from table ...*.
   Deal with children first, explicitly.
3. **`CASCADE`:** parent DELETE succeeds; every pointing child dies too, chained further if
   children also cascade. Output reports only `DELETE 1` — the parent. No list of what died.
4. **`SET NULL`:** parent DELETE succeeds; children survive with the pointer NULLed.
   Requires the FK column to be nullable — contradicts NOT NULL and errors at delete time
   otherwise. Only for relationships where "pointing at nothing" is a designed state.

### Example — real session in `zenith` (scratch tables, dropped afterwards)

```text
-- Default: refuse
postgres@localhost:zenith> DELETE FROM sv1 WHERE id = 'aaaaaaaa-...';
update or delete on table "sv1" violates foreign key constraint "se1_venue_id_fkey" on table "se1"
DETAIL:  Key (id)=(aaaaaaaa-...) is still referenced from table "se1".

-- CASCADE ('River Hall' had TWO events)
postgres@localhost:zenith> DELETE FROM sv2 WHERE id = 'bbbbbbbb-...';
DELETE 1
postgres@localhost:zenith> SELECT count(*) AS events_left FROM se2;
| 0 |

-- SET NULL (nullable venue_id)
postgres@localhost:zenith> DELETE FROM sv3 WHERE id = 'cccccccc-...';
DELETE 1
postgres@localhost:zenith> SELECT name, venue_id FROM se3;
| Food Fest | <null> |
```

Walkthrough:

1. No `ON DELETE` declared → refusal with constraint name and referencing table. Nothing
   decided for you.
2. CASCADE: `DELETE 1` counted only the venue; two events died unnamed in any output. Scale
   it: 300 events with cascading tiers, one mistyped WHERE at 3 a.m. — `DELETE 1`, catalogue
   gone.
3. SET NULL (on a deliberately nullable pointer): 'Food Fest' survived as "assigned to no
   venue" — whether that sentence makes business sense decides whether SET NULL is ever
   right for the relationship.
4. All three execute in the database, uniformly, regardless of which application issued the
   DELETE.

### Important terms

| Term | Meaning |
| --- | --- |
| **Refuse / `RESTRICT`** | Parent delete fails while children exist. The default. |
| **`ON DELETE CASCADE`** | Children die with the parent, silently, chained. |
| **`ON DELETE SET NULL`** | Children survive; pointer blanked. Needs a nullable FK. |

### Common mistakes

- **CASCADE everywhere so deletes "just work".** Wrong deletes also just work. The refusal
  was information: something still depends on this.
- **SET NULL on a NOT NULL FK.** Contradiction; errors when the blanking is attempted.
- **Deciding in application code.** The second service, the migration, the manual fix skip
  your dance. The rule lives where every write passes.

### Where it is used

Rule of thumb: CASCADE only where the child is a meaningless fragment without its parent
(seat → seat_map); refuse wherever money or history hangs off the children (event → venue,
price_tier → event); SET NULL only where "unassigned" is a real designed state.

### Link to the task

Subtasks 5 and 6 perform this on the real tables (Lesson 8). Checkpoint question 3's answer:
refusing is right for venues because events carry sold tickets and history — their deletion
must be a conscious, multi-step human decision, never a side effect. Project decisions:
`event → venue` refuses; `price_tier → event` refuses; a future `seat → seat_map` cascades.

### Summary for revision

- Three behaviours: refuse (default), cascade (children die silently), set null (pointer
  blanked, needs nullable FK).
- Per-relationship business decision, made once in the schema.
- CASCADE reports only the parent's deletion. Easy to regret.
- Money or history on children → refuse.

### Check questions, with answers

**Question 1.** `seat.seat_map_id uuid NOT NULL REFERENCES seat_map(id) ON DELETE CASCADE`.
Deleting the only seat map reports `DELETE 1`. What happened to the seats, what did Postgres
print about them, and why is CASCADE defensible here but dangerous for venues?

**Answer.** Every seat pointing at that map was deleted in the same instant; Postgres printed
nothing about them — `DELETE 1` counts only the parent. It is defensible because a seat is a
meaningless fragment without its map: no money, no history, no independent life — recreating
the map recreates seats. Venue→event is different: events carry sold tickets, payments, and
customers, so their silent disappearance is data loss, not cleanup.

**Question 2.** A teammate proposes
`event.venue_id uuid NOT NULL REFERENCES venue(id) ON DELETE SET NULL`. One mechanical
problem, one business problem?

**Answer.** Mechanical: `NOT NULL` and `SET NULL` contradict — when a venue is deleted,
Postgres tries to blank `venue_id` and the NOT NULL constraint fails the operation. Business:
even made nullable, "an event assigned to no venue" is nonsense in ticketing — customers
hold tickets to a place. Venue deletion must stay a refused, conscious operation.

### Your doubts

None for this lesson.

---

## Lesson 5 — Building the real tables, every constraint named (subtasks 1 and 2)

### Definition

This lesson creates the project's three permanent tables — `venue`, `event`, `price_tier` —
in `zenith`, with every rule from the task: primary keys, NOT NULLs, named CHECKs, a named
foreign key per relationship, and the named composite UNIQUE. Not scratch tables; they stay.

### Why it is needed

Every later task builds on these tables: F5.4 queries them, F5.6 aggregates them. And writing
one complete `CREATE TABLE` with all constraint kinds in one place is where separate lessons
become one skill.

### The DDL

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
    id         uuid PRIMARY KEY,
    venue_id   uuid NOT NULL,
    name       text NOT NULL,
    starts_at  timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
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
```

Design notes:

- Foreign keys use the long named form (`CONSTRAINT ... FOREIGN KEY ... REFERENCES ...`)
  because the names (`event_venue_fk`, `price_tier_event_fk`) appear in errors and in Java.
- Both FKs deliberately say nothing about `ON DELETE` — refusal is the chosen behaviour.
- Primary keys keep their auto-names (`venue_pkey` etc.); those are already clear.
- `seat_map_hints jsonb` is the schema's single, deliberate nullable-jsonb column.
- Creation order is `venue → event → price_tier`: each references the one before.

### Example — real run, verified with `\d price_tier` (trimmed)

```text
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

And `\d venue` ends with a line nobody wrote:
`Referenced by: TABLE "event" CONSTRAINT "event_venue_fk" FOREIGN KEY (venue_id) REFERENCES venue(id)`.

### Walkthrough — what Postgres added

1. NOT NULL shows on `id` though we never typed it there: PRIMARY KEY added it.
2. `timestamptz` displays under its formal name `timestamp with time zone`, `now()` in the
   Default column.
3. *Two* indexes on `price_tier`: `_pkey`, plus one for the composite UNIQUE — uniqueness is
   checked through an index on every insert.
4. The CHECK is restated as `amount >= 0::numeric` — same rule, Postgres's spelling, with an
   explicit type conversion.
5. `venue`'s `Referenced by:` line is the FK seen from the parent's side — the machinery
   behind refused deletes.

### Common mistakes

- **Wrong database.** Check the prompt says `:zenith`. Built elsewhere, the tables "vanish"
  when you connect correctly.
- **Wrong order.** `price_tier` first fails: `relation "event" does not exist`.
- **Re-running.** `relation "venue" already exists` — harmless; it means this is done.
- **Piping SQL into `pgcli`.** Piped mode runs each *line* separately, so multi-line
  statements break into a column of syntax errors. Interactive typing is fine (it waits for
  the `;`); scripts should use `psql`.

### Do it yourself (subtasks 1 and 2)

1. Connect: `pgcli postgresql://postgres:dev@localhost:5432/zenith`. Prompt must end
   `:zenith`.
2. Paste the three `CREATE TABLE` statements above, **in order** (`venue`, `event`,
   `price_tier`). Expect `CREATE TABLE` after each.
3. Verify: `\dt` → three tables; then `\d venue`, `\d event`, `\d price_tier`.
4. Point at: the NOT NULL on `id` you never typed; the `now()` default; the second index on
   `price_tier`; the `Referenced by:` line on `venue`; your four named constraints.
5. In `NOTES.md`: one line on what Postgres added unasked (NOT NULLs via PRIMARY KEY, the
   indexes, `::numeric`), one line on anything you forgot versus your paper design.
6. Errors: `relation "..." does not exist` → wrong order; `already exists` → that one is
   done, continue.

**Done when:** `\dt` lists all three tables, all four named constraints appear in `\d`
output, and the two `NOTES.md` lines are written.

### Summary for revision

- Order: parents before children — `venue → event → price_tier`.
- Named form: `CONSTRAINT <name> CHECK/UNIQUE/FOREIGN KEY ...`.
- `\d` review: Postgres adds NOT NULL under PRIMARY KEY, an index per PK and per UNIQUE, and
  a `Referenced by:` line on parents.
- No `ON DELETE` written = refusal, and here that is a decision, not an omission.

### Check questions, with answers

**Question 1.** `\d price_tier` shows two indexes: `price_tier_pkey` and
`price_tier_name_unique_per_event`. Nobody typed the word "index". Where did each come from,
and what does each make impossible?

**Answer.** `price_tier_pkey` comes from `PRIMARY KEY`: every primary key is enforced through
a btree index so each insert can instantly check "does this id already exist?" — it makes a
second row with the same `id` impossible (and a NULL `id` impossible). The second comes from
`UNIQUE (event_id, name)`, also enforced through an index: it makes two tiers with the same
name *inside the same event* impossible, while 'General' may still appear on many different
events.

**Question 2.** A teammate runs the three statements reversed: `price_tier`, `event`,
`venue`. What exact error does the first produce, and why is the order not a style
preference?

**Answer.** `ERROR:  relation "event" does not exist`. The `price_tier` DDL contains
`FOREIGN KEY (event_id) REFERENCES event(id)`, and Postgres checks the referenced table
exists at creation time. Children name their parents inside their own DDL, so parents must
exist first — reversed order does not merely look odd, it cannot run.

### Your doubts

None for this lesson.

---

## Lesson 6 — Seed data: giving the tables something to hold (subtask 3)

### Definition

**Seed data** (also **fixture data**) is a small set of hand-written rows inserted into a
development database so there is something real to query, break, and experiment on. Not
production data — scaffolding you know by heart.

### Why it is needed

Empty tables teach nothing further. The remaining subtasks break constraints and test
deletes against rows; F5.4–F5.6 query and aggregate them. Seeding once with chosen values
means every later error mentions names you recognise.

### How it works

1. **Fixed ids, written by hand.** `'11111111-1111-1111-1111-111111111111'` is a valid uuid
   (any 32 hex digits in the 8-4-4-4-12 shape). Fixed ids let children name their parents
   directly, and re-running the script on any machine yields an identical database. Real
   application code generates random ids; fixtures use fixed ones. Both are normal.
2. **Multi-row INSERT.** One `INSERT ... VALUES` carries several rows — one parenthesised
   group per row, comma-separated.
3. **Insert order = creation order.** Parents before children: venues, events, tiers. The
   foreign key checks inserts too, not just deletes.

### The seed script (run for real; this is the project's permanent data)

```sql
INSERT INTO venue (id, name, city, capacity) VALUES
('11111111-1111-1111-1111-111111111111', 'Riverside Arena', 'Hyderabad', 12000),
('22222222-2222-2222-2222-222222222222', 'The Blue Note',   'Mumbai',      350);

INSERT INTO event (id, venue_id, name, starts_at) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Monsoon Rock Night', '2026-11-20 19:00+05:30'),
('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Classical Evening',  '2026-12-05 18:30+05:30'),
('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'Jazz at the Note',   '2026-11-28 21:00+05:30');

INSERT INTO price_tier (id, event_id, name, amount) VALUES
('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'General',     1499.0000),
('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'VIP',         4999.0000),
('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000002', 'General',      999.0000),
('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000003', 'General',     1999.0000),
('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000003', 'Front Table', 3499.0000);
```

Replies: `INSERT 0 2`, `INSERT 0 3`, `INSERT 0 5`.

### Example — real spot-checks

```text
SELECT name, starts_at, created_at FROM event ORDER BY starts_at;
        name        |       starts_at        |          created_at
--------------------+------------------------+-------------------------------
 Monsoon Rock Night | 2026-11-20 13:30:00+00 | 2026-10-24 13:04:21.939416+00
 Jazz at the Note   | 2026-11-28 15:30:00+00 | 2026-10-24 13:04:21.939416+00
 Classical Evening  | 2026-12-05 13:00:00+00 | 2026-10-24 13:04:21.939416+00

SELECT name, amount FROM price_tier ORDER BY amount;
    name     |  amount
-------------+-----------
 General     |  999.0000
 General     | 1499.0000
 General     | 1999.0000
 Front Table | 3499.0000
 VIP         | 4999.0000
```

A child naming a missing parent — run for real:

```text
ERROR:  insert or update on table "event" violates foreign key constraint "event_venue_fk"
DETAIL:  Key (venue_id)=(99999999-9999-9999-9999-999999999999) is not present in table "venue".
```

### Walkthrough

1. The timestamps changed clothes, not meaning: the script wrote `'2026-11-20 19:00+05:30'`;
   the SELECT shows `13:30:00+00`. Same instant, displayed in the server's UTC zone — what
   `timestamptz` is for.
2. `created_at` was never in the script; `DEFAULT now()` filled it. All three events carry
   the identical microsecond: `now()` is stamped once per statement, not per row.
3. 'General' appears three times without complaint — the uniqueness rule is composite,
   `(event_id, name)`, and these belong to three different events.
4. The refusal names the chosen constraint, `event_venue_fk` — the error alone identifies
   the violated relationship.

### Common mistakes

- **Running the seed script twice.** Real output:

  ```text
  ERROR:  duplicate key value violates unique constraint "venue_pkey"
  DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) already exists.
  ```

  Fixed ids plus the primary key prevent a double-run from silently doubling the data.
- **Children before parents.** The FK error above, for the whole script.
- **Supplying `created_at` by hand.** Works, but forfeits the default and invites made-up
  times. Omit it.

### Where it is used

Every project formalises this: Java test fixtures (known rows loaded before each test),
staging demo data, a `seed.sql` after migrations. Fixed, recognisable ids ("the 1111...
venue") keep test failures readable for years.

### Link to the task

Subtask 3 exactly: two venues, three events (two at Riverside Arena, one at The Blue Note),
five price tiers. Permanent data — Lessons 7–8 break and delete against it; F5.4–F5.6 query
it.

### Do it yourself (subtask 3)

1. Connect: `pgcli postgresql://postgres:dev@localhost:5432/zenith`.
2. Paste the three INSERTs from the script above, in order. Expect `INSERT 0 2`,
   `INSERT 0 3`, `INSERT 0 5`.
3. Verify: `SELECT count(*) FROM venue;` → 2; `FROM event` → 3; `FROM price_tier` → 5.
4. Run `SELECT name, starts_at FROM event;` — you typed `+05:30` times; note the displayed
   `+00` form and why it is the same instant.
5. Errors: `duplicate key ... "venue_pkey"` → data already in, skip to verification;
   `violates foreign key constraint` → a parent insert was skipped, run in order.

**Done when:** the three counts read 2, 3, 5.

### Summary for revision

- Seed/fixture data: hand-written rows, fixed hand-written uuids, repeatable on any machine.
- One INSERT, many rows; parents before children.
- `timestamptz` redisplays the same instant in the session's zone; `now()` stamps once per
  statement.
- A double-run is refused by the primary key — by design.

### Check questions, with answers

**Question 1.** Tables: `venue (id uuid PRIMARY KEY, name text NOT NULL)` and
`event (id uuid PRIMARY KEY, venue_id uuid NOT NULL REFERENCES venue(id), name text NOT
NULL)`. A seed script inserts the event rows first, then the venues, ids all correct and
matching. What happens, and why does *insert* order mirror table-*creation* order?

**Answer.** The first insert fails:
`insert or update on table "event" violates foreign key constraint ... Key (venue_id)=(...)
is not present in table "venue"`. A foreign key is checked at insert time: the parent row
must already exist when the child lands — matching ids "eventually" is not enough. The
mirror is no coincidence: at creation time the referenced *table* must exist first; at
insert time the referenced *row* must exist first. Children name parents, so parents go
first, always.

**Question 2.** A seed script inserted `starts_at` as `'2026-11-20 19:00+05:30'` into a
`timestamptz` column. SELECT later shows `2026-11-20 13:30:00+00`. A teammate says the
database corrupted the time. What do you tell them — and what *would* have been lost with a
plain `timestamp` column?

**Answer.** Nothing is corrupted: 19:00 at offset +05:30 and 13:30 UTC are the same instant.
`timestamptz` stores the moment itself and *displays* it in the session's time zone (UTC
here); `SET time zone 'Asia/Kolkata';` would show `19:00:00+05:30` again. With plain
`timestamp`, the `+05:30` would have been silently discarded at insert — the column would
hold the bare wall-clock text `19:00` with no record of where on Earth that clock was, and
the actual moment would be unrecoverable.

### Your doubts

None for this lesson.

---

## Lesson 7 — Breaking all five constraints on purpose (subtasks 4 and 7)

### Definition

A deliberate failure tour: one insert per constraint, each written to be refused, each error
read and recorded. Five constraint kinds, five different messages — plus fishing the
**constraint name** out of a duplicate-key error, which is exactly what Java code does later.

### Why it is needed

These five errors recur for an entire career, usually wrapped in a stack trace at a bad
moment. Met on purpose, on self-written data, they become readable in seconds. The skill:
*which table, which rule, which name, which value*.

### The five breaks — all run for real against the seeded tables

Database `zenith`, holding 2 venues, 3 events, 5 tiers. Every insert refused; counts
afterwards still 2, 3, 5.

**Break 1 — duplicate primary key** (re-insert venue id `11111111-...`):

```text
ERROR:  duplicate key value violates unique constraint "venue_pkey"
DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) already exists.
```

**Break 2 — NULL name** (venue with `name = NULL`):

```text
ERROR:  null value in column "name" of relation "venue" violates not-null constraint
DETAIL:  Failing row contains (33333333-..., null, Pune, 800, null, 2026-10-24 13:10:11.346488+00).
```

**Break 3 — negative amount** (tier 'Clearance' at `-5.0000`):

```text
ERROR:  new row for relation "price_tier" violates check constraint "price_tier_amount_non_negative"
DETAIL:  Failing row contains (bbbbbbbb-...0006, aaaaaaaa-...0001, Clearance, -5.0000, 2026-10-24 ...).
```

**Break 4 — event pointing at a missing venue** (`venue_id = '99999999-...'`):

```text
ERROR:  insert or update on table "event" violates foreign key constraint "event_venue_fk"
DETAIL:  Key (venue_id)=(99999999-9999-9999-9999-999999999999) is not present in table "venue".
```

**Break 5 — a second 'General' tier on the same event:**

```text
ERROR:  duplicate key value violates unique constraint "price_tier_name_unique_per_event"
DETAIL:  Key (event_id, name)=(aaaaaaaa-0000-0000-0000-000000000001, General) already exists.
```

**Bonus — zero capacity** ('Tiny Hall', capacity 0): refused via
`violates check constraint "venue_capacity_positive"`.

### Decoding table

| Break | Key phrase | What it tells you |
| --- | --- | --- |
| 1 | `duplicate key ... "venue_pkey"` | A primary key *is* a unique constraint — same family as break 5; the name says which. |
| 2 | `null value in column "name" ... not-null` | Exact column named; `Failing row contains` shows the whole rejected row (note `seat_map_hints` null is fine, and the default had already filled `created_at` before rejection). |
| 3 | `check constraint "price_tier_amount_non_negative"` | The chosen name explains the rule without reading any SQL. |
| 4 | `foreign key constraint "event_venue_fk" ... not present in table "venue"` | The relationship (name) and the missing value. |
| 5 | `Key (event_id, name)=(..., General) already exists` | The composite pair printed as a pair — the rule is per event, not global. |

### Subtask 7 — fishing the name

In break 5, the constraint name is the string between the double quotes after
`unique constraint`: **`price_tier_name_unique_per_event`**. That exact string is what a
Java application later receives inside the exception when two users submit the same tier
name simultaneously — the code checks which constraint name the error carries and
translates this one into an HTTP 409 Conflict. The name chosen in `CREATE TABLE` is the
hinge between the database refusing and the user seeing a polite error.

### Common mistakes

- **Reading only the first line.** `DETAIL:` carries the values — the fastest route to
  *which* row and *why*.
- **Fearing the errors harmed data.** A refused statement is a no-op; the counts prove it.
- **Confusing breaks 1 and 5.** Identical first line — only the *name* distinguishes "same
  id twice" from "same tier name on one event".

### Where it is used

Failed migrations, bug reports, night alerts. The practiced reader extracts table, rule,
name, offending value in one glance.

### Link to the task

Subtask 4's five errors, produced and recorded; subtask 7's name-fishing with the exact
string Java will catch in Phase 5.

### Do it yourself (subtasks 4 and 7)

1. Connect: `pgcli postgresql://postgres:dev@localhost:5432/zenith` (seed must be in:
   counts 2, 3, 5).
2. Run the five breaks one at a time; after each, copy the full `ERROR` + `DETAIL` into
   `NOTES.md`:

   ```sql
   INSERT INTO venue (id, name, city, capacity)
   VALUES ('11111111-1111-1111-1111-111111111111', 'Riverside Arena Copy', 'Hyderabad', 9000);

   INSERT INTO venue (id, name, city, capacity)
   VALUES ('33333333-3333-3333-3333-333333333333', NULL, 'Pune', 800);

   INSERT INTO price_tier (id, event_id, name, amount)
   VALUES ('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000001', 'Clearance', -5.0000);

   INSERT INTO event (id, venue_id, name, starts_at)
   VALUES ('aaaaaaaa-0000-0000-0000-000000000004', '99999999-9999-9999-9999-999999999999', 'Ghost Gig', '2026-12-31 20:00+05:30');

   INSERT INTO price_tier (id, event_id, name, amount)
   VALUES ('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000001', 'General', 1299.0000);
   ```

3. Expected: the five errors above, in order. If an insert *succeeds*, a value no longer
   breaks the rule — remove the accidental row (`DELETE FROM venue WHERE id = '<id>';`) and
   retry as written.
4. Subtask 7: from the fifth error, copy the string between the quotes into `NOTES.md` with
   one sentence on when Java will meet it.
5. Verify: counts still 2, 3, 5.

**Done when:** `NOTES.md` holds five distinct errors, the name
`price_tier_name_unique_per_event`, and the counts are unchanged.

### Summary for revision

- Five families: duplicate pkey, not-null, check, foreign key, duplicate composite unique.
- First line = rule + name; `DETAIL:` = the offending values.
- A refused statement changes nothing.
- Breaks 1 and 5 share a first line; the constraint name is the differentiator.

### Check questions, with answers

**Question 1.** Two errors both begin `duplicate key value violates unique constraint` — one
ends `"price_tier_pkey"` with `Key (id)=(...)`, the other
`"price_tier_name_unique_per_event"` with `Key (event_id, name)=(...)`. A teammate says
"same error, same bug." What are the two different mistakes, and which is a busy
ticket-selling website more likely to produce?

**Answer.** The `_pkey` error means the same `id` was inserted twice — with random uuids
that practically never happens by chance, so it signals a programming bug: a hardcoded or
reused id, or a script executed twice. The composite error is a *business* collision: two
requests (two admins, or one double-submitted form) created a tier named 'General' for the
same event at nearly the same moment. The busy website produces the second one routinely —
which is exactly why Java watches for that constraint name and answers 409 Conflict, while a
`_pkey` violation would instead be investigated as a bug.

**Question 2.** An insert breaks the negative-amount rule and is refused; nothing is stored.
If a row broke two rules at once (duplicate id *and* negative amount), Postgres would report
one error and store nothing. Why is "the whole row is refused, nothing partial is kept" the
only behaviour that makes sense?

**Answer.** Because the entire value of a constraint is the guarantee that *anything inside
the table passed every rule*. A half-kept row — id stored, amount rejected — is not a row,
and a table holding "mostly valid" data forces every reader to re-validate everything,
which is the world constraints exist to abolish. Statements are all-or-nothing: on any
failure the table is exactly as before, so you can fix the statement and simply retry.
Reporting one error just means "first rule that failed"; the retry surfaces the next one, in
an unchanged table.

### Your doubts

None for this lesson.

---

## Lesson 8 — The delete experiments (subtasks 5 and 6)

### Definition

Two experiments on delete behaviour: attempt to delete a real venue that has events and read
the refusal; then build a *throwaway copy* of the venue–event relationship with
`ON DELETE CASCADE`, delete a venue there, watch children vanish, and drop the copy.
Finish by recording a deliberate `ON DELETE` decision per relationship.

### Why it is needed

The three delete behaviours were learned on scratch tables; here the refusal protects *your*
data. And the cascade experiment teaches the safe-experimentation habit: to see something
destructive, build a disposable copy and destroy that.

### How it works

- Experiment 1 is one statement against Riverside Arena (two events point at it). No
  `ON DELETE` was written on `event_venue_fk`, so the default — refuse — fires.
- Experiment 2 uses `INSERT INTO ... SELECT`: instead of typed values, it copies rows from a
  query into another table. Create `venue_copy`/`event_copy` (the copy's FK declared
  `ON DELETE CASCADE`), copy the real rows, delete a venue in the copy, count the damage,
  `DROP` both copies.

### Example — both experiments, run for real

Experiment 1 — the real tables defend themselves:

```text
DELETE FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';
ERROR:  update or delete on table "venue" violates foreign key constraint "event_venue_fk" on table "event"
DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) is still referenced from table "event".

DELETE FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001';
ERROR:  update or delete on table "event" violates foreign key constraint "price_tier_event_fk" on table "price_tier"
DETAIL:  Key (id)=(aaaaaaaa-0000-0000-0000-000000000001) is still referenced from table "price_tier".
```

Experiment 2 — the throwaway cascade:

```sql
CREATE TABLE venue_copy (id uuid PRIMARY KEY, name text NOT NULL);
CREATE TABLE event_copy (
    id       uuid PRIMARY KEY,
    venue_id uuid NOT NULL REFERENCES venue_copy(id) ON DELETE CASCADE,
    name     text NOT NULL
);
INSERT INTO venue_copy SELECT id, name FROM venue;           -- INSERT 0 2
INSERT INTO event_copy SELECT id, venue_id, name FROM event; -- INSERT 0 3

DELETE FROM venue_copy WHERE name = 'Riverside Arena';       -- DELETE 1
SELECT count(*) FROM event_copy;                             -- 1   (was 3)
SELECT name FROM event_copy;                                 -- Jazz at the Note

DROP TABLE event_copy;
DROP TABLE venue_copy;
```

Real counts afterwards: still 2 venues, 3 events, 5 tiers. No trace.

### Walkthrough

1. The venue refusal is the system working: constraint name, referencing table, exact id.
   Refusing is right because those events carry price tiers — and eventually tickets and
   payments; silent co-deletion would destroy business history as a side effect.
2. The event deletion is refused one level down by `price_tier_event_fk` — protection is
   chained; the middle of the hierarchy cannot be hollowed out either.
3. Really deleting Riverside Arena would mean walking the chain bottom-up, each step
   explicit: its events' tiers, then its events, then the venue. (Not done — the data is
   needed for F5.4–F5.6.)
4. In the copy, `DELETE 1` was all Postgres said — yet two of three events died; only the
   other venue's 'Jazz at the Note' survived. That silence is the argument against casual
   CASCADE.
5. Decisions recorded (subtask 6): `event → venue` **refuse** (money and history);
   `price_tier → event` **refuse** (same, one level down); future `seat → seat_map`
   **cascade** (a seat is a meaningless fragment without its map).

### Common mistakes

- **"Fixing" a refusal by adding CASCADE to the real schema.** The refusal was information:
  something depends on this. Making it silent removes the warning, not the dependency.
- **Experimenting on real tables.** One `ALTER` to "just test" and the safety is gone.
  Copies are cheap; seed data is not.
- **Forgetting to drop the copies**, or dropping them in the wrong order. Real output:

  ```text
  DROP TABLE venue_copy;
  ERROR:  cannot drop table venue_copy because other objects depend on it
  DETAIL:  constraint event_copy_venue_id_fkey on table event_copy depends on table venue_copy
  HINT:  Use DROP ... CASCADE to drop the dependent objects too.
  ```

  Children first: `event_copy`, then `venue_copy` — creation order, reversed.

### Where it is used

Copy-then-destroy is how professionals test risky operations: a copied table, a dump, a
staging environment — never the real thing first. The bottom-up deletion chain is what an
"offboard customer" / "purge venue" feature implements as a careful, logged, multi-step
operation.

### Link to the task

Subtask 5: venue delete attempted, refused, reason recorded. Subtask 6: cascade observed on
a throwaway copy, copies dropped, `ON DELETE` decision recorded per relationship. Hands-on
work complete.

### Do it yourself (subtasks 5 and 6)

1. Connect: `pgcli postgresql://postgres:dev@localhost:5432/zenith` (counts must read
   2, 3, 5).
2. Attempt both real deletions; copy each refusal into `NOTES.md`:

   ```sql
   DELETE FROM venue WHERE id = '11111111-1111-1111-1111-111111111111';
   DELETE FROM event WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001';
   ```

   Expected: the two errors above. If the first *succeeds*, your events point elsewhere —
   stop and re-check the seed data.
3. In `NOTES.md`, one sentence: why is refusing right for venues? (Events carry tiers,
   tickets, history — deletion must be a conscious multi-step decision, not a side effect.)
4. Run the throwaway experiment exactly as in the example: two `CREATE TABLE`s, two
   `INSERT ... SELECT`s, the `DELETE`, the two `SELECT`s, then both `DROP TABLE`s
   (`event_copy` first). Expected: `DELETE 1`, count 3 → 1, only 'Jazz at the Note' left.
5. Record in `NOTES.md`: `event → venue` refuse; `price_tier → event` refuse; future
   `seat → seat_map` cascade — one reason each.
6. Verify no trace: `\dt` shows only `venue`, `event`, `price_tier`; counts still 2, 3, 5.

**Done when:** both refusals and the three decisions are in `NOTES.md`, the copies are gone,
and the real counts are unchanged.

### Summary for revision

- Refusal on live data = the schema doing its job; read name, table, id.
- Destructive curiosity goes to a throwaway copy, never the real schema.
- `DELETE 1` after cascade counts only the parent.
- Drop copies children-first; `ON DELETE CASCADE` governs rows, not `DROP TABLE`.

### Check questions, with answers

**Question 1.** `order.customer_id` references `customer(id)` with no `ON DELETE` clause.
A GDPR "delete this customer" request is refused:
`violates foreign key constraint "order_customer_fk" ... still referenced from table
"order"`. A colleague proposes `ALTER`-ing to `ON DELETE CASCADE` so such requests "just
work". What would that do on the next deletion, and what is the safer design?

**Answer.** With CASCADE, the next customer deletion silently deletes every order they ever
placed — financial and audit records — reporting only `DELETE 1`; and it applies to *every*
future deletion, including accidental ones. Safer: keep the FK refusing, and build "delete
customer" as an explicit, logged, multi-step operation — anonymise the personal data (which
usually satisfies GDPR) or archive-then-delete the orders per retention rules, and only then
remove the customer row. The refusal stays as the safety net under the careful path.

**Question 2.** Cleaning up a throwaway experiment (`event_copy.venue_id REFERENCES
venue_copy(id) ON DELETE CASCADE`), you run `DROP TABLE venue_copy;` first — and it fails.
What does Postgres say, why didn't the CASCADE help, and what order works?

**Answer.** Real output:
`ERROR: cannot drop table venue_copy because other objects depend on it` /
`DETAIL: constraint event_copy_venue_id_fkey on table event_copy depends on table
venue_copy` / `HINT: Use DROP ... CASCADE to drop the dependent objects too.`
`ON DELETE CASCADE` governs what happens to child *rows* when a parent *row* is deleted; it
says nothing about dropping whole tables. The foreign key itself is an object that depends
on `venue_copy`, so the table cannot vanish while the constraint exists. Drop children
first: `DROP TABLE event_copy;` then `DROP TABLE venue_copy;` — creation order, reversed.

### Your doubts

None for this lesson.

---

## Task checkpoint — answered

**1. What does a foreign key actually prevent, and what is an orphan row?**

A foreign key (`event.venue_id REFERENCES venue(id)`) makes it impossible for a child row to
point at a parent that does not exist — in both directions: an insert naming a missing
parent is refused (`Key (venue_id)=(...) is not present in table "venue"`), and deleting a
parent that children still point at is refused (`Key (id)=(...) is still referenced from
table "event"`). An **orphan row** is what it prevents: a child whose parent id points at
nothing — an event whose venue is gone. Orphans make joins silently lose rows and turn
"where is this event held?" into an unanswerable question.

**2. What is the difference between `UNIQUE (name)` and `UNIQUE (event_id, name)`, in
ticketing terms?**

`UNIQUE (name)` means one tier called 'General' *across the entire system* — the moment one
event has a 'General' tier, no other event ever can. Wrong rule. `UNIQUE (event_id, name)`
means the *combination* must be unique: each event can have its own 'General' (our seed data
has three), but no event can have two. Our name for it: `price_tier_name_unique_per_event` —
the constraint Java will translate into HTTP 409 when two requests race.

**3. You try to delete a venue that has events and it fails. Why is that the sensible
default?**

Because the events hanging off the venue carry price tiers — and, in the full system,
tickets, payments, and history. A delete that silently took them along would destroy
business records as a side effect of a one-line command, reporting only `DELETE 1`. Refusal
turns venue deletion into what it should be: a conscious, bottom-up, multi-step decision
(tiers, then events, then venue), each step explicit.

**4. Why is a database constraint stronger than the same check written in Java?**

The Java check runs only in that one application, and check-then-insert loses races: two
requests both pass the `if`, both insert, and the duplicate lands. The constraint lives
where every write must pass — so it holds against two app servers racing, against a second
service written next year, against migrations, and against a human fixing data by hand at
2 a.m. The database refuses the second write no matter who sends it; Java's job is merely to
catch that refusal (by constraint name) and answer politely.

---

## Ask the AI — `ON DELETE RESTRICT` vs `CASCADE` vs `SET NULL`, with examples both ways

All three are settings on a foreign key (e.g. `event.venue_id REFERENCES venue(id)`). They
answer one question: **when a parent row is deleted, what happens to the children pointing
at it?**

| Behaviour | On parent delete | What it prints |
| --- | --- | --- |
| `RESTRICT` (default — writing nothing means this) | **Refused** while any child points at the parent | `ERROR: ... still referenced from table "..."` |
| `CASCADE` | Parent dies **and every pointing child dies too**, chained downward | Just `DELETE 1` — children die unannounced |
| `SET NULL` | Parent dies; children **survive with the pointer blanked** (FK column must be nullable) | `DELETE 1`; children live on with a NULL pointer |

All three were run for real in Lessons 4 and 8: the refusal protecting Riverside Arena, the
cascade that turned 3 copied events into 1 with only `DELETE 1` printed, and the SET NULL
that left a surviving event pointing at nothing.

### RESTRICT (refuse)

- **Correct choice: `event → venue`.** Deleting Riverside Arena while Monsoon Rock Night
  exists must fail: events carry price tiers — and in the full system, tickets, payments,
  history. The refusal forces deletion to be a conscious, bottom-up operation (tiers, then
  events, then venue). The friction is the feature.
- **Where it "causes data loss" — honestly: it can't, directly.** RESTRICT never deletes
  anything; it has no destructive mode. Its failure is indirect, through workarounds. Put
  RESTRICT on `seat → seat_map` (where seats are disposable fragments) and every map
  replacement needs a hand-written bulk `DELETE FROM seat WHERE ...` first — and one tired
  evening, a wrong `WHERE` deletes seats belonging to a *different* map. The constraint
  didn't destroy data; the ad-hoc script written to get around a badly placed refusal did.
  Wrongly placed RESTRICT moves danger from the schema into hand-written cleanup scripts.

### CASCADE

- **Correct choice: `seat → seat_map`.** A seat row is a meaningless fragment without its
  map — no money, no history, no independent life. The map dies, its 2,000 seat rows vanish
  with it, and that *is* the correct cleanup; recreating the map recreates the seats.
- **Data loss: `event → venue` with CASCADE.** One `DELETE FROM venue WHERE ...` — a
  mistyped `WHERE` at 3 a.m. — and the venue's whole event catalogue dies silently, chaining
  through price tiers to everything below. Postgres prints `DELETE 1`; the first sign is a
  customer asking where their event went.

### SET NULL

- **Correct choice: an optional relationship, like `event.promoter_id → promoter`.** The
  promoter leaves; their events are real and must survive; "no promoter currently assigned"
  makes business sense. The FK column is deliberately nullable, and blanking it is honest.
- **Data loss: `ticket.event_id` (or `price_tier.event_id`) with SET NULL.** Delete an event
  and every sold ticket survives — with `event_id = NULL`. The rows are physically there,
  but the association is destroyed irrecoverably: payments for admission to nowhere, and no
  query can ever again say which event the money was for. Data loss doesn't require rows to
  disappear; losing the link that gave them meaning is the same disease in quieter clothes.

**Decision rule, compressed:** money or history on the children → refuse. Child is a
meaningless fragment of the parent → cascade. "Pointing at nothing" is a real, designed
business state → set null — and nothing else qualifies.

---

## Task F5.3 — complete

All seven subtasks covered with do-it-yourself steps: tables built with named constraints
(1, 2), seed data in (3), all five violations triggered and recorded (4), venue-delete
refusal understood (5), cascade observed on a throwaway copy and `ON DELETE` decided per
relationship (6), constraint name fished from the duplicate error for Java's future 409 (7).

**Machine end state:** container `pg` running on volume `pgdata`; database `zenith` holds
`venue` (2 rows), `event` (3 rows), `price_tier` (5 rows); no scratch or copy tables. This
data is the input for F5.4 (SELECT/WHERE/ORDER BY), F5.5, and F5.6 (grouping and
aggregates).

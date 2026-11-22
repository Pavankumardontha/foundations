# F5.4 — Reading data: SELECT, WHERE, ORDER BY, and the NULL trap

Notes for Task F5.4. One lesson per section. Each lesson ends with its understanding-check
questions and full answers. Per the chosen structure for this task, concepts come first and
**all seven subtasks are run through together in the final section**, with complete
do-it-yourself steps.

**Prerequisite machine state (from F5.3):** container `pg` running with the `pgdata` volume;
database `zenith` holding `venue` (2 rows: Riverside Arena/Hyderabad, The Blue
Note/Mumbai), `event` (3 rows: Monsoon Rock Night, Classical Evening, Jazz at the Note —
Nov–Dec 2026), `price_tier` (5 rows). The task's literal example values ('Leeds',
'%monkeys%', July) match nothing in this data; the run-through uses real equivalents.

---

## Lesson 1 — `SELECT`: choosing your columns

### Definition

`SELECT` reads rows from a table. The list right after it says *which columns* come back —
that choice is the **projection**. `SELECT *` = every column; `SELECT name, city` = just
those two.

### Why it is needed

Every list endpoint, report, and admin screen is a `SELECT` underneath. Projection is not
cosmetic: a real table may have twenty columns and the screen needs two. Asking for exactly
what you need means less data moved, clearer intent, fewer surprises when columns are added
later.

### How it works

1. `SELECT * FROM venue;` — all columns, all rows (no filter yet).
2. `SELECT name, city FROM venue;` — same rows, two columns, in the order you listed them.
3. Only columns the table *owns* can be projected — asking `event` for `city` fails; city
   lives on `venue`.
4. `SELECT DISTINCT venue_id FROM event;` — `DISTINCT` removes duplicate result rows. Three
   events point at two venues: without `DISTINCT`, three venue_ids (repeats included); with
   it, two.
5. `SELECT v.name FROM venue v;` — `v` is an **alias**, a nickname declared in `FROM`.
   Pointless on one table; essential once two tables share a query and both have a `name`.

### Example — real run against the seeded data

```text
SELECT name, city FROM venue;
      name       |   city
-----------------+-----------
 Riverside Arena | Hyderabad
 The Blue Note   | Mumbai

SELECT city FROM event;
ERROR:  column "city" does not exist
LINE 1: SELECT city FROM event;
               ^

SELECT venue_id FROM event;            SELECT DISTINCT venue_id FROM event;
 11111111-...                           11111111-...
 11111111-...                           22222222-...
 22222222-...                          (2 rows)
(3 rows)
```

### Walkthrough

1. Projection changes *width*, never *which rows*: the two-column query returned the same
   two rows as `SELECT *`.
2. The error is the task's predicted one: `column "city" does not exist`, caret pointing at
   the word. The event *page* on a website shows a city, so the brain files city under
   event — but the *schema* stores it once, on `venue`, because a venue's city never changes
   per event. The error is the database teaching you your own schema.
3. The duplicate venue_ids are honest — two events really point at Riverside Arena.
   `DISTINCT` answers a different question: "which venues host anything?" (2) versus "one
   row per event" (3). Choose by question, not tidiness.

### Common mistakes

- **`SELECT *` in application code.** Fine while exploring; in code it silently changes
  shape when a column is added, and hauls data nobody renders.
- **Guessing column locations from UI pages.** The page is denormalised for the eye; the
  schema is normalised for truth. In doubt: `\d event`.
- **Using `DISTINCT` to hide a surprise.** First ask *why* duplicates exist (here: two
  events, same venue — correct). `DISTINCT` is for genuine repeats your question doesn't
  want.

### Where it is used

Projection discipline becomes Java discipline: repository methods fetching only needed
columns, DTOs that don't haul twenty fields to render two. "Which table owns this column" is
the thinking that later makes joins obvious — city must be *joined in* from `venue`.

### Link to the task

Subtask 1 is this lesson: all columns, named columns, and the deliberate `city`-on-`event`
error. Full commands with expected output in the final run-through section.

### Summary for revision

- `SELECT columns FROM table` — projection picks width, not rows.
- A table can only give what it owns; `column "..." does not exist` means "wrong table" more
  often than "typo".
- `DISTINCT` removes duplicate result rows; aliases (`FROM venue v`) are nicknames for
  tables.

### Check questions, with answers

**Question 1.** Table `venue (id uuid, name text, city text, capacity integer)` with 2 rows.
`SELECT city FROM venue;` works — then `SELECT city FROM event;` fails with
`ERROR: column "city" does not exist`, though every event happens in some city. Why *must*
the second query fail, and what would `\d event` show that eventually leads to a city?

**Answer.** A query can only project columns its `FROM` table owns, and `event` owns none
called `city` — the schema stores city exactly once, on `venue`, because a venue's city does
not vary per event. `\d event` shows: `id`, `venue_id`, `name`, `starts_at`, `created_at` —
and `venue_id` is the road: it points at the venue row, which owns the city. Reaching it in
one query is a *join*, which a later task teaches; the discipline of knowing which table
owns which column starts here.

**Question 2.** `event` has 3 rows, two sharing a `venue_id`. For "venues that host at least
one event", someone runs `SELECT venue_id FROM event;` — three rows, one duplicated. One
teammate says "add `DISTINCT`"; another says "the duplicate is a data bug, delete one of
those events." Who is right, and what question does each query answer?

**Answer.** The first teammate. The duplicate is honest data — Riverside Arena really hosts
two events; deleting one would falsify reality to beautify a result. Without `DISTINCT` the
query answers "for each event, its venue" (3 rows, one per event). With `DISTINCT` it
answers "which venues host at least one event" (2 rows). The question asked is the second
one, so `DISTINCT` is the correct tool here — not as cleanup, but because the question
doesn't want per-event repeats.

### Your doubts

None for this lesson.

---

## Lesson 2 — `WHERE`: keeping only the rows that pass a test

### Definition

`WHERE` filters rows. After it comes a **predicate** — a condition checked against each row
(`city = 'Hyderabad'`, `amount > 2000`). Only rows where it holds come back. Predicates
combine with `AND` (both must hold) and `OR` (either suffices).

### Why it is needed

Every real question is a filter: events in December, tiers under 2000, venues in a city.
And one detail costs money when misunderstood: `AND`/`OR` have a fixed priority, and a
missing pair of brackets changes which rows return — with no error.

### How it works

1. The predicate runs per row; matches are kept.
2. **A filter matching nothing is not an error.** Zero rows is a valid answer.
3. Comparisons: `=`, `<>`, `<`, `>`, `<=`, `>=` — numbers, text, timestamps alike.
4. **`AND` binds tighter than `OR`.** `a OR b AND c` means `a OR (b AND c)` — like `*`
   before `+`. Brackets override.

### Example — real run, including the precedence flip

`price_tier` data: General 999, General 1499, General 1999, Front Table 3499, VIP 4999.

```text
SELECT name, capacity FROM venue WHERE city = 'Hyderabad';
 Riverside Arena | 12000          (1 row)

SELECT name, city FROM venue WHERE city = 'Leeds';
 (0 rows)

-- No brackets:
SELECT name, amount FROM price_tier WHERE name = 'General' OR name = 'VIP' AND amount > 2000;
 General | 1499.0000
 VIP     | 4999.0000
 General |  999.0000
 General | 1999.0000              (4 rows)

-- Brackets around the OR group:
SELECT name, amount FROM price_tier WHERE (name = 'General' OR name = 'VIP') AND amount > 2000;
 VIP  | 4999.0000                 (1 row)
```

### Walkthrough

1. `'Leeds'` returns `(0 rows)` — no error. An empty result and a broken query look
   identical from outside; only you know which question you meant.
2. Same characters, with and without brackets: 4 rows vs 1. Without, `AND` grabs first —
   "any General at all, or a VIP over 2000", so every General passes regardless of price.
   With brackets — "a General or VIP, and over 2000" — only VIP 4999 survives.
3. Neither query is wrong; they ask different questions. The bug is when your head holds one
   and your fingers type the other. Mixing `AND` with `OR`: always bracket.

### Common mistakes

- **Trusting zero rows.** Maybe no data — maybe wrong case, wrong range, precedence slip.
  Re-read the predicate before trusting emptiness.
- **Mixing `AND`/`OR` without brackets.** Legal, silent, frequently not what you meant.
- **Quote confusion.** Text and timestamps need quotes (`'Hyderabad'`, `'2026-12-01'`);
  numbers don't.

### Where it is used

Every `WHERE` for the rest of the course; the same precedence exists in Java (`&&` over
`||`), so the habit pays twice.

### Link to the task

Subtask 1's "filtered by city" (on `venue`, the owner) and subtask 2's bracket-the-OR-group
experiment — the 4-row/1-row flip above. Full run in the final section.

### Summary for revision

- `WHERE` keeps rows whose predicate holds; zero rows is an answer, not an error.
- `AND` before `OR`, always — bracket every mix.
- Same characters, different brackets, different question.

### Check questions, with answers

**Question 1.** `price_tier (name text, amount numeric)`: General 999, General 1499, General
1999, Front Table 3499, VIP 4999. Wanted: "General or Front Table, under 1500". Written:
`WHERE name = 'General' OR name = 'Front Table' AND amount < 1500`. Which rows return, why
is a 1999 row among them, and what is the corrected condition?

**Answer.** `AND` binds tighter, so it reads `name = 'General' OR (name = 'Front Table' AND
amount < 1500)`. Every General passes unconditionally — 999, 1499, **and 1999** — because
the `amount < 1500` test is glued only to Front Table (3499, which fails it). Three rows,
one costing 1999. Corrected: `WHERE (name = 'General' OR name = 'Front Table') AND amount <
1500` → General 999 and General 1499 only.

**Question 2.** `SELECT * FROM venue WHERE city = 'hyderabad';` returns `(0 rows)` though
the stored value is `'Hyderabad'`. The colleague concludes "no Hyderabad venues." What
happened, why no warning, and why should zero rows trigger a re-read of the predicate?

**Answer.** Text `=` is case-sensitive: `'hyderabad' ≠ 'Hyderabad'`, so the database
answered *exactly the question asked* — which rows carry the lowercase value: none. No
warning exists because an empty result is a legitimate answer, indistinguishable from "the
data isn't there". So zero rows means: before concluding about the data, re-read the
predicate for case, spelling, ranges, and bracketing — the query that runs is not always the
question you meant. (Case-insensitive matching has a proper tool, `ILIKE`, taught next.)

### Your doubts

None for this lesson.

---

## Lesson 3 — `IN`, `BETWEEN`, and `LIKE`/`ILIKE`: the everyday operators

### Definition

`IN` — matches any value in a list. `BETWEEN` — within a range, both ends included. `LIKE` —
text pattern matching, `%` meaning "anything, any length"; `ILIKE` the same ignoring letter
case (a Postgres extra).

### Why it is needed

They replace hand-chained `OR`s and `>=`/`<=` pairs with readable intent. Search boxes →
`ILIKE`, date-range pickers → timestamp ranges, multi-select filters → `IN`.

### How it works

1. `IN ('a','b','c')` ≡ `= 'a' OR = 'b' OR = 'c'`. Listing a value absent from the table is
   not an error — it just matches nothing.
2. `BETWEEN x AND y` ≡ `>= x AND <= y`, inclusive both ends. **Timestamp trap:** a bare date
   `'2026-11-28'` becomes *midnight starting that day*, so the range includes the 28th only
   up to 00:00 — an event at 15:30 that day is silently excluded.
3. `LIKE 'Monsoon%'` — starts with; `LIKE '%jazz%'` — contains, case-sensitively;
   `ILIKE '%jazz%'` — contains, any casing.

### Example — real run

Events: Monsoon Rock Night (Nov 20), Jazz at the Note (Nov 28, 15:30 UTC), Classical
Evening (Dec 5).

```text
SELECT name, city FROM venue WHERE city IN ('Hyderabad', 'Leeds');
 Riverside Arena | Hyderabad                    (1 row)

SELECT name, starts_at FROM event WHERE starts_at BETWEEN '2026-11-01' AND '2026-11-30';
 Monsoon Rock Night | 2026-11-20 13:30:00+00
 Jazz at the Note   | 2026-11-28 15:30:00+00    (2 rows)

-- Upper bound moved to the 28th:
SELECT name, starts_at FROM event WHERE starts_at BETWEEN '2026-11-01' AND '2026-11-28';
 Monsoon Rock Night | 2026-11-20 13:30:00+00    (1 row)   ← Jazz, on the 28th, is GONE

SELECT name FROM event WHERE name LIKE '%jazz%';
 (0 rows)

SELECT name FROM event WHERE name ILIKE '%jazz%';
 Jazz at the Note                               (1 row)
```

### Walkthrough

1. `IN` matched what it could; 'Leeds' matched nothing, no complaint.
2. `BETWEEN`'s end `'2026-11-28'` is 00:00:00 on the 28th — Jazz at 15:30 fell after it,
   though a human reading "between the 1st and the 28th" means to include it. Professional
   habit for timestamps: **half-open range** — `starts_at >= '2026-11-01' AND starts_at <
   '2026-11-29'` — exclusive upper bound at the *next* day's midnight. Nothing falls in the
   crack.
3. `LIKE '%jazz%'` found nothing (stored name says 'Jazz'); `ILIKE` is the tool when the
   search box was typed in lowercase. Zero rows, no warning — the empty-result pattern
   again.

### Common mistakes

- **`BETWEEN` on timestamps with a bare end date** — loses the whole last day except its
  first microsecond. Use `>= start AND < day_after_end`.
- **`LIKE` when you meant `ILIKE`** — zero rows, "we have no jazz events."
- **Leading wildcards (`'%jazz%'`) everywhere** — fine on 3 rows; can't use an ordinary
  index on 3 million (Phase 3 topic; file it away).

### Where it is used

Search endpoints: multi-select city → `IN`; date picker → half-open timestamp range; search
box → `ILIKE '%term%'`. Later phases bind API query parameters into exactly these.

### Link to the task

Subtask 2's operator tour, on the owning tables: `IN` on `venue.city`, `BETWEEN` on
`event.starts_at`, `ILIKE` on `event.name`. The task's literal values ('Leeds','Manchester',
July, '%monkeys%') match nothing in our data; the run-through shows both the literal
versions (instructively empty) and the real-data ones.

### Summary for revision

- `IN` = any of the list; absent values match nothing, no error.
- `BETWEEN` is inclusive — but a bare date is *midnight*, so timestamps want half-open
  ranges: `>= start AND < next-day`.
- `LIKE` is case-sensitive; `ILIKE` isn't; `%` = anything.

### Check questions, with answers

**Question 1.** `event (name text, starts_at timestamptz)` holds `('New Year Gala',
'2026-12-31 22:00+00')`. The December report runs `WHERE starts_at BETWEEN '2026-12-01' AND
'2026-12-31'` and the gala is missing. Why, precisely — and what condition can never miss a
December event?

**Answer.** `'2026-12-31'` as a timestamp is `2026-12-31 00:00:00` — the first instant of
the day, not the whole day. `BETWEEN` includes that instant and nothing after it; the gala
at 22:00 falls outside. The report loses every event on the last day except one starting at
exactly midnight. Fix with a half-open range:
`WHERE starts_at >= '2026-12-01' AND starts_at < '2027-01-01'` — inclusive start, exclusive
upper bound at the next period's first instant. Every moment of December satisfies it,
including 23:59:59.999 on the 31st.

**Question 2.** Search term "note"; table holds 'Jazz at the Note'. The developer writes
`WHERE name LIKE '%note%'`, gets zero rows, files a bug against the database. What is wrong,
what one-character change fixes it, and why zero rows instead of an error?

**Answer.** `LIKE` is case-sensitive: the stored text has 'Note' with a capital N, and
`'%note%'` demands a lowercase n. The one-character fix: `LIKE` → `ILIKE` (adding the `I`),
which matches ignoring case. No error is raised because a pattern that matches nothing is a
perfectly valid outcome — the database cannot know the pattern "obviously should" match; it
answers the question exactly as asked. The bug is in the query, not the database.

### Your doubts

None for this lesson.

---

## Lesson 4 — `ORDER BY` and `LIMIT`/`OFFSET`: sorting and paging

### Definition

`ORDER BY` sorts the result by one or more columns, `ASC` (default) or `DESC`. `LIMIT n`
returns at most *n* rows; `OFFSET m` skips the first *m*. Together: paging — page 2 of a
10-per-page list is `LIMIT 10 OFFSET 10`.

### Why it is needed

Every list screen is sorted and paged. And the subtle half: **without `ORDER BY` the
database promises no order at all.** Insert order on a tiny table is luck, not contract.
Paging without `ORDER BY` can show one row on two pages and skip another, no error.

### How it works

1. `ORDER BY starts_at` — soonest first; `ORDER BY amount DESC` — priciest first.
2. Multiple keys break ties: `ORDER BY name, amount` — by name, then amount within equal
   names.
3. `LIMIT 2 OFFSET 0` → sorted rows 1–2; `OFFSET 2` → 3–4; `OFFSET 4` → row 5. Running out
   of data gives a short (or empty) page, not an error.
4. Written order is fixed: `SELECT ... FROM ... WHERE ... ORDER BY ... LIMIT ... OFFSET ...`.

### Example — real run: paging all five tiers, priciest first

```text
ORDER BY amount DESC LIMIT 2 OFFSET 0;   → VIP 4999, Front Table 3499   (page 1)
ORDER BY amount DESC LIMIT 2 OFFSET 2;   → General 1999, General 1499   (page 2)
ORDER BY amount DESC LIMIT 2 OFFSET 4;   → General 999                  (page 3 — short, no error)

SELECT name, amount FROM price_tier ORDER BY name, amount;
 Front Table | 3499.0000
 General     |  999.0000   ┐
 General     | 1499.0000   ├ equal names → amount decides inside the group
 General     | 1999.0000   ┘
 VIP         | 4999.0000
```

### Walkthrough

1. The pages tile perfectly — each row exactly once — *because* the `ORDER BY` is identical
   on every page and all amounts differ. The sort is the contract making `OFFSET` arithmetic
   meaningful.
2. With tied values the database may legally order the tie either way per query — page
   boundaries can then duplicate or swallow a row. Cure: end the sort with a unique
   tie-breaker — `ORDER BY amount DESC, id`.
3. This is F4.5 meeting its implementation: the `limit`/`offset` query parameters map
   one-to-one onto these clauses.
4. For Phase 3: `OFFSET 10000` walks and discards ten thousand sorted rows first. Harmless
   here, real cost at scale — keyset paging ("rows after this id") replaces it later.

### Common mistakes

- **Paging without `ORDER BY`** — works in demos, lies in production.
- **Paging on a non-unique sort** — ties make page boundaries unstable.
- **Expecting an error past the end** — `OFFSET 100` on 5 rows returns `(0 rows)` calmly;
  clients must treat a short/empty page as "the end".

### Where it is used

Every list endpoint: newest events, cheapest tiers, page 3 of search, infinite scroll, CSV
export.

### Link to the task

Subtask 1's "ordered by `starts_at`, first ten" and subtask 3 (`LIMIT 10 OFFSET 0/10/20`,
tied to F4.5, deep-offset cost noted). Our 3 events mean `LIMIT 10` returns all and later
offsets return empty pages — shown as-is in the run-through, because a short page *is* the
lesson.

### Summary for revision

- No `ORDER BY` → no promised order; paging needs a sort ending in a unique column.
- `LIMIT`/`OFFSET` = the API's `limit`/`offset`; short or empty page = the end.
- Deep `OFFSET` walks skipped rows — Phase 3 replaces it with keyset paging.

### Check questions, with answers

**Question 1.** `price_tier (id uuid unique, name text, amount numeric)`, five rows, two
sharing `amount = 1499`. Paging uses `ORDER BY amount DESC LIMIT 2` with `OFFSET 0/2/4`. A
user saw one tier on both page 1 and page 2, and another tier never appeared. No code
changed. How is that possible without any error, and what is the one-change fix?

**Answer.** `ORDER BY amount DESC` does not specify the relative order of the two 1499
rows — the database may emit the tie either way, and may do so *differently on different
executions* of the same query. If the page boundary falls between the tied rows, one
execution puts row A at position 2 (end of page 1) and the next execution puts row B there
instead — so A repeats on page 2 while B is never seen. Every individual query honoured its
(under-specified) contract, hence no error. Fix: make the sort deterministic with a unique
tie-breaker: `ORDER BY amount DESC, id`.

**Question 2.** The F4.5 endpoint `GET /events?limit=10&offset=20` — what SQL shape runs
(soonest first), what returns on a 3-row table, and what must the JSON response signal?

**Answer.** `SELECT id, name, starts_at FROM event ORDER BY starts_at LIMIT 10 OFFSET 20;`
On 3 rows, `OFFSET 20` skips past everything: `(0 rows)` — calmly, no error. The API
response must make "you've reached the end" explicit to the client: an empty `items` array,
plus whatever the F4.5 design chose — a `total` count or a `hasMore: false` flag — so the
client stops requesting further pages instead of looping on empty results.

### Your doubts

None for this lesson.

---

## Lesson 5 — Three-valued logic: why `= NULL` silently matches nothing

### Definition

Java conditions are true or false. SQL adds a third outcome: **unknown**. Any comparison
involving `NULL` — even `NULL = NULL` — is unknown, and `WHERE` keeps only rows that are
**definitely true**. The correct tests for absence: `IS NULL` / `IS NOT NULL`.

### Why it is needed

`NULL` means "no value here", and SQL takes it seriously: whether two unknowns are equal
cannot be known — unknown encodes exactly that. Consequence: a `= NULL` query returns zero
rows, raises nothing, and looks like working code. It costs real money quietly.

### Setup (the task's schema change — done on the real table)

```sql
ALTER TABLE event ADD COLUMN cancelled_at timestamptz;  -- new nullable column, NULL everywhere
UPDATE event SET cancelled_at = now() WHERE name = 'Classical Evening';  -- cancel one event
```

`ALTER TABLE ... ADD COLUMN` adds a column to an existing table (existing rows get NULL).
`UPDATE ... SET ... WHERE` changes existing rows where the filter matches.

### How it works

1. For a row whose `cancelled_at` is NULL, the test `cancelled_at = NULL` asks "is unknown
   equal to unknown?" → **unknown**.
2. `WHERE` keeps rows only when the condition is *true*. Unknown is not true → dropped.
3. `IS NULL` is not a comparison but an inspection — "is this cell empty?" — with a definite
   true/false answer.

### Example — real run

```text
SELECT name, cancelled_at FROM event;
 Monsoon Rock Night |                                  ← NULL
 Jazz at the Note   |                                  ← NULL
 Classical Evening  | 2026-10-24 18:05:00.690845+00

SELECT * FROM event WHERE cancelled_at = NULL;
 (0 rows)                                 ← the trap: not even the two NULL rows

SELECT name FROM event WHERE cancelled_at IS NULL;
 Monsoon Rock Night
 Jazz at the Note                         ← the un-cancelled events, correctly

SELECT name, cancelled_at FROM event WHERE cancelled_at IS NOT NULL;
 Classical Evening | 2026-10-24 ...       ← just the cancelled one

SELECT NULL = NULL AS eq, NULL IS NULL AS is_null;
 eq | is_null
----+---------
    | t                                   ← eq is blank: the comparison itself is unknown
```

### Walkthrough

1. The trap in one line: a reasonable person reads `cancelled_at = NULL` as "rows where it's
   empty" — and two rows *are* empty — yet zero return. The comparison was unknown for every
   row, `WHERE` kept only true, nothing survived. No error, no warning. On a live-events
   page that reads as "everything is cancelled"; on a cancelled-report as "nothing is" —
   production-grade wrong answers in working clothes.
2. `NULL = NULL` displays blank — that blank *is* unknown. `NULL IS NULL` prints `t`.
3. The sentence to own: *a comparison with NULL is not false, it is unknown; and WHERE only
   keeps rows that are definitely true.*

### Common mistakes

- **`= NULL` instead of `IS NULL`.** Zero rows, no error.
- **`<> NULL` for "has a value".** Equally broken — unknown again. `IS NOT NULL`.
- **Java intuition.** In Java `null == null` is true; in SQL it is unknown. Inside the
  database, SQL wins.

### Where it is used

Soft-delete and status columns: `cancelled_at IS NULL` = active, `deleted_at IS NULL` =
live, `paid_at IS NOT NULL` = settled. NULL-means-not-yet is everywhere, so `IS NULL`
filters are daily bread — each one once someone's `= NULL` bug.

### Link to the task

Subtasks 4 and 5: `cancelled_at` added to the real `event` table, Classical Evening
cancelled, trap and fix reproduced, the "why" sentence written. Checkpoint questions 1
and 2.

### Summary for revision

- Three outcomes: true, false, unknown. `WHERE` returns only true.
- Any `=`/`<>` against NULL is unknown — including `NULL = NULL`.
- `IS NULL` / `IS NOT NULL` inspect; they do not compare.

### Check questions, with answers

**Question 1.** `invoice (id uuid, paid_at timestamptz)`, `paid_at` NULL until paid. Finance
runs `SELECT count(*) FROM invoice WHERE paid_at = NULL;`, gets 0, celebrates. What did the
condition evaluate to for an unpaid row, why was it dropped, and what is the correct query?

**Answer.** For an unpaid row, `paid_at` is NULL, so `paid_at = NULL` is a comparison with
NULL → **unknown** (not false, and crucially not true). `WHERE` keeps definitely-true rows
only, so every unpaid row was dropped and the count was honestly 0 — of rows matching an
unanswerable condition. No error at any point. Correct:
`SELECT count(*) FROM invoice WHERE paid_at IS NULL;` — inspection, not comparison; the
unpaid invoices return.

**Question 2.** Name the three outcomes of a SQL condition and which `WHERE` returns. Then:
`SELECT * FROM event WHERE cancelled_at <> '2026-01-01';` — does a row with NULL
`cancelled_at` come back?

**Answer.** True, false, unknown; `WHERE` returns only true. The NULL row does **not** come
back: `NULL <> '2026-01-01'` is a comparison involving NULL, hence unknown — even though in
everyday terms an empty value "isn't" that date. Both `=` and `<>` go unknown against NULL;
the row can only be included explicitly:
`WHERE cancelled_at <> '2026-01-01' OR cancelled_at IS NULL`.

### Your doubts

None for this lesson.

---

## Lesson 6 — The `NOT IN` trap: one `NULL` poisons the whole list

### Definition

`NOT IN (list)` means "not equal to *any* value in the list". If the list contains a `NULL`,
the condition can never be true for any row — the query returns **no rows**, silently.
Reliable alternatives: `NOT EXISTS`, or excluding `NULL`s from the list.

### Why it is needed

The list usually comes from a **subquery** — a `SELECT` nested in brackets feeding the outer
query — and whether it contains a NULL depends on *data*, not code. Works in every test;
one nullable row lands in production and the report silently goes empty.

### How it works

1. `id NOT IN (x, y, NULL)` unpacks to `id <> x AND id <> y AND id <> NULL`.
2. `id <> NULL` → unknown, for every row (Lesson 5's rule).
3. `true AND true AND unknown` = unknown — one unknown drags the whole `AND`-chain down;
   `WHERE` keeps only true. Every row dropped.
4. Asymmetry: `IN` unpacks to `OR`s, and `true OR unknown` is still true — plain `IN`
   survives a NULL. Only the `NOT` direction dies.

### Example — real run (the task's scratch setup)

```text
CREATE TABLE scratch_ref (ref_id uuid);
INSERT INTO scratch_ref VALUES ((SELECT id FROM event LIMIT 1)), (NULL);

SELECT name FROM event WHERE id IN (SELECT ref_id FROM scratch_ref);
 Monsoon Rock Night                      (1 row — IN works despite the NULL)

SELECT name FROM event WHERE id NOT IN (SELECT ref_id FROM scratch_ref);
 (0 rows)                                ← expected Jazz and Classical. Nothing. No error.

-- Fix 1: strip NULLs from the list
SELECT name FROM event WHERE id NOT IN (SELECT ref_id FROM scratch_ref WHERE ref_id IS NOT NULL);
 Jazz at the Note
 Classical Evening                       (2 rows)

-- Fix 2: NOT EXISTS
SELECT name FROM event e
WHERE NOT EXISTS (SELECT 1 FROM scratch_ref s WHERE s.ref_id = e.id);
 Jazz at the Note
 Classical Evening                       (2 rows)

DROP TABLE scratch_ref;
```

### Walkthrough

1. Two events are *visibly* not in the list, yet `NOT IN` returned nothing: each row's chain
   held one `id <> NULL`, and an `AND` containing an unknown can never be fully true. The
   database refuses to claim "this id differs from a value nobody knows".
2. Fix 1 removes the poison at the source.
3. Fix 2 asks a different-shaped question: for each event `e`, "does any `scratch_ref` row
   exist with `ref_id = e.id`?" — existence, not value comparison; a NULL never equals
   anything, so it cannot poison. `SELECT 1` = "only existence matters". The professional
   default for "not in that set".

### Common mistakes

- **`NOT IN (subquery)` over a nullable column.** Works until the data betrays it.
- **Debugging the outer query.** The poison is in the *list* — check the subquery for NULLs
  first when `NOT IN` returns a suspicious zero.
- **Banning `NOT IN` entirely.** Fine with literal NULL-free lists
  (`city NOT IN ('Leeds','Manchester')`); dangerous with lists you don't control.

### Where it is used

Any "A not referenced by B": events with no tickets, customers with no orders, venues never
used. The subquery column is often a nullable foreign key — exactly where production NULLs
appear. `NOT EXISTS` is also what query planners optimise best (later phases).

### Link to the task

Subtask 6 verbatim, plus checkpoint question 3.

### Summary for revision

- `NOT IN` + NULL in list = zero rows, silently. `IN` is immune (OR vs AND).
- Fixes: `WHERE ... IS NOT NULL` inside the subquery, or `NOT EXISTS`.
- Suspicious zero from `NOT IN` → inspect the subquery for NULLs.

### Check questions, with answers

**Question 1.** `customer (id uuid, name text)`; `order_tbl (customer_id uuid)` where
`customer_id` is nullable (guest checkouts store NULL).
`SELECT name FROM customer WHERE id NOT IN (SELECT customer_id FROM order_tbl);` worked for
months, then one guest checkout emptied the list. Explain the mechanics and write the
`NOT EXISTS` version.

**Answer.** The subquery now yields `(..., NULL)`. For every customer the condition unpacks
to `id <> c1 AND id <> c2 AND ... AND id <> NULL`; the last term is unknown, and `AND` with
an unknown can never reach true — so `WHERE` drops every customer, including those who
plainly never ordered. No error, because each comparison behaved exactly per three-valued
logic. Fix:

```sql
SELECT name FROM customer c
WHERE NOT EXISTS (SELECT 1 FROM order_tbl o WHERE o.customer_id = c.id);
```

A NULL `customer_id` never equals `c.id`, so guest checkouts simply never match — they stop
poisoning anything.

**Question 2.** Same NULL-bearing list: `id IN (x, NULL)` still finds rows equal to x, while
`id NOT IN (x, NULL)` finds nothing. Why?

**Answer.** `IN` unpacks to `id = x OR id = NULL` — for a row equal to x that is
`true OR unknown`, and OR needs only one true: **true**, row returned. `NOT IN` unpacks to
`id <> x AND id <> NULL` — even when `id <> x` is true, the chain is `true AND unknown`,
and AND needs *everything* true: **unknown**, row dropped. The same poison is harmless in an
OR-chain and fatal in an AND-chain.

### Your doubts

> "I did not understand NOT EXISTS at all. Can you please explain it properly?"

In plain words, step by step:

**1. A subquery is a small query inside brackets.**
`(SELECT 1 FROM scratch_ref s WHERE s.ref_id = e.id)` is a little search: "look in
`scratch_ref` for rows where `ref_id` equals some id." Nothing more.

**2. EXISTS asks one yes/no question.**
`EXISTS (little search)` asks: "did the search find at least one row — yes or no?" It never
looks at *what* was found; one row or a hundred, the answer is just "yes". `NOT EXISTS`
flips it: "did the search find **nothing**?"

**3. The database checks each event one at a time.**
In `SELECT name FROM event e WHERE NOT EXISTS (...)`, the `e` is a nickname for "the event
row being looked at right now". The database works like a loop: take event 1, run the little
search *using event 1's id* — found something? skip the event; found nothing? keep it. Then
event 2, then event 3. In Java shape:

```java
for (Event e : events) {                              // outer query
    boolean found = anyRowMatches(scratchRef, e.id);  // the little search
    if (!found) keep(e);                              // NOT EXISTS
}
```

**4. Watched on real data** (`scratch_ref` = Monsoon's id + one NULL):

```text
SELECT name, EXISTS (SELECT 1 FROM scratch_ref s WHERE s.ref_id = event.id) AS found_in_list
FROM event;
        name        | found_in_list
--------------------+---------------
 Monsoon Rock Night | t      ← search for Monsoon's id: first row matches → yes
 Jazz at the Note   | f      ← Monsoon's id? no. NULL? never matches. nothing found → no
 Classical Evening  | f      ← same → no
```

`NOT EXISTS` keeps the "no" rows: Jazz and Classical. Exactly the result.

**5. Why the NULL does no harm here.**
In `NOT IN`, the NULL sits **inside each row's test**: "your id must differ from Monsoon's
id AND differ from NULL" — and "is your id different from an unknown thing?" cannot be
answered, so the whole test fails for every row. In `NOT EXISTS`, the NULL is just **one
more row the search looks at and walks past** — it never matches, the search moves on, and
"did I find anything?" still has a clean answer: no. `NOT IN` compares against every value
in a list (one unknowable value breaks all comparisons); `NOT EXISTS` only counts search
hits (a NULL simply never becomes a hit).

**6. Small leftovers.** `SELECT 1` is a placeholder meaning "contents don't matter — only
whether a row exists"; `SELECT *` would behave identically. `s` is just a nickname for
`scratch_ref`.

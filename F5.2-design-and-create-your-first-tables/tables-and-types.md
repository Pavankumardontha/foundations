# F5.2 — Design and create your first tables

Notes for Task F5.2. One lesson per section. Each lesson ends with its understanding-check
questions and full answers.

**Prerequisite machine state (from F5.1):** container `pg` running with the `pgdata` volume,
database `zenith` created. The F5.1 README has the from-scratch steps.

---

## Lesson 1 — DDL vs DML: the two families of SQL

### Definition

Every SQL statement belongs to one of two families. **DDL** (Data Definition Language)
changes the *shape* of the database: which tables exist, which columns they have. **DML**
(Data Manipulation Language) changes or reads the *contents*: the rows inside the tables.

### Why it is needed

The families carry different risk. Contents change constantly — inserts and reads are
routine and cheap. Shape changes are rare and expensive: once a table holds a million real
rows, changing its shape means touching every row and coordinating with running code,
usually without downtime. Knowing the family tells you how much care a statement deserves.
`DELETE` a wrong row: bad, fixable. `DROP` a wrong table: shape and all rows gone. This is
why the task insists on paper before typing — paper is cheap; reshaping a live table is not.

**Analogy.** A printed paper form. DDL designs the form: which boxes exist, what each is
called, what kind of answer goes in each. DML fills forms in, reads them, corrects them,
throws individual forms away. Redesigning the form after ten thousand are filled in is the
expensive thing.

### How it works, step by step

- **DDL — shape:** `CREATE TABLE` (new table), `ALTER TABLE` (change columns),
  `DROP TABLE` (remove table and everything in it).
- **DML — contents:** `INSERT` (add rows), `SELECT` (read rows), `UPDATE` (change rows),
  `DELETE` (remove rows).

Quick test for any statement: after it runs, did the set of tables or columns change, or
only the rows? Columns/tables → DDL. Rows → DML.

### Example — real session in `zenith` (scratch table, dropped at the end)

```text
postgres@localhost:zenith> CREATE TABLE demo (id integer, note text);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO demo (id, note) VALUES (1, 'hello');
INSERT 0 1

postgres@localhost:zenith> SELECT * FROM demo;
+----+-------+
| id | note  |
|----+-------|
| 1  | hello |
+----+-------+

postgres@localhost:zenith> DROP TABLE demo;
DROP TABLE
```

Walkthrough:

1. `CREATE TABLE` — DDL. A new shape exists: table `demo`, two typed columns, no rows.
2. `INSERT` — DML. One row in; the reply `INSERT 0 1` ends with the row count.
3. `SELECT * FROM demo;` — DML. `*` = all columns. Reading changes nothing but is still
   about contents.
4. `DROP TABLE` — DDL. Shape removed; the row inside went with it. Nothing asked "are you
   sure".

### Important terms

| Term | Meaning |
| --- | --- |
| **DDL** | Statements that change shape: `CREATE TABLE`, `ALTER TABLE`, `DROP TABLE`. |
| **DML** | Statements that change or read contents: `INSERT`, `SELECT`, `UPDATE`, `DELETE`. |
| **`DROP TABLE`** | Removes the table's shape *and* all its rows. |
| **`DELETE FROM`** | Removes rows; the table stays. |

### Common mistakes

- **Thinking `DROP TABLE` just empties the table.** It removes shape and contents. To keep
  the table and remove rows: `DELETE FROM demo;`.
- **Treating `SELECT` as a third thing.** It operates on contents, so it is DML. The family
  is about what is touched, not whether it writes.
- **Equal caution for both families.** Re-running `SELECT` is free; `DROP`/`ALTER` on the
  wrong database is the classic career story. Glance at the prompt before DDL.

### Where it is used

Real projects split the families mechanically: DDL lives in version-controlled migration
files (run in order by Flyway later in this module); application code does DML all day and
is typically forbidden from DDL entirely.

### Link to the task

Subtask 3 is pure DDL. The break-it subtasks mix one DDL statement (scratch table) with DML
(`INSERT`, `SELECT`). The paper-first philosophy exists because DDL on live data is the
expensive family.

### Summary for revision

- DDL = shape (`CREATE`, `ALTER`, `DROP`). DML = contents (`INSERT`, `SELECT`, `UPDATE`,
  `DELETE`).
- Shape changes are rare and expensive; content changes are constant and cheap.
- `DROP` kills shape and rows; `DELETE FROM` kills rows only.
- Paper design exists because DDL is the hard-to-undo family.

### Check questions, with answers

**Question 1.** Sort into DDL and DML, one phrase each:
`ALTER TABLE event ADD COLUMN doors_open_at timestamptz;` —
`UPDATE event SET name = 'Jazz Night' WHERE id = 7;` —
`DELETE FROM event WHERE id = 7;` — `DROP TABLE event;`

**Answer.** `ALTER TABLE ... ADD COLUMN` — DDL: adds a new column to the table's shape.
`UPDATE ... SET` — DML: changes the contents of matching rows. `DELETE FROM ... WHERE` —
DML: removes matching rows, table stays. `DROP TABLE` — DDL: removes the table's shape and
every row in it.

**Question 2.** A teammate wants to wipe all test rows out of `booking` but keep the table.
They run `DROP TABLE booking;`. What went wrong, which family is the mistake in, and what
should they have run?

**Answer.** `DROP TABLE` removed the table itself — shape and contents — so nothing can be
refilled; the table must be recreated. The mistake was reaching for DDL when the job was
about contents. They wanted DML: `DELETE FROM booking;`, which empties the table and keeps
the shape.

### Your doubts

None for this lesson.

---

## Lesson 2 — NULL and NOT NULL, and the three-state boolean trap

### Definition

**NULL** means "no value here". Not zero, not an empty string, not false — the absence of any
value. **NOT NULL** is a column rule: every row must have a value here; the database refuses
rows without one.

### Why it is needed

Real data has genuinely missing values (a capacity still under negotiation, an unannounced
doors-open time), and NULL is the honest way to store that. But in Postgres every column
allows NULL unless you say otherwise — and every nullable column taxes all future code with
"but what if it's NULL?". So each column needs a decision: can this honestly be missing?
Mostly the answer is no → `NOT NULL`, enforced at the gate.

### How it works, step by step

1. `nickname text` — nullable by default; empty slots hold NULL.
2. `name text NOT NULL` — inserting or updating a NULL there is rejected; the bad row never
   enters.
3. NULL is contagious in logic: not equal to anything, not even another NULL. (Full
   consequences in Task F5.4.)
4. The boolean trap: `is_verified boolean` without NOT NULL has three states — true, false,
   NULL. Nobody knows what NULL means there. Booleans should be `NOT NULL` with a default,
   or they are not really booleans.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE person (name text NOT NULL, nickname text, is_verified boolean);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO person (name) VALUES ('Asha');
INSERT 0 1

postgres@localhost:zenith> INSERT INTO person (name, nickname) VALUES ('Vik', '');
INSERT 0 1

postgres@localhost:zenith> SELECT * FROM person;
+------+----------+-------------+
| name | nickname | is_verified |
|------+----------+-------------|
| Asha | <null>   | <null>      |
| Vik  |          | <null>      |
+------+----------+-------------+

postgres@localhost:zenith> INSERT INTO person (name, nickname) VALUES (NULL, 'Rook');
null value in column "name" of relation "person" violates not-null constraint
DETAIL:  Failing row contains (null, Rook, null).
```

Walkthrough:

1. Asha's row omitted `nickname` and `is_verified` → both NULL: missing, honestly marked.
2. Vik's `nickname` is the empty string `''`. In the output: Asha shows `<null>` (no value),
   Vik shows a blank (a value of zero characters). **Different things** — "we have no
   nickname" vs "the nickname is empty text".
3. `is_verified` NULL in both rows: the three-state boolean in the wild.
4. The NULL `name` was rejected; nothing was inserted.

Decoding the error:

| Error text | Plain English |
| --- | --- |
| `null value in column "name"` | The row had no value for `name`. |
| `of relation "person"` | "Relation" is Postgres's formal word for table. |
| `violates not-null constraint` | The NOT NULL rule rejected the whole row. |
| `DETAIL: Failing row contains (...)` | The exact row that was refused. |

### Important terms

| Term | Meaning |
| --- | --- |
| **NULL** | Absence of a value. Distinct from `0`, `''`, and `false`. |
| **NOT NULL** | Column rule: a value is required in every row. |
| **Nullable** | A column without NOT NULL; Postgres's silent default. |
| **Constraint** | A rule the database enforces by rejecting bad rows. |

### Common mistakes

- **Using `''` or `0` to mean "missing".** Then missing and genuinely-empty are
  indistinguishable. NULL exists to keep them apart.
- **Leaving columns nullable by accident.** Silence = nullable. Say `NOT NULL` almost
  everywhere; treat each nullable column as a documented exception.
- **Nullable booleans.** Three states wearing a two-state name. Use
  `boolean NOT NULL DEFAULT false`, or admit it is a status with more values.

### Where it is used

Every table. Ticketing: `venue.name text NOT NULL` (a nameless venue is nonsense),
`capacity NOT NULL` (overbooking checks need it), but `event.doors_open_at` may be genuinely
unannounced — nullable, deliberately. You should be able to defend every column's
nullability out loud.

### Link to the task

Subtask 2's paper design requires "which columns may be empty" for every column. Answer:
NOT NULL by default; nullable only with a reason you can say out loud.

### Summary for revision

- NULL = no value. Not 0, not '', not false.
- Postgres default is nullable; `NOT NULL` must be said explicitly.
- `<null>` and blank look similar in output but mean different things.
- Nullable boolean = three states = design smell.

### Check questions, with answers

**Question 1.** Given:

```sql
CREATE TABLE attendee (full_name text NOT NULL, phone text);
INSERT INTO attendee (full_name)        VALUES ('Mira');       -- row 1
INSERT INTO attendee (full_name, phone) VALUES ('Dev', '');    -- row 2
INSERT INTO attendee (full_name, phone) VALUES (NULL, '999');  -- row 3
```

Does each insert succeed? For the successes, what is in `phone`, and are the two values the
same thing?

**Answer.** Row 1 succeeds; `phone` is NULL (no value — the column was never given one).
Row 2 succeeds; `phone` is `''`, the empty string — a real value of zero characters. They are
not the same: NULL means "missing", `''` means "present and empty". Row 3 fails:
`full_name` is NOT NULL and the row offers NULL, so the insert is rejected
(`violates not-null constraint`) and nothing is stored.

**Question 2.** A teammate designs
`CREATE TABLE ticket_scan (ticket_id uuid NOT NULL, was_accepted boolean);`. Months later,
thousands of rows have `was_accepted` neither true nor false and nobody knows what they
meant. What did the declaration allow, and what is a better one-line declaration?

**Answer.** `was_accepted boolean` without NOT NULL allowed a third state, NULL, and code
that never set the value created thousands of "unknown" scans. Better:
`was_accepted boolean NOT NULL` (with `DEFAULT false` if an explicit starting state is
wanted) — or, if "unknown" is a real business state, an honest
`result text NOT NULL CHECK (result IN ('accepted','rejected','unknown'))`.

### Your doubts

None for this lesson.

---

## Lesson 3 — Text columns: `text` vs `varchar(n)`, and CHECK for real limits

### Definition

`text` holds text of any length. `varchar(n)` is text with a maximum of `n` characters bolted
on. A **CHECK constraint** is a rule attached to a column — any condition — that the database
enforces by rejecting rows that fail it.

### Why it is needed

`varchar(255)` everywhere is folklore copied from other databases. In Postgres, `text` and
`varchar(n)` are stored identically and run at identical speed — `varchar(n)` is literally
`text` plus a length check. The habit buys nothing and plants time bombs: one day a real
value exceeds the arbitrary guess and an insert fails for no business reason. Real limits do
exist (currency code = 3 letters), and for those `CHECK` is the better tool: it can say
"exactly 3", "at most 5", "never empty" — and it documents a decision, not a habit.

### How it works, step by step

1. `name text` — any length. The default choice.
2. `code varchar(5)` — at most 5 characters; the type itself rejects longer values.
3. `label text CHECK (length(label) <= 5)` — plain text with an explicit rule;
   `length(...)` counts characters; failing rows are rejected.
4. Prefer CHECK because it is more expressive: `CHECK (length(code) = 3)` (exactly),
   `CHECK (name <> '')` (no empty string — which NOT NULL alone does not stop).

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_text (code varchar(5), label text CHECK (length(label) <= 5));
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_text (code) VALUES ('ABCDEF');
value too long for type character varying(5)

postgres@localhost:zenith> INSERT INTO scratch_text (label) VALUES ('ABCDEF');
new row for relation "scratch_text" violates check constraint "scratch_text_label_check"
DETAIL:  Failing row contains (null, ABCDEF).

postgres@localhost:zenith> INSERT INTO scratch_text (code, label) VALUES ('AB', 'CDE');
INSERT 0 1
```

Walkthrough:

1. Six characters into `varchar(5)`: the **type** refused. `character varying` is the formal
   name of `varchar`.
2. Six characters into the CHECK'd column: the **rule** refused. Postgres auto-named it
   `scratch_text_label_check` (table, column, kind) and names it in the error so you know
   which rule fired.
3. Values within limits: `INSERT 0 1`. Both mechanisms are silent when rules pass.
4. Same outcome either way — bad row never entered. The difference is expressiveness and
   visible intent.

Decoding the errors:

| Error text | Plain English |
| --- | --- |
| `value too long for type character varying(5)` | The varchar(5) type itself rejected a longer value. |
| `violates check constraint "scratch_text_label_check"` | The named CHECK rule rejected the row. |
| `DETAIL: Failing row contains (...)` | The exact row that was refused. |

### Important terms

| Term | Meaning |
| --- | --- |
| **`text`** | Text of any length. Same speed as varchar in Postgres. Default choice. |
| **`varchar(n)`** | `text` plus a maximum-length check. Formal name: `character varying`. |
| **CHECK constraint** | Any condition on a column, enforced by rejecting failing rows. |
| **`length(x)`** | Character count of text `x`. |

### Common mistakes

- **`varchar(255)` on everything.** No speed gain in Postgres; arbitrary future failures.
- **Length limits as UI validation.** Constraints should express data truth ("currency = 3
  letters"), not screen layout. UIs change; schemas should not break with them.
- **Thinking NOT NULL stops `''`.** The empty string is a value. A genuinely required name
  wants both: `name text NOT NULL CHECK (name <> '')`.

### Where it is used

Ticketing: `venue.name`, `venue.city`, `event.name`, `price_tier.name` — all `text`; no real
rule caps their length. A `currency` column: `text CHECK (length(currency) = 3)`.

### Link to the task

Checkpoint question 1 verbatim: why `text` rather than `varchar(50)`, and how to enforce a
needed limit → same storage and speed, so `text` by default; a real limit becomes an explicit
`CHECK (length(...) <= n)`.

### Summary for revision

- In Postgres, `varchar(n)` = `text` + length check. Same speed. Prefer `text`.
- Real limits → CHECK, which can also say "exactly n" and "not empty".
- CHECK violations name the failed rule; type violations name the type.
- Constraints express data truth, not UI rules.

### Check questions, with answers

**Question 1.** A teammate proposes `city varchar(50)`; you propose `city text`. They ask:
"won't `text` be slower, and what if someone inserts a 10,000-character city?" Answer both
halves as in a design review.

**Answer.** Speed: in Postgres the two are stored identically — `varchar(n)` is just `text`
plus a length check — so `text` cannot be slower. Garbage input: a 10,000-character string is
wrong *as a city*, not wrong *at 51 characters*; if we genuinely want a cap, we state an
honest one explicitly (`CHECK (length(city) <= 200)`), where the number is a decision we can
defend, not folklore. Arbitrary 50 will one day reject a real place name.

**Question 2.** Given:

```sql
CREATE TABLE coupon (
    code text NOT NULL CHECK (length(code) = 8)
);
INSERT INTO coupon (code) VALUES ('SAVE20');
```

Does the insert succeed? What error, and why would `varchar(8)` have let it through?

**Answer.** It fails: `'SAVE20'` is 6 characters and the rule demands exactly 8 — expect
`new row for relation "coupon" violates check constraint "coupon_code_check"`. `varchar(8)`
means *at most* 8, so a 6-character value passes the type check; `varchar` cannot express
"exactly n" — another reason CHECK is the sharper tool.

### Your doubts

None for this lesson.

---

## Lesson 4 — Whole numbers: `integer` vs `bigint`

### Definition

`integer` and `bigint` hold whole numbers. `integer` reaches about 2.1 billion in either
direction; `bigint` about 9.2 quintillion. Both are exact — no rounding, ever.

### Why it is needed

The choice is purely about growth, and choosing wrong is asymmetric. Too big costs 4 extra
bytes per row. Too small costs a famous outage: a counter hits 2,147,483,647, every next
insert fails, and the fix is reshaping a live table under traffic ("integer exhaustion").
Rule of thumb: `integer` for quantities the real world bounds (venue capacity); `bigint` for
anything that grows forever (lifetime ticket counters).

### How it works, step by step

1. `capacity integer` — whole numbers from −2,147,483,648 to 2,147,483,647.
2. `tickets_sold_total bigint` — roughly ±9.2 quintillion.
3. A value outside the range is rejected with an error — no silent wraparound, no
   truncation. The type is a gate, like NOT NULL and CHECK.
4. Arithmetic is exact at any size within range. (Contrast with floating-point in the money
   lesson, where `0.1 + 0.2` is not exactly `0.3`.)

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_num (small_one integer, big_one bigint);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_num (small_one) VALUES (2147483647);
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_num (small_one) VALUES (2147483648);
integer out of range

postgres@localhost:zenith> INSERT INTO scratch_num (big_one) VALUES (2147483648);
INSERT 0 1
```

Walkthrough:

1. `2147483647` into `integer`: accepted — the exact ceiling.
2. One more: rejected, `integer out of range`. No wraparound, no truncation; the gate held
   loudly.
3. The same number into `bigint`: accepted without comment — its ceiling is over four
   billion times higher.
4. Picture that error on a sold-tickets counter during an on-sale rush: that is the outage.
   One word in `CREATE TABLE`, written today, prevents it.

| Error text | Plain English |
| --- | --- |
| `integer out of range` | The value does not fit in `integer`'s ±2.1 billion range. |

### Important terms

| Term | Meaning |
| --- | --- |
| **`integer`** | Exact whole number, ±2.1 billion. 4 bytes. |
| **`bigint`** | Exact whole number, ±9.2 quintillion. 8 bytes. |
| **Integer exhaustion** | A counter reaching integer's ceiling in production; every insert fails. |

### Common mistakes

- **Numbers as `text`.** `'10'` sorts before `'9'`, arithmetic needs conversions, `'abc'`
  sneaks in. Numbers get number types.
- **`integer` on ever-growing counters.** Ask: does reality bound this? Capacity: yes.
  "Total scans ever": no → `bigint`.
- **`numeric` where a whole number fits.** `numeric` is for exact decimals; it is slower and
  bulkier. Counts have no decimal places.

### Where it is used

`venue.capacity integer` — the largest stadium seats ~150,000. Lifetime counters and busy
auto-numbering IDs: `bigint` (many teams default IDs to `bigint` as policy; this project uses
`uuid` for IDs instead).

### Link to the task

`venue.capacity` in the subtask 2 paper design: `integer`, defensible out loud. Feeds the
subtask 1 one-liners for both types.

### Summary for revision

- Both exact; the choice is range: integer ±2.1 billion, bigint ±9.2 quintillion.
- Out-of-range = loud error, never silent wraparound.
- Reality-bounded → integer. Grows-forever → bigint.
- Numbers never go in text columns.

### Check questions, with answers

**Question 1.** Pick `integer` or `bigint`, one-phrase reason each: (a) `venue.capacity`;
(b) `scan_counter` — +1 for every ticket scan ever, company-wide, forever; (c)
`event.min_age`; (d) `payment_attempts_total` — lifetime counter on a busy platform.

**Answer.** (a) `integer` — physically bounded far below 2 billion. (b) `bigint` — unbounded
lifetime counter. (c) `integer` — a tiny bounded number. (d) `bigint` — unbounded and busy;
exhaustion is a real risk.

**Question 2.** Given:

```sql
CREATE TABLE gate_stats (scans_today integer);
INSERT INTO gate_stats (scans_today) VALUES (3000000000);
```

The insert fails. Expected error, why, and the two one-line fixes depending on what
`scans_today` really counts?

**Answer.** Error: `integer out of range` — three billion exceeds integer's ceiling of
2,147,483,647. Fixes: if the column can genuinely grow that large, change the type —
`scans_today bigint`. If one day's scans cannot really reach three billion, the type is
right and the *value* is a bug — keep `integer` and fix the code producing it (optionally add
an honest `CHECK (scans_today >= 0)` style bound).

### Your doubts

None for this lesson.

---

## Lesson 5 — Money: `numeric(19,4)`, and why floats are forbidden

### Definition

`numeric(19,4)` holds exact decimal numbers: 19 digits total, 4 after the decimal point. It
is the money type. `double precision` (a float) is a fast *approximate* type — fine for
physics, forbidden for money.

### Why it is needed

Floats are stored in binary, and most decimal fractions — including `0.1` — cannot be written
exactly in binary, like `1/3` cannot be written exactly in decimal. A float stores the
nearest representable value, and each arithmetic step can add error. In money this means
**sums stop matching**: your ledger says `10000.2999...`, the payment provider says
`10000.30`, and a human reconciles pennies at midnight. The same problem exists in Java with
`double` (Task F4.6); database and Java must both use exact decimals or values corrupt in
transit. `numeric` stores decimal digits as decimal digits: `0.1` is exactly `0.1`.

### How it works, step by step

1. `numeric(19,4)`: 19 = **precision**, total digits allowed; 4 = **scale**, digits after the
   decimal point. So up to 15 digits before the point, exactly 4 kept after.
2. `numeric` arithmetic runs in decimal — no binary conversion error, somewhat slower. For
   money the trade is not a debate.
3. `double precision` runs in binary hardware — fast, rounded to nearest binary value at
   every step.
4. The failure is **silent**: no error, no warning, just slightly wrong values discovered
   much later. That is why the rule is absolute.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_money (bad double precision, good numeric(19,4));
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_money (bad, good) VALUES (0.1, 0.1);
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_money (bad, good) VALUES (0.2, 0.2);
INSERT 0 1

postgres@localhost:zenith> SELECT SUM(bad) AS bad_sum, SUM(good) AS good_sum FROM scratch_money;
+---------------------+----------+
| bad_sum             | good_sum |
|---------------------+----------|
| 0.30000000000000004 | 0.3000   |
+---------------------+----------+
```

Walkthrough:

1. Same two values into both columns; all inserts succeed silently — the float column
   happily took values it cannot represent.
2. `SUM(bad)` = `0.30000000000000004`: the accumulated binary error, exposed.
3. `SUM(good)` = `0.3000`: exactly three tenths, shown with the scale's four places.
4. The only difference was the type in `CREATE TABLE`. One line decides whether every future
   sum reconciles.

### Important terms

| Term | Meaning |
| --- | --- |
| **`numeric(p,s)`** | Exact decimal: `p` total digits, `s` after the point. Money type. |
| **Precision / scale** | Total digit budget / digits after the decimal point. |
| **`double precision`** | Approximate binary float. Never for money. |
| **Silent failure** | No error raised; the stored value is just slightly wrong. |

### Common mistakes

- **"I'll round at display time."** Rounding hides the error; stored sums still disagree.
- **Minor-unit integers without a convention.** Storing paise in `integer` works only as an
  explicit team-wide rule. This project's rule: `numeric(19,4)`, full stop.
- **Thinking approximate = random.** Float error is deterministic and stable — tests pass,
  reconciliation fails. That makes it worse.

### Where it is used

Every money column everywhere: prices, fees, refunds, balances, tax. Here: `price_tier`'s
price. Java side: `BigDecimal`. Both layers exact, so values survive the round trip.

### Link to the task

Subtask 6 (break it — money as float) and checkpoint question 3. Decides the price column
type in the subtask 2 paper design.

### Do it yourself (subtask 6)

With the `pg` container running and `zenith` existing:

1. `pgcli postgresql://postgres:dev@localhost:5432/zenith`
2. `CREATE TABLE scratch_money (bad double precision, good numeric(19,4));` → `CREATE TABLE`
3. `INSERT INTO scratch_money (bad, good) VALUES (0.1, 0.1);` → `INSERT 0 1`
4. `INSERT INTO scratch_money (bad, good) VALUES (0.2, 0.2);` → `INSERT 0 1`
5. `SELECT SUM(bad) AS bad_sum, SUM(good) AS good_sum FROM scratch_money;`
   → `bad_sum = 0.30000000000000004`, `good_sum = 0.3000`
6. Say out loud: the float sum is wrong with no error anywhere; the numeric sum is exact.
7. `DROP TABLE scratch_money;` → `DROP TABLE`, then `\q`.
8. In `NOTES.md`: *float sum of 0.1 + 0.2 gave 0.30000000000000004; numeric(19,4) gave
   0.3000 — money is always numeric.*

**Done when:** you saw `0.30000000000000004` on your own machine and the note is written.

### Summary for revision

- Money = `numeric(19,4)`. Never `float`/`double precision`.
- Floats store nearest binary value; `0.1 + 0.2 ≠ 0.3`; failure is silent and stable.
- precision = total digits; scale = digits after the point.
- Java counterpart: `BigDecimal`. Both layers must agree.

### Check questions, with answers

**Question 1.** A teammate: "I used `double precision` for prices — it's faster, and errors
are only in the sixteenth decimal place; nobody sells tickets with sixteen decimals." What is
the flaw?

**Answer.** Single values look fine; *aggregates* are the problem. A day's revenue is a
`SUM` over thousands of prices, and binary error accumulates and surfaces exactly there —
the ledger stops matching the payment provider's total, comparisons like
`SUM(price) = expected` silently return false, and reconciliation becomes manual work. Money
correctness is defined by sums matching to the last paisa, so the speed trade buys nothing
you are allowed to spend.

**Question 2.** Decode `numeric(19,4)`: what are 19 and 4, and how many digits fit before the
point? A junior proposes `numeric(5,4)` for prices "to save space" — largest storable price,
and why is that a problem for a ₹500 ticket?

**Answer.** 19 = precision, the total digit budget; 4 = scale, digits after the decimal
point; so 19 − 4 = 15 digits may sit before the point. `numeric(5,4)` leaves 5 − 4 = 1 digit
before the point: the largest value is `9.9999`. A ₹500 ticket cannot be stored at all —
the insert fails with a numeric-overflow error. Space saving on money types is a false
economy.

### Your doubts

None for this lesson.

---

## Lesson 6 — Time: `timestamptz` vs `timestamp`

### Definition

`timestamptz` stores a **moment in time** — one exact instant, kept in UTC, shown to each
reader converted to their time zone. `timestamp` stores **date-and-time digits** with no zone
— and silently throws away any offset you give it. Project rule: always `timestamptz`.

### Why it is needed

"2026-07-14 19:30" is not a moment; it is 19:30 *somewhere*. Digits without a zone are
ambiguous, and ambiguous start times mean customers at closed doors. `timestamptz` stores the
instant (database twin of Java's `Instant`). `timestamp`'s danger is that it never errors:
given `19:30:00+05:30` it keeps `19:30:00` and drops the `+05:30` without a word — silently
wrong for anyone assuming UTC, like the float money error. Hence the absolute rule.

### How it works, step by step

1. **Insert:** `timestamptz` converts the offset-bearing value to UTC and stores the instant.
   `timestamp` strips the offset, stores raw digits.
2. **Read:** `timestamptz` converts the instant to the session's zone (changeable with
   `SET time zone '...'`). `timestamp` prints its frozen digits regardless of reader.
3. Same stored `timestamptz` → `19:30:00+05:30` in a Mumbai session, `14:00:00+00` in a UTC
   session. Different clock faces, same instant. Stored value never changes.
4. `pgcli` sets the session zone to Asia/Kolkata automatically (it says so at connect).

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_time (naive timestamp, aware timestamptz);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_time (naive, aware) VALUES ('2026-07-14 19:30:00+05:30', '2026-07-14 19:30:00+05:30');
INSERT 0 1

postgres@localhost:zenith> SELECT naive, aware FROM scratch_time;
+---------------------+---------------------------+
| naive               | aware                     |
|---------------------+---------------------------|
| 2026-07-14 19:30:00 | 2026-07-14 19:30:00+05:30 |
+---------------------+---------------------------+

postgres@localhost:zenith> SET time zone 'UTC';
SET

postgres@localhost:zenith> SELECT naive, aware FROM scratch_time;
+---------------------+------------------------+
| naive               | aware                  |
|---------------------+------------------------|
| 2026-07-14 19:30:00 | 2026-07-14 14:00:00+00 |
+---------------------+------------------------+
```

Walkthrough:

1. Identical value into both columns: 7:30 pm Indian time, offset included.
2. First SELECT (session in Asia/Kolkata): `naive` shows `19:30:00` — **the `+05:30` is
   gone**. `aware` shows the full value. The damage hides while your session's zone matches
   the discarded offset.
3. `SET time zone 'UTC';` changes only this session's display of `timestamptz`.
4. Second SELECT: `aware` = `14:00:00+00` — same instant, correct conversion (7:30 pm IST
   *is* 2 pm UTC). `naive` still `19:30:00` — frozen digits. A UTC server reading them is
   wrong by 5½ hours, silently.
5. `timestamptz` carried the moment; `timestamp` carried digits and lost the meaning.

### Important terms

| Term | Meaning |
| --- | --- |
| **`timestamptz`** | A stored instant (UTC inside), converted per reader. Always use this. |
| **`timestamp`** | Zone-less digits; silently discards offsets. Avoid. |
| **Session time zone** | Per-connection display setting; `SET time zone 'UTC';`. |
| **Instant** | One exact point on the world's timeline. Java's `Instant`. |

### Common mistakes

- **Times as `text`.** No comparing, sorting, or converting. Time values get time types.
- **"All our users are in India."** Servers run UTC, cloud logs are UTC, rules change,
  businesses expand. `timestamptz` costs nothing; being wrong costs a support queue.
- **Thinking display change = value change.** `19:30:00+05:30` and `14:00:00+00` are the same
  stored instant presented differently.

### Where it is used

Every "when" column: `event.starts_at`, every `created_at`, payment and scan times.
Production servers run UTC while users live in zones — the conversion runs on every read, and
application code never does zone arithmetic by hand.

### Link to the task

Subtask 5 (break it — wrong timestamp type) and checkpoint question 2: what goes wrong is a
silent discard of the offset, leaving plausible digits that mean nothing. Design: `starts_at
timestamptz NOT NULL`, `created_at timestamptz NOT NULL`.

### Do it yourself (subtask 5)

With the `pg` container running and `zenith` existing:

1. `pgcli postgresql://postgres:dev@localhost:5432/zenith`
2. `CREATE TABLE scratch_time (naive timestamp, aware timestamptz);` → `CREATE TABLE`
3. `INSERT INTO scratch_time (naive, aware) VALUES ('2026-07-14 19:30:00+05:30', '2026-07-14 19:30:00+05:30');` → `INSERT 0 1`
4. `SELECT naive, aware FROM scratch_time;` → `naive` lacks the `+05:30`; `aware` has it.
5. `SET time zone 'UTC';` then repeat the SELECT → `aware` becomes `14:00:00+00`; `naive`
   stays `19:30:00`.
6. Say out loud: timestamp kept digits, threw the offset away; timestamptz kept the moment.
7. `DROP TABLE scratch_time;` then `\q`.
8. In `NOTES.md`: *timestamp silently dropped `+05:30`; timestamptz converted 19:30 IST to
   14:00 UTC — always timestamptz.*

**Done when:** you saw frozen `19:30:00` beside converted `14:00:00+00` on your machine.

### Summary for revision

- `timestamptz` = instant, UTC inside, converted per session. `timestamp` = zone-less digits,
  discards offsets silently.
- The failure never errors — it is silent, like float money.
- `SET time zone` changes display, not storage.
- Every "when" column: `timestamptz NOT NULL`.

### Check questions, with answers

**Question 1.** `event.starts_at` is plain `timestamp`; the server runs UTC. A Mumbai
organiser submits `'2026-11-20 20:00:00+05:30'`. What digits are stored, what does the UTC
server believe, how wrong is it, and why did no error surface?

**Answer.** Stored: `2026-11-20 20:00:00` — offset stripped. The UTC server reads those
digits as 20:00 *UTC*, but the real start is 14:30 UTC, so it is wrong by 5 hours 30 minutes
(it believes the show starts later than it does). No error surfaced because `timestamp`
accepts offset-bearing input and discards the offset by design — nothing in the pipeline
considers that invalid.

**Question 2.** Two sessions read the same `timestamptz` column. Session A
(`SET time zone 'Asia/Kolkata'`) sees `2026-11-20 20:00:00+05:30`; session B
(`SET time zone 'UTC'`) sees `2026-11-20 14:30:00+00`. A junior files a bug: "the database
shows different times to different users." Is anything wrong?

**Answer.** Nothing is wrong — that is the feature. One instant is stored (in UTC). Each
session converts it for display according to its own time zone setting, and
`20:00:00+05:30` and `14:30:00+00` are the same moment written two ways. The stored data is
identical for both readers; only the presentation differs.

### Your doubts

None for this lesson.

---

## Lesson 7 — `uuid`: identifiers that need no central counter

### Definition

A **uuid** (universally unique identifier) is a 128-bit value written as 36 hex characters
with dashes, like `ba3475d6-8f23-49c8-99dd-e761aaa08158`. Anyone can generate one, anywhere,
and it will be unique — without asking any central authority. Every identifier column in this
project is a `uuid`.

### Why it is needed

The obvious alternative is a counter (1, 2, 3, ...). It works until it hurts:

- **Only the database can hand out numbers** — code cannot know an ID before inserting.
- **Counters collide when systems merge** — both sides have an order 1017.
- **Counters leak business volume and invite probing** — ticket 1018 this week, 1540 next
  week tells a competitor your sales; `/orders/1017`, `/orders/1018` invites enumeration.

uuids dissolve all three: minted freely anywhere, no collisions on merge, nothing guessable.

### How it works, step by step

1. 128 bits, mostly random; generated by Postgres's built-in `gen_random_uuid()`.
2. No collisions without coordination because the space is 2¹²² — a billion uuids per second
   for a century still leaves duplicate odds negligible. Uniqueness by overwhelming
   probability, treated as certainty.
3. The `uuid` type stores 16 compact bytes (not 36 text characters), compares fast, and
   rejects malformed values — a gate, like every type.
4. The dashes are human formatting: 8-4-4-4-12 hex groups.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> SELECT gen_random_uuid();
| ba3475d6-8f23-49c8-99dd-e761aaa08158 |

postgres@localhost:zenith> SELECT gen_random_uuid();
| 00bbbef9-e316-4d59-8f4a-442c935c1548 |

postgres@localhost:zenith> CREATE TABLE scratch_uuid (id uuid);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_uuid VALUES (gen_random_uuid());
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_uuid VALUES ('not-a-uuid');
invalid input syntax for type uuid: "not-a-uuid"
LINE 1: INSERT INTO scratch_uuid VALUES ('not-a-uuid')
                                         ^
```

Walkthrough:

1. Two calls, two completely different values — no counter ticked; randomness minted both.
2. The `gen_random_uuid()` insert succeeded.
3. `'not-a-uuid'` was rejected: *invalid input syntax for type uuid* — the text could not be
   read as the hex identifier format, so the type refused. The `^` arrow points at the
   offending value.
4. A `text` column would have accepted the garbage happily; the type is protecting you.

| Error text | Plain English |
| --- | --- |
| `invalid input syntax for type uuid: "not-a-uuid"` | The value is not formatted like a uuid; the type rejected the row. |

### Important terms

| Term | Meaning |
| --- | --- |
| **uuid** | 128-bit identifier, unique without central coordination. |
| **`gen_random_uuid()`** | Postgres function that mints a random uuid. |
| **Enumeration** | Guessing sequential IDs to probe other people's records. |

### Common mistakes

- **uuids in `text` columns.** 2–3× storage, slower compares, zero validation. Use the type.
- **Expecting creation-time order.** Random uuids have no order; "newest first" is what
  `created_at timestamptz` is for.
- **Fearing collisions.** A `gen_random_uuid()` duplicate is less likely than hardware
  failure. Design for real risks.

### Where it is used

Public API identifiers, payment references, tracing IDs, any system where multiple services
create records independently. Here: `venue.id`, `event.id`, `price_tier.id`.

### Link to the task

The identifier column of every table in the subtask 2 design: `id uuid PRIMARY KEY` — this
lesson is the `uuid` half; `PRIMARY KEY` is the next concept. Feeds the subtask 1 one-liner.

### Summary for revision

- uuid = 128 random bits; unique by overwhelming probability; minted anywhere.
- Counters: central, collide on merge, leak volume, invite probing.
- Use the `uuid` type, never text: compact, fast, validated.
- No time order in random uuids — that is `created_at`'s job.

### Check questions, with answers

**Question 1.** A teammate: "Let's use a counting number for `event.id` — simpler, and uuids
can collide anyway since they're random." Reply in two parts: the truth about collision risk,
and two concrete problems the counter brings that uuid avoids.

**Answer.** Collision truth: the space is 2¹²²; even a billion uuids per second for a century
leaves the odds of one duplicate negligible — in practice impossible, and certainly less
likely than any other failure in the system. Counter problems: (1) IDs exist only after the
database assigns them, so code cannot construct linked records before inserting, and merging
datasets from two environments collides; (2) sequential IDs leak sales volume and invite
enumeration of other people's records.

**Question 2.** Given:

```sql
CREATE TABLE api_key (id uuid, label text);
INSERT INTO api_key (id, label) VALUES (gen_random_uuid(), 'staging');
INSERT INTO api_key (id, label) VALUES ('hello-world', 'production');
```

What happens to each, the exact error for the failure, and why would `id text` have been
worse even though both inserts would succeed?

**Answer.** The first insert succeeds. The second fails:
`invalid input syntax for type uuid: "hello-world"` — the value is not uuid-formatted, so the
type rejects the row. With `id text`, both inserts succeed — which is worse: the garbage
`'hello-world'` sits in the identifier column with no validation, wasting space, comparing
slowly, and waiting to break whatever later assumes identifiers are real uuids.

### Your doubts

None for this lesson.

---

## Lesson 8 — `jsonb`: variable-shape data, and where it is allowed

### Definition

`jsonb` holds an entire JSON document — objects, arrays, nested values — parsed and stored in
a binary form (the `b`) that Postgres can search and index. It is for data whose shape
genuinely varies from row to row.

### Why it is needed

Columns assume every row has the same fields. Some data refuses: seat-map rendering hints —
one venue needs `{"zoom": 2, "palette": "dark"}`, another `{"rotation": 90}`, a third
nothing. Modelling that as columns means a mostly-NULL column per hint and a schema change
per new hint. `jsonb` holds whatever document the row needs. The danger is the same feature
upside down: "we'll put it in JSON for now" is how schemas rot — nothing validates it,
nothing constrains it.

### How it works, step by step

1. On insert, the text is parsed as JSON; malformed JSON is rejected (type gate).
2. The gate checks **syntax only, not meaning**: `{"zoom": "banana"}` is valid JSON and goes
   in. No per-key types, no per-key NOT NULL, no REFERENCES inside documents.
3. Reach inside with operators: `hints->>'palette'` extracts a key's value as text; a missing
   key gives NULL, not an error.
4. Binary storage makes `jsonb` indexable — unlike JSON stuffed into a `text` column.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_json (hints jsonb);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_json VALUES ('{"zoom": 2, "palette": "dark"}');
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_json VALUES ('{not valid json');
invalid input syntax for type json
DETAIL:  Token "not" is invalid.

postgres@localhost:zenith> INSERT INTO scratch_json VALUES ('{"zoom": "banana"}');
INSERT 0 1

postgres@localhost:zenith> SELECT hints->>'palette' AS palette, hints->>'zoom' AS zoom FROM scratch_json;
+---------+--------+
| palette | zoom   |
|---------+--------|
| dark    | 2      |
| <null>  | banana |
+---------+--------+
```

Walkthrough:

1. The sensible document went in.
2. The malformed text was rejected: the parser hit `{not` and gave up. Syntax gate works.
3. The trap: `{"zoom": "banana"}` was **accepted** — valid JSON, meaningless content. Inside
   a document you are back in the world before types.
4. The SELECT reaches inside: row one has `palette` → `dark`; row two → `<null>`. And
   `banana` sits where a number belongs — only your application will choke on it, later.

| Error text | Plain English |
| --- | --- |
| `invalid input syntax for type json` + `Token "not" is invalid` | The text is not well-formed JSON; the parser stopped at the shown token. |

### Important terms

| Term | Meaning |
| --- | --- |
| **`jsonb`** | Parsed, indexable JSON document in a column. Syntax validated; meaning not. |
| **`->>`** | Operator extracting a key's value as text; missing key → NULL. |
| **Variable-shape data** | Data whose keys genuinely differ per row — jsonb's only good use. |

### Common mistakes

- **Using `jsonb` to avoid designing.** Known keys on every row = columns. Columns get types,
  NOT NULL, CHECK, REFERENCES; jsonb keys get nothing.
- **Routinely filtering/joining/summing on a jsonb key.** Then it was never a hint — promote
  it to a real column.
- **Money or timestamps inside jsonb.** JSON has no decimal or timestamp types; the
  `numeric(19,4)` and `timestamptz` guarantees evaporate inside a document.

### Where it is used

Per-row configuration with varying keys (seat-map hints), raw third-party payloads stored
verbatim, experimental attributes before they stabilise into columns. In this project's
schema: at most one optional `jsonb` on `venue` for seat-map hints.

### Link to the task

Completes the type list. Unlocks subtask 7 (write the jsonb rule) and subtask 1 (one line per
type) — both below.

### Do it yourself (subtasks 1 and 7 — paper work, no database)

1. **The jsonb rule (subtask 7), reference version to compare with yours:**
   *`jsonb` is allowed only when the keys genuinely vary per row (seat-map rendering hints,
   third-party payloads). Anything the application filters on, joins on, sums, or constrains
   gets a real typed column. "We'll put it in JSON for now" is not a design — if we know the
   keys, they are columns.*
2. **Type one-liners (subtask 1), reference list:**
   - `uuid` — every `id` column: unique without a counter, safe to mint anywhere, not guessable.
   - `text` — names and cities (`venue.name`): no real length rule; same speed as varchar in Postgres.
   - `integer` — `venue.capacity`: bounded by physical reality, exact.
   - `bigint` — lifetime counters (total scans ever): grows forever, overflow-safe.
   - `numeric(19,4)` — ticket price: exact decimal money; sums reconcile to the paisa.
   - `boolean` — `is_published NOT NULL DEFAULT false`: two honest states, never three.
   - `timestamptz` — `starts_at`, `created_at`: one instant, UTC inside, zone-safe.
   - `timestamp` — never: silently discards offsets.
   - `jsonb` — seat-map rendering hints on `venue`: keys genuinely vary per row.

**Done when:** both entries are in `NOTES.md` in your own words and nothing important
disagrees with the references.

### Summary for revision

- jsonb = whole JSON document, parsed and indexable. Syntax checked; meaning never.
- Good: keys vary per row. Bad: avoiding design.
- Filter/join/sum on a key → it must be a real column.
- `->>` extracts as text; missing key → NULL.

### Check questions, with answers

**Question 1.** A teammate proposes `CREATE TABLE ticket_order (id uuid, data jsonb);` where
`data` holds `{"buyer": "...", "price": 549.50, "paid_at": "..."}` — same keys every row.
Two strongest objections?

**Answer.** First: the keys are fixed and known, so this is exactly the case for real columns
— inside jsonb, `price` loses `numeric(19,4)` exactness (JSON numbers carry float semantics
in most consumers), `paid_at` is just a string with no `timestamptz` instant handling, and no
key can be NOT NULL, so a missing buyer is silently fine. Second: every query that filters,
joins, or sums must reach inside the document — slower, unindexed by default, and
unconstrainable. The design throws away every guarantee the earlier lessons built, for
nothing.

**Question 2.** Given:

```sql
CREATE TABLE venue_hints (venue_name text, hints jsonb);
INSERT INTO venue_hints VALUES ('Arena A', '{"zoom": 3}');
INSERT INTO venue_hints VALUES ('Arena B', '{zoom: 3}');
INSERT INTO venue_hints VALUES ('Arena C', '{"zoom": "three"}');
```

Accepted or rejected, each, and why? What does jsonb validate?

**Answer.** Arena A: accepted — well-formed JSON. Arena B: rejected — JSON requires quoted
keys, so `{zoom: 3}` is malformed (`invalid input syntax for type json`). Arena C: accepted —
well-formed JSON even though "three" is nonsense as a zoom. jsonb validates **syntax only**;
meaning is entirely your application's problem.

### Your doubts

None for this lesson.

---

## Lesson 9 — Primary key: the column that identifies a row

### Definition

A **primary key** is the column whose value identifies exactly one row, forever. Declaring
`PRIMARY KEY` enforces two things at once: no two rows share the value (unique), and no row
lacks it (not null).

### Why it is needed

Everything done to a specific row needs a way to say *which* row — updates, deletes, URLs
(`/events/{id}`), and other tables pointing here. Keeping ids unique "by discipline" fails at
2 a.m. under a retry bug. PRIMARY KEY turns the intention into enforcement, like NOT NULL and
CHECK did.

### How it works, step by step

1. `id uuid PRIMARY KEY` — one per table.
2. Postgres silently marks the column NOT NULL, adds a uniqueness rule, and builds an
   **index** (a fast lookup structure, like a book's index) named `tablename_pkey`.
3. Duplicate or NULL ids are rejected with errors.
4. Paired with `uuid`: the application mints ids (`gen_random_uuid()`), the database
   guarantees no reuse.

### Example — real session in `zenith` (scratch table, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_pk (id uuid PRIMARY KEY, name text NOT NULL);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_pk VALUES ('11111111-1111-1111-1111-111111111111', 'first');
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_pk VALUES ('11111111-1111-1111-1111-111111111111', 'second');
duplicate key value violates unique constraint "scratch_pk_pkey"
DETAIL:  Key (id)=(11111111-1111-1111-1111-111111111111) already exists.

postgres@localhost:zenith> INSERT INTO scratch_pk VALUES (NULL, 'third');
null value in column "id" of relation "scratch_pk" violates not-null constraint
DETAIL:  Failing row contains (null, third).

postgres@localhost:zenith> \d scratch_pk
| id     | uuid |  not null |
| name   | text |  not null |
Indexes:
    "scratch_pk_pkey" PRIMARY KEY, btree (id)
```

Walkthrough:

1. First insert with a hand-written uuid: succeeds.
2. Same uuid again: rejected by the auto-named rule `scratch_pk_pkey`; `DETAIL` shows the
   colliding value. The `'second'` row never entered.
3. NULL id: rejected with the familiar not-null error — PRIMARY KEY included NOT NULL even
   though we never typed it.
4. `\d` shows `id` as `not null` plus the `pkey` index (`btree` is the index structure) —
   enforcement machinery Postgres added unasked. Relevant to subtask 4's "note anything it
   added".

| Error text | Plain English |
| --- | --- |
| `duplicate key value violates unique constraint "scratch_pk_pkey"` | That id already exists in the table; the PK rule rejected the row. |
| `null value in column "id" ... not-null constraint` | PRIMARY KEY includes NOT NULL; a NULL id is refused. |

### Important terms

| Term | Meaning |
| --- | --- |
| **Primary key** | The identifying column: unique + not null, enforced. One per table. |
| **`tablename_pkey`** | Auto-created name of the PK rule and its index. |
| **Index** | Fast lookup structure; PK lookups never scan the whole table. |

### Common mistakes

- **Business data as the key.** Emails change; keys must be meaningless and permanent
  (`uuid`). Email uniqueness is real but is a separate unique rule, not the key.
- **No primary key at all.** Allowed by Postgres; gives you unaddressable, possibly duplicate
  rows. Noticed at the worst time.
- **Assuming unique implies present.** A plain unique rule still allows NULLs; PRIMARY KEY is
  the package: unique *and* not null.

### Where it is used

Every table, no practical exceptions. `id uuid PRIMARY KEY` on `venue`, `event`,
`price_tier`. The PK is what foreign keys point at, what URLs carry, what JPA uses to track
objects.

### Link to the task

The "which column identifies a row" line of the subtask 2 paper design — `id uuid PRIMARY
KEY`, all three tables. In subtask 4, the `not null` and `_pkey` index in `\d` output are
things Postgres added for you.

### Summary for revision

- PK = unique + not null + index, enforced, one per table.
- `uuid PRIMARY KEY`: app mints, database guarantees no reuse.
- Keys are meaningless and permanent; business uniqueness is a separate rule.
- `\d` shows the PK as `not null` plus the `tablename_pkey` index.

### Check questions, with answers

**Question 1.** Given:

```sql
CREATE TABLE price_tier (id uuid PRIMARY KEY, name text NOT NULL);
INSERT INTO price_tier VALUES ('aaaaaaaa-0000-0000-0000-000000000000', 'VIP');       -- A
INSERT INTO price_tier VALUES ('aaaaaaaa-0000-0000-0000-000000000000', 'Standard');  -- B
INSERT INTO price_tier VALUES (gen_random_uuid(), 'VIP');                            -- C
```

B and C: accepted or rejected, and why?

**Answer.** B is rejected: its id duplicates A's id — `duplicate key value violates unique
constraint "price_tier_pkey"`. C is **accepted**: its id is fresh, and the primary key
polices only the `id` column — the duplicate *name* 'VIP' is none of its business. If tier
names must be unique, that is a separate uniqueness rule you would add deliberately.

**Question 2.** A teammate designs
`CREATE TABLE attendee (email text PRIMARY KEY, full_name text NOT NULL);` — "emails are
unique anyway". What breaks when an attendee changes email, and what design keeps emails
unique without the breakage?

**Answer.** The email *is* the identifier, so changing it changes the key: every other table
pointing at this attendee, every stored reference and URL, must be updated in step or it
dangles. Keys must be meaningless and permanent. Design:
`id uuid PRIMARY KEY, email text NOT NULL UNIQUE, full_name text NOT NULL` — identity lives
in `id` forever; the email stays unique but is free to change.

### Your doubts

None for this lesson.

---

## Lesson 10 — Foreign key: columns that point at another table

### Definition

A **foreign key** is a column whose values must exist as a primary key in another table.
`venue_id uuid NOT NULL REFERENCES venue(id)`: every event's `venue_id` must be the `id` of a
real `venue` row — anything else is refused.

### Why it is needed

Relationships are stored as plain columns holding another table's id. Without enforcement,
two rots: **ghost references** (an event pointing at a venue that never existed) and
**orphaning** (deleting a venue that still has events). Both are data lying about a
relationship. The foreign key makes both impossible at the gate.

### How it works, step by step

1. Inline declaration: `venue_id uuid NOT NULL REFERENCES venue(id)` — holds uuids, always
   present, each must exist in `venue.id`.
2. Checks run in both directions: inserts/updates of events verify the venue exists; deletes
   of venues (and dropping the venue table) verify nothing still points there.
3. Auto-named rule: `scratch_event_venue_id_fkey` (table, column, `fkey`).
4. Order matters: referenced table is created first and dropped last.

### Example — real session in `zenith` (scratch tables, dropped afterwards)

```text
postgres@localhost:zenith> CREATE TABLE scratch_venue (id uuid PRIMARY KEY, name text NOT NULL);
CREATE TABLE
postgres@localhost:zenith> CREATE TABLE scratch_event (id uuid PRIMARY KEY, venue_id uuid NOT NULL REFERENCES scratch_venue(id), name text NOT NULL);
CREATE TABLE

postgres@localhost:zenith> INSERT INTO scratch_venue VALUES ('aaaaaaaa-0000-0000-0000-000000000000', 'City Arena');
INSERT 0 1
postgres@localhost:zenith> INSERT INTO scratch_event VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000000', 'Jazz Night');
INSERT 0 1

postgres@localhost:zenith> INSERT INTO scratch_event VALUES (gen_random_uuid(), 'bbbbbbbb-0000-0000-0000-000000000000', 'Ghost Gig');
insert or update on table "scratch_event" violates foreign key constraint "scratch_event_venue_id_fkey"
DETAIL:  Key (venue_id)=(bbbbbbbb-...) is not present in table "scratch_venue".

postgres@localhost:zenith> DELETE FROM scratch_venue WHERE id = 'aaaaaaaa-0000-0000-0000-000000000000';
update or delete on table "scratch_venue" violates foreign key constraint "scratch_event_venue_id_fkey" on table "scratch_event"
DETAIL:  Key (id)=(aaaaaaaa-...) is still referenced from table "scratch_event".

postgres@localhost:zenith> DROP TABLE scratch_venue;
cannot drop table scratch_venue because other objects depend on it
```

Walkthrough:

1. Venue created first (must exist to be referenced), then event with `REFERENCES`.
2. 'Jazz Night' → real venue id: accepted.
3. 'Ghost Gig' → nonexistent id: rejected; `DETAIL` says the key *is not present in*
   `scratch_venue`. No ghost entered.
4. Deleting the referenced venue: rejected from the other side; the key *is still referenced
   from* `scratch_event`. Delete or re-home the events first. No orphans.
5. Even `DROP TABLE` of the referenced table is refused while the dependent exists; dropping
   in reverse order works.

| Error text | Plain English |
| --- | --- |
| `violates foreign key constraint ... is not present in table "venue"` | The row points at an id that does not exist in the parent table. |
| `... is still referenced from table "event"` | The parent row cannot go while children point at it. |
| `cannot drop table ... other objects depend on it` | Drop dependents first (or re-think). |

### Important terms

| Term | Meaning |
| --- | --- |
| **Foreign key** | Column whose values must exist as a PK in another table. |
| **`REFERENCES venue(id)`** | The clause declaring the link, checked both directions. |
| **Ghost reference / orphan** | Pointing at a nonexistent row / parent deleted under children. Both prevented. |
| **`_fkey`** | Suffix of the auto-named foreign key rule. |

### Common mistakes

- **Storing the venue's name in the event.** Duplicated data drifts on rename. The
  relationship column holds the *id*; the name lives once, in `venue`.
- **Nullable foreign keys by accident.** Without NOT NULL, an event may point at nothing.
  Occasionally a real choice; usually an accident. An event without a venue is nonsense.
- **Fighting the order.** "Table does not exist" on create, "cannot drop" on drop — the
  dependency direction talking. Referenced first in, last out.

### Where it is used

Every relationship everywhere: `event.venue_id → venue.id`,
`price_tier.event_id → event.id`, later bookings and payments. Joins (Task F5.5) walk exactly
these links.

### Link to the task

The "which columns point at another table" line of the paper design: `event.venue_id →
venue.id`; `price_tier.event_id → event.id`; `venue` points at nobody.

### Summary for revision

- FK = column of parent ids, enforced both directions, auto-named `_fkey`.
- No ghosts in, no orphans left behind; even DROP respects the dependency.
- FK + NOT NULL unless "may point at nothing" is a deliberate choice.
- Store ids, never names, for relationships.

### Check questions, with answers

**Question 1.** With one venue row ('City Arena', id `aaaaaaaa-...`) and
`event.venue_id uuid NOT NULL REFERENCES venue(id)`, what happens to each, in order:
(a) `INSERT INTO event VALUES (gen_random_uuid(), NULL, 'Secret Show');`
(b) `INSERT INTO event VALUES (gen_random_uuid(), 'aaaaaaaa-0000-0000-0000-000000000000', 'Jazz Night');`
(c) `DELETE FROM venue WHERE name = 'City Arena';`

**Answer.** (a) Rejected — but by NOT NULL, not the foreign key: NULL in `venue_id` violates
the not-null constraint before any reference checking happens. (b) Accepted — the id exists
in `venue`. (c) Rejected — 'Jazz Night' (inserted in b) still references that venue:
`violates foreign key constraint ... still referenced from table "event"`.

**Question 2.** A teammate stores relationships as
`event.venue_name text NOT NULL` — "no foreign key needed". The venue 'City Arena' is renamed
to 'City Arena Powered by ZenCola'. What does the data look like afterwards, and what design
avoids it?

**Answer.** There is no single place where the venue's name lives, so the rename only affects
whatever rows someone remembers to update: old events say 'City Arena', new ones say the new
name, and nothing connects them — the venue's identity has drifted apart with no error
anywhere. The design: a `venue` table owning the name once, and
`event.venue_id uuid NOT NULL REFERENCES venue(id)` — rename the venue in one row, and every
event follows automatically through the id link.

### Your doubts

None for this lesson.

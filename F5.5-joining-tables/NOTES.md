# NOTES.md — The four joins, drawn

Kept from Task F5.5 (subtask S10). Revise from this page alone before writing any join.

---

## The one machine, four settings

Every join does the same two things, then differs only in a third:

1. **Pair** — take every row of the left table with every row of the right table.
2. **Test** — keep a pair only if the `ON` condition is true for it.
3. **Rescue** — *this is the only difference between the four joins.*

| Join | Step 3 rescues | Keeps |
| --- | --- | --- |
| `INNER JOIN` | nothing | matched pairs only |
| `LEFT JOIN` | left rows that matched nothing | all left rows + matched pairs |
| `RIGHT JOIN` | right rows that matched nothing | all right rows + matched pairs |
| `FULL JOIN` | unmatched rows on **both** sides | everything |

---

## The reference data for every diagram below

Two tiny tables. Key `2` is the only value they share.

```text
  Table A                    Table B
  k | fruit                  k | colour
 ---+--------               ---+--------
  1 | apple      <-- left    2 | yellow
  2 | banana         only    3 | cherry   <-- right only
                                             matched: k = 2
```

Join condition used throughout: `ON a.k = b.k`

---

## Diagram 1 — `INNER JOIN`

Keep only what matched. Both sets of leftovers are thrown away, silently.

```text
     LEFT ONLY          MATCHED          RIGHT ONLY
      (apple)        (banana/yellow)      (cherry)
    ┌───────────┐  ┌─────────────────┐  ┌───────────┐
    │  dropped  │  │ #### KEPT ##### │  │  dropped  │
    └───────────┘  └─────────────────┘  └───────────┘
```

```sql
FROM a INNER JOIN b ON a.k = b.k
```

```text
 a_k | fruit  | b_k | colour
-----+--------+-----+--------
   2 | banana |   2 | yellow
(1 row)
```

**Say it as:** "only the ones that matched on both sides."

---

## Diagram 2 — `LEFT JOIN`

Keep every left row. Where there was no partner, the right columns come back `NULL`.

```text
     LEFT ONLY          MATCHED          RIGHT ONLY
      (apple)        (banana/yellow)      (cherry)
    ┌───────────┐  ┌─────────────────┐  ┌───────────┐
    │ ## KEPT ##│  │ #### KEPT ##### │  │  dropped  │
    │ right =   │  │                 │  │           │
    │   NULL    │  │                 │  │           │
    └───────────┘  └─────────────────┘  └───────────┘
```

```sql
FROM a LEFT JOIN b ON a.k = b.k
```

```text
 a_k | fruit  |  b_k   | colour
-----+--------+--------+--------
   1 | apple  | «NULL» | «NULL»
   2 | banana |      2 | yellow
(2 rows)
```

**Say it as:** "all of the left, plus their right-hand details if they have any."

**Promise:** every left row appears **at least once** — not exactly once.

---

## Diagram 3 — `RIGHT JOIN`

The mirror image. Keep every right row.

```text
     LEFT ONLY          MATCHED          RIGHT ONLY
      (apple)        (banana/yellow)      (cherry)
    ┌───────────┐  ┌─────────────────┐  ┌───────────┐
    │  dropped  │  │ #### KEPT ##### │  │ ## KEPT ##│
    │           │  │                 │  │ left =    │
    │           │  │                 │  │   NULL    │
    └───────────┘  └─────────────────┘  └───────────┘
```

```sql
FROM a RIGHT JOIN b ON a.k = b.k
```

```text
  a_k   | fruit  | b_k | colour
--------+--------+-----+--------
      2 | banana |   2 | yellow
 «NULL» | «NULL» |   3 | cherry
(2 rows)
```

**Say it as:** "all of the right, plus their left-hand details if they have any."

**Rule to keep:** `A RIGHT JOIN B` is the same as `B LEFT JOIN A`. **Always write `LEFT` and
reorder the tables instead** — the first table named should be the one the query is about.

---

## Diagram 4 — `FULL JOIN`

Keep everything from both sides, padding with `NULL` wherever there was no partner.

```text
     LEFT ONLY          MATCHED          RIGHT ONLY
      (apple)        (banana/yellow)      (cherry)
    ┌───────────┐  ┌─────────────────┐  ┌───────────┐
    │ ## KEPT ##│  │ #### KEPT ##### │  │ ## KEPT ##│
    │ right =   │  │                 │  │ left =    │
    │   NULL    │  │                 │  │   NULL    │
    └───────────┘  └─────────────────┘  └───────────┘
```

```sql
FROM a FULL JOIN b ON a.k = b.k
```

```text
  a_k   | fruit  |  b_k   | colour
--------+--------+--------+--------
      1 | apple  | «NULL» | «NULL»
      2 | banana |      2 | yellow
 «NULL» | «NULL» |      3 | cherry
(3 rows)
```

**Say it as:** "everything from both, lined up where they match."

**Why it is rare:** a `NOT NULL` foreign key makes orphans impossible on one side, so a full join
collapses into a left or right join. It earns its place only when the two sides are **not**
linked by a foreign key — reconciling expected invoices against received payments, for example.

---

## All four at once

```text
                LEFT ONLY        MATCHED        RIGHT ONLY
                 (apple)      (banana/yellow)    (cherry)

   INNER                      [##############]                → 1 row
   LEFT         [###########] [##############]                → 2 rows
   RIGHT                      [##############] [###########]  → 2 rows
   FULL         [###########] [##############] [###########]  → 3 rows
```

The **matched** block is identical in all four. The joins disagree only about the leftovers.

---

## Diagram 5 — the Cartesian product (no `ON` at all)

Not one of the four, but the thing that happens when step 2 never runs.

```text
   A (2 rows)          B (2 rows)            RESULT (2 × 2 = 4 rows)

    apple  ────┬────────  yellow              apple  + yellow
               │                              apple  + cherry
    banana ────┼────────  cherry              banana + yellow
               │                              banana + cherry
          every row paired
           with every row
```

```sql
FROM a CROSS JOIN b        -- deliberate
FROM a, b                  -- comma form: the accidental version
FROM a JOIN b ON true      -- rare but legal
FROM a JOIN b              -- SYNTAX ERROR: modern syntax demands an ON
```

**Size = left rows × right rows.** Measured on a real container: 4,000 × 4,000 = 16 million rows
in 459 ms; 20,000 × 20,000 = 400 million rows in 9.4 s, against 0.7 ms for a filtered baseline.

**Accidental cause:** the comma form with a missing or incomplete `WHERE`, most often when a
third table is added to the list and its matching condition is not.

---

## Diagram 6 — fan-out

Not a join type. A consequence of joining towards a "many" side.

```text
   event (1 row)              price_tier (3 rows)        RESULT (3 rows)

   Monsoon Rock Night  ─┬───  General  1499       Monsoon Rock Night | General | 1499
                        ├───  Balcony  2499       Monsoon Rock Night | Balcony | 2499
                        └───  VIP      4999       Monsoon Rock Night | VIP     | 4999

                                                  ^^^ the event's columns are COPIED
                                                      into every row
```

**Why:** each **surviving pair** becomes one output row — not each left row.

**Which direction fans out:**

| Joining... | Row count |
| --- | --- |
| towards the table that **holds** the foreign key (the "many" side) | **multiplies** |
| towards the table the foreign key **points at** (the "one" side) | **no change** |

**What it breaks:**

| Thing | What goes wrong | Fix |
| --- | --- | --- |
| `SUM` | Adds the same value once per child. Real example: true venue capacity 13,150 became 48,700. | Aggregate before joining, or sum only "many"-side columns |
| `COUNT(*)` | Counts children, not parents: 6 tiers instead of 3 events | `COUNT(DISTINCT <parent primary key>)` |
| `COUNT(*)` after a `LEFT JOIN` | Reports **1** for a parent with no children, because the rescued row is still a row | `COUNT(<right-hand column>)` — it skips `NULL` |
| `LIMIT` | Takes n **rows**, not n parents. Cuts mid-parent, giving a partially loaded object that looks complete. | Page the parents in a CTE first, then join |

---

## Diagram 7 — the silent inner join (`ON` versus `WHERE`)

The most common join bug in professional code.

```text
   CLAUSE RUN ORDER
   ────────────────────────────────────────────────────────────
   1. FROM / JOIN  →  pair, test with ON, RESCUE unmatched rows
   2. WHERE        →  filters the FINISHED result
   3. GROUP BY     →  collapses groups
   4. SELECT       →  picks columns
   5. ORDER BY     →  sorts
   6. LIMIT        →  takes a slice
```

```text
   BROKEN                                  FIXED
   ─────────────────────────────           ─────────────────────────────
   LEFT JOIN price_tier pt                 LEFT JOIN price_tier pt
     ON pt.event_id = e.id                   ON pt.event_id = e.id
   WHERE pt.amount > 50                      AND pt.amount > 50

   step 1: rescue adds                     step 1: condition is part of
           Open Mic Night with NULL                the MATCH test
   step 2: NULL > 50 is UNKNOWN            (no step 2 — nothing to undo
           WHERE keeps only TRUE                    the rescue)
           → the rescued row is DELETED

   → 6 rows  (silently an INNER JOIN)      → 7 rows  (a real LEFT JOIN)
```

**Why it happens:** `NULL > 50` is not false, it is **unknown**. `WHERE` keeps only rows whose
condition is **true**, so unknown rows are discarded exactly like false ones.

**Why nobody catches it:** on an `INNER JOIN` there is no rescue step, so `ON` and `WHERE` give
identical results. The habit "conditions go in `WHERE`" is correct everywhere it was first
learned, and only turns dangerous once a rescue step exists.

**Safe or not:**

| `WHERE` condition on... | Safe? |
| --- | --- |
| A table joined with `INNER JOIN` | **Yes** |
| The left-hand table | **Yes** |
| The right-hand table of a `LEFT JOIN` | **No** — moves it into `ON` instead |
| The right-hand table, testing `IS NULL` | **Yes** — this is the deliberate anti-join |

---

## Two shorthands to avoid

```text
   USING (col)        =  ON left.col = right.col, AND merges the two columns into one.
                         Needs the SAME column name on both sides.
                         This schema uses id + <table>_id, so it does not apply.

                         TRAP: USING (id) compiles — both tables have an `id` —
                         and matches two unrelated primary keys. 0 rows, no error.

   NATURAL JOIN       =  matches on EVERY shared column name, invisibly.
                         event + venue share id, name, created_at → 0 rows, no error.
                         Its meaning changes when a column is added. NEVER USE IT.
```

---

## The checklist before running any join

1. **Which table am I keeping all of?** That one goes on the left. Then pick inner or left.
2. **Which table holds the foreign key?** That is the "many" side, and joining towards it
   multiplies rows.
3. **What row count do I predict,** and can I justify it from `NOT NULL` and the foreign keys?
4. **Is there a `WHERE` naming the right-hand table of a `LEFT JOIN`?** If so, and it is not
   `IS NULL`, move it into the `ON`.
5. **Am I counting or summing?** If the join fanned out, `COUNT(*)` and `SUM` are wrong.
6. **Is there a `LIMIT`?** If the join fanned out, page the parents first.

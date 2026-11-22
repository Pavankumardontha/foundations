# F6.11 — Notes

## Project rule (Lesson 6): a relationship with data becomes an entity

The moment a relationship needs data of its own (who granted the role, and when), it is
not a `@ManyToMany` any more. Make the join table a real table with its own primary key,
keep the pair rule with a unique constraint, map it as an entity, and link both sides
with `@ManyToOne`.

Example in this project: `user_role (user_id, role_id)` grew into
`role_grant (id, user_id, role_id, granted_by, granted_at)` mapped by `RoleGrantEntity`.

## Project rule (Lesson 12): records built inside the transaction

Services never return entities. The `@Transactional` service method loads entities,
touches every relationship the response needs (the session is alive there), copies the
data into a record, and returns the record. Controllers and JSON writing only ever see
finished records — nothing outside the service can trigger SQL or
`LazyInitializationException`, and the API shape is chosen on purpose instead of leaking
the table shape.

Example in this project: `GET /v2/events/{id}` → `L12EventService.eventSummary(...)` →
`EventSummary(id, name, startsAt, venueName)`.

## The number (Lesson 13, S8): the N+1, seen live

Loading **20** events and touching each event's venue while building summaries fired
**21 SQL statements** — 1 select for the event list + 20 selects for the 20 venues, one
per row (counted with `grep -c "Hibernate:"` on the run log).

The shape of the disease: statements grow with rows. 20 rows → 21 statements;
20,000 rows → 20,001 statements. The code looks correct and *is* correct — only the
query count is wrong.

## The one-sentence diagnostic (Lesson 15, S10)

**If the number of SQL statements grows with the number of rows returned, it is an N+1 —
fix it by loading the relationship in the same query (`join fetch` or `@EntityGraph`).**

Verified both fixes on the same 20 events:

| Approach | Statements | Join kind in the SQL |
|---|---|---|
| Lazy touch in a loop (Lesson 13) | 21 | n/a (one select per row) |
| `join fetch` in `@Query` (Lesson 14) | 1 | inner `join` |
| `@EntityGraph(attributePaths="venue")` (Lesson 15) | 1 | `left join` (outer) |

## Entity hygiene rules (Lesson 19, S12): what we write instead of `@Data`

Never put `@Data` (or `@Setter`, or `@EqualsAndHashCode`) on an entity. Instead:

1. **`@Getter` only** — read access from outside; no blanket setters. Mutate through
   narrow, intention-revealing methods (e.g. the `addSeat` helper that keeps both sides of
   a relationship in sync), never a raw `setX`.
2. **Explicit constructors** — a `protected` no-arg constructor for Hibernate, and a
   public constructor that assigns the business key and required fields.
3. **A stable business key** — a client-assigned `UUID`, set in the constructor, never
   changed. Not a database-generated id (which is null before save, a value after).
4. **`equals`/`hashCode` on that key** — using `instanceof` (not `getClass() ==`) so a
   Hibernate proxy still compares equal, and reading the other object's key through its
   **getter** so the proxy initializes. The key never changes, so the hashCode never
   changes.
5. **`toString()` on scalar fields only** — never a relationship, to avoid hidden lazy
   loads, `LazyInitializationException`, and `StackOverflowError`.

Why each rule exists (the dangers from Lessons 16-18):

| Rule | Danger it prevents |
|---|---|
| `toString()` excludes relationships | hidden queries / `LazyInitializationException` / `StackOverflowError` (L16) |
| `equals`/`hashCode` on a stable key | a `HashSet` losing an entity after save (L17) |
| no `setId`, no blanket setters | mutating a managed id ("identifier was altered"), broken two-sided relationships (L18) |
| `instanceof` + getter in `equals` | a loaded entity and its proxy comparing unequal (L8) |

Proven: `GoodNoteEntity` (UUID key assigned in the constructor) kept its hashCode
`268901588` identical before and after save, so `HashSet.contains` stayed `true` — the
exact scenario that failed in Lesson 17.

## Checkpoint 5 — two reasons `@Data` is dangerous on an entity

1. Its `toString()`/`equals()`/`hashCode()` include **relationships**, so logging or
   comparing an entity triggers lazy loads (hidden queries, `LazyInitializationException`)
   or infinite recursion (`StackOverflowError`).
2. Its `equals()`/`hashCode()` include the **mutable / database-generated id**, so an
   entity's hash changes after save and hash-based collections (`HashSet`, `@OneToMany
   Set`) silently lose it; and its blanket **setters** (including `setId`) let code break
   invariants the entity should protect.

# Task F3.7 — Sealed interfaces, pattern matching, and text blocks

## Concept 1 — Sealed interfaces: a closed set of types

A **sealed interface** names, right in its declaration, the *only* types allowed to implement
it. The list is written after the keyword `permits`:

```java
sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}
```

Read out loud: *"A `HoldResult` is a `Granted`, a `Rejected`, or a `PartiallyGranted` — and
nothing else, ever."*

### Why it matters

An ordinary interface is an *open invitation*: any class, in any file, written by anyone, any
time in the future, can say `implements HoldResult`. That is right for `Comparable`-style
contracts, where unknown future types are the point.

A seat-hold request is different: the answer is one of a **known, fixed set of outcomes**.
With an ordinary interface the compiler cannot know the set is fixed, so it cannot check that
code handling "all" outcomes really covers all of them. `sealed` tells the compiler: **the
list is complete, here it is.** Everything else in this task builds on that.

Analogy: an ordinary interface is a party anyone can walk into. A sealed interface is a party
with a guest list — `permits` *is* the guest list, and the compiler is the bouncer.

### How it works

1. **`sealed`** on the interface says "this has a guest list."
2. **`permits A, B, C`** is the guest list.
3. Each permitted type must implement the interface, and must itself say what *its* future
   is: `final` (no subclasses), `sealed` (its own guest list), or `non-sealed` (open again
   from this point down). **Records are implicitly `final`**, so records satisfy this
   automatically.

The compiler enforces the list *in both directions*: a type on the list that does not
implement the interface is an error, and a type not on the list that tries to implement it is
an error.

Convenience: if all implementing types live in the **same file** as the sealed interface, the
`permits` clause may be omitted and the compiler infers it. Explicit is clearer while learning.

### Step 1 — the closed set (`Concept1.java`)

```java
import java.util.List;

sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

record Granted(String holdId, List<String> seatIds) implements HoldResult {}
record Rejected(String reason) implements HoldResult {}
record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

public class Concept1 {
    public static void main(String[] args) {
        HoldResult a = new Granted("H-101", List.of("A1", "A2"));
        HoldResult b = new Rejected("sold out");
        HoldResult c = new PartiallyGranted("H-102", List.of("B1"), 2);

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
    }
}
```

Output (Java 21):

```
Granted[holdId=H-101, seatIds=[A1, A2]]
Rejected[reason=sold out]
PartiallyGranted[holdId=H-102, seatIds=[B1], missing=2]
```

(Record refresher: `record Rejected(String reason)` generates a final, immutable class with a
constructor, a `reason()` accessor, `equals`, `hashCode`, and the `toString` printed above.)

### Step 2 — break it on purpose (`Concept1Broken.java.txt`)

A fourth record implements `HoldResult` **without** being added to `permits`:

```java
record Expired(String holdId) implements HoldResult {}
```

Compiler output:

```
Concept1Broken.java:11: error: class is not allowed to extend sealed class: HoldResult (as it is not listed in its 'permits' clause)
record Expired(String holdId) implements HoldResult {}
^
1 error
```

| Part of the error | What it means |
| --- | --- |
| `class is not allowed to extend sealed class: HoldResult` | `Expired` tried to join the `HoldResult` family. (javac says "class"/"extend" even for a record implementing an interface — general wording.) |
| `as it is not listed in its 'permits' clause` | The reason: `Expired` is not on the guest list. |
| `^` under line 11 | Points at the declaration that tried to sneak in. |

**This error is the feature.** Nobody can quietly add a fourth outcome. Adding one *requires*
editing `permits`, and that edit then lights up every `switch` over the type (Concept 2).

### Practical application

Sealed types fit a **fixed menu of alternatives** where each alternative carries different
data (that is why records, not an enum):

- operation results: approved / declined / pending
- command outcomes: success / validation-failed / conflict
- workflow states: draft / submitted / published
- protocol messages: request / response / heartbeat

### Common mistakes

- **Sealing everything.** `sealed` is only right when the set is genuinely closed by the
  nature of the problem. Plugin APIs and extensible hierarchies want an ordinary interface.
- **Forgetting permitted types must be `final`, `sealed`, or `non-sealed`.** Records never
  show this (implicitly final); a plain `class` on the list triggers the error (see doubts).
- **A `permits` entry that doesn't implement the interface.** Compile error — list and
  reality must match both ways.
- **Thinking `sealed` means immutable.** It seals the *set of types*, not the data inside
  them. Immutability comes from records / F3.8 techniques.

### Your doubts

> **Doubt:** "Each permitted type must say what its future is — `final`, `sealed`, or
> `non-sealed`… I did not understand this point at all. Explain in detail with proper
> examples."

**The one idea:** `sealed` promises the compiler that the complete family of types is
knowable. But inheritance is a back door: if `Failed implements Result`, then *any subclass
of `Failed` is also a `Result`* — automatically, without touching `permits`. So controlling
direct implementors is not enough; if `Failed` could be freely subclassed, the "closed" set
would leak open one level down. Java therefore forces every permitted type to declare what
happens below it. Three possible answers, and silence is forbidden.

**Seeing the rule fire** (`Doubt1Broken.java.txt`) — a plain class on the permits list:

```java
sealed interface Result permits Ok, Failed {}

final class Ok implements Result {}

class Failed implements Result {}     // plain class — says nothing about its subclasses
```

```
Doubt1Broken.java:7: error: sealed, non-sealed or final modifiers expected
class Failed implements Result {}
^
```

The error states the rule literally: pick one of the three.

**Answer 1 — `final`: "nothing below me."** The branch ends; the family is exactly
`{Ok, Failed}`. The most common answer.

```java
final class Failed implements Result {}
```

**Answer 2 — `sealed`: "below me is another guest list."** (`Doubt1Sealed.java`, compiles
and runs.) The tree is still fully known, just two levels deep: `{Ok, NetworkFailed, DiskFailed}`.
The sub-list members must again answer the same question (here: `final`).

```java
sealed class Failed implements Result permits NetworkFailed, DiskFailed {}

final class NetworkFailed extends Failed {}
final class DiskFailed   extends Failed {}
```

**Answer 3 — `non-sealed`: "I deliberately reopen my branch."** (`Doubt1NonSealed.java`)

```java
non-sealed class Failed implements Result {}

class NetworkFailed extends Failed {}   // NOT on any permits list — legal now
```

Output:

```
is it a Result? true
actual class:   NetworkFailed
```

`NetworkFailed` is a `Result` though no `permits` clause mentions it — the back door, but as
a *documented decision* written in the source. The compiler still knows every `Result` is
either `Ok` or *some kind of* `Failed`; it just cannot list the kinds of `Failed`.

So the rule is **not** "sealing must go all the way down." It is: **no branch may be open by
accident.** Only silence is forbidden.

**Why records never show this:** a `record` is implicitly `final`. Proof
(`Doubt1RecordFinal.java.txt`) — subclassing a record fails:

```
Doubt1RecordFinal.java:8: error: cannot inherit from final Granted2
```

A record arrives with Answer 1 pre-filled, which is why `HoldResult`'s records compiled
without ever writing `final`.

| You write | Meaning | Can someone extend it? | Compiler knows the full set? |
| --- | --- | --- | --- |
| `final class Failed` | branch ends here | no | yes |
| `sealed class Failed permits …` | branch continues with its own list | only the listed ones | yes |
| `non-sealed class Failed` | branch reopened on purpose | anyone | only down to `Failed`, not below |
| `class Failed` (nothing) | — | — | **compile error** — silence not allowed |
| `record Failed(…)` | implicitly `final` | no | yes |

### Check yourself

1. *A teammate suggests a comment — `// only Granted, Rejected, PartiallyGranted should
   implement this` — instead of `sealed`. What can go wrong?*
   A comment is not enforced. Anyone can add an implementation anyway, and every "handles
   all outcomes" spot silently becomes wrong. `sealed` turns the same mistake into a listed
   compile error.
2. *Two changes would each make `Concept1Broken.java` compile — which keeps the set closed?*
   (a) Add `Expired` to the `permits` list — keeps the set closed (now four known types).
   (b) Remove `implements HoldResult` from `Expired` — also compiles, but `Expired` is then
   not an outcome at all. Only (a) extends the closed set.

### Summary

- `sealed interface X permits A, B, C` = the complete, compiler-known list of implementors.
- Implementing without being on the list → compile error ("not allowed to extend sealed
  class"). That error is the feature.
- Every permitted type must choose: `final`, `sealed`, or `non-sealed`. Records are
  implicitly final, so they fit naturally.
- Use sealed for closed sets of outcomes; ordinary interfaces for open-ended extension.

## Concept 2 — The exhaustive `switch`: no `default`, checked by the compiler

An **exhaustive switch** covers every possible case. Over a sealed type the compiler *knows*
the full list of possibilities, so it can **verify** coverage — and you write **no `default`
branch**.

### Why no `default` is the point

`default` means *"for anything I didn't think of, do this."* When a fourth outcome is added
later, `default` **silently swallows it** — the code compiles, runs, and quietly does the
wrong thing in production. Without `default`, the compiler must *prove* the cases cover
everything; the moment a new type joins `permits`, the proof breaks and the build fails,
pointing at every switch that needs updating. The bug moves from runtime to build time.

### Machinery

- **Switch expression** (Java 14+): `return switch (x) { case ... -> ...; };` — produces a
  value, arrow arms, no fall-through.
- **Type pattern in `case`**: `case Granted g ->` means "if the value is a `Granted`, call it
  `g` in this arm" — already typed, no cast.
- Over a sealed type the compiler checks: cases covered vs. the `permits` list. Complete →
  compiles with no `default`. Incomplete → compile error.

### Step 1 — three cases, no `default`, compiles (`Concept2.java`)

```java
static String describe(HoldResult r) {
    return switch (r) {
        case Granted g          -> "granted: hold " + g.holdId() + " for seats " + g.seatIds();
        case Rejected rej       -> "rejected: " + rej.reason();
        case PartiallyGranted p -> "partial: hold " + p.holdId() + ", got " + p.seatIds()
                                   + ", still missing " + p.missing();
    };
}
```

Output (Java 21):

```
granted: hold H-101 for seats [A1, A2]
rejected: sold out
partial: hold H-102, got [B1], still missing 2
```

### Step 2 — the payoff: add `Expired`, forget the switch (`Concept2Broken.java.txt`)

```java
sealed interface HoldResult permits Granted, Rejected, PartiallyGranted, Expired {}
record Expired(String holdId) implements HoldResult {}
// describe() unchanged — still three cases
```

```
Concept2Broken.java:15: error: the switch expression does not cover all possible input values
        return switch (r) {
               ^
1 error
```

| Part of the error | What it means |
| --- | --- |
| `does not cover all possible input values` | permits now has 4 types, the cases cover 3 — the exhaustiveness proof failed. |
| `^` at `switch (r)` | Points at the incomplete switch. In a real codebase you get one error *per* such switch — a complete to-do list. |

Two worlds compared:

- **With `default`:** compiles; `Expired` falls into `default`; wrong behaviour discovered in
  production weeks later.
- **Sealed, no `default`:** build fails immediately, listing every place that must handle
  `Expired`.

Fix = one line (`Concept2Fixed.java`): `case Expired e -> "expired: hold " + e.holdId() + ...`.
Output after fix adds: `expired: hold H-103 no longer reserved`.

**New cases became compile errors instead of production bugs.**

### Practical application

A result flows through many handlers — booking formats a message, metrics counts outcomes,
the API layer maps to HTTP status. Each is a switch over `HoldResult`. Adding `Expired`
turns *all* of them into compile errors; none can be forgotten. Without sealing you would be
grepping and hoping.

### Common mistakes

- **Adding `default` "just to be safe."** It *disables* the exhaustiveness check — the
  compile-time safety net is traded for a runtime mystery. Over a sealed type, `default`
  turns the feature off.
- **Expecting the same check from an old statement-switch.** The guarantee is strongest with
  switch *expressions*, which must produce a value for every input.
- **Null worry:** a pattern switch throws `NullPointerException` for `null` (unless you add
  `case null`) — it does not silently skip.

### Check yourself

1. *A teammate adds `default -> "unknown result";` — what was lost?*
   The exhaustiveness check. New permitted types now compile silently into the `default`
   branch; forgotten handlers are found at runtime, not build time.
2. *Why did the compiler complain at the switch rather than at the `Expired` record?*
   `Expired` is legal — it is on the `permits` list. The thing that became wrong is the
   switch's claim to cover all inputs, so that is where the proof fails.

### Summary

- Sealed type + switch expression + no `default` = compiler-verified exhaustiveness.
- `case Granted g ->` is a type pattern: tested and typed in one step, no cast.
- Adding a permitted type breaks every incomplete switch at *build* time, with locations.
- Writing `default` over a sealed type disables the safety net — avoid it.

## Concept 3 — Record patterns: destructuring inside the switch

A **record pattern** matches a record *and pulls its components into variables* in one step.
The pattern looks like the record's own declaration:

```java
case Granted(String id, var seats) -> ...
```

Taking a structure apart into named pieces is called **destructuring**.

### Why it matters

With a type pattern (`case Granted g ->`) the arm still calls accessors: `g.holdId()`,
`g.seatIds()`. `g` is a middleman. The record pattern cuts it out — the pieces land directly
in named variables, and the case label documents what data the arm uses.

Before pattern matching existed, this spot in Java was: test with `instanceof`, cast, extract
— three chances to make a mistake:

```java
if (r instanceof Granted) {
    Granted g = (Granted) r;      // the cast you never write any more
    String id = g.holdId();
}
```

The record pattern is all three collapsed into one compiler-checked line.

### How it works

Matching `case Granted(String id, var seats)`:

1. **Type test** — is the value a `Granted`? If not, try the next case.
2. **Extraction** — if yes, the record's accessors run behind the scenes.
3. **Binding** — results land in fresh variables `id` and `seats`, visible only in that arm.

Rules:

- The pattern must list **exactly as many components as the record has, in order**.
- Each component may use an explicit type (`String id`) or **`var`** — identical in effect.
- Exhaustiveness checking (Concept 2) is unchanged.

### Step 1 — `describe` rewritten with record patterns (`Concept3.java`)

```java
static String describe(HoldResult r) {
    return switch (r) {
        case Granted(String id, var seats)                    -> "granted " + seats + " under hold " + id;
        case Rejected(String reason)                          -> "rejected: " + reason;
        case PartiallyGranted(var id, var seats, int missing) -> "got " + seats + ", missing " + missing
                                                                 + " (hold " + id + ")";
    };
}
```

Output (Java 21):

```
granted [A1, A2] under hold H-101
rejected: sold out
got [B1], missing 2 (hold H-102)
```

### Step 2 — break the shape rule (`Concept3Broken.java.txt`)

`Granted` has two components; the pattern names only one:

```java
case Granted(String id) -> "granted under hold " + id;
```

```
Concept3Broken.java:14: error: incorrect number of nested patterns
  required: String,List<String>
  found: String
```

| Part of the error | What it means |
| --- | --- |
| `incorrect number of nested patterns` | The pattern's component list doesn't match the record's. |
| `required: String,List<String>` | `Granted` declares two components of these types. |
| `found: String` | The pattern supplied one. |

Unused components must still be named (convention: `var ignored`). The shape always matches
in full, by **position**, not by name.

### Step 3 — nesting (`Concept3Nested.java`)

```java
record Seat(String row, int number) {}
record Booking(String id, Seat seat) {}

case Booking(String id, Seat(String row, int num)) ->
        "booking " + id + " is at row " + row + ", seat " + num;
```

Output: `booking B-7 is at row C, seat 12`. One case label checks two types and extracts
three values — manually that is two tests, two casts, three extractions. (A `default` is fine
here because the selector is `Object`, which is not sealed.)

### Common mistakes

- **Wrong number of components** — the error above.
- **Believing components bind by name.** They bind by *position*; swapped names compile but
  lie. Keep pattern variable names in the record's declared order.
- **Destructuring when the arm also needs the whole object.** Then `case Granted g` plus
  accessors reads better. Destructure when you only want the pieces.

### Check yourself

1. *What three things happen at `case Granted(String id, var seats)`?*
   Type test (is it a `Granted`?), extraction (accessors run), binding (fresh variables for
   this arm only).
2. *`case Rejected(String reason, String code)` — what happens, when?*
   Compile error (`incorrect number of nested patterns`) — at build time, never runtime:
   `Rejected` has one component, the pattern claims two.

### Summary

- Record pattern = type test + extraction + binding in one compiler-checked step.
- Shape must mirror the record exactly: all components, in order; `var` allowed.
- Patterns nest — destructure records inside records in one case label.
- You never write a cast; wrong shapes die at compile time.

## Concept 4 — Guarded patterns: attaching a condition with `when`

A **guarded pattern** is a case label with an extra boolean condition attached using `when`:

```java
case PartiallyGranted p when p.missing() > 5 -> ...
```

"If the value is a `PartiallyGranted` **and** its `missing` count is more than 5, take this
arm." The condition after `when` is the **guard**.

### Why it matters

Sometimes the *type* alone doesn't decide the response — the *data inside* matters. Missing 2
seats → offer to continue; missing 8 → escalate. Same type, different business rule. Without
guards the condition hides in an `if` inside the branch; the guard lifts it into the case
label, so the switch reads like a rule table: every "situation → response" pair on one line.

### How it works

1. **Pattern test first** — not a `PartiallyGranted`? Skip the case; the guard never runs.
2. **Guard second** — pattern matched, `p` bound, guard evaluated.
3. Both true → arm taken. Guard false → matching **continues to the next case**.

Two compiler rules follow:

- **Guarded cases never count toward exhaustiveness.** The compiler does not analyze guard
  conditions; an unguarded case for the type must exist.
- **Order matters: specific (guarded) first, general (unguarded) after** — cases are tried
  top to bottom, and unreachable cases are compile errors.

### Step 1 — the guard in action (`Concept4.java`)

```java
static String describe(HoldResult r) {
    return switch (r) {
        case Granted(String id, var seats) -> "granted " + seats + " under hold " + id;
        case Rejected(String reason)       -> "rejected: " + reason;
        case PartiallyGranted p when p.missing() > 5
                                           -> "ESCALATE: hold " + p.holdId() + " is short "
                                              + p.missing() + " seats — offer another show";
        case PartiallyGranted p            -> "partial: hold " + p.holdId() + ", missing "
                                              + p.missing() + " — offer to continue";
    };
}
```

Output for `missing = 2` and `missing = 8` (Java 21):

```
partial: hold H-102, missing 2 — offer to continue
ESCALATE: hold H-103 is short 8 seats — offer another show
```

### Step 2 — same logic with a nested `if`, for comparison

```java
case PartiallyGranted p -> {
    if (p.missing() > 5) {
        yield "ESCALATE: ...";
    } else {
        yield "partial: ...";
    }
}
```

(`yield` produces the switch expression's value from a `{}` block arm.) Identical behaviour —
verified — but the decision is buried a level deep, needs a block and two `yield`s, and grows
messier with each threshold. The guard version grows one line per rule.

### Step 3 — the two guard errors

**Only a guarded case, no unguarded fallback** (`Concept4OnlyGuard.java.txt`):

```
Concept4OnlyGuard.java:14: error: the switch expression does not cover all possible input values
```

A `PartiallyGranted` with `missing() <= 5` has nowhere to go. Guards are always "maybe" to
the compiler — they never prove coverage.

**Unguarded case placed before the guarded one** (`Concept4Dominated.java.txt`):

```
Concept4Dominated.java:18: error: this case label is dominated by a preceding case label
```

**Dominated** = an earlier case already swallows everything this one could match, making it
dead code. Java rejects unreachable cases instead of silently ignoring them.

### Common mistakes

- Relying on guards for exhaustiveness — always keep an unguarded case per type.
- Putting the general case first — domination error.
- Complex multi-clause guards — at some point a nested `if` or a separate method is clearer.

### Check yourself

1. *Why doesn't the compiler analyze `p.missing() > 5` to see what's covered?*
   Guards are arbitrary boolean code (method calls, I/O, anything). Proving what they cover
   is impossible in general, so the compiler conservatively treats every guarded case as
   "maybe matches."
2. *Delete `when` but keep both `PartiallyGranted` cases — what happens?*
   The first case now catches every `PartiallyGranted`; the second is dead code → the
   `dominated by a preceding case label` error.

### Summary

- `when` attaches a condition to a pattern; pattern first, guard second; false guard → fall
  to next case.
- Guards never count toward exhaustiveness — keep an unguarded case.
- Specific before general, or the dominated-case compile error.
- Guards keep business thresholds visible at the case-label level.

## Concept 5 — Pattern matching for `instanceof`: the everyday form

`instanceof` can test a type *and* introduce a typed variable in one step:

```java
if (result instanceof Rejected rej) {
    log(rej.reason());
}
```

"If `result` is a `Rejected`, call it `rej` inside this block." The form you write most often
in day-to-day code — `switch` is for handling *all* cases; `instanceof` is for caring about
*one*.

### Step 1 — a variable has a label; the object inside has a real type

```java
HoldResult r = new Rejected("sold out");
```

- `new Rejected("sold out")` creates an actual **`Rejected` object** — what the thing
  *really is*.
- `HoldResult r` is the **variable's label**: "some kind of `HoldResult`, not saying which."

Analogy: `r` is a sealed parcel with a sticker "contains: Granted or Rejected". **The
compiler only reads the sticker, never opens the parcel.**

### Step 2 — the problem: the label blocks you (`Doubt5ABroken.java.txt`)

```java
HoldResult r = new Rejected("sold out");
System.out.println(r.reason());
```

```
Doubt5ABroken.java:12: error: cannot find symbol
  symbol:   method reason()
  location: variable r of type HoldResult
```

The compiler refuses because `r`'s label is `HoldResult`, and that interface has no
`reason()`. Why so stubborn when the object plainly is a `Rejected`? Because in real code
`r` arrives as a parameter — `void handle(HoldResult r)` — and callers pass *any* permitted
type. Only operations safe for **every** possibility are allowed through the label.
**The label blocks you, not the object.** This is the entire problem the concept solves.

### Step 3 — `instanceof` asks what's really inside (`Doubt5A.java`)

```java
HoldResult a = new Rejected("sold out");
HoldResult b = new Granted("H-1");
System.out.println("is a a Rejected? " + (a instanceof Rejected));
System.out.println("is b a Rejected? " + (b instanceof Rejected));
```

```
is a a Rejected? true
is b a Rejected? false
```

A runtime yes/no question — nothing more. It does not yet let you call `reason()`.

### Step 4 — the old world: test, then cast (`Doubt5B.java`)

```java
if (a instanceof Rejected) {            // step 1: ask
    Rejected rej = (Rejected) a;        // step 2: cast — relabel, on YOUR responsibility
    System.out.println(rej.reason());   // step 3: use
}
```

Output: `sold out`. The cast `(Rejected) a` copies the same reference into a new variable
labeled `Rejected` — through *that* label, `reason()` is visible.

Danger: the compiler does not connect the test to the cast. This compiles without warning
(`Concept5OldBug.java`):

```java
if (r instanceof Granted) {
    Rejected rej = (Rejected) r;        // wrong cast — compiles anyway
    System.out.println(rej.reason());
}
```

and crashes at runtime:

```
Exception in thread "main" java.lang.ClassCastException: class Concept5OldBug$Granted cannot be cast to class Concept5OldBug$Rejected
	at Concept5OldBug.logOldBug(Concept5OldBug.java:15)
```

| Part of the error | What it means |
| --- | --- |
| `ClassCastException` | "You promised a `Rejected`; it is actually a `Granted`." |
| thrown at runtime | The test/cast disagreement was invisible at compile time. |

### Step 5 — the new form: ask + relabel + name in one step (`Doubt5C.java`)

```java
if (a instanceof Rejected rej) {
    System.out.println("a: " + rej.reason());
}
if (b instanceof Rejected rej) {
    System.out.println("b: " + rej.reason());   // never runs — b is a Granted
}
System.out.println("done");
```

```
a: sold out
done
```

Match succeeds → `rej` ready, already typed. Match fails → block skipped, no crash. The
wrong-cast bug is now *unwritable*: there is no cast in your code, so test and relabel can
never disagree.

### Step 6 — flow scoping: the variable exists only where the match is proven

Using `rej` after the block (`Concept5Scope.java.txt`):

```java
if (r instanceof Rejected rej) {
    System.out.println(rej.reason());    // fine — match guaranteed here
}
System.out.println(rej.reason());        // outside the proven region
```

```
Concept5Scope.java:15: error: cannot find symbol
  symbol:   variable rej
```

Outside the guaranteed region `rej` does not merely hold a wrong value — it **does not
exist**. The rule is not "inside the braces"; it is "wherever a successful match is
provable." Hence the early-return shape (`Doubt5D.java`):

```java
static String reasonOrDefault(HoldResult r) {
    if (!(r instanceof Rejected rej)) {
        return "not a rejection";               // every non-Rejected exits here
    }
    return "rejected because: " + rej.reason(); // only reachable if match succeeded
}
```

```
rejected because: sold out
not a rejection
```

The only way to reach the last line is a successful match, so `rej` lives there — outside
any braces.

### Common mistakes

- **An `instanceof` chain where a `switch` belongs.** `if/else if` ladders have no
  exhaustiveness check — a new permitted type silently falls off the end. Use the exhaustive
  `switch` when every case must be handled.
- **Expecting the variable after the block** — `cannot find symbol`. Use the early-return
  form if you need it afterward.
- **`null`:** `null instanceof Rejected rej` is just `false` — block skipped, no exception.
  (A pattern `switch` on `null` *throws* unless `case null` exists.)

### Your doubts

> **Doubt:** "I did not understand this concept at all. Explain in more detail, break it
> into more details, more examples, incrementally."

Resolved by rebuilding the concept as Steps 1–6 above (label vs object → the blocked call →
`instanceof` as a question → test-then-cast and its runtime trap → the one-step pattern form
→ flow scoping). Key sentences that made it click:

- The compiler reads the variable's **label**, never the actual object: *the label blocks
  you, not the object.*
- A cast is *you overriding the compiler on your own responsibility* — and the old world
  never checked that the test and the cast agreed.
- The pattern form fuses ask + relabel + name into one unit, so disagreement is impossible.
- The pattern variable exists exactly where a successful match is *provable* — no more, no
  less.

### Check yourself

1. *`HoldResult r = new Rejected("sold out"); System.out.println(r.reason());` — the object
   really is a `Rejected`. Why does compilation fail?*
   The call goes through variable `r`, whose declared type is `HoldResult` — an interface
   with no `reason()`. The compiler only consults the declared type (the label), never the
   runtime contents.
2. *`HoldResult b = new Granted("H-1"); if (b instanceof Rejected rej) { ... } print("done")`
   — compile error, crash, or runs?*
   Runs fine, prints only `done`. A failed pattern match is a non-event: the test answers
   "no", the block is skipped.

### Summary

- Declared type = label; it limits what you may call regardless of the real object.
- Old world: `instanceof` test + manual cast — test and cast can disagree; that bug compiles
  and crashes at runtime (`ClassCastException`).
- New world: `instanceof Rejected rej` — one checked unit, no cast to get wrong.
- Flow scoping: the variable exists exactly where the match is proven — inside the `if`, or
  after an early return that eliminated the "no" path.

## Concept 6 — Text blocks: multi-line strings without escaping

A **text block** is a string between triple quotes (`"""`) typed like normal text: Enter
makes a new line, a quote is just a quote, no special codes.

### Step 1 — the old way: special codes inside normal strings (`Doubt6A.java`)

A normal string must stay on one line of code. A line break must be typed as `\n`; a quote
inside must be typed as `\"`:

```java
String s = "hello\nworld";
String j = "{ \"name\": \"Asha\" }";
```

Output:

```
hello
world
-----
{ "name": "Asha" }
```

Works, but `\"name\"` is hard to read — imagine 20 lines of JSON like that. That pain is why
text blocks exist.

### Step 2 — the text block: just type the text (`Doubt6B.java`)

```java
String s = """
        hello
        world
        """;

String j = """
        { "name": "Asha" }
        """;
```

Output — identical to Step 1. Grammar rules:

- After the opening `"""` you must press Enter; text starts on the next line.
- The text ends where the closing `"""` is.

(Proof from `Concept6.java`: the escaped single-line SQL/JSON versions and the text block
versions compare `equals` → `true`. Same strings, readable source.)

### Step 3 — the spaces question (`Doubt6C.java`, spaces printed as dots)

In the code, `hello` has 8 spaces before it — code decoration, not text. Do they enter the
string? Printed with every space as a dot:

```
hello
world
```

No dots — Java removed them. Rule in simple words: **Java finds the spaces that ALL lines
share at the start, and cuts only those. Anything extra stays.** Pushing `world` 2 spaces
further right than `hello`:

```
hello
..world
```

The shared 8 were cut; the 2 extra are real text and stayed.

### Step 4 — the closing `"""` plays the game too

Moving the closing `"""` to the left edge of the file (column 0):

```java
String three = """
                hello
                world
""";
```

```
................hello
................world
```

All 16 spaces stayed: the closing-`"""` line counts as a line, it starts at column 0, so the
shared spaces are zero and nothing is cut. Steering wheel:

- Closing `"""` aligned with the text → no leading spaces in the string.
- Closing `"""` slid left → spaces kept up to where the quotes sit.

### Step 5 — the invisible ending (`Doubt6D.java`)

```java
String a = """
        hello
        world
        """;        // closing quotes on their OWN line

String b = """
        hello
        world""";   // closing quotes GLUED to the last word
```

Printed between `[` and `]`:

```
[hello
world
]
[hello
world]
```

- `a` ends with a line break (the `]` fell to the next line).
- `b` does not (the `]` sits right after `world`).

**The string ends where you put the closing `"""`.** Own line → trailing line break; glued →
none. Extra trick: a `\` at the very end of a line joins it with the next line (no line
break inserted).

This answers the task's two AI questions: indentation = shared leading spaces (including the
closing-quote line) are stripped, the rest kept; trailing newline = controlled by where the
closing `"""` sits (or suppressed with `\`).

### Common mistakes

- Content on the opening line: `"""hello` does not compile — Enter must follow `"""`.
- The invisible trailing line break failing string-equality tests.
- Mixing tabs and spaces in the block's left margin — the cut compares characters, not
  visual width.
- Using text blocks for one-line strings — ordinary quotes remain right for `"sold out"`.

### Your doubts

> **Doubt:** "I did not understand this at all. Break it down more, be precise, use simple
> language."

Resolved by rebuilding as Steps 1–5 above with spaces made visible (printed as dots) and the
ending made visible (printed between `[ ]`). The four-line version:

1. A text block is a string typed the natural way between `"""` pairs.
2. Java cuts the starting spaces **all lines share** — decoration, not text.
3. The closing `"""` line counts too — slide it left to keep spaces.
4. Closing `"""` on its own line → ends with a line break; glued to the last word → doesn't.

### Check yourself

1. *`aaa` at column 16, `bbb` at column 19, closing `"""` at column 16 — how many dots
   before `bbb` when spaces print as dots?*
   Three. The shared margin is 16 (set by `aaa` and the closing quotes); `bbb` had 19;
   19 − 16 = 3 kept.
2. *`String s = """⏎                hello"""; System.out.println("[" + s + "]");` — one line
   or two?*
   One line: `[hello]`. The closing `"""` is glued to `hello`, so no trailing line break
   exists.

### Summary

- Text block = `"""` + Enter + your text + `"""`; quotes and line breaks typed naturally.
- Shared leading spaces are stripped; extras per line are kept; the closing-quote line
  participates.
- Trailing line break exists iff the closing `"""` sits on its own line; `\` joins lines.
- Made for SQL, JSON, HTML, test fixtures — text with structure.

## Concept 7 — Choosing between enum, sealed interface, and open interface

Three tools for "one of several possibilities", each making a different **promise**:

| Tool | Its promise |
| --- | --- |
| `enum` | "These are all the **values**." |
| `sealed interface` | "These are all the **kinds**, each carrying its own data." |
| open `interface` | "Anyone may add more kinds, forever." |

Each tool *forbids* something the others allow, and the restriction is where the value lives:
an enum forbids per-instance data (simplicity), a sealed interface forbids outside
implementations (exhaustive switches), an open interface forbids nothing (extensibility).
Choosing deliberately is the senior habit; the wrong choice doesn't fail loudly — it makes
every future change awkward.

### The decision in two questions

1. **Do occurrences carry data?** No → **enum**. Yes → question 2.
2. **Is the set of kinds closed by the nature of the domain?** Yes → **sealed interface**
   (reward: exhaustive, `default`-free switches). No → **open interface** (reward: new
   implementations plug in without touching old code; price: no exhaustiveness, correctly).

### Step 1 — the proof (`Concept7.java`)

```java
enum HoldStatus { GRANTED, REJECTED, PARTIAL }

sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}
record Rejected(String reason) implements HoldResult {}
// ... Granted, PartiallyGranted ...

interface DiscountRule { double apply(double price); }
```

```java
HoldStatus s1 = HoldStatus.REJECTED;
HoldStatus s2 = HoldStatus.REJECTED;
Rejected r1 = new Rejected("sold out");
Rejected r2 = new Rejected("payment failed");
```

Output (Java 21):

```
same enum object? true
same Rejected object? false
r1 says: sold out
r2 says: payment failed
status = REJECTED ... but WHY? The enum cannot tell us.
```

- `true` — every mention of `HoldStatus.REJECTED` is literally one shared object (enum
  constants are singletons, F2.11). No place for *this request's* reason.
- `false` + two reasons — each `new Rejected(...)` is its own object with its own data:
  a fixed set of *types*, instances as individual as needed.
- Head test: if the next line of code asks "*why* rejected?" or "*which* seats?", an enum
  has no answer — you need the sealed family of records.
- Both enum and sealed give exhaustive `switch`. The open `DiscountRule` never can — the
  compiler cannot enumerate implementations that don't exist yet. That is the correct price
  for openness.

### Applied to this project

- Export compression `ZIP/GZIP/NONE` → enum (no per-occurrence data).
- `HoldResult` → sealed interface (data per occurrence; set closed by the domain).
- `DiscountRule`, notification channels → open interface (set must grow freely).

### Common mistakes

- **A mutable "data" field on an enum constant.** One shared object then holds
  request-specific state; two simultaneous scans/rejections overwrite each other's reason.
  If occurrences carry data, it was never an enum.
- **Sealing what wants to grow.** Every new implementation then edits `permits` and
  recompiles all switches — friction with no payoff.
- **Leaving a genuinely closed set open.** Compiles, works, and silently gives up every
  compile-time guarantee of Concepts 1–2.

### Check yourself

1. *(a) report compression ZIP/GZIP/NONE, (b) gate-scan result `Admitted(gate, time)` /
   `Refused(reason)`, (c) notification channels with partners adding more — which tool each?*
   (a) enum — no data per occurrence. (b) sealed interface — data per occurrence, closed
   set. (c) open interface — the set must grow without touching existing code.
2. *Enum with `private String reason; setReason(...)` — two gates refuse at the same moment
   with different reasons. What goes wrong?*
   `REFUSED` is one shared object (`same enum object? true`), so there is only ONE `reason`
   field in the entire program; the second scan overwrites the first. Per-occurrence data
   needs types: `record Refused(String reason)`.

### Summary

- Enum = all the values; sealed = all the kinds (with data); open = anyone may add more.
- Decide with two questions: data per occurrence? set closed?
- Enum and sealed both earn exhaustive switches; open correctly cannot.
- Full design idea recorded in `NOTES.md`.

## Task checkpoint — answers for revision

1. *What does `sealed` give you that an ordinary interface does not?*
   A compiler-known complete list of implementors. Gate-crashers are compile errors, and
   switches over the type can be proven exhaustive.
2. *Why is a `switch` with no `default` a good thing here?*
   `default` swallows future cases silently; without it, the compiler demands proof of full
   coverage, so a new permitted type breaks the build at every unhandled switch — with
   locations.
3. *What happened when a fourth permitted type was added?*
   `error: the switch expression does not cover all possible input values` at every
   incomplete switch. Fix was one new case. New cases became compile errors instead of
   production bugs — the whole point.
4. *What does a record pattern save you from writing?*
   The test-then-cast-then-extract dance: `case Granted(String id, var seats)` does type
   test, accessor calls, and variable binding in one checked step. No casts anywhere.
5. *When would you choose an enum over a sealed interface?*
   When the alternatives carry no per-occurrence data — they are pure values (ZIP/GZIP/NONE,
   card suits). The moment an occurrence needs its own data (a reason, seat ids), you need
   types: sealed + records.

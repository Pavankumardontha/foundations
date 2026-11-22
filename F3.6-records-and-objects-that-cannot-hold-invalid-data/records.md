# Task F3.6 — Records, and objects that cannot hold invalid data

## Concept 1 — What a record is, and what the compiler writes for you

A **record** is a class declared by listing only its data:

```java
record Money(long amountInPaise, String currency) {}
```

That one line is a complete, working class. Each item in the parentheses — `amountInPaise`,
`currency` — is a **component**. From the component list the compiler generates the whole
body of the class.

### Why it exists

"A class that just holds data" is the most common class in a backend, and writing it by hand
is long and easy to get wrong: two fields, a constructor, two getters, `equals`, `hashCode`,
`toString` — roughly 40 lines for a two-field class. Forget or botch `equals`/`hashCode` and
the bug surfaces far away (a `HashMap` lookup silently fails) — the F2.8 contract problem.

### What the compiler generates

From `record Money(long amountInPaise, String currency)` the compiler writes, invisibly:

1. A **private final field** per component.
2. A **constructor** taking all components in order: `new Money(450000, "INR")`.
3. An **accessor** per component, named exactly after it: `amountInPaise()`, `currency()` —
   no `get` prefix.
4. **`equals`** comparing component by component.
5. **`hashCode`** consistent with that `equals`.
6. **`toString`** printing the components readably.

### Step 1 — plain class vs record, same data

```java
// plain class: fields, constructor, accessors hand-written; no equals/toString
static class MoneyClass {
    private final long amountInPaise;
    private final String currency;

    MoneyClass(long amountInPaise, String currency) {
        this.amountInPaise = amountInPaise;
        this.currency = currency;
    }

    long amountInPaise() { return amountInPaise; }
    String currency()    { return currency; }
}

// record: this is the ENTIRE class
record Money(long amountInPaise, String currency) {}

public static void main(String[] args) {
    MoneyClass c1 = new MoneyClass(450000, "INR");
    MoneyClass c2 = new MoneyClass(450000, "INR");
    System.out.println("class printed:  " + c1);
    System.out.println("class equals:   " + c1.equals(c2));

    Money r1 = new Money(450000, "INR");
    Money r2 = new Money(450000, "INR");
    System.out.println("record printed: " + r1);
    System.out.println("record equals:  " + r1.equals(r2));
    System.out.println("record accessor: " + r1.amountInPaise());
}
```

Output (Java 21, `Concept1.java`):

```
class printed:  Concept1$MoneyClass@15db9742
class equals:   false
record printed: Money[amountInPaise=450000, currency=INR]
record equals:  true
record accessor: 450000
```

### Step 2 — walkthrough

| Line | Why it printed that |
| --- | --- |
| `Concept1$MoneyClass@15db9742` | No hand-written `toString`, so Java falls back to class name + hash. Useless in a log. |
| `class equals: false` | No hand-written `equals`, so Java compares *object identity* — "same object in memory?". Two separate objects, so `false`, even with identical data. |
| `Money[amountInPaise=450000, currency=INR]` | The record's generated `toString`. Readable. |
| `record equals: true` | The generated `equals` compares the **components**. Same data ⇒ equal — what you almost always want for data. |

### Proof the methods really exist — `javap`

`javap` lists what is inside a compiled class. On the record:

```
final class Concept1$Money extends java.lang.Record {
  Concept1$Money(long, java.lang.String);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public long amountInPaise();
  public java.lang.String currency();
}
```

Six members, zero written by hand. Note the class is `final` — records cannot be subclassed —
and it extends `java.lang.Record`. A record is a normal class underneath, a *shorthand with
guarantees*, not new machinery.

### Common mistakes

- **Looking for `getAmountInPaise()`.** Record accessors have the component's exact name:
  `amountInPaise()`. There is no `get` prefix.
- **Thinking a record is a different kind of thing than a class.** It compiles to a normal
  (final) class; the compiler just writes the boilerplate.

### Connection to the task

Subtask 1 done: declared `Money`, created one, printed it, compared two equal ones. Not
written by hand: constructor, two accessors, `equals`, `hashCode`, `toString` — exactly the
methods F2.8 showed are dangerous to get wrong.

### Check yourself

1. *Two separate `Money` record objects hold the same values. `r1.equals(r2)` is `true`, but
   for the plain class with the same data it was `false`. What is each one comparing?*
   The record's generated `equals` compares the **components** (`amountInPaise`, `currency`)
   value by value — same data means equal. The plain class never defined `equals`, so Java
   uses the default: **object identity** — "are these literally the same object in memory?"
   Two separate objects are never identical, so `false` even with identical data.
2. *For `record Money(long amountInPaise, String currency)`, what is the exact call to read
   the currency from a `Money` named `m`?*
   `m.currency()` — the accessor is named exactly after the component, with no `get` prefix.

### Summary

- `record Money(long amountInPaise, String currency) {}` is a complete class.
- The compiler generates: final fields, constructor, accessors (no `get` prefix),
  `equals`, `hashCode`, `toString`.
- Generated `equals` compares data, not identity — two records with the same components
  are equal.
- Records are `final` classes extending `java.lang.Record`; verify with `javap`.

## Concept 2 — Record components are final

Every component of a record becomes a **final field**: assigned exactly once, in the
constructor, and never again — not even by code inside the record itself. The compiler also
generates **no setters**, so there is not even a method that could try.

### Why it matters

Data that cannot change is data you never have to re-check. If `Money` could be modified
after creation, every method receiving one would have to worry: "has someone changed this
since it was validated? Is another thread changing it right now?" With final components, a
`Money` you saw a microsecond ago is identical now. Check the data once, at construction,
and it stays correct forever.

### Step 1 — trying to reassign a component

```java
record Money(long amountInPaise, String currency) {
    // Attempt 1: a setter INSIDE the record
    void setAmount(long newAmount) {
        this.amountInPaise = newAmount;
    }
}

// Attempt 2: from OUTSIDE, in main
price.amountInPaise = 0;
```

Real compiler output (Java 21, `Concept2Broken.java.txt`):

```
Concept2Broken.java:6: error: cannot assign a value to final variable amountInPaise
            this.amountInPaise = newAmount;
                ^
Concept2Broken.java:14: error: cannot assign a value to final variable amountInPaise
        price.amountInPaise = 0;
             ^
2 errors
```

### Step 2 — decoding the error

| Error part | Plain English |
| --- | --- |
| `cannot assign a value to final variable` | "This field is final; final fields get exactly one assignment, and the constructor already used it. Any second assignment is refused." |
| Fires even for the attempt *inside* the record | `final` is absolute. The record's own methods get no special permission to mutate. |
| Compile error, not runtime exception | The mutation cannot happen on any execution path — a program containing it never runs at all. |

The setter *declaration* is legal; only the assignment inside it is rejected. The protection
sits on the field and is airtight.

### Step 3 — how you "change" an amount: build a new record

```java
Money price = new Money(450000, "INR");
Money discounted = new Money(price.amountInPaise() - 50000, price.currency());
```

Output (Java 21, `Concept2.java`):

```
original:   Money[amountInPaise=450000, currency=INR]
discounted: Money[amountInPaise=400000, currency=INR]
original unchanged? true
```

Two separate objects. `price` is untouched — anyone still holding it sees what they always
saw. A record is like a printed receipt: you never edit a receipt, you issue a new one.

### Common mistakes

- **Adding a setter to a record.** Class habit. Records have no mutable state, full stop.
- **Thinking `final` means "constant forever in the program".** It means *this field of this
  object* never changes; you can create as many different `Money` objects as you like.
- **Fearing new objects are wasteful.** Small short-lived objects are extremely cheap on the
  JVM; correctness first.
- **Caveat for later:** `final` locks the *field*, not the object it points to. A `List`
  component's contents can still be changed — that is the "shallow immutability" limit,
  covered in its own concept.

### Check yourself

1. *Inside `record Money(long amountInPaise, String currency)`, a method containing
   `this.amountInPaise = newAmount;` fails to compile with `cannot assign a value to final
   variable amountInPaise`. Why doesn't the record's own code get to modify its own field?*
   Because the generated field is `final`, and `final` means "assigned exactly once, in the
   constructor". The rule is attached to the field itself, not to who is writing — code
   inside the record has no special mutation privilege. That is what makes the guarantee
   trustworthy: there is no path, internal or external, that can change a component.
2. *You have `Money m = new Money(1000, "INR")` and you need 2000 paise. What do you write,
   and what happens to `m`?*
   Build a new record: `Money doubled = new Money(2000, "INR");` (or
   `new Money(m.amountInPaise() * 2, m.currency())` to derive it from `m`). Nothing happens
   to `m` — it still holds 1000. Records are never edited; you create a new value.

### Summary

- Record components compile to `private final` fields: one assignment, in the constructor,
  then locked — compile error on any reassignment, even from inside the record.
- No setters are generated, and you cannot write a working one.
- To get different data, create a new record from the old one's accessors.
- `final` locks the field, not the object it points to (mutable components come later).

## Concept 3 — The compact constructor: validation at the door

A **compact constructor** is a constructor body with **no parameter list** — just the
record's name and braces:

```java
record Money(long amountInPaise, String currency) {
    Money {   // no (...) — that is what makes it "compact"
        // checks go here
    }
}
```

It runs on every `new Money(...)`, *before* the fields are filled in.

### Why it exists

Without it, the record happily accepts `new Money(-500, "BANANAS")`. The compact constructor
is the place for rules every instance must pass **to be born**. A statement true of every
instance that exists — "the amount is never negative" — is an **invariant**; this is where
invariants are enforced.

### The three mechanics

1. **You receive the constructor parameters, not fields.** Inside `Money { ... }`,
   `amountInPaise` and `currency` are the values the caller just passed. No field exists yet.
2. **If the checks pass, the compiler assigns the fields for you.** At the closing brace, the
   invisible `this.amountInPaise = amountInPaise; this.currency = currency;` runs
   automatically.
3. **If you throw, the object is never created.** A `new` expression either completes and
   hands you an object, or throws and hands you *nothing* — no half-built object exists.
   The exception for "the caller passed garbage arguments" is `IllegalArgumentException`.

Result: every `Money` in the program *has* passed the checks — existing is the proof.

### Step 1 — the validating record

```java
record Money(long amountInPaise, String currency) {
    Money {
        if (amountInPaise < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
        }
    }
}
```

Main creates one valid `Money`, two invalid ones inside try/catch (only so one run shows both
rejections), then a negative one with no safety net.

Output (Java 21, `Concept3.java`):

```
valid:    Money[amountInPaise=450000, currency=INR]
rejected: amount must be >= 0, was -1
rejected: ISO-4217 currency code required, was RUPEES
now building a negative Money with no try/catch...
Exception in thread "main" java.lang.IllegalArgumentException: amount must be >= 0, was -500
	at Concept3$Money.<init>(Concept3.java:6)
	at Concept3.main(Concept3.java:33)
exit code: 1
```

### Step 2 — decoding the crash

| Stack trace part | Plain English |
| --- | --- |
| `Exception in thread "main"` | Nothing caught it; the program died. For an illegal argument during development that is *good* — find out loudly. |
| `IllegalArgumentException: amount must be >= 0, was -500` | Your message, with the offending value included. |
| `at Concept3$Money.<init>(Concept3.java:6)` | `<init>` is the JVM's name for a constructor. The throw happened at line 6 — the check. |
| `at Concept3.main(Concept3.java:33)` | The guilty caller: line 33, the `new Money(-500, "INR")`. |
| `this line never runs` never printed | The `new` threw, so nothing was assigned. No negative `Money` ever existed, even momentarily. |

### Why this matters in real systems

It flips where bugs are caught. Without construction-time validation, a negative amount
sneaks in at point A and explodes at point Z (a refund calculation, a database row), and you
work backwards for hours. With it, the program stops at the exact line that produced the bad
value, with a message saying what was wrong. A request DTO validated this way cannot enter
the system in an invalid state.

### Common mistakes

- **Writing `Money() { ... }` with empty parentheses.** That is a *zero-argument*
  constructor — a different thing. The compact constructor has no parentheses at all.
- **Assigning fields yourself: `this.amountInPaise = amountInPaise;`** — compile error in a
  compact constructor. Assignment is the compiler's job, at the closing brace.
- **Validating in the accessor instead.** Then the bad object already exists and has been
  travelling through the program. Validate at the door, not in the living room.
- **Wrapping every `new Money(...)` in try/catch.** `IllegalArgumentException` means a
  programming bug in the caller; the fix is correcting the code, not catching.

### Check yourself

1. *In a compact constructor body like `Money { if (amountInPaise < 0) ... }`, does
   `amountInPaise` refer to a field of the object, or to something else? When do the fields
   actually get their values?*
   It refers to the **constructor parameter** — the raw value the caller just passed in. The
   fields do not exist yet at that point. They are assigned automatically by the compiler at
   the closing brace of the compact constructor, and only if no exception was thrown.
2. *A teammate suggests skipping the constructor check and instead checking
   `if (m.amountInPaise() < 0)` in every method that uses a `Money`. Both reject negative
   amounts. What does the constructor approach guarantee that scattered checks never can?*
   That no invalid `Money` **exists at all**. With scattered checks, the invalid object is
   alive and travelling; every method must remember to check, and one forgotten check lets
   the bug through — and the failure surfaces far from where the bad value was created. With
   the constructor check there is exactly one gate, it cannot be forgotten, and a stack trace
   points at the line that tried to create the bad value.

### Summary

- Compact constructor: record name + braces, no parameter list; runs on every `new`.
- Inside it you validate the *parameters*; fields are auto-assigned at the closing brace.
- Throw `IllegalArgumentException` on bad input → the object is never created.
- Therefore an invariant checked here is true of **every instance in the program** —
  an invalid `Money` cannot exist anywhere.

## Concept 4 — Adding behaviour: records are not data bags

A record can contain ordinary **methods**, just like a class. The header defines the data;
below it lives behaviour that belongs to that data.

```java
record Money(long amountInPaise, String currency) {
    Money { /* validation */ }

    Money plus(Money other) { ... }   // behaviour, inside the record
}
```

### Why it exists

Where should the rule "you cannot add rupees to dollars" live? If `Money` is a dumb
container, the rule gets copied into every place that adds money — booking, refund,
reporting — and the fourth place forgets it. A method inside the record means data and the
rules about the data travel together; everyone adds money the one correct way.

### The mechanics

1. **Methods read components directly** — inside the record, `this.amountInPaise` is the
   field; no accessor needed.
2. **Methods cannot mutate** — fields are final, so behaviour like `plus` must return its
   result as a **new record**, never by updating `this`.
3. **The result passes the compact constructor too** — `plus` ends in `new Money(...)`, so
   even computed results are validated at the door. No back entrance.

One restriction vs a class: no extra *instance fields* beyond the components. Methods,
static fields, static methods are all fine.

### Step 1 — `plus` with a currency guard

```java
Money plus(Money other) {
    if (!this.currency.equals(other.currency)) {
        throw new IllegalArgumentException(
                "currency mismatch: cannot add " + other.currency + " to " + this.currency);
    }
    return new Money(this.amountInPaise + other.amountInPaise, this.currency);
}
```

Output (Java 21, `Concept4.java`):

```
ticket:  Money[amountInPaise=450000, currency=INR]
fee:     Money[amountInPaise=25000, currency=INR]
total:   Money[amountInPaise=475000, currency=INR]
ticket unchanged? true
now adding USD to INR...
Exception in thread "main" java.lang.IllegalArgumentException: currency mismatch: cannot add USD to INR
	at Concept4$Money.plus(Concept4.java:16)
	at Concept4.main(Concept4.java:36)
exit code: 1
```

### Step 2 — walkthrough

| Moment | What happens |
| --- | --- |
| `ticket.plus(fee)` | Inside `plus`, `this` is `ticket`, `other` is `fee`. `"INR".equals("INR")` passes; returns `new Money(475000, "INR")` — a third, brand-new object. |
| `ticket unchanged? true` | `plus` cannot touch `ticket` — final fields. Like `5 + 3` producing `8` without modifying the `5`. |
| `ticket.plus(usd)` | `"USD".equals("INR")` is false → throws before any arithmetic. A nonsense total never existed. |
| Stack trace | Problem in `plus` (line 16), called from `main` (line 36) — reads like a sentence. |

### Common mistakes

- **Comparing currencies with `==`.** That compares whether two references point at the same
  String *object in memory*, not whether the text matches. It accidentally works for
  identical literals (Java reuses them), then fails for runtime-built strings — the worst
  kind of bug. For text content, always `.equals()`.
- **Trying `this.amountInPaise += ...`** — compile error, final field. Return a new `Money`,
  and the caller must *use* the return value: `total = a.plus(b)`. A bare `a.plus(b);`
  computes a result and drops it.
- **Putting the logic in a far-away helper (`MoneyUtils.add`).** Works, but separates the
  rule from the data, and nothing stops unchecked additions elsewhere. The record carrying
  its own rules is the stronger design.

### Check yourself

1. *`total = ticket.plus(fee)` produced a third object and left `ticket` and `fee`
   untouched. Why couldn't `plus` update `ticket`'s amount instead, and what would be lost
   if it could?*
   The generated fields are final — any assignment to them outside construction is a compile
   error, even inside the record's own methods. If mutation were possible, every other
   holder of `ticket` would silently see the amount change under them, and "a `Money` you
   validated a moment ago is still that value now" would stop being true.
2. *Suppose a future bug made `plus` compute a negative sum. What happens at that moment,
   and which already-written code catches it?*
   `plus` builds its result with `new Money(...)`, which runs the compact constructor — the
   check `if (amountInPaise < 0) throw ...` fires immediately with
   `IllegalArgumentException`. The invalid total never comes into existence; the validation
   written once at the record's door also guards every computed result.

### Summary

- Records can hold methods; the data and the rules about the data travel together.
- Behaviour never mutates — it returns new records, and those pass validation too.
- `plus` rejects mismatched currencies with a clear exception at the exact call site.
- Compare string contents with `.equals()`, never `==`.

## Concept 5 — Static factories: naming the way objects are created

A **static factory** is a static method whose job is to create and return an instance, with
a *name* describing what it creates:

```java
record Money(long amountInPaise, String currency) {
    static Money inr(long paise) { return new Money(paise, "INR"); }
}
```

Callers write `Money.inr(450000)` instead of `new Money(450000, "INR")`. `static` means the
method belongs to the record itself, called on the type — "Money: give me an INR amount."

### Why it exists

A constructor has two limits the factory fixes:

1. **Its name is always the class name.** `new Money(450000, "INR")` cannot say which kind of
   creation this is. A factory can: `inr(...)`, `zero()`, `fromRequest(...)`.
2. **Every caller supplies every argument.** Fifty call sites each type `"INR"` by hand, and
   one day someone types `"IRN"` — three letters, so the length-3 validation accepts it. The
   factory writes `"INR"` exactly once; that typo category becomes impossible.

### The critical property

The factory **calls the real constructor** and returns the result — a convenience on top of
the gate, never a way around it. The compact constructor runs on every creation, whichever
door the caller used.

### Step 1 — same value, two doors

```java
Money viaConstructor = new Money(450000, "INR");
Money viaFactory     = Money.inr(450000);
// then: Money.inr(-100)  → ?
```

Output (Java 21, `Concept5.java`):

```
constructor: Money[amountInPaise=450000, currency=INR]
factory:     Money[amountInPaise=450000, currency=INR]
equal?       true
factory rejected: amount must be >= 0, was -100
```

### Step 2 — walkthrough

| Line | Meaning |
| --- | --- |
| `equal? true` | The factory produced a normal `Money`; generated `equals` compares components, which match. Only the creation's readability differs. |
| `factory rejected: ...` | `Money.inr(-100)` called `new Money(-100, "INR")` → compact constructor → throw. Validation written once guards both doors. |

Call-site comparison:

```java
Money price = new Money(450000, "INR");   // "new Money of... 450000 what? ah, INR"
Money price = Money.inr(450000);          // "450000 paise, in rupees" — instant
```

### Everyday JDK examples

`List.of("a", "b")`, `Optional.empty()`, `String.valueOf(42)` — all static factories.

### Common mistakes

- **Thinking the factory replaces validation.** It delegates to the validated constructor. A
  factory that builds state bypassing checks is a hole in the fence.
- **Forgetting `static`.** Then you would need a `Money` already in hand to create a `Money`;
  `Money.inr(100)` would not compile.
- **Factories everywhere.** Add one when a call-site pattern repeats (always INR) or needs a
  name (zero, defaults). A record used twice doesn't need five factories.

### Check yourself

1. *`Money.inr(-100)` was rejected with exactly the same message as
   `new Money(-100, "INR")`. What sequence of calls happens when `Money.inr(-100)` runs?*
   `Money.inr(-100)` executes `return new Money(-100, "INR");` → the `new` runs the compact
   constructor → the check `amountInPaise < 0` fires → `IllegalArgumentException` propagates
   out through the factory to the caller. The factory has no creation power of its own; it
   goes through the same gate as everyone else.
2. *Fifty call sites write `new Money(amount, "INR")` and one typos `"IRN"`. The compact
   constructor checks `currency.length() != 3`. Is the typo caught? How does the factory
   change the answer?*
   Not caught — `"IRN"` is three characters, so it passes the length check and a wrong-
   currency `Money` is born. With `Money.inr(amount)`, the literal `"INR"` exists in exactly
   one line of the program, inside the factory; call sites never type it, so the typo has
   nowhere to happen.

### Summary

- A static factory is a named creation method: `Money.inr(450000)` — readable call sites,
  constants written once.
- It must delegate to the real constructor, so compact-constructor validation still runs.
- The JDK uses this pattern everywhere (`List.of`, `Optional.empty`).

## Concept 6 — Normalise in the compact constructor, not just validate

**Normalising** = converting input to one canonical form before storing it — here,
uppercasing the currency so `"inr"`, `"Inr"`, `"INR"` are all stored as `"INR"`:

```java
Money {
    // ...validation checks...
    currency = currency.toUpperCase();   // normalise before it is stored
}
```

### Why it exists

The generated `equals` compares the **stored components exactly**. `"INR"` and `"inr"` are
different strings, so without normalisation two objects any human calls "the same thousand
paise" are unequal — and equality is what `HashSet`, `HashMap` and `contains` are built on
(the F2.8 contract). Same money, different casing ⇒ duplicate set entries, failed lookups.

### The mechanic that makes it legal

Inside the compact constructor you hold the **constructor parameters** (fields don't exist
yet). At the closing brace the compiler stores *whatever the parameters hold at that moment*
into the final fields. So `currency = currency.toUpperCase();` reassigns the **parameter** —
an ordinary local variable. No final field is touched; the field is born already clean.
Check the guest, tidy them up, then let them in — tidy for life.

### Step 1 — side by side

`MoneyRaw` validates only; `Money` validates then uppercases.

Output (Java 21, `Concept6.java`):

```
raw upper:  MoneyRaw[amountInPaise=1000, currency=INR]
raw lower:  MoneyRaw[amountInPaise=1000, currency=inr]
raw equal?  false
raw set size: 2  (same money counted twice!)
factory:    Money[amountInPaise=1000, currency=INR]
lowercase:  Money[amountInPaise=1000, currency=INR]
equal now?  true
set size:   1
```

### Step 2 — walkthrough

| Line | Meaning |
| --- | --- |
| `raw equal? false` | `MoneyRaw` stored `"inr"` as-is; `"INR".equals("inr")` is false. |
| `raw set size: 2` | The damage: a `HashSet` counts the "same" money twice. Imagine deduplicating refunds with it. |
| `lowercase: Money[... currency=INR]` | We *passed* `"inr"` but the object *prints* `INR` — the parameter was uppercased before the field was assigned. The lowercase form was never stored. |
| `equal now? true`, `set size: 1` | Every instance is canonical, so equality and hash-based collections behave as a human expects. |

Ordering note: if normalisation changes what validation sees (e.g. trimming whitespace),
normalise first and validate the cleaned value, so `" INR "` doesn't fail a check it morally
passes. For pure case changes the order doesn't matter.

### Common mistakes

- **Thinking `currency = currency.toUpperCase()` mutates a final field.** It reassigns the
  constructor *parameter*; the field doesn't exist yet. (Classic-constructor equivalent:
  `this.currency = currency.toUpperCase();`.)
- **Normalising at read time instead** (`currency().toUpperCase()` at call sites). The stored
  data stays messy, `equals` stays broken (it reads the raw field), and one forgotten call
  site reintroduces the bug.
- **Forgetting normalisation discards the original form.** After uppercasing, nothing can
  retrieve `"inr"`. Deliberate and right for currency codes; wrong tool if the original
  casing matters.

### Check yourself

1. *Fields of a record are final, yet `currency = currency.toUpperCase();` compiles inside
   the compact constructor. What is being assigned, and when does the final field get its
   value?*
   The assignment targets the **constructor parameter** — a mutable local variable holding
   the caller's raw value. The final field receives its value automatically at the compact
   constructor's closing brace, taking whatever the parameter holds then. Parameter
   reassignment is therefore how you normalise; the final field is still assigned exactly
   once.
2. *We passed `"inr"` but `toString` printed `currency=INR`, and the lowercase original is
   unrecoverable. Bug or design? Why must this happen at construction, not display time?*
   Design. Hash-based collections compute `hashCode` and `equals` from the **stored**
   components at insert/lookup time — they cannot apply your display-time tidying. If
   `"inr"` were stored, `Money.inr(1000)` and the lowercase version would land in different
   hash buckets and compare unequal, breaking sets and map lookups. Canonicalising at the
   door makes every stored instance already-equal-compatible everywhere.

### Summary

- Validation rejects bad input; normalisation standardises acceptable-but-messy input.
- In a compact constructor, reassign the *parameter* (`currency = currency.toUpperCase();`);
  the compiler stores the cleaned value into the final field at the closing brace.
- Normalise at construction so generated `equals`/`hashCode` see canonical data — proven by
  `equal now? true` and `set size: 1`.
- Normalise before validating whenever normalisation affects what validation sees.

## Concept 7 — The limit: a record is only *shallowly* immutable

**Shallow immutability:** the record's *fields* can never be reassigned, but if a field
points at a mutable object — an `ArrayList` — the contents of that object can still change.
The record locks its references, not the world behind them.

### Why the limit exists

A reference-type field stores the *address* of an object living elsewhere, not the object.
`final` guarantees the address never changes; it says nothing about what happens at that
address. Anyone else holding the same address can renovate the building — the record's
signpost still points at the same plot, no rule broken, everything different.

### Step 1 — watching a "immutable" record change

```java
record Seat(String id) {}
record BookingLeaky(String eventId, List<Seat> seats) {}

List<Seat> chosen = new ArrayList<>(List.of(new Seat("A1"), new Seat("A2")));
BookingLeaky leaky = new BookingLeaky("EVT-1", chosen);

chosen.add(new Seat("Z99"));             // attack 1: caller kept the reference
leaky.seats().add(new Seat("Z100"));     // attack 2: accessor returns the real list
```

Output (Java 21, `Concept7.java`):

```
leaky before: BookingLeaky[eventId=EVT-1, seats=[Seat[id=A1], Seat[id=A2]]]
leaky after:  BookingLeaky[eventId=EVT-1, seats=[Seat[id=A1], Seat[id=A2], Seat[id=Z99], Seat[id=Z100]]]
```

No setter exists, every field is final, and the booking still grew two seats.

### Step 2 — why that is not a contradiction

| Moment | What actually happened |
| --- | --- |
| construction | The record stored the **address** of the caller's `ArrayList`. One list, two names: `chosen` and the field. |
| `chosen.add(...)` | The caller edited the list through their name. The field still holds the same address — nothing final was violated. |
| `leaky.seats().add(...)` | The generated accessor returns the field's value: the address of the real internal list. A live door into the record. |
| the promise | Kept to the letter (no field changed), broken in spirit. That is "shallow". |

### Step 3 — the fix (defensive copy, from F3.8)

```java
record BookingSafe(String eventId, List<Seat> seats) {
    BookingSafe {
        seats = List.copyOf(seats);   // snapshot; the copy is also unmodifiable
    }
}
```

Same parameter-reassignment move as normalising the currency, but replacing the caller's
list with a private snapshot. `List.copyOf` **copies** (their later edits hit their list,
not ours) and **locks** (the copy rejects modification). Output for the same two attacks:

```
safe before:  BookingSafe[eventId=EVT-1, seats=[Seat[id=A1], Seat[id=A2]]]
safe after:   BookingSafe[eventId=EVT-1, seats=[Seat[id=A1], Seat[id=A2]]]
accessor attack: UnsupportedOperationException — list is unmodifiable
```

Attack 1 bounced off; attack 2 crashed loudly instead of silently corrupting. The record is
now *deeply* immutable.

### Why it matters

Records with collection components are everywhere (order → line items, event → seats,
response DTO → results). Without the copy, `equals`, `hashCode` and validated state can all
be invalidated after construction. A mutated component silently changes `hashCode` — an
object stored in a `HashSet` before the mutation becomes unfindable after it.

### Common mistakes

- **"It's a record, so it's immutable."** True only when every component is itself immutable
  (primitives, `String`, other well-built records). One `List` or `StringBuilder` component
  and you are shallow.
- **Copying in the accessor instead of the constructor.** Blocks attack 2 only; the caller's
  kept reference still edits your internals. Copy on the way **in** — then the way out is
  automatically safe, because what you hand out is the unmodifiable copy.
- **Thinking `List.copyOf` deep-copies elements.** It copies the list, not the seats inside.
  Enough here because `Seat` is itself an immutable record — value objects compose.

### Check yourself

1. *`BookingLeaky`'s field is final and no setter exists, yet `toString` showed four seats
   where there were two. What does `final` protect, and what does it not?*
   `final` protects the **reference** — the field can never be re-pointed at a different
   list. It does not protect the **object behind the reference** — the list's contents.
   The mutation changed the list's contents through other names for the same address; the
   field never changed, so `final` had nothing to object to.
2. *Why must the copy happen at construction rather than in the `seats()` accessor?*
   An accessor copy protects only readers after the fact — the caller who supplied the list
   still holds a live reference to the record's actual internal list (attack 1) and can
   mutate it anytime. Copying at construction cuts that link at the only moment it can be
   cut: before the external reference and the record ever share an object. The accessor then
   returns the private unmodifiable copy for free.

### Summary

- Record immutability is **shallow**: final locks each field's reference, not the mutable
  object it may point to.
- Mutable components create two attack paths: the caller's kept reference and the accessor's
  returned reference.
- Fix in the compact constructor: `seats = List.copyOf(seats);` — copy + lock on the way in.
- A record of immutable components (primitives, `String`, other records) is deeply immutable
  with no extra work.

## Concept 8 — When a record is the wrong choice: values vs entities

Two kinds of objects:

- A **value** is defined entirely by its data. ₹4,500 *is* "4500, INR" — there is no "which
  ₹4,500?" Equal data ⇒ interchangeable; a value never changes, you make a new one.
- An **entity** is defined by its **identity**, which survives changes to its data. An event
  gets renamed, rescheduled, cancelled — and remains *the same event* throughout.

Records are built for values. Entities should be normal classes.

### Why the distinction decides record vs class

Everything the compiler generates assumes "the data is the whole truth": `equals` compares
**all** components, and components are frozen forever. For a value both assumptions are
exactly right. For something with a lifecycle both are exactly wrong — equality should
follow the identity, and the state must be able to change.

### Step 1 — an event modelled both ways

```java
record EventRecord(String id, String title) {}

static class EventEntity {
    private final String id;
    private String title;                 // mutable: events get renamed
    void rename(String newTitle) { this.title = newTitle; }
    // equals and hashCode compare id ONLY
}
```

Each goes into a `HashSet`, then gets renamed.

Output (Java 21, `Concept8.java`):

```
record: same event before/after rename? false
record: set still recognises the event? false
entity: still found after rename?      true
entity: equal to a fresh load of EVT-1? true
```

### Step 2 — walkthrough

| Observation | Why |
| --- | --- |
| record `false` / set lost it | A record cannot change, so "rename" = new object. Generated `equals` compares all components; titles differ ⇒ different "event". The published-events set no longer recognises EVT-1. Working code, wrong answers for this domain. |
| entity `true` / still found | Title mutated in place; `equals`/`hashCode` anchored to `id`. A fresh load of EVT-1 registers as the same event. Identity survived the data change — the definition of an entity. |

Neither behaviour is a bug: they are two meanings of "same". Choosing record vs class is
choosing which meaning the object needs.

### Record or class in a Spring backend — three of each

| Use a **record** | Why |
| --- | --- |
| `Money(long amountInPaise, String currency)` | Pure value: data is the identity, never changes, compared by contents. |
| `CreateEventRequest` / `EventResponse` DTOs | Snapshots crossing the HTTP boundary — born from JSON, validated once in the compact constructor, never edited. |
| `DateRange(LocalDate from, LocalDate to)` | Small composite value with an invariant (`from` not after `to`) enforced at the door; equal ranges are interchangeable. |

| Use a **class** | Why |
| --- | --- |
| `Event` JPA entity | One identity through many states (published, renamed, cancelled). Also practical: JPA needs a no-arg constructor and mutable fields; records provide neither. |
| `User` account | Email and password change; the account remains the same account. Equality by database id, never by data. |
| `BookingService` / controllers | Not data at all — behaviour with injected dependencies. "Are two services equal?" is a question nobody should ask. |

**Rule of thumb:** if you would happily swap the object for another with identical data, it
is a record. If "which one" matters independently of the data, it is a class.

### Common mistakes

- **Choosing a record because it is shorter.** If the thing is renamed, cancelled, or
  tracked over time, all-components `equals` is a landmine — the vanishing `HashSet` entry
  above.
- **Writing `Money` as a mutable class with setters.** Reopens every hole this task closed:
  unvalidated mutation, broken hash keys, defensive re-checking everywhere.
- **Forcing a record to act like an entity** (hand-writing `equals`/`hashCode` to ignore
  most components). When the record fights you, that is the signal to write a class.

### Check yourself

1. *The `HashSet` stopped recognising the renamed event as a record but kept recognising it
   as an id-based entity class. What was each `equals` comparing, and why did it matter?*
   The record's generated `equals` compared **id and title**; renaming produced a new object
   whose title differed, so it was "not equal" to the stored one, and `HashSet` (which looks
   up by `hashCode`/`equals`) could not find it. The entity's hand-written `equals` compared
   **id only**, so the rename changed nothing the set cared about — EVT-1 stayed EVT-1.
2. *Record or class? (a) `SeatNumber("A", 12)`; (b) a `Booking` moving PENDING → CONFIRMED →
   CANCELLED; (c) a `RefundAmount` attached to a cancellation.*
   (a) **Record** — a pure value; seat A-12 is seat A-12, interchangeable with any equal one.
   (b) **Class** — it has a lifecycle; the same booking must change state while staying the
   same booking.
   (c) **Record** — a money-like value; it is its amount and currency, and must not change
   after being computed.

### Summary

- Value = defined by data, immutable, interchangeable ⇒ record.
- Entity = defined by identity that survives data changes ⇒ class with id-based
  `equals`/`hashCode`.
- Generated record `equals` compares all components — wrong for anything with a lifecycle.
- In Spring: records for value objects and DTOs; classes for JPA entities, accounts, and
  services.

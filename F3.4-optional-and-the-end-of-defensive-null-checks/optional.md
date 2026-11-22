# Task F3.4 — `Optional` and the end of defensive null checks

## Concept 1 — The problem: a return type that lies

Every method advertises what it gives back. `static Event findById(String id)` is a promise:
*"call me, you get an `Event`."* But when no event has that id, the old habit was to return
`null` — and the signature does not change. It still says `Event`. The promise is now a lie,
and the compiler cannot warn anyone.

### Step 1 — watch the lie explode

```java
record Event(String name) {}

// Signature says "I return an Event" — but for unknown ids it returns null.
static Event findById(String id) {
    if (id.equals("E1")) {
        return new Event("Coldplay Live");
    }
    return null; // "not found" — invisible to the caller
}

public static void main(String[] args) {
    Event found = findById("E1");
    System.out.println("Found: " + found.name());   // works — bug stays hidden

    Event missing = findById("E999");
    System.out.println("Missing: " + missing.name()); // boom
}
```

Output:

```
Found: Coldplay Live
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "Concept1$Event.name()" because "<local2>" is null
	at Concept1.main(Concept1.java:25)
```

### Decoding the error

| Part of the message | Plain English |
| --- | --- |
| `NullPointerException` | You asked a nothing to do something. |
| `Cannot invoke "Concept1$Event.name()"` | The code tried to call `.name()` on an `Event`. |
| `because "<local2>" is null` | But the variable (`missing` in our code) held `null`, not an `Event`. |
| `at Concept1.main(Concept1.java:25)` | It blew up on line 25 — **where the null was used, not where it was created** (line 17). In real code those can be thousands of lines apart. |

Two things make this nasty:

1. **The happy path works.** `findById("E1")` runs fine, so tests pass and the bug ships.
   It only explodes when a missing id shows up — usually in production.
2. **The crash site is far from the cause.** The stack trace points at the caller, but the
   real culprit is the method that quietly returned `null`.

### Step 2 — the old cure: defensive null checks

```java
Event e = findById("E999");
if (e != null) {
    System.out.println(e.name());
} else {
    System.out.println("unknown");
}
```

Output:

```
unknown
```

No crash — but the cure has problems of its own:

- **Every caller must remember, every time.** Nothing enforces the check. One forgetful
  caller out of fifty and the NPE is back.
- **The knowledge lives in people's heads**, not in the code. You only learn that
  `findById` can return null by reading its body or by getting burned.
- **The checks multiply.** If `Event` has a `venue()` that might also be null, you end up
  with `if (e != null && e.venue() != null && ...)` nested through the whole codebase.

### Summary

- Returning `null` from a method whose signature promises a real object is a lie the
  compiler cannot catch.
- The NPE appears where the null is **used**, far from where it was **created**.
- Defensive null checks work but rely on every caller's discipline, forever.
- The real fix is to move the warning **into the return type**, so the compiler reminds the
  caller — that is what `Optional` does.

## Concept 2 — What `Optional<T>` actually is

`Optional<T>` is a tiny class in `java.util`: **a box that holds either exactly one value or
nothing**. `Optional<Event>` means "a box that may contain one `Event`, or may be empty."
The `<T>` is ordinary generics — one `Optional` class works for any content type, and the
compiler tracks what is inside.

### Step 1 — the honest signature

```java
import java.util.Optional;

record Event(String name) {}

// "I return a box. It may contain an Event, or it may be empty."
static Optional<Event> findById(String id) {
    if (id.equals("E1")) {
        return Optional.of(new Event("Coldplay Live")); // box with a value
    }
    return Optional.empty();                            // box with nothing
}

public static void main(String[] args) {
    Optional<Event> found = findById("E1");
    Optional<Event> missing = findById("E999");

    System.out.println("found   = " + found);
    System.out.println("missing = " + missing);
}
```

Output:

```
found   = Optional[Event[name=Coldplay Live]]
missing = Optional.empty
```

No crash. `missing` is not `null` — it is a real object that happens to contain nothing.
You can print it and pass it around safely. "Not found" went from an invisible landmine to
an ordinary value you can see.

### Step 2 — the payoff: the compiler nags the careless caller

With `null`, the careless caller's `missing.name()` compiled fine and exploded at runtime.
The same carelessness with `Optional`:

```java
Optional<Event> e = findById("E999");
System.out.println(e.name()); // trying to use the box as if it were the Event
```

Output (compile time!):

```
Concept2Broken.java:19: error: cannot find symbol
        System.out.println(e.name()); // compile error: Optional has no name()
                            ^
  symbol:   method name()
  location: variable e of type Optional<Event>
1 error
```

| Part | Plain English |
| --- | --- |
| `cannot find symbol: method name()` | You called `.name()`, but the thing you called it on has no such method. |
| `location: variable e of type Optional<Event>` | Because `e` is the **box**, not the `Event`. Boxes have no `name()`. |

### Summary

- `Optional<T>` is a box holding exactly one value or nothing; absence becomes an ordinary,
  visible value instead of a hidden `null`.
- An empty `Optional` is a real object — printing and passing it around are safe.
- Forgetting to handle "might be empty" now fails **at compile time** on your machine,
  instead of at runtime in production.
- `Optional` does not prevent absence — it makes absence **impossible to ignore**.

## Concept 3 — Three ways to create an `Optional`

Each factory method is a different **statement of certainty** about the value:

| Method | What you are declaring |
| --- | --- |
| `Optional.of(value)` | "I am **certain** this value exists. If I am wrong, crash right here." |
| `Optional.empty()` | "I am **certain** there is nothing." |
| `Optional.ofNullable(value)` | "This **might** be null — box it either way and sort it out." |

### Step 1 — all four cases

```java
// 1. of — certain there's a value
Optional<Event> a = Optional.of(new Event("Coldplay Live"));

// 2. empty — certain there's nothing
Optional<Event> b = Optional.empty();

// 3. ofNullable with a null — e.g. a legacy API that returns null
Event fromOldCode = null;
Optional<Event> c = Optional.ofNullable(fromOldCode);

// 4. ofNullable with a real value
Optional<Event> d = Optional.ofNullable(new Event("Ed Sheeran Tour"));
```

Output:

```
of          : Optional[Event[name=Coldplay Live]]
empty       : Optional.empty
ofNullable null  : Optional.empty
ofNullable value : Optional[Event[name=Ed Sheeran Tour]]
```

`ofNullable` is the bridge between the old null world and the Optional world: null in →
empty box, value in → full box. Use it when wrapping anything that might hand you null
(legacy libraries, `Map.get`, ...).

### Step 2 — breaking it on purpose: `Optional.of(null)`

```java
Event nothing = null;
Optional<Event> boom = Optional.of(nothing); // throws immediately
```

Output:

```
Exception in thread "main" java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java:233)
	at java.base/java.util.Optional.of(Optional.java:113)
	at Concept3.main(Concept3.java:31)
```

| Line | Plain English |
| --- | --- |
| `at java.util.Objects.requireNonNull` | `of` internally runs a "must not be null" check, and it failed. |
| `at java.util.Optional.of` | The crash happened *inside* `of` — the box was never created. |
| `at Concept3.main(Concept3.java:31)` | And here is **your** line that passed the null. |

### Why `of` throwing is deliberate

`Optional.of` is an assertion: "this value definitely exists." If that is false, your
program has a bug *right there*, and crashing immediately at the source is the best outcome —
the stack trace points at the culprit (line 31, where the null appeared), not at some
distant victim, unlike the Concept 1 NPE.

Rule of thumb:

- You **just constructed** the value or know it cannot be null → `Optional.of`.
- The value comes from **anywhere that might produce null** → `Optional.ofNullable`.
- You want to say "not found" / "nothing" → `Optional.empty()`.

Do **not** use `ofNullable` everywhere "to be safe" — that is vague, not safe. If a value
must never be null but a bug makes it null, `ofNullable` silently swallows the bug into an
empty box, and you hunt a mystery "why is this empty?" far from the cause. Say what you
mean: `of` when certain, `ofNullable` when genuinely unsure.

### Summary

- `of` = certain value (throws NPE at the source if you are wrong — deliberately).
- `empty` = certain nothing.
- `ofNullable` = maybe null; the bridge from null-returning code into the Optional world.
- Choosing between `of` and `ofNullable` is documentation: it records how certain you were.

## Concept 4 — Getting the value out of the box

The box is nice, but at some point you need an actual `Event` in your hand. This concept is
taken in tiny steps, one unwrapping tool at a time.

Throughout, this is the lookup being used:

```java
static Optional<Event> findById(String id) {
    if (id.equals("E1")) {
        return Optional.of(new Event("Coldplay Live"));
    }
    return Optional.empty();
}
```

- `findById("E1")` → a **full** box containing `Event("Coldplay Live")`
- `findById("E999")` → an **empty** box

### Step 1 — `orElse`: "the value, or else this fallback"

Read `orElse` as: *"give me the value inside the box. Or else, give me this one instead."*

Empty-box case:

```java
Event e = findById("E999").orElse(new Event("Unknown"));
```

1. `findById("E999")` returns an **empty** box.
2. `orElse` asks the box: "do you have a value?"
3. No → `orElse` hands back the fallback, `Event("Unknown")`.
4. `e` is now a real `Event` — not a box, not a null.

```
Unknown
```

Full-box case — same line, different id:

```java
Event e = findById("E1").orElse(new Event("Unknown"));
```

1. `findById("E1")` returns a **full** box.
2. "Do you have a value?" — yes → the box's own value comes out; the fallback is ignored.

```
Coldplay Live
```

What `orElse` guarantees:

- Box full → you get the box's value.
- Box empty → you get your fallback.
- Either way you hold a real value. No null escapes, no crash is possible.

This one line replaces the whole `if (e != null) { ... } else { ... }` block from
Concept 1. The check still happens — but *inside* `orElse`, written once by the JDK
authors, instead of by every caller, every time.

### Step 2 — the hidden cost inside `orElse`

A helper that builds a fallback and **prints when it runs** — the print is our spy:

```java
static Event expensiveDefault() {
    System.out.println("...expensiveDefault() is RUNNING...");
    return new Event("Default Event");
}
```

Imagine building this default is slow — a database call, a big computation.

The experiment: the box is **full** (`"E1"` exists), so the fallback will not be needed.

```java
Event e = findById("E1").orElse(expensiveDefault());
System.out.println("result: " + e.name());
```

Output:

```
...expensiveDefault() is RUNNING...
result: Coldplay Live
```

The fallback was thrown away — but **the spy printed. The method ran anyway.**

**Why?** A plain Java rule, nothing to do with `Optional`: **arguments are computed before
the method they are passed to gets called.** Java works inside-out:

1. Run `findById("E1")` → full box.
2. Prepare the argument for `orElse` — i.e. **run `expensiveDefault()` right now**.
3. Only then call `orElse(...)` with the already-built value.

By the time `orElse` sees the box is full, the expensive work is already done. `orElse`
received a finished value, not instructions for making one.

True meaning of `orElse(x)`: **"build `x` now, no matter what — use it only if the box is
empty."**

When it hurts:

- `orElse(new Event("Unknown"))` — harmless; a tiny object thrown away costs nothing.
- `orElse(loadDefaultFromDatabase())` — every lookup now hits the database **even when the
  value was found**. Looks innocent, works correctly, quietly doubles your database
  traffic. A real bug people ship.

### Step 3 — `orElseGet`: give instructions, not the finished thing

The problem in step 2: `orElse` gets a **finished value**. The work to build it is already
done before the box is even checked.

The fix: give **instructions for making the fallback**, instead of the fallback itself.
Instructions on paper cost nothing. They only cost something when someone follows them.

That is `orElseGet`. It takes a small function (a "recipe"):

```java
// as a lambda:
Event e = findById("E1").orElseGet(() -> expensiveDefault());

// or as a method reference — means the same thing:
Event e = findById("E1").orElseGet(Concept4::expensiveDefault);
```

Full box:

```
result: Coldplay Live
```

**The spy did not print.** The box was full, so the instructions were never followed.
`expensiveDefault()` never ran. This is what `orElse` could not do.

Empty box:

```
...expensiveDefault() is RUNNING...
result: Default Event
```

Now it ran — because the fallback was truly needed.

The whole difference in one table:

| | Box full | Box empty |
| --- | --- | --- |
| `orElse(expensiveDefault())` | fallback **built anyway**, then thrown away | fallback built and used |
| `orElseGet(Concept4::expensiveDefault)` | recipe **never runs** | recipe runs, result used |

The results are always the same. Only the wasted work (top-left cell) differs.

Rule of thumb:

- Cheap fallback (`"unknown"`, a small `new`) → `orElse`. Reads simpler.
- Costly fallback (database, network) → `orElseGet`. Pays only when needed.

### Step 4 — `orElseThrow`: empty box means "error, stop here"

Sometimes an empty box is **not** okay. A user books a ticket for event `E999`. The box is
empty — no such event. No fallback makes sense. The only honest thing: **stop, and say
clearly what went wrong.**

In Java, "stop and say what went wrong" = **throwing an exception**. An exception is an
error report. Throwing it stops the normal flow, and the report travels up until something
handles it (or the program ends and prints it).

`orElseThrow`:

- Box full → gives the value. Nothing special happens.
- Box empty → throws the error **you** describe.

```java
// Full box: just gives the value.
Event good = findById("E1")
        .orElseThrow(() -> new IllegalStateException("No event with id E1"));
System.out.println("full box gave: " + good.name());

// Empty box: stops the program with OUR error message.
Event bad = findById("E999")
        .orElseThrow(() -> new IllegalStateException("No event with id E999"));
```

Output:

```
full box gave: Coldplay Live
Exception in thread "main" java.lang.IllegalStateException: No event with id E999
	at Concept4Step4.main(Concept4Step4.java:24)
```

| Line | In plain words |
| --- | --- |
| `full box gave: Coldplay Live` | `E1` was found. `orElseThrow` handed over the value. No error. |
| `IllegalStateException: No event with id E999` | `E999` was missing. The program stopped — with **our own words**, naming the exact missing id. |
| `at Concept4Step4.main(...java:24)` | And the exact line. |

Taking apart the bracket:

- `new IllegalStateException("...")` — **creates** the error report with our message.
- `() ->` — wraps it in a **recipe**: "when you need it, make this error."

Why a recipe? Same reason as `orElseGet`: usually the box is full and no error is needed.
With a recipe, the error object is only created in the rare empty case.

Compare the errors:

- Concept 1 crash: `NullPointerException: Cannot invoke "Event.name()"...` — machine
  complaint, far from the cause, says nothing about *what* was missing.
- This: `No event with id E999` — our sentence, at the exact line, naming the missing
  thing. A controlled error instead of a crash.

Use `orElseThrow` when an empty box means something is genuinely wrong.

### Step 5 — `get()`: the method you must never use

What `get()` does:

- Box full → gives the value.
- Box empty → **crash.** No fallback, no message of yours.

```java
// get() on a FULL box: works. This is what tempts people.
Event good = findById("E1").get();
System.out.println("full box, get() gave: " + good.name());

// get() on an EMPTY box: crash.
Event bad = findById("E999").get();
```

Output:

```
full box, get() gave: Coldplay Live
Exception in thread "main" java.util.NoSuchElementException: No value present
	at java.base/java.util.Optional.get(Optional.java:143)
	at Concept4Step5.main(Concept4Step5.java:22)
```

| Line | In plain words |
| --- | --- |
| `full box, get() gave: Coldplay Live` | On a full box, `get()` works. This is the trap — it works in tests, so it feels safe. |
| `NoSuchElementException: No value present` | Empty box: crash. "You asked for the thing inside, but there is no thing inside." |
| `at Concept4Step5.main(...java:22)` | The line that called `get()`. |

Compare with Concept 1:

- **Concept 1:** happy path works → missing value → crash, unhelpful machine message.
- **`get()`:** happy path works → missing value → crash, unhelpful machine message.

Same bug, different exception name. We put the value in a box, then `get()` threw the box's
safety away. That is why `get()` is called "a NullPointerException with extra steps."

The trap: `get()` *works* when the value is there. You test with `E1`, ship it, and months
later `E999` arrives — crash. The Concept 1 story again.

What to write instead:

- Missing is normal, cheap fallback → `orElse("unknown")`
- Missing is normal, costly fallback → `orElseGet(recipe)`
- Missing is an error → `orElseThrow(() -> new ...("your clear message"))`

Even when 100% sure the box is full, prefer `orElseThrow` with a message — if you are ever
wrong, you get *your* sentence instead of `No value present`, which tells future-you
nothing.

You will see `get()` in older tutorials and codebases, so recognize it. In your own code:
**never `get()`. Always say what should happen when the box is empty.**

### Concept 4 summary

| Method | Box full | Box empty |
| --- | --- | --- |
| `orElse(value)` | box's value (fallback built anyway, thrown away) | your fallback value |
| `orElseGet(recipe)` | box's value (recipe never runs) | recipe runs, result used |
| `orElseThrow(recipe)` | box's value | your exception, your message |
| `get()` — forbidden | box's value | crash: `NoSuchElementException` |

## Concept 5 — Working on the value without opening the box

Real code wants to do things *between* the lookup and the opening — e.g. we found an
`Event` but only want its **name**. Opening the box early is where the danger lives. These
methods work **while the value stays in the box**.

### Step 1 — `map`: change the value inside

Read `map` as: *"if the box has a value, change it using this instruction and give me a new
box with the result. If the box is empty, skip the instruction and give me an empty box."*

```java
// FULL box: map applies the change to the value inside.
Optional<String> fullName = findById("E1").map(e -> e.name());
System.out.println("full  box after map: " + fullName);

// EMPTY box: map quietly does nothing. No crash.
Optional<String> emptyName = findById("E999").map(e -> e.name());
System.out.println("empty box after map: " + emptyName);
```

Output:

```
full  box after map: Optional[Coldplay Live]
empty box after map: Optional.empty
```

Taking the key line apart:

- `findById("E1")` → a box with an `Event`. Type: `Optional<Event>`.
- `e -> e.name()` — the instruction: "take the event, call it `e`, give back its name."
- `map` applies it to the value **inside** the box and wraps the result in a **new box**.
- The type changed: box of Event → box of String. Hence `Optional<String>`.

Two things to notice:

1. **The result is still in a box** — `Optional[Coldplay Live]`, not `Coldplay Live`.
   `map` never opens the box. To take the value out, finish with Concept 4
   (e.g. `.orElse("unknown")`).
2. **The empty case did not crash.** The instruction never ran — no event to run it on —
   and the emptiness simply carried forward. In Concept 1 the same situation was a
   `NullPointerException`; here it flows through silently with no `if`.

This is the start of chains: lookup → `map` (maybe several) → open at the end. The empty
case rides along untouched the whole way.

### Step 2 — `filter`: keep the value only if it passes a test

Read `filter` as: *"if the box has a value, test it. Pass → keep the box as it is. Fail →
give me an **empty** box. Already empty → stay empty, don't even run the test."*

Real-life need: booking. "The event exists" is not enough — it must also have tickets left.
A sold-out event is as useless to the booking flow as a missing one.

`Event` now also has a `ticketsLeft` number:

```java
record Event(String name, int ticketsLeft) {}

static Optional<Event> findById(String id) {
    if (id.equals("E1")) {
        return Optional.of(new Event("Coldplay Live", 12));   // tickets available
    }
    if (id.equals("E2")) {
        return Optional.of(new Event("Ed Sheeran Tour", 0));  // sold out
    }
    return Optional.empty();
}

// Case 1: full box, test PASSES (12 > 0) -> box stays full.
Optional<Event> bookable1 = findById("E1").filter(e -> e.ticketsLeft() > 0);

// Case 2: full box, test FAILS (0 > 0 is false) -> box becomes empty.
Optional<Event> bookable2 = findById("E2").filter(e -> e.ticketsLeft() > 0);

// Case 3: empty box -> test never runs, stays empty.
Optional<Event> bookable3 = findById("E999").filter(e -> e.ticketsLeft() > 0);
```

Output:

```
E1   (12 tickets): Optional[Event[name=Coldplay Live, ticketsLeft=12]]
E2   (sold out)  : Optional.empty
E999 (not found) : Optional.empty
```

The test `e -> e.ticketsLeft() > 0` answers only yes or no:

- **E1:** `12 > 0` → yes → box passes through **unchanged**.
- **E2:** `0 > 0` → no → `filter` **empties the box**. The event existed, but failed the
  test, so from here on it is "nothing usable."
- **E999:** already empty → test never runs → stays empty. No crash.

Worth noticing:

1. **`filter` never changes the value — only full vs. empty.** `map` can turn a box-of-Event
   into a box-of-String; `filter` cannot. Type in = type out.
2. **After `filter`, two different stories look identical.** "Sold out" and "not found" both
   end as `Optional.empty`. Intentional: to booking code both mean "nothing to book," one
   `orElseThrow` handles both. If you need different error messages for the two cases,
   `filter` is the wrong tool — check the conditions separately.

In a chain:

```java
findById("E2")
    .filter(e -> e.ticketsLeft() > 0)   // only bookable events survive
    .map(e -> e.name())                  // then take the name
    .orElse("nothing to book");          // open the box with a fallback
```

For `E2` the `filter` empties the box, the `map` is skipped, `orElse` gives
`"nothing to book"`. Still no `if` anywhere.

### Step 3 — `ifPresent`: do an action with the value, only if there is one

`map` and `filter` give back a box — they compute results. Sometimes you want no result at
all, just an **action**: print, send an email, write a log. Only if the value exists.

Read `ifPresent` as: *"if the box has a value, run this action with it. If the box is
empty, do nothing at all."*

```java
// FULL box: the action runs with the value.
System.out.println("-- E1 --");
findById("E1").ifPresent(e -> System.out.println("sending email about: " + e.name()));

// EMPTY box: the action is skipped. Nothing happens, no crash.
System.out.println("-- E999 --");
findById("E999").ifPresent(e -> System.out.println("sending email about: " + e.name()));

System.out.println("-- done --");
```

Output:

```
-- E1 --
sending email about: Coldplay Live
-- E999 --
-- done --
```

- **E1:** box full → the action runs, `e` is the event from the box.
- **E999:** box empty → the action is **skipped**. Between `-- E999 --` and `-- done --`
  there is nothing: no message, no error. The program just moved on.

What makes `ifPresent` different: **it gives nothing back.** You cannot chain after it or
store its result. It is always an **ending**, used when the goal is a side effect rather
than a value.

Choosing between the tools so far:

| Need | Tool |
| --- | --- |
| A **value** out of the box | `orElse` / `orElseGet` / `orElseThrow` (Concept 4) |
| **Transform** the contents, stay in the box | `map` |
| **Test** the contents, stay in the box | `filter` |
| **Do an action** if the value exists, else nothing | `ifPresent` |

A useful relative — `ifPresentOrElse` takes **two** actions, one per case:

```java
findById("E999").ifPresentOrElse(
        e -> System.out.println("sending email about: " + e.name()),
        () -> System.out.println("no event, nothing to send"));
```

Output:

```
no event, nothing to send
```

Spelling warning: `ifPresent` (an action) is not `isPresent` (a yes/no question). The
latter is half of the anti-pattern covered in Concept 7.

## Concept 6 — `flatMap`: when the function already returns a box

### Step 1 — the problem: a box stuck inside another box

So far the functions given to `map` returned **plain values** (`e -> e.name()` → `String`).
But many real functions return an **`Optional` themselves**, because their answer might
also be missing. Watch `map` meet such a function.

Two lookups now — an event knows its venue's *id*, and the venue is a separate lookup that
can also fail:

```java
record Venue(String name) {}
record Event(String name, String venueId) {}

// Lookup 1: id -> maybe an Event
static Optional<Event> findEvent(String id) {
    if (id.equals("E1")) {
        return Optional.of(new Event("Coldplay Live", "V7"));
    }
    return Optional.empty();
}

// Lookup 2: venueId -> maybe a Venue (venues can be missing too!)
static Optional<Venue> findVenue(String venueId) {
    if (venueId.equals("V7")) {
        return Optional.of(new Venue("Wembley Stadium"));
    }
    return Optional.empty();
}

// Goal: from event id, get the venue. Try the tool we know: map.
Optional<Optional<Venue>> nested = findEvent("E1").map(e -> findVenue(e.venueId()));
System.out.println("what we got: " + nested);
```

Output:

```
what we got: Optional[Optional[Venue[name=Wembley Stadium]]]
```

**Two** `Optional`s — a box, inside it another box, and only inside that, the venue.

Why? No bug — `map` did its job: "run the instruction on the value, **put the result in a
new box**."

1. Value in the box: the event.
2. Instruction result: `findVenue(...)` returns `Optional<Venue>` — **a box**.
3. `map` wraps that box in a new box, as it always does. → `Optional<Optional<Venue>>`.

The nested thing is unusable. Trying to continue the chain:

```java
String name = nested.map(v -> v.name()).orElse("unknown"); // compile error!
```

Output (compile time):

```
error: cannot find symbol
        String name = nested.map(v -> v.name()).orElse("unknown");
                                       ^
  symbol:   method name()
  location: variable v of type Optional<Venue>
```

| Part | In plain words |
| --- | --- |
| `cannot find symbol: method name()` | `.name()` called on something without that method. |
| `location: variable v of type Optional<Venue>` | Opening the **outer** box revealed the **inner box**, not a `Venue`. Boxes have no `.name()`. |

The rule this teaches:

- Function returns a **plain value** → `map`. One layer of wrapping is right.
- Function returns **an `Optional` already** → `map` adds a second layer. One too many.
  You need `flatMap`.

### Step 2 — `flatMap`: the fix, one word changed

`flatMap` behaves exactly like `map` with one difference: it knows your instruction already
returns a box, so it **does not wrap the result again** — it hands that box through as-is.
"Flat" = it flattens two layers into one.

Same lookups, plus an event `E2` whose venue id points nowhere (bad data):

```java
static Optional<Event> findEvent(String id) {
    if (id.equals("E1")) {
        return Optional.of(new Event("Coldplay Live", "V7"));   // venue exists
    }
    if (id.equals("E2")) {
        return Optional.of(new Event("Mystery Night", "V404")); // venue missing!
    }
    return Optional.empty();
}

// Same line as step 1, with ONE word changed: map -> flatMap.
Optional<Venue> venue = findEvent("E1").flatMap(e -> findVenue(e.venueId()));
System.out.println("E1 venue: " + venue);

// The chain we could not write before now just works:
String name = findEvent("E1")                 // box with the event
        .flatMap(e -> findVenue(e.venueId())) // box with the venue (no double-wrap)
        .map(v -> v.name())                   // box with the venue's name (a String)
        .orElse("unknown venue");             // open the box: value or fallback
```

Output (chain run for E1, E2, E999):

```
E1 venue: Optional[Venue[name=Wembley Stadium]]
E1 venue name: Wembley Stadium
E2 venue name: unknown venue
E999 venue name: unknown venue
```

With `map` this printed `Optional[Optional[Venue[...]]]`; with `flatMap` it is **one** box,
type `Optional<Venue>`.

Why `flatMap` on one line but `map` on the next? Look at what each instruction returns:

- `findVenue(...)` returns `Optional<Venue>` — **already a box** → `flatMap`.
- `v.name()` returns `String` — **a plain value** → `map`.

That is the entire decision rule: check the return type of the function you are passing.

The failure cases, with zero `if` statements:

- `E2`: event found, **venue** missing → box becomes empty at the `flatMap` step, `map` is
  skipped, `orElse` gives the fallback.
- `E999`: **event** missing → box empty from the start, everything skipped, same fallback.

Concept 1's defensive version of this was `if (e != null && e.venue() != null) { ... }` —
every arm of that nested check is now built into the chain.

## Concept 7 — the anti-pattern: `isPresent()` + `get()`

One new method: `isPresent()` asks the box a yes/no question — "do you have a value?" It
returns `true`/`false`. It does not open the box.

The most common `Optional` mistake in the Java world:

```java
Optional<Event> opt = findById("E1");
if (opt.isPresent()) {           // "is the box full?"
    sendEmailAbout(opt.get());   // "then open it"
}
```

It compiles, runs, and is even safe — the `if` guards the `get()`. The problem shows when
you put it next to the Concept 1 code it was supposed to replace:

```java
// the old null world:
Event e = findById("E1");
if (e != null) {
    sendEmailAbout(e);
}

// the "new" Optional world:
Optional<Event> opt = findById("E1");
if (opt.isPresent()) {
    sendEmailAbout(opt.get());
}
```

**Same shape. Same `if`. Same manual check.** Only the words changed. You boxed the value —
then checked it by hand anyway. A null check in a costume: more code, zero added safety,
chain tools unused.

The rewrites (both are things already covered):

```java
// Rewrite 1: you wanted to DO something if the value exists -> ifPresent.
findById("E1").ifPresent(e -> sendEmailAbout(e));
findById("E1").ifPresent(Concept7::sendEmailAbout);   // same, method reference

// Rewrite 2: you wanted the VALUE, and absence is an error -> chain.
String name = findById("E1")
        .map(e -> e.name())
        .orElseThrow(() -> new IllegalStateException("No event with id E1"));
```

Output (anti-pattern and both forms of Rewrite 1 behave identically):

```
sending email about: Coldplay Live
sending email about: Coldplay Live
sending email about: Coldplay Live
chain gave: Coldplay Live
```

Why reviewers reject `isPresent()`/`get()` on sight:

- **It reintroduces the forgettable step.** Safety again depends on someone writing the
  `if` — the discipline problem from Concept 1. Someone can later move the `get()` out of
  the `if` and it still compiles.
- **More code that says less.** `opt.ifPresent(this::use)` states the intent in one read.

`isPresent()` alone is fine when a true/false answer is all you need
(`boolean exists = findById(id).isPresent();`). The anti-pattern is the **pair**:
check with `isPresent()`, then unwrap with `get()`. One of `ifPresent`, `map`, `filter`,
`orElse`, `orElseGet`, `orElseThrow`, `flatMap` always says it better.

## Concept 8 — the placement rules: where `Optional` must never appear

`Optional` was designed for one job: being a **return type**. The reason behind all three
forbidden places is the same:

> **An `Optional` variable is still an object reference — it can itself be `null`.**
> The box does not protect you from the box being missing.

The compiler never forces anyone to fill in an `Optional`. Only in the return-type position
is that harmless (the method body creates the box right there). Everywhere else, someone
can leave the box as `null` — then you have null danger *plus* boxing ceremony.

Breaking all three rules:

```java
// RULE BREAK 1: Optional as a FIELD.
static class UserProfile {
    Optional<String> nickname; // looks safe... but fields start as null!
}

// RULE BREAK 2: Optional as a METHOD PARAMETER.
static void greet(Optional<String> name) {
    System.out.println("Hello, " + name.orElse("guest"));
}

// Parameter problem A: every caller must wrap. Clumsy.
greet(Optional.of("Pavan"));
greet(Optional.empty());
// Parameter problem B: the box itself can be null!
greet(null); // compiles fine, crashes inside

// RULE BREAK 3: Optional inside a collection. Two kinds of nothing.
Map<String, Optional<String>> seatHolder = new HashMap<>();
seatHolder.put("A1", Optional.of("Pavan"));
seatHolder.put("A2", Optional.empty());   // seat exists, nobody in it
seatHolder.get("A3");                     // null! not Optional.empty

// Field problem: the field starts as null, box or no box.
UserProfile p = new UserProfile();
p.nickname.orElse("no nickname"); // crash
```

Output:

```
Hello, Pavan
Hello, guest
greet(null) crashed: Cannot invoke "java.util.Optional.orElse(Object)" because "<parameter1>" is null
A1: Optional[Pavan]
A2: Optional.empty
A3: null
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "java.util.Optional.orElse(Object)" because "<local2>.nickname" is null
	at Concept8.main(Concept8.java:41)
```

**Not a field.** Every object field without a value starts as `null` — including `Optional`
fields. `nickname` was not an empty box; it was **no box at all**, and `.orElse()` on a
missing box is the Concept 1 crash again. Instead: a plain field that may be null inside
the class, and a **getter returning `Optional<String>`** — box at the boundary. (Also:
`Optional` is deliberately not serializable, and it adds a wrapper object per field.)

**Not a parameter.** Callers are forced into ceremony (`Optional.of("Pavan")` instead of
`"Pavan"`), and a caller can still pass `null` *as the box* — compiles, crashes inside.
Use two overloads (`greet()` / `greet(String)`) instead. Deeper point: in a return type,
`Optional` tells the *caller* something it could not know; in a parameter it tells the
*method* nothing it could not express better.

**Not inside a collection.** `A2` is `Optional.empty` but the absent key `A3` is `null` —
**two different kinds of nothing**, and every reader must handle both. Collections already
say "absent" perfectly: the entry just is not there.

| Place | Verdict | One-line reason |
| --- | --- | --- |
| Return type | **yes** — its entire purpose | warns the caller: "may be missing" |
| Field | never | fields start as `null`, so the box itself can be missing; not serializable |
| Parameter | never | forces ceremony on callers, and `null` can still be passed as the box |
| In a collection | never | creates two kinds of nothing; absence = just no entry |

## Concept 9 — the real Phase 1 line, end to end

```java
repository.findById(id).map(Event::name).orElseThrow(() -> new EventNotFoundException(id));
```

Everything in it is covered above. The full program around it:

```java
record Event(String name) {}

// Our own error type, with a message that names the missing id.
static class EventNotFoundException extends RuntimeException {
    EventNotFoundException(String id) {
        super("Event not found: " + id);
    }
}

// A tiny "repository" — in Phase 1 this will be a real one backed by a database.
static class EventRepository {
    Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }
}

// THE LINE.
static String eventName(String id) {
    return repository.findById(id)
            .map(Event::name)
            .orElseThrow(() -> new EventNotFoundException(id));
}
```

Output:

```
E1 gave: Coldplay Live
Exception in thread "main" Concept9$EventNotFoundException: Event not found: E999
	at java.base/java.util.Optional.orElseThrow(Optional.java:403)
	at Concept9.eventName(Concept9.java:33)
	at Concept9.main(Concept9.java:41)
```

`EventNotFoundException` is an error type **we made ourselves** — a class extending
`RuntimeException` whose constructor builds the message. The error carries its meaning in
its name; in a web service a handler catches this type and turns it into "404 Not Found".

Word by word:

| Piece | What it is | What it does |
| --- | --- | --- |
| `repository.findById(id)` | the lookup (Concept 2) | returns `Optional<Event>` — maybe an event, maybe empty |
| `.map(Event::name)` | transform inside the box (Concept 5) | full → box with the name `String`; empty → stays empty |
| `.orElseThrow(() -> new EventNotFoundException(id))` | open or stop (Concept 4) | full → gives the `String`; empty → throws our error naming the id |

`Event::name` is the shorter way to write `e -> e.name()`.

**The two stories, out loud:**

- **Event exists** (`E1`): `findById` → full box with the event. `map` runs `name()` →
  full box with `"Coldplay Live"`. `orElseThrow` sees full → the exception recipe is
  **never used**, no error object is even created. The string comes out.
- **Event missing** (`E999`): `findById` → empty box. `map` **skips** `Event::name`,
  passes emptiness on. `orElseThrow` sees empty → runs the recipe → throws
  `EventNotFoundException: Event not found: E999` — our words, the exact id, the exact
  line. Normal flow stops there.

Compare with the Concept 1 version of the same job:

```java
Event e = repo.findById(id);          // might be null — nothing warns you
if (e != null) { return e.name(); }   // every caller must remember this
throw new SomethingException();        // and this
```

The chain says all of that in one readable sentence, the compiler enforces that the empty
case is handled, and failure is a named, located, self-explaining error instead of a
surprise `NullPointerException`.

## Task summary

- A method returning `Event` that can be absent is lying; `Optional<Event>` puts the
  warning in the type, where the compiler enforces it.
- Create: `of` (certain), `empty` (certain nothing), `ofNullable` (maybe null).
- Open: `orElse` (cheap fallback), `orElseGet` (costly fallback, runs only if needed),
  `orElseThrow` (absence is an error). Never `get()`.
- Work inside the box: `map` (transform), `filter` (test), `ifPresent` (act).
- `flatMap` when the function you pass already returns an `Optional`.
- `isPresent()` + `get()` = a null check in a costume; use the chain tools.
- `Optional` is a **return type only**: never a field, parameter, or collection element.

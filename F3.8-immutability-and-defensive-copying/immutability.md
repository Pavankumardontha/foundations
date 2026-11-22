# Task F3.8 — Immutability and defensive copying

## Concept 1 — The leaked reference

A **leaked reference** is when your object hands out a direct reference to its own internal
mutable data. Anyone holding that reference can change your object's state from the outside —
without calling any method of yours, and without your object ever finding out.

### Why it matters

`private` only protects the *field* (the variable holding an address). It stops outsiders from
writing `order.seatIds = otherList`. It says nothing about what someone can do with the list
once you hand them its address. If the data behind the field is mutable and you leak it,
`private` is defeated.

The mechanism is aliasing (from F2.5): **a reference-type variable contains the object's
address, not the object.** Return the field from a getter and the caller's variable holds the
same address — two names, one list.

### Step 1 — the "safe-looking" class that isn't

```java
static class Order {
    private final List<String> seatIds;

    Order(List<String> seatIds) {
        this.seatIds = seatIds;
    }

    public List<String> seatIds() {
        return seatIds;   // hands out the internal list itself
    }
}

public static void main(String[] args) {
    Order order = new Order(new ArrayList<>(List.of("A1", "A2")));
    System.out.println("Order starts as: " + order.seatIds());

    // The "attack": a perfectly innocent-looking caller
    List<String> seats = order.seatIds();
    seats.add("Z99");

    System.out.println("Order is now:    " + order.seatIds());
}
```

Output (Java 21, `Concept1.java`):

```
Order starts as: [A1, A2]
Order is now:    [A1, A2, Z99]
```

The order now contains `Z99`. No method of `Order` was called to add it, `Order` has no
setter, the field is `final` — it still changed.

### Step 2 — walkthrough

| Line | What actually happens |
| --- | --- |
| `new Order(new ArrayList<>(...))` | The **caller** creates an `ArrayList` `[A1, A2]` on the heap. The constructor only stores its address in `order.seatIds`. |
| `List<String> seats = order.seatIds();` | The getter returns the **address** of that same `ArrayList`. `seats` and the private field now point at one identical list. |
| `seats.add("Z99")` | There is exactly one list in memory, so adding through `seats` changes it. The private field was never looking at a different list. |
| second print | `Order` reports corrupted state, with no idea when or where the change happened. |

### Why this bug is nasty in real systems

A seat map handed to three services (pricing, booking, display) can be accidentally edited by
any of them if the getter leaks the internal list. The failure surfaces where the corrupted
data is *read*, far away from where it was *written* — the classic "who changed this?" bug
with no crash at the scene of the crime.

### Common mistakes

- **"It's `private`, so it's protected."** `private` guards the field, not the object the
  field points to.
- **"It's `final`, so it can't change."** `final` stops the field being re-pointed at a
  different list; the list itself stays fully editable.
- **"My getter only reads the field."** What it returns is a live door into your object.

### Check yourself

1. *After `List<String> seats = order.seatIds();`, how many `ArrayList` objects exist?*
   One. `seats` contains the same address the private field contains — two variables, one
   list. (Precision point: that list was created by the **caller** in `main`; the constructor
   only stored its address.)
2. *Could a caller corrupt the order through a leaked `private final String eventName`?*
   No. `String` is immutable — it has no method that changes its contents — so leaking a
   reference to it is harmless. Leaking is only dangerous when the leaked object is mutable.

### Summary

- A reference-type getter that returns a mutable field hands out the object's address.
- Caller and object then share one list: aliasing. The caller can mutate it; the object
  never knows.
- `private` and `final` do not prevent this.
- Leaking immutable things (`String`, records of primitives) is safe; leaking mutable
  collections is the bug.

## Concept 2 — Copy on the way out

**Copy on the way out:** a getter that would expose internal mutable data returns a **copy**
instead of the original. Same contents, different object — the caller's actions cannot reach
your internals.

```java
public List<String> seatIds() {
    return List.copyOf(seatIds);     // copy on the way OUT
}
```

`List.copyOf(source)` does two things:

1. **Copies** — creates a new list filled with `source`'s elements at that instant.
2. **Locks the copy** — the returned list is *unmodifiable*: `add`/`remove`/`set` throw.

### Step 1 — same attack, one line changed

Same `Order` as Concept 1, only the getter changed (`Concept2.java`):

```java
List<String> seats = order.seatIds();
seats.add("Z99");                    // exact same attack as Concept 1
```

Output:

```
Order starts as: [A1, A2]
Exception in thread "main" java.lang.UnsupportedOperationException
	at java.base/java.util.ImmutableCollections.uoe(ImmutableCollections.java:142)
	at java.base/java.util.ImmutableCollections$AbstractImmutableCollection.add(ImmutableCollections.java:147)
	at Concept2.main(Concept2.java:23)
```

### Decoding the error

| Part of the message | Plain English |
| --- | --- |
| `UnsupportedOperationException` | "You asked this object to do something it refuses to do, ever." The operation itself is forbidden. |
| `ImmutableCollections.uoe(...)` | The list came from Java's immutable-collections family (`List.copyOf`, `List.of`). `uoe` is their internal helper that throws this. |
| `AbstractImmutableCollection.add` | This `add` is a deliberate booby trap: its entire implementation is "throw". |
| `at Concept2.main(Concept2.java:23)` | The error fires **on the attack line itself**, not far away later. |

Proof the object survived (`Concept2Proof.java` catches the exception):

```
Attack failed with: java.lang.UnsupportedOperationException
Order is still:    [A1, A2]
```

### Leak vs. copy-out, side by side

| Step | Concept 1 (leak) | Concept 2 (copy out) |
| --- | --- | --- |
| getter returns | the private list's own address | a new unmodifiable list, same contents |
| lists in memory after the call | 1 | 2 |
| `seats.add("Z99")` | silently corrupts the order | throws immediately |
| bug surfaces | wherever corrupted data is *read*, later | on the attack line, in the stack trace |

### Why `List.copyOf`, not `new ArrayList<>(...)`, on the way out

Both protect the order. The difference is the *caller's* mistake:

- `new ArrayList<>(seatIds)` → caller's `add` silently succeeds on their private copy; they
  now wrongly believe they changed the order.
- `List.copyOf(seatIds)` → caller's `add` throws on the spot: "this is not how you change an
  order." A loud failure at the scene beats a silent lie.

### Common mistakes

- **Thinking the class is now immutable.** The way **in** is still open — the constructor
  still stores the caller's list address directly (fixed in Concept 3).
- **Worrying about copy cost prematurely.** One small copy per call is trivial next to one
  "who changed this?" debugging session.
- **Confusing "unmodifiable" with "nothing can change".** Java also has *views* that look
  unmodifiable while the data behind them moves (Concept 5).

### Check yourself

1. *After `seats = order.seatIds()` in the copy-out version — how many lists, and which can
   `seats.add` modify?* Two lists; neither. The copy throws on `add`, and the private list's
   address never left the object.
2. *What does the way-in attack (`mine.add("HACKED")` after `new Order(mine)`) print?*
   `[A1, HACKED]` — `mine` and the field alias the same mutable `ArrayList`. Copy-out does
   nothing about this; that is the remaining hole.

### Summary

- Copy on the way out = getter returns `List.copyOf(field)`: new object, locked contents.
- The attack from Concept 1 now throws `UnsupportedOperationException` at the attack line.
- Prefer the unmodifiable copy over a mutable copy: loud failure beats silent lie.
- The constructor is still a hole — copy-out alone is not immutability.

## Concept 3 — Copy on the way in

**Copy on the way in:** the constructor does not store the caller's list — it stores a
**private snapshot** of its contents, made at construction time.

```java
Order(List<String> seatIds) {
    this.seatIds = List.copyOf(seatIds);   // copy on the way IN
}
```

### Why copy-out could not save us

In the way-out attack the caller got the dangerous address *from the getter*, so fixing the
getter fixed the attack. In the way-in attack the caller **created the list** — they held its
address before the object existed, and that can never be revoked. The only move is to stop
using their list: snapshot its contents and point the field at the snapshot.

### Step 1 — proof the hole was real (`Concept3Hole.java`)

The Concept 2 class (copy-out only) under the way-in attack:

```java
List<String> mine = new ArrayList<>(List.of("A1"));
Order order = new Order(mine);
mine.add("HACKED");                  // never touched 'order' at all
```

Output:

```
Order starts as: [A1]
Order is now:    [A1, HACKED]
```

### Step 2 — both doors closed (`Concept3.java`)

```java
static class Order {
    private final List<String> seatIds;

    Order(List<String> seatIds) {
        this.seatIds = List.copyOf(seatIds);   // copy on the way IN
    }

    public List<String> seatIds() {
        return seatIds;                        // already an unmodifiable snapshot
    }
}
```

Both attacks against it:

```
mine is:  [A1, HACKED]
order is: [A1]
way-out attack failed with: java.lang.UnsupportedOperationException
order is: [A1]
```

The way-in attack *succeeds on the caller's own list* (`mine` really becomes
`[A1, HACKED]` — no exception) but the edit lands on a list the order stopped caring about at
construction. The way-out attack throws. Either way the order stays `[A1]`.

### The getter surprise

The getter is back to `return seatIds;` — the exact line that caused the Concept 1 disaster —
yet it is now safe. The real rule was never "always copy in getters"; it is **"never hand out
a reference to mutable internal state."** Here the field itself is an unmodifiable snapshot,
so handing out its address gives attackers nothing. This is the standard pattern: **one copy
at construction, free reads forever.** (`List.copyOf` on an already-unmodifiable list returns
it as-is, no copy — so defending twice costs nothing.)

### Common mistakes

- **`new ArrayList<>(seatIds)` on the way in + `return seatIds;` on the way out.** The field
  is now a *mutable* copy, so the getter leaks it — Concept 1 all over again. Mutable copy-in
  must still be paired with copy-out; `List.copyOf` at construction covers both doors at once.
- **Believing one direction is enough.** Copy-out alone: see `[A1, HACKED]`. Copy-in alone
  with a leaked mutable field: see Concept 1.
- **Copying too late.** The snapshot must happen in the constructor, before first use.

### Check yourself

1. *"Copying in the constructor is wasted work — the getter already returns `List.copyOf`."*
   Wrong: the caller can edit the list **after construction and before any getter call**,
   through the reference they already hold. The getter then faithfully copies corrupted data.
2. *Constructor stores `new ArrayList<>(seatIds)`, getter returns the field directly — which
   attack works?* The way-out attack: the way in is defended by the copy, but the field is a
   mutable list and the getter hands it out.

### Summary

- Copy-in = snapshot the constructor argument; the caller's retained reference becomes
  harmless because it points at a list you no longer use.
- Both directions are needed; `List.copyOf` in the constructor covers both, because the field
  itself becomes unmodifiable and can be returned directly.
- One copy at construction, free reads forever.

## Concept 4 — What `final` actually prevents

`final` on a field means: **the field can be assigned exactly once, and never re-assigned.**
That is the entire feature. A reference-type field is a box holding an address; `final` locks
the **box**, not the object at that address.

| Operation | What it is | Does `final` stop it? |
| --- | --- | --- |
| `seatIds = otherList;` | writing a new address into the box | **Yes — compile error** |
| `seatIds.add("B2");` | following the address, changing the object there | **No — compiles and runs** |

### Step 1 — re-pointing a final field (`Concept4Error.java`)

```java
private final List<String> seatIds = new ArrayList<>(List.of("A1"));

void swap() {
    seatIds = new ArrayList<>();   // re-pointing a final field
}
```

Real compiler output:

```
Concept4Error.java:8: error: cannot assign a value to final variable seatIds
        seatIds = new ArrayList<>();   // re-pointing a final field
        ^
1 error
```

Note: caught at **compile time**. `final` is enforced before the program runs — unlike the
leaked-reference bug, which no compiler catches.

### Step 2 — mutating THROUGH a final field (`Concept4.java`)

```java
private final List<String> seatIds = new ArrayList<>(List.of("A1"));

o.seatIds.add("B2");           // mutating THROUGH a final field
o.seatIds.add("C3");
```

Real output:

```
final field, happily mutated: [A1, B2, C3]
```

Same field, same `final`, opposite outcomes — only the first operation touches the box that
`final` guards.

### What `final` is actually for

- Guarantees the field means the same object for the whole life of the instance — no swaps,
  assigned once in the constructor.
- It is **half the immutability recipe**: `final` field **+** the object behind it is itself
  unchangeable (primitive, `String`, or a `List.copyOf` snapshot) **=** immutable class.
  Remove either half and there is a hole.

### The NOTES.md sentence

> `private final List<X> items` prevents the field being pointed at a different list.
> It does nothing about adding items.

### Common mistakes

- **"Field is `final` ⇒ class is immutable."** A `final` mutable list still accepts `add`
  through any alias.
- **Thinking `final` is "deep".** It is one lock on one box; it does not propagate into the
  object, its fields, or its elements.
- **Skipping `final` because it "doesn't do much".** Use it everywhere; it is half the
  recipe and documents intent for free.

### Check yourself

1. *What bug does `final` actually solve, and what does it leave open?* It solves the
   reassignment bug (the field being re-pointed). It leaves open mutation through any other
   reference that points at the same object.
2. *"If every field of a class is `final`, the class is immutable" — true?* **False.** Each
   field must additionally point at something that cannot itself change (primitive, `String`,
   or an unmodifiable snapshot such as `List.copyOf`'s result).

### Summary

- `final` = assign once; it locks the reference box, never the object behind it.
- Re-pointing fails at compile time; mutating through the field runs fine.
- Immutability recipe: `final` on the field **and** an immutable object behind it.

## Concept 5 — A view is a window, a snapshot is a photo

Two things that both look like "a read-only list":

- **View** — `Collections.unmodifiableList(original)` — a **window** onto the original. It
  stores the original's address; reads pass through live, writes are blocked. If the original
  changes, the view shows the change.
- **Snapshot** — `List.copyOf(original)` — a **photo**. Elements are copied out at that
  instant into a new locked list. Later changes to the original do not reach it.

The trap: from the outside they are nearly identical — both refuse `add` with the same
exception, both print the same contents at creation. The difference appears only **later**,
when the original changes.

### Step 1 — side by side (`Concept5.java`)

```java
List<String> original = new ArrayList<>(List.of("A1"));

List<String> view     = Collections.unmodifiableList(original); // a window
List<String> snapshot = List.copyOf(original);                  // a photo

original.add("B2");   // change the ORIGINAL — we never touch view/snapshot
```

Output:

```
original: [A1, B2]
view:     [A1, B2]
snapshot: [A1]
view.add     -> java.lang.UnsupportedOperationException
snapshot.add -> java.lang.UnsupportedOperationException
```

The view shows `B2`, added after the view was created. The snapshot does not. And both throw
identically on direct `add` — so "it refused my write" tells you nothing about which you hold.

### Step 2 — the bug caused by using the wrong one (`Concept5Bug.java`)

A getter returns a **view** where a **snapshot** was needed:

```java
static class Order {
    private final List<String> seatIds = new ArrayList<>();

    void hold(String seat) { seatIds.add(seat); }

    List<String> seatIds() {
        return Collections.unmodifiableList(seatIds);   // view, not copy
    }
}

List<String> emailSeats = order.seatIds();   // "seats at confirmation time"... we think
order.hold("B2");                            // later
```

Output:

```
email sent with seats:  [A1]
email now claims seats: [A1, B2]
```

The saved record of what the email contained **rewrote itself** — no exception anywhere.
Receipts, audit logs, "what did the user see at the time?" all go silently wrong.

### When is a view right?

When "live" is the requirement: e.g. a dashboard that must always display current seats,
read-only, without paying a copy per read. A view is a feature when you want *live*, a bug
when you want *a stable record*. **When returning internal state, default to the snapshot.**

### Common mistakes

- **"Unmodifiable" ≠ "immutable".** Unmodifiable = this handle refuses writes. Immutable =
  no handle anywhere can change it. `unmodifiableList` gives the first, `List.copyOf` the
  second.
- **Testing with `add` and concluding you are safe.** Both kinds throw the same exception;
  the only difference is whether the original's later changes show through.
- **Using a view as a cheap snapshot.** You save one copy and buy a history-rewriting bug.

### Check yourself

1. ```java
   List<String> names = new ArrayList<>(List.of("X"));
   List<String> frozen = List.copyOf(names);
   names.add("Y");
   System.out.println(frozen);
   ```
   Prints `[X]` — `frozen` is a photo taken before `Y` existed; the original's later change
   cannot reach it.
2. *A getter returns `Collections.unmodifiableList(tags)` over a private mutable `tags` —
   can callers be surprised?* Yes: a caller stores the returned view, the class's own methods
   later mutate the private `tags`, and the caller's "unmodifiable" list changes contents
   without the caller modifying anything. Both sides acted correctly; the surprise still
   happens — that is why it is the wrong return type for a stable result.

### Summary

- View = live window (blocks writes through itself, shows the original's changes).
- Snapshot = independent photo (nothing can change it, by anyone).
- Both throw `UnsupportedOperationException` on `add` — indistinguishable until the original
  moves.
- Returning internal state: snapshot by default; view only when "live" is explicitly wanted.

## Concept 6 — Defending a record with a compact constructor

### Record recap

`record Booking(String id, List<String> seats) {}` generates: a `private final` field per
component, a constructor, accessors `id()` / `seats()`, `equals`, `hashCode`, `toString`.
Records are designed as immutable data carriers — but they are only automatically **`final`**,
not automatically **immutable**.

### Step 1 — a plain record falls to both attacks (`Concept6Hole.java`)

The generated constructor stores exactly the reference you pass; the generated accessor
returns exactly that reference. Both doors are open:

```java
record Booking(String id, List<String> seats) {}

List<String> mine = new ArrayList<>(List.of("A1"));
Booking booking = new Booking("B-1", mine);

mine.add("HACKED");                 // attack 1: the way in
booking.seats().add("Z99");         // attack 2: the way out
```

Output:

```
booking starts as: [A1]
after way-in:      [A1, HACKED]
after way-out:     [A1, HACKED, Z99]
```

### Step 2 — the one-line fix: the compact constructor (`Concept6.java`)

A **compact constructor** is a record-only form: the record's name, **no parameter list**, no
field assignments.

```java
record Booking(String id, List<String> seats) {
    Booking {
        seats = List.copyOf(seats);     // copy IN; this value gets stored
    }
}
```

How it works:

1. Inside, `seats` refers to the **constructor parameter** (the caller's list).
2. Your code runs first; reassigning the parameter replaces what it holds — now a locked
   snapshot.
3. The compiler then silently appends `this.id = id; this.seats = seats;` — storing whatever
   the parameters hold **at the end**, i.e. the snapshot.
4. The generated accessor returns the stored field — the snapshot — so the way out is
   defended too.

Same attacks, after the fix:

```
booking starts as: [A1]
after way-in:      [A1]
way-out attack failed with: java.lang.UnsupportedOperationException
booking is still:  [A1]
```

### Validation and copying share the same gate

```java
Booking {
    if (seats.isEmpty()) throw new IllegalArgumentException("a booking needs seats");
    seats = List.copyOf(seats);
}
```

Now no `Booking` can be empty **or** corruptible — one gate enforces both "cannot hold
invalid data" and "cannot be changed from outside".

### Common mistakes

- **"It's a record, so it's immutable."** Only if every component is itself immutable. A
  `List`, `Map`, `Set`, array, or `Date` component reopens both doors.
- **Writing `this.seats = List.copyOf(seats);` in the compact form.** Compile error — you
  only adjust parameters; the compiler does the storing.
- **Also overriding the accessor to copy again.** Harmless but pointless: the stored field is
  already locked, and `List.copyOf` on a locked list returns it as-is.

### Check yourself

1. *Record `Tags` copies in its compact constructor; caller does `raw.add("new")` after
   construction.* `raw` prints `[sale, new]`; `tags.values()` prints `[sale]` — the record
   stored a snapshot taken before `new` existed.
2. *`record Cart(String userId, List<String> itemIds) {}` — what can go wrong?* Both doors
   are open: outsiders can mutate via the `itemIds()` accessor (way out), and the constructor
   caller can edit the list they passed in afterwards (way in). Fix with one line:
   `Cart { itemIds = List.copyOf(itemIds); }`.

### Summary

- Records auto-generate `final` fields, constructor, accessors — but store and return the
  exact references you pass.
- Compact constructor + `List.copyOf` = both doors closed in one line.
- Validation lives in the same gate: impossible to construct invalid *or* corruptible
  instances.

## Concept 7 — Persistent update: how an object that cannot change... changes

> A **persistent update** is a method that does **not** touch the object it is called on.
> Instead it **builds a new object** containing the change and **returns it**. The old object
> keeps its old value forever ("persists" — hence the name).

### Step 1 — you already use this daily: `String`

A `String` can never be changed, yet `toUpperCase()` "changes" it — by building a new string:

```java
String name = "pavan";

name.toUpperCase();                    // result thrown away
System.out.println("1: " + name);

String loud = name.toUpperCase();      // result kept in a NEW variable
System.out.println("2: " + loud);
System.out.println("3: " + name);
```

Output (`Concept7A.java`):

```
1: pavan
2: PAVAN
3: pavan
```

Line 1: the new string was created and instantly lost — nobody stored it, and `name` cannot be
changed. Line 2: stored this time. Line 3: the old string persisted.

### Step 2 — the smallest version you can build yourself: `Counter`

```java
static final class Counter {
    private final int value;

    Counter(int value)  { this.value = value; }
    int value()         { return value; }

    Counter plusOne() {
        return new Counter(value + 1);   // NEW counter; this one is untouched
    }
}
```

`plusOne()` reads its own value, adds one, and builds a **second** `Counter`. Nothing in the
class writes to a field — the class *cannot* change, yet counting works:

```java
Counter c1 = new Counter(0);
Counter c2 = c1.plusOne();
Counter c3 = c2.plusOne();
```

Output (`Concept7B.java`):

```
c1 = 0
c2 = 1
c3 = 2
```

Three separate frozen objects. **The mental picture is not "one object changing over time"
but "a chain of frozen versions"** — photos, not a whiteboard.

### Step 3 — same idea with a list: `SeatMap`

The only new difficulty: you cannot write `seats + newSeat` like `value + 1`, so the method
needs a private working copy:

```java
static final class SeatMap {
    private final List<Seat> seats;

    SeatMap(List<Seat> seats) {
        this.seats = List.copyOf(seats);   // locked snapshot — copy IN
    }

    List<Seat> seats() { return seats; }

    SeatMap withSeatAdded(Seat seat) {
        List<Seat> bigger = new ArrayList<>(seats);  // 1. temp editable copy of my contents
        bigger.add(seat);                            // 2. add to the copy
        return new SeatMap(bigger);                  // 3. new version built from it
    }
}
```

| `Counter.plusOne()` | `SeatMap.withSeatAdded(seat)` |
| --- | --- |
| read my value | read my list |
| compute `value + 1` | copy the list, add the seat to the copy |
| `new Counter(result)` | `new SeatMap(result)` |
| I am untouched | I am untouched |

The temp copy is editable because the method must add to it (its own list refuses `add`); it
lives only inside the method, invisible to everyone, so editing it is safe. The constructor
then snapshots it — the new version is as locked as the old.

Output (`Concept7.java`):

```
v1: [Seat[id=A1]]
v2: [Seat[id=A1], Seat[id=A2]]
v3: [Seat[id=A1], Seat[id=A2], Seat[id=A3]]
v1 after ignored call: [Seat[id=A1]]
```

### The one trap

```java
v1.withSeatAdded(new Seat("B9"));   // result thrown away — does NOTHING visible
```

Same bug as `name.toUpperCase();` alone on a line. **Always keep the returned object:**
`v1 = v1.withSeatAdded(...)` or assign to a new variable.

Also note: an immutable class is declared `final` so no subclass can add mutable state or
override methods to misbehave.

### The honest trade-off

- **Cost:** every "change" allocates a new object (100 updates = 100 maps where mutation
  would build 1).
- **Benefit:** any holder of any version can cache it, share it, hand it to a thread —
  nothing can change it underneath them. "Who changed this?" becomes impossible, not rare.
- For a seat map updated on human timescales: cost trivial, benefit huge.

### Your doubts

> "Can you please once again start this concept from fresh and in simple terms. Its hard to
> understand."

Resolved by rebuilding the concept as a three-step ladder, each step one small addition:

1. `String` — persistent update you already use (`toUpperCase` returns a new string; old one
   unchanged; ignoring the result does nothing).
2. `Counter` — the same pattern hand-built with one `int`: `plusOne()` returns
   `new Counter(value + 1)`, never writes anything.
3. `SeatMap` — identical to `Counter`, except "value + 1" becomes "copy list, add seat",
   because lists need a working copy.

### Check yourself

1. ```java
   Counter c = new Counter(5);
   c.plusOne();
   c.plusOne();
   System.out.println(c.value());
   ```
   Prints `5` — both returned counters were thrown away; `c` was never (and can never be)
   updated.
2. *What does `plusOne()` do / never do?* It creates a new object by adding 1 to the value of
   the object it was called on; it never updates the value of the object it was called on.

### Summary

- Persistent update = return a changed **new** object; the old one persists unchanged.
- `String` already works this way; `Counter` is the minimal hand-built version; `SeatMap` is
  the same with a list and a working copy.
- Always assign the returned object — an ignored `with...` call is a no-op.
- Trade-off: more allocations, dramatically less reasoning.

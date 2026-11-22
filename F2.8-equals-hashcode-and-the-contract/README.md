# Task F2.8 — `equals`, `hashCode`, and the contract collections rely on

Beginner-friendly study notes for Task F2.8 of the Zenith plan. Each subtask is added here
as we go through it. Every code example and output below was compiled and run on Java 21.

## Subtasks covered so far

- [x] Subtask 1 — The quiet failure: two identical seats in a `HashSet`
- [x] Subtask 2 — Write `equals` and `hashCode` by hand
- [x] Subtask 3 — Let the IDE generate them, and read every line
- [x] Subtask 4 — Break it on purpose, version one: `equals` without `hashCode`
- [x] Subtask 5 — Break it on purpose, version two: change a field after adding
- [x] Subtask 6 — Write the contract in your own words
- [x] Subtask 7 — What `HashMap` does inside `put` and `get`
- [x] Subtask 8 — Rewrite `Seat` as a `record`
- [x] Subtask 9 — Entities: a `Customer` that compares by id

## What this task is about

`HashSet` and `HashMap` decide whether two objects are "the same" by calling two methods on
them: `hashCode()` and `equals()`. Every class inherits both from `Object`, and those inherited
versions treat only the very same object as equal. So unless you write your own, two seats
that are both "A1" count as two different seats, and nothing warns you.

This task shows that failure, fixes it, then breaks it two more ways on purpose. It ends with
a decision you'll make for every class in Phase 1. Should two objects count as equal when their
contents match (a *value*, like a seat id or an amount of money)? Or only when they're the same
thing with the same id (an *entity*, like a customer)?

In Zenith, getting this wrong means selling the same seat twice. It's also one of the most
common Java interview questions.

## Where things live

- **This README:** the reference notes for this task.
- **`../NOTES.md`:** your own notes, in your own words. The plan asks for these.
- **Your code for this task:** `SeatDemo.java` (Subtask 1 onwards). Type it into a file in
  this folder.

To run an example:

```bash
javac SeatDemo.java
java SeatDemo
```

Compile again every time you change the file.

## Key words in plain English

| Word | Meaning |
| --- | --- |
| `equals(Object o)` | Answers "do these two objects mean the same thing?". Every class inherits one from `Object`, and that version just does `==`. |
| Hash code | The `int` that `hashCode()` returns. Hash-based collections use it to decide where to file an object. The `hashCode()` every class inherits from `Object` gives each object its own number and ignores the fields. |
| Bucket | One of the internal slots a `HashSet` or `HashMap` files objects into, chosen from the hash code. Picture a wall of numbered drawers. |
| Collision | Two different objects landing in the same bucket. Normal and allowed: the collection then compares them to tell them apart. |
| Hash-based collection | A collection that files objects by hash code: `HashSet`, `HashMap`, `LinkedHashSet`, `LinkedHashMap`. |
| `Objects.equals(a, b)` | Compares two values with `.equals()`, and copes with either being `null`. (Subtask 2) |
| `Objects.hash(...)` | Combines the hash codes of the values you pass into one `int`. (Subtask 2) |
| Cast | `(Seat) o`: treat a reference whose declared type is `Object` as a `Seat`, so you can use `Seat`'s fields. Only safe after checking with `instanceof`. (Subtask 2) |
| `getClass()` | Returns the object's actual class while the program runs. For a `VipSeat` it's `VipSeat`, never `Seat`. (Subtask 3) |
| Symmetric | `a.equals(b)` and `b.equals(a)` always give the same answer. Part of what `equals` must promise. (Subtask 3) |
| Contract | A rule the compiler doesn't check but every library assumes. Breaking it gives wrong answers, not errors. Here: equal objects must have equal hash codes. |
| Mutable field | A field that can be changed after the object is created (not `final`). Dangerous inside `hashCode`. (Subtask 5) |
| Value object | A thing defined entirely by its contents. Two seat ids "A1" are the same seat; two `Money(10, "GBP")` are the same money. (Subtask 8) |
| Entity | A thing with its own identity, which stays the same when its contents change. Two customers named Sam aren't one customer. (Subtask 9) |
| `record` | A short way to declare a class that just holds data. You get `equals`, `hashCode`, `toString` and final fields for free. (Subtask 8) |

---

## Subtask 1 — The quiet failure: two identical seats in a `HashSet`

> **From the plan:** Using the plain `Seat` class with no `equals` or `hashCode`, put two
> logically identical seats into a `HashSet`. Print the size. It is **2**. Then call
> `set.contains(new Seat("A", 1))` and watch it return `false`. Sit with how quiet that
> failure is.

### What it's asking

1. Take the plain `Seat` class from Task F2.1: a row and a number, nothing else. Give it a
   constructor (Task F2.2) so you can write `new Seat("A", 1)`.
2. Make a `HashSet` and add two seats that are both "row A, seat 1". That's what "logically
   identical" means: two separate objects describing the same real seat.
3. Print the set's size. A set never holds the same thing twice, so you'd expect **1**. You
   get **2**.
4. Ask the set whether it contains seat A1. You'd expect `true`. You get `false`.
5. Notice that nothing warned you: no compile error and no exception, just wrong answers.

### The ideas

**Two reminders.**
- From F2.3: `==` asks "are these the very same object?". `.equals()` asks "do these mean the
  same thing?".
- From F2.4: a `Set` never holds the same item twice, and `contains()` tells you whether
  something is in it. A `HashSet` answers `contains()` fast, however many items it holds.

**Every class already has `equals` and `hashCode`.** In F2.7 you saw that every class
secretly extends `Object`. `Object` gives every class an `equals` method and a `hashCode`
method (and `toString`). If you don't write your own, your class uses `Object`'s versions:

| Method | `Object`'s version, which `Seat` uses right now |
| --- | --- |
| `equals(Object o)` | Exactly `==`: `true` only for the very same object. It never looks at the fields. |
| `hashCode()` | A number the JVM picks for that one object. It ignores the fields, so two seats with the same row and number get different numbers. |

People often say this number is the object's memory address. On your Java 21 it isn't. The
JVM generates it the first time it's needed and stores it with the object, so it never changes
for that object.

**You've seen a hash code before.** In F2.1, printing a seat gave something like
`Seat@6d06d69c`. That's `Object`'s `toString()`: the class name, then `@`, then the hash code
written in hexadecimal (base 16).

**How a `HashSet` stores and finds things.** Inside, a `HashSet` keeps a row of numbered slots
called **buckets**. Picture a wall of numbered drawers. A new `HashSet` starts with 16 buckets
and adds more as it fills up. Every `add(seat)` and every `contains(seat)` goes through the
same three steps:

1. **Pick a bucket.** Call `seat.hashCode()` and turn that number into a bucket number from 0
   to 15. (Subtask 7 shows exactly how.)
2. **Look only in that bucket.** The set never searches the other buckets. That's why it's
   fast.
3. **Check for a match there.** For each object already in that bucket, the set asks: is its
   hash code the same as mine? If so, does `equals` say `true`? Only a yes to both counts as a
   match.

`add` stores the seat only if step 3 found no match. `contains` returns `true` only if it found
one.

In short: **`hashCode()` decides where to look, and `equals()` confirms the match.** Every
subtask in this task comes back to that sentence.

### The code

```java
import java.util.HashSet;
import java.util.Set;

class Seat {
    String row;
    int number;

    Seat(String row, int number) {
        this.row = row;
        this.number = number;
    }
    // no equals() and no hashCode(), so Seat gets Object's versions
}

public class SeatDemo {
    public static void main(String[] args) {
        Set<Seat> taken = new HashSet<>();

        taken.add(new Seat("A", 1));
        taken.add(new Seat("A", 1));          // the same real seat again

        System.out.println("Size: " + taken.size());
        System.out.println("Is A1 taken? " + taken.contains(new Seat("A", 1)));
    }
}
```

Output:

```
Size: 2
Is A1 taken? false
```

The fields aren't `private` or `final`. That breaks the F2.2 habit on purpose: Subtask 5
changes `seat.number` directly.

### What happened, step by step

I traced this exact program and printed each seat's hash code, so these numbers are real.
Yours may be different, which doesn't change anything.

**First `add`:** `new Seat("A", 1)` creates seat object #1. Its hash code (from `Object`) is
366712642, which points to bucket 9. Bucket 9 is empty, so seat #1 goes in.

**Second `add`:** `new` always creates a new object (F2.1), so this is seat object #2. It has
the same row and number, but its own hash code, 1829164700, which points to bucket 10. Bucket
10 is empty, so there's nothing to compare against, and seat #2 goes in. The set now holds two
seats: **size 2**.

**`contains(new Seat("A", 1))`:** this is a third object, seat #3. Its hash code is 1554874502,
which points to bucket 11. Bucket 11 is empty, so the answer is **`false`**.

**What if two seats land in the same bucket?** With only 16 buckets, that happens. It's called
a **collision**, and it's normal: the set just does step 3. Here the hash codes would differ,
and even if they didn't, `Object`'s `equals` only matches the very same object. So the seats
would still count as different, and you'd get the same result: size 2, `contains` false.

**The set isn't broken.** It answered the question "do I contain *this exact object*?". You
meant "do I contain *seat A1*?". `Seat` never told Java what "the same seat" means, so Java
used `Object`'s meaning: the same object.

### How quiet the failure is

Look at what you didn't get: no compile error, no warning, no exception. The program printed
believable answers, and they were wrong.

In Zenith, "Is seat A1 already held?" is exactly this `contains` call. A wrong "no" means the
seat gets sold twice, and you find out only when two people turn up with tickets for A1.
Experiment C below shows it happening.

### Experiments

**Experiment A — look inside.** Add this at the end of `main` and run again:

```java
Seat a = new Seat("A", 1);
Seat b = new Seat("A", 1);
System.out.println("a == b: " + (a == b));
System.out.println("a.equals(b): " + a.equals(b));
System.out.println("a.hashCode(): " + a.hashCode());
System.out.println("b.hashCode(): " + b.hashCode());
System.out.println("a printed: " + a);
System.out.println("a's hash code in hex: " + Integer.toHexString(a.hashCode()));
System.out.println("The set: " + taken);
```

`Integer.toHexString(...)` writes a number in hexadecimal. The new lines of output:

```
a == b: false
a.equals(b): false
a.hashCode(): 1639705018
b.hashCode(): 1627674070
a printed: Seat@61bbe9ba
a's hash code in hex: 61bbe9ba
The set: [Seat@15db9742, Seat@6d06d69c]
```

- `a.equals(b)` is `false` even though the contents match, because `Object`'s `equals` is just
  `==`.
- `a` and `b` have different hash codes.
- The digits after `Seat@` are exactly the hash code in hex.
- The set holds two separate seats. `15db9742` and `6d06d69c` are seat #1's and seat #2's hash
  codes from the step-by-step (366712642 and 1829164700), written in hex. `6d06d69c` is even
  the number the plan shows in F2.1.

The numbers look random, but the JVM generates them with a fixed recipe. So the same program
usually prints the same numbers on every run, and changing the program changes them. Either
way, every object gets its own.

**Experiment B — the same object twice.** Add this at the end of `main`:

```java
Set<Seat> held = new HashSet<>();
Seat a1 = new Seat("A", 1);
held.add(a1);
held.add(a1);                          // the very same object again
System.out.println("Size: " + held.size());
System.out.println("Contains a1? " + held.contains(a1));
```

The new lines of output:

```
Size: 1
Contains a1? true
```

With the very same object, everything works: it has the same hash code, so the set looks in
the same bucket, and `equals` (which is `==`) says `true`. So the `HashSet` is working
correctly. It's comparing *objects*, and you want it to compare *seats*.

That doesn't help in a real system. Every booking request builds its own new `Seat` objects
from data that arrives over the network, so the set never gets handed the same object twice.

**Experiment C — sell the same seat twice.** This is the Zenith version of the bug. Add this
method inside `SeatDemo`, below `main` (not inside it):

```java
static void sell(Set<Seat> sold, Seat seat, String customer) {
    if (sold.contains(seat)) {
        System.out.println("Sorry " + customer + ", seat " + seat.row + seat.number + " is already sold");
    } else {
        sold.add(seat);
        System.out.println("Sold seat " + seat.row + seat.number + " to " + customer);
    }
}
```

Then add these lines at the end of `main`:

```java
Set<Seat> sold = new HashSet<>();
sell(sold, new Seat("A", 1), "Sam");
sell(sold, new Seat("A", 1), "Priya");
```

The new lines of output:

```
Sold seat A1 to Sam
Sold seat A1 to Priya
```

Two customers, one seat, and no error anywhere. Keep this experiment: after Subtask 2, the
second line becomes `Sorry Priya, seat A1 is already sold`.

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.8 — equals and hashCode: the quiet failure
- Every class inherits equals() and hashCode() from Object.
  - Object's equals is just ==: "the very same object?". It never looks at the fields.
  - Object's hashCode gives each object its own number. It ignores the fields too.
- A HashSet uses hashCode() to pick one bucket and looks only there. Something in that bucket
  is a match only if it has the same hash code AND equals() says true.
- Plain Seat: two new Seat("A", 1) objects get different hash codes and aren't equal, so the
  set keeps both (size 2), and contains(new Seat("A", 1)) is false.
- No error and no warning, just a wrong answer. In Zenith, that's the same seat sold twice.
- Seat@6d06d69c (from F2.1) is the class name plus the hash code in hex.
```

### Check yourself

1. `Seat` has no `equals` method in its code, yet `a.equals(b)` compiles. Where does the method
   come from, and what does it compare?
2. What three steps does a `HashSet` go through on every `add` and `contains`? Which step uses
   `hashCode()`, and which uses `equals()`?
3. Why is the size 2 and not 1? Explain it using buckets.
4. What's it called when two seats land in the same bucket? Would it have changed the result
   here?
5. In Experiment B, `contains(a1)` returned `true`. Why does that work when
   `contains(new Seat("A", 1))` doesn't?
6. Why is this kind of failure more dangerous than one that throws an exception?

---

## Subtask 2 — Write `equals` and `hashCode` by hand

> **From the plan:** Implement both methods by hand. `Objects.equals(this.row, other.row)` and
> `Objects.hash(row, number)` keep it short. Re-run. Both problems disappear.

### What it's asking

1. Inside `Seat`, write your own `equals`, which says two seats are equal when they have the
   same row and the same number.
2. Write your own `hashCode`, which builds a number from the row and the number, so equal
   seats always get the same hash code.
3. Use two helpers from Java's `Objects` class, `Objects.equals` and `Objects.hash`, to keep
   both methods short.
4. Run `SeatDemo` again. The size becomes 1 and `contains` returns `true`.

### The ideas

**This is overriding, like `price()` in F2.7.** `Seat` already has `equals` and `hashCode`
from `Object`. Writing your own replaces them, so the F2.7 rules apply:
- Use exactly the same name and parameters as `Object`'s versions: `equals(Object o)` and
  `hashCode()`.
- Put `@Override` on both.
- Make both `public`. `Object`'s versions are `public`, and an override can't be more private
  (F2.6).

**Why the set will use your versions.** `HashSet`'s code was written long before your `Seat`
existed. It just calls `hashCode()` and `equals()` on whatever it's given, and dynamic
dispatch (F2.7) runs the object's own versions. So once `Seat` has its own, every `HashSet`
and `HashMap` uses them automatically.

**Your two jobs:**
- **`equals`** decides what "the same seat" means. Here: same row and same number.
- **`hashCode`** makes sure seats that are equal get the same number, so they land in the same
  bucket and `equals` gets the chance to compare them.

### The code

Add `import java.util.Objects;` at the top of `SeatDemo.java`, and the two methods inside
`Seat`, below the constructor:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class Seat {
    String row;
    int number;

    Seat(String row, int number) {
        this.row = row;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Seat)) {          // not a Seat (this includes null)
            return false;
        }
        Seat other = (Seat) o;
        return Objects.equals(this.row, other.row) && this.number == other.number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, number);    // the same fields equals compares
    }
}
```

`main` stays exactly as it was in Subtask 1. Output:

```
Size: 1
Is A1 taken? true
```

Both problems are gone.

If you forget the import, the compiler can't find `Objects`:

```
error: cannot find symbol
        return Objects.equals(this.row, other.row) && this.number == other.number;
               ^
  symbol:   variable Objects
  location: class Seat
```

### `equals`, line by line

**`public boolean equals(Object o)`.** The parameter type is `Object`, not `Seat`, because
`Object`'s method has an `Object` parameter, and that's the one you're replacing. A `HashSet`
can hold anything, so it calls `equals` with an `Object`. Writing `equals(Seat other)` instead
is a classic bug (Experiment E).

If you leave out `public`, out of habit from F2.7's `int price()`, you get:

```
error: equals(Object) in Seat cannot override equals(Object) in Object
    boolean equals(Object o) {
            ^
  attempting to assign weaker access privileges; was public
```

**`if (!(o instanceof Seat)) { return false; }`.** `o` could be anything: a `Seat`, a
`String`, a `Ticket`, or `null`. `o instanceof Seat` is `true` only when `o` really is a `Seat`
object, and it's `false` for `null`. So anything that isn't a `Seat` gets the answer `false`
straight away:
- `a.equals(null)` returns `false`.
- `a.equals("A1")` returns `false`. The text "A1" isn't a `Seat`, even though it reads like
  one.

Without this check, both calls crash. `a.equals("A1")` throws

```
java.lang.ClassCastException: class java.lang.String cannot be cast to class Seat (...)
```

and `a.equals(null)` throws a `NullPointerException`. `equals` should never crash. For
anything that isn't a `Seat`, the right answer is simply `false`.

**`Seat other = (Seat) o;`.** This is a cast. The variable `o` is labelled `Object`, and the
`Object` label has no `row` or `number` (F2.7's labelled box: the label decides what you may
use). The cast gives you a `Seat`-labelled variable pointing at the same object, so you can
read `other.row` and `other.number`. It's safe because the line above already checked that
`o` is a `Seat`.

**`Objects.equals(this.row, other.row)`.** Compares the two rows with `.equals()`, never `==`,
because strings must be compared with `.equals()` (F2.3). Experiment F shows `==` going wrong.
`Objects.equals` also copes with `null` (F2.3): two `null` rows count as equal, and one `null`
row doesn't crash anything. Writing `this.row.equals(other.row)` instead would crash when
`this.row` is `null`:

```
java.lang.NullPointerException: Cannot invoke "String.equals(Object)" because "this.row" is null
```

**`this.number == other.number`.** `number` is an `int`, a primitive, so `==` compares the
values. This is where `==` is right (F2.3).

**`&&`.** Both parts must be true: same row **and** same number.

**A shorter way to write the check and the cast (Java 16 and later).** You'll often see this
version. It means exactly the same thing:

```java
@Override
public boolean equals(Object o) {
    if (!(o instanceof Seat other)) {
        return false;
    }
    return Objects.equals(this.row, other.row) && this.number == other.number;
}
```

`o instanceof Seat other` checks that `o` is a `Seat` and, if it is, gives you a `Seat`
variable called `other` in the same step. Because the `if` returns early whenever `o` isn't a
`Seat`, Java knows `other` is safe to use on the lines after it.

### `hashCode`, line by line

**`public int hashCode()`.** Same name, no parameters, and `public`, matching `Object`'s.

**`return Objects.hash(row, number);`.** `Objects.hash` takes the fields you list and combines
their hash codes into one `int`. It's plain arithmetic, so the same row and number always give
the same answer, on every run and every computer. For seat A1:

1. Start with 1.
2. Multiply by 31 and add the row's hash code. `"A".hashCode()` is 65, the character code of
   `A`. So 1 × 31 + 65 = 96.
3. Multiply by 31 and add the number's hash code. An `int`'s hash code is just the number. So
   96 × 31 + 1 = **2977**.

So every `new Seat("A", 1)` has hash code 2977. In Subtask 1, by contrast, each A1 seat got
its own unrelated number. (31 is simply a number that mixes the fields together well. You
never have to work this out yourself; it's here to show there's no magic.)

**Build `hashCode` from the same fields as `equals`.** `equals` compares `row` and `number`,
so `hashCode` uses `row` and `number`. Then any two seats that `equals` calls the same are
guaranteed the same hash code, and so the same bucket. That guarantee is the **contract**:
**equal objects must have equal hash codes.** Subtask 6 writes the whole contract out.

### What happens now, step by step

**First `add`:** seat #1's `hashCode()` is now yours: 2977, which points to bucket 1. Bucket 1
is empty, so seat #1 goes in.

**Second `add`:** seat #2 is still a separate object, but its hash code is also 2977, so it
points to the same bucket 1. This time step 3 has something to check. Seat #1 has the same
hash code, so the set calls `equals`: row `"A"` equals `"A"`, and 1 == 1, so it returns
`true`. That's a match, so seat #2 is **not** added (`add` returns `false` to tell you). The
size is **1**.

**`contains(new Seat("A", 1))`:** hash code 2977, bucket 1. Seat #1 is there with the same hash
code, and `equals` says `true`. The answer is **`true`**.

Both methods were needed. `hashCode` sent the second seat to the bucket where the first one
already was, and `equals` recognised them as the same seat once they met. Subtask 4 removes
`hashCode` to show what happens with only one of the two.

### Re-run your Subtask 1 experiments

**Experiment A (look inside)** now prints:

```
a == b: false
a.equals(b): true
a.hashCode(): 2977
b.hashCode(): 2977
a printed: Seat@ba1
a's hash code in hex: ba1
The set: [Seat@ba1]
```

- `a == b` is still `false`, because they're still two separate objects. But `a.equals(b)` is
  now `true`, because they mean the same seat. It's the same split you saw with two `String`s
  in F2.3.
- Both have hash code 2977.
- `Object`'s `toString()` uses `hashCode()`, so the printout now shows your hash code: 2977 is
  `ba1` in hex. The printout still doesn't tell you much; Subtask 8 fixes that.
- The set holds one seat.

**Experiment B (the same object twice)** prints the same as before: `Size: 1` and
`Contains a1? true`. It already worked.

**Experiment C (sell the same seat twice)** now prints:

```
Sold seat A1 to Sam
Sorry Priya, seat A1 is already sold
```

### Experiments

**Experiment D — different seats stay different.** Writing `equals` didn't make all seats
equal. Add this at the end of `main`:

```java
Set<Seat> seats = new HashSet<>();
seats.add(new Seat("A", 1));
seats.add(new Seat("A", 2));
seats.add(new Seat("B", 1));
seats.add(new Seat("A", 1));                 // A1 again
System.out.println("Seats: " + seats.size());
System.out.println("A1: " + new Seat("A", 1).hashCode());
System.out.println("A2: " + new Seat("A", 2).hashCode());
System.out.println("B1: " + new Seat("B", 1).hashCode());
```

The new lines of output:

```
Seats: 3
A1: 2977
A2: 2978
B1: 3008
```

A1, A2 and B1 are kept as three seats, and only the second A1 is turned away. Different seats
got different hash codes here. That's usual, but it isn't a requirement; Subtask 6 explains.

**Experiment E — the `equals(Seat other)` trap.** This one catches a lot of people, including
experienced ones. Replace your `equals` with this version. It takes a `Seat` instead of an
`Object`, and has no `@Override`:

```java
public boolean equals(Seat other) {
    return Objects.equals(this.row, other.row) && this.number == other.number;
}
```

Keep `hashCode` as it is. Add these two lines at the end of `main` (they use `b` from
Experiment A):

```java
Object bAsObject = b;                        // b, seen the way a HashSet sees it
System.out.println("a.equals(bAsObject): " + a.equals(bAsObject));
```

Run it. These are the lines to look at:

```
Size: 2
Is A1 taken? false
a.equals(b): true
The set: [Seat@ba1, Seat@ba1]
Sold seat A1 to Sam
Sold seat A1 to Priya
Seats: 4
a.equals(bAsObject): false
```

Your own test, `a.equals(b)`, says `true`, yet every set is broken: size 2, the seat sold
twice, and 4 seats instead of 3. Here's why:
- A different parameter type makes `equals(Seat other)` a **new method with the same name**
  (overloading, F2.2), not a replacement. `Seat` now has two `equals` methods: `Object`'s
  `equals(Object)`, which still compares objects, and your `equals(Seat)`.
- When a method is overloaded, the compiler picks the version from the *declared* types of the
  arguments (compile-time polymorphism). In `a.equals(b)`, `b` is declared as `Seat`, so your
  method runs and says `true`.
- `HashSet` only ever calls `equals(Object)`, just like `a.equals(bAsObject)` does. That
  reaches `Object`'s version, which says `false` for two different objects.

So your quick test passes and every collection fails, which is what makes this bug hard to
spot. Put `@Override` on it and the compiler catches it immediately:

```
error: method does not override or implement a method from a supertype
    @Override
    ^
```

It's the same safety net as F2.7's `prise()` typo. Change `equals` back to the `Object`
version before moving on. The `bAsObject` line then prints `true`.

**Experiment F — comparing the rows with `==`.** In your correct `equals`, change
`Objects.equals(this.row, other.row)` to `this.row == other.row`. Then add this at the end of
`main`:

```java
Set<Seat> typed = new HashSet<>();
typed.add(new Seat("A", 1));
typed.add(new Seat("A", 1));
System.out.println("Rows typed in the code: size " + typed.size());

Set<Seat> fromRequests = new HashSet<>();
fromRequests.add(new Seat("A", 1));
fromRequests.add(new Seat(new String("A"), 1));    // a row built while the program runs
System.out.println("One row built at run time: size " + fromRequests.size());
```

The new lines of output:

```
Rows typed in the code: size 1
One row built at run time: size 2
```

The first one works only by luck. Both `"A"`s are typed into the code, so the string pool
(F2.3) makes them the very same `String` object, and `==` says `true`. In the second, the row
is built while the program runs, as it would be when it arrives in a booking request. That's a
different `String` object, so `==` says `false` and the set keeps both. It's F2.3's "passes
the test, fails in production" bug, hiding inside `equals`. Change it back to
`Objects.equals`, and both lines print `size 1`.

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.8 — writing equals and hashCode
- Both are overrides of Object's methods, so: public, @Override, and the exact signatures
  public boolean equals(Object o) and public int hashCode().
- equals must take an Object. equals(Seat other) is a separate overload that HashSet never
  calls, so sets keep comparing objects. @Override catches this mistake.
- Inside equals: if o isn't a Seat (this covers null), return false. Then cast, and compare
  every field that defines "the same seat": Objects.equals for objects like String (never ==),
  == for primitives like int.
- hashCode: return Objects.hash(row, number), built from exactly the fields equals compares.
  Equal seats then always get the same hash code (A1 is always 2977), so they land in the same
  bucket, where equals recognises them.
- After the fix, two new Seat("A", 1) objects are still two objects (== is false), but they're
  equal, so the set keeps one and contains() finds it.
```

### Check yourself

1. Why must `equals` take an `Object` and not a `Seat`? What happens if you write
   `equals(Seat other)` without `@Override`, and what happens with it?
2. What does the `instanceof` check protect against? Give two arguments it handles.
3. Why `Objects.equals` for the row but `==` for the number?
4. Work out seat B1's hash code by hand (`"B".hashCode()` is 66), then check your answer
   against Experiment D.
5. Why must `hashCode` be built from the same fields as `equals`?
6. After the fix, `a == b` is still `false`. Why, and why doesn't the set care?

### Your question: two `new` seats with the same hash code — one object or two?

> If I create two `Seat` objects with `new`, with the same contents, and `hashCode` returns the
> same number for both, will both objects be created, or only one?

**Both are created.** `new` always creates a brand-new object, every time it runs. `equals`
and `hashCode` don't even run when an object is created. They're ordinary methods that run
only when something calls them, such as a `HashSet` deciding whether to store an object.

The "only one" part comes from the set. The set keeps the first seat and turns the second one
away, but the second object still exists. Think of photocopying a ticket: now there are two
pieces of paper (two objects), both saying A1 (equal, with the same hash code). The booking
list (the set) writes A1 down once.

The proof:

```java
Seat first = new Seat("A", 1);
Seat second = new Seat("A", 1);

System.out.println("Hash codes: " + first.hashCode() + " and " + second.hashCode());
System.out.println("first.equals(second): " + first.equals(second));
System.out.println("first == second: " + (first == second));
System.out.println("JVM's own numbers: " + System.identityHashCode(first) + " and " + System.identityHashCode(second));

Set<Seat> taken = new HashSet<>();
System.out.println("add(first): " + taken.add(first));
System.out.println("add(second): " + taken.add(second));
System.out.println("Size: " + taken.size());
for (Seat s : taken) {
    System.out.println("Is the seat in the set the first object? " + (s == first));
}

second.number = 99;
System.out.println("After changing second: first.number = " + first.number + ", second.number = " + second.number);
```

Output:

```
Hash codes: 2977 and 2977
first.equals(second): true
first == second: false
JVM's own numbers: 1725154839 and 1670675563
add(first): true
add(second): false
Size: 1
Is the seat in the set the first object? true
After changing second: first.number = 1, second.number = 99
```

- `first == second` is `false`. `==` asks "same object?" (F2.3), so these are two objects.
- `System.identityHashCode(x)` gives the number `Object`'s own `hashCode()` would have
  returned, the Subtask 1 kind. The two seats got different ones (your numbers may differ from
  mine), which fits with them being two objects. Your `hashCode()` is just a method you wrote;
  it doesn't change what the objects are. (`==` doesn't use either number; see the next
  question.)
- The set kept `first` and rejected `second` (`add` returned `false`). It doesn't swap in the
  newer one.
- Changing `second` didn't change `first`. If they were one object, both variables would show
  99 (F2.1). `second` isn't in the set, so changing it is harmless here; Subtask 5 shows what
  happens when you change a seat that *is* in a set.

**What happens to the rejected object?** In `SeatDemo` you write `taken.add(new Seat("A", 1))`
without keeping the seat in a variable. Once the set turns it away, nothing refers to it any
more, so the garbage collector removes it later (F2.1). If a variable still refers to it, like
`second` above, it stays alive like any other object.

**Does Java ever reuse an object instead of creating a new one?** Only in the special cases you
met in F2.3: string literals typed into your code (the string pool), and small `Integer` values
from -128 to 127 (the Integer cache). Neither has anything to do with your `hashCode`, and an
explicit `new`, such as `new String("A")`, always creates a fresh object anyway.

### Your question: does `==` compare the JVM numbers?

> So `==` compares the JVM numbers and not the hash code numbers, right?

**Half right.** `==` doesn't use your `hashCode()` numbers. But it doesn't use the JVM numbers
either. For objects, `==` compares the **references themselves**: it's `true` only when both
variables point at the very same object. It doesn't call any method, and it doesn't read any
number stored in the objects.

Using F2.1's picture, a variable holds the address of an object, like an address written on a
slip of paper:

| | What it is | Used by |
| --- | --- | --- |
| The reference | The address on the slip of paper: *where* the object is | `==` |
| The JVM number (`System.identityHashCode`) | A number the JVM writes on the object the first time something asks for it | `Object`'s `hashCode()`, when you don't write your own |
| Your `hashCode()` | A number you calculate from the fields (row and number) | `HashSet` and `HashMap`, once you override it |

`==` only checks whether the two slips of paper have the same address on them. It never looks
at either number.

**Why it looked like `==` used the JVM numbers.** Two different objects almost always get
different JVM numbers, so in the previous demo the numbers and `==` told the same story.
"Almost" is the important word. This loop keeps creating seats until two different ones get
the same JVM number:

```java
Map<Integer, Seat> seatByJvmNumber = new HashMap<>();
for (int i = 1; i <= 1_000_000; i++) {
    Seat seat = new Seat("A", i);
    int jvmNumber = System.identityHashCode(seat);
    if (seatByJvmNumber.containsKey(jvmNumber)) {
        Seat earlier = seatByJvmNumber.get(jvmNumber);
        System.out.println("After " + i + " seats, two of them got the same JVM number: " + jvmNumber);
        System.out.println("They are seats " + earlier.row + earlier.number + " and " + seat.row + seat.number);
        System.out.println("earlier == seat: " + (earlier == seat));
        break;
    }
    seatByJvmNumber.put(jvmNumber, seat);
}
```

To run it, add it at the end of `main`, and add `import java.util.HashMap;` and
`import java.util.Map;` at the top. (`1_000_000` is one million; the underscores only make it
easier to read.) Output:

```
After 105842 seats, two of them got the same JVM number: 2134400190
They are seats A62355 and A105842
earlier == seat: false
```

Your numbers will probably differ, but you'll get a pair. Seats A62355 and A105842 have the
same JVM number, and `==` still says `false`. If `==` compared JVM numbers, it would have said
`true`.

**Another way to see it.** The JVM only creates an object's number when something asks for
it, so most objects never get one. `==` still works on all of them, because it doesn't need
one.

This is also why the JVM number can't simply be the memory address. The garbage collector
moves objects around in memory while the program runs, but an object's hash code must never
change, so the JVM writes down a separate number that stays the same.

**The three comparisons side by side:**
- `a == b` is `true` only for the very same object. You can't override `==`, so no class can
  change what it means.
- Equal JVM numbers usually mean the same object, but not always, as the demo shows.
- Equal `hashCode()` results prove nothing on their own. Every A1 seat gets 2977, and
  different seats can share a number too (Subtask 6). That's why a `HashSet` always confirms
  with `equals`.

---

## Subtask 3 — Let IntelliJ generate them, and read every line

> **From the plan:** Let IntelliJ generate them too (`⌘N` in a class, then "equals() and
> hashCode()"), and read what it produced line by line. You should recognise every part,
> including the `getClass() != o.getClass()` check.

### What it's asking

1. Get IntelliJ to write `equals` and `hashCode` for `Seat`, instead of writing them yourself.
2. Read every line it produces and match it to something you already know from Subtask 2.
3. Understand the one genuinely new part, `getClass() != o.getClass()`, which IntelliJ uses
   where you used `instanceof`.

In real work you'll usually generate these methods rather than type them. The point of this
subtask is that you can *read* what was generated and spot when it's wrong for your class.

### How to generate them (IntelliJ IDEA 2026.1, the version on your Mac)

1. Open the `foundations` folder in IntelliJ (**File → Open**). If a banner says no JDK is set
   up, pick your Java 21.
2. Open `SeatDemo.java` and click on an empty line inside `class Seat`, below the constructor.
3. Press **`⌘N`** (or **Code → Generate…**) and choose **equals() and hashCode()**.
4. Your hand-written methods are still there, so IntelliJ asks: *"Methods 'boolean
   equals(Object)' and 'int hashCode()' are already defined for class Seat. Do you want to
   delete them and proceed?"* Say **Yes**. Your version is still in the Subtask 2 section if you
   want to compare.
5. On the first page, keep the defaults:
   - **Template:** IntelliJ Default
   - **For class type comparison in equals() method generate:** getClass() comparison
     expression (the other choice is instanceof expression)
   - **Use getters when available:** unticked
6. **Choose fields to be included in equals():** tick `row` and `number`.
7. **Choose fields to be included in hashCode():** tick `row` and `number`.
8. **Select all non-null fields:** tick nothing. Ticking `row` would promise IntelliJ that the
   row is never `null`, and it would then write `row.equals(seat.row)`, which crashes on a
   `null` row (Subtask 2).
9. Finish the wizard.

### What IntelliJ writes

I built this from the template files inside your IntelliJ, then compiled and ran it on Java
21. The spacing in your file may differ slightly.

```java
@Override
public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    Seat seat = (Seat) o;
    return number == seat.number && Objects.equals(row, seat.row);
}

@Override
public int hashCode() {
    int result = Objects.hashCode(row);
    result = 31 * result + number;
    return result;
}
```

Many tutorials, and older IntelliJ versions, also put `if (this == o) return true;` as the
first line. It's only a shortcut for "the very same object" (and it gives the same answer
without it), so your IntelliJ version leaves it out.

### `equals`, line by line

**`@Override public boolean equals(Object o)`.** The same signature you wrote. IntelliJ calls
the parameter `o`.

**`if (o == null || getClass() != o.getClass()) return false;`.** This replaces your
`instanceof` check. Taking it piece by piece:
- **No `{ }`.** When an `if` controls just one statement, the braces are optional. This is the
  same as `if (...) { return false; }`.
- **`o == null`** comes first, because the next part calls `o.getClass()`, and calling a method
  on `null` throws a `NullPointerException`. Your `instanceof` handled `null` for free; this
  version needs the extra check.
- **`||`** stops as soon as the left side is `true`. So if `o` is `null`, `o.getClass()` never
  runs.
- **`getClass()`** means `this.getClass()`. Every object can tell you its actual class while
  the program runs. For a `Seat` it's `Seat`, and for a subclass object like `VipSeat` it's
  `VipSeat`, not `Seat`.
- **`getClass() != o.getClass()`** asks: is `o` *exactly* the same class as me? A subclass
  doesn't count. It uses `!=` on objects, which is correct here: the JVM keeps exactly one
  `Class` object per class, so comparing references (rule 9) is the right test.

**`Seat seat = (Seat) o;`.** The same cast as yours. IntelliJ names the variable after the
class. It's safe because the line above proved `o` is exactly a `Seat`.

**`return number == seat.number && Objects.equals(row, seat.row);`.** The same two comparisons
as yours, in the opposite order. IntelliJ always puts primitive fields first. Comparing two
`int`s is cheaper than comparing two strings, and `&&` stops at the first `false`, so a
different number is spotted without ever looking at the row. It writes `number` instead of
`this.number`; when nothing else has that name, they mean the same thing.

### `hashCode`, line by line

**`int result = Objects.hashCode(row);`.** Note: `Objects.hashCode` (singular, one value), not
`Objects.hash` (any number of values). It returns `row.hashCode()`, or 0 if `row` is `null`.
For `"A"` that's 65.

**`result = 31 * result + number;`.** The same multiply-by-31-and-add step you saw inside
`Objects.hash`: 31 × 65 + 1 = 2016.

**`return result;`.** So IntelliJ's version gives seat A1 the hash code **2016**, where yours
gave **2977**. Both are correct. `Objects.hash` simply starts from 1 instead of the first
field, which adds 31 × 31 = 961 (2016 + 961 = 2977). The only rule is that equal seats get
equal numbers, and both versions build the number from the same fields `equals` compares.

### Re-run `SeatDemo`

Everything behaves exactly as in Subtask 2. Only the numbers change:

```
Size: 1
Is A1 taken? true
a == b: false
a.equals(b): true
a.hashCode(): 2016
b.hashCode(): 2016
a printed: Seat@7e0
a's hash code in hex: 7e0
The set: [Seat@7e0]
Size: 1
Contains a1? true
Sold seat A1 to Sam
Sorry Priya, seat A1 is already sold
Seats: 3
A1: 2016
A2: 2017
B1: 2047
```

If you still have Experiments E or F from Subtask 2 in your code, they no longer apply, because
IntelliJ replaced your `equals`.

### The one real difference: `getClass()` or `instanceof`?

For `Seat` alone, the two checks give identical answers. They differ only when a **subclass**
exists. To see it, add this class to `SeatDemo.java`, a VIP seat with one extra field, whose
`equals` also compares that field:

```java
class VipSeat extends Seat {
    boolean lounge;

    VipSeat(String row, int number, boolean lounge) {
        super(row, number);
        this.lounge = lounge;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VipSeat)) {
            return false;
        }
        VipSeat other = (VipSeat) o;
        return super.equals(other) && this.lounge == other.lounge;
    }
    // hashCode() is inherited from Seat: built from row and number
}
```

Then put these lines in `main`:

```java
Seat plain = new Seat("A", 1);
VipSeat vip = new VipSeat("A", 1, true);
System.out.println("plain.equals(vip): " + plain.equals(vip));
System.out.println("vip.equals(plain): " + vip.equals(plain));

Set<Seat> holdsPlain = new HashSet<>();
holdsPlain.add(plain);
System.out.println("Set holding plain A1, contains(vip)? " + holdsPlain.contains(vip));

Set<Seat> holdsVip = new HashSet<>();
holdsVip.add(vip);
System.out.println("Set holding VIP A1, contains(plain)? " + holdsVip.contains(plain));
```

**With your Subtask 2 `equals` in `Seat` (the `instanceof` check):**

```
plain.equals(vip): true
vip.equals(plain): false
Set holding plain A1, contains(vip)? false
Set holding VIP A1, contains(plain)? true
```

- `plain.equals(vip)` is `true`: `Seat`'s `instanceof` check accepts a `VipSeat` (it *is* a
  `Seat`), and the row and number match.
- `vip.equals(plain)` is `false`: `VipSeat`'s check rejects a plain `Seat`.
- So the answer depends on which object you ask. That breaks a promise `equals` must keep:
  it must be **symmetric**, giving the same answer both ways round.
- The sets show why it matters. "Is A1 taken?" gets a different answer depending on which kind
  of seat was stored and which kind you ask with. (`contains(x)` calls `x.equals(...)` on what's
  already in the bucket.)

**With IntelliJ's `equals` in `Seat` (the `getClass()` check):**

```
plain.equals(vip): false
vip.equals(plain): false
Set holding plain A1, contains(vip)? false
Set holding VIP A1, contains(plain)? false
```

Both directions agree. A plain seat and a VIP seat are never equal, because they're different
classes. That's why IntelliJ defaults to `getClass()`: it keeps `equals` symmetric even when a
subclass overrides it.

**The cost of `getClass()`.** It also rejects a subclass that adds nothing. With `class
PlainSubSeat extends Seat { }` (just a constructor), `plain.equals(new PlainSubSeat("A", 1))`
is `false`, even though nothing about the seat differs. That breaks F2.7's Liskov rule: a
child object should work wherever the parent works.

IntelliJ shows its own comparison of the two choices in the wizard:

| | `instanceof` | `getClass()` |
| --- | --- | --- |
| A subclass object can equal a parent object | yes | no |
| A subclass can override the generated `equals` without breaking its contract | no | yes |
| No extra `null` check needed | yes | no |
| Obeys the Liskov substitution principle (F2.7) | yes | no |

Its help text adds that accepting subclasses (`instanceof`) "may be necessary ... with
frameworks which generate Proxy subclasses, like Hibernate". You'll meet those proxies when the
project starts using a database.

**What to do in practice.** Neither check is perfect, so avoid the situation. For a value
class like `Seat`, mark it `final` (F2.7). Then no subclass can exist, the two checks give the
same answers, and the whole question disappears:

```
error: cannot inherit from final Seat
class VipSeat extends Seat {
                      ^
```

If you need a "VIP seat", hold a `Seat` inside it (composition, F2.7) instead of extending it.
Records, in Subtask 8, are `final` automatically.

### Experiments

**Experiment A — the other template.** Generate again, but choose the template
**java.util.Objects.equals() and hash() (Java 7 and higher)**. You get the same `equals`
(without the blank line), and a `hashCode` that's identical to the one you wrote by hand:

```java
@Override
public int hashCode() {
    return Objects.hash(row, number);
}
```

Re-run, and the hash codes go back to 2977. The template only changes how the number is
calculated, never what `equals` decides.

**Experiment B — make `Seat` final.** Write `final class Seat`, keep the `VipSeat` class, and
compile. You get the `cannot inherit from final Seat` error above. Delete `VipSeat` and its
lines in `main` afterwards (or leave `Seat` non-final, as the later subtasks don't need either).

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.8 — reading IDE-generated equals and hashCode
- IntelliJ (⌘N → equals() and hashCode()) writes the same logic I wrote by hand:
  - if (o == null || getClass() != o.getClass()) return false;  ← null check + exact class
  - cast, then compare every field: == for primitives (first, as they're cheaper),
    Objects.equals for objects
  - hashCode built from the same fields: result = 31 * result + field
- Its A1 hash code is 2016, mine was 2977. Both fine: only "equal → same number" matters.
- getClass(): only exactly the same class can be equal. instanceof: subclasses count too.
  - instanceof + a subclass that adds a field to equals = not symmetric
    (plain.equals(vip) true, vip.equals(plain) false), so set lookups depend on who asks.
  - getClass() keeps it symmetric, but even an empty subclass is never equal (breaks LSP).
- Practical answer: make value classes final (or records), and use composition instead of
  subclassing them. Then both checks behave the same.
```

### Check yourself

1. Why does IntelliJ's version need `o == null` when your `instanceof` version didn't?
2. What does `getClass()` return for a `VipSeat` object? Why is `!=` correct for comparing two
   `Class` objects?
3. Why does IntelliJ compare `number` before `row`?
4. IntelliJ's A1 hash code is 2016 and yours was 2977. Why are both correct?
5. With `instanceof`, `plain.equals(vip)` was `true` but `vip.equals(plain)` was `false`. Which
   rule does that break, and what goes wrong in a `HashSet` because of it?
6. How does marking `Seat` as `final` make the `instanceof` versus `getClass()` question go
   away?

---

## Subtask 4 — Break it on purpose, version one: `equals` without `hashCode`

> **From the plan:** Delete `hashCode` and keep `equals`. `List.contains` still works, `HashSet`
> does not. Explain why: the list checks every element with `equals`, the set goes to one bucket
> first and never finds the object.

### What it's asking

1. Delete the `hashCode` method from `Seat`. Keep `equals`, so seats still know when they're
   the same seat.
2. Run `SeatDemo` and watch the `HashSet` problems from Subtask 1 come back.
3. Put seats in a `List` and see that `list.contains` still finds seat A1.
4. Explain the difference: a list asks every element "are you equal?", while a set only looks
   in one bucket.

This is the most common real-world version of the bug. Someone writes `equals`, forgets
`hashCode`, a quick test like `a.equals(b)` passes, and the class is quietly broken in every
set and map.

### The code

Delete `hashCode` from `Seat`. With IntelliJ's version from Subtask 3, the class becomes:

```java
class Seat {
    String row;
    int number;

    Seat(String row, int number) {
        this.row = row;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Seat seat = (Seat) o;
        return number == seat.number && Objects.equals(row, seat.row);
    }
    // hashCode() deleted, so Seat gets Object's version again
}
```

It compiles with no error and no warning. (IntelliJ has an inspection called *"'equals()' and
'hashCode()' not paired"* that would flag this, but in your version it's switched off by
default. You can turn it on under **Settings → Editor → Inspections → Java → Probable bugs**.)

Run `SeatDemo`. The lines to look at:

```
Size: 2
Is A1 taken? false
a == b: false
a.equals(b): true
a.hashCode(): 1639705018
b.hashCode(): 1627674070
The set: [Seat@15db9742, Seat@6d06d69c]
Sold seat A1 to Sam
Sold seat A1 to Priya
```

Every Subtask 1 problem is back: size 2, A1 not found, A1 sold twice. But look at the fourth
line: `a.equals(b)` is **`true`**. Your `equals` is working perfectly, and the set ignores it.
The hash codes are `Object`'s per-object numbers again, the same ones as in Subtask 1.

This breaks the contract: `a` and `b` are equal, but their hash codes are different.

### Now try a `List`

Add these imports at the top of the file:

```java
import java.util.ArrayList;
import java.util.List;
```

And these lines at the end of `main`:

```java
List<Seat> list = new ArrayList<>();
list.add(new Seat("A", 1));
list.add(new Seat("B", 2));
System.out.println("List contains A1? " + list.contains(new Seat("A", 1)));

Set<Seat> set = new HashSet<>();
set.add(new Seat("A", 1));
set.add(new Seat("B", 2));
System.out.println("Set contains A1? " + set.contains(new Seat("A", 1)));
```

The new lines of output:

```
List contains A1? true
Set contains A1? false
```

The same seats, the same question, and two different answers.

### Why the list works

`ArrayList.contains(x)` walks through the list from the first element to the last, asking
each one: `x.equals(element)`? It stops at the first `true`. It never calls `hashCode()` at
all, so a missing `hashCode` doesn't affect it. Your `equals` is all it needs.

The price is speed: it may check every element. With 10 seats that's nothing; with 50,000
it's the slow loop you timed in F2.4.

### Why the set doesn't

`HashSet` is fast precisely because it doesn't check every element. Remember its three steps
from Subtask 1:

1. **Pick a bucket** using `hashCode()`.
2. **Look only in that bucket.**
3. **Check for a match there:** same hash code, *and then* `equals`.

Without your `hashCode`, step 1 uses `Object`'s per-object number:
- The A1 seat in the set got one number and was filed in its bucket.
- The A1 seat you ask about is a new object with a different number, so it points to a
  different bucket (in this run, an empty one). The set looks there, finds nothing, and
  answers `false`. It never sees the stored A1 at all.
- Even when the two land in the same bucket (a collision, which does happen), step 3 checks
  the hash codes first. They differ, so the set moves on without ever calling `equals`.

Your `equals` would have said "yes, that's the same seat", but the set never asks it. That's
what "the set goes to one bucket first and never finds the object" means.

(Very rarely, two different objects get the same `Object` hash code, as you saw in Subtask 2's
million-seat loop. Then the set would call `equals` and find the seat. So this bug isn't even
consistent: it almost always says `false`, and very occasionally `true`.)

### Experiments

**Experiment A — watch who calls `equals`.** Add a print as the first line inside `equals`:

```java
System.out.println("  equals called: " + row + number + " vs " + (o == null ? "null" : ((Seat) o).row + ((Seat) o).number));
```

(The extra code after `vs` only prints the other seat's row and number. It assumes `o` is a
`Seat`, which is fine for this experiment.) Then run this:

```java
List<Seat> list = new ArrayList<>();
list.add(new Seat("C", 3));
list.add(new Seat("B", 2));
list.add(new Seat("A", 1));
System.out.println("list.contains(A1):");
System.out.println("  answer: " + list.contains(new Seat("A", 1)));

Set<Seat> set = new HashSet<>();
set.add(new Seat("C", 3));
set.add(new Seat("B", 2));
set.add(new Seat("A", 1));
System.out.println("set.contains(A1):");
System.out.println("  answer: " + set.contains(new Seat("A", 1)));
```

Output:

```
list.contains(A1):
  equals called: A1 vs C3
  equals called: A1 vs B2
  equals called: A1 vs A1
  answer: true
set.contains(A1):
  answer: false
```

The list called `equals` three times, once per element, until it found A1. The set didn't
call `equals` even once. This is the whole subtask in four lines. Remove the print afterwards.

**Experiment B — a `HashMap` has the same bug.** A `HashSet` is actually a `HashMap` inside
(Subtask 7), so maps fail the same way. Add `import java.util.HashMap;` and
`import java.util.Map;`, then:

```java
Map<Seat, String> holder = new HashMap<>();
holder.put(new Seat("A", 1), "Sam");
System.out.println("Who holds A1? " + holder.get(new Seat("A", 1)));
```

Output:

```
Who holds A1? null
```

Sam's booking is in the map, but looking it up with an equal seat finds nothing. `get`
returns `null`, the same quiet "not there" as `contains` returning `false`.

### Put `hashCode` back

Subtask 5 needs both methods. Put IntelliJ's `hashCode` back (or regenerate both with `⌘N`):

```java
@Override
public int hashCode() {
    int result = Objects.hashCode(row);
    result = 31 * result + number;
    return result;
}
```

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.8 — equals without hashCode
- Deleted hashCode, kept equals: a.equals(b) is true, but a and b have different hash codes.
  That breaks the contract (equal objects must have equal hash codes).
- List.contains still works: it checks every element with equals. No hash code needed, but
  slow for big lists.
- HashSet.contains fails: it uses hashCode to pick ONE bucket, and checks the hash code
  before calling equals. The equal seat is in another bucket (or has another hash code), so
  equals is never even called. Proved it by printing inside equals: the list called it 3
  times, the set 0 times.
- HashMap.get fails the same way and returns null.
- No compile error, no warning (IntelliJ's "equals() and hashCode() not paired" inspection is
  off by default). Rule: override both or neither.
```

### Check yourself

1. After deleting `hashCode`, `a.equals(b)` is `true` but the set has size 2. Which part of
   the contract is broken?
2. Why does `list.contains` still find A1? Which method does it use, and how many elements
   might it check?
3. Give the two separate reasons why `set.contains` never calls your `equals`: one for when
   the seats land in different buckets, and one for when they share a bucket.
4. In Experiment A, how many times was `equals` called for the list, and for the set? Why?
5. Why does `HashMap.get` return `null` here, when the booking is clearly in the map?
6. If the list works and the set doesn't, why not just use lists everywhere?

---

## Subtask 5 — Break it on purpose, version two: change a field after adding

> **From the plan:** Restore both, put a seat in a `HashSet`, then change `seat.number` after it
> is in the set. Call `contains(seat)` with the very same object. It returns `false`. The object
> is now filed in the wrong bucket and is effectively lost. This is why mutable fields in
> `hashCode` are dangerous.

### What it's asking

1. Make sure `Seat` has **both** `equals` and `hashCode` again (IntelliJ's versions from
   Subtask 3). Nothing is wrong with them.
2. Add a seat to a `HashSet`.
3. Change `seat.number` while the seat is still inside the set.
4. Ask the set whether it contains **that same object**, not a new copy. It says no.
5. Explain why: the set filed the seat under its *old* hash code, and nobody told it the hash
   code changed.

Subtask 4 broke the methods. This time the methods are correct; the bug is **changing the
object** after it went in. That's why `row` and `number` were left non-`final` back in
Subtask 1.

### The code

`Seat` with both methods, same as the end of Subtask 4:

```java
class Seat {
    String row;
    int number;

    Seat(String row, int number) {
        this.row = row;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Seat seat = (Seat) o;
        return number == seat.number && Objects.equals(row, seat.row);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(row);
        result = 31 * result + number;
        return result;
    }
}
```

A new `main` (keep your old one in another file or comment it out):

```java
public static void main(String[] args) {
    Set<Seat> taken = new HashSet<>();
    Seat seat = new Seat("A", 1);
    taken.add(seat);

    System.out.println("Before: hashCode " + seat.hashCode() + ", contains? " + taken.contains(seat));

    seat.number = 2;                       // change it while it's inside the set

    System.out.println("After:  hashCode " + seat.hashCode() + ", contains? " + taken.contains(seat));
    System.out.println("Size: " + taken.size());
    System.out.println("The set: " + taken);
}
```

Output:

```
Before: hashCode 2016, contains? true
After:  hashCode 2017, contains? false
Size: 1
The set: [Seat@7e1]
```

Read it slowly:
- **Before:** hash code 2016, and the set finds the seat. All good.
- **After:** we passed the *very same object* to `contains`, not a copy, and the set says
  `false`.
- **Size 1:** the seat is still in there. The set didn't throw it away.
- **`[Seat@7e1]`:** printing the set shows it. `7e1` is hex for 2017, the seat's *new* hash
  code. So the seat is visibly inside the set, and the set says it isn't.

### Step by step: why the same object gets lost

Remember (Subtask 1) that when you `add`, the set calculates the hash code **once**, picks a
bucket from it, and **stores that hash number** next to the object. It never recalculates it
on its own.

1. **`add(seat)`** while the seat is A1: hash code 2016. Bucket = 2016 with the last 4 bits
   kept = **bucket 0**. The set files the seat in bucket 0 and writes down "hash 2016".
2. **`seat.number = 2`:** you change the object itself. The set isn't told. The seat is still
   sitting in bucket 0 with "2016" written next to it. But if you ask the seat now, its
   `hashCode()` returns 31 × 65 + 2 = **2017**.
3. **`contains(seat)`:** the set asks the seat for its hash code *now*: 2017, so
   **bucket 1**. Bucket 1 is empty, so the answer is `false`. The seat is in bucket 0 and the set
   never looks there.

I confirmed this by looking inside the set's real bucket table:

```
after add:
  bucket 0: stored hash 2016, seat now A1 (hashCode now 2016)
after change:
  bucket 0: stored hash 2016, seat now A2 (hashCode now 2017)
```

After the change, the set still files the seat in bucket 0 under hash 2016, but the seat itself
now says 2017. The filing label is out of date. That's what "filed in the wrong bucket" means.

An analogy: a library shelves a book under "A" because of its title. Someone takes a pen and
changes the title on the cover to start with "B". Now everyone looks for it under "B", and
it's sitting under "A" forever.

### Experiments

Add these lines to the end of the new `main`, one at a time, and predict each answer before
running it.

**Experiment A — can't find it with a new seat either.**

```java
System.out.println("contains(new A1)? " + taken.contains(new Seat("A", 1)));
System.out.println("contains(new A2)? " + taken.contains(new Seat("A", 2)));
```

```
contains(new A1)? false
contains(new A2)? false
```

Both are `false`, and they fail for different reasons:
- **A new A1** has hash 2016, so it goes to bucket 0 and finds the stored "2016": the hash
  matches! Then `equals` runs: is A1 equal to the stored seat? The stored seat is *now* A2, so
  no.
- **A new A2** has hash 2017, so it goes to bucket 1, which is empty.

No seat you can build will find it. It's truly lost.

**Experiment B — you can't remove it, and you can add it twice.**

```java
System.out.println("remove(seat)? " + taken.remove(seat));
System.out.println("add(seat) again? " + taken.add(seat));
System.out.println("Size: " + taken.size());
System.out.println("The set: " + taken);
```

```
remove(seat)? false
add(seat) again? true
Size: 2
The set: [Seat@7e1, Seat@7e1]
```

`remove` fails for the same reason `contains` did. Then `add` looks in bucket 1, finds it
empty, and files the seat **again**. Now the set holds the **same object twice**, which is
something a set is supposed to make impossible. The bucket table shows it:

```
after re-add:
  bucket 0: stored hash 2016, seat now A2 (hashCode now 2017)
  bucket 1: stored hash 2017, seat now A2 (hashCode now 2017)
```

In Zenith terms: someone "moves" a booking from seat A1 to A2 by changing the field. Now the
booked-seats set can't tell you that A2 is taken, can't cancel it, and could hold it twice.

**Experiment C — looping still finds it.**

```java
boolean found = false;
for (Seat s : taken) {
    if (s == seat) found = true;
}
System.out.println("Found by looping? " + found);
```

```
Found by looping? true
```

Looping just walks every bucket, like a list does, without using hash codes. So the seat
really is in there; only the fast hash lookup is broken. (Same lesson as Subtask 4: anything
that goes "straight to one bucket" depends on the hash code staying correct.)

**Experiment D — the safe way to change it: remove, change, add.** Replace the
`seat.number = 2;` line with:

```java
taken.remove(seat);    // take it out while its hash is still 2016
seat.number = 2;
taken.add(seat);       // file it again under 2017
```

```
Before: hashCode 2016, contains? true
After:  hashCode 2017, contains? true
Size: 1
```

This works, but you have to remember it every time, in every place that changes a seat, for
every set and map the seat might be in. Nobody remembers that reliably.

**Experiment E — the real fix: make the change impossible.** Mark the fields `final`:

```java
final String row;
final int number;
```

Now the line `seat.number = 2;` doesn't compile (your line number may differ):

```
SeatDemo.java:38: error: cannot assign a value to final variable number
        seat.number = 2;
            ^
1 error
```

A seat that can't change can't get lost. If a booking moves from A1 to A2, you make a **new**
`Seat("A", 2)` (remove the old one, add the new one). This is the F2.2 habit (`private final`
fields) paying off, and it's exactly what a `record` does for you in Subtask 8. Put the
`final`s back afterwards if you want to keep experimenting, or keep them. Seats really
shouldn't change.

### Where you'll meet this again

The plan warns that this exact bug comes back in F6.11 with JPA entities: if an entity's
`hashCode` uses a field that changes when it's saved (like a database id that's empty before
saving and filled in after), an entity added to a `HashSet` before saving gets lost after.
Subtask 9 starts on that.

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.8 — changing a field after adding
- Both methods correct. Added seat A1 (hash 2016) to a HashSet, then set seat.number = 2.
- contains(seat) with the SAME object: false. Size still 1. Printing the set shows Seat@7e1.
- Why: the set stores the hash it calculated at add time (2016, bucket 0) and never
  recalculates. The seat now says 2017, so lookups go to bucket 1, which is empty.
- new A1 fails too (right bucket and hash, but equals says A1 != A2). new A2 fails (empty
  bucket). remove fails. add puts the same object in twice.
- Looping over the set still finds it; only hash lookups are broken.
- Fix: remove, change, add again (fragile), or better, make the fields final / use a record so
  the change can't compile. Rule: the hash code must not change while the object is in a set.
```

### Check yourself

1. After `seat.number = 2`, why does `contains(seat)` with the very same object return `false`?
   Name the two hash codes and the two buckets.
2. Is the seat still in the set? Give two pieces of evidence from the output.
3. `contains(new Seat("A", 1))` reaches the right bucket and the hash matches. Why is the
   answer still `false`?
4. How did the set end up holding the same object twice?
5. Why does looping over the set still find the seat?
6. Why is making the fields `final` a better fix than "remove, change, add"?

---

## Subtask 6 — Write the contract in your own words

> **From the plan:** Write the contract in `NOTES.md`: equal objects must have equal hash codes;
> unequal objects may share one; the hash code must not change while the object sits in a
> hash-based collection.

### What it's asking

This subtask has no new code to write; the output is a paragraph in **your** `NOTES.md`. You've
now seen each rule broken, so you can write each one with the evidence next to it. Writing it
yourself is the point; the model at the end is for comparing, not copying.

A **contract** (from the key words table) is a promise the compiler never checks. `HashSet` and
`HashMap` were written *assuming* your class keeps these three promises. If you break one,
nothing crashes; you just get wrong answers.

### Rule 1 — Equal objects must have equal hash codes

> If `a.equals(b)` is `true`, then `a.hashCode() == b.hashCode()` must be `true`.

**Why the set needs it:** the set uses the hash code to choose *which bucket to look in*.
If two equal seats had different hash codes, the set would look in the wrong bucket and never
reach `equals`.

**You saw it broken in Subtask 4.** You deleted `hashCode` and kept `equals`. Then `a.equals(b)`
was `true`, but the hash codes were 1639705018 and 1627674070. Result: size 2, A1 "not
taken", sold twice, `HashMap.get` returned `null`.

**The one-way trap:** this rule only goes one direction. Equal objects → same hash code. It
does **not** say same hash code → equal objects. That's rule 2.

### Rule 2 — Unequal objects may share a hash code

> If `a.equals(b)` is `false`, their hash codes are *allowed* to be the same.

This is a **permission**, not a demand. It has to be allowed: there are only about 4 billion
`int` values, and far more possible seats (any `String` row × any number). Some different
seats *must* end up with the same number. That's a **collision**, and the set handles it:
same bucket, then `equals` decides.

**See it work.** The strings `"Aa"` and `"BB"` happen to have the same hash code, so seats
`Aa1` and `BB1` do too:

```java
System.out.println("\"Aa\".hashCode(): " + "Aa".hashCode());
System.out.println("\"BB\".hashCode(): " + "BB".hashCode());

Seat x = new Seat("Aa", 1);
Seat y = new Seat("BB", 1);
System.out.println("x.hashCode(): " + x.hashCode());
System.out.println("y.hashCode(): " + y.hashCode());
System.out.println("x.equals(y): " + x.equals(y));

Set<Seat> set = new HashSet<>();
set.add(x);
set.add(y);
System.out.println("Size: " + set.size());
System.out.println("contains Aa1? " + set.contains(new Seat("Aa", 1)));
System.out.println("contains BB1? " + set.contains(new Seat("BB", 1)));
```

```
"Aa".hashCode(): 2112
"BB".hashCode(): 2112
x.hashCode(): 65473
y.hashCode(): 65473
x.equals(y): false
Size: 2
contains Aa1? true
contains BB1? true
```

Same hash code, different seats, and the set gets everything right: both kept, both found.
The shared hash code just put them in the same bucket; `equals` told them apart.

**The catch is speed, not correctness.** The most extreme legal `hashCode`:

```java
@Override
public int hashCode() {
    return 42;          // every seat gets the same number
}
```

That obeys rule 1 (equal seats do get equal hash codes, since *everything* does) and rule 2.
So the answers are correct:

```
Size: 2
contains A1? true
contains C3? false
```

But every seat lands in **one** bucket, so the set has to compare seat after seat, like a
list. Adding 20,000 seats:

| `hashCode` | Time to add 20,000 seats |
| --- | --- |
| `return 42;` | about 900 ms |
| IntelliJ's (uses `row` and `number`) | about 3 ms |

(Your numbers will differ; the gap won't.) The official docs say the same thing: distinct
hash codes for unequal objects "may improve the performance of hash tables". So build the
hash code from the same fields as `equals`, and different seats will *usually* get different
numbers.

### Rule 3 — The hash code must not change while the object is in a hash-based collection

> Once an object is in a `HashSet` (or is a `HashMap` key), its `hashCode()` must keep
> returning the same number until it's taken out.

**Why the set needs it:** the set calculates the hash code once, at `add`, and files the
object under that number. It never recalculates by itself.

**You saw it broken in Subtask 5.** Seat A1 went in with hash 2016 (bucket 0). Then
`seat.number = 2` made its hash 2017. The very same object was "not there", couldn't be
removed, and could be added a second time.

**How to keep it:** the hash code only changes if a field used in it changes. So make those
fields `final` (or use a record, Subtask 8), and rule 3 is kept automatically.

### What the official documentation says

This is the actual wording from the Java 21 docs for `Object.hashCode()`
([Object docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)),
with the plain meaning under each:

> Whenever it is invoked on the same object more than once during an execution of a Java
> application, the `hashCode` method must consistently return the same integer, provided no
> information used in `equals` comparisons on the object is modified.

Rule 3. Note the "provided": Java itself admits the hash code changes if you change the
fields. It's on **you** not to do that while the object is in a set. The plan's rule is the
practical version.

> This integer need not remain consistent from one execution of an application to another
> execution of the same application.

Bonus point: the number can differ between runs. So never **save** a hash code (in a file or
database) and expect it to match tomorrow. (`Object`'s identity numbers looked the same in
your runs, but nothing promises that.)

> If two objects are equal according to the `equals` method, then calling the `hashCode`
> method on each of the two objects must produce the same integer result.

Rule 1.

> It is not required that if two objects are unequal according to the `equals` method, then
> calling the `hashCode` method on each of the two objects must produce distinct integer
> results. However, the programmer should be aware that producing distinct integer results
> for unequal objects may improve the performance of hash tables.

Rule 2, plus the speed warning you just measured.

And from `Object.equals()`:

> It is generally necessary to override the `hashCode` method whenever this method is
> overridden, so as to maintain the general contract for the `hashCode` method, which states
> that equal objects must have equal hash codes.

That's Subtask 4 in one sentence.

### Bonus: `equals` has its own contract too

The same docs list five promises for `equals`. You've already met most of them:

| Promise | Plain meaning | Where you met it |
| --- | --- | --- |
| Reflexive | `x.equals(x)` is `true` | Every seat equals itself (Subtask 1, Experiment B) |
| Symmetric | `x.equals(y)` and `y.equals(x)` give the same answer | The `VipSeat` problem (Subtask 3) |
| Transitive | If `x` equals `y` and `y` equals `z`, then `x` equals `z` | Follows if you compare the same fields every time |
| Consistent | Asking again gives the same answer, if nothing changed | Comparing fields, not random things |
| Not equal to null | `x.equals(null)` is `false` (and doesn't crash) | The `o == null` / `instanceof` check (Subtasks 2 and 3) |

You don't need to memorise this table. Comparing the same fields with `Objects.equals` / `==`,
with a null check, keeps all five. The `hashCode` contract is what the plan wants written down.

### Your turn: write it in `NOTES.md`

Write the three rules **without looking**, each with one line of evidence from your own runs.
Then compare with this model:

```markdown
## F2.8 — the equals/hashCode contract
HashSet and HashMap assume three promises. Nothing checks them; breaking one gives wrong
answers, not errors.

1. Equal objects MUST have equal hash codes.
   The set picks the bucket from the hash code, so equal objects with different hash codes
   end up in different buckets and equals is never reached.
   Evidence: deleted hashCode (Subtask 4): a.equals(b) true, hashes 1639705018 vs 1627674070,
   set size 2, HashMap.get returned null.

2. Unequal objects MAY share a hash code (a collision). That's allowed and the answers stay
   correct: same bucket, then equals decides. But too many collisions make it slow.
   Evidence: seats Aa1 and BB1 both hash to 65473, set kept and found both.
   hashCode returning 42 for everything: correct, but 20,000 seats took ~900 ms vs ~3 ms.

3. The hash code MUST NOT change while the object is in a hash-based collection.
   The set stores the hash from add time and never recalculates.
   Evidence: changed seat.number after adding (Subtask 5): hash 2016 -> 2017, contains(seat)
   with the same object was false, remove failed, add let it in twice.
   Keep it by making the fields used in hashCode final (or using a record).

Rule of thumb: override equals and hashCode together, from the same fields, and make those
fields final.
```

### Check yourself

1. Say the three rules without looking. Which one is a permission rather than a requirement?
2. Rule 1 says equal → same hash code. Does same hash code → equal? Give the example that
   proves it.
3. Why *must* rule 2 be allowed? (Think about how many `int` values exist.)
4. `hashCode() { return 42; }`: is it legal? Is it correct? Is it a good idea? One word each,
   then one sentence why.
5. The official rule 3 has a "provided no information used in `equals` ... is modified". What
   does that mean you're responsible for?
6. Why should you never save a hash code to a database and compare it next week?

---

## Subtask 7 — What `HashMap` does inside `put` and `get`

> **From the plan:** Ask AI: *"Walk me through, step by step, what HashMap does internally when
> I call put and then get, and show me exactly where it uses hashCode and where it uses
> equals."*

### What it's asking

So far you've used `HashSet`. Real code uses `HashMap` even more: seat → customer, event id →
event, and so on. The plan wants you to know exactly what happens inside `put` and `get`, and
the two moments where *your* methods get called.

Instead of trusting an explanation, we make the map **tell us**: `hashCode` and `equals` print
a line every time they're called. Then every claim below is something you can see in the
output. (At the end, ask an AI the plan's question anyway, and check its answer against this
output.)

### Words for this subtask

| Word | Meaning |
| --- | --- |
| Key | What you look things up by. In `Map<Seat, String>`, the `Seat`. |
| Value | What you get back. Here, the customer's name. |
| Entry (node) | One key-value pair inside the map, plus the key's stored hash code. |
| `put(key, value)` | "Remember this value for this key." Returns the *old* value if the key was already there, else `null`. |
| `get(key)` | "What value do you have for this key?" Returns `null` if none. |
| Resize | When the map gets too full, it doubles its buckets (16 → 32 → 64...) and re-files every entry. |

### The code

A `Seat` whose two methods report every call, plus a `toString` so it prints as `A1` instead of
`Seat@7e0`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class Seat {
    final String row;
    final int number;

    Seat(String row, int number) {
        this.row = row;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        System.out.println("    equals() called: " + this + " vs " + o);
        if (o == null || getClass() != o.getClass()) return false;

        Seat seat = (Seat) o;
        return number == seat.number && Objects.equals(row, seat.row);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(row);
        result = 31 * result + number;
        System.out.println("    hashCode() called on " + this + " -> " + result);
        return result;
    }

    @Override
    public String toString() {
        return row + number;
    }
}

public class MapDemo {
    public static void main(String[] args) {
        Map<Seat, String> holder = new HashMap<>();

        System.out.println("1. put(A1, Sam)");
        System.out.println("   returned " + holder.put(new Seat("A", 1), "Sam"));

        System.out.println("2. put(B2, Priya)");
        System.out.println("   returned " + holder.put(new Seat("B", 2), "Priya"));

        System.out.println("3. put(A1, Ravi)");
        System.out.println("   returned " + holder.put(new Seat("A", 1), "Ravi"));

        System.out.println("4. get(A1)");
        System.out.println("   returned " + holder.get(new Seat("A", 1)));

        System.out.println("5. get(B2)");
        System.out.println("   returned " + holder.get(new Seat("B", 2)));

        System.out.println("6. get(A2)");
        System.out.println("   returned " + holder.get(new Seat("A", 2)));

        Seat mine = new Seat("C", 3);
        System.out.println("7. put(C3, Meera) then get with the SAME object");
        holder.put(mine, "Meera");
        System.out.println("   get:");
        System.out.println("   returned " + holder.get(mine));

        System.out.println("8. put(Aa1, Kiran) then get(BB1)");
        holder.put(new Seat("Aa", 1), "Kiran");
        System.out.println("   get:");
        System.out.println("   returned " + holder.get(new Seat("BB", 1)));

        System.out.println("Size: " + holder.size());
        System.out.println("The map: " + holder);
    }
}
```

(Save as `MapDemo.java`, run with `javac MapDemo.java` and `java MapDemo`.)

### The output

```
1. put(A1, Sam)
    hashCode() called on A1 -> 2016
   returned null
2. put(B2, Priya)
    hashCode() called on B2 -> 2048
   returned null
3. put(A1, Ravi)
    hashCode() called on A1 -> 2016
    equals() called: A1 vs A1
   returned Sam
4. get(A1)
    hashCode() called on A1 -> 2016
    equals() called: A1 vs A1
   returned Ravi
5. get(B2)
    hashCode() called on B2 -> 2048
    equals() called: B2 vs B2
   returned Priya
6. get(A2)
    hashCode() called on A2 -> 2017
   returned null
7. put(C3, Meera) then get with the SAME object
    hashCode() called on C3 -> 2080
   get:
    hashCode() called on C3 -> 2080
   returned Meera
8. put(Aa1, Kiran) then get(BB1)
    hashCode() called on Aa1 -> 65473
   get:
    hashCode() called on BB1 -> 65473
    equals() called: BB1 vs Aa1
   returned null
Size: 4
The map: {A1=Ravi, B2=Priya, C3=Meera, Aa1=Kiran}
```

I also looked inside the map's real bucket table at the end (the same trick as Subtask 1):

```
Buckets (table length 16):
  bucket 0: -> [hash 2016, A1=Ravi] -> [hash 2048, B2=Priya] -> [hash 2080, C3=Meera]
  bucket 1: -> [hash 65473, Aa1=Kiran]
```

A1, B2 and C3 all ended up in **bucket 0**. (2016, 2048 and 2080 are all multiples of 16, so
"keep the last 4 bits" gives 0 for all three.) That collision turns out to be useful: it shows
exactly when `equals` is and isn't called.

### What `put` does, step by step

When you call `holder.put(key, value)`:

1. **Ask the key for its hash code.** This is the *only* `hashCode()` call. (Every `put` in the
   output shows exactly one.)
2. **Pick the bucket** from that number (16 buckets, so keep the last 4 bits, after a small mix
   step; see Subtask 1).
3. **Bucket empty?** Make a new entry `[hash, key, value]` there. Return `null`. Done.
   *Step 1* (A1 into empty bucket 0): no `equals` at all.
4. **Bucket not empty?** Walk through its entries one by one. For each one, check **in this
   order**:
   - a. **Is the stored hash the same number?** If not, skip this entry. No `equals` call.
   - b. **Is it the very same object (`==`)?** If yes, it's a match. No `equals` call.
   - c. **Otherwise, call `key.equals(storedKey)`.** If `true`, it's a match.
5. **Found a match?** Replace the entry's **value**, keep the old key, and return the old value.
   *Step 3*: `put(A1, Ravi)` found A1 → `equals` was called once, and `put` returned `Sam`.
   Sam's name was replaced by Ravi's; the map still has one A1.
6. **No match?** Add a new entry at the end of the bucket. Return `null`.
   *Step 2*: B2 (hash 2048) went into bucket 0 next to A1 (hash 2016). Check 4a said "hash
   differs", so `equals` was **never** called, even though they share a bucket.
7. **Too full?** If the map now holds more than 75% of its bucket count (more than 12 of 16),
   double the buckets (see "Experiment B — resize" below).

### What `get` does, step by step

`holder.get(key)` is the same search without the storing:

1. **Ask the key for its hash code.** Once.
2. **Pick the bucket.**
3. **Bucket empty?** Return `null`. *Step 6*: A2 has hash 2017 → bucket 1 (which was empty at
   that point) → `null`, and **no `equals` at all**.
4. **Walk the bucket**, doing the same checks as `put` 4a/4b/4c.
5. **Match?** Return its value. **No match?** Return `null`.

Now read steps 4, 5, 7 and 8 again with this in mind:

- **Step 4 — `get(A1)`:** bucket 0, first entry A1 has hash 2016, the same → different objects
  → `equals` → `true` → `Ravi`. One `equals` call.
- **Step 5 — `get(B2)`:** bucket 0 holds A1 then B2. The A1 entry's hash is 2016, not 2048 →
  skipped **without** `equals`. The B2 entry matches the hash → `equals` → `Priya`. Look at
  the output: only **one** `equals` call (`B2 vs B2`), never `B2 vs A1`.
- **Step 7 — the same object:** `put(mine, ...)` then `get(mine)`. Hash matches, and check 4b
  sees it's the very same object (`==`) → match, **no `equals` call at all**. That shortcut is
  why Subtask 1's Experiment B (adding the same object twice) worked even without `equals`.
- **Step 8 — a real collision:** Aa1 and BB1 both hash to 65473 (Subtask 6). The hash
  matches, they're different objects, so `equals` finally has to decide: `BB1 vs Aa1` → `false`
  → `null`. This is the only case where `equals` says "no".

### Where `hashCode` is used, and where `equals` is used

| Your method | When `HashMap` calls it | What for |
| --- | --- | --- |
| `hashCode()` | Once at the start of every `put`, `get`, `containsKey`, `remove` | To pick **which bucket** to look in |
| `equals()` | Only for an entry in that bucket whose stored hash is the same and that isn't the same object | To decide **is this really my key?** |

Notice which object's `equals` is called: the output says `BB1 vs Aa1`. The key **you pass in**
is `this`, and the stored key is the argument. (Same as `contains(x)` in Subtask 1.)

And now the earlier breakages make complete sense:
- **Subtask 4** (no `hashCode`): equal seats → different hashes → different bucket, or check 4a
  fails. `equals` is never reached.
- **Subtask 5** (changed field): the stored hash is from `put` time. The new hash points to a
  different bucket, or check 4a fails.

### Experiments

**Experiment A — a `HashSet` is a `HashMap` inside.** Add to `main`:

```java
System.out.println("9. HashSet.add(A1)");
Set<Seat> set = new HashSet<>();
System.out.println("   returned " + set.add(new Seat("A", 1)));
System.out.println("   add(A1) again:");
System.out.println("   returned " + set.add(new Seat("A", 1)));
```

```
9. HashSet.add(A1)
    hashCode() called on A1 -> 2016
   returned true
   add(A1) again:
    hashCode() called on A1 -> 2016
    equals() called: A1 vs A1
   returned false
```

Exactly the same calls as `put`. Inside, `HashSet` holds a `HashMap`; `add(x)` does
`map.put(x, someDummyValue)` and returns `true` if `put` returned `null` (the key was new).
That's why everything you learned about sets applies to map **keys**. (The values don't matter:
`HashMap` never calls `hashCode` or `equals` on values.)

**Experiment B — resize.** Put 13 seats into a new map and count `hashCode` calls (use a
`static int hashCalls` counter in `Seat` instead of the print):

```
after put #1: buckets 16, hashCode calls so far 1
...
after put #12: buckets 16, hashCode calls so far 12
after put #13: buckets 32, hashCode calls so far 13
```

On the 13th entry the map passed 75% full (12 of 16) and doubled to 32 buckets, moving every
entry to its new bucket. But it made **no extra `hashCode` calls**: 13 puts, 13 calls. It
re-filed everything using the **stored** hash numbers. That's the same stored number that
went stale in Subtask 5.

**Experiment C (reading only) — very crowded buckets.** If one bucket gets 8 or more entries
(and the map has at least 64 buckets), Java turns that bucket from a simple chain into a small
tree to keep lookups fast. You'll never need to touch this; it's why the `return 42` version in
Subtask 6 was slow but not *unbearably* slow.

### Ask the AI, then check it

Now ask an AI the plan's exact question:

> *"Walk me through, step by step, what HashMap does internally when I call put and then get,
> and show me exactly where it uses hashCode and where it uses equals."*

Check its answer against your output. A good answer mentions all of these; mark any it
misses or gets wrong:

- [ ] `hashCode` is called once per `put`/`get`, on the key passed in
- [ ] the bucket is chosen from the hash (with a mixing step and "keep the last bits")
- [ ] the stored hash is compared **before** `equals`
- [ ] the `==` shortcut before `equals`
- [ ] `put` with an existing key replaces the value and returns the old one
- [ ] `get` returns `null` when there's no match
- [ ] resize at 75% full (load factor 0.75), using stored hashes
- [ ] crowded buckets turn into trees

### Write it up in your `NOTES.md`

```markdown
## F2.8 — inside HashMap put and get
put(key, value):
1. key.hashCode() — once. Picks the bucket.
2. Empty bucket: store [hash, key, value], return null. No equals.
3. Otherwise for each entry in the bucket: stored hash same? same object (==)? key.equals(stored)?
   Only reaches equals if the hashes already match.
4. Match: replace the VALUE, return the old value. No match: add entry, return null.
5. Over 75% full: double the buckets, re-file using the STORED hashes (no hashCode calls).
get(key): same steps 1 and 3, returns the value or null.

Proof from my printout: get(B2) skipped A1 in the same bucket without calling equals (different
hash). get(sameObject) called no equals (== shortcut). Aa1 and BB1 had the same hash, so
equals was called and said false.

HashSet is a HashMap inside: add(x) = put(x, dummy). hashCode/equals are only used on KEYS.
```

### Check yourself

1. How many times does `put` call `hashCode()`? On which object?
2. In step 2, B2 went into the same bucket as A1. Why was `equals` not called?
3. In step 3, `put(A1, Ravi)` returned `Sam`. What did the map change, and what did it keep?
4. Name the three checks, in order, `HashMap` makes for each entry in a bucket. Which one is
   your `equals`?
5. Why didn't step 7 (`get` with the same object) call `equals`?
6. During a resize, why doesn't `HashMap` call `hashCode` again? How does that connect to
   Subtask 5?
7. Why does everything you learned about `HashSet` also apply to `HashMap` keys?

---

## Subtask 8 — Rewrite `Seat` as a `record`

> **From the plan:** Rewrite `Seat` as `record Seat(String row, int number) {}` — a one-line
> preview of Task F3.6, where records get explained properly. Re-run every experiment above.
> Everything works and you wrote no boilerplate. Record why a record is the default choice for
> value-like data.

### What it's asking

1. Delete the whole `Seat` class (fields, constructor, `equals`, `hashCode`) and replace it
   with **one line**.
2. Re-run the experiments from Subtasks 1–7 and see that they all give the *right* answers.
3. Write down in `NOTES.md` why a record is the default for data like a seat.

**Boilerplate** means code you have to write but that has no ideas in it: the same
fields-constructor-equals-hashCode pattern every time. You've now written it by hand
(Subtask 2), generated it (Subtask 3), and broken it twice (Subtasks 4 and 5). A record means
you never write it again.

This is only a preview. F3.6 covers records properly (validation in the constructor, and
more). Here we only care about `equals` and `hashCode`.

### The code

The entire `Seat`:

```java
record Seat(String row, int number) {}
```

That's it. `row` and `number` in the brackets are called the record's **components**.

### What the compiler wrote for you

You can see what Java generated with `javap -p Seat.class` (after compiling):

```
final class Seat extends java.lang.Record {
  private final java.lang.String row;
  private final int number;
  Seat(java.lang.String, int);
  public final java.lang.String toString();
  public final int hashCode();
  public final boolean equals(java.lang.Object);
  public java.lang.String row();
  public int number();
}
```

Line by line:

| Generated | What it means for you |
| --- | --- |
| `final class Seat` | No class can extend it. The `VipSeat` symmetry problem (Subtask 3) can't happen. |
| `private final ... row; number;` | The F2.2 habit, done for you. The fields can't be changed, so a seat can't get lost in a set (Subtask 5). |
| `Seat(String, int)` | The constructor that sets both fields. |
| `toString()` | Prints `Seat[row=A, number=1]` instead of `Seat@7e0`. |
| `hashCode()` | Built from **all** components. |
| `equals(Object)` | Compares **all** components. Takes `Object` (no overload trap, Subtask 2), checks the type, handles `null`. |
| `row()`, `number()` | **Accessors**: methods to read the fields. Note the brackets. |

### Fixing your old code: two compile errors you'll hit

Your `main` and `sell` use `seat.row` and `seat.number`. With a record, those fields are
`private`, so:

```
SeatDemo.java:9: error: row has private access in Seat
        System.out.println(seat.row);
                               ^
```

Fix: call the accessor, `seat.row()` and `seat.number()`. For example `sell` becomes:

```java
static void sell(Set<Seat> sold, Seat seat, String customer) {
    if (sold.contains(seat)) {
        System.out.println("Sorry " + customer + ", seat " + seat.row() + seat.number() + " is already sold");
    } else {
        sold.add(seat);
        System.out.println("Sold seat " + seat.row() + seat.number() + " to " + customer);
    }
}
```

And the Subtask 5 line `seat.number = 2;` can't even be written:

```
SeatDemo.java:10: error: number has private access in Seat
        seat.number = 2;
            ^
```

That's the record protecting you. Delete that experiment's line (or see Experiment D below).

### Re-running every experiment

All of these ran with the one-line record:

```
Size: 1
Is A1 taken? true
a == b: false
a.equals(b): true
a.hashCode(): 2016
b.hashCode(): 2016
The set: [Seat[row=A, number=1]]
a.row(): A, a.number(): 1
Sold seat A1 to Sam
Sorry Priya, seat A1 is already sold
Seats: 3
A1 2016, A2 2017, B1 2047
new String row equal? true
null row equal? true
equals(null)? false
equals("A1")? false
List contains A1? true
put again returned: Sam
Who holds A1? Ravi
Aa1 hash 65473, BB1 hash 65473, equal? false
clash size 2, contains BB1? true
```

Going through them against the earlier subtasks:

| Experiment | Before (broken or hand-written) | With the record |
| --- | --- | --- |
| Subtask 1: two equal seats in a set | Size 2, A1 "not taken" | Size 1, `true` |
| Subtask 1: the double sale | Sold A1 to Sam **and** Priya | "Sorry Priya, seat A1 is already sold" |
| Subtask 1: printing the set | `[Seat@15db9742, Seat@6d06d69c]` | `[Seat[row=A, number=1]]` |
| Subtask 2: `new` still makes two objects | `a == b` false | `a == b` still **false**. A record is still an object; only `equals` changed |
| Subtask 2 D: A1, A2, B1, A1 again | 3 seats | 3 seats |
| Subtask 2 F: `new String("A")` as the row | Broke the `==` version | `true`. Records compare objects with `equals`, not `==` |
| Subtask 2: `null` row, `equals(null)`, `equals("A1")` | Needed the null and type checks | `true`, `false`, `false`, no crash |
| Subtask 3: a `VipSeat` subclass | Asymmetric `equals` | Can't compile: `error: cannot inherit from final Seat` |
| Subtask 4: delete `hashCode` | Lost in the set | Can't forget: you never write it |
| Subtask 5: change `number` after adding | Lost in the set | Can't compile (`private`, and `final` inside) |
| Subtask 6: Aa1 vs BB1 (same hash) | Both kept | Both kept, both found |
| Subtask 7: `put` twice, `get` | Returned `Sam`, then `Ravi` | Same |
| `List.contains` | `true` | `true` |

**About the hash numbers:** the record gave A1 = 2016, the same as IntelliJ's version. On Java 21
it happens to use the same "31 × ... + ..." recipe. But Java's docs for `Record.hashCode` say
the exact recipe is *unspecified and may change*. That's fine, because all the contract asks
for is "equal seats → equal numbers". Never rely on the actual number (Subtask 6: never save
it).

### Experiments

**Experiment A — print a seat.** `System.out.println(new Seat("A", 1));` prints
`Seat[row=A, number=1]`. Compare the `Seat@7e0` you've been staring at. Free `toString` makes
debugging much easier.

**Experiment B — try to subclass it.** Add `class VipSeat extends Seat {}`:

```
SeatDemo.java:3: error: cannot inherit from final Seat
class VipSeat extends Seat {
                      ^
```

(You'll also get a second error about the constructor; the first is the one that matters.)
Records are always `final`. That's the Subtask 3 rule ("make value classes `final`") done for
you.

**Experiment C — `new` still makes new objects.** `a == b` is `false` with the record too.
Your question from Subtask 2 still applies: `new` always creates a separate object, and `==`
still compares references. The record only changes what `equals` and `hashCode` say.

**Experiment D — "moving" a booking the record way.** You can't change a seat, so make a new
one:

```java
Seat seat = new Seat("A", 1);
Set<Seat> booked = new HashSet<>();
booked.add(seat);

Seat moved = new Seat(seat.row(), 2);   // a new seat: same row, number 2
booked.remove(seat);
booked.add(moved);
System.out.println("Booked: " + booked + ", contains A2? " + booked.contains(new Seat("A", 2)));
```

```
Booked: [Seat[row=A, number=2]], contains A2? true
```

That's Subtask 5's "remove, change, add" fix, except now it's the *only* way to write it, so
you can't forget a step and lose a seat.

### Why a record is the default for value-like data

A **value object** (key words table) is defined only by its contents: two `Seat("A", 1)` are
the same seat, two `Money(500, "INR")` are the same money. For those, a record:

1. **Gets `equals` and `hashCode` right automatically**, from all the components, together.
   No overload trap, no `==` on strings, no forgotten `hashCode` (Subtasks 2 and 4).
2. **Can't change**, so its hash code can't change while it's in a set (Subtask 5). This is
   contract rule 3 for free.
3. **Is `final`**, so no subclass can break symmetry (Subtask 3).
4. **Stays correct when you add a field.** Add `String section` to the brackets and
   `equals`/`hashCode`/`toString` include it automatically. With a hand-written class you'd
   have to remember to update both methods (and forgetting one breaks rule 1).
5. **Is one line**, so the reader sees the data, not 30 lines of boilerplate.

**When *not* to use a record (preview):**
- **Things with identity that change over time**, like a `Customer` or an `Event` that gets
  published and cancelled. Two customers named Sam are not the same customer. Those are
  **entities**, which is Subtask 9.
- **A record is only *shallowly* immutable.** If a component is a `List`, the record can't be
  pointed at a different list, but the list's contents can still change. F3.6 and F3.8 deal
  with that.

### Write it up in your `NOTES.md`

```markdown
## F2.8 — Seat as a record
record Seat(String row, int number) {}  — replaces ~30 lines.
Compiler generates: final class, private final fields, constructor, accessors row()/number(),
equals + hashCode from all components, toString "Seat[row=A, number=1]".
Re-ran everything: size 1, A1 taken, Priya refused, Aa1/BB1 both kept, HashMap put/get right.
a == b is still false (new still makes 2 objects). Hash A1 = 2016 on Java 21, but the exact
number is unspecified, so don't rely on it.
Old code: seat.row -> seat.row() ("row has private access"). seat.number = 2 can't compile.
VipSeat extends Seat: "cannot inherit from final Seat".
Why default for value data: equals/hashCode always correct and in sync, can't change (can't
get lost in a set), final (symmetric), updates itself when I add a field, one line.
Not for entities (identity + changes over time), and only shallowly immutable.
```

### Check yourself

1. List everything the compiler generates for `record Seat(String row, int number) {}`.
2. Why does `seat.row` stop compiling, and what do you write instead?
3. With the record, is `new Seat("A", 1) == new Seat("A", 1)` true or false? Why?
4. Which earlier bug does each of these record features prevent: `final` class, `final`
   fields, generated `hashCode`?
5. Why shouldn't you rely on the record's hash code being 2016?
6. You add a `section` component to the record. What do you have to change in `equals` and
   `hashCode`?
7. Name one Zenith class that should *not* be a record, and say why.

---

## Subtask 9 — Entities: a `Customer` that compares by id

> **From the plan:** Decide the other case: a `Customer` who changes their email is still the
> same customer. Write a `Customer` whose `equals` compares the id field only. Note in
> `NOTES.md` that this is identity equality, it is correct for entities, and Task 1.2 —
> Aggregates, invariants, and consistency boundaries formalises it.

### What it's asking

Everything so far was about `Seat`, a **value object**: it *is* its contents. Seat A1 is
just "row A, number 1"; any object holding those two values is that seat. That's why
comparing all fields (or a record) was right.

A customer is different:
- Sam changes their email from `sam@old.com` to `sam@new.com`. **Still the same customer.**
  Their bookings, loyalty points and history are still theirs.
- Two different people are both called Sam, with the same email typed by mistake. **Not the
  same customer.**

So "same contents" is the wrong question for a customer. The right question is "same
**id**?". That's an **entity**: a thing with its own identity that stays the same while its
details change. Comparing by id is called **identity equality**.

(Careful with the word: this "identity" is the *business* identity, the customer id. It's
not the JVM's object identity that `==` checks. Two separate `Customer` objects with id 42
are different objects in memory, but the same customer.)

### The code

```java
import java.util.HashSet;
import java.util.Set;

final class Customer {
    private final long id;          // never changes
    private String name;            // can change
    private String email;           // can change

    Customer(long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    long id() { return id; }
    String email() { return email; }

    void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Customer other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", name=" + name + ", email=" + email + "]";
    }
}
```

What's in it, and why:

| Part | Why |
| --- | --- |
| `private final long id` | The identity. `final`, so it can never change. That protects contract rule 3, because `hashCode` uses it. |
| `name`, `email` not `final` | These really do change in real life. They're **not** in `equals`/`hashCode`, so changing them is safe. |
| `changeEmail(...)` | A method named for *what happens*, rather than a generic `setEmail`. Zenith's rules ask for this style later. |
| `equals`: `id == other.id` only | Same id → same customer, whatever the name/email say. `==` because `long` is a primitive. |
| `instanceof Customer other` | The pattern-matching form from Subtask 2: checks the type, handles `null`, and gives you `other` in one go. |
| `hashCode`: `Long.hashCode(id)` | Built from **exactly** the field `equals` uses (rule 1). For id 42 it's just 42. |
| `final class` | No subclass can break symmetry (Subtask 3). |

Note that `equals` and `hashCode` use the **same field**. Using `id` in `equals` but all three
fields in `hashCode` would break rule 1: two equal customers with different emails would get
different hash codes (Subtask 4's bug again).

### Run it

```java
public class CustomerDemo {
    public static void main(String[] args) {
        Customer sam = new Customer(42, "Sam", "sam@old.com");
        Set<Customer> vip = new HashSet<>();
        vip.add(sam);
        System.out.println("Before: hashCode " + sam.hashCode() + ", VIP? " + vip.contains(sam));
        sam.changeEmail("sam@new.com");
        System.out.println("After:  hashCode " + sam.hashCode() + ", VIP? " + vip.contains(sam));
        System.out.println("The set: " + vip);

        Customer loadedAgain = new Customer(42, "Sam", "sam@old.com");
        System.out.println("sam == loadedAgain: " + (sam == loadedAgain));
        System.out.println("sam.equals(loadedAgain): " + sam.equals(loadedAgain));
        System.out.println("VIP (stale copy)? " + vip.contains(loadedAgain));

        Customer otherSam = new Customer(77, "Sam", "sam@new.com");
        System.out.println("sam.equals(otherSam): " + sam.equals(otherSam));
        vip.add(otherSam);
        System.out.println("VIP count: " + vip.size());
    }
}
```

```
Before: hashCode 42, VIP? true
After:  hashCode 42, VIP? true
The set: [Customer[id=42, name=Sam, email=sam@new.com]]
sam == loadedAgain: false
sam.equals(loadedAgain): true
VIP (stale copy)? true
sam.equals(otherSam): false
VIP count: 2
```

Reading it:
- **Email changed, still a VIP.** The hash code stayed 42 because email isn't part of it.
  Compare with Subtask 5, where changing `number` lost the seat. The difference: the field
  that changed here isn't used in `hashCode`.
- **`loadedAgain`** is a second object for customer 42 (think: loaded from the database again,
  with the old email). Different object (`==` false), **same customer** (`equals` true), and
  the set agrees.
- **`otherSam`** has the same name and email, but id 77. **Different customer**, so the set
  now has 2 VIPs. Correct.

### Experiments: why not the other two approaches?

**Experiment A — compare all fields (IntelliJ's default if you tick everything).**

```java
@Override
public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    CustomerByFields that = (CustomerByFields) o;
    return id == that.id && Objects.equals(name, that.name) && Objects.equals(email, that.email);
}

@Override
public int hashCode() {
    return Objects.hash(id, name, email);
}
```

Add the customer to a set, call `changeEmail`, then `contains`:

```
All-fields version, VIP after email change? false
```

Sam is lost from the VIP set, the same bug as Subtask 5, because `email` is in `hashCode`
and it changed. It's also wrong in meaning: it says Sam-with-a-new-email is a *different*
customer. When IntelliJ's ⌘N dialog asks which fields to include, for an entity tick **only
the id**.

**Experiment B — make it a record.**

```java
record CustomerRecord(long id, String name, String email) {}

CustomerRecord r1 = new CustomerRecord(42, "Sam", "sam@old.com");
CustomerRecord r2 = new CustomerRecord(r1.id(), r1.name(), "sam@new.com");   // "change" the email
System.out.println("Record version, same customer after email change? " + r1.equals(r2));
```

```
Record version, same customer after email change? false
```

A record compares **all** components, so it says customer 42 with a new email isn't
customer 42. That's right for a seat and wrong for a customer. This is the "when not to use
a record" from Subtask 8: an entity has identity and a lifecycle; a record is a value.

**Experiment C — the id as a `HashMap` key.** Add `import java.util.HashMap;` and
`import java.util.Map;`:

```java
Map<Customer, Integer> loyaltyPoints = new HashMap<>();
loyaltyPoints.put(sam, 100);
sam.changeEmail("sam@newest.com");
System.out.println("Points for customer 42: " + loyaltyPoints.get(new Customer(42, "?", "?")));
```

```
Points for customer 42: 100
```

The email changed after `put`, and the lookup used a brand-new object with junk name and
email. Same id, so `get` finds the 100 points (Subtask 7: same hash 42, then `equals` says
yes).

### Value object or entity? How to decide

Ask: **"If every detail changed, would it still be the same thing?"**

| Thing in Zenith | Same thing if details change? | Kind | Equality | Write it as |
| --- | --- | --- | --- | --- |
| `Seat` (row A, number 1) | No: A2 is a different seat | Value object | All fields | `record` |
| `Money` (500, INR) | No: 600 INR is different money | Value object | All fields | `record` |
| `Customer` | Yes: new email, same customer | Entity | id only | class, `final` id |
| `Event` (gets published, edited, cancelled) | Yes: renamed, same event | Entity | id only | class, `final` id |
| `Booking` | Yes: status changes, same booking | Entity | id only | class, `final` id |

### Where this goes next

- **Task 1.2 (Aggregates)** formalises this: entities, value objects, and which object
  "owns" which.
- **F6.11 (JPA)** brings the warning from the plan's "where people get stuck" back. With a
  database, the id is often **empty until the object is saved** and filled in afterwards. Then
  the id *changes* after creation, and an entity put in a `HashSet` before saving gets lost
  after saving: Subtask 5 again. That's why rule 24 says the id must be set when the object is
  created. F6.11 shows how to handle it properly.

### Write it up in your `NOTES.md`

```markdown
## F2.8 — entities compare by id (identity equality)
A Customer who changes their email is still the same customer. So Customer.equals compares the
id only, and hashCode uses the id only (same field, rule 1).
- id is private final (set in the constructor, never changes), so the hash never moves.
- name/email can change safely: they're not in hashCode. Changed email -> still in the VIP set.
- Another object with id 42 (stale email) equals sam. Different id, same name -> not equal.
- All-fields version: changing email loses the customer from the set (Subtask 5 bug) and wrongly
  says it's a different customer. Record version: same problem in meaning.
This is identity equality. It's correct for entities (Customer, Event, Booking). Value objects
(Seat, Money) use all-fields equality, usually as records. Task 1.2 (Aggregates) formalises it;
F6.11 shows the danger when a database assigns the id after creation.
```

### Check yourself

1. Why is "same contents" the right test for `Seat` but the wrong one for `Customer`?
2. Why must `hashCode` use the id *only*, and not the id plus the email?
3. Sam changed their email after being added to the VIP set and was still found. In Subtask 5
   the seat was lost. What's the difference?
4. `sam == loadedAgain` is `false` but `sam.equals(loadedAgain)` is `true`. Explain both, and
   why that's what you want.
5. Why is a record the wrong choice for `Customer`?
6. Why must the id be `final` and set in the constructor? What goes wrong in F6.11 if it isn't?
7. Is `Booking` a value object or an entity? Use the "if every detail changed..." question.

---

## Checkpoint — answer out loud before moving on

From the plan. Answer each without looking, then check the section in brackets.

1. With the `HashSet` demonstration in front of you, explain why `equals` without `hashCode`
   is a bug. *(Subtask 4; contract rule 1 in Subtask 6.)*
2. What exactly does a collision mean, and why is it not a problem? *(Key words table;
   Subtask 6 rule 2, Aa1 and BB1; Subtask 7 step 8.)*
3. You mutated a field after adding the object to a set and lost the object. Explain the
   mechanism. *(Subtask 5; stored hashes in Subtask 7.)*
4. Which Zenith classes should compare by contents, and which by id? Give one of each and
   justify it. *(Subtask 9, the value object or entity table.)*
5. What does a `record` give you, and when would you not want it? *(Subtask 8; Subtask 9,
   Experiment B.)*

**Done when** (from the plan): you can start from a plain class, show both failures, fix them,
and state the contract from memory.

### Your question — Checkpoint 5: what does a record give you, and when would you not want it?

**What a record gives you.** From one line, `record Seat(String row, int number) {}`, the
compiler writes:

1. **`private final` fields** for each component, so the data can't be changed after creation.
2. **A constructor** that sets all of them.
3. **Accessors**, `row()` and `number()`, to read them.
4. **`equals`** comparing all components (takes `Object`, handles `null` and the wrong type).
5. **`hashCode`** from the same components, so equals and hashCode always agree (rule 1).
6. **`toString`**: `Seat[row=A, number=1]`.
7. **A `final` class**, so no subclass can break symmetry.

What that saves you from getting wrong, all of which you did on purpose earlier: the overload
trap and `==` on strings (Subtask 2), the subclass symmetry problem (Subtask 3), forgetting
`hashCode` (Subtask 4), and a changed field losing the object in a set (Subtask 5). Plus, if
you add a component later, all of it updates automatically.

**When you would not want it.**

1. **The thing is an entity**, meaning it keeps its identity while its details change:
   `Customer`, `Event`, `Booking`. A record compares *all* components, so it says "Sam with a
   new email is a different customer" (Subtask 9, Experiment B). It also can't change at all,
   but a customer's email really does change. Use a class whose `equals`/`hashCode` use only a
   `final` id.
2. **The object needs to change over time.** Record fields are always final. You can only
   make a new record, which is right for a seat and awkward for something with a lifecycle
   (an event that is drafted, then published, then cancelled).
3. **You need extra hidden state or a parent class.** A record can't declare extra instance
   fields:

   ```java
   record Seat(String row, int number) {
       String section;
   }
   ```

   ```
   Bad.java:2: error: field declaration must be static
       String section;
              ^
     (consider replacing field with record component)
   ```

   It also can't `extend` another class, though it *can* implement interfaces and have methods.
4. **JPA entities (Phase 6).** Database entities need things a record can't do (a no-argument
   constructor, changeable fields), so they're classes.

**A trap to know about even when a record *is* right: it's only shallowly immutable.**

```java
record Order(String customer, List<Seat> seats) {}

List<Seat> seats = new ArrayList<>();
seats.add(new Seat("A", 1));
Order order = new Order("Sam", seats);
System.out.println("Before: " + order);
seats.add(new Seat("A", 2));                // change the list from outside
System.out.println("After:  " + order);
```

```
Before: Order[customer=Sam, seats=[Seat[row=A, number=1]]]
After:  Order[customer=Sam, seats=[Seat[row=A, number=1], Seat[row=A, number=2]]]
```

The record's `seats` field is final, so it always points to the same list. But that list
itself can still be changed, so the "unchangeable" order gained a seat. (F2.5's shallow copy
lesson again.) F3.6 and F3.8 fix this with `List.copyOf` in the constructor.

**One-sentence answer:** a record gives you correct, matching `equals`/`hashCode`/`toString`,
final fields and a final class for free, so it's the default for value objects like `Seat` and
`Money`; don't use it for entities (identity plus change over time, like `Customer`), for
anything that must change or needs a parent class, or for JPA entities, and remember it only
freezes its own fields, not the lists inside them.

---

## Rules so far (quick reference)

1. Every class inherits `equals` and `hashCode` from `Object`. Those versions treat only the
   very same object as equal; they never look at the fields.
2. A `HashSet` or `HashMap` uses `hashCode()` to decide where to look, and `equals()` to
   confirm the match.
3. If a class doesn't define what "equal" means, two objects with the same contents are
   different as far as every collection is concerned, and nothing warns you.
4. To make a class compare by contents, override both `equals(Object o)` and `hashCode()`,
   both `public` and both with `@Override`.
5. `equals` must take an `Object`. `equals(Seat other)` is a separate overloaded method that
   collections never call; `@Override` catches the mistake.
6. In `equals`, return `false` for anything that isn't the right type (this covers `null`),
   then compare the fields: `Objects.equals` for objects, `==` for primitives.
7. Build `hashCode` from exactly the fields `equals` compares (`Objects.hash(...)` does the
   arithmetic). Then equal objects always get equal hash codes.
8. `new` always creates a new object. `equals` and `hashCode` never change that; they only
   decide which objects a collection treats as duplicates.
9. `==` on objects compares the references: it's `true` only when both sides are the very same
   object. It never calls `hashCode()`, doesn't use the JVM's number, and can't be overridden.
10. `getClass() != o.getClass()` accepts only exactly the same class; `instanceof` also accepts
    subclasses. For a class nobody can extend, the two give the same answers.
11. `equals` must be symmetric: if `a.equals(b)` is `true`, then `b.equals(a)` must be `true`
    too. A subclass that adds a field to `equals` breaks this when the parent uses `instanceof`.
12. Make value classes like `Seat` `final` (or use a record) so the subclass problem can't
    arise. And read generated code before trusting it.
13. If you override `equals`, override `hashCode` too, always. `equals` without `hashCode`
    means equal objects get different hash codes, and every hash-based collection loses them.
14. `List.contains` checks every element with `equals` (slow, but needs no hash code).
    `HashSet` and `HashMap` look in one bucket chosen by `hashCode`, and compare hash codes
    before they ever call `equals`.
15. Never change a field used in `hashCode` while the object is inside a `HashSet` or used as a
    `HashMap` key. The set remembers the old hash, so the object gets lost: `contains` and
    `remove` fail even with the very same object, and `add` lets it in twice.
16. The real fix is to make such fields `final` (or use a record), so the change can't compile.
    If you truly must change one: `remove`, change, then `add` again.
17. The contract has three parts: equal objects **must** have equal hash codes; unequal objects
    **may** share one; the hash code **must not change** while the object is in a hash-based
    collection. Only the first and third cause wrong answers when broken.
18. Sharing hash codes is legal but slow. A hash code that always returns `42` gives correct
    answers, but put 20,000 seats in and the set takes ~900 ms instead of ~3 ms. Use all the
    fields that `equals` uses, so different objects usually get different hash codes.
19. `HashMap` calls `hashCode()` **once** per `put`/`get`, on the key you pass, to pick the
    bucket. Then for each entry in that bucket it checks, in order: same stored hash? same
    object (`==`)? `equals`? So `equals` only runs when the hash codes already match.
20. `put` with an equal key **replaces the value** and returns the old one (the key stays);
    `get` returns `null` when nothing matches. A `HashSet` is a `HashMap` inside: `add(x)` is
    `put(x, dummy)`.
21. For value-like data, use a `record` by default: `record Seat(String row, int number) {}`
    gives `private final` fields, a constructor, accessors (`row()`, `number()`), and correct
    `equals`, `hashCode` and `toString`. It's `final`, so no subclass can break symmetry, and
    its fields can't change, so it can't get lost in a set.
22. Read a record's data with `seat.row()` (method call), not `seat.row`. To "change" a
    record, make a new one.
23. Two kinds of equality. **Value objects** (`Seat`, `Money`) are equal when *all their
    contents* are equal, so use a record. **Entities** (`Customer`, `Event`) are equal when their
    *id* is equal, whatever else has changed. Write `equals` and `hashCode` from the id only.
24. An entity's id must be `final` and set when the object is created. Its other fields can
    change safely, because they're not in `hashCode`.

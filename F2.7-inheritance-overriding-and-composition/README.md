# Task F2.7 — Inheritance, overriding, and why you will usually prefer composition

Beginner-friendly study notes for Task F2.7 of the Zenith plan. Each subtask is added here
as we go through it. Every code example and output below was compiled and run on Java 21.

## Subtasks covered so far

- [x] Subtask 1 — A child class that replaces a parent's method
- [x] Subtask 2 — `super`, and which constructor runs first
- [x] Subtask 3 — Break it on purpose: the fragile base class
- [x] Subtask 4 — The classic LSP violation (Square and Rectangle)
- [x] Subtask 5 — Rewrite the ticket example with composition
- [x] Subtask 6 — Write the rule in your own words
- [x] Subtask 7 — `final` classes and methods
- [ ] Subtask 8 — The two uses in this project (Template Method)

## What this task is about

Inheritance means building a new class on top of an existing one with `extends`. This task
teaches how to do it (subclass, override, `super`). Then comes the important part: you cause
bugs on purpose that show why experienced developers usually avoid inheritance and use
composition instead.

Interviewers rarely ask "what is inheritance?". They ask "when would you use inheritance
instead of composition?", and the best answer uses a bug you have personally seen. The Zenith
project uses inheritance in exactly one place on purpose (Phase 1's `AbstractPricingRule`)
and composition everywhere else.

## Where things live

- **This README:** the reference notes for this task.
- **`../NOTES.md`:** your own notes, in your own words. The plan asks for these.
- **Your code for this task:** `TicketDemo.java` (Subtasks 1–3), `ShapeDemo.java`
  (Subtask 4), and `composition/TicketDemo.java` (Subtask 5). Type them into files in this
  folder.

To run an example:

```bash
javac TicketDemo.java
java TicketDemo
```

Compile again every time you change the file. Subtask 4 uses `assert`, which only runs if you
start Java with `-ea`: `java -ea ShapeDemo`.

## Key words in plain English

| Word | Meaning |
| --- | --- |
| Inheritance | `class VipTicket extends Ticket`: the child class gets the parent's methods and fields, and can add or replace some. |
| Superclass / subclass | Parent / child. A class can `extends` only one parent, but can `implements` many interfaces. |
| Override | The child writes a method with the same name and parameters as a parent method, replacing it. The object's real type decides which version runs. |
| `@Override` | Asks the compiler to check that you really are replacing a parent method. Without it, a typo silently creates a new, unused method. |
| `super` | The parent's version. `super.price()` runs the parent's method; `super(...)` runs the parent's constructor. |
| Constructor chaining | Creating a child object always runs the parent's constructor first. |
| Dynamic dispatch | Java chooses which version of a method to run while the program runs, based on the object's actual type. |
| Composition | Holding another object in a field and using it, instead of inheriting from it. "Has-a" instead of "is-a". (Subtask 5) |
| Delegation | Passing a call on to the object you hold. How composition works day to day. (Subtask 5) |
| Fragile base class | A change inside a parent silently breaks a child. (Subtask 3) |
| LSP (Liskov Substitution Principle) | Anywhere the parent works, the child must work too. (Subtask 4) |
| `final` class / method | A `final` class can't be extended; a `final` method can't be overridden. (Subtask 7) |

---

## Subtask 1 — A child class that replaces a parent's method

> **From the plan:** Write `class Ticket` with a `price()` method, then `class VipTicket extends
> Ticket` overriding `price()`. Put a `VipTicket` in a `Ticket` variable and call `price()`.
> The subclass version runs. That is dynamic dispatch — the same mechanism as Task F2.6.

### What it's asking

1. A normal class `Ticket` with a method `price()` that returns a number.
2. A second class `VipTicket`, built on top of `Ticket`, with its own, different `price()`.
3. Create a `VipTicket` object and store it in a variable whose type is `Ticket`. Call
   `price()` and see which price comes back. It's the VIP price.

### The ideas

**Inheritance and `extends`.** `class VipTicket extends Ticket` means "a VipTicket is a kind of
Ticket." `VipTicket` automatically gets `Ticket`'s methods without copying them. Methods marked
`private` are an exception. The child can then:
- **add** new methods of its own
- **replace** methods it got from the parent

**Parent and child.** `Ticket` is the superclass (parent) and `VipTicket` is the subclass
(child). A class can `extends` only one parent class, but can `implements` many interfaces
(Task F2.6).

**Overriding.** The child writes a method with the same name and the same parameters as a
parent method. From then on, `VipTicket` objects use the child's version.

**`@Override`.** Tells the compiler: "I mean to replace a parent method. Check that one with this
exact name and parameters exists."

**Dynamic dispatch.** When you call a method, Java decides which version to run while the
program is running, by looking at the actual object, not at the variable's type.

**The two types in one line:**

```java
Ticket vip = new VipTicket();
```

- **Left side, `Ticket`:** the variable's type. It decides which methods you're **allowed to
  call**. The compiler checks this before the program runs.
- **Right side, `new VipTicket()`:** the object's actual type. It decides **which version
  runs**. The JVM decides this while the program runs.

Picture a labelled box. The label ("Ticket") decides which buttons you may press. The thing
inside the box (a VipTicket) decides what happens when you press one. Putting a child object
into a parent-type variable is sometimes called **upcasting**.

### The code

```java
class Ticket {
    int price() {
        return 1000;                        // a normal ticket: ₹1000 (kept simple)
    }

    void printTerms() {
        System.out.println("Non-refundable. Carry a photo ID.");
    }
}

class VipTicket extends Ticket {            // VipTicket IS-A Ticket
    @Override
    int price() {                           // replaces Ticket's price()
        return 2500;
    }
    // printTerms() is NOT written here, but VipTicket still has it (inherited)
}

public class TicketDemo {
    public static void main(String[] args) {
        Ticket normal = new Ticket();
        Ticket vip = new VipTicket();       // VipTicket object, in a Ticket-type variable

        System.out.println("Normal: " + normal.price());
        System.out.println("VIP: " + vip.price());
        vip.printTerms();                   // inherited from Ticket
    }
}
```

Output:

```
Normal: 1000
VIP: 2500
Non-refundable. Carry a photo ID.
```

`javac` makes one `.class` file per class: `Ticket.class`, `VipTicket.class` and
`TicketDemo.class`.

The methods have no `public`, which is fine because everything is in one file. The rule from
F2.6 still applies: an overriding method can't be *more private* than the parent's method.

### What happened, step by step

**`vip.price()`:**
1. **Before the program runs, the compiler checks:** the variable `vip` is labelled `Ticket`.
   Does `Ticket` have `price()`? Yes, so the line is allowed.
2. **While the program runs, the JVM looks inside the box:** the object is a `VipTicket`. Does
   `VipTicket` have its own `price()`? Yes, so it runs that one and you get **2500**.

**`vip.printTerms()`:**
1. The compiler checks that `Ticket` has `printTerms()`. It does, so the line is allowed.
2. At run time, the JVM looks for `printTerms()` in `VipTicket` and doesn't find it. It moves
   **up** to `Ticket`, finds it there, and runs it. That's what "inherited" means in practice.

**The lookup rule:** Java starts at the object's actual class and looks for the method. If it
isn't there, Java moves up to the parent, and keeps going up until it finds it.

You never wrote `if (ticket is VIP) return 2500;`. Java picked the right price by itself,
based on the object. That's dynamic dispatch.

### Same mechanism as Task F2.6

```java
Notifier n = new EmailNotifier();   // F2.6: interface variable, implementation object
Ticket   t = new VipTicket();       // F2.7: parent-class variable, child object
```

In both lines the variable's type is general, the object is specific, and the object decides
which method runs. The difference: an interface has no code to pass down, while a parent class
does. `printTerms()` came to `VipTicket` for free. That free code is the convenience of
inheritance. The later subtasks show what it costs.

### Experiments

**Experiment 1 — the label limits which buttons you can press.** Add this to `VipTicket`:

```java
void loungeAccess() {
    System.out.println("Welcome to the VIP lounge");
}
```

Then try it in `main`:

```java
vip.loungeAccess();                  // compile error

VipTicket realVip = new VipTicket();
realVip.loungeAccess();              // works
```

The compiler says:

```
error: cannot find symbol
        vip.loungeAccess();
           ^
  symbol:   method loungeAccess()
  location: variable vip of type Ticket
```

The object in `vip` really is a `VipTicket`, but the box is labelled `Ticket`, and `Ticket` has
no `loungeAccess()` button.

**Experiment 2 — why `@Override` matters more here than in F2.6.** Remove `@Override` and
misspell the method:

```java
class VipTicket extends Ticket {
    int prise() {                    // typo, and no @Override
        return 2500;
    }
}
```

It **compiles**, and prints `VIP: 1000`. That's a silent bug: VIP customers get charged the
normal price.
- `VipTicket` no longer has a `price()` method, so the lookup moves up to `Ticket`'s `price()`.
- `prise()` is just a new method that nobody calls.

In F2.6 the same typo still failed to compile, because the interface method had no body and
the class was forced to write one. A parent class's method *does* have a body, so nothing forces
the child to write anything.

Put `@Override` back on `prise()` and the compiler stops you:

```
error: method does not override or implement a method from a supertype
```

Changing the parameters, such as `int price(int quantity)`, is the same trap. It creates a new
method with the same name (overloading), not an override, and `@Override` catches it too.

### Check yourself

1. In `Ticket vip = new VipTicket();`, which side decides which `price()` runs, and which side
   decides which methods you're allowed to call?
2. `VipTicket` never wrote `printTerms()`. Explain, step by step, why `vip.printTerms()` works.
3. Why is it called *dynamic* dispatch?

---

## Subtask 2 — `super`, and which constructor runs first

> **From the plan:** Call `super.price()` from inside the override to reuse the parent's
> calculation. Then give both classes constructors with print statements and create a
> `VipTicket`. **The parent constructor runs first**, every time.

### What it's asking

1. **Reuse the parent's method.** Inside `VipTicket`'s `price()`, ask the parent for *its* price
   and build on it, instead of typing a new number from scratch.
2. **Watch the order of construction.** Give both classes a constructor that prints a message,
   create one `VipTicket`, and see which message prints first. The parent's always does.

### The ideas

**`super`** refers to the parent's version of things, for *this same object*:
- `super.price()` means "run my parent's `price()`, not my own."
- `super(...)` means "run my parent's constructor." You can only write it inside a constructor.

**Constructor (refresher from F2.2).** The special block that runs once, automatically, when
you write `new`. Its job is to set up the new object. It has the same name as the class and no
return type:

```java
class Ticket {
    Ticket() {          // constructor: same name as the class, no return type
        // set-up code goes here
    }
}
```

**Constructor chaining.** Creating a child object always builds the **parent part first**, then
the child part. Think of a building: the ground floor (`Ticket`) has to exist before the first
floor (`VipTicket`) can sit on top of it.

### Part A — Reuse the parent's calculation with `super.price()`

```java
class Ticket {
    int price() {
        return 1000;
    }
}

class VipTicket extends Ticket {
    @Override
    int price() {
        return super.price() + 1500;   // parent's price (1000) + VIP extra (1500)
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        Ticket vip = new VipTicket();
        System.out.println("VIP price: " + vip.price());   // VIP price: 2500
    }
}
```

**What happens when Java runs `vip.price()`:**
1. The object is a `VipTicket`, so dynamic dispatch runs **`VipTicket`'s `price()`**.
2. Inside it, `super.price()` jumps to **`Ticket`'s `price()`**, which returns 1000.
3. Back in `VipTicket`'s version, 1000 + 1500 = **2500** is returned.

**Why reuse instead of just writing `return 2500;`?** Because the normal price now lives in
**one place**. Change `Ticket`'s `1000` to `1200` and the VIP price becomes 2700 by itself.

**Experiment — delete the word `super`.** Change the line to `return price() + 1500;`. The
program crashes:

```
Exception in thread "main" java.lang.StackOverflowError
    at VipTicket.price(TicketDemo.java:10)
    at VipTicket.price(TicketDemo.java:10)
    at VipTicket.price(TicketDemo.java:10)
    ...
```

(The line number depends on your file.)

- A plain `price()` means "call *this object's* `price()`." Dynamic dispatch picks `VipTicket`'s
  `price()`, which is the method you're already in.
- So the method calls itself, which calls itself again, forever.
- Every method call uses a little memory (called the **call stack**). Eventually that memory
  runs out, and Java throws a `StackOverflowError`.

**The difference to remember:**
- **`price()`** always goes through dynamic dispatch, starting from the object's actual class.
- **`super.price()`** skips your own version and goes straight to the parent's.

### Part B — Watch the order of construction

```java
class Ticket {
    Ticket() {
        System.out.println("1. Ticket constructor runs");
    }

    int price() {
        return 1000;
    }
}

class VipTicket extends Ticket {
    VipTicket() {
        // super();   <-- you didn't write this, but Java secretly puts it here
        System.out.println("2. VipTicket constructor runs");
    }

    @Override
    int price() {
        return super.price() + 1500;
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        System.out.println("Creating a VipTicket...");
        Ticket vip = new VipTicket();
        System.out.println("VIP price: " + vip.price());
    }
}
```

Output:

```
Creating a VipTicket...
1. Ticket constructor runs
2. VipTicket constructor runs
VIP price: 2500
```

**Why the parent prints first.** The **very first thing every constructor does is call its
parent's constructor**. If you don't write that call yourself, Java silently adds `super();` as
the first line. So the steps for `new VipTicket()` are:
1. `VipTicket`'s constructor starts. Its first (invisible) line is `super();`, so it immediately
   hands over to the parent.
2. `Ticket`'s constructor starts. It also has an invisible `super();`, which calls the
   constructor of `Object`. **Every class that doesn't say `extends` automatically extends
   `Object`**, the root of all Java classes. `Object`'s constructor does nothing visible.
3. Control comes back to `Ticket`'s constructor, and its body runs. It prints **"1. Ticket
   constructor runs"**.
4. Control comes back to `VipTicket`'s constructor, and the rest of its body runs. It prints
   **"2. VipTicket constructor runs"**.

So `VipTicket`'s constructor is *called* first, but it passes control upward straight away. The
parent's code finishes before the child's own code runs. The chain is always
**Object → Ticket → VipTicket**.

**This does NOT create two objects.** `new VipTicket()` creates **exactly one object**.
`Ticket`'s constructor isn't building a separate `Ticket`; it's setting up the *Ticket part* of
that one `VipTicket` object. In the same way, `super.price()` doesn't create anything. It runs
the parent's version of the method on the same object.

### Part C — `super(...)` when the parent needs information

Give `Ticket` a constructor that *requires* an event name:

```java
class Ticket {
    private final String eventName;

    Ticket(String eventName) {                 // the ONLY constructor needs a name
        this.eventName = eventName;
        System.out.println("1. Ticket constructor runs for " + eventName);
    }
}

class VipTicket extends Ticket {
    VipTicket() {
        System.out.println("2. VipTicket constructor runs");
    }
}
```

This **won't compile**:

```
error: constructor Ticket in class Ticket cannot be applied to given types;
    VipTicket() {
                ^
  required: String
  found:    no arguments
  reason: actual and formal argument lists differ in length
```

The error is about the invisible `super();` call:
- Java added `super();` to `VipTicket`'s constructor. That means "call `Ticket`'s constructor
  with **no** arguments."
- `Ticket` has no such constructor. Its only constructor needs a `String`.

The fix is for the child to pass the information up itself:

```java
class VipTicket extends Ticket {
    VipTicket(String eventName) {
        super(eventName);                      // must be the FIRST line
        System.out.println("2. VipTicket constructor runs");
    }
}

// in main:
Ticket vip = new VipTicket("Coldplay Mumbai");
```

Output:

```
1. Ticket constructor runs for Coldplay Mumbai
2. VipTicket constructor runs
```

**`super(...)` must be the first line.** Move the print statement *above* `super(eventName);`
and Java 21, the version this plan uses, refuses:

```
error: call to super must be first statement in constructor
```

It's the building rule again: the parent part must be fully built before the child's code does
anything.

### Check yourself

1. Inside `VipTicket`, what's the difference between calling `price()` and calling
   `super.price()`? What happened when you removed `super`, and why?
2. You never wrote `super();` in `VipTicket`'s constructor. Why does `Ticket`'s constructor
   still run first?
3. After `new VipTicket()`, how many objects exist?
4. If `Ticket`'s only constructor takes a `String`, what must `VipTicket`'s constructor do, and
   on which line?

---

## Subtask 3 — Break it on purpose: the fragile base class

> **From the plan:** Add `total()` to `Ticket` that calls `price()`. Have the parent constructor
> call `total()`. In `VipTicket`, make `price()` depend on a field assigned in the `VipTicket`
> constructor. Create one. The field is still `null`/`0`, because the parent constructor ran
> before it was set. Write it up in `NOTES.md`: **this is a real bug people ship**, and it exists
> only because inheritance let the parent call into the child before the child was ready.

### What it's asking

You build a chain of calls that runs from the parent's constructor into the child's code
before the child is ready:
1. Add a method `total()` to `Ticket` that calls `price()` (the price plus a booking fee).
2. Make `Ticket`'s constructor call `total()`.
3. Give `VipTicket` a field, `vipSurcharge`, that gets its value in `VipTicket`'s constructor.
   Make `VipTicket`'s `price()` use that field.
4. Create a `VipTicket` and watch the total come out wrong, because the field hasn't been set
   yet.
5. Write up what happened in your `NOTES.md`.

### The ideas

**Fields start with default values.** When Java creates an object, before any constructor code
runs, it sets every field to a default: `0` for numbers, `false` for booleans, and `null` for
objects such as a `String`. A field only gets your value when the line that sets it actually
runs.

**The full order of building an object.** This extends the order from Subtask 2:
1. Java reserves memory for the whole object (its Ticket part and its VipTicket part) and sets
   every field to its default. `vipSurcharge` is `0`.
2. `VipTicket`'s constructor starts. Its first line is `super()`, so it hands over to the
   parent straight away.
3. `Ticket`'s constructor runs completely.
4. Back in `VipTicket`, its fields get their values: first any value written directly on a
   field (like `= 1500`), then the rest of the constructor body (like
   `this.vipSurcharge = vipSurcharge;`).

Step 3 happens before step 4. So while the parent's constructor is running, the child's fields
still hold their default values.

**Dynamic dispatch still works inside the parent's constructor.** From step 1, the object
already *is* a `VipTicket`. So if `Ticket`'s constructor calls a method that `VipTicket`
overrides, the lookup rule from Subtask 1 applies, and `VipTicket`'s version runs, even though
`VipTicket`'s constructor hasn't finished.

Put those two facts together and you get the bug: the parent's constructor calls into the
child's method, and the child's method reads a field that hasn't been set yet.

### The code

```java
class Ticket {
    Ticket() {
        System.out.println("1. Ticket constructor runs and calls total()");
        System.out.println("   total = " + total());
    }

    int price() {
        return 1000;
    }

    int total() {
        int bookingFee = 50;
        return price() + bookingFee;        // which price()? the object's own version
    }
}

class VipTicket extends Ticket {
    private int vipSurcharge;               // starts as 0 (the default)

    VipTicket(int vipSurcharge) {
        // super();  <-- runs first, invisibly (Subtask 2)
        this.vipSurcharge = vipSurcharge;   // this.vipSurcharge = the field, vipSurcharge = the parameter
        System.out.println("2. VipTicket constructor sets vipSurcharge = " + this.vipSurcharge);
    }

    @Override
    int price() {
        System.out.println("   VipTicket.price() sees vipSurcharge = " + vipSurcharge);
        return super.price() + vipSurcharge;
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        Ticket vip = new VipTicket(1500);
        System.out.println("3. Asking for the total again, after construction:");
        System.out.println("   total = " + vip.total());
    }
}
```

Output:

```
1. Ticket constructor runs and calls total()
   VipTicket.price() sees vipSurcharge = 0
   total = 1050
2. VipTicket constructor sets vipSurcharge = 1500
3. Asking for the total again, after construction:
   VipTicket.price() sees vipSurcharge = 1500
   total = 2550
```

During construction the total was **1050**. It should have been 1000 + 1500 + 50 = **2550**.

### What happened, step by step

1. `new VipTicket(1500)` reserves memory. `vipSurcharge` is `0`.
2. `VipTicket`'s constructor starts, and its invisible `super()` hands over to `Ticket`'s
   constructor.
3. `Ticket`'s constructor prints line 1, then calls `total()`.
4. `total()` calls `price()`. The object is a `VipTicket`, so `VipTicket`'s `price()` runs.
5. `VipTicket`'s `price()` reads `vipSurcharge`, which is still `0`. It returns 1000 + 0 = 1000.
6. `total()` returns 1000 + 50 = **1050**. That's the wrong total.
7. `Ticket`'s constructor finishes. Only now does `VipTicket`'s constructor body run and set
   `vipSurcharge` to 1500. Too late: the wrong total has already been worked out. In a real
   system it might already have been saved, logged, or charged.
8. Afterwards, `vip.total()` gives the right answer, **2550**.

Step 8 is what makes this bug so hard to catch. If you test the object *after* creating it,
everything looks fine. It's only wrong during construction.

The `int` version doesn't even crash. It quietly produces a wrong number, which in a ticketing
system means wrong money.

### Experiment 1 — the `null` version: this time it crashes

Use a `String` field instead of a number. Keep the same `Ticket` class and change `VipTicket`
and `main`:

```java
class VipTicket extends Ticket {
    private String tier;                    // starts as null (the default)

    VipTicket(String tier) {
        this.tier = tier;
        System.out.println("2. VipTicket constructor sets tier = " + this.tier);
    }

    @Override
    int price() {
        if (tier.equals("PLATINUM")) {      // tier is still null during construction
            return super.price() + 3000;
        }
        return super.price() + 1500;
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        Ticket vip = new VipTicket("PLATINUM");
        System.out.println("total = " + vip.total());
    }
}
```

Output:

```
1. Ticket constructor runs and calls total()
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "String.equals(Object)" because "this.tier" is null
    at VipTicket.price(TicketDemo.java:27)
    at Ticket.total(TicketDemo.java:13)
    at Ticket.<init>(TicketDemo.java:4)
    at VipTicket.<init>(TicketDemo.java:20)
    at TicketDemo.main(TicketDemo.java:36)
```

- `tier` is still `null`, and calling `.equals(...)` on `null` throws a `NullPointerException`.
  The message even names the field: `"this.tier" is null`.
- Read the `at ...` lines from the bottom up and they tell the whole story: `main` called the
  `VipTicket` constructor, which called the `Ticket` constructor, which called `total()`, which
  called `VipTicket`'s `price()`, which crashed. (`<init>` is how Java names a constructor in
  these lists.) You can see the parent's constructor calling into the child.
- Line 2 never printed. The child's constructor body never got the chance to run.

Which is worse? The `null` version crashes loudly, so you find it quickly. The `0` version runs
and gives a wrong answer, so you might not find it for weeks.

### Experiment 2 — "What if I set the value right on the field?"

Give the field its value where it's declared, and leave the constructor empty:

```java
class VipTicket extends Ticket {
    private int vipSurcharge = 1500;        // value written directly on the field

    VipTicket() {
        System.out.println("2. VipTicket constructor runs, vipSurcharge = " + vipSurcharge);
    }

    @Override
    int price() {
        System.out.println("   VipTicket.price() sees vipSurcharge = " + vipSurcharge);
        return super.price() + vipSurcharge;
    }
}
// in main: Ticket vip = new VipTicket();
```

Output:

```
1. Ticket constructor runs and calls total()
   VipTicket.price() sees vipSurcharge = 0
   total = 1050
2. VipTicket constructor runs, vipSurcharge = 1500
```

It's **still 0**. A value written on the field is set in step 4, exactly like code in the
constructor: after the parent's constructor has finished.

One exception, so it doesn't confuse you if you try it: add `final`
(`private final int vipSurcharge = 1500;`) and you'll see 1500. When a `final` field is set to a
fixed number or piece of text right where it's declared, the compiler copies that value straight
into the code that uses it. That's a special case. A value worked out while the program runs,
such as one passed into the constructor, still shows the bug even when the field is `final`.

### Why this is called the "fragile base class" problem

"Base class" is another name for the parent class. "Fragile" means easy to break.

Look at it from the point of view of whoever wrote `VipTicket`. Their code is correct on its
own: it sets its field in its constructor and uses it in `price()`. It broke because of a detail
*inside* `Ticket`: `Ticket`'s constructor calls `total()`, which calls `price()`. You can't see
that from outside `Ticket`, and nothing warned them.

Now imagine how this happens in a real team:
- **Monday:** `Ticket`'s constructor doesn't call `total()`. `VipTicket` works perfectly.
- **Tuesday:** someone adds one harmless-looking line to `Ticket`'s constructor, perhaps to log
  the total. They test `Ticket` and it works. They never open `VipTicket`.
- **From then on:** every `VipTicket` works out the wrong total during construction, or crashes.
  Nobody touched `VipTicket`.

That's the fragile base class problem: **a change inside the parent silently breaks a child,
because inheritance ties the child to the parent's internal details** (here, which methods the
parent calls, and when). The plan's phrase "inheritance let the parent call into the child
before the child was ready" describes steps 3 to 5 above.

### How to avoid it

**A constructor should never call a method that a child can override, directly or through
another method.** Here the constructor called `total()`, and `total()` called `price()`, which
`VipTicket` overrides.

If a constructor needs a helper method, make that helper `private` or `final`, so no child can
replace it. Or work the value out later, when someone asks for it, instead of during
construction. Subtask 5 shows the bigger fix: composition.

### Write it up in your `NOTES.md`

The plan wants this in **your** `NOTES.md` (your own words, at the top of the `foundations`
folder), not only in this README. Cover:
- what you built, in one or two sentences
- what you expected, and what actually happened
- why, in order: the construction order plus dynamic dispatch
- why it's a real bug people ship
- the rule you'll follow

Write yours first, then compare it with this example:

```markdown
## F2.7 — The constructor-ordering bug (fragile base class)
- Ticket's constructor called total(), and total() called price().
- VipTicket overrides price() to add vipSurcharge, a field it sets in its own constructor.
- new VipTicket(1500) worked out total = 1050 during construction instead of 2550.
- Why: Ticket's constructor runs before VipTicket's constructor body, so vipSurcharge
  still had its default value, 0. Dynamic dispatch still sent the price() call to
  VipTicket, because the object was already a VipTicket.
- With a String field, the same bug crashes with a NullPointerException instead.
- Why it's a real bug people ship: it's silent, it only happens during construction,
  and it can appear when someone edits the parent without ever touching the child.
- My rule: never call an overridable method from a constructor.
```

### Check yourself

1. Walk through, in order, everything that happens when `new VipTicket(1500)` runs. At which
   step does `vipSurcharge` become 1500?
2. The call to `price()` came from inside `Ticket`'s constructor. Why did `VipTicket`'s version
   run?
3. Why did `vip.total()` give the right answer afterwards, and why does that make this bug
   harder to catch?
4. `VipTicket`'s code looked correct. Whose change caused the break, and why is that called
   "fragile base class"?
5. What rule prevents this bug?

---

## Subtask 4 — The classic LSP violation: Square and Rectangle

> **From the plan:** Write `Rectangle` with `setWidth`/`setHeight`/`area`, then `Square extends
> Rectangle` overriding both setters to keep the sides equal. Now write a method that takes a
> `Rectangle`, sets width 5 and height 4, and asserts the area is 20. Pass it a `Square`. It
> fails. Note what that proves: "a square is a rectangle" is true in geometry and false in code,
> and `extends` is a much stronger claim than it looks.

### What it's asking

1. Write a `Rectangle` class. You can set its width, set its height, and ask for its area.
2. Write `Square extends Rectangle`. A square's sides must always be equal, so `Square`
   overrides both setters: setting one side sets both.
3. Write a method that accepts any `Rectangle`, makes it 5 wide and 4 tall, and checks that the
   area is 20.
4. Pass it a real `Rectangle` (the check passes), then a `Square` (the check fails).
5. Write down what this proves.

### The ideas

**LSP (Liskov Substitution Principle).** Named after the computer scientist Barbara Liskov. In
plain words: **if some code works correctly with a parent object, it must keep working correctly
when you hand it a child object instead.** "Substitution" just means swapping one in for the
other. The child may do things differently (that's what overriding is for), but it must never
break what the parent promised.

**A class's promises.** Code that uses a `Rectangle` relies on things nobody wrote down:
- setting the width doesn't change the height, and setting the height doesn't change the width
- after `setWidth(5)` and `setHeight(4)`, the area is 5 × 4 = 20

These unwritten promises are part of what "being a Rectangle" means, just as much as the method
names are. A child class has to keep all of them.

**The warning sign.** The plan says: "If a caller has to check which subclass it has, the
hierarchy is wrong." In Subtask 1 and in F2.6, the whole point was that the caller doesn't need
to know which object it's holding. If a caller has to ask "is this actually a Square?", that
benefit is gone.

**`assert`.** `assert condition : message;` means "this must be true at this point. If it
isn't, stop the program with an `AssertionError` that shows the message." Java **skips asserts
unless you run with `-ea`** ("enable assertions"). Real projects use test frameworks for this
instead (Module F7), but the idea is the same.

### The code

Create `ShapeDemo.java`:

```java
class Rectangle {
    private int width;
    private int height;

    void setWidth(int width) {
        this.width = width;
    }

    void setHeight(int height) {
        this.height = height;
    }

    int area() {
        return width * height;
    }

    String describe() {
        return width + " x " + height;
    }
}

class Square extends Rectangle {
    @Override
    void setWidth(int side) {
        super.setWidth(side);               // keep both sides equal
        super.setHeight(side);
    }

    @Override
    void setHeight(int side) {
        super.setWidth(side);               // keep both sides equal
        super.setHeight(side);
    }
}

public class ShapeDemo {
    static void resizeAndCheck(Rectangle r) {             // written for ANY Rectangle
        r.setWidth(5);
        r.setHeight(4);
        int area = r.area();
        String type = r.getClass().getSimpleName();      // the object's real class name
        System.out.println(type + " " + r.describe() + ", area = " + area);
        assert area == 20 : "Expected area 20 but got " + area;
    }

    public static void main(String[] args) {
        resizeAndCheck(new Rectangle());
        resizeAndCheck(new Square());
    }
}
```

A few small things in this code:
- `width` and `height` are `private`, so `Square` can't change them directly. It uses
  `super.setWidth(...)` and `super.setHeight(...)` (Subtask 2) to run `Rectangle`'s setters.
- `describe()` just returns the two sides as text, such as `5 x 4`, so you can see the shape.
- `r.getClass().getSimpleName()` asks the object for the name of its real class, such as
  `Square`. It shows the object's actual type, whatever the variable says.

Run it with assertions switched on:

```bash
javac ShapeDemo.java
java -ea ShapeDemo
```

(If you use an editor's Run button, add `-ea` to the VM options of its run configuration.)

Output:

```
Rectangle 5 x 4, area = 20
Square 4 x 4, area = 16
Exception in thread "main" java.lang.AssertionError: Expected area 20 but got 16
    at ShapeDemo.resizeAndCheck(ShapeDemo.java:43)
    at ShapeDemo.main(ShapeDemo.java:48)
```

### What happened, step by step

With the real `Rectangle`, `setWidth(5)` sets the width, `setHeight(4)` sets the height, and the
area is 20. The check passes.

With the `Square`:
1. `r.setWidth(5)`: the object is a `Square`, so dynamic dispatch runs `Square`'s `setWidth`.
   Both sides become 5. The shape is **5 x 5**.
2. `r.setHeight(4)`: `Square`'s `setHeight` runs. Both sides become 4. The shape is **4 x 4**.
   The width you set a moment ago has silently changed from 5 to 4.
3. The area is **16**, not 20, and the assert stops the program.

Notice that `resizeAndCheck` did nothing wrong. It's ordinary code for a rectangle, and it relied
on a rectangle's promise. `Square` broke that promise. And `resizeAndCheck` had no way of knowing
it had been handed a `Square`: dynamic dispatch worked exactly as designed. That's what makes
this kind of bug dangerous.

### Experiment 1 — run it without `-ea`

Run `java ShapeDemo` (no `-ea`). Both lines print, including `area = 16`, and there's **no
error**. The assert was skipped. A check that isn't switched on protects nothing.

### Experiment 2 — set the sides in the other order

In `resizeAndCheck`, swap the two lines so `setHeight(4)` comes before `setWidth(5)`. Output:

```
Rectangle 5 x 4, area = 20
Square 5 x 5, area = 25
```

Now the answer depends on which setter you called last. With a real rectangle the order never
matters. That's another broken promise. Swap the lines back afterwards.

### Experiment 3 — "Then don't override the setters"

Delete both methods from `Square`, leaving just `class Square extends Rectangle { }`. Output:

```
Rectangle 5 x 4, area = 20
Square 5 x 4, area = 20
```

The check passes, but look at the shape: a "Square" that is 5 wide and 4 tall. It isn't a square
any more.

This is the trap:
- **Override the setters:** `Square` keeps its own rule (equal sides) but breaks `Rectangle`'s
  promise (the sides change independently).
- **Don't override them:** `Square` keeps `Rectangle`'s promise but breaks its own rule.

There's no correct way to write `Square extends Rectangle` while `Rectangle` has these setters.
When every version is wrong, the problem isn't the code inside `Square`. It's the `extends`
itself.

### What this proves

**"A square is a rectangle" is true in geometry and false in code.**
- In geometry, a square is a rectangle with equal sides. It's a fact about shapes drawn on paper,
  and shapes on paper never change size.
- In code, a `Rectangle` object is defined by what you can *do* with it. Here, that includes
  changing its width and height separately, and a `Square` can't support that.
- So `extends` doesn't mean the everyday English "is a". It's a much bigger promise: **anything
  that works with a `Rectangle` will also work, unchanged, with a `Square`.** That's exactly what
  LSP says, and `Square` can't keep it.

A better test than "does the sentence 'X is a Y' sound true?" is: **can X do everything Y
promises, in every situation, without the caller noticing the difference?** For `Square` and this
`Rectangle`, the answer is no.

### The tempting "fix" that makes it worse

```java
static void resizeAndCheck(Rectangle r) {
    if (r instanceof Square) {      // "is this object actually a Square?"
        // special handling for squares ...
    } else {
        r.setWidth(5);
        r.setHeight(4);
    }
    // ...
}
```

`instanceof` asks whether an object really is of a particular class. This "works" today, but the
method now has to know about every subclass of `Rectangle`. Every new subclass means finding and
editing every method like this one. You've lost what made polymorphism useful: the caller no
longer gets to ignore which object it's holding. This is the warning sign the plan describes.

### How you'd design it instead (not required for this subtask)

Stop claiming that a `Square` is a `Rectangle`. Give them a shared interface that promises only
what both can keep:

```java
interface Shape {
    int area();
}

class Rectangle implements Shape {
    private final int width;
    private final int height;

    Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int area() {
        return width * height;
    }
}

class Square implements Shape {
    private final int side;

    Square(int side) {
        this.side = side;
    }

    @Override
    public int area() {
        return side * side;
    }
}
```

`new Rectangle(5, 4).area()` gives 20 and `new Square(4).area()` gives 16. `Shape` only promises
that a shape can tell you its area, and both classes keep that promise. There are no setters, so
there's nothing for `Square` to break. (Objects that can't change after they're created are called
*immutable*. Task F3.8 covers them.)

### Write it up in your `NOTES.md`

Cover what you ran, what happened, and what it proves. Write yours first, then compare:

```markdown
## F2.7 — Square extends Rectangle breaks LSP
- resizeAndCheck(Rectangle r) sets width 5 and height 4, then asserts area == 20.
- A real Rectangle passes. A Square gives 16 (4 x 4): its setHeight also changed the width.
- In the other order (height first, then width), the Square gives 25 (5 x 5).
- If Square doesn't override the setters, the check passes but the Square is 5 x 4:
  not a square. So there's no correct way to write Square extends Rectangle here.
- Proves: "a square is a rectangle" is true in geometry, false in code. extends promises
  that the child works everywhere the parent works (that's LSP).
- Warning sign: a caller needing "if (r instanceof Square)".
```

### Check yourself

1. State the Liskov Substitution Principle in your own words.
2. Walk through what happens to the width and height when `resizeAndCheck` runs on a `Square`.
3. `resizeAndCheck` has no bug in it. So where is the bug?
4. Why can't you fix this by removing the setter overrides from `Square`?
5. Why is `if (r instanceof Square)` inside `resizeAndCheck` a bad fix?

---

## Subtask 5 — Rewrite the ticket with composition

> **From the plan:** Rewrite the ticket example with **composition**: `class VipTicket { private
> final Ticket base; private final Discount discount; }`, delegating to `base`. Compare in
> `NOTES.md` — what could the inheritance version reach that the composition version cannot, and
> why is that reach a liability rather than a convenience?

### What it's asking

1. Rewrite `VipTicket` so that it **doesn't** `extends Ticket`. Instead it holds two objects in
   fields: a `Ticket` called `base`, and a `Discount`.
2. When someone asks a `VipTicket` for its price, it asks `base` for the normal price, adds the
   VIP extra, and lets the `Discount` adjust the result. Passing a request on to an object you
   hold is called **delegating**.
3. In your `NOTES.md`, compare the two versions. What could the inheritance version reach (see,
   change, or be affected by) that this version can't? And why is that reach dangerous rather
   than helpful?

### The ideas

**Composition ("has-a").** Instead of saying "a VipTicket *is a* Ticket" (`extends`), you say "a
VipTicket *has a* Ticket": it keeps a `Ticket` object in a field and uses it. Building a class out
of other objects like this is called composition.

**Delegation.** Passing a call on to the object you hold. `base.price()` means "ask the `Ticket`
I'm holding for its price." It's the everyday mechanics of composition.

**Separate objects instead of one.** This is the biggest change from the inheritance version.
- With inheritance, `new VipTicket()` made **one** object with a Ticket part inside it
  (Subtask 2). `super.price()` ran the parent's version of the method on that same object.
- With composition there are **three** separate objects: a `VipTicket`, a `Ticket`, and a
  `Discount`. The `VipTicket` just keeps references to the other two in its fields.
  `base.price()` asks a different object.

### The code

Keep your inheritance version for comparison: make a new folder called `composition` inside this
F2.7 folder, and create a new `TicketDemo.java` there.

```java
interface Discount {
    int applyTo(int price);
}

class NoDiscount implements Discount {
    @Override
    public int applyTo(int price) {
        return price;                               // price unchanged
    }
}

class PercentOff implements Discount {
    private final int percent;

    PercentOff(int percent) {
        this.percent = percent;
    }

    @Override
    public int applyTo(int price) {
        return price - price * percent / 100;       // e.g. 10% off
    }
}

class Ticket {
    int price() {
        return 1000;
    }

    void printTerms() {
        System.out.println("Non-refundable. Carry a photo ID.");
    }
}

class VipTicket {                                   // no "extends" any more
    private final Ticket base;                      // VipTicket HAS-A Ticket
    private final Discount discount;                // ... and HAS-A Discount

    VipTicket(Ticket base, Discount discount) {
        this.base = base;
        this.discount = discount;
    }

    int price() {
        int vipPrice = base.price() + 1500;         // was: super.price() + 1500
        return discount.applyTo(vipPrice);          // let the discount adjust it
    }

    void printTerms() {
        base.printTerms();                          // delegation: pass the call on
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        VipTicket vip = new VipTicket(new Ticket(), new NoDiscount());
        VipTicket earlyBirdVip = new VipTicket(new Ticket(), new PercentOff(10));

        System.out.println("VIP: " + vip.price());
        System.out.println("Early-bird VIP: " + earlyBirdVip.price());
        vip.printTerms();
    }
}
```

Output:

```
VIP: 2500
Early-bird VIP: 2250
Non-refundable. Carry a photo ID.
```

`Discount` works exactly like `PricingRule` and `Notifier` from Task F2.6: an interface with two
implementations, `NoDiscount` and `PercentOff`. Their `applyTo` methods are `public` because
interface methods are always public (the F2.6 rule).

### What happened, step by step

**Building it:** `new VipTicket(new Ticket(), new PercentOff(10))` builds the arguments first. The
`Ticket` object is built completely, then the `PercentOff` object, and only then does
`VipTicket`'s constructor run and store the two finished objects in its fields.

**`earlyBirdVip.price()`:**
1. `VipTicket`'s own `price()` runs. There's no parent version to choose between any more.
2. `base.price()` asks the `Ticket` it holds for its price, and gets 1000. That's delegation.
3. 1000 + 1500 = 2500.
4. `discount.applyTo(2500)` asks the `Discount` it holds. It's a `PercentOff(10)`, so dynamic
   dispatch runs `PercentOff`'s `applyTo`: 2500 − 250 = **2250**.

Compare the key line with Subtask 2:

```java
return super.price() + 1500;          // inheritance: my parent's version, on this same object
int vipPrice = base.price() + 1500;   // composition: ask the Ticket object I'm holding
```

**Mix and match without new classes.** A VIP ticket with no discount and a VIP ticket with 10% off
are the same class, just given different `Discount` objects. With inheritance, every combination
tends to become its own subclass (`DiscountedTicket`, `DiscountedVipTicket`,
`EarlyBirdVipTicket`, ...), and the number of classes grows with every new option. With
composition you combine objects instead. This is the same idea as the plan's `PricedEvent`
example and F2.6's Strategy pattern.

### Experiment 1 — bring back the Subtask 3 trap

Give `Ticket` the dangerous constructor from Subtask 3 again, and add a print to `VipTicket`'s
constructor:

```java
class Ticket {
    Ticket() {
        System.out.println("Ticket constructor: total = " + total());   // the Subtask 3 trap
    }

    int price() {
        return 1000;
    }

    int total() {
        int bookingFee = 50;
        return price() + bookingFee;
    }
}

// in VipTicket's constructor, after the two assignments:
System.out.println("VipTicket constructor runs");

// in main:
VipTicket vip = new VipTicket(new Ticket(), new NoDiscount());
System.out.println("VIP: " + vip.price());
```

Output:

```
Ticket constructor: total = 1050
VipTicket constructor runs
VIP: 2500
```

Both numbers are right:
- 1050 is the correct total for a plain ticket. `total()` called `price()` and got **Ticket's
  own** `price()`, because nothing overrides it any more.
- There's no route from `Ticket`'s code into `VipTicket`'s code. `Ticket` doesn't even know
  `VipTicket` exists, and its constructor finished before `VipTicket`'s constructor started.

The Subtask 3 bug isn't avoided by being careful. The design simply leaves no way for it to
happen.

### Experiment 2 — the parent grows a new method

Imagine someone adds this to `Ticket` next month:

```java
int studentPrice() {
    return price() / 2;      // students pay half
}
```

- **In the inheritance version** (Subtask 2's `VipTicket extends Ticket`), `vip.studentPrice()`
  compiles and prints `VIP student price: 1250`. Every VIP ticket silently gained a half-price
  student option that nobody decided to offer. The 1250 came from dynamic dispatch: a method
  written for plain tickets called the VIP `price()`.
- **In the composition version**, `vip.studentPrice()` doesn't compile:

```
error: cannot find symbol
        System.out.println("VIP student price: " + vip.studentPrice());
                                                      ^
  symbol:   method studentPrice()
  location: variable vip of type VipTicket
```

`VipTicket` only has the methods you wrote. If VIP tickets should have a student price, someone
has to add it on purpose and decide what it should be.

### The comparison the subtask asks for

**What could the inheritance version reach that the composition version cannot?**

| | Inheritance (`VipTicket extends Ticket`) | Composition (`VipTicket` holds a `Ticket`) |
| --- | --- | --- |
| Change how Ticket's own methods behave | Yes: overriding `price()` also changed `total()` and `studentPrice()` | No: `base.total()` always uses Ticket's own `price()` |
| Be called by Ticket's code, even from its constructor | Yes: the Subtask 3 bug | No: Ticket doesn't know VipTicket exists |
| Get every new Ticket method automatically | Yes, wanted or not | No: only the methods you write |
| Has to keep every promise a Ticket makes | Yes: it can be used anywhere a Ticket is expected (Subtask 4) | No: it isn't a Ticket, so it only keeps its own promises |

**Why that reach is a liability, not a convenience.** Every row is a connection to Ticket's
*insides*: which methods Ticket calls internally, what its constructor does, which methods it
adds next year. With inheritance, VipTicket depends on all of them, so Ticket can't safely change
any of them, and nobody can tell which changes are safe just by reading Ticket. That's the
fragile base class problem from Subtask 3. The reach also goes both ways: the child can change
the parent's behaviour, and the parent can call into the child.

The "convenience" and the danger are the same thing. Getting `studentPrice()` for free is
convenient right up until it charges VIPs half price.

With composition, VipTicket uses Ticket only through its methods, the same way any other class
would. Ticket can rewrite its insides freely, and as long as `price()` and `printTerms()` still
work, VipTicket keeps working.

### What composition costs (be honest about this)

- **You write small forwarding methods**, like `printTerms() { base.printTerms(); }`. If VipTicket
  needed 20 of Ticket's methods, you'd write 20 short forwarders.
- **You can't change Ticket's internal behaviour.** `base.total()` gives the plain ticket's total
  (1050). If VipTicket needs a total, it writes its own `total()`. That's more typing, but you can
  read VipTicket and see exactly how its numbers are worked out.
- **A VipTicket can't go into a `Ticket` variable any more.**
  `Ticket t = new VipTicket(new Ticket(), new NoDiscount());` fails with
  `incompatible types: VipTicket cannot be converted to Ticket`.

These costs show up as code you can see or as compile errors, not as silent bugs. That's why
they're usually worth paying.

**If callers need to treat both the same way**, give them a small shared interface, just like
`Shape` in Subtask 4:

```java
interface Priced {
    int price();
}

class Ticket implements Priced {
    @Override
    public int price() {                // public now: it implements an interface method
        return 1000;
    }
}

class VipTicket implements Priced {
    // fields and constructor exactly as before

    @Override
    public int price() {
        int vipPrice = base.price() + 1500;
        return discount.applyTo(vipPrice);
    }
}

// inside TicketDemo: a method that accepts either kind
static void printPrice(Priced item) {
    System.out.println(item.getClass().getSimpleName() + ": " + item.price());
}

// in main:
printPrice(new Ticket());                                    // Ticket: 1000
printPrice(new VipTicket(new Ticket(), new NoDiscount()));   // VipTicket: 2500
```

This is "interface plus composition", which is how the plan says the rest of the Zenith project
is built. Subtask 8 comes back to it.

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.7 — The ticket with composition instead of inheritance
- VipTicket no longer extends Ticket. It holds a Ticket (base) and a Discount.
  price() asks base for the price, adds 1500, then lets the discount adjust it.
- What the inheritance version could reach that this one can't:
  - change Ticket's own behaviour (overriding price() also changed total())
  - be called by Ticket's constructor before it was ready (the Subtask 3 bug)
  - get every new Ticket method automatically (studentPrice() gave VIPs half price)
  - it had to keep every promise a Ticket makes, because it could be used as a Ticket (LSP)
- Why that reach is a liability: each one is a hidden link to Ticket's insides, so a change
  to Ticket can break VipTicket without anyone touching VipTicket.
- Composition only uses Ticket's methods, like any other class. Ticket can change freely.
- Cost: small forwarding methods, my own total(), and a shared interface if callers need
  to treat both the same way. Worth it: the costs are visible, not silent.
- Bonus: a different Discount object gives new behaviour with no new class.
```

### Check yourself

1. What's the difference between "is-a" and "has-a"? Which one is `VipTicket` now?
2. What does "delegation" mean? Point to two places in `VipTicket` where it happens.
3. How many objects exist after `new VipTicket(new Ticket(), new NoDiscount())`? How many
   existed after `new VipTicket()` in the inheritance version?
4. Why can't the Subtask 3 constructor bug happen with composition?
5. Name two things the inheritance version could reach that this version can't, and explain why
   each one is a liability.
6. What does composition cost you, and why is that cost usually worth paying?

---

## Subtask 6 — Write the rule in your own words

> **From the plan:** Write the rule in your own words: **inheritance when the child is genuinely
> substitutable for the parent; composition for reuse.** Then write the practical test you will
> actually apply: *if someone adds a method to the parent tomorrow, could it silently break my
> subclass?* If yes, compose.

### What it's asking

There's no code in this subtask. You write two short things in your `NOTES.md`:
1. **The rule:** when to use inheritance and when to use composition, in your own words.
2. **The practical test:** the one question you'll actually ask yourself when you're about to
   write `extends`.

The point is to turn Subtasks 1–5 into a decision you can make quickly, and explain, in a real
codebase or an interview.

### Part 1 — The rule, piece by piece

**"Inheritance when the child is genuinely substitutable for the parent."**
- *Substitutable* is the LSP idea from Subtask 4: anywhere the parent is used, you can hand over
  the child instead, and the code still works without noticing anything different.
- *Genuinely* means in every situation, including the parent's unwritten promises. It isn't
  enough that the English sentence "X is a Y" sounds true. "A square is a rectangle" sounds true,
  and Subtask 4 showed it isn't.

**"Composition for reuse."**
- The most common reason people write `extends` is "that class already has code I want, and I
  don't want to retype it." That's *reuse*.
- Reuse is a fine goal, but inheritance is the wrong tool for it. Get reuse the Subtask 5 way:
  hold an object of that class in a field and delegate to it.

So there are two different questions, and only one of them is a reason for inheritance:
- "Do I want that class's code?" That's reuse, so use composition.
- "Can my class stand in for that class everywhere?" Only a genuine yes here can justify
  inheritance.

### Part 2 — The practical test

> **If someone adds a method to the parent tomorrow, could it silently break my subclass?**
> If yes, compose.

- **"Silently"** is the key word. A change that causes a compile error is annoying but safe: you
  find it straight away. The dangerous changes compile perfectly and quietly give wrong results.
- **You've already seen the answer be "yes" twice:**
  - Subtask 5: someone adds `studentPrice()` to `Ticket`, and every `VipTicket` quietly gets a
    half-price student price of 1250.
  - Subtask 3: someone adds one log line to `Ticket`'s constructor, and every `VipTicket` works
    out its total as 1050 instead of 2550.
- **Why this test works:** it's concrete. You can actually picture the change and trace what
  happens. It goes straight at the fragile base class danger. And it asks about the future, not
  just whether the code works today.
- **Ask it a little more broadly too:** not only "adds a method", but also "changes which of its
  own methods call each other", like `total()` starting to call `price()`.

With composition, the answer is almost always no. New methods on `Ticket` don't appear in
`VipTicket`, and `Ticket`'s internal calls can't reach `VipTicket`'s code. The only thing that can
affect a composed class is a change to what the methods it calls actually do, such as `price()`
starting to return 1200. That's a visible change every user of `Ticket` sees, not a hidden link.

### Using the rule and the test together

Before you write `extends`, ask these three questions in order:
1. **Why do I want `extends`?** If the honest answer is "to reuse its code", use composition.
2. **Can my class stand in for the parent everywhere, keeping every promise?** If not, use
   composition, plus a small shared interface if callers need to treat both the same way (like
   `Shape` and `Priced`).
3. **The tomorrow test:** could a new parent method, or a change in which parent methods call
   which, silently break my class? If yes, use composition.

Only if you get through all three is inheritance the right choice. In practice that usually means
the parent was **built to be extended**, with a narrow, clear job for the child. Subtask 8's
Template Method is that kind of parent. You'd also mark anything children mustn't change as
`final` (Subtask 7).

### Applying it to what you've built

- **`VipTicket extends Ticket`:** question 1 says the real reason was reuse (`price()` and
  `printTerms()`). Question 3 says yes, `studentPrice()` broke it. **Composition.**
- **`Square extends Rectangle`:** question 2 says no, a `Square` can't keep `Rectangle`'s promise
  about independent sides. **Composition**, or a shared `Shape` interface.
- **A case that passes:** your own exception types, like
  `class SeatUnavailableException extends RuntimeException` (Task F2.10). Every place that
  handles a `RuntimeException` also handles yours correctly, and `RuntimeException` was built to
  be extended. **Inheritance is fine here.**

### How this sounds in an interview

The plan says the real interview question is "when would you use inheritance rather than
composition?". A strong answer gives the rule, the test, and a bug you've caused yourself. In your
own words, something like:

> "I default to composition and use inheritance only when the child can genuinely stand in for
> the parent everywhere. Wanting to reuse code isn't enough. For reuse I hold an object and
> delegate to it. The test I use is: if someone adds a method to the parent tomorrow, could it
> silently break my subclass? I've seen that happen: a parent constructor called an overridable
> method, so my subclass worked out a total before its own field was set, and a new method on the
> parent quietly gave VIP tickets a half-price student rate. Where I would use inheritance is a
> parent designed to be extended, like an abstract base class where subclasses fill in one step."

### Write it in your `NOTES.md`

This subtask *is* the write-up. Write yours first, then compare:

```markdown
## F2.7 — Inheritance or composition: my rule
- Rule: I use inheritance only when the child can genuinely stand in for the parent
  everywhere, keeping every promise the parent makes. If I just want to reuse a class's
  code, I hold an object of it and delegate (composition).
- My test: "If someone adds a method to the parent tomorrow, could it silently break my
  subclass?" If yes, I compose.
- Seen it fail: studentPrice() added to Ticket gave VIP tickets half price (1250), and a
  log line in Ticket's constructor made VipTicket work out 1050 instead of 2550.
- Square extends Rectangle fails the "stand in everywhere" part of the rule.
- Inheritance passes when the parent was built to be extended, e.g. custom exceptions,
  or Template Method (Phase 1's AbstractPricingRule).
```

### Check yourself

1. Say the rule in one sentence, without looking.
2. Why is "I want to reuse its code" not a good reason to write `extends`? What should you do
   instead?
3. What does "silently" mean in the practical test, and why does that word matter so much?
4. Run the three questions on `VipTicket extends Ticket`. What does each one tell you?
5. Give one example where inheritance passes both the rule and the test, and say why.

---

## Subtask 7 — `final` classes and methods, and why `String` is final

> **From the plan:** Mark a class `final` and try to extend it; mark a method `final` and try to
> override it. Then answer: why is `String` final, and what would break if it were not?

### What it's asking

1. Put `final` on a class, then try to write a class that extends it. Watch the compiler refuse.
2. Put `final` on a method, then try to override it in a child class. Watch the compiler refuse.
3. Answer in your `NOTES.md`: why did Java's designers make `String` final, and what would go
   wrong if they hadn't?

Try Experiments A to C in your `TicketDemo.java` from Subtasks 1–3, and undo each change
afterwards.

### The ideas

**`final` means "this can't be changed past this point."** It can go on three things:

| Where it goes | What it means | Seen already? |
| --- | --- | --- |
| A variable or field | It can't be given a new value after it's set | Yes: `private final Ticket base;` (Subtask 5) |
| A method | No child class can override it | New here |
| A class | No class can extend it | New here |

**Why would a class's author want this?** Subtasks 3–6 showed what goes wrong when a child
replaces methods or depends on a parent's insides. `final` lets the author rule that out at
compile time. Either "nobody may extend this class at all", or "you may extend it, but these
particular methods are fixed".

### Experiment A — a `final` class

```java
final class Ticket {
    int price() {
        return 1000;
    }
}

class VipTicket extends Ticket {        // try to extend it
}
```

The compiler refuses:

```
error: cannot inherit from final Ticket
class VipTicket extends Ticket {
                        ^
```

`Ticket` still works normally: you can create `Ticket` objects and call `price()`. You just can't
build a child class on top of it. You can still **compose** it: a class that holds a `Ticket` in a
field, like Subtask 5's `VipTicket`, is completely unaffected. `final` blocks inheritance, not
reuse.

### Experiment B — a `final` method

```java
class Ticket {
    final int price() {                 // no child may replace this
        return 1000;
    }
}

class VipTicket extends Ticket {
    @Override
    int price() {                       // try to override it
        return 2500;
    }
}
```

The compiler refuses:

```
error: price() in VipTicket cannot override price() in Ticket
    int price() {
        ^
  overridden method is final
```

A `final` method doesn't stop the child from existing, inheriting the method, or calling it. It
only stops the child from *replacing* it. This version compiles and runs:

```java
class Ticket {
    final int price() {
        return 1000;
    }

    void printTerms() {                 // not final, so children may override it
        System.out.println("Non-refundable. Carry a photo ID.");
    }
}

class VipTicket extends Ticket {
    @Override
    void printTerms() {                 // allowed: printTerms() isn't final
        System.out.println("VIP: lounge access included. Price: " + price());   // calling price() is fine
    }
}

public class TicketDemo {
    public static void main(String[] args) {
        Ticket vip = new VipTicket();
        System.out.println("Price: " + vip.price());
        vip.printTerms();
    }
}
```

Output:

```
Price: 1000
VIP: lounge access included. Price: 1000
```

### Experiment C — does `final` fix the Subtask 3 bug?

Go back to the Subtask 3 code and make `total()` final:

```java
final int total() {
    int bookingFee = 50;
    return price() + bookingFee;
}
```

Output:

```
1. Ticket constructor runs and calls total()
   VipTicket.price() sees vipSurcharge = 0
   total = 1050
2. VipTicket constructor sets vipSurcharge = 1500
```

**The bug is still there.** `final` stops any child from replacing `total()`, but `total()` still
calls `price()`. `price()` isn't final, so `VipTicket`'s version still runs during the parent's
constructor. **`final` protects only the method it's on, not the methods that method calls.**

Making `price()` final as well would remove the bug, but then `VipTicket` couldn't change the
price at all. That's the real choice `final` gives a parent's author: decide which methods are
fixed and which are deliberate "fill this in" hooks for children. Subtask 8's Template Method is
built on exactly that split.

### Why `String` is final

**What `String` promises.** Every `String` is *immutable*: once it's created, its text never
changes. Methods like `toUpperCase()` don't change the string; they return a new one. Enormous
amounts of code, in the JDK and in every Java program, rely on that promise without thinking
about it.

**What would happen if `String` weren't final.** Anyone could write
`class SneakyString extends String` and override its methods. By Subtask 1's rule, a child
object can go anywhere the parent type is expected. So a `SneakyString` could be passed to every
method that accepts a `String`, and none of them could tell. The promise could then be broken in
several ways:
- **Security checks could be fooled.** Code like
  `if (path.startsWith("/home/pavan/")) { open(path); }` checks a file path before using it. A
  subclass could override `startsWith` to always say yes, or show one piece of text during the
  check and a different one afterwards. The check would pass, and the program would open a file
  it should never touch.
- **`HashMap` and `HashSet` could lose entries.** They find a key using its `hashCode()` and
  `equals()` (Task F2.8), and they assume those never change. A string subclass whose value
  changed after it was stored would become impossible to find.
- **Sharing between threads would stop being safe.** Because Strings can't change, any number
  of threads can read the same `String` at once with no risk (Phase 12). A subclass could add a
  changeable field and quietly break that.

In each case, the code that received the "String" did nothing wrong. It was handed an impostor.
That's the LSP problem from Subtask 4, at the scale of the whole Java platform.

**Why `final` is the fix.** `String`'s designers couldn't trust every possible subclass, written
by anyone, to keep all of `String`'s promises. So they made subclasses impossible. Because
`String` is final, every object whose type is `String` is guaranteed to be the real JDK `String`,
with exactly its behaviour. Try it: create `MyString.java` containing only
`class MyString extends String { }` and run `javac MyString.java`:

```
error: cannot inherit from final String
class MyString extends String {
                       ^
```

`Integer` (and the other number wrapper classes) and `LocalDate` are final for the same reason.

**A common misunderstanding: `final` class doesn't mean immutable.** `final` on a class only
stops subclasses. A final class can still have setters that change its objects. `String` is
immutable because of two things together:
- its text is stored in a private field, and it has no methods that change it
- it's final, so nobody can add such methods in a subclass

`final` closes the last loophole. (Task F3.8 covers immutability properly.)

### When to use `final` yourself

- **Mark a class `final` if it wasn't designed to be extended.** That stops anyone from building
  the Subtask 3 and Subtask 5 problems on top of it, and they can still reuse it through
  composition. Records, which you'll meet in Task F3.6, are final automatically.
- **In a class that *is* designed to be extended, mark the methods children must not change as
  `final`.** That's exactly what Template Method does in Subtask 8.

### Write it up in your `NOTES.md`

Write yours first, then compare:

```markdown
## F2.7 — final, and why String is final
- final class: nobody can extend it ("cannot inherit from final Ticket").
- final method: no child can override it ("overridden method is final"), but children
  still inherit it and can call it.
- final only protects the method it's on: a final total() that calls an overridable
  price() still has the Subtask 3 bug.
- Why String is final: every String must keep String's promises (above all, its text
  never changes). If subclasses were allowed, one could be passed anywhere a String is
  expected and break them: fool a security check on a file path, get lost inside a
  HashMap, or become unsafe to share between threads.
- final makes sure every String is the real String. Integer and LocalDate are final for
  the same reason.
- A final class isn't automatically immutable: String is immutable because it has no
  methods that change it AND nobody can add any.
```

### Check yourself

1. `final` can go on a variable, a method, or a class. What does it mean in each case?
2. What error do you get when you extend a final class, and when you override a final method?
3. A child class extends a parent that has a `final` method. Can the child call that method?
   Can it override it?
4. Why didn't making `total()` final fix the Subtask 3 bug?
5. Why is `String` final? Describe one concrete thing that would break if it weren't.
6. Does `final` on a class mean its objects can never change? What else makes `String`
   immutable?

---

## Rules so far (quick reference)

1. In `Ticket vip = new VipTicket();`, the variable's type decides what you may call; the
   object's type decides which version runs.
2. Java looks for a method starting at the object's actual class, then moves up through the
   parents until it finds it.
3. Always write `@Override` on a method you mean to replace. With a parent class, a typo
   without it compiles and silently runs the parent's version.
4. `price()` goes through dynamic dispatch; `super.price()` goes straight to the parent's
   version.
5. Every constructor first calls its parent's constructor (Java adds `super();` if you don't).
   The parent part is fully built before the child's constructor body runs.
6. `new VipTicket()` creates one object, not two.
7. If the parent's only constructor needs arguments, the child must call `super(...)`, and it
   must be the first line.
8. Fields hold default values (`0`, `false`, `null`) until the line that sets them runs.
9. A constructor must never call a method a child can override, directly or through another
   method.
10. A child must keep every promise the parent makes, including the unwritten ones. Code that
    works with the parent must work, unchanged, with the child (LSP).
11. "X is a Y" sounding true in English isn't enough for `extends`. Ask: can X do everything Y
    promises, in every situation, without the caller noticing the difference?
12. If a caller needs `instanceof` to check which subclass it has, the hierarchy is wrong.
13. `assert` checks only run when you start Java with `-ea`.
14. Composition means holding another object in a field and passing calls on to it
    (delegation): "has-a" instead of "is-a".
15. A composed class uses the objects it holds only through their methods, the same ones any
    other class can use. Those objects can change their insides without breaking it, and they
    can never call back into it unexpectedly.
16. With composition you change behaviour by passing in a different object (like a different
    `Discount`), not by writing a new subclass.
17. Use inheritance only when the child is genuinely substitutable for the parent. If you only
    want to reuse code, use composition.
18. The practical test: if someone adds a method to the parent tomorrow (or changes which of its
    methods call each other), could it silently break my subclass? If yes, compose.
19. `final` on a class: nobody can extend it. `final` on a method: no child can override it,
    though children still inherit it and can call it.
20. `final` protects only the method it's on, not the methods that method calls.
21. Mark classes that weren't designed to be extended as `final`. Others can still reuse them
    through composition.
22. `String` is final so that every `String` is guaranteed to be the real one and keep its
    promises, above all that its text never changes.

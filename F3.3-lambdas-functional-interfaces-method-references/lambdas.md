# Task F3.3 — Lambdas, functional interfaces, and method references

## Concept 1: Passing behaviour as a value

**The problem.** Java makes it easy to pass *data* (numbers, strings, objects) to a method.
But sometimes you want to pass *behaviour*: "here is a piece of logic, run it when you need it."

Example: you write one method, `printMatchingRows`, whose job is simple — loop through the seat
rows and print the ones that pass a check. But *which* check? One caller wants front rows
(`rowNumber <= 3`), another wants even-numbered rows, another wants rows above 10. If you write
one fixed check inside the method (that is what "hard-code" means), the method works for only one
of those callers. The better design: the method only does the looping and printing, and each
caller *passes in* their own check — the same way they would pass in data.

Java's answer (long before lambdas): define an **interface with one method**, and let the caller
hand over an object that implements it.

```java
interface SeatFilter {
    boolean test(int rowNumber);  // exactly ONE abstract method
}

static void printMatchingRows(int[] rows, SeatFilter filter) {
    for (int row : rows) {
        if (filter.test(row)) {
            System.out.println("Row " + row + " matches");
        }
    }
}
```

### The three ways to hand over behaviour

**Way 1 — a named class.** Full ceremony: class name, `implements`, method signature.
Five lines of boilerplate around one line of real logic (`rowNumber <= 3`).

```java
class FrontRowFilter implements SeatFilter {
    public boolean test(int rowNumber) {
        return rowNumber <= 3;
    }
}
// usage:
printMatchingRows(rows, new FrontRowFilter());
```

**Way 2 — an anonymous class.** Same thing without inventing a name; the class and its single
instance are created in one expression at the call site. Still noisy — the interface name and
method signature are repeated even though the compiler already knows them.

```java
printMatchingRows(rows, new SeatFilter() {
    public boolean test(int rowNumber) {
        return rowNumber <= 3;
    }
});
```

> **Your doubt:** *"Suppose I have another method in which I want to pass the FrontRowFilter
> logic into it. If I implement an anonymous class, I should write the logic at 2 places, right?"*
>
> Yes — **if you write the anonymous class inline at each call site.** An anonymous class has no
> name, so you cannot say "use that same one again" from somewhere else in the code. Suppose there
> is a second method that also needs the front-row check:
>
> ```java
> static int countMatchingRows(int[] rows, SeatFilter filter) {
>     int count = 0;
>     for (int row : rows) {
>         if (filter.test(row)) count++;
>     }
>     return count;
> }
> ```
>
> Writing the anonymous class inline at both call sites duplicates the logic:
>
> ```java
> printMatchingRows(rows, new SeatFilter() {
>     public boolean test(int rowNumber) {
>         return rowNumber <= 3;          // copy 1 of the logic
>     }
> });
> int c = countMatchingRows(rows, new SeatFilter() {
>     public boolean test(int rowNumber) {
>         return rowNumber <= 3;          // copy 2 of the SAME logic
>     }
> });
> ```
>
> Now if the rule changes ("front row" becomes `<= 5`), you must remember to fix *both* copies —
> miss one and the two methods silently disagree. That is the real danger of duplication.
>
> **The fix: write the anonymous class once, store it in a variable, and pass the variable.**
> An anonymous class is an *expression* that produces an object, and an object can be saved:
>
> ```java
> SeatFilter frontRow = new SeatFilter() {
>     public boolean test(int rowNumber) {
>         return rowNumber <= 3;          // the ONLY copy
>     }
> };
> printMatchingRows(rows, frontRow);      // reuse
> int c = countMatchingRows(rows, frontRow); // reuse again
> ```
>
> Verified output — both versions behave identically:
>
> ```text
> -- inline anonymous classes (logic duplicated) --
> Row 1 matches
> Row 2 matches
> Row 3 matches
> count = 3
> -- anonymous class stored in a variable (logic once) --
> Row 1 matches
> Row 2 matches
> Row 3 matches
> count = 3
> ```
>
> So the rule of thumb:
>
> | How you write it | Logic written | Good for |
> |---|---|---|
> | Anonymous class inline at every call site | once **per call site** (duplication) | truly one-off logic |
> | Anonymous class stored in a variable | once | reuse within one class/method |
> | Named class (`FrontRowFilter`) | once | reuse across many classes/files |
>
> (The same trick works for lambdas: `SeatFilter frontRow = rowNumber -> rowNumber <= 3;` — one
> definition, passed to as many methods as you like.)

**Way 3 — a lambda.** The compiler knows the method wants a `SeatFilter`, and that `SeatFilter`
has exactly one method taking an `int` and returning a `boolean`. Everything except the logic is
redundant, so the lambda strips it away:

```java
printMatchingRows(rows, rowNumber -> rowNumber <= 3);
```

Read `->` as "goes to": *"rowNumber goes to: rowNumber <= 3"*.
That one expression IS an implementation of `SeatFilter`.

### Lambdas from zero (full runnable file: `LambdaLevel0.java`)

> **Your doubt:** *"In Way 3 you have used lambda. But I have not read lambdas anywhere till now.
> I am completely new to it. Can you explain everything from level 0? Also how are lambdas
> assigned to variables?"*

**Step 1 — start from what you already know.** Here is the anonymous class from Way 2, stored in
a variable:

```java
SeatFilter viaAnonymous = new SeatFilter() {
    public boolean test(int rowNumber) {
        return rowNumber <= 3;
    }
};
```

Look closely at how much of that text the compiler *already knows* on its own:

- The variable is declared as `SeatFilter`, so the object must be a `new SeatFilter() {...}` —
  writing it again is repetition.
- `SeatFilter` has exactly **one** method, `boolean test(int rowNumber)`, so the method name
  `test`, the return type `boolean`, and the parameter type `int` are all repetition too.

The only part the compiler *cannot* guess is your actual logic: take `rowNumber`, give back
`rowNumber <= 3`. Everything else is ceremony.

**Step 2 — delete the ceremony, piece by piece.**

```java
// Full anonymous class:
SeatFilter f = new SeatFilter() {                 // compiler knows this from the variable type
    public boolean test(int rowNumber) {          // compiler knows name + types from the interface
        return rowNumber <= 3;                    // <-- the ONLY new information
    }
};

// Delete everything the compiler already knows. What survives:
//        parameter        logic
SeatFilter f = rowNumber -> rowNumber <= 3;
```

That surviving line **is** a lambda. A lambda is not a new kind of thing in Java — it is the
anonymous class with every guessable part deleted. The arrow `->` was added as a separator so the
compiler can tell where the parameters end and the logic begins.

**Step 3 — the anatomy.** Every lambda has exactly three parts:

```
 parameters   ->   body
 (the inputs)      (what to do with them)
```

Read `->` as "goes to". Examples of the shapes you will meet:

```java
rowNumber -> rowNumber <= 3        // one parameter, one expression
(a, b)    -> a + b                 // two parameters need parentheses
()        -> System.out.println("hi")  // zero parameters: empty parentheses required
n         -> { int d = n * 2;      // multi-line body needs braces...
               return d; }         // ...and then an explicit return
```

Two body rules worth memorising: if the body is a **single expression** (like `rowNumber <= 3`),
its value is returned automatically — writing `return` there is actually a compile error. If the
body has **braces**, it is ordinary statements and you must write `return` yourself (when the
method returns something).

**Step 4 — how a lambda is assigned to a variable.** The same way any object is: the variable's
declared type is the interface, and the lambda is the value.

```java
SeatFilter viaLambda = rowNumber -> rowNumber <= 3;
```

What does `viaLambda` hold now? An object that implements `SeatFilter` — so you *use* it by
calling the interface's one method, `test`:

```java
System.out.println(viaLambda.test(2));   // runs the body with rowNumber = 2
System.out.println(viaLambda.test(5));   // runs the body with rowNumber = 5
```

Note: the body does **not** run at the assignment line. The assignment only stores the behaviour.
The body runs each time `.test(...)` is called — that is the whole "pass behaviour, run it later"
idea in action.

Real output from `LambdaLevel0.java`, where the anonymous-class version and the lambda version sit
side by side:

```text
viaAnonymous.test(2) = true
viaAnonymous.test(5) = false
viaLambda.test(2)    = true
viaLambda.test(5)    = false
-- passing the lambda variable --
Row 1 matches
Row 2 matches
Row 3 matches
-- passing a lambda directly --
Row 1 matches
Row 2 matches
Row 3 matches
```

Identical behaviour, as expected: both variables hold an implementation of `SeatFilter`.

**Step 5 — why the variable's type is doing real work.** A lambda does *not* carry its own type.
`rowNumber -> rowNumber <= 3` alone tells the compiler nothing about what `rowNumber` is. The
compiler reads the **target type** — the interface on the left of the `=` (or the parameter type
of the method you are passing it to) — finds that interface's single method, and takes all the
types from there. This is why these two lines fail (`LambdaLevel0Broken.java.txt`):

```java
var f = rowNumber -> rowNumber <= 3;      // no interface anywhere to read types from
Object g = rowNumber -> rowNumber <= 3;   // Object has no single method to implement
```

```text
LambdaLevel0Broken.java:5: error: cannot infer type for local variable f
        var f = rowNumber -> rowNumber <= 3;
            ^
  (lambda expression needs an explicit target-type)
LambdaLevel0Broken.java:6: error: incompatible types: Object is not a functional interface
        Object g = rowNumber -> rowNumber <= 3;
                   ^
```

| Error phrase | Plain English |
|---|---|
| `cannot infer type for local variable f` | `var` asks the compiler to deduce the type from the right-hand side, but a lambda has no type of its own — dead end in both directions. |
| `lambda expression needs an explicit target-type` | A lambda must land somewhere that names a one-method interface: a typed variable, a method parameter, or a return type. |
| `Object is not a functional interface` | "Functional interface" is the official name for a one-method interface. `Object` declares no abstract method, so there is nothing for the lambda to implement. |

**Step 6 — variable or no variable?** Both of these work; they differ only in reuse:

```java
// Without a variable: logic lives only at this call site.
printMatchingRows(rows, rowNumber -> rowNumber <= 3);

// With a variable: define once, pass to many methods (same reuse trick as the
// anonymous-class-in-a-variable from the doubt above).
SeatFilter frontRow = rowNumber -> rowNumber <= 3;
printMatchingRows(rows, frontRow);
```

**Summary of the level-0 picture:**

- A lambda is an anonymous class with every part the compiler can guess deleted; only
  `parameters -> body` survives.
- Single-expression body returns its value automatically; a braced body needs `return`.
- Assigning to a variable: `SeatFilter f = rowNumber -> rowNumber <= 3;` — the variable's type
  must be a one-method interface, and you call it via that method name: `f.test(4)`.
- Assignment stores the behaviour; the body runs only when the method is called.
- A lambda has no type of its own — `var` and `Object` both fail because there is no one-method
  interface in sight.

### Proof: all three behave identically

Full runnable file: `Concept1.java`. Output:

```
--- Way 1: named class ---
Row 1 matches
Row 2 matches
Row 3 matches
--- Way 2: anonymous class ---
Row 1 matches
Row 2 matches
Row 3 matches
--- Way 3: lambda ---
Row 1 matches
Row 2 matches
Row 3 matches
```

### Summary

- A one-method interface is Java's way of describing "a piece of behaviour".
- A lambda is **not new magic** — it is an implementation of a one-method interface with all the
  ceremony deleted.
- `rowNumber -> rowNumber <= 3` and the named `FrontRowFilter` class are the same thing to the
  code that calls `filter.test(...)`.

## Concept 2: What exactly is a functional interface

**The rule:** a functional interface is an interface with **exactly one abstract method**.
That is the whole definition.

**Why it matters:** a lambda has no name and no method signature of its own — the compiler must
figure out *which* method the lambda is implementing. That is only unambiguous when there is
exactly one candidate.

Two things that do **not** count against the "one abstract method" rule, because they already
have a body:

- **`default` methods** — an interface method with an implementation.
- **`static` methods** — same, but belongs to the interface itself.

So an interface can have one abstract method plus ten default/static methods and still be
functional.

**`@FunctionalInterface`** is an annotation that does not change behaviour — it is a promise to
the compiler: *"this interface is meant to be used with lambdas; stop anyone who adds a second
abstract method."* Like `@Override`, it turns a silent future breakage into a loud compile error.

> **How `@Override` works:** when a child-class method is marked `@Override`, the compiler
> verifies that the parent class (or an implemented interface) contains a method with the **same
> name and the same parameter types in the same order** — not just the same name. The return type
> must also be compatible (same type or a subtype). If no such exact match exists — because of a
> typo, wrong parameters, or a later rename in the parent — compilation fails at that line
> instead of silently creating a new, unrelated method.

### Working example (full runnable file: `Concept2.java`)

```java
@FunctionalInterface
interface DiscountRule {
    int discountedPrice(int originalPrice); // the ONE abstract method

    // default methods are allowed - they have a body, so they don't count
    default String describe() {
        return "A rule that discounts a price";
    }

    // static methods are allowed too - they also have a body
    static DiscountRule noDiscount() {
        return price -> price;
    }
}

public class Concept2 {
    public static void main(String[] args) {
        DiscountRule halfPrice = price -> price / 2;
        DiscountRule tenOff    = price -> price - 10;

        System.out.println("half of 500  = " + halfPrice.discountedPrice(500));
        System.out.println("ten off 500  = " + tenOff.discountedPrice(500));
        System.out.println(halfPrice.describe());
        System.out.println("no discount  = " + DiscountRule.noDiscount().discountedPrice(500));
    }
}
```

Output:

```
half of 500  = 250
ten off 500  = 490
A rule that discounts a price
no discount  = 500
```

### Breaking it on purpose: a second abstract method

```java
@FunctionalInterface
interface BrokenRule {
    int discountedPrice(int originalPrice);
    boolean appliesTo(int seatRow);          // second abstract method -> compile error
}

public class Concept2Broken {
    public static void main(String[] args) {
        BrokenRule r = price -> price / 2;   // which method would this lambda implement?
    }
}
```

Compiler output (file kept as `Concept2Broken.java.txt`; rename to `.java` to reproduce):

```
Concept2Broken.java:5: error: Unexpected @FunctionalInterface annotation
@FunctionalInterface
^
  BrokenRule is not a functional interface
    multiple non-overriding abstract methods found in interface BrokenRule
Concept2Broken.java:13: error: incompatible types: BrokenRule is not a functional interface
        BrokenRule r = price -> price / 2;
                       ^
    multiple non-overriding abstract methods found in interface BrokenRule
2 errors
```

Decoding the errors:

| Error text | What it means |
| --- | --- |
| `Unexpected @FunctionalInterface annotation` | You promised this interface is functional, but it is not — so the annotation itself is now an error. |
| `multiple non-overriding abstract methods found` | The compiler found two abstract methods, so "exactly one" is violated. |
| `incompatible types: BrokenRule is not a functional interface` | The lambda cannot be used: the compiler cannot tell if it is meant to be `discountedPrice` or `appliesTo`. |

The second error is the deeper one: even *without* the annotation, the lambda assignment would
fail — the annotation just catches the problem at the interface definition instead of at every
usage site.

### Summary

- Functional interface = exactly one abstract method = exactly one thing a lambda could mean.
- `default` and `static` methods have bodies, so they do not count.
- `@FunctionalInterface` is a guard rail, not a requirement — lambdas work on any qualifying
  interface, annotated or not.

### Your doubts

> **"`static DiscountRule noDiscount() { return price -> price; }` — what is this method doing?
> I'm unable to understand this clearly."**

**Step 1: a lambda is a value, so it can be *returned*, not just assigned.**
You have seen a lambda stored in a variable:

```java
DiscountRule halfPrice = price -> price / 2;
```

If a lambda can sit on the right side of `=`, it can also sit after `return`. Both just move a
value around. So `noDiscount()` is a method whose return value is *an object that implements
`DiscountRule`*. Nothing more.

**Step 2: what does `price -> price` itself mean?**
It implements the one abstract method `int discountedPrice(int originalPrice)`. Written as the
equivalent anonymous class:

```java
new DiscountRule() {
    public int discountedPrice(int price) {
        return price;   // give back the same price, unchanged
    }
}
```

"Take the price, return the price" — in other words, **no discount at all**. Hence the name.

**Step 3: why would anyone write such a method?**
It is a *factory*: a named, ready-made rule that callers can grab instead of writing the lambda
themselves:

```java
DiscountRule r = price -> price;              // caller writes the logic, meaning unclear
DiscountRule r = DiscountRule.noDiscount();   // caller grabs a named rule, meaning obvious
```

You already use factories like this constantly: `List.of(...)`, `LocalDate.now()`,
`Comparator.comparing(...)` — all static methods that build and return an object for you.

Proof that the lambda and anonymous-class versions are identical (full file: `DoubtDemo.java`):

```java
@FunctionalInterface
interface Rule {
    int discountedPrice(int originalPrice);

    static Rule noDiscount() {
        return price -> price; // returns an object implementing Rule
    }

    static Rule noDiscountVerbose() {
        return new Rule() {
            public int discountedPrice(int price) {
                return price;
            }
        };
    }
}

public class DoubtDemo {
    public static void main(String[] args) {
        Rule a = Rule.noDiscount();          // step 1: call the factory, get a Rule object
        Rule b = Rule.noDiscountVerbose();

        System.out.println(a.discountedPrice(500));  // step 2: use the object
        System.out.println(b.discountedPrice(500));
    }
}
```

Output:

```
500
500
```

**Mental model in one line:** `noDiscount()` manufactures and hands back a `DiscountRule`
object; the lambda `price -> price` is just the shortest way to write that object's logic.
Two separate moments in time: calling `Rule.noDiscount()` only *creates* the rule; the logic
inside the lambda runs later, when someone calls `.discountedPrice(500)` on it.

## Concept 3: The four built-in functional interfaces

**The problem this solves.** Without them, every programmer would write their own one-method
interfaces: `SeatFilter`, `WordFilter`, `UserFilter`... all shaped "take a thing, return
true/false". Java ships the common shapes ready-made in `java.util.function` — you pick the
shape that matches instead of writing your own.

**The four you meet daily** (plus one bonus). The only differences are *what goes in* and
*what comes out*:

| Interface | Takes | Returns | Its one method | Used for |
| --- | --- | --- | --- | --- |
| `Predicate<T>` | a T | `boolean` | `test(t)` | yes/no checks, filtering |
| `Function<A, B>` | an A | a B | `apply(a)` | converting one thing into another |
| `Supplier<T>` | nothing | a T | `get()` | producing a value on demand |
| `Consumer<T>` | a T | nothing | `accept(t)` | side effects — printing, logging, saving |
| `BiFunction<A, B, C>` | an A and a B | a C | `apply(a, b)` | converting, but with two inputs |

The `<T>` is ordinary generics: `Predicate<String>` = "a yes/no test on Strings",
`Function<String, Integer>` = "converts a String into an Integer".

### One example of each (full runnable file: `BuiltInInterfaces.java`)

```java
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiFunction;

public class BuiltInInterfaces {
    public static void main(String[] args) {

        // Predicate<T>: takes a T, returns boolean. For yes/no tests.
        Predicate<String> isLong = word -> word.length() > 5;
        System.out.println("isLong(\"hi\")        = " + isLong.test("hi"));
        System.out.println("isLong(\"elephant\")  = " + isLong.test("elephant"));

        // Function<A, B>: takes an A, returns a B. For converting.
        Function<String, Integer> length = word -> word.length();
        System.out.println("length(\"elephant\")  = " + length.apply("elephant"));

        // Supplier<T>: takes NOTHING, returns a T. For producing on demand.
        Supplier<Double> randomPercent = () -> Math.random() * 100;
        System.out.println("randomPercent.get() = " + randomPercent.get());
        System.out.println("randomPercent.get() = " + randomPercent.get()); // fresh value each call

        // Consumer<T>: takes a T, returns NOTHING. For side effects.
        Consumer<String> shout = message -> System.out.println(message.toUpperCase() + "!!!");
        shout.accept("the show is starting");

        // BiFunction<A, B, C>: takes an A and a B, returns a C.
        BiFunction<String, Integer, String> repeat = (word, times) -> word.repeat(times);
        System.out.println("repeat(\"ha\", 3)     = " + repeat.apply("ha", 3));
    }
}
```

Output:

```
isLong("hi")        = false
isLong("elephant")  = true
length("elephant")  = 8
randomPercent.get() = 14.948199613046087
randomPercent.get() = 71.25246159230102
THE SHOW IS STARTING!!!
repeat("ha", 3)     = hahaha
```

### Things worth noticing

1. **Each interface has a differently-named method** — `test`, `apply`, `get`, `accept`. The
   names are fixed by the interface. When you *write* the lambda you never mention the name, but
   when you *call* it you must use the right one: `isLong.test(...)`, `length.apply(...)`,
   `randomPercent.get()`.
2. **`Supplier` runs fresh every call** — the output shows two different random numbers from the
   same `randomPercent`. The lambda body re-executes on each `.get()`. That is why Suppliers are
   used for "compute this only if/when needed".
3. **`SeatFilter` from Concept 1 was a `Predicate` all along** — `boolean test(int rowNumber)` has
   exactly the Predicate shape, so `Predicate<Integer>` would do. Before writing a one-method
   interface, check if a built-in already has that shape.

### How to pick the right one

Ask two questions — *does it take something in?* and *does it give something back?*

- Takes + returns `boolean` → `Predicate`
- Takes + returns something else → `Function`
- Takes nothing → `Supplier`
- Returns nothing → `Consumer`
- Two inputs → `BiFunction`

## Concept 4: Method references — the four shapes

**The idea.** Sometimes a lambda's body does nothing except call one existing method:

```java
Function<String, Integer> parse = text -> Integer.parseInt(text);
```

The lambda adds no logic of its own — it just forwards `text` into `parseInt`. For exactly this
case Java has a shorthand: **name the method instead of calling it**.

```java
Function<String, Integer> parse = Integer::parseInt;
```

Read `::` as "the method ... of". It is *only* a spelling change — same object, same behaviour.
If the lambda body does anything more than one method call (`x -> x.row() + 1`), a method
reference is impossible and you keep the lambda.

### The four shapes (full runnable file: `MethodReferences.java`)

```java
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.ArrayList;

public class MethodReferences {

    record Seat(int row, String section) { }

    public static void main(String[] args) {

        // Shape 1: STATIC method -> ClassName::staticMethod
        Function<String, Integer> parseLambda = text -> Integer.parseInt(text);
        Function<String, Integer> parseRef    = Integer::parseInt;

        // Shape 2: BOUND to a particular object -> object::method
        // System.out is one specific object; the reference always prints through IT.
        Consumer<String> printLambda = line -> System.out.println(line);
        Consumer<String> printRef    = System.out::println;

        // Shape 3: UNBOUND, on an arbitrary instance -> ClassName::instanceMethod
        // No object yet! The object arrives later as the lambda's parameter.
        Function<Seat, Integer> rowLambda = seat -> seat.row();
        Function<Seat, Integer> rowRef    = Seat::row;
        Seat seat = new Seat(7, "Balcony");

        // Shape 4: CONSTRUCTOR -> ClassName::new
        Supplier<ArrayList<String>> listLambda = () -> new ArrayList<>();
        Supplier<ArrayList<String>> listRef    = ArrayList::new;

        // The same example from verbose to terse:
        Function<Seat, Integer> v1 = (Seat s) -> { return s.row(); }; // everything spelled out
        Function<Seat, Integer> v2 = s -> s.row();                    // inferred type, expression body
        Function<Seat, Integer> v3 = Seat::row;                       // method reference
    }
}
```

Output:

```
parseLambda("42") = 42
parseRef("42")    = 42
printed via lambda
printed via method reference
rowLambda(seat) = 7
rowRef(seat)    = 7
list built via reference: [Asha]
v1=7  v2=7  v3=7
```

### The four shapes side by side

| Shape | Syntax | Equivalent lambda | The parameter becomes... |
| --- | --- | --- | --- |
| 1. Static | `Integer::parseInt` | `text -> Integer.parseInt(text)` | the method's argument |
| 2. Bound | `System.out::println` | `line -> System.out.println(line)` | the method's argument |
| 3. Unbound | `Seat::row` | `seat -> seat.row()` | **the object the method is called ON** |
| 4. Constructor | `ArrayList::new` | `() -> new ArrayList<>()` | the constructor's arguments |

### The tricky distinction: bound (shape 2) vs unbound (shape 3)

Both call an instance method, so what is different?

- **Bound:** `System.out::println` — written as *object*`::`*method*, on an object that
  **already exists**. The incoming parameter is used as the method's *argument*:
  "print `line` through this particular printer."
- **Unbound:** `Seat::row` — written as *ClassName*`::`*method*, but `row()` is **not static**.
  There is no seat yet when you write it. The incoming parameter becomes the *object itself*:
  "whatever seat arrives, call `.row()` on it."

Quick test: what is left of the `::`? A specific object → bound. A class name whose method needs
an object → unbound.

### The verbose-to-terse ladder

All three are the same `Function<Seat, Integer>`:

```java
(Seat s) -> { return s.row(); }   // every part written out
s -> s.row()                      // type inferred, expression body
Seat::row                         // body is just one method call, so name the method
```

Rule of thumb: use the method reference when it reads naturally ("the row of a Seat"); fall back
to the lambda the moment you need any extra logic.

## Concept 5: Variable capture and "effectively final"

**The setup.** A lambda can use variables from the method it is written in — that is called
*capturing* them:

```java
String greeting = "Hello, ";
names.forEach(name -> System.out.println(greeting + name));
```

`greeting` is not a lambda parameter — it is a local variable from outside that the lambda
reaches out and uses. That is allowed, **with one condition**: the variable must be
**effectively final** — assigned exactly once and never changed. You do not write the `final`
keyword; the compiler checks the behaviour.

### Breaking it on purpose (file: `EffectivelyFinalBroken.java.txt`, rename to `.java` to reproduce)

```java
import java.util.List;

public class EffectivelyFinalBroken {
    public static void main(String[] args) {
        List<String> names = List.of("Asha", "Ravi", "Meena");

        int counter = 0;                       // a local variable
        names.forEach(name -> counter++);      // lambda tries to change it -> compile error
        System.out.println(counter);
    }
}
```

Compiler output:

```
EffectivelyFinalBroken.java:11: error: local variables referenced from a lambda expression must be final or effectively final
        names.forEach(name -> counter++);      // lambda tries to change it -> compile error
                              ^
1 error
```

| Error text | What it means |
| --- | --- |
| `local variables referenced from a lambda expression` | The lambda captured `counter` from the surrounding method. |
| `must be final or effectively final` | A captured variable must never be reassigned — not before the lambda, not inside it, not after. `counter++` is a reassignment, so the deal is off. |

### Why Java insists on this

1. **The lambda may run later — maybe after the method is gone.** A lambda is a value; it can be
   stored, returned, or run on another thread. Local variables live on the stack and die when the
   method returns. So the lambda cannot share the *actual* variable — it secretly keeps **a copy**
   of its value, taken at creation time.
2. **A copy that could change is a lie.** If `counter` could change after the lambda copied it,
   there would be two "counters" silently disagreeing — the method's one and the lambda's stale
   copy. Java demands the variable never changes, so the copy and the original can never diverge.

**The workaround you should NOT use:** a one-element array (`int[] counter = {0};` then
`counter[0]++` inside the lambda). It compiles — the array *variable* is never reassigned, only
its contents mutate — but it defeats the safety the rule provides. If you are doing this, the
design is wrong; step back.

### The correct way to accumulate (full runnable file: `EffectivelyFinal.java`)

Do not capture-and-mutate — let the operation produce the result:

```java
import java.util.List;

public class EffectivelyFinal {
    public static void main(String[] args) {
        List<String> names = List.of("Asha", "Ravi", "Meena");

        // READING a local variable inside a lambda is fine,
        // as long as that variable is never reassigned:
        String greeting = "Hello, ";                          // assigned once -> effectively final
        names.forEach(name -> System.out.println(greeting + name));

        // The CORRECT way to count: use the right tool.
        long count = names.size();
        System.out.println("count via size()   = " + count);

        // Or, previewing streams (Task F3.5): count the ones matching a condition:
        long shortNames = names.stream().filter(n -> n.length() <= 4).count();
        System.out.println("names with <= 4 letters = " + shortNames);
    }
}
```

Output:

```
Hello, Asha
Hello, Ravi
Hello, Meena
count via size()   = 3
names with <= 4 letters = 2
```

### Summary

- A lambda may **read** local variables, but only if they are never reassigned (effectively final).
- Reason: the lambda keeps a *copy* (it may outlive the method), and a copy of a changing variable
  would silently go stale.
- To accumulate a value, do not capture-and-mutate — use an operation that returns the result
  (e.g. `filter(...).count()`).
- The rule applies only to **local variables**. Fields of objects are not on the stack, so a
  lambda may read and write them freely (thread-safety aside).

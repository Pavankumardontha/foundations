# Task F3.2 — Bounded types, wildcards, and type erasure

Beginner-friendly study notes for Task F3.2 of the Zenith plan. We go through the concepts first,
one at a time, then do the subtasks. Every code example and output below was compiled and run on
Java 21.

## Progress

### Concepts

- [x] Concept 1 — "Is a": subtypes and supertypes
- [x] Concept 2 — Bounds: `<T extends Number>`
- [x] Concept 3 — `Comparable` and the bound `<T extends Comparable<T>>`
- [x] Concept 3b — Reading "upper bounds", "lower bounds" and "equality constraints" in errors
- [x] Concept 4 — Why a `List<Dog>` is not a `List<Animal>`
- [x] Concept 5 — Wildcards: `?` and `? extends X`
- [ ] Concept 6 — `? super X`, and the memory aid "produce extends, consume super"
- [ ] Concept 7 — Type erasure: generics disappear when the program runs

### Subtasks

- [ ] Subtask 1 — `firstTwo`: a generic method
- [ ] Subtask 2 — `largest` with a bound, then break it on purpose
- [ ] Subtask 3 — Try to add to a `List<? extends Number>`
- [ ] Subtask 4 — The memory aid: produce extends, consume super
- [ ] Subtask 5 — Meet erasure: `new T[10]` and `instanceof List<String>`
- [ ] Subtask 6 — Where this comes back: Jackson's `TypeReference`
- [ ] Checkpoint questions

## What this task is about

In Task F3.1, a type parameter `T` could be **anything**. This task adds three things:

1. **Bounds:** saying "T can be anything, *as long as it's a kind of X*", so you can call X's methods
   on it.
2. **Wildcards** (`?`): the question marks you'll see in Spring and Jackson signatures, like
   `List<? extends T>`. The goal is to **recognise** them, not master them.
3. **Type erasure:** the surprising fact that generics only exist while compiling, and are gone when
   the program runs.

All three are built on one older idea: one type can be **a kind of** another type. That's
Concept 1.

## Where things live

- **This file:** the reference notes for this task.
- **`../NOTES.md`:** your own notes, in your own words.

Each example is one complete file. The file name must match the `public class` name inside it. To
run one:

```bash
javac IsADemo.java
java IsADemo
```

---

## Concept 1 — "Is a": subtypes and supertypes

You met this in Task F2.6 and F2.7 (inheritance). Here it is again, slowly, because everything in
this task depends on it.

### Step 1: A small family of classes

```java
class Animal {
    String name() {
        return "animal";
    }
}

class Dog extends Animal {
    String name() {
        return "dog";
    }

    void bark() {
        System.out.println("Woof!");
    }
}

class Cat extends Animal {
    String name() {
        return "cat";
    }
}
```

- `Dog extends Animal` means "a `Dog` **is an** `Animal`". Every dog is an animal.
- `Cat extends Animal` means "a `Cat` **is an** `Animal`".
- `Dog` also has something extra that animals in general don't: `bark()`.

Two words for this relationship:

| Word | Meaning | Here |
| --- | --- | --- |
| **subtype** | the more specific type | `Dog` and `Cat` are subtypes of `Animal` |
| **supertype** | the more general type | `Animal` is the supertype of `Dog` and `Cat` |

```text
            Animal          ◄── supertype (general)
           /      \
        Dog        Cat      ◄── subtypes (specific)
```

### Step 2: A `Dog` can go where an `Animal` is expected

```java
public class IsADemo {

    static void describe(Animal animal) {
        System.out.println("Describing: " + animal.name());
    }

    public static void main(String[] args) {
        Dog rex = new Dog();
        Animal a = rex;

        System.out.println(a.name());

        describe(new Dog());
        describe(new Cat());
        describe(new Animal());
    }
}
```

(Put the `Animal`, `Dog` and `Cat` classes from Step 1 at the top of the same file.)

Output:

```text
dog
Describing: dog
Describing: cat
Describing: animal
```

- `Animal a = rex;` is allowed. A dog **is an** animal, so an `Animal` variable can hold it.
- `describe(Animal animal)` accepts a `Dog`, a `Cat` or an `Animal`. Anything that **is an**
  `Animal` fits.
- `a.name()` printed `dog`, not `animal`. The variable's type is `Animal`, but the real object is a
  `Dog`, so `Dog`'s version of `name()` runs. (That's polymorphism, from F2.6.)

**The rule:** wherever a supertype is expected, a subtype is allowed.

### Step 3: The other direction is not allowed

```java
class Animal {
}

class Dog extends Animal {
}

class Cat extends Animal {
}

public class WrongDirection {

    public static void main(String[] args) {
        Animal a = new Cat();
        Dog d = a;
    }
}
```

```text
WrongDirection.java:14: error: incompatible types: Animal cannot be converted to Dog
        Dog d = a;
                ^
```

Every dog is an animal, but **not every animal is a dog**. `a` is declared as `Animal`, and it's
actually a cat here. The compiler only knows "it's some animal", so it refuses to let you treat it
as a dog.

```text
Dog    ──►  Animal      allowed   (every dog is an animal)
Animal ──►  Dog         refused   (this animal might be a cat)
```

### Step 4: Through an `Animal` variable, you only see `Animal`'s methods

```java
class Animal {
    String name() {
        return "animal";
    }
}

class Dog extends Animal {
    void bark() {
        System.out.println("Woof!");
    }
}

public class OnlyAnimalMethods {

    public static void main(String[] args) {
        Animal a = new Dog();
        a.bark();
    }
}
```

```text
OnlyAnimalMethods.java:17: error: cannot find symbol
        a.bark();
         ^
  symbol:   method bark()
  location: variable a of type Animal
```

The object really is a dog, but the variable is declared `Animal`, and not every animal can bark. The
compiler only allows the methods that **every** `Animal` is guaranteed to have.

This is the same rule as `Object` in F3.1 Concept 2: **the compiler goes by the declared type, not
the real object.** Keep this in mind for Concept 2 (bounds), where it's the whole point.

### Step 5: Java's own family — `Number`

Java has a built-in class called `Number`, and the number wrapper classes are its subtypes:

```text
                 Number
         /      /     \       \
   Integer   Double   Long   (and a few more)
```

```java
public class NumberFamily {

    public static void main(String[] args) {
        Number a = 42;
        Number b = 9.99;
        Number c = 5_000_000_000L;

        System.out.println(a.getClass());
        System.out.println(b.getClass());
        System.out.println(c.getClass());

        System.out.println(a.doubleValue());
        System.out.println(b.intValue());
        System.out.println(c.doubleValue());
    }
}
```

Output:

```text
class java.lang.Integer
class java.lang.Double
class java.lang.Long
42.0
9
5.0E9
```

- `42` became an `Integer`, `9.99` a `Double`, `5_000_000_000L` a `Long` (the `L` means "a long
  number"; the `_` underscores are only there to make it readable). All three fit in a `Number`
  variable, because each **is a** `Number`.
- `Number` guarantees a few methods that every number has:
  - `doubleValue()` gives the number as a decimal: `42` became `42.0`.
  - `intValue()` gives it as a whole number, chopping off any decimals: `9.99` became `9`.
- `5.0E9` is how Java prints a very large `double`: 5.0 × 10⁹, which is 5,000,000,000.

And the wrong direction is refused, exactly like `Animal` and `Dog`:

```java
public class NumberWrongDirection {

    public static void main(String[] args) {
        Number n = 42;
        Integer i = n;
    }
}
```

```text
NumberWrongDirection.java:5: error: incompatible types: Number cannot be converted to Integer
        Integer i = n;
                    ^
```

A `Number` might be a `Double`, so it can't be put straight into an `Integer` variable.

We'll use `Number` a lot in this task, because it's a family you didn't have to write yourself.

### Summary

1. `Dog extends Animal` means "a dog **is an** animal". `Dog` is a **subtype**, `Animal` is a
   **supertype**.
2. A subtype can go wherever the supertype is expected: `Animal a = new Dog();`.
3. The other direction is refused: an `Animal` might be a `Cat`, so it can't go into a `Dog`
   variable.
4. Through a variable of the supertype, you can only call the supertype's methods. The compiler
   goes by the **declared** type.
5. Java's `Number` is the supertype of `Integer`, `Double` and `Long`. Every `Number` has
   `doubleValue()` and `intValue()`.

---

## Concept 2 — Bounds: `<T extends Number>`

In F3.1, `T` could be **anything**. That's flexible, but it has a cost: if T could be anything, the
compiler won't let you call any interesting methods on it. A **bound** fixes that.

### Step 1: The problem — you can't call methods on a plain `T`

We want a generic method that adds up a list of numbers:

```java
import java.util.List;

public class NoBound {

    static <T> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        System.out.println(sum(List.of(1, 2, 3)));
    }
}
```

```text
NoBound.java:8: error: cannot find symbol
            total = total + x.doubleValue();
                             ^
  symbol:   method doubleValue()
  location: variable x of type T
  where T is a type-variable:
    T extends Object declared in method <T>sum(List<T>)
```

We're only passing in numbers, so why the error?

Because of the rule from Concept 1, Step 4: **the compiler goes by the declared type.** Here x's
declared type is `T`, and `T` could be **anything**: an `Integer`, but also a `String` or a `Seat`.
A `String` has no `doubleValue()` method, so the compiler can't promise it exists.

Look at the last line of the error: **`T extends Object`**. That's what a plain `<T>` really means:
"T is some kind of `Object`". And the only methods every `Object` has are the basic ones like
`toString()`, `equals()` and `getClass()`:

```java
import java.util.List;

public class ObjectMethodsOnly {

    static <T> void show(List<T> xs) {
        for (T x : xs) {
            System.out.println(x.toString() + " is a " + x.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        show(List.of(1, 2));
        show(List.of("A1", "B2"));
    }
}
```

Output:

```text
1 is a Integer
2 is a Integer
A1 is a String
B2 is a String
```

`toString()` and `getClass()` work on a plain T, because **every** object has them.
(`getSimpleName()` just gives the class name without the `java.lang.` part.) `doubleValue()`
doesn't, because only numbers have it.

### Step 2: The fix — add a bound

```java
import java.util.List;

public class WithBound {

    static <T extends Number> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> tickets = List.of(2, 4, 1);
        List<Double> prices = List.of(49.99, 150.0);

        System.out.println(sum(tickets));
        System.out.println(sum(prices));
    }
}
```

Output:

```text
7.0
199.99
```

The only change is `<T>` becoming **`<T extends Number>`**. Read it aloud:

> "T can be any type, **as long as it is a `Number`**."

That restriction is called a **bound**. It works like a promise in both directions:

| Who | What the bound means for them |
| --- | --- |
| **inside the method** | "T is definitely some kind of `Number`, so every `Number` method is allowed on it": `x.doubleValue()` now compiles. |
| **the caller** | "You may only fill T with `Number` or a subtype of it": `Integer`, `Double`, `Long`... |

And it still works for different number types: `sum(tickets)` uses T = `Integer`, and `sum(prices)`
uses T = `Double`.

Note: in a bound, the word `extends` is used whether `Number` is a class or an interface. It just
means "is a kind of". (Concept 3 uses a bound that's an interface.)

### Step 3: The bound refuses the wrong types

```java
import java.util.List;

public class BoundRefusesString {

    static <T extends Number> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<String> seats = List.of("A1", "B2");
        System.out.println(sum(seats));
    }
}
```

```text
BoundRefusesString.java:15: error: method sum in class BoundRefusesString cannot be applied to given types;
        System.out.println(sum(seats));
                           ^
  required: List<T>
  found:    List<String>
  reason: inference variable T has incompatible bounds
    equality constraints: String
    upper bounds: Number
  where T is a type-variable:
    T extends Number declared in method <T>sum(List<T>)
```

Decode it:

| Error line | Plain English |
| --- | --- |
| `equality constraints: String` | "You passed a list of strings, so T would be `String`." |
| `upper bounds: Number` | "But T has to be a `Number` (that's the bound)." |
| `T extends Number declared in method ...` | "This is the bound you wrote." |

A `String` isn't a `Number`, so the call is refused. The word **"upper bound"** means "the most
general type T is allowed to be". Here, T can be `Number` or anything below it in the family tree.

```text
                 Number         ◄── upper bound: T can go up to here, no higher
         /      /     \
   Integer   Double   Long      ◄── all allowed

   String                       ◄── not in the family, refused
```

### Step 4: Why not just write `Number` instead of `T`?

Fair question: why `<T extends Number>` instead of a plain `Number` parameter? Because the bound
**keeps the exact type**. Compare two "bigger of two numbers" methods:

```java
public class KeepsExactType {

    static Number biggerNumber(Number a, Number b) {
        if (a.doubleValue() >= b.doubleValue()) {
            return a;
        }
        return b;
    }

    static <T extends Number> T bigger(T a, T b) {
        if (a.doubleValue() >= b.doubleValue()) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        Integer x = bigger(3, 7);
        System.out.println(x + 1);

        Double y = bigger(2.5, 1.5);
        System.out.println(y);

        Number z = biggerNumber(3, 7);
        System.out.println(z);
    }
}
```

Output:

```text
8
2.5
7
```

- `bigger(3, 7)`: T = `Integer`, so it **returns an `Integer`**. `x + 1` works straight away.
- `bigger(2.5, 1.5)`: T = `Double`, so it returns a `Double`.
- `biggerNumber(3, 7)` can only promise it returns "some `Number`". Try to use it as an `Integer`:

```java
public class LosesExactType {

    static Number biggerNumber(Number a, Number b) {
        if (a.doubleValue() >= b.doubleValue()) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        Integer x = biggerNumber(3, 7);
    }
}
```

```text
LosesExactType.java:11: error: incompatible types: Number cannot be converted to Integer
        Integer x = biggerNumber(3, 7);
                                ^
```

You put two `Integer`s in, but got back a plain `Number`. To use it as an `Integer` you'd need a cast,
which is the F3.1 problem all over again.

| | `Number` parameter | `<T extends Number>` |
| --- | --- | --- |
| Can call `doubleValue()` inside? | yes | yes |
| Pass in two `Integer`s, get back... | a `Number` (exact type lost) | an `Integer` (exact type kept) |
| Cast needed afterwards? | yes | no |

There's a second reason, about lists, that's surprising enough to get its own concept (Concept 4).

### Step 5: Bounds on a generic class

Classes can have bounds too. A locker that only holds numbers, and can therefore do maths on them:

```java
class NumberLocker<T extends Number> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }

    double doubled() {
        return item.doubleValue() * 2;
    }
}

public class BoundedClass {

    public static void main(String[] args) {
        NumberLocker<Integer> tickets = new NumberLocker<>();
        tickets.put(21);
        System.out.println(tickets.get());
        System.out.println(tickets.doubled());

        NumberLocker<Double> price = new NumberLocker<>();
        price.put(9.5);
        System.out.println(price.doubled());
    }
}
```

Output:

```text
21
42.0
19.0
```

`doubled()` calls `item.doubleValue()`, which is only allowed because of the bound
`<T extends Number>`. Try to make a locker of strings:

```java
public class BoundedClassWrong {

    public static void main(String[] args) {
        NumberLocker<String> names = new NumberLocker<>();
    }
}
```

(Same `NumberLocker` class at the top of the file.)

```text
BoundedClassWrong.java:16: error: type argument String is not within bounds of type-variable T
        NumberLocker<String> names = new NumberLocker<>();
                     ^
  where T is a type-variable:
    T extends Number declared in class NumberLocker
```

**"type argument String is not within bounds of type-variable T"**: "You tried to fill the blank T
with `String`, but T must be a `Number`." (The compiler then prints a second error about the diamond
`<>` for the same reason. Fix the first one and both go away.)

### Summary

1. A plain `<T>` secretly means `<T extends Object>`. You can only call `Object`'s methods on it
   (`toString`, `equals`, `getClass`).
2. A **bound** restricts T: `<T extends Number>` means "T can be any type, as long as it is a
   `Number`".
3. Inside, the bound lets you call the bound's methods on T (`x.doubleValue()`).
4. Outside, the bound refuses wrong types: "inference variable T has incompatible bounds... upper
   bounds: Number", or "type argument String is not within bounds of type-variable T".
5. `<T extends Number>` beats a plain `Number` parameter because it keeps the exact type: two
   `Integer`s in, an `Integer` back.
6. Bounds work on generic methods and generic classes. In a bound, `extends` means "is a kind of",
   for classes and interfaces alike.

---

## Concept 3 — `Comparable` and the bound `<T extends Comparable<T>>`

This is the bound you'll see most often in real code. It means "T must be something that can be put
in order". Subtask 2 is built on it.

### Step 1: `compareTo` — asking "which comes first?"

Some types have a natural order: numbers go smallest to largest, text goes alphabetically. Those
types have a method called `compareTo`:

```java
public class CompareToBasics {

    public static void main(String[] args) {
        Integer five = 5;
        Integer nine = 9;

        System.out.println(five.compareTo(nine));
        System.out.println(nine.compareTo(five));
        System.out.println(five.compareTo(5));

        System.out.println("apple".compareTo("banana"));
        System.out.println("banana".compareTo("apple"));
        System.out.println("apple".compareTo("apple"));
    }
}
```

Output:

```text
-1
1
0
-1
1
0
```

`a.compareTo(b)` returns a number, and **only its sign matters**:

| Result | Meaning | Example |
| --- | --- | --- |
| **negative** (below 0) | `a` comes **before** `b` | `5` before `9`; `"apple"` before `"banana"` |
| **zero** | they're equal in order | `5` and `5` |
| **positive** (above 0) | `a` comes **after** `b` | `9` after `5` |

Here the results happened to be exactly `-1` and `1`, but don't rely on that. For other values,
`String.compareTo` can return numbers like `17` or `-3`. Always test with `< 0`, `== 0` or `> 0`:

```java
public class UsingCompareTo {

    public static void main(String[] args) {
        String a = "Ravi";
        String b = "Asha";

        if (a.compareTo(b) < 0) {
            System.out.println(a + " comes first");
        } else if (a.compareTo(b) > 0) {
            System.out.println(b + " comes first");
        } else {
            System.out.println("They are the same");
        }
    }
}
```

Output:

```text
Asha comes first
```

`"Ravi".compareTo("Asha")` is positive (R comes after A), so `Asha` comes first.

### Step 2: Where does `compareTo` come from? The `Comparable` interface

`compareTo` isn't magic. It comes from a generic interface in Java's library:

```java
public interface Comparable<T> {
    int compareTo(T other);
}
```

Read it aloud: **"Something that is `Comparable<T>` can be compared to a T."** It has one method,
`compareTo`, which takes another T.

`String` and `Integer` both implement it. In Java's source code they're declared like this:

```java
public final class String implements Comparable<String>, ...
public final class Integer extends Number implements Comparable<Integer>
```

- `String implements Comparable<String>`: "a string can be compared to **another string**".
- `Integer implements Comparable<Integer>`: "an integer can be compared to **another integer**".

This is exactly the pattern from F3.1 Concept 9: the class fills in the interface's blank T with
**itself**.

### Step 3: Your own classes don't have it unless you add it

```java
record Seat(String id) { }

public class SeatNotComparable {

    public static void main(String[] args) {
        Seat a = new Seat("A1");
        Seat b = new Seat("B2");

        System.out.println(a.compareTo(b));
    }
}
```

```text
SeatNotComparable.java:9: error: cannot find symbol
        System.out.println(a.compareTo(b));
                            ^
  symbol:   method compareTo(Seat)
  location: variable a of type Seat
```

Java has no idea how seats should be ordered (by row? by number? by price?), so `Seat` has no
`compareTo`.

To give a class an order, **implement `Comparable`** and decide the rule yourself. Here, tickets are
ordered by price:

```java
record Ticket(String id, int price) implements Comparable<Ticket> {

    public int compareTo(Ticket other) {
        return Integer.compare(this.price, other.price);
    }
}

public class TicketComparable {

    public static void main(String[] args) {
        Ticket general = new Ticket("G-1", 50);
        Ticket vip = new Ticket("V-1", 150);

        System.out.println(general.compareTo(vip));
        System.out.println(vip.compareTo(general));
        System.out.println(general.compareTo(new Ticket("G-2", 50)));
    }
}
```

Output:

```text
-1
1
0
```

- `implements Comparable<Ticket>`: "a ticket can be compared to another ticket". The blank T is
  filled with `Ticket`, so the method we must write is `int compareTo(Ticket other)`.
- `Integer.compare(x, y)` is a ready-made helper that returns negative, zero or positive for two
  `int`s. It does the job for us.
- The cheaper ticket comes first (`-1`), and two tickets at the same price count as equal in order
  (`0`), even though their ids differ.

### Step 4: A generic method that needs `compareTo` — without a bound

We want a generic method that returns the smaller of two values, for any type:

```java
public class SmallerNoBound {

    static <T> T smallerOf(T a, T b) {
        if (a.compareTo(b) <= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        System.out.println(smallerOf(5, 9));
    }
}
```

```text
SmallerNoBound.java:4: error: cannot find symbol
        if (a.compareTo(b) <= 0) {
             ^
  symbol:   method compareTo(T)
  location: variable a of type T
  where T is a type-variable:
    T extends Object declared in method <T>smallerOf(T,T)
```

Same problem as Concept 2: **`T extends Object`**. With no bound, T could be a `Seat`, and seats
have no `compareTo`. So the compiler won't allow it.

### Step 5: Add the bound `<T extends Comparable<T>>`

```java
record Ticket(String id, int price) implements Comparable<Ticket> {

    public int compareTo(Ticket other) {
        return Integer.compare(this.price, other.price);
    }
}

public class SmallerWithBound {

    static <T extends Comparable<T>> T smallerOf(T a, T b) {
        if (a.compareTo(b) <= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        Integer n = smallerOf(5, 9);
        String s = smallerOf("Ravi", "Asha");
        Ticket t = smallerOf(new Ticket("V-1", 150), new Ticket("G-1", 50));

        System.out.println(n);
        System.out.println(s);
        System.out.println(t);
    }
}
```

Output:

```text
5
Asha
Ticket[id=G-1, price=50]
```

One method works for integers, strings **and** our own `Ticket`, and each call returns the exact type
(an `Integer`, a `String`, a `Ticket`) with no cast.

### Step 6: Reading `<T extends Comparable<T>>` aloud

It looks odd because `T` appears twice. Break it into pieces:

```text
<T  extends  Comparable<T>>
 │     │          │     │
 │     │          │     └── "...to another T"
 │     │          └──────── "...can be compared..."
 │     └─────────────────── "...must be something that..."
 └───────────────────────── "T..."

= "T must be something that can be compared to another T."
```

Why the second `T`? Because we compare `a` with `b`, and both are T's. We need a T that can be
compared **to another T**, not to something else.

Check it against the real types:

| T is | Does T implement `Comparable<T>`? | Allowed? |
| --- | --- | --- |
| `Integer` | yes: `Integer implements Comparable<Integer>` | yes |
| `String` | yes: `String implements Comparable<String>` | yes |
| `Ticket` | yes: we wrote `implements Comparable<Ticket>` | yes |
| `Seat` | no | **no** |

### Step 7: The bound refuses types that can't be compared

```java
record Seat(String id) { }

public class BoundRefusesSeat {

    static <T extends Comparable<T>> T smallerOf(T a, T b) {
        if (a.compareTo(b) <= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        smallerOf(new Seat("A1"), new Seat("B2"));
    }
}
```

```text
BoundRefusesSeat.java:13: error: method smallerOf in class BoundRefusesSeat cannot be applied to given types;
        smallerOf(new Seat("A1"), new Seat("B2"));
        ^
  required: T,T
  found:    Seat,Seat
  reason: inference variable T has incompatible bounds
    upper bounds: Comparable<T>
    lower bounds: Seat
  where T is a type-variable:
    T extends Comparable<T> declared in method <T>smallerOf(T,T)
```

Decode it:

| Error line | Plain English |
| --- | --- |
| `found: Seat,Seat` | "You passed two seats." |
| `upper bounds: Comparable<T>` | "T has to be something comparable (that's the bound)." |
| `lower bounds: Seat` | "But T would be `Seat`." |

`Seat` doesn't implement `Comparable`, so the call is refused before the program runs. Adding
`implements Comparable<Seat>` to `Seat`, as we did for `Ticket`, would fix it.

### Summary

1. `a.compareTo(b)` answers "which comes first?": **negative** = a first, **zero** = equal,
   **positive** = b first. Only the sign matters.
2. `compareTo` comes from the generic interface `Comparable<T>`. `String` and `Integer` implement
   it; your own classes don't unless you write `implements Comparable<YourClass>`.
3. A plain `<T>` can't call `compareTo` ("cannot find symbol ... T extends Object").
4. `<T extends Comparable<T>>` reads as "T must be something that can be compared to another T".
   With it, `a.compareTo(b)` compiles.
5. The bound refuses types that can't be compared, like `Seat`: "upper bounds: Comparable<T>,
   lower bounds: Seat".

---

## Concept 3b — Reading "upper bounds", "lower bounds" and "equality constraints" in errors

Almost every generics error you've seen so far has lines like these:

```text
    upper bounds: Number
    lower bounds: String
```

or

```text
    equality constraints: String
    upper bounds: Integer,Object
```

This section explains exactly what they mean, from the ground up.

### The idea: the compiler has to choose T

When you call a generic method, you usually don't say what T is. The compiler has to **work it out**.
It does that like a detective: it collects **clues** from your code, and each clue is a rule about
what T is allowed to be. Then it looks for a type that obeys **every** rule at once.

There are only three kinds of clue:

| Clue in the error | Plain meaning | Think of it as |
| --- | --- | --- |
| **upper bound: X** | "T can be X, or anything **below** X" | a **ceiling**: T can't go higher than X |
| **lower bound: X** | "T can be X, or anything **above** X" | a **floor**: T can't go lower than X |
| **equality constraint: X** | "T must be **exactly** X" | a fixed spot |

If some type fits between the floor and the ceiling (and matches any exact rule), the code compiles.
If **no** type fits, you get **"inference variable T has incompatible bounds"**, which just means
"your clues contradict each other, so no T works".

### An everyday version first

A theme park ride has a sign:

> **You must be at least 120 cm tall, and at most 190 cm tall.**

- "At least 120 cm" is a **floor** (lower bound). You can't be shorter.
- "At most 190 cm" is a **ceiling** (upper bound). You can't be taller.
- Anyone between 120 and 190 can ride.
- If the sign said "at least 190 cm and at most 120 cm", **nobody** could ride. The rules
  contradict each other. That's "incompatible bounds".

The compiler does the same thing, except instead of heights it uses the **family tree of types**.

### The family tree, with "up" and "down"

```text
                     Object          ◄── top: everything is an Object
                    /      \
               Number      String
              /      \
         Integer    Double           ◄── bottom: the most specific
```

- **Up** means more general: `Integer` → `Number` → `Object`.
- **Down** means more specific: `Object` → `Number` → `Integer`.
- `String` is on a **different branch**. It's not above or below `Number` or `Integer`. They're not
  related, except that all of them are `Object`s.

So "T can be anything **below** Number" means T can be `Number`, `Integer` or `Double`, but not
`Object` (too high) and not `String` (different branch).

### Where do the clues come from?

| Clue | Comes from | Why |
| --- | --- | --- |
| **upper bound** (ceiling) | 1. a bound you wrote: `<T extends Number>` | you said T must be a `Number` or below |
| | 2. the variable you store the result in: `String s = echo(...)` | the result must fit in a `String` variable, so T must be `String` or below |
| | 3. always: `Object` | every T is at least an `Object`, so `Object` is always a ceiling. That's why you keep seeing `Object` in the list. |
| **lower bound** (floor) | a value you pass in: `echo(5)` | T has to be able to hold an `Integer`, so T must be `Integer` or above |
| **equality constraint** (exact) | a generic type passed in: a `List<String>` given to a `List<T>` parameter | a `List<T>` must match a `List<String>` **exactly**, so T must be exactly `String` (Concept 4 explains why it must be exact) |

Now let's see each one in real code. We'll use one tiny generic method that just gives back whatever
you give it:

```java
static <T> T echo(T x) {
    return x;
}
```

"For any type T, `echo` takes a T and gives back a T."

### Example 1: Floor and ceiling that fit — it compiles

```java
public class EchoWorks {

    static <T> T echo(T x) {
        return x;
    }

    public static void main(String[] args) {
        Number n = echo(5);
        Object o = echo(5);
        Integer i = echo(5);

        System.out.println(n);
        System.out.println(o);
        System.out.println(i);
    }
}
```

Output:

```text
5
5
5
```

Look at the first line, `Number n = echo(5);`, the way the compiler does:

- **Clue 1**, from the value `5` passed in: T must be able to hold an `Integer`. That gives a
  **lower bound** (floor) of `Integer`.
- **Clue 2**, from the variable `Number n`: the result must fit in a `Number`. That gives an
  **upper bound** (ceiling) of `Number`.

```text
                     Object
                    /
               Number          ◄── ceiling (upper bound): T can't be higher than this
              /
         Integer               ◄── floor (lower bound): T can't be lower than this

   T can be Integer or Number. Something fits, so it compiles.
```

The same goes for `Object o = echo(5)` (floor `Integer`, ceiling `Object`) and `Integer i = echo(5)`
(floor and ceiling both `Integer`, so T = `Integer` exactly). Each time, something fits.

### Example 2: Floor and ceiling on different branches — error

```java
public class EchoFails {

    static <T> T echo(T x) {
        return x;
    }

    public static void main(String[] args) {
        String s = echo(5);
    }
}
```

```text
EchoFails.java:8: error: incompatible types: inference variable T has incompatible bounds
        String s = echo(5);
                       ^
    upper bounds: String,Object
    lower bounds: Integer
  where T is a type-variable:
    T extends Object declared in method <T>echo(T)
```

The clues:

- `lower bounds: Integer`: you passed `5`, so T must be `Integer` **or above**.
- `upper bounds: String,Object`: you're storing the result in a `String`, so T must be `String` **or
  below**. (`Object` is always there; ignore it.)

```text
                     Object
                    /      \
               Number      String     ◄── ceiling: T must be String or below
              /
         Integer                      ◄── floor: T must be Integer or above

   Is there a type that is BOTH "Integer or above" AND "String or below"?
   Integer or above:  Integer, Number, Object
   String or below:   String
   Nothing is in both lists, so: incompatible bounds.
```

Just like the ride sign that nobody can satisfy. In plain English: **"You gave me a number, but you
want a String back. No T can do both."**

### Example 3: The ceiling comes from `extends` — error

Now a method with a bound:

```java
public class EchoNumberFails {

    static <T extends Number> T echoNumber(T x) {
        return x;
    }

    public static void main(String[] args) {
        echoNumber("A1");
    }
}
```

```text
EchoNumberFails.java:8: error: method echoNumber in class EchoNumberFails cannot be applied to given types;
        echoNumber("A1");
        ^
  required: T
  found:    String
  reason: inference variable T has incompatible bounds
    upper bounds: Number
    lower bounds: String
  where T is a type-variable:
    T extends Number declared in method <T>echoNumber(T)
```

The clues:

- `upper bounds: Number`: from **your bound** `<T extends Number>`. T must be `Number` or below.
- `lower bounds: String`: from the value `"A1"` you passed. T must be `String` or above.

```text
                     Object
                    /      \
               Number      String     ◄── floor: T must be String or above
              /      \
         Integer    Double
               ▲
               └── ceiling: T must be Number or below (Number, Integer, Double)

   "Number or below" and "String or above" share nothing, so: incompatible bounds.
```

In plain English: **"T has to be a number (your bound), but you gave me text."**

The same method with numbers works fine:

```java
public class EchoNumberWorks {

    static <T extends Number> T echoNumber(T x) {
        return x;
    }

    public static void main(String[] args) {
        Integer i = echoNumber(5);
        Double d = echoNumber(2.5);

        System.out.println(i);
        System.out.println(d);
    }
}
```

Output:

```text
5
2.5
```

For `echoNumber(5)`: floor `Integer`, ceiling `Number`, and `Integer` fits.

### Example 4: An exact rule — "equality constraints"

```java
import java.util.List;

public class EqualityFails {

    static <T> T firstOf(List<T> list) {
        return list.get(0);
    }

    public static void main(String[] args) {
        List<String> names = List.of("Ravi");
        Integer n = firstOf(names);
    }
}
```

```text
EqualityFails.java:11: error: incompatible types: inference variable T has incompatible bounds
        Integer n = firstOf(names);
                           ^
    equality constraints: String
    upper bounds: Integer,Object
  where T is a type-variable:
    T extends Object declared in method <T>firstOf(List<T>)
```

The clues:

- `equality constraints: String`: you passed a `List<String>` where a `List<T>` is expected. For
  those to match, T must be **exactly** `String`. Not above, not below, exactly.
- `upper bounds: Integer,Object`: you're storing the result in an `Integer`, so T must be
  `Integer` or below.

```text
   Exact rule:  T = String
   Ceiling:     T must be Integer or below

   Is String "Integer or below"? No, it's on a different branch. So: incompatible bounds.
```

In plain English: **"The first item of a list of strings is a String, and a String can't go into an
Integer variable."**

Why is a list an *exact* rule instead of a floor? Because a `List<String>` can only ever be treated
as a list of **exactly** strings, never as a list of something more general. That surprising rule is
what Concept 4 is all about.

### Example 5: Re-reading the errors from Concepts 2 and 3

You can now read the earlier errors yourself.

**Concept 2**, `sum(seats)` where `sum` is `<T extends Number> double sum(List<T> xs)` and `seats` is
a `List<String>`:

```text
    equality constraints: String
    upper bounds: Number
```

- Exact rule: T = `String` (from the `List<String>` you passed).
- Ceiling: `Number` (from `<T extends Number>`).
- `String` is not `Number` or below, so the clues contradict each other.

**Concept 3**, `smallerOf(new Seat("A1"), new Seat("B2"))` where `smallerOf` is
`<T extends Comparable<T>> T smallerOf(T a, T b)`:

```text
    upper bounds: Comparable<T>
    lower bounds: Seat
```

- Ceiling: `Comparable<T>` (from the bound). T must be something comparable.
- Floor: `Seat` (from the seats you passed). T must be `Seat` or above.
- Neither `Seat` nor anything above it is `Comparable`, so the clues contradict each other.

### How to read any of these errors in 10 seconds

1. Find the line **`inference variable T has incompatible bounds`**. It means "your clues about T
   contradict each other".
2. Read the clue lines:
   - **`lower bounds: X`**: "you **gave** me an X" (usually a value you passed in).
   - **`upper bounds: Y`**: "T must **fit inside** Y" (from an `extends` bound, or the variable
     you're storing the result in). Ignore `Object` in this list; it's always there.
   - **`equality constraints: Z`**: "T must be **exactly** Z" (usually from a `List<Z>` or other
     generic type you passed in).
3. Say it as one sentence: "I gave it an X / a list of Z, but it needs something that fits inside
   Y." That sentence is almost always the whole bug.

### Summary

1. When you call a generic method, the compiler collects clues about T and looks for a type that
   obeys all of them.
2. **Upper bound** = a **ceiling**: T can be that type or anything **below** it (more specific).
   It comes from `extends` in the declaration, or from the variable that receives the result.
   `Object` is always one.
3. **Lower bound** = a **floor**: T can be that type or anything **above** it (more general). It
   comes from the values you pass in.
4. **Equality constraint** = an **exact** type. It usually comes from passing a generic type like a
   `List<String>`.
5. **"incompatible bounds"** means no type fits all the clues, like a ride sign that says "at least
   190 cm and at most 120 cm".

---

## Concept 4 — Why a `List<Dog>` is not a `List<Animal>`

The task says to go slowly here, because this is the one part of generics that genuinely confuses
people. It's also the reason wildcards (Concepts 5 and 6) exist.

For this concept, the animal classes live in their own file, `Animals.java`, so we don't repeat them
in every example:

```java
class Animal {
    String name() {
        return "animal";
    }
}

class Dog extends Animal {
    String name() {
        return "dog";
    }

    void bark() {
        System.out.println("Woof!");
    }
}

class Cat extends Animal {
    String name() {
        return "cat";
    }
}
```

Compile it together with each example: `javac Animals.java ListNotList.java`, then
`java ListNotList`.

### Step 1: The natural guess

From Concept 1 you know:

```text
Dog  ──►  Animal        allowed: a dog is an animal
```

So it feels obvious that this should work too:

```text
List<Dog>  ──►  List<Animal>        "a list of dogs is a list of animals"... right?
```

Let's try it:

```java
import java.util.ArrayList;
import java.util.List;

public class ListNotList {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());

        List<Animal> animals = dogs;
    }
}
```

```text
ListNotList.java:10: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        List<Animal> animals = dogs;
                               ^
```

**Refused.** Even though every dog is an animal, Java says a list of dogs is **not** a list of
animals. Why?

### Step 2: The reason — think about *adding*

A `List<Animal>` makes a promise: **"You can add any animal to me."** Dogs, cats, anything that is an
`Animal`.

A `List<Dog>` makes a different promise: **"Everything inside me is a dog."**

Now imagine Java *did* allow `List<Animal> animals = dogs;`. Both variables would point at **the same
list**:

```text
   dogs     ──┐
              ├──►  [ Dog ]        one list in memory
   animals  ──┘
```

Then:

```java
animals.add(new Cat());   // allowed: animals is a List<Animal>, and a cat is an animal
```

But `animals` **is** the dog list. So now:

```text
   dogs     ──┐
              ├──►  [ Dog, Cat ]   a cat is inside the list of dogs
   animals  ──┘
```

```java
Dog d = dogs.get(1);      // the compiler thinks this is a Dog... it's a Cat
d.bark();                 // cats can't bark
```

The `List<Dog>` broke its promise, and nobody wrote anything obviously wrong. Every single line
looked legal.

**That's why Java refuses `List<Animal> animals = dogs;`.** If it allowed it, a `Cat` could get
into a `List<Dog>`.

### Step 3: Proof — arrays allow it, and crash at runtime

Java's **arrays** were designed (back in 1996, before generics) to allow exactly this conversion. So
we can watch the problem actually happen:

```java
public class ArraysAllowIt {

    public static void main(String[] args) {
        Dog[] dogs = new Dog[2];
        dogs[0] = new Dog();

        Animal[] animals = dogs;
        System.out.println("Treating the dog array as an animal array: OK");

        animals[1] = new Cat();
        System.out.println("This line never prints");
    }
}
```

```bash
javac Animals.java ArraysAllowIt.java
java ArraysAllowIt
```

Output:

```text
Treating the dog array as an animal array: OK
Exception in thread "main" java.lang.ArrayStoreException: Cat
	at ArraysAllowIt.main(ArraysAllowIt.java:10)
```

- `Animal[] animals = dogs;` **compiled**: arrays allow `Dog[]` → `Animal[]`.
- `animals[1] = new Cat();` also **compiled**: a cat is an animal.
- At **runtime**, Java noticed that the real array is a `Dog[]` and stopped with an
  `ArrayStoreException` ("you tried to store the wrong kind of thing in this array").

It's the exact problem from Step 2, but caught at **runtime**, in front of a user. When generics were
added in 2004, Java's designers chose **not** to repeat that mistake. Instead of a runtime crash,
generics give you a **compile-time** error (Step 1). That's the F3.1 lesson again: move the error
earlier.

### Step 4: Proof — forcing it with generics corrupts the list

You can force the conversion past the compiler with a raw-type trick (F3.1 Concept 5). **Never do
this in real code.** It's only here to show what the compiler was protecting you from:

```java
import java.util.ArrayList;
import java.util.List;

public class ForcedCorruption {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());

        List<Animal> animals = (List<Animal>) (List) dogs;

        animals.add(new Cat());
        System.out.println("Added a cat to the dog list. Size is now " + dogs.size());

        for (Dog d : dogs) {
            d.bark();
        }
    }
}
```

Output:

```text
Added a cat to the dog list. Size is now 2
Woof!
Exception in thread "main" java.lang.ClassCastException: class Cat cannot be cast to class Dog (Cat and Dog are in unnamed module of loader 'app')
	at ForcedCorruption.main(ForcedCorruption.java:16)
```

- `(List<Animal>) (List) dogs` forces the conversion: first to a raw `List`, then to `List<Animal>`.
  `@SuppressWarnings("unchecked")` hides the warning.
- `animals.add(new Cat())` put a cat into the dog list. The list now has 2 items.
- The loop treats every item as a `Dog`. The first one barks (`Woof!`). The second one is a cat, so:
  `ClassCastException: Cat cannot be cast to Dog`.

That is precisely the disaster from Step 2. The compiler's refusal in Step 1 is what normally stops
it.

### Step 5: The frustrating part — methods refuse too

This rule also applies to method parameters, which is where it really bites:

```java
import java.util.ArrayList;
import java.util.List;

public class MethodRefuses {

    static void printAll(List<Animal> animals) {
        for (Animal a : animals) {
            System.out.println(a.name());
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());

        printAll(dogs);
    }
}
```

```text
MethodRefuses.java:16: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        printAll(dogs);
                 ^
```

That's annoying. `printAll` only **reads** the animals and prints their names. It never adds a cat,
so it would be perfectly safe to give it a list of dogs. But the compiler only sees the parameter
type `List<Animal>`, which means "a list you can add any animal to", so it refuses.

The same thing happens with numbers:

```java
import java.util.List;

public class NumberListRefuses {

    static double total(List<Number> xs) {
        double sum = 0;
        for (Number x : xs) {
            sum = sum + x.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Integer> tickets = List.of(2, 4, 1);
        System.out.println(total(tickets));
    }
}
```

```text
NumberListRefuses.java:15: error: incompatible types: List<Integer> cannot be converted to List<Number>
        System.out.println(total(tickets));
                                 ^
```

A `List<Integer>` is not a `List<Number>`, for exactly the same reason: if it were, `total` could add
a `Double` into your list of integers.

This is the second reason (promised in Concept 2) why `<T extends Number>` is useful: it lets the
method accept a `List<Integer>` **or** a `List<Double>`. **Wildcards** (Concept 5) are the other way
to solve it, and they're what you'll see in Spring's signatures.

### Step 6: The rule, stated plainly

```text
   Dog          is an     Animal           yes
   Integer      is a      Number           yes

   List<Dog>    is a      List<Animal>     NO
   List<Integer> is a     List<Number>     NO
```

**Even when `Dog` is a subtype of `Animal`, `List<Dog>` and `List<Animal>` are completely unrelated
types.** The thing inside the angle brackets must match **exactly**.

The official word for this is **invariance**: generic types are *invariant*, which means the
relationship between `Dog` and `Animal` does **not** carry over to `List<Dog>` and `List<Animal>`.
(This is also why Concept 3b's errors showed **equality constraints**: a `List<T>` matched against a
`List<String>` forces T to be *exactly* `String`.)

### Step 7: What *is* allowed

Two things that are fine, so you don't over-learn the rule:

```java
import java.util.ArrayList;
import java.util.List;

public class WhatIsAllowed {

    public static void main(String[] args) {
        ArrayList<Dog> dogArrayList = new ArrayList<>();
        List<Dog> dogs = dogArrayList;
        dogs.add(new Dog());
        System.out.println("ArrayList<Dog> used as List<Dog>: OK");

        List<Animal> animals = new ArrayList<>();
        animals.add(new Dog());
        animals.add(new Cat());
        for (Animal a : animals) {
            System.out.println(a.name());
        }
    }
}
```

Output:

```text
ArrayList<Dog> used as List<Dog>: OK
dog
cat
```

1. **The outside type can change, as long as the inside matches.** `ArrayList<Dog>` → `List<Dog>` is
   fine, because an `ArrayList` is a `List`, and the inside is `Dog` on both sides.
2. **A `List<Animal>` can hold dogs and cats.** Putting a `Dog` *into* a list of animals is fine.
   What's refused is treating a whole **list of dogs** as a **list of animals**.

| Conversion | Allowed? | Why |
| --- | --- | --- |
| `Dog` → `Animal` | yes | a dog is an animal |
| `ArrayList<Dog>` → `List<Dog>` | yes | outside changes, inside matches exactly |
| adding a `Dog` to a `List<Animal>` | yes | the list accepts any animal |
| `List<Dog>` → `List<Animal>` | **no** | then you could add a cat to the dog list |
| `List<Integer>` → `List<Number>` | **no** | then you could add a double to the integer list |

### Summary

1. `Dog` is an `Animal`, but **`List<Dog>` is not a `List<Animal>`**. The compiler says
   "`List<Dog>` cannot be converted to `List<Animal>`".
2. The reason is **adding**: a `List<Animal>` lets you add a `Cat`. If a list of dogs could be
   treated as a list of animals, a cat could get into the dog list.
3. Arrays *do* allow `Dog[]` → `Animal[]`, and crash at runtime with `ArrayStoreException`. Generics
   chose a compile-time error instead.
4. It bites method parameters: `printAll(List<Animal>)` refuses a `List<Dog>`, even if it only
   reads.
5. The rule is called **invariance**: the type inside the angle brackets must match exactly.
6. The outside type can change (`ArrayList<Dog>` → `List<Dog>`), and a `List<Animal>` can hold dogs.
7. To write a method that accepts a list of dogs **or** cats **or** animals, you need a wildcard.
   That's Concept 5.

---

## Concept 5 — Wildcards: `? extends X` and `?`

We'll go one small step at a time. Each step has **one idea**.

The animal classes are in `Animals.java`, the same as in Concept 4: `Animal`, `Dog` (which can also
`bark()`) and `Cat`. Compile each example together with it, for example
`javac Animals.java ExtendsPrint.java`, then `java ExtendsPrint`.

### Step 1: Remember the problem

From Concept 4:

```java
static void printNames(List<Animal> animals) { ... }

printNames(dogs);   // dogs is a List<Dog>
```

```text
error: incompatible types: List<Dog> cannot be converted to List<Animal>
```

`List<Animal>` only accepts a list that was made as a list of **exactly** `Animal`. A list of dogs is
refused.

But `printNames` only **looks** at the animals. It would be perfectly safe to give it dogs. We want a
way to say:

> "Give me a list of animals, **or** a list of dogs, **or** a list of cats. I don't mind which."

### Step 2: A picture — baskets

Think of each list as a **basket** with a label saying what's allowed inside:

```text
  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
  │  DOGS ONLY  │     │  CATS ONLY  │     │ ANY ANIMAL  │
  │  Dog  Dog   │     │  Cat        │     │  Dog  Cat   │
  └─────────────┘     └─────────────┘     └─────────────┘
    List<Dog>           List<Cat>           List<Animal>
```

`printNames(List<Animal>)` says: **"I only accept the ANY ANIMAL basket."**

What we want to say is: **"I accept any of these three baskets."**

### Step 3: The fix — `List<? extends Animal>`

Change one thing, the parameter type:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsPrint {

    static void printNames(List<? extends Animal> animals) {
        for (Animal a : animals) {
            System.out.println(a.name());
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());
        dogs.add(new Dog());

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat());

        List<Animal> mixed = new ArrayList<>();
        mixed.add(new Dog());
        mixed.add(new Cat());

        System.out.println("-- dogs --");
        printNames(dogs);
        System.out.println("-- cats --");
        printNames(cats);
        System.out.println("-- mixed --");
        printNames(mixed);
    }
}
```

Output:

```text
-- dogs --
dog
dog
-- cats --
cat
-- mixed --
dog
cat
```

All three baskets are accepted. **That's the whole fix.**

### Step 4: How to read `? extends Animal`

```text
List< ?           extends Animal >
      │           │
      │           └── "...which is Animal, or a kind of Animal"
      └────────────── "some type I'm not naming..."
```

Say it out loud as: **"a list of some kind of animal".**

- The `?` is called a **wildcard**. It means "some type, I'm not saying which".
- `extends Animal` limits it: the type must be `Animal`, `Dog`, `Cat`, or any other kind of
  animal.

So `List<? extends Animal>` matches:

| List | Matches? |
| --- | --- |
| `List<Animal>` | yes |
| `List<Dog>` | yes |
| `List<Cat>` | yes |
| `List<String>` | **no**, strings aren't animals |

The last row, as real code:

```java
import java.util.List;

public class ExtendsRefusesString {

    static void printNames(List<? extends Animal> animals) {
        for (Animal a : animals) {
            System.out.println(a.name());
        }
    }

    public static void main(String[] args) {
        List<String> words = List.of("hello");
        printNames(words);
    }
}
```

```text
ExtendsRefusesString.java:13: error: incompatible types: List<String> cannot be converted to List<? extends Animal>
        printNames(words);
                   ^
```

### Step 5: Inside the method, the basket is closed

Here's the key thing to understand. Inside `printNames`, the method **doesn't know which basket it
was given**. It might be the dog basket, the cat basket or the any-animal basket.

```text
           printNames receives...

           ┌──────────────────────┐
           │   ??? (closed lid)   │
           │ "some kind of animal │
           │      is inside"      │
           └──────────────────────┘

           Could be DOGS ONLY, CATS ONLY, or ANY ANIMAL.
           The method can't tell.
```

Everything else follows from that one fact. There are only two things you can do with a basket:
**take something out**, or **put something in**.

### Step 6: Taking out is safe

If you reach into the closed basket and take something out, what will you get?

| If it's really the... | you take out a... | is it an animal? |
| --- | --- | --- |
| DOGS ONLY basket | dog | yes |
| CATS ONLY basket | cat | yes |
| ANY ANIMAL basket | dog or cat | yes |

**Whatever the basket is, you always get an animal.** So the compiler lets you take items out as
`Animal`. That's why the loop `for (Animal a : animals)` and `a.name()` work.

But you only know it's *an animal*, not *which* animal. So you can only use what **every** animal can
do. Making it bark is refused:

```java
import java.util.List;

public class ExtendsNoBark {

    static void makeThemBark(List<? extends Animal> animals) {
        for (Animal a : animals) {
            a.bark();
        }
    }

    public static void main(String[] args) {
        makeThemBark(List.of(new Dog()));
    }
}
```

```text
ExtendsNoBark.java:7: error: cannot find symbol
            a.bark();
             ^
  symbol:   method bark()
  location: variable a of type Animal
```

It might be the cat basket, and cats can't bark.

### Step 7: Putting in is refused

Now try to put a dog into the closed basket:

| If it's really the... | can you put a dog in? |
| --- | --- |
| DOGS ONLY basket | yes |
| CATS ONLY basket | **no**: that's a dog in the cat basket |
| ANY ANIMAL basket | yes |

The method can't tell which basket it has, and in one of the three cases it would be wrong. So **the
compiler refuses**, even though it's "just a dog":

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsNoAdd {

    static void addADog(List<? extends Animal> animals) {
        animals.add(new Dog());
    }

    public static void main(String[] args) {
        addADog(new ArrayList<Cat>());
    }
}
```

```text
ExtendsNoAdd.java:7: error: incompatible types: Dog cannot be converted to CAP#1
        animals.add(new Dog());
                    ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

Look at `main`: we really did hand it the **cat** basket. Without this error, a dog would have ended
up in a list of cats. That's exactly the Concept 4 problem, and this is the compiler preventing it.

### Step 8: What is `CAP#1`?

You'll see `CAP#1` in almost every wildcard error, so it's worth one minute.

`CAP#1` is just the compiler's **nickname for "the type that's really in the closed basket"**. It
doesn't know the real name (`Dog`? `Cat`?), so it calls it CAP#1.

Read the error with that in mind:

| Error text | Plain English |
| --- | --- |
| `CAP#1` | "the type in the closed basket" |
| `CAP#1 extends Animal` | "all I know is that it's some kind of animal" |
| `Dog cannot be converted to CAP#1` | "I can't promise a dog belongs in this basket" |

Whenever you see `CAP#1` (or `CAP#2`...), say **"the unknown type in the basket"** in your head.

### Step 9: The rule in one line

> **`List<? extends Animal>`: you can take animals out. You can't put anything in.**

```text
                    ┌──────────────────────┐
   take out ◄───────│  some kind of animal │◄─────── put in
   you get an       └──────────────────────┘         REFUSED
   Animal: OK
```

(The only thing you can put in is `null`, which means "nothing", so it fits any basket. You'll almost
never need it.)

That's the task's table: **`? extends X` is safe to read from, not safe to add to.**

A list you only take things out of is **giving** (producing) values to you. Keep that word in mind
for Concept 6.

### Step 10: The same thing with numbers

Everything above works the same way for numbers. `List<? extends Number>` means "a list of some kind
of number": integers, doubles, or a mix.

**Taking out works:**

```java
import java.util.List;

public class NumberTotal {

    static double total(List<? extends Number> xs) {
        double sum = 0;
        for (Number x : xs) {
            sum = sum + x.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Integer> tickets = List.of(2, 4, 1);
        List<Double> prices = List.of(49.99, 150.0);
        List<Number> mixed = List.of(1, 2.5);

        System.out.println(total(tickets));
        System.out.println(total(prices));
        System.out.println(total(mixed));
    }
}
```

Output:

```text
7.0
199.99
3.5
```

Every item is some kind of `Number`, so `doubleValue()` is safe.

**Putting in is refused:**

```java
import java.util.ArrayList;
import java.util.List;

public class NumberNoAdd {

    static void addFive(List<? extends Number> xs) {
        xs.add(5);
    }

    public static void main(String[] args) {
        addFive(new ArrayList<Double>());
    }
}
```

```text
NumberNoAdd.java:7: error: incompatible types: int cannot be converted to CAP#1
        xs.add(5);
               ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Number from capture of ? extends Number
```

"The basket holds some kind of number, but it might be the DOUBLES ONLY basket (and in `main` it
is), so I can't put an integer `5` in."

### Step 11: It works for variables too

The task asks you to see this working:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsVariable {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());

        List<? extends Animal> someAnimals = dogs;

        Animal first = someAnimals.get(0);
        System.out.println(first.name());
        System.out.println(someAnimals.size());
    }
}
```

Output:

```text
dog
1
```

Side by side:

```java
List<Animal> animals = dogs;                // error (Concept 4): you could put a cat in
List<? extends Animal> someAnimals = dogs;  // OK: you can't put anything in
```

The second line is allowed **because** `someAnimals` can't be used to put a cat into the dog list.

### Step 12: The plain `?` — "a list of anything"

You can also write `?` on its own, with no `extends`:

```text
List<?>        "a list of something" (any type at all)
```

It's the same closed basket, except now you don't even know it holds animals. It could be strings,
numbers, anything. So:

- **Taking out** gives you an `Object` (the only thing everything is).
- **Putting in** is refused.

```java
import java.util.List;

public class UnknownListPrint {

    static void printAll(List<?> items) {
        for (Object item : items) {
            System.out.println(item);
        }
        System.out.println("size: " + items.size());
    }

    public static void main(String[] args) {
        printAll(List.of("A1", "B2"));
        printAll(List.of(5, 9, 12));
        printAll(List.of(true));
    }
}
```

Output:

```text
A1
B2
size: 2
5
9
12
size: 3
true
size: 1
```

Use `List<?>` when you don't care what's inside at all, for example just printing items or counting
them.

### Summary

| You write | Read aloud | Accepts | Take out as | Put in? |
| --- | --- | --- | --- | --- |
| `List<Animal>` | a list of animals | **only** `List<Animal>` | `Animal` | yes |
| `List<? extends Animal>` | a list of some kind of animal | `List<Animal>`, `List<Dog>`, `List<Cat>` | `Animal` | **no** |
| `List<?>` | a list of something | any list | `Object` | **no** |

1. `? extends Animal` lets one method accept a list of animals, dogs **or** cats. That fixes
   Concept 4's problem.
2. Inside the method the basket is **closed**: it doesn't know which kind of list it got.
3. **Taking out** is safe: whatever the basket, you get an `Animal`.
4. **Putting in** is refused: it might be the wrong basket (a dog into the cats).
5. `CAP#1` in an error means "the unknown type in the basket".

---

### Extra details (read once, no need to memorise)

These are useful to recognise later. Everything above is the important part.

**1. `List<?>` versus a raw `List`.** A raw `List` (F3.1 Concept 5) also accepts any list, but it
*lets you put things in*, and that corrupts the list:

```java
import java.util.ArrayList;
import java.util.List;

public class RawVsUnknown {

    static void rawAdd(List items) {
        items.add("hello");
    }

    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(1);

        rawAdd(numbers);
        System.out.println(numbers);

        Integer second = numbers.get(1);
    }
}
```

```text
Note: RawVsUnknown.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
[1, hello]
Exception in thread "main" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer ...
	at RawVsUnknown.main(RawVsUnknown.java:17)
```

`List<?>` would have refused the `add`. So when you mean "any list", write `List<?>`, never raw
`List`.

**2. `<T extends Number>` versus `? extends Number`.** Both accept a list of integers or doubles. Use
`<T extends Number>` (Concept 2) when you need to **name** the type and use it again, for example to
return it: `<T extends Number> T bigger(T a, T b)`. Use `? extends Number` when you only mention it
once. They also work together. This is the task's own example, and it compiles and runs:

```java
import java.util.List;

public class TaskExample {

    static <T extends Number> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    static double total(List<? extends Number> xs) {
        return sum(xs);
    }

    public static void main(String[] args) {
        System.out.println(total(List.of(1, 2, 3)));
        System.out.println(total(List.of(0.5, 0.25)));
    }
}
```

Output:

```text
6.0
0.75
```

**3. A real signature from Java.** `List` has this method, which copies every item from another
collection into the list:

```java
boolean addAll(Collection<? extends E> c)
```

Read it as: "`addAll` takes a collection of **some kind of E**." It only takes items *out* of `c`, so
`? extends` is safe. That's why a list of animals accepts a list of dogs and a list of cats:

```java
import java.util.ArrayList;
import java.util.List;

public class AddAllDemo {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog());

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat());

        List<Animal> shelter = new ArrayList<>();
        shelter.addAll(dogs);
        shelter.addAll(cats);

        for (Animal a : shelter) {
            System.out.println(a.name());
        }
    }
}
```

Output:

```text
dog
cat
```

When you see `? extends Something` in Spring or Jackson, read it as **"some kind of Something, and
the method only takes things out of it"**, and move on.

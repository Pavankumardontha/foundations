# Task F3.1 — Generics: the problem they solve

Beginner-friendly study notes for Task F3.1 of the Zenith plan. We go through the concepts first,
one at a time, then do the subtasks. Every code example and output below was compiled and run on
Java 21.

## Progress

### Concepts

- [x] Concept 1 — Compile time versus runtime: two moments an error can appear
- [x] Concept 2 — `Object`, casts and `ClassCastException`
- [x] Concept 3 — A generic class: type parameters and type arguments
- [x] Concept 4 — The diamond operator `<>`
- [x] Concept 5 — Raw types and "unchecked" warnings
- [x] Concept 6 — More than one type parameter, and the naming convention
- [x] Concept 7 — Reading generic types aloud
- [x] Concept 8 — Generic methods
- [x] Concept 9 — Generic interfaces, and filling in the blanks with `extends`

### Subtasks

- [ ] Subtask 1 — `Box<T>`: the compiler refuses a `String` in an integer box
- [ ] Subtask 2 — The before-times: an `Object` box and a `ClassCastException`
- [ ] Subtask 3 — A raw `List` and the "unchecked" warning
- [ ] Subtask 4 — Read five real Spring types aloud
- [ ] Subtask 5 — The naming convention: `T`, `E`, `K`, `V`, `R`
- [ ] Subtask 6 — Ask AI: a standard library method with two type parameters
- [ ] Subtask 7 — `Repository<T, ID>` and `SeatRepository`
- [ ] Checkpoint questions

## What this task is about

Every file in Zenith is full of angle brackets: `List<Seat>`, `Map<String, List<Seat>>`,
`Repository<T, ID>`. These are **generics**. You've *used* them since Task F2.4 (`List<String>`);
this task shows what problem they solve and how to write your own.

The one-sentence version: **generics let the compiler catch "wrong type" mistakes while you're
building the program, instead of the program crashing in front of a user.** To understand why that
matters, we start with the two moments an error can appear.

## Where things live

- **This file:** the reference notes for this task.
- **`../NOTES.md`:** your own notes, in your own words.
- **Your code:** type the examples into `.java` files in this folder.

Each example is one complete file. The file name must match the `public class` name inside it. To
run one:

```bash
javac RuntimeErrorDemo.java
java RuntimeErrorDemo
```

---

## Concept 1 — Compile time versus runtime: two moments an error can appear

Running a Java program is always **two separate steps**, and an error can show up in either one.

```text
   You write            Step 1: COMPILE                 Step 2: RUN
   Hello.java   ──►   javac Hello.java   ──►  Hello.class  ──►  java Hello
                      (checks your code,                  (actually does what
                       builds the program)                 the code says)

                      Errors here are                     Errors here are
                      COMPILE-TIME errors                 RUNTIME errors
```

| | Compile time | Runtime |
| --- | --- | --- |
| When | When you run `javac`, before the program starts | When you run `java`, while the program is working |
| Who finds it | The compiler (`javac`), by reading your code | Nobody, until that exact line actually runs |
| Who sees it | You, the programmer, at your desk | Possibly a real user |
| What you get | An `error:` message, and **no program at all** | An `Exception in thread "main" ...`, and the program stops |

### Step 1: A compile-time error

```java
public class CompileErrorDemo {

    public static void main(String[] args) {
        System.out.println("Program started");

        String seat = 42;

        System.out.println(seat);
    }
}
```

```bash
javac CompileErrorDemo.java
```

```text
CompileErrorDemo.java:6: error: incompatible types: int cannot be converted to String
        String seat = 42;
                      ^
1 error
```

- `String seat = 42;` tries to put a number into a variable that's meant for text.
- `javac` reads the code, sees the mismatch, and **refuses to build the program**. No
  `CompileErrorDemo.class` file is created.
- So there's nothing to run. Even the first line, `"Program started"`, never prints.

The compiler could catch this just by **reading** the code, because the types are written right
there: `String` on the left, a number on the right. That's what a compile-time error is: a mistake
visible from the code alone.

### Step 2: A runtime error

```java
public class RuntimeErrorDemo {

    public static void main(String[] args) {
        System.out.println("Program started");

        int tickets = 10;
        int people = 0;

        int ticketsEach = tickets / people;

        System.out.println(ticketsEach);
    }
}
```

```bash
javac RuntimeErrorDemo.java
java RuntimeErrorDemo
```

`javac` gives **no errors**. The program is built. Then `java` runs it:

```text
Program started
Exception in thread "main" java.lang.ArithmeticException: / by zero
	at RuntimeErrorDemo.main(RuntimeErrorDemo.java:9)
```

- Every line is valid Java: dividing an `int` by an `int` is allowed, so the compiler is happy.
- The problem is the **value**: `people` is `0`, and you can't divide by zero. That only becomes a
  problem when the line actually runs.
- `"Program started"` printed, so the program really was running when it crashed.
- An **exception** is Java's way of saying "something went wrong while running, I'm stopping".
  `ArithmeticException` is the kind of problem. `RuntimeErrorDemo.java:9` is the line.

### Step 3: Why runtime errors are the dangerous ones

A runtime error only happens **when that line runs with bad data**. Most of the time it doesn't:

```java
public class HiddenRuntimeError {

    static int ticketsEach(int tickets, int people) {
        return tickets / people;
    }

    public static void main(String[] args) {
        System.out.println(ticketsEach(10, 2));
        System.out.println(ticketsEach(12, 3));
        System.out.println(ticketsEach(9, 0));
        System.out.println("Done");
    }
}
```

```bash
javac HiddenRuntimeError.java
java HiddenRuntimeError
```

Output:

```text
5
4
Exception in thread "main" java.lang.ArithmeticException: / by zero
	at HiddenRuntimeError.ticketsEach(HiddenRuntimeError.java:4)
	at HiddenRuntimeError.main(HiddenRuntimeError.java:10)
```

- The first two calls worked. If you only tested those, you'd think the code was fine.
- The third call, with `0`, crashed. `"Done"` never printed.

Imagine this in Zenith. It works in every test, ships to production, and weeks later one customer
does the unusual thing, and their page crashes. That's what makes runtime errors dangerous: **the
code looks fine until the exact bad case happens, and the person who finds it is a user.**

### Step 4: The big idea of this whole task

| Error found at... | Who suffers | Cost to fix |
| --- | --- | --- |
| compile time | you, at your desk, in seconds | tiny: read the message, fix the line |
| runtime | a user, maybe weeks later | big: a bug report, investigation, a fix, a redeploy |

So programmers always want to **move errors earlier**, from runtime to compile time. The compiler
can only catch what it can see from the code, mostly **types**: is this a `String`? an `int`? a
`Seat`?

Generics exist to give the compiler more type information, so that a whole family of mistakes,
"I put the wrong kind of thing in here", becomes a **compile-time** error instead of a runtime crash.
The next concept shows what that family of mistakes looks like without generics.

### Summary

1. Java runs in two steps: **compile** (`javac`, checks and builds) and **run** (`java`, executes).
2. A **compile-time error** is found by reading the code. The program isn't built at all, so
   nothing runs.
3. A **runtime error** (an *exception*) happens while the program runs, only when the bad line
   meets bad data. It can hide through all your tests.
4. Compile-time errors are cheap and found by you. Runtime errors are expensive and often found by
   users.
5. Generics move "wrong type" mistakes from runtime to compile time. That's their entire value.

---

## Concept 2 — `Object`, casts and `ClassCastException`

Before generics existed (before Java 5, in 2004), the only way to write code that worked with "any
kind of thing" was to use the type `Object`. This concept shows how that works, and the runtime
error it leads to.

### Step 1: `Object` can hold anything

In Java, every class automatically extends a class called `Object`. `String` is an `Object`.
`Integer` is an `Object`. Your own `Seat` class is an `Object`. So a variable of type `Object` can
hold **anything**:

```java
public class ObjectHoldsAnything {

    public static void main(String[] args) {
        Object a = "A1";
        Object b = 42;
        Object c = 9.99;

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);

        System.out.println(a.getClass());
        System.out.println(b.getClass());
        System.out.println(c.getClass());
    }
}
```

Output:

```text
A1
42
9.99
class java.lang.String
class java.lang.Integer
class java.lang.Double
```

- Text, a whole number and a decimal number all went into `Object` variables without complaint.
- `getClass()` asks "what is this object **really**?" while the program runs. The real objects are
  still a `String`, an `Integer` and a `Double`.

A side note you'll need later: `42` on its own is an `int`, a **primitive** (a plain value, not an
object). Only objects fit in an `Object` variable, so Java quietly wraps the `42` in an `Integer`
object for you. This is called **autoboxing**. Every primitive has an object version:

| Primitive (plain value) | Object version (wrapper) |
| --- | --- |
| `int` | `Integer` |
| `double` | `Double` |
| `boolean` | `Boolean` |
| `char` | `Character` |
| `long` | `Long` |

### Step 2: But the compiler forgets what it is

Here's the catch. Try to use the `String` inside an `Object` variable:

```java
public class ObjectForgets {

    public static void main(String[] args) {
        Object seat = "A1";

        System.out.println(seat.length());
    }
}
```

```text
ObjectForgets.java:6: error: cannot find symbol
        System.out.println(seat.length());
                               ^
  symbol:   method length()
  location: variable seat of type Object
```

But it *is* a `String`. Why can't we call `.length()`?

Because the compiler doesn't look at what's **in** the variable. It only looks at the **declared
type**, the word on the left: `Object seat`. As far as the compiler knows, `seat` could be anything,
a number or a date, and those don't have `.length()`. So it refuses.

```text
                  what the compiler sees     what's really there while running
Object seat  =    "something, an Object"     "A1", a String
```

The same goes for assigning it back to a `String` variable:

```java
public class NoCast {

    public static void main(String[] args) {
        Object seat = "A1";

        String text = seat;
    }
}
```

```text
NoCast.java:6: error: incompatible types: Object cannot be converted to String
        String text = seat;
                      ^
```

"An `Object` might not be a `String`, so I won't let you put it in a `String` variable."

Putting something into `Object` is easy. Getting it back out **as what it really is** is the
problem.

### Step 3: A cast — "trust me, it's a `String`"

A **cast** is how you tell the compiler: "I know better than you. Treat this as a `String`." You
write the type in brackets in front of the value:

```java
public class CastDemo {

    public static void main(String[] args) {
        Object seat = "A1";

        String text = (String) seat;

        System.out.println(text.length());
        System.out.println(text.toLowerCase());
    }
}
```

Output:

```text
2
a1
```

- `(String) seat` means "treat `seat` as a `String`".
- Now `text` is declared as `String`, so the compiler lets you call `String` methods.

The important bit is that **the compiler doesn't check your promise**. It just believes you. The
check happens later, at runtime, when Java looks at the real object.

### Step 4: When the cast is a lie — `ClassCastException`

What if you promise the wrong type?

```java
public class BadCast {

    public static void main(String[] args) {
        System.out.println("Program started");

        Object seat = "A1";

        Integer number = (Integer) seat;

        System.out.println(number + 1);
    }
}
```

`javac` gives **no errors**. Then:

```text
Program started
Exception in thread "main" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer (java.lang.String and java.lang.Integer are in module java.base of loader 'bootstrap')
	at BadCast.main(BadCast.java:8)
```

Read the important part: **`String cannot be cast to class Integer`**.

1. At compile time: the compiler sees `Object` and thinks "an `Object` *could* be an `Integer`.
   OK, I'll trust you." So it builds the program.
2. At runtime: Java checks the real object, finds it's a `String`, and stops with a
   `ClassCastException`, meaning "your cast was a lie".

That's a **runtime** error, exactly the dangerous kind from Concept 1.

### Step 5: The compiler *can* catch it — if it knows the type

Compare with this, where the variable is declared as `String`, not `Object`:

```java
public class ImpossibleCast {

    public static void main(String[] args) {
        String seat = "A1";

        Integer number = (Integer) seat;
    }
}
```

```text
ImpossibleCast.java:6: error: incompatible types: String cannot be converted to Integer
        Integer number = (Integer) seat;
                                   ^
```

Now it's a **compile-time** error. The compiler knows `seat` is a `String`, and a `String` can never
be an `Integer`, so it refuses straight away.

| Variable declared as | `(Integer) seat` | Error found at |
| --- | --- | --- |
| `Object seat` | "could be an `Integer`, I'll trust you" | **runtime** (`ClassCastException`) |
| `String seat` | "can never be an `Integer`" | **compile time** |

**This is the whole story of generics in one table.** The compiler catches the mistake when it
knows the real type. `Object` throws that knowledge away. Generics (next concept) keep it.

### Step 6: Why this hides in real code

Like the divide-by-zero example, a bad cast only explodes when bad data reaches it:

```java
public class HiddenBadCast {

    static int seatCount(Object value) {
        Integer count = (Integer) value;
        return count;
    }

    public static void main(String[] args) {
        System.out.println(seatCount(10));
        System.out.println(seatCount(25));
        System.out.println(seatCount("30"));
        System.out.println("Done");
    }
}
```

Output:

```text
10
25
Exception in thread "main" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer ...
	at HiddenBadCast.seatCount(HiddenBadCast.java:4)
	at HiddenBadCast.main(HiddenBadCast.java:11)
```

- `seatCount` accepts `Object`, so the compiler lets you pass **anything**, even the text `"30"`.
- The first two calls work. The third passes text that *looks* like a number, and crashes.
- Notice where it crashed: **inside `seatCount`** (line 4), not where the wrong value was passed
  in (line 11). With `Object`, the error appears far from the actual mistake, which makes it harder
  to track down.

Before generics, every collection (`List`, `Map`, ...) worked exactly like this: it stored `Object`,
and you cast every item on the way out. Every cast was a little bet that could lose at runtime.

### Summary

1. Every class extends `Object`, so an `Object` variable can hold anything.
2. Primitives like `int` get wrapped into objects like `Integer` automatically (autoboxing).
3. The compiler only knows a variable's **declared** type. Once something is stored as `Object`,
   the compiler forgets what it really is.
4. A **cast** `(String) x` says "trust me, it's a `String`". The compiler doesn't check it; Java
   checks it at runtime.
5. A wrong cast compiles fine and crashes at runtime with a **`ClassCastException`**.
6. When the compiler knows the real type, it catches impossible casts at compile time. Generics
   are a way of keeping that knowledge.

---

## Concept 3 — A generic class: type parameters and type arguments

We want a simple class: a **locker** that holds one item. You `put` something in and `get` it back
out. The question is: what type is the item?

### Step 1: Option A — one class per type (lots of copying)

```java
class StringLocker {
    private String item;

    void put(String item) {
        this.item = item;
    }

    String get() {
        return item;
    }
}

class IntegerLocker {
    private Integer item;

    void put(Integer item) {
        this.item = item;
    }

    Integer get() {
        return item;
    }
}

public class SeparateLockers {

    public static void main(String[] args) {
        StringLocker names = new StringLocker();
        names.put("Ravi");
        System.out.println(names.get());

        IntegerLocker numbers = new IntegerLocker();
        numbers.put(42);
        System.out.println(numbers.get());
    }
}
```

Output:

```text
Ravi
42
```

This is **type-safe**: you can't put a number in `StringLocker`. But look at the two classes. They're
identical except for one word (`String` versus `Integer`). For a `Seat` locker you'd copy it again,
and again for `Order`, `Customer`... Any bug fix has to be made in every copy.

### Step 2: Option B — one class using `Object` (no safety)

You saw this in Concept 2. One class with `Object item` works for every type, but the compiler
forgets what's inside. You need a cast on every `get()`, and a wrong cast crashes at **runtime** with
a `ClassCastException`.

So we're stuck choosing:

| | One class for all types? | Compiler catches wrong types? |
| --- | --- | --- |
| Option A: one class per type | no, copy it for every type | yes |
| Option B: `Object` | yes | **no**, crashes at runtime |
| **Generics** | **yes** | **yes** |

Generics give you both.

### Step 3: Option C — a generic class

Write the class **once**, with a **blank** where the type goes:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

Read it slowly:

| Code | Meaning |
| --- | --- |
| `class Locker<T>` | "This class has a blank called `T`. Whoever uses `Locker` fills in the blank with a real type." |
| `private T item;` | "The item is of type T, whatever T turns out to be." |
| `void put(T item)` | "`put` accepts a T." |
| `T get()` | "`get` gives back a T." |

`T` is not a real class. It's a **placeholder**, like a blank on a form: "Name: ______". It's
written in angle brackets `< >` right after the class name.

That `T` is called a **type parameter**.

### Step 4: Using it — fill in the blank

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}

public class GenericLockerDemo {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker<String>();
        nameLocker.put("Ravi");
        String name = nameLocker.get();
        System.out.println(name);
        System.out.println(name.length());

        Locker<Integer> numberLocker = new Locker<Integer>();
        numberLocker.put(42);
        Integer number = numberLocker.get();
        System.out.println(number);
        System.out.println(number + 1);
    }
}
```

Output:

```text
Ravi
4
42
43
```

- `Locker<String>` means "a locker where T is `String`". Read it as "a locker of strings".
- `Locker<Integer>` means "a locker where T is `Integer`". Read it as "a locker of integers".
- **One class, two different uses.** No copying.
- **No casts.** `nameLocker.get()` gives back a `String` directly, so `name.length()` just works.
  Compare with Concept 2, where you had to write `(String)` every time.

The `String` you put inside the angle brackets is called a **type argument**.

#### A trick for reading it: replace T in your head

When you write `Locker<String>`, imagine Java crossing out every `T` and writing `String`:

```text
class Locker<T> {                     what Locker<String> means:
    private T item;          ──►          private String item;
    void put(T item)         ──►          void put(String item)
    T get()                  ──►          String get()
}
```

And for `Locker<Integer>`, every `T` becomes `Integer`. That's exactly how the compiler checks your
code.

(Behind the scenes Java doesn't really make two copies of the class. There's still only one
`Locker`. But the compiler checks your code *as if* the T's had been replaced, which is all that
matters for now.)

### Step 5: Type parameter versus type argument

These two names get mixed up constantly, so here they are side by side:

| | Type **parameter** | Type **argument** |
| --- | --- | --- |
| What it is | the **blank** | the **real type** you fill the blank with |
| Where you see it | where the class is **declared**: `class Locker<T>` | where the class is **used**: `Locker<String>` |
| Example | `T` | `String`, `Integer`, `Seat` |

It's the same idea as method parameters and arguments, which you already know:

```text
void greet(String name) { ... }      name    = parameter (the blank in the declaration)
greet("Ravi");                       "Ravi"  = argument  (the real value you pass in)

class Locker<T> { ... }              T       = type parameter (the blank)
Locker<String>                       String  = type argument  (the real type you pass in)
```

The only difference: a method parameter is a blank for a **value**, a type parameter is a blank for
a **type**.

### Step 6: The compiler now catches wrong types

Put a `String` into an integer locker:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}

public class WrongTypeIn {

    public static void main(String[] args) {
        Locker<Integer> numberLocker = new Locker<Integer>();
        numberLocker.put("Ravi");
    }
}
```

```text
WrongTypeIn.java:17: error: incompatible types: String cannot be converted to Integer
        numberLocker.put("Ravi");
                         ^
```

For a `Locker<Integer>`, `put` means `put(Integer item)`, and `"Ravi"` is not an `Integer`. The
program is **never built**.

It works on the way out too:

```java
public class WrongTypeOut {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker<String>();
        nameLocker.put("Ravi");
        Integer number = nameLocker.get();
    }
}
```

(Same `Locker` class at the top of the file.)

```text
WrongTypeOut.java:18: error: incompatible types: String cannot be converted to Integer
        Integer number = nameLocker.get();
                                       ^
```

For a `Locker<String>`, `get()` returns a `String`, which can't go into an `Integer` variable.

Compare this with Concept 2:

| | `Object` version | Generic version |
| --- | --- | --- |
| Put a `String` where an `Integer` belongs | allowed | **compile error** |
| Take it out as the wrong type | compiles, then `ClassCastException` at **runtime** | **compile error** |
| Casts needed | on every `get()` | **none** |

The mistake moved from runtime to compile time. That's the whole point of generics.

### Step 7: Type arguments must be objects, not primitives

```java
public class PrimitiveArg {

    public static void main(String[] args) {
        Locker<int> numberLocker = new Locker<int>();
    }
}
```

(Same `Locker` class at the top of the file.)

```text
PrimitiveArg.java:16: error: unexpected type
        Locker<int> numberLocker = new Locker<int>();
               ^
  required: reference
  found:    int
```

"Reference" means "an object type". The blank can only be filled with a class, not with a primitive
like `int`. So you use the wrapper from Concept 2: `Locker<Integer>`, not `Locker<int>`. You can
still call `put(42)` with a plain number, because Java wraps it into an `Integer` for you
(autoboxing).

### Step 8: It works with your own classes too

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}

class Seat {
    private final String id;

    Seat(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }
}

public class SeatLocker {

    public static void main(String[] args) {
        Locker<Seat> seatLocker = new Locker<Seat>();
        seatLocker.put(new Seat("A1"));

        Seat seat = seatLocker.get();
        System.out.println(seat.getId());
    }
}
```

Output:

```text
A1
```

The same `Locker` class, never changed, now holds a `Seat`. `get()` gives back a `Seat`, so
`seat.getId()` works with no cast.

### You've been using this all along

`ArrayList` is written exactly like `Locker`, just with more methods. Inside the Java library it's
declared roughly like this:

```java
public class ArrayList<E> {
    public boolean add(E e) { ... }
    public E get(int index) { ... }
}
```

So when you wrote `List<String>` in Task F2.4, you were filling in the blank `E` with `String`.
That's why `names.add(42)` didn't compile and `names.get(0)` gave back a `String` with no cast.

### Summary

1. A **generic class** is written once with a blank for the type: `class Locker<T>`.
2. The blank `T` is the **type parameter**. The real type you fill it with, `String` in
   `Locker<String>`, is the **type argument**.
3. Read `Locker<String>` as "a locker of strings": every `T` in the class becomes `String`.
4. One class works for every type, with no copying and no casts.
5. Putting in or taking out the wrong type is a **compile-time** error, not a runtime crash.
6. Type arguments must be classes: `Integer`, not `int`.

---

## Concept 4 — The diamond operator `<>`

A small concept, but you'll see it on almost every line of real code.

For this concept, `Locker` lives in its own file, `Locker.java`, so we don't have to repeat it at
the top of every example:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

When several `.java` files are in the same folder, they can all use each other's classes.

### Step 1: The type is written twice

So far we've written:

```java
public class WithoutDiamond {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker<String>();
        nameLocker.put("Ravi");
        System.out.println(nameLocker.get());
    }
}
```

Output:

```text
Ravi
```

Look at the first line: `String` appears **twice**, once on the left and once on the right.

```text
Locker<String> nameLocker = new Locker<String>();
       ^^^^^^                         ^^^^^^
       left side                      right side
```

The left side already says it's a `Locker<String>`, so writing `String` again on the right tells the
compiler nothing new.

### Step 2: The diamond — leave the right side empty

```java
public class WithDiamond {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker<>();
        nameLocker.put("Ravi");
        System.out.println(nameLocker.get());

        Locker<Integer> numberLocker = new Locker<>();
        numberLocker.put(42);
        System.out.println(numberLocker.get() + 1);
    }
}
```

Output:

```text
Ravi
43
```

`new Locker<>()`: the empty angle brackets `<>` are called the **diamond operator** (because `<>`
looks a bit like a diamond shape). It means:

> "Fill in this blank yourself, by looking at the left side."

The compiler sees `Locker<String>` on the left and works out that the right side must be
`new Locker<String>()`. Working something out like this is called **inferring** the type.

```text
Locker<String> nameLocker = new Locker<>();
       ──────                         ▲
          └─── compiler copies it ────┘
```

### Step 3: It's exactly as safe

The diamond is **only a shortcut for typing**. The object is still a `Locker<Integer>`, and the
compiler still checks everything:

```java
public class DiamondStillChecks {

    public static void main(String[] args) {
        Locker<Integer> numberLocker = new Locker<>();
        numberLocker.put("Ravi");
    }
}
```

```text
DiamondStillChecks.java:5: error: incompatible types: String cannot be converted to Integer
        numberLocker.put("Ravi");
                         ^
```

Same error as in Concept 3. Nothing was lost.

### Step 4: Where it really helps — long types

With nested types the saving is big:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LongTypes {

    public static void main(String[] args) {
        Map<String, List<String>> seatsByRow = new HashMap<>();

        List<String> rowA = new ArrayList<>();
        rowA.add("A1");
        rowA.add("A2");

        seatsByRow.put("A", rowA);

        System.out.println(seatsByRow);
    }
}
```

Output:

```text
{A=[A1, A2]}
```

Without the diamond, the first line would be:

```java
Map<String, List<String>> seatsByRow = new HashMap<String, List<String>>();
```

With it:

```java
Map<String, List<String>> seatsByRow = new HashMap<>();
```

(Don't worry about reading `Map<String, List<String>>` yet. That's Concept 7.)

Notice the diamond also works when the left and right are **different classes**: the left is the
interface `Map`, the right is the class `HashMap`. The compiler copies the type arguments
`<String, List<String>>` across.

### Step 5: Trap 1 — the diamond with `var`

`var` (Java 10+) means "work out the variable's type from the right side". The diamond means "work
out the type argument from the left side". Put them together and **each one is waiting for the
other**:

```java
public class VarWithDiamond {

    public static void main(String[] args) {
        var locker = new Locker<>();
        locker.put("Ravi");
        locker.put(42);

        String name = locker.get();
    }
}
```

```text
VarWithDiamond.java:8: error: incompatible types: Object cannot be converted to String
        String name = locker.get();
                                ^
```

There's no type written anywhere, so the compiler falls back to the most general type, `Object`.
`locker` became a `Locker<Object>`, which is why `put("Ravi")` *and* `put(42)` were both accepted.
That's exactly the "holds anything, forgets what it is" problem from Concept 2.

**Rule:** the type has to be written **somewhere**. Either on the left with the diamond on the right
(`Locker<String> locker = new Locker<>();`), or on the right with `var`
(`var locker = new Locker<String>();`). Never neither.

### Step 6: Trap 2 — forgetting the `<>` completely

The diamond is `<>`, with the brackets. Leave the brackets out and it means something different:

```java
public class ForgotDiamond {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker();
        nameLocker.put("Ravi");
        System.out.println(nameLocker.get());
    }
}
```

```bash
javac ForgotDiamond.java
java ForgotDiamond
```

Output:

```text
Note: ForgotDiamond.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
Ravi
```

It compiles and runs, but `javac` printed a **note** about "unchecked or unsafe operations". If you
ask for the details with `-Xlint:unchecked`:

```bash
javac -Xlint:unchecked ForgotDiamond.java
```

```text
ForgotDiamond.java:4: warning: [unchecked] unchecked conversion
        Locker<String> nameLocker = new Locker();
                                    ^
  required: Locker<String>
  found:    Locker
```

`new Locker()` with no angle brackets at all creates a **raw** `Locker`: a locker with the type
checking switched off. The compiler warns that it can't promise this is really a `Locker<String>`.
Raw types are a whole concept of their own. That's Concept 5.

| You write | Meaning |
| --- | --- |
| `new Locker<String>()` | a locker of strings |
| `new Locker<>()` | a locker of strings (the compiler copies `String` from the left) |
| `new Locker()` | a **raw** locker: type checking off, and a warning |

### Summary

1. The **diamond operator** `<>` means "compiler, copy the type argument from the left side".
2. `Locker<String> x = new Locker<>();` is exactly the same as `new Locker<String>()`, and exactly
   as safe. It just saves typing.
3. It saves the most on long types like `Map<String, List<String>>`.
4. Don't combine `var` with `<>`. With no type written anywhere, you get `Object`.
5. `<>` and no brackets at all are different: `new Locker()` is a **raw type**, with type checking
   off.

---

## Concept 5 — Raw types and "unchecked" warnings

A **raw type** is a generic class used with **no type argument at all**: `Locker` instead of
`Locker<String>`, or `List` instead of `List<String>`.

### Why raw types exist

Generics were added in Java 5 (2004). Millions of lines of older code already said `List list = new
ArrayList();`. If Java had made that an error, all of that code would have stopped compiling
overnight. So Java kept it legal, for backwards compatibility only, and made the compiler **warn**
you when you use it.

A raw type isn't "the simple version" or "an older style". It's a generic class with **the type
checking switched off**.

`Locker.java` is in its own file, the same as in Concept 4:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

### Step 1: A raw `Locker` accepts anything and forgets everything

```java
public class RawLockerDemo {

    public static void main(String[] args) {
        Locker locker = new Locker();
        locker.put("Ravi");
        locker.put(42);

        Object thing = locker.get();
        System.out.println(thing);

        String name = (String) locker.get();
        System.out.println(name);
    }
}
```

```bash
javac RawLockerDemo.java
java RawLockerDemo
```

Output:

```text
Note: RawLockerDemo.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
42
Exception in thread "main" java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String ...
	at RawLockerDemo.main(RawLockerDemo.java:11)
```

What happened:

1. `Locker locker` has no `<...>`, so it's raw. **There's no T any more**, and Java treats every T
   as `Object`.
2. `put("Ravi")` and then `put(42)` were both accepted. The `42` replaced `"Ravi"`.
3. `get()` returns `Object`, so we needed a cast `(String)` to use it as text.
4. The real item was `42`, an `Integer`, so the cast was a lie: **`ClassCastException` at
   runtime**.

This is exactly the `Object` world from Concept 2. The raw type threw away everything generics gave
us.

The compiler won't even let you skip the cast:

```java
public class RawNoCast {

    public static void main(String[] args) {
        Locker locker = new Locker();
        locker.put("Ravi");

        String name = locker.get();
    }
}
```

```text
RawNoCast.java:7: error: incompatible types: Object cannot be converted to String
        String name = locker.get();
                                ^
```

On a raw `Locker`, `get()` returns `Object`, the same as the old box.

### Step 2: Reading the warning

By default `javac` only prints a short note:

```text
Note: RawLockerDemo.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
```

Notice it's a **note**, not an error. The program still gets built. That's why people miss it. To
see the full warning, compile with `-Xlint:unchecked`:

```bash
javac -Xlint:unchecked RawLockerDemo.java
```

```text
RawLockerDemo.java:5: warning: [unchecked] unchecked call to put(T) as a member of the raw type Locker
        locker.put("Ravi");
                  ^
  where T is a type-variable:
    T extends Object declared in class Locker
RawLockerDemo.java:6: warning: [unchecked] unchecked call to put(T) as a member of the raw type Locker
        locker.put(42);
                  ^
  where T is a type-variable:
    T extends Object declared in class Locker
2 warnings
```

Word by word:

| Part | Meaning |
| --- | --- |
| `[unchecked]` | "I couldn't check this." |
| `unchecked call to put(T)` | "You called `put`, which expects a `T`..." |
| `as a member of the raw type Locker` | "...but you used `Locker` without saying what T is, so I have no idea what T should be." |
| `T extends Object` | "So I'm treating T as `Object`, which means anything goes." |

In plain English: **"I've stopped protecting you on this line."**

### Step 3: Why it's a *hole* — breaking a properly typed locker

So far, raw types just bring back the `Object` problem. It gets worse. A raw type can break
a locker that **was** properly typed:

```java
public class RawHole {

    static void addDefault(Locker locker) {
        locker.put("none");
    }

    public static void main(String[] args) {
        Locker<Integer> ticketCount = new Locker<>();
        ticketCount.put(5);

        addDefault(ticketCount);

        System.out.println("Still running...");

        Integer count = ticketCount.get();
        System.out.println(count + 1);
    }
}
```

```bash
javac -Xlint:unchecked RawHole.java
java RawHole
```

Output:

```text
RawHole.java:4: warning: [unchecked] unchecked call to put(T) as a member of the raw type Locker
        locker.put("none");
                  ^
  where T is a type-variable:
    T extends Object declared in class Locker
1 warning
Still running...
Exception in thread "main" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer ...
	at RawHole.main(RawHole.java:15)
```

Follow it step by step:

1. `ticketCount` is a proper `Locker<Integer>`. It holds `5`. All good.
2. We pass it to `addDefault`, whose parameter is a **raw** `Locker`. Java allows that, with only a
   warning.
3. Inside `addDefault` the locker is raw, so `put("none")` is allowed. **A `String` is now inside
   a `Locker<Integer>`.**
4. `"Still running..."` prints. Nothing has noticed.
5. Line 15, `Integer count = ticketCount.get();`, crashes with `ClassCastException`.

Look at line 15. **There's no cast written on it.** You did everything right in `main`: you used
`Locker<Integer>` and never wrote `(Integer)`. It still crashed, because a different method, maybe
in a different file written by someone else, used a raw type and slipped a `String` in.

(Where does the cast come from? When you write `Integer count = ticketCount.get();`, the compiler
quietly inserts a hidden cast to `Integer` for you. Normally that cast can never fail, because the
compiler checked every `put`. The raw type let one `put` through unchecked, so the hidden cast
failed.)

That's why it's called a **hole in the type system**: the protection only works if *every* piece
of code that touches the locker is checked. One raw type anywhere and the guarantee is gone, and the
crash happens far away from the real mistake.

### Step 4: Raw `Locker` versus `Locker<Object>`

"But I really do want a locker that holds anything." Then say so: `Locker<Object>`. That's
**not** the same as a raw `Locker`.

```java
public class ObjectLockerDemo {

    public static void main(String[] args) {
        Locker<Object> locker = new Locker<>();
        locker.put("Ravi");
        locker.put(42);

        Object thing = locker.get();
        System.out.println(thing);
    }
}
```

```bash
javac -Xlint:unchecked ObjectLockerDemo.java
java ObjectLockerDemo
```

Output:

```text
42
```

It accepts anything too, but there are **no warnings**. You told the compiler honestly, "T is
`Object`", and it checks that.

Now the important difference. Try the `RawHole` trick with `Locker<Object>` instead of raw
`Locker`:

```java
public class ObjectLockerProtects {

    static void addDefault(Locker<Object> locker) {
        locker.put("none");
    }

    public static void main(String[] args) {
        Locker<Integer> ticketCount = new Locker<>();
        ticketCount.put(5);

        addDefault(ticketCount);
    }
}
```

```text
ObjectLockerProtects.java:11: error: incompatible types: Locker<Integer> cannot be converted to Locker<Object>
        addDefault(ticketCount);
                   ^
```

**The compiler blocks it.** A `Locker<Integer>` is not allowed where a `Locker<Object>` is
expected, precisely because the method could put a `String` in it. The hole is closed. `Locker<Object>` is not a parent (supertype) of `Locker<Integer>` in Java. Even though Integer is a subclass of Object, the relationship does not automatically extend to generic types. One important distinction: `Locker<Integer>` and `Locker<Object>` are not parent and child classes. They are two different parameterizations of the same generic class. Remember: In Java, A extends B does not imply `Generic<A>` extends `Generic<B>`. This property is called invariance.

| | raw `Locker` | `Locker<Object>` |
| --- | --- | --- |
| What it says | "I'm not telling you what's inside" | "anything can be inside, and I mean it" |
| Holds anything? | yes | yes |
| Warnings | yes, `unchecked` | none |
| Can you pass a `Locker<Integer>` into it? | **yes**, silently | **no**, compile error |
| Can it sneak a `String` into a `Locker<Integer>`? | **yes**: the hole | **no** |

So a raw `List` is worse than `List<Object>`. Because in raw List the compiler is not allowed to check any types at all. Both hold anything, but `List<Object>` is still type
checked, and the raw `List` switches checking off and can corrupt other, properly typed lists.

### Step 5: Don't "fix" the warning by hiding it

You can silence the warning:

```java
@SuppressWarnings("unchecked")
```

That doesn't fix anything. The hole is still there; you've just stopped the compiler from telling
you about it. The warning is the compiler saying "the design is wrong here". The real fix is almost
always to **add the type argument**: change `Locker` to `Locker<Integer>` (or whatever type it
really holds).

### Summary

1. A **raw type** is a generic class used with no type argument: `Locker`, `List`.
2. It exists only so pre-2004 code still compiles. It's not a style to copy.
3. In a raw type, every T becomes `Object`, so you're back to casts and `ClassCastException`.
4. `javac` reports it as a quiet **note**. `-Xlint:unchecked` shows the full warning:
   "unchecked call ... as a member of the raw type". It means "I've stopped protecting you here."
5. It's a **hole**: a raw reference can put the wrong type into a properly typed object, and the
   crash happens later, on a line with no cast.
6. `Locker<Object>` is the honest version of "holds anything". It's still checked, and the compiler
   won't let a `Locker<Integer>` be passed into it.
7. Don't silence the warning with `@SuppressWarnings`. Add the missing type argument.

---

## Concept 6 — More than one type parameter, and the naming convention

`Locker<T>` had one blank. A class can have **as many blanks as it needs**, separated by commas.
You already know one with two: `Map<String, Integer>`.

### Step 1: A class with two blanks — `Pair<A, B>`

A pair holds two things, which may be of different types: a seat and its price, a first name and a
last name.

```java
class Pair<A, B> {
    private final A first;
    private final B second;

    Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    A getFirst() {
        return first;
    }

    B getSecond() {
        return second;
    }
}

public class PairDemo {

    public static void main(String[] args) {
        Pair<String, Integer> seatAndPrice = new Pair<>("A1", 50);

        String seat = seatAndPrice.getFirst();
        Integer price = seatAndPrice.getSecond();

        System.out.println(seat);
        System.out.println(price + 10);

        Pair<String, String> firstAndLast = new Pair<>("Ravi", "Kumar");
        System.out.println(firstAndLast.getFirst() + " " + firstAndLast.getSecond());

        Pair<Integer, Boolean> rowAndVip = new Pair<>(7, true);
        System.out.println("Row " + rowAndVip.getFirst() + ", VIP: " + rowAndVip.getSecond());
    }
}
```

Output:

```text
A1
60
Ravi Kumar
Row 7, VIP: true
```

- `class Pair<A, B>` has **two type parameters**, `A` and `B`, separated by a comma.
- `first` is an `A`, `second` is a `B`. They can be different types.
- The constructor takes an `A` and a `B`. Generic classes can have constructors just like normal
  classes.

### Step 2: The blanks are filled **by position**

`Pair<String, Integer>` fills the blanks **in order**: the first type argument goes into the first
type parameter, and so on.

```text
class Pair< A,       B       >
            │        │
Pair<    String,  Integer    >

So:  A = String     B = Integer
     getFirst()  returns String
     getSecond() returns Integer
```

Replacing in your head, like in Concept 3:

```text
private final A first;         ──►   private final String first;
private final B second;        ──►   private final Integer second;
Pair(A first, B second)        ──►   Pair(String first, Integer second)
A getFirst()                   ──►   String getFirst()
B getSecond()                  ──►   Integer getSecond()
```

The other uses in Step 1:

| Written | A is | B is |
| --- | --- | --- |
| `Pair<String, Integer>` | `String` | `Integer` |
| `Pair<String, String>` | `String` | `String` (both blanks can get the same type) |
| `Pair<Integer, Boolean>` | `Integer` | `Boolean` |

Each blank is filled **independently**. One class, endless combinations.

### Step 3: Mixing up the order is caught at compile time

Pass the values the wrong way round:

```java
public class PairWrongOrder {

    public static void main(String[] args) {
        Pair<String, Integer> seatAndPrice = new Pair<>(50, "A1");
    }
}
```

(Same `Pair` class at the top of the file.)

```text
PairWrongOrder.java:22: error: incompatible types: cannot infer type arguments for Pair<>
        Pair<String, Integer> seatAndPrice = new Pair<>(50, "A1");
                                                     ^
    reason: inference variable A has incompatible bounds
      equality constraints: String
      lower bounds: Integer
  where A is a type-variable:
    A extends Object declared in class Pair
```

This is the kind of generics error message that looks frightening. Decode it one line at a time:

| Line of the error | Plain English |
| --- | --- |
| `cannot infer type arguments for Pair<>` | "I tried to fill in the diamond `<>` and couldn't." |
| `inference variable A has incompatible bounds` | "I got two different answers for what `A` should be." |
| `equality constraints: String` | "The left side says A **must be** `String`." |
| `lower bounds: Integer` | "But the first value you passed, `50`, is an `Integer`." |
| `where A is a type-variable ... declared in class Pair` | "`A` is the first blank of `Pair`." |

So the whole message just says: **"The first value should be a `String`, but you gave an
`Integer`."** Most generics errors are this simple underneath. Look for the `required` / `found`
or `equality constraints` / `lower bounds` lines, which tell you what was expected and what you
gave.

Taking a value out as the wrong type is caught too:

```java
public class PairWrongGet {

    public static void main(String[] args) {
        Pair<String, Integer> seatAndPrice = new Pair<>("A1", 50);
        Integer price = seatAndPrice.getFirst();
    }
}
```

```text
PairWrongGet.java:23: error: incompatible types: String cannot be converted to Integer
        Integer price = seatAndPrice.getFirst();
                                             ^
```

`getFirst()` returns an `A`, and `A` is `String` here, so it can't go into an `Integer` variable.

### Step 4: The letters are just names

`T`, `A`, `B` aren't special keywords. A type parameter is just a **name**, like a variable name.
This compiles and works perfectly:

```java
class Pair<Banana, Apple> {
    private final Banana first;
    private final Apple second;

    Pair(Banana first, Apple second) {
        this.first = first;
        this.second = second;
    }

    Banana getFirst() {
        return first;
    }

    Apple getSecond() {
        return second;
    }
}

public class AnyNames {

    public static void main(String[] args) {
        Pair<String, Integer> seatAndPrice = new Pair<>("A1", 50);
        System.out.println(seatAndPrice.getFirst() + " costs " + seatAndPrice.getSecond());
    }
}
```

Output:

```text
A1 costs 50
```

`Banana` and `Apple` work exactly like `A` and `B`. Nobody writes this, though. Java programmers
follow a **convention** (an agreed habit), so that everyone can read everyone else's code quickly.

### Step 5: The naming convention

Type parameters are written as **one capital letter**, so you can tell them apart from real classes
at a glance (`T` is a blank; `Seat` is a real class). Each letter hints at what the blank is for:

| Letter | Stands for | Used when | Real example from Java |
| --- | --- | --- | --- |
| `T` | **T**ype | general "some type", the default choice | `Optional<T>`, our `Locker<T>` |
| `E` | **E**lement | the things stored **in a collection** | `List<E>`, `Set<E>` |
| `K` | **K**ey | the key in a key → value lookup | `Map<K, V>` |
| `V` | **V**alue | the value in a key → value lookup | `Map<K, V>` |
| `R` | **R**esult | what a function **returns** | `Function<T, R>` |
| `S`, `U` | 2nd, 3rd type | when you need more general types after `T` | `BiFunction<T, U, R>` |
| `ID` | **ID** | the type of an identifier | `Repository<T, ID>` (Spring) |

`ID` breaks the one-letter habit. Spring uses it because `I` alone would be unclear.

Here's the convention in use, a tiny version of one entry in a map:

```java
class Entry<K, V> {
    private final K key;
    private final V value;

    Entry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K getKey() {
        return key;
    }

    V getValue() {
        return value;
    }
}

public class EntryDemo {

    public static void main(String[] args) {
        Entry<String, Integer> stock = new Entry<>("A1", 3);

        System.out.println("Key: " + stock.getKey());
        System.out.println("Value: " + stock.getValue());
    }
}
```

Output:

```text
Key: A1
Value: 3
```

Exactly the same mechanics as `Pair<A, B>`, but the names `K` and `V` tell any reader immediately:
"this is a key and a value". When you see `Map<K, V>` in Java's documentation, you'll know `K` is
the key type and `V` the value type, before reading a word of explanation.

### How this helps you read real code

| You see | You immediately know |
| --- | --- |
| `interface List<E>` | E is the type of the elements in the list |
| `interface Map<K, V>` | K is the key type, V is the value type |
| `interface Function<T, R>` | it takes a T and gives back an R (result) |
| `interface Repository<T, ID>` | it stores T's, looked up by an ID |

### Summary

1. A generic class can have several type parameters: `class Pair<A, B>`.
2. Type arguments fill them **by position**: in `Pair<String, Integer>`, A = `String` and
   B = `Integer`.
3. Each blank is independent. They can be different types or the same type.
4. Mistakes (wrong order, wrong type out) are compile-time errors. Long generics errors usually just
   mean "expected X, found Y". Look for those two lines.
5. Type parameter names are just names. By **convention**: `T` type, `E` element, `K` key,
   `V` value, `R` result. They carry no magic, they just make code faster to read.

---

## Concept 7 — Reading generic types aloud

Types like `Map<String, List<Seat>>` look like noise at first. But there are only **two reading
rules**, and once you know them every generic type reads like a normal sentence.

### Rule 1: `X<Y>` reads as "an X of Y"

| Type | Read aloud |
| --- | --- |
| `List<Seat>` | "a list **of** seats" |
| `Set<String>` | "a set **of** strings" |
| `Locker<Integer>` | "a locker **of** integers" |
| `Optional<Seat>` | "an optional seat" (maybe a seat, maybe nothing) |

### Rule 2: `Map<K, V>` reads as "a map **from** K **to** V"

A map looks things up: you give it a key, it gives you back a value.

| Type | Read aloud | Meaning |
| --- | --- | --- |
| `Map<String, Integer>` | "a map from strings to integers" | give it text, get a number back |
| `Map<String, Seat>` | "a map from strings to seats" | give it a seat id like `"A1"`, get the `Seat` |
| `Map<Seat, Boolean>` | "a map from seats to booleans" | give it a seat, find out true/false (for example, "is it sold?") |

### Nested types: read from the outside in

When there are brackets inside brackets, read the **outer** type first, then go one layer inward
at a time, and keep applying the two rules.

`Map<String, List<Seat>>`:

```text
Map<String, List<Seat>>
│    │       │    │
│    │       │    └── "...of seats"
│    │       └─────── "...lists..."
│    └─────────────── "...from strings..."
└──────────────────── "a map..."

= "a map from strings to lists of seats"
```

Word order: `Map<String, ...>` gives "a map from strings to ...", then `List<Seat>` gives "lists of
seats".

### What does one entry hold?

A map is made of **entries**: one key paired with one value. To understand a map type, ask: "What's
the key? What's the value?"

For `Map<String, List<Seat>>`:

- **key** = a `String`, for example the row name `"A"`
- **value** = a `List<Seat>`, all the seats in that row

So **one entry** is "one row name, and the list of all seats in that row".

```text
   key (String)       value (List<Seat>)
   ────────────       ───────────────────────────
   "A"          ──►   [ Seat A1, Seat A2 ]
   "B"          ──►   [ Seat B1 ]
```

### Step 1: See it in real code, peeling one layer at a time

Here, `Seat` is a **record** (Task F2.8): a short way to declare a class that just holds data.
`record Seat(String id) { }` gives you a constructor `new Seat("A1")`, a getter `id()` and a
readable `toString()`.

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record Seat(String id) { }

public class ReadAloudDemo {

    public static void main(String[] args) {
        List<Seat> rowA = new ArrayList<>();
        rowA.add(new Seat("A1"));
        rowA.add(new Seat("A2"));

        List<Seat> rowB = new ArrayList<>();
        rowB.add(new Seat("B1"));

        Map<String, List<Seat>> seatsByRow = new HashMap<>();
        seatsByRow.put("A", rowA);
        seatsByRow.put("B", rowB);

        System.out.println("The whole map:");
        System.out.println(seatsByRow);

        List<Seat> seatsInA = seatsByRow.get("A");
        System.out.println("One value (a list of seats):");
        System.out.println(seatsInA);

        Seat firstSeat = seatsInA.get(0);
        System.out.println("One seat:");
        System.out.println(firstSeat);

        String id = firstSeat.id();
        System.out.println("Its id:");
        System.out.println(id);
    }
}
```

Output:

```text
The whole map:
{A=[Seat[id=A1], Seat[id=A2]], B=[Seat[id=B1]]}
One value (a list of seats):
[Seat[id=A1], Seat[id=A2]]
One seat:
Seat[id=A1]
Its id:
A1
```

Each `.get(...)` removes **one layer** of the type:

| Code | Type of the result | Read aloud |
| --- | --- | --- |
| `seatsByRow` | `Map<String, List<Seat>>` | a map from strings to lists of seats |
| `seatsByRow.get("A")` | `List<Seat>` | a list of seats |
| `seatsInA.get(0)` | `Seat` | a seat |
| `firstSeat.id()` | `String` | a string |

In the printed map, `{ }` is the map, `A=` is a key, and `[ ]` is a list value.

The compiler tracks every layer. Skip a layer, and it tells you:

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record Seat(String id) { }

public class WrongLayer {

    public static void main(String[] args) {
        Map<String, List<Seat>> seatsByRow = new HashMap<>();

        Seat seat = seatsByRow.get("A");
    }
}
```

```text
WrongLayer.java:12: error: incompatible types: List<Seat> cannot be converted to Seat
        Seat seat = seatsByRow.get("A");
                                  ^
```

"Getting from this map gives you a **list** of seats, not one seat." Reading the type aloud tells you
the same thing before you even compile.

### Step 2: A list of lists

`List<List<String>>` reads as "a list **of lists of** strings". Picture a grid: a list of rows, where
each row is a list of seat names.

```java
import java.util.List;

public class GridDemo {

    public static void main(String[] args) {
        List<List<String>> grid = List.of(
            List.of("A1", "A2", "A3"),
            List.of("B1", "B2")
        );

        System.out.println(grid);

        List<String> secondRow = grid.get(1);
        System.out.println(secondRow);

        String seat = secondRow.get(0);
        System.out.println(seat);

        System.out.println(grid.get(0).get(2));
    }
}
```

Output:

```text
[[A1, A2, A3], [B1, B2]]
[B1, B2]
B1
A3
```

- `List.of(...)` is a quick way to build a small list that can't be changed afterwards.
- `grid.get(1)` gives a `List<String>`: the second row.
- `secondRow.get(0)` gives a `String`: the first seat in that row.
- `grid.get(0).get(2)` does both in one line: row 0, seat 2, which is `A3`.

### Step 3: A map of maps

`Map<String, Map<String, Integer>>` reads as "a map from strings to **maps from strings to
integers**". For example: venue name, then tier name, then price.

```java
import java.util.HashMap;
import java.util.Map;

public class MapOfMapsDemo {

    public static void main(String[] args) {
        Map<String, Integer> wembleyPrices = new HashMap<>();
        wembleyPrices.put("GENERAL", 50);
        wembleyPrices.put("VIP", 150);

        Map<String, Map<String, Integer>> pricesByVenue = new HashMap<>();
        pricesByVenue.put("WEMBLEY", wembleyPrices);

        System.out.println(pricesByVenue);

        Map<String, Integer> prices = pricesByVenue.get("WEMBLEY");
        Integer vipPrice = prices.get("VIP");
        System.out.println(vipPrice);
    }
}
```

Output:

```text
{WEMBLEY={GENERAL=50, VIP=150}}
150
```

One entry: key `"WEMBLEY"`, value **another whole map** `{GENERAL=50, VIP=150}`. Two `.get`s peel
the two layers: venue gives a price map, and tier gives the price.

### More practice — read these aloud

| Type | Read aloud | One item / entry holds |
| --- | --- | --- |
| `List<Seat>` | a list of seats | one seat |
| `Set<SeatId>` | a set of seat ids | one seat id |
| `Map<String, Seat>` | a map from strings to seats | a string key, and one seat |
| `Map<String, List<Seat>>` | a map from strings to lists of seats | a string key, and a whole list of seats |
| `List<List<String>>` | a list of lists of strings | one inner list of strings |
| `Map<Long, Set<String>>` | a map from longs to sets of strings | a number key, and a set of strings |
| `Optional<List<Seat>>` | an optional list of seats | maybe a list of seats, maybe nothing |
| `Pair<String, Integer>` | a pair of a string and an integer | a string and an integer |

### Summary

1. `X<Y>` reads as "an X of Y": `List<Seat>` is "a list of seats".
2. `Map<K, V>` reads as "a map from K to V": `Map<String, Seat>` is "a map from strings to seats".
3. For nested types, read from the outside in, one layer at a time.
4. For a map, ask "what's one entry?": one key of type K, paired with one value of type V. The value
   can itself be a whole list or map.
5. Each `.get(...)` peels off one layer, and the compiler knows the exact type at every layer.

---

## Concept 8 — Generic methods

So far the blank `T` belonged to a whole **class** (`class Locker<T>`). A single **method** can also
have its own blank, even inside a class that isn't generic at all. This is how most of Java's
helper methods (like `List.of` and `Map.of`) are written.

### Before we start: whose blank is it?

That first paragraph packs in three ideas. Let's unpack them one at a time before writing any real
generic methods.

#### Idea 1: A class blank is filled in once, for the whole object

Remember `Locker<T>`:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

The `<T>` sits right after the **class name**, so T belongs to the **whole class**. Every method
inside (`put`, `get`) shares that same T.

You fill the blank **once**, at the moment you create the object:

```java
Locker<String> nameLocker = new Locker<>();   // T = String, decided here, once
```

From then on, that locker is a locker of strings **for its whole life**. `put` only takes a
`String`, `get` only returns a `String`. You can't say "this time, put an `Integer`": that's the
compile error from Concept 3.

Think of it like a **labelled jar**. You write "SUGAR" on the label when you buy the jar, and from
then on everything that goes in and comes out is sugar.

#### Idea 2: A method blank is filled in fresh, on every call

Now think of a job that doesn't need any object to remember a type. For example: "print this thing
twice." There's nothing to store. You just want **one method** that works for whatever you hand it.

So put the blank on the **method** instead of the class:

```java
class Printer {                              // no <T> here: the class is NOT generic

    static <T> void printTwice(T item) {     // <T> here: this ONE method has its own blank
        System.out.println(item);
        System.out.println(item);
    }
}
```

Read the first line aloud: **"For any type T, `printTwice` takes a T."** The `<T>` just before the
return type (`void`) is where the method **declares** its own blank.

Now each call fills the blank **separately**:

```java
class Locker<T> {
    private T item;

    void put(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}

class Printer {

    static <T> void printTwice(T item) {
        System.out.println(item);
        System.out.println(item);
    }
}

public class ClassBlankVsMethodBlank {

    public static void main(String[] args) {
        Locker<String> nameLocker = new Locker<>();
        nameLocker.put("Ravi");
        System.out.println(nameLocker.get());

        Printer.printTwice("Ravi");
        Printer.printTwice(42);
        Printer.printTwice(true);
    }
}
```

Output:

```text
Ravi
Ravi
Ravi
42
42
true
true
```

| Call | You passed | So T is, **for this call only** |
| --- | --- | --- |
| `printTwice("Ravi")` | a `String` | `String` |
| `printTwice(42)` | an `Integer` | `Integer` |
| `printTwice(true)` | a `Boolean` | `Boolean` |

Three calls, three different T's, and no object was ever created. You never wrote the type either:
the compiler looks at what you passed in and works T out by itself (Step 4 covers this in detail).

Think of it like a **vending machine's coin slot** instead of a labelled jar. It doesn't remember
the last coin. Each time you use it, it looks at what you put in right then.

#### Idea 3: The class around the method doesn't need to be generic

Look at `Printer` again: `class Printer`, with **no** `<T>` after the name. The class has no blank
at all. Only `printTwice` does. The blank belongs to that one method, and nothing outside the method
can see or use it.

So there are two places a blank can live:

```text
class Locker<T>                    blank after the CLASS name       -> shared by the whole class
static <T> void printTwice(T item) blank before the RETURN TYPE     -> belongs to this method only
```

| | Generic class | Generic method |
| --- | --- | --- |
| Where `<T>` is written | after the class name: `class Locker<T>` | before the return type: `static <T> void printTwice(...)` |
| Who can use T | every method in the class | only that one method |
| When T is decided | once, when you write `new Locker<String>()` | fresh on every call, from what you pass in |
| Does the class need a `<T>`? | yes, that's the whole point | no |
| Everyday picture | a labelled jar | a coin slot |

#### Why `List.of` and `Map.of` have to be generic methods

`List` itself is a generic interface, `List<E>`. So why can't `List.of` just use that `E`?

Because `of` is a **static** method. A static method belongs to the class itself, not to any one
object. And the class's blank is only filled in when you have an object (`List<String>`). When you
call `List.of(...)` there **is no list yet**: `of` is the thing that makes it. So there's no `E` to
borrow.

Java actually refuses if a static method tries to use the class's blank:

```java
class Locker<T> {
    private T item;

    static T makeEmpty() {
        return null;
    }
}
```

```text
StaticCantUseClassBlank.java:4: error: non-static type variable T cannot be referenced from a static context
    static T makeEmpty() {
           ^
1 error
```

| Error part | Plain English |
| --- | --- |
| `non-static type variable T` | "T is the class's blank, and it belongs to each object." |
| `cannot be referenced from a static context` | "This method is static, so there's no object here, so there's no T." |

That's why `List.of` declares **its own** blank, just before its return type. Simplified, Java's
source looks like this:

```java
static <E> List<E> of(E e1, E e2)
```

"For any type E, `of` takes two E's and gives back a list of E." So:

```java
List<String> names = List.of("Ravi", "Asha");   // you passed Strings,  so E = String
List<Integer> prices = List.of(50, 150);        // you passed Integers, so E = Integer
```

Same method, different E on each call, exactly like `printTwice`. `Map.of` is the same idea with two
blanks: `static <K, V> Map<K, V> of(K k1, V v1)`. Step 7 comes back to these.

#### In one sentence

A generic **class** asks "what type is this **object** for?" once, when it's created. A generic
**method** asks "what type is this **call** for?" every time it's called, which is why a method can
be generic even when its class isn't.

Now let's see the problem generic methods solve, and build one properly.

### Step 1: The problem — one method per type

We want a helper that gives back the first item of a list. Without generics:

```java
import java.util.List;

record Seat(String id) { }

public class CopyPasteMethods {

    static String firstString(List<String> list) {
        return list.get(0);
    }

    static Seat firstSeat(List<Seat> list) {
        return list.get(0);
    }

    public static void main(String[] args) {
        List<String> names = List.of("Ravi", "Asha");
        List<Seat> seats = List.of(new Seat("A1"), new Seat("A2"));

        System.out.println(firstString(names));
        System.out.println(firstSeat(seats));
    }
}
```

Output:

```text
Ravi
Seat[id=A1]
```

The two methods are identical except for the type, the same copying problem as `StringLocker` and
`IntegerLocker` in Concept 3. The other option, returning `Object`, brings back casts and
`ClassCastException` (Concept 2).

### Step 2: A generic method

```java
import java.util.List;

record Seat(String id) { }

public class GenericMethodDemo {

    static <T> T firstOf(List<T> list) {
        return list.get(0);
    }

    public static void main(String[] args) {
        List<String> names = List.of("Ravi", "Asha");
        List<Seat> seats = List.of(new Seat("A1"), new Seat("A2"));
        List<Integer> prices = List.of(50, 150);

        String name = firstOf(names);
        Seat seat = firstOf(seats);
        Integer price = firstOf(prices);

        System.out.println(name.toUpperCase());
        System.out.println(seat.id());
        System.out.println(price + 1);
    }
}
```

Output:

```text
RAVI
A1
51
```

**One method, three types, no casts.** `name.toUpperCase()`, `seat.id()` and `price + 1` all work
directly, because the compiler knows exactly what came back each time.

### Step 3: Reading the method's first line

```java
static <T> T firstOf(List<T> list)
```

Take it piece by piece:

| Part | Meaning |
| --- | --- |
| `static` | a normal static method, nothing new |
| `<T>` | **"This method has its own blank called T."** This is where T is *declared*. |
| `T` (the second one) | the **return type**: "this method gives back a T" |
| `firstOf` | the method name |
| `List<T> list` | the parameter: "a list of T" |

Read the whole line aloud: **"For any type T, `firstOf` takes a list of T and gives back a T."**

The rule to remember: **for a generic method, the `<T>` goes just before the return type.** That's
how Java knows `T` is a new blank and not a class called `T`.

```text
class Locker<T>                      blank declared after the CLASS name, for the whole class
static <T> T firstOf(List<T> list)   blank declared before the RETURN TYPE, for this method only
```

Notice `GenericMethodDemo` itself is **not** generic (no `<T>` after the class name). Only the method
is.

### Step 4: The compiler fills in T for you

With a generic class you wrote the type argument yourself: `Locker<String>`. With a generic method
you usually don't. The compiler looks at **what you passed in** and works T out (it **infers** it, as
with the diamond in Concept 4):

| Call | You passed | So T is | So it returns |
| --- | --- | --- | --- |
| `firstOf(names)` | a `List<String>` | `String` | `String` |
| `firstOf(seats)` | a `List<Seat>` | `Seat` | `Seat` |
| `firstOf(prices)` | a `List<Integer>` | `Integer` | `Integer` |

You *can* write T explicitly if you want to. The syntax is unusual: the type goes in front of the
method name, after a dot:

```java
import java.util.List;

public class ExplicitTypeArg {

    static <T> T firstOf(List<T> list) {
        return list.get(0);
    }

    public static void main(String[] args) {
        List<String> names = List.of("Ravi", "Asha");

        String a = firstOf(names);
        String b = ExplicitTypeArg.<String>firstOf(names);

        System.out.println(a);
        System.out.println(b);
    }
}
```

Output:

```text
Ravi
Ravi
```

Both lines do exactly the same thing. You'll almost never need the explicit form, but now you'll
recognise it if you see it.

### Step 5: Still checked at compile time

```java
import java.util.List;

record Seat(String id) { }

public class GenericMethodWrong {

    static <T> T firstOf(List<T> list) {
        return list.get(0);
    }

    public static void main(String[] args) {
        List<String> names = List.of("Ravi", "Asha");

        Seat seat = firstOf(names);
    }
}
```

```text
GenericMethodWrong.java:14: error: incompatible types: inference variable T has incompatible bounds
        Seat seat = firstOf(names);
                           ^
    equality constraints: String
    upper bounds: Seat,Object
  where T is a type-variable:
    T extends Object declared in method <T>firstOf(List<T>)
```

Decode it the same way as in Concept 6:

| Error line | Plain English |
| --- | --- |
| `inference variable T has incompatible bounds` | "I got two different answers for T." |
| `equality constraints: String` | "You passed a list of strings, so T must be `String`." |
| `upper bounds: Seat,Object` | "But you want to store the result in a `Seat`, so T would need to be a `Seat`." |
| `declared in method <T>firstOf(List<T>)` | "This T is the method's own blank." |

"The first item of a list of strings is a string, not a seat." Caught before the program runs.

### Step 6: Two type parameters on a method

Just like classes, methods can have several blanks:

```java
import java.util.HashMap;
import java.util.Map;

public class TwoTypeParamsMethod {

    static <K, V> Map<K, V> singleEntry(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    public static void main(String[] args) {
        Map<String, Integer> stock = singleEntry("A1", 3);
        Map<Integer, Boolean> vipRows = singleEntry(7, true);

        System.out.println(stock);
        System.out.println(vipRows);

        Integer count = stock.get("A1");
        System.out.println(count + 1);
    }
}
```

Output:

```text
{A1=3}
{7=true}
4
```

Read `static <K, V> Map<K, V> singleEntry(K key, V value)` aloud: **"For any types K and V,
`singleEntry` takes a key of type K and a value of type V, and gives back a map from K to V."**

- `singleEntry("A1", 3)`: K = `String`, V = `Integer`, so it returns `Map<String, Integer>`.
- `singleEntry(7, true)`: K = `Integer`, V = `Boolean`, so it returns `Map<Integer, Boolean>`.

### Step 7: You already use generic methods from Java's library

```java
import java.util.List;
import java.util.Map;

public class JdkGenericMethods {

    public static void main(String[] args) {
        List<String> seats = List.of("A1", "A2", "A3");
        Map<String, Integer> prices = Map.of("VIP", 150);

        System.out.println(seats);
        System.out.println(prices);
        System.out.println(prices.get("VIP") + 10);
    }
}
```

Output:

```text
[A1, A2, A3]
{VIP=150}
160
```

In Java's own source code, these two methods are declared like this:

```java
static <E> List<E> of(E e1, E e2, E e3)
static <K, V> Map<K, V> of(K k1, V v1)
```

You can read them now:

- `List.of`: "For any type E, `of` takes three E's and gives back a list of E." You passed three
  strings, so E = `String` and you get a `List<String>`.
- `Map.of`: "For any types K and V, `of` takes a key K and a value V and gives back a map from K to
  V." You passed `"VIP"` and `150`, so you get a `Map<String, Integer>`.

That's why `prices.get("VIP") + 10` works with no cast: the compiler knows the value is an
`Integer`.

### Step 8: The real fix when the compiler fights you

The task warns about this: when the types don't line up, people often "fix" it with a cast or
`@SuppressWarnings`. That hides the problem and brings back runtime crashes (Concept 2's
`HiddenBadCast`, Concept 5's `RawHole`).

Almost always, the real fix is a **type parameter**: on the class (Concept 3) if the whole class
works with that type, or on the method (this concept) if only one method does. That way the compiler
can follow the type through, and no cast is needed.

### Summary

1. A **generic method** has its own type parameter, declared just **before the return type**:
   `static <T> T firstOf(List<T> list)`.
2. Read it as: "for any type T, `firstOf` takes a list of T and gives back a T."
3. The class doesn't need to be generic. The blank belongs to that one method.
4. The compiler **infers** T from the arguments you pass, so you rarely write it. (The explicit
   form is `ClassName.<String>firstOf(...)`.)
5. Methods can have several type parameters: `static <K, V> Map<K, V> singleEntry(K key, V value)`.
6. `List.of` and `Map.of` are generic methods. That's why they return correctly typed collections.
7. When types don't line up, add a type parameter instead of a cast.

---

## Concept 9 — Generic interfaces, and filling in the blanks with `extends`

Interfaces can have type parameters too, in exactly the same way as classes. This is what
`Repository<T, ID>` in Subtask 7 is, and what Spring's repositories are built on.

### Step 1: Reminder — a normal interface (Task F2.6)

```java
interface Greeter {
    String greet(String name);
}

class PoliteGreeter implements Greeter {
    public String greet(String name) {
        return "Good morning, " + name;
    }
}

public class PlainInterfaceDemo {

    public static void main(String[] args) {
        Greeter greeter = new PoliteGreeter();
        System.out.println(greeter.greet("Ravi"));
    }
}
```

Output:

```text
Good morning, Ravi
```

- An **interface** lists methods that a class promises to have, but doesn't write their code.
- `implements Greeter` means "`PoliteGreeter` promises to provide `greet`". It must write the code.
- The method is `public` in the class because interface methods are always public.

### Step 2: A generic interface

A **converter** turns one thing into another. What goes in and what comes out can be any types, so
both are blanks:

```java
interface Converter<T, R> {
    R convert(T input);
}
```

Read it aloud: **"A converter from T to R has a method `convert` that takes a T and gives back an
R."** (T = the type that goes in, R = the **r**esult, by the naming convention from Concept 6.)

It's declared just like a generic class: the blanks go in `< >` after the name.

### Step 3: Implementing it — the class fills in the blanks

```java
interface Converter<T, R> {
    R convert(T input);
}

record Seat(String id) { }

class SeatIdToSeat implements Converter<String, Seat> {
    public Seat convert(String input) {
        return new Seat(input);
    }
}

class TextLength implements Converter<String, Integer> {
    public Integer convert(String input) {
        return input.length();
    }
}

public class ConverterDemo {

    public static void main(String[] args) {
        Converter<String, Seat> toSeat = new SeatIdToSeat();
        Seat seat = toSeat.convert("A1");
        System.out.println(seat);
        System.out.println(seat.id());

        Converter<String, Integer> toLength = new TextLength();
        Integer length = toLength.convert("Ravi");
        System.out.println(length + 1);
    }
}
```

Output:

```text
Seat[id=A1]
A1
5
```

Look at `class SeatIdToSeat implements Converter<String, Seat>`. The class **fills in the blanks**
right there in the `implements` line: T = `String`, R = `Seat`. Then the "replace in your head" trick
from Concept 3 tells you exactly which method the class must write:

```text
In the interface:              R    convert(T      input)
With T = String, R = Seat:     Seat convert(String input)    ◄── what SeatIdToSeat must write
With T = String, R = Integer:  Integer convert(String input) ◄── what TextLength must write
```

**One interface, two implementations, each with its own types.** And the variables are declared with
the interface type: `Converter<String, Seat> toSeat = new SeatIdToSeat();`, read aloud as "a
converter from strings to seats".

### Step 4: The compiler checks the class wrote the right method

Suppose `TextLength` promises `Converter<String, Integer>` but writes the method with the wrong
parameter type:

```java
interface Converter<T, R> {
    R convert(T input);
}

class TextLength implements Converter<String, Integer> {
    public Integer convert(Integer input) {
        return input;
    }
}

public class WrongSignature {

    public static void main(String[] args) {
        System.out.println(new TextLength().convert(5));
    }
}
```

```text
WrongSignature.java:5: error: TextLength is not abstract and does not override abstract method convert(String) in Converter
class TextLength implements Converter<String, Integer> {
^
```

Read the error: **"does not override abstract method `convert(String)`"**. The compiler itself shows
the method with the blank already filled in: it's looking for `convert(String)`, because T =
`String`. The class wrote `convert(Integer)` instead, so the promise isn't kept.

("Is not abstract" just means "this is a normal class that you can create objects from, so it must
provide every method its interface asks for.")

### Step 5: And it checks the people using it

```java
interface Converter<T, R> {
    R convert(T input);
}

class TextLength implements Converter<String, Integer> {
    public Integer convert(String input) {
        return input.length();
    }
}

public class WrongUse {

    public static void main(String[] args) {
        Converter<String, Integer> toLength = new TextLength();
        toLength.convert(42);
    }
}
```

```text
WrongUse.java:15: error: incompatible types: int cannot be converted to String
        toLength.convert(42);
                         ^
```

A converter from strings to integers only accepts strings. Both sides are checked: the class that
**implements** the interface, and the code that **calls** it.

### Step 6: An interface that extends a generic interface and fills in the blanks

This is the step Subtask 7 is about. An interface can **extend** a generic interface and fill in its
blanks, without adding anything else:

```java
interface Converter<T, R> {
    R convert(T input);
}

record Seat(String id) { }

interface SeatConverter extends Converter<String, Seat> {
}

class SimpleSeatConverter implements SeatConverter {
    public Seat convert(String input) {
        return new Seat(input.toUpperCase());
    }
}

public class ExtendsFillsBlanks {

    public static void main(String[] args) {
        SeatConverter converter = new SimpleSeatConverter();
        Seat seat = converter.convert("b7");
        System.out.println(seat);
    }
}
```

Output:

```text
Seat[id=B7]
```

Look closely at this line:

```java
interface SeatConverter extends Converter<String, Seat> {
}
```

Its body is **empty**, yet it has a method. `SeatConverter` gets everything from `Converter`, with
T = `String` and R = `Seat` filled in. So, without writing a single method, `SeatConverter` now has:

```java
Seat convert(String input);
```

What that one line did to the method:

| | Method signature |
| --- | --- |
| In `Converter<T, R>` (generic) | `R convert(T input)` |
| In `SeatConverter` (blanks filled) | `Seat convert(String input)` |

And `SeatConverter` itself has **no** type parameters. Anyone using it doesn't see any `< >` at
all: `SeatConverter converter = ...`, and `converter.convert("b7")` returns a `Seat`. The compiler
still checks it:

```java
public class ExtendsWrong {

    public static void main(String[] args) {
        SeatConverter converter = new SimpleSeatConverter();
        Integer number = converter.convert("b7");
    }
}
```

(Same `Converter`, `Seat`, `SeatConverter` and `SimpleSeatConverter` at the top of the file.)

```text
ExtendsWrong.java:20: error: incompatible types: Seat cannot be converted to Integer
        Integer number = converter.convert("b7");
                                          ^
```

The compiler knows `SeatConverter`'s `convert` returns a `Seat`, even though `SeatConverter`'s body
is empty.

Why bother? It gives the specific version a **clear name**. `SeatConverter` says what it is, and is
much easier to read than `Converter<String, Seat>` everywhere. You write the general rule once
(`Converter<T, R>`), then create specific, named versions with one line each.

### Step 7: Or keep a blank open

A class doesn't have to fill in every blank. It can pass its own blank through:

```java
interface Converter<T, R> {
    R convert(T input);
}

class NoChange<T> implements Converter<T, T> {
    public T convert(T input) {
        return input;
    }
}

public class KeepBlankOpen {

    public static void main(String[] args) {
        Converter<String, String> same = new NoChange<>();
        System.out.println(same.convert("A1"));

        Converter<Integer, Integer> sameNumber = new NoChange<>();
        System.out.println(sameNumber.convert(42) + 1);
    }
}
```

Output:

```text
A1
43
```

`class NoChange<T> implements Converter<T, T>`: "`NoChange` has its own blank T, and it's a
converter from T to the **same** T." The blank is only filled when someone creates a
`NoChange<String>` or a `NoChange<Integer>`.

| How you write it | Blanks after this line |
| --- | --- |
| `class SeatIdToSeat implements Converter<String, Seat>` | none: fully filled in |
| `interface SeatConverter extends Converter<String, Seat>` | none: fully filled in |
| `class NoChange<T> implements Converter<T, T>` | one (T), filled in later by whoever uses it |

### You'll meet these generic interfaces everywhere

| Interface | Read aloud | Its main method |
| --- | --- | --- |
| `List<E>` | a list of E | `E get(int index)` |
| `Map<K, V>` | a map from K to V | `V get(Object key)` |
| `Comparable<T>` | something that can be compared to a T | `int compareTo(T other)` |
| `Function<T, R>` | a function from T to R (Java's built-in version of our `Converter`) | `R apply(T t)` |
| `JpaRepository<T, ID>` (Spring) | a repository of T's, looked up by ID | `Optional<T> findById(ID id)` and more |

In Phase 1 you'll write `interface SeatRepository extends JpaRepository<Seat, String> {}`, exactly
the pattern from Step 6.

### Summary

1. Interfaces can be generic, just like classes: `interface Converter<T, R> { R convert(T input); }`.
2. A class fills in the blanks in its `implements` line:
   `class TextLength implements Converter<String, Integer>`. It must then write the method with
   those types: `Integer convert(String input)`.
3. The compiler checks both sides: the class must write the right method ("does not override
   abstract method `convert(String)`"), and callers must pass and receive the right types.
4. An interface can `extend` a generic interface and fill in its blanks:
   `interface SeatConverter extends Converter<String, Seat> {}`. With an empty body, it still has
   `Seat convert(String input)`.
5. A class can also keep a blank open: `class NoChange<T> implements Converter<T, T>`.

---
---

# Task F3.2 — Bounded types, wildcards, and type erasure

Beginner-friendly study notes for Task F3.2, added to the same file as F3.1 because it builds
directly on it. Same approach: concepts first, one at a time, then the subtasks. Every code example
and output below was compiled and run on Java 21.

## F3.2 Progress

### Concepts

- [x] Concept 1 — "Is a": subtypes and supertypes (its doubts section covers `Comparable`,
  `compareTo` and `<T extends Comparable<T>>`)
- [x] Concept 2 — Bounds: `<T extends Number>`
- [x] Concept 3 — Why a `List<Dog>` is not a `List<Animal>`
- [x] Concept 4 — Wildcards: `? extends X` and `?`
- [ ] Concept 5 — `? super X`, and the memory aid "produce extends, consume super"
- [ ] Concept 6 — Type erasure: generics disappear when the program runs
- [ ] Concept 7 — What erasure stops you doing, and why Jackson needs `TypeReference`

Standalone wildcard guide (`? extends` and `? super`, from zero, with worked examples and practice
questions): `F3.2-bounded-types-wildcards-and-type-erasure/java-wildcards-extends-and-super-from-zero.md`

### Subtasks

- [ ] Subtask 1 — `firstTwo`: a generic method
- [ ] Subtask 2 — `largest` with a bound, then break it on purpose
- [ ] Subtask 3 — Try to add to a `List<? extends Number>`
- [ ] Subtask 4 — The memory aid: produce extends, consume super
- [ ] Subtask 5 — Meet erasure: `new T[10]` and `instanceof List<String>`
- [ ] Subtask 6 — Where this comes back: Jackson's `TypeReference`
- [ ] Checkpoint questions

## What F3.2 is about

In F3.1, a blank like `T` could be filled with **any** type. That's flexible, but it has a cost:
if T could be anything, the compiler won't let you do much with it. F3.2 is about three things you
see constantly in real library code:

1. **Bounds** (`<T extends Number>`): "T can be anything, *as long as* it's some kind of Number."
   That lets you call Number's methods on T.
2. **Wildcards** (`List<? extends Number>`, `List<? super Integer>`): a `?` meaning "some type I'm
   not naming". These look scary in Spring and Jackson signatures. The goal is to **recognise and
   read** them, not to master them.
3. **Type erasure**: generics exist only while compiling. When the program runs, a `List<String>`
   is just a `List`. This explains several "why won't Java let me do that?" moments.

You already know generic methods: a method with its own blank, written just before the return type,
like `static <T> T firstOf(List<T> list)`. Every idea in F3.2 is built on one older idea: **"is a"**,
meaning "a Dog *is an* Animal". So that's where we start.

---

## Concept 1 — "Is a": subtypes and supertypes

Before bounds and wildcards make sense, you need one idea to be completely solid: when one class
**extends** another, the child **is a** kind of the parent. This concept is a refresher on plain
Java (no generics yet), but every later concept leans on it.

### Step 1: A small family of classes

Three classes, each in its own file in the same folder:

`Animal.java`:

```java
class Animal {
    private final String name;

    Animal(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    void eat() {
        System.out.println(name + " is eating");
    }
}
```

`Dog.java`:

```java
class Dog extends Animal {

    Dog(String name) {
        super(name);
    }

    void bark() {
        System.out.println(name() + " says woof");
    }
}
```

`Cat.java`:

```java
class Cat extends Animal {

    Cat(String name) {
        super(name);
    }

    void meow() {
        System.out.println(name() + " says meow");
    }
}
```

`Dog extends Animal` means: **"a Dog is an Animal, plus some extra."** A `Dog` gets everything an
`Animal` has (`name()`, `eat()`) and adds its own `bark()`. A `Cat` is also an `Animal`, with its own
`meow()`.

Drawn as a family tree, with the general type on top:

```text
            Animal            (has: name(), eat())
           /      \
        Dog        Cat
  (adds bark())  (adds meow())
```

Two words you'll see in every error message and article from here on:

| Word | Meaning | Here |
| --- | --- | --- |
| **subtype** (or subclass) | the more specific type, lower in the tree | `Dog` and `Cat` are subtypes of `Animal` |
| **supertype** (or superclass) | the more general type, higher in the tree | `Animal` is the supertype of `Dog` and `Cat` |

A quick test for "is a": say it aloud. "A Dog is an Animal" — true. "An Animal is a Dog" — not
always (it might be a Cat). That one-way direction is the whole of this concept.

### Step 2: A Dog can go wherever an Animal is expected

Because every Dog **is an** Animal, Java lets you use a `Dog` anywhere an `Animal` is asked for:

```java
public class DogIsAnAnimal {

    static void feed(Animal animal) {
        animal.eat();
    }

    public static void main(String[] args) {
        Dog rex = new Dog("Rex");
        rex.bark();

        Animal pet = rex;
        pet.eat();

        feed(rex);
        feed(new Cat("Tom"));
    }
}
```

To run it (javac finds `Animal.java`, `Dog.java` and `Cat.java` in the same folder by itself):

```bash
javac DogIsAnAnimal.java
java DogIsAnAnimal
```

Output:

```text
Rex says woof
Rex is eating
Rex is eating
Tom is eating
```

Line by line:

- `Animal pet = rex;` — a `Dog` stored in an `Animal` variable. Allowed, because a Dog **is an**
  Animal.
- `feed(rex)` — `feed` asks for an `Animal`; we hand it a `Dog`. Allowed, same reason.
- `feed(new Cat("Tom"))` — a `Cat` is also an Animal. Allowed.

So **one** `feed` method works for every kind of animal, now and in the future. If you add a
`Parrot extends Animal` next year, `feed` already works for it. That's the main benefit of "is a".

Everyday version: a sign saying "**Animals** allowed in this park" lets your dog in. Your dog
doesn't need its own "Dogs allowed" sign.

### Step 3: The other direction is refused

```java
public class AnimalIsNotADog {

    public static void main(String[] args) {
        Animal pet = new Cat("Tom");
        Dog dog = pet;
    }
}
```

```text
AnimalIsNotADog.java:5: error: incompatible types: Animal cannot be converted to Dog
        Dog dog = pet;
                  ^
1 error
```

| Error part | Plain English |
| --- | --- |
| `incompatible types` | "The thing on the right doesn't fit the variable on the left." |
| `Animal cannot be converted to Dog` | "All I know is that `pet` is *some* Animal. It might not be a Dog." |

And here it really isn't one: it's a Cat. If Java allowed this, `dog.bark()` would ask a cat to
bark. So the compiler refuses: **a Dog is an Animal, but an Animal is not necessarily a Dog.**

(You *can* force it with a cast, `Dog dog = (Dog) pet;`, which tells the compiler "trust me, it's a
Dog". But that just moves the problem to runtime: it compiles, then crashes with a
`ClassCastException` when the program finds a Cat inside.)

| Direction | Allowed? | Why |
| --- | --- | --- |
| `Animal a = someDog;` (specific into general) | yes, always | every Dog is an Animal |
| `Dog d = someAnimal;` (general into specific) | no, compile error | that Animal might be a Cat |

### Step 4: Through an `Animal` variable, you only see `Animal`'s methods

This is the most important step in this concept. Bounds, coming up next, exist to solve exactly this
problem.

```java
public class OnlyAnimalMethods {

    public static void main(String[] args) {
        Animal pet = new Dog("Rex");
        pet.eat();
        pet.bark();
    }
}
```

```text
OnlyAnimalMethods.java:6: error: cannot find symbol
        pet.bark();
           ^
  symbol:   method bark()
  location: variable pet of type Animal
1 error
```

| Error part | Plain English |
| --- | --- |
| `cannot find symbol` | "I can't find what you're asking for." |
| `symbol: method bark()` | "You asked for a method called `bark()`." |
| `location: variable pet of type Animal` | "You asked on `pet`, and as far as I know `pet` is just an **Animal**. Animals don't have `bark()`." |

But the object really **is** a Dog! Why won't the compiler let us bark?

Because the compiler goes by the **type of the variable** (the label you wrote: `Animal`), not by
the object that happens to be inside it at runtime. The compiler checks your code *before* it runs
(at `javac` time, when no objects exist yet), so it can't look at what's actually inside. It only knows the label. And the label
`Animal` only promises `name()` and `eat()`.

```text
   variable's type (the label)        object inside (only known at runtime)
   ┌──────────┐                       ┌───────────────────────────┐
   │  Animal  │  ───────────────────► │  Dog: name, eat, bark     │
   └──────────┘                       └───────────────────────────┘
   compiler lets you call:            the object could do:
   name(), eat()                      name(), eat(), bark()
```

When the program runs, the object really is a Dog, and Java can tell you so:

```java
public class WhatsInside {

    public static void main(String[] args) {
        Animal pet = new Dog("Rex");
        System.out.println(pet.getClass().getSimpleName());
        System.out.println(pet instanceof Dog);
        System.out.println(pet instanceof Cat);
    }
}
```

Output:

```text
Dog
true
false
```

So there are two views of the same variable: the **compiler's view** (its type is `Animal`) and the
**runtime view** (the object is a `Dog`). The compiler decides what you're **allowed to call**, and
it only uses its own view.

**The rule: you can only call the methods that the variable's type promises.** Keep this rule in
mind. In a generic method, a parameter like `T item` has the type `T`, and the compiler knows
nothing about T. So by this same rule, you can call almost nothing useful on it. Bounds fix that.

### Step 5: Java's own family — `Number`

Java's library has the same kind of family for numbers. `Integer`, `Double` and `Long` are the
**wrapper classes**: object versions of `int`, `double` and `long`, the ones you write inside angle
brackets (`List<Integer>`, never `List<int>`). They all extend one supertype, **`Number`**:

```text
                   Number          (has: intValue(), longValue(), doubleValue(), ...)
              /      |      \
        Integer    Double    Long     (each adds its own extras, e.g. compareTo)
```

`Number` promises that **every** number can turn itself into an `int`, a `long` or a `double`:
`intValue()`, `longValue()`, `doubleValue()`. So one method can handle all of them:

```java
public class NumberFamily {

    static void show(Number n) {
        System.out.println(n + " as a double is " + n.doubleValue());
    }

    public static void main(String[] args) {
        Integer seatsLeft = 3;
        Double price = 149.5;
        Long bookingId = 9000000001L;

        show(seatsLeft);
        show(price);
        show(bookingId);
    }
}
```

Output:

```text
3 as a double is 3.0
149.5 as a double is 149.5
9000000001 as a double is 9.000000001E9
```

`show` asks for a `Number`. An `Integer` is a Number, a `Double` is a Number, a `Long` is a Number,
so all three are allowed, and `doubleValue()` works on each. (`9.000000001E9` is just how Java
prints a big `double`: 9.000000001 × 10⁹.)

The Step 4 rule applies here too. Through a `Number` variable you only get `Number`'s methods.
`Integer` has a `compareTo` method, but `Number` doesn't promise one:

```java
public class NumberOnlyNumberMethods {

    public static void main(String[] args) {
        Number n = 3;
        System.out.println(n.doubleValue());
        System.out.println(n.compareTo(5));
    }
}
```

```text
NumberOnlyNumberMethods.java:6: error: cannot find symbol
        System.out.println(n.compareTo(5));
                            ^
  symbol:   method compareTo(int)
  location: variable n of type Number
1 error
```

The same message as `pet.bark()`: "`n` is just a Number, and Number doesn't promise `compareTo`."
`doubleValue()` on the line above is fine, because Number does promise that. (This gap matters
later: if you want to compare numbers in a generic method, `Number` alone isn't enough.)

### Step 6: Interfaces make "is a" too

"Is a" isn't only about `extends` between classes. When a class **implements** an interface, it
also **is a** that interface. For example, Java's `String` class is declared like this (simplified):

```java
public final class String implements CharSequence, Comparable<String> { ... }
```

So a String **is a** `CharSequence` ("something made of characters"), and **is a**
`Comparable<String>` ("something that can be compared to another String"). And every class in Java,
yours included, is an `Object`. So one value can be stored under several different labels:

```java
public class InterfaceIsA {

    public static void main(String[] args) {
        Comparable<String> name = "Ravi";
        System.out.println(name.compareTo("Asha"));

        CharSequence text = "A1";
        System.out.println(text.length());

        Object anything = "Ravi";
        System.out.println(anything);
    }
}
```

Output:

```text
17
2
Ravi
```

All three lines store a String. Each label lets you call only that label's methods: `compareTo` on
the `Comparable<String>` label, `length()` on the `CharSequence` label. (`17` is a positive number,
meaning "Ravi" comes **after** "Asha" alphabetically. `compareTo` is explained properly in the doubts
section at the end of this concept.)

Putting both kinds of "is a" into one picture:

```text
                           Object                      (everything is an Object)
            /                 |                \
        Animal              Number              String ── is a ──► Comparable<String>
        /    \            /   |    \                   └── is a ──► CharSequence
      Dog    Cat    Integer Double Long
```

Here's a sneak preview of why this matters for generics. Soon you'll write `<T extends Number>`.
In generics, `extends` means **"is a"**, covering *both* "extends a class" and "implements an
interface". So `<T extends Comparable<T>>` uses `extends` even though `Comparable` is an interface.
(This surprised you, so it's explained fully in the doubts section below.)

### Step 7: Hold this question for later

Here's a question to keep in the back of your mind:

> A Dog **is an** Animal. So is a **`List<Dog>`** a **`List<Animal>`**?

It seems like it should be. Surprisingly, Java says **no**. Later in this task you'll see exactly
why, and what wildcards (`?`) do about it. That's the one genuinely confusing part of generics, and
it all rests on the "is a" idea from this concept.

### Summary

1. `class Dog extends Animal` means **a Dog is an Animal**. Dog is the **subtype**, Animal the
   **supertype**.
2. "Is a" only goes one way. A Dog can go wherever an Animal is expected (`Animal a = dog;`,
   `feed(dog)`). An Animal can't go where a Dog is expected (`Animal cannot be converted to Dog`).
3. The compiler only uses the **variable's type**, not the object inside, so **you can only call
   the methods that type promises**. `pet.bark()` fails on an `Animal` variable even when it holds
   a Dog (`cannot find symbol ... variable pet of type Animal`).
4. Java's `Integer`, `Double` and `Long` all extend **`Number`**, which promises `intValue()`,
   `longValue()` and `doubleValue()`. `Number` does **not** promise `compareTo`.
5. Implementing an interface is also "is a": a String is a `Comparable<String>`. Everything is an
   `Object`.
6. In generics, `extends` means "is a", for classes **and** interfaces.

### Your doubts

#### Doubt 1: "In `<T extends Comparable<T>>`, T is a class and it's extending an interface. I've never heard of that."

> *I know a class extending a class (`class Dog extends Animal`), a class implementing an interface
> (`class String implements Comparable<String>`), and an interface extending an interface. But here
> T is a class and it's extending from an interface. I did not hear this.*

Fair point: none of those three forms looks like this one. The answer comes in three parts: T is not
a class, this `extends` is not the same kind of `extends`, and generics only ever use the one word.

##### Part 1: T is not a class. It's a blank.

`T` is a **placeholder**, a blank on a form. It isn't a class, and nobody ever writes `class T`. The
real type gets filled in later, by whoever **uses** the method. And the real type doesn't have to be a
class: it can be a class (`String`), an enum, a record, or even an **interface**.

Here's a blank filled with an interface type:

```java
public class InterfaceAsT {

    static <T extends CharSequence> int lengthOf(T text) {
        return text.length();
    }

    public static void main(String[] args) {
        System.out.println(lengthOf("Ravi"));

        CharSequence label = "A1";
        System.out.println(InterfaceAsT.<CharSequence>lengthOf(label));
    }
}
```

Output:

```text
4
2
```

| Call | T is | What kind of type |
| --- | --- | --- |
| `lengthOf("Ravi")` | `String` | a class |
| `InterfaceAsT.<CharSequence>lengthOf(label)` | `CharSequence` | an **interface** |

(The second call writes T explicitly, `.<CharSequence>`, just to force T to be the interface.
Normally you'd let the compiler work T out.)

So it's never "a class extending an interface". It's "**a blank**, with a rule about what may fill
it".

##### Part 2: This `extends` doesn't create anything. It's a rule, a check.

The three forms you know all **declare something new**. The bound **declares nothing**. It only
**checks** whatever type someone tries to put into T.

| You write | What it does |
| --- | --- |
| `class Dog extends Animal { ... }` | **creates** a new class Dog that inherits from Animal |
| `class Seat implements Comparable<Seat> { ... }` | **creates** a new class Seat that promises a `compareTo` method |
| `interface SeatConverter extends Converter<String, Seat> {}` | **creates** a new interface that inherits from another |
| `<T extends Comparable<T>>` | creates **nothing**. It's a rule: "only a type that **is a** `Comparable<T>` may fill T" |

An everyday picture: a job advert that says "**Applicants must be licensed drivers.**" The advert
doesn't create any drivers, and it doesn't care whether your licence came from a car school or a
truck school. It just turns away anyone who isn't a licensed driver. `T extends Comparable<T>` is the
same: "T may be any type, **as long as it is a** `Comparable<T>`."

##### Part 3: Inside `< >`, Java only has the word `extends`

You might expect `<T implements Comparable<T>>` because `Comparable` is an interface. Java doesn't
allow it:

```java
public class ImplementsKeyword {

    static <T implements Comparable<T>> T larger(T a, T b) {
        return a;
    }
}
```

```text
ImplementsKeyword.java:3: error: > expected
    static <T implements Comparable<T>> T larger(T a, T b) {
             ^
ImplementsKeyword.java:3: error: illegal start of type
    static <T implements Comparable<T>> T larger(T a, T b) {
              ^
ImplementsKeyword.java:3: error: <identifier> expected
    static <T implements Comparable<T>> T larger(T a, T b) {
                                      ^
3 errors
```

`> expected` means: "after `<T` I expected either `>` or `extends`. I don't know the word
`implements` here." Inside the angle brackets, `extends` is the **only** word for "is a". It covers
both:

- "T is a subclass of this class" (`<T extends Number>`: Integer extends Number), **and**
- "T implements this interface" (`<T extends Comparable<T>>`: String implements Comparable).

Why one word? The rule only cares that **T is a** `Comparable<T>`. It doesn't care *how* T got there
(by extending a class or by implementing an interface), so Java uses one word for both.

##### What `compareTo` does

`Comparable<T>` is an interface with exactly one method:

```java
int compareTo(T other);
```

It answers "which of us comes first?" with a number:

| Result | Meaning |
| --- | --- |
| negative | `this` comes **before** `other` (it's smaller) |
| zero | they're equal |
| positive | `this` comes **after** `other` (it's bigger) |

```java
public class CompareToBasics {

    public static void main(String[] args) {
        Integer five = 5;
        System.out.println(five.compareTo(9));
        System.out.println(five.compareTo(5));
        System.out.println(five.compareTo(2));
    }
}
```

Output:

```text
-1
0
1
```

Only look at whether it's negative, zero or positive, not the exact number. (That's why
`"Ravi".compareTo("Asha")` gave `17` earlier: positive, so "Ravi" comes after "Asha".)

##### Reading `<T extends Comparable<T>>` aloud

**"T can be any type, as long as a T can be compared with another T."**

T appears twice, and each one matters:

```text
<T extends Comparable<T>>
 ▲                    ▲
 │                    └── ...can be compared with another T
 └── the blank: some type T, which...
```

To check whether a real type is allowed, **replace every T with it** and ask the "is a" question:

| Try T = | Becomes | Is that true? | Allowed? |
| --- | --- | --- | --- |
| `String` | "String is a `Comparable<String>`?" | yes, `String implements Comparable<String>` | yes |
| `Integer` | "Integer is a `Comparable<Integer>`?" | yes, `Integer implements Comparable<Integer>` | yes |
| `Seat` (plain class) | "Seat is a `Comparable<Seat>`?" | no, Seat implements nothing | **no**, compile error |

##### Seeing it work

```java
public class LargerDemo {

    static <T extends Comparable<T>> T larger(T a, T b) {
        if (a.compareTo(b) >= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        System.out.println(larger("Ravi", "Asha"));
        System.out.println(larger(50, 150));
        System.out.println(larger(9.5, 2.0));
    }
}
```

Output:

```text
Ravi
150
9.5
```

One method works for strings, integers and doubles, because each of them is a `Comparable` of
itself.

##### Seeing the rule refuse a type

A plain `Seat` class that implements nothing, in `Seat.java`:

```java
class Seat {
    private final int row;

    Seat(int row) {
        this.row = row;
    }

    int row() {
        return row;
    }

    @Override
    public String toString() {
        return "Seat in row " + row;
    }
}
```

```java
public class SeatNotComparable {

    static <T extends Comparable<T>> T larger(T a, T b) {
        if (a.compareTo(b) >= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        System.out.println(larger(new Seat(3), new Seat(7)));
    }
}
```

```text
SeatNotComparable.java:11: error: method larger in class SeatNotComparable cannot be applied to given types;
        System.out.println(larger(new Seat(3), new Seat(7)));
                           ^
  required: T,T
  found:    Seat,Seat
  reason: inference variable T has incompatible bounds
    upper bounds: Comparable<T>
    lower bounds: Seat
  where T is a type-variable:
    T extends Comparable<T> declared in method <T>larger(T,T)
1 error
```

| Error part | Plain English |
| --- | --- |
| `found: Seat,Seat` | "You gave me two Seats, so T would have to be Seat." |
| `upper bounds: Comparable<T>` | "But the rule says T must be a `Comparable<T>`." |
| `lower bounds: Seat` | "And T must be able to hold a Seat." |
| `incompatible bounds` | "Seat isn't a Comparable, so no T satisfies both. I refuse." |

##### Fixing it: make Seat a `Comparable<Seat>`

Change `Seat.java` so Seat **implements** the interface and writes `compareTo` (comparing by row):

```java
class Seat implements Comparable<Seat> {
    private final int row;

    Seat(int row) {
        this.row = row;
    }

    int row() {
        return row;
    }

    @Override
    public int compareTo(Seat other) {
        return this.row - other.row;
    }

    @Override
    public String toString() {
        return "Seat in row " + row;
    }
}
```

```java
public class SeatComparable {

    static <T extends Comparable<T>> T larger(T a, T b) {
        if (a.compareTo(b) >= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        System.out.println(larger(new Seat(3), new Seat(7)));
        System.out.println(larger("Ravi", "Asha"));
    }
}
```

Output:

```text
Seat in row 7
Ravi
```

Look at the two lines together:

- `class Seat implements Comparable<Seat>` is the form **you already knew**: a class implementing an
  interface. It **creates** the "is a".
- `<T extends Comparable<T>>` is the **new** form: it **checks** for that "is a". Now that Seat
  passes the check, `larger` accepts Seats.

##### Why the rule is needed at all

Remove the bound and keep the `compareTo` call:

```java
public class NoBound {

    static <T> T larger(T a, T b) {
        if (a.compareTo(b) >= 0) {
            return a;
        }
        return b;
    }
}
```

```text
NoBound.java:4: error: cannot find symbol
        if (a.compareTo(b) >= 0) {
             ^
  symbol:   method compareTo(T)
  location: variable a of type T
  where T is a type-variable:
    T extends Object declared in method <T>larger(T,T)
1 error
```

This is Step 4's rule again: **you can only call the methods the variable's type promises.** Without
a bound, the last line shows what the compiler assumes: `T extends Object`, meaning "T could be
anything at all". `Object` has no `compareTo`, so the call is refused. That's the same reason
`pet.bark()` failed on an `Animal` variable.

The bound **is** the promise. `<T extends Comparable<T>>` tells the compiler "every T has
`compareTo`", so the compiler lets you call it.

##### The answer in one breath

`T` is not a class. It's a blank. `T extends Comparable<T>` doesn't create anything. It's a
**rule** that only lets in types that **are** a `Comparable<T>`. Inside `< >`, Java uses `extends`
for "is a" whether the thing on the right is a class or an interface. It never uses `implements`
there. The rule is what lets you call `compareTo` on T.

---

## Concept 2 — Bounds: `<T extends Number>`

**What you'll be able to do after this:** write a generic method that calls methods on `T`, and say
exactly what a bound allows and what it refuses.

### Step 1: What the compiler knows about a plain `T`

Start from one rule of plain Java: **you can only call the methods that a variable's type
promises.** If a variable's type is `Animal`, you can only call `Animal`'s methods, even when the
object inside is a `Dog`.

Now apply that rule to a generic method. In `static <T> void describe(T item)`, the type of `item`
is `T`. The compiler doesn't know what T will be: it could be `String`, `Integer`, `Seat`, anything.
So it assumes the only thing that is true of **every** type: **it is an `Object`**.

That means on a plain T you can call only `Object`'s methods: `toString()`, `equals()`,
`hashCode()` and `getClass()`.

```java
public class ObjectMethodsOnly {

    static <T> void describe(T item) {
        System.out.println(item.toString());
        System.out.println(item.getClass().getSimpleName());
    }

    public static void main(String[] args) {
        describe("Ravi");
        describe(42);
    }
}
```

Output:

```text
Ravi
String
42
Integer
```

That works, because `toString()` and `getClass()` are `Object` methods. Now try something useful:
adding up a list of numbers.

```java
import java.util.List;

public class NoBoundSum {

    static <T> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }
}
```

```text
NoBoundSum.java:8: error: cannot find symbol
            total = total + x.doubleValue();
                             ^
  symbol:   method doubleValue()
  location: variable x of type T
  where T is a type-variable:
    T extends Object declared in method <T>sum(List<T>)
1 error
```

| Error part | Plain English |
| --- | --- |
| `symbol: method doubleValue()` | "You asked for a method called `doubleValue()`." |
| `location: variable x of type T` | "You asked on `x`, whose type is `T`." |
| `T extends Object` | "All I know about T is that it's an Object." |

`Object` has no `doubleValue()`, so the call is refused. The compiler is right to refuse it: if
someone called `sum` with a `List<String>`, there would be no `doubleValue()` to call.

### Step 2: The fix — add a bound

Add `extends Number` to the blank:

```java
import java.util.List;

public class BoundedSum {

    static <T extends Number> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> seatsSold = List.of(3, 5, 2);
        List<Double> prices = List.of(149.5, 50.0);
        List<Long> bookingIds = List.of(9000000001L, 1L);
        List<Number> mixed = List.of(1, 2.5);

        System.out.println(sum(seatsSold));
        System.out.println(sum(prices));
        System.out.println(sum(bookingIds));
        System.out.println(sum(mixed));
    }
}
```

Output:

```text
10.0
199.5
9.000000002E9
3.5
```

(`9.000000002E9` is how Java prints a large `double`: 9.000000002 × 10⁹, which is 9000000002.)

A bound has **exactly two effects**, one on each side of the method:

| Where | Effect |
| --- | --- |
| **Inside the method** | The compiler now knows every T is a `Number`, so you may call `Number`'s methods on T: `doubleValue()`, `intValue()`, `longValue()` and so on. `Object`'s methods still work too. |
| **At the call** | Only a type that **is a** `Number` may fill T. Anything else is a compile error. |

What T became in each call (the compiler works it out from the list you pass in):

| Call | You passed | T is |
| --- | --- | --- |
| `sum(seatsSold)` | `List<Integer>` | `Integer` |
| `sum(prices)` | `List<Double>` | `Double` |
| `sum(bookingIds)` | `List<Long>` | `Long` |
| `sum(mixed)` | `List<Number>` | `Number` itself |

Note the last row: `T extends Number` allows **`Number` itself**, not only its subtypes. In a bound,
`extends` means "is a", and a Number is a Number.

### Step 3: The bound refuses types that aren't Numbers

```java
import java.util.List;

public class BoundRefuses {

    static <T extends Number> double sum(List<T> xs) {
        double total = 0;
        for (T x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<String> seatIds = List.of("A1", "B2");
        System.out.println(sum(seatIds));
    }
}
```

```text
BoundRefuses.java:15: error: method sum in class BoundRefuses cannot be applied to given types;
        System.out.println(sum(seatIds));
                           ^
  required: List<T>
  found:    List<String>
  reason: inference variable T has incompatible bounds
    equality constraints: String
    upper bounds: Number
  where T is a type-variable:
    T extends Number declared in method <T>sum(List<T>)
1 error
```

| Error part | Plain English |
| --- | --- |
| `required: List<T>` / `found: List<String>` | "The method wants a list of T. You gave a list of String." |
| `equality constraints: String` | "So T must be **exactly** `String`." |
| `upper bounds: Number` | "But the bound says T must be `Number` or something below it." |
| `incompatible bounds` | "String is not a Number. No T satisfies both rules, so I refuse." |

**Why it's called an "upper" bound.** Draw the family tree with the most general type at the top.
The bound is a **ceiling**: T may be `Number` or anything **below** it, nothing above it and
nothing beside it.

```text
                Object
              /        \
         Number          String     ✗ not under the ceiling
     ── ceiling ──
      /    |    \
  Integer Double Long               ✓ all allowed (and Number itself)
```

### Step 4: Why not just write `Number` instead of `T`?

`Number` already has `doubleValue()`, so why bother with T? There are two concrete reasons.

#### Reason 1: A bound keeps the exact type for the caller

A method that returns the bigger of two numbers, written with plain `Number`:

```java
public class LosesExactType {

    static Number bigger(Number a, Number b) {
        if (a.doubleValue() >= b.doubleValue()) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        Integer seats = bigger(3, 7);
        System.out.println(seats + 1);
    }
}
```

```text
LosesExactType.java:11: error: incompatible types: Number cannot be converted to Integer
        Integer seats = bigger(3, 7);
                              ^
1 error
```

You passed in two `Integer`s, but the return type says `Number`, so a `Number` is all the caller
gets back. The compiler won't let a `Number` go into an `Integer` variable, because some Numbers are
Doubles. The exact type was lost.

The same method with a bound:

```java
public class KeepsExactType {

    static <T extends Number> T bigger(T a, T b) {
        if (a.doubleValue() >= b.doubleValue()) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        Integer seats = bigger(3, 7);
        System.out.println(seats + 1);

        Double price = bigger(149.5, 50.0);
        System.out.println(price / 2);
    }
}
```

Output:

```text
8
74.75
```

The return type is `T`, and T is the same type that came in. Two `Integer`s in, an `Integer` out.
Two `Double`s in, a `Double` out. No cast needed.

#### Reason 2: `List<Number>` does not accept a `List<Integer>`

```java
import java.util.List;

public class NumberListParam {

    static double sumNumbers(List<Number> xs) {
        double total = 0;
        for (Number x : xs) {
            total = total + x.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> seatsSold = List.of(3, 5, 2);
        System.out.println(sumNumbers(seatsSold));
    }
}
```

```text
NumberListParam.java:15: error: incompatible types: List<Integer> cannot be converted to List<Number>
        System.out.println(sumNumbers(seatsSold));
                                      ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

This surprises everyone: an `Integer` **is a** `Number`, yet a `List<Integer>` is **not** accepted
as a `List<Number>`. It's a deliberate Java rule, explained fully in the next concept. For now,
remember the fact:

- A parameter of type `List<Number>` accepts **only** a `List<Number>`.
- A parameter of type `List<T>`, with `<T extends Number>`, accepts a `List<Integer>`, a
  `List<Double>`, a `List<Long>` and a `List<Number>`.

#### So when do you need a bound?

- **Plain `Number` is enough** when the type appears once and nothing depends on it.
  `static double half(Number n) { return n.doubleValue() / 2; }` needs no generics.
- **Use `<T extends Number>`** when the type has to **connect** two places: the parameter and the
  return type (Reason 1), or the type inside another generic type like `List<T>` (Reason 2).

### Step 5: Bounds on a generic class

A class's blank can have a bound too, written where the blank is declared. `PriceTag.java`:

```java
class PriceTag<T extends Number> {
    private final T amount;

    PriceTag(T amount) {
        this.amount = amount;
    }

    T amount() {
        return amount;
    }

    double withTax() {
        return amount.doubleValue() * 1.18;
    }
}
```

```java
public class PriceTagDemo {

    public static void main(String[] args) {
        PriceTag<Integer> ticket = new PriceTag<>(100);
        PriceTag<Double> snack = new PriceTag<>(50.0);

        System.out.println(ticket.withTax());
        System.out.println(snack.withTax());

        Integer base = ticket.amount();
        System.out.println(base + 1);
    }
}
```

Output:

```text
118.0
59.0
101
```

Same two effects as before. Inside the class, `amount.doubleValue()` is allowed. Outside, only
Number types may fill T:

```java
public class PriceTagWrong {

    public static void main(String[] args) {
        PriceTag<String> label = new PriceTag<String>("free");
    }
}
```

```text
PriceTagWrong.java:4: error: type argument String is not within bounds of type-variable T
        PriceTag<String> label = new PriceTag<String>("free");
                 ^
  where T is a type-variable:
    T extends Number declared in class PriceTag
PriceTagWrong.java:4: error: type argument String is not within bounds of type-variable T
        PriceTag<String> label = new PriceTag<String>("free");
                                              ^
  where T is a type-variable:
    T extends Number declared in class PriceTag
2 errors
```

`type argument String is not within bounds of type-variable T` means: "You tried to fill T with
String, but T's bound is Number, and String isn't a Number." It's reported twice because `<String>`
is written in two places on that line.

### Step 6: Two bounds at once, with `&`

You'll sometimes see this in library code:

```java
public class TwoBounds {

    static <T extends Number & Comparable<T>> T larger(T a, T b) {
        if (a.compareTo(b) >= 0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        System.out.println(larger(3, 7));
        System.out.println(larger(149.5, 50.0));
    }
}
```

Output:

```text
7
149.5
```

Read it as **"T must be a Number, AND T must be comparable with another T."** Both rules apply.
It's needed here because `Number` alone does not promise `compareTo`. `Integer` and `Double` pass
both checks.

One ordering rule: you may list **at most one class**, and it must come **first**. Interfaces come
after it.

```java
public class TwoBoundsWrongOrder {

    static <T extends Comparable<T> & Number> T larger(T a, T b) {
        return a;
    }
}
```

```text
TwoBoundsWrongOrder.java:3: error: interface expected here
    static <T extends Comparable<T> & Number> T larger(T a, T b) {
                                      ^
1 error
```

"After `&` I expected an interface. `Number` is a class, so it must go first."

You don't need to write two-bound signatures yet. You only need to be able to read them.

### Summary

1. On a plain `T`, the compiler assumes only `T extends Object`, so you can call only `Object`'s
   methods (`toString`, `equals`, `hashCode`, `getClass`).
2. A bound like `<T extends Number>` has two effects. **Inside**, you may call Number's methods on
   T. **At the call**, only Number or its subtypes may fill T.
3. "Upper bound" means a ceiling: T may be the bound type itself, or anything below it.
4. Use a bound instead of plain `Number` when the type must connect two places: a return type of
   `T` keeps the caller's exact type, and `List<T>` accepts a `List<Integer>` where `List<Number>`
   refuses it.
5. Classes can have bounds too: `class PriceTag<T extends Number>`. A wrong type gives
   `not within bounds of type-variable T`.
6. `<T extends A & B>` means T must satisfy both. At most one class, and it must come first.

---

## Concept 3 — Why a `List<Dog>` is not a `List<Animal>`

**What you'll be able to do after this:** explain why `List<Animal> a = dogs;` doesn't compile,
using a concrete example of what would break if Java allowed it.

This is the one part of generics that genuinely confuses people, so we go in small steps.

The examples use three small classes, each in its own file in the same folder.

`Animal.java`:

```java
class Animal {
    private final String name;

    Animal(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    void eat() {
        System.out.println(name + " is eating");
    }
}
```

`Dog.java`:

```java
class Dog extends Animal {

    Dog(String name) {
        super(name);
    }

    void bark() {
        System.out.println(name() + " says woof");
    }
}
```

`Cat.java`:

```java
class Cat extends Animal {

    Cat(String name) {
        super(name);
    }

    void meow() {
        System.out.println(name() + " says meow");
    }
}
```

### Step 1: The natural guess fails

A single Dog fits into an Animal variable. So you'd expect a list of Dogs to fit into a "list of
Animals" variable:

```java
import java.util.ArrayList;
import java.util.List;

public class NaturalGuess {

    public static void main(String[] args) {
        Dog rex = new Dog("Rex");
        Animal pet = rex;

        List<Dog> dogs = new ArrayList<>();
        dogs.add(rex);
        List<Animal> animals = dogs;
    }
}
```

```text
NaturalGuess.java:12: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        List<Animal> animals = dogs;
                               ^
1 error
```

`Animal pet = rex;` compiles. `List<Animal> animals = dogs;` doesn't. A **Dog** is an **Animal**,
but a **list of Dogs** is not a **list of Animals**. To see why, we need two small facts first.

### Step 2: Fact 1 — assigning a list does not copy it

`List<String> b = a;` doesn't create a second list. It creates a **second label on the same
list**:

```java
import java.util.ArrayList;
import java.util.List;

public class TwoLabelsOneList {

    public static void main(String[] args) {
        List<String> kennel = new ArrayList<>();
        List<String> sameKennel = kennel;

        sameKennel.add("Rex");
        sameKennel.add("Bruno");

        System.out.println(kennel);
        System.out.println(kennel.size());
        System.out.println(kennel == sameKennel);
    }
}
```

Output:

```text
[Rex, Bruno]
2
true
```

```text
   kennel ─────┐
               ├──►  [ Rex, Bruno ]     ← only ONE list exists
   sameKennel ─┘
```

`kennel == sameKennel` is `true`: both labels point at the very same object. Whatever is added
through one label is seen through the other.

### Step 3: Fact 2 — a `List<Animal>` is allowed to hold Cats

That's what "list of Animals" means: **any** Animal may go in, Dog or Cat.

```java
import java.util.ArrayList;
import java.util.List;

public class AnimalListTakesCats {

    public static void main(String[] args) {
        List<Animal> animals = new ArrayList<>();
        animals.add(new Dog("Rex"));
        animals.add(new Cat("Tom"));

        for (Animal a : animals) {
            a.eat();
        }
    }
}
```

Output:

```text
Rex is eating
Tom is eating
```

So the type `List<Animal>` **promises**: "you may add any Animal to me, Cats included."

### Step 4: Put the two facts together

Suppose, for a moment, Java **did** allow `List<Animal> animals = dogs;`. Follow it line by line
(this code is imaginary: it doesn't compile, which is the point):

```java
List<Dog> dogs = new ArrayList<>();
dogs.add(new Dog("Rex"));

List<Animal> animals = dogs;     // imagine this were allowed

animals.add(new Cat("Tom"));     // allowed: a List<Animal> may hold Cats (Fact 2)

Dog d = dogs.get(1);             // dogs is a List<Dog>, so get() returns a Dog... right?
d.bark();                        // a Cat asked to bark!
```

```text
   dogs ─────┐
             ├──►  [ Rex (Dog), Tom (Cat) ]     ← the "Dogs only" list now holds a Cat
   animals ──┘
```

1. `animals = dogs` made a **second label** on the **same** list (Fact 1). Nothing was copied.
2. Through the `animals` label, adding a Cat is perfectly legal (Fact 2).
3. The `dogs` label still promises "everything in here is a Dog", but now there's a Cat inside.

**A promise was broken.** Java prevents it by refusing the very first step, `animals = dogs`.

**The reason in one sentence:** if a `List<Dog>` could be used as a `List<Animal>`, you could add a
Cat to it through the `List<Animal>` label, and the list of Dogs would secretly contain a Cat.

Everyday version: a kennel has a sign "Dogs only". If the town also listed that same kennel as an
"animal shelter", someone could drop a cat off through the shelter listing, and the kennel's
"Dogs only" promise would be broken without the kennel ever agreeing to it.

### Step 5: Proof 1 — arrays allow it, and crash while running

Java's **arrays** don't have this protection: a `Dog[]` **is** accepted as an `Animal[]`.

```java
public class ArraysAllowIt {

    public static void main(String[] args) {
        Dog[] dogs = new Dog[2];
        Animal[] animals = dogs;

        animals[0] = new Dog("Rex");
        System.out.println("Stored a Dog");

        animals[1] = new Cat("Tom");
        System.out.println("Stored a Cat");
    }
}
```

```text
Stored a Dog
Exception in thread "main" java.lang.ArrayStoreException: Cat
	at ArraysAllowIt.main(ArraysAllowIt.java:10)
```

It's the Step 4 problem exactly: a Cat going into a Dogs-only container. Arrays only catch it
**while the program is running**, with an `ArrayStoreException` ("you tried to store the wrong type
in this array"). They can do that because every array object remembers its element type at
runtime.

Generics were designed to catch it **earlier, at compile time**, before the program ever runs. A
list object does **not** remember its element type at runtime (you'll learn why later in this
task), so a runtime check like the array's isn't possible. Java refuses the assignment instead.

- `Animal[] a = dogArray;` compiles, and storing a Cat crashes **at runtime**.
- `List<Animal> a = dogList;` doesn't compile, so the Cat can never get in.

### Step 6: Proof 2 — force it anyway, and the list gets corrupted

You can trick the compiler with a **raw type**: `List` written with no angle brackets, which
switches type checking off. Now Step 4 really happens:

```java
import java.util.ArrayList;
import java.util.List;

public class ForcedWithRaw {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List raw = dogs;
        List<Animal> animals = raw;

        animals.add(new Cat("Tom"));
        System.out.println("Added a Cat. dogs now has " + dogs.size() + " items");

        for (Dog d : dogs) {
            d.bark();
        }
    }
}
```

```text
Note: ForcedWithRaw.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
Added a Cat. dogs now has 2 items
Rex says woof
Exception in thread "main" java.lang.ClassCastException: class Cat cannot be cast to class Dog (Cat and Dog are in unnamed module of loader 'app')
	at ForcedWithRaw.main(ForcedWithRaw.java:16)
```

1. The compiler only gave a **warning** ("unchecked or unsafe operations"), because we switched
   checking off.
2. The Cat went into the list of Dogs: `dogs.size()` is 2.
3. The loop treats every item as a `Dog`. Rex barks. Then it reaches Tom, a Cat, and crashes with
   `ClassCastException`: "a Cat cannot be treated as a Dog."

That's the Step 4 disaster made real. The compile error in Step 1 exists to stop it.

### Step 7: Methods refuse it too — the frustrating part

This method only **reads** the list. It never adds anything:

```java
import java.util.ArrayList;
import java.util.List;

public class MethodRefuses {

    static void feedAll(List<Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        feedAll(dogs);
    }
}
```

```text
MethodRefuses.java:15: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        feedAll(dogs);
                ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

"But my method doesn't add anything!" The compiler checks the call by looking **only at the
parameter type**, `List<Animal>`. It doesn't read the method body. And a `List<Animal>` parameter
**does** allow adding a Cat. This version compiles fine:

```java
import java.util.ArrayList;
import java.util.List;

public class MethodCouldAdd {

    static void feedAll(List<Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
        animals.add(new Cat("Stray"));
    }

    public static void main(String[] args) {
        List<Animal> zoo = new ArrayList<>();
        zoo.add(new Dog("Rex"));
        feedAll(zoo);
        System.out.println(zoo.size());
    }
}
```

Output:

```text
Rex is eating
2
```

If `feedAll(dogs)` were allowed, this version would sneak a Cat into your Dogs. The parameter type
alone can't tell the two versions apart, so the compiler refuses both.

"I only want to read, so let me pass a `List<Dog>`" is exactly the problem **wildcards** solve.
That's the next concept.

### Step 8: The rule, stated precisely

> **Even though `Dog` is an `Animal`, `List<Dog>` is not a `List<Animal>`.**
> `List<A>` and `List<B>` are only compatible when **A and B are exactly the same type**, however
> A and B are related.

This rule has a name: generic types are **invariant**. "Invariant" just means "the type inside
`< >` must match exactly." It works in both directions, and for every generic type, not just
`List` and animals. A `List<Integer>` isn't a `List<Number>` either.

### Step 9: Every case, one at a time

Here are eight lines of code, some allowed and some refused, worked through one at a time.

**Watch the variable names.** The same names (`dogs`, `animals`) appear in several cases, but each
case is a **separate program**, so each one means something slightly different. That's why every
case starts with **"The variables in this case"**, saying exactly what each variable is declared as
and what's inside it. Read that part first.

Two tools make every case easy.

**Tool 1: Split a generic type into an outer part and an inner part.**

```text
   ArrayList < Dog >
   └───┬───┘   └┬┘
     outer     inner
```

For any line that puts one generic type into another, ask two questions:

- **Question 1 (outer):** is the right side's outer type a kind of the left side's outer type?
  Here the normal "is a" applies: an `ArrayList` is a `List`.
- **Question 2 (inner):** is the inner type **exactly the same** on both sides? Not "is a":
  *exactly the same*. `Dog` and `Dog` pass. `Dog` and `Animal` fail.

Both answers yes: allowed. Either answer no: refused.

**Tool 2: Passing an argument is the same as an assignment.** Take a method declared like this:

```java
static void feedAll(List<Animal> animals) { ... }
```

Here `animals` is the method's **parameter**: a variable that exists only inside `feedAll`,
declared as `List<Animal>`. Now say `main` has a variable `dogs`, declared as `List<Dog>`, and
calls:

```java
feedAll(dogs);
```

To start the method, Java has to put the value of `dogs` (the argument) into `animals` (the
parameter). So behind the scenes it does this assignment:

```java
List<Animal> animals = dogs;     // parameter = argument
```

So every method call can be checked exactly like an assignment line: **parameter on the left,
argument on the right.**

#### Case 1: one Dog into an `Animal` variable — allowed

**The variables in this case:**

- `rex` is declared as `Dog`. It holds one Dog object, named "Rex".
- `pet` is declared as `Animal`. The line below makes it point at the same Dog object as `rex`.

**The line being checked:** `Animal pet = rex;`, which means "put the Dog that `rex` points at into
the `Animal` variable `pet`."

```text
   rex  (label: Dog)    ──┐
                          ├──►  Rex (a Dog object)
   pet  (label: Animal) ──┘
```

```java
public class Case1SingleDog {

    public static void main(String[] args) {
        Dog rex = new Dog("Rex");
        Animal pet = rex;
        pet.eat();
    }
}
```

Output:

```text
Rex is eating
```

- **The compiler's question:** `rex` is a `Dog`, `pet` is an `Animal`. "Is a Dog an Animal?" Yes.
- **Result:** allowed.
- **Why it's safe:** everything the `Animal` label promises (`name()`, `eat()`), a Dog can do.

No lists are involved, so this is ordinary "is a".

#### Case 2: one Dog (or Cat) added into a `List<Animal>` — allowed

**The variables in this case:**

- `animals` is declared as `List<Animal>`. It starts as a new, empty `ArrayList`. Its label
  promises: "everything inside is an Animal."
- There are no `Dog` or `Cat` variables. `new Dog("Rex")` and `new Cat("Tom")` create single
  objects that are handed straight to `add`.
- `a` is the loop variable, declared as `Animal`. It points at each item in turn.

**The lines being checked:** `animals.add(new Dog("Rex"));` and `animals.add(new Cat("Tom"));`,
which mean "put this one Dog (then this one Cat) into the list."

```text
   animals  (label: List<Animal>)  ──►  [ Rex (Dog), Tom (Cat) ]
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case2AddDogToAnimalList {

    public static void main(String[] args) {
        List<Animal> animals = new ArrayList<>();
        animals.add(new Dog("Rex"));
        animals.add(new Cat("Tom"));

        System.out.println(animals.size());
        for (Animal a : animals) {
            a.eat();
        }
    }
}
```

Output:

```text
2
Rex is eating
Tom is eating
```

- **The compiler's question:** `animals` is a `List<Animal>`, so its `add` method accepts an
  `Animal`. "Is a Dog an Animal?" Yes. "Is a Cat an Animal?" Yes.
- **Result:** both allowed.
- **Why it's safe:** the `animals` label only promises "everything inside is an Animal", and a Dog
  and a Cat both are.

This is still about putting **one item** in, so it's still ordinary "is a". The rule from Step 8 is
about pointing a **second list label at a whole list**. That starts with Case 3.

#### Case 3: an `ArrayList<Dog>` into a `List<Dog>` — allowed

**The variables in this case:**

- `kennel` is declared as `ArrayList<Dog>`. It's a new `ArrayList` holding one Dog, Rex. Its label
  promises: "only Dogs inside."
- `dogs` is declared as `List<Dog>`. The line below makes it a **second label on the same list**
  that `kennel` points at. No copy is made. Its label also promises: "only Dogs inside."

**The line being checked:** `List<Dog> dogs = kennel;`, which means "make `dogs` point at the list
that `kennel` points at."

```text
   kennel  (label: ArrayList<Dog>)  ──┐
                                      ├──►  [ Rex (Dog) ]     ← one list
   dogs    (label: List<Dog>)       ──┘
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case3ArrayListIntoList {

    public static void main(String[] args) {
        ArrayList<Dog> kennel = new ArrayList<>();
        kennel.add(new Dog("Rex"));

        List<Dog> dogs = kennel;
        System.out.println(dogs.size());
        dogs.get(0).bark();
    }
}
```

Output:

```text
1
Rex says woof
```

Left side: `dogs`, a `List<Dog>`. Right side: `kennel`, an `ArrayList<Dog>`.

- **Question 1 (outer):** is an `ArrayList` a `List`? Yes, `ArrayList implements List`.
- **Question 2 (inner):** `Dog` on the right, `Dog` on the left. Exactly the same? Yes.
- **Result:** allowed.

**Why it's safe:** both labels say "Dogs only", so neither label can let a Cat in.

#### Case 4: an `ArrayList<Dog>` into a `List<Animal>` — refused

**The variables in this case:**

- `kennel` is declared as `ArrayList<Dog>`. It's a new, empty `ArrayList`. Its label promises:
  "only Dogs inside."
- `animals` is declared as `List<Animal>`. The line below **tries** to make it a second label on
  the list that `kennel` points at. Its label would promise: "you may add any Animal, Cats
  included."

**The line being checked:** `List<Animal> animals = kennel;`, which means "make `animals` point at
the list that `kennel` points at."

```text
   kennel   (label: ArrayList<Dog>)  ──┐
                                       ├──►  [ ]     ← one list, meant for Dogs only
   animals  (label: List<Animal>)   ──┘      ✗ refused: this label would let Cats in
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case4ArrayListIntoAnimalList {

    public static void main(String[] args) {
        ArrayList<Dog> kennel = new ArrayList<>();
        List<Animal> animals = kennel;
    }
}
```

```text
Case4ArrayListIntoAnimalList.java:8: error: incompatible types: ArrayList<Dog> cannot be converted to List<Animal>
        List<Animal> animals = kennel;
                               ^
1 error
```

Left side: `animals`, a `List<Animal>`. Right side: `kennel`, an `ArrayList<Dog>`.

- **Question 1 (outer):** is an `ArrayList` a `List`? Yes.
- **Question 2 (inner):** `Dog` on the right, `Animal` on the left. Exactly the same? **No.**
- **Result:** refused.

This case shows that **a good outer part doesn't save you**. Both questions must pass. Question 2
fails here for the same reason as Case 5 below: through `animals`, a Cat could be added to
`kennel`'s Dogs-only list.

#### Case 5: a `List<Dog>` into a `List<Animal>` — refused

**The variables in this case:**

- `dogs` is declared as `List<Dog>`. It's a new, empty `ArrayList`. Its label promises: "only Dogs
  inside."
- `animals` is declared as `List<Animal>`. The line below **tries** to make it a second label on
  the list that `dogs` points at. Its label would promise: "you may add any Animal, Cats included."

**The line being checked:** `List<Animal> animals = dogs;`, which means "make `animals` point at the
list that `dogs` points at."

```text
   dogs     (label: List<Dog>)     ──┐
                                     ├──►  [ ]     ← one list, meant for Dogs only
   animals  (label: List<Animal>)  ──┘     ✗ refused: this label would let Cats in
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case5DogListIntoAnimalList {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        List<Animal> animals = dogs;
    }
}
```

```text
Case5DogListIntoAnimalList.java:8: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        List<Animal> animals = dogs;
                               ^
1 error
```

Left side: `animals`, a `List<Animal>`. Right side: `dogs`, a `List<Dog>`.

- **Question 1 (outer):** `List` and `List`. Yes.
- **Question 2 (inner):** `Dog` on the right, `Animal` on the left. Exactly the same? **No.**
- **Result:** refused.

**What would go wrong if it were allowed.** The danger is a Cat being **added later**. Here is the
same program continued, imagining Java allowed the line (it doesn't compile, which is the point):

```java
List<Dog> dogs = new ArrayList<>();      // dogs: List<Dog>, empty, "only Dogs inside"
dogs.add(new Dog("Rex"));                // dogs now holds [Rex]

List<Animal> animals = dogs;             // imagine allowed: animals is a SECOND label on the same list

animals.add(new Cat("Tom"));             // legal through animals: a List<Animal> may take a Cat
                                         // the one list now holds [Rex, Tom]

Dog second = dogs.get(1);                // dogs is a List<Dog>, so get(1) says it returns a Dog...
second.bark();                           // ...but it's Tom, a Cat
```

```text
   dogs     (label: List<Dog>)     ──┐
                                     ├──►  [ Rex (Dog), Tom (Cat) ]   ← "Dogs only" list holds a Cat
   animals  (label: List<Animal>)  ──┘
```

(That's Step 4 again, and Step 6 showed it really crashing.)

#### Case 6: a `List<Animal>` into a `List<Dog>` — refused

**The variables in this case:**

- `animals` is declared as `List<Animal>`. It's a new, empty `ArrayList`. Its label promises: "you
  may add any Animal, Cats included."
- `dogs` is declared as `List<Dog>`. The line below **tries** to make it a second label on the list
  that `animals` points at. Its label would promise: "everything inside is a Dog."

**The line being checked:** `List<Dog> dogs = animals;`, which means "make `dogs` point at the list
that `animals` points at."

This is the **opposite direction** to Case 5: the list is a list of Animals, and the new label says
Dogs.

```text
   animals  (label: List<Animal>)  ──┐
                                     ├──►  [ ]     ← one list, may hold any Animal
   dogs     (label: List<Dog>)     ──┘     ✗ refused: this label promises "only Dogs"
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case6AnimalListIntoDogList {

    public static void main(String[] args) {
        List<Animal> animals = new ArrayList<>();
        List<Dog> dogs = animals;
    }
}
```

```text
Case6AnimalListIntoDogList.java:8: error: incompatible types: List<Animal> cannot be converted to List<Dog>
        List<Dog> dogs = animals;
                         ^
1 error
```

Left side: `dogs`, a `List<Dog>`. Right side: `animals`, a `List<Animal>`.

- **Question 1 (outer):** `List` and `List`. Yes.
- **Question 2 (inner):** `Animal` on the right, `Dog` on the left. Exactly the same? **No.**
- **Result:** refused.

**What would go wrong if it were allowed.** The danger is different from Case 5: a list of Animals
may **already** contain a Cat. The same program continued, imagining Java allowed the line:

```java
List<Animal> animals = new ArrayList<>();   // animals: List<Animal>, empty, "any Animal may go in"
animals.add(new Cat("Tom"));                // fine: animals now holds [Tom]

List<Dog> dogs = animals;                   // imagine allowed: dogs is a SECOND label on the same list

Dog first = dogs.get(0);                    // dogs is a List<Dog>, so get(0) says it returns a Dog...
first.bark();                               // ...but it's Tom, a Cat
```

```text
   animals  (label: List<Animal>)  ──┐
                                     ├──►  [ Tom (Cat) ]   ← the "Dogs only" label points at a Cat
   dogs     (label: List<Dog>)     ──┘
```

The `dogs` label would promise "everything in here is a Dog", which was never true.

Cases 5 and 6 side by side:

- **Case 5**: the list is `dogs` (a `List<Dog>`), and the new label is `animals`
  (a `List<Animal>`). The danger is a Cat being **added later** through `animals`.
- **Case 6**: the list is `animals` (a `List<Animal>`), and the new label is `dogs`
  (a `List<Dog>`). The danger is a Cat **already being inside**, then read out through `dogs`.

Either way, Question 2 catches it.

#### Case 7: calling `feedAll(List<Animal>)` with a `List<Dog>` — refused

**The variables in this case:**

- `dogs` is a variable in `main`, declared as `List<Dog>`. It's a new `ArrayList` holding one Dog,
  Rex. Its label promises: "only Dogs inside."
- `animals` is **not** a variable in `main`. It's the **parameter** of `feedAll`, declared in the
  method's first line as `List<Animal>`. It exists only while `feedAll` runs. Its label promises:
  "you may add any Animal, Cats included."
- `a` is the loop variable inside `feedAll`, declared as `Animal`.

**The line being checked:** `feedAll(dogs);`, which means "run `feedAll`, with its parameter
`animals` pointing at the list that `dogs` points at."

```text
   in main:     dogs     (label: List<Dog>)     ──┐
                                                  ├──►  [ Rex (Dog) ]
   in feedAll:  animals  (label: List<Animal>)  ──┘     ✗ refused: this label would let Cats in
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case7MethodWithAnimalList {

    static void feedAll(List<Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        feedAll(dogs);
    }
}
```

```text
Case7MethodWithAnimalList.java:15: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        feedAll(dogs);
                ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Use Tool 2. The parameter is `animals` (a `List<Animal>`), the argument is `dogs` (a `List<Dog>`).
Rewrite the call as the assignment Java does behind the scenes, parameter on the left, argument on
the right:

```java
List<Animal> animals = dogs;     // parameter = argument
```

That's **exactly Case 5**:

- **Question 1 (outer):** `List` and `List`. Yes.
- **Question 2 (inner):** `Dog` on the right, `Animal` on the left. Exactly the same? **No.**
- **Result:** refused.

It doesn't matter that this `feedAll` only reads. The compiler judges by the parameter type alone,
and a `List<Animal>` parameter would allow the method to write `animals.add(new Cat("Stray"));`
(Step 7 showed a version that does), which would put a Cat into `main`'s `dogs` list.

#### Case 8: calling `<T extends Animal> feedAll(List<T>)` with a `List<Dog>` — allowed

**The variables in this case:**

- `dogs` is a variable in `main`, declared as `List<Dog>`. It's a new `ArrayList` holding one Dog,
  Rex.
- `cats` is a variable in `main`, declared as `List<Cat>`. It's a new `ArrayList` holding one Cat,
  Tom.
- `animals` is the **parameter** of `feedAll`, declared as `List<T>`. Its exact type depends on the
  call: for `feedAll(dogs)`, T is `Dog`, so `animals` is a `List<Dog>`. For `feedAll(cats)`, T is
  `Cat`, so `animals` is a `List<Cat>`.
- `a` is the loop variable inside `feedAll`, declared as `T`. The bound `T extends Animal` means
  it's at least an Animal, so `a.eat()` is allowed.

**The lines being checked:** `feedAll(dogs);` and `feedAll(cats);`.

```text
   call feedAll(dogs):   dogs     (label: List<Dog>)  ──┐
                                                        ├──►  [ Rex (Dog) ]
                         animals  (label: List<Dog>)  ──┘     ✓ both labels say "Dogs only"

   call feedAll(cats):   cats     (label: List<Cat>)  ──┐
                                                        ├──►  [ Tom (Cat) ]
                         animals  (label: List<Cat>)  ──┘     ✓ both labels say "Cats only"
```

```java
import java.util.ArrayList;
import java.util.List;

public class Case8BoundedMethod {

    static <T extends Animal> void feedAll(List<T> animals) {
        for (T a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));

        feedAll(dogs);
        feedAll(cats);
    }
}
```

Output:

```text
Rex is eating
Tom is eating
```

This method has a blank, `T`, so for `feedAll(dogs)` the compiler works in three small steps:

1. **Choose T.** "The argument `dogs` is a `List<Dog>`, and the parameter `animals` is a `List<T>`,
   so T = `Dog`."
2. **Check the bound.** "Is Dog an Animal (`T extends Animal`)?" Yes.
3. **Fill in T, then check like an assignment (Tool 2).** The parameter `animals` becomes a
   `List<Dog>`, so behind the scenes it's:

   ```java
   List<Dog> animals = dogs;     // parameter = argument
   ```

   - **Question 1 (outer):** `List` and `List`. Yes.
   - **Question 2 (inner):** `Dog` on the right, `Dog` on the left. Exactly the same? Yes.
   - **Result:** allowed.

For `feedAll(cats)`: the argument `cats` is a `List<Cat>`, so T = `Cat`. Cat is an Animal, and
behind the scenes it's `List<Cat> animals = cats;`, which passes both questions.

**Why this is safe when Case 7 wasn't:** in Case 7, `animals` was a `List<Animal>`, a label that
allows adding a Cat. Here, during `feedAll(dogs)`, `animals` is a `List<Dog>`, the same promise as
`dogs` itself. Inside the method, `animals` is a "list of T", and the only thing you could add to it
is a T. A `new Cat(...)` is not a T, so no Cat can be added. And because of the bound, you can still
call `eat()` on each item.

#### All eight cases in short

Each line names the variables, their declared types, and the result.

- **Case 1**: `Animal pet = rex;`, where `rex` is a `Dog`. **Allowed.** One Dog is an Animal.
- **Case 2**: `animals.add(new Dog("Rex"));`, where `animals` is a `List<Animal>`. **Allowed.** One
  Dog is an Animal.
- **Case 3**: `List<Dog> dogs = kennel;`, where `kennel` is an `ArrayList<Dog>`. **Allowed.** Outer:
  an ArrayList is a List. Inner: Dog = Dog.
- **Case 4**: `List<Animal> animals = kennel;`, where `kennel` is an `ArrayList<Dog>`. **Refused.**
  Outer is fine, but inner: Dog ≠ Animal.
- **Case 5**: `List<Animal> animals = dogs;`, where `dogs` is a `List<Dog>`. **Refused.** Inner:
  Dog ≠ Animal. A Cat could be added later through `animals`.
- **Case 6**: `List<Dog> dogs = animals;`, where `animals` is a `List<Animal>`. **Refused.** Inner:
  Animal ≠ Dog. A Cat may already be inside, and `dogs` would call it a Dog.
- **Case 7**: `feedAll(dogs);`, where `dogs` is a `List<Dog>` and the parameter `animals` is a
  `List<Animal>`. **Refused.** It's `List<Animal> animals = dogs;`, which is Case 5.
- **Case 8**: `feedAll(dogs);`, where `dogs` is a `List<Dog>` and the parameter `animals` is a
  `List<T>` with `<T extends Animal>`. **Allowed.** T becomes Dog, so it's
  `List<Dog> animals = dogs;`.

### Summary

1. A Dog is an Animal, but a `List<Dog>` is **not** a `List<Animal>`.
2. The reason: assigning a list doesn't copy it, and a `List<Animal>` label allows adding a Cat.
   Together, a Cat could get into the list of Dogs.
3. Arrays allow `Animal[] a = dogArray;` and crash **at runtime** with `ArrayStoreException`.
   Generics refuse **at compile time** instead.
4. Forcing it with a raw type compiles with a warning, then crashes with `ClassCastException`.
5. A method with a `List<Animal>` parameter refuses a `List<Dog>` even if it only reads, because
   the compiler judges by the parameter type.
6. Generic types are **invariant**: `List<A>` and `List<B>` are compatible only when A and B are
   exactly the same type.
7. To check any line: split into outer and inner. Outer must pass "is a"; inner must be **exactly
   the same**. A method call is checked as `parameter = argument`.

### Check yourself

1. In one sentence, what would go wrong if `List<Animal> animals = dogs;` were allowed?
2. Why does that danger depend on "assigning a list doesn't copy it"?
3. Arrays allow `Animal[] a = dogArray;`. What happens when you then store a Cat, and *when*?
4. In Case 4, which of the two questions fails, and which passes?
5. Cases 5 and 6 are both refused. What's the danger in each one?
6. In Case 6, which variable is the original list, and which is the new label?
7. In Case 7, where does `animals` live, and what is its declared type? Rewrite the call as an
   assignment. Which case does it become?
8. In Case 8, what is the declared type of `animals` during `feedAll(cats)`?

### Your doubts

#### Doubt 1: "I did not understand the table of allowed and refused code at all."

> *I did not understand the code table properly at all. Can you please break this down a bit more
> cleanly and in an easy to understand way?*

The original version of Step 9 was a table that listed all the cases at once, with a few words of
reason each. It was hard to follow for two reasons:

1. **It mixed two different kinds of lines without saying so.** Cases 1 and 2 put **one item**
   somewhere, where ordinary "is a" applies. Cases 3 to 6 put a **whole list** into another list's
   label, where the inner type must match exactly. Those are different questions, and the table
   didn't say which one each row was asking.
2. **It didn't say that a method call is an assignment.** Cases 7 and 8 looked like a brand new
   kind of rule, when they're really assignments in disguise (`parameter = argument`).

Step 9 is now written case by case instead, using the two tools:

- **Split each type into outer and inner.** Outer follows "is a". Inner must be exactly the same.
- **Rewrite a method call as an assignment**, `parameter = argument`, then check it the same way.

With those two tools, each case is answered by the same two questions, and every refused case comes
with a concrete picture of the Cat that would get in.

#### Doubt 2: "In each case, what is the `dogs` variable and what is the `animals` variable?"

> *In each case, please define what the `dogs` variable is and what the `animals` variable is.
> Please define these for each new case, because it's very hard to follow.*

The confusion came from **reusing the same names in different cases with different meanings**:

- In Case 2, `animals` is a list in `main` that you add Dogs and Cats to.
- In Cases 4 and 5, `animals` is a new label you **try** to point at an existing Dogs list.
- In Case 6, `animals` is the **original** list, and `dogs` is the new label.
- In Cases 7 and 8, `animals` isn't in `main` at all. It's the **parameter** inside `feedAll`.

Each case in Step 9 now begins with **"The variables in this case"**, which gives three things for
every variable:

1. **Its declared type**, for example `dogs` is declared as `List<Dog>`.
2. **What's inside it**, for example "a new `ArrayList` holding one Dog, Rex", or "empty".
3. **What its label promises**, for example "only Dogs inside" or "you may add any Animal".

Then each case states **the line being checked** in plain words ("make `animals` point at the list
that `dogs` points at"), and shows a small picture of which label points at which list.

A habit that helps with any generic code: **before judging a line, say each variable's declared
type out loud.** "`dogs` is a `List<Dog>`. `animals` is a `List<Animal>`. The line points
`animals` at the same list as `dogs`." Once the types are said out loud, the two questions (outer
"is a", inner exactly the same) answer themselves.

---

## Concept 4 — Wildcards: `? extends X` and `?`

**What you'll be able to do after this:** read signatures like `List<? extends Animal>` or
`Collection<? extends GrantedAuthority>`, and say exactly what they accept, what you can do with
them, and what you can't.

### The classes used in this concept

Four small classes, each in its own file in the same folder. `Puppy` is new: it adds one more
level to the family so we can test deeper cases.

```text
            Animal            has: name(), eat()
           /      \
        Dog        Cat        Dog adds bark(), Cat adds meow()
         |
       Puppy                  Puppy adds wagTail()
```

So a Puppy **is a** Dog, and (because a Dog is an Animal) a Puppy **is also an** Animal.

`Animal.java`:

```java
class Animal {
    private final String name;

    Animal(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    void eat() {
        System.out.println(name + " is eating");
    }
}
```

`Dog.java`:

```java
class Dog extends Animal {

    Dog(String name) {
        super(name);
    }

    void bark() {
        System.out.println(name() + " says woof");
    }
}
```

`Cat.java`:

```java
class Cat extends Animal {

    Cat(String name) {
        super(name);
    }

    void meow() {
        System.out.println(name() + " says meow");
    }
}
```

`Puppy.java`:

```java
class Puppy extends Dog {

    Puppy(String name) {
        super(name);
    }

    void wagTail() {
        System.out.println(name() + " wags its tail");
    }
}
```

### Step 1: The problem we're solving

```java
import java.util.ArrayList;
import java.util.List;

public class MethodRefuses {

    static void feedAll(List<Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        feedAll(dogs);
    }
}
```

**The variables:**

- `dogs` is in `main`, declared as `List<Dog>`. It holds one Dog, Rex.
- `animals` is the **parameter** of `feedAll`, declared as `List<Animal>`. Its label promises "you
  may add any Animal, Cats included."

```text
MethodRefuses.java:15: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        feedAll(dogs);
                ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The call is really the assignment `List<Animal> animals = dogs;`. If that were allowed, `feedAll`
could do `animals.add(new Cat(...))` and put a Cat into the Dogs list. The compiler judges only the
parameter type, so it refuses even though this method never adds anything.

**What we want:** a way for the method to say *"I accept a list of Dogs, or of Cats, or of Animals,
and I promise I'll only read from it."* That's what a wildcard says.

### Step 2: The fix — `List<? extends Animal>`

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardFeedAll {

    static void feedAll(List<? extends Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));

        List<Animal> zoo = new ArrayList<>();
        zoo.add(new Dog("Bruno"));
        zoo.add(new Cat("Kitty"));

        feedAll(dogs);
        feedAll(cats);
        feedAll(zoo);
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `cats` is declared as `List<Cat>`. It holds Tom.
- `zoo` is declared as `List<Animal>`. It holds Bruno (a Dog) and Kitty (a Cat).
- `animals` is `feedAll`'s parameter, declared as `List<? extends Animal>`.
- `a` is the loop variable, declared as `Animal`.

Output:

```text
Rex is eating
Tom is eating
Bruno is eating
Kitty is eating
```

All three calls compile. One method now accepts a `List<Dog>`, a `List<Cat>` and a `List<Animal>`.

### Step 3: How to read `? extends Animal`

The `?` is called a **wildcard**. It means **"some type, which I'm not naming."**

`List<? extends Animal>` reads as:

> **"A list of some specific type, which I'm not naming, but which is Animal or a subtype of
> Animal."**

So a `List<? extends Animal>` variable can point at a `List<Animal>`, a `List<Dog>`, a
`List<Cat>`, a `List<Puppy>`, or a list of any other Animal subtype anyone writes later. It can't
point at a `List<String>`, because String isn't an Animal.

**The important detail:** it's a list of **one** specific type that the compiler doesn't know. It
is **not** "a list where any mix of Animals is allowed". That's what `List<Animal>` means. The two
labels say different things:

- **`List<Animal>`:** "I am a list of Animals. You may put any Animal into me, Dog or Cat."
- **`List<? extends Animal>`:** "I am a list of Dogs, *or* a list of Cats, *or* a list of Puppies,
  *or* a list of Animals... I'm not telling you which."

Everything else in this concept follows from that second sentence.

### Step 4: One wildcard label, different lists at different times

A variable declared as `List<? extends Animal>` is a **label**, like any other variable. You can
point it at one list, then later point it at a different list, exactly like `int score = 5;`
followed by `score = 7;` changes what `score` holds.

```java
import java.util.ArrayList;
import java.util.List;

public class RelabelDemo {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));
        cats.add(new Cat("Kitty"));

        List<? extends Animal> someAnimals;

        someAnimals = dogs;
        System.out.println("Now pointing at the dogs list: " + someAnimals.size() + " item");

        someAnimals = cats;
        System.out.println("Now pointing at the cats list: " + someAnimals.size() + " items");
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds one Dog, Rex.
- `cats` is declared as `List<Cat>`. It holds two Cats, Tom and Kitty.
- `someAnimals` is declared as `List<? extends Animal>`. It's declared first with no value, then
  pointed at the `dogs` list, then **re-pointed** at the `cats` list.

Output:

```text
Now pointing at the dogs list: 1 item
Now pointing at the cats list: 2 items
```

Picture the two moments:

```text
After  someAnimals = dogs;

   dogs         (label: List<Dog>)              ──┐
                                                  ├──►  [ Rex (Dog) ]
   someAnimals  (label: List<? extends Animal>) ──┘

   cats         (label: List<Cat>)              ────►  [ Tom (Cat), Kitty (Cat) ]


After  someAnimals = cats;

   dogs         (label: List<Dog>)              ────►  [ Rex (Dog) ]

   cats         (label: List<Cat>)              ──┐
                                                  ├──►  [ Tom (Cat), Kitty (Cat) ]
   someAnimals  (label: List<? extends Animal>) ──┘
```

Nothing is copied. `someAnimals` is just pointed at whichever list you give it.

Both lines are checked the same way. Split the types into outer (`List`) and inner (the type inside
`< >`). Without a wildcard, the inner types must be **exactly the same**. With `? extends Animal`
on the left, the inner type on the right only needs to **be an Animal**:

- `someAnimals = dogs;` — outer: `List` and `List`, yes. Inner: is Dog an Animal? Yes. Allowed.
- `someAnimals = cats;` — outer: yes. Inner: is Cat an Animal? Yes. Allowed.

**Sometimes the choice is only made while the program runs.** In this program, which list
`someAnimals` points at depends on how you start it:

```java
import java.util.ArrayList;
import java.util.List;

public class DecidedAtRuntime {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));

        List<? extends Animal> someAnimals;
        if (args.length > 0) {
            someAnimals = dogs;
        } else {
            someAnimals = cats;
        }

        Animal first = someAnimals.get(0);
        first.eat();
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `cats` is declared as `List<Cat>`. It holds Tom.
- `someAnimals` is declared as `List<? extends Animal>`. It points at `dogs` if you pass anything
  on the command line, otherwise at `cats`.
- `first` is declared as `Animal`. It gets the first item of whichever list `someAnimals` points at.
- `args` holds the command-line words you type after `java DecidedAtRuntime`.

```bash
java DecidedAtRuntime
```

```text
Tom is eating
```

```bash
java DecidedAtRuntime x
```

```text
Rex is eating
```

The compiler checks this code **before** it ever runs, when nobody has typed a command yet. So when
the compiler reads `someAnimals.get(0)` or `someAnimals.add(...)`, it **cannot know** whether
`someAnimals` points at the Dogs list or the Cats list. It only knows the label:
`List<? extends Animal>`. Every rule below is the compiler being safe for **every** list the label
could point at.

A list of Strings still can't be pointed at:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardRefusesString {

    public static void main(String[] args) {
        List<String> seatIds = new ArrayList<>();
        List<? extends Animal> someAnimals = seatIds;
    }
}
```

**The variables:** `seatIds` is declared as `List<String>` (empty). `someAnimals` is declared as
`List<? extends Animal>`.

```text
WildcardRefusesString.java:8: error: incompatible types: List<String> cannot be converted to List<? extends Animal>
        List<? extends Animal> someAnimals = seatIds;
                                             ^
1 error
```

Inner: is String an Animal? No. Refused.

### Step 5: Reading from it is safe — as `Animal`

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardRead {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<? extends Animal> someAnimals = dogs;

        Animal first = someAnimals.get(0);
        first.eat();
        System.out.println(first.name());
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `someAnimals` is declared as `List<? extends Animal>`. It points at the same list as `dogs`.
- `first` is declared as `Animal`. It gets the first item.

Output:

```text
Rex is eating
Rex
```

**Why it's safe:** whatever list `someAnimals` points at (Dogs, Cats, Puppies, Animals), every item
in it is an Animal. So taking an item out **as an `Animal`** can never go wrong.

But you can **only** read items as `Animal` (or something above it, like `Object`), not as `Dog`:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardReadAsDog {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<? extends Animal> someAnimals = dogs;

        Dog first = someAnimals.get(0);
    }
}
```

**The variables:** same `dogs` and `someAnimals` as above. `first` is now declared as `Dog`.

```text
WildcardReadAsDog.java:12: error: incompatible types: CAP#1 cannot be converted to Dog
        Dog first = someAnimals.get(0);
                                   ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

**You** can see that `someAnimals` points at the Dogs list, because it's written one line above.
The compiler only goes by the label, `List<? extends Animal>`, and through that label the list could
be a `List<Cat>`. So it won't promise the item is a Dog. (`CAP#1` is explained fully in Step 7.)

### Step 6: Adding to it is refused

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardAddDog {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        List<? extends Animal> someAnimals = dogs;

        someAnimals.add(new Dog("Bruno"));
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It's empty.
- `someAnimals` is declared as `List<? extends Animal>`. It points at the same list as `dogs`.
- `new Dog("Bruno")` is a single Dog object we try to add.

```text
WildcardAddDog.java:10: error: incompatible types: Dog cannot be converted to CAP#1
        someAnimals.add(new Dog("Bruno"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Even adding a **Dog** to what is really a Dogs list is refused. The compiler only sees the label,
and through that label the list might be a `List<Cat>` (as `DecidedAtRuntime` in Step 4 showed, it
can't always know). A Dog in a list of Cats is exactly what generics exist to stop.

Adding a plain Animal is refused too:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardAddAnimal {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        List<? extends Animal> someAnimals = dogs;

        someAnimals.add(new Animal("Generic"));
    }
}
```

```text
WildcardAddAnimal.java:10: error: incompatible types: Animal cannot be converted to CAP#1
        someAnimals.add(new Animal("Generic"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The list might be a `List<Dog>` (it is, here), and a plain Animal isn't a Dog.

**The danger inside a method.** This is where the rule really protects you:

```java
import java.util.List;

public class WhyNoAdd {

    static void feedAll(List<? extends Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
        animals.add(new Dog("Intruder"));
    }
}
```

**The variables:** `animals` is the parameter, declared as `List<? extends Animal>`. `a` is the loop
variable, declared as `Animal`.

```text
WhyNoAdd.java:10: error: incompatible types: Dog cannot be converted to CAP#1
        animals.add(new Dog("Intruder"));
                    ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Anyone may call `feedAll(cats)` with a `List<Cat>`. If this `add` were allowed, a Dog called
"Intruder" would land in their list of Cats. The compiler refuses the `add` **inside the method**,
which is exactly what makes it safe to let the method accept a `List<Dog>` or a `List<Cat>` from
outside.

(The one thing you *can* add is `null`, because `null` fits every type. It compiles, but it's
almost never useful.)

### Step 7: What `CAP#1` is, and where it comes from

You never wrote `CAP#1`. **The compiler invented it.**

When the compiler checks a line that uses a variable whose type contains `?`, it has to reason about
"the actual type of the list behind this `?`". That type has no name, because `?` means "I'm not
naming it". So the compiler **makes up a temporary name** for it, and calls it `CAP#1`.

- **CAP** is short for **capture**. The compiler "captures" the unknown type behind the `?` and
  gives it a name, so it can talk about it.
- **#1** is a number: the first such invented name in this error message. If one error involves two
  different unknown types, you'll see `CAP#1` and `CAP#2`.
- **fresh type-variable** means "a brand-new placeholder name, invented just now, not one you
  wrote."

Everyday version: a police report says *"Suspect #1: name unknown. Known facts: about 6 feet tall."*
Nobody knows the suspect's real name, but they need *some* name to write the report. `CAP#1` is the
compiler's "Suspect #1", and `extends Animal` is the one known fact about it.

Reading the error from Step 6 line by line:

```text
WildcardAddDog.java:10: error: incompatible types: Dog cannot be converted to CAP#1
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

- `where CAP#1 is a fresh type-variable:` — "CAP#1 is a name I just made up."
- `CAP#1 extends Animal` — "All I know about it: it's Animal or something below Animal."
- `from capture of ? extends Animal` — "I made it up for the `? extends Animal` you wrote."
- `Dog cannot be converted to CAP#1` — "You want to put a Dog where a CAP#1 goes. I can't be sure a
  Dog **is** a CAP#1, because CAP#1 might be Cat."

**A trick for reading any CAP error:** replace `CAP#1` with "the unknown type behind the `?`". The
error becomes: *"Dog cannot be converted to the unknown type behind `? extends Animal`."*

**`CAP#1` is the compiler's own version of `T`.** Compare the same mistake written with a named
type parameter `T`, and with a wildcard:

```java
import java.util.List;

public class TErrorMessage {

    static <T extends Animal> void addDog(List<T> animals) {
        animals.add(new Dog("Bruno"));
    }
}
```

```text
TErrorMessage.java:6: error: incompatible types: Dog cannot be converted to T
        animals.add(new Dog("Bruno"));
                    ^
  where T is a type-variable:
    T extends Animal declared in method <T>addDog(List<T>)
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```java
import java.util.List;

public class WildcardErrorMessage {

    static void addDog(List<? extends Animal> animals) {
        animals.add(new Dog("Bruno"));
    }
}
```

```text
WildcardErrorMessage.java:6: error: incompatible types: Dog cannot be converted to CAP#1
        animals.add(new Dog("Bruno"));
                    ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The two errors have the same shape:

- With `<T extends Animal>`, **you** named the unknown type `T`, so the error says `T` and
  `declared in method`.
- With `? extends Animal`, **nobody** named it, so the compiler invented `CAP#1` and says
  `from capture of`.

**Two wildcards give `CAP#1` and `CAP#2`.** Here, two separate wildcard labels point at two
different lists:

```java
import java.util.ArrayList;
import java.util.List;

public class TwoCaptures {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));

        List<? extends Animal> first = dogs;
        List<? extends Animal> second = cats;

        first.add(second.get(0));
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>` (holds Rex). `cats` is declared as `List<Cat>` (holds Tom).
- `first` is declared as `List<? extends Animal>` and points at the `dogs` list.
- `second` is declared as `List<? extends Animal>` and points at the `cats` list.
- The line tries to take Tom out of `second` and add him to `first`, which would put a Cat into the
  Dogs list.

With the full error details switched on (`javac -Xdiags:verbose TwoCaptures.java`):

```text
TwoCaptures.java:15: error: no suitable method found for add(CAP#1)
        first.add(second.get(0));
             ^
    method List.add(CAP#2) is not applicable
      (argument mismatch; Animal cannot be converted to CAP#2)
    method List.add(int,CAP#2) is not applicable
      (actual and formal argument lists differ in length)
  where CAP#1,CAP#2 are fresh type-variables:
    CAP#1 extends Animal from capture of ? extends Animal
    CAP#2 extends Animal from capture of ? extends Animal
1 error
```

Two unknowns, two invented names. Both are "some Animal type", but the compiler doesn't assume
they're the **same** Animal type, and here they really aren't (Cat and Dog). So it refuses.

### Step 8: The rule in one line

> **`? extends X`: safe to read from (each item is an X), not safe to add to.**

Everyday picture: a sealed box labelled **"contains one kind of animal."** When you take something
out, you know it's *an animal*, though not which kind. You can't put anything in, because the box
might be the cats box and you're holding a dog.

The two labels side by side:

- **`List<Animal>`**
  - Accepts: only a `List<Animal>`.
  - Read: yes, as `Animal`.
  - Add: yes, any Animal.
- **`List<? extends Animal>`**
  - Accepts: a `List<Animal>`, `List<Dog>`, `List<Cat>`, `List<Puppy>`, ...
  - Read: yes, as `Animal`.
  - Add: **no** (except `null`).

You give up adding, and in exchange you accept many more kinds of list. Removing items, `size()`,
`contains(...)` and `clear()` all still work, because none of them **puts** anything in (see the
solved examples in Step 12).

### Step 9: The same with numbers

```java
import java.util.List;

public class WildcardNumbers {

    static double total(List<? extends Number> xs) {
        double sum = 0;
        for (Number x : xs) {
            sum = sum + x.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Integer> seatsSold = List.of(3, 5, 2);
        List<Double> prices = List.of(149.5, 50.0);

        System.out.println(total(seatsSold));
        System.out.println(total(prices));
    }
}
```

**The variables:**

- `seatsSold` is declared as `List<Integer>`. It holds 3, 5 and 2.
- `prices` is declared as `List<Double>`. It holds 149.5 and 50.0.
- `xs` is `total`'s parameter, declared as `List<? extends Number>`: "a list of some type that is
  Number or below."
- `x` is the loop variable, declared as `Number`. Reading as Number is safe, so `doubleValue()`
  works.
- `sum` is a plain `double` that adds up the values.

Output:

```text
10.0
199.5
```

Adding is refused, exactly as with animals:

```java
import java.util.List;

public class WildcardNumbersAdd {

    static void addOne(List<? extends Number> xs) {
        xs.add(1);
    }
}
```

**The variables:** `xs` is the parameter, declared as `List<? extends Number>`. `1` is an `int`,
which Java would turn into an `Integer`.

```text
WildcardNumbersAdd.java:6: error: incompatible types: int cannot be converted to CAP#1
        xs.add(1);
               ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Number from capture of ? extends Number
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`xs` might point at a `List<Double>`, and an Integer must not go into a list of Doubles.

### Step 10: The plain `?` — "a list of anything"

A `?` with no `extends` means **"some type, which could be anything."** It's the same as
`? extends Object`.

```java
import java.util.List;

public class UnboundedWildcard {

    static void printAll(List<?> items) {
        System.out.println(items.size() + " items:");
        for (Object item : items) {
            System.out.println("  " + item);
        }
    }

    public static void main(String[] args) {
        List<String> seatIds = List.of("A1", "B2");
        List<Integer> prices = List.of(150, 200, 250);

        printAll(seatIds);
        printAll(prices);
    }
}
```

**The variables:**

- `seatIds` is declared as `List<String>`. It holds "A1" and "B2".
- `prices` is declared as `List<Integer>`. It holds 150, 200 and 250.
- `items` is `printAll`'s parameter, declared as `List<?>`: "a list of some type I'm not naming."
- `item` is the loop variable, declared as `Object`. That's the only safe type to read as, because
  the items could be anything.

Output:

```text
2 items:
  A1
  B2
3 items:
  150
  200
  250
```

Same rules: read items as `Object`, call `size()`, but no adding:

```java
import java.util.List;

public class UnboundedAdd {

    static void addSomething(List<?> items) {
        items.add("A1");
    }
}
```

```text
UnboundedAdd.java:6: error: incompatible types: String cannot be converted to CAP#1
        items.add("A1");
                  ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object from capture of ?
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`items` might be a `List<Integer>`, and a String must not go into it. You'll see `List<?>` on
methods that don't care what's in the list at all: printing it, counting it, clearing it.

### Step 11: `? extends Animal` versus `<T extends Animal>`

These look similar, and they overlap a lot. This step shows exactly where they're the same and where
they differ.

#### First: `List<T extends Animal>` is not valid Java

A type parameter's bound is written **only where T is declared**: in the `< >` just before the
method's return type. You then **use** T plainly, as `List<T>`. Writing the bound inside `List< >`
is a syntax error:

```java
import java.util.List;

public class BadSyntax {

    static void feedAll(List<T extends Animal> animals) {
    }
}
```

```text
BadSyntax.java:5: error: > or ',' expected
    static void feedAll(List<T extends Animal> animals) {
                               ^
1 error
```

So the real comparison is between these two method signatures:

```java
static <T extends Animal> void feedT(List<T> animals)          // a named type parameter T
static void feedWildcard(List<? extends Animal> animals)         // an unnamed wildcard ?
```

#### Same 1: both accept the same lists, and both read items as Animals

```java
import java.util.ArrayList;
import java.util.List;

public class BothAccept {

    static void feedWildcard(List<? extends Animal> animals) {
        for (Animal a : animals) {
            a.eat();
        }
    }

    static <T extends Animal> void feedT(List<T> animals) {
        for (T a : animals) {
            a.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        List<Puppy> puppies = new ArrayList<>();
        puppies.add(new Puppy("Bolt"));

        feedWildcard(dogs);
        feedT(dogs);
        feedWildcard(puppies);
        feedT(puppies);
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `puppies` is declared as `List<Puppy>`. It holds one Puppy, Bolt.
- In `feedWildcard`, the parameter `animals` is declared as `List<? extends Animal>`, and the loop
  variable `a` is an `Animal`.
- In `feedT`, the parameter `animals` is declared as `List<T>`, and the loop variable `a` is a `T`.
  For `feedT(dogs)`, T = Dog. For `feedT(puppies)`, T = Puppy.

Output:

```text
Rex is eating
Rex is eating
Bolt is eating
Bolt is eating
```

#### Same 2: neither lets you add a brand-new Dog

Step 7 showed both: `Dog cannot be converted to T` and `Dog cannot be converted to CAP#1`. With
`List<T>`, T might be Cat, so a new Dog isn't safe there either.

#### Difference 1: T has a name, so the return type can use it

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardVsT {

    static Animal firstWildcard(List<? extends Animal> animals) {
        return animals.get(0);
    }

    static <T extends Animal> T firstT(List<T> animals) {
        return animals.get(0);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        Dog viaT = firstT(dogs);
        viaT.bark();

        Animal viaWildcard = firstWildcard(dogs);
        viaWildcard.eat();
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `firstWildcard`'s parameter `animals` is a `List<? extends Animal>`. Its return type is `Animal`.
- `firstT`'s parameter `animals` is a `List<T>`. Its return type is `T`. For `firstT(dogs)`,
  T = Dog, so it returns a `Dog`.
- `viaT` is declared as `Dog`. `viaWildcard` is declared as `Animal`.

Output:

```text
Rex says woof
Rex is eating
```

With the wildcard, the hidden type has no name to put in the return type, so the best it can promise
is `Animal`. Asking for a Dog fails:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardVsTWrong {

    static Animal firstWildcard(List<? extends Animal> animals) {
        return animals.get(0);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        Dog viaWildcard = firstWildcard(dogs);
    }
}
```

```text
WildcardVsTWrong.java:14: error: incompatible types: Animal cannot be converted to Dog
        Dog viaWildcard = firstWildcard(dogs);
                                       ^
1 error
```

#### Difference 2: with T, you can put back an item that came from the same list

```java
import java.util.ArrayList;
import java.util.List;

public class DuplicateFirstT {

    static <T extends Animal> void duplicateFirst(List<T> animals) {
        T first = animals.get(0);
        animals.add(first);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        duplicateFirst(dogs);
        System.out.println(dogs.size());
        for (Dog d : dogs) {
            d.bark();
        }
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It starts holding Rex.
- `animals` is the parameter, declared as `List<T>`. For this call, T = Dog.
- `first` is declared as `T`: the first item, whose type is "the same T as the list".
- `d` is the loop variable, declared as `Dog`.

Output:

```text
2
Rex says woof
Rex says woof
```

`first` is a `T`, and `animals` is a list of `T`, so adding `first` back is safe **whatever T is**.

The wildcard version can't do it, because reading gives only an `Animal`, and an Animal can't be
added:

```java
import java.util.List;

public class DuplicateFirstWildcard {

    static void duplicateFirst(List<? extends Animal> animals) {
        Animal first = animals.get(0);
        animals.add(first);
    }
}
```

```text
DuplicateFirstWildcard.java:7: error: incompatible types: Animal cannot be converted to CAP#1
        animals.add(first);
                    ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

#### Difference 3: T ties two parameters to the **same** type; two `?`s are unrelated

```java
import java.util.ArrayList;
import java.util.List;

public class SameTTwoLists {

    static <T extends Animal> void moveFirst(List<T> from, List<T> to) {
        T first = from.remove(0);
        to.add(first);
    }

    public static void main(String[] args) {
        List<Dog> shelter = new ArrayList<>();
        shelter.add(new Dog("Rex"));
        List<Dog> home = new ArrayList<>();

        moveFirst(shelter, home);
        System.out.println(shelter.size() + " in shelter, " + home.size() + " at home");
    }
}
```

**The variables:**

- `shelter` is declared as `List<Dog>`. It starts holding Rex.
- `home` is declared as `List<Dog>`. It starts empty.
- `from` and `to` are the two parameters, **both** declared as `List<T>`, with the **same** T. For
  this call, T = Dog.
- `first` is declared as `T`: the item removed from `from`.

Output:

```text
0 in shelter, 1 at home
```

Because both parameters say `List<T>`, the compiler insists both lists have the same T. Mixing Dogs
and Cats is refused **at the call**:

```java
import java.util.ArrayList;
import java.util.List;

public class SameTTwoListsWrong {

    static <T extends Animal> void moveFirst(List<T> from, List<T> to) {
        T first = from.remove(0);
        to.add(first);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));
        List<Cat> cats = new ArrayList<>();

        moveFirst(dogs, cats);
    }
}
```

**The variables:** `dogs` is a `List<Dog>` holding Rex. `cats` is a `List<Cat>`, empty.

```text
SameTTwoListsWrong.java:16: error: method moveFirst in class SameTTwoListsWrong cannot be applied to given types;
        moveFirst(dogs, cats);
        ^
  required: List<T>,List<T>
  found:    List<Dog>,List<Cat>
  reason: inference variable T has incompatible equality constraints Cat,Dog
  where T is a type-variable:
    T extends Animal declared in method <T>moveFirst(List<T>,List<T>)
1 error
```

`incompatible equality constraints Cat,Dog` means: "the first list says T must be Dog, the second
says T must be Cat. T can't be both."

With two wildcards, each `?` is its **own** unknown (`CAP#1` and `CAP#2`), so the compiler can't
promise they match, and refuses the `add` **inside** the method:

```java
import java.util.List;

public class TwoWildcardLists {

    static void moveFirst(List<? extends Animal> from, List<? extends Animal> to) {
        Animal first = from.remove(0);
        to.add(first);
    }
}
```

```text
TwoWildcardLists.java:7: error: incompatible types: Animal cannot be converted to CAP#1
        to.add(first);
               ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

#### Difference 4: a wildcard can be written anywhere; T only inside the method that declares it

`? extends Animal` can be used as the type of **any** variable, like `someAnimals` in `main`. `T`
only exists inside the method (or class) whose `< >` declared it:

```java
import java.util.ArrayList;
import java.util.List;

public class TOnlyInsideMethod {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        List<? extends Animal> someAnimals = dogs;
        List<T> sameDogs = dogs;
    }
}
```

```text
TOnlyInsideMethod.java:9: error: cannot find symbol
        List<T> sameDogs = dogs;
             ^
  symbol:   class T
  location: class TOnlyInsideMethod
1 error
```

The wildcard line compiles. The `T` line doesn't: `main` never declared a T, so the compiler goes
looking for a class called `T` and can't find one.

#### The difference in one sentence

**`T` is an unknown type with a name**: declared once, usable in several places (return type, other
parameters, local variables), and the same type everywhere in one call. **`?` is an unknown type
without a name**: written in one place, and every `?` is a separate unknown.

Rule of thumb:

- The type is needed **only once**, just to read from one parameter? Use a wildcard. It's shorter.
- The type must **connect** two places (parameter and return type, two parameters, or putting an
  item back)? Use `<T extends ...>`.

### Step 12: Solved examples

Every line below was compiled on its own. They're grouped into three kinds: pointing a wildcard label
at a list, using a wildcard label, and calling methods with wildcard parameters.

#### Group A: pointing a label at a list

Every case in this group starts from these variables. Each one is an empty list, so only the
**types** matter.

```java
List<Animal> animals = new ArrayList<>();        // animals:      a List<Animal>
List<Dog> dogs = new ArrayList<>();              // dogs:         a List<Dog>
List<Cat> cats = new ArrayList<>();              // cats:         a List<Cat>
List<Puppy> puppies = new ArrayList<>();         // puppies:      a List<Puppy>
List<String> seatIds = new ArrayList<>();        // seatIds:      a List<String>
List<Integer> seatsSold = new ArrayList<>();     // seatsSold:    a List<Integer>
List<Double> prices = new ArrayList<>();         // prices:       a List<Double>
List<Number> mixedNumbers = new ArrayList<>();   // mixedNumbers: a List<Number>
ArrayList<Dog> kennel = new ArrayList<>();       // kennel:       an ArrayList<Dog>
```

In each case, `x` (and `y`, where used) is the new label being created. The check is: **outer**
must pass "is a". **Inner**: if the left side has `? extends X`, the right side's inner type must
be X or below. Without a wildcard, it must be exactly the same.

**A1.** `List<? extends Animal> x = animals;` — `animals` is a `List<Animal>`.
Inner: is Animal "Animal or below"? Yes (Animal itself counts). **Allowed.**

**A2.** `List<? extends Animal> x = dogs;` — `dogs` is a `List<Dog>`.
Inner: is Dog an Animal? Yes. **Allowed.**

**A3.** `List<? extends Animal> x = cats;` — `cats` is a `List<Cat>`.
Inner: is Cat an Animal? Yes. **Allowed.**

**A4.** `List<? extends Animal> x = puppies;` — `puppies` is a `List<Puppy>`.
Inner: is Puppy an Animal? Yes, a Puppy is a Dog, and a Dog is an Animal. **Allowed.**

**A5.** `List<? extends Animal> x = seatIds;` — `seatIds` is a `List<String>`.
Inner: is String an Animal? No. **Refused.**

```text
error: incompatible types: List<String> cannot be converted to List<? extends Animal>
```

**A6.** `List<? extends Dog> x = dogs;` — `dogs` is a `List<Dog>`. The label now says "Dog or
below".
Inner: is Dog "Dog or below"? Yes. **Allowed.**

**A7.** `List<? extends Dog> x = puppies;` — `puppies` is a `List<Puppy>`.
Inner: is Puppy a Dog? Yes. **Allowed.**

**A8.** `List<? extends Dog> x = cats;` — `cats` is a `List<Cat>`.
Inner: is Cat a Dog? No. Cat is beside Dog in the family tree, not below it. **Refused.**

```text
error: incompatible types: List<Cat> cannot be converted to List<? extends Dog>
```

**A9.** `List<? extends Dog> x = animals;` — `animals` is a `List<Animal>`.
Inner: is Animal a Dog? No. Animal is **above** Dog, and `extends` only allows Dog or below.
**Refused.**

```text
error: incompatible types: List<Animal> cannot be converted to List<? extends Dog>
```

**A10.** `List<? extends Number> x = seatsSold;` — `seatsSold` is a `List<Integer>`.
Inner: is Integer a Number? Yes. **Allowed.**

**A11.** `List<? extends Number> x = prices;` — `prices` is a `List<Double>`.
Inner: is Double a Number? Yes. **Allowed.**

**A12.** `List<? extends Number> x = mixedNumbers;` — `mixedNumbers` is a `List<Number>`.
Inner: is Number "Number or below"? Yes. **Allowed.**

**A13.** `List<? extends Number> x = seatIds;` — `seatIds` is a `List<String>`.
Inner: is String a Number? No. **Refused.**

```text
error: incompatible types: List<String> cannot be converted to List<? extends Number>
```

**A14.** `List<?> x = seatIds;` — `seatIds` is a `List<String>`. Plain `?` accepts anything.
**Allowed.**

**A15.** `List<?> x = dogs;` — `dogs` is a `List<Dog>`. **Allowed.**

**A16.** `List<? extends Animal> x = kennel;` — `kennel` is an `ArrayList<Dog>`.
Outer: is an ArrayList a List? Yes. Inner: is Dog an Animal? Yes. **Allowed.**

**A17.** Two lines: `List<? extends Animal> x = dogs;` then `List<Animal> y = x;`.
`x` is a `List<? extends Animal>` pointing at the Dogs list. `y` would be a `List<Animal>`.
Inner: `y` needs **exactly** Animal (no wildcard on the left), but `x`'s inner type is the unknown
CAP#1. **Refused.** If allowed, `y.add(new Cat(...))` would put a Cat into the Dogs list.

```text
error: incompatible types: List<CAP#1> cannot be converted to List<Animal>
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

**A18.** Two lines: `List<? extends Dog> x = puppies;` then `List<? extends Animal> y = x;`.
`x` is "a list of Dog or below". `y` would be "a list of Animal or below".
Every "Dog or below" type is also "Animal or below". **Allowed.** (Why this isn't "CAP#1 versus
CAP#2": see Doubt 5 at the end of this concept.)

**A19.** Two lines: `List<? extends Animal> x = dogs;` then `List<? extends Dog> y = x;`.
`x` is "a list of Animal or below", and through that label it might be a list of Cats. `y` needs
"Dog or below". **Refused.**

```text
error: incompatible types: List<CAP#1> cannot be converted to List<? extends Dog>
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

**A20.** `List<Dog> x = puppies;` — no wildcard. `puppies` is a `List<Puppy>`.
Inner must be **exactly** the same: Puppy and Dog aren't. **Refused.** (The same rule as
`List<Dog>` into `List<Animal>`, one level lower.)

```text
error: incompatible types: List<Puppy> cannot be converted to List<Dog>
```

#### Group B: using a `List<? extends Animal>` label

Every case in this group starts from these variables:

```java
List<Dog> dogs = new ArrayList<>();          // dogs: a List<Dog>
dogs.add(new Dog("Rex"));                    // dogs holds [Rex]
List<? extends Animal> someAnimals = dogs;   // someAnimals: a List<? extends Animal>,
                                             // pointing at the same list as dogs
```

Remember: the compiler judges only the label `List<? extends Animal>`. It doesn't use the fact that
we pointed it at `dogs`.

**B1.** `Animal a = someAnimals.get(0);` — reading as Animal. Every item is an Animal. **Allowed.**

**B2.** `Object o = someAnimals.get(0);` — reading as Object. Every Animal is an Object.
**Allowed.**

**B3.** `Dog d = someAnimals.get(0);` — reading as Dog. The list might be Cats. **Refused.**

```text
error: incompatible types: CAP#1 cannot be converted to Dog
```

**B4.** `someAnimals.add(new Dog("Bruno"));` — the list might be a `List<Cat>` or a
`List<Puppy>` (a plain Dog isn't a Puppy). **Refused.**

```text
error: incompatible types: Dog cannot be converted to CAP#1
```

**B5.** `someAnimals.add(new Cat("Tom"));` — the list might be a `List<Dog>` or a `List<Puppy>`.
**Refused.**

```text
error: incompatible types: Cat cannot be converted to CAP#1
```

**B6.** `someAnimals.add(new Animal("Generic"));` — the list might be Dogs, Cats or Puppies. A plain
Animal is none of them. **Refused.**

```text
error: incompatible types: Animal cannot be converted to CAP#1
```

**B7.** `someAnimals.add(new Puppy("Bolt"));` — even the lowest type is refused: the list might be
a `List<Cat>`. **Refused.**

```text
error: incompatible types: Puppy cannot be converted to CAP#1
```

**B8.** `someAnimals.add(null);` — `null` fits every type. **Allowed** (but rarely useful).

**B9.** `System.out.println(someAnimals.size());` — nothing is put in. **Allowed.** Prints `1`.

**B10.** `Animal removed = someAnimals.remove(0);` — taking an item **out** is fine, and it comes out
as an Animal. **Allowed.** Followed by
`System.out.println(removed.name() + " removed, " + dogs.size() + " left");` it prints
`Rex removed, 0 left`. (It's the same list as `dogs`, so `dogs` shrinks too.)

**B11.** `System.out.println(someAnimals.contains(new Cat("Tom")));` — `contains` only looks, and
accepts any object. **Allowed.** Prints `false`.

**B12.** `someAnimals.clear();` — empties the list. Nothing is put in. **Allowed.** Afterwards
`dogs.size()` is `0`.

**B13.** `someAnimals.get(0).eat();` — the item is an Animal, and Animals have `eat()`. **Allowed.**
Prints `Rex is eating`.

**B14.** `someAnimals.get(0).bark();` — the item is only known to be an Animal, and Animal has no
`bark()`. **Refused.**

```text
error: cannot find symbol
        someAnimals.get(0).bark();
                          ^
  symbol:   method bark()
  location: class Animal
```

The same with numbers. These cases start from:

```java
List<Integer> seatsSold = new ArrayList<>();     // seatsSold: a List<Integer>
seatsSold.add(3);                                // seatsSold holds [3]
List<? extends Number> someNumbers = seatsSold;  // someNumbers: a List<? extends Number>,
                                                 // pointing at the same list as seatsSold
```

**B15.** `Number n = someNumbers.get(0);` — reading as Number. **Allowed.**
`System.out.println(n.doubleValue());` prints `3.0`.

**B16.** `double d = someNumbers.get(0).doubleValue() * 2;` — Number has `doubleValue()`.
**Allowed.** Prints `6.0`.

**B17.** `Integer i = someNumbers.get(0);` — the list might be Doubles. **Refused.**

```text
error: incompatible types: CAP#1 cannot be converted to Integer
```

**B18.** `someNumbers.add(5);` — the list might be a `List<Double>`. **Refused.**

```text
error: incompatible types: int cannot be converted to CAP#1
```

**B19.** `someNumbers.add(2.5);` — the list might be a `List<Integer>` (here it is). **Refused.**

```text
error: incompatible types: double cannot be converted to CAP#1
```

#### Group C: calling methods that take wildcard parameters

The methods:

```java
static void feedAll(List<? extends Animal> animals)    // parameter animals: "Animal or below"
static void walkDogs(List<? extends Dog> pack)         // parameter pack:    "Dog or below"
static double total(List<? extends Number> xs)         // parameter xs:      "Number or below"
static int countAll(List<?> items)                     // parameter items:   "anything"
```

`feedAll` calls `eat()` on each item, `walkDogs` calls `bark()` on each item, `total` adds up
`doubleValue()`, and `countAll` returns `size()`.

The variables in `main`:

```java
List<Animal> zoo = new ArrayList<>();           // zoo:        a List<Animal>, holds Kitty (a Cat)
List<Dog> dogs = new ArrayList<>();             // dogs:       a List<Dog>,    holds Rex
List<Puppy> puppies = new ArrayList<>();        // puppies:    a List<Puppy>,  holds Bolt
List<Cat> cats = new ArrayList<>();             // cats:       a List<Cat>,    empty
List<Integer> seatsSold = List.of(3, 5);        // seatsSold:  a List<Integer>
List<Double> prices = List.of(149.5);           // prices:     a List<Double>
List<Long> bookingIds = List.of(9000000001L);   // bookingIds: a List<Long>
List<String> seatIds = List.of("A1", "B2", "C3"); // seatIds:  a List<String>
```

Each call is checked as `parameter = argument`.

**C1.** `feedAll(zoo);` — `List<? extends Animal> animals = zoo;`. Inner: Animal is Animal or
below. **Allowed.** Prints `Kitty is eating`.

**C2.** `feedAll(dogs);` — inner: Dog is an Animal. **Allowed.** Prints `Rex is eating`.

**C3.** `feedAll(puppies);` — inner: Puppy is an Animal. **Allowed.** Prints `Bolt is eating`.

**C4.** `walkDogs(dogs);` — `List<? extends Dog> pack = dogs;` (the parameter `pack` on the left,
`main`'s `dogs` on the right). Inner: Dog is Dog or below. **Allowed.** Prints `Rex says woof`.

**C5.** `walkDogs(puppies);` — inner: Puppy is a Dog. **Allowed.** Prints `Bolt says woof`.
(A Puppy can bark, because it's a Dog.)

**C6.** `walkDogs(cats);` — inner: is Cat a Dog? No. **Refused.** (Good: a Cat can't bark.)

```text
error: incompatible types: List<Cat> cannot be converted to List<? extends Dog>
```

**C7.** `walkDogs(zoo);` — inner: is Animal a Dog? No. Animal is above Dog. **Refused.** (Good:
`zoo` holds a Cat, and `walkDogs` would ask her to bark.)

```text
error: incompatible types: List<Animal> cannot be converted to List<? extends Dog>
```

**C8.** `total(seatsSold);` — inner: Integer is a Number. **Allowed.** Returns `8.0`.

**C9.** `total(prices);` — inner: Double is a Number. **Allowed.** Returns `149.5`.

**C10.** `total(bookingIds);` — inner: Long is a Number. **Allowed.** Returns `9.000000001E9`.

**C11.** `total(seatIds);` — inner: is String a Number? No. **Refused.**

```text
error: incompatible types: List<String> cannot be converted to List<? extends Number>
```

**C12.** `countAll(seatIds);` — `List<?>` accepts anything. **Allowed.** Returns `3`.

**C13.** `countAll(dogs);` — **Allowed.** Returns `1`.

### Step 13: Where you'll meet this in real code

**Java's own `List.addAll`** is declared like this:

```java
boolean addAll(Collection<? extends E> c)
```

```java
import java.util.ArrayList;
import java.util.List;

public class AddAllDemo {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<Cat> cats = new ArrayList<>();
        cats.add(new Cat("Tom"));

        List<Animal> zoo = new ArrayList<>();
        zoo.addAll(dogs);
        zoo.addAll(cats);

        for (Animal a : zoo) {
            a.eat();
        }
    }
}
```

**The variables:**

- `dogs` is declared as `List<Dog>`. It holds Rex.
- `cats` is declared as `List<Cat>`. It holds Tom.
- `zoo` is declared as `List<Animal>`. It starts empty. For `zoo`, E = Animal, so `zoo.addAll`
  accepts a `Collection<? extends Animal>`.
- `a` is the loop variable, declared as `Animal`.

Output:

```text
Rex is eating
Tom is eating
```

Read it aloud: *"Add everything from a collection of some type that is an Animal or below."*
`addAll` only **reads** from `dogs` and `cats`, and **copies** each item into `zoo`. No second label
is created on `dogs`, so no Cat can get into the Dogs list. And `zoo` is a `List<Animal>`, so it may
hold both.

**Spring Security** (you'll meet it later) has this method on a logged-in user:

```java
Collection<? extends GrantedAuthority> getAuthorities()
```

Read it aloud: *"Returns a collection of some type that is a GrantedAuthority or below. You can read
GrantedAuthority items from it, but you can't add to it."* Being able to read it like that is the
whole goal of this concept.

### Summary

1. `?` is a **wildcard**: "some type I'm not naming."
2. `List<? extends Animal>` means "a list of **one** specific type, unknown to the compiler, which is
   Animal or below." A variable of this type can point at a `List<Animal>`, `List<Dog>`,
   `List<Cat>`, `List<Puppy>`, and can be re-pointed later.
3. With a wildcard on the left, the inner check becomes "is a" (X or below) instead of "exactly the
   same."
4. **Reading** is safe, as the bound type (`Animal`, `Number`, `Object`). **Adding** is refused
   (except `null`), because the list might be of a different subtype. Removing, `size()`,
   `contains()` and `clear()` still work.
5. `CAP#1` is a temporary name the compiler invents for the unknown type behind a `?`. Read it as
   "the unknown type". Two separate `?`s become `CAP#1` and `CAP#2`.
6. Plain `List<?>` is "a list of anything": read as `Object`, no adding.
7. `List<T extends Animal>` isn't valid Java. Compare `<T extends Animal> ... List<T>` with
   `List<? extends Animal>`: both accept the same lists, but **T has a name** (usable as a return
   type, for a second parameter, or to put an item back), while each **`?` is nameless** and
   separate.

### Check yourself

1. What's the difference between `List<Animal>` and `List<? extends Animal>` in what they *accept*,
   and in what you can *do* with them?
2. `someAnimals` is declared as `List<? extends Animal>` and points at a `List<Dog>`. Why is
   `someAnimals.add(new Dog("Bruno"))` still refused?
3. In `CAP#1 extends Animal from capture of ? extends Animal`, what is `CAP#1`, and who made it?
4. Is `List<? extends Dog> x = animals;` allowed, where `animals` is a `List<Animal>`? Why?
5. Why can `firstT(dogs)` return a `Dog`, while `firstWildcard(dogs)` can only return an `Animal`?
6. Why does `moveFirst(dogs, cats)` fail for `<T extends Animal> moveFirst(List<T>, List<T>)`?

### Your doubts

#### Doubt 1: "I did not understand the assignment part to `List<Dog>` and `List<Cat>`."

> *I did not understand the assignment part to `List<Dog>` and `List<Cat>`.*

This was about these lines:

```java
List<? extends Animal> someAnimals = dogs;
someAnimals = cats;
```

`someAnimals` is a variable, a label, like any other. The first line points it at the list that
`dogs` points at. The second line **re-points** the same label at the list that `cats` points at,
exactly like `int score = 5;` then `score = 7;`. Nothing is copied, and `dogs` and `cats` don't
change.

Both lines are allowed because the label's type, `List<? extends Animal>`, means "I can point at a
list of any one Animal type". Dog is an Animal, and Cat is an Animal.

**Why it matters:** the same label can point at a Dogs list at one moment and a Cats list at
another, and sometimes which one is only decided while the program runs (`DecidedAtRuntime` in
Step 4). The compiler checks the code before it runs, so when it sees `someAnimals.add(...)` it
can't know which list is behind the label. Step 4 has the full programs and the before/after
pictures.

#### Doubt 2: "What is `CAP#1`, and where is it coming from?"

> *Also the CAP#1, what is it and from where is it coming?*

`CAP#1` is **not** something you wrote. It's a temporary name **the compiler invents** for "the
actual type behind this `?`", because `?` has no name and the compiler needs one to write the error.

- **CAP** = capture: the compiler "captures" the unknown type and names it.
- **#1** = the first invented name in this message. Two unknowns give `CAP#1` and `CAP#2`.
- `CAP#1 extends Animal from capture of ? extends Animal` = "CAP#1 is my name for the unknown type
  behind your `? extends Animal`. All I know about it is that it's Animal or below."

It's the compiler's own version of `T`. With `<T extends Animal>` the same mistake says
`Dog cannot be converted to T`. With `? extends Animal` it says `Dog cannot be converted to CAP#1`.
To read any CAP error, replace `CAP#1` with "the unknown type behind the `?`". Step 7 has the full
explanation and all the examples.

#### Doubt 3: "Is my understanding of why we can't add correct?"

> *I guess we cannot add anything to `List<? extends Animal>` because its type is undefined and it
> can be anything of the 3. From what I understand, for `List<? extends Animal> animal`:*
>
> - *Why can't `animal.add(cat)`? → it can be a dogs list.*
> - *Why can't I `animal.add(dog)`? → it can be a cats list.*
> - *Why can't I `animal.add(animal)`? → it can be a dogs list or a cats list.*

**Yes, that's exactly right.** Two small refinements make it fully precise:

1. **"Unknown", not "undefined".** When the program runs, the list behind the label really is one
   definite type, for example a `List<Dog>`. It's only the **compiler** that doesn't know which,
   because it checks the code before it runs.
2. **It isn't "one of 3".** The hidden type can be Animal, Dog, Cat, **or any other subtype**,
   including Puppy and any class someone writes next year. That's why even adding the most specific
   type is refused.

Your three reasons, completed with Puppy:

- `animal.add(cat)` is refused: the list might be a `List<Dog>` or a `List<Puppy>`.
- `animal.add(dog)` is refused: the list might be a `List<Cat>`, or a `List<Puppy>` (a plain Dog
  isn't a Puppy).
- `animal.add(new Animal(...))` is refused: the list might be Dogs, Cats or Puppies, and a plain
  Animal is none of them.
- `animal.add(puppy)` is refused too: the list might be a `List<Cat>`.

The general rule behind all four: an `add` is only allowed if the item would be safe in **every**
list the label could point at. No object (except `null`) fits a list of Dogs, a list of Cats and a
list of Puppies all at once, so nothing can be added. Cases B4 to B8 in Step 12 show each of these
compiling or failing.

#### Doubt 4: "What's the difference between `List<T extends Animal>` and `List<? extends Animal>`? Are they the same?"

> *What is the difference between `List<T extends Animal>` and `List<? extends Animal>`? Are these
> both the same? Please explain this difference with proper examples.*

First, a correction: `List<T extends Animal>` isn't valid Java (`> or ',' expected`). A bound on T
is written only where T is **declared**, in the `< >` before the return type:
`static <T extends Animal> void feedT(List<T> animals)`. So the real comparison is `List<T>` (with T
declared as `<T extends Animal>`) against `List<? extends Animal>`.

**They're the same in two ways:**

- They accept the same lists (`List<Animal>`, `List<Dog>`, `List<Cat>`, `List<Puppy>`), and you can
  read items as Animals.
- Neither lets you add a brand-new Dog (`Dog cannot be converted to T` / `... to CAP#1`).

**They're different in four ways, all because T has a name and `?` doesn't:**

1. **Return type.** `<T extends Animal> T firstT(List<T>)` returns a `Dog` for a list of Dogs. The
   wildcard version can only return `Animal`.
2. **Putting an item back.** With `List<T>`, `T first = animals.get(0); animals.add(first);` compiles,
   because `first` is the same T as the list. With the wildcard, the item comes out as just an
   `Animal`, and adding it back is refused.
3. **Tying two parameters together.** `<T extends Animal> moveFirst(List<T> from, List<T> to)`
   forces both lists to have the same T, so `moveFirst(dogs, cats)` is refused at the call. Two
   `List<? extends Animal>` parameters are two separate unknowns (`CAP#1`, `CAP#2`), so the method
   can't move items between them.
4. **Where you can write it.** `List<? extends Animal>` can be the type of any variable, even in
   `main`. `T` only exists inside the method or class that declared it (`cannot find symbol: class
   T`).

Rule of thumb: use a wildcard when the type is needed in **one** place, just to read. Use
`<T extends ...>` when the type must **connect** two places. Step 11 has every example as a full
program.

#### Doubt 5: "In A18, `? extends Dog` is CAP#1 and `? extends Animal` is CAP#2. CAP#1 ≠ CAP#2, so how is `y = x` allowed?"

> *A18: `List<? extends Dog> x = puppies;` then `List<? extends Animal> y = x;`.
> `<? extends Dog>` is CAP#1, `<? extends Animal>` is CAP#2. So why is `List<CAP#2> y = List<CAP#1> x`
> OK, when CAP#2 ≠ CAP#1?*

The assumption that **both** sides get a CAP is the part that's off. In an assignment, **only the
right side gets a CAP. The left side keeps its `?`.**

##### The rule: only a value being used gets captured

- **Right side** (`x`): this is a **value being used**. The compiler asks "what list is actually
  behind `x` right now?" It doesn't know, so it invents a name: `x` is a `List<CAP#1>`, where
  `CAP#1 extends Dog`.
- **Left side** (`y`'s declared type, `List<? extends Animal>`): this is **not** a value. It's the
  **rule** for what `y` may point at: "any list of Animal or below." A rule is a range of allowed
  types, not one specific unknown type, so there's nothing to capture and no CAP#2.

So the check the compiler really does is:

```text
   Can a List<CAP#1>  (CAP#1 = some type that is Dog or below)
   go into a label of type List<? extends Animal>  (rule: inner type must be Animal or below)?
```

- **Outer:** `List` and `List`. Yes.
- **Inner:** the rule asks "is CAP#1 Animal or below?" All the compiler knows is that CAP#1 is Dog
  or below, and every Dog is an Animal. So CAP#1 is **definitely** Animal or below. Yes.
- **Result:** allowed.

The compiler never needs CAP#1 to *equal* anything. It only needs CAP#1 to *fit the left side's
range*, and it does.

##### Proof from the compiler's own error messages

Make the same kind of line fail on purpose, so the compiler prints how it sees each side:

```java
import java.util.ArrayList;
import java.util.List;

public class OnlyRightSideCaptured {

    public static void main(String[] args) {
        List<Puppy> puppies = new ArrayList<>();
        List<? extends Dog> x = puppies;
        List<? extends Cat> y = x;
    }
}
```

**The variables:**

- `puppies` is declared as `List<Puppy>`. It's empty.
- `x` is declared as `List<? extends Dog>`. It points at the same list as `puppies`.
- `y` is declared as `List<? extends Cat>`. The last line tries to point it at the same list as `x`.

```text
OnlyRightSideCaptured.java:9: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Cat>
        List<? extends Cat> y = x;
                                ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Dog from capture of ? extends Dog
1 error
```

Look at the first line of the error:

- The **right** side is written as `List<CAP#1>`: captured.
- The **left** side is written as `List<? extends Cat>`: **still a `?`**, no CAP.
- There is only **one** CAP in the whole message.

It's refused because "is CAP#1 Cat or below?" can't be answered yes: CAP#1 is Dog or below, and no
Dog is a Cat. The same pattern was in A17 (`List<CAP#1> cannot be converted to List<Animal>`) and
A19 (`List<CAP#1> cannot be converted to List<? extends Dog>`): always CAP on the right, the
declared type unchanged on the left.

##### A18 running

```java
import java.util.ArrayList;
import java.util.List;

public class A18Works {

    public static void main(String[] args) {
        List<Puppy> puppies = new ArrayList<>();
        puppies.add(new Puppy("Bolt"));

        List<? extends Dog> x = puppies;
        List<? extends Animal> y = x;

        Animal first = y.get(0);
        first.eat();
        System.out.println(x == y);
    }
}
```

**The variables:**

- `puppies` is declared as `List<Puppy>`. It holds Bolt.
- `x` is declared as `List<? extends Dog>`. It points at the same list as `puppies`.
- `y` is declared as `List<? extends Animal>`. It points at the same list as `x`.
- `first` is declared as `Animal`. It gets the first item through `y`.

Output:

```text
Bolt is eating
true
```

`x == y` is `true`: all three labels point at **one** list of Puppies.

```text
   puppies  (label: List<Puppy>)               ──┐
   x        (label: List<? extends Dog>)       ──┼──►  [ Bolt (Puppy) ]
   y        (label: List<? extends Animal>)    ──┘
```

**Why this is safe:** through `y` you can only **read** items as Animals (true: a Puppy is an
Animal), and you **can't add** anything. So `y` can't break the Puppies list. Compare A17, where the
new label was a plain `List<Animal>`: that label would allow `add(cat)`, so it was refused.

##### Then when does `y` get a CAP?

As soon as `y` is **used** as a value, for example by calling a method on it:

```java
import java.util.ArrayList;
import java.util.List;

public class UsingYCaptures {

    public static void main(String[] args) {
        List<Puppy> puppies = new ArrayList<>();
        List<? extends Dog> x = puppies;
        List<? extends Animal> y = x;

        y.add(new Cat("Tom"));
    }
}
```

**The variables:** `puppies`, `x` and `y` are as above, all pointing at the one list of Puppies.
`new Cat("Tom")` is a Cat we try to add through `y`.

```text
UsingYCaptures.java:11: error: incompatible types: Cat cannot be converted to CAP#1
        y.add(new Cat("Tom"));
              ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Now `y` is the value being used, so **its** `?` is captured: `CAP#1 extends Animal`. (It's called
CAP#1 again because CAP numbers restart in every error message. They're not global names.) And
the add is refused, which is lucky, since the list is really Puppies.

##### Summary of this doubt

- A CAP is invented only for a **value being used**: the right side of an assignment, an argument
  being passed, or a variable you call a method on.
- The **declared type** of the variable or parameter receiving it keeps its `?`. It's a **range**
  ("Animal or below"), not one unknown type.
- So `y = x` isn't "CAP#2 = CAP#1". It's "**does CAP#1 fit the range Animal-or-below?**" CAP#1 is
  Dog or below, so yes.
- CAP numbers are local to each error message, so `CAP#1` in two different errors are unrelated.

#### Doubt 6: "Is my 'list of possibilities' way of checking correct?"

> *Let `puppies` be a `List<Puppy>`. `List<? extends Dog> x = puppies;` is true only if `puppies`
> is one of `List<Dog>` or `List<Puppy>`. Now x = `List<Puppy>`.
> `List<? extends Animal> y = x;` is true only if x is one of `List<Animal>`, `List<Dog>`,
> `List<Puppy>` or `List<Cat>`. It can be assigned since x = `List<Puppy>`.*
>
> *`someAnimals` = [`List<Animal>` animals, or `List<Dog>` dogs, or `List<Cat>` cats, or
> `List<Puppy>` puppies]. To add a Dog, it should work on all the possible values of `someAnimals`:*
> *1) animals can hold a Dog, possible. 2) dogs can hold dogs, possible. 3) but cats can't hold
> dogs. 4) puppies can't hold dogs.*

**Yes, this is the right way to think about it,** and the `add` part is exactly correct. One step
needs tightening: "it can be assigned **since x = `List<Puppy>`**". The compiler never uses that
fact.

##### What the compiler knows about `x`

After `List<? extends Dog> x = puppies;`, **you** know `x` points at a `List<Puppy>`. When the
program runs, that's true. But the compiler keeps **only the declared type**, `List<? extends Dog>`.
To the compiler, `x` is *any* of its possibilities: `List<Dog>` or `List<Puppy>` (or a list of any
other Dog subtype written later).

Proof: if the compiler remembered "x is a `List<Puppy>`", this would compile. It doesn't:

```java
import java.util.ArrayList;
import java.util.List;

public class CompilerForgetsPuppy {

    public static void main(String[] args) {
        List<Puppy> puppies = new ArrayList<>();
        List<? extends Dog> x = puppies;
        List<? extends Puppy> z = x;
    }
}
```

**The variables:**

- `puppies` is declared as `List<Puppy>`. It's empty.
- `x` is declared as `List<? extends Dog>`. It points at the same list as `puppies`.
- `z` is declared as `List<? extends Puppy>`: "a list of Puppy or below". The last line tries to
  point it at `x`'s list.

```text
CompilerForgetsPuppy.java:9: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Puppy>
        List<? extends Puppy> z = x;
                                  ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Dog from capture of ? extends Dog
1 error
```

The list really **is** Puppies, yet it's refused, because to the compiler `x` might be a `List<Dog>`,
and that's not "Puppy or below". `CAP#1 extends Dog` is literally all it knows.

##### The corrected reason for A18

`List<? extends Animal> y = x;` is allowed because **every** possibility for `x` is also a
possibility for `y`:

```text
   x could be:   List<Dog>, List<Puppy>
   y may be:     List<Animal>, List<Dog>, List<Cat>, List<Puppy>

   Is EVERY possibility of x in y's list?   List<Dog> ✓   List<Puppy> ✓   → allowed
```

So it would be allowed even if `x` pointed at a `List<Dog>`:

```java
import java.util.ArrayList;
import java.util.List;

public class XPointsAtDogs {

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Rex"));

        List<? extends Dog> x = dogs;
        List<? extends Animal> y = x;

        y.get(0).eat();
    }
}
```

**The variables:** `dogs` is a `List<Dog>` holding Rex. `x` (a `List<? extends Dog>`) and `y` (a
`List<? extends Animal>`) both point at that same list.

Output:

```text
Rex is eating
```

##### Your method as three rules

Your "list of possibilities" idea handles every wildcard question. Always list what the compiler
thinks a label **could** be, from its **declared type** only.

1. **Assigning** `y = x`: allowed only if **every** possibility of `x` is also a possibility of
   `y`.
2. **Adding** an item through a label: allowed only if the item fits in **every** possibility.
3. **Reading** an item as type R: allowed only if R is true for the items of **every** possibility.

Applying the rules to earlier cases:

- **A17**: `x` is a `List<? extends Animal>` (possibilities: `List<Animal>`, `List<Dog>`,
  `List<Cat>`, `List<Puppy>`). `List<Animal> y = x;`: `y` may only be a `List<Animal>`. Not every
  possibility fits (a `List<Dog>` doesn't). **Refused.**
- **A18**: `x` is a `List<? extends Dog>` (possibilities: `List<Dog>`, `List<Puppy>`).
  `List<? extends Animal> y = x;`: both fit. **Allowed.**
- **A19**: `x` is a `List<? extends Animal>` (possibilities: Animal, Dog, Cat, Puppy lists).
  `List<? extends Dog> y = x;`: `y` may only be a Dog or Puppy list. Animal and Cat lists don't fit.
  **Refused.**
- **Adding a Dog** to `someAnimals` (`List<? extends Animal>`): your four checks. The Animal list
  ✓, the Dog list ✓, the Cat list ✗, the Puppy list ✗. Not all fit. **Refused.**
- **Reading as Animal** from `someAnimals`: items of an Animal, Dog, Cat or Puppy list are all
  Animals. **Allowed.** Reading as Dog: items of a Cat list aren't Dogs. **Refused.**

One small addition to your lists: each one should end with "**...or a list of any other subtype
written later**". That's why the compiler can never treat the possibilities as a closed list of
three or four.

#### Doubt 7: "Explain the three rules with proper examples, with every variable named"

> *Can you explain these by taking proper examples? Name the variables here. I cannot refer
> backwards. Name everything and then explain.*

Everything below is self-contained. Each snippet is the body of `main` in its own file, with
`import java.util.ArrayList;` and `import java.util.List;` at the top, and every snippet was
compiled and run on its own.

##### The family

```text
                 Object
                    |
                 Animal          has: name(), eat()
                /      \
             Dog        Cat      Dog adds bark(), Cat adds meow()
              |
            Puppy                Puppy adds wagTail()
```

##### The variable names say their types

Every variable name in this doubt tells you its declared type:

| Name | Declared type | In words |
| --- | --- | --- |
| `animalList` | `List<Animal>` | a list of exactly Animal |
| `dogList` | `List<Dog>` | a list of exactly Dog |
| `catList` | `List<Cat>` | a list of exactly Cat |
| `puppyList` | `List<Puppy>` | a list of exactly Puppy |
| `objectList` | `List<Object>` | a list of exactly Object |
| `dogsOrBelow` | `List<? extends Dog>` | a list of Dog, or of something below Dog |
| `animalsOrBelow` | `List<? extends Animal>` | a list of Animal, or of something below Animal |
| `dogsOrAbove` | `List<? super Dog>` | a list of Dog, or of something above Dog |
| `puppiesOrAbove` | `List<? super Puppy>` | a list of Puppy, or of something above Puppy |
| `animalsOrAbove` | `List<? super Animal>` | a list of Animal, or of something above Animal |

##### Step 0: list a label's possibilities from its declared type

The compiler only knows a label's **declared type**, never which list you actually pointed it at.
So for every label, write down every kind of list it **could** be pointing at:

- `animalList` (`List<Animal>`) could be: **only** a `List<Animal>`. No wildcard means exactly one
  possibility.
- `dogList` (`List<Dog>`) could be: **only** a `List<Dog>`.
- `dogsOrBelow` (`List<? extends Dog>`) could be: `List<Dog>` or `List<Puppy>`, or a list of any
  Dog subtype written later.
- `animalsOrBelow` (`List<? extends Animal>`) could be: `List<Animal>`, `List<Dog>`, `List<Cat>` or
  `List<Puppy>`, or a list of any Animal subtype written later.
- `dogsOrAbove` (`List<? super Dog>`) could be: `List<Dog>`, `List<Animal>` or `List<Object>`.
- `puppiesOrAbove` (`List<? super Puppy>`) could be: `List<Puppy>`, `List<Dog>`, `List<Animal>` or
  `List<Object>`.
- `animalsOrAbove` (`List<? super Animal>`) could be: `List<Animal>` or `List<Object>`.

Now the three rules use these lists.

##### Rule 1: assigning `left = right`

> **Allowed only if every possibility of the right side is also a possibility of the left side.**

**Example 1.1 — allowed.**

```java
List<Puppy> puppyList = new ArrayList<>();
puppyList.add(new Puppy("Bolt"));

List<? extends Dog> dogsOrBelow = puppyList;
List<? extends Animal> animalsOrBelow = dogsOrBelow;

System.out.println(animalsOrBelow.size());
```

- `puppyList` is a `List<Puppy>` holding Bolt.
- `dogsOrBelow` is a `List<? extends Dog>`.
- `animalsOrBelow` is a `List<? extends Animal>`.

Line `dogsOrBelow = puppyList`:

```text
   right (puppyList) could be:    List<Puppy>
   left (dogsOrBelow) may be:     List<Dog>, List<Puppy>
   every right possibility on the left?   List<Puppy> ✓   → allowed
```

Line `animalsOrBelow = dogsOrBelow`. The right side is now `dogsOrBelow`, and the compiler uses
its **declared** possibilities, not "it's really Puppies":

```text
   right (dogsOrBelow) could be:     List<Dog>, List<Puppy>
   left (animalsOrBelow) may be:     List<Animal>, List<Dog>, List<Cat>, List<Puppy>
   every right possibility on the left?   List<Dog> ✓   List<Puppy> ✓   → allowed
```

Output:

```text
1
```

**Example 1.2 — refused.**

```java
List<Cat> catList = new ArrayList<>();
catList.add(new Cat("Tom"));

List<? extends Animal> animalsOrBelow = catList;
List<? extends Dog> dogsOrBelow = animalsOrBelow;
```

- `catList` is a `List<Cat>` holding Tom.
- `animalsOrBelow` is a `List<? extends Animal>`.
- `dogsOrBelow` is a `List<? extends Dog>`.

Line `animalsOrBelow = catList`: the right side could be `List<Cat>`, which is on the left's list.
Allowed.

Line `dogsOrBelow = animalsOrBelow`:

```text
   right (animalsOrBelow) could be:   List<Animal>, List<Dog>, List<Cat>, List<Puppy>
   left (dogsOrBelow) may be:         List<Dog>, List<Puppy>
   every right possibility on the left?   List<Animal> ✗   List<Cat> ✗   → refused
```

```text
Assign2.java:11: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Dog>
        List<? extends Dog> dogsOrBelow = animalsOrBelow;
                                          ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

Good thing: the list really holds Tom, a Cat, and `dogsOrBelow` would let you read him as a Dog.

**Example 1.3 — refused: wildcard into an exact type.**

```java
List<Dog> dogList = new ArrayList<>();
dogList.add(new Dog("Rex"));

List<? extends Animal> animalsOrBelow = dogList;
List<Animal> animalList = animalsOrBelow;
```

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`.
- `animalList` is a `List<Animal>`.

Line `animalList = animalsOrBelow`:

```text
   right (animalsOrBelow) could be:   List<Animal>, List<Dog>, List<Cat>, List<Puppy>
   left (animalList) may be:          List<Animal>   (only this one)
   every right possibility on the left?   List<Dog> ✗   List<Cat> ✗   List<Puppy> ✗   → refused
```

```text
Assign3.java:11: error: incompatible types: List<CAP#1> cannot be converted to List<Animal>
        List<Animal> animalList = animalsOrBelow;
                                  ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

Good thing: `animalList.add(new Cat(...))` would have put a Cat into Rex's list of Dogs.

**Example 1.4 — allowed: exact type into a wildcard.**

```java
List<Dog> dogList = new ArrayList<>();
dogList.add(new Dog("Rex"));

List<? extends Animal> animalsOrBelow = dogList;
System.out.println(animalsOrBelow.size());
```

```text
   right (dogList) could be:          List<Dog>   (only this one)
   left (animalsOrBelow) may be:      List<Animal>, List<Dog>, List<Cat>, List<Puppy>
   every right possibility on the left?   List<Dog> ✓   → allowed
```

Output:

```text
1
```

**Example 1.5 — refused: two exact types that differ.** This is the original "a `List<Dog>` is not
a `List<Animal>`" rule, seen through Rule 1.

```java
List<Dog> dogList = new ArrayList<>();
List<Animal> animalList = dogList;
```

```text
   right (dogList) could be:      List<Dog>
   left (animalList) may be:      List<Animal>
   every right possibility on the left?   List<Dog> ✗   → refused
```

```text
Assign5.java:8: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        List<Animal> animalList = dogList;
                                  ^
1 error
```

**Example 1.6 — allowed, with `super`.**

```java
List<Animal> animalList = new ArrayList<>();

List<? super Dog> dogsOrAbove = animalList;
List<? super Puppy> puppiesOrAbove = dogsOrAbove;

System.out.println(puppiesOrAbove.size());
```

- `animalList` is a `List<Animal>`, empty.
- `dogsOrAbove` is a `List<? super Dog>`.
- `puppiesOrAbove` is a `List<? super Puppy>`.

Line `puppiesOrAbove = dogsOrAbove`:

```text
   right (dogsOrAbove) could be:      List<Dog>, List<Animal>, List<Object>
   left (puppiesOrAbove) may be:      List<Puppy>, List<Dog>, List<Animal>, List<Object>
   every right possibility on the left?   List<Dog> ✓   List<Animal> ✓   List<Object> ✓   → allowed
```

Output:

```text
0
```

**Example 1.7 — refused, with `super`.**

```java
List<Dog> dogList = new ArrayList<>();

List<? super Dog> dogsOrAbove = dogList;
List<? super Animal> animalsOrAbove = dogsOrAbove;
```

- `dogList` is a `List<Dog>`, empty.
- `dogsOrAbove` is a `List<? super Dog>`.
- `animalsOrAbove` is a `List<? super Animal>`.

Line `animalsOrAbove = dogsOrAbove`:

```text
   right (dogsOrAbove) could be:      List<Dog>, List<Animal>, List<Object>
   left (animalsOrAbove) may be:      List<Animal>, List<Object>
   every right possibility on the left?   List<Dog> ✗   → refused
```

```text
Assign7.java:10: error: incompatible types: List<CAP#1> cannot be converted to List<? super Animal>
        List<? super Animal> animalsOrAbove = dogsOrAbove;
                                              ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

Good thing: `animalsOrAbove.add(new Cat(...))` is allowed for "Animal or above" lists, and it would
have put a Cat into the list of Dogs.

##### Rule 2: adding an item through a label

> **Allowed only if the item fits in every possibility of the label.**

**Example 2.1 — a `? extends Animal` label: every add is refused.**

```java
List<Dog> dogList = new ArrayList<>();
dogList.add(new Dog("Rex"));
List<? extends Animal> animalsOrBelow = dogList;
```

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at the same list as `dogList`.

`animalsOrBelow` could be a `List<Animal>`, `List<Dog>`, `List<Cat>` or `List<Puppy>`. Each item is
checked against all four:

```text
   item            List<Animal>   List<Dog>   List<Cat>   List<Puppy>   result
   new Dog(..)          ✓             ✓           ✗           ✗         refused
   new Cat(..)          ✓             ✗           ✓           ✗         refused
   new Puppy(..)        ✓             ✓           ✗           ✓         refused
   new Animal(..)       ✓             ✗           ✗           ✗         refused
```

Each one has at least one ✗, so each is refused. For example, adding the Dog:

```java
animalsOrBelow.add(new Dog("Bruno"));
```

```text
AddE1.java:11: error: incompatible types: Dog cannot be converted to CAP#1
        animalsOrBelow.add(new Dog("Bruno"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The Cat, Puppy and Animal versions give the same error, with `Cat`, `Puppy` or `Animal` in place
of `Dog`.

**Example 2.2 — a `? super Dog` label: Dogs and Puppies are allowed.**

```java
List<Animal> animalList = new ArrayList<>();
animalList.add(new Cat("Kitty"));
List<? super Dog> dogsOrAbove = animalList;

dogsOrAbove.add(new Dog("Rex"));
dogsOrAbove.add(new Puppy("Bolt"));
System.out.println(animalList.size());
```

- `animalList` is a `List<Animal>` holding Kitty, a Cat.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at the same list as `animalList`.

`dogsOrAbove` could be a `List<Dog>`, `List<Animal>` or `List<Object>`:

```text
   item            List<Dog>   List<Animal>   List<Object>   result
   new Dog(..)        ✓             ✓              ✓          allowed
   new Puppy(..)      ✓             ✓              ✓          allowed
   new Animal(..)     ✗             ✓              ✓          refused
   new Cat(..)        ✗             ✓              ✓          refused
```

Output (Kitty plus the two new ones):

```text
3
```

Adding the Animal or the Cat is refused, because the list might be a `List<Dog>`:

```java
dogsOrAbove.add(new Animal("Generic"));
```

```text
AddS2.java:11: error: incompatible types: Animal cannot be converted to CAP#1
        dogsOrAbove.add(new Animal("Generic"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```java
dogsOrAbove.add(new Cat("Tom"));
```

```text
AddS3.java:11: error: incompatible types: Cat cannot be converted to CAP#1
        dogsOrAbove.add(new Cat("Tom"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

**Example 2.3 — an exact `List<Animal>`: one possibility, so any Animal fits.**

```java
List<Animal> animalList = new ArrayList<>();
animalList.add(new Dog("Rex"));
animalList.add(new Cat("Tom"));
animalList.add(new Animal("Generic"));
System.out.println(animalList.size());
```

`animalList` could only be a `List<Animal>`. A Dog, a Cat and a plain Animal each fit it.

Output:

```text
3
```

##### Rule 3: reading an item as type R

> **Allowed only if R is true for the items of every possibility.**

**Example 3.1 — a `? extends Animal` label: read as Animal or Object, not Dog.**

```java
List<Dog> dogList = new ArrayList<>();
dogList.add(new Dog("Rex"));
List<? extends Animal> animalsOrBelow = dogList;

Animal firstAnimal = animalsOrBelow.get(0);
Object firstObject = animalsOrBelow.get(0);
firstAnimal.eat();
System.out.println(firstObject.getClass().getSimpleName());
```

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at the same list.
- `firstAnimal` is an `Animal`. `firstObject` is an `Object`.

`animalsOrBelow` could be a `List<Animal>`, `List<Dog>`, `List<Cat>` or `List<Puppy>`. What's true of
the items in each?

```text
   read as        Animal items   Dog items   Cat items   Puppy items   result
   Animal              ✓             ✓           ✓            ✓        allowed
   Object              ✓             ✓           ✓            ✓        allowed
   Dog                 ✗             ✓           ✗            ✓        refused
```

Output:

```text
Rex is eating
Dog
```

Reading as `Dog` is refused, even though Rex really is a Dog:

```java
Dog firstDog = animalsOrBelow.get(0);
```

```text
ReadE2.java:11: error: incompatible types: CAP#1 cannot be converted to Dog
        Dog firstDog = animalsOrBelow.get(0);
                                         ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

**Example 3.2 — a `? super Dog` label: read only as Object.**

```java
List<Object> objectList = new ArrayList<>();
objectList.add("A1");
List<? super Dog> dogsOrAbove = objectList;

Object firstObject = dogsOrAbove.get(0);
System.out.println(firstObject);
```

- `objectList` is a `List<Object>` holding the String `"A1"`.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at the same list.
- `firstObject` is an `Object`.

`dogsOrAbove` could be a `List<Dog>`, `List<Animal>` or `List<Object>`. What's true of the items in
each?

```text
   read as        Dog items   Animal items   Object items (anything, e.g. "A1")   result
   Object             ✓            ✓                  ✓                           allowed
   Animal             ✓            ✓                  ✗                           refused
   Dog                ✓            ✗                  ✗                           refused
```

Output:

```text
A1
```

The first item really is a String, which proves the `Object items` column is real. Reading as Animal
or as Dog is refused:

```java
Animal firstAnimal = dogsOrAbove.get(0);
```

```text
ReadS2.java:11: error: incompatible types: CAP#1 cannot be converted to Animal
        Animal firstAnimal = dogsOrAbove.get(0);
                                            ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

```java
Dog firstDog = dogsOrAbove.get(0);
```

```text
ReadS3.java:11: error: incompatible types: CAP#1 cannot be converted to Dog
        Dog firstDog = dogsOrAbove.get(0);
                                      ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

##### The pattern that falls out of the three rules

- **`? extends X` labels** (like `animalsOrBelow`): Rule 3 lets you read as X. Rule 2 refuses every
  add, because the possibilities go **down** to many different subtypes and no one item fits them
  all.
- **`? super X` labels** (like `dogsOrAbove`): Rule 2 lets you add X (and its subtypes). Rule 3
  only lets you read as Object, because the possibilities go **up** to `List<Object>`, which can
  hold anything.
- **Exact labels** (like `animalList`): one possibility, so you can add any X and read as X. But
  Rule 1 then needs the other side to be exactly the same type.

That's the reason behind the memory aid "produce extends, consume super": a label you **read from**
(it produces items) should be `extends`, and a label you **add to** (it consumes items) should be
`super`.

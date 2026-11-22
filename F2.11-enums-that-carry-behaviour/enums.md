# Task F2.11 — Enums that carry behaviour

Beginner-friendly study notes for Task F2.11 of the Zenith plan. We go through the concepts first,
one at a time, then do the subtasks. Every code example and output below was compiled and run on
Java 21.

## Progress

### Concepts

- [x] Concept 1 — What an enum is, and why it beats a `String`
- [x] Concept 2 — The methods every enum gets for free: `name()`, `ordinal()`, `values()`, `valueOf()`
- [x] Concept 3 — `switch` over an enum: the old statement versus the new expression
- [x] Concept 4 — Enums with fields and a constructor
- [x] Concept 5 — Methods on an enum, and per-constant behaviour
- [x] Concept 6 — `EnumSet` and `EnumMap`
- [x] Concept 7 — A state machine is just states plus allowed moves
- [x] Concept 8 — Saving enums to a database: `ORDINAL` versus `STRING`
- [x] Concept 9 — When an enum is the wrong choice

### Subtasks

- [x] Subtask 1 — `OrderStatus` and a `switch` expression that refuses to compile
- [x] Subtask 2 — `TicketTier` with fields and a constructor
- [ ] Subtask 3 — Per-constant behaviour that deletes a `switch`
- [ ] Subtask 4 — `canTransitionTo` backed by `EnumSet`
- [ ] Subtask 5 — Swap a `HashMap` for an `EnumMap`
- [ ] Subtask 6 — The persistence rule
- [ ] Subtask 7 — Ask AI: when is an enum the wrong choice?

## What this task is about

Zenith has lots of things that can only ever be one of a few fixed values: an order is `DRAFT`,
`PENDING_PAYMENT`, `CONFIRMED` or `CANCELLED`, and nothing else. Java's tool for that is the
**enum**. This task shows that an enum is more than a list of names. It can hold data and
behaviour, which lets you delete `switch` statements that would otherwise be copied around the
codebase. It ends with one rule about saving enums to a database that prevents a silent, nasty
production bug.

## Where things live

- **This file:** the reference notes for this task.
- **`../NOTES.md`:** your own notes, in your own words. The plan asks for these.
- **Your code:** type the examples into `.java` files in this folder.

Each example is one complete file. The file name must match the `public class` name inside it.
To run one, for example `LightWithEnum.java`:

```bash
javac LightWithEnum.java
java LightWithEnum
```

`javac` checks and compiles the file. If there's a mistake, it prints an error here and stops.
`java` runs it.

---

## Concept 1 — What an enum is, and why it beats a `String`

We'll use a traffic light because everyone knows it: a light can only be **RED**, **YELLOW** or
**GREEN**. Never anything else.

### Step 1: Store the light as a `String`

```java
public class LightWithString {

    public static void main(String[] args) {
        String light = "GREEN";

        if (light.equals("GREEN")) {
            System.out.println("Go");
        } else {
            System.out.println("Stop");
        }
    }
}
```

Output:

```text
Go
```

This works. Nothing wrong yet.

### Step 2: Make one small typo

Change only one line: `"GREEN"` becomes `"GREN"`.

```java
public class LightWithStringBug {

    public static void main(String[] args) {
        String light = "GREN";           // typo

        if (light.equals("GREEN")) {
            System.out.println("Go");
        } else {
            System.out.println("Stop");
        }
    }
}
```

Output:

```text
Stop
```

The light is meant to be green, but the program says **Stop**. And notice:

- `javac` gave **no error**.
- The program **didn't crash**.
- It just quietly gave the wrong answer.

Why? A `String` can hold *any* text: `"GREN"`, `"green"`, `"BANANA"`. Java has no idea that only
three words make sense for a traffic light. That's the problem.

### Step 3: Make a list of the only allowed values

What we want is to tell Java: "a light is one of these three things, and nothing else." That's
exactly what an **enum** is.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}
```

Read it line by line:

| Code | What it means |
| --- | --- |
| `enum` | "I'm making a new type, and I'll list every possible value of it." |
| `Light` | The name of the new type. Just like `String` or `int` are types, `Light` is now a type. |
| `RED, YELLOW, GREEN` | The *only* three values a `Light` can ever be. Each one is called a **constant**. |

Constants are written in CAPITAL LETTERS by convention.

### Step 4: Use the enum

Same program as step 1, but using `Light` instead of `String`:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class LightWithEnum {

    public static void main(String[] args) {
        Light light = Light.GREEN;

        if (light == Light.GREEN) {
            System.out.println("Go");
        } else {
            System.out.println("Stop");
        }
    }
}
```

Output:

```text
Go
```

Compare the two versions:

| With `String` | With `enum` |
| --- | --- |
| `String light = "GREEN";` | `Light light = Light.GREEN;` |
| `light.equals("GREEN")` | `light == Light.GREEN` |

- `Light light` means "this variable can only hold a `Light`."
- `Light.GREEN` means "the `GREEN` value from the `Light` list."
- We compare with `==`. Step 6 explains why that's fine for enums.

### Step 5: Make the same typo again

```java
Light light = Light.GREN;        // typo
```

This time `javac` refuses to build the program:

```text
LightWithEnumBug.java:10: error: cannot find symbol
        Light light = Light.GREN;
                           ^
  symbol:   variable GREN
  location: class Light
```

In plain English: "There's no `GREN` in the `Light` list."

And if you try to sneak a `String` in:

```java
Light light = "GREEN";
```

```text
LightWrongType.java:10: error: incompatible types: String cannot be converted to Light
```

In plain English: "That's text, not a `Light`."

**This is the main reason enums exist.** With a `String`, the typo gave a wrong answer at runtime,
silently. With an enum, the typo is caught *before the program even runs*.

### Step 6: Each constant is one object, made once

Here's the part that makes the rest of this task possible.

`Light.GREEN` is not the text `"GREEN"`. It's an **object**, just like the objects you made from
classes in Task F2.2. When your program starts using `Light`, Java creates exactly three objects,
one for each constant, and never creates any more.

Think of it as a box that holds exactly three toys. You can point at a toy as often as you like, but
nobody can add a fourth toy to the box.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class LightIsObject {

    public static void main(String[] args) {
        Light a = Light.GREEN;
        Light b = Light.GREEN;

        System.out.println(a);
        System.out.println(a == b);
    }
}
```

Output:

```text
GREEN
true
```

- `System.out.println(a)` prints `GREEN`: printing an enum shows its name.
- `a == b` is `true`. `a` and `b` aren't two copies; both point at **the same single** `GREEN`
  object.

That's why `==` is the right way to compare enums. In Task F2.8, `==` was usually wrong because two
separate objects could hold the same data. With enums there's only ever **one** `GREEN` object, so
"is it the same object?" is exactly the question you want to ask.

And you can't make a fourth light yourself:

```java
Light light = new Light();
```

```text
LightNew.java:10: error: enum classes may not be instantiated
```

In plain English: "You're not allowed to use `new` on an enum." The list you wrote is final.

### Why "it's an object" matters

Because each constant is a real object, it can carry things, just like objects from normal classes:

- **data**, for example `GREEN` could store "wait 30 seconds" (Concept 4)
- **behaviour**, for example `GREEN` could have a method that answers "can cars go?" (Concept 5)

That's what the task title, "enums that carry behaviour", means. We'll build up to it.

### Summary

1. Some values can only be one of a few fixed choices (a traffic light, an order status).
2. Storing them as a `String` lets typos through silently.
3. An `enum` lists every allowed value. Anything else is a compile error.
4. Each constant (`Light.GREEN`) is a single object that Java creates once. You can't create more.
5. Compare enums with `==`.

---

## Concept 2 — The methods every enum gets for free: `name()`, `ordinal()`, `values()`, `valueOf()`

When you write an enum, Java quietly gives it some ready-made methods. You don't write them, you just
use them. There are four you need to know.

We keep using the same traffic light:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}
```

| Method | Question it answers | Example | Result |
| --- | --- | --- | --- |
| `name()` | "What is this constant called, as text?" | `Light.GREEN.name()` | `"GREEN"` |
| `ordinal()` | "What position is it in the list?" (counting from 0) | `Light.GREEN.ordinal()` | `2` |
| `values()` | "Give me all the constants." | `Light.values()` | `[RED, YELLOW, GREEN]` |
| `valueOf(text)` | "Find the constant with this name." | `Light.valueOf("GREEN")` | `Light.GREEN` |

The first two are asked **of one constant** (`Light.GREEN.name()`). The last two are asked **of the
whole enum** (`Light.values()`).

### Method 1: `name()` — the constant as text

Turns an enum value into a `String`.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class NameDemo {

    public static void main(String[] args) {
        Light light = Light.GREEN;

        String text = light.name();

        System.out.println(text);
        System.out.println(text.length());
    }
}
```

Output:

```text
GREEN
5
```

- `light.name()` gives back the `String` `"GREEN"`, exactly as you typed it in the enum.
- It's a real `String`, so you can do `String` things with it, like `.length()` (5 letters).

**When you'd use it:** whenever you need to send the value somewhere that only understands text:
saving to a database, writing to a file, sending in JSON.

### Method 2: `ordinal()` — the position in the list

Tells you where the constant sits in the list. Counting starts at **0**, like array indexes.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class OrdinalDemo {

    public static void main(String[] args) {
        System.out.println(Light.RED.ordinal());
        System.out.println(Light.YELLOW.ordinal());
        System.out.println(Light.GREEN.ordinal());
    }
}
```

Output:

```text
0
1
2
```

```text
enum Light {  RED,   YELLOW,   GREEN  }
position:      0       1         2
```

#### The danger with `ordinal()`

The position depends only on **where you typed the constant**. Watch what happens if someone later
adds `ORANGE` between `RED` and `YELLOW`:

```java
enum Light {
    RED,
    ORANGE,
    YELLOW,
    GREEN
}

public class OrdinalTrapDemo {

    public static void main(String[] args) {
        System.out.println(Light.RED.ordinal());
        System.out.println(Light.ORANGE.ordinal());
        System.out.println(Light.YELLOW.ordinal());
        System.out.println(Light.GREEN.ordinal());
    }
}
```

Output:

```text
0
1
2
3
```

| Constant | Position before | Position after adding `ORANGE` |
| --- | --- | --- |
| `RED` | 0 | 0 |
| `ORANGE` | — | 1 |
| `YELLOW` | 1 | **2** |
| `GREEN` | 2 | **3** |

`GREEN` used to be `2`. Now it's `3`. And `2` now means `YELLOW`.

Imagine you had saved the number `2` somewhere yesterday to mean "green". Today that same `2` reads
back as `YELLOW`. No error, no crash, just the wrong answer. That's exactly the database bug in
Concept 8.

**Rule:** use `ordinal()` almost never. Never save it anywhere. If you need to store or send an enum,
use `name()`.

### Method 3: `values()` — all the constants

Gives you every constant, in the order you wrote them, as an array.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class ValuesDemo {

    public static void main(String[] args) {
        Light[] allLights = Light.values();

        System.out.println("How many lights: " + allLights.length);

        for (Light light : allLights) {
            System.out.println(light);
        }
    }
}
```

Output:

```text
How many lights: 3
RED
YELLOW
GREEN
```

- `Light.values()` returns a `Light[]`, an array of `Light`.
- `allLights.length` is `3`, because there are three constants.
- The `for` loop goes through each one and prints it.

**When you'd use it:** showing every option to a user (a dropdown of order statuses), or checking
something for every constant. If someone adds a new constant later, `values()` includes it
automatically, so the loop doesn't need changing.

### Method 4: `valueOf(text)` — find a constant by its name

The opposite of `name()`. You give it text, it gives you back the matching constant.

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class ValueOfDemo {

    public static void main(String[] args) {
        String textFromUser = "GREEN";

        Light light = Light.valueOf(textFromUser);

        System.out.println(light);
        System.out.println(light == Light.GREEN);
    }
}
```

Output:

```text
GREEN
true
```

- We start with a `String`, `"GREEN"`.
- `Light.valueOf("GREEN")` finds the `GREEN` constant.
- `light == Light.GREEN` is `true`: it's the same single `GREEN` object from Concept 1.

`name()` and `valueOf()` are a round trip:

```text
Light.GREEN  --name()-->  "GREEN"  --valueOf()-->  Light.GREEN
```

#### What if the text doesn't match?

`valueOf` must match the name **exactly**, including capital letters:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class ValueOfBadDemo {

    public static void main(String[] args) {
        Light light = Light.valueOf("green");

        System.out.println(light);
    }
}
```

Output:

```text
Exception in thread "main" java.lang.IllegalArgumentException: No enum constant Light.green
	at java.base/java.lang.Enum.valueOf(Enum.java:293)
	at Light.valueOf(ValueOfBadDemo.java:1)
	at ValueOfBadDemo.main(ValueOfBadDemo.java:10)
```

`"green"` (lowercase) isn't `GREEN`, so the program **crashes loudly** with an
`IllegalArgumentException` ("bad input").

That sounds bad, but it's actually what you want. Remember the `String` version from Concept 1,
where `"GREN"` silently gave the wrong answer? Here, bad text is caught immediately with a clear
message saying exactly what went wrong.

**When you'd use it:** turning text that came from outside (a web request, a database row, a file)
back into an enum.

### A note on printing

`System.out.println(light)` prints `GREEN`. Behind the scenes `println` calls `toString()`, and for
an enum, `toString()` returns the same thing as `name()` unless you change it. When you specifically
need the exact constant name (for example, to save it), call `name()`, because it can never be
changed.

### Summary

1. `name()` turns a constant into text: `Light.GREEN.name()` is `"GREEN"`.
2. `ordinal()` is its position, starting at 0. It changes if someone reorders or inserts constants,
   so never save it.
3. `values()` gives all constants as an array, in the order written.
4. `valueOf("GREEN")` turns text back into a constant. It must match exactly, otherwise it throws
   `IllegalArgumentException`.

---

## Concept 3 — `switch` over an enum: the old statement versus the new expression

Very often you want to do something different for each constant: RED means "Stop", YELLOW means
"Slow down", GREEN means "Go". This concept shows three ways to write that, and why the last one is
the safest.

### Step 1: The `if / else if` way

You already know this:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class IfElseDemo {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        if (light == Light.RED) {
            System.out.println("Stop");
        } else if (light == Light.YELLOW) {
            System.out.println("Slow down");
        } else if (light == Light.GREEN) {
            System.out.println("Go");
        }
    }
}
```

Output:

```text
Slow down
```

It works, but you repeat `light == Light.` on every line. A `switch` is a shorter way to say
"look at this one value and pick a branch".

### Step 2: The old `switch` statement (with `case X:` and `break`)

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class OldSwitchDemo {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        switch (light) {
            case RED:
                System.out.println("Stop");
                break;
            case YELLOW:
                System.out.println("Slow down");
                break;
            case GREEN:
                System.out.println("Go");
                break;
        }
    }
}
```

Output:

```text
Slow down
```

Read it like this:

| Code | Meaning |
| --- | --- |
| `switch (light)` | "Look at the value of `light`." |
| `case YELLOW:` | "If it's `YELLOW`, start running from here." |
| `break;` | "Stop, and jump out of the `switch`." |

Note: inside the `switch` you write `case RED`, not `case Light.RED`. Java already knows `light` is
a `Light`.

This old style has **two traps**.

#### Trap 1: Forget a `break` and it falls into the next case

Same program, but the `break` after "Slow down" is missing:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class OldSwitchNoBreak {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        switch (light) {
            case RED:
                System.out.println("Stop");
                break;
            case YELLOW:
                System.out.println("Slow down");
                                                 // forgot break here
            case GREEN:
                System.out.println("Go");
                break;
        }
    }
}
```

Output:

```text
Slow down
Go
```

The light is yellow, but it printed **both** "Slow down" and "Go". Without `break`, Java keeps
running straight into the next case. This is called **fall-through**. It compiles with no error.

#### Trap 2: Forget a whole case and nothing tells you

Now we want to store the message in a variable, and we forgot the `GREEN` case completely:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class OldSwitchMissingCase {

    public static void main(String[] args) {
        Light light = Light.GREEN;

        String message = "UNKNOWN";

        switch (light) {
            case RED:
                message = "Stop";
                break;
            case YELLOW:
                message = "Slow down";
                break;
                                       // GREEN is missing
        }

        System.out.println(message);
    }
}
```

Output:

```text
UNKNOWN
```

The light is green and the program printed `UNKNOWN`. It **compiled fine** and failed only when it
ran. The old `switch` statement doesn't care whether you handled every constant.

This is exactly what happens in a real codebase. Someone adds a new constant to an enum, and every
old-style `switch` that should handle it just silently skips it.

### Step 3: The new `switch` expression (with `->`)

Since Java 14 there's a better form. Same logic:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class NewSwitchDemo {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        String message = switch (light) {
            case RED    -> "Stop";
            case YELLOW -> "Slow down";
            case GREEN  -> "Go";
        };

        System.out.println(message);
    }
}
```

Output:

```text
Slow down
```

What's different:

| Old statement | New expression |
| --- | --- |
| `case RED:` | `case RED ->` |
| needs `break;` on every case | no `break`, ever. Only the matching line runs |
| sets a variable inside each case | the whole `switch` **produces a value**: `String message = switch (...) { ... };` |
| missing case → compiles, wrong at runtime | missing case → **won't compile** |

Two words to know:

- A **statement** *does* something (like printing or setting a variable).
- An **expression** *produces a value* (like `2 + 3` produces `5`). The new `switch` produces a
  `String` here, which goes straight into `message`. Note the `;` after the closing `}`, because the
  whole thing is one assignment.

No `break` means **Trap 1 is gone**. Now look at Trap 2.

### Step 4: Delete a case and watch it fail to compile

Remove the `GREEN` line:

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class NewSwitchMissingCase {

    public static void main(String[] args) {
        Light light = Light.GREEN;

        String message = switch (light) {
            case RED    -> "Stop";
            case YELLOW -> "Slow down";
        };

        System.out.println(message);
    }
}
```

```text
NewSwitchMissingCase.java:12: error: the switch expression does not cover all possible input values
        String message = switch (light) {
                         ^
```

In plain English: "If `light` is `GREEN`, what should `message` be? You didn't tell me, so I won't
build this."

The reason is simple: the `switch` must produce a value for `message`, so it **has to have an
answer for every possible input**. Java checks that every constant is covered. This is called
**exhaustiveness** ("you covered everything").

### Step 5: The real win — adding a new constant

This is the situation that matters in real projects. Someone adds `FLASHING` to the enum but
doesn't touch the `switch`:

```java
enum Light {
    RED,
    YELLOW,
    GREEN,
    FLASHING
}

public class NewSwitchNewConstant {

    public static void main(String[] args) {
        Light light = Light.GREEN;

        String message = switch (light) {
            case RED    -> "Stop";
            case YELLOW -> "Slow down";
            case GREEN  -> "Go";
        };

        System.out.println(message);
    }
}
```

```text
NewSwitchNewConstant.java:13: error: the switch expression does not cover all possible input values
```

The compiler points at **every** `switch` expression that needs updating. With the old statement
style, all those places would have compiled and quietly done the wrong thing.

### Step 6: Don't add `default` to an enum `switch` expression

You might be tempted to "fix" the error with `default`, which means "anything else":

```java
enum Light {
    RED,
    YELLOW,
    GREEN,
    FLASHING
}

public class NewSwitchDefault {

    public static void main(String[] args) {
        Light light = Light.FLASHING;

        String message = switch (light) {
            case RED    -> "Stop";
            case YELLOW -> "Slow down";
            default     -> "Go";
        };

        System.out.println(message);
    }
}
```

Output:

```text
Go
```

It compiles, and a **flashing** light now says "Go". `default` catches everything you didn't list,
including constants added in the future, so the compiler can no longer warn you. You've thrown away
the safety from Step 5.

**Rule:** when switching over an enum, list every constant and leave out `default`.

### Summary

1. A `switch` picks a branch based on one value. With enums you write `case RED`, not
   `case Light.RED`.
2. The old statement (`case RED:` + `break`) has two silent traps: forgetting `break` runs the next
   case too, and forgetting a case compiles fine.
3. The new expression (`case RED ->`) has no `break` and produces a value:
   `String message = switch (light) { ... };`
4. A `switch` expression must cover every constant, or it won't compile. Adding a new constant
   makes every unfinished `switch` fail to compile, which is exactly what you want.
5. Don't use `default` in an enum `switch` expression: it turns that safety off.

---

## Concept 4 — Enums with fields and a constructor

In Concept 1 we said each constant is an object. Objects can hold data. So each constant can hold
its own data: RED can remember "Stop" and "30 seconds", GREEN can remember "Go" and "25 seconds".

### Step 1: Reminder — fields and a constructor in a normal class

From Task F2.2:

```java
class Person {
    private final String name;
    private final int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    String getName() {
        return name;
    }

    int getAge() {
        return age;
    }
}

public class PersonDemo {

    public static void main(String[] args) {
        Person p = new Person("Ravi", 30);

        System.out.println(p.getName());
        System.out.println(p.getAge());
    }
}
```

Output:

```text
Ravi
30
```

- **Fields** (`name`, `age`) are the data each object holds.
- The **constructor** (`Person(...)`) runs when the object is created and fills in the fields.
- `new Person("Ravi", 30)` creates the object and passes the values to the constructor.
- **Getters** (`getName()`, `getAge()`) let other code read the fields.

An enum with data uses exactly these same pieces.

### Step 2: The same pieces inside an enum

```java
enum Light {
    RED("Stop", 30),
    YELLOW("Slow down", 5),
    GREEN("Go", 25);

    private final String message;
    private final int seconds;

    Light(String message, int seconds) {
        this.message = message;
        this.seconds = seconds;
    }

    String getMessage() {
        return message;
    }

    int getSeconds() {
        return seconds;
    }
}

public class LightWithData {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        System.out.println(light);
        System.out.println(light.getMessage());
        System.out.println(light.getSeconds());
    }
}
```

Output:

```text
YELLOW
Slow down
5
```

The enum has four parts, in this order:

| Part | Code | Meaning |
| --- | --- | --- |
| 1. Constants with values | `RED("Stop", 30),` | "Create RED, passing `"Stop"` and `30` to the constructor." |
| 2. Fields | `private final String message;` | The data every constant holds. |
| 3. Constructor | `Light(String message, int seconds) { ... }` | Fills in the fields for each constant. |
| 4. Getters | `String getMessage()` | Lets other code read the data. |

Compare with `Person`:

| Normal class | Enum |
| --- | --- |
| `new Person("Ravi", 30)` written wherever you want an object | `RED("Stop", 30)` written **once**, inside the enum |
| you can make as many `Person`s as you like | there are exactly three `Light`s, forever |

`RED("Stop", 30)` is basically "`RED = new Light("Stop", 30)`", except you don't write `new` and
Java does it for you.

Why the fields are `final`: there's only one RED object in the whole program, shared by everyone. If
one piece of code could change RED's message, every other piece of code would suddenly see the
change. So enum fields should always be `final`, set once and never changed.

### Step 3: The semicolon after the last constant

When an enum has nothing but constants, you can write `enum Light { RED, YELLOW, GREEN }`. Once you
add fields or methods, the list of constants **must end with a `;`**:

```java
    GREEN("Go", 25);     // <-- this semicolon
```

Forget it and you get a confusing pile of errors:

```text
MissingSemicolon.java:4: error: ',', '}', or ';' expected
    GREEN("Go")
               ^
MissingSemicolon.java:8: error: enum constant expected here
    ...
```

Java thought the list of constants was still going and tried to read the constructor as another
constant. If you see "enum constant expected here", check for that semicolon first.

### Step 4: Every constant must pass the values

The constructor asks for a `String`. If one constant doesn't pass one:

```java
enum Light {
    RED("Stop"),
    YELLOW,             // forgot the message
    GREEN("Go");
    ...
}
```

```text
MissingArg.java:3: error: constructor Light in enum Light cannot be applied to given types;
    YELLOW,
    ^
  required: String
  found:    no arguments
```

In plain English: "The constructor needs a `String`, but `YELLOW` gave nothing." You can't forget to
give a constant its data.

### Step 5: The constructor is automatically private

Try to make the constructor `public`:

```java
    public Light(String message) {
        this.message = message;
    }
```

```text
PublicConstructor.java:8: error: modifier public not allowed here
    public Light(String message) {
           ^
```

And try to create a new light from outside:

```java
Light purple = new Light("Party");
```

```text
NewWithArgs.java:16: error: enum classes may not be instantiated
```

An enum constructor is **always private**, even when you don't write `private`. Only the constants
listed at the top of the enum can call it. That's what keeps the set closed: nobody can create a
`PURPLE` light at runtime.

You may write `private Light(...)` if you like, but it's the same thing. Most people leave it out.

### Step 6: The constructor runs once per constant

Put a `println` inside the constructor to see when it runs:

```java
enum Light {
    RED("Stop"),
    YELLOW("Slow down"),
    GREEN("Go");

    private final String message;

    Light(String message) {
        System.out.println("Creating " + message);
        this.message = message;
    }

    String getMessage() {
        return message;
    }
}

public class ConstructorRunsOnce {

    public static void main(String[] args) {
        System.out.println("Program started");

        System.out.println(Light.GREEN.getMessage());
        System.out.println(Light.GREEN.getMessage());
        System.out.println(Light.RED.getMessage());
    }
}
```

Output:

```text
Program started
Creating Stop
Creating Slow down
Creating Go
Go
Go
Stop
```

- The constructor ran **three times, once for each constant**, and never again.
- It ran the first time the program touched `Light`, which is why "Program started" came first.
- Using `Light.GREEN` twice didn't create anything new. Both times it's the same single object,
  just as in Concept 1.

### Step 7: Looping over constants and their data

`values()` from Concept 2 works with data too:

```java
public class LightLoop {

    public static void main(String[] args) {
        for (Light light : Light.values()) {
            System.out.println(light + " -> " + light.getMessage() + " for " + light.getSeconds() + " seconds");
        }
    }
}
```

(Use the `Light` enum from Step 2 at the top of the file.)

Output:

```text
RED -> Stop for 30 seconds
YELLOW -> Slow down for 5 seconds
GREEN -> Go for 25 seconds
```

### Step 8: The data already removed a `switch`

In Concept 3 we needed this to get a message:

```java
String message = switch (light) {
    case RED    -> "Stop";
    case YELLOW -> "Slow down";
    case GREEN  -> "Go";
};
```

Now the message lives **inside** each constant, so all of that becomes one line:

```java
enum Light {
    RED("Stop"),
    YELLOW("Slow down"),
    GREEN("Go");

    private final String message;

    Light(String message) {
        this.message = message;
    }

    String getMessage() {
        return message;
    }
}

public class NoSwitchNeeded {

    public static void main(String[] args) {
        Light light = Light.YELLOW;

        System.out.println(light.getMessage());
    }
}
```

Output:

```text
Slow down
```

And if someone adds a new constant, they're **forced** to give it a message, because the
constructor needs one (Step 4). There's no `switch` anywhere to forget.

This works when each constant just needs a different **value**. When each constant needs to *do
something* different, you need a method per constant. That's Concept 5.

### Summary

1. Enum constants can hold data: `RED("Stop", 30)` passes values to the constructor.
2. An enum with data has four parts in order: constants (ending with `;`), fields, constructor,
   getters.
3. Make the fields `final`: each constant is one object shared by the whole program.
4. The constructor is always private. Writing `public` is a compile error, and `new Light(...)` is
   not allowed anywhere.
5. The constructor runs exactly once per constant.
6. Storing the data inside each constant can replace a `switch` that only looked up values.

---

## Concept 5 — Methods on an enum, and per-constant behaviour

Concept 4 gave each constant different **data**. This concept gives each constant different
**behaviour**: each constant runs its own code when you call the same method.

### Step 1: A normal method, shared by all constants

An enum can have ordinary methods, just like a class. Every constant gets the same method:

```java
enum Light {
    RED("Stop", 30),
    YELLOW("Slow down", 5),
    GREEN("Go", 25);

    private final String message;
    private final int seconds;

    Light(String message, int seconds) {
        this.message = message;
        this.seconds = seconds;
    }

    String describe() {
        return this.name() + " means " + message + " for " + seconds + " seconds";
    }
}

public class SharedMethodDemo {

    public static void main(String[] args) {
        System.out.println(Light.RED.describe());
        System.out.println(Light.GREEN.describe());
    }
}
```

Output:

```text
RED means Stop for 30 seconds
GREEN means Go for 25 seconds
```

- `describe()` is written **once**, and every constant can call it.
- Inside the method, `this` means "the constant I was called on". In `Light.RED.describe()`, `this`
  is `RED`, so `message` is RED's message.

The code is the same for every constant. Only the data differs. That's fine for this, but not for
the next example.

### Step 2: When each constant must *do* something different

A traffic light is a bit limited for this, so we'll use a tiny calculator. Each operation takes two
numbers, but each one does a **different calculation**:

- `PLUS` adds them
- `MINUS` subtracts them
- `TIMES` multiplies them

You can't store "add" in a `String` field. It's code, not data. The first idea is a `switch` inside
the method:

```java
enum Operation {
    PLUS,
    MINUS,
    TIMES;

    int apply(int a, int b) {
        return switch (this) {
            case PLUS  -> a + b;
            case MINUS -> a - b;
            case TIMES -> a * b;
        };
    }
}

public class SwitchInsideMethod {

    public static void main(String[] args) {
        System.out.println(Operation.PLUS.apply(6, 3));
        System.out.println(Operation.MINUS.apply(6, 3));
        System.out.println(Operation.TIMES.apply(6, 3));
    }
}
```

Output:

```text
9
3
18
```

- `switch (this)` means "which constant am I?"
- It's a `switch` expression, so it's safe (Concept 3): forget a case and it won't compile.

This is acceptable. But there's a neater way, where each constant carries its own code directly.

### Step 3: Per-constant bodies

```java
enum Operation {
    PLUS {
        int apply(int a, int b) {
            return a + b;
        }
    },
    MINUS {
        int apply(int a, int b) {
            return a - b;
        }
    },
    TIMES {
        int apply(int a, int b) {
            return a * b;
        }
    };

    abstract int apply(int a, int b);
}

public class PerConstantDemo {

    public static void main(String[] args) {
        System.out.println(Operation.PLUS.apply(6, 3));
        System.out.println(Operation.MINUS.apply(6, 3));
        System.out.println(Operation.TIMES.apply(6, 3));

        for (Operation op : Operation.values()) {
            System.out.println(op + " of 6 and 3 is " + op.apply(6, 3));
        }
    }
}
```

Output:

```text
9
3
18
PLUS of 6 and 3 is 9
MINUS of 6 and 3 is 3
TIMES of 6 and 3 is 18
```

Read it piece by piece:

| Code | Meaning |
| --- | --- |
| `abstract int apply(int a, int b);` (at the bottom) | "Every `Operation` has an `apply` method, but I'm not saying what it does here." `abstract` means "no body, someone else must write it". |
| `PLUS { ... }` | The `{ ... }` after a constant is that constant's **own body**: code that belongs only to `PLUS`. |
| `int apply(int a, int b) { return a + b; }` inside `PLUS` | `PLUS`'s version of `apply`. |

So `Operation.PLUS.apply(6, 3)` runs the code inside `PLUS { ... }` and gives `9`.
`Operation.TIMES.apply(6, 3)` runs the code inside `TIMES { ... }` and gives `18`.

**There's no `switch` anywhere.** Each constant already knows its own answer. The calling code just
says `op.apply(6, 3)` and the right code runs.

The punctuation can be confusing, so look closely:

```text
PLUS { ...method... },      <- comma between constants
MINUS { ...method... },
TIMES { ...method... };     <- semicolon after the LAST constant
```

### Step 4: `abstract` forces every constant to answer

This is the important part. Someone adds `DIVIDE` but forgets to give it a body:

```java
enum Operation {
    PLUS {
        int apply(int a, int b) {
            return a + b;
        }
    },
    MINUS {
        int apply(int a, int b) {
            return a - b;
        }
    },
    TIMES {
        int apply(int a, int b) {
            return a * b;
        }
    },
    DIVIDE;                      // no body

    abstract int apply(int a, int b);
}
```

```text
ForgotBody.java:17: error: Operation is abstract; cannot be instantiated
    DIVIDE;
    ^
```

In plain English: "`apply` has no code for `DIVIDE`, so I can't create `DIVIDE`." The program won't
build until you write `DIVIDE`'s `apply`.

That's the whole point. Because the method is `abstract`, **every constant must answer it**. A new
constant can't slip in without deciding what it does.

### Step 5: Why this is better than a `switch` spread across the codebase

Imagine the `switch` from Step 2 wasn't inside the enum, but copied into four different classes in
the project (the checkout page, the receipt printer, the report, the API). Now someone adds
`DIVIDE`:

| `switch` copied around the codebase | Per-constant body |
| --- | --- |
| You have to *find* all four `switch`es | The code is in one place: inside `DIVIDE` |
| Old-style `switch` statements compile even if you miss one | Won't compile until `DIVIDE` has its method |
| Typically three get updated and one is forgotten | Nothing to forget |

This is the problem the task describes: "a `switch` that gets copied to four places and updated in
three". Per-constant behaviour removes it, because the behaviour lives *with* the constant.

### Step 6: You've seen this before — it's polymorphism

In Task F2.6 you had an interface or abstract class with one method, and several classes each
implementing it differently. Calling the method ran the right version for the actual object. That's
**polymorphism**: same method call, different behaviour depending on the object.

Per-constant bodies are exactly that, in miniature:

| F2.6 polymorphism | Enum per-constant body |
| --- | --- |
| abstract method in an abstract class | `abstract int apply(...)` in the enum |
| several classes each implement it | each constant implements it in its `{ ... }` |
| anyone can add a new class later | the set of constants is fixed: only what's written in the enum |

### Step 7: Fields and per-constant bodies together

You can mix everything from Concepts 4 and 5 in one enum:

```java
enum Operation {
    PLUS("+") {
        int apply(int a, int b) {
            return a + b;
        }
    },
    MINUS("-") {
        int apply(int a, int b) {
            return a - b;
        }
    },
    TIMES("*") {
        int apply(int a, int b) {
            return a * b;
        }
    };

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    abstract int apply(int a, int b);

    String show(int a, int b) {
        return a + " " + symbol + " " + b + " = " + apply(a, b);
    }
}

public class WithFieldsAndBodies {

    public static void main(String[] args) {
        for (Operation op : Operation.values()) {
            System.out.println(op.show(6, 3));
        }
    }
}
```

Output:

```text
6 + 3 = 9
6 - 3 = 3
6 * 3 = 18
```

- `PLUS("+") { ... }`: first the data in `( )`, then the body in `{ }`.
- `symbol` is **data** (a field, Concept 4).
- `apply` is **behaviour**, different per constant (this concept).
- `show` is a **shared method**, written once, which uses both.

### Step 8: Which one should I use?

| Each constant needs... | Use | Example |
| --- | --- | --- |
| a different **value** | a field + constructor (Concept 4) | RED's message is "Stop" |
| the same code that uses its values | a normal shared method | `describe()` |
| different **code** | an `abstract` method + per-constant bodies | PLUS adds, TIMES multiplies |

Start with fields. Only reach for per-constant bodies when the constants genuinely need different
code.

### Summary

1. An enum can have normal methods. Inside them, `this` is the constant the method was called on.
2. Per-constant body: put `{ ... }` after a constant to give it its own version of a method.
3. Declare the method `abstract` in the enum, so every constant **must** implement it. A new
   constant without one won't compile.
4. This deletes `switch` statements: callers just call `op.apply(...)` and the right code runs.
5. It's polymorphism (F2.6) with a fixed set of types.

---

## Concept 6 — `EnumSet` and `EnumMap`

Java has a special `Set` and a special `Map` made only for enums. They do the same job as `HashSet`
and `HashMap`, but faster and more neatly. First a quick reminder of what a `Set` and a `Map` are.

| Collection | What it holds | Everyday example |
| --- | --- | --- |
| `Set` | A group of things, no duplicates. The main question is "is X in it?" | "The lights where cars must stop: RED, YELLOW" |
| `Map` | Pairs of *key → value*. You look up a value by its key. | "RED → 7 cars waiting, GREEN → 2 cars waiting" |

### Part A: `EnumSet`

#### Step 1: A normal `HashSet` of enums

```java
import java.util.HashSet;
import java.util.Set;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class HashSetDemo {

    public static void main(String[] args) {
        Set<Light> mustStop = new HashSet<>();
        mustStop.add(Light.RED);
        mustStop.add(Light.YELLOW);

        System.out.println(mustStop.contains(Light.RED));
        System.out.println(mustStop.contains(Light.GREEN));
    }
}
```

Output:

```text
true
false
```

This works. But `HashSet` was built for *any* kind of object, so it does general-purpose work it
doesn't need here (Step 3 explains).

#### Step 2: The same thing with `EnumSet`

```java
import java.util.EnumSet;
import java.util.Set;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class EnumSetDemo {

    public static void main(String[] args) {
        Set<Light> mustStop = EnumSet.of(Light.RED, Light.YELLOW);

        System.out.println(mustStop);
        System.out.println(mustStop.contains(Light.RED));
        System.out.println(mustStop.contains(Light.GREEN));
    }
}
```

Output:

```text
[RED, YELLOW]
true
false
```

- You don't write `new EnumSet<>()`. Instead you ask `EnumSet` to build one for you.
  `EnumSet.of(Light.RED, Light.YELLOW)` means "a set containing RED and YELLOW".
- The variable type is still `Set<Light>`, so you use it exactly like any other set: `contains`,
  `add`, `remove`.
- It's shorter: one line instead of three.

The common ways to build one:

```java
import java.util.EnumSet;
import java.util.Set;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class EnumSetWays {

    public static void main(String[] args) {
        Set<Light> some = EnumSet.of(Light.GREEN, Light.RED);
        Set<Light> all = EnumSet.allOf(Light.class);
        Set<Light> empty = EnumSet.noneOf(Light.class);

        System.out.println("of:     " + some);
        System.out.println("allOf:  " + all);
        System.out.println("noneOf: " + empty);

        empty.add(Light.YELLOW);
        System.out.println("after add: " + empty);
    }
}
```

Output:

```text
of:     [RED, GREEN]
allOf:  [RED, YELLOW, GREEN]
noneOf: []
after add: [YELLOW]
```

| Code | Gives you |
| --- | --- |
| `EnumSet.of(A, B)` | a set with just A and B |
| `EnumSet.allOf(Light.class)` | a set with every constant |
| `EnumSet.noneOf(Light.class)` | an empty set you can `add` to later |

`Light.class` means "the `Light` type itself". `allOf` and `noneOf` need it to know which enum you
mean, because you haven't passed any constants.

Notice the first line: we wrote `GREEN, RED` but it printed `[RED, GREEN]`. An `EnumSet` always
keeps the constants in the order they're written in the enum.

#### Step 3: Why `EnumSet` is faster

Remember from Task F2.8 how a `HashSet` works: it calls `hashCode()` on the object, picks a bucket
from that number, then uses `equals()` to check inside the bucket. That's a lot of steps, needed
because a `HashSet` can hold *any* object.

An `EnumSet` knows something a `HashSet` doesn't: **there are only 3 possible lights, and each has a
fixed position** (its `ordinal()` from Concept 2: RED = 0, YELLOW = 1, GREEN = 2). So it just
keeps one yes/no switch per position:

```text
                RED   YELLOW   GREEN
position:        0      1        2
in the set?     yes    yes      no
```

To answer "is GREEN in the set?" it looks at position 2 and reads "no". There's no `hashCode()`, no
buckets and no `equals()`. Inside, those yes/no switches are the bits of a single number, so the
whole set is tiny and every check is almost instant.

Wait, didn't Concept 2 say `ordinal()` is dangerous? Only when you **save** it somewhere (like a
database) and read it back after the enum has changed. `EnumSet` uses it only inside the running
program and rebuilds everything each time the program starts, so it's always correct.

### Part B: `EnumMap`

#### Step 4: A normal `HashMap` with enum keys

```java
import java.util.HashMap;
import java.util.Map;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class HashMapDemo {

    public static void main(String[] args) {
        Map<Light, Integer> carsWaiting = new HashMap<>();
        carsWaiting.put(Light.GREEN, 2);
        carsWaiting.put(Light.RED, 7);
        carsWaiting.put(Light.YELLOW, 1);

        System.out.println(carsWaiting.get(Light.RED));
        System.out.println(carsWaiting);
    }
}
```

Output:

```text
7
{YELLOW=1, GREEN=2, RED=7}
```

- `put(Light.RED, 7)` stores "RED → 7".
- `get(Light.RED)` looks it up and gives `7`.
- But look at the printed order: `YELLOW, GREEN, RED`. That's neither the order we added them nor
  the order in the enum. A `HashMap` orders things by their bucket, which comes from `hashCode()`.
  On another computer the order could be different.

#### Step 5: The same thing with `EnumMap`

Only one line changes, the `new` line:

```java
import java.util.EnumMap;
import java.util.Map;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class EnumMapDemo {

    public static void main(String[] args) {
        Map<Light, Integer> carsWaiting = new EnumMap<>(Light.class);
        carsWaiting.put(Light.GREEN, 2);
        carsWaiting.put(Light.RED, 7);
        carsWaiting.put(Light.YELLOW, 1);

        System.out.println(carsWaiting.get(Light.RED));
        System.out.println(carsWaiting);
    }
}
```

Output:

```text
7
{RED=7, YELLOW=1, GREEN=2}
```

- `new EnumMap<>(Light.class)`: you must pass `Light.class` so it knows which enum the keys come
  from (and therefore how many slots it needs).
- `put` and `get` work exactly the same as before. The rest of your code doesn't change.
- The order is now always the enum's order: `RED, YELLOW, GREEN`. Predictable, every time, on every
  computer.

#### Step 6: Why `EnumMap` is faster

Same idea as `EnumSet`. Because there are exactly 3 keys with fixed positions, an `EnumMap` is
really just an **array with 3 slots**:

```text
slot (ordinal):   [0]     [1]      [2]
key:              RED    YELLOW   GREEN
value:             7       1        2
```

- `get(Light.RED)`: RED's ordinal is 0, so read slot 0. Done.
- A `HashMap` instead has to call `hashCode()`, find the bucket, then call `equals()` to compare
  keys, and it also creates an extra "entry" object for every pair it stores.

So an `EnumMap` is faster (one array lookup), smaller (no extra entry objects, no empty buckets) and
ordered (by the enum).

#### Step 7: A common use — counting

```java
import java.util.EnumMap;
import java.util.Map;

enum Light {
    RED,
    YELLOW,
    GREEN
}

public class EnumMapCount {

    public static void main(String[] args) {
        Light[] seenToday = {
            Light.RED, Light.GREEN, Light.RED, Light.YELLOW, Light.RED, Light.GREEN
        };

        Map<Light, Integer> counts = new EnumMap<>(Light.class);

        for (Light light : seenToday) {
            int oldCount = counts.getOrDefault(light, 0);
            counts.put(light, oldCount + 1);
        }

        System.out.println(counts);
    }
}
```

Output:

```text
{RED=3, YELLOW=1, GREEN=2}
```

- `getOrDefault(light, 0)`: "give me the current count, or 0 if this light isn't in the map yet".
- Then `put` stores the count plus one.

### Rule of thumb

Whenever the things in your `Set`, or the keys of your `Map`, are enum constants:

| Instead of | Use |
| --- | --- |
| `new HashSet<>()` | `EnumSet.noneOf(X.class)` or `EnumSet.of(...)` |
| `new HashMap<>()` | `new EnumMap<>(X.class)` |

Your code stays the same (it's still a `Set` or a `Map`), it just runs faster.

### Summary

1. `EnumSet` is a `Set` for enum constants. Build it with `EnumSet.of(...)`, `allOf(X.class)` or
   `noneOf(X.class)`.
2. `EnumMap` is a `Map` with enum keys. Build it with `new EnumMap<>(X.class)`.
3. Both are faster and smaller than the `Hash` versions because they skip `hashCode()`, buckets and
   `equals()`. Inside, they're just a row of slots, one per constant, found by `ordinal()`.
4. Both keep the constants in the enum's order.
5. Using `ordinal()` inside them is safe, because it's never saved. It's rebuilt every time the
   program runs.

---

## Concept 7 — A state machine is just states plus allowed moves

"State machine" sounds scary, but it's only two things:

1. **States:** the situations something can be in. A traffic light can be RED, YELLOW or GREEN.
2. **Transitions:** the rules for which moves from one state to another are allowed.

A real traffic light doesn't jump around randomly. It follows a cycle:

```text
   RED ──────► GREEN ──────► YELLOW
    ▲                          │
    └──────────────────────────┘
```

| From | Allowed to go to |
| --- | --- |
| RED | GREEN |
| GREEN | YELLOW |
| YELLOW | RED |

Anything else, like RED → YELLOW, is **illegal**. The enum already gives us the states. This concept
adds the rules.

### Step 1: Without rules, anything goes

```java
enum Light {
    RED,
    YELLOW,
    GREEN
}

public class NoRulesDemo {

    public static void main(String[] args) {
        Light current = Light.RED;
        System.out.println("Now: " + current);

        current = Light.YELLOW;
        System.out.println("Now: " + current);
    }
}
```

Output:

```text
Now: RED
Now: YELLOW
```

RED → YELLOW is wrong, but nothing stopped it. The variable `current` accepts any `Light` at any
time. The enum controls *which* states exist, but not *how you move* between them.

### Step 2: Teach each light where it may go next

We add two methods to the enum:

```java
import java.util.EnumSet;
import java.util.Set;

enum Light {
    RED,
    YELLOW,
    GREEN;

    Set<Light> allowedNext() {
        return switch (this) {
            case RED    -> EnumSet.of(GREEN);
            case GREEN  -> EnumSet.of(YELLOW);
            case YELLOW -> EnumSet.of(RED);
        };
    }

    boolean canTransitionTo(Light next) {
        return allowedNext().contains(next);
    }
}

public class CanTransitionDemo {

    public static void main(String[] args) {
        System.out.println(Light.RED.canTransitionTo(Light.GREEN));
        System.out.println(Light.RED.canTransitionTo(Light.YELLOW));
        System.out.println(Light.GREEN.canTransitionTo(Light.YELLOW));
        System.out.println(Light.YELLOW.canTransitionTo(Light.RED));
    }
}
```

Output:

```text
true
false
true
true
```

Line by line:

| Code | Meaning |
| --- | --- |
| `Set<Light> allowedNext()` | "Give me the set of lights I'm allowed to change to." |
| `switch (this)` | "Which light am I?" (Concept 5) |
| `case RED -> EnumSet.of(GREEN);` | "If I'm RED, the only allowed next light is GREEN." (Concept 6) |
| `boolean canTransitionTo(Light next)` | "Am I allowed to change to `next`?" |
| `allowedNext().contains(next)` | Look `next` up in my allowed set. Yes → `true`, no → `false`. |

So `Light.RED.canTransitionTo(Light.YELLOW)` works like this:

1. `this` is RED.
2. RED's allowed set is `{GREEN}`.
3. Does `{GREEN}` contain YELLOW? No, so the answer is `false`.

Notice we used everything from earlier concepts:

- a `switch` **expression**, so adding a new light won't compile until you give it rules (Concept 3)
- a method on the enum (Concept 5)
- `EnumSet` to hold the allowed moves (Concept 6)

If a state allows **more than one** next state, list them: `EnumSet.of(A, B)`. If it allows
**none** (a final state, like a cancelled order), use `EnumSet.noneOf(Light.class)`.

### Step 3: Actually reject the illegal move

`canTransitionTo` only *answers the question*. Something has to *enforce* it. We put the current
light inside a small class, and the only way to change it is a method that checks the rule first:

```java
import java.util.EnumSet;
import java.util.Set;

enum Light {
    RED,
    YELLOW,
    GREEN;

    Set<Light> allowedNext() {
        return switch (this) {
            case RED    -> EnumSet.of(GREEN);
            case GREEN  -> EnumSet.of(YELLOW);
            case YELLOW -> EnumSet.of(RED);
        };
    }

    boolean canTransitionTo(Light next) {
        return allowedNext().contains(next);
    }
}

class TrafficSignal {
    private Light current = Light.RED;

    Light getCurrent() {
        return current;
    }

    void changeTo(Light next) {
        if (!current.canTransitionTo(next)) {
            throw new IllegalStateException("Cannot go from " + current + " to " + next);
        }
        current = next;
    }
}

public class TrafficSignalDemo {

    public static void main(String[] args) {
        TrafficSignal signal = new TrafficSignal();
        System.out.println("Start: " + signal.getCurrent());

        signal.changeTo(Light.GREEN);
        System.out.println("Now: " + signal.getCurrent());

        signal.changeTo(Light.YELLOW);
        System.out.println("Now: " + signal.getCurrent());

        signal.changeTo(Light.GREEN);
        System.out.println("Now: " + signal.getCurrent());
    }
}
```

Output:

```text
Start: RED
Now: GREEN
Now: YELLOW
Exception in thread "main" java.lang.IllegalStateException: Cannot go from YELLOW to GREEN
	at TrafficSignal.changeTo(TrafficSignalDemo.java:31)
	at TrafficSignalDemo.main(TrafficSignalDemo.java:49)
```

- RED → GREEN: allowed, so it changed.
- GREEN → YELLOW: allowed, so it changed.
- YELLOW → GREEN: **not allowed**, so `changeTo` threw an exception and the light stayed YELLOW.

Why this works:

- `current` is `private`. Nobody outside `TrafficSignal` can write `current = ...` directly, so
  nobody can skip the check.
- `changeTo` is the **only door**, and it always checks `canTransitionTo` first.
- `throw new IllegalStateException(...)` stops the program with a clear message. "Illegal state"
  is exactly what this is: a move the rules don't allow.

That's a complete state machine: states (the enum), rules (`canTransitionTo`) and enforcement
(`changeTo`).

### Step 4: Why not store the allowed set in a field?

After Concept 4 you might try putting the rules in the constructor:

```java
enum Light {
    RED(EnumSet.of(GREEN)),
    YELLOW(EnumSet.of(RED)),
    GREEN(EnumSet.of(YELLOW));
    ...
}
```

```text
ForwardReference.java:5: error: illegal forward reference
    RED(EnumSet.of(GREEN)),
                   ^
```

Constants are created top to bottom (Concept 4, Step 6). When Java creates RED, GREEN doesn't exist
yet, so RED can't refer to it. "Forward reference" means "pointing at something further down that
isn't ready yet".

Putting the rules inside a **method** (like `allowedNext()`) avoids this. The method only runs when
you call it, and by then all three constants exist.

### Step 5: Where this shows up in Zenith

The traffic light is a toy, but the idea is everywhere in real systems:

| Thing | States | Example of an illegal move |
| --- | --- | --- |
| An order | DRAFT, PENDING_PAYMENT, CONFIRMED, CANCELLED | CANCELLED → CONFIRMED |
| A seat hold (Phase 5) | held, confirmed, expired... | expired → confirmed |
| A saga (Phase 6) | each step of a multi-service process | skipping a step |

Without a state machine, any code anywhere can set any status, and one day an order that was
cancelled gets shipped. With one, the illegal move throws immediately.

### Summary

1. A state machine is **states** plus **allowed moves** between them.
2. The enum gives you the states. A method `canTransitionTo(next)` gives you the rules.
3. Store each state's allowed next states in an `EnumSet`, returned from a method
   (`allowedNext()`). `EnumSet.noneOf(...)` means "no moves allowed".
4. Enforce the rules by keeping the current state `private` and changing it only through a method
   that checks first and throws `IllegalStateException` if the move is illegal.
5. You can't refer to a later constant from an earlier constant's constructor ("illegal forward
   reference"), so put the rules in a method instead.

---

## Concept 8 — Saving enums to a database: `ORDINAL` versus `STRING`

This is the one rule from this task you must never forget.

### The situation

While your program runs, an order's status is an enum object like `OrderStatus.CONFIRMED`. But when
the program stops, objects disappear. To remember the order until tomorrow, you **save** it
somewhere: a database, or here, a plain text file. A database or file can't hold a Java object. It
can only hold simple things like **numbers** and **text**.

So you have to turn the enum into a number or text when saving, and turn it back when loading. From
Concept 2 there are two obvious choices:

| Save as | Method to save | Method to load | `CONFIRMED` becomes |
| --- | --- | --- | --- |
| its position (a number) | `ordinal()` | `values()[number]` | `2` |
| its name (text) | `name()` | `valueOf(text)` | `"CONFIRMED"` |

Both work today. The difference shows up **after someone changes the enum**. We'll run a real
experiment.

### The experiment setup

Three kinds of file. First, the enum in its own file, `OrderStatus.java`:

```java
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED
}
```

Positions: DRAFT = 0, PENDING_PAYMENT = 1, CONFIRMED = 2, CANCELLED = 3.

#### Way 1: save the position — `SaveByOrdinal.java` and `LoadByOrdinal.java`

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class SaveByOrdinal {

    public static void main(String[] args) throws Exception {
        String rows = ""
            + "101," + OrderStatus.CONFIRMED.ordinal() + "\n"
            + "102," + OrderStatus.CANCELLED.ordinal() + "\n";

        Files.writeString(Path.of("orders-ordinal.txt"), rows);

        System.out.println("Saved:");
        System.out.print(rows);
    }
}
```

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class LoadByOrdinal {

    public static void main(String[] args) throws Exception {
        for (String row : Files.readAllLines(Path.of("orders-ordinal.txt"))) {
            String[] parts = row.split(",");
            String orderId = parts[0];
            int number = Integer.parseInt(parts[1]);

            OrderStatus status = OrderStatus.values()[number];

            System.out.println("Order " + orderId + " is " + status);
        }
    }
}
```

What the new bits mean:

| Code | Meaning |
| --- | --- |
| `Files.writeString(Path.of("orders-ordinal.txt"), rows)` | Write the text `rows` into a file called `orders-ordinal.txt`. This file is our "database". |
| `Files.readAllLines(Path.of(...))` | Read the file back, one `String` per line. |
| `row.split(",")` | Split `"101,2"` into `["101", "2"]`. |
| `Integer.parseInt(parts[1])` | Turn the text `"2"` into the number `2`. |
| `OrderStatus.values()[number]` | All constants as an array (Concept 2), then pick the one at position `number`. |
| `throws Exception` | Reading and writing files can fail. This just says "if it fails, stop the program". |

Each line of the file is one "row": the order id, then its status.

#### Way 2: save the name — `SaveByName.java` and `LoadByName.java`

Exactly the same, but using `name()` and `valueOf()`:

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class SaveByName {

    public static void main(String[] args) throws Exception {
        String rows = ""
            + "101," + OrderStatus.CONFIRMED.name() + "\n"
            + "102," + OrderStatus.CANCELLED.name() + "\n";

        Files.writeString(Path.of("orders-name.txt"), rows);

        System.out.println("Saved:");
        System.out.print(rows);
    }
}
```

```java
import java.nio.file.Files;
import java.nio.file.Path;

public class LoadByName {

    public static void main(String[] args) throws Exception {
        for (String row : Files.readAllLines(Path.of("orders-name.txt"))) {
            String[] parts = row.split(",");
            String orderId = parts[0];
            String text = parts[1];

            OrderStatus status = OrderStatus.valueOf(text);

            System.out.println("Order " + orderId + " is " + status);
        }
    }
}
```

To compile all five files at once: `javac *.java`.

### Day 1: save and load — both work

```bash
javac *.java
java SaveByOrdinal
java LoadByOrdinal
java SaveByName
java LoadByName
```

Output:

```text
Saved:
101,2
102,3
Order 101 is CONFIRMED
Order 102 is CANCELLED
Saved:
101,CONFIRMED
102,CANCELLED
Order 101 is CONFIRMED
Order 102 is CANCELLED
```

Order 101 is confirmed, order 102 is cancelled. Both ways read back correctly. The files now
contain:

```text
orders-ordinal.txt        orders-name.txt
101,2                     101,CONFIRMED
102,3                     102,CANCELLED
```

### Day 2: a teammate adds a new status

A teammate needs an "on hold" status and adds it where it makes sense, **in the middle**:

```java
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    ON_HOLD,
    CONFIRMED,
    CANCELLED
}
```

Nobody touches the saved files. They're yesterday's real orders. Now only **load** (don't save
again):

```bash
javac *.java
java LoadByOrdinal
java LoadByName
```

Output:

```text
Order 101 is ON_HOLD
Order 102 is CONFIRMED

Order 101 is CONFIRMED
Order 102 is CANCELLED
```

Look at the ordinal version:

| Order | Really is | File says | Position 2 / 3 today | Loaded as |
| --- | --- | --- | --- | --- |
| 101 | CONFIRMED | `2` | position 2 is now ON_HOLD | **ON_HOLD** (wrong) |
| 102 | CANCELLED | `3` | position 3 is now CONFIRMED | **CONFIRMED** (wrong) |

**A cancelled order is now confirmed.** In Zenith that means a customer who cancelled gets tickets
anyway, or gets charged. And notice:

- There was **no error**.
- There was **no crash**.
- The file didn't change. The number `3` is still sitting there. What changed is what `3` *means*.
- **Every** old row after the insertion point changed meaning at the same moment.

The name version read back perfectly, because `"CANCELLED"` still means CANCELLED no matter where it
sits in the list.

This is why it's such a nasty bug: nothing tells you. You find out when customers complain, and by
then new orders have been saved with the *new* numbering, so the database is a mix of old and new
meanings that's very hard to untangle.

### Day 3: names have one weakness — renaming

Suppose someone renames `CANCELLED` to the American spelling `CANCELED`:

```java
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    ON_HOLD,
    CONFIRMED,
    CANCELED
}
```

```bash
javac OrderStatus.java LoadByName.java
java LoadByName
```

Output:

```text
Order 101 is CONFIRMED
Exception in thread "main" java.lang.IllegalArgumentException: No enum constant OrderStatus.CANCELLED
```

Saving by name breaks too, **but loudly**. You get an immediate error that says exactly which value
is the problem. Compare:

| Change to the enum | Saved as ordinal | Saved as name |
| --- | --- | --- |
| add a constant in the middle | **silently wrong** | fine |
| reorder constants | **silently wrong** | fine |
| delete a constant | **silently wrong** (or crash) | loud error |
| rename a constant | fine (!) | loud error |

A loud error you can fix in five minutes. A silent wrong answer can go unnoticed for weeks. So
**names are far safer**. The one rule with names: **once a constant has been saved, don't rename
it.**

### Option 3: an explicit code that never changes

If you want to be free to rename constants, give each one a fixed **code** (a field, Concept 4) and
save that instead:

```java
enum OrderStatus {
    DRAFT("D"),
    PENDING_PAYMENT("P"),
    ON_HOLD("H"),
    CONFIRMED("C"),
    CANCELED("X");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    String getCode() {
        return code;
    }

    static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }
}

public class CodeDemo {

    public static void main(String[] args) {
        System.out.println(OrderStatus.CONFIRMED.getCode());
        System.out.println(OrderStatus.fromCode("C"));
        System.out.println(OrderStatus.fromCode("X"));
        System.out.println(OrderStatus.fromCode("Z"));
    }
}
```

Output:

```text
C
CONFIRMED
CANCELED
Exception in thread "main" java.lang.IllegalArgumentException: Unknown order status code: Z
```

- To save: `status.getCode()` gives `"C"`.
- To load: `OrderStatus.fromCode("C")` loops over all constants (Concept 2) and returns the one
  whose code matches. An unknown code throws a loud error, like `valueOf` does.
- `static` means you call it on the enum itself (`OrderStatus.fromCode(...)`), not on one constant,
  just like `valueOf`.
- You can now rename `CANCELED` to anything, or reorder the list, and `"X"` still means the same
  thing. The code is a promise: **never change a code once it's been saved.**

### What this looks like with JPA (Phase 1 preview)

In Phase 1 you won't write files by hand. You'll use **JPA**, a library that saves Java objects into
database tables for you. You tell it how to save an enum field with `@Enumerated`. You can't run this
yet (it needs the JPA library), so just recognise it:

```java
@Entity
public class Order {

    @Enumerated(EnumType.ORDINAL)    // WRONG: saves 0, 1, 2, 3 — the bug from Day 2
    private OrderStatus status;
}
```

```java
@Entity
public class Order {

    @Enumerated(EnumType.STRING)     // RIGHT: saves "CONFIRMED", "CANCELLED"
    private OrderStatus status;
}
```

**Watch out: if you leave `@Enumerated` off completely, JPA uses `ORDINAL` by default.** So the
dangerous option is the one you get by doing nothing. Always write `@Enumerated(EnumType.STRING)`
on every enum field.

### The rule (copy this into `NOTES.md`)

> **Never save an enum's `ordinal()`.** It's the constant's position, so adding, removing or
> reordering constants silently changes the meaning of every saved row, with no error anywhere.
> With JPA, always write `@Enumerated(EnumType.STRING)` (the default is `ORDINAL`). Or store an
> explicit code field that never changes. With names, never rename a constant once it's been
> saved; with codes, never change a code.

### Summary

1. Databases store numbers and text, not objects, so enums must be converted when saving.
2. Saving `ordinal()` (the position) is dangerous: inserting a constant in the middle silently
   changes what every old row means. In our test, a **cancelled** order came back as
   **confirmed**.
3. Saving `name()` survives adding and reordering. Renaming breaks it, but with a loud error.
4. An explicit code field survives everything, as long as you never change the codes.
5. In JPA: `@Enumerated(EnumType.STRING)`, always. Leaving it off means `ORDINAL`.
6. This is a common interview question: "What's wrong with `EnumType.ORDINAL`?"

---

## Concept 9 — When an enum is the wrong choice

Enums are great, but they have one built-in limit, and it's the same thing that makes them safe:
**the list of constants is written in the code.** To add, remove or change a constant, a programmer
has to edit the code, compile it and **deploy** it (release a new version of the program to the
servers). Nobody can add a constant while the program is running.

That's perfect when the list really is fixed. It's a problem when it isn't.

### Wrong case 1: the list changes while the program is running

Zenith has venues where events happen. Suppose we made them an enum:

```java
enum Venue {
    WEMBLEY,
    O2_ARENA
}

public class VenueEnumDemo {

    public static void main(String[] args) {
        String chosenByAdmin = "ROYAL_ALBERT_HALL";

        Venue venue = Venue.valueOf(chosenByAdmin);

        System.out.println("Event booked at " + venue);
    }
}
```

Output:

```text
Exception in thread "main" java.lang.IllegalArgumentException: No enum constant Venue.ROYAL_ALBERT_HALL
```

An admin signs a deal with a new venue on Monday and wants to start selling tickets today. But
`ROYAL_ALBERT_HALL` isn't in the code, so it can't exist. Someone has to ask a programmer, wait for
the code change, wait for testing, wait for a deploy. All just to add a name to a list.

Venues are **data**: new ones appear all the time, and each one has the same kind of information
(name, capacity, address). Data belongs in a database table, or in a normal class or record, not in
an enum.

### Wrong case 2: another system owns the list

A payment provider (another company) tells us the status of each payment. We wrote an enum for the
statuses they send today:

```java
enum PaymentStatus {
    PAID,
    FAILED,
    REFUNDED
}

public class ExternalStatusDemo {

    public static void main(String[] args) {
        String[] messagesFromPaymentProvider = { "PAID", "FAILED", "PARTIALLY_REFUNDED", "PAID" };

        for (String text : messagesFromPaymentProvider) {
            PaymentStatus status = PaymentStatus.valueOf(text);
            System.out.println("Handled: " + status);
        }
    }
}
```

Output:

```text
Handled: PAID
Handled: FAILED
Exception in thread "main" java.lang.IllegalArgumentException: No enum constant PaymentStatus.PARTIALLY_REFUNDED
```

One day the provider starts sending a new status, `PARTIALLY_REFUNDED`. They didn't ask us, and
they don't have to: it's **their** list. Our program crashes on that message, and the `PAID`
message after it is **never handled** either. One unexpected word stops everything.

When a list is owned by someone else (another company, another team's service, a config file),
**you don't control when it changes**, so you can't promise your enum has every value.

If you still want an enum for the values you understand, at least don't crash on new ones. Add an
`UNKNOWN` constant and a safe way to read outside text:

```java
enum PaymentStatus {
    PAID,
    FAILED,
    REFUNDED,
    UNKNOWN;

    static PaymentStatus fromProvider(String text) {
        for (PaymentStatus status : values()) {
            if (status.name().equals(text)) {
                return status;
            }
        }
        System.out.println("WARNING: unknown payment status from provider: " + text);
        return UNKNOWN;
    }
}

public class SafeExternalDemo {

    public static void main(String[] args) {
        String[] messagesFromPaymentProvider = { "PAID", "FAILED", "PARTIALLY_REFUNDED", "PAID" };

        for (String text : messagesFromPaymentProvider) {
            PaymentStatus status = PaymentStatus.fromProvider(text);
            System.out.println("Handled: " + status);
        }
    }
}
```

Output:

```text
Handled: PAID
Handled: FAILED
WARNING: unknown payment status from provider: PARTIALLY_REFUNDED
Handled: UNKNOWN
Handled: PAID
```

- `fromProvider` is like `valueOf`, but instead of crashing on an unknown word it prints a warning
  and returns `UNKNOWN`.
- The last `PAID` is still handled. One surprise no longer stops everything.
- The warning tells a human to look at it and decide whether to add the new value in the next
  release.

Note the difference from Concept 2, where we said `valueOf` crashing is "usually what you want".
That's true for **your own** data, where an unknown value means a bug. For data from **outside**,
new values are normal, so be tolerant and log a warning.

### Wrong case 3: adding a value must not need a deploy

This is really cases 1 and 2 from the business side. Ask: "When a new value is needed, who adds
it, and how fast?"

- If the answer is "a programmer, as part of a code change": an enum is fine.
- If the answer is "an admin, a customer or another company, right now": an enum is wrong.

Examples: promo codes, ticket categories set up per event, countries you sell in, event genres,
feature flags. Business people expect to change these themselves, today.

### The alternative: keep it as data

For venues, use a normal record (Task F2.8) and keep them in a collection (in a real app, a
database table):

```java
import java.util.HashMap;
import java.util.Map;

record Venue(String code, String name, int capacity) { }

public class VenueAsDataDemo {

    public static void main(String[] args) {
        Map<String, Venue> venues = new HashMap<>();
        venues.put("WEMBLEY", new Venue("WEMBLEY", "Wembley Stadium", 90000));
        venues.put("O2_ARENA", new Venue("O2_ARENA", "The O2 Arena", 20000));

        System.out.println("Venues: " + venues.keySet());

        Venue added = new Venue("ROYAL_ALBERT_HALL", "Royal Albert Hall", 5272);
        venues.put(added.code(), added);

        Venue venue = venues.get("ROYAL_ALBERT_HALL");
        System.out.println("Event booked at " + venue.name() + " (" + venue.capacity() + " seats)");
    }
}
```

Output:

```text
Venues: [O2_ARENA, WEMBLEY]
Event booked at Royal Albert Hall (5272 seats)
```

The new venue was added **while the program was running**, with no code change. In a real app the
admin would add a row in a screen, and it would be saved to the database.

What you give up: the compiler can no longer check venue names for you, and there's no
exhaustive `switch` over venues. That's the right trade, because there's no special code per
venue anyway. Every venue is handled the same way, just with different data.

### How to decide

Ask these questions:

| Question | If yes |
| --- | --- |
| Is the full list known when you write the code? | enum |
| Does adding a new value **also need new code** anyway? (new rules, new behaviour) | enum |
| Can users, admins or config add values while the program runs? | **not** an enum: use data |
| Does another system or company decide the values? | **not** an enum, or an enum with `UNKNOWN` and tolerant parsing |
| Must a new value go live without a deploy? | **not** an enum: use data |

The second question is the key one. A new **order status** like `ON_HOLD` needs new code anyway:
new rules about which moves are allowed (Concept 7), new screens, new emails. So making it an enum
costs nothing extra, and the compiler helps you find everything to update. A new **venue** needs no
new code, just new data, so an enum would only get in the way.

In Zenith:

| Enum (fixed, each value has its own rules) | Data (changes at runtime) |
| --- | --- |
| `OrderStatus`, `PaymentStatus`, `HoldState` | venues, events, cities |
| `TicketTier`, if tiers have fixed rules in code | ticket prices, promo codes |

### Summary

1. An enum's constants live in the code, so adding one needs a code change and a deploy.
2. Wrong choice when the list changes at runtime (venues, events, promo codes): use a record or a
   database table instead.
3. Wrong choice when another system owns the list: it can send values you've never heard of, and
   `valueOf` will crash. If you still use an enum, add `UNKNOWN` and a tolerant `fromX` method that
   logs a warning.
4. Right choice when the list is fixed and each new value needs new code anyway (like
   `OrderStatus`).

---

# The subtasks

From here on we use Zenith's real `OrderStatus` instead of traffic lights, and each piece of code
goes in its own file in this folder. All the ideas come from the concepts above. Each subtask says
which one.

---

## Subtask 1 — `OrderStatus` and a `switch` expression that refuses to compile

> **From the plan:** Write `enum OrderStatus { DRAFT, PENDING_PAYMENT, CONFIRMED, CANCELLED }` and
> a `switch` *expression* that maps each to a user-facing label. Delete one case and watch it fail
> to compile. Note that the old statement form (`switch` with `case X:` and `break`) would have
> compiled fine and failed at runtime.

**Concepts used:** 1 (what an enum is), 3 (`switch` statement versus expression).

### What it's asking

1. Make the `OrderStatus` enum.
2. Write a method that turns each status into a friendly sentence for the customer (a "label"),
   using the **new** `switch` with `->`.
3. Delete one `case` and see the compile error.
4. Write the same thing with the **old** `switch` and see that it compiles and goes wrong while the
   program runs.

### Step 1: The enum — `OrderStatus.java`

```java
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED
}
```

It's `public` and in its own file, so every other file in this folder can use it. The file name must
be `OrderStatus.java`, matching the enum's name.

What each status means in Zenith:

| Status | Meaning |
| --- | --- |
| `DRAFT` | The customer is still picking seats. |
| `PENDING_PAYMENT` | Seats chosen, waiting for the payment to go through. |
| `CONFIRMED` | Paid. The tickets are theirs. |
| `CANCELLED` | The order was cancelled. |

### Step 2: The labels with a `switch` expression — `OrderLabels.java`

The status names are for programmers. The customer should see a friendly sentence instead.

```java
public class OrderLabels {

    static String label(OrderStatus status) {
        return switch (status) {
            case DRAFT           -> "Not submitted yet";
            case PENDING_PAYMENT -> "Waiting for payment";
            case CONFIRMED       -> "Your tickets are booked";
            case CANCELLED       -> "This order was cancelled";
        };
    }

    public static void main(String[] args) {
        for (OrderStatus status : OrderStatus.values()) {
            System.out.println(status + " -> " + label(status));
        }
    }
}
```

Run it:

```bash
javac OrderStatus.java OrderLabels.java
java OrderLabels
```

Output:

```text
DRAFT -> Not submitted yet
PENDING_PAYMENT -> Waiting for payment
CONFIRMED -> Your tickets are booked
CANCELLED -> This order was cancelled
```

- `return switch (status) { ... };` — the `switch` **produces** the label, and `return` hands it
  straight back. There's no variable to fill in and no `break`.
- `OrderStatus.values()` (Concept 2) loops over every status, so we test all four in one go.

### Step 3: Delete one case and watch it fail to compile

Delete the `CANCELLED` line:

```java
        return switch (status) {
            case DRAFT           -> "Not submitted yet";
            case PENDING_PAYMENT -> "Waiting for payment";
            case CONFIRMED       -> "Your tickets are booked";
        };
```

Compile again:

```text
OrderLabels.java:4: error: the switch expression does not cover all possible input values
        return switch (status) {
               ^
1 error
```

In plain English: "If the status is `CANCELLED`, what label should I return? You haven't said, so I
won't build this." The mistake was caught **before the program ever ran**.

Put the `CANCELLED` line back afterwards.

### Step 4: The old statement form — `OldSwitchLabels.java`

The same method written the old way, with the same `CANCELLED` case missing:

```java
public class OldSwitchLabels {

    static String label(OrderStatus status) {
        String text = null;

        switch (status) {
            case DRAFT:
                text = "Not submitted yet";
                break;
            case PENDING_PAYMENT:
                text = "Waiting for payment";
                break;
            case CONFIRMED:
                text = "Your tickets are booked";
                break;
        }

        return text;
    }

    public static void main(String[] args) {
        for (OrderStatus status : OrderStatus.values()) {
            System.out.println(status + " -> " + label(status));
        }

        String shout = label(OrderStatus.CANCELLED).toUpperCase();
        System.out.println(shout);
    }
}
```

```bash
javac OrderStatus.java OldSwitchLabels.java
java OldSwitchLabels
```

Output:

```text
DRAFT -> Not submitted yet
PENDING_PAYMENT -> Waiting for payment
CONFIRMED -> Your tickets are booked
CANCELLED -> null
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "String.toUpperCase()" because the return value of "OldSwitchLabels.label(OrderStatus)" is null
	at OldSwitchLabels.main(OldSwitchLabels.java:26)
```

What happened:

1. `javac` gave **no error**. The old `switch` statement doesn't check that every status is covered.
2. For `CANCELLED`, no case matched, so `text` stayed `null`, and the customer would literally see the
   word **null** on their order page.
3. The next piece of code that used the label (`.toUpperCase()`) **crashed** with a
   `NullPointerException`, because you can't call a method on `null`.

Same mistake, two very different results:

| | `switch` expression (`->`) | `switch` statement (`case X:` + `break`) |
| --- | --- | --- |
| Missing case | **compile error** | compiles fine |
| When you find out | before the program runs | while it's running, in front of a customer |
| What the customer sees | nothing, because it never shipped | "null", then a crash |

### Why this matters

In Concept 3 we saw the same thing with traffic lights. Here it's real Zenith code: the day someone
adds a new status (say `ON_HOLD`), every `switch` **expression** over `OrderStatus` fails to compile
and points at itself. Every old-style `switch` **statement** silently returns `null` for `ON_HOLD`.

### For your `NOTES.md`

> A `switch` **expression** over an enum must cover every constant or it won't compile. When I
> deleted the `CANCELLED` case, `javac` refused with "the switch expression does not cover all
> possible input values". The old `switch` **statement** (`case X:` + `break`) compiled fine with
> the same case missing, returned `null` at runtime, and crashed later with a
> `NullPointerException`. So: always return a value from a `switch` expression, and don't add
> `default` to an enum `switch`.

---

## Subtask 2 — `TicketTier` with fields and a constructor

> **From the plan:** Give an enum fields and a constructor:
> `TicketTier { GENERAL("General", 1), VIP("VIP", 3); private final String label; private final int priority; }`.
> Note the constructor is implicitly private.

**Concepts used:** 4 (fields and a constructor), 2 (`values()`).

### What it's asking

Zenith sells different kinds of ticket. Each kind (a **tier**) carries two pieces of data:

| Tier | `label` (shown to the customer) | `priority` (bigger = more important) |
| --- | --- | --- |
| `GENERAL` | "General" | 1 |
| `VIP` | "VIP" | 3 |

`priority` could decide who enters the venue first, or who gets served first in a queue. You'll:

1. Build the enum with two fields, a constructor and getters.
2. Use it.
3. Prove the constructor is private, even though we never wrote `private`.

### Step 1: The enum — `TicketTier.java`

```java
public enum TicketTier {
    GENERAL("General", 1),
    VIP("VIP", 3);

    private final String label;
    private final int priority;

    TicketTier(String label, int priority) {
        this.label = label;
        this.priority = priority;
    }

    public String getLabel() {
        return label;
    }

    public int getPriority() {
        return priority;
    }
}
```

The same four parts from Concept 4, in order:

| Part | Code |
| --- | --- |
| 1. Constants with their data (end with `;`) | `GENERAL("General", 1),` and `VIP("VIP", 3);` |
| 2. Fields, `private final` | `label`, `priority` |
| 3. Constructor (no `public`, no `private`) | `TicketTier(String label, int priority)` |
| 4. Getters | `getLabel()`, `getPriority()` |

`VIP("VIP", 3)` means: "Create the `VIP` constant, and pass `"VIP"` and `3` to the constructor." The
constructor stores them in that constant's `label` and `priority`.

The getters are `public` here because the enum is in its own file and other classes need to read
them.

### Step 2: Use it — `TicketTierDemo.java`

```java
public class TicketTierDemo {

    public static void main(String[] args) {
        TicketTier tier = TicketTier.VIP;

        System.out.println(tier);
        System.out.println(tier.getLabel());
        System.out.println(tier.getPriority());

        System.out.println("--- all tiers ---");
        for (TicketTier t : TicketTier.values()) {
            System.out.println(t.getLabel() + " has priority " + t.getPriority());
        }

        System.out.println("--- who enters first? ---");
        TicketTier a = TicketTier.GENERAL;
        TicketTier b = TicketTier.VIP;
        if (a.getPriority() > b.getPriority()) {
            System.out.println(a.getLabel() + " enters first");
        } else {
            System.out.println(b.getLabel() + " enters first");
        }
    }
}
```

```bash
javac TicketTier.java TicketTierDemo.java
java TicketTierDemo
```

Output:

```text
VIP
VIP
3
--- all tiers ---
General has priority 1
VIP has priority 3
--- who enters first? ---
VIP enters first
```

- The first two lines both say `VIP`, but they come from different places. `println(tier)` prints
  the constant's **name** (`VIP`, Concept 2). `tier.getLabel()` prints the **label field**, which
  happens to be the text `"VIP"` too. For `GENERAL` they'd differ: the name is `GENERAL`, the label
  is `General`.
- The loop uses `values()` to show every tier with its data.
- The comparison reads each tier's priority. No `switch`, no `if (tier == VIP)`. The data lives
  inside each constant.

### Step 3: The constructor is implicitly private

"Implicitly" means "automatically, without you writing it". Two quick tests prove it.

**Test A: try to create a new tier from outside** — `MakeNewTier.java`:

```java
public class MakeNewTier {

    public static void main(String[] args) {
        TicketTier platinum = new TicketTier("Platinum", 5);
    }
}
```

```bash
javac TicketTier.java MakeNewTier.java
```

```text
MakeNewTier.java:4: error: enum classes may not be instantiated
        TicketTier platinum = new TicketTier("Platinum", 5);
                              ^
```

Nobody can make a `PLATINUM` tier while the program is running. The only tiers that exist are the
ones written at the top of `TicketTier.java`.

**Test B: try to make the constructor `public`.** In `TicketTier.java`, change
`TicketTier(String label, int priority)` to `public TicketTier(String label, int priority)`:

```text
TicketTier.java:8: error: modifier public not allowed here
    public TicketTier(String label, int priority) {
           ^
```

Java doesn't even let you *try* to open it up. If you write `private TicketTier(...)` instead, it
compiles. That's allowed, but it changes nothing, because it was already private. Change it back
afterwards.

### Why "implicitly private" matters

A private constructor is what makes an enum an enum. If anyone could call `new TicketTier(...)`, then:

- someone could create a tier with priority 999 and jump every queue,
- `==` would stop being safe (Concept 1), because there could be two different `VIP` objects,
- `values()` would no longer list every tier.

Keeping the constructor private keeps the set **closed**: exactly `GENERAL` and `VIP`, and nothing
else.

To add a new tier, a programmer edits `TicketTier.java`, for example adding `PLATINUM("Platinum", 5),`,
and the constructor forces them to give it a label and a priority (Concept 4, Step 4).

### For your `NOTES.md`

> An enum can have fields and a constructor. `VIP("VIP", 3)` passes data to the constructor once,
> when the constant is created. Fields should be `private final` because each constant is one shared
> object. The constructor is implicitly private: `new TicketTier(...)` gives "enum classes may not be
> instantiated", and writing `public` gives "modifier public not allowed here". That's what keeps the
> set of tiers closed.

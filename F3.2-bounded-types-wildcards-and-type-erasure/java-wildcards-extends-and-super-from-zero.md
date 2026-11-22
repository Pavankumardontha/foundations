# Java Wildcards from Zero: `? extends` and `? super`

This is a complete guide to Java wildcards on its own. It starts from zero and builds up one idea
at a time. Every program in it was compiled and run on Java 21. Every output and every error
message shown is the real one, copied from the terminal.

## Contents

- Part 0: What you need before wildcards
- Part 1: The problem wildcards solve
- Part 2: What a wildcard is
- Part 3: The method — list the possibilities, then apply three rules
- Part 4: `? extends X` in depth
- Part 5: `? super X` in depth
- Part 6: Reading the error messages (what `CAP#1` means)
- Part 7: Worked examples — assigning one label to another
- Part 8: Worked examples with methods — PECS
- Part 9: The same rules with a different family: numbers
- Part 10: Wildcards inside Java's own library
- Part 11: A wildcard `?` versus a named type `T`
- Part 12: Practice questions with answers
- Part 13: Cheat sheet

---

# Part 0: What you need before wildcards

## 0.1 The family of classes used everywhere in this guide

Every example uses these four classes. Put each one in its own file, in the same folder as the
example programs.

`Animal.java`

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

`Dog.java`

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

`Cat.java`

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

`Puppy.java`

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

The family tree looks like this. Every class in Java is ultimately below `Object`.

```
              Object
                |
              Animal          (has eat())
             /      \
          Dog        Cat      (Dog adds bark(), Cat adds meow())
           |
         Puppy                (adds wagTail())
```

How to run any example: put the four class files and the example file in one folder, then:

```
javac *.java
java ExampleName
```

## 0.2 "Is a": a variable can hold its own type or anything below it

Because `Dog extends Animal`, every Dog **is an** Animal. So a variable whose declared type is
`Animal` is allowed to hold a Dog.

```java
import java.util.ArrayList;
import java.util.List;

public class IsADemo {

    public static void main(String[] args) {
        Dog rex = new Dog("Rex");
        Animal pet = rex;
        pet.eat();
    }
}
```

Output:

```
Rex is eating
```

The variables:

- `rex` is declared as `Dog`. It holds the Dog named Rex.
- `pet` is declared as `Animal`. It holds the same Dog named Rex.

Two words we will use a lot:

- **Below** (a "subtype"): Dog is below Animal. Puppy is below Dog, and also below Animal.
- **Above** (a "supertype"): Animal is above Dog. Object is above everything.

The rule: **a variable of type X can hold an X, or anything below X.**

One more fact: the compiler lets you call only the methods of the **declared type**. `pet` is
declared as `Animal`, so `pet.eat()` is allowed but `pet.bark()` is not, even though the object
really is a Dog. The compiler judges by the label, not by the object.

## 0.3 A variable is a label, not a box

When you write `listB = listA`, Java does **not** copy the list. It sticks a second label onto
the **same** list.

```java
import java.util.ArrayList;
import java.util.List;

public class LabelNotCopy {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        List<Dog> sameDogList = dogList;

        sameDogList.add(new Dog("Rex"));

        System.out.println(dogList.size());
        System.out.println(dogList == sameDogList);
    }
}
```

Output:

```
1
true
```

The variables:

- `dogList` is declared as `List<Dog>`. It points at one list object.
- `sameDogList` is declared as `List<Dog>`. It points at **the same** list object.

We added Rex through `sameDogList`, and `dogList` sees him (size 1). `==` is `true`, which means
both labels point at one single list. Keep this in mind: it is the reason wildcards exist.

## 0.4 The compiler only knows declared types

The compiler checks your code **before** it runs. It does not run your program in its head and
does not remember what you assigned earlier. When it looks at a line, the only thing it knows
about a variable is its **declared type** (the type written on the left when the variable was
created).

## 0.5 A method call is an assignment

When you call a method, Java assigns each argument to its parameter:

```
static void feedAll(List<Animal> animalList) { ... }

feedAll(dogList);     is checked exactly like     List<Animal> animalList = dogList;
```

So everything in this guide about assignment also applies to method calls.

---

# Part 1: The problem wildcards solve

## 1.1 A `List<Dog>` is not a `List<Animal>`

A Dog is an Animal. You might expect a list of Dogs to be a list of Animals. Java says no:

```java
import java.util.ArrayList;
import java.util.List;

public class DogListNotAnimalList {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        List<Animal> animalList = dogList;
    }
}
```

Compiler output:

```
DogListNotAnimalList.java:8: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        List<Animal> animalList = dogList;
                                  ^
1 error
```

In plain English: "You have a list of Dogs. You are trying to put a list-of-Animals label on it.
I won't allow that."

## 1.2 Why Java refuses

Imagine Java allowed it. Remember that a variable is only a label (0.3). Here is what could
happen. **This code does not compile.** It shows what would go wrong if it did:

```
List<Dog> dogList = new ArrayList<>();
List<Animal> animalList = dogList;      // imagine this were allowed

animalList.add(new Cat("Tom"));         // fine for a List<Animal>: a Cat is an Animal
Dog firstDog = dogList.get(0);          // dogList promises Dogs... but it's Tom the Cat!
firstDog.bark();                        // a Cat cannot bark -> crash
```

Picture it:

```
dogList    ───┐
              ├──►  [ Tom the Cat ]     ← one single list object
animalList ───┘
```

`animalList` promises "anything that is an Animal can go in". `dogList` promises "everything in
here is a Dog". Both labels point at one list, and both promises cannot be true at once. Java
stops it at the very first step: the assignment.

## 1.3 The same problem with methods

Because a method call is an assignment (0.5), the same refusal happens when you pass a list to a
method:

```java
import java.util.ArrayList;
import java.util.List;

public class MethodRefused {

    static void feedAll(List<Animal> animalList) {
        for (Animal animal : animalList) {
            animal.eat();
        }
    }

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        feedAll(dogList);
    }
}
```

Compiler output:

```
MethodRefused.java:15: error: incompatible types: List<Dog> cannot be converted to List<Animal>
        feedAll(dogList);
                ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

This is annoying. `feedAll` only **reads** the animals and feeds them. Feeding Dogs is perfectly
safe. But Java refuses because the parameter type `List<Animal>` would also allow adding a Cat.

## 1.4 The rule behind the refusal

The type inside `< >` must match **exactly**. `List<Dog>` fits only a `List<Dog>` label.

(The part outside `< >` may still use "is a". That is why `List<Dog> dogList = new ArrayList<>();`
works: an ArrayList is a List.)

## 1.5 The two needs

We want two things that the exact rule doesn't allow:

1. **A method that reads from many kinds of list**, for example "feed every animal in this list,
   whether it's a list of Dogs, Cats or Animals". The tool for this is `? extends`.
2. **A method that adds into many kinds of list**, for example "put two Dogs into this list,
   whether it's a list of Dogs, Animals or Objects". The tool for this is `? super`.

---

# Part 2: What a wildcard is

## 2.1 The question mark

A `?` inside `< >` means:

> "This list holds **one specific type**. I'm not telling you which one, but it is somewhere in
> this range."

The `?` is called a **wildcard**. It means "unknown", not "anything". A `List<? extends Animal>`
is **not** a list where you can mix any animals. It is a label that points at a list of **one**
type, and that type is Animal or something below Animal.

## 2.2 The two kinds of wildcard

**`? extends X`: X or anything below X.** X is a **ceiling**.

```
List<? extends Animal>  could point at:

              Object          ✗ (above the ceiling)
                |
   ceiling →  Animal          ✓ List<Animal>
             /      \
          Dog        Cat      ✓ List<Dog>   ✓ List<Cat>
           |
         Puppy                ✓ List<Puppy>
```

**`? super X`: X or anything above X.** X is a **floor**.

```
List<? super Dog>  could point at:

              Object          ✓ List<Object>
                |
              Animal          ✓ List<Animal>
             /      \
  floor →   Dog      Cat      ✓ List<Dog>    ✗ List<Cat> (not above Dog)
           |
         Puppy                ✗ (below the floor)
```

(There is also a plain `?`, which means `? extends Object`: "a list of some type, any type".
Everything in this guide about `? extends` applies to it, with Object as the ceiling.)

## 2.3 How variables are named in this guide

Every variable name tells you its declared type:

- `animalList` is a `List<Animal>`
- `dogList` is a `List<Dog>`
- `catList` is a `List<Cat>`
- `puppyList` is a `List<Puppy>`
- `objectList` is a `List<Object>`
- `animalsOrBelow` is a `List<? extends Animal>`
- `dogsOrBelow` is a `List<? extends Dog>`
- `dogsOrAbove` is a `List<? super Dog>`
- `puppiesOrAbove` is a `List<? super Puppy>`
- `animalsOrAbove` is a `List<? super Animal>`

The number examples (Part 9) use `integerList`, `doubleList`, `longList`, `numberList`,
`numbersOrBelow` (a `List<? extends Number>`) and `integersOrAbove` (a `List<? super Integer>`).

---

# Part 3: The method — list the possibilities, then apply three rules

This is the one tool you need. It answers every wildcard question.

## 3.1 Step 1: list the possibilities

For any label, write down every kind of list it **could** be pointing at. Use only the
**declared type**, because that is all the compiler knows (0.4).

- `List<Dog> dogList` has exactly one possibility: `List<Dog>`.
- `List<? extends Animal> animalsOrBelow` could be a `List<Animal>`, `List<Dog>`, `List<Cat>`,
  `List<Puppy>`, **or a list of any Animal subclass someone writes later** (say a
  `class Parrot extends Animal` written next year).
- `List<? extends Dog> dogsOrBelow` could be a `List<Dog>`, `List<Puppy>`, or a list of any Dog
  subclass written later.
- `List<? super Dog> dogsOrAbove` could be a `List<Dog>`, `List<Animal>` or `List<Object>`.
  Nothing can be added later, because Dog's parents are already fixed.
- `List<? super Puppy> puppiesOrAbove` could be a `List<Puppy>`, `List<Dog>`, `List<Animal>` or
  `List<Object>`.
- `List<? super Animal> animalsOrAbove` could be a `List<Animal>` or `List<Object>`.

Even if you just assigned `dogList` to `animalsOrBelow` on the line above, the compiler does not
remember that. On every line, `animalsOrBelow` could be any of its possibilities.

## 3.2 Step 2: apply the rule for what you're doing

There are only three things you do with a list label: point another label at it, add to it, and
read from it. Each has one rule.

**Rule 1: Assigning (`left = right`).**
Allowed only if **every possibility of the right side is also a possibility of the left side.**

Why: after the assignment, the left label points at whatever the right side was pointing at.
That had better be something the left label is allowed to point at.

**Rule 2: Adding an item (`label.add(item)`).**
Allowed only if **the item fits into every possibility.**

Why: the compiler doesn't know which list it really is, so the item must be safe in all of them.
If even one possibility would be broken, the add is refused.

**Rule 3: Reading as a type R (`R value = label.get(0)`).**
Allowed only if **every item of every possibility is guaranteed to be an R.**

Why: the compiler doesn't know which list it really is, so it can only promise what is true for
all of them.

Remember that a method call is an assignment (0.5), so passing an argument to a parameter is
checked with Rule 1.

## 3.3 What falls out of the rules

When you apply the rules, a clear pattern appears:

- `? extends X`: you can **read** items as X. You **cannot add** anything (except `null`).
- `? super X`: you can **add** an X (or anything below X). You can only **read** items as `Object`.

Parts 4 and 5 show why, with real programs.

---

# Part 4: `? extends X` in depth

## 4.1 One method that accepts many kinds of list

Change the parameter of `feedAll` from `List<Animal>` to `List<? extends Animal>`:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsAcceptsMany {

    static void feedAll(List<? extends Animal> animalsOrBelow) {
        for (Animal animal : animalsOrBelow) {
            animal.eat();
        }
    }

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        animalList.add(new Cat("Kitty"));
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));
        List<Puppy> puppyList = new ArrayList<>();
        puppyList.add(new Puppy("Bolt"));

        feedAll(animalList);
        feedAll(dogList);
        feedAll(catList);
        feedAll(puppyList);
    }
}
```

Output:

```
Kitty is eating
Rex is eating
Tom is eating
Bolt is eating
```

The variables:

- `animalList` is a `List<Animal>` holding Kitty the Cat.
- `dogList` is a `List<Dog>` holding Rex.
- `catList` is a `List<Cat>` holding Tom.
- `puppyList` is a `List<Puppy>` holding Bolt.
- `animalsOrBelow` (the parameter) is a `List<? extends Animal>`. It promises: "I point at a list
  of Animal, or of something below Animal".

Check with Rule 1. Each call is the assignment `animalsOrBelow = argument`:

- `feedAll(animalList)`: the right side has one possibility, `List<Animal>`. Is that a
  possibility of `List<? extends Animal>`? Yes. Allowed.
- `feedAll(dogList)`: `List<Dog>` is a possibility of `List<? extends Animal>`. Allowed.
- `feedAll(catList)`: `List<Cat>`, yes. Allowed.
- `feedAll(puppyList)`: `List<Puppy>`, yes (Puppy is below Dog, which is below Animal). Allowed.

A list that is not in the range is still refused:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsRefusesStrings {

    static void feedAll(List<? extends Animal> animalsOrBelow) {
        for (Animal animal : animalsOrBelow) {
            animal.eat();
        }
    }

    public static void main(String[] args) {
        List<String> stringList = new ArrayList<>();
        stringList.add("A1");
        feedAll(stringList);
    }
}
```

Compiler output:

```
ExtendsRefusesStrings.java:15: error: incompatible types: List<String> cannot be converted to List<? extends Animal>
        feedAll(stringList);
                ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`stringList` is a `List<String>`. String is not Animal or below, so `List<String>` is not a
possibility of `List<? extends Animal>`. Rule 1 fails.

## 4.2 A wildcard label can be moved to a different list

A wildcard label is not stuck to one list. You can point it at a Dog list, then at a Cat list:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsRelabel {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));
        catList.add(new Cat("Kitty"));

        List<? extends Animal> animalsOrBelow;

        animalsOrBelow = dogList;
        System.out.println("Pointing at dogList: " + animalsOrBelow.size() + " item");

        animalsOrBelow = catList;
        System.out.println("Pointing at catList: " + animalsOrBelow.size() + " items");
    }
}
```

Output:

```
Pointing at dogList: 1 item
Pointing at catList: 2 items
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `catList` is a `List<Cat>` holding Tom and Kitty.
- `animalsOrBelow` is a `List<? extends Animal>`. First it points at `dogList`, then at `catList`.

Picture it. The label moves; the lists never change type:

```
first:   animalsOrBelow ──►  dogList  [ Rex ]
then:    animalsOrBelow ──►  catList  [ Tom, Kitty ]
```

## 4.3 The compiler cannot know which list it really is

Sometimes nobody knows which list the label points at until the program runs:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsDecidedAtRuntime {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));

        List<? extends Animal> animalsOrBelow;
        if (args.length > 0) {
            animalsOrBelow = dogList;
        } else {
            animalsOrBelow = catList;
        }

        Animal firstAnimal = animalsOrBelow.get(0);
        firstAnimal.eat();
    }
}
```

Run without anything after the name (`java ExtendsDecidedAtRuntime`):

```
Tom is eating
```

Run with a word after the name (`java ExtendsDecidedAtRuntime anything`):

```
Rex is eating
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `catList` is a `List<Cat>` holding Tom.
- `animalsOrBelow` is a `List<? extends Animal>`. Which list it points at depends on how you run
  the program.
- `firstAnimal` is declared as `Animal`. It holds the first item of whichever list was chosen.

The compiler checked this code once, before either run. It had to approve code that works for
**both**. That is exactly why it uses "every possibility" in the three rules.

## 4.4 Reading from `? extends`: allowed as the ceiling type

Setup used in this section and the next one:

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at `dogList`.

**Reading as Animal: allowed.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsReadAnimal {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        Animal firstAnimal = animalsOrBelow.get(0);
        Object firstObject = animalsOrBelow.get(0);
        firstAnimal.eat();
        System.out.println(firstObject.getClass().getSimpleName());
    }
}
```

Output:

```
Rex is eating
Dog
```

The new variables:

- `firstAnimal` is declared as `Animal`. It holds Rex.
- `firstObject` is declared as `Object`. It also holds Rex. (`getClass().getSimpleName()` prints
  the real class of the object, which is `Dog`.)

Check with Rule 3. Is every item of every possibility an Animal?

```
Possibility of animalsOrBelow     Items in it are...       Is every item an Animal?
List<Animal>                      Animals                  yes
List<Dog>                         Dogs                     yes
List<Cat>                         Cats                     yes
List<Puppy>                       Puppies                  yes
List<Parrot> (written later)      Parrots                  yes (Parrot extends Animal)
```

Yes in every row, so reading as Animal is allowed. Anything above Animal (Object) is also fine.

**Reading as Dog: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsReadDog {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        Dog firstDog = animalsOrBelow.get(0);
    }
}
```

Compiler output:

```
ExtendsReadDog.java:11: error: incompatible types: CAP#1 cannot be converted to Dog
        Dog firstDog = animalsOrBelow.get(0);
                                         ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

Check with Rule 3. Is every item of every possibility a Dog?

```
Possibility of animalsOrBelow     Is every item a Dog?
List<Animal>                      no (could hold a Cat)
List<Dog>                         yes
List<Cat>                         NO
List<Puppy>                       yes
```

Not every row says yes, so it is refused. Yes, we know the list is really `dogList`. The compiler
doesn't remember that; it only knows the declared type `List<? extends Animal>`.

In plain English, the error says: "The item has some unknown type I'm calling `CAP#1`. All I know
is that `CAP#1` is Animal or below. That's not enough to promise it's a Dog." (Part 6 explains
`CAP#1` fully.)

## 4.5 Adding to `? extends`: refused, whatever the item

Same setup: `dogList` is a `List<Dog>` holding Rex, and `animalsOrBelow` is a
`List<? extends Animal>` pointing at it.

**Adding a Dog: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsAddDog {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        animalsOrBelow.add(new Dog("Bruno"));
    }
}
```

Compiler output:

```
ExtendsAddDog.java:11: error: incompatible types: Dog cannot be converted to CAP#1
        animalsOrBelow.add(new Dog("Bruno"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Check with Rule 2. Does a Dog fit into every possibility?

```
Possibility of animalsOrBelow     Can it hold a Dog?
List<Animal>                      yes
List<Dog>                         yes
List<Cat>                         NO   (a Dog in a Cat list)
List<Puppy>                       NO   (a Dog is not a Puppy)
```

**Adding a Cat: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsAddCat {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        animalsOrBelow.add(new Cat("Tom"));
    }
}
```

Compiler output:

```
ExtendsAddCat.java:11: error: incompatible types: Cat cannot be converted to CAP#1
        animalsOrBelow.add(new Cat("Tom"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```
Possibility of animalsOrBelow     Can it hold a Cat?
List<Animal>                      yes
List<Dog>                         NO
List<Cat>                         yes
List<Puppy>                       NO
```

This is the exact disaster from 1.2: here the list really is `dogList`, so a Cat would sneak into
a Dog list.

**Adding a Puppy: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsAddPuppy {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        animalsOrBelow.add(new Puppy("Bolt"));
    }
}
```

Compiler output:

```
ExtendsAddPuppy.java:11: error: incompatible types: Puppy cannot be converted to CAP#1
        animalsOrBelow.add(new Puppy("Bolt"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```
Possibility of animalsOrBelow     Can it hold a Puppy?
List<Animal>                      yes
List<Dog>                         yes
List<Cat>                         NO
List<Puppy>                       yes
```

Puppy is the lowest class we have, and it still fails because of the Cat list.

**Adding a plain Animal: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsAddAnimal {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        animalsOrBelow.add(new Animal("Generic"));
    }
}
```

Compiler output:

```
ExtendsAddAnimal.java:11: error: incompatible types: Animal cannot be converted to CAP#1
        animalsOrBelow.add(new Animal("Generic"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```
Possibility of animalsOrBelow     Can it hold a plain Animal?
List<Animal>                      yes
List<Dog>                         NO
List<Cat>                         NO
List<Puppy>                       NO
```

**Why no item can ever pass.** Whatever item you pick, there is always a possibility it doesn't fit:

- a Dog doesn't fit a Cat list;
- a Cat doesn't fit a Dog list;
- an Animal doesn't fit a Dog list;
- and even if you invented a class below every animal, it wouldn't fit a list of some Animal
  subclass written later (like `List<Parrot>`).

So for `? extends`, Rule 2 always fails. The only exception is `null`, which fits into every list.

## 4.6 What still works on a `? extends` list

You can't add, but you can still do everything that doesn't put a new item in:

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsOtherMethods {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<? extends Animal> animalsOrBelow = dogList;

        System.out.println(animalsOrBelow.size());
        System.out.println(animalsOrBelow.contains(new Cat("Tom")));
        animalsOrBelow.add(null);
        System.out.println(animalsOrBelow.size());
        Animal removed = animalsOrBelow.remove(0);
        System.out.println(removed.name() + " removed");
        animalsOrBelow.clear();
        System.out.println(dogList.size());
    }
}
```

Output:

```
1
false
2
Rex removed
0
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>` pointing at `dogList`.
- `removed` is declared as `Animal`. It holds the item that `remove(0)` took out (Rex).

Line by line:

- `size()` gives 1. Counting is always safe.
- `contains(new Cat("Tom"))` compiles because `contains` accepts any Object. It only looks, it
  never adds. It prints `false` because Tom is not in the list.
- `add(null)` is allowed, because `null` fits every list. The size becomes 2.
- `remove(0)` takes out Rex and gives him back as an Animal (reading as the ceiling type, Rule 3).
- `clear()` empties the list. `dogList.size()` prints 0, which proves `animalsOrBelow` and
  `dogList` are the same list.

## 4.7 The same refusal inside a method

The protection also works inside a method body. This is what makes `List<? extends Animal>` a
safe parameter type: the method promises callers it will only read.

```java
import java.util.ArrayList;
import java.util.List;

public class ExtendsMethodCantAdd {

    static void feedAll(List<? extends Animal> animalsOrBelow) {
        for (Animal animal : animalsOrBelow) {
            animal.eat();
        }
        animalsOrBelow.add(new Dog("Intruder"));
    }

    public static void main(String[] args) {

    }
}
```

Compiler output:

```
ExtendsMethodCantAdd.java:10: error: incompatible types: Dog cannot be converted to CAP#1
        animalsOrBelow.add(new Dog("Intruder"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

Someone could call `feedAll(catList)`. If this add were allowed, a Dog would end up in a Cat list.

## 4.8 Summary of `? extends X`

- It means: "a list of one specific type, which is X or below X".
- It accepts a `List<X>` or a list of anything below X.
- **Read:** yes, as X (or anything above X).
- **Add:** no, except `null`.
- Use it when a method only **takes items out** of a list.

---

# Part 5: `? super X` in depth

## 5.1 The problem: a method that adds Dogs

This method puts two Dogs into a list:

```java
import java.util.ArrayList;
import java.util.List;

public class ExactDogListOnly {

    static void addTwoDogs(List<Dog> dogList) {
        dogList.add(new Dog("Rex"));
        dogList.add(new Dog("Bruno"));
    }

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        addTwoDogs(animalList);
    }
}
```

Compiler output:

```
ExactDogListOnly.java:13: error: incompatible types: List<Animal> cannot be converted to List<Dog>
        addTwoDogs(animalList);
                   ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The variables:

- `animalList` is a `List<Animal>`, empty.
- `dogList` (the parameter) is a `List<Dog>`.

Putting Dogs into a list of Animals is perfectly safe, since a Dog is an Animal. But the exact
rule refuses it: `List<Animal>` is not `List<Dog>`.

`? extends` can't help here, because it refuses all adds. We need the opposite wildcard.

## 5.2 One method that adds into many kinds of list

Change the parameter to `List<? super Dog>`:

```java
import java.util.ArrayList;
import java.util.List;

public class SuperAcceptsMany {

    static void addTwoDogs(List<? super Dog> dogsOrAbove) {
        dogsOrAbove.add(new Dog("Rex"));
        dogsOrAbove.add(new Dog("Bruno"));
    }

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        List<Animal> animalList = new ArrayList<>();
        animalList.add(new Cat("Kitty"));
        List<Object> objectList = new ArrayList<>();
        objectList.add("A1");

        addTwoDogs(dogList);
        addTwoDogs(animalList);
        addTwoDogs(objectList);

        System.out.println(dogList.size() + " in dogList");
        System.out.println(animalList.size() + " in animalList");
        System.out.println(objectList.size() + " in objectList");
    }
}
```

Output:

```
2 in dogList
3 in animalList
3 in objectList
```

The variables:

- `dogList` is a `List<Dog>`, empty at the start.
- `animalList` is a `List<Animal>`, holding Kitty the Cat at the start.
- `objectList` is a `List<Object>`, holding the String `"A1"` at the start.
- `dogsOrAbove` (the parameter) is a `List<? super Dog>`. It promises: "I point at a list of Dog,
  or of something above Dog".

Check each call with Rule 1 (`dogsOrAbove = argument`):

- `addTwoDogs(dogList)`: `List<Dog>` is a possibility of `List<? super Dog>`. Allowed. It ends up
  with 2 items.
- `addTwoDogs(animalList)`: `List<Animal>`, yes. Allowed. Kitty plus 2 Dogs makes 3.
- `addTwoDogs(objectList)`: `List<Object>`, yes. Allowed. "A1" plus 2 Dogs makes 3.

Lists below the floor, or off to the side, are refused:

```java
import java.util.ArrayList;
import java.util.List;

public class SuperRefusesPuppyList {

    static void addTwoDogs(List<? super Dog> dogsOrAbove) {
        dogsOrAbove.add(new Dog("Rex"));
        dogsOrAbove.add(new Dog("Bruno"));
    }

    public static void main(String[] args) {
        List<Puppy> puppyList = new ArrayList<>();
        addTwoDogs(puppyList);
    }
}
```

Compiler output:

```
SuperRefusesPuppyList.java:13: error: incompatible types: List<Puppy> cannot be converted to List<? super Dog>
        addTwoDogs(puppyList);
                   ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`puppyList` is a `List<Puppy>`. Puppy is **below** Dog, so it is not "Dog or above". It must be
refused: otherwise plain Dogs (Rex and Bruno, who are not Puppies) would land in a Puppy list.

```java
import java.util.ArrayList;
import java.util.List;

public class SuperRefusesCatList {

    static void addTwoDogs(List<? super Dog> dogsOrAbove) {
        dogsOrAbove.add(new Dog("Rex"));
        dogsOrAbove.add(new Dog("Bruno"));
    }

    public static void main(String[] args) {
        List<Cat> catList = new ArrayList<>();
        addTwoDogs(catList);
    }
}
```

Compiler output:

```
SuperRefusesCatList.java:13: error: incompatible types: List<Cat> cannot be converted to List<? super Dog>
        addTwoDogs(catList);
                   ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`catList` is a `List<Cat>`. Cat is neither Dog nor above Dog (it's a sibling), so it's refused.

## 5.3 Adding to `? super`: Dog and anything below Dog is allowed

Setup used in this section:

- `animalList` is a `List<Animal>` holding Kitty the Cat.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `animalList`.

**Adding a Dog and a Puppy: allowed.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperAddDogAndPuppy {

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        animalList.add(new Cat("Kitty"));
        List<? super Dog> dogsOrAbove = animalList;

        dogsOrAbove.add(new Dog("Rex"));
        dogsOrAbove.add(new Puppy("Bolt"));
        System.out.println(animalList.size());
    }
}
```

Output:

```
3
```

Check with Rule 2:

```
Possibility of dogsOrAbove     Can it hold a Dog?     Can it hold a Puppy?
List<Dog>                      yes                    yes (a Puppy is a Dog)
List<Animal>                   yes                    yes
List<Object>                   yes                    yes
```

Every row says yes, so both adds are allowed. In general: **whatever the list is, it holds Dogs
or something above Dogs. So a Dog always fits, and so does anything below Dog.**

**Adding a plain Animal: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperAddAnimal {

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        animalList.add(new Cat("Kitty"));
        List<? super Dog> dogsOrAbove = animalList;

        dogsOrAbove.add(new Animal("Generic"));
    }
}
```

Compiler output:

```
SuperAddAnimal.java:11: error: incompatible types: Animal cannot be converted to CAP#1
        dogsOrAbove.add(new Animal("Generic"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```
Possibility of dogsOrAbove     Can it hold a plain Animal?
List<Dog>                      NO
List<Animal>                   yes
List<Object>                   yes
```

The list really is `animalList`, but the compiler doesn't remember that. It could be a
`List<Dog>`, and a plain Animal must not go into a Dog list.

**Adding a Cat: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperAddCat {

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        animalList.add(new Cat("Kitty"));
        List<? super Dog> dogsOrAbove = animalList;

        dogsOrAbove.add(new Cat("Tom"));
    }
}
```

Compiler output:

```
SuperAddCat.java:11: error: incompatible types: Cat cannot be converted to CAP#1
        dogsOrAbove.add(new Cat("Tom"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

```
Possibility of dogsOrAbove     Can it hold a Cat?
List<Dog>                      NO
List<Animal>                   yes
List<Object>                   yes
```

## 5.4 Reading from `? super`: only as Object

Setup used in this section:

- `objectList` is a `List<Object>` holding the String `"A1"`.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `objectList`.
- Then a Dog named Rex is added through `dogsOrAbove`, so the list is `["A1", Rex]`.

**Reading as Object: allowed.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperReadObject {

    public static void main(String[] args) {
        List<Object> objectList = new ArrayList<>();
        objectList.add("A1");
        List<? super Dog> dogsOrAbove = objectList;
        dogsOrAbove.add(new Dog("Rex"));

        Object firstItem = dogsOrAbove.get(0);
        Object secondItem = dogsOrAbove.get(1);
        System.out.println(firstItem);
        System.out.println(secondItem.getClass().getSimpleName());
    }
}
```

Output:

```
A1
Dog
```

The new variables:

- `firstItem` is declared as `Object`. It holds the String `"A1"`.
- `secondItem` is declared as `Object`. It holds Rex the Dog.

Check with Rule 3:

```
Possibility of dogsOrAbove     Items in it could be...                 Every item an Object?
List<Dog>                      Dogs                                    yes
List<Animal>                   Dogs, Cats, any Animal                  yes
List<Object>                   anything at all: Strings, numbers...    yes
```

Everything in Java is an Object, so reading as Object is always allowed.

**Reading as Animal: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperReadAnimal {

    public static void main(String[] args) {
        List<Object> objectList = new ArrayList<>();
        objectList.add("A1");
        List<? super Dog> dogsOrAbove = objectList;
        dogsOrAbove.add(new Dog("Rex"));

        Animal firstAnimal = dogsOrAbove.get(0);
    }
}
```

Compiler output:

```
SuperReadAnimal.java:12: error: incompatible types: CAP#1 cannot be converted to Animal
        Animal firstAnimal = dogsOrAbove.get(0);
                                            ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

```
Possibility of dogsOrAbove     Is every item an Animal?
List<Dog>                      yes
List<Animal>                   yes
List<Object>                   NO (here, item 0 is the String "A1")
```

This isn't only theory. Item 0 really is `"A1"`, a String. If Java allowed this line, a String
would be sitting in an `Animal` variable.

**Reading as Dog: refused.**

```java
import java.util.ArrayList;
import java.util.List;

public class SuperReadDog {

    public static void main(String[] args) {
        List<Object> objectList = new ArrayList<>();
        objectList.add("A1");
        List<? super Dog> dogsOrAbove = objectList;
        dogsOrAbove.add(new Dog("Rex"));

        Dog firstDog = dogsOrAbove.get(0);
    }
}
```

Compiler output:

```
SuperReadDog.java:12: error: incompatible types: CAP#1 cannot be converted to Dog
        Dog firstDog = dogsOrAbove.get(0);
                                      ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

```
Possibility of dogsOrAbove     Is every item a Dog?
List<Dog>                      yes
List<Animal>                   NO (could hold a Cat)
List<Object>                   NO (could hold a String)
```

People often expect "`? super Dog` means Dogs come out". They don't: Dogs **go in**, but whatever
was already in the list (Cats, Strings...) can come out.

## 5.5 Summary of `? super X`

- It means: "a list of one specific type, which is X or above X".
- It accepts a `List<X>` or a list of anything above X.
- **Add:** yes, an X or anything below X.
- **Read:** only as `Object`.
- Use it when a method only **puts items into** a list.

## 5.6 The two wildcards side by side

```
                       ? extends Animal                   ? super Dog
Range                  Animal or below (ceiling)          Dog or above (floor)
Points at, e.g.        List<Animal>, List<Dog>,           List<Dog>, List<Animal>,
                       List<Cat>, List<Puppy>             List<Object>
Add                    nothing (only null)                Dog, Puppy
Read as                Animal (or Object)                 Object only
Good for               taking items out                   putting items in
```

---

# Part 6: Reading the error messages (what `CAP#1` means)

## 6.1 Where `CAP#1` comes from

When you **use** a wildcard label (call `add`, call `get`, assign it to something, pass it to a
method), the compiler has to work with "the one specific type behind the `?`". It doesn't know
what that type is, so it invents a placeholder name for it: `CAP#1`.

- `CAP` is short for **capture**: the compiler "captures" the unknown type and gives it a name.
- `#1` is just a counter. If one error message involves two unknown types, they are called
  `CAP#1` and `CAP#2`. The counting restarts in every error message.
- "fresh type-variable" means "a new placeholder name I just made up".

`CAP#1` is not a class you wrote, and it is not a class that exists anywhere. It is the
compiler's way of saying "the unknown type".

## 6.2 Decoding the `? extends` error line by line

From `ExtendsAddDog` (4.5):

```
ExtendsAddDog.java:11: error: incompatible types: Dog cannot be converted to CAP#1
        animalsOrBelow.add(new Dog("Bruno"));
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

- `Dog cannot be converted to CAP#1`: "You're giving me a Dog, but this list only takes items of
  the unknown type CAP#1."
- `CAP#1 extends Animal`: "All I know about CAP#1 is that it's Animal or below."
- `from capture of ? extends Animal`: "I made CAP#1 from the `? extends Animal` in the declared
  type of `animalsOrBelow`."

Put together: "The list holds some unknown type that is Animal or below. A Dog is not guaranteed
to be that type (it could be Cat), so I can't put the Dog in."

## 6.3 Decoding the `? super` error line by line

From `SuperAddAnimal` (5.3):

```
SuperAddAnimal.java:11: error: incompatible types: Animal cannot be converted to CAP#1
        dogsOrAbove.add(new Animal("Generic"));
                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
```

- `Animal cannot be converted to CAP#1`: "You're giving me an Animal, but this list only takes
  items of the unknown type CAP#1."
- `CAP#1 extends Object super: Dog`: "All I know about CAP#1 is that it's below Object and above
  Dog." In other words, Dog or above.
- `from capture of ? super Dog`: "I made it from the `? super Dog` in the declared type."

Put together: "The list holds some unknown type that is Dog or above. If that type is exactly
Dog, a plain Animal doesn't fit."

## 6.4 Reading errors: `CAP#1 cannot be converted to ...`

From `ExtendsReadDog` (4.4): `CAP#1 cannot be converted to Dog` with `CAP#1 extends Animal`. This
means "The item you're reading is of the unknown type. It's guaranteed to be an Animal, but not
guaranteed to be a Dog."

From `SuperReadAnimal` (5.4): `CAP#1 cannot be converted to Animal` with
`CAP#1 extends Object super: Dog`. This means "The item is of the unknown type. All that's
guaranteed is that it's an Object."

**Trick:** look at the word after `extends` in the `where` line. That is the most you can read
items as. For `? extends Animal` it's Animal. For `? super Dog` it's Object.

## 6.5 Assignment errors: `List<CAP#1> cannot be converted to ...`

When the error is about assigning one wildcard label to another, you see `List<CAP#1>`:

```
AssignWildcardToExact.java:11: error: incompatible types: List<CAP#1> cannot be converted to List<Animal>
        List<Animal> animalList = animalsOrBelow;
                                  ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
```

Only the label **being used** (the right side, here `animalsOrBelow`) gets captured. The compiler
says: "`animalsOrBelow` points at a list of some unknown type `CAP#1`, which is Animal or below.
Can a `List<CAP#1>` be put under a `List<Animal>` label?" The left side keeps its written type,
because it describes what the left label **accepts**, not a particular list.

Answer: only if `CAP#1` is exactly Animal, and that's not guaranteed (it could be Dog). So it's
refused. This matches Rule 1, and Part 7 has many examples like it.

---

# Part 7: Worked examples — assigning one label to another

Every example here uses Rule 1: **`left = right` is allowed only if every possibility of the right
side is also a possibility of the left side.** For each one: name the variables, list the
possibilities of both sides, compare.

## Example 7.1: an exact list into a wildcard label (allowed)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignExactToWildcard {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));

        List<? extends Animal> animalsOrBelow = dogList;
        System.out.println(animalsOrBelow.size());
    }
}
```

Output:

```
1
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`.

```
Right side (dogList) could be:       List<Dog>
Left side (animalsOrBelow) accepts:  List<Animal>, List<Dog>, List<Cat>, List<Puppy>, ...
Is List<Dog> accepted?               yes  →  allowed
```

## Example 7.2: one wildcard label into a wider one (allowed)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignPuppiesUp {

    public static void main(String[] args) {
        List<Puppy> puppyList = new ArrayList<>();
        puppyList.add(new Puppy("Bolt"));

        List<? extends Dog> dogsOrBelow = puppyList;
        List<? extends Animal> animalsOrBelow = dogsOrBelow;

        System.out.println(animalsOrBelow.size());
    }
}
```

Output:

```
1
```

The variables:

- `puppyList` is a `List<Puppy>` holding Bolt.
- `dogsOrBelow` is a `List<? extends Dog>`, pointing at `puppyList`.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at the same list.

First assignment, `dogsOrBelow = puppyList`:

```
Right side (puppyList) could be:   List<Puppy>
Left side (dogsOrBelow) accepts:   List<Dog>, List<Puppy>, (Dog subclasses written later)
List<Puppy> accepted?              yes  →  allowed
```

Second assignment, `animalsOrBelow = dogsOrBelow`:

```
Right side (dogsOrBelow) could be:     List<Dog>, List<Puppy>, (Dog subclasses written later)
Left side (animalsOrBelow) accepts:    List<Animal>, List<Dog>, List<Cat>, List<Puppy>,
                                       (Animal subclasses written later)
Every right possibility accepted?      List<Dog> yes, List<Puppy> yes,
                                       a later Dog subclass is also an Animal subclass, yes
                                       →  allowed
```

"Dog or below" sits entirely inside "Animal or below", so the assignment is always safe.

The compiler gave the unknown type behind `dogsOrBelow` the name `CAP#1` when checking this line.
`animalsOrBelow` does not get a CAP name, because only the right side is captured (6.5). The
check is simply "is `List<CAP#1>`, with CAP#1 being Dog or below, acceptable to a
`List<? extends Animal>` label?" The answer is yes.

## Example 7.3: a wide wildcard label into a narrow one (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignAnimalsOrBelowToDogsOrBelow {

    public static void main(String[] args) {
        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));

        List<? extends Animal> animalsOrBelow = catList;
        List<? extends Dog> dogsOrBelow = animalsOrBelow;
    }
}
```

Compiler output:

```
AssignAnimalsOrBelowToDogsOrBelow.java:11: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Dog>
        List<? extends Dog> dogsOrBelow = animalsOrBelow;
                                          ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

The variables:

- `catList` is a `List<Cat>` holding Tom.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at `catList`.
- `dogsOrBelow` is a `List<? extends Dog>`, which we try to point at the same list.

```
Right side (animalsOrBelow) could be:  List<Animal>, List<Dog>, List<Cat>, List<Puppy>
Left side (dogsOrBelow) accepts:       List<Dog>, List<Puppy>
List<Animal> accepted?                 NO
List<Cat> accepted?                    NO
                                       →  refused
```

Here it really is a Cat list. If this were allowed, `dogsOrBelow.get(0)` would be read as a Dog
(Rule 3 says that's fine for `? extends Dog`), but it would actually be Tom the Cat.

## Example 7.4: a wildcard label into an exact label (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignWildcardToExact {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));

        List<? extends Animal> animalsOrBelow = dogList;
        List<Animal> animalList = animalsOrBelow;
    }
}
```

Compiler output:

```
AssignWildcardToExact.java:11: error: incompatible types: List<CAP#1> cannot be converted to List<Animal>
        List<Animal> animalList = animalsOrBelow;
                                  ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Animal from capture of ? extends Animal
1 error
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is a `List<? extends Animal>`, pointing at `dogList`.
- `animalList` is a `List<Animal>`, which we try to point at the same list.

```
Right side (animalsOrBelow) could be:  List<Animal>, List<Dog>, List<Cat>, List<Puppy>
Left side (animalList) accepts:        List<Animal> only
List<Dog> accepted?                    NO   →  refused
```

This is the "back door" check. If it were allowed, `animalList.add(new Cat("Tom"))` would put a
Cat into `dogList`, the exact disaster from 1.2.

## Example 7.5: the compiler forgets what you assigned (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class CompilerForgetsPuppy {

    public static void main(String[] args) {
        List<Puppy> puppyList = new ArrayList<>();
        List<? extends Dog> dogsOrBelow = puppyList;
        List<? extends Puppy> puppiesOrBelow = dogsOrBelow;
    }
}
```

Compiler output:

```
CompilerForgetsPuppy.java:9: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Puppy>
        List<? extends Puppy> puppiesOrBelow = dogsOrBelow;
                                               ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Dog from capture of ? extends Dog
1 error
```

The variables:

- `puppyList` is a `List<Puppy>`, empty.
- `dogsOrBelow` is a `List<? extends Dog>`, pointing at `puppyList`.
- `puppiesOrBelow` is a `List<? extends Puppy>`, which we try to point at the same list.

We know `dogsOrBelow` points at a Puppy list: we assigned it one line earlier. But the compiler
only knows its declared type, `List<? extends Dog>`:

```
Right side (dogsOrBelow) could be:     List<Dog>, List<Puppy>
Left side (puppiesOrBelow) accepts:    List<Puppy>, (Puppy subclasses written later)
List<Dog> accepted?                    NO   →  refused
```

## Example 7.6: one `? super` label into a lower `? super` label (allowed)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignSuperToSuper {

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();

        List<? super Dog> dogsOrAbove = animalList;
        List<? super Puppy> puppiesOrAbove = dogsOrAbove;

        System.out.println(puppiesOrAbove.size());
    }
}
```

Output:

```
0
```

The variables:

- `animalList` is a `List<Animal>`, empty.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `animalList`.
- `puppiesOrAbove` is a `List<? super Puppy>`, pointing at the same list.

```
Right side (dogsOrAbove) could be:     List<Dog>, List<Animal>, List<Object>
Left side (puppiesOrAbove) accepts:    List<Puppy>, List<Dog>, List<Animal>, List<Object>
Every right possibility accepted?      Dog yes, Animal yes, Object yes  →  allowed
```

"Dog or above" sits entirely inside "Puppy or above". It makes sense: any list that can take Dogs
can also take Puppies.

## Example 7.7: one `? super` label into a higher `? super` label (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignSuperRefused {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();

        List<? super Dog> dogsOrAbove = dogList;
        List<? super Animal> animalsOrAbove = dogsOrAbove;
    }
}
```

Compiler output:

```
AssignSuperRefused.java:10: error: incompatible types: List<CAP#1> cannot be converted to List<? super Animal>
        List<? super Animal> animalsOrAbove = dogsOrAbove;
                                              ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

The variables:

- `dogList` is a `List<Dog>`, empty.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `dogList`.
- `animalsOrAbove` is a `List<? super Animal>`, which we try to point at the same list.

```
Right side (dogsOrAbove) could be:     List<Dog>, List<Animal>, List<Object>
Left side (animalsOrAbove) accepts:    List<Animal>, List<Object>
List<Dog> accepted?                    NO   →  refused
```

Here it really is a Dog list. If this were allowed, `animalsOrAbove.add(new Cat("Tom"))` would be
fine by Rule 2 (a Cat fits both a `List<Animal>` and a `List<Object>`), and a Cat would land in
`dogList`.

## Example 7.8: a `? extends` label into a `? super` label (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignExtendsToSuper {

    public static void main(String[] args) {
        List<Puppy> puppyList = new ArrayList<>();

        List<? extends Dog> dogsOrBelow = puppyList;
        List<? super Dog> dogsOrAbove = dogsOrBelow;
    }
}
```

Compiler output:

```
AssignExtendsToSuper.java:10: error: incompatible types: List<CAP#1> cannot be converted to List<? super Dog>
        List<? super Dog> dogsOrAbove = dogsOrBelow;
                                        ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Dog from capture of ? extends Dog
1 error
```

The variables:

- `puppyList` is a `List<Puppy>`, empty.
- `dogsOrBelow` is a `List<? extends Dog>`, pointing at `puppyList`.
- `dogsOrAbove` is a `List<? super Dog>`, which we try to point at the same list.

```
Right side (dogsOrBelow) could be:   List<Dog>, List<Puppy>
Left side (dogsOrAbove) accepts:     List<Dog>, List<Animal>, List<Object>
List<Puppy> accepted?                NO   →  refused
```

Both labels mention Dog, but they mean opposite ranges. Their only overlap is `List<Dog>`. If this
were allowed, `dogsOrAbove.add(new Dog("Rex"))` would put a plain Dog into `puppyList`.

## Example 7.9: a `? super` label into a `? extends` label (refused)

```java
import java.util.ArrayList;
import java.util.List;

public class AssignSuperToExtends {

    public static void main(String[] args) {
        List<Object> objectList = new ArrayList<>();

        List<? super Dog> dogsOrAbove = objectList;
        List<? extends Animal> animalsOrBelow = dogsOrAbove;
    }
}
```

Compiler output:

```
AssignSuperToExtends.java:10: error: incompatible types: List<CAP#1> cannot be converted to List<? extends Animal>
        List<? extends Animal> animalsOrBelow = dogsOrAbove;
                                                ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Dog from capture of ? super Dog
1 error
```

The variables:

- `objectList` is a `List<Object>`, empty.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `objectList`.
- `animalsOrBelow` is a `List<? extends Animal>`, which we try to point at the same list.

```
Right side (dogsOrAbove) could be:      List<Dog>, List<Animal>, List<Object>
Left side (animalsOrBelow) accepts:     List<Animal>, List<Dog>, List<Cat>, List<Puppy>, ...
List<Object> accepted?                  NO   →  refused
```

Here it really is an Object list. If this were allowed, `animalsOrBelow.get(0)` would be read as
an Animal, but a `List<Object>` could contain a String.

## The pattern in Part 7

- `? extends` labels can be assigned to a label with a **higher or equal** ceiling
  (`? extends Dog` into `? extends Animal`), never a lower one.
- `? super` labels can be assigned to a label with a **lower or equal** floor
  (`? super Dog` into `? super Puppy`), never a higher one.
- A wildcard label can never be assigned to an exact label (`List<Animal>`, `List<Dog>`).
- An exact list can be assigned to any wildcard label whose range includes it.

---

# Part 8: Worked examples with methods — PECS

## 8.1 A method that reads from one list and adds into another

`copyDogs` takes Dogs out of `source` and puts them into `target`:

```java
import java.util.ArrayList;
import java.util.List;

public class CopyDogs {

    static void copyDogs(List<? extends Dog> source, List<? super Dog> target) {
        for (Dog dog : source) {
            target.add(dog);
        }
    }

    public static void main(String[] args) {
        List<Puppy> puppyList = new ArrayList<>();
        puppyList.add(new Puppy("Bolt"));
        puppyList.add(new Puppy("Max"));
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<Animal> animalList = new ArrayList<>();
        List<Object> objectList = new ArrayList<>();

        copyDogs(puppyList, animalList);
        copyDogs(dogList, animalList);
        copyDogs(dogList, objectList);

        for (Animal animal : animalList) {
            animal.eat();
        }
        System.out.println(objectList.size() + " in objectList");
    }
}
```

Output:

```
Bolt is eating
Max is eating
Rex is eating
1 in objectList
```

The variables:

- `puppyList` is a `List<Puppy>` holding Bolt and Max.
- `dogList` is a `List<Dog>` holding Rex.
- `animalList` is a `List<Animal>`, empty at the start.
- `objectList` is a `List<Object>`, empty at the start.
- `source` (first parameter) is a `List<? extends Dog>`: Dog or below. The method only **reads**
  from it.
- `target` (second parameter) is a `List<? super Dog>`: Dog or above. The method only **adds**
  into it.
- `dog` (the loop variable) is declared as `Dog`.

Inside the method:

- `for (Dog dog : source)` reads each item as a Dog. Rule 3: every item of a `List<Dog>` or
  `List<Puppy>` is a Dog. Allowed.
- `target.add(dog)` adds a Dog. Rule 2: a Dog fits a `List<Dog>`, `List<Animal>` and
  `List<Object>`. Allowed.

The calls (each argument is assigned to its parameter, Rule 1):

- `copyDogs(puppyList, animalList)`: `List<Puppy>` is Dog or below ✓. `List<Animal>` is Dog or
  above ✓.
- `copyDogs(dogList, animalList)`: `List<Dog>` ✓ and `List<Animal>` ✓.
- `copyDogs(dogList, objectList)`: `List<Dog>` ✓ and `List<Object>` ✓.

Two wrong calls:

```java
import java.util.ArrayList;
import java.util.List;

public class CopyDogsFromCats {

    static void copyDogs(List<? extends Dog> source, List<? super Dog> target) {
        for (Dog dog : source) {
            target.add(dog);
        }
    }

    public static void main(String[] args) {
        List<Cat> catList = new ArrayList<>();
        List<Animal> animalList = new ArrayList<>();
        copyDogs(catList, animalList);
    }
}
```

Compiler output:

```
CopyDogsFromCats.java:15: error: incompatible types: List<Cat> cannot be converted to List<? extends Dog>
        copyDogs(catList, animalList);
                 ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`catList` is a `List<Cat>`. Cat isn't Dog or below, so it can't be the `source`. (Otherwise the
loop would read Cats as Dogs.)

```java
import java.util.ArrayList;
import java.util.List;

public class CopyDogsIntoPuppies {

    static void copyDogs(List<? extends Dog> source, List<? super Dog> target) {
        for (Dog dog : source) {
            target.add(dog);
        }
    }

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        List<Puppy> puppyList = new ArrayList<>();
        copyDogs(dogList, puppyList);
    }
}
```

Compiler output:

```
CopyDogsIntoPuppies.java:15: error: incompatible types: List<Puppy> cannot be converted to List<? super Dog>
        copyDogs(dogList, puppyList);
                          ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

`puppyList` is a `List<Puppy>`. Puppy isn't Dog or above, so it can't be the `target`. (Otherwise
plain Dogs would land in a Puppy list.)

## 8.2 PECS: how to choose

**PECS = Producer Extends, Consumer Super.**

- A list that **produces** items for your method (you take items **out** of it) is a
  **producer**. Use `? extends X`. In `copyDogs`, `source` is the producer.
- A list that **consumes** items from your method (you put items **into** it) is a **consumer**.
  Use `? super X`. In `copyDogs`, `target` is the consumer.
- A list you both take from and put into: use the **exact** type, `List<X>`, with no wildcard.

Questions to ask about each list parameter:

```
Does my method only read items from it?        →  List<? extends X>
Does my method only add items into it?         →  List<? super X>
Does it do both?                               →  List<X>
```

---

# Part 9: The same rules with a different family: numbers

The rules don't depend on animals. Here is Java's number family:

```
              Object
                |
              Number          (has doubleValue())
           /    |     \
     Integer  Double   Long
```

One small fact: when you write `7`, Java sees an `int` and automatically turns it into an
`Integer` when an object is needed. That's why some errors below say `int` or `double`.

The possibilities:

- `List<? extends Number> numbersOrBelow` could be a `List<Number>`, `List<Integer>`,
  `List<Double>`, `List<Long>`, or a list of other Number subclasses.
- `List<? super Integer> integersOrAbove` could be a `List<Integer>`, `List<Number>` or
  `List<Object>`. (Integer also "is a" `Comparable<Integer>`, so a `List<Comparable<Integer>>` is
  possible too. That doesn't change any answer below.)

## 9.1 `? extends Number`: one method that totals any list of numbers

```java
import java.util.ArrayList;
import java.util.List;

public class TotalNumbers {

    static double total(List<? extends Number> numbersOrBelow) {
        double sum = 0;
        for (Number number : numbersOrBelow) {
            sum = sum + number.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<Integer> integerList = List.of(3, 5, 2);
        List<Double> doubleList = List.of(149.5, 50.0);
        List<Long> longList = List.of(10L, 20L);
        List<Number> numberList = List.of(1, 2.5);

        System.out.println(total(integerList));
        System.out.println(total(doubleList));
        System.out.println(total(longList));
        System.out.println(total(numberList));
    }
}
```

Output:

```
10.0
199.5
30.0
3.5
```

The variables:

- `integerList` is a `List<Integer>` holding 3, 5, 2.
- `doubleList` is a `List<Double>` holding 149.5, 50.0.
- `longList` is a `List<Long>` holding 10, 20.
- `numberList` is a `List<Number>` holding 1 (an Integer) and 2.5 (a Double).
- `numbersOrBelow` (the parameter) is a `List<? extends Number>`.
- `number` (the loop variable) is declared as `Number`. Reading as Number is allowed by Rule 3,
  so `number.doubleValue()` can be called.
- `sum` is a `double`, the running total.

A list outside the range is refused:

```java
import java.util.ArrayList;
import java.util.List;

public class TotalRefusesStrings {

    static double total(List<? extends Number> numbersOrBelow) {
        double sum = 0;
        for (Number number : numbersOrBelow) {
            sum = sum + number.doubleValue();
        }
        return sum;
    }

    public static void main(String[] args) {
        List<String> stringList = List.of("A1");
        total(stringList);
    }
}
```

Compiler output:

```
TotalRefusesStrings.java:16: error: incompatible types: List<String> cannot be converted to List<? extends Number>
        total(stringList);
              ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

## 9.2 `? extends Number`: adding is refused

```java
import java.util.ArrayList;
import java.util.List;

public class NumbersOrBelowAdd {

    public static void main(String[] args) {
        List<Integer> integerList = new ArrayList<>();
        List<? extends Number> numbersOrBelow = integerList;
        numbersOrBelow.add(7);
    }
}
```

Compiler output:

```
NumbersOrBelowAdd.java:9: error: incompatible types: int cannot be converted to CAP#1
        numbersOrBelow.add(7);
                           ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Number from capture of ? extends Number
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The variables:

- `integerList` is a `List<Integer>`, empty.
- `numbersOrBelow` is a `List<? extends Number>`, pointing at `integerList`.

```
Possibility of numbersOrBelow     Can it hold 7 (an Integer)?
List<Number>                      yes
List<Integer>                     yes
List<Double>                      NO
List<Long>                        NO
```

## 9.3 `? super Integer`: one method that adds integers into many kinds of list

```java
import java.util.ArrayList;
import java.util.List;

public class AddSeatCounts {

    static void addSeatCounts(List<? super Integer> integersOrAbove) {
        integersOrAbove.add(3);
        integersOrAbove.add(5);
    }

    public static void main(String[] args) {
        List<Integer> integerList = new ArrayList<>();
        List<Number> numberList = new ArrayList<>();
        numberList.add(2.5);
        List<Object> objectList = new ArrayList<>();
        objectList.add("A1");

        addSeatCounts(integerList);
        addSeatCounts(numberList);
        addSeatCounts(objectList);

        System.out.println(integerList);
        System.out.println(numberList);
        System.out.println(objectList);
    }
}
```

Output:

```
[3, 5]
[2.5, 3, 5]
[A1, 3, 5]
```

The variables:

- `integerList` is a `List<Integer>`, empty at the start.
- `numberList` is a `List<Number>`, holding 2.5 at the start.
- `objectList` is a `List<Object>`, holding `"A1"` at the start.
- `integersOrAbove` (the parameter) is a `List<? super Integer>`.

```
Possibility of integersOrAbove    Can it hold 3 (an Integer)?
List<Integer>                     yes
List<Number>                      yes
List<Object>                      yes
```

A `List<Double>` is refused, because Double is not Integer or above:

```java
import java.util.ArrayList;
import java.util.List;

public class AddSeatCountsRefusesDoubles {

    static void addSeatCounts(List<? super Integer> integersOrAbove) {
        integersOrAbove.add(3);
        integersOrAbove.add(5);
    }

    public static void main(String[] args) {
        List<Double> doubleList = new ArrayList<>();
        addSeatCounts(doubleList);
    }
}
```

Compiler output:

```
AddSeatCountsRefusesDoubles.java:13: error: incompatible types: List<Double> cannot be converted to List<? super Integer>
        addSeatCounts(doubleList);
                      ^
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

## 9.4 `? super Integer`: adding a Double is refused

```java
import java.util.ArrayList;
import java.util.List;

public class IntegersOrAboveAddDouble {

    public static void main(String[] args) {
        List<Number> numberList = new ArrayList<>();
        List<? super Integer> integersOrAbove = numberList;
        integersOrAbove.add(2.5);
    }
}
```

Compiler output:

```
IntegersOrAboveAddDouble.java:9: error: incompatible types: double cannot be converted to CAP#1
        integersOrAbove.add(2.5);
                            ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Integer from capture of ? super Integer
Note: Some messages have been simplified; recompile with -Xdiags:verbose to get full output
1 error
```

The variables:

- `numberList` is a `List<Number>`, empty.
- `integersOrAbove` is a `List<? super Integer>`, pointing at `numberList`.

```
Possibility of integersOrAbove    Can it hold 2.5 (a Double)?
List<Integer>                     NO
List<Number>                      yes
List<Object>                      yes
```

## 9.5 `? super Integer`: reading only as Object

```java
import java.util.ArrayList;
import java.util.List;

public class IntegersOrAboveRead {

    public static void main(String[] args) {
        List<Number> numberList = new ArrayList<>();
        numberList.add(2.5);
        List<? super Integer> integersOrAbove = numberList;

        Object firstItem = integersOrAbove.get(0);
        System.out.println(firstItem);

        Number firstNumber = integersOrAbove.get(0);
    }
}
```

Compiler output (the whole file is refused because of the last line, so nothing runs):

```
IntegersOrAboveRead.java:14: error: incompatible types: CAP#1 cannot be converted to Number
        Number firstNumber = integersOrAbove.get(0);
                                                ^
  where CAP#1 is a fresh type-variable:
    CAP#1 extends Object super: Integer from capture of ? super Integer
1 error
```

The variables:

- `numberList` is a `List<Number>` holding 2.5.
- `integersOrAbove` is a `List<? super Integer>`, pointing at `numberList`.
- `firstItem` is declared as `Object`. That line is fine.
- `firstNumber` is declared as `Number`. That line is refused.

```
Possibility of integersOrAbove    Is every item a Number?
List<Integer>                     yes
List<Number>                      yes
List<Object>                      NO (could hold a String)
```

Line 11 (`Object firstItem = ...`) is fine. Only line 14 is refused.

---

# Part 10: Wildcards inside Java's own library

Java's own collection methods use exactly these rules. Here are their real signatures, with `E`
meaning "the element type of this list".

**`List.addAll(Collection<? extends E> c)`**: producer, so `extends`. A `List<Animal>` (E is
Animal) can take all items from a `List<Dog>` or a `List<Cat>`, because it only reads from them.

```java
import java.util.ArrayList;
import java.util.List;

public class AddAllDemo {

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));

        List<Animal> animalList = new ArrayList<>();
        animalList.addAll(dogList);
        animalList.addAll(catList);

        for (Animal animal : animalList) {
            animal.eat();
        }
    }
}
```

Output:

```
Rex is eating
Tom is eating
```

Here `animalList.addAll(dogList)` assigns `dogList` (a `List<Dog>`) to the parameter
`Collection<? extends Animal>`. Dog is Animal or below, so it's allowed.

**`List.sort(Comparator<? super E> c)`**: consumer, so `super`. A comparator "consumes" two items
and compares them. A comparator that can compare any two Animals can certainly compare two Dogs.

This comparator compares Animals by name (it needs `ByName.java` in the same folder):

```java
import java.util.Comparator;

class ByName implements Comparator<Animal> {

    @Override
    public int compare(Animal a, Animal b) {
        return a.name().compareTo(b.name());
    }
}
```

```java
import java.util.ArrayList;
import java.util.List;

public class SortWithComparator {

    public static void main(String[] args) {
        ByName byName = new ByName();

        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));
        dogList.add(new Dog("Bruno"));
        dogList.add(new Dog("Max"));
        dogList.sort(byName);
        for (Dog dog : dogList) {
            dog.bark();
        }

        List<Cat> catList = new ArrayList<>();
        catList.add(new Cat("Tom"));
        catList.add(new Cat("Kitty"));
        catList.sort(byName);
        for (Cat cat : catList) {
            cat.meow();
        }
    }
}
```

Output:

```
Bruno says woof
Max says woof
Rex says woof
Kitty says meow
Tom says meow
```

The variables:

- `byName` is a `ByName`, which is a `Comparator<Animal>`.
- `dogList` is a `List<Dog>` holding Rex, Bruno and Max. `catList` is a `List<Cat>` holding Tom
  and Kitty.
- In `dogList.sort(byName)`, E is Dog, so the parameter is `Comparator<? super Dog>`.
  `Comparator<Animal>` fits, because Animal is Dog or above.
- In `catList.sort(byName)`, E is Cat, so the parameter is `Comparator<? super Cat>`.
  `Comparator<Animal>` fits again.

One comparator works for both lists. Without `? super`, you'd need a separate Dog comparator and
Cat comparator.

**`Collections.addAll(Collection<? super T> c, T... elements)`**: the collection is a consumer,
so `super`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectionsAddAllDemo {

    public static void main(String[] args) {
        List<Animal> animalList = new ArrayList<>();
        Collections.addAll(animalList, new Dog("Rex"), new Puppy("Bolt"));

        for (Animal animal : animalList) {
            animal.eat();
        }
    }
}
```

Output:

```
Rex is eating
Bolt is eating
```

**`Collections.copy(List<? super T> dest, List<? extends T> src)`** has the same shape as our
`copyDogs`: `src` is the producer (extends), `dest` is the consumer (super).

**In Spring Security** (which you'll meet in backend work), the user's roles are returned as
`Collection<? extends GrantedAuthority> getAuthorities()`. That's a producer: you read the
authorities out, but you can't add to them through that label.

---

# Part 11: A wildcard `?` versus a named type `T`

You may also see methods written with a named type, like `<T extends Animal>`. They look similar,
but they are not the same.

**Difference 1: a bound with a name can't be written on a variable.** `List<T extends Animal>` is
not valid as a variable type:

```java
import java.util.List;

public class BoundInVariable {

    public static void main(String[] args) {
        List<T extends Animal> someList = null;
    }
}
```

Compiler output:

```
BoundInVariable.java:6: error: > or ',' expected
        List<T extends Animal> someList = null;
               ^
BoundInVariable.java:6: error: not a statement
        List<T extends Animal> someList = null;
            ^
2 errors
```

`T` has to be declared first, in front of a method (`static <T extends Animal> ...`) or after a
class name (`class Box<T extends Animal>`). A `?` can be used directly in any variable or
parameter type.

**Difference 2: a named type remembers the exact type; a wildcard forgets it.**

```java
import java.util.ArrayList;
import java.util.List;

public class NamedTypeVsWildcard {

    static Animal firstWithWildcard(List<? extends Animal> animalsOrBelow) {
        return animalsOrBelow.get(0);
    }

    static <T extends Animal> T firstWithNamedType(List<T> someList) {
        return someList.get(0);
    }

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));

        Animal fromWildcard = firstWithWildcard(dogList);
        fromWildcard.eat();

        Dog fromNamedType = firstWithNamedType(dogList);
        fromNamedType.bark();
    }
}
```

Output:

```
Rex is eating
Rex says woof
```

The variables:

- `dogList` is a `List<Dog>` holding Rex.
- `animalsOrBelow` is the parameter of `firstWithWildcard`, a `List<? extends Animal>`. The method
  can only promise to return an `Animal`, because the `?` has no name to put in the return type.
- `someList` is the parameter of `firstWithNamedType`, a `List<T>`. When called with `dogList`, T
  becomes Dog, so the method returns a Dog.
- `fromWildcard` is declared as `Animal`. `fromNamedType` is declared as `Dog`, so `bark()` works.

With the wildcard version, asking for a Dog fails:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardLosesDog {

    static Animal firstWithWildcard(List<? extends Animal> animalsOrBelow) {
        return animalsOrBelow.get(0);
    }

    public static void main(String[] args) {
        List<Dog> dogList = new ArrayList<>();
        dogList.add(new Dog("Rex"));

        Dog firstDog = firstWithWildcard(dogList);
    }
}
```

Compiler output:

```
WildcardLosesDog.java:14: error: incompatible types: Animal cannot be converted to Dog
        Dog firstDog = firstWithWildcard(dogList);
                                        ^
1 error
```

When to use which:

- Use a **wildcard** when you don't need to mention the type again (just read, or just add).
- Use a **named T** when you need to use the same type in a second place: as the return type, for
  a second parameter that must match, or to put an item back into the same list.

---

# Part 12: Practice questions with answers

Setup for all questions (assume these lines come first):

- `animalList` is a `List<Animal>` holding Kitty the Cat.
- `dogList` is a `List<Dog>` holding Rex.
- `catList` is a `List<Cat>` holding Tom.
- `puppyList` is a `List<Puppy>` holding Bolt.
- `objectList` is a `List<Object>` holding `"A1"`.
- `integerList` is a `List<Integer>` holding 3.
- `doubleList` is a `List<Double>` holding 2.5.
- `numberList` is a `List<Number>` holding 1.
- `dogsOrBelow` is a `List<? extends Dog>`, pointing at `puppyList`.
- `dogsOrAbove` is a `List<? super Dog>`, pointing at `animalList`.
- `numbersOrBelow` is a `List<? extends Number>`, pointing at `integerList`.
- `integersOrAbove` is a `List<? super Integer>`, pointing at `numberList`.
- `feedAll(List<? extends Animal> animalsOrBelow)` feeds every animal.
- `addTwoDogs(List<? super Dog> dogsOrAbove)` adds two Dogs.

For each line: does it compile? Work it out with the possibilities method, then open the answer.
Every answer was checked with the compiler.

**Q1.** `List<? extends Animal> x = puppyList;`

<details><summary>Answer</summary>

Compiles. Right side: `List<Puppy>`. Left side accepts Animal or below. Puppy is below Animal. ✓
</details>

**Q2.** `List<? extends Dog> x = catList;`

<details><summary>Answer</summary>

Refused: `List<Cat> cannot be converted to List<? extends Dog>`. Cat is not Dog or below.
</details>

**Q3.** `List<? extends Dog> x = animalList;`

<details><summary>Answer</summary>

Refused: `List<Animal> cannot be converted to List<? extends Dog>`. Animal is above the ceiling
Dog. (Reading Dogs out of it would be unsafe: it holds Kitty the Cat.)
</details>

**Q4.** `List<? super Dog> x = objectList;`

<details><summary>Answer</summary>

Compiles. Object is above Dog. ✓
</details>

**Q5.** `List<? super Dog> x = puppyList;`

<details><summary>Answer</summary>

Refused: `List<Puppy> cannot be converted to List<? super Dog>`. Puppy is below the floor Dog.
</details>

**Q6.** `List<? super Puppy> x = dogList;`

<details><summary>Answer</summary>

Compiles. Dog is above Puppy. ✓
</details>

**Q7.** `List<? super Animal> x = dogList;`

<details><summary>Answer</summary>

Refused: `List<Dog> cannot be converted to List<? super Animal>`. Dog is below the floor Animal.
(Otherwise a Cat could be added through `x` into the Dog list.)
</details>

**Q8.** `List<? extends Number> x = integerList;`

<details><summary>Answer</summary>

Compiles. Integer is below Number. ✓
</details>

**Q9.** `List<? super Integer> x = doubleList;`

<details><summary>Answer</summary>

Refused: `List<Double> cannot be converted to List<? super Integer>`. Double is a sibling of
Integer, not above it.
</details>

**Q10.** `List<? super Integer> x = numberList;`

<details><summary>Answer</summary>

Compiles. Number is above Integer. ✓
</details>

**Q11.** `dogsOrBelow.add(new Puppy("Max"));`

<details><summary>Answer</summary>

Refused: `Puppy cannot be converted to CAP#1` with `CAP#1 extends Dog from capture of ? extends Dog`.
`dogsOrBelow` could be a list of some Dog subclass written later, and a Puppy wouldn't fit there.
No add is ever allowed on `? extends` (except `null`), even though it really points at
`puppyList` right now.
</details>

**Q12.** `dogsOrAbove.add(new Puppy("Max"));`

<details><summary>Answer</summary>

Compiles, and `animalList.size()` becomes 2. A Puppy fits a `List<Dog>`, a `List<Animal>` and a
`List<Object>`. ✓
</details>

**Q13.** `Dog firstDog = dogsOrAbove.get(0);`

<details><summary>Answer</summary>

Refused: `CAP#1 cannot be converted to Dog` with `CAP#1 extends Object super: Dog`. The list could
be a `List<Animal>` or `List<Object>`, and here item 0 really is Kitty the Cat. Only `Object` is
safe.
</details>

**Q14.** `Dog firstDog = dogsOrBelow.get(0); firstDog.bark();`

<details><summary>Answer</summary>

Compiles and prints `Bolt says woof`. Every possibility of `dogsOrBelow` (Dog list, Puppy list,
later Dog subclass) contains only Dogs. ✓
</details>

**Q15.** `Puppy firstPuppy = dogsOrBelow.get(0);`

<details><summary>Answer</summary>

Refused: `CAP#1 cannot be converted to Puppy` with `CAP#1 extends Dog`. The list could be a
`List<Dog>` holding plain Dogs. The compiler doesn't remember it points at `puppyList`.
</details>

**Q16.** `List<? super Dog> x = dogsOrBelow;`

<details><summary>Answer</summary>

Refused: `List<CAP#1> cannot be converted to List<? super Dog>` with `CAP#1 extends Dog`. The right
side could be a `List<Puppy>`, which is below the floor.
</details>

**Q17.** `List<? extends Animal> x = dogsOrAbove;`

<details><summary>Answer</summary>

Refused: `List<CAP#1> cannot be converted to List<? extends Animal>` with
`CAP#1 extends Object super: Dog`. The right side could be a `List<Object>`, which is above the
ceiling.
</details>

**Q18.** `integersOrAbove.add(7); System.out.println(numberList);`

<details><summary>Answer</summary>

Compiles and prints `[1, 7]`. An Integer fits a `List<Integer>`, `List<Number>` and `List<Object>`. ✓
</details>

**Q19.** `Number firstNumber = numbersOrBelow.get(0); System.out.println(firstNumber);`

<details><summary>Answer</summary>

Compiles and prints `3`. Every item in a list of "Number or below" is a Number. ✓
</details>

**Q20.** `numbersOrBelow.add(7);`

<details><summary>Answer</summary>

Refused: `int cannot be converted to CAP#1` with `CAP#1 extends Number`. The list could be a
`List<Double>`.
</details>

**Q21.** `feedAll(objectList);`

<details><summary>Answer</summary>

Refused: `List<Object> cannot be converted to List<? extends Animal>`. Object is above the
ceiling.
</details>

**Q22.** `addTwoDogs(puppyList);`

<details><summary>Answer</summary>

Refused: `List<Puppy> cannot be converted to List<? super Dog>`. Puppy is below the floor.
</details>

**Q23.** `feedAll(dogsOrBelow);`

<details><summary>Answer</summary>

Compiles and prints `Bolt is eating`. Every possibility of `dogsOrBelow` (Dog or below) is also
Animal or below. ✓
</details>

**Q24.** `addTwoDogs(dogsOrAbove); System.out.println(animalList.size());`

<details><summary>Answer</summary>

Compiles and prints `3` (Kitty plus two Dogs). Every possibility of `dogsOrAbove` (Dog or above) is
accepted by the parameter `List<? super Dog>`. ✓
</details>

---

# Part 13: Cheat sheet

**What the symbols mean**

- `List<Dog>`: exactly a list of Dogs. Accepts only a `List<Dog>`.
- `List<? extends Dog>`: a list of one type that is **Dog or below**. Dog is the ceiling.
- `List<? super Dog>`: a list of one type that is **Dog or above**. Dog is the floor.
- `List<?>`: the same as `List<? extends Object>`, a list of some type.
- `?` means **one unknown type**, not "mix anything".

**The method**

1. List the possibilities of each label, using **declared types only**. For `? extends`, include
   "subclasses written later".
2. Apply the rule:
   - **Assign `left = right`** (including passing an argument): every possibility of the right
     must be accepted by the left.
   - **Add an item**: the item must fit **every** possibility.
   - **Read as R**: every item of **every** possibility must be an R.

**What falls out**

```
                 ? extends X                  ? super X
Accepts          List<X>, lists below X       List<X>, lists above X
Add              nothing (only null)          X, or anything below X
Read as          X (or above)                 Object only
Role             producer (you take out)      consumer (you put in)
```

**PECS:** Producer Extends, Consumer Super. Only reading means `? extends`. Only adding means
`? super`. Both means the exact type.

**Error decoder**

- `List<Dog> cannot be converted to List<Animal>`: the type inside `< >` must match exactly. Use
  a wildcard if you only read.
- `X cannot be converted to CAP#1`: you tried to **add** an X, and X doesn't fit every possibility.
- `CAP#1 cannot be converted to X`: you tried to **read** as X, and not every possibility
  guarantees X.
- `List<CAP#1> cannot be converted to ...`: you tried to **assign** a wildcard label, and some
  possibility of the right side isn't accepted by the left.
- In the `where` line, `CAP#1 extends A` means "the most you can read items as is A", and
  `super: B` means "you can add a B".
- `CAP#1` is the compiler's made-up name for "the unknown type behind this `?`". It is created
  only for the label being used (the right side, the argument, or the list you call a method on).

**Patterns for assigning wildcard labels**

- `? extends Dog` can go into `? extends Animal` (higher ceiling), but not the other way round.
- `? super Dog` can go into `? super Puppy` (lower floor), but not the other way round.
- A wildcard label can never go into an exact label like `List<Animal>`.
- `? extends` and `? super` labels can't be assigned to each other (except into `List<?>`).
